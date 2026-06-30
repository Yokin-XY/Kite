package com.kite.app.foundation.runtime

enum class RuntimeCanaryActivationState {
    LOCKED,
    WAITING_FOR_WARMING,
    MANUAL_READY
}

enum class RuntimeCanaryActivationRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_SCOPE_READY,
    WAIT_FOR_PRESSURE_WARMING,
    READY_FOR_MANUAL_LIMITED_CANARY
}

enum class RuntimeCanaryActivationManualAction {
    NONE,
    REVIEW_LIMITED_GOVERNANCE_CANARY
}

enum class RuntimeCanaryActivationDisposition {
    WOULD_ARM_DRY_RUN_CANARY,
    WAIT_FOR_WARMING_HOLD,
    BLOCKED_BY_SCOPE,
    BLOCKED_BY_READINESS,
    OUT_OF_SCOPE
}

data class RuntimeCanaryActivationPlanItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val inScope: Boolean,
    val scopeDisposition: RuntimeCanaryScopeDisposition,
    val activationDisposition: RuntimeCanaryActivationDisposition,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryActivationPlanDryRunSnapshot(
    val mode: String = "runtime_canary_activation_plan_dry_run_v0",
    val enforcementMode: String = "manual_only_dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryActivationState = RuntimeCanaryActivationState.LOCKED,
    val recommendation: RuntimeCanaryActivationRecommendation =
        RuntimeCanaryActivationRecommendation.KEEP_SHADOW,
    val manualAction: RuntimeCanaryActivationManualAction =
        RuntimeCanaryActivationManualAction.NONE,
    val manualReady: Boolean = false,
    val manualActionRequired: Boolean = false,
    val autoActivationAllowed: Boolean = false,
    val activationAllowed: Boolean = false,
    val scopePlanState: RuntimeCanaryScopePlanState = RuntimeCanaryScopePlanState.LOCKED,
    val scopeAllowed: Boolean = false,
    val plannedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val pressureArmingState: RuntimePressureCanaryArmingState =
        RuntimePressureCanaryArmingState.BLOCKED,
    val pressureBlocker: String = "waiting_for_telemetry",
    val warmingHoldActive: Boolean = false,
    val minWaitingVisibleMs: Long = 0L,
    val waitingHoldActive: Boolean = false,
    val waitingVisibleUntilMs: Long = 0L,
    val waitingHoldRemainingMs: Long = 0L,
    val eligibleCapabilityCount: Int = 0,
    val wouldArmCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val actualActivationCount: Int = 0,
    val actualEnforcementCount: Int = 0,
    val reason: String = "waiting_for_scope_plan",
    val items: List<RuntimeCanaryActivationPlanItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation manualReady=$manualReady " +
            "manualAction=$manualAction actionRequired=$manualActionRequired scope=$plannedScope " +
            "scopeAllowed=$scopeAllowed pressure=$pressureArmingState/$pressureBlocker " +
            "warmingHold=$warmingHoldActive waitingHold=$waitingHoldActive " +
            "remaining=${waitingHoldRemainingMs}ms wouldArm=$wouldArmCapabilityCount " +
            "blocked=$blockedCapabilityCount actualActivation=$actualActivationCount " +
            "actualEnforcement=$actualEnforcementCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_activation_plan_mode=${mode.toCanaryActivationEnvValue()}")
            appendLine("canary_activation_plan_enforcement_mode=${enforcementMode.toCanaryActivationEnvValue()}")
            appendLine("canary_activation_plan_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_activation_plan_generated_at=$generatedAtMs")
            appendLine("canary_activation_plan_state=${state.name}")
            appendLine("canary_activation_plan_recommendation=${recommendation.name}")
            appendLine("canary_activation_plan_manual_action=${manualAction.name}")
            appendLine("canary_activation_plan_manual_ready=$manualReady")
            appendLine("canary_activation_plan_manual_action_required=$manualActionRequired")
            appendLine("canary_activation_plan_auto_activation_allowed=$autoActivationAllowed")
            appendLine("canary_activation_plan_activation_allowed=$activationAllowed")
            appendLine("canary_activation_plan_scope_plan_state=${scopePlanState.name}")
            appendLine("canary_activation_plan_scope_allowed=$scopeAllowed")
            appendLine("canary_activation_plan_planned_scope=${plannedScope.name}")
            appendLine("canary_activation_plan_pressure_arming_state=${pressureArmingState.name}")
            appendLine("canary_activation_plan_pressure_blocker=${pressureBlocker.toCanaryActivationEnvValue()}")
            appendLine("canary_activation_plan_warming_hold_active=$warmingHoldActive")
            appendLine("canary_activation_plan_min_waiting_visible_ms=$minWaitingVisibleMs")
            appendLine("canary_activation_plan_waiting_hold_active=$waitingHoldActive")
            appendLine("canary_activation_plan_waiting_visible_until=$waitingVisibleUntilMs")
            appendLine("canary_activation_plan_waiting_hold_remaining_ms=$waitingHoldRemainingMs")
            appendLine("canary_activation_plan_eligible_capability_count=$eligibleCapabilityCount")
            appendLine("canary_activation_plan_would_arm_capability_count=$wouldArmCapabilityCount")
            appendLine("canary_activation_plan_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_activation_plan_actual_activation_count=$actualActivationCount")
            appendLine("canary_activation_plan_actual_enforcement_count=$actualEnforcementCount")
            appendLine("canary_activation_plan_reason=${reason.toCanaryActivationEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_activation_plan_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_in_scope=${item.inScope}")
                appendLine("${prefix}_scope_disposition=${item.scopeDisposition.name}")
                appendLine("${prefix}_activation_disposition=${item.activationDisposition.name}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryActivationEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryActivationEnvValue()}")
            }
            appendLine("canary_activation_plan_boundary=dry_run_manual_only_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanaryActivationPlanDryRun {
    private const val MIN_WAITING_VISIBLE_MS = 5_000L

    private val lock = Any()
    private var waitingVisibleUntilMs: Long = 0L
    private var lastScopePlanState: RuntimeCanaryScopePlanState = RuntimeCanaryScopePlanState.LOCKED

    fun evaluate(
        scopePlan: RuntimeCanaryScopePlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryActivationPlanDryRunSnapshot {
        val waitingHold = updateWaitingHold(scopePlan, now)
        val state = when {
            waitingHold.active -> RuntimeCanaryActivationState.WAITING_FOR_WARMING
            scopePlan.scopeAllowed -> RuntimeCanaryActivationState.MANUAL_READY
            scopePlan.state == RuntimeCanaryScopePlanState.PRESSURE_WARMING ->
                RuntimeCanaryActivationState.WAITING_FOR_WARMING
            else -> RuntimeCanaryActivationState.LOCKED
        }
        val manualReady = state == RuntimeCanaryActivationState.MANUAL_READY
        val items = scopePlan.items.map { scopeItem ->
            val disposition = activationDispositionFor(state, scopePlan, scopeItem)
            RuntimeCanaryActivationPlanItem(
                capability = scopeItem.capability,
                inScope = scopeItem.inScope,
                scopeDisposition = scopeItem.disposition,
                activationDisposition = disposition,
                blocker = activationBlockerFor(disposition, scopeItem, scopePlan),
                reason = buildItemReason(scopePlan, scopeItem, disposition)
            )
        }

        return RuntimeCanaryActivationPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            manualAction = if (manualReady) {
                RuntimeCanaryActivationManualAction.REVIEW_LIMITED_GOVERNANCE_CANARY
            } else {
                RuntimeCanaryActivationManualAction.NONE
            },
            manualReady = manualReady,
            manualActionRequired = manualReady,
            autoActivationAllowed = false,
            activationAllowed = false,
            scopePlanState = scopePlan.state,
            scopeAllowed = scopePlan.scopeAllowed,
            plannedScope = scopePlan.plannedScope,
            pressureArmingState = scopePlan.pressureArmingState,
            pressureBlocker = scopePlan.pressureBlocker,
            warmingHoldActive = scopePlan.warmingHoldActive,
            minWaitingVisibleMs = MIN_WAITING_VISIBLE_MS,
            waitingHoldActive = waitingHold.active,
            waitingVisibleUntilMs = waitingHold.visibleUntilMs,
            waitingHoldRemainingMs = waitingHold.remainingMs,
            eligibleCapabilityCount = items.size,
            wouldArmCapabilityCount = items.count {
                it.activationDisposition == RuntimeCanaryActivationDisposition.WOULD_ARM_DRY_RUN_CANARY
            },
            blockedCapabilityCount = items.count {
                it.activationDisposition != RuntimeCanaryActivationDisposition.WOULD_ARM_DRY_RUN_CANARY
            },
            actualActivationCount = 0,
            actualEnforcementCount = 0,
            reason = buildReason(scopePlan, state, manualReady, waitingHold),
            items = items
        )
    }

    private fun updateWaitingHold(
        scopePlan: RuntimeCanaryScopePlanDryRunSnapshot,
        now: Long
    ): WaitingHold {
        return synchronized(lock) {
            if (scopePlan.state == RuntimeCanaryScopePlanState.LOCKED) {
                waitingVisibleUntilMs = 0L
            } else {
                val enteredWarming = scopePlan.state == RuntimeCanaryScopePlanState.PRESSURE_WARMING &&
                    lastScopePlanState != RuntimeCanaryScopePlanState.PRESSURE_WARMING
                val recentlyLeftWarming = scopePlan.state == RuntimeCanaryScopePlanState.READY_LIMITED &&
                    lastScopePlanState == RuntimeCanaryScopePlanState.PRESSURE_WARMING
                if (enteredWarming || recentlyLeftWarming) {
                    waitingVisibleUntilMs = maxOf(waitingVisibleUntilMs, now + MIN_WAITING_VISIBLE_MS)
                }
            }

            val active = scopePlan.state == RuntimeCanaryScopePlanState.PRESSURE_WARMING ||
                (scopePlan.state == RuntimeCanaryScopePlanState.READY_LIMITED && waitingVisibleUntilMs > now)
            val remaining = if (active) {
                (waitingVisibleUntilMs - now).coerceAtLeast(0L)
            } else {
                0L
            }
            lastScopePlanState = scopePlan.state
            WaitingHold(
                active = active,
                visibleUntilMs = waitingVisibleUntilMs,
                remainingMs = remaining
            )
        }
    }

    private fun activationDispositionFor(
        state: RuntimeCanaryActivationState,
        scopePlan: RuntimeCanaryScopePlanDryRunSnapshot,
        scopeItem: RuntimeCanaryScopePlanItem
    ): RuntimeCanaryActivationDisposition {
        return when {
            state == RuntimeCanaryActivationState.WAITING_FOR_WARMING ->
                RuntimeCanaryActivationDisposition.WAIT_FOR_WARMING_HOLD
            scopeItem.disposition == RuntimeCanaryScopeDisposition.BLOCKED_BY_READINESS ->
                RuntimeCanaryActivationDisposition.BLOCKED_BY_READINESS
            scopeItem.disposition == RuntimeCanaryScopeDisposition.OUT_OF_SCOPE ->
                RuntimeCanaryActivationDisposition.OUT_OF_SCOPE
            !scopePlan.scopeAllowed || !scopeItem.inScope ->
                RuntimeCanaryActivationDisposition.BLOCKED_BY_SCOPE
            else -> RuntimeCanaryActivationDisposition.WOULD_ARM_DRY_RUN_CANARY
        }
    }

    private fun activationBlockerFor(
        disposition: RuntimeCanaryActivationDisposition,
        scopeItem: RuntimeCanaryScopePlanItem,
        scopePlan: RuntimeCanaryScopePlanDryRunSnapshot
    ): String {
        return when (disposition) {
            RuntimeCanaryActivationDisposition.WOULD_ARM_DRY_RUN_CANARY -> "none"
            RuntimeCanaryActivationDisposition.WAIT_FOR_WARMING_HOLD -> "stability_window_warming"
            RuntimeCanaryActivationDisposition.BLOCKED_BY_SCOPE ->
                scopeItem.blocker.takeIf { it != "none" }
                    ?: scopePlan.pressureBlocker.takeIf { it != "none" }
                    ?: "scope_not_allowed"
            RuntimeCanaryActivationDisposition.BLOCKED_BY_READINESS -> scopeItem.blocker
            RuntimeCanaryActivationDisposition.OUT_OF_SCOPE -> "scope_not_included"
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryActivationState
    ): RuntimeCanaryActivationRecommendation {
        return when (state) {
            RuntimeCanaryActivationState.MANUAL_READY ->
                RuntimeCanaryActivationRecommendation.READY_FOR_MANUAL_LIMITED_CANARY
            RuntimeCanaryActivationState.WAITING_FOR_WARMING ->
                RuntimeCanaryActivationRecommendation.WAIT_FOR_PRESSURE_WARMING
            RuntimeCanaryActivationState.LOCKED ->
                RuntimeCanaryActivationRecommendation.WAIT_FOR_SCOPE_READY
        }
    }

    private fun buildReason(
        scopePlan: RuntimeCanaryScopePlanDryRunSnapshot,
        state: RuntimeCanaryActivationState,
        manualReady: Boolean,
        waitingHold: WaitingHold
    ): String {
        return "state=${state.name},manualReady=$manualReady,scopeState=${scopePlan.state.name}," +
            "scopeAllowed=${scopePlan.scopeAllowed},plannedScope=${scopePlan.plannedScope.name}," +
            "pressureBlocker=${scopePlan.pressureBlocker},warmingHold=${scopePlan.warmingHoldActive}," +
            "waitingHold=${waitingHold.active},waitingRemainingMs=${waitingHold.remainingMs}"
    }

    private fun buildItemReason(
        scopePlan: RuntimeCanaryScopePlanDryRunSnapshot,
        scopeItem: RuntimeCanaryScopePlanItem,
        disposition: RuntimeCanaryActivationDisposition
    ): String {
        return "scopeState=${scopePlan.state.name},plannedScope=${scopePlan.plannedScope.name}," +
            "scopeDisposition=${scopeItem.disposition.name},activationDisposition=${disposition.name}," +
            "scopeReason=${scopeItem.reason}"
    }

    private data class WaitingHold(
        val active: Boolean,
        val visibleUntilMs: Long,
        val remainingMs: Long
    )
}

private fun String?.toCanaryActivationEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
