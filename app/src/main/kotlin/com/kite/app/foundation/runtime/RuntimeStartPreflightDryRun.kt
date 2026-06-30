package com.kite.app.foundation.runtime

enum class RuntimeStartIntentKind {
    INTERACTIVE_SESSION,
    PINNED_SERVICE,
    BUILD_JOB,
    PROBE_JOB,
    EPHEMERAL_JOB
}

enum class RuntimeStartPreflightDecision {
    OPEN,
    WATCH,
    WOULD_QUEUE,
    WOULD_DEFER,
    WOULD_REQUIRE_FOREGROUND,
    NO_CAPACITY
}

enum class RuntimeStartPreflightRecommendation {
    OBSERVE_ONLY,
    KEEP_STARTS_OPEN,
    REVIEW_START_WATCH,
    REVIEW_START_QUEUE,
    REVIEW_START_DEFER,
    REVIEW_FOREGROUND_ONLY,
    REVIEW_NO_CAPACITY
}

data class RuntimeStartPreflightIntent(
    val intentKind: RuntimeStartIntentKind,
    val workloadClass: RuntimeWorkloadClass,
    val retention: RuntimeWorkloadRetention,
    val lane: RuntimeLaneKind,
    val laneState: RuntimeLaneAdmissionState,
    val activeWorkloads: Int,
    val effectiveMaxConcurrency: Int,
    val backgroundAllowed: Boolean,
    val decision: RuntimeStartPreflightDecision,
    val recommendation: RuntimeStartPreflightRecommendation,
    val reason: String
)

data class RuntimeStartPreflightDryRunSnapshot(
    val mode: String = "start_intent_preflight_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val recommendation: RuntimeStartPreflightRecommendation = RuntimeStartPreflightRecommendation.OBSERVE_ONLY,
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val laneAdmissionRecommendation: RuntimeLaneAdmissionRecommendation = RuntimeLaneAdmissionRecommendation.OBSERVE_ONLY,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val prootPressureScore: Int = 0,
    val currentWorkloadCount: Int = 0,
    val intentCount: Int = 0,
    val openIntentCount: Int = 0,
    val watchIntentCount: Int = 0,
    val queueIntentCount: Int = 0,
    val deferIntentCount: Int = 0,
    val foregroundOnlyIntentCount: Int = 0,
    val noCapacityIntentCount: Int = 0,
    val intents: List<RuntimeStartPreflightIntent> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode recommendation=$recommendation lifecycle=$lifecycleState " +
            "budget=$budgetOverallState lane=${laneAdmissionRecommendation.name} " +
            "pressure=$pressureState/$prootPressureScore open=$openIntentCount watch=$watchIntentCount " +
            "queue=$queueIntentCount defer=$deferIntentCount foregroundOnly=$foregroundOnlyIntentCount " +
            "noCapacity=$noCapacityIntentCount currentWorkloads=$currentWorkloadCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxIntents: Int = 8): String {
        return buildString {
            appendLine("start_preflight_mode=${mode.toStartPreflightEnvValue()}")
            appendLine("start_preflight_enforcement_mode=${enforcementMode.toStartPreflightEnvValue()}")
            appendLine("start_preflight_enforcement_enabled=$enforcementEnabled")
            appendLine("start_preflight_generated_at=$generatedAtMs")
            appendLine("start_preflight_recommendation=${recommendation.name}")
            appendLine("start_preflight_lifecycle_state=${lifecycleState.name}")
            appendLine("start_preflight_background_phase=${backgroundPhase.name}")
            appendLine("start_preflight_budget_overall_state=${budgetOverallState.name}")
            appendLine("start_preflight_lane_admission_recommendation=${laneAdmissionRecommendation.name}")
            appendLine("start_preflight_pressure_state=${pressureState.name}")
            appendLine("start_preflight_proot_score=$prootPressureScore")
            appendLine("start_preflight_current_workload_count=$currentWorkloadCount")
            appendLine("start_preflight_intent_count=$intentCount")
            appendLine("start_preflight_open_intent_count=$openIntentCount")
            appendLine("start_preflight_watch_intent_count=$watchIntentCount")
            appendLine("start_preflight_queue_intent_count=$queueIntentCount")
            appendLine("start_preflight_defer_intent_count=$deferIntentCount")
            appendLine("start_preflight_foreground_only_intent_count=$foregroundOnlyIntentCount")
            appendLine("start_preflight_no_capacity_intent_count=$noCapacityIntentCount")
            intents.take(maxIntents).forEachIndexed { index, intent ->
                val prefix = "start_preflight_intent_${index + 1}"
                appendLine("${prefix}_kind=${intent.intentKind.name}")
                appendLine("${prefix}_class=${intent.workloadClass.name}")
                appendLine("${prefix}_retention=${intent.retention.name}")
                appendLine("${prefix}_lane=${intent.lane.name}")
                appendLine("${prefix}_lane_state=${intent.laneState.name}")
                appendLine("${prefix}_active=${intent.activeWorkloads}")
                appendLine("${prefix}_effective_max=${intent.effectiveMaxConcurrency}")
                appendLine("${prefix}_background_allowed=${intent.backgroundAllowed}")
                appendLine("${prefix}_decision=${intent.decision.name}")
                appendLine("${prefix}_recommendation=${intent.recommendation.name}")
                appendLine("${prefix}_reason=${intent.reason.toStartPreflightEnvValue()}")
            }
            appendLine("start_preflight_boundary=dry_run_no_start_no_queue_no_defer_no_reject_no_lane_control")
        }
    }
}

