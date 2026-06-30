package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerRecord

import android.content.Context
import android.system.OsConstants
import com.kite.app.foundation.jni.KFJni
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.service.BackgroundRuntimeRecord
import com.kite.app.foundation.service.BackgroundRuntimeRegistry
import com.kite.app.foundation.service.isActiveRuntime
import com.kite.app.foundation.terminal.TerminalRuntimeEntry
import com.kite.app.foundation.terminal.TerminalRuntimeRegistry
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.contracts.ManagedTerminalRecord
import com.kite.app.foundation.contracts.ManagedTerminalStatus
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.contracts.isLiveProcessStatus
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class ContainerProcessRecord(
    val pid: Int,
    val parentPid: Int,
    val processGroupId: Int? = null,
    val sessionId: Int? = null,
    val rawState: String,
    val stateLabel: String,
    val title: String,
    val sourceLabel: String,
    val command: String,
    val commandLine: String,
    val isSynthetic: Boolean = false,
    val linkedRuntimeId: String? = null,
    val linkedRuntimeTitle: String? = null,
    val linkedTerminalSessionId: String? = null,
    val linkedTerminalTitle: String? = null,
    val rssKb: Long? = null,
    val vmSizeKb: Long? = null,
    val oomScoreAdj: Int? = null,
    val cpuTimeTicks: Long? = null,
    val ioReadBytes: Long? = null,
    val ioWriteBytes: Long? = null
)

data class ContainerProcessSnapshot(
    val spaceId: String? = null,
    val processes: List<ContainerProcessRecord> = emptyList(),
    val refreshedAt: Long = 0L,
    val collectionSource: String = "unknown",
    val hostProcessCount: Int = 0,
    val containerProcessCount: Int = 0,
    val mergedProcessCount: Int = 0,
    val processSample: String = "",
    val resourceSnapshot: ContainerProcessResourceSnapshot = ContainerProcessResourceSnapshot()
)

data class ContainerProcessResourceSnapshot(
    val source: String = "none",
    val processCount: Int = 0,
    val rssKb: Long = 0L,
    val vmSizeKb: Long = 0L,
    val cpuTimeTicks: Long = 0L,
    val ioReadBytes: Long = 0L,
    val ioWriteBytes: Long = 0L,
    val refreshedAt: Long = 0L
) {
    val hasCpuCounter: Boolean
        get() = cpuTimeTicks > 0L

    val hasIoCounter: Boolean
        get() = ioReadBytes > 0L || ioWriteBytes > 0L

    val hasMemoryCounter: Boolean
        get() = rssKb > 0L || vmSizeKb > 0L

    val hasAnyCounter: Boolean
        get() = hasCpuCounter || hasIoCounter || hasMemoryCounter
}

data class ContainerProcessTerminationResult(
    val pid: Int,
    val processGroupId: Int? = null,
    val targetMode: String = "single_pid",
    val force: Boolean,
    val completed: Boolean,
    val exited: Boolean,
    val sentTerminate: Boolean = false,
    val sentKill: Boolean = false,
    val reason: String
)

object ContainerProcessStore {

    private const val LOG_TAG = "ContainerProcessStore"
    private const val PROCESS_LIST_TIMEOUT_SECONDS = 12L
    private const val PROCESS_ACTION_LOG_FILE = "task-manager-process-actions.log"
    private const val MIN_REFRESH_INTERVAL_MS = 450L
    private const val SOURCE_CONTAINER_PS = "container_ps"
    private const val SOURCE_CONTAINER_PS_AUGMENTED = "container_ps+host_targeted"
    private const val SOURCE_HOST_PROC = "host_proc"
    private const val SOURCE_HOST_PROC_WITH_CONTAINER_PS = "host_proc+container_ps"
    private const val SOURCE_HOST_FALLBACK = "host_fallback"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(ContainerProcessSnapshot())
    val snapshot: StateFlow<ContainerProcessSnapshot> = _snapshot

    @Volatile
    private var refreshJob: Job? = null

    @Volatile
    private var pendingRefresh = false

    @Volatile
    private var lastRefreshAt = 0L

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            val running = refreshJob
            if (running != null && running.isActive) {
                pendingRefresh = true
                Logger.i(LOG_TAG, "refresh 已合并到当前采样批次")
                return
            }

            val delayMs = (lastRefreshAt + MIN_REFRESH_INTERVAL_MS - System.currentTimeMillis())
                .coerceAtLeast(0L)

