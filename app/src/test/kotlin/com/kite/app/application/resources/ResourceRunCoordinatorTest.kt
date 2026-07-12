package com.kite.app.application.resources

import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeExecutor
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RecipeStopRequest
import com.kite.app.application.runs.RunLifecycleEvent
import com.kite.app.application.runs.RunLifecycleEventHub
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.application.runs.RunStateGateway
import com.kite.app.application.runs.RunStateMutation
import com.kite.app.application.runs.StopExecutionResult
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceRunCoordinatorTest {
    @Test
    fun `安装完成登记快照并推进下一资源`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val first = launch("first", KiteResourceInstallRecipes.OP_INSTALL)
        val second = launch("second", KiteResourceInstallRecipes.OP_INSTALL)
        gateway.advanceResults["first"] = listOf("second")
        gateway.plannedInstalls["second"] = second

        val started = coordinator.start(first) as ResourceRunLaunchResult.Accepted
        hub.onStateCommitted(
            RunLifecycleEvent(
                first.recipe,
                started.state.copy(
                    status = CardRunStatus.Completed,
                    runId = "run-first",
                    lastMeaningfulOutput = "安装完成"
                )
            )
        )

        waitUntil { gateway.startedRequests.any { it.resourceId == "second" } }
        assertEquals(listOf("first"), gateway.installedResources)
        assertEquals(listOf("first"), gateway.savedSnapshots)
        assertEquals(listOf("second"), gateway.planStepsStarted)
        assertEquals(listOf("first"), gateway.advancedResources)
    }

    @Test
    fun `安装失败写入失败事实并阻断当前队列`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val request = launch("failed", KiteResourceInstallRecipes.OP_INSTALL)
        val started = coordinator.start(request) as ResourceRunLaunchResult.Accepted

        hub.onStateCommitted(
            RunLifecycleEvent(
                request.recipe,
                started.state.copy(
                    status = CardRunStatus.Failed,
                    lastError = "网络中断"
                )
            )
        )

        waitUntil { gateway.failedResources.isNotEmpty() }
        assertEquals("failed", gateway.failedResources.single().resourceId)
        assertEquals("网络中断", gateway.failedResources.single().reason)
        assertEquals(listOf("failed"), gateway.failedPlanResources)
    }

    @Test
    fun `卸载成功后的重新获取由进程协调器直接续接`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val reinstall = launch("tool", KiteResourceInstallRecipes.OP_INSTALL)
        gateway.plannedInstalls["tool"] = reinstall
        val uninstall = launch(
            "tool",
            KiteResourceInstallRecipes.OP_UNINSTALL,
            continuation = ResourceRunContinuation.Reinstall
        )
        val started = coordinator.start(uninstall) as ResourceRunLaunchResult.Accepted

        hub.onStateCommitted(
            RunLifecycleEvent(
                uninstall.recipe,
                started.state.copy(status = CardRunStatus.Completed)
            )
        )

        waitUntil { gateway.startedRequests.count { it.resourceId == "tool" } == 2 }
        assertEquals(listOf("tool"), gateway.clearedResources)
        assertEquals(KiteResourceInstallRecipes.OP_INSTALL, gateway.startedRequests.last().operation)
    }

    @Test
    fun `同一运行代次的重复终态只结算一次`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val request = launch("once", KiteResourceInstallRecipes.OP_INSTALL)
        val started = coordinator.start(request) as ResourceRunLaunchResult.Accepted
        val completed = started.state.copy(status = CardRunStatus.Completed)

        hub.onStateCommitted(RunLifecycleEvent(request.recipe, completed))
        hub.onStateCommitted(RunLifecycleEvent(request.recipe, completed.copy(updatedAt = completed.updatedAt + 1)))

        waitUntil { gateway.installedResources.isNotEmpty() }
        assertEquals(1, gateway.installedResources.size)
        assertEquals(1, gateway.savedSnapshots.size)
    }

    private fun coordinator(
        gateway: FakeResourceRunGateway,
        hub: RunLifecycleEventHub
    ): ResourceRunCoordinator = ResourceRunCoordinator(
        gateway = gateway,
        runOrchestrator = RunOrchestrator(FakeRunStateGateway(), NoOpRecipeExecutor()),
        lifecycleHub = hub
    )

    private fun launch(
        resourceId: String,
        operation: String,
        continuation: ResourceRunContinuation = ResourceRunContinuation.None
    ): ResourceRunLaunchRequest {
        val recipe = KiteRecipe(
            id = KiteResourceInstallRecipes.recipeId(resourceId, operation),
            name = resourceId,
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep(id = "step", type = KiteRecipe.STEP_SHELL, cmd = "true"))
            ),
            runtimeSource = KiteResourceInstallRecipes.RUNTIME_SOURCE
        )
        return ResourceRunLaunchRequest(
            resourceId = resourceId,
            recipe = recipe,
            operation = operation,
            continuation = continuation
        )
    }

    private fun waitUntil(assertion: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (System.currentTimeMillis() < deadline && !assertion()) {
            Thread.sleep(10L)
        }
        assertTrue(assertion())
    }
}

