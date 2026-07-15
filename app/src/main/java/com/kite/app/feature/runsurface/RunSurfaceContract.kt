package com.kite.app.feature.runsurface

import com.kite.app.application.runs.RunStepActionPolicy
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunWindowIds
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
    val windowId: String,
    val surface: CardRunSurface,
    val kind: RunSurfaceWindowKind,
    val title: String,
    val subtitle: String,
    val selected: Boolean,
    val stepIndex: Int? = null,
    val canRestart: Boolean = false,
    val canClose: Boolean = false
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
    val selectedWindowId: String,
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
    val canCloseInstance: Boolean,
    val windows: List<RunSurfaceWindowUiState>,
    val updatedAt: Long
)

internal object RunSurfaceProjector {
    private data class WindowSource(
        val windowId: String,
        val surface: CardRunSurface,
        val kind: RunSurfaceWindowKind,
        val title: String,
        val subtitle: String,
        val state: CardRunState,
        val stepIndex: Int?,
        val canRestart: Boolean,
        val canClose: Boolean
    )

    fun project(
        recipe: KiteRecipe,
        state: CardRunState,
        children: List<CardRunState> = emptyList()
    ): RunSurfaceUiState {
        val sources = projectWindowSources(recipe, state, children)
        val selectedId = selectedWindowId(state, sources)
        val selected = sources.firstOrNull { it.windowId == selectedId } ?: sources.first()
        val contentState = selected.contentState(state)
        val content = when (selected.surface) {
            CardRunSurface.Report -> RunReportPresenter.project(recipe, contentState)
            CardRunSurface.Terminal -> RunSurfaceContent.Terminal(contentState.terminalSessionId)
            CardRunSurface.Web -> RunSurfaceContent.Web(contentState.nextActionUrl)
            CardRunSurface.X11 -> RunSurfaceContent.X11(contentState.x11Display, contentState.x11SocketPath)
            CardRunSurface.InstallWizard -> RunSurfaceContent.InstallWizard
            CardRunSurface.Summary -> RunReportPresenter.project(recipe, contentState)
        }
        return RunSurfaceUiState(
            target = RunSurfaceTarget(recipe.id, state.instanceId),
            selectedWindowId = selected.windowId,
            title = recipe.name.ifBlank { state.recipeName.ifBlank { "运行窗口" } },
            status = contentState.status,
            statusLabel = contentState.status.label,
            surface = selected.surface,
            content = content,
            structureKey = structureKey(state.instanceId, selected, contentState),
            currentStepIndex = state.currentStepIndex,
            stepCount = state.stepCount,
            createdAt = contentState.createdAt,
            canCompleteCurrentStep = RunStepActionPolicy.canComplete(recipe, state),
            canCloseInstance = state.status != CardRunStatus.Stopping,
            windows = sources.map { source ->
                RunSurfaceWindowUiState(
                    windowId = source.windowId,
                    surface = source.surface,
                    kind = source.kind,
                    title = source.title,
                    subtitle = source.subtitle,
                    selected = source.windowId == selected.windowId,
                    stepIndex = source.stepIndex,
                    canRestart = source.canRestart,
                    canClose = source.canClose
                )
            },
            updatedAt = contentState.updatedAt
        )
    }

