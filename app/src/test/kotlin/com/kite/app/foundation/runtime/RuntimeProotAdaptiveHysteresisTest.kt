package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeProotAdaptiveHysteresisTest {
    @Test
    fun `three consecutive healthy windows produce one promotion recommendation`() {
        var state = RuntimeProotAdaptiveHysteresis.initial(2)

        val first = advance(state, healthy(2, 4), AT_MS)
        state = first.state
        assertEquals(RuntimeProotAdaptiveTransitionReason.PROMOTION_STREAK_BUILDING, first.reason)
        assertEquals(1, state.consecutivePromotionWindows)

        val second = advance(state, healthy(2, 4), AT_MS + WINDOW_MS)
        state = second.state
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, second.recommendation)
        assertEquals(2, state.consecutivePromotionWindows)

        val third = advance(state, healthy(2, 4), AT_MS + 2 * WINDOW_MS)
        assertEquals(RuntimeProotAdaptiveRecommendation.PROMOTE_ONE, third.recommendation)
        assertEquals(RuntimeProotAdaptiveTransitionReason.PROMOTION_READY, third.reason)
        assertEquals(4, third.recommendedConfiguredGlobalMax)
        assertEquals(4, third.state.pendingTargetMax)
        assertFalse(third.changesCoordinator)

        val fourth = advance(third.state, healthy(2, 4), AT_MS + 3 * WINDOW_MS)
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, fourth.recommendation)
        assertEquals(RuntimeProotAdaptiveTransitionReason.WAITING_FOR_APPLY, fourth.reason)
        assertEquals(4, fourth.recommendedConfiguredGlobalMax)
    }

    @Test
    fun `hold window resets streak and cancels unapplied promotion`() {
        var state = RuntimeProotAdaptiveHysteresis.initial(2)
        state = advance(state, healthy(2, 4), AT_MS).state
        val reset = advance(state, hold(2), AT_MS + WINDOW_MS)
        assertEquals(0, reset.state.consecutivePromotionWindows)

        state = advance(reset.state, healthy(2, 4), AT_MS + 2 * WINDOW_MS).state
        state = advance(state, healthy(2, 4), AT_MS + 3 * WINDOW_MS).state
        state = advance(state, healthy(2, 4), AT_MS + 4 * WINDOW_MS).state
        assertEquals(4, state.pendingTargetMax)

        val cancelled = advance(state, hold(2), AT_MS + 5 * WINDOW_MS)
        assertEquals(RuntimeProotAdaptiveTransitionReason.PENDING_PROMOTION_CANCELLED, cancelled.reason)
        assertNull(cancelled.state.pendingTargetMax)
    }

    @Test
    fun `promotion acknowledgement starts cooldown and keeps rollback target`() {
        var state = promotedCandidateState()
        val appliedAt = AT_MS + 3 * WINDOW_MS
        val acknowledged = RuntimeProotAdaptiveHysteresis.advance(
            state = state,
            actualConfiguredGlobalMax = 4,
            observedAtMs = appliedAt,
            window = healthy(4, 4),
            policy = POLICY,
        )

        assertEquals(RuntimeProotAdaptiveTransitionReason.APPLY_ACKNOWLEDGED, acknowledged.reason)
        assertEquals(2, acknowledged.state.rollbackTargetMax)
        assertEquals(appliedAt + POLICY.cooldownMs, acknowledged.state.cooldownUntilMs)
        assertNull(acknowledged.state.pendingTargetMax)

        state = acknowledged.state
        val cooldown = advance(
            state = state,
            window = healthy(4, 4),
            atMs = appliedAt + WINDOW_MS,
            actualMax = 4,
        )
        assertEquals(RuntimeProotAdaptiveTransitionReason.PROMOTION_COOLDOWN, cooldown.reason)
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, cooldown.recommendation)
    }

    @Test
    fun `urgent pressure bypasses cooldown and recommends one step rollback`() {
        val acknowledged = RuntimeProotAdaptiveHysteresis.advance(
            state = promotedCandidateState(),
            actualConfiguredGlobalMax = 4,
            observedAtMs = AT_MS + 3 * WINDOW_MS,
            window = healthy(4, 4),
            policy = POLICY,
        )
        val urgent = advance(
            state = acknowledged.state,
            actualMax = 4,
            atMs = AT_MS + 4 * WINDOW_MS,
            window = downgrade(
                current = 4,
                target = 2,
                reason = RuntimeProotAdaptiveWindowReason.MEMORY_PRESSURE_HIGH,
            ),
        )

        assertEquals(RuntimeProotAdaptiveRecommendation.DOWNGRADE_ONE, urgent.recommendation)
        assertEquals(RuntimeProotAdaptiveTransitionReason.URGENT_PRESSURE_DOWNGRADE, urgent.reason)
        assertEquals(2, urgent.recommendedConfiguredGlobalMax)
        assertEquals(2, urgent.state.pendingTargetMax)
    }

    @Test
    fun `failure downgrade spends two consecutive windows before recommendation`() {
        var state = RuntimeProotAdaptiveHysteresis.initial(4)
        val failure = downgrade(
            current = 4,
            target = 2,
            reason = RuntimeProotAdaptiveWindowReason.FAILURE_RATE_HIGH,
        )
        val first = advance(state, failure, AT_MS, actualMax = 4)
        state = first.state
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, first.recommendation)
        assertEquals(RuntimeProotAdaptiveTransitionReason.FAILURE_BUDGET_BUILDING, first.reason)
        assertEquals(1, state.consecutiveFailureWindows)

        val second = advance(state, failure, AT_MS + WINDOW_MS, actualMax = 4)
        assertEquals(RuntimeProotAdaptiveRecommendation.DOWNGRADE_ONE, second.recommendation)
        assertEquals(RuntimeProotAdaptiveTransitionReason.FAILURE_BUDGET_EXHAUSTED, second.reason)
        assertEquals(2, second.state.pendingTargetMax)
    }

    @Test
    fun `good or hold window breaks failure sequence`() {
        var state = RuntimeProotAdaptiveHysteresis.initial(4)
        val failure = downgrade(
            current = 4,
            target = 2,
            reason = RuntimeProotAdaptiveWindowReason.FAILURE_RATE_HIGH,
        )
        state = advance(state, failure, AT_MS, actualMax = 4).state
        state = advance(state, hold(4), AT_MS + WINDOW_MS, actualMax = 4).state
        val nextFailure = advance(state, failure, AT_MS + 2 * WINDOW_MS, actualMax = 4)

        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, nextFailure.recommendation)
        assertEquals(1, nextFailure.state.consecutiveFailureWindows)
    }

    @Test
    fun `restore never converts pending or external actual change into a jump`() {
        val pending = promotedCandidateState()
        val sameActual = RuntimeProotAdaptiveHysteresis.restore(
            persisted = pending,
            actualConfiguredGlobalMax = 2,
            nowMs = AT_MS,
            policy = POLICY,
        )
        assertEquals(RuntimeProotAdaptiveTransitionReason.WAITING_FOR_APPLY, sameActual.reason)
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, sameActual.recommendation)
        assertEquals(4, sameActual.recommendedConfiguredGlobalMax)

        val changedActual = RuntimeProotAdaptiveHysteresis.restore(
            persisted = pending,
            actualConfiguredGlobalMax = 4,
            nowMs = AT_MS,
            policy = POLICY,
        )
        assertEquals(RuntimeProotAdaptiveTransitionReason.ACTUAL_STATE_REBASED, changedActual.reason)
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, changedActual.recommendation)
        assertEquals(4, changedActual.state.lastObservedActualMax)
        assertNull(changedActual.state.pendingTargetMax)
        assertEquals(AT_MS + POLICY.cooldownMs, changedActual.state.cooldownUntilMs)
    }

    @Test
    fun `invalid persisted state resets safely and malformed window is rejected`() {
        val invalid = RuntimeProotAdaptiveHysteresisState(
            schema = "unknown",
            lastObservedActualMax = 2,
            pendingTargetMax = 4,
        )
        val restored = RuntimeProotAdaptiveHysteresis.restore(
            persisted = invalid,
            actualConfiguredGlobalMax = 2,
            nowMs = AT_MS,
            policy = POLICY,
        )
        assertEquals(RuntimeProotAdaptiveTransitionReason.INVALID_STATE_RESET, restored.reason)
        assertNull(restored.state.pendingTargetMax)

        val malformedWindow = healthy(2, 4).copy(
            currentConfiguredGlobalMax = 1,
        )
        val rejected = advance(
            state = RuntimeProotAdaptiveHysteresis.initial(2),
            window = malformedWindow,
            atMs = AT_MS,
        )
        assertEquals(RuntimeProotAdaptiveTransitionReason.INVALID_WINDOW_REJECTED, rejected.reason)
        assertEquals(RuntimeProotAdaptiveRecommendation.NONE, rejected.recommendation)
    }

    private fun promotedCandidateState(): RuntimeProotAdaptiveHysteresisState {
        var state = RuntimeProotAdaptiveHysteresis.initial(2)
        repeat(3) { index ->
            state = advance(
                state = state,
                window = healthy(2, 4),
                atMs = AT_MS + index * WINDOW_MS,
            ).state
        }
        return state
    }

    private fun advance(
        state: RuntimeProotAdaptiveHysteresisState,
        window: RuntimeProotAdaptiveWindowDecision,
        atMs: Long,
        actualMax: Int = 2,
    ): RuntimeProotAdaptiveHysteresisResult = RuntimeProotAdaptiveHysteresis.advance(
        state = state,
        actualConfiguredGlobalMax = actualMax,
        observedAtMs = atMs,
        window = window,
        policy = POLICY,
    )

    private fun healthy(current: Int, target: Int) = window(
        action = RuntimeProotAdaptiveWindowAction.PROMOTION_WINDOW_ELIGIBLE,
        reason = RuntimeProotAdaptiveWindowReason.HEALTHY_WINDOW,
        current = current,
        target = target,
    )

    private fun hold(current: Int) = window(
        action = RuntimeProotAdaptiveWindowAction.HOLD,
        reason = RuntimeProotAdaptiveWindowReason.THERMAL_SIGNAL_UNAVAILABLE,
        current = current,
        target = current,
    )

    private fun downgrade(
        current: Int,
        target: Int,
        reason: RuntimeProotAdaptiveWindowReason,
    ) = window(
        action = RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE,
        reason = reason,
        current = current,
        target = target,
    )

    private fun window(
        action: RuntimeProotAdaptiveWindowAction,
        reason: RuntimeProotAdaptiveWindowReason,
        current: Int,
        target: Int,
    ) = RuntimeProotAdaptiveWindowDecision(
        action = action,
        reason = reason,
        currentConfiguredGlobalMax = current,
        suggestedConfiguredGlobalMax = target,
        sampleCount = 20L,
        totalLatencyP95Bucket = BoundedProotLatencyBucket.LE_100_MS,
    )

    companion object {
        private const val AT_MS = 1_000_000L
        private const val WINDOW_MS = 60_000L
        private val POLICY = RuntimeProotAdaptiveHysteresisPolicy(
            promotionWindowsRequired = 3,
            failureWindowsRequired = 2,
            cooldownMs = 5 * WINDOW_MS,
        )
    }
}
