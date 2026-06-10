package com.kftest.app.foundation.runtime

enum class RuntimeLaneAdmissionState {
    OPEN,
    WATCH,
    SATURATED,
    BACKGROUND_LIMITED,
    DEFER_LOW_PRIORITY,
    NO_CAPACITY
}

enum class RuntimeLaneAdmissionRecommendation {
    OBSERVE_ONLY,
    KEEP_LANES_OPEN,
    REVIEW_LANE_SATURATION,
    REVIEW_DEFER_LOW_PRIORITY,
    REVIEW_BACKGROUND_LIMITS,
    REVIEW_NO_CAPACITY
}

data class RuntimeLaneAdmissionLane(
    val lane: RuntimeLaneKind,
    val activeWorkloads: Int,
    val maxConcurrency: Int,
    val backgroundMaxConcurrency: Int,
    val effectiveMaxConcurrency: Int,
    val serial: Boolean,
    val allowBurst: Boolean,
    val priority: Int,
    val state: RuntimeLaneAdmissionState,
    val recommendation: RuntimeLaneAdmissionRecommendation,
    val reason: String
)

data class RuntimeLaneAdmissionCandidate(
    val workloadId: String,
    val workloadClass: RuntimeWorkloadClass,
    val retention: RuntimeWorkloadRetention,
    val lane: RuntimeLaneKind,
    val state: RuntimeLaneAdmissionState,
    val reason: String
)

data class RuntimeLaneAdmissionDryRunSnapshot(
    val mode: String = "lane_admission_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val recommendation: RuntimeLaneAdmissionRecommendation = RuntimeLaneAdmissionRecommendation.OBSERVE_ONLY,
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val prootPressureScore: Int = 0,
    val policyLaneCount: Int = 0,
    val activeWorkloadCount: Int = 0,
    val openLaneCount: Int = 0,
    val watchLaneCount: Int = 0,
    val saturatedLaneCount: Int = 0,
    val backgroundLimitedLaneCount: Int = 0,
    val deferLowPriorityLaneCount: Int = 0,
    val noCapacityLaneCount: Int = 0,
    val suggestedQueueCount: Int = 0,
    val suggestedDeferCount: Int = 0,
    val lanes: List<RuntimeLaneAdmissionLane> = emptyList(),
    val candidates: List<RuntimeLaneAdmissionCandidate> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode recommendation=$recommendation lifecycle=$lifecycleState " +
            "budget=$budgetOverallState pressure=$pressureState/$prootPressureScore " +
            "open=$openLaneCount watch=$watchLaneCount saturated=$saturatedLaneCount " +
            "backgroundLimited=$backgroundLimitedLaneCount defer=$deferLowPriorityLaneCount " +
            "noCapacity=$noCapacityLaneCount candidates=${candidates.size} enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxLanes: Int = 8, maxCandidates: Int = 8): String {
        return buildString {
            appendLine("lane_admission_mode=${mode.toLaneAdmissionEnvValue()}")
            appendLine("lane_admission_enforcement_mode=${enforcementMode.toLaneAdmissionEnvValue()}")
            appendLine("lane_admission_enforcement_enabled=$enforcementEnabled")
            appendLine("lane_admission_generated_at=$generatedAtMs")
            appendLine("lane_admission_recommendation=${recommendation.name}")
            appendLine("lane_admission_lifecycle_state=${lifecycleState.name}")
            appendLine("lane_admission_background_phase=${backgroundPhase.name}")
            appendLine("lane_admission_budget_overall_state=${budgetOverallState.name}")
            appendLine("lane_admission_pressure_state=${pressureState.name}")
            appendLine("lane_admission_proot_score=$prootPressureScore")
            appendLine("lane_admission_policy_lane_count=$policyLaneCount")
            appendLine("lane_admission_active_workload_count=$activeWorkloadCount")
            appendLine("lane_admission_open_lane_count=$openLaneCount")
            appendLine("lane_admission_watch_lane_count=$watchLaneCount")
            appendLine("lane_admission_saturated_lane_count=$saturatedLaneCount")
            appendLine("lane_admission_background_limited_lane_count=$backgroundLimitedLaneCount")
            appendLine("lane_admission_defer_low_priority_lane_count=$deferLowPriorityLaneCount")
            appendLine("lane_admission_no_capacity_lane_count=$noCapacityLaneCount")
            appendLine("lane_admission_suggested_queue_count=$suggestedQueueCount")
            appendLine("lane_admission_suggested_defer_count=$suggestedDeferCount")
            lanes.take(maxLanes).forEachIndexed { index, lane ->
                val prefix = "lane_admission_lane_${index + 1}"
                appendLine("${prefix}_name=${lane.lane.name}")
                appendLine("${prefix}_active=${lane.activeWorkloads}")
                appendLine("${prefix}_max=${lane.maxConcurrency}")
                appendLine("${prefix}_background_max=${lane.backgroundMaxConcurrency}")
                appendLine("${prefix}_effective_max=${lane.effectiveMaxConcurrency}")
                appendLine("${prefix}_serial=${lane.serial}")
                appendLine("${prefix}_allow_burst=${lane.allowBurst}")
                appendLine("${prefix}_priority=${lane.priority}")
                appendLine("${prefix}_state=${lane.state.name}")
                appendLine("${prefix}_recommendation=${lane.recommendation.name}")
                appendLine("${prefix}_reason=${lane.reason.toLaneAdmissionEnvValue()}")
            }
            candidates.take(maxCandidates).forEachIndexed { index, candidate ->
                val prefix = "lane_admission_candidate_${index + 1}"
                appendLine("${prefix}_id=${candidate.workloadId.toLaneAdmissionEnvValue()}")
                appendLine("${prefix}_class=${candidate.workloadClass.name}")
                appendLine("${prefix}_retention=${candidate.retention.name}")
                appendLine("${prefix}_lane=${candidate.lane.name}")
                appendLine("${prefix}_state=${candidate.state.name}")
                appendLine("${prefix}_reason=${candidate.reason.toLaneAdmissionEnvValue()}")
            }
            appendLine("lane_admission_boundary=dry_run_no_queue_no_defer_no_reject_no_lane_control")
        }
    }
}

