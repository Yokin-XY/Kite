package com.kite.app.foundation.runtime

enum class RuntimeGovernanceReadinessState {
    SHADOW_ONLY,
    CANARY_BLOCKED,
    CANARY_READY
}

enum class RuntimeGovernanceReadinessRecommendation {
    KEEP_DRY_RUN,
    REVIEW_TELEMETRY_HEALTH,
    REVIEW_PRESSURE_STABILITY,
    REVIEW_CAPACITY_BUDGET,
    READY_FOR_QUEUE_CANARY,
    READY_FOR_LANE_CANARY,
    READY_FOR_LIMITED_CANARY
}

enum class RuntimeGovernanceReadinessCapability {
    TELEMETRY_HEALTH,
    START_HOLD,
    QUEUE_PLAN,
    LANE_LIMIT,
    BUDGET_REVIEW,
    CLEANUP_REVIEW
}

data class RuntimeGovernanceReadinessCheck(
    val capability: RuntimeGovernanceReadinessCapability,
    val shadowReady: Boolean,
    val canaryReady: Boolean,
    val blocker: String,
    val reason: String
)

data class RuntimeGovernanceReadinessGateDryRunSnapshot(
    val mode: String = "runtime_governance_readiness_gate_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeGovernanceReadinessState = RuntimeGovernanceReadinessState.SHADOW_ONLY,
    val recommendation: RuntimeGovernanceReadinessRecommendation =
        RuntimeGovernanceReadinessRecommendation.KEEP_DRY_RUN,
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val actionPlanRecommendation: RuntimeGovernanceActionRecommendation =
        RuntimeGovernanceActionRecommendation.OBSERVE_ONLY,
    val lifecycleReclaimState: RuntimeLifecycleReclaimPlanState =
        RuntimeLifecycleReclaimPlanState.FOREGROUND_KEEP,
    val lifecycleReclaimRecommendation: RuntimeLifecycleReclaimRecommendation =
        RuntimeLifecycleReclaimRecommendation.KEEP_OBSERVING,
    val queuePlanRecommendation: RuntimeStartQueueRecommendation = RuntimeStartQueueRecommendation.OBSERVE_ONLY,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val pressureStabilityState: RuntimePressureStabilityState = RuntimePressureStabilityState.NO_SOURCE,
    val pressureStabilityArmingState: RuntimePressureCanaryArmingState =
        RuntimePressureCanaryArmingState.BLOCKED,
    val pressureStableForCanary: Boolean = false,
    val pressureStabilityBlocker: String = "waiting_for_telemetry",
    val prootPressureScore: Int = 0,
    val telemetryHealthy: Boolean = false,
    val plannedActionCount: Int = 0,
    val dryRunBacklogCount: Int = 0,
    val shadowReadyCount: Int = 0,
    val canaryReadyCount: Int = 0,
    val blockedCount: Int = 0,
    val telemetryBlockerCount: Int = 0,
    val pressureBlockerCount: Int = 0,
    val capacityBlockerCount: Int = 0,
    val prootCapacityReviewCount: Int = 0,
    val prootCapacityBlockedCount: Int = 0,
    val lifecycleBlockerCount: Int = 0,
    val lifecyclePendingReviewCount: Int = 0,
    val lifecycleExpireLeaseCount: Int = 0,
    val lifecycleLeasePoolOverBudget: Boolean = false,
    val lifecycleLeasePoolEvictionCandidateCount: Int = 0,
    val lifecycleLeasePoolEvictionReviewCount: Int = 0,
    val lifecycleCleanupReviewCount: Int = 0,
    val lifecycleReclaimChildrenReviewCount: Int = 0,
    val lifecycleRestartReviewCount: Int = 0,
    val lifecycleTerminateReviewCount: Int = 0,
    val lifecycleQuarantineReviewCount: Int = 0,
    val lifecycleAdvisoryIntentReviewCount: Int = 0,
    val lifecycleDirectIntentRejectReviewCount: Int = 0,
    val repairManualReady: Boolean = false,
    val repairAction: ProotTelemetryRepairAction = ProotTelemetryRepairAction.NONE,
    val checks: List<RuntimeGovernanceReadinessCheck> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation lifecycle=$lifecycleState " +
            "budget=$budgetOverallState actionPlan=${actionPlanRecommendation.name} " +
            "lifecycleReclaim=${lifecycleReclaimState.name}/${lifecycleReclaimRecommendation.name} " +
            "queue=${queuePlanRecommendation.name} pressure=$pressureState/$prootPressureScore " +
            "telemetryHealthy=$telemetryHealthy actions=$plannedActionCount backlog=$dryRunBacklogCount " +
            "shadowReady=$shadowReadyCount canaryReady=$canaryReadyCount blocked=$blockedCount " +
            "telemetryBlockers=$telemetryBlockerCount pressureBlockers=$pressureBlockerCount " +
            "capacityBlockers=$capacityBlockerCount prootCapacity=$prootCapacityReviewCount " +
            "prootCapacityBlocked=$prootCapacityBlockedCount lifecycleBlockers=$lifecycleBlockerCount " +
            "lifecyclePending=$lifecyclePendingReviewCount " +
            "leasePoolEvict=$lifecycleLeasePoolEvictionReviewCount " +
            "lifecycleIntent=$lifecycleAdvisoryIntentReviewCount " +
            "directIntentReject=$lifecycleDirectIntentRejectReviewCount " +
            "repairManualReady=$repairManualReady " +
            "repairAction=$repairAction enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxChecks: Int = 8): String {
        return buildString {
            appendLine("governance_readiness_mode=${mode.toGovernanceReadinessEnvValue()}")
            appendLine("governance_readiness_enforcement_mode=${enforcementMode.toGovernanceReadinessEnvValue()}")
            appendLine("governance_readiness_enforcement_enabled=$enforcementEnabled")
            appendLine("governance_readiness_generated_at=$generatedAtMs")
            appendLine("governance_readiness_state=${state.name}")
            appendLine("governance_readiness_recommendation=${recommendation.name}")
            appendLine("governance_readiness_lifecycle_state=${lifecycleState.name}")
            appendLine("governance_readiness_background_phase=${backgroundPhase.name}")
            appendLine("governance_readiness_budget_overall_state=${budgetOverallState.name}")
            appendLine("governance_readiness_action_plan_recommendation=${actionPlanRecommendation.name}")
            appendLine("governance_readiness_lifecycle_reclaim_state=${lifecycleReclaimState.name}")
            appendLine("governance_readiness_lifecycle_reclaim_recommendation=${lifecycleReclaimRecommendation.name}")
            appendLine("governance_readiness_queue_plan_recommendation=${queuePlanRecommendation.name}")
            appendLine("governance_readiness_pressure_state=${pressureState.name}")
            appendLine("governance_readiness_pressure_stability_state=${pressureStabilityState.name}")
            appendLine("governance_readiness_pressure_stability_arming_state=${pressureStabilityArmingState.name}")
            appendLine("governance_readiness_pressure_stable_for_canary=$pressureStableForCanary")
            appendLine("governance_readiness_pressure_stability_blocker=${pressureStabilityBlocker.toGovernanceReadinessEnvValue()}")
            appendLine("governance_readiness_proot_score=$prootPressureScore")
            appendLine("governance_readiness_telemetry_healthy=$telemetryHealthy")
            appendLine("governance_readiness_planned_action_count=$plannedActionCount")
            appendLine("governance_readiness_dry_run_backlog_count=$dryRunBacklogCount")
            appendLine("governance_readiness_shadow_ready_count=$shadowReadyCount")
            appendLine("governance_readiness_canary_ready_count=$canaryReadyCount")
            appendLine("governance_readiness_blocked_count=$blockedCount")
            appendLine("governance_readiness_telemetry_blocker_count=$telemetryBlockerCount")
            appendLine("governance_readiness_pressure_blocker_count=$pressureBlockerCount")
            appendLine("governance_readiness_capacity_blocker_count=$capacityBlockerCount")
            appendLine("governance_readiness_proot_capacity_review_count=$prootCapacityReviewCount")
            appendLine("governance_readiness_proot_capacity_blocked_count=$prootCapacityBlockedCount")
            appendLine("governance_readiness_lifecycle_blocker_count=$lifecycleBlockerCount")
            appendLine("governance_readiness_lifecycle_pending_review_count=$lifecyclePendingReviewCount")
            appendLine("governance_readiness_lifecycle_expire_lease_count=$lifecycleExpireLeaseCount")
            appendLine("governance_readiness_lifecycle_lease_pool_over_budget=$lifecycleLeasePoolOverBudget")
            appendLine("governance_readiness_lifecycle_lease_pool_eviction_candidate_count=$lifecycleLeasePoolEvictionCandidateCount")
            appendLine("governance_readiness_lifecycle_lease_pool_eviction_review_count=$lifecycleLeasePoolEvictionReviewCount")
            appendLine("governance_readiness_lifecycle_cleanup_review_count=$lifecycleCleanupReviewCount")
            appendLine("governance_readiness_lifecycle_reclaim_children_review_count=$lifecycleReclaimChildrenReviewCount")
            appendLine("governance_readiness_lifecycle_restart_review_count=$lifecycleRestartReviewCount")
            appendLine("governance_readiness_lifecycle_terminate_review_count=$lifecycleTerminateReviewCount")
            appendLine("governance_readiness_lifecycle_quarantine_review_count=$lifecycleQuarantineReviewCount")
            appendLine("governance_readiness_lifecycle_advisory_intent_review_count=$lifecycleAdvisoryIntentReviewCount")
            appendLine("governance_readiness_lifecycle_direct_intent_reject_review_count=$lifecycleDirectIntentRejectReviewCount")
            appendLine("governance_readiness_repair_manual_ready=$repairManualReady")
            appendLine("governance_readiness_repair_action=${repairAction.name}")
            checks.take(maxChecks).forEachIndexed { index, check ->
                val prefix = "governance_readiness_check_${index + 1}"
                appendLine("${prefix}_capability=${check.capability.name}")
                appendLine("${prefix}_shadow_ready=${check.shadowReady}")
                appendLine("${prefix}_canary_ready=${check.canaryReady}")
                appendLine("${prefix}_blocker=${check.blocker.toGovernanceReadinessEnvValue()}")
                appendLine("${prefix}_reason=${check.reason.toGovernanceReadinessEnvValue()}")
            }
            appendLine("governance_readiness_boundary=dry_run_no_activation_no_enforcement_no_queue_creation_no_start_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeGovernanceReadinessGateDryRun {
    fun evaluate(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        prootTelemetryRepairPlan: ProotTelemetryRepairPlanDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot,
        governanceActionPlan: RuntimeGovernanceActionPlanDryRunSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeGovernanceReadinessGateDryRunSnapshot {
        val telemetryHealthy = prootTelemetryHealth.canaryHealthy
        val telemetryBlocker = if (telemetryHealthy) "none" else prootTelemetryHealth.blocker
        val pressureBlocker = when {
            !telemetryHealthy -> "none"
            pressureStability.canaryStable -> "none"
            pressureStability.canaryArmingState == RuntimePressureCanaryArmingState.WARMING ->
                "stability_window_warming"
            pressureStability.blocker != "none" -> pressureStability.blocker
            else -> "pressure_stability_not_armed"
        }
        val pressureBlocksCanary = pressureBlocker != "none"
        val capacityBlocked = startQueuePlan.blockedNoCapacityCount > 0 ||
            laneAdmission.noCapacityLaneCount > 0 ||
            governanceActionPlan.prootCapacityBlockedCount > 0
        val lifecyclePendingReviewCount = governanceActionPlan.lifecycleExpireLeaseCount +
            governanceActionPlan.lifecycleLeasePoolEvictionReviewCount +
            governanceActionPlan.lifecycleCleanupReviewCount +
            governanceActionPlan.lifecycleReclaimChildrenReviewCount +
            governanceActionPlan.lifecycleRestartReviewCount +
            governanceActionPlan.lifecycleTerminateReviewCount +
            governanceActionPlan.lifecycleQuarantineReviewCount +
            governanceActionPlan.lifecycleAdvisoryIntentReviewCount +
            governanceActionPlan.lifecycleDirectIntentRejectReviewCount
        val checks = listOf(
            telemetryCheck(prootTelemetryHealth, telemetryHealthy, pressureConsumer),
            startHoldCheck(prootTelemetryHealth, telemetryHealthy, pressureBlocker, capacityBlocked, startQueuePlan),
            queuePlanCheck(prootTelemetryHealth, telemetryHealthy, capacityBlocked, startQueuePlan),
            laneLimitCheck(prootTelemetryHealth, telemetryHealthy, pressureBlocker, capacityBlocked, laneAdmission),
            budgetReviewCheck(prootTelemetryHealth, telemetryHealthy, budgetPressure),
            cleanupReviewCheck(prootTelemetryHealth, telemetryHealthy, governanceActionPlan)
        )
        val canaryReadyCount = checks.count { it.canaryReady }
        val blockedCount = checks.count { !it.canaryReady }
        val state = when {
            blockedCount == checks.size -> RuntimeGovernanceReadinessState.CANARY_BLOCKED
            blockedCount > 0 -> RuntimeGovernanceReadinessState.SHADOW_ONLY
            else -> RuntimeGovernanceReadinessState.CANARY_READY
        }

        return RuntimeGovernanceReadinessGateDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(
                telemetryHealthy = telemetryHealthy,
                pressureBlocksCanary = pressureBlocksCanary,
                capacityBlocked = capacityBlocked,
                canaryReadyCount = canaryReadyCount,
                totalChecks = checks.size
            ),
            lifecycleState = backgroundDecay.lifecycleState,
            backgroundPhase = backgroundDecay.phase,
            budgetOverallState = budgetPressure.overallState,
            actionPlanRecommendation = governanceActionPlan.recommendation,
            lifecycleReclaimState = lifecycleReclaimPlan.state,
            lifecycleReclaimRecommendation = lifecycleReclaimPlan.recommendation,
            queuePlanRecommendation = startQueuePlan.recommendation,
            pressureState = pressureConsumer.state,
            pressureStabilityState = pressureStability.state,
            pressureStabilityArmingState = pressureStability.canaryArmingState,
            pressureStableForCanary = pressureStability.canaryStable,
            pressureStabilityBlocker = pressureBlocker,
            prootPressureScore = pressureConsumer.prootPressureScore,
            telemetryHealthy = prootTelemetryHealth.canaryHealthy,
            plannedActionCount = governanceActionPlan.plannedActionCount,
            dryRunBacklogCount = startQueuePlan.dryRunBacklogCount,
            shadowReadyCount = checks.count { it.shadowReady },
            canaryReadyCount = canaryReadyCount,
            blockedCount = blockedCount,
            telemetryBlockerCount = checks.count { telemetryBlocker != "none" && it.blocker == telemetryBlocker },
            pressureBlockerCount = checks.count { pressureBlocker != "none" && it.blocker == pressureBlocker },
            capacityBlockerCount = checks.count { it.blocker == "capacity_blocked" },
            prootCapacityReviewCount = governanceActionPlan.prootCapacityReviewCount,
            prootCapacityBlockedCount = governanceActionPlan.prootCapacityBlockedCount,
            lifecycleBlockerCount = checks.count {
                it.blocker == "lifecycle_reclaim_review_pending" ||
                    it.blocker == "lifecycle_intent_review_pending" ||
                    it.blocker == "lifecycle_direct_action_intent_rejected"
            },
            lifecyclePendingReviewCount = lifecyclePendingReviewCount,
            lifecycleExpireLeaseCount = governanceActionPlan.lifecycleExpireLeaseCount,
            lifecycleLeasePoolOverBudget = governanceActionPlan.lifecycleLeasePoolOverBudget,
            lifecycleLeasePoolEvictionCandidateCount =
                governanceActionPlan.lifecycleLeasePoolEvictionCandidateCount,
            lifecycleLeasePoolEvictionReviewCount =
                governanceActionPlan.lifecycleLeasePoolEvictionReviewCount,
            lifecycleCleanupReviewCount = governanceActionPlan.lifecycleCleanupReviewCount,
            lifecycleReclaimChildrenReviewCount = governanceActionPlan.lifecycleReclaimChildrenReviewCount,
            lifecycleRestartReviewCount = governanceActionPlan.lifecycleRestartReviewCount,
            lifecycleTerminateReviewCount = governanceActionPlan.lifecycleTerminateReviewCount,
            lifecycleQuarantineReviewCount = governanceActionPlan.lifecycleQuarantineReviewCount,
            lifecycleAdvisoryIntentReviewCount =
                governanceActionPlan.lifecycleAdvisoryIntentReviewCount,
            lifecycleDirectIntentRejectReviewCount =
                governanceActionPlan.lifecycleDirectIntentRejectReviewCount,
            repairManualReady = prootTelemetryRepairPlan.readiness == ProotTelemetryRepairReadiness.MANUAL_READY,
            repairAction = prootTelemetryRepairPlan.action,
            checks = checks
        )
    }

    private fun telemetryCheck(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        telemetryHealthy: Boolean,
        pressureConsumer: RuntimePressureConsumerSnapshot
    ): RuntimeGovernanceReadinessCheck {
        return RuntimeGovernanceReadinessCheck(
            capability = RuntimeGovernanceReadinessCapability.TELEMETRY_HEALTH,
            shadowReady = pressureConsumer.generatedAtMs > 0L,
            canaryReady = telemetryHealthy,
            blocker = if (telemetryHealthy) "none" else prootTelemetryHealth.blocker,
            reason = "${prootTelemetryHealth.state.name}:${prootTelemetryHealth.reason}"
        )
    }

    private fun startHoldCheck(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        telemetryHealthy: Boolean,
        pressureBlocker: String,
        capacityBlocked: Boolean,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot
    ): RuntimeGovernanceReadinessCheck {
        val hasHoldPlan = startQueuePlan.deferUntilPressureEasesCount > 0 ||
            startQueuePlan.foregroundRequiredCount > 0
        val blocker = readinessBlocker(prootTelemetryHealth, telemetryHealthy, pressureBlocker, capacityBlocked)
        return RuntimeGovernanceReadinessCheck(
            capability = RuntimeGovernanceReadinessCapability.START_HOLD,
            shadowReady = hasHoldPlan || startQueuePlan.sourceIntentCount > 0,
            canaryReady = hasHoldPlan && blocker == "none",
            blocker = blocker,
            reason = "holds=${startQueuePlan.deferUntilPressureEasesCount}+${startQueuePlan.foregroundRequiredCount},backlog=${startQueuePlan.dryRunBacklogCount}"
        )
    }

    private fun queuePlanCheck(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        telemetryHealthy: Boolean,
        capacityBlocked: Boolean,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot
    ): RuntimeGovernanceReadinessCheck {
        val blocker = when {
            !telemetryHealthy -> prootTelemetryHealth.blocker
            capacityBlocked -> "capacity_blocked"
            else -> "none"
        }
        return RuntimeGovernanceReadinessCheck(
            capability = RuntimeGovernanceReadinessCapability.QUEUE_PLAN,
            shadowReady = startQueuePlan.sourceIntentCount > 0,
            canaryReady = startQueuePlan.sourceIntentCount > 0 && blocker == "none",
            blocker = blocker,
            reason = "backlog=${startQueuePlan.dryRunBacklogCount},queue=${startQueuePlan.wouldQueueCount},watch=${startQueuePlan.watchOnlyCount}"
        )
    }

    private fun laneLimitCheck(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        telemetryHealthy: Boolean,
        pressureBlocker: String,
        capacityBlocked: Boolean,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot
    ): RuntimeGovernanceReadinessCheck {
        val blocker = readinessBlocker(prootTelemetryHealth, telemetryHealthy, pressureBlocker, capacityBlocked)
        return RuntimeGovernanceReadinessCheck(
            capability = RuntimeGovernanceReadinessCapability.LANE_LIMIT,
            shadowReady = laneAdmission.policyLaneCount > 0,
            canaryReady = laneAdmission.policyLaneCount > 0 && blocker == "none",
            blocker = blocker,
            reason = "defer=${laneAdmission.deferLowPriorityLaneCount},watch=${laneAdmission.watchLaneCount},noCapacity=${laneAdmission.noCapacityLaneCount}"
        )
    }

    private fun budgetReviewCheck(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        telemetryHealthy: Boolean,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ): RuntimeGovernanceReadinessCheck {
        val stableEnough = budgetPressure.overallState.severity() <= RuntimeBudgetState.NEAR_BUDGET.severity()
        val blocker = when {
            !telemetryHealthy -> prootTelemetryHealth.blocker
            !stableEnough -> "pressure_not_stable"
            else -> "none"
        }
        return RuntimeGovernanceReadinessCheck(
            capability = RuntimeGovernanceReadinessCapability.BUDGET_REVIEW,
            shadowReady = budgetPressure.generatedAtMs > 0L,
            canaryReady = stableEnough && blocker == "none",
            blocker = blocker,
            reason = "state=${budgetPressure.overallState.name},candidates=${budgetPressure.candidates.size}"
        )
    }

    private fun cleanupReviewCheck(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        telemetryHealthy: Boolean,
        governanceActionPlan: RuntimeGovernanceActionPlanDryRunSnapshot
    ): RuntimeGovernanceReadinessCheck {
        val hasCleanupPlan = governanceActionPlan.cleanupReviewCount > 0
        val lifecycleReviewCount = governanceActionPlan.lifecycleExpireLeaseCount +
            governanceActionPlan.lifecycleLeasePoolEvictionReviewCount +
            governanceActionPlan.lifecycleCleanupReviewCount +
            governanceActionPlan.lifecycleReclaimChildrenReviewCount +
            governanceActionPlan.lifecycleRestartReviewCount +
            governanceActionPlan.lifecycleTerminateReviewCount +
            governanceActionPlan.lifecycleQuarantineReviewCount
        val lifecycleIntentReviewCount = governanceActionPlan.lifecycleAdvisoryIntentReviewCount +
            governanceActionPlan.lifecycleDirectIntentRejectReviewCount
        val hasLifecycleReclaimPlan = lifecycleReviewCount > 0
        val hasLifecycleIntentPlan = lifecycleIntentReviewCount > 0
        return RuntimeGovernanceReadinessCheck(
            capability = RuntimeGovernanceReadinessCapability.CLEANUP_REVIEW,
            shadowReady = true,
            canaryReady = telemetryHealthy &&
                !hasCleanupPlan &&
                !hasLifecycleReclaimPlan &&
                !hasLifecycleIntentPlan,
            blocker = when {
                !telemetryHealthy -> prootTelemetryHealth.blocker
                hasLifecycleReclaimPlan -> "lifecycle_reclaim_review_pending"
                governanceActionPlan.lifecycleDirectIntentRejectReviewCount > 0 ->
                    "lifecycle_direct_action_intent_rejected"
                hasLifecycleIntentPlan -> "lifecycle_intent_review_pending"
                hasCleanupPlan -> "cleanup_candidates_present"
                else -> "none"
            },
            reason = "cleanupReview=${governanceActionPlan.cleanupReviewCount}," +
                "lifecycleExpire=${governanceActionPlan.lifecycleExpireLeaseCount}," +
                "leasePoolEvict=${governanceActionPlan.lifecycleLeasePoolEvictionReviewCount}," +
                "lifecycleCleanup=${governanceActionPlan.lifecycleCleanupReviewCount}," +
                "lifecycleReclaim=${governanceActionPlan.lifecycleReclaimChildrenReviewCount}," +
                "lifecycleRestart=${governanceActionPlan.lifecycleRestartReviewCount}," +
                "lifecycleTerminate=${governanceActionPlan.lifecycleTerminateReviewCount}," +
                "lifecycleQuarantine=${governanceActionPlan.lifecycleQuarantineReviewCount}," +
                "lifecycleIntent=${governanceActionPlan.lifecycleAdvisoryIntentReviewCount}," +
                "lifecycleDirectIntent=${governanceActionPlan.lifecycleDirectIntentRejectReviewCount}"
        )
    }

    private fun readinessBlocker(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        telemetryHealthy: Boolean,
        pressureBlocker: String,
        capacityBlocked: Boolean
    ): String {
        return when {
            !telemetryHealthy -> prootTelemetryHealth.blocker
            capacityBlocked -> "capacity_blocked"
            pressureBlocker != "none" -> pressureBlocker
            else -> "none"
        }
    }

    private fun recommendationFor(
        telemetryHealthy: Boolean,
        pressureBlocksCanary: Boolean,
        capacityBlocked: Boolean,
        canaryReadyCount: Int,
        totalChecks: Int
    ): RuntimeGovernanceReadinessRecommendation {
        if (!telemetryHealthy) {
            return RuntimeGovernanceReadinessRecommendation.REVIEW_TELEMETRY_HEALTH
        }
        if (capacityBlocked) {
            return RuntimeGovernanceReadinessRecommendation.REVIEW_CAPACITY_BUDGET
        }
        if (pressureBlocksCanary) {
            return RuntimeGovernanceReadinessRecommendation.REVIEW_PRESSURE_STABILITY
        }
        if (canaryReadyCount >= totalChecks) {
            return RuntimeGovernanceReadinessRecommendation.READY_FOR_LIMITED_CANARY
        }
        if (canaryReadyCount >= 2) {
            return RuntimeGovernanceReadinessRecommendation.READY_FOR_QUEUE_CANARY
        }
        return RuntimeGovernanceReadinessRecommendation.KEEP_DRY_RUN
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

private fun String?.toGovernanceReadinessEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(220)
}
