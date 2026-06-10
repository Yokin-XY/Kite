package com.kftest.app.foundation.runtime

enum class RuntimeCanaryEnforcementPlanState {
    LOCKED,
    WAITING_FOR_WARMING,
    WAITING_FOR_GRANT,
    WAITING_FOR_SESSION_START,
    WAITING_FOR_LEASE,
    ENFORCEMENT_REVIEW
}

enum class RuntimeCanaryEnforcementRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_SESSION_LEASE,
    WAIT_FOR_PRESSURE_WARMING,
    WAIT_FOR_MANUAL_GRANT,
    WAIT_FOR_MANUAL_SESSION_START,
    REVIEW_SHADOW_CANARY_ENFORCEMENT
}

enum class RuntimeCanaryEnforcementDisposition {
    WOULD_ENFORCE_SHADOW_CANARY,
    WAIT_FOR_WARMING_HOLD,
    WAIT_FOR_MANUAL_GRANT,
    WAIT_FOR_MANUAL_SESSION_START,
    WAIT_FOR_SESSION_LEASE,
    BLOCKED_BY_SESSION_LEASE_PLAN,
    OUT_OF_SCOPE
}

data class RuntimeCanaryEnforcementPlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val leaseDisposition: RuntimeCanarySessionLeaseDisposition,
    val enforcementDisposition: RuntimeCanaryEnforcementDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryEnforcementPlanDryRunSnapshot(
    val mode: String = "runtime_canary_enforcement_plan_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryEnforcementPlanState = RuntimeCanaryEnforcementPlanState.LOCKED,
    val recommendation: RuntimeCanaryEnforcementRecommendation =
        RuntimeCanaryEnforcementRecommendation.KEEP_SHADOW,
    val sessionLeasePlanState: RuntimeCanarySessionLeaseState =
        RuntimeCanarySessionLeaseState.LOCKED,
    val leaseRequired: Boolean = false,
    val leaseCreated: Boolean = false,
    val leaseValid: Boolean = false,
    val leaseExpired: Boolean = false,
    val rollbackArmed: Boolean = false,
    val killSwitchArmed: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val leaseTtlMs: Long = 0L,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val killSwitchRequired: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val activationWaitingHoldActive: Boolean = false,
    val sessionWaitingHoldActive: Boolean = false,
    val reviewCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val simulatedEnforcementCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val actualLaneControlCount: Int = 0,
    val actualQueueCount: Int = 0,
    val actualStartCount: Int = 0,
    val actualReclaimCount: Int = 0,
    val actualRestartCount: Int = 0,
    val actualTerminateCount: Int = 0,
    val reason: String = "waiting_for_session_lease_plan",
    val items: List<RuntimeCanaryEnforcementPlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation lease=$sessionLeasePlanState " +
            "leaseCreated=$leaseCreated leaseValid=$leaseValid expired=$leaseExpired scope=$plannedScope " +
            "kind=$proposedSessionKind rollbackArmed=$rollbackArmed killSwitchArmed=$killSwitchArmed " +
            "pressure=$pressureBlocker review=$reviewCapabilityCount blocked=$blockedCapabilityCount " +
            "simulatedEnforcement=$simulatedEnforcementCount actualEnforcement=$actualEnforcementCount " +
            "laneControl=$actualLaneControlCount queue=$actualQueueCount start=$actualStartCount " +
            "reclaim=$actualReclaimCount restart=$actualRestartCount terminate=$actualTerminateCount " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_enforcement_plan_mode=${mode.toCanaryEnforcementEnvValue()}")
            appendLine("canary_enforcement_plan_enforcement_mode=${enforcementMode.toCanaryEnforcementEnvValue()}")
            appendLine("canary_enforcement_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_enforcement_plan_generated_at=$generatedAtMs")
            appendLine("canary_enforcement_plan_state=${state.name}")
            appendLine("canary_enforcement_plan_recommendation=${recommendation.name}")
            appendLine("canary_enforcement_plan_session_lease_plan_state=${sessionLeasePlanState.name}")
            appendLine("canary_enforcement_plan_lease_required=$leaseRequired")
            appendLine("canary_enforcement_plan_lease_created=$leaseCreated")
            appendLine("canary_enforcement_plan_lease_valid=$leaseValid")
            appendLine("canary_enforcement_plan_lease_expired=$leaseExpired")
            appendLine("canary_enforcement_plan_rollback_armed=$rollbackArmed")
            appendLine("canary_enforcement_plan_kill_switch_armed=$killSwitchArmed")
            appendLine("canary_enforcement_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_enforcement_plan_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_enforcement_plan_lease_ttl_ms=$leaseTtlMs")
            appendLine("canary_enforcement_plan_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_enforcement_plan_kill_switch_required=$killSwitchRequired")
            appendLine("canary_enforcement_plan_pressure_blocker=${pressureBlocker.toCanaryEnforcementEnvValue()}")
            appendLine("canary_enforcement_plan_activation_waiting_hold_active=$activationWaitingHoldActive")
            appendLine("canary_enforcement_plan_session_waiting_hold_active=$sessionWaitingHoldActive")
            appendLine("canary_enforcement_plan_review_capability_count=$reviewCapabilityCount")
            appendLine("canary_enforcement_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_enforcement_plan_simulated_enforcement_count=$simulatedEnforcementCount")
            appendLine("canary_enforcement_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_enforcement_plan_actual_lane_control_count=$actualLaneControlCount")
            appendLine("canary_enforcement_plan_actual_queue_count=$actualQueueCount")
            appendLine("canary_enforcement_plan_actual_start_count=$actualStartCount")
            appendLine("canary_enforcement_plan_actual_reclaim_count=$actualReclaimCount")
            appendLine("canary_enforcement_plan_actual_restart_count=$actualRestartCount")
            appendLine("canary_enforcement_plan_actual_terminate_count=$actualTerminateCount")
            appendLine("canary_enforcement_plan_reason=${reason.toCanaryEnforcementEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_enforcement_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_lease_disposition=${item.leaseDisposition.name}")
                appendLine("${prefix}_enforcement_disposition=${item.enforcementDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryEnforcementEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryEnforcementEnvValue()}")
            }
            appendLine("canary_enforcement_plan_boundary=dry_run_manual_only_no_enforcement_no_lane_control_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate")
        }
    }
}