object RuntimeLaneAdmissionDryRun {
    fun evaluate(
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        policy: RuntimeWorkloadPolicy,
        now: Long = System.currentTimeMillis()
    ): RuntimeLaneAdmissionDryRunSnapshot {
        val lanePolicies = policy.lanes.withDefaults()
        val activeByLane = workloadRegistry.entries
            .groupingBy { it.suggestedLane }
            .eachCount()
        val inForeground = backgroundDecay.lifecycleState == RuntimeAppVisibilityState.FOREGROUND
        val highSignal = pressureConsumer.isHighSignal() ||
            budgetPressure.overallState.severity() >= RuntimeBudgetState.SOFT_PRESSURE.severity()
        val hardSignal = budgetPressure.overallState.severity() >= RuntimeBudgetState.HARD_PRESSURE.severity() ||
            backgroundDecay.phase == RuntimeBackgroundDecayPhase.LOW_MEMORY_REVIEW

        val lanes = RuntimeLaneKind.entries.map { lane ->
            val lanePolicy = lanePolicies.getValue(lane)
            val active = activeByLane[lane] ?: 0
            lanePolicy.toLaneSnapshot(
                activeWorkloads = active,
                inForeground = inForeground,
                highSignal = highSignal,
                hardSignal = hardSignal
            )
        }
        val laneByKind = lanes.associateBy { it.lane }
        val candidates = workloadRegistry.entries
            .mapNotNull { entry ->
                val lane = laneByKind[entry.suggestedLane] ?: return@mapNotNull null
                if (lane.state == RuntimeLaneAdmissionState.OPEN ||
                    lane.state == RuntimeLaneAdmissionState.WATCH
                ) {
                    null
                } else {
                    RuntimeLaneAdmissionCandidate(
                        workloadId = entry.workloadId,
                        workloadClass = entry.workloadClass,
                        retention = entry.retention,
                        lane = entry.suggestedLane,
                        state = lane.state,
                        reason = "lane_${lane.state.name.lowercase()}_${lane.reason}"
                    )
                }
            }
            .sortedWith(
                compareByDescending<RuntimeLaneAdmissionCandidate> { it.state.severity() }
                    .thenBy { it.lane.ordinal }
                    .thenBy { it.workloadId }
            )

        val recommendation = lanes
            .map { it.recommendation }
            .maxByOrNull { it.severity() }
            ?: RuntimeLaneAdmissionRecommendation.OBSERVE_ONLY

        return RuntimeLaneAdmissionDryRunSnapshot(
            generatedAtMs = now,
            recommendation = recommendation,
            lifecycleState = backgroundDecay.lifecycleState,
            backgroundPhase = backgroundDecay.phase,
            budgetOverallState = budgetPressure.overallState,
            pressureState = pressureConsumer.state,
            prootPressureScore = pressureConsumer.prootPressureScore,
            policyLaneCount = policy.lanes.size,
            activeWorkloadCount = workloadRegistry.totalWorkloads,
            openLaneCount = lanes.count { it.state == RuntimeLaneAdmissionState.OPEN },
            watchLaneCount = lanes.count { it.state == RuntimeLaneAdmissionState.WATCH },
            saturatedLaneCount = lanes.count { it.state == RuntimeLaneAdmissionState.SATURATED },
            backgroundLimitedLaneCount = lanes.count { it.state == RuntimeLaneAdmissionState.BACKGROUND_LIMITED },
            deferLowPriorityLaneCount = lanes.count { it.state == RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY },
            noCapacityLaneCount = lanes.count { it.state == RuntimeLaneAdmissionState.NO_CAPACITY },
            suggestedQueueCount = lanes.count {
                it.state == RuntimeLaneAdmissionState.SATURATED ||
                    it.state == RuntimeLaneAdmissionState.NO_CAPACITY
            },
            suggestedDeferCount = candidates.count {
                it.state == RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY ||
                    it.state == RuntimeLaneAdmissionState.BACKGROUND_LIMITED ||
                    it.state == RuntimeLaneAdmissionState.NO_CAPACITY
            },
            lanes = lanes.sortedWith(
                compareByDescending<RuntimeLaneAdmissionLane> { it.state.severity() }
                    .thenBy { it.priority }
                    .thenBy { it.lane.ordinal }
            ),
            candidates = candidates
        )
    }

