package com.kite.app.foundation.devicebridge

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** 在 Shizuku/Sui 身份下运行的正式权限代理。 */
@Keep
class KiteShizukuUserService : IKiteDeviceBridgeService.Stub {
    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    private data class RunningProcess(
        val process: Process,
        val cancelled: AtomicBoolean,
        val stdin: ParcelFileDescriptor,
        val stdout: ParcelFileDescriptor,
        val stderr: ParcelFileDescriptor,
        val status: ParcelFileDescriptor,
        val ownsProcessGroup: Boolean
    )

    private val processes = ConcurrentHashMap<String, RunningProcess>()

    override fun getProtocolVersion(): Int = DeviceBridgeContract.PROTOCOL_VERSION

    override fun getServiceUid(): Int = Os.getuid()

    override fun startProcess(
        requestId: String?,
        argv: Array<out String>?,
        environment: Array<out String>?,
        workingDirectory: String?,
        stdin: ParcelFileDescriptor?,
        stdout: ParcelFileDescriptor?,
        stderr: ParcelFileDescriptor?,
        status: ParcelFileDescriptor?
    ): Int {
        val safeRequestId = requestId?.takeIf { it.matches(REQUEST_ID_PATTERN) }
            ?: return rejectDescriptors(
                DeviceBridgeContract.EXIT_INVALID_REQUEST,
                stdin,
                stdout,
                stderr,
                status
            )
        val safeArgv = argv?.toList().orEmpty()
        if (safeArgv.isEmpty() || safeArgv.any { '\u0000' in it }) {
            return rejectDescriptors(DeviceBridgeContract.EXIT_INVALID_REQUEST, stdin, stdout, stderr, status)
        }
        if (stdin == null || stdout == null || stderr == null || status == null) {
            return rejectDescriptors(DeviceBridgeContract.EXIT_INVALID_REQUEST, stdin, stdout, stderr, status)
        }
        if (processes.containsKey(safeRequestId)) {
            return rejectDescriptors(DeviceBridgeContract.EXIT_INVALID_REQUEST, stdin, stdout, stderr, status)
        }

        val ownsProcessGroup = File(SETSID_PATH).canExecute()
        val launchArgv = if (ownsProcessGroup) listOf(SETSID_PATH) + safeArgv else safeArgv
        val process = runCatching {
            ProcessBuilder(launchArgv)
                .apply {
                    directory(resolveWorkingDirectory(workingDirectory))
                    redirectErrorStream(false)
                    applyEnvironment(environment.orEmpty())
                }
                .start()
        }.getOrElse { error ->
            Log.e(LOG_TAG, "start process failed request=$safeRequestId", error)
            return rejectDescriptors(DeviceBridgeContract.EXIT_TRANSPORT_ERROR, stdin, stdout, stderr, status)
        }

        val running = RunningProcess(
            process = process,
            cancelled = AtomicBoolean(false),
            stdin = stdin,
            stdout = stdout,
            stderr = stderr,
            status = status,
            ownsProcessGroup = ownsProcessGroup
        )
        val previous = processes.putIfAbsent(safeRequestId, running)
        if (previous != null) {
            process.destroyForcibly()
            return rejectDescriptors(DeviceBridgeContract.EXIT_INVALID_REQUEST, stdin, stdout, stderr, status)
        }

        startPump("stdin-$safeRequestId") {
            ParcelFileDescriptor.AutoCloseInputStream(stdin).use { source ->
                process.outputStream.use { sink -> source.copyTo(sink) }
            }
        }
        val stdoutThread = startPump("stdout-$safeRequestId") {
            process.inputStream.use { source ->
                ParcelFileDescriptor.AutoCloseOutputStream(stdout).use { sink -> source.copyTo(sink) }
            }
        }
        val stderrThread = startPump("stderr-$safeRequestId") {
            process.errorStream.use { source ->
                ParcelFileDescriptor.AutoCloseOutputStream(stderr).use { sink -> source.copyTo(sink) }
            }
        }
        thread(start = true, isDaemon = true, name = "kite-device-wait-$safeRequestId") {
            val exitCode = runCatching { process.waitFor() }
                .getOrDefault(DeviceBridgeContract.EXIT_TRANSPORT_ERROR)
            stdoutThread.join(STREAM_JOIN_TIMEOUT_MS)
            stderrThread.join(STREAM_JOIN_TIMEOUT_MS)
            val finalStatus = if (running.cancelled.get()) {
                KiteDeviceProcessStatus.cancelled()
            } else {
                KiteDeviceProcessStatus.completed(exitCode)
            }
            writeStatus(status, finalStatus)
            processes.remove(safeRequestId, running)
        }
        return DeviceBridgeContract.EXIT_OK
    }

