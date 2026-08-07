package com.kite.app.action

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteRecipeActionCoordinatorTest {
    private val coordinator = KiteRecipeActionCoordinator(KiteActionRouter())
    private val recipe = KiteRecipe(
        id = "test.action.coordinator",
        name = "Action Coordinator",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(emptyList()),
        actions = mapOf(
            KiteRecipe.ACTION_START to KiteRecipeAction(
                id = KiteRecipe.ACTION_START,
                steps = listOf(
                    KiteRecipeStep(id = "start", type = KiteRecipe.STEP_SHELL, cmd = "echo start")
                )
            )
        )
    )

    @Test
    fun `忙碌状态拒绝重复启动动作`() {
        val plan = plan(KiteRecipeActionIntent.Primary, CardRunStatus.Starting)

        assertEquals(KiteRecipeActionPlan.Ignored("busy"), plan)
    }

    @Test
    fun `启动中仍允许显式打开运行面`() {
        val plan = plan(KiteRecipeActionIntent.Open, CardRunStatus.Starting)

        assertEquals(KiteRecipeActionPlan.OpenRun, plan)
    }

    @Test
    fun `启动中仍允许显式停止`() {
        val plan = plan(KiteRecipeActionIntent.Stop, CardRunStatus.Starting)

        assertEquals(KiteRecipeActionPlan.Stop, plan)
    }

    @Test
    fun `首页主动作在运行中归一化为停止`() {
        val plan = plan(KiteRecipeActionIntent.Primary, CardRunStatus.Running)

        assertEquals(KiteRecipeActionPlan.Stop, plan)
    }

    @Test
    fun `停止待确认的首页主动作继续停止而不是重新启动`() {
        val plan = plan(KiteRecipeActionIntent.Primary, CardRunStatus.CleanupPending)

        assertEquals(KiteRecipeActionPlan.Stop, plan)
    }

    @Test
    fun `编辑页启动遇到已有实例时归一化为打开`() {
        val plan = plan(
            intent = KiteRecipeActionIntent.Start,
            status = CardRunStatus.Stopped,
            runId = "run-1"
        )

        assertEquals(KiteRecipeActionPlan.OpenRun, plan)
    }

    @Test
    fun `运行环境阻塞时先返回准备计划`() {
        val plan = plan(
            intent = KiteRecipeActionIntent.Start,
            status = CardRunStatus.Stopped,
            runtimeBlocked = true
        )

        assertEquals(KiteRecipeActionPlan.RuntimeRequired, plan)
    }

    @Test
    fun `首页需要独立实例时返回任务启动计划`() {
        val request = KiteRecipeActionRequest(
            recipe = recipe,
            intent = KiteRecipeActionIntent.Primary,
            source = KiteRecipeActionSource.ConsoleCard,
            openTaskOnStart = true
        )

        val plan = coordinator.plan(request, state(CardRunStatus.Stopped), runtimeBlocked = false)

        assertEquals(KiteRecipeActionPlan.LaunchTask, plan)
    }

    @Test
    fun `显式启动遇到运行实例时只打开而不停止`() {
        val plan = plan(KiteRecipeActionIntent.Start, CardRunStatus.Running)

        assertEquals(KiteRecipeActionPlan.OpenRun, plan)
    }

    @Test
    fun `显式启动需要独立实例时仍先启动同一任务`() {
        val request = KiteRecipeActionRequest(
            recipe = recipe,
            intent = KiteRecipeActionIntent.Start,
            source = KiteRecipeActionSource.ConsoleCard,
            openTaskOnStart = true
        )

        val plan = coordinator.plan(request, state(CardRunStatus.Stopped), runtimeBlocked = false)

        assertEquals(KiteRecipeActionPlan.LaunchTask, plan)
    }

    @Test
    fun `普通启动计划继续委托动作路由器`() {
        val plan = plan(KiteRecipeActionIntent.Start, CardRunStatus.Stopped)

        assertTrue(plan is KiteRecipeActionPlan.Execute)
        assertTrue((plan as KiteRecipeActionPlan.Execute).route is KiteActionRoute.RunRecipe)
    }

    private fun plan(
        intent: KiteRecipeActionIntent,
        status: CardRunStatus,
        runId: String? = null,
        runtimeBlocked: Boolean = false
    ): KiteRecipeActionPlan = coordinator.plan(
        request = KiteRecipeActionRequest(
            recipe = recipe,
            intent = intent,
            source = KiteRecipeActionSource.Editor
        ),
        state = state(status, runId),
        runtimeBlocked = runtimeBlocked
    )

    private fun state(status: CardRunStatus, runId: String? = null): CardRunState = CardRunState(
        instanceId = "instance-1",
        recipeId = recipe.id,
        status = status,
        runId = runId
    )
}
