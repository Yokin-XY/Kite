package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus

internal const val RUN_NOTIFICATIONS_REQUIRED = "run_notifications_required"

internal sealed interface RunNotificationAction {
    val label: String

    data class CompleteStep(
        val command: RunStepCompletionCommand
    ) : RunNotificationAction {
        override val label: String = "下一步"
    }

    data class Close(
        val instanceId: String,
        val expectedGeneration: Long
    ) : RunNotificationAction {
        override val label: String = "关闭"
    }

    data class Restart(
        val recipeId: String,
        val instanceId: String,
        val expectedGeneration: Long,
        val retry: Boolean
    ) : RunNotificationAction {
        override val label: String = if (retry) "重试" else "再次运行"
    }
}

internal data class RunNotificationUiState(
    val recipeId: String,
    val instanceId: String,
    val generation: Long,
    val updatedAt: Long,
    val title: String,
    val stepLabel: String,
    val detail: String,
    val progress: Int,
    val progressMax: Int,
    val indeterminate: Boolean,
    val ongoing: Boolean,
    val compactAction: RunNotificationAction?,
    val expandedActions: List<RunNotificationAction>
) {
    val completionCommand: RunStepCompletionCommand?
        get() = expandedActions
            .filterIsInstance<RunNotificationAction.CompleteStep>()
            .firstOrNull()
            ?.command
}

internal object RunNotificationProjector {
    fun project(recipe: KiteRecipe, state: CardRunState): RunNotificationUiState? {
        if (state.ownerKind != CardRunState.OWNER_KIND_CARD || state.status == CardRunStatus.Unknown) return null
        val terminal = state.status in TERMINAL_STATUSES
        if (terminal && !recipe.launch.keepFinishedNotification) return null
        val stepCount = state.stepCount.coerceAtLeast(recipe.steps.size).coerceAtLeast(0)
        val stepIndex = state.currentStepIndex.coerceAtLeast(0)
        val currentStep = recipe.steps.getOrNull(state.currentStepIndex)
        val shownStep = when {
            stepCount <= 0 -> 0
            state.status == CardRunStatus.Completed -> stepCount
            else -> (stepIndex + 1).coerceAtMost(stepCount)
        }
        val stepLabel = if (stepCount > 0) {
            "第 $shownStep/$stepCount 步${currentStep?.let { " · ${stepTypeLabel(it.type)}" }.orEmpty()}"
        } else {
            state.status.label
        }
        val detail = when {
            !state.lastError.isNullOrBlank() -> state.lastError
            !state.lastMeaningfulOutput.isNullOrBlank() -> state.lastMeaningfulOutput
            else -> state.status.label
        }.orEmpty().take(MAX_DETAIL_LENGTH)
        val completion = RunStepActionPolicy.completionCommand(recipe, state)
            ?.let(RunNotificationAction::CompleteStep)
        val close = if (!terminal && state.status != CardRunStatus.Stopping) {
            RunNotificationAction.Close(state.instanceId, state.createdAt)
        } else {
            null
        }
        val restart = if (terminal) {
            RunNotificationAction.Restart(
                recipeId = recipe.id,
                instanceId = state.instanceId,
                expectedGeneration = state.createdAt,
                retry = state.status == CardRunStatus.Failed || state.status == CardRunStatus.BridgeUnavailable
            )
        } else {
            null
        }
        return RunNotificationUiState(
            recipeId = recipe.id,
            instanceId = state.instanceId,
            generation = state.createdAt,
            updatedAt = state.updatedAt,
            title = recipe.name.ifBlank { state.recipeName.ifBlank { "Kite 运行实例" } },
            stepLabel = stepLabel,
            detail = detail,
            progress = shownStep.coerceAtMost(stepCount),
            progressMax = stepCount,
            indeterminate = !terminal && stepCount <= 0,
            ongoing = !terminal,
            compactAction = restart ?: completion ?: close,
            expandedActions = when {
                restart != null -> listOf(restart)
                close != null && completion != null -> listOf(close, completion)
                close != null -> listOf(close)
                else -> emptyList()
            }
        )
    }

    private fun stepTypeLabel(type: String): String = when (type) {
        KiteRecipe.STEP_SHELL -> "SH"
        KiteRecipe.STEP_TERMINAL -> "终端"
        KiteRecipe.STEP_OPEN_WEB -> "网页"
        KiteRecipe.STEP_X11 -> "X11"
        KiteRecipe.STEP_AGENT -> "Agent 会话"
        KiteRecipe.STEP_ANDROID_ACTION -> "安卓动作"
        else -> "执行"
    }

    private val TERMINAL_STATUSES = setOf(
        CardRunStatus.Completed,
        CardRunStatus.Failed,
        CardRunStatus.Stopped,
        CardRunStatus.BridgeUnavailable
    )
    private const val MAX_DETAIL_LENGTH = 800
}
