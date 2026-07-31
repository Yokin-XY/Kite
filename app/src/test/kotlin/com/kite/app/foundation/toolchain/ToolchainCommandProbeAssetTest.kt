package com.kite.app.foundation.toolchain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolchainCommandProbeAssetTest {
    @Test
    fun installScriptUsesBundledBoundedProbeProtocol() {
        val installScript = projectFile(
            "../assets/toolchain/ai-dev-pack/install.sh",
            "assets/toolchain/ai-dev-pack/install.sh"
        ).readText()
        val probeLibrary = projectFile(
            "../assets/toolchain/ai-dev-pack/lib/command-probe.sh",
            "assets/toolchain/ai-dev-pack/lib/command-probe.sh"
        ).readText()
        val manifest = projectFile(
            "../assets/toolchain/ai-dev-pack/manifest.json",
            "assets/toolchain/ai-dev-pack/manifest.json"
        ).readText()

        assertTrue(installScript.contains("source \"\$PROBE_LIBRARY\""))
        assertTrue(installScript.contains("kf_probe_command \"\$command_name\" \"\$probe_argument\""))
        assertFalse(installScript.contains("timeout -k 2s 5s \"\$command_name\""))
        assertTrue(probeLibrary.contains("KF_TOOLCHAIN_PROBE_TIMEOUT_SECONDS:-30"))
        assertTrue(probeLibrary.contains("KF_PROBE_REASON=\"timeout(\${timeout_seconds}s)\""))
        assertTrue(manifest.contains("\"version\": 18"))
    }

    private fun projectFile(vararg candidates: String): File =
        candidates.map(::File).first { it.isFile }
}
