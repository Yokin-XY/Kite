package com.kite.app.foundation.devicebridge

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** Device Bridge 后端统一暴露的进程形状；调用方不接触 Shizuku Binder 或 su 细节。 */
interface DeviceBridgeProcess {
    val stdout: InputStream
    val stderr: InputStream
    val stdin: OutputStream

    fun waitForTimeout(timeoutMs: Long): Boolean
    fun exitValue(): Int
    fun destroy()
}

class DeviceBridgeBackendException(
    val exitCode: Int,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

enum class DeviceBridgeBackendMode(val storageValue: String) {
    Shizuku("shizuku"),
    RootExperimental("root_experimental");

    companion object {
        fun fromStorage(value: String?): DeviceBridgeBackendMode =
            entries.firstOrNull { it.storageValue == value } ?: Shizuku
    }
}

/**
 * 后端选择是用户显式状态。默认值永远是 Shizuku，读取状态不会尝试执行 su。
 * Root 在完成真实 Root 真机验收前只能通过明确的实验入口选中。
 */
object DeviceBridgeBackendModeStore {
    private const val PREFERENCES = "kite_device_bridge"
    private const val KEY_BACKEND_MODE = "backend_mode"

    fun current(context: Context): DeviceBridgeBackendMode = DeviceBridgeBackendMode.fromStorage(
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND_MODE, null)
    )

    fun select(context: Context, mode: DeviceBridgeBackendMode) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKEND_MODE, mode.storageValue)
            .apply()
    }
}

object DeviceBridgeProcessBackend {
    fun startShell(context: Context, command: String): DeviceBridgeProcess {
        require(command.isNotBlank()) { "Device Bridge command must not be blank" }
        return when (DeviceBridgeBackendModeStore.current(context)) {
            DeviceBridgeBackendMode.Shizuku -> startShizuku(context, command)
            DeviceBridgeBackendMode.RootExperimental -> RootDeviceBridgeBackend.startShell(command)
        }
    }

    private fun startShizuku(context: Context, command: String): DeviceBridgeProcess {
        val state = ShizukuBridgeStateOwner.current()
        if (state.lifecycle != DeviceBridgeLifecycleStatus.Ready) {
            val exitCode = if (
                state.lifecycle == DeviceBridgeLifecycleStatus.PermissionRequired ||
                state.lifecycle == DeviceBridgeLifecycleStatus.Revoked
            ) {
                DeviceBridgeContract.EXIT_PERMISSION_DENIED
            } else {
                DeviceBridgeContract.EXIT_BACKEND_UNAVAILABLE
            }
            throw DeviceBridgeBackendException(
                exitCode,
                "Shizuku backend is not ready: ${state.lifecycle}"
            )
        }
        return ShizukuProcess(
            ShizukuUserServiceProcessClient.startShell(context.applicationContext, command)
        )
    }

    private class ShizukuProcess(
        private val delegate: ShizukuUserServiceProcessClient.RemoteProcess,
    ) : DeviceBridgeProcess {
        override val stdout: InputStream = ParcelFileDescriptor.AutoCloseInputStream(delegate.inputStream)
        override val stderr: InputStream = ParcelFileDescriptor.AutoCloseInputStream(delegate.errorStream)
        override val stdin: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(delegate.outputStream)

        override fun waitForTimeout(timeoutMs: Long): Boolean = delegate.waitForTimeout(timeoutMs)
        override fun exitValue(): Int = delegate.exitValue()
        override fun destroy() = delegate.destroy()
    }
}

data class RootBridgeProbe(
    val lifecycle: DeviceBridgeLifecycleStatus,
    val identity: DeviceBridgeIdentity,
    val uid: Int?,
    val exitCode: Int,
    val detail: String,
)

