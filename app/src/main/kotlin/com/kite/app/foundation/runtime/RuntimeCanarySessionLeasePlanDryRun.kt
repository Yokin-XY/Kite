package com.kite.app.foundation.runtime

enum class RuntimeCanarySessionLeaseState {
    LOCKED,
    WAITING_FOR_WARMING,
    WAITING_FOR_GRANT,
    WAITING_FOR_SESSION_START
}

enum class RuntimeCanarySessionLeaseRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_SESSION_START_PLAN,
    WAIT_FOR_PRESSURE_WARMING,
    WAIT_FOR_MANUAL_GRANT,
    WAIT_FOR_MANUAL_SESSION_START
}

enum class RuntimeCanarySessionLeaseDisposition {
    WAIT_FOR_MANUAL_SESSION_START,
    WAIT_FOR_WARMING_HOLD,
    WAIT_FOR_MANUAL_GRANT,
    BLOCKED_BY_SESSION_START_PLAN,
    OUT_OF_SCOPE
}

data class RuntimeCanarySessionLeasePlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val startDisposition: RuntimeCanarySessionStartDisposition,
    val leaseDisposition: RuntimeCanarySessionLeaseDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanarySessionLeasePlanDryRunSnapshot(
    val mode: String = "runtime_canary_session_lease_plan_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanarySessionLeaseState = RuntimeCanarySessionLeaseState.LOCKED,
    val recommendation: RuntimeCanarySessionLeaseRecommendation =
        RuntimeCanarySessionLeaseRecommendation.KEEP_SHADOW,
    val sessionStartPlanState: RuntimeCanarySessionStartState = RuntimeCanarySessionStartState.LOCKED,
    val startReady: Boolean = false,
    val sessionStartAllowed: Boolean = false,
    val autoSessionStartAllowed: Boolean = false,
    val manualSessionStartRequired: Boolean = false,
    val manualSessionStartObserved: Boolean = false,
    val leaseRequired: Boolean = false,
    val leaseCreated: Boolean = false,
    val leaseValid: Boolean = false,
    val leaseExpired: Boolean = false,
    val leaseId: String = "none",
    val leaseSource: String = "none",
    val leaseTtlMs: Long = 0L,
    val leaseExpiresAtMs: Long = 0L,
    val rollbackArmed: Boolean = false,
    val killSwitchArmed: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val killSwitchRequired: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val activationWaitingHoldActive: Boolean = false,
    val sessionWaitingHoldActive: Boolean = false,
    val waitingStartCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val actualLeaseCount: Int = 0,
    val actualSessionCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val reason: String = "waiting_for_session_start_plan",
    val items: List<RuntimeCanarySessionLeasePlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation start=$sessionStartPlanState " +
            "startReady=$startReady startAllowed=$sessionStartAllowed manualStartObserved=$manualSessionStartObserved " +
            "leaseRequired=$leaseRequired leaseCreated=$leaseCreated leaseValid=$leaseValid " +
            "leaseExpired=$leaseExpired ttlMs=$leaseTtlMs scope=$plannedScope kind=$proposedSessionKind " +
            "rollback=$rollbackPolicy killSwitchRequired=$killSwitchRequired killSwitchArmed=$killSwitchArmed " +
            "pressure=$pressureBlocker waitingStart=$waitingStartCapabilityCount blocked=$blockedCapabilityCount " +
            "actualLease=$actualLeaseCount actualSessions=$actualSessionCount " +
            "actualActivation=$actualActivationCount actualEnforcement=$actualEnforcementCount " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_session_lease_plan_mode=${mode.toCanarySessionLeaseEnvValue()}")
            appendLine("canary_session_lease_plan_enforcement_mode=${enforcementMode.toCanarySessionLeaseEnvValue()}")
            appendLine("canary_session_lease_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_session_lease_plan_generated_at=$generatedAtMs")
            appendLine("canary_session_lease_plan_state=${state.name}")
            appendLine("canary_session_lease_plan_recommendation=${recommendation.name}")
            appendLine("canary_session_lease_plan_session_start_plan_state=${sessionStartPlanState.name}")
            appendLine("canary_session_lease_plan_start_ready=$startReady")
            appendLine("canary_session_lease_plan_session_start_allowed=$sessionStartAllowed")
            appendLine("canary_session_lease_plan_auto_session_start_allowed=$autoSessionStartAllowed")
            appendLine("canary_session_lease_plan_manual_session_start_required=$manualSessionStartRequired")
            appendLine("canary_session_lease_plan_manual_session_start_observed=$manualSessionStartObserved")
            appendLine("canary_session_lease_plan_lease_required=$leaseRequired")
            appendLine("canary_session_lease_plan_lease_created=$leaseCreated")
            appendLine("canary_session_lease_plan_lease_valid=$leaseValid")
            appendLine("canary_session_lease_plan_lease_expired=$leaseExpired")
            appendLine("canary_session_lease_plan_lease_id=${leaseId.toCanarySessionLeaseEnvValue()}")
            appendLine("canary_session_lease_plan_lease_source=${leaseSource.toCanarySessionLeaseEnvValue()}")
            appendLine("canary_session_lease_plan_lease_ttl_ms=$leaseTtlMs")
            appendLine("canary_session_lease_plan_lease_expires_at=$leaseExpiresAtMs")
            appendLine("canary_session_lease_plan_rollback_armed=$rollbackArmed")
            appendLine("canary_session_lease_plan_kill_switch_armed=$killSwitchArmed")
            appendLine("canary_session_lease_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_session_lease_plan_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_session_lease_plan_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_session_lease_plan_kill_switch_required=$killSwitchRequired")
            appendLine("canary_session_lease_plan_pressure_blocker=${pressureBlocker.toCanarySessionLeaseEnvValue()}")
            appendLine("canary_session_lease_plan_activation_waiting_hold_active=$activationWaitingHoldActive")
            appendLine("canary_session_lease_plan_session_waiting_hold_active=$sessionWaitingHoldActive")
            appendLine("canary_session_lease_plan_waiting_start_capability_count=$waitingStartCapabilityCount")
            appendLine("canary_session_lease_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_session_lease_plan_actual_lease_count=$actualLeaseCount")
            appendLine("canary_session_lease_plan_actual_session_count=$actualSessionCount")
            appendLine("canary_session_lease_plan_actual_activation_count=$actualActivationCount")
            appendLine("canary_session_lease_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_session_lease_plan_reason=${reason.toCanarySessionLeaseEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_session_lease_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_start_disposition=${item.startDisposition.name}")
                appendLine("${prefix}_lease_disposition=${item.leaseDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanarySessionLeaseEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanarySessionLeaseEnvValue()}")
            }
            appendLine("canary_session_lease_plan_boundary=dry_run_manual_only_no_session_lease_creation_no_session_start_no_session_creation_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanarySessionLeasePlanDryRun {
    fun evaluate(
        sessionStartPlan: RuntimeCanarySessionStartPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanarySessionLeasePlanDryRunSnapshot {
        val state = when (sessionStartPlan.state) {
            RuntimeCanarySessionStartState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionLeaseState.WAITING_FOR_WARMING
            RuntimeCanarySessionStartState.WAITING_FOR_GRANT ->
                RuntimeCanarySessionLeaseState.WAITING_FOR_GRANT
            RuntimeCanarySessionStartState.START_READY ->
                RuntimeCanarySessionLeaseState.WAITING_FOR_SESSION_START
            RuntimeCanarySessionStartState.LOCKED ->
                RuntimeCanarySessionLeaseState.LOCKED
        }
        val items = sessionStartPlan.items.map { startItem ->
            val disposition = leaseDispositionFor(state, startItem)
            RuntimeCanarySessionLeasePlanItem(
                capability = startItem.capability,
                inScope = startItem.inScope,
                startDisposition = startItem.startDisposition,
                leaseDisposition = disposition,
                blocker = leaseBlockerFor(disposition, startItem, sessionStartPlan),
                reason = buildItemReason(sessionStartPlan, startItem, disposition)
            )
        }

        return RuntimeCanarySessionLeasePlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            sessionStartPlanState = sessionStartPlan.state,
            startReady = sessionStartPlan.startReady,
            sessionStartAllowed = sessionStartPlan.sessionStartAllowed,
            autoSessionStartAllowed = sessionStartPlan.autoSessionStartAllowed,
            manualSessionStartRequired = sessionStartPlan.manualSessionStartRequired,
            manualSessionStartObserved = sessionStartPlan.manualSessionStartObserved,
            leaseRequired = state == RuntimeCanarySessionLeaseState.WAITING_FOR_SESSION_START,
            leaseCreated = false,
            leaseValid = false,
            leaseExpired = false,
            leaseId = "none",
            leaseSource = "none",
            leaseTtlMs = sessionStartPlan.proposedSessionTtlMs,
            leaseExpiresAtMs = 0L,
            rollbackArmed = false,
            killSwitchArmed = false,
            plannedScope = sessionStartPlan.plannedScope,
            proposedSessionKind = sessionStartPlan.proposedSessionKind,
            rollbackPolicy = sessionStartPlan.rollbackPolicy,
            killSwitchRequired = sessionStartPlan.killSwitchRequired,
            pressureBlocker = sessionStartPlan.pressureBlocker,
            activationWaitingHoldActive = sessionStartPlan.activationWaitingHoldActive,
            sessionWaitingHoldActive = sessionStartPlan.sessionWaitingHoldActive,
            waitingStartCapabilityCount = items.count {
                it.leaseDisposition == RuntimeCanarySessionLeaseDisposition.WAIT_FOR_MANUAL_SESSION_START
            },
            blockedCapabilityCount = items.count {
                it.leaseDisposition != RuntimeCanarySessionLeaseDisposition.WAIT_FOR_MANUAL_SESSION_START
            },
            actualLeaseCount = 0,
            actualSessionCount = 0,
            actualActivationCount = 0,
            actualEnforcementCount = 0,
            reason = buildReason(sessionStartPlan, state),
            items = items
        )
    }

    private fun leaseDispositionFor(
        state: RuntimeCanarySessionLeaseState,
        startItem: RuntimeCanarySessionStartPlanItem
    ): RuntimeCanarySessionLeaseDisposition {
        return when {
            state == RuntimeCanarySessionLeaseState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionLeaseDisposition.WAIT_FOR_WARMING_HOLD
            startItem.startDisposition == RuntimeCanarySessionStartDisposition.OUT_OF_SCOPE ->
                RuntimeCanarySessionLeaseDisposition.OUT_OF_SCOPE
            state == RuntimeCanarySessionLeaseState.WAITING_FOR_GRANT ->
                RuntimeCanarySessionLeaseDisposition.WAIT_FOR_MANUAL_GRANT
            state == RuntimeCanarySessionLeaseState.WAITING_FOR_SESSION_START &&
                startItem.startDisposition == RuntimeCanarySessionStartDisposition.WOULD_START_DRY_RUN_SESSION ->
                RuntimeCanarySessionLeaseDisposition.WAIT_FOR_MANUAL_SESSION_START
            else -> RuntimeCanarySessionLeaseDisposition.BLOCKED_BY_SESSION_START_PLAN
        }
    }

    private fun leaseBlockerFor(
        disposition: RuntimeCanarySessionLeaseDisposition,
        startItem: RuntimeCanarySessionStartPlanItem,
        sessionStartPlan: RuntimeCanarySessionStartPlanDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanarySessionLeaseDisposition.WAIT_FOR_MANUAL_SESSION_START -> "manual_session_start_pending"
            RuntimeCanarySessionLeaseDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanarySessionLeaseDisposition.WAIT_FOR_MANUAL_GRANT -> "manual_grant_pending"
            RuntimeCanarySessionLeaseDisposition.BLOCKED_BY_SESSION_START_PLAN ->
                startItem.blocker.takeIf { it != "none" }
                    ?: sessionStartPlan.pressureBlocker.takeIf { it != "none" }
                    ?: "session_start_plan_not_ready"
            RuntimeCanarySessionLeaseDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanarySessionLeaseState
    ): RuntimeCanarySessionLeaseRecommendation {
        return when (state) {
            RuntimeCanarySessionLeaseState.WAITING_FOR_SESSION_START ->
                RuntimeCanarySessionLeaseRecommendation.WAIT_FOR_MANUAL_SESSION_START
            RuntimeCanarySessionLeaseState.WAITING_FOR_GRANT ->
                RuntimeCanarySessionLeaseRecommendation.WAIT_FOR_MANUAL_GRANT
            RuntimeCanarySessionLeaseState.WAITING_FOR_WARMING ->
                RuntimeCanarySessionLeaseRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanarySessionLeaseState.LOCKED ->
                RuntimeCanarySessionLeaseRecommendation.WAIT_FOR_SESSION_START_PLAN
        }
    }

    private fun buildReason(
        sessionStartPlan: RuntimeCanarySessionStartPlanDryRunSnapshot,
        state: RuntimeCanarySessionLeaseState
    ): String {
        return "state=${state.name},sessionStartState=${sessionStartPlan.state.name}," +
            "startReady=${sessionStartPlan.startReady},sessionStartAllowed=${sessionStartPlan.sessionStartAllowed}," +
            "manualSessionStartObserved=${sessionStartPlan.manualSessionStartObserved}," +
            "grantIssued=${sessionStartPlan.grantIssued},grantValid=${sessionStartPlan.grantValid}," +
            "plannedScope=${sessionStartPlan.plannedScope.name}," +
            "pressureBlocker=${sessionStartPlan.pressureBlocker}," +
            "activationWait=${sessionStartPlan.activationWaitingHoldActive}," +
            "sessionWait=${sessionStartPlan.sessionWaitingHoldActive}"
    }

    private fun buildItemReason(
        sessionStartPlan: RuntimeCanarySessionStartPlanDryRunSnapshot,
        startItem: RuntimeCanarySessionStartPlanItem,
        disposition: RuntimeCanarySessionLeaseDisposition
    ): String {
        return "sessionStartState=${sessionStartPlan.state.name},plannedScope=${sessionStartPlan.plannedScope.name}," +
            "startDisposition=${startItem.startDisposition.name}," +
            "leaseDisposition=${disposition.name},startReason=${startItem.reason}"
    }
}

private fun String?.toCanarySessionLeaseEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
