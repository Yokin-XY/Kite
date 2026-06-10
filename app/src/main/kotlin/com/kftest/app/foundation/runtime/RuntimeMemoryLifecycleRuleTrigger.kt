package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
import java.util.concurrent.TimeUnit
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class RuntimeMemoryLifecycleRuleAction {
    NO_OP,
    RECLAIM_LEASE,
    REQUEST_PROOT_SCALE_OUT,
    REQUEST_PROOT_DOWNSCALE,
    WAIT
}

enum class RuntimeMemoryLifecycleRuleResult {
    IDLE,
    TRIGGERED,
    WAITING,
    BLOCKED
}

data class RuntimeMemoryLifecycleRuleRecord(
    val action: RuntimeMemoryLifecycleRuleAction,
    val result: RuntimeMemoryLifecycleRuleResult,
    val unitId: String = "none",
    val targetId: String = "none",
    val reason: String,
    val executorBoundary: String = "none",
    val timestampMs: Long
)

data class RuntimeMemoryLifecycleRuleSnapshot(
    val enabled: Boolean = true,
    val ruleModel: String = "event_table_coarse_tick_with_memory_pressure_settlement_no_time_bypass",
    val leaseSettlementTickMs: Long = RuntimeLifecycleLeasePolicy.default().settlementTickMs,
    val memorySampleTickMs: Long = RuntimeLifecycleLeasePolicy.default().memorySampleTickMs,
    val memoryPressureSampleAvailablePercent: Int =
        RuntimeLifecycleLeasePolicy.default().memoryPressureSampleAvailablePercent,
    val memoryPressureSampleCooldownMs: Long =
        RuntimeLifecycleLeasePolicy.default().memoryPressureSampleCooldownMs,
    val memoryPressureImmediateSettlement: Boolean =
        RuntimeLifecycleLeasePolicy.default().memoryPressureImmediateSettlement,
    val recordCount: Int = 0,
    val lastAction: RuntimeMemoryLifecycleRuleAction = RuntimeMemoryLifecycleRuleAction.NO_OP,
    val lastResult: RuntimeMemoryLifecycleRuleResult = RuntimeMemoryLifecycleRuleResult.IDLE,
    val lastUnitId: String = "none",
    val lastTargetId: String = "none",
    val lastReason: String = "not_evaluated",
    val records: List<RuntimeMemoryLifecycleRuleRecord> = emptyList()
) {
    fun toEnvText(maxRecords: Int = 5): String {
        return buildString {
            appendLine("runtime_memory_lifecycle_rule_trigger_enabled=$enabled")
            appendLine("runtime_memory_lifecycle_rule_trigger_model=${ruleModel.toRuleTriggerEnvValue()}")
            appendLine("runtime_memory_lifecycle_rule_trigger_lease_settlement_tick_ms=$leaseSettlementTickMs")
            appendLine("runtime_memory_lifecycle_rule_trigger_memory_sample_tick_ms=$memorySampleTickMs")
            appendLine("runtime_memory_lifecycle_rule_trigger_memory_pressure_sample_available_percent=$memoryPressureSampleAvailablePercent")
            appendLine("runtime_memory_lifecycle_rule_trigger_memory_pressure_sample_cooldown_ms=$memoryPressureSampleCooldownMs")
            appendLine("runtime_memory_lifecycle_rule_trigger_memory_pressure_immediate_settlement=$memoryPressureImmediateSettlement")
            appendLine("runtime_memory_lifecycle_rule_trigger_record_count=$recordCount")
            appendLine("runtime_memory_lifecycle_rule_trigger_last_action=${lastAction.name.lowercase()}")
            appendLine("runtime_memory_lifecycle_rule_trigger_last_result=${lastResult.name.lowercase()}")
            appendLine("runtime_memory_lifecycle_rule_trigger_last_unit_id=${lastUnitId.toRuleTriggerEnvValue()}")
            appendLine("runtime_memory_lifecycle_rule_trigger_last_target_id=${lastTargetId.toRuleTriggerEnvValue()}")
            appendLine("runtime_memory_lifecycle_rule_trigger_last_reason=${lastReason.toRuleTriggerEnvValue()}")
            appendLine("runtime_memory_lifecycle_rule_trigger_flow=monitoring_thresholds_existing_executor_boundary")
            append(RuntimeProcessTableResourceSampler.executionSnapshot().toEnvText())
            records.take(maxRecords).forEachIndexed { index, record ->
                val prefix = "runtime_memory_lifecycle_rule_trigger_record_${index + 1}"
                appendLine("${prefix}_action=${record.action.name.lowercase()}")
                appendLine("${prefix}_result=${record.result.name.lowercase()}")
                appendLine("${prefix}_unit_id=${record.unitId.toRuleTriggerEnvValue()}")
                appendLine("${prefix}_target_id=${record.targetId.toRuleTriggerEnvValue()}")
                appendLine("${prefix}_reason=${record.reason.toRuleTriggerEnvValue()}")
                appendLine("${prefix}_executor_boundary=${record.executorBoundary.toRuleTriggerEnvValue()}")
                appendLine("${prefix}_timestamp=${record.timestampMs}")
            }
        }
    }
}

