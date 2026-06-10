package com.kftest.app.foundation.runtime

enum class RuntimeStartQueueDisposition {
    RUN_IMMEDIATELY,
    WATCH_ONLY,
    WOULD_QUEUE,
    DEFER_UNTIL_PRESSURE_EASES,
    REQUIRE_FOREGROUND,
    BLOCKED_NO_CAPACITY
}

enum class RuntimeStartQueueRecommendation {
    OBSERVE_ONLY,
    KEEP_QUEUE_EMPTY,
    REVIEW_WATCHLIST,
    REVIEW_QUEUE_ORDER,
    REVIEW_DEFERRED_STARTS,
    REVIEW_FOREGROUND_REQUIRED,
    REVIEW_NO_CAPACITY
}

data class RuntimeStartQueuePlanEntry(
    val intentKind: RuntimeStartIntentKind,
    val workloadClass: RuntimeWorkloadClass,
    val retention: RuntimeWorkloadRetention,
    val lane: RuntimeLaneKind,
    val laneState: RuntimeLaneAdmissionState,
    val preflightDecision: RuntimeStartPreflightDecision,
    val disposition: RuntimeStartQueueDisposition,
    val recommendation: RuntimeStartQueueRecommendation,
    val priority: Int,
    val dryRunRank: Int,
    val activeWorkloads: Int,
    val effectiveMaxConcurrency: Int,
    val reason: String
)

data class RuntimeStartQueuePlanDryRunSnapshot(
    val mode: String = "start_queue_plan_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val recommendation: RuntimeStartQueueRecommendation = RuntimeStartQueueRecommendation.OBSERVE_ONLY,
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val startPreflightRecommendation: RuntimeStartPreflightRecommendation =
        RuntimeStartPreflightRecommendation.OBSERVE_ONLY,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val prootPressureScore: Int = 0,
    val sourceIntentCount: Int = 0,
    val runImmediatelyCount: Int = 0,
    val watchOnlyCount: Int = 0,
    val wouldQueueCount: Int = 0,
    val deferUntilPressureEasesCount: Int = 0,
    val foregroundRequiredCount: Int = 0,
    val blockedNoCapacityCount: Int = 0,
    val dryRunBacklogCount: Int = 0,
    val entries: List<RuntimeStartQueuePlanEntry> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode recommendation=$recommendation lifecycle=$lifecycleState " +
            "budget=$budgetOverallState preflight=${startPreflightRecommendation.name} " +
            "pressure=$pressureState/$prootPressureScore run=$runImmediatelyCount watch=$watchOnlyCount " +
            "queue=$wouldQueueCount defer=$deferUntilPressureEasesCount " +
            "foreground=$foregroundRequiredCount blocked=$blockedNoCapacityCount " +
            "backlog=$dryRunBacklogCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxEntries: Int = 8): String {
        return buildString {
            appendLine("start_queue_plan_mode=${mode.toStartQueueEnvValue()}")
            appendLine("start_queue_plan_enforcement_mode=${enforcementMode.toStartQueueEnvValue()}")
            appendLine("start_queue_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("start_queue_plan_generated_at=$generatedAtMs")
            appendLine("start_queue_plan_recommendation=${recommendation.name}")
            appendLine("start_queue_plan_lifecycle_state=${lifecycleState.name}")
            appendLine("start_queue_plan_background_phase=${backgroundPhase.name}")
            appendLine("start_queue_plan_budget_overall_state=${budgetOverallState.name}")
            appendLine("start_queue_plan_start_preflight_recommendation=${startPreflightRecommendation.name}")
            appendLine("start_queue_plan_pressure_state=${pressureState.name}")
            appendLine("start_queue_plan_proot_score=$prootPressureScore")
            appendLine("start_queue_plan_source_intent_count=$sourceIntentCount")
            appendLine("start_queue_plan_run_immediately_count=$runImmediatelyCount")
            appendLine("start_queue_plan_watch_only_count=$watchOnlyCount")
            appendLine("start_queue_plan_would_queue_count=$wouldQueueCount")
            appendLine("start_queue_plan_defer_until_pressure_eases_count=$deferUntilPressureEasesCount")
            appendLine("start_queue_plan_foreground_required_count=$foregroundRequiredCount")
            appendLine("start_queue_plan_blocked_no_capacity_count=$blockedNoCapacityCount")
            appendLine("start_queue_plan_dry_run_backlog_count=$dryRunBacklogCount")
            entries.take(maxEntries).forEachIndexed { index, entry ->
                val prefix = "start_queue_plan_entry_${index + 1}"
                appendLine("${prefix}_intent=${entry.intentKind.name}")
                appendLine("${prefix}_class=${entry.workloadClass.name}")
                appendLine("${prefix}_retention=${entry.retention.name}")
                appendLine("${prefix}_lane=${entry.lane.name}")
                appendLine("${prefix}_lane_state=${entry.laneState.name}")
                appendLine("${prefix}_preflight_decision=${entry.preflightDecision.name}")
                appendLine("${prefix}_disposition=${entry.disposition.name}")
                appendLine("${prefix}_recommendation=${entry.recommendation.name}")
                appendLine("${prefix}_priority=${entry.priority}")
                appendLine("${prefix}_dry_run_rank=${entry.dryRunRank}")
                appendLine("${prefix}_active=${entry.activeWorkloads}")
                appendLine("${prefix}_effective_max=${entry.effectiveMaxConcurrency}")
                appendLine("${prefix}_reason=${entry.reason.toStartQueueEnvValue()}")
            }
            appendLine("start_queue_plan_boundary=dry_run_no_queue_creation_no_start_no_defer_no_reject_no_lane_control")
        }
    }
}

