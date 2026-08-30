package com.kite.app.foundation.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.util.Base64
import com.kite.app.foundation.devicebridge.DeviceBridgeContract
import com.kite.app.foundation.devicebridge.DeviceBridgeBackendException
import com.kite.app.foundation.devicebridge.DeviceBridgeProcessBackend
import com.kite.app.foundation.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * File-backed request/response bridge for the container-visible kf-host-self
 * ADB target. The container writes standard-ish adb shell requests under the
 * shared workspace; the APK executes them through Shizuku when authorized.
 */
object HostSelfAdbBridgeWorker {
    private const val LOG_TAG = "HostSelfAdbBridge"
    private const val BRIDGE_DIR = ".kf/adb-bridge"
    private const val REQUEST_DIR = "requests"
    private const val PROCESSING_DIR = "processing"
    private const val RESPONSE_DIR = "responses"
    private const val POLL_INTERVAL_MS = 180L
    private const val MAX_OUTPUT_BYTES = 16 * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var workerJob: Job? = null

    fun start(context: Context) {
        if (workerJob?.isActive == true) {
            return
        }
        val appContext = context.applicationContext
        workerJob = scope.launch {
            while (isActive) {
                runCatching {
                    pollOnce(appContext)
                }.onFailure { error ->
                    Logger.e(LOG_TAG, "host-self adb bridge poll failed: ${error.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun pollOnce(context: Context) {
        val bridgeRoot = bridgeRoot(context) ?: return
        val requestDir = File(bridgeRoot, REQUEST_DIR)
        val processingDir = File(bridgeRoot, PROCESSING_DIR)
        val responseDir = File(bridgeRoot, RESPONSE_DIR)
        listOf(requestDir, processingDir, responseDir).forEach { it.mkdirs() }

        requestDir.listFiles { file -> file.isFile && file.name.endsWith(".req") }
            ?.sortedBy { it.lastModified() }
            ?.take(4)
            ?.forEach { request ->
                val claimed = File(processingDir, request.name)
                if (request.renameTo(claimed)) {
                    handleRequest(context, claimed, responseDir)
                }
            }
    }

    private fun bridgeRoot(context: Context): File? {
        val container = KFContainerManager.getSavedContainer(context) ?: return null
        return File(container.workspacePath, BRIDGE_DIR)
    }

    private fun handleRequest(context: Context, requestFile: File, responseDir: File) {
        val request = BridgeRequest.fromFile(requestFile)
        if (request == null) {
            requestFile.delete()
            return
        }

        val stream = BridgeStream(
            stdout = File(responseDir, "${request.id}.stdout.stream"),
            stderr = File(responseDir, "${request.id}.stderr.stream"),
            cancel = File(responseDir, "${request.id}.cancel")
        )
        stream.clear()

        val result = when (request.kind) {
            "shell" -> executeShell(context, request.command, appendTextNewline = true, stream)
            "exec-out" -> executeShell(context, request.command, appendTextNewline = false, stream)
            "pull" -> executeShell(context, "cat ${shellQuote(request.remotePath)}", appendTextNewline = false, stream)
            "push" -> writeRemoteFile(context, request, stream)
            "install" -> installPackage(context, request, stream)
            else -> BridgeResult(
                exitCode = 125,
                stdout = ByteArray(0),
                stderr = "unsupported host-self adb request kind: ${request.kind}\n".toByteArray()
            )
        }

        val response = File(responseDir, "${request.id}.resp")
        val temp = File(responseDir, "${request.id}.resp.tmp")
        temp.writeText(result.toResponseText(request.id))
        temp.renameTo(response)
        requestFile.delete()
    }

    private fun executeShell(
        context: Context,
        command: String,
        appendTextNewline: Boolean,
        stream: BridgeStream
    ): BridgeResult {
        if (command.isBlank()) {
            return BridgeResult(125, ByteArray(0), "interactive adb shell is not supported by kf-host-self V0\n".toByteArray())
        }
        executeInputTextCompat(context, command, stream)?.let { return it }

        return executeRawShell(context, command, appendTextNewline, stream)
    }

    private fun executeRawShell(
        context: Context,
        command: String,
        appendTextNewline: Boolean,
        stream: BridgeStream
    ): BridgeResult {
        if (command.isBlank()) {
            return BridgeResult(125, ByteArray(0), "interactive adb shell is not supported by kf-host-self V0\n".toByteArray())
        }

        return runCatching {
            val remote = DeviceBridgeProcessBackend.startShell(context, command)

            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val stdoutThread = drainAsync(remote.stdout, stdout, stream.stdout)
            val stderrThread = drainAsync(remote.stderr, stderr, stream.stderr)

            while (true) {
                val completed = runCatching {
                    remote.waitForTimeout(500L)
                }.getOrDefault(false)
                if (completed) break
                if (stream.cancel.exists()) {
                    runCatching { remote.destroy() }
                    return BridgeResult(
                        exitCode = 130,
                        stdout = stdout.toByteArray(),
                        stderr = stderr.toByteArray() + "kf-host-self command cancelled\n".toByteArray()
                    )
                }
            }

            stdoutThread.join(1_000L)
            stderrThread.join(1_000L)
            BridgeResult(
                exitCode = remote.exitValue(),
                stdout = stdout.toByteArray().withTrailingNewlineIfNeeded(appendTextNewline),
                stderr = stderr.toByteArray().withTrailingNewlineIfNeeded(appendTextNewline)
            )
        }.getOrElse { error ->
            if (error is DeviceBridgeBackendException) {
                return BridgeResult(
                    error.exitCode,
                    ByteArray(0),
                    "kf-host-self backend unavailable: ${error.message}\n".toByteArray()
                )
            }
            BridgeResult(125, ByteArray(0), "kf-host-self shell bridge failed: ${error.javaClass.simpleName}: ${error.message}\n".toByteArray())
        }
    }

    private fun writeRemoteFile(context: Context, request: BridgeRequest, stream: BridgeStream): BridgeResult {
        if (request.remotePath.isBlank()) {
            return BridgeResult(125, ByteArray(0), "adb push requires a remote path\n".toByteArray())
        }
        val command = "cat > ${shellQuote(request.remotePath)}"
        return runRemoteProcess(context, command = command, stdin = request.payload, appendTextNewline = true, stream)
    }

    private fun installPackage(context: Context, request: BridgeRequest, stream: BridgeStream): BridgeResult {
        if (request.payload.isEmpty()) {
            return BridgeResult(125, ByteArray(0), "adb install requires a local APK payload\n".toByteArray())
        }
        val tempPath = "/data/local/tmp/kf-host-self-${request.id}.apk"
        val writeResult = runRemoteProcess(
            context = context,
            command = "cat > ${shellQuote(tempPath)}",
            stdin = request.payload,
            appendTextNewline = true,
            stream = stream
        )
        if (writeResult.exitCode != 0) {
            return writeResult
        }
        val installCommand = buildString {
            append("pm install ")
            if (request.command.isNotBlank()) {
                append(request.command)
                append(' ')
            }
            append(shellQuote(tempPath))
            append("; code=\$?; rm -f ")
            append(shellQuote(tempPath))
            append("; exit \$code")
        }
        return executeShell(context, installCommand, appendTextNewline = true, stream)
    }

    private fun executeInputTextCompat(context: Context, command: String, stream: BridgeStream): BridgeResult? {
        val text = parseInputTextCompatPayload(command) ?: return null
        if (text.none { it.code > 127 || it == '\n' || it == '\t' }) {
            return null
        }
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("kf-host-self-input-text", text))
            val pasteResult = executeRawShell(
                context = context,
                command = "input keyevent ${KeyEvent.KEYCODE_PASTE}",
                appendTextNewline = true,
                stream = stream
            )
            if (pasteResult.exitCode == 0) {
                BridgeResult(0, ByteArray(0), pasteResult.stderr)
            } else {
                pasteResult
            }
        }.getOrElse { error ->
            BridgeResult(
                125,
                ByteArray(0),
                "kf-host-self input text compatibility failed: ${error.javaClass.simpleName}: ${error.message}\n".toByteArray()
            )
        }
    }

    private fun parseInputTextCompatPayload(command: String): String? {
        val normalized = command.trim()
        if ('\u0000' in normalized) {
            return null
        }
        val prefixes = listOf("input text ", "/system/bin/input text ")
        val payload = prefixes.firstNotNullOfOrNull { prefix ->
            normalized.takeIf { it.startsWith(prefix) }?.substring(prefix.length)
        } ?: return null
        if (payload.isBlank()) {
            return null
        }
        return payload
            .trimMatchingQuotes()
            .replace("%s", " ")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
    }

    private fun runRemoteProcess(
        context: Context,
        command: String,
        stdin: ByteArray,
        appendTextNewline: Boolean,
        stream: BridgeStream
    ): BridgeResult {
        return runCatching {
            val remote = DeviceBridgeProcessBackend.startShell(context, command)
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val stdoutThread = drainAsync(remote.stdout, stdout, stream.stdout)
            val stderrThread = drainAsync(remote.stderr, stderr, stream.stderr)
            remote.stdin.use { output ->
                output.write(stdin)
                output.flush()
            }

            while (true) {
                val completed = runCatching {
                    remote.waitForTimeout(500L)
                }.getOrDefault(false)
                if (completed) break
                if (stream.cancel.exists()) {
                    runCatching { remote.destroy() }
                    return BridgeResult(130, stdout.toByteArray(), stderr.toByteArray() + "kf-host-self command cancelled\n".toByteArray())
                }
            }
            stdoutThread.join(1_000L)
            stderrThread.join(1_000L)
            BridgeResult(
                exitCode = remote.exitValue(),
                stdout = stdout.toByteArray().withTrailingNewlineIfNeeded(appendTextNewline),
                stderr = stderr.toByteArray().withTrailingNewlineIfNeeded(appendTextNewline)
            )
        }.getOrElse { error ->
            if (error is DeviceBridgeBackendException) {
                return BridgeResult(
                    error.exitCode,
                    ByteArray(0),
                    "kf-host-self backend unavailable: ${error.message}\n".toByteArray()
                )
            }
            BridgeResult(125, ByteArray(0), "kf-host-self bridge failed: ${error.javaClass.simpleName}: ${error.message}\n".toByteArray())
        }
    }

    private fun drainAsync(
        input: InputStream,
        output: ByteArrayOutputStream,
        streamFile: File
    ): Thread {
        return thread(start = true, isDaemon = true, name = "kf-adb-bridge-drain") {
            streamFile.parentFile?.mkdirs()
            input.use {
                FileOutputStream(streamFile, true).use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val remaining = MAX_OUTPUT_BYTES - total
                    if (remaining <= 0) break
                    val accepted = minOf(read, remaining)
                    output.write(buffer, 0, accepted)
                    stream.write(buffer, 0, accepted)
                    stream.flush()
                    total += accepted
                }
                }
            }
        }
    }

