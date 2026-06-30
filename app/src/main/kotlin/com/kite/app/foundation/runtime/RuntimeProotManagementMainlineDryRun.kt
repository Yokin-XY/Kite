package com.kite.app.foundation.runtime

enum class RuntimeProotManagementMainlineState {
    SUBSTRATE_BLOCKED,
    TRACEE_RISK_HELD,
    READY_FOR_LIVE_TRACEE_PROBE,
    STEADY_OBSERVE
}

enum class RuntimeProotManagementMainlineRecommendation {
    REPAIR_TELEMETRY_SUBSTRATE,
    HOLD_EXPANSION_AT_KNOWN_RISK_SAMPLE,
    RUN_CLEAN_BASELINE_LIVE_TRACEE_PROBE,
    KEEP_DEFAULT_BALANCED_OBSERVING
}

data class RuntimeProotManagementMainlineDryRunSnapshot(
    val mode: String = "proot_management_mainline_observe_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeProotManagementMainlineState =
        RuntimeProotManagementMainlineState.SUBSTRATE_BLOCKED,
    val recommendation: RuntimeProotManagementMainlineRecommendation =
        RuntimeProotManagementMainlineRecommendation.REPAIR_TELEMETRY_SUBSTRATE,
    val systemRole: String = "proot_resource_pressure_pool_lane_queue",
    val sharedSubstrate: String = "proot_interception_telemetry",
    val selectedSystem: String = "proot_management",
    val lifecycleSystemPaused: Boolean = true,
    val defaultProfile: String = "DEFAULT_BALANCED",
    val defaultPolicyStatus: String = "bounded_17_pass_18_fail",
    val primaryRiskAxis: String = "live_tracee_accumulation",
    val substrateHealthy: Boolean = false,
    val policySubstrateReason: String = "waiting_for_telemetry_health",
    val probeSubstrateClean: Boolean = false,
    val probeSubstrateReason: String = "waiting_for_fresh_probe_source",
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val pressureStabilityState: RuntimePressureStabilityState = RuntimePressureStabilityState.NO_SOURCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val prootPressureScore: Int = 0,
    val liveTraceeCount: Int = 0,
    val forkExecEventsInWindow: Int = 0,
    val eventsInWindow: Int = 0,
    val knownRiskUpperBoundLiveTracees: Int = 18,
    val knownRiskUpperBoundPressureScore: Int = 36,
    val knownSafeLowerBoundLiveTracees: Int = 17,
    val recommendedDefaultLiveTraceeSoftCap: Int = 16,
    val knownSafeLowerBoundMeasured: Boolean = true,
    val cleanBaselineRequired: Boolean = true,
    val defaultLowPriorityBackgroundAllowed: Boolean = false,
    val defaultHoldExpansionEnabled: Boolean = true,
    val defaultPoolProbeAllowedNow: Boolean = false,
    val defaultCandidatePoolSlots: Int = 0,
    val adaptivePolicyStatus: String = "bounded_observe_only",
    val adaptiveUserContext: String = "kf_foreground_performance_bias",
    val adaptiveResourceLimiter: String = "live_tracee_accumulation",
    val adaptiveEffectiveLiveTraceeSoftCap: Int = 16,
    val adaptiveHardStopLiveTracees: Int = 18,
    val adaptiveConcurrencyPosture: String = "allow_until_effective_soft_cap",
    val adaptiveQueuePosture: String = "run_high_priority_queue_low_priority_when_over_cap",
    val nextExperiment: String = "clean_baseline_live_tracee_probe_only",
    val nextExperimentSequence: String = "N=4,8,16,32,64",
    val nextExperimentStopCondition: String =
        "ui_stutter_or_pressure_hold_or_budget_soft_pressure_or_crash",
    val reason: String = "waiting_for_proot_management_inputs"
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation role=$systemRole " +
            "profile=$defaultProfile risk=$primaryRiskAxis upper=$knownRiskUpperBoundLiveTracees " +
            "lowerMeasured=$knownSafeLowerBoundMeasured lowPriorityBg=$defaultLowPriorityBackgroundAllowed " +
            "holdExpansion=$defaultHoldExpansionEnabled enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("proot_management_mainline_mode=${mode.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_enforcement_mode=${enforcementMode.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_enforcement_enabled=$enforcementEnabled")
            appendLine("proot_management_mainline_generated_at=$generatedAtMs")
            appendLine("proot_management_mainline_state=${state.name}")
            appendLine("proot_management_mainline_recommendation=${recommendation.name}")
            appendLine("proot_management_mainline_system_role=${systemRole.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_shared_substrate=${sharedSubstrate.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_selected_system=${selectedSystem.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_lifecycle_system_paused=$lifecycleSystemPaused")
            appendLine("proot_management_mainline_default_profile=${defaultProfile.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_default_policy_status=${defaultPolicyStatus.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_primary_risk_axis=${primaryRiskAxis.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_substrate_healthy=$substrateHealthy")
            appendLine("proot_management_mainline_policy_substrate_reason=${policySubstrateReason.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_probe_substrate_clean=$probeSubstrateClean")
            appendLine("proot_management_mainline_probe_substrate_reason=${probeSubstrateReason.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_pressure_state=${pressureState.name}")
            appendLine("proot_management_mainline_pressure_stability_state=${pressureStabilityState.name}")
            appendLine("proot_management_mainline_budget_overall_state=${budgetOverallState.name}")
            appendLine("proot_management_mainline_proot_pressure_score=$prootPressureScore")
            appendLine("proot_management_mainline_live_tracee_count=$liveTraceeCount")
            appendLine("proot_management_mainline_fork_exec_events_window=$forkExecEventsInWindow")
            appendLine("proot_management_mainline_events_window=$eventsInWindow")
            appendLine("proot_management_mainline_known_risk_upper_bound_live_tracees=$knownRiskUpperBoundLiveTracees")
            appendLine("proot_management_mainline_known_risk_upper_bound_pressure_score=$knownRiskUpperBoundPressureScore")
            appendLine("proot_management_mainline_known_safe_lower_bound_live_tracees=$knownSafeLowerBoundLiveTracees")
            appendLine("proot_management_mainline_recommended_default_live_tracee_soft_cap=$recommendedDefaultLiveTraceeSoftCap")
            appendLine("proot_management_mainline_known_safe_lower_bound_measured=$knownSafeLowerBoundMeasured")
            appendLine("proot_management_mainline_clean_baseline_required=$cleanBaselineRequired")
            appendLine("proot_management_mainline_default_low_priority_background_allowed=$defaultLowPriorityBackgroundAllowed")
            appendLine("proot_management_mainline_default_hold_expansion_enabled=$defaultHoldExpansionEnabled")
            appendLine("proot_management_mainline_default_pool_probe_allowed_now=$defaultPoolProbeAllowedNow")
            appendLine("proot_management_mainline_default_candidate_pool_slots=$defaultCandidatePoolSlots")
            appendLine("proot_management_mainline_adaptive_policy_status=${adaptivePolicyStatus.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_adaptive_user_context=${adaptiveUserContext.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_adaptive_resource_limiter=${adaptiveResourceLimiter.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_adaptive_effective_live_tracee_soft_cap=$adaptiveEffectiveLiveTraceeSoftCap")
            appendLine("proot_management_mainline_adaptive_hard_stop_live_tracees=$adaptiveHardStopLiveTracees")
            appendLine("proot_management_mainline_adaptive_concurrency_posture=${adaptiveConcurrencyPosture.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_adaptive_queue_posture=${adaptiveQueuePosture.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_next_experiment=${nextExperiment.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_next_experiment_sequence=${nextExperimentSequence.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_next_experiment_stop_condition=${nextExperimentStopCondition.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_reason=${reason.toProotMainlineEnvValue()}")
            appendLine("proot_management_mainline_boundary=observe_only_no_pool_resize_no_spawn_no_kill_no_queue_no_lane_control")
        }
    }
}

