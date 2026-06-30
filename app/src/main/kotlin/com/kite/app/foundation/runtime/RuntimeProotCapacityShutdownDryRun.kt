package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.BackgroundRuntimeRecord
import com.kite.app.foundation.service.isActiveRuntime

data class RuntimeProotCapacityShutdownDryRunDecision(
    val canShutdown: Boolean,
    val activeTasks: Int,
    val queueEmpty: Boolean,
    val ageMs: Long,
    val idleMs: Long,
    val releaseMemoryReservationOnShutdown: Boolean,
    val reason: String
)

object RuntimeProotCapacityShutdownDryRun {
    private const val CAPACITY_WORKER_IDLE_BASELINE_PROCESSES = 2

    fun evaluate(
        worker: BackgroundRuntimeRecord,
        root: RuntimeRootSnapshot?,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot,
        policy: RuntimeProotCapacityExecutorPolicy,
        now: Long,
        idleSinceAtMs: Long?
    ): RuntimeProotCapacityShutdownDryRunDecision {
        val queueEmpty = startQueuePlan.isGlobalStartQueueEmpty()
        val ageMs = worker.lastStartedAt?.let { now - it } ?: -1L
        val idleMs = idleSinceAtMs?.let { now - it } ?: -1L
        val activeTasks = root?.let(::activeTasksForRoot) ?: -1
        val reason = when {
            worker.kind != BackgroundRuntimeKind.PROOT_CAPACITY_WORKER ->
                "not_proot_capacity_worker"
            worker.prootCapacityWorkerIndexForShutdownDryRun() <= 1 ->
                "default_proot_1_is_resident"
            !worker.isActiveRuntime() ->
                "worker_not_active"
            !queueEmpty ->
                "global_queue_not_empty"
            worker.lastStartedAt == null ->
                "worker_start_time_unknown"
            ageMs < policy.minLifetimeMs.coerceAtLeast(0L) ->
                "min_lifetime_not_satisfied"
            root == null ->
                "runtime_root_not_observed"
            activeTasks > 0 ->
                "worker_has_active_tasks"
            idleSinceAtMs == null ->
                "idle_clock_not_started"
            idleMs < policy.idleGraceMs.coerceAtLeast(0L) ->
                "idle_grace_not_satisfied"
            else -> "idle_temporary_worker_can_shutdown"
        }
        val canShutdown = reason == "idle_temporary_worker_can_shutdown"
        return RuntimeProotCapacityShutdownDryRunDecision(
            canShutdown = canShutdown,
            activeTasks = activeTasks.coerceAtLeast(0),
            queueEmpty = queueEmpty,
            ageMs = ageMs,
            idleMs = idleMs,
            releaseMemoryReservationOnShutdown = canShutdown,
            reason = reason
        )
    }

    fun activeTasksForRoot(root: RuntimeRootSnapshot): Int {
        return (root.processCount - CAPACITY_WORKER_IDLE_BASELINE_PROCESSES).coerceAtLeast(0)
    }

    fun RuntimeStartQueuePlanDryRunSnapshot.isGlobalStartQueueEmpty(): Boolean {
        return wouldQueueCount == 0 &&
            deferUntilPressureEasesCount == 0 &&
            foregroundRequiredCount == 0 &&
            blockedNoCapacityCount == 0 &&
            dryRunBacklogCount == 0
    }
}

private fun BackgroundRuntimeRecord.prootCapacityWorkerIndexForShutdownDryRun(): Int {
    return id.substringAfterLast("-proot-capacity-worker-", "")
        .toIntOrNull()
        ?.takeIf { it > 0 }
        ?: Int.MAX_VALUE
}
