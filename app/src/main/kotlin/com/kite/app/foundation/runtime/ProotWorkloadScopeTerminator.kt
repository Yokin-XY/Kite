package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import java.io.File

enum class ProotWorkloadScopeTerminationOutcome {
    CONFIRMED,
    SCOPE_NOT_FOUND,
    TELEMETRY_UNAVAILABLE,
    IDENTITY_UNAVAILABLE,
    STILL_RUNNING,
}

data class ProotWorkloadScopeTerminationResult(
    val workloadScopeId: String,
    val outcome: ProotWorkloadScopeTerminationOutcome,
    val targetLifecycleIds: List<String> = emptyList(),
    val remainingLifecycleIds: List<String> = emptyList(),
    val reason: String,
) {
    val settled: Boolean
        get() = outcome == ProotWorkloadScopeTerminationOutcome.CONFIRMED ||
            outcome == ProotWorkloadScopeTerminationOutcome.SCOPE_NOT_FOUND
}

/**
 * 结束系统生成的工作负载作用域。
 *
 * 每轮都从 PRoot 状态拥有者重新解析成员，随后逐个核验强生命周期；不按名称搜索，
 * 也不把 PGID 当成未经证明的 kill 范围。有限重读用于接住停止期间新派生的子进程。
 */
object ProotWorkloadScopeTerminator {
    private const val LOG_TAG = "ProotWorkloadStop"
    private const val MAX_DISCOVERY_ROUNDS = 3

    fun terminate(
        context: Context,
        workloadScopeId: String,
    ): ProotWorkloadScopeTerminationResult {
        val scopeId = workloadScopeId.trim()
        if (scopeId.isBlank()) {
            return result(scopeId, ProotWorkloadScopeTerminationOutcome.IDENTITY_UNAVAILABLE, "scope_id_missing")
        }

        val appContext = context.applicationContext
        val targeted = linkedSetOf<String>()
        var observed = false
        var lastReason = "scope_not_observed"

        repeat(MAX_DISCOVERY_ROUNDS) {
            val snapshot = ProotTelemetryStore.refreshBlocking(appContext)
            val readiness = ProotOwnerTerminationEvidence.readiness(snapshot)
            if (!readiness.usable) {
                return result(
                    scopeId,
                    ProotWorkloadScopeTerminationOutcome.TELEMETRY_UNAVAILABLE,
                    readiness.reason,
                    targeted,
                ).also(::logResult)
            }
            val group = snapshot.workloadScopeIndex.groups.firstOrNull { it.workloadScopeId == scopeId }
            if (group == null) {
                return result(
                    scopeId,
                    if (observed) ProotWorkloadScopeTerminationOutcome.CONFIRMED
                    else ProotWorkloadScopeTerminationOutcome.SCOPE_NOT_FOUND,
                    if (observed) "workload_scope_quiet" else "workload_scope_not_observed",
                    targeted,
                ).also(::logResult)
            }
            observed = true

            if (group.telemetrySessionIds.isEmpty() || snapshot.activeRegistryRootPath.isBlank()) {
                return result(
                    scopeId,
                    ProotWorkloadScopeTerminationOutcome.TELEMETRY_UNAVAILABLE,
                    "workload_target_registry_binding_missing",
                    targeted,
                ).also(::logResult)
            }
            val registry = ProotActiveRegistryReader(File(snapshot.activeRegistryRootPath))
                .readSessions(group.telemetrySessionIds)
            if (!registry.complete) {
                return result(
                    scopeId,
                    ProotWorkloadScopeTerminationOutcome.TELEMETRY_UNAVAILABLE,
                    "workload_target_registry_${registry.status.name.lowercase()}",
                    targeted,
                ).also(::logResult)
            }
            val registryEntries = registry.sessions.flatMap(ProotActiveRegistrySession::entries)
            val targetSessions = group.telemetrySessionIds.toSet()
            val registryScopes = ProotWorkloadScopeProjector.projectRegistry(
                entries = registryEntries,
                historicalRecords = snapshot.tracees.filter { record ->
                    record.telemetrySessionId in targetSessions
                },
            )

            val telemetryTargets = snapshot.processLiveTable.entries
                .asSequence()
                .filter { entry ->
                    entry.state == ProotLiveProcessState.RUNNING && entry.workloadScopeId == scopeId
                }
                .map { entry ->
                    ProotProcessControlTarget(
                        ref = entry.processRef(),
                        parentHostPid = entry.parentTraceePid?.takeIf { it > 1 },
                        processGroupId = entry.processGroupId,
                    )
                }
                .distinctBy { it.ref.lifecycleId }
                .toList()
            val registryTargets = registryEntries
                .asSequence()
                .filter { entry -> registryScopes[entry.lifecycleId] == scopeId }
                .map { entry -> entry.toControlTarget() }
                .toList()
            val targets = (telemetryTargets + registryTargets)
                .distinctBy { it.ref.lifecycleId }

            if (targets.isEmpty() || targets.any { !it.ref.hasStrongIdentity }) {
                return result(
                    scopeId,
                    ProotWorkloadScopeTerminationOutcome.IDENTITY_UNAVAILABLE,
                    "workload_scope_strong_identity_required",
                    targeted,
                    targets.map { it.ref.lifecycleId },
                ).also(::logResult)
            }
            targeted += targets.map { it.ref.lifecycleId }
            val termination = ProotDirectProcessTreeTerminator.terminate(appContext, targets)
            lastReason = termination.reason
        }

        val finalSnapshot = ProotTelemetryStore.refreshBlocking(appContext)
        val remaining = finalSnapshot.processLiveTable.entries
            .filter { it.state == ProotLiveProcessState.RUNNING && it.workloadScopeId == scopeId }
            .map(ProotLiveProcessEntry::lifecycleId)
            .distinct()
            .sorted()
        return result(
            scopeId,
            if (remaining.isEmpty()) ProotWorkloadScopeTerminationOutcome.CONFIRMED
            else ProotWorkloadScopeTerminationOutcome.STILL_RUNNING,
            if (remaining.isEmpty()) "workload_scope_quiet" else lastReason,
            targeted,
            remaining,
        ).also(::logResult)
    }

    private fun result(
        scopeId: String,
        outcome: ProotWorkloadScopeTerminationOutcome,
        reason: String,
        targeted: Collection<String> = emptyList(),
        remaining: Collection<String> = emptyList(),
    ): ProotWorkloadScopeTerminationResult = ProotWorkloadScopeTerminationResult(
        workloadScopeId = scopeId,
        outcome = outcome,
        targetLifecycleIds = targeted.distinct().sorted(),
        remainingLifecycleIds = remaining.distinct().sorted(),
        reason = reason,
    )

    private fun logResult(result: ProotWorkloadScopeTerminationResult) {
        val message = "scope=${result.workloadScopeId} outcome=${result.outcome} " +
            "targets=${result.targetLifecycleIds.size} remaining=${result.remainingLifecycleIds.size} " +
            "reason=${result.reason}"
        if (result.settled) Logger.i(LOG_TAG, message) else Logger.e(LOG_TAG, message)
    }

    private fun ProotActiveTraceeEntry.toControlTarget(): ProotProcessControlTarget =
        ProotProcessControlTarget(
            ref = processRef(),
            parentHostPid = parentTraceePid?.takeIf { it > 1 },
            processGroupId = processGroupId,
        )
}
