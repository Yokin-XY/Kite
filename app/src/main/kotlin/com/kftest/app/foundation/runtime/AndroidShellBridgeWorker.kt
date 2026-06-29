package com.kftest.app.foundation.runtime

import android.content.Context
import android.util.Base64
import com.kftest.app.foundation.logging.Logger
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
 * Plain Android shell bridge for Ubuntu-side commands.
 *
 * This runs as the app user through /system/bin/sh. It is intentionally not
 * ADB, root, or Shizuku; commands get exactly the permissions Android grants
 * this APK process.
 */
object AndroidShellBridgeWorker {
    private const val LOG_TAG = "AndroidShellBridge"
    private const val BRIDGE_DIR = ".kf/android-shell-bridge"
    private const val REQUEST_DIR = "requests"
    private const val PROCESSING_DIR = "processing"
    private const val RESPONSE_DIR = "responses"
    private const val POLL_INTERVAL_MS = 180L
    private const val MAX_OUTPUT_BYTES = 16 * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var workerJob: Job? = null

    fun start(context: Context) {
        if (workerJob?.isActive == true) return
        val appContext = context.applicationContext
        workerJob = scope.launch {
            while (isActive) {
                runCatching { pollOnce(appContext) }
                    .onFailure { Logger.e(LOG_TAG, "android shell bridge poll failed: ${it.message}") }
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
        val request = ShellRequest.fromFile(requestFile)
        if (request == null) {
            requestFile.delete()
            return
        }

        val stream = ShellStream(
            stdout = File(responseDir, "${request.id}.stdout.stream"),
            stderr = File(responseDir, "${request.id}.stderr.stream"),
            cancel = File(responseDir, "${request.id}.cancel")
        )
        stream.clear()

        val result = when (request.mode) {
            "command" -> executeCommand(request.command, request.environment, stream)
            "script" -> executeScript(context, request, stream)
            else -> ShellResult(125, ByteArray(0), "unsupported kf-android-sh mode: ${request.mode}\n".toByteArray())
        }

        val response = File(responseDir, "${request.id}.resp")
        val temp = File(responseDir, "${request.id}.resp.tmp")
        temp.writeText(result.toResponseText(request.id))
        temp.renameTo(response)
        requestFile.delete()
    }

    private fun executeCommand(command: String, environment: Map<String, String>, stream: ShellStream): ShellResult {
        if (command.isBlank()) {
            return ShellResult(125, ByteArray(0), "kf-android-sh requires a command or script path\n".toByteArray())
        }
        return runProcess(ProcessBuilder("/system/bin/sh", "-c", command), environment, tempFile = null, stream)
    }

    private fun executeScript(context: Context, request: ShellRequest, stream: ShellStream): ShellResult {
        if (request.script.isEmpty()) {
            return ShellResult(125, ByteArray(0), "kf-android-sh script request is empty\n".toByteArray())
        }
        val scriptFile = File.createTempFile("kf-android-sh-${request.id}-", ".sh", context.cacheDir)
        return try {
            scriptFile.writeBytes(request.script)
            runProcess(ProcessBuilder("/system/bin/sh", scriptFile.absolutePath), request.environment, scriptFile, stream)
        } finally {
            scriptFile.delete()
        }
    }

    private fun runProcess(
        processBuilder: ProcessBuilder,
        environment: Map<String, String>,
        tempFile: File?,
        stream: ShellStream
    ): ShellResult {
        return runCatching {
            processBuilder.directory(File("/"))
            processBuilder.environment().putAll(environment)
            val process = processBuilder.start()
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val stdoutThread = drainAsync(process.inputStream, stdout, stream.stdout)
            val stderrThread = drainAsync(process.errorStream, stderr, stream.stderr)
            while (true) {
                if (process.waitFor(500L, TimeUnit.MILLISECONDS)) break
                if (stream.cancel.exists()) {
                    runCatching { process.destroy() }
                    return ShellResult(
                        130,
                        stdout.toByteArray(),
                        stderr.toByteArray() + "kf-android-sh command cancelled\n".toByteArray()
                    )
                }
            }
            stdoutThread.join(1_000L)
            stderrThread.join(1_000L)
            ShellResult(process.exitValue(), stdout.toByteArray(), stderr.toByteArray())
        }.getOrElse { error ->
            tempFile?.delete()
            ShellResult(125, ByteArray(0), "kf-android-sh failed: ${error.javaClass.simpleName}: ${error.message}\n".toByteArray())
        }
    }

    private fun drainAsync(input: InputStream, output: ByteArrayOutputStream, streamFile: File): Thread =
        thread(start = true, isDaemon = true, name = "kf-android-sh-drain") {
            streamFile.parentFile?.mkdirs()
            input.use {
                FileOutputStream(streamFile, true).use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = it.read(buffer)
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

    private data class ShellStream(
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

    private data class ShellRequest(
        val id: String,
        val mode: String,
        val command: String,
        val script: ByteArray,
        val environment: Map<String, String>
    ) {
        companion object {
            fun fromFile(file: File): ShellRequest? {
                val values = file.readLines()
                    .mapNotNull { line ->
                        val index = line.indexOf('=')
                        if (index <= 0) return@mapNotNull null
                        line.substring(0, index) to line.substring(index + 1)
                    }
                    .toMap()
                val id = values["id"]?.takeIf { it.isNotBlank() } ?: return null
                val mode = values["mode"]?.takeIf { it.isNotBlank() } ?: "command"
                val command = values["command_b64"]?.decodeBase64Text().orEmpty()
                val script = values["script_b64"]?.let { Base64.decode(it, Base64.DEFAULT) } ?: ByteArray(0)
                val env = values["env_b64"].decodeEnvironment()
                return ShellRequest(id, mode, command, script, env)
            }

            private fun String?.decodeEnvironment(): Map<String, String> =
                this?.decodeBase64Text()
                    ?.lineSequence()
                    ?.mapNotNull { line ->
                        val index = line.indexOf('=')
                        if (index <= 0) return@mapNotNull null
                        line.substring(0, index) to line.substring(index + 1)
                    }
                    ?.toMap()
                    .orEmpty()
        }
    }

    private data class ShellResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray
    ) {
        fun toResponseText(id: String): String = buildString {
            appendLine("id=$id")
            appendLine("exit_code=$exitCode")
            appendLine("stdout_b64=${stdout.encodeBase64()}")
            appendLine("stderr_b64=${stderr.encodeBase64()}")
        }
    }

    private fun String.decodeBase64Text(): String =
        Base64.decode(this, Base64.DEFAULT).toString(Charsets.UTF_8)

    private fun ByteArray.encodeBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)
}
