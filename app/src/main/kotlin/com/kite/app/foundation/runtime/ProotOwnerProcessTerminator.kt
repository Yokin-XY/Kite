package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.jni.KFJni
import com.kite.app.foundation.logging.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

enum class ProotOwnerTerminationOutcome {
    CONFIRMED,
    OWNER_NOT_FOUND,
    TELEMETRY_UNAVAILABLE,
    PROBE_UNAVAILABLE,
    STILL_RUNNING,
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

    val settled: Boolean
        get() = outcome == ProotOwnerTerminationOutcome.CONFIRMED ||
            outcome == ProotOwnerTerminationOutcome.OWNER_NOT_FOUND

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
        now: Long = System.currentTimeMillis(),
        ownerId: String? = null
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
        val activeRegistryComplete = snapshot.activeRegistryStatus == "loaded" &&
            snapshot.activeRegistryReconciledAtMs > 0L &&
            snapshot.activeRegistryUnstableSessions.isEmpty()
        if (snapshot.counters.parseErrors > 0L && !activeRegistryComplete) {
            return ProotTelemetryDestructiveReadiness(false, "telemetry_parse_errors")
        }
        if (activeRegistryComplete) {
            return ProotTelemetryDestructiveReadiness(true, "active_registry_complete")
        }
        val coverageStart = snapshot.ownerEvidenceCompleteFromMs
        if (coverageStart > 0L && ownerId != null) {
            val generation = RuntimeOwnerIdentity.generation(ownerId)
                ?: return ProotTelemetryDestructiveReadiness(false, "owner_generation_unavailable")
            if (generation < coverageStart) {
                return ProotTelemetryDestructiveReadiness(false, "owner_predates_telemetry_coverage")
            }
        } else if (snapshot.counters.skippedBytes > 0L && coverageStart <= 0L && ownerId != null) {
            return ProotTelemetryDestructiveReadiness(false, "telemetry_coverage_unknown")
        }
        return ProotTelemetryDestructiveReadiness(
            usable = true,
            reason = if (coverageStart > 0L) "telemetry_owner_coverage_available" else "telemetry_complete"
        )
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

/** 执行窗口只限制升级动作；最终状态必须由目标事实决定。 */
internal object ProotOwnerTerminationDecision {
    fun finalOutcome(
        remainingTraceePids: Collection<Int>,
        remainingProcessGroupIds: Collection<Int>,
    ): ProotOwnerTerminationOutcome = if (
        remainingTraceePids.none { it > 1 } &&
        remainingProcessGroupIds.none { it > 1 }
    ) {
        ProotOwnerTerminationOutcome.CONFIRMED
    } else {
        ProotOwnerTerminationOutcome.STILL_RUNNING
    }
}

object ProotOwnerProcessTerminator {
    private const val LOG_TAG = "ProotOwnerProcessStop"
    private const val SILENCE_CONFIRM_MS = 220L
    private const val DIRECT_OWNER_STOP_DEADLINE_MS = 3_500L
    private const val DIRECT_OWNER_MAX_ROUNDS = 8
    private const val DIRECT_TERM_GRACE_MS = 300L
    private const val DIRECT_KILL_GRACE_MS = 180L
    private val RESIDUAL_REAP_DELAYS_MS = longArrayOf(250L, 1_000L, 3_000L)
    private val ownersBeingReaped = ConcurrentHashMap.newKeySet<String>()

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
        val results = ownerIds.map { ownerId -> terminate(context, ownerId) }
        scheduleResidualReap(
            context = context,
            ownerIds = results.filterNot { it.settled }.map { it.ownerId }
        )
        return results
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

        return terminateDirectV2(context.applicationContext, cleanOwnerId).also { result ->
            if (result.settled) {
                TaskManagerStore.confirmOwnersStopped(listOf(cleanOwnerId))
            }
        }
    }

