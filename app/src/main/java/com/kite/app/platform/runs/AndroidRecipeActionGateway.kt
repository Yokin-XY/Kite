package com.kite.app.platform.runs

import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.application.runs.RecipeActionGateway
import com.kite.app.application.runs.RecipeActionStartResult
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore

/** 通用配方动作的 Store/运行时适配器，不持有 Activity 或显示状态。 */
internal class AndroidRecipeActionGateway(
    private val orchestrator: RunOrchestrator,
    private val diagnostics: KiteDiagnostics
) : RecipeActionGateway {
    override fun resolveState(
        recipe: KiteRecipe,
        requestedInstanceId: String?,
        focusedInstanceId: String?
    ): CardRunState = requestedInstanceId
        ?.takeIf(String::isNotBlank)
        ?.let(CardRunStore::get)
        ?.takeIf { it.recipeId == recipe.id }
        ?: focusedInstanceId
            ?.takeIf(String::isNotBlank)
            ?.let(CardRunStore::get)
            ?.takeIf { it.recipeId == recipe.id }
        ?: CardRunStore.currentForRecipe(recipe.id)
        ?: CardRunState.fromRecipeStatus(recipe.id, "unknown")

    override fun start(
        recipe: KiteRecipe,
        previousState: CardRunState,
        preferredInstanceId: String?
    ): RecipeActionStartResult {
        val instanceId = preferredInstanceId?.takeIf(String::isNotBlank)
            ?: previousState.instanceId.takeIf { previousState.recipeId == recipe.id && it.isNotBlank() }
            ?: CardRunStore.currentForRecipe(recipe.id)?.instanceId
            ?: recipe.id
        val result = orchestrator.start(
            RunStartRequest(
                recipe = recipe,
                instanceId = instanceId,
                parentInstanceId = previousState.parentInstanceId,
                ownerKind = previousState.ownerKind,
                stepId = previousState.stepId
            )
        )
        diagnostics.logRecipeAction(
            recipe,
            "run_orchestrator_start",
            mapOf(
                "instanceId" to instanceId,
                "result" to when (result) {
                    is RunCommandResult.Accepted -> "accepted"
                    is RunCommandResult.Ignored -> "ignored:${result.reason}"
                },
                "steps" to recipe.steps.joinToString(" -> ") { it.type }
            )
        )
        return RecipeActionStartResult(instanceId, result)
    }

    override fun stop(recipe: KiteRecipe, state: CardRunState): RunCommandResult {
        diagnostics.logBridgeEvent(
            "stop_orchestrator_request",
            recipe,
            mapOf(
                "cardInstanceId" to state.cardInstanceId,
                "runId" to state.runId.orEmpty(),
                "terminalSessionId" to state.terminalSessionId.orEmpty()
            )
        )
        return orchestrator.stop(state.instanceId).also { result ->
            if (result is RunCommandResult.Ignored) {
                diagnostics.logBridgeEvent(
                    "stop_orchestrator_ignored",
                    recipe,
                    mapOf("cardInstanceId" to state.cardInstanceId, "reason" to result.reason)
                )
            }
        }
    }

    override fun markFailed(recipe: KiteRecipe, state: CardRunState, reason: String) {
        val failed = CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Failed,
            instanceId = state.instanceId,
            lastError = reason
        )
        diagnostics.logLifecycleEvent(
            recipe,
            CardRunStatus.Failed.lifecycleEvent,
            failed.runId,
            failed.pid,
            CardRunStatus.Failed.name,
            failed.lastMeaningfulOutput,
            failed.lastError
        )
    }

    override fun logSubmit(request: KiteRecipeActionRequest, state: CardRunState) {
        diagnostics.logRecipeAction(
            request.recipe,
            "action_submit",
            mapOf(
                "intent" to request.intent.name,
                "source" to request.source.logValue,
                "status" to state.status.name
            )
        )
    }
}
