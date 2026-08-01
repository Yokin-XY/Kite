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

internal enum class WarmProotPolicySource {
    INITIAL_CONSERVATIVE,
    BOOTSTRAP_POLICY_FILES_HOST_MEMORY,
    RUNTIME_HEALTH,
}

internal data class WarmProotPolicyState(
    val source: WarmProotPolicySource,
    val policy: ProotJobAdmissionPolicy,
)

internal object WarmProotBootstrapPolicyResolver {
    fun resolve(
        profileGroup: RuntimeLifecyclePolicyProfileGroup,
        lanes: List<RuntimeLanePolicy>,
        pressure: RuntimePressureLevel,
    ): ProotJobAdmissionPolicy = ProotJobAdmissionPolicy(
        profileGroup = profileGroup,
        lanes = lanes.ifEmpty { RuntimeWorkloadPolicy.defaultLanes() },
        pressure = pressure,
        foreground = true,
    )

    fun load(context: Context): ProotJobAdmissionPolicy {
        val reclaimerPolicy = RuntimeReclaimerPolicyStore.load(context)
        val residentPolicy = RuntimeResidentPolicyStore.load(context)
        val workloadPolicy = RuntimeWorkloadPolicyStore.load(context)
        return resolve(
            profileGroup = inferRuntimeLifecyclePolicyProfileGroup(
                reclaimerProfile = reclaimerPolicy.activeProfile,
                residentProfile = residentPolicy.activeProfile,
            ),
            lanes = workloadPolicy.lanes,
            pressure = RuntimePressureGuard.evaluate(
                roots = emptyList(),
                reclaimerPolicy = reclaimerPolicy,
            ).level,
        )
    }
}

/** RuntimeHealth 永远可覆盖 bootstrap；迟到的 bootstrap 不得反向覆盖正式策略。 */
internal fun transitionWarmProotPolicy(
    current: WarmProotPolicyState,
    source: WarmProotPolicySource,
    policy: ProotJobAdmissionPolicy,
): WarmProotPolicyState {
    if (
        source == WarmProotPolicySource.BOOTSTRAP_POLICY_FILES_HOST_MEMORY &&
        current.source != WarmProotPolicySource.INITIAL_CONSERVATIVE
    ) {
        return current
    }
    return WarmProotPolicyState(source = source, policy = policy)
}

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
    val queueWaitMs: Long = 0L,
    val executeMs: Long = 0L,
    val totalMs: Long = 0L,
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
        val totalStartedNanos = monotonicNanos()
        val granted = when (val result = admission.acquireBlocking(admissionRequest)) {
            is ProotJobAdmissionResult.Granted -> result
            is ProotJobAdmissionResult.Rejected -> return timed(
                execution = WarmProotPoolExecution(
                    WarmProotExecutionRoute.ADMISSION_REJECTED,
                    execution = null,
                    reason = result.reason,
                ),
                totalStartedNanos = totalStartedNanos,
                executionStartedNanos = null,
            )
        }

        granted.lease.use {
            val identity = identityProvider()
                ?: return timed(
                    execution = runFallback(independentFallback, "runner_identity_unavailable"),
                    totalStartedNanos = totalStartedNanos,
                    executionStartedNanos = monotonicNanos(),
                )
            val slot = acquireSlot(identity, admissionRequest.waitTimeoutMs)
                ?: return timed(
                    execution = runFallback(independentFallback, "runner_slot_unavailable"),
                    totalStartedNanos = totalStartedNanos,
                    executionStartedNanos = monotonicNanos(),
                )
            val executionStartedNanos = monotonicNanos()
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
                return timed(
                    execution = runFallback(
                        independentFallback,
                        execution.failureReason.ifBlank { "runner_prestart_failed" },
                    ),
                    totalStartedNanos = totalStartedNanos,
                    executionStartedNanos = executionStartedNanos,
                )
            }
            return timed(
                execution = WarmProotPoolExecution(
                    route = if (execution.completed) {
                        WarmProotExecutionRoute.WARM_RUNNER
                    } else {
                        WarmProotExecutionRoute.RUNNER_FAILED_AFTER_START
                    },
                    execution = execution,
                    reason = execution.failureReason.ifBlank { "runner_completed" },
                ),
                totalStartedNanos = totalStartedNanos,
                executionStartedNanos = executionStartedNanos,
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

    private fun timed(
        execution: WarmProotPoolExecution,
        totalStartedNanos: Long,
        executionStartedNanos: Long?,
    ): WarmProotPoolExecution {
        val completedNanos = monotonicNanos()
        val totalMs = TimeUnit.NANOSECONDS.toMillis(
            (completedNanos - totalStartedNanos).coerceAtLeast(0L)
        )
        val queueWaitMs = executionStartedNanos?.let { started ->
            TimeUnit.NANOSECONDS.toMillis((started - totalStartedNanos).coerceAtLeast(0L))
        } ?: totalMs
        return execution.copy(
            queueWaitMs = queueWaitMs.coerceAtMost(totalMs),
            executeMs = (totalMs - queueWaitMs).coerceAtLeast(0L),
            totalMs = totalMs,
        )
    }
}

