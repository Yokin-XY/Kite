package com.kftest.app.foundation.terminal

import android.content.Context
import com.kftest.app.foundation.bootstrap.KFApplication
import com.kftest.app.foundation.capability.CapabilityCallerType
import com.kftest.app.foundation.capability.CapabilityDomain
import com.kftest.app.foundation.capability.CapabilityGate
import com.kftest.app.foundation.capability.CapabilityOutputLevel
import com.kftest.app.foundation.capability.CapabilityRequest
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.runtime.ContainerProcessStore
import com.kftest.app.foundation.runtime.HostStopAuditor
import com.kftest.app.foundation.runtime.HostProcessTerminator
import com.kftest.app.foundation.runtime.ProcessExitSemantics
import com.kftest.app.foundation.runtime.RuntimeFrameCoordinator
import com.kftest.app.foundation.runtime.RuntimeStorageGuard
import com.kftest.app.foundation.workspace.AgentLaunchMode
import com.kftest.app.foundation.workspace.AgentRuntimeRecord
import com.kftest.app.foundation.workspace.AgentRuntimeStatus
import com.kftest.app.foundation.workspace.KFWorkspaceManager
import com.kftest.app.foundation.workspace.ManagedTerminalRecord
import com.kftest.app.foundation.workspace.ManagedTerminalStatus
import com.kftest.app.foundation.workspace.SpaceRecord
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kftest.app.foundation.workspace.isArchivedRecord
import com.kftest.app.foundation.workspace.isLiveProcessStatus
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 工作面层对象，主责是把“空间里的多终端会话”稳定跑起来。
 *
 * 页面层只负责展示和手势，建房层只提供底层容器配置；
 * 会话创建、切换、关闭、转录镜像和 ready 探测都应继续收口在这里。
 */