            refreshJob = scope.launch {
                try {
                    if (delayMs > 0L) {
                        delay(delayMs)
                    }
                    do {
                        clearPendingRefresh()
                        lastRefreshAt = System.currentTimeMillis()
                        _snapshot.value = collectSnapshot(appContext)
                    } while (consumePendingRefresh())
                } finally {
                    synchronized(this@ContainerProcessStore) {
                        refreshJob = null
                    }
                }
            }
        }
    }

    fun terminate(context: Context, pid: Int, force: Boolean = false) {
        val appContext = context.applicationContext
        actionScope.launch {
            terminateInternal(
                context = appContext,
                pid = pid,
                force = force,
                requester = "task_manager"
            )
        }
    }

    fun terminateForRuntimeReclaimer(
        context: Context,
        pid: Int,
        processGroupId: Int? = null,
        force: Boolean = false
    ): ContainerProcessTerminationResult {
        val appContext = context.applicationContext
        return runBlocking(Dispatchers.IO) {
            terminateInternal(
                context = appContext,
                pid = pid,
                processGroupId = processGroupId,
                force = force,
                requester = "runtime_reclaimer"
            )
        }
    }

    private suspend fun terminateInternal(
        context: Context,
        pid: Int,
        processGroupId: Int? = null,
        force: Boolean,
        requester: String
    ): ContainerProcessTerminationResult {
        val trackedRecord = _snapshot.value.processes.firstOrNull { it.pid == pid }
        val safeProcessGroupId = processGroupId
            ?.takeIf { it > 1 }
            ?: trackedRecord?.processGroupId?.takeIf { it > 1 && it == pid }
        val stopAuditSeed = HostStopAuditor.capture(pid, LOG_TAG)
        writeProcessActionLog(
            context = context,
            content = buildString {
                append("== 收到结束进程请求 ==\n")
                append("requester=$requester pid=$pid force=$force ")
                safeProcessGroupId?.let { append("pgid=$it mode=ubuntu_process_group ") }
                append("title=${trackedRecord?.title ?: "PID $pid"} ")
                append("source=${trackedRecord?.sourceLabel ?: "unknown"}\n")
                trackedRecord?.commandLine
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append("command=$it\n") }
            }
        )
        return runCatching {
            val groupTarget = if (!force) safeProcessGroupId else null
            val useProcessGroup = groupTarget != null
            val outcome = if (groupTarget != null) {
                terminateUbuntuProcessGroup(
                    context = context,
                    pid = pid,
                    processGroupId = groupTarget
                ).also { outcome ->
                    Logger.i(
                        LOG_TAG,
                        "结束 Ubuntu 进程组完成: requester=$requester pid=$pid pgid=$groupTarget exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill}"
                    )
                    writeProcessActionLog(
                        context = context,
                        content = "== 结束 Ubuntu 进程组 requester=$requester pid=$pid pgid=$groupTarget exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill} ==\n"
                    )
                }
            } else if (force && requester == "runtime_reclaimer") {
                killUbuntuProcessPid(
                    context = context,
                    pid = pid
                ).also { outcome ->
                    Logger.i(
                        LOG_TAG,
                        "强制 KO Ubuntu 单进程完成: requester=$requester pid=$pid exited=${outcome.exited} kill=${outcome.sentKill}"
                    )
                    writeProcessActionLog(
                        context = context,
                        content = "== 强制 KO Ubuntu 单进程 requester=$requester pid=$pid exited=${outcome.exited} kill=${outcome.sentKill} ==\n"
                    )
                }
            } else if (force) {
                killUbuntuProcessPid(
                    context = context,
                    pid = pid
                ).also { outcome ->
                    Logger.i(
                        LOG_TAG,
                        "强制 KO Ubuntu 单进程完成: requester=$requester pid=$pid exited=${outcome.exited} kill=${outcome.sentKill}"
                    )
                    writeProcessActionLog(
                        context = context,
                        content = "== 强制 KO Ubuntu 单进程 requester=$requester pid=$pid exited=${outcome.exited} kill=${outcome.sentKill} ==\n"
                    )
                }
            } else if (requester == "runtime_reclaimer") {
                terminateUbuntuProcessPid(
                    context = context,
                    pid = pid
                ).also { outcome ->
                    Logger.i(
                        LOG_TAG,
                        "结束 Ubuntu 单进程完成: requester=$requester pid=$pid exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill}"
                    )
                    writeProcessActionLog(
                        context = context,
                        content = "== 结束 Ubuntu 单进程 requester=$requester pid=$pid exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill} ==\n"
                    )
                }
            } else {
                HostProcessTerminator.terminateHostProcess(pid) { message ->
                    Logger.i(LOG_TAG, "结束进程补偿: pid=$pid $message")
                }.also { outcome ->
                    Logger.i(
                        LOG_TAG,
                        "结束进程完成: requester=$requester pid=$pid mode=term exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill}"
                    )
                    writeProcessActionLog(
                        context = context,
                        content = "== 结束进程补偿 requester=$requester pid=$pid mode=term exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill} ==\n"
                    )
                }
            }
            HostStopAuditor.audit(stopAuditSeed, LOG_TAG)?.let { report ->
                Logger.i(LOG_TAG, "结束进程诊断: pid=$pid ${report.toCompactSummary()}")
                writeProcessActionLog(
                    context = context,
                    content = report.toLogBlock("结束进程诊断 PID $pid")
                )
            }
            _snapshot.value = collectSnapshot(context)
            delay(420L)
            _snapshot.value = collectSnapshot(context)
            ContainerProcessTerminationResult(
                pid = pid,
                processGroupId = safeProcessGroupId,
                targetMode = if (useProcessGroup) "ubuntu_process_group" else "single_pid",
                force = force,
                completed = true,
                exited = outcome.exited,
                sentTerminate = outcome.sentTerminate,
                sentKill = outcome.sentKill,
                reason = if (outcome.exited) {
                    "process_exited"
                } else {
                    "process_still_alive_after_signal"
                }
            )
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "terminate failed: requester=$requester pid=$pid, ${error.message}")
            writeProcessActionLog(
                context = context,
                content = "== 结束进程失败 requester=$requester pid=$pid ==\n${error.stackTraceToString()}\n"
            )
            ContainerProcessTerminationResult(
                pid = pid,
                processGroupId = safeProcessGroupId,
                targetMode = if (safeProcessGroupId != null && !force) "ubuntu_process_group" else "single_pid",
                force = force,
                completed = false,
                exited = false,
                reason = error.message ?: "terminate_failed"
            )
        }
    }

    private suspend fun terminateUbuntuProcessPid(
        context: Context,
        pid: Int
    ): HostTerminationOutcome {
        if (!isProcessTracked(pid, _snapshot.value) && !isContainerProcessAlive(context, pid)) {
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = false,
                sentHangup = false,
                sentTerminate = false,
                sentKill = false,
                exited = true
            )
        }

        val sentTerminate = sendContainerProcessSignal(context, pid, OsConstants.SIGTERM)
        delay(900L)
        var refreshed = collectSnapshot(context)
        _snapshot.value = refreshed
        if (!isContainerProcessAlive(context, pid)) {
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = false,
                sentHangup = false,
                sentTerminate = sentTerminate,
                sentKill = false,
                exited = true
            )
        }

        val sentKill = sendContainerProcessSignal(context, pid, OsConstants.SIGKILL)
        delay(900L)
        refreshed = collectSnapshot(context)
        _snapshot.value = refreshed
        val exited = !isContainerProcessAlive(context, pid)
        return HostTerminationOutcome(
            pid = pid,
            usedProcessGroup = false,
            sentHangup = false,
            sentTerminate = sentTerminate,
            sentKill = sentKill,
            exited = exited
        )
    }

    private suspend fun killUbuntuProcessPid(
        context: Context,
        pid: Int
    ): HostTerminationOutcome {
        if (!isProcessTracked(pid, _snapshot.value) && !isContainerProcessAlive(context, pid)) {
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = false,
                sentHangup = false,
                sentTerminate = false,
                sentKill = false,
                exited = true
            )
        }

        val sentKill = sendContainerProcessSignal(context, pid, OsConstants.SIGKILL)
        delay(420L)
        val refreshed = collectSnapshot(context)
        _snapshot.value = refreshed
        val exited = !isContainerProcessAlive(context, pid)
        return HostTerminationOutcome(
            pid = pid,
            usedProcessGroup = false,
            sentHangup = false,
            sentTerminate = false,
            sentKill = sentKill,
            exited = exited
        )
    }

    private suspend fun terminateUbuntuProcessGroup(
        context: Context,
        pid: Int,
        processGroupId: Int
    ): HostTerminationOutcome {
        if (!isProcessOrGroupTracked(pid, processGroupId, _snapshot.value)) {
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = true,
                sentHangup = false,
                sentTerminate = false,
                sentKill = false,
                exited = true
            )
        }

        val sentTerminate = sendProcessGroupSignal(processGroupId, OsConstants.SIGTERM)
        delay(900L)
        var refreshed = collectSnapshot(context)
        _snapshot.value = refreshed
        if (!isProcessOrGroupTracked(pid, processGroupId, refreshed)) {
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = true,
                sentHangup = false,
                sentTerminate = sentTerminate,
                sentKill = false,
                exited = true
            )
        }

        val sentKill = sendProcessGroupSignal(processGroupId, OsConstants.SIGKILL)
        delay(350L)
        refreshed = collectSnapshot(context)
        _snapshot.value = refreshed
        val exited = !isProcessOrGroupTracked(pid, processGroupId, refreshed)
        return HostTerminationOutcome(
            pid = pid,
            usedProcessGroup = true,
            sentHangup = false,
            sentTerminate = sentTerminate,
            sentKill = sentKill,
            exited = exited
        )
    }

    private fun sendProcessSignal(pid: Int, signal: Int): Boolean {
        if (pid <= 1) return false
        return runCatching {
            KFJni.sendSignal(pid, signal)
        }.getOrElse { error ->
            Logger.i(LOG_TAG, "发送 Ubuntu 单进程信号失败: pid=$pid signal=$signal error=${error.message}")
            false
        }
    }

    private fun sendContainerProcessSignal(context: Context, pid: Int, signal: Int): Boolean {
        if (pid <= 1) return false
        if (signal != OsConstants.SIGTERM && signal != OsConstants.SIGKILL) return false
        return runCatching {
            runContainerShellCommand(
                context = context,
                payload = "kill -$signal $pid >/dev/null 2>&1",
                workingDirectory = "/root"
            ).exitCode == 0
        }.getOrElse { error ->
            Logger.i(LOG_TAG, "发送 Ubuntu 容器内信号失败: pid=$pid signal=$signal error=${error.message}")
            false
        }
    }

    private fun sendProcessGroupSignal(processGroupId: Int, signal: Int): Boolean {
        if (processGroupId <= 1) return false
        val target = -processGroupId
        return runCatching {
            KFJni.sendSignal(target, signal)
        }.getOrElse { error ->
            Logger.i(LOG_TAG, "发送 Ubuntu 进程组信号失败: pgid=$processGroupId signal=$signal error=${error.message}")
            false
        }
    }

    private fun isProcessTracked(
        pid: Int,
        snapshot: ContainerProcessSnapshot
    ): Boolean {
        return snapshot.processes.any { process -> process.pid == pid }
    }

    private fun isProcessOrGroupTracked(
        pid: Int,
        processGroupId: Int,
        snapshot: ContainerProcessSnapshot
    ): Boolean {
        return snapshot.processes.any { process ->
            process.pid == pid || process.processGroupId == processGroupId
        }
    }

    private fun collectSnapshot(context: Context): ContainerProcessSnapshot {
        val space = runCatching {
            KFWorkspaceManager.getCurrentSpace(context)
                ?: KFWorkspaceManager.listSpaces(context).firstOrNull()
                ?: KFWorkspaceManager.ensureDefaultSpace(context)
        }.getOrNull()
        if (space == null) {
            return ContainerProcessSnapshot(refreshedAt = System.currentTimeMillis())
        }

        val container = WorkSurfaceRuntimeBridge.resolveActiveContainer(context)
        val terminals = resolveTrackedTerminals(context, space.id)
        val runtimes = BackgroundRuntimeRegistry.snapshot(space.id)
        val knownRoots = buildKnownTrackedRoots(terminals, runtimes)

        val collectionContext = CollectionContext(container)
        val hostProcesses = collectHostProcesses(collectionContext, knownRoots)
        val collection = collectContainerProcesses(context, container, knownRoots, collectionContext)
        val mergedCollection = mergeProcessCollectionsForObservation(
            hostProcesses = hostProcesses,
            containerProcesses = collection.processes,
            containerSource = collection.source
        )
        val rawProcesses = mergedCollection.processes
        val source = mergedCollection.source

        val visibleProcesses = annotateDisplayProcesses(rawProcesses, terminals, runtimes)
        val logSample = buildSnapshotLogSample(visibleProcesses)

        Logger.i(
            LOG_TAG,
            "snapshot source=$source raw=${rawProcesses.size} visible=${visibleProcesses.size} sample=$logSample"
        )

        val refreshedAt = System.currentTimeMillis()
        return ContainerProcessSnapshot(
            spaceId = space.id,
            processes = visibleProcesses.sortedWith(
                compareBy<ContainerProcessRecord> { it.title.lowercase() }
                    .thenBy { it.pid }
            ),
            refreshedAt = refreshedAt,
            collectionSource = source,
            hostProcessCount = hostProcesses.size,
            containerProcessCount = collection.processes.size,
            mergedProcessCount = rawProcesses.size,
            processSample = logSample,
            resourceSnapshot = visibleProcesses.toResourceSnapshot(source, refreshedAt)
        )
    }

    private fun buildSnapshotLogSample(
        processes: List<ContainerProcessRecord>
    ): String {
        if (processes.isEmpty()) {
            return ""
        }
        val sorted = processes.sortedBy { it.pid }
        val sample = if (sorted.size <= 8) {
            sorted
        } else {
            (sorted.take(4) + sorted.takeLast(4))
                .distinctBy { it.pid }
                .sortedBy { it.pid }
        }
        return sample.joinToString { "${it.pid}:${it.command}" }
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

    private data class CollectionResult(
        val processes: List<RawProcessRecord>,
        val source: String
    )

    internal data class ProcessCollectionMergeResult(
        val processes: List<RawProcessRecord>,
        val source: String
    )

    internal fun mergeProcessCollectionsForObservation(
        hostProcesses: List<RawProcessRecord>,
        containerProcesses: List<RawProcessRecord>,
        containerSource: String
    ): ProcessCollectionMergeResult {
        if (hostProcesses.isEmpty()) {
            return ProcessCollectionMergeResult(
                processes = containerProcesses,
                source = if (containerProcesses.isNotEmpty()) containerSource else "none"
            )
        }
        if (containerProcesses.isEmpty()) {
            return ProcessCollectionMergeResult(
                processes = hostProcesses,
                source = SOURCE_HOST_PROC
            )
        }

        val merged = linkedMapOf<Int, RawProcessRecord>()
        hostProcesses.forEach { process ->
            merged[process.pid] = process
        }
        containerProcesses.forEach { process ->
            merged.putIfAbsent(process.pid, process)
        }
        return ProcessCollectionMergeResult(
            processes = merged.values.sortedBy { it.pid },
            source = SOURCE_HOST_PROC_WITH_CONTAINER_PS
        )
    }

    private class CollectionContext(
        private val container: ContainerRecord
    ) {
        private var hostSnapshot: HostProcessSnapshot? = null

        fun hostSnapshot(): HostProcessSnapshot {
            return hostSnapshot ?: HostProcessInspector.readSnapshot(
                logTag = LOG_TAG,
                timeoutSeconds = PROCESS_LIST_TIMEOUT_SECONDS
            ).also { snapshot ->
                hostSnapshot = snapshot
            }
        }

        fun hostTargetedProcesses(
            knownRoots: KnownTrackedRoots,
            allowBroadFallback: Boolean = true
        ): List<RawProcessRecord> {
            val snapshot = hostSnapshot()
            val rootScoped = snapshot
                .collectTrackedRootSubtrees(knownRoots.rootPids)
                .toRawProcessRecords()
            if (rootScoped.isNotEmpty()) {
                Logger.i(
                    LOG_TAG,
                    "host targeted roots ok: roots=${knownRoots.rootPids.sorted().joinToString()} size=${rootScoped.size}"
                )
                return rootScoped
            }
            if (!allowBroadFallback) {
                return emptyList()
            }

            val extraCommands = knownRoots.commandHints
                .map { it.lowercase() }
                .toSet()
            val includeDefaultCommands = knownRoots.rootPids.isEmpty() && extraCommands.isEmpty()
            val broadProcesses = snapshot
                .collectContainerSubtree(
                    container = container,
                    extraCommands = extraCommands,
                    includeDefaultCommands = includeDefaultCommands
                )
                .toRawProcessRecords()
            if (broadProcesses.isNotEmpty()) {
                Logger.i(
                    LOG_TAG,
                    "host targeted broad ok: size=${broadProcesses.size} hints=${extraCommands.sorted().joinToString()} includeDefaults=$includeDefaultCommands"
                )
            }
            return broadProcesses
        }
    }

    private fun collectContainerProcesses(
        context: Context,
        container: ContainerRecord,
        knownRoots: KnownTrackedRoots,
        collectionContext: CollectionContext
    ): CollectionResult {
        val rootfsPath = container.rootfsPath
        val workspacePath = container.workspacePath

        runCatching {
            val result = runContainerArgvCommand(
                context = context,
                argv = listOf("/usr/bin/ps", "-eo", "pid=,ppid=,pgid=,sid=,stat=,comm=,args=")
            )
            if (result.exitCode == 0) {
                val filtered = filterForContainerProcesses(
                    allProcesses = result.output.lineSequence()
                        .mapNotNull(::parsePsProcessLine)
                        .toList(),
                    rootfsPath = rootfsPath,
                    workspacePath = workspacePath,
                    knownRoots = knownRoots
                )
                if (filtered.isNotEmpty()) {
                    return finalizeContainerCollection(
                        containerProcesses = filtered,
                        knownRoots = knownRoots,
                        collectionContext = collectionContext,
                        baseSource = SOURCE_CONTAINER_PS,
                        augmentedSource = SOURCE_CONTAINER_PS_AUGMENTED,
                        sourceLabel = "container ps"
                    )
                }
            } else {
                Logger.i(LOG_TAG, "container ps exit=${result.exitCode} output=${result.output.take(120)}")
            }
        }.onFailure { error ->
            Logger.i(LOG_TAG, "container ps failed: ${error.message}")
        }

        return CollectionResult(emptyList(), "none")
    }

    private fun finalizeContainerCollection(
        containerProcesses: List<RawProcessRecord>,
        knownRoots: KnownTrackedRoots,
        collectionContext: CollectionContext,
        baseSource: String,
        augmentedSource: String,
        sourceLabel: String
    ): CollectionResult {
        val augmented = augmentMissingTrackedRootsWithHost(
            containerProcesses = containerProcesses,
            knownRoots = knownRoots,
            collectionContext = collectionContext,
            sourceLabel = sourceLabel
        )
        if (augmented != null) {
            Logger.i(LOG_TAG, "$sourceLabel 缺根已补齐: ${augmented.size}")
            return CollectionResult(augmented, augmentedSource)
        }
        Logger.i(LOG_TAG, "$sourceLabel ok: ${containerProcesses.size}")
        return CollectionResult(containerProcesses, baseSource)
    }

    private fun augmentMissingTrackedRootsWithHost(
        containerProcesses: List<RawProcessRecord>,
        knownRoots: KnownTrackedRoots,
        collectionContext: CollectionContext,
        sourceLabel: String
    ): List<RawProcessRecord>? {
        if (containerProcesses.isEmpty() || knownRoots.rootPids.isEmpty()) {
            return null
        }

        val hostSnapshot = collectionContext.hostSnapshot()
        val visibleKnownRoots = knownRoots.rootPids.filterTo(linkedSetOf()) { rootPid ->
            hostSnapshot.appProcess(rootPid) != null
        }
        if (visibleKnownRoots.isEmpty()) {
            return null
        }

        val containerMap = containerProcesses.associateBy { it.pid }
        val coveredRoots = visibleKnownRoots.filterTo(linkedSetOf()) { rootPid ->
            containerProcesses.any { process ->
                process.pid == rootPid ||
                    findTrackedRootPid(process, containerMap, setOf(rootPid)) == rootPid
            }
        }
        val missingRoots = visibleKnownRoots - coveredRoots
        if (missingRoots.isEmpty()) {
            return null
        }

        val hostTargeted = collectionContext.hostTargetedProcesses(
            knownRoots = knownRoots,
            allowBroadFallback = false
        )
        if (hostTargeted.isEmpty()) {
            return null
        }

        val hostMap = hostTargeted.associateBy { it.pid }
        val missingSubtree = hostTargeted.filter { process ->
            process.pid in missingRoots ||
                findTrackedRootPid(process, hostMap, missingRoots) != null
        }
        if (missingSubtree.isEmpty()) {
            return null
        }

        Logger.i(
            LOG_TAG,
            "$sourceLabel 缺失已知根=${missingRoots.joinToString()}，host 补齐=${missingSubtree.joinToString { "${it.pid}:${it.command}" }}"
        )
        return (containerProcesses + missingSubtree)
            .distinctBy { it.pid }
            .sortedBy { it.pid }
    }

    private fun collectHostProcesses(
        collectionContext: CollectionContext,
        knownRoots: KnownTrackedRoots
    ): List<RawProcessRecord> {
        val targeted = collectionContext.hostTargetedProcesses(
            knownRoots = knownRoots,
            allowBroadFallback = true
        )
        if (targeted.isNotEmpty()) {
            Logger.i(LOG_TAG, "host fallback ok: ${targeted.size}")
        }
        return targeted
    }

    private fun filterForContainerProcesses(
        allProcesses: List<RawProcessRecord>,
        rootfsPath: String,
        workspacePath: String,
        knownRoots: KnownTrackedRoots
    ): List<RawProcessRecord> {
        if (allProcesses.isEmpty()) return emptyList()

        val processMap = allProcesses.associateBy { it.pid }
        val aliases = buildSet {
            addAll(WorkSurfaceRuntimeBridge.hostPathAliases(rootfsPath, workspacePath))
            addAll(WorkSurfaceRuntimeBridge.containerPathAliases())
            add("ubuntu-main")
        }

        val directRootPids = allProcesses
            .filter { process ->
                process.command.equals("proot", ignoreCase = true) ||
                    aliases.any { alias -> process.commandLine.contains(alias) } ||
                    process.pid in knownRoots.rootPids
            }
            .map { it.pid }
            .toSet()

        val trackedRoots = if (directRootPids.isNotEmpty()) {
            directRootPids
        } else {
            allProcesses
                .filter { process ->
                    val normalizedCommand = process.command.lowercase()
                    normalizedCommand in CONTAINER_VISIBLE_COMMANDS ||
                        normalizedCommand in knownRoots.commandHints
                }
                .map { it.pid }
                .toSet()
        }
        if (trackedRoots.isEmpty()) return emptyList()

        return allProcesses.filter { process ->
            (process.pid in trackedRoots ||
                findTrackedRootPid(process, processMap, trackedRoots) != null) &&
                !process.command.equals("ps", ignoreCase = true)
        }
    }

    private fun annotateDisplayProcesses(
        allProcesses: List<RawProcessRecord>,
        terminals: List<TerminalRuntimeEntry>,
        runtimes: List<BackgroundRuntimeRecord>
    ): List<ContainerProcessRecord> {
        if (allProcesses.isEmpty()) return emptyList()

        val processMap = allProcesses.associateBy { it.pid }
        val terminalRoots = terminals
            .filter { it.status.isLiveTerminalStatus() }
            .mapNotNull { entry ->
                entry.lastPid?.takeIf { pid -> pid > 0 }?.let { pid -> pid to entry }
            }
            .toMap()
        val runtimeRoots = runtimes
            .filter { it.isActiveRuntime() }
            .mapNotNull { runtime ->
                runtime.pid?.takeIf { pid -> pid > 0 }?.let { pid -> pid to runtime }
            }
            .toMap()

        return allProcesses.map { process ->
            val linkedTerminal = findTrackedRootPid(process, processMap, terminalRoots.keys)
                ?.let(terminalRoots::get)
            val linkedRuntime = findTrackedRootPid(process, processMap, runtimeRoots.keys)
                ?.let(runtimeRoots::get)

            process.toProcessRecord(linkedTerminal, linkedRuntime)
        }
    }

    private fun findTrackedRootPid(
        process: RawProcessRecord,
        processMap: Map<Int, RawProcessRecord>,
        rootPids: Set<Int>
    ): Int? {
        if (rootPids.isEmpty()) return null
        if (process.pid in rootPids) return process.pid

        var currentParent = process.parentPid
        var guard = 0
        while (currentParent > 1 && guard < 64) {
            if (currentParent in rootPids) return currentParent
            currentParent = processMap[currentParent]?.parentPid ?: break
            guard += 1
        }
        return null
    }

    private fun RawProcessRecord.toProcessRecord(
        linkedTerminal: TerminalRuntimeEntry?,
        linkedRuntime: BackgroundRuntimeRecord?
    ): ContainerProcessRecord {
        val sourceLabel = when {
            linkedTerminal != null -> "终端 · ${linkedTerminal.title}"
            linkedRuntime != null -> "任务 · ${linkedRuntime.title}"
            else -> "Linux 进程"
        }

        return ContainerProcessRecord(
            pid = pid,
            parentPid = parentPid,
            processGroupId = processGroupId,
            sessionId = sessionId,
            rawState = rawState,
            stateLabel = mapProcessState(rawState),
            title = command,
            sourceLabel = sourceLabel,
            command = command,
            commandLine = commandLine,
            isSynthetic = false,
            linkedRuntimeId = linkedRuntime?.id,
            linkedRuntimeTitle = linkedRuntime?.title,
            linkedTerminalSessionId = linkedTerminal?.sessionId,
            linkedTerminalTitle = linkedTerminal?.title,
            rssKb = rssKb,
            vmSizeKb = vmSizeKb,
            oomScoreAdj = oomScoreAdj,
            cpuTimeTicks = cpuTimeTicks,
            ioReadBytes = ioReadBytes,
            ioWriteBytes = ioWriteBytes
        )
    }

    private fun mapProcessState(rawState: String): String {
        return when (rawState.firstOrNull()?.uppercaseChar()) {
            'R' -> "运行中"
            'S', 'D', 'I' -> "运行中"
            'T' -> "已暂停"
            'Z' -> "僵尸"
            else -> "未知"
        }
    }

    private fun parsePsProcessLine(line: String): RawProcessRecord? {
        val match = PS_PROCESS_REGEX.matchEntire(line.trim()) ?: return null
        val pid = match.groupValues[1].toIntOrNull() ?: return null
        val parentPid = match.groupValues[2].toIntOrNull() ?: return null
        val processGroupId: Int?
        val sessionId: Int?
        val rawState: String
        val command: String
        val commandLine: String
        if (match.groupValues[3].isNotBlank() && match.groupValues[4].isNotBlank()) {
            processGroupId = match.groupValues[3].toIntOrNull()
            sessionId = match.groupValues[4].toIntOrNull()
            rawState = match.groupValues[5].trim()
            command = match.groupValues[6].trim().ifBlank { return null }
            commandLine = match.groupValues[7].trim().ifBlank { command }
        } else {
            processGroupId = null
            sessionId = null
            rawState = match.groupValues[5].trim()
            command = match.groupValues[6].trim().ifBlank { return null }
            commandLine = match.groupValues[7].trim().ifBlank { command }
        }

        return RawProcessRecord(
            pid = pid,
            parentPid = parentPid,
            processGroupId = processGroupId,
            sessionId = sessionId,
            rawState = rawState,
            command = command,
            commandLine = commandLine
        )
    }

    private fun runContainerShellCommand(
        context: Context,
        payload: String,
        workingDirectory: String = "/root"
    ): ShellCommandResult {
        // 进程采样属于工作面观察面，真正进入容器执行时仍统一走 bridge。
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = workingDirectory,
            payload = payload
        )
        return executeCommand(config.command, config.env)
    }

    private fun runContainerArgvCommand(
        context: Context,
        argv: List<String>,
        workingDirectory: String = "/root"
    ): ShellCommandResult {
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            workingDirectory = workingDirectory,
            argv = argv
        )
        return executeCommand(config.command, config.env)
    }

    private fun isContainerProcessAlive(context: Context, pid: Int): Boolean {
        if (pid <= 0) return false
        return runCatching {
            runContainerShellCommand(
                context = context,
                payload = "kill -0 $pid >/dev/null 2>&1",
                workingDirectory = "/root"
            ).exitCode == 0
        }.getOrDefault(false)
    }

    private fun executeCommand(
        command: List<String>,
        env: Map<String, String>
    ): ShellCommandResult {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply {
                environment().putAll(env)
            }
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val timedOut = !process.waitFor(PROCESS_LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return ShellCommandResult(process.exitValue(), output, timedOut)
    }

    private fun ManagedTerminalStatus.isLiveTerminalStatus(): Boolean {
        return isLiveProcessStatus()
    }

    internal data class RawProcessRecord(
        val pid: Int,
        val parentPid: Int,
        val processGroupId: Int? = null,
        val sessionId: Int? = null,
        val rawState: String,
        val command: String,
        val commandLine: String,
        val rssKb: Long? = null,
        val vmSizeKb: Long? = null,
        val oomScoreAdj: Int? = null,
        val cpuTimeTicks: Long? = null,
        val ioReadBytes: Long? = null,
        val ioWriteBytes: Long? = null
    )

    private data class KnownTrackedRoots(
        val rootPids: Set<Int> = emptySet(),
        val commandHints: Set<String> = emptySet()
    )

    private fun resolveTrackedTerminals(context: Context, spaceId: String): List<TerminalRuntimeEntry> {
        val persistedEntries = KFWorkspaceManager.listTerminalSessions(context, spaceId)
            .map { it.toTrackedRuntimeEntry() }
        val runtimeEntries = TerminalRuntimeRegistry.snapshot().filter { it.spaceId == spaceId }
        if (runtimeEntries.isEmpty()) {
            return persistedEntries
        }

        val merged = linkedMapOf<String, TerminalRuntimeEntry>()
        persistedEntries.forEach { entry ->
            merged[entry.sessionId] = entry
        }
        runtimeEntries.forEach { entry ->
            val persisted = merged[entry.sessionId]
            merged[entry.sessionId] = mergeTrackedTerminalEntry(persisted, entry)
        }
        return merged.values.toList()
    }

    private fun buildKnownTrackedRoots(
        terminals: List<TerminalRuntimeEntry>,
        runtimes: List<BackgroundRuntimeRecord>
    ): KnownTrackedRoots {
        val rootPids = buildSet {
            terminals
                .filter { it.status.isLiveTerminalStatus() }
                .mapNotNull { entry -> entry.lastPid?.takeIf { pid -> pid > 0 } }
                .forEach(::add)
            runtimes
                .filter { it.isActiveRuntime() }
                .mapNotNull { runtime -> runtime.pid?.takeIf { pid -> pid > 0 } }
                .forEach(::add)
        }
        val commandHints = buildSet {
            terminals
                .asSequence()
                .filter { it.status.isLiveTerminalStatus() }
                .mapNotNull { entry -> entry.startupCommand.extractTrackedCommandHint() }
                .forEach(::add)
            runtimes
                .asSequence()
                .filter { it.isActiveRuntime() }
                .mapNotNull { runtime -> runtime.startCommand.extractTrackedCommandHint() }
                .forEach(::add)
        }
        return KnownTrackedRoots(rootPids = rootPids, commandHints = commandHints)
    }

    private fun mergeTrackedTerminalEntry(
        persisted: TerminalRuntimeEntry?,
        runtime: TerminalRuntimeEntry
    ): TerminalRuntimeEntry {
        if (persisted == null) {
            return runtime
        }

        val mergedStatus = when {
            runtime.status.isLiveTerminalStatus() -> runtime.status
            persisted.status.isLiveTerminalStatus() -> persisted.status
            else -> runtime.status
        }
        val mergedPid = runtime.lastPid?.takeIf { it > 0 } ?: persisted.lastPid?.takeIf { it > 0 }

        return runtime.copy(
            status = mergedStatus,
            lastAttachedAt = runtime.lastAttachedAt ?: persisted.lastAttachedAt,
            lastStartedAt = runtime.lastStartedAt ?: persisted.lastStartedAt,
            lastExitedAt = runtime.lastExitedAt ?: persisted.lastExitedAt,
            lastPid = mergedPid,
            lastExitCode = runtime.lastExitCode ?: persisted.lastExitCode,
            sourceAgentRuntimeId = runtime.sourceAgentRuntimeId ?: persisted.sourceAgentRuntimeId,
            startupCommand = runtime.startupCommand ?: persisted.startupCommand,
            transcriptPath = runtime.transcriptPath.ifBlank { persisted.transcriptPath },
            isActive = runtime.isActive || persisted.isActive
        )
    }

    private fun HostProcessSnapshot.collectTrackedRootSubtrees(
        rootPids: Set<Int>
    ): List<HostProcessRecord> {
        if (rootPids.isEmpty()) {
            return emptyList()
        }
        return rootPids
            .asSequence()
            .filter { it > 0 }
            .flatMap { rootPid -> collectTrackedSubtree(rootPid).asSequence() }
            .distinctBy { it.pid }
            .sortedBy { it.pid }
            .toList()
    }

    private fun List<HostProcessRecord>.toRawProcessRecords(): List<RawProcessRecord> {
        return map { process ->
            RawProcessRecord(
                pid = process.pid,
                parentPid = process.parentPid,
                processGroupId = process.processGroupId,
                sessionId = process.sessionId,
                rawState = process.rawState,
                command = process.command.trimStart('[').trimEnd(']'),
                commandLine = process.commandLine,
                rssKb = process.rssKb,
                vmSizeKb = process.vmSizeKb,
                oomScoreAdj = process.oomScoreAdj,
                cpuTimeTicks = process.cpuTimeTicks,
                ioReadBytes = process.ioReadBytes,
                ioWriteBytes = process.ioWriteBytes
            )
        }.filterNot { process ->
            process.command.equals("ps", ignoreCase = true) ||
                process.command.equals("com.kite.app", ignoreCase = true)
        }
    }

    private fun List<ContainerProcessRecord>.toResourceSnapshot(
        source: String,
        refreshedAt: Long
    ): ContainerProcessResourceSnapshot {
        if (isEmpty()) {
            return ContainerProcessResourceSnapshot(
                source = "$source:empty",
                refreshedAt = refreshedAt
            )
        }
        return ContainerProcessResourceSnapshot(
            source = "process_snapshot:$source",
            processCount = size,
            rssKb = sumOf { it.rssKb ?: 0L },
            vmSizeKb = sumOf { it.vmSizeKb ?: 0L },
            cpuTimeTicks = sumOf { it.cpuTimeTicks ?: 0L },
            ioReadBytes = sumOf { it.ioReadBytes ?: 0L },
            ioWriteBytes = sumOf { it.ioWriteBytes ?: 0L },
            refreshedAt = refreshedAt
        )
    }

    private fun ManagedTerminalRecord.toTrackedRuntimeEntry(): TerminalRuntimeEntry {
        return TerminalRuntimeEntry(
            sessionId = id,
            spaceId = spaceId,
            title = title,
            kind = kind,
            status = status,
            createdAt = createdAt,
            lastAttachedAt = lastAttachedAt,
            lastStartedAt = lastStartedAt,
            lastExitedAt = lastExitedAt,
            lastPid = lastPid,
            lastExitCode = lastExitCode,
            sourceAgentRuntimeId = sourceAgentRuntimeId,
            startupCommand = startupCommand,
            transcriptPath = "",
            isActive = false
        )
    }

    private fun String?.extractTrackedCommandHint(): String? {
        val normalized = this
            ?.trim()
            ?.removePrefix("exec ")
            ?.trim()
            .orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        val firstToken = normalized
            .split(Regex("\\s+"), limit = 2)
            .firstOrNull()
            ?.trim('"', '\'')
            .orEmpty()
        if (firstToken.isBlank()) {
            return null
        }
        val hint = firstToken.substringAfterLast('/').lowercase()
        return hint.takeIf { it.isNotBlank() }
    }

    internal fun parseContainerPsLineForTesting(line: String): ContainerProcessRecord? {
        return parsePsProcessLine(line)?.toProcessRecord(
            linkedTerminal = null,
            linkedRuntime = null
        )
    }

    private fun buildProcessActionLogFile(context: Context): File {
        val logsDir = WorkSurfaceRuntimeBridge.getLogsDir(context)
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, PROCESS_ACTION_LOG_FILE)
    }

    private fun writeProcessActionLog(context: Context, content: String) {
        buildProcessActionLogFile(context).appendText(content)
    }

    private data class ShellCommandResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean = false
    )

    private val CONTAINER_VISIBLE_COMMANDS = setOf(
        "bash",
        "sh",
        "proot",
        "tmux",
        "node",
        "npm",
        "python",
        "python3",
        "openclaw",
        "git",
        "apt",
        "apt-get",
        "dpkg",
        "uv",
        "pip",
        "pipx",
        "claude",
        "codex"
    )

    private val PS_PROCESS_REGEX =
        Regex("""^(\d+)\s+(\d+)(?:\s+(\d+)\s+(\d+))?\s+(\S+)\s+(\S+)\s*(.*)$""")
}
