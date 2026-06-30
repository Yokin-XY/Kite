package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.NetworkMode

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.BackgroundRuntimeRecord
import com.kite.app.foundation.service.RuntimeRetentionClass
import com.kite.app.foundation.service.SupervisordServiceHealthSnapshot
import com.kite.app.foundation.service.SupervisordServiceHealthStore
import com.kite.app.foundation.service.isActiveRuntime
import com.kite.app.foundation.terminal.TerminalRuntimeEntry
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import com.kite.app.foundation.contracts.isLiveProcessStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

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

object RuntimeHealthStore {

    private const val LOG_TAG = "RuntimeHealthStore"
    private const val POLICY_AUTO_REFRESH_INTERVAL_MS = 1_200L
    private const val HEALTH_LOG_MIN_INTERVAL_MS = 15_000L
    private const val PROOT_LIVE_TABLE_ENV_ENTRY_LIMIT = 5
    private const val PROCFS_PROJECTION_MAX_ENTRIES = 512
    private const val PROCFS_PROJECTION_RECENT_TERMINAL_TTL_MS = 120_000L
    private const val PROCFS_PROJECTION_RETENTION_MODE = "bounded_live_v1"
    private const val PROCFS_PROJECTION_CLEANUP_MODE = "delete_stale_pid_dirs_v1"
    private const val PROOT_TELEMETRY_EVENT_SURFACE_LIMIT = 128
    private const val RUNTIME_PRESSURE_SURFACE_MIN_WRITE_INTERVAL_MS = 10_000L
    private const val PROOT_POOL_TUNING_LOG_MIN_INTERVAL_MS = 60_000L
    private const val PROOT_POOL_TUNING_LOG_MAX_BYTES = 2_097_152L
    private const val PROOT_POOL_TUNING_LOG_ARCHIVE_MAX_BYTES = 4_194_304L
    private const val PROOT_POOL_TUNING_LOG_ARCHIVE_NAME = "proot-pool-tuning.old.jsonl"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _containerMetadata = MutableStateFlow(ContainerRuntimeMetadata())
    private val _reclaimerPolicy = MutableStateFlow(RuntimeReclaimerPolicy.default())
    private val _residentPolicy = MutableStateFlow(RuntimeResidentPolicy.default())
    private val _workloadPolicy = MutableStateFlow(RuntimeWorkloadPolicy.default())
    private val _processUnitManifest = MutableStateFlow(RuntimeProcessUnitManifest.default())
    private val _reconciliationReason = MutableStateFlow("initial")
    private val _snapshot = MutableStateFlow(RuntimeHealthSnapshot())
    @Volatile
    private var lastRuntimePressureSurface: String = ""
    @Volatile
    private var lastRuntimeProcessTableSurface: String = ""
    @Volatile
    private var lastRuntimeProcProjectionSurface: String = ""
    @Volatile
    private var lastProotTelemetryEventSurface: String = ""
    @Volatile
    private var lastProotPoolTuningLogEntry: String = ""
    @Volatile
    private var lastRuntimePressureSurfacePublishedAt: Long = 0L
    @Volatile
    private var lastProotPoolTuningLogSignature: String = ""
    @Volatile
    private var lastProotPoolTuningLogAtMs: Long = 0L
    @Volatile
    private var applicationContext: Context? = null
    @Volatile
    private var policyAutoRefreshJob: Job? = null
    @Volatile
    private var policyAutoRefreshWorkspacePath: String? = null
    @Volatile
    private var lastReclaimerPolicySignature: PolicyFileSignature? = null
    @Volatile
    private var lastResidentPolicySignature: PolicyFileSignature? = null
    @Volatile
    private var lastWorkloadPolicySignature: PolicyFileSignature? = null
    @Volatile
    private var lastProcessUnitManifestSignature: PolicyFileSignature? = null
    @Volatile
    private var policyHotReloadGeneration: Long = 0L
    @Volatile
    private var policyHotReloadLastReloadAt: Long = 0L
    @Volatile
    private var policyHotReloadLastChanged: String = "none"
    @Volatile
    private var lastHealthLogAtMs: Long = 0L
    val snapshot: StateFlow<RuntimeHealthSnapshot> = _snapshot

    private data class PolicyFileSignature(
        val path: String?,
        val exists: Boolean,
        val lastModified: Long,
        val length: Long
    )

    private data class RuntimeHealthInputs(
        val overview: RuntimeOverviewSnapshot,
        val processSnapshot: ContainerProcessSnapshot,
        val supervisordServices: SupervisordServiceHealthSnapshot,
        val prootTelemetry: ProotTelemetrySnapshot,
        val containerMetadata: ContainerRuntimeMetadata,
        val reclaimerPolicy: RuntimeReclaimerPolicy,
        val residentPolicy: RuntimeResidentPolicy,
        val workloadPolicy: RuntimeWorkloadPolicy,
        val processUnitManifest: RuntimeProcessUnitManifest,
        val lifecycleSignal: RuntimeLifecycleSignalSnapshot
    )

    private data class RuntimePolicyInputs(
        val containerMetadata: ContainerRuntimeMetadata,
        val reclaimerPolicy: RuntimeReclaimerPolicy,
        val residentPolicy: RuntimeResidentPolicy,
        val workloadPolicy: RuntimeWorkloadPolicy,
        val processUnitManifest: RuntimeProcessUnitManifest,
        val lifecycleSignal: RuntimeLifecycleSignalSnapshot
    )

    private data class RuntimePolicyBaseInputs(
        val containerMetadata: ContainerRuntimeMetadata,
        val reclaimerPolicy: RuntimeReclaimerPolicy,
        val residentPolicy: RuntimeResidentPolicy,
        val workloadPolicy: RuntimeWorkloadPolicy,
        val processUnitManifest: RuntimeProcessUnitManifest
    )

    init {
        scope.launch {
            val metadataAndPolicyBase = combine(
                _containerMetadata,
                _reclaimerPolicy,
                _residentPolicy,
                _workloadPolicy,
                _processUnitManifest
            ) { containerMetadata, reclaimerPolicy, residentPolicy, workloadPolicy, processUnitManifest ->
                RuntimePolicyBaseInputs(
                    containerMetadata = containerMetadata,
                    reclaimerPolicy = reclaimerPolicy,
                    residentPolicy = residentPolicy,
                    workloadPolicy = workloadPolicy,
                    processUnitManifest = processUnitManifest
                )
            }
            val metadataAndPolicy = combine(
                metadataAndPolicyBase,
                RuntimeLifecycleSignalStore.snapshot
            ) { base, lifecycleSignal ->
                RuntimePolicyInputs(
                    containerMetadata = base.containerMetadata,
                    reclaimerPolicy = base.reclaimerPolicy,
                    residentPolicy = base.residentPolicy,
                    workloadPolicy = base.workloadPolicy,
                    processUnitManifest = base.processUnitManifest,
                    lifecycleSignal = lifecycleSignal
                )
            }
            val runtimeInputs = combine(
                RuntimeOverviewStore.snapshot,
                ContainerProcessStore.snapshot,
                SupervisordServiceHealthStore.snapshot,
                ProotTelemetryStore.snapshot,
                metadataAndPolicy
            ) { overview, processSnapshot, supervisordServices, prootTelemetry, policies ->
                RuntimeHealthInputs(
                    overview = overview,
                    processSnapshot = processSnapshot,
                    supervisordServices = supervisordServices,
                    prootTelemetry = prootTelemetry,
                    containerMetadata = policies.containerMetadata,
                    reclaimerPolicy = policies.reclaimerPolicy,
                    residentPolicy = policies.residentPolicy,
                    workloadPolicy = policies.workloadPolicy,
                    processUnitManifest = policies.processUnitManifest,
                    lifecycleSignal = policies.lifecycleSignal
                )
            }
            combine(
                runtimeInputs,
                _reconciliationReason
            ) { inputs, reason ->
                buildSnapshot(
                    overview = inputs.overview,
                    processSnapshot = inputs.processSnapshot,
                    supervisordServices = inputs.supervisordServices,
                    prootTelemetry = inputs.prootTelemetry,
                    containerMetadata = inputs.containerMetadata,
                    reclaimerPolicy = inputs.reclaimerPolicy,
                    residentPolicy = inputs.residentPolicy,
                    workloadPolicy = inputs.workloadPolicy,
                    processUnitManifest = inputs.processUnitManifest,
                    lifecycleSignal = inputs.lifecycleSignal,
                    reconciliationReason = reason
                )
            }.collect { latest ->
                applicationContext?.let { appContext ->
                    RuntimeMemoryLifecycleRuleTrigger.onSnapshot(appContext, latest)
                }
                _snapshot.value = latest
                publishRuntimePressureSurface(latest)
            }
        }
    }

    fun attachContext(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        ProotTelemetryStore.startAutoRefresh(appContext)
        _containerMetadata.value = appContext.resolveContainerRuntimeMetadata()
        loadRuntimePolicies(appContext)
        startPolicyAutoRefresh(appContext)
    }

    fun refresh(context: Context, reason: String = "runtime-health-refresh") {
        val appContext = context.applicationContext
        attachContext(appContext)
        ProotTelemetryStore.refresh(appContext)
        RuntimeOverviewStore.publishCurrentSnapshot(appContext)
        markReconciliation(reason)
    }

    fun markReconciliation(reason: String) {
        _reconciliationReason.value = reason
    }

    fun publishCurrentSnapshot(context: Context, reason: String = "manual-publish") {
        attachContext(context.applicationContext)
        markReconciliation(reason)
    }

    private fun loadRuntimePolicies(appContext: Context) {
        _reclaimerPolicy.value = RuntimeReclaimerPolicyStore.load(appContext)
        _residentPolicy.value = RuntimeResidentPolicyStore.load(appContext)
        _workloadPolicy.value = RuntimeWorkloadPolicyStore.load(appContext)
        _processUnitManifest.value = RuntimeProcessUnitManifestStore.load(appContext)
        recordPolicySignatures(appContext)
    }

    private fun startPolicyAutoRefresh(appContext: Context) {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
            ?.workspacePath
            ?.takeIf { it.isNotBlank() }
        if (policyAutoRefreshJob?.isActive == true && policyAutoRefreshWorkspacePath == workspacePath) {
            return
        }
        policyAutoRefreshJob?.cancel()
        policyAutoRefreshJob = null
        policyAutoRefreshWorkspacePath = workspacePath
        if (workspacePath == null) {
            lastReclaimerPolicySignature = null
            lastResidentPolicySignature = null
            lastWorkloadPolicySignature = null
            lastProcessUnitManifestSignature = null
            policyHotReloadLastChanged = "workspace_missing"
            return
        }
        val workspaceDir = File(workspacePath)
        recordPolicySignatures(workspaceDir)
        policyAutoRefreshJob = scope.launch {
            while (isActive) {
                delay(POLICY_AUTO_REFRESH_INTERVAL_MS)
                reloadRuntimePoliciesIfChanged(appContext, workspaceDir)
            }
        }
    }

    private fun reloadRuntimePoliciesIfChanged(appContext: Context, workspaceDir: File) {
        val reclaimerFile = WorkspaceBuildSupport.runtimeReclaimerPolicyFile(workspaceDir)
        val residentFile = WorkspaceBuildSupport.runtimeResidentPolicyFile(workspaceDir)
        val workloadFile = WorkspaceBuildSupport.runtimeWorkloadPolicyFile(workspaceDir)
        val processUnitFile = WorkspaceBuildSupport.runtimeProcessManifestFile(workspaceDir)
        val reclaimerSignature = reclaimerFile.toPolicySignature()
        val residentSignature = residentFile.toPolicySignature()
        val workloadSignature = workloadFile.toPolicySignature()
        val processUnitSignature = processUnitFile.toPolicySignature()
        val reclaimerChanged = reclaimerSignature != lastReclaimerPolicySignature
        val residentChanged = residentSignature != lastResidentPolicySignature
        val workloadChanged = workloadSignature != lastWorkloadPolicySignature
        val processUnitChanged = processUnitSignature != lastProcessUnitManifestSignature
        if (!reclaimerChanged && !residentChanged && !workloadChanged && !processUnitChanged) {
            return
        }
        if (reclaimerChanged) {
            _reclaimerPolicy.value = RuntimeReclaimerPolicyStore.load(appContext)
        }
        if (residentChanged) {
            _residentPolicy.value = RuntimeResidentPolicyStore.load(appContext)
        }
        if (workloadChanged) {
            _workloadPolicy.value = RuntimeWorkloadPolicyStore.load(appContext)
        }
        if (processUnitChanged) {
            _processUnitManifest.value = RuntimeProcessUnitManifestStore.load(appContext)
        }
        recordPolicySignatures(workspaceDir)
        val changed = buildList {
            if (reclaimerChanged) add("reclaimer")
            if (residentChanged) add("resident")
            if (workloadChanged) add("workload")
            if (processUnitChanged) add("process_unit")
        }.joinToString("+")
        policyHotReloadGeneration += 1L
        policyHotReloadLastReloadAt = System.currentTimeMillis()
        policyHotReloadLastChanged = changed.ifBlank { "none" }
        Logger.i(LOG_TAG, "runtime policy hot-reloaded: $changed")
        markReconciliation("runtime-policy-hot-reload:$changed")
    }

    private fun recordPolicySignatures(appContext: Context) {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
            ?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?: return
        recordPolicySignatures(File(workspacePath))
    }

    private fun recordPolicySignatures(workspaceDir: File) {
        lastReclaimerPolicySignature = WorkspaceBuildSupport.runtimeReclaimerPolicyFile(workspaceDir)
            .toPolicySignature()
        lastResidentPolicySignature = WorkspaceBuildSupport.runtimeResidentPolicyFile(workspaceDir)
            .toPolicySignature()
        lastWorkloadPolicySignature = WorkspaceBuildSupport.runtimeWorkloadPolicyFile(workspaceDir)
            .toPolicySignature()
        lastProcessUnitManifestSignature = WorkspaceBuildSupport.runtimeProcessManifestFile(workspaceDir)
            .toPolicySignature()
    }

    private fun File.toPolicySignature(): PolicyFileSignature {
        val canonicalPath = runCatching { canonicalPath }.getOrElse { absolutePath }
        return PolicyFileSignature(
            path = canonicalPath,
            exists = exists(),
            lastModified = if (exists()) lastModified() else 0L,
            length = if (exists()) length() else 0L
        )
    }

