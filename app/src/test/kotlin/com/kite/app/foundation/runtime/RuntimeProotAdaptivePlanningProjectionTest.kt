package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProotAdaptivePlanningProjectionTest {
    @Test
    fun `promotion recommendation stays visibly separate from actual scheduler`() {
        val projection = project(
            window = healthyWindow(),
            hysteresis = hysteresis(
                recommendation = RuntimeProotAdaptiveRecommendation.PROMOTE_ONE,
                reason = RuntimeProotAdaptiveTransitionReason.PROMOTION_READY,
                recommendedMax = 4,
                state = RuntimeProotAdaptiveHysteresisState(
                    lastObservedActualMax = 2,
                    pendingTargetMax = 4,
                ),
            ),
        )

        assertTrue(projection.valid)
        assertEquals(RuntimeProotAdaptivePlanRelation.PROMOTION_PENDING, projection.relation)
        assertEquals(2, projection.actualConfiguredGlobalMax)
        assertEquals(4, projection.plannedTargetGlobalMax)
        assertTrue(projection.pendingApply)
        assertFalse(projection.changesCoordinator)
    }

    @Test
    fun `waiting recommendation remains pending without claiming a second action`() {
        val projection = project(
            window = healthyWindow(),
            hysteresis = hysteresis(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.WAITING_FOR_APPLY,
                recommendedMax = 4,
                state = RuntimeProotAdaptiveHysteresisState(
                    lastObservedActualMax = 2,
                    pendingTargetMax = 4,
                ),
            ),
        )

        assertTrue(projection.valid)
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, projection.recommendation)
        assertEquals(RuntimeProotAdaptivePlanRelation.PROMOTION_PENDING, projection.relation)
        assertEquals(4, projection.plannedTargetGlobalMax)
    }

    @Test
    fun `aligned hold exposes bounded evidence and cooldown remaining`() {
        val projection = project(
            nowMs = NOW_MS,
            window = holdWindow(),
            hysteresis = hysteresis(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.PROMOTION_COOLDOWN,
                recommendedMax = 2,
                state = RuntimeProotAdaptiveHysteresisState(
                    lastObservedActualMax = 2,
                    cooldownUntilMs = NOW_MS + 45_000L,
                    rollbackTargetMax = 1,
                ),
            ),
        )

        assertEquals(RuntimeProotAdaptivePlanRelation.ALIGNED_NO_CHANGE, projection.relation)
        assertEquals(45_000L, projection.cooldownRemainingMs)
        assertTrue(projection.rollbackArmed)
        assertEquals(20L, projection.windowSampleCount)
        assertEquals(RuntimeProotThermalEvidence.UNAVAILABLE, projection.thermalEvidence)
    }

    @Test
    fun `stale actual or non adjacent target fails closed`() {
        val staleActual = project(
            window = healthyWindow().copy(currentConfiguredGlobalMax = 1),
            hysteresis = hysteresis(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.HOLD_WINDOW,
                recommendedMax = 2,
            ),
        )
        assertFalse(staleActual.valid)
        assertEquals(RuntimeProotAdaptivePlanRelation.CONTRACT_MISMATCH, staleActual.relation)
        assertEquals(2, staleActual.plannedTargetGlobalMax)
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, staleActual.recommendation)

        val jumpingTarget = project(
            actual = actual(profile = RuntimeLifecyclePolicyProfileGroup.LOW_POWER),
            window = healthyWindow(current = 1, target = 4),
            hysteresis = hysteresis(
                actualMax = 1,
                recommendation = RuntimeProotAdaptiveRecommendation.PROMOTE_ONE,
                reason = RuntimeProotAdaptiveTransitionReason.PROMOTION_READY,
                recommendedMax = 4,
                state = RuntimeProotAdaptiveHysteresisState(
                    lastObservedActualMax = 1,
                    pendingTargetMax = 4,
                ),
            ),
        )
        assertFalse(jumpingTarget.valid)
        assertEquals(1, jumpingTarget.plannedTargetGlobalMax)
    }

    @Test
    fun `health text uses fixed low cardinality fields and explicit boundary`() {
        val text = project(
            window = healthyWindow(),
            hysteresis = hysteresis(
                recommendation = RuntimeProotAdaptiveRecommendation.PROMOTE_ONE,
                reason = RuntimeProotAdaptiveTransitionReason.PROMOTION_READY,
                recommendedMax = 4,
                state = RuntimeProotAdaptiveHysteresisState(
                    lastObservedActualMax = 2,
                    pendingTargetMax = 4,
                ),
            ),
        ).toRuntimeHealthEnvText()

        assertTrue(text.contains("proot_adaptive_planned_scope=planned_not_production"))
        assertTrue(text.contains("proot_adaptive_actual_reference_scope=mirror_of_proot_actual_scheduler"))
        assertTrue(text.contains("proot_adaptive_planned_relation=PROMOTION_PENDING"))
        assertTrue(text.contains("proot_adaptive_planned_changes_coordinator=false"))
        assertTrue(text.contains("recommendation_is_not_actual_policy"))
        listOf(
            "owner_id",
            "lease_id",
            "host_pid",
            "process_start",
            "argv",
            "cwd",
            "command=",
            "session_id",
            "resource_id",
        ).forEach { forbidden ->
            assertFalse("projection must not contain $forbidden", text.contains(forbidden, ignoreCase = true))
        }
    }

    private fun project(
        nowMs: Long = NOW_MS,
        actual: WarmProotExecutionCoordinator.TuningSnapshot = actual(),
        window: RuntimeProotAdaptiveWindowDecision,
        hysteresis: RuntimeProotAdaptiveHysteresisResult,
    ) = RuntimeProotAdaptivePlanningProjector.project(
        nowMs = nowMs,
        actual = actual,
        calibration = RuntimeProotCalibrationAlignmentResult(
            evidenceStatus = RuntimeProotCalibrationEvidenceStatus.READY_TRACE_GUARD_ONLY,
            currentProfile = actual.profileGroup,
            currentConfiguredGlobalMax = actual.configuredGlobalMax,
            productionProfileLimits = RuntimeProotProductionProfileLimits(1, 2, 4),
            eligibleAsSafetyGuard = true,
            reason = "ready",
        ),
        thermal = RuntimeProotThermalSignal(),
        window = window,
        hysteresis = hysteresis,
    )

    private fun actual(
        profile: RuntimeLifecyclePolicyProfileGroup = RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
    ): WarmProotExecutionCoordinator.TuningSnapshot {
        val max = when (profile) {
            RuntimeLifecyclePolicyProfileGroup.LOW_POWER -> 1
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED -> 2
            RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE -> 4
            RuntimeLifecyclePolicyProfileGroup.CUSTOM -> 2
        }
        return WarmProotExecutionCoordinator.TuningSnapshot(
            policySource = WarmProotPolicySource.RUNTIME_HEALTH,
            profileGroup = profile,
            pressure = RuntimePressureLevel.NORMAL,
            foreground = true,
            configuredGlobalMax = max,
            effectiveGlobalMax = max,
            maxWarmRunners = max,
            idleTimeoutMs = 30_000L,
            activeJobs = 0,
            queuedJobs = 0,
            totalWarmSessions = 0,
            activeWarmSessions = 0,
            idleWarmSessions = 0,
            staleWarmSessions = 0,
            oldestIdleAgeMs = 0L,
        )
    }

    private fun healthyWindow(current: Int = 2, target: Int = 4) =
        RuntimeProotAdaptiveWindowDecision(
            action = RuntimeProotAdaptiveWindowAction.PROMOTION_WINDOW_ELIGIBLE,
            reason = RuntimeProotAdaptiveWindowReason.HEALTHY_WINDOW,
            currentConfiguredGlobalMax = current,
            suggestedConfiguredGlobalMax = target,
            sampleCount = 20L,
            totalLatencyP95Bucket = BoundedProotLatencyBucket.LE_100_MS,
        )

    private fun holdWindow() = RuntimeProotAdaptiveWindowDecision(
        action = RuntimeProotAdaptiveWindowAction.HOLD,
        reason = RuntimeProotAdaptiveWindowReason.THERMAL_SIGNAL_UNAVAILABLE,
        currentConfiguredGlobalMax = 2,
        suggestedConfiguredGlobalMax = 2,
        sampleCount = 20L,
        totalLatencyP95Bucket = BoundedProotLatencyBucket.LE_100_MS,
    )

    private fun hysteresis(
        actualMax: Int = 2,
        recommendation: RuntimeProotAdaptiveRecommendation,
        reason: RuntimeProotAdaptiveTransitionReason,
        recommendedMax: Int,
        state: RuntimeProotAdaptiveHysteresisState = RuntimeProotAdaptiveHysteresisState(
            lastObservedActualMax = actualMax,
        ),
    ) = RuntimeProotAdaptiveHysteresisResult(
        recommendation = recommendation,
        reason = reason,
        actualConfiguredGlobalMax = actualMax,
        recommendedConfiguredGlobalMax = recommendedMax,
        state = state,
    )

    companion object {
        private const val NOW_MS = 1_000_000L
    }
}