    /**
     * v2 正常路径：只使用 owner 已登记的稳定引用，逐个核验 starttime 后直接发信号。
     * 不启动 Ubuntu Shell、不跑 `ps`、不枚举 `/proc`；新派生进程会在下一轮事件刷新中加入。
     */
    private fun terminateDirectV2(
        context: Context,
        ownerId: String,
    ): ProotOwnerTerminationResult {
        val firstSnapshot = ProotTelemetryStore.refreshBlocking(context)
        val readiness = ProotOwnerTerminationEvidence.readiness(firstSnapshot, ownerId = ownerId)
        if (!readiness.usable) return unavailableResult(ownerId, readiness.reason)
        val firstGroup = firstSnapshot.ownerProcessIndex.groups.firstOrNull { it.ownerId == ownerId }
            ?: return ProotOwnerTerminationResult(
                ownerId = ownerId,
                outcome = ProotOwnerTerminationOutcome.OWNER_NOT_FOUND,
                reason = "owner_not_observed",
            )
        if (firstGroup.processRefs.isEmpty() || firstGroup.telemetrySessionIds.isEmpty()) {
            return unavailableResult(ownerId, "owner_v2_registry_binding_missing")
        }
        if (firstGroup.processRefs.any { !it.hasStrongIdentity }) {
            return unavailableResult(ownerId, "owner_v2_strong_identity_required")
        }

        val verifier = ProotProcessVerifier()
        val backend = ProotProcessControlBackend(
            verifier = ProotProcessIdentityVerifier(verifier::verify),
            signalSender = ProotProcessSignalSender { pid, signal ->
                runCatching { KFJni.sendSignal(pid, signal) }.getOrDefault(false)
            },
        )
        val allTargets = linkedMapOf<String, ProotProcessControlTarget>()
        val telemetrySessionIds = firstGroup.telemetrySessionIds.toMutableSet()
        firstGroup.toControlTargets(firstSnapshot).forEach { target ->
            allTargets[target.ref.lifecycleId] = target
        }
        var sentTerminate = false
        var sentKill = false
        var silentRounds = 0
        val deadline = System.currentTimeMillis() + DIRECT_OWNER_STOP_DEADLINE_MS

        Logger.i(LOG_TAG, "direct owner stop owner=$ownerId mode=identity_v2")
        repeat(DIRECT_OWNER_MAX_ROUNDS) {
            if (System.currentTimeMillis() >= deadline) return@repeat
            val snapshot = ProotTelemetryStore.refreshBlocking(context)
            val currentGroup = snapshot.ownerProcessIndex.groups.firstOrNull { it.ownerId == ownerId }
            currentGroup?.telemetrySessionIds?.let(telemetrySessionIds::addAll)
            val telemetryTargets = currentGroup?.toControlTargets(snapshot).orEmpty()
            if (telemetryTargets.any { !it.ref.hasStrongIdentity }) {
                return ProotOwnerTerminationResult(
                    ownerId = ownerId,
                    outcome = ProotOwnerTerminationOutcome.TELEMETRY_UNAVAILABLE,
                    targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                    targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                    remainingTraceePids = telemetryTargets.map { it.ref.hostPid }.validProcessIds(),
                    remainingProcessGroupIds = telemetryTargets.mapNotNull { it.processGroupId }.validProcessIds(),
                    sentTerminate = sentTerminate,
                    sentKill = sentKill,
                    reason = "owner_v2_identity_became_incomplete",
                )
            }
            telemetryTargets.forEach { target -> allTargets[target.ref.lifecycleId] = target }

            val registry = ProotActiveRegistryReader(File(snapshot.activeRegistryRootPath))
                .readSessions(telemetrySessionIds)
            if (!registry.complete) {
                return ProotOwnerTerminationResult(
                    ownerId = ownerId,
                    outcome = ProotOwnerTerminationOutcome.TELEMETRY_UNAVAILABLE,
                    targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                    targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                    remainingTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                    remainingProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                    sentTerminate = sentTerminate,
                    sentKill = sentKill,
                    reason = "owner_target_registry_${registry.status.name.lowercase()}",
                )
            }
            val registryTargets = registry.sessions
                .flatMap(ProotActiveRegistrySession::entries)
                .filter { it.kfRuntimeId == ownerId }
                .map { it.toControlTarget() }
            if (registryTargets.any { !it.ref.hasStrongIdentity }) {
                return unavailableResult(ownerId, "owner_target_registry_identity_incomplete")
            }
            registryTargets.forEach { target -> allTargets[target.ref.lifecycleId] = target }

            val verifications = ProotTelemetryStore.verifyProcessRefs(
                context,
                allTargets.values.map { it.ref },
                refreshFirst = false,
            )
            val verificationById = verifications.associateBy { it.ref.lifecycleId }
            val unreadable = allTargets.values.filter { target ->
                verificationById[target.ref.lifecycleId]?.status == ProotProcessVerificationStatus.UNREADABLE
            }
            if (unreadable.isNotEmpty()) {
                return ProotOwnerTerminationResult(
                    ownerId = ownerId,
                    outcome = ProotOwnerTerminationOutcome.PROBE_UNAVAILABLE,
                    targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                    targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                    remainingTraceePids = unreadable.map { it.ref.hostPid }.validProcessIds(),
                    remainingProcessGroupIds = unreadable.mapNotNull { it.processGroupId }.validProcessIds(),
                    sentTerminate = sentTerminate,
                    sentKill = sentKill,
                    reason = "owner_targeted_proc_unreadable",
                )
            }
            val remaining = allTargets.values.filter { target ->
                verificationById[target.ref.lifecycleId]?.status == ProotProcessVerificationStatus.MATCHED_ACTIVE
            }

            if (remaining.isEmpty() && registryTargets.isEmpty()) {
                silentRounds += 1
                if (silentRounds >= 2) {
                    return ProotOwnerTerminationResult(
                        ownerId = ownerId,
                        outcome = ProotOwnerTerminationOutcome.CONFIRMED,
                        targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                        targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                        sentTerminate = sentTerminate,
                        sentKill = sentKill,
                        reason = "owner_stably_silent_after_identity_probe",
                    )
                }
                Thread.sleep(SILENCE_CONFIRM_MS)
                return@repeat
            }
            silentRounds = 0

            val signal = if (!sentTerminate) ProotControlSignal.TERM else ProotControlSignal.KILL
            val signalResult = backend.signal(remaining, signal)
            ProotTelemetryStore.applyProcessVerifications(
                context,
                signalResult.attempts.map(ProotSignalAttempt::verification),
            )
            if (signal == ProotControlSignal.TERM) {
                sentTerminate = signalResult.sentHostPids.isNotEmpty()
            } else {
                sentKill = sentKill || signalResult.sentHostPids.isNotEmpty()
            }
            Thread.sleep(if (signal == ProotControlSignal.TERM) DIRECT_TERM_GRACE_MS else DIRECT_KILL_GRACE_MS)
        }

        val finalSnapshot = ProotTelemetryStore.refreshBlocking(context)
        val finalGroup = finalSnapshot.ownerProcessIndex.groups.firstOrNull { it.ownerId == ownerId }
        finalGroup?.telemetrySessionIds?.let(telemetrySessionIds::addAll)
        val finalTelemetryTargets = finalGroup?.toControlTargets(finalSnapshot).orEmpty()
        if (finalTelemetryTargets.any { !it.ref.hasStrongIdentity }) {
            return unavailableResult(ownerId, "owner_v2_identity_became_incomplete_at_deadline")
        }
        finalTelemetryTargets.forEach { target -> allTargets[target.ref.lifecycleId] = target }
        val finalRegistry = ProotActiveRegistryReader(File(finalSnapshot.activeRegistryRootPath))
            .readSessions(telemetrySessionIds)
        if (!finalRegistry.complete) {
            return ProotOwnerTerminationResult(
                ownerId = ownerId,
                outcome = ProotOwnerTerminationOutcome.TELEMETRY_UNAVAILABLE,
                targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                remainingTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                remainingProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                sentTerminate = sentTerminate,
                sentKill = sentKill,
                reason = "owner_target_registry_${finalRegistry.status.name.lowercase()}_at_deadline",
            )
        }
        val finalRegistryTargets = finalRegistry.sessions
            .flatMap(ProotActiveRegistrySession::entries)
            .filter { it.kfRuntimeId == ownerId }
            .map { it.toControlTarget() }
        if (finalRegistryTargets.any { !it.ref.hasStrongIdentity }) {
            return unavailableResult(ownerId, "owner_target_registry_identity_incomplete_at_deadline")
        }
        finalRegistryTargets.forEach { target -> allTargets[target.ref.lifecycleId] = target }
        val finalVerifications = ProotTelemetryStore.verifyProcessRefs(
            context,
            allTargets.values.map { it.ref },
            refreshFirst = false,
        )
        val unreadable = finalVerifications.filter {
            it.status == ProotProcessVerificationStatus.UNREADABLE
        }
        if (unreadable.isNotEmpty()) {
            return ProotOwnerTerminationResult(
                ownerId = ownerId,
                outcome = ProotOwnerTerminationOutcome.PROBE_UNAVAILABLE,
                targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                remainingTraceePids = unreadable.map { it.ref.hostPid }.validProcessIds(),
                remainingProcessGroupIds = unreadable.mapNotNull { result ->
                    allTargets[result.ref.lifecycleId]?.processGroupId
                }.validProcessIds(),
                sentTerminate = sentTerminate,
                sentKill = sentKill,
                reason = "owner_targeted_proc_unreadable_at_deadline",
            )
        }
        val finalVerificationById = finalVerifications.associateBy { it.ref.lifecycleId }
        val remaining = allTargets.values.filter { target ->
            finalVerificationById[target.ref.lifecycleId]?.status == ProotProcessVerificationStatus.MATCHED_ACTIVE
        }
        val remainingTraceePids = remaining.map { it.ref.hostPid }.validProcessIds()
        val remainingProcessGroupIds = remaining.mapNotNull { it.processGroupId }.validProcessIds()
        val finalOutcome = ProotOwnerTerminationDecision.finalOutcome(
            remainingTraceePids = remainingTraceePids,
            remainingProcessGroupIds = remainingProcessGroupIds,
        )
        if (finalOutcome == ProotOwnerTerminationOutcome.CONFIRMED) {
            return ProotOwnerTerminationResult(
                ownerId = ownerId,
                outcome = ProotOwnerTerminationOutcome.CONFIRMED,
                targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
                targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
                sentTerminate = sentTerminate,
                sentKill = sentKill,
                reason = "owner_absent_at_final_identity_probe",
            )
        }
        return ProotOwnerTerminationResult(
            ownerId = ownerId,
            outcome = finalOutcome,
            targetTraceePids = allTargets.values.map { it.ref.hostPid }.validProcessIds(),
            targetProcessGroupIds = allTargets.values.mapNotNull { it.processGroupId }.validProcessIds(),
            remainingTraceePids = remainingTraceePids,
            remainingProcessGroupIds = remainingProcessGroupIds,
            sentTerminate = sentTerminate,
            sentKill = sentKill,
            reason = "owner_still_running_after_escalation",
        )
    }

