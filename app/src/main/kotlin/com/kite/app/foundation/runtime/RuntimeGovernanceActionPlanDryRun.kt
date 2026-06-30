package com.kite.app.foundation.runtime

enum class RuntimeGovernanceActionKind {
    WATCH_SYSTEM,
    WATCH_START_INTENT,
    WATCH_LIFECYCLE_LEASE,
    HOLD_START_UNTIL_PRESSURE_EASES,
    HOLD_START_UNTIL_FOREGROUND,
    WOULD_QUEUE_START,
    REVIEW_LANE_LIMIT,
    REVIEW_BUDGET_PRESSURE,
    REVIEW_EXPIRE_LEASE,
    REVIEW_LEASE_POOL_EVICTION,
    REVIEW_CLEANUP_CANDIDATE,
    REVIEW_RECLAIM_CHILDREN,
    REVIEW_RESTART_MAIN,
    REVIEW_TERMINATE_WORKLOAD,
    REVIEW_QUARANTINE,
    REVIEW_LIFECYCLE_ADVISORY_INTENT,
    REVIEW_REJECTED_DIRECT_INTENT,
    REVIEW_TELEMETRY_HEALTH,
    REVIEW_PROOT_EXPANSION_REQUEST,
    REVIEW_PROOT_QUEUE_REQUEST,
    REVIEW_PROOT_DOWNSCALE_REQUEST,
    REVIEW_NO_CAPACITY
}

enum class RuntimeGovernanceActionTarget {
    SYSTEM,
    PROOT,
    INTENT,
    LANE,
    WORKLOAD
}

enum class RuntimeGovernanceActionRecommendation {
    OBSERVE_ONLY,
    REVIEW_WATCHLIST,
    REVIEW_QUEUE_PLAN,
    REVIEW_FOREGROUND_HOLDS,
    REVIEW_PRESSURE_HOLDS,
    REVIEW_BUDGET_ACTIONS,
    REVIEW_LIFECYCLE_RECLAIM,
    REVIEW_LIFECYCLE_INTENTS,
    REVIEW_PROOT_CAPACITY,
    REVIEW_NO_CAPACITY
}

data class RuntimeGovernanceActionPlanItem(
    val actionKind: RuntimeGovernanceActionKind,
    val target: RuntimeGovernanceActionTarget,
    val targetId: String,
    val sourceLayer: String,
    val lane: RuntimeLaneKind? = null,
    val workloadClass: RuntimeWorkloadClass? = null,
    val priority: Int = 0,
    val dryRunRank: Int = 0,
    val severity: Int = 0,
    val reason: String
)

