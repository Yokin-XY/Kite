package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.service.WorkstationActionGateway
import com.kftest.app.foundation.terminal.TerminalRuntimeEntry
import com.kftest.app.foundation.terminal.TerminalRuntimeHost
import com.kftest.app.foundation.workspace.KFWorkspaceManager
import com.kftest.app.foundation.workspace.ManagedTerminalStatus
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.workspace.isArchivedStatus
import com.kftest.app.foundation.workspace.isLiveProcessStatus
import com.kftest.app.foundation.workspace.isOpenableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class TerminalSessionAction(val label: String) {
    NEW("新建"),
    OPEN("进入"),
    END("结束会话"),
    DELETE("删除记录"),
    REFRESH("刷新")
}

enum class TerminalSessionLifecycleState(
    val label: String,
    val allowsInput: Boolean,
    val allowsQueuedInput: Boolean,
    val recoverable: Boolean
) {
    REGISTERED("registered", allowsInput = false, allowsQueuedInput = true, recoverable = true),
    PREPARING("preparing", allowsInput = false, allowsQueuedInput = true, recoverable = true),
    ATTACHED_PROBING("attached/probing", allowsInput = false, allowsQueuedInput = true, recoverable = true),
    RUNNING("running", allowsInput = true, allowsQueuedInput = false, recoverable = true),
    FROZEN("frozen", allowsInput = false, allowsQueuedInput = true, recoverable = true),
    STOPPED("stopped", allowsInput = false, allowsQueuedInput = false, recoverable = false),
    EXITED("exited", allowsInput = false, allowsQueuedInput = false, recoverable = false),
    FAILED("failed", allowsInput = false, allowsQueuedInput = false, recoverable = false),
    STALE("stale", allowsInput = false, allowsQueuedInput = true, recoverable = true),
    UNKNOWN("unknown", allowsInput = false, allowsQueuedInput = true, recoverable = true)
}

data class TerminalSessionItem(
    val id: String,
    val title: String,
    val kindLabel: String,
    val status: ManagedTerminalStatus,
    val statusLabel: String,
    val createdAt: Long,
    val lastAttachedAt: Long? = null,
    val lastStartedAt: Long? = null,
    val lastExitedAt: Long? = null,
    val rootPid: Int? = null,
    val observedPid: Int? = null,
    val runtimeRealityLabel: String? = null,
    val observedStatusLabel: String? = null,
    val processCount: Int = 0,
    val lastSeenAt: Long? = null,
    val staleReason: String? = null,
    val lifecycleState: TerminalSessionLifecycleState = TerminalSessionLifecycleState.UNKNOWN,
    val lifecycleLabel: String = lifecycleState.label,
    val lifecycleDetail: String? = null,
    val hasAttachedSession: Boolean = false,
    val pendingInputCount: Int = 0,
    val isInputReady: Boolean = false,
    val allowsQueuedInput: Boolean = false,
    val isRecoverable: Boolean = false,
    val isObservedRunning: Boolean = false,
    val isCurrentViewed: Boolean,
    val startupCommand: String? = null,
    val sourceAgentRuntimeId: String? = null,
    val availableActions: List<TerminalSessionAction> = emptyList()
)

data class TerminalSessionsSnapshot(
    val spaceId: String? = null,
    val currentViewedSessionId: String? = null,
    val primaryEntry: TerminalSessionItem? = null,
    val liveSessions: List<TerminalSessionItem> = emptyList(),
    val sessions: List<TerminalSessionItem> = emptyList(),
    val refreshedAt: Long = 0L
)

/**
 * 任务入口层适配器，给终端页提供稳定的快照与动作入口。
 *
 * 页面层不直接拼控制器状态；需要触发创建/结束等动作时，优先走入口网关。
 */
object TerminalSessionStore {

    private const val TERMINAL_SNAPSHOT_LOG_MIN_INTERVAL_MS = 15_000L
    private const val UI_REFRESH_MIN_INTERVAL_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(TerminalSessionsSnapshot())
    val snapshot: StateFlow<TerminalSessionsSnapshot> = _snapshot

    @Volatile
    private var lastSnapshotLogSignature: String = ""
    @Volatile
    private var lastSnapshotLogAtMs: Long = 0L
    @Volatile
    private var lastEmittedSnapshotSignature: String = ""
    @Volatile
    private var refreshJob: Job? = null
    @Volatile
    private var pendingRefresh = false
    @Volatile
    private var lastRefreshAtMs = 0L

