package com.kite.app.foundation.runtime

internal enum class RuntimeProotAdaptivePlanRelation {
    ALIGNED_NO_CHANGE,
    PROMOTION_PENDING,
    DOWNGRADE_PENDING,
    CONTRACT_MISMATCH,
}

internal data class RuntimeProotAdaptivePlanningProjection(
    val schema: String = "proot_adaptive_planning_v1",
    val scope: String = "planned_not_production",
    val valid: Boolean,
    val relation: RuntimeProotAdaptivePlanRelation,
    val actualPolicySource: WarmProotPolicySource,
    val actualProfile: RuntimeLifecyclePolicyProfileGroup,
    val actualConfiguredGlobalMax: Int,
    val actualEffectiveGlobalMax: Int,
    val windowAction: RuntimeProotAdaptiveWindowAction,
    val windowReason: RuntimeProotAdaptiveWindowReason,
    val windowSampleCount: Long,
    val windowFailureCount: Long,
    val windowFailureRatePermille: Int,
    val windowTotalLatencyP95Bucket: BoundedProotLatencyBucket?,
    val recommendation: RuntimeProotAdaptiveRecommendation,
    val transitionReason: RuntimeProotAdaptiveTransitionReason,
    val plannedTargetGlobalMax: Int,
    val pendingApply: Boolean,
    val promotionWindowStreak: Int,
    val failureWindowStreak: Int,
    val cooldownRemainingMs: Long,
    val rollbackArmed: Boolean,
    val calibrationEvidenceStatus: RuntimeProotCalibrationEvidenceStatus,
    val calibrationGuardEligible: Boolean,
    val thermalEvidence: RuntimeProotThermalEvidence,
    val changesCoordinator: Boolean = false,
) {
    fun toRuntimeHealthEnvText(): String = buildString {
        appendLine("proot_adaptive_planned_schema=$schema")
        appendLine("proot_adaptive_planned_scope=$scope")
        appendLine("proot_adaptive_planned_valid=$valid")
        appendLine("proot_adaptive_planned_relation=${relation.name}")
        appendLine("proot_adaptive_actual_reference_scope=mirror_of_proot_actual_scheduler")
        appendLine("proot_adaptive_actual_reference_policy_source=${actualPolicySource.name}")
        appendLine("proot_adaptive_actual_reference_profile=${actualProfile.name}")
        appendLine("proot_adaptive_actual_reference_configured_global_max=$actualConfiguredGlobalMax")
        appendLine("proot_adaptive_actual_reference_effective_global_max=$actualEffectiveGlobalMax")
        appendLine("proot_adaptive_planned_window_action=${windowAction.name}")
        appendLine("proot_adaptive_planned_window_reason=${windowReason.name}")
        appendLine("proot_adaptive_planned_window_sample_count=$windowSampleCount")
        appendLine("proot_adaptive_planned_window_failure_count=$windowFailureCount")
        appendLine("proot_adaptive_planned_window_failure_rate_permille=$windowFailureRatePermille")
        appendLine(
            "proot_adaptive_planned_window_total_latency_p95_bucket=" +
                (windowTotalLatencyP95Bucket?.name ?: "NONE")
        )
        appendLine("proot_adaptive_planned_recommendation=${recommendation.name}")
        appendLine("proot_adaptive_planned_transition_reason=${transitionReason.name}")
        appendLine("proot_adaptive_planned_target_global_max=$plannedTargetGlobalMax")
        appendLine("proot_adaptive_planned_pending_apply=$pendingApply")
        appendLine("proot_adaptive_planned_promotion_window_streak=$promotionWindowStreak")
        appendLine("proot_adaptive_planned_failure_window_streak=$failureWindowStreak")
        appendLine("proot_adaptive_planned_cooldown_remaining_ms=$cooldownRemainingMs")
        appendLine("proot_adaptive_planned_rollback_armed=$rollbackArmed")
        appendLine("proot_adaptive_planned_calibration_status=${calibrationEvidenceStatus.name}")
        appendLine("proot_adaptive_planned_calibration_guard_eligible=$calibrationGuardEligible")
        appendLine("proot_adaptive_planned_thermal_evidence=${thermalEvidence.name}")
        appendLine("proot_adaptive_planned_changes_coordinator=$changesCoordinator")
        appendLine("proot_adaptive_planned_boundary=recommendation_is_not_actual_policy")
    }
}