object RuntimeStartPreflightDryRun {
    fun evaluate(
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        laneAdmission: RuntimeLaneAdmissionDryRunSnapshot,
        policy: RuntimeWorkloadPolicy,
        now: Long = System.currentTimeMillis()
    ): RuntimeStartPreflightDryRunSnapshot {
        val envelopes = policy.envelopes.withDefaultEnvelopes()
        val lanes = laneAdmission.lanes.associateBy { it.lane }
        val intents = defaultIntentSpecs().map { spec ->
            val envelope = envelopes.getValue(spec.workloadClass)
            val lane = lanes[spec.lane] ?: spec.defaultLaneSnapshot()
            val decisionAndReason = resolveDecision(
                envelope = envelope,
                lane = lane,
                pressureConsumer = pressureConsumer,
                backgroundDecay = backgroundDecay,
                budgetPressure = budgetPressure
            )
            val decision = decisionAndReason.first
            RuntimeStartPreflightIntent(
                intentKind = spec.intentKind,
                workloadClass = spec.workloadClass,
                retention = envelope.defaultRetention,
                lane = spec.lane,
                laneState = lane.state,
                activeWorkloads = lane.activeWorkloads,
                effectiveMaxConcurrency = lane.effectiveMaxConcurrency,
                backgroundAllowed = envelope.backgroundAllowed,
                decision = decision,
                recommendation = decision.toRecommendation(),
                reason = decisionAndReason.second
            )
        }.sortedWith(
            compareByDescending<RuntimeStartPreflightIntent> { it.decision.severity() }
                .thenBy { it.lane.ordinal }
                .thenBy { it.intentKind.ordinal }
        )

        val recommendation = intents
            .map { it.recommendation }
            .maxByOrNull { it.severity() }
            ?: RuntimeStartPreflightRecommendation.OBSERVE_ONLY

        return RuntimeStartPreflightDryRunSnapshot(
            generatedAtMs = now,
            recommendation = recommendation,
            lifecycleState = backgroundDecay.lifecycleState,
            backgroundPhase = backgroundDecay.phase,
            budgetOverallState = budgetPressure.overallState,
            laneAdmissionRecommendation = laneAdmission.recommendation,
            pressureState = pressureConsumer.state,
            prootPressureScore = pressureConsumer.prootPressureScore,
            currentWorkloadCount = workloadRegistry.totalWorkloads,
            intentCount = intents.size,
            openIntentCount = intents.count { it.decision == RuntimeStartPreflightDecision.OPEN },
            watchIntentCount = intents.count { it.decision == RuntimeStartPreflightDecision.WATCH },
            queueIntentCount = intents.count { it.decision == RuntimeStartPreflightDecision.WOULD_QUEUE },
            deferIntentCount = intents.count { it.decision == RuntimeStartPreflightDecision.WOULD_DEFER },
            foregroundOnlyIntentCount = intents.count {
                it.decision == RuntimeStartPreflightDecision.WOULD_REQUIRE_FOREGROUND
            },
            noCapacityIntentCount = intents.count { it.decision == RuntimeStartPreflightDecision.NO_CAPACITY },
            intents = intents
        )
    }

