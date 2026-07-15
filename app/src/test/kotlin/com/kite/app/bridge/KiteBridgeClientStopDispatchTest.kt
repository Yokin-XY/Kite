package com.kite.app.bridge

import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KiteBridgeClientStopDispatchTest {
    @Test
    fun `无直接绑定的停止只进入后台队列而不阻塞调用线程`() {
        val queuedExecutor = QueuedExecutor()
        val context = RuntimeEnvironment.getApplication()
        val client = KiteBridgeClient(
            diagnostics = KiteDiagnostics(context),
            appContext = context,
            stopExecutor = queuedExecutor
        )
        var callbackResult: BridgeResult? = null

        client.stopRun(
            recipe = recipe(),
            runId = "detached-run",
            cardInstanceId = "card-instance",
            runtimeOwnerIds = listOf("card:card-instance/1"),
            callback = { callbackResult = it }
        )

        assertEquals(1, queuedExecutor.pendingCount)
        assertNull(callbackResult)
    }

    @Test
    fun `按配方停止的兜底同样只进入后台队列`() {
        val queuedExecutor = QueuedExecutor()
        val context = RuntimeEnvironment.getApplication()
        val client = KiteBridgeClient(
            diagnostics = KiteDiagnostics(context),
            appContext = context,
            stopExecutor = queuedExecutor
        )
        var callbackResult: BridgeResult? = null

        client.stopRecipe(recipe()) { callbackResult = it }

        assertEquals(1, queuedExecutor.pendingCount)
        assertNull(callbackResult)
    }

    private fun recipe(): KiteRecipe = KiteRecipe(
        id = "bridge-stop-dispatch",
        name = "Bridge Stop Dispatch",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(
            listOf(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "sleep 10"))
        )
    )

    private class QueuedExecutor : Executor {
        private val tasks = mutableListOf<Runnable>()

        val pendingCount: Int
            get() = tasks.size

        override fun execute(command: Runnable) {
            tasks += command
        }
    }
}
