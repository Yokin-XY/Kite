package com.kftest.app.foundation.bootstrap

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.runtime.KFContainerManager
import com.kftest.app.foundation.runtime.RuntimeBootstrapProgress
import com.kftest.app.foundation.service.BackgroundRuntimeHost
import com.kftest.app.foundation.service.KFShellService
import com.kftest.app.foundation.terminal.TerminalRuntimeHost
import com.kftest.app.foundation.toolchain.ToolchainInstallPhase
import com.kftest.app.foundation.toolchain.ToolchainPackInstaller
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
            RuntimeBootstrapProgress.beginBootstrapRun()
            try {
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

                    // 冷启动时只归一化旧宿主残留的终端快照，不主动创建终端入口。
                    TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
                    KFApplication.markLaunchStage(LOG_TAG, "持久终端快照已归一化")

                    withContext(Dispatchers.IO) {
                        RuntimeBootstrapProgress.stageStarted("ensureRuntimeOperational")
                        KFContainerManager.ensureRuntimeOperational(appContext)
                        RuntimeBootstrapProgress.stageCompleted("ensureRuntimeOperational")
                    }
                    KFApplication.markLaunchStage(LOG_TAG, "Ubuntu 最小 shell 校验通过")

                    var readyDetail = "系统镜像、基础工具和工作区已经准备好。"
                    withContext(Dispatchers.IO) {
                        RuntimeBootstrapProgress.stageStarted("installBundledToolchain")
                        val toolchain = ToolchainPackInstaller.prepareAiEnvForBootstrap(appContext)
                        if (toolchain.phase == ToolchainInstallPhase.FAILED || toolchain.exitCode?.let { it != 0 } == true) {
                            val summary = toolchain.summary.ifBlank { "部分内置工具安装失败" }
                            readyDetail = "Ubuntu 已可用；部分内置工具需要稍后处理：$summary"
                            Logger.e(LOG_TAG, "内置工具包安装未完成: $summary")
                        }
                        RuntimeBootstrapProgress.stageCompleted("installBundledToolchain")
                    }
                    KFApplication.markLaunchStage(LOG_TAG, "内置工具包安装路径已走完")

                    _snapshot.value = _snapshot.value.copy(
                        stage = BootstrapStage.READY,
                        finishedAt = System.currentTimeMillis(),
                        lastError = null
                    )
                    RuntimeBootstrapProgress.ready(readyDetail)
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
            } finally {
                RuntimeBootstrapProgress.endBootstrapRun()
            }
        }
    }
}
