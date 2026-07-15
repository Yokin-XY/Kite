package com.kite.app.application.runs

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunNotificationProjectorTest {
    @Test
    fun `等待终端通知携带精确步骤身份和当前进度`() {
        val recipe = recipe(KiteRecipe.STEP_TERMINAL, KiteRecipe.STEP_SHELL)
        val state = state(
            status = CardRunStatus.WaitingTerminal,
            surface = CardRunSurface.Terminal,
            terminalSessionId = "terminal-1"
        )

        val model = RunNotificationProjector.project(recipe, state)!!

        assertEquals("第 1/2 步 · 终端", model.stepLabel)
        assertEquals(1, model.progress)
        assertEquals(2, model.progressMax)
        assertTrue(model.ongoing)
        assertEquals("下一步", model.compactAction?.label)
        assertEquals(listOf("关闭", "下一步"), model.expandedActions.map { it.label })
        assertEquals(
            RunStepCompletionCommand(
                instanceId = "instance-1",
                expectedGeneration = 42L,
                expectedStepIndex = 0,
                expectedStepId = "step-0",
                output = "终端已由用户标记完成"
            ),
            model.completionCommand
        )
    }

    @Test
    fun `普通执行进度不提供下一步动作`() {
        val recipe = recipe(KiteRecipe.STEP_SHELL)

        val model = RunNotificationProjector.project(
            recipe,
            state(status = CardRunStatus.Running, surface = CardRunSurface.Report)
        )!!

        assertNull(model.completionCommand)
        assertEquals("关闭", model.compactAction?.label)
        assertEquals(listOf("关闭"), model.expandedActions.map { it.label })
        assertTrue(model.ongoing)
    }

    @Test
    fun `最终人工步骤只提供关闭而不再产生下一步命令`() {
        val recipe = recipe(KiteRecipe.STEP_TERMINAL)
        val model = RunNotificationProjector.project(
            recipe,
            state(
                status = CardRunStatus.WaitingTerminal,
                surface = CardRunSurface.Terminal,
                terminalSessionId = "terminal-1"
            )
        )!!

        assertNull(model.completionCommand)
        assertEquals("关闭", model.compactAction?.label)
        assertEquals(listOf("关闭"), model.expandedActions.map { it.label })
    }

    @Test
    fun `默认卡片结束后不再投影通知`() {
        assertNull(
            RunNotificationProjector.project(
                recipe(KiteRecipe.STEP_SHELL),
                state(status = CardRunStatus.Completed, surface = CardRunSurface.Report)
            )
        )
    }

    @Test
    fun `开启保留后完成通知为满进度且可再次运行`() {
        val recipe = recipe(
            KiteRecipe.STEP_SHELL,
            KiteRecipe.STEP_TERMINAL,
            keepFinishedNotification = true
        )
        val model = RunNotificationProjector.project(
            recipe,
            state(
                status = CardRunStatus.Completed,
                surface = CardRunSurface.Report,
                currentStepIndex = 2
            )
        )!!

        assertEquals(2, model.progress)
        assertEquals(2, model.progressMax)
        assertFalse(model.ongoing)
        assertNull(model.completionCommand)
        assertEquals("再次运行", model.compactAction?.label)
        assertEquals(listOf("再次运行"), model.expandedActions.map { it.label })
    }

    @Test
    fun `失败结果提供重试而不是继续旧代次`() {
        val model = RunNotificationProjector.project(
            recipe(KiteRecipe.STEP_SHELL, keepFinishedNotification = true),
            state(status = CardRunStatus.Failed, surface = CardRunSurface.Report)
        )!!

        assertFalse(model.ongoing)
        assertEquals("重试", model.compactAction?.label)
        assertTrue(model.compactAction is RunNotificationAction.Restart)
    }

    @Test
    fun `资源拥有的运行不进入首页卡片通知`() {
        assertNull(
            RunNotificationProjector.project(
                recipe(KiteRecipe.STEP_SHELL, keepFinishedNotification = true),
                state(
                    status = CardRunStatus.Running,
                    surface = CardRunSurface.Report,
                    ownerKind = CardRunState.OWNER_KIND_RESOURCE
                )
            )
        )
    }

    private fun recipe(
        vararg types: String,
        keepFinishedNotification: Boolean = false
    ): KiteRecipe = KiteRecipe(
        id = "recipe-1",
        name = "测试流程",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        launch = KiteLaunchConfig(keepFinishedNotification = keepFinishedNotification),
        execution = KiteExecution.steps(
            types.mapIndexed { index, type ->
                KiteRecipeStep(
                    id = "step-$index",
                    type = type,
                    cmd = "echo $index".takeIf { type != KiteRecipe.STEP_OPEN_WEB },
                    url = "https://example.com/$index".takeIf { type == KiteRecipe.STEP_OPEN_WEB }
                )
            }
        )
    )

    private fun state(
        status: CardRunStatus,
        surface: CardRunSurface,
        currentStepIndex: Int = 0,
        terminalSessionId: String? = null,
        ownerKind: String = CardRunState.OWNER_KIND_CARD
    ): CardRunState = CardRunState(
        instanceId = "instance-1",
        recipeId = "recipe-1",
        recipeName = "测试流程",
        ownerKind = ownerKind,
        status = status,
        surface = surface,
        currentStepIndex = currentStepIndex,
        stepCount = 2,
        terminalSessionId = terminalSessionId,
        lastMeaningfulOutput = "等待处理",
        createdAt = 42L,
        updatedAt = 43L
    )
}
