package com.kite.app.browser.automation

import java.io.File

object BrowserAutomationArtifactResolver {
    fun resolve(filesDir: File, rawPath: String?): BrowserAutomationArtifactResolution {
        val value = rawPath.orEmpty().trim()
        if (value.isBlank()) {
            return BrowserAutomationArtifactResolution.Rejected("missing_path")
        }
        val root = File(filesDir, SCREENSHOT_DIR).canonicalFile
        val requested = if (File(value).isAbsolute) {
            File(value)
        } else {
            File(root, value)
        }.canonicalFile
        if (!isInsideRoot(root, requested)) {
            return BrowserAutomationArtifactResolution.Rejected("artifact_path_not_allowed")
        }
        if (!requested.name.endsWith(".png", ignoreCase = true)) {
            return BrowserAutomationArtifactResolution.Rejected("artifact_type_not_allowed")
        }
        if (!requested.isFile) {
            return BrowserAutomationArtifactResolution.Missing
        }
        return BrowserAutomationArtifactResolution.Found(requested)
    }

    private fun isInsideRoot(root: File, requested: File): Boolean {
        val rootPath = root.path.trimEnd(File.separatorChar)
        return requested.path == rootPath || requested.path.startsWith(rootPath + File.separator)
    }

    private const val SCREENSHOT_DIR = "browser-automation/screenshots"
}

sealed class BrowserAutomationArtifactResolution {
    data class Found(val file: File) : BrowserAutomationArtifactResolution()
    data object Missing : BrowserAutomationArtifactResolution()
    data class Rejected(val error: String) : BrowserAutomationArtifactResolution()
}
