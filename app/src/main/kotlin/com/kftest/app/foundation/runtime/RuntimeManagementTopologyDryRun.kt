package com.kftest.app.foundation.runtime

enum class RuntimeManagementTopologyState {
    OBSERVING,
    SUBSTRATE_BLOCKED,
    PRESSURE_HELD,
    SUBSTRATE_READY,
    CANARY_SCAFFOLD_LOCKED,
    READY_FOR_MAINLINE_REVIEW
}

enum class RuntimeManagementTopologyRecommendation {
    KEEP_OBSERVING,
    REPAIR_TELEMETRY_SUBSTRATE,
    WAIT_FOR_PRESSURE_STABILITY,
    KEEP_CANARY_SCAFFOLD_LOCKED,
    REVIEW_TWO_SYSTEM_MAINLINE
}

data class RuntimeManagementTopologyDryRunSnapshot(
    val mode: String = "runtime_management_topology_observe_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeManagementTopologyState = RuntimeManagementTopologyState.OBSERVING,
    val recommendation: RuntimeManagementTopologyRecommendation =
        RuntimeManagementTopologyRecommendation.KEEP_OBSERVING,
    val sharedSubstrate: String = "proot_interception_telemetry",
    val prootManagementRole: String = "proot_resource_pressure_pool_lane_queue",
    val lifecycleManagementRole: String = "android_lifecycle_core_locked_foreground_lease_pool_anomaly_pool",
    val lifecycleLayerModel: String = "core_system_user_locked_foreground_priority_lease_pool_anomaly_pool",
    val canaryScaffoldRole: String = "rollout_safety_audit_not_business_system",
    val substrateHealthy: Boolean = false,
    val policySubstrateReason: String = "waiting_for_telemetry_health",
    val probeSubstrateClean: Boolean = false,
    val probeSubstrateReason: String = "waiting_for_fresh_probe_source",
    val prootManagementBlocked: Boolean = false,
    val lifecycleManagementBlocked: Boolean = false,
    val canaryScaffoldLocked: Boolean = true,
    val substrateModuleCount: Int = 0,
    val prootManagementModuleCount: Int = 0,
    val lifecycleManagementModuleCount: Int = 0,
    val canaryScaffoldModuleCount: Int = 0,
    val telemetryHealthState: ProotTelemetryHealthState = ProotTelemetryHealthState.NOT_STARTED,
    val pressureStabilityState: RuntimePressureStabilityState = RuntimePressureStabilityState.NO_SOURCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val prootManagementMainlineState: RuntimeProotManagementMainlineState =
        RuntimeProotManagementMainlineState.SUBSTRATE_BLOCKED,
    val prootManagementMainlineRecommendation: RuntimeProotManagementMainlineRecommendation =
        RuntimeProotManagementMainlineRecommendation.REPAIR_TELEMETRY_SUBSTRATE,
    val prootManagementPrimaryRiskAxis: String = "live_tracee_accumulation",
    val prootManagementKnownRiskUpperBoundLiveTracees: Int = 18,
    val prootManagementKnownSafeLowerBoundLiveTracees: Int = 17,
    val prootManagementRecommendedDefaultLiveTraceeSoftCap: Int = 16,
    val prootManagementAdaptivePolicyStatus: String = "bounded_observe_only",
    val prootManagementAdaptiveUserContext: String = "kf_foreground_performance_bias",
    val prootManagementAdaptiveResourceLimiter: String = "live_tracee_accumulation",
    val prootManagementAdaptiveEffectiveLiveTraceeSoftCap: Int = 16,
    val prootManagementAdaptiveConcurrencyPosture: String = "allow_until_effective_soft_cap",
    val prootManagementAdaptiveQueuePosture: String = "run_high_priority_queue_low_priority_when_over_cap",
    val prootCapacityRequestContract: String =
        "proot_requests_capacity_lifecycle_reviews_budget_android_executes",
    val prootCapacityRequestedAction: String = "NONE",
    val prootCapacityReviewNeeded: Boolean = false,
    val prootCapacityExpansionRequested: Boolean = false,
    val prootCapacityQueueRequested: Boolean = false,
    val prootCapacityDownscaleRequested: Boolean = false,
    val prootCapacityDownlineRequested: Boolean = false,
    val prootCapacityRequestReason: String = "single_proot_within_peak_or_queue_band",
    val prootCapacityExecutorState: RuntimeProotCapacityExecutorState =
        RuntimeProotCapacityExecutorState.IDLE,
    val prootCapacityExecutorRecommendation: RuntimeProotCapacityExecutorRecommendation =
        RuntimeProotCapacityExecutorRecommendation.KEEP_CURRENT_POOL,
    val prootCapacityExecutorPolicyEnabled: Boolean = false,
    val prootCapacityExecutorLaunchEligible: Boolean = false,
    val prootCapacityExecutorTargetBindingStatus: String = "not_requested",
    val prootCapacityExecutorConfiguredTargetId: String = "none",
    val prootCapacityExecutorRegisteredTargetId: String = "none",
    val prootCapacityExecutorRegisteredTargetKind: String = "none",
    val lifecyclePolicySurfaceState: RuntimeLifecyclePolicySurfaceState =
        RuntimeLifecyclePolicySurfaceState.WORKSPACE_MISSING,
    val lifecyclePolicySurfaceRecommendation: RuntimeLifecyclePolicySurfaceRecommendation =
        RuntimeLifecyclePolicySurfaceRecommendation.WAIT_FOR_WORKSPACE,
    val lifecyclePolicyLoadStatus: String = "unknown",
    val lifecyclePolicyPath: String? = null,
    val lifecyclePolicyAndroidExecutionOwner: Boolean = true,
    val lifecyclePolicyUbuntuPolicyAdvisory: Boolean = true,
    val lifecyclePolicyUbuntuDirectExecutionAllowed: Boolean = false,
    val lifecyclePolicySafetyFloorApplied: Boolean = false,
    val lifecyclePolicySafetyFloorAddedEnvelopeCount: Int = 0,
    val lifecyclePolicyProfileSurfaceState: RuntimeLifecyclePolicyProfileSurfaceState =
        RuntimeLifecyclePolicyProfileSurfaceState.WORKSPACE_MISSING,
    val lifecyclePolicyProfileSurfaceRecommendation:
        RuntimeLifecyclePolicyProfileSurfaceRecommendation =
        RuntimeLifecyclePolicyProfileSurfaceRecommendation.WAIT_FOR_WORKSPACE,
    val lifecyclePolicyActiveProfileGroup: RuntimeLifecyclePolicyProfileGroup =
        RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
    val lifecyclePolicyProfileSelectorKind: String = "policy_group_config_not_runtime_command",
    val lifecyclePolicyProfileGroupCount: Int = 0,
    val lifecyclePolicyProfileUbuntuDirectExecutionAllowed: Boolean = false,
    val lifecyclePolicyProfileConcurrencyPolicy: String = "lane_max_background_max_effective_max",
    val lifecyclePolicyProfileQueuePolicy: String = "priority_then_declaration_order_android_queue_only",
    val lifecyclePolicyProfileKfSafetyRule: String = "interactive_first_kf_survival_over_workload",
    val lifecycleIntentSurfaceState: RuntimeLifecycleIntentSurfaceState =
        RuntimeLifecycleIntentSurfaceState.WORKSPACE_MISSING,
    val lifecycleIntentSurfaceRecommendation: RuntimeLifecycleIntentSurfaceRecommendation =
        RuntimeLifecycleIntentSurfaceRecommendation.WAIT_FOR_WORKSPACE,
    val lifecycleIntentDeclaredCount: Int = 0,
    val lifecycleIntentAcceptedAdvisoryCount: Int = 0,
    val lifecycleIntentIgnoredDirectActionCount: Int = 0,
    val lifecycleIntentAndroidExecutionOwner: Boolean = true,
    val lifecycleIntentUbuntuAdvisory: Boolean = true,
    val lifecycleReclaimState: RuntimeLifecycleReclaimPlanState =
        RuntimeLifecycleReclaimPlanState.FOREGROUND_KEEP,
    val lifecycleReclaimRecommendation: RuntimeLifecycleReclaimRecommendation =
        RuntimeLifecycleReclaimRecommendation.KEEP_OBSERVING,
    val lifecycleReclaimReviewPending: Boolean = false,
    val lifecycleLeasePoolMemoryBudgetKb: Long = 0L,
    val lifecycleLeasePoolRssKb: Long = 0L,
    val lifecycleLeasePoolOverBudget: Boolean = false,
    val lifecycleLeasePoolEvictionCandidateCount: Int = 0,
    val lifecycleSystemCoreLayerCount: Int = 0,
    val lifecycleUserLockedLayerCount: Int = 0,
    val lifecycleForegroundPriorityLayerCount: Int = 0,
    val lifecycleLeasePoolLayerCount: Int = 0,
    val lifecycleAnomalyPoolLayerCount: Int = 0,
    val lifecycleProotExpansionBudgetState: RuntimeLifecycleProotExpansionBudgetState =
        RuntimeLifecycleProotExpansionBudgetState.NOT_REQUESTED,
    val lifecycleProotExpansionBudgetRecommendation: RuntimeLifecycleProotExpansionBudgetRecommendation =
        RuntimeLifecycleProotExpansionBudgetRecommendation.KEEP_SINGLE_PROOT,
    val lifecycleProotExpansionRequested: Boolean = false,
    val lifecycleProotExpansionCanReserve: Boolean = false,
    val lifecycleProotExpansionRequiredMemoryKb: Long = 0L,
    val lifecycleProotExpansionReclaimNeeded: Boolean = false,
    val lifecycleProotCapacityContract: String =
        "proot_requests_capacity_lifecycle_reviews_budget_android_executes",
    val lifecycleProotCapacityAction: String = "NONE",
    val lifecycleProotCapacityReviewNeeded: Boolean = false,
    val lifecycleProotCapacityQueueRequested: Boolean = false,
    val lifecycleProotCapacityDownscaleRequested: Boolean = false,
    val lifecycleProotCapacityDownlineRequested: Boolean = false,
    val lifecycleProotCapacityApproved: Boolean = false,
    val lifecycleProotCapacityActualStartCount: Int = 0,
    val lifecycleProotCapacityActualDownlineCount: Int = 0,
    val lifecycleProotCapacityActualQueueCount: Int = 0,
    val lifecycleProotCapacityActualReclaimCount: Int = 0,
    val lifecycleProotCapacityExecutionOwner: String = "android_control_plane",
    val lifecycleProotCapacityAndroidExecutorStatus: String =
        "budget_review_only_no_next_proot_launcher_enabled",
    val lifecycleProotCapacityUbuntuDirectControlAllowed: Boolean = false,
    val lifecycleProotCapacityProotDirectControlAllowed: Boolean = false,
    val lifecycleExpireLeaseCount: Int = 0,
    val lifecycleCleanupReviewCount: Int = 0,
    val lifecycleReclaimChildrenReviewCount: Int = 0,
    val lifecycleRestartReviewCount: Int = 0,
    val lifecycleTerminateReviewCount: Int = 0,
    val lifecycleQuarantineReviewCount: Int = 0,
    val workloadCount: Int = 0,
    val lanePolicyCount: Int = 0,
    val canaryAuditState: RuntimeCanaryAuditState = RuntimeCanaryAuditState.LOCKED,
    val canaryUnsafeActualActionCount: Int = 0,
    val mainlineNextFocus: String = "observe_two_systems",
    val reason: String = "waiting_for_runtime_snapshot"
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation substrateHealthy=$substrateHealthy " +
            "prootManagementBlocked=$prootManagementBlocked lifecycleBlocked=$lifecycleManagementBlocked " +
            "canaryLocked=$canaryScaffoldLocked modules=substrate:$substrateModuleCount," +
            "proot:$prootManagementModuleCount,lifecycle:$lifecycleManagementModuleCount," +
            "canary:$canaryScaffoldModuleCount next=$mainlineNextFocus enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("runtime_topology_mode=${mode.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_enforcement_mode=${enforcementMode.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_topology_generated_at=$generatedAtMs")
            appendLine("runtime_topology_state=${state.name}")
            appendLine("runtime_topology_recommendation=${recommendation.name}")
            appendLine("runtime_topology_shared_substrate=${sharedSubstrate.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_management_role=${prootManagementRole.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_management_role=${lifecycleManagementRole.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_layer_model=${lifecycleLayerModel.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_canary_scaffold_role=${canaryScaffoldRole.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_substrate_healthy=$substrateHealthy")
            appendLine("runtime_topology_policy_substrate_reason=${policySubstrateReason.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_probe_substrate_clean=$probeSubstrateClean")
            appendLine("runtime_topology_probe_substrate_reason=${probeSubstrateReason.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_management_blocked=$prootManagementBlocked")
            appendLine("runtime_topology_lifecycle_management_blocked=$lifecycleManagementBlocked")
            appendLine("runtime_topology_canary_scaffold_locked=$canaryScaffoldLocked")
            appendLine("runtime_topology_substrate_module_count=$substrateModuleCount")
            appendLine("runtime_topology_proot_management_module_count=$prootManagementModuleCount")
            appendLine("runtime_topology_lifecycle_management_module_count=$lifecycleManagementModuleCount")
            appendLine("runtime_topology_canary_scaffold_module_count=$canaryScaffoldModuleCount")
            appendLine("runtime_topology_telemetry_health_state=${telemetryHealthState.name}")
            appendLine("runtime_topology_pressure_stability_state=${pressureStabilityState.name}")
            appendLine("runtime_topology_budget_overall_state=${budgetOverallState.name}")
            appendLine("runtime_topology_proot_management_mainline_state=${prootManagementMainlineState.name}")
            appendLine("runtime_topology_proot_management_mainline_recommendation=${prootManagementMainlineRecommendation.name}")
            appendLine("runtime_topology_proot_management_primary_risk_axis=${prootManagementPrimaryRiskAxis.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_management_known_risk_upper_bound_live_tracees=$prootManagementKnownRiskUpperBoundLiveTracees")
            appendLine("runtime_topology_proot_management_known_safe_lower_bound_live_tracees=$prootManagementKnownSafeLowerBoundLiveTracees")
            appendLine("runtime_topology_proot_management_recommended_default_live_tracee_soft_cap=$prootManagementRecommendedDefaultLiveTraceeSoftCap")
            appendLine("runtime_topology_proot_management_adaptive_policy_status=${prootManagementAdaptivePolicyStatus.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_management_adaptive_user_context=${prootManagementAdaptiveUserContext.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_management_adaptive_resource_limiter=${prootManagementAdaptiveResourceLimiter.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_management_adaptive_effective_live_tracee_soft_cap=$prootManagementAdaptiveEffectiveLiveTraceeSoftCap")
            appendLine("runtime_topology_proot_management_adaptive_concurrency_posture=${prootManagementAdaptiveConcurrencyPosture.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_management_adaptive_queue_posture=${prootManagementAdaptiveQueuePosture.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_capacity_request_contract=${prootCapacityRequestContract.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_capacity_requested_action=${prootCapacityRequestedAction.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_capacity_review_needed=$prootCapacityReviewNeeded")
            appendLine("runtime_topology_proot_capacity_expansion_requested=$prootCapacityExpansionRequested")
            appendLine("runtime_topology_proot_capacity_queue_requested=$prootCapacityQueueRequested")
            appendLine("runtime_topology_proot_capacity_downscale_requested=$prootCapacityDownscaleRequested")
            appendLine("runtime_topology_proot_capacity_downline_requested=$prootCapacityDownlineRequested")
            appendLine("runtime_topology_proot_capacity_request_reason=${prootCapacityRequestReason.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_capacity_executor_state=${prootCapacityExecutorState.name}")
            appendLine("runtime_topology_proot_capacity_executor_recommendation=${prootCapacityExecutorRecommendation.name}")
            appendLine("runtime_topology_proot_capacity_executor_policy_enabled=$prootCapacityExecutorPolicyEnabled")
            appendLine("runtime_topology_proot_capacity_executor_launch_eligible=$prootCapacityExecutorLaunchEligible")
            appendLine("runtime_topology_proot_capacity_executor_target_binding_status=${prootCapacityExecutorTargetBindingStatus.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_capacity_executor_configured_target_id=${prootCapacityExecutorConfiguredTargetId.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_capacity_executor_registered_target_id=${prootCapacityExecutorRegisteredTargetId.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_proot_capacity_executor_registered_target_kind=${prootCapacityExecutorRegisteredTargetKind.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_policy_surface_state=${lifecyclePolicySurfaceState.name}")
            appendLine("runtime_topology_lifecycle_policy_surface_recommendation=${lifecyclePolicySurfaceRecommendation.name}")
            appendLine("runtime_topology_lifecycle_policy_load_status=${lifecyclePolicyLoadStatus.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_policy_path=${lifecyclePolicyPath.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_policy_android_execution_owner=$lifecyclePolicyAndroidExecutionOwner")
            appendLine("runtime_topology_lifecycle_policy_ubuntu_policy_advisory=$lifecyclePolicyUbuntuPolicyAdvisory")
            appendLine("runtime_topology_lifecycle_policy_ubuntu_direct_execution_allowed=$lifecyclePolicyUbuntuDirectExecutionAllowed")
            appendLine("runtime_topology_lifecycle_policy_safety_floor_applied=$lifecyclePolicySafetyFloorApplied")
            appendLine("runtime_topology_lifecycle_policy_safety_floor_added_envelope_count=$lifecyclePolicySafetyFloorAddedEnvelopeCount")
            appendLine("runtime_topology_lifecycle_policy_profile_surface_state=${lifecyclePolicyProfileSurfaceState.name}")
            appendLine("runtime_topology_lifecycle_policy_profile_surface_recommendation=${lifecyclePolicyProfileSurfaceRecommendation.name}")
            appendLine("runtime_topology_lifecycle_policy_active_profile_group=${lifecyclePolicyActiveProfileGroup.name}")
            appendLine("runtime_topology_lifecycle_policy_profile_selector_kind=${lifecyclePolicyProfileSelectorKind.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_policy_profile_group_count=$lifecyclePolicyProfileGroupCount")
            appendLine("runtime_topology_lifecycle_policy_profile_ubuntu_direct_execution_allowed=$lifecyclePolicyProfileUbuntuDirectExecutionAllowed")
            appendLine("runtime_topology_lifecycle_policy_profile_concurrency_policy=${lifecyclePolicyProfileConcurrencyPolicy.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_policy_profile_queue_policy=${lifecyclePolicyProfileQueuePolicy.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_policy_profile_kf_safety_rule=${lifecyclePolicyProfileKfSafetyRule.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_intent_surface_state=${lifecycleIntentSurfaceState.name}")
            appendLine("runtime_topology_lifecycle_intent_surface_recommendation=${lifecycleIntentSurfaceRecommendation.name}")
            appendLine("runtime_topology_lifecycle_intent_declared_count=$lifecycleIntentDeclaredCount")
            appendLine("runtime_topology_lifecycle_intent_accepted_advisory_count=$lifecycleIntentAcceptedAdvisoryCount")
            appendLine("runtime_topology_lifecycle_intent_ignored_direct_action_count=$lifecycleIntentIgnoredDirectActionCount")
            appendLine("runtime_topology_lifecycle_intent_android_execution_owner=$lifecycleIntentAndroidExecutionOwner")
            appendLine("runtime_topology_lifecycle_intent_ubuntu_advisory=$lifecycleIntentUbuntuAdvisory")
            appendLine("runtime_topology_lifecycle_reclaim_state=${lifecycleReclaimState.name}")
            appendLine("runtime_topology_lifecycle_reclaim_recommendation=${lifecycleReclaimRecommendation.name}")
            appendLine("runtime_topology_lifecycle_reclaim_review_pending=$lifecycleReclaimReviewPending")
            appendLine("runtime_topology_lifecycle_lease_pool_memory_budget_kb=$lifecycleLeasePoolMemoryBudgetKb")
            appendLine("runtime_topology_lifecycle_lease_pool_rss_kb=$lifecycleLeasePoolRssKb")
            appendLine("runtime_topology_lifecycle_lease_pool_over_budget=$lifecycleLeasePoolOverBudget")
            appendLine("runtime_topology_lifecycle_lease_pool_eviction_candidate_count=$lifecycleLeasePoolEvictionCandidateCount")
            appendLine("runtime_topology_lifecycle_system_core_layer_count=$lifecycleSystemCoreLayerCount")
            appendLine("runtime_topology_lifecycle_user_locked_layer_count=$lifecycleUserLockedLayerCount")
            appendLine("runtime_topology_lifecycle_foreground_priority_layer_count=$lifecycleForegroundPriorityLayerCount")
            appendLine("runtime_topology_lifecycle_lease_pool_layer_count=$lifecycleLeasePoolLayerCount")
            appendLine("runtime_topology_lifecycle_anomaly_pool_layer_count=$lifecycleAnomalyPoolLayerCount")
            appendLine("runtime_topology_lifecycle_proot_expansion_budget_state=${lifecycleProotExpansionBudgetState.name}")
            appendLine("runtime_topology_lifecycle_proot_expansion_budget_recommendation=${lifecycleProotExpansionBudgetRecommendation.name}")
            appendLine("runtime_topology_lifecycle_proot_expansion_requested=$lifecycleProotExpansionRequested")
            appendLine("runtime_topology_lifecycle_proot_expansion_can_reserve=$lifecycleProotExpansionCanReserve")
            appendLine("runtime_topology_lifecycle_proot_expansion_required_memory_kb=$lifecycleProotExpansionRequiredMemoryKb")
            appendLine("runtime_topology_lifecycle_proot_expansion_reclaim_needed=$lifecycleProotExpansionReclaimNeeded")
            appendLine("runtime_topology_lifecycle_proot_capacity_contract=${lifecycleProotCapacityContract.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_proot_capacity_action=${lifecycleProotCapacityAction.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_proot_capacity_review_needed=$lifecycleProotCapacityReviewNeeded")
            appendLine("runtime_topology_lifecycle_proot_capacity_queue_requested=$lifecycleProotCapacityQueueRequested")
            appendLine("runtime_topology_lifecycle_proot_capacity_downscale_requested=$lifecycleProotCapacityDownscaleRequested")
            appendLine("runtime_topology_lifecycle_proot_capacity_downline_requested=$lifecycleProotCapacityDownlineRequested")
            appendLine("runtime_topology_lifecycle_proot_capacity_approved=$lifecycleProotCapacityApproved")
            appendLine("runtime_topology_lifecycle_proot_capacity_actual_start_count=$lifecycleProotCapacityActualStartCount")
            appendLine("runtime_topology_lifecycle_proot_capacity_actual_downline_count=$lifecycleProotCapacityActualDownlineCount")
            appendLine("runtime_topology_lifecycle_proot_capacity_actual_queue_count=$lifecycleProotCapacityActualQueueCount")
            appendLine("runtime_topology_lifecycle_proot_capacity_actual_reclaim_count=$lifecycleProotCapacityActualReclaimCount")
            appendLine("runtime_topology_lifecycle_proot_capacity_execution_owner=${lifecycleProotCapacityExecutionOwner.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_proot_capacity_android_executor_status=${lifecycleProotCapacityAndroidExecutorStatus.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_lifecycle_proot_capacity_ubuntu_direct_control_allowed=$lifecycleProotCapacityUbuntuDirectControlAllowed")
            appendLine("runtime_topology_lifecycle_proot_capacity_proot_direct_control_allowed=$lifecycleProotCapacityProotDirectControlAllowed")
            appendLine("runtime_topology_lifecycle_expire_lease_count=$lifecycleExpireLeaseCount")
            appendLine("runtime_topology_lifecycle_cleanup_review_count=$lifecycleCleanupReviewCount")
            appendLine("runtime_topology_lifecycle_reclaim_children_review_count=$lifecycleReclaimChildrenReviewCount")
            appendLine("runtime_topology_lifecycle_restart_review_count=$lifecycleRestartReviewCount")
            appendLine("runtime_topology_lifecycle_terminate_review_count=$lifecycleTerminateReviewCount")
            appendLine("runtime_topology_lifecycle_quarantine_review_count=$lifecycleQuarantineReviewCount")
            appendLine("runtime_topology_workload_count=$workloadCount")
            appendLine("runtime_topology_lane_policy_count=$lanePolicyCount")
            appendLine("runtime_topology_canary_audit_state=${canaryAuditState.name}")
            appendLine("runtime_topology_canary_unsafe_actual_action_count=$canaryUnsafeActualActionCount")
            appendLine("runtime_topology_mainline_next_focus=${mainlineNextFocus.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_reason=${reason.toRuntimeTopologyEnvValue()}")
            appendLine("runtime_topology_boundary=observe_only_no_policy_action_no_activation_no_enforcement")
        }
    }
}