object RuntimeCanaryEnforcementPlanDryRun {
    fun evaluate(
        sessionLeasePlan: RuntimeCanarySessionLeasePlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryEnforcementPlanDryRunSnapshot {
        val state = when {
            sessionLeasePlan.state == RuntimeCanarySessionLeaseState.WAITING_FOR_WARMING ->
                RuntimeCanaryEnforcementPlanState.WAITING_FOR_WARMING
            sessionLeasePlan.state == RuntimeCanarySessionLeaseState.WAITING_FOR_GRANT ->
                RuntimeCanaryEnforcementPlanState.WAITING_FOR_GRANT
            sessionLeasePlan.state == RuntimeCanarySessionLeaseState.WAITING_FOR_SESSION_START ->
                RuntimeCanaryEnforcementPlanState.WAITING_FOR_SESSION_START
            sessionLeasePlan.leaseCreated && sessionLeasePlan.leaseValid && !sessionLeasePlan.leaseExpired ->
                RuntimeCanaryEnforcementPlanState.ENFORCEMENT_REVIEW
            sessionLeasePlan.leaseRequired ->
                RuntimeCanaryEnforcementPlanState.WAITING_FOR_LEASE
            else -> RuntimeCanaryEnforcementPlanState.LOCKED
        }
        val items = sessionLeasePlan.items.map { leaseItem ->
            val disposition = enforcementDispositionFor(state, leaseItem)
            RuntimeCanaryEnforcementPlanItem(
                capability = leaseItem.capability,
                inScope = leaseItem.inScope,
                leaseDisposition = leaseItem.leaseDisposition,
                enforcementDisposition = disposition,
                blocker = enforcementBlockerFor(disposition, leaseItem, sessionLeasePlan),
                reason = buildItemReason(sessionLeasePlan, leaseItem, disposition)
            )
        }

        return RuntimeCanaryEnforcementPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            sessionLeasePlanState = sessionLeasePlan.state,
            leaseRequired = sessionLeasePlan.leaseRequired,
            leaseCreated = sessionLeasePlan.leaseCreated,
            leaseValid = sessionLeasePlan.leaseValid,
            leaseExpired = sessionLeasePlan.leaseExpired,
            rollbackArmed = sessionLeasePlan.rollbackArmed,
            killSwitchArmed = sessionLeasePlan.killSwitchArmed,
            plannedScope = sessionLeasePlan.plannedScope,
            proposedSessionKind = sessionLeasePlan.proposedSessionKind,
            leaseTtlMs = sessionLeasePlan.leaseTtlMs,
            rollbackPolicy = sessionLeasePlan.rollbackPolicy,
            killSwitchRequired = sessionLeasePlan.killSwitchRequired,
            pressureBlocker = sessionLeasePlan.pressureBlocker,
            activationWaitingHoldActive = sessionLeasePlan.activationWaitingHoldActive,
            sessionWaitingHoldActive = sessionLeasePlan.sessionWaitingHoldActive,
            reviewCapabilityCount = items.count {
                it.enforcementDisposition == RuntimeCanaryEnforcementDisposition.WOULD_ENFORCE_SHADOW_CANARY
            },
            blockedCapabilityCount = items.count {
                it.enforcementDisposition != RuntimeCanaryEnforcementDisposition.WOULD_ENFORCE_SHADOW_CANARY
            },
            simulatedEnforcementCount = items.count {
                it.enforcementDisposition == RuntimeCanaryEnforcementDisposition.WOULD_ENFORCE_SHADOW_CANARY
            },
            actualEnforcementCount = 0,
            actualLaneControlCount = 0,
            actualQueueCount = 0,
            actualStartCount = 0,
            actualReclaimCount = 0,
            actualRestartCount = 0,
            actualTerminateCount = 0,
            reason = buildReason(sessionLeasePlan, state),
            items = items
        )
    }

