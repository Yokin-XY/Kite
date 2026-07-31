package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.LongLivedProotLeasePhase

/** RF920 持久化镜像；后台记录仍是 owner 唯一事实源。 */
internal data class BackgroundRuntimeProotLeaseCheckpoint(
    val generation: Long,
    val phase: LongLivedProotLeasePhase,
    val updatedAtMs: Long,
)

internal sealed interface BackgroundRuntimeProotLeaseCheckpointState {
    data object Absent : BackgroundRuntimeProotLeaseCheckpointState

    data class Ready(
        val checkpoint: BackgroundRuntimeProotLeaseCheckpoint,
    ) : BackgroundRuntimeProotLeaseCheckpointState

    data class Malformed(
        val reason: String,
    ) : BackgroundRuntimeProotLeaseCheckpointState
}

internal data class BackgroundRuntimeProotLeaseMutation(
    val record: BackgroundRuntimeRecord?,
    val changed: Boolean,
    val rejectionReason: String? = null,
) {
    val accepted: Boolean get() = rejectionReason == null
}

internal object BackgroundRuntimeProotLeaseCheckpointPolicy {
    private const val PROOT_LAUNCH_LANE = "proot_shell"

    fun inspect(record: BackgroundRuntimeRecord): BackgroundRuntimeProotLeaseCheckpointState {
        val generation = record.longLivedProotLeaseGeneration
        val phaseName = record.longLivedProotLeasePhase
        val updatedAtMs = record.longLivedProotLeaseUpdatedAt
        if (generation == null && phaseName == null && updatedAtMs == null) {
            return BackgroundRuntimeProotLeaseCheckpointState.Absent
        }
        if (generation == null || phaseName == null || updatedAtMs == null) {
            return malformed("background_proot_lease_partial_checkpoint")
        }
        if (generation <= 0L) return malformed("background_proot_lease_generation_invalid")
        if (updatedAtMs < 0L) return malformed("background_proot_lease_time_invalid")
        val phase = LongLivedProotLeasePhase.entries.firstOrNull { it.name == phaseName }
            ?: return malformed("background_proot_lease_phase_invalid")
        if (
            phase != LongLivedProotLeasePhase.RELEASED &&
            (record.mode != BackgroundRuntimeMode.PROCESS || record.lastLaunchLane != PROOT_LAUNCH_LANE)
        ) {
            return malformed("background_proot_lease_route_conflict")
        }
        return BackgroundRuntimeProotLeaseCheckpointState.Ready(
            BackgroundRuntimeProotLeaseCheckpoint(
                generation = generation,
                phase = phase,
                updatedAtMs = updatedAtMs,
            )
        )
    }

    /**
     * 在实际 controller 已准入后、进程创建前，把路由和 STARTING 占位写入同一条后台记录。
     * RF920 只提供持久化原语，生产启动链要到 RF930 才允许调用。
     */
    fun beginStarting(
        record: BackgroundRuntimeRecord,
        generation: Long,
        launchReason: String,
        updatedAtMs: Long,
    ): BackgroundRuntimeProotLeaseMutation {
        if (record.mode != BackgroundRuntimeMode.PROCESS) {
            return rejected(record, "background_proot_lease_requires_process_mode")
        }
        if (generation <= 0L) return rejected(record, "background_proot_lease_generation_invalid")
        if (updatedAtMs < 0L) return rejected(record, "background_proot_lease_time_invalid")
        val reason = launchReason.trim()
        if (reason.isEmpty()) return rejected(record, "background_proot_lease_route_reason_missing")

        when (val current = inspect(record)) {
            BackgroundRuntimeProotLeaseCheckpointState.Absent -> Unit
            is BackgroundRuntimeProotLeaseCheckpointState.Malformed -> {
                return rejected(record, current.reason)
            }
            is BackgroundRuntimeProotLeaseCheckpointState.Ready -> {
                val checkpoint = current.checkpoint
                if (
                    checkpoint.generation == generation &&
                    checkpoint.phase == LongLivedProotLeasePhase.STARTING
                ) {
                    return BackgroundRuntimeProotLeaseMutation(record, changed = false)
                }
                if (
                    checkpoint.phase != LongLivedProotLeasePhase.RELEASED ||
                    generation <= checkpoint.generation
                ) {
                    return rejected(record, "background_proot_lease_generation_conflict")
                }
                if (updatedAtMs < checkpoint.updatedAtMs) {
                    return rejected(record, "background_proot_lease_time_regressed")
                }
            }
        }

        return accepted(
            record.copy(
                lastLaunchLane = PROOT_LAUNCH_LANE,
                lastLaunchReason = reason,
                longLivedProotLeaseGeneration = generation,
                longLivedProotLeasePhase = LongLivedProotLeasePhase.STARTING.name,
                longLivedProotLeaseUpdatedAt = updatedAtMs,
            )
        )
    }