    private fun projectWindowSources(
        recipe: KiteRecipe,
        parent: CardRunState,
        children: List<CardRunState>
    ): List<WindowSource> {
        val selectedManualWindow = children.any { child ->
            child.instanceId == parent.selectedWindowId &&
                (child.ownerKind == CardRunState.OWNER_KIND_TERMINAL || child.ownerKind == CardRunState.OWNER_KIND_WEB)
        }
        val replayByStep = children
            .filter { it.ownerKind == CardRunState.OWNER_KIND_STEP_REPLAY && it.currentStepIndex >= 0 }
            .groupBy(CardRunState::currentStepIndex)
            .mapValues { (_, values) -> values.maxByOrNull(CardRunState::updatedAt)!! }
        val fixed = linkedMapOf<String, WindowSource>()

        reportStepIndex(recipe, parent.currentStepIndex)
            ?.takeIf { stepIndex -> parent.hasReportWindowFor(stepIndex) }
            ?.let { stepIndex ->
                val source = replayByStep[stepIndex]?.takeIf { it.surface == CardRunSurface.Report } ?: parent
                fixed[CardRunWindowIds.workflow(stepIndex, CardRunSurface.Report)] = workflowSource(
                    recipe = recipe,
                    parent = parent,
                    source = source,
                    stepIndex = stepIndex,
                    surface = CardRunSurface.Report
                )
            }
        if (!parent.terminalSessionId.isNullOrBlank() ||
            (parent.surface == CardRunSurface.Terminal && !selectedManualWindow)
        ) {
            val stepIndex = stepIndexFor(recipe, parent, KiteRecipe.STEP_TERMINAL)
            val source = replayByStep[stepIndex]?.takeIf { it.surface == CardRunSurface.Terminal } ?: parent
            fixed[CardRunWindowIds.workflow(stepIndex, CardRunSurface.Terminal)] = workflowSource(
                recipe = recipe,
                parent = parent,
                source = source,
                stepIndex = stepIndex,
                surface = CardRunSurface.Terminal
            )
        }
        if (!parent.nextActionUrl.isNullOrBlank() ||
            (parent.surface == CardRunSurface.Web && !selectedManualWindow)
        ) {
            val stepIndex = stepIndexFor(recipe, parent, KiteRecipe.STEP_OPEN_WEB)
            val source = replayByStep[stepIndex]?.takeIf { it.surface == CardRunSurface.Web } ?: parent
            fixed[CardRunWindowIds.workflow(stepIndex, CardRunSurface.Web)] = workflowSource(
                recipe = recipe,
                parent = parent,
                source = source,
                stepIndex = stepIndex,
                surface = CardRunSurface.Web
            )
        }
        if (!parent.x11Display.isNullOrBlank() ||
            (parent.surface == CardRunSurface.X11 && !selectedManualWindow)
        ) {
            val stepIndex = stepIndexFor(recipe, parent, KiteRecipe.STEP_X11)
            val source = replayByStep[stepIndex]?.takeIf { it.surface == CardRunSurface.X11 } ?: parent
            fixed[CardRunWindowIds.workflow(stepIndex, CardRunSurface.X11)] = workflowSource(
                recipe = recipe,
                parent = parent,
                source = source,
                stepIndex = stepIndex,
                surface = CardRunSurface.X11
            )
        }
        if (parent.surface == CardRunSurface.InstallWizard && !selectedManualWindow) {
            fixed[INSTALL_WIZARD_WINDOW_ID] = WindowSource(
                windowId = INSTALL_WIZARD_WINDOW_ID,
                surface = CardRunSurface.InstallWizard,
                kind = RunSurfaceWindowKind.InstallWizard,
                title = "安装向导",
                subtitle = parent.status.label,
                state = parent,
                stepIndex = null,
                canRestart = false,
                canClose = false
            )
        }

        replayByStep.forEach { (stepIndex, child) ->
            val windowId = CardRunWindowIds.workflow(stepIndex, child.surface)
            if (windowId !in fixed) {
                fixed[windowId] = workflowSource(
                    recipe = recipe,
                    parent = parent,
                    source = child,
                    stepIndex = stepIndex,
                    surface = child.surface
                )
            }
        }

        val manual = children
            .filter { it.ownerKind == CardRunState.OWNER_KIND_TERMINAL || it.ownerKind == CardRunState.OWNER_KIND_WEB }
            .sortedBy(CardRunState::createdAt)
            .map { child ->
                WindowSource(
                    windowId = child.instanceId,
                    surface = child.surface,
                    kind = child.surface.windowKind(),
                    title = when (child.ownerKind) {
                        CardRunState.OWNER_KIND_TERMINAL -> "终端"
                        CardRunState.OWNER_KIND_WEB -> "网页"
                        else -> child.surface.label
                    },
                    subtitle = child.manualWindowSubtitle(),
                    state = child,
                    stepIndex = null,
                    canRestart = false,
                    canClose = true
                )
            }

        val combined = fixed.values.sortedBy { it.stepIndex ?: Int.MAX_VALUE } + manual
        if (combined.isNotEmpty()) return combined
        return listOf(
            WindowSource(
                windowId = SUMMARY_WINDOW_ID,
                surface = parent.surface,
                kind = parent.surface.windowKind(),
                title = when (parent.surface) {
                    CardRunSurface.Report,
                    CardRunSurface.Summary -> "执行摘要"
                    else -> parent.surface.label
                },
                subtitle = parent.status.label,
                state = parent,
                stepIndex = null,
                canRestart = false,
                canClose = false
            )
        )
    }

