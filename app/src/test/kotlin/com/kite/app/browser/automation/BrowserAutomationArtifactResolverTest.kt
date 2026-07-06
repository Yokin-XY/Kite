package com.kite.app.browser.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BrowserAutomationArtifactResolverTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun resolvesScreenshotPngInsideAutomationDirectory() {
        val filesDir = temp.newFolder("files")
        val screenshot = screenshotFile(filesDir, "shot.png")
        screenshot.writeBytes(PNG_BYTES)

        val absolute = BrowserAutomationArtifactResolver.resolve(filesDir, screenshot.absolutePath)
        val relative = BrowserAutomationArtifactResolver.resolve(filesDir, "shot.png")

        assertTrue(absolute is BrowserAutomationArtifactResolution.Found)
        assertEquals(screenshot.canonicalFile, (absolute as BrowserAutomationArtifactResolution.Found).file)
        assertTrue(relative is BrowserAutomationArtifactResolution.Found)
        assertEquals(screenshot.canonicalFile, (relative as BrowserAutomationArtifactResolution.Found).file)
    }

    @Test
    fun rejectsArtifactsOutsideScreenshotDirectory() {
        val filesDir = temp.newFolder("files")
        val outside = File(filesDir, "outside.png").apply { writeBytes(PNG_BYTES) }

        val absolute = BrowserAutomationArtifactResolver.resolve(filesDir, outside.absolutePath)
        val traversal = BrowserAutomationArtifactResolver.resolve(filesDir, "../outside.png")

        assertEquals(
            BrowserAutomationArtifactResolution.Rejected("artifact_path_not_allowed"),
            absolute
        )
        assertEquals(
            BrowserAutomationArtifactResolution.Rejected("artifact_path_not_allowed"),
            traversal
        )
    }

    @Test
    fun rejectsNonPngAndReportsMissingPng() {
        val filesDir = temp.newFolder("files")
        val text = screenshotFile(filesDir, "shot.txt").apply { writeText("not a screenshot") }

        val nonPng = BrowserAutomationArtifactResolver.resolve(filesDir, text.absolutePath)
        val missing = BrowserAutomationArtifactResolver.resolve(filesDir, "missing.png")
        val blank = BrowserAutomationArtifactResolver.resolve(filesDir, "")

        assertEquals(
            BrowserAutomationArtifactResolution.Rejected("artifact_type_not_allowed"),
            nonPng
        )
        assertEquals(BrowserAutomationArtifactResolution.Missing, missing)
        assertEquals(
            BrowserAutomationArtifactResolution.Rejected("missing_path"),
            blank
        )
    }

    private fun screenshotFile(filesDir: File, name: String): File {
        val dir = File(filesDir, "browser-automation/screenshots").apply { mkdirs() }
        return File(dir, name)
    }

    companion object {
        private val PNG_BYTES = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
    }
}
