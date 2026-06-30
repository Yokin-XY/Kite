package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.SupervisordServiceHealthSnapshot

data class RuntimeRootDiagnostic(
    val owner: String,
    val ownerId: String?,
    val title: String,
    val reality: String,
    val status: String,
    val observedPid: Int?,
    val expectedPid: Int?,
    val processCount: Int,
    val rssKb: Long,
    val retentionClass: String,
    val resident: Boolean,
    val reclaimPriority: Int,
    val autoReclaimAllowed: Boolean,
    val classificationSource: String,
    val classificationReason: String?,
    val oomScoreAdj: Int?,
    val activeOwner: Boolean,
    val staleReason: String?,
    val restartPolicy: String?,
    val restartFailureCount: Int,
    val restartDelayMs: Long?,
    val lastRestartReason: String?,
    val lastRecoveredAt: Long?,
    val lastRecoverySource: String?,
    val lastRecoveryReason: String?,
    val lastAdmissionDeferredAt: Long?,
    val lastAdmissionSource: String?,
    val lastAdmissionReason: String?,
    val lastReclaimedAt: Long?,
    val lastReclaimSource: String?,
    val lastReclaimReason: String?,
    val lastError: String?
)

data class SupervisordServiceDiagnostic(
    val serviceId: String,
    val displayName: String,
    val health: String,
    val pid: Int?,
    val source: String,
    val failureReason: String?,
    val bindAddress: String?,
    val bindPort: Int?,
    val exposureScope: String?
)

data class RuntimeEndpointDiagnostic(
    val ownerId: String,
    val displayName: String,
    val bindAddress: String?,
    val bindPort: Int?,
    val exposureScope: String?,
    val status: String
)

