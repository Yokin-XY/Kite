package com.kftest.app.foundation.runtime

import com.kftest.app.foundation.service.RuntimeRetentionClass

data class RuntimeWorkloadRegistryEntry(
    val workloadId: String,
    val title: String,
    val rootPid: Int?,
    val ownerKind: RuntimeRootOwnerKind,
    val owner: RuntimeWorkloadOwner,
    val workloadClass: RuntimeWorkloadClass,
    val retention: RuntimeWorkloadRetention,
    val suggestedLane: RuntimeLaneKind,
    val processCount: Int,
    val rssKb: Long,
    val vmSizeKb: Long,
    val cpuTimeTicks: Long,
    val ioReadBytes: Long,
    val ioWriteBytes: Long,
    val lastSeenAt: Long?,
    val lastStartedAt: Long?,
    val restartFailureCount: Int,
    val backgroundAllowed: Boolean,
    val restartAllowed: Boolean,
    val maxChildren: Int,
    val overChildBudget: Boolean,
    val source: String,
    val reason: String
) {
    fun compact(): String {
        val pid = rootPid?.toString() ?: "none"
        val over = if (overChildBudget) ",over_children=1" else ""
        return "$workloadId:$workloadClass:$retention:lane=$suggestedLane,pid=$pid,children=$processCount/$maxChildren$over"
    }
}

