package com.kite.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserRuntimeModeTest {
    @Test
    fun missingStorageKeyUsesDefaultMode() {
        assertEquals(
            BrowserRuntimeMode.WebViewWithSystemAuth,
            BrowserRuntimeMode.fromStorageKey(null)
        )
    }

    @Test
    fun unknownStorageKeyUsesDefaultMode() {
        assertEquals(
            BrowserRuntimeMode.WebViewWithSystemAuth,
            BrowserRuntimeMode.fromStorageKey("legacy")
        )
    }

    @Test
    fun automationBrowserStorageKeyRestoresAutomationMode() {
        assertEquals(
            BrowserRuntimeMode.AutomationBrowser,
            BrowserRuntimeMode.fromStorageKey("automation_browser")
        )
    }
}
