package com.termux.x11

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X11ViewportPlanTest {
    @Test
    fun landscapeKeepsNativeScale() {
        assertEquals(100, X11ViewportPlan.autoZoomPercent(1200, 700))
    }

    @Test
    fun portraitUsesBoundedDesktopZoom() {
        val zoom = X11ViewportPlan.autoZoomPercent(1080, 2400)

        assertTrue(zoom > 100)
        assertEquals(156, zoom)
        assertEquals(160, X11ViewportPlan.autoZoomPercent(1080, 2600))
    }
}
