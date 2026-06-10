package com.kftest.app.foundation.runtime

enum class RuntimeCanaryRollbackPlanState {
    LOCKED,
    WATCHING_CANARY,
    ROLLBACK_REVIEW
}

enum class RuntimeCanaryRollbackRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_CANARY_ENFORCEMENT,
    OBSERVE_CANARY_HEALTH,
    REVIEW_CANARY_ROLLBACK
}

enum class RuntimeCanaryRollbackTrigger {
    NONE,
    PRESSURE_REGRESSION,
    TELEMETRY_UNHEALTHY,
    PRESSURE_AND_TELEMETRY
}

enum class RuntimeCanaryRollbackDisposition {
    WATCH_ONLY,
    WOULD_ROLLBACK_SHADOW_CANARY,
    BLOCKED_BY_ENFORCEMENT_PLAN,
    OUT_OF_SCOPE
}

data class RuntimeCanaryRollbackPlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val enforcementDisposition: RuntimeCanaryEnforcementDisposition,
    val rollbackDisposition: RuntimeCanaryRollbackDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryRollbackPlanDryRunSnapshot(
    val mode: String = "runtime_canary_rollback_plan_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryRollbackPlanState = RuntimeCanaryRollbackPlanState.LOCKED,
    val recommendation: RuntimeCanaryRollbackRecommendation =
        RuntimeCanaryRollbackRecommendation.KEEP_SHADOW,
    val enforcementPlanState: RuntimeCanaryEnforcementPlanState =
        RuntimeCanaryEnforcementPlanState.LOCKED,
    val canaryActiveSimulated: Boolean = false,
    val rollbackPolicy: RuntimeCanarySessionRollbackPolicy = RuntimeCanarySessionRollbackPolicy.NONE,
    val rollbackRequired: Boolean = false,
    val rollbackTrigger: RuntimeCanaryRollbackTrigger = RuntimeCanaryRollbackTrigger.NONE,
    val telemetryHealthy: Boolean = false,
    val telemetryHealthState: ProotTelemetryHealthState = ProotTelemetryHealthState.NOT_STARTED,
    val telemetryBlocker: String = "not_started",
    val pressureStableForCanary: Boolean = false,
    val pressureStabilityState: RuntimePressureStabilityState = RuntimePressureStabilityState.NO_SOURCE,
    val pressureBlocker: String = "waiting_for_telemetry",
    val killSwitchRequired: Boolean = false,
    val killSwitchWouldArm: Boolean = false,
    val rollbackWouldArm: Boolean = false,
    val rollbackArmed: Boolean = false,
    val killSwitchArmed: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val proposedSessionKind: RuntimeCanarySessionKind = RuntimeCanarySessionKind.NONE,
    val leaseTtlMs: Long = 0L,
    val reviewCapabilityCount: Int = 0,
    val watchCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val simulatedRollbackCount: Int = 0,
    val actualRollbackCount: Int = 0,
    val actualKillSwitchCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val actualTerminateCount: Int = 0,
    val actualRestartCount: Int = 0,
    val actualReclaimCount: Int = 0,
    val reason: String = "waiting_for_canary_enforcement",
    val items: List<RuntimeCanaryRollbackPlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation enforcement=$enforcementPlanState " +
            "active=$canaryActiveSimulated policy=$rollbackPolicy required=$rollbackRequired trigger=$rollbackTrigger " +
            "telemetry=$telemetryHealthState/$telemetryHealthy pressure=$pressureStabilityState/$pressureStableForCanary " +
            "killSwitchWouldArm=$killSwitchWouldArm rollbackWouldArm=$rollbackWouldArm " +
            "rollbackArmed=$rollbackArmed killSwitchArmed=$killSwitchArmed " +
            "review=$reviewCapabilityCount watch=$watchCapabilityCount blocked=$blockedCapabilityCount " +
            "simulatedRollback=$simulatedRollbackCount actualRollback=$actualRollbackCount " +
            "actualKillSwitch=$actualKillSwitchCount actualEnforcement=$actualEnforcementCount " +
            "terminate=$actualTerminateCount restart=$actualRestartCount reclaim=$actualReclaimCount " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_rollback_plan_mode=${mode.toCanaryRollbackEnvValue()}")
            appendLine("canary_rollback_plan_enforcement_mode=${enforcementMode.toCanaryRollbackEnvValue()}")
            appendLine("canary_rollback_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_rollback_plan_generated_at=$generatedAtMs")
            appendLine("canary_rollback_plan_state=${state.name}")
            appendLine("canary_rollback_plan_recommendation=${recommendation.name}")
            appendLine("canary_rollback_plan_enforcement_plan_state=${enforcementPlanState.name}")
            appendLine("canary_rollback_plan_canary_active_simulated=$canaryActiveSimulated")
            appendLine("canary_rollback_plan_rollback_policy=${rollbackPolicy.name}")
            appendLine("canary_rollback_plan_rollback_required=$rollbackRequired")
            appendLine("canary_rollback_plan_rollback_trigger=${rollbackTrigger.name}")
            appendLine("canary_rollback_plan_telemetry_healthy=$telemetryHealthy")
            appendLine("canary_rollback_plan_telemetry_health_state=${telemetryHealthState.name}")
            appendLine("canary_rollback_plan_telemetry_blocker=${telemetryBlocker.toCanaryRollbackEnvValue()}")
            appendLine("canary_rollback_plan_pressure_stable_for_canary=$pressureStableForCanary")
            appendLine("canary_rollback_plan_pressure_stability_state=${pressureStabilityState.name}")
            appendLine("canary_rollback_plan_pressure_blocker=${pressureBlocker.toCanaryRollbackEnvValue()}")
            appendLine("canary_rollback_plan_kill_switch_required=$killSwitchRequired")
            appendLine("canary_rollback_plan_kill_switch_would_arm=$killSwitchWouldArm")
            appendLine("canary_rollback_plan_rollback_would_arm=$rollbackWouldArm")
            appendLine("canary_rollback_plan_rollback_armed=$rollbackArmed")
            appendLine("canary_rollback_plan_kill_switch_armed=$killSwitchArmed")
            appendLine("canary_rollback_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_rollback_plan_proposed_session_kind=${proposedSessionKind.name}")
            appendLine("canary_rollback_plan_lease_ttl_ms=$leaseTtlMs")
            appendLine("canary_rollback_plan_review_capability_count=$reviewCapabilityCount")
            appendLine("canary_rollback_plan_watch_capability_count=$watchCapabilityCount")
            appendLine("canary_rollback_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_rollback_plan_simulated_rollback_count=$simulatedRollbackCount")
            appendLine("canary_rollback_plan_actual_rollback_count=$actualRollbackCount")
            appendLine("canary_rollback_plan_actual_kill_switch_count=$actualKillSwitchCount")
            appendLine("canary_rollback_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_rollback_plan_actual_terminate_count=$actualTerminateCount")
            appendLine("canary_rollback_plan_actual_restart_count=$actualRestartCount")
            appendLine("canary_rollback_plan_actual_reclaim_count=$actualReclaimCount")
            appendLine("canary_rollback_plan_reason=${reason.toCanaryRollbackEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_rollback_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_enforcement_disposition=${item.enforcementDisposition.name}")
                appendLine("${prefix}_rollback_disposition=${item.rollbackDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryRollbackEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryRollbackEnvValue()}")
            }
            appendLine("canary_rollback_plan_boundary=dry_run_manual_only_no_rollback_no_kill_switch_no_enforcement_no_lane_control_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate")
        }
    }
}

