package com.kite.app.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteVscodeResourceLaunchTest {
    @Test
    fun desktopResourcesLaunchThroughShellDesktopProxy() {
        listOf("kite.vscode.x11", "kite.pcmanfm.x11").forEach { resourceId ->
            val manifest = resourceManifest(resourceId).readText()

            assertFalse(manifest.contains("\"type\": \"x11\""))
            assertTrue(manifest.contains("\"type\": \"shell\""))
            assertTrue(manifest.contains("kite-open-desktop"))
            assertTrue(manifest.contains("KITE_DESKTOP_PROXY"))
        }
    }

    @Test
    fun pcmanfmLaunchConstrainsWindowToFixed1080pDesktop() {
        val manifest = resourceManifest("kite.pcmanfm.x11").readText()

        assertTrue(manifest.contains("xdotool"))
        assertTrue(manifest.contains("win_width=\$KITE_X11_DESKTOP_WIDTH"))
        assertTrue(manifest.contains("win_height=\$KITE_X11_DESKTOP_HEIGHT"))
        assertTrue(manifest.contains("--new-win /workspace"))
        assertTrue(manifest.contains("xdotool windowmove"))
        assertTrue(manifest.contains("xdotool windowsize"))
    }

    private fun resourceManifest(resourceId: String): File =
        listOf(
            File("assets/resources/$resourceId/manifest.json"),
            File("../assets/resources/$resourceId/manifest.json")
        ).first { it.isFile }
}
