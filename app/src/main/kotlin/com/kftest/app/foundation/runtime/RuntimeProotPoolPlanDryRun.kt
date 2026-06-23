package com.kftest.app.foundation.runtime

enum class RuntimeProotPoolState {
    NO_SOURCE,
    SUBSTRATE_BLOCKED,
    STEADY_POOL,
    PRESSURE_HOLD,
    BURST_HEADROOM_REVIEW,
    BACKGROUND_SHRINK_REVIEW,
    IDLE_RECLAIM_REVIEW
}

enum class RuntimeProotPoolRecommendation {
    WAIT_FOR_PROOT_TELEMETRY,
    REPAIR_PROOT_TELEMETRY,
    KEEP_POOL_OBSERVING,
    HOLD_POOL_EXPANSION,
    REVIEW_BURST_HEADROOM,
    REVIEW_BACKGROUND_SHRINK,
    REVIEW_IDLE_RECLAIM
}

enum class RuntimeProotPoolLaneAction {
    OBSERVE,
    KEEP_HEADROOM,
    HOLD_EXPANSION,
    REVIEW_BURST_HEADROOM,
    REVIEW_BACKGROUND_SHRINK,
    REVIEW_IDLE_RECLAIM
}

enum class RuntimeProotTaskPressureTier {
    NO_SOURCE,
    IDLE,
    HEALTHY,
    LIGHT_PRESSURE,
    MODERATE_PRESSURE,
    HIGH_PRESSURE,
    ABNORMAL
}

enum class RuntimeProotTaskPressureAction {
    WAIT_FOR_TELEMETRY,
    KEEP_SINGLE_PROOT,
    QUEUE_ON_CURRENT_PROOT,
    REQUEST_NEXT_PROOT,
    HOLD_FOR_MEMORY,
    REVIEW_DOWNLINE
}

private const val SAFE_DEFAULT_START_TRACEES = 12

data class RuntimeProotPoolLanePlan(
    val lane: RuntimeLaneKind,
    val priority: Int,
    val activeWorkloads: Int,
    val maxConcurrency: Int,
    val backgroundMaxConcurrency: Int,
    val effectiveMaxConcurrency: Int,
    val burstCeiling: Int,
    val plannedPoolSlots: Int,
    val existingActiveSlots: Int = activeWorkloads,
    val newAdmissionSlots: Int = (plannedPoolSlots - activeWorkloads).coerceAtLeast(0),
    val overEffectiveMaxWorkloads: Int = (activeWorkloads - effectiveMaxConcurrency).coerceAtLeast(0),
    val spareSlots: Int,
    val serial: Boolean,
    val allowBurst: Boolean,
    val admissionState: RuntimeLaneAdmissionState,
    val action: RuntimeProotPoolLaneAction,
    val reason: String,
    val activeWorkloadDisposition: String = when {
        activeWorkloads <= 0 -> "no_existing_active_workload"
        effectiveMaxConcurrency <= 0 ->
            "existing_active_observed_new_background_admission_closed"
        activeWorkloads > effectiveMaxConcurrency ->
            "existing_active_over_effective_capacity_observed_until_lease_or_cleanup"
        else -> "existing_active_within_effective_capacity"
    }
)

private data class RuntimeProotAdaptivePolicy(
    val profileGroup: RuntimeLifecyclePolicyProfileGroup,
    val profileSource: String,
    val status: String,
    val userContext: String,
    val resourceLimiter: String,
    val memorySignal: RuntimePressureLevel,
    val cpuSignal: String,
    val ioSignal: String,
    val cpuBusyTicksPerSecond: Long,
    val ioBusyBytesPerSecond: Long,
    val defaultLiveTraceeSoftCap: Int,
    val foregroundLiveTraceeSoftCap: Int,
    val backgroundLiveTraceeSoftCap: Int,
    val effectiveLiveTraceeSoftCap: Int,
    val hardStopLiveTracees: Int,
    val queuePolicy: String,
    val lowPriorityBackgroundPolicy: String,
    val concurrencyPosture: String,
    val queuePosture: String,
    val lowPriorityBackgroundAllowed: Boolean,
    val strategyMode: String,
    val strategyValueContract: String,
    val strategyActiveBand: String,
    val strategyPeakTracees: Int,
    val strategyQueueUntilTracees: Int,
    val strategySecondProotTriggerTracees: Int,
    val strategyOverflowHeadroomTracees: Int,
    val strategySingleProotOverflowPercent: Int,
    val strategyOverflowPercentBase: String,
    val strategyQueueHeadroomPercent: Int,
    val strategySecondProotTriggerHeadroomPercent: Int,
    val strategyPercentBase: String,
    val strategyLowPowerLimit: Int,
    val strategyBalancedLimit: Int,
    val strategyHighPerformanceLimit: Int,
    val uiProtectionRule: String,
    val reason: String
)

private data class RuntimeProotAdaptiveProfileParams(
    val profileGroup: RuntimeLifecyclePolicyProfileGroup,
    val profileSource: String,
    val defaultLiveTraceeSoftCap: Int,
    val foregroundLiveTraceeSoftCap: Int,
    val backgroundLiveTraceeSoftCap: Int,
    val hardStopLiveTracees: Int,
    val cpuBusyTicksPerSecond: Long,
    val ioBusyBytesPerSecond: Long,
    val singleProotPeakTracees: Int,
    val singleProotQueueUntilTracees: Int,
    val secondProotTriggerTracees: Int,
    val overflowHeadroomTracees: Int,
    val singleProotOverflowPercent: Int,
    val overflowPercentBase: String,
    val queueHeadroomPercent: Int,
    val secondProotTriggerHeadroomPercent: Int,
    val queueStrategyPercentBase: String,
    val lowPowerProfileLimit: Int,
    val balancedProfileLimit: Int,
    val highPerformanceProfileLimit: Int,
    val lowPriorityBackgroundPolicy: String,
    val queuePolicy: String,
    val uiProtectionRule: String
)

private data class RuntimeProotResourceEquation(
    val mode: String,
    val model: String,
    val axisCoverage: String,
    val axisContract: String,
    val cpuAxisStatus: String,
    val ioAxisStatus: String,
    val memoryAxisStatus: String,
    val blindAxisCount: Int,
    val calibrationGate: String,
    val liveTraceeRatioPercent: Int,
    val cpuRatioPercent: Int,
    val ioRatioPercent: Int,
    val memoryRatioPercent: Int,
    val budgetRatioPercent: Int,
    val pressureScorePercent: Int,
    val riskPercent: Int,
    val headroomPercent: Int,
    val bottleneckAxis: String,
    val expansionAllowed: Boolean,
    val lowPriorityQueueRequired: Boolean,
    val targetParallelSlots: Int,
    val targetQueueDepth: Int,
    val decision: String,
    val liveTraceeRaw: Int,
    val liveTraceeCap: Int,
    val cpuRawTicksPerSecond: Long,
    val cpuCapTicksPerSecond: Long,
    val ioRawBytesPerSecond: Long,
    val ioCapBytesPerSecond: Long,
    val memoryRawLevel: RuntimePressureLevel,
    val memoryCapLevel: RuntimePressureLevel,
    val budgetRawState: RuntimeBudgetState,
    val budgetCapState: RuntimeBudgetState,
    val pressureScoreRaw: Int,
    val pressureScoreCap: Int,
    val proportionalPolicy: String,
    val calibrationStatus: String,
    val nextCalibrationFocus: String,
    val reason: String
)

private data class RuntimeProotPolicySubstrate(
    val policyUsable: Boolean,
    val policyReason: String,
    val probeClean: Boolean,
    val probeReason: String
)

private data class RuntimeProotCapacityRequest(
    val contract: String,
    val requestedAction: String,
    val reviewNeeded: Boolean,
    val expansionRequested: Boolean,
    val queueRequested: Boolean,
    val downscaleRequested: Boolean,
    val downlineRequested: Boolean,
    val reason: String
)

private data class RuntimeProotTaskPressure(
    val mode: String,
    val contract: String,
    val tier: RuntimeProotTaskPressureTier,
    val action: RuntimeProotTaskPressureAction,
    val reason: String,
    val primarySignal: String,
    val profileGroup: RuntimeLifecyclePolicyProfileGroup,
    val activeWorkloads: Int,
    val runningRoots: Int,
    val queueCount: Int,
    val backlogCount: Int,
    val noCapacityCount: Int,
    val deferredCount: Int,
    val saturatedLaneCount: Int,
    val overEffectiveMaxWorkloads: Int,
    val idleDownlineCandidateCount: Int,
    val telemetryHealthy: Boolean,
    val telemetryState: RuntimePressureConsumerState,
    val telemetrySignal: ProotPressureSignalLevel,
    val telemetryScore: Int,
    val latencySignalStatus: String,
    val peakTasksRole: String
)