object RuntimeProotManagementMainlineDryRun {
    fun evaluate(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        prootPoolPlan: RuntimeProotPoolPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeProotManagementMainlineDryRunSnapshot {
        val substrateHealthy = prootPoolPlan.policySubstrateUsable
        val pressureHeld = pressureStability.state == RuntimePressureStabilityState.PRESSURE_HOLD ||
            pressureConsumer.state == RuntimePressureConsumerState.BURST ||
            pressureConsumer.state == RuntimePressureConsumerState.DEGRADED ||
            budgetPressure.overallState.severity() >= RuntimeBudgetState.SOFT_PRESSURE.severity()
        val cleanZeroBaseline = prootPoolPlan.probeSubstrateClean &&
            pressureConsumer.liveTraceeCount == 0 &&
            pressureConsumer.prootPressureScore == 0 &&
            budgetPressure.overallState.severity() <= RuntimeBudgetState.NEAR_BUDGET.severity()
        val state = when {
            !substrateHealthy -> RuntimeProotManagementMainlineState.SUBSTRATE_BLOCKED
            pressureHeld -> RuntimeProotManagementMainlineState.TRACEE_RISK_HELD
            cleanZeroBaseline || prootPoolPlan.tuningCanProbeNow ->
                RuntimeProotManagementMainlineState.READY_FOR_LIVE_TRACEE_PROBE
            else -> RuntimeProotManagementMainlineState.STEADY_OBSERVE
        }
        val recommendation = when (state) {
            RuntimeProotManagementMainlineState.SUBSTRATE_BLOCKED ->
                RuntimeProotManagementMainlineRecommendation.REPAIR_TELEMETRY_SUBSTRATE
            RuntimeProotManagementMainlineState.TRACEE_RISK_HELD ->
                RuntimeProotManagementMainlineRecommendation.HOLD_EXPANSION_AT_KNOWN_RISK_SAMPLE
            RuntimeProotManagementMainlineState.READY_FOR_LIVE_TRACEE_PROBE ->
                RuntimeProotManagementMainlineRecommendation.RUN_CLEAN_BASELINE_LIVE_TRACEE_PROBE
            RuntimeProotManagementMainlineState.STEADY_OBSERVE ->
                RuntimeProotManagementMainlineRecommendation.KEEP_DEFAULT_BALANCED_OBSERVING
        }
        return RuntimeProotManagementMainlineDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendation,
            substrateHealthy = substrateHealthy,
            policySubstrateReason = prootPoolPlan.policySubstrateReason,
            probeSubstrateClean = prootPoolPlan.probeSubstrateClean,
            probeSubstrateReason = prootPoolPlan.probeSubstrateReason,
            pressureState = pressureConsumer.state,
            pressureStabilityState = pressureStability.state,
            budgetOverallState = budgetPressure.overallState,
            prootPressureScore = pressureConsumer.prootPressureScore,
            liveTraceeCount = pressureConsumer.liveTraceeCount,
            forkExecEventsInWindow = pressureConsumer.forkExecEventsInWindow,
            eventsInWindow = pressureConsumer.eventsInWindow,
            knownRiskUpperBoundLiveTracees = prootPoolPlan.knownRiskSampleLiveTracees,
            knownRiskUpperBoundPressureScore = prootPoolPlan.knownRiskSamplePressureScore,
            knownSafeLowerBoundLiveTracees = prootPoolPlan.knownSafeLowerBoundLiveTracees,
            recommendedDefaultLiveTraceeSoftCap = minOf(
                prootPoolPlan.knownSafeLowerBoundLiveTracees.coerceAtLeast(1),
                16
            ),
            knownSafeLowerBoundMeasured = prootPoolPlan.knownSafeLowerBoundLiveTracees > 0,
            cleanBaselineRequired = prootPoolPlan.cleanBaselineRequiredForLowerBound,
            defaultHoldExpansionEnabled = pressureHeld ||
                prootPoolPlan.tuningStatus == "HOLD_AND_MARK_UPPER_BOUND",
            defaultPoolProbeAllowedNow = cleanZeroBaseline || prootPoolPlan.tuningCanProbeNow,
            defaultCandidatePoolSlots = prootPoolPlan.tuningCandidatePoolSlots,
            adaptivePolicyStatus = prootPoolPlan.adaptivePolicyStatus,
            adaptiveUserContext = prootPoolPlan.adaptiveUserContext,
            adaptiveResourceLimiter = prootPoolPlan.adaptiveResourceLimiter,
            adaptiveEffectiveLiveTraceeSoftCap = prootPoolPlan.adaptiveEffectiveLiveTraceeSoftCap,
            adaptiveHardStopLiveTracees = prootPoolPlan.adaptiveHardStopLiveTracees,
            adaptiveConcurrencyPosture = prootPoolPlan.adaptiveConcurrencyPosture,
            adaptiveQueuePosture = prootPoolPlan.adaptiveQueuePosture,
            reason = buildReason(
                substrateHealthy = substrateHealthy,
                pressureHeld = pressureHeld,
                cleanZeroBaseline = cleanZeroBaseline,
                pressureConsumer = pressureConsumer,
                pressureStability = pressureStability,
                prootPoolPlan = prootPoolPlan
            )
        )
    }

    private fun buildReason(
        substrateHealthy: Boolean,
        pressureHeld: Boolean,
        cleanZeroBaseline: Boolean,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        prootPoolPlan: RuntimeProotPoolPlanDryRunSnapshot
    ): String {
        return "substrateHealthy=$substrateHealthy,pressureHeld=$pressureHeld," +
            "cleanZeroBaseline=$cleanZeroBaseline," +
            "pressure=${pressureConsumer.state.name}/${pressureConsumer.prootPressureScore}," +
            "stability=${pressureStability.state.name}/${pressureStability.blocker}," +
            "policySubstrate=${prootPoolPlan.policySubstrateReason}," +
            "probeSubstrate=${prootPoolPlan.probeSubstrateReason}," +
            "poolTuning=${prootPoolPlan.tuningStatus}/${prootPoolPlan.tuningPressureAttribution}," +
            "knownRisk=${prootPoolPlan.knownRiskSampleLiveTracees}"
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
}

private fun String?.toProotMainlineEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
