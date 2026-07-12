package com.kite.app.action

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus

internal enum class KiteRecipeActionIntent {
    Primary,
    Start,
    Open,
    Stop
}

internal enum class KiteRecipeActionSource(val logValue: String) {
    ConsoleCard("console_card"),
    Editor("editor"),
    RunSurface("run_surface"),
    RunManagement("run_management")
}

internal data class KiteRecipeActionRequest(
    val recipe: KiteRecipe,
    val intent: KiteRecipeActionIntent,
    val source: KiteRecipeActionSource,
    val instanceId: String? = null,
    val openTaskOnStart: Boolean = false
)

internal sealed interface KiteRecipeActionPlan {
    data class Ignored(val reason: String) : KiteRecipeActionPlan
    data object RuntimeRequired : KiteRecipeActionPlan
    data object OpenRun : KiteRecipeActionPlan
    data object LaunchTask : KiteRecipeActionPlan
    data object Stop : KiteRecipeActionPlan
    data class Execute(val route: KiteActionRoute) : KiteRecipeActionPlan
}

/**
 * 将页面动作意图归一化为轻量计划。这里只决定下一跳，不执行运行时重活或写入运行事实。
 */
internal class KiteRecipeActionCoordinator(
    private val router: KiteActionRouter
) {
    fun plan(
        request: KiteRecipeActionRequest,
        state: CardRunState,
        runtimeBlocked: Boolean
    ): KiteRecipeActionPlan {
        return when (request.intent) {
            KiteRecipeActionIntent.Open -> KiteRecipeActionPlan.OpenRun
            KiteRecipeActionIntent.Stop -> if (state.status == CardRunStatus.Stopping) {
                KiteRecipeActionPlan.Ignored("busy")
            } else if (state.status == CardRunStatus.Starting || state.isInterruptible() || state.hasRunBinding()) {
                KiteRecipeActionPlan.Stop
            } else {
                KiteRecipeActionPlan.Ignored("not_running")
            }
            KiteRecipeActionIntent.Primary -> when {
                state.status == CardRunStatus.Starting || state.status == CardRunStatus.Stopping ->
                    KiteRecipeActionPlan.Ignored("busy")
                runtimeBlocked -> KiteRecipeActionPlan.RuntimeRequired
                state.isInterruptible() -> KiteRecipeActionPlan.Stop
                request.openTaskOnStart -> KiteRecipeActionPlan.LaunchTask
                else -> KiteRecipeActionPlan.Execute(
                    router.route(request.recipe, KiteRecipe.ACTION_START)
                )
            }
            KiteRecipeActionIntent.Start -> when {
                state.status == CardRunStatus.Starting || state.status == CardRunStatus.Stopping ->
                    KiteRecipeActionPlan.Ignored("busy")
                runtimeBlocked -> KiteRecipeActionPlan.RuntimeRequired
                state.isInterruptible() || state.hasRunBinding() -> KiteRecipeActionPlan.OpenRun
                else -> KiteRecipeActionPlan.Execute(
                    router.route(request.recipe, KiteRecipe.ACTION_START)
                )
            }
        }
    }
}
