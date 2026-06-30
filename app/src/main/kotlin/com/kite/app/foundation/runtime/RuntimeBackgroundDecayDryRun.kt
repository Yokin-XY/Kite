package com.kite.app.foundation.runtime

enum class RuntimeBackgroundDecayPhase {
    FOREGROUND_ALLOWANCE,
    BACKGROUND_GRACE,
    TRANSIENT_CLEANUP_REVIEW,
    SERVICE_ONLY_REVIEW,
    LOW_ACTIVITY_REVIEW,
    PRESSURE_ACCELERATED_REVIEW,
    LOW_MEMORY_REVIEW
}

enum class RuntimeBackgroundDecayRecommendation {
    OBSERVE_ONLY,
    KEEP_CURRENT,
    REVIEW_TRANSIENT_LEASES,
    REVIEW_SERVICE_ONLY,
    REVIEW_LOW_ACTIVITY,
    REVIEW_PRESSURE_ACCELERATED,
    REVIEW_LOW_MEMORY
}

data class RuntimeBackgroundDecayCandidate(
    val workloadId: String,
    val workloadClass: RuntimeWorkloadClass,
    val retention: RuntimeWorkloadRetention,
    val lane: RuntimeLaneKind,
    val reason: String
)

data class RuntimeBackgroundDecayDryRunSnapshot(
    val mode: String = "background_decay_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val lifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val lifecycleEvent: String = "initial",
    val foregroundActivityCount: Int = 0,
    val backgroundAgeMs: Long = 0L,
    val lastResumedAtMs: Long? = null,
    val lastResumedActivity: String = "none",
    val lastTrimLevel: Int? = null,
    val lastTrimLabel: String = "none",
    val phase: RuntimeBackgroundDecayPhase = RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE,
    val recommendation: RuntimeBackgroundDecayRecommendation = RuntimeBackgroundDecayRecommendation.OBSERVE_ONLY,
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val prootPressureScore: Int = 0,
    val policyGraceMs: Long = 0L,
    val policyTransientCleanupMs: Long = 0L,
    val policyServiceOnlyMs: Long = 0L,
    val policyLowActivityMs: Long = 0L,
    val pressureAccelerated: Boolean = false,
    val keepCount: Int = 0,
    val leaseCount: Int = 0,
    val cleanupCandidateCount: Int = 0,
    val serviceEligibleCount: Int = 0,
    val transientReviewCount: Int = 0,
    val cleanupReviewCount: Int = 0,
    val overChildBudgetCount: Int = 0,
    val unassignedLiveTracees: Int = 0,
    val candidates: List<RuntimeBackgroundDecayCandidate> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode phase=$phase recommendation=$recommendation " +
            "state=$lifecycleState ageMs=$backgroundAgeMs keep=$keepCount lease=$leaseCount " +
            "cleanup=$cleanupCandidateCount candidates=${candidates.size} enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxCandidates: Int = 8): String {
        return buildString {
            appendLine("background_decay_mode=${mode.toBackgroundDecayEnvValue()}")
            appendLine("background_decay_enforcement_mode=${enforcementMode.toBackgroundDecayEnvValue()}")
            appendLine("background_decay_enforcement_enabled=$enforcementEnabled")
            appendLine("background_decay_generated_at=$generatedAtMs")
            appendLine("background_decay_lifecycle_state=${lifecycleState.name}")
            appendLine("background_decay_lifecycle_event=${lifecycleEvent.toBackgroundDecayEnvValue()}")
            appendLine("background_decay_foreground_activity_count=$foregroundActivityCount")
            appendLine("background_decay_background_age_ms=$backgroundAgeMs")
            appendLine("background_decay_last_resumed_at=${lastResumedAtMs ?: 0L}")
            appendLine("background_decay_last_resumed_activity=${lastResumedActivity.toBackgroundDecayEnvValue()}")
            appendLine("background_decay_last_trim_level=${lastTrimLevel ?: -1}")
            appendLine("background_decay_last_trim_label=${lastTrimLabel.toBackgroundDecayEnvValue()}")
            appendLine("background_decay_phase=${phase.name}")
            appendLine("background_decay_recommendation=${recommendation.name}")
            appendLine("background_decay_pressure_state=${pressureState.name}")
            appendLine("background_decay_proot_pressure_score=$prootPressureScore")
            appendLine("background_decay_policy_grace_ms=$policyGraceMs")
            appendLine("background_decay_policy_transient_cleanup_ms=$policyTransientCleanupMs")
            appendLine("background_decay_policy_service_only_ms=$policyServiceOnlyMs")
            appendLine("background_decay_policy_low_activity_ms=$policyLowActivityMs")
            appendLine("background_decay_pressure_accelerated=$pressureAccelerated")
            appendLine("background_decay_keep_count=$keepCount")
            appendLine("background_decay_lease_count=$leaseCount")
            appendLine("background_decay_cleanup_candidate_count=$cleanupCandidateCount")
            appendLine("background_decay_service_eligible_count=$serviceEligibleCount")
            appendLine("background_decay_transient_review_count=$transientReviewCount")
            appendLine("background_decay_cleanup_review_count=$cleanupReviewCount")
            appendLine("background_decay_over_child_budget_count=$overChildBudgetCount")
            appendLine("background_decay_unassigned_live_tracees=$unassignedLiveTracees")
            appendLine("background_decay_candidate_count=${candidates.size}")
            candidates.take(maxCandidates).forEachIndexed { index, candidate ->
                val prefix = "background_decay_candidate_${index + 1}"
                appendLine("${prefix}_id=${candidate.workloadId.toBackgroundDecayEnvValue()}")
                appendLine("${prefix}_class=${candidate.workloadClass.name}")
                appendLine("${prefix}_retention=${candidate.retention.name}")
                appendLine("${prefix}_lane=${candidate.lane.name}")
                appendLine("${prefix}_reason=${candidate.reason.toBackgroundDecayEnvValue()}")
            }
            appendLine("background_decay_boundary=dry_run_no_cleanup_no_restart_no_quarantine")
        }
    }
}

