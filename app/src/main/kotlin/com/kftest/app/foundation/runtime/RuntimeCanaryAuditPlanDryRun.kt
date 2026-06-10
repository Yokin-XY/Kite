package com.kftest.app.foundation.runtime

enum class RuntimeCanaryAuditState {
    LOCKED,
    SHADOW_READY,
    CANARY_REVIEW,
    ROLLBACK_REVIEW
}

enum class RuntimeCanaryAuditRecommendation {
    KEEP_SHADOW,
    REVIEW_CANARY_ENTRY,
    REVIEW_CANARY_ENFORCEMENT,
    REVIEW_CANARY_ROLLBACK
}

data class RuntimeCanaryAuditPlanDryRunSnapshot(
    val mode: String = "runtime_canary_audit_plan_dry_run_v0",
    val enforcementMode: String = "observe_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryAuditState = RuntimeCanaryAuditState.LOCKED,
    val recommendation: RuntimeCanaryAuditRecommendation =
        RuntimeCanaryAuditRecommendation.KEEP_SHADOW,
    val pressureStabilityState: RuntimePressureStabilityState = RuntimePressureStabilityState.NO_SOURCE,
    val pressureArmingState: RuntimePressureCanaryArmingState = RuntimePressureCanaryArmingState.BLOCKED,
    val readinessState: RuntimeGovernanceReadinessState = RuntimeGovernanceReadinessState.CANARY_BLOCKED,
    val entryState: RuntimeCanaryEntryState = RuntimeCanaryEntryState.BLOCKED,
    val scopeState: RuntimeCanaryScopePlanState = RuntimeCanaryScopePlanState.LOCKED,
    val activationState: RuntimeCanaryActivationState = RuntimeCanaryActivationState.LOCKED,
    val sessionState: RuntimeCanarySessionState = RuntimeCanarySessionState.LOCKED,
    val approvalRequestState: RuntimeCanaryApprovalRequestState = RuntimeCanaryApprovalRequestState.LOCKED,
    val approvalGateState: RuntimeCanaryApprovalGateState = RuntimeCanaryApprovalGateState.LOCKED,
    val grantPlanState: RuntimeCanaryGrantPlanState = RuntimeCanaryGrantPlanState.LOCKED,
    val sessionStartState: RuntimeCanarySessionStartState = RuntimeCanarySessionStartState.LOCKED,
    val sessionLeaseState: RuntimeCanarySessionLeaseState = RuntimeCanarySessionLeaseState.LOCKED,
    val enforcementPlanState: RuntimeCanaryEnforcementPlanState = RuntimeCanaryEnforcementPlanState.LOCKED,
    val rollbackPlanState: RuntimeCanaryRollbackPlanState = RuntimeCanaryRollbackPlanState.LOCKED,
    val telemetryHealthy: Boolean = false,
    val pressureStableForCanary: Boolean = false,
    val readinessCanaryReadyCount: Int = 0,
    val readinessBlockedCount: Int = 0,
    val allowedCapabilityCount: Int = 0,
    val pendingApprovalCapabilityCount: Int = 0,
    val pendingGrantCapabilityCount: Int = 0,
    val waitingStartCapabilityCount: Int = 0,
    val simulatedEnforcementCount: Int = 0,
    val simulatedRollbackCount: Int = 0,
    val actualApprovalCount: Int = 0,
    val actualGrantCount: Int = 0,
    val actualLeaseCount: Int = 0,
    val actualSessionCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val actualRollbackCount: Int = 0,
    val actualKillSwitchCount: Int = 0,
    val actualLaneControlCount: Int = 0,
    val actualQueueCount: Int = 0,
    val actualStartCount: Int = 0,
    val actualReclaimCount: Int = 0,
    val actualRestartCount: Int = 0,
    val actualTerminateCount: Int = 0,
    val dryRunBoundaryCount: Int = 0,
    val unsafeActualActionCount: Int = 0,
    val reason: String = "waiting_for_canary_chain"
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation pressure=$pressureStabilityState/$pressureArmingState " +
            "readiness=$readinessState entry=$entryState scope=$scopeState activation=$activationState " +
            "session=$sessionState approval=$approvalRequestState gate=$approvalGateState grant=$grantPlanState " +
            "start=$sessionStartState lease=$sessionLeaseState enforcementPlan=$enforcementPlanState " +
            "rollback=$rollbackPlanState allowed=$allowedCapabilityCount pendingApproval=$pendingApprovalCapabilityCount " +
            "pendingGrant=$pendingGrantCapabilityCount simulatedEnforcement=$simulatedEnforcementCount " +
            "simulatedRollback=$simulatedRollbackCount actualActions=$unsafeActualActionCount " +
            "boundaries=$dryRunBoundaryCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("canary_audit_plan_mode=${mode.toCanaryAuditEnvValue()}")
            appendLine("canary_audit_plan_enforcement_mode=${enforcementMode.toCanaryAuditEnvValue()}")
            appendLine("canary_audit_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_audit_plan_generated_at=$generatedAtMs")
            appendLine("canary_audit_plan_state=${state.name}")
            appendLine("canary_audit_plan_recommendation=${recommendation.name}")
            appendLine("canary_audit_plan_pressure_stability_state=${pressureStabilityState.name}")
            appendLine("canary_audit_plan_pressure_arming_state=${pressureArmingState.name}")
            appendLine("canary_audit_plan_readiness_state=${readinessState.name}")
            appendLine("canary_audit_plan_entry_state=${entryState.name}")
            appendLine("canary_audit_plan_scope_state=${scopeState.name}")
            appendLine("canary_audit_plan_activation_state=${activationState.name}")
            appendLine("canary_audit_plan_session_state=${sessionState.name}")
            appendLine("canary_audit_plan_approval_request_state=${approvalRequestState.name}")
            appendLine("canary_audit_plan_approval_gate_state=${approvalGateState.name}")
            appendLine("canary_audit_plan_grant_plan_state=${grantPlanState.name}")
            appendLine("canary_audit_plan_session_start_state=${sessionStartState.name}")
            appendLine("canary_audit_plan_session_lease_state=${sessionLeaseState.name}")
            appendLine("canary_audit_plan_enforcement_plan_state=${enforcementPlanState.name}")
            appendLine("canary_audit_plan_rollback_plan_state=${rollbackPlanState.name}")
            appendLine("canary_audit_plan_telemetry_healthy=$telemetryHealthy")
            appendLine("canary_audit_plan_pressure_stable_for_canary=$pressureStableForCanary")
            appendLine("canary_audit_plan_readiness_canary_ready_count=$readinessCanaryReadyCount")
            appendLine("canary_audit_plan_readiness_blocked_count=$readinessBlockedCount")
            appendLine("canary_audit_plan_allowed_capability_count=$allowedCapabilityCount")
            appendLine("canary_audit_plan_pending_approval_capability_count=$pendingApprovalCapabilityCount")
            appendLine("canary_audit_plan_pending_grant_capability_count=$pendingGrantCapabilityCount")
            appendLine("canary_audit_plan_waiting_start_capability_count=$waitingStartCapabilityCount")
            appendLine("canary_audit_plan_simulated_enforcement_count=$simulatedEnforcementCount")
            appendLine("canary_audit_plan_simulated_rollback_count=$simulatedRollbackCount")
            appendLine("canary_audit_plan_actual_approval_count=$actualApprovalCount")
            appendLine("canary_audit_plan_actual_grant_count=$actualGrantCount")
            appendLine("canary_audit_plan_actual_lease_count=$actualLeaseCount")
            appendLine("canary_audit_plan_actual_session_count=$actualSessionCount")
            appendLine("canary_audit_plan_actual_activation_count=$actualActivationCount")
            appendLine("canary_audit_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_audit_plan_actual_rollback_count=$actualRollbackCount")
            appendLine("canary_audit_plan_actual_kill_switch_count=$actualKillSwitchCount")
            appendLine("canary_audit_plan_actual_lane_control_count=$actualLaneControlCount")
            appendLine("canary_audit_plan_actual_queue_count=$actualQueueCount")
            appendLine("canary_audit_plan_actual_start_count=$actualStartCount")
            appendLine("canary_audit_plan_actual_reclaim_count=$actualReclaimCount")
            appendLine("canary_audit_plan_actual_restart_count=$actualRestartCount")
            appendLine("canary_audit_plan_actual_terminate_count=$actualTerminateCount")
            appendLine("canary_audit_plan_dry_run_boundary_count=$dryRunBoundaryCount")
            appendLine("canary_audit_plan_unsafe_actual_action_count=$unsafeActualActionCount")
            appendLine("canary_audit_plan_reason=${reason.toCanaryAuditEnvValue()}")
            appendLine("canary_audit_plan_boundary=observe_only_audit_no_approval_no_grant_no_session_lease_no_enforcement_no_rollback_no_kill_switch_no_lane_control_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate")
        }
    }
}

