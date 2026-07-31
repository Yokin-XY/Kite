package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class WarmProotRunnerIdentity(
    val runtime: RuntimeLaunchPreparationIdentity,
    val runner: ManagedCommandHostFileStamp,
)

internal data class WarmProotRunnerPoolTuning(
    val maxWarmRunners: Int,
    val idleTimeoutMs: Long,
) {
    companion object {
        fun forPolicy(
            profile: RuntimeLifecyclePolicyProfileGroup,
            lanes: List<RuntimeLanePolicy>,
        ): WarmProotRunnerPoolTuning {
            val tuning = ProotPerformanceTunings.resolve(profile, lanes)
            return WarmProotRunnerPoolTuning(
                maxWarmRunners = tuning.maxWarmRunners,
                idleTimeoutMs = tuning.idleTimeoutMs,
            )
        }
    }
}

internal data class WarmProotRunnerPoolSnapshot(
    val totalSessions: Int,
    val activeSessions: Int,
    val idleSessions: Int,
    val staleSessions: Int,
    val oldestIdleAgeMs: Long,
)

internal enum class WarmProotExecutionRoute {
    WARM_RUNNER,
    INDEPENDENT_FALLBACK,
    ADMISSION_REJECTED,
    RUNNER_FAILED_AFTER_START,
    FALLBACK_FAILED,
}

internal data class WarmProotPoolExecution(
    val route: WarmProotExecutionRoute,
    val execution: WarmProotJobExecution?,
    val reason: String,
) {
    val succeeded: Boolean get() = execution?.succeeded == true
}

internal interface WarmProotJobSession : AutoCloseable {
    fun executeBlocking(
        request: WarmProotJobRequest,
        onOutput: (WarmProotOutputStream, ByteArray) -> Unit,
    ): WarmProotJobExecution

    fun isWarm(): Boolean
}

private class ControllerWarmProotJobSession(
    private val controller: WarmProotRunnerController,
) : WarmProotJobSession {
    override fun executeBlocking(
        request: WarmProotJobRequest,
        onOutput: (WarmProotOutputStream, ByteArray) -> Unit,
    ): WarmProotJobExecution = controller.executeBlocking(request, onOutput)

    override fun isWarm(): Boolean = controller.isWarm()
    override fun close() = controller.close()
}

/**
 * 复用具备相同 runtime/runner 身份的温热 PRoot。准入 lease 覆盖 warm 与独立回退的完整执行时间；
 * pool 只回收 session，不拥有业务状态，也不会自动重放 STARTED job。
 */
