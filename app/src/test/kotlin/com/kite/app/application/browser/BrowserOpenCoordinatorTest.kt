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

        val result = coordinator.open(request("  https://example.com  ", "card_run_surface"))

        assertEquals("https://example.com", received?.url)
        assertEquals(expected, result)
    }

    @Test
    fun `CLI browser request opens externally without creating a Web run`() {
        var called = false
        val coordinator = BrowserOpenCoordinator {
            called = true
            BrowserOpenResult.RoutedToExistingSurface
        }

        val result = coordinator.open(
            request("https://www.kimi.com/code/authorize_device", "terminal_step")
        )

        assertEquals(
            BrowserOpenResult.OpenExternalBrowser("https://www.kimi.com/code/authorize_device"),
            result
        )
        assertFalse(called)
    }

    @Test
    fun `local terminal Web UI still reaches the Web surface gateway`() {
        var called = false
        val coordinator = BrowserOpenCoordinator {
            called = true
            BrowserOpenResult.RoutedToExistingSurface
        }

        val result = coordinator.open(request("http://127.0.0.1:5494/", "terminal_step"))

        assertEquals(BrowserOpenResult.RoutedToExistingSurface, result)
        assertEquals(true, called)
    }

    private fun request(url: String, source: String = "test") =
        BrowserOpenRequest(url, null, null, source)
}
