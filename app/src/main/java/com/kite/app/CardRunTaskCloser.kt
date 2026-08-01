package com.kite.app

import java.util.concurrent.ConcurrentHashMap

object CardRunTaskCloser {
    private data class Key(val instanceId: String, val generation: Long)

    private val closers = ConcurrentHashMap<Key, () -> Unit>()

    fun register(instanceId: String, generation: Long, closer: () -> Unit) {
        if (instanceId.isBlank() || generation <= 0L) return
        closers[Key(instanceId, generation)] = closer
    }

    fun unregister(instanceId: String?, generation: Long?) {
        if (instanceId.isNullOrBlank() || generation == null || generation <= 0L) return
        closers.remove(Key(instanceId, generation))
    }

    fun close(instanceId: String, generation: Long): Boolean {
        if (instanceId.isBlank() || generation <= 0L) return false
        val closer = closers[Key(instanceId, generation)] ?: return false
        closer()
        return true
    }
}
