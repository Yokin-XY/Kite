package com.kite.app.platform.runtimemanagement

import android.content.Context
import com.kite.app.application.runtimemanagement.ProotEnvironmentInspection
import com.kite.app.application.runtimemanagement.ProotEnvironmentOperation
import com.kite.app.application.runtimemanagement.ProotViewAcceptanceCheck
import com.kite.app.application.runtimemanagement.ProotViewAcceptanceResult
import com.kite.app.application.runtimemanagement.ProotViewInspectionGateway
import com.kite.app.application.runtimemanagement.ProotViewInspectionSnapshot
import com.kite.app.foundation.runtime.AssetExtractor
import com.kite.app.foundation.runtime.ProotEnvironmentWorkspace
import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.ProotViewRuntime
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.KFWorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

/**
 * Android 侧 PRoot View 工程检查网关实现。
 *
 * 在 IO 协程里聚合 ProotViewStore 的只读查询为快照；UI 只 collect，不直接扫文件树。
 * runtime 不支持或容器未就绪时返回 available=false 的安全快照，不抛异常。
 */
internal class AndroidProotViewInspectionGateway(
    context: Context,
    private val onActiveEnvironmentChanged: (String) -> Unit = {},
    private val monotonicNanos: () -> Long = System::nanoTime,
) : ProotViewInspectionGateway {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val labRunner = ProotViewLabRunner(appContext)
    private val isolationRunner = ProotEnvironmentIsolationRunner(appContext)
    private val acceptanceRunner = ProotViewAcceptanceRunner(appContext)
    private val environmentOperationMutex = Mutex()
    private val environmentManager = ProotEnvironmentManager(
        containerProvider = {
            requireNotNull(WorkSurfaceRuntimeBridge.getSavedContainer(appContext)) {
                "容器未就绪"
            }
        },
    )
    // 构造发生在设置页面创建链路上，初值必须是纯内存状态。
    // catalog 恢复和空间统计统一由下面的 IO scope refresh() 完成，避免页面首帧被文件读取阻塞。
    private val mutable = MutableStateFlow(ProotViewInspectionSnapshot())

    override val snapshots: StateFlow<ProotViewInspectionSnapshot> = mutable.asStateFlow()

    init {
        refresh()
    }

    override fun currentSnapshot(): ProotViewInspectionSnapshot = mutable.value

    override fun refresh() {
        if (environmentOperationMutex.isLocked) return
        scope.launch {
            val previous = mutable.value
            mutable.value = probe().copy(
                lastVerification = previous.lastVerification,
                lastIsolationVerification = previous.lastIsolationVerification,
                environmentOperationTarget = previous.environmentOperationTarget,
                environmentOperationError = previous.environmentOperationError,
            )
        }
    }

    override fun runAcceptance() {
        if (!environmentOperationMutex.tryLock()) return
        mutable.value = mutable.value.copy(
            environmentOperation = ProotEnvironmentOperation.VerifyingAcceptance,
            environmentOperationTarget = "",
            environmentOperationError = "",
        )
        scope.launch {
            try {
                val execution = runCatching { acceptanceRunner.run() }
                val previous = mutable.value
                val refreshed = runCatching { probe() }.getOrElse { previous }
                refreshed.environmentId.takeIf { it.isNotBlank() }?.let(onActiveEnvironmentChanged)
                val failure = execution.exceptionOrNull()
                val acceptance = execution.getOrNull()?.result ?: ProotViewAcceptanceResult(
                    checks = listOf(ProotViewAcceptanceCheck(
                        id = "acceptance_execution",
                        title = "底层通用验收执行",
                        passed = false,
                        detail = failure?.message ?: failure?.javaClass?.simpleName.orEmpty(),
                    )),
                    environmentId = refreshed.environmentId,
                    viewId = refreshed.currentViewId,
                    atUnixMs = System.currentTimeMillis(),
                )
                mutable.value = refreshed.copy(
                    lastAcceptance = acceptance,
                    lastVerification = execution.getOrNull()?.viewVerification
                        ?: previous.lastVerification,
                    lastIsolationVerification = execution.getOrNull()?.environmentIsolation
                        ?: previous.lastIsolationVerification,
                    environmentOperation = ProotEnvironmentOperation.Idle,
                    environmentOperationError = when {
                        failure != null -> failure.message ?: failure.javaClass.simpleName
                        acceptance.success -> ""
                        else -> "底层通用验收存在失败项"
                    },
                )
            } finally {
                environmentOperationMutex.unlock()
            }
        }
    }

    override fun runVerification() {
        scope.launch {
            val result = runCatching { labRunner.run() }.getOrElse {
                com.kite.app.application.runtimemanagement.ProotViewVerificationResult(
                    success = false,
                    message = it.message ?: it.javaClass.simpleName,
                    atUnixMs = System.currentTimeMillis(),
                )
            }
            // 实验写入改变 View Upper，重新 probe 让工程页展示最新 Upper 状态。
            val refreshed = probe().copy(
                lastVerification = result,
                lastIsolationVerification = mutable.value.lastIsolationVerification,
            )
            mutable.value = refreshed
        }
    }

    override fun createEnvironment() {
        runEnvironmentOperation(
            operation = ProotEnvironmentOperation.Creating,
            initialTarget = "",
        ) {
            val container = requireNotNull(WorkSurfaceRuntimeBridge.getSavedContainer(appContext)) {
                "容器未就绪"
            }
            val store = ProotViewStore.forContainer(container)
            store.ensureInitialized()
            val environmentId = nextEnvironmentId(store.environmentCurrents().keys)
            environmentManager.createEnvironment(environmentId).getOrThrow()
        }
    }

    override fun switchEnvironment(environmentId: String) {
        var managerMs = 0L
        var reconciliationMs = 0L
        runEnvironmentOperation(
            operation = ProotEnvironmentOperation.Switching,
            initialTarget = environmentId,
            deferFullProbe = true,
            onMeasured = { metrics ->
                Logger.i(
                    "AndroidProotViewInspection",
                    "environment-switch target=${metrics.targetEnvironmentId} success=${metrics.success} " +
                        "managerMs=$managerMs reconcileMs=$reconciliationMs probeMs=${metrics.probeMs} " +
                        "storageStatsMs=${metrics.storageStatsMs} totalMs=${metrics.totalMs} " +
                        "error=${metrics.error.ifBlank { "none" }}",
                )
            },
        ) {
            val managerStartedAt = monotonicNanos()
            val binding = try {
                environmentManager.switchEnvironment(environmentId).getOrThrow()
            } finally {
                managerMs = elapsedMs(managerStartedAt)
            }
            val reconciliationStartedAt = monotonicNanos()
            try {
                onActiveEnvironmentChanged(binding.environmentId)
            } finally {
                reconciliationMs = elapsedMs(reconciliationStartedAt)
            }
            binding
        }
    }

    override fun runEnvironmentIsolationVerification() {
        if (!environmentOperationMutex.tryLock()) return
        mutable.value = mutable.value.copy(
            environmentOperation = ProotEnvironmentOperation.VerifyingIsolation,
            environmentOperationTarget = "",
            environmentOperationError = "",
        )
        scope.launch {
            try {
                val result = isolationRunner.run()
                val previous = mutable.value
                val refreshed = runCatching { probe() }.getOrElse { previous }
                refreshed.environmentId.takeIf { it.isNotBlank() }?.let(onActiveEnvironmentChanged)
                mutable.value = refreshed.copy(
                    lastVerification = previous.lastVerification,
                    lastIsolationVerification = result,
                    environmentOperation = ProotEnvironmentOperation.Idle,
                    environmentOperationError = if (result.success) "" else result.message,
                )
            } finally {
                environmentOperationMutex.unlock()
            }
        }
    }

    private fun runEnvironmentOperation(
        operation: ProotEnvironmentOperation,
        initialTarget: String,
        deferFullProbe: Boolean = false,
        onMeasured: (EnvironmentOperationMetrics) -> Unit = {},
        action: () -> ProotViewBinding,
    ) {
        if (!environmentOperationMutex.tryLock()) return
        mutable.value = mutable.value.copy(
            environmentOperation = operation,
            environmentOperationTarget = initialTarget,
            environmentOperationError = "",
        )
        scope.launch {
            val totalStartedAt = monotonicNanos()
            var target = initialTarget
            var error = ""
            var probeMs = 0L
            var storageStatsMs = 0L
            var deferredBinding: ProotViewBinding? = null
            try {
                val actionResult = runCatching { action() }
                    .onSuccess { target = it.environmentId }
                    .onFailure { error = it.message ?: it.javaClass.simpleName }
                val previous = mutable.value
                val binding = actionResult.getOrNull()
                val refreshed = if (deferFullProbe && binding != null) {
                    deferredBinding = binding
                    projectCompletedBinding(previous, binding)
                } else {
                    val probeStartedAt = monotonicNanos()
                    runCatching {
                        probe { measuredMs -> storageStatsMs = measuredMs }
                    }.also {
                        probeMs = elapsedMs(probeStartedAt)
                    }.getOrElse { probeError ->
                        previous.copy(
                            environmentOperationError = listOf(error, probeError.message.orEmpty())
                                .filter { it.isNotBlank() }
                                .joinToString("；"),
                        )
                    }
                }
                mutable.value = refreshed.copy(
                    lastVerification = previous.lastVerification,
                    lastIsolationVerification = previous.lastIsolationVerification,
                    environmentOperation = ProotEnvironmentOperation.Idle,
                    environmentOperationTarget = target,
                    environmentOperationError = refreshed.environmentOperationError.ifBlank { error },
                )
            } finally {
                onMeasured(
                    EnvironmentOperationMetrics(
                        targetEnvironmentId = target,
                        probeMs = probeMs,
                        storageStatsMs = storageStatsMs,
                        totalMs = elapsedMs(totalStartedAt),
                        success = error.isBlank(),
                        error = error,
                    )
                )
                environmentOperationMutex.unlock()
            }
            deferredBinding?.let(::refreshDeferredDiagnostics)
        }
    }

    private fun projectCompletedBinding(
        previous: ProotViewInspectionSnapshot,
        binding: ProotViewBinding,
    ): ProotViewInspectionSnapshot {
        val currentSpace = KFWorkspaceManager.currentSpaceState.value
            ?.takeIf { it.environmentId == binding.environmentId }
        val previousEnvironment = previous.environments.firstOrNull {
            it.environmentId == binding.environmentId
        }
        val targetEnvironment = ProotEnvironmentInspection(
            environmentId = binding.environmentId,
            viewId = binding.viewId,
            active = true,
            parentDepth = binding.parentViewIds.size,
            workspacePath = currentSpace?.workspacePath ?: previousEnvironment?.workspacePath.orEmpty(),
        )
        val environments = previous.environments
            .filterNot { it.environmentId == binding.environmentId }
            .map { it.copy(active = false) } + targetEnvironment
        return previous.copy(
            available = true,
            enabled = true,
            containerReady = true,
            currentViewId = binding.viewId,
            environmentId = binding.environmentId,
            spaceId = currentSpace?.id.orEmpty(),
            workspacePath = currentSpace?.workspacePath.orEmpty(),
            parentDepth = binding.parentViewIds.size,
            upperAllocatedBytes = null,
            upperLogicalBytes = 0L,
            scopeRootPaths = binding.scopeRootPaths,
            environments = environments.sortedWith(compareBy<ProotEnvironmentInspection> {
                it.environmentId != ProotViewStore.DEFAULT_ENVIRONMENT_ID
            }.thenBy { it.environmentId }),
        )
    }

    private fun refreshDeferredDiagnostics(binding: ProotViewBinding) {
        scope.launch {
            val startedAt = monotonicNanos()
            var storageStatsMs = 0L
            val refreshed = runCatching {
                probe { measuredMs -> storageStatsMs = measuredMs }
            }.getOrNull()
            val current = mutable.value
            if (
                refreshed != null &&
                refreshed.environmentId == binding.environmentId &&
                current.environmentId == binding.environmentId &&
                current.environmentOperation == ProotEnvironmentOperation.Idle
            ) {
                mutable.value = refreshed.copy(
                    lastVerification = current.lastVerification,
                    lastIsolationVerification = current.lastIsolationVerification,
                    environmentOperation = ProotEnvironmentOperation.Idle,
                    environmentOperationTarget = current.environmentOperationTarget,
                    environmentOperationError = current.environmentOperationError,
                )
            }
            Logger.i(
                "AndroidProotViewInspection",
                "environment-switch-diagnostics target=${binding.environmentId} " +
                    "applied=${refreshed != null && current.environmentId == binding.environmentId} " +
                    "storageStatsMs=$storageStatsMs totalMs=${elapsedMs(startedAt)}",
            )
        }
    }

    private fun probe(onStorageStatsMeasured: (Long) -> Unit = {}): ProotViewInspectionSnapshot {
        val container = runCatching {
            WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
        }.getOrNull() ?: return ProotViewInspectionSnapshot()
        val layout = runCatching { AssetExtractor.getRuntimeLayout(appContext) }.getOrNull()
            ?: return ProotViewInspectionSnapshot(containerReady = false)
        val descriptor = runCatching { readDescriptor(layout) }.getOrNull() ?: JSONObject()
        val runtimeSupported = ProotViewRuntime.run {
            descriptor.hasCapability(ProotViewStore.RUNTIME_CAPABILITY) &&
                descriptor.hasCapability(ProotViewStore.BLOCK_RUNTIME_CAPABILITY)
        }
        if (!runtimeSupported) {
            return ProotViewInspectionSnapshot(
                available = false,
                runtimeSupported = false,
                containerReady = true,
            )
        }
        val store = runCatching { ProotViewStore.forContainer(container) }.getOrNull()
            ?: return ProotViewInspectionSnapshot(
                available = false,
                runtimeSupported = true,
                containerReady = true,
            )
        val enabled = runCatching { store.isEnabled() }.getOrDefault(false)
        val snapshot = runCatching { store.catalogSnapshot() }.getOrNull()
        val activeEnvironmentId = if (enabled) {
            runCatching { store.activeEnvironmentId() }.getOrNull()
        } else null
        val currentBinding = if (enabled) runCatching { store.activeBinding() }.getOrNull() else null
        val environments = if (enabled) {
            store.environmentCurrents().entries
                .sortedWith(compareBy<Map.Entry<String, String>> {
                    it.key != ProotViewStore.DEFAULT_ENVIRONMENT_ID
                }.thenBy { it.key })
                .mapNotNull { (environmentId, viewId) ->
                    runCatching {
                        val binding = store.binding(viewId)
                        ProotEnvironmentInspection(
                            environmentId = environmentId,
                            viewId = binding.viewId,
                            active = environmentId == activeEnvironmentId,
                            parentDepth = binding.parentViewIds.size,
                            workspacePath = ProotEnvironmentWorkspace.plan(container, binding)
                                .workspaceDirectory.absolutePath,
                        )
                    }.getOrNull()
                }
        } else emptyList()
        val statsStartedAt = monotonicNanos()
        val stats = try {
            currentBinding?.let { runCatching { store.storageStats(it.viewId) }.getOrNull() }
        } finally {
            onStorageStatsMeasured(elapsedMs(statsStartedAt))
        }
        val currentSpace = runCatching { KFWorkspaceManager.getCurrentSpace(appContext) }
            .getOrNull()
            ?.takeIf { it.environmentId == currentBinding?.environmentId }
        val parentDepth = currentBinding?.parentViewIds?.size ?: 0
        return ProotViewInspectionSnapshot(
            available = true,
            enabled = enabled,
            runtimeSupported = true,
            containerReady = true,
            currentViewId = currentBinding?.viewId ?: snapshot?.currentViewId ?: "",
            environmentId = currentBinding?.environmentId ?: activeEnvironmentId.orEmpty(),
            spaceId = currentSpace?.id.orEmpty(),
            workspacePath = currentSpace?.workspacePath.orEmpty(),
            parentDepth = parentDepth,
            upperAllocatedBytes = stats?.totalAllocatedBytes,
            upperLogicalBytes = stats?.totalLogicalBytes ?: 0L,
            scopeRootPaths = currentBinding?.scopeRootPaths ?: snapshot?.scopeRootPaths ?: emptyList(),
            // Base 封存：catalog 已初始化即视为封存（initial Base 投影建立后不可变）。
            baseSealed = snapshot != null,
            environments = environments,
            lastAcceptance = acceptanceRunner.latest(),
        )
    }

    private fun nextEnvironmentId(existing: Set<String>): String {
        var ordinal = 2
        while ("profile_$ordinal" in existing) ordinal += 1
        return "profile_$ordinal"
    }

    private fun readDescriptor(layout: AssetExtractor.RuntimeLayout): JSONObject {
        // 复用 ResourceTransactionCoordinator.readRuntimeDescriptor 的等价逻辑。
        val file = layout.prootRuntimeDescriptorFile
        if (!file.isFile) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun JSONObject.hasCapability(capability: String): Boolean {
        val capabilities = optJSONArray("capabilities") ?: return false
        for (index in 0 until capabilities.length()) {
            if (capabilities.optString(index) == capability) return true
        }
        return false
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        ((monotonicNanos() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)

    private data class EnvironmentOperationMetrics(
        val targetEnvironmentId: String,
        val probeMs: Long,
        val storageStatsMs: Long,
        val totalMs: Long,
        val success: Boolean,
        val error: String,
    )
}

internal fun ProotViewInspectionSnapshot.bindingIdentityMatches(binding: ProotViewBinding): Boolean =
    available && currentViewId == binding.viewId && environmentId == binding.environmentId
