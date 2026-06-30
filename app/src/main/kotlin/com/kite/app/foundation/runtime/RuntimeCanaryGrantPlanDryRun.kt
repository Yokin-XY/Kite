package com.kite.app.foundation.runtime

enum class RuntimeCanaryGrantPlanState {
    LOCKED,
    WAITING_FOR_WARMING,
    GRANT_PENDING
}

enum class RuntimeCanaryGrantPlanRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_APPROVAL_GATE,
    WAIT_FOR_PRESSURE_WARMING,
    WAIT_FOR_MANUAL_GRANT
}

enum class RuntimeCanaryGrantDisposition {
    WAIT_FOR_MANUAL_GRANT,
    WAIT_FOR_WARMING_HOLD,
    BLOCKED_BY_APPROVAL_GATE,
    OUT_OF_SCOPE
}

data class RuntimeCanaryGrantPlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val gateDisposition: RuntimeCanaryApprovalGateDisposition,
    val grantDisposition: RuntimeCanaryGrantDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryGrantPlanDryRunSnapshot(
    val mode: String = "runtime_canary_grant_plan_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryGrantPlanState = RuntimeCanaryGrantPlanState.LOCKED,
    val recommendation: RuntimeCanaryGrantPlanRecommendation =
        RuntimeCanaryGrantPlanRecommendation.KEEP_SHADOW,
    val approvalGateState: RuntimeCanaryApprovalGateState = RuntimeCanaryApprovalGateState.LOCKED,
    val approvalGateRecommendation: RuntimeCanaryApprovalGateRecommendation =
        RuntimeCanaryApprovalGateRecommendation.KEEP_SHADOW,
    val approvalGateGranted: Boolean = false,
    val manualApprovalRequired: Boolean = false,
    val manualApprovalObserved: Boolean = false,
    val requiredApprovalAction: RuntimeCanaryApprovalAction = RuntimeCanaryApprovalAction.NONE,
    val grantRequired: Boolean = false,
    val manualGrantObserved: Boolean = false,
    val grantIssued: Boolean = false,
    val grantValid: Boolean = false,
    val grantExpired: Boolean = false,
    val grantId: String = "none",
    val grantSource: String = "none",
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val proposedSessionTtlMs: Long = 0L,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val killSwitchRequired: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val activationWaitingHoldActive: Boolean = false,
    val sessionWaitingHoldActive: Boolean = false,
    val pendingGrantCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val actualGrantCount: Int = 0,
    val actualApprovalCount: Int = 0,
    val actualSessionCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val reason: String = "waiting_for_approval_gate",
    val items: List<RuntimeCanaryGrantPlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation gate=$approvalGateState " +
            "required=$grantRequired approvalObserved=$manualApprovalObserved " +
            "grantObserved=$manualGrantObserved issued=$grantIssued valid=$grantValid " +
            "expired=$grantExpired scope=$plannedScope kind=$proposedSessionKind " +
            "pressure=$pressureBlocker pending=$pendingGrantCapabilityCount blocked=$blockedCapabilityCount " +
            "actualGrant=$actualGrantCount actualApproval=$actualApprovalCount " +
            "actualSessions=$actualSessionCount actualActivation=$actualActivationCount " +
            "actualEnforcement=$actualEnforcementCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_grant_plan_mode=${mode.toCanaryGrantEnvValue()}")
            appendLine("canary_grant_plan_enforcement_mode=${enforcementMode.toCanaryGrantEnvValue()}")
            appendLine("canary_grant_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_grant_plan_generated_at=$generatedAtMs")
            appendLine("canary_grant_plan_state=${state.name}")
            appendLine("canary_grant_plan_recommendation=${recommendation.name}")
            appendLine("canary_grant_plan_approval_gate_state=${approvalGateState.name}")
            appendLine("canary_grant_plan_approval_gate_recommendation=${approvalGateRecommendation.name}")
            appendLine("canary_grant_plan_approval_gate_granted=$approvalGateGranted")
            appendLine("canary_grant_plan_manual_approval_required=$manualApprovalRequired")
            appendLine("canary_grant_plan_manual_approval_observed=$manualApprovalObserved")
            appendLine("canary_grant_plan_required_approval_action=${requiredApprovalAction.name}")
            appendLine("canary_grant_plan_grant_required=$grantRequired")
            appendLine("canary_grant_plan_manual_grant_observed=$manualGrantObserved")
            appendLine("canary_grant_plan_grant_issued=$grantIssued")
            appendLine("canary_grant_plan_grant_valid=$grantValid")
            appendLine("canary_grant_plan_grant_expired=$grantExpired")
            appendLine("canary_grant_plan_grant_id=${grantId.toCanaryGrantEnvValue()}")
            appendLine("canary_grant_plan_grant_source=${grantSource.toCanaryGrantEnvValue()}")
            appendLine("canary_grant_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_grant_plan_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_grant_plan_proposed_session_ttl_ms=$proposedSessionTtlMs")
            appendLine("canary_grant_plan_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_grant_plan_kill_switch_required=$killSwitchRequired")
            appendLine("canary_grant_plan_pressure_blocker=${pressureBlocker.toCanaryGrantEnvValue()}")
            appendLine("canary_grant_plan_activation_waiting_hold_active=$activationWaitingHoldActive")
            appendLine("canary_grant_plan_session_waiting_hold_active=$sessionWaitingHoldActive")
            appendLine("canary_grant_plan_pending_grant_capability_count=$pendingGrantCapabilityCount")
            appendLine("canary_grant_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_grant_plan_actual_grant_count=$actualGrantCount")
            appendLine("canary_grant_plan_actual_approval_count=$actualApprovalCount")
            appendLine("canary_grant_plan_actual_session_count=$actualSessionCount")
            appendLine("canary_grant_plan_actual_activation_count=$actualActivationCount")
            appendLine("canary_grant_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_grant_plan_reason=${reason.toCanaryGrantEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_grant_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_gate_disposition=${item.gateDisposition.name}")
                appendLine("${prefix}_grant_disposition=${item.grantDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryGrantEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryGrantEnvValue()}")
            }
            appendLine("canary_grant_plan_boundary=dry_run_manual_only_no_grant_issue_no_auto_approval_no_approval_no_session_creation_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanaryGrantPlanDryRun {
    fun evaluate(
        approvalGate: RuntimeCanaryApprovalGateDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryGrantPlanDryRunSnapshot {
        val state = when (approvalGate.state) {
            RuntimeCanaryApprovalGateState.PENDING_MANUAL_APPROVAL ->
                RuntimeCanaryGrantPlanState.GRANT_PENDING
            RuntimeCanaryApprovalGateState.WAITING_FOR_WARMING ->
                RuntimeCanaryGrantPlanState.WAITING_FOR_WARMING
            RuntimeCanaryApprovalGateState.LOCKED ->
                RuntimeCanaryGrantPlanState.LOCKED
        }
        val items = approvalGate.items.map { gateItem ->
            val disposition = grantDispositionFor(state, gateItem)
            RuntimeCanaryGrantPlanItem(
                capability = gateItem.capability,
                inScope = gateItem.inScope,
                gateDisposition = gateItem.gateDisposition,
                grantDisposition = disposition,
                blocker = grantBlockerFor(disposition, gateItem, approvalGate),
                reason = buildItemReason(approvalGate, gateItem, disposition)
            )
        }

        return RuntimeCanaryGrantPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            approvalGateState = approvalGate.state,
            approvalGateRecommendation = approvalGate.recommendation,
            approvalGateGranted = approvalGate.approvalGranted,
            manualApprovalRequired = approvalGate.manualApprovalRequired,
            manualApprovalObserved = approvalGate.manualApprovalObserved,
            requiredApprovalAction = approvalGate.requestAction,
            grantRequired = state == RuntimeCanaryGrantPlanState.GRANT_PENDING,
            manualGrantObserved = false,
            grantIssued = false,
            grantValid = false,
            grantExpired = false,
            grantId = "none",
            grantSource = "none",
            plannedScope = approvalGate.plannedScope,
            proposedSessionKind = approvalGate.proposedSessionKind,
            proposedSessionTtlMs = approvalGate.proposedSessionTtlMs,
            rollbackPolicy = approvalGate.rollbackPolicy,
            killSwitchRequired = approvalGate.killSwitchRequired,
            pressureBlocker = approvalGate.pressureBlocker,
            activationWaitingHoldActive = approvalGate.activationWaitingHoldActive,
            sessionWaitingHoldActive = approvalGate.sessionWaitingHoldActive,
            pendingGrantCapabilityCount = items.count {
                it.grantDisposition == RuntimeCanaryGrantDisposition.WAIT_FOR_MANUAL_GRANT
            },
            blockedCapabilityCount = items.count {
                it.grantDisposition != RuntimeCanaryGrantDisposition.WAIT_FOR_MANUAL_GRANT
            },
            actualGrantCount = 0,
            actualApprovalCount = 0,
            actualSessionCount = 0,
            actualActivationCount = 0,
            actualEnforcementCount = 0,
            reason = buildReason(approvalGate, state),
            items = items
        )
    }

    private fun grantDispositionFor(
        state: RuntimeCanaryGrantPlanState,
        gateItem: RuntimeCanaryApprovalGateItem
    ): RuntimeCanaryGrantDisposition {
        return when {
            state == RuntimeCanaryGrantPlanState.WAITING_FOR_WARMING ->
                RuntimeCanaryGrantDisposition.WAIT_FOR_WARMING_HOLD
            gateItem.gateDisposition == RuntimeCanaryApprovalGateDisposition.OUT_OF_SCOPE ->
                RuntimeCanaryGrantDisposition.OUT_OF_SCOPE
            state != RuntimeCanaryGrantPlanState.GRANT_PENDING ->
                RuntimeCanaryGrantDisposition.BLOCKED_BY_APPROVAL_GATE
            gateItem.gateDisposition == RuntimeCanaryApprovalGateDisposition.WAIT_FOR_MANUAL_APPROVAL ->
                RuntimeCanaryGrantDisposition.WAIT_FOR_MANUAL_GRANT
            else -> RuntimeCanaryGrantDisposition.BLOCKED_BY_APPROVAL_GATE
        }
    }

    private fun grantBlockerFor(
        disposition: RuntimeCanaryGrantDisposition,
        gateItem: RuntimeCanaryApprovalGateItem,
        approvalGate: RuntimeCanaryApprovalGateDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanaryGrantDisposition.WAIT_FOR_MANUAL_GRANT -> "manual_grant_pending"
            RuntimeCanaryGrantDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanaryGrantDisposition.BLOCKED_BY_APPROVAL_GATE ->
                gateItem.blocker.takeIf { it != "none" }
                    ?: approvalGate.pressureBlocker.takeIf { it != "none" }
                    ?: "approval_gate_not_ready"
            RuntimeCanaryGrantDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryGrantPlanState
    ): RuntimeCanaryGrantPlanRecommendation {
        return when (state) {
            RuntimeCanaryGrantPlanState.GRANT_PENDING ->
                RuntimeCanaryGrantPlanRecommendation.WAIT_FOR_MANUAL_GRANT
            RuntimeCanaryGrantPlanState.WAITING_FOR_WARMING ->
                RuntimeCanaryGrantPlanRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanaryGrantPlanState.LOCKED ->
                RuntimeCanaryGrantPlanRecommendation.WAIT_FOR_APPROVAL_GATE
        }
    }

    private fun buildReason(
        approvalGate: RuntimeCanaryApprovalGateDryRunSnapshot,
        state: RuntimeCanaryGrantPlanState
    ): String {
        return "state=${state.name},gateState=${approvalGate.state.name}," +
            "approvalGranted=${approvalGate.approvalGranted}," +
            "manualApprovalObserved=${approvalGate.manualApprovalObserved}," +
            "action=${approvalGate.requestAction.name},plannedScope=${approvalGate.plannedScope.name}," +
            "pressureBlocker=${approvalGate.pressureBlocker}," +
            "activationWait=${approvalGate.activationWaitingHoldActive}," +
            "sessionWait=${approvalGate.sessionWaitingHoldActive}"
    }

    private fun buildItemReason(
        approvalGate: RuntimeCanaryApprovalGateDryRunSnapshot,
        gateItem: RuntimeCanaryApprovalGateItem,
        disposition: RuntimeCanaryGrantDisposition
    ): String {
        return "gateState=${approvalGate.state.name},plannedScope=${approvalGate.plannedScope.name}," +
            "gateDisposition=${gateItem.gateDisposition.name}," +
            "grantDisposition=${disposition.name},gateReason=${gateItem.reason}"
    }
}

private fun String?.toCanaryGrantEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
