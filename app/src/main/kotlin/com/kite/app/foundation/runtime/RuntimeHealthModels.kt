package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.RuntimeRetentionClass
import com.kite.app.foundation.service.SupervisordServiceHealthSnapshot

/*
 * T11:从 RuntimeHealthStore.kt 抽出的纯 model 定义(2 enum + 4 data class)。
 * 这些 model 与 store 逻辑同包,无需相互 import。抽出后 RuntimeHealthStore.kt
 * 从 3168 行降至约 2860 行,store 逻辑与 model 定义分离。
 */

enum class RuntimeRootOwnerKind(val label: String) {
    TERMINAL("终端"),
    CARD("卡片"),
    RESOURCE("资源"),
    BACKGROUND_RUNTIME("后台运行项"),
    UNATTRIBUTED("未归属运行根")
}

enum class RuntimeRootReality(val label: String) {
    OBSERVED("已观测"),
    STALE_RECORD("记录过期"),
    UNKNOWN("未知")
}

data class RuntimeRootSnapshot(
    val ownerKind: RuntimeRootOwnerKind,
    val ownerId: String?,
    val title: String,
    val statusLabel: String,
    val expectedPid: Int? = null,
    val observedPid: Int? = null,
    val rootPid: Int? = observedPid ?: expectedPid,
    val parentPid: Int? = null,
    val prootPid: Int? = null,
    val rootProcessGroupId: Int? = null,
    val rootSessionId: Int? = null,
    val processCount: Int = 0,
    val rssKb: Long = 0L,
    val vmSizeKb: Long = 0L,
    val maxOomScoreAdj: Int? = null,
    val cpuTimeTicks: Long = 0L,
    val ioReadBytes: Long = 0L,
    val ioWriteBytes: Long = 0L,
    val retentionClass: RuntimeRetentionClass = RuntimeRetentionClass.EPHEMERAL,
    val resident: Boolean = retentionClass.resident,
    val reclaimPriority: Int = retentionClass.reclaimPriority,
    val autoReclaimAllowed: Boolean = false,
    val classificationSource: String = "unknown",
    val classificationReason: String? = null,
    val sourceLabel: String = "",
    val commandLine: String = "",
    val runtimeKind: BackgroundRuntimeKind? = null,
    val observedStatusLabel: String? = null,
    val isActiveOwner: Boolean = false,
    val reality: RuntimeRootReality = RuntimeRootReality.UNKNOWN,
    val lastSeenAt: Long? = null,
    val lastStartedAt: Long? = null,
    val lastExitedAt: Long? = null,
    val lastExitCode: Int? = null,
    val lastError: String? = null,
    val staleReason: String? = null,
    val restartPolicyLabel: String? = null,
    val restartFailureCount: Int = 0,
    val nextRestartAllowedAt: Long? = null,
    val lastRestartReason: String? = null,
    val lastRecoveredAt: Long? = null,
    val lastRecoverySource: String? = null,
    val lastRecoveryReason: String? = null,
    val lastAdmissionDeferredAt: Long? = null,
    val lastAdmissionSource: String? = null,
    val lastAdmissionReason: String? = null,
    val lastReclaimedAt: Long? = null,
    val lastReclaimSource: String? = null,
    val lastReclaimReason: String? = null,
    val processUnitId: String? = null,
    val processUnitDisplayName: String? = null,
    val processUnitTier: RuntimeProcessUnitTier? = null,
    val processUnitSource: String? = null,
    val processUnitExpectedMemoryLimitKb: Long? = null,
    val processUnitUnlimitedMemory: Boolean = false,
    val processUnitWarningThresholdRatio: Double = 0.9,
    val processUnitRestartThresholdRatio: Double = 1.0,
    val processUnitQuarantineAfterFailures: Int = 3,
    val processUnitManualKillPolicy: RuntimeProcessUnitManualKillPolicy? = null,
    val processUnitUserEditable: Boolean = true,
    val processUnitAllowReclaim: Boolean = false,
    val processUnitAllowKill: Boolean = false,
    val processUnitAllowRestart: Boolean = false,
    val processUnitRequiresMemoryAdmission: Boolean = false,
    val processUnitReason: String? = null,
    val processUnitObservedState: RuntimeProcessUnitObservationState? = null,
    val processUnitMatchSource: RuntimeProcessUnitMatchSource = RuntimeProcessUnitMatchSource.NONE,
    val processUnitMatchConfidence: RuntimeProcessUnitMatchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
    val processUnitMatchState: RuntimeProcessUnitMatchState = RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED,
    val processUnitMatchedPid: Int? = null,
    val processUnitMatchedPgid: Int? = null,
    val processUnitMatchedSid: Int? = null,
    val processUnitConflictUnitIds: List<String> = emptyList(),
    val processUnitFallbackReason: String? = null,
    val stopReconciliationState: RuntimeProcessUnitObservationState? = null,
    val stopReconciliationReason: String? = null,
    val stopReconciliationAt: Long? = null,
    val stopReconciliationAutoRecoverySuppressed: Boolean = false
) {
    val isRunning: Boolean
        get() = reality == RuntimeRootReality.OBSERVED && observedPid != null && observedPid > 0

    val ownershipKey: String
        get() = "${ownerKind.name}:${ownerId ?: rootPid ?: title}"
}

