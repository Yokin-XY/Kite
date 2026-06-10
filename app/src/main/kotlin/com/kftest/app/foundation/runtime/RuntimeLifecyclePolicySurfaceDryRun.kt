package com.kftest.app.foundation.runtime

enum class RuntimeLifecyclePolicySurfaceState {
    WORKSPACE_MISSING,
    DEFAULT_ACTIVE,
    USER_POLICY_LOADED,
    POLICY_ERROR_DEFAULT
}

enum class RuntimeLifecyclePolicySurfaceRecommendation {
    WAIT_FOR_WORKSPACE,
    KEEP_DEFAULT_POLICY,
    REVIEW_USER_POLICY,
    REPAIR_POLICY_JSON
}

data class RuntimeLifecyclePolicySurfaceDryRunSnapshot(
    val mode: String = "ubuntu_lifecycle_policy_surface_observe_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeLifecyclePolicySurfaceState =
        RuntimeLifecyclePolicySurfaceState.WORKSPACE_MISSING,
    val recommendation: RuntimeLifecyclePolicySurfaceRecommendation =
        RuntimeLifecyclePolicySurfaceRecommendation.WAIT_FOR_WORKSPACE,
    val authority: String = "android_control_plane",
    val telemetrySource: String = "unknown",
    val policyPath: String? = null,
    val loadStatus: String = "unknown",
    val loadError: String? = null,
    val androidExecutionOwner: Boolean = true,
    val ubuntuPolicyAdvisory: Boolean = true,
    val ubuntuDirectExecutionAllowed: Boolean = false,
    val prootPolicyExecutionAllowed: Boolean = false,
    val policyEditableFromUbuntu: Boolean = false,
    val safetyFloorApplied: Boolean = false,
    val safetyFloorStatus: String = "none",
    val safetyFloorAddedLaneCount: Int = 0,
    val safetyFloorAddedEnvelopeCount: Int = 0,
    val safetyFloorAddedBudgetStateCount: Int = 0,
    val safetyFloorAddedEnvelopeClasses: List<RuntimeWorkloadClass> = emptyList(),
    val policyVersion: Int = 1,
    val lifecycleManagementEnabled: Boolean = false,
    val lifecycleStrategyGroup: String = "balanced_default",
    val lanePolicyCount: Int = 0,
    val envelopePolicyCount: Int = 0,
    val budgetStatePolicyCount: Int = 0,
    val keepRetentionCount: Int = 0,
    val leaseRetentionCount: Int = 0,
    val cleanupRetentionCount: Int = 0,
    val quarantineRetentionCount: Int = 0,
    val backgroundAllowedEnvelopeCount: Int = 0,
    val restartAllowedEnvelopeCount: Int = 0,
    val autoQuarantineAllowedEnvelopeCount: Int = 0,
    val serialLaneCount: Int = 0,
    val burstLaneCount: Int = 0,
    val backgroundZeroLaneCount: Int = 0,
    val backgroundDecayGraceMs: Long = 0L,
    val backgroundDecayPressureAccelerates: Boolean = false,
    val repeatOffenderMaxRestartsInWindow: Int = 0,
    val repeatOffenderMaxViolationsInWindow: Int = 0,
    val lifecycleLeaseForegroundBudgetPercent: Int = 0,
    val lifecycleLeaseHiddenBudgetPercent: Int = 0,
    val lifecycleLeaseBackgroundPressureBudgetPercent: Int = 0,
    val lifecycleLeaseLowMemoryBudgetPercent: Int = 0,
    val lifecycleLeasePressureBudgetPercent: Int = 0,
    val lifecycleLeaseSettlementTickMs: Long = 0L,
    val lifecycleLeaseMemorySampleTickMs: Long = 0L,
    val lifecycleLeaseMemoryPressureSampleAvailablePercent: Int = 0,
    val lifecycleLeaseMemoryPressureSampleCooldownMs: Long = 0L,
    val lifecycleLeaseMemoryPressureImmediateSettlement: Boolean = false,
    val lifecycleLeaseActiveTtlMs: Long = 0L,
    val lifecycleLeaseWeakActivityTtlMs: Long = 0L,
    val lifecycleLeaseCoolingTtlMs: Long = 0L,
    val lifecycleLeaseMaxTotalMs: Long = 0L,
    val lifecycleLeaseCpuStrongDeltaTicks: Long = 0L,
    val lifecycleLeaseCpuWeakDeltaTicks: Long = 0L,
    val lifecycleLeaseIoStrongDeltaBytes: Long = 0L,
    val lifecycleLeaseIoWeakDeltaBytes: Long = 0L,
    val lifecycleLeaseRssMinDeltaKb: Long = 0L,
    val lifecycleLeaseRssDeltaPercent: Int = 0,
    val lifecycleLeaseInitialMs: Long = 0L,
    val lifecycleLeaseMemoryMaxExtensionMs: Long = 0L,
    val lifecycleLeaseRssStrongDeltaKb: Long = 0L,
    val lifecycleLeaseRssStrongDeltaPercent: Int = 0,
    val lifecycleLeaseProcessTreeBonusMs: Long = 0L,
    val lifecycleLeaseForegroundBonusMs: Long = 0L,
    val lifecycleLeaseCpuBonusMs: Long = 0L,
    val lifecycleLeaseNetworkLikelyBonusMs: Long = 0L,
    val lifecycleLeaseMaxExtensionPerSettlementMs: Long = 0L,
    val lifecycleLeaseExpiredGraceMs: Long = 0L,
    val lifecycleLeaseMaxTotalUnlockAverageScorePercent: Int = 0,
    val lifecycleLeaseMaxTotalUnlockLatestScorePercent: Int = 0,
    val lifecycleLeaseMaxTotalUnlockInitialMs: Long = 0L,
    val lifecycleLeaseTrustedActivityExtensionMs: Long = 0L,
    val lifecycleLeaseStrongActivityExtensionMs: Long = 0L,
    val lifecycleLeaseMultiEvidenceActivityExtensionMs: Long = 0L,
    val lifecycleLeaseHighCostUpgradeMs: Long = 0L,
    val lifecycleLeaseHighCostRssKb: Long = 0L,
    val lifecycleLeaseHighCostPoolBudgetPercent: Int = 0,
    val lifecycleLeaseCpuTicksPerSecond: Long = 0L,
    val lifecycleLeaseCpuWeakPercent: Int = 0,
    val lifecycleLeaseCpuTrustedPercent: Int = 0,
    val lifecycleLeaseCpuStrongPercent: Int = 0,
    val lifecycleLeaseMaxTotalUnlockActiveSamplePercent: Int = 0,
    val lanes: List<RuntimeLanePolicy> = emptyList(),
    val envelopes: List<RuntimeWorkloadEnvelope> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation authority=$authority " +
            "loadStatus=$loadStatus path=${policyPath ?: "none"} androidOwner=$androidExecutionOwner " +
            "ubuntuAdvisory=$ubuntuPolicyAdvisory ubuntuDirect=$ubuntuDirectExecutionAllowed " +
            "safetyFloor=$safetyFloorStatus addedEnvelopes=$safetyFloorAddedEnvelopeCount " +
            "lanes=$lanePolicyCount envelopes=$envelopePolicyCount budgetStates=$budgetStatePolicyCount " +
            "lifecycleEnabled=$lifecycleManagementEnabled strategyGroup=$lifecycleStrategyGroup " +
            "keep=$keepRetentionCount lease=$leaseRetentionCount cleanup=$cleanupRetentionCount " +
            "serialLanes=$serialLaneCount burstLanes=$burstLaneCount " +
            "leasePoolBudget=${lifecycleLeaseForegroundBudgetPercent}/${lifecycleLeaseHiddenBudgetPercent}/" +
            "$lifecycleLeasePressureBudgetPercent leaseMaxMs=$lifecycleLeaseMaxTotalMs " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxLanes: Int = 8, maxEnvelopes: Int = 12): String {
        return buildString {
            appendLine("lifecycle_policy_surface_mode=${mode.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_enforcement_mode=${enforcementMode.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_enforcement_enabled=$enforcementEnabled")
            appendLine("lifecycle_policy_surface_generated_at=$generatedAtMs")
            appendLine("lifecycle_policy_surface_state=${state.name}")
            appendLine("lifecycle_policy_surface_recommendation=${recommendation.name}")
            appendLine("lifecycle_policy_surface_authority=${authority.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_telemetry_source=${telemetrySource.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_policy_path=${policyPath.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_load_status=${loadStatus.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_load_error=${loadError.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_android_execution_owner=$androidExecutionOwner")
            appendLine("lifecycle_policy_surface_ubuntu_policy_advisory=$ubuntuPolicyAdvisory")
            appendLine("lifecycle_policy_surface_ubuntu_direct_execution_allowed=$ubuntuDirectExecutionAllowed")
            appendLine("lifecycle_policy_surface_proot_policy_execution_allowed=$prootPolicyExecutionAllowed")
            appendLine("lifecycle_policy_surface_policy_editable_from_ubuntu=$policyEditableFromUbuntu")
            appendLine("lifecycle_policy_surface_safety_floor_applied=$safetyFloorApplied")
            appendLine("lifecycle_policy_surface_safety_floor_status=${safetyFloorStatus.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_safety_floor_added_lane_count=$safetyFloorAddedLaneCount")
            appendLine("lifecycle_policy_surface_safety_floor_added_envelope_count=$safetyFloorAddedEnvelopeCount")
            appendLine("lifecycle_policy_surface_safety_floor_added_budget_state_count=$safetyFloorAddedBudgetStateCount")
            appendLine(
                "lifecycle_policy_surface_safety_floor_added_envelope_classes=" +
                    safetyFloorAddedEnvelopeClasses.joinToString(",") { it.name }
                        .toLifecyclePolicySurfaceEnvValue()
            )
            appendLine("lifecycle_policy_surface_policy_version=$policyVersion")
            appendLine("lifecycle_policy_surface_lifecycle_management_enabled=$lifecycleManagementEnabled")
            appendLine("lifecycle_policy_surface_strategy_group=${lifecycleStrategyGroup.toLifecyclePolicySurfaceEnvValue()}")
            appendLine("lifecycle_policy_surface_lane_policy_count=$lanePolicyCount")
            appendLine("lifecycle_policy_surface_envelope_policy_count=$envelopePolicyCount")
            appendLine("lifecycle_policy_surface_budget_state_policy_count=$budgetStatePolicyCount")
            appendLine("lifecycle_policy_surface_keep_retention_count=$keepRetentionCount")
            appendLine("lifecycle_policy_surface_lease_retention_count=$leaseRetentionCount")
            appendLine("lifecycle_policy_surface_cleanup_retention_count=$cleanupRetentionCount")
            appendLine("lifecycle_policy_surface_quarantine_retention_count=$quarantineRetentionCount")
            appendLine("lifecycle_policy_surface_background_allowed_envelope_count=$backgroundAllowedEnvelopeCount")
            appendLine("lifecycle_policy_surface_restart_allowed_envelope_count=$restartAllowedEnvelopeCount")
            appendLine("lifecycle_policy_surface_auto_quarantine_allowed_envelope_count=$autoQuarantineAllowedEnvelopeCount")
            appendLine("lifecycle_policy_surface_serial_lane_count=$serialLaneCount")
            appendLine("lifecycle_policy_surface_burst_lane_count=$burstLaneCount")
            appendLine("lifecycle_policy_surface_background_zero_lane_count=$backgroundZeroLaneCount")
            appendLine("lifecycle_policy_surface_background_decay_grace_ms=$backgroundDecayGraceMs")
            appendLine("lifecycle_policy_surface_background_decay_pressure_accelerates=$backgroundDecayPressureAccelerates")
            appendLine("lifecycle_policy_surface_repeat_offender_max_restarts_in_window=$repeatOffenderMaxRestartsInWindow")
            appendLine("lifecycle_policy_surface_repeat_offender_max_violations_in_window=$repeatOffenderMaxViolationsInWindow")
            appendLine("lifecycle_policy_surface_lease_pool_foreground_budget_percent=$lifecycleLeaseForegroundBudgetPercent")
            appendLine("lifecycle_policy_surface_lease_pool_hidden_budget_percent=$lifecycleLeaseHiddenBudgetPercent")
            appendLine("lifecycle_policy_surface_lease_pool_background_pressure_budget_percent=$lifecycleLeaseBackgroundPressureBudgetPercent")
            appendLine("lifecycle_policy_surface_lease_pool_low_memory_budget_percent=$lifecycleLeaseLowMemoryBudgetPercent")
            appendLine("lifecycle_policy_surface_lease_pool_pressure_budget_percent=$lifecycleLeasePressureBudgetPercent")
            appendLine("lifecycle_policy_surface_lease_settlement_tick_ms=$lifecycleLeaseSettlementTickMs")
            appendLine("lifecycle_policy_surface_lease_memory_sample_tick_ms=$lifecycleLeaseMemorySampleTickMs")
            appendLine("lifecycle_policy_surface_lease_memory_pressure_sample_available_percent=$lifecycleLeaseMemoryPressureSampleAvailablePercent")
            appendLine("lifecycle_policy_surface_lease_memory_pressure_sample_cooldown_ms=$lifecycleLeaseMemoryPressureSampleCooldownMs")
            appendLine("lifecycle_policy_surface_lease_memory_pressure_immediate_settlement=$lifecycleLeaseMemoryPressureImmediateSettlement")
            appendLine("lifecycle_policy_surface_lease_active_ttl_ms=$lifecycleLeaseActiveTtlMs")
            appendLine("lifecycle_policy_surface_lease_weak_activity_ttl_ms=$lifecycleLeaseWeakActivityTtlMs")
            appendLine("lifecycle_policy_surface_lease_cooling_ttl_ms=$lifecycleLeaseCoolingTtlMs")
            appendLine("lifecycle_policy_surface_lease_max_total_ms=$lifecycleLeaseMaxTotalMs")
            appendLine("lifecycle_policy_surface_activity_cpu_strong_delta_ticks=$lifecycleLeaseCpuStrongDeltaTicks")
            appendLine("lifecycle_policy_surface_activity_cpu_weak_delta_ticks=$lifecycleLeaseCpuWeakDeltaTicks")
            appendLine("lifecycle_policy_surface_activity_io_strong_delta_bytes=$lifecycleLeaseIoStrongDeltaBytes")
            appendLine("lifecycle_policy_surface_activity_io_weak_delta_bytes=$lifecycleLeaseIoWeakDeltaBytes")
            appendLine("lifecycle_policy_surface_activity_rss_min_delta_kb=$lifecycleLeaseRssMinDeltaKb")
            appendLine("lifecycle_policy_surface_activity_rss_delta_percent=$lifecycleLeaseRssDeltaPercent")
            appendLine("lifecycle_policy_surface_lease_initial_ms=$lifecycleLeaseInitialMs")
            appendLine("lifecycle_policy_surface_lease_memory_max_extension_ms=$lifecycleLeaseMemoryMaxExtensionMs")
            appendLine("lifecycle_policy_surface_activity_rss_strong_delta_kb=$lifecycleLeaseRssStrongDeltaKb")
            appendLine("lifecycle_policy_surface_activity_rss_strong_delta_percent=$lifecycleLeaseRssStrongDeltaPercent")
            appendLine("lifecycle_policy_surface_lease_process_tree_bonus_ms=$lifecycleLeaseProcessTreeBonusMs")
            appendLine("lifecycle_policy_surface_lease_foreground_bonus_ms=$lifecycleLeaseForegroundBonusMs")
            appendLine("lifecycle_policy_surface_lease_cpu_bonus_ms=$lifecycleLeaseCpuBonusMs")
            appendLine("lifecycle_policy_surface_lease_network_likely_bonus_ms=$lifecycleLeaseNetworkLikelyBonusMs")
            appendLine("lifecycle_policy_surface_lease_max_extension_per_settlement_ms=$lifecycleLeaseMaxExtensionPerSettlementMs")
            appendLine("lifecycle_policy_surface_lease_expired_grace_ms=$lifecycleLeaseExpiredGraceMs")
            appendLine("lifecycle_policy_surface_lease_max_total_unlock_average_score_percent=$lifecycleLeaseMaxTotalUnlockAverageScorePercent")
            appendLine("lifecycle_policy_surface_lease_max_total_unlock_latest_score_percent=$lifecycleLeaseMaxTotalUnlockLatestScorePercent")
            appendLine("lifecycle_policy_surface_lease_max_total_unlock_initial_ms=$lifecycleLeaseMaxTotalUnlockInitialMs")
            appendLine("lifecycle_policy_surface_lease_trusted_activity_extension_ms=$lifecycleLeaseTrustedActivityExtensionMs")
            appendLine("lifecycle_policy_surface_lease_strong_activity_extension_ms=$lifecycleLeaseStrongActivityExtensionMs")
            appendLine("lifecycle_policy_surface_lease_multi_evidence_activity_extension_ms=$lifecycleLeaseMultiEvidenceActivityExtensionMs")
            appendLine("lifecycle_policy_surface_lease_high_cost_upgrade_ms=$lifecycleLeaseHighCostUpgradeMs")
            appendLine("lifecycle_policy_surface_lease_high_cost_rss_kb=$lifecycleLeaseHighCostRssKb")
            appendLine("lifecycle_policy_surface_lease_high_cost_pool_budget_percent=$lifecycleLeaseHighCostPoolBudgetPercent")
            appendLine("lifecycle_policy_surface_activity_cpu_ticks_per_second=$lifecycleLeaseCpuTicksPerSecond")
            appendLine("lifecycle_policy_surface_activity_cpu_weak_percent=$lifecycleLeaseCpuWeakPercent")
            appendLine("lifecycle_policy_surface_activity_cpu_trusted_percent=$lifecycleLeaseCpuTrustedPercent")
            appendLine("lifecycle_policy_surface_activity_cpu_strong_percent=$lifecycleLeaseCpuStrongPercent")
            appendLine("lifecycle_policy_surface_lease_max_total_unlock_active_sample_percent=$lifecycleLeaseMaxTotalUnlockActiveSamplePercent")
            lanes.take(maxLanes).forEachIndexed { index, lane ->
                val prefix = "lifecycle_policy_surface_lane_${index + 1}"
                appendLine("${prefix}_name=${lane.lane.name}")
                appendLine("${prefix}_max=${lane.maxConcurrency}")
                appendLine("${prefix}_background_max=${lane.backgroundMaxConcurrency}")
                appendLine("${prefix}_serial=${lane.serial}")
                appendLine("${prefix}_allow_burst=${lane.allowBurst}")
                appendLine("${prefix}_priority=${lane.priority}")
            }
            envelopes.take(maxEnvelopes).forEachIndexed { index, envelope ->
                val prefix = "lifecycle_policy_surface_envelope_${index + 1}"
                appendLine("${prefix}_class=${envelope.workloadClass.name}")
                appendLine("${prefix}_retention=${envelope.defaultRetention.name}")
                appendLine("${prefix}_background_allowed=${envelope.backgroundAllowed}")
                appendLine("${prefix}_max_children=${envelope.maxChildren}")
                appendLine("${prefix}_max_runtime_ms=${envelope.maxRuntimeMs}")
                appendLine("${prefix}_max_idle_ms=${envelope.maxIdleMs}")
                appendLine("${prefix}_restart_allowed=${envelope.restartAllowed}")
                appendLine("${prefix}_auto_quarantine_allowed=${envelope.autoQuarantineAllowed}")
            }
            appendLine("lifecycle_policy_surface_boundary=observe_only_policy_contract_no_direct_start_no_pool_resize_no_queue_no_reclaim_no_enforcement")
        }
    }
}

