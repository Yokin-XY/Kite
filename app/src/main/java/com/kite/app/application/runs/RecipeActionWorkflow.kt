package com.kite.app.application.runs

import com.kite.app.action.KiteActionRoute
import com.kite.app.action.KiteRecipeActionCoordinator
import com.kite.app.action.KiteRecipeActionPlan
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState

internal sealed interface RecipeActionEffect {
    data object EnsureRuntime : RecipeActionEffect
    data class FocusRun(val instanceId: String) : RecipeActionEffect
    data class OpenRun(
        val recipeId: String,
        val instanceId: String,
        val autoStart: Boolean
    ) : RecipeActionEffect
    data class CloseRunTask(val recipeId: String, val instanceId: String) : RecipeActionEffect
    data object ShowConsole : RecipeActionEffect
    data class Message(val text: String) : RecipeActionEffect
    data object RequireNotifications : RecipeActionEffect
}

internal data class RecipeActionStartResult(
    val instanceId: String,
    val command: RunCommandResult
)

internal interface RecipeActionGateway {
    fun resolveState(
        recipe: KiteRecipe,
        requestedInstanceId: String?,
        focusedInstanceId: String?
    ): CardRunState

    fun start(
        recipe: KiteRecipe,
        previousState: CardRunState,
        preferredInstanceId: String?
    ): RecipeActionStartResult

    fun stop(recipe: KiteRecipe, state: CardRunState): RunCommandResult
    fun markFailed(recipe: KiteRecipe, state: CardRunState, reason: String)
    fun logSubmit(request: KiteRecipeActionRequest, state: CardRunState)
}

/** 把页面提交的配方意图转换成可由 Shell 解释的 Effect。 */
internal class RecipeActionWorkflowCoordinator(
    private val planner: KiteRecipeActionCoordinator,
    private val gateway: RecipeActionGateway
) {
    fun dispatch(
        request: KiteRecipeActionRequest,
        runtimeBlocked: Boolean,
        focusedInstanceId: String?
    ): List<RecipeActionEffect> {
        val state = gateway.resolveState(request.recipe, request.instanceId, focusedInstanceId)
        gateway.logSubmit(request, state)
        return when (val plan = planner.plan(request, state, runtimeBlocked)) {
            is KiteRecipeActionPlan.Ignored -> emptyList()
            KiteRecipeActionPlan.RuntimeRequired -> listOf(RecipeActionEffect.EnsureRuntime)
            KiteRecipeActionPlan.OpenRun -> listOf(
                RecipeActionEffect.OpenRun(request.recipe.id, state.instanceId, autoStart = false)
            )
            KiteRecipeActionPlan.LaunchTask -> launchTask(request, state)
            KiteRecipeActionPlan.Stop -> stopEffects(request.recipe, state)
            is KiteRecipeActionPlan.Execute -> execute(request, state, plan.route)
        }
    }

    private fun launchTask(
        request: KiteRecipeActionRequest,
        state: CardRunState
    ): List<RecipeActionEffect> {
        val started = gateway.start(request.recipe, state, request.instanceId)
        return when (val command = started.command) {
            is RunCommandResult.Accepted -> listOf(
                RecipeActionEffect.OpenRun(request.recipe.id, started.instanceId, autoStart = false)
            )
            is RunCommandResult.Ignored -> startIgnoredEffects(command)
        }
    }

    private fun execute(
        request: KiteRecipeActionRequest,
        state: CardRunState,
        route: KiteActionRoute
    ): List<RecipeActionEffect> = when (route) {
        is KiteActionRoute.StopRecipe -> stopEffects(request.recipe, state)
        is KiteActionRoute.RunRecipe -> {
            val started = gateway.start(route.recipe, state, request.instanceId)
            when (val command = started.command) {
                is RunCommandResult.Accepted -> buildList {
                    add(RecipeActionEffect.FocusRun(started.instanceId))
                    if (request.source != KiteRecipeActionSource.Editor && route.recipe.launch.openInstance) {
                        add(RecipeActionEffect.ShowConsole)
                    } else {
                        add(RecipeActionEffect.OpenRun(route.recipe.id, started.instanceId, autoStart = false))
                    }
                }
                is RunCommandResult.Ignored -> startIgnoredEffects(command)
            }
        }
        is KiteActionRoute.Unsupported -> {
            gateway.markFailed(request.recipe, state, route.reason)
            listOf(RecipeActionEffect.Message(route.reason))
        }
    }

    private fun stopEffects(recipe: KiteRecipe, state: CardRunState): List<RecipeActionEffect> =
        when (gateway.stop(recipe, state)) {
            is RunCommandResult.Accepted -> listOf(
                RecipeActionEffect.CloseRunTask(recipe.id, state.instanceId),
                RecipeActionEffect.ShowConsole
            )
            is RunCommandResult.Ignored -> emptyList()
        }

    private fun startIgnoredEffects(command: RunCommandResult.Ignored): List<RecipeActionEffect> =
        if (command.reason == RUN_NOTIFICATIONS_REQUIRED) {
            listOf(RecipeActionEffect.RequireNotifications)
        } else {
            listOf(RecipeActionEffect.Message("运行未启动：${command.reason}"))
        }
}
