package com.kite.app.browser.automation

import java.lang.ref.WeakReference

object BrowserAutomationControllerRegistry {
    private val controllersBySession = linkedMapOf<String, WeakReference<BrowserAutomationController>>()
    private var latestSessionId: String? = null

    @Synchronized
    fun register(sessionId: String, controller: BrowserAutomationController) {
        if (sessionId.isBlank()) return
        pruneLocked()
        controllersBySession[sessionId] = WeakReference(controller)
        latestSessionId = sessionId
    }

    @Synchronized
    fun unregister(sessionId: String) {
        controllersBySession.remove(sessionId)
        if (latestSessionId == sessionId) {
            latestSessionId = controllersBySession.keys.lastOrNull()
        }
    }

    @Synchronized
    fun controllerFor(sessionId: String?): BrowserAutomationController? {
        if (sessionId.isNullOrBlank()) return null
        pruneLocked()
        return controllersBySession[sessionId]?.get()
    }

    @Synchronized
    fun latestController(): BrowserAutomationController? {
        pruneLocked()
        return latestSessionId?.let { controllersBySession[it]?.get() }
    }

    @Synchronized
    fun resetForTest() {
        controllersBySession.clear()
        latestSessionId = null
    }

    private fun pruneLocked() {
        val stale = controllersBySession
            .filterValues { it.get() == null }
            .keys
            .toList()
        stale.forEach { controllersBySession.remove(it) }
        if (latestSessionId !in controllersBySession.keys) {
            latestSessionId = controllersBySession.keys.lastOrNull()
        }
    }
}
