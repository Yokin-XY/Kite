package com.kite.app.application.runs

import com.kite.app.action.KiteActionRouter
import com.kite.app.action.KiteRecipeActionCoordinator
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeActionWorkflowCoordinatorTest {
    private val recipe = KiteRecipe(
        id = "workflow.recipe",
        name = "Workflow",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        launch = KiteLaunchConfig(openInstance = true),
        execution = KiteExecution.steps(emptyList()),
        actions = mapOf(
            KiteRecipe.ACTION_START to KiteRecipeAction(
                id = KiteRecipe.ACTION_START,
                steps = listOf(KiteRecipeStep(id = "start", type = KiteRecipe.STEP_SHELL, cmd = "true"))
            )
        )
    )
    private val gateway = FakeGateway(recipe)
    private val coordinator = RecipeActionWorkflowCoordinator(
        KiteRecipeActionCoordinator(KiteActionRouter()),
        gateway
    )

    @Test
    fun `blocked start requests runtime preparation only`() {
        val effects = dispatch(KiteRecipeActionIntent.Start, runtimeBlocked = true)

        assertEquals(listOf(RecipeActionEffect.EnsureRuntime), effects)
        assertEquals(emptyList<String>(), gateway.calls)
    }

    @Test
    fun `editor start writes run fact then opens the resolved instance`() {
        val effects = dispatch(KiteRecipeActionIntent.Start, KiteRecipeActionSource.Editor)

        assertEquals(listOf("start:instance-1"), gateway.calls)
        assertEquals(
            listOf(
                RecipeActionEffect.FocusRun("instance-1"),
                RecipeActionEffect.OpenRun(recipe.id, "instance-1", autoStart = false)
            ),
            effects
        )
    }

    @Test
    fun `console start preserves existing console navigation behavior`() {
        val effects = dispatch(KiteRecipeActionIntent.Start, KiteRecipeActionSource.ConsoleCard)

        assertEquals(
            listOf(
                RecipeActionEffect.FocusRun("instance-1"),
                RecipeActionEffect.ShowConsole
            ),
            effects
        )
    }

    @Test
    fun `通知不可用时只请求权限且不打开半成品运行窗口`() {
        gateway.startCommand = RunCommandResult.Ignored(RUN_NOTIFICATIONS_REQUIRED)

        val effects = dispatch(KiteRecipeActionIntent.Start)

        assertEquals(listOf("start:instance-1"), gateway.calls)
        assertEquals(listOf(RecipeActionEffect.RequireNotifications), effects)
    }

    @Test
    fun `accepted stop closes the exact instance and returns to console`() {
        gateway.state = gateway.state.copy(status = CardRunStatus.Running)

        val effects = dispatch(KiteRecipeActionIntent.Stop)

        assertEquals(listOf("stop:instance-1"), gateway.calls)
        assertEquals(
            listOf(
                RecipeActionEffect.CloseRunTask(recipe.id, "instance-1"),
                RecipeActionEffect.ShowConsole
            ),
            effects
        )
    }

    @Test
    fun `unsupported route records failure and returns a message`() {
        val unsupported = recipe.copy(actions = emptyMap())
        val effects = coordinator.dispatch(
            KiteRecipeActionRequest(
                unsupported,
                KiteRecipeActionIntent.Start,
                KiteRecipeActionSource.Editor
            ),
            runtimeBlocked = false,
            focusedInstanceId = null
        )

        assertEquals(listOf("failed:missing_action"), gateway.calls)
        assertEquals(listOf(RecipeActionEffect.Message("missing_action")), effects)
    }

    private fun dispatch(
        intent: KiteRecipeActionIntent,
        source: KiteRecipeActionSource = KiteRecipeActionSource.Editor,
        runtimeBlocked: Boolean = false
    ): List<RecipeActionEffect> = coordinator.dispatch(
        KiteRecipeActionRequest(recipe, intent, source),
        runtimeBlocked,
        focusedInstanceId = null
    )

    private class FakeGateway(private val recipe: KiteRecipe) : RecipeActionGateway {
        var state = CardRunState(
            instanceId = "instance-1",
            recipeId = recipe.id,
            status = CardRunStatus.Stopped
        )
        val calls = mutableListOf<String>()
        var startCommand: RunCommandResult = RunCommandResult.Accepted(state.instanceId)

        override fun resolveState(
            recipe: KiteRecipe,
            requestedInstanceId: String?,
            focusedInstanceId: String?
        ): CardRunState = state.copy(recipeId = recipe.id)

        override fun start(
            recipe: KiteRecipe,
            previousState: CardRunState,
            preferredInstanceId: String?
        ): RecipeActionStartResult {
            calls += "start:${previousState.instanceId}"
            return RecipeActionStartResult(previousState.instanceId, startCommand)
        }

        override fun stop(recipe: KiteRecipe, state: CardRunState): RunCommandResult {
            calls += "stop:${state.instanceId}"
            return RunCommandResult.Accepted(state.instanceId)
        }

        override fun markFailed(recipe: KiteRecipe, state: CardRunState, reason: String) {
            calls += "failed:$reason"
        }

        override fun logSubmit(request: KiteRecipeActionRequest, state: CardRunState) = Unit
    }
}
