package com.kite.app.application.resources

import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunLifecycleEvent
import com.kite.app.application.runs.RunLifecycleEventHub
import com.kite.app.application.runs.RunLifecycleSink
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.foundation.concurrency.WriteScopeLeaseRegistry
import com.kite.app.recipe.KiteRecipe
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.UUID

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
    val targetVersion: String? = null,
    val stageBundledResource: Boolean = false,
    val parentInstanceId: String? = null,
    val preferredInstanceId: String? = null,
    val continuation: ResourceRunContinuation = ResourceRunContinuation.None,
    val environmentId: String = ""
)

internal sealed interface ResourceRunLaunchResult {
    data class Accepted(val state: CardRunState) : ResourceRunLaunchResult
    data class Rejected(val reason: String) : ResourceRunLaunchResult
}

internal interface ResourceRunGateway {
    fun recipe(resourceId: String, operation: String, targetVersion: String? = null): KiteRecipe?
    fun isBundled(resourceId: String): Boolean
    fun writeScopes(request: ResourceRunLaunchRequest): Set<String> =
        setOf("resource:${request.resourceId}")
    fun currentEnvironmentId(): String
    fun beginRun(request: ResourceRunLaunchRequest): CardRunState
    fun prepare(
        request: ResourceRunLaunchRequest,
        instanceId: String,
        callback: (Result<Unit>) -> Unit
    )
    fun commitMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        Result.success(Unit)
    fun markMutationInstalling(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        Result.success(Unit)
    fun markMutationVerified(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        Result.success(Unit)
    fun finalizeMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        Result.success(Unit)
    fun rollbackMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        Result.success(Unit)
    fun failRunPreparation(request: ResourceRunLaunchRequest, instanceId: String, message: String)
    fun markOperationStarted(
        resourceId: String,
        operation: String,
        instanceId: String,
        environmentId: String,
    )
    fun markInstalled(
        resourceId: String,
        versionHint: String?,
        runId: String?,
        summary: String?,
        evidence: String?,
        environmentId: String
    )
    fun saveInstalledSnapshot(resourceId: String, environmentId: String)
    fun markFailed(resourceId: String, operation: String, runId: String?, reason: String, environmentId: String)
    fun clearResource(resourceId: String, environmentId: String)
    fun advancePlanAfter(resourceId: String, environmentId: String): List<String>
    fun failPlanAt(resourceId: String, environmentId: String)
    fun clearPlan(environmentId: String)
    fun resumePlanFrom(resourceId: String, environmentId: String): Boolean
    fun isInstalled(resourceId: String, environmentId: String): Boolean
    fun markPlanStepRunning(resourceId: String, environmentId: String): Boolean
    fun pendingPlanResourceIds(environmentId: String): List<String>
    fun plannedInstall(resourceId: String, parentInstanceId: String?, environmentId: String): ResourceRunLaunchRequest?
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
        val writeScopeOwnerId: String,
        @Volatile var lastRunId: String? = null
    )

    private val serialExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KiteResourceRunCoordinator").apply { isDaemon = true }
    }
    private val activeRuns = ConcurrentHashMap<String, ActiveRun>()
    private val settledGenerations = ConcurrentHashMap.newKeySet<String>()
    private val writeScopeLeases = WriteScopeLeaseRegistry()

    init {
        lifecycleHub.register(this)
    }

    fun recipe(resourceId: String, operation: String, targetVersion: String? = null): KiteRecipe? =
        gateway.recipe(resourceId, operation, targetVersion)

    fun isBundled(resourceId: String): Boolean = gateway.isBundled(resourceId)

    fun startRejectionReason(): String? = runOrchestrator.startRejection()?.reason

