package com.kite.app.application.resources

import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunLifecycleEvent
import com.kite.app.application.runs.RunLifecycleEventHub
import com.kite.app.application.runs.RunLifecycleSink
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.recipe.KiteRecipe
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal enum class ResourceRunContinuation {
    None,
    Reinstall,
    CancelFailedInstall,
    ResumeInstallWizard
}

internal data class ResourceRunLaunchRequest(
    val resourceId: String,
    val recipe: KiteRecipe,
    val operation: String,
    val stageBundledResource: Boolean = false,
    val parentInstanceId: String? = null,
    val preferredInstanceId: String? = null,
    val continuation: ResourceRunContinuation = ResourceRunContinuation.None
)

internal sealed interface ResourceRunLaunchResult {
    data class Accepted(val state: CardRunState) : ResourceRunLaunchResult
    data class Rejected(val reason: String) : ResourceRunLaunchResult
}

internal interface ResourceRunGateway {
    fun recipe(resourceId: String, operation: String): KiteRecipe?
    fun isBundled(resourceId: String): Boolean
    fun beginRun(request: ResourceRunLaunchRequest): CardRunState
    fun prepare(request: ResourceRunLaunchRequest, callback: (Result<Unit>) -> Unit)
    fun failRunPreparation(request: ResourceRunLaunchRequest, instanceId: String, message: String)
    fun markOperationStarted(resourceId: String, operation: String)
    fun markInstalled(resourceId: String, runId: String?, summary: String?)
    fun saveInstalledSnapshot(resourceId: String)
    fun markFailed(resourceId: String, operation: String, runId: String?, reason: String)
    fun clearResource(resourceId: String)
    fun advancePlanAfter(resourceId: String): List<String>
    fun failPlanAt(resourceId: String)
    fun clearPlan()
    fun resumePlanFrom(resourceId: String): Boolean
    fun isInstalled(resourceId: String): Boolean
    fun markPlanStepRunning(resourceId: String): Boolean
    fun plannedInstall(resourceId: String, parentInstanceId: String?): ResourceRunLaunchRequest?
}

/**
 * 进程级资源运行协调器。页面只提交启动意图；资源登记和队列推进不依赖页面可见性。
 */