/**
 * Memory lifecycle rule trigger.
 *
 * It consumes existing monitoring snapshots, checks time and memory thresholds, and forwards only
 * the allowed actions to their existing execution boundaries.
 */
object RuntimeMemoryLifecycleRuleTrigger {
    @Volatile
    private var lastSnapshot = RuntimeMemoryLifecycleRuleSnapshot()
    @Volatile
    private var lastLeaseSettlementAtMs: Long = 0L

    fun executionSnapshot(): RuntimeMemoryLifecycleRuleSnapshot = lastSnapshot

    fun onSnapshot(
        context: Context,
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig = RuntimeLifecycleStrategyActivationConfig(
            enabled = snapshot.lifecyclePolicySurface.lifecycleManagementEnabled
        ),
        reclaimerPolicy: RuntimeReclaimerPolicy = RuntimeReclaimerPolicyStore.load(context.applicationContext),
        reclaimer: RuntimeLifecycleLeaseReclaimer? = null,
        resourceSampler: RuntimeProcessResourceSampler = RuntimeProcessTableResourceSampler,
        now: Long = System.currentTimeMillis()
    ): RuntimeMemoryLifecycleRuleSnapshot {
        if (!config.enabled) {
            val records = listOf(
                RuntimeMemoryLifecycleRuleRecord(
                    action = RuntimeMemoryLifecycleRuleAction.WAIT,
                    result = RuntimeMemoryLifecycleRuleResult.WAITING,
                    reason = "runtime_lifecycle_management_disabled",
                    timestampMs = now
                )
            )
            return snapshotFor(records, snapshot.lifecycleReclaimPlan, enabled = false)
                .also { lastSnapshot = it }
        }
        resourceSampler.requestIfNeeded(
            context = context.applicationContext,
            snapshot = snapshot,
            policy = snapshot.lifecycleReclaimPlan.toLeasePolicyForSampler(),
            now = now
        )
        val records = evaluate(
            snapshot = snapshot,
            config = config,
            reclaimerPolicy = reclaimerPolicy,
            now = now,
            respectSettlementTick = true,
            lastLeaseSettlementAtMs = lastLeaseSettlementAtMs
        )
        if (records.any { it.result != RuntimeMemoryLifecycleRuleResult.WAITING }) {
            lastLeaseSettlementAtMs = now
        }
        val appContext = context.applicationContext
        if (records.any { it.action == RuntimeMemoryLifecycleRuleAction.REQUEST_PROOT_SCALE_OUT ||
                it.action == RuntimeMemoryLifecycleRuleAction.REQUEST_PROOT_DOWNSCALE ||
                it.reason == "proot_capacity_queue_requested"
            }
        ) {
            RuntimeProotCapacityActuator.onSnapshot(appContext, snapshot)
        }
        if (records.any { it.action == RuntimeMemoryLifecycleRuleAction.RECLAIM_LEASE }) {
            if (reclaimer == null) {
                RuntimeLifecycleStrategyActivator.onSnapshot(
                    context = appContext,
                    snapshot = snapshot,
                    config = config,
                    reclaimerPolicy = reclaimerPolicy,
                    now = now
                )
            } else {
                RuntimeLifecycleStrategyActivator.onSnapshot(
                    context = appContext,
                    snapshot = snapshot,
                    config = config,
                    reclaimerPolicy = reclaimerPolicy,
                    reclaimer = reclaimer,
                    now = now
                )
            }
        }
        return snapshotFor(records, snapshot.lifecycleReclaimPlan, enabled = config.enabled).also { lastSnapshot = it }
    }

