package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.service.BackgroundRuntimeHost
import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.BackgroundRuntimeRecord
import com.kite.app.foundation.service.isActiveRuntime
import java.util.LinkedHashMap

data class RuntimeProotCapacityActuatorExecutionSnapshot(
    val lastExecutionAtMs: Long = 0L,
    val lastExecutionKind: String = "none",
    val lastTargetId: String = "none",
    val lastReason: String = "none",
    val lastSkipReason: String = "none",
    val secondProotStartRequestCount: Int = 0,
    val nextProotStartRequestCount: Int = secondProotStartRequestCount,
    val prootDownlineRequestCount: Int = 0,
    val queueCreationRequestCount: Int = 0,
    val skippedInFlightCount: Int = 0,
    val inFlightTargetCount: Int = 0
) {
    fun toEnvText(): String {
        return buildString {
            appendLine("proot_capacity_actuator_mode=android_proot_capacity_actuator_v0")
            appendLine("proot_capacity_actuator_execution_owner=android_control_plane")
            appendLine("proot_capacity_actuator_last_execution_at=$lastExecutionAtMs")
            appendLine("proot_capacity_actuator_last_execution_kind=${lastExecutionKind.toProotCapacityActuatorEnvValue()}")
            appendLine("proot_capacity_actuator_last_target_id=${lastTargetId.toProotCapacityActuatorEnvValue()}")
            appendLine("proot_capacity_actuator_last_reason=${lastReason.toProotCapacityActuatorEnvValue()}")
            appendLine("proot_capacity_actuator_last_skip_reason=${lastSkipReason.toProotCapacityActuatorEnvValue()}")
            appendLine("proot_capacity_actuator_next_proot_start_request_count=$nextProotStartRequestCount")
            appendLine("proot_capacity_actuator_second_proot_start_request_count=$secondProotStartRequestCount")
            appendLine("proot_capacity_actuator_proot_downline_request_count=$prootDownlineRequestCount")
            appendLine("proot_capacity_actuator_queue_creation_request_count=$queueCreationRequestCount")
            appendLine("proot_capacity_actuator_skipped_in_flight_count=$skippedInFlightCount")
            appendLine("proot_capacity_actuator_in_flight_target_count=$inFlightTargetCount")
            appendLine("proot_capacity_actuator_boundary=android_only_policy_bound_deduped_no_ubuntu_direct_proot_control")
        }
    }
}

/**
 * Android-side actuator for PRoot capacity changes.
 *
 * The dry-run planner can only request capacity. This actuator is the narrow execution bridge:
 * it starts or downlines only explicitly bound PROOT_CAPACITY_WORKER runtimes, and only after
 * lifecycle budget has approved the request.
 */
object RuntimeProotCapacityActuator {
    private const val LOG_TAG = "RuntimeProotCapacityActuator"
    private const val IN_FLIGHT_TTL_MS = 2 * 60_000L
    private const val QUEUE_DELEGATE_MIN_INTERVAL_MS = 30_000L
    private const val CAPACITY_WORKER_IDLE_BASELINE_PROCESSES = 2

    private val inFlightTargetIds = LinkedHashMap<String, Long>()
    private val idleSinceByTargetId = LinkedHashMap<String, Long>()

    @Volatile
    private var lastExecutionAtMs: Long = 0L
    private var lastExecutionKind: String = "none"
    private var lastTargetId: String = "none"
    private var lastReason: String = "none"
    private var lastSkipReason: String = "none"
    private var secondProotStartRequestCount: Int = 0
    private var prootDownlineRequestCount: Int = 0
    private var queueCreationRequestCount: Int = 0
    private var skippedInFlightCount: Int = 0

    @Synchronized
    fun executionSnapshot(): RuntimeProotCapacityActuatorExecutionSnapshot {
        pruneInFlight(System.currentTimeMillis())
        return RuntimeProotCapacityActuatorExecutionSnapshot(
            lastExecutionAtMs = lastExecutionAtMs,
            lastExecutionKind = lastExecutionKind,
            lastTargetId = lastTargetId,
            lastReason = lastReason,
            lastSkipReason = lastSkipReason,
            secondProotStartRequestCount = secondProotStartRequestCount,
            prootDownlineRequestCount = prootDownlineRequestCount,
            queueCreationRequestCount = queueCreationRequestCount,
            skippedInFlightCount = skippedInFlightCount,
            inFlightTargetCount = inFlightTargetIds.size
        )
    }

