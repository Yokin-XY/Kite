package com.kftest.app.foundation.runtime

enum class RuntimePressureStabilityState {
    NO_SOURCE,
    TELEMETRY_BLOCKED,
    STABLE_NOW,
    WATCHING,
    PRESSURE_HOLD
}

enum class RuntimePressureStabilityRecommendation {
    WAIT_FOR_TELEMETRY,
    REVIEW_TELEMETRY_HEALTH,
    OBSERVE_STABLE_WINDOW,
    READY_FOR_PRESSURE_CANARY,
    REVIEW_PRESSURE_STABILITY
}

enum class RuntimePressureCanaryArmingState {
    BLOCKED,
    WARMING,
    ARMED
}

data class RuntimePressureStabilityGateDryRunSnapshot(
    val mode: String = "runtime_pressure_stability_gate_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimePressureStabilityState = RuntimePressureStabilityState.NO_SOURCE,
    val recommendation: RuntimePressureStabilityRecommendation =
        RuntimePressureStabilityRecommendation.WAIT_FOR_TELEMETRY,
    val canaryStable: Boolean = false,
    val stableNow: Boolean = false,
    val canaryArmingState: RuntimePressureCanaryArmingState = RuntimePressureCanaryArmingState.BLOCKED,
    val canaryArmed: Boolean = false,
    val blocker: String = "waiting_for_telemetry",
    val pressureState: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val prootSignalLevel: ProotPressureSignalLevel = ProotPressureSignalLevel.QUIET,
    val prootPressureScore: Int = 0,
    val eventsInWindow: Int = 0,
    val forkExecEventsInWindow: Int = 0,
    val liveTraceeCount: Int = 0,
    val rssPressureLevel: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val budgetOverallState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val requiredStableRefreshes: Int = 3,
    val observedStableRefreshes: Int = 0,
    val consecutiveStableRefreshes: Int = 0,
    val lastArmingTransitionAtMs: Long = 0L,
    val reason: String = "waiting_for_telemetry"
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation stable=$canaryStable " +
            "pressure=$pressureState/$prootPressureScore signal=$prootSignalLevel " +
            "budget=$budgetOverallState rss=$rssPressureLevel arming=$canaryArmingState " +
            "stableRefreshes=$consecutiveStableRefreshes/$requiredStableRefreshes blocker=$blocker " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("pressure_stability_mode=${mode.toPressureStabilityEnvValue()}")
            appendLine("pressure_stability_enforcement_mode=${enforcementMode.toPressureStabilityEnvValue()}")
            appendLine("pressure_stability_enforcement_enabled=$enforcementEnabled")
            appendLine("pressure_stability_generated_at=$generatedAtMs")
            appendLine("pressure_stability_state=${state.name}")
            appendLine("pressure_stability_recommendation=${recommendation.name}")
            appendLine("pressure_stability_canary_stable=$canaryStable")
            appendLine("pressure_stability_stable_now=$stableNow")
            appendLine("pressure_stability_canary_arming_state=${canaryArmingState.name}")
            appendLine("pressure_stability_canary_armed=$canaryArmed")
            appendLine("pressure_stability_blocker=${blocker.toPressureStabilityEnvValue()}")
            appendLine("pressure_stability_pressure_state=${pressureState.name}")
            appendLine("pressure_stability_proot_signal=${prootSignalLevel.name}")
            appendLine("pressure_stability_proot_score=$prootPressureScore")
            appendLine("pressure_stability_events_window=$eventsInWindow")
            appendLine("pressure_stability_fork_exec_window=$forkExecEventsInWindow")
            appendLine("pressure_stability_live_tracees=$liveTraceeCount")
            appendLine("pressure_stability_rss_pressure=${rssPressureLevel.name}")
            appendLine("pressure_stability_budget_overall_state=${budgetOverallState.name}")
            appendLine("pressure_stability_required_stable_refreshes=$requiredStableRefreshes")
            appendLine("pressure_stability_observed_stable_refreshes=$observedStableRefreshes")
            appendLine("pressure_stability_consecutive_stable_refreshes=$consecutiveStableRefreshes")
            appendLine("pressure_stability_last_arming_transition_at=$lastArmingTransitionAtMs")
            appendLine("pressure_stability_reason=${reason.toPressureStabilityEnvValue()}")
            appendLine("pressure_stability_boundary=observe_only_no_wait_no_start_no_queue_no_defer_no_reject_no_lane_control")
        }
    }
}

