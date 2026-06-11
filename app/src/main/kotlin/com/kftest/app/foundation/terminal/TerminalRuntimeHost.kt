package com.kftest.app.foundation.terminal

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.workspace.KFWorkspaceManager
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 工作面层对象，主责是把终端会话宿主化到进程级。
 *
 * 主归属是工作面层，次归属是任务入口层适配：
 * - 对上给 Fragment / Service 一个稳定入口
 * - 对下只调用终端控制器，不把入口业务直接塞进建房层
 */
object TerminalRuntimeHost {

    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callbackProxy = TerminalUiCallbacksProxy()

    @Volatile
    private var controller: TerminalSessionController? = null
    @Volatile
    private var reconcileJob: Job? = null

    @Synchronized
    fun ensureController(appContext: Context): TerminalSessionController {
        val context = appContext.applicationContext
        return controller ?: TerminalSessionController(
            appContext = context,
            parentScope = hostScope,
            uiCallbacks = callbackProxy
        ).also {
            controller = it
            schedulePersistedSessionsReconcile(context)
        }
    }

    @Synchronized
    fun attachUi(
        appContext: Context,
        uiCallbacks: TerminalSessionUiCallbacks
    ): TerminalSessionController {
        callbackProxy.attach(uiCallbacks)
        return ensureController(appContext).also { it.reattachActiveSession() }
    }

    fun createShellSession(appContext: Context) {
        ensureController(appContext).createAndSwitchShellSession()
    }

    fun prewarmPrimarySession(appContext: Context) {
        Logger.i("TerminalRuntimeHost", "请求预热主终端")
        ensureController(appContext).prepareAndStartContainer(
            resetContainer = false,
            forceRestart = false
        )
    }

    fun launchAgentSession(appContext: Context, runtimeId: String) {
        ensureController(appContext).launchAgentSession(runtimeId)
    }

    fun switchToSession(appContext: Context, sessionId: String) {
        ensureController(appContext).switchToSession(sessionId)
    }

    fun setLaunchEnvironmentOverrides(
        appContext: Context,
        sessionId: String,
        overrides: Map<String, String>
    ) {
        ensureController(appContext).setLaunchEnvironmentOverrides(sessionId, overrides)
    }

    fun sendCommand(appContext: Context, command: String, sessionId: String? = null) {
        ensureController(appContext).sendCommandToSession(sessionId, command)
    }

    fun pasteMultiline(appContext: Context, payload: String) {
        ensureController(appContext).writePastedInputToDefault(payload)
    }

    fun endSession(appContext: Context, sessionId: String? = null) {
        val controller = ensureController(appContext)
        if (sessionId.isNullOrBlank()) {
            controller.closeActiveSession()
        } else {
            controller.endSession(sessionId)
        }
    }

    fun deleteSession(appContext: Context, sessionId: String) {
        ensureController(appContext).deleteSession(sessionId)
    }

    fun refreshRuntimeSnapshot(appContext: Context) {
        val context = appContext.applicationContext
        val existingController = controller
        if (existingController != null) {
            existingController.refreshManagedSessionsSnapshot()
            return
        }

        val space = KFWorkspaceManager.ensureDefaultSpace(context)
        val records = KFWorkspaceManager.freezeRecoverableTerminalSessions(context)
            .filter { it.spaceId == space.id }
        val transcriptDir = File(
            WorkSurfaceRuntimeBridge.getLogsDir(context),
            "terminal-transcripts"
        ).also { if (!it.exists()) it.mkdirs() }
        TerminalRuntimeRegistry.replaceAll(
            records = records,
            transcriptDir = transcriptDir,
            currentViewedSessionId = space.currentTerminalSessionId
        )
    }

    @Synchronized
    fun detachUi(uiCallbacks: TerminalSessionUiCallbacks? = null) {
        controller?.flushTranscriptMirrors("detach-ui")
        callbackProxy.detach(uiCallbacks)
    }

    @Synchronized
    fun release(reason: String) {
        controller?.flushTranscriptMirrors("release:$reason")
        controller?.stopCurrentSession(reason)
        controller = null
        reconcileJob?.cancel()
        reconcileJob = null
        callbackProxy.detach()
    }

    @Synchronized
    private fun schedulePersistedSessionsReconcile(appContext: Context) {
        val existing = reconcileJob
        if (existing != null && existing.isActive) {
            return
        }
        reconcileJob = hostScope.launch(Dispatchers.IO) {
            runCatching {
                reconcilePersistedSessions(appContext)
            }.onFailure { error ->
                Logger.e("TerminalRuntimeHost", "恢复持久化终端快照失败: ${error.message}")
            }.also {
                reconcileJob = null
            }
        }
    }

    private fun reconcilePersistedSessions(appContext: Context) {
        val space = KFWorkspaceManager.ensureDefaultSpace(appContext)
        val records = KFWorkspaceManager.freezeRecoverableTerminalSessions(appContext)
            .filter { it.spaceId == space.id }
        val transcriptDir = File(
            WorkSurfaceRuntimeBridge.getLogsDir(appContext),
            "terminal-transcripts"
        ).also { if (!it.exists()) it.mkdirs() }
        Logger.i(
            "TerminalRuntimeHost",
            "宿主初始化完成，会话快照=${records.size}，当前查看=${space.currentTerminalSessionId ?: "无"}"
        )
        TerminalRuntimeRegistry.replaceAll(
            records = records,
            transcriptDir = transcriptDir,
            currentViewedSessionId = space.currentTerminalSessionId
        )
    }

    private class TerminalUiCallbacksProxy : TerminalSessionUiCallbacks {

        @Volatile
        private var delegate: TerminalSessionUiCallbacks? = null

        @Volatile
        private var lastSessionNote: String = ""

        fun attach(uiCallbacks: TerminalSessionUiCallbacks) {
            delegate = uiCallbacks
            if (lastSessionNote.isNotBlank()) {
                uiCallbacks.showSessionNote(lastSessionNote)
            }
        }

        fun detach(uiCallbacks: TerminalSessionUiCallbacks? = null) {
            if (uiCallbacks == null || delegate === uiCallbacks) {
                delegate = null
            }
        }

        override fun showSessionNote(message: String) {
            lastSessionNote = message
            delegate?.showSessionNote(message)
        }

        override fun attachSession(session: TerminalSession) {
            delegate?.attachSession(session)
        }

        override fun onManagedSessionsChanged() {
            delegate?.onManagedSessionsChanged()
        }

        override fun refreshTerminalView() {
            delegate?.refreshTerminalView()
        }

        override fun copyTextToClipboard(text: String) {
            delegate?.copyTextToClipboard(text)
        }

        override fun pasteTextFromClipboard() {
            delegate?.pasteTextFromClipboard()
        }

        override fun performBellFeedback() {
            delegate?.performBellFeedback()
        }

        override fun refreshTerminalColors() {
            delegate?.refreshTerminalColors()
        }

        override fun updateCursorState(state: Boolean) {
            delegate?.updateCursorState(state)
        }
    }
}