    private fun enforcementDispositionFor(
        state: RuntimeCanaryEnforcementPlanState,
        leaseItem: RuntimeCanarySessionLeasePlanItem
    ): RuntimeCanaryEnforcementDisposition {
        return when {
            state == RuntimeCanaryEnforcementPlanState.WAITING_FOR_WARMING ->
                RuntimeCanaryEnforcementDisposition.WAIT_FOR_WARMING_HOLD
            leaseItem.leaseDisposition == RuntimeCanarySessionLeaseDisposition.OUT_OF_SCOPE ->
                RuntimeCanaryEnforcementDisposition.OUT_OF_SCOPE
            state == RuntimeCanaryEnforcementPlanState.WAITING_FOR_GRANT ->
                RuntimeCanaryEnforcementDisposition.WAIT_FOR_MANUAL_GRANT
            state == RuntimeCanaryEnforcementPlanState.WAITING_FOR_SESSION_START ->
                RuntimeCanaryEnforcementDisposition.WAIT_FOR_MANUAL_SESSION_START
            state == RuntimeCanaryEnforcementPlanState.WAITING_FOR_LEASE ->
                RuntimeCanaryEnforcementDisposition.WAIT_FOR_SESSION_LEASE
            state == RuntimeCanaryEnforcementPlanState.ENFORCEMENT_REVIEW &&
                leaseItem.leaseDisposition == RuntimeCanarySessionLeaseDisposition.WAIT_FOR_MANUAL_SESSION_START ->
                RuntimeCanaryEnforcementDisposition.WOULD_ENFORCE_SHADOW_CANARY
            else -> RuntimeCanaryEnforcementDisposition.BLOCKED_BY_SESSION_LEASE_PLAN
        }
    }

