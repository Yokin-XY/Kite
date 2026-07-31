package com.kite.app.foundation.runtime

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal enum class ProotJobAccess {
    READ_ONLY,
    SHARED_WRITE,
}

internal enum class ProotJobCancellationMode {
    /** 任务没有独立交互入口，由调用方的 timeout 和运行 owner 完成回收。 */
    TIMEOUT_AND_OWNER,

    /** 用户通过终端会话发起取消，终端 owner 负责回收进程树。 */
    TERMINAL_SESSION,

    /** 长期进程通过受管服务或 Agent owner 停止。 */
    MANAGED_OWNER,
}

internal enum class ProotJobResultMode {
    CAPTURED_STDIO,
    TERMINAL_SESSION,
    DETACHED_BINDING,
    MANAGED_CHANNEL,
}

internal data class ProotJobAdmissionRequest(
    val jobId: String,
    val ownerId: String,
    val lane: RuntimeLaneKind,
    val access: ProotJobAccess = ProotJobAccess.READ_ONLY,
    val cancellationMode: ProotJobCancellationMode,
    val resultMode: ProotJobResultMode,
    val pressureEssential: Boolean = false,
    val waitTimeoutMs: Long = 10_000L,
)

internal data class ProotJobAdmissionPolicy(
    val profileGroup: RuntimeLifecyclePolicyProfileGroup =
        RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
    val lanes: List<RuntimeLanePolicy> = RuntimeWorkloadPolicy.defaultLanes(),
    val pressure: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val foreground: Boolean = true,
    /** 仅供受控标定覆盖；正式策略保持 null，并由档位与 lane policy 决定。 */
    val globalMaxOverride: Int? = null,
)

internal data class ProotJobAdmissionSnapshot(
    val profileGroup: RuntimeLifecyclePolicyProfileGroup,
    val pressure: RuntimePressureLevel,
    val effectiveGlobalMax: Int,
    val activeCount: Int,
    val queuedCount: Int,
    val activeSharedWrite: Boolean,
    val admittedCount: Long,
    val cancelledCount: Long,
    val timedOutCount: Long,
    val maxObservedActive: Int,
)

internal sealed interface ProotJobAdmissionResult {
    data class Granted(val lease: ProotJobAdmissionLease) : ProotJobAdmissionResult
    data class Rejected(val reason: String) : ProotJobAdmissionResult
}

internal class ProotJobAdmissionLease internal constructor(
    val request: ProotJobAdmissionRequest,
    private val releaseAction: () -> Unit,
) : AutoCloseable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) releaseAction()
    }
}

/**
 * Android 持有的、供已迁移 PRoot job 共用的唯一准入原语。
 *
 * 它只决定“何时允许开始”，不执行命令、不拥有业务结果，也不杀死已经准入的任务。现有 lane policy
 * 是唯一并发规则来源；压力升高只收缩后续准入。共享写任务与所有已登记 job 互斥。
 */
