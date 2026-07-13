package com.kite.app.platform.browser

import com.kite.app.application.browser.BrowserOpenGateway
import com.kite.app.application.browser.BrowserOpenRequest
import com.kite.app.application.browser.BrowserOpenResult
import com.kite.app.application.runs.CardRunSpecialRecipes
import com.kite.app.bridge.KiteBrowserOpenRequest
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunBrowserRouter
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import java.util.UUID

/** Ubuntu 浏览器请求的路由/Store 适配器，不创建 WebView 或 Activity。 */
internal class AndroidBrowserOpenGateway(
    private val diagnostics: KiteDiagnostics,
    private val recipeResolver: (String) -> KiteRecipe?
) : BrowserOpenGateway {
    override fun open(request: BrowserOpenRequest): BrowserOpenResult {
        val bridgeRequest = KiteBrowserOpenRequest(
            url = request.url,
            recipeId = request.recipeId,
            instanceId = request.instanceId,
            source = request.source
        )
        if (!request.instanceId.isNullOrBlank() && CardRunBrowserRouter.dispatch(bridgeRequest)) {
            return BrowserOpenResult.RoutedToExistingSurface
        }
        val recipe = request.recipeId?.takeIf(String::isNotBlank)?.let(recipeResolver)
        val instanceId = request.instanceId?.takeIf(String::isNotBlank)
        if (recipe != null && instanceId != null) {
            updateRun(recipe, instanceId, request.url)
            diagnostics.logRecipeAction(
                recipe,
                "browser_request_waiting_for_instance",
                mapOf(
                    "instanceId" to instanceId,
                    "source" to request.source,
                    "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url)
                )
            )
            return BrowserOpenResult.RecordedForInstance(recipe.id, instanceId)
        }
        val recipeId = "temp_web_${UUID.randomUUID().toString().replace("-", "")}"
        val temporary = CardRunSpecialRecipes.temporaryBrowser(recipeId, request.url)
        val temporaryInstanceId = "run_${recipeId}_${UUID.randomUUID().toString().replace("-", "")}"
        CardRunStore.start(temporary, temporaryInstanceId)
        updateRun(temporary, temporaryInstanceId, request.url)
        diagnostics.logRecipeAction(
            temporary,
            "browser_request_opened_temporary_instance",
            mapOf(
                "instanceId" to temporaryInstanceId,
                "source" to request.source,
                "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url)
            )
        )
        return BrowserOpenResult.OpenTemporaryRun(
            recipeId = recipeId,
            instanceId = temporaryInstanceId,
            url = request.url,
            source = request.source
        )
    }

    private fun updateRun(recipe: KiteRecipe, instanceId: String, url: String) {
        val existing = CardRunStore.get(instanceId)
        val status = when (existing?.status) {
            CardRunStatus.Starting,
            CardRunStatus.Running,
            CardRunStatus.WaitingTerminal -> existing.status
            else -> CardRunStatus.Opened
        }
        CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId,
            surface = CardRunSurface.Web,
            currentStepIndex = existing?.currentStepIndex,
            runId = existing?.runId,
            terminalSessionId = existing?.terminalSessionId,
            pid = existing?.pid,
            lastMeaningfulOutput = "Ubuntu 请求打开网页",
            nextActionUrl = url
        )
    }
}