private class FakeResourceRunGateway : ResourceRunGateway {
    data class Failure(val resourceId: String, val operation: String, val reason: String)

    val startedRequests = mutableListOf<ResourceRunLaunchRequest>()
    val installedResources = mutableListOf<String>()
    val savedSnapshots = mutableListOf<String>()
    val failedResources = mutableListOf<Failure>()
    val clearedResources = mutableListOf<String>()
    val advancedResources = mutableListOf<String>()
    val failedPlanResources = mutableListOf<String>()
    val planStepsStarted = mutableListOf<String>()
    val advanceResults = mutableMapOf<String, List<String>>()
    val plannedInstalls = mutableMapOf<String, ResourceRunLaunchRequest>()
    private var generation = 100L

    override fun beginRun(request: ResourceRunLaunchRequest): CardRunState {
        startedRequests += request
        generation += 1
        return CardRunState(
            instanceId = request.preferredInstanceId ?: "${request.resourceId}-$generation",
            recipeId = request.recipe.id,
            recipeName = request.recipe.name,
            ownerKind = CardRunState.OWNER_KIND_RESOURCE,
            stepId = request.resourceId,
            status = CardRunStatus.Starting,
            surface = CardRunSurface.Report,
            createdAt = generation,
            updatedAt = generation
        )
    }

    override fun prepare(request: ResourceRunLaunchRequest, callback: (Result<Unit>) -> Unit) = Unit

    override fun failRunPreparation(request: ResourceRunLaunchRequest, instanceId: String, message: String) = Unit

    override fun markOperationStarted(resourceId: String, operation: String) = Unit

    override fun markInstalled(resourceId: String, runId: String?, summary: String?) {
        installedResources += resourceId
    }

    override fun saveInstalledSnapshot(resourceId: String) {
        savedSnapshots += resourceId
    }

    override fun markFailed(resourceId: String, operation: String, runId: String?, reason: String) {
        failedResources += Failure(resourceId, operation, reason)
    }

    override fun clearResource(resourceId: String) {
        clearedResources += resourceId
    }

    override fun advancePlanAfter(resourceId: String): List<String> {
        advancedResources += resourceId
        return advanceResults[resourceId].orEmpty()
    }

    override fun failPlanAt(resourceId: String) {
        failedPlanResources += resourceId
    }

    override fun clearPlan() = Unit

    override fun resumePlanFrom(resourceId: String): Boolean = true

    override fun isInstalled(resourceId: String): Boolean = false

    override fun markPlanStepRunning(resourceId: String): Boolean {
        planStepsStarted += resourceId
        return true
    }

    override fun plannedInstall(resourceId: String, parentInstanceId: String?): ResourceRunLaunchRequest? =
        plannedInstalls[resourceId]?.copy(parentInstanceId = parentInstanceId)
}

private class FakeRunStateGateway : RunStateGateway {
    private val recipes = mutableMapOf<String, KiteRecipe>()
    private val states = mutableMapOf<String, CardRunState>()

    override fun register(recipe: KiteRecipe) {
        recipes[recipe.id] = recipe
    }

    override fun recipe(recipeId: String): KiteRecipe? = recipes[recipeId]

    override fun state(instanceId: String): CardRunState? = states[instanceId]

    override fun current(recipeId: String): CardRunState? =
        states.values.lastOrNull { it.recipeId == recipeId }

    override fun start(request: RunStartRequest): CardRunState = CardRunState(
        instanceId = request.instanceId,
        recipeId = request.recipe.id,
        recipeName = request.recipe.name,
        status = CardRunStatus.Starting,
        createdAt = 1L,
        updatedAt = 1L
    ).also { states[it.instanceId] = it }

    override fun update(recipe: KiteRecipe, instanceId: String, mutation: RunStateMutation): CardRunState {
        val previous = states.getValue(instanceId)
        return previous.copy(
            status = mutation.status,
            surface = mutation.surface ?: previous.surface,
            currentStepIndex = mutation.currentStepIndex ?: previous.currentStepIndex,
            updatedAt = previous.updatedAt + 1
        ).also { states[instanceId] = it }
    }
}

private class NoOpRecipeExecutor : RecipeExecutor {
    override fun execute(request: RecipeStepExecutionRequest, callback: (RecipeExecutionEvent) -> Unit) = Unit
    override fun stop(request: RecipeStopRequest, callback: (StopExecutionResult) -> Unit) = Unit
}