object RuntimeManagementTopologyDryRun {
    fun evaluate(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        prootPoolPlan: RuntimeProotPoolPlanDryRunSnapshot,
        prootManagementMainline: RuntimeProotManagementMainlineDryRunSnapshot,
        lifecyclePolicySurface: RuntimeLifecyclePolicySurfaceDryRunSnapshot,
        lifecyclePolicyProfileSurface: RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot,
        lifecycleIntentSurface: RuntimeLifecycleIntentSurfaceDryRunSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        lifecycleProotExpansionBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot,
        prootCapacityExecutor: RuntimeProotCapacityExecutorSnapshot,
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        startPreflight: RuntimeStartPreflightDryRunSnapshot,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot,
        governanceReadiness: RuntimeGovernanceReadinessGateDryRunSnapshot,
        canaryAudit: RuntimeCanaryAuditPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeManagementTopologyDryRunSnapshot {
        val substrateHealthy = prootPoolPlan.policySubstrateUsable
        val pressureBlocked = pressureStability.blocker != "none" ||
            pressureStability.state == RuntimePressureStabilityState.PRESSURE_HOLD ||
            pressureConsumer.state == RuntimePressureConsumerState.BUSY ||
            pressureConsumer.state == RuntimePressureConsumerState.BURST ||
            pressureConsumer.state == RuntimePressureConsumerState.DEGRADED
        val lifecycleBlocked = budgetPressure.overallState.severity() >=
            RuntimeBudgetState.SOFT_PRESSURE.severity()
        val lifecycleReclaimReviewPending = lifecycleReclaimPlan.expireLeaseCount > 0 ||
            lifecycleReclaimPlan.cleanupReviewCount > 0 ||
            lifecycleReclaimPlan.reclaimChildrenReviewCount > 0 ||
            lifecycleReclaimPlan.restartReviewCount > 0 ||
            lifecycleReclaimPlan.terminateReviewCount > 0 ||
            lifecycleReclaimPlan.quarantineReviewCount > 0 ||
            lifecycleReclaimPlan.leasePoolEvictionCandidateCount > 0
        val prootExpansionBlocked = lifecycleProotExpansionBudget.expansionRequested &&
            !lifecycleProotExpansionBudget.canReserveForSecondProot
        val prootCapacityBlocked = lifecycleProotExpansionBudget.capacityReviewNeeded &&
            !lifecycleProotExpansionBudget.lifecycleApprovesRequestedAction
        val canaryLocked = canaryAudit.state == RuntimeCanaryAuditState.LOCKED ||
            canaryAudit.unsafeActualActionCount > 0

        val state = when {
            !substrateHealthy -> RuntimeManagementTopologyState.SUBSTRATE_BLOCKED
            pressureBlocked -> RuntimeManagementTopologyState.PRESSURE_HELD
            canaryAudit.unsafeActualActionCount > 0 -> RuntimeManagementTopologyState.CANARY_SCAFFOLD_LOCKED
            else -> RuntimeManagementTopologyState.SUBSTRATE_READY
        }

        return RuntimeManagementTopologyDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            substrateHealthy = substrateHealthy,
            policySubstrateReason = prootPoolPlan.policySubstrateReason,
            probeSubstrateClean = prootPoolPlan.probeSubstrateClean,
            probeSubstrateReason = prootPoolPlan.probeSubstrateReason,
            prootManagementBlocked = !substrateHealthy || pressureBlocked,
            lifecycleManagementBlocked = !substrateHealthy ||
                lifecycleBlocked ||
                lifecycleReclaimReviewPending ||
                prootExpansionBlocked ||
                prootCapacityBlocked,
            canaryScaffoldLocked = canaryLocked,
            substrateModuleCount = 4,
            prootManagementModuleCount = 4 +
                (if (prootPoolPlan.mode.isNotBlank()) 1 else 0) +
                (if (prootManagementMainline.mode.isNotBlank()) 1 else 0) +
                (if (prootCapacityExecutor.mode.isNotBlank()) 1 else 0),
            lifecycleManagementModuleCount = 5 +
                (if (lifecyclePolicySurface.mode.isNotBlank()) 1 else 0) +
                (if (lifecyclePolicyProfileSurface.mode.isNotBlank()) 1 else 0) +
                (if (lifecycleIntentSurface.mode.isNotBlank()) 1 else 0) +
                (if (lifecycleReclaimPlan.mode.isNotBlank()) 1 else 0) +
                (if (lifecycleProotExpansionBudget.mode.isNotBlank()) 1 else 0),
            canaryScaffoldModuleCount = canaryAudit.dryRunBoundaryCount,
            telemetryHealthState = prootTelemetryHealth.state,
            pressureStabilityState = pressureStability.state,
            budgetOverallState = budgetPressure.overallState,
            prootManagementMainlineState = prootManagementMainline.state,
            prootManagementMainlineRecommendation = prootManagementMainline.recommendation,
            prootManagementPrimaryRiskAxis = prootManagementMainline.primaryRiskAxis,
            prootManagementKnownRiskUpperBoundLiveTracees =
                prootManagementMainline.knownRiskUpperBoundLiveTracees,
            prootManagementKnownSafeLowerBoundLiveTracees =
                prootManagementMainline.knownSafeLowerBoundLiveTracees,
            prootManagementRecommendedDefaultLiveTraceeSoftCap =
                prootManagementMainline.recommendedDefaultLiveTraceeSoftCap,
            prootManagementAdaptivePolicyStatus = prootManagementMainline.adaptivePolicyStatus,
            prootManagementAdaptiveUserContext = prootManagementMainline.adaptiveUserContext,
            prootManagementAdaptiveResourceLimiter = prootManagementMainline.adaptiveResourceLimiter,
            prootManagementAdaptiveEffectiveLiveTraceeSoftCap =
                prootManagementMainline.adaptiveEffectiveLiveTraceeSoftCap,
            prootManagementAdaptiveConcurrencyPosture =
                prootManagementMainline.adaptiveConcurrencyPosture,
            prootManagementAdaptiveQueuePosture = prootManagementMainline.adaptiveQueuePosture,
            prootCapacityRequestContract = prootPoolPlan.capacityRequestContract,
            prootCapacityRequestedAction = prootPoolPlan.capacityRequestedAction,
            prootCapacityReviewNeeded = prootPoolPlan.capacityReviewNeeded,
            prootCapacityExpansionRequested = prootPoolPlan.capacityExpansionRequested,
            prootCapacityQueueRequested = prootPoolPlan.capacityQueueRequested,
            prootCapacityDownscaleRequested = prootPoolPlan.capacityDownscaleRequested,
            prootCapacityDownlineRequested = prootPoolPlan.capacityDownlineRequested,
            prootCapacityRequestReason = prootPoolPlan.capacityRequestReason,
            prootCapacityExecutorState = prootCapacityExecutor.state,
            prootCapacityExecutorRecommendation = prootCapacityExecutor.recommendation,
            prootCapacityExecutorPolicyEnabled = prootCapacityExecutor.policyEnabled,
            prootCapacityExecutorLaunchEligible = prootCapacityExecutor.launchEligible,
            prootCapacityExecutorTargetBindingStatus = prootCapacityExecutor.targetBindingStatus,
            prootCapacityExecutorConfiguredTargetId = prootCapacityExecutor.configuredRuntimeTargetId,
            prootCapacityExecutorRegisteredTargetId = prootCapacityExecutor.registeredRuntimeTargetId,
            prootCapacityExecutorRegisteredTargetKind = prootCapacityExecutor.registeredRuntimeTargetKind,
            lifecyclePolicySurfaceState = lifecyclePolicySurface.state,
            lifecyclePolicySurfaceRecommendation = lifecyclePolicySurface.recommendation,
            lifecyclePolicyLoadStatus = lifecyclePolicySurface.loadStatus,
            lifecyclePolicyPath = lifecyclePolicySurface.policyPath,
            lifecyclePolicyAndroidExecutionOwner = lifecyclePolicySurface.androidExecutionOwner,
            lifecyclePolicyUbuntuPolicyAdvisory = lifecyclePolicySurface.ubuntuPolicyAdvisory,
            lifecyclePolicyUbuntuDirectExecutionAllowed = lifecyclePolicySurface.ubuntuDirectExecutionAllowed,
            lifecyclePolicySafetyFloorApplied = lifecyclePolicySurface.safetyFloorApplied,
            lifecyclePolicySafetyFloorAddedEnvelopeCount =
                lifecyclePolicySurface.safetyFloorAddedEnvelopeCount,
            lifecyclePolicyProfileSurfaceState = lifecyclePolicyProfileSurface.state,
            lifecyclePolicyProfileSurfaceRecommendation = lifecyclePolicyProfileSurface.recommendation,
            lifecyclePolicyActiveProfileGroup = lifecyclePolicyProfileSurface.activeProfileGroup,
            lifecyclePolicyProfileSelectorKind = lifecyclePolicyProfileSurface.profileSelectorKind,
            lifecyclePolicyProfileGroupCount =
                lifecyclePolicyProfileSurface.userFacingProfileGroupCount,
            lifecyclePolicyProfileUbuntuDirectExecutionAllowed =
                lifecyclePolicyProfileSurface.ubuntuDirectExecutionAllowed,
            lifecyclePolicyProfileConcurrencyPolicy =
                lifecyclePolicyProfileSurface.concurrencyPolicy,
            lifecyclePolicyProfileQueuePolicy =
                lifecyclePolicyProfileSurface.queuePolicy,
            lifecyclePolicyProfileKfSafetyRule =
                lifecyclePolicyProfileSurface.kfSafetyRule,
            lifecycleIntentSurfaceState = lifecycleIntentSurface.state,
            lifecycleIntentSurfaceRecommendation = lifecycleIntentSurface.recommendation,
            lifecycleIntentDeclaredCount = lifecycleIntentSurface.declaredIntentCount,
            lifecycleIntentAcceptedAdvisoryCount = lifecycleIntentSurface.acceptedAdvisoryCount,
            lifecycleIntentIgnoredDirectActionCount =
                lifecycleIntentSurface.ignoredDirectActionCount,
            lifecycleIntentAndroidExecutionOwner = lifecycleIntentSurface.androidExecutionOwner,
            lifecycleIntentUbuntuAdvisory = lifecycleIntentSurface.ubuntuIntentAdvisory,
            lifecycleReclaimState = lifecycleReclaimPlan.state,
            lifecycleReclaimRecommendation = lifecycleReclaimPlan.recommendation,
            lifecycleReclaimReviewPending = lifecycleReclaimReviewPending,
            lifecycleLeasePoolMemoryBudgetKb = lifecycleReclaimPlan.leasePoolMemoryBudgetKb,
            lifecycleLeasePoolRssKb = lifecycleReclaimPlan.leasePoolRssKb,
            lifecycleLeasePoolOverBudget = lifecycleReclaimPlan.leasePoolOverBudget,
            lifecycleLeasePoolEvictionCandidateCount =
                lifecycleReclaimPlan.leasePoolEvictionCandidateCount,
            lifecycleSystemCoreLayerCount = lifecycleReclaimPlan.systemCoreLayerCount,
            lifecycleUserLockedLayerCount = lifecycleReclaimPlan.userLockedLayerCount,
            lifecycleForegroundPriorityLayerCount = lifecycleReclaimPlan.foregroundPriorityLayerCount,
            lifecycleLeasePoolLayerCount = lifecycleReclaimPlan.leasePoolLayerCount,
            lifecycleAnomalyPoolLayerCount = lifecycleReclaimPlan.anomalyPoolLayerCount,
            lifecycleProotExpansionBudgetState = lifecycleProotExpansionBudget.state,
            lifecycleProotExpansionBudgetRecommendation = lifecycleProotExpansionBudget.recommendation,
            lifecycleProotExpansionRequested = lifecycleProotExpansionBudget.expansionRequested,
            lifecycleProotExpansionCanReserve = lifecycleProotExpansionBudget.canReserveForSecondProot,
            lifecycleProotExpansionRequiredMemoryKb =
                lifecycleProotExpansionBudget.secondProotRequiredMemoryKb,
            lifecycleProotExpansionReclaimNeeded =
                lifecycleProotExpansionBudget.lifecycleReclaimNeededBeforeExpansion,
            lifecycleProotCapacityContract = lifecycleProotExpansionBudget.capacityContract,
            lifecycleProotCapacityAction = lifecycleProotExpansionBudget.requestedAction,
            lifecycleProotCapacityReviewNeeded = lifecycleProotExpansionBudget.capacityReviewNeeded,
            lifecycleProotCapacityQueueRequested = lifecycleProotExpansionBudget.queueRequested,
            lifecycleProotCapacityDownscaleRequested = lifecycleProotExpansionBudget.downscaleRequested,
            lifecycleProotCapacityDownlineRequested = lifecycleProotExpansionBudget.downlineRequested,
            lifecycleProotCapacityApproved = lifecycleProotExpansionBudget.lifecycleApprovesRequestedAction,
            lifecycleProotCapacityActualStartCount =
                lifecycleProotExpansionBudget.actualSecondProotStartCount,
            lifecycleProotCapacityActualDownlineCount =
                lifecycleProotExpansionBudget.actualProotDownlineCount,
            lifecycleProotCapacityActualQueueCount =
                lifecycleProotExpansionBudget.actualQueueCreationCount,
            lifecycleProotCapacityActualReclaimCount =
                lifecycleProotExpansionBudget.actualLifecycleReclaimCount,
            lifecycleProotCapacityExecutionOwner =
                lifecycleProotExpansionBudget.executionOwner,
            lifecycleProotCapacityAndroidExecutorStatus =
                lifecycleProotExpansionBudget.androidExecutorStatus,
            lifecycleProotCapacityUbuntuDirectControlAllowed =
                lifecycleProotExpansionBudget.ubuntuDirectCapacityControlAllowed,
            lifecycleProotCapacityProotDirectControlAllowed =
                lifecycleProotExpansionBudget.prootDirectCapacityControlAllowed,
            lifecycleExpireLeaseCount = lifecycleReclaimPlan.expireLeaseCount,
            lifecycleCleanupReviewCount = lifecycleReclaimPlan.cleanupReviewCount,
            lifecycleReclaimChildrenReviewCount = lifecycleReclaimPlan.reclaimChildrenReviewCount,
            lifecycleRestartReviewCount = lifecycleReclaimPlan.restartReviewCount,
            lifecycleTerminateReviewCount = lifecycleReclaimPlan.terminateReviewCount,
            lifecycleQuarantineReviewCount = lifecycleReclaimPlan.quarantineReviewCount,
            workloadCount = workloadRegistry.totalWorkloads,
            lanePolicyCount = laneAdmission.policyLaneCount,
            canaryAuditState = canaryAudit.state,
            canaryUnsafeActualActionCount = canaryAudit.unsafeActualActionCount,
            mainlineNextFocus = nextFocus(
                state = state,
                workloadRegistry = workloadRegistry,
                backgroundDecay = backgroundDecay,
                laneAdmission = laneAdmission,
                startPreflight = startPreflight,
                startQueuePlan = startQueuePlan,
                lifecyclePolicySurface = lifecyclePolicySurface,
                lifecyclePolicyProfileSurface = lifecyclePolicyProfileSurface,
                lifecycleIntentSurface = lifecycleIntentSurface,
                lifecycleReclaimPlan = lifecycleReclaimPlan,
                lifecycleProotExpansionBudget = lifecycleProotExpansionBudget,
                prootCapacityExecutor = prootCapacityExecutor,
                lifecycleReclaimReviewPending = lifecycleReclaimReviewPending,
                governanceReadiness = governanceReadiness
            ),
            reason = buildReason(
                state = state,
                prootTelemetryHealth = prootTelemetryHealth,
                prootPoolPlan = prootPoolPlan,
                pressureStability = pressureStability,
                budgetPressure = budgetPressure,
                lifecyclePolicySurface = lifecyclePolicySurface,
                lifecyclePolicyProfileSurface = lifecyclePolicyProfileSurface,
                lifecycleIntentSurface = lifecycleIntentSurface,
                lifecycleReclaimPlan = lifecycleReclaimPlan,
                lifecycleProotExpansionBudget = lifecycleProotExpansionBudget,
                prootCapacityExecutor = prootCapacityExecutor,
                canaryAudit = canaryAudit
            )
        )
    }