    init {
        scope.launch {
            combine(
                RuntimeOverviewStore.snapshot,
                RuntimeHealthStore.snapshot
            ) { overview, health ->
                val primarySessionId = overview.spaceId?.let { KFWorkspaceManager.primaryShellSessionId(it) }
                val allSessions = overview.terminalSessions
                    .sortedWith(
                        compareByDescending<TerminalRuntimeEntry> { it.isActive }
                            .thenBy { it.createdAt }
                    )
                    .map {
                        it.toTerminalSessionItem(
                            currentViewedSessionId = overview.currentViewedSessionId,
                            root = health.terminalRoot(it.sessionId)
                        )
                    }
                    .withStableDisplayTitles(primarySessionId)
                val primaryEntry = allSessions.firstOrNull { it.id == primarySessionId }
                val liveSessions = allSessions.filter { item ->
                    item.id != primarySessionId && item.isVisibleInMainList
                }
                TerminalSessionsSnapshot(
                    spaceId = overview.spaceId,
                    currentViewedSessionId = overview.currentViewedSessionId,
                    primaryEntry = primaryEntry,
                    liveSessions = liveSessions,
                    sessions = allSessions,
                    refreshedAt = maxOf(overview.refreshedAt, health.reconciledAt)
                )
            }.collect { latest ->
                val signature = buildSnapshotSignature(latest)
                if (signature == lastEmittedSnapshotSignature) {
                    return@collect
                }
                logSnapshotIfNeeded(latest, signature)
                _snapshot.value = latest
                lastEmittedSnapshotSignature = signature
            }
        }
    }

    fun refresh(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        synchronized(this) {
            val running = refreshJob
            if (running != null && running.isActive) {
                pendingRefresh = true
                return
            }
            val now = System.currentTimeMillis()
            val delayMs = if (force) {
                0L
            } else {
                (lastRefreshAtMs + UI_REFRESH_MIN_INTERVAL_MS - now).coerceAtLeast(0L)
            }
            refreshJob = scope.launch {
                try {
                    if (delayMs > 0L) {
                        delay(delayMs)
                    }
                    do {
                        clearPendingRefresh()
                        lastRefreshAtMs = System.currentTimeMillis()
                        RuntimeOverviewStore.refresh(appContext)
                        RuntimeHealthStore.publishCurrentSnapshot(
                            context = appContext,
                            reason = "terminal-session-store-refresh"
                        )
                        if (consumePendingRefresh()) {
                            delay(UI_REFRESH_MIN_INTERVAL_MS)
                        } else {
                            break
                        }
                    } while (true)
                } finally {
                    synchronized(this@TerminalSessionStore) {
                        refreshJob = null
                    }
                }
            }
        }
    }

    fun create(context: Context) {
        WorkstationActionGateway.createShellSession(context.applicationContext)
    }

    fun open(context: Context, sessionId: String) {
        TerminalRuntimeHost.switchToSession(context.applicationContext, sessionId)
    }

    fun end(context: Context, sessionId: String) {
        WorkstationActionGateway.endTerminalSession(context.applicationContext, sessionId)
    }

    fun delete(context: Context, sessionId: String) {
        TerminalRuntimeHost.deleteSession(context.applicationContext, sessionId)
    }

    fun getSession(sessionId: String): TerminalSessionItem? {
        return _snapshot.value.sessions.firstOrNull { it.id == sessionId }
    }

    @Synchronized
    private fun clearPendingRefresh() {
        pendingRefresh = false
    }

    @Synchronized
    private fun consumePendingRefresh(): Boolean {
        val shouldRun = pendingRefresh
        pendingRefresh = false
        return shouldRun
    }

    private fun logSnapshotIfNeeded(latest: TerminalSessionsSnapshot, signature: String) {
        val now = System.currentTimeMillis()
        if (
            signature == lastSnapshotLogSignature &&
            now - lastSnapshotLogAtMs < TERMINAL_SNAPSHOT_LOG_MIN_INTERVAL_MS
        ) {
            return
        }
        Logger.i(
            "TerminalSessionStore",
            "终端快照刷新: primary=${latest.primaryEntry?.id ?: "none"}, live=${latest.liveSessions.joinToString { "${it.title}:${it.status.name}" }}, all=${latest.sessions.joinToString { "${it.title}:${it.status.name}/${it.runtimeRealityLabel ?: "record"}" }}"
        )
        lastSnapshotLogSignature = signature
        lastSnapshotLogAtMs = now
    }

    private fun buildSnapshotSignature(latest: TerminalSessionsSnapshot): String {
        return buildString {
            append(latest.spaceId ?: "none")
            append('|')
            append(latest.currentViewedSessionId ?: "none")
            append('|')
            append(latest.primaryEntry?.id ?: "none")
            append('|')
            latest.sessions.forEach { session ->
                appendSessionSignature(session)
            }
        }
    }