    private fun workflowSource(
        recipe: KiteRecipe,
        parent: CardRunState,
        source: CardRunState,
        stepIndex: Int,
        surface: CardRunSurface
    ): WindowSource {
        val step = recipe.steps.getOrNull(stepIndex)
        val contentState = source.forWorkflowStep(parent, stepIndex, surface)
        return WindowSource(
            windowId = CardRunWindowIds.workflow(stepIndex, surface),
            surface = surface,
            kind = surface.windowKind(),
            title = when (surface) {
                CardRunSurface.Report -> when (step?.type) {
                    KiteRecipe.STEP_SHELL -> "SH 报告"
                    KiteRecipe.STEP_ANDROID_ACTION -> "动作报告"
                    else -> "执行摘要"
                }
                CardRunSurface.Terminal -> "终端"
                CardRunSurface.Web -> "网页"
                CardRunSurface.X11 -> "X11"
                CardRunSurface.InstallWizard -> "安装向导"
                CardRunSurface.Summary -> "执行摘要"
            },
            subtitle = when (surface) {
                CardRunSurface.Web -> webWindowSubtitle(contentState.nextActionUrl)
                CardRunSurface.Terminal -> if (contentState.terminalSessionId.isNullOrBlank()) {
                    contentState.status.label
                } else {
                    "终端会话"
                }
                CardRunSurface.X11 -> contentState.x11Display?.let { "DISPLAY=$it" } ?: contentState.status.label
                else -> contentState.status.label
            },
            state = source,
            stepIndex = stepIndex,
            canRestart = step != null,
            canClose = false
        )
    }

    private fun selectedWindowId(parent: CardRunState, sources: List<WindowSource>): String {
        parent.selectedWindowId?.takeIf { selected -> sources.any { it.windowId == selected } }?.let { return it }
        sources.firstOrNull {
            it.stepIndex == parent.currentStepIndex && it.surface == parent.surface
        }?.let { return it.windowId }
        return sources.lastOrNull { it.surface == parent.surface }?.windowId ?: sources.first().windowId
    }

    private fun WindowSource.contentState(parent: CardRunState): CardRunState =
        state.forWorkflowStep(parent, stepIndex, surface)

    private fun CardRunState.forWorkflowStep(
        parent: CardRunState,
        stepIndex: Int?,
        surface: CardRunSurface
    ): CardRunState {
        if (this === parent) {
            return copy(surface = surface, currentStepIndex = stepIndex ?: currentStepIndex)
        }
        return parent.copy(
            status = status,
            surface = surface,
            currentStepIndex = stepIndex ?: currentStepIndex,
            runId = runId,
            terminalSessionId = terminalSessionId,
            pid = pid,
            rootPid = rootPid,
            processGroupId = processGroupId,
            systemSessionId = systemSessionId,
            lastMeaningfulOutput = lastMeaningfulOutput,
            lastError = lastError,
            shellReportText = shellReportText,
            nextActionUrl = nextActionUrl,
            x11Display = x11Display,
            x11SocketPath = x11SocketPath,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun reportStepIndex(recipe: KiteRecipe, currentStepIndex: Int): Int? {
        if (recipe.steps.isEmpty()) return null
        val capped = currentStepIndex.coerceIn(0, recipe.steps.lastIndex.coerceAtLeast(0))
        return (capped downTo 0).firstOrNull { index ->
            recipe.steps.getOrNull(index)?.type in REPORT_STEP_TYPES
        }
    }

    private fun stepIndexFor(recipe: KiteRecipe, state: CardRunState, stepType: String): Int {
        val current = state.currentStepIndex
        if (recipe.steps.getOrNull(current)?.type == stepType) return current
        return (current.coerceAtMost(recipe.steps.lastIndex) downTo 0)
            .firstOrNull { recipe.steps.getOrNull(it)?.type == stepType }
            ?: current.coerceAtLeast(0)
    }

    private fun CardRunState.hasReportWindowFor(stepIndex: Int): Boolean =
        !shellReportText.isNullOrBlank() ||
            (currentStepIndex == stepIndex && (
                surface == CardRunSurface.Report ||
                    !lastError.isNullOrBlank()
                ))

    private fun CardRunState.manualWindowSubtitle(): String = when (ownerKind) {
        CardRunState.OWNER_KIND_WEB -> webWindowSubtitle(nextActionUrl)
        CardRunState.OWNER_KIND_TERMINAL -> if (terminalSessionId.isNullOrBlank()) status.label else "独立终端"
        else -> status.label
    }

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

    private fun structureKey(
        parentInstanceId: String,
        source: WindowSource,
        contentState: CardRunState
    ): String = buildString {
        append(parentInstanceId)
        append(':').append(source.windowId)
        append(':').append(source.surface.name)
        if (source.surface == CardRunSurface.Terminal) {
            append(':').append(contentState.terminalSessionId.orEmpty())
        }
    }

    private val REPORT_STEP_TYPES = setOf(KiteRecipe.STEP_SHELL, KiteRecipe.STEP_ANDROID_ACTION)
    private const val INSTALL_WIZARD_WINDOW_ID = "workflow:install-wizard"
    private const val SUMMARY_WINDOW_ID = "workflow:summary"
}
