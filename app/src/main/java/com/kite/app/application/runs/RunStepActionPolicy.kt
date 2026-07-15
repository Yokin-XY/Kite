package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface

/** 人工步骤动作的唯一业务判定；显示面和通知只投影这里的结论。 */
internal object RunStepActionPolicy {
    fun completionCommand(recipe: KiteRecipe, state: CardRunState): RunStepCompletionCommand? {
        val step = recipe.steps.getOrNull(state.currentStepIndex) ?: return null
        if (state.currentStepIndex >= recipe.steps.lastIndex) return null
        if (!canComplete(step, state)) return null
        return RunStepCompletionCommand(
            instanceId = state.instanceId,
            expectedGeneration = state.createdAt,
            expectedStepIndex = state.currentStepIndex,
            expectedStepId = step.id,
            output = completionMessage(step)
        )
    }

    fun canComplete(recipe: KiteRecipe, state: CardRunState): Boolean =
        completionCommand(recipe, state) != null

    private fun canComplete(
        step: KiteRecipeStep,
        state: CardRunState
    ): Boolean = when (step.type) {
        KiteRecipe.STEP_SHELL -> state.surface == CardRunSurface.Report &&
            !state.shellReportText.isNullOrBlank() &&
            state.status in setOf(CardRunStatus.Running, CardRunStatus.AlreadyRunning)
        KiteRecipe.STEP_TERMINAL -> state.status == CardRunStatus.WaitingTerminal &&
            !state.terminalSessionId.isNullOrBlank()
        KiteRecipe.STEP_OPEN_WEB -> state.surface == CardRunSurface.Web &&
            !state.nextActionUrl.isNullOrBlank() &&
            state.status in setOf(CardRunStatus.Opened, CardRunStatus.Running, CardRunStatus.AlreadyRunning)
        KiteRecipe.STEP_X11 -> state.surface == CardRunSurface.X11 &&
            !state.x11Display.isNullOrBlank() &&
            state.status in setOf(CardRunStatus.Opened, CardRunStatus.Running, CardRunStatus.AlreadyRunning)
        else -> false
    }

    private fun completionMessage(step: KiteRecipeStep): String = when (step.type) {
        KiteRecipe.STEP_TERMINAL -> "终端已由用户标记完成"
        KiteRecipe.STEP_OPEN_WEB -> "网页已由用户标记完成"
        KiteRecipe.STEP_X11 -> "X11 GUI 已由用户标记完成"
        KiteRecipe.STEP_SHELL -> "SH 报告已由用户确认继续"
        else -> "步骤已由用户标记完成"
    }
}