/** 第一批生产执行通道；所有 warm 与独立回退都经过同一个 admission lease。 */
internal object WarmProotExecutionCoordinator {
    private data class Holder(
        val pool: WarmProotRunnerPool,
    )

    private val lock = Any()
    private val policyLock = Any()
    private val sequence = AtomicLong(0L)
    private val admission = ProotJobAdmissionController()
    private val managedOwners = ManagedProotOwnerAdmissionRegistry(admission)

    @Volatile
    private var policyState = WarmProotPolicyState(
        source = WarmProotPolicySource.INITIAL_CONSERVATIVE,
        policy = ProotJobAdmissionPolicy(),
    )

    @Volatile
    private var holder: Holder? = null

    internal data class TuningSnapshot(
        val policySource: WarmProotPolicySource,
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
        val unifiedActualCapacity: ProotUnifiedActualHealthSnapshot =
            ProotUnifiedActualHealthSnapshot.empty(),
    )

    fun nextJobId(prefix: String): String =
        "${prefix.filter { it.isLetterOrDigit() || it in "-_.:@" }.take(48)}-${sequence.incrementAndGet()}"

    fun updateFrom(snapshot: RuntimeHealthSnapshot) {
        val surface = snapshot.lifecyclePolicyProfileSurface
        val formalPolicy = ProotJobAdmissionPolicy(
            profileGroup = surface.activeProfileGroup,
            lanes = surface.activeLanes.ifEmpty { RuntimeWorkloadPolicy.defaultLanes() },
            pressure = snapshot.pressure.level,
            foreground = snapshot.backgroundDecay.lifecycleState == RuntimeAppVisibilityState.FOREGROUND,
        )
        val pool = synchronized(policyLock) {
            policyState = transitionWarmProotPolicy(
                current = policyState,
                source = WarmProotPolicySource.RUNTIME_HEALTH,
                policy = formalPolicy,
            )
            admission.updatePolicy(policyState.policy)
            holder?.pool
        }
        pool?.trimTo(admission.snapshot().effectiveGlobalMax)
    }

    /**
     * 只供固定诊断矩阵在短时间内串行化 policy 更新；不选择策略，也不改变正式状态源。
     * block 内仍可调用 [updateFrom]（JVM monitor 可重入），退出后等待中的正式更新会继续接管。
     */
    internal fun <T> withPolicyUpdateBarrier(block: () -> T): T =
        synchronized(policyLock) { block() }

    fun executeBlocking(
        context: Context,
        admissionRequest: ProotJobAdmissionRequest,
        jobRequest: WarmProotJobRequest,
        onOutput: (WarmProotOutputStream, ByteArray) -> Unit = { _, _ -> },
        independentFallback: (() -> WarmProotJobExecution)? = null,
    ): WarmProotPoolExecution {
        val appContext = context.applicationContext
        ensureBootstrapPolicy(appContext)
        return holder(appContext).pool.executeBlocking(
            admissionRequest = admissionRequest,
            jobRequest = jobRequest,
            onOutput = onOutput,
            independentFallback = independentFallback,
        )
    }

    /** 长期 owner 与短任务共用同一个 actual admission；这里只持有容量句柄。 */
    fun acquireManagedOwnerBlocking(
        context: Context,
        request: ProotJobAdmissionRequest,
        generation: Long,
    ): ManagedProotOwnerAdmissionResult {
        ensureBootstrapPolicy(context.applicationContext)
        return managedOwners.acquireBlocking(request, generation)
    }

    /** 重建控制面时恢复持久 lease；允许形成 overcommitted，但不会驱逐既有 holder。 */
    fun restoreManagedOwner(
        context: Context,
        request: ProotJobAdmissionRequest,
        generation: Long,
    ): ManagedProotOwnerAdmissionResult {
        ensureBootstrapPolicy(context.applicationContext)
        return managedOwners.restore(request, generation)
    }

    fun releaseManagedOwner(ownerId: String, generation: Long): Boolean =
        managedOwners.release(ownerId, generation)

    fun clearManagedOwnerAdmissionBlock(ownerId: String) {
        admission.clearAdmissionBlock("managed-owner:${ownerId.trim()}")
    }

