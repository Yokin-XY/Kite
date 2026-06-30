package com.kite.app.foundation.runtime

enum class RuntimeCanaryApprovalGateState {
    LOCKED,
    WAITING_FOR_WARMING,
    PENDING_MANUAL_APPROVAL
}

enum class RuntimeCanaryApprovalGateRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_APPROVAL_REQUEST,
    WAIT_FOR_PRESSURE_WARMING,
    WAIT_FOR_MANUAL_APPROVAL
}

enum class RuntimeCanaryApprovalGateDisposition {
    WAIT_FOR_MANUAL_APPROVAL,
    WAIT_FOR_WARMING_HOLD,
    BLOCKED_BY_APPROVAL_REQUEST,
    OUT_OF_SCOPE
}

data class RuntimeCanaryApprovalGateItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val requestDisposition: RuntimeCanaryApprovalDisposition,
    val gateDisposition: RuntimeCanaryApprovalGateDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryApprovalGateDryRunSnapshot(
    val mode: String = "runtime_canary_approval_gate_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryApprovalGateState = RuntimeCanaryApprovalGateState.LOCKED,
    val recommendation: RuntimeCanaryApprovalGateRecommendation =
        RuntimeCanaryApprovalGateRecommendation.KEEP_SHADOW,
    val requestState: RuntimeCanaryApprovalRequestState = RuntimeCanaryApprovalRequestState.LOCKED,
    val requestReady: Boolean = false,
    val requestAction: RuntimeCanaryApprovalAction = RuntimeCanaryApprovalAction.NONE,
    val manualApprovalRequired: Boolean = false,
    val manualApprovalObserved: Boolean = false,
    val approvalGranted: Boolean = false,
    val approvalExpired: Boolean = false,
    val autoApprovalAllowed: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val proposedSessionTtlMs: Long = 0L,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val killSwitchRequired: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val activationWaitingHoldActive: Boolean = false,
    val sessionWaitingHoldActive: Boolean = false,
    val pendingCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val actualApprovalCount: Int = 0,
    val actualSessionCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val reason: String = "waiting_for_approval_request",
    val items: List<RuntimeCanaryApprovalGateItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation request=$requestState/$requestReady " +
            "manualRequired=$manualApprovalRequired observed=$manualApprovalObserved granted=$approvalGranted " +
            "expired=$approvalExpired autoApproval=$autoApprovalAllowed scope=$plannedScope kind=$proposedSessionKind " +
            "pressure=$pressureBlocker activationWait=$activationWaitingHoldActive " +
            "sessionWait=$sessionWaitingHoldActive pending=$pendingCapabilityCount " +
            "blocked=$blockedCapabilityCount actualApproval=$actualApprovalCount " +
            "actualSessions=$actualSessionCount actualActivation=$actualActivationCount " +
            "actualEnforcement=$actualEnforcementCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_approval_gate_mode=${mode.toCanaryApprovalGateEnvValue()}")
            appendLine("canary_approval_gate_enforcement_mode=${enforcementMode.toCanaryApprovalGateEnvValue()}")
            appendLine("canary_approval_gate_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_approval_gate_generated_at=$generatedAtMs")
            appendLine("canary_approval_gate_state=${state.name}")
            appendLine("canary_approval_gate_recommendation=${recommendation.name}")
            appendLine("canary_approval_gate_request_state=${requestState.name}")
            appendLine("canary_approval_gate_request_ready=$requestReady")
            appendLine("canary_approval_gate_request_action=${requestAction.name}")
            appendLine("canary_approval_gate_manual_approval_required=$manualApprovalRequired")
            appendLine("canary_approval_gate_manual_approval_observed=$manualApprovalObserved")
            appendLine("canary_approval_gate_approval_granted=$approvalGranted")
            appendLine("canary_approval_gate_approval_expired=$approvalExpired")
            appendLine("canary_approval_gate_auto_approval_allowed=$autoApprovalAllowed")
            appendLine("canary_approval_gate_planned_scope=${plannedScope.name}")
            appendLine("canary_approval_gate_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_approval_gate_proposed_session_ttl_ms=$proposedSessionTtlMs")
            appendLine("canary_approval_gate_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_approval_gate_kill_switch_required=$killSwitchRequired")
            appendLine("canary_approval_gate_pressure_blocker=${pressureBlocker.toCanaryApprovalGateEnvValue()}")
            appendLine("canary_approval_gate_activation_waiting_hold_active=$activationWaitingHoldActive")
            appendLine("canary_approval_gate_session_waiting_hold_active=$sessionWaitingHoldActive")
            appendLine("canary_approval_gate_pending_capability_count=$pendingCapabilityCount")
            appendLine("canary_approval_gate_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_approval_gate_actual_approval_count=$actualApprovalCount")
            appendLine("canary_approval_gate_actual_session_count=$actualSessionCount")
            appendLine("canary_approval_gate_actual_activation_count=$actualActivationCount")
            appendLine("canary_approval_gate_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_approval_gate_reason=${reason.toCanaryApprovalGateEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_approval_gate_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_request_disposition=${item.requestDisposition.name}")
                appendLine("${prefix}_gate_disposition=${item.gateDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryApprovalGateEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryApprovalGateEnvValue()}")
            }
            appendLine("canary_approval_gate_boundary=dry_run_manual_only_no_auto_approval_no_approval_no_session_creation_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanaryApprovalGateDryRun {
    fun evaluate(
        approvalRequest: RuntimeCanaryApprovalRequestDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryApprovalGateDryRunSnapshot {
        val state = when {
            approvalRequest.approvalReady -> RuntimeCanaryApprovalGateState.PENDING_MANUAL_APPROVAL
            approvalRequest.state == RuntimeCanaryApprovalRequestState.WAITING_FOR_WARMING ->
                RuntimeCanaryApprovalGateState.WAITING_FOR_WARMING
            else -> RuntimeCanaryApprovalGateState.LOCKED
        }
        val items = approvalRequest.items.map { requestItem ->
            val disposition = gateDispositionFor(state, requestItem)
            RuntimeCanaryApprovalGateItem(
                capability = requestItem.capability,
                inScope = requestItem.inScope,
                requestDisposition = requestItem.approvalDisposition,
                gateDisposition = disposition,
                blocker = gateBlockerFor(disposition, requestItem, approvalRequest),
                reason = buildItemReason(approvalRequest, requestItem, disposition)
            )
        }

        return RuntimeCanaryApprovalGateDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            requestState = approvalRequest.state,
            requestReady = approvalRequest.approvalReady,
            requestAction = approvalRequest.approvalAction,
            manualApprovalRequired = approvalRequest.manualApprovalRequired,
            manualApprovalObserved = false,
            approvalGranted = false,
            approvalExpired = false,
            autoApprovalAllowed = false,
            plannedScope = approvalRequest.plannedScope,
            proposedSessionKind = approvalRequest.proposedSessionKind,
            proposedSessionTtlMs = approvalRequest.proposedSessionTtlMs,
            rollbackPolicy = approvalRequest.rollbackPolicy,
            killSwitchRequired = approvalRequest.killSwitchRequired,
            pressureBlocker = approvalRequest.pressureBlocker,
            activationWaitingHoldActive = approvalRequest.activationWaitingHoldActive,
            sessionWaitingHoldActive = approvalRequest.sessionWaitingHoldActive,
            pendingCapabilityCount = items.count {
                it.gateDisposition == RuntimeCanaryApprovalGateDisposition.WAIT_FOR_MANUAL_APPROVAL
            },
            blockedCapabilityCount = items.count {
                it.gateDisposition != RuntimeCanaryApprovalGateDisposition.WAIT_FOR_MANUAL_APPROVAL
            },
            actualApprovalCount = 0,
            actualSessionCount = 0,
            actualActivationCount = 0,
            actualEnforcementCount = 0,
            reason = buildReason(approvalRequest, state),
            items = items
        )
    }

    private fun gateDispositionFor(
        state: RuntimeCanaryApprovalGateState,
        requestItem: RuntimeCanaryApprovalRequestItem
    ): RuntimeCanaryApprovalGateDisposition {
        return when {
            state == RuntimeCanaryApprovalGateState.WAITING_FOR_WARMING ->
                RuntimeCanaryApprovalGateDisposition.WAIT_FOR_WARMING_HOLD
            requestItem.approvalDisposition == RuntimeCanaryApprovalDisposition.OUT_OF_SCOPE ->
                RuntimeCanaryApprovalGateDisposition.OUT_OF_SCOPE
            state != RuntimeCanaryApprovalGateState.PENDING_MANUAL_APPROVAL ->
                RuntimeCanaryApprovalGateDisposition.BLOCKED_BY_APPROVAL_REQUEST
            requestItem.approvalDisposition == RuntimeCanaryApprovalDisposition.WOULD_REQUEST_APPROVAL ->
                RuntimeCanaryApprovalGateDisposition.WAIT_FOR_MANUAL_APPROVAL
            else -> RuntimeCanaryApprovalGateDisposition.BLOCKED_BY_APPROVAL_REQUEST
        }
    }

    private fun gateBlockerFor(
        disposition: RuntimeCanaryApprovalGateDisposition,
        requestItem: RuntimeCanaryApprovalRequestItem,
        approvalRequest: RuntimeCanaryApprovalRequestDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanaryApprovalGateDisposition.WAIT_FOR_MANUAL_APPROVAL -> "manual_approval_pending"
            RuntimeCanaryApprovalGateDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanaryApprovalGateDisposition.BLOCKED_BY_APPROVAL_REQUEST ->
                requestItem.blocker.takeIf { it != "none" }
                    ?: approvalRequest.pressureBlocker.takeIf { it != "none" }
                    ?: "approval_request_not_ready"
            RuntimeCanaryApprovalGateDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryApprovalGateState
    ): RuntimeCanaryApprovalGateRecommendation {
        return when (state) {
            RuntimeCanaryApprovalGateState.PENDING_MANUAL_APPROVAL ->
                RuntimeCanaryApprovalGateRecommendation.WAIT_FOR_MANUAL_APPROVAL
            RuntimeCanaryApprovalGateState.WAITING_FOR_WARMING ->
                RuntimeCanaryApprovalGateRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanaryApprovalGateState.LOCKED ->
                RuntimeCanaryApprovalGateRecommendation.WAIT_FOR_APPROVAL_REQUEST
        }
    }

    private fun buildReason(
        approvalRequest: RuntimeCanaryApprovalRequestDryRunSnapshot,
        state: RuntimeCanaryApprovalGateState
    ): String {
        return "state=${state.name},requestState=${approvalRequest.state.name}," +
            "requestReady=${approvalRequest.approvalReady},manualRequired=${approvalRequest.manualApprovalRequired}," +
            "action=${approvalRequest.approvalAction.name},plannedScope=${approvalRequest.plannedScope.name}," +
            "pressureBlocker=${approvalRequest.pressureBlocker}," +
            "activationWait=${approvalRequest.activationWaitingHoldActive}," +
            "sessionWait=${approvalRequest.sessionWaitingHoldActive}"
    }

    private fun buildItemReason(
        approvalRequest: RuntimeCanaryApprovalRequestDryRunSnapshot,
        requestItem: RuntimeCanaryApprovalRequestItem,
        disposition: RuntimeCanaryApprovalGateDisposition
    ): String {
        return "requestState=${approvalRequest.state.name},plannedScope=${approvalRequest.plannedScope.name}," +
            "approvalDisposition=${requestItem.approvalDisposition.name}," +
            "gateDisposition=${disposition.name},requestReason=${requestItem.reason}"
    }
}

private fun String?.toCanaryApprovalGateEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
