package com.kite.app.application.runs

import com.kite.app.run.CardRunState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class RunInstanceCloseSource {
    Explicit,
    NavigateBack,
    TaskRemoved,
    MainTaskRemoved,
}

internal data class RunInstanceCloseCommand(
    val instanceId: String,
    val expectedGeneration: Long,
    val source: RunInstanceCloseSource,
)

/**
 * 显式关闭、系统任务移除，以及需要解析运行语义的页面返回共用的进程级入口。
 * 普通页面返回仍只做导航；只有安装向导会在这里判断能否取消尚未开始的计划。
 */
internal class RunInstanceCloseCoordinator(
    private val scope: CoroutineScope,
    private val state: (String) -> CardRunState?,
    private val stopRun: (RunStopCommand) -> RunCommandResult,
    private val cancelInstallWizard: suspend (CardRunState, RunInstanceCloseSource) -> Boolean,
) {
    private data class CloseKey(val instanceId: String, val generation: Long)

    private val closeLock = Any()
    private val activeCloses = mutableSetOf<CloseKey>()

    fun request(command: RunInstanceCloseCommand) {
        if (command.instanceId.isBlank() || command.expectedGeneration <= 0L) return
        scope.launch { close(command) }
    }

    internal suspend fun close(command: RunInstanceCloseCommand): RunCommandResult {
        if (command.instanceId.isBlank() || command.expectedGeneration <= 0L) {
            return RunCommandResult.Ignored("invalid_identity")
        }
        val key = CloseKey(command.instanceId, command.expectedGeneration)
        val accepted = synchronized(closeLock) { activeCloses.add(key) }
        if (!accepted) return RunCommandResult.Ignored("close_in_progress")
        return try {
            try {
                closeCurrent(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                RunCommandResult.Ignored("close_failed")
            }
        } finally {
            synchronized(closeLock) { activeCloses.remove(key) }
        }
    }

    private suspend fun closeCurrent(command: RunInstanceCloseCommand): RunCommandResult {
        val current = state(command.instanceId)
            ?: return RunCommandResult.Ignored("missing_instance")
        if (current.createdAt != command.expectedGeneration) {
            return RunCommandResult.Ignored("generation_mismatch")
        }
        return if (current.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD) {
            if (cancelInstallWizard(current, command.source)) {
                RunCommandResult.Accepted(current.instanceId)
            } else {
                RunCommandResult.Ignored("install_wizard_close_rejected")
            }
        } else {
            stopRun(RunStopCommand(current.instanceId, current.createdAt))
        }
    }
}