    private fun RuntimeLanePolicy.toLaneSnapshot(
        activeWorkloads: Int,
        inForeground: Boolean,
        highSignal: Boolean,
        hardSignal: Boolean
    ): RuntimeLaneAdmissionLane {
        val effectiveMax = if (inForeground) {
            maxConcurrency
        } else {
            backgroundMaxConcurrency
        }.coerceAtLeast(0)
        val stateAndReason = resolveLaneState(
            activeWorkloads = activeWorkloads,
            effectiveMax = effectiveMax,
            inForeground = inForeground,
            highSignal = highSignal,
            hardSignal = hardSignal
        )
        val state = stateAndReason.first
        return RuntimeLaneAdmissionLane(
            lane = lane,
            activeWorkloads = activeWorkloads,
            maxConcurrency = maxConcurrency,
            backgroundMaxConcurrency = backgroundMaxConcurrency,
            effectiveMaxConcurrency = effectiveMax,
            serial = serial,
            allowBurst = allowBurst,
            priority = priority,
            state = state,
            recommendation = state.toRecommendation(),
            reason = stateAndReason.second
        )
    }

    private fun RuntimeLanePolicy.resolveLaneState(
        activeWorkloads: Int,
        effectiveMax: Int,
        inForeground: Boolean,
        highSignal: Boolean,
        hardSignal: Boolean
    ): Pair<RuntimeLaneAdmissionState, String> {
        if (effectiveMax <= 0 && activeWorkloads > 0) {
            return RuntimeLaneAdmissionState.NO_CAPACITY to "active_without_effective_capacity"
        }
        if (effectiveMax <= 0 && !inForeground) {
            return RuntimeLaneAdmissionState.BACKGROUND_LIMITED to "background_capacity_zero"
        }
        if (activeWorkloads > effectiveMax && effectiveMax > 0) {
            return RuntimeLaneAdmissionState.SATURATED to "active_exceeds_effective_max"
        }
        if (hardSignal && priority >= RuntimeLaneKind.BUILD.defaultPriority()) {
            return RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY to "hard_signal_reviews_low_priority_lanes"
        }
        if (highSignal && !allowBurst && priority >= RuntimeLaneKind.BUILD.defaultPriority()) {
            return RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY to "high_signal_reviews_non_burst_low_priority_lane"
        }
        if (highSignal) {
            return RuntimeLaneAdmissionState.WATCH to "high_signal_watch_lane"
        }
        return RuntimeLaneAdmissionState.OPEN to "within_lane_capacity"
    }