    fun evaluate(
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig = RuntimeLifecycleStrategyActivationConfig(),
        reclaimerPolicy: RuntimeReclaimerPolicy = RuntimeReclaimerPolicy.default(),
        now: Long = System.currentTimeMillis(),
        respectSettlementTick: Boolean = false,
        lastLeaseSettlementAtMs: Long = 0L
    ): List<RuntimeMemoryLifecycleRuleRecord> {
        val records = mutableListOf<RuntimeMemoryLifecycleRuleRecord>()
        records += leaseRule(
            snapshot = snapshot,
            config = config,
            reclaimerPolicy = reclaimerPolicy,
            now = now,
            respectSettlementTick = respectSettlementTick,
            lastLeaseSettlementAtMs = lastLeaseSettlementAtMs
        )
        val proot = prootRule(snapshot, now)
        if (proot != null) {
            records += proot
        }
        return records.ifEmpty {
            listOf(
                RuntimeMemoryLifecycleRuleRecord(
                    action = RuntimeMemoryLifecycleRuleAction.NO_OP,
                    result = RuntimeMemoryLifecycleRuleResult.IDLE,
                    reason = "no_time_or_memory_threshold_reached",
                    timestampMs = now
                )
            )
        }
    }

    fun resetForTests() {
        lastSnapshot = RuntimeMemoryLifecycleRuleSnapshot()
        lastLeaseSettlementAtMs = 0L
        RuntimeProcessTableResourceSampler.resetForTests()
    }

    private fun leaseRule(
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        now: Long,
        respectSettlementTick: Boolean,
        lastLeaseSettlementAtMs: Long
    ): List<RuntimeMemoryLifecycleRuleRecord> {
        if (!config.enabled) {
            return listOf(wait("lease_reclaim_executor_disabled", now))
        }
        val foregroundHandoff = RuntimeForegroundLeaseHandoff.evaluate(
            snapshot = snapshot,
            strategy = config.defaultStrategy
        )
        if (foregroundHandoff.entries.any { it.state == RuntimeForegroundLeaseHandoffState.COOLING_LEASE }) {
            val entry = foregroundHandoff.entries.first { it.state == RuntimeForegroundLeaseHandoffState.COOLING_LEASE }
            return listOf(wait("foreground_background_cooling_not_expired", now, unitId = entry.unitId))
        }
        val candidates = leaseCandidates(snapshot, foregroundHandoff)
        if (candidates.isEmpty()) {
            val activeLease = snapshot.lifecycleReclaimPlan.items.firstOrNull {
                it.retention == RuntimeWorkloadRetention.LEASE &&
                    it.disposition == RuntimeLifecycleReclaimDisposition.WATCH_LEASE &&
                    (it.activityState == RuntimeLifecycleActivityState.ACTIVE ||
                        it.activityState == RuntimeLifecycleActivityState.WEAK_ACTIVITY)
            }
            return activeLease?.let {
                listOf(wait("active_lease_kept_or_renewed", now, unitId = it.workloadId))
            } ?: emptyList()
        }
        val settlementGate = settlementGate(
            snapshot = snapshot,
            candidates = candidates,
            config = config,
            reclaimerPolicy = reclaimerPolicy,
            now = now,
            lastLeaseSettlementAtMs = lastLeaseSettlementAtMs
        )
        if (respectSettlementTick && !settlementGate.allowed) {
            return listOf(
                wait(
                    settlementGate.reason,
                    now,
                    unitId = candidates.firstOrNull()?.plan?.unitId ?: "none",
                    targetId = candidates.firstOrNull()?.root?.leaseTargetId() ?: "none"
                )
            )
        }
        val records = mutableListOf<RuntimeMemoryLifecycleRuleRecord>()
        val memoryThresholdReached = memoryThresholdReached(
            snapshot = snapshot,
            candidates = candidates,
            config = config,
            reclaimerPolicy = reclaimerPolicy,
            now = now
        )
        for (candidate in candidates) {
            val blockReason = blockReason(candidate, snapshot, config, reclaimerPolicy, now)
            if (blockReason != null) {
                records += block(
                    blockReason,
                    now,
                    unitId = candidate.plan.unitId,
                    targetId = candidate.root.leaseTargetId()
                )
                continue
            }
            val leaseExpired = RuntimeLifecycleLeasePoolAdmission.run {
                candidate.item.isLeaseExpired()
            }
            if (!leaseExpired) {
                records += RuntimeMemoryLifecycleRuleRecord(
                    action = RuntimeMemoryLifecycleRuleAction.NO_OP,
                    result = RuntimeMemoryLifecycleRuleResult.IDLE,
                    unitId = candidate.plan.unitId,
                    targetId = candidate.root.leaseTargetId(),
                    reason = if (memoryThresholdReached) {
                        "memory_pressure_settlement_waits_for_lease_expiry"
                    } else {
                        "no_memory_pressure_or_expired_lease"
                    },
                    timestampMs = now
                )
                continue
            }
            records += RuntimeMemoryLifecycleRuleRecord(
                action = RuntimeMemoryLifecycleRuleAction.RECLAIM_LEASE,
                result = RuntimeMemoryLifecycleRuleResult.TRIGGERED,
                unitId = candidate.plan.unitId,
                targetId = candidate.root.leaseTargetId(),
                reason = "coarse_tick_expired_lease_reclaim",
                executorBoundary = "RuntimeReclaimer",
                timestampMs = now
            )
            return records
        }
        return records
    }

