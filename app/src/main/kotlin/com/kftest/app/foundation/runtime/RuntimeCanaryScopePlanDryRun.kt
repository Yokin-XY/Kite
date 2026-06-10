package com.kftest.app.foundation.runtime

enum class RuntimeCanaryScopePlanState {
    LOCKED,
    PRESSURE_WARMING,
    READY_LIMITED
}

enum class RuntimeCanaryScopePlanRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_ENTRY,
    WAIT_FOR_PRESSURE_WARMING,
    READY_FOR_LIMITED_SCOPE
}

enum class RuntimeCanaryScopeDisposition {
    ALLOW_DRY_RUN_CANARY,
    WAIT_FOR_WARMING_HOLD,
    BLOCKED_BY_ENTRY,
    BLOCKED_BY_READINESS,
    OUT_OF_SCOPE
}

data class RuntimeCanaryScopePlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val entryAllowed: Boolean,
    val readinessCanaryReady: Boolean,
    val disposition: RuntimeCanaryScopeDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryScopePlanDryRunSnapshot(
    val mode: String = "runtime_canary_scope_plan_dry_run_v0",
    val enforcementMode: String = "dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryScopePlanState = RuntimeCanaryScopePlanState.LOCKED,
    val recommendation: RuntimeCanaryScopePlanRecommendation =
        RuntimeCanaryScopePlanRecommendation.KEEP_SHADOW,
    val entryState: RuntimeCanaryEntryState = RuntimeCanaryEntryState.BLOCKED,
    val entryAllowed: Boolean = false,
    val entrySuggestedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val scopeAllowed: Boolean = false,
    val readinessState: RuntimeGovernanceReadinessState = RuntimeGovernanceReadinessState.SHADOW_ONLY,
    val pressureArmingState: RuntimePressureCanaryArmingState = RuntimePressureCanaryArmingState.BLOCKED,
    val pressureBlocker: String = "waiting_for_telemetry",
    val warmingHoldActive: Boolean = false,
    val minWarmingVisibleMs: Long = 0L,
    val eligibleCapabilityCount: Int = 0,
    val inScopeCapabilityCount: Int = 0,
    val allowedCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val simulatedActivationCount: Int = 0,
    val simulatedEnforcementCount: Int = 0,
    val reason: String = "waiting_for_canary_entry",
    val items: List<RuntimeCanaryScopePlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation scope=$plannedScope " +
            "scopeAllowed=$scopeAllowed entry=$entryState/${entryAllowed} " +
            "readiness=$readinessState pressure=$pressureArmingState/$pressureBlocker " +
            "warmingHold=$warmingHoldActive inScope=$inScopeCapabilityCount " +
            "allowed=$allowedCapabilityCount blocked=$blockedCapabilityCount " +
            "activation=$simulatedActivationCount enforcementActions=$simulatedEnforcementCount " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_scope_plan_mode=${mode.toCanaryScopeEnvValue()}")
            appendLine("canary_scope_plan_enforcement_mode=${enforcementMode.toCanaryScopeEnvValue()}")
            appendLine("canary_scope_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_scope_plan_generated_at=$generatedAtMs")
            appendLine("canary_scope_plan_state=${state.name}")
            appendLine("canary_scope_plan_recommendation=${recommendation.name}")
            appendLine("canary_scope_plan_entry_state=${entryState.name}")
            appendLine("canary_scope_plan_entry_allowed=$entryAllowed")
            appendLine("canary_scope_plan_entry_suggested_scope=${entrySuggestedScope.name}")
            appendLine("canary_scope_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_scope_plan_scope_allowed=$scopeAllowed")
            appendLine("canary_scope_plan_readiness_state=${readinessState.name}")
            appendLine("canary_scope_plan_pressure_arming_state=${pressureArmingState.name}")
            appendLine("canary_scope_plan_pressure_blocker=${pressureBlocker.toCanaryScopeEnvValue()}")
            appendLine("canary_scope_plan_warming_hold_active=$warmingHoldActive")
            appendLine("canary_scope_plan_min_warming_visible_ms=$minWarmingVisibleMs")
            appendLine("canary_scope_plan_eligible_capability_count=$eligibleCapabilityCount")
            appendLine("canary_scope_plan_in_scope_capability_count=$inScopeCapabilityCount")
            appendLine("canary_scope_plan_allowed_capability_count=$allowedCapabilityCount")
            appendLine("canary_scope_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_scope_plan_simulated_activation_count=$simulatedActivationCount")
            appendLine("canary_scope_plan_simulated_enforcement_count=$simulatedEnforcementCount")
            appendLine("canary_scope_plan_reason=${reason.toCanaryScopeEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_scope_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_entry_allowed=${item.entryAllowed}")
                appendLine("${prefix}_readiness_canary_ready=${item.readinessCanaryReady}")
                appendLine("${prefix}_disposition=${item.disposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryScopeEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryScopeEnvValue()}")
            }
            appendLine("canary_scope_plan_boundary=dry_run_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanaryScopePlanDryRun {
    fun evaluate(
        canaryEntry: RuntimeCanaryEntryPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryScopePlanDryRunSnapshot {
        val state = when {
            canaryEntry.entryAllowed -> RuntimeCanaryScopePlanState.READY_LIMITED
            canaryEntry.state == RuntimeCanaryEntryState.PRESSURE_WARMING ->
                RuntimeCanaryScopePlanState.PRESSURE_WARMING
            else -> RuntimeCanaryScopePlanState.LOCKED
        }
        val plannedScope = if (state == RuntimeCanaryScopePlanState.READY_LIMITED) {
            canaryEntry.suggestedScope
        } else {
            RuntimeCanaryScope.NONE
        }
        val scopeAllowed = state == RuntimeCanaryScopePlanState.READY_LIMITED &&
            plannedScope != RuntimeCanaryScope.NONE
        val items = canaryEntry.items.map { entryItem ->
            val inScope = scopeAllowed && entryItem.capability.inScopeFor(plannedScope)
            val disposition = dispositionFor(
                state = state,
                scopeAllowed = scopeAllowed,
                inScope = inScope,
                entryItem = entryItem
            )
            RuntimeCanaryScopePlanItem(
                capability = entryItem.capability,
                inScope = inScope,
                entryAllowed = entryItem.entryAllowed,
                readinessCanaryReady = entryItem.readinessCanaryReady,
                disposition = disposition,
                blocker = blockerFor(disposition, canaryEntry, entryItem),
                reason = buildItemReason(canaryEntry, entryItem, plannedScope, disposition)
            )
        }

        return RuntimeCanaryScopePlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state, scopeAllowed),
            entryState = canaryEntry.state,
            entryAllowed = canaryEntry.entryAllowed,
            entrySuggestedScope = canaryEntry.suggestedScope,
            plannedScope = plannedScope,
            scopeAllowed = scopeAllowed,
            readinessState = canaryEntry.readinessState,
            pressureArmingState = canaryEntry.pressureArmingState,
            pressureBlocker = canaryEntry.pressureBlocker,
            warmingHoldActive = canaryEntry.warmingHoldActive,
            minWarmingVisibleMs = canaryEntry.minWarmingVisibleMs,
            eligibleCapabilityCount = items.size,
            inScopeCapabilityCount = items.count { it.inScope },
            allowedCapabilityCount = items.count {
                it.disposition == RuntimeCanaryScopeDisposition.ALLOW_DRY_RUN_CANARY
            },
            blockedCapabilityCount = items.count {
                it.disposition != RuntimeCanaryScopeDisposition.ALLOW_DRY_RUN_CANARY
            },
            reason = buildReason(canaryEntry, state, plannedScope, scopeAllowed),
            items = items
        )
    }

    private fun RuntimeGovernanceReadinessCapability.inScopeFor(scope: RuntimeCanaryScope): Boolean {
        return when (scope) {
            RuntimeCanaryScope.NONE -> false
            RuntimeCanaryScope.QUEUE_ONLY -> this == RuntimeGovernanceReadinessCapability.TELEMETRY_HEALTH ||
                this == RuntimeGovernanceReadinessCapability.QUEUE_PLAN ||
                this == RuntimeGovernanceReadinessCapability.BUDGET_REVIEW ||
                this == RuntimeGovernanceReadinessCapability.CLEANUP_REVIEW
            RuntimeCanaryScope.LIMITED_GOVERNANCE -> true
        }
    }

    private fun dispositionFor(
        state: RuntimeCanaryScopePlanState,
        scopeAllowed: Boolean,
        inScope: Boolean,
        entryItem: RuntimeCanaryEntryItem
    ): RuntimeCanaryScopeDisposition {
        return when {
            state == RuntimeCanaryScopePlanState.PRESSURE_WARMING ->
                RuntimeCanaryScopeDisposition.WAIT_FOR_WARMING_HOLD
            !entryItem.readinessCanaryReady -> RuntimeCanaryScopeDisposition.BLOCKED_BY_READINESS
            !entryItem.entryAllowed || !scopeAllowed -> RuntimeCanaryScopeDisposition.BLOCKED_BY_ENTRY
            !inScope -> RuntimeCanaryScopeDisposition.OUT_OF_SCOPE
            else -> RuntimeCanaryScopeDisposition.ALLOW_DRY_RUN_CANARY
        }
    }

    private fun blockerFor(
        disposition: RuntimeCanaryScopeDisposition,
        canaryEntry: RuntimeCanaryEntryPlanDryRunSnapshot,
        entryItem: RuntimeCanaryEntryItem
    ): String {
        return when (disposition) {
            RuntimeCanaryScopeDisposition.ALLOW_DRY_RUN_CANARY -> "none"
            RuntimeCanaryScopeDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanaryScopeDisposition.BLOCKED_BY_READINESS -> entryItem.blocker
            RuntimeCanaryScopeDisposition.BLOCKED_BY_ENTRY ->
                canaryEntry.pressureBlocker.takeIf { it != "none" } ?: "entry_not_allowed"
            RuntimeCanaryScopeDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryScopePlanState,
        scopeAllowed: Boolean
    ): RuntimeCanaryScopePlanRecommendation {
        return when {
            scopeAllowed -> RuntimeCanaryScopePlanRecommendation.READY_FOR_LIMITED_SCOPE
            state == RuntimeCanaryScopePlanState.PRESSURE_WARMING ->
                RuntimeCanaryScopePlanRecommendation.WAIT_FOR_PRESSURE_WARMING
            state == RuntimeCanaryScopePlanState.LOCKED -> RuntimeCanaryScopePlanRecommendation.WAIT_FOR_ENTRY
            else -> RuntimeCanaryScopePlanRecommendation.KEEP_SHADOW
        }
    }

    private fun buildReason(
        canaryEntry: RuntimeCanaryEntryPlanDryRunSnapshot,
        state: RuntimeCanaryScopePlanState,
        plannedScope: RuntimeCanaryScope,
        scopeAllowed: Boolean
    ): String {
        return "state=${state.name},entry=${canaryEntry.state.name},entryAllowed=${canaryEntry.entryAllowed}," +
            "entryScope=${canaryEntry.suggestedScope.name},plannedScope=${plannedScope.name}," +
            "scopeAllowed=$scopeAllowed,pressureBlocker=${canaryEntry.pressureBlocker}," +
            "warmingHold=${canaryEntry.warmingHoldActive}"
    }

    private fun buildItemReason(
        canaryEntry: RuntimeCanaryEntryPlanDryRunSnapshot,
        entryItem: RuntimeCanaryEntryItem,
        plannedScope: RuntimeCanaryScope,
        disposition: RuntimeCanaryScopeDisposition
    ): String {
        return "entryState=${canaryEntry.state.name},plannedScope=${plannedScope.name}," +
            "entryAllowed=${entryItem.entryAllowed},readinessReady=${entryItem.readinessCanaryReady}," +
            "disposition=${disposition.name},entryReason=${entryItem.reason}"
    }
}

private fun String?.toCanaryScopeEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