object RuntimeBackgroundDecayDryRun {
    fun evaluate(
        lifecycle: RuntimeLifecycleSignalSnapshot,
        workloadRegistry: RuntimeWorkloadRegistrySnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        policy: RuntimeBackgroundDecayPolicy,
        now: Long = System.currentTimeMillis()
    ): RuntimeBackgroundDecayDryRunSnapshot {
        val backgroundAgeMs = lifecycle.backgroundAgeMs(now)
        val pressureAccelerated = policy.pressureAccelerates &&
            (pressureConsumer.state == RuntimePressureConsumerState.BUSY ||
                pressureConsumer.state == RuntimePressureConsumerState.BURST ||
                pressureConsumer.state == RuntimePressureConsumerState.DEGRADED)
        val phase = resolvePhase(
            lifecycle = lifecycle,
            backgroundAgeMs = backgroundAgeMs,
            policy = policy,
            pressureAccelerated = pressureAccelerated
        )
        val recommendation = resolveRecommendation(phase)
        val candidates = buildCandidates(
            phase = phase,
            entries = workloadRegistry.entries
        )

        return RuntimeBackgroundDecayDryRunSnapshot(
            generatedAtMs = now,
            lifecycleState = lifecycle.visibilityState,
            lifecycleEvent = lifecycle.lastEvent,
            foregroundActivityCount = lifecycle.foregroundActivityCount,
            backgroundAgeMs = backgroundAgeMs,
            lastResumedAtMs = lifecycle.lastResumedAtMs,
            lastResumedActivity = lifecycle.lastResumedActivity,
            lastTrimLevel = lifecycle.lastTrimLevel,
            lastTrimLabel = lifecycle.lastTrimLabel,
            phase = phase,
            recommendation = recommendation,
            pressureState = pressureConsumer.state,
            prootPressureScore = pressureConsumer.prootPressureScore,
            policyGraceMs = policy.graceMs,
            policyTransientCleanupMs = policy.transientCleanupMs,
            policyServiceOnlyMs = policy.serviceOnlyMs,
            policyLowActivityMs = policy.lowActivityMs,
            pressureAccelerated = pressureAccelerated,
            keepCount = workloadRegistry.keepCount,
            leaseCount = workloadRegistry.leaseCount,
            cleanupCandidateCount = workloadRegistry.cleanupCandidateCount,
            serviceEligibleCount = workloadRegistry.entries.count {
                it.retention == RuntimeWorkloadRetention.KEEP && it.backgroundAllowed
            },
            transientReviewCount = workloadRegistry.entries.count { it.isTransientReviewCandidate() },
            cleanupReviewCount = workloadRegistry.entries.count { it.isCleanupReviewCandidate() },
            overChildBudgetCount = workloadRegistry.overChildBudgetCount,
            unassignedLiveTracees = workloadRegistry.unassignedLiveTracees,
            candidates = candidates
        )
    }

    private fun resolvePhase(
        lifecycle: RuntimeLifecycleSignalSnapshot,
        backgroundAgeMs: Long,
        policy: RuntimeBackgroundDecayPolicy,
        pressureAccelerated: Boolean
    ): RuntimeBackgroundDecayPhase {
        if (lifecycle.visibilityState == RuntimeAppVisibilityState.LOW_MEMORY) {
            return RuntimeBackgroundDecayPhase.LOW_MEMORY_REVIEW
        }
        if (lifecycle.visibilityState == RuntimeAppVisibilityState.FOREGROUND) {
            return RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE
        }
        if (pressureAccelerated) {
            return RuntimeBackgroundDecayPhase.PRESSURE_ACCELERATED_REVIEW
        }
        return when {
            backgroundAgeMs < policy.graceMs -> RuntimeBackgroundDecayPhase.BACKGROUND_GRACE
            backgroundAgeMs < policy.transientCleanupMs -> RuntimeBackgroundDecayPhase.TRANSIENT_CLEANUP_REVIEW
            backgroundAgeMs < policy.serviceOnlyMs -> RuntimeBackgroundDecayPhase.SERVICE_ONLY_REVIEW
            else -> RuntimeBackgroundDecayPhase.LOW_ACTIVITY_REVIEW
        }
    }

