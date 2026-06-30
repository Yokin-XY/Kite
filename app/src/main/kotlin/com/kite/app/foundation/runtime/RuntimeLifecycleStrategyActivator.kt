package com.kite.app.foundation.runtime

import android.content.Context
import java.util.LinkedHashMap

enum class RuntimeLifecycleStrategyActivationMode {
    LEASE_RECLAIM_ONLY
}

enum class RuntimeLifecycleStrategyActivationResult {
    EXECUTED,
    SKIPPED,
    BLOCKED,
    FAILED
}

data class RuntimeLifecycleStrategyActivationConfig(
    val enabled: Boolean = true,
    val mode: RuntimeLifecycleStrategyActivationMode =
        RuntimeLifecycleStrategyActivationMode.LEASE_RECLAIM_ONLY,
    val defaultStrategy: RuntimeLifecycleDefaultStrategy =
        RuntimeLifecycleDefaultStrategy.standard(),
    val maxActionsPerTick: Int = 1,
    val minCandidateAgeMs: Long = 0L,
    val requireNonAmbiguousMatch: Boolean = true,
    val requireReclaimerBoundary: Boolean = true,
    val allowUserLocked: Boolean = false,
    val allowCore: Boolean = false,
    val allowProotCore: Boolean = false,
    val allowForegroundActive: Boolean = false,
    val allowUnmanaged: Boolean = false,
    val failureCooldownMs: Long = 60_000L
)

data class RuntimeLifecycleStrategyActivationRecord(
    val unitId: String,
    val action: String = "lease_reclaim",
    val result: RuntimeLifecycleStrategyActivationResult,
    val reason: String,
    val timestampMs: Long,
    val executor: String = "RuntimeReclaimer",
    val actionStateAfter: String = "unchanged",
    val resourceEpisodeAfter: String = "unchanged",
    val userNoticeAfter: String = "unchanged"
)

