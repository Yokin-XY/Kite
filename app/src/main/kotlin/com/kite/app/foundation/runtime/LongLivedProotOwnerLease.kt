package com.kite.app.foundation.runtime

internal enum class LongLivedProotOwnerKind {
    BACKGROUND_RUNTIME,
    TERMINAL_SESSION,
    AGENT_SESSION,
    OTHER_MANAGED_OWNER,
}

/** 长期 owner 的文件系统姿态不能复用短任务 SHARED_WRITE 的调用期互斥语义。 */
internal enum class LongLivedProotFilesystemPosture {
    SHARED_RUNTIME,
    ISOLATED_RUNTIME,
    EXCLUSIVE_MAINTENANCE,
}

internal enum class LongLivedProotLeasePhase {
    REQUESTED,
    ADMITTED,
    STARTING,
    RUNNING,
    STOPPING,
    ORPHAN_REVIEW,
    RELEASED,
}

internal enum class LongLivedProotReleaseReason {
    CANCELLED_BEFORE_START,
    START_FAILED,
    STOP_CONFIRMED,
    PROCESS_DEAD_CONFIRMED,
}

internal data class LongLivedProotOwnerKey(
    val kind: LongLivedProotOwnerKind,
    val ownerId: String,
) {
    init {
        require(ownerId.isNotBlank() && ownerId.length <= 160) { "long_lived_owner_id_invalid" }
    }
}

internal data class LongLivedProotProcessIdentity(
    val hostPid: Int,
    val processStartTicks: Long,
) {
    init {
        require(hostPid > 0) { "long_lived_process_pid_invalid" }
        require(processStartTicks > 0L) { "long_lived_process_start_ticks_invalid" }
    }
}

internal data class LongLivedProotLeaseSpec(
    val owner: LongLivedProotOwnerKey,
    val lane: RuntimeLaneKind,
    val filesystemPosture: LongLivedProotFilesystemPosture,
    val pressureEssential: Boolean = false,
)

internal data class LongLivedProotLeaseRecord(
    val leaseId: String,
    val generation: Long,
    val spec: LongLivedProotLeaseSpec,
    val phase: LongLivedProotLeasePhase,
    val processIdentity: LongLivedProotProcessIdentity? = null,
    val phaseBeforeOrphan: LongLivedProotLeasePhase? = null,
    val releaseReason: LongLivedProotReleaseReason? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    init {
        require(leaseId.isNotBlank() && leaseId.length <= 128) { "long_lived_lease_id_invalid" }
        require(generation > 0L) { "long_lived_lease_generation_invalid" }
        require(createdAtMs >= 0L && updatedAtMs >= createdAtMs) { "long_lived_lease_time_invalid" }
        require(
            (phase == LongLivedProotLeasePhase.ORPHAN_REVIEW) == (phaseBeforeOrphan != null)
        ) { "long_lived_orphan_origin_invalid" }
        require(
            phaseBeforeOrphan == null || phaseBeforeOrphan in setOf(
                LongLivedProotLeasePhase.RUNNING,
                LongLivedProotLeasePhase.STOPPING,
            )
        ) { "long_lived_orphan_origin_phase_invalid" }
    }

    val holdsCapacity: Boolean get() = phase in setOf(
        LongLivedProotLeasePhase.ADMITTED,
        LongLivedProotLeasePhase.STARTING,
        LongLivedProotLeasePhase.RUNNING,
        LongLivedProotLeasePhase.STOPPING,
        LongLivedProotLeasePhase.ORPHAN_REVIEW,
    )
}

internal data class LongLivedProotLeaseTransition(
    val record: LongLivedProotLeaseRecord,
    val changed: Boolean,
    val rejectionReason: String? = null,
) {
    val accepted: Boolean get() = rejectionReason == null
}

/** 纯状态转换；不持有集合、不操作 Store、不创建或停止进程。 */
internal object LongLivedProotOwnerLeaseTransitions {
    fun requested(
        leaseId: String,
        generation: Long,
        spec: LongLivedProotLeaseSpec,
        nowMs: Long,
    ): LongLivedProotLeaseRecord = LongLivedProotLeaseRecord(
        leaseId = leaseId,
        generation = generation,
        spec = spec,
        phase = LongLivedProotLeasePhase.REQUESTED,
        createdAtMs = nowMs,
        updatedAtMs = nowMs,
    )

    fun admit(record: LongLivedProotLeaseRecord, nowMs: Long): LongLivedProotLeaseTransition =
        move(record, LongLivedProotLeasePhase.REQUESTED, LongLivedProotLeasePhase.ADMITTED, nowMs)

    fun beginStart(record: LongLivedProotLeaseRecord, nowMs: Long): LongLivedProotLeaseTransition =
        move(record, LongLivedProotLeasePhase.ADMITTED, LongLivedProotLeasePhase.STARTING, nowMs)

    fun attachProcess(
        record: LongLivedProotLeaseRecord,
        identity: LongLivedProotProcessIdentity,
        nowMs: Long,
    ): LongLivedProotLeaseTransition {
        if (record.phase == LongLivedProotLeasePhase.RUNNING) {
            return if (record.processIdentity == identity) {
                LongLivedProotLeaseTransition(record, changed = false)
            } else {
                rejected(record, "long_lived_process_identity_conflict")
            }
        }
        if (record.phase != LongLivedProotLeasePhase.STARTING) {
            return rejected(record, "long_lived_attach_requires_starting")
        }
        if (nowMs < record.updatedAtMs) return rejected(record, "long_lived_transition_time_regressed")
        if (record.processIdentity != null && record.processIdentity != identity) {
            return rejected(record, "long_lived_process_identity_conflict")
        }
        return accepted(
            record.copy(
                phase = LongLivedProotLeasePhase.RUNNING,
                processIdentity = identity,
                updatedAtMs = nowMs,
            )
        )
    }

