package com.kite.app.foundation.toolchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolchainPackInstallerTest {
    @Test
    fun bootstrapRegistration_usesCommandExitCodeAsInstallSignal() {
        val installed = ToolchainPackInstaller.resolveBootstrapResourceRegistration(
            resourceId = "kite.ripgrep",
            version = "rootfs",
            exitCode = 0,
            timedOut = false,
            summary = "SUMMARY PASS=1 WARN=0 FAIL=0"
        )

        assertEquals("kite.ripgrep", installed.resourceId)
        assertTrue(installed.installed)
        assertEquals("rootfs", installed.version)
    }

    @Test
    fun bootstrapRegistration_nonZeroExitIsResourceFailureOnly() {
        val failed = ToolchainPackInstaller.resolveBootstrapResourceRegistration(
            resourceId = "kite.curl",
            version = "rootfs",
            exitCode = 2,
            timedOut = false,
            summary = "missing rootfs command: curl"
        )

        assertFalse(failed.installed)
        assertEquals("missing rootfs command: curl", failed.summary)
    }

    @Test
    fun bootstrapRegistration_timeoutIsFailure() {
        val timedOut = ToolchainPackInstaller.resolveBootstrapResourceRegistration(
            resourceId = "kite.nodejs",
            version = "26.4.0",
            exitCode = -1,
            timedOut = true,
            summary = "exitCode=-1 timedOut=true"
        )

        assertFalse(timedOut.installed)
    }

    @Test
    fun bootstrapSummaryLine_prefersFailOverEndMarker() {
        val summary = ToolchainPackInstaller.bootstrapSummaryLine(
            """
            KFSHELL_AI_DEV_PACK_BEGIN
            FAIL	python-package	missing bundled package
            SUMMARY PASS=0 WARN=0 FAIL=1
            KFSHELL_AI_DEV_PACK_END
            """.trimIndent()
        )

        assertEquals("FAIL\tpython-package\tmissing bundled package", summary)
    }

    @Test
    fun bootstrapResourceStatusSettled_acceptsInstalledAndFailedOnly() {
        assertTrue(ToolchainPackInstaller.bootstrapResourceStatusSettled("installed"))
        assertTrue(ToolchainPackInstaller.bootstrapResourceStatusSettled("failed"))
        assertFalse(ToolchainPackInstaller.bootstrapResourceStatusSettled(""))
        assertFalse(ToolchainPackInstaller.bootstrapResourceStatusSettled("installing"))
    }
}
