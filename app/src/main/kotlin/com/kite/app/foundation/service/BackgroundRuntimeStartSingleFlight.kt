package com.kite.app.foundation.service

import java.util.concurrent.atomic.AtomicBoolean

internal class BackgroundRuntimeStartLease internal constructor(
    val runtimeId: String,
    private val token: Long,
    private val release: (String, Long) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release(runtimeId, token)
    }
}

/**
 * 合并同一 runtime 的并发启动请求，但允许不同 runtime 并行准备。
 *
 * lease 带代次；环境切换清除旧记录后，迟到的旧 lease 关闭不会误删新一代启动。
 */
internal class BackgroundRuntimeStartSingleFlight {
    private val lock = Any()
    private val inFlight = linkedMapOf<String, Long>()
    private var nextToken = 0L

    fun tryAcquire(runtimeId: String): BackgroundRuntimeStartLease? {
        require(runtimeId.isNotBlank()) { "background_runtime_id_blank" }
        synchronized(lock) {
            if (runtimeId in inFlight) return null
            val token = ++nextToken
            inFlight[runtimeId] = token
            return BackgroundRuntimeStartLease(runtimeId, token, ::release)
        }
    }

    fun forget(runtimeId: String) {
        synchronized(lock) {
            inFlight.remove(runtimeId)
        }
    }

    fun isInFlight(runtimeId: String): Boolean = synchronized(lock) {
        runtimeId in inFlight
    }

    private fun release(runtimeId: String, token: Long) {
        synchronized(lock) {
            if (inFlight[runtimeId] == token) inFlight.remove(runtimeId)
        }
    }
}
