package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteExecution
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

class RunSurfaceControllerTest {
    private val actions = FakeRunSurfaceActions()
    private val controller = RunSurfaceController(actions)

    @Test
    fun `报告内容变化不改变显示结构键`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"))
        val first = state(surface = CardRunSurface.Report, report = "第一段")
        val second = first.copy(shellReportText = "第二段", updatedAt = first.updatedAt + 1)

        val attached = controller.attach(recipe, first)
        val updated = controller.update(recipe, second)

        assertEquals(attached.structureKey, updated?.structureKey)
        assertEquals(RunSurfaceContent.Report("第二段"), updated?.content)
    }

    @Test
    fun `终端绑定携带同一实例和会话身份`() {
        val recipe = recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"))
        val state = state(
            surface = CardRunSurface.Terminal,
            status = CardRunStatus.WaitingTerminal,
            terminalSessionId = "terminal-1"
        )

        val ui = controller.attach(recipe, state)

        assertEquals(RunSurfaceContent.Terminal("terminal-1"), ui.content)
        assertTrue(ui.canCompleteCurrentStep)
        assertTrue(ui.canStop)
    }

    @Test
    fun `其他实例的迟到状态不能覆盖当前显示面`() {
        val recipe = recipe(KiteRecipeStep(id = "web", type = KiteRecipe.STEP_OPEN_WEB, url = "https://example.com"))
        controller.attach(recipe, state(surface = CardRunSurface.Web, nextActionUrl = "https://example.com"))

        val stale = controller.update(recipe, state(instanceId = "other", surface = CardRunSurface.Report))

        assertNull(stale)
    }

    @Test
    fun `页面离开只解绑而显式停止才提交停止命令`() {
        val recipe = recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"))
        controller.attach(recipe, state(surface = CardRunSurface.Terminal, terminalSessionId = "terminal-1"))

        controller.detach()

        assertFalse(controller.stop())
        assertTrue(actions.stoppedInstances.isEmpty())

        controller.attach(recipe, state(surface = CardRunSurface.Terminal, terminalSessionId = "terminal-1"))
        assertTrue(controller.stop())
        assertEquals(listOf("instance-1"), actions.stoppedInstances)
    }

    private fun recipe(step: KiteRecipeStep): KiteRecipe = KiteRecipe(
        id = "recipe-1",
        name = "测试运行",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(listOf(step))
    )

    private fun state(
        instanceId: String = "instance-1",
        surface: CardRunSurface,
        status: CardRunStatus = CardRunStatus.Running,
        terminalSessionId: String? = null,
        nextActionUrl: String? = null,
        report: String? = null
    ): CardRunState = CardRunState(
        instanceId = instanceId,
        recipeId = "recipe-1",
        recipeName = "测试运行",
        status = status,
        surface = surface,
        currentStepIndex = 0,
        stepCount = 1,
        terminalSessionId = terminalSessionId,
        nextActionUrl = nextActionUrl,
        shellReportText = report,
        createdAt = 1L,
        updatedAt = 1L
    )
}

private class FakeRunSurfaceActions : RunSurfaceActionGateway {
    val completedInstances = mutableListOf<String>()
    val stoppedInstances = mutableListOf<String>()

    override fun completeCurrentStep(instanceId: String, output: String) {
        completedInstances += instanceId
    }

    override fun stop(instanceId: String) {
        stoppedInstances += instanceId
    }
}