data class RuntimeWorkloadRegistrySnapshot(
    val mode: String = "workload_registry_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val policyStatus: String = "not_loaded",
    val policyPath: String? = null,
    val policyAuthority: String = "android_control_plane",
    val telemetrySource: String = "unknown",
    val totalWorkloads: Int = 0,
    val keepCount: Int = 0,
    val leaseCount: Int = 0,
    val cleanupCandidateCount: Int = 0,
    val quarantineCount: Int = 0,
    val systemCoreCount: Int = 0,
    val pinnedServiceCount: Int = 0,
    val interactiveCount: Int = 0,
    val buildCount: Int = 0,
    val probeCount: Int = 0,
    val ephemeralCount: Int = 0,
    val strayCount: Int = 0,
    val unknownCount: Int = 0,
    val unattributedCount: Int = 0,
    val overChildBudgetCount: Int = 0,
    val telemetryLiveTracees: Int = 0,
    val matchedRootTracees: Int = 0,
    val unassignedLiveTracees: Int = 0,
    val unassignedBuildTracees: Int = 0,
    val unassignedProbeTracees: Int = 0,
    val unassignedEphemeralTracees: Int = 0,
    val unassignedStrayTracees: Int = 0,
    val unassignedClassifiedTracees: Int = 0,
    val unassignedClassificationSource: String = "none",
    val unassignedLastEventAtMs: Long = 0L,
    val boundary: String = "observe_only_no_cleanup_no_restart_no_quarantine",
    val entries: List<RuntimeWorkloadRegistryEntry> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode status=$policyStatus total=$totalWorkloads keep=$keepCount lease=$leaseCount " +
            "cleanup=$cleanupCandidateCount stray=$strayCount unassignedTracees=$unassignedLiveTracees " +
            "overChildren=$overChildBudgetCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxEntries: Int = 8): String {
        return buildString {
            appendLine("workload_registry_mode=${mode.toRuntimeWorkloadEnvValue()}")
            appendLine("workload_registry_enforcement_mode=${enforcementMode.toRuntimeWorkloadEnvValue()}")
            appendLine("workload_registry_enforcement_enabled=$enforcementEnabled")
            appendLine("workload_registry_generated_at=$generatedAtMs")
            appendLine("workload_registry_policy_status=${policyStatus.toRuntimeWorkloadEnvValue()}")
            appendLine("workload_registry_policy_path=${policyPath.toRuntimeWorkloadEnvValue()}")
            appendLine("workload_registry_policy_authority=${policyAuthority.toRuntimeWorkloadEnvValue()}")
            appendLine("workload_registry_telemetry_source=${telemetrySource.toRuntimeWorkloadEnvValue()}")
            appendLine("workload_registry_total=$totalWorkloads")
            appendLine("workload_registry_keep_count=$keepCount")
            appendLine("workload_registry_lease_count=$leaseCount")
            appendLine("workload_registry_cleanup_candidate_count=$cleanupCandidateCount")
            appendLine("workload_registry_quarantine_count=$quarantineCount")
            appendLine("workload_registry_system_core_count=$systemCoreCount")
            appendLine("workload_registry_pinned_service_count=$pinnedServiceCount")
            appendLine("workload_registry_interactive_count=$interactiveCount")
            appendLine("workload_registry_build_count=$buildCount")
            appendLine("workload_registry_probe_count=$probeCount")
            appendLine("workload_registry_ephemeral_count=$ephemeralCount")
            appendLine("workload_registry_stray_count=$strayCount")
            appendLine("workload_registry_unknown_count=$unknownCount")
            appendLine("workload_registry_unattributed_count=$unattributedCount")
            appendLine("workload_registry_over_child_budget_count=$overChildBudgetCount")
            appendLine("workload_registry_telemetry_live_tracees=$telemetryLiveTracees")
            appendLine("workload_registry_matched_root_tracees=$matchedRootTracees")
            appendLine("workload_registry_unassigned_live_tracees=$unassignedLiveTracees")
            appendLine("workload_registry_unassigned_build_tracees=$unassignedBuildTracees")
            appendLine("workload_registry_unassigned_probe_tracees=$unassignedProbeTracees")
            appendLine("workload_registry_unassigned_ephemeral_tracees=$unassignedEphemeralTracees")
            appendLine("workload_registry_unassigned_stray_tracees=$unassignedStrayTracees")
            appendLine("workload_registry_unassigned_classified_tracees=$unassignedClassifiedTracees")
            appendLine("workload_registry_unassigned_classification_source=${unassignedClassificationSource.toRuntimeWorkloadEnvValue()}")
            appendLine("workload_registry_unassigned_last_event_at=$unassignedLastEventAtMs")
            appendLine("workload_registry_boundary=${boundary.toRuntimeWorkloadEnvValue()}")
            entries.take(maxEntries).forEachIndexed { index, entry ->
                val ordinal = index + 1
                appendLine("workload_${ordinal}_id=${entry.workloadId.toRuntimeWorkloadEnvValue()}")
                appendLine("workload_${ordinal}_class=${entry.workloadClass.name}")
                appendLine("workload_${ordinal}_owner=${entry.owner.name}")
                appendLine("workload_${ordinal}_retention=${entry.retention.name}")
                appendLine("workload_${ordinal}_lane=${entry.suggestedLane.name}")
                appendLine("workload_${ordinal}_pid=${entry.rootPid ?: 0}")
                appendLine("workload_${ordinal}_children=${entry.processCount}")
                appendLine("workload_${ordinal}_max_children=${entry.maxChildren}")
                appendLine("workload_${ordinal}_over_child_budget=${entry.overChildBudget}")
                appendLine("workload_${ordinal}_rss_kb=${entry.rssKb}")
                appendLine("workload_${ordinal}_cpu_time_ticks=${entry.cpuTimeTicks}")
                appendLine("workload_${ordinal}_io_read_bytes=${entry.ioReadBytes}")
                appendLine("workload_${ordinal}_io_write_bytes=${entry.ioWriteBytes}")
                appendLine("workload_${ordinal}_last_seen_at=${entry.lastSeenAt ?: 0L}")
                appendLine("workload_${ordinal}_last_started_at=${entry.lastStartedAt ?: 0L}")
                appendLine("workload_${ordinal}_source=${entry.source.toRuntimeWorkloadEnvValue()}")
                appendLine("workload_${ordinal}_reason=${entry.reason.toRuntimeWorkloadEnvValue()}")
            }
        }
    }
}