    private fun ProotOwnerProcessGroup.toControlTargets(
        snapshot: ProotTelemetrySnapshot,
    ): List<ProotProcessControlTarget> {
        val entriesByLifecycle = snapshot.processLiveTable.entries
            .associateBy(ProotLiveProcessEntry::lifecycleId)
        return processRefs.map { ref ->
            val entry = entriesByLifecycle[ref.lifecycleId]
            ProotProcessControlTarget(
                ref = ref,
                parentHostPid = entry?.parentTraceePid,
                processGroupId = entry?.processGroupId,
            )
        }
    }

    private fun ProotActiveTraceeEntry.toControlTarget(): ProotProcessControlTarget =
        ProotProcessControlTarget(
            ref = processRef(),
            parentHostPid = parentTraceePid,
            processGroupId = processGroupId,
        )

    /** 旧代 owner 的补偿回收与前台实例状态解耦，并按 owner 去重。 */
    fun scheduleResidualReap(context: Context, ownerIds: Collection<String>) {
        val appContext = context.applicationContext
        ownerIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { ownerId ->
                if (!ownersBeingReaped.add(ownerId)) return@forEach
                thread(
                    name = "KiteOwnerReap-${ownerId.hashCode().toUInt().toString(16)}",
                    isDaemon = true
                ) {
                    try {
                        for ((index, delayMs) in RESIDUAL_REAP_DELAYS_MS.withIndex()) {
                            Thread.sleep(delayMs)
                            val result = terminate(appContext, ownerId)
                            Logger.i(
                                LOG_TAG,
                                "background reap owner=$ownerId attempt=${index + 1} " +
                                    "outcome=${result.outcome} reason=${result.reason}"
                            )
                            if (result.settled) break
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } finally {
                        ownersBeingReaped.remove(ownerId)
                    }
                }
            }
    }

    private fun unavailableResult(
        ownerId: String,
        reason: String,
    ): ProotOwnerTerminationResult = ProotOwnerTerminationResult(
        ownerId = ownerId,
        outcome = ProotOwnerTerminationOutcome.TELEMETRY_UNAVAILABLE,
        reason = reason
    )

    private fun Collection<Int>.validProcessIds(): List<Int> =
        filter { it > 1 }.distinct().sorted()
}
