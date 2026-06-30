package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.BackgroundRuntimeHost
import com.kite.app.foundation.service.WorkstationActionGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class TaskManagerAction(val label: String) {
    OPEN_TERMINAL("\u8fdb\u5165\u7ec8\u7aef"),
    END_PROCESS("\u7ed3\u675f\u8fdb\u7a0b"),
    STOP_RUNTIME("\u505c\u6b62\u4efb\u52a1"),
    RESTART_RUNTIME("\u91cd\u542f\u4efb\u52a1"),
    VIEW_LOG("\u67e5\u770b\u65e5\u5fd7"),
    REFRESH("\u5237\u65b0")
}

data class TaskManagerProcessItem(
    val id: String,
    val pid: Int,
    val parentPid: Int,
    val title: String,
    val subtitle: String,
    val sourceLabel: String,
    val stateLabel: String,
    val rawState: String,
    val command: String,
    val commandLine: String,
    val isSynthetic: Boolean = false,
    val linkedRuntimeId: String? = null,
    val linkedRuntimeTitle: String? = null,
    val linkedTerminalSessionId: String? = null,
    val linkedTerminalTitle: String? = null,
    val runtimeOwnerId: String? = null,
    val runtimeUnitId: String? = null,
    val runtimeOwnerKindLabel: String? = null,
    val runtimeRootPid: Int? = null,
    val runtimeRealityLabel: String? = null,
    val runtimeStaleReason: String? = null,
    val runtimeLastSeenAt: Long? = null,
    val availableActions: List<TaskManagerAction> = emptyList()
)

data class TaskManagerSnapshot(
    val spaceId: String? = null,
    val processes: List<TaskManagerProcessItem> = emptyList(),
    val refreshedAt: Long = 0L
)

/**
 * 任务入口层适配器，给任务页提供进程/运行项快照与入口动作。
 *
 * 页面只跟快照和动作打交道；真正的 runtime/terminal 处理继续交给工作面宿主。
 */
object TaskManagerStore {

    private const val UI_REFRESH_MIN_INTERVAL_MS = 2_000L
    private const val MAX_LIVE_PROCESS_ROWS = 160
    private const val EMPTY_PROCESS_GRACE_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(TaskManagerSnapshot())
    val snapshot: StateFlow<TaskManagerSnapshot> = _snapshot

    @Volatile
    private var refreshJob: Job? = null

    @Volatile
    private var pendingRefresh = false

    @Volatile
    private var lastRefreshAtMs = 0L
    @Volatile
    private var lastNonEmptySnapshot: TaskManagerSnapshot? = null
    @Volatile
    private var emptySnapshotStartedAtMs = 0L