    private fun prootRule(
        snapshot: RuntimeHealthSnapshot,
        now: Long
    ): RuntimeMemoryLifecycleRuleRecord? {
        val executor = snapshot.prootCapacityExecutor
        val budget = snapshot.lifecycleProotExpansionBudget
        return when {
            executor.launchEligible -> RuntimeMemoryLifecycleRuleRecord(
                action = RuntimeMemoryLifecycleRuleAction.REQUEST_PROOT_SCALE_OUT,
                result = RuntimeMemoryLifecycleRuleResult.TRIGGERED,
                targetId = executor.registeredRuntimeTargetId,
                reason = "proot_scale_out_threshold_reached_and_capacity_boundary_ready",
                executorBoundary = "RuntimeProotCapacityActuator",
                timestampMs = now
            )
            executor.downlineEligible -> RuntimeMemoryLifecycleRuleRecord(
                action = RuntimeMemoryLifecycleRuleAction.REQUEST_PROOT_DOWNSCALE,
                result = RuntimeMemoryLifecycleRuleResult.TRIGGERED,
                targetId = executor.registeredRuntimeTargetId,
                reason = "proot_elastic_idle_threshold_reached_and_capacity_boundary_ready",
                executorBoundary = "RuntimeProotCapacityActuator",
                timestampMs = now
            )
            budget.queueRequested || executor.state == RuntimeProotCapacityExecutorState.QUEUE_ON_SINGLE_PROOT_REVIEW ->
                wait("proot_capacity_queue_requested", now, targetId = executor.registeredRuntimeTargetId)
            budget.expansionRequested ->
                wait("proot_scale_out_waiting_for_memory_or_capacity_boundary", now, targetId = executor.registeredRuntimeTargetId)
            budget.downscaleRequested || budget.downlineRequested ->
                wait("proot_elastic_idle_not_ready_for_capacity_downline", now, targetId = executor.registeredRuntimeTargetId)
            else -> null
        }
    }

