package com.kite.app.run

import com.kite.app.bridge.KiteDesktopOpenRequest
import java.util.concurrent.ConcurrentHashMap

object CardRunDesktopRouter {
    private val handlers = ConcurrentHashMap<String, (KiteDesktopOpenRequest) -> Boolean>()
    private val pending = ConcurrentHashMap<String, KiteDesktopOpenRequest>()

    fun register(instanceId: String, handler: (KiteDesktopOpenRequest) -> Boolean) {
        if (instanceId.isBlank()) return
        handlers[instanceId] = handler
        pending.remove(instanceId)?.let { request ->
            if (!handler(request)) {
                pending[instanceId] = request
            }
        }
    }

    fun unregister(instanceId: String?) {
        if (!instanceId.isNullOrBlank()) {
            handlers.remove(instanceId)
        }
    }

    fun dispatch(request: KiteDesktopOpenRequest): Boolean {
        val instanceId = request.instanceId?.takeIf { it.isNotBlank() } ?: return false
        val handler = handlers[instanceId]
        if (handler != null && handler(request)) {
            pending.remove(instanceId)
            return true
        }
        pending[instanceId] = request
        return false
    }

    fun consumePending(instanceId: String): KiteDesktopOpenRequest? =
        pending.remove(instanceId)
}