object RuntimePressureStabilityGateDryRun {
    private const val REQUIRED_STABLE_REFRESHES = 3
    private const val STABLE_PROOT_SCORE_MAX = 34
    private const val STABLE_FORK_EXEC_WINDOW_MAX = 3
    private val lock = Any()
    private var lastInputKey: String = ""
    private var consecutiveStableRefreshes: Int = 0
    private var lastArmingState: RuntimePressureCanaryArmingState = RuntimePressureCanaryArmingState.BLOCKED
    private var lastArmingTransitionAtMs: Long = 0L

    fun evaluate(
        pressureConsumer: RuntimePressureConsumerSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimePressureStabilityGateDryRunSnapshot {
        val state = resolveState(pressureConsumer, budgetPressure)
        val stableNow = state == RuntimePressureStabilityState.STABLE_NOW
        val arming = updateArmingState(
            inputKey = buildInputKey(state, pressureConsumer, budgetPressure),
            stableNow = stableNow,
            now = now
        )
        val canaryStable = arming.state == RuntimePressureCanaryArmingState.ARMED
        return RuntimePressureStabilityGateDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state, arming.state),
            canaryStable = canaryStable,
            stableNow = stableNow,
            canaryArmingState = arming.state,
            canaryArmed = canaryStable,
            blocker = blockerFor(state, arming.state),
            pressureState = pressureConsumer.state,
            prootSignalLevel = pressureConsumer.prootSignalLevel,
            prootPressureScore = pressureConsumer.prootPressureScore,
            eventsInWindow = pressureConsumer.eventsInWindow,
            forkExecEventsInWindow = pressureConsumer.forkExecEventsInWindow,
            liveTraceeCount = pressureConsumer.liveTraceeCount,
            rssPressureLevel = pressureConsumer.rssPressureLevel,
            budgetOverallState = budgetPressure.overallState,
            requiredStableRefreshes = REQUIRED_STABLE_REFRESHES,
            observedStableRefreshes = arming.consecutiveStableRefreshes,
            consecutiveStableRefreshes = arming.consecutiveStableRefreshes,
            lastArmingTransitionAtMs = arming.lastTransitionAtMs,
            reason = buildReason(state, pressureConsumer, budgetPressure)
        )
    }

    private fun resolveState(
        pressureConsumer: RuntimePressureConsumerSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ): RuntimePressureStabilityState {
        if (
            pressureConsumer.state == RuntimePressureConsumerState.NO_SOURCE ||
            pressureConsumer.telemetryStatus == "not_started"
        ) {
            return RuntimePressureStabilityState.NO_SOURCE
        }
        if (!pressureConsumer.telemetryHealthy) {
            return RuntimePressureStabilityState.TELEMETRY_BLOCKED
        }
        val budgetStable = budgetPressure.overallState.severity() <=
            RuntimeBudgetState.NEAR_BUDGET.severity()
        val rssStable = pressureConsumer.rssPressureLevel.isStableForCanary()
        val prootStable = pressureConsumer.state == RuntimePressureConsumerState.QUIET ||
            pressureConsumer.state == RuntimePressureConsumerState.OBSERVING
        val windowStable = pressureConsumer.prootPressureScore <= STABLE_PROOT_SCORE_MAX &&
            pressureConsumer.forkExecEventsInWindow <= STABLE_FORK_EXEC_WINDOW_MAX
        if (prootStable && windowStable && rssStable && budgetStable) {
            return RuntimePressureStabilityState.STABLE_NOW
        }
        if (pressureConsumer.state == RuntimePressureConsumerState.BUSY) {
            return RuntimePressureStabilityState.WATCHING
        }
        return RuntimePressureStabilityState.PRESSURE_HOLD
    }

    private fun recommendationFor(
        state: RuntimePressureStabilityState,
        armingState: RuntimePressureCanaryArmingState
    ): RuntimePressureStabilityRecommendation {
        if (armingState == RuntimePressureCanaryArmingState.ARMED) {
            return RuntimePressureStabilityRecommendation.READY_FOR_PRESSURE_CANARY
        }
        return when (state) {
            RuntimePressureStabilityState.NO_SOURCE -> RuntimePressureStabilityRecommendation.WAIT_FOR_TELEMETRY
            RuntimePressureStabilityState.TELEMETRY_BLOCKED ->
                RuntimePressureStabilityRecommendation.REVIEW_TELEMETRY_HEALTH
            RuntimePressureStabilityState.STABLE_NOW ->
                RuntimePressureStabilityRecommendation.OBSERVE_STABLE_WINDOW
            RuntimePressureStabilityState.WATCHING,
            RuntimePressureStabilityState.PRESSURE_HOLD ->
                RuntimePressureStabilityRecommendation.REVIEW_PRESSURE_STABILITY
        }
    }

    private fun blockerFor(
        state: RuntimePressureStabilityState,
        armingState: RuntimePressureCanaryArmingState
    ): String {
        if (armingState == RuntimePressureCanaryArmingState.ARMED) {
            return "none"
        }
        if (state == RuntimePressureStabilityState.STABLE_NOW) {
            return "stability_window_warming"
        }
        return when (state) {
            RuntimePressureStabilityState.NO_SOURCE -> "waiting_for_telemetry"
            RuntimePressureStabilityState.TELEMETRY_BLOCKED -> "telemetry_unhealthy"
            RuntimePressureStabilityState.STABLE_NOW -> "stability_window_warming"
            RuntimePressureStabilityState.WATCHING,
            RuntimePressureStabilityState.PRESSURE_HOLD -> "pressure_not_stable"
        }
    }

    private fun buildReason(
        state: RuntimePressureStabilityState,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ): String {
        return "state=${state.name},pressure=${pressureConsumer.state.name}," +
            "signal=${pressureConsumer.prootSignalLevel.name},score=${pressureConsumer.prootPressureScore}," +
            "forkExec=${pressureConsumer.forkExecEventsInWindow},rss=${pressureConsumer.rssPressureLevel.name}," +
            "budget=${budgetPressure.overallState.name}"
    }

    private fun RuntimePressureLevel.isStableForCanary(): Boolean {
        return this == RuntimePressureLevel.UNKNOWN ||
            this == RuntimePressureLevel.NORMAL
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

    private fun buildInputKey(
        state: RuntimePressureStabilityState,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot
    ): String {
        return listOf(
            pressureConsumer.generatedAtMs,
            budgetPressure.generatedAtMs,
            state.name,
            pressureConsumer.prootPressureScore,
            pressureConsumer.forkExecEventsInWindow,
            pressureConsumer.rssPressureLevel.name,
            budgetPressure.overallState.name
        ).joinToString(":")
    }

    private fun updateArmingState(
        inputKey: String,
        stableNow: Boolean,
        now: Long
    ): ArmingUpdate {
        return synchronized(lock) {
            if (inputKey != lastInputKey) {
                lastInputKey = inputKey
                consecutiveStableRefreshes = if (stableNow) {
                    (consecutiveStableRefreshes + 1).coerceAtMost(REQUIRED_STABLE_REFRESHES)
                } else {
                    0
                }
            }
            val state = when {
                !stableNow -> RuntimePressureCanaryArmingState.BLOCKED
                consecutiveStableRefreshes >= REQUIRED_STABLE_REFRESHES -> RuntimePressureCanaryArmingState.ARMED
                else -> RuntimePressureCanaryArmingState.WARMING
            }
            if (state != lastArmingState) {
                lastArmingState = state
                lastArmingTransitionAtMs = now
            }
            ArmingUpdate(
                state = state,
                consecutiveStableRefreshes = consecutiveStableRefreshes,
                lastTransitionAtMs = lastArmingTransitionAtMs
            )
        }
    }

    private data class ArmingUpdate(
        val state: RuntimePressureCanaryArmingState,
        val consecutiveStableRefreshes: Int,
        val lastTransitionAtMs: Long
    )
}

private fun String?.toPressureStabilityEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(220)
}
