package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.util.concurrent.TimeUnit

enum class ProotOwnerTerminationOutcome {
    CONFIRMED,
    OWNER_NOT_FOUND,
    TELEMETRY_UNAVAILABLE,
    PROBE_UNAVAILABLE,
    TIMEOUT,
    FAILED
}

data class ProotOwnerTerminationResult(
    val ownerId: String,
    val outcome: ProotOwnerTerminationOutcome = ProotOwnerTerminationOutcome.FAILED,
    val targetTraceePids: List<Int> = emptyList(),
    val targetProcessGroupIds: List<Int> = emptyList(),
    val remainingTraceePids: List<Int> = emptyList(),
    val remainingProcessGroupIds: List<Int> = emptyList(),
    val sentTerminate: Boolean = false,
    val sentKill: Boolean = false,
    val reason: String = "not_started"
) {
    val ok: Boolean
        get() = outcome == ProotOwnerTerminationOutcome.CONFIRMED

    fun toStopOutput(): String = buildString {
        appendLine("__kite_owner_stop_owner:$ownerId")
        appendLine("__kite_owner_stop_outcome:${outcome.name}")
        appendLine("__kite_owner_stop_reason:$reason")
        appendLine("__kite_owner_stop_targets:${targetTraceePids.joinToString(",")}")
        appendLine("__kite_owner_stop_pgid:${targetProcessGroupIds.joinToString(",")}")
        appendLine("__kite_owner_stop_term:$sentTerminate")
        appendLine("__kite_owner_stop_kill:$sentKill")
        appendLine("__kite_stop_remaining:${remainingTraceePids.joinToString(",")}")
        appendLine("__kite_stop_remaining_pgid:${remainingProcessGroupIds.joinToString(",")}")
    }
}

internal data class ProotTelemetryDestructiveReadiness(
    val usable: Boolean,
    val reason: String
)

/** 破坏性动作只关心读取完整性；长时间没有新事件不等于来源损坏。 */
internal object ProotOwnerTerminationEvidence {
    private const val MAX_REFRESH_AGE_MS = 5_000L

    fun readiness(
        snapshot: ProotTelemetrySnapshot,
        now: Long = System.currentTimeMillis()
    ): ProotTelemetryDestructiveReadiness {
        if (snapshot.collectionStatus != "loaded") {
            return ProotTelemetryDestructiveReadiness(false, "status_${snapshot.collectionStatus}")
        }
        if (!snapshot.fileExists) {
            return ProotTelemetryDestructiveReadiness(false, "telemetry_file_missing")
        }
        if (snapshot.refreshedAtMs <= 0L || now - snapshot.refreshedAtMs > MAX_REFRESH_AGE_MS) {
            return ProotTelemetryDestructiveReadiness(false, "telemetry_refresh_stale")
        }
        if (snapshot.counters.parseErrors > 0L) {
            return ProotTelemetryDestructiveReadiness(false, "telemetry_parse_errors")
        }
        if (snapshot.counters.skippedBytes > 0L) {
            return ProotTelemetryDestructiveReadiness(false, "telemetry_skipped_bytes")
        }
        return ProotTelemetryDestructiveReadiness(true, "telemetry_complete")
    }

    fun canConfirm(
        ownerWasObserved: Boolean,
        healthySilentRounds: Int,
        probeReliable: Boolean,
        liveTraceePids: Collection<Int>,
        liveProcessGroupIds: Collection<Int>
    ): Boolean = ownerWasObserved &&
        healthySilentRounds >= 2 &&
        probeReliable &&
        liveTraceePids.isEmpty() &&
        liveProcessGroupIds.isEmpty()
}

object ProotOwnerProcessTerminator {
    private const val LOG_TAG = "ProotOwnerProcessStop"
    private const val OWNER_STOP_DEADLINE_MS = 10_000L
    private const val OWNER_SHELL_TIMEOUT_MS = 3_000L
    private const val OWNER_STOP_MAX_ROUNDS = 12
    private const val TERM_GRACE_MS = 450L
    private const val KILL_GRACE_MS = 250L
    private const val SILENCE_CONFIRM_MS = 220L

