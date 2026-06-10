package com.kftest.app.foundation.runtime

enum class RuntimeLifecycleReclaimPlanState {
    FOREGROUND_KEEP,
    BACKGROUND_GRACE,
    LEASE_REVIEW,
    CLEANUP_REVIEW,
    PRESSURE_RECLAIM_REVIEW,
    QUARANTINE_REVIEW
}

enum class RuntimeLifecycleReclaimRecommendation {
    KEEP_OBSERVING,
    REVIEW_BACKGROUND_LEASES,
    REVIEW_CLEANUP_CANDIDATES,
    REVIEW_PRESSURE_RECLAIM,
    REVIEW_QUARANTINE
}

enum class RuntimeLifecycleReclaimDisposition {
    KEEP,
    WATCH_LEASE,
    WOULD_EXPIRE_LEASE,
    WOULD_CLEANUP,
    WOULD_RECLAIM_CHILDREN,
    WOULD_TERMINATE_WORKLOAD,
    WOULD_RESTART_MAIN,
    WOULD_QUARANTINE,
    MANUAL_ONLY_QUARANTINED
}

enum class RuntimeLifecycleTier {
    SYSTEM_CORE,
    USER_LOCKED,
    KEEP_ACTIVE,
    BACKGROUND_GRACE,
    LEASED_BACKGROUND,
    CLEANUP_CANDIDATE,
    QUARANTINE
}

enum class RuntimeLifecycleLayer {
    SYSTEM_CORE,
    USER_LOCKED,
    FOREGROUND_PRIORITY,
    LEASE_POOL,
    ANOMALY_POOL
}

enum class RuntimeLifecycleActivityState {
    PROTECTED,
    ACTIVE,
    WEAK_ACTIVITY,
    COOLING,
    EXPIRED,
    ABNORMAL
}

data class RuntimeLifecycleReclaimItem(
    val workloadId: String,
    val workloadClass: RuntimeWorkloadClass,
    val retention: RuntimeWorkloadRetention,
    val tier: RuntimeLifecycleTier,
    val layer: RuntimeLifecycleLayer,
    val activityState: RuntimeLifecycleActivityState,
    val lane: RuntimeLaneKind,
    val backgroundAllowed: Boolean,
    val processCount: Int,
    val rssKb: Long,
    val maxChildren: Int,
    val overChildBudget: Boolean,
    val budgetState: RuntimeBudgetState,
    val budgetActions: List<RuntimeBudgetAction>,
    val disposition: RuntimeLifecycleReclaimDisposition,
    val leaseBaseTtlMs: Long,
    val leaseMaxTotalMs: Long,
    val leaseRemainingMs: Long,
    val leaseFirstSeenAtMs: Long = 0L,
    val leaseLastActivityAtMs: Long = 0L,
    val leaseExpireAtMs: Long = 0L,
    val leaseAtMax: Boolean,
    val leaseExtensionMs: Long = 0L,
    val leaseExpiredGraceMs: Long = 0L,
    val leaseExpiredGraceRemainingMs: Long = 0L,
    val leaseExpiredGraceActive: Boolean = false,
    val leaseExpiredSettlementCount: Int = 0,
    val activityScorePercent: Int = 0,
    val reclaimRank: Int,
    val tierReason: String,
    val layerReason: String,
    val activityReason: String,
    val reason: String
)