    fun reconcileManagedOwnerAdmissionBlocks(ownerIds: Set<String>) {
        admission.replaceAdmissionBlocks("managed-owner:", ownerIds)
    }

    fun managedOwnerSnapshot(): ManagedProotOwnerAdmissionSnapshot = managedOwners.snapshot()

    fun invalidate(reason: String) {
        holder?.pool?.invalidate(reason)
    }

    fun snapshot(): ProotJobAdmissionSnapshot = admission.snapshot()

    /** 从现有 policy、admission 和 pool 即时投影，不建立第二份状态。 */
    fun tuningSnapshot(): TuningSnapshot {
        val currentState = policyState
        val currentPolicy = currentState.policy
        val tuning = ProotPerformanceTunings.resolve(currentPolicy.profileGroup, currentPolicy.lanes)
        val admissionSnapshot = admission.snapshot()
        val poolSnapshot = holder?.pool?.snapshot() ?: WarmProotRunnerPoolSnapshot(0, 0, 0, 0, 0L)
        return TuningSnapshot(
            policySource = currentState.source,
            profileGroup = currentPolicy.profileGroup,
            pressure = currentPolicy.pressure,
            foreground = currentPolicy.foreground,
            configuredGlobalMax = tuning.configuredGlobalMax,
            effectiveGlobalMax = admissionSnapshot.effectiveGlobalMax,
            maxWarmRunners = tuning.maxWarmRunners,
            idleTimeoutMs = tuning.idleTimeoutMs,
            activeJobs = admissionSnapshot.activeCount - admissionSnapshot.managedOwnerActiveCount,
            queuedJobs = admissionSnapshot.queuedCount - admissionSnapshot.managedOwnerQueuedCount,
            totalWarmSessions = poolSnapshot.totalSessions,
            activeWarmSessions = poolSnapshot.activeSessions,
            idleWarmSessions = poolSnapshot.idleSessions,
            staleWarmSessions = poolSnapshot.staleSessions,
            oldestIdleAgeMs = poolSnapshot.oldestIdleAgeMs,
            unifiedActualCapacity = ProotUnifiedActualHealthProjection.project(admissionSnapshot),
        )
    }

    private fun ensureBootstrapPolicy(context: Context) {
        if (policyState.source != WarmProotPolicySource.INITIAL_CONSERVATIVE) return
        val bootstrapPolicy = WarmProotBootstrapPolicyResolver.load(context)
        val pool = synchronized(policyLock) {
            val updated = transitionWarmProotPolicy(
                current = policyState,
                source = WarmProotPolicySource.BOOTSTRAP_POLICY_FILES_HOST_MEMORY,
                policy = bootstrapPolicy,
            )
            if (updated === policyState) return@synchronized null
            policyState = updated
            admission.updatePolicy(updated.policy)
            holder?.pool
        }
        pool?.trimTo(admission.snapshot().effectiveGlobalMax)
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
                        policyState.policy.let { currentPolicy ->
                            WarmProotRunnerPoolTuning.forPolicy(
                                currentPolicy.profileGroup,
                                currentPolicy.lanes,
                            )
                        }
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

/** 正式健康面只投影低基数调度事实，不暴露任务身份、命令、路径、环境或输出。 */
internal fun WarmProotExecutionCoordinator.TuningSnapshot.toRuntimeHealthEnvText(): String = buildString {
    appendLine("proot_actual_scheduler_schema=bounded_proot_scheduler_v1")
    appendLine("proot_actual_scheduler_source=warm_proot_execution_coordinator")
    appendLine("proot_actual_scheduler_scope=actual_not_planned")
    appendLine("proot_actual_policy_source=${policySource.name}")
    appendLine("proot_actual_profile=${profileGroup.name}")
    appendLine("proot_actual_pressure=${pressure.name}")
    appendLine("proot_actual_foreground=$foreground")
    appendLine("proot_actual_configured_global_max=$configuredGlobalMax")
    appendLine("proot_actual_effective_global_max=$effectiveGlobalMax")
    appendLine("proot_actual_warm_runner_max=$maxWarmRunners")
    appendLine("proot_actual_idle_timeout_ms=$idleTimeoutMs")
    appendLine("proot_actual_active_jobs=$activeJobs")
    appendLine("proot_actual_queued_jobs=$queuedJobs")
    appendLine("proot_actual_warm_session_total=$totalWarmSessions")
    appendLine("proot_actual_warm_session_active=$activeWarmSessions")
    appendLine("proot_actual_warm_session_idle=$idleWarmSessions")
    appendLine("proot_actual_warm_session_stale=$staleWarmSessions")
    appendLine("proot_actual_oldest_idle_age_ms=$oldestIdleAgeMs")
    append(unifiedActualCapacity.toRuntimeHealthEnvText())
}
