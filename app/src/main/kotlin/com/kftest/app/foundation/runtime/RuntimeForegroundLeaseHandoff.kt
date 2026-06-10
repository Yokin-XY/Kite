package com.kftest.app.foundation.runtime

enum class RuntimeForegroundLeaseHandoffState {
    ACTIVE_PROTECTED,
    INACTIVE_NO_RELIABLE_SIGNAL,
    COOLING_LEASE,
    LEASE_EXPIRED,
    NOT_FOREGROUND
}

data class RuntimeForegroundLeaseHandoffEntry(
    val rootKey: String,
    val unitId: String,
    val state: RuntimeForegroundLeaseHandoffState,
    val reliableSignal: Boolean,
    val remainingLeaseMs: Long,
    val reason: String
)

data class RuntimeForegroundLeaseHandoffSnapshot(
    val enabled: Boolean = true,
    val activeCount: Int = 0,
    val inactiveCount: Int = 0,
    val reliableSignalCount: Int = 0,
    val handoffToLeaseCount: Int = 0,
    val blockedCount: Int = 0,
    val lastReason: String = "not_evaluated",
    val entries: List<RuntimeForegroundLeaseHandoffEntry> = emptyList()
) {
    fun toEnvText(): String {
        return buildString {
            appendLine("runtime_lifecycle_foreground_handoff_enabled=$enabled")
            appendLine("runtime_lifecycle_foreground_handoff_active_count=$activeCount")
            appendLine("runtime_lifecycle_foreground_handoff_inactive_count=$inactiveCount")
            appendLine("runtime_lifecycle_foreground_handoff_reliable_signal_count=$reliableSignalCount")
            appendLine("runtime_lifecycle_foreground_handoff_to_lease_count=$handoffToLeaseCount")
            appendLine("runtime_lifecycle_foreground_handoff_blocked_count=$blockedCount")
            appendLine("runtime_lifecycle_foreground_handoff_last_reason=${lastReason.toForegroundHandoffEnvValue()}")
        }
    }
}

object RuntimeForegroundLeaseHandoff {
    fun evaluate(
        snapshot: RuntimeHealthSnapshot,
        strategy: RuntimeLifecycleDefaultStrategy = RuntimeLifecycleDefaultStrategy.standard()
    ): RuntimeForegroundLeaseHandoffSnapshot {
        if (!strategy.foregroundDowngradeSupported) {
            return RuntimeForegroundLeaseHandoffSnapshot(
                enabled = false,
                lastReason = "foreground_downgrade_disabled_by_default_strategy"
            )
        }
        val authorityByRoot = snapshot.systemProcessLifecycle.authorityMatrix.entries
            .associateBy { it.rootKey }
        val entries = snapshot.roots
            .filter { root ->
                authorityByRoot[root.ownershipKey]?.effectiveTier == RuntimeLifecycleAuthorityTier.FOREGROUND ||
                    root.processUnitTier == RuntimeProcessUnitTier.FOREGROUND
            }
            .map { root ->
                evaluateRoot(root, snapshot)
            }
        return RuntimeForegroundLeaseHandoffSnapshot(
            enabled = true,
            activeCount = entries.count { it.state == RuntimeForegroundLeaseHandoffState.ACTIVE_PROTECTED },
            inactiveCount = entries.count {
                it.state == RuntimeForegroundLeaseHandoffState.INACTIVE_NO_RELIABLE_SIGNAL ||
                    it.state == RuntimeForegroundLeaseHandoffState.COOLING_LEASE ||
                    it.state == RuntimeForegroundLeaseHandoffState.LEASE_EXPIRED
            },
            reliableSignalCount = entries.count { it.reliableSignal },
            handoffToLeaseCount = entries.count {
                it.state == RuntimeForegroundLeaseHandoffState.COOLING_LEASE ||
                    it.state == RuntimeForegroundLeaseHandoffState.LEASE_EXPIRED
            },
            blockedCount = entries.count {
                it.state == RuntimeForegroundLeaseHandoffState.ACTIVE_PROTECTED ||
                    it.state == RuntimeForegroundLeaseHandoffState.INACTIVE_NO_RELIABLE_SIGNAL
            },
            lastReason = entries.lastOrNull()?.reason ?: "no_foreground_runtime",
            entries = entries
        )
    }

    private fun evaluateRoot(
        root: RuntimeRootSnapshot,
        snapshot: RuntimeHealthSnapshot
    ): RuntimeForegroundLeaseHandoffEntry {
        val unitId = root.processUnitId ?: root.ownerId ?: root.ownershipKey
        if (root.isRunning && snapshot.backgroundDecay.lifecycleState == RuntimeAppVisibilityState.FOREGROUND) {
            return entry(
                root = root,
                unitId = unitId,
                state = RuntimeForegroundLeaseHandoffState.ACTIVE_PROTECTED,
                reliableSignal = false,
                remainingLeaseMs = Long.MAX_VALUE,
                reason = "foreground_active_protected"
            )
        }
        val reliableInactiveSignal = snapshot.backgroundDecay.lifecycleState != RuntimeAppVisibilityState.FOREGROUND &&
            snapshot.backgroundDecay.foregroundActivityCount == 0 &&
            snapshot.backgroundDecay.backgroundAgeMs >= snapshot.backgroundDecay.policyGraceMs.coerceAtLeast(0L) &&
            !root.isActiveOwner
        if (!reliableInactiveSignal) {
            return entry(
                root = root,
                unitId = unitId,
                state = RuntimeForegroundLeaseHandoffState.INACTIVE_NO_RELIABLE_SIGNAL,
                reliableSignal = false,
                remainingLeaseMs = Long.MAX_VALUE,
                reason = "foreground_inactive_no_reliable_lease_downgrade_signal"
            )
        }
        val coolingTtl = snapshot.lifecycleReclaimPlan.policyLeaseCoolingTtlMs
            .takeIf { it > 0L }
            ?: snapshot.backgroundDecay.policyTransientCleanupMs
                .minus(snapshot.backgroundDecay.policyGraceMs)
                .coerceAtLeast(0L)
        val handoffAge = (
            snapshot.backgroundDecay.backgroundAgeMs -
                snapshot.backgroundDecay.policyGraceMs.coerceAtLeast(0L)
            ).coerceAtLeast(0L)
        val remaining = (coolingTtl - handoffAge).coerceAtLeast(0L)
        return entry(
            root = root,
            unitId = unitId,
            state = if (remaining <= 0L) {
                RuntimeForegroundLeaseHandoffState.LEASE_EXPIRED
            } else {
                RuntimeForegroundLeaseHandoffState.COOLING_LEASE
            },
            reliableSignal = true,
            remainingLeaseMs = remaining,
            reason = if (remaining <= 0L) {
                "foreground_inactive_reliable_signal_lease_expired"
            } else {
                "foreground_inactive_reliable_signal_cooling_lease"
            }
        )
    }

    private fun entry(
        root: RuntimeRootSnapshot,
        unitId: String,
        state: RuntimeForegroundLeaseHandoffState,
        reliableSignal: Boolean,
        remainingLeaseMs: Long,
        reason: String
    ): RuntimeForegroundLeaseHandoffEntry {
        return RuntimeForegroundLeaseHandoffEntry(
            rootKey = root.ownershipKey,
            unitId = unitId,
            state = state,
            reliableSignal = reliableSignal,
            remainingLeaseMs = remainingLeaseMs,
            reason = reason
        )
    }
}

private fun String?.toForegroundHandoffEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
