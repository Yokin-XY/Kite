package com.kite.app.resources

import com.kite.app.run.CardRunStatus

data class KiteResourceUiProjection(
    val stateLabel: String,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val secondaryActionLabel: String?
)

object KiteResourceUiProjector {
    fun project(
        installed: Boolean,
        preparing: Boolean,
        installing: Boolean,
        uninstalling: Boolean,
        failed: Boolean,
        failedOperation: String,
        currentOperation: String = "",
        idleStateLabel: String,
        openRunStatus: CardRunStatus? = null,
        extraBusy: Boolean = false,
        installPlanInProgress: Boolean = false,
    ): KiteResourceUiProjection {
        val labels = when {
            installPlanInProgress -> "获取中" to "获取中"
            preparing -> "准备中" to "准备中"
            installing && currentOperation == KiteResourceInstallRecipes.OP_UPDATE -> "更新中" to "更新中"
            installing && currentOperation == KiteResourceInstallRecipes.OP_REINSTALL -> "重新安装中" to "重新安装中"
            installing -> "获取中" to "获取中"
            uninstalling -> "卸载中" to "卸载中"
            failed && failedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "卸载失败" to "继续卸载"
            failed -> "获取失败" to "重新获取"
            installed -> openRunLabels(openRunStatus) ?: ("已获取" to "打开")
            else -> idleStateLabel to "获取"
        }
        val busy = installPlanInProgress || preparing || installing || uninstalling || extraBusy ||
            openRunStatus == CardRunStatus.Starting || openRunStatus == CardRunStatus.Stopping
        return KiteResourceUiProjection(
            stateLabel = labels.first,
            actionLabel = labels.second,
            actionEnabled = when (labels.second) {
                "准备中", "启动中", "停止中", "卸载中", "处理中" -> false
                "获取中" -> true
                else -> !busy
            },
            secondaryActionLabel = when {
                labels.second == "获取中" -> "取消"
                failed && failedOperation != KiteResourceInstallStore.OP_UNINSTALL -> "取消"
                installed && labels.second == "运行中" -> "中止"
                installed && labels.second == "打开" -> "卸载"
                else -> null
            }
        )
    }

    private fun openRunLabels(status: CardRunStatus?): Pair<String, String>? =
        when (status) {
            CardRunStatus.Starting -> "启动中" to "启动中"
            CardRunStatus.Stopping -> "停止中" to "停止中"
            CardRunStatus.CleanupPending -> "停止待确认" to "继续停止"
            CardRunStatus.WaitingTerminal -> "等待终端" to "运行中"
            CardRunStatus.Running,
            CardRunStatus.AlreadyRunning,
            CardRunStatus.Opened -> "运行中" to "运行中"
            else -> null
        }
}
