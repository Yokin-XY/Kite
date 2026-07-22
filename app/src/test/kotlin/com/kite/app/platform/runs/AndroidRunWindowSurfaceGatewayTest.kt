package com.kite.app.platform.runs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeExecutor
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RecipeStopRequest
import com.kite.app.application.runs.RunOwnedWindowsCloseResult
import com.kite.app.application.runs.StopExecutionOutcome
import com.kite.app.application.runs.StopExecutionResult
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.TestRecipes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidRunWindowSurfaceGatewayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        CardRunStore.resetForTest()
        context.getSharedPreferences("kite_card_run_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
        CardRunStore.initialize(context)
    }

    @After
    fun tearDown() {
        CardRunStore.resetForTest()
        context.getSharedPreferences("kite_card_run_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `close all stops grandchild before child and removes only confirmed generations`() {
        val recipe = TestRecipes.serviceRecipe("recursive-close")
        val root = seed(recipe, "root", parentInstanceId = null, runId = null)
        seed(recipe, "child", parentInstanceId = root.instanceId, runId = "child-run")
        seed(recipe, "grandchild", parentInstanceId = "child", runId = "grandchild-run")
        val executor = RecordingStopExecutor()
        val gateway = AndroidRunWindowSurfaceGateway(
            context = context,
            diagnostics = KiteDiagnostics(context),
            executor = executor
        )
        var closeResult: RunOwnedWindowsCloseResult? = null

        gateway.closeAll(root.instanceId, root.createdAt) { closeResult = it }

        assertEquals(listOf("grandchild", "child"), executor.stoppedInstanceIds)
        assertEquals(true, closeResult?.confirmed)
        assertNull(CardRunStore.get("grandchild"))
        assertNull(CardRunStore.get("child"))
        assertNotNull(CardRunStore.get("root"))
    }

    @Test
    fun `close all keeps child facts when process cleanup is unconfirmed`() {
        val recipe = TestRecipes.serviceRecipe("failed-child-close")
        val root = seed(recipe, "root", parentInstanceId = null, runId = null)
        seed(recipe, "child", parentInstanceId = root.instanceId, runId = "child-run")
        val executor = RecordingStopExecutor(failingInstanceId = "child")
        val gateway = AndroidRunWindowSurfaceGateway(
            context = context,
            diagnostics = KiteDiagnostics(context),
            executor = executor
        )
        var closeResult: RunOwnedWindowsCloseResult? = null

        gateway.closeAll(root.instanceId, root.createdAt) { closeResult = it }

        assertEquals(false, closeResult?.confirmed)
        assertEquals(listOf("child"), closeResult?.remainingInstanceIds)
        assertNotNull(CardRunStore.get("child"))
        assertNotNull(CardRunStore.get("root"))
    }

    @Test
    fun `close all sends manual terminal ownership to executor before removing child`() {
        val recipe = TestRecipes.serviceRecipe("manual-terminal-close")
        val root = seed(recipe, "root", parentInstanceId = null, runId = "root-run")
        CardRunStore.start(
            recipe = recipe,
            instanceId = "manual-terminal",
            parentInstanceId = root.instanceId,
            ownerKind = CardRunState.OWNER_KIND_TERMINAL
        )
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Opened,
            instanceId = "manual-terminal",
            parentInstanceId = root.instanceId,
            ownerKind = CardRunState.OWNER_KIND_TERMINAL,
            runtimeOwnerId = "terminal:manual-session/instance/manual-terminal/manual",
            ownedRuntimeOwnerIds = listOf("terminal:manual-session/instance/manual-terminal/manual"),
            runId = "manual-session",
            terminalSessionId = "manual-session"
        )
        val executor = RecordingStopExecutor()
        val gateway = AndroidRunWindowSurfaceGateway(
            context = context,
            diagnostics = KiteDiagnostics(context),
            executor = executor
        )

        gateway.closeAll(root.instanceId, root.createdAt) { }

        val request = executor.stopRequests.single()
        assertEquals("manual-terminal", request.instanceId)
        assertEquals("manual-session", request.terminalSessionId)
        assertEquals(
            listOf("terminal:manual-session/instance/manual-terminal/manual"),
            request.runtimeOwnerIds
        )
        assertNull(CardRunStore.get("manual-terminal"))
    }

    @Test
    fun `replay starts new generation even when old cleanup is not yet confirmed`() {
        val recipe = TestRecipes.serviceRecipe("replay-old-residue")
        val root = seed(recipe, "root", parentInstanceId = null, runId = null)
        val replayInstanceId = "root:step-replay:0"
        seed(recipe, replayInstanceId, parentInstanceId = root.instanceId, runId = "old-run")
        val executor = RecordingStopExecutor(failingInstanceId = replayInstanceId)
        val gateway = AndroidRunWindowSurfaceGateway(
            context = context,
            diagnostics = KiteDiagnostics(context),
            executor = executor
        )

        val accepted = gateway.replayWorkflowStep(recipe, root.instanceId, 0)

        assertEquals(true, accepted)
        assertEquals(1, executor.executeRequests.size)
        assertEquals(CardRunStatus.Running, CardRunStore.get(executor.executeRequests.single().instanceId)?.status)
    }

    private fun seed(
        recipe: com.kite.app.recipe.KiteRecipe,
        instanceId: String,
        parentInstanceId: String?,
        runId: String?
    ): CardRunState {
        CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            parentInstanceId = parentInstanceId,
            ownerKind = CardRunState.OWNER_KIND_STEP_REPLAY
        )
        return CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Running,
            instanceId = instanceId,
            parentInstanceId = parentInstanceId,
            ownerKind = CardRunState.OWNER_KIND_STEP_REPLAY,
            runId = runId
        )
    }
}

private class RecordingStopExecutor(
    private val failingInstanceId: String? = null
) : RecipeExecutor {
    val stoppedInstanceIds = mutableListOf<String>()
    val stopRequests = mutableListOf<RecipeStopRequest>()
    val executeRequests = mutableListOf<RecipeStepExecutionRequest>()

    override fun execute(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        executeRequests += request
    }

    override fun stop(request: RecipeStopRequest, callback: (StopExecutionResult) -> Unit) {
        stoppedInstanceIds += request.instanceId
        stopRequests += request
        callback(
            if (request.instanceId == failingInstanceId) {
                StopExecutionResult(StopExecutionOutcome.Failed, "模拟未确认")
            } else {
                StopExecutionResult(StopExecutionOutcome.Confirmed, "已停止")
            }
        )
    }
}