data class RuntimeProotPoolPlanDryRunSnapshot(
    val mode: String = "proot_pool_plan_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeProotPoolState = RuntimeProotPoolState.NO_SOURCE,
    val recommendation: RuntimeProotPoolRecommendation =
        RuntimeProotPoolRecommendation.WAIT_FOR_PROOT_TELEMETRY,
    val substrateHealthy: Boolean = false,
    val policySubstrateUsable: Boolean = false,
    val policySubstrateReason: String = "waiting_for_telemetry_health",
    val probeSubstrateClean: Boolean = false,
    val probeSubstrateReason: String = "waiting_for_fresh_probe_source",
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val pressureStabilityState: RuntimePressureStabilityState = RuntimePressureStabilityState.NO_SOURCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val prootSignalLevel: ProotPressureSignalLevel = ProotPressureSignalLevel.QUIET,
    val prootPressureScore: Int = 0,
    val eventsInWindow: Int = 0,
    val forkExecEventsInWindow: Int = 0,
    val liveTraceeCount: Int = 0,
    val policyLaneCount: Int = 0,
    val activeWorkloadCount: Int = 0,
    val ownerContainerCount: Int = 0,
    val ownerContainerTraceeCount: Int = 0,
    val maxPoolSlots: Int = 0,
    val effectivePoolSlots: Int = 0,
    val plannedPoolSlots: Int = 0,
    val sparePoolSlots: Int = 0,
    val burstLaneCount: Int = 0,
    val burstHeadroomLaneCount: Int = 0,
    val backgroundShrinkLaneCount: Int = 0,
    val idleReclaimCandidateCount: Int = 0,
    val holdExpansionLaneCount: Int = 0,
    val saturatedLaneCount: Int = 0,
    val capacityRequestContract: String =
        "proot_requests_capacity_lifecycle_reviews_budget_android_executes",
    val capacityRequestedAction: String = "NONE",
    val capacityReviewNeeded: Boolean = false,
    val capacityExpansionRequested: Boolean = false,
    val capacityQueueRequested: Boolean = false,
    val capacityDownscaleRequested: Boolean = false,
    val capacityDownlineRequested: Boolean = false,
    val capacityRequestReason: String = "single_proot_within_multiplier_limit",
    val taskPressureMode: String = "proot_task_pressure_health_v1",
    val taskPressureContract: String =
        "unified_runtime_start_path_measures_queue_pressure_capacity_boundary_executes",
    val taskPressureTier: RuntimeProotTaskPressureTier = RuntimeProotTaskPressureTier.NO_SOURCE,
    val taskPressureAction: RuntimeProotTaskPressureAction =
        RuntimeProotTaskPressureAction.WAIT_FOR_TELEMETRY,
    val taskPressureReason: String = "waiting_for_proot_telemetry",
    val taskPressurePrimarySignal: String = "none",
    val taskPressureProfileGroup: RuntimeLifecyclePolicyProfileGroup =
        RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
    val taskPressureActiveWorkloads: Int = 0,
    val taskPressureRunningRoots: Int = 0,
    val taskPressureQueueCount: Int = 0,
    val taskPressureBacklogCount: Int = 0,
    val taskPressureNoCapacityCount: Int = 0,
    val taskPressureDeferredCount: Int = 0,
    val taskPressureSaturatedLaneCount: Int = 0,
    val taskPressureOverEffectiveMaxWorkloads: Int = 0,
    val taskPressureIdleDownlineCandidateCount: Int = 0,
    val taskPressureTelemetryHealthy: Boolean = false,
    val taskPressureTelemetryState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val taskPressureTelemetrySignal: ProotPressureSignalLevel = ProotPressureSignalLevel.QUIET,
    val taskPressureTelemetryScore: Int = 0,
    val taskPressureLatencySignalStatus: String = "not_wired",
    val taskPressurePeakTasksRole: String =
        "memory_estimate_and_fallback_only_not_scale_trigger",
    val tuningMode: String = "observe_only_exponential_probe_then_binary_search",
    val tuningAxis: String = "proot_pool_parallel_slot_capacity",
    val tuningStatus: String = "WAIT_FOR_BASELINE",
    val tuningCandidatePoolSlots: Int = 0,
    val tuningNextCandidateIfPass: Int = 0,
    val tuningNextCandidateIfFail: Int = 0,
    val tuningLowerBoundPoolSlots: Int = 0,
    val tuningUpperBoundPoolSlots: Int = 0,
    val tuningStopCondition: String = "ui_stutter_or_pressure_hold_or_budget_soft_pressure_or_crash",
    val tuningOutcomeSource: String = "tester_observation_and_android_runtime_log",
    val tuningCrashLogPath: String = "/workspace/.kf/proot-pool-tuning.jsonl",
    val tuningLogFormat: String = "bounded_jsonl_significant_change_or_heartbeat_v1",
    val tuningSafeRestartReadable: Boolean = true,
    val tuningGateReason: String = "waiting_for_baseline",
    val tuningPrerequisite: String = "clean_telemetry_and_stable_or_observed_baseline",
    val tuningPressureAttribution: String = "unknown",
    val tuningCanProbeNow: Boolean = false,
    val knownRiskSampleLiveTracees: Int = 18,
    val knownRiskSamplePressureScore: Int = 36,
    val knownRiskSampleAttribution: String = "live_tracee_accumulation",
    val knownRiskSampleDeviceScope: String = "oneplus_8t_controlled_probe_20260531",
    val knownSafeLowerBoundLiveTracees: Int = 17,
    val cleanBaselineRequiredForLowerBound: Boolean = false,
    val recommendedDefaultLiveTraceePolicy: String = "default_balanced_soft_cap_16_review_at_18_live_tracees",
    val deviceCalibrationOverlayStatus: String = "not_measured_safe_defaults_active",
    val deviceCalibrationOverlaySource: String = "safe_defaults",
    val deviceCalibrationOverlayValid: Boolean = false,
    val deviceCalibrationTraceeMaxCap: Int = 16,
    val deviceCalibrationMeasuredMaxTracees: Int = 0,
    val deviceCalibrationHealthyStableTraceeCap: Int = 8,
    val deviceCalibrationBudgetKneeTracees: Int = 0,
    val deviceCalibrationBudgetKneeUsedForCapacity: Boolean = false,
    val deviceCalibrationBudgetKneePolicy: String = "advisory_budget_observation_not_capacity_trigger",
    val deviceCalibrationDefaultStartCap: Int = SAFE_DEFAULT_START_TRACEES,
    val deviceCalibrationTraceeSoftCap: Int = 8,
    val deviceCalibrationTraceeHardCap: Int = 16,
    val deviceCalibrationMemoryWorkerRssKb: Long = 96L * 1024L,
    val adaptivePolicyMode: String = "proot_adaptive_policy_observe_v0",
    val adaptiveProfileGroup: RuntimeLifecyclePolicyProfileGroup =
        RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
    val adaptiveProfileSource: String = "lifecycle_policy_profile_surface",
    val adaptivePolicyStatus: String = "bounded_observe_only",
    val adaptiveUserContext: String = "kf_foreground_performance_bias",
    val adaptiveResourceLimiter: String = "live_tracee_accumulation",
    val adaptiveMemorySignal: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val adaptiveCpuSignal: String = "not_observed_yet",
    val adaptiveIoSignal: String = "not_observed_yet",
    val adaptiveCpuBusyTicksPerSecond: Long = 80L,
    val adaptiveIoBusyBytesPerSecond: Long = 1024L * 1024L,
    val adaptiveDefaultLiveTraceeSoftCap: Int = 16,
    val adaptiveForegroundLiveTraceeSoftCap: Int = 16,
    val adaptiveBackgroundLiveTraceeSoftCap: Int = 8,
    val adaptiveEffectiveLiveTraceeSoftCap: Int = 16,
    val adaptiveHardStopLiveTracees: Int = 18,
    val adaptiveQueuePolicy: String = "android_owned_priority_then_declaration_order",
    val adaptiveLowPriorityBackgroundPolicy: String = "foreground_only_when_healthy_under_cap",
    val adaptiveConcurrencyPosture: String = "allow_until_effective_soft_cap",
    val adaptiveQueuePosture: String = "run_high_priority_queue_low_priority_when_over_cap",
    val adaptiveLowPriorityBackgroundAllowed: Boolean = false,
    val adaptiveStrategyMode: String = "single_proot_peak_multiplier_then_next_proot_v1",
    val adaptiveStrategyValueContract: String =
        "measured_throughput_peak_times_single_multiplier_then_next_proot",
    val adaptiveStrategyActiveBand: String = "FILL_SINGLE_PROOT_TO_PEAK",
    val adaptiveStrategyPeakTracees: Int = SAFE_DEFAULT_START_TRACEES,
    val adaptiveStrategyQueueUntilTracees: Int = SAFE_DEFAULT_START_TRACEES,
    val adaptiveStrategySecondProotTriggerTracees: Int = SAFE_DEFAULT_START_TRACEES + 1,
    val adaptiveStrategyOverflowHeadroomTracees: Int = 1,
    val adaptiveStrategySingleProotOverflowPercent: Int = 25,
    val adaptiveStrategyOverflowPercentBase: String = "single_proot_peak_multiplier",
    val adaptiveStrategyQueueHeadroomPercent: Int = 25,
    val adaptiveStrategySecondProotTriggerHeadroomPercent: Int = 25,
    val adaptiveStrategyPercentBase: String = "single_proot_peak_multiplier",
    val adaptiveStrategyLowPowerLimit: Int = 1,
    val adaptiveStrategyBalancedLimit: Int = SAFE_DEFAULT_START_TRACEES,
    val adaptiveStrategyHighPerformanceLimit: Int = SAFE_DEFAULT_START_TRACEES,
    val adaptiveProfileBandsOperational: Boolean = false,
    val adaptiveProfileBandsRole: String = "deprecated_display_aliases_single_peak_multiplier",
    val adaptiveUiProtectionRule: String = "kf_foreground_performance_user_foreground_protection_when_hidden",
    val adaptiveReason: String = "oneplus_8t_bounded_sample_pending_more_axes",
    val resourceEquationMode: String = "proot_resource_equation_v0",
    val resourceEquationModel: String = "max_axis_ratio_guard_band_v0",
    val resourceEquationAxisCoverage: String = "live_tracee+cpu_rate+io_rate+rss_memory+budget+proot_score",
    val resourceEquationAxisContract: String =
        "ratio_each_axis_to_cap_use_max_axis_to_shape_parallelism_and_queue",
    val resourceEquationCpuAxisStatus: String = "not_observed_yet",
    val resourceEquationIoAxisStatus: String = "not_observed_yet",
    val resourceEquationMemoryAxisStatus: String = "android_rss_memory_only",
    val resourceEquationBlindAxisCount: Int = 0,
    val resourceEquationCalibrationGate: String = "collect_baseline",
    val resourceEquationLiveTraceeRatioPercent: Int = 0,
    val resourceEquationCpuRatioPercent: Int = 0,
    val resourceEquationIoRatioPercent: Int = 0,
    val resourceEquationMemoryRatioPercent: Int = 0,
    val resourceEquationBudgetRatioPercent: Int = 0,
    val resourceEquationPressureScorePercent: Int = 0,
    val resourceEquationRiskPercent: Int = 0,
    val resourceEquationHeadroomPercent: Int = 100,
    val resourceEquationBottleneckAxis: String = "none",
    val resourceEquationExpansionAllowed: Boolean = false,
    val resourceEquationLowPriorityQueueRequired: Boolean = true,
    val resourceEquationTargetParallelSlots: Int = 0,
    val resourceEquationTargetQueueDepth: Int = 0,
    val resourceEquationDecision: String = "wait_for_clean_input",
    val resourceEquationLiveTraceeRaw: Int = 0,
    val resourceEquationLiveTraceeCap: Int = 0,
    val resourceEquationCpuRawTicksPerSecond: Long = 0L,
    val resourceEquationCpuCapTicksPerSecond: Long = 0L,
    val resourceEquationIoRawBytesPerSecond: Long = 0L,
    val resourceEquationIoCapBytesPerSecond: Long = 0L,
    val resourceEquationMemoryRawLevel: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val resourceEquationMemoryCapLevel: RuntimePressureLevel = RuntimePressureLevel.HIGH,
    val resourceEquationBudgetRawState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val resourceEquationBudgetCapState: RuntimeBudgetState = RuntimeBudgetState.SOFT_PRESSURE,
    val resourceEquationPressureScoreRaw: Int = 0,
    val resourceEquationPressureScoreCap: Int = 100,
    val resourceEquationProportionalPolicy: String =
        "risk=max_axis_ratio,parallel_slots=step_down_by_risk,queue_depth=axis_overflow_or_background_policy",
    val resourceEquationCalibrationStatus: String = "collecting_resource_axes",
    val resourceEquationNextCalibrationFocus: String = "collect_passive_foreground_background_samples",
    val resourceEquationReason: String = "waiting_for_proot_resource_inputs",
    val probeProtocol: String = "live_tracee_probe_two_phase_v0",
    val probePhase: String = "WAIT_FOR_BASELINE",
    val probeSequence: String = "N=1,2,3,...,peak_plus_10_confirm_rounds",
    val probeDeclaredTargetLiveTracees: Int = 0,
    val probeBaselineContract: String = "liveTracees_0_score_0_forkExec_0_events_0_budget_healthy_or_near",
    val probeBaselineSatisfied: Boolean = false,
    val probePreflightLogRequired: Boolean = true,
    val probeSampleValid: Boolean = false,
    val probeSampleValidity: String = "not_ready",
    val probeObservedLiveTracees: Int = 0,
    val probeObservedTransientEvents: Int = 0,
    val probeCrashRecoveryKey: String = "read_last_PLAN_DECLARED_before_reboot",
    val reason: String = "waiting_for_proot_pool_inputs",
    val lanes: List<RuntimeProotPoolLanePlan> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation substrate=$substrateHealthy " +
            "pressure=$pressureState/$prootPressureScore stability=$pressureStabilityState " +
            "budget=$budgetOverallState active=$activeWorkloadCount slots=$plannedPoolSlots/" +
            "$effectivePoolSlots max=$maxPoolSlots spare=$sparePoolSlots burstHeadroom=$burstHeadroomLaneCount " +
            "backgroundShrink=$backgroundShrinkLaneCount idleReclaim=$idleReclaimCandidateCount " +
            "capacityAction=$capacityRequestedAction capacityReview=$capacityReviewNeeded " +
            "holdExpansion=$holdExpansionLaneCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxLanes: Int = 8): String {
        return buildString {
            appendLine("proot_pool_plan_mode=${mode.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_enforcement_mode=${enforcementMode.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("proot_pool_plan_generated_at=$generatedAtMs")
            appendLine("proot_pool_plan_state=${state.name}")
            appendLine("proot_pool_plan_recommendation=${recommendation.name}")
            appendLine("proot_pool_plan_substrate_healthy=$substrateHealthy")
            appendLine("proot_pool_plan_policy_substrate_usable=$policySubstrateUsable")
            appendLine("proot_pool_plan_policy_substrate_reason=${policySubstrateReason.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_probe_substrate_clean=$probeSubstrateClean")
            appendLine("proot_pool_plan_probe_substrate_reason=${probeSubstrateReason.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_lifecycle_state=${lifecycleState.name}")
            appendLine("proot_pool_plan_background_phase=${backgroundPhase.name}")
            appendLine("proot_pool_plan_pressure_state=${pressureState.name}")
            appendLine("proot_pool_plan_pressure_stability_state=${pressureStabilityState.name}")
            appendLine("proot_pool_plan_budget_overall_state=${budgetOverallState.name}")
            appendLine("proot_pool_plan_proot_signal=${prootSignalLevel.name}")
            appendLine("proot_pool_plan_proot_score=$prootPressureScore")
            appendLine("proot_pool_plan_events_window=$eventsInWindow")
            appendLine("proot_pool_plan_fork_exec_window=$forkExecEventsInWindow")
            appendLine("proot_pool_plan_live_tracees=$liveTraceeCount")
            appendLine("proot_pool_plan_policy_lane_count=$policyLaneCount")
            appendLine("proot_pool_plan_active_workload_count=$activeWorkloadCount")
            appendLine("proot_pool_plan_owner_container_count=$ownerContainerCount")
            appendLine("proot_pool_plan_owner_container_tracee_count=$ownerContainerTraceeCount")
            appendLine("proot_pool_plan_max_pool_slots=$maxPoolSlots")
            appendLine("proot_pool_plan_effective_pool_slots=$effectivePoolSlots")
            appendLine("proot_pool_plan_planned_pool_slots=$plannedPoolSlots")
            appendLine("proot_pool_plan_spare_pool_slots=$sparePoolSlots")
            appendLine("proot_pool_plan_burst_lane_count=$burstLaneCount")
            appendLine("proot_pool_plan_burst_headroom_lane_count=$burstHeadroomLaneCount")
            appendLine("proot_pool_plan_background_shrink_lane_count=$backgroundShrinkLaneCount")
            appendLine("proot_pool_plan_idle_reclaim_candidate_count=$idleReclaimCandidateCount")
            appendLine("proot_pool_plan_hold_expansion_lane_count=$holdExpansionLaneCount")
            appendLine("proot_pool_plan_saturated_lane_count=$saturatedLaneCount")
            appendLine("proot_pool_plan_capacity_request_contract=${capacityRequestContract.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_capacity_requested_action=${capacityRequestedAction.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_capacity_review_needed=$capacityReviewNeeded")
            appendLine("proot_pool_plan_capacity_expansion_requested=$capacityExpansionRequested")
            appendLine("proot_pool_plan_capacity_queue_requested=$capacityQueueRequested")
            appendLine("proot_pool_plan_capacity_downscale_requested=$capacityDownscaleRequested")
            appendLine("proot_pool_plan_capacity_downline_requested=$capacityDownlineRequested")
            appendLine("proot_pool_plan_capacity_request_reason=${capacityRequestReason.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_task_pressure_mode=${taskPressureMode.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_task_pressure_contract=${taskPressureContract.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_task_pressure_tier=${taskPressureTier.name}")
            appendLine("proot_pool_plan_task_pressure_action=${taskPressureAction.name}")
            appendLine("proot_pool_plan_task_pressure_reason=${taskPressureReason.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_task_pressure_primary_signal=${taskPressurePrimarySignal.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_task_pressure_profile_group=${taskPressureProfileGroup.name}")
            appendLine("proot_pool_plan_task_pressure_active_workloads=$taskPressureActiveWorkloads")
            appendLine("proot_pool_plan_task_pressure_running_roots=$taskPressureRunningRoots")
            appendLine("proot_pool_plan_task_pressure_queue_count=$taskPressureQueueCount")
            appendLine("proot_pool_plan_task_pressure_backlog_count=$taskPressureBacklogCount")
            appendLine("proot_pool_plan_task_pressure_no_capacity_count=$taskPressureNoCapacityCount")
            appendLine("proot_pool_plan_task_pressure_deferred_count=$taskPressureDeferredCount")
            appendLine("proot_pool_plan_task_pressure_saturated_lane_count=$taskPressureSaturatedLaneCount")
            appendLine("proot_pool_plan_task_pressure_over_effective_max_workloads=$taskPressureOverEffectiveMaxWorkloads")
            appendLine("proot_pool_plan_task_pressure_idle_downline_candidate_count=$taskPressureIdleDownlineCandidateCount")
            appendLine("proot_pool_plan_task_pressure_telemetry_healthy=$taskPressureTelemetryHealthy")
            appendLine("proot_pool_plan_task_pressure_telemetry_state=${taskPressureTelemetryState.name}")
            appendLine("proot_pool_plan_task_pressure_telemetry_signal=${taskPressureTelemetrySignal.name}")
            appendLine("proot_pool_plan_task_pressure_telemetry_score=$taskPressureTelemetryScore")
            appendLine("proot_pool_plan_task_pressure_latency_signal_status=${taskPressureLatencySignalStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_task_pressure_peak_tasks_role=${taskPressurePeakTasksRole.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_mode=${tuningMode.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_axis=${tuningAxis.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_status=${tuningStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_candidate_pool_slots=$tuningCandidatePoolSlots")
            appendLine("proot_pool_plan_tuning_next_candidate_if_pass=$tuningNextCandidateIfPass")
            appendLine("proot_pool_plan_tuning_next_candidate_if_fail=$tuningNextCandidateIfFail")
            appendLine("proot_pool_plan_tuning_lower_bound_pool_slots=$tuningLowerBoundPoolSlots")
            appendLine("proot_pool_plan_tuning_upper_bound_pool_slots=$tuningUpperBoundPoolSlots")
            appendLine("proot_pool_plan_tuning_stop_condition=${tuningStopCondition.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_outcome_source=${tuningOutcomeSource.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_crash_log_path=${tuningCrashLogPath.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_log_format=${tuningLogFormat.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_safe_restart_readable=$tuningSafeRestartReadable")
            appendLine("proot_pool_plan_tuning_gate_reason=${tuningGateReason.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_prerequisite=${tuningPrerequisite.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_pressure_attribution=${tuningPressureAttribution.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_tuning_can_probe_now=$tuningCanProbeNow")
            appendLine("proot_pool_plan_known_risk_sample_live_tracees=$knownRiskSampleLiveTracees")
            appendLine("proot_pool_plan_known_risk_sample_pressure_score=$knownRiskSamplePressureScore")
            appendLine("proot_pool_plan_known_risk_sample_attribution=${knownRiskSampleAttribution.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_known_risk_sample_device_scope=${knownRiskSampleDeviceScope.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_known_safe_lower_bound_live_tracees=$knownSafeLowerBoundLiveTracees")
            appendLine("proot_pool_plan_clean_baseline_required_for_lower_bound=$cleanBaselineRequiredForLowerBound")
            appendLine("proot_pool_plan_recommended_default_live_tracee_policy=${recommendedDefaultLiveTraceePolicy.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_device_calibration_overlay_status=${deviceCalibrationOverlayStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_device_calibration_overlay_source=${deviceCalibrationOverlaySource.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_device_calibration_overlay_valid=$deviceCalibrationOverlayValid")
            appendLine("proot_pool_plan_device_calibration_tracee_max_cap=$deviceCalibrationTraceeMaxCap")
            appendLine("proot_pool_plan_device_calibration_measured_max_tracees=$deviceCalibrationMeasuredMaxTracees")
            appendLine("proot_pool_plan_device_calibration_healthy_stable_tracee_cap=$deviceCalibrationHealthyStableTraceeCap")
            appendLine("proot_pool_plan_device_calibration_budget_knee_tracees=$deviceCalibrationBudgetKneeTracees")
            appendLine("proot_pool_plan_device_calibration_budget_knee_used_for_capacity=$deviceCalibrationBudgetKneeUsedForCapacity")
            appendLine("proot_pool_plan_device_calibration_budget_knee_policy=${deviceCalibrationBudgetKneePolicy.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_device_calibration_default_start_cap=$deviceCalibrationDefaultStartCap")
            appendLine("proot_pool_plan_device_calibration_tracee_soft_cap=$deviceCalibrationTraceeSoftCap")
            appendLine("proot_pool_plan_device_calibration_tracee_hard_cap=$deviceCalibrationTraceeHardCap")
            appendLine("proot_pool_plan_device_calibration_memory_worker_rss_kb=$deviceCalibrationMemoryWorkerRssKb")
            appendLine("proot_pool_plan_adaptive_policy_mode=${adaptivePolicyMode.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_profile_group=${adaptiveProfileGroup.name}")
            appendLine("proot_pool_plan_adaptive_profile_source=${adaptiveProfileSource.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_policy_status=${adaptivePolicyStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_user_context=${adaptiveUserContext.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_resource_limiter=${adaptiveResourceLimiter.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_memory_signal=${adaptiveMemorySignal.name}")
            appendLine("proot_pool_plan_adaptive_cpu_signal=${adaptiveCpuSignal.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_io_signal=${adaptiveIoSignal.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_cpu_busy_ticks_per_second=$adaptiveCpuBusyTicksPerSecond")
            appendLine("proot_pool_plan_adaptive_io_busy_bytes_per_second=$adaptiveIoBusyBytesPerSecond")
            appendLine("proot_pool_plan_adaptive_default_live_tracee_soft_cap=$adaptiveDefaultLiveTraceeSoftCap")
            appendLine("proot_pool_plan_adaptive_foreground_live_tracee_soft_cap=$adaptiveForegroundLiveTraceeSoftCap")
            appendLine("proot_pool_plan_adaptive_background_live_tracee_soft_cap=$adaptiveBackgroundLiveTraceeSoftCap")
            appendLine("proot_pool_plan_adaptive_effective_live_tracee_soft_cap=$adaptiveEffectiveLiveTraceeSoftCap")
            appendLine("proot_pool_plan_adaptive_hard_stop_live_tracees=$adaptiveHardStopLiveTracees")
            appendLine("proot_pool_plan_adaptive_queue_policy=${adaptiveQueuePolicy.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_low_priority_background_policy=${adaptiveLowPriorityBackgroundPolicy.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_concurrency_posture=${adaptiveConcurrencyPosture.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_queue_posture=${adaptiveQueuePosture.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_low_priority_background_allowed=$adaptiveLowPriorityBackgroundAllowed")
            appendLine("proot_pool_plan_adaptive_strategy_mode=${adaptiveStrategyMode.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_strategy_value_contract=${adaptiveStrategyValueContract.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_strategy_active_band=${adaptiveStrategyActiveBand.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_strategy_peak_tracees=$adaptiveStrategyPeakTracees")
            appendLine("proot_pool_plan_adaptive_strategy_single_proot_limit_tracees=$adaptiveStrategyQueueUntilTracees")
            appendLine("proot_pool_plan_adaptive_strategy_queue_until_tracees=$adaptiveStrategyQueueUntilTracees")
            appendLine("proot_pool_plan_adaptive_strategy_second_proot_trigger_tracees=$adaptiveStrategySecondProotTriggerTracees")
            appendLine("proot_pool_plan_adaptive_strategy_next_proot_trigger_tracees=$adaptiveStrategySecondProotTriggerTracees")
            appendLine("proot_pool_plan_adaptive_strategy_overflow_headroom_tracees=$adaptiveStrategyOverflowHeadroomTracees")
            appendLine("proot_pool_plan_adaptive_strategy_single_proot_overflow_percent=$adaptiveStrategySingleProotOverflowPercent")
            appendLine("proot_pool_plan_adaptive_strategy_overflow_percent_base=${adaptiveStrategyOverflowPercentBase.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_strategy_queue_headroom_percent=$adaptiveStrategyQueueHeadroomPercent")
            appendLine("proot_pool_plan_adaptive_strategy_second_proot_trigger_headroom_percent=$adaptiveStrategySecondProotTriggerHeadroomPercent")
            appendLine("proot_pool_plan_adaptive_strategy_next_proot_trigger_headroom_percent=$adaptiveStrategySecondProotTriggerHeadroomPercent")
            appendLine("proot_pool_plan_adaptive_strategy_percent_base=${adaptiveStrategyPercentBase.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_strategy_low_power_limit=$adaptiveStrategyLowPowerLimit")
            appendLine("proot_pool_plan_adaptive_strategy_balanced_limit=$adaptiveStrategyBalancedLimit")
            appendLine("proot_pool_plan_adaptive_strategy_high_performance_limit=$adaptiveStrategyHighPerformanceLimit")
            appendLine("proot_pool_plan_adaptive_profile_bands_operational=$adaptiveProfileBandsOperational")
            appendLine("proot_pool_plan_adaptive_profile_bands_role=${adaptiveProfileBandsRole.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_ui_protection_rule=${adaptiveUiProtectionRule.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_adaptive_reason=${adaptiveReason.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_mode=${resourceEquationMode.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_model=${resourceEquationModel.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_axis_coverage=${resourceEquationAxisCoverage.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_axis_contract=${resourceEquationAxisContract.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_cpu_axis_status=${resourceEquationCpuAxisStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_io_axis_status=${resourceEquationIoAxisStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_memory_axis_status=${resourceEquationMemoryAxisStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_blind_axis_count=$resourceEquationBlindAxisCount")
            appendLine("proot_pool_plan_resource_equation_calibration_gate=${resourceEquationCalibrationGate.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_live_tracee_ratio_percent=$resourceEquationLiveTraceeRatioPercent")
            appendLine("proot_pool_plan_resource_equation_cpu_ratio_percent=$resourceEquationCpuRatioPercent")
            appendLine("proot_pool_plan_resource_equation_io_ratio_percent=$resourceEquationIoRatioPercent")
            appendLine("proot_pool_plan_resource_equation_memory_ratio_percent=$resourceEquationMemoryRatioPercent")
            appendLine("proot_pool_plan_resource_equation_budget_ratio_percent=$resourceEquationBudgetRatioPercent")
            appendLine("proot_pool_plan_resource_equation_pressure_score_percent=$resourceEquationPressureScorePercent")
            appendLine("proot_pool_plan_resource_equation_risk_percent=$resourceEquationRiskPercent")
            appendLine("proot_pool_plan_resource_equation_headroom_percent=$resourceEquationHeadroomPercent")
            appendLine("proot_pool_plan_resource_equation_bottleneck_axis=${resourceEquationBottleneckAxis.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_expansion_allowed=$resourceEquationExpansionAllowed")
            appendLine("proot_pool_plan_resource_equation_low_priority_queue_required=$resourceEquationLowPriorityQueueRequired")
            appendLine("proot_pool_plan_resource_equation_target_parallel_slots=$resourceEquationTargetParallelSlots")
            appendLine("proot_pool_plan_resource_equation_target_queue_depth=$resourceEquationTargetQueueDepth")
            appendLine("proot_pool_plan_resource_equation_decision=${resourceEquationDecision.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_live_tracee_raw=$resourceEquationLiveTraceeRaw")
            appendLine("proot_pool_plan_resource_equation_live_tracee_cap=$resourceEquationLiveTraceeCap")
            appendLine("proot_pool_plan_resource_equation_cpu_raw_ticks_per_second=$resourceEquationCpuRawTicksPerSecond")
            appendLine("proot_pool_plan_resource_equation_cpu_cap_ticks_per_second=$resourceEquationCpuCapTicksPerSecond")
            appendLine("proot_pool_plan_resource_equation_io_raw_bytes_per_second=$resourceEquationIoRawBytesPerSecond")
            appendLine("proot_pool_plan_resource_equation_io_cap_bytes_per_second=$resourceEquationIoCapBytesPerSecond")
            appendLine("proot_pool_plan_resource_equation_memory_raw_level=${resourceEquationMemoryRawLevel.name}")
            appendLine("proot_pool_plan_resource_equation_memory_cap_level=${resourceEquationMemoryCapLevel.name}")
            appendLine("proot_pool_plan_resource_equation_budget_raw_state=${resourceEquationBudgetRawState.name}")
            appendLine("proot_pool_plan_resource_equation_budget_cap_state=${resourceEquationBudgetCapState.name}")
            appendLine("proot_pool_plan_resource_equation_pressure_score_raw=$resourceEquationPressureScoreRaw")
            appendLine("proot_pool_plan_resource_equation_pressure_score_cap=$resourceEquationPressureScoreCap")
            appendLine("proot_pool_plan_resource_equation_proportional_policy=${resourceEquationProportionalPolicy.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_calibration_status=${resourceEquationCalibrationStatus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_next_calibration_focus=${resourceEquationNextCalibrationFocus.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_resource_equation_reason=${resourceEquationReason.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_probe_protocol=${probeProtocol.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_probe_phase=${probePhase.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_probe_sequence=${probeSequence.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_probe_declared_target_live_tracees=$probeDeclaredTargetLiveTracees")
            appendLine("proot_pool_plan_probe_baseline_contract=${probeBaselineContract.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_probe_baseline_satisfied=$probeBaselineSatisfied")
            appendLine("proot_pool_plan_probe_preflight_log_required=$probePreflightLogRequired")
            appendLine("proot_pool_plan_probe_sample_valid=$probeSampleValid")
            appendLine("proot_pool_plan_probe_sample_validity=${probeSampleValidity.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_probe_observed_live_tracees=$probeObservedLiveTracees")
            appendLine("proot_pool_plan_probe_observed_transient_events=$probeObservedTransientEvents")
            appendLine("proot_pool_plan_probe_crash_recovery_key=${probeCrashRecoveryKey.toProotPoolEnvValue()}")
            appendLine("proot_pool_plan_reason=${reason.toProotPoolEnvValue()}")
            lanes.take(maxLanes).forEachIndexed { index, lane ->
                val prefix = "proot_pool_plan_lane_${index + 1}"
                appendLine("${prefix}_name=${lane.lane.name}")
                appendLine("${prefix}_priority=${lane.priority}")
                appendLine("${prefix}_active=${lane.activeWorkloads}")
                appendLine("${prefix}_max=${lane.maxConcurrency}")
                appendLine("${prefix}_background_max=${lane.backgroundMaxConcurrency}")
                appendLine("${prefix}_effective_max=${lane.effectiveMaxConcurrency}")
                appendLine("${prefix}_burst_ceiling=${lane.burstCeiling}")
                appendLine("${prefix}_planned_slots=${lane.plannedPoolSlots}")
                appendLine("${prefix}_existing_active_slots=${lane.existingActiveSlots}")
                appendLine("${prefix}_new_admission_slots=${lane.newAdmissionSlots}")
                appendLine("${prefix}_over_effective_max=${lane.overEffectiveMaxWorkloads}")
                appendLine("${prefix}_spare_slots=${lane.spareSlots}")
                appendLine("${prefix}_serial=${lane.serial}")
                appendLine("${prefix}_allow_burst=${lane.allowBurst}")
                appendLine("${prefix}_admission_state=${lane.admissionState.name}")
                appendLine("${prefix}_action=${lane.action.name}")
                appendLine("${prefix}_reason=${lane.reason.toProotPoolEnvValue()}")
                appendLine("${prefix}_active_disposition=${lane.activeWorkloadDisposition.toProotPoolEnvValue()}")
            }
            appendLine("proot_pool_plan_boundary=observe_only_no_pool_resize_no_spawn_no_kill_no_queue_no_lane_control")
        }
    }
}