internal object RootBridgeProbeClassifier {
    fun classify(
        processExitCode: Int?,
        stdout: String,
        stderr: String,
        timedOut: Boolean = false,
        startFailure: Throwable? = null,
    ): RootBridgeProbe {
        if (startFailure != null) {
            return RootBridgeProbe(
                DeviceBridgeLifecycleStatus.Unavailable,
                DeviceBridgeIdentity.Unknown,
                null,
                DeviceBridgeContract.EXIT_BACKEND_UNAVAILABLE,
                "su_start_failed:${startFailure.javaClass.simpleName}"
            )
        }
        if (timedOut) {
            return RootBridgeProbe(
                DeviceBridgeLifecycleStatus.Failed,
                DeviceBridgeIdentity.Unknown,
                null,
                DeviceBridgeContract.EXIT_TIMEOUT,
                "su_probe_timeout"
            )
        }
        val uid = stdout.lineSequence().map(String::trim).firstNotNullOfOrNull(String::toIntOrNull)
        if (processExitCode == 0 && uid == 0) {
            return RootBridgeProbe(
                DeviceBridgeLifecycleStatus.Ready,
                DeviceBridgeIdentity.Root,
                uid,
                DeviceBridgeContract.EXIT_OK,
                "uid=0"
            )
        }
        val detail = stderr.trim().ifBlank { stdout.trim() }
        val permissionDenied = detail.contains("denied", ignoreCase = true) ||
            detail.contains("not allowed", ignoreCase = true) ||
            detail.contains("cancel", ignoreCase = true)
        return RootBridgeProbe(
            lifecycle = if (permissionDenied) {
                DeviceBridgeLifecycleStatus.PermissionRequired
            } else {
                DeviceBridgeLifecycleStatus.Failed
            },
            identity = DeviceBridgeIdentity.Unknown,
            uid = uid,
            exitCode = if (permissionDenied) {
                DeviceBridgeContract.EXIT_PERMISSION_DENIED
            } else {
                DeviceBridgeContract.EXIT_BACKEND_UNAVAILABLE
            },
            detail = detail.ifBlank { "su_probe_failed:exit=$processExitCode uid=$uid" }
        )
    }
}

/** 实验性 Root 后端。只有用户已明确选中 RootExperimental 时才会进入这里。 */
object RootDeviceBridgeBackend {
    private const val PROBE_TIMEOUT_MS = 3_000L

    fun probe(): RootBridgeProbe {
        val process = runCatching {
            ProcessBuilder("su", "-c", "id -u").start()
        }.getOrElse { error ->
            return RootBridgeProbeClassifier.classify(null, "", "", startFailure = error)
        }
        val completed = runCatching {
            process.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!completed) {
            process.destroyForcibly()
            return RootBridgeProbeClassifier.classify(null, "", "", timedOut = true)
        }
        return RootBridgeProbeClassifier.classify(
            processExitCode = process.exitValue(),
            stdout = process.inputStream.bufferedReader().use { it.readText() },
            stderr = process.errorStream.bufferedReader().use { it.readText() }
        )
    }

    fun startShell(command: String): DeviceBridgeProcess {
        val probe = probe()
        if (probe.lifecycle != DeviceBridgeLifecycleStatus.Ready) {
            throw DeviceBridgeBackendException(probe.exitCode, "Root backend is not ready: ${probe.detail}")
        }
        val process = runCatching {
            ProcessBuilder("su", "-c", command).start()
        }.getOrElse { error ->
            throw DeviceBridgeBackendException(
                DeviceBridgeContract.EXIT_BACKEND_UNAVAILABLE,
                "Cannot start Root backend",
                error
            )
        }
        return LocalRootProcess(process)
    }

    private class LocalRootProcess(private val process: Process) : DeviceBridgeProcess {
        override val stdout: InputStream get() = process.inputStream
        override val stderr: InputStream get() = process.errorStream
        override val stdin: OutputStream get() = process.outputStream

        override fun waitForTimeout(timeoutMs: Long): Boolean =
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

        override fun exitValue(): Int = process.exitValue()

        override fun destroy() {
            process.destroy()
            if (!runCatching { process.waitFor(500L, TimeUnit.MILLISECONDS) }.getOrDefault(false)) {
                process.destroyForcibly()
            }
        }
    }
}