object RuntimeCanaryRollbackPlanDryRun {
    fun evaluate(
        enforcementPlan: RuntimeCanaryEnforcementPlanDryRunSnapshot,
        telemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryRollbackPlanDryRunSnapshot {
        val canaryActive = enforcementPlan.state == RuntimeCanaryEnforcementPlanState.ENFORCEMENT_REVIEW &&
            enforcementPlan.simulatedEnforcementCount > 0
        val trigger = resolveTrigger(
            canaryActive = canaryActive,
            telemetryHealthy = telemetryHealth.canaryHealthy,
            pressureStable = pressureStability.canaryStable
        )
        val state = when {
            !canaryActive -> RuntimeCanaryRollbackPlanState.LOCKED
            trigger == RuntimeCanaryRollbackTrigger.NONE -> RuntimeCanaryRollbackPlanState.WATCHING_CANARY
            else -> RuntimeCanaryRollbackPlanState.ROLLBACK_REVIEW
        }
        val rollbackWouldArm = canaryActive &&
            enforcementPlan.rollbackPolicy != RuntimeCanarySessionRollbackPolicy.NONE
        val killSwitchWouldArm = canaryActive && enforcementPlan.killSwitchRequired
        val items = enforcementPlan.items.map { enforcementItem ->
            val disposition = rollbackDispositionFor(state, enforcementItem)
            RuntimeCanaryRollbackPlanItem(
                capability = enforcementItem.capability,
                inScope = enforcementItem.inScope,
                enforcementDisposition = enforcementItem.enforcementDisposition,
                rollbackDisposition = disposition,
                blocker = rollbackBlockerFor(disposition, enforcementItem, trigger, telemetryHealth, pressureStability),
                reason = buildItemReason(enforcementPlan, enforcementItem, disposition, trigger)
            )
        }

        return RuntimeCanaryRollbackPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            enforcementPlanState = enforcementPlan.state,
            canaryActiveSimulated = canaryActive,
            rollbackPolicy = enforcementPlan.rollbackPolicy,
            rollbackRequired = state == RuntimeCanaryRollbackPlanState.ROLLBACK_REVIEW,
            rollbackTrigger = trigger,
            telemetryHealthy = telemetryHealth.canaryHealthy,
            telemetryHealthState = telemetryHealth.state,
            telemetryBlocker = telemetryHealth.blocker,
            pressureStableForCanary = pressureStability.canaryStable,
            pressureStabilityState = pressureStability.state,
            pressureBlocker = pressureStability.blocker,
            killSwitchRequired = enforcementPlan.killSwitchRequired,
            killSwitchWouldArm = killSwitchWouldArm,
            rollbackWouldArm = rollbackWouldArm,
            rollbackArmed = false,
            killSwitchArmed = false,
            plannedScope = enforcementPlan.plannedScope,
            proposedSessionKind = enforcementPlan.proposedSessionKind,
            leaseTtlMs = enforcementPlan.leaseTtlMs,
            reviewCapabilityCount = items.count {
                it.rollbackDisposition == RuntimeCanaryRollbackDisposition.WOULD_ROLLBACK_SHADOW_CANARY
            },
            watchCapabilityCount = items.count {
                it.rollbackDisposition == RuntimeCanaryRollbackDisposition.WATCH_ONLY
            },
            blockedCapabilityCount = items.count {
                it.rollbackDisposition == RuntimeCanaryRollbackDisposition.BLOCKED_BY_ENFORCEMENT_PLAN
            },
            simulatedRollbackCount = items.count {
                it.rollbackDisposition == RuntimeCanaryRollbackDisposition.WOULD_ROLLBACK_SHADOW_CANARY
            },
            actualRollbackCount = 0,
            actualKillSwitchCount = 0,
            actualEnforcementCount = 0,
            actualTerminateCount = 0,
            actualRestartCount = 0,
            actualReclaimCount = 0,
            reason = buildReason(enforcementPlan, telemetryHealth, pressureStability, state, trigger),
            items = items
        )
    }

