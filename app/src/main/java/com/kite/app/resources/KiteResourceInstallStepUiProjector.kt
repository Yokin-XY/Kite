package com.kite.app.resources

enum class KiteResourceStepTone {
    Primary,
    Success,
    Danger,
    Neutral
}

data class KiteResourceInstallStepUiProjection(
    val statusLabel: String,
    val tone: KiteResourceStepTone,
    val failed: Boolean,
    val uninstalling: Boolean
)

object KiteResourceInstallStepUiProjector {
    fun project(
        uninstalling: Boolean,
        failed: Boolean,
        failedOperation: String,
        planStepStatus: String,
        installed: Boolean,
        isActive: Boolean
    ): KiteResourceInstallStepUiProjection {
        val uninstallFailed = failed && failedOperation == KiteResourceInstallStore.OP_UNINSTALL
        val effectiveFailed = failed || planStepStatus == KiteResourceInstallStore.PLAN_STEP_FAILED
        val statusLabel = when {
            uninstalling -> "卸载中"
            uninstallFailed -> "需重置"
            effectiveFailed -> "需卸载"
            planStepStatus == KiteResourceInstallStore.PLAN_STEP_RUNNING -> "获取中"
            installed -> "已完成"
            planStepStatus == KiteResourceInstallStore.PLAN_STEP_BLOCKED -> "已暂停"
            isActive -> "待获取"
            else -> "待获取"
        }
        val tone = when {
            uninstallFailed || effectiveFailed -> KiteResourceStepTone.Danger
            statusLabel == "获取中" || statusLabel == "卸载中" -> KiteResourceStepTone.Primary
            statusLabel == "已完成" -> KiteResourceStepTone.Success
            else -> KiteResourceStepTone.Neutral
        }
        return KiteResourceInstallStepUiProjection(
            statusLabel = statusLabel,
            tone = tone,
            failed = uninstallFailed || effectiveFailed,
            uninstalling = uninstalling
        )
    }
}