    private fun resolveDecision(
        envelope: RuntimeWorkloadEnvelope,
        lane: RuntimeLaneAdmissionLane,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ): Pair<RuntimeStartPreflightDecision, String> {
        if (lane.effectiveMaxConcurrency <= 0 && lane.state == RuntimeLaneAdmissionState.NO_CAPACITY) {
            return RuntimeStartPreflightDecision.NO_CAPACITY to "lane_has_no_effective_capacity"
        }
        if (lane.state == RuntimeLaneAdmissionState.SATURATED) {
            return RuntimeStartPreflightDecision.WOULD_QUEUE to "lane_saturated_next_start_would_queue"
        }
        if (lane.state == RuntimeLaneAdmissionState.NO_CAPACITY) {
            return RuntimeStartPreflightDecision.WOULD_QUEUE to "lane_no_capacity_next_start_would_queue"
        }
        if (lane.state == RuntimeLaneAdmissionState.BACKGROUND_LIMITED) {
            return RuntimeStartPreflightDecision.WOULD_REQUIRE_FOREGROUND to "lane_background_limited"
        }
        if (!backgroundDecay.isForegroundLike() && !envelope.backgroundAllowed) {
            return RuntimeStartPreflightDecision.WOULD_REQUIRE_FOREGROUND to "intent_not_background_allowed"
        }
        if (lane.state == RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY) {
            return RuntimeStartPreflightDecision.WOULD_DEFER to "lane_defer_low_priority"
        }
        if (budgetPressure.overallState.severity() >= RuntimeBudgetState.HARD_PRESSURE.severity() &&
            envelope.isTransient()
        ) {
            return RuntimeStartPreflightDecision.WOULD_DEFER to "hard_budget_reviews_transient_intent"
        }
        if (pressureConsumer.isHighSignal() ||
            budgetPressure.overallState.severity() >= RuntimeBudgetState.NEAR_BUDGET.severity() ||
            lane.state == RuntimeLaneAdmissionState.WATCH
        ) {
            return RuntimeStartPreflightDecision.WATCH to "pressure_or_budget_watch"
        }
        return RuntimeStartPreflightDecision.OPEN to "within_preflight_capacity"
    }

    private fun RuntimeBackgroundDecayDryRunSnapshot.isForegroundLike(): Boolean {
        return lifecycleState == RuntimeAppVisibilityState.FOREGROUND ||
            phase == RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE ||
            phase == RuntimeBackgroundDecayPhase.BACKGROUND_GRACE
    }

    private fun RuntimeWorkloadEnvelope.isTransient(): Boolean {
        return defaultRetention == RuntimeWorkloadRetention.LEASE ||
            workloadClass == RuntimeWorkloadClass.BUILD ||
            workloadClass == RuntimeWorkloadClass.PROBE ||
            workloadClass == RuntimeWorkloadClass.EPHEMERAL
    }

    private fun Map<RuntimeWorkloadClass, RuntimeWorkloadEnvelope>.withDefaults(): Map<RuntimeWorkloadClass, RuntimeWorkloadEnvelope> {
        val defaults = RuntimeWorkloadPolicy.defaultEnvelopes().associateBy { it.workloadClass }
        return RuntimeWorkloadClass.entries.associateWith { workloadClass ->
            this[workloadClass] ?: defaults.getValue(workloadClass)
        }
    }

    private fun List<RuntimeWorkloadEnvelope>.withDefaultEnvelopes(): Map<RuntimeWorkloadClass, RuntimeWorkloadEnvelope> {
        return associateBy { it.workloadClass }.withDefaults()
    }