data class RuntimeDiagnosticSnapshot(
    val spaceId: String?,
    val containerId: String?,
    val networkModeLabel: String?,
    val networkSummary: String?,
    val loopbackSummary: String?,
    val portPolicySummary: String?,
    val controlBoundarySummary: String?,
    val networkNotes: List<String>,
    val processSource: String,
    val reconciliationReason: String,
    val runningRoots: Int,
    val staleRoots: Int,
    val terminalRoots: Int,
    val backgroundRoots: Int,
    val unattributedRoots: Int,
    val pressureLevel: String,
    val pressureProfile: String,
    val residentProfile: String,
    val pressureTotalRssKb: Long,
    val pressureProtectedRssKb: Long,
    val pressureReclaimableRssKb: Long,
    val pressureCandidateCount: Int,
    val pressureSummary: String,
    val prootTelemetrySummary: String,
    val prootTelemetryStatus: String,
    val prootTelemetryPath: String,
    val prootTelemetryTotalEvents: Long,
    val prootTelemetryLastRefreshEvents: Int,
    val prootTelemetryForkExecLastRefresh: Int,
    val prootTelemetryLiveTracees: Int,
    val prootTelemetryKnownTracees: Int,
    val prootTelemetryParseErrors: Long,
    val prootTelemetryHealthSummary: String,
    val prootTelemetryHealthState: String,
    val prootTelemetryHealthRecommendation: String,
    val prootTelemetryHealthCanaryHealthy: Boolean,
    val prootTelemetryHealthBlocker: String,
    val prootTelemetryRepairPlanSummary: String,
    val prootTelemetryRepairAction: String,
    val prootTelemetryRepairReadiness: String,
    val prootTelemetryRepairManualActionRequired: Boolean,
    val prootLiveTableSummary: String,
    val prootPressureWindowSummary: String,
    val pressureConsumerSummary: String,
    val pressureConsumerState: String,
    val pressureConsumerRecommendation: String,
    val pressureConsumerEnforcementEnabled: Boolean,
    val prootPoolPlanSummary: String,
    val prootPoolPlanState: String,
    val prootPoolPlanRecommendation: String,
    val prootPoolPlanEnforcementEnabled: Boolean,
    val prootPoolPlanPlannedSlots: Int,
    val prootPoolPlanBurstHeadroomLaneCount: Int,
    val prootPoolPlanIdleReclaimCandidateCount: Int,
    val workloadRegistrySummary: String,
    val workloadRegistryTotal: Int,
    val workloadRegistryKeepCount: Int,
    val workloadRegistryLeaseCount: Int,
    val workloadRegistryCleanupCandidateCount: Int,
    val workloadRegistryStrayCount: Int,
    val workloadRegistryUnassignedTracees: Int,
    val workloadRegistryEnforcementEnabled: Boolean,
    val backgroundDecaySummary: String,
    val backgroundDecayPhase: String,
    val backgroundDecayRecommendation: String,
    val backgroundDecayEnforcementEnabled: Boolean,
    val budgetPressureSummary: String,
    val budgetPressureOverallState: String,
    val budgetPressureRecommendation: String,
    val budgetPressureEnforcementEnabled: Boolean,
    val lifecycleReclaimPlanSummary: String,
    val lifecycleReclaimPlanState: String,
    val lifecycleReclaimPlanRecommendation: String,
    val lifecycleReclaimPlanEnforcementEnabled: Boolean,
    val lifecycleReclaimPlanExpireLeaseCount: Int,
    val lifecycleReclaimPlanCleanupReviewCount: Int,
    val lifecycleReclaimPlanQuarantineReviewCount: Int,
    val laneAdmissionSummary: String,
    val laneAdmissionRecommendation: String,
    val laneAdmissionEnforcementEnabled: Boolean,
    val startPreflightSummary: String,
    val startPreflightRecommendation: String,
    val startPreflightEnforcementEnabled: Boolean,
    val startQueuePlanSummary: String,
    val startQueuePlanRecommendation: String,
    val startQueuePlanEnforcementEnabled: Boolean,
    val governanceActionPlanSummary: String,
    val governanceActionPlanRecommendation: String,
    val governanceActionPlanEnforcementEnabled: Boolean,
    val governanceReadinessSummary: String,
    val governanceReadinessState: String,
    val governanceReadinessRecommendation: String,
    val governanceReadinessEnforcementEnabled: Boolean,
    val canaryEntryPlanSummary: String,
    val canaryEntryPlanState: String,
    val canaryEntryPlanRecommendation: String,
    val canaryEntryPlanAllowed: Boolean,
    val canaryScopePlanSummary: String,
    val canaryScopePlanState: String,
    val canaryScopePlanRecommendation: String,
    val canaryScopePlanAllowed: Boolean,
    val canaryActivationPlanSummary: String,
    val canaryActivationPlanState: String,
    val canaryActivationPlanRecommendation: String,
    val canaryActivationPlanManualReady: Boolean,
    val canarySessionPlanSummary: String,
    val canarySessionPlanState: String,
    val canarySessionPlanRecommendation: String,
    val canarySessionPlanManualStartAllowed: Boolean,
    val canaryApprovalRequestSummary: String,
    val canaryApprovalRequestState: String,
    val canaryApprovalRequestRecommendation: String,
    val canaryApprovalRequestReady: Boolean,
    val canaryApprovalGateSummary: String,
    val canaryApprovalGateState: String,
    val canaryApprovalGateRecommendation: String,
    val canaryApprovalGateApprovalGranted: Boolean,
    val canaryGrantPlanSummary: String,
    val canaryGrantPlanState: String,
    val canaryGrantPlanRecommendation: String,
    val canaryGrantPlanGrantIssued: Boolean,
    val canarySessionStartPlanSummary: String,
    val canarySessionStartPlanState: String,
    val canarySessionStartPlanRecommendation: String,
    val canarySessionStartPlanStartReady: Boolean,
    val canarySessionLeasePlanSummary: String,
    val canarySessionLeasePlanState: String,
    val canarySessionLeasePlanRecommendation: String,
    val canarySessionLeasePlanLeaseCreated: Boolean,
    val canaryEnforcementPlanSummary: String,
    val canaryEnforcementPlanState: String,
    val canaryEnforcementPlanRecommendation: String,
    val canaryEnforcementPlanActualEnforcementCount: Int,
    val canaryRollbackPlanSummary: String,
    val canaryRollbackPlanState: String,
    val canaryRollbackPlanRecommendation: String,
    val canaryRollbackPlanActualRollbackCount: Int,
    val canaryAuditPlanSummary: String,
    val canaryAuditPlanState: String,
    val canaryAuditPlanRecommendation: String,
    val canaryAuditPlanUnsafeActualActionCount: Int,
    val managementTopologySummary: String,
    val managementTopologyState: String,
    val managementTopologyRecommendation: String,
    val managementTopologyMainlineNextFocus: String,
    val primaryMetricsPid: Int?,
    val legacyContainerPid: Int?,
    val containerLastError: String?,
    val supervisordSummary: String?,
    val roots: List<RuntimeRootDiagnostic>,
    val services: List<SupervisordServiceDiagnostic>,
    val endpoints: List<RuntimeEndpointDiagnostic>,
    val processSnapshotAgeMs: Long?,
    val overviewAgeMs: Long?,
    val reconciledAgeMs: Long?
) {
    fun toLogLine(maxRoots: Int = 8, maxServices: Int = 6): String {
        val rootText = roots.take(maxRoots).joinToString("; ") { it.toCompactText() }
        val serviceText = services.take(maxServices).joinToString("; ") { it.toCompactText() }
        val endpointText = endpoints.take(maxServices).joinToString("; ") { it.toCompactText() }
        return buildString {
            append("runtime diagnostics: ")
            append("space=${spaceId ?: "none"} container=${containerId ?: "none"} ")
            networkModeLabel?.let { append("networkMode=${it.compact(32)} ") }
            networkSummary?.let { append("network=${it.compact(64)} ") }
            loopbackSummary?.let { append("loopback=${it.compact(64)} ") }
            portPolicySummary?.let { append("ports=${it.compact(64)} ") }
            controlBoundarySummary?.let { append("control=${it.compact(64)} ") }
            append("running=$runningRoots stale=$staleRoots terminal=$terminalRoots ")
            append("background=$backgroundRoots unattributed=$unattributedRoots ")
            append("pressure=$pressureLevel rssKb=$pressureTotalRssKb reclaimableKb=$pressureReclaimableRssKb ")
            append("prootTelemetry=[$prootTelemetrySummary] ")
            append("prootTelemetryHealth=[$prootTelemetryHealthSummary] ")
            append("prootTelemetryRepair=[$prootTelemetryRepairPlanSummary] ")
            append("prootFacts=[$prootLiveTableSummary] prootPressure=[$prootPressureWindowSummary] ")
            append("pressureConsumer=[$pressureConsumerSummary] ")
            append("prootPoolPlan=[$prootPoolPlanSummary] ")
            append("workloadRegistry=[$workloadRegistrySummary] ")
            append("backgroundDecay=[$backgroundDecaySummary] ")
            append("budgetPressure=[$budgetPressureSummary] ")
            append("lifecycleReclaimPlan=[$lifecycleReclaimPlanSummary] ")
            append("laneAdmission=[$laneAdmissionSummary] ")
            append("startPreflight=[$startPreflightSummary] ")
            append("startQueuePlan=[$startQueuePlanSummary] ")
            append("governanceActionPlan=[$governanceActionPlanSummary] ")
            append("governanceReadiness=[$governanceReadinessSummary] ")
            append("canaryEntry=[$canaryEntryPlanSummary] ")
            append("canaryScope=[$canaryScopePlanSummary] ")
            append("canaryActivation=[$canaryActivationPlanSummary] ")
            append("canarySession=[$canarySessionPlanSummary] ")
            append("canaryApproval=[$canaryApprovalRequestSummary] ")
            append("canaryApprovalGate=[$canaryApprovalGateSummary] ")
            append("canaryGrantPlan=[$canaryGrantPlanSummary] ")
            append("canarySessionStart=[$canarySessionStartPlanSummary] ")
            append("canarySessionLease=[$canarySessionLeasePlanSummary] ")
            append("canaryEnforcement=[$canaryEnforcementPlanSummary] ")
            append("canaryRollback=[$canaryRollbackPlanSummary] ")
            append("canaryAudit=[$canaryAuditPlanSummary] ")
            append("runtimeTopology=[$managementTopologySummary] ")
            append("profile=$pressureProfile residentProfile=$residentProfile ")
            append("metricsPid=${primaryMetricsPid ?: 0} legacyPid=${legacyContainerPid ?: 0} ")
            append("source=$processSource processAgeMs=${processSnapshotAgeMs ?: -1} ")
            append("overviewAgeMs=${overviewAgeMs ?: -1} reconciledAgeMs=${reconciledAgeMs ?: -1} ")
            append("reason=$reconciliationReason")
            containerLastError?.takeIf { it.isNotBlank() }?.let {
                append(" containerError=${it.compact(120)}")
            }
            supervisordSummary?.takeIf { it.isNotBlank() }?.let {
                append(" supervisord=${it.compact(160)}")
            }
            if (rootText.isNotBlank()) {
                append(" roots=[$rootText")
                if (roots.size > maxRoots) append("; +${roots.size - maxRoots}")
                append("]")
            }
            if (serviceText.isNotBlank()) {
                append(" services=[$serviceText")
                if (services.size > maxServices) append("; +${services.size - maxServices}")
                append("]")
            }
            if (endpointText.isNotBlank()) {
                append(" endpoints=[$endpointText")
                if (endpoints.size > maxServices) append("; +${endpoints.size - maxServices}")
                append("]")
            }
        }
    }

    fun toStatusText(maxRoots: Int = 4, maxServices: Int = 4): String {
        val lines = mutableListOf<String>()
        networkModeLabel?.let { lines += "networkMode=$it" }
        networkSummary?.let { lines += "network=$it" }
        loopbackSummary?.let { lines += "loopback=$it" }
        portPolicySummary?.let { lines += "ports=$it" }
        controlBoundarySummary?.let { lines += "control=$it" }
        networkNotes.take(3).forEach { note ->
            lines += "note=${note.compact(180)}"
        }
        lines += "roots running=$runningRoots stale=$staleRoots terminal=$terminalRoots background=$backgroundRoots unattributed=$unattributedRoots"
        lines += "pressure=$pressureSummary profile=$pressureProfile residentProfile=$residentProfile"
        lines += "prootTelemetry=$prootTelemetrySummary path=${prootTelemetryPath.compact(120)}"
        lines += "prootTelemetryHealth=$prootTelemetryHealthSummary"
        lines += "prootTelemetryRepair=$prootTelemetryRepairPlanSummary"
        lines += "prootFacts=$prootLiveTableSummary"
        lines += "prootPressure=$prootPressureWindowSummary"
        lines += "pressureConsumer=$pressureConsumerSummary"
        lines += "prootPoolPlan=$prootPoolPlanSummary"
        lines += "workloadRegistry=$workloadRegistrySummary"
        lines += "backgroundDecay=$backgroundDecaySummary"
        lines += "budgetPressure=$budgetPressureSummary"
        lines += "lifecycleReclaimPlan=$lifecycleReclaimPlanSummary"
        lines += "laneAdmission=$laneAdmissionSummary"
        lines += "startPreflight=$startPreflightSummary"
        lines += "startQueuePlan=$startQueuePlanSummary"
        lines += "governanceActionPlan=$governanceActionPlanSummary"
        lines += "governanceReadiness=$governanceReadinessSummary"
        lines += "canaryEntry=$canaryEntryPlanSummary"
        lines += "canaryScope=$canaryScopePlanSummary"
        lines += "canaryActivation=$canaryActivationPlanSummary"
        lines += "canarySession=$canarySessionPlanSummary"
        lines += "canaryApproval=$canaryApprovalRequestSummary"
        lines += "canaryApprovalGate=$canaryApprovalGateSummary"
        lines += "canaryGrantPlan=$canaryGrantPlanSummary"
        lines += "canarySessionStart=$canarySessionStartPlanSummary"
        lines += "canarySessionLease=$canarySessionLeasePlanSummary"
        lines += "canaryEnforcement=$canaryEnforcementPlanSummary"
        lines += "canaryRollback=$canaryRollbackPlanSummary"
        lines += "canaryAudit=$canaryAuditPlanSummary"
        lines += "runtimeTopology=$managementTopologySummary"
        lines += "source=$processSource metricsPid=${primaryMetricsPid ?: "--"} reason=$reconciliationReason"
        if (processSnapshotAgeMs != null || overviewAgeMs != null || reconciledAgeMs != null) {
            lines += "age process=${processSnapshotAgeMs?.let(::formatAge) ?: "--"} overview=${overviewAgeMs?.let(::formatAge) ?: "--"} reconciled=${reconciledAgeMs?.let(::formatAge) ?: "--"}"
        }
        containerLastError?.takeIf { it.isNotBlank() }?.let {
            lines += "containerError=${it.compact(160)}"
        }
        supervisordSummary?.takeIf { it.isNotBlank() }?.let {
            lines += "supervisord=${it.compact(180)}"
        }
        roots.take(maxRoots).forEach { root ->
            lines += "- ${root.toCompactText()}"
        }
        if (roots.size > maxRoots) {
            lines += "- +${roots.size - maxRoots} more roots"
        }
        services
            .filter { it.health != "RUNNING" || !it.failureReason.isNullOrBlank() }
            .take(maxServices)
            .forEach { service ->
                lines += "- svc ${service.toCompactText()}"
            }
        endpoints.take(maxServices).forEach { endpoint ->
            lines += "- endpoint ${endpoint.toCompactText()}"
        }
        return lines.joinToString("\n")
    }

    private fun RuntimeRootDiagnostic.toCompactText(): String {
        val pid = observedPid?.let { "pid=$it" } ?: expectedPid?.let { "old=$it" } ?: "pid=?"
        val active = if (activeOwner) ",active" else ""
        val memory = if (rssKb > 0L) ",rss=${formatKb(rssKb)}" else ""
        val retention = ",retain=$retentionClass"
        val residentText = if (resident) ",resident=1" else ""
        val autoReclaim = if (autoReclaimAllowed) ",auto=1" else ""
        val classify = if (classificationSource.isNotBlank()) ",class=$classificationSource" else ""
        val oom = oomScoreAdj?.let { ",oomAdj=$it" }.orEmpty()
        val restart = if (restartFailureCount > 0 || restartDelayMs != null) {
            ",restartFailures=$restartFailureCount,next=${restartDelayMs?.let(::formatAge) ?: "now"}"
        } else {
            ""
        }
        val recovery = lastRecoverySource
            ?.takeIf { it.isNotBlank() }
            ?.let {
                ",recovery=${it.compact(24)}@${lastRecoveredAt?.let { ts -> formatAge((System.currentTimeMillis() - ts).coerceAtLeast(0L)) } ?: "--"}"
            }
            .orEmpty()
        val admission = lastAdmissionSource
            ?.takeIf { it.isNotBlank() }
            ?.let {
                ",admission=${it.compact(24)}@${lastAdmissionDeferredAt?.let { ts -> formatAge((System.currentTimeMillis() - ts).coerceAtLeast(0L)) } ?: "--"}"
            }
            .orEmpty()
        val reclaim = lastReclaimSource
            ?.takeIf { it.isNotBlank() }
            ?.let {
                ",reclaim=${it.compact(24)}@${lastReclaimedAt?.let { ts -> formatAge((System.currentTimeMillis() - ts).coerceAtLeast(0L)) } ?: "--"}"
            }
            .orEmpty()
        val reason = staleReason
            ?.takeIf { it.isNotBlank() }
            ?.let { ",stale=${it.compact(90)}" }
            .orEmpty()
        val error = lastError
            ?.takeIf { it.isNotBlank() }
            ?.let { ",error=${it.compact(90)}" }
            .orEmpty()
        return "$owner:${title.compact(36)}:$reality:$status:$pid,children=$processCount$memory$retention$residentText$autoReclaim$classify$oom$active$restart$admission$recovery$reclaim$reason$error"
    }

    private fun SupervisordServiceDiagnostic.toCompactText(): String {
        val pidText = pid?.let { ",pid=$it" }.orEmpty()
        val bindText = when {
            !bindAddress.isNullOrBlank() && bindPort != null -> ",bind=$bindAddress:$bindPort"
            bindPort != null -> ",port=$bindPort"
            else -> ""
        }
        val scopeText = exposureScope
            ?.takeIf { it.isNotBlank() }
            ?.let { ",scope=${it.compact(24)}" }
            .orEmpty()
        val failure = failureReason
            ?.takeIf { it.isNotBlank() }
            ?.let { ",reason=${it.compact(100)}" }
            .orEmpty()
        return "${displayName.compact(36)}:$health$pidText,source=$source$bindText$scopeText$failure"
    }

    private fun RuntimeEndpointDiagnostic.toCompactText(): String {
        val bindText = when {
            !bindAddress.isNullOrBlank() && bindPort != null -> "$bindAddress:$bindPort"
            bindPort != null -> "port=$bindPort"
            else -> "unbound"
        }
        val scopeText = exposureScope?.takeIf { it.isNotBlank() } ?: "--"
        return "${displayName.compact(36)}:$status,$bindText,scope=${scopeText.compact(24)}"
    }
}

