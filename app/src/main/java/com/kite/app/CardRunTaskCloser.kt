package com.kite.app

import java.util.concurrent.ConcurrentHashMap

object CardRunTaskCloser {
    private val closers = ConcurrentHashMap<String, () -> Unit>()

    fun register(instanceId: String, closer: () -> Unit) {
        if (instanceId.isBlank()) return
        closers[instanceId] = closer
    }

    fun unregister(instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        closers.remove(instanceId)
    }

    fun close(instanceId: String): Boolean {
        val closer = closers[instanceId] ?: return false
        closer()
        return true
    }
}