data class RuntimeHealthSnapshot(
    val spaceId: String? = null,
    val containerId: String? = null,
    val legacyContainerPid: Int? = null,
    val networkModeLabel: String? = null,
    val networkSemantics: RuntimeNetworkSemantics? = null,
    val containerLastError: String? = null,
    val supervisordServices: SupervisordServiceHealthSnapshot? = null,
    val processResourceSnapshot: ContainerProcessResourceSnapshot = ContainerProcessResourceSnapshot(),
    val roots: List<RuntimeRootSnapshot> = emptyList(),
    val processObservationProbe: RuntimeProcessObservationProbeSnapshot =
        RuntimeProcessObservationProbeSnapshot(),
    val processObservationValidationReport: RuntimeProcessObservationValidationReport =
        RuntimeProcessObservationValidationReport(),
    val lifecycleWarningNotice: RuntimeLifecycleUserNoticeSnapshot =
        RuntimeLifecycleUserNoticeSnapshot(),
    val pressure: RuntimePressureSnapshot = RuntimePressureSnapshot(),
    val prootTelemetry: ProotTelemetrySnapshot = ProotTelemetrySnapshot(),
    val prootTelemetryLaunchState: RuntimeProotTelemetryLaunchState =
        RuntimeProotTelemetryLaunchState(),
    val prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot = ProotTelemetryHealthDryRunSnapshot(),
    val prootTelemetryRepairPlan: ProotTelemetryRepairPlanDryRunSnapshot =
        ProotTelemetryRepairPlanDryRunSnapshot(),
    val pressureConsumer: RuntimePressureConsumerSnapshot = RuntimePressureConsumerSnapshot(),
    val pressureStability: RuntimePressureStabilityGateDryRunSnapshot =
        RuntimePressureStabilityGateDryRunSnapshot(),
    val prootPoolPlan: RuntimeProotPoolPlanDryRunSnapshot = RuntimeProotPoolPlanDryRunSnapshot(),
    val prootManagementMainline: RuntimeProotManagementMainlineDryRunSnapshot =
        RuntimeProotManagementMainlineDryRunSnapshot(),
    val prootDeviceCalibration: RuntimeProotDeviceCalibrationDryRunSnapshot =
        RuntimeProotDeviceCalibrationDryRunSnapshot(),
    val workloadRegistry: RuntimeWorkloadRegistrySnapshot = RuntimeWorkloadRegistrySnapshot(),
    val processUnitManifest: RuntimeProcessUnitManifestSnapshot = RuntimeProcessUnitManifestSnapshot(),
    val backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot = RuntimeBackgroundDecayDryRunSnapshot(),
    val budgetPressure: RuntimeBudgetPressureDryRunSnapshot = RuntimeBudgetPressureDryRunSnapshot(),
    val lifecyclePolicySurface: RuntimeLifecyclePolicySurfaceDryRunSnapshot =
        RuntimeLifecyclePolicySurfaceDryRunSnapshot(),
    val lifecyclePolicyProfileSurface: RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot =
        RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot(),
    val lifecycleIntentSurface: RuntimeLifecycleIntentSurfaceDryRunSnapshot =
        RuntimeLifecycleIntentSurfaceDryRunSnapshot(),
    val lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot =
        RuntimeLifecycleReclaimPlanDryRunSnapshot(),
    val lifecycleProotExpansionBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot =
        RuntimeLifecycleProotExpansionBudgetDryRunSnapshot(),
    val prootCapacityExecutor: RuntimeProotCapacityExecutorSnapshot =
        RuntimeProotCapacityExecutorSnapshot(),
    val systemProcessLifecycle: RuntimeSystemProcessLifecycleDryRunSnapshot =
        RuntimeSystemProcessLifecycleDryRunSnapshot(),
    val laneAdmission: RuntimeLaneAdmissionDryRunSnapshot = RuntimeLaneAdmissionDryRunSnapshot(),
    val startPreflight: RuntimeStartPreflightDryRunSnapshot = RuntimeStartPreflightDryRunSnapshot(),
    val startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot = RuntimeStartQueuePlanDryRunSnapshot(),
    val governanceActionPlan: RuntimeGovernanceActionPlanDryRunSnapshot =
        RuntimeGovernanceActionPlanDryRunSnapshot(),
    val governanceReadiness: RuntimeGovernanceReadinessGateDryRunSnapshot =
        RuntimeGovernanceReadinessGateDryRunSnapshot(),
    val canaryEntryPlan: RuntimeCanaryEntryPlanDryRunSnapshot =
        RuntimeCanaryEntryPlanDryRunSnapshot(),
    val canaryScopePlan: RuntimeCanaryScopePlanDryRunSnapshot =
        RuntimeCanaryScopePlanDryRunSnapshot(),
    val canaryActivationPlan: RuntimeCanaryActivationPlanDryRunSnapshot =
        RuntimeCanaryActivationPlanDryRunSnapshot(),
    val canarySessionPlan: RuntimeCanarySessionPlanDryRunSnapshot =
        RuntimeCanarySessionPlanDryRunSnapshot(),
    val canaryApprovalRequest: RuntimeCanaryApprovalRequestDryRunSnapshot =
        RuntimeCanaryApprovalRequestDryRunSnapshot(),
    val canaryApprovalGate: RuntimeCanaryApprovalGateDryRunSnapshot =
        RuntimeCanaryApprovalGateDryRunSnapshot(),
    val canaryGrantPlan: RuntimeCanaryGrantPlanDryRunSnapshot =
        RuntimeCanaryGrantPlanDryRunSnapshot(),
    val canarySessionStartPlan: RuntimeCanarySessionStartPlanDryRunSnapshot =
        RuntimeCanarySessionStartPlanDryRunSnapshot(),
    val canarySessionLeasePlan: RuntimeCanarySessionLeasePlanDryRunSnapshot =
        RuntimeCanarySessionLeasePlanDryRunSnapshot(),
    val canaryEnforcementPlan: RuntimeCanaryEnforcementPlanDryRunSnapshot =
        RuntimeCanaryEnforcementPlanDryRunSnapshot(),
    val canaryRollbackPlan: RuntimeCanaryRollbackPlanDryRunSnapshot =
        RuntimeCanaryRollbackPlanDryRunSnapshot(),
    val canaryAuditPlan: RuntimeCanaryAuditPlanDryRunSnapshot =
        RuntimeCanaryAuditPlanDryRunSnapshot(),
    val managementTopology: RuntimeManagementTopologyDryRunSnapshot =
        RuntimeManagementTopologyDryRunSnapshot(),
    val reclaimerPolicyProfile: String = RuntimeReclaimerProfile.BALANCED.name,
    val reclaimerPolicyPath: String? = null,
    val residentPolicyProfile: String = RuntimeResidentProfile.BALANCED.name,
    val residentPolicyPath: String? = null,
    val runtimePolicyHotReloadEnabled: Boolean = false,
    val runtimePolicyHotReloadIntervalMs: Long = 0L,
    val runtimePolicyHotReloadGeneration: Long = 0L,
    val runtimePolicyHotReloadLastReloadAt: Long = 0L,
    val runtimePolicyHotReloadLastChanged: String = "none",
    val runtimePolicyHotReloadWorkspacePath: String? = null,
    val processSnapshotSource: String = "unknown",
    val processSnapshotRefreshedAt: Long = 0L,
    val processSnapshotHostProcessCount: Int = 0,
    val processSnapshotContainerProcessCount: Int = 0,
    val processSnapshotMergedProcessCount: Int = 0,
    val processSnapshotSample: String = "",
    val controlledProbeVisibility: RuntimeControlledProbeProcessVisibility =
        RuntimeControlledProbeProcessVisibility(),
    val overviewRefreshedAt: Long = 0L,
    val reconciledAt: Long = 0L,
    val reconciliationReason: String = "unknown"
) {
    val runningRootCount: Int
        get() = roots.count { it.isRunning }

    val staleRootCount: Int
        get() = roots.count { it.reality == RuntimeRootReality.STALE_RECORD }

    val terminalRoots: List<RuntimeRootSnapshot>
        get() = roots.filter { it.ownerKind == RuntimeRootOwnerKind.TERMINAL }

    val backgroundRuntimeRoots: List<RuntimeRootSnapshot>
        get() = roots.filter { it.ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME }

    val staleRoots: List<RuntimeRootSnapshot>
        get() = roots.filter { it.reality == RuntimeRootReality.STALE_RECORD }

    val primaryMetricsPid: Int?
        get() = roots
            .firstOrNull {
                it.isRunning &&
                    it.ownerKind == RuntimeRootOwnerKind.TERMINAL &&
                    it.isActiveOwner
            }
            ?.observedPid
            ?: roots.firstOrNull {
                it.isRunning && it.ownerKind == RuntimeRootOwnerKind.TERMINAL
            }?.observedPid
            ?: roots.firstOrNull {
                it.isRunning && it.ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME
            }?.observedPid
            ?: roots.firstOrNull { it.isRunning }?.observedPid

    fun terminalRoot(sessionId: String): RuntimeRootSnapshot? {
        return roots.firstOrNull {
            it.ownerKind == RuntimeRootOwnerKind.TERMINAL && it.ownerId == sessionId
        }
    }

    fun backgroundRuntimeRoot(runtimeId: String): RuntimeRootSnapshot? {
        return roots.firstOrNull {
            it.ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME && it.ownerId == runtimeId
        }
    }

    fun rootForProcess(process: ContainerProcessRecord): RuntimeRootSnapshot? {
        roots.firstOrNull {
            it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED && it.observedPid == process.pid
        }?.let { return it }
        if (!process.linkedTerminalSessionId.isNullOrBlank()) {
            terminalRoot(process.linkedTerminalSessionId)?.let { return it }
        }
        if (!process.linkedRuntimeId.isNullOrBlank()) {
            backgroundRuntimeRoot(process.linkedRuntimeId)
                ?.takeIf { it.observedPid == process.pid || it.expectedPid == process.pid }
                ?.let { return it }
        }
        return roots.firstOrNull { root ->
            root.observedPid == process.pid ||
                (
                    root.ownerKind != RuntimeRootOwnerKind.BACKGROUND_RUNTIME &&
                        root.observedPid == process.parentPid
                    ) ||
                root.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED && root.observedPid == process.pid
        }
    }
}

