package com.kite.app.application.runs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopOpenCoordinatorTest {
    @Test
    fun `blank command is rejected before platform work`() {
        var called = false
        val coordinator = DesktopOpenCoordinator {
            called = true
            DesktopOpenResult(accepted = true)
        }

        val result = coordinator.open(request("   "))

        assertFalse(called)
        assertFalse(result.accepted)
        assertEquals("missing_command", result.error)
    }

    @Test
    fun `command is trimmed and platform result is preserved`() {
        var received: DesktopOpenRequest? = null
        val coordinator = DesktopOpenCoordinator { request ->
            received = request
            DesktopOpenResult(
                accepted = true,
                recipeId = "recipe",
                instanceId = "instance",
                display = ":7",
                socketPath = "/tmp/.X11-unix/X7",
                openRunTask = true
            )
        }

        val result = coordinator.open(request("  xfce4-session  "))

        assertEquals("xfce4-session", received?.command)
        assertTrue(result.accepted)
        assertTrue(result.openRunTask)
        assertEquals(":7", result.display)
    }

    private fun request(command: String) = DesktopOpenRequest(
        command = command,
        title = null,
        recipeId = null,
        instanceId = null,
        source = "test"
    )
}
