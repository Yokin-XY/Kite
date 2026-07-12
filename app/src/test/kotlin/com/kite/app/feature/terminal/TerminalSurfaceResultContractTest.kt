package com.kite.app.feature.terminal

import android.os.Bundle
import com.kite.app.application.surface.SurfaceChromeMode
import com.kite.app.application.surface.SurfaceEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalSurfaceResultContractTest {
    @Test
    fun `chrome mode parses as generic surface effect`() {
        val bundle = Bundle().apply {
            putString("kind", "chrome")
            putString("mode", SurfaceChromeMode.Immersive.name)
        }

        assertEquals(
            SurfaceEffect.SetChromeMode(SurfaceChromeMode.Immersive),
            TerminalSurfaceResultContract.parse(bundle)
        )
    }

    @Test
    fun `back effect parses without activity identity`() {
        val bundle = Bundle().apply { putString("kind", "back") }

        assertEquals(SurfaceEffect.RequestBack, TerminalSurfaceResultContract.parse(bundle))
    }

    @Test
    fun `invalid chrome mode is ignored`() {
        val bundle = Bundle().apply {
            putString("kind", "chrome")
            putString("mode", "unknown")
        }

        assertNull(TerminalSurfaceResultContract.parse(bundle))
    }
}