data class RuntimeControlledProbeProcessVisibility(
    val processSeen: Boolean = false,
    val pid: Int? = null,
    val parentPid: Int? = null,
    val processGroupId: Int? = null,
    val sessionId: Int? = null,
    val state: String = "none",
    val sourceLabel: String = "none",
    val linkedTerminalSessionIdPresent: Boolean = false,
    val linkedRuntimeIdPresent: Boolean = false,
    val strongCandidateCount: Int = 0,
    val firstStrongCandidatePid: Int? = null,
    val firstStrongCandidateUnitId: String? = null,
    val firstStrongCandidateMatchSource: RuntimeProcessUnitMatchSource = RuntimeProcessUnitMatchSource.NONE,
    val firstStrongCandidateRejectedReason: String = "none"
)

data class RuntimeProotTelemetryLaunchState(
    val requestedMode: String = ProotLaunchPlan.TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0,
    val runtimeDescriptorMode: String = "unknown",
    val runtimeAssetId: String = "unknown",
    val runtimeActiveRuntimeId: String = "unknown",
    val runtimeProvider: String = "unknown",
    val substrateState: String = "unknown",
    val substrateReason: String = "unknown",
    val telemetryCapableCandidateRuntimeId: String = "none",
    val telemetryCapableCandidateValidationState: String = "none",
    val runtimeDescriptorPath: String = "",
    val supportsRequestedTelemetry: Boolean = false,
    val status: String = "context_unavailable"
)