    private fun leaseCandidates(
        snapshot: RuntimeHealthSnapshot,
        foregroundHandoff: RuntimeForegroundLeaseHandoffSnapshot
    ): List<LeaseCandidate> {
        val rootsByKey = snapshot.roots.associateBy { it.ownershipKey }
        val itemsByWorkloadId = snapshot.lifecycleReclaimPlan.items.associateBy { it.workloadId }
        val handoffByRoot = foregroundHandoff.entries.associateBy { it.rootKey }
        val planned = snapshot.systemProcessLifecycle.lifecycleActionPlanner.entries.mapNotNull { plan ->
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
            val item = listOfNotNull(root.ownershipKey, root.ownerId, root.processUnitId)
                .firstNotNullOfOrNull { itemsByWorkloadId[it] }
                ?: return@mapNotNull null
            LeaseCandidate(root, plan, item)
        }
        val handoff = foregroundHandoff.entries
            .filter { it.state == RuntimeForegroundLeaseHandoffState.LEASE_EXPIRED }
            .mapNotNull { entry ->
                val root = rootsByKey[entry.rootKey] ?: return@mapNotNull null
                LeaseCandidate(
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
                    item = RuntimeLifecycleReclaimItem(
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
                )
            }
        return (handoff + planned)
            .distinctBy { it.root.ownershipKey }
            .sortedWith(
                compareBy<LeaseCandidate> {
                    if (it.item.reclaimRank == 0) Int.MAX_VALUE else it.item.reclaimRank
                }.thenBy { it.item.leaseRemainingMs }
                    .thenByDescending { it.item.rssKb }
                    .thenBy { it.plan.unitId }
            )
    }

    private fun blockReason(
        candidate: LeaseCandidate,
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        now: Long
    ): String? {
        return RuntimeLifecycleLeasePoolAdmission.reclaimCommandBlockReason(
            root = candidate.root,
            plan = candidate.plan,
            item = candidate.item,
            snapshot = snapshot,
            config = config,
            reclaimerPolicy = reclaimerPolicy,
            now = now,
            requirePlannerCandidate = false,
            requireThreshold = false
        )
    }

    private fun settlementGate(
        snapshot: RuntimeHealthSnapshot,
        candidates: List<LeaseCandidate>,
        config: RuntimeLifecycleStrategyActivationConfig,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        now: Long,
        lastLeaseSettlementAtMs: Long
    ): SettlementGate {
        val tickMs = snapshot.lifecycleReclaimPlan.policyLeaseSettlementTickMs
            .takeIf { it > 0L }
            ?: RuntimeLifecycleLeasePolicy.default().settlementTickMs
        val memoryImmediate = snapshot.lifecycleReclaimPlan.policyLeaseMemoryPressureImmediateSettlement ||
            snapshot.lifecycleReclaimPlan.policyLeaseSettlementTickMs <= 0L
        val memoryPressure = memoryThresholdReached(snapshot, candidates, config, reclaimerPolicy, now)
        if (memoryImmediate && memoryPressure) {
            return SettlementGate(true, "memory_pressure_bypasses_coarse_lease_tick")
        }
        if (lastLeaseSettlementAtMs <= 0L || now - lastLeaseSettlementAtMs >= tickMs.coerceAtLeast(1L)) {
            return SettlementGate(true, "coarse_lease_settlement_tick_due")
        }
        return SettlementGate(false, "lease_settlement_waiting_for_coarse_tick")
    }

    private fun memoryThresholdReached(
        snapshot: RuntimeHealthSnapshot,
        candidates: List<LeaseCandidate>,
        config: RuntimeLifecycleStrategyActivationConfig,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        now: Long
    ): Boolean {
        return snapshot.lifecycleReclaimPlan.leasePoolOverBudget ||
            snapshot.pressure.level.ordinal >= RuntimePressureLevel.HIGH.ordinal ||
            candidates.any {
                it.plan.resourceState == RuntimeProcessResourceMemoryState.OVER_LIMIT &&
                    blockReason(it, snapshot, config, reclaimerPolicy, now) == null
            }
    }

    private fun wait(
        reason: String,
        now: Long,
        unitId: String = "none",
        targetId: String = "none"
    ): RuntimeMemoryLifecycleRuleRecord {
        return RuntimeMemoryLifecycleRuleRecord(
            action = RuntimeMemoryLifecycleRuleAction.WAIT,
            result = RuntimeMemoryLifecycleRuleResult.WAITING,
            unitId = unitId,
            targetId = targetId,
            reason = reason,
            timestampMs = now
        )
    }

    private fun block(
        reason: String,
        now: Long,
        unitId: String = "none",
        targetId: String = "none"
    ): RuntimeMemoryLifecycleRuleRecord {
        return RuntimeMemoryLifecycleRuleRecord(
            action = RuntimeMemoryLifecycleRuleAction.NO_OP,
            result = RuntimeMemoryLifecycleRuleResult.BLOCKED,
            unitId = unitId,
            targetId = targetId,
            reason = reason,
            timestampMs = now
        )
    }

    private fun RuntimeRootSnapshot.leaseTargetId(): String {
        return ownerId ?: observedPid?.takeIf { it > 0 }?.let { "pid:$it" } ?: "none"
    }

    private fun snapshotFor(
        records: List<RuntimeMemoryLifecycleRuleRecord>,
        reclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot = RuntimeLifecycleReclaimPlanDryRunSnapshot(),
        enabled: Boolean = true
    ): RuntimeMemoryLifecycleRuleSnapshot {
        val last = records.lastOrNull()
        return RuntimeMemoryLifecycleRuleSnapshot(
            enabled = enabled,
            leaseSettlementTickMs = reclaimPlan.policyLeaseSettlementTickMs
                .takeIf { it > 0L }
                ?: RuntimeLifecycleLeasePolicy.default().settlementTickMs,
            memorySampleTickMs = reclaimPlan.policyLeaseMemorySampleTickMs
                .takeIf { it > 0L }
                ?: RuntimeLifecycleLeasePolicy.default().memorySampleTickMs,
            memoryPressureSampleAvailablePercent = reclaimPlan.policyLeaseMemoryPressureSampleAvailablePercent
                .takeIf { it > 0 }
                ?: RuntimeLifecycleLeasePolicy.default().memoryPressureSampleAvailablePercent,
            memoryPressureSampleCooldownMs = reclaimPlan.policyLeaseMemoryPressureSampleCooldownMs
                .takeIf { it > 0L }
                ?: RuntimeLifecycleLeasePolicy.default().memoryPressureSampleCooldownMs,
            memoryPressureImmediateSettlement =
                reclaimPlan.policyLeaseMemoryPressureImmediateSettlement ||
                    reclaimPlan.policyLeaseSettlementTickMs <= 0L,
            recordCount = records.size,
            lastAction = last?.action ?: RuntimeMemoryLifecycleRuleAction.NO_OP,
            lastResult = last?.result ?: RuntimeMemoryLifecycleRuleResult.IDLE,
            lastUnitId = last?.unitId ?: "none",
            lastTargetId = last?.targetId ?: "none",
            lastReason = last?.reason ?: "no_records",
            records = records
        )
    }

    private data class LeaseCandidate(
        val root: RuntimeRootSnapshot,
        val plan: RuntimeLifecycleActionPlanEntry,
        val item: RuntimeLifecycleReclaimItem
    )

    private data class SettlementGate(
        val allowed: Boolean,
        val reason: String
    )
}

data class RuntimeProcessTableResourceSamplerDecision(
    val requested: Boolean,
    val reason: String
)

data class RuntimeProcessTableResourceSamplerSnapshot(
    val enabled: Boolean = true,
    val mode: String = "standard_command_on_read_plus_low_density_timer_and_pressure",
    val command: String = WorkspaceBuildSupport.CONTAINER_RUNTIME_RESOURCE_SAMPLER_COMMAND,
    val lastRequestAtMs: Long = 0L,
    val lastRunAtMs: Long = 0L,
    val lastResult: String = "not_requested",
    val lastReason: String = "none",
    val lastExitCode: Int = -1,
    val lastOutputPreview: String = "none"
) {
    fun toEnvText(): String {
        return buildString {
            appendLine("runtime_process_resource_sampler_enabled=$enabled")
            appendLine("runtime_process_resource_sampler_mode=${mode.toRuleTriggerEnvValue()}")
            appendLine("runtime_process_resource_sampler_command=${command.toRuleTriggerEnvValue()}")
            appendLine("runtime_process_resource_sampler_standard_command_triggers=ps_aux,top")
            appendLine("runtime_process_resource_sampler_last_request_at=$lastRequestAtMs")
            appendLine("runtime_process_resource_sampler_last_run_at=$lastRunAtMs")
            appendLine("runtime_process_resource_sampler_last_result=${lastResult.toRuleTriggerEnvValue()}")
            appendLine("runtime_process_resource_sampler_last_reason=${lastReason.toRuleTriggerEnvValue()}")
            appendLine("runtime_process_resource_sampler_last_exit_code=$lastExitCode")
            appendLine("runtime_process_resource_sampler_last_output_preview=${lastOutputPreview.toRuleTriggerEnvValue()}")
            appendLine("runtime_process_resource_sampler_boundary=internal_observation_only_no_kill_no_reclaim_no_proot_capacity_action")
        }
    }
}

fun interface RuntimeProcessResourceSampler {
    fun requestIfNeeded(
        context: Context,
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeLifecycleLeasePolicy,
        now: Long
    ): RuntimeProcessTableResourceSamplerDecision
}

object RuntimeProcessTableResourceSampler : RuntimeProcessResourceSampler {
    private const val LOG_TAG = "RuntimeProcessSampler"
    private const val TIMEOUT_SECONDS = 20L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running: Boolean = false