    private enum class OwnerSignal(val shellName: String) {
        TERM("TERM"),
        KILL("KILL")
    }

    private data class OwnerSignalTargets(
        val traceePids: List<Int> = emptyList(),
        val processGroupIds: List<Int> = emptyList()
    ) {
        fun isEmpty(): Boolean = traceePids.isEmpty() && processGroupIds.isEmpty()

        operator fun plus(other: OwnerSignalTargets): OwnerSignalTargets = OwnerSignalTargets(
            traceePids = (traceePids + other.traceePids).validProcessIds(),
            processGroupIds = (processGroupIds + other.processGroupIds).validProcessIds()
        )
    }

    private sealed interface OwnerDiscovery {
        data class Ready(val targets: OwnerSignalTargets) : OwnerDiscovery
        data class Missing(val reason: String = "owner_not_observed") : OwnerDiscovery
        data class Unavailable(val reason: String) : OwnerDiscovery
    }

    private data class OwnerSignalResult(
        val discoveredTargets: OwnerSignalTargets,
        val markerObserved: Boolean,
        val timedOut: Boolean,
        val exitCode: Int
    )

    private data class OwnerProbeResult(
        val liveTargets: OwnerSignalTargets,
        val reliable: Boolean,
        val reason: String
    )

    private data class ShellCommandResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean
    )

    fun terminateTerminalSession(
        context: Context,
        terminalSessionId: String
    ): List<ProotOwnerTerminationResult> {
        val cleanSessionId = terminalSessionId.trim()
        val fallbackOwnerId = "terminal:$cleanSessionId"
        if (cleanSessionId.isBlank()) {
            return listOf(
                ProotOwnerTerminationResult(
                    ownerId = fallbackOwnerId,
                    outcome = ProotOwnerTerminationOutcome.FAILED,
                    reason = "terminal_session_id_missing"
                )
            )
        }
        val snapshot = ProotTelemetryStore.refreshBlocking(context.applicationContext)
        val readiness = ProotOwnerTerminationEvidence.readiness(snapshot)
        if (!readiness.usable) {
            return listOf(unavailableResult(fallbackOwnerId, readiness.reason))
        }
        val ownerIds = snapshot.ownerProcessIndex.groups
            .map { it.ownerId }
            .filter { RuntimeOwnerIdentity.terminalSessionId(it) == cleanSessionId }
            .distinct()
        if (ownerIds.isEmpty()) {
            return listOf(
                ProotOwnerTerminationResult(
                    ownerId = fallbackOwnerId,
                    outcome = ProotOwnerTerminationOutcome.OWNER_NOT_FOUND,
                    reason = "terminal_owner_not_observed"
                )
            )
        }
        return ownerIds.map { ownerId -> terminate(context, ownerId) }
    }

    fun terminate(context: Context, ownerId: String): ProotOwnerTerminationResult {
        val cleanOwnerId = ownerId.trim()
        if (cleanOwnerId.isBlank()) {
            return ProotOwnerTerminationResult(
                ownerId = "",
                outcome = ProotOwnerTerminationOutcome.FAILED,
                reason = "owner_id_missing"
            )
        }

        val appContext = context.applicationContext
        val initial = discover(appContext, cleanOwnerId)
        if (initial is OwnerDiscovery.Unavailable) {
            return unavailableResult(cleanOwnerId, initial.reason)
        }
        if (initial is OwnerDiscovery.Missing) {
            return ProotOwnerTerminationResult(
                ownerId = cleanOwnerId,
                outcome = ProotOwnerTerminationOutcome.OWNER_NOT_FOUND,
                reason = initial.reason
            )
        }
        initial as OwnerDiscovery.Ready
        if (initial.targets.isEmpty()) {
            return ProotOwnerTerminationResult(
                ownerId = cleanOwnerId,
                outcome = ProotOwnerTerminationOutcome.TELEMETRY_UNAVAILABLE,
                reason = "owner_has_no_signal_target"
            )
        }

        var allTargets = initial.targets
        var latestLive = initial.targets
        var sentTerminate = false
        var sentKill = false
        var healthySilentRounds = 0
        val deadline = System.currentTimeMillis() + OWNER_STOP_DEADLINE_MS

        Logger.i(
            LOG_TAG,
            "stop owner=$cleanOwnerId tracees=${allTargets.traceePids.joinToString(",")} " +
                "pgids=${allTargets.processGroupIds.joinToString(",")} deadlineMs=$OWNER_STOP_DEADLINE_MS"
        )

        for (round in 0 until OWNER_STOP_MAX_ROUNDS) {
            if (System.currentTimeMillis() >= deadline) break

            when (val discovery = discover(appContext, cleanOwnerId)) {
                is OwnerDiscovery.Unavailable -> {
                    return unavailableResult(
                        ownerId = cleanOwnerId,
                        reason = discovery.reason,
                        targets = allTargets,
                        remaining = latestLive,
                        sentTerminate = sentTerminate,
                        sentKill = sentKill
                    )
                }
                is OwnerDiscovery.Ready -> {
                    val before = allTargets
                    allTargets += discovery.targets
                    if (allTargets != before) healthySilentRounds = 0
                }
                is OwnerDiscovery.Missing -> Unit
            }

            val probe = probeUbuntuLiveTargets(appContext, allTargets)
            if (!probe.reliable) {
                return ProotOwnerTerminationResult(
                    ownerId = cleanOwnerId,
                    outcome = ProotOwnerTerminationOutcome.PROBE_UNAVAILABLE,
                    targetTraceePids = allTargets.traceePids,
                    targetProcessGroupIds = allTargets.processGroupIds,
                    remainingTraceePids = latestLive.traceePids,
                    remainingProcessGroupIds = latestLive.processGroupIds,
                    sentTerminate = sentTerminate,
                    sentKill = sentKill,
                    reason = probe.reason
                )
            }
            latestLive = probe.liveTargets

            val verificationDiscovery = discover(appContext, cleanOwnerId)
            if (verificationDiscovery is OwnerDiscovery.Unavailable) {
                return unavailableResult(
                    cleanOwnerId,
                    verificationDiscovery.reason,
                    allTargets,
                    latestLive,
                    sentTerminate,
                    sentKill
                )
            }
            val newlyDiscovered = if (verificationDiscovery is OwnerDiscovery.Ready) {
                val before = allTargets
                allTargets += verificationDiscovery.targets
                allTargets != before
            } else {
                false
            }
            if (newlyDiscovered) {
                healthySilentRounds = 0
                continue
            }
            if (latestLive.isEmpty()) {
                healthySilentRounds += 1
                if (
                    ProotOwnerTerminationEvidence.canConfirm(
                        ownerWasObserved = true,
                        healthySilentRounds = healthySilentRounds,
                        probeReliable = true,
                        liveTraceePids = latestLive.traceePids,
                        liveProcessGroupIds = latestLive.processGroupIds
                    )
                ) {
                    return confirmAndRetire(
                        context = appContext,
                        ownerId = cleanOwnerId,
                        allTargets = allTargets,
                        sentTerminate = sentTerminate,
                        sentKill = sentKill
                    )
                }
                Thread.sleep(SILENCE_CONFIRM_MS)
                continue
            }
            healthySilentRounds = 0

            val signal = if (!sentTerminate) OwnerSignal.TERM else OwnerSignal.KILL
            val signalTargets = allTargets + latestLive
            val signalResult = signalUbuntuTargets(appContext, signalTargets, signal)
            allTargets += signalResult.discoveredTargets
            if (signal == OwnerSignal.TERM) sentTerminate = signalResult.markerObserved
            if (signal == OwnerSignal.KILL) sentKill = sentKill || signalResult.markerObserved
            if (signalResult.timedOut) {
                return ProotOwnerTerminationResult(
                    ownerId = cleanOwnerId,
                    outcome = ProotOwnerTerminationOutcome.TIMEOUT,
                    targetTraceePids = allTargets.traceePids,
                    targetProcessGroupIds = allTargets.processGroupIds,
                    remainingTraceePids = latestLive.traceePids,
                    remainingProcessGroupIds = latestLive.processGroupIds,
                    sentTerminate = sentTerminate,
                    sentKill = sentKill,
                    reason = "owner_signal_${signal.shellName.lowercase()}_timeout"
                )
            }
            if (!signalResult.markerObserved || signalResult.exitCode != 0) {
                return ProotOwnerTerminationResult(
                    ownerId = cleanOwnerId,
                    outcome = ProotOwnerTerminationOutcome.FAILED,
                    targetTraceePids = allTargets.traceePids,
                    targetProcessGroupIds = allTargets.processGroupIds,
                    remainingTraceePids = latestLive.traceePids,
                    remainingProcessGroupIds = latestLive.processGroupIds,
                    sentTerminate = sentTerminate,
                    sentKill = sentKill,
                    reason = "owner_signal_${signal.shellName.lowercase()}_failed_${signalResult.exitCode}"
                )
            }
            Thread.sleep(if (round == 0) TERM_GRACE_MS else KILL_GRACE_MS)
        }

        val finalProbe = probeUbuntuLiveTargets(appContext, allTargets)
        return ProotOwnerTerminationResult(
            ownerId = cleanOwnerId,
            outcome = if (finalProbe.reliable) {
                ProotOwnerTerminationOutcome.TIMEOUT
            } else {
                ProotOwnerTerminationOutcome.PROBE_UNAVAILABLE
            },
            targetTraceePids = allTargets.traceePids,
            targetProcessGroupIds = allTargets.processGroupIds,
            remainingTraceePids = finalProbe.liveTargets.traceePids,
            remainingProcessGroupIds = finalProbe.liveTargets.processGroupIds,
            sentTerminate = sentTerminate,
            sentKill = sentKill,
            reason = if (finalProbe.reliable) "owner_stop_deadline_reached" else finalProbe.reason
        )
    }

    private fun discover(context: Context, ownerId: String): OwnerDiscovery {
        val snapshot = ProotTelemetryStore.refreshBlocking(context)
        val readiness = ProotOwnerTerminationEvidence.readiness(snapshot)
        if (!readiness.usable) return OwnerDiscovery.Unavailable(readiness.reason)
        val group = snapshot.ownerProcessIndex.groups.firstOrNull { it.ownerId == ownerId }
            ?: return OwnerDiscovery.Missing()
        return OwnerDiscovery.Ready(
            OwnerSignalTargets(
                traceePids = group.traceePids.validProcessIds(),
                processGroupIds = group.processGroupIds.validProcessIds()
            )
        )
    }

    private fun signalUbuntuTargets(
        context: Context,
        targets: OwnerSignalTargets,
        signal: OwnerSignal
    ): OwnerSignalResult {
        val result = executeUbuntuShell(context, buildUbuntuSignalPayload(targets, signal))
        return OwnerSignalResult(
            discoveredTargets = OwnerSignalTargets(
                traceePids = parseTaggedPidList(result.output, "__kite_owner_signal_targets:"),
                processGroupIds = parseTaggedPidList(result.output, "__kite_owner_signal_pgids:")
            ),
            markerObserved = result.output.contains("__kite_owner_signal_sent:${signal.shellName}"),
            timedOut = result.timedOut,
            exitCode = result.exitCode
        )
    }

    private fun probeUbuntuLiveTargets(context: Context, targets: OwnerSignalTargets): OwnerProbeResult {
        if (targets.isEmpty()) return OwnerProbeResult(OwnerSignalTargets(), true, "no_targets")
        val result = executeUbuntuShell(context, buildUbuntuLiveProbePayload(targets))
        val markerObserved = result.output.contains("__kite_probe_complete:true")
        return OwnerProbeResult(
            liveTargets = OwnerSignalTargets(
                traceePids = parseTaggedPidList(result.output, "__kite_probe_remaining:"),
                processGroupIds = parseTaggedPidList(result.output, "__kite_probe_remaining_pgid:")
            ),
            reliable = markerObserved && !result.timedOut && result.exitCode == 0,
            reason = when {
                result.timedOut -> "owner_probe_timeout"
                !markerObserved -> "owner_probe_marker_missing"
                result.exitCode != 0 -> "owner_probe_exit_${result.exitCode}"
                else -> "owner_probe_complete"
            }
        )
    }

    private fun confirmAndRetire(
        context: Context,
        ownerId: String,
        allTargets: OwnerSignalTargets,
        sentTerminate: Boolean,
        sentKill: Boolean
    ): ProotOwnerTerminationResult {
        val retired = ProotTelemetryStore.retireOwnerTracees(
            context = context,
            ownerId = ownerId,
            reason = "owner_direct_probe_confirmed_exit"
        )
        Logger.i(LOG_TAG, "retire owner after direct Ubuntu /proc proof: ${retired.summary()}")
        val after = discover(context, ownerId)
        if (after is OwnerDiscovery.Unavailable) {
            return unavailableResult(ownerId, after.reason, allTargets, OwnerSignalTargets(), sentTerminate, sentKill)
        }
        if (after is OwnerDiscovery.Ready && !after.targets.isEmpty()) {
            return ProotOwnerTerminationResult(
                ownerId = ownerId,
                outcome = ProotOwnerTerminationOutcome.FAILED,
                targetTraceePids = allTargets.traceePids,
                targetProcessGroupIds = allTargets.processGroupIds,
                remainingTraceePids = after.targets.traceePids,
                remainingProcessGroupIds = after.targets.processGroupIds,
                sentTerminate = sentTerminate,
                sentKill = sentKill,
                reason = "owner_reappeared_after_retire"
            )
        }
        return ProotOwnerTerminationResult(
            ownerId = ownerId,
            outcome = ProotOwnerTerminationOutcome.CONFIRMED,
            targetTraceePids = allTargets.traceePids,
            targetProcessGroupIds = allTargets.processGroupIds,
            sentTerminate = sentTerminate,
            sentKill = sentKill,
            reason = "owner_stably_silent_after_direct_probe"
        )
    }

    private fun unavailableResult(
        ownerId: String,
        reason: String,
        targets: OwnerSignalTargets = OwnerSignalTargets(),
        remaining: OwnerSignalTargets = OwnerSignalTargets(),
        sentTerminate: Boolean = false,
        sentKill: Boolean = false
    ): ProotOwnerTerminationResult = ProotOwnerTerminationResult(
        ownerId = ownerId,
        outcome = ProotOwnerTerminationOutcome.TELEMETRY_UNAVAILABLE,
        targetTraceePids = targets.traceePids,
        targetProcessGroupIds = targets.processGroupIds,
        remainingTraceePids = remaining.traceePids,
        remainingProcessGroupIds = remaining.processGroupIds,
        sentTerminate = sentTerminate,
        sentKill = sentKill,
        reason = reason
    )

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
            if (!finished) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            ShellCommandResult(
                exitCode = if (finished) process.exitValue() else -1,
                output = output,
                timedOut = !finished
            )
        }.getOrElse { error ->
            Logger.i(LOG_TAG, "Ubuntu owner shell failed: ${error.message}")
            ShellCommandResult(exitCode = -1, output = error.message.orEmpty(), timedOut = false)
        }
    }

    private fun buildUbuntuSignalPayload(targets: OwnerSignalTargets, signal: OwnerSignal): String {
        val pids = targets.traceePids.joinToString(" ")
        val pgids = targets.processGroupIds.joinToString(" ")
        return """
            kf_owner_pids='$pids'
            kf_owner_pgids='$pgids'
            kf_signal='${signal.shellName}'
            kf_signal_targets=""
            kf_signal_pgids=""
            kf_self_pgid=${'$'}(ps -o pgid= -p "${'$'}${'$'}" 2>/dev/null | tr -d ' ')
            kf_add_pid() {
              case "${'$'}1" in ''|*[!0-9]*|0|1) return 0 ;; esac
              case " ${'$'}kf_signal_targets " in *" ${'$'}1 "*) ;; *) kf_signal_targets="${'$'}kf_signal_targets ${'$'}1" ;; esac
            }
            kf_add_pgid() {
              case "${'$'}1" in ''|*[!0-9]*|0|1) return 0 ;; esac
              [ "${'$'}1" = "${'$'}kf_self_pgid" ] && return 0
              case " ${'$'}kf_signal_pgids " in *" ${'$'}1 "*) ;; *) kf_signal_pgids="${'$'}kf_signal_pgids ${'$'}1" ;; esac
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
                  kf_add_pid "${'$'}kf_parent"
                  for kf_child in ${'$'}(kf_children_of "${'$'}kf_parent"); do
                    case " ${'$'}kf_seen " in
                      *" ${'$'}kf_child "*) ;;
                      *) kf_seen="${'$'}kf_seen ${'$'}kf_child"; kf_next="${'$'}kf_next ${'$'}kf_child" ;;
                    esac
                  done
                done
                kf_todo="${'$'}kf_next"
              done
            }
            for kf_pid in ${'$'}kf_owner_pids; do kf_collect_tree "${'$'}kf_pid"; done
            for kf_pgid in ${'$'}kf_owner_pgids; do kf_add_pgid "${'$'}kf_pgid"; done
            for kf_pgid in ${'$'}kf_signal_pgids; do kill -"${'$'}kf_signal" -- "-${'$'}kf_pgid" >/dev/null 2>&1 || true; done
            for kf_pid in ${'$'}kf_signal_targets; do
              [ "${'$'}kf_pid" = "${'$'}${'$'}" ] && continue
              [ -n "${'$'}PPID" ] && [ "${'$'}kf_pid" = "${'$'}PPID" ] && continue
              kill -"${'$'}kf_signal" "${'$'}kf_pid" >/dev/null 2>&1 || true
            done
            printf '__kite_owner_signal_targets:%s\n' "${'$'}(printf '%s\n' ${'$'}kf_signal_targets | tr ' ' ',' | sed 's/^,*//;s/,*${'$'}//')"
            printf '__kite_owner_signal_pgids:%s\n' "${'$'}(printf '%s\n' ${'$'}kf_signal_pgids | tr ' ' ',' | sed 's/^,*//;s/,*${'$'}//')"
            printf '__kite_owner_signal_sent:%s\n' "${'$'}kf_signal"
        """.trimIndent()
    }

    private fun buildUbuntuLiveProbePayload(targets: OwnerSignalTargets): String {
        val pids = targets.traceePids.joinToString(" ")
        val pgids = targets.processGroupIds.joinToString(" ")
        return """
            kf_owner_pids='$pids'
            kf_owner_pgids='$pgids'
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
            kf_remaining_pgids=${'$'}(
              for kf_pgid in ${'$'}kf_owner_pgids; do
                kill -0 -- "-${'$'}kf_pgid" >/dev/null 2>&1 && printf '%s\n' "${'$'}kf_pgid"
              done
            )
            printf '__kite_probe_remaining:%s\n' "${'$'}(printf '%s\n' "${'$'}kf_remaining" | tr '\n' ',')"
            printf '__kite_probe_remaining_pgid:%s\n' "${'$'}(printf '%s\n' "${'$'}kf_remaining_pgids" | tr '\n' ',')"
            printf '__kite_probe_complete:true\n'
        """.trimIndent()
    }

    private fun parseTaggedPidList(output: String, prefix: String): List<Int> = output
        .lineSequence()
        .filter { it.startsWith(prefix) }
        .lastOrNull()
        ?.substringAfter(':')
        ?.split(',')
        .orEmpty()
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { pid -> pid > 1 } }
        .validProcessIds()

    private fun Collection<Int>.validProcessIds(): List<Int> =
        filter { it > 1 }.distinct().sorted()
}
