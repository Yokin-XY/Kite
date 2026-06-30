package com.kite.app.foundation.runtime

enum class RuntimeCanarySessionState {
    LOCKED,
    WAITING_FOR_WARMING,
    SESSION_READY
}

enum class RuntimeCanarySessionRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_ACTIVATION_PLAN,
    WAIT_FOR_PRESSURE_WARMING,
    REVIEW_MANUAL_CANARY_SESSION
}

enum class RuntimeCanarySessionKind {
    NONE,
    LIMITED_GOVERNANCE_SHADOW_CANARY
}

enum class RuntimeCanarySessionRollbackPolicy {
    NONE,
    PRESSURE_REGRESSION_OR_TELEMETRY_UNHEALTHY
}

enum class RuntimeCanarySessionDisposition {
    WOULD_INCLUDE_IN_DRY_RUN_SESSION,
    WAIT_FOR_WARMING_HOLD,
    BLOCKED_BY_ACTIVATION,
    OUT_OF_SCOPE
}

data class RuntimeCanarySessionPlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val activationDisposition: RuntimeCanaryActivationDisposition,
    val sessionDisposition: RuntimeCanarySessionDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanarySessionPlanDryRunSnapshot(
    val mode: String = "runtime_canary_session_plan_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanarySessionState = RuntimeCanarySessionState.LOCKED,
    val recommendation: RuntimeCanarySessionRecommendation =
        RuntimeCanarySessionRecommendation.KEEP_SHADOW,
    val activationState: RuntimeCanaryActivationState = RuntimeCanaryActivationState.LOCKED,
    val activationManualReady: Boolean = false,
    val activationManualActionRequired: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val proposedSessionTtlMs: Long = 0L,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val killSwitchRequired: Boolean = false,
    val autoSessionStartAllowed: Boolean = false,
    val manualSessionStartAllowed: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val warmingHoldActive: Boolean = false,
    val waitingHoldActive: Boolean = false,
    val activationMinWaitingVisibleMs: Long = 0L,
    val activationWaitingHoldActive: Boolean = false,
    val activationWaitingVisibleUntilMs: Long = 0L,
    val activationWaitingHoldRemainingMs: Long = 0L,
    val minWaitingVisibleMs: Long = 0L,
    val sessionWaitingHoldActive: Boolean = false,
    val sessionWaitingVisibleUntilMs: Long = 0L,
    val sessionWaitingHoldRemainingMs: Long = 0L,
    val eligibleCapabilityCount: Int = 0,
    val wouldIncludeCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val actualSessionCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val reason: String = "waiting_for_activation_plan",
    val items: List<RuntimeCanarySessionPlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation kind=$proposedSessionKind " +
            "scope=$plannedScope manualStart=$manualSessionStartAllowed autoStart=$autoSessionStartAllowed " +
            "activation=$activationState ready=$activationManualReady pressure=$pressureBlocker " +
            "warmingHold=$warmingHoldActive waitingHold=$waitingHoldActive " +
            "activationWaitingHold=$activationWaitingHoldActive activationRemaining=${activationWaitingHoldRemainingMs}ms " +
            "sessionWaitingHold=$sessionWaitingHoldActive remaining=${sessionWaitingHoldRemainingMs}ms " +
            "wouldInclude=$wouldIncludeCapabilityCount blocked=$blockedCapabilityCount " +
            "actualSessions=$actualSessionCount actualActivation=$actualActivationCount " +
            "actualEnforcement=$actualEnforcementCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_session_plan_mode=${mode.toCanarySessionEnvValue()}")
            appendLine("canary_session_plan_enforcement_mode=${enforcementMode.toCanarySessionEnvValue()}")
            appendLine("canary_session_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_session_plan_generated_at=$generatedAtMs")
            appendLine("canary_session_plan_state=${state.name}")
            appendLine("canary_session_plan_recommendation=${recommendation.name}")
            appendLine("canary_session_plan_activation_state=${activationState.name}")
            appendLine("canary_session_plan_activation_manual_ready=$activationManualReady")
            appendLine("canary_session_plan_activation_manual_action_required=$activationManualActionRequired")
            appendLine("canary_session_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_session_plan_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_session_plan_proposed_session_ttl_ms=$proposedSessionTtlMs")
            appendLine("canary_session_plan_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_session_plan_kill_switch_required=$killSwitchRequired")
            appendLine("canary_session_plan_auto_session_start_allowed=$autoSessionStartAllowed")
            appendLine("canary_session_plan_manual_session_start_allowed=$manualSessionStartAllowed")
            appendLine("canary_session_plan_pressure_blocker=${pressureBlocker.toCanarySessionEnvValue()}")
            appendLine("canary_session_plan_warming_hold_active=$warmingHoldActive")
            appendLine("canary_session_plan_waiting_hold_active=$waitingHoldActive")
            appendLine("canary_session_plan_activation_min_waiting_visible_ms=$activationMinWaitingVisibleMs")
            appendLine("canary_session_plan_activation_waiting_hold_active=$activationWaitingHoldActive")
            appendLine("canary_session_plan_activation_waiting_visible_until=$activationWaitingVisibleUntilMs")
            appendLine("canary_session_plan_activation_waiting_hold_remaining_ms=$activationWaitingHoldRemainingMs")
            appendLine("canary_session_plan_min_waiting_visible_ms=$minWaitingVisibleMs")
            appendLine("canary_session_plan_session_waiting_hold_active=$sessionWaitingHoldActive")
            appendLine("canary_session_plan_session_waiting_visible_until=$sessionWaitingVisibleUntilMs")
            appendLine("canary_session_plan_session_waiting_hold_remaining_ms=$sessionWaitingHoldRemainingMs")
            appendLine("canary_session_plan_eligible_capability_count=$eligibleCapabilityCount")
            appendLine("canary_session_plan_would_include_capability_count=$wouldIncludeCapabilityCount")
            appendLine("canary_session_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_session_plan_actual_session_count=$actualSessionCount")
            appendLine("canary_session_plan_actual_activation_count=$actualActivationCount")
            appendLine("canary_session_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_session_plan_reason=${reason.toCanarySessionEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_session_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_activation_disposition=${item.activationDisposition.name}")
                appendLine("${prefix}_session_disposition=${item.sessionDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanarySessionEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanarySessionEnvValue()}")
            }
            appendLine("canary_session_plan_boundary=dry_run_manual_only_no_session_creation_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanarySessionPlanDryRun {
    private const val DEFAULT_SESSION_TTL_MS = 5 * 60 * 1000L
    private const val MIN_WAITING_VISIBLE_MS = 5_000L

    private val lock = Any()
    private var waitingVisibleUntilMs: Long = 0L
    private var lastActivationState: RuntimeCanaryActivationState = RuntimeCanaryActivationState.LOCKED
    private var lastActivationWaiting: Boolean = false

    fun evaluate(
        activationPlan: RuntimeCanaryActivationPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanarySessionPlanDryRunSnapshot {
        val waitingHold = updateWaitingHold(activationPlan, now)
        val state = when {
            waitingHold.active -> RuntimeCanarySessionState.WAITING_FOR_WARMING
            activationPlan.manualReady -> RuntimeCanarySessionState.SESSION_READY
            activationPlan.state == RuntimeCanaryActivationState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionState.WAITING_FOR_WARMING
            else -> RuntimeCanarySessionState.LOCKED
        }
        val manualSessionStartAllowed = state == RuntimeCanarySessionState.SESSION_READY
        val items = activationPlan.items.map { activationItem ->
            val disposition = sessionDispositionFor(state, activationItem)
            RuntimeCanarySessionPlanItem(
                capability = activationItem.capability,
                inScope = activationItem.inScope,
                activationDisposition = activationItem.activationDisposition,
                sessionDisposition = disposition,
                blocker = sessionBlockerFor(disposition, activationItem, activationPlan),
                reason = buildItemReason(activationPlan, activationItem, disposition)
            )
        }

        return RuntimeCanarySessionPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            activationState = activationPlan.state,
            activationManualReady = activationPlan.manualReady,
            activationManualActionRequired = activationPlan.manualActionRequired,
            plannedScope = activationPlan.plannedScope,
            proposedSessionKind = if (manualSessionStartAllowed) {
                RuntimeCanarySessionKind.LIMITED_GOVERNANCE_SHADOW_CANARY
            } else {
                RuntimeCanarySessionKind.NONE
            },
            proposedSessionTtlMs = if (manualSessionStartAllowed) DEFAULT_SESSION_TTL_MS else 0L,
            rollbackPolicy = if (manualSessionStartAllowed) {
                RuntimeCanarySessionRollbackPolicy.PRESSURE_REGRESSION_OR_TELEMETRY_UNHEALTHY
            } else {
                RuntimeCanarySessionRollbackPolicy.NONE
            },
            killSwitchRequired = manualSessionStartAllowed,
            autoSessionStartAllowed = false,
            manualSessionStartAllowed = manualSessionStartAllowed,
            pressureBlocker = activationPlan.pressureBlocker,
            warmingHoldActive = activationPlan.warmingHoldActive,
            waitingHoldActive = activationPlan.waitingHoldActive,
            activationMinWaitingVisibleMs = activationPlan.minWaitingVisibleMs,
            activationWaitingHoldActive = activationPlan.waitingHoldActive,
            activationWaitingVisibleUntilMs = activationPlan.waitingVisibleUntilMs,
            activationWaitingHoldRemainingMs = activationPlan.waitingHoldRemainingMs,
            minWaitingVisibleMs = MIN_WAITING_VISIBLE_MS,
            sessionWaitingHoldActive = waitingHold.active,
            sessionWaitingVisibleUntilMs = waitingHold.visibleUntilMs,
            sessionWaitingHoldRemainingMs = waitingHold.remainingMs,
            eligibleCapabilityCount = items.size,
            wouldIncludeCapabilityCount = items.count {
                it.sessionDisposition == RuntimeCanarySessionDisposition.WOULD_INCLUDE_IN_DRY_RUN_SESSION
            },
            blockedCapabilityCount = items.count {
                it.sessionDisposition != RuntimeCanarySessionDisposition.WOULD_INCLUDE_IN_DRY_RUN_SESSION
            },
            actualSessionCount = 0,
            actualActivationCount = 0,
            actualEnforcementCount = 0,
            reason = buildReason(activationPlan, state, manualSessionStartAllowed, waitingHold),
            items = items
        )
    }

    private fun updateWaitingHold(
        activationPlan: RuntimeCanaryActivationPlanDryRunSnapshot,
        now: Long
    ): WaitingHold {
        return synchronized(lock) {
            val activationWaiting = activationPlan.state == RuntimeCanaryActivationState.WAITING_FOR_WARMING ||
                activationPlan.waitingHoldActive
            val activationReady = activationPlan.state == RuntimeCanaryActivationState.MANUAL_READY ||
                activationPlan.manualReady

            if (activationPlan.state == RuntimeCanaryActivationState.LOCKED && !activationWaiting) {
                waitingVisibleUntilMs = 0L
            } else {
                val enteredWaiting = activationWaiting && !lastActivationWaiting
                val recentlyReady = activationReady && lastActivationWaiting
                if (enteredWaiting || recentlyReady) {
                    waitingVisibleUntilMs = maxOf(waitingVisibleUntilMs, now + MIN_WAITING_VISIBLE_MS)
                }
            }

            val active = activationWaiting ||
                (activationReady && waitingVisibleUntilMs > now)
            val remaining = if (active) {
                (waitingVisibleUntilMs - now).coerceAtLeast(0L)
            } else {
                0L
            }
            lastActivationState = activationPlan.state
            lastActivationWaiting = activationWaiting
            WaitingHold(
                active = active,
                visibleUntilMs = waitingVisibleUntilMs,
                remainingMs = remaining
            )
        }
    }

    private fun sessionDispositionFor(
        state: RuntimeCanarySessionState,
        activationItem: RuntimeCanaryActivationPlanItem
    ): RuntimeCanarySessionDisposition {
        return when {
            state == RuntimeCanarySessionState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionDisposition.WAIT_FOR_WARMING_HOLD
            activationItem.activationDisposition == RuntimeCanaryActivationDisposition.OUT_OF_SCOPE ->
                RuntimeCanarySessionDisposition.OUT_OF_SCOPE
            state != RuntimeCanarySessionState.SESSION_READY ->
                RuntimeCanarySessionDisposition.BLOCKED_BY_ACTIVATION
            activationItem.activationDisposition == RuntimeCanaryActivationDisposition.WOULD_ARM_DRY_RUN_CANARY ->
                RuntimeCanarySessionDisposition.WOULD_INCLUDE_IN_DRY_RUN_SESSION
            else -> RuntimeCanarySessionDisposition.BLOCKED_BY_ACTIVATION
        }
    }

    private fun sessionBlockerFor(
        disposition: RuntimeCanarySessionDisposition,
        activationItem: RuntimeCanaryActivationPlanItem,
        activationPlan: RuntimeCanaryActivationPlanDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanarySessionDisposition.WOULD_INCLUDE_IN_DRY_RUN_SESSION -> "none"
            RuntimeCanarySessionDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanarySessionDisposition.BLOCKED_BY_ACTIVATION ->
                activationItem.blocker.takeIf { it != "none" }
                    ?: activationPlan.pressureBlocker.takeIf { it != "none" }
                    ?: "activation_not_ready"
            RuntimeCanarySessionDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanarySessionState
    ): RuntimeCanarySessionRecommendation {
        return when (state) {
            RuntimeCanarySessionState.SESSION_READY ->
                RuntimeCanarySessionRecommendation.REVIEW_MANUAL_CANARY_SESSION
            RuntimeCanarySessionState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanarySessionState.LOCKED ->
                RuntimeCanarySessionRecommendation.WAIT_FOR_ACTIVATION_PLAN
        }
    }

    private fun buildReason(
        activationPlan: RuntimeCanaryActivationPlanDryRunSnapshot,
        state: RuntimeCanarySessionState,
        manualSessionStartAllowed: Boolean,
        waitingHold: WaitingHold
    ): String {
        return "state=${state.name},manualSessionStartAllowed=$manualSessionStartAllowed," +
            "activationState=${activationPlan.state.name},manualReady=${activationPlan.manualReady}," +
            "plannedScope=${activationPlan.plannedScope.name},pressureBlocker=${activationPlan.pressureBlocker}," +
            "warmingHold=${activationPlan.warmingHoldActive},waitingHold=${activationPlan.waitingHoldActive}," +
            "sessionWaitingHold=${waitingHold.active},sessionWaitingRemainingMs=${waitingHold.remainingMs}"
    }

    private fun buildItemReason(
        activationPlan: RuntimeCanaryActivationPlanDryRunSnapshot,
        activationItem: RuntimeCanaryActivationPlanItem,
        disposition: RuntimeCanarySessionDisposition
    ): String {
        return "activationState=${activationPlan.state.name},plannedScope=${activationPlan.plannedScope.name}," +
            "activationDisposition=${activationItem.activationDisposition.name}," +
            "sessionDisposition=${disposition.name},activationReason=${activationItem.reason}"
    }

    private data class WaitingHold(
        val active: Boolean,
        val visibleUntilMs: Long,
        val remainingMs: Long
    )
}

private fun String?.toCanarySessionEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
