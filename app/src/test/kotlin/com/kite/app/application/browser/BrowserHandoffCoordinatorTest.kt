package com.kite.app.application.browser

import com.kite.app.browser.BrowserAuthCallbackChannelStatus
import com.kite.app.browser.BrowserAuthSession
import com.kite.app.browser.BrowserAuthSessionKind
import com.kite.app.browser.BrowserAuthSessionStatus
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHandoffCoordinatorTest {
    @Test
    fun `existing pending session is reused without opening browser`() {
        val gateway = FakeGateway().apply { pending = session("existing") }
        val result = BrowserHandoffCoordinator(gateway).launch(request(), decision())

        assertTrue(result is BrowserHandoffLaunchResult.Reused)
        assertEquals(listOf("find"), gateway.calls)
    }

    @Test
    fun `new handoff writes waiting state before callback and browser open`() {
        val gateway = FakeGateway()
        val result = BrowserHandoffCoordinator(gateway).launch(request(), decision())

        assertTrue(result is BrowserHandoffLaunchResult.Opened)
        assertEquals(listOf("find", "create", "waiting", "prepare", "open", "record"), gateway.calls)
    }

    @Test
    fun `failed browser open closes callback channel and marks session failed`() {
        val gateway = FakeGateway().apply { openResult = false }
        val result = BrowserHandoffCoordinator(gateway).launch(request(), decision())

        assertTrue(result is BrowserHandoffLaunchResult.Failed)
        assertFalse(result.accepted)
        assertEquals(listOf("find", "create", "waiting", "prepare", "open", "fail"), gateway.calls)
    }

    @Test
    fun `non handoff decision is rejected without gateway access`() {
        val gateway = FakeGateway()
        val result = BrowserHandoffCoordinator(gateway).launch(
            request(),
            BrowserHandoffDecision.StayInWebView
        )

        assertTrue(result is BrowserHandoffLaunchResult.Rejected)
        assertTrue(gateway.calls.isEmpty())
    }

    private fun request() = BrowserHandoffRequest(
        url = "https://example.com/oauth/authorize?response_type=code&client_id=test&redirect_uri=http%3A%2F%2F127.0.0.1%3A1455%2Fcallback&state=s",
        recipeId = "recipe",
        recipeName = "Recipe",
        instanceId = "run",
        source = "terminal_step"
    )

    private fun decision() = BrowserHandoffDecision.StartCliCallbackHandoff(
        redirectUri = "http://127.0.0.1:1455/callback",
        state = "s"
    )

    private fun session(id: String) = BrowserAuthSession(
        sessionId = id,
        kind = BrowserAuthSessionKind.CliLoopback,
        recipeId = "recipe",
        recipeName = "Recipe",
        instanceId = "run",
        source = "terminal_step",
        originalUrl = "redacted",
        requestKey = "key",
        redirectUri = "http://127.0.0.1:1455/callback",
        state = "present",
        stateKey = "state-key",
        createdAt = 1L,
        expiresAt = Long.MAX_VALUE,
        status = BrowserAuthSessionStatus.Pending,
        callbackChannelStatus = BrowserAuthCallbackChannelStatus.Unprepared
    )

    private inner class FakeGateway : BrowserHandoffGateway {
        val calls = mutableListOf<String>()
        var pending: BrowserAuthSession? = null
        var openResult = true
        private val created = session("created")

        override fun findPending(request: BrowserHandoffRequest): BrowserAuthSession? {
            calls += "find"
            return pending
        }

        override fun createPending(
            request: BrowserHandoffRequest,
            decision: BrowserHandoffDecision
        ): BrowserAuthSession {
            calls += "create"
            return created
        }

        override fun updateWaiting(
            session: BrowserAuthSession,
            request: BrowserHandoffRequest
        ): BrowserHandoffTargetUpdate? {
            calls += "waiting"
            return null
        }

        override fun prepareCallback(session: BrowserAuthSession): BrowserHandoffCallbackPreparation {
            calls += "prepare"
            return BrowserHandoffCallbackPreparation("Direct", 1455)
        }

        override fun openExternal(url: String): Boolean {
            calls += "open"
            return openResult
        }

        override fun recordOpened(
            session: BrowserAuthSession,
            request: BrowserHandoffRequest,
            preparation: BrowserHandoffCallbackPreparation?
        ) {
            calls += "record"
        }

        override fun fail(session: BrowserAuthSession, reason: String) {
            calls += "fail"
        }
    }
}
