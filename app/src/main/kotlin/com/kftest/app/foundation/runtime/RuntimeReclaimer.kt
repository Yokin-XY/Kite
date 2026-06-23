package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.service.BackgroundRuntimeHost
import com.kftest.app.foundation.service.BackgroundRuntimeKind
import java.util.LinkedHashMap

data class RuntimeReclaimerExecutionSnapshot(
    val lastExecutionAtMs: Long = 0L,
    val lastExecutionKind: String = "none",
    val lastTargetId: String = "none",
    val lastTargetTitle: String = "none",
    val lastReason: String = "none",
    val runtimeStopRequestCount: Int = 0,
    val unattributedTerminateRequestCount: Int = 0,
    val ownerProcessTerminateRequestCount: Int = 0,
    val skippedInFlightCount: Int = 0,
    val inFlightRuntimeCount: Int = 0,
    val inFlightPidCount: Int = 0,
    val inFlightOwnerCount: Int = 0
)

data class RuntimeReclaimerRuntimeReclaimResult(
    val executed: Boolean,
    val skippedInFlight: Boolean = false,
    val reason: String,
    val signal: String = "none",
    val targetMode: String = "none"
)

/**
 * Android-side memory pressure reclaimer.
 *
 * This reclaimer follows a workspace policy file. Registered background runtimes are the primary
 * targets; unattributed roots stay manual-only unless explicitly classified by policy.
 */
object RuntimeReclaimer {

    private const val LOG_TAG = "RuntimeReclaimer"
    private const val IN_FLIGHT_TTL_MS = 2 * 60_000L

    private val inFlightRuntimeIds = LinkedHashMap<String, Long>()
    private val inFlightPids = LinkedHashMap<Int, Long>()
    private val inFlightOwnerIds = LinkedHashMap<String, Long>()

    @Volatile
    private var lastReclaimAtMs: Long = 0L
    private var lastExecutionKind: String = "none"
    private var lastTargetId: String = "none"
    private var lastTargetTitle: String = "none"
    private var lastReason: String = "none"
    private var runtimeStopRequestCount: Int = 0
    private var unattributedTerminateRequestCount: Int = 0
    private var ownerProcessTerminateRequestCount: Int = 0
    private var skippedInFlightCount: Int = 0

    @Synchronized
    fun executionSnapshot(): RuntimeReclaimerExecutionSnapshot {
        val now = System.currentTimeMillis()
        pruneInFlight(now)
        return RuntimeReclaimerExecutionSnapshot(
            lastExecutionAtMs = lastReclaimAtMs,
            lastExecutionKind = lastExecutionKind,
            lastTargetId = lastTargetId,
            lastTargetTitle = lastTargetTitle,
            lastReason = lastReason,
            runtimeStopRequestCount = runtimeStopRequestCount,
            unattributedTerminateRequestCount = unattributedTerminateRequestCount,
            ownerProcessTerminateRequestCount = ownerProcessTerminateRequestCount,
            skippedInFlightCount = skippedInFlightCount,
            inFlightRuntimeCount = inFlightRuntimeIds.size,
            inFlightPidCount = inFlightPids.size,
            inFlightOwnerCount = inFlightOwnerIds.size
        )
    }

    fun onSnapshot(context: Context, snapshot: RuntimeHealthSnapshot) {
        val policy = RuntimeReclaimerPolicyStore.load(context.applicationContext)
        val lifecyclePlanHasExecutableCandidate = snapshot.lifecycleReclaimPlan.items.any { item ->
            item.disposition.canBeExecutedByReclaimer()
        }
        if (policy.activeProfile == RuntimeReclaimerProfile.OBSERVE_ONLY) {
            return
        }
        if (!policy.activeProfile.allowsReclaim(snapshot.pressure.level) &&
            !lifecyclePlanHasExecutableCandidate
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastReclaimAtMs < policy.activeProfile.minReclaimIntervalMs) {
            return
        }
        val decision = chooseLifecyclePlanCandidate(snapshot, policy, now)
            ?: chooseCandidate(snapshot, policy, now)
            ?: return
        synchronized(this) {
            pruneInFlight(now)
            when (decision) {
                is RuntimeStopDecision -> {
                    if (inFlightRuntimeIds.containsKey(decision.runtimeId)) {
                        skippedInFlightCount += 1
                        return
                    }
                    inFlightRuntimeIds[decision.runtimeId] = now
                }
                is UnattributedTerminateDecision -> {
                    if (inFlightPids.containsKey(decision.pid)) {
                        skippedInFlightCount += 1
                        return
                    }
                    inFlightPids[decision.pid] = now
                }
            }
            lastReclaimAtMs = now
        }

        val reason = buildReason(snapshot, policy, decision)
        when (decision) {
            is RuntimeStopDecision -> {
                Logger.i(LOG_TAG, "reclaim runtime=${decision.runtimeId} title=${decision.title} reason=$reason")
                BackgroundRuntimeHost.reclaimRuntime(
                    context = context.applicationContext,
                    runtimeId = decision.runtimeId,
                    reason = reason
                )
                recordExecutionRequest(decision, reason)
            }
            is UnattributedTerminateDecision -> {
                Logger.i(LOG_TAG, "reclaim unattributed pid=${decision.pid} title=${decision.title} reason=$reason")
                val termination = ContainerProcessStore.terminateForRuntimeReclaimer(
                    context = context.applicationContext,
                    pid = decision.pid,
                    processGroupId = decision.processGroupId,
                    force = false
                )
                if (termination.exited) {
                    recordExecutionRequest(decision, reason)
                } else {
                    synchronized(this) {
                        inFlightPids.remove(decision.pid)
                    }
                    Logger.i(
                        LOG_TAG,
                        "reclaim unattributed failed pid=${decision.pid} reason=${termination.reason}"
                    )
                }
            }
        }
    }

