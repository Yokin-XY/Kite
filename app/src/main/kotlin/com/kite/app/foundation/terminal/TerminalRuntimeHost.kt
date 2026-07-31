package com.kite.app.foundation.terminal

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ManagedTerminalRecord
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
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
    @Volatile
    private var preparedRegistrySpaceId: String? = null

    @Synchronized
    fun ensureController(appContext: Context): TerminalSessionController {
        val context = appContext.applicationContext
        return controller ?: TerminalSessionController(
            appContext = context,
            parentScope = hostScope,
            uiCallbacks = callbackProxy
        ).also {
            controller = it
            val preparedSpaceId = preparedRegistrySpaceId
            preparedRegistrySpaceId = null
            if (preparedSpaceId == null) {
                schedulePersistedSessionsReconcile(context)
            } else {
                Logger.i("TerminalRuntimeHost", "复用已准备终端快照: space=$preparedSpaceId")
            }
        }
    }

    @Synchronized
    fun attachUi(
        appContext: Context,
        uiCallbacks: TerminalSessionUiCallbacks,
        preferredSessionId: String? = null,
        notifyManagedSessionsChanged: Boolean = true
    ): TerminalSessionController {
        callbackProxy.attach(uiCallbacks)
        return ensureController(appContext).also {
            it.reattachActiveSession(
                preferredSessionId = preferredSessionId,
                notifyManagedSessionsChanged = notifyManagedSessionsChanged
            )
        }
    }

    fun createShellSession(appContext: Context) {
        ensureController(appContext).createAndSwitchShellSession()
    }

    fun launchAgentSession(appContext: Context, runtimeId: String) {
        ensureController(appContext).launchAgentSession(runtimeId)
    }

    fun switchToSession(appContext: Context, sessionId: String) {
        ensureController(appContext).switchToSession(sessionId)
    }

    fun openEmbeddedSession(appContext: Context, sessionId: String) {
        ensureController(appContext).openEmbeddedSession(sessionId)
    }

    fun stageEmbeddedSession(appContext: Context, record: ManagedTerminalRecord) {
        ensureController(appContext).stageEmbeddedSession(record)
    }

    fun openEmbeddedSession(appContext: Context, record: ManagedTerminalRecord) {
        ensureController(appContext).openEmbeddedSession(record)
    }

    fun setLaunchEnvironmentOverrides(
        appContext: Context,
        sessionId: String,
        overrides: Map<String, String>
    ) {
        ensureController(appContext).setLaunchEnvironmentOverrides(sessionId, overrides)
    }

    fun setLaunchConfigOverride(
        appContext: Context,
        sessionId: String,
        config: ContainerLaunchConfig,
    ) {
        ensureController(appContext).setLaunchConfigOverride(sessionId, config)
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

    @Synchronized
    fun refreshRuntimeSnapshot(appContext: Context, preparedSpace: com.kite.app.foundation.contracts.SpaceRecord? = null) {
        val context = appContext.applicationContext
        val existingController = controller
        if (existingController != null) {
            existingController.refreshManagedSessionsSnapshot()
            return
        }

        val space = preparedSpace ?: KFWorkspaceManager.ensureActiveSpace(context)
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
        preparedRegistrySpaceId = space.id
    }

    /**
     * 环境切换已由 View 守卫确认旧进程退出。丢弃旧 controller 的内存句柄，
     * 但保留 UI 回调绑定，让页面回到前台时能直接附着目标 Space。
     */
    @Synchronized
    fun releaseForEnvironmentSwitch(reason: String) {
        controller?.stopCurrentSession(reason)
        controller = null
        preparedRegistrySpaceId = null
        reconcileJob?.cancel()
        reconcileJob = null
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
        preparedRegistrySpaceId = null
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
        val space = KFWorkspaceManager.ensureActiveSpace(appContext)
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
