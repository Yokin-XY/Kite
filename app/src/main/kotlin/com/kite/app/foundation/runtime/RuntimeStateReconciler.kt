package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.service.BackgroundRuntimeHealthStatus
import com.kite.app.foundation.service.BackgroundRuntimeHealthText
import com.kite.app.foundation.service.BackgroundRuntimeHost
import com.kite.app.foundation.service.BackgroundRuntimeRegistry
import com.kite.app.foundation.service.BackgroundRuntimeStatus
import com.kite.app.foundation.service.isActiveStatus
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.ManagedTerminalRecord
import com.kite.app.foundation.workspace.ManagedTerminalStatus
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.isLiveProcessStatus

data class RuntimeReconciliationReport(
    val reason: String,
    val terminalUpdated: Int = 0,
    val backgroundUpdated: Int = 0,
    val containerLegacyCleared: Boolean = false,
    val skippedUnknown: Int = 0,
    val skippedGrace: Int = 0
) {
    val changed: Boolean
        get() = terminalUpdated > 0 || backgroundUpdated > 0 || containerLegacyCleared
}

object RuntimeStateReconciler {

    private const val LOG_TAG = "RuntimeStateReconciler"
    private const val BACKGROUND_START_GRACE_MS = 30_000L
    private const val TERMINAL_ATTACH_GRACE_MS = 30_000L

    fun reconcile(
        context: Context,
        reason: String,
        health: RuntimeHealthSnapshot = RuntimeHealthStore.snapshot.value
    ): RuntimeReconciliationReport {
        val appContext = context.applicationContext
        if (health.processSnapshotRefreshedAt <= 0L) {
            Logger.i(LOG_TAG, "skip reconciliation without process snapshot: reason=$reason")
            return RuntimeReconciliationReport(reason = reason, skippedUnknown = health.roots.size)
        }

        var terminalUpdated = 0
        var backgroundUpdated = 0
        var skippedUnknown = 0
        var skippedGrace = 0

        health.roots.forEach { root ->
            when (root.ownerKind) {
                RuntimeRootOwnerKind.TERMINAL -> {
                    val outcome = reconcileTerminal(appContext, root)
                    when (outcome) {
                        ReconcileOutcome.UPDATED -> terminalUpdated += 1
                        ReconcileOutcome.SKIPPED_UNKNOWN -> skippedUnknown += 1
                        ReconcileOutcome.SKIPPED_GRACE -> skippedGrace += 1
                        ReconcileOutcome.NOOP -> Unit
                    }
                }

                RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> {
                    val outcome = reconcileBackgroundRuntime(appContext, root)
                    when (outcome) {
                        ReconcileOutcome.UPDATED -> backgroundUpdated += 1
                        ReconcileOutcome.SKIPPED_UNKNOWN -> skippedUnknown += 1
                        ReconcileOutcome.SKIPPED_GRACE -> skippedGrace += 1
                        ReconcileOutcome.NOOP -> Unit
                    }
                }

                RuntimeRootOwnerKind.CARD,
                RuntimeRootOwnerKind.RESOURCE,
                RuntimeRootOwnerKind.UNATTRIBUTED -> Unit
            }
        }

        val containerLegacyCleared = reconcileLegacyContainerPid(appContext, health)
        val report = RuntimeReconciliationReport(
            reason = reason,
            terminalUpdated = terminalUpdated,
            backgroundUpdated = backgroundUpdated,
            containerLegacyCleared = containerLegacyCleared,
            skippedUnknown = skippedUnknown,
            skippedGrace = skippedGrace
        )

        if (report.changed) {
            TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
            RuntimeOverviewStore.publishCurrentSnapshot(appContext)
            RuntimeHealthStore.publishCurrentSnapshot(appContext, reason = "post-reconcile:$reason")
        }
        Logger.i(
            LOG_TAG,
            "reconcile done: reason=$reason terminal=$terminalUpdated background=$backgroundUpdated " +
                "containerLegacyCleared=$containerLegacyCleared skippedUnknown=$skippedUnknown skippedGrace=$skippedGrace"
        )
        return report
    }

    private fun reconcileTerminal(
        context: Context,
        root: RuntimeRootSnapshot
    ): ReconcileOutcome {
        if (root.reality == RuntimeRootReality.OBSERVED) {
            return ReconcileOutcome.NOOP
        }
        if (root.reality == RuntimeRootReality.UNKNOWN) {
            return ReconcileOutcome.SKIPPED_UNKNOWN
        }
        val sessionId = root.ownerId ?: return ReconcileOutcome.NOOP
        val current = KFWorkspaceManager.getTerminalSession(context, sessionId)
            ?: return ReconcileOutcome.NOOP
        if (!current.status.isLiveProcessStatus()) {
            return ReconcileOutcome.NOOP
        }
        if (shouldKeepTerminalAttachGrace(current)) {
            Logger.i(
                LOG_TAG,
                "terminal reconciliation skipped by attach grace: session=$sessionId status=${current.status.name} " +
                    "pid=${root.expectedPid ?: 0}"
            )
            return ReconcileOutcome.SKIPPED_GRACE
        }

        val nextStatus = when {
            root.expectedPid == null &&
                current.lastAttachedAt == null &&
                current.lastStartedAt == null -> ManagedTerminalStatus.REGISTERED

            root.expectedPid == null -> ManagedTerminalStatus.REGISTERED

            ProcessExitSemantics.isManagedStopExit(current.lastExitCode) -> ManagedTerminalStatus.STOPPED
            else -> ManagedTerminalStatus.FAILED
        }
        val now = System.currentTimeMillis()
        KFWorkspaceManager.updateTerminalSessionStatus(
            context = context,
            sessionId = sessionId,
            status = nextStatus,
            lastExitedAt = if (nextStatus == ManagedTerminalStatus.FAILED ||
                nextStatus == ManagedTerminalStatus.STOPPED
            ) {
                current.lastExitedAt ?: now
            } else {
                current.lastExitedAt
            },
            lastPid = current.lastPid,
            lastExitCode = current.lastExitCode
        )
        Logger.i(
            LOG_TAG,
            "terminal reconciled: session=$sessionId ${current.status.name}->${nextStatus.name} " +
                "pid=${root.expectedPid ?: 0} reason=${root.staleReason ?: "stale"}"
        )
        return ReconcileOutcome.UPDATED
    }

