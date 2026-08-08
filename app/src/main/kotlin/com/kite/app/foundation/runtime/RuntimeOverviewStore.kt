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
import kotlinx.coroutines.Job
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

    private val _snapshot = MutableStateFlow(RuntimeOverviewSnapshot())
    val snapshot: StateFlow<RuntimeOverviewSnapshot> = _snapshot

    @Volatile
    private var lifecycleOwner: Context? = null
    @Volatile
    private var lifecycleJob: Job? = null

    fun start(context: Context, parentScope: CoroutineScope): Job {
        val appContext = context.applicationContext
        lifecycleJob
            ?.takeIf { lifecycleOwner === appContext && it.isActive }
            ?.let { return it }

        val rootJob: Job
        val scope: CoroutineScope
        synchronized(this) {
            lifecycleJob
                ?.takeIf { lifecycleOwner === appContext && it.isActive }
                ?.let { return it }

            lifecycleOwner = null
            lifecycleJob?.cancel()
            lifecycleJob = null

            rootJob = SupervisorJob(parentScope.coroutineContext[Job])
            scope = CoroutineScope(rootJob + Dispatchers.Default)
            lifecycleOwner = appContext
            lifecycleJob = rootJob
        }

        scope.launch {
            combine(
                KFWorkspaceManager.currentSpaceState,
                TerminalRuntimeRegistry.entries,
                BackgroundRuntimeRegistry.entries
            ) { space, terminals, runtimes ->
                buildSnapshot(space, terminals, runtimes)
            }.collect { latest ->
                if (isCurrent(appContext, rootJob)) {
                    _snapshot.value = latest
                }
            }
        }
        return rootJob
    }

    fun release(context: Context): Job? {
        val appContext = context.applicationContext
        return synchronized(this) {
            if (lifecycleOwner !== appContext) return@synchronized null
            lifecycleOwner = null
            lifecycleJob.also { job ->
                lifecycleJob = null
                job?.cancel()
            }
        }
    }

    private fun isCurrent(owner: Context, job: Job): Boolean {
        return lifecycleOwner === owner && lifecycleJob === job && job.isActive
    }

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val rootJob = currentJob(appContext) ?: return
        if (isCurrent(appContext, rootJob)) {
            RuntimeFrameCoordinator.refreshRuntimeOverview(appContext)
        }
    }

    internal fun publishCurrentSnapshot(
        context: Context,
        space: SpaceRecord? = null
    ) {
        val appContext = context.applicationContext
        val rootJob = currentJob(appContext) ?: return
        val currentSpace = space
            ?: KFWorkspaceManager.getCurrentSpace(appContext)
            ?: KFWorkspaceManager.listSpaces(appContext).firstOrNull()
        if (!isCurrent(appContext, rootJob)) return
        currentSpace?.id?.let { spaceId ->
            BackgroundRuntimeRegistry.ensureProotCapacityWorkerHeadroom(appContext, spaceId)
        }
        if (!isCurrent(appContext, rootJob)) return
        val latest = buildSnapshot(
            currentSpace,
            TerminalRuntimeRegistry.snapshot(),
            BackgroundRuntimeRegistry.snapshot(currentSpace?.id)
        )
        if (isCurrent(appContext, rootJob)) {
            _snapshot.value = latest
        }
    }

    private fun currentJob(owner: Context): Job? {
        return synchronized(this) {
            lifecycleJob?.takeIf { job -> isCurrent(owner, job) }
        }
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