    private fun recommendationFor(
        state: RuntimeManagementTopologyState
    ): RuntimeManagementTopologyRecommendation {
        return when (state) {
            RuntimeManagementTopologyState.SUBSTRATE_BLOCKED ->
                RuntimeManagementTopologyRecommendation.REPAIR_TELEMETRY_SUBSTRATE
            RuntimeManagementTopologyState.PRESSURE_HELD ->
                RuntimeManagementTopologyRecommendation.WAIT_FOR_PRESSURE_STABILITY
            RuntimeManagementTopologyState.CANARY_SCAFFOLD_LOCKED ->
                RuntimeManagementTopologyRecommendation.KEEP_CANARY_SCAFFOLD_LOCKED
            RuntimeManagementTopologyState.SUBSTRATE_READY,
            RuntimeManagementTopologyState.READY_FOR_MAINLINE_REVIEW ->
                RuntimeManagementTopologyRecommendation.REVIEW_TWO_SYSTEM_MAINLINE
            RuntimeManagementTopologyState.OBSERVING ->
                RuntimeManagementTopologyRecommendation.KEEP_OBSERVING
        }
    }

    private fun nextFocus(
        state: RuntimeManagementTopologyState,
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        startPreflight: RuntimeStartPreflightDryRunSnapshot,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot,
        lifecyclePolicySurface: RuntimeLifecyclePolicySurfaceDryRunSnapshot,
        lifecyclePolicyProfileSurface: RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot,
        lifecycleIntentSurface: RuntimeLifecycleIntentSurfaceDryRunSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        lifecycleProotExpansionBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot,
        prootCapacityExecutor: RuntimeProotCapacityExecutorSnapshot,
        lifecycleReclaimReviewPending: Boolean,
        governanceReadiness: RuntimeGovernanceReadinessGateDryRunSnapshot
    ): String {
        if (state == RuntimeManagementTopologyState.SUBSTRATE_BLOCKED) {
            return "repair_or_wait_for_clean_proot_telemetry"
        }
        if (state == RuntimeManagementTopologyState.PRESSURE_HELD) {
            return "hold_canary_and_observe_proot_pressure"
        }
        if (lifecycleReclaimReviewPending) {
            return "review_lifecycle_reclaim_plan"
        }
        if (lifecycleProotExpansionBudget.expansionRequested &&
            !lifecycleProotExpansionBudget.canReserveForSecondProot
        ) {
            return "review_lifecycle_budget_for_next_proot"
        }
        if (prootCapacityExecutor.state ==
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_POLICY_ENABLE
        ) {
            return "enable_or_review_proot_capacity_executor_policy"
        }
        if (prootCapacityExecutor.state ==
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_LAUNCHER_BINDING
        ) {
            return "bind_dedicated_next_proot_runtime_target"
        }
        if (prootCapacityExecutor.launchEligible) {
            return "review_bound_next_proot_launch"
        }
        if (prootCapacityExecutor.state ==
            RuntimeProotCapacityExecutorState.DOWNSCALE_READY_FOR_ANDROID_DOWNLINE
        ) {
            return "review_bound_proot_downline"
        }
        if (lifecycleProotExpansionBudget.downlineRequested) {
            return "review_proot_downline_request"
        }
        if (lifecycleProotExpansionBudget.queueRequested) {
            return "keep_single_proot_queue_until_capacity_trigger"
        }
        if (lifecyclePolicySurface.state == RuntimeLifecyclePolicySurfaceState.POLICY_ERROR_DEFAULT) {
            return "repair_lifecycle_policy_json"
        }
        if (lifecyclePolicyProfileSurface.state ==
            RuntimeLifecyclePolicyProfileSurfaceState.CUSTOM_PROFILE_REVIEW
        ) {
            return "review_lifecycle_policy_profile_group"
        }
        if (lifecycleIntentSurface.state == RuntimeLifecycleIntentSurfaceState.INTENT_ERROR_IGNORED) {
            return "repair_lifecycle_intent_json"
        }
        if (lifecycleIntentSurface.declaredIntentCount > 0) {
            return "review_lifecycle_advisory_intents"
        }
        if (workloadRegistry.enforcementEnabled ||
            backgroundDecay.enforcementEnabled ||
            lifecycleReclaimPlan.enforcementEnabled ||
            laneAdmission.enforcementEnabled ||
            startPreflight.enforcementEnabled ||
            startQueuePlan.enforcementEnabled ||
            governanceReadiness.enforcementEnabled
        ) {
            return "review_enabled_runtime_controls"
        }
        return "observe_runtime_management_contracts"
    }

