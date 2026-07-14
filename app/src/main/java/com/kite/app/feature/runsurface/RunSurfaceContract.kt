package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import java.net.URI

internal data class RunSurfaceTarget(
    val recipeId: String,
    val instanceId: String
)

internal sealed interface RunSurfaceContent {
    data object Summary : RunSurfaceContent
    data class Report(
        val outputText: String,
        val currentCommand: String,
        val fullCommand: String,
        val commandHint: String?,
        val insight: RunReportInsight?,
        val failed: Boolean
    ) : RunSurfaceContent
    data class Terminal(val sessionId: String?) : RunSurfaceContent
    data class Web(val url: String?) : RunSurfaceContent
    data class X11(val display: String?, val socketPath: String?) : RunSurfaceContent
    data object InstallWizard : RunSurfaceContent
}

internal enum class RunSurfaceWindowKind {
    Report,
    Terminal,
    Web,
    X11,
    InstallWizard
}

internal data class RunSurfaceWindowUiState(
    val surface: CardRunSurface,
    val kind: RunSurfaceWindowKind,
    val title: String,
    val subtitle: String,
    val selected: Boolean
)

internal data class RunWebNavigationUiState(
    val url: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0
)

internal data class RunSurfaceUiState(
    val target: RunSurfaceTarget,
    val title: String,
    val status: CardRunStatus,
    val statusLabel: String,
    val surface: CardRunSurface,
    val content: RunSurfaceContent,
    val structureKey: String,
    val currentStepIndex: Int,
    val stepCount: Int,
    val createdAt: Long,
    val canCompleteCurrentStep: Boolean,
    val canStop: Boolean,
    val windows: List<RunSurfaceWindowUiState>,
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
            CardRunSurface.Report -> RunReportPresenter.project(recipe, state)
            CardRunSurface.Terminal -> RunSurfaceContent.Terminal(state.terminalSessionId)
            CardRunSurface.Web -> RunSurfaceContent.Web(state.nextActionUrl)
            CardRunSurface.X11 -> RunSurfaceContent.X11(state.x11Display, state.x11SocketPath)
            CardRunSurface.InstallWizard -> RunSurfaceContent.InstallWizard
            CardRunSurface.Summary -> RunReportPresenter.project(recipe, state)
        }
        return RunSurfaceUiState(
            target = RunSurfaceTarget(recipe.id, state.instanceId),
            title = recipe.name.ifBlank { state.recipeName.ifBlank { "运行窗口" } },
            status = state.status,
            statusLabel = state.status.label,
            surface = surface,
            content = content,
            structureKey = structureKey(state, surface),
            currentStepIndex = state.currentStepIndex,
            stepCount = state.stepCount,
            createdAt = state.createdAt,
            canCompleteCurrentStep = canCompleteCurrentStep(recipe, state),
            canStop = state.status != CardRunStatus.Stopping &&
                (state.isInterruptible() || state.hasRunBinding()),
            windows = projectWindows(state),
            updatedAt = state.updatedAt
        )
    }

    private fun projectWindows(state: CardRunState): List<RunSurfaceWindowUiState> = buildList {
        if (state.hasReportWindow()) {
            add(
                RunSurfaceWindowUiState(
                    surface = CardRunSurface.Report,
                    kind = RunSurfaceWindowKind.Report,
                    title = "SH 报告",
                    subtitle = state.status.label,
                    selected = state.surface == CardRunSurface.Report || state.surface == CardRunSurface.Summary
                )
            )
        }
        if (!state.terminalSessionId.isNullOrBlank()) {
            add(
                RunSurfaceWindowUiState(
                    surface = CardRunSurface.Terminal,
                    kind = RunSurfaceWindowKind.Terminal,
                    title = "终端",
                    subtitle = "终端会话",
                    selected = state.surface == CardRunSurface.Terminal
                )
            )
        }
        if (!state.nextActionUrl.isNullOrBlank() || state.surface == CardRunSurface.Web) {
            add(
                RunSurfaceWindowUiState(
                    surface = CardRunSurface.Web,
                    kind = RunSurfaceWindowKind.Web,
                    title = "网页",
                    subtitle = webWindowSubtitle(state.nextActionUrl),
                    selected = state.surface == CardRunSurface.Web
                )
            )
        }
        if (!state.x11Display.isNullOrBlank() || state.surface == CardRunSurface.X11) {
            add(
                RunSurfaceWindowUiState(
                    surface = CardRunSurface.X11,
                    kind = RunSurfaceWindowKind.X11,
                    title = "X11",
                    subtitle = state.x11Display?.let { "DISPLAY=$it" } ?: "桌面窗口",
                    selected = state.surface == CardRunSurface.X11
                )
            )
        }
        if (state.surface == CardRunSurface.InstallWizard) {
            add(
                RunSurfaceWindowUiState(
                    surface = CardRunSurface.InstallWizard,
                    kind = RunSurfaceWindowKind.InstallWizard,
                    title = "安装向导",
                    subtitle = state.status.label,
                    selected = true
                )
            )
        }
        if (isEmpty()) {
            add(
                RunSurfaceWindowUiState(
                    surface = state.surface,
                    kind = state.surface.windowKind(),
                    title = state.surface.label,
                    subtitle = state.status.label,
                    selected = true
                )
            )
        }
    }.distinctBy(RunSurfaceWindowUiState::surface)

    private fun CardRunState.hasReportWindow(): Boolean =
        !shellReportText.isNullOrBlank() ||
            !lastMeaningfulOutput.isNullOrBlank() ||
            !lastError.isNullOrBlank() ||
            surface == CardRunSurface.Report ||
            surface == CardRunSurface.Summary

    private fun webWindowSubtitle(url: String?): String {
        val value = url?.trim().orEmpty()
        if (value.isBlank()) return "等待网址"
        val parsed = runCatching { URI(value) }.getOrNull()
        val host = parsed?.host?.removePrefix("www.").orEmpty()
        val port = parsed?.port?.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
        return (host + port).ifBlank { "网页窗口" }
    }

    private fun CardRunSurface.windowKind(): RunSurfaceWindowKind = when (this) {
        CardRunSurface.Terminal -> RunSurfaceWindowKind.Terminal
        CardRunSurface.Web -> RunSurfaceWindowKind.Web
        CardRunSurface.X11 -> RunSurfaceWindowKind.X11
        CardRunSurface.InstallWizard -> RunSurfaceWindowKind.InstallWizard
        CardRunSurface.Report,
        CardRunSurface.Summary -> RunSurfaceWindowKind.Report
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

}
