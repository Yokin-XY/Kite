package com.kite.app.application.packages

internal data class InstallApkResult(
    val accepted: Boolean,
    val path: String,
    val resolvedPath: String = "",
    val error: String = ""
)

internal fun interface InstallApkGateway {
    fun resolve(path: String): InstallApkResult
}

internal class InstallApkCoordinator(
    private val gateway: InstallApkGateway
) {
    fun resolve(path: String): InstallApkResult {
        val normalized = path.trim()
        return if (normalized.isBlank()) {
            InstallApkResult(false, path, error = "missing_path")
        } else {
            gateway.resolve(normalized)
        }
    }
}