    private fun buildReason(
        state: RuntimeManagementTopologyState,
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        prootPoolPlan: RuntimeProotPoolPlanDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        lifecyclePolicySurface: RuntimeLifecyclePolicySurfaceDryRunSnapshot,
        lifecyclePolicyProfileSurface: RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot,
        lifecycleIntentSurface: RuntimeLifecycleIntentSurfaceDryRunSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        lifecycleProotExpansionBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot,
        prootCapacityExecutor: RuntimeProotCapacityExecutorSnapshot,
        canaryAudit: RuntimeCanaryAuditPlanDryRunSnapshot
    ): String {
        return "state=${state.name},telemetry=${prootTelemetryHealth.state.name}/" +
            "${prootTelemetryHealth.blocker},policySubstrate=${prootPoolPlan.policySubstrateUsable}/" +
            "${prootPoolPlan.policySubstrateReason},probeSubstrate=${prootPoolPlan.probeSubstrateClean}/" +
            "${prootPoolPlan.probeSubstrateReason},pressure=${pressureStability.state.name}/" +
            "${pressureStability.blocker},budget=${budgetPressure.overallState.name}," +
            "lifecyclePolicy=${lifecyclePolicySurface.state.name}/" +
            "${lifecyclePolicySurface.loadStatus}," +
            "lifecycleProfile=${lifecyclePolicyProfileSurface.activeProfileGroup.name}/" +
            "${lifecyclePolicyProfileSurface.profileSelectorKind}," +
            "lifecycleIntent=${lifecycleIntentSurface.state.name}/" +
            "${lifecycleIntentSurface.declaredIntentCount}," +
            "lifecycleReclaim=${lifecycleReclaimPlan.state.name}/" +
            "${lifecycleReclaimPlan.recommendation.name}," +
            "leasePool=${lifecycleReclaimPlan.leasePoolRssKb}/" +
            "${lifecycleReclaimPlan.leasePoolMemoryBudgetKb}/" +
            "${lifecycleReclaimPlan.leasePoolEvictionCandidateCount}," +
            "prootCapacity=${lifecycleProotExpansionBudget.requestedAction}/" +
            "${lifecycleProotExpansionBudget.lifecycleApprovesRequestedAction}," +
            "prootCapacityExecutor=${prootCapacityExecutor.state.name}/" +
            "${prootCapacityExecutor.targetBindingStatus}," +
            "prootExpansion=${lifecycleProotExpansionBudget.state.name}/" +
            "${lifecycleProotExpansionBudget.decision}," +
            "canary=${canaryAudit.state.name},unsafeActions=${canaryAudit.unsafeActualActionCount}"
    }
}

private fun String?.toRuntimeTopologyEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
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
