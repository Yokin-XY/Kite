package com.kite.app.foundation.runtime

enum class RuntimeCanaryEntryState {
    BLOCKED,
    PRESSURE_WARMING,
    READY_LIMITED
}

enum class RuntimeCanaryEntryRecommendation {
    KEEP_SHADOW,
    WAIT_FOR_PRESSURE_STABILITY,
    REVIEW_READINESS_BLOCKERS,
    READY_FOR_LIMITED_CANARY
}

enum class RuntimeCanaryScope {
    NONE,
    QUEUE_ONLY,
    LIMITED_GOVERNANCE
}

data class RuntimeCanaryEntryItem(
    val capability: RuntimeGovernanceReadinessCapability,
    val readinessCanaryReady: Boolean,
    val entryAllowed: Boolean,
    val blocker: String,
    val reason: String
)

data class RuntimeCanaryEntryPlanDryRunSnapshot(
    val mode: String = "runtime_canary_entry_plan_dry_run_v0",
    val enforcementMode: String = "dry_run",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeCanaryEntryState = RuntimeCanaryEntryState.BLOCKED,
    val recommendation: RuntimeCanaryEntryRecommendation = RuntimeCanaryEntryRecommendation.KEEP_SHADOW,
    val entryAllowed: Boolean = false,
    val suggestedScope: RuntimeCanaryScope = RuntimeCanaryScope.NONE,
    val readinessState: RuntimeGovernanceReadinessState = RuntimeGovernanceReadinessState.SHADOW_ONLY,
    val readinessCanaryReadyCount: Int = 0,
    val readinessBlockedCount: Int = 0,
    val pressureArmingState: RuntimePressureCanaryArmingState = RuntimePressureCanaryArmingState.BLOCKED,
    val pressureStableForCanary: Boolean = false,
    val pressureBlocker: String = "waiting_for_telemetry",
    val minWarmingVisibleMs: Long = 0L,
    val warmingHoldActive: Boolean = false,
    val warmingVisibleUntilMs: Long = 0L,
    val warmingHoldRemainingMs: Long = 0L,
    val telemetryHealthy: Boolean = false,
    val eligibleCapabilityCount: Int = 0,
    val allowedCapabilityCount: Int = 0,
    val blockedCapabilityCount: Int = 0,
    val reason: String = "waiting_for_readiness",
    val items: List<RuntimeCanaryEntryItem> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation entryAllowed=$entryAllowed " +
            "scope=$suggestedScope readiness=$readinessState canaryReady=$readinessCanaryReadyCount " +
            "blocked=$readinessBlockedCount pressure=$pressureArmingState/$pressureBlocker " +
            "warmingHold=$warmingHoldActive remaining=${warmingHoldRemainingMs}ms " +
            "allowed=$allowedCapabilityCount blockedCaps=$blockedCapabilityCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("canary_entry_mode=${mode.toCanaryEntryEnvValue()}")
            appendLine("canary_entry_enforcement_mode=${enforcementMode.toCanaryEntryEnvValue()}")
            appendLine("canary_entry_enforcement_enabled=$enforcementEnabled")
            appendLine("canary_entry_generated_at=$generatedAtMs")
            appendLine("canary_entry_state=${state.name}")
            appendLine("canary_entry_recommendation=${recommendation.name}")
            appendLine("canary_entry_allowed=$entryAllowed")
            appendLine("canary_entry_suggested_scope=${suggestedScope.name}")
            appendLine("canary_entry_readiness_state=${readinessState.name}")
            appendLine("canary_entry_readiness_canary_ready_count=$readinessCanaryReadyCount")
            appendLine("canary_entry_readiness_blocked_count=$readinessBlockedCount")
            appendLine("canary_entry_pressure_arming_state=${pressureArmingState.name}")
            appendLine("canary_entry_pressure_stable_for_canary=$pressureStableForCanary")
            appendLine("canary_entry_pressure_blocker=${pressureBlocker.toCanaryEntryEnvValue()}")
            appendLine("canary_entry_min_warming_visible_ms=$minWarmingVisibleMs")
            appendLine("canary_entry_warming_hold_active=$warmingHoldActive")
            appendLine("canary_entry_warming_visible_until=$warmingVisibleUntilMs")
            appendLine("canary_entry_warming_hold_remaining_ms=$warmingHoldRemainingMs")
            appendLine("canary_entry_telemetry_healthy=$telemetryHealthy")
            appendLine("canary_entry_eligible_capability_count=$eligibleCapabilityCount")
            appendLine("canary_entry_allowed_capability_count=$allowedCapabilityCount")
            appendLine("canary_entry_blocked_capability_count=$blockedCapabilityCount")
            appendLine("canary_entry_reason=${reason.toCanaryEntryEnvValue()}")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "canary_entry_item_${index + 1}"
                appendLine("${prefix}_capability=${item.capability.name}")
                appendLine("${prefix}_readiness_canary_ready=${item.readinessCanaryReady}")
                appendLine("${prefix}_entry_allowed=${item.entryAllowed}")
                appendLine("${prefix}_blocker=${item.blocker.toCanaryEntryEnvValue()}")
                appendLine("${prefix}_reason=${item.reason.toCanaryEntryEnvValue()}")
            }
            appendLine("canary_entry_boundary=dry_run_no_activation_no_enforcement_no_queue_creation_no_start_no_defer_no_reclaim_no_restart_no_terminate_no_lane_control")
        }
    }
}