    private fun resolveRecommendation(
        phase: RuntimeBackgroundDecayPhase
    ): RuntimeBackgroundDecayRecommendation {
        return when (phase) {
            RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE ->
                RuntimeBackgroundDecayRecommendation.OBSERVE_ONLY
            RuntimeBackgroundDecayPhase.BACKGROUND_GRACE ->
                RuntimeBackgroundDecayRecommendation.KEEP_CURRENT
            RuntimeBackgroundDecayPhase.TRANSIENT_CLEANUP_REVIEW ->
                RuntimeBackgroundDecayRecommendation.REVIEW_TRANSIENT_LEASES
            RuntimeBackgroundDecayPhase.SERVICE_ONLY_REVIEW ->
                RuntimeBackgroundDecayRecommendation.REVIEW_SERVICE_ONLY
            RuntimeBackgroundDecayPhase.LOW_ACTIVITY_REVIEW ->
                RuntimeBackgroundDecayRecommendation.REVIEW_LOW_ACTIVITY
            RuntimeBackgroundDecayPhase.PRESSURE_ACCELERATED_REVIEW ->
                RuntimeBackgroundDecayRecommendation.REVIEW_PRESSURE_ACCELERATED
            RuntimeBackgroundDecayPhase.LOW_MEMORY_REVIEW ->
                RuntimeBackgroundDecayRecommendation.REVIEW_LOW_MEMORY
        }
    }

    private fun buildCandidates(
        phase: RuntimeBackgroundDecayPhase,
        entries: List<RuntimeWorkloadRegistryEntry>
    ): List<RuntimeBackgroundDecayCandidate> {
        if (phase == RuntimeBackgroundDecayPhase.FOREGROUND_ALLOWANCE ||
            phase == RuntimeBackgroundDecayPhase.BACKGROUND_GRACE
        ) {
            return emptyList()
        }
        return entries
            .filter { entry ->
                entry.overChildBudget ||
                    entry.isCleanupReviewCandidate() ||
                    entry.isTransientReviewCandidate() ||
                    phase.reviewsBackgroundRights() && !entry.backgroundAllowed
            }
            .map { entry ->
                RuntimeBackgroundDecayCandidate(
                    workloadId = entry.workloadId,
                    workloadClass = entry.workloadClass,
                    retention = entry.retention,
                    lane = entry.suggestedLane,
                    reason = entry.decayCandidateReason()
                )
            }
            .sortedWith(
                compareByDescending<RuntimeBackgroundDecayCandidate> {
                    if (it.retention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE) 1 else 0
                }.thenBy { it.lane.ordinal }
                    .thenBy { it.workloadId }
            )
    }

    private fun RuntimeWorkloadRegistryEntry.isTransientReviewCandidate(): Boolean {
        return retention == RuntimeWorkloadRetention.LEASE ||
            workloadClass == RuntimeWorkloadClass.BUILD ||
            workloadClass == RuntimeWorkloadClass.PROBE ||
            workloadClass == RuntimeWorkloadClass.EPHEMERAL
    }

    private fun RuntimeWorkloadRegistryEntry.isCleanupReviewCandidate(): Boolean {
        return retention == RuntimeWorkloadRetention.CLEANUP_CANDIDATE ||
            workloadClass == RuntimeWorkloadClass.STRAY ||
            workloadClass == RuntimeWorkloadClass.UNKNOWN
    }

    private fun RuntimeWorkloadRegistryEntry.decayCandidateReason(): String {
        return when {
            overChildBudget -> "over_child_budget"
            isCleanupReviewCandidate() -> "cleanup_candidate_or_stray"
            isTransientReviewCandidate() -> "lease_or_transient_background_review"
            !backgroundAllowed -> "background_not_allowed"
            else -> "review"
        }
    }

    private fun RuntimeBackgroundDecayPhase.reviewsBackgroundRights(): Boolean {
        return this == RuntimeBackgroundDecayPhase.SERVICE_ONLY_REVIEW ||
            this == RuntimeBackgroundDecayPhase.LOW_ACTIVITY_REVIEW ||
            this == RuntimeBackgroundDecayPhase.PRESSURE_ACCELERATED_REVIEW ||
            this == RuntimeBackgroundDecayPhase.LOW_MEMORY_REVIEW
    }
}

private fun String?.toBackgroundDecayEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/-]"), "_")
        .take(160)
}
