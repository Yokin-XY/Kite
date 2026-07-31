package com.kite.app.foundation.runtime

internal enum class RuntimeProotAdaptiveRecommendation {
    NONE,
    PROMOTE_ONE,
    DOWNGRADE_ONE,
}

internal enum class RuntimeProotAdaptiveTransitionReason {
    HOLD_WINDOW,
    PROMOTION_STREAK_BUILDING,
    PROMOTION_READY,
    PROMOTION_COOLDOWN,
    FAILURE_BUDGET_BUILDING,
    FAILURE_BUDGET_EXHAUSTED,
    URGENT_PRESSURE_DOWNGRADE,
    WAITING_FOR_APPLY,
    PENDING_PROMOTION_CANCELLED,
    APPLY_ACKNOWLEDGED,
    ACTUAL_STATE_REBASED,
    INVALID_STATE_RESET,
    INVALID_WINDOW_REJECTED,
}

internal data class RuntimeProotAdaptiveHysteresisPolicy(
    val promotionWindowsRequired: Int = 3,
    val failureWindowsRequired: Int = 2,
    val cooldownMs: Long = 10 * 60_000L,
) {
    init {
        require(promotionWindowsRequired >= 2)
        require(failureWindowsRequired >= 1)
        require(cooldownMs >= RuntimeProotAdaptiveSignalGate.MIN_WINDOW_MS)
    }
}

internal data class RuntimeProotAdaptiveHysteresisState(
    val schema: String = RuntimeProotAdaptiveHysteresis.SCHEMA,
    val lastObservedActualMax: Int,
    val consecutivePromotionWindows: Int = 0,
    val consecutiveFailureWindows: Int = 0,
    val pendingTargetMax: Int? = null,
    val cooldownUntilMs: Long = 0L,
    val rollbackTargetMax: Int? = null,
    val lastAppliedAtMs: Long = 0L,
)

internal data class RuntimeProotAdaptiveHysteresisResult(
    val scope: String = "planned_not_production",
    val recommendation: RuntimeProotAdaptiveRecommendation,
    val reason: RuntimeProotAdaptiveTransitionReason,
    val actualConfiguredGlobalMax: Int,
    val recommendedConfiguredGlobalMax: Int,
    val state: RuntimeProotAdaptiveHysteresisState,
    val changesCoordinator: Boolean = false,
)

/** RF720 窗口之上的纯迟滞状态机；它只保存候选历史，不拥有 actual 策略。 */
internal object RuntimeProotAdaptiveHysteresis {
    const val SCHEMA = "proot_adaptive_hysteresis_v1"

    fun initial(actualConfiguredGlobalMax: Int): RuntimeProotAdaptiveHysteresisState {
        require(actualConfiguredGlobalMax in allowedLimits())
        return RuntimeProotAdaptiveHysteresisState(
            lastObservedActualMax = actualConfiguredGlobalMax,
        )
    }

