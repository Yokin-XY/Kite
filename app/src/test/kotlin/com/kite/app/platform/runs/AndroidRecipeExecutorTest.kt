package com.kite.app.platform.runs

import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RecipeStopRequest
import com.kite.app.application.runs.StopExecutionOutcome
import com.kite.app.application.runs.StopExecutionResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidRecipeExecutorTest {
    private lateinit var executor: AndroidRecipeExecutor

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val diagnostics = KiteDiagnostics(context)
        executor = AndroidRecipeExecutor(
            context = context,
            bridgeClient = KiteBridgeClient(diagnostics, context),
            diagnostics = diagnostics
        )
    }

    @Test
    fun `可见 Web 步骤写入等待事实并发出打开 Effect`() {
        val request = request(
            KiteRecipeStep(
                id = "web",
                type = KiteRecipe.STEP_OPEN_WEB,
                url = "http://127.0.0.1:8648"
            )
        )
        var event: RecipeExecutionEvent? = null

        executor.execute(request) { event = it }

        val waiting = event as RecipeExecutionEvent.AwaitingUser
        assertEquals(CardRunStatus.Opened, waiting.mutation.status)
        assertEquals(CardRunSurface.Web, waiting.mutation.surface)
        assertEquals("http://127.0.0.1:8648", waiting.mutation.nextActionUrl)
        assertTrue(waiting.effect is com.kite.app.application.runs.RunExecutionEffect.OpenWeb)
    }

    @Test
    fun `静默 Web 步骤不创建等待点`() {
        val request = request(
            KiteRecipeStep(
                id = "web-silent",
                type = KiteRecipe.STEP_OPEN_WEB,
                url = "http://127.0.0.1:8648",
                surfaceMode = KiteRecipe.SURFACE_MODE_SILENT
            )
        )
        var event: RecipeExecutionEvent? = null

        executor.execute(request) { event = it }

        assertTrue(event is RecipeExecutionEvent.Completed)
        assertEquals(null, (event as RecipeExecutionEvent.Completed).mutation.nextActionUrl)
    }

    @Test
    fun `未知步骤返回结构化失败而不是触碰页面`() {
        val request = request(KiteRecipeStep(id = "bad", type = "unknown_step"))
        var event: RecipeExecutionEvent? = null

        executor.execute(request) { event = it }

        val failed = event as RecipeExecutionEvent.Failed
        assertEquals("unsupported_step:unknown_step", failed.message)
        assertEquals(false, failed.bridgeUnavailable)
    }

    @Test
    fun `尚未获得进程绑定的启动任务可以确定取消`() {
        val recipe = recipe(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "sleep 10"))
        var result: StopExecutionResult? = null

        executor.stop(
            RecipeStopRequest(
                recipe = recipe,
                instanceId = "preparing-instance"
            )
        ) { result = it }

        assertEquals(StopExecutionOutcome.Confirmed, result?.outcome)
        assertEquals("终端已发送中断并关闭", result?.message)
    }

    private fun request(step: KiteRecipeStep): RecipeStepExecutionRequest {
        val recipe = recipe(step)
        val state = CardRunState(
            instanceId = "executor-instance",
            recipeId = recipe.id,
            recipeName = recipe.name,
            status = CardRunStatus.Running,
            currentStepIndex = 0,
            createdAt = 100L,
            updatedAt = 100L
        )
        return RecipeStepExecutionRequest(
            recipe = recipe,
            instanceId = state.instanceId,
            generation = state.createdAt,
            stepIndex = 0,
            step = step,
            previousState = state
        )
    }

    private fun recipe(step: KiteRecipeStep): KiteRecipe = KiteRecipe(
            id = "executor-test",
            name = "Executor Test",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(listOf(step))
        )
}