object RuntimeDiagnostics {
    fun from(
        health: RuntimeHealthSnapshot = RuntimeHealthStore.snapshot.value,
        overview: RuntimeOverviewSnapshot = RuntimeOverviewStore.snapshot.value,
        now: Long = System.currentTimeMillis()
    ): RuntimeDiagnosticSnapshot {
        val services = health.supervisordServices?.services.orEmpty().map { service ->
            SupervisordServiceDiagnostic(
                serviceId = service.definition.serviceId,
                displayName = service.definition.displayName,
                health = service.health.name,
                pid = service.pid,
                source = service.source,
                failureReason = service.failureReason,
                bindAddress = null,
                bindPort = null,
                exposureScope = null
            )
        }
        val endpoints = overview.backgroundRuntimes
            .mapNotNull { runtime ->
                val hasBinding = !runtime.bindAddress.isNullOrBlank() || runtime.bindPort != null
                if (!hasBinding) {
                    null
                } else {
                    RuntimeEndpointDiagnostic(
                        ownerId = runtime.id,
                        displayName = runtime.title,
                        bindAddress = runtime.bindAddress,
                        bindPort = runtime.bindPort,
                        exposureScope = runtime.exposureScope.label,
                        status = runtime.status.name
                    )
                }
            }
        return RuntimeDiagnosticSnapshot(
            spaceId = health.spaceId,
            containerId = health.containerId,
            networkModeLabel = health.networkModeLabel,
            networkSummary = health.networkSemantics?.topology?.label,
            loopbackSummary = health.networkSemantics?.loopback?.label,
            portPolicySummary = health.networkSemantics?.portPolicy?.label,
            controlBoundarySummary = health.networkSemantics?.controlBoundary?.label,
            networkNotes = health.networkSemantics?.notes.orEmpty(),
            processSource = health.processSnapshotSource,
            reconciliationReason = health.reconciliationReason,
            runningRoots = health.runningRootCount,
            staleRoots = health.staleRootCount,
            terminalRoots = health.terminalRoots.size,
            backgroundRoots = health.backgroundRuntimeRoots.size,
            unattributedRoots = health.roots.count { it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED },
            pressureLevel = health.pressure.level.name,
            pressureProfile = health.pressure.activeProfile,
            residentProfile = health.residentPolicyProfile,
            pressureTotalRssKb = health.pressure.totalRssKb,
            pressureProtectedRssKb = health.pressure.protectedRssKb,
            pressureReclaimableRssKb = health.pressure.reclaimableRssKb,
            pressureCandidateCount = health.pressure.candidateCount,
            pressureSummary = health.pressure.summary(),
            prootTelemetrySummary = health.prootTelemetry.summary(),
            prootTelemetryStatus = health.prootTelemetry.collectionStatus,
            prootTelemetryPath = health.prootTelemetry.sourcePath,
            prootTelemetryTotalEvents = health.prootTelemetry.counters.totalEvents,
            prootTelemetryLastRefreshEvents = health.prootTelemetry.lastRefreshEvents,
            prootTelemetryForkExecLastRefresh = health.prootTelemetry.lastRefreshForkExecEvents,
            prootTelemetryLiveTracees = health.prootTelemetry.liveTraceeCount,
            prootTelemetryKnownTracees = health.prootTelemetry.knownTraceeCount,
            prootTelemetryParseErrors = health.prootTelemetry.counters.parseErrors,
            prootTelemetryHealthSummary = health.prootTelemetryHealth.summary(),
            prootTelemetryHealthState = health.prootTelemetryHealth.state.name,
            prootTelemetryHealthRecommendation = health.prootTelemetryHealth.recommendation.name,
            prootTelemetryHealthCanaryHealthy = health.prootTelemetryHealth.canaryHealthy,
            prootTelemetryHealthBlocker = health.prootTelemetryHealth.blocker,
            prootTelemetryRepairPlanSummary = health.prootTelemetryRepairPlan.summary(),
            prootTelemetryRepairAction = health.prootTelemetryRepairPlan.action.name,
            prootTelemetryRepairReadiness = health.prootTelemetryRepairPlan.readiness.name,
            prootTelemetryRepairManualActionRequired = health.prootTelemetryRepairPlan.manualActionRequired,
            prootLiveTableSummary = health.prootTelemetry.processLiveTable.summary(),
            prootPressureWindowSummary = health.prootTelemetry.pressureWindow.summary(),
            pressureConsumerSummary = health.pressureConsumer.summary(),
            pressureConsumerState = health.pressureConsumer.state.name,
            pressureConsumerRecommendation = health.pressureConsumer.recommendation.name,
            pressureConsumerEnforcementEnabled = health.pressureConsumer.enforcementEnabled,
            prootPoolPlanSummary = health.prootPoolPlan.summary(),
            prootPoolPlanState = health.prootPoolPlan.state.name,
            prootPoolPlanRecommendation = health.prootPoolPlan.recommendation.name,
            prootPoolPlanEnforcementEnabled = health.prootPoolPlan.enforcementEnabled,
            prootPoolPlanPlannedSlots = health.prootPoolPlan.plannedPoolSlots,
            prootPoolPlanBurstHeadroomLaneCount = health.prootPoolPlan.burstHeadroomLaneCount,
            prootPoolPlanIdleReclaimCandidateCount = health.prootPoolPlan.idleReclaimCandidateCount,
            workloadRegistrySummary = health.workloadRegistry.summary(),
            workloadRegistryTotal = health.workloadRegistry.totalWorkloads,
            workloadRegistryKeepCount = health.workloadRegistry.keepCount,
            workloadRegistryLeaseCount = health.workloadRegistry.leaseCount,
            workloadRegistryCleanupCandidateCount = health.workloadRegistry.cleanupCandidateCount,
            workloadRegistryStrayCount = health.workloadRegistry.strayCount,
            workloadRegistryUnassignedTracees = health.workloadRegistry.unassignedLiveTracees,
            workloadRegistryEnforcementEnabled = health.workloadRegistry.enforcementEnabled,
            backgroundDecaySummary = health.backgroundDecay.summary(),
            backgroundDecayPhase = health.backgroundDecay.phase.name,
            backgroundDecayRecommendation = health.backgroundDecay.recommendation.name,
            backgroundDecayEnforcementEnabled = health.backgroundDecay.enforcementEnabled,
            budgetPressureSummary = health.budgetPressure.summary(),
            budgetPressureOverallState = health.budgetPressure.overallState.name,
            budgetPressureRecommendation = health.budgetPressure.recommendation.name,
            budgetPressureEnforcementEnabled = health.budgetPressure.enforcementEnabled,
            lifecycleReclaimPlanSummary = health.lifecycleReclaimPlan.summary(),
            lifecycleReclaimPlanState = health.lifecycleReclaimPlan.state.name,
            lifecycleReclaimPlanRecommendation = health.lifecycleReclaimPlan.recommendation.name,
            lifecycleReclaimPlanEnforcementEnabled = health.lifecycleReclaimPlan.enforcementEnabled,
            lifecycleReclaimPlanExpireLeaseCount = health.lifecycleReclaimPlan.expireLeaseCount,
            lifecycleReclaimPlanCleanupReviewCount = health.lifecycleReclaimPlan.cleanupReviewCount,
            lifecycleReclaimPlanQuarantineReviewCount = health.lifecycleReclaimPlan.quarantineReviewCount,
            laneAdmissionSummary = health.laneAdmission.summary(),
            laneAdmissionRecommendation = health.laneAdmission.recommendation.name,
            laneAdmissionEnforcementEnabled = health.laneAdmission.enforcementEnabled,
            startPreflightSummary = health.startPreflight.summary(),
            startPreflightRecommendation = health.startPreflight.recommendation.name,
            startPreflightEnforcementEnabled = health.startPreflight.enforcementEnabled,
            startQueuePlanSummary = health.startQueuePlan.summary(),
            startQueuePlanRecommendation = health.startQueuePlan.recommendation.name,
            startQueuePlanEnforcementEnabled = health.startQueuePlan.enforcementEnabled,
            governanceActionPlanSummary = health.governanceActionPlan.summary(),
            governanceActionPlanRecommendation = health.governanceActionPlan.recommendation.name,
            governanceActionPlanEnforcementEnabled = health.governanceActionPlan.enforcementEnabled,
            governanceReadinessSummary = health.governanceReadiness.summary(),
            governanceReadinessState = health.governanceReadiness.state.name,
            governanceReadinessRecommendation = health.governanceReadiness.recommendation.name,
            governanceReadinessEnforcementEnabled = health.governanceReadiness.enforcementEnabled,
            canaryEntryPlanSummary = health.canaryEntryPlan.summary(),
            canaryEntryPlanState = health.canaryEntryPlan.state.name,
            canaryEntryPlanRecommendation = health.canaryEntryPlan.recommendation.name,
            canaryEntryPlanAllowed = health.canaryEntryPlan.entryAllowed,
            canaryScopePlanSummary = health.canaryScopePlan.summary(),
            canaryScopePlanState = health.canaryScopePlan.state.name,
            canaryScopePlanRecommendation = health.canaryScopePlan.recommendation.name,
            canaryScopePlanAllowed = health.canaryScopePlan.scopeAllowed,
            canaryActivationPlanSummary = health.canaryActivationPlan.summary(),
            canaryActivationPlanState = health.canaryActivationPlan.state.name,
            canaryActivationPlanRecommendation = health.canaryActivationPlan.recommendation.name,
            canaryActivationPlanManualReady = health.canaryActivationPlan.manualReady,
            canarySessionPlanSummary = health.canarySessionPlan.summary(),
            canarySessionPlanState = health.canarySessionPlan.state.name,
            canarySessionPlanRecommendation = health.canarySessionPlan.recommendation.name,
            canarySessionPlanManualStartAllowed = health.canarySessionPlan.manualSessionStartAllowed,
            canaryApprovalRequestSummary = health.canaryApprovalRequest.summary(),
            canaryApprovalRequestState = health.canaryApprovalRequest.state.name,
            canaryApprovalRequestRecommendation = health.canaryApprovalRequest.recommendation.name,
            canaryApprovalRequestReady = health.canaryApprovalRequest.approvalReady,
            canaryApprovalGateSummary = health.canaryApprovalGate.summary(),
            canaryApprovalGateState = health.canaryApprovalGate.state.name,
            canaryApprovalGateRecommendation = health.canaryApprovalGate.recommendation.name,
            canaryApprovalGateApprovalGranted = health.canaryApprovalGate.approvalGranted,
            canaryGrantPlanSummary = health.canaryGrantPlan.summary(),
            canaryGrantPlanState = health.canaryGrantPlan.state.name,
            canaryGrantPlanRecommendation = health.canaryGrantPlan.recommendation.name,
            canaryGrantPlanGrantIssued = health.canaryGrantPlan.grantIssued,
            canarySessionStartPlanSummary = health.canarySessionStartPlan.summary(),
            canarySessionStartPlanState = health.canarySessionStartPlan.state.name,
            canarySessionStartPlanRecommendation = health.canarySessionStartPlan.recommendation.name,
            canarySessionStartPlanStartReady = health.canarySessionStartPlan.startReady,
            canarySessionLeasePlanSummary = health.canarySessionLeasePlan.summary(),
            canarySessionLeasePlanState = health.canarySessionLeasePlan.state.name,
            canarySessionLeasePlanRecommendation = health.canarySessionLeasePlan.recommendation.name,
            canarySessionLeasePlanLeaseCreated = health.canarySessionLeasePlan.leaseCreated,
            canaryEnforcementPlanSummary = health.canaryEnforcementPlan.summary(),
            canaryEnforcementPlanState = health.canaryEnforcementPlan.state.name,
            canaryEnforcementPlanRecommendation = health.canaryEnforcementPlan.recommendation.name,
            canaryEnforcementPlanActualEnforcementCount = health.canaryEnforcementPlan.actualEnforcementCount,
            canaryRollbackPlanSummary = health.canaryRollbackPlan.summary(),
            canaryRollbackPlanState = health.canaryRollbackPlan.state.name,
            canaryRollbackPlanRecommendation = health.canaryRollbackPlan.recommendation.name,
            canaryRollbackPlanActualRollbackCount = health.canaryRollbackPlan.actualRollbackCount,
            canaryAuditPlanSummary = health.canaryAuditPlan.summary(),
            canaryAuditPlanState = health.canaryAuditPlan.state.name,
            canaryAuditPlanRecommendation = health.canaryAuditPlan.recommendation.name,
            canaryAuditPlanUnsafeActualActionCount = health.canaryAuditPlan.unsafeActualActionCount,
            managementTopologySummary = health.managementTopology.summary(),
            managementTopologyState = health.managementTopology.state.name,
            managementTopologyRecommendation = health.managementTopology.recommendation.name,
            managementTopologyMainlineNextFocus = health.managementTopology.mainlineNextFocus,
            primaryMetricsPid = health.primaryMetricsPid,
            legacyContainerPid = health.legacyContainerPid,
            containerLastError = health.containerLastError,
            supervisordSummary = health.supervisordServices?.toDiagnosticSummary(),
            roots = health.roots.map { root -> root.toDiagnostic(now) },
            services = services,
            endpoints = endpoints,
            processSnapshotAgeMs = health.processSnapshotRefreshedAt.toAgeMs(now),
            overviewAgeMs = health.overviewRefreshedAt.toAgeMs(now),
            reconciledAgeMs = health.reconciledAt.toAgeMs(now)
        )
    }

