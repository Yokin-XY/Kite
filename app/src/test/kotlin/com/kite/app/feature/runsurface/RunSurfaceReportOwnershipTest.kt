package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RunSurfaceReportOwnershipTest {
    @Test
    fun `终端流程的通用进度不生成 SH 报告窗口`() {
        val recipe = recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash"))
        val state = state(
            surface = CardRunSurface.Terminal,
            terminalSessionId = "terminal-1",
            lastMeaningfulOutput = "等待终端完成"
        )

        val ui = RunSurfaceProjector.project(recipe, state)

        assertEquals(listOf(CardRunSurface.Terminal), ui.windows.map(RunSurfaceWindowUiState::surface))
        assertEquals("终端", ui.windows.single().title)
    }

    @Test
    fun `真实 SH 步骤保留 SH 报告窗口`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"))
        val state = state(
            surface = CardRunSurface.Report,
            shellReportText = "ok"
        )

        val ui = RunSurfaceProjector.project(recipe, state)

        assertEquals(listOf(CardRunSurface.Report), ui.windows.map(RunSurfaceWindowUiState::surface))
        assertEquals("SH 报告", ui.windows.single().title)
    }

    @Test
    fun `SH 报告只展示终端输出而不展示资源心跳`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "install"))
        val state = state(
            surface = CardRunSurface.Report,
            shellReportText = "原始输出：\n" +
                "KITE_RESOURCE_HEARTBEAT stage=install step=tool elapsed=5\n" +
                "Downloading cryptography 20%\rDownloading cryptography 100%\n" +
                "Installed successfully",
        )

        val report = RunReportPresenter.project(recipe, state)

        assertEquals("Downloading cryptography 100%\nInstalled successfully", report.outputText)
        assertFalse(report.outputText.contains("HEARTBEAT"))
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
        surface: CardRunSurface,
        terminalSessionId: String? = null,
        lastMeaningfulOutput: String? = null,
        shellReportText: String? = null
    ): CardRunState = CardRunState(
        instanceId = "instance-1",
        recipeId = "recipe-1",
        recipeName = "测试运行",
        status = CardRunStatus.Running,
        surface = surface,
        currentStepIndex = 0,
        stepCount = 1,
        terminalSessionId = terminalSessionId,
        lastMeaningfulOutput = lastMeaningfulOutput,
        shellReportText = shellReportText,
        createdAt = 1L,
        updatedAt = 1L
    )
}