data class RuntimeLifecycleStrategyActivationSnapshot(
    val enabled: Boolean = RuntimeLifecycleStrategyActivationConfig().enabled,
    val mode: RuntimeLifecycleStrategyActivationMode =
        RuntimeLifecycleStrategyActivationMode.LEASE_RECLAIM_ONLY,
    val defaultStrategy: RuntimeLifecycleDefaultStrategy =
        RuntimeLifecycleDefaultStrategy.standard(),
    val foregroundHandoff: RuntimeForegroundLeaseHandoffSnapshot =
        RuntimeForegroundLeaseHandoffSnapshot(),
    val maxActionsPerTick: Int = RuntimeLifecycleStrategyActivationConfig().maxActionsPerTick,
    val minCandidateAgeMs: Long = RuntimeLifecycleStrategyActivationConfig().minCandidateAgeMs,
    val requireNonAmbiguousMatch: Boolean = true,
    val requireReclaimerBoundary: Boolean = true,
    val allowUserLocked: Boolean = false,
    val allowCore: Boolean = false,
    val allowProotCore: Boolean = false,
    val allowForegroundActive: Boolean = false,
    val allowUnmanaged: Boolean = false,
    val lastActionAtMs: Long = 0L,
    val lastUnitId: String = "none",
    val lastResult: RuntimeLifecycleStrategyActivationResult =
        RuntimeLifecycleStrategyActivationResult.SKIPPED,
    val lastReason: String = "not_evaluated",
    val executedCount: Int = 0,
    val skippedCount: Int = 0,
    val blockedCount: Int = 0,
    val failedCount: Int = 0,
    val records: List<RuntimeLifecycleStrategyActivationRecord> = emptyList()
) {
    fun toEnvText(maxRecords: Int = 5): String {
        return buildString {
            appendLine("runtime_lifecycle_lease_reclaim_executor_enabled=$enabled")
            appendLine("runtime_lifecycle_lease_reclaim_executor_mode=${mode.name.lowercase()}")
            appendLine("runtime_lifecycle_lease_reclaim_executor_max_actions_per_tick=$maxActionsPerTick")
            appendLine("runtime_lifecycle_lease_reclaim_executor_min_candidate_age_ms=$minCandidateAgeMs")
            appendLine("runtime_lifecycle_lease_reclaim_executor_require_non_ambiguous_match=$requireNonAmbiguousMatch")
            appendLine("runtime_lifecycle_lease_reclaim_executor_require_reclaimer_boundary=$requireReclaimerBoundary")
            appendLine("runtime_lifecycle_lease_reclaim_executor_allow_user_locked=$allowUserLocked")
            appendLine("runtime_lifecycle_lease_reclaim_executor_allow_core=$allowCore")
            appendLine("runtime_lifecycle_lease_reclaim_executor_allow_proot_core=$allowProotCore")
            appendLine("runtime_lifecycle_lease_reclaim_executor_allow_foreground_active=$allowForegroundActive")
            appendLine("runtime_lifecycle_lease_reclaim_executor_allow_unmanaged=$allowUnmanaged")
            appendLine("runtime_lifecycle_lease_reclaim_executor_last_action_at=$lastActionAtMs")
            appendLine("runtime_lifecycle_lease_reclaim_executor_last_unit_id=${lastUnitId.toStrategyActivationEnvValue()}")
            appendLine("runtime_lifecycle_lease_reclaim_executor_last_result=${lastResult.name.lowercase()}")
            appendLine("runtime_lifecycle_lease_reclaim_executor_last_reason=${lastReason.toStrategyActivationEnvValue()}")
            appendLine("runtime_lifecycle_lease_reclaim_executor_executed_count=$executedCount")
            appendLine("runtime_lifecycle_lease_reclaim_executor_skipped_count=$skippedCount")
            appendLine("runtime_lifecycle_lease_reclaim_executor_blocked_count=$blockedCount")
            appendLine("runtime_lifecycle_lease_reclaim_executor_failed_count=$failedCount")
            appendLine("runtime_lifecycle_strategy_activation_enabled=$enabled")
            appendLine("runtime_lifecycle_strategy_activation_mode=${mode.name.lowercase()}")
            appendLine("runtime_lifecycle_strategy_activation_max_actions_per_tick=$maxActionsPerTick")
            appendLine("runtime_lifecycle_strategy_activation_min_candidate_age_ms=$minCandidateAgeMs")
            appendLine("runtime_lifecycle_strategy_activation_require_non_ambiguous_match=$requireNonAmbiguousMatch")
            appendLine("runtime_lifecycle_strategy_activation_require_reclaimer_boundary=$requireReclaimerBoundary")
            appendLine("runtime_lifecycle_strategy_activation_allow_user_locked=$allowUserLocked")
            appendLine("runtime_lifecycle_strategy_activation_allow_core=$allowCore")
            appendLine("runtime_lifecycle_strategy_activation_allow_proot_core=$allowProotCore")
            appendLine("runtime_lifecycle_strategy_activation_allow_foreground_active=$allowForegroundActive")
            appendLine("runtime_lifecycle_strategy_activation_allow_unmanaged=$allowUnmanaged")
            appendLine("runtime_lifecycle_strategy_activation_last_action_at=$lastActionAtMs")
            appendLine("runtime_lifecycle_strategy_activation_last_unit_id=${lastUnitId.toStrategyActivationEnvValue()}")
            appendLine("runtime_lifecycle_strategy_activation_last_result=${lastResult.name.lowercase()}")
            appendLine("runtime_lifecycle_strategy_activation_last_reason=${lastReason.toStrategyActivationEnvValue()}")
            appendLine("runtime_lifecycle_strategy_activation_executed_count=$executedCount")
            appendLine("runtime_lifecycle_strategy_activation_skipped_count=$skippedCount")
            appendLine("runtime_lifecycle_strategy_activation_blocked_count=$blockedCount")
            appendLine("runtime_lifecycle_strategy_activation_failed_count=$failedCount")
            append(defaultStrategy.toEnvText(lastReason))
            append(foregroundHandoff.toEnvText())
            records.take(maxRecords).forEachIndexed { index, record ->
                val leasePrefix = "runtime_lifecycle_lease_reclaim_executor_record_${index + 1}"
                appendLine("${leasePrefix}_unit_id=${record.unitId.toStrategyActivationEnvValue()}")
                appendLine("${leasePrefix}_action=${record.action.toStrategyActivationEnvValue()}")
                appendLine("${leasePrefix}_result=${record.result.name.lowercase()}")
                appendLine("${leasePrefix}_reason=${record.reason.toStrategyActivationEnvValue()}")
                appendLine("${leasePrefix}_timestamp=${record.timestampMs}")
                appendLine("${leasePrefix}_executor=${record.executor.toStrategyActivationEnvValue()}")
                appendLine("${leasePrefix}_action_state_after=${record.actionStateAfter.toStrategyActivationEnvValue()}")
                appendLine("${leasePrefix}_resource_episode_after=${record.resourceEpisodeAfter.toStrategyActivationEnvValue()}")
                appendLine("${leasePrefix}_user_notice_after=${record.userNoticeAfter.toStrategyActivationEnvValue()}")
                val prefix = "runtime_lifecycle_strategy_activation_record_${index + 1}"
                appendLine("${prefix}_unit_id=${record.unitId.toStrategyActivationEnvValue()}")
                appendLine("${prefix}_action=${record.action.toStrategyActivationEnvValue()}")
                appendLine("${prefix}_result=${record.result.name.lowercase()}")
                appendLine("${prefix}_reason=${record.reason.toStrategyActivationEnvValue()}")
                appendLine("${prefix}_timestamp=${record.timestampMs}")
                appendLine("${prefix}_executor=${record.executor.toStrategyActivationEnvValue()}")
                appendLine("${prefix}_action_state_after=${record.actionStateAfter.toStrategyActivationEnvValue()}")
                appendLine("${prefix}_resource_episode_after=${record.resourceEpisodeAfter.toStrategyActivationEnvValue()}")
                appendLine("${prefix}_user_notice_after=${record.userNoticeAfter.toStrategyActivationEnvValue()}")
            }
            appendLine("runtime_lifecycle_lease_reclaim_executor_boundary=registered_runtime_or_ordinary_ubuntu_lease_via_runtime_reclaimer")
            appendLine("runtime_lifecycle_strategy_activation_boundary=lease_reclaim_only_via_runtime_reclaimer_no_host_process_terminator_no_proot_capacity_action")
        }
    }
}