object RuntimeProotPoolPlanDryRun {
    fun evaluate(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot = RuntimeStartQueuePlanDryRunSnapshot(),
        lifecyclePolicyProfileSurface: RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot =
            RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot(),
        deviceCalibrationOverlay: RuntimeProotDeviceCalibrationOverlay =
            RuntimeProotDeviceCalibrationOverlay(),
        declaredProbeTargetLiveTracees: Int = 0,
        now: Long = System.currentTimeMillis()
    ): RuntimeProotPoolPlanDryRunSnapshot {
        val policySubstrate = prootTelemetryHealth.toProotPolicySubstrate()
        val substrateHealthy = policySubstrate.policyUsable
        val highPressure = pressureConsumer.isHighSignal() ||
            pressureStability.state == RuntimePressureStabilityState.PRESSURE_HOLD ||
            budgetPressure.overallState.severity() >= RuntimeBudgetState.SOFT_PRESSURE.severity()
        val stable = substrateHealthy &&
            pressureStability.state == RuntimePressureStabilityState.STABLE_NOW &&
            budgetPressure.overallState.severity() <= RuntimeBudgetState.NEAR_BUDGET.severity()
        val pressureAttribution = attributePressure(
            eventsInWindow = pressureConsumer.eventsInWindow,
            forkExecEventsInWindow = pressureConsumer.forkExecEventsInWindow,
            liveTraceeCount = pressureConsumer.liveTraceeCount,
            prootPressureScore = pressureConsumer.prootPressureScore,
            budgetPressure = budgetPressure
        )
        val knownSafeLowerBoundLiveTracees = if (deviceCalibrationOverlay.valid) {
            deviceCalibrationOverlay.healthyStableTraceeCap
                .takeIf { it > 0 }
                ?: deviceCalibrationOverlay.traceeSoftCap
        } else {
            17
        }
        val knownRiskUpperBoundLiveTracees = if (deviceCalibrationOverlay.valid) {
            deviceCalibrationOverlay.traceeHardCap
        } else {
            18
        }
        val adaptivePolicy = buildAdaptivePolicy(
            pressureAttribution = pressureAttribution,
            pressureConsumer = pressureConsumer,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            lifecyclePolicyProfileSurface = lifecyclePolicyProfileSurface,
            knownSafeLowerBoundLiveTracees = knownSafeLowerBoundLiveTracees,
            knownRiskUpperBoundLiveTracees = knownRiskUpperBoundLiveTracees,
            calibrationOverlay = deviceCalibrationOverlay
        )
        val resourceEquation = buildResourceEquation(
            substrateHealthy = substrateHealthy,
            pressureConsumer = pressureConsumer,
            backgroundDecay = backgroundDecay,
            budgetPressure = budgetPressure,
            adaptivePolicy = adaptivePolicy,
            maxPoolSlots = laneAdmission.lanes.sumOf { it.effectiveMaxConcurrency },
            activeWorkloadCount = workloadRegistry.totalWorkloads
        )

        val lanes = laneAdmission.lanes
            .sortedWith(compareBy<RuntimeLaneAdmissionLane> { it.priority }.thenBy { it.lane.ordinal })
            .map { lane ->
                lane.toPoolLanePlan(
                    substrateHealthy = substrateHealthy,
                    highPressure = highPressure,
                    stable = stable,
                    inForeground = backgroundDecay.lifecycleState == RuntimeAppVisibilityState.FOREGROUND,
                    adaptivePolicy = adaptivePolicy
                )
            }

        val state = resolveState(
            substrateHealthy = substrateHealthy,
            pressureConsumer = pressureConsumer,
            pressureStability = pressureStability,
            lanes = lanes
        )
        val tuning = buildTuningPlan(
            substrateHealthy = policySubstrate.probeClean,
            highPressure = highPressure,
            stable = stable,
            plannedPoolSlots = lanes.sumOf { it.plannedPoolSlots },
            effectivePoolSlots = lanes.sumOf { it.effectiveMaxConcurrency }
        )
        val probeProtocol = buildProbeProtocol(
            substrateHealthy = policySubstrate.probeClean,
            highPressure = highPressure,
            stable = stable,
            pressureConsumer = pressureConsumer,
            budgetPressure = budgetPressure,
            declaredProbeTargetLiveTracees = declaredProbeTargetLiveTracees
        )
        val backgroundShrinkLaneCount = lanes.count {
            it.action == RuntimeProotPoolLaneAction.REVIEW_BACKGROUND_SHRINK
        }
        val idleReclaimCandidateCount = lanes.count {
            it.action == RuntimeProotPoolLaneAction.REVIEW_IDLE_RECLAIM
        }
        val taskPressure = buildTaskPressure(
            policySubstrateUsable = policySubstrate.policyUsable,
            pressureConsumer = pressureConsumer,
            budgetPressure = budgetPressure,
            laneAdmission = laneAdmission,
            startQueuePlan = startQueuePlan,
            workloadRegistry = workloadRegistry,
            adaptivePolicy = adaptivePolicy,
            backgroundShrinkLaneCount = backgroundShrinkLaneCount,
            idleReclaimCandidateCount = idleReclaimCandidateCount
        )
        val capacityRequest = buildCapacityRequest(
            policySubstrateUsable = policySubstrate.policyUsable,
            taskPressure = taskPressure
        )
        val ownerContainerEntries = workloadRegistry.entries.filter { entry ->
            entry.ownerKind == RuntimeRootOwnerKind.CARD ||
                entry.ownerKind == RuntimeRootOwnerKind.RESOURCE
        }

        return RuntimeProotPoolPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            substrateHealthy = substrateHealthy,
            policySubstrateUsable = policySubstrate.policyUsable,
            policySubstrateReason = policySubstrate.policyReason,
            probeSubstrateClean = policySubstrate.probeClean,
            probeSubstrateReason = policySubstrate.probeReason,
            lifecycleState = backgroundDecay.lifecycleState,
            backgroundPhase = backgroundDecay.phase,
            pressureState = pressureConsumer.state,
            pressureStabilityState = pressureStability.state,
            budgetOverallState = budgetPressure.overallState,
            prootSignalLevel = pressureConsumer.prootSignalLevel,
            prootPressureScore = pressureConsumer.prootPressureScore,
            eventsInWindow = pressureConsumer.eventsInWindow,
            forkExecEventsInWindow = pressureConsumer.forkExecEventsInWindow,
            liveTraceeCount = pressureConsumer.liveTraceeCount,
            policyLaneCount = laneAdmission.policyLaneCount,
            activeWorkloadCount = workloadRegistry.totalWorkloads,
            ownerContainerCount = ownerContainerEntries.size,
            ownerContainerTraceeCount = ownerContainerEntries.sumOf { it.processCount },
            maxPoolSlots = lanes.sumOf { it.maxConcurrency },
            effectivePoolSlots = lanes.sumOf { it.effectiveMaxConcurrency },
            plannedPoolSlots = lanes.sumOf { it.plannedPoolSlots },
            sparePoolSlots = lanes.sumOf { it.spareSlots },
            burstLaneCount = lanes.count { it.allowBurst },
            burstHeadroomLaneCount = lanes.count {
                it.action == RuntimeProotPoolLaneAction.REVIEW_BURST_HEADROOM
            },
            backgroundShrinkLaneCount = backgroundShrinkLaneCount,
            idleReclaimCandidateCount = idleReclaimCandidateCount,
            holdExpansionLaneCount = lanes.count {
                it.action == RuntimeProotPoolLaneAction.HOLD_EXPANSION
            },
            saturatedLaneCount = lanes.count {
                it.admissionState == RuntimeLaneAdmissionState.SATURATED ||
                    it.admissionState == RuntimeLaneAdmissionState.NO_CAPACITY
            },
            capacityRequestContract = capacityRequest.contract,
            capacityRequestedAction = capacityRequest.requestedAction,
            capacityReviewNeeded = capacityRequest.reviewNeeded,
            capacityExpansionRequested = capacityRequest.expansionRequested,
            capacityQueueRequested = capacityRequest.queueRequested,
            capacityDownscaleRequested = capacityRequest.downscaleRequested,
            capacityDownlineRequested = capacityRequest.downlineRequested,
            capacityRequestReason = capacityRequest.reason,
            taskPressureMode = taskPressure.mode,
            taskPressureContract = taskPressure.contract,
            taskPressureTier = taskPressure.tier,
            taskPressureAction = taskPressure.action,
            taskPressureReason = taskPressure.reason,
            taskPressurePrimarySignal = taskPressure.primarySignal,
            taskPressureProfileGroup = taskPressure.profileGroup,
            taskPressureActiveWorkloads = taskPressure.activeWorkloads,
            taskPressureRunningRoots = taskPressure.runningRoots,
            taskPressureQueueCount = taskPressure.queueCount,
            taskPressureBacklogCount = taskPressure.backlogCount,
            taskPressureNoCapacityCount = taskPressure.noCapacityCount,
            taskPressureDeferredCount = taskPressure.deferredCount,
            taskPressureSaturatedLaneCount = taskPressure.saturatedLaneCount,
            taskPressureOverEffectiveMaxWorkloads = taskPressure.overEffectiveMaxWorkloads,
            taskPressureIdleDownlineCandidateCount = taskPressure.idleDownlineCandidateCount,
            taskPressureTelemetryHealthy = taskPressure.telemetryHealthy,
            taskPressureTelemetryState = taskPressure.telemetryState,
            taskPressureTelemetrySignal = taskPressure.telemetrySignal,
            taskPressureTelemetryScore = taskPressure.telemetryScore,
            taskPressureLatencySignalStatus = taskPressure.latencySignalStatus,
            taskPressurePeakTasksRole = taskPressure.peakTasksRole,
            tuningStatus = tuning.status,
            tuningCandidatePoolSlots = tuning.candidate,
            tuningNextCandidateIfPass = tuning.nextIfPass,
            tuningNextCandidateIfFail = tuning.nextIfFail,
            tuningLowerBoundPoolSlots = tuning.lowerBound,
            tuningUpperBoundPoolSlots = tuning.upperBound,
            tuningGateReason = tuning.gateReason,
            tuningPressureAttribution = pressureAttribution,
            tuningCanProbeNow = tuning.canProbeNow,
            knownRiskSampleLiveTracees = knownRiskUpperBoundLiveTracees,
            knownRiskSampleDeviceScope = deviceCalibrationOverlay.source,
            knownSafeLowerBoundLiveTracees = knownSafeLowerBoundLiveTracees,
            cleanBaselineRequiredForLowerBound = !deviceCalibrationOverlay.valid,
            recommendedDefaultLiveTraceePolicy = if (deviceCalibrationOverlay.valid) {
                "apply_local_device_calibration_overlay_to_adaptive_policy"
            } else {
                "safe_defaults_until_p0_device_calibration_overlay_exists"
            },
            deviceCalibrationOverlayStatus = deviceCalibrationOverlay.status,
            deviceCalibrationOverlaySource = deviceCalibrationOverlay.source,
            deviceCalibrationOverlayValid = deviceCalibrationOverlay.valid,
            deviceCalibrationTraceeMaxCap = deviceCalibrationOverlay.traceeMaxCap,
            deviceCalibrationMeasuredMaxTracees = deviceCalibrationOverlay.measuredMaxTracees,
            deviceCalibrationHealthyStableTraceeCap = deviceCalibrationOverlay.healthyStableTraceeCap,
            deviceCalibrationBudgetKneeTracees = deviceCalibrationOverlay.budgetKneeTracees,
            deviceCalibrationBudgetKneeUsedForCapacity = deviceCalibrationOverlay.budgetKneeUsedForCapacity,
            deviceCalibrationBudgetKneePolicy = deviceCalibrationOverlay.budgetKneePolicy,
            deviceCalibrationDefaultStartCap = deviceCalibrationOverlay.defaultStartCap,
            deviceCalibrationTraceeSoftCap = deviceCalibrationOverlay.traceeSoftCap,
            deviceCalibrationTraceeHardCap = deviceCalibrationOverlay.traceeHardCap,
            deviceCalibrationMemoryWorkerRssKb = deviceCalibrationOverlay.memoryWorkerRssKb,
            adaptiveProfileGroup = adaptivePolicy.profileGroup,
            adaptiveProfileSource = adaptivePolicy.profileSource,
            adaptivePolicyStatus = adaptivePolicy.status,
            adaptiveUserContext = adaptivePolicy.userContext,
            adaptiveResourceLimiter = adaptivePolicy.resourceLimiter,
            adaptiveMemorySignal = adaptivePolicy.memorySignal,
            adaptiveCpuSignal = adaptivePolicy.cpuSignal,
            adaptiveIoSignal = adaptivePolicy.ioSignal,
            adaptiveCpuBusyTicksPerSecond = adaptivePolicy.cpuBusyTicksPerSecond,
            adaptiveIoBusyBytesPerSecond = adaptivePolicy.ioBusyBytesPerSecond,
            adaptiveDefaultLiveTraceeSoftCap = adaptivePolicy.defaultLiveTraceeSoftCap,
            adaptiveForegroundLiveTraceeSoftCap = adaptivePolicy.foregroundLiveTraceeSoftCap,
            adaptiveBackgroundLiveTraceeSoftCap = adaptivePolicy.backgroundLiveTraceeSoftCap,
            adaptiveEffectiveLiveTraceeSoftCap = adaptivePolicy.effectiveLiveTraceeSoftCap,
            adaptiveHardStopLiveTracees = adaptivePolicy.hardStopLiveTracees,
            adaptiveQueuePolicy = adaptivePolicy.queuePolicy,
            adaptiveLowPriorityBackgroundPolicy = adaptivePolicy.lowPriorityBackgroundPolicy,
            adaptiveConcurrencyPosture = adaptivePolicy.concurrencyPosture,
            adaptiveQueuePosture = adaptivePolicy.queuePosture,
            adaptiveLowPriorityBackgroundAllowed = adaptivePolicy.lowPriorityBackgroundAllowed,
            adaptiveStrategyMode = adaptivePolicy.strategyMode,
            adaptiveStrategyValueContract = adaptivePolicy.strategyValueContract,
            adaptiveStrategyActiveBand = adaptivePolicy.strategyActiveBand,
            adaptiveStrategyPeakTracees = adaptivePolicy.strategyPeakTracees,
            adaptiveStrategyQueueUntilTracees = adaptivePolicy.strategyQueueUntilTracees,
            adaptiveStrategySecondProotTriggerTracees = adaptivePolicy.strategySecondProotTriggerTracees,
            adaptiveStrategyOverflowHeadroomTracees = adaptivePolicy.strategyOverflowHeadroomTracees,
            adaptiveStrategySingleProotOverflowPercent = adaptivePolicy.strategySingleProotOverflowPercent,
            adaptiveStrategyOverflowPercentBase = adaptivePolicy.strategyOverflowPercentBase,
            adaptiveStrategyQueueHeadroomPercent = adaptivePolicy.strategyQueueHeadroomPercent,
            adaptiveStrategySecondProotTriggerHeadroomPercent =
                adaptivePolicy.strategySecondProotTriggerHeadroomPercent,
            adaptiveStrategyPercentBase = adaptivePolicy.strategyPercentBase,
            adaptiveStrategyLowPowerLimit = adaptivePolicy.strategyLowPowerLimit,
            adaptiveStrategyBalancedLimit = adaptivePolicy.strategyBalancedLimit,
            adaptiveStrategyHighPerformanceLimit = adaptivePolicy.strategyHighPerformanceLimit,
            adaptiveUiProtectionRule = adaptivePolicy.uiProtectionRule,
            adaptiveReason = adaptivePolicy.reason,
            resourceEquationMode = resourceEquation.mode,
            resourceEquationModel = resourceEquation.model,
            resourceEquationAxisCoverage = resourceEquation.axisCoverage,
            resourceEquationAxisContract = resourceEquation.axisContract,
            resourceEquationCpuAxisStatus = resourceEquation.cpuAxisStatus,
            resourceEquationIoAxisStatus = resourceEquation.ioAxisStatus,
            resourceEquationMemoryAxisStatus = resourceEquation.memoryAxisStatus,
            resourceEquationBlindAxisCount = resourceEquation.blindAxisCount,
            resourceEquationCalibrationGate = resourceEquation.calibrationGate,
            resourceEquationLiveTraceeRatioPercent = resourceEquation.liveTraceeRatioPercent,
            resourceEquationCpuRatioPercent = resourceEquation.cpuRatioPercent,
            resourceEquationIoRatioPercent = resourceEquation.ioRatioPercent,
            resourceEquationMemoryRatioPercent = resourceEquation.memoryRatioPercent,
            resourceEquationBudgetRatioPercent = resourceEquation.budgetRatioPercent,
            resourceEquationPressureScorePercent = resourceEquation.pressureScorePercent,
            resourceEquationRiskPercent = resourceEquation.riskPercent,
            resourceEquationHeadroomPercent = resourceEquation.headroomPercent,
            resourceEquationBottleneckAxis = resourceEquation.bottleneckAxis,
            resourceEquationExpansionAllowed = resourceEquation.expansionAllowed,
            resourceEquationLowPriorityQueueRequired = resourceEquation.lowPriorityQueueRequired,
            resourceEquationTargetParallelSlots = resourceEquation.targetParallelSlots,
            resourceEquationTargetQueueDepth = resourceEquation.targetQueueDepth,
            resourceEquationDecision = resourceEquation.decision,
            resourceEquationLiveTraceeRaw = resourceEquation.liveTraceeRaw,
            resourceEquationLiveTraceeCap = resourceEquation.liveTraceeCap,
            resourceEquationCpuRawTicksPerSecond = resourceEquation.cpuRawTicksPerSecond,
            resourceEquationCpuCapTicksPerSecond = resourceEquation.cpuCapTicksPerSecond,
            resourceEquationIoRawBytesPerSecond = resourceEquation.ioRawBytesPerSecond,
            resourceEquationIoCapBytesPerSecond = resourceEquation.ioCapBytesPerSecond,
            resourceEquationMemoryRawLevel = resourceEquation.memoryRawLevel,
            resourceEquationMemoryCapLevel = resourceEquation.memoryCapLevel,
            resourceEquationBudgetRawState = resourceEquation.budgetRawState,
            resourceEquationBudgetCapState = resourceEquation.budgetCapState,
            resourceEquationPressureScoreRaw = resourceEquation.pressureScoreRaw,
            resourceEquationPressureScoreCap = resourceEquation.pressureScoreCap,
            resourceEquationProportionalPolicy = resourceEquation.proportionalPolicy,
            resourceEquationCalibrationStatus = resourceEquation.calibrationStatus,
            resourceEquationNextCalibrationFocus = resourceEquation.nextCalibrationFocus,
            resourceEquationReason = resourceEquation.reason,
            probePhase = probeProtocol.phase,
            probeDeclaredTargetLiveTracees = probeProtocol.declaredTarget,
            probeBaselineSatisfied = probeProtocol.baselineSatisfied,
            probeSampleValid = probeProtocol.sampleValid,
            probeSampleValidity = probeProtocol.sampleValidity,
            probeObservedLiveTracees = pressureConsumer.liveTraceeCount,
            probeObservedTransientEvents = pressureConsumer.eventsInWindow +
                pressureConsumer.forkExecEventsInWindow,
            reason = buildReason(
                state,
                prootTelemetryHealth,
                policySubstrate,
                pressureConsumer,
                pressureStability,
                budgetPressure
            ),
            lanes = lanes
        )
    }

    private data class TuningPlan(
        val status: String,
        val candidate: Int,
        val nextIfPass: Int,
        val nextIfFail: Int,
        val lowerBound: Int,
        val upperBound: Int,
        val gateReason: String,
        val canProbeNow: Boolean
    )

    private data class ProbeProtocol(
        val phase: String,
        val declaredTarget: Int,
        val baselineSatisfied: Boolean,
        val sampleValid: Boolean,
        val sampleValidity: String
    )

    private fun buildAdaptivePolicy(
        pressureAttribution: String,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        lifecyclePolicyProfileSurface: RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot,
        knownSafeLowerBoundLiveTracees: Int,
        knownRiskUpperBoundLiveTracees: Int,
        calibrationOverlay: RuntimeProotDeviceCalibrationOverlay
    ): RuntimeProotAdaptivePolicy {
        val profileParams = resolveAdaptiveProfileParams(
            profileGroup = lifecyclePolicyProfileSurface.activeProfileGroup,
            knownSafeLowerBoundLiveTracees = knownSafeLowerBoundLiveTracees,
            knownRiskUpperBoundLiveTracees = knownRiskUpperBoundLiveTracees,
            calibrationOverlay = calibrationOverlay
        )
        val userContext = when (backgroundDecay.lifecycleState) {
            RuntimeAppVisibilityState.FOREGROUND -> "kf_foreground_performance_bias"
            RuntimeAppVisibilityState.UI_HIDDEN -> "user_foreground_protection_bias"
            RuntimeAppVisibilityState.BACKGROUND_PRESSURE -> "kf_background_survival_bias"
            RuntimeAppVisibilityState.LOW_MEMORY -> "user_foreground_memory_protection_bias"
        }
        val memoryLimitedCap = when (pressureConsumer.rssPressureLevel) {
            RuntimePressureLevel.CRITICAL -> 0
            RuntimePressureLevel.HIGH -> 4
            RuntimePressureLevel.ELEVATED -> profileParams.backgroundLiveTraceeSoftCap
            RuntimePressureLevel.NORMAL,
            RuntimePressureLevel.UNKNOWN -> profileParams.defaultLiveTraceeSoftCap
        }
        val cpuRatePressure = pressureConsumer.resourceTrendStatus == "rate_window_valid" &&
            pressureConsumer.rootCpuTicksPerSecond >= profileParams.cpuBusyTicksPerSecond
        val ioRatePressure = pressureConsumer.resourceTrendStatus == "rate_window_valid" &&
            pressureConsumer.rootIoBytesPerSecond >= profileParams.ioBusyBytesPerSecond
        val cpuLimitedCap = when {
            cpuRatePressure -> profileParams.backgroundLiveTraceeSoftCap
            else -> profileParams.defaultLiveTraceeSoftCap
        }
        val ioLimitedCap = when {
            ioRatePressure -> profileParams.backgroundLiveTraceeSoftCap
            else -> profileParams.defaultLiveTraceeSoftCap
        }
        val contextCap = when (backgroundDecay.lifecycleState) {
            RuntimeAppVisibilityState.FOREGROUND -> profileParams.foregroundLiveTraceeSoftCap
            RuntimeAppVisibilityState.UI_HIDDEN -> profileParams.backgroundLiveTraceeSoftCap
            RuntimeAppVisibilityState.BACKGROUND_PRESSURE -> minOf(profileParams.backgroundLiveTraceeSoftCap, 6)
            RuntimeAppVisibilityState.LOW_MEMORY -> 0
        }
        val budgetCap = when {
            budgetPressure.overallState.severity() >= RuntimeBudgetState.HARD_PRESSURE.severity() -> 0
            budgetPressure.overallState.severity() >= RuntimeBudgetState.SOFT_PRESSURE.severity() -> minOf(contextCap, 4)
            budgetPressure.overallState.severity() >= RuntimeBudgetState.NEAR_BUDGET.severity() ->
                minOf(contextCap, profileParams.backgroundLiveTraceeSoftCap)
            else -> contextCap
        }
        val effectiveCap = minOf(memoryLimitedCap, budgetCap, cpuLimitedCap, ioLimitedCap).coerceAtLeast(0)
        val overEffectiveCap = effectiveCap > 0 && pressureConsumer.liveTraceeCount >= effectiveCap
        val atHardStop = pressureConsumer.liveTraceeCount >= profileParams.hardStopLiveTracees ||
            budgetPressure.overallState.severity() >= RuntimeBudgetState.SOFT_PRESSURE.severity() ||
            pressureConsumer.state == RuntimePressureConsumerState.BURST ||
            pressureConsumer.state == RuntimePressureConsumerState.DEGRADED ||
            pressureConsumer.rssPressureLevel == RuntimePressureLevel.HIGH ||
            pressureConsumer.rssPressureLevel == RuntimePressureLevel.CRITICAL

        val concurrencyPosture = when {
            effectiveCap <= 0 -> "stop_new_proot_work_until_memory_or_budget_recovers"
            atHardStop -> "hold_expansion_mark_upper_bound"
            overEffectiveCap -> "hold_low_priority_expansion_keep_interactive_headroom"
            backgroundDecay.lifecycleState != RuntimeAppVisibilityState.FOREGROUND ->
                "background_limited_run_high_priority_only"
            else -> "allow_until_effective_soft_cap"
        }
        val queuePosture = when {
            effectiveCap <= 0 -> "queue_all_new_work_except_existing_keep"
            atHardStop -> "queue_or_defer_low_priority_until_pressure_eases"
            overEffectiveCap -> "queue_low_priority_keep_interactive_and_pinned"
            backgroundDecay.lifecycleState != RuntimeAppVisibilityState.FOREGROUND ->
                "queue_background_low_priority_by_default"
            else -> "run_high_priority_queue_low_priority_when_over_cap"
        }
        val lowPriorityBackgroundAllowed = when (profileParams.lowPriorityBackgroundPolicy) {
            "disabled" -> false
            "foreground_only_when_healthy_under_cap",
            "short_foreground_bursts_when_healthy_under_cap" ->
                backgroundDecay.lifecycleState == RuntimeAppVisibilityState.FOREGROUND &&
                    !overEffectiveCap &&
                    !atHardStop &&
                    budgetPressure.overallState == RuntimeBudgetState.HEALTHY
            else -> false
        }
        val activeStrategyBand = when {
            effectiveCap <= 0 || atHardStop ||
                budgetPressure.overallState.severity() >= RuntimeBudgetState.SOFT_PRESSURE.severity() ->
                "HARD_GUARD"
            overEffectiveCap ||
                pressureConsumer.liveTraceeCount >= profileParams.secondProotTriggerTracees ->
                "NEXT_PROOT_NEEDED"
            pressureConsumer.liveTraceeCount >= profileParams.singleProotPeakTracees ->
                "FILL_SINGLE_PROOT_OVER_PEAK"
            backgroundDecay.lifecycleState != RuntimeAppVisibilityState.FOREGROUND ->
                "BACKGROUND_SINGLE_PROOT_LIMIT"
            else -> "FILL_SINGLE_PROOT_TO_PEAK"
        }
        val status = when {
            knownSafeLowerBoundLiveTracees <= 0 -> "needs_device_calibration"
            atHardStop -> "bounded_upper_guard_active"
            overEffectiveCap -> "bounded_soft_cap_guard_active"
            backgroundDecay.lifecycleState != RuntimeAppVisibilityState.FOREGROUND ->
                "bounded_user_protection_active"
            else -> "bounded_observe_only"
        }
        val resourceLimiter = when {
            pressureConsumer.rssPressureLevel == RuntimePressureLevel.HIGH ||
                pressureConsumer.rssPressureLevel == RuntimePressureLevel.CRITICAL -> "memory_pressure"
            cpuRatePressure -> "cpu_rate_pressure"
            ioRatePressure -> "io_rate_pressure"
            overEffectiveCap -> "live_tracee_soft_cap"
            pressureAttribution != "baseline_or_unknown" -> pressureAttribution
            budgetPressure.overallState.severity() >= RuntimeBudgetState.NEAR_BUDGET.severity() -> "budget_pressure"
            else -> "no_current_limiter"
        }
        val statusPrefix = if (calibrationOverlay.valid) {
            "device_calibrated"
        } else {
            "safe_default"
        }

        return RuntimeProotAdaptivePolicy(
            profileGroup = profileParams.profileGroup,
            profileSource = profileParams.profileSource,
            status = "${statusPrefix}_$status",
            userContext = userContext,
            resourceLimiter = resourceLimiter,
            memorySignal = pressureConsumer.rssPressureLevel,
            cpuSignal = buildCpuAxisSignal(
                trendStatus = pressureConsumer.resourceTrendStatus,
                ticksPerSecond = pressureConsumer.rootCpuTicksPerSecond,
                pressureThresholdTicksPerSecond = profileParams.cpuBusyTicksPerSecond
            ),
            ioSignal = buildIoAxisSignal(
                trendStatus = pressureConsumer.resourceTrendStatus,
                bytesPerSecond = pressureConsumer.rootIoBytesPerSecond,
                pressureThresholdBytesPerSecond = profileParams.ioBusyBytesPerSecond
            ),
            cpuBusyTicksPerSecond = profileParams.cpuBusyTicksPerSecond,
            ioBusyBytesPerSecond = profileParams.ioBusyBytesPerSecond,
            defaultLiveTraceeSoftCap = profileParams.defaultLiveTraceeSoftCap,
            foregroundLiveTraceeSoftCap = profileParams.foregroundLiveTraceeSoftCap,
            backgroundLiveTraceeSoftCap = profileParams.backgroundLiveTraceeSoftCap,
            effectiveLiveTraceeSoftCap = effectiveCap,
            hardStopLiveTracees = profileParams.hardStopLiveTracees,
            queuePolicy = profileParams.queuePolicy,
            lowPriorityBackgroundPolicy = profileParams.lowPriorityBackgroundPolicy,
            concurrencyPosture = concurrencyPosture,
            queuePosture = queuePosture,
            lowPriorityBackgroundAllowed = lowPriorityBackgroundAllowed,
            strategyMode = "single_proot_peak_multiplier_then_next_proot_v1",
            strategyValueContract =
                "measure_standard_task_throughput_peak_times_single_multiplier_then_next_proot",
            strategyActiveBand = activeStrategyBand,
            strategyPeakTracees = profileParams.singleProotPeakTracees,
            strategyQueueUntilTracees = profileParams.singleProotQueueUntilTracees,
            strategySecondProotTriggerTracees = profileParams.secondProotTriggerTracees,
            strategyOverflowHeadroomTracees = profileParams.overflowHeadroomTracees,
            strategySingleProotOverflowPercent = profileParams.singleProotOverflowPercent,
            strategyOverflowPercentBase = profileParams.overflowPercentBase,
            strategyQueueHeadroomPercent = profileParams.queueHeadroomPercent,
            strategySecondProotTriggerHeadroomPercent = profileParams.secondProotTriggerHeadroomPercent,
            strategyPercentBase = profileParams.queueStrategyPercentBase,
            strategyLowPowerLimit = profileParams.singleProotPeakTracees,
            strategyBalancedLimit = profileParams.singleProotQueueUntilTracees,
            strategyHighPerformanceLimit = profileParams.highPerformanceProfileLimit,
            uiProtectionRule = profileParams.uiProtectionRule,
            reason = "profile=${profileParams.profileGroup.name},context=$userContext," +
                "calibration=${calibrationOverlay.status}/${calibrationOverlay.valid}," +
                "live=${pressureConsumer.liveTraceeCount},cap=$effectiveCap," +
                "hardStop=${profileParams.hardStopLiveTracees},budget=${budgetPressure.overallState.name}," +
                "rss=${pressureConsumer.rssPressureLevel.name}," +
                "cpuRate=${pressureConsumer.rootCpuTicksPerSecond}/${profileParams.cpuBusyTicksPerSecond}," +
                "ioRate=${pressureConsumer.rootIoBytesPerSecond}/${profileParams.ioBusyBytesPerSecond}," +
                "limiter=$resourceLimiter"
        )
    }

    private fun resolveAdaptiveProfileParams(
        profileGroup: RuntimeLifecyclePolicyProfileGroup,
        knownSafeLowerBoundLiveTracees: Int,
        knownRiskUpperBoundLiveTracees: Int,
        calibrationOverlay: RuntimeProotDeviceCalibrationOverlay
    ): RuntimeProotAdaptiveProfileParams {
        val safeLowerBound = knownSafeLowerBoundLiveTracees.coerceAtLeast(1)
        val riskUpperBound = knownRiskUpperBoundLiveTracees.coerceAtLeast(safeLowerBound + 1)
        val calibratedPeakCap = if (calibrationOverlay.valid) {
            calibrationOverlay.singleProotPeakTracees.coerceIn(1, riskUpperBound)
        } else {
            minOf(SAFE_DEFAULT_START_TRACEES, safeLowerBound, riskUpperBound - 1).coerceAtLeast(1)
        }
        val defaultScaleOutThreshold = scaleOutThreshold(calibratedPeakCap)
        val calibratedSecondProotTrigger = defaultScaleOutThreshold
        val calibratedQueueUntilCap = if (calibrationOverlay.valid) {
            calibrationOverlay.singleProotQueueUntilTracees
                .coerceIn(calibratedPeakCap, calibratedSecondProotTrigger - 1)
        } else {
            calibratedPeakCap
        }
        val calibratedOverflowHeadroom = if (calibrationOverlay.valid) {
            calibrationOverlay.overflowHeadroomTracees.coerceAtLeast(0)
        } else {
            0
        }
        val defaultSoftCap = calibratedPeakCap
        val lowPowerCap = if (calibrationOverlay.valid) {
            calibratedPeakCap
        } else {
            minOf(SAFE_DEFAULT_START_TRACEES / 2, safeLowerBound, riskUpperBound - 1).coerceAtLeast(1)
        }
        val highPerformanceCap = calibratedPeakCap
        val sourceSuffix = if (calibrationOverlay.valid) {
            "local_device_calibration_overlay"
        } else {
            "safe_default_until_device_calibration"
        }
        return when (profileGroup) {
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED ->
                RuntimeProotAdaptiveProfileParams(
                    profileGroup = profileGroup,
                    profileSource = "builtin_default_balanced_from_lifecycle_profile:$sourceSuffix",
                    defaultLiveTraceeSoftCap = defaultSoftCap,
                    foregroundLiveTraceeSoftCap = defaultSoftCap,
                    backgroundLiveTraceeSoftCap = if (calibrationOverlay.valid) {
                        calibratedPeakCap
                    } else {
                        minOf((defaultSoftCap / 2).coerceAtLeast(4), 8)
                    },
                    hardStopLiveTracees = riskUpperBound,
                    cpuBusyTicksPerSecond = 80L,
                    ioBusyBytesPerSecond = 1024L * 1024L,
                    singleProotPeakTracees = calibratedPeakCap,
                    singleProotQueueUntilTracees = calibratedQueueUntilCap,
                    secondProotTriggerTracees = calibratedSecondProotTrigger,
                    overflowHeadroomTracees = calibratedOverflowHeadroom,
                    singleProotOverflowPercent = calibrationOverlay.singleProotOverflowPercent,
                    overflowPercentBase = calibrationOverlay.overflowPercentBase,
                    queueHeadroomPercent = calibrationOverlay.queueHeadroomPercent,
                    secondProotTriggerHeadroomPercent = calibrationOverlay.secondProotTriggerHeadroomPercent,
                    queueStrategyPercentBase = calibrationOverlay.queueStrategyPercentBase,
                    lowPowerProfileLimit = lowPowerCap,
                    balancedProfileLimit = defaultSoftCap,
                    highPerformanceProfileLimit = highPerformanceCap,
                    lowPriorityBackgroundPolicy = "foreground_only_when_healthy_under_cap",
                    queuePolicy = "android_owned_priority_then_declaration_order",
                    uiProtectionRule = "kf_foreground_performance_user_foreground_protection_when_hidden"
                )
            RuntimeLifecyclePolicyProfileGroup.LOW_POWER ->
                RuntimeProotAdaptiveProfileParams(
                    profileGroup = profileGroup,
                    profileSource = "builtin_low_power_from_lifecycle_profile:$sourceSuffix",
                    defaultLiveTraceeSoftCap = if (calibrationOverlay.valid) defaultSoftCap else lowPowerCap,
                    foregroundLiveTraceeSoftCap = if (calibrationOverlay.valid) defaultSoftCap else lowPowerCap,
                    backgroundLiveTraceeSoftCap = if (calibrationOverlay.valid) {
                        calibratedPeakCap
                    } else {
                        minOf((lowPowerCap / 2).coerceAtLeast(2), lowPowerCap)
                    },
                    hardStopLiveTracees = if (calibrationOverlay.valid) riskUpperBound else minOf(riskUpperBound, 18),
                    cpuBusyTicksPerSecond = 40L,
                    ioBusyBytesPerSecond = 512L * 1024L,
                    singleProotPeakTracees = calibratedPeakCap,
                    singleProotQueueUntilTracees = calibratedQueueUntilCap,
                    secondProotTriggerTracees = calibratedSecondProotTrigger,
                    overflowHeadroomTracees = calibratedOverflowHeadroom,
                    singleProotOverflowPercent = calibrationOverlay.singleProotOverflowPercent,
                    overflowPercentBase = calibrationOverlay.overflowPercentBase,
                    queueHeadroomPercent = calibrationOverlay.queueHeadroomPercent,
                    secondProotTriggerHeadroomPercent = calibrationOverlay.secondProotTriggerHeadroomPercent,
                    queueStrategyPercentBase = calibrationOverlay.queueStrategyPercentBase,
                    lowPowerProfileLimit = lowPowerCap,
                    balancedProfileLimit = defaultSoftCap,
                    highPerformanceProfileLimit = highPerformanceCap,
                    lowPriorityBackgroundPolicy = "disabled",
                    queuePolicy = "android_owned_priority_then_declaration_order_low_power",
                    uiProtectionRule = "user_foreground_and_battery_first"
                )
            RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE ->
                RuntimeProotAdaptiveProfileParams(
                    profileGroup = profileGroup,
                    profileSource = "builtin_high_performance_from_lifecycle_profile:$sourceSuffix",
                    defaultLiveTraceeSoftCap = if (calibrationOverlay.valid) defaultSoftCap else highPerformanceCap,
                    foregroundLiveTraceeSoftCap = if (calibrationOverlay.valid) defaultSoftCap else highPerformanceCap,
                    backgroundLiveTraceeSoftCap = if (calibrationOverlay.valid) {
                        calibratedPeakCap
                    } else {
                        minOf((highPerformanceCap / 2).coerceAtLeast(4), highPerformanceCap)
                    },
                    hardStopLiveTracees = riskUpperBound,
                    cpuBusyTicksPerSecond = 120L,
                    ioBusyBytesPerSecond = 4L * 1024L * 1024L,
                    singleProotPeakTracees = calibratedPeakCap,
                    singleProotQueueUntilTracees = calibratedQueueUntilCap,
                    secondProotTriggerTracees = calibratedSecondProotTrigger,
                    overflowHeadroomTracees = calibratedOverflowHeadroom,
                    singleProotOverflowPercent = calibrationOverlay.singleProotOverflowPercent,
                    overflowPercentBase = calibrationOverlay.overflowPercentBase,
                    queueHeadroomPercent = calibrationOverlay.queueHeadroomPercent,
                    secondProotTriggerHeadroomPercent = calibrationOverlay.secondProotTriggerHeadroomPercent,
                    queueStrategyPercentBase = calibrationOverlay.queueStrategyPercentBase,
                    lowPowerProfileLimit = lowPowerCap,
                    balancedProfileLimit = defaultSoftCap,
                    highPerformanceProfileLimit = highPerformanceCap,
                    lowPriorityBackgroundPolicy = "short_foreground_bursts_when_healthy_under_cap",
                    queuePolicy = "android_owned_priority_then_declaration_order_high_throughput",
                    uiProtectionRule = "kf_foreground_throughput_with_background_user_guard"
                )
            RuntimeLifecyclePolicyProfileGroup.CUSTOM ->
                RuntimeProotAdaptiveProfileParams(
                    profileGroup = profileGroup,
                    profileSource = "custom_profile_safe_default_until_custom_proot_policy:$sourceSuffix",
                    defaultLiveTraceeSoftCap = defaultSoftCap,
                    foregroundLiveTraceeSoftCap = defaultSoftCap,
                    backgroundLiveTraceeSoftCap = if (calibrationOverlay.valid) {
                        calibratedPeakCap
                    } else {
                        minOf((defaultSoftCap / 2).coerceAtLeast(4), 8)
                    },
                    hardStopLiveTracees = riskUpperBound,
                    cpuBusyTicksPerSecond = 80L,
                    ioBusyBytesPerSecond = 1024L * 1024L,
                    singleProotPeakTracees = calibratedPeakCap,
                    singleProotQueueUntilTracees = calibratedQueueUntilCap,
                    secondProotTriggerTracees = calibratedSecondProotTrigger,
                    overflowHeadroomTracees = calibratedOverflowHeadroom,
                    singleProotOverflowPercent = calibrationOverlay.singleProotOverflowPercent,
                    overflowPercentBase = calibrationOverlay.overflowPercentBase,
                    queueHeadroomPercent = calibrationOverlay.queueHeadroomPercent,
                    secondProotTriggerHeadroomPercent = calibrationOverlay.secondProotTriggerHeadroomPercent,
                    queueStrategyPercentBase = calibrationOverlay.queueStrategyPercentBase,
                    lowPowerProfileLimit = lowPowerCap,
                    balancedProfileLimit = defaultSoftCap,
                    highPerformanceProfileLimit = highPerformanceCap,
                    lowPriorityBackgroundPolicy = "foreground_only_when_healthy_under_cap",
                    queuePolicy = "android_owned_priority_then_declaration_order_custom_safe_fallback",
                    uiProtectionRule = "custom_profile_android_execution_owner_safe_fallback"
                )
        }
    }

    private fun buildCpuAxisSignal(
        trendStatus: String,
        ticksPerSecond: Long,
        pressureThresholdTicksPerSecond: Long
    ): String {
        if (trendStatus != "rate_window_valid") {
            return trendStatus
        }
        return when {
            ticksPerSecond >= pressureThresholdTicksPerSecond -> "cpu_rate_pressure"
            ticksPerSecond > 0L -> "cpu_rate_observed"
            else -> "cpu_rate_quiet"
        }
    }

    private fun buildIoAxisSignal(
        trendStatus: String,
        bytesPerSecond: Long,
        pressureThresholdBytesPerSecond: Long
    ): String {
        if (trendStatus != "rate_window_valid") {
            return trendStatus
        }
        return when {
            bytesPerSecond >= pressureThresholdBytesPerSecond -> "io_rate_pressure"
            bytesPerSecond > 0L -> "io_rate_observed"
            else -> "io_rate_quiet"
        }
    }

    private fun buildResourceEquation(
        substrateHealthy: Boolean,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        adaptivePolicy: RuntimeProotAdaptivePolicy,
        maxPoolSlots: Int,
        activeWorkloadCount: Int
    ): RuntimeProotResourceEquation {
        val effectiveLiveCap = adaptivePolicy.effectiveLiveTraceeSoftCap.coerceAtLeast(1)
        val liveTraceeRatio = ratioPercent(
            numerator = pressureConsumer.liveTraceeCount.toLong(),
            denominator = effectiveLiveCap.toLong()
        )
        val cpuRatio = if (pressureConsumer.resourceTrendStatus == "rate_window_valid") {
            ratioPercent(
                numerator = pressureConsumer.rootCpuTicksPerSecond,
                denominator = adaptivePolicy.cpuBusyTicksPerSecond.coerceAtLeast(1L)
            )
        } else {
            0
        }
        val ioRatio = if (pressureConsumer.resourceTrendStatus == "rate_window_valid") {
            ratioPercent(
                numerator = pressureConsumer.rootIoBytesPerSecond,
                denominator = adaptivePolicy.ioBusyBytesPerSecond.coerceAtLeast(1L)
            )
        } else {
            0
        }
        val memoryRatio = when (pressureConsumer.rssPressureLevel) {
            RuntimePressureLevel.UNKNOWN -> 0
            RuntimePressureLevel.NORMAL -> 0
            RuntimePressureLevel.ELEVATED -> 60
            RuntimePressureLevel.HIGH -> 85
            RuntimePressureLevel.CRITICAL -> 100
        }
        val budgetRatio = when (budgetPressure.overallState) {
            RuntimeBudgetState.HEALTHY -> 0
            RuntimeBudgetState.NEAR_BUDGET -> 65
            RuntimeBudgetState.SOFT_PRESSURE -> 85
            RuntimeBudgetState.HARD_PRESSURE,
            RuntimeBudgetState.THREATENING_KF,
            RuntimeBudgetState.REPEAT_OFFENDER,
            RuntimeBudgetState.QUARANTINED -> 100
        }
        val pressureRatio = pressureConsumer.prootPressureScore.coerceIn(0, 100)
        val cpuAxisStatus = resolveResourceEquationCpuAxisStatus(pressureConsumer)
        val ioAxisStatus = resolveResourceEquationIoAxisStatus(pressureConsumer)
        val memoryAxisStatus = resolveResourceEquationMemoryAxisStatus(pressureConsumer)
        val blindAxisCount = listOf(cpuAxisStatus, ioAxisStatus, memoryAxisStatus)
            .count { it.startsWith("blind") || it.contains("not_container_internal") }
        val calibrationStatus = resolveResourceEquationCalibrationStatus(pressureConsumer)
        val nextCalibrationFocus = resolveResourceEquationNextCalibrationFocus(
            pressureConsumer = pressureConsumer,
            liveTraceeRatio = liveTraceeRatio,
            cpuRatio = cpuRatio,
            ioRatio = ioRatio,
            memoryRatio = memoryRatio,
            budgetRatio = budgetRatio
        )
        val calibrationGate = resolveResourceEquationCalibrationGate(
            substrateHealthy = substrateHealthy,
            liveTraceeRatio = liveTraceeRatio,
            blindAxisCount = blindAxisCount,
            cpuRatio = cpuRatio,
            ioRatio = ioRatio,
            memoryRatio = memoryRatio
        )
        val observedAxes = buildString {
            append("proot_tracee+${pressureConsumer.resourceMetricSource}+rss_memory")
            if (pressureConsumer.rootCpuTimeTicks > 0L) append("+cpu_ticks")
            if (pressureConsumer.rootIoReadBytes > 0L || pressureConsumer.rootIoWriteBytes > 0L) {
                append("+io_bytes")
            }
        }
        val axisCoverage = "observed:$observedAxes+budget+proot_score," +
            "intended:live_tracee+cpu_rate+io_rate+memory+budget"
        val axisContract = "ratio_each_axis_to_cap_use_max_axis_to_shape_parallelism_and_queue"
        val proportionalPolicy = "risk=max_axis_ratio,parallel_slots=step_down_by_risk," +
            "queue_depth=axis_overflow_or_background_policy"
        if (!substrateHealthy) {
            return RuntimeProotResourceEquation(
                mode = "proot_resource_equation_v0",
                model = "max_axis_ratio_guard_band_v0",
                axisCoverage = axisCoverage + "+telemetry_guard",
                axisContract = axisContract,
                cpuAxisStatus = cpuAxisStatus,
                ioAxisStatus = ioAxisStatus,
                memoryAxisStatus = memoryAxisStatus,
                blindAxisCount = blindAxisCount,
                calibrationGate = calibrationGate,
                liveTraceeRatioPercent = liveTraceeRatio,
                cpuRatioPercent = cpuRatio,
                ioRatioPercent = ioRatio,
                memoryRatioPercent = memoryRatio,
                budgetRatioPercent = budgetRatio,
                pressureScorePercent = pressureRatio,
                riskPercent = 100,
                headroomPercent = 0,
                bottleneckAxis = "telemetry_substrate",
                expansionAllowed = false,
                lowPriorityQueueRequired = true,
                targetParallelSlots = activeWorkloadCount.coerceAtLeast(0),
                targetQueueDepth = 1,
                decision = "HOLD_EXPANSION",
                liveTraceeRaw = pressureConsumer.liveTraceeCount,
                liveTraceeCap = effectiveLiveCap,
                cpuRawTicksPerSecond = pressureConsumer.rootCpuTicksPerSecond,
                cpuCapTicksPerSecond = adaptivePolicy.cpuBusyTicksPerSecond,
                ioRawBytesPerSecond = pressureConsumer.rootIoBytesPerSecond,
                ioCapBytesPerSecond = adaptivePolicy.ioBusyBytesPerSecond,
                memoryRawLevel = pressureConsumer.rssPressureLevel,
                memoryCapLevel = RuntimePressureLevel.HIGH,
                budgetRawState = budgetPressure.overallState,
                budgetCapState = RuntimeBudgetState.SOFT_PRESSURE,
                pressureScoreRaw = pressureConsumer.prootPressureScore,
                pressureScoreCap = 100,
                proportionalPolicy = proportionalPolicy,
                calibrationStatus = calibrationStatus,
                nextCalibrationFocus = nextCalibrationFocus,
                reason = "telemetry_substrate_unhealthy_ratio_function_disabled"
            )
        }
        val axisRatios = listOf(
            "live_tracee" to liveTraceeRatio,
            "cpu_rate" to cpuRatio,
            "io_rate" to ioRatio,
            "rss_memory" to memoryRatio,
            "budget" to budgetRatio,
            "proot_score" to pressureRatio
        )
        val bottleneck = axisRatios.maxWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
        val risk = bottleneck.second.coerceIn(0, 100)
        val headroom = (100 - risk).coerceIn(0, 100)
        val backgroundHidden = backgroundDecay.lifecycleState != RuntimeAppVisibilityState.FOREGROUND
        val targetParallelSlots = resolveResourceEquationTargetSlots(
            riskPercent = risk,
            maxPoolSlots = maxPoolSlots,
            activeWorkloadCount = activeWorkloadCount,
            backgroundHidden = backgroundHidden
        )
        val targetQueueDepth = when {
            risk >= 100 -> maxOf(1, pressureConsumer.liveTraceeCount - effectiveLiveCap + 1)
            risk >= 80 -> maxOf(1, pressureConsumer.liveTraceeCount - effectiveLiveCap + 1)
            risk >= 65 -> 1
            backgroundHidden && !adaptivePolicy.lowPriorityBackgroundAllowed -> 1
            else -> 0
        }
        val expansionAllowed = risk < 65 &&
            !backgroundHidden &&
            adaptivePolicy.lowPriorityBackgroundAllowed &&
            pressureConsumer.liveTraceeCount < effectiveLiveCap
        val lowPriorityQueueRequired = targetQueueDepth > 0 ||
            !adaptivePolicy.lowPriorityBackgroundAllowed ||
            backgroundHidden ||
            risk >= 65
        val decision = when {
            risk >= 100 -> "STOP_NEW_WORK"
            risk >= 80 -> "HOLD_EXPANSION_QUEUE_LOW_PRIORITY"
            risk >= 65 -> "QUEUE_LOW_PRIORITY_KEEP_HEADROOM"
            backgroundHidden -> "BACKGROUND_QUEUE_LOW_PRIORITY"
            expansionAllowed -> "ALLOW_PARALLEL_UNTIL_SOFT_CAP"
            else -> "KEEP_HEADROOM_OBSERVE"
        }
        return RuntimeProotResourceEquation(
            mode = "proot_resource_equation_v0",
            model = "max_axis_ratio_guard_band_v0",
            axisCoverage = axisCoverage,
            axisContract = axisContract,
            cpuAxisStatus = cpuAxisStatus,
            ioAxisStatus = ioAxisStatus,
            memoryAxisStatus = memoryAxisStatus,
            blindAxisCount = blindAxisCount,
            calibrationGate = calibrationGate,
            liveTraceeRatioPercent = liveTraceeRatio,
            cpuRatioPercent = cpuRatio,
            ioRatioPercent = ioRatio,
            memoryRatioPercent = memoryRatio,
            budgetRatioPercent = budgetRatio,
            pressureScorePercent = pressureRatio,
            riskPercent = risk,
            headroomPercent = headroom,
            bottleneckAxis = bottleneck.first,
            expansionAllowed = expansionAllowed,
            lowPriorityQueueRequired = lowPriorityQueueRequired,
            targetParallelSlots = targetParallelSlots,
            targetQueueDepth = targetQueueDepth,
            decision = decision,
            liveTraceeRaw = pressureConsumer.liveTraceeCount,
            liveTraceeCap = effectiveLiveCap,
            cpuRawTicksPerSecond = pressureConsumer.rootCpuTicksPerSecond,
            cpuCapTicksPerSecond = adaptivePolicy.cpuBusyTicksPerSecond,
            ioRawBytesPerSecond = pressureConsumer.rootIoBytesPerSecond,
            ioCapBytesPerSecond = adaptivePolicy.ioBusyBytesPerSecond,
            memoryRawLevel = pressureConsumer.rssPressureLevel,
            memoryCapLevel = RuntimePressureLevel.HIGH,
            budgetRawState = budgetPressure.overallState,
            budgetCapState = RuntimeBudgetState.SOFT_PRESSURE,
            pressureScoreRaw = pressureConsumer.prootPressureScore,
            pressureScoreCap = 100,
            proportionalPolicy = proportionalPolicy,
            calibrationStatus = calibrationStatus,
            nextCalibrationFocus = nextCalibrationFocus,
            reason = "risk=$risk,bottleneck=${bottleneck.first},live=$liveTraceeRatio," +
                "cpu=$cpuRatio,io=$ioRatio,mem=$memoryRatio,budget=$budgetRatio," +
                "score=$pressureRatio,targetSlots=$targetParallelSlots,queueDepth=$targetQueueDepth"
        )
    }

    private fun resolveResourceEquationCalibrationStatus(
        pressureConsumer: RuntimePressureConsumerSnapshot
    ): String {
        val rateValid = pressureConsumer.resourceTrendStatus == "rate_window_valid"
        val hasCpu = pressureConsumer.rootCpuTimeTicks > 0L
        val hasIo = pressureConsumer.rootIoReadBytes > 0L || pressureConsumer.rootIoWriteBytes > 0L
        return when {
            !rateValid -> "needs_rate_window"
            hasCpu && hasIo -> "all_primary_axes_observable"
            hasCpu -> "cpu_axis_observable_io_axis_pending"
            hasIo -> "io_axis_observable_cpu_axis_pending"
            else -> "tracee_memory_budget_only_cpu_io_pending"
        }
    }

    private fun resolveResourceEquationCpuAxisStatus(
        pressureConsumer: RuntimePressureConsumerSnapshot
    ): String {
        val hasCounter = pressureConsumer.rootCpuTimeTicks > 0L
        val source = pressureConsumer.resourceMetricSource
        return when {
            hasCounter && source.startsWith("process_snapshot:") &&
                pressureConsumer.resourceTrendStatus == "rate_window_valid" ->
                "proot_process_snapshot_rate_window_valid_cpu"
            hasCounter && source.startsWith("process_snapshot:") ->
                "proot_process_snapshot_collecting_rate_window_cpu"
            hasCounter && pressureConsumer.resourceTrendStatus == "rate_window_valid" ->
                "android_root_process_rate_window_valid_not_verified_container_internal_cpu"
            hasCounter -> "android_root_process_collecting_rate_window_not_verified_container_internal_cpu"
            else -> "blind_current_substrate_not_container_internal_cpu"
        }
    }

    private fun resolveResourceEquationIoAxisStatus(
        pressureConsumer: RuntimePressureConsumerSnapshot
    ): String {
        val hasCounter = pressureConsumer.rootIoReadBytes > 0L || pressureConsumer.rootIoWriteBytes > 0L
        val source = pressureConsumer.resourceMetricSource
        return when {
            hasCounter && source.startsWith("process_snapshot:") &&
                pressureConsumer.resourceTrendStatus == "rate_window_valid" ->
                "proot_process_snapshot_rate_window_valid_io"
            hasCounter && source.startsWith("process_snapshot:") ->
                "proot_process_snapshot_collecting_rate_window_io"
            hasCounter && pressureConsumer.resourceTrendStatus == "rate_window_valid" ->
                "android_root_process_rate_window_valid_not_verified_container_internal_io"
            hasCounter -> "android_root_process_collecting_rate_window_not_verified_container_internal_io"
            else -> "blind_current_substrate_not_container_internal_io"
        }
    }

    private fun resolveResourceEquationMemoryAxisStatus(
        pressureConsumer: RuntimePressureConsumerSnapshot
    ): String {
        return when (pressureConsumer.rssPressureLevel) {
            RuntimePressureLevel.UNKNOWN -> "blind_no_memory_signal"
            else -> if (pressureConsumer.processResourceRssKb > 0L) {
                "proot_process_snapshot_rss_observed_memory"
            } else {
                "android_rss_memory_only_not_container_internal_memory"
            }
        }
    }

    private fun resolveResourceEquationCalibrationGate(
        substrateHealthy: Boolean,
        liveTraceeRatio: Int,
        blindAxisCount: Int,
        cpuRatio: Int,
        ioRatio: Int,
        memoryRatio: Int
    ): String {
        return when {
            !substrateHealthy -> "wait_for_healthy_telemetry_substrate"
            liveTraceeRatio >= 80 && cpuRatio == 0 && ioRatio == 0 && memoryRatio == 0 ->
                "live_tracee_bottleneck_masks_or_current_probe_not_attributed_to_resource_axes"
            blindAxisCount >= 2 && liveTraceeRatio >= 80 ->
                "live_tracee_bottleneck_masks_cpu_io_memory_until_internal_resource_substrate"
            blindAxisCount >= 2 ->
                "need_proot_internal_resource_substrate_before_cpu_io_memory_ratio"
            cpuRatio >= 65 || ioRatio >= 65 || memoryRatio >= 65 ->
                "resource_axis_observed_can_tune_parallelism"
            else -> "resource_axes_observable_collect_more_samples"
        }
    }

    private fun resolveResourceEquationNextCalibrationFocus(
        pressureConsumer: RuntimePressureConsumerSnapshot,
        liveTraceeRatio: Int,
        cpuRatio: Int,
        ioRatio: Int,
        memoryRatio: Int,
        budgetRatio: Int
    ): String {
        return when {
            pressureConsumer.resourceTrendStatus != "rate_window_valid" ->
                "wait_for_two_rate_windows_before_active_probe"
            liveTraceeRatio >= 80 && cpuRatio == 0 && ioRatio == 0 && memoryRatio == 0 ->
                "extend_proot_internal_resource_substrate_then_retest_cpu_io_memory_axes"
            cpuRatio == 0 && ioRatio == 0 && memoryRatio == 0 ->
                "collect_container_internal_cpu_io_memory_before_tuning_non_tracee_axes"
            cpuRatio >= 65 -> "cpu_axis_calibration_reduce_parallelism_or_queue_cpu_heavy_work"
            ioRatio >= 65 -> "io_axis_calibration_queue_io_heavy_work"
            memoryRatio >= 65 -> "memory_axis_calibration_hold_expansion_before_lmk_pressure"
            budgetRatio >= 65 -> "budget_axis_calibration_queue_low_priority_until_budget_recovers"
            else -> "collect_passive_foreground_background_samples"
        }
    }

    private fun buildProbeProtocol(
        substrateHealthy: Boolean,
        highPressure: Boolean,
        stable: Boolean,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        declaredProbeTargetLiveTracees: Int
    ): ProbeProtocol {
        val transientEvents = pressureConsumer.eventsInWindow + pressureConsumer.forkExecEventsInWindow
        val budgetOk = budgetPressure.overallState.severity() <=
            RuntimeBudgetState.NEAR_BUDGET.severity()
        val cleanZeroBaseline = substrateHealthy &&
            pressureConsumer.liveTraceeCount == 0 &&
            pressureConsumer.prootPressureScore == 0 &&
            transientEvents == 0 &&
            budgetOk
        val declaredTarget = if (declaredProbeTargetLiveTracees > 0) {
            declaredProbeTargetLiveTracees
        } else {
            nextProbeTarget(pressureConsumer.liveTraceeCount)
        }
        if (!substrateHealthy) {
            return ProbeProtocol(
                phase = "BLOCKED_TELEMETRY",
                declaredTarget = declaredTarget,
                baselineSatisfied = false,
                sampleValid = false,
                sampleValidity = "telemetry_substrate_not_clean"
            )
        }
        if (cleanZeroBaseline) {
            return ProbeProtocol(
                phase = "PLAN_DECLARED",
                declaredTarget = declaredTarget,
                baselineSatisfied = true,
                sampleValid = false,
                sampleValidity = "preflight_declared_no_sample_yet"
            )
        }
        if (pressureConsumer.liveTraceeCount > 0 && transientEvents > 0) {
            return ProbeProtocol(
                phase = "SAMPLE_CONTAMINATED_TRANSIENT_EVENTS",
                declaredTarget = declaredTarget,
                baselineSatisfied = false,
                sampleValid = false,
                sampleValidity = "fork_exec_or_event_window_not_zero_wait_before_conclusion"
            )
        }
        if (pressureConsumer.liveTraceeCount > 0 && highPressure) {
            return ProbeProtocol(
                phase = "SAMPLE_FAILED_UPPER_BOUND",
                declaredTarget = declaredTarget,
                baselineSatisfied = false,
                sampleValid = true,
                sampleValidity = "valid_pure_live_tracee_sample_mark_upper_bound"
            )
        }
        if (pressureConsumer.liveTraceeCount > 0 && stable) {
            return ProbeProtocol(
                phase = "SAMPLE_PASSED_LOWER_BOUND",
                declaredTarget = declaredTarget,
                baselineSatisfied = false,
                sampleValid = true,
                sampleValidity = "valid_pure_live_tracee_sample_mark_lower_bound"
            )
        }
        return ProbeProtocol(
            phase = "OBSERVE_BASELINE",
            declaredTarget = declaredTarget,
            baselineSatisfied = false,
            sampleValid = false,
            sampleValidity = "baseline_or_sample_not_ready"
        )
    }

    private fun nextProbeTarget(liveTraceeCount: Int): Int {
        return (liveTraceeCount + 1).coerceIn(1, 256)
    }

    private fun scaleOutThreshold(peakTasks: Int): Int {
        val peak = peakTasks.coerceAtLeast(1)
        return (((peak * 125) + 99) / 100).coerceAtLeast(peak + 1)
    }

    private fun buildCapacityRequest(
        policySubstrateUsable: Boolean,
        taskPressure: RuntimeProotTaskPressure
    ): RuntimeProotCapacityRequest {
        val expansionRequested = policySubstrateUsable &&
            taskPressure.action == RuntimeProotTaskPressureAction.REQUEST_NEXT_PROOT
        val queueRequested = policySubstrateUsable &&
            (taskPressure.action == RuntimeProotTaskPressureAction.QUEUE_ON_CURRENT_PROOT ||
                taskPressure.action == RuntimeProotTaskPressureAction.HOLD_FOR_MEMORY)
        val downscaleRequested = policySubstrateUsable &&
            !expansionRequested &&
            taskPressure.action == RuntimeProotTaskPressureAction.REVIEW_DOWNLINE
        val requestedAction = when {
            expansionRequested -> "REQUEST_NEXT_PROOT"
            queueRequested -> "QUEUE_ON_CURRENT_PROOT"
            downscaleRequested -> "REQUEST_PROOT_DOWNSCALE"
            else -> "NONE"
        }
        val reason = when {
            policySubstrateUsable -> taskPressure.reason
            else -> "proot_capacity_request_waiting_for_substrate"
        }
        return RuntimeProotCapacityRequest(
            contract = "task_pressure_health_requests_capacity_lifecycle_reviews_budget_android_executes",
            requestedAction = requestedAction,
            reviewNeeded = requestedAction != "NONE",
            expansionRequested = expansionRequested,
            queueRequested = queueRequested,
            downscaleRequested = downscaleRequested,
            downlineRequested = downscaleRequested,
            reason = reason
        )
    }

    private fun buildTaskPressure(
        policySubstrateUsable: Boolean,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot,
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        adaptivePolicy: RuntimeProotAdaptivePolicy,
        backgroundShrinkLaneCount: Int,
        idleReclaimCandidateCount: Int
    ): RuntimeProotTaskPressure {
        if (!policySubstrateUsable) {
            return RuntimeProotTaskPressure(
                mode = "proot_task_pressure_health_v1",
                contract = "single_entry_queue_and_telemetry_pressure_before_capacity_action",
                tier = RuntimeProotTaskPressureTier.NO_SOURCE,
                action = RuntimeProotTaskPressureAction.WAIT_FOR_TELEMETRY,
                reason = "waiting_for_proot_telemetry_substrate",
                primarySignal = "telemetry_substrate_missing",
                profileGroup = adaptivePolicy.profileGroup,
                activeWorkloads = workloadRegistry.totalWorkloads,
                runningRoots = pressureConsumer.runningRootCount,
                queueCount = 0,
                backlogCount = startQueuePlan.dryRunBacklogCount,
                noCapacityCount = startQueuePlan.blockedNoCapacityCount + laneAdmission.noCapacityLaneCount,
                deferredCount = startQueuePlan.deferUntilPressureEasesCount + laneAdmission.suggestedDeferCount,
                saturatedLaneCount = laneAdmission.saturatedLaneCount,
                overEffectiveMaxWorkloads = laneAdmission.lanes.sumOf {
                    (it.activeWorkloads - it.effectiveMaxConcurrency).coerceAtLeast(0)
                },
                idleDownlineCandidateCount = backgroundShrinkLaneCount + idleReclaimCandidateCount,
                telemetryHealthy = pressureConsumer.telemetryHealthy,
                telemetryState = pressureConsumer.state,
                telemetrySignal = pressureConsumer.prootSignalLevel,
                telemetryScore = pressureConsumer.prootPressureScore,
                latencySignalStatus = "not_wired",
                peakTasksRole = "memory_estimate_and_fallback_only_not_scale_trigger"
            )
        }

        val queueCount = startQueuePlan.wouldQueueCount +
            startQueuePlan.blockedNoCapacityCount +
            laneAdmission.suggestedQueueCount
        val backlogCount = startQueuePlan.dryRunBacklogCount
        val noCapacityCount = startQueuePlan.blockedNoCapacityCount + laneAdmission.noCapacityLaneCount
        val deferredCount = startQueuePlan.deferUntilPressureEasesCount + laneAdmission.suggestedDeferCount
        val saturatedLaneCount = laneAdmission.saturatedLaneCount
        val overEffectiveMax = laneAdmission.lanes.sumOf {
            (it.activeWorkloads - it.effectiveMaxConcurrency).coerceAtLeast(0)
        }
        val idleDownlineCandidates = backgroundShrinkLaneCount + idleReclaimCandidateCount
        val pressureState = pressureConsumer.state
        val budgetSeverity = budgetPressure.overallState.severity()
        val hardMemoryHold = budgetSeverity >= RuntimeBudgetState.HARD_PRESSURE.severity() ||
            pressureConsumer.rssPressureLevel == RuntimePressureLevel.CRITICAL
        val memoryConstrained = budgetSeverity >= RuntimeBudgetState.SOFT_PRESSURE.severity() ||
            pressureConsumer.rssPressureLevel == RuntimePressureLevel.HIGH ||
            pressureConsumer.rssPressureLevel == RuntimePressureLevel.CRITICAL
        val hasQueuePressure = queueCount > 0 || backlogCount > 0 || deferredCount > 0
        val hasCapacityPressure = noCapacityCount > 0 || saturatedLaneCount > 0 || overEffectiveMax > 0
        val hasTelemetryPressure = pressureState == RuntimePressureConsumerState.BUSY ||
            pressureState == RuntimePressureConsumerState.BURST ||
            pressureConsumer.prootSignalLevel == ProotPressureSignalLevel.BUSY ||
            pressureConsumer.prootSignalLevel == ProotPressureSignalLevel.BURST

        val tier = when {
            pressureState == RuntimePressureConsumerState.DEGRADED || hardMemoryHold ->
                RuntimeProotTaskPressureTier.ABNORMAL
            pressureState == RuntimePressureConsumerState.BURST ||
                memoryConstrained ||
                noCapacityCount > 0 ->
                RuntimeProotTaskPressureTier.HIGH_PRESSURE
            hasCapacityPressure || hasQueuePressure || pressureState == RuntimePressureConsumerState.BUSY ->
                RuntimeProotTaskPressureTier.MODERATE_PRESSURE
            pressureConsumer.prootPressureScore > 0 || hasTelemetryPressure || workloadRegistry.totalWorkloads > 0 ->
                RuntimeProotTaskPressureTier.LIGHT_PRESSURE
            pressureConsumer.liveTraceeCount <= 0 && workloadRegistry.totalWorkloads <= 0 ->
                RuntimeProotTaskPressureTier.IDLE
            else -> RuntimeProotTaskPressureTier.HEALTHY
        }

        val primarySignal = when {
            hardMemoryHold -> "hard_memory_or_budget_pressure"
            noCapacityCount > 0 -> "no_capacity"
            hasCapacityPressure -> "lane_capacity_pressure"
            hasQueuePressure -> "queue_or_backlog"
            memoryConstrained -> "memory_or_budget_pressure"
            hasTelemetryPressure -> "proot_telemetry_pressure"
            idleDownlineCandidates > 0 -> "idle_downline_candidate"
            workloadRegistry.totalWorkloads > 0 -> "active_workload"
            else -> "idle"
        }

        val action = when (tier) {
            RuntimeProotTaskPressureTier.NO_SOURCE ->
                RuntimeProotTaskPressureAction.WAIT_FOR_TELEMETRY
            RuntimeProotTaskPressureTier.ABNORMAL ->
                RuntimeProotTaskPressureAction.HOLD_FOR_MEMORY
            RuntimeProotTaskPressureTier.HIGH_PRESSURE -> when {
                hardMemoryHold -> RuntimeProotTaskPressureAction.HOLD_FOR_MEMORY
                else -> RuntimeProotTaskPressureAction.REQUEST_NEXT_PROOT
            }
            RuntimeProotTaskPressureTier.MODERATE_PRESSURE -> when (adaptivePolicy.profileGroup) {
                RuntimeLifecyclePolicyProfileGroup.LOW_POWER ->
                    RuntimeProotTaskPressureAction.QUEUE_ON_CURRENT_PROOT
                RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
                RuntimeLifecyclePolicyProfileGroup.CUSTOM,
                RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE ->
                    RuntimeProotTaskPressureAction.REQUEST_NEXT_PROOT
            }
            RuntimeProotTaskPressureTier.LIGHT_PRESSURE -> when {
                adaptivePolicy.profileGroup == RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE &&
                    hasQueuePressure -> RuntimeProotTaskPressureAction.REQUEST_NEXT_PROOT
                hasQueuePressure -> RuntimeProotTaskPressureAction.QUEUE_ON_CURRENT_PROOT
                else -> RuntimeProotTaskPressureAction.KEEP_SINGLE_PROOT
            }
            RuntimeProotTaskPressureTier.IDLE,
            RuntimeProotTaskPressureTier.HEALTHY -> when {
                idleDownlineCandidates > 0 && !hasQueuePressure ->
                    RuntimeProotTaskPressureAction.REVIEW_DOWNLINE
                else -> RuntimeProotTaskPressureAction.KEEP_SINGLE_PROOT
            }
        }

        val reason = when (action) {
            RuntimeProotTaskPressureAction.WAIT_FOR_TELEMETRY ->
                "proot_task_pressure_waiting_for_telemetry"
            RuntimeProotTaskPressureAction.REQUEST_NEXT_PROOT ->
                "proot_task_pressure_${tier.name.lowercase()}_$primarySignal"
            RuntimeProotTaskPressureAction.QUEUE_ON_CURRENT_PROOT ->
                "proot_task_pressure_queue_on_current_${tier.name.lowercase()}_$primarySignal"
            RuntimeProotTaskPressureAction.HOLD_FOR_MEMORY ->
                "proot_task_pressure_hold_for_memory_$primarySignal"
            RuntimeProotTaskPressureAction.REVIEW_DOWNLINE ->
                "proot_task_pressure_idle_downline_candidate"
            RuntimeProotTaskPressureAction.KEEP_SINGLE_PROOT ->
                "proot_task_pressure_keep_current_$primarySignal"
        }

        return RuntimeProotTaskPressure(
            mode = "proot_task_pressure_health_v1",
            contract = "single_entry_queue_and_telemetry_pressure_before_capacity_action",
            tier = tier,
            action = action,
            reason = reason,
            primarySignal = primarySignal,
            profileGroup = adaptivePolicy.profileGroup,
            activeWorkloads = workloadRegistry.totalWorkloads,
            runningRoots = pressureConsumer.runningRootCount,
            queueCount = queueCount,
            backlogCount = backlogCount,
            noCapacityCount = noCapacityCount,
            deferredCount = deferredCount,
            saturatedLaneCount = saturatedLaneCount,
            overEffectiveMaxWorkloads = overEffectiveMax,
            idleDownlineCandidateCount = idleDownlineCandidates,
            telemetryHealthy = pressureConsumer.telemetryHealthy,
            telemetryState = pressureConsumer.state,
            telemetrySignal = pressureConsumer.prootSignalLevel,
            telemetryScore = pressureConsumer.prootPressureScore,
            latencySignalStatus = "not_wired",
            peakTasksRole = "memory_estimate_and_fallback_only_not_scale_trigger"
        )
    }

    private fun buildTuningPlan(
        substrateHealthy: Boolean,
        highPressure: Boolean,
        stable: Boolean,
        plannedPoolSlots: Int,
        effectivePoolSlots: Int
    ): TuningPlan {
        val candidate = maxOf(plannedPoolSlots, effectivePoolSlots, 1)
        if (!substrateHealthy) {
            return TuningPlan(
                status = "WAIT_FOR_CLEAN_TELEMETRY",
                candidate = candidate,
                nextIfPass = candidate,
                nextIfFail = (candidate / 2).coerceAtLeast(1),
                lowerBound = 0,
                upperBound = candidate,
                gateReason = "telemetry_substrate_not_clean",
                canProbeNow = false
            )
        }
        if (highPressure) {
            return TuningPlan(
                status = "HOLD_AND_MARK_UPPER_BOUND",
                candidate = candidate,
                nextIfPass = candidate,
                nextIfFail = (candidate / 2).coerceAtLeast(1),
                lowerBound = 0,
                upperBound = candidate,
                gateReason = "pressure_or_budget_already_high_mark_candidate_as_upper_bound",
                canProbeNow = false
            )
        }
        if (stable) {
            return TuningPlan(
                status = "READY_TO_EXPONENTIAL_PROBE",
                candidate = candidate,
                nextIfPass = (candidate * 2).coerceAtLeast(1),
                nextIfFail = (candidate / 2).coerceAtLeast(1),
                lowerBound = candidate,
                upperBound = 0,
                gateReason = "stable_baseline_ready_for_next_probe",
                canProbeNow = true
            )
        }
        return TuningPlan(
            status = "OBSERVE_BASELINE",
            candidate = candidate,
            nextIfPass = candidate,
            nextIfFail = (candidate / 2).coerceAtLeast(1),
            lowerBound = 0,
            upperBound = 0,
            gateReason = "collecting_baseline_do_not_change_candidate_yet",
            canProbeNow = false
        )
    }

    private fun ratioPercent(numerator: Long, denominator: Long): Int {
        if (denominator <= 0L) return 100
        if (numerator <= 0L) return 0
        return ((numerator * 100L) / denominator)
            .coerceIn(0L, 200L)
            .toInt()
    }

    private fun resolveResourceEquationTargetSlots(
        riskPercent: Int,
        maxPoolSlots: Int,
        activeWorkloadCount: Int,
        backgroundHidden: Boolean
    ): Int {
        val maxSlots = maxPoolSlots.coerceAtLeast(0)
        val activeSlots = activeWorkloadCount.coerceAtLeast(0)
        return when {
            riskPercent >= 100 -> activeSlots.coerceAtMost(1)
            riskPercent >= 80 -> activeSlots.coerceAtMost(maxSlots).coerceAtLeast(1)
            riskPercent >= 65 -> minOf(maxSlots, maxOf(1, activeSlots))
            backgroundHidden -> minOf(maxSlots, maxOf(1, activeSlots))
            else -> maxSlots
        }.coerceAtLeast(0)
    }

    private fun attributePressure(
        eventsInWindow: Int,
        forkExecEventsInWindow: Int,
        liveTraceeCount: Int,
        prootPressureScore: Int,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ): String {
        val budgetHigh = budgetPressure.overallState.severity() >=
            RuntimeBudgetState.SOFT_PRESSURE.severity()
        return when {
            forkExecEventsInWindow >= 20 || eventsInWindow >= 40 ->
                "fork_exec_storm"
            liveTraceeCount >= 18 && forkExecEventsInWindow == 0 ->
                "live_tracee_accumulation"
            liveTraceeCount >= 18 ->
                "mixed_live_tracee_and_fork_exec_pressure"
            budgetHigh && prootPressureScore < 40 ->
                "budget_or_memory_pressure"
            prootPressureScore >= 80 ->
                "proot_signal_pressure"
            else -> "baseline_or_unknown"
        }
    }

    private fun RuntimeLaneAdmissionLane.toPoolLanePlan(
        substrateHealthy: Boolean,
        highPressure: Boolean,
        stable: Boolean,
        inForeground: Boolean,
        adaptivePolicy: RuntimeProotAdaptivePolicy
    ): RuntimeProotPoolLanePlan {
        val burstCeiling = if (allowBurst && inForeground && substrateHealthy) {
            maxConcurrency + 1
        } else {
            maxConcurrency
        }.coerceAtLeast(0)
        val actionAndSlots = resolveActionAndSlots(
            substrateHealthy = substrateHealthy,
            highPressure = highPressure,
            stable = stable,
            burstCeiling = burstCeiling,
            adaptivePolicy = adaptivePolicy
        )
        val plannedSlots = actionAndSlots.second.coerceAtLeast(0)
        return RuntimeProotPoolLanePlan(
            lane = lane,
            priority = priority,
            activeWorkloads = activeWorkloads,
            maxConcurrency = maxConcurrency,
            backgroundMaxConcurrency = backgroundMaxConcurrency,
            effectiveMaxConcurrency = effectiveMaxConcurrency,
            burstCeiling = burstCeiling,
            plannedPoolSlots = plannedSlots,
            spareSlots = (plannedSlots - activeWorkloads).coerceAtLeast(0),
            serial = serial,
            allowBurst = allowBurst,
            admissionState = state,
            action = actionAndSlots.first,
            reason = actionAndSlots.third
        )
    }

    private fun RuntimeLaneAdmissionLane.resolveActionAndSlots(
        substrateHealthy: Boolean,
        highPressure: Boolean,
        stable: Boolean,
        burstCeiling: Int,
        adaptivePolicy: RuntimeProotAdaptivePolicy
    ): Triple<RuntimeProotPoolLaneAction, Int, String> {
        if (!substrateHealthy) {
            return Triple(
                RuntimeProotPoolLaneAction.HOLD_EXPANSION,
                activeWorkloads,
                "substrate_unhealthy_hold_pool_expansion"
            )
        }
        if (effectiveMaxConcurrency <= 0) {
            return Triple(
                RuntimeProotPoolLaneAction.REVIEW_BACKGROUND_SHRINK,
                0,
                "effective_capacity_zero_new_background_admission_closed_existing_active_observed"
            )
        }
        if (state == RuntimeLaneAdmissionState.SATURATED || state == RuntimeLaneAdmissionState.NO_CAPACITY) {
            return Triple(
                RuntimeProotPoolLaneAction.HOLD_EXPANSION,
                activeWorkloads.coerceAtMost(effectiveMaxConcurrency),
                "lane_saturated_or_no_capacity_hold_expansion"
            )
        }
        if (
            priority >= RuntimeLaneKind.BUILD.defaultPriority() &&
            !adaptivePolicy.lowPriorityBackgroundAllowed &&
            adaptivePolicy.effectiveLiveTraceeSoftCap <= adaptivePolicy.backgroundLiveTraceeSoftCap
        ) {
            return Triple(
                RuntimeProotPoolLaneAction.HOLD_EXPANSION,
                activeWorkloads.coerceAtMost(effectiveMaxConcurrency),
                "adaptive_policy_queue_low_priority:${adaptivePolicy.status}:" +
                    adaptivePolicy.resourceLimiter
            )
        }
        if (
            priority >= RuntimeLaneKind.BUILD.defaultPriority() &&
            adaptivePolicy.status.endsWith("bounded_user_protection_active")
        ) {
            return Triple(
                RuntimeProotPoolLaneAction.REVIEW_BACKGROUND_SHRINK,
                activeWorkloads.coerceAtMost(backgroundMaxConcurrency),
                "adaptive_profile_background_user_protection_low_priority_lane"
            )
        }
        if (highPressure && allowBurst && priority < RuntimeLaneKind.BUILD.defaultPriority()) {
            return Triple(
                RuntimeProotPoolLaneAction.REVIEW_BURST_HEADROOM,
                maxOf(effectiveMaxConcurrency, (activeWorkloads + 1).coerceAtMost(burstCeiling)),
                "high_pressure_preserve_interactive_burst_headroom"
            )
        }
        if (highPressure) {
            return Triple(
                RuntimeProotPoolLaneAction.HOLD_EXPANSION,
                activeWorkloads.coerceAtMost(effectiveMaxConcurrency),
                "high_pressure_hold_non_burst_or_low_priority_expansion"
            )
        }
        if (stable && activeWorkloads == 0 && priority >= RuntimeLaneKind.BUILD.defaultPriority()) {
            return Triple(
                RuntimeProotPoolLaneAction.REVIEW_IDLE_RECLAIM,
                0,
                "stable_idle_low_priority_lane_can_release_pool_slot"
            )
        }
        return Triple(
            RuntimeProotPoolLaneAction.KEEP_HEADROOM,
            effectiveMaxConcurrency,
            "steady_pool_keep_effective_headroom"
        )
    }

    private fun resolveState(
        substrateHealthy: Boolean,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        lanes: List<RuntimeProotPoolLanePlan>
    ): RuntimeProotPoolState {
        if (!substrateHealthy) return RuntimeProotPoolState.SUBSTRATE_BLOCKED
        if (pressureConsumer.state == RuntimePressureConsumerState.NO_SOURCE ||
            pressureStability.state == RuntimePressureStabilityState.NO_SOURCE
        ) {
            return RuntimeProotPoolState.NO_SOURCE
        }
        if (lanes.any { it.action == RuntimeProotPoolLaneAction.REVIEW_BACKGROUND_SHRINK }) {
            return RuntimeProotPoolState.BACKGROUND_SHRINK_REVIEW
        }
        if (lanes.any { it.action == RuntimeProotPoolLaneAction.REVIEW_BURST_HEADROOM }) {
            return RuntimeProotPoolState.BURST_HEADROOM_REVIEW
        }
        if (pressureStability.state == RuntimePressureStabilityState.PRESSURE_HOLD ||
            lanes.any { it.action == RuntimeProotPoolLaneAction.HOLD_EXPANSION }
        ) {
            return RuntimeProotPoolState.PRESSURE_HOLD
        }
        if (lanes.any { it.action == RuntimeProotPoolLaneAction.REVIEW_IDLE_RECLAIM }) {
            return RuntimeProotPoolState.IDLE_RECLAIM_REVIEW
        }
        return RuntimeProotPoolState.STEADY_POOL
    }

    private fun recommendationFor(state: RuntimeProotPoolState): RuntimeProotPoolRecommendation {
        return when (state) {
            RuntimeProotPoolState.NO_SOURCE -> RuntimeProotPoolRecommendation.WAIT_FOR_PROOT_TELEMETRY
            RuntimeProotPoolState.SUBSTRATE_BLOCKED -> RuntimeProotPoolRecommendation.REPAIR_PROOT_TELEMETRY
            RuntimeProotPoolState.STEADY_POOL -> RuntimeProotPoolRecommendation.KEEP_POOL_OBSERVING
            RuntimeProotPoolState.PRESSURE_HOLD -> RuntimeProotPoolRecommendation.HOLD_POOL_EXPANSION
            RuntimeProotPoolState.BURST_HEADROOM_REVIEW ->
                RuntimeProotPoolRecommendation.REVIEW_BURST_HEADROOM
            RuntimeProotPoolState.BACKGROUND_SHRINK_REVIEW ->
                RuntimeProotPoolRecommendation.REVIEW_BACKGROUND_SHRINK
            RuntimeProotPoolState.IDLE_RECLAIM_REVIEW ->
                RuntimeProotPoolRecommendation.REVIEW_IDLE_RECLAIM
        }
    }

    private fun buildReason(
        state: RuntimeProotPoolState,
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        policySubstrate: RuntimeProotPolicySubstrate,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ): String {
        return "state=${state.name},telemetry=${prootTelemetryHealth.state.name}/" +
            "${prootTelemetryHealth.blocker},policySubstrate=${policySubstrate.policyUsable}/" +
            "${policySubstrate.policyReason},probeSubstrate=${policySubstrate.probeClean}/" +
            "${policySubstrate.probeReason},pressure=${pressureConsumer.state.name}/" +
            "${pressureConsumer.prootPressureScore},stability=${pressureStability.state.name}/" +
            "${pressureStability.blocker},budget=${budgetPressure.overallState.name}"
    }

    private fun RuntimePressureConsumerSnapshot.isHighSignal(): Boolean {
        return state == RuntimePressureConsumerState.BUSY ||
            state == RuntimePressureConsumerState.BURST ||
            state == RuntimePressureConsumerState.DEGRADED ||
            prootPressureScore >= 80 ||
            rssPressureLevel == RuntimePressureLevel.ELEVATED ||
            rssPressureLevel == RuntimePressureLevel.HIGH ||
            rssPressureLevel == RuntimePressureLevel.CRITICAL
    }

    private fun ProotTelemetryHealthDryRunSnapshot.toProotPolicySubstrate(): RuntimeProotPolicySubstrate {
        val parseClean = parseErrors == 0L && skippedBytes == 0L
        val readerFresh = (refreshedAgeMs ?: Long.MAX_VALUE) <= 60_000L
        val sourceLoaded = sourceStatus == "loaded" && fileExists
        if (blocker == "none" && parseClean && sourceLoaded) {
            return RuntimeProotPolicySubstrate(
                policyUsable = true,
                policyReason = "fresh_clean_telemetry_source",
                probeClean = true,
                probeReason = "fresh_clean_telemetry_source"
            )
        }
        if (blocker == "stale_source" && shadowUsable && sourceLoaded && parseClean && readerFresh) {
            return RuntimeProotPolicySubstrate(
                policyUsable = true,
                policyReason = "clean_stale_source_policy_usable_reader_fresh",
                probeClean = false,
                probeReason = "stale_source_wait_for_fresh_event_before_probe"
            )
        }
        return RuntimeProotPolicySubstrate(
            policyUsable = false,
            policyReason = "blocked_${blocker}_parseErrors_${parseErrors}_skippedBytes_${skippedBytes}",
            probeClean = false,
            probeReason = "blocked_${blocker}_requires_fresh_clean_probe_source"
        )
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

    private fun RuntimeLaneKind.defaultPriority(): Int {
        return RuntimeWorkloadPolicy.defaultLanes()
            .first { it.lane == this }
            .priority
    }
}

private fun String?.toProotPoolEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
