package com.kite.app.application.runtimemanagement

import com.kite.app.run.CardRunState

/** 运行管理消费的稳定事实快照，不包含 View、导航或页面展开状态。 */
data class RuntimeManagementSnapshot(
    val runs: List<CardRunState> = emptyList(),
    val terminals: List<RuntimeManagedTerminal> = emptyList(),
    val processes: List<RuntimeManagedProcess> = emptyList(),
    val cardIconsByRecipeId: Map<String, RuntimeManagedCardIcon> = emptyMap(),
    val topology: InstanceRuntimeTopology = InstanceRuntimeTopologyBuilder.build(runs, terminals, processes),
    val observedProcessCount: Int = 0,
    val refreshedAt: Long = 0L
)

/** 卡片配方已经声明的图标事实；位图加载仍由 UI 专用仓库异步完成。 */
data class RuntimeManagedCardIcon(
    val type: String = "builtin",
    val name: String = "default",
    val source: String = ""
)

data class RuntimeManagedTerminal(
    val id: String,
    val title: String,
    val statusLabel: String,
    val processCount: Int = 0,
    val rootPid: Int? = null,
    val observedPid: Int? = null,
    val isLive: Boolean = false
)

enum class RuntimeManagedOwnerKind {
    Card,
    Resource,
    Terminal,
    BackgroundRuntime,
    System,
    Unattributed
}

data class RuntimeManagedProcess(
    val id: String,
    val pid: Int,
    val parentPid: Int = 0,
    val title: String,
    val stateLabel: String,
    val commandLine: String = "",
    val purpose: String = "",
    val ownerKind: RuntimeManagedOwnerKind = RuntimeManagedOwnerKind.Unattributed,
    val ownerId: String? = null,
    val unitId: String? = null,
    val workloadScopeId: String? = null,
    val ownerRootPid: Int? = null,
    val linkedTerminalSessionId: String? = null,
    val linkedRuntimeId: String? = null,
    val isOwnerRoot: Boolean = false,
    val isRuntimeScaffold: Boolean = false,
    val canEndDirectly: Boolean = false,
    val lifecycleId: String? = null,
    val processGroupId: Int? = null,
    val kernelState: String = "UNKNOWN",
    val identityVerified: Boolean = false,
)
