package com.kite.app.foundation.runtime

enum class RuntimeLifecycleProotExpansionBudgetState {
    NOT_REQUESTED,
    BUDGET_AVAILABLE,
    RECLAIM_REVIEW,
    QUEUE_FOR_MEMORY,
    BLOCKED_BY_PRESSURE,
    QUEUE_REVIEW,
    DOWNSCALE_REVIEW
}

enum class RuntimeLifecycleProotExpansionBudgetRecommendation {
    KEEP_SINGLE_PROOT,
    ALLOW_NEXT_PROOT_BUDGET,
    REVIEW_RECLAIM_BEFORE_NEXT_PROOT,
    QUEUE_UNTIL_MEMORY_RECOVERS,
    HOLD_PROOT_EXPANSION,
    REVIEW_SINGLE_PROOT_QUEUE,
    REVIEW_PROOT_DOWNSCALE
}

data class RuntimeLifecycleProotExpansionBudgetDryRunSnapshot(
    val mode: String = "lifecycle_proot_expansion_budget_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeLifecycleProotExpansionBudgetState =
        RuntimeLifecycleProotExpansionBudgetState.NOT_REQUESTED,
    val recommendation: RuntimeLifecycleProotExpansionBudgetRecommendation =
        RuntimeLifecycleProotExpansionBudgetRecommendation.KEEP_SINGLE_PROOT,
    val capacityContract: String =
        "proot_requests_capacity_lifecycle_reviews_budget_android_executes",
    val capacityRole: String =
        "proot_special_capacity_budget_consumer_not_lifecycle_reclaim_pool_member",
    val expansionDecisionFormula: String =
        "queue_until_single_proot_peak_then_review_next_proot_memory_budget",
    val secondProotMemoryFormula: String =
        "base_proot_memory_plus_estimated_task_memory_x_peak_tasks_plus_safety_margin",
    val lifecycleBudgetGate: String =
        "host_available_minus_safety_reserve_must_cover_next_peak_memory",
    val actualControlBoundary: String =
        "android_capacity_executor_only_no_ubuntu_direct_proot_control",
    val requestedAction: String = "NONE",
    val capacityReviewNeeded: Boolean = false,
    val expansionRequested: Boolean = false,
    val queueRequested: Boolean = false,
    val downscaleRequested: Boolean = false,
    val downlineRequested: Boolean = false,
    val requestReason: String = "single_proot_within_peak_budget",
    val currentTracees: Int = 0,
    val singleProotPeakTracees: Int = 0,
    val queueUntilTracees: Int = 0,
    val secondProotTriggerTracees: Int = 0,
    val overflowTracees: Int = 0,
    val memoryWorkerRssKb: Long = 0L,
    val baseProotMemoryKb: Long = 0L,
    val estimatedTaskMemoryKb: Long = 0L,
    val safetyMarginKb: Long = 0L,
    val reservedProotMemoryKb: Long = 0L,
    val comfortMemoryBudgetKb: Long = 0L,
    val peakMemoryBudgetKb: Long = 0L,
    val secondProotRequiredMemoryKb: Long = 0L,
    val hostMemTotalKb: Long = 0L,
    val hostMemAvailableKb: Long = 0L,
    val hostSafetyReserveKb: Long = 0L,
    val globalMemoryLedgerAvailableForProotKb: Long = 0L,
    val globalMemoryLedgerLeasePoolBudgetKb: Long = 0L,
    val globalMemoryLedgerLeasePoolOverBudget: Boolean = false,
    val globalMemoryLedgerReason: String = "waiting_for_global_memory_ledger",
    val hostMemoryAfterReserveKb: Long = 0L,
    val hostMemoryAfterSecondProotKb: Long = 0L,
    val hostAvailableLevel: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val rssPressureLevel: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val lifecyclePendingReviewCount: Int = 0,
    val backgroundShrinkCandidateCount: Int = 0,
    val idleReclaimCandidateCount: Int = 0,
    val lifecycleReclaimNeededBeforeExpansion: Boolean = false,
    val canReserveForSecondProot: Boolean = false,
    val lifecycleApprovesRequestedAction: Boolean = false,
    val actualSecondProotStartCount: Int = 0,
    val actualProotDownlineCount: Int = 0,
    val actualQueueCreationCount: Int = 0,
    val actualLifecycleReclaimCount: Int = 0,
    val executionOwner: String = "android_control_plane",
    val androidExecutorStatus: String = "budget_review_passes_to_proot_capacity_executor_policy_gate",
    val ubuntuDirectCapacityControlAllowed: Boolean = false,
    val prootDirectCapacityControlAllowed: Boolean = false,
    val decision: String = "KEEP_SINGLE_PROOT",
    val reason: String = "waiting_for_proot_expansion_inputs"
) {
    fun summary(): String {
        return "mode=$mode state=$state action=$requestedAction requested=$capacityReviewNeeded " +
            "expand=$expansionRequested queue=$queueRequested downscale=$downscaleRequested current=$currentTracees " +
            "peak=$singleProotPeakTracees trigger=$secondProotTriggerTracees " +
            "needMem=$secondProotRequiredMemoryKb avail=$hostMemAvailableKb " +
            "canReserve=$canReserveForSecondProot approved=$lifecycleApprovesRequestedAction " +
            "pending=$lifecyclePendingReviewCount " +
            "decision=$decision enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("lifecycle_proot_expansion_budget_mode=${mode.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_enforcement_mode=${enforcementMode.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_enforcement_enabled=$enforcementEnabled")
            appendLine("lifecycle_proot_expansion_budget_generated_at=$generatedAtMs")
            appendLine("lifecycle_proot_expansion_budget_state=${state.name}")
            appendLine("lifecycle_proot_expansion_budget_recommendation=${recommendation.name}")
            appendLine("lifecycle_proot_expansion_budget_capacity_contract=${capacityContract.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_capacity_role=${capacityRole.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_expansion_decision_formula=${expansionDecisionFormula.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_second_proot_memory_formula=${secondProotMemoryFormula.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_next_proot_memory_formula=${secondProotMemoryFormula.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_lifecycle_budget_gate=${lifecycleBudgetGate.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_actual_control_boundary=${actualControlBoundary.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_requested_action=${requestedAction.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_capacity_review_needed=$capacityReviewNeeded")
            appendLine("lifecycle_proot_expansion_budget_requested=$expansionRequested")
            appendLine("lifecycle_proot_expansion_budget_queue_requested=$queueRequested")
            appendLine("lifecycle_proot_expansion_budget_downscale_requested=$downscaleRequested")
            appendLine("lifecycle_proot_expansion_budget_downline_requested=$downlineRequested")
            appendLine("lifecycle_proot_expansion_budget_request_reason=${requestReason.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_current_tracees=$currentTracees")
            appendLine("lifecycle_proot_expansion_budget_single_proot_peak_tracees=$singleProotPeakTracees")
            appendLine("lifecycle_proot_expansion_budget_queue_until_tracees=$queueUntilTracees")
            appendLine("lifecycle_proot_expansion_budget_second_proot_trigger_tracees=$secondProotTriggerTracees")
            appendLine("lifecycle_proot_expansion_budget_next_proot_trigger_tracees=$secondProotTriggerTracees")
            appendLine("lifecycle_proot_expansion_budget_overflow_tracees=$overflowTracees")
            appendLine("lifecycle_proot_expansion_budget_memory_worker_rss_kb=$memoryWorkerRssKb")
            appendLine("lifecycle_proot_expansion_budget_base_proot_memory_kb=$baseProotMemoryKb")
            appendLine("lifecycle_proot_expansion_budget_estimated_task_memory_kb=$estimatedTaskMemoryKb")
            appendLine("lifecycle_proot_expansion_budget_safety_margin_kb=$safetyMarginKb")
            appendLine("lifecycle_proot_expansion_budget_reserved_proot_memory_kb=$reservedProotMemoryKb")
            appendLine("lifecycle_proot_expansion_budget_comfort_memory_budget_kb=$comfortMemoryBudgetKb")
            appendLine("lifecycle_proot_expansion_budget_peak_memory_budget_kb=$peakMemoryBudgetKb")
            appendLine("lifecycle_proot_expansion_budget_second_proot_required_memory_kb=$secondProotRequiredMemoryKb")
            appendLine("lifecycle_proot_expansion_budget_next_proot_required_memory_kb=$secondProotRequiredMemoryKb")
            appendLine("lifecycle_proot_expansion_budget_host_mem_total_kb=$hostMemTotalKb")
            appendLine("lifecycle_proot_expansion_budget_host_mem_available_kb=$hostMemAvailableKb")
            appendLine("lifecycle_proot_expansion_budget_host_safety_reserve_kb=$hostSafetyReserveKb")
            appendLine("lifecycle_proot_expansion_budget_global_memory_ledger_available_for_proot_kb=$globalMemoryLedgerAvailableForProotKb")
            appendLine("lifecycle_proot_expansion_budget_global_memory_ledger_lease_pool_budget_kb=$globalMemoryLedgerLeasePoolBudgetKb")
            appendLine("lifecycle_proot_expansion_budget_global_memory_ledger_lease_pool_over_budget=$globalMemoryLedgerLeasePoolOverBudget")
            appendLine("lifecycle_proot_expansion_budget_global_memory_ledger_reason=${globalMemoryLedgerReason.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_host_memory_after_reserve_kb=$hostMemoryAfterReserveKb")
            appendLine("lifecycle_proot_expansion_budget_host_memory_after_second_proot_kb=$hostMemoryAfterSecondProotKb")
            appendLine("lifecycle_proot_expansion_budget_host_available_level=${hostAvailableLevel.name}")
            appendLine("lifecycle_proot_expansion_budget_rss_pressure_level=${rssPressureLevel.name}")
            appendLine("lifecycle_proot_expansion_budget_budget_overall_state=${budgetOverallState.name}")
            appendLine("lifecycle_proot_expansion_budget_lifecycle_pending_review_count=$lifecyclePendingReviewCount")
            appendLine("lifecycle_proot_expansion_budget_background_shrink_candidate_count=$backgroundShrinkCandidateCount")
            appendLine("lifecycle_proot_expansion_budget_idle_reclaim_candidate_count=$idleReclaimCandidateCount")
            appendLine("lifecycle_proot_expansion_budget_reclaim_needed_before_expansion=$lifecycleReclaimNeededBeforeExpansion")
            appendLine("lifecycle_proot_expansion_budget_can_reserve_for_second_proot=$canReserveForSecondProot")
            appendLine("lifecycle_proot_expansion_budget_can_reserve_for_next_proot=$canReserveForSecondProot")
            appendLine("lifecycle_proot_expansion_budget_lifecycle_approves_requested_action=$lifecycleApprovesRequestedAction")
            appendLine("lifecycle_proot_expansion_budget_actual_second_proot_start_count=$actualSecondProotStartCount")
            appendLine("lifecycle_proot_expansion_budget_actual_next_proot_start_count=$actualSecondProotStartCount")
            appendLine("lifecycle_proot_expansion_budget_actual_proot_downline_count=$actualProotDownlineCount")
            appendLine("lifecycle_proot_expansion_budget_actual_queue_creation_count=$actualQueueCreationCount")
            appendLine("lifecycle_proot_expansion_budget_actual_lifecycle_reclaim_count=$actualLifecycleReclaimCount")
            appendLine("lifecycle_proot_expansion_budget_execution_owner=${executionOwner.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_android_executor_status=${androidExecutorStatus.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_ubuntu_direct_capacity_control_allowed=$ubuntuDirectCapacityControlAllowed")
            appendLine("lifecycle_proot_expansion_budget_proot_direct_capacity_control_allowed=$prootDirectCapacityControlAllowed")
            appendLine("lifecycle_proot_expansion_budget_decision=${decision.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_reason=${reason.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_expansion_budget_boundary=observe_only_no_next_proot_start_no_proot_downline_no_reclaim_no_queue_no_enforcement")
            appendLine("lifecycle_proot_capacity_budget_mode=${mode.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_state=${state.name}")
            appendLine("lifecycle_proot_capacity_budget_recommendation=${recommendation.name}")
            appendLine("lifecycle_proot_capacity_budget_contract=${capacityContract.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_role=${capacityRole.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_expansion_decision_formula=${expansionDecisionFormula.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_second_proot_memory_formula=${secondProotMemoryFormula.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_next_proot_memory_formula=${secondProotMemoryFormula.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_lifecycle_budget_gate=${lifecycleBudgetGate.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_actual_control_boundary=${actualControlBoundary.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_requested_action=${requestedAction.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_review_needed=$capacityReviewNeeded")
            appendLine("lifecycle_proot_capacity_budget_expansion_requested=$expansionRequested")
            appendLine("lifecycle_proot_capacity_budget_queue_requested=$queueRequested")
            appendLine("lifecycle_proot_capacity_budget_downscale_requested=$downscaleRequested")
            appendLine("lifecycle_proot_capacity_budget_downline_requested=$downlineRequested")
            appendLine("lifecycle_proot_capacity_budget_lifecycle_approves_requested_action=$lifecycleApprovesRequestedAction")
            appendLine("lifecycle_proot_capacity_budget_actual_second_proot_start_count=$actualSecondProotStartCount")
            appendLine("lifecycle_proot_capacity_budget_actual_next_proot_start_count=$actualSecondProotStartCount")
            appendLine("lifecycle_proot_capacity_budget_actual_proot_downline_count=$actualProotDownlineCount")
            appendLine("lifecycle_proot_capacity_budget_actual_queue_creation_count=$actualQueueCreationCount")
            appendLine("lifecycle_proot_capacity_budget_actual_lifecycle_reclaim_count=$actualLifecycleReclaimCount")
            appendLine("lifecycle_proot_capacity_budget_execution_owner=${executionOwner.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_android_executor_status=${androidExecutorStatus.toLifecycleProotBudgetEnvValue()}")
            appendLine("lifecycle_proot_capacity_budget_ubuntu_direct_capacity_control_allowed=$ubuntuDirectCapacityControlAllowed")
            appendLine("lifecycle_proot_capacity_budget_proot_direct_capacity_control_allowed=$prootDirectCapacityControlAllowed")
            appendLine("lifecycle_proot_capacity_budget_boundary=observe_only_no_next_proot_start_no_proot_downline_no_reclaim_no_queue_no_enforcement")
        }
    }
}

object RuntimeLifecycleProotExpansionBudgetDryRun {
    private const val MIN_HOST_SAFETY_RESERVE_KB = 512L * 1024L
    private const val HOST_SAFETY_RESERVE_PERCENT = 15

    fun evaluate(
        pressure: RuntimePressureSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        prootPoolPlan: RuntimeProotPoolPlanDryRunSnapshot,
        capacityPolicy: RuntimeProotCapacityExecutorPolicy = RuntimeProotCapacityExecutorPolicy(),
        now: Long = System.currentTimeMillis()
    ): RuntimeLifecycleProotExpansionBudgetDryRunSnapshot {
        val currentTracees = prootPoolPlan.liveTraceeCount
        val peakTracees = prootPoolPlan.adaptiveStrategyPeakTracees.coerceAtLeast(1)
        val queueUntilTracees = prootPoolPlan.adaptiveStrategyQueueUntilTracees.coerceAtLeast(peakTracees)
        val secondTrigger = prootPoolPlan.adaptiveStrategySecondProotTriggerTracees
            .coerceAtLeast(peakTracees + 1)
        val memoryWorkerRssKb = prootPoolPlan.deviceCalibrationMemoryWorkerRssKb.coerceAtLeast(0L)
        val comfortMemoryBudgetKb = prootPoolPlan.deviceCalibrationHealthyStableTraceeCap
            .coerceAtLeast(1)
            .toLong() * memoryWorkerRssKb
        val peakMemoryBudgetKb = peakTracees.toLong() * memoryWorkerRssKb
        val hostTotalKb = pressure.hostMemTotalKb ?: 0L
        val hostAvailableKb = pressure.hostMemAvailableKb ?: 0L
        val hostSafetyReserveKb = maxOf(
            MIN_HOST_SAFETY_RESERVE_KB,
            (hostTotalKb * HOST_SAFETY_RESERVE_PERCENT / 100L).coerceAtLeast(0L)
        )
        val pendingReviewCount = lifecycleReclaimPlan.expireLeaseCount +
            lifecycleReclaimPlan.cleanupReviewCount +
            lifecycleReclaimPlan.reclaimChildrenReviewCount +
            lifecycleReclaimPlan.restartReviewCount +
            lifecycleReclaimPlan.terminateReviewCount +
            lifecycleReclaimPlan.quarantineReviewCount
        val requestedAction = prootPoolPlan.capacityRequestedAction
        val expansionRequested = prootPoolPlan.capacityExpansionRequested
        val queueRequested = prootPoolPlan.capacityQueueRequested
        val downscaleRequested = prootPoolPlan.capacityDownscaleRequested
        val downlineRequested = prootPoolPlan.capacityDownlineRequested
        val prootAdmissionSafetyMarginKb = maxOf(capacityPolicy.safetyMarginKb, hostSafetyReserveKb)
        val globalMemoryLedger = RuntimeProcessMemoryBudgetLedger.evaluate(
            pressure = pressure,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            prootReservedMemoryKb = RuntimeProotMemoryAdmission.reservedMemoryKb(),
            safetyMarginKb = prootAdmissionSafetyMarginKb
        )
        val capacityReviewNeeded = prootPoolPlan.capacityReviewNeeded
        val hostOrRuntimeMemoryPressure =
            pressure.hostAvailableLevel.ordinal >= RuntimePressureLevel.HIGH.ordinal ||
                pressure.level.ordinal >= RuntimePressureLevel.HIGH.ordinal ||
                budgetPressure.rssPressure.ordinal >= RuntimePressureLevel.HIGH.ordinal
        val leasePoolBudgetPressure = globalMemoryLedger.leasePoolOverBudget
        val memorySignalsOk = !hostOrRuntimeMemoryPressure && !leasePoolBudgetPressure
        val memoryAdmission = RuntimeProotMemoryAdmission.evaluate(
            expansionRequested = expansionRequested,
            hostAvailableKb = hostAvailableKb,
            peakTasks = peakTracees,
            defaultEstimatedTaskMemoryKb = memoryWorkerRssKb.coerceAtLeast(1L),
            policy = RuntimeProotMemoryAdmissionPolicy(
                baseProotMemoryKb = capacityPolicy.baseProotMemoryKb,
                estimatedTaskMemoryKb = capacityPolicy.estimatedTaskMemoryKb,
                safetyMarginKb = prootAdmissionSafetyMarginKb
            ),
            memorySignalsOk = memorySignalsOk,
            globalBudgetLedger = globalMemoryLedger
        )
        val secondProotRequiredMemoryKb = memoryAdmission.requiredMemoryKb
        val afterReserveKb = memoryAdmission.availableAfterExistingReservationsKb
        val afterSecondProotKb = memoryAdmission.availableAfterNewReservationKb
        val canReserve = memoryAdmission.canReserve
        val reclaimNeeded = expansionRequested && !canReserve && pendingReviewCount > 0
        val state = when {
            !expansionRequested -> RuntimeLifecycleProotExpansionBudgetState.NOT_REQUESTED
            hostOrRuntimeMemoryPressure || leasePoolBudgetPressure ->
                RuntimeLifecycleProotExpansionBudgetState.BLOCKED_BY_PRESSURE
            canReserve -> RuntimeLifecycleProotExpansionBudgetState.BUDGET_AVAILABLE
            reclaimNeeded -> RuntimeLifecycleProotExpansionBudgetState.RECLAIM_REVIEW
            else -> RuntimeLifecycleProotExpansionBudgetState.QUEUE_FOR_MEMORY
        }
        val effectiveState = when {
            expansionRequested -> state
            downscaleRequested || downlineRequested -> RuntimeLifecycleProotExpansionBudgetState.DOWNSCALE_REVIEW
            queueRequested -> RuntimeLifecycleProotExpansionBudgetState.QUEUE_REVIEW
            else -> RuntimeLifecycleProotExpansionBudgetState.NOT_REQUESTED
        }
        val recommendation = when (effectiveState) {
            RuntimeLifecycleProotExpansionBudgetState.NOT_REQUESTED ->
                RuntimeLifecycleProotExpansionBudgetRecommendation.KEEP_SINGLE_PROOT
            RuntimeLifecycleProotExpansionBudgetState.BUDGET_AVAILABLE ->
                RuntimeLifecycleProotExpansionBudgetRecommendation.ALLOW_NEXT_PROOT_BUDGET
            RuntimeLifecycleProotExpansionBudgetState.RECLAIM_REVIEW ->
                RuntimeLifecycleProotExpansionBudgetRecommendation.REVIEW_RECLAIM_BEFORE_NEXT_PROOT
            RuntimeLifecycleProotExpansionBudgetState.QUEUE_FOR_MEMORY ->
                RuntimeLifecycleProotExpansionBudgetRecommendation.QUEUE_UNTIL_MEMORY_RECOVERS
            RuntimeLifecycleProotExpansionBudgetState.BLOCKED_BY_PRESSURE ->
                RuntimeLifecycleProotExpansionBudgetRecommendation.HOLD_PROOT_EXPANSION
            RuntimeLifecycleProotExpansionBudgetState.QUEUE_REVIEW ->
                RuntimeLifecycleProotExpansionBudgetRecommendation.REVIEW_SINGLE_PROOT_QUEUE
            RuntimeLifecycleProotExpansionBudgetState.DOWNSCALE_REVIEW ->
                RuntimeLifecycleProotExpansionBudgetRecommendation.REVIEW_PROOT_DOWNSCALE
        }
        val decision = when (effectiveState) {
            RuntimeLifecycleProotExpansionBudgetState.NOT_REQUESTED -> "KEEP_SINGLE_PROOT"
            RuntimeLifecycleProotExpansionBudgetState.BUDGET_AVAILABLE -> "ALLOW_NEXT_PROOT_BUDGET"
            RuntimeLifecycleProotExpansionBudgetState.RECLAIM_REVIEW -> "REVIEW_RECLAIM_BEFORE_NEXT_PROOT"
            RuntimeLifecycleProotExpansionBudgetState.QUEUE_FOR_MEMORY -> "QUEUE_UNTIL_MEMORY_RECOVERS"
            RuntimeLifecycleProotExpansionBudgetState.BLOCKED_BY_PRESSURE -> "HOLD_PROOT_EXPANSION"
            RuntimeLifecycleProotExpansionBudgetState.QUEUE_REVIEW -> "KEEP_QUEUE_ON_SINGLE_PROOT"
            RuntimeLifecycleProotExpansionBudgetState.DOWNSCALE_REVIEW -> "REVIEW_PROOT_DOWNSCALE"
        }
        val approvesRequestedAction = when (effectiveState) {
            RuntimeLifecycleProotExpansionBudgetState.BUDGET_AVAILABLE,
            RuntimeLifecycleProotExpansionBudgetState.QUEUE_REVIEW,
            RuntimeLifecycleProotExpansionBudgetState.DOWNSCALE_REVIEW -> true
            else -> false
        }
        return RuntimeLifecycleProotExpansionBudgetDryRunSnapshot(
            generatedAtMs = now,
            state = effectiveState,
            recommendation = recommendation,
            requestedAction = requestedAction,
            capacityReviewNeeded = capacityReviewNeeded,
            expansionRequested = expansionRequested,
            queueRequested = queueRequested,
            downscaleRequested = downscaleRequested,
            downlineRequested = downlineRequested,
            requestReason = prootPoolPlan.capacityRequestReason,
            currentTracees = currentTracees,
            singleProotPeakTracees = peakTracees,
            queueUntilTracees = queueUntilTracees,
            secondProotTriggerTracees = secondTrigger,
            overflowTracees = (currentTracees - peakTracees).coerceAtLeast(0),
            memoryWorkerRssKb = memoryWorkerRssKb,
            baseProotMemoryKb = memoryAdmission.baseProotMemoryKb,
            estimatedTaskMemoryKb = memoryAdmission.estimatedTaskMemoryKb,
            safetyMarginKb = memoryAdmission.safetyMarginKb,
            reservedProotMemoryKb = memoryAdmission.reservedMemoryKb,
            comfortMemoryBudgetKb = comfortMemoryBudgetKb,
            peakMemoryBudgetKb = peakMemoryBudgetKb,
            secondProotRequiredMemoryKb = secondProotRequiredMemoryKb,
            hostMemTotalKb = hostTotalKb,
            hostMemAvailableKb = hostAvailableKb,
            hostSafetyReserveKb = hostSafetyReserveKb,
            globalMemoryLedgerAvailableForProotKb =
                globalMemoryLedger.availableForElasticProotBeforeProotReservationsKb,
            globalMemoryLedgerLeasePoolBudgetKb = globalMemoryLedger.leasePoolBudgetKb,
            globalMemoryLedgerLeasePoolOverBudget = globalMemoryLedger.leasePoolOverBudget,
            globalMemoryLedgerReason = globalMemoryLedger.reason,
            hostMemoryAfterReserveKb = afterReserveKb,
            hostMemoryAfterSecondProotKb = afterSecondProotKb,
            hostAvailableLevel = pressure.hostAvailableLevel,
            rssPressureLevel = pressure.level,
            budgetOverallState = budgetPressure.overallState,
            lifecyclePendingReviewCount = pendingReviewCount,
            backgroundShrinkCandidateCount = prootPoolPlan.backgroundShrinkLaneCount,
            idleReclaimCandidateCount = prootPoolPlan.idleReclaimCandidateCount,
            lifecycleReclaimNeededBeforeExpansion = reclaimNeeded,
            canReserveForSecondProot = canReserve,
            lifecycleApprovesRequestedAction = approvesRequestedAction,
            decision = decision,
            reason = "action=$requestedAction,review=$capacityReviewNeeded,expand=$expansionRequested," +
                "queue=$queueRequested,downscale=$downscaleRequested,current=$currentTracees,peak=$peakTracees," +
                "trigger=$secondTrigger,needKb=$secondProotRequiredMemoryKb," +
                "availableKb=$hostAvailableKb,reserveKb=$hostSafetyReserveKb," +
                "pending=$pendingReviewCount,budget=${budgetPressure.overallState.name}," +
                "host=${pressure.hostAvailableLevel.name},rss=${pressure.level.name}," +
                "memoryAdmission=${memoryAdmission.reason}"
        )
    }
}

private fun RuntimeBudgetState.severityForProotBudget(): Int {
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

private fun String?.toLifecycleProotBudgetEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(220)
}