object RuntimeWorkloadRegistry {
    fun evaluate(
        roots: List<RuntimeRootSnapshot>,
        prootTelemetry: ProotTelemetrySnapshot,
        policy: RuntimeWorkloadPolicy
    ): RuntimeWorkloadRegistrySnapshot {
        val envelopes = policy.envelopes.associateBy { it.workloadClass }
        val entries = roots.map { root ->
            root.toRegistryEntry(envelopes)
        }
        val liveTraceeEntries = prootTelemetry.processLiveTable.entries
            .filter { it.state == ProotLiveProcessState.RUNNING }
        val liveTraceePids = liveTraceeEntries
            .mapTo(mutableSetOf()) { it.traceePid }
        val rootPids = roots.mapNotNull { it.observedPid }.toSet()
        val matchedRootTracees = liveTraceePids.count { it in rootPids }
        val unassignedTracees = liveTraceeEntries.filter { it.traceePid !in rootPids }

        val unassignedClassifications = unassignedTracees.map { it.syntheticWorkloadClass() }
        val classifiedTracees = unassignedClassifications.count {
            it != RuntimeWorkloadClass.STRAY
        }

        return RuntimeWorkloadRegistrySnapshot(
            generatedAtMs = System.currentTimeMillis(),
            policyStatus = policy.loadStatus,
            policyPath = policy.policyPath,
            policyAuthority = policy.authority,
            telemetrySource = policy.telemetrySource,
            totalWorkloads = entries.size,
            keepCount = entries.count { it.retention == RuntimeWorkloadRetention.KEEP },
            leaseCount = entries.count { it.retention == RuntimeWorkloadRetention.LEASE },
            cleanupCandidateCount = entries.count { it.retention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE },
            quarantineCount = entries.count { it.retention == RuntimeWorkloadRetention.QUARANTINE },
            systemCoreCount = entries.count { it.workloadClass == RuntimeWorkloadClass.SYSTEM_CORE },
            pinnedServiceCount = entries.count { it.workloadClass == RuntimeWorkloadClass.PINNED_SERVICE },
            interactiveCount = entries.count { it.workloadClass == RuntimeWorkloadClass.INTERACTIVE },
            buildCount = entries.count { it.workloadClass == RuntimeWorkloadClass.BUILD },
            probeCount = entries.count { it.workloadClass == RuntimeWorkloadClass.PROBE },
            ephemeralCount = entries.count { it.workloadClass == RuntimeWorkloadClass.EPHEMERAL },
            strayCount = entries.count { it.workloadClass == RuntimeWorkloadClass.STRAY },
            unknownCount = entries.count { it.workloadClass == RuntimeWorkloadClass.UNKNOWN },
            unattributedCount = entries.count { it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED },
            overChildBudgetCount = entries.count { it.overChildBudget },
            telemetryLiveTracees = prootTelemetry.processLiveTable.liveTraceeCount,
            matchedRootTracees = matchedRootTracees,
            unassignedLiveTracees = unassignedTracees.size,
            unassignedBuildTracees = unassignedClassifications.count { it == RuntimeWorkloadClass.BUILD },
            unassignedProbeTracees = unassignedClassifications.count { it == RuntimeWorkloadClass.PROBE },
            unassignedEphemeralTracees = unassignedClassifications.count { it == RuntimeWorkloadClass.EPHEMERAL },
            unassignedStrayTracees = unassignedClassifications.count { it == RuntimeWorkloadClass.STRAY },
            unassignedClassifiedTracees = classifiedTracees,
            unassignedClassificationSource = if (unassignedTracees.isEmpty()) {
                "none"
            } else {
                "proot_source_hook_and_cost_level_hints"
            },
            unassignedLastEventAtMs = unassignedTracees.maxOfOrNull { it.lastSeenAtMs } ?: 0L,
            entries = entries.sortedWith(
                compareBy<RuntimeWorkloadRegistryEntry> { it.retention.ordinal }
                    .thenBy { it.workloadClass.ordinal }
                    .thenByDescending { it.overChildBudget }
                    .thenBy { it.workloadId }
            )
        )
    }