    private fun resolveTrigger(
        canaryActive: Boolean,
        telemetryHealthy: Boolean,
        pressureStable: Boolean
    ): RuntimeCanaryRollbackTrigger {
        if (!canaryActive) return RuntimeCanaryRollbackTrigger.NONE
        return when {
            !telemetryHealthy && !pressureStable -> RuntimeCanaryRollbackTrigger.PRESSURE_AND_TELEMETRY
            !telemetryHealthy -> RuntimeCanaryRollbackTrigger.TELEMETRY_UNHEALTHY
            !pressureStable -> RuntimeCanaryRollbackTrigger.PRESSURE_REGRESSION
            else -> RuntimeCanaryRollbackTrigger.NONE
        }
    }

    private fun rollbackDispositionFor(
        state: RuntimeCanaryRollbackPlanState,
        enforcementItem: RuntimeCanaryEnforcementPlanItem
    ): RuntimeCanaryRollbackDisposition {
        return when {
            enforcementItem.enforcementDisposition == RuntimeCanaryEnforcementDisposition.OUT_OF_SCOPE ->
                RuntimeCanaryRollbackDisposition.OUT_OF_SCOPE
            state == RuntimeCanaryRollbackPlanState.ROLLBACK_REVIEW &&
                enforcementItem.enforcementDisposition == RuntimeCanaryEnforcementDisposition.WOULD_ENFORCE_SHADOW_CANARY ->
                RuntimeCanaryRollbackDisposition.WOULD_ROLLBACK_SHADOW_CANARY
            state == RuntimeCanaryRollbackPlanState.WATCHING_CANARY &&
                enforcementItem.enforcementDisposition == RuntimeCanaryEnforcementDisposition.WOULD_ENFORCE_SHADOW_CANARY ->
                RuntimeCanaryRollbackDisposition.WATCH_ONLY
            else -> RuntimeCanaryRollbackDisposition.BLOCKED_BY_ENFORCEMENT_PLAN
        }
    }

