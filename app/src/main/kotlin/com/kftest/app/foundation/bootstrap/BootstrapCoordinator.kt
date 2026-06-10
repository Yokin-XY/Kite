package com.kftest.app.foundation.bootstrap

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.runtime.RuntimeBootstrapProgress
import com.kftest.app.foundation.service.BackgroundRuntimeHost
import com.kftest.app.foundation.service.KFShellService
import com.kftest.app.foundation.terminal.TerminalRuntimeHost
import com.kftest.app.foundation.workspace.KFWorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BootstrapStage {
    IDLE,
    SERVICE_REQUESTED,
    ROOTFS_EXTRACTING,
    BASE_BOOTSTRAP,
    SPACE_READY,
    TERMINAL_WARMING,
    READY,
    FAILED
}

data class BootstrapSnapshot(
    val stage: BootstrapStage = BootstrapStage.IDLE,
    val startedAt: Long = 0L,
    val finishedAt: Long? = null,
    val lastError: String? = null
)

/**
 * 任务入口层启动协调器。
 *
 * 它只负责把“开机后先拉哪些工作面宿主”串起来，不直接承载容器细节或终端业务。
 */
object BootstrapCoordinator {

    private const val LOG_TAG = "BootstrapCoordinator"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(BootstrapSnapshot())
    val snapshot: StateFlow<BootstrapSnapshot> = _snapshot

    @Volatile
    private var bootstrapJob: Job? = null

    @Synchronized
    fun ensureStarted(context: Context) {
        val existing = bootstrapJob
        if (existing != null && existing.isActive) {
            Logger.i(LOG_TAG, "忽略重复启动协调请求")
            return
        }
        val appContext = context.applicationContext
        bootstrapJob = scope.launch {
            runCatching {
                val startedAt = System.currentTimeMillis()
                _snapshot.value = BootstrapSnapshot(
                    stage = BootstrapStage.ROOTFS_EXTRACTING,
                    startedAt = startedAt
                )
                KFApplication.markLaunchStage(LOG_TAG, "启动协调器已接管")
                Logger.i(LOG_TAG, "Host service start deferred until workspace is ready")
                Logger.i(LOG_TAG, "Host service request is waiting for workspace initialization")

                withContext(Dispatchers.IO) {
                    KFWorkspaceManager.ensureDefaultSpace(appContext)
                }
                _snapshot.value = _snapshot.value.copy(stage = BootstrapStage.SPACE_READY)
                KFShellService.start(appContext)
                Logger.i(LOG_TAG, "Host service start requested after workspace became ready")
                KFApplication.markLaunchStage(LOG_TAG, "默认空间已预热")
                BackgroundRuntimeHost.ensureCoreRuntimes(appContext)
                BackgroundRuntimeHost.ensureResidentRuntimes(appContext, reason = "service-start:app-bootstrap")
                KFApplication.markLaunchStage(LOG_TAG, "后台守护运行项已请求")

                // 冷启动时先把旧宿主残留的终端快照归一化，再决定当前会话和预热入口，
                // 避免短时间内把已经死掉的 RUNNING/ATTACHED 记录误当成当前会话。
                TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
                KFApplication.markLaunchStage(LOG_TAG, "持久终端快照已归一化")

                _snapshot.value = _snapshot.value.copy(stage = BootstrapStage.TERMINAL_WARMING)
                TerminalRuntimeHost.prewarmPrimarySession(appContext)
                KFApplication.markLaunchStage(LOG_TAG, "主终端预热已请求")

                _snapshot.value = _snapshot.value.copy(
                    stage = BootstrapStage.READY,
                    finishedAt = System.currentTimeMillis(),
                    lastError = null
                )
                RuntimeBootstrapProgress.ready()
                Logger.i(LOG_TAG, "启动协调完成")
            }.onFailure { error ->
                Logger.e(LOG_TAG, "启动协调失败: ${error.message}")
                RuntimeBootstrapProgress.failed(error.message ?: error.javaClass.simpleName)
                _snapshot.value = _snapshot.value.copy(
                    stage = BootstrapStage.FAILED,
                    finishedAt = System.currentTimeMillis(),
                    lastError = error.message
                )
            }
        }
    }
}
