package com.kite.app.application.browser

internal data class BrowserOpenRequest(
    val url: String,
    val recipeId: String?,
    val instanceId: String?,
    val source: String
)

internal sealed interface BrowserOpenResult {
    data object Ignored : BrowserOpenResult
    data object RoutedToExistingSurface : BrowserOpenResult
    data class RecordedForInstance(
        val recipeId: String,
        val instanceId: String
    ) : BrowserOpenResult
    data class OpenTemporaryRun(
        val recipeId: String,
        val instanceId: String,
        val url: String,
        val source: String,
        val title: String = "临时网页"
    ) : BrowserOpenResult
}

internal fun interface BrowserOpenGateway {
    fun open(request: BrowserOpenRequest): BrowserOpenResult
}

internal class BrowserOpenCoordinator(
    private val gateway: BrowserOpenGateway
) {
    fun open(request: BrowserOpenRequest): BrowserOpenResult {
        val normalized = request.copy(url = request.url.trim())
        return if (normalized.url.isBlank()) BrowserOpenResult.Ignored else gateway.open(normalized)
    }
}