    private fun rollbackBlockerFor(
        disposition: RuntimeCanaryRollbackDisposition,
        enforcementItem: RuntimeCanaryEnforcementPlanItem,
        trigger: RuntimeCanaryRollbackTrigger,
        telemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanaryRollbackDisposition.WATCH_ONLY -> "none"
            RuntimeCanaryRollbackDisposition.WOULD_ROLLBACK_SHADOW_CANARY ->
                trigger.name.lowercase()
            RuntimeCanaryRollbackDisposition.BLOCKED_BY_ENFORCEMENT_PLAN ->
                enforcementItem.blocker.takeIf { it != "none" }
                    ?: telemetryHealth.blocker.takeIf { it != "none" }
                    ?: pressureStability.blocker.takeIf { it != "none" }
                    ?: "canary_not_active"
            RuntimeCanaryRollbackDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryRollbackPlanState
    ): RuntimeCanaryRollbackRecommendation {
        return when (state) {
            RuntimeCanaryRollbackPlanState.ROLLBACK_REVIEW ->
                RuntimeCanaryRollbackRecommendation.REVIEW_CANARY_ROLLBACK
            RuntimeCanaryRollbackPlanState.WATCHING_CANARY ->
                RuntimeCanaryRollbackRecommendation.OBSERVE_CANARY_HEALTH
            RuntimeCanaryRollbackPlanState.LOCKED ->
                RuntimeCanaryRollbackRecommendation.WAIT_FOR_CANARY_ENFORCEMENT
        }
    }

    private fun buildReason(
        enforcementPlan: RuntimeCanaryEnforcementPlanDryRunSnapshot,
        telemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        state: RuntimeCanaryRollbackPlanState,
        trigger: RuntimeCanaryRollbackTrigger
    ): String {
        return "state=${state.name},trigger=${trigger.name},enforcementState=${enforcementPlan.state.name}," +
            "simulatedEnforcement=${enforcementPlan.simulatedEnforcementCount}," +
            "telemetry=${telemetryHealth.state.name}/${telemetryHealth.canaryHealthy}," +
            "pressure=${pressureStability.state.name}/${pressureStability.canaryStable}," +
            "rollbackPolicy=${enforcementPlan.rollbackPolicy.name},killSwitch=${enforcementPlan.killSwitchRequired}"
    }

    private fun buildItemReason(
        enforcementPlan: RuntimeCanaryEnforcementPlanDryRunSnapshot,
        enforcementItem: RuntimeCanaryEnforcementPlanItem,
        disposition: RuntimeCanaryRollbackDisposition,
        trigger: RuntimeCanaryRollbackTrigger
    ): String {
        return "enforcementState=${enforcementPlan.state.name},trigger=${trigger.name}," +
            "enforcementDisposition=${enforcementItem.enforcementDisposition.name}," +
            "rollbackDisposition=${disposition.name},enforcementReason=${enforcementItem.reason}"
    }
}

private fun String?.toCanaryRollbackEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
