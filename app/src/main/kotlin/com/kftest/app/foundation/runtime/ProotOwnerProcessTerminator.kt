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
    private const val TERM_GRACE_MS = 700L
    private const val KILL_GRACE_MS = 350L

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
        if (targetTracees.isEmpty() && targetGroups.isEmpty()) {
            return ProotOwnerTerminationResult(
                ownerId = cleanOwnerId,
                reason = "owner_has_no_signal_target"
            )
        }

        Logger.i(
            LOG_TAG,
            "stop owner=$cleanOwnerId tracees=${targetTracees.joinToString(",")} pgids=${targetGroups.joinToString(",")}"
        )
        val sentTerm = signalTargets(targetGroups, targetTracees, OsConstants.SIGTERM)
        Thread.sleep(TERM_GRACE_MS)
        var remaining = remainingTracees(context, cleanOwnerId)
        if (remaining.isNotEmpty()) {
            remaining = retireIfOsTargetsGone(
                context = context,
                ownerId = cleanOwnerId,
                remainingTraceePids = remaining,
                reason = "owner_processes_missing_after_term"
            )
        }
        if (remaining.isNotEmpty()) {
            val killTargets = ownerTargets(context, cleanOwnerId)
            val sentKill = signalTargets(
                processGroupIds = killTargets.processGroupIds,
                traceePids = killTargets.traceePids.ifEmpty { remaining },
                signal = OsConstants.SIGKILL
            )
            Thread.sleep(KILL_GRACE_MS)
            remaining = remainingTracees(context, cleanOwnerId)
            if (remaining.isNotEmpty()) {
                remaining = retireIfOsTargetsGone(
                    context = context,
                    ownerId = cleanOwnerId,
                    remainingTraceePids = remaining,
                    reason = "owner_processes_missing_after_kill"
                )
            }
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
            reason = "owner_processes_exited_after_term"
        )
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

    private fun signalTargets(
        processGroupIds: List<Int>,
        traceePids: List<Int>,
        signal: Int
    ): Boolean {
        var sent = false
        processGroupIds.forEach { pgid ->
            sent = sendSignal(-pgid, signal, "pgid=$pgid") || sent
        }
        // PRoot tracee process groups are not always signalable on Android. The
        // owner index already gives a bounded direct tracee set, so use both.
        traceePids.forEach { pid ->
            sent = sendSignal(pid, signal, "pid=$pid") || sent
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