    fun restore(
        persisted: RuntimeProotAdaptiveHysteresisState,
        actualConfiguredGlobalMax: Int,
        nowMs: Long,
        policy: RuntimeProotAdaptiveHysteresisPolicy = RuntimeProotAdaptiveHysteresisPolicy(),
    ): RuntimeProotAdaptiveHysteresisResult {
        require(actualConfiguredGlobalMax in allowedLimits())
        require(nowMs > 0L)

        if (!persisted.isStructurallyValid(policy)) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.INVALID_STATE_RESET,
                actualMax = actualConfiguredGlobalMax,
                recommendedMax = actualConfiguredGlobalMax,
                state = initial(actualConfiguredGlobalMax).copy(
                    cooldownUntilMs = nowMs + policy.cooldownMs,
                ),
            )
        }
        if (
            persisted.lastObservedActualMax != actualConfiguredGlobalMax ||
            persisted.pendingTargetMax == actualConfiguredGlobalMax
        ) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.ACTUAL_STATE_REBASED,
                actualMax = actualConfiguredGlobalMax,
                recommendedMax = actualConfiguredGlobalMax,
                state = persisted.copy(
                    lastObservedActualMax = actualConfiguredGlobalMax,
                    consecutivePromotionWindows = 0,
                    consecutiveFailureWindows = 0,
                    pendingTargetMax = null,
                    cooldownUntilMs = maxOf(persisted.cooldownUntilMs, nowMs + policy.cooldownMs),
                    rollbackTargetMax = null,
                    lastAppliedAtMs = nowMs,
                ),
            )
        }
        return result(
            recommendation = RuntimeProotAdaptiveRecommendation.NONE,
            reason = if (persisted.pendingTargetMax != null) {
                RuntimeProotAdaptiveTransitionReason.WAITING_FOR_APPLY
            } else {
                RuntimeProotAdaptiveTransitionReason.HOLD_WINDOW
            },
            actualMax = actualConfiguredGlobalMax,
            recommendedMax = persisted.pendingTargetMax ?: actualConfiguredGlobalMax,
            state = persisted,
        )
    }

    fun advance(
        state: RuntimeProotAdaptiveHysteresisState,
        actualConfiguredGlobalMax: Int,
        observedAtMs: Long,
        window: RuntimeProotAdaptiveWindowDecision,
        policy: RuntimeProotAdaptiveHysteresisPolicy = RuntimeProotAdaptiveHysteresisPolicy(),
    ): RuntimeProotAdaptiveHysteresisResult {
        require(actualConfiguredGlobalMax in allowedLimits())
        require(observedAtMs > 0L)

        if (!state.isStructurallyValid(policy)) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.INVALID_STATE_RESET,
                actualMax = actualConfiguredGlobalMax,
                recommendedMax = actualConfiguredGlobalMax,
                state = initial(actualConfiguredGlobalMax).copy(
                    cooldownUntilMs = observedAtMs + policy.cooldownMs,
                ),
            )
        }
        if (state.lastObservedActualMax != actualConfiguredGlobalMax) {
            val expectedApply = state.pendingTargetMax == actualConfiguredGlobalMax
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = if (expectedApply) {
                    RuntimeProotAdaptiveTransitionReason.APPLY_ACKNOWLEDGED
                } else {
                    RuntimeProotAdaptiveTransitionReason.ACTUAL_STATE_REBASED
                },
                actualMax = actualConfiguredGlobalMax,
                recommendedMax = actualConfiguredGlobalMax,
                state = state.copy(
                    lastObservedActualMax = actualConfiguredGlobalMax,
                    consecutivePromotionWindows = 0,
                    consecutiveFailureWindows = 0,
                    pendingTargetMax = null,
                    cooldownUntilMs = maxOf(state.cooldownUntilMs, observedAtMs + policy.cooldownMs),
                    rollbackTargetMax = if (
                        expectedApply && actualConfiguredGlobalMax > state.lastObservedActualMax
                    ) {
                        state.lastObservedActualMax
                    } else {
                        null
                    },
                    lastAppliedAtMs = if (expectedApply) observedAtMs else state.lastAppliedAtMs,
                ),
            )
        }
        if (!window.matchesActual(actualConfiguredGlobalMax)) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.INVALID_WINDOW_REJECTED,
                actualMax = actualConfiguredGlobalMax,
                recommendedMax = actualConfiguredGlobalMax,
                state = state.copy(
                    consecutivePromotionWindows = 0,
                    consecutiveFailureWindows = 0,
                    pendingTargetMax = null,
                ),
            )
        }

        if (
            state.pendingTargetMax != null &&
            state.pendingTargetMax < actualConfiguredGlobalMax
        ) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.WAITING_FOR_APPLY,
                actualMax = actualConfiguredGlobalMax,
                recommendedMax = state.pendingTargetMax,
                state = state,
            )
        }

        return when (window.action) {
            RuntimeProotAdaptiveWindowAction.HOLD -> holdWindow(
                state = state,
                actualMax = actualConfiguredGlobalMax,
                observedAtMs = observedAtMs,
            )
            RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE -> downgradeWindow(
                state = state,
                actualMax = actualConfiguredGlobalMax,
                window = window,
                policy = policy,
            )
            RuntimeProotAdaptiveWindowAction.PROMOTION_WINDOW_ELIGIBLE -> promotionWindow(
                state = state,
                actualMax = actualConfiguredGlobalMax,
                observedAtMs = observedAtMs,
                window = window,
                policy = policy,
            )
        }
    }

    private fun holdWindow(
        state: RuntimeProotAdaptiveHysteresisState,
        actualMax: Int,
        observedAtMs: Long,
    ): RuntimeProotAdaptiveHysteresisResult {
        val cancelledPromotion = state.pendingTargetMax?.let { it > actualMax } == true
        val stablePastRollback =
            state.rollbackTargetMax != null && observedAtMs >= state.cooldownUntilMs
        return result(
            recommendation = RuntimeProotAdaptiveRecommendation.NONE,
            reason = if (cancelledPromotion) {
                RuntimeProotAdaptiveTransitionReason.PENDING_PROMOTION_CANCELLED
            } else {
                RuntimeProotAdaptiveTransitionReason.HOLD_WINDOW
            },
            actualMax = actualMax,
            recommendedMax = actualMax,
            state = state.copy(
                consecutivePromotionWindows = 0,
                consecutiveFailureWindows = 0,
                pendingTargetMax = null,
                rollbackTargetMax = if (stablePastRollback) null else state.rollbackTargetMax,
            ),
        )
    }

    private fun promotionWindow(
        state: RuntimeProotAdaptiveHysteresisState,
        actualMax: Int,
        observedAtMs: Long,
        window: RuntimeProotAdaptiveWindowDecision,
        policy: RuntimeProotAdaptiveHysteresisPolicy,
    ): RuntimeProotAdaptiveHysteresisResult {
        if (state.pendingTargetMax != null) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.WAITING_FOR_APPLY,
                actualMax = actualMax,
                recommendedMax = state.pendingTargetMax,
                state = state,
            )
        }
        if (observedAtMs < state.cooldownUntilMs) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.PROMOTION_COOLDOWN,
                actualMax = actualMax,
                recommendedMax = actualMax,
                state = state.copy(
                    consecutivePromotionWindows = 0,
                    consecutiveFailureWindows = 0,
                ),
            )
        }

        val streak = (state.consecutivePromotionWindows + 1)
            .coerceAtMost(policy.promotionWindowsRequired)
        if (streak < policy.promotionWindowsRequired) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.PROMOTION_STREAK_BUILDING,
                actualMax = actualMax,
                recommendedMax = actualMax,
                state = state.copy(
                    consecutivePromotionWindows = streak,
                    consecutiveFailureWindows = 0,
                ),
            )
        }
        return result(
            recommendation = RuntimeProotAdaptiveRecommendation.PROMOTE_ONE,
            reason = RuntimeProotAdaptiveTransitionReason.PROMOTION_READY,
            actualMax = actualMax,
            recommendedMax = window.suggestedConfiguredGlobalMax,
            state = state.copy(
                consecutivePromotionWindows = 0,
                consecutiveFailureWindows = 0,
                pendingTargetMax = window.suggestedConfiguredGlobalMax,
            ),
        )
    }

    private fun downgradeWindow(
        state: RuntimeProotAdaptiveHysteresisState,
        actualMax: Int,
        window: RuntimeProotAdaptiveWindowDecision,
        policy: RuntimeProotAdaptiveHysteresisPolicy,
    ): RuntimeProotAdaptiveHysteresisResult {
        val baseState = state.copy(
            consecutivePromotionWindows = 0,
            pendingTargetMax = null,
        )
        if (window.reason.isUrgentPressure()) {
            return proposeDowngrade(
                state = baseState.copy(consecutiveFailureWindows = 0),
                actualMax = actualMax,
                targetMax = window.suggestedConfiguredGlobalMax,
                reason = RuntimeProotAdaptiveTransitionReason.URGENT_PRESSURE_DOWNGRADE,
            )
        }

        val failures = (baseState.consecutiveFailureWindows + 1)
            .coerceAtMost(policy.failureWindowsRequired)
        if (failures < policy.failureWindowsRequired) {
            return result(
                recommendation = RuntimeProotAdaptiveRecommendation.NONE,
                reason = RuntimeProotAdaptiveTransitionReason.FAILURE_BUDGET_BUILDING,
                actualMax = actualMax,
                recommendedMax = actualMax,
                state = baseState.copy(consecutiveFailureWindows = failures),
            )
        }
        return proposeDowngrade(
            state = baseState.copy(consecutiveFailureWindows = 0),
            actualMax = actualMax,
            targetMax = window.suggestedConfiguredGlobalMax,
            reason = RuntimeProotAdaptiveTransitionReason.FAILURE_BUDGET_EXHAUSTED,
        )
    }

    private fun proposeDowngrade(
        state: RuntimeProotAdaptiveHysteresisState,
        actualMax: Int,
        targetMax: Int,
        reason: RuntimeProotAdaptiveTransitionReason,
    ): RuntimeProotAdaptiveHysteresisResult = result(
        recommendation = RuntimeProotAdaptiveRecommendation.DOWNGRADE_ONE,
        reason = reason,
        actualMax = actualMax,
        recommendedMax = targetMax,
        state = state.copy(pendingTargetMax = targetMax),
    )

    private fun RuntimeProotAdaptiveWindowDecision.matchesActual(actualMax: Int): Boolean {
        if (scope != "planned_not_production" || changesCoordinator) return false
        if (currentConfiguredGlobalMax != actualMax) return false
        if (currentConfiguredGlobalMax !in allowedLimits()) return false
        return when (action) {
            RuntimeProotAdaptiveWindowAction.HOLD ->
                suggestedConfiguredGlobalMax == actualMax
            RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE ->
                suggestedConfiguredGlobalMax == adjacentLower(actualMax)
            RuntimeProotAdaptiveWindowAction.PROMOTION_WINDOW_ELIGIBLE ->
                suggestedConfiguredGlobalMax == adjacentHigher(actualMax)
        }
    }

    private fun RuntimeProotAdaptiveWindowReason.isUrgentPressure(): Boolean =
        this == RuntimeProotAdaptiveWindowReason.MEMORY_PRESSURE_HIGH ||
            this == RuntimeProotAdaptiveWindowReason.THERMAL_PRESSURE_HIGH

    private fun RuntimeProotAdaptiveHysteresisState.isStructurallyValid(
        policy: RuntimeProotAdaptiveHysteresisPolicy,
    ): Boolean {
        if (schema != SCHEMA || lastObservedActualMax !in allowedLimits()) return false
        if (consecutivePromotionWindows !in 0 until policy.promotionWindowsRequired) return false
        if (consecutiveFailureWindows !in 0 until policy.failureWindowsRequired) return false
        if (cooldownUntilMs < 0L || lastAppliedAtMs < 0L) return false
        if (pendingTargetMax != null && !isAdjacent(lastObservedActualMax, pendingTargetMax)) return false
        if (rollbackTargetMax != null && rollbackTargetMax != adjacentLower(lastObservedActualMax)) return false
        return true
    }

    private fun allowedLimits(): Set<Int> {
        val limits = RuntimeProotCalibrationAlignment.productionProfileLimits()
        return setOf(limits.lowPower, limits.balanced, limits.highPerformance)
    }

    private fun adjacentHigher(actualMax: Int): Int {
        val limits = RuntimeProotCalibrationAlignment.productionProfileLimits()
        return when (actualMax) {
            limits.lowPower -> limits.balanced
            limits.balanced -> limits.highPerformance
            else -> actualMax
        }
    }

    private fun adjacentLower(actualMax: Int): Int {
        val limits = RuntimeProotCalibrationAlignment.productionProfileLimits()
        return when (actualMax) {
            limits.highPerformance -> limits.balanced
            limits.balanced -> limits.lowPower
            else -> actualMax
        }
    }

    private fun isAdjacent(first: Int, second: Int): Boolean =
        second != first && (adjacentHigher(first) == second || adjacentLower(first) == second)

    private fun result(
        recommendation: RuntimeProotAdaptiveRecommendation,
        reason: RuntimeProotAdaptiveTransitionReason,
        actualMax: Int,
        recommendedMax: Int,
        state: RuntimeProotAdaptiveHysteresisState,
    ) = RuntimeProotAdaptiveHysteresisResult(
        recommendation = recommendation,
        reason = reason,
        actualConfiguredGlobalMax = actualMax,
        recommendedConfiguredGlobalMax = recommendedMax,
        state = state,
    )
}