object RuntimeStartQueuePlanDryRun {
    fun evaluate(
        startPreflight: RuntimeStartPreflightDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        policy: RuntimeWorkloadPolicy,
        now: Long = System.currentTimeMillis()
    ): RuntimeStartQueuePlanDryRunSnapshot {
        val lanePriorities = policy.lanes.withDefaultLanePriorities()
        val backlogIntents = startPreflight.intents
            .filter { it.decision.backlogEligible() }
            .sortedWith(
                compareBy<RuntimeStartPreflightIntent> { lanePriorities.getValue(it.lane) }
                    .thenBy { it.lane.ordinal }
                    .thenBy { it.intentKind.ordinal }
            )
            .mapIndexed { index, intent -> intent.intentKind to (index + 1) }
            .toMap()

        val entries = startPreflight.intents.map { intent ->
            val disposition = intent.decision.toDisposition()
            RuntimeStartQueuePlanEntry(
                intentKind = intent.intentKind,
                workloadClass = intent.workloadClass,
                retention = intent.retention,
                lane = intent.lane,
                laneState = intent.laneState,
                preflightDecision = intent.decision,
                disposition = disposition,
                recommendation = disposition.toRecommendation(),
                priority = lanePriorities.getValue(intent.lane),
                dryRunRank = backlogIntents[intent.intentKind] ?: 0,
                activeWorkloads = intent.activeWorkloads,
                effectiveMaxConcurrency = intent.effectiveMaxConcurrency,
                reason = intent.queueReason(disposition, laneAdmission)
            )
        }.sortedWith(
            compareByDescending<RuntimeStartQueuePlanEntry> { it.disposition.severity() }
                .thenBy { if (it.dryRunRank > 0) it.dryRunRank else Int.MAX_VALUE }
                .thenBy { it.priority }
                .thenBy { it.intentKind.ordinal }
        )

        val recommendation = entries
            .map { it.recommendation }
            .maxByOrNull { it.severity() }
            ?: RuntimeStartQueueRecommendation.OBSERVE_ONLY

        return RuntimeStartQueuePlanDryRunSnapshot(
            generatedAtMs = now,
            recommendation = recommendation,
            lifecycleState = backgroundDecay.lifecycleState,
            backgroundPhase = backgroundDecay.phase,
            budgetOverallState = budgetPressure.overallState,
            startPreflightRecommendation = startPreflight.recommendation,
            pressureState = pressureConsumer.state,
            prootPressureScore = pressureConsumer.prootPressureScore,
            sourceIntentCount = startPreflight.intentCount,
            runImmediatelyCount = entries.count { it.disposition == RuntimeStartQueueDisposition.RUN_IMMEDIATELY },
            watchOnlyCount = entries.count { it.disposition == RuntimeStartQueueDisposition.WATCH_ONLY },
            wouldQueueCount = entries.count { it.disposition == RuntimeStartQueueDisposition.WOULD_QUEUE },
            deferUntilPressureEasesCount = entries.count {
                it.disposition == RuntimeStartQueueDisposition.DEFER_UNTIL_PRESSURE_EASES
            },
            foregroundRequiredCount = entries.count { it.disposition == RuntimeStartQueueDisposition.REQUIRE_FOREGROUND },
            blockedNoCapacityCount = entries.count { it.disposition == RuntimeStartQueueDisposition.BLOCKED_NO_CAPACITY },
            dryRunBacklogCount = backlogIntents.size,
            entries = entries
        )
    }