    override fun cancelProcess(requestId: String?): Boolean {
        val running = requestId?.let(processes::get) ?: return false
        running.cancelled.set(true)
        terminate(running)
        closeQuietly(running.stdin)
        return true
    }

    override fun destroy() {
        processes.values.forEach { running ->
            running.cancelled.set(true)
            terminate(running)
            closeQuietly(running.stdin)
            closeQuietly(running.stdout)
            closeQuietly(running.stderr)
            closeQuietly(running.status)
        }
        processes.clear()
        exitProcess(0)
    }

    private fun ProcessBuilder.applyEnvironment(entries: Array<out String>) {
        entries.forEach { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) return@forEach
            val key = entry.substring(0, separator)
            val value = entry.substring(separator + 1)
            if (ENVIRONMENT_KEY_PATTERN.matches(key) && '\u0000' !in value) {
                environment()[key] = value
            }
        }
    }

    private fun resolveWorkingDirectory(value: String?): File {
        val candidate = value?.takeIf { it.startsWith('/') && '\u0000' !in it }?.let(::File)
        return candidate?.takeIf { it.isDirectory } ?: File("/")
    }

    private fun startPump(name: String, block: () -> Unit): Thread = thread(
        start = true,
        isDaemon = true,
        name = "kite-device-$name"
    ) {
        runCatching(block).onFailure { error ->
            Log.w(LOG_TAG, "$name stream closed: ${error.message}")
        }
    }

    private fun terminate(running: RunningProcess) {
        val pid = processId(running.process)
        if (running.ownsProcessGroup && pid != null) {
            runCatching { Os.kill(-pid, OsConstants.SIGTERM) }
        } else {
            runCatching { running.process.destroy() }
        }
        if (!running.process.isAlive) return
        Thread.sleep(TERM_GRACE_MS)
        if (!running.process.isAlive) return
        if (running.ownsProcessGroup && pid != null) {
            runCatching { Os.kill(-pid, OsConstants.SIGKILL) }
        } else {
            runCatching { running.process.destroyForcibly() }
        }
    }

    private fun processId(process: Process): Int? {
        runCatching {
            // Android 的 java.lang.Process#pid 在部分编译桩中不可见。
            (process.javaClass.getMethod("pid").invoke(process) as Number).toInt()
        }.getOrNull()?.let { return it }

        var type: Class<*>? = process.javaClass
        while (type != null) {
            val currentType = type
            runCatching {
                currentType.getDeclaredField("pid").apply { isAccessible = true }.get(process) as Number
            }.getOrNull()?.toInt()?.let { return it }
            type = currentType.superclass
        }

        return Regex("pid=(\\d+)")
            .find(process.toString())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun writeStatus(descriptor: ParcelFileDescriptor, value: KiteDeviceProcessStatus) {
        runCatching {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                output.write(value.encode().toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }.onFailure { error ->
            Log.w(LOG_TAG, "status stream closed: ${error.message}")
        }
    }

    private fun rejectDescriptors(
        exitCode: Int,
        stdin: ParcelFileDescriptor?,
        stdout: ParcelFileDescriptor?,
        stderr: ParcelFileDescriptor?,
        status: ParcelFileDescriptor?
    ): Int {
        closeQuietly(stdin)
        closeQuietly(stdout)
        closeQuietly(stderr)
        closeQuietly(status)
        return exitCode
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        runCatching { descriptor?.close() }
    }

    companion object {
        private const val LOG_TAG = "KiteShizukuService"
        private const val STREAM_JOIN_TIMEOUT_MS = 2_000L
        private const val TERM_GRACE_MS = 200L
        private const val SETSID_PATH = "/system/bin/setsid"
        private val REQUEST_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,96}")
        private val ENVIRONMENT_KEY_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
