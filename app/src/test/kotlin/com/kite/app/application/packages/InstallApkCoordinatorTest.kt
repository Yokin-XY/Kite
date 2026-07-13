package com.kite.app.application.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InstallApkCoordinatorTest {
    @Test
    fun `blank path is rejected before platform resolution`() {
        var called = false
        val coordinator = InstallApkCoordinator {
            called = true
            InstallApkResult(true, it)
        }

        val result = coordinator.resolve("   ")

        assertFalse(called)
        assertFalse(result.accepted)
        assertEquals("missing_path", result.error)
    }

    @Test
    fun `path is trimmed before platform resolution`() {
        var received = ""
        val coordinator = InstallApkCoordinator { path ->
            received = path
            InstallApkResult(false, path, error = "apk_not_found")
        }

        val result = coordinator.resolve("  /sdcard/Download/app.apk  ")

        assertEquals("/sdcard/Download/app.apk", received)
        assertEquals("apk_not_found", result.error)
    }
}
