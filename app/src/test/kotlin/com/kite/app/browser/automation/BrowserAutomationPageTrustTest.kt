package com.kite.app.browser.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAutomationPageTrustTest {
    @Test
    fun localUrlsAreTrustedForEvaluate() {
        val urls = listOf(
            "http://127.0.0.1:8791/browser-automation/test-page",
            "http://localhost:8791/browser-automation/test-page",
            "https://appassets.androidplatform.net/assets/page.html",
            "file:///android_asset/page.html"
        )

        urls.forEach { url ->
            assertEquals(BrowserAutomationPageTrust.SCOPE_LOCAL, BrowserAutomationPageTrust.scope(url))
            assertTrue(BrowserAutomationPageTrust.evaluateAllowed(url))
        }
    }

    @Test
    fun remoteAndUnknownUrlsAreNotTrustedForEvaluate() {
        assertEquals(BrowserAutomationPageTrust.SCOPE_REMOTE, BrowserAutomationPageTrust.scope("https://example.com"))
        assertFalse(BrowserAutomationPageTrust.evaluateAllowed("https://example.com"))
        assertEquals(BrowserAutomationPageTrust.SCOPE_UNKNOWN, BrowserAutomationPageTrust.scope("about:blank"))
        assertFalse(BrowserAutomationPageTrust.evaluateAllowed("about:blank"))
        assertEquals(BrowserAutomationPageTrust.SCOPE_UNKNOWN, BrowserAutomationPageTrust.scope(""))
        assertFalse(BrowserAutomationPageTrust.evaluateAllowed(""))
    }
}
