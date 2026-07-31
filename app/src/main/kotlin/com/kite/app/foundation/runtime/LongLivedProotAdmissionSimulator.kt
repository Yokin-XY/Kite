package com.kite.app.foundation.runtime

internal data class LongLivedProotAdmissionPolicy(
    val globalMax: Int = 2,
    val lanes: List<RuntimeLanePolicy> = RuntimeWorkloadPolicy.defaultLanes(),
    val pressure: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val foreground: Boolean = true,
)

internal enum class LongLivedProotRequestDisposition {
    CREATED,
    EXISTING,
    SPEC_CONFLICT,
}

internal data class LongLivedProotRequestResult(
    val disposition: LongLivedProotRequestDisposition,
    val record: LongLivedProotLeaseRecord,
)

internal data class LongLivedProotAdmissionSnapshot(
    val scope: String = "planned_not_production",
    val pressure: RuntimePressureLevel,
    val configuredGlobalMax: Int,
    val effectiveGlobalMax: Int,
    val activeCount: Int,
    val queuedCount: Int,
    val activeExclusiveMaintenance: Boolean,
    val queuedExclusiveMaintenance: Boolean,
    val activeByLane: Map<RuntimeLaneKind, Int>,
    val queuedByLane: Map<RuntimeLaneKind, Int>,
)

/**
 * 长期 PRoot owner 的确定性模型。
 *
 * 它不创建线程或进程，不持久化，也不接入任何生产 Store。调用方逐步输入事件，用于在生产迁移前验证
 * 容量、压力、维护屏障、owner 去重和 lane 公平性。
 */