class TerminalSessionController(
    private val appContext: Context,
    parentScope: CoroutineScope,
    private val uiCallbacks: TerminalSessionUiCallbacks
) : TerminalSessionClient {

    private data class SessionHolder(
        var record: ManagedTerminalRecord,
        val session: TerminalSession,
        val transcriptMirrorFile: File,
        var lastTranscriptSnapshot: String = "",
        var lastTerminalOutputAuditAt: Long = 0L,
        var lastTranscriptMirrorAuditAt: Long = 0L,
        var transcriptMirrorDirty: Boolean = false,
        val pendingInput: ArrayDeque<String> = ArrayDeque(),
        var firstPendingInputAt: Long? = null,
        var probeStartedAt: Long? = null,
        var probeJob: Job? = null,
        var transcriptMirrorJob: Job? = null
    )

    companion object {
        private const val LOG_TAG = "TerminalController"
        private const val TERMINAL_ACTION_LOG_FILE = "terminal-actions.log"
        private const val DEFAULT_COLUMNS = 120
        private const val DEFAULT_ROWS = 36
        private const val DEFAULT_CELL_WIDTH = 9
        private const val DEFAULT_CELL_HEIGHT = 18
        private const val TRANSCRIPT_ROWS = 4000
        private const val STARTUP_PROBE_ATTEMPTS = 40
        private const val STARTUP_PROBE_DELAY_MS = 150L
        private const val TERMINAL_OUTPUT_AUDIT_WINDOW_MS = 1000L
        private const val TRANSCRIPT_MIRROR_AUDIT_WINDOW_MS = 1500L
        private const val TRANSCRIPT_MIRROR_THROTTLE_MS = 500L
        private const val MAX_PENDING_INPUT_CHUNKS = 128
        private const val MAX_TERMINAL_WRITE_CHARS = 2048
        private const val INPUT_PREVIEW_CHARS = 200
        private const val MAX_TERMINAL_ACTION_LOG_BYTES = 1024L * 1024L
    }

    private val controllerScope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )
    private val sessionHolders = LinkedHashMap<String, SessionHolder>()
    private val transcriptMirrorRequested = AtomicLong(0L)
    private val transcriptMirrorFlushed = AtomicLong(0L)
    private val transcriptMirrorCoalesced = AtomicLong(0L)

    private var activeSessionId: String? = null

    @Volatile
    private var persistedSessionsReconciled = false

    private fun TerminalSession.validPid(): Int? = pid.takeIf { it > 0 }

    private fun SessionHolder.currentPid(): Int? {
        return session.validPid() ?: record.lastPid?.takeIf { it > 0 }
    }

    private fun SessionHolder.isSessionRunning(): Boolean {
        return session.isRunning && session.validPid() != null
    }

    private fun SessionHolder.hasEndedRecord(): Boolean {
        return record.isArchivedRecord()
    }

    fun getActiveSessionId(): String? = activeSessionId

    fun canOpenSessionDetail(record: ManagedTerminalRecord): Boolean {
        return sessionHolders.containsKey(record.id) || !record.isArchivedRecord()
    }

    fun getActiveTranscriptText(): String {
        val holder = sessionHolders[activeSessionId] ?: return ""
        return holder.session.emulator?.screen?.transcriptText
            ?: holder.lastTranscriptSnapshot
    }

    fun buildOutputBackpressureDebugSummary(): String {
        return "terminal-backpressure " +
            "transcriptMirrorRequested=${transcriptMirrorRequested.get()} " +
            "transcriptMirrorFlushed=${transcriptMirrorFlushed.get()} " +
            "transcriptMirrorCoalesced=${transcriptMirrorCoalesced.get()}"
    }

    fun flushTranscriptMirrors(reason: String? = null) {
        controllerScope.launch {
            sessionHolders.values.forEach { holder ->
                flushTranscriptMirror(holder, force = true)
            }
            reason?.let {
                Logger.i(LOG_TAG, "$it ${buildOutputBackpressureDebugSummary()}")
            }
        }
    }

    fun reattachActiveSession() {
        val holder = sessionHolders[activeSessionId] ?: return
        controllerScope.launch {
            flushTranscriptMirror(holder, force = true)
        }
        holder.session.updateTerminalSessionClient(this)
        uiCallbacks.attachSession(holder.session)
        TerminalRuntimeRegistry.markActive(holder.record.id)
        syncRuntimeEntry(holder)
        uiCallbacks.onManagedSessionsChanged()
        uiCallbacks.refreshTerminalView()
        if (holder.hasEndedRecord()) {
            uiCallbacks.updateCursorState(false)
        } else if (holder.isSessionRunning()) {
            uiCallbacks.updateCursorState(true)
        } else {
            uiCallbacks.updateCursorState(false)
        }
    }

    fun refreshManagedSessionsSnapshot() {
        controllerScope.launch {
            val currentSpace = ensureSpaceRecord()
            val persisted = loadPersistedSpaceSessions(currentSpace.id)

            sessionHolders.values.forEach { holder ->
                flushTranscriptMirror(holder, force = true)
                if (!holder.hasEndedRecord() && holder.isSessionRunning()) {
                    val runningRecord = if (holder.record.status == ManagedTerminalStatus.RUNNING) {
                        holder.record
                    } else {
                        holder.record.copy(
                            status = ManagedTerminalStatus.RUNNING,
                            lastStartedAt = holder.record.lastStartedAt ?: System.currentTimeMillis()
                        )
                    }
                    holder.record = runningRecord
                    syncRuntimeEntry(holder)
                } else {
                    syncRuntimeEntry(holder)
                }
            }

            val merged = persisted.map { persistedRecord ->
                sessionHolders[persistedRecord.id]?.record ?: persistedRecord
            }
            val transcriptDir = buildTranscriptMirrorFile("snapshot").parentFile
                ?: File(
                    WorkSurfaceRuntimeBridge.getLogsDir(appContext),
                    "terminal-transcripts"
                ).also { if (!it.exists()) it.mkdirs() }
            TerminalRuntimeRegistry.replaceAll(
                records = merged,
                transcriptDir = transcriptDir,
                currentViewedSessionId = activeSessionId ?: currentSpace.currentTerminalSessionId
            )
            uiCallbacks.onManagedSessionsChanged()
        }
    }

    fun isActiveSession(session: TerminalSession): Boolean {
        return sessionHolders[activeSessionId]?.session == session
    }

    fun prepareAndStartContainer(resetContainer: Boolean, forceRestart: Boolean) {
        controllerScope.launch {
            KFApplication.markLaunchStage(
                LOG_TAG,
                "prepareAndStartContainer 开始 reset=$resetContainer forceRestart=$forceRestart"
            )
            val actionMessage = when {
                resetContainer -> "正在重建容器并恢复终端会话。"
                forceRestart -> "正在重连终端会话。"
                else -> "正在准备容器和终端。"
            }
            uiCallbacks.showSessionNote(actionMessage)

            try {
                if (resetContainer || forceRestart) {
                    stopAllSessionsInternal("容器会话准备重建。")
                }

                val space = withContext(Dispatchers.IO) {
                    if (resetContainer) {
                        WorkSurfaceRuntimeBridge.resetDefaultContainer(appContext, wipeWorkspace = false)
                    } else {
                        WorkSurfaceRuntimeBridge.ensureDefaultContainer(appContext)
                    }
                    KFWorkspaceManager.ensureDefaultSpace(appContext)
                }
                KFApplication.markLaunchStage(LOG_TAG, "默认空间与容器已就绪")

                val record = withContext(Dispatchers.IO) {
                    reconcilePersistedSessionsIfNeeded()
                    resolveCurrentTerminalRecord(space)
                }
                KFApplication.markLaunchStage(LOG_TAG, "当前终端记录已解析: ${record.id}")

                switchToRecord(
                    record = record,
                    note = when {
                        resetContainer -> "容器已重建，已切回 ${record.title}。"
                        forceRestart -> "终端已重连，当前会话：${record.title}。"
                        record.status == ManagedTerminalStatus.FROZEN ->
                            "已恢复 ${record.title}，当前会话可继续使用。"
                        else -> "容器已就绪，当前会话：${record.title}。"
                    }
                )
                KFApplication.markLaunchStage(LOG_TAG, "switchToRecord 已完成: ${record.id}")
            } catch (error: Exception) {
                Logger.e(LOG_TAG, "准备容器失败: ${error.message}")
                WorkSurfaceRuntimeBridge.markContainerError(appContext, error.message ?: "终端准备失败")
                uiCallbacks.showSessionNote("容器或终端准备失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun createAndSwitchShellSession() {
        controllerScope.launch {
            try {
                val space = ensureSpaceRecord()
                val record = withContext(Dispatchers.IO) {
                    KFWorkspaceManager.createShellSession(appContext, space.id)
                }
                uiCallbacks.onManagedSessionsChanged()
                switchToRecord(record, "已创建 ${record.title}。")
            } catch (error: Exception) {
                Logger.e(LOG_TAG, "创建终端会话失败: ${error.message}")
                uiCallbacks.showSessionNote("创建终端失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun openPrimaryShellEntry() {
        controllerScope.launch {
            try {
                val space = ensureSpaceRecord()
                val record = withContext(Dispatchers.IO) {
                    KFWorkspaceManager.ensurePrimaryShellSession(appContext, space.id)
                }
                switchToRecord(record, "已进入 ${record.title}。")
            } catch (error: Exception) {
                Logger.e(LOG_TAG, "打开主终端入口失败: ${error.message}")
                uiCallbacks.showSessionNote("打开主终端失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun createAndLaunchAgentSession(agentRuntime: AgentRuntimeRecord) {
        controllerScope.launch {
            launchAgentRuntime(agentRuntime)
        }
    }

    fun launchAgentSession(runtimeId: String) {
        controllerScope.launch {
            val agentRuntime = withContext(Dispatchers.IO) {
                KFWorkspaceManager.getAgentRuntime(appContext, runtimeId)
            }
            if (agentRuntime == null) {
                uiCallbacks.showSessionNote("未找到要启动的智能体入口。")
                return@launch
            }
            launchAgentRuntime(agentRuntime)
        }
    }

    fun switchToSession(sessionId: String) {
        controllerScope.launch {
            try {
                val record = withContext(Dispatchers.IO) {
                    KFWorkspaceManager.getTerminalSession(appContext, sessionId)
                }

                if (record == null) {
                    uiCallbacks.showSessionNote("未找到要切换的终端会话。")
                    return@launch
                }

                if (!canOpenSessionDetail(record)) {
                    uiCallbacks.showSessionNote("${record.title} 已结束，当前版本先保留状态记录，稍后再补历史 transcript 回放。")
                    return@launch
                }

                val note = if (record.status == ManagedTerminalStatus.FROZEN) {
                    "已恢复 ${record.title}，可以继续使用。"
                } else {
                    "已切换到 ${record.title}。"
                }

                switchToRecord(record, note)
            } catch (error: Exception) {
                Logger.e(LOG_TAG, "切换终端会话失败: ${error.message}")
                uiCallbacks.showSessionNote("切换终端失败：${error.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 显式结束当前会话。
     *
     * 注意：
     * 1. 这里只有“结束会话”的语义，不包含“删除记录”。
     * 2. 返回列表、切 Tab、页面销毁、应用暂时退到后台，都不等于结束终端。
     */
    fun closeActiveSession() {
        controllerScope.launch {
            val targetSessionId = activeSessionId
            if (targetSessionId.isNullOrBlank()) {
                uiCallbacks.showSessionNote("当前没有可关闭的终端会话。")
                return@launch
            }

            endSessionInternal(targetSessionId)
        }
    }

    fun endSession(sessionId: String) {
        controllerScope.launch {
            endSessionInternal(sessionId)
        }
    }

    fun removeActiveSession() {
        controllerScope.launch {
            val targetSessionId = activeSessionId
            if (targetSessionId.isNullOrBlank()) {
                uiCallbacks.showSessionNote("当前没有可删除的终端会话。")
                return@launch
            }

            removeSessionInternal(targetSessionId)
        }
    }

    fun removeSession(sessionId: String) {
        controllerScope.launch {
            removeSessionInternal(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        controllerScope.launch {
            try {
                val targetRecord = sessionHolders[sessionId]?.record ?: withContext(Dispatchers.IO) {
                    KFWorkspaceManager.getTerminalSession(appContext, sessionId)
                }

                if (targetRecord == null) {
                    uiCallbacks.showSessionNote("目标终端记录不存在，无法删除。")
                    return@launch
                }

                if (!targetRecord.isArchivedRecord()) {
                    uiCallbacks.showSessionNote("${targetRecord.title} 仍在运行，请先结束会话，再删除记录。")
                    return@launch
                }

                val space = ensureSpaceRecord()
                if (activeSessionId == sessionId) {
                    val fallback = withContext(Dispatchers.IO) {
                        KFWorkspaceManager.listTerminalSessions(appContext, space.id)
                            .firstOrNull { it.id != sessionId && !it.isArchivedRecord() }
                    }
                    if (fallback != null) {
                        switchToRecord(
                            record = fallback,
                            note = "已切到 ${fallback.title}，正在删除 ${targetRecord.title}。"
                        )
                        delay(200L)
                    } else {
                        activeSessionId = null
                        withContext(Dispatchers.IO) {
                            KFWorkspaceManager.setCurrentTerminalSession(appContext, space.id, null)
                        }
                        TerminalRuntimeRegistry.markActive(null)
                    }
                }

                sessionHolders.remove(sessionId)?.probeJob?.cancel()
                withContext(Dispatchers.IO) {
                    KFWorkspaceManager.deleteTerminalSession(appContext, sessionId)
                }
                buildTranscriptMirrorFile(sessionId).takeIf { it.exists() }?.delete()
                TerminalRuntimeRegistry.remove(sessionId)
                uiCallbacks.onManagedSessionsChanged()
                uiCallbacks.showSessionNote("${targetRecord.title} 已删除。")
            } catch (error: Exception) {
                Logger.e(LOG_TAG, "删除终端会话失败: ${error.message}")
                uiCallbacks.showSessionNote("删除终端失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun sendCommand(command: String) {
        sendCommandToSession(null, command)
    }

    fun runCommandInPrimaryShell(command: String, note: String? = null) {
        if (command.isBlank()) {
            return
        }

        controllerScope.launch {
            try {
                val space = ensureSpaceRecord()
                val record = withContext(Dispatchers.IO) {
                    KFWorkspaceManager.ensurePrimaryShellSession(appContext, space.id)
                }
                switchToRecord(record, note ?: "已进入 ${record.title}。")
                sendCommandToSession(record.id, command)
            } catch (error: Exception) {
                Logger.e(LOG_TAG, "主终端快捷命令执行失败: ${error.message}")
                uiCallbacks.showSessionNote("主终端快捷命令执行失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun sendCommandToSession(sessionId: String?, command: String) {
        if (command.isBlank()) {
            return
        }

        controllerScope.launch {
            val holder = resolveWritableSessionHolder(sessionId) ?: return@launch
            val normalizedCommand = if (command.endsWith("\n") || command.endsWith("\r")) {
                command
            } else {
                "$command\r"
            }
            writeOrQueueInput(holder, normalizeTerminalLineEndings(normalizedCommand))
        }
    }

    fun writeRawInput(input: String) {
        if (input.isEmpty()) {
            return
        }

        val holder = sessionHolders[activeSessionId]
        if (holder == null) {
            uiCallbacks.showSessionNote("当前没有可写入的终端会话。")
            return
        }

        writeOrQueueInput(holder, input)
    }

    fun writePastedInput(input: String) {
        if (input.isEmpty()) {
            return
        }

        val holder = sessionHolders[activeSessionId]
        if (holder == null) {
            uiCallbacks.showSessionNote("当前没有可写入的终端会话。")
            return
        }

        val normalizedInput = normalizeTerminalLineEndings(input)
        Logger.i(
            LOG_TAG,
            "Terminal paste input: session=${holder.record.id} length=${input.length} normalizedLength=${normalizedInput.length} " +
                "hasLf=${input.contains('\n')} hasCr=${input.contains('\r')} chunks=${chunkCount(normalizedInput)} " +
                "preview=${input.escapedPreview(INPUT_PREVIEW_CHARS)}"
        )
        writeOrQueueInput(holder, normalizedInput)
    }

    fun writePastedInputToDefault(input: String) {
        if (input.isEmpty()) {
            return
        }

        controllerScope.launch {
            val holder = resolveWritableSessionHolder(null) ?: return@launch
            val normalizedInput = normalizeTerminalLineEndings(input)
            Logger.i(
                LOG_TAG,
                "Terminal paste input: session=${holder.record.id} length=${input.length} normalizedLength=${normalizedInput.length} " +
                    "hasLf=${input.contains('\n')} hasCr=${input.contains('\r')} chunks=${chunkCount(normalizedInput)} " +
                    "preview=${input.escapedPreview(INPUT_PREVIEW_CHARS)}"
            )
            writeOrQueueInput(holder, normalizedInput)
        }
    }

    /**
     * 页面销毁时同步回收所有会话，避免 viewLifecycleScope 被取消后清理逻辑半途而废。
     */
    fun stopCurrentSession(reason: String) {
        stopAllSessionsInternal(reason)
        controllerScope.cancel(reason)
    }

    fun handleEmulatorReady() {
        val holder = sessionHolders[activeSessionId] ?: return
        Logger.i(LOG_TAG, "TerminalView 已就绪，尝试提升会话状态：${holder.record.id}")
        if (holder.hasEndedRecord()) {
            syncRuntimeEntry(holder)
            uiCallbacks.updateCursorState(false)
            uiCallbacks.refreshTerminalView()
            uiCallbacks.showSessionNote("${holder.record.title} 已结束，输出记录已保留。")
            return
        }
        ensureSessionInitialized(holder)
        if (holder.isSessionRunning()) {
            promoteHolderToRunning(holder, showNote = false)
        } else {
            launchActivationProbe(holder.record.id)
        }
        uiCallbacks.refreshTerminalView()
    }

    private suspend fun ensureSpaceRecord(): SpaceRecord {
        return withContext(Dispatchers.IO) {
            KFWorkspaceManager.getCurrentSpace(appContext)
                ?: KFWorkspaceManager.ensureDefaultSpace(appContext)
        }
    }

    private suspend fun loadPersistedSpaceSessions(spaceId: String): List<ManagedTerminalRecord> {
        return withContext(Dispatchers.IO) {
            reconcilePersistedSessionsIfNeeded()
            KFWorkspaceManager.listTerminalSessions(appContext, spaceId)
        }
    }

    private fun reconcilePersistedSessionsIfNeeded() {
        if (persistedSessionsReconciled || sessionHolders.isNotEmpty()) {
            return
        }
        KFWorkspaceManager.freezeRecoverableTerminalSessions(appContext)
        persistedSessionsReconciled = true
    }

    private suspend fun launchAgentRuntime(agentRuntime: AgentRuntimeRecord) {
        try {
            Logger.i(
                LOG_TAG,
                "准备启动智能体: ${agentRuntime.displayName}, mode=${agentRuntime.launchMode.name}, command=${agentRuntime.launchCommand}"
            )
            if (!ensureAgentCommandAvailable(agentRuntime)) {
                return
            }

            when (agentRuntime.launchMode) {
                AgentLaunchMode.REUSE_CURRENT -> {
                    launchAgentInCurrentSession(agentRuntime)
                }

                AgentLaunchMode.NEW_MANAGED_SESSION -> {
                    launchAgentInManagedSession(agentRuntime)
                }

                AgentLaunchMode.BACKGROUND_SERVICE -> {
                    withContext(Dispatchers.IO) {
                        KFWorkspaceManager.updateAgentRuntimeStatus(
                            context = appContext,
                            runtimeId = agentRuntime.id,
                            status = AgentRuntimeStatus.REGISTERED,
                            lastError = null
                        )
                    }
                    uiCallbacks.showSessionNote("${agentRuntime.displayName} 属于后台服务型入口，后续请从任务管理器启动。")
                }
            }
        } catch (error: Exception) {
            Logger.e(LOG_TAG, "启动智能体失败: ${error.message}")
            withContext(Dispatchers.IO) {
                KFWorkspaceManager.updateAgentRuntimeStatus(
                    context = appContext,
                    runtimeId = agentRuntime.id,
                    status = AgentRuntimeStatus.ERROR,
                    lastError = error.message ?: "未知错误"
                )
            }
            uiCallbacks.showSessionNote("启动 ${agentRuntime.displayName} 失败：${error.message ?: "未知错误"}")
        }
    }

    private suspend fun ensureAgentCommandAvailable(agentRuntime: AgentRuntimeRecord): Boolean {
        if (WorkSurfaceRuntimeBridge.isCommandAvailable(appContext, agentRuntime.launchCommand)) {
            return true
        }

        withContext(Dispatchers.IO) {
            KFWorkspaceManager.updateAgentRuntimeStatus(
                context = appContext,
                runtimeId = agentRuntime.id,
                status = AgentRuntimeStatus.ERROR,
                lastError = "${agentRuntime.displayName} 未安装到当前空间"
            )
        }
        uiCallbacks.showSessionNote("${agentRuntime.displayName} 还没有安装到当前空间，先在主终端里按正常 Linux 方式安装。")
        return false
    }

    private suspend fun launchAgentInCurrentSession(agentRuntime: AgentRuntimeRecord) {
        withContext(Dispatchers.IO) {
            KFWorkspaceManager.updateAgentRuntimeStatus(
                context = appContext,
                runtimeId = agentRuntime.id,
                status = AgentRuntimeStatus.STARTING,
                lastError = null
            )
        }
        val space = ensureSpaceRecord()
        val record = withContext(Dispatchers.IO) {
            resolveCurrentTerminalRecord(space)
        }
        switchToRecord(record, "正在当前终端里启动 ${agentRuntime.displayName}。")
        sendCommand(agentRuntime.launchCommand)
        withContext(Dispatchers.IO) {
            KFWorkspaceManager.updateAgentRuntimeStatus(
                context = appContext,
                runtimeId = agentRuntime.id,
                status = AgentRuntimeStatus.RUNNING,
                lastError = null
            )
        }
    }

    private suspend fun launchAgentInManagedSession(agentRuntime: AgentRuntimeRecord) {
        withContext(Dispatchers.IO) {
            KFWorkspaceManager.updateAgentRuntimeStatus(
                context = appContext,
                runtimeId = agentRuntime.id,
                status = AgentRuntimeStatus.STARTING,
                lastError = null
            )
        }
        val space = ensureSpaceRecord()
        val record = withContext(Dispatchers.IO) {
            KFWorkspaceManager.createAgentConsoleSession(
                context = appContext,
                spaceId = space.id,
                agentDisplayName = agentRuntime.displayName,
                sourceAgentRuntimeId = agentRuntime.id,
                startupCommand = agentRuntime.launchCommand
            )
        }
        uiCallbacks.onManagedSessionsChanged()
        switchToRecord(
            record = record,
            note = "已为 ${agentRuntime.displayName} 创建独立终端，正在启动。"
        )
        Logger.i(LOG_TAG, "智能体独立终端已创建: ${record.id}, kind=${record.kind.name}, title=${record.title}")
        sendCommand(agentRuntime.launchCommand)
        withContext(Dispatchers.IO) {
            KFWorkspaceManager.updateAgentRuntimeStatus(
                context = appContext,
                runtimeId = agentRuntime.id,
                status = AgentRuntimeStatus.RUNNING,
                lastError = null
            )
        }
    }

    private fun resolveCurrentTerminalRecord(space: SpaceRecord): ManagedTerminalRecord {
        val currentRecord = KFWorkspaceManager.getCurrentTerminalSession(appContext, space.id)
        if (currentRecord != null && !currentRecord.isArchivedRecord()) {
            return currentRecord
        }

        return KFWorkspaceManager.listTerminalSessions(appContext, space.id)
            .firstOrNull { !it.isArchivedRecord() }
            ?: KFWorkspaceManager.createShellSession(appContext, space.id)
    }

    private suspend fun resolveWritableSessionHolder(targetSessionId: String?): SessionHolder? {
        val resolvedTargetId = targetSessionId?.trim().orEmpty()
        if (resolvedTargetId.isBlank()) {
            sessionHolders[activeSessionId]?.let { return it }
            val space = ensureSpaceRecord()
            val record = withContext(Dispatchers.IO) {
                resolveCurrentTerminalRecord(space)
            }
            switchToRecord(record, "已进入 ${record.title}。")
            return sessionHolders[record.id]
        }

        sessionHolders[resolvedTargetId]?.let { existing ->
            if (activeSessionId != resolvedTargetId) {
                switchToRecord(existing.record, "已切换到 ${existing.record.title}。")
            }
            return sessionHolders[resolvedTargetId]
        }

        val record = withContext(Dispatchers.IO) {
            KFWorkspaceManager.getTerminalSession(appContext, resolvedTargetId)
        }
        if (record == null) {
            uiCallbacks.showSessionNote("未找到要写入的终端会话。")
            return null
        }
        if (!canOpenSessionDetail(record)) {
            uiCallbacks.showSessionNote("${record.title} 已结束，当前不能继续写入。")
            return null
        }

        val note = if (record.status == ManagedTerminalStatus.FROZEN) {
            "已恢复 ${record.title}，可以继续使用。"
        } else {
            "已切换到 ${record.title}。"
        }
        switchToRecord(record, note)
        return sessionHolders[record.id]
    }

    private suspend fun switchToRecord(record: ManagedTerminalRecord, note: String) {
        val previousActiveHolder = sessionHolders[activeSessionId]
            ?.takeIf { it.record.id != record.id }
        previousActiveHolder?.let { holder ->
            flushTranscriptMirror(holder, force = true)
        }
        activeSessionId = record.id
        withContext(Dispatchers.IO) {
            KFWorkspaceManager.setCurrentTerminalSession(appContext, record.spaceId, record.id)
        }
        TerminalRuntimeRegistry.markActive(record.id)
        uiCallbacks.onManagedSessionsChanged()

        val existingHolder = sessionHolders[record.id]
        if (existingHolder != null) {
            attachExistingHolder(existingHolder, note)
        } else {
            attachNewSession(record, note)
        }
    }

    private suspend fun attachExistingHolder(holder: SessionHolder, note: String) {
        if (!holder.isSessionRunning() &&
            !holder.hasEndedRecord() &&
            holder.record.lastStartedAt != null
        ) {
            Logger.i(LOG_TAG, "会话 ${holder.record.id} 已结束，重新创建真实终端。")
            sessionHolders.remove(holder.record.id)
            attachNewSession(holder.record, note)
            return
        }

        holder.session.updateTerminalSessionClient(this)
        activeSessionId = holder.record.id
        TerminalRuntimeRegistry.markActive(holder.record.id)
        uiCallbacks.attachSession(holder.session)
        uiCallbacks.refreshTerminalView()
        if (holder.hasEndedRecord()) {
            syncRuntimeEntry(holder)
            uiCallbacks.updateCursorState(false)
            uiCallbacks.showSessionNote("${holder.record.title} 已结束，输出记录已保留。")
            return
        }
        if (holder.isSessionRunning()) {
            updateSessionStatus(holder, ManagedTerminalStatus.ATTACHED)
            ensureSessionInitialized(holder)
            promoteHolderToRunning(holder, showNote = false)
        } else {
            syncRuntimeEntry(holder)
            uiCallbacks.updateCursorState(false)
            if (holder.record.isArchivedRecord()) {
                uiCallbacks.showSessionNote("${holder.record.title} 已结束，输出记录已保留。")
                return
            }
            ensureSessionInitialized(holder)
            launchActivationProbe(holder.record.id)
        }

        uiCallbacks.showSessionNote(note)
    }

    private suspend fun attachNewSession(record: ManagedTerminalRecord, note: String) {
        val storage = RuntimeStorageGuard.canStartNewRuntime(appContext, "terminal_attach:${record.id}")
        if (storage.isCritical) {
            updateDetachedSessionStatus(record, ManagedTerminalStatus.FAILED, lastExitCode = null)
            TerminalRuntimeRegistry.remove(record.id)
            uiCallbacks.onManagedSessionsChanged()
            uiCallbacks.updateCursorState(false)
            uiCallbacks.showSessionNote(storage.userMessage())
            Logger.e(LOG_TAG, "Terminal start blocked by storage pressure: session=${record.id} usable=${storage.usableBytes}")
            return
        }

        val recoveryPolicy = when (record.status) {
            ManagedTerminalStatus.FROZEN -> "reuse-session-id-from-frozen-record"
            ManagedTerminalStatus.REGISTERED -> "reuse-registered-session-id"
            else -> "attach-existing-record"
        }
        Logger.i(
            LOG_TAG,
            "Attaching terminal session: id=${record.id}, previousStatus=${record.status.name}, policy=$recoveryPolicy"
        )
        writeTerminalActionLog(
            "== terminal attach sessionId=${record.id} previousStatus=${record.status.name} policy=$recoveryPolicy ==\n"
        )
        WorkSurfaceRuntimeBridge.markContainerStarting(appContext)
        val config = withContext(Dispatchers.IO) {
            // 终端会话属于工作面动作；真正的容器 launch 配置统一经 bridge 向建房层索取。
            WorkSurfaceRuntimeBridge.buildTerminalLaunchConfig(appContext)
        }

        val session = TerminalSession(
            config.executablePath,
            config.workingDirectory,
            config.args,
            config.env,
            TRANSCRIPT_ROWS,
            this
        ).apply {
            mSessionName = record.title
        }

        val holder = SessionHolder(
            record = record,
            session = session,
            transcriptMirrorFile = buildTranscriptMirrorFile(record.id)
        )
        sessionHolders[record.id] = holder
        activeSessionId = record.id
        TerminalRuntimeRegistry.markActive(record.id)

        ensureSessionInitialized(holder)
        uiCallbacks.attachSession(session)
        uiCallbacks.refreshTerminalView()
        updateSessionStatus(holder, ManagedTerminalStatus.ATTACHED)

        if (holder.isSessionRunning()) {
            promoteHolderToRunning(holder, showNote = false)
        } else {
            launchActivationProbe(record.id)
        }

        uiCallbacks.showSessionNote(note)
        Logger.i(LOG_TAG, "已创建并挂接终端会话: ${record.id}, pid=${session.pid}")
    }

    private fun ensureSessionInitialized(holder: SessionHolder) {
        if (holder.isSessionRunning()) {
            return
        }

        runCatching {
            holder.session.updateSize(
                DEFAULT_COLUMNS,
                DEFAULT_ROWS,
                DEFAULT_CELL_WIDTH,
                DEFAULT_CELL_HEIGHT
            )
        }.onFailure { error ->
            Logger.e(LOG_TAG, "初始化终端会话失败: ${holder.record.id}, ${error.message}")
            WorkSurfaceRuntimeBridge.markContainerError(appContext, error.message ?: "终端初始化失败")
        }
    }

    private fun launchActivationProbe(sessionId: String) {
        val holder = sessionHolders[sessionId] ?: return
        if (holder.probeJob?.isActive == true) {
            syncRuntimeEntry(holder)
            return
        }
        holder.probeJob?.cancel()
        holder.probeStartedAt = System.currentTimeMillis()
        syncRuntimeEntry(holder)
        Logger.i(
            LOG_TAG,
            "Starting terminal activation probe: session=$sessionId status=${holder.record.status.name} pending=${holder.pendingInput.size}"
        )
        writeTerminalActionLog(
            "== terminal probe start sessionId=$sessionId status=${holder.record.status.name} pending=${holder.pendingInput.size} ==\n"
        )
        holder.probeJob = controllerScope.launch {
            repeat(STARTUP_PROBE_ATTEMPTS) { attempt ->
                val currentHolder = sessionHolders[sessionId] ?: return@launch
                if (currentHolder.isSessionRunning()) {
                    promoteHolderToRunning(currentHolder, showNote = attempt > 0)
                    return@launch
                }
                delay(STARTUP_PROBE_DELAY_MS)
            }

            if (activeSessionId == sessionId) {
                uiCallbacks.showSessionNote("终端已经挂接，但还没进入可执行状态。")
            }
            val currentHolder = sessionHolders[sessionId] ?: return@launch
            Logger.i(
                LOG_TAG,
                "Terminal activation probe unresolved: session=$sessionId pending=${currentHolder.pendingInput.size}"
            )
            writeTerminalActionLog(
                "== terminal probe unresolved sessionId=$sessionId pending=${currentHolder.pendingInput.size} elapsedMs=${System.currentTimeMillis() - (currentHolder.probeStartedAt ?: System.currentTimeMillis())} ==\n"
            )
            syncRuntimeEntry(currentHolder)
        }
    }

    private fun promoteHolderToRunning(holder: SessionHolder, showNote: Boolean = true) {
        val livePid = holder.session.validPid()
        if (!holder.isSessionRunning() || livePid == null) {
            return
        }

        holder.probeJob?.cancel()
        holder.probeStartedAt = null
        val flushedInputCount = holder.pendingInput.size
        updateSessionStatus(
            holder,
            ManagedTerminalStatus.RUNNING,
            lastStartedAt = System.currentTimeMillis(),
            lastPid = livePid
        )
        flushPendingInput(holder)
        if (flushedInputCount > 0) {
            Logger.i(
                LOG_TAG,
                "Flushed queued terminal input after ready: session=${holder.record.id}, chunks=$flushedInputCount"
            )
            writeTerminalActionLog(
                "== terminal queued input flushed sessionId=${holder.record.id} chunks=$flushedInputCount ==\n"
            )
        }
        requestTranscriptMirror(holder)
        WorkSurfaceRuntimeBridge.markContainerRunning(appContext, livePid)
        RuntimeFrameCoordinator.refreshProcessSnapshot(
            context = appContext,
            reason = "terminal-running:${holder.record.id}"
        )
        RuntimeFrameCoordinator.scheduleProcessRefreshes(
            context = appContext,
            delaysMs = listOf(1400L),
            reason = "terminal-running:${holder.record.id}"
        )
        uiCallbacks.refreshTerminalView()

        if (showNote && activeSessionId == holder.record.id) {
            uiCallbacks.showSessionNote("${holder.record.title} 已进入运行态，可以直接交互。")
        }
    }

    private fun writeOrQueueInput(holder: SessionHolder, input: String) {
        if (holder.hasEndedRecord()) {
            if (activeSessionId == holder.record.id) {
                uiCallbacks.showSessionNote("${holder.record.title} 已结束，请切换到其他终端或新建会话。")
            }
            return
        }
        if (holder.isSessionRunning()) {
            writeInputChunks(holder, input)
            requestTranscriptMirror(holder)
            return
        }

        if (holder.pendingInput.size >= MAX_PENDING_INPUT_CHUNKS) {
            holder.pendingInput.removeFirst()
            Logger.i(
                LOG_TAG,
                "Dropped oldest queued terminal input chunk: session=${holder.record.id}, max=$MAX_PENDING_INPUT_CHUNKS"
            )
            writeTerminalActionLog(
                "== terminal queued input dropped-oldest sessionId=${holder.record.id} max=$MAX_PENDING_INPUT_CHUNKS ==\n"
            )
        }
        if (holder.pendingInput.isEmpty()) {
            holder.firstPendingInputAt = System.currentTimeMillis()
        }
        holder.pendingInput.addLast(input)
        syncRuntimeEntry(holder)
        launchActivationProbe(holder.record.id)
        if (activeSessionId == holder.record.id) {
            uiCallbacks.showSessionNote("${holder.record.title} 正在启动，输入已排队。")
        }
    }

    private fun flushPendingInput(holder: SessionHolder) {
        while (holder.pendingInput.isNotEmpty() && holder.isSessionRunning()) {
            writeInputChunks(holder, holder.pendingInput.removeFirst())
        }
        if (holder.pendingInput.isEmpty()) {
            holder.firstPendingInputAt = null
        }
        syncRuntimeEntry(holder)
    }

    private fun writeInputChunks(holder: SessionHolder, input: String) {
        if (input.length <= MAX_TERMINAL_WRITE_CHARS) {
            holder.session.write(input)
            return
        }

        var offset = 0
        while (offset < input.length && holder.isSessionRunning()) {
            val end = (offset + MAX_TERMINAL_WRITE_CHARS).coerceAtMost(input.length)
            holder.session.write(input.substring(offset, end))
            offset = end
        }
    }

    private fun normalizeTerminalLineEndings(input: String): String {
        return input
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\n', '\r')
    }

    private fun chunkCount(input: String): Int {
        return if (input.isEmpty()) {
            0
        } else {
            ((input.length - 1) / MAX_TERMINAL_WRITE_CHARS) + 1
        }
    }

    private fun String.escapedPreview(maxChars: Int): String {
        val preview = take(maxChars)
        val escaped = buildString {
            preview.forEach { char ->
                when (char) {
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.isISOControl()) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
        return if (length > maxChars) "$escaped..." else escaped
    }

    private fun updateSessionStatus(holder: SessionHolder, status: ManagedTerminalStatus) {
        updateSessionStatus(holder, status, null, null, null, null, null)
    }

    private fun updateSessionStatus(
        holder: SessionHolder,
        status: ManagedTerminalStatus,
        lastAttachedAt: Long? = null,
        lastStartedAt: Long? = null,
        lastExitedAt: Long? = null,
        lastPid: Int? = null,
        lastExitCode: Int? = null
    ) {
        val attachedAt = if (status.isLiveProcessStatus()) {
            lastAttachedAt ?: System.currentTimeMillis()
        } else {
            lastAttachedAt ?: holder.record.lastAttachedAt
        }

        holder.record = holder.record.copy(
            status = status,
            lastAttachedAt = attachedAt,
            lastStartedAt = lastStartedAt ?: holder.record.lastStartedAt,
            lastExitedAt = lastExitedAt ?: holder.record.lastExitedAt,
            lastPid = lastPid ?: holder.record.lastPid,
            lastExitCode = lastExitCode ?: holder.record.lastExitCode
        )
        sessionHolders[holder.record.id] = holder

        controllerScope.launch(Dispatchers.IO) {
            KFWorkspaceManager.updateTerminalSessionStatus(
                context = appContext,
                sessionId = holder.record.id,
                status = status,
                lastAttachedAt = attachedAt,
                lastStartedAt = holder.record.lastStartedAt,
                lastExitedAt = holder.record.lastExitedAt,
                lastPid = holder.record.lastPid,
                lastExitCode = holder.record.lastExitCode
            )
        }
        syncRuntimeEntry(holder)
        uiCallbacks.onManagedSessionsChanged()
    }

    private fun syncRuntimeEntry(holder: SessionHolder) {
        val hasAttachedSession = sessionHolders[holder.record.id] === holder && !holder.hasEndedRecord()
        TerminalRuntimeRegistry.upsert(
            record = holder.record,
            transcriptFile = holder.transcriptMirrorFile,
            isActive = activeSessionId == holder.record.id,
            hasAttachedSession = hasAttachedSession,
            pendingInputCount = if (hasAttachedSession) holder.pendingInput.size else 0
        )
    }

    private fun requestTranscriptMirror(holder: SessionHolder) {
        auditTerminalOutputCapability(
            actionName = "transcriptMirror",
            holder = holder,
            outputLevel = CapabilityOutputLevel.MEDIUM
        )
        transcriptMirrorRequested.incrementAndGet()
        holder.transcriptMirrorDirty = true
        if (holder.transcriptMirrorJob?.isActive == true) {
            transcriptMirrorCoalesced.incrementAndGet()
            return
        }
        holder.transcriptMirrorJob = controllerScope.launch {
            delay(TRANSCRIPT_MIRROR_THROTTLE_MS)
            flushTranscriptMirror(holder)
        }
    }

    private suspend fun flushTranscriptMirror(holder: SessionHolder, force: Boolean = false) {
        if (force) {
            holder.transcriptMirrorJob?.cancel()
        }
        holder.transcriptMirrorJob = null
        if (!force && !holder.transcriptMirrorDirty) {
            return
        }
        holder.transcriptMirrorDirty = false
        val snapshot = holder.session.emulator?.screen?.transcriptText ?: return
        if (snapshot == holder.lastTranscriptSnapshot) {
            return
        }

        holder.lastTranscriptSnapshot = snapshot
        transcriptMirrorFlushed.incrementAndGet()
        withContext(Dispatchers.IO) {
            RuntimeStorageGuard.safeWriteText(
                context = appContext,
                file = holder.transcriptMirrorFile,
                text = snapshot,
                reason = "terminal_transcript_mirror:${holder.record.id}"
            )
        }
    }

    private fun buildTranscriptMirrorFile(sessionId: String): File {
        val logsDir = WorkSurfaceRuntimeBridge.getLogsDir(appContext)
        val transcriptDir = File(logsDir, "terminal-transcripts")
        if (!transcriptDir.exists()) {
            transcriptDir.mkdirs()
        }
        return File(transcriptDir, "$sessionId.txt")
    }

    private fun buildTerminalActionLogFile(): File {
        val logsDir = WorkSurfaceRuntimeBridge.getLogsDir(appContext)
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, TERMINAL_ACTION_LOG_FILE)
    }

    private fun auditTerminalOutputCapability(
        actionName: String,
        holder: SessionHolder,
        outputLevel: CapabilityOutputLevel
    ) {
        val now = System.currentTimeMillis()
        val lastAuditAt = when (actionName) {
            "terminalOutput" -> holder.lastTerminalOutputAuditAt
            "transcriptMirror" -> holder.lastTranscriptMirrorAuditAt
            else -> 0L
        }
        val auditWindowMs = when (actionName) {
            "terminalOutput" -> TERMINAL_OUTPUT_AUDIT_WINDOW_MS
            "transcriptMirror" -> TRANSCRIPT_MIRROR_AUDIT_WINDOW_MS
            else -> 0L
        }
        if (auditWindowMs > 0L && now - lastAuditAt < auditWindowMs) {
            return
        }
        when (actionName) {
            "terminalOutput" -> holder.lastTerminalOutputAuditAt = now
            "transcriptMirror" -> holder.lastTranscriptMirrorAuditAt = now
        }
        CapabilityGate.evaluate(
            CapabilityRequest(
                callerName = "TerminalSessionController",
                callerType = CapabilityCallerType.LEGACY,
                actionName = actionName,
                capabilityDomains = setOf(CapabilityDomain.TERMINAL, CapabilityDomain.OUTPUT),
                requiresContainer = true,
                longRunning = false,
                expectedOutputLevel = outputLevel,
                concurrencyKey = "terminal:${holder.record.id}",
                sourcePath = "foundation/terminal/TerminalSessionController.kt",
                sourceModule = "terminal",
                legacyDirectCall = true
            )
        )
    }

    private fun writeTerminalActionLog(content: String) {
        RuntimeStorageGuard.safeAppendBounded(
            context = appContext,
            file = buildTerminalActionLogFile(),
            text = content,
            maxBytes = MAX_TERMINAL_ACTION_LOG_BYTES,
            reason = "terminal_action_log"
        )
    }

    private suspend fun updateDetachedSessionStatus(
        record: ManagedTerminalRecord,
        status: ManagedTerminalStatus,
        lastExitCode: Int?
    ) {
        withContext(Dispatchers.IO) {
            KFWorkspaceManager.updateTerminalSessionStatus(
                context = appContext,
                sessionId = record.id,
                status = status,
                lastAttachedAt = record.lastAttachedAt,
                lastStartedAt = record.lastStartedAt,
                lastExitedAt = System.currentTimeMillis(),
                lastPid = record.lastPid,
                lastExitCode = lastExitCode
            )
        }
    }

    private suspend fun endSessionInternal(targetSessionId: String) {
        try {
            val space = ensureSpaceRecord()
            val targetRecord = sessionHolders[targetSessionId]?.record ?: withContext(Dispatchers.IO) {
                KFWorkspaceManager.getTerminalSession(appContext, targetSessionId)
            }

            if (targetRecord == null) {
                uiCallbacks.showSessionNote("目标终端记录不存在，无法结束。")
                return
            }

            if (targetRecord.isArchivedRecord()) {
                uiCallbacks.showSessionNote("${targetRecord.title} 已经结束，无需重复操作。")
                return
            }

            val fallback = if (activeSessionId == targetSessionId) {
                withContext(Dispatchers.IO) {
                    KFWorkspaceManager.listTerminalSessions(appContext, space.id)
                        .firstOrNull { it.id != targetSessionId && !it.isArchivedRecord() }
                }
            } else {
                null
            }

            if (fallback != null) {
                switchToRecord(
                    record = fallback,
                    note = "已切到 ${fallback.title}，正在结束 ${targetRecord.title}。"
                )
                delay(200L)
            } else if (activeSessionId == targetSessionId) {
                activeSessionId = null
                withContext(Dispatchers.IO) {
                    KFWorkspaceManager.setCurrentTerminalSession(appContext, space.id, null)
                }
                TerminalRuntimeRegistry.markActive(null)
                uiCallbacks.onManagedSessionsChanged()
            }

            if (sessionHolders.containsKey(targetSessionId)) {
                stopManagedSession(targetSessionId, "用户结束终端会话。")
            } else {
                stopDetachedSessionRecord(targetRecord, "用户结束终端会话。")
            }
            uiCallbacks.onManagedSessionsChanged()
            uiCallbacks.showSessionNote("${targetRecord.title} 已结束，记录已保留。")
        } catch (error: Exception) {
            Logger.e(LOG_TAG, "结束终端会话失败: ${error.message}")
            uiCallbacks.showSessionNote("结束终端失败：${error.message ?: "未知错误"}")
        }
    }

    private suspend fun removeSessionInternal(targetSessionId: String) {
        try {
            val targetRecord = sessionHolders[targetSessionId]?.record ?: withContext(Dispatchers.IO) {
                KFWorkspaceManager.getTerminalSession(appContext, targetSessionId)
            }

            if (targetRecord == null) {
                uiCallbacks.showSessionNote("目标终端会话不存在，无法删除。")
                return
            }

            if (!targetRecord.isArchivedRecord()) {
                endSessionInternal(targetSessionId)
            }

            val archivedRecord = sessionHolders[targetSessionId]?.record ?: withContext(Dispatchers.IO) {
                KFWorkspaceManager.getTerminalSession(appContext, targetSessionId)
            }

            if (archivedRecord == null) {
                uiCallbacks.showSessionNote("目标终端会话不存在，无法删除。")
                return
            }

            if (!archivedRecord.isArchivedRecord()) {
                uiCallbacks.showSessionNote("${archivedRecord.title} 还没有完全结束，暂时无法删除。")
                return
            }

            sessionHolders.remove(targetSessionId)?.probeJob?.cancel()
            withContext(Dispatchers.IO) {
                KFWorkspaceManager.deleteTerminalSession(appContext, targetSessionId)
            }
            buildTranscriptMirrorFile(targetSessionId).takeIf { it.exists() }?.delete()
            TerminalRuntimeRegistry.remove(targetSessionId)
            uiCallbacks.onManagedSessionsChanged()
            uiCallbacks.showSessionNote("${archivedRecord.title} 已删除。")
        } catch (error: Exception) {
            Logger.e(LOG_TAG, "删除终端会话失败: ${error.message}")
            uiCallbacks.showSessionNote("删除终端失败：${error.message ?: "未知错误"}")
        }
    }

    private fun stopManagedSession(sessionId: String, reason: String) {
        val holder = sessionHolders.remove(sessionId) ?: return
        holder.probeJob?.cancel()
        controllerScope.launch {
            flushTranscriptMirror(holder, force = true)
        }

        val pid = holder.currentPid() ?: 0
        writeTerminalActionLog(
            buildString {
                append("== 收到终端停止请求 ==\n")
                append("sessionId=$sessionId title=${holder.record.title} pid=$pid reason=$reason\n")
                holder.record.startupCommand
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append("startupCommand=$it\n") }
            }
        )
        if (holder.isSessionRunning() || pid > 0) {
            Logger.i(LOG_TAG, "停止终端会话: $sessionId, pid=$pid, reason=$reason")
            val stopAuditSeed = HostStopAuditor.capture(pid, LOG_TAG)
            runCatching {
                holder.session.finishIfRunning()
            }.onFailure { error ->
                Logger.e(LOG_TAG, "停止终端会话失败: ${error.message}")
            }
            if (pid > 0) {
                controllerScope.launch {
                    val outcome = HostProcessTerminator.terminateTerminalProcessGroup(pid) { message ->
                        Logger.i(LOG_TAG, "终端停止补偿: session=$sessionId pid=$pid $message")
                    }
                    Logger.i(
                        LOG_TAG,
                        "终端停止补偿完成: session=$sessionId pid=$pid exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill}"
                    )
                    writeTerminalActionLog(
                        "== 终端停止补偿 session=$sessionId pid=$pid exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill} ==\n"
                    )
                    HostStopAuditor.audit(stopAuditSeed, LOG_TAG)?.let { report ->
                        Logger.i(
                            LOG_TAG,
                            "终端停止诊断: session=$sessionId ${report.toCompactSummary()}"
                        )
                        writeTerminalActionLog(report.toLogBlock("终端停止诊断 $sessionId"))
                    }
                    RuntimeFrameCoordinator.refreshProcessSnapshot(
                        context = appContext,
                        reason = "terminal-stop:$sessionId"
                    )
                    RuntimeFrameCoordinator.scheduleProcessRefreshes(
                        context = appContext,
                        delaysMs = listOf(600L),
                        reason = "terminal-stop:$sessionId"
                    )
                }
            }
        } else {
            Logger.i(LOG_TAG, "终端会话未真正启动，无需发送停止信号: $sessionId, pid=$pid")
            writeTerminalActionLog(
                "== 终端停止跳过 session=$sessionId pid=$pid reason=not-running ==\n"
            )
        }

        holder.pendingInput.clear()
        holder.firstPendingInputAt = null
        holder.probeStartedAt = null
        updateSessionStatus(
            holder,
            ManagedTerminalStatus.STOPPED,
            lastExitedAt = System.currentTimeMillis(),
            lastPid = pid.takeIf { it > 0 } ?: holder.record.lastPid,
            lastExitCode = holder.record.lastExitCode ?: holder.session.exitStatus
        )
        if (activeSessionId == sessionId) {
            activeSessionId = null
            TerminalRuntimeRegistry.markActive(null)
        }
        if (sessionHolders.values.none { it.isSessionRunning() }) {
            WorkSurfaceRuntimeBridge.markContainerStopped(appContext)
        }
    }

    private suspend fun stopDetachedSessionRecord(
        record: ManagedTerminalRecord,
        reason: String
    ) {
        val resolvedPid = resolveTerminalStopPid(record)
        writeTerminalActionLog(
            buildString {
                append("== 收到终端停止请求 ==\n")
                append("sessionId=${record.id} title=${record.title} pid=${resolvedPid ?: 0} reason=$reason detached=true\n")
                record.startupCommand
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append("startupCommand=$it\n") }
            }
        )

        val report = if (resolvedPid != null && resolvedPid > 0) {
            val stopAuditSeed = HostStopAuditor.capture(resolvedPid, LOG_TAG)
            val outcome = HostProcessTerminator.terminateTerminalProcessGroup(resolvedPid) { message ->
                Logger.i(LOG_TAG, "终端停止补偿(脱离态): session=${record.id} pid=$resolvedPid $message")
            }
            Logger.i(
                LOG_TAG,
                "终端停止补偿完成(脱离态): session=${record.id} pid=$resolvedPid exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill}"
            )
            writeTerminalActionLog(
                "== 终端停止补偿 session=${record.id} pid=$resolvedPid exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill} detached=true ==\n"
            )
            HostStopAuditor.audit(stopAuditSeed, LOG_TAG)
        } else {
            Logger.i(LOG_TAG, "终端脱离态停止未命中可用 pid: session=${record.id}")
            writeTerminalActionLog(
                "== 终端停止跳过 session=${record.id} pid=0 reason=no-resolved-pid detached=true ==\n"
            )
            null
        }

        report?.let { auditReport ->
            Logger.i(LOG_TAG, "终端停止诊断(脱离态): session=${record.id} ${auditReport.toCompactSummary()}")
            writeTerminalActionLog(auditReport.toLogBlock("终端停止诊断 ${record.id}"))
        }

        withContext(Dispatchers.IO) {
            KFWorkspaceManager.updateTerminalSessionStatus(
                context = appContext,
                sessionId = record.id,
                status = ManagedTerminalStatus.STOPPED,
                lastExitedAt = System.currentTimeMillis(),
                lastPid = resolvedPid ?: record.lastPid,
                lastExitCode = record.lastExitCode
            )
        }
        refreshManagedSessionsSnapshot()
        RuntimeFrameCoordinator.refreshProcessSnapshot(
            context = appContext,
            reason = "terminal-stop-detached:${record.id}"
        )
        RuntimeFrameCoordinator.scheduleProcessRefreshes(
            context = appContext,
            delaysMs = listOf(600L),
            reason = "terminal-stop-detached:${record.id}"
        )
    }

    private fun resolveTerminalStopPid(record: ManagedTerminalRecord): Int? {
        val mappedPid = ContainerProcessStore.snapshot.value.processes
            .firstOrNull { process ->
                process.linkedTerminalSessionId == record.id && process.pid > 0 && !process.isSynthetic
            }
            ?.pid
        return mappedPid ?: record.lastPid?.takeIf { it > 0 }
    }

    private fun stopAllSessionsInternal(reason: String) {
        val allSessionIds = sessionHolders.keys.toList()
        allSessionIds.forEach { sessionId ->
            stopManagedSession(sessionId, reason)
        }
        activeSessionId = null
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        val holder = sessionHolders.values.firstOrNull { it.session == changedSession } ?: return
        auditTerminalOutputCapability(
            actionName = "terminalOutput",
            holder = holder,
            outputLevel = CapabilityOutputLevel.STREAM
        )
        if (holder.hasEndedRecord()) {
            requestTranscriptMirror(holder)
            if (activeSessionId == holder.record.id) {
                uiCallbacks.refreshTerminalView()
            }
            return
        }
        if (holder.record.status != ManagedTerminalStatus.RUNNING && holder.isSessionRunning()) {
            promoteHolderToRunning(holder, showNote = false)
        }
        requestTranscriptMirror(holder)
        if (activeSessionId == holder.record.id) {
            uiCallbacks.refreshTerminalView()
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        val holder = sessionHolders.values.firstOrNull { it.session == changedSession } ?: return
        val newTitle = sanitizeTerminalTitle(changedSession.title.orEmpty()) ?: return
        if (newTitle != holder.record.title) {
            Logger.i(LOG_TAG, "终端标题变化: ${holder.record.title} -> $newTitle")
            holder.record = holder.record.copy(title = newTitle)
            changedSession.mSessionName = newTitle
            syncRuntimeEntry(holder)
            uiCallbacks.onManagedSessionsChanged()
            controllerScope.launch(Dispatchers.IO) {
                KFWorkspaceManager.updateTerminalSessionTitle(
                    context = appContext,
                    sessionId = holder.record.id,
                    title = newTitle
                )
            }
        }
    }

    private fun sanitizeTerminalTitle(title: String): String? {
        val cleaned = title
            .replace(Regex("\\p{Cntrl}+"), " ")
            .trim()
            .take(80)
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val holder = sessionHolders.values.firstOrNull { it.session == finishedSession } ?: return
        holder.probeJob?.cancel()
        holder.probeStartedAt = null
        holder.firstPendingInputAt = null
        holder.pendingInput.clear()
        controllerScope.launch {
            flushTranscriptMirror(holder, force = true)
        }
        val exitCode = finishedSession.exitStatus
        val finalStatus = ProcessExitSemantics.terminalFinalStatus(holder.record.status, exitCode)
        updateSessionStatus(
            holder,
            finalStatus,
            lastExitedAt = System.currentTimeMillis(),
            lastPid = finishedSession.validPid() ?: holder.record.lastPid,
            lastExitCode = exitCode
        )
        if (sessionHolders.values.none { it.isSessionRunning() }) {
            WorkSurfaceRuntimeBridge.markContainerStopped(appContext)
        }
        if (activeSessionId == holder.record.id) {
            uiCallbacks.showSessionNote(
                if (finalStatus == ManagedTerminalStatus.FAILED) {
                    "${holder.record.title} 异常退出，状态已保留。"
                } else if (finalStatus == ManagedTerminalStatus.STOPPED) {
                    "${holder.record.title} 已结束，状态已保留。"
                } else {
                    "${holder.record.title} 已退出，状态已保留。"
                }
            )
            uiCallbacks.refreshTerminalView()
            uiCallbacks.updateCursorState(false)
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        uiCallbacks.copyTextToClipboard(text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        uiCallbacks.pasteTextFromClipboard()
    }

    override fun onBell(session: TerminalSession) {
        uiCallbacks.performBellFeedback()
    }

    override fun onColorsChanged(session: TerminalSession) {
        uiCallbacks.refreshTerminalColors()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        uiCallbacks.updateCursorState(state)
    }

    override fun getTerminalCursorStyle(): Int {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    }

    override fun logError(tag: String, message: String) {
        Logger.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Logger.i(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Logger.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Logger.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Logger.d(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Logger.e(tag, "$message: ${e.message}")
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Logger.e(tag, e.stackTraceToString())
    }
}