internal class WarmProotRunnerPool(
    private val admission: ProotJobAdmissionController,
    private val identityProvider: () -> WarmProotRunnerIdentity?,
    private val sessionFactory: () -> WarmProotJobSession,
    private val tuningProvider: () -> WarmProotRunnerPoolTuning,
    private val monotonicNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private data class Slot(
        val id: Long,
        val identity: WarmProotRunnerIdentity,
        val session: WarmProotJobSession,
        var inUse: Boolean,
        var stale: Boolean,
        var lastUsedNanos: Long,
    )

    private val lock = ReentrantLock(true)
    private val changed = lock.newCondition()
    private val slots = mutableListOf<Slot>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "WarmProotRunnerPoolIdleReaper").apply { isDaemon = true }
    }
    private var nextSlotId = 0L
    private var creatingCount = 0
    private var generation = 0L
    private var closed = false

    fun executeBlocking(
        admissionRequest: ProotJobAdmissionRequest,
        jobRequest: WarmProotJobRequest,
        onOutput: (WarmProotOutputStream, ByteArray) -> Unit = { _, _ -> },
        independentFallback: (() -> WarmProotJobExecution)? = null,
    ): WarmProotPoolExecution {
        require(admissionRequest.jobId == jobRequest.jobId) { "runner_pool_job_id_mismatch" }
        val granted = when (val result = admission.acquireBlocking(admissionRequest)) {
            is ProotJobAdmissionResult.Granted -> result
            is ProotJobAdmissionResult.Rejected -> return WarmProotPoolExecution(
                WarmProotExecutionRoute.ADMISSION_REJECTED,
                execution = null,
                reason = result.reason,
            )
        }

        granted.lease.use {
            val identity = identityProvider()
                ?: return runFallback(independentFallback, "runner_identity_unavailable")
            val slot = acquireSlot(identity, admissionRequest.waitTimeoutMs)
                ?: return runFallback(independentFallback, "runner_slot_unavailable")
            val execution = try {
                slot.session.executeBlocking(jobRequest, onOutput)
            } catch (error: Throwable) {
                WarmProotJobExecution(
                    jobId = jobRequest.jobId,
                    started = false,
                    failureKind = WarmProotRunnerFailureKind.START_FAILED,
                    failureReason = error.message ?: error.javaClass.simpleName,
                )
            }

            val keep = execution.completed && slot.session.isWarm()
            releaseSlot(slot, keep)
            if (execution.fallbackAllowed && !execution.completed) {
                return runFallback(independentFallback, execution.failureReason.ifBlank { "runner_prestart_failed" })
            }
            return WarmProotPoolExecution(
                route = if (execution.completed) {
                    WarmProotExecutionRoute.WARM_RUNNER
                } else {
                    WarmProotExecutionRoute.RUNNER_FAILED_AFTER_START
                },
                execution = execution,
                reason = execution.failureReason.ifBlank { "runner_completed" },
            )
        }
    }

    fun trimTo(maxIdleAndActive: Int) {
        val close = mutableListOf<WarmProotJobSession>()
        lock.withLock {
            val target = maxIdleAndActive.coerceAtLeast(0)
            slots.filter { !it.inUse }
                .sortedBy { it.lastUsedNanos }
                .take((slots.size - target).coerceAtLeast(0))
                .forEach { slot ->
                    slots.remove(slot)
                    close += slot.session
                }
            val excessActive = (slots.size - target).coerceAtLeast(0)
            slots.filter { it.inUse }
                .sortedBy { it.lastUsedNanos }
                .take(excessActive)
                .forEach { it.stale = true }
            changed.signalAll()
        }
        close.forEach { runCatching { it.close() } }
    }

    fun invalidate(@Suppress("UNUSED_PARAMETER") reason: String) {
        val close = mutableListOf<WarmProotJobSession>()
        lock.withLock {
            generation += 1L
            slots.forEach { slot ->
                if (slot.inUse) slot.stale = true else close += slot.session
            }
            slots.removeAll { !it.inUse }
            changed.signalAll()
        }
        close.forEach { runCatching { it.close() } }
    }

    fun snapshot(): WarmProotRunnerPoolSnapshot = lock.withLock {
        val nowNanos = monotonicNanos()
        WarmProotRunnerPoolSnapshot(
            totalSessions = slots.size,
            activeSessions = slots.count(Slot::inUse),
            idleSessions = slots.count { !it.inUse },
            staleSessions = slots.count(Slot::stale),
            oldestIdleAgeMs = slots.asSequence()
                .filter { !it.inUse }
                .map { TimeUnit.NANOSECONDS.toMillis((nowNanos - it.lastUsedNanos).coerceAtLeast(0L)) }
                .maxOrNull()
                ?: 0L,
        )
    }

    fun sessionCount(): Int = snapshot().totalSessions

    override fun close() {
        val close = lock.withLock {
            if (closed) return
            closed = true
            generation += 1L
            changed.signalAll()
            slots.map { it.session }.also { slots.clear() }
        }
        scheduler.shutdownNow()
        close.forEach { runCatching { it.close() } }
    }

    private fun acquireSlot(identity: WarmProotRunnerIdentity, waitTimeoutMs: Long): Slot? {
        val deadline = monotonicNanos() + TimeUnit.MILLISECONDS.toNanos(waitTimeoutMs)
        while (true) {
            trimExpired()
            var create = false
            var createGeneration = -1L
            var retired = emptyList<WarmProotJobSession>()
            val existing = lock.withLock {
                if (closed) return null
                val mismatched = slots.filter { it.identity != identity }
                mismatched.filter { it.inUse }.forEach { it.stale = true }
                val idleMismatched = mismatched.filter { !it.inUse }
                slots.removeAll(idleMismatched.toSet())
                retired = idleMismatched.map { it.session }
                slots.firstOrNull { !it.inUse && !it.stale && it.identity == identity }?.also {
                    it.inUse = true
                } ?: run {
                    val limit = tuningProvider().maxWarmRunners.coerceIn(1, 8)
                    if (slots.size + creatingCount < limit) {
                        creatingCount += 1
                        create = true
                        createGeneration = generation
                    }
                    null
                }
            }
            retired.forEach { runCatching { it.close() } }
            if (existing != null) return existing
            if (create) {
                val session = runCatching(sessionFactory).getOrNull()
                if (session == null) {
                    lock.withLock {
                        creatingCount -= 1
                        changed.signalAll()
                    }
                    return null
                }
                var discard = false
                val created = lock.withLock {
                    creatingCount -= 1
                    changed.signalAll()
                    if (closed || createGeneration != generation) {
                        discard = true
                        null
                    } else {
                        Slot(
                            id = ++nextSlotId,
                            identity = identity,
                            session = session,
                            inUse = true,
                            stale = false,
                            lastUsedNanos = monotonicNanos(),
                        ).also(slots::add)
                    }
                }
                if (discard) runCatching { session.close() }
                return created
            }
            val remaining = deadline - monotonicNanos()
            if (remaining <= 0L) return null
            lock.withLock {
                try {
                    changed.awaitNanos(remaining)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
    }

    private fun releaseSlot(slot: Slot, keep: Boolean) {
        var close: WarmProotJobSession? = null
        var idleTimeoutMs = 0L
        lock.withLock {
            val current = slots.firstOrNull { it.id == slot.id }
            if (current == null) {
                close = slot.session
            } else if (!keep || current.stale || closed) {
                slots.remove(current)
                close = current.session
            } else {
                current.inUse = false
                current.lastUsedNanos = monotonicNanos()
                idleTimeoutMs = tuningProvider().idleTimeoutMs.coerceIn(0L, 600_000L)
            }
            changed.signalAll()
        }
        close?.let { runCatching { it.close() } }
        if (close == null) scheduleIdleTrim(idleTimeoutMs)
    }

    private fun scheduleIdleTrim(idleTimeoutMs: Long) {
        if (idleTimeoutMs <= 0L) {
            trimExpired()
            return
        }
        runCatching {
            scheduler.schedule({ trimExpired() }, idleTimeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun trimExpired() {
        val close = mutableListOf<WarmProotJobSession>()
        lock.withLock {
            close += evictExpiredLocked(monotonicNanos())
            changed.signalAll()
        }
        close.forEach { runCatching { it.close() } }
    }

    private fun evictExpiredLocked(nowNanos: Long): List<WarmProotJobSession> {
        val idleNanos = TimeUnit.MILLISECONDS.toNanos(
            tuningProvider().idleTimeoutMs.coerceIn(0L, 600_000L)
        )
        val expired = slots.filter { slot ->
            !slot.inUse && (slot.stale || idleNanos == 0L || nowNanos - slot.lastUsedNanos >= idleNanos)
        }
        slots.removeAll(expired.toSet())
        return expired.map { it.session }
    }

    private fun runFallback(
        fallback: (() -> WarmProotJobExecution)?,
        reason: String,
    ): WarmProotPoolExecution {
        if (fallback == null) {
            return WarmProotPoolExecution(
                WarmProotExecutionRoute.FALLBACK_FAILED,
                execution = null,
                reason = "$reason:fallback_unavailable",
            )
        }
        val result = runCatching(fallback).getOrElse { error ->
            return WarmProotPoolExecution(
                WarmProotExecutionRoute.FALLBACK_FAILED,
                execution = null,
                reason = "$reason:${error.message ?: error.javaClass.simpleName}",
            )
        }
        return WarmProotPoolExecution(
            WarmProotExecutionRoute.INDEPENDENT_FALLBACK,
            execution = result,
            reason = reason,
        )
    }
}

/** 第一批生产执行通道；所有 warm 与独立回退都经过同一个 admission lease。 */
internal object WarmProotExecutionCoordinator {
    private data class Holder(
        val pool: WarmProotRunnerPool,
    )

    private val lock = Any()
    private val sequence = AtomicLong(0L)
    private val admission = ProotJobAdmissionController()

    @Volatile
    private var policy = ProotJobAdmissionPolicy()

    @Volatile
    private var holder: Holder? = null

    internal data class TuningSnapshot(
        val profileGroup: RuntimeLifecyclePolicyProfileGroup,
        val pressure: RuntimePressureLevel,
        val foreground: Boolean,
        val configuredGlobalMax: Int,
        val effectiveGlobalMax: Int,
        val maxWarmRunners: Int,
        val idleTimeoutMs: Long,
        val activeJobs: Int,
        val queuedJobs: Int,
        val totalWarmSessions: Int,
        val activeWarmSessions: Int,
        val idleWarmSessions: Int,
        val staleWarmSessions: Int,
        val oldestIdleAgeMs: Long,
    )

    fun nextJobId(prefix: String): String =
        "${prefix.filter { it.isLetterOrDigit() || it in "-_.:@" }.take(48)}-${sequence.incrementAndGet()}"

    fun updateFrom(snapshot: RuntimeHealthSnapshot) {
        val surface = snapshot.lifecyclePolicyProfileSurface
        policy = ProotJobAdmissionPolicy(
            profileGroup = surface.activeProfileGroup,
            lanes = surface.activeLanes.ifEmpty { RuntimeWorkloadPolicy.defaultLanes() },
            pressure = snapshot.pressure.level,
            foreground = snapshot.backgroundDecay.lifecycleState == RuntimeAppVisibilityState.FOREGROUND,
        )
        admission.updatePolicy(policy)
        holder?.pool?.trimTo(admission.snapshot().effectiveGlobalMax)
    }

    fun executeBlocking(
        context: Context,
        admissionRequest: ProotJobAdmissionRequest,
        jobRequest: WarmProotJobRequest,
        onOutput: (WarmProotOutputStream, ByteArray) -> Unit = { _, _ -> },
        independentFallback: (() -> WarmProotJobExecution)? = null,
    ): WarmProotPoolExecution = holder(context.applicationContext).pool.executeBlocking(
        admissionRequest = admissionRequest,
        jobRequest = jobRequest,
        onOutput = onOutput,
        independentFallback = independentFallback,
    )

    fun invalidate(reason: String) {
        holder?.pool?.invalidate(reason)
    }

    fun snapshot(): ProotJobAdmissionSnapshot = admission.snapshot()

    /** 从现有 policy、admission 和 pool 即时投影，不建立第二份状态。 */
    fun tuningSnapshot(): TuningSnapshot {
        val currentPolicy = policy
        val tuning = ProotPerformanceTunings.resolve(currentPolicy.profileGroup, currentPolicy.lanes)
        val admissionSnapshot = admission.snapshot()
        val poolSnapshot = holder?.pool?.snapshot() ?: WarmProotRunnerPoolSnapshot(0, 0, 0, 0, 0L)
        return TuningSnapshot(
            profileGroup = currentPolicy.profileGroup,
            pressure = currentPolicy.pressure,
            foreground = currentPolicy.foreground,
            configuredGlobalMax = tuning.configuredGlobalMax,
            effectiveGlobalMax = admissionSnapshot.effectiveGlobalMax,
            maxWarmRunners = tuning.maxWarmRunners,
            idleTimeoutMs = tuning.idleTimeoutMs,
            activeJobs = admissionSnapshot.activeCount,
            queuedJobs = admissionSnapshot.queuedCount,
            totalWarmSessions = poolSnapshot.totalSessions,
            activeWarmSessions = poolSnapshot.activeSessions,
            idleWarmSessions = poolSnapshot.idleSessions,
            staleWarmSessions = poolSnapshot.staleSessions,
            oldestIdleAgeMs = poolSnapshot.oldestIdleAgeMs,
        )
    }

    private fun holder(context: Context): Holder {
        holder?.let { return it }
        return synchronized(lock) {
            holder?.let { return@synchronized it }
            Holder(
                pool = WarmProotRunnerPool(
                    admission = admission,
                    identityProvider = { identity(context) },
                    sessionFactory = {
                        ControllerWarmProotJobSession(
                            WarmProotRunnerController(
                                processFactory = { WorkSurfaceRuntimeBridge.startWarmProotRunnerProcess(context) }
                            )
                        )
                    },
                    tuningProvider = {
                        WarmProotRunnerPoolTuning.forPolicy(policy.profileGroup, policy.lanes)
                    },
                )
            ).also { holder = it }
        }
    }

    private fun identity(context: Context): WarmProotRunnerIdentity? {
        val basis = WorkSurfaceRuntimeBridge.managedCommandVerificationBasis(
            context = context,
            commands = listOf("kf-runner"),
        ) ?: run {
            holder?.pool?.invalidate("runner_identity_unavailable")
            return null
        }
        return WarmProotRunnerIdentity(
            runtime = basis.runtimeIdentity,
            runner = basis.commandFiles.singleOrNull() ?: return null,
        )
    }
}