    private fun RuntimeRootSnapshot.toRegistryEntry(
        envelopes: Map<RuntimeWorkloadClass, RuntimeWorkloadEnvelope>
    ): RuntimeWorkloadRegistryEntry {
        val classification = classifyWorkload()
        val envelope = envelopes[classification.workloadClass]
            ?: RuntimeWorkloadPolicy.defaultEnvelopes()
                .firstOrNull { it.workloadClass == classification.workloadClass }
            ?: RuntimeWorkloadEnvelope(
                workloadClass = RuntimeWorkloadClass.UNKNOWN,
                defaultRetention = RuntimeWorkloadRetention.CLEANUP_CANDIDATE,
                backgroundAllowed = false,
                maxChildren = 0,
                maxRuntimeMs = 60_000L,
                maxIdleMs = 30_000L,
                restartAllowed = false,
                autoQuarantineAllowed = true
            )
        val overChildBudget = envelope.maxChildren > 0 && processCount > envelope.maxChildren
        return RuntimeWorkloadRegistryEntry(
            workloadId = ownershipKey,
            title = title,
            rootPid = observedPid ?: expectedPid,
            ownerKind = ownerKind,
            owner = classification.owner,
            workloadClass = classification.workloadClass,
            retention = classification.retention ?: envelope.defaultRetention,
            suggestedLane = classification.lane,
            processCount = processCount,
            rssKb = rssKb,
            vmSizeKb = vmSizeKb,
            cpuTimeTicks = cpuTimeTicks,
            ioReadBytes = ioReadBytes,
            ioWriteBytes = ioWriteBytes,
            lastSeenAt = lastSeenAt,
            lastStartedAt = lastStartedAt,
            restartFailureCount = restartFailureCount,
            backgroundAllowed = envelope.backgroundAllowed,
            restartAllowed = envelope.restartAllowed,
            maxChildren = envelope.maxChildren,
            overChildBudget = overChildBudget,
            source = classificationSource.ifBlank { classification.source },
            reason = classification.reason
        )
    }

    private fun RuntimeRootSnapshot.classifyWorkload(): WorkloadClassification {
        if (processUnitTier == RuntimeProcessUnitTier.SYSTEM_CORE) {
            return WorkloadClassification(
                workloadClass = RuntimeWorkloadClass.SYSTEM_CORE,
                owner = RuntimeWorkloadOwner.KF,
                retention = RuntimeWorkloadRetention.KEEP,
                lane = RuntimeLaneKind.SERVICE,
                source = processUnitSource ?: "process_unit:system_core",
                reason = processUnitReason ?: "process unit marks this root as system core"
            )
        }
        if (processUnitTier == RuntimeProcessUnitTier.USER_LOCKED) {
            return WorkloadClassification(
                workloadClass = RuntimeWorkloadClass.PINNED_SERVICE,
                owner = RuntimeWorkloadOwner.SERVICE,
                retention = RuntimeWorkloadRetention.KEEP,
                lane = RuntimeLaneKind.SERVICE,
                source = processUnitSource ?: "process_unit:user_locked",
                reason = processUnitReason ?: "process unit marks this root as user locked"
            )
        }
        if (hasStrongLeaseProcessUnitAuthority()) {
            return WorkloadClassification(
                workloadClass = RuntimeWorkloadClass.EPHEMERAL,
                owner = RuntimeWorkloadOwner.LEGACY,
                retention = RuntimeWorkloadRetention.LEASE,
                lane = RuntimeLaneKind.PROBE,
                source = "process_unit:lease",
                reason = "strong process unit match grants ordinary lease observation"
            )
        }
        return when (ownerKind) {
            RuntimeRootOwnerKind.TERMINAL -> WorkloadClassification(
                workloadClass = RuntimeWorkloadClass.INTERACTIVE,
                owner = RuntimeWorkloadOwner.USER,
                retention = RuntimeWorkloadRetention.KEEP,
                lane = RuntimeLaneKind.INTERACTIVE,
                source = "builtin:terminal",
                reason = "terminal sessions are interactive workloads"
            )
            RuntimeRootOwnerKind.CARD,
            RuntimeRootOwnerKind.RESOURCE -> WorkloadClassification(
                workloadClass = RuntimeWorkloadClass.INTERACTIVE,
                owner = RuntimeWorkloadOwner.USER,
                retention = RuntimeWorkloadRetention.KEEP,
                lane = RuntimeLaneKind.INTERACTIVE,
                source = "proot_owner_index:${ownerKind.name.lowercase()}",
                reason = "PRoot owner index binds this Ubuntu process group to a Kite owner container"
            )
            RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> classifyBackgroundRuntime()
            RuntimeRootOwnerKind.UNATTRIBUTED -> classifyUnattributed()
        }
    }