    fun onSnapshot(context: Context, snapshot: RuntimeHealthSnapshot) {
        val appContext = context.applicationContext
        val policy = RuntimeProotCapacityExecutorPolicyStore.load(appContext)
        val executor = snapshot.prootCapacityExecutor
        val lifecycleBudget = snapshot.lifecycleProotExpansionBudget

        when {
            executor.launchEligible -> maybeStartNextProot(
                context = appContext,
                policy = policy,
                executor = executor
            )
            lifecycleBudget.lifecycleApprovesRequestedAction &&
                (lifecycleBudget.downscaleRequested || lifecycleBudget.downlineRequested) ->
                maybeDownlineProot(
                    context = appContext,
                    policy = policy,
                    requestedAction = lifecycleBudget.requestedAction,
                    snapshot = snapshot
                )
            lifecycleBudget.lifecycleApprovesRequestedAction && lifecycleBudget.queueRequested ->
                recordQueueDelegated(lifecycleBudget.requestedAction)
        }
    }

    private fun maybeStartNextProot(
        context: Context,
        policy: RuntimeProotCapacityExecutorPolicy,
        executor: RuntimeProotCapacityExecutorSnapshot
    ) {
        if (!policy.enabled) {
            recordSkip("policy_disabled")
            return
        }
        if (executor.maxProotsReached) {
            recordSkip("max_proots_reached:${executor.maxProots}")
            return
        }
        val targetId = executor.registeredRuntimeTargetId
            .takeIf { it.isNotBlank() && it != "none" }
            ?: policy.expansionRuntimeIds.firstOrNull()
            ?: "auto_registered_proot_capacity_worker"
        val target = findCapacityRuntime(context, targetId, policy.maxProots)
        if (target == null) {
            recordSkip("next_proot_runtime_not_bound")
            return
        }
        if (target.prootCapacityWorkerIndex() <= 1) {
            recordSkip("default_proot_1_is_not_scale_out_target")
            return
        }
        if (target.isActiveRuntime()) {
            recordSkip("next_proot_runtime_already_active:${target.id}")
            return
        }
        if (!markInFlight(target.id, policy.scaleOutCooldownMs)) {
            return
        }
        if (!RuntimeProotMemoryAdmission.reserve(target.id, executor.memoryReservationRequiredKb)) {
            recordSkip("memory_budget_already_reserved_or_invalid:${target.id}")
            return
        }
        val reason = executor.reason
        Logger.i(LOG_TAG, "start bound next PRoot capacity worker=${target.id} reason=$reason")
        BackgroundRuntimeHost.startRuntime(context, target.id)
        recordExecution(
            kind = "start_bound_next_proot_runtime",
            targetId = target.id,
            reason = reason
        )
    }

    private fun maybeDownlineProot(
        context: Context,
        policy: RuntimeProotCapacityExecutorPolicy,
        requestedAction: String,
        snapshot: RuntimeHealthSnapshot
    ) {
        if (!policy.enabled) {
            recordSkip("policy_disabled")
            return
        }
        val now = System.currentTimeMillis()
        val target = findDownlineTarget(context, policy, snapshot, now)
        if (target == null) {
            recordSkip("downline_runtime_not_idle_or_not_eligible")
            return
        }
        if (!markInFlight(target.id, policy.scaleOutCooldownMs)) {
            return
        }
        val reason = "lifecycle_approved_$requestedAction"
        Logger.i(LOG_TAG, "downline PRoot capacity worker=${target.id} reason=$reason")
        BackgroundRuntimeHost.reclaimRuntime(context, target.id, reason)
        recordExecution(
            kind = "downline_bound_proot_runtime",
            targetId = target.id,
            reason = reason
        )
    }