    private fun RuntimeStartPreflightDecision.toDisposition(): RuntimeStartQueueDisposition {
        return when (this) {
            RuntimeStartPreflightDecision.OPEN -> RuntimeStartQueueDisposition.RUN_IMMEDIATELY
            RuntimeStartPreflightDecision.WATCH -> RuntimeStartQueueDisposition.WATCH_ONLY
            RuntimeStartPreflightDecision.WOULD_QUEUE -> RuntimeStartQueueDisposition.WOULD_QUEUE
            RuntimeStartPreflightDecision.WOULD_DEFER -> RuntimeStartQueueDisposition.DEFER_UNTIL_PRESSURE_EASES
            RuntimeStartPreflightDecision.WOULD_REQUIRE_FOREGROUND ->
                RuntimeStartQueueDisposition.REQUIRE_FOREGROUND
            RuntimeStartPreflightDecision.NO_CAPACITY -> RuntimeStartQueueDisposition.BLOCKED_NO_CAPACITY
        }
    }

    private fun RuntimeStartPreflightDecision.backlogEligible(): Boolean {
        return this == RuntimeStartPreflightDecision.WOULD_QUEUE ||
            this == RuntimeStartPreflightDecision.WOULD_DEFER ||
            this == RuntimeStartPreflightDecision.WOULD_REQUIRE_FOREGROUND
    }

    private fun RuntimeStartPreflightIntent.queueReason(
        disposition: RuntimeStartQueueDisposition,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot
    ): String {
        val laneReason = laneAdmission.lanes.firstOrNull { it.lane == lane }?.reason ?: "lane_reason_missing"
        return when (disposition) {
            RuntimeStartQueueDisposition.RUN_IMMEDIATELY -> "preflight_open"
            RuntimeStartQueueDisposition.WATCH_ONLY -> "preflight_watch:${reason}"
            RuntimeStartQueueDisposition.WOULD_QUEUE -> "dry_run_queue_order:${reason}:$laneReason"
            RuntimeStartQueueDisposition.DEFER_UNTIL_PRESSURE_EASES -> "dry_run_defer_until_pressure_eases:${reason}:$laneReason"
            RuntimeStartQueueDisposition.REQUIRE_FOREGROUND -> "dry_run_requires_foreground:${reason}:$laneReason"
            RuntimeStartQueueDisposition.BLOCKED_NO_CAPACITY -> "dry_run_no_capacity:${reason}:$laneReason"
        }
    }

    private fun List<RuntimeLanePolicy>.withDefaultLanePriorities(): Map<RuntimeLaneKind, Int> {
        val supplied = associateBy { it.lane }
        val defaults = RuntimeWorkloadPolicy.defaultLanes().associateBy { it.lane }
        return RuntimeLaneKind.entries.associateWith { lane ->
            supplied[lane]?.priority ?: defaults.getValue(lane).priority
        }
    }

    private fun RuntimeStartQueueDisposition.toRecommendation(): RuntimeStartQueueRecommendation {
        return when (this) {
            RuntimeStartQueueDisposition.RUN_IMMEDIATELY -> RuntimeStartQueueRecommendation.KEEP_QUEUE_EMPTY
            RuntimeStartQueueDisposition.WATCH_ONLY -> RuntimeStartQueueRecommendation.REVIEW_WATCHLIST
            RuntimeStartQueueDisposition.WOULD_QUEUE -> RuntimeStartQueueRecommendation.REVIEW_QUEUE_ORDER
            RuntimeStartQueueDisposition.DEFER_UNTIL_PRESSURE_EASES ->
                RuntimeStartQueueRecommendation.REVIEW_DEFERRED_STARTS
            RuntimeStartQueueDisposition.REQUIRE_FOREGROUND ->
                RuntimeStartQueueRecommendation.REVIEW_FOREGROUND_REQUIRED
            RuntimeStartQueueDisposition.BLOCKED_NO_CAPACITY -> RuntimeStartQueueRecommendation.REVIEW_NO_CAPACITY
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

    private fun RuntimeStartQueueRecommendation.severity(): Int {
        return when (this) {
            RuntimeStartQueueRecommendation.OBSERVE_ONLY -> 0
            RuntimeStartQueueRecommendation.KEEP_QUEUE_EMPTY -> 0
            RuntimeStartQueueRecommendation.REVIEW_WATCHLIST -> 1
            RuntimeStartQueueRecommendation.REVIEW_QUEUE_ORDER -> 2
            RuntimeStartQueueRecommendation.REVIEW_FOREGROUND_REQUIRED -> 3
            RuntimeStartQueueRecommendation.REVIEW_DEFERRED_STARTS -> 4
            RuntimeStartQueueRecommendation.REVIEW_NO_CAPACITY -> 5
        }
    }
}

private fun String?.toStartQueueEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(220)
}