    fun start(request: ResourceRunLaunchRequest): ResourceRunLaunchResult {
        if (request.resourceId.isBlank()) return ResourceRunLaunchResult.Rejected("missing_resource_id")
        if (request.operation !in SUPPORTED_OPERATIONS) {
            return ResourceRunLaunchResult.Rejected("unsupported_operation:${request.operation}")
        }
        startRejectionReason()?.let { return ResourceRunLaunchResult.Rejected(it) }
        val boundRequest = request.copy(
            environmentId = request.environmentId.ifBlank(gateway::currentEnvironmentId)
        )
        val writeScopeOwnerId = "resource-launch-${UUID.randomUUID()}"
        val writeScopes = environmentWriteScopes(boundRequest)
        writeScopeLeases.tryAcquire(writeScopeOwnerId, writeScopes)?.let { conflict ->
            return ResourceRunLaunchResult.Rejected("resource_write_conflict:${conflict.scope}")
        }
        val state = try {
            gateway.beginRun(boundRequest)
        } catch (error: Throwable) {
            writeScopeLeases.release(writeScopeOwnerId)
            throw error
        }
        val active = ActiveRun(boundRequest, writeScopeOwnerId)
        activeRuns[state.instanceId] = active
        try {
            gateway.markOperationStarted(
                resourceId = boundRequest.resourceId,
                operation = boundRequest.operation,
                instanceId = state.instanceId,
                environmentId = boundRequest.environmentId,
            )
        } catch (error: Throwable) {
            activeRuns.remove(state.instanceId, active)
            releaseWriteScopes(active)
            throw error
        }
        runCatching {
            gateway.prepare(boundRequest, state.instanceId) { result ->
                serialExecutor.execute {
                    result.onSuccess {
                        val current = activeRuns[state.instanceId] ?: return@onSuccess
                        val installing = gateway.markMutationInstalling(current.request, state.instanceId)
                        if (installing.isFailure) {
                            settlePreparationFailure(
                                current,
                                state.instanceId,
                                "资源安装状态落盘失败：${installing.exceptionOrNull()?.message ?: "未知错误"}",
                            )
                            return@onSuccess
                        }
                        val startResult = runOrchestrator.start(
                            RunStartRequest(
                                recipe = current.request.recipe,
                                instanceId = state.instanceId,
                                parentInstanceId = current.request.parentInstanceId,
                                ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                                stepId = current.request.resourceId,
                                environmentId = current.request.environmentId
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
        }.onFailure { error ->
            settlePreparationFailure(
                active,
                state.instanceId,
                "资源准备失败：${error.message ?: error.javaClass.simpleName}"
            )
        }
        return ResourceRunLaunchResult.Accepted(state)
    }

    fun owns(instanceId: String): Boolean = activeRuns.containsKey(instanceId)

    /** PRoot 已确认旧环境进程退出后，终结仍登记中的资源事务，避免后台队列跨环境续跑。 */
    fun onEnvironmentStopped(environmentId: String) {
        val target = environmentId.trim()
        if (target.isBlank()) return
        serialExecutor.execute {
            activeRuns.entries
                .filter { (_, active) -> active.request.environmentId == target }
                .forEach { (instanceId, active) ->
                    if (activeRuns.remove(instanceId, active)) {
                        try {
                            settleFailure(active, instanceId, "环境已切换，原环境资源任务已结束")
                        } finally {
                            releaseWriteScopes(active)
                        }
                    }
                }
        }
    }

    fun startNextPlannedInstall(parentInstanceId: String?): Boolean {
        val environmentId = gateway.currentEnvironmentId()
        return startNextPlannedInstall(
            gateway.pendingPlanResourceIds(environmentId),
            parentInstanceId,
            environmentId
        )
    }

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
            try {
                val finalMessage = rollbackFailureMessage(active.request, instanceId, message)
                gateway.failRunPreparation(active.request, instanceId, finalMessage)
                gateway.markFailed(
                    active.request.resourceId,
                    active.request.operation,
                    active.lastRunId,
                    finalMessage,
                    active.request.environmentId
                )
                if (active.request.operation == KiteResourceInstallRecipes.OP_INSTALL) {
                    gateway.failPlanAt(active.request.resourceId, active.request.environmentId)
                }
            } finally {
                releaseWriteScopes(active)
            }
        }
    }

    private fun settle(active: ActiveRun, state: CardRunState) {
        if (!activeRuns.remove(state.instanceId, active)) return
        try {
            when (state.status) {
                CardRunStatus.Completed -> settleSuccess(active, state)
                CardRunStatus.Stopped -> settleFailure(
                    active,
                    state.instanceId,
                    "${operationLabel(active.request.operation)}已取消"
                )
                CardRunStatus.BridgeUnavailable,
                CardRunStatus.Failed -> settleFailure(
                    active,
                    state.instanceId,
                    state.lastError ?: state.lastMeaningfulOutput ?: "${operationLabel(active.request.operation)}失败"
                )
                else -> Unit
            }
        } finally {
            releaseWriteScopes(active)
        }
    }

    private fun settleSuccess(active: ActiveRun, state: CardRunState) {
        val request = active.request
        val verified = gateway.markMutationVerified(request, state.instanceId)
        if (verified.isFailure) {
            settleFailure(
                active,
                state.instanceId,
                "${operationLabel(request.operation)}验证状态落盘失败：" +
                    (verified.exceptionOrNull()?.message ?: "未知错误"),
            )
            return
        }
        val commit = gateway.commitMutation(request, state.instanceId)
        if (commit.isFailure) {
            settleFailure(
                active,
                state.instanceId,
                "${operationLabel(request.operation)}保护点提交失败：${commit.exceptionOrNull()?.message ?: "未知错误"}"
            )
            return
        }
        when (request.operation) {
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR -> {
                gateway.markInstalled(
                    request.resourceId,
                    request.targetVersion,
                    active.lastRunId,
                    state.lastMeaningfulOutput,
                    state.shellReportText,
                    request.environmentId
                )
                gateway.saveInstalledSnapshot(request.resourceId, request.environmentId)
                // View 已经原子切换，资源登记也已经落盘；收尾失败只留给启动恢复补齐，
                // 不能把已成功的更新重新解释成失败并触发回滚。
                gateway.finalizeMutation(request, state.instanceId)
                if (request.operation == KiteResourceInstallRecipes.OP_INSTALL) {
                    releaseWriteScopes(active)
                    startNextPlannedInstall(
                        gateway.advancePlanAfter(request.resourceId, request.environmentId),
                        request.parentInstanceId,
                        request.environmentId
                    )
                }
            }
            KiteResourceInstallRecipes.OP_UNINSTALL -> {
                gateway.clearResource(request.resourceId, request.environmentId)
                // 卸载也在独立 View 中提交。先收尾释放 writer，再续接重新安装，
                // 否则下一次资源变更会被仍处于 VIEW_COMMITTED 的事务正确拒绝。
                gateway.finalizeMutation(request, state.instanceId)
                releaseWriteScopes(active)
                when (request.continuation) {
                    ResourceRunContinuation.Reinstall -> {
                        gateway.plannedInstall(
                            request.resourceId,
                            request.parentInstanceId,
                            request.environmentId
                        )?.let(::start)
                            ?: gateway.markFailed(
                                request.resourceId,
                                KiteResourceInstallRecipes.OP_INSTALL,
                                null,
                                "卸载完成，但获取目标缺少资源定义",
                                request.environmentId
                            )
                    }
                    ResourceRunContinuation.CancelFailedInstall -> gateway.clearPlan(request.environmentId)
                    ResourceRunContinuation.ResumeInstallWizard -> {
                        if (gateway.resumePlanFrom(request.resourceId, request.environmentId)) {
                            startNextPlannedInstall(
                                gateway.pendingPlanResourceIds(request.environmentId),
                                request.parentInstanceId,
                                request.environmentId,
                            )
                        }
                    }
                    ResourceRunContinuation.None -> Unit
                }
            }
        }
    }

    private fun settleFailure(active: ActiveRun, instanceId: String, reason: String) {
        val request = active.request
        gateway.markFailed(
            request.resourceId,
            request.operation,
            active.lastRunId,
            rollbackFailureMessage(request, instanceId, reason),
            request.environmentId
        )
        if (request.operation == KiteResourceInstallRecipes.OP_INSTALL) {
            gateway.failPlanAt(request.resourceId, request.environmentId)
        }
    }

    private fun rollbackFailureMessage(
        request: ResourceRunLaunchRequest,
        instanceId: String,
        reason: String
    ): String {
        val rollback = gateway.rollbackMutation(request, instanceId)
        return rollback.exceptionOrNull()?.let { error ->
            "$reason；自动恢复失败：${error.message ?: error.javaClass.simpleName}"
        } ?: reason
    }

    private fun releaseWriteScopes(active: ActiveRun) {
        writeScopeLeases.release(active.writeScopeOwnerId)
    }

    private fun environmentWriteScopes(request: ResourceRunLaunchRequest): Set<String> {
        val environment = request.environmentId.trim().ifBlank { "default" }
        return gateway.writeScopes(request)
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapTo(linkedSetOf()) { scope ->
                if (scope.startsWith("global:")) scope else "environment:$environment:$scope"
            }
            .ifEmpty { setOf("environment:$environment:resource:${request.resourceId}") }
    }

    private fun startNextPlannedInstall(
        resourceIds: List<String>,
        parentInstanceId: String?,
        environmentId: String
    ): Boolean {
        if (startRejectionReason() != null) return false
        var remaining = resourceIds
        while (remaining.isNotEmpty()) {
            val resourceId = remaining.first()
            if (gateway.isInstalled(resourceId, environmentId)) {
                remaining = gateway.advancePlanAfter(resourceId, environmentId)
                continue
            }
            val request = gateway.plannedInstall(resourceId, parentInstanceId, environmentId)
            if (request == null) {
                gateway.markFailed(
                    resourceId,
                    KiteResourceInstallRecipes.OP_INSTALL,
                    null,
                    "执行队列缺少资源定义",
                    environmentId
                )
                gateway.failPlanAt(resourceId, environmentId)
                return false
            }
            if (!gateway.markPlanStepRunning(resourceId, environmentId)) return false
            return when (val result = start(request)) {
                is ResourceRunLaunchResult.Accepted -> true
                is ResourceRunLaunchResult.Rejected -> {
                    gateway.markFailed(
                        resourceId = resourceId,
                        operation = KiteResourceInstallRecipes.OP_INSTALL,
                        runId = null,
                        reason = "资源运行未启动：${result.reason}",
                        environmentId = environmentId,
                    )
                    gateway.failPlanAt(resourceId, environmentId)
                    false
                }
            }
        }
        return false
    }

    private fun operationLabel(operation: String): String = when (operation) {
        KiteResourceInstallRecipes.OP_UNINSTALL -> "卸载"
        KiteResourceInstallRecipes.OP_UPDATE -> "更新"
        KiteResourceInstallRecipes.OP_REINSTALL -> "重新安装"
        KiteResourceInstallRecipes.OP_REPAIR -> "修复"
        else -> "获取"
    }

    companion object {
        private val SUPPORTED_OPERATIONS = setOf(
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR,
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
