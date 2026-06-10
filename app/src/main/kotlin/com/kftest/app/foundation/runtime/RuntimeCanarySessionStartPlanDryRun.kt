package com.kftest.app.foundation.runtime

enum class RuntimeCanarySessionStartState {
    LOCKED,
    WAITING_FOR_WARMING,
    WAITING_FOR_GRANT,
    START_READY
}

enum class RuntimeCanarySessionStartRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_GRANT_PLAN,
    WAIT_FOR_PRESSURE_WARMING,
    WAIT_FOR_MANUAL_GRANT,
    REVIEW_SESSION_START
}

enum class RuntimeCanarySessionStartDisposition {
    WOULD_START_DRY_RUN_SESSION,
    WAIT_FOR_WARMING_HOLD,
    WAIT_FOR_MANUAL_GRANT,
    BLOCKED_BY_GRANT_PLAN,
    OUT_OF_SCOPE
}

data class RuntimeCanarySessionStartPlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val grantDisposition: RuntimeCanaryGrantDisposition,
    val startDisposition: RuntimeCanarySessionStartDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanarySessionStartPlanDryRunSnapshot(
    val mode: String = "runtime_canary_session_start_plan_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanarySessionStartState = RuntimeCanarySessionStartState.LOCKED,
    val recommendation: RuntimeCanarySessionStartRecommendation =
        RuntimeCanarySessionStartRecommendation.KEEP_SHADOW,
    val grantPlanState: RuntimeCanaryGrantPlanState = RuntimeCanaryGrantPlanState.LOCKED,
    val grantRequired: Boolean = false,
    val manualGrantObserved: Boolean = false,
    val grantIssued: Boolean = false,
    val grantValid: Boolean = false,
    val grantExpired: Boolean = false,
    val grantId: String = "none",
    val grantSource: String = "none",
    val startReady: Boolean = false,
    val manualSessionStartRequired: Boolean = false,
    val manualSessionStartObserved: Boolean = false,
    val sessionStartAllowed: Boolean = false,
    val autoSessionStartAllowed: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val proposedSessionTtlMs: Long = 0L,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val killSwitchRequired: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val activationWaitingHoldActive: Boolean = false,
    val sessionWaitingHoldActive: Boolean = false,
    val waitingForGrantCapabilityCount: Int = 0,
    val wouldStartCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val actualStartCount: Int = 0,
    val actualSessionCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val reason: String = "waiting_for_grant_plan",
    val items: List<RuntimeCanarySessionStartPlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation grant=$grantPlanState " +
            "required=$grantRequired observed=$manualGrantObserved issued=$grantIssued valid=$grantValid " +
            "expired=$grantExpired startReady=$startReady startAllowed=$sessionStartAllowed " +
            "manualStartObserved=$manualSessionStartObserved scope=$plannedScope kind=$proposedSessionKind " +
            "pressure=$pressureBlocker waitingGrant=$waitingForGrantCapabilityCount " +
            "wouldStart=$wouldStartCapabilityCount blocked=$blockedCapabilityCount actualStart=$actualStartCount " +
            "actualSessions=$actualSessionCount actualActivation=$actualActivationCount " +
            "actualEnforcement=$actualEnforcementCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_session_start_plan_mode=${mode.toCanarySessionStartEnvValue()}")
            appendLine("canary_session_start_plan_enforcement_mode=${enforcementMode.toCanarySessionStartEnvValue()}")
            appendLine("canary_session_start_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_session_start_plan_generated_at=$generatedAtMs")
            appendLine("canary_session_start_plan_state=${state.name}")
            appendLine("canary_session_start_plan_recommendation=${recommendation.name}")
            appendLine("canary_session_start_plan_grant_plan_state=${grantPlanState.name}")
            appendLine("canary_session_start_plan_grant_required=$grantRequired")
            appendLine("canary_session_start_plan_manual_grant_observed=$manualGrantObserved")
            appendLine("canary_session_start_plan_grant_issued=$grantIssued")
            appendLine("canary_session_start_plan_grant_valid=$grantValid")
            appendLine("canary_session_start_plan_grant_expired=$grantExpired")
            appendLine("canary_session_start_plan_grant_id=${grantId.toCanarySessionStartEnvValue()}")
            appendLine("canary_session_start_plan_grant_source=${grantSource.toCanarySessionStartEnvValue()}")
            appendLine("canary_session_start_plan_start_ready=$startReady")
            appendLine("canary_session_start_plan_manual_session_start_required=$manualSessionStartRequired")
            appendLine("canary_session_start_plan_manual_session_start_observed=$manualSessionStartObserved")
            appendLine("canary_session_start_plan_session_start_allowed=$sessionStartAllowed")
            appendLine("canary_session_start_plan_auto_session_start_allowed=$autoSessionStartAllowed")
            appendLine("canary_session_start_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_session_start_plan_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_session_start_plan_proposed_session_ttl_ms=$proposedSessionTtlMs")
            appendLine("canary_session_start_plan_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_session_start_plan_kill_switch_required=$killSwitchRequired")
            appendLine("canary_session_start_plan_pressure_blocker=${pressureBlocker.toCanarySessionStartEnvValue()}")
            appendLine("canary_session_start_plan_activation_waiting_hold_active=$activationWaitingHoldActive")
            appendLine("canary_session_start_plan_session_waiting_hold_active=$sessionWaitingHoldActive")
            appendLine("canary_session_start_plan_waiting_for_grant_capability_count=$waitingForGrantCapabilityCount")
            appendLine("canary_session_start_plan_would_start_capability_count=$wouldStartCapabilityCount")
            appendLine("canary_session_start_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_session_start_plan_actual_start_count=$actualStartCount")
            appendLine("canary_session_start_plan_actual_session_count=$actualSessionCount")
            appendLine("canary_session_start_plan_actual_activation_count=$actualActivationCount")
            appendLine("canary_session_start_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_session_start_plan_reason=${reason.toCanarySessionStartEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_session_start_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_grant_disposition=${item.grantDisposition.name}")
                appendLine("${prefix}_start_disposition=${item.startDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanarySessionStartEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanarySessionStartEnvValue()}")
            }
            appendLine("canary_session_start_plan_boundary=dry_run_manual_only_no_session_start_no_session_creation_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanarySessionStartPlanDryRun {
    fun evaluate(
        grantPlan: RuntimeCanaryGrantPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanarySessionStartPlanDryRunSnapshot {
        val state = when {
            grantPlan.state == RuntimeCanaryGrantPlanState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionStartState.WAITING_FOR_WARMING
            grantPlan.grantIssued && grantPlan.grantValid && !grantPlan.grantExpired ->
                RuntimeCanarySessionStartState.START_READY
            grantPlan.state == RuntimeCanaryGrantPlanState.GRANT_PENDING || grantPlan.grantRequired ->
                RuntimeCanarySessionStartState.WAITING_FOR_GRANT
            else -> RuntimeCanarySessionStartState.LOCKED
        }
        val items = grantPlan.items.map { grantItem ->
            val disposition = startDispositionFor(state, grantItem)
            RuntimeCanarySessionStartPlanItem(
                capability = grantItem.capability,
                inScope = grantItem.inScope,
                grantDisposition = grantItem.grantDisposition,
                startDisposition = disposition,
                blocker = startBlockerFor(disposition, grantItem, grantPlan),
                reason = buildItemReason(grantPlan, grantItem, disposition)
            )
        }

        return RuntimeCanarySessionStartPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            grantPlanState = grantPlan.state,
            grantRequired = grantPlan.grantRequired,
            manualGrantObserved = grantPlan.manualGrantObserved,
            grantIssued = grantPlan.grantIssued,
            grantValid = grantPlan.grantValid,
            grantExpired = grantPlan.grantExpired,
            grantId = grantPlan.grantId,
            grantSource = grantPlan.grantSource,
            startReady = state == RuntimeCanarySessionStartState.START_READY,
            manualSessionStartRequired = state == RuntimeCanarySessionStartState.START_READY,
            manualSessionStartObserved = false,
            sessionStartAllowed = false,
            autoSessionStartAllowed = false,
            plannedScope = grantPlan.plannedScope,
            proposedSessionKind = grantPlan.proposedSessionKind,
            proposedSessionTtlMs = grantPlan.proposedSessionTtlMs,
            rollbackPolicy = grantPlan.rollbackPolicy,
            killSwitchRequired = grantPlan.killSwitchRequired,
            pressureBlocker = grantPlan.pressureBlocker,
            activationWaitingHoldActive = grantPlan.activationWaitingHoldActive,
            sessionWaitingHoldActive = grantPlan.sessionWaitingHoldActive,
            waitingForGrantCapabilityCount = items.count {
                it.startDisposition == RuntimeCanarySessionStartDisposition.WAIT_FOR_MANUAL_GRANT
            },
            wouldStartCapabilityCount = items.count {
                it.startDisposition == RuntimeCanarySessionStartDisposition.WOULD_START_DRY_RUN_SESSION
            },
            blockedCapabilityCount = items.count {
                it.startDisposition != RuntimeCanarySessionStartDisposition.WOULD_START_DRY_RUN_SESSION
            },
            actualStartCount = 0,
            actualSessionCount = 0,
            actualActivationCount = 0,
            actualEnforcementCount = 0,
            reason = buildReason(grantPlan, state),
            items = items
        )
    }

    private fun startDispositionFor(
        state: RuntimeCanarySessionStartState,
        grantItem: RuntimeCanaryGrantPlanItem
    ): RuntimeCanarySessionStartDisposition {
        return when {
            state == RuntimeCanarySessionStartState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionStartDisposition.WAIT_FOR_WARMING_HOLD
            grantItem.grantDisposition == RuntimeCanaryGrantDisposition.OUT_OF_SCOPE ->
                RuntimeCanarySessionStartDisposition.OUT_OF_SCOPE
            state == RuntimeCanarySessionStartState.WAITING_FOR_GRANT &&
                grantItem.grantDisposition == RuntimeCanaryGrantDisposition.WAIT_FOR_MANUAL_GRANT ->
                RuntimeCanarySessionStartDisposition.WAIT_FOR_MANUAL_GRANT
            state == RuntimeCanarySessionStartState.START_READY &&
                grantItem.grantDisposition == RuntimeCanaryGrantDisposition.WAIT_FOR_MANUAL_GRANT ->
                RuntimeCanarySessionStartDisposition.WOULD_START_DRY_RUN_SESSION
            else -> RuntimeCanarySessionStartDisposition.BLOCKED_BY_GRANT_PLAN
        }
    }

    private fun startBlockerFor(
        disposition: RuntimeCanarySessionStartDisposition,
        grantItem: RuntimeCanaryGrantPlanItem,
        grantPlan: RuntimeCanaryGrantPlanDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanarySessionStartDisposition.WOULD_START_DRY_RUN_SESSION -> "dry_run_no_session_start"
            RuntimeCanarySessionStartDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanarySessionStartDisposition.WAIT_FOR_MANUAL_GRANT -> "manual_grant_pending"
            RuntimeCanarySessionStartDisposition.BLOCKED_BY_GRANT_PLAN ->
                grantItem.blocker.takeIf { it != "none" }
                    ?: grantPlan.pressureBlocker.takeIf { it != "none" }
                    ?: "grant_plan_not_ready"
            RuntimeCanarySessionStartDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanarySessionStartState
    ): RuntimeCanarySessionStartRecommendation {
        return when (state) {
            RuntimeCanarySessionStartState.START_READY ->
                RuntimeCanarySessionStartRecommendation.REVIEW_SESSION_START
            RuntimeCanarySessionStartState.WAITING_FOR_GRANT ->
                RuntimeCanarySessionStartRecommendation.WAIT_FOR_MANUAL_GRANT
            RuntimeCanarySessionStartState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionStartRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanarySessionStartState.LOCKED ->
                RuntimeCanarySessionStartRecommendation.WAIT_FOR_GRANT_PLAN
        }
    }

    private fun buildReason(
        grantPlan: RuntimeCanaryGrantPlanDryRunSnapshot,
        state: RuntimeCanarySessionStartState
    ): String {
        return "state=${state.name},grantState=${grantPlan.state.name}," +
            "grantRequired=${grantPlan.grantRequired},grantIssued=${grantPlan.grantIssued}," +
            "grantValid=${grantPlan.grantValid},grantExpired=${grantPlan.grantExpired}," +
            "manualGrantObserved=${grantPlan.manualGrantObserved},plannedScope=${grantPlan.plannedScope.name}," +
            "pressureBlocker=${grantPlan.pressureBlocker}," +
            "activationWait=${grantPlan.activationWaitingHoldActive}," +
            "sessionWait=${grantPlan.sessionWaitingHoldActive}"
    }

    private fun buildItemReason(
        grantPlan: RuntimeCanaryGrantPlanDryRunSnapshot,
        grantItem: RuntimeCanaryGrantPlanItem,
        disposition: RuntimeCanarySessionStartDisposition
    ): String {
        return "grantState=${grantPlan.state.name},plannedScope=${grantPlan.plannedScope.name}," +
            "grantDisposition=${grantItem.grantDisposition.name}," +
            "startDisposition=${disposition.name},grantReason=${grantItem.reason}"
    }
}

private fun String?.toCanarySessionStartEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