    @Volatile
    private var lastRequestAtMs: Long = 0L

    @Volatile
    private var lastSnapshot = RuntimeProcessTableResourceSamplerSnapshot()

    fun executionSnapshot(): RuntimeProcessTableResourceSamplerSnapshot = lastSnapshot

    fun resourceSamplingReasonForTests(
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeLifecycleLeasePolicy,
        now: Long,
        lastRequestAtMs: Long
    ): String? {
        return resourceSamplingReason(snapshot, policy, now, lastRequestAtMs)
    }

    override fun requestIfNeeded(
        context: Context,
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeLifecycleLeasePolicy,
        now: Long
    ): RuntimeProcessTableResourceSamplerDecision {
        if (!snapshot.hasResourceSamplingTargets()) {
            recordSkipped("no_running_process_table_targets", now)
            return RuntimeProcessTableResourceSamplerDecision(false, "no_running_process_table_targets")
        }
        val reason = resourceSamplingReason(snapshot, policy, now, lastRequestAtMs)
        if (reason == null) {
            recordSkipped("waiting_for_memory_sample_tick_or_pressure", now)
            return RuntimeProcessTableResourceSamplerDecision(false, "waiting_for_memory_sample_tick_or_pressure")
        }
        synchronized(this) {
            if (running) {
                recordSkipped("sampler_already_running", now)
                return RuntimeProcessTableResourceSamplerDecision(false, "sampler_already_running")
            }
            running = true
            lastRequestAtMs = now
            lastSnapshot = lastSnapshot.copy(
                lastRequestAtMs = now,
                lastResult = "requested",
                lastReason = reason,
                lastExitCode = -1,
                lastOutputPreview = "none"
            )
        }
        val appContext = context.applicationContext
        scope.launch {
            runSampler(appContext, reason, now)
        }
        return RuntimeProcessTableResourceSamplerDecision(true, reason)
    }

