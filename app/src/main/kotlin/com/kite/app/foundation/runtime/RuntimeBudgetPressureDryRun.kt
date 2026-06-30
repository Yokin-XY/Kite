package com.kite.app.foundation.runtime

enum class RuntimeBudgetPressureRecommendation {
    OBSERVE_ONLY,
    REVIEW_WARNINGS,
    REVIEW_THROTTLE,
    REVIEW_HARD_PRESSURE,
    REVIEW_KF_THREAT,
    REVIEW_REPEAT_OFFENDERS,
    REVIEW_QUARANTINE
}

data class RuntimeBudgetPressureCandidate(
    val workloadId: String,
    val workloadClass: RuntimeWorkloadClass,
    val retention: RuntimeWorkloadRetention,
    val lane: RuntimeLaneKind,
    val state: RuntimeBudgetState,
    val actions: List<RuntimeBudgetAction>,
    val processCount: Int,
    val maxChildren: Int,
    val rssKb: Long,
    val reason: String
)

data class RuntimeBudgetPressureDryRunSnapshot(
    val mode: String = "budget_pressure_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val overallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val recommendation: RuntimeBudgetPressureRecommendation = RuntimeBudgetPressureRecommendation.OBSERVE_ONLY,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val rssPressure: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val prootPressureScore: Int = 0,
    val backgroundPhase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val backgroundRecommendation: RuntimeBackgroundDecayRecommendation = RuntimeBackgroundDecayRecommendation.OBSERVE_ONLY,
    val budgetPolicyCount: Int = 0,
    val repeatQuickRelapseMs: Long = 0L,
    val repeatRestartWindowMs: Long = 0L,
    val repeatMaxRestarts: Int = 0,
    val repeatViolationWindowMs: Long = 0L,
    val repeatMaxViolations: Int = 0,
    val healthyCount: Int = 0,
    val nearBudgetCount: Int = 0,
    val softPressureCount: Int = 0,
    val hardPressureCount: Int = 0,
    val threateningKfCount: Int = 0,
    val repeatOffenderCount: Int = 0,
    val quarantinedCount: Int = 0,
    val overChildBudgetCount: Int = 0,
    val cleanupCandidateCount: Int = 0,
    val transientCandidateCount: Int = 0,
    val restartRiskCount: Int = 0,
    val candidates: List<RuntimeBudgetPressureCandidate> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$overallState recommendation=$recommendation " +
            "pressure=$pressureState/$prootPressureScore rss=$rssPressure " +
            "near=$nearBudgetCount soft=$softPressureCount hard=$hardPressureCount " +
            "threat=$threateningKfCount repeat=$repeatOffenderCount quarantine=$quarantinedCount " +
            "candidates=${candidates.size} enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxCandidates: Int = 8): String {
        return buildString {
            appendLine("budget_pressure_mode=${mode.toBudgetPressureEnvValue()}")
            appendLine("budget_pressure_enforcement_mode=${enforcementMode.toBudgetPressureEnvValue()}")
            appendLine("budget_pressure_enforcement_enabled=$enforcementEnabled")
            appendLine("budget_pressure_generated_at=$generatedAtMs")
            appendLine("budget_pressure_overall_state=${overallState.name}")
            appendLine("budget_pressure_recommendation=${recommendation.name}")
            appendLine("budget_pressure_pressure_state=${pressureState.name}")
            appendLine("budget_pressure_rss_pressure=${rssPressure.name}")
            appendLine("budget_pressure_proot_score=$prootPressureScore")
            appendLine("budget_pressure_background_phase=${backgroundPhase.name}")
            appendLine("budget_pressure_background_recommendation=${backgroundRecommendation.name}")
            appendLine("budget_pressure_policy_count=$budgetPolicyCount")
            appendLine("budget_pressure_repeat_quick_relapse_ms=$repeatQuickRelapseMs")
            appendLine("budget_pressure_repeat_restart_window_ms=$repeatRestartWindowMs")
            appendLine("budget_pressure_repeat_max_restarts=$repeatMaxRestarts")
            appendLine("budget_pressure_repeat_violation_window_ms=$repeatViolationWindowMs")
            appendLine("budget_pressure_repeat_max_violations=$repeatMaxViolations")
            appendLine("budget_pressure_healthy_count=$healthyCount")
            appendLine("budget_pressure_near_budget_count=$nearBudgetCount")
            appendLine("budget_pressure_soft_pressure_count=$softPressureCount")
            appendLine("budget_pressure_hard_pressure_count=$hardPressureCount")
            appendLine("budget_pressure_threatening_kf_count=$threateningKfCount")
            appendLine("budget_pressure_repeat_offender_count=$repeatOffenderCount")
            appendLine("budget_pressure_quarantined_count=$quarantinedCount")
            appendLine("budget_pressure_over_child_budget_count=$overChildBudgetCount")
            appendLine("budget_pressure_cleanup_candidate_count=$cleanupCandidateCount")
            appendLine("budget_pressure_transient_candidate_count=$transientCandidateCount")
            appendLine("budget_pressure_restart_risk_count=$restartRiskCount")
            appendLine("budget_pressure_candidate_count=${candidates.size}")
            candidates.take(maxCandidates).forEachIndexed { index, candidate ->
                val prefix = "budget_pressure_candidate_${index + 1}"
                appendLine("${prefix}_id=${candidate.workloadId.toBudgetPressureEnvValue()}")
                appendLine("${prefix}_class=${candidate.workloadClass.name}")
                appendLine("${prefix}_retention=${candidate.retention.name}")
                appendLine("${prefix}_lane=${candidate.lane.name}")
                appendLine("${prefix}_state=${candidate.state.name}")
                appendLine("${prefix}_actions=${candidate.actions.joinToString("+") { it.name }.toBudgetPressureEnvValue()}")
                appendLine("${prefix}_children=${candidate.processCount}")
                appendLine("${prefix}_max_children=${candidate.maxChildren}")
                appendLine("${prefix}_rss_kb=${candidate.rssKb}")
                appendLine("${prefix}_reason=${candidate.reason.toBudgetPressureEnvValue()}")
            }
            appendLine("budget_pressure_boundary=dry_run_no_throttle_no_freeze_no_terminate_no_restart_no_quarantine")
        }
    }
}