    private fun RuntimeRootSnapshot.hasStrongLeaseProcessUnitAuthority(): Boolean {
        return processUnitTier == RuntimeProcessUnitTier.LEASE &&
            processUnitAllowReclaim &&
            processUnitMatchState != RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS &&
            processUnitMatchSource in setOf(
                RuntimeProcessUnitMatchSource.RUNTIME_ID,
                RuntimeProcessUnitMatchSource.PID_FILE,
                RuntimeProcessUnitMatchSource.PROCESS_GROUP,
                RuntimeProcessUnitMatchSource.COMMAND_EXACT
            )
    }

    private fun RuntimeRootSnapshot.classifyBackgroundRuntime(): WorkloadClassification {
        return when (retentionClass) {
            RuntimeRetentionClass.CRITICAL_CORE -> WorkloadClassification(
                RuntimeWorkloadClass.SYSTEM_CORE,
                RuntimeWorkloadOwner.KF,
                RuntimeWorkloadRetention.KEEP,
                RuntimeLaneKind.SERVICE,
                "retention:critical_core",
                "critical runtime is treated as system core"
            )
            RuntimeRetentionClass.RESIDENT -> WorkloadClassification(
                RuntimeWorkloadClass.PINNED_SERVICE,
                RuntimeWorkloadOwner.SERVICE,
                RuntimeWorkloadRetention.KEEP,
                RuntimeLaneKind.SERVICE,
                "retention:resident",
                "resident runtime is treated as pinned service"
            )
            RuntimeRetentionClass.INTERACTIVE -> WorkloadClassification(
                RuntimeWorkloadClass.INTERACTIVE,
                RuntimeWorkloadOwner.USER,
                RuntimeWorkloadRetention.KEEP,
                RuntimeLaneKind.INTERACTIVE,
                "retention:interactive",
                "interactive runtime keeps foreground semantics"
            )
            RuntimeRetentionClass.BATCH -> classifyCommandLikeBatch(RuntimeWorkloadOwner.USER)
            RuntimeRetentionClass.EPHEMERAL -> WorkloadClassification(
                RuntimeWorkloadClass.EPHEMERAL,
                RuntimeWorkloadOwner.LEGACY,
                RuntimeWorkloadRetention.LEASE,
                RuntimeLaneKind.PROBE,
                "retention:ephemeral",
                "ephemeral runtime receives a lease"
            )
            RuntimeRetentionClass.UNKNOWN -> WorkloadClassification(
                RuntimeWorkloadClass.UNKNOWN,
                RuntimeWorkloadOwner.UNKNOWN,
                RuntimeWorkloadRetention.CLEANUP_CANDIDATE,
                RuntimeLaneKind.PROBE,
                "retention:unknown",
                "unknown runtime remains cleanup candidate until declared"
            )
        }
    }

