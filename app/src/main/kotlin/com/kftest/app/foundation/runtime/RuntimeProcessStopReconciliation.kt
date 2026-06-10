package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.service.BackgroundRuntimeKind
import com.kftest.app.foundation.service.BackgroundRuntimeRecord
import com.kftest.app.foundation.service.BackgroundRuntimeRegistry
import com.kftest.app.foundation.service.BackgroundRuntimeStatus
import com.kftest.app.foundation.service.RuntimeRetentionClass

data class RuntimeProcessStopReconciliationDecision(
    val observedState: RuntimeProcessUnitObservationState,
    val reason: String,
    val suppressAutoRecovery: Boolean,
    val autoRecoveryAllowed: Boolean,
    val expectedStop: Boolean = false,
    val coreRecoveryRequired: Boolean = false
)

object RuntimeProcessStopReconciliation {
    const val MANUAL_STOP_REASON = "manual-stop"
    const val EXPECTED_STOP_PREFIX = "expected_stop:"

    fun expectedStopReason(source: String, reason: String? = null): String {
        val normalizedSource = source.trim().ifBlank { MANUAL_STOP_REASON }
        val normalizedReason = reason?.trim()?.takeIf { it.isNotBlank() }
        return if (normalizedReason == null) {
            "$EXPECTED_STOP_PREFIX$normalizedSource"
        } else {
            "$EXPECTED_STOP_PREFIX$normalizedSource:$normalizedReason"
        }
    }

    fun markExpectedStop(
        context: Context,
        runtimeId: String,
        source: String,
        reason: String? = null
    ) {
        BackgroundRuntimeRegistry.updateStopReconciliationState(
            context = context,
            runtimeId = runtimeId,
            state = RuntimeProcessUnitObservationState.STOPPED_EXPECTED,
            reason = expectedStopReason(source, reason),
            autoRecoverySuppressed = true
        )
    }

    fun evaluate(
        context: Context,
        record: BackgroundRuntimeRecord,
        reason: String,
        manifest: RuntimeProcessUnitManifest = RuntimeProcessUnitManifestStore.load(context)
    ): RuntimeProcessStopReconciliationDecision {
        return evaluate(
            root = manifest.applyToRoot(record.toStopReconciliationRoot()),
            triggerReason = reason,
            recordRestartPolicy = record.restartPolicy.name
        )
    }

    fun evaluate(
        record: BackgroundRuntimeRecord,
        manifest: RuntimeProcessUnitManifest = RuntimeProcessUnitManifest.default(),
        reason: String = "runtime-stop-reconciliation"
    ): RuntimeProcessStopReconciliationDecision {
        return evaluate(
            root = manifest.applyToRoot(record.toStopReconciliationRoot()),
            triggerReason = reason,
            recordRestartPolicy = record.restartPolicy.name
        )
    }

    fun evaluate(
        root: RuntimeRootSnapshot,
        triggerReason: String = "runtime-stop-reconciliation",
        recordRestartPolicy: String? = null
    ): RuntimeProcessStopReconciliationDecision {
        if (root.isRunning) {
            return decision(
                state = RuntimeProcessUnitObservationState.RUNNING,
                reason = "process_unit_root_observed_running",
                suppress = false,
                auto = false
            )
        }
        if (root.reality != RuntimeRootReality.STALE_RECORD) {
            return decision(
                state = RuntimeProcessUnitObservationState.STOPPED_EXPECTED,
                reason = "process_unit_root_not_running_without_stale_record",
                suppress = true,
                auto = false,
                expected = true
            )
        }
        if (requiresCoreRecovery(root)) {
            return decision(
                state = RuntimeProcessUnitObservationState.CORE_RECOVERY_REQUIRED,
                reason = "core_process_missing_requires_existing_runtime_host_recovery_path",
                suppress = false,
                auto = true,
                core = true
            )
        }
        expectedStopReason(root)?.let { expectedReason ->
            return decision(
                state = RuntimeProcessUnitObservationState.STOPPED_EXPECTED,
                reason = expectedReason,
                suppress = true,
                auto = false,
                expected = true
            )
        }

        val tier = root.processUnitTier
        val policy = root.processUnitManualKillPolicy
        return when {
            tier == RuntimeProcessUnitTier.LEASE -> decision(
                state = RuntimeProcessUnitObservationState.STOPPED_EXPECTED,
                reason = "lease_process_missing_released_no_auto_restart",
                suppress = true,
                auto = false
            )

            tier == RuntimeProcessUnitTier.UNMANAGED ||
                policy == RuntimeProcessUnitManualKillPolicy.RESPECT_USER_KILL -> decision(
                state = RuntimeProcessUnitObservationState.STOPPED_EXPECTED,
                reason = "ordinary_process_missing_respects_ubuntu_user_kill",
                suppress = true,
                auto = false
            )

            tier == RuntimeProcessUnitTier.USER_LOCKED &&
                policy == RuntimeProcessUnitManualKillPolicy.WAIT_CONFIRM -> decision(
                state = RuntimeProcessUnitObservationState.WAIT_CONFIRM_RESTART,
                reason = "user_locked_process_missing_wait_for_confirmation",
                suppress = true,
                auto = false
            )

            tier == RuntimeProcessUnitTier.USER_LOCKED &&
                policy == RuntimeProcessUnitManualKillPolicy.AUTO_RESTART -> decision(
                state = RuntimeProcessUnitObservationState.AUTO_RESTART_ALLOWED,
                reason = "user_locked_process_missing_auto_restart_policy_allows_candidate",
                suppress = false,
                auto = true
            )

            policy == RuntimeProcessUnitManualKillPolicy.AUTO_RESTART -> decision(
                state = RuntimeProcessUnitObservationState.AUTO_RESTART_ALLOWED,
                reason = "manifest_explicitly_allows_auto_restart_after_missing_root",
                suppress = false,
                auto = true
            )

            tier == RuntimeProcessUnitTier.FOREGROUND -> decision(
                state = RuntimeProcessUnitObservationState.STOPPED_MANUAL_KILL_UNKNOWN,
                reason = "foreground_process_missing_manual_kill_or_crash_unknown",
                suppress = true,
                auto = false
            )

            root.processUnitId == null &&
                root.ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> decision(
                state = RuntimeProcessUnitObservationState.STOPPED_CRASH_SUSPECTED,
                reason = "registered_runtime_missing_without_manifest_policy:$triggerReason" +
                    restartPolicySuffix(recordRestartPolicy),
                suppress = false,
                auto = true
            )

            root.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED -> decision(
                state = RuntimeProcessUnitObservationState.STOPPED_EXPECTED,
                reason = "unmanaged_unregistered_process_disappeared_from_snapshot_only",
                suppress = true,
                auto = false
            )

            else -> decision(
                state = RuntimeProcessUnitObservationState.STOPPED_CRASH_SUSPECTED,
                reason = "registered_process_missing_crash_suspected:$triggerReason" +
                    restartPolicySuffix(recordRestartPolicy),
                suppress = false,
                auto = true
            )
        }
    }

