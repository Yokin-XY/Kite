package com.kite.app.application.resources

import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeExecutor
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RecipeStopRequest
import com.kite.app.application.runs.RunLifecycleEvent
import com.kite.app.application.runs.RunLifecycleEventHub
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartGate
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceRunCoordinatorTest {
    @Test
    fun `资源运行先登记实例再投影安装状态`() {
        val gateway = FakeResourceRunGateway()
        val coordinator = coordinator(gateway, RunLifecycleEventHub())

        val accepted = coordinator.start(launch("tool", KiteResourceInstallRecipes.OP_INSTALL))
            as ResourceRunLaunchResult.Accepted

        assertEquals(
            listOf(FakeResourceRunGateway.OperationStart(
                resourceId = "tool",
                operation = KiteResourceInstallRecipes.OP_INSTALL,
                instanceId = accepted.state.instanceId,
                environmentId = "default",
            )),
            gateway.operationStarts,
        )
    }

    @Test
    fun `计划项启动被拒绝时写入失败而不伪装运行中`() {
        val gateway = FakeResourceRunGateway().apply {
            writeScopesByResource["blocker"] = setOf("command:shared")
            writeScopesByResource["queued"] = setOf("command:shared")
            pendingResources += "queued"
            plannedInstalls["queued"] = launch("queued", KiteResourceInstallRecipes.OP_INSTALL)
        }
        val coordinator = coordinator(gateway, RunLifecycleEventHub())
        assertTrue(coordinator.start(launch("blocker", KiteResourceInstallRecipes.OP_INSTALL)) is ResourceRunLaunchResult.Accepted)

        val started = coordinator.startNextPlannedInstall("wizard-instance")

        assertFalse(started)
        assertEquals(listOf("queued"), gateway.planStepsStarted)
        assertEquals(listOf("queued"), gateway.failedPlanResources)
        assertTrue(gateway.failedResources.single().reason.contains("resource_write_conflict"))
        assertTrue(gateway.startedRequests.none { it.resourceId == "queued" })
    }

    @Test
    fun `写入范围冲突被拒绝且释放后可以重新启动`() {
        val gateway = FakeResourceRunGateway().apply {
            writeScopesByResource["left"] = setOf("command:shared")
            writeScopesByResource["right"] = setOf("command:shared")
            writeScopesByResource["independent"] = setOf("command:other")
        }
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val leftRequest = launch("left", KiteResourceInstallRecipes.OP_INSTALL)
        val left = coordinator.start(leftRequest) as ResourceRunLaunchResult.Accepted

        val conflicting = coordinator.start(launch("right", KiteResourceInstallRecipes.OP_INSTALL))
        val independent = coordinator.start(launch("independent", KiteResourceInstallRecipes.OP_INSTALL))

        assertTrue(conflicting is ResourceRunLaunchResult.Rejected)
        assertTrue(independent is ResourceRunLaunchResult.Accepted)
        hub.onStateCommitted(
            RunLifecycleEvent(
                leftRequest.recipe,
                left.state.copy(status = CardRunStatus.Completed),
            ),
        )
        var restarted: ResourceRunLaunchResult.Accepted? = null
        waitUntil {
            if (restarted != null) {
                true
            } else {
                (coordinator.start(launch("right", KiteResourceInstallRecipes.OP_INSTALL))
                    as? ResourceRunLaunchResult.Accepted)
                    ?.also { restarted = it } != null
            }
        }
        assertTrue(restarted != null)
    }

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
        assertEquals(
            listOf("commit:tool", "clear:tool", "finalize:tool"),
            gateway.settlementOrder.take(3)
        )
        assertEquals(listOf("tool"), gateway.finalizedMutations)
        assertEquals(KiteResourceInstallRecipes.OP_INSTALL, gateway.startedRequests.last().operation)
    }

    @Test
    fun `失败安装清理成功后立即恢复向导队列`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        gateway.pendingResources += "tool"
        gateway.plannedInstalls["tool"] = launch("tool", KiteResourceInstallRecipes.OP_INSTALL)
        val cleanup = launch(
            "tool",
            KiteResourceInstallRecipes.OP_UNINSTALL,
            continuation = ResourceRunContinuation.ResumeInstallWizard,
        ).copy(parentInstanceId = "wizard-instance")
        val started = coordinator.start(cleanup) as ResourceRunLaunchResult.Accepted

        hub.onStateCommitted(
            RunLifecycleEvent(
                cleanup.recipe,
                started.state.copy(status = CardRunStatus.Completed),
            )
        )

        waitUntil { gateway.startedRequests.count { it.resourceId == "tool" } == 2 }
        assertEquals(KiteResourceInstallRecipes.OP_INSTALL, gateway.startedRequests.last().operation)
        assertEquals("wizard-instance", gateway.startedRequests.last().parentInstanceId)
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

    @Test
    fun `重新安装成功按安装事务结算且不进入安装队列`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val request = launch("tool", KiteResourceInstallRecipes.OP_REINSTALL)
        val started = coordinator.start(request) as ResourceRunLaunchResult.Accepted

        hub.onStateCommitted(
            RunLifecycleEvent(
                request.recipe,
                started.state.copy(status = CardRunStatus.Completed)
            )
        )

        waitUntil { gateway.installedResources.isNotEmpty() }
        assertEquals(listOf("tool"), gateway.installedResources)
        assertTrue(gateway.clearedResources.isEmpty())
        assertTrue(gateway.advancedResources.isEmpty())
    }

    @Test
    fun `更新成功先提交底层恢复点再登记新版本`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val request = launch("tool", KiteResourceInstallRecipes.OP_UPDATE)
        val started = coordinator.start(request) as ResourceRunLaunchResult.Accepted

        hub.onStateCommitted(
            RunLifecycleEvent(request.recipe, started.state.copy(status = CardRunStatus.Completed))
        )

        waitUntil { gateway.installedResources.isNotEmpty() }
        assertEquals(listOf("tool"), gateway.committedMutations)
        assertEquals(
            listOf("commit:tool", "installed:tool", "finalize:tool"),
            gateway.settlementOrder
        )
        assertEquals(listOf("tool"), gateway.finalizedMutations)
        assertTrue(gateway.rolledBackMutations.isEmpty())
    }

    @Test
    fun `更新失败先自动回滚再保留已安装失败事实`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val request = launch("tool", KiteResourceInstallRecipes.OP_UPDATE)
        val started = coordinator.start(request) as ResourceRunLaunchResult.Accepted

        hub.onStateCommitted(
            RunLifecycleEvent(
                request.recipe,
                started.state.copy(status = CardRunStatus.Failed, lastError = "download failed")
            )
        )

        waitUntil { gateway.failedResources.isNotEmpty() }
        assertEquals(listOf("tool"), gateway.rolledBackMutations)
        assertEquals("download failed", gateway.failedResources.single().reason)
        assertTrue(gateway.installedResources.isEmpty())
    }

    @Test
    fun `恢复点提交失败会回滚而不会登记更新成功`() {
        val gateway = FakeResourceRunGateway().apply { commitFailure = "checkpoint write failed" }
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val request = launch("tool", KiteResourceInstallRecipes.OP_UPDATE)
        val started = coordinator.start(request) as ResourceRunLaunchResult.Accepted

        hub.onStateCommitted(
            RunLifecycleEvent(request.recipe, started.state.copy(status = CardRunStatus.Completed))
        )

        waitUntil { gateway.failedResources.isNotEmpty() }
        assertEquals(listOf("tool"), gateway.rolledBackMutations)
        assertTrue(gateway.failedResources.single().reason.contains("checkpoint write failed"))
        assertTrue(gateway.installedResources.isEmpty())
    }

    @Test
    fun `安装向导只向协调器提交启动下一计划项`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        gateway.pendingResources += listOf("installed", "next")
        gateway.installedFacts += "installed"
        gateway.advanceResults["installed"] = listOf("next")
        gateway.plannedInstalls["next"] = launch("next", KiteResourceInstallRecipes.OP_INSTALL)

        assertTrue(coordinator.startNextPlannedInstall("wizard-instance"))

        assertEquals(listOf("installed"), gateway.advancedResources)
        assertEquals(listOf("next"), gateway.planStepsStarted)
        assertEquals("wizard-instance", gateway.startedRequests.single().parentInstanceId)
    }

    @Test
    fun `启动门拒绝时不写入资源处理中事实或推进安装队列`() {
        val gateway = FakeResourceRunGateway()
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(
            gateway = gateway,
            hub = hub,
            startGate = RunStartGate { "notification_permission_required" }
        )

        val result = coordinator.start(launch("blocked", KiteResourceInstallRecipes.OP_INSTALL))
        gateway.pendingResources += "blocked"

        assertEquals(
            ResourceRunLaunchResult.Rejected("notification_permission_required"),
            result
        )
        assertTrue(gateway.operationStarts.isEmpty())
        assertTrue(gateway.startedRequests.isEmpty())
        assertTrue(!coordinator.startNextPlannedInstall("wizard-instance"))
        assertTrue(gateway.planStepsStarted.isEmpty())
    }

    @Test
    fun `运行完成回执始终写回启动时环境而不跟随中途切换`() {
        val gateway = FakeResourceRunGateway().apply { activeEnvironmentId = "default" }
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        val request = launch("environment-bound", KiteResourceInstallRecipes.OP_INSTALL)
        val started = coordinator.start(request) as ResourceRunLaunchResult.Accepted

        gateway.activeEnvironmentId = "profile-2"
        hub.onStateCommitted(
            RunLifecycleEvent(
                request.recipe,
                started.state.copy(status = CardRunStatus.Completed, runId = "bound-run")
            )
        )

        waitUntil { gateway.installedEnvironments.isNotEmpty() }
        assertEquals(listOf("default"), gateway.installedEnvironments)
        assertEquals("default", gateway.startedRequests.single().environmentId)
    }

    @Test
    fun `环境切换会终结旧环境资源事务并执行更新回滚`() {
        val gateway = FakeResourceRunGateway().apply { activeEnvironmentId = "default" }
        val hub = RunLifecycleEventHub()
        val coordinator = coordinator(gateway, hub)
        coordinator.start(launch("environment-update", KiteResourceInstallRecipes.OP_UPDATE))

        coordinator.onEnvironmentStopped("default")

        waitUntil { gateway.failedResources.isNotEmpty() }
        assertEquals(listOf("environment-update"), gateway.rolledBackMutations)
        assertTrue(gateway.failedResources.single().reason.contains("环境已切换"))
        assertEquals("default", gateway.startedRequests.single().environmentId)
    }

    private fun coordinator(
        gateway: FakeResourceRunGateway,
        hub: RunLifecycleEventHub,
        startGate: RunStartGate = RunStartGate.Allow
    ): ResourceRunCoordinator = ResourceRunCoordinator(
        gateway = gateway,
        runOrchestrator = RunOrchestrator(
            stateGateway = FakeRunStateGateway(),
            executor = NoOpRecipeExecutor(),
            startGate = startGate
        ),
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
    data class OperationStart(
        val resourceId: String,
        val operation: String,
        val instanceId: String,
        val environmentId: String,
    )

    val startedRequests = CopyOnWriteArrayList<ResourceRunLaunchRequest>()
    val operationStarts = CopyOnWriteArrayList<OperationStart>()
    val installedResources = CopyOnWriteArrayList<String>()
    val installedEnvironments = CopyOnWriteArrayList<String>()
    val savedSnapshots = CopyOnWriteArrayList<String>()
    val failedResources = CopyOnWriteArrayList<Failure>()
    val clearedResources = CopyOnWriteArrayList<String>()
    val advancedResources = CopyOnWriteArrayList<String>()
    val failedPlanResources = CopyOnWriteArrayList<String>()
    val planStepsStarted = CopyOnWriteArrayList<String>()
    val committedMutations = CopyOnWriteArrayList<String>()
    val finalizedMutations = CopyOnWriteArrayList<String>()
    val rolledBackMutations = CopyOnWriteArrayList<String>()
    val settlementOrder = CopyOnWriteArrayList<String>()
    @Volatile var commitFailure: String? = null
    val advanceResults = ConcurrentHashMap<String, List<String>>()
    val plannedInstalls = ConcurrentHashMap<String, ResourceRunLaunchRequest>()
    val pendingResources = CopyOnWriteArrayList<String>()
    val installedFacts = ConcurrentHashMap.newKeySet<String>()
    val writeScopesByResource = ConcurrentHashMap<String, Set<String>>()
    @Volatile var activeEnvironmentId: String = "default"
    private val generation = AtomicLong(100L)

    override fun recipe(resourceId: String, operation: String, targetVersion: String?): KiteRecipe? =
        plannedInstalls[resourceId]?.recipe

    override fun isBundled(resourceId: String): Boolean = false

    override fun writeScopes(request: ResourceRunLaunchRequest): Set<String> =
        writeScopesByResource[request.resourceId] ?: setOf("resource:${request.resourceId}")

    override fun currentEnvironmentId(): String = activeEnvironmentId

    override fun beginRun(request: ResourceRunLaunchRequest): CardRunState {
        startedRequests += request
        val nextGeneration = generation.incrementAndGet()
        return CardRunState(
            instanceId = request.preferredInstanceId ?: "${request.resourceId}-$nextGeneration",
            recipeId = request.recipe.id,
            recipeName = request.recipe.name,
            ownerKind = CardRunState.OWNER_KIND_RESOURCE,
            stepId = request.resourceId,
            status = CardRunStatus.Starting,
            surface = CardRunSurface.Report,
            createdAt = nextGeneration,
            updatedAt = nextGeneration
        )
    }

    override fun prepare(
        request: ResourceRunLaunchRequest,
        instanceId: String,
        callback: (Result<Unit>) -> Unit
    ) = Unit

    override fun commitMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> {
        committedMutations += request.resourceId
        settlementOrder += "commit:${request.resourceId}"
        return commitFailure?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(Unit)
    }

    override fun finalizeMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> {
        finalizedMutations += request.resourceId
        settlementOrder += "finalize:${request.resourceId}"
        return Result.success(Unit)
    }

    override fun rollbackMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> {
        rolledBackMutations += request.resourceId
        settlementOrder += "rollback:${request.resourceId}"
        return Result.success(Unit)
    }

    override fun failRunPreparation(request: ResourceRunLaunchRequest, instanceId: String, message: String) = Unit

    override fun markOperationStarted(
        resourceId: String,
        operation: String,
        instanceId: String,
        environmentId: String,
    ) {
        operationStarts += OperationStart(resourceId, operation, instanceId, environmentId)
    }

    override fun markInstalled(
        resourceId: String,
        versionHint: String?,
        runId: String?,
        summary: String?,
        evidence: String?,
        environmentId: String
    ) {
        installedResources += resourceId
        installedEnvironments += environmentId
        settlementOrder += "installed:$resourceId"
    }

    override fun saveInstalledSnapshot(resourceId: String, environmentId: String) {
        savedSnapshots += resourceId
    }

    override fun markFailed(
        resourceId: String,
        operation: String,
        runId: String?,
        reason: String,
        environmentId: String
    ) {
        failedResources += Failure(resourceId, operation, reason)
    }

    override fun clearResource(resourceId: String, environmentId: String) {
        clearedResources += resourceId
        settlementOrder += "clear:$resourceId"
    }

    override fun advancePlanAfter(resourceId: String, environmentId: String): List<String> {
        advancedResources += resourceId
        return advanceResults[resourceId].orEmpty()
    }

    override fun failPlanAt(resourceId: String, environmentId: String) {
        failedPlanResources += resourceId
    }

    override fun clearPlan(environmentId: String) = Unit

    override fun resumePlanFrom(resourceId: String, environmentId: String): Boolean = true

    override fun isInstalled(resourceId: String, environmentId: String): Boolean = resourceId in installedFacts

    override fun markPlanStepRunning(resourceId: String, environmentId: String): Boolean {
        planStepsStarted += resourceId
        return true
    }

    override fun pendingPlanResourceIds(environmentId: String): List<String> = pendingResources.toList()

    override fun plannedInstall(
        resourceId: String,
        parentInstanceId: String?,
        environmentId: String
    ): ResourceRunLaunchRequest? =
        plannedInstalls[resourceId]?.copy(parentInstanceId = parentInstanceId, environmentId = environmentId)
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
