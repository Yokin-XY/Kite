package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.util.concurrent.TimeUnit

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
    private const val OWNER_SHELL_TIMEOUT_MS = OWNER_STOP_DEADLINE_MS + 2_000L
    private const val OWNER_STOP_ATTEMPTS = 24
    private const val OWNER_STOP_SLEEP_SECONDS = "0.35"

    private data class OwnerSignalTargets(
        val traceePids: List<Int>,
        val processGroupIds: List<Int>
    )

    private data class OwnerStopCommandResult(
        val output: String,
        val remainingTraceePids: List<Int>,
        val sentKill: Boolean,
        val timedOut: Boolean,
        val exitCode: Int
    )

    private data class ShellCommandResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean
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
            "stop owner=$cleanOwnerId tracees=${targetTracees.joinToString(",")} pgids=${targetGroups.joinToString(",")} mode=ubuntu_pid_tree deadlineMs=$OWNER_STOP_DEADLINE_MS"
        )
        val stopResult = runUbuntuOwnerKill(
            context = context.applicationContext,
            targets = OwnerSignalTargets(
                traceePids = targetTracees,
                processGroupIds = targetGroups
            )
        )
        val refreshedTargets = ownerTargets(context, cleanOwnerId)
        val liveAfterUbuntu = probeUbuntuLiveTracees(
            context = context.applicationContext,
            traceePids = refreshedTargets.traceePids.ifEmpty { targetTracees }
        )
        val remaining = if (liveAfterUbuntu.isEmpty()) {
            retireOwnerTraceesAfterUbuntuStop(
                context = context,
                ownerId = cleanOwnerId,
                reason = if (stopResult.timedOut) {
                    "owner_processes_missing_after_timed_out_ubuntu_kill"
                } else {
                    "owner_processes_missing_after_ubuntu_kill"
                }
            )
        } else {
            liveAfterUbuntu
        }

        return ProotOwnerTerminationResult(
            ownerId = cleanOwnerId,
            targetTraceePids = targetTracees,
            targetProcessGroupIds = targetGroups,
            remainingTraceePids = remaining,
            sentTerminate = false,
            sentKill = stopResult.sentKill,
            reason = when {
                remaining.isEmpty() -> "owner_processes_exited_after_ubuntu_kill"
                stopResult.timedOut -> "owner_processes_still_live_after_ubuntu_kill_timeout"
                stopResult.exitCode != 0 -> "owner_processes_still_live_after_ubuntu_kill_exit_${stopResult.exitCode}"
                else -> "owner_processes_still_live_after_ubuntu_kill"
            }
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

    private fun retireOwnerTraceesAfterUbuntuStop(
        context: Context,
        ownerId: String,
        reason: String
    ): List<Int> {
        val result = ProotTelemetryStore.retireOwnerTracees(
            context = context,
            ownerId = ownerId,
            reason = reason
        )
        Logger.i(LOG_TAG, "retire stale owner tracees after Ubuntu /proc check: ${result.summary()}")
        return remainingTracees(context, ownerId)
    }

    private fun runUbuntuOwnerKill(
        context: Context,
        targets: OwnerSignalTargets
    ): OwnerStopCommandResult {
        val result = executeUbuntuShell(
            context = context,
            payload = buildUbuntuOwnerKillPayload(targets)
        )
        return OwnerStopCommandResult(
            output = result.output,
            remainingTraceePids = parseTaggedPidList(result.output, "__kite_stop_remaining:"),
            sentKill = result.output.contains("__kite_owner_stop_sent_kill:true"),
            timedOut = result.timedOut,
            exitCode = result.exitCode
        )
    }

    private fun probeUbuntuLiveTracees(context: Context, traceePids: List<Int>): List<Int> {
        val targets = traceePids.filter { it > 1 }.distinct().sorted()
        if (targets.isEmpty()) return emptyList()
        val result = executeUbuntuShell(
            context = context,
            payload = buildUbuntuLiveProbePayload(targets)
        )
        return parseTaggedPidList(result.output, "__kite_probe_remaining:")
    }

    private fun executeUbuntuShell(context: Context, payload: String): ShellCommandResult {
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = "/root",
            payload = payload,
            loginShell = true
        )
        return runCatching {
            val process = ProcessBuilder(config.command)
                .redirectErrorStream(true)
                .apply { environment().putAll(config.env) }
                .start()
            val finished = process.waitFor(OWNER_SHELL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            ShellCommandResult(
                exitCode = if (finished) process.exitValue() else -1,
                output = output,
                timedOut = !finished
            )
        }.getOrElse { error ->
            Logger.i(LOG_TAG, "Ubuntu owner stop shell failed: ${error.message}")
            ShellCommandResult(exitCode = -1, output = error.message.orEmpty(), timedOut = false)
        }
    }

    private fun buildUbuntuOwnerKillPayload(targets: OwnerSignalTargets): String {
        val pids = targets.traceePids.filter { it > 1 }.distinct().sorted().joinToString(" ")
        val pgids = targets.processGroupIds.filter { it > 1 }.distinct().sorted().joinToString(" ")
        return """
            kf_owner_pids='$pids'
            kf_owner_pgids='$pgids'
            kf_sent_kill=false
            printf '__kite_owner_stop_mode:ubuntu-pid-tree-force-kill\n'
            printf '__kite_owner_stop_targets:%s\n' "${'$'}(printf '%s\n' ${'$'}kf_owner_pids | tr '\n' ',')"
            printf '__kite_owner_stop_pgid:%s\n' "${'$'}(printf '%s\n' ${'$'}kf_owner_pgids | tr '\n' ',')"
            kf_signal_targets=""
            kf_is_live_process() {
              [ -n "${'$'}1" ] || return 1
              [ -d "/proc/${'$'}1" ] || return 1
              kf_state=${'$'}(sed 's/^.*) //' "/proc/${'$'}1/stat" 2>/dev/null | awk '{ print ${'$'}1 }')
              [ "${'$'}kf_state" = "Z" ] && return 1
              kill -0 "${'$'}1" >/dev/null 2>&1
            }
            kf_add_target() {
              case "${'$'}1" in
                ''|*[!0-9]*|0|1) return 0 ;;
              esac
              case " ${'$'}kf_signal_targets " in
                *" ${'$'}1 "*) ;;
                *) kf_signal_targets="${'$'}kf_signal_targets ${'$'}1" ;;
              esac
            }
            kf_children_of() {
              ps -eo pid=,ppid= 2>/dev/null | awk -v p="${'$'}1" '${'$'}2 == p { print ${'$'}1 }'
            }
            kf_collect_tree() {
              kf_todo="${'$'}1"
              kf_seen=""
              while [ -n "${'$'}kf_todo" ]; do
                kf_next=""
                for kf_parent in ${'$'}kf_todo; do
                  for kf_child in ${'$'}(kf_children_of "${'$'}kf_parent"); do
                    case " ${'$'}kf_seen " in
                      *" ${'$'}kf_child "*) ;;
                      *) kf_add_target "${'$'}kf_child"; kf_seen="${'$'}kf_seen ${'$'}kf_child"; kf_next="${'$'}kf_next ${'$'}kf_child" ;;
                    esac
                  done
                done
                kf_todo="${'$'}kf_next"
              done
            }
            kf_refresh_targets() {
              for kf_pid in ${'$'}kf_owner_pids; do
                kf_add_target "${'$'}kf_pid"
                kf_collect_tree "${'$'}kf_pid"
              done
            }
            kf_collect_remaining() {
              for kf_pid in ${'$'}kf_signal_targets; do
                kf_is_live_process "${'$'}kf_pid" && printf '%s\n' "${'$'}kf_pid"
              done
            }
            kf_kill_pids() {
              for kf_pid in ${'$'}kf_signal_targets; do
                [ -n "${'$'}kf_pid" ] || continue
                [ "${'$'}kf_pid" = "${'$'}${'$'}" ] && continue
                [ -n "${'$'}PPID" ] && [ "${'$'}kf_pid" = "${'$'}PPID" ] && continue
                kill -KILL "${'$'}kf_pid" >/dev/null 2>&1 || true
                kf_sent_kill=true
              done
            }
            kf_attempt=0
            while [ "${'$'}kf_attempt" -lt $OWNER_STOP_ATTEMPTS ]; do
              kf_refresh_targets
              kf_remaining=${'$'}(kf_collect_remaining)
              [ -n "${'$'}kf_remaining" ] || break
              kf_kill_pids
              sleep $OWNER_STOP_SLEEP_SECONDS
              kf_attempt=${'$'}((kf_attempt + 1))
            done
            kf_remaining=${'$'}(kf_collect_remaining)
            kf_remaining_line=${'$'}(printf '%s\n' "${'$'}kf_remaining" | tr '\n' ',')
            printf '__kite_owner_stop_sent_kill:%s\n' "${'$'}kf_sent_kill"
            printf '__kite_stop_remaining:%s\n' "${'$'}kf_remaining_line"
        """.trimIndent()
    }

    private fun buildUbuntuLiveProbePayload(traceePids: List<Int>): String {
        val pids = traceePids.filter { it > 1 }.distinct().sorted().joinToString(" ")
        return """
            kf_owner_pids='$pids'
            kf_is_live_process() {
              [ -n "${'$'}1" ] || return 1
              [ -d "/proc/${'$'}1" ] || return 1
              kf_state=${'$'}(sed 's/^.*) //' "/proc/${'$'}1/stat" 2>/dev/null | awk '{ print ${'$'}1 }')
              [ "${'$'}kf_state" = "Z" ] && return 1
              kill -0 "${'$'}1" >/dev/null 2>&1
            }
            kf_remaining=${'$'}(
              for kf_pid in ${'$'}kf_owner_pids; do
                kf_is_live_process "${'$'}kf_pid" && printf '%s\n' "${'$'}kf_pid"
              done
            )
            kf_remaining_line=${'$'}(printf '%s\n' "${'$'}kf_remaining" | tr '\n' ',')
            printf '__kite_probe_remaining:%s\n' "${'$'}kf_remaining_line"
        """.trimIndent()
    }

    private fun parseTaggedPidList(output: String, prefix: String): List<Int> {
        return output
            .lineSequence()
            .filter { it.startsWith(prefix) }
            .lastOrNull()
            ?.substringAfter(':')
            ?.split(',')
            .orEmpty()
            .mapNotNull { it.trim().toIntOrNull()?.takeIf { pid -> pid > 1 } }
            .distinct()
            .sorted()
    }
}
