package com.kite.app.foundation.runtime

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * Protects the Android control plane from userland storage pressure.
 *
 * This guard does not clean Ubuntu/user files. It only keeps a tiny KF-owned
 * reserve and degrades KF-owned writes when the app data filesystem is close to
 * full, so terminal/control-plane failures are explicit instead of crashy.
 */
object RuntimeStorageGuard {
    private const val RESERVE_FILE_NAME = ".kf-control-plane.reserve"
    private const val RESERVE_BYTES = 8L * 1024L * 1024L
    private const val CRITICAL_USABLE_BYTES = 48L * 1024L * 1024L
    private const val WATCH_USABLE_BYTES = 256L * 1024L * 1024L

    enum class State {
        OK,
        WATCH,
        CRITICAL
    }

    data class Snapshot(
        val state: State,
        val usableBytes: Long,
        val totalBytes: Long,
        val reserveReleased: Boolean,
        val reason: String
    ) {
        val isCritical: Boolean get() = state == State.CRITICAL

        fun userMessage(): String {
            val usableMiB = usableBytes / 1024L / 1024L
            return when (state) {
                State.OK -> "存储空间正常。"
                State.WATCH -> "KF 可写空间偏低，剩余约 ${usableMiB}MiB；现有会话可继续，建议减少 Ubuntu 内部临时文件。"
                State.CRITICAL -> "KF 可写空间严重不足，剩余约 ${usableMiB}MiB；已阻止新终端启动以避免闪退。"
            }
        }
    }

    fun snapshot(context: Context, reason: String): Snapshot {
        val runtimeRoot = File(context.filesDir, "runtime")
        runtimeRoot.mkdirs()
        val reserveFile = File(runtimeRoot, RESERVE_FILE_NAME)
        var reserveReleased = false

        var usable = runtimeRoot.usableSpace
        if (usable < CRITICAL_USABLE_BYTES && reserveFile.exists()) {
            reserveReleased = reserveFile.delete()
            usable = runtimeRoot.usableSpace
        } else if (usable > WATCH_USABLE_BYTES + RESERVE_BYTES && !reserveFile.exists()) {
            createReserveFile(reserveFile)
            usable = runtimeRoot.usableSpace
        }

        val state = when {
            usable < CRITICAL_USABLE_BYTES -> State.CRITICAL
            usable < WATCH_USABLE_BYTES -> State.WATCH
            else -> State.OK
        }
        return Snapshot(
            state = state,
            usableBytes = usable,
            totalBytes = runtimeRoot.totalSpace,
            reserveReleased = reserveReleased,
            reason = reason
        )
    }

    fun canStartNewRuntime(context: Context, reason: String): Snapshot {
        return snapshot(context, reason)
    }

    fun safeWriteText(context: Context, file: File, text: String, reason: String): Boolean {
        val storage = snapshot(context, reason)
        if (storage.isCritical) {
            return false
        }
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text)
            true
        }.getOrDefault(false)
    }

    fun safeAppendBounded(
        context: Context,
        file: File,
        text: String,
        maxBytes: Long,
        reason: String
    ): Boolean {
        val storage = snapshot(context, reason)
        if (storage.isCritical) {
            return false
        }
        return runCatching {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > maxBytes) {
                val oldFile = File(file.parentFile, "${file.name}.old")
                if (oldFile.exists()) {
                    oldFile.delete()
                }
                file.renameTo(oldFile)
            }
            file.appendText(text)
            true
        }.getOrDefault(false)
    }

    private fun createReserveFile(file: File) {
        runCatching {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(RESERVE_BYTES)
            }
        }
    }
}
