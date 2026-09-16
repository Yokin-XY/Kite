package com.kite.app.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KiteX11SurfacePlanTest {
    @Test
    fun allocateReturnsDisplaySocketAndEnvironment() {
        val binding = KiteX11SurfacePlan.allocate("card-alpha")

        assertEquals(binding.display, binding.environment()["DISPLAY"])
        assertEquals(binding.display, binding.environment()["KITE_X11_DISPLAY"])
        assertEquals(binding.socketPath, binding.environment()["KITE_X11_SOCKET"])
        assertEquals("/tmp/.X11-unix/X${binding.display.removePrefix(":")}", binding.socketPath)
    }

    @Test
    fun allocateSkipsOccupiedDisplayForAnotherInstance() {
        val first = KiteX11SurfacePlan.allocate("card-alpha")
        val second = KiteX11SurfacePlan.allocate("card-alpha", setOf(first.display))

        assertNotEquals(first.display, second.display)
    }
}
