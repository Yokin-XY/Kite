package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.RuntimeActionKind

import android.content.Context
import com.kite.app.foundation.capability.CapabilityCallerType
import com.kite.app.foundation.capability.CapabilityDomain
import com.kite.app.foundation.capability.CapabilityGate
import com.kite.app.foundation.capability.CapabilityOutputLevel
import com.kite.app.foundation.capability.CapabilityRequest
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.service.BackgroundRuntimeHost
import com.kite.app.foundation.service.SupervisordServiceHealthStore
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.contracts.SpaceRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * P0 收口阶段的统一编排入口。
 *
 * 目标不是新增一套状态，而是把现有这条主链明确收口：
 * 空间 -> 终端/后台运行时 -> 运行时总表 -> 真实进程快照。
 */
object RuntimeFrameCoordinator {

    private const val LOG_TAG = "RuntimeFrameCoordinator"
    private const val PROCESS_REFRESH_BURST_DELAY_SHORT_MS = 1800L
    private const val PROCESS_REFRESH_BURST_DELAY_LONG_MS = 5200L
    private val DEFAULT_PROCESS_REFRESH_BURST_DELAYS = listOf(
        PROCESS_REFRESH_BURST_DELAY_SHORT_MS,
        PROCESS_REFRESH_BURST_DELAY_LONG_MS
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scheduledProcessRefreshes = LinkedHashMap<Long, Long>()

    fun refreshRuntimeOverview(context: Context): SpaceRecord {
        val appContext = context.applicationContext
        auditRuntimeCapability("runtimeOverviewRefresh", "runtime-overview")
        RuntimeHealthStore.attachContext(appContext)
        val currentSpace = resolveCurrentSpace(appContext)
        refreshRuntimeOverviewInternal(appContext, currentSpace)
        RuntimeHealthStore.markReconciliation("runtime-overview-refresh")
        return currentSpace
    }

    fun refreshTaskManager(context: Context): SpaceRecord {
        val appContext = context.applicationContext
        val currentSpace = refreshRuntimeOverview(appContext)
        refreshProcessSnapshot(appContext, reason = "task-manager-refresh")
        SupervisordServiceHealthStore.refresh(appContext, reason = "task-manager-refresh")
        RuntimeHealthStore.markReconciliation("task-manager-refresh")
        scheduleRuntimeReconciliation(appContext, "task-manager-refresh")
        Logger.i(LOG_TAG, "任务视图已刷新: space=${currentSpace.id}")
        return currentSpace
    }

    fun refreshTaskManagerSurface(context: Context): SpaceRecord {
        val appContext = context.applicationContext
        auditRuntimeCapability("taskManagerSurfaceRefresh", "task-manager-surface")
        RuntimeHealthStore.attachContext(appContext)
        val currentSpace = resolveCurrentSpace(appContext)
        TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
        RuntimeOverviewStore.publishCurrentSnapshot(appContext, currentSpace)
        refreshProcessSnapshot(appContext, reason = "task-manager-surface")
        RuntimeHealthStore.markReconciliation("task-manager-surface")
        scheduleRuntimeReconciliation(appContext, "task-manager-surface", delayMs = 1800L)
        Logger.i(LOG_TAG, "任务视图首屏已刷新: space=${currentSpace.id}")
        return currentSpace
    }

    fun ensureOperationalFrame(context: Context): SpaceRecord {
        val appContext = context.applicationContext
        auditRuntimeCapability("ensureOperationalFrame", "runtime-operational-frame")
        RuntimeHealthStore.attachContext(appContext)
        val currentSpace = resolveCurrentSpace(appContext)
        val boundarySnapshot = WorkSurfaceRuntimeBridge.resolveRuntimeSnapshot(
            context = appContext,
            container = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
        )
        BackgroundRuntimeHost.ensureInitialized(appContext)
        BackgroundRuntimeHost.ensureCoreRuntimes(appContext)
        refreshRuntimeOverviewInternal(appContext, currentSpace)
        refreshProcessSnapshot(appContext, reason = "ensure-operational-frame")
        SupervisordServiceHealthStore.refresh(appContext, reason = "ensure-operational-frame")
        RuntimeHealthStore.markReconciliation("ensure-operational-frame")
        RuntimeHealthStore.logCurrentHealth("ensure-operational-frame")
        scheduleProcessRefreshBurst(appContext, reason = "ensure-operational-frame")
        Logger.i(
            LOG_TAG,
            "P0 运行时框架已就绪: space=${currentSpace.id}, build=${WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.MOBILE_BUILD)}, process=${WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.PROCESS_SAMPLING)}, logs=${boundarySnapshot.logsDir.absolutePath}"
        )
        return currentSpace
    }

    fun refreshProcessSnapshot(
        context: Context,
        reason: String? = null
    ) {
        val appContext = context.applicationContext
        auditRuntimeCapability(
            actionName = "processSnapshotRefresh",
            concurrencyKey = reason?.let { "process-snapshot:$it" } ?: "process-snapshot"
        )
        RuntimeHealthStore.attachContext(appContext)
        ProotTelemetryStore.refresh(appContext)
        RuntimeHealthStore.markReconciliation(reason ?: "process-refresh")
        if (!reason.isNullOrBlank()) {
            Logger.i(
                LOG_TAG,
                "进程快照刷新: route=${WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.PROCESS_SAMPLING)}, reason=$reason"
            )
        }
    }

    fun scheduleProcessRefreshBurst(
        context: Context,
        reason: String? = null,
        delaysMs: List<Long> = DEFAULT_PROCESS_REFRESH_BURST_DELAYS
    ) {
        scheduleProcessRefreshes(context, delaysMs, reason ?: "burst")
    }

    fun scheduleProcessRefreshes(
        context: Context,
        delaysMs: List<Long>,
        reason: String
    ) {
        val appContext = context.applicationContext
        delaysMs.forEach { delayMs ->
            if (!reserveProcessRefresh(delayMs)) {
                Logger.i(LOG_TAG, "进程快照刷新已合并: reason=$reason@$delayMs")
                return@forEach
            }
            scope.launch {
                try {
                    delay(delayMs)
                    refreshProcessSnapshot(
                        context = appContext,
                        reason = "$reason@$delayMs"
                    )
                    delay(700L)
                    RuntimeStateReconciler.reconcile(
                        context = appContext,
                        reason = "$reason@$delayMs"
                    )
                    SupervisordServiceHealthStore.refresh(
                        context = appContext,
                        reason = "$reason@$delayMs"
                    )
                    RuntimeHealthStore.logCurrentHealth("$reason@$delayMs")
                } finally {
                    releaseProcessRefresh(delayMs)
                }
            }
        }
    }

    @Synchronized
    private fun reserveProcessRefresh(delayMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val scheduledAt = scheduledProcessRefreshes[delayMs]
        if (scheduledAt != null && now - scheduledAt < delayMs + 900L) {
            return false
        }
        scheduledProcessRefreshes[delayMs] = now
        return true
    }

    @Synchronized
    private fun releaseProcessRefresh(delayMs: Long) {
        scheduledProcessRefreshes.remove(delayMs)
    }

    private fun auditRuntimeCapability(
        actionName: String,
        concurrencyKey: String
    ) {
        CapabilityGate.evaluate(
            CapabilityRequest(
                callerName = "RuntimeFrameCoordinator",
                callerType = CapabilityCallerType.LEGACY,
                actionName = actionName,
                capabilityDomains = setOf(CapabilityDomain.RUNTIME),
                requiresContainer = false,
                longRunning = false,
                expectedOutputLevel = CapabilityOutputLevel.LOW,
                concurrencyKey = concurrencyKey,
                sourcePath = "foundation/runtime/RuntimeFrameCoordinator.kt",
                sourceModule = "runtime",
                legacyDirectCall = true
            )
        )
    }

    private fun scheduleRuntimeReconciliation(
        context: Context,
        reason: String,
        delayMs: Long = 900L
    ) {
        val appContext = context.applicationContext
        scope.launch {
            delay(delayMs)
            RuntimeStateReconciler.reconcile(
                context = appContext,
                reason = reason
            )
            SupervisordServiceHealthStore.refresh(
                context = appContext,
                reason = reason
            )
        }
    }

    private fun refreshRuntimeOverviewInternal(
        appContext: Context,
        currentSpace: SpaceRecord
    ) {
        val container = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
        val workspaceLabel = WorkSurfaceRuntimeBridge.describeHostPath(
            context = appContext,
            path = container?.workspacePath,
            container = container
        )
        BackgroundRuntimeHost.ensureInitialized(appContext)
        TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
        BackgroundRuntimeHost.refreshRuntimeStatuses(appContext, currentSpace.id)
        RuntimeOverviewStore.publishCurrentSnapshot(appContext, currentSpace)
        Logger.i(
            LOG_TAG,
            "运行时总表已刷新: space=${currentSpace.id}, workspace=$workspaceLabel, terminal=${WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.TERMINAL_COMMAND)}, background=${WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.BACKGROUND_RUNTIME)}"
        )
    }

    private fun resolveCurrentSpace(appContext: Context): SpaceRecord {
        return KFWorkspaceManager.getCurrentSpace(appContext)
            ?: KFWorkspaceManager.listSpaces(appContext).firstOrNull()
            ?: KFWorkspaceManager.ensureDefaultSpace(appContext)
    }
}
