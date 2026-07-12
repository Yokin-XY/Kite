package com.kite.app.feature.runtimebootstrap

import com.kite.app.application.runtimebootstrap.RuntimeBootstrapGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal sealed interface RuntimeStatusFeatureEffect {
    data object ContinueFirstRunPermissionOnboarding : RuntimeStatusFeatureEffect
    data object RequestRuntimePermissions : RuntimeStatusFeatureEffect
    data object OpenAllFilesSettings : RuntimeStatusFeatureEffect
    data object OpenProcessManagement : RuntimeStatusFeatureEffect
}

/** Runtime chrome state owner. It combines process facts with bootstrap facts and emits Shell effects. */
internal class RuntimeStatusFeatureController(
    private val bootstrapGateway: RuntimeBootstrapGateway,
    private val managementGateway: RuntimeManagementGateway,
    scope: CoroutineScope
) {
    private val onboarding = MutableStateFlow(RuntimePermissionOnboardingUiInput())

    val state: StateFlow<RuntimeStatusUiState> = combine(
        bootstrapGateway.snapshots,
        managementGateway.snapshots,
        onboarding
    ) { bootstrap, management, onboardingState ->
        RuntimeStatusProjector.project(
            snapshot = bootstrap,
            counts = management.toStatusCounts(),
            onboarding = onboardingState
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = RuntimeStatusUiState.checking(managementGateway.currentSnapshot().toStatusCounts())
    )

    fun refresh() {
        bootstrapGateway.refresh()
        managementGateway.refresh(force = true)
    }

    fun ensureReady() {
        bootstrapGateway.ensureReady()
    }

    fun updateOnboarding(input: RuntimePermissionOnboardingUiInput) {
        onboarding.value = input
    }

    fun clearOnboarding() {
        onboarding.value = RuntimePermissionOnboardingUiInput()
    }

    fun submitPrimaryAction(): RuntimeStatusFeatureEffect? {
        val current = state.value
        if (current.firstRunPermissionOnboarding) {
            return RuntimeStatusFeatureEffect.ContinueFirstRunPermissionOnboarding
        }
        return when (current.primaryAction) {
            RuntimeStatusAction.RequestRuntimePermissions -> RuntimeStatusFeatureEffect.RequestRuntimePermissions
            RuntimeStatusAction.OpenAllFilesSettings -> RuntimeStatusFeatureEffect.OpenAllFilesSettings
            RuntimeStatusAction.RetryDeployment -> {
                bootstrapGateway.ensureReady()
                null
            }
            RuntimeStatusAction.OpenProcessManagement -> RuntimeStatusFeatureEffect.OpenProcessManagement
        }
    }

    private fun RuntimeManagementSnapshot.toStatusCounts(): RuntimeStatusCounts = RuntimeStatusCounts(
        runningCards = runs.count { run ->
            run.parentInstanceId.isNullOrBlank() && run.status in setOf(
                CardRunStatus.Starting,
                CardRunStatus.Running,
                CardRunStatus.WaitingTerminal,
                CardRunStatus.AlreadyRunning,
                CardRunStatus.Opened,
                CardRunStatus.Stopping
            )
        },
        runningTerminals = terminals.count { it.isLive },
        runningProcesses = maxOf(processes.size, observedProcessCount).coerceAtLeast(0)
    )
}
