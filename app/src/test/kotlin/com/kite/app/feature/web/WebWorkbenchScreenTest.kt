package com.kite.app.feature.web

import android.app.Activity
import android.webkit.WebView
import com.kite.app.browser.BrowserHandoffLauncher
import com.kite.app.browser.automation.BrowserAutomationControllerRegistry
import com.kite.app.browser.automation.BrowserAutomationSessionStatus
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebWorkbenchScreenTest {
    private class TrackingWebView(activity: Activity) : WebView(activity) {
        var hasHistory = false
        var goBackCalls = 0
        var destroyCalls = 0

        override fun canGoBack(): Boolean = hasHistory

        override fun goBack() {
            goBackCalls += 1
        }

        override fun destroy() {
            destroyCalls += 1
            super.destroy()
        }
    }

    @After
    fun resetRegistry() {
        BrowserAutomationControllerRegistry.resetForTest()
    }

    @Test
    fun `网页有历史时返回只回退当前 WebView`() {
        val fixture = fixture()
        fixture.webView.hasHistory = true

        assertTrue(fixture.screen.handleBack())
        assertEquals(1, fixture.webView.goBackCalls)
        assertEquals(0, fixture.exitCalls())
    }

    @Test
    fun `没有网页历史时返回交给应用导航`() {
        val fixture = fixture()

        assertFalse(fixture.screen.handleBack())
        assertEquals(0, fixture.webView.goBackCalls)
    }

    @Test
    fun `自动模式创建显示会话且释放显示面时关闭会话`() {
        val fixture = fixture()
        fixture.screen.open(
            WebWorkbenchTarget(
                url = "http://127.0.0.1:8010/automation-test",
                source = "test",
                automationEnabled = true
            )
        )
        val session = fixture.sessions.latestOpenSession()
        assertNotNull(session)

        fixture.screen.dispose()

        assertTrue(fixture.screen.isDisposedForTest())
        assertEquals(1, fixture.webView.destroyCalls)
        assertEquals(BrowserAutomationSessionStatus.Closed, fixture.sessions.get(session!!.sessionId)?.status)
    }

    private fun fixture(): Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = TrackingWebView(activity)
        val sessions = BrowserAutomationSessionStore(activity)
        var exits = 0
        val screen = WebWorkbenchScreen(
            activity = activity,
            pageBackground = 0xFFF7F8FA.toInt(),
            textPrimary = 0xFF111827.toInt(),
            diagnostics = KiteDiagnostics(activity),
            automationSessions = sessions,
            onExit = { exits += 1 },
            onLaunchHandoff = BrowserHandoffLauncher { _, _ -> true },
            webViewFactory = { webView }
        )
        return Fixture(screen, webView, sessions) { exits }
    }

    private data class Fixture(
        val screen: WebWorkbenchScreen,
        val webView: TrackingWebView,
        val sessions: BrowserAutomationSessionStore,
        val exitCalls: () -> Int
    )
}
