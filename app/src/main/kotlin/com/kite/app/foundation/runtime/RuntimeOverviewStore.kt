package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.service.BackgroundRuntimeRecord
import com.kite.app.foundation.service.BackgroundRuntimeRegistry
import com.kite.app.foundation.terminal.TerminalRuntimeEntry
import com.kite.app.foundation.terminal.TerminalRuntimeRegistry
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.contracts.SpaceRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 运行时总表。
 *
 * 作用：
 * 1. 把“终端列表”和“后台列表”统一收成一份可观察快照。
 * 2. 后面的 UI 只需要订阅 / 刷新这里，不再自己拼接硬状态。
 * 3. 当前阶段先服务终端页与任务管理器页，暂不关心复杂统计。
 */
data class RuntimeOverviewSnapshot(
    val spaceId: String? = null,
    val spaceName: String? = null,
    val currentViewedSessionId: String? = null,
    val terminalSessions: List<TerminalRuntimeEntry> = emptyList(),
    val backgroundRuntimes: List<BackgroundRuntimeRecord> = emptyList(),
    val refreshedAt: Long = 0L
)

object RuntimeOverviewStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(RuntimeOverviewSnapshot())
    val snapshot: StateFlow<RuntimeOverviewSnapshot> = _snapshot

    init {
        scope.launch {
            combine(
                KFWorkspaceManager.currentSpaceState,
                TerminalRuntimeRegistry.entries,
                BackgroundRuntimeRegistry.entries
            ) { space, terminals, runtimes ->
                buildSnapshot(space, terminals, runtimes)
            }.collect { latest ->
                _snapshot.value = latest
            }
        }
    }

    fun refresh(context: Context) {
        RuntimeFrameCoordinator.refreshRuntimeOverview(context.applicationContext)
    }

    internal fun publishCurrentSnapshot(
        context: Context,
        space: SpaceRecord? = null
    ) {
        val appContext = context.applicationContext
        val currentSpace = space
            ?: KFWorkspaceManager.getCurrentSpace(appContext)
            ?: KFWorkspaceManager.listSpaces(appContext).firstOrNull()
        currentSpace?.id?.let { spaceId ->
            BackgroundRuntimeRegistry.ensureProotCapacityWorkerHeadroom(appContext, spaceId)
        }
        _snapshot.value = buildSnapshot(
            currentSpace,
            TerminalRuntimeRegistry.snapshot(),
            BackgroundRuntimeRegistry.snapshot(currentSpace?.id)
        )
    }

    private fun buildSnapshot(
        space: SpaceRecord?,
        terminals: List<TerminalRuntimeEntry>,
        runtimes: List<BackgroundRuntimeRecord>
    ): RuntimeOverviewSnapshot {
        val spaceId = space?.id
        val filteredTerminals = if (spaceId.isNullOrBlank()) {
            terminals
        } else {
            terminals.filter { it.spaceId == spaceId }
        }
        val filteredRuntimes = if (spaceId.isNullOrBlank()) {
            runtimes
        } else {
            runtimes.filter { it.spaceId == spaceId }
        }

        return RuntimeOverviewSnapshot(
            spaceId = spaceId,
            spaceName = space?.displayName,
            currentViewedSessionId = space?.currentTerminalSessionId,
            terminalSessions = filteredTerminals,
            backgroundRuntimes = filteredRuntimes,
            refreshedAt = System.currentTimeMillis()
        )
    }
}