    private fun List<RuntimeLanePolicy>.withDefaults(): Map<RuntimeLaneKind, RuntimeLanePolicy> {
        val supplied = associateBy { it.lane }
        val defaults = RuntimeWorkloadPolicy.defaultLanes().associateBy { it.lane }
        return RuntimeLaneKind.entries.associateWith { lane ->
            supplied[lane] ?: defaults.getValue(lane)
        }
    }

    private fun RuntimeLaneAdmissionState.toRecommendation(): RuntimeLaneAdmissionRecommendation {
        return when (this) {
            RuntimeLaneAdmissionState.OPEN -> RuntimeLaneAdmissionRecommendation.KEEP_LANES_OPEN
            RuntimeLaneAdmissionState.WATCH -> RuntimeLaneAdmissionRecommendation.OBSERVE_ONLY
            RuntimeLaneAdmissionState.SATURATED -> RuntimeLaneAdmissionRecommendation.REVIEW_LANE_SATURATION
            RuntimeLaneAdmissionState.BACKGROUND_LIMITED -> RuntimeLaneAdmissionRecommendation.REVIEW_BACKGROUND_LIMITS
            RuntimeLaneAdmissionState.DEFER_LOW_PRIORITY -> RuntimeLaneAdmissionRecommendation.REVIEW_DEFER_LOW_PRIORITY
            RuntimeLaneAdmissionState.NO_CAPACITY -> RuntimeLaneAdmissionRecommendation.REVIEW_NO_CAPACITY
        }
    }

    private fun RuntimeLaneKind.defaultPriority(): Int {
        return RuntimeWorkloadPolicy.defaultLanes()
            .first { it.lane == this }
            .priority
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

    private fun RuntimeLaneAdmissionRecommendation.severity(): Int {
        return when (this) {
            RuntimeLaneAdmissionRecommendation.OBSERVE_ONLY -> 0
            RuntimeLaneAdmissionRecommendation.KEEP_LANES_OPEN -> 0
            RuntimeLaneAdmissionRecommendation.REVIEW_LANE_SATURATION -> 2
            RuntimeLaneAdmissionRecommendation.REVIEW_BACKGROUND_LIMITS -> 3
            RuntimeLaneAdmissionRecommendation.REVIEW_DEFER_LOW_PRIORITY -> 4
            RuntimeLaneAdmissionRecommendation.REVIEW_NO_CAPACITY -> 5
        }
    }
}

private fun String?.toLaneAdmissionEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(200)
}