object RuntimeCanaryEntryPlanDryRun {
    private const val MIN_WARMING_VISIBLE_MS = 5_000L

    private val lock = Any()
    private var lastPressureArmingState: RuntimePressureCanaryArmingState =
        RuntimePressureCanaryArmingState.BLOCKED
    private var warmingVisibleUntilMs: Long = 0L

    fun evaluate(
        readiness: RuntimeGovernanceReadinessGateDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeCanaryEntryPlanDryRunSnapshot {
        val warmingHold = updateWarmingHold(readiness, pressureStability, now)
        val pressureBlocker = pressureBlocker(readiness, pressureStability, warmingHold.active)
        val entryAllowed = readiness.state == RuntimeGovernanceReadinessState.CANARY_READY &&
            pressureStability.canaryArmingState == RuntimePressureCanaryArmingState.ARMED &&
            pressureStability.canaryStable &&
            pressureBlocker == "none" &&
            !warmingHold.active
        val state = when {
            entryAllowed -> RuntimeCanaryEntryState.READY_LIMITED
            warmingHold.active -> RuntimeCanaryEntryState.PRESSURE_WARMING
            else -> RuntimeCanaryEntryState.BLOCKED
        }
        val items = readiness.checks.map { check ->
            val capabilityAllowed = entryAllowed && check.canaryReady
            RuntimeCanaryEntryItem(
                capability = check.capability,
                readinessCanaryReady = check.canaryReady,
                entryAllowed = capabilityAllowed,
                blocker = when {
                    capabilityAllowed -> "none"
                    !check.canaryReady -> check.blocker
                    pressureBlocker != "none" -> pressureBlocker
                    else -> "entry_not_allowed"
                },
                reason = check.reason
            )
        }
        return RuntimeCanaryEntryPlanDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state, readiness),
            entryAllowed = entryAllowed,
            suggestedScope = suggestedScope(entryAllowed, items),
            readinessState = readiness.state,
            readinessCanaryReadyCount = readiness.canaryReadyCount,
            readinessBlockedCount = readiness.blockedCount,
            pressureArmingState = pressureStability.canaryArmingState,
            pressureStableForCanary = pressureStability.canaryStable,
            pressureBlocker = pressureBlocker,
            minWarmingVisibleMs = MIN_WARMING_VISIBLE_MS,
            warmingHoldActive = warmingHold.active,
            warmingVisibleUntilMs = warmingHold.visibleUntilMs,
            warmingHoldRemainingMs = warmingHold.remainingMs,
            telemetryHealthy = readiness.telemetryHealthy,
            eligibleCapabilityCount = readiness.checks.size,
            allowedCapabilityCount = items.count { it.entryAllowed },
            blockedCapabilityCount = items.count { !it.entryAllowed },
            reason = buildReason(state, readiness, pressureStability, pressureBlocker, warmingHold),
            items = items
        )
    }

    private fun pressureBlocker(
        readiness: RuntimeGovernanceReadinessGateDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        warmingHoldActive: Boolean
    ): String {
        if (!readiness.telemetryHealthy) {
            return "telemetry_not_ready"
        }
        if (warmingHoldActive) {
            return "stability_window_warming"
        }
        if (pressureStability.canaryArmingState == RuntimePressureCanaryArmingState.ARMED &&
            pressureStability.canaryStable
        ) {
            return "none"
        }
        if (pressureStability.canaryArmingState == RuntimePressureCanaryArmingState.WARMING) {
            return "stability_window_warming"
        }
        return pressureStability.blocker.takeIf { it != "none" } ?: "pressure_stability_not_armed"
    }

    private fun updateWarmingHold(
        readiness: RuntimeGovernanceReadinessGateDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        now: Long
    ): WarmingHold {
        return synchronized(lock) {
            val armingState = pressureStability.canaryArmingState
            val clearHold = !readiness.telemetryHealthy ||
                armingState == RuntimePressureCanaryArmingState.BLOCKED
            if (clearHold) {
                warmingVisibleUntilMs = 0L
            } else {
                val enteredWarming = armingState == RuntimePressureCanaryArmingState.WARMING &&
                    lastPressureArmingState != RuntimePressureCanaryArmingState.WARMING
                val recentlyArmed = armingState == RuntimePressureCanaryArmingState.ARMED &&
                    lastPressureArmingState != RuntimePressureCanaryArmingState.ARMED &&
                    pressureStability.lastArmingTransitionAtMs > 0L &&
                    now - pressureStability.lastArmingTransitionAtMs <= MIN_WARMING_VISIBLE_MS
                if (enteredWarming || recentlyArmed) {
                    warmingVisibleUntilMs = maxOf(warmingVisibleUntilMs, now + MIN_WARMING_VISIBLE_MS)
                }
            }

            val active = readiness.telemetryHealthy &&
                (armingState == RuntimePressureCanaryArmingState.WARMING ||
                    (armingState == RuntimePressureCanaryArmingState.ARMED && warmingVisibleUntilMs > now))
            val remaining = if (active) {
                (warmingVisibleUntilMs - now).coerceAtLeast(0L)
            } else {
                0L
            }
            lastPressureArmingState = armingState
            WarmingHold(
                active = active,
                visibleUntilMs = warmingVisibleUntilMs,
                remainingMs = remaining
            )
        }
    }

    private fun recommendationFor(
        state: RuntimeCanaryEntryState,
        readiness: RuntimeGovernanceReadinessGateDryRunSnapshot
    ): RuntimeCanaryEntryRecommendation {
        return when (state) {
            RuntimeCanaryEntryState.READY_LIMITED -> RuntimeCanaryEntryRecommendation.READY_FOR_LIMITED_CANARY
            RuntimeCanaryEntryState.PRESSURE_WARMING ->
                RuntimeCanaryEntryRecommendation.WAIT_FOR_PRESSURE_STABILITY
            RuntimeCanaryEntryState.BLOCKED -> {
                if (readiness.blockedCount > 0) {
                    RuntimeCanaryEntryRecommendation.REVIEW_READINESS_BLOCKERS
                } else {
                    RuntimeCanaryEntryRecommendation.KEEP_SHADOW
                }
            }
        }
    }

    private fun suggestedScope(
        entryAllowed: Boolean,
        items: List<RuntimeCanaryEntryItem>
    ): RuntimeCanaryScope {
        if (!entryAllowed) return RuntimeCanaryScope.NONE
        val allowed = items.filter { it.entryAllowed }.map { it.capability }.toSet()
        return if (
            RuntimeGovernanceReadinessCapability.START_HOLD in allowed &&
            RuntimeGovernanceReadinessCapability.LANE_LIMIT in allowed
        ) {
            RuntimeCanaryScope.LIMITED_GOVERNANCE
        } else {
            RuntimeCanaryScope.QUEUE_ONLY
        }
    }

    private fun buildReason(
        state: RuntimeCanaryEntryState,
        readiness: RuntimeGovernanceReadinessGateDryRunSnapshot,
        pressureStability: RuntimePressureStabilityGateDryRunSnapshot,
        pressureBlocker: String,
        warmingHold: WarmingHold
    ): String {
        return "state=${state.name},readiness=${readiness.state.name}," +
            "readinessReady=${readiness.canaryReadyCount},readinessBlocked=${readiness.blockedCount}," +
            "pressureArming=${pressureStability.canaryArmingState.name}," +
            "pressureStable=${pressureStability.canaryStable},pressureBlocker=$pressureBlocker," +
            "warmingHold=${warmingHold.active},warmingRemainingMs=${warmingHold.remainingMs}"
    }

    private data class WarmingHold(
        val active: Boolean,
        val visibleUntilMs: Long,
        val remainingMs: Long
    )
}

private fun String?.toCanaryEntryEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(220)
}
