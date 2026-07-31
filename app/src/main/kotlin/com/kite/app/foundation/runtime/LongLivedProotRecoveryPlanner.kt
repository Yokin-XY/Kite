package com.kite.app.foundation.runtime

internal enum class LongLivedProotProcessMatch {
    NOT_APPLICABLE,
    EXACT_GENERATION,
    PID_REUSED,
    NOT_FOUND,
    IDENTITY_NOT_PERSISTED,
}

internal enum class LongLivedProotRecoveryAction {
    RESTORED_QUEUED,
    RELEASED_UNSTARTED_ADMISSION,
    HELD_START_REVIEW,
    RESTORED_RUNNING,
    RESTORED_STOPPING,
    MOVED_TO_ORPHAN_REVIEW,
    REMAINED_IN_ORPHAN_REVIEW,
    ALREADY_RELEASED,
    DUPLICATE_CONFLICT_REVIEW,
    PROCESS_IDENTITY_CONFLICT_REVIEW,
}

internal data class LongLivedProotRecoveryDecision(
    val record: LongLivedProotLeaseRecord,
    val action: LongLivedProotRecoveryAction,
    val processMatch: LongLivedProotProcessMatch,
    val discardedOlderGenerations: Int = 0,
    val collapsedExactDuplicates: Int = 0,
)

internal data class LongLivedProotRecoveryPlan(
    val scope: String = "planned_not_production",
    val decisions: List<LongLivedProotRecoveryDecision>,
    val processStartsRequested: Int = 0,
) {
    init {
        require(processStartsRequested == 0) { "long_lived_recovery_must_not_start_process" }
        require(decisions.map { it.record.spec.owner }.distinct().size == decisions.size) {
            "long_lived_recovery_duplicate_owner"
        }
    }
}