    fun transition(
        record: BackgroundRuntimeRecord,
        expectedGeneration: Long,
        expectedPhase: LongLivedProotLeasePhase,
        nextPhase: LongLivedProotLeasePhase,
        updatedAtMs: Long,
    ): BackgroundRuntimeProotLeaseMutation {
        val current = when (val state = inspect(record)) {
            BackgroundRuntimeProotLeaseCheckpointState.Absent -> {
                return rejected(record, "background_proot_lease_checkpoint_missing")
            }
            is BackgroundRuntimeProotLeaseCheckpointState.Malformed -> {
                return rejected(record, state.reason)
            }
            is BackgroundRuntimeProotLeaseCheckpointState.Ready -> state.checkpoint
        }
        if (current.generation != expectedGeneration || current.phase != expectedPhase) {
            return rejected(record, "background_proot_lease_stale_expectation")
        }
        if (updatedAtMs < current.updatedAtMs) {
            return rejected(record, "background_proot_lease_time_regressed")
        }
        if (nextPhase == current.phase) {
            return BackgroundRuntimeProotLeaseMutation(record, changed = false)
        }
        if (!isAllowedTransition(current.phase, nextPhase)) {
            return rejected(record, "background_proot_lease_transition_invalid")
        }
        return accepted(
            record.copy(
                longLivedProotLeasePhase = nextPhase.name,
                longLivedProotLeaseUpdatedAt = updatedAtMs,
            )
        )
    }

    private fun isAllowedTransition(
        current: LongLivedProotLeasePhase,
        next: LongLivedProotLeasePhase,
    ): Boolean = when (current) {
        LongLivedProotLeasePhase.STARTING -> next in setOf(
            LongLivedProotLeasePhase.RUNNING,
            LongLivedProotLeasePhase.STOPPING,
            LongLivedProotLeasePhase.ORPHAN_REVIEW,
            LongLivedProotLeasePhase.RELEASED,
        )
        LongLivedProotLeasePhase.RUNNING -> next in setOf(
            LongLivedProotLeasePhase.STOPPING,
            LongLivedProotLeasePhase.ORPHAN_REVIEW,
        )
        LongLivedProotLeasePhase.STOPPING -> next in setOf(
            LongLivedProotLeasePhase.ORPHAN_REVIEW,
            LongLivedProotLeasePhase.RELEASED,
        )
        LongLivedProotLeasePhase.ORPHAN_REVIEW -> next in setOf(
            LongLivedProotLeasePhase.RUNNING,
            LongLivedProotLeasePhase.STOPPING,
            LongLivedProotLeasePhase.RELEASED,
        )
        LongLivedProotLeasePhase.REQUESTED,
        LongLivedProotLeasePhase.ADMITTED,
        LongLivedProotLeasePhase.RELEASED -> false
    }

    private fun accepted(record: BackgroundRuntimeRecord) = BackgroundRuntimeProotLeaseMutation(
        record = record,
        changed = true,
    )

    private fun rejected(
        record: BackgroundRuntimeRecord,
        reason: String,
    ) = BackgroundRuntimeProotLeaseMutation(
        record = record,
        changed = false,
        rejectionReason = reason,
    )

    private fun malformed(reason: String) =
        BackgroundRuntimeProotLeaseCheckpointState.Malformed(reason)
}

internal fun BackgroundRuntimeRecord.hasUnreleasedLongLivedProotLease(): Boolean {
    val checkpoint = (
        BackgroundRuntimeProotLeaseCheckpointPolicy.inspect(this) as?
            BackgroundRuntimeProotLeaseCheckpointState.Ready
        )?.checkpoint ?: return false
    return checkpoint.phase != LongLivedProotLeasePhase.RELEASED
}