    fun resetForTests() {
        synchronized(this) {
            running = false
            lastRequestAtMs = 0L
            lastSnapshot = RuntimeProcessTableResourceSamplerSnapshot()
        }
    }

    private fun resourceSamplingReason(
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeLifecycleLeasePolicy,
        now: Long,
        lastRequestAtMs: Long
    ): String? {
        val pressure = snapshot.samplePressureReached(policy)
        val pressureCooldownMs = policy.memoryPressureSampleCooldownMs.coerceAtLeast(1_000L)
        if (pressure && now - lastRequestAtMs >= pressureCooldownMs) {
            return "memory_pressure_resource_sample"
        }
        val tickMs = policy.memorySampleTickMs.coerceAtLeast(1_000L)
        if (lastRequestAtMs <= 0L || now - lastRequestAtMs >= tickMs) {
            return "low_density_memory_sample_tick"
        }
        return null
    }

    private fun runSampler(
        context: Context,
        reason: String,
        requestedAt: Long
    ) {
        val result = runCatching {
            val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                workingDirectory = "/workspace",
                argv = listOf("/workspace/.kf/system/bin/kf-resource-sampler", "--update-table")
            )
            val process = ProcessBuilder(config.command)
                .redirectErrorStream(true)
                .apply { environment().putAll(config.env) }
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                RuntimeProcessTableSamplerRunResult(
                    exitCode = -1,
                    result = "timeout",
                    output = output
                )
            } else {
                RuntimeProcessTableSamplerRunResult(
                    exitCode = process.exitValue(),
                    result = if (process.exitValue() == 0) "executed" else "failed",
                    output = output
                )
            }
        }.getOrElse { error ->
            RuntimeProcessTableSamplerRunResult(
                exitCode = -1,
                result = "failed",
                output = error.message ?: error::class.java.simpleName
            )
        }
        synchronized(this) {
            running = false
            lastSnapshot = lastSnapshot.copy(
                lastRequestAtMs = requestedAt,
                lastRunAtMs = System.currentTimeMillis(),
                lastResult = result.result,
                lastReason = reason,
                lastExitCode = result.exitCode,
                lastOutputPreview = result.output.take(180)
            )
        }
        if (result.result == "executed") {
            ContainerProcessStore.refresh(context)
        } else {
            Logger.i(LOG_TAG, "resource sampler ${result.result}: reason=$reason exit=${result.exitCode} output=${result.output.take(180)}")
        }
    }

    private fun recordSkipped(
        reason: String,
        @Suppress("UNUSED_PARAMETER") now: Long
    ) {
        if (lastSnapshot.lastResult != "not_requested") {
            return
        }
        lastSnapshot = lastSnapshot.copy(
            lastRequestAtMs = lastRequestAtMs,
            lastResult = "skipped",
            lastReason = reason,
            lastOutputPreview = "none"
        )
    }

    private data class RuntimeProcessTableSamplerRunResult(
        val exitCode: Int,
        val result: String,
        val output: String
    )
}