/** 纯恢复规划；观察值由未来生产 owner 提供，本类不读取 /proc、不创建或停止进程。 */
internal object LongLivedProotRecoveryPlanner {
    fun plan(
        persisted: List<LongLivedProotLeaseRecord>,
        observedAlive: Set<LongLivedProotProcessIdentity>,
        stopRequestedOwners: Set<LongLivedProotOwnerKey> = emptySet(),
        nowMs: Long,
    ): LongLivedProotRecoveryPlan {
        require(nowMs >= 0L && persisted.all { nowMs >= it.updatedAtMs }) {
            "long_lived_recovery_time_regressed"
        }

        val ownerDecisions = persisted
            .groupBy { it.spec.owner }
            .toSortedMap(compareBy<LongLivedProotOwnerKey>({ it.kind.ordinal }, { it.ownerId }))
            .map { (owner, candidates) ->
                recoverOwner(
                    owner = owner,
                    candidates = candidates,
                    observedAlive = observedAlive,
                    stopRequested = owner in stopRequestedOwners,
                    nowMs = nowMs,
                )
            }
        val conflictingIdentities = ownerDecisions
            .filter { it.record.phase != LongLivedProotLeasePhase.RELEASED }
            .mapNotNull { decision -> decision.record.processIdentity?.let { it to decision } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .filterValues { it.size > 1 }
            .keys
        val decisions = ownerDecisions.map { decision ->
            if (decision.record.processIdentity in conflictingIdentities) {
                decision.copy(action = LongLivedProotRecoveryAction.PROCESS_IDENTITY_CONFLICT_REVIEW)
            } else {
                decision
            }
        }
        return LongLivedProotRecoveryPlan(decisions = decisions)
    }

    private fun recoverOwner(
        owner: LongLivedProotOwnerKey,
        candidates: List<LongLivedProotLeaseRecord>,
        observedAlive: Set<LongLivedProotProcessIdentity>,
        stopRequested: Boolean,
        nowMs: Long,
    ): LongLivedProotRecoveryDecision {
        val generation = candidates.maxOf { it.generation }
        val newest = candidates.filter { it.generation == generation }
        val distinctNewest = newest.distinct()
        val chosen = distinctNewest.sortedBy { it.leaseId }.first()
        val olderCount = candidates.count { it.generation < generation }
        val exactDuplicateCount = newest.size - distinctNewest.size

        if (distinctNewest.size > 1) {
            return LongLivedProotRecoveryDecision(
                record = chosen,
                action = LongLivedProotRecoveryAction.DUPLICATE_CONFLICT_REVIEW,
                processMatch = match(chosen.processIdentity, observedAlive),
                discardedOlderGenerations = olderCount,
                collapsedExactDuplicates = exactDuplicateCount,
            )
        }

        var record = chosen
        if (stopRequested) {
            record = applyStopIntent(record, nowMs)
        }
        val decision = recoverRecord(record, observedAlive, nowMs)
        check(decision.record.spec.owner == owner)
        return decision.copy(
            discardedOlderGenerations = olderCount,
            collapsedExactDuplicates = exactDuplicateCount,
        )
    }

    private fun applyStopIntent(
        record: LongLivedProotLeaseRecord,
        nowMs: Long,
    ): LongLivedProotLeaseRecord {
        val transition = when (record.phase) {
            LongLivedProotLeasePhase.REQUESTED,
            LongLivedProotLeasePhase.ADMITTED ->
                LongLivedProotOwnerLeaseTransitions.cancelBeforeStart(record, nowMs)
            LongLivedProotLeasePhase.STARTING,
            LongLivedProotLeasePhase.RUNNING ->
                LongLivedProotOwnerLeaseTransitions.beginStop(record, nowMs)
            LongLivedProotLeasePhase.ORPHAN_REVIEW ->
                LongLivedProotOwnerLeaseTransitions.requestStopDuringOrphan(record, nowMs)
            LongLivedProotLeasePhase.STOPPING,
            LongLivedProotLeasePhase.RELEASED -> return record
        }
        check(transition.accepted) { transition.rejectionReason ?: "long_lived_recovery_stop_rejected" }
        return transition.record
    }

    private fun recoverRecord(
        record: LongLivedProotLeaseRecord,
        observedAlive: Set<LongLivedProotProcessIdentity>,
        nowMs: Long,
    ): LongLivedProotRecoveryDecision {
        val processMatch = match(record.processIdentity, observedAlive)
        return when (record.phase) {
            LongLivedProotLeasePhase.REQUESTED -> decision(
                record,
                LongLivedProotRecoveryAction.RESTORED_QUEUED,
                LongLivedProotProcessMatch.NOT_APPLICABLE,
            )
            LongLivedProotLeasePhase.ADMITTED -> {
                val released = accepted(LongLivedProotOwnerLeaseTransitions.cancelBeforeStart(record, nowMs))
                decision(
                    released,
                    LongLivedProotRecoveryAction.RELEASED_UNSTARTED_ADMISSION,
                    LongLivedProotProcessMatch.NOT_APPLICABLE,
                )
            }
            LongLivedProotLeasePhase.STARTING -> recoverStarting(record, processMatch, nowMs)
            LongLivedProotLeasePhase.RUNNING,
            LongLivedProotLeasePhase.STOPPING -> recoverAttached(record, processMatch, nowMs)
            LongLivedProotLeasePhase.ORPHAN_REVIEW -> recoverOrphan(record, processMatch, nowMs)
            LongLivedProotLeasePhase.RELEASED -> decision(
                record,
                LongLivedProotRecoveryAction.ALREADY_RELEASED,
                LongLivedProotProcessMatch.NOT_APPLICABLE,
            )
        }
    }

    private fun recoverStarting(
        record: LongLivedProotLeaseRecord,
        processMatch: LongLivedProotProcessMatch,
        nowMs: Long,
    ): LongLivedProotRecoveryDecision {
        if (processMatch == LongLivedProotProcessMatch.EXACT_GENERATION) {
            val running = accepted(
                LongLivedProotOwnerLeaseTransitions.attachProcess(
                    record,
                    requireNotNull(record.processIdentity),
                    nowMs,
                )
            )
            return decision(running, LongLivedProotRecoveryAction.RESTORED_RUNNING, processMatch)
        }
        return decision(record, LongLivedProotRecoveryAction.HELD_START_REVIEW, processMatch)
    }

    private fun recoverAttached(
        record: LongLivedProotLeaseRecord,
        processMatch: LongLivedProotProcessMatch,
        nowMs: Long,
    ): LongLivedProotRecoveryDecision {
        if (processMatch == LongLivedProotProcessMatch.EXACT_GENERATION) {
            val action = if (record.phase == LongLivedProotLeasePhase.STOPPING) {
                LongLivedProotRecoveryAction.RESTORED_STOPPING
            } else {
                LongLivedProotRecoveryAction.RESTORED_RUNNING
            }
            return decision(record, action, processMatch)
        }
        val orphan = accepted(LongLivedProotOwnerLeaseTransitions.observeProcessLost(record, nowMs))
        return decision(orphan, LongLivedProotRecoveryAction.MOVED_TO_ORPHAN_REVIEW, processMatch)
    }

    private fun recoverOrphan(
        record: LongLivedProotLeaseRecord,
        processMatch: LongLivedProotProcessMatch,
        nowMs: Long,
    ): LongLivedProotRecoveryDecision {
        if (processMatch != LongLivedProotProcessMatch.EXACT_GENERATION) {
            return decision(record, LongLivedProotRecoveryAction.REMAINED_IN_ORPHAN_REVIEW, processMatch)
        }
        val reconciled = accepted(
            LongLivedProotOwnerLeaseTransitions.reconcileAlive(
                record,
                requireNotNull(record.processIdentity),
                nowMs,
            )
        )
        val action = if (reconciled.phase == LongLivedProotLeasePhase.STOPPING) {
            LongLivedProotRecoveryAction.RESTORED_STOPPING
        } else {
            LongLivedProotRecoveryAction.RESTORED_RUNNING
        }
        return decision(reconciled, action, processMatch)
    }

    private fun match(
        identity: LongLivedProotProcessIdentity?,
        observedAlive: Set<LongLivedProotProcessIdentity>,
    ): LongLivedProotProcessMatch {
        identity ?: return LongLivedProotProcessMatch.IDENTITY_NOT_PERSISTED
        return when {
            identity in observedAlive -> LongLivedProotProcessMatch.EXACT_GENERATION
            observedAlive.any { it.hostPid == identity.hostPid } -> LongLivedProotProcessMatch.PID_REUSED
            else -> LongLivedProotProcessMatch.NOT_FOUND
        }
    }

    private fun accepted(transition: LongLivedProotLeaseTransition): LongLivedProotLeaseRecord {
        check(transition.accepted) { transition.rejectionReason ?: "long_lived_recovery_transition_rejected" }
        return transition.record
    }

    private fun decision(
        record: LongLivedProotLeaseRecord,
        action: LongLivedProotRecoveryAction,
        processMatch: LongLivedProotProcessMatch,
    ) = LongLivedProotRecoveryDecision(record, action, processMatch)
}