internal class ResourceRunCoordinator(
    private val gateway: ResourceRunGateway,
    private val runOrchestrator: RunOrchestrator,
    lifecycleHub: RunLifecycleEventHub
) : RunLifecycleSink {
    private data class ActiveRun(
        val request: ResourceRunLaunchRequest,
        @Volatile var lastRunId: String? = null
    )

    private val serialExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KiteResourceRunCoordinator").apply { isDaemon = true }
    }
    private val activeRuns = ConcurrentHashMap<String, ActiveRun>()
    private val settledGenerations = ConcurrentHashMap.newKeySet<String>()

    init {
        lifecycleHub.register(this)
    }

    fun recipe(resourceId: String, operation: String): KiteRecipe? =
        gateway.recipe(resourceId, operation)

    fun isBundled(resourceId: String): Boolean = gateway.isBundled(resourceId)

    fun start(request: ResourceRunLaunchRequest): ResourceRunLaunchResult {
        if (request.resourceId.isBlank()) return ResourceRunLaunchResult.Rejected("missing_resource_id")
        if (request.operation !in SUPPORTED_OPERATIONS) {
            return ResourceRunLaunchResult.Rejected("unsupported_operation:${request.operation}")
        }
        gateway.markOperationStarted(request.resourceId, request.operation)
        val state = gateway.beginRun(request)
        activeRuns[state.instanceId] = ActiveRun(request)
        gateway.prepare(request) { result ->
            serialExecutor.execute {
                result.onSuccess {
                    val current = activeRuns[state.instanceId] ?: return@onSuccess
                    val startResult = runOrchestrator.start(
                        RunStartRequest(
                            recipe = current.request.recipe,
                            instanceId = state.instanceId,
                            parentInstanceId = current.request.parentInstanceId,
                            ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                            stepId = current.request.resourceId
                        )
                    )
                    if (startResult is RunCommandResult.Ignored && startResult.reason != "instance_already_active") {
                        settlePreparationFailure(current, state.instanceId, "运行启动失败：${startResult.reason}")
                    }
                }.onFailure { error ->
                    val current = activeRuns[state.instanceId] ?: return@onFailure
                    settlePreparationFailure(
                        current,
                        state.instanceId,
                        "资源准备失败：${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
        return ResourceRunLaunchResult.Accepted(state)
    }

    fun owns(instanceId: String): Boolean = activeRuns.containsKey(instanceId)

    override fun onStateCommitted(event: RunLifecycleEvent) {
        val active = activeRuns[event.state.instanceId] ?: return
        event.state.runId?.takeIf { it.isNotBlank() }?.let { active.lastRunId = it }
        if (event.state.status !in TERMINAL_STATUSES) return
        val generationKey = "${event.state.instanceId}@${event.state.createdAt}"
        if (!settledGenerations.add(generationKey)) return
        serialExecutor.execute { settle(active, event.state) }
    }

    private fun settlePreparationFailure(active: ActiveRun, instanceId: String, message: String) {
        if (activeRuns.remove(instanceId, active)) {
            gateway.failRunPreparation(active.request, instanceId, message)
            gateway.markFailed(
                active.request.resourceId,
                active.request.operation,
                active.lastRunId,
                message
            )
            if (active.request.operation == KiteResourceInstallRecipes.OP_INSTALL) {
                gateway.failPlanAt(active.request.resourceId)
            }
        }
    }

    private fun settle(active: ActiveRun, state: CardRunState) {
        if (!activeRuns.remove(state.instanceId, active)) return
        when (state.status) {
            CardRunStatus.Completed -> settleSuccess(active, state)
            CardRunStatus.Stopped -> settleFailure(active, "${operationLabel(active.request.operation)}已取消")
            CardRunStatus.BridgeUnavailable,
            CardRunStatus.Failed -> settleFailure(
                active,
                state.lastError ?: state.lastMeaningfulOutput ?: "${operationLabel(active.request.operation)}失败"
            )
            else -> Unit
        }
    }

    private fun settleSuccess(active: ActiveRun, state: CardRunState) {
        val request = active.request
        when (request.operation) {
            KiteResourceInstallRecipes.OP_INSTALL -> {
                gateway.markInstalled(
                    request.resourceId,
                    active.lastRunId,
                    state.lastMeaningfulOutput
                )
                gateway.saveInstalledSnapshot(request.resourceId)
                startNextPlannedInstall(
                    gateway.advancePlanAfter(request.resourceId),
                    request.parentInstanceId
                )
            }
            KiteResourceInstallRecipes.OP_UNINSTALL -> {
                gateway.clearResource(request.resourceId)
                when (request.continuation) {
                    ResourceRunContinuation.Reinstall -> {
                        gateway.plannedInstall(request.resourceId, request.parentInstanceId)?.let(::start)
                            ?: gateway.markFailed(
                                request.resourceId,
                                KiteResourceInstallRecipes.OP_INSTALL,
                                null,
                                "卸载完成，但获取目标缺少资源定义"
                            )
                    }
                    ResourceRunContinuation.CancelFailedInstall -> gateway.clearPlan()
                    ResourceRunContinuation.ResumeInstallWizard -> gateway.resumePlanFrom(request.resourceId)
                    ResourceRunContinuation.None -> Unit
                }
            }
        }
    }

    private fun settleFailure(active: ActiveRun, reason: String) {
        val request = active.request
        gateway.markFailed(request.resourceId, request.operation, active.lastRunId, reason)
        if (request.operation == KiteResourceInstallRecipes.OP_INSTALL) {
            gateway.failPlanAt(request.resourceId)
        }
    }

    private fun startNextPlannedInstall(resourceIds: List<String>, parentInstanceId: String?) {
        var remaining = resourceIds
        while (remaining.isNotEmpty()) {
            val resourceId = remaining.first()
            if (gateway.isInstalled(resourceId)) {
                remaining = gateway.advancePlanAfter(resourceId)
                continue
            }
            val request = gateway.plannedInstall(resourceId, parentInstanceId)
            if (request == null) {
                gateway.markFailed(
                    resourceId,
                    KiteResourceInstallRecipes.OP_INSTALL,
                    null,
                    "执行队列缺少资源定义"
                )
                gateway.failPlanAt(resourceId)
                return
            }
            if (!gateway.markPlanStepRunning(resourceId)) return
            start(request)
            return
        }
    }

    private fun operationLabel(operation: String): String = when (operation) {
        KiteResourceInstallRecipes.OP_UNINSTALL -> "卸载"
        else -> "获取"
    }

    companion object {
        private val SUPPORTED_OPERATIONS = setOf(
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UNINSTALL
        )
        private val TERMINAL_STATUSES = setOf(
            CardRunStatus.Completed,
            CardRunStatus.Failed,
            CardRunStatus.Stopped,
            CardRunStatus.BridgeUnavailable
        )
    }
}