data class RuntimeLifecycleReclaimPlanDryRunSnapshot(
    val mode: String = "ubuntu_lifecycle_reclaim_plan_dry_run_v0",
    val enforcementMode: String = "dry_run_manual_review",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeLifecycleReclaimPlanState =
        RuntimeLifecycleReclaimPlanState.FOREGROUND_KEEP,
    val recommendation: RuntimeLifecycleReclaimRecommendation =
        RuntimeLifecycleReclaimRecommendation.KEEP_OBSERVING,
    val managementModel: String =
        "system_core_user_locked_foreground_priority_lease_pool_anomaly_pool",
    val primaryBudgetAxis: String = "memory_budget_with_activity_lease_refresh",
    val layerContract: String =
        "system_locked_foreground_are_protected_lease_pool_is_ranked_anomaly_pool_is_manual_review",
    val leasePoolContract: String =
        "identity_gates_lease_pool_activity_refreshes_expire_at_until_max_total_window",
    val leasePoolEvictionOrder: String =
        "expired_or_low_remaining_time_first_no_cost_rerank",
    val androidReclaimerScope: String =
        "registered_background_runtime_and_policy_classified_unattributed_only",
    val prootCoreHandling: String =
        "proot_capacity_is_budget_review_not_lifecycle_reclaim_target",
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val policyGraceMs: Long = 0L,
    val policyTransientCleanupMs: Long = 0L,
    val policyServiceOnlyMs: Long = 0L,
    val policyLowActivityMs: Long = 0L,
    val policyLeaseActiveTtlMs: Long = 0L,
    val policyLeaseWeakActivityTtlMs: Long = 0L,
    val policyLeaseCoolingTtlMs: Long = 0L,
    val policyLeaseMaxTotalMs: Long = 0L,
    val policyLeaseSettlementTickMs: Long = 0L,
    val policyLeaseMemorySampleTickMs: Long = 0L,
    val policyLeaseMemoryPressureSampleAvailablePercent: Int = 0,
    val policyLeaseMemoryPressureSampleCooldownMs: Long = 0L,
    val policyLeaseMemoryPressureImmediateSettlement: Boolean = false,
    val policyActivityCpuStrongDeltaTicks: Long = 0L,
    val policyActivityCpuWeakDeltaTicks: Long = 0L,
    val policyActivityIoStrongDeltaBytes: Long = 0L,
    val policyActivityIoWeakDeltaBytes: Long = 0L,
    val policyActivityRssMinDeltaKb: Long = 0L,
    val policyActivityRssDeltaPercent: Int = 0,
    val policyLeaseInitialMs: Long = 0L,
    val policyLeaseMemoryMaxExtensionMs: Long = 0L,
    val policyActivityRssStrongDeltaKb: Long = 0L,
    val policyActivityRssStrongDeltaPercent: Int = 0,
    val policyLeaseProcessTreeBonusMs: Long = 0L,
    val policyLeaseForegroundBonusMs: Long = 0L,
    val policyLeaseCpuBonusMs: Long = 0L,
    val policyLeaseNetworkLikelyBonusMs: Long = 0L,
    val policyLeaseMaxExtensionPerSettlementMs: Long = 0L,
    val policyLeaseExpiredGraceMs: Long = 0L,
    val policyLeaseMaxTotalUnlockAverageScorePercent: Int = 0,
    val policyLeaseMaxTotalUnlockLatestScorePercent: Int = 0,
    val policyLeaseMaxTotalUnlockInitialMs: Long = 0L,
    val policyLeaseTrustedActivityExtensionMs: Long = 0L,
    val policyLeaseStrongActivityExtensionMs: Long = 0L,
    val policyLeaseMultiEvidenceActivityExtensionMs: Long = 0L,
    val policyLeaseHighCostUpgradeMs: Long = 0L,
    val policyLeaseHighCostRssKb: Long = 0L,
    val policyLeaseHighCostPoolBudgetPercent: Int = 0,
    val policyActivityCpuTicksPerSecond: Long = 0L,
    val policyActivityCpuWeakPercent: Int = 0,
    val policyActivityCpuTrustedPercent: Int = 0,
    val policyActivityCpuStrongPercent: Int = 0,
    val policyLeaseMaxTotalUnlockActiveSamplePercent: Int = 0,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val workloadCount: Int = 0,
    val itemCount: Int = 0,
    val unassignedLiveTracees: Int = 0,
    val syntheticStrayItemCount: Int = 0,
    val syntheticLeaseItemCount: Int = 0,
    val syntheticBuildItemCount: Int = 0,
    val syntheticProbeItemCount: Int = 0,
    val syntheticEphemeralItemCount: Int = 0,
    val keepCount: Int = 0,
    val leaseCount: Int = 0,
    val cleanupCandidateCount: Int = 0,
    val quarantineCount: Int = 0,
    val systemCoreProtectedCount: Int = 0,
    val pinnedServiceKeepCount: Int = 0,
    val systemCoreTierCount: Int = 0,
    val userLockedTierCount: Int = 0,
    val keepActiveTierCount: Int = 0,
    val backgroundGraceTierCount: Int = 0,
    val leasedBackgroundTierCount: Int = 0,
    val cleanupCandidateTierCount: Int = 0,
    val quarantineTierCount: Int = 0,
    val systemCoreLayerCount: Int = 0,
    val userLockedLayerCount: Int = 0,
    val foregroundPriorityLayerCount: Int = 0,
    val leasePoolLayerCount: Int = 0,
    val anomalyPoolLayerCount: Int = 0,
    val protectedActivityCount: Int = 0,
    val activeActivityCount: Int = 0,
    val weakActivityCount: Int = 0,
    val coolingActivityCount: Int = 0,
    val expiredActivityCount: Int = 0,
    val maxTotalLeaseCandidateCount: Int = 0,
    val abnormalActivityCount: Int = 0,
    val leasePoolMemoryBudgetKb: Long = 0L,
    val leasePoolRssKb: Long = 0L,
    val leasePoolOverBudget: Boolean = false,
    val leasePoolBudgetPercent: Int = 0,
    val leasePoolEvictionCandidateCount: Int = 0,
    val backgroundCandidateCount: Int = 0,
    val budgetCandidateCount: Int = 0,
    val keepDispositionCount: Int = 0,
    val watchLeaseCount: Int = 0,
    val expireLeaseCount: Int = 0,
    val cleanupReviewCount: Int = 0,
    val reclaimChildrenReviewCount: Int = 0,
    val terminateReviewCount: Int = 0,
    val restartReviewCount: Int = 0,
    val quarantineReviewCount: Int = 0,
    val manualOnlyQuarantinedCount: Int = 0,
    val reason: String = "waiting_for_workload_lifecycle_inputs",
    val items: List<RuntimeLifecycleReclaimItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation lifecycle=$lifecycleState/$backgroundPhase " +
            "budget=$budgetOverallState pressure=$pressureState workloads=$workloadCount items=$itemCount " +
            "unassignedTracees=$unassignedLiveTracees syntheticStray=$syntheticStrayItemCount " +
            "syntheticLease=$syntheticLeaseItemCount keep=$keepCount " +
            "lease=$leaseCount cleanupCandidates=$cleanupCandidateCount quarantine=$quarantineCount " +
            "protectedCore=$systemCoreProtectedCount pinnedKeep=$pinnedServiceKeepCount " +
            "tiers(core=$systemCoreTierCount,locked=$userLockedTierCount,active=$keepActiveTierCount," +
            "grace=$backgroundGraceTierCount,lease=$leasedBackgroundTierCount,cleanup=$cleanupCandidateTierCount) " +
            "layers(core=$systemCoreLayerCount,locked=$userLockedLayerCount,fg=$foregroundPriorityLayerCount," +
            "leasePool=$leasePoolLayerCount,anomaly=$anomalyPoolLayerCount) " +
            "leasePoolRss=$leasePoolRssKb/$leasePoolMemoryBudgetKb over=$leasePoolOverBudget " +
            "expireLease=$expireLeaseCount cleanup=$cleanupReviewCount reclaimChildren=$reclaimChildrenReviewCount " +
            "terminate=$terminateReviewCount restart=$restartReviewCount quarantineReview=$quarantineReviewCount " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 10): String {
        return buildString {
            appendLine("lifecycle_reclaim_plan_mode=${mode.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_enforcement_mode=${enforcementMode.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("lifecycle_reclaim_plan_contract=${executionContract().toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_execution_owner=android_control_plane")
            appendLine("lifecycle_reclaim_plan_ubuntu_direct_reclaim_allowed=false")
            appendLine("lifecycle_reclaim_plan_android_executor_policy_gate=runtime_reclaimer_policy")
            appendLine("lifecycle_reclaim_plan_execution_boundary=${executionBoundary().toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_management_model=${managementModel.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_primary_budget_axis=${primaryBudgetAxis.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_layer_contract=${layerContract.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_lease_pool_contract=${leasePoolContract.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_lease_pool_eviction_order=${leasePoolEvictionOrder.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_android_reclaimer_scope=${androidReclaimerScope.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_proot_core_handling=${prootCoreHandling.toLifecycleReclaimEnvValue()}")
            appendLine("lifecycle_reclaim_plan_generated_at=$generatedAtMs")
            appendLine("lifecycle_reclaim_plan_state=${state.name}")
            appendLine("lifecycle_reclaim_plan_recommendation=${recommendation.name}")
            appendLine("lifecycle_reclaim_plan_lifecycle_state=${lifecycleState.name}")
            appendLine("lifecycle_reclaim_plan_background_phase=${backgroundPhase.name}")
            appendLine("lifecycle_reclaim_plan_policy_grace_ms=$policyGraceMs")
            appendLine("lifecycle_reclaim_plan_policy_transient_cleanup_ms=$policyTransientCleanupMs")
            appendLine("lifecycle_reclaim_plan_policy_service_only_ms=$policyServiceOnlyMs")
            appendLine("lifecycle_reclaim_plan_policy_low_activity_ms=$policyLowActivityMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_active_ttl_ms=$policyLeaseActiveTtlMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_weak_activity_ttl_ms=$policyLeaseWeakActivityTtlMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_cooling_ttl_ms=$policyLeaseCoolingTtlMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_max_total_ms=$policyLeaseMaxTotalMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_settlement_tick_ms=$policyLeaseSettlementTickMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_memory_sample_tick_ms=$policyLeaseMemorySampleTickMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_memory_pressure_sample_available_percent=$policyLeaseMemoryPressureSampleAvailablePercent")
            appendLine("lifecycle_reclaim_plan_policy_lease_memory_pressure_sample_cooldown_ms=$policyLeaseMemoryPressureSampleCooldownMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_memory_pressure_immediate_settlement=$policyLeaseMemoryPressureImmediateSettlement")
            appendLine("lifecycle_reclaim_plan_policy_activity_cpu_strong_delta_ticks=$policyActivityCpuStrongDeltaTicks")
            appendLine("lifecycle_reclaim_plan_policy_activity_cpu_weak_delta_ticks=$policyActivityCpuWeakDeltaTicks")
            appendLine("lifecycle_reclaim_plan_policy_activity_io_strong_delta_bytes=$policyActivityIoStrongDeltaBytes")
            appendLine("lifecycle_reclaim_plan_policy_activity_io_weak_delta_bytes=$policyActivityIoWeakDeltaBytes")
            appendLine("lifecycle_reclaim_plan_policy_activity_rss_min_delta_kb=$policyActivityRssMinDeltaKb")
            appendLine("lifecycle_reclaim_plan_policy_activity_rss_delta_percent=$policyActivityRssDeltaPercent")
            appendLine("lifecycle_reclaim_plan_policy_lease_initial_ms=$policyLeaseInitialMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_memory_max_extension_ms=$policyLeaseMemoryMaxExtensionMs")
            appendLine("lifecycle_reclaim_plan_policy_activity_rss_strong_delta_kb=$policyActivityRssStrongDeltaKb")
            appendLine("lifecycle_reclaim_plan_policy_activity_rss_strong_delta_percent=$policyActivityRssStrongDeltaPercent")
            appendLine("lifecycle_reclaim_plan_policy_lease_process_tree_bonus_ms=$policyLeaseProcessTreeBonusMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_foreground_bonus_ms=$policyLeaseForegroundBonusMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_cpu_bonus_ms=$policyLeaseCpuBonusMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_network_likely_bonus_ms=$policyLeaseNetworkLikelyBonusMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_max_extension_per_settlement_ms=$policyLeaseMaxExtensionPerSettlementMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_expired_grace_ms=$policyLeaseExpiredGraceMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_max_total_unlock_average_score_percent=$policyLeaseMaxTotalUnlockAverageScorePercent")
            appendLine("lifecycle_reclaim_plan_policy_lease_max_total_unlock_latest_score_percent=$policyLeaseMaxTotalUnlockLatestScorePercent")
            appendLine("lifecycle_reclaim_plan_policy_lease_max_total_unlock_initial_ms=$policyLeaseMaxTotalUnlockInitialMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_trusted_activity_extension_ms=$policyLeaseTrustedActivityExtensionMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_strong_activity_extension_ms=$policyLeaseStrongActivityExtensionMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_multi_evidence_activity_extension_ms=$policyLeaseMultiEvidenceActivityExtensionMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_high_cost_upgrade_ms=$policyLeaseHighCostUpgradeMs")
            appendLine("lifecycle_reclaim_plan_policy_lease_high_cost_rss_kb=$policyLeaseHighCostRssKb")
            appendLine("lifecycle_reclaim_plan_policy_lease_high_cost_pool_budget_percent=$policyLeaseHighCostPoolBudgetPercent")
            appendLine("lifecycle_reclaim_plan_policy_activity_cpu_ticks_per_second=$policyActivityCpuTicksPerSecond")
            appendLine("lifecycle_reclaim_plan_policy_activity_cpu_weak_percent=$policyActivityCpuWeakPercent")
            appendLine("lifecycle_reclaim_plan_policy_activity_cpu_trusted_percent=$policyActivityCpuTrustedPercent")
            appendLine("lifecycle_reclaim_plan_policy_activity_cpu_strong_percent=$policyActivityCpuStrongPercent")
            appendLine("lifecycle_reclaim_plan_policy_lease_max_total_unlock_active_sample_percent=$policyLeaseMaxTotalUnlockActiveSamplePercent")
            appendLine("lifecycle_reclaim_plan_budget_overall_state=${budgetOverallState.name}")
            appendLine("lifecycle_reclaim_plan_pressure_state=${pressureState.name}")
            appendLine("lifecycle_reclaim_plan_workload_count=$workloadCount")
            appendLine("lifecycle_reclaim_plan_item_count=$itemCount")
            appendLine("lifecycle_reclaim_plan_unassigned_live_tracees=$unassignedLiveTracees")
            appendLine("lifecycle_reclaim_plan_synthetic_stray_item_count=$syntheticStrayItemCount")
            appendLine("lifecycle_reclaim_plan_synthetic_lease_item_count=$syntheticLeaseItemCount")
            appendLine("lifecycle_reclaim_plan_synthetic_build_item_count=$syntheticBuildItemCount")
            appendLine("lifecycle_reclaim_plan_synthetic_probe_item_count=$syntheticProbeItemCount")
            appendLine("lifecycle_reclaim_plan_synthetic_ephemeral_item_count=$syntheticEphemeralItemCount")
            appendLine("lifecycle_reclaim_plan_keep_count=$keepCount")
            appendLine("lifecycle_reclaim_plan_lease_count=$leaseCount")
            appendLine("lifecycle_reclaim_plan_cleanup_candidate_count=$cleanupCandidateCount")
            appendLine("lifecycle_reclaim_plan_quarantine_count=$quarantineCount")
            appendLine("lifecycle_reclaim_plan_system_core_protected_count=$systemCoreProtectedCount")
            appendLine("lifecycle_reclaim_plan_pinned_service_keep_count=$pinnedServiceKeepCount")
            appendLine("lifecycle_reclaim_plan_system_core_tier_count=$systemCoreTierCount")
            appendLine("lifecycle_reclaim_plan_user_locked_tier_count=$userLockedTierCount")
            appendLine("lifecycle_reclaim_plan_keep_active_tier_count=$keepActiveTierCount")
            appendLine("lifecycle_reclaim_plan_background_grace_tier_count=$backgroundGraceTierCount")
            appendLine("lifecycle_reclaim_plan_leased_background_tier_count=$leasedBackgroundTierCount")
            appendLine("lifecycle_reclaim_plan_cleanup_candidate_tier_count=$cleanupCandidateTierCount")
            appendLine("lifecycle_reclaim_plan_quarantine_tier_count=$quarantineTierCount")
            appendLine("lifecycle_reclaim_plan_system_core_layer_count=$systemCoreLayerCount")
            appendLine("lifecycle_reclaim_plan_user_locked_layer_count=$userLockedLayerCount")
            appendLine("lifecycle_reclaim_plan_foreground_priority_layer_count=$foregroundPriorityLayerCount")
            appendLine("lifecycle_reclaim_plan_lease_pool_layer_count=$leasePoolLayerCount")
            appendLine("lifecycle_reclaim_plan_anomaly_pool_layer_count=$anomalyPoolLayerCount")
            appendLine("lifecycle_reclaim_plan_protected_activity_count=$protectedActivityCount")
            appendLine("lifecycle_reclaim_plan_active_activity_count=$activeActivityCount")
            appendLine("lifecycle_reclaim_plan_weak_activity_count=$weakActivityCount")
            appendLine("lifecycle_reclaim_plan_cooling_activity_count=$coolingActivityCount")
            appendLine("lifecycle_reclaim_plan_expired_activity_count=$expiredActivityCount")
            appendLine("lifecycle_reclaim_plan_max_total_lease_candidate_count=$maxTotalLeaseCandidateCount")
            appendLine("lifecycle_reclaim_plan_abnormal_activity_count=$abnormalActivityCount")
            appendLine("lifecycle_reclaim_plan_lease_pool_memory_budget_kb=$leasePoolMemoryBudgetKb")
            appendLine("lifecycle_reclaim_plan_lease_pool_rss_kb=$leasePoolRssKb")
            appendLine("lifecycle_reclaim_plan_lease_pool_over_budget=$leasePoolOverBudget")
            appendLine("lifecycle_reclaim_plan_lease_pool_budget_percent=$leasePoolBudgetPercent")
            appendLine("lifecycle_reclaim_plan_lease_pool_eviction_candidate_count=$leasePoolEvictionCandidateCount")
            appendLine("lifecycle_reclaim_plan_background_candidate_count=$backgroundCandidateCount")
            appendLine("lifecycle_reclaim_plan_budget_candidate_count=$budgetCandidateCount")
            appendLine("lifecycle_reclaim_plan_keep_disposition_count=$keepDispositionCount")
            appendLine("lifecycle_reclaim_plan_watch_lease_count=$watchLeaseCount")
            appendLine("lifecycle_reclaim_plan_expire_lease_count=$expireLeaseCount")
            appendLine("lifecycle_reclaim_plan_cleanup_review_count=$cleanupReviewCount")
            appendLine("lifecycle_reclaim_plan_reclaim_children_review_count=$reclaimChildrenReviewCount")
            appendLine("lifecycle_reclaim_plan_terminate_review_count=$terminateReviewCount")
            appendLine("lifecycle_reclaim_plan_restart_review_count=$restartReviewCount")
            appendLine("lifecycle_reclaim_plan_quarantine_review_count=$quarantineReviewCount")
            appendLine("lifecycle_reclaim_plan_manual_only_quarantined_count=$manualOnlyQuarantinedCount")
            appendLine("lifecycle_reclaim_plan_reason=${reason.toLifecycleReclaimEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "lifecycle_reclaim_plan_item_${index + 1}"
                appendLine("${prefix}_id=${item.workloadId.toLifecycleReclaimEnvValue()}")
                appendLine("${prefix}_class=${item.workloadClass.name}")
                appendLine("${prefix}_retention=${item.retention.name}")
                appendLine("${prefix}_tier=${item.tier.name}")
                appendLine("${prefix}_layer=${item.layer.name}")
                appendLine("${prefix}_activity_state=${item.activityState.name}")
                appendLine("${prefix}_lane=${item.lane.name}")
                appendLine("${prefix}_background_allowed=${item.backgroundAllowed}")
                appendLine("${prefix}_children=${item.processCount}")
                appendLine("${prefix}_rss_kb=${item.rssKb}")
                appendLine("${prefix}_max_children=${item.maxChildren}")
                appendLine("${prefix}_over_child_budget=${item.overChildBudget}")
                appendLine("${prefix}_budget_state=${item.budgetState.name}")
                appendLine("${prefix}_budget_actions=${item.budgetActions.joinToString("+") { it.name }.toLifecycleReclaimEnvValue()}")
                appendLine("${prefix}_disposition=${item.disposition.name}")
                appendLine("${prefix}_lease_base_ttl_ms=${item.leaseBaseTtlMs}")
                appendLine("${prefix}_lease_max_total_ms=${item.leaseMaxTotalMs}")
                appendLine("${prefix}_lease_remaining_ms=${item.leaseRemainingMs}")
                appendLine("${prefix}_lease_first_seen_at_ms=${item.leaseFirstSeenAtMs}")
                appendLine("${prefix}_lease_last_activity_at_ms=${item.leaseLastActivityAtMs}")
                appendLine("${prefix}_lease_expire_at_ms=${item.leaseExpireAtMs}")
                appendLine("${prefix}_lease_at_max=${item.leaseAtMax}")
                appendLine("${prefix}_lease_extension_ms=${item.leaseExtensionMs}")
                appendLine("${prefix}_lease_expired_grace_ms=${item.leaseExpiredGraceMs}")
                appendLine("${prefix}_lease_expired_grace_remaining_ms=${item.leaseExpiredGraceRemainingMs}")
                appendLine("${prefix}_lease_expired_grace_active=${item.leaseExpiredGraceActive}")
                appendLine("${prefix}_lease_expired_settlement_count=${item.leaseExpiredSettlementCount}")
                appendLine("${prefix}_activity_score_percent=${item.activityScorePercent}")
                appendLine("${prefix}_reclaim_rank=${item.reclaimRank}")
                appendLine("${prefix}_tier_reason=${item.tierReason.toLifecycleReclaimEnvValue()}")
                appendLine("${prefix}_layer_reason=${item.layerReason.toLifecycleReclaimEnvValue()}")
                appendLine("${prefix}_activity_reason=${item.activityReason.toLifecycleReclaimEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toLifecycleReclaimEnvValue()}")
            }
            appendLine("lifecycle_reclaim_plan_boundary=${planBoundary().toLifecycleReclaimEnvValue()}")
        }
    }

    private fun executionContract(): String {
        return if (enforcementEnabled) {
            "classify_rank_and_execute_lease_candidates_via_android_reclaimer_policy"
        } else {
            "classify_rank_review_android_reclaimer_policy_decides_execution"
        }
    }

    private fun executionBoundary(): String {
        return if (enforcementEnabled) {
            "plan_ranks_candidates_android_reclaimer_executes_registered_or_policy_classified_lease_only"
        } else {
            "plan_no_ubuntu_cleanup_android_reclaimer_may_execute_registered_candidates_by_policy"
        }
    }

    private fun planBoundary(): String {
        return if (enforcementEnabled) {
            "lease_reclaim_execution_armed_no_ubuntu_direct_cleanup_no_restart_no_quarantine"
        } else {
            "dry_run_no_cleanup_no_freeze_no_kill_no_restart_no_quarantine"
        }
    }
}

object RuntimeLifecycleReclaimPlanDryRun {
    fun evaluate(
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressure: RuntimePressureSnapshot = RuntimePressureSnapshot(),
        policy: RuntimeWorkloadPolicy = RuntimeWorkloadPolicy.default(),
        enforcementEnabled: Boolean = false,
        enforcementMode: String = "dry_run_manual_review",
        now: Long = System.currentTimeMillis()
    ): RuntimeLifecycleReclaimPlanDryRunSnapshot {
        val budgetById = budgetPressure.candidates.associateBy { it.workloadId }
        val backgroundCandidateIds = backgroundDecay.candidates.mapTo(mutableSetOf()) { it.workloadId }
        val leasePoolBudgetPercent = leasePoolBudgetPercentFor(
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            pressure = pressure,
            leasePolicy = policy.lifecycleLease
        )
        val leasePoolMemoryBudgetKb = ((pressure.hostMemAvailableKb ?: 0L) * leasePoolBudgetPercent / 100L)
            .coerceAtLeast(0L)
        val registryItems = workloadRegistry.entries.map { entry ->
            val budget = budgetById[entry.workloadId]
            entry.toReclaimItem(
                backgroundCandidate = entry.workloadId in backgroundCandidateIds,
                budget = budget,
                backgroundDecay = backgroundDecay,
                leasePolicy = policy.lifecycleLease,
                leasePoolMemoryBudgetKb = leasePoolMemoryBudgetKb,
                now = now
            )
        }.sortedWith(
            compareByDescending<RuntimeLifecycleReclaimItem> { it.disposition.severity() }
                .thenBy { it.lane.ordinal }
                .thenBy { it.workloadId }
        )
        val items = dropSyntheticUnassignedTraceeItems(registryItems)
        val leasePoolRssKb = items
            .filter { it.layer == RuntimeLifecycleLayer.LEASE_POOL }
            .sumOf { it.rssKb.coerceAtLeast(0L) }
        val leasePoolOverBudget = leasePoolMemoryBudgetKb > 0L && leasePoolRssKb > leasePoolMemoryBudgetKb
        val rankedItems = items
            .withLeasePoolEvictionRanks(leasePoolOverBudget)
        val state = resolveState(
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            items = rankedItems
        )

        return RuntimeLifecycleReclaimPlanDryRunSnapshot(
            enforcementEnabled = enforcementEnabled,
            enforcementMode = enforcementMode,
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            lifecycleState = backgroundDecay.lifecycleState,
            backgroundPhase = backgroundDecay.phase,
            policyGraceMs = backgroundDecay.policyGraceMs,
            policyTransientCleanupMs = backgroundDecay.policyTransientCleanupMs,
            policyServiceOnlyMs = backgroundDecay.policyServiceOnlyMs,
            policyLowActivityMs = backgroundDecay.policyLowActivityMs,
            policyLeaseActiveTtlMs = policy.lifecycleLease.activeLeaseTtlMs,
            policyLeaseWeakActivityTtlMs = policy.lifecycleLease.weakActivityLeaseTtlMs,
            policyLeaseCoolingTtlMs = policy.lifecycleLease.coolingLeaseTtlMs,
            policyLeaseMaxTotalMs = policy.lifecycleLease.maxTotalLeaseMs,
            policyLeaseSettlementTickMs = policy.lifecycleLease.settlementTickMs,
            policyLeaseMemorySampleTickMs = policy.lifecycleLease.memorySampleTickMs,
            policyLeaseMemoryPressureSampleAvailablePercent =
                policy.lifecycleLease.memoryPressureSampleAvailablePercent,
            policyLeaseMemoryPressureSampleCooldownMs =
                policy.lifecycleLease.memoryPressureSampleCooldownMs,
            policyLeaseMemoryPressureImmediateSettlement =
                policy.lifecycleLease.memoryPressureImmediateSettlement,
            policyActivityCpuStrongDeltaTicks = policy.lifecycleLease.cpuStrongDeltaTicks,
            policyActivityCpuWeakDeltaTicks = policy.lifecycleLease.cpuWeakDeltaTicks,
            policyActivityIoStrongDeltaBytes = policy.lifecycleLease.ioStrongDeltaBytes,
            policyActivityIoWeakDeltaBytes = policy.lifecycleLease.ioWeakDeltaBytes,
            policyActivityRssMinDeltaKb = policy.lifecycleLease.rssMinDeltaKb,
            policyActivityRssDeltaPercent = policy.lifecycleLease.rssDeltaPercent,
            policyLeaseInitialMs = policy.lifecycleLease.initialLeaseMs,
            policyLeaseMemoryMaxExtensionMs = policy.lifecycleLease.memoryMaxExtensionMs,
            policyActivityRssStrongDeltaKb = policy.lifecycleLease.rssStrongDeltaKb,
            policyActivityRssStrongDeltaPercent = policy.lifecycleLease.rssStrongDeltaPercent,
            policyLeaseProcessTreeBonusMs = policy.lifecycleLease.processTreeBonusMs,
            policyLeaseForegroundBonusMs = policy.lifecycleLease.foregroundBonusMs,
            policyLeaseCpuBonusMs = policy.lifecycleLease.cpuBonusMs,
            policyLeaseNetworkLikelyBonusMs = policy.lifecycleLease.networkLikelyBonusMs,
            policyLeaseMaxExtensionPerSettlementMs = policy.lifecycleLease.maxExtensionPerSettlementMs,
            policyLeaseExpiredGraceMs = policy.lifecycleLease.expiredGraceMs,
            policyLeaseMaxTotalUnlockAverageScorePercent =
                policy.lifecycleLease.maxTotalUnlockAverageScorePercent,
            policyLeaseMaxTotalUnlockLatestScorePercent =
                policy.lifecycleLease.maxTotalUnlockLatestScorePercent,
            policyLeaseMaxTotalUnlockInitialMs = policy.lifecycleLease.maxTotalUnlockInitialMs,
            policyLeaseTrustedActivityExtensionMs = policy.lifecycleLease.trustedActivityExtensionMs,
            policyLeaseStrongActivityExtensionMs = policy.lifecycleLease.strongActivityExtensionMs,
            policyLeaseMultiEvidenceActivityExtensionMs =
                policy.lifecycleLease.multiEvidenceActivityExtensionMs,
            policyLeaseHighCostUpgradeMs = policy.lifecycleLease.highCostUpgradeMs,
            policyLeaseHighCostRssKb = policy.lifecycleLease.highCostRssKb,
            policyLeaseHighCostPoolBudgetPercent = policy.lifecycleLease.highCostLeasePoolBudgetPercent,
            policyActivityCpuTicksPerSecond = policy.lifecycleLease.cpuTicksPerSecond,
            policyActivityCpuWeakPercent = policy.lifecycleLease.cpuWeakPercent,
            policyActivityCpuTrustedPercent = policy.lifecycleLease.cpuTrustedPercent,
            policyActivityCpuStrongPercent = policy.lifecycleLease.cpuStrongPercent,
            policyLeaseMaxTotalUnlockActiveSamplePercent =
                policy.lifecycleLease.maxTotalUnlockActiveSamplePercent,
            budgetOverallState = budgetPressure.overallState,
            pressureState = pressureConsumer.state,
            workloadCount = workloadRegistry.totalWorkloads,
            itemCount = rankedItems.size,
            unassignedLiveTracees = workloadRegistry.unassignedLiveTracees,
            syntheticStrayItemCount = rankedItems.count {
                it.workloadId.startsWith("UNASSIGNED_PROOT_TRACEES:")
            },
            syntheticLeaseItemCount = rankedItems.count {
                it.workloadId.startsWith("UNASSIGNED_PROOT_BUILD_TRACEES:") ||
                    it.workloadId.startsWith("UNASSIGNED_PROOT_PROBE_TRACEES:") ||
                    it.workloadId.startsWith("UNASSIGNED_PROOT_EPHEMERAL_TRACEES:")
            },
            syntheticBuildItemCount = rankedItems.count {
                it.workloadId.startsWith("UNASSIGNED_PROOT_BUILD_TRACEES:")
            },
            syntheticProbeItemCount = rankedItems.count {
                it.workloadId.startsWith("UNASSIGNED_PROOT_PROBE_TRACEES:")
            },
            syntheticEphemeralItemCount = rankedItems.count {
                it.workloadId.startsWith("UNASSIGNED_PROOT_EPHEMERAL_TRACEES:")
            },
            keepCount = workloadRegistry.keepCount,
            leaseCount = workloadRegistry.leaseCount,
            cleanupCandidateCount = workloadRegistry.cleanupCandidateCount,
            quarantineCount = workloadRegistry.quarantineCount,
            systemCoreProtectedCount = rankedItems.count {
                it.workloadClass == RuntimeWorkloadClass.SYSTEM_CORE &&
                    it.disposition == RuntimeLifecycleReclaimDisposition.KEEP
            },
            pinnedServiceKeepCount = rankedItems.count {
                it.workloadClass == RuntimeWorkloadClass.PINNED_SERVICE &&
                    it.disposition == RuntimeLifecycleReclaimDisposition.KEEP
            },
            systemCoreTierCount = rankedItems.count { it.tier == RuntimeLifecycleTier.SYSTEM_CORE },
            userLockedTierCount = rankedItems.count { it.tier == RuntimeLifecycleTier.USER_LOCKED },
            keepActiveTierCount = rankedItems.count { it.tier == RuntimeLifecycleTier.KEEP_ACTIVE },
            backgroundGraceTierCount = rankedItems.count { it.tier == RuntimeLifecycleTier.BACKGROUND_GRACE },
            leasedBackgroundTierCount = rankedItems.count { it.tier == RuntimeLifecycleTier.LEASED_BACKGROUND },
            cleanupCandidateTierCount = rankedItems.count { it.tier == RuntimeLifecycleTier.CLEANUP_CANDIDATE },
            quarantineTierCount = rankedItems.count { it.tier == RuntimeLifecycleTier.QUARANTINE },
            systemCoreLayerCount = rankedItems.count { it.layer == RuntimeLifecycleLayer.SYSTEM_CORE },
            userLockedLayerCount = rankedItems.count { it.layer == RuntimeLifecycleLayer.USER_LOCKED },
            foregroundPriorityLayerCount = rankedItems.count { it.layer == RuntimeLifecycleLayer.FOREGROUND_PRIORITY },
            leasePoolLayerCount = rankedItems.count { it.layer == RuntimeLifecycleLayer.LEASE_POOL },
            anomalyPoolLayerCount = rankedItems.count { it.layer == RuntimeLifecycleLayer.ANOMALY_POOL },
            protectedActivityCount = rankedItems.count { it.activityState == RuntimeLifecycleActivityState.PROTECTED },
            activeActivityCount = rankedItems.count { it.activityState == RuntimeLifecycleActivityState.ACTIVE },
            weakActivityCount = rankedItems.count { it.activityState == RuntimeLifecycleActivityState.WEAK_ACTIVITY },
            coolingActivityCount = rankedItems.count { it.activityState == RuntimeLifecycleActivityState.COOLING },
            expiredActivityCount = rankedItems.count { it.activityState == RuntimeLifecycleActivityState.EXPIRED },
            maxTotalLeaseCandidateCount = rankedItems.count {
                it.leaseAtMax && it.layer == RuntimeLifecycleLayer.LEASE_POOL
            },
            abnormalActivityCount = rankedItems.count { it.activityState == RuntimeLifecycleActivityState.ABNORMAL },
            leasePoolMemoryBudgetKb = leasePoolMemoryBudgetKb,
            leasePoolRssKb = leasePoolRssKb,
            leasePoolOverBudget = leasePoolOverBudget,
            leasePoolBudgetPercent = leasePoolBudgetPercent,
            leasePoolEvictionCandidateCount = rankedItems.count { it.reclaimRank > 0 },
            backgroundCandidateCount = backgroundDecay.candidates.size,
            budgetCandidateCount = budgetPressure.candidates.size,
            keepDispositionCount = rankedItems.count { it.disposition == RuntimeLifecycleReclaimDisposition.KEEP },
            watchLeaseCount = rankedItems.count { it.disposition == RuntimeLifecycleReclaimDisposition.WATCH_LEASE },
            expireLeaseCount = rankedItems.count { it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE },
            cleanupReviewCount = rankedItems.count { it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP },
            reclaimChildrenReviewCount = rankedItems.count {
                it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_RECLAIM_CHILDREN
            },
            terminateReviewCount = rankedItems.count {
                it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD
            },
            restartReviewCount = rankedItems.count {
                it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_RESTART_MAIN
            },
            quarantineReviewCount = rankedItems.count {
                it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_QUARANTINE
            },
            manualOnlyQuarantinedCount = rankedItems.count {
                it.disposition == RuntimeLifecycleReclaimDisposition.MANUAL_ONLY_QUARANTINED
            },
            reason = buildReason(state, backgroundDecay, budgetPressure, pressureConsumer) +
                ",leasePool=${leasePoolRssKb}/${leasePoolMemoryBudgetKb},over=$leasePoolOverBudget",
            items = rankedItems
        )
    }

    private fun dropSyntheticUnassignedTraceeItems(
        items: List<RuntimeLifecycleReclaimItem>
    ): List<RuntimeLifecycleReclaimItem> {
        // Unassigned tracee buckets are observability-only. Real lease accounting must be per PID.
        return items
    }

    private fun MutableList<RuntimeLifecycleReclaimItem>.addLeaseTraceeItem(
        count: Int,
        workloadClass: RuntimeWorkloadClass,
        lane: RuntimeLaneKind,
        foregroundOrGrace: Boolean,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        leasePolicy: RuntimeLifecycleLeasePolicy,
        lastEventAtMs: Long,
        now: Long,
        maxChildren: Int,
        reason: String
    ) {
        if (count <= 0) return
        val layer = if (foregroundOrGrace) {
            RuntimeLifecycleLayer.FOREGROUND_PRIORITY
        } else {
            RuntimeLifecycleLayer.LEASE_POOL
        }
        val activity = syntheticActivityFor(lastEventAtMs, now, backgroundDecay)
        val lease = syntheticLeaseWindow(
            layer = layer,
            activityState = activity.first,
            backgroundDecay = backgroundDecay,
            leasePolicy = leasePolicy,
            lastEventAtMs = lastEventAtMs,
            now = now
        )
        val disposition = if (foregroundOrGrace) {
            RuntimeLifecycleReclaimDisposition.WATCH_LEASE
        } else if (activity.first == RuntimeLifecycleActivityState.EXPIRED || lease.remainingMs <= 0L) {
            RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE
        } else {
            RuntimeLifecycleReclaimDisposition.WATCH_LEASE
        }
        add(
            RuntimeLifecycleReclaimItem(
                workloadId = "UNASSIGNED_PROOT_${workloadClass.name}_TRACEES:$count",
                workloadClass = workloadClass,
                retention = RuntimeWorkloadRetention.LEASE,
                tier = if (foregroundOrGrace) {
                    RuntimeLifecycleTier.BACKGROUND_GRACE
                } else {
                    RuntimeLifecycleTier.LEASED_BACKGROUND
                },
                layer = layer,
                activityState = activity.first,
                lane = lane,
                backgroundAllowed = false,
                processCount = count,
                rssKb = 0L,
                maxChildren = maxChildren,
                overChildBudget = count > maxChildren,
                budgetState = RuntimeBudgetState.SOFT_PRESSURE,
                budgetActions = listOf(RuntimeBudgetAction.WARN),
                disposition = disposition,
                leaseBaseTtlMs = lease.baseTtlMs,
                leaseMaxTotalMs = lease.maxTotalMs,
                leaseRemainingMs = lease.remainingMs,
                leaseAtMax = lease.atMax,
                reclaimRank = 0,
                tierReason = if (foregroundOrGrace) {
                    "unassigned_proot_tracees_in_foreground_or_grace_window"
                } else {
                    "unassigned_proot_lease_tracees_after_grace"
                },
                layerReason = if (foregroundOrGrace) {
                    "foreground_or_grace_protects_unassigned_tracee_temporarily"
                } else {
                    "unassigned_tracee_uses_lease_pool_until_declared_or_expired"
                },
                activityReason = activity.second,
                reason = reason
            )
        )
    }

    private fun MutableList<RuntimeLifecycleReclaimItem>.addStrayTraceeItem(
        count: Int,
        foregroundOrGrace: Boolean,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        leasePolicy: RuntimeLifecycleLeasePolicy,
        lastEventAtMs: Long,
        now: Long
    ) {
        if (count <= 0) return
        val layer = if (foregroundOrGrace) {
            RuntimeLifecycleLayer.FOREGROUND_PRIORITY
        } else {
            RuntimeLifecycleLayer.LEASE_POOL
        }
        val activity = syntheticActivityFor(lastEventAtMs, now, backgroundDecay)
        val lease = syntheticLeaseWindow(
            layer = layer,
            activityState = activity.first,
            backgroundDecay = backgroundDecay,
            leasePolicy = leasePolicy,
            lastEventAtMs = lastEventAtMs,
            now = now
        )
        val disposition = if (foregroundOrGrace) {
            RuntimeLifecycleReclaimDisposition.WATCH_LEASE
        } else if (activity.first == RuntimeLifecycleActivityState.EXPIRED || lease.remainingMs <= 0L) {
            RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP
        } else {
            RuntimeLifecycleReclaimDisposition.WATCH_LEASE
        }
        add(
            RuntimeLifecycleReclaimItem(
                workloadId = "UNASSIGNED_PROOT_TRACEES:$count",
                workloadClass = RuntimeWorkloadClass.STRAY,
                retention = RuntimeWorkloadRetention.CLEANUP_CANDIDATE,
                tier = if (foregroundOrGrace) {
                    RuntimeLifecycleTier.BACKGROUND_GRACE
                } else {
                    RuntimeLifecycleTier.CLEANUP_CANDIDATE
                },
                layer = layer,
                activityState = activity.first,
                lane = RuntimeLaneKind.PROBE,
                backgroundAllowed = false,
                processCount = count,
                rssKb = 0L,
                maxChildren = 0,
                overChildBudget = true,
                budgetState = RuntimeBudgetState.SOFT_PRESSURE,
                budgetActions = listOf(RuntimeBudgetAction.REQUEST_CLEANUP),
                disposition = disposition,
                leaseBaseTtlMs = lease.baseTtlMs,
                leaseMaxTotalMs = lease.maxTotalMs,
                leaseRemainingMs = lease.remainingMs,
                leaseAtMax = lease.atMax,
                reclaimRank = 0,
                tierReason = if (foregroundOrGrace) {
                    "unassigned_stray_tracees_in_foreground_or_grace_window"
                } else {
                    "unassigned_stray_tracees_cleanup_candidate_after_grace"
                },
                layerReason = if (foregroundOrGrace) {
                    "foreground_or_grace_protects_unassigned_stray_temporarily"
                } else {
                    "unassigned_stray_uses_lease_pool_and_cleanup_budget"
                },
                activityReason = activity.second,
                reason = "proot_live_tracees_not_matched_to_android_workload_registry"
            )
        )
    }

    private fun RuntimeWorkloadRegistryEntry.toReclaimItem(
        backgroundCandidate: Boolean,
        budget: RuntimeBudgetPressureCandidate?,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        leasePolicy: RuntimeLifecycleLeasePolicy,
        leasePoolMemoryBudgetKb: Long,
        now: Long
    ): RuntimeLifecycleReclaimItem {
        val budgetState = budget?.state ?: RuntimeBudgetState.HEALTHY
        val budgetActions = budget?.actions ?: listOf(RuntimeBudgetAction.OBSERVE)
        val tierAndReason = resolveLifecycleTier(backgroundDecay.phase)
        val layerAndReason = resolveLifecycleLayer(backgroundDecay)
        val activity = RuntimeLifecycleActivityTracker.observe(
            entry = this,
            layer = layerAndReason.first,
            backgroundDecay = backgroundDecay,
            leasePolicy = leasePolicy,
            leasePoolMemoryBudgetKb = leasePoolMemoryBudgetKb,
            budgetState = budgetState,
            budgetActions = budgetActions,
            now = now
        )
        val lease = activity.leaseWindow
        val dispositionAndReason = resolveDisposition(
            backgroundCandidate = backgroundCandidate,
            budgetState = budgetState,
            budgetActions = budgetActions,
            backgroundPhase = backgroundDecay.phase,
            layer = layerAndReason.first,
            activityState = activity.state,
            leaseRemainingMs = lease.remainingMs,
            leaseAtMax = lease.atMax
        )
        return RuntimeLifecycleReclaimItem(
            workloadId = workloadId,
            workloadClass = workloadClass,
            retention = retention,
            tier = tierAndReason.first,
            layer = layerAndReason.first,
            activityState = activity.state,
            lane = suggestedLane,
            backgroundAllowed = backgroundAllowed,
            processCount = processCount,
            rssKb = rssKb,
            maxChildren = maxChildren,
            overChildBudget = overChildBudget,
            budgetState = budgetState,
            budgetActions = budgetActions,
            disposition = dispositionAndReason.first,
            leaseBaseTtlMs = lease.baseTtlMs,
            leaseMaxTotalMs = lease.maxTotalMs,
            leaseRemainingMs = lease.remainingMs,
            leaseFirstSeenAtMs = activity.firstSeenAtMs,
            leaseLastActivityAtMs = activity.lastActivityAtMs,
            leaseExpireAtMs = lease.expireAtMs,
            leaseAtMax = lease.atMax,
            leaseExtensionMs = lease.extensionMs,
            leaseExpiredGraceMs = lease.expiredGraceMs,
            leaseExpiredGraceRemainingMs = lease.expiredGraceRemainingMs,
            leaseExpiredGraceActive = lease.expiredGraceActive,
            leaseExpiredSettlementCount = activity.expiredSettlementCount,
            activityScorePercent = activity.scorePercent,
            reclaimRank = 0,
            tierReason = tierAndReason.second,
            layerReason = layerAndReason.second,
            activityReason = activity.reason,
            reason = dispositionAndReason.second
        )
    }

    private fun RuntimeWorkloadRegistryEntry.resolveLifecycleTier(
        backgroundPhase: RuntimeBackgroundDecayPhase
    ): Pair<RuntimeLifecycleTier, String> {
        if (workloadClass == RuntimeWorkloadClass.SYSTEM_CORE) {
            return RuntimeLifecycleTier.SYSTEM_CORE to
                "system_core_runtime_required_for_container_operation"
        }
        if (workloadClass == RuntimeWorkloadClass.PINNED_SERVICE ||
            backgroundAllowed && retention == RuntimeWorkloadRetention.KEEP
        ) {
            return RuntimeLifecycleTier.USER_LOCKED to
                "pinned_or_background_allowed_service_kept_by_user_or_policy"
        }
        if (retention == RuntimeWorkloadRetention.QUARANTINE) {
            return RuntimeLifecycleTier.QUARANTINE to
                "manual_recovery_only_quarantine_tier"
        }
        if (retention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE ||
            workloadClass == RuntimeWorkloadClass.STRAY ||
            workloadClass == RuntimeWorkloadClass.UNKNOWN
        ) {
            return RuntimeLifecycleTier.CLEANUP_CANDIDATE to
                "unowned_or_unknown_workload_cleanup_tier"
        }
        if (backgroundPhase == RuntimeBackgroundDecayPhase.BACKGROUND_GRACE) {
            return RuntimeLifecycleTier.BACKGROUND_GRACE to
                "recently_left_foreground_inside_grace_window"
        }
        if (retention == RuntimeWorkloadRetention.LEASE ||
            workloadClass == RuntimeWorkloadClass.BUILD ||
            workloadClass == RuntimeWorkloadClass.PROBE ||
            workloadClass == RuntimeWorkloadClass.EPHEMERAL
        ) {
            return RuntimeLifecycleTier.LEASED_BACKGROUND to
                "lease_or_transient_workload_after_foreground_allowance"
        }
        return RuntimeLifecycleTier.KEEP_ACTIVE to
            "active_keep_workload_observed_by_lifecycle"
    }

    private fun RuntimeWorkloadRegistryEntry.resolveLifecycleLayer(
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot
    ): Pair<RuntimeLifecycleLayer, String> {
        if (workloadClass == RuntimeWorkloadClass.SYSTEM_CORE) {
            return RuntimeLifecycleLayer.SYSTEM_CORE to
                "core_system_never_reclaimed_by_lease_pool"
        }
        if (workloadClass == RuntimeWorkloadClass.PINNED_SERVICE ||
            backgroundAllowed && retention == RuntimeWorkloadRetention.KEEP
        ) {
            return RuntimeLifecycleLayer.USER_LOCKED to
                "user_or_policy_locked_runtime_not_part_of_unknown_lease_pool"
        }
        if (retention == RuntimeWorkloadRetention.QUARANTINE ||
            restartFailureCount >= 3 ||
            overChildBudget
        ) {
            return RuntimeLifecycleLayer.ANOMALY_POOL to
                "abnormal_or_quarantined_runtime_requires_review_before_cleanup"
        }
        if (backgroundDecay.lifecycleState == RuntimeAppVisibilityState.FOREGROUND &&
            retention == RuntimeWorkloadRetention.KEEP
        ) {
            return RuntimeLifecycleLayer.FOREGROUND_PRIORITY to
                "foreground_active_runtime_gets_priority_until_background_decay"
        }
        return RuntimeLifecycleLayer.LEASE_POOL to
            "ordinary_or_unknown_background_runtime_uses_activity_lease"
    }

    private fun RuntimeWorkloadRegistryEntry.resolveDisposition(
        backgroundCandidate: Boolean,
        budgetState: RuntimeBudgetState,
        budgetActions: List<RuntimeBudgetAction>,
        backgroundPhase: RuntimeBackgroundDecayPhase,
        layer: RuntimeLifecycleLayer,
        activityState: RuntimeLifecycleActivityState,
        leaseRemainingMs: Long,
        leaseAtMax: Boolean
    ): Pair<RuntimeLifecycleReclaimDisposition, String> {
        if (workloadClass == RuntimeWorkloadClass.SYSTEM_CORE) {
            return RuntimeLifecycleReclaimDisposition.KEEP to
                "system_core_protected_by_android_lifecycle_no_cleanup_or_terminate"
        }
        if (workloadClass == RuntimeWorkloadClass.PINNED_SERVICE &&
            retention == RuntimeWorkloadRetention.KEEP
        ) {
            return RuntimeLifecycleReclaimDisposition.KEEP to
                "pinned_service_keep_observe_restart_policy_if_unhealthy"
        }
        if (retention == RuntimeWorkloadRetention.QUARANTINE ||
            budgetState == RuntimeBudgetState.QUARANTINED
        ) {
            return RuntimeLifecycleReclaimDisposition.MANUAL_ONLY_QUARANTINED to
                "already_quarantined_manual_foreground_recovery_only"
        }
        if (budgetState == RuntimeBudgetState.REPEAT_OFFENDER ||
            RuntimeBudgetAction.QUARANTINE in budgetActions
        ) {
            return RuntimeLifecycleReclaimDisposition.WOULD_QUARANTINE to
                "repeat_offender_or_budget_policy_requests_quarantine"
        }
        if (RuntimeBudgetAction.TERMINATE_WORKLOAD in budgetActions ||
            RuntimeBudgetAction.RECOVERY_CUTOFF in budgetActions
        ) {
            return RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD to
                "kf_survival_budget_policy_reviews_workload_termination"
        }
        if (RuntimeBudgetAction.RESTART_MAIN in budgetActions) {
            return RuntimeLifecycleReclaimDisposition.WOULD_RESTART_MAIN to
                "hard_pressure_budget_policy_reviews_main_restart"
        }
        if (RuntimeBudgetAction.TERMINATE_CHILDREN in budgetActions ||
            RuntimeBudgetAction.FREEZE_SHORT in budgetActions ||
            overChildBudget
        ) {
            return RuntimeLifecycleReclaimDisposition.WOULD_RECLAIM_CHILDREN to
                "over_child_budget_or_hard_pressure_reviews_child_reclaim"
        }
        if (layer == RuntimeLifecycleLayer.FOREGROUND_PRIORITY) {
            return RuntimeLifecycleReclaimDisposition.KEEP to
                "foreground_priority_layer_protected_until_background_decay"
        }
        if (layer == RuntimeLifecycleLayer.LEASE_POOL &&
            (activityState == RuntimeLifecycleActivityState.EXPIRED || leaseRemainingMs <= 0L)
        ) {
            if (leaseAtMax && retention == RuntimeWorkloadRetention.LEASE) {
                return RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE to
                    "lease_pool_max_total_reached_user_lock_candidate"
            }
            return if (retention == RuntimeWorkloadRetention.LEASE) {
                RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE to
                    "lease_pool_expired_without_real_activity"
            } else {
                RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP to
                    "lease_pool_unknown_or_cleanup_candidate_expired"
            }
        }
        if (layer == RuntimeLifecycleLayer.LEASE_POOL &&
            activityState == RuntimeLifecycleActivityState.ABNORMAL
        ) {
            return RuntimeLifecycleReclaimDisposition.WOULD_QUARANTINE to
                "lease_pool_abnormal_activity_requires_quarantine_review"
        }
        if (retention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE ||
            workloadClass == RuntimeWorkloadClass.STRAY ||
            workloadClass == RuntimeWorkloadClass.UNKNOWN
        ) {
            return RuntimeLifecycleReclaimDisposition.WATCH_LEASE to
                "cleanup_candidate_uses_activity_lease_until_expired_or_budget_over"
        }
        if (backgroundCandidate &&
            retention == RuntimeWorkloadRetention.LEASE &&
            backgroundPhase != RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE &&
            backgroundPhase != RuntimeBackgroundDecayPhase.BACKGROUND_GRACE
        ) {
            return if (activityState == RuntimeLifecycleActivityState.EXPIRED || leaseRemainingMs <= 0L) {
                RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE to
                    "background_decay_reviews_expired_transient_or_lease_workload"
            } else {
                RuntimeLifecycleReclaimDisposition.WATCH_LEASE to
                    "background_decay_keeps_active_or_unexpired_lease_workload"
            }
        }
        if (retention == RuntimeWorkloadRetention.LEASE) {
            return RuntimeLifecycleReclaimDisposition.WATCH_LEASE to
                "lease_valid_observe_runtime_idle_or_budget"
        }
        return RuntimeLifecycleReclaimDisposition.KEEP to "keep_retention_or_current_foreground_allowance"
    }

    private data class ActivityObservation(
        val state: RuntimeLifecycleActivityState,
        val firstSeenAtMs: Long,
        val lastActivityAtMs: Long,
        val leaseWindow: LeaseWindow,
        val scorePercent: Int,
        val expiredSettlementCount: Int,
        val reason: String
    )

    private data class LeaseWindow(
        val baseTtlMs: Long,
        val maxTotalMs: Long,
        val remainingMs: Long,
        val atMax: Boolean,
        val extensionMs: Long = 0L,
        val expiredGraceMs: Long = 0L,
        val expiredGraceRemainingMs: Long = 0L,
        val expiredGraceActive: Boolean = false,
        val expireAtMs: Long = 0L
    )

    private data class ActivityRecord(
        val firstSeenAtMs: Long,
        val lastObservedAtMs: Long,
        val lastActivityAtMs: Long,
        val processCount: Int,
        val rssKb: Long,
        val cpuTimeTicks: Long,
        val ioReadBytes: Long,
        val ioWriteBytes: Long,
        val leaseExpireAtMs: Long = 0L,
        val firstExpiredAtMs: Long = 0L,
        val maxTotalWindowStartedAtMs: Long = firstSeenAtMs,
        val activityScoreSum: Long = 0L,
        val activitySampleCount: Int = 0,
        val trustedActivitySampleCount: Int = 0,
        val maxActivityScorePercent: Int = 0,
        val lastActivityLevelRank: Int = 0,
        val previousActivityLevelRank: Int = 0,
        val expiredSettlementCount: Int = 0,
        val maxTotalUnlockCount: Int = 0
    )

    private enum class LeaseActivityLevel(val rank: Int, val scorePercent: Int) {
        NONE(0, 0),
        WEAK(1, 10),
        TRUSTED(2, 45),
        STRONG(3, 70),
        MULTI_EVIDENCE_STRONG(4, 90)
    }

    private data class ActivitySignal(
        val source: String,
        val level: LeaseActivityLevel
    )

    private object RuntimeLifecycleActivityTracker {
        private const val MAX_RECORDS = 256

        private val records = linkedMapOf<String, ActivityRecord>()

        fun observe(
            entry: RuntimeWorkloadRegistryEntry,
            layer: RuntimeLifecycleLayer,
            backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
            leasePolicy: RuntimeLifecycleLeasePolicy,
            leasePoolMemoryBudgetKb: Long,
            budgetState: RuntimeBudgetState,
            budgetActions: List<RuntimeBudgetAction>,
            now: Long
        ): ActivityObservation {
            if (layer == RuntimeLifecycleLayer.SYSTEM_CORE ||
                layer == RuntimeLifecycleLayer.USER_LOCKED ||
                layer == RuntimeLifecycleLayer.FOREGROUND_PRIORITY
            ) {
                val firstSeenAt = entry.lastStartedAt ?: entry.lastSeenAt ?: now
                val record = ActivityRecord(
                    firstSeenAtMs = firstSeenAt,
                    lastObservedAtMs = now,
                    lastActivityAtMs = now,
                    processCount = entry.processCount,
                    rssKb = entry.rssKb,
                    cpuTimeTicks = entry.cpuTimeTicks,
                    ioReadBytes = entry.ioReadBytes,
                    ioWriteBytes = entry.ioWriteBytes,
                    maxTotalWindowStartedAtMs = firstSeenAt
                )
                remember(entry.workloadId, record)
                return ActivityObservation(
                    state = RuntimeLifecycleActivityState.PROTECTED,
                    firstSeenAtMs = firstSeenAt,
                    lastActivityAtMs = now,
                    leaseWindow = resolveLeaseWindow(
                        layer = layer,
                        activityState = RuntimeLifecycleActivityState.PROTECTED,
                        firstSeenAt = firstSeenAt,
                        lastActivityAt = now,
                        backgroundDecay = backgroundDecay,
                        leasePolicy = leasePolicy,
                        now = now
                    ),
                    scorePercent = 0,
                    expiredSettlementCount = 0,
                    reason = "protected_layer_not_scored_by_lease_pool"
                )
            }

            val previous = records[entry.workloadId].forSameProcessIncarnation(entry)
            val firstSeenAt = previous?.firstSeenAtMs ?: entry.lastStartedAt ?: entry.lastSeenAt ?: now
            val previousActivityAt = previous?.lastActivityAtMs ?: firstSeenAt
            val processDelta = if (previous == null) 0 else kotlin.math.abs(entry.processCount - previous.processCount)
            val cpuDelta = (entry.cpuTimeTicks - (previous?.cpuTimeTicks ?: entry.cpuTimeTicks)).coerceAtLeast(0L)
            val rssDelta = if (previous == null) 0L else kotlin.math.abs(entry.rssKb - previous.rssKb)
            val elapsedMs = (now - (previous?.lastObservedAtMs ?: now)).coerceAtLeast(1L)
            val rssMinDeltaKb = leasePolicy.rssMinDeltaKb.coerceAtLeast(0L)
            val rssDeltaPercent = leasePolicy.rssDeltaPercent.coerceAtLeast(0)
            val previousRssKb = previous?.rssKb ?: entry.rssKb
            val rssDeltaPercentValue = if (previous != null && previousRssKb > 0L) {
                ((rssDelta * 100L) / previousRssKb).coerceAtLeast(0L).toInt()
            } else {
                0
            }
            val rssThreshold = maxOf(
                rssMinDeltaKb,
                previousRssKb.times(rssDeltaPercent.toLong()).div(100L)
            ).coerceAtLeast(1L)
            val memoryLevel = memoryActivityLevel(
                rssDeltaKb = rssDelta,
                rssDeltaPercentValue = rssDeltaPercentValue,
                rssThresholdKb = rssThreshold,
                minPercent = rssDeltaPercent,
                strongDeltaKb = leasePolicy.rssStrongDeltaKb,
                strongDeltaPercent = leasePolicy.rssStrongDeltaPercent
            )
            val cpuPercent = cpuPercentForWindow(
                cpuDelta = cpuDelta,
                elapsedMs = elapsedMs,
                ticksPerSecond = leasePolicy.cpuTicksPerSecond
            )
            val cpuLevel = cpuActivityLevel(cpuPercent, leasePolicy)
            val processLevel = if (processDelta > 0) {
                LeaseActivityLevel.WEAK
            } else {
                LeaseActivityLevel.NONE
            }
            val foregroundLike = entry.isForegroundOrTerminalLikely()
            val foregroundLevel = if (foregroundLike) LeaseActivityLevel.STRONG else LeaseActivityLevel.NONE
            val networkLikely = entry.isNetworkLikelyWorkload()
            val networkLevel = if (networkLikely) LeaseActivityLevel.TRUSTED else LeaseActivityLevel.NONE
            val signals = listOf(
                ActivitySignal("memory", memoryLevel),
                ActivitySignal("cpu", cpuLevel),
                ActivitySignal("processTree", processLevel),
                ActivitySignal("foreground", foregroundLevel),
                ActivitySignal("network", networkLevel)
            )
            val nonProcessSignals = signals.filter { it.source != "processTree" }
            val trustedEvidenceCount = signals.count { it.level.rank >= LeaseActivityLevel.TRUSTED.rank }
            val nonProcessTrustedEvidenceCount =
                nonProcessSignals.count { it.level.rank >= LeaseActivityLevel.TRUSTED.rank }
            val strongestNonProcessSignal =
                nonProcessSignals.maxByOrNull { it.level.rank } ?: ActivitySignal("none", LeaseActivityLevel.NONE)
            val strongestSignal = signals.maxByOrNull { it.level.rank } ?: ActivitySignal("none", LeaseActivityLevel.NONE)
            val processTreeChanged = processDelta > 0
            val baseActivityLevel = when {
                nonProcessTrustedEvidenceCount >= 2 &&
                    strongestNonProcessSignal.level.rank >= LeaseActivityLevel.TRUSTED.rank ->
                    LeaseActivityLevel.MULTI_EVIDENCE_STRONG

                processTreeChanged &&
                    strongestNonProcessSignal.level.rank >= LeaseActivityLevel.STRONG.rank ->
                    LeaseActivityLevel.MULTI_EVIDENCE_STRONG

                processTreeChanged &&
                    strongestNonProcessSignal.level.rank >= LeaseActivityLevel.TRUSTED.rank ->
                    LeaseActivityLevel.STRONG

                strongestNonProcessSignal.level.rank > LeaseActivityLevel.NONE.rank ->
                    strongestNonProcessSignal.level

                else -> strongestSignal.level
            }
            val highCost = isHighCostLease(
                rssKb = entry.rssKb,
                leasePolicy = leasePolicy,
                leasePoolMemoryBudgetKb = leasePoolMemoryBudgetKb
            )
            val extensionBeforeCostMs = extensionMsFor(baseActivityLevel, leasePolicy)
            val highCostUpgradeMs = if (
                highCost &&
                baseActivityLevel.rank >= LeaseActivityLevel.TRUSTED.rank &&
                extensionBeforeCostMs > 0L
            ) {
                leasePolicy.highCostUpgradeMs.coerceAtLeast(0L)
            } else {
                0L
            }
            val extensionMs = (extensionBeforeCostMs + highCostUpgradeMs)
                .coerceAtMost(leasePolicy.maxExtensionPerSettlementMs.coerceAtLeast(0L))
            val latestActivityScorePercent = baseActivityLevel.scorePercent
            val previousWindowStartedAt = previous?.maxTotalWindowStartedAtMs ?: firstSeenAt
            val maxTotalMs = leasePolicy.maxTotalLeaseMs.coerceAtLeast(leasePolicy.initialLeaseMs)
            val scoreSumForWindow = (previous?.activityScoreSum ?: 0L) + latestActivityScorePercent.toLong()
            val sampleCountForWindow = (previous?.activitySampleCount ?: 0) + 1
            val durableActivity = baseActivityLevel.rank >= LeaseActivityLevel.TRUSTED.rank &&
                nonProcessTrustedEvidenceCount > 0
            val trustedSampleCountForWindow = (previous?.trustedActivitySampleCount ?: 0) +
                if (durableActivity) 1 else 0
            val averageScoreForWindow = if (sampleCountForWindow > 0) {
                (scoreSumForWindow / sampleCountForWindow).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val activeSamplePercentForWindow = if (sampleCountForWindow > 0) {
                (trustedSampleCountForWindow * 100 / sampleCountForWindow).coerceIn(0, 100)
            } else {
                0
            }
            val maxScoreForWindow = maxOf(previous?.maxActivityScorePercent ?: 0, latestActivityScorePercent)
            val maxTotalDeadlineReached = now >= previousWindowStartedAt + maxTotalMs
            val recentStrongActivity = (durableActivity && baseActivityLevel.rank >= LeaseActivityLevel.STRONG.rank) ||
                (previous?.lastActivityLevelRank ?: 0) >= LeaseActivityLevel.STRONG.rank
            val previousExpiredSettlementCount = previous?.expiredSettlementCount ?: 0
            val unlockAllowed = maxTotalDeadlineReached &&
                latestActivityScorePercent >= leasePolicy.maxTotalUnlockLatestScorePercent &&
                averageScoreForWindow >= leasePolicy.maxTotalUnlockAverageScorePercent &&
                activeSamplePercentForWindow >= leasePolicy.maxTotalUnlockActiveSamplePercent &&
                recentStrongActivity &&
                previousExpiredSettlementCount == 0
            val maxTotalWindowStartedAt = if (unlockAllowed) now else previousWindowStartedAt
            val nextScoreSum = if (unlockAllowed) latestActivityScorePercent.toLong() else scoreSumForWindow
            val nextSampleCount = if (unlockAllowed) 1 else sampleCountForWindow
            val nextTrustedSampleCount = if (unlockAllowed) {
                if (durableActivity) 1 else 0
            } else {
                trustedSampleCountForWindow
            }
            val nextMaxScore = if (unlockAllowed) latestActivityScorePercent else maxScoreForWindow
            val nextUnlockCount = (previous?.maxTotalUnlockCount ?: 0) + if (unlockAllowed) 1 else 0
            val abnormal = layer == RuntimeLifecycleLayer.ANOMALY_POOL ||
                budgetState.severity() >= RuntimeBudgetState.REPEAT_OFFENDER.severity() ||
                RuntimeBudgetAction.QUARANTINE in budgetActions ||
                entry.restartFailureCount >= 3 ||
                entry.overChildBudget
            val lastActivityAt = when {
                abnormal -> now
                extensionMs > 0L -> now
                else -> previousActivityAt
            }
            val observedLease = resolveObservedLeaseWindow(
                layer = layer,
                firstSeenAt = firstSeenAt,
                previous = previous,
                leasePolicy = leasePolicy,
                extensionMs = extensionMs,
                maxTotalWindowStartedAt = maxTotalWindowStartedAt,
                maxTotalUnlocked = unlockAllowed,
                now = now
            )
            val state = when {
                abnormal -> RuntimeLifecycleActivityState.ABNORMAL
                observedLease.remainingMs <= 0L -> RuntimeLifecycleActivityState.EXPIRED
                baseActivityLevel.rank >= LeaseActivityLevel.TRUSTED.rank -> RuntimeLifecycleActivityState.ACTIVE
                baseActivityLevel == LeaseActivityLevel.WEAK -> RuntimeLifecycleActivityState.WEAK_ACTIVITY
                else -> RuntimeLifecycleActivityState.COOLING
            }
            val leaseWindow = if (abnormal) {
                resolveLeaseWindow(
                    layer = layer,
                    activityState = RuntimeLifecycleActivityState.ABNORMAL,
                    firstSeenAt = firstSeenAt,
                    lastActivityAt = lastActivityAt,
                    backgroundDecay = backgroundDecay,
                    leasePolicy = leasePolicy,
                    now = now
                )
            } else {
                observedLease
            }
            val storedFirstExpiredAt = when {
                leaseWindow.expiredGraceActive || leaseWindow.remainingMs <= 0L ->
                    previous?.firstExpiredAtMs?.takeIf { it > 0L } ?: now
                else -> 0L
            }
            val nextExpiredSettlementCount = if (leaseWindow.expiredGraceActive || leaseWindow.remainingMs <= 0L) {
                previousExpiredSettlementCount + 1
            } else {
                0
            }
            remember(
                entry.workloadId,
                ActivityRecord(
                    firstSeenAtMs = firstSeenAt,
                    lastObservedAtMs = now,
                    lastActivityAtMs = lastActivityAt,
                    processCount = entry.processCount,
                    rssKb = entry.rssKb,
                    cpuTimeTicks = entry.cpuTimeTicks,
                    ioReadBytes = entry.ioReadBytes,
                    ioWriteBytes = entry.ioWriteBytes,
                    leaseExpireAtMs = leaseWindow.expireAtMs,
                    firstExpiredAtMs = storedFirstExpiredAt,
                    maxTotalWindowStartedAtMs = maxTotalWindowStartedAt,
                    activityScoreSum = nextScoreSum,
                    activitySampleCount = nextSampleCount,
                    trustedActivitySampleCount = nextTrustedSampleCount,
                    maxActivityScorePercent = nextMaxScore,
                    lastActivityLevelRank = baseActivityLevel.rank,
                    previousActivityLevelRank = previous?.lastActivityLevelRank ?: 0,
                    expiredSettlementCount = nextExpiredSettlementCount,
                    maxTotalUnlockCount = nextUnlockCount
                )
            )
            val activitySummary = signals.joinToString("|") { "${it.source}:${it.level.name}" }
            return ActivityObservation(
                state = state,
                firstSeenAtMs = firstSeenAt,
                lastActivityAtMs = lastActivityAt,
                leaseWindow = leaseWindow,
                scorePercent = latestActivityScorePercent,
                expiredSettlementCount = nextExpiredSettlementCount,
                reason = "activity=${state.name},activityLevel=${baseActivityLevel.name},extensionMs=$extensionMs," +
                    "extensionBeforeCostMs=$extensionBeforeCostMs,highCost=$highCost," +
                    "highCostUpgradeMs=$highCostUpgradeMs,activitySignals=$activitySummary," +
                    "trustedEvidenceCount=$trustedEvidenceCount," +
                    "nonProcessTrustedEvidenceCount=$nonProcessTrustedEvidenceCount," +
                    "processTreeChanged=$processTreeChanged,processDelta=$processDelta," +
                    "cpuDelta=$cpuDelta,cpuPercent=$cpuPercent,rssDelta=$rssDelta," +
                    "rssDeltaPercent=$rssDeltaPercentValue,rssThreshold=$rssThreshold," +
                    "latestActivityScorePercent=$latestActivityScorePercent," +
                    "windowAverageScorePercent=$averageScoreForWindow," +
                    "windowTrustedSamplePercent=$activeSamplePercentForWindow," +
                    "windowMaxScorePercent=$maxScoreForWindow," +
                    "recentStrongActivity=$recentStrongActivity,maxTotalUnlocked=$unlockAllowed," +
                    "unlockCount=$nextUnlockCount,expiredSettlementCount=$nextExpiredSettlementCount," +
                    "expiredGraceActive=${leaseWindow.expiredGraceActive}"
            )
        }

        private fun remember(key: String, record: ActivityRecord) {
            records[key] = record
            while (records.size > MAX_RECORDS) {
                val firstKey = records.keys.firstOrNull() ?: break
                records.remove(firstKey)
            }
        }

        private fun ActivityRecord?.forSameProcessIncarnation(
            entry: RuntimeWorkloadRegistryEntry
        ): ActivityRecord? {
            if (this == null) return null
            val startedAt = entry.lastStartedAt ?: return this
            return takeIf { firstSeenAtMs == startedAt }
        }

        private fun resolveObservedLeaseWindow(
            layer: RuntimeLifecycleLayer,
            firstSeenAt: Long,
            previous: ActivityRecord?,
            leasePolicy: RuntimeLifecycleLeasePolicy,
            extensionMs: Long,
            maxTotalWindowStartedAt: Long,
            maxTotalUnlocked: Boolean,
            now: Long
        ): LeaseWindow {
            if (layer == RuntimeLifecycleLayer.SYSTEM_CORE ||
                layer == RuntimeLifecycleLayer.USER_LOCKED ||
                layer == RuntimeLifecycleLayer.FOREGROUND_PRIORITY
            ) {
                return LeaseWindow(
                    baseTtlMs = 0L,
                    maxTotalMs = 0L,
                    remainingMs = Long.MAX_VALUE,
                    atMax = false
                )
            }
            val initialLeaseMs = leasePolicy.initialLeaseMs.coerceAtLeast(0L)
            val maxTotalMs = leasePolicy.maxTotalLeaseMs.coerceAtLeast(initialLeaseMs)
            val previousExpireAt = previous?.leaseExpireAtMs
                ?.takeIf { it > 0L }
                ?: firstSeenAt + initialLeaseMs
            val proposedExpireAt = if (maxTotalUnlocked) {
                now + maxOf(leasePolicy.maxTotalUnlockInitialMs.coerceAtLeast(0L), extensionMs)
            } else if (extensionMs > 0L) {
                maxOf(previousExpireAt, now + extensionMs)
            } else {
                previousExpireAt
            }
            val hardDeadline = maxTotalWindowStartedAt + maxTotalMs
            val expireAt = minOf(proposedExpireAt, hardDeadline)
            val rawRemainingMs = (expireAt - now).coerceAtMost(maxTotalMs)
            if (rawRemainingMs > 0L) {
                return LeaseWindow(
                    baseTtlMs = initialLeaseMs,
                    maxTotalMs = maxTotalMs,
                    remainingMs = rawRemainingMs,
                    atMax = now >= hardDeadline,
                    extensionMs = extensionMs,
                    expiredGraceMs = leasePolicy.expiredGraceMs.coerceAtLeast(0L),
                    expireAtMs = expireAt
                )
            }
            val expiredGraceMs = leasePolicy.expiredGraceMs.coerceAtLeast(0L)
            val firstExpiredAt = previous?.firstExpiredAtMs?.takeIf { it > 0L } ?: now
            val graceRemainingMs = (expiredGraceMs - (now - firstExpiredAt).coerceAtLeast(0L))
                .coerceAtLeast(0L)
            return LeaseWindow(
                baseTtlMs = initialLeaseMs,
                maxTotalMs = maxTotalMs,
                remainingMs = graceRemainingMs,
                atMax = now >= hardDeadline,
                extensionMs = extensionMs,
                expiredGraceMs = expiredGraceMs,
                expiredGraceRemainingMs = graceRemainingMs,
                expiredGraceActive = graceRemainingMs > 0L,
                expireAtMs = expireAt
            )
        }

        private fun memoryActivityLevel(
            rssDeltaKb: Long,
            rssDeltaPercentValue: Int,
            rssThresholdKb: Long,
            minPercent: Int,
            strongDeltaKb: Long,
            strongDeltaPercent: Int
        ): LeaseActivityLevel {
            if (rssDeltaKb < rssThresholdKb || rssDeltaPercentValue < minPercent.coerceAtLeast(1)) {
                return if (rssDeltaKb > 0L) LeaseActivityLevel.WEAK else LeaseActivityLevel.NONE
            }
            val safeStrongDelta = strongDeltaKb.coerceAtLeast(rssThresholdKb.coerceAtLeast(1L))
            val safeStrongPercent = strongDeltaPercent.coerceAtLeast(minPercent.coerceAtLeast(1))
            return if (rssDeltaKb >= safeStrongDelta && rssDeltaPercentValue >= safeStrongPercent) {
                LeaseActivityLevel.STRONG
            } else {
                LeaseActivityLevel.TRUSTED
            }
        }

        private fun cpuPercentForWindow(
            cpuDelta: Long,
            elapsedMs: Long,
            ticksPerSecond: Long
        ): Int {
            if (cpuDelta <= 0L || elapsedMs <= 0L || ticksPerSecond <= 0L) return 0
            return ((cpuDelta * 100_000L) / (elapsedMs * ticksPerSecond))
                .coerceIn(0L, 10_000L)
                .toInt()
        }

        private fun cpuActivityLevel(
            cpuPercent: Int,
            leasePolicy: RuntimeLifecycleLeasePolicy
        ): LeaseActivityLevel {
            return when {
                cpuPercent >= leasePolicy.cpuStrongPercent -> LeaseActivityLevel.STRONG
                cpuPercent >= leasePolicy.cpuTrustedPercent -> LeaseActivityLevel.TRUSTED
                cpuPercent >= leasePolicy.cpuWeakPercent -> LeaseActivityLevel.WEAK
                else -> LeaseActivityLevel.NONE
            }
        }

        private fun extensionMsFor(
            activityLevel: LeaseActivityLevel,
            leasePolicy: RuntimeLifecycleLeasePolicy
        ): Long {
            return when (activityLevel) {
                LeaseActivityLevel.NONE,
                LeaseActivityLevel.WEAK -> 0L
                LeaseActivityLevel.TRUSTED -> leasePolicy.trustedActivityExtensionMs
                LeaseActivityLevel.STRONG -> leasePolicy.strongActivityExtensionMs
                LeaseActivityLevel.MULTI_EVIDENCE_STRONG -> leasePolicy.multiEvidenceActivityExtensionMs
            }.coerceAtLeast(0L)
        }

        private fun isHighCostLease(
            rssKb: Long,
            leasePolicy: RuntimeLifecycleLeasePolicy,
            leasePoolMemoryBudgetKb: Long
        ): Boolean {
            if (rssKb >= leasePolicy.highCostRssKb.coerceAtLeast(0L)) return true
            if (leasePoolMemoryBudgetKb <= 0L) return false
            return rssKb * 100L >= leasePoolMemoryBudgetKb * leasePolicy.highCostLeasePoolBudgetPercent
        }
    }

    private fun RuntimeWorkloadRegistryEntry.isForegroundOrTerminalLikely(): Boolean {
        if (workloadClass == RuntimeWorkloadClass.INTERACTIVE) return true
        val text = "$title $source $reason".lowercase()
        return text.contains("foreground") ||
            text.contains("terminal") ||
            text.contains("tty") ||
            text.contains("interactive")
    }

    private fun RuntimeWorkloadRegistryEntry.isNetworkLikelyWorkload(): Boolean {
        val text = "$workloadId $title $source $reason".lowercase()
        val needles = listOf(
            "curl",
            "wget",
            "aria2",
            "git clone",
            "git fetch",
            "git pull",
            "apt ",
            "apt-get",
            "pip ",
            "pip3 ",
            "npm ",
            "pnpm ",
            "yarn ",
            "uv ",
            "uvx ",
            "cargo install",
            "go get",
            "http://",
            "https://",
            "github.com",
            "pypi",
            "registry.npmjs"
        )
        return needles.any { it in text }
    }

    private fun syntheticActivityFor(
        lastEventAtMs: Long,
        now: Long,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot
    ): Pair<RuntimeLifecycleActivityState, String> {
        if (lastEventAtMs <= 0L) {
            return RuntimeLifecycleActivityState.COOLING to "synthetic_tracee_has_no_recent_event_timestamp"
        }
        val ageMs = (now - lastEventAtMs).coerceAtLeast(0L)
        val activeWindowMs = backgroundDecay.policyGraceMs.coerceAtLeast(30_000L)
        val expireWindowMs = backgroundDecay.policyTransientCleanupMs.coerceAtLeast(activeWindowMs)
        return when {
            ageMs <= activeWindowMs -> RuntimeLifecycleActivityState.ACTIVE to
                "synthetic_tracee_recent_proot_event_ageMs_$ageMs"
            ageMs >= expireWindowMs -> RuntimeLifecycleActivityState.EXPIRED to
                "synthetic_tracee_idle_beyond_expire_window_ageMs_$ageMs"
            else -> RuntimeLifecycleActivityState.COOLING to
                "synthetic_tracee_cooling_ageMs_$ageMs"
        }
    }

    private fun syntheticLeaseWindow(
        layer: RuntimeLifecycleLayer,
        activityState: RuntimeLifecycleActivityState,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        leasePolicy: RuntimeLifecycleLeasePolicy,
        lastEventAtMs: Long,
        now: Long
    ): LeaseWindow {
        val firstSeen = lastEventAtMs.takeIf { it > 0L } ?: now
        val lastActivity = if (activityState == RuntimeLifecycleActivityState.ACTIVE) {
            now
        } else {
            firstSeen
        }
        return resolveLeaseWindow(
            layer = layer,
            activityState = activityState,
            firstSeenAt = firstSeen,
            lastActivityAt = lastActivity,
            backgroundDecay = backgroundDecay,
            leasePolicy = leasePolicy,
            now = now
        )
    }

    private fun resolveLeaseWindow(
        layer: RuntimeLifecycleLayer,
        activityState: RuntimeLifecycleActivityState,
        firstSeenAt: Long,
        lastActivityAt: Long,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        leasePolicy: RuntimeLifecycleLeasePolicy,
        now: Long
    ): LeaseWindow {
        if (layer == RuntimeLifecycleLayer.SYSTEM_CORE ||
            layer == RuntimeLifecycleLayer.USER_LOCKED ||
            layer == RuntimeLifecycleLayer.FOREGROUND_PRIORITY
        ) {
            return LeaseWindow(
                baseTtlMs = 0L,
                maxTotalMs = 0L,
                remainingMs = Long.MAX_VALUE,
                atMax = false
            )
        }
        val baseTtlMs = when (activityState) {
            RuntimeLifecycleActivityState.ACTIVE ->
                leasePolicy.activeLeaseTtlMs
            RuntimeLifecycleActivityState.WEAK_ACTIVITY ->
                leasePolicy.weakActivityLeaseTtlMs
            RuntimeLifecycleActivityState.ABNORMAL ->
                leasePolicy.coolingLeaseTtlMs
            else ->
                leasePolicy.coolingLeaseTtlMs
        }.coerceAtLeast(0L)
        val maxTotalMs = leasePolicy.maxTotalLeaseMs
            .coerceAtLeast(backgroundDecay.policyGraceMs.coerceAtLeast(60_000L))
        val idleAgeMs = (now - lastActivityAt).coerceAtLeast(0L)
        val totalAgeMs = (now - firstSeenAt).coerceAtLeast(0L)
        val ttlRemainingMs = baseTtlMs - idleAgeMs
        val totalRemainingMs = maxTotalMs - totalAgeMs
        val remainingMs = minOf(ttlRemainingMs, totalRemainingMs).coerceAtLeast(0L)
        return LeaseWindow(
            baseTtlMs = baseTtlMs,
            maxTotalMs = maxTotalMs,
            remainingMs = remainingMs,
            atMax = totalRemainingMs <= 0L
        )
    }

    private fun leasePoolBudgetPercentFor(
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        pressure: RuntimePressureSnapshot,
        leasePolicy: RuntimeLifecycleLeasePolicy
    ): Int {
        if (pressure.level.ordinal >= RuntimePressureLevel.HIGH.ordinal ||
            budgetPressure.overallState.severity() >= RuntimeBudgetState.SOFT_PRESSURE.severity()
        ) {
            return leasePolicy.pressureLeasePoolBudgetPercent
        }
        return when (backgroundDecay.lifecycleState) {
            RuntimeAppVisibilityState.FOREGROUND -> leasePolicy.foregroundLeasePoolBudgetPercent
            RuntimeAppVisibilityState.UI_HIDDEN -> leasePolicy.hiddenLeasePoolBudgetPercent
            RuntimeAppVisibilityState.BACKGROUND_PRESSURE ->
                leasePolicy.backgroundPressureLeasePoolBudgetPercent
            RuntimeAppVisibilityState.LOW_MEMORY -> leasePolicy.lowMemoryLeasePoolBudgetPercent
        }
    }

    private fun List<RuntimeLifecycleReclaimItem>.withLeasePoolEvictionRanks(
        leasePoolOverBudget: Boolean
    ): List<RuntimeLifecycleReclaimItem> {
        if (!leasePoolOverBudget) return map { it.copy(reclaimRank = 0) }
        val ranked = filter {
            it.layer == RuntimeLifecycleLayer.LEASE_POOL &&
                (it.activityState == RuntimeLifecycleActivityState.EXPIRED || it.leaseRemainingMs <= 0L)
        }
            .sortedWith(
                compareBy<RuntimeLifecycleReclaimItem> { it.leaseRemainingMs }
                    .thenBy { it.workloadId }
            )
            .mapIndexed { index, item -> item.workloadId to (index + 1) }
            .toMap()
        return map { item ->
            val rank = ranked[item.workloadId] ?: 0
            if (rank == 0) {
                item.copy(reclaimRank = 0)
            } else {
                item.copy(
                    reclaimRank = rank,
                    disposition = if (item.retention == RuntimeWorkloadRetention.LEASE) {
                        RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE
                    } else {
                        RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP
                    },
                    reason = "lease_pool_over_memory_budget_reclaim_by_remaining_time"
                )
            }
        }.sortedWith(
            compareByDescending<RuntimeLifecycleReclaimItem> { it.disposition.severity() }
                .thenBy { if (it.reclaimRank == 0) Int.MAX_VALUE else it.reclaimRank }
                .thenBy { it.lane.ordinal }
                .thenBy { it.workloadId }
        )
    }

    private fun resolveState(
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        items: List<RuntimeLifecycleReclaimItem>
    ): RuntimeLifecycleReclaimPlanState {
        if (items.any {
                it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_QUARANTINE ||
                    it.disposition == RuntimeLifecycleReclaimDisposition.MANUAL_ONLY_QUARANTINED
            }
        ) {
            return RuntimeLifecycleReclaimPlanState.QUARANTINE_REVIEW
        }
        if (budgetPressure.overallState.severity() >= RuntimeBudgetState.HARD_PRESSURE.severity() ||
            items.any {
                it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_RECLAIM_CHILDREN ||
                    it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD ||
                    it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_RESTART_MAIN
            }
        ) {
            return RuntimeLifecycleReclaimPlanState.PRESSURE_RECLAIM_REVIEW
        }
        if (items.any { it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP }) {
            return RuntimeLifecycleReclaimPlanState.CLEANUP_REVIEW
        }
        if (items.any { it.reclaimRank > 0 }) {
            return RuntimeLifecycleReclaimPlanState.CLEANUP_REVIEW
        }
        if (items.any { it.disposition == RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE }) {
            return RuntimeLifecycleReclaimPlanState.LEASE_REVIEW
        }
        if (backgroundDecay.phase == RuntimeBackgroundDecayPhase.BACKGROUND_GRACE) {
            return RuntimeLifecycleReclaimPlanState.BACKGROUND_GRACE
        }
        return RuntimeLifecycleReclaimPlanState.FOREGROUND_KEEP
    }

    private fun recommendationFor(
        state: RuntimeLifecycleReclaimPlanState
    ): RuntimeLifecycleReclaimRecommendation {
        return when (state) {
            RuntimeLifecycleReclaimPlanState.FOREGROUND_KEEP,
            RuntimeLifecycleReclaimPlanState.BACKGROUND_GRACE ->
                RuntimeLifecycleReclaimRecommendation.KEEP_OBSERVING
            RuntimeLifecycleReclaimPlanState.LEASE_REVIEW ->
                RuntimeLifecycleReclaimRecommendation.REVIEW_BACKGROUND_LEASES
            RuntimeLifecycleReclaimPlanState.CLEANUP_REVIEW ->
                RuntimeLifecycleReclaimRecommendation.REVIEW_CLEANUP_CANDIDATES
            RuntimeLifecycleReclaimPlanState.PRESSURE_RECLAIM_REVIEW ->
                RuntimeLifecycleReclaimRecommendation.REVIEW_PRESSURE_RECLAIM
            RuntimeLifecycleReclaimPlanState.QUARANTINE_REVIEW ->
                RuntimeLifecycleReclaimRecommendation.REVIEW_QUARANTINE
        }
    }

    private fun buildReason(
        state: RuntimeLifecycleReclaimPlanState,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot
    ): String {
        return "state=${state.name},lifecycle=${backgroundDecay.lifecycleState.name}/" +
            "${backgroundDecay.phase.name},budget=${budgetPressure.overallState.name}," +
            "pressure=${pressureConsumer.state.name}/${pressureConsumer.prootPressureScore}"
    }

    private fun RuntimeBudgetState.severity(): Int {
        return when (this) {
            RuntimeBudgetState.HEALTHY -> 0
            RuntimeBudgetState.NEAR_BUDGET -> 1
            RuntimeBudgetState.SOFT_PRESSURE -> 2
            RuntimeBudgetState.HARD_PRESSURE -> 3
            RuntimeBudgetState.THREATENING_KF -> 4
            RuntimeBudgetState.REPEAT_OFFENDER -> 5
            RuntimeBudgetState.QUARANTINED -> 6
        }
    }

    private fun RuntimeLifecycleReclaimDisposition.severity(): Int {
        return when (this) {
            RuntimeLifecycleReclaimDisposition.KEEP -> 0
            RuntimeLifecycleReclaimDisposition.WATCH_LEASE -> 1
            RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE -> 2
            RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP -> 3
            RuntimeLifecycleReclaimDisposition.WOULD_RECLAIM_CHILDREN -> 4
            RuntimeLifecycleReclaimDisposition.WOULD_RESTART_MAIN -> 5
            RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD -> 6
            RuntimeLifecycleReclaimDisposition.WOULD_QUARANTINE -> 7
            RuntimeLifecycleReclaimDisposition.MANUAL_ONLY_QUARANTINED -> 8
        }
    }
}

private fun String?.toLifecycleReclaimEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