    private fun recordQueueDelegated(requestedAction: String) {
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (lastExecutionKind == "queue_delegated_to_android_start_queue_plan" &&
                now - lastExecutionAtMs < QUEUE_DELEGATE_MIN_INTERVAL_MS
            ) {
                return
            }
            lastExecutionAtMs = now
            lastExecutionKind = "queue_delegated_to_android_start_queue_plan"
            lastTargetId = "android_start_queue_plan"
            lastReason = requestedAction
            lastSkipReason = "none"
            queueCreationRequestCount += 1
        }
    }

    private fun findCapacityRuntime(
        context: Context,
        runtimeId: String,
        maxProots: Int
    ): BackgroundRuntimeRecord? {
        val normalized = runtimeId.trim()
        val workers = BackgroundRuntimeHost.listRuntimes(context)
            .filter { it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER }
            .sortedWith(
                compareBy<BackgroundRuntimeRecord> { it.prootCapacityWorkerIndex() }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
        val record = if (
            normalized.isBlank() ||
            normalized == "none" ||
            normalized.equals("auto", ignoreCase = true) ||
            normalized.equals("auto_registered_proot_capacity_worker", ignoreCase = true)
        ) {
            workers
                .filter { it.prootCapacityWorkerIndex() in 2..maxProots.coerceAtLeast(1) }
                .firstOrNull { !it.isActiveRuntime() }
                ?: workers
                    .filter { it.prootCapacityWorkerIndex() in 2..maxProots.coerceAtLeast(1) }
                    .firstOrNull()
        } else {
            workers.firstOrNull { it.id == normalized }
        }
            ?: return null
        return record.takeIf { it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER }
    }

    private fun findDownlineTarget(
        context: Context,
        policy: RuntimeProotCapacityExecutorPolicy,
        snapshot: RuntimeHealthSnapshot,
        now: Long
    ): BackgroundRuntimeRecord? {
        if (!snapshot.globalStartQueueEmpty()) {
            idleSinceByTargetId.clear()
            return null
        }
        val targetIds = policy.downlineRuntimeIds.takeIf { it.isNotEmpty() }
            ?: policy.expansionRuntimeIds.takeIf { it.isNotEmpty() }?.toSet()
            ?: setOf("auto_registered_proot_capacity_worker")
        val workers = BackgroundRuntimeHost.listRuntimes(context)
            .filter { it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER }
            .filter { it.prootCapacityWorkerIndex() > 1 }
            .filter { it.isActiveRuntime() }
            .filter { worker ->
                targetIds.any { targetId ->
                    targetId.isAutoCapacityTarget() || targetId.trim() == worker.id
                }
            }
            .sortedWith(
                compareByDescending<BackgroundRuntimeRecord> { it.prootCapacityWorkerIndex() }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
        return workers.firstOrNull { worker ->
            worker.isIdleDownlineEligible(snapshot, policy, now)
        }
    }

    private fun RuntimeHealthSnapshot.globalStartQueueEmpty(): Boolean {
        return startQueuePlan.wouldQueueCount == 0 &&
            startQueuePlan.deferUntilPressureEasesCount == 0 &&
            startQueuePlan.foregroundRequiredCount == 0 &&
            startQueuePlan.blockedNoCapacityCount == 0 &&
            startQueuePlan.dryRunBacklogCount == 0
    }

    private fun BackgroundRuntimeRecord.isIdleDownlineEligible(
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeProotCapacityExecutorPolicy,
        now: Long
    ): Boolean {
        val startedAt = lastStartedAt ?: return false
        if (now - startedAt < policy.minLifetimeMs.coerceAtLeast(0L)) {
            idleSinceByTargetId.remove(id)
            return false
        }
        val root = snapshot.backgroundRuntimeRoot(id) ?: return false
        val inferredActiveTasks = (root.processCount - CAPACITY_WORKER_IDLE_BASELINE_PROCESSES)
            .coerceAtLeast(0)
        if (inferredActiveTasks > 0) {
            idleSinceByTargetId.remove(id)
            return false
        }
        val idleSince = idleSinceByTargetId.getOrPut(id) { now }
        return now - idleSince >= policy.idleGraceMs.coerceAtLeast(0L)
    }

    @Synchronized
    private fun markInFlight(targetId: String, cooldownMs: Long): Boolean {
        val now = System.currentTimeMillis()
        pruneInFlight(now, cooldownMs)
        if (inFlightTargetIds.containsKey(targetId)) {
            skippedInFlightCount += 1
            lastSkipReason = "target_in_flight:$targetId"
            return false
        }
        inFlightTargetIds[targetId] = now
        return true
    }

    @Synchronized
    private fun recordExecution(kind: String, targetId: String, reason: String) {
        lastExecutionAtMs = System.currentTimeMillis()
        lastExecutionKind = kind
        lastTargetId = targetId
        lastReason = reason
        lastSkipReason = "none"
        when (kind) {
            "start_bound_next_proot_runtime",
            "start_bound_second_proot_runtime" -> secondProotStartRequestCount += 1
            "downline_bound_proot_runtime" -> prootDownlineRequestCount += 1
        }
    }

    @Synchronized
    private fun recordSkip(reason: String) {
        lastSkipReason = reason
    }

    private fun pruneInFlight(now: Long) {
        pruneInFlight(now, IN_FLIGHT_TTL_MS)
    }

    private fun pruneInFlight(now: Long, cooldownMs: Long) {
        val ttl = maxOf(IN_FLIGHT_TTL_MS, cooldownMs.coerceAtLeast(0L))
        val expired = inFlightTargetIds
            .filterValues { startedAt -> now - startedAt > ttl }
            .keys
        expired.forEach(inFlightTargetIds::remove)
    }
}

private fun String.isAutoCapacityTarget(): Boolean {
    val normalized = trim()
    return normalized.isBlank() ||
        normalized == "none" ||
        normalized.equals("auto", ignoreCase = true) ||
        normalized.equals("auto_registered_proot_capacity_worker", ignoreCase = true)
}

private fun BackgroundRuntimeRecord.prootCapacityWorkerIndex(): Int {
    return id.substringAfterLast("-proot-capacity-worker-", "")
        .toIntOrNull()
        ?.takeIf { it > 0 }
        ?: Int.MAX_VALUE
}

private fun String?.toProotCapacityActuatorEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