internal class LongLivedProotAdmissionSimulator(
    initialPolicy: LongLivedProotAdmissionPolicy = LongLivedProotAdmissionPolicy(),
) {
    private data class QueuedOwner(
        val sequence: Long,
        val owner: LongLivedProotOwnerKey,
    )

    private val records = linkedMapOf<LongLivedProotOwnerKey, LongLivedProotLeaseRecord>()
    private val pending = mutableListOf<QueuedOwner>()
    private var policy = initialPolicy.normalized()
    private var nextSequence = 0L
    private var lastEventAtMs = -1L

    fun request(
        spec: LongLivedProotLeaseSpec,
        nowMs: Long,
    ): LongLivedProotRequestResult {
        acceptEventTime(nowMs)
        val existing = records[spec.owner]
        if (existing != null && existing.phase != LongLivedProotLeasePhase.RELEASED) {
            val disposition = if (existing.spec == spec) {
                LongLivedProotRequestDisposition.EXISTING
            } else {
                LongLivedProotRequestDisposition.SPEC_CONFLICT
            }
            return LongLivedProotRequestResult(disposition, existing)
        }

        val sequence = ++nextSequence
        val requested = LongLivedProotOwnerLeaseTransitions.requested(
            leaseId = "planned-long-lived-$sequence",
            generation = (existing?.generation ?: 0L) + 1L,
            spec = spec,
            nowMs = nowMs,
        )
        records[spec.owner] = requested
        pending += QueuedOwner(sequence, spec.owner)
        drainAdmissions(nowMs)
        return LongLivedProotRequestResult(
            disposition = LongLivedProotRequestDisposition.CREATED,
            record = requireNotNull(records[spec.owner]),
        )
    }

    fun updatePolicy(updated: LongLivedProotAdmissionPolicy, nowMs: Long) {
        acceptEventTime(nowMs)
        policy = updated.normalized()
        drainAdmissions(nowMs)
    }

    fun beginStart(owner: LongLivedProotOwnerKey, nowMs: Long): LongLivedProotLeaseTransition =
        transition(owner, nowMs) { record -> LongLivedProotOwnerLeaseTransitions.beginStart(record, nowMs) }

    fun attachProcess(
        owner: LongLivedProotOwnerKey,
        identity: LongLivedProotProcessIdentity,
        nowMs: Long,
    ): LongLivedProotLeaseTransition = transition(owner, nowMs) { record ->
        LongLivedProotOwnerLeaseTransitions.attachProcess(record, identity, nowMs)
    }

    fun startFailed(owner: LongLivedProotOwnerKey, nowMs: Long): LongLivedProotLeaseTransition =
        transition(owner, nowMs) { record -> LongLivedProotOwnerLeaseTransitions.startFailed(record, nowMs) }

    fun beginStop(owner: LongLivedProotOwnerKey, nowMs: Long): LongLivedProotLeaseTransition =
        transition(owner, nowMs) { record -> LongLivedProotOwnerLeaseTransitions.beginStop(record, nowMs) }

    fun observeProcessLost(owner: LongLivedProotOwnerKey, nowMs: Long): LongLivedProotLeaseTransition =
        transition(owner, nowMs) { record -> LongLivedProotOwnerLeaseTransitions.observeProcessLost(record, nowMs) }

    fun reconcileAlive(
        owner: LongLivedProotOwnerKey,
        identity: LongLivedProotProcessIdentity,
        nowMs: Long,
    ): LongLivedProotLeaseTransition = transition(owner, nowMs) { record ->
        LongLivedProotOwnerLeaseTransitions.reconcileAlive(record, identity, nowMs)
    }

    fun confirmStopped(owner: LongLivedProotOwnerKey, nowMs: Long): LongLivedProotLeaseTransition =
        transition(owner, nowMs) { record -> LongLivedProotOwnerLeaseTransitions.confirmStopped(record, nowMs) }

    fun confirmDead(owner: LongLivedProotOwnerKey, nowMs: Long): LongLivedProotLeaseTransition =
        transition(owner, nowMs) { record -> LongLivedProotOwnerLeaseTransitions.confirmDead(record, nowMs) }

    fun cancelBeforeStart(owner: LongLivedProotOwnerKey, nowMs: Long): LongLivedProotLeaseTransition =
        transition(owner, nowMs) { record -> LongLivedProotOwnerLeaseTransitions.cancelBeforeStart(record, nowMs) }

    fun record(owner: LongLivedProotOwnerKey): LongLivedProotLeaseRecord? = records[owner]

    fun allRecords(): List<LongLivedProotLeaseRecord> = records.values.toList()

    fun snapshot(): LongLivedProotAdmissionSnapshot {
        val active = activeRecords()
        val queued = pending.mapNotNull { records[it.owner] }
        return LongLivedProotAdmissionSnapshot(
            pressure = policy.pressure,
            configuredGlobalMax = policy.globalMax,
            effectiveGlobalMax = policy.effectiveGlobalMax(),
            activeCount = active.size,
            queuedCount = queued.size,
            activeExclusiveMaintenance = active.any { it.spec.isExclusiveMaintenance() },
            queuedExclusiveMaintenance = queued.any { it.spec.isExclusiveMaintenance() },
            activeByLane = RuntimeLaneKind.entries.associateWith { lane ->
                active.count { it.spec.lane == lane }
            },
            queuedByLane = RuntimeLaneKind.entries.associateWith { lane ->
                queued.count { it.spec.lane == lane }
            },
        )
    }

    private fun transition(
        owner: LongLivedProotOwnerKey,
        nowMs: Long,
        action: (LongLivedProotLeaseRecord) -> LongLivedProotLeaseTransition,
    ): LongLivedProotLeaseTransition {
        acceptEventTime(nowMs)
        val current = requireNotNull(records[owner]) { "long_lived_simulator_owner_unknown" }
        val result = action(current)
        if (!result.accepted || !result.changed) return result

        records[owner] = result.record
        if (result.record.phase == LongLivedProotLeasePhase.RELEASED) {
            pending.removeAll { it.owner == owner }
            drainAdmissions(nowMs)
        }
        return result
    }

    private fun drainAdmissions(nowMs: Long) {
        while (true) {
            val candidate = selectedRunnableOwner() ?: return
            val current = records[candidate.owner]
            if (current == null) {
                pending.remove(candidate)
                continue
            }
            val admitted = LongLivedProotOwnerLeaseTransitions.admit(current, nowMs)
            if (!admitted.accepted) return
            records[candidate.owner] = admitted.record
            pending.remove(candidate)
        }
    }

    private fun selectedRunnableOwner(): QueuedOwner? {
        val active = activeRecords()
        if (active.size >= policy.effectiveGlobalMax()) return null
        if (active.any { it.spec.isExclusiveMaintenance() }) return null

        val barrierSequence = pending
            .filter { queued ->
                records[queued.owner]?.spec?.let { spec ->
                    spec.isExclusiveMaintenance() && !policy.blocksNewAdmission(spec)
                } == true
            }
            .minOfOrNull { it.sequence }

        return pending
            .asSequence()
            .filter { candidate -> barrierSequence == null || candidate.sequence <= barrierSequence }
            .sortedWith(
                compareBy<QueuedOwner> { candidate -> effectivePriority(requireRecord(candidate).spec) }
                    .thenBy { it.sequence }
            )
            .firstOrNull { candidate -> canAdmit(requireRecord(candidate), active) }
    }

    private fun canAdmit(
        candidate: LongLivedProotLeaseRecord,
        active: List<LongLivedProotLeaseRecord>,
    ): Boolean {
        if (candidate.phase != LongLivedProotLeasePhase.REQUESTED) return false
        if (policy.blocksNewAdmission(candidate.spec)) return false
        if (candidate.spec.isExclusiveMaintenance()) return active.isEmpty()

        val lanePolicy = policy.lane(candidate.spec.lane)
        val laneMax = if (policy.foreground) lanePolicy.maxConcurrency else lanePolicy.backgroundMaxConcurrency
        val laneActive = active.count { it.spec.lane == candidate.spec.lane }
        return laneMax > 0 && laneActive < laneMax && (!lanePolicy.serial || laneActive == 0)
    }

    private fun activeRecords(): List<LongLivedProotLeaseRecord> =
        records.values.filter { it.holdsCapacity }

    private fun requireRecord(candidate: QueuedOwner): LongLivedProotLeaseRecord =
        requireNotNull(records[candidate.owner]) { "long_lived_simulator_queue_record_missing" }

    private fun effectivePriority(spec: LongLivedProotLeaseSpec): Int {
        val lanePriority = policy.lane(spec.lane).priority
        return if (spec.isExclusiveMaintenance()) minOf(lanePriority, 10) else lanePriority
    }

    private fun LongLivedProotAdmissionPolicy.blocksNewAdmission(
        spec: LongLivedProotLeaseSpec,
    ): Boolean = !spec.pressureEssential && (
        pressure == RuntimePressureLevel.HIGH || pressure == RuntimePressureLevel.CRITICAL
        )

    private fun LongLivedProotAdmissionPolicy.effectiveGlobalMax(): Int = when (pressure) {
        RuntimePressureLevel.UNKNOWN -> 1
        RuntimePressureLevel.NORMAL -> globalMax
        RuntimePressureLevel.ELEVATED -> minOf(globalMax, 2)
        RuntimePressureLevel.HIGH,
        RuntimePressureLevel.CRITICAL -> 1
    }

    private fun LongLivedProotAdmissionPolicy.lane(kind: RuntimeLaneKind): RuntimeLanePolicy =
        lanes.first { it.lane == kind }

    private fun LongLivedProotAdmissionPolicy.normalized(): LongLivedProotAdmissionPolicy {
        val supplied = lanes.associateBy { it.lane }
        return copy(
            globalMax = globalMax.coerceIn(1, 8),
            lanes = RuntimeWorkloadPolicy.defaultLanes().map { fallback ->
                (supplied[fallback.lane] ?: fallback).let { lane ->
                    lane.copy(
                        maxConcurrency = lane.maxConcurrency.coerceIn(0, 32),
                        backgroundMaxConcurrency = lane.backgroundMaxConcurrency.coerceIn(0, 32),
                        priority = lane.priority.coerceIn(0, 10_000),
                    )
                }
            },
        )
    }

    private fun LongLivedProotLeaseSpec.isExclusiveMaintenance(): Boolean =
        filesystemPosture == LongLivedProotFilesystemPosture.EXCLUSIVE_MAINTENANCE

    private fun acceptEventTime(nowMs: Long) {
        require(nowMs >= 0L && nowMs >= lastEventAtMs) { "long_lived_simulator_time_regressed" }
        lastEventAtMs = nowMs
    }
}