/** 从调用方提供的不可变快照投影，不回读 coordinator、遥测 collector 或文件。 */
internal object RuntimeProotAdaptivePlanningProjector {
    fun project(
        nowMs: Long,
        actual: WarmProotExecutionCoordinator.TuningSnapshot,
        calibration: RuntimeProotCalibrationAlignmentResult,
        thermal: RuntimeProotThermalSignal,
        window: RuntimeProotAdaptiveWindowDecision,
        hysteresis: RuntimeProotAdaptiveHysteresisResult,
    ): RuntimeProotAdaptivePlanningProjection {
        val valid = contractValid(
            nowMs = nowMs,
            actual = actual,
            window = window,
            hysteresis = hysteresis,
        )
        val target = if (valid) {
            hysteresis.state.pendingTargetMax
                ?: hysteresis.recommendedConfiguredGlobalMax
        } else {
            actual.configuredGlobalMax
        }
        val relation = when {
            !valid -> RuntimeProotAdaptivePlanRelation.CONTRACT_MISMATCH
            target > actual.configuredGlobalMax -> RuntimeProotAdaptivePlanRelation.PROMOTION_PENDING
            target < actual.configuredGlobalMax -> RuntimeProotAdaptivePlanRelation.DOWNGRADE_PENDING
            else -> RuntimeProotAdaptivePlanRelation.ALIGNED_NO_CHANGE
        }
        return RuntimeProotAdaptivePlanningProjection(
            valid = valid,
            relation = relation,
            actualPolicySource = actual.policySource,
            actualProfile = actual.profileGroup,
            actualConfiguredGlobalMax = actual.configuredGlobalMax,
            actualEffectiveGlobalMax = actual.effectiveGlobalMax,
            windowAction = window.action,
            windowReason = window.reason,
            windowSampleCount = window.sampleCount.coerceAtLeast(0L),
            windowFailureCount = window.failureCount.coerceAtLeast(0L),
            windowFailureRatePermille = window.failureRatePermille.coerceIn(0, 1_000),
            windowTotalLatencyP95Bucket = window.totalLatencyP95Bucket,
            recommendation = if (valid) {
                hysteresis.recommendation
            } else {
                RuntimeProotAdaptiveRecommendation.NONE
            },
            transitionReason = if (valid) {
                hysteresis.reason
            } else {
                RuntimeProotAdaptiveTransitionReason.INVALID_WINDOW_REJECTED
            },
            plannedTargetGlobalMax = target,
            pendingApply = valid && hysteresis.state.pendingTargetMax != null,
            promotionWindowStreak = if (valid) {
                hysteresis.state.consecutivePromotionWindows
            } else {
                0
            },
            failureWindowStreak = if (valid) {
                hysteresis.state.consecutiveFailureWindows
            } else {
                0
            },
            cooldownRemainingMs = if (valid) {
                (hysteresis.state.cooldownUntilMs - nowMs).coerceAtLeast(0L)
            } else {
                0L
            },
            rollbackArmed = valid && hysteresis.state.rollbackTargetMax != null,
            calibrationEvidenceStatus = calibration.evidenceStatus,
            calibrationGuardEligible = calibration.eligibleAsSafetyGuard,
            thermalEvidence = thermal.evidence,
        )
    }

