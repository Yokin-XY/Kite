package com.kftest.app.foundation.runtime

enum class RuntimeCanaryApprovalRequestState {
    LOCKED,
    WAITING_FOR_WARMING,
    APPROVAL_READY
}

enum class RuntimeCanaryApprovalRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_SESSION_PLAN,
    WAIT_FOR_PRESSURE_WARMING,
    REVIEW_MANUAL_APPROVAL
}

enum class RuntimeCanaryApprovalAction {
    NONE,
    APPROVE_LIMITED_GOVERNANCE_SHADOW_CANARY
}

enum class RuntimeCanaryApprovalDisposition {
    WOULD_REQUEST_APPROVAL,
    WAIT_FOR_WARMING_HOLD,
    BLOCKED_BY_SESSION,
    OUT_OF_SCOPE
}

data class RuntimeCanaryApprovalRequestItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val sessionDisposition: RuntimeCanarySessionDisposition,
    val approvalDisposition: RuntimeCanaryApprovalDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryApprovalRequestDryRunSnapshot(
    val mode: String = "runtime_canary_approval_request_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryApprovalRequestState = RuntimeCanaryApprovalRequestState.LOCKED,
    val recommendation: RuntimeCanaryApprovalRecommendation =
        RuntimeCanaryApprovalRecommendation.KEEP_SHADOW,
    val approvalAction: RuntimeCanaryApprovalAction = RuntimeCanaryApprovalAction.NONE,
    val approvalReady: Boolean = false,
    val manualApprovalRequired: Boolean = false,
    val autoApprovalAllowed: Boolean = false,
    val sessionState: RuntimeCanarySessionState = RuntimeCanarySessionState.LOCKED,
    val sessionReady: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val proposedSessionTtlMs: Long = 0L,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val killSwitchRequired: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val activationWaitingHoldActive: Boolean = false,
    val activationWaitingHoldRemainingMs: Long = 0L,
    val sessionWaitingHoldActive: Boolean = false,
    val sessionWaitingHoldRemainingMs: Long = 0L,
    val eligibleCapabilityCount: Int = 0,
    val requestCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val actualApprovalCount: Int = 0,
    val actualSessionCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val reason: String = "waiting_for_session_plan",
    val items: List<RuntimeCanaryApprovalRequestItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation approvalReady=$approvalReady " +
            "action=$approvalAction manualRequired=$manualApprovalRequired autoApproval=$autoApprovalAllowed " +
            "session=$sessionState ready=$sessionReady scope=$plannedScope kind=$proposedSessionKind " +
            "pressure=$pressureBlocker activationWait=$activationWaitingHoldActive " +
            "sessionWait=$sessionWaitingHoldActive requestCaps=$requestCapabilityCount " +
            "blocked=$blockedCapabilityCount actualApproval=$actualApprovalCount " +
            "actualSessions=$actualSessionCount actualActivation=$actualActivationCount " +
            "actualEnforcement=$actualEnforcementCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_approval_request_mode=${mode.toCanaryApprovalEnvValue()}")
            appendLine("canary_approval_request_enforcement_mode=${enforcementMode.toCanaryApprovalEnvValue()}")
            appendLine("canary_approval_request_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_approval_request_generated_at=$generatedAtMs")
            appendLine("canary_approval_request_state=${state.name}")
            appendLine("canary_approval_request_recommendation=${recommendation.name}")
            appendLine("canary_approval_request_action=${approvalAction.name}")
            appendLine("canary_approval_request_ready=$approvalReady")
            appendLine("canary_approval_request_manual_approval_required=$manualApprovalRequired")
            appendLine("canary_approval_request_auto_approval_allowed=$autoApprovalAllowed")
            appendLine("canary_approval_request_session_state=${sessionState.name}")
            appendLine("canary_approval_request_session_ready=$sessionReady")
            appendLine("canary_approval_request_planned_scope=${plannedScope.name}")
            appendLine("canary_approval_request_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_approval_request_proposed_session_ttl_ms=$proposedSessionTtlMs")
            appendLine("canary_approval_request_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_approval_request_kill_switch_required=$killSwitchRequired")
            appendLine("canary_approval_request_pressure_blocker=${pressureBlocker.toCanaryApprovalEnvValue()}")
            appendLine("canary_approval_request_activation_waiting_hold_active=$activationWaitingHoldActive")
            appendLine("canary_approval_request_activation_waiting_hold_remaining_ms=$activationWaitingHoldRemainingMs")
            appendLine("canary_approval_request_session_waiting_hold_active=$sessionWaitingHoldActive")
            appendLine("canary_approval_request_session_waiting_hold_remaining_ms=$sessionWaitingHoldRemainingMs")
            appendLine("canary_approval_request_eligible_capability_count=$eligibleCapabilityCount")
            appendLine("canary_approval_request_capability_count=$requestCapabilityCount")
            appendLine("canary_approval_request_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_approval_request_actual_approval_count=$actualApprovalCount")
            appendLine("canary_approval_request_actual_session_count=$actualSessionCount")
            appendLine("canary_approval_request_actual_activation_count=$actualActivationCount")
            appendLine("canary_approval_request_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_approval_request_reason=${reason.toCanaryApprovalEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_approval_request_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_session_disposition=${item.sessionDisposition.name}")
                appendLine("${prefix}_approval_disposition=${item.approvalDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryApprovalEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryApprovalEnvValue()}")
            }
            appendLine("canary_approval_request_boundary=dry_run_manual_only_no_approval_no_session_creation_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanaryApprovalRequestDryRun {
    fun evaluate(
        sessionPlan: RuntimeCanarySessionPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryApprovalRequestDryRunSnapshot {
        val sessionReady = sessionPlan.state == RuntimeCanarySessionState.SESSION_READY &&
            sessionPlan.manualSessionStartAllowed
        val state = when {
            sessionReady -> RuntimeCanaryApprovalRequestState.APPROVAL_READY
            sessionPlan.state == RuntimeCanarySessionState.WAITING_FOR_WARMING ->
                RuntimeCanaryApprovalRequestState.WAITING_FOR_WARMING
            else -> RuntimeCanaryApprovalRequestState.LOCKED
        }
        val items = sessionPlan.items.map { sessionItem ->
            val disposition = approvalDispositionFor(state, sessionItem)
            RuntimeCanaryApprovalRequestItem(
                capability = sessionItem.capability,
                inScope = sessionItem.inScope,
                sessionDisposition = sessionItem.sessionDisposition,
                approvalDisposition = disposition,
                blocker = approvalBlockerFor(disposition, sessionItem, sessionPlan),
                reason = buildItemReason(sessionPlan, sessionItem, disposition)
            )
        }

        return RuntimeCanaryApprovalRequestDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            approvalAction = if (sessionReady) {
                RuntimeCanaryApprovalAction.APPROVE_LIMITED_GOVERNANCE_SHADOW_CANARY
            } else {
                RuntimeCanaryApprovalAction.NONE
            },
            approvalReady = sessionReady,
            manualApprovalRequired = sessionReady,
            autoApprovalAllowed = false,
            sessionState = sessionPlan.state,
            sessionReady = sessionReady,
            plannedScope = sessionPlan.plannedScope,
            proposedSessionKind = sessionPlan.proposedSessionKind,
            proposedSessionTtlMs = sessionPlan.proposedSessionTtlMs,
            rollbackPolicy = sessionPlan.rollbackPolicy,
            killSwitchRequired = sessionPlan.killSwitchRequired,
            pressureBlocker = sessionPlan.pressureBlocker,
            activationWaitingHoldActive = sessionPlan.activationWaitingHoldActive,
            activationWaitingHoldRemainingMs = sessionPlan.activationWaitingHoldRemainingMs,
            sessionWaitingHoldActive = sessionPlan.sessionWaitingHoldActive,
            sessionWaitingHoldRemainingMs = sessionPlan.sessionWaitingHoldRemainingMs,
            eligibleCapabilityCount = items.size,
            requestCapabilityCount = items.count {
                it.approvalDisposition == RuntimeCanaryApprovalDisposition.WOULD_REQUEST_APPROVAL
            },
            blockedCapabilityCount = items.count {
                it.approvalDisposition != RuntimeCanaryApprovalDisposition.WOULD_REQUEST_APPROVAL
            },
            actualApprovalCount = 0,
            actualSessionCount = 0,
            actualActivationCount = 0,
            actualEnforcementCount = 0,
            reason = buildReason(sessionPlan, state, sessionReady),
            items = items
        )
    }

    private fun approvalDispositionFor(
        state: RuntimeCanaryApprovalRequestState,
        sessionItem: RuntimeCanarySessionPlanItem
    ): RuntimeCanaryApprovalDisposition {
        return when {
            state == RuntimeCanaryApprovalRequestState.WAITING_FOR_WARMING ->
                RuntimeCanaryApprovalDisposition.WAIT_FOR_WARMING_HOLD
            sessionItem.sessionDisposition == RuntimeCanarySessionDisposition.OUT_OF_SCOPE ->
                RuntimeCanaryApprovalDisposition.OUT_OF_SCOPE
            state != RuntimeCanaryApprovalRequestState.APPROVAL_READY ->
                RuntimeCanaryApprovalDisposition.BLOCKED_BY_SESSION
            sessionItem.sessionDisposition == RuntimeCanarySessionDisposition.WOULD_INCLUDE_IN_DRY_RUN_SESSION ->
                RuntimeCanaryApprovalDisposition.WOULD_REQUEST_APPROVAL
            else -> RuntimeCanaryApprovalDisposition.BLOCKED_BY_SESSION
        }
    }

    private fun approvalBlockerFor(
        disposition: RuntimeCanaryApprovalDisposition,
        sessionItem: RuntimeCanarySessionPlanItem,
        sessionPlan: RuntimeCanarySessionPlanDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanaryApprovalDisposition.WOULD_REQUEST_APPROVAL -> "none"
            RuntimeCanaryApprovalDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanaryApprovalDisposition.BLOCKED_BY_SESSION ->
                sessionItem.blocker.takeIf { it != "none" }
                    ?: sessionPlan.pressureBlocker.takeIf { it != "none" }
                    ?: "session_not_ready"
            RuntimeCanaryApprovalDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryApprovalRequestState
    ): RuntimeCanaryApprovalRecommendation {
        return when (state) {
            RuntimeCanaryApprovalRequestState.APPROVAL_READY ->
                RuntimeCanaryApprovalRecommendation.REVIEW_MANUAL_APPROVAL
            RuntimeCanaryApprovalRequestState.WAITING_FOR_WARMING ->
                RuntimeCanaryApprovalRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanaryApprovalRequestState.LOCKED ->
                RuntimeCanaryApprovalRecommendation.WAIT_FOR_SESSION_PLAN
        }
    }

    private fun buildReason(
        sessionPlan: RuntimeCanarySessionPlanDryRunSnapshot,
        state: RuntimeCanaryApprovalRequestState,
        sessionReady: Boolean
    ): String {
        return "state=${state.name},sessionReady=$sessionReady,sessionState=${sessionPlan.state.name}," +
            "manualStart=${sessionPlan.manualSessionStartAllowed},plannedScope=${sessionPlan.plannedScope.name}," +
            "kind=${sessionPlan.proposedSessionKind.name},pressureBlocker=${sessionPlan.pressureBlocker}," +
            "activationWait=${sessionPlan.activationWaitingHoldActive}," +
            "sessionWait=${sessionPlan.sessionWaitingHoldActive}"
    }

    private fun buildItemReason(
        sessionPlan: RuntimeCanarySessionPlanDryRunSnapshot,
        sessionItem: RuntimeCanarySessionPlanItem,
        disposition: RuntimeCanaryApprovalDisposition
    ): String {
        return "sessionState=${sessionPlan.state.name},plannedScope=${sessionPlan.plannedScope.name}," +
            "sessionDisposition=${sessionItem.sessionDisposition.name}," +
            "approvalDisposition=${disposition.name},sessionReason=${sessionItem.reason}"
    }
}

private fun String?.toCanaryApprovalEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