object RuntimeBudgetPressureDryRun {
    fun evaluate(
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        roots: List<RuntimeRootSnapshot>,
        policy: RuntimeWorkloadPolicy,
        now: Long = System.currentTimeMillis()
    ): RuntimeBudgetPressureDryRunSnapshot {
        val statePolicies = policy.budgetStates.associateBy { it.state }
        val rootByWorkloadId = roots.associateBy { it.ownershipKey }
        val candidates = workloadRegistry.entries.map { entry ->
            val root = rootByWorkloadId[entry.workloadId]
            val stateAndReason = classifyEntry(
                entry = entry,
                root = root,
                pressureConsumer = pressureConsumer,
                backgroundDecay = backgroundDecay,
                repeatOffender = policy.repeatOffender
            )
            val state = stateAndReason.first
            RuntimeBudgetPressureCandidate(
                workloadId = entry.workloadId,
                workloadClass = entry.workloadClass,
                retention = entry.retention,
                lane = entry.suggestedLane,
                state = state,
                actions = statePolicies[state]?.actions ?: listOf(RuntimeBudgetAction.OBSERVE),
                processCount = entry.processCount,
                maxChildren = entry.maxChildren,
                rssKb = entry.rssKb,
                reason = stateAndReason.second
            )
        }
        val overallState = candidates
            .map { it.state }
            .maxByOrNull { it.severity() }
            ?: RuntimeBudgetState.HEALTHY
        val visibleCandidates = candidates
            .filter { it.state != RuntimeBudgetState.HEALTHY }
            .sortedWith(
                compareByDescending<RuntimeBudgetPressureCandidate> { it.state.severity() }
                    .thenByDescending { it.rssKb }
                    .thenBy { it.workloadId }
            )

        return RuntimeBudgetPressureDryRunSnapshot(
            generatedAtMs = now,
            overallState = overallState,
            recommendation = recommendationFor(overallState),
            pressureState = pressureConsumer.state,
            rssPressure = pressureConsumer.rssPressureLevel,
            prootPressureScore = pressureConsumer.prootPressureScore,
            backgroundPhase = backgroundDecay.phase,
            backgroundRecommendation = backgroundDecay.recommendation,
            budgetPolicyCount = policy.budgetStates.size,
            repeatQuickRelapseMs = policy.repeatOffender.quickRelapseMs,
            repeatRestartWindowMs = policy.repeatOffender.restartWindowMs,
            repeatMaxRestarts = policy.repeatOffender.maxRestartsInWindow,
            repeatViolationWindowMs = policy.repeatOffender.violationWindowMs,
            repeatMaxViolations = policy.repeatOffender.maxViolationsInWindow,
            healthyCount = candidates.count { it.state == RuntimeBudgetState.HEALTHY },
            nearBudgetCount = candidates.count { it.state == RuntimeBudgetState.NEAR_BUDGET },
            softPressureCount = candidates.count { it.state == RuntimeBudgetState.SOFT_PRESSURE },
            hardPressureCount = candidates.count { it.state == RuntimeBudgetState.HARD_PRESSURE },
            threateningKfCount = candidates.count { it.state == RuntimeBudgetState.THREATENING_KF },
            repeatOffenderCount = candidates.count { it.state == RuntimeBudgetState.REPEAT_OFFENDER },
            quarantinedCount = candidates.count { it.state == RuntimeBudgetState.QUARANTINED },
            overChildBudgetCount = workloadRegistry.overChildBudgetCount,
            cleanupCandidateCount = workloadRegistry.cleanupCandidateCount,
            transientCandidateCount = candidates.count {
                it.retention == RuntimeWorkloadRetention.LEASE ||
                    it.workloadClass == RuntimeWorkloadClass.BUILD ||
                    it.workloadClass == RuntimeWorkloadClass.PROBE ||
                    it.workloadClass == RuntimeWorkloadClass.EPHEMERAL
            },
            restartRiskCount = roots.count { root ->
                root.restartFailureCount >= policy.repeatOffender.maxRestartsInWindow &&
                    policy.repeatOffender.maxRestartsInWindow > 0
            },
            candidates = visibleCandidates
        )
    }