    private data class BridgeStream(
        val stdout: File,
        val stderr: File,
        val cancel: File
    ) {
        fun clear() {
            stdout.delete()
            stderr.delete()
            cancel.delete()
        }
    }

    private data class BridgeRequest(
        val id: String,
        val kind: String,
        val command: String,
        val remotePath: String,
        val payload: ByteArray
    ) {
        companion object {
            fun fromFile(file: File): BridgeRequest? {
                val values = file.readLines()
                    .mapNotNull { line ->
                        val index = line.indexOf('=')
                        if (index <= 0) return@mapNotNull null
                        line.substring(0, index) to line.substring(index + 1)
                    }
                    .toMap()
                val id = values["id"]?.takeIf { it.isNotBlank() } ?: return null
                val kind = values["kind"]?.takeIf { it.isNotBlank() } ?: "shell"
                val command = values["command_b64"]
                    ?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
                    .orEmpty()
                val remotePath = values["remote_b64"]
                    ?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
                    .orEmpty()
                val payload = values["data_b64"]
                    ?.let { Base64.decode(it, Base64.DEFAULT) }
                    ?: ByteArray(0)
                return BridgeRequest(
                    id = id,
                    kind = kind,
                    command = command,
                    remotePath = remotePath,
                    payload = payload
                )
            }
        }
    }

    private data class BridgeResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray
    ) {
        fun toResponseText(id: String): String {
            return buildString {
                appendLine("id=$id")
                appendLine("exit_code=$exitCode")
                appendLine("stdout_b64=${stdout.encodeBase64()}")
                appendLine("stderr_b64=${stderr.encodeBase64()}")
            }
        }
    }

    private fun ByteArray.encodeBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun ByteArray.withTrailingNewlineIfNeeded(enabled: Boolean): ByteArray {
        if (!enabled || isEmpty()) return this
        val last = last()
        return if (last == '\n'.code.toByte() || last == '\r'.code.toByte()) this else this + '\n'.code.toByte()
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun String.trimMatchingQuotes(): String {
        if (length < 2) return this
        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, length - 1)
        } else {
            this
        }
    }
}