    private fun RuntimeRootSnapshot.classifyUnattributed(): WorkloadClassification {
        if (resident) {
            return WorkloadClassification(
                RuntimeWorkloadClass.PINNED_SERVICE,
                RuntimeWorkloadOwner.LEGACY,
                RuntimeWorkloadRetention.KEEP,
                RuntimeLaneKind.SERVICE,
                "unknown_rule:resident",
                "unknown-process rule marked this root as resident"
            )
        }
        return classifyCommandLikeBatch(RuntimeWorkloadOwner.UNKNOWN).copy(
            source = "unattributed_proot_root_default_lease",
            reason = "ordinary PRoot-observed Ubuntu root enters the lowest-priority lease pool"
        )
    }

    private fun RuntimeRootSnapshot.classifyCommandLikeBatch(owner: RuntimeWorkloadOwner): WorkloadClassification {
        val text = "$title $commandLine".lowercase()
        return when {
            text.hasAnyWorkloadMarker(BUILD_MARKERS) ->
                WorkloadClassification(
                    RuntimeWorkloadClass.BUILD,
                    owner,
                    RuntimeWorkloadRetention.LEASE,
                    RuntimeLaneKind.BUILD,
                    "heuristic:build_command",
                    "batch command looks like a build or install workload"
                )
            text.hasAnyWorkloadMarker(PROBE_MARKERS) ->
                WorkloadClassification(
                    RuntimeWorkloadClass.PROBE,
                    owner,
                    RuntimeWorkloadRetention.LEASE,
                    RuntimeLaneKind.PROBE,
                    "heuristic:probe_command",
                    "batch command looks like a probe or health check"
                )
            else ->
                WorkloadClassification(
                    RuntimeWorkloadClass.EPHEMERAL,
                    owner,
                    RuntimeWorkloadRetention.LEASE,
                    RuntimeLaneKind.PROBE,
                    "retention:batch",
                    "batch runtime receives a bounded lease"
                )
        }
    }

    private data class WorkloadClassification(
        val workloadClass: RuntimeWorkloadClass,
        val owner: RuntimeWorkloadOwner,
        val retention: RuntimeWorkloadRetention?,
        val lane: RuntimeLaneKind,
        val source: String,
        val reason: String
    )
}

private fun ProotLiveProcessEntry.syntheticWorkloadClass(): RuntimeWorkloadClass {
    val marker = "${lastSourceHook}_${lastCostLevel}".lowercase()
    return when {
        marker.hasAnyWorkloadMarker(BUILD_MARKERS) -> RuntimeWorkloadClass.BUILD
        marker.hasAnyWorkloadMarker(PROBE_MARKERS) -> RuntimeWorkloadClass.PROBE
        marker.hasAnyWorkloadMarker(EPHEMERAL_MARKERS) -> RuntimeWorkloadClass.EPHEMERAL
        else -> RuntimeWorkloadClass.STRAY
    }
}

private val BUILD_MARKERS = listOf(
    "gradle",
    "assemble",
    "build",
    "compile",
    "compiler",
    "javac",
    "kotlinc",
    "cmake",
    "ninja",
    "make ",
    "make_",
    "cargo",
    "go_build",
    "mvn",
    "webpack",
    "vite_build",
    "npm_install",
    "npm install",
    "pnpm_install",
    "yarn_install",
    "pip_install",
    "pip install",
    "apt ",
    "apt_",
    "apt-get",
    "install"
)

private val PROBE_MARKERS = listOf(
    "probe",
    "doctor",
    "health",
    "check",
    "monitor",
    "diagnose",
    "diagnostic",
    "lint",
    "test",
    "pytest",
    "jest",
    "unit_test",
    "smoke"
)

private val EPHEMERAL_MARKERS = listOf(
    "ephemeral",
    "lease",
    "temp",
    "tmp",
    "batch",
    "one_shot",
    "oneshot",
    "short_task",
    "quick"
)

private fun String.hasAnyWorkloadMarker(markers: List<String>): Boolean {
    return markers.any { marker -> marker in this }
}

private fun String?.toRuntimeWorkloadEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/-]"), "_")
        .take(160)
}
