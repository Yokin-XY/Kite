package com.kite.app.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserAuthRedirectPersistenceTest {
    private lateinit var context: Context

    @Before
    fun clearState() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("kite_browser_auth_sessions", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `进程重建后的 Store 仍按原 state 交付回跳`() {
        val request = BrowserHandoffRequest(
            url = "https://auth.example/authorize?response_type=code&client_id=test&redirect_uri=kite-auth%3A%2F%2Fcallback&state=stable-state",
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "test"
        )
        val firstProcessStore = BrowserAuthSessionStore(context)
        val pending = firstProcessStore.createPending(
            request = request,
            decision = BrowserHandoffDecision.StartAuthHandoff(
                redirectUri = "kite-auth://callback",
                state = "stable-state"
            ),
            now = 1_000L
        )

        val reconstructedProcessStore = BrowserAuthSessionStore(context)
        val redirect = BrowserAuthRedirectParser.parse(
            "kite-auth://callback?state=stable-state&code=provider-code"
        )
        val returned = reconstructedProcessStore.markReturned(checkNotNull(redirect), now = 1_001L)

        assertNotNull(returned)
        assertEquals(pending.sessionId, returned?.sessionId)
        assertEquals(BrowserAuthSessionStatus.Returned, returned?.status)
    }
}
