package com.termux.x11

import org.junit.Assert.assertEquals
import org.junit.Test

class X11ViewportPlanTest {
    @Test
    fun portraitViewFitsLandscapeDesktopByWidth() {
        val layout = X11ViewportPlan.fitLandscapeDesktop(360, 800)

        assertEquals(1280, layout.desktopWidth)
        assertEquals(720, layout.desktopHeight)
        assertEquals(0, layout.viewportLeft)
        assertEquals(298, layout.viewportTop)
        assertEquals(360, layout.viewportWidth)
        assertEquals(203, layout.viewportHeight)
    }

    @Test
    fun wideLandscapeViewCentersDesktopByHeight() {
        val layout = X11ViewportPlan.fitLandscapeDesktop(900, 360)

        assertEquals(130, layout.viewportLeft)
        assertEquals(0, layout.viewportTop)
        assertEquals(640, layout.viewportWidth)
        assertEquals(360, layout.viewportHeight)
    }
}
