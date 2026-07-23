package com.kite.app.feature.runtimemanagement

import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.run.KiteRunUiTone

internal enum class RuntimeManagementMutationPhase {
    Requested,
    AwaitingConfirmation,
    Failed
}

internal data class RuntimeManagementMutation(
    val key: String,
    val phase: RuntimeManagementMutationPhase,
    val message: String = ""
)

internal sealed interface RuntimeManagementActionTarget {
    data object Refresh : RuntimeManagementActionTarget
    data class OpenSurface(
        val recipeId: String,
        val instanceId: String,
        val surface: CardRunSurface
    ) : RuntimeManagementActionTarget

    data class StopRun(val instanceId: String) : RuntimeManagementActionTarget
    data class EndTerminal(val sessionId: String) : RuntimeManagementActionTarget
    data class EndProcess(val processId: String, val pid: Int) : RuntimeManagementActionTarget
    data class EndWorkloadScope(val workloadScopeId: String) : RuntimeManagementActionTarget
    data class StopBackgroundRuntime(val runtimeId: String) : RuntimeManagementActionTarget
    data class RestartBackgroundRuntime(val runtimeId: String) : RuntimeManagementActionTarget
}

internal data class RuntimeManagementActionUiState(
    val label: String,
    val target: RuntimeManagementActionTarget,
    val mutationKey: String,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val mutationPhase: RuntimeManagementMutationPhase? = null
)

internal data class RuntimeManagementSummaryUiState(
    val runningCards: Int = 0,
    val runningTerminals: Int = 0,
    val runningProcesses: Int = 0
)

internal data class RuntimeManagementSurfaceUiState(
    val key: String,
    val instanceId: String,
    val surface: CardRunSurface,
    val title: String,
    val caption: String,
    val openAction: RuntimeManagementActionUiState
)

internal data class RuntimeManagementProcessUiState(
    val key: String,
    val pid: Int,
    val parentPid: Int,
    val title: String,
    val subtitle: String,
    val stateLabel: String,
    val ownerLabel: String,
    val purpose: String,
    val commandLine: String,
    val cardInstanceId: String? = null,
    val cardLabel: String? = null,
    val depth: Int = 0,
    val isInfrastructure: Boolean = false,
    val canEndAsWorkload: Boolean = false,
    val processGroupId: Int? = null,
    val lifecycleId: String? = null,
    val kernelState: String = "UNKNOWN",
    val identityVerified: Boolean = false,
    val stopAction: RuntimeManagementActionUiState?
)

internal data class RuntimeManagementProcessGroupUiState(
    val key: String,
    val title: String,
    val processCount: Int,
    val processes: List<RuntimeManagementProcessUiState>,
    val workloadScopeId: String? = null,
    val cardLabels: List<String> = emptyList(),
    val isInfrastructure: Boolean = false,
    val stopAction: RuntimeManagementActionUiState? = null,
) {
    /** 数据层始终保留作用域；只有两个以上可见成员时，界面才需要额外的展开层。 */
    val isExpandable: Boolean
        get() = processes.size > 1
}

internal data class RuntimeManagementCardIconUiState(
    val type: String = "builtin",
    val name: String = "default",
    val source: String = ""
)

internal data class RuntimeManagementRunUiState(
    val instanceId: String,
    val recipeId: String,
    val title: String,
    val sourceLabel: String,
    val status: CardRunStatus,
    val statusLabel: String,
    val statusTone: KiteRunUiTone,
    val createdAt: Long,
    val icon: RuntimeManagementCardIconUiState = RuntimeManagementCardIconUiState(),
    val surfaces: List<RuntimeManagementSurfaceUiState>,
    val terminalTitle: String?,
    val processCount: Int,
    val processGroups: List<RuntimeManagementProcessGroupUiState>,
    val stopAction: RuntimeManagementActionUiState?
)

internal data class RuntimeManagementUiState(
    val summary: RuntimeManagementSummaryUiState = RuntimeManagementSummaryUiState(),
    val runs: List<RuntimeManagementRunUiState> = emptyList(),
    val allProcessGroups: List<RuntimeManagementProcessGroupUiState> = emptyList(),
    val unassignedProcessGroups: List<RuntimeManagementProcessGroupUiState> = emptyList(),
    val refreshedAt: Long = 0L
) {
    val isEmpty: Boolean
        get() = runs.isEmpty() && unassignedProcessGroups.isEmpty()
}

internal sealed interface RuntimeManagementFeatureAction {
    data class Refresh(val force: Boolean = false) : RuntimeManagementFeatureAction
    data class Submit(val action: RuntimeManagementActionUiState) : RuntimeManagementFeatureAction
    data class DismissFailure(val mutationKey: String) : RuntimeManagementFeatureAction
}

internal sealed interface RuntimeManagementFeatureEffect {
    data class OpenSurface(
        val recipeId: String,
        val instanceId: String,
        val surface: CardRunSurface
    ) : RuntimeManagementFeatureEffect

    data class ActionRejected(val reason: String) : RuntimeManagementFeatureEffect
}