    private fun defaultIntentSpecs(): List<RuntimeStartIntentSpec> {
        return listOf(
            RuntimeStartIntentSpec(
                RuntimeStartIntentKind.INTERACTIVE_SESSION,
                RuntimeWorkloadClass.INTERACTIVE,
                RuntimeLaneKind.INTERACTIVE
            ),
            RuntimeStartIntentSpec(
                RuntimeStartIntentKind.PINNED_SERVICE,
                RuntimeWorkloadClass.PINNED_SERVICE,
                RuntimeLaneKind.SERVICE
            ),
            RuntimeStartIntentSpec(
                RuntimeStartIntentKind.BUILD_JOB,
                RuntimeWorkloadClass.BUILD,
                RuntimeLaneKind.BUILD
            ),
            RuntimeStartIntentSpec(
                RuntimeStartIntentKind.PROBE_JOB,
                RuntimeWorkloadClass.PROBE,
                RuntimeLaneKind.PROBE
            ),
            RuntimeStartIntentSpec(
                RuntimeStartIntentKind.EPHEMERAL_JOB,
                RuntimeWorkloadClass.EPHEMERAL,
                RuntimeLaneKind.PROBE
            )
        )
    }

    private fun RuntimeStartIntentSpec.defaultLaneSnapshot(): RuntimeLaneAdmissionLane {
        val policy = RuntimeWorkloadPolicy.defaultLanes().first { it.lane == lane }
        return RuntimeLaneAdmissionLane(
            lane = lane,
            activeWorkloads = 0,
            maxConcurrency = policy.maxConcurrency,
            backgroundMaxConcurrency = policy.backgroundMaxConcurrency,
            effectiveMaxConcurrency = policy.maxConcurrency,
            serial = policy.serial,
            allowBurst = policy.allowBurst,
            priority = policy.priority,
            state = RuntimeLaneAdmissionState.OPEN,
            recommendation = RuntimeLaneAdmissionRecommendation.KEEP_LANES_OPEN,
            reason = "default_lane_snapshot"
        )
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

    private fun RuntimeStartPreflightDecision.toRecommendation(): RuntimeStartPreflightRecommendation {
        return when (this) {
            RuntimeStartPreflightDecision.OPEN -> RuntimeStartPreflightRecommendation.KEEP_STARTS_OPEN
            RuntimeStartPreflightDecision.WATCH -> RuntimeStartPreflightRecommendation.REVIEW_START_WATCH
            RuntimeStartPreflightDecision.WOULD_QUEUE -> RuntimeStartPreflightRecommendation.REVIEW_START_QUEUE
            RuntimeStartPreflightDecision.WOULD_DEFER -> RuntimeStartPreflightRecommendation.REVIEW_START_DEFER
            RuntimeStartPreflightDecision.WOULD_REQUIRE_FOREGROUND ->
                RuntimeStartPreflightRecommendation.REVIEW_FOREGROUND_ONLY
            RuntimeStartPreflightDecision.NO_CAPACITY -> RuntimeStartPreflightRecommendation.REVIEW_NO_CAPACITY
        }
    }

    private fun RuntimeStartPreflightDecision.severity(): Int {
        return when (this) {
            RuntimeStartPreflightDecision.OPEN -> 0
            RuntimeStartPreflightDecision.WATCH -> 1
            RuntimeStartPreflightDecision.WOULD_QUEUE -> 2
            RuntimeStartPreflightDecision.WOULD_REQUIRE_FOREGROUND -> 3
            RuntimeStartPreflightDecision.WOULD_DEFER -> 4
            RuntimeStartPreflightDecision.NO_CAPACITY -> 5
        }
    }

    private fun RuntimeStartPreflightRecommendation.severity(): Int {
        return when (this) {
            RuntimeStartPreflightRecommendation.OBSERVE_ONLY -> 0
            RuntimeStartPreflightRecommendation.KEEP_STARTS_OPEN -> 0
            RuntimeStartPreflightRecommendation.REVIEW_START_WATCH -> 1
            RuntimeStartPreflightRecommendation.REVIEW_START_QUEUE -> 2
            RuntimeStartPreflightRecommendation.REVIEW_FOREGROUND_ONLY -> 3
            RuntimeStartPreflightRecommendation.REVIEW_START_DEFER -> 4
            RuntimeStartPreflightRecommendation.REVIEW_NO_CAPACITY -> 5
        }
    }

    private data class RuntimeStartIntentSpec(
        val intentKind: RuntimeStartIntentKind,
        val workloadClass: RuntimeWorkloadClass,
        val lane: RuntimeLaneKind
    )
}

private fun String?.toStartPreflightEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(200)
}