    private fun enforcementBlockerFor(
        disposition: RuntimeCanaryEnforcementDisposition,
        leaseItem: RuntimeCanarySessionLeasePlanItem,
        sessionLeasePlan: RuntimeCanarySessionLeasePlanDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanaryEnforcementDisposition.WOULD_ENFORCE_SHADOW_CANARY -> "dry_run_no_enforcement"
            RuntimeCanaryEnforcementDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanaryEnforcementDisposition.WAIT_FOR_MANUAL_GRANT -> "manual_grant_pending"
            RuntimeCanaryEnforcementDisposition.WAIT_FOR_MANUAL_SESSION_START -> "manual_session_start_pending"
            RuntimeCanaryEnforcementDisposition.WAIT_FOR_SESSION_LEASE -> "session_lease_not_created"
            RuntimeCanaryEnforcementDisposition.BLOCKED_BY_SESSION_LEASE_PLAN ->
                leaseItem.blocker.takeIf { it != "none" }
                    ?: sessionLeasePlan.pressureBlocker.takeIf { it != "none" }
                    ?: "session_lease_plan_not_ready"
            RuntimeCanaryEnforcementDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryEnforcementPlanState
    ): RuntimeCanaryEnforcementRecommendation {
        return when (state) {
            RuntimeCanaryEnforcementPlanState.ENFORCEMENT_REVIEW ->
                RuntimeCanaryEnforcementRecommendation.REVIEW_SHADOW_CANARY_ENFORCEMENT
            RuntimeCanaryEnforcementPlanState.WAITING_FOR_LEASE ->
                RuntimeCanaryEnforcementRecommendation.WAIT_FOR_SESSION_LEASE
            RuntimeCanaryEnforcementPlanState.WAITING_FOR_SESSION_START ->
                RuntimeCanaryEnforcementRecommendation.WAIT_FOR_MANUAL_SESSION_START
            RuntimeCanaryEnforcementPlanState.WAITING_FOR_GRANT ->
                RuntimeCanaryEnforcementRecommendation.WAIT_FOR_MANUAL_GRANT
            RuntimeCanaryEnforcementPlanState.WAITING_FOR_WARMING ->
                RuntimeCanaryEnforcementRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanaryEnforcementPlanState.LOCKED ->
                RuntimeCanaryEnforcementRecommendation.WAIT_FOR_SESSION_LEASE
        }
    }

    private fun buildReason(
        sessionLeasePlan: RuntimeCanarySessionLeasePlanDryRunSnapshot,
        state: RuntimeCanaryEnforcementPlanState
    ): String {
        return "state=${state.name},leaseState=${sessionLeasePlan.state.name}," +
            "leaseCreated=${sessionLeasePlan.leaseCreated},leaseValid=${sessionLeasePlan.leaseValid}," +
            "leaseExpired=${sessionLeasePlan.leaseExpired},rollbackArmed=${sessionLeasePlan.rollbackArmed}," +
            "killSwitchArmed=${sessionLeasePlan.killSwitchArmed},plannedScope=${sessionLeasePlan.plannedScope.name}," +
            "pressureBlocker=${sessionLeasePlan.pressureBlocker}," +
            "activationWait=${sessionLeasePlan.activationWaitingHoldActive}," +
            "sessionWait=${sessionLeasePlan.sessionWaitingHoldActive}"
    }

    private fun buildItemReason(
        sessionLeasePlan: RuntimeCanarySessionLeasePlanDryRunSnapshot,
        leaseItem: RuntimeCanarySessionLeasePlanItem,
        disposition: RuntimeCanaryEnforcementDisposition
    ): String {
        return "leaseState=${sessionLeasePlan.state.name},plannedScope=${sessionLeasePlan.plannedScope.name}," +
            "leaseDisposition=${leaseItem.leaseDisposition.name}," +
            "enforcementDisposition=${disposition.name},leaseReason=${leaseItem.reason}"
    }
}

private fun String?.toCanaryEnforcementEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
