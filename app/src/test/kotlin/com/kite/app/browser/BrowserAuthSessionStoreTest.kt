package com.kite.app.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserAuthSessionStoreTest {
    private val context by lazy {
        ApplicationProvider.getApplicationContext<Context>()
    }

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("kite_browser_auth_sessions", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun expirePendingReturnsOnlyNewlyExpiredSessions() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createCliLoopbackSession(now = now)

        assertTrue(store.expirePending(now = now + SESSION_TTL_MS).isEmpty())

        val expired = store.expirePending(now = now + SESSION_TTL_MS + 1)

        assertEquals(1, expired.size)
        assertEquals(session.sessionId, expired.single().sessionId)
        assertEquals(BrowserAuthSessionStatus.Expired, expired.single().status)
        assertEquals("expired", expired.single().failureReason)
        assertEquals(session.sessionId, store.expiredNeedingRuntimeSync().single().sessionId)

        store.markRuntimeNotified(session.sessionId, now = now + SESSION_TTL_MS + 2)

        assertTrue(store.expiredNeedingRuntimeSync().isEmpty())
        assertTrue(store.expirePending(now = now + SESSION_TTL_MS + 2).isEmpty())
    }

    @Test
    fun markReturnedAcceptsMatchingAppRedirectSession() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createAppRedirectSession(state = "app-state", now = now)
        val redirect = BrowserAuthRedirectParser.parse("kite-auth://callback?code=ok&state=app-state")

        val returned = store.markReturned(checkNotNull(redirect), now = now + 1)

        checkNotNull(returned)
        assertEquals(session.sessionId, returned.sessionId)
        assertEquals(BrowserAuthSessionKind.AppRedirect, returned.kind)
        assertEquals(BrowserAuthSessionStatus.Returned, returned.status)
        assertEquals("kite-auth://callback?code=present&state=present", returned.returnedUrl)
        assertNull(returned.failureReason)
    }

    @Test
    fun createPendingPersistsRedactedOriginalUrlAndRequestKey() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val secretState = "secret-state"
        val secretChallenge = "secret-code-challenge"
        val rawUrl = appRedirectUrl(state = secretState, codeChallenge = secretChallenge)

        val session = store.createAppRedirectSession(state = secretState, url = rawUrl, now = now)

        assertEquals(BrowserHandoffPolicy.redactedUrlForDiagnostics(rawUrl), session.originalUrl)
        assertFalse(session.originalUrl.contains(secretState))
        assertFalse(session.originalUrl.contains(secretChallenge))
        assertTrue(session.requestKey.isNotBlank())
        assertFalse(session.requestKey.contains(secretState))
        assertFalse(session.requestKey.contains(secretChallenge))
        val persisted = context.getSharedPreferences("kite_browser_auth_sessions", Context.MODE_PRIVATE)
            .getString("sessions_v1", "")
            .orEmpty()
        assertFalse(persisted.contains(secretState))
        assertFalse(persisted.contains(secretChallenge))
        assertTrue(persisted.contains("\"requestKey\""))

        val pending = store.findPending(instanceId = "instance", originalUrl = rawUrl, now = now + 1)
        checkNotNull(pending)
        assertEquals(session.sessionId, pending.sessionId)
    }

    @Test
    fun findPendingSeparatesOauthRequestsWithDifferentState() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val firstUrl = appRedirectUrl(state = "state-one")
        val secondUrl = appRedirectUrl(state = "state-two")
        val session = store.createAppRedirectSession(state = "state-one", url = firstUrl, now = now)

        assertNull(store.findPending(instanceId = "instance", originalUrl = secondUrl, now = now + 1))
        val pending = store.findPending(instanceId = "instance", originalUrl = firstUrl, now = now + 2)

        checkNotNull(pending)
        assertEquals(session.sessionId, pending.sessionId)
    }

    @Test
    fun markReturnedRedactsFragmentTokensBeforePersisting() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createAppRedirectSession(state = "app-state", now = now)
        val redirect = BrowserAuthRedirectParser.parse(
            "kite-auth://callback#access_token=secret-token&id_token=secret-jwt&state=app-state"
        )

        val returned = store.markReturned(checkNotNull(redirect), now = now + 1)

        checkNotNull(returned)
        assertEquals(session.sessionId, returned.sessionId)
        assertEquals(BrowserAuthSessionStatus.Returned, returned.status)
        assertEquals(
            "kite-auth://callback?access_token=present&id_token=present&state=present",
            returned.returnedUrl
        )
    }

    @Test
    fun markReturnedKeepsMismatchedStatePending() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createAppRedirectSession(state = "app-state", now = now)
        val redirect = BrowserAuthRedirectParser.parse("kite-auth://callback?code=ok&state=wrong-state")

        val returned = store.markReturned(checkNotNull(redirect), now = now + 1)

        assertNull(returned)
        val pending = store.findPending(instanceId = "instance", originalUrl = appRedirectUrl(), now = now + 2)
        checkNotNull(pending)
        assertEquals(session.sessionId, pending.sessionId)
        assertEquals(BrowserAuthSessionStatus.Pending, pending.status)
    }

    @Test
    fun markReturnedDoesNotConsumeCliLoopbackWithSameState() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createCliLoopbackSession(state = "shared-state", now = now)
        val redirect = BrowserAuthRedirectParser.parse("kite-auth://callback?code=ok&state=shared-state")

        val returned = store.markReturned(checkNotNull(redirect), now = now + 1)

        assertNull(returned)
        val pending = store.findPending(instanceId = "instance", originalUrl = cliLoopbackUrl("shared-state"), now = now + 2)
        checkNotNull(pending)
        assertEquals(session.sessionId, pending.sessionId)
        assertEquals(BrowserAuthSessionKind.CliLoopback, pending.kind)
        assertEquals(BrowserAuthSessionStatus.Pending, pending.status)
    }

    @Test
    fun forwardedLoopbackCallbackBecomesAOneShotRuntimeSignal() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createCliLoopbackSession(now = now)

        store.markLoopbackCallbackChannel(
            sessionId = session.sessionId,
            status = BrowserAuthCallbackChannelStatus.RelayReady,
            now = now + 1
        )
        store.markLoopbackCallbackForwarded(session.sessionId, now = now + 2)

        val forwarded = store.forwardedLoopbackNeedingRuntimeSync().single()
        assertEquals(BrowserAuthSessionStatus.Delivered, forwarded.status)
        assertEquals(BrowserAuthCallbackChannelStatus.Forwarded, forwarded.callbackChannelStatus)
        assertNull(forwarded.callbackChannelFailureReason)
        assertNull(store.findPending("instance", cliLoopbackUrl(), now = now + 3))

        store.markRuntimeNotified(session.sessionId, now = now + 4)

        assertTrue(store.forwardedLoopbackNeedingRuntimeSync().isEmpty())
    }

    @Test
    fun relayFailureCannotDowngradeAnAlreadyForwardedCallback() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createCliLoopbackSession(now = now)

        store.markLoopbackCallbackForwarded(session.sessionId, now = now + 1)
        store.markLoopbackCallbackChannel(
            sessionId = session.sessionId,
            status = BrowserAuthCallbackChannelStatus.RelayUnavailable,
            reason = "late_connection_failed",
            now = now + 2
        )

        val forwarded = store.forwardedLoopbackNeedingRuntimeSync().single()
        assertEquals(BrowserAuthSessionStatus.Delivered, forwarded.status)
        assertEquals(BrowserAuthCallbackChannelStatus.Forwarded, forwarded.callbackChannelStatus)
        assertNull(forwarded.callbackChannelFailureReason)
    }

    @Test
    fun markReturnedDoesNotReviveExpiredAppRedirectSession() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createAppRedirectSession(state = "app-state", now = now)
        val redirect = BrowserAuthRedirectParser.parse("kite-auth://callback?code=ok&state=app-state")

        val returned = store.markReturned(checkNotNull(redirect), now = now + SESSION_TTL_MS + 1)

        assertNull(returned)
        val expired = store.expiredNeedingRuntimeSync().single()
        assertEquals(session.sessionId, expired.sessionId)
        assertEquals(BrowserAuthSessionStatus.Expired, expired.status)
        assertEquals("expired", expired.failureReason)
    }

    @Test
    fun markReturnedRecordsProviderErrorAsFailed() {
        val store = BrowserAuthSessionStore(context)
        val now = 1_000L
        val session = store.createAppRedirectSession(state = "app-state", now = now)
        val redirect = BrowserAuthRedirectParser.parse(
            "kite-auth://callback?error=access_denied&state=app-state"
        )

        val returned = store.markReturned(checkNotNull(redirect), now = now + 1)

        checkNotNull(returned)
        assertEquals(session.sessionId, returned.sessionId)
        assertEquals(BrowserAuthSessionStatus.Failed, returned.status)
        assertEquals("kite-auth://callback?error=present&state=present", returned.returnedUrl)
        assertEquals("access_denied", returned.failureReason)
    }

    private fun BrowserAuthSessionStore.createAppRedirectSession(
        state: String = "app-state",
        url: String = appRedirectUrl(state),
        now: Long
    ): BrowserAuthSession =
        createPending(
            request = BrowserHandoffRequest(
                url = url,
                recipeId = "recipe",
                recipeName = "Recipe",
                instanceId = "instance",
                source = "browser_proxy"
            ),
            decision = BrowserHandoffDecision.StartAuthHandoff(
                redirectUri = "kite-auth://callback",
                state = state
            ),
            now = now
        )

    private fun BrowserAuthSessionStore.createCliLoopbackSession(
        state: String = "cli-state",
        url: String = cliLoopbackUrl(state),
        now: Long
    ): BrowserAuthSession =
        createPending(
            request = BrowserHandoffRequest(
                url = url,
                recipeId = "recipe",
                recipeName = "Recipe",
                instanceId = "instance",
                source = "terminal_step"
            ),
            decision = BrowserHandoffDecision.StartCliCallbackHandoff(
                redirectUri = "http://localhost:1455/callback",
                state = state
            ),
            now = now
        )

    private fun appRedirectUrl(
        state: String = "app-state",
        codeChallenge: String? = null
    ): String =
        buildString {
            append("https://login.example.test/oauth/authorize")
            append("?response_type=code")
            append("&client_id=app")
            append("&redirect_uri=kite-auth%3A%2F%2Fcallback")
            append("&state=$state")
            if (!codeChallenge.isNullOrBlank()) {
                append("&code_challenge=$codeChallenge")
                append("&code_challenge_method=S256")
            }
        }

    private fun cliLoopbackUrl(state: String = "cli-state"): String =
        "https://login.example.test/authorize" +
            "?response_type=code" +
            "&client_id=cli" +
            "&redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fcallback" +
            "&state=$state"

    companion object {
        private const val SESSION_TTL_MS = 10 * 60 * 1000L
    }
}