    fun statusText(
        health: RuntimeHealthSnapshot = RuntimeHealthStore.snapshot.value
    ): String = from(health).toStatusText()

    private fun RuntimeRootSnapshot.toDiagnostic(now: Long): RuntimeRootDiagnostic {
        val delayMs = nextRestartAllowedAt
            ?.let { (it - now).coerceAtLeast(0L) }
        return RuntimeRootDiagnostic(
            owner = ownerKind.name,
            ownerId = ownerId,
            title = title,
            reality = reality.name,
            status = statusLabel,
            observedPid = observedPid,
            expectedPid = expectedPid,
            processCount = processCount,
            rssKb = rssKb,
            retentionClass = retentionClass.name,
            resident = resident,
            reclaimPriority = reclaimPriority,
            autoReclaimAllowed = autoReclaimAllowed,
            classificationSource = classificationSource,
            classificationReason = classificationReason,
            oomScoreAdj = maxOomScoreAdj,
            activeOwner = isActiveOwner,
            staleReason = staleReason,
            restartPolicy = restartPolicyLabel,
            restartFailureCount = restartFailureCount,
            restartDelayMs = delayMs,
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
            lastError = lastError
        )
    }

    private fun SupervisordServiceHealthSnapshot.toDiagnosticSummary(): String {
        return "health=${overallHealth.name},degraded=$degraded,failed=$failedServiceCount,source=$collectionSource,exit=${commandExitCode ?: "--"},summary=${diagnosticSummary.compact(140)}"
    }
}

private fun Long.toAgeMs(now: Long): Long? {
    if (this <= 0L) return null
    return (now - this).coerceAtLeast(0L)
}

private fun String.compact(maxLength: Int): String {
    val compacted = replace(Regex("\\s+"), " ").trim()
    return if (compacted.length <= maxLength) {
        compacted
    } else {
        compacted.take(maxLength - 3) + "..."
    }
}

private fun formatAge(ageMs: Long): String {
    return when {
        ageMs < 1_000L -> "${ageMs}ms"
        ageMs < 60_000L -> "${ageMs / 1_000L}s"
        else -> "${ageMs / 60_000L}m"
    }
}

private fun formatKb(kb: Long): String {
    return when {
        kb <= 0L -> "0"
        kb < 1024L -> "${kb}KB"
        else -> "${kb / 1024L}MB"
    }
}