    private fun decision(
        state: RuntimeProcessUnitObservationState,
        reason: String,
        suppress: Boolean,
        auto: Boolean,
        expected: Boolean = false,
        core: Boolean = false
    ): RuntimeProcessStopReconciliationDecision {
        return RuntimeProcessStopReconciliationDecision(
            observedState = state,
            reason = reason,
            suppressAutoRecovery = suppress,
            autoRecoveryAllowed = auto,
            expectedStop = expected,
            coreRecoveryRequired = core
        )
    }

    private fun requiresCoreRecovery(root: RuntimeRootSnapshot): Boolean {
        return root.processUnitTier == RuntimeProcessUnitTier.SYSTEM_CORE ||
            root.processUnitTier == RuntimeProcessUnitTier.PROOT_CORE ||
            root.processUnitManualKillPolicy == RuntimeProcessUnitManualKillPolicy.CORE_RECOVER ||
            root.runtimeKind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
            (
                root.runtimeKind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER &&
                    root.ownerId?.substringAfterLast("-proot-capacity-worker-", "")
                        ?.toIntOrNull() == 1
                ) ||
            root.retentionClass == RuntimeRetentionClass.CRITICAL_CORE
    }

    private fun expectedStopReason(root: RuntimeRootSnapshot): String? {
        val recordedReason = root.stopReconciliationReason
            ?.takeIf { it.startsWith(EXPECTED_STOP_PREFIX) }
        if (recordedReason != null) {
            return recordedReason
        }
        if (root.stopReconciliationState == RuntimeProcessUnitObservationState.STOPPED_EXPECTED &&
            root.stopReconciliationAutoRecoverySuppressed
        ) {
            return root.stopReconciliationReason ?: expectedStopReason("recorded")
        }
        if (root.lastRestartReason == MANUAL_STOP_REASON) {
            return expectedStopReason(MANUAL_STOP_REASON)
        }
        return null
    }

    private fun restartPolicySuffix(recordRestartPolicy: String?): String {
        return recordRestartPolicy
            ?.takeIf { it.isNotBlank() }
            ?.let { ":restartPolicy=$it" }
            .orEmpty()
    }

    private fun BackgroundRuntimeRecord.toStopReconciliationRoot(): RuntimeRootSnapshot {
        return RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.BACKGROUND_RUNTIME,
            ownerId = id,
            title = title,
            statusLabel = status.label,
            expectedPid = pid?.takeIf { it > 0 },
            observedPid = null,
            retentionClass = retentionClass,
            resident = retentionClass.resident,
            reclaimPriority = retentionClass.reclaimPriority,
            runtimeKind = kind,
            reality = when (status) {
                BackgroundRuntimeStatus.STARTING,
                BackgroundRuntimeStatus.RUNNING -> RuntimeRootReality.STALE_RECORD
                BackgroundRuntimeStatus.REGISTERED -> RuntimeRootReality.UNKNOWN
                BackgroundRuntimeStatus.STOPPED,
                BackgroundRuntimeStatus.ERROR -> RuntimeRootReality.STALE_RECORD
            },
            lastStartedAt = lastStartedAt,
            lastExitedAt = lastStoppedAt,
            lastExitCode = lastExitCode,
            lastError = lastError,
            restartPolicyLabel = restartPolicy.label,
            restartFailureCount = restartFailureCount,
            nextRestartAllowedAt = nextRestartAllowedAt,
            lastRestartReason = lastRestartReason,
            lastRecoveredAt = lastRecoveredAt,
            lastRecoverySource = lastRecoverySource,
            lastRecoveryReason = lastRecoveryReason,
            lastAdmissionDeferredAt = lastAdmissionDeferredAt,
            lastAdmissionSource = lastAdmissionSource,
            lastAdmissionReason = lastAdmissionReason,
            lastReclaimedAt = lastReclaimedAt,
            lastReclaimSource = lastReclaimSource,
            lastReclaimReason = lastReclaimReason,
            staleReason = "registered runtime missing during stop reconciliation",
            stopReconciliationState = lastStopReconciliationState,
            stopReconciliationReason = lastStopReconciliationReason,
            stopReconciliationAt = lastStopReconciliationAt,
            stopReconciliationAutoRecoverySuppressed = lastStopReconciliationAutoRecoverySuppressed
        )
    }
}
