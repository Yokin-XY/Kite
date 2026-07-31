package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunAgentBinding
import com.kite.app.run.CardRunAgentConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSurfaceControllerTest {
    private val controller = RunSurfaceController()

    @Test
    fun `报告内容变化不改变显示结构键`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"))
        val first = state(surface = CardRunSurface.Report, report = "第一段")
        val second = first.copy(shellReportText = "第二段", updatedAt = first.updatedAt + 1)

        val attached = controller.attach(recipe, first)
        val updated = controller.update(recipe, second)

        assertEquals(attached.structureKey, updated?.structureKey)
        assertEquals("第二段", (updated?.content as? RunSurfaceContent.Report)?.outputText)
    }

    @Test
    fun `Agent 从准备到会话就绪不重建显示面`() {
        val recipe = recipe(KiteRecipeStep(id = "agent", type = KiteRecipe.STEP_AGENT, agentId = "opencode"))
        val preparing = state(
            surface = CardRunSurface.Agent,
            agentId = "opencode",
            agentBinding = CardRunAgentBinding(
                providerId = "opencode",
                status = CardRunAgentConnectionStatus.Preparing,
                statusMessage = "正在启动 OpenCode"
            )
        )
        val ready = preparing.copy(
            agentBinding = preparing.agentBinding?.copy(
                sessionId = "session-1",
                status = CardRunAgentConnectionStatus.Ready,
                statusMessage = "准备就绪"
            ),
            updatedAt = preparing.updatedAt + 1
        )

        val first = controller.attach(recipe, preparing)
        val second = controller.update(recipe, ready)!!

        assertEquals(first.structureKey, second.structureKey)
        assertEquals(CardRunSurface.Agent, second.surface)
        assertEquals(
            RunSurfaceContent.Agent(
                agentId = "opencode",
                providerId = "opencode",
                sessionId = "session-1",
                connectionStatus = CardRunAgentConnectionStatus.Ready,
                statusMessage = "准备就绪"
            ),
            second.content
        )
        assertEquals(listOf(CardRunSurface.Agent), second.windows.map(RunSurfaceWindowUiState::surface))
    }

    @Test
    fun `报告投影清理运行标记并保留命令`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "printf hello"))
        val ui = controller.attach(
            recipe,
            state(
                surface = CardRunSurface.Report,
                report = "命令：printf hello\n结果：成功\n有效输出：hello\n__kite_root_pid:123"
            )
        )

        val report = ui.content as RunSurfaceContent.Report
        assertEquals("hello", report.outputText)
        assertEquals("printf hello", report.currentCommand)
        assertEquals("printf hello", report.fullCommand)
    }

    @Test
    fun `概览沿用报告显示合同而不建立第二套页面状态`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"))

        val ui = controller.attach(recipe, state(surface = CardRunSurface.Summary, report = "输出：ok"))

        assertTrue(ui.content is RunSurfaceContent.Report)
        assertEquals("ok", (ui.content as RunSurfaceContent.Report).outputText)
    }

    @Test
    fun `失败提示由投影层稳定生成`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "curl example.com"))

        val ui = controller.attach(
            recipe,
            state(
                surface = CardRunSurface.Report,
                status = CardRunStatus.Failed,
                lastError = "curl: (28) Operation timed out after 30000 milliseconds"
            )
        )

        val report = ui.content as RunSurfaceContent.Report
        assertTrue(report.failed)
        assertTrue(report.commandHint.orEmpty().contains("命令超时"))
    }

    @Test
    fun `最终终端绑定携带同一实例和会话身份但不暴露继续动作`() {
        val recipe = recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"))
        val state = state(
            surface = CardRunSurface.Terminal,
            status = CardRunStatus.WaitingTerminal,
            terminalSessionId = "terminal-1"
        )

        val ui = controller.attach(recipe, state)

        assertEquals(RunSurfaceContent.Terminal("terminal-1"), ui.content)
        assertFalse(ui.canCompleteCurrentStep)
        assertTrue(ui.canCloseInstance)
    }

    @Test
    fun `其他实例的迟到状态不能覆盖当前显示面`() {
        val recipe = recipe(KiteRecipeStep(id = "web", type = KiteRecipe.STEP_OPEN_WEB, url = "https://example.com"))
        controller.attach(recipe, state(surface = CardRunSurface.Web, nextActionUrl = "https://example.com"))

        val stale = controller.update(recipe, state(instanceId = "other", surface = CardRunSurface.Report))

        assertNull(stale)
    }

    @Test
    fun `页面离开只解绑显示目标`() {
        val recipe = recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"))
        controller.attach(recipe, state(surface = CardRunSurface.Terminal, terminalSessionId = "terminal-1"))

        controller.detach()

        assertNull(controller.update(recipe, state(surface = CardRunSurface.Terminal, terminalSessionId = "terminal-1")))
    }

    @Test
    fun `实例窗口由同一运行事实投影并标记当前显示面`() {
        val recipe = recipe(
            KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"),
            KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"),
            KiteRecipeStep(id = "web", type = KiteRecipe.STEP_OPEN_WEB, url = "https://example.com"),
            KiteRecipeStep(id = "x11", type = KiteRecipe.STEP_X11, cmd = "start-x11")
        )

        val ui = controller.attach(
            recipe,
            state(
                surface = CardRunSurface.Web,
                currentStepIndex = 2,
                terminalSessionId = "terminal-1",
                nextActionUrl = "https://www.example.com:8443/path",
                report = "执行完成",
                x11Display = ":1"
            )
        )

        assertEquals(
            listOf(
                CardRunSurface.Report,
                CardRunSurface.Terminal,
                CardRunSurface.Web,
                CardRunSurface.X11
            ),
            ui.windows.map(RunSurfaceWindowUiState::surface)
        )
        assertEquals(CardRunSurface.Web, ui.windows.single(RunSurfaceWindowUiState::selected).surface)
        assertEquals("example.com:8443", ui.windows.first { it.surface == CardRunSurface.Web }.subtitle)
    }

    @Test
    fun `终端作为第一步时通用进度不能生成 SH 报告窗口`() {
        val recipe = recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"))

        val ui = controller.attach(
            recipe,
            state(
                surface = CardRunSurface.Terminal,
                status = CardRunStatus.WaitingTerminal,
                terminalSessionId = "terminal-1",
                lastMeaningfulOutput = "等待终端完成"
            )
        )

        assertEquals(listOf(CardRunSurface.Terminal), ui.windows.map(RunSurfaceWindowUiState::surface))
        assertEquals("终端", ui.windows.single().title)
    }

    @Test
    fun `停止确认期间不再暴露重复停止动作`() {
        val recipe = recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"))

        val ui = controller.attach(
            recipe,
            state(
                surface = CardRunSurface.Terminal,
                status = CardRunStatus.Stopping,
                terminalSessionId = "terminal-1"
            )
        )

        assertFalse(ui.canCloseInstance)
    }

    private fun recipe(vararg steps: KiteRecipeStep): KiteRecipe = KiteRecipe(
        id = "recipe-1",
        name = "测试运行",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(steps.toList())
    )

    private fun state(
        instanceId: String = "instance-1",
        surface: CardRunSurface,
        status: CardRunStatus = CardRunStatus.Running,
        currentStepIndex: Int = 0,
        terminalSessionId: String? = null,
        nextActionUrl: String? = null,
        report: String? = null,
        lastMeaningfulOutput: String? = null,
        lastError: String? = null,
        x11Display: String? = null,
        agentId: String? = null,
        agentBinding: CardRunAgentBinding? = null
    ): CardRunState = CardRunState(
        instanceId = instanceId,
        recipeId = "recipe-1",
        recipeName = "测试运行",
        status = status,
        surface = surface,
        currentStepIndex = currentStepIndex,
        stepCount = 1,
        terminalSessionId = terminalSessionId,
        nextActionUrl = nextActionUrl,
        x11Display = x11Display,
        agentId = agentId,
        agentBinding = agentBinding,
        lastMeaningfulOutput = lastMeaningfulOutput,
        lastError = lastError,
        shellReportText = report,
        createdAt = 1L,
        updatedAt = 1L
    )
}