    init {
        scope.launch {
            RuntimeHealthStore.snapshot.collect { healthSnapshot ->
                val items = buildTaskItems(healthSnapshot)
                _snapshot.value = stabilizeSnapshot(TaskManagerSnapshot(
                    spaceId = healthSnapshot.spaceId,
                    processes = items,
                    refreshedAt = maxOf(
                        healthSnapshot.reconciledAt,
                        healthSnapshot.prootTelemetry.refreshedAtMs
                    )
                ))
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
            val delayMs = if (force && lastRefreshAtMs == 0L) {
                0L
            } else if (lastRefreshAtMs == 0L) {
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
                        RuntimeHealthStore.attachContext(appContext)
                        ProotTelemetryStore.refresh(appContext)
                        RuntimeHealthStore.publishCurrentSnapshot(
                            context = appContext,
                            reason = "task-manager-refresh"
                        )
                        if (consumePendingRefresh()) {
                            delay(UI_REFRESH_MIN_INTERVAL_MS)
                        } else {
                            break
                        }
                    } while (true)
                } finally {
                    synchronized(this@TaskManagerStore) {
                        refreshJob = null
                    }
                }
            }
        }
    }

    fun endProcess(context: Context, pid: Int) {
        ContainerProcessStore.terminate(context.applicationContext, pid, force = true)
    }

    fun endProcess(context: Context, item: TaskManagerProcessItem) {
        endProcess(context, item, item.pid)
    }

    fun endProcess(context: Context, item: TaskManagerProcessItem?, pid: Int) {
        val ownerId = item?.prootOwnerStopId()
        if (ownerId == null) {
            endProcess(context, pid)
            return
        }
        val appContext = context.applicationContext
        actionScope.launch {
            ProotOwnerProcessTerminator.terminate(appContext, ownerId)
            refresh(appContext, force = true)
        }
    }

    fun stopRuntime(context: Context, runtimeId: String) {
        WorkstationActionGateway.stopBackgroundRuntime(context.applicationContext, runtimeId)
    }

    fun restartRuntime(context: Context, runtimeId: String) {
        WorkstationActionGateway.restartBackgroundRuntime(context.applicationContext, runtimeId)
    }

    fun readRuntimeLog(context: Context, runtimeId: String, maxChars: Int = 6000): String {
        return BackgroundRuntimeHost.readRecentLog(context.applicationContext, runtimeId, maxChars)
    }

    fun getProcess(processId: String): TaskManagerProcessItem? {
        return _snapshot.value.processes.firstOrNull { it.id == processId }
    }

    private fun TaskManagerProcessItem.prootOwnerStopId(): String? =
        runtimeOwnerId
            ?.takeIf { id.startsWith("root-") }
            ?.takeIf { ownerId ->
                ownerId.startsWith("card:") ||
                    ownerId.startsWith("resource:") ||
                    ownerId.startsWith("terminal:")
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

    @Synchronized
    private fun stabilizeSnapshot(next: TaskManagerSnapshot): TaskManagerSnapshot {
        if (next.processes.isNotEmpty()) {
            lastNonEmptySnapshot = next
            emptySnapshotStartedAtMs = 0L
            return next
        }
        val previous = lastNonEmptySnapshot ?: return next
        val now = System.currentTimeMillis()
        if (emptySnapshotStartedAtMs == 0L) {
            emptySnapshotStartedAtMs = now
        }
        // ponytail: short empty grace hides collector gaps; replace with refresh generations if collectors expose them.
        return if (now - emptySnapshotStartedAtMs < EMPTY_PROCESS_GRACE_MS) {
            previous.copy(refreshedAt = next.refreshedAt)
        } else {
            next
        }
    }

    private fun buildTaskItems(
        healthSnapshot: RuntimeHealthSnapshot
    ): List<TaskManagerProcessItem> {
        val rootItems = healthSnapshot.roots
            .filter { root -> root.reality == RuntimeRootReality.OBSERVED && (root.observedPid ?: 0) > 0 }
            .map { it.toTaskManagerItem() }

        val rootPids = rootItems
            .mapNotNull { item -> item.pid.takeIf { it > 0 } }
            .toSet()

        val runningEntries = healthSnapshot.prootTelemetry.processLiveTable.entries
            .filter { entry -> entry.state == ProotLiveProcessState.RUNNING }
        val entriesByPid = runningEntries.associateBy { it.traceePid }
        val processItems = runningEntries
            .filterNot { entry -> entry.traceePid in rootPids }
            .sortedWith(
                compareBy<ProotLiveProcessEntry> { it.commandTitle().lowercase() }
                    .thenBy { it.traceePid }
            )
            .take(MAX_LIVE_PROCESS_ROWS)
            .map { entry -> entry.toTaskManagerItem(entriesByPid) }

        return rootItems + processItems
    }

    internal fun buildItemsForTesting(
        healthSnapshot: RuntimeHealthSnapshot
    ): List<TaskManagerProcessItem> {
        return buildTaskItems(healthSnapshot)
    }

    private fun RuntimeRootSnapshot.toTaskManagerItem(): TaskManagerProcessItem {
        val pid = observedPid ?: expectedPid ?: 0
        return TaskManagerProcessItem(
            id = "root-${ownerKind.name}-${ownerId ?: pid}",
            pid = pid,
            parentPid = 0,
            title = processUnitDisplayName?.ifBlank { null } ?: title,
            subtitle = buildRootSubtitle(),
            sourceLabel = buildRootSourceLabel(),
            stateLabel = if (isRunning) "运行中" else reality.label,
            rawState = if (isRunning) "R" else "X",
            command = commandLine.substringBefore(' ').ifBlank { title },
            commandLine = commandLine,
            isSynthetic = false,
            linkedRuntimeId = ownerId.takeIf { ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME },
            linkedRuntimeTitle = title.takeIf { ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME },
            linkedTerminalSessionId = ownerId.takeIf { ownerKind == RuntimeRootOwnerKind.TERMINAL },
            linkedTerminalTitle = title.takeIf { ownerKind == RuntimeRootOwnerKind.TERMINAL },
            runtimeOwnerId = ownerId,
            runtimeUnitId = processUnitId,
            runtimeOwnerKindLabel = ownerKind.label,
            runtimeRootPid = pid.takeIf { it > 0 },
            runtimeRealityLabel = reality.label,
            runtimeStaleReason = staleReason,
            runtimeLastSeenAt = lastSeenAt,
            availableActions = buildRootAvailableActions()
        )
    }

    private fun RuntimeRootSnapshot.buildRootSubtitle(): String {
        val pidLabel = (observedPid ?: expectedPid)
            ?.takeIf { it > 0 }
            ?.let { "PID $it" }
            ?: "会话级"
        val processCountLabel = if (processCount > 1) {
            "进程数 $processCount"
        } else {
            null
        }
        val command = commandLine.trim()
        return buildList {
            add("${buildRootSourceLabel()} · $pidLabel")
            add("${ownerKind.label} · ${reality.label} · root=${observedPid ?: expectedPid ?: "?"}")
            if (!processCountLabel.isNullOrBlank()) {
                add(processCountLabel)
            }
            if (command.isNotBlank()) {
                add(command)
            }
        }.joinToString("\n")
    }

    private fun RuntimeRootSnapshot.buildRootSourceLabel(): String {
        return when (ownerKind) {
            RuntimeRootOwnerKind.TERMINAL -> "终端"
            RuntimeRootOwnerKind.CARD -> "卡片容器"
            RuntimeRootOwnerKind.RESOURCE -> "资源容器"
            RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> when (runtimeKind) {
                BackgroundRuntimeKind.CONTAINER_SUPERVISOR -> "后台 容器骨架"
                BackgroundRuntimeKind.PROOT_CAPACITY_WORKER -> "后台 PRoot 容量工作器"
                null -> "后台运行项"
                else -> "后台 ${runtimeKind.label}"
            }
            RuntimeRootOwnerKind.UNATTRIBUTED -> "未归属运行根"
        }
    }

    private fun RuntimeRootSnapshot.buildRootAvailableActions(): List<TaskManagerAction> {
        val actions = mutableListOf<TaskManagerAction>()
        when (ownerKind) {
            RuntimeRootOwnerKind.TERMINAL -> {
                actions += TaskManagerAction.OPEN_TERMINAL
            }
            RuntimeRootOwnerKind.CARD,
            RuntimeRootOwnerKind.RESOURCE -> Unit
            RuntimeRootOwnerKind.BACKGROUND_RUNTIME -> {
                actions += TaskManagerAction.STOP_RUNTIME
                actions += TaskManagerAction.RESTART_RUNTIME
                actions += TaskManagerAction.VIEW_LOG
            }
            RuntimeRootOwnerKind.UNATTRIBUTED -> {
                if ((observedPid ?: 0) > 0) {
                    actions += TaskManagerAction.END_PROCESS
                }
            }
        }
        actions += TaskManagerAction.REFRESH
        return actions
    }

    private fun ContainerProcessRecord.toTaskManagerItem(
        root: RuntimeRootSnapshot?
    ): TaskManagerProcessItem {
        return TaskManagerProcessItem(
            id = "process-$pid",
            pid = pid,
            parentPid = parentPid,
            title = title,
            subtitle = buildSubtitle(root),
            sourceLabel = sourceLabel,
            stateLabel = stateLabel,
            rawState = rawState,
            command = command,
            commandLine = commandLine,
            isSynthetic = isSynthetic,
            linkedRuntimeId = linkedRuntimeId,
            linkedRuntimeTitle = linkedRuntimeTitle,
            linkedTerminalSessionId = linkedTerminalSessionId,
            linkedTerminalTitle = linkedTerminalTitle,
            runtimeOwnerId = root?.ownerId,
            runtimeUnitId = root?.processUnitId,
            runtimeOwnerKindLabel = root?.ownerKind?.label,
            runtimeRootPid = root?.observedPid ?: root?.expectedPid,
            runtimeRealityLabel = root?.reality?.label,
            runtimeStaleReason = root?.staleReason,
            runtimeLastSeenAt = root?.lastSeenAt,
            availableActions = buildAvailableActions()
        )
    }

    private fun ProotLiveProcessEntry.toTaskManagerItem(
        entriesByPid: Map<Int, ProotLiveProcessEntry>
    ): TaskManagerProcessItem {
        val commandIdentity = commandIdentity()
        val title = commandTitle()
        val ownerEntry = ownerSource(entriesByPid)
        val runtimeOwnerId = ownerEntry.kfRuntimeId.takeIf { it.isNotBlank() }
        val runtimeUnitId = ownerEntry.kfUnitId.takeIf { it.isNotBlank() }
        val terminalSessionId = ownerEntry.terminalOwnerSessionId()
        return TaskManagerProcessItem(
            id = "ubuntu-process-$traceePid",
            pid = traceePid,
            parentPid = parentTraceePid ?: 0,
            title = title,
            subtitle = buildUbuntuProcessSubtitle(commandIdentity),
            sourceLabel = "Ubuntu 进程",
            stateLabel = when (state) {
                ProotLiveProcessState.RUNNING -> "运行中"
                ProotLiveProcessState.EXITED -> "已退出"
                ProotLiveProcessState.SIGNALED -> "已结束"
                ProotLiveProcessState.UNKNOWN -> "未知"
            },
            rawState = when (state) {
                ProotLiveProcessState.RUNNING -> "R"
                ProotLiveProcessState.EXITED -> "X"
                ProotLiveProcessState.SIGNALED -> "X"
                ProotLiveProcessState.UNKNOWN -> "?"
            },
            command = title,
            commandLine = commandIdentity,
            isSynthetic = false,
            linkedTerminalSessionId = terminalSessionId,
            linkedTerminalTitle = terminalSessionId?.let { "终端 $it" },
            runtimeOwnerId = runtimeOwnerId,
            runtimeUnitId = runtimeUnitId,
            runtimeOwnerKindLabel = ownerEntry.runtimeOwnerKindLabel(),
            runtimeRootPid = traceePid,
            runtimeRealityLabel = "PRoot 事件表",
            runtimeLastSeenAt = lastSeenAtMs.takeIf { it > 0L },
            availableActions = listOfNotNull(
                terminalSessionId?.let { TaskManagerAction.OPEN_TERMINAL },
                TaskManagerAction.END_PROCESS,
                TaskManagerAction.REFRESH
            )
        )
    }

    private fun ProotLiveProcessEntry.ownerSource(
        entriesByPid: Map<Int, ProotLiveProcessEntry>
    ): ProotLiveProcessEntry {
        var current = this
        val seen = mutableSetOf(traceePid)
        repeat(8) {
            if (current.kfRuntimeId.isNotBlank() || current.kfUnitId.isNotBlank()) return current
            val parentPid = current.parentTraceePid ?: return current
            if (!seen.add(parentPid)) return current
            current = entriesByPid[parentPid] ?: return current
        }
        return current
    }

    private fun ProotLiveProcessEntry.terminalOwnerSessionId(): String? =
        kfRuntimeId
            .takeIf { it.startsWith("terminal:") }
            ?.substringAfter(':')
            ?.takeIf { it.isNotBlank() }

    private fun ProotLiveProcessEntry.runtimeOwnerKindLabel(): String? =
        when (kfRuntimeId.substringBefore(':')) {
            "card" -> "卡片"
            "resource" -> "资源"
            "terminal" -> "终端"
            else -> null
        }

    private fun ProotLiveProcessEntry.buildUbuntuProcessSubtitle(commandIdentity: String): String {
        return buildList {
            add("Ubuntu 进程 · PID $traceePid")
            parentTraceePid?.takeIf { it > 0 }?.let { add("PPID $it") }
            processGroupId?.takeIf { it > 0 }?.let { add("PGID $it") }
            sessionId?.takeIf { it > 0 }?.let { add("SID $it") }
            if (kfRuntimeId.isNotBlank() || kfUnitId.isNotBlank()) {
                add(
                    listOfNotNull(
                        kfRuntimeId.takeIf { it.isNotBlank() }?.let { "runtime=$it" },
                        kfUnitId.takeIf { it.isNotBlank() }?.let { "unit=$it" }
                    ).joinToString(" · ")
                )
            }
            if (commandIdentity.isNotBlank()) {
                add(commandIdentity)
            }
        }.joinToString("\n")
    }

    private fun ProotLiveProcessEntry.commandIdentity(): String {
        return argvPreview.ifBlank { executable }.trim()
    }

    private fun ProotLiveProcessEntry.commandTitle(): String {
        return executable
            .substringAfterLast('/')
            .ifBlank { commandIdentity().substringBefore(' ') }
            .ifBlank { "pid-$traceePid" }
    }

    private fun ContainerProcessRecord.buildSubtitle(root: RuntimeRootSnapshot?): String {
        val pidLabel = if (pid > 0) "PID $pid" else "\u4f1a\u8bdd\u7ea7"
        val mappingLabel = when {
            !linkedTerminalTitle.isNullOrBlank() && !linkedRuntimeTitle.isNullOrBlank() ->
                "终端 ${linkedTerminalTitle} · 后台 ${linkedRuntimeTitle}"

            !linkedTerminalTitle.isNullOrBlank() -> "终端 ${linkedTerminalTitle}"
            !linkedRuntimeTitle.isNullOrBlank() -> "后台 ${linkedRuntimeTitle}"
            else -> sourceLabel
        }
        val base = "$mappingLabel \u00b7 $pidLabel"
        val rootLabel = root?.let { runtimeRoot ->
            val rootPid = runtimeRoot.observedPid ?: runtimeRoot.expectedPid
            val pid = rootPid?.let { "root=$it" } ?: "root=?"
            "${runtimeRoot.ownerKind.label} · ${runtimeRoot.reality.label} · $pid"
        }
        val trimmedCommand = commandLine.trim()
        return buildList {
            add(base)
            if (!rootLabel.isNullOrBlank()) {
                add(rootLabel)
            }
            if (trimmedCommand.isNotBlank()) {
                add(trimmedCommand)
            }
        }.joinToString("\n")
    }

    private fun ContainerProcessRecord.buildAvailableActions(): List<TaskManagerAction> {
        val actions = mutableListOf<TaskManagerAction>()
        if (!linkedTerminalSessionId.isNullOrBlank()) {
            actions += TaskManagerAction.OPEN_TERMINAL
        }
        if (!linkedRuntimeId.isNullOrBlank()) {
            actions += TaskManagerAction.STOP_RUNTIME
            actions += TaskManagerAction.RESTART_RUNTIME
            actions += TaskManagerAction.VIEW_LOG
        }
        if (!isSynthetic && pid > 0) {
            actions += TaskManagerAction.END_PROCESS
        }
        actions += TaskManagerAction.REFRESH
        return actions
    }
}