    private fun contractValid(
        nowMs: Long,
        actual: WarmProotExecutionCoordinator.TuningSnapshot,
        window: RuntimeProotAdaptiveWindowDecision,
        hysteresis: RuntimeProotAdaptiveHysteresisResult,
    ): Boolean {
        val limits = RuntimeProotCalibrationAlignment.productionProfileLimits()
        val allowed = setOf(limits.lowPower, limits.balanced, limits.highPerformance)
        if (nowMs <= 0L || actual.configuredGlobalMax !in allowed) return false
        if (window.scope != "planned_not_production" || window.changesCoordinator) return false
        if (hysteresis.scope != "planned_not_production" || hysteresis.changesCoordinator) return false
        if (window.currentConfiguredGlobalMax != actual.configuredGlobalMax) return false
        if (hysteresis.actualConfiguredGlobalMax != actual.configuredGlobalMax) return false
        if (hysteresis.state.lastObservedActualMax != actual.configuredGlobalMax) return false
        if (hysteresis.state.schema != RuntimeProotAdaptiveHysteresis.SCHEMA) return false
        if (
            hysteresis.state.consecutivePromotionWindows < 0 ||
            hysteresis.state.consecutiveFailureWindows < 0 ||
            hysteresis.state.cooldownUntilMs < 0L ||
            hysteresis.state.lastAppliedAtMs < 0L
        ) return false
        if (
            hysteresis.state.rollbackTargetMax != null &&
            hysteresis.state.rollbackTargetMax != adjacentLower(actual.configuredGlobalMax, limits)
        ) return false
        if (window.sampleCount < 0L || window.failureCount !in 0L..window.sampleCount) return false
        if (window.failureRatePermille !in 0..1_000) return false
        val expectedFailureRate = if (window.sampleCount == 0L) {
            0
        } else {
            ((window.failureCount * 1_000L) / window.sampleCount).toInt()
        }
        if (window.failureRatePermille != expectedFailureRate) return false
        val windowTargetValid = when (window.action) {
            RuntimeProotAdaptiveWindowAction.HOLD ->
                window.suggestedConfiguredGlobalMax == actual.configuredGlobalMax
            RuntimeProotAdaptiveWindowAction.PROMOTION_WINDOW_ELIGIBLE ->
                window.suggestedConfiguredGlobalMax != actual.configuredGlobalMax &&
                    window.suggestedConfiguredGlobalMax == adjacentHigher(actual.configuredGlobalMax, limits)
            RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE ->
                window.suggestedConfiguredGlobalMax != actual.configuredGlobalMax &&
                    window.suggestedConfiguredGlobalMax == adjacentLower(actual.configuredGlobalMax, limits)
        }
        if (!windowTargetValid) return false

        val pendingTarget = hysteresis.state.pendingTargetMax
        val target = pendingTarget
            ?: hysteresis.recommendedConfiguredGlobalMax
        if (target !in allowed) return false
        return when (hysteresis.recommendation) {
            RuntimeProotAdaptiveRecommendation.NONE -> if (pendingTarget == null) {
                hysteresis.recommendedConfiguredGlobalMax == actual.configuredGlobalMax
            } else {
                hysteresis.recommendedConfiguredGlobalMax == pendingTarget &&
                    isAdjacent(actual.configuredGlobalMax, pendingTarget, limits)
            }
            RuntimeProotAdaptiveRecommendation.PROMOTE_ONE ->
                pendingTarget == target &&
                    target != actual.configuredGlobalMax &&
                    target == adjacentHigher(actual.configuredGlobalMax, limits)
            RuntimeProotAdaptiveRecommendation.DOWNGRADE_ONE ->
                pendingTarget == target &&
                    target != actual.configuredGlobalMax &&
                    target == adjacentLower(actual.configuredGlobalMax, limits)
        }
    }

    private fun adjacentHigher(
        value: Int,
        limits: RuntimeProotProductionProfileLimits,
    ): Int = when (value) {
        limits.lowPower -> limits.balanced
        limits.balanced -> limits.highPerformance
        else -> value
    }

    private fun adjacentLower(
        value: Int,
        limits: RuntimeProotProductionProfileLimits,
    ): Int = when (value) {
        limits.highPerformance -> limits.balanced
        limits.balanced -> limits.lowPower
        else -> value
    }

    private fun isAdjacent(
        actual: Int,
        target: Int,
        limits: RuntimeProotProductionProfileLimits,
    ): Boolean =
        target != actual &&
            (target == adjacentHigher(actual, limits) || target == adjacentLower(actual, limits))
}
