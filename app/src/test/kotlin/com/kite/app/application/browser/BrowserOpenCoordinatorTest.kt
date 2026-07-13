package com.kite.app.application.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrowserOpenCoordinatorTest {
    @Test
    fun `blank URL is ignored before gateway work`() {
        var called = false
        val coordinator = BrowserOpenCoordinator {
            called = true
            BrowserOpenResult.RoutedToExistingSurface
        }

        val result = coordinator.open(request("  "))

        assertEquals(BrowserOpenResult.Ignored, result)
        assertFalse(called)
    }

    @Test
    fun `URL is trimmed and gateway result is preserved`() {
        var received: BrowserOpenRequest? = null
        val expected = BrowserOpenResult.OpenTemporaryRun("recipe", "instance", "https://example.com", "test")
        val coordinator = BrowserOpenCoordinator { request ->
            received = request
            expected
        }

        val result = coordinator.open(request("  https://example.com  "))

        assertEquals("https://example.com", received?.url)
        assertEquals(expected, result)
    }

    private fun request(url: String) = BrowserOpenRequest(url, null, null, "test")
}