    fun reclaimRegisteredRuntime(
        context: Context,
        runtimeId: String,
        title: String,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): RuntimeReclaimerRuntimeReclaimResult {
        if (runtimeId.isBlank()) {
            return RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = "runtime_id_missing"
            )
        }
        synchronized(this) {
            pruneInFlight(now)
            if (inFlightRuntimeIds.containsKey(runtimeId)) {
                skippedInFlightCount += 1
                return RuntimeReclaimerRuntimeReclaimResult(
                    executed = false,
                    skippedInFlight = true,
                    reason = "runtime_reclaim_already_in_flight"
                )
            }
            inFlightRuntimeIds[runtimeId] = now
            lastReclaimAtMs = now
        }
        val decision = RuntimeStopDecision(
            runtimeId = runtimeId,
            title = title,
            retentionClass = com.kftest.app.foundation.service.RuntimeRetentionClass.EPHEMERAL,
            reclaimPriority = com.kftest.app.foundation.service.RuntimeRetentionClass.EPHEMERAL.reclaimPriority,
            rssKb = 0L,
            policyHint = "lease_strategy_activation"
        )
        Logger.i(LOG_TAG, "lease strategy reclaim runtime=$runtimeId title=$title reason=$reason")
        return runCatching {
            BackgroundRuntimeHost.reclaimRuntime(
                context = context.applicationContext,
                runtimeId = runtimeId,
                reason = reason
            )
            recordExecutionRequest(decision, reason)
            RuntimeReclaimerRuntimeReclaimResult(
                executed = true,
                reason = "runtime_reclaimer_dispatched"
            )
        }.getOrElse { error ->
            synchronized(this) {
                inFlightRuntimeIds.remove(runtimeId)
            }
            RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = error.message ?: "runtime_reclaimer_failed"
            )
        }
    }

    fun reclaimUnattributedProcess(
        context: Context,
        pid: Int,
        processGroupId: Int? = null,
        title: String,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): RuntimeReclaimerRuntimeReclaimResult {
        if (pid <= 0) {
            return RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = "pid_missing"
            )
        }
        synchronized(this) {
            pruneInFlight(now)
            if (inFlightPids.containsKey(pid)) {
                skippedInFlightCount += 1
                return RuntimeReclaimerRuntimeReclaimResult(
                    executed = false,
                    skippedInFlight = true,
                    reason = "unattributed_process_reclaim_already_in_flight"
                )
            }
            inFlightPids[pid] = now
            lastReclaimAtMs = now
        }
        val decision = UnattributedTerminateDecision(
            pid = pid,
            processGroupId = processGroupId?.takeIf { it > 1 && it == pid },
            title = title,
            retentionClass = com.kftest.app.foundation.service.RuntimeRetentionClass.EPHEMERAL,
            reclaimPriority = com.kftest.app.foundation.service.RuntimeRetentionClass.EPHEMERAL.reclaimPriority,
            rssKb = 0L,
            classificationSource = "lease_strategy_activation"
        )
        Logger.i(LOG_TAG, "lease strategy reclaim unattributed pid=$pid title=$title reason=$reason")
        return runCatching {
            val termination = ContainerProcessStore.terminateForRuntimeReclaimer(
                context = context.applicationContext,
                pid = pid,
                processGroupId = decision.processGroupId,
                force = true
            )
            val signal = if (termination.sentKill) {
                "SIGKILL"
            } else if (termination.sentTerminate) {
                "SIGTERM"
            } else {
                "none"
            }
            if (!termination.exited) {
                synchronized(this) {
                    inFlightPids.remove(pid)
                }
                return RuntimeReclaimerRuntimeReclaimResult(
                    executed = false,
                    reason = "unattributed_process_terminate_failed:${termination.reason}",
                    signal = signal,
                    targetMode = termination.targetMode
                )
            }
            recordExecutionRequest(decision, reason)
            RuntimeReclaimerRuntimeReclaimResult(
                executed = true,
                reason = "runtime_reclaimer_terminated_unattributed_process",
                signal = signal,
                targetMode = termination.targetMode
            )
        }.getOrElse { error ->
            synchronized(this) {
                inFlightPids.remove(pid)
            }
            RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = error.message ?: "runtime_reclaimer_failed"
            )
        }
    }

    fun reclaimOwnerRuntime(
        context: Context,
        ownerId: String,
        title: String,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): RuntimeReclaimerRuntimeReclaimResult {
        val cleanOwnerId = ownerId.trim()
        if (cleanOwnerId.isBlank()) {
            return RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = "owner_id_missing"
            )
        }
        if (!cleanOwnerId.isExplicitOwnerReclaimId()) {
            return RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = "owner_id_not_reclaimable"
            )
        }
        synchronized(this) {
            pruneInFlight(now)
            if (inFlightOwnerIds.containsKey(cleanOwnerId)) {
                skippedInFlightCount += 1
                return RuntimeReclaimerRuntimeReclaimResult(
                    executed = false,
                    skippedInFlight = true,
                    reason = "owner_reclaim_already_in_flight"
                )
            }
            inFlightOwnerIds[cleanOwnerId] = now
            lastReclaimAtMs = now
        }
        Logger.i(LOG_TAG, "explicit owner reclaim owner=$cleanOwnerId title=$title reason=$reason")
        return runCatching {
            val termination = ProotOwnerProcessTerminator.terminate(
                context = context.applicationContext,
                ownerId = cleanOwnerId
            )
            val signal = when {
                termination.sentKill -> "SIGKILL"
                termination.sentTerminate -> "SIGTERM"
                else -> "none"
            }
            val hadTarget = termination.targetTraceePids.isNotEmpty() ||
                termination.targetProcessGroupIds.isNotEmpty()
            if (!hadTarget || !termination.ok) {
                synchronized(this) {
                    inFlightOwnerIds.remove(cleanOwnerId)
                }
                return RuntimeReclaimerRuntimeReclaimResult(
                    executed = false,
                    reason = "owner_process_terminate_failed:${termination.reason}",
                    signal = signal,
                    targetMode = "proot_owner_process_group_and_tracee"
                )
            }
            recordOwnerExecutionRequest(
                ownerId = cleanOwnerId,
                title = title,
                reason = reason
            )
            RuntimeReclaimerRuntimeReclaimResult(
                executed = true,
                reason = "runtime_reclaimer_terminated_owner_processes:${termination.reason}",
                signal = signal,
                targetMode = "proot_owner_process_group_and_tracee"
            )
        }.getOrElse { error ->
            synchronized(this) {
                inFlightOwnerIds.remove(cleanOwnerId)
            }
            RuntimeReclaimerRuntimeReclaimResult(
                executed = false,
                reason = error.message ?: "owner_reclaim_failed"
            )
        }
    }

    @Synchronized
    private fun recordExecutionRequest(decision: RuntimeReclaimDecision, reason: String) {
        lastExecutionKind = when (decision) {
            is RuntimeStopDecision -> {
                runtimeStopRequestCount += 1
                "registered_runtime_stop"
            }
            is UnattributedTerminateDecision -> {
                unattributedTerminateRequestCount += 1
                "policy_classified_unattributed_terminate"
            }
        }
        lastTargetId = when (decision) {
            is RuntimeStopDecision -> decision.runtimeId
            is UnattributedTerminateDecision -> decision.pid.toString()
        }
        lastTargetTitle = decision.title
        lastReason = reason
    }

    @Synchronized
    private fun recordOwnerExecutionRequest(ownerId: String, title: String, reason: String) {
        ownerProcessTerminateRequestCount += 1
        lastExecutionKind = "explicit_owner_process_terminate"
        lastTargetId = ownerId
        lastTargetTitle = title
        lastReason = reason
    }

    private fun chooseCandidate(
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeReclaimerPolicy,
        now: Long
    ): RuntimeReclaimDecision? {
        return snapshot.roots.asSequence()
            .filter { root ->
                root.isRunning &&
                    root.rssKb > 0L &&
                    !root.isProotCapacityRuntime() &&
                    root.isReclaimableUnder(policy.activeProfile, snapshot.pressure.level, now)
            }
            .sortedWith(
                compareByDescending<RuntimeRootSnapshot> { it.reclaimPriority }
                    .thenByDescending { it.rssKb }
                    .thenBy { it.title.lowercase() }
            )
            .firstOrNull()
            ?.let { root ->
                when (root.ownerKind) {
                    RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> RuntimeStopDecision(
                        runtimeId = root.ownerId.orEmpty(),
                        title = root.title,
                        retentionClass = root.retentionClass,
                        reclaimPriority = root.reclaimPriority,
                        rssKb = root.rssKb
                    )
                    RuntimeRootOwnerKind.UNATTRIBUTED -> UnattributedTerminateDecision(
                        pid = root.observedPid ?: return@let null,
                        title = root.title,
                        retentionClass = root.retentionClass,
                        reclaimPriority = root.reclaimPriority,
                        rssKb = root.rssKb,
                        classificationSource = root.classificationSource
                    )
                    RuntimeRootOwnerKind.CARD,
                    RuntimeRootOwnerKind.RESOURCE,
                    RuntimeRootOwnerKind.TERMINAL -> null
                }
            }
    }

    private fun chooseLifecyclePlanCandidate(
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeReclaimerPolicy,
        now: Long
    ): RuntimeReclaimDecision? {
        val rootsByWorkloadId = snapshot.roots.associateBy { it.ownershipKey }
        return snapshot.lifecycleReclaimPlan.items.asSequence()
            .filter { item -> item.disposition.canBeExecutedByReclaimer() }
            .sortedWith(
                compareBy<RuntimeLifecycleReclaimItem> {
                    if (it.reclaimRank == 0) Int.MAX_VALUE else it.reclaimRank
                }.thenBy { it.leaseRemainingMs }
                    .thenByDescending { it.rssKb }
                    .thenBy { it.workloadId }
            )
            .mapNotNull { item ->
                val root = rootsByWorkloadId[item.workloadId] ?: return@mapNotNull null
                if (root.isProotCapacityRuntime()) {
                    return@mapNotNull null
                }
                if (!root.isReclaimableByLifecyclePlan(item, policy.activeProfile, now)) {
                    return@mapNotNull null
                }
                root.toReclaimDecision(item.reason)
            }
            .firstOrNull()
    }

    private fun RuntimeRootSnapshot.isReclaimableUnder(
        profile: RuntimeReclaimerProfile,
        level: RuntimePressureLevel,
        now: Long
    ): Boolean {
        if (!profile.allowsReclaim(level)) return false
        if (resident || !autoReclaimAllowed) return false
        return when (ownerKind) {
            RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> when (retentionClass) {
                com.kftest.app.foundation.service.RuntimeRetentionClass.EPHEMERAL -> !ownerId.isNullOrBlank()
                com.kftest.app.foundation.service.RuntimeRetentionClass.BATCH ->
                    !ownerId.isNullOrBlank() &&
                        profile.allowsBatch(level) &&
                        isOldEnoughBatch(now, profile.batchMinAgeMs)
                else -> false
            }
            RuntimeRootOwnerKind.UNATTRIBUTED ->
                observedPid != null && profile.allowsUnknown(level)
            RuntimeRootOwnerKind.CARD,
            RuntimeRootOwnerKind.RESOURCE,
            RuntimeRootOwnerKind.TERMINAL -> false
        }
    }

    private fun RuntimeRootSnapshot.isReclaimableByLifecyclePlan(
        item: RuntimeLifecycleReclaimItem,
        profile: RuntimeReclaimerProfile,
        now: Long
    ): Boolean {
        if (profile == RuntimeReclaimerProfile.OBSERVE_ONLY) return false
        if (resident || !autoReclaimAllowed) return false
        return when (ownerKind) {
            RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> when (retentionClass) {
                com.kftest.app.foundation.service.RuntimeRetentionClass.EPHEMERAL ->
                    !ownerId.isNullOrBlank()
                com.kftest.app.foundation.service.RuntimeRetentionClass.BATCH ->
                    !ownerId.isNullOrBlank() &&
                        (item.reclaimRank > 0 || isOldEnoughBatch(now, profile.batchMinAgeMs))
                else -> false
            }
            RuntimeRootOwnerKind.UNATTRIBUTED ->
                observedPid != null &&
                    item.reclaimRank > 0 &&
                    profile != RuntimeReclaimerProfile.CONSERVATIVE
            RuntimeRootOwnerKind.CARD,
            RuntimeRootOwnerKind.RESOURCE,
            RuntimeRootOwnerKind.TERMINAL -> false
        }
    }

    private fun RuntimeRootSnapshot.isOldEnoughBatch(now: Long, minAgeMs: Long): Boolean {
        val startedAt = lastStartedAt ?: return false
        return now - startedAt >= minAgeMs
    }

    private fun pruneInFlight(now: Long) {
        val iterator = inFlightRuntimeIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= IN_FLIGHT_TTL_MS) {
                iterator.remove()
            }
        }
        val pidIterator = inFlightPids.entries.iterator()
        while (pidIterator.hasNext()) {
            val entry = pidIterator.next()
            if (now - entry.value >= IN_FLIGHT_TTL_MS) {
                pidIterator.remove()
            }
        }
        val ownerIterator = inFlightOwnerIds.entries.iterator()
        while (ownerIterator.hasNext()) {
            val entry = ownerIterator.next()
            if (now - entry.value >= IN_FLIGHT_TTL_MS) {
                ownerIterator.remove()
            }
        }
    }

    private fun buildReason(
        snapshot: RuntimeHealthSnapshot,
        policy: RuntimeReclaimerPolicy,
        decision: RuntimeReclaimDecision
    ): String {
        return "pressure=${snapshot.pressure.level.name} " +
            "profile=${policy.activeProfile.name} " +
            "totalRssKb=${snapshot.pressure.totalRssKb} " +
            "targetRssKb=${decision.rssKb} " +
            "retention=${decision.retentionClass.name} " +
            "priority=${decision.reclaimPriority} " +
            "policy=${decision.policyHint()}"
    }

    private fun RuntimeRootSnapshot.toReclaimDecision(policyHint: String): RuntimeReclaimDecision? {
        return when (ownerKind) {
            RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> RuntimeStopDecision(
                runtimeId = ownerId.orEmpty(),
                title = title,
                retentionClass = retentionClass,
                reclaimPriority = reclaimPriority,
                rssKb = rssKb,
                policyHint = policyHint
            )
            RuntimeRootOwnerKind.UNATTRIBUTED -> UnattributedTerminateDecision(
                pid = observedPid ?: return null,
                processGroupId = rootProcessGroupId
                    ?.takeIf { it > 1 && (it == observedPid || rootSessionId == observedPid) },
                title = title,
                retentionClass = retentionClass,
                reclaimPriority = reclaimPriority,
                rssKb = rssKb,
                classificationSource = policyHint
            )
            RuntimeRootOwnerKind.CARD,
            RuntimeRootOwnerKind.RESOURCE,
            RuntimeRootOwnerKind.TERMINAL -> null
        }
    }

    private fun RuntimeLifecycleReclaimDisposition.canBeExecutedByReclaimer(): Boolean {
        return this == RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE ||
            this == RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP ||
            this == RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD
    }

    private fun RuntimeRootSnapshot.isProotCapacityRuntime(): Boolean {
        return runtimeKind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
            runtimeKind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER
    }

    private fun String.isExplicitOwnerReclaimId(): Boolean {
        return startsWith("card:") ||
            startsWith("resource:") ||
            startsWith("terminal:")
    }

    private sealed interface RuntimeReclaimDecision {
        val title: String
        val retentionClass: com.kftest.app.foundation.service.RuntimeRetentionClass
        val reclaimPriority: Int
        val rssKb: Long

        fun policyHint(): String
    }

    private data class RuntimeStopDecision(
        val runtimeId: String,
        override val title: String,
        override val retentionClass: com.kftest.app.foundation.service.RuntimeRetentionClass,
        override val reclaimPriority: Int,
        override val rssKb: Long,
        val policyHint: String = "registered_runtime"
    ) : RuntimeReclaimDecision {
        override fun policyHint(): String = policyHint
    }

    private data class UnattributedTerminateDecision(
        val pid: Int,
        val processGroupId: Int? = null,
        override val title: String,
        override val retentionClass: com.kftest.app.foundation.service.RuntimeRetentionClass,
        override val reclaimPriority: Int,
        override val rssKb: Long,
        val classificationSource: String
    ) : RuntimeReclaimDecision {
        override fun policyHint(): String = classificationSource
    }
}
