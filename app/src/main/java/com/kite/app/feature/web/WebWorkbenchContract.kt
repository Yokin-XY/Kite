package com.kite.app.feature.web

import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics

internal data class WebWorkbenchTarget(
    val url: String,
    val source: String,
    val recipeId: String? = null,
    val recipeName: String? = null,
    val instanceId: String? = null,
    val automationEnabled: Boolean = false
)

/** Application 只提供进程级依赖，不接管 WebView 或页面状态。 */
internal interface WebWorkbenchDependenciesOwner {
    val webWorkbenchDiagnostics: KiteDiagnostics
    val webWorkbenchAutomationSessions: BrowserAutomationSessionStore
    fun launchWebWorkbenchHandoff(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision
    ): Boolean
}
