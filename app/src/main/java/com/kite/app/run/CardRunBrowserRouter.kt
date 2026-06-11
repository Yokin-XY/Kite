package com.kite.app.run

import com.kite.app.bridge.KiteBrowserOpenRequest
import java.util.concurrent.ConcurrentHashMap

object CardRunBrowserRouter {
    private val handlers = ConcurrentHashMap<String, (KiteBrowserOpenRequest) -> Boolean>()
    private val pending = ConcurrentHashMap<String, KiteBrowserOpenRequest>()

    fun register(instanceId: String, handler: (KiteBrowserOpenRequest) -> Boolean) {
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

    fun dispatch(request: KiteBrowserOpenRequest): Boolean {
        val instanceId = request.instanceId?.takeIf { it.isNotBlank() } ?: return false
        val handler = handlers[instanceId]
        if (handler != null && handler(request)) {
            pending.remove(instanceId)
            return true
        }
        pending[instanceId] = request
        return false
    }

    fun consumePending(instanceId: String): KiteBrowserOpenRequest? =
        pending.remove(instanceId)
}