    private fun shouldKeepTerminalAttachGrace(current: ManagedTerminalRecord): Boolean {
        if (current.status != ManagedTerminalStatus.ATTACHED) {
            return false
        }
        if (current.lastPid != null && current.lastPid > 0) {
            return false
        }
        val attachedAt = current.lastAttachedAt ?: return false
        val ageMs = System.currentTimeMillis() - attachedAt
        return ageMs in 0 until TERMINAL_ATTACH_GRACE_MS
    }

    private fun reconcileBackgroundRuntime(
        context: Context,
        root: RuntimeRootSnapshot
    ): ReconcileOutcome {
        if (root.reality == RuntimeRootReality.OBSERVED) {
            return ReconcileOutcome.NOOP
        }
        if (root.reality == RuntimeRootReality.UNKNOWN) {
            return ReconcileOutcome.SKIPPED_UNKNOWN
        }
        val runtimeId = root.ownerId ?: return ReconcileOutcome.NOOP
        val current = BackgroundRuntimeRegistry.get(context, runtimeId) ?: return ReconcileOutcome.NOOP
        if (!current.status.isActiveStatus()) {
            return ReconcileOutcome.NOOP
        }
        val withinGrace = current.status == BackgroundRuntimeStatus.STARTING &&
            current.lastStartedAt != null &&
            System.currentTimeMillis() - current.lastStartedAt < BACKGROUND_START_GRACE_MS
        if (withinGrace) {
            return ReconcileOutcome.SKIPPED_GRACE
        }

        val stopReconciliation = RuntimeProcessStopReconciliation.evaluate(
            root = root,
            triggerReason = "health-reconciler-stale",
            recordRestartPolicy = current.restartPolicy.name
        )
        val nextStatus = if (!current.lastError.isNullOrBlank()) {
            BackgroundRuntimeStatus.ERROR
        } else {
            BackgroundRuntimeStatus.STOPPED
        }
        val message = stopReconciliation.reason.ifBlank {
            root.staleReason ?: "runtime root disappeared during reconciliation"
        }
        BackgroundRuntimeRegistry.updateStatus(
            context = context,
            runtimeId = runtimeId,
            status = nextStatus,
            pid = null,
            lastError = if (nextStatus == BackgroundRuntimeStatus.ERROR) {
                current.lastError ?: message
            } else {
                message
            }
        )
        BackgroundRuntimeRegistry.updateStopReconciliationState(
            context = context,
            runtimeId = runtimeId,
            state = stopReconciliation.observedState,
            reason = message,
            autoRecoverySuppressed = stopReconciliation.suppressAutoRecovery
        )
        BackgroundRuntimeRegistry.updateHealth(
            context = context,
            runtimeId = runtimeId,
            healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
            lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
            lastHealthCheckedAt = null
        )
        RuntimeProotMemoryAdmission.release(runtimeId)
        Logger.i(
            LOG_TAG,
            "background runtime reconciled: runtime=$runtimeId ${current.status.name}->${nextStatus.name} " +
                "pid=${root.expectedPid ?: 0} reason=$message"
        )
        if (stopReconciliation.suppressAutoRecovery) {
            Logger.i(
                LOG_TAG,
                "background runtime auto recovery suppressed by stop reconciliation: " +
                    "runtime=$runtimeId state=${stopReconciliation.observedState.name} reason=$message"
            )
        } else {
            BackgroundRuntimeHost.scheduleAutoRecovery(
                context = context,
                runtimeId = runtimeId,
                reason = "health-reconciler-stale"
            )
        }
        return ReconcileOutcome.UPDATED
    }

    private fun reconcileLegacyContainerPid(
        context: Context,
        health: RuntimeHealthSnapshot
    ): Boolean {
        val legacyPid = health.legacyContainerPid ?: return false
        if (health.roots.any { it.observedPid == legacyPid }) {
            return false
        }
        if (health.runningRootCount > 0) {
            return false
        }
        WorkSurfaceRuntimeBridge.markContainerStopped(context)
        Logger.i(LOG_TAG, "legacy container pid cleared: pid=$legacyPid")
        return true
    }

    private enum class ReconcileOutcome {
        NOOP,
        UPDATED,
        SKIPPED_UNKNOWN,
        SKIPPED_GRACE
    }
}