object RuntimeLifecyclePolicySurfaceDryRun {
    fun evaluate(
        policy: RuntimeWorkloadPolicy,
        now: Long = System.currentTimeMillis()
    ): RuntimeLifecyclePolicySurfaceDryRunSnapshot {
        val state = when (policy.loadStatus) {
            "workspace_missing" -> RuntimeLifecyclePolicySurfaceState.WORKSPACE_MISSING
            "error_default" -> RuntimeLifecyclePolicySurfaceState.POLICY_ERROR_DEFAULT
            "loaded" -> RuntimeLifecyclePolicySurfaceState.USER_POLICY_LOADED
            else -> RuntimeLifecyclePolicySurfaceState.DEFAULT_ACTIVE
        }
        return RuntimeLifecyclePolicySurfaceDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            authority = policy.authority,
            telemetrySource = policy.telemetrySource,
            policyPath = policy.policyPath,
            loadStatus = policy.loadStatus,
            loadError = policy.loadError,
            policyEditableFromUbuntu = policy.policyPath != null,
            safetyFloorApplied = policy.compatOverlayStatus != "none",
            safetyFloorStatus = policy.compatOverlayStatus,
            safetyFloorAddedLaneCount = policy.compatAddedLaneCount,
            safetyFloorAddedEnvelopeCount = policy.compatAddedEnvelopeCount,
            safetyFloorAddedBudgetStateCount = policy.compatAddedBudgetStateCount,
            safetyFloorAddedEnvelopeClasses = policy.compatAddedEnvelopeClasses,
            policyVersion = policy.version,
            lifecycleManagementEnabled = policy.lifecycleManagementEnabled,
            lifecycleStrategyGroup = policy.lifecycleStrategyGroup,
            lanePolicyCount = policy.lanes.size,
            envelopePolicyCount = policy.envelopes.size,
            budgetStatePolicyCount = policy.budgetStates.size,
            keepRetentionCount = policy.envelopes.count {
                it.defaultRetention == RuntimeWorkloadRetention.KEEP
            },
            leaseRetentionCount = policy.envelopes.count {
                it.defaultRetention == RuntimeWorkloadRetention.LEASE
            },
            cleanupRetentionCount = policy.envelopes.count {
                it.defaultRetention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE
            },
            quarantineRetentionCount = policy.envelopes.count {
                it.defaultRetention == RuntimeWorkloadRetention.QUARANTINE
            },
            backgroundAllowedEnvelopeCount = policy.envelopes.count { it.backgroundAllowed },
            restartAllowedEnvelopeCount = policy.envelopes.count { it.restartAllowed },
            autoQuarantineAllowedEnvelopeCount = policy.envelopes.count { it.autoQuarantineAllowed },
            serialLaneCount = policy.lanes.count { it.serial },
            burstLaneCount = policy.lanes.count { it.allowBurst },
            backgroundZeroLaneCount = policy.lanes.count { it.backgroundMaxConcurrency == 0 },
            backgroundDecayGraceMs = policy.backgroundDecay.graceMs,
            backgroundDecayPressureAccelerates = policy.backgroundDecay.pressureAccelerates,
            repeatOffenderMaxRestartsInWindow = policy.repeatOffender.maxRestartsInWindow,
            repeatOffenderMaxViolationsInWindow = policy.repeatOffender.maxViolationsInWindow,
            lifecycleLeaseForegroundBudgetPercent = policy.lifecycleLease.foregroundLeasePoolBudgetPercent,
            lifecycleLeaseHiddenBudgetPercent = policy.lifecycleLease.hiddenLeasePoolBudgetPercent,
            lifecycleLeaseBackgroundPressureBudgetPercent =
                policy.lifecycleLease.backgroundPressureLeasePoolBudgetPercent,
            lifecycleLeaseLowMemoryBudgetPercent = policy.lifecycleLease.lowMemoryLeasePoolBudgetPercent,
            lifecycleLeasePressureBudgetPercent = policy.lifecycleLease.pressureLeasePoolBudgetPercent,
            lifecycleLeaseSettlementTickMs = policy.lifecycleLease.settlementTickMs,
            lifecycleLeaseMemorySampleTickMs = policy.lifecycleLease.memorySampleTickMs,
            lifecycleLeaseMemoryPressureSampleAvailablePercent =
                policy.lifecycleLease.memoryPressureSampleAvailablePercent,
            lifecycleLeaseMemoryPressureSampleCooldownMs =
                policy.lifecycleLease.memoryPressureSampleCooldownMs,
            lifecycleLeaseMemoryPressureImmediateSettlement =
                policy.lifecycleLease.memoryPressureImmediateSettlement,
            lifecycleLeaseActiveTtlMs = policy.lifecycleLease.activeLeaseTtlMs,
            lifecycleLeaseWeakActivityTtlMs = policy.lifecycleLease.weakActivityLeaseTtlMs,
            lifecycleLeaseCoolingTtlMs = policy.lifecycleLease.coolingLeaseTtlMs,
            lifecycleLeaseMaxTotalMs = policy.lifecycleLease.maxTotalLeaseMs,
            lifecycleLeaseCpuStrongDeltaTicks = policy.lifecycleLease.cpuStrongDeltaTicks,
            lifecycleLeaseCpuWeakDeltaTicks = policy.lifecycleLease.cpuWeakDeltaTicks,
            lifecycleLeaseIoStrongDeltaBytes = policy.lifecycleLease.ioStrongDeltaBytes,
            lifecycleLeaseIoWeakDeltaBytes = policy.lifecycleLease.ioWeakDeltaBytes,
            lifecycleLeaseRssMinDeltaKb = policy.lifecycleLease.rssMinDeltaKb,
            lifecycleLeaseRssDeltaPercent = policy.lifecycleLease.rssDeltaPercent,
            lifecycleLeaseInitialMs = policy.lifecycleLease.initialLeaseMs,
            lifecycleLeaseMemoryMaxExtensionMs = policy.lifecycleLease.memoryMaxExtensionMs,
            lifecycleLeaseRssStrongDeltaKb = policy.lifecycleLease.rssStrongDeltaKb,
            lifecycleLeaseRssStrongDeltaPercent = policy.lifecycleLease.rssStrongDeltaPercent,
            lifecycleLeaseProcessTreeBonusMs = policy.lifecycleLease.processTreeBonusMs,
            lifecycleLeaseForegroundBonusMs = policy.lifecycleLease.foregroundBonusMs,
            lifecycleLeaseCpuBonusMs = policy.lifecycleLease.cpuBonusMs,
            lifecycleLeaseNetworkLikelyBonusMs = policy.lifecycleLease.networkLikelyBonusMs,
            lifecycleLeaseMaxExtensionPerSettlementMs = policy.lifecycleLease.maxExtensionPerSettlementMs,
            lifecycleLeaseExpiredGraceMs = policy.lifecycleLease.expiredGraceMs,
            lifecycleLeaseMaxTotalUnlockAverageScorePercent =
                policy.lifecycleLease.maxTotalUnlockAverageScorePercent,
            lifecycleLeaseMaxTotalUnlockLatestScorePercent =
                policy.lifecycleLease.maxTotalUnlockLatestScorePercent,
            lifecycleLeaseMaxTotalUnlockInitialMs = policy.lifecycleLease.maxTotalUnlockInitialMs,
            lifecycleLeaseTrustedActivityExtensionMs = policy.lifecycleLease.trustedActivityExtensionMs,
            lifecycleLeaseStrongActivityExtensionMs = policy.lifecycleLease.strongActivityExtensionMs,
            lifecycleLeaseMultiEvidenceActivityExtensionMs =
                policy.lifecycleLease.multiEvidenceActivityExtensionMs,
            lifecycleLeaseHighCostUpgradeMs = policy.lifecycleLease.highCostUpgradeMs,
            lifecycleLeaseHighCostRssKb = policy.lifecycleLease.highCostRssKb,
            lifecycleLeaseHighCostPoolBudgetPercent = policy.lifecycleLease.highCostLeasePoolBudgetPercent,
            lifecycleLeaseCpuTicksPerSecond = policy.lifecycleLease.cpuTicksPerSecond,
            lifecycleLeaseCpuWeakPercent = policy.lifecycleLease.cpuWeakPercent,
            lifecycleLeaseCpuTrustedPercent = policy.lifecycleLease.cpuTrustedPercent,
            lifecycleLeaseCpuStrongPercent = policy.lifecycleLease.cpuStrongPercent,
            lifecycleLeaseMaxTotalUnlockActiveSamplePercent =
                policy.lifecycleLease.maxTotalUnlockActiveSamplePercent,
            lanes = policy.lanes.sortedBy { it.priority },
            envelopes = policy.envelopes.sortedBy { it.workloadClass.ordinal }
        )
    }

    private fun recommendationFor(
        state: RuntimeLifecyclePolicySurfaceState
    ): RuntimeLifecyclePolicySurfaceRecommendation {
        return when (state) {
            RuntimeLifecyclePolicySurfaceState.WORKSPACE_MISSING ->
                RuntimeLifecyclePolicySurfaceRecommendation.WAIT_FOR_WORKSPACE
            RuntimeLifecyclePolicySurfaceState.DEFAULT_ACTIVE ->
                RuntimeLifecyclePolicySurfaceRecommendation.KEEP_DEFAULT_POLICY
            RuntimeLifecyclePolicySurfaceState.USER_POLICY_LOADED ->
                RuntimeLifecyclePolicySurfaceRecommendation.REVIEW_USER_POLICY
            RuntimeLifecyclePolicySurfaceState.POLICY_ERROR_DEFAULT ->
                RuntimeLifecyclePolicySurfaceRecommendation.REPAIR_POLICY_JSON
        }
    }
}

private fun String?.toLifecyclePolicySurfaceEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