    fun logCurrentHealth(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastHealthLogAtMs < HEALTH_LOG_MIN_INTERVAL_MS) {
            return
        }
        lastHealthLogAtMs = now
        Logger.i(LOG_TAG, _snapshot.value.toMainlineLogLine(reason))
    }

    internal fun formatMainlineEnvForTests(snapshot: RuntimeHealthSnapshot): String {
        return snapshot.toEnvText()
    }

    internal fun formatUbuntuProcessTableForTests(snapshot: RuntimeHealthSnapshot): String {
        return snapshot.toUbuntuProcessTableText()
    }

    internal fun formatUbuntuProcProjectionForTests(snapshot: RuntimeHealthSnapshot): Map<String, String> {
        return snapshot.toUbuntuProcProjectionFiles()
    }

    internal fun formatProotTelemetryEventsForTests(snapshot: RuntimeHealthSnapshot): String {
        return snapshot.toProotTelemetryEventsText()
    }

    internal fun publishUbuntuProcProjectionForTests(procRoot: File, files: Map<String, String>) {
        publishUbuntuProcProjection(procRoot, files)
    }

    internal fun applyRuntimeProcessResourceSamplesForTests(
        snapshot: RuntimeHealthSnapshot,
        pid: Int,
        rssKb: Long,
        vmSizeKb: Long,
        cpuTimeTicks: Long
    ): RuntimeHealthSnapshot {
        val samples = mapOf(
            pid to RuntimeProcessResourceSample(
                rssKb = rssKb,
                vmSizeKb = vmSizeKb,
                cpuTimeTicks = cpuTimeTicks
            )
        )
        return snapshot.copy(
            processResourceSnapshot = snapshot.processResourceSnapshot
                .withRuntimeProcessResourceSamples(samples),
            roots = snapshot.roots.withRuntimeProcessResourceSamples(samples)
        )
    }

    @Synchronized
    private fun publishRuntimePressureSurface(snapshot: RuntimeHealthSnapshot) {
        val workspacePath = _containerMetadata.value.workspacePath?.takeIf { it.isNotBlank() } ?: return
        val now = System.currentTimeMillis()
        if (now - lastRuntimePressureSurfacePublishedAt < RUNTIME_PRESSURE_SURFACE_MIN_WRITE_INTERVAL_MS) {
            return
        }
        val workspaceDir = File(workspacePath)
        val tableFile = WorkspaceBuildSupport.runtimeProcessTableFile(workspaceDir)
        val previousResourceSamples = readRuntimeProcessTableResourceSamples(tableFile)
        val content = snapshot.toEnvText()
        val processTable = snapshot.toUbuntuProcessTableText(previousResourceSamples)
        val telemetryEvents = snapshot.toProotTelemetryEventsText()
        val procProjection = snapshot.toUbuntuProcProjectionFiles(previousResourceSamples)
        val procProjectionSurface = procProjection.toSortedMap().entries.joinToString("\n") { (path, text) ->
            "$path=${text.hashCode()}:${text.length}"
        }
        if (
            content == lastRuntimePressureSurface &&
            processTable == lastRuntimeProcessTableSurface &&
            telemetryEvents == lastProotTelemetryEventSurface &&
            procProjectionSurface == lastRuntimeProcProjectionSurface
        ) return
        runCatching {
            val file = WorkspaceBuildSupport.runtimePressureFile(workspaceDir)
            val telemetryEventsFile = WorkspaceBuildSupport.prootTelemetryEventsFile(workspaceDir)
            val procDir = WorkspaceBuildSupport.runtimeProcProjectionDir(workspaceDir)
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            if (!file.exists() || file.readText() != content) {
                file.writeText(content)
            }
            tableFile.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            if (!tableFile.exists() || tableFile.readText() != processTable) {
                tableFile.writeText(processTable)
            }
            telemetryEventsFile.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            if (!telemetryEventsFile.exists() || telemetryEventsFile.readText() != telemetryEvents) {
                telemetryEventsFile.writeText(telemetryEvents)
            }
            publishUbuntuProcProjection(procDir, procProjection)
            publishProotPoolTuningLog(workspaceDir, snapshot)
            lastRuntimePressureSurface = content
            lastRuntimeProcessTableSurface = processTable
            lastProotTelemetryEventSurface = telemetryEvents
            lastRuntimeProcProjectionSurface = procProjectionSurface
            lastRuntimePressureSurfacePublishedAt = now
        }.onFailure { error ->
            Logger.e(LOG_TAG, "failed to publish runtime pressure surface: ${error.message}")
        }
    }

    private fun publishUbuntuProcProjection(procRoot: File, files: Map<String, String>) {
        if (!procRoot.exists()) {
            procRoot.mkdirs()
        }
        val expectedPidDirs = files.keys
            .mapNotNull { relativePath ->
                relativePath.substringBefore('/').takeIf(::isProcProjectionPidName)
            }
            .toSet()
        files.forEach { (relativePath, text) ->
            val target = File(procRoot, relativePath)
            writeProcProjectionTextIfChanged(target, text)
        }
        cleanupStaleProcProjectionPidDirs(procRoot, expectedPidDirs)
    }

    private fun cleanupStaleProcProjectionPidDirs(procRoot: File, expectedPidDirs: Set<String>): Int {
        val children = procRoot.listFiles() ?: return 0
        var deletedCount = 0
        children.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (!isProcProjectionPidName(child.name)) return@forEach
            if (child.name in expectedPidDirs) return@forEach
            if (deleteProcProjectionPidDir(child)) {
                deletedCount++
            }
        }
        return deletedCount
    }

    private fun deleteProcProjectionPidDir(pidDir: File): Boolean {
        val children = pidDir.listFiles() ?: return pidDir.delete()
        var success = true
        children.forEach { child ->
            val childDeleted = if (child.isDirectory) {
                deleteProcProjectionPidDir(child)
            } else {
                child.delete()
            }
            success = success && childDeleted
        }
        return pidDir.delete() && success
    }

    private fun isProcProjectionPidName(name: String): Boolean {
        return name.isNotBlank() && name.all { it in '0'..'9' }
    }

    private fun writeProcProjectionTextIfChanged(target: File, text: String) {
        val parent = target.parentFile ?: return
        if (!parent.exists()) {
            parent.mkdirs()
        }
        if (target.exists() && runCatching { target.readText() }.getOrNull() == text) {
            return
        }
        val temp = File(parent, ".${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    private fun publishProotPoolTuningLog(workspaceDir: File, snapshot: RuntimeHealthSnapshot) {
        val now = System.currentTimeMillis()
        val signature = buildProotPoolTuningLogSignature(snapshot)
        if (
            signature == lastProotPoolTuningLogSignature &&
            now - lastProotPoolTuningLogAtMs < PROOT_POOL_TUNING_LOG_MIN_INTERVAL_MS
        ) {
            return
        }
        val entry = buildProotPoolTuningLogEntry(snapshot)
        if (
            entry == lastProotPoolTuningLogEntry &&
            now - lastProotPoolTuningLogAtMs < PROOT_POOL_TUNING_LOG_MIN_INTERVAL_MS
        ) {
            return
        }
        runCatching {
            val file = WorkspaceBuildSupport.prootPoolTuningLogFile(workspaceDir)
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            rotateProotPoolTuningLogIfNeeded(file)
            file.appendText(entry + "\n")
            lastProotPoolTuningLogEntry = entry
            lastProotPoolTuningLogSignature = signature
            lastProotPoolTuningLogAtMs = now
        }.onFailure { error ->
            Logger.e(LOG_TAG, "failed to append proot pool tuning log: ${error.message}")
        }
    }

    private fun rotateProotPoolTuningLogIfNeeded(file: File) {
        if (!file.exists() || file.length() <= PROOT_POOL_TUNING_LOG_MAX_BYTES) return
        val length = file.length()
        val parent = file.parentFile
        val archive = parent?.let { File(it, PROOT_POOL_TUNING_LOG_ARCHIVE_NAME) }
        runCatching {
            archive?.takeIf { it.exists() }?.delete()
            val archived = archive != null &&
                length <= PROOT_POOL_TUNING_LOG_ARCHIVE_MAX_BYTES &&
                file.renameTo(archive)
            if (!archived) {
                file.delete()
            }
        }.onFailure { error ->
            Logger.e(LOG_TAG, "failed to rotate proot pool tuning log: ${error.message}")
        }
    }

    private fun buildProotPoolTuningLogSignature(snapshot: RuntimeHealthSnapshot): String {
        val plan = snapshot.prootPoolPlan
        return listOf(
            "state=${plan.state.name}",
            "recommendation=${plan.recommendation.name}",
            "substrate=${plan.substrateHealthy}/${plan.policySubstrateUsable}/${plan.probeSubstrateClean}",
            "tuning=${plan.tuningStatus}/${plan.tuningCandidatePoolSlots}",
            "pressure=${plan.pressureState.name}/${plan.pressureStabilityState.name}/${plan.budgetOverallState.name}",
            "signal=${plan.prootSignalLevel.name}/${bucketPercent(plan.prootPressureScore)}",
            "events=${bucketCount(plan.eventsInWindow)}/${bucketCount(plan.forkExecEventsInWindow)}",
            "live=${bucketCount(plan.liveTraceeCount)}",
            "workloads=${bucketCount(plan.activeWorkloadCount)}",
            "slots=${plan.plannedPoolSlots}/${plan.effectivePoolSlots}/${plan.sparePoolSlots}",
            "capacity=${plan.capacityRequestedAction}/${plan.capacityReviewNeeded}",
            "equation=${plan.resourceEquationDecision}/${plan.resourceEquationBottleneckAxis}",
            "risk=${bucketPercent(plan.resourceEquationRiskPercent)}",
            "target=${plan.resourceEquationTargetParallelSlots}/${bucketCount(plan.resourceEquationTargetQueueDepth)}",
            "adaptive=${plan.adaptiveEffectiveLiveTraceeSoftCap}/${plan.adaptiveConcurrencyPosture}/${plan.adaptiveQueuePosture}"
        ).joinToString("|")
    }

    private fun bucketPercent(value: Int): Int = (value.coerceAtLeast(0) / 10) * 10

    private fun bucketCount(value: Int): Int {
        return when {
            value <= 0 -> 0
            value <= 5 -> value
            value <= 20 -> (value / 5) * 5
            else -> (value / 10) * 10
        }
    }

    private fun buildProotPoolTuningLogEntry(snapshot: RuntimeHealthSnapshot): String {
        val plan = snapshot.prootPoolPlan
        return JSONObject()
            .put("schema", "proot_pool_tuning_v0")
            .put("generatedAtMs", plan.generatedAtMs)
            .put("state", plan.state.name)
            .put("recommendation", plan.recommendation.name)
            .put("substrateHealthy", plan.substrateHealthy)
            .put("policySubstrateUsable", plan.policySubstrateUsable)
            .put("policySubstrateReason", plan.policySubstrateReason)
            .put("probeSubstrateClean", plan.probeSubstrateClean)
            .put("probeSubstrateReason", plan.probeSubstrateReason)
            .put("tuningMode", plan.tuningMode)
            .put("tuningAxis", plan.tuningAxis)
            .put("tuningStatus", plan.tuningStatus)
            .put("probeProtocol", plan.probeProtocol)
            .put("probePhase", plan.probePhase)
            .put("probeSequence", plan.probeSequence)
            .put("probeDeclaredTargetLiveTracees", plan.probeDeclaredTargetLiveTracees)
            .put("probeBaselineContract", plan.probeBaselineContract)
            .put("probeBaselineSatisfied", plan.probeBaselineSatisfied)
            .put("probePreflightLogRequired", plan.probePreflightLogRequired)
            .put("probeSampleValid", plan.probeSampleValid)
            .put("probeSampleValidity", plan.probeSampleValidity)
            .put("probeObservedLiveTracees", plan.probeObservedLiveTracees)
            .put("probeObservedTransientEvents", plan.probeObservedTransientEvents)
            .put("probeCrashRecoveryKey", plan.probeCrashRecoveryKey)
            .put("knownRiskSampleLiveTracees", plan.knownRiskSampleLiveTracees)
            .put("knownRiskSamplePressureScore", plan.knownRiskSamplePressureScore)
            .put("knownRiskSampleAttribution", plan.knownRiskSampleAttribution)
            .put("knownRiskSampleDeviceScope", plan.knownRiskSampleDeviceScope)
            .put("knownSafeLowerBoundLiveTracees", plan.knownSafeLowerBoundLiveTracees)
            .put("recommendedDefaultLiveTraceePolicy", plan.recommendedDefaultLiveTraceePolicy)
            .put("adaptivePolicyMode", plan.adaptivePolicyMode)
            .put("adaptiveProfileGroup", plan.adaptiveProfileGroup.name)
            .put("adaptiveProfileSource", plan.adaptiveProfileSource)
            .put("adaptivePolicyStatus", plan.adaptivePolicyStatus)
            .put("adaptiveUserContext", plan.adaptiveUserContext)
            .put("adaptiveResourceLimiter", plan.adaptiveResourceLimiter)
            .put("adaptiveMemorySignal", plan.adaptiveMemorySignal.name)
            .put("adaptiveCpuSignal", plan.adaptiveCpuSignal)
            .put("adaptiveIoSignal", plan.adaptiveIoSignal)
            .put("adaptiveCpuBusyTicksPerSecond", plan.adaptiveCpuBusyTicksPerSecond)
            .put("adaptiveIoBusyBytesPerSecond", plan.adaptiveIoBusyBytesPerSecond)
            .put("adaptiveDefaultLiveTraceeSoftCap", plan.adaptiveDefaultLiveTraceeSoftCap)
            .put("adaptiveForegroundLiveTraceeSoftCap", plan.adaptiveForegroundLiveTraceeSoftCap)
            .put("adaptiveBackgroundLiveTraceeSoftCap", plan.adaptiveBackgroundLiveTraceeSoftCap)
            .put("adaptiveEffectiveLiveTraceeSoftCap", plan.adaptiveEffectiveLiveTraceeSoftCap)
            .put("adaptiveHardStopLiveTracees", plan.adaptiveHardStopLiveTracees)
            .put("adaptiveQueuePolicy", plan.adaptiveQueuePolicy)
            .put("adaptiveLowPriorityBackgroundPolicy", plan.adaptiveLowPriorityBackgroundPolicy)
            .put("adaptiveConcurrencyPosture", plan.adaptiveConcurrencyPosture)
            .put("adaptiveQueuePosture", plan.adaptiveQueuePosture)
            .put("adaptiveLowPriorityBackgroundAllowed", plan.adaptiveLowPriorityBackgroundAllowed)
            .put("adaptiveReason", plan.adaptiveReason)
            .put("resourceEquationMode", plan.resourceEquationMode)
            .put("resourceEquationModel", plan.resourceEquationModel)
            .put("resourceEquationAxisCoverage", plan.resourceEquationAxisCoverage)
            .put("resourceEquationAxisContract", plan.resourceEquationAxisContract)
            .put("resourceEquationCpuAxisStatus", plan.resourceEquationCpuAxisStatus)
            .put("resourceEquationIoAxisStatus", plan.resourceEquationIoAxisStatus)
            .put("resourceEquationMemoryAxisStatus", plan.resourceEquationMemoryAxisStatus)
            .put("resourceEquationBlindAxisCount", plan.resourceEquationBlindAxisCount)
            .put("resourceEquationCalibrationGate", plan.resourceEquationCalibrationGate)
            .put("resourceEquationLiveTraceeRatioPercent", plan.resourceEquationLiveTraceeRatioPercent)
            .put("resourceEquationCpuRatioPercent", plan.resourceEquationCpuRatioPercent)
            .put("resourceEquationIoRatioPercent", plan.resourceEquationIoRatioPercent)
            .put("resourceEquationMemoryRatioPercent", plan.resourceEquationMemoryRatioPercent)
            .put("resourceEquationBudgetRatioPercent", plan.resourceEquationBudgetRatioPercent)
            .put("resourceEquationPressureScorePercent", plan.resourceEquationPressureScorePercent)
            .put("resourceEquationRiskPercent", plan.resourceEquationRiskPercent)
            .put("resourceEquationHeadroomPercent", plan.resourceEquationHeadroomPercent)
            .put("resourceEquationBottleneckAxis", plan.resourceEquationBottleneckAxis)
            .put("resourceEquationExpansionAllowed", plan.resourceEquationExpansionAllowed)
            .put("resourceEquationLowPriorityQueueRequired", plan.resourceEquationLowPriorityQueueRequired)
            .put("resourceEquationTargetParallelSlots", plan.resourceEquationTargetParallelSlots)
            .put("resourceEquationTargetQueueDepth", plan.resourceEquationTargetQueueDepth)
            .put("resourceEquationDecision", plan.resourceEquationDecision)
            .put("resourceEquationLiveTraceeRaw", plan.resourceEquationLiveTraceeRaw)
            .put("resourceEquationLiveTraceeCap", plan.resourceEquationLiveTraceeCap)
            .put("resourceEquationCpuRawTicksPerSecond", plan.resourceEquationCpuRawTicksPerSecond)
            .put("resourceEquationCpuCapTicksPerSecond", plan.resourceEquationCpuCapTicksPerSecond)
            .put("resourceEquationIoRawBytesPerSecond", plan.resourceEquationIoRawBytesPerSecond)
            .put("resourceEquationIoCapBytesPerSecond", plan.resourceEquationIoCapBytesPerSecond)
            .put("resourceEquationMemoryRawLevel", plan.resourceEquationMemoryRawLevel.name)
            .put("resourceEquationMemoryCapLevel", plan.resourceEquationMemoryCapLevel.name)
            .put("resourceEquationBudgetRawState", plan.resourceEquationBudgetRawState.name)
            .put("resourceEquationBudgetCapState", plan.resourceEquationBudgetCapState.name)
            .put("resourceEquationPressureScoreRaw", plan.resourceEquationPressureScoreRaw)
            .put("resourceEquationPressureScoreCap", plan.resourceEquationPressureScoreCap)
            .put("resourceEquationCalibrationStatus", plan.resourceEquationCalibrationStatus)
            .put("resourceEquationNextCalibrationFocus", plan.resourceEquationNextCalibrationFocus)
            .put("resourceEquationReason", plan.resourceEquationReason)
            .put("candidatePoolSlots", plan.tuningCandidatePoolSlots)
            .put("nextCandidateIfPass", plan.tuningNextCandidateIfPass)
            .put("nextCandidateIfFail", plan.tuningNextCandidateIfFail)
            .put("lowerBoundPoolSlots", plan.tuningLowerBoundPoolSlots)
            .put("upperBoundPoolSlots", plan.tuningUpperBoundPoolSlots)
            .put("stopCondition", plan.tuningStopCondition)
            .put("substrateHealthy", plan.substrateHealthy)
            .put("pressureState", plan.pressureState.name)
            .put("pressureStabilityState", plan.pressureStabilityState.name)
            .put("budgetOverallState", plan.budgetOverallState.name)
            .put("prootSignal", plan.prootSignalLevel.name)
            .put("prootPressureScore", plan.prootPressureScore)
            .put("eventsInWindow", plan.eventsInWindow)
            .put("forkExecEventsInWindow", plan.forkExecEventsInWindow)
            .put("liveTraceeCount", plan.liveTraceeCount)
            .put("resourceMetricSource", snapshot.pressureConsumer.resourceMetricSource)
            .put("processResourceCount", snapshot.pressureConsumer.processResourceCount)
            .put("processResourceRssKb", snapshot.pressureConsumer.processResourceRssKb)
            .put("processResourceCpuTimeTicks", snapshot.pressureConsumer.processResourceCpuTimeTicks)
            .put("processResourceIoReadBytes", snapshot.pressureConsumer.processResourceIoReadBytes)
            .put("processResourceIoWriteBytes", snapshot.pressureConsumer.processResourceIoWriteBytes)
            .put("rootCpuTimeTicks", snapshot.pressureConsumer.rootCpuTimeTicks)
            .put("rootIoReadBytes", snapshot.pressureConsumer.rootIoReadBytes)
            .put("rootIoWriteBytes", snapshot.pressureConsumer.rootIoWriteBytes)
            .put("resourceTrendStatus", snapshot.pressureConsumer.resourceTrendStatus)
            .put("resourceTrendWindowMs", snapshot.pressureConsumer.resourceTrendWindowMs)
            .put("rootCpuDeltaTicks", snapshot.pressureConsumer.rootCpuDeltaTicks)
            .put("rootCpuTicksPerSecond", snapshot.pressureConsumer.rootCpuTicksPerSecond)
            .put("rootIoReadDeltaBytes", snapshot.pressureConsumer.rootIoReadDeltaBytes)
            .put("rootIoWriteDeltaBytes", snapshot.pressureConsumer.rootIoWriteDeltaBytes)
            .put("rootIoBytesPerSecond", snapshot.pressureConsumer.rootIoBytesPerSecond)
            .put("resourceDominantAxis", snapshot.pressureConsumer.resourceDominantAxis)
            .put("resourceAxisCoverage", snapshot.pressureConsumer.resourceAxisCoverage)
            .put("activeWorkloadCount", plan.activeWorkloadCount)
            .put("plannedPoolSlots", plan.plannedPoolSlots)
            .put("effectivePoolSlots", plan.effectivePoolSlots)
            .put("sparePoolSlots", plan.sparePoolSlots)
            .put("reason", plan.reason)
            .toString()
    }

    private fun readProotTelemetryLaunchState(context: Context?): RuntimeProotTelemetryLaunchState {
        val requestedMode = ProotLaunchPlan.TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0
        if (context == null) {
            return RuntimeProotTelemetryLaunchState(
                requestedMode = requestedMode,
                status = "context_unavailable"
            )
        }
        return runCatching {
            val layout = AssetExtractor.getRuntimeLayout(context.applicationContext)
            val descriptorFile = layout.prootRuntimeDescriptorFile
            val descriptor = if (descriptorFile.exists()) {
                JSONObject(descriptorFile.readText())
            } else {
                JSONObject()
            }
            val descriptorMode = descriptor.optString("telemetryMode")
                .ifBlank { ProotLaunchPlan.TELEMETRY_NONE_CURRENT }
            val supportsRequested = descriptorMode == requestedMode
            RuntimeProotTelemetryLaunchState(
                requestedMode = requestedMode,
                runtimeDescriptorMode = descriptorMode,
                runtimeAssetId = descriptor.optString("assetId").ifBlank { "unknown" },
                runtimeActiveRuntimeId = descriptor.optString("activeRuntimeId").ifBlank { "unknown" },
                runtimeProvider = descriptor.optString("provider").ifBlank { "unknown" },
                substrateState = descriptor.optString("telemetrySubstrateState").ifBlank {
                    if (supportsRequested) "PASS" else "BLOCKED"
                },
                substrateReason = descriptor.optString("telemetrySubstrateReason").ifBlank {
                    if (supportsRequested) {
                        "runtime_descriptor_supports_requested_telemetry"
                    } else {
                        "runtime_descriptor_does_not_support_requested_telemetry"
                    }
                },
                telemetryCapableCandidateRuntimeId = descriptor
                    .optString("telemetryCapableCandidateRuntimeId")
                    .ifBlank { "none" },
                telemetryCapableCandidateValidationState = descriptor
                    .optString("telemetryCapableCandidateValidationState")
                    .ifBlank { "none" },
                runtimeDescriptorPath = descriptorFile.absolutePath,
                supportsRequestedTelemetry = supportsRequested,
                status = if (supportsRequested) {
                    "runtime_descriptor_supports_requested_telemetry"
                } else {
                    "runtime_descriptor_does_not_support_requested_telemetry"
                }
            )
        }.getOrElse { error ->
            RuntimeProotTelemetryLaunchState(
                requestedMode = requestedMode,
                runtimeDescriptorMode = ProotLaunchPlan.TELEMETRY_NONE_CURRENT,
                runtimeAssetId = "invalid_descriptor",
                runtimeProvider = "runtime_descriptor_read_failed",
                status = "runtime_descriptor_read_failed:${error.javaClass.simpleName}"
            )
        }
    }

    private fun buildSnapshot(
        overview: RuntimeOverviewSnapshot,
        processSnapshot: ContainerProcessSnapshot,
        supervisordServices: SupervisordServiceHealthSnapshot,
        prootTelemetry: ProotTelemetrySnapshot,
        containerMetadata: ContainerRuntimeMetadata,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        residentPolicy: RuntimeResidentPolicy,
        workloadPolicy: RuntimeWorkloadPolicy,
        processUnitManifest: RuntimeProcessUnitManifest,
        lifecycleSignal: RuntimeLifecycleSignalSnapshot,
        reconciliationReason: String
    ): RuntimeHealthSnapshot {
        val resourceSamples = containerMetadata.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?.let { workspacePath ->
                readRuntimeProcessTableResourceSamples(
                    WorkspaceBuildSupport.runtimeProcessTableFile(File(workspacePath))
                )
            }
            ?: emptyMap()
        val roots = buildRoots(
            terminals = overview.terminalSessions,
            runtimes = overview.backgroundRuntimes,
            processes = processSnapshot.processes,
            prootTelemetry = prootTelemetry,
            reclaimerPolicy = reclaimerPolicy,
            processUnitManifest = processUnitManifest,
            pidFileReader = applicationContext
                ?.let(RuntimeProcessUnitPidFileReader::fromContext)
                ?: RuntimeProcessUnitPidFileReader.noop(),
            processRefreshedAt = processSnapshot.refreshedAt
        ).withRuntimeProcessResourceSamples(resourceSamples)
        val processResourceSnapshot = processSnapshot.resourceSnapshot
            .withRuntimeProcessResourceSamples(resourceSamples)
        val controlledProbeVisibility = buildControlledProbeProcessVisibility(
            processes = processSnapshot.processes,
            prootTelemetry = prootTelemetry,
            processUnitManifest = processUnitManifest,
            pidFileReader = applicationContext
                ?.let(RuntimeProcessUnitPidFileReader::fromContext)
                ?: RuntimeProcessUnitPidFileReader.noop(),
            processRefreshedAt = processSnapshot.refreshedAt
        )

        val pressure = RuntimePressureGuard.evaluate(roots, reclaimerPolicy)
        val prootTelemetryLaunchState = readProotTelemetryLaunchState(applicationContext)
        val prootTelemetryHealth = ProotTelemetryHealthDryRun.evaluate(prootTelemetry)
        val prootTelemetryRepairPlan = ProotTelemetryRepairPlanDryRun.evaluate(
            health = prootTelemetryHealth,
            telemetry = prootTelemetry
        )
        val pressureConsumer = RuntimePressureConsumer.evaluate(
            prootTelemetry = prootTelemetry,
            roots = roots,
            pressure = pressure,
            processResources = processResourceSnapshot
        )
        val workloadRegistry = RuntimeWorkloadRegistry.evaluate(
            roots = roots,
            prootTelemetry = prootTelemetry,
            policy = workloadPolicy
        )
        val lifecyclePolicySurface = RuntimeLifecyclePolicySurfaceDryRun.evaluate(
            policy = workloadPolicy
        )
        val lifecyclePolicyProfileSurface = RuntimeLifecyclePolicyProfileSurfaceDryRun.evaluate(
            reclaimerPolicy = reclaimerPolicy,
            residentPolicy = residentPolicy,
            workloadPolicy = workloadPolicy,
            lifecyclePolicySurface = lifecyclePolicySurface
        )
        val lifecycleIntentSurface = RuntimeLifecycleIntentSurfaceDryRun.evaluate(
            workspacePath = containerMetadata.workspacePath
        )
        val backgroundDecay = RuntimeBackgroundDecayDryRun.evaluate(
            lifecycle = lifecycleSignal,
            workloadRegistry = workloadRegistry,
            pressureConsumer = pressureConsumer,
            policy = workloadPolicy.backgroundDecay
        )
        val budgetPressure = RuntimeBudgetPressureDryRun.evaluate(
            workloadRegistry = workloadRegistry,
            pressureConsumer = pressureConsumer,
            backgroundDecay = backgroundDecay,
            roots = roots,
            policy = workloadPolicy
        )
        val lifecycleReclaimExecutionEnabled = workloadPolicy.lifecycleManagementEnabled
        val lifecycleReclaimPlan = RuntimeLifecycleReclaimPlanDryRun.evaluate(
            workloadRegistry = workloadRegistry,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            pressureConsumer = pressureConsumer,
            pressure = pressure,
            policy = workloadPolicy,
            enforcementEnabled = lifecycleReclaimExecutionEnabled,
            enforcementMode = if (lifecycleReclaimExecutionEnabled) {
                "lease_reclaim_executor_armed"
            } else {
                "lifecycle_management_disabled"
            }
        )
        val pressureStability = RuntimePressureStabilityGateDryRun.evaluate(
            pressureConsumer = pressureConsumer,
            budgetPressure = budgetPressure
        )
        val laneAdmission = RuntimeLaneAdmissionDryRun.evaluate(
            workloadRegistry = workloadRegistry,
            pressureConsumer = pressureConsumer,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            policy = workloadPolicy
        )
        val startPreflight = RuntimeStartPreflightDryRun.evaluate(
            workloadRegistry = workloadRegistry,
            pressureConsumer = pressureConsumer,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            laneAdmission = laneAdmission,
            policy = workloadPolicy
        )
        val startQueuePlan = RuntimeStartQueuePlanDryRun.evaluate(
            startPreflight = startPreflight,
            laneAdmission = laneAdmission,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            pressureConsumer = pressureConsumer,
            policy = workloadPolicy
        )
        val prootDeviceCalibrationOverlay = RuntimeProotDeviceCalibrationOverlayStore.load(
            containerMetadata.workspacePath
        )
        val prootPoolPlan = RuntimeProotPoolPlanDryRun.evaluate(
            prootTelemetryHealth = prootTelemetryHealth,
            pressureConsumer = pressureConsumer,
            pressureStability = pressureStability,
            workloadRegistry = workloadRegistry,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            laneAdmission = laneAdmission,
            startQueuePlan = startQueuePlan,
            lifecyclePolicyProfileSurface = lifecyclePolicyProfileSurface,
            deviceCalibrationOverlay = prootDeviceCalibrationOverlay,
            declaredProbeTargetLiveTracees = prootTelemetry.probeDeclaredTargetLiveTracees
        )
        val prootManagementMainline = RuntimeProotManagementMainlineDryRun.evaluate(
            prootTelemetryHealth = prootTelemetryHealth,
            pressureConsumer = pressureConsumer,
            pressureStability = pressureStability,
            budgetPressure = budgetPressure,
            prootPoolPlan = prootPoolPlan
        )
        val prootDeviceCalibration = RuntimeProotDeviceCalibrationDryRun.evaluate(
            prootTelemetryHealth = prootTelemetryHealth,
            pressureConsumer = pressureConsumer,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            prootPoolPlan = prootPoolPlan,
            overlay = prootDeviceCalibrationOverlay
        )
        val prootCapacityExecutorPolicy = applicationContext
            ?.let(RuntimeProotCapacityExecutorPolicyStore::load)
            ?: RuntimeProotCapacityExecutorPolicy()
        val lifecycleProotExpansionBudget = RuntimeLifecycleProotExpansionBudgetDryRun.evaluate(
            pressure = pressure,
            budgetPressure = budgetPressure,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            prootPoolPlan = prootPoolPlan,
            capacityPolicy = prootCapacityExecutorPolicy
        )
        val prootCapacityExecutor = RuntimeProotCapacityExecutor.evaluate(
            lifecycleBudget = lifecycleProotExpansionBudget,
            bindingPolicy = prootCapacityExecutorPolicy,
            backgroundRuntimes = overview.backgroundRuntimes
        )
        val runtimeHostState = if (overview.backgroundRuntimes.any {
                it.kind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR && it.isActiveRuntime()
            }
        ) {
            RuntimeHostLifecycleState.RUNNING
        } else {
            RuntimeHostLifecycleState.STOPPED
        }
        val resourceEventLedgerFile = containerMetadata.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?.let { WorkspaceBuildSupport.runtimeResourceEventLedgerFile(File(it)) }
        val lifecycleActionInboxFile = containerMetadata.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?.let { WorkspaceBuildSupport.runtimeLifecycleActionInboxFile(File(it)) }
        val systemProcessLifecycle = RuntimeSystemProcessLifecycleDryRun.evaluate(
            pressure = pressure,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            lifecycleProotExpansionBudget = lifecycleProotExpansionBudget,
            prootCapacityExecutor = prootCapacityExecutor,
            startQueuePlan = startQueuePlan,
            backgroundRuntimes = overview.backgroundRuntimes,
            capacityPolicy = prootCapacityExecutorPolicy,
            runtimeHostState = runtimeHostState,
            roots = roots,
            processUnitManifest = processUnitManifest,
            resourceEventLedgerFile = resourceEventLedgerFile,
            lifecycleActionInboxFile = lifecycleActionInboxFile
        )
        val processUnitManifestSnapshot = processUnitManifest.snapshot(
            roots = roots,
            states = systemProcessLifecycle.processUnitStates
        )
        RuntimeLifecycleLedgerStore.recordSnapshot(
            workspacePath = containerMetadata.workspacePath,
            roots = roots,
            reclaimPlan = lifecycleReclaimPlan,
            now = lifecycleReclaimPlan.generatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        val processObservationProbe = RuntimeProcessObservationProbeSnapshot(
            mode = "legacy_process_observation_probe_disabled",
            probeEnabled = false,
            boundary = "legacy_disabled_mainline_uses_host_proc_container_ps"
        )
        val processObservationValidationReport = RuntimeProcessObservationValidationReport(
            mode = "legacy_process_observation_validation_disabled",
            boundary = "legacy_disabled_no_user_notice_no_mainline_gate",
            nextRecommendedStep = "use_host_proc_container_ps_process_unit_matching"
        )
        val lifecycleWarningNotice = RuntimeLifecycleUserNotice.evaluate(
            actionInbox = systemProcessLifecycle.lifecycleActionInbox,
            actionPlanner = systemProcessLifecycle.lifecycleActionPlanner,
            resourceWatch = systemProcessLifecycle.processResourceWatch,
            resourceEventLedger = systemProcessLifecycle.resourceEventLedger,
            diagnosticReview = systemProcessLifecycle.lifecycleDiagnosticReview
        )
        val governanceActionPlan = RuntimeGovernanceActionPlanDryRun.evaluate(
            pressureConsumer = pressureConsumer,
            workloadRegistry = workloadRegistry,
            budgetPressure = budgetPressure,
            laneAdmission = laneAdmission,
            startPreflight = startPreflight,
            startQueuePlan = startQueuePlan,
            lifecycleIntentSurface = lifecycleIntentSurface,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            lifecycleProotExpansionBudget = lifecycleProotExpansionBudget,
            backgroundDecay = backgroundDecay
        )
        val governanceReadiness = RuntimeGovernanceReadinessGateDryRun.evaluate(
            prootTelemetryHealth = prootTelemetryHealth,
            prootTelemetryRepairPlan = prootTelemetryRepairPlan,
            pressureConsumer = pressureConsumer,
            pressureStability = pressureStability,
            budgetPressure = budgetPressure,
            laneAdmission = laneAdmission,
            startQueuePlan = startQueuePlan,
            governanceActionPlan = governanceActionPlan,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            backgroundDecay = backgroundDecay
        )
        val canaryEntryPlan = RuntimeCanaryEntryPlanDryRun.evaluate(
            readiness = governanceReadiness,
            pressureStability = pressureStability
        )
        val canaryScopePlan = RuntimeCanaryScopePlanDryRun.evaluate(
            canaryEntry = canaryEntryPlan
        )
        val canaryActivationPlan = RuntimeCanaryActivationPlanDryRun.evaluate(
            scopePlan = canaryScopePlan
        )
        val canarySessionPlan = RuntimeCanarySessionPlanDryRun.evaluate(
            activationPlan = canaryActivationPlan
        )
        val canaryApprovalRequest = RuntimeCanaryApprovalRequestDryRun.evaluate(
            sessionPlan = canarySessionPlan
        )
        val canaryApprovalGate = RuntimeCanaryApprovalGateDryRun.evaluate(
            approvalRequest = canaryApprovalRequest
        )
        val canaryGrantPlan = RuntimeCanaryGrantPlanDryRun.evaluate(
            approvalGate = canaryApprovalGate
        )
        val canarySessionStartPlan = RuntimeCanarySessionStartPlanDryRun.evaluate(
            grantPlan = canaryGrantPlan
        )
        val canarySessionLeasePlan = RuntimeCanarySessionLeasePlanDryRun.evaluate(
            sessionStartPlan = canarySessionStartPlan
        )
        val canaryEnforcementPlan = RuntimeCanaryEnforcementPlanDryRun.evaluate(
            sessionLeasePlan = canarySessionLeasePlan
        )
        val canaryRollbackPlan = RuntimeCanaryRollbackPlanDryRun.evaluate(
            enforcementPlan = canaryEnforcementPlan,
            telemetryHealth = prootTelemetryHealth,
            pressureStability = pressureStability
        )
        val canaryAuditPlan = RuntimeCanaryAuditPlanDryRun.evaluate(
            pressureStability = pressureStability,
            governanceReadiness = governanceReadiness,
            canaryEntry = canaryEntryPlan,
            canaryScope = canaryScopePlan,
            canaryActivation = canaryActivationPlan,
            canarySession = canarySessionPlan,
            approvalRequest = canaryApprovalRequest,
            approvalGate = canaryApprovalGate,
            grantPlan = canaryGrantPlan,
            sessionStartPlan = canarySessionStartPlan,
            sessionLeasePlan = canarySessionLeasePlan,
            enforcementPlan = canaryEnforcementPlan,
            rollbackPlan = canaryRollbackPlan
        )
        val managementTopology = RuntimeManagementTopologyDryRun.evaluate(
            prootTelemetryHealth = prootTelemetryHealth,
            pressureConsumer = pressureConsumer,
            pressureStability = pressureStability,
            prootPoolPlan = prootPoolPlan,
            prootManagementMainline = prootManagementMainline,
            lifecyclePolicySurface = lifecyclePolicySurface,
            lifecyclePolicyProfileSurface = lifecyclePolicyProfileSurface,
            lifecycleIntentSurface = lifecycleIntentSurface,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            lifecycleProotExpansionBudget = lifecycleProotExpansionBudget,
            prootCapacityExecutor = prootCapacityExecutor,
            workloadRegistry = workloadRegistry,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            laneAdmission = laneAdmission,
            startPreflight = startPreflight,
            startQueuePlan = startQueuePlan,
            governanceReadiness = governanceReadiness,
            canaryAudit = canaryAuditPlan
        )

        return RuntimeHealthSnapshot(
            spaceId = overview.spaceId ?: processSnapshot.spaceId,
            containerId = containerMetadata.containerId,
            legacyContainerPid = containerMetadata.legacyContainerPid,
            networkModeLabel = containerMetadata.networkMode?.label,
            networkSemantics = containerMetadata.networkSemantics,
            containerLastError = containerMetadata.lastError,
            supervisordServices = supervisordServices.takeIf { it.refreshedAt > 0L },
            processResourceSnapshot = processResourceSnapshot,
            roots = roots,
            processObservationProbe = processObservationProbe,
            processObservationValidationReport = processObservationValidationReport,
            lifecycleWarningNotice = lifecycleWarningNotice,
            pressure = pressure,
            prootTelemetry = prootTelemetry,
            prootTelemetryLaunchState = prootTelemetryLaunchState,
            prootTelemetryHealth = prootTelemetryHealth,
            prootTelemetryRepairPlan = prootTelemetryRepairPlan,
            pressureConsumer = pressureConsumer,
            pressureStability = pressureStability,
            prootPoolPlan = prootPoolPlan,
            prootManagementMainline = prootManagementMainline,
            prootDeviceCalibration = prootDeviceCalibration,
            workloadRegistry = workloadRegistry,
            processUnitManifest = processUnitManifestSnapshot,
            lifecyclePolicySurface = lifecyclePolicySurface,
            lifecyclePolicyProfileSurface = lifecyclePolicyProfileSurface,
            lifecycleIntentSurface = lifecycleIntentSurface,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            lifecycleProotExpansionBudget = lifecycleProotExpansionBudget,
            prootCapacityExecutor = prootCapacityExecutor,
            systemProcessLifecycle = systemProcessLifecycle,
            laneAdmission = laneAdmission,
            startPreflight = startPreflight,
            startQueuePlan = startQueuePlan,
            governanceActionPlan = governanceActionPlan,
            governanceReadiness = governanceReadiness,
            canaryEntryPlan = canaryEntryPlan,
            canaryScopePlan = canaryScopePlan,
            canaryActivationPlan = canaryActivationPlan,
            canarySessionPlan = canarySessionPlan,
            canaryApprovalRequest = canaryApprovalRequest,
            canaryApprovalGate = canaryApprovalGate,
            canaryGrantPlan = canaryGrantPlan,
            canarySessionStartPlan = canarySessionStartPlan,
            canarySessionLeasePlan = canarySessionLeasePlan,
            canaryEnforcementPlan = canaryEnforcementPlan,
            canaryRollbackPlan = canaryRollbackPlan,
            canaryAuditPlan = canaryAuditPlan,
            managementTopology = managementTopology,
            reclaimerPolicyProfile = reclaimerPolicy.activeProfile.name,
            reclaimerPolicyPath = reclaimerPolicy.policyPath,
            residentPolicyProfile = residentPolicy.activeProfile.name,
            residentPolicyPath = residentPolicy.policyPath,
            runtimePolicyHotReloadEnabled = policyAutoRefreshJob?.isActive == true,
            runtimePolicyHotReloadIntervalMs = POLICY_AUTO_REFRESH_INTERVAL_MS,
            runtimePolicyHotReloadGeneration = policyHotReloadGeneration,
            runtimePolicyHotReloadLastReloadAt = policyHotReloadLastReloadAt,
            runtimePolicyHotReloadLastChanged = policyHotReloadLastChanged,
            runtimePolicyHotReloadWorkspacePath = policyAutoRefreshWorkspacePath,
            processSnapshotSource = processSnapshot.collectionSource,
            processSnapshotRefreshedAt = processSnapshot.refreshedAt,
            processSnapshotHostProcessCount = processSnapshot.hostProcessCount,
            processSnapshotContainerProcessCount = processSnapshot.containerProcessCount,
            processSnapshotMergedProcessCount = processSnapshot.mergedProcessCount,
            processSnapshotSample = processSnapshot.processSample,
            controlledProbeVisibility = controlledProbeVisibility,
            overviewRefreshedAt = overview.refreshedAt,
            reconciledAt = System.currentTimeMillis(),
            reconciliationReason = reconciliationReason
        )
    }

    private fun buildRoots(
        terminals: List<TerminalRuntimeEntry>,
        runtimes: List<BackgroundRuntimeRecord>,
        processes: List<ContainerProcessRecord>,
        prootTelemetry: ProotTelemetrySnapshot,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader,
        processRefreshedAt: Long
    ): List<RuntimeRootSnapshot> {
        val terminalRoots = terminals
            .filter { it.status.isLiveProcessStatus() }
            .map { terminal ->
                val group = processes.filter { it.linkedTerminalSessionId == terminal.sessionId }
                val rootProcess = chooseRootProcess(group, terminal.lastPid)
                terminal.toRuntimeRoot(rootProcess, group, processRefreshedAt)
            }

        val runtimeRoots = runtimes
            .filter { it.isActiveRuntime() }
            .map { runtime ->
                val group = processes.filter { it.pid == runtime.pid }
                val rootProcess = chooseRootProcess(group, runtime.pid)
                runtime.toRuntimeRoot(rootProcess, group, processRefreshedAt)
            }

        val existingTerminalOwnerIds = terminalRoots
            .mapNotNull { root -> root.ownerId?.takeIf { it.isNotBlank() }?.let { "terminal:$it" } }
            .toSet()
        val ownerRoots = buildProotOwnerRoots(
            prootTelemetry = prootTelemetry,
            excludedOwnerIds = existingTerminalOwnerIds,
            processRefreshedAt = processRefreshedAt
        )
        val attributedOwnerIds = ownerRoots
            .mapNotNull { it.ownerId }
            .toSet()
        val ownerTraceePids = prootTelemetry.ownerProcessIndex.groups
            .filter { group -> group.ownerId in attributedOwnerIds || group.ownerId in existingTerminalOwnerIds }
            .flatMapTo(mutableSetOf()) { it.traceePids }
        val attributedPids = (terminalRoots + runtimeRoots)
            .mapNotNull { it.observedPid }
            .toSet()
        val unownedRoots = buildUnattributedRoots(
            processes = processes,
            prootTelemetry = prootTelemetry,
            attributedRootPids = attributedPids + ownerTraceePids,
            attributedRuntimeRootPids = runtimeRoots.mapNotNullTo(mutableSetOf()) { it.observedPid },
            reclaimerPolicy = reclaimerPolicy,
            processUnitManifest = processUnitManifest,
            pidFileReader = pidFileReader,
            processRefreshedAt = processRefreshedAt
        )

        return processUnitManifest.applyToRoots(
            roots = terminalRoots + ownerRoots + runtimeRoots + unownedRoots,
            pidFileReader = pidFileReader
        )
            .sortedWith(
                compareBy<RuntimeRootSnapshot> { it.ownerKind.ordinal }
                    .thenByDescending { it.isActiveOwner }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.observedPid ?: it.expectedPid ?: Int.MAX_VALUE }
            )
    }

    private fun TerminalRuntimeEntry.toRuntimeRoot(
        rootProcess: ContainerProcessRecord?,
        group: List<ContainerProcessRecord>,
        processRefreshedAt: Long
    ): RuntimeRootSnapshot {
        val expected = lastPid?.takeIf { it > 0 }
        val memory = group.toMemorySummary()
        return RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.TERMINAL,
            ownerId = sessionId,
            title = title,
            statusLabel = status.label,
            expectedPid = expected,
            observedPid = rootProcess?.pid,
            parentPid = rootProcess?.parentPid,
            rootProcessGroupId = rootProcess?.processGroupId,
            rootSessionId = rootProcess?.sessionId,
            processCount = group.size,
            rssKb = memory.rssKb,
            vmSizeKb = memory.vmSizeKb,
            maxOomScoreAdj = memory.maxOomScoreAdj,
            cpuTimeTicks = memory.cpuTimeTicks,
            ioReadBytes = memory.ioReadBytes,
            ioWriteBytes = memory.ioWriteBytes,
            retentionClass = RuntimeRetentionClass.INTERACTIVE,
            resident = true,
            reclaimPriority = RuntimeRetentionClass.INTERACTIVE.reclaimPriority,
            autoReclaimAllowed = false,
            classificationSource = "builtin:terminal_interactive",
            classificationReason = "interactive terminal root",
            sourceLabel = rootProcess?.sourceLabel.orEmpty(),
            commandLine = rootProcess?.commandLine.orEmpty(),
            observedStatusLabel = rootProcess?.stateLabel,
            isActiveOwner = isActive,
            reality = resolveReality(rootProcess, processRefreshedAt),
            lastSeenAt = rootProcess?.let { processRefreshedAt },
            lastStartedAt = lastStartedAt,
            lastExitedAt = lastExitedAt,
            lastExitCode = lastExitCode,
            staleReason = if (rootProcess == null && processRefreshedAt > 0L) {
                buildStaleReason(expected, status.label)
            } else {
                null
            }
        )
    }

    private fun BackgroundRuntimeRecord.toRuntimeRoot(
        rootProcess: ContainerProcessRecord?,
        group: List<ContainerProcessRecord>,
        processRefreshedAt: Long
    ): RuntimeRootSnapshot {
        val expected = pid?.takeIf { it > 0 }
        val memory = group.toMemorySummary()
        val genericAutoReclaimAllowed = retentionClass == RuntimeRetentionClass.EPHEMERAL ||
            retentionClass == RuntimeRetentionClass.BATCH
        val capacityWorkerProtected = kind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
            kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER
        return RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.BACKGROUND_RUNTIME,
            ownerId = id,
            title = title,
            statusLabel = status.label,
            expectedPid = expected,
            observedPid = rootProcess?.pid,
            parentPid = rootProcess?.parentPid,
            rootProcessGroupId = rootProcess?.processGroupId,
            rootSessionId = rootProcess?.sessionId,
            processCount = group.size,
            rssKb = memory.rssKb,
            vmSizeKb = memory.vmSizeKb,
            maxOomScoreAdj = memory.maxOomScoreAdj,
            cpuTimeTicks = memory.cpuTimeTicks,
            ioReadBytes = memory.ioReadBytes,
            ioWriteBytes = memory.ioWriteBytes,
            retentionClass = retentionClass,
            resident = retentionClass.resident,
            reclaimPriority = retentionClass.reclaimPriority,
            autoReclaimAllowed = genericAutoReclaimAllowed && !capacityWorkerProtected,
            classificationSource = "runtime_registry",
            classificationReason = if (capacityWorkerProtected) {
                "registered runtime kind=${kind.name} protected from generic lifecycle reclaim"
            } else {
                "registered runtime retention=${retentionClass.name}"
            },
            sourceLabel = rootProcess?.sourceLabel.orEmpty(),
            commandLine = rootProcess?.commandLine.orEmpty(),
            runtimeKind = kind,
            observedStatusLabel = rootProcess?.stateLabel,
            reality = resolveReality(rootProcess, processRefreshedAt),
            lastSeenAt = rootProcess?.let { processRefreshedAt },
            lastStartedAt = lastStartedAt,
            lastExitedAt = lastStoppedAt,
            lastExitCode = lastExitCode,
            lastError = lastError,
            restartPolicyLabel = restartPolicy.label,
            restartFailureCount = restartFailureCount,
            nextRestartAllowedAt = nextRestartAllowedAt,
            lastRestartReason = lastRestartReason,
            lastRecoveredAt = lastRecoveredAt,
            lastRecoverySource = lastRecoverySource,
            lastRecoveryReason = lastRecoveryReason,
            lastAdmissionDeferredAt = lastAdmissionDeferredAt,
            lastAdmissionSource = lastAdmissionSource,
            lastAdmissionReason = lastAdmissionReason,
            lastReclaimedAt = lastReclaimedAt,
            lastReclaimSource = lastReclaimSource,
            lastReclaimReason = lastReclaimReason,
            stopReconciliationState = lastStopReconciliationState,
            stopReconciliationReason = lastStopReconciliationReason,
            stopReconciliationAt = lastStopReconciliationAt,
            stopReconciliationAutoRecoverySuppressed = lastStopReconciliationAutoRecoverySuppressed,
            staleReason = if (rootProcess == null && processRefreshedAt > 0L) {
                buildStaleReason(expected, status.label)
            } else {
                null
            }
        )
    }

    private fun chooseRootProcess(
        processes: List<ContainerProcessRecord>,
        expectedPid: Int?
    ): ContainerProcessRecord? {
        if (processes.isEmpty()) return null
        val expected = expectedPid?.takeIf { it > 0 }
        if (expected != null) {
            processes.firstOrNull { it.pid == expected }?.let { return it }
        }
        val pidSet = processes.mapTo(mutableSetOf()) { it.pid }
        return processes.firstOrNull { it.parentPid !in pidSet } ?: processes.minByOrNull { it.pid }
    }

    private fun List<ContainerProcessRecord>.toMemorySummary(): RuntimeRootMemorySummary {
        return RuntimeRootMemorySummary(
            rssKb = sumOf { it.rssKb ?: 0L },
            vmSizeKb = sumOf { it.vmSizeKb ?: 0L },
            maxOomScoreAdj = mapNotNull { it.oomScoreAdj }.maxOrNull(),
            cpuTimeTicks = sumOf { it.cpuTimeTicks ?: 0L },
            ioReadBytes = sumOf { it.ioReadBytes ?: 0L },
            ioWriteBytes = sumOf { it.ioWriteBytes ?: 0L }
        )
    }

    private data class ContainerSkeletonProtection(
        val processUnitIdPrefix: String,
        val displayName: String,
        val tier: RuntimeProcessUnitTier,
        val retentionClass: RuntimeRetentionClass,
        val manualKillPolicy: RuntimeProcessUnitManualKillPolicy,
        val source: String,
        val reason: String
    ) {
        fun unitId(pid: Int): String = "$processUnitIdPrefix:$pid"
    }

    private fun buildProotOwnerRoots(
        prootTelemetry: ProotTelemetrySnapshot,
        excludedOwnerIds: Set<String> = emptySet(),
        processRefreshedAt: Long
    ): List<RuntimeRootSnapshot> {
        val entriesByPid = prootTelemetry.processLiveTable.entries.associateBy { it.traceePid }
        return prootTelemetry.ownerProcessIndex.groups
            .mapNotNull { group ->
                if (group.ownerId in excludedOwnerIds) return@mapNotNull null
                val ownerKind = group.ownerId.toRuntimeOwnerKind() ?: return@mapNotNull null
                val rootPid = group.rootTraceePid(entriesByPid)
                RuntimeRootSnapshot(
                    ownerKind = ownerKind,
                    ownerId = group.ownerId,
                    title = group.ownerTitle(ownerKind),
                    statusLabel = "running",
                    observedPid = rootPid,
                    prootPid = group.prootPids.firstOrNull(),
                    rootProcessGroupId = group.processGroupIds.singleOrNull(),
                    rootSessionId = group.sessionIds.singleOrNull(),
                    processCount = group.liveTraceeCount,
                    retentionClass = RuntimeRetentionClass.INTERACTIVE,
                    resident = true,
                    reclaimPriority = RuntimeRetentionClass.INTERACTIVE.reclaimPriority,
                    autoReclaimAllowed = false,
                    classificationSource = "proot_telemetry_owner_process_index",
                    classificationReason = "PRoot event stream grouped live tracees by KF runtime owner id",
                    sourceLabel = "PRoot owner index",
                    commandLine = group.unitIds.joinToString(" "),
                    observedStatusLabel = "RUNNING",
                    reality = RuntimeRootReality.OBSERVED,
                    lastSeenAt = group.lastSeenAtMs.takeIf { it > 0L } ?: processRefreshedAt,
                    lastStartedAt = group.lastSeenAtMs.takeIf { it > 0L }
                )
            }
    }

    private fun String.toRuntimeOwnerKind(): RuntimeRootOwnerKind? {
        return when (substringBefore(':')) {
            "card" -> RuntimeRootOwnerKind.CARD
            "resource" -> RuntimeRootOwnerKind.RESOURCE
            "terminal" -> RuntimeRootOwnerKind.TERMINAL
            else -> null
        }
    }

    private fun ProotOwnerProcessGroup.ownerTitle(ownerKind: RuntimeRootOwnerKind): String {
        val id = ownerId.substringAfter(':', ownerId).ifBlank { ownerId }
        return when (ownerKind) {
            RuntimeRootOwnerKind.CARD -> "卡片 $id"
            RuntimeRootOwnerKind.RESOURCE -> "资源 $id"
            RuntimeRootOwnerKind.TERMINAL -> "终端 $id"
            RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> "后台 $id"
            RuntimeRootOwnerKind.UNATTRIBUTED -> "未归属 $id"
        }
    }

    private fun ProotOwnerProcessGroup.rootTraceePid(
        entriesByPid: Map<Int, ProotLiveProcessEntry>
    ): Int? {
        val traceeSet = traceePids.toSet()
        val candidates = traceePids.filter { pid ->
            val parent = entriesByPid[pid]?.parentTraceePid
            parent == null || parent !in traceeSet
        }
        return candidates.minOrNull() ?: traceePids.minOrNull()
    }

    private fun buildUnattributedRoots(
        processes: List<ContainerProcessRecord>,
        prootTelemetry: ProotTelemetrySnapshot,
        attributedRootPids: Set<Int>,
        attributedRuntimeRootPids: Set<Int>,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader,
        processRefreshedAt: Long
    ): List<RuntimeRootSnapshot> {
        val pidSet = processes.mapTo(mutableSetOf()) { it.pid }
        val strongManifestRootPids = processes
            .filter { process ->
                process.pid !in attributedRootPids &&
                    isStrongManifestRootCandidate(
                        process = process,
                        processUnitManifest = processUnitManifest,
                        pidFileReader = pidFileReader,
                        processRefreshedAt = processRefreshedAt
                    )
            }
            .mapTo(mutableSetOf()) { it.pid }
        val processRootCandidates = processes
            .filter { process ->
                process.pid !in attributedRootPids &&
                    (
                        (
                            process.linkedTerminalSessionId == null &&
                                process.linkedRuntimeId == null &&
                                process.parentPid !in pidSet
                            ) ||
                            process.parentPid in attributedRuntimeRootPids ||
                            isRuntimeDescendant(
                                process = process,
                                attributedRuntimeRootPids = attributedRuntimeRootPids,
                                processes = processes
                            ) ||
                            process.pid in strongManifestRootPids
                        )
            }
        val processRoots = processRootCandidates
            .map { process ->
                val memory = listOf(process).toMemorySummary()
                val classification = RuntimeReclaimerPolicyStore.classifyUnknownProcess(
                    process = process,
                    policy = reclaimerPolicy
                )
                val skeleton = process.containerSkeletonProtection()
                RuntimeRootSnapshot(
                    ownerKind = RuntimeRootOwnerKind.UNATTRIBUTED,
                    ownerId = null,
                    title = process.title,
                    statusLabel = process.stateLabel,
                    observedPid = process.pid,
                    parentPid = process.parentPid,
                    rootProcessGroupId = process.processGroupId,
                    rootSessionId = process.sessionId,
                    processCount = 1,
                    rssKb = memory.rssKb,
                    vmSizeKb = memory.vmSizeKb,
                    maxOomScoreAdj = memory.maxOomScoreAdj,
                    cpuTimeTicks = memory.cpuTimeTicks,
                    ioReadBytes = memory.ioReadBytes,
                    ioWriteBytes = memory.ioWriteBytes,
                    retentionClass = skeleton?.retentionClass ?: classification?.retentionClass ?: RuntimeRetentionClass.UNKNOWN,
                    resident = skeleton != null || (classification?.resident ?: false),
                    reclaimPriority = skeleton?.retentionClass?.reclaimPriority
                        ?: classification?.reclaimPriority
                        ?: RuntimeRetentionClass.UNKNOWN.reclaimPriority,
                    autoReclaimAllowed = if (skeleton != null) false else classification?.autoReclaimAllowed ?: false,
                    classificationSource = skeleton?.source ?: classification?.classificationSource ?: "unclassified",
                    classificationReason = skeleton?.reason ?: classification?.classificationReason ?: "no unknown-process rule matched",
                    sourceLabel = process.sourceLabel,
                    commandLine = process.commandLine,
                    observedStatusLabel = process.stateLabel,
                    reality = RuntimeRootReality.OBSERVED,
                    lastSeenAt = processRefreshedAt,
                    processUnitId = skeleton?.unitId(process.pid),
                    processUnitDisplayName = skeleton?.displayName,
                    processUnitTier = skeleton?.tier,
                    processUnitSource = skeleton?.source,
                    processUnitManualKillPolicy = skeleton?.manualKillPolicy,
                    processUnitUserEditable = false,
                    processUnitAllowReclaim = false,
                    processUnitAllowKill = false,
                    processUnitAllowRestart = false,
                    processUnitReason = skeleton?.reason,
                    processUnitMatchSource = if (skeleton != null) {
                        RuntimeProcessUnitMatchSource.BUILT_IN_CORE
                    } else {
                        RuntimeProcessUnitMatchSource.NONE
                    },
                    processUnitMatchConfidence = if (skeleton != null) {
                        RuntimeProcessUnitMatchConfidence.EXACT
                    } else {
                        RuntimeProcessUnitMatchConfidence.NONE
                    },
                    processUnitMatchState = if (skeleton != null) {
                        RuntimeProcessUnitMatchState.MATCHED_EXACT
                    } else {
                        RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED
                    },
                    processUnitMatchedPid = skeleton?.let { process.pid }
                )
            }
        val existingRootPids = processRoots
            .mapNotNullTo(mutableSetOf()) { it.observedPid }
        val telemetryRoots = buildProotTelemetryStrongManifestRoots(
            prootTelemetry = prootTelemetry,
            excludedRootPids = attributedRootPids + existingRootPids,
            processUnitManifest = processUnitManifest,
            pidFileReader = pidFileReader,
            processRefreshedAt = processRefreshedAt
        )
        val ordinaryTelemetryRoots = buildProotTelemetryOrdinaryRoots(
            prootTelemetry = prootTelemetry,
            excludedRootPids = attributedRootPids + existingRootPids +
                telemetryRoots.mapNotNullTo(mutableSetOf()) { it.observedPid },
            processRefreshedAt = processRefreshedAt
        )
        return processRoots + telemetryRoots + ordinaryTelemetryRoots
    }

    private fun isRuntimeDescendant(
        process: ContainerProcessRecord,
        attributedRuntimeRootPids: Set<Int>,
        processes: List<ContainerProcessRecord>
    ): Boolean {
        if (attributedRuntimeRootPids.isEmpty()) return false
        return attributedRuntimeRootPids.any { rootPid ->
            isDescendantOf(process, rootPid, processes)
        }
    }

    private fun buildControlledProbeProcessVisibility(
        processes: List<ContainerProcessRecord>,
        prootTelemetry: ProotTelemetrySnapshot,
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader,
        processRefreshedAt: Long
    ): RuntimeControlledProbeProcessVisibility {
        val matchingProcesses = processes.filter { process ->
            process.commandLine == RuntimeControlledLeaseProbeRegistration.COMMAND
        }
        val visibleProcess = matchingProcesses
            .sortedWith(
                compareByDescending<ContainerProcessRecord> { it.rssKb ?: 0L }
                    .thenByDescending { it.pid }
            )
            .firstOrNull()
        val processStrongCandidates = matchingProcesses.mapNotNull { process ->
            val root = process.toManifestCandidateRoot(processRefreshedAt)
            val matched = processUnitManifest.applyToRoots(
                roots = listOf(root),
                pidFileReader = pidFileReader
            ).single()
            val strong = isStrongManifestRootCandidate(
                process = process,
                processUnitManifest = processUnitManifest,
                pidFileReader = pidFileReader,
                processRefreshedAt = processRefreshedAt
            )
            if (strong) {
                matched
            } else {
                null
            }
        }
        val matchingTelemetryEntries = prootTelemetry.processLiveTable.entries
            .filter { entry ->
                entry.state == ProotLiveProcessState.RUNNING &&
                    entry.commandIdentity() == RuntimeControlledLeaseProbeRegistration.COMMAND
            }
        val visibleTelemetryEntry = matchingTelemetryEntries
            .maxWithOrNull(
                compareBy<ProotLiveProcessEntry> { it.lastSeenAtMs }
                    .thenBy { it.traceePid }
            )
        val telemetryStrongCandidates = matchingTelemetryEntries.mapNotNull { entry ->
            val root = entry.toManifestCandidateRoot(processRefreshedAt) ?: return@mapNotNull null
            val matched = processUnitManifest.applyToRoots(
                roots = listOf(root),
                pidFileReader = pidFileReader
            ).single()
            if (isStrongManifestRootCandidate(
                    root = root,
                    processUnitManifest = processUnitManifest,
                    pidFileReader = pidFileReader,
                    requireIndependentProcessIdentity = true
                )
            ) {
                matched
            } else {
                null
            }
        }
        val strongCandidates = processStrongCandidates + telemetryStrongCandidates
        val firstStrong = strongCandidates.firstOrNull()
        val rejectedReason = when {
            firstStrong != null -> "none"
            visibleProcess == null && visibleTelemetryEntry == null ->
                "controlled_probe_command_not_in_mainline_fact_sources"
            visibleProcess != null &&
                (
                    visibleProcess.processGroupId != visibleProcess.pid ||
                        visibleProcess.sessionId != visibleProcess.pid
                    ) ->
                "exact_command_candidate_requires_pid_eq_pgid_eq_sid"
            else -> "manifest_strong_match_not_found"
        }
        return RuntimeControlledProbeProcessVisibility(
            processSeen = visibleProcess != null || visibleTelemetryEntry != null,
            pid = visibleProcess?.pid ?: visibleTelemetryEntry?.traceePid,
            parentPid = visibleProcess?.parentPid ?: visibleTelemetryEntry?.parentTraceePid,
            processGroupId = visibleProcess?.processGroupId ?: visibleTelemetryEntry?.processGroupId,
            sessionId = visibleProcess?.sessionId ?: visibleTelemetryEntry?.sessionId,
            state = visibleProcess?.rawState ?: visibleTelemetryEntry?.state?.name ?: "none",
            sourceLabel = visibleProcess?.sourceLabel ?: visibleTelemetryEntry?.let { "proot_telemetry_live_table" } ?: "none",
            linkedTerminalSessionIdPresent = visibleProcess?.linkedTerminalSessionId != null,
            linkedRuntimeIdPresent = visibleProcess?.linkedRuntimeId != null ||
                !visibleTelemetryEntry?.kfRuntimeId.isNullOrBlank(),
            strongCandidateCount = strongCandidates.size,
            firstStrongCandidatePid = firstStrong?.observedPid,
            firstStrongCandidateUnitId = firstStrong?.processUnitId,
            firstStrongCandidateMatchSource = firstStrong?.processUnitMatchSource
                ?: RuntimeProcessUnitMatchSource.NONE,
            firstStrongCandidateRejectedReason = rejectedReason
        )
    }

    private fun buildProotTelemetryStrongManifestRoots(
        prootTelemetry: ProotTelemetrySnapshot,
        excludedRootPids: Set<Int>,
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader,
        processRefreshedAt: Long
    ): List<RuntimeRootSnapshot> {
        return prootTelemetry.processLiveTable.entries
            .filter { entry ->
                entry.state == ProotLiveProcessState.RUNNING &&
                    entry.traceePid !in excludedRootPids
            }
            .mapNotNull { entry ->
                val root = entry.toManifestCandidateRoot(processRefreshedAt) ?: return@mapNotNull null
                if (isStrongManifestRootCandidate(
                        root = root,
                        processUnitManifest = processUnitManifest,
                        pidFileReader = pidFileReader,
                        requireIndependentProcessIdentity = true
                    )
                ) {
                    root
                } else {
                    null
                }
            }
    }

    private fun buildProotTelemetryOrdinaryRoots(
        prootTelemetry: ProotTelemetrySnapshot,
        excludedRootPids: Set<Int>,
        processRefreshedAt: Long
    ): List<RuntimeRootSnapshot> {
        return prootTelemetry.processLiveTable.entries
            .filter { entry ->
                entry.state == ProotLiveProcessState.RUNNING &&
                    entry.traceePid > 1 &&
                    entry.traceePid !in excludedRootPids
            }
            .map { entry ->
                entry.toOrdinaryTraceeRoot(processRefreshedAt)
            }
    }

    private fun ContainerProcessRecord.toManifestCandidateRoot(
        processRefreshedAt: Long
    ): RuntimeRootSnapshot {
        return RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.UNATTRIBUTED,
            ownerId = null,
            title = title,
            statusLabel = stateLabel,
            observedPid = pid,
            parentPid = parentPid,
            rootProcessGroupId = processGroupId,
            rootSessionId = sessionId,
            processCount = 1,
            rssKb = rssKb ?: 0L,
            vmSizeKb = vmSizeKb ?: 0L,
            autoReclaimAllowed = false,
            classificationSource = "process_snapshot:controlled_probe_visibility",
            sourceLabel = sourceLabel,
            commandLine = commandLine,
            observedStatusLabel = stateLabel,
            reality = RuntimeRootReality.OBSERVED,
            lastSeenAt = processRefreshedAt
        )
    }

    private fun ProotLiveProcessEntry.toManifestCandidateRoot(
        processRefreshedAt: Long
    ): RuntimeRootSnapshot? {
        val commandLine = commandIdentity()
        if (commandLine.isBlank()) return null
        val title = executable
            .substringAfterLast('/')
            .ifBlank { commandLine.substringBefore(' ') }
            .ifBlank { "tracee-$traceePid" }
        return RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.UNATTRIBUTED,
            ownerId = null,
            title = title,
            statusLabel = state.name.lowercase(),
            observedPid = traceePid,
            parentPid = parentTraceePid,
            prootPid = prootPid,
            rootProcessGroupId = processGroupId,
            rootSessionId = sessionId,
            processCount = 1,
            autoReclaimAllowed = false,
            classificationSource = "proot_telemetry_live_table:strong_manifest_candidate",
            classificationReason = if (kfRuntimeId.isNotBlank() || kfUnitId.isNotBlank()) {
                "low-cost PRoot lifecycle event carried KF runtime/unit identity as attribution only"
            } else {
                "low-cost PRoot lifecycle event matched a strong manifest unit"
            },
            sourceLabel = "PRoot telemetry live table",
            commandLine = commandLine,
            observedStatusLabel = state.name,
            reality = RuntimeRootReality.OBSERVED,
            lastSeenAt = lastSeenAtMs.takeIf { it > 0L } ?: processRefreshedAt,
            lastStartedAt = createdAtMs
        )
    }

    private fun ProotLiveProcessEntry.toOrdinaryTraceeRoot(
        processRefreshedAt: Long
    ): RuntimeRootSnapshot {
        val commandLine = commandIdentity()
        val title = executable
            .substringAfterLast('/')
            .ifBlank { commandLine.substringBefore(' ') }
            .ifBlank { "tracee-$traceePid" }
        val skeleton = containerSkeletonProtection(commandLine, title)
        return RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.UNATTRIBUTED,
            ownerId = null,
            title = title,
            statusLabel = state.name.lowercase(),
            observedPid = traceePid,
            parentPid = parentTraceePid,
            prootPid = prootPid,
            rootProcessGroupId = processGroupId,
            rootSessionId = sessionId,
            processCount = 1,
            autoReclaimAllowed = false,
            retentionClass = skeleton?.retentionClass ?: RuntimeRetentionClass.EPHEMERAL,
            resident = skeleton != null,
            reclaimPriority = skeleton?.retentionClass?.reclaimPriority ?: RuntimeRetentionClass.EPHEMERAL.reclaimPriority,
            classificationSource = skeleton?.source ?: "proot_telemetry_live_table:ordinary_tracee",
            classificationReason = skeleton?.reason ?: "ordinary PRoot tracee becomes an individual lease ledger entry",
            sourceLabel = "PRoot telemetry live table",
            commandLine = commandLine,
            observedStatusLabel = state.name,
            reality = RuntimeRootReality.OBSERVED,
            lastSeenAt = lastSeenAtMs.takeIf { it > 0L } ?: processRefreshedAt,
            lastStartedAt = createdAtMs,
            processUnitId = skeleton?.unitId(traceePid),
            processUnitDisplayName = skeleton?.displayName,
            processUnitTier = skeleton?.tier,
            processUnitSource = skeleton?.source,
            processUnitManualKillPolicy = skeleton?.manualKillPolicy,
            processUnitUserEditable = false,
            processUnitAllowReclaim = false,
            processUnitAllowKill = false,
            processUnitAllowRestart = false,
            processUnitReason = skeleton?.reason,
            processUnitMatchSource = if (skeleton != null) {
                RuntimeProcessUnitMatchSource.BUILT_IN_CORE
            } else {
                RuntimeProcessUnitMatchSource.NONE
            },
            processUnitMatchConfidence = if (skeleton != null) {
                RuntimeProcessUnitMatchConfidence.EXACT
            } else {
                RuntimeProcessUnitMatchConfidence.NONE
            },
            processUnitMatchState = if (skeleton != null) {
                RuntimeProcessUnitMatchState.MATCHED_EXACT
            } else {
                RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED
            },
            processUnitMatchedPid = skeleton?.let { traceePid }
        )
    }

    private fun ContainerProcessRecord.containerSkeletonProtection(): ContainerSkeletonProtection? {
        return containerSkeletonProtection(
            commandLine = commandLine,
            title = listOf(title, command).firstOrNull { it.isNotBlank() } ?: title
        )
    }

    private fun containerSkeletonProtection(
        commandLine: String,
        title: String
    ): ContainerSkeletonProtection? {
        val executable = title.substringAfterLast('/').lowercase()
        val command = commandLine.lowercase()
        val text = "$executable $command"
        return when {
            executable == "supervisord" ||
                " supervisord " in " $text " ||
                "/usr/bin/supervisord" in text ||
                "/bin/supervisord" in text -> ContainerSkeletonProtection(
                processUnitIdPrefix = "builtin-container-supervisord",
                displayName = "容器骨架",
                tier = RuntimeProcessUnitTier.SYSTEM_CORE,
                retentionClass = RuntimeRetentionClass.CRITICAL_CORE,
                manualKillPolicy = RuntimeProcessUnitManualKillPolicy.CORE_RECOVER,
                source = "builtin:container_skeleton",
                reason = "container supervisor is system skeleton and never enters the ordinary lease pool"
            )
            "/runtime/bin/proot" in text ||
                "link2symlink" in text -> ContainerSkeletonProtection(
                processUnitIdPrefix = "builtin-proot-entry",
                displayName = "PRoot 容器入口",
                tier = RuntimeProcessUnitTier.PROOT_CORE,
                retentionClass = RuntimeRetentionClass.CRITICAL_CORE,
                manualKillPolicy = RuntimeProcessUnitManualKillPolicy.CORE_RECOVER,
                source = "builtin:proot_core",
                reason = "PRoot entry owns the Ubuntu filesystem boundary and is shown as a system support process"
            )
            executable == "hermes" ||
                executable == "hermes-gateway" ||
                command.startsWith("/workspace/.kf/hermes-gateway") ||
                command.startsWith(".kf/hermes-gateway") ||
                command.startsWith("hermes-gateway ") ||
                command == "hermes-gateway" -> ContainerSkeletonProtection(
                processUnitIdPrefix = "builtin-hermes-gateway",
                displayName = "Hermes 守护入口",
                tier = RuntimeProcessUnitTier.USER_LOCKED,
                retentionClass = RuntimeRetentionClass.RESIDENT,
                manualKillPolicy = RuntimeProcessUnitManualKillPolicy.WAIT_CONFIRM,
                source = "builtin:container_daemon",
                reason = "daemon root is pinned independently; descendants remain separate lease roots"
            )
            else -> null
        }
    }

    internal fun buildUnattributedRootsForTesting(
        processes: List<ContainerProcessRecord>,
        prootTelemetry: ProotTelemetrySnapshot = ProotTelemetrySnapshot(),
        attributedRootPids: Set<Int> = emptySet(),
        attributedRuntimeRootPids: Set<Int> = emptySet(),
        reclaimerPolicy: RuntimeReclaimerPolicy = RuntimeReclaimerPolicy.default(),
        processUnitManifest: RuntimeProcessUnitManifest = RuntimeProcessUnitManifest.default(),
        pidFileReader: RuntimeProcessUnitPidFileReader = RuntimeProcessUnitPidFileReader.noop(),
        processRefreshedAt: Long = System.currentTimeMillis()
    ): List<RuntimeRootSnapshot> {
        return buildUnattributedRoots(
            processes = processes,
            prootTelemetry = prootTelemetry,
            attributedRootPids = attributedRootPids,
            attributedRuntimeRootPids = attributedRuntimeRootPids,
            reclaimerPolicy = reclaimerPolicy,
            processUnitManifest = processUnitManifest,
            pidFileReader = pidFileReader,
            processRefreshedAt = processRefreshedAt
        )
    }

    internal fun buildControlledProbeProcessVisibilityForTesting(
        processes: List<ContainerProcessRecord>,
        prootTelemetry: ProotTelemetrySnapshot = ProotTelemetrySnapshot(),
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader = RuntimeProcessUnitPidFileReader.noop(),
        processRefreshedAt: Long = System.currentTimeMillis()
    ): RuntimeControlledProbeProcessVisibility {
        return buildControlledProbeProcessVisibility(
            processes = processes,
            prootTelemetry = prootTelemetry,
            processUnitManifest = processUnitManifest,
            pidFileReader = pidFileReader,
            processRefreshedAt = processRefreshedAt
        )
    }

    internal fun isStrongManifestRootCandidateForObservation(
        process: ContainerProcessRecord,
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader = RuntimeProcessUnitPidFileReader.noop(),
        processRefreshedAt: Long = System.currentTimeMillis()
    ): Boolean {
        return isStrongManifestRootCandidate(
            process = process,
            processUnitManifest = processUnitManifest,
            pidFileReader = pidFileReader,
            processRefreshedAt = processRefreshedAt
        )
    }

    private fun isStrongManifestRootCandidate(
        process: ContainerProcessRecord,
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader,
        processRefreshedAt: Long
    ): Boolean {
        val candidate = RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.UNATTRIBUTED,
            ownerId = null,
            title = process.title,
            statusLabel = process.stateLabel,
            observedPid = process.pid,
            rootProcessGroupId = process.processGroupId,
            rootSessionId = process.sessionId,
            processCount = 1,
            rssKb = process.rssKb ?: 0L,
            vmSizeKb = process.vmSizeKb ?: 0L,
            autoReclaimAllowed = false,
            classificationSource = "process_snapshot:candidate_probe",
            sourceLabel = process.sourceLabel,
            commandLine = process.commandLine,
            observedStatusLabel = process.stateLabel,
            reality = RuntimeRootReality.OBSERVED,
            lastSeenAt = processRefreshedAt
        )
        val matched = processUnitManifest.applyToRoots(
            roots = listOf(candidate),
            pidFileReader = pidFileReader
        ).single()
        return isStrongManifestRootCandidate(
            root = matched,
            processUnitManifest = processUnitManifest,
            pidFileReader = pidFileReader,
            requireIndependentProcessIdentity = true
        )
    }

    private fun isStrongManifestRootCandidate(
        root: RuntimeRootSnapshot,
        processUnitManifest: RuntimeProcessUnitManifest,
        pidFileReader: RuntimeProcessUnitPidFileReader,
        requireIndependentProcessIdentity: Boolean
    ): Boolean {
        val matched = if (root.processUnitMatchSource == RuntimeProcessUnitMatchSource.NONE) {
            processUnitManifest.applyToRoots(
                roots = listOf(root),
                pidFileReader = pidFileReader
            ).single()
        } else {
            root
        }
        return matched.processUnitTier == RuntimeProcessUnitTier.LEASE &&
            matched.processUnitMatchState != RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS &&
            when (matched.processUnitMatchSource) {
                RuntimeProcessUnitMatchSource.COMMAND_EXACT ->
                    !requireIndependentProcessIdentity ||
                        (
                            matched.rootProcessGroupId == matched.observedPid &&
                                matched.rootSessionId == matched.observedPid
                            )
                RuntimeProcessUnitMatchSource.PID_FILE,
                RuntimeProcessUnitMatchSource.PROCESS_GROUP,
                RuntimeProcessUnitMatchSource.RUNTIME_ID -> true
                else -> false
            }
    }

    private fun ProotLiveProcessEntry.commandIdentity(): String {
        return argvPreview.ifBlank { executable }.trim()
    }

    private fun isDescendantOf(
        process: ContainerProcessRecord,
        rootPid: Int,
        processes: List<ContainerProcessRecord>
    ): Boolean {
        val processMap = processes.associateBy { it.pid }
        var parentPid = process.parentPid
        var guard = 0
        while (parentPid > 1 && guard < 64) {
            if (parentPid == rootPid) return true
            parentPid = processMap[parentPid]?.parentPid ?: break
            guard += 1
        }
        return false
    }

    private fun buildStaleReason(expectedPid: Int?, statusLabel: String): String {
        return if (expectedPid != null) {
            "持久化状态为 $statusLabel，但进程采样未发现 root pid $expectedPid"
        } else {
            "持久化状态为 $statusLabel，但没有可校验 root pid"
        }
    }

    private fun resolveReality(
        rootProcess: ContainerProcessRecord?,
        processRefreshedAt: Long
    ): RuntimeRootReality {
        return when {
            rootProcess != null -> RuntimeRootReality.OBSERVED
            processRefreshedAt <= 0L -> RuntimeRootReality.UNKNOWN
            else -> RuntimeRootReality.STALE_RECORD
        }
    }

    private fun Context.resolveContainerRuntimeMetadata(): ContainerRuntimeMetadata {
        val container = WorkSurfaceRuntimeBridge.getSavedContainer(this)
        return ContainerRuntimeMetadata(
            containerId = container?.id,
            legacyContainerPid = container?.pid?.takeIf { it > 0 },
            rootfsPath = container?.rootfsPath,
            workspacePath = container?.workspacePath,
            networkMode = container?.networkMode,
            networkSemantics = container?.networkMode?.toRuntimeNetworkSemantics(),
            lastError = container?.lastError
        )
    }

    private data class ContainerRuntimeMetadata(
        val containerId: String? = null,
        val legacyContainerPid: Int? = null,
        val rootfsPath: String? = null,
        val workspacePath: String? = null,
        val networkMode: NetworkMode? = null,
        val networkSemantics: RuntimeNetworkSemantics? = null,
        val lastError: String? = null
    )

    private data class RuntimeRootMemorySummary(
        val rssKb: Long,
        val vmSizeKb: Long,
        val maxOomScoreAdj: Int?,
        val cpuTimeTicks: Long,
        val ioReadBytes: Long,
        val ioWriteBytes: Long
    )

    private data class RuntimeProcessResourceSample(
        val rssKb: Long = 0L,
        val vmSizeKb: Long = 0L,
        val cpuTimeTicks: Long = 0L
    )

    private fun readRuntimeProcessTableResourceSamples(
        tableFile: File
    ): Map<Int, RuntimeProcessResourceSample> {
        if (!tableFile.isFile) return emptyMap()
        return runCatching {
            val lines = tableFile.readLines()
            if (lines.size < 2) return@runCatching emptyMap()
            val headers = lines.first().split('\t')
            val pidIndex = headers.indexOf("pid")
            val rssIndex = headers.indexOf("rss_kb")
            val vmIndex = headers.indexOf("vm_size_kb")
            val cpuIndex = headers.indexOf("cpu_time_ticks")
            if (pidIndex < 0 || rssIndex < 0 || vmIndex < 0) return@runCatching emptyMap()
            lines.drop(1).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val fields = line.split('\t')
                val pid = fields.getOrNull(pidIndex)?.toIntOrNull() ?: return@mapNotNull null
                val rssKb = fields.getOrNull(rssIndex)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val vmSizeKb = fields.getOrNull(vmIndex)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val cpuTimeTicks = fields.getOrNull(cpuIndex)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                if (rssKb <= 0L && vmSizeKb <= 0L && cpuTimeTicks <= 0L) return@mapNotNull null
                pid to RuntimeProcessResourceSample(
                    rssKb = rssKb,
                    vmSizeKb = vmSizeKb,
                    cpuTimeTicks = cpuTimeTicks
                )
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun List<RuntimeRootSnapshot>.withRuntimeProcessResourceSamples(
        resourceSamples: Map<Int, RuntimeProcessResourceSample>
    ): List<RuntimeRootSnapshot> {
        if (resourceSamples.isEmpty()) return this
        return map { root ->
            val pid = root.observedPid ?: root.rootPid ?: return@map root
            val sample = resourceSamples[pid] ?: return@map root
            root.copy(
                rssKb = root.rssKb.takeIf { it > 0L } ?: sample.rssKb,
                vmSizeKb = root.vmSizeKb.takeIf { it > 0L } ?: sample.vmSizeKb,
                cpuTimeTicks = root.cpuTimeTicks.takeIf { it > 0L } ?: sample.cpuTimeTicks
            )
        }
    }

    private fun ContainerProcessResourceSnapshot.withRuntimeProcessResourceSamples(
        resourceSamples: Map<Int, RuntimeProcessResourceSample>
    ): ContainerProcessResourceSnapshot {
        if (resourceSamples.isEmpty()) return this
        val sampledRssKb = resourceSamples.values.sumOf { it.rssKb.coerceAtLeast(0L) }
        val sampledVmSizeKb = resourceSamples.values.sumOf { it.vmSizeKb.coerceAtLeast(0L) }
        val sampledCpuTimeTicks = resourceSamples.values.sumOf { it.cpuTimeTicks.coerceAtLeast(0L) }
        val sampledSource = "runtime_process_table_sampler"
        val mergedSource = when {
            source.isBlank() || source == "none" -> sampledSource
            source.contains(sampledSource) -> source
            else -> "${source}+$sampledSource"
        }
        return copy(
            source = mergedSource,
            processCount = maxOf(processCount, resourceSamples.size),
            rssKb = maxOf(rssKb, sampledRssKb),
            vmSizeKb = maxOf(vmSizeKb, sampledVmSizeKb),
            cpuTimeTicks = maxOf(cpuTimeTicks, sampledCpuTimeTicks)
        )
    }

    private fun RuntimeHealthSnapshot.toUbuntuProcessTableText(
        resourceSamples: Map<Int, RuntimeProcessResourceSample> = emptyMap()
    ): String {
        val builder = StringBuilder()
        builder.appendLine(
            listOf(
                "pid",
                "ppid",
                "pgid",
                "sid",
                "stat",
                "rss_kb",
                "vm_size_kb",
                "cpu_time_ticks",
                "comm",
                "args",
                "source",
                "proot_pid",
                "tracee_vpid",
                "runtime_id",
                "unit_id",
                "started_at_ms",
                "last_seen_ms",
                "state",
                "last_event_type",
                "exit_code",
                "signal",
                "exited_at_ms",
                "signaled_at_ms"
            ).joinToString("\t")
        )
        val resourceByPid = roots
            .mapNotNull { root ->
                root.observedPid?.let { pid ->
                    pid to RuntimeProcessResourceSample(
                        rssKb = root.rssKb,
                        vmSizeKb = root.vmSizeKb,
                        cpuTimeTicks = root.cpuTimeTicks
                    )
                }
            }
            .toMap()
        prootTelemetry.processLiveTable.entries
            .sortedWith(compareBy<ProotLiveProcessEntry> { it.state != ProotLiveProcessState.RUNNING }
                .thenBy { it.traceePid })
            .forEach { entry ->
                val comm = entry.commandNameForUbuntuSurface()
                val args = entry.argvPreview
                    .ifBlank { entry.executable }
                    .ifBlank { comm }
                val rootResource = resourceByPid[entry.traceePid]
                val sampledResource = resourceSamples[entry.traceePid]
                val rssKb = rootResource?.rssKb?.takeIf { it > 0L } ?: sampledResource?.rssKb ?: 0L
                val vmSizeKb = rootResource?.vmSizeKb?.takeIf { it > 0L } ?: sampledResource?.vmSizeKb ?: 0L
                val cpuTimeTicks = rootResource?.cpuTimeTicks?.takeIf { it > 0L } ?: sampledResource?.cpuTimeTicks ?: 0L
                builder.appendLine(
                    listOf(
                        entry.traceePid.toString(),
                        (entry.parentTraceePid ?: 0).toString(),
                        (entry.processGroupId ?: entry.traceePid).toString(),
                        (entry.sessionId ?: 0).toString(),
                        entry.state.toUbuntuStat(),
                        rssKb.toString(),
                        vmSizeKb.toString(),
                        cpuTimeTicks.toString(),
                        comm,
                        args,
                        "proot_telemetry_live_table",
                        entry.prootPid.toString(),
                        entry.traceeVpid.toString(),
                        entry.kfRuntimeId,
                        entry.kfUnitId,
                        entry.createdAtMs.toString(),
                        entry.lastSeenAtMs.toString(),
                        entry.state.name,
                        entry.lastEventType.name,
                        (entry.exitCode ?: -1).toString(),
                        (entry.signal ?: 0).toString(),
                        (entry.exitedAtMs ?: 0L).toString(),
                        (entry.signaledAtMs ?: 0L).toString()
                    ).joinToString("\t") { value -> value.toRuntimeProcessTableValue() }
                )
            }
        return builder.toString()
    }

    private fun RuntimeHealthSnapshot.toProotTelemetryEventsText(): String {
        val builder = StringBuilder()
        builder.appendLine(
            listOf(
                "timestamp_ms",
                "event_type",
                "proot_pid",
                "tracee_pid",
                "tracee_vpid",
                "parent_tracee_pid",
                "parent_tracee_vpid",
                "process_group_id",
                "session_id",
                "source_hook",
                "cost_level",
                "executable",
                "argv_hash",
                "argv_preview",
                "cwd",
                "kf_runtime_id",
                "kf_unit_id",
                "exit_code",
                "signal"
            ).joinToString("\t")
        )
        prootTelemetry.recentEvents
            .takeLast(PROOT_TELEMETRY_EVENT_SURFACE_LIMIT)
            .forEach { event ->
                builder.appendLine(
                    listOf(
                        event.timestampMs.toString(),
                        event.eventType.name,
                        event.prootPid.toString(),
                        event.traceePid.toString(),
                        event.traceeVpid.toString(),
                        (event.parentTraceePid ?: 0).toString(),
                        (event.parentTraceeVpid ?: 0L).toString(),
                        (event.processGroupId ?: 0).toString(),
                        (event.sessionId ?: 0).toString(),
                        event.sourceHook,
                        event.costLevel,
                        event.executable,
                        event.argvHash,
                        event.argvPreview,
                        event.cwd,
                        event.kfRuntimeId,
                        event.kfUnitId,
                        (event.exitCode ?: -1).toString(),
                        (event.signal ?: 0).toString()
                    ).joinToString("\t") { value -> value.toRuntimeProcessTableValue() }
                )
            }
        return builder.toString()
    }

    private fun RuntimeHealthSnapshot.toUbuntuProcProjectionFiles(
        resourceSamples: Map<Int, RuntimeProcessResourceSample> = emptyMap()
    ): Map<String, String> {
        val resourceByPid = roots
            .mapNotNull { root ->
                root.observedPid?.let { pid ->
                    pid to RuntimeProcessResourceSample(
                        rssKb = root.rssKb,
                        vmSizeKb = root.vmSizeKb,
                        cpuTimeTicks = root.cpuTimeTicks
                    )
                }
            }
            .toMap()
        val files = linkedMapOf<String, String>()
        val nowMs = System.currentTimeMillis()
        selectProcfsProjectionEntries(nowMs).forEach { entry ->
            val pid = entry.traceePid
            if (pid <= 0) return@forEach
            val comm = entry.commandNameForUbuntuSurface().take(64).ifBlank { "tracee-$pid" }
            val args = entry.argvPreview.ifBlank { entry.executable }.ifBlank { comm }
            val rootResource = resourceByPid[pid]
            val sampledResource = resourceSamples[pid]
            val rssKb = rootResource?.rssKb?.takeIf { it > 0L } ?: sampledResource?.rssKb ?: 0L
            val vmSizeKb = rootResource?.vmSizeKb?.takeIf { it > 0L } ?: sampledResource?.vmSizeKb ?: 0L
            val cpuTimeTicks = rootResource?.cpuTimeTicks?.takeIf { it > 0L } ?: sampledResource?.cpuTimeTicks ?: 0L
            val rssPages = (rssKb * 1024L / 4096L).coerceAtLeast(0L)
            val vmPages = (vmSizeKb * 1024L / 4096L).coerceAtLeast(0L)
            val parentPid = entry.parentTraceePid ?: 0
            val pgid = entry.processGroupId ?: pid
            val sid = entry.sessionId ?: 0
            val state = entry.state.toUbuntuStat().ifBlank { "?" }
            val startTicks = (entry.createdAtMs / 10L).coerceAtLeast(0L)
            val vmBytes = vmSizeKb * 1024L
            val stat = listOf(
                pid.toString(),
                "(${comm.replace(')', '_')})",
                state,
                parentPid.toString(),
                pgid.toString(),
                sid.toString(),
                "0",
                "-1",
                cpuTimeTicks.toString(),
                "0",
                "0",
                "0",
                "0",
                "0",
                "0",
                "0",
                "0",
                "20",
                "0",
                "1",
                "0",
                startTicks.toString(),
                vmBytes.toString(),
                rssPages.toString()
            ).joinToString(" ") + "\n"
            files["$pid/stat"] = stat
            files["$pid/status"] = buildString {
                appendLine("Name:\t$comm")
                appendLine("Umask:\t0022")
                appendLine("State:\t${state.toUbuntuStatusState()}")
                appendLine("Tgid:\t$pid")
                appendLine("Ngid:\t0")
                appendLine("Pid:\t$pid")
                appendLine("PPid:\t$parentPid")
                appendLine("TracerPid:\t${entry.prootPid}")
                appendLine("Uid:\t0\t0\t0\t0")
                appendLine("Gid:\t0\t0\t0\t0")
                appendLine("Groups:\t")
                appendLine("NStgid:\t$pid")
                appendLine("NSpid:\t$pid")
                appendLine("NSpgid:\t$pgid")
                appendLine("NSsid:\t$sid")
                appendLine("VmSize:\t$vmSizeKb kB")
                appendLine("VmRSS:\t$rssKb kB")
                appendLine("KfSource:\tproot_telemetry_live_table")
                appendLine("KfRuntimeId:\t${entry.kfRuntimeId.ifBlank { "-" }}")
                appendLine("KfUnitId:\t${entry.kfUnitId.ifBlank { "-" }}")
                appendLine("KfLastEvent:\t${entry.lastEventType.name}")
                appendLine("KfExitCode:\t${entry.exitCode ?: -1}")
                appendLine("KfSignal:\t${entry.signal ?: 0}")
            }
            files["$pid/cmdline"] = args.toProcCmdline()
            files["$pid/comm"] = "$comm\n"
            files["$pid/statm"] = listOf(
                vmPages,
                rssPages,
                0L,
                0L,
                0L,
                0L,
                0L
            ).joinToString(" ") + "\n"
            files["$pid/cwd"] = entry.cwd.ifBlank { "/" } + "\n"
            files["$pid/exe"] = entry.executable.ifBlank { comm } + "\n"
        }
        return files
    }

    private fun RuntimeHealthSnapshot.selectProcfsProjectionEntries(nowMs: Long): List<ProotLiveProcessEntry> {
        val entries = prootTelemetry.processLiveTable.entries
        if (entries.isEmpty()) return emptyList()
        val running = entries
            .filter { it.state == ProotLiveProcessState.RUNNING }
            .sortedBy { it.traceePid }
        val recentTerminal = entries
            .asSequence()
            .filter { it.state == ProotLiveProcessState.EXITED || it.state == ProotLiveProcessState.SIGNALED }
            .filter { entry ->
                val terminalAt = entry.exitedAtMs ?: entry.signaledAtMs ?: entry.lastSeenAtMs
                terminalAt > 0L && nowMs - terminalAt <= PROCFS_PROJECTION_RECENT_TERMINAL_TTL_MS
            }
            .sortedWith(compareByDescending<ProotLiveProcessEntry> { it.exitedAtMs ?: it.signaledAtMs ?: it.lastSeenAtMs }
                .thenBy { it.traceePid })
            .toList()
        return (running + recentTerminal)
            .distinctBy { it.traceePid }
            .take(PROCFS_PROJECTION_MAX_ENTRIES)
    }

    private fun ProotLiveProcessEntry.commandNameForUbuntuSurface(): String {
        return executable
            .substringAfterLast('/')
            .ifBlank { argvPreview.trim().substringBefore(' ') }
            .ifBlank { "tracee-$traceePid" }
    }

    private fun ProotLiveProcessState.toUbuntuStat(): String {
        return when (this) {
            ProotLiveProcessState.RUNNING -> "S"
            ProotLiveProcessState.EXITED -> "X"
            ProotLiveProcessState.SIGNALED -> "X"
            ProotLiveProcessState.UNKNOWN -> "?"
        }
    }

    private fun String.toUbuntuStatusState(): String {
        return when (this) {
            "S" -> "S (sleeping)"
            "R" -> "R (running)"
            "X" -> "X (dead)"
            "Z" -> "Z (zombie)"
            else -> "? (unknown)"
        }
    }

    private fun String.toProcCmdline(): String {
        val normalized = trim()
        if (normalized.isBlank()) return "\u0000"
        return normalized
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(separator = "\u0000", postfix = "\u0000")
    }

    private fun String.toRuntimeProcessTableValue(): String {
        return replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }

    private fun RuntimeHealthSnapshot.toEnvText(): String {
        val builder = StringBuilder()
        val nowMs = System.currentTimeMillis()
        val procfsProjectionEntries = selectProcfsProjectionEntries(nowMs)
        val procfsProjectionRunningCount = procfsProjectionEntries.count {
            it.state == ProotLiveProcessState.RUNNING
        }
        val procfsProjectionRecentTerminalCount = procfsProjectionEntries.size - procfsProjectionRunningCount
        val recentExit = prootTelemetry.processLiveTable.entries
            .filter { it.state == ProotLiveProcessState.EXITED }
            .maxByOrNull { it.exitedAtMs ?: it.lastSeenAtMs }
        val recentSignal = prootTelemetry.processLiveTable.entries
            .filter { it.state == ProotLiveProcessState.SIGNALED }
            .maxByOrNull { it.signaledAtMs ?: it.lastSeenAtMs }
        builder.appendLine("runtime_health_output_profile=mainline_default")
        builder.appendLine("runtime_health_mainline_flow=monitoring_judgment_android_kf_execution_boundary")
        builder.appendLine("runtime_health_mainline_monitoring_sources=pressure_proot_process_resource_workload_manifest_authority_lease_proot_capacity")
        builder.appendLine("runtime_health_mainline_judgment_source=runtime_memory_lifecycle_rule_trigger")
        builder.appendLine("runtime_health_mainline_execution_boundaries=runtime_reclaimer_and_proot_capacity_actuator")
        builder.appendLine("ubuntu_process_surface_source=proot_telemetry_live_table")
        builder.appendLine("ubuntu_process_surface_path=${WorkspaceBuildSupport.CONTAINER_RUNTIME_PROCESS_TABLE_PATH}")
        builder.appendLine("ubuntu_process_surface_mode=native_system_applet")
        builder.appendLine("ubuntu_process_surface_commands=ps_pgrep_pkill_kill_pidof_pstree_free_top_systemctl_service_kf_resource_sampler")
        builder.appendLine("ubuntu_process_surface_signal_semantics=standard_unix_signal_no_kf_reclaim")
        builder.appendLine("ubuntu_resource_sampler_command=${WorkspaceBuildSupport.CONTAINER_RUNTIME_RESOURCE_SAMPLER_COMMAND}")
        builder.appendLine("ubuntu_resource_sampler_mode=internal_native_running_pid_list_no_proc_scan")
        builder.appendLine("ubuntu_resource_sampler_standard_command_triggers=ps_aux,top")
        builder.appendLine("ubuntu_resource_sampler_standard_command_cache_ttl_ms=2000")
        builder.appendLine("ubuntu_resource_sampler_read_trigger_bucket_capacity=4")
        builder.appendLine("ubuntu_resource_sampler_read_trigger_refill_interval_ms=2000")
        builder.appendLine("ubuntu_resource_sampler_read_trigger_advice_cache_hit_threshold=5")
        builder.appendLine("ubuntu_resource_sampler_read_trigger_advice_cooldown_ms=30000")
        builder.appendLine("ubuntu_resource_sampler_force_min_interval_ms=250")
        builder.appendLine("ubuntu_resource_sampler_high_frequency_entry=kf-resource-sampler --watch --interval-ms 500")
        builder.appendLine("ubuntu_resource_sampler_high_frequency_hint=standard_commands_reuse_cache_use_kf_resource_sampler_for_explicit_curves")
        builder.appendLine("ubuntu_resource_sampler_target_table=${WorkspaceBuildSupport.CONTAINER_RUNTIME_PROCESS_TABLE_PATH}")
        builder.appendLine("ubuntu_resource_sampler_procfs_projection_bypass=stat_statm_for_resource_view_commands")
        builder.appendLine("ubuntu_procfs_projection_source=proot_telemetry_live_table")
        builder.appendLine("ubuntu_procfs_projection_root=${WorkspaceBuildSupport.CONTAINER_HELPER_SYSTEM_PROC_PATH}")
        builder.appendLine("ubuntu_procfs_projection_mode=proot_path_projection")
        builder.appendLine("ubuntu_procfs_projection_files=stat_status_cmdline_comm_statm_cwd_exe_text")
        builder.appendLine("ubuntu_procfs_projection_argv_scope=argv_preview_not_full_argv")
        builder.appendLine("ubuntu_procfs_projection_default_scope=running_plus_recent_terminal")
        builder.appendLine("ubuntu_procfs_projection_retention_mode=$PROCFS_PROJECTION_RETENTION_MODE")
        builder.appendLine("ubuntu_procfs_projection_cleanup_mode=$PROCFS_PROJECTION_CLEANUP_MODE")
        builder.appendLine("ubuntu_procfs_projection_entry_cap=$PROCFS_PROJECTION_MAX_ENTRIES")
        builder.appendLine("ubuntu_procfs_projection_recent_terminal_ttl_ms=$PROCFS_PROJECTION_RECENT_TERMINAL_TTL_MS")
        builder.appendLine("ubuntu_procfs_projection_entry_count=${procfsProjectionEntries.size}")
        builder.appendLine("ubuntu_procfs_projection_running_entry_count=$procfsProjectionRunningCount")
        builder.appendLine("ubuntu_procfs_projection_recent_terminal_entry_count=$procfsProjectionRecentTerminalCount")
        builder.appendLine("ubuntu_procfs_projection_total_tracee_count=${prootTelemetry.processLiveTable.entries.size}")
        builder.appendLine("proot_telemetry_events_surface_path=${WorkspaceBuildSupport.CONTAINER_PROOT_TELEMETRY_EVENTS_PATH}")
        builder.appendLine("proot_telemetry_events_surface_mode=bounded_recent_events_from_kf_reader")
        builder.appendLine("proot_telemetry_events_surface_limit=$PROOT_TELEMETRY_EVENT_SURFACE_LIMIT")
        builder.appendLine("proot_telemetry_events_surface_count=${prootTelemetry.recentEvents.takeLast(PROOT_TELEMETRY_EVENT_SURFACE_LIMIT).size}")
        builder.appendLine("proot_telemetry_raw_jsonl_direct_ubuntu_contract=not_stable_use_events_surface")
        builder.appendLine("runtime_health_legacy_observe_only_detail_mode=summarized")
        builder.append(pressure.toEnvText())
        builder.appendLine("proot_telemetry_mode=${prootTelemetry.mode.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_launch_requested_mode=${prootTelemetryLaunchState.requestedMode.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_runtime_descriptor_mode=${prootTelemetryLaunchState.runtimeDescriptorMode.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_runtime_asset_id=${prootTelemetryLaunchState.runtimeAssetId.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_runtime_active_id=${prootTelemetryLaunchState.runtimeActiveRuntimeId.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_runtime_provider=${prootTelemetryLaunchState.runtimeProvider.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_substrate_state=${prootTelemetryLaunchState.substrateState.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_substrate_reason=${prootTelemetryLaunchState.substrateReason.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_capable_candidate_runtime_id=${prootTelemetryLaunchState.telemetryCapableCandidateRuntimeId.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_capable_candidate_validation_state=${prootTelemetryLaunchState.telemetryCapableCandidateValidationState.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_runtime_descriptor_path=${prootTelemetryLaunchState.runtimeDescriptorPath.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_runtime_supports_requested=${prootTelemetryLaunchState.supportsRequestedTelemetry}")
        builder.appendLine("proot_telemetry_runtime_status=${prootTelemetryLaunchState.status.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_status=${prootTelemetry.collectionStatus.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_path=${prootTelemetry.sourcePath.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_file_exists=${prootTelemetry.fileExists}")
        builder.appendLine("proot_telemetry_file_size_bytes=${prootTelemetry.fileSizeBytes}")
        builder.appendLine("proot_telemetry_file_last_modified_at=${prootTelemetry.fileLastModifiedMs}")
        builder.appendLine("proot_telemetry_last_read_offset_bytes=${prootTelemetry.lastReadOffsetBytes}")
        builder.appendLine("proot_telemetry_refreshed_at=${prootTelemetry.refreshedAtMs}")
        builder.appendLine("proot_telemetry_events_total=${prootTelemetry.counters.totalEvents}")
        builder.appendLine("proot_telemetry_events_last_refresh=${prootTelemetry.lastRefreshEvents}")
        builder.appendLine("proot_telemetry_fork_exec_last_refresh=${prootTelemetry.lastRefreshForkExecEvents}")
        builder.appendLine("proot_telemetry_live_tracees=${prootTelemetry.liveTraceeCount}")
        builder.appendLine("proot_telemetry_known_tracees=${prootTelemetry.knownTraceeCount}")
        builder.appendLine("proot_telemetry_parse_errors=${prootTelemetry.counters.parseErrors}")
        builder.appendLine("proot_telemetry_skipped_bytes=${prootTelemetry.counters.skippedBytes}")
        builder.appendLine("proot_telemetry_last_event_at=${prootTelemetry.lastEventAtMs}")
        val lastProotEvent = prootTelemetry.recentEvents.lastOrNull()
        builder.appendLine("proot_telemetry_last_event_type=${lastProotEvent?.eventType?.name.toRuntimeEnvValue()}")
        builder.appendLine("proot_telemetry_last_event_tracee_pid=${lastProotEvent?.traceePid ?: 0}")
        builder.appendLine("proot_telemetry_last_event_proot_pid=${lastProotEvent?.prootPid ?: 0}")
        builder.appendLine("proot_telemetry_last_event_source=${lastProotEvent?.sourceHook.toRuntimeEnvValue()}")
        builder.appendLine("proot_live_table_mode=${prootTelemetry.processLiveTable.mode.toRuntimeEnvValue()}")
        builder.appendLine("proot_live_table_status=${prootTelemetry.processLiveTable.sourceStatus.toRuntimeEnvValue()}")
        builder.appendLine("proot_live_table_retention_mode=${prootTelemetry.processLiveTable.retentionMode.toRuntimeEnvValue()}")
        builder.appendLine("proot_live_table_terminal_retention_max_entries=${prootTelemetry.processLiveTable.terminalRetentionMaxEntries}")
        builder.appendLine("proot_live_table_terminal_retention_ttl_ms=${prootTelemetry.processLiveTable.terminalRetentionTtlMs}")
        builder.appendLine("proot_telemetry_raw_jsonl_retention_mode=size_bounded_rotation_v1")
        builder.appendLine("proot_telemetry_raw_jsonl_rotate_max_bytes=8388608")
        builder.appendLine("proot_telemetry_raw_jsonl_rotate_archive_count=3")
        builder.appendLine("proot_telemetry_raw_jsonl_rotate_check_interval=128")
        builder.appendLine("proot_live_table_live_tracees=${prootTelemetry.processLiveTable.liveTraceeCount}")
        builder.appendLine("proot_live_table_known_tracees=${prootTelemetry.processLiveTable.knownTraceeCount}")
        builder.appendLine("proot_live_table_exited_tracees=${prootTelemetry.processLiveTable.exitedTraceeCount}")
        builder.appendLine("proot_live_table_signaled_tracees=${prootTelemetry.processLiveTable.signaledTraceeCount}")
        builder.appendLine("proot_live_table_entry_visible_limit=$PROOT_LIVE_TABLE_ENV_ENTRY_LIMIT")
        builder.appendLine("proot_live_table_entry_total=${prootTelemetry.processLiveTable.entries.size}")
        builder.appendLine("proot_live_table_recent_exit_pid=${recentExit?.traceePid ?: 0}")
        builder.appendLine("proot_live_table_recent_exit_code=${recentExit?.exitCode ?: -1}")
        builder.appendLine("proot_live_table_recent_exit_at=${recentExit?.exitedAtMs ?: 0L}")
        builder.appendLine("proot_live_table_recent_signal_pid=${recentSignal?.traceePid ?: 0}")
        builder.appendLine("proot_live_table_recent_signal_number=${recentSignal?.signal ?: 0}")
        builder.appendLine("proot_live_table_recent_signal_at=${recentSignal?.signaledAtMs ?: 0L}")
        prootTelemetry.processLiveTable.entries.take(PROOT_LIVE_TABLE_ENV_ENTRY_LIMIT).forEachIndexed { index, entry ->
            val ordinal = index + 1
            builder.appendLine("proot_live_table_entry_${ordinal}_telemetry_session_id=${entry.telemetrySessionId.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_proot_start_ms=${entry.prootStartMs}")
            builder.appendLine("proot_live_table_entry_${ordinal}_proot_pid=${entry.prootPid}")
            builder.appendLine("proot_live_table_entry_${ordinal}_tracee_pid=${entry.traceePid}")
            builder.appendLine("proot_live_table_entry_${ordinal}_tracee_vpid=${entry.traceeVpid}")
            builder.appendLine("proot_live_table_entry_${ordinal}_process_group_id=${entry.processGroupId ?: 0}")
            builder.appendLine("proot_live_table_entry_${ordinal}_session_id=${entry.sessionId ?: 0}")
            builder.appendLine("proot_live_table_entry_${ordinal}_parent_tracee_pid=${entry.parentTraceePid ?: 0}")
            builder.appendLine("proot_live_table_entry_${ordinal}_state=${entry.state.name}")
            builder.appendLine("proot_live_table_entry_${ordinal}_created_at=${entry.createdAtMs}")
            builder.appendLine("proot_live_table_entry_${ordinal}_last_seen_at=${entry.lastSeenAtMs}")
            builder.appendLine("proot_live_table_entry_${ordinal}_last_event_type=${entry.lastEventType.name}")
            builder.appendLine("proot_live_table_entry_${ordinal}_source=${entry.lastSourceHook.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_cost=${entry.lastCostLevel.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_executable=${entry.executable.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_argv_hash=${entry.argvHash.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_argv_preview=${entry.argvPreview.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_kf_runtime_id=${entry.kfRuntimeId.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_kf_unit_id=${entry.kfUnitId.toRuntimeEnvValue()}")
            builder.appendLine("proot_live_table_entry_${ordinal}_exit_code=${entry.exitCode ?: -1}")
            builder.appendLine("proot_live_table_entry_${ordinal}_signal=${entry.signal ?: 0}")
        }
        builder.appendLine("proot_pressure_window_mode=${prootTelemetry.pressureWindow.mode.toRuntimeEnvValue()}")
        builder.appendLine("proot_pressure_window_ms=${prootTelemetry.pressureWindow.windowMs}")
        builder.appendLine("proot_pressure_level=${prootTelemetry.pressureWindow.signalLevel.name}")
        builder.appendLine("proot_pressure_score=${prootTelemetry.pressureWindow.pressureScore}")
        builder.appendLine("proot_pressure_events=${prootTelemetry.pressureWindow.eventsInWindow}")
        builder.appendLine("proot_pressure_fork_exec_events=${prootTelemetry.pressureWindow.forkExecEventsInWindow}")
        builder.appendLine("proot_process_resource_source=${processResourceSnapshot.source.toRuntimeEnvValue()}")
        builder.appendLine("proot_process_resource_process_count=${processResourceSnapshot.processCount}")
        builder.appendLine("proot_process_resource_rss_kb=${processResourceSnapshot.rssKb}")
        builder.appendLine("proot_process_resource_vm_size_kb=${processResourceSnapshot.vmSizeKb}")
        builder.appendLine("proot_process_resource_cpu_time_ticks=${processResourceSnapshot.cpuTimeTicks}")
        builder.appendLine("proot_process_resource_io_read_bytes=${processResourceSnapshot.ioReadBytes}")
        builder.appendLine("proot_process_resource_io_write_bytes=${processResourceSnapshot.ioWriteBytes}")
        builder.appendLine("process_snapshot_source=${processSnapshotSource.toRuntimeEnvValue()}")
        builder.appendLine("process_snapshot_host_proc_count=$processSnapshotHostProcessCount")
        builder.appendLine("process_snapshot_container_ps_count=$processSnapshotContainerProcessCount")
        builder.appendLine("process_snapshot_merged_process_count=$processSnapshotMergedProcessCount")
        builder.appendLine("process_snapshot_sample=${processSnapshotSample.toRuntimeEnvValue()}")
        builder.appendLine("runtime_lifecycle_ledger_path=${WorkspaceBuildSupport.CONTAINER_RUNTIME_LIFECYCLE_LEDGER_PATH}")
        builder.appendLine("runtime_lifecycle_ledger_archive_dir=${WorkspaceBuildSupport.CONTAINER_RUNTIME_LIFECYCLE_LEDGER_DIR_PATH}")
        builder.appendLine("runtime_lifecycle_ledger_retention=session_current_plus_8_archives_max_2mb_each")
        builder.appendLine("controlled_probe_process_seen=${controlledProbeVisibility.processSeen}")
        builder.appendLine("controlled_probe_process_pid=${controlledProbeVisibility.pid ?: 0}")
        builder.appendLine("controlled_probe_process_ppid=${controlledProbeVisibility.parentPid ?: 0}")
        builder.appendLine("controlled_probe_process_pgid=${controlledProbeVisibility.processGroupId ?: 0}")
        builder.appendLine("controlled_probe_process_sid=${controlledProbeVisibility.sessionId ?: 0}")
        builder.appendLine("controlled_probe_process_state=${controlledProbeVisibility.state.toRuntimeEnvValue()}")
        builder.appendLine("controlled_probe_process_source_label=${controlledProbeVisibility.sourceLabel.toRuntimeEnvValue()}")
        builder.appendLine("controlled_probe_process_linked_terminal_present=${controlledProbeVisibility.linkedTerminalSessionIdPresent}")
        builder.appendLine("controlled_probe_process_linked_runtime_present=${controlledProbeVisibility.linkedRuntimeIdPresent}")
        builder.appendLine("runtime_process_unit_manifest_strong_candidate_count=${controlledProbeVisibility.strongCandidateCount}")
        builder.appendLine("runtime_process_unit_manifest_strong_candidate_1_pid=${controlledProbeVisibility.firstStrongCandidatePid ?: 0}")
        builder.appendLine("runtime_process_unit_manifest_strong_candidate_1_unit_id=${controlledProbeVisibility.firstStrongCandidateUnitId.toRuntimeEnvValue()}")
        builder.appendLine("runtime_process_unit_manifest_strong_candidate_1_match_source=${controlledProbeVisibility.firstStrongCandidateMatchSource.name}")
        builder.appendLine("runtime_process_unit_manifest_strong_candidate_1_rejected_reason=${controlledProbeVisibility.firstStrongCandidateRejectedReason.toRuntimeEnvValue()}")
        builder.appendLine("runtime_policy_hot_reload_enabled=$runtimePolicyHotReloadEnabled")
        builder.appendLine("runtime_policy_hot_reload_interval_ms=$runtimePolicyHotReloadIntervalMs")
        builder.appendLine("runtime_policy_hot_reload_generation=$runtimePolicyHotReloadGeneration")
        builder.appendLine("runtime_policy_hot_reload_last_reload_at=$runtimePolicyHotReloadLastReloadAt")
        builder.appendLine("runtime_policy_hot_reload_last_changed=${runtimePolicyHotReloadLastChanged.toRuntimeEnvValue()}")
        builder.appendLine("runtime_policy_hot_reload_workspace_path=${runtimePolicyHotReloadWorkspacePath.toRuntimeEnvValue()}")
        val runtimeReclaimerExecutionEnabled =
            reclaimerPolicyProfile != RuntimeReclaimerProfile.OBSERVE_ONLY.name
        builder.appendLine("runtime_reclaimer_execution_owner=android_control_plane")
        builder.appendLine("runtime_reclaimer_policy_profile=$reclaimerPolicyProfile")
        builder.appendLine("runtime_reclaimer_policy_path=${reclaimerPolicyPath.toRuntimeEnvValue()}")
        builder.appendLine("runtime_reclaimer_execution_enabled=$runtimeReclaimerExecutionEnabled")
        builder.appendLine("runtime_reclaimer_consumes_lifecycle_reclaim_plan=true")
        builder.appendLine("runtime_reclaimer_lifecycle_model=system_core_user_locked_foreground_priority_lease_pool_anomaly_pool")
        builder.appendLine("runtime_reclaimer_lifecycle_execution_contract=android_executes_only_ranked_lifecycle_candidates")
        builder.appendLine("runtime_reclaimer_lifecycle_priority=lease_pool_rank_before_generic_pressure_reclaim")
        builder.appendLine("runtime_reclaimer_execution_scope=automatic_registered_background_runtime_policy_classified_unattributed_explicit_owner_reclaim")
        builder.appendLine("runtime_reclaimer_excluded_scope=system_core_user_locked_unclassified_unknown_auto_interactive_owner_reclaim")
        builder.appendLine("runtime_reclaimer_owner_reclaim_mode=explicit_owner_id_only")
        builder.appendLine("runtime_reclaimer_proot_core_reclaim_allowed=false")
        val runtimeReclaimerExecution = RuntimeReclaimer.executionSnapshot()
        builder.appendLine("runtime_reclaimer_last_execution_at=${runtimeReclaimerExecution.lastExecutionAtMs}")
        builder.appendLine("runtime_reclaimer_last_execution_kind=${runtimeReclaimerExecution.lastExecutionKind.toRuntimeEnvValue()}")
        builder.appendLine("runtime_reclaimer_last_target_id=${runtimeReclaimerExecution.lastTargetId.toRuntimeEnvValue()}")
        builder.appendLine("runtime_reclaimer_last_target_title=${runtimeReclaimerExecution.lastTargetTitle.toRuntimeEnvValue()}")
        builder.appendLine("runtime_reclaimer_last_reason=${runtimeReclaimerExecution.lastReason.toRuntimeEnvValue()}")
        builder.appendLine("runtime_reclaimer_runtime_stop_request_count=${runtimeReclaimerExecution.runtimeStopRequestCount}")
        builder.appendLine("runtime_reclaimer_unattributed_terminate_request_count=${runtimeReclaimerExecution.unattributedTerminateRequestCount}")
        builder.appendLine("runtime_reclaimer_owner_process_terminate_request_count=${runtimeReclaimerExecution.ownerProcessTerminateRequestCount}")
        builder.appendLine("runtime_reclaimer_skipped_in_flight_count=${runtimeReclaimerExecution.skippedInFlightCount}")
        builder.appendLine("runtime_reclaimer_in_flight_runtime_count=${runtimeReclaimerExecution.inFlightRuntimeCount}")
        builder.appendLine("runtime_reclaimer_in_flight_pid_count=${runtimeReclaimerExecution.inFlightPidCount}")
        builder.appendLine("runtime_reclaimer_in_flight_owner_count=${runtimeReclaimerExecution.inFlightOwnerCount}")
        builder.append(RuntimeMemoryLifecycleRuleTrigger.executionSnapshot().toEnvText())
        builder.append(RuntimeLifecycleStrategyActivator.executionSnapshot().toEnvText())
        builder.append(prootTelemetryHealth.toEnvText())
        builder.append(prootTelemetryRepairPlan.toEnvText())
        builder.append(pressureConsumer.toEnvText())
        builder.append(pressureStability.toEnvText())
        builder.append(prootPoolPlan.toEnvText())
        builder.append(prootManagementMainline.toEnvText())
        builder.append(prootDeviceCalibration.toEnvText())
        builder.append(workloadRegistry.toEnvText())
        builder.append(processUnitManifest.toEnvText(maxItems = 5, states = systemProcessLifecycle.processUnitStates))
        builder.append(lifecyclePolicySurface.toEnvText())
        builder.append(lifecyclePolicyProfileSurface.toEnvText())
        builder.append(lifecycleIntentSurface.toEnvText())
        builder.append(backgroundDecay.toEnvText())
        builder.append(budgetPressure.toEnvText())
        builder.append(lifecycleReclaimPlan.toEnvText())
        builder.append(lifecycleProotExpansionBudget.toEnvText())
        builder.append(prootCapacityExecutor.toEnvText())
        appendSystemProcessLifecycleMainlineEnv(builder)
        builder.append(RuntimeProotCapacityActuator.executionSnapshot().toEnvText())
        builder.append(laneAdmission.toEnvText())
        builder.append(startPreflight.toEnvText())
        builder.append(startQueuePlan.toEnvText())
        appendLegacyObserveOnlySummary(builder)
        builder.appendLine("resident_active_profile=$residentPolicyProfile")
        builder.appendLine("resident_policy_path=${residentPolicyPath.toRuntimeEnvValue()}")
        val backgroundRoots = roots
            .filter { it.ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME }
        builder.appendLine("background_root_count=${backgroundRoots.size}")
        backgroundRoots
            .sortedByDescending {
                maxOf(
                    it.lastAdmissionDeferredAt ?: 0L,
                    it.lastRecoveredAt ?: 0L,
                    it.lastReclaimedAt ?: 0L,
                    it.stopReconciliationAt ?: 0L
                )
            }
            .take(5)
            .forEachIndexed { index, root ->
                val ordinal = index + 1
                builder.appendLine("runtime_${ordinal}_id=${root.ownerId.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_title=${root.title.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_kind=${root.runtimeKind?.name.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_status=${root.statusLabel.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_admission_source=${root.lastAdmissionSource.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_admission_reason=${root.lastAdmissionReason.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_admission_deferred_at=${root.lastAdmissionDeferredAt ?: 0L}")
                builder.appendLine("runtime_${ordinal}_recovery_source=${root.lastRecoverySource.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_recovery_reason=${root.lastRecoveryReason.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_recovered_at=${root.lastRecoveredAt ?: 0L}")
                builder.appendLine("runtime_${ordinal}_reclaim_source=${root.lastReclaimSource.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_reclaim_reason=${root.lastReclaimReason.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_reclaimed_at=${root.lastReclaimedAt ?: 0L}")
                builder.appendLine("runtime_${ordinal}_stop_reconciliation_state=${root.stopReconciliationState?.name.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_stop_reconciliation_reason=${root.stopReconciliationReason.toRuntimeEnvValue()}")
                builder.appendLine("runtime_${ordinal}_stop_reconciliation_at=${root.stopReconciliationAt ?: 0L}")
                builder.appendLine("runtime_${ordinal}_stop_reconciliation_auto_recovery_suppressed=${root.stopReconciliationAutoRecoverySuppressed}")
        }
        return builder.toString()
    }

    private fun RuntimeHealthSnapshot.appendSystemProcessLifecycleMainlineEnv(builder: StringBuilder) {
        builder.appendLine("system_process_lifecycle_mode=${systemProcessLifecycle.mode.toRuntimeEnvValue()}")
        builder.appendLine("system_process_lifecycle_output_profile=mainline_summary")
        builder.appendLine("system_process_lifecycle_enforcement_mode=${systemProcessLifecycle.enforcementMode.toRuntimeEnvValue()}")
        builder.appendLine("system_process_lifecycle_enforcement_enabled=${systemProcessLifecycle.enforcementEnabled}")
        builder.appendLine("system_process_lifecycle_management_model=${systemProcessLifecycle.managementModel.toRuntimeEnvValue()}")
        builder.appendLine("system_process_lifecycle_execution_boundary=${systemProcessLifecycle.executionBoundary.toRuntimeEnvValue()}")
        builder.append(systemProcessLifecycle.memoryLedger.toEnvText("system_process_lifecycle_memory_ledger"))
        builder.appendLine("system_process_lifecycle_runtime_host_state=${systemProcessLifecycle.runtimeHostState.name}")
        builder.appendLine("system_process_lifecycle_default_proot_running=${systemProcessLifecycle.defaultProotRunning}")
        builder.appendLine("system_process_lifecycle_default_proot_recovery_action=${systemProcessLifecycle.defaultProotRecoveryAction.name}")
        builder.appendLine("system_process_lifecycle_proot_scale_out_action=${systemProcessLifecycle.prootScaleOutAction.name}")
        builder.appendLine("system_process_lifecycle_proot_scale_out_requested=${systemProcessLifecycle.prootScaleOutRequested}")
        builder.appendLine("system_process_lifecycle_proot_scale_out_approved=${systemProcessLifecycle.prootScaleOutApproved}")
        builder.appendLine("system_process_lifecycle_proot_scale_out_queue_instead=${systemProcessLifecycle.prootScaleOutQueueInstead}")
        builder.appendLine("system_process_lifecycle_lease_pool_over_budget=${systemProcessLifecycle.leasePoolOverBudget}")
        builder.appendLine("system_process_lifecycle_lease_pool_cleanup_candidate_count=${systemProcessLifecycle.leasePoolCleanupCandidateCount}")
        builder.appendLine("system_process_lifecycle_user_locked_layer_count=${systemProcessLifecycle.userLockedLayerCount}")
        builder.appendLine("system_process_lifecycle_quarantine_layer_count=${systemProcessLifecycle.quarantineLayerCount}")
        builder.appendLine("system_process_lifecycle_process_unit_manifest_status=${systemProcessLifecycle.processUnitManifestStatus.toRuntimeEnvValue()}")
        builder.appendLine("system_process_lifecycle_process_unit_matched_root_count=${systemProcessLifecycle.processUnitMatchedRootCount}")
        builder.appendLine("system_process_lifecycle_process_unit_user_locked_count=${systemProcessLifecycle.processUnitUserLockedCount}")
        builder.appendLine("system_process_lifecycle_process_unit_wait_confirm_restart_count=${systemProcessLifecycle.processUnitWaitConfirmRestartCount}")
        builder.appendLine("system_process_lifecycle_process_unit_auto_restart_allowed_count=${systemProcessLifecycle.processUnitAutoRestartAllowedCount}")
        builder.appendLine("system_process_lifecycle_process_unit_core_recovery_required_count=${systemProcessLifecycle.processUnitCoreRecoveryRequiredCount}")
        builder.append(systemProcessLifecycle.authorityMatrix.toEnvText())
        builder.append(systemProcessLifecycle.processResourceWatch.toEnvText())
        builder.append(systemProcessLifecycle.resourceEventLedger.toEnvText())
        builder.append(systemProcessLifecycle.lifecycleActionPlanner.toEnvText())
        builder.appendLine("system_process_lifecycle_legacy_action_inbox_detail_mode=summarized")
        builder.appendLine("system_process_lifecycle_legacy_diagnostic_review_detail_mode=summarized")
        builder.appendLine("system_process_lifecycle_reason=${systemProcessLifecycle.reason.toRuntimeEnvValue()}")
    }

    private fun RuntimeHealthSnapshot.appendLegacyObserveOnlySummary(builder: StringBuilder) {
        builder.appendLine("runtime_health_legacy_observe_only_modules=process_observation,user_notice,action_inbox,diagnostic_review,governance,canary,topology")
        builder.appendLine("runtime_health_legacy_observe_only_module_count=7")
        builder.appendLine("runtime_health_legacy_observe_only_boundary=no_default_detail_expansion_no_runtime_action")
        builder.appendLine("legacy_process_observation_mode=${processObservationProbe.mode.toRuntimeEnvValue()}")
        builder.appendLine("legacy_process_observation_status=disabled_mainline_uses_host_proc_container_ps")
        builder.appendLine("legacy_process_observation_validation_status=disabled_no_user_notice_no_mainline_gate")
        builder.appendLine("legacy_user_notice_count=${lifecycleWarningNotice.noticeCount}")
        builder.appendLine("legacy_user_notice_action_required_count=${lifecycleWarningNotice.actionRequiredCount}")
        builder.appendLine("legacy_user_notice_critical_dry_run_count=${lifecycleWarningNotice.criticalDryRunCount}")
        builder.appendLine("legacy_action_inbox_open_action_count=${systemProcessLifecycle.lifecycleActionInbox.openActionCount}")
        builder.appendLine("legacy_action_inbox_critical_dry_run_count=${systemProcessLifecycle.lifecycleActionInbox.criticalDryRunCount}")
        builder.appendLine("legacy_diagnostic_review_scenario_count=${systemProcessLifecycle.lifecycleDiagnosticReview.scenarioReviewCount}")
        builder.appendLine("legacy_diagnostic_review_failed_invariant_count=${systemProcessLifecycle.lifecycleDiagnosticReview.scenarioFailedInvariantCount}")
        builder.appendLine("legacy_governance_action_recommendation=${governanceActionPlan.recommendation.name}")
        builder.appendLine("legacy_governance_action_planned_count=${governanceActionPlan.plannedActionCount}")
        builder.appendLine("legacy_governance_readiness_state=${governanceReadiness.state.name}")
        builder.appendLine("legacy_governance_readiness_blocked_count=${governanceReadiness.blockedCount}")
        builder.appendLine("legacy_canary_audit_state=${canaryAuditPlan.state.name}")
        builder.appendLine("legacy_canary_audit_unsafe_actual_action_count=${canaryAuditPlan.unsafeActualActionCount}")
        builder.appendLine("legacy_canary_audit_summary=${canaryAuditPlan.summary().toRuntimeEnvValue()}")
        builder.appendLine("legacy_topology_state=${managementTopology.state.name}")
        builder.appendLine("legacy_topology_recommendation=${managementTopology.recommendation.name}")
        builder.appendLine("legacy_topology_summary=${managementTopology.summary().toRuntimeEnvValue()}")
    }

    private fun RuntimeHealthSnapshot.toMainlineLogLine(reason: String): String {
        return buildString {
            append("runtime mainline: ")
            append("trigger=${reason.toRuntimeEnvValue()} ")
            append("flow=monitoring_judgment_android_kf_execution_boundary ")
            append("pressure=${pressure.level.name}/${pressure.totalRssKb}kb ")
            append("prootTelemetry=${prootTelemetry.collectionStatus.toRuntimeEnvValue()} ")
            append("events=${prootTelemetry.counters.totalEvents} ")
            append("lastRefresh=${prootTelemetry.lastRefreshEvents} ")
            append("liveTracees=${prootTelemetry.processLiveTable.liveTraceeCount} ")
            append("health=${prootTelemetryHealth.state.name}/${prootTelemetryHealth.blocker.toRuntimeEnvValue()} ")
            append("processSource=${processSnapshotSource.toRuntimeEnvValue()} ")
            append("processes=${processResourceSnapshot.processCount} ")
            append("matchedUnits=${systemProcessLifecycle.processUnitMatchedRootCount} ")
            append("leases=${workloadRegistry.leaseCount}/${lifecycleReclaimPlan.leaseCount} ")
            append("reclaimer=${RuntimeReclaimer.executionSnapshot().lastExecutionKind.toRuntimeEnvValue()} ")
            append("prootActuator=${RuntimeProotCapacityActuator.executionSnapshot().lastExecutionKind.toRuntimeEnvValue()}")
        }
    }
}

private fun String?.toRuntimeEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/-]"), "_")
        .take(160)
}