internal class ProotJobAdmissionController(
    initialPolicy: ProotJobAdmissionPolicy = ProotJobAdmissionPolicy(),
    private val monotonicNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private data class Waiter(
        val sequence: Long,
        val enqueuedAtNanos: Long,
        val request: ProotJobAdmissionRequest,
        var cancelled: Boolean = false,
    )

    private data class Active(
        val sequence: Long,
        val request: ProotJobAdmissionRequest,
    )

    private val lock = ReentrantLock(true)
    private val changed = lock.newCondition()
    private val pending = mutableListOf<Waiter>()
    private val active = linkedMapOf<Long, Active>()

    @Volatile
    private var policy: ProotJobAdmissionPolicy = initialPolicy.normalized()

    private var nextSequence = 0L
    private var admittedCount = 0L
    private var cancelledCount = 0L
    private var timedOutCount = 0L
    private var maxObservedActive = 0
    private var closed = false

    fun updatePolicy(updated: ProotJobAdmissionPolicy) {
        lock.withLock {
            policy = updated.normalized()
            changed.signalAll()
        }
    }

    fun acquireBlocking(request: ProotJobAdmissionRequest): ProotJobAdmissionResult {
        validate(request)
        val waiter = lock.withLock {
            if (closed) return ProotJobAdmissionResult.Rejected("admission_closed")
            if (pending.any { it.request.jobId == request.jobId } || active.values.any {
                    it.request.jobId == request.jobId
                }
            ) {
                return ProotJobAdmissionResult.Rejected("admission_job_id_conflict")
            }
            Waiter(++nextSequence, monotonicNanos(), request).also(pending::add)
        }
        val deadline = waiter.enqueuedAtNanos + TimeUnit.MILLISECONDS.toNanos(request.waitTimeoutMs)

        return lock.withLock {
            while (true) {
                if (waiter.cancelled) {
                    return@withLock ProotJobAdmissionResult.Rejected("admission_cancelled")
                }
                if (closed) {
                    pending.remove(waiter)
                    return@withLock ProotJobAdmissionResult.Rejected("admission_closed")
                }
                if (canAdmit(waiter)) {
                    pending.remove(waiter)
                    active[waiter.sequence] = Active(waiter.sequence, request)
                    admittedCount += 1L
                    maxObservedActive = maxOf(maxObservedActive, active.size)
                    return@withLock ProotJobAdmissionResult.Granted(
                        ProotJobAdmissionLease(request) { release(waiter.sequence) }
                    )
                }
                val remaining = deadline - monotonicNanos()
                if (remaining <= 0L) {
                    pending.remove(waiter)
                    timedOutCount += 1L
                    changed.signalAll()
                    return@withLock ProotJobAdmissionResult.Rejected(blockedReason(waiter))
                }
                try {
                    changed.awaitNanos(remaining)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    pending.remove(waiter)
                    changed.signalAll()
                    return@withLock ProotJobAdmissionResult.Rejected("admission_interrupted")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            ProotJobAdmissionResult.Rejected("admission_loop_ended")
        }
    }

    /** 只取消尚未准入的任务；已经开始的任务必须交回其声明的运行 owner。 */
    fun cancelQueued(jobId: String): Boolean = lock.withLock {
        val waiter = pending.firstOrNull { it.request.jobId == jobId } ?: return@withLock false
        pending.remove(waiter)
        waiter.cancelled = true
        cancelledCount += 1L
        changed.signalAll()
        true
    }

    fun snapshot(): ProotJobAdmissionSnapshot = lock.withLock {
        ProotJobAdmissionSnapshot(
            profileGroup = policy.profileGroup,
            pressure = policy.pressure,
            effectiveGlobalMax = policy.effectiveGlobalMax(),
            activeCount = active.size,
            queuedCount = pending.size,
            activeSharedWrite = active.values.any { it.request.access == ProotJobAccess.SHARED_WRITE },
            admittedCount = admittedCount,
            cancelledCount = cancelledCount,
            timedOutCount = timedOutCount,
            maxObservedActive = maxObservedActive,
        )
    }

    override fun close() {
        lock.withLock {
            closed = true
            changed.signalAll()
        }
    }

    private fun release(sequence: Long) {
        lock.withLock {
            active.remove(sequence)
            changed.signalAll()
        }
    }

    private fun canAdmit(waiter: Waiter): Boolean {
        if (selectedWaiter() !== waiter) return false
        val currentPolicy = policy
        if (blockedByPressure(waiter.request, currentPolicy)) return false
        if (active.size >= currentPolicy.effectiveGlobalMax()) return false
        if (active.values.any { it.request.access == ProotJobAccess.SHARED_WRITE }) return false
        if (waiter.request.access == ProotJobAccess.SHARED_WRITE && active.isNotEmpty()) return false

        val lanePolicy = currentPolicy.lane(waiter.request.lane)
        val laneActive = active.values.count { it.request.lane == waiter.request.lane }
        val laneMax = if (currentPolicy.foreground) lanePolicy.maxConcurrency else lanePolicy.backgroundMaxConcurrency
        if (laneMax <= 0 || laneActive >= laneMax) return false
        if (lanePolicy.serial && laneActive > 0) return false
        return true
    }

    /**
     * 从按优先级排序的队列中选择当前真正能推进的任务，避免某个已满 lane 阻塞其他空闲 lane。
     * 共享写任务是有意保留的队首屏障：它一旦排到前面，就不再接纳新读任务，避免持续读流量令写任务饥饿。
     */
    private fun selectedWaiter(): Waiter? {
        val currentPolicy = policy
        return pending.sortedWith(waiterComparator()).firstOrNull { candidate ->
            if (blockedByPressure(candidate.request, currentPolicy)) return@firstOrNull false
            if (active.size >= currentPolicy.effectiveGlobalMax()) return@firstOrNull true
            if (active.values.any { it.request.access == ProotJobAccess.SHARED_WRITE }) {
                return@firstOrNull true
            }
            if (candidate.request.access == ProotJobAccess.SHARED_WRITE) return@firstOrNull true

            val lanePolicy = currentPolicy.lane(candidate.request.lane)
            val laneActive = active.values.count { it.request.lane == candidate.request.lane }
            val laneMax = if (currentPolicy.foreground) {
                lanePolicy.maxConcurrency
            } else {
                lanePolicy.backgroundMaxConcurrency
            }
            laneMax > 0 && laneActive < laneMax && (!lanePolicy.serial || laneActive == 0)
        }
    }

    private fun blockedByPressure(
        request: ProotJobAdmissionRequest,
        currentPolicy: ProotJobAdmissionPolicy,
    ): Boolean {
        if (request.pressureEssential) return false
        val lowPriority = currentPolicy.lane(request.lane).priority >=
            currentPolicy.lane(RuntimeLaneKind.BUILD).priority
        return lowPriority && (
            currentPolicy.pressure == RuntimePressureLevel.HIGH ||
                currentPolicy.pressure == RuntimePressureLevel.CRITICAL
            )
    }

    private fun blockedReason(waiter: Waiter): String {
        val currentPolicy = policy
        return when {
            blockedByPressure(waiter.request, currentPolicy) ->
                "admission_pressure_${currentPolicy.pressure.name.lowercase()}"
            currentPolicy.effectiveGlobalMax() <= active.size -> "admission_global_capacity_timeout"
            active.values.any { it.request.access == ProotJobAccess.SHARED_WRITE } ->
                "admission_shared_write_active"
            waiter.request.access == ProotJobAccess.SHARED_WRITE && active.isNotEmpty() ->
                "admission_shared_write_waiting_for_exclusive"
            else -> "admission_lane_capacity_timeout"
        }
    }

    private fun waiterComparator(): Comparator<Waiter> =
        compareBy<Waiter> { waiter -> effectivePriority(waiter.request) }
            .thenBy { it.sequence }

    private fun effectivePriority(request: ProotJobAdmissionRequest): Int {
        val lanePriority = policy.lane(request.lane).priority
        return if (request.access == ProotJobAccess.SHARED_WRITE) minOf(lanePriority, 10) else lanePriority
    }

    private fun ProotJobAdmissionPolicy.effectiveGlobalMax(): Int {
        val profileMax = globalMaxOverride
            ?: ProotPerformanceTunings.resolve(profileGroup, lanes).configuredGlobalMax
        return when (pressure) {
            RuntimePressureLevel.UNKNOWN -> 1
            RuntimePressureLevel.NORMAL -> profileMax
            RuntimePressureLevel.ELEVATED -> minOf(profileMax, 2)
            RuntimePressureLevel.HIGH,
            RuntimePressureLevel.CRITICAL -> 1
        }
    }

    private fun ProotJobAdmissionPolicy.lane(kind: RuntimeLaneKind): RuntimeLanePolicy {
        return lanes.firstOrNull { it.lane == kind }
            ?: RuntimeWorkloadPolicy.defaultLanes().first { it.lane == kind }
    }

    private fun ProotJobAdmissionPolicy.normalized(): ProotJobAdmissionPolicy {
        val supplied = lanes.associateBy { it.lane }
        val merged = RuntimeWorkloadPolicy.defaultLanes().map { fallback ->
            (supplied[fallback.lane] ?: fallback).let { lane ->
                lane.copy(
                    maxConcurrency = lane.maxConcurrency.coerceIn(0, 32),
                    backgroundMaxConcurrency = lane.backgroundMaxConcurrency.coerceIn(0, 32),
                    priority = lane.priority.coerceIn(0, 10_000),
                )
            }
        }
        return copy(
            lanes = merged,
            globalMaxOverride = globalMaxOverride?.coerceIn(1, 8),
        )
    }

    private fun validate(request: ProotJobAdmissionRequest) {
        require(request.jobId.isNotBlank() && request.jobId.length <= 96) { "admission_job_id_invalid" }
        require(request.ownerId.isNotBlank() && request.ownerId.length <= 160) { "admission_owner_id_invalid" }
        require(request.waitTimeoutMs in 1L..600_000L) { "admission_wait_timeout_invalid" }
    }
}
