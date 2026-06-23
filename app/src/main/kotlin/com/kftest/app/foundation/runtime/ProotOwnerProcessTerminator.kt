package com.kftest.app.foundation.runtime

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.kftest.app.foundation.logging.Logger

data class ProotOwnerTerminationResult(
    val ownerId: String,
    val targetTraceePids: List<Int> = emptyList(),
    val targetProcessGroupIds: List<Int> = emptyList(),
    val remainingTraceePids: List<Int> = emptyList(),
    val sentTerminate: Boolean = false,
    val sentKill: Boolean = false,
    val reason: String = "not_started"
) {
    val ok: Boolean
        get() = remainingTraceePids.isEmpty()

    fun toStopOutput(): String {
        return buildString {
            appendLine("__kite_owner_stop_owner:$ownerId")
            appendLine("__kite_owner_stop_reason:$reason")
            appendLine("__kite_owner_stop_targets:${targetTraceePids.joinToString(",")}")
            appendLine("__kite_owner_stop_pgid:${targetProcessGroupIds.joinToString(",")}")
            appendLine("__kite_owner_stop_term:$sentTerminate")
            appendLine("__kite_owner_stop_kill:$sentKill")
            appendLine("__kite_stop_remaining:${remainingTraceePids.joinToString(",")}")
        }
    }
}

object ProotOwnerProcessTerminator {
    private const val LOG_TAG = "ProotOwnerProcessStop"
    private const val OWNER_STOP_DEADLINE_MS = 10_000L
    private const val KILL_WINDOW_MS = 2_000L
    private const val OWNER_POLL_MS = 700L
    private const val SIGNAL_BATCH_SIZE = 4
    private const val SIGNAL_BATCH_DELAY_MS = 180L

    private data class OwnerSignalTargets(
        val traceePids: List<Int>,
        val processGroupIds: List<Int>
    )

    fun terminate(
        context: Context,
        ownerId: String
    ): ProotOwnerTerminationResult {
        val cleanOwnerId = ownerId.trim()
        if (cleanOwnerId.isBlank()) {
            return ProotOwnerTerminationResult(reason = "owner_id_missing", ownerId = "")
        }

        val before = ProotTelemetryStore.refreshBlocking(context)
        val group = before.ownerProcessIndex.groups.firstOrNull { it.ownerId == cleanOwnerId }
            ?: return ProotOwnerTerminationResult(
                ownerId = cleanOwnerId,
                reason = "owner_not_live"
            )
        val targetTracees = group.traceePids.filter { it > 1 }.distinct().sorted()
        val targetGroups = group.processGroupIds.filter { it > 1 }.distinct().sorted()
        if (targetTracees.isEmpty()) {
            return ProotOwnerTerminationResult(
                ownerId = cleanOwnerId,
                targetProcessGroupIds = targetGroups,
                reason = "owner_has_no_tracee_signal_target"
            )
        }

        Logger.i(
            LOG_TAG,
            "stop owner=$cleanOwnerId tracees=${targetTracees.joinToString(",")} pgids=${targetGroups.joinToString(",")} pgidMode=report_only deadlineMs=$OWNER_STOP_DEADLINE_MS"
        )
        val deadlineAt = System.currentTimeMillis() + OWNER_STOP_DEADLINE_MS
        val killWindowAt = deadlineAt - KILL_WINDOW_MS
        val sentTerm = signalTraceePidsLazily(targetTracees, OsConstants.SIGTERM)
        var remaining = waitForOwnerExit(
            context = context,
            ownerId = cleanOwnerId,
            deadlineAtMs = killWindowAt,
            staleTelemetryReason = "owner_processes_missing_after_lazy_term"
        )
        if (remaining.isNotEmpty()) {
            val killTargets = ownerTargets(context, cleanOwnerId)
            val sentKill = signalTraceePidsLazily(killTargets.traceePids.ifEmpty { remaining }, OsConstants.SIGKILL)
            remaining = waitForOwnerExit(
                context = context,
                ownerId = cleanOwnerId,
                deadlineAtMs = deadlineAt,
                staleTelemetryReason = "owner_processes_missing_after_lazy_kill"
            )
            return ProotOwnerTerminationResult(
                ownerId = cleanOwnerId,
                targetTraceePids = targetTracees,
                targetProcessGroupIds = targetGroups,
                remainingTraceePids = remaining,
                sentTerminate = sentTerm,
                sentKill = sentKill,
                reason = if (remaining.isEmpty()) "owner_processes_exited_after_kill" else "owner_processes_still_live"
            )
        }

        return ProotOwnerTerminationResult(
            ownerId = cleanOwnerId,
            targetTraceePids = targetTracees,
            targetProcessGroupIds = targetGroups,
            remainingTraceePids = emptyList(),
            sentTerminate = sentTerm,
            sentKill = false,
            reason = "owner_processes_exited_after_lazy_term"
        )
    }