    private fun classifyEntry(
        entry: RuntimeWorkloadRegistryEntry,
        root: RuntimeRootSnapshot?,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        repeatOffender: RuntimeRepeatOffenderPolicy
    ): Pair<RuntimeBudgetState, String> {
        if (entry.retention == RuntimeWorkloadRetention.QUARANTINE) {
            return RuntimeBudgetState.QUARANTINED to "retention_quarantined"
        }
        if (root != null &&
            repeatOffender.maxRestartsInWindow > 0 &&
            root.restartFailureCount >= repeatOffender.maxRestartsInWindow
        ) {
            return RuntimeBudgetState.REPEAT_OFFENDER to "restart_failures_in_policy_window"
        }
        if (backgroundDecay.phase == RuntimeBackgroundDecayPhase.LOW_MEMORY_REVIEW &&
            (entry.overChildBudget || entry.isCleanupCandidate() || entry.isTransient())
        ) {
            return RuntimeBudgetState.THREATENING_KF to "low_memory_reviews_non_core_or_over_budget_workload"
        }
        if (pressureConsumer.rssPressureLevel == RuntimePressureLevel.CRITICAL &&
            (entry.overChildBudget || entry.isCleanupCandidate() || !entry.backgroundAllowed)
        ) {
            return RuntimeBudgetState.THREATENING_KF to "critical_rss_pressure_reviews_reclaimable_or_disallowed_workload"
        }
        if (entry.overChildBudget) {
            return if (pressureConsumer.isHighSignal() || backgroundDecay.phase == RuntimeBackgroundDecayPhase.PRESSURE_ACCELERATED_REVIEW) {
                RuntimeBudgetState.HARD_PRESSURE to "over_child_budget_under_high_signal"
            } else {
                RuntimeBudgetState.SOFT_PRESSURE to "over_child_budget"
            }
        }
        if (entry.isCleanupCandidate()) {
            return if (pressureConsumer.isHighSignal()) {
                RuntimeBudgetState.HARD_PRESSURE to "cleanup_candidate_under_high_signal"
            } else {
                RuntimeBudgetState.SOFT_PRESSURE to "cleanup_candidate_or_stray"
            }
        }
        if (backgroundDecay.phase.reviewsServiceOnly() && !entry.backgroundAllowed) {
            return RuntimeBudgetState.SOFT_PRESSURE to "background_phase_reviews_non_background_workload"
        }
        if (backgroundDecay.phase != RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE &&
            backgroundDecay.phase != RuntimeBackgroundDecayPhase.BACKGROUND_GRACE &&
            entry.isTransient()
        ) {
            return RuntimeBudgetState.SOFT_PRESSURE to "transient_or_lease_under_background_decay"
        }
        if (pressureConsumer.isHighSignal()) {
            return RuntimeBudgetState.NEAR_BUDGET to "proot_or_rss_pressure_requires_watch"
        }
        return RuntimeBudgetState.HEALTHY to "within_budget"
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

    private fun recommendationFor(state: RuntimeBudgetState): RuntimeBudgetPressureRecommendation {
        return when (state) {
            RuntimeBudgetState.HEALTHY -> RuntimeBudgetPressureRecommendation.OBSERVE_ONLY
            RuntimeBudgetState.NEAR_BUDGET -> RuntimeBudgetPressureRecommendation.REVIEW_WARNINGS
            RuntimeBudgetState.SOFT_PRESSURE -> RuntimeBudgetPressureRecommendation.REVIEW_THROTTLE
            RuntimeBudgetState.HARD_PRESSURE -> RuntimeBudgetPressureRecommendation.REVIEW_HARD_PRESSURE
            RuntimeBudgetState.THREATENING_KF -> RuntimeBudgetPressureRecommendation.REVIEW_KF_THREAT
            RuntimeBudgetState.REPEAT_OFFENDER -> RuntimeBudgetPressureRecommendation.REVIEW_REPEAT_OFFENDERS
            RuntimeBudgetState.QUARANTINED -> RuntimeBudgetPressureRecommendation.REVIEW_QUARANTINE
        }
    }

    private fun RuntimePressureConsumerSnapshot.isHighSignal(): Boolean {
        return state == RuntimePressureConsumerState.BUSY ||
            state == RuntimePressureConsumerState.BURST ||
            state == RuntimePressureConsumerState.DEGRADED ||
            rssPressureLevel == RuntimePressureLevel.ELEVATED ||
            rssPressureLevel == RuntimePressureLevel.HIGH ||
            rssPressureLevel == RuntimePressureLevel.CRITICAL ||
            prootPressureScore >= 80
    }

    private fun RuntimeWorkloadRegistryEntry.isCleanupCandidate(): Boolean {
        return retention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE ||
            workloadClass == RuntimeWorkloadClass.STRAY ||
            workloadClass == RuntimeWorkloadClass.UNKNOWN
    }

    private fun RuntimeWorkloadRegistryEntry.isTransient(): Boolean {
        return retention == RuntimeWorkloadRetention.LEASE ||
            workloadClass == RuntimeWorkloadClass.BUILD ||
            workloadClass == RuntimeWorkloadClass.PROBE ||
            workloadClass == RuntimeWorkloadClass.EPHEMERAL
    }

    private fun RuntimeBackgroundDecayPhase.reviewsServiceOnly(): Boolean {
        return this == RuntimeBackgroundDecayPhase.SERVICE_ONLY_REVIEW ||
            this == RuntimeBackgroundDecayPhase.LOW_ACTIVITY_REVIEW ||
            this == RuntimeBackgroundDecayPhase.PRESSURE_ACCELERATED_REVIEW ||
            this == RuntimeBackgroundDecayPhase.LOW_MEMORY_REVIEW
    }
}

private fun String?.toBudgetPressureEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(200)
}