    private fun StringBuilder.appendSessionSignature(session: TerminalSessionItem) {
        append(session.id)
        append(':')
        append(session.title)
        append(':')
        append(session.status.name)
        append(':')
        append(session.statusLabel)
        append(':')
        append(session.runtimeRealityLabel ?: "record")
        append(':')
        append(session.rootPid ?: 0)
        append(':')
        append(session.observedPid ?: 0)
        append(':')
        append(session.processCount)
        append(':')
        append(session.pendingInputCount)
        append(':')
        append(session.isInputReady)
        append(':')
        append(session.allowsQueuedInput)
        append(':')
        append(session.isCurrentViewed)
        append(':')
        append(session.lastAttachedAt ?: 0L)
        append(':')
        append(session.lastStartedAt ?: 0L)
        append(':')
        append(session.lastExitedAt ?: 0L)
        append(';')
    }

    private fun TerminalRuntimeEntry.toTerminalSessionItem(
        currentViewedSessionId: String?,
        root: RuntimeRootSnapshot?
    ): TerminalSessionItem {
        val lifecycle = resolveLifecycle(root)
        return TerminalSessionItem(
            id = sessionId,
            title = title,
            kindLabel = kind.label,
            status = status,
            statusLabel = lifecycle.statusLabel,
            createdAt = createdAt,
            lastAttachedAt = lastAttachedAt,
            lastStartedAt = lastStartedAt,
            lastExitedAt = lastExitedAt,
            rootPid = root?.observedPid ?: root?.expectedPid,
            observedPid = root?.observedPid,
            runtimeRealityLabel = root?.reality?.label,
            observedStatusLabel = root?.observedStatusLabel,
            processCount = root?.processCount ?: 0,
            lastSeenAt = root?.lastSeenAt,
            staleReason = root?.staleReason,
            lifecycleState = lifecycle.state,
            lifecycleLabel = lifecycle.state.label,
            lifecycleDetail = lifecycle.detail,
            hasAttachedSession = hasAttachedSession,
            pendingInputCount = pendingInputCount,
            isInputReady = lifecycle.state.allowsInput,
            allowsQueuedInput = lifecycle.state.allowsQueuedInput,
            isRecoverable = lifecycle.state.recoverable,
            isObservedRunning = lifecycle.state == TerminalSessionLifecycleState.RUNNING,
            isCurrentViewed = currentViewedSessionId == sessionId,
            startupCommand = startupCommand,
            sourceAgentRuntimeId = sourceAgentRuntimeId,
            availableActions = buildAvailableActions(status, root)
        )
    }

    private val TerminalSessionItem.isInteractionActive: Boolean
        get() = lifecycleState == TerminalSessionLifecycleState.RUNNING ||
            lifecycleState == TerminalSessionLifecycleState.ATTACHED_PROBING ||
            lifecycleState == TerminalSessionLifecycleState.PREPARING

    private val TerminalSessionItem.isVisibleInMainList: Boolean
        get() = isInteractionActive ||
            lifecycleState == TerminalSessionLifecycleState.FROZEN ||
            lifecycleState == TerminalSessionLifecycleState.STALE ||
            (lifecycleState == TerminalSessionLifecycleState.REGISTERED && isCurrentViewed)

    private data class TerminalLifecycleProjection(
        val state: TerminalSessionLifecycleState,
        val statusLabel: String,
        val detail: String? = null
    )