    private fun waitForOwnerExit(
        context: Context,
        ownerId: String,
        deadlineAtMs: Long,
        staleTelemetryReason: String
    ): List<Int> {
        var remaining = remainingTracees(context, ownerId)
        while (remaining.isNotEmpty() && System.currentTimeMillis() < deadlineAtMs) {
            val sleepMs = (deadlineAtMs - System.currentTimeMillis())
                .coerceAtMost(OWNER_POLL_MS)
                .coerceAtLeast(0L)
            if (sleepMs > 0L) Thread.sleep(sleepMs)
            remaining = remainingTracees(context, ownerId)
            if (remaining.isNotEmpty()) {
                remaining = retireIfOsTargetsGone(
                    context = context,
                    ownerId = ownerId,
                    remainingTraceePids = remaining,
                    reason = staleTelemetryReason
                )
            }
        }
        return remaining
    }

    private fun remainingTracees(context: Context, ownerId: String): List<Int> {
        return ownerTargets(context, ownerId).traceePids
    }

    private fun ownerTargets(context: Context, ownerId: String): OwnerSignalTargets {
        val group = ProotTelemetryStore.refreshBlocking(context)
            .ownerProcessIndex
            .groups
            .firstOrNull { it.ownerId == ownerId }
        return OwnerSignalTargets(
            traceePids = group
                ?.traceePids
                .orEmpty()
                .filter { it > 1 }
                .distinct()
                .sorted(),
            processGroupIds = group
                ?.processGroupIds
                .orEmpty()
                .filter { it > 1 }
                .distinct()
                .sorted()
        )
    }

    private fun retireIfOsTargetsGone(
        context: Context,
        ownerId: String,
        remainingTraceePids: List<Int>,
        reason: String
    ): List<Int> {
        val liveOsPids = remainingTraceePids.filter { pid -> isProcessStillPresent(pid) }
        if (liveOsPids.isNotEmpty()) {
            return remainingTraceePids
        }
        val result = ProotTelemetryStore.retireOwnerTracees(
            context = context,
            ownerId = ownerId,
            reason = reason
        )
        Logger.i(LOG_TAG, "retire stale owner tracees after signal check: ${result.summary()}")
        return remainingTracees(context, ownerId)
    }

    private fun signalTraceePidsLazily(
        traceePids: List<Int>,
        signal: Int
    ): Boolean {
        var sent = false
        // ponytail: avoid negative process-group kills; Android app children can share unsafe pgid boundaries.
        val targets = traceePids
            .filter { it > 1 }
            .distinct()
            .sorted()
        targets
            .chunked(SIGNAL_BATCH_SIZE)
            .forEachIndexed { index, chunk ->
                chunk.forEach { pid ->
                    sent = sendSignal(pid, signal, "pid=$pid") || sent
                }
                if (index < (targets.size - 1) / SIGNAL_BATCH_SIZE) {
                    Thread.sleep(SIGNAL_BATCH_DELAY_MS)
                }
            }
        return sent
    }

    private fun sendSignal(target: Int, signal: Int, label: String): Boolean {
        if (target == 0 || target == -1 || target == 1) return false
        return runCatching {
            Os.kill(target, signal)
            true
        }.getOrElse { error ->
            Logger.i(LOG_TAG, "send owner stop signal failed: $label signal=$signal error=${error.message}")
            false
        }
    }

    private fun isProcessStillPresent(pid: Int): Boolean {
        if (pid <= 1) return false
        return runCatching {
            Os.kill(pid, 0)
            true
        }.getOrElse { error ->
            val message = error.message.orEmpty()
            !message.contains("ESRCH") &&
                !message.contains("No such process", ignoreCase = true)
        }
    }
}