data class RuntimeLifecycleLeaseReclaimRequest(
    val runtimeId: String,
    val unitId: String,
    val title: String,
    val reason: String
)

fun interface RuntimeLifecycleLeaseReclaimer {
    fun reclaim(
        context: Context?,
        request: RuntimeLifecycleLeaseReclaimRequest,
        now: Long
    ): RuntimeReclaimerRuntimeReclaimResult
}

/**
 * Narrow real-runtime entry for lease reclaim only.
 *
 * Despite the compatibility name, this is not a generic lifecycle strategy platform: it only
 * turns already-approved lease reclaim candidates into RuntimeReclaimer requests for registered
 * background runtimes or ordinary PRoot-observed Ubuntu processes.
 */
object RuntimeLifecycleStrategyActivator {
    private val defaultReclaimer = RuntimeLifecycleLeaseReclaimer { context, request, now ->
        if (context == null) {
            return@RuntimeLifecycleLeaseReclaimer RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = "android_context_missing"
            )
        }
        RuntimeReclaimer.reclaimRegisteredRuntime(
            context = context,
            runtimeId = request.runtimeId,
            title = request.title,
            reason = request.reason,
            now = now
        )
    }

    private val recentFailureAtByUnit = LinkedHashMap<String, Long>()

    @Volatile
    private var lastSnapshot = RuntimeLifecycleStrategyActivationSnapshot()
    @Volatile
    private var lastForegroundHandoff = RuntimeForegroundLeaseHandoffSnapshot()

    fun executionSnapshot(): RuntimeLifecycleStrategyActivationSnapshot = lastSnapshot

    fun onSnapshot(
        context: Context,
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig = RuntimeLifecycleStrategyActivationConfig(),
        reclaimerPolicy: RuntimeReclaimerPolicy = RuntimeReclaimerPolicyStore.load(context.applicationContext),
        reclaimer: RuntimeLifecycleLeaseReclaimer = defaultReclaimer,
        now: Long = System.currentTimeMillis()
    ): RuntimeLifecycleStrategyActivationSnapshot {
        val records = activate(
            context = context.applicationContext,
            snapshot = snapshot,
            config = config,
            reclaimerPolicy = reclaimerPolicy,
            reclaimer = reclaimer,
            now = now
        )
        val updated = snapshotFor(config, records)
        lastSnapshot = updated
        return updated
    }

    fun activate(
        context: Context?,
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig = RuntimeLifecycleStrategyActivationConfig(),
        reclaimerPolicy: RuntimeReclaimerPolicy = RuntimeReclaimerPolicy.default(),
        reclaimer: RuntimeLifecycleLeaseReclaimer = defaultReclaimer,
        now: Long = System.currentTimeMillis()
    ): List<RuntimeLifecycleStrategyActivationRecord> {
        if (!config.enabled) {
            return listOf(record("none", RuntimeLifecycleStrategyActivationResult.SKIPPED, "lease_reclaim_executor_disabled", now))
        }
        if (config.mode != RuntimeLifecycleStrategyActivationMode.LEASE_RECLAIM_ONLY ||
            config.defaultStrategy.mode != RuntimeLifecycleStrategyActivationMode.LEASE_RECLAIM_ONLY
        ) {
            return listOf(record("none", RuntimeLifecycleStrategyActivationResult.BLOCKED, "unsupported_lease_reclaim_executor_mode", now))
        }
        val maxActions = config.maxActionsPerTick.coerceAtLeast(0)
        if (maxActions == 0) {
            return listOf(record("none", RuntimeLifecycleStrategyActivationResult.SKIPPED, "max_actions_per_tick_zero", now))
        }
        pruneFailureCooldowns(now, config.failureCooldownMs)
        val foregroundHandoff = RuntimeForegroundLeaseHandoff.evaluate(
            snapshot = snapshot,
            strategy = config.defaultStrategy
        )
        lastForegroundHandoff = foregroundHandoff
        val candidates = candidates(snapshot, foregroundHandoff)
        if (candidates.isEmpty()) {
            if (foregroundHandoff.entries.any { it.state == RuntimeForegroundLeaseHandoffState.COOLING_LEASE }) {
                return listOf(
                    record(
                        foregroundHandoff.entries.first { it.state == RuntimeForegroundLeaseHandoffState.COOLING_LEASE }.unitId,
                        RuntimeLifecycleStrategyActivationResult.SKIPPED,
                        "foreground_handoff_cooling_lease_active",
                        now
                    )
                )
            }
            val activeLease = snapshot.lifecycleReclaimPlan.items.firstOrNull {
                it.retention == RuntimeWorkloadRetention.LEASE &&
                    it.disposition == RuntimeLifecycleReclaimDisposition.WATCH_LEASE &&
                    (
                        it.activityState == RuntimeLifecycleActivityState.ACTIVE ||
                            it.activityState == RuntimeLifecycleActivityState.WEAK_ACTIVITY
                        )
            }
            if (activeLease != null) {
                return listOf(
                    record(
                        activeLease.workloadId,
                        RuntimeLifecycleStrategyActivationResult.SKIPPED,
                        "active_lease_auto_renewed_by_reclaim_plan",
                        now
                    )
                )
            }
            return listOf(record("none", RuntimeLifecycleStrategyActivationResult.SKIPPED, "no_lease_reclaim_candidates", now))
        }

        val records = mutableListOf<RuntimeLifecycleStrategyActivationRecord>()
        var executed = 0
        for (candidate in candidates) {
            if (executed >= maxActions) break
            val blockReason = blockReason(candidate, snapshot, config, reclaimerPolicy, now)
            if (blockReason != null) {
                RuntimeLifecycleLedgerStore.recordAction(
                    context = context,
                    root = candidate.root,
                    item = candidate.reclaimItem,
                    unitId = candidate.plan.unitId,
                    pid = candidate.root.observedPid,
                    action = "lease_reclaim_blocked",
                    signal = "none",
                    targetMode = "none",
                    result = RuntimeLifecycleStrategyActivationResult.BLOCKED.name,
                    reason = blockReason,
                    hardConditions = candidate.reclaimItem.activityReason,
                    now = now
                )
                records += record(candidate.plan.unitId, RuntimeLifecycleStrategyActivationResult.BLOCKED, blockReason, now)
                continue
            }
            val reason = buildReason(candidate, snapshot, reclaimerPolicy)
            val result = if (candidate.root.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED) {
                reclaimUnattributedCandidate(
                    context = context,
                    candidate = candidate,
                    reason = reason,
                    now = now
                )
            } else {
                val runtimeId = candidate.root.ownerId
                if (runtimeId.isNullOrBlank()) {
                    records += record(
                        candidate.plan.unitId,
                        RuntimeLifecycleStrategyActivationResult.BLOCKED,
                        "registered_runtime_id_missing",
                        now
                    )
                    continue
                }
                reclaimer.reclaim(
                    context,
                    RuntimeLifecycleLeaseReclaimRequest(
                        runtimeId = runtimeId,
                        unitId = candidate.plan.unitId,
                        title = candidate.root.title,
                        reason = reason
                    ),
                    now
                )
            }
            if (result.executed) {
                RuntimeLifecycleLedgerStore.recordAction(
                    context = context,
                    root = candidate.root,
                    item = candidate.reclaimItem,
                    unitId = candidate.plan.unitId,
                    pid = candidate.root.observedPid,
                    action = "lease_reclaim_ko",
                    signal = result.signal,
                    targetMode = result.targetMode,
                    result = RuntimeLifecycleStrategyActivationResult.EXECUTED.name,
                    reason = result.reason,
                    hardConditions = candidate.reclaimItem.activityReason,
                    now = now
                )
                records += record(
                    candidate.plan.unitId,
                    RuntimeLifecycleStrategyActivationResult.EXECUTED,
                    result.reason,
                    now,
                    actionStateAfter = "resolved_by_lease_reclaim_executor",
                    resourceEpisodeAfter = "expected_stop_will_close_or_recover_episode",
                    userNoticeAfter = "resolved_by_lease_reclaim_executor"
                )
                executed += 1
            } else {
                val activationResult = if (result.skippedInFlight) {
                    RuntimeLifecycleStrategyActivationResult.BLOCKED
                } else {
                    recentFailureAtByUnit[failureCooldownKey(candidate)] = now
                    RuntimeLifecycleStrategyActivationResult.FAILED
                }
                RuntimeLifecycleLedgerStore.recordAction(
                    context = context,
                    root = candidate.root,
                    item = candidate.reclaimItem,
                    unitId = candidate.plan.unitId,
                    pid = candidate.root.observedPid,
                    action = "lease_reclaim_ko",
                    signal = result.signal,
                    targetMode = result.targetMode,
                    result = activationResult.name,
                    reason = result.reason,
                    hardConditions = candidate.reclaimItem.activityReason,
                    now = now
                )
                records += record(candidate.plan.unitId, activationResult, result.reason, now)
            }
        }
        return records.ifEmpty {
            listOf(record("none", RuntimeLifecycleStrategyActivationResult.SKIPPED, "all_candidates_consumed_without_action", now))
        }
    }

    fun resetForTests() {
        recentFailureAtByUnit.clear()
        lastForegroundHandoff = RuntimeForegroundLeaseHandoffSnapshot()
        lastSnapshot = RuntimeLifecycleStrategyActivationSnapshot()
    }

    private fun candidates(
        snapshot: RuntimeHealthSnapshot,
        foregroundHandoff: RuntimeForegroundLeaseHandoffSnapshot
    ): List<ActivationCandidate> {
        val rootsByKey = snapshot.roots.associateBy { it.ownershipKey }
        val itemsByWorkloadId = snapshot.lifecycleReclaimPlan.items.associateBy { it.workloadId }
        val handoffByRoot = foregroundHandoff.entries.associateBy { it.rootKey }
        val plannedCandidates = snapshot.systemProcessLifecycle.lifecycleActionPlanner.entries
            .mapNotNull { plan ->
                if (plan.finalAction != RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN) {
                    return@mapNotNull null
                }
                val root = rootsByKey[plan.rootKey] ?: return@mapNotNull null
                val handoff = handoffByRoot[root.ownershipKey]
                if (handoff?.state == RuntimeForegroundLeaseHandoffState.COOLING_LEASE ||
                    handoff?.state == RuntimeForegroundLeaseHandoffState.LEASE_EXPIRED
                ) {
                    return@mapNotNull null
                }
                val item = listOfNotNull(
                    root.ownershipKey,
                    root.ownerId,
                    root.processUnitId
                ).firstNotNullOfOrNull { itemsByWorkloadId[it] } ?: return@mapNotNull null
                ActivationCandidate(root = root, plan = plan, reclaimItem = item)
            }
            .sortedWith(
                compareBy<ActivationCandidate> {
                    if (it.reclaimItem.reclaimRank == 0) Int.MAX_VALUE else it.reclaimItem.reclaimRank
                }.thenBy { it.reclaimItem.leaseRemainingMs }
                    .thenByDescending { it.reclaimItem.rssKb }
                    .thenBy { it.plan.unitId }
            )
        val handoffCandidates = foregroundHandoff.entries
            .filter { it.state == RuntimeForegroundLeaseHandoffState.LEASE_EXPIRED }
            .mapNotNull { entry ->
                val root = rootsByKey[entry.rootKey] ?: return@mapNotNull null
                ActivationCandidate(
                    root = root,
                    plan = RuntimeLifecycleActionPlanEntry(
                        rootKey = root.ownershipKey,
                        unitId = entry.unitId,
                        effectiveTier = RuntimeLifecycleAuthorityTier.LEASE,
                        matchState = root.processUnitMatchState,
                        authoritySource = "foreground_lease_handoff",
                        lifecycleState = RuntimeLifecycleMatrixState.LEASE_CANDIDATE,
                        finalAction = RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN,
                        finalActionMode = RuntimeLifecycleFinalActionMode.DRY_RUN,
                        primaryReason = entry.reason,
                        allowedFutureActions = listOf(RuntimeLifecycleLeasePoolAdmission.RECLAIMER_ACTION),
                        executorBoundary = RuntimeLifecycleExecutorBoundary.RUNTIME_RECLAIMER
                    ),
                    reclaimItem = foregroundHandoffItem(root, entry)
                )
            }
        return (handoffCandidates + plannedCandidates)
            .distinctBy { it.root.ownershipKey }
            .sortedWith(
                compareBy<ActivationCandidate> {
                    if (it.reclaimItem.reclaimRank == 0) Int.MAX_VALUE else it.reclaimItem.reclaimRank
                }.thenBy { it.reclaimItem.leaseRemainingMs }
                    .thenByDescending { it.reclaimItem.rssKb }
                    .thenBy { it.plan.unitId }
            )
    }

    private fun reclaimUnattributedCandidate(
        context: Context?,
        candidate: ActivationCandidate,
        reason: String,
        now: Long
    ): RuntimeReclaimerRuntimeReclaimResult {
        if (context == null) {
            return RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = "android_context_missing"
            )
        }
        val pid = candidate.root.observedPid ?: return RuntimeReclaimerRuntimeReclaimResult(
            executed = false,
            reason = "unattributed_pid_missing"
        )
        return RuntimeReclaimer.reclaimUnattributedProcess(
            context = context,
            pid = pid,
            processGroupId = candidate.root.rootProcessGroupId
                ?.takeIf { it == pid || candidate.root.rootSessionId == pid },
            title = candidate.root.title,
            reason = reason,
            now = now
        )
    }

    private fun blockReason(
        candidate: ActivationCandidate,
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        now: Long
    ): String? {
        RuntimeLifecycleLeasePoolAdmission.reclaimCommandBlockReason(
            root = candidate.root,
            plan = candidate.plan,
            item = candidate.reclaimItem,
            snapshot = snapshot,
            config = config,
            reclaimerPolicy = reclaimerPolicy,
            now = now,
            requirePlannerCandidate = true,
            requireThreshold = true
        )?.let { return it }
        recentFailureAtByUnit[failureCooldownKey(candidate)]?.let { failedAt ->
            if (now - failedAt < config.failureCooldownMs.coerceAtLeast(0L)) {
                return "recent_reclaim_failure_cooldown"
            }
        }
        return null
    }

    private fun failureCooldownKey(candidate: ActivationCandidate): String {
        return when (candidate.root.ownerKind) {
            RuntimeRootOwnerKind.UNATTRIBUTED -> {
                candidate.root.observedPid
                    ?.let { "pid:$it" }
                    ?: "unit:${candidate.plan.unitId}"
            }
            else -> candidate.root.ownerId
                ?.takeIf { it.isNotBlank() }
                ?.let { "runtime:$it" }
                ?: "unit:${candidate.plan.unitId}"
        }
    }

    private fun buildReason(
        candidate: ActivationCandidate,
        snapshot: RuntimeHealthSnapshot,
        reclaimerPolicy: RuntimeReclaimerPolicy
    ): String {
        val item = candidate.reclaimItem
        return "lease_reclaim_executor=lease_reclaim_only " +
            "unit=${candidate.plan.unitId} " +
            "pressure=${snapshot.pressure.level.name} " +
            "leaseRemainingMs=${item.leaseRemainingMs} " +
            "leasePoolOverBudget=${snapshot.lifecycleReclaimPlan.leasePoolOverBudget} " +
            "reclaimRank=${item.reclaimRank} " +
            "profile=${reclaimerPolicy.activeProfile.name} " +
            "reason=${item.reason}"
    }

    private fun record(
        unitId: String,
        result: RuntimeLifecycleStrategyActivationResult,
        reason: String,
        now: Long,
        actionStateAfter: String = "unchanged",
        resourceEpisodeAfter: String = "unchanged",
        userNoticeAfter: String = "unchanged"
    ): RuntimeLifecycleStrategyActivationRecord {
        return RuntimeLifecycleStrategyActivationRecord(
            unitId = unitId,
            result = result,
            reason = reason,
            timestampMs = now,
            actionStateAfter = actionStateAfter,
            resourceEpisodeAfter = resourceEpisodeAfter,
            userNoticeAfter = userNoticeAfter
        )
    }

    private fun snapshotFor(
        config: RuntimeLifecycleStrategyActivationConfig,
        records: List<RuntimeLifecycleStrategyActivationRecord>
    ): RuntimeLifecycleStrategyActivationSnapshot {
        val last = records.lastOrNull()
        return RuntimeLifecycleStrategyActivationSnapshot(
            enabled = config.enabled,
            mode = config.mode,
            defaultStrategy = config.defaultStrategy,
            foregroundHandoff = lastForegroundHandoff,
            maxActionsPerTick = config.maxActionsPerTick,
            minCandidateAgeMs = config.minCandidateAgeMs,
            requireNonAmbiguousMatch = config.requireNonAmbiguousMatch,
            requireReclaimerBoundary = config.requireReclaimerBoundary,
            allowUserLocked = config.allowUserLocked,
            allowCore = config.allowCore,
            allowProotCore = config.allowProotCore,
            allowForegroundActive = config.allowForegroundActive,
            allowUnmanaged = config.allowUnmanaged,
            lastActionAtMs = last?.timestampMs ?: 0L,
            lastUnitId = last?.unitId ?: "none",
            lastResult = last?.result ?: RuntimeLifecycleStrategyActivationResult.SKIPPED,
            lastReason = last?.reason ?: "no_records",
            executedCount = records.count { it.result == RuntimeLifecycleStrategyActivationResult.EXECUTED },
            skippedCount = records.count { it.result == RuntimeLifecycleStrategyActivationResult.SKIPPED },
            blockedCount = records.count { it.result == RuntimeLifecycleStrategyActivationResult.BLOCKED },
            failedCount = records.count { it.result == RuntimeLifecycleStrategyActivationResult.FAILED },
            records = records
        )
    }

    private fun pruneFailureCooldowns(now: Long, cooldownMs: Long) {
        val iterator = recentFailureAtByUnit.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value >= cooldownMs.coerceAtLeast(0L)) {
                iterator.remove()
            }
        }
    }

    private fun foregroundHandoffItem(
        root: RuntimeRootSnapshot,
        entry: RuntimeForegroundLeaseHandoffEntry
    ): RuntimeLifecycleReclaimItem {
        return RuntimeLifecycleReclaimItem(
            workloadId = root.ownershipKey,
            workloadClass = RuntimeWorkloadClass.EPHEMERAL,
            retention = RuntimeWorkloadRetention.LEASE,
            tier = RuntimeLifecycleTier.LEASED_BACKGROUND,
            layer = RuntimeLifecycleLayer.LEASE_POOL,
            activityState = RuntimeLifecycleActivityState.EXPIRED,
            lane = RuntimeLaneKind.INTERACTIVE,
            backgroundAllowed = false,
            processCount = root.processCount,
            rssKb = root.rssKb,
            maxChildren = 16,
            overChildBudget = false,
            budgetState = RuntimeBudgetState.SOFT_PRESSURE,
            budgetActions = listOf(RuntimeBudgetAction.REQUEST_CLEANUP),
            disposition = RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE,
            leaseBaseTtlMs = 0L,
            leaseMaxTotalMs = 0L,
            leaseRemainingMs = entry.remainingLeaseMs,
            leaseAtMax = true,
            leaseExpiredSettlementCount = 2,
            reclaimRank = 1,
            tierReason = "foreground_handoff",
            layerReason = "foreground_inactive_handoff_to_lease_pool",
            activityReason = entry.reason,
            reason = entry.reason
        )
    }

    private data class ActivationCandidate(
        val root: RuntimeRootSnapshot,
        val plan: RuntimeLifecycleActionPlanEntry,
        val reclaimItem: RuntimeLifecycleReclaimItem
    )
}

private fun String?.toStrategyActivationEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
