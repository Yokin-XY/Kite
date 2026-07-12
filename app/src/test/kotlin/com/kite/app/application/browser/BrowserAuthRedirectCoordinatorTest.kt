package com.kite.app.application.browser

import com.kite.app.browser.BrowserAuthRedirect
import com.kite.app.browser.BrowserAuthCallbackChannelStatus
import com.kite.app.browser.BrowserAuthSession
import com.kite.app.browser.BrowserAuthSessionKind
import com.kite.app.browser.BrowserAuthSessionStatus
import com.kite.app.recipe.KiteRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAuthRedirectCoordinatorTest {
    @Test
    fun `非 Kite 回跳不触碰 Gateway`() {
        val gateway = FakeGateway()

        val result = BrowserAuthRedirectCoordinator(gateway).handle("https://example.com/callback")

        assertEquals(BrowserAuthRedirectResult.NotRedirect, result)
        assertTrue(gateway.events.isEmpty())
    }

    @Test
    fun `匹配失败只记录 unmatched`() {
        val gateway = FakeGateway(returnedSession = null)

        val result = BrowserAuthRedirectCoordinator(gateway).handle(successUrl())

        assertEquals(BrowserAuthRedirectResult.Unmatched, result)
        assertEquals(listOf("match", "unmatched"), gateway.events)
    }

    @Test
    fun `成功回跳先投影目标再标记 delivered`() {
        val gateway = FakeGateway(returnedSession = session())

        val result = BrowserAuthRedirectCoordinator(gateway).handle(successUrl())

        assertEquals(BrowserAuthRedirectResult.Delivered("recipe", "instance", false), result)
        assertEquals(
            listOf("match", "resolve", "project:false", "delivered", "record:false"),
            gateway.events
        )
    }

    @Test
    fun `错误回跳投影失败结果并保留提供方错误`() {
        val gateway = FakeGateway(returnedSession = session(status = BrowserAuthSessionStatus.Failed))

        val result = BrowserAuthRedirectCoordinator(gateway).handle(
            "kite-auth://callback?state=state-value&error=access_denied"
        )

        assertEquals(BrowserAuthRedirectResult.Delivered("recipe", "instance", true), result)
        assertEquals("access_denied", gateway.failedReason)
        assertEquals(
            listOf("match", "resolve", "project:true", "failed:access_denied", "record:true"),
            gateway.events
        )
    }

    @Test
    fun `新协调器实例仍可交付 Gateway 中的持久化 session`() {
        val gateway = FakeGateway(returnedSession = session())
        val reconstructedCoordinator = BrowserAuthRedirectCoordinator(gateway)

        val result = reconstructedCoordinator.handle(successUrl())

        assertTrue(result is BrowserAuthRedirectResult.Delivered)
        assertEquals("delivered", gateway.events[3])
    }

    @Test
    fun `恢复时停止过期 callback 且只确认同步成功的 session`() {
        val expiredCallback = session(id = "callback-expired", status = BrowserAuthSessionStatus.Expired)
        val forwardedOk = session(id = "forwarded-ok", kind = BrowserAuthSessionKind.CliLoopback)
        val forwardedRetry = session(id = "forwarded-retry", kind = BrowserAuthSessionKind.CliLoopback)
        val expiredRun = session(id = "expired-run", status = BrowserAuthSessionStatus.Expired)
        val gateway = FakeGateway().apply {
            expiredPending = listOf(expiredCallback)
            forwardedForSync = listOf(forwardedOk, forwardedRetry)
            expiredForSync = listOf(expiredRun)
            forwardedSyncResults["forwarded-ok"] = true
            forwardedSyncResults["forwarded-retry"] = false
            expiredSyncResults["expired-run"] = true
        }

        val summary = BrowserAuthRedirectCoordinator(gateway).reconcile()

        assertEquals(BrowserAuthReconcileSummary(1, 1, 1), summary)
        assertEquals(listOf("callback-expired"), gateway.stoppedCallbacks)
        assertEquals(listOf("forwarded-ok", "expired-run"), gateway.runtimeNotified)
    }

    private class FakeGateway(
        private val returnedSession: BrowserAuthSession? = session()
    ) : BrowserAuthRedirectGateway {
        val events = mutableListOf<String>()
        var failedReason: String? = null
        var projectSucceeded = true
        var target: BrowserAuthRedirectTarget? = BrowserAuthRedirectTarget(recipe(), "instance")
        var expiredPending = emptyList<BrowserAuthSession>()
        var forwardedForSync = emptyList<BrowserAuthSession>()
        var expiredForSync = emptyList<BrowserAuthSession>()
        val forwardedSyncResults = mutableMapOf<String, Boolean>()
        val expiredSyncResults = mutableMapOf<String, Boolean>()
        val stoppedCallbacks = mutableListOf<String>()
        val runtimeNotified = mutableListOf<String>()

        override fun matchReturned(redirect: BrowserAuthRedirect): BrowserAuthSession? {
            events += "match"
            return returnedSession
        }

        override fun resolveTarget(session: BrowserAuthSession): BrowserAuthRedirectTarget? {
            events += "resolve"
            return target
        }

        override fun projectDelivery(
            target: BrowserAuthRedirectTarget,
            session: BrowserAuthSession,
            redirect: BrowserAuthRedirect,
            failed: Boolean
        ): Boolean {
            events += "project:$failed"
            return projectSucceeded
        }

        override fun markDelivered(sessionId: String) {
            events += "delivered"
        }

        override fun markFailed(sessionId: String, reason: String) {
            failedReason = reason
            events += "failed:$reason"
        }

        override fun recordUnmatched(redirect: BrowserAuthRedirect) {
            events += "unmatched"
        }

        override fun recordMissingTarget(session: BrowserAuthSession) {
            events += "missing"
        }

        override fun recordDelivered(
            target: BrowserAuthRedirectTarget,
            session: BrowserAuthSession,
            redirect: BrowserAuthRedirect,
            failed: Boolean
        ) {
            events += "record:$failed"
        }

        override fun expirePending(): List<BrowserAuthSession> = expiredPending

        override fun stopCallback(sessionId: String) {
            stoppedCallbacks += sessionId
        }

        override fun forwardedNeedingRuntimeSync(): List<BrowserAuthSession> = forwardedForSync

        override fun synchronizeForwarded(session: BrowserAuthSession): Boolean =
            forwardedSyncResults[session.sessionId] ?: false

        override fun expiredNeedingRuntimeSync(): List<BrowserAuthSession> = expiredForSync

        override fun synchronizeExpired(session: BrowserAuthSession): Boolean =
            expiredSyncResults[session.sessionId] ?: false

        override fun markRuntimeNotified(sessionId: String) {
            runtimeNotified += sessionId
        }
    }

    private companion object {
        fun successUrl(): String = "kite-auth://callback?state=state-value&code=auth-code"

        fun recipe(): KiteRecipe = KiteRecipe(
            id = "recipe",
            name = "Recipe",
            description = "Test recipe",
            type = "web",
            defaultUrl = "",
            shortcut = false
        )

        fun session(
            id: String = "session",
            kind: BrowserAuthSessionKind = BrowserAuthSessionKind.AppRedirect,
            status: BrowserAuthSessionStatus = BrowserAuthSessionStatus.Returned
        ): BrowserAuthSession = BrowserAuthSession(
            sessionId = id,
            kind = kind,
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "test",
            originalUrl = "https://auth.example/authorize",
            requestKey = "request-key",
            redirectUri = "kite-auth://callback",
            state = "present",
            stateKey = "state-key",
            createdAt = 1L,
            expiresAt = Long.MAX_VALUE,
            status = status,
            callbackChannelStatus = BrowserAuthCallbackChannelStatus.Unprepared
        )
    }
}