object RuntimeCanaryAuditPlanDryRun {
    fun evaluate(
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        governanceReadiness: RuntimeGovernanceReadinessGateDryRunSnapshot,
        canaryEntry: RuntimeCanaryEntryPlanDryRunSnapshot,
        canaryScope: RuntimeCanaryScopePlanDryRunSnapshot,
        canaryActivation: RuntimeCanaryActivationPlanDryRunSnapshot,
        canarySession: RuntimeCanarySessionPlanDryRunSnapshot,
        approvalRequest: RuntimeCanaryApprovalRequestDryRunSnapshot,
        approvalGate: RuntimeCanaryApprovalGateDryRunSnapshot,
        grantPlan: RuntimeCanaryGrantPlanDryRunSnapshot,
        sessionStartPlan: RuntimeCanarySessionStartPlanDryRunSnapshot,
        sessionLeasePlan: RuntimeCanarySessionLeasePlanDryRunSnapshot,
        enforcementPlan: RuntimeCanaryEnforcementPlanDryRunSnapshot,
        rollbackPlan: RuntimeCanaryRollbackPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryAuditPlanDryRunSnapshot {
        val actualActionCount = listOf(
            approvalRequest.actualApprovalCount,
            approvalGate.actualApprovalCount,
            grantPlan.actualGrantCount,
            sessionStartPlan.actualStartCount,
            sessionLeasePlan.actualLeaseCount,
            enforcementPlan.actualEnforcementCount,
            rollbackPlan.actualRollbackCount,
            rollbackPlan.actualKillSwitchCount,
            enforcementPlan.actualLaneControlCount,
            enforcementPlan.actualQueueCount,
            enforcementPlan.actualStartCount,
            rollbackPlan.actualTerminateCount,
            rollbackPlan.actualRestartCount,
            rollbackPlan.actualReclaimCount
        ).sum()
        val state = when {
            rollbackPlan.state == RuntimeCanaryRollbackPlanState.ROLLBACK_REVIEW ->
                RuntimeCanaryAuditState.ROLLBACK_REVIEW
            enforcementPlan.state == RuntimeCanaryEnforcementPlanState.ENFORCEMENT_REVIEW ->
                RuntimeCanaryAuditState.CANARY_REVIEW
            canaryEntry.entryAllowed || governanceReadiness.canaryReadyCount > 0 ->
                RuntimeCanaryAuditState.SHADOW_READY
            else -> RuntimeCanaryAuditState.LOCKED
        }

        return RuntimeCanaryAuditPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            pressureStabilityState = pressureStability.state,
            pressureArmingState = pressureStability.canaryArmingState,
            readinessState = governanceReadiness.state,
            entryState = canaryEntry.state,
            scopeState = canaryScope.state,
            activationState = canaryActivation.state,
            sessionState = canarySession.state,
            approvalRequestState = approvalRequest.state,
            approvalGateState = approvalGate.state,
            grantPlanState = grantPlan.state,
            sessionStartState = sessionStartPlan.state,
            sessionLeaseState = sessionLeasePlan.state,
            enforcementPlanState = enforcementPlan.state,
            rollbackPlanState = rollbackPlan.state,
            telemetryHealthy = rollbackPlan.telemetryHealthy,
            pressureStableForCanary = pressureStability.canaryStable,
            readinessCanaryReadyCount = governanceReadiness.canaryReadyCount,
            readinessBlockedCount = governanceReadiness.blockedCount,
            allowedCapabilityCount = canaryEntry.allowedCapabilityCount,
            pendingApprovalCapabilityCount = approvalGate.pendingCapabilityCount,
            pendingGrantCapabilityCount = grantPlan.pendingGrantCapabilityCount,
            waitingStartCapabilityCount = sessionLeasePlan.waitingStartCapabilityCount,
            simulatedEnforcementCount = enforcementPlan.simulatedEnforcementCount,
            simulatedRollbackCount = rollbackPlan.simulatedRollbackCount,
            actualApprovalCount = approvalGate.actualApprovalCount,
            actualGrantCount = grantPlan.actualGrantCount,
            actualLeaseCount = sessionLeasePlan.actualLeaseCount,
            actualSessionCount = sessionLeasePlan.actualSessionCount,
            actualActivationCount = sessionLeasePlan.actualActivationCount,
            actualEnforcementCount = enforcementPlan.actualEnforcementCount,
            actualRollbackCount = rollbackPlan.actualRollbackCount,
            actualKillSwitchCount = rollbackPlan.actualKillSwitchCount,
            actualLaneControlCount = enforcementPlan.actualLaneControlCount,
            actualQueueCount = enforcementPlan.actualQueueCount,
            actualStartCount = enforcementPlan.actualStartCount,
            actualReclaimCount = rollbackPlan.actualReclaimCount,
            actualRestartCount = rollbackPlan.actualRestartCount,
            actualTerminateCount = rollbackPlan.actualTerminateCount,
            dryRunBoundaryCount = 13,
            unsafeActualActionCount = actualActionCount,
            reason = buildReason(state, pressureStability, governanceReadiness, enforcementPlan, rollbackPlan)
        )
    }

