package com.kite.app.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteVscodeResourceLaunchTest {
    @Test
    fun vscodeLaunchesThroughShellDesktopProxy() {
        val manifest = resourceManifest().readText()

        assertFalse(manifest.contains("\"type\": \"x11\""))
        assertTrue(manifest.contains("\"type\": \"shell\""))
        assertTrue(manifest.contains("kite-open-desktop"))
        assertTrue(manifest.contains("KITE_DESKTOP_PROXY"))
    }

    private fun resourceManifest(): File =
        listOf(
            File("assets/resources/kite.vscode.x11/manifest.json"),
            File("../assets/resources/kite.vscode.x11/manifest.json")
        ).first { it.isFile }
}
