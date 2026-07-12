package com.kite.app.application.runtimemanagement

import com.kite.app.run.CardRunState

/** 运行管理消费的稳定事实快照，不包含 View、导航或页面展开状态。 */
internal data class RuntimeManagementSnapshot(
    val runs: List<CardRunState> = emptyList(),
    val terminals: List<RuntimeManagedTerminal> = emptyList(),
    val processes: List<RuntimeManagedProcess> = emptyList(),
    val observedProcessCount: Int = 0,
    val refreshedAt: Long = 0L
)

internal data class RuntimeManagedTerminal(
    val id: String,
    val title: String,
    val statusLabel: String,
    val processCount: Int = 0,
    val rootPid: Int? = null,
    val observedPid: Int? = null,
    val isLive: Boolean = false
)

internal enum class RuntimeManagedOwnerKind {
    Card,
    Resource,
    Terminal,
    BackgroundRuntime,
    System,
    Unattributed
}

internal data class RuntimeManagedProcess(
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
    val ownerRootPid: Int? = null,
    val linkedTerminalSessionId: String? = null,
    val linkedRuntimeId: String? = null,
    val isOwnerRoot: Boolean = false,
    val canEndDirectly: Boolean = false
)