    fun cancelBeforeStart(
        record: LongLivedProotLeaseRecord,
        nowMs: Long,
    ): LongLivedProotLeaseTransition {
        if (record.phase !in setOf(LongLivedProotLeasePhase.REQUESTED, LongLivedProotLeasePhase.ADMITTED)) {
            return rejected(record, "long_lived_cancel_requires_prestart")
        }
        return release(record, LongLivedProotReleaseReason.CANCELLED_BEFORE_START, nowMs)
    }

    fun startFailed(record: LongLivedProotLeaseRecord, nowMs: Long): LongLivedProotLeaseTransition {
        if (record.phase != LongLivedProotLeasePhase.STARTING || record.processIdentity != null) {
            return rejected(record, "long_lived_start_failure_requires_unattached_starting")
        }
        return release(record, LongLivedProotReleaseReason.START_FAILED, nowMs)
    }

    fun beginStop(record: LongLivedProotLeaseRecord, nowMs: Long): LongLivedProotLeaseTransition {
        if (record.phase == LongLivedProotLeasePhase.STOPPING) {
            return LongLivedProotLeaseTransition(record, changed = false)
        }
        if (record.phase !in setOf(LongLivedProotLeasePhase.STARTING, LongLivedProotLeasePhase.RUNNING)) {
            return rejected(record, "long_lived_stop_requires_starting_or_running")
        }
        if (nowMs < record.updatedAtMs) return rejected(record, "long_lived_transition_time_regressed")
        return accepted(record.copy(phase = LongLivedProotLeasePhase.STOPPING, updatedAtMs = nowMs))
    }

    fun observeProcessLost(
        record: LongLivedProotLeaseRecord,
        nowMs: Long,
    ): LongLivedProotLeaseTransition {
        if (record.phase == LongLivedProotLeasePhase.ORPHAN_REVIEW) {
            return LongLivedProotLeaseTransition(record, changed = false)
        }
        if (record.phase !in setOf(LongLivedProotLeasePhase.RUNNING, LongLivedProotLeasePhase.STOPPING)) {
            return rejected(record, "long_lived_process_loss_requires_attached_owner")
        }
        if (nowMs < record.updatedAtMs) return rejected(record, "long_lived_transition_time_regressed")
        return accepted(
            record.copy(
                phase = LongLivedProotLeasePhase.ORPHAN_REVIEW,
                phaseBeforeOrphan = record.phase,
                updatedAtMs = nowMs,
            )
        )
    }

    fun reconcileAlive(
        record: LongLivedProotLeaseRecord,
        identity: LongLivedProotProcessIdentity,
        nowMs: Long,
    ): LongLivedProotLeaseTransition {
        if (record.phase != LongLivedProotLeasePhase.ORPHAN_REVIEW) {
            return rejected(record, "long_lived_reconcile_alive_requires_orphan_review")
        }
        if (record.processIdentity != identity) {
            return rejected(record, "long_lived_process_identity_conflict")
        }
        if (nowMs < record.updatedAtMs) return rejected(record, "long_lived_transition_time_regressed")
        return accepted(
            record.copy(
                phase = requireNotNull(record.phaseBeforeOrphan),
                phaseBeforeOrphan = null,
                updatedAtMs = nowMs,
            )
        )
    }

    fun confirmStopped(record: LongLivedProotLeaseRecord, nowMs: Long): LongLivedProotLeaseTransition {
        if (record.phase != LongLivedProotLeasePhase.STOPPING) {
            return rejected(record, "long_lived_stop_confirmation_requires_stopping")
        }
        return release(record, LongLivedProotReleaseReason.STOP_CONFIRMED, nowMs)
    }

    fun confirmDead(record: LongLivedProotLeaseRecord, nowMs: Long): LongLivedProotLeaseTransition {
        if (record.phase != LongLivedProotLeasePhase.ORPHAN_REVIEW) {
            return rejected(record, "long_lived_death_confirmation_requires_orphan_review")
        }
        return release(record, LongLivedProotReleaseReason.PROCESS_DEAD_CONFIRMED, nowMs)
    }

    private fun move(
        record: LongLivedProotLeaseRecord,
        from: LongLivedProotLeasePhase,
        to: LongLivedProotLeasePhase,
        nowMs: Long,
    ): LongLivedProotLeaseTransition {
        if (nowMs < record.updatedAtMs) return rejected(record, "long_lived_transition_time_regressed")
        if (record.phase != from) return rejected(record, "long_lived_transition_${from.name.lowercase()}_required")
        return accepted(record.copy(phase = to, updatedAtMs = nowMs))
    }

    private fun release(
        record: LongLivedProotLeaseRecord,
        reason: LongLivedProotReleaseReason,
        nowMs: Long,
    ): LongLivedProotLeaseTransition {
        if (nowMs < record.updatedAtMs) return rejected(record, "long_lived_transition_time_regressed")
        return accepted(record.copy(
            phase = LongLivedProotLeasePhase.RELEASED,
            processIdentity = null,
            phaseBeforeOrphan = null,
            releaseReason = reason,
            updatedAtMs = nowMs,
        ))
    }

    private fun accepted(record: LongLivedProotLeaseRecord): LongLivedProotLeaseTransition =
        LongLivedProotLeaseTransition(record = record, changed = true)

    private fun rejected(
        record: LongLivedProotLeaseRecord,
        reason: String,
    ): LongLivedProotLeaseTransition = LongLivedProotLeaseTransition(
        record = record,
        changed = false,
        rejectionReason = reason,
    )
}
