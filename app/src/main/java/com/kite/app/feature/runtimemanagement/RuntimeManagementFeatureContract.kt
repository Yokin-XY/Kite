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
    data class StopBackgroundRuntime(val runtimeId: String) : RuntimeManagementActionTarget
    data class RestartBackgroundRuntime(val runtimeId: String) : RuntimeManagementActionTarget
    data class ViewBackgroundRuntimeLog(val runtimeId: String) : RuntimeManagementActionTarget
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
    val ownerLabel: String,
    val purpose: String,
    val isMain: Boolean,
    val stopAction: RuntimeManagementActionUiState?
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
    val surfaces: List<RuntimeManagementSurfaceUiState>,
    val terminalTitle: String?,
    val processCount: Int,
    val mainProcess: RuntimeManagementProcessUiState?,
    val childProcesses: List<RuntimeManagementProcessUiState>,
    val stopAction: RuntimeManagementActionUiState?
)

internal data class RuntimeManagementProcessSectionUiState(
    val key: String,
    val title: String,
    val processes: List<RuntimeManagementProcessUiState>
)

internal data class RuntimeManagementUiState(
    val summary: RuntimeManagementSummaryUiState = RuntimeManagementSummaryUiState(),
    val runs: List<RuntimeManagementRunUiState> = emptyList(),
    val otherProcessSections: List<RuntimeManagementProcessSectionUiState> = emptyList(),
    val refreshedAt: Long = 0L
) {
    val isEmpty: Boolean
        get() = runs.isEmpty() && otherProcessSections.all { it.processes.isEmpty() }
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

    data class ViewBackgroundRuntimeLog(val runtimeId: String) : RuntimeManagementFeatureEffect
    data class ActionRejected(val reason: String) : RuntimeManagementFeatureEffect
}