    private fun recommendationFor(state: RuntimeCanaryAuditState): RuntimeCanaryAuditRecommendation {
        return when (state) {
            RuntimeCanaryAuditState.ROLLBACK_REVIEW ->
                RuntimeCanaryAuditRecommendation.REVIEW_CANARY_ROLLBACK
            RuntimeCanaryAuditState.CANARY_REVIEW ->
                RuntimeCanaryAuditRecommendation.REVIEW_CANARY_ENFORCEMENT
            RuntimeCanaryAuditState.SHADOW_READY ->
                RuntimeCanaryAuditRecommendation.REVIEW_CANARY_ENTRY
            RuntimeCanaryAuditState.LOCKED ->
                RuntimeCanaryAuditRecommendation.KEEP_SHADOW
        }
    }

    private fun buildReason(
        state: RuntimeCanaryAuditState,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        governanceReadiness: RuntimeGovernanceReadinessGateDryRunSnapshot,
        enforcementPlan: RuntimeCanaryEnforcementPlanDryRunSnapshot,
        rollbackPlan: RuntimeCanaryRollbackPlanDryRunSnapshot
    ): String {
        return "state=${state.name},pressure=${pressureStability.state.name}/${pressureStability.canaryArmingState.name}," +
            "readiness=${governanceReadiness.state.name},canaryReady=${governanceReadiness.canaryReadyCount}," +
            "blocked=${governanceReadiness.blockedCount},enforcement=${enforcementPlan.state.name}," +
            "rollback=${rollbackPlan.state.name},rollbackTrigger=${rollbackPlan.rollbackTrigger.name}"
    }
}

private fun String?.toCanaryAuditEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