private fun RuntimeHealthSnapshot.hasResourceSamplingTargets(): Boolean {
    return roots.any { it.isRunning } || processResourceSnapshot.processCount > 0
}

private fun RuntimeHealthSnapshot.samplePressureReached(
    policy: RuntimeLifecycleLeasePolicy
): Boolean {
    if (pressure.level.ordinal >= RuntimePressureLevel.HIGH.ordinal ||
        pressure.hostAvailableLevel.ordinal >= RuntimePressureLevel.HIGH.ordinal ||
        lifecycleReclaimPlan.leasePoolOverBudget
    ) {
        return true
    }
    val total = pressure.hostMemTotalKb ?: 0L
    val available = pressure.hostMemAvailableKb ?: 0L
    if (total <= 0L || available < 0L) return false
    val availablePercent = ((available * 100L) / total).toInt()
    return availablePercent <= policy.memoryPressureSampleAvailablePercent
}

private fun RuntimeLifecycleReclaimPlanDryRunSnapshot.toLeasePolicyForSampler(): RuntimeLifecycleLeasePolicy {
    val defaults = RuntimeLifecycleLeasePolicy.default()
    return defaults.copy(
        settlementTickMs = policyLeaseSettlementTickMs.takeIf { it > 0L } ?: defaults.settlementTickMs,
        memorySampleTickMs = policyLeaseMemorySampleTickMs.takeIf { it > 0L } ?: defaults.memorySampleTickMs,
        memoryPressureSampleAvailablePercent =
            policyLeaseMemoryPressureSampleAvailablePercent.takeIf { it > 0 }
                ?: defaults.memoryPressureSampleAvailablePercent,
        memoryPressureSampleCooldownMs =
            policyLeaseMemoryPressureSampleCooldownMs.takeIf { it > 0L }
                ?: defaults.memoryPressureSampleCooldownMs,
        memoryPressureImmediateSettlement =
            policyLeaseMemoryPressureImmediateSettlement || policyLeaseSettlementTickMs <= 0L
    )
}

private fun String?.toRuleTriggerEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return replace("\r", " ")
        .replace("\n", " ")
        .replace("=", ":")
        .trim()
        .ifBlank { "none" }
}
