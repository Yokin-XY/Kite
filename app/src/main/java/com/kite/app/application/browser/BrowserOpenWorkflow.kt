package com.kite.app.application.browser

import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffPolicy

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
    data class OpenExternalBrowser(val url: String) : BrowserOpenResult
}

internal fun interface BrowserOpenGateway {
    fun open(request: BrowserOpenRequest): BrowserOpenResult
}

internal class BrowserOpenCoordinator(
    private val gateway: BrowserOpenGateway
) {
    fun open(request: BrowserOpenRequest): BrowserOpenResult {
        val normalized = request.copy(url = request.url.trim())
        if (normalized.url.isBlank()) return BrowserOpenResult.Ignored
        return if (
            BrowserHandoffPolicy.classify(normalized.url, normalized.source) ==
            BrowserHandoffDecision.OpenExternalBrowser
        ) {
            BrowserOpenResult.OpenExternalBrowser(normalized.url)
        } else {
            gateway.open(normalized)
        }
    }
}