data class RuntimeGovernanceActionPlanDryRunSnapshot(
    val mode: String = "runtime_governance_action_plan_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val recommendation: RuntimeGovernanceActionRecommendation =
        RuntimeGovernanceActionRecommendation.OBSERVE_ONLY,
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val queuePlanRecommendation: RuntimeStartQueueRecommendation = RuntimeStartQueueRecommendation.OBSERVE_ONLY,
    val startPreflightRecommendation: RuntimeStartPreflightRecommendation =
        RuntimeStartPreflightRecommendation.OBSERVE_ONLY,
    val laneAdmissionRecommendation: RuntimeLaneAdmissionRecommendation =
        RuntimeLaneAdmissionRecommendation.OBSERVE_ONLY,
    val lifecycleReclaimRecommendation: RuntimeLifecycleReclaimRecommendation =
        RuntimeLifecycleReclaimRecommendation.KEEP_OBSERVING,
    val lifecycleReclaimState: RuntimeLifecycleReclaimPlanState =
        RuntimeLifecycleReclaimPlanState.FOREGROUND_KEEP,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val prootPressureScore: Int = 0,
    val plannedActionCount: Int = 0,
    val watchActionCount: Int = 0,
    val pressureHoldCount: Int = 0,
    val foregroundHoldCount: Int = 0,
    val queueStartCount: Int = 0,
    val laneLimitReviewCount: Int = 0,
    val budgetReviewCount: Int = 0,
    val cleanupReviewCount: Int = 0,
    val lifecycleWatchLeaseCount: Int = 0,
    val lifecycleExpireLeaseCount: Int = 0,
    val lifecycleLeasePoolMemoryBudgetKb: Long = 0L,
    val lifecycleLeasePoolRssKb: Long = 0L,
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
    val telemetryReviewCount: Int = 0,
    val prootCapacityReviewCount: Int = 0,
    val prootExpansionReviewCount: Int = 0,
    val prootQueueReviewCount: Int = 0,
    val prootDownlineReviewCount: Int = 0,
    val prootCapacityApprovedCount: Int = 0,
    val prootCapacityBlockedCount: Int = 0,
    val noCapacityReviewCount: Int = 0,
    val dryRunBacklogCount: Int = 0,
    val items: List<RuntimeGovernanceActionPlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode recommendation=$recommendation lifecycle=$lifecycleState " +
            "budget=$budgetOverallState queue=${queuePlanRecommendation.name} " +
            "preflight=${startPreflightRecommendation.name} lane=${laneAdmissionRecommendation.name} " +
            "lifecycleReclaim=${lifecycleReclaimState.name}/${lifecycleReclaimRecommendation.name} " +
            "pressure=$pressureState/$prootPressureScore actions=$plannedActionCount watch=$watchActionCount " +
            "pressureHold=$pressureHoldCount foregroundHold=$foregroundHoldCount queue=$queueStartCount " +
            "laneReview=$laneLimitReviewCount budgetReview=$budgetReviewCount cleanupReview=$cleanupReviewCount " +
            "lifecycleExpire=$lifecycleExpireLeaseCount lifecycleCleanup=$lifecycleCleanupReviewCount " +
            "leasePool=${lifecycleLeasePoolRssKb}/${lifecycleLeasePoolMemoryBudgetKb} " +
            "leasePoolEvict=$lifecycleLeasePoolEvictionReviewCount " +
            "lifecycleIntent=$lifecycleAdvisoryIntentReviewCount " +
            "directIntentReject=$lifecycleDirectIntentRejectReviewCount " +
            "prootCapacity=$prootCapacityReviewCount prootCapacityBlocked=$prootCapacityBlockedCount " +
            "telemetryReview=$telemetryReviewCount noCapacity=$noCapacityReviewCount backlog=$dryRunBacklogCount " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 24): String {
        return buildString {
            appendLine("governance_action_plan_mode=${mode.toGovernanceActionEnvValue()}")
            appendLine("governance_action_plan_enforcement_mode=${enforcementMode.toGovernanceActionEnvValue()}")
            appendLine("governance_action_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("governance_action_plan_generated_at=$generatedAtMs")
            appendLine("governance_action_plan_recommendation=${recommendation.name}")
            appendLine("governance_action_plan_lifecycle_state=${lifecycleState.name}")
            appendLine("governance_action_plan_background_phase=${backgroundPhase.name}")
            appendLine("governance_action_plan_budget_overall_state=${budgetOverallState.name}")
            appendLine("governance_action_plan_queue_plan_recommendation=${queuePlanRecommendation.name}")
            appendLine("governance_action_plan_start_preflight_recommendation=${startPreflightRecommendation.name}")
            appendLine("governance_action_plan_lane_admission_recommendation=${laneAdmissionRecommendation.name}")
            appendLine("governance_action_plan_lifecycle_reclaim_state=${lifecycleReclaimState.name}")
            appendLine("governance_action_plan_lifecycle_reclaim_recommendation=${lifecycleReclaimRecommendation.name}")
            appendLine("governance_action_plan_pressure_state=${pressureState.name}")
            appendLine("governance_action_plan_proot_score=$prootPressureScore")
            appendLine("governance_action_plan_planned_action_count=$plannedActionCount")
            appendLine("governance_action_plan_watch_action_count=$watchActionCount")
            appendLine("governance_action_plan_pressure_hold_count=$pressureHoldCount")
            appendLine("governance_action_plan_foreground_hold_count=$foregroundHoldCount")
            appendLine("governance_action_plan_queue_start_count=$queueStartCount")
            appendLine("governance_action_plan_lane_limit_review_count=$laneLimitReviewCount")
            appendLine("governance_action_plan_budget_review_count=$budgetReviewCount")
            appendLine("governance_action_plan_cleanup_review_count=$cleanupReviewCount")
            appendLine("governance_action_plan_lifecycle_watch_lease_count=$lifecycleWatchLeaseCount")
            appendLine("governance_action_plan_lifecycle_expire_lease_count=$lifecycleExpireLeaseCount")
            appendLine("governance_action_plan_lifecycle_lease_pool_memory_budget_kb=$lifecycleLeasePoolMemoryBudgetKb")
            appendLine("governance_action_plan_lifecycle_lease_pool_rss_kb=$lifecycleLeasePoolRssKb")
            appendLine("governance_action_plan_lifecycle_lease_pool_over_budget=$lifecycleLeasePoolOverBudget")
            appendLine("governance_action_plan_lifecycle_lease_pool_eviction_candidate_count=$lifecycleLeasePoolEvictionCandidateCount")
            appendLine("governance_action_plan_lifecycle_lease_pool_eviction_review_count=$lifecycleLeasePoolEvictionReviewCount")
            appendLine("governance_action_plan_lifecycle_cleanup_review_count=$lifecycleCleanupReviewCount")
            appendLine("governance_action_plan_lifecycle_reclaim_children_review_count=$lifecycleReclaimChildrenReviewCount")
            appendLine("governance_action_plan_lifecycle_restart_review_count=$lifecycleRestartReviewCount")
            appendLine("governance_action_plan_lifecycle_terminate_review_count=$lifecycleTerminateReviewCount")
            appendLine("governance_action_plan_lifecycle_quarantine_review_count=$lifecycleQuarantineReviewCount")
            appendLine("governance_action_plan_lifecycle_advisory_intent_review_count=$lifecycleAdvisoryIntentReviewCount")
            appendLine("governance_action_plan_lifecycle_direct_intent_reject_review_count=$lifecycleDirectIntentRejectReviewCount")
            appendLine("governance_action_plan_telemetry_review_count=$telemetryReviewCount")
            appendLine("governance_action_plan_proot_capacity_review_count=$prootCapacityReviewCount")
            appendLine("governance_action_plan_proot_expansion_review_count=$prootExpansionReviewCount")
            appendLine("governance_action_plan_proot_queue_review_count=$prootQueueReviewCount")
            appendLine("governance_action_plan_proot_downline_review_count=$prootDownlineReviewCount")
            appendLine("governance_action_plan_proot_capacity_approved_count=$prootCapacityApprovedCount")
            appendLine("governance_action_plan_proot_capacity_blocked_count=$prootCapacityBlockedCount")
            appendLine("governance_action_plan_no_capacity_review_count=$noCapacityReviewCount")
            appendLine("governance_action_plan_dry_run_backlog_count=$dryRunBacklogCount")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "governance_action_plan_item_${index + 1}"
                appendLine("${prefix}_kind=${item.actionKind.name}")
                appendLine("${prefix}_target=${item.target.name}")
                appendLine("${prefix}_target_id=${item.targetId.toGovernanceActionEnvValue()}")
                appendLine("${prefix}_source_layer=${item.sourceLayer.toGovernanceActionEnvValue()}")
                appendLine("${prefix}_lane=${item.lane?.name ?: "none"}")
                appendLine("${prefix}_class=${item.workloadClass?.name ?: "none"}")
                appendLine("${prefix}_priority=${item.priority}")
                appendLine("${prefix}_dry_run_rank=${item.dryRunRank}")
                appendLine("${prefix}_severity=${item.severity}")
                appendLine("${prefix}_reason=${item.reason.toGovernanceActionEnvValue()}")
            }
            appendLine("governance_action_plan_boundary=dry_run_no_actions_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeGovernanceActionPlanDryRun {
    fun evaluate(
        pressureConsumer: RuntimePressureConsumerSnapshot,
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        startPreflight: RuntimeStartPreflightDryRunSnapshot,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot,
        lifecycleIntentSurface: RuntimeLifecycleIntentSurfaceDryRunSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        lifecycleProotExpansionBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeGovernanceActionPlanDryRunSnapshot {
        val items = buildList {
            addSystemPressureItems(pressureConsumer, budgetPressure)
            addLaneItems(laneAdmission)
            addQueueItems(startQueuePlan)
            addBudgetItems(budgetPressure)
            addWorkloadRegistryItems(workloadRegistry)
            addLifecycleIntentItems(lifecycleIntentSurface)
            addLifecycleReclaimItems(lifecycleReclaimPlan)
            addProotCapacityItem(lifecycleProotExpansionBudget)
        }.sortedWith(
            compareByDescending<RuntimeGovernanceActionPlanItem> { it.severity }
                .thenBy { if (it.dryRunRank > 0) it.dryRunRank else Int.MAX_VALUE }
                .thenBy { it.priority }
                .thenBy { it.actionKind.ordinal }
                .thenBy { it.targetId }
        )
        val recommendation = items
            .map { it.actionKind.toRecommendation() }
            .maxByOrNull { it.severity() }
            ?: RuntimeGovernanceActionRecommendation.OBSERVE_ONLY

        return RuntimeGovernanceActionPlanDryRunSnapshot(
            generatedAtMs = now,
            recommendation = recommendation,
            lifecycleState = backgroundDecay.lifecycleState,
            backgroundPhase = backgroundDecay.phase,
            budgetOverallState = budgetPressure.overallState,
            queuePlanRecommendation = startQueuePlan.recommendation,
            startPreflightRecommendation = startPreflight.recommendation,
            laneAdmissionRecommendation = laneAdmission.recommendation,
            lifecycleReclaimRecommendation = lifecycleReclaimPlan.recommendation,
            lifecycleReclaimState = lifecycleReclaimPlan.state,
            pressureState = pressureConsumer.state,
            prootPressureScore = pressureConsumer.prootPressureScore,
            plannedActionCount = items.size,
            watchActionCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.WATCH_SYSTEM ||
                    it.actionKind == RuntimeGovernanceActionKind.WATCH_START_INTENT ||
                    it.actionKind == RuntimeGovernanceActionKind.WATCH_LIFECYCLE_LEASE
            },
            pressureHoldCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.HOLD_START_UNTIL_PRESSURE_EASES
            },
            foregroundHoldCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.HOLD_START_UNTIL_FOREGROUND
            },
            queueStartCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.WOULD_QUEUE_START
            },
            laneLimitReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_LANE_LIMIT
            },
            budgetReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_BUDGET_PRESSURE
            },
            cleanupReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_CLEANUP_CANDIDATE
            },
            lifecycleWatchLeaseCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.WATCH_LIFECYCLE_LEASE
            },
            lifecycleExpireLeaseCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_EXPIRE_LEASE
            },
            lifecycleLeasePoolMemoryBudgetKb = lifecycleReclaimPlan.leasePoolMemoryBudgetKb,
            lifecycleLeasePoolRssKb = lifecycleReclaimPlan.leasePoolRssKb,
            lifecycleLeasePoolOverBudget = lifecycleReclaimPlan.leasePoolOverBudget,
            lifecycleLeasePoolEvictionCandidateCount = lifecycleReclaimPlan.leasePoolEvictionCandidateCount,
            lifecycleLeasePoolEvictionReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_LEASE_POOL_EVICTION
            },
            lifecycleCleanupReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_CLEANUP_CANDIDATE &&
                    it.sourceLayer == "LifecycleReclaimPlan"
            },
            lifecycleReclaimChildrenReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_RECLAIM_CHILDREN
            },
            lifecycleRestartReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_RESTART_MAIN
            },
            lifecycleTerminateReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_TERMINATE_WORKLOAD
            },
            lifecycleQuarantineReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_QUARANTINE
            },
            lifecycleAdvisoryIntentReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_LIFECYCLE_ADVISORY_INTENT
            },
            lifecycleDirectIntentRejectReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_REJECTED_DIRECT_INTENT
            },
            telemetryReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_TELEMETRY_HEALTH
            },
            prootCapacityReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_PROOT_EXPANSION_REQUEST ||
                    it.actionKind == RuntimeGovernanceActionKind.REVIEW_PROOT_QUEUE_REQUEST ||
                    it.actionKind == RuntimeGovernanceActionKind.REVIEW_PROOT_DOWNSCALE_REQUEST
            },
            prootExpansionReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_PROOT_EXPANSION_REQUEST
            },
            prootQueueReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_PROOT_QUEUE_REQUEST
            },
            prootDownlineReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_PROOT_DOWNSCALE_REQUEST
            },
            prootCapacityApprovedCount = if (
                lifecycleProotExpansionBudget.capacityReviewNeeded &&
                lifecycleProotExpansionBudget.lifecycleApprovesRequestedAction
            ) 1 else 0,
            prootCapacityBlockedCount = if (
                lifecycleProotExpansionBudget.capacityReviewNeeded &&
                !lifecycleProotExpansionBudget.lifecycleApprovesRequestedAction
            ) 1 else 0,
            noCapacityReviewCount = items.count {
                it.actionKind == RuntimeGovernanceActionKind.REVIEW_NO_CAPACITY
            },
            dryRunBacklogCount = startQueuePlan.dryRunBacklogCount,
            items = items
        )
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addSystemPressureItems(
        pressureConsumer: RuntimePressureConsumerSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ) {
        if (pressureConsumer.state == RuntimePressureConsumerState.DEGRADED ||
            !pressureConsumer.telemetryHealthy
        ) {
            add(
                RuntimeGovernanceActionPlanItem(
                    actionKind = RuntimeGovernanceActionKind.REVIEW_TELEMETRY_HEALTH,
                    target = RuntimeGovernanceActionTarget.SYSTEM,
                    targetId = "pressure_consumer",
                    sourceLayer = "PressureConsumer",
                    severity = 4,
                    reason = pressureConsumer.reason
                )
            )
        }
        if (budgetPressure.overallState != RuntimeBudgetState.HEALTHY) {
            add(
                RuntimeGovernanceActionPlanItem(
                    actionKind = RuntimeGovernanceActionKind.REVIEW_BUDGET_PRESSURE,
                    target = RuntimeGovernanceActionTarget.SYSTEM,
                    targetId = budgetPressure.overallState.name,
                    sourceLayer = "BudgetPressure",
                    severity = budgetPressure.overallState.severity().coerceAtLeast(1),
                    reason = budgetPressure.recommendation.name
                )
            )
        }
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addLaneItems(
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot
    ) {
        laneAdmission.lanes
            .filter {
                it.state == RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY ||
                    it.state == RuntimeLaneAdmissionState.BACKGROUND_LIMITED ||
                    it.state == RuntimeLaneAdmissionState.SATURATED ||
                    it.state == RuntimeLaneAdmissionState.NO_CAPACITY
            }
            .forEach { lane ->
                val actionKind = if (lane.state == RuntimeLaneAdmissionState.NO_CAPACITY) {
                    RuntimeGovernanceActionKind.REVIEW_NO_CAPACITY
                } else {
                    RuntimeGovernanceActionKind.REVIEW_LANE_LIMIT
                }
                add(
                    RuntimeGovernanceActionPlanItem(
                        actionKind = actionKind,
                        target = RuntimeGovernanceActionTarget.LANE,
                        targetId = lane.lane.name,
                        sourceLayer = "LaneAdmission",
                        lane = lane.lane,
                        priority = lane.priority,
                        severity = lane.state.severity(),
                        reason = lane.reason
                    )
                )
            }
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addQueueItems(
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot
    ) {
        startQueuePlan.entries.forEach { entry ->
            val actionKind = when (entry.disposition) {
                RuntimeStartQueueDisposition.RUN_IMMEDIATELY -> null
                RuntimeStartQueueDisposition.WATCH_ONLY -> RuntimeGovernanceActionKind.WATCH_START_INTENT
                RuntimeStartQueueDisposition.WOULD_QUEUE -> RuntimeGovernanceActionKind.WOULD_QUEUE_START
                RuntimeStartQueueDisposition.DEFER_UNTIL_PRESSURE_EASES ->
                    RuntimeGovernanceActionKind.HOLD_START_UNTIL_PRESSURE_EASES
                RuntimeStartQueueDisposition.REQUIRE_FOREGROUND ->
                    RuntimeGovernanceActionKind.HOLD_START_UNTIL_FOREGROUND
                RuntimeStartQueueDisposition.BLOCKED_NO_CAPACITY ->
                    RuntimeGovernanceActionKind.REVIEW_NO_CAPACITY
            } ?: return@forEach
            add(
                RuntimeGovernanceActionPlanItem(
                    actionKind = actionKind,
                    target = RuntimeGovernanceActionTarget.INTENT,
                    targetId = entry.intentKind.name,
                    sourceLayer = "StartQueuePlan",
                    lane = entry.lane,
                    workloadClass = entry.workloadClass,
                    priority = entry.priority,
                    dryRunRank = entry.dryRunRank,
                    severity = entry.disposition.severity(),
                    reason = entry.reason
                )
            )
        }
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addBudgetItems(
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ) {
        budgetPressure.candidates.forEach { candidate ->
            add(
                RuntimeGovernanceActionPlanItem(
                    actionKind = RuntimeGovernanceActionKind.REVIEW_BUDGET_PRESSURE,
                    target = RuntimeGovernanceActionTarget.WORKLOAD,
                    targetId = candidate.workloadId,
                    sourceLayer = "BudgetPressure",
                    lane = candidate.lane,
                    workloadClass = candidate.workloadClass,
                    severity = candidate.state.severity(),
                    reason = candidate.reason
                )
            )
        }
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addWorkloadRegistryItems(
        workloadRegistry: RuntimeWorkloadRegistrySnapshot
    ) {
        workloadRegistry.entries
            .filter {
                it.retention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE ||
                    it.workloadClass == RuntimeWorkloadClass.STRAY ||
                    it.workloadClass == RuntimeWorkloadClass.UNKNOWN ||
                    it.overChildBudget
            }
            .forEach { entry ->
                add(
                    RuntimeGovernanceActionPlanItem(
                        actionKind = RuntimeGovernanceActionKind.REVIEW_CLEANUP_CANDIDATE,
                        target = RuntimeGovernanceActionTarget.WORKLOAD,
                        targetId = entry.workloadId,
                        sourceLayer = "WorkloadRegistry",
                        lane = entry.suggestedLane,
                        workloadClass = entry.workloadClass,
                        severity = if (entry.overChildBudget) 3 else 2,
                        reason = entry.reason
                    )
                )
            }
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addLifecycleIntentItems(
        lifecycleIntentSurface: RuntimeLifecycleIntentSurfaceDryRunSnapshot
    ) {
        lifecycleIntentSurface.entries.forEach { entry ->
            val actionKind = if (entry.directActionRequested) {
                RuntimeGovernanceActionKind.REVIEW_REJECTED_DIRECT_INTENT
            } else if (entry.advisoryAccepted) {
                RuntimeGovernanceActionKind.REVIEW_LIFECYCLE_ADVISORY_INTENT
            } else {
                RuntimeGovernanceActionKind.REVIEW_REJECTED_DIRECT_INTENT
            }
            add(
                RuntimeGovernanceActionPlanItem(
                    actionKind = actionKind,
                    target = RuntimeGovernanceActionTarget.INTENT,
                    targetId = entry.intentId,
                    sourceLayer = "LifecycleIntentSurface",
                    lane = entry.lane,
                    workloadClass = entry.workloadClass,
                    priority = entry.lane?.defaultPriority() ?: 100,
                    severity = if (entry.directActionRequested) 4 else 2,
                    reason = entry.reason
                )
            )
        }
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addLifecycleReclaimItems(
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot
    ) {
        if (lifecycleReclaimPlan.leasePoolOverBudget &&
            lifecycleReclaimPlan.leasePoolEvictionCandidateCount > 0
        ) {
            add(
                RuntimeGovernanceActionPlanItem(
                    actionKind = RuntimeGovernanceActionKind.REVIEW_LEASE_POOL_EVICTION,
                    target = RuntimeGovernanceActionTarget.WORKLOAD,
                    targetId = "LEASE_POOL",
                    sourceLayer = "LifecycleReclaimPlan",
                    severity = 4,
                    reason = "lease_pool_over_budget:rss_${lifecycleReclaimPlan.leasePoolRssKb},budget_${lifecycleReclaimPlan.leasePoolMemoryBudgetKb},candidates_${lifecycleReclaimPlan.leasePoolEvictionCandidateCount}"
                )
            )
        }
        lifecycleReclaimPlan.items.forEach { item ->
            val actionKind = when (item.disposition) {
                RuntimeLifecycleReclaimDisposition.KEEP -> null
                RuntimeLifecycleReclaimDisposition.WATCH_LEASE ->
                    RuntimeGovernanceActionKind.WATCH_LIFECYCLE_LEASE
                RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE ->
                    RuntimeGovernanceActionKind.REVIEW_EXPIRE_LEASE
                RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP ->
                    RuntimeGovernanceActionKind.REVIEW_CLEANUP_CANDIDATE
                RuntimeLifecycleReclaimDisposition.WOULD_RECLAIM_CHILDREN ->
                    RuntimeGovernanceActionKind.REVIEW_RECLAIM_CHILDREN
                RuntimeLifecycleReclaimDisposition.WOULD_RESTART_MAIN ->
                    RuntimeGovernanceActionKind.REVIEW_RESTART_MAIN
                RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD ->
                    RuntimeGovernanceActionKind.REVIEW_TERMINATE_WORKLOAD
                RuntimeLifecycleReclaimDisposition.WOULD_QUARANTINE,
                RuntimeLifecycleReclaimDisposition.MANUAL_ONLY_QUARANTINED ->
                    RuntimeGovernanceActionKind.REVIEW_QUARANTINE
            } ?: return@forEach
            add(
                RuntimeGovernanceActionPlanItem(
                    actionKind = actionKind,
                    target = RuntimeGovernanceActionTarget.WORKLOAD,
                    targetId = item.workloadId,
                    sourceLayer = "LifecycleReclaimPlan",
                    lane = item.lane,
                    workloadClass = item.workloadClass,
                    severity = item.disposition.severity(),
                    reason = item.reason
                )
            )
        }
    }

    private fun MutableList<RuntimeGovernanceActionPlanItem>.addProotCapacityItem(
        lifecycleProotExpansionBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot
    ) {
        if (!lifecycleProotExpansionBudget.capacityReviewNeeded) return
        val actionKind = when (lifecycleProotExpansionBudget.requestedAction) {
            "REQUEST_NEXT_PROOT" -> RuntimeGovernanceActionKind.REVIEW_PROOT_EXPANSION_REQUEST
            "REQUEST_SECOND_PROOT" -> RuntimeGovernanceActionKind.REVIEW_PROOT_EXPANSION_REQUEST
            "REQUEST_SINGLE_PROOT_QUEUE" -> RuntimeGovernanceActionKind.REVIEW_PROOT_QUEUE_REQUEST
            "REQUEST_PROOT_DOWNSCALE" -> RuntimeGovernanceActionKind.REVIEW_PROOT_DOWNSCALE_REQUEST
            else -> RuntimeGovernanceActionKind.REVIEW_PROOT_QUEUE_REQUEST
        }
        val severity = when {
            !lifecycleProotExpansionBudget.lifecycleApprovesRequestedAction -> 5
            actionKind == RuntimeGovernanceActionKind.REVIEW_PROOT_EXPANSION_REQUEST -> 3
            else -> 2
        }
        add(
            RuntimeGovernanceActionPlanItem(
                actionKind = actionKind,
                target = RuntimeGovernanceActionTarget.PROOT,
                targetId = lifecycleProotExpansionBudget.requestedAction,
                sourceLayer = "LifecycleProotCapacityBudget",
                severity = severity,
                reason = lifecycleProotExpansionBudget.reason
            )
        )
    }

    private fun RuntimeGovernanceActionKind.toRecommendation(): RuntimeGovernanceActionRecommendation {
        return when (this) {
            RuntimeGovernanceActionKind.WATCH_SYSTEM,
            RuntimeGovernanceActionKind.WATCH_START_INTENT,
            RuntimeGovernanceActionKind.WATCH_LIFECYCLE_LEASE -> RuntimeGovernanceActionRecommendation.REVIEW_WATCHLIST
            RuntimeGovernanceActionKind.HOLD_START_UNTIL_PRESSURE_EASES ->
                RuntimeGovernanceActionRecommendation.REVIEW_PRESSURE_HOLDS
            RuntimeGovernanceActionKind.HOLD_START_UNTIL_FOREGROUND ->
                RuntimeGovernanceActionRecommendation.REVIEW_FOREGROUND_HOLDS
            RuntimeGovernanceActionKind.WOULD_QUEUE_START ->
                RuntimeGovernanceActionRecommendation.REVIEW_QUEUE_PLAN
            RuntimeGovernanceActionKind.REVIEW_LANE_LIMIT,
            RuntimeGovernanceActionKind.REVIEW_BUDGET_PRESSURE,
            RuntimeGovernanceActionKind.REVIEW_TELEMETRY_HEALTH ->
                RuntimeGovernanceActionRecommendation.REVIEW_BUDGET_ACTIONS
            RuntimeGovernanceActionKind.REVIEW_EXPIRE_LEASE,
            RuntimeGovernanceActionKind.REVIEW_LEASE_POOL_EVICTION,
            RuntimeGovernanceActionKind.REVIEW_CLEANUP_CANDIDATE,
            RuntimeGovernanceActionKind.REVIEW_RECLAIM_CHILDREN,
            RuntimeGovernanceActionKind.REVIEW_RESTART_MAIN,
            RuntimeGovernanceActionKind.REVIEW_TERMINATE_WORKLOAD,
            RuntimeGovernanceActionKind.REVIEW_QUARANTINE ->
                RuntimeGovernanceActionRecommendation.REVIEW_LIFECYCLE_RECLAIM
            RuntimeGovernanceActionKind.REVIEW_LIFECYCLE_ADVISORY_INTENT,
            RuntimeGovernanceActionKind.REVIEW_REJECTED_DIRECT_INTENT ->
                RuntimeGovernanceActionRecommendation.REVIEW_LIFECYCLE_INTENTS
            RuntimeGovernanceActionKind.REVIEW_PROOT_EXPANSION_REQUEST,
            RuntimeGovernanceActionKind.REVIEW_PROOT_QUEUE_REQUEST,
            RuntimeGovernanceActionKind.REVIEW_PROOT_DOWNSCALE_REQUEST ->
                RuntimeGovernanceActionRecommendation.REVIEW_PROOT_CAPACITY
            RuntimeGovernanceActionKind.REVIEW_NO_CAPACITY ->
                RuntimeGovernanceActionRecommendation.REVIEW_NO_CAPACITY
        }
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

    private fun RuntimeLaneAdmissionState.severity(): Int {
        return when (this) {
            RuntimeLaneAdmissionState.OPEN -> 0
            RuntimeLaneAdmissionState.WATCH -> 1
            RuntimeLaneAdmissionState.SATURATED -> 2
            RuntimeLaneAdmissionState.BACKGROUND_LIMITED -> 3
            RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY -> 4
            RuntimeLaneAdmissionState.NO_CAPACITY -> 5
        }
    }

    private fun RuntimeStartQueueDisposition.severity(): Int {
        return when (this) {
            RuntimeStartQueueDisposition.RUN_IMMEDIATELY -> 0
            RuntimeStartQueueDisposition.WATCH_ONLY -> 1
            RuntimeStartQueueDisposition.WOULD_QUEUE -> 2
            RuntimeStartQueueDisposition.REQUIRE_FOREGROUND -> 3
            RuntimeStartQueueDisposition.DEFER_UNTIL_PRESSURE_EASES -> 4
            RuntimeStartQueueDisposition.BLOCKED_NO_CAPACITY -> 5
        }
    }

    private fun RuntimeGovernanceActionRecommendation.severity(): Int {
        return when (this) {
            RuntimeGovernanceActionRecommendation.OBSERVE_ONLY -> 0
            RuntimeGovernanceActionRecommendation.REVIEW_WATCHLIST -> 1
            RuntimeGovernanceActionRecommendation.REVIEW_QUEUE_PLAN -> 2
            RuntimeGovernanceActionRecommendation.REVIEW_FOREGROUND_HOLDS -> 3
            RuntimeGovernanceActionRecommendation.REVIEW_PRESSURE_HOLDS -> 4
            RuntimeGovernanceActionRecommendation.REVIEW_BUDGET_ACTIONS -> 5
            RuntimeGovernanceActionRecommendation.REVIEW_LIFECYCLE_RECLAIM -> 5
            RuntimeGovernanceActionRecommendation.REVIEW_LIFECYCLE_INTENTS -> 5
            RuntimeGovernanceActionRecommendation.REVIEW_PROOT_CAPACITY -> 5
            RuntimeGovernanceActionRecommendation.REVIEW_NO_CAPACITY -> 6
        }
    }

    private fun RuntimeLifecycleReclaimDisposition.severity(): Int {
        return when (this) {
            RuntimeLifecycleReclaimDisposition.KEEP -> 0
            RuntimeLifecycleReclaimDisposition.WATCH_LEASE -> 1
            RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE -> 3
            RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP -> 4
            RuntimeLifecycleReclaimDisposition.WOULD_RECLAIM_CHILDREN -> 5
            RuntimeLifecycleReclaimDisposition.WOULD_RESTART_MAIN -> 6
            RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD -> 7
            RuntimeLifecycleReclaimDisposition.WOULD_QUARANTINE -> 8
            RuntimeLifecycleReclaimDisposition.MANUAL_ONLY_QUARANTINED -> 8
        }
    }
}

private fun RuntimeLaneKind.defaultPriority(): Int {
    return when (this) {
        RuntimeLaneKind.INTERACTIVE -> 0
        RuntimeLaneKind.SERVICE -> 20
        RuntimeLaneKind.BUILD -> 60
        RuntimeLaneKind.PROBE -> 90
    }
}

private fun String?.toGovernanceActionEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