    private fun TerminalRuntimeEntry.resolveLifecycle(
        root: RuntimeRootSnapshot?
    ): TerminalLifecycleProjection {
        if (root?.reality == RuntimeRootReality.OBSERVED) {
            val observed = root.observedStatusLabel
                ?.takeIf { it.isNotBlank() }
                ?.let { "running / observed $it" }
                ?: "running / observed"
            return TerminalLifecycleProjection(
                state = TerminalSessionLifecycleState.RUNNING,
                statusLabel = withPendingSuffix(observed),
                detail = "PTY/proot observed at pid=${root.observedPid ?: 0}"
            )
        }
        if (status.isLiveProcessStatus() && root?.reality == RuntimeRootReality.STALE_RECORD) {
            return TerminalLifecycleProjection(
                state = TerminalSessionLifecycleState.STALE,
                statusLabel = withPendingSuffix("stale / reopen to restore"),
                detail = root.staleReason
            )
        }
        return when (status) {
            ManagedTerminalStatus.RUNNING,
            ManagedTerminalStatus.ATTACHED -> {
                val state = if (hasAttachedSession) {
                    TerminalSessionLifecycleState.ATTACHED_PROBING
                } else {
                    TerminalSessionLifecycleState.PREPARING
                }
                TerminalLifecycleProjection(
                    state = state,
                    statusLabel = withPendingSuffix(state.label),
                    detail = if (hasAttachedSession) {
                        "TerminalSession is attached; PTY/proot is still being probed."
                    } else {
                        "Persisted live record exists; no attached TerminalSession is present yet."
                    }
                )
            }

            ManagedTerminalStatus.REGISTERED -> TerminalLifecycleProjection(
                state = TerminalSessionLifecycleState.REGISTERED,
                statusLabel = withPendingSuffix(TerminalSessionLifecycleState.REGISTERED.label),
                detail = "Persisted terminal record exists; no PTY/proot has been confirmed."
            )

            ManagedTerminalStatus.FROZEN -> TerminalLifecycleProjection(
                state = TerminalSessionLifecycleState.FROZEN,
                statusLabel = withPendingSuffix("frozen / reopen to restore"),
                detail = "Recovered record from a previous app process; reopen reuses the session id and creates a new PTY."
            )

            ManagedTerminalStatus.STOPPED -> TerminalLifecycleProjection(
                state = TerminalSessionLifecycleState.STOPPED,
                statusLabel = TerminalSessionLifecycleState.STOPPED.label,
                detail = "Terminal was stopped intentionally."
            )

            ManagedTerminalStatus.EXITED -> TerminalLifecycleProjection(
                state = TerminalSessionLifecycleState.EXITED,
                statusLabel = TerminalSessionLifecycleState.EXITED.label,
                detail = "Terminal process exited."
            )

            ManagedTerminalStatus.FAILED -> TerminalLifecycleProjection(
                state = TerminalSessionLifecycleState.FAILED,
                statusLabel = TerminalSessionLifecycleState.FAILED.label,
                detail = "Terminal process failed."
            )
        }
    }

    private fun TerminalRuntimeEntry.withPendingSuffix(label: String): String {
        return if (pendingInputCount > 0) {
            "$label / queued input=$pendingInputCount"
        } else {
            label
        }
    }

    private fun buildAvailableActions(
        status: ManagedTerminalStatus,
        root: RuntimeRootSnapshot?
    ): List<TerminalSessionAction> {
        if (status.isLiveProcessStatus() && root?.reality == RuntimeRootReality.STALE_RECORD) {
            return listOf(
                TerminalSessionAction.OPEN,
                TerminalSessionAction.REFRESH
            )
        }
        return when {
            status.isArchivedStatus() -> listOf(
                TerminalSessionAction.DELETE,
                TerminalSessionAction.REFRESH
            )

            status == ManagedTerminalStatus.FROZEN -> listOf(
                TerminalSessionAction.OPEN,
                TerminalSessionAction.REFRESH
            )

            status == ManagedTerminalStatus.REGISTERED -> listOf(
                TerminalSessionAction.OPEN,
                TerminalSessionAction.REFRESH
            )

            status.isOpenableStatus() -> listOf(
                TerminalSessionAction.OPEN,
                TerminalSessionAction.END,
                TerminalSessionAction.REFRESH
            )

            else -> listOf(
                TerminalSessionAction.OPEN,
                TerminalSessionAction.REFRESH
            )
        }
    }

    private fun List<TerminalSessionItem>.withStableDisplayTitles(
        primarySessionId: String?
    ): List<TerminalSessionItem> {
        val normalized = map { item ->
            item.copy(title = item.resolveDisplayTitle(primarySessionId))
        }
        val titleCounts = normalized.groupingBy { it.title }.eachCount()
        return normalized.map { item ->
            if ((titleCounts[item.title] ?: 0) <= 1) {
                item
            } else {
                item.copy(title = "${item.title} #${item.shortSessionTag()}")
            }
        }
    }

    private fun TerminalSessionItem.resolveDisplayTitle(primarySessionId: String?): String {
        val cleaned = title.trim()
        if (id == primarySessionId) {
            return cleaned.ifBlank { "主终端" }
        }
        if (
            cleaned.isBlank() ||
            cleaned == "主终端" ||
            Regex("^终端\\s+\\d+$").matches(cleaned)
        ) {
            return "Shell ${shortSessionTag()}"
        }
        return cleaned
    }

    private fun TerminalSessionItem.shortSessionTag(): String {
        val timestampTail = id.substringAfterLast('-').takeLast(4)
        return timestampTail.ifBlank { id.takeLast(4) }.uppercase()
    }
}
