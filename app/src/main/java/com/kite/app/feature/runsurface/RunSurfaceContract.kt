package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface

internal data class RunSurfaceTarget(
    val recipeId: String,
    val instanceId: String
)

internal sealed interface RunSurfaceContent {
    data object Summary : RunSurfaceContent
    data class Report(val text: String) : RunSurfaceContent
    data class Terminal(val sessionId: String?) : RunSurfaceContent
    data class Web(val url: String?) : RunSurfaceContent
    data class X11(val display: String?, val socketPath: String?) : RunSurfaceContent
    data object InstallWizard : RunSurfaceContent
}

internal data class RunSurfaceUiState(
    val target: RunSurfaceTarget,
    val title: String,
    val status: CardRunStatus,
    val statusLabel: String,
    val surface: CardRunSurface,
    val content: RunSurfaceContent,
    val structureKey: String,
    val canCompleteCurrentStep: Boolean,
    val canStop: Boolean,
    val updatedAt: Long
)

internal interface RunSurfaceActionGateway {
    fun completeCurrentStep(instanceId: String, output: String)
    fun stop(instanceId: String)
}

internal object RunSurfaceProjector {
    fun project(recipe: KiteRecipe, state: CardRunState): RunSurfaceUiState {
        val surface = state.surface
        val content = when (surface) {
            CardRunSurface.Report -> RunSurfaceContent.Report(reportText(state))
            CardRunSurface.Terminal -> RunSurfaceContent.Terminal(state.terminalSessionId)
            CardRunSurface.Web -> RunSurfaceContent.Web(state.nextActionUrl)
            CardRunSurface.X11 -> RunSurfaceContent.X11(state.x11Display, state.x11SocketPath)
            CardRunSurface.InstallWizard -> RunSurfaceContent.InstallWizard
            CardRunSurface.Summary -> RunSurfaceContent.Summary
        }
        return RunSurfaceUiState(
            target = RunSurfaceTarget(recipe.id, state.instanceId),
            title = recipe.name.ifBlank { state.recipeName.ifBlank { "运行窗口" } },
            status = state.status,
            statusLabel = state.status.label,
            surface = surface,
            content = content,
            structureKey = structureKey(state, surface),
            canCompleteCurrentStep = canCompleteCurrentStep(recipe, state),
            canStop = state.isInterruptible() || state.hasRunBinding(),
            updatedAt = state.updatedAt
        )
    }

    private fun structureKey(state: CardRunState, surface: CardRunSurface): String = buildString {
        append(state.instanceId)
        append(':')
        append(surface.name)
        when (surface) {
            CardRunSurface.Terminal -> append(':').append(state.terminalSessionId.orEmpty())
            CardRunSurface.X11 -> append(':').append(state.x11Display.orEmpty())
            else -> Unit
        }
    }

    private fun canCompleteCurrentStep(recipe: KiteRecipe, state: CardRunState): Boolean {
        val step = recipe.steps.getOrNull(state.currentStepIndex) ?: return false
        return when (step.type) {
            KiteRecipe.STEP_SHELL -> state.surface == CardRunSurface.Report &&
                state.currentStepIndex < recipe.steps.lastIndex &&
                !state.shellReportText.isNullOrBlank() &&
                state.status in setOf(CardRunStatus.Running, CardRunStatus.AlreadyRunning)
            KiteRecipe.STEP_TERMINAL -> state.status == CardRunStatus.WaitingTerminal &&
                !state.terminalSessionId.isNullOrBlank()
            KiteRecipe.STEP_OPEN_WEB -> state.surface == CardRunSurface.Web &&
                !state.nextActionUrl.isNullOrBlank()
            KiteRecipe.STEP_X11 -> state.surface == CardRunSurface.X11 &&
                !state.x11Display.isNullOrBlank()
            else -> false
        }
    }

    private fun reportText(state: CardRunState): String = when {
        !state.shellReportText.isNullOrBlank() -> state.shellReportText
        !state.lastError.isNullOrBlank() -> state.lastError
        !state.lastMeaningfulOutput.isNullOrBlank() -> state.lastMeaningfulOutput
        else -> state.status.label
    }.orEmpty()
}
