package com.kite.app.action

internal enum class KiteInstallPlanActionIntent {
    StartNext,
    Finish
}

internal data class KiteInstallPlanActionPlan(
    val label: String,
    val enabled: Boolean,
    val intent: KiteInstallPlanActionIntent? = null
)

internal object KiteInstallPlanActionCoordinator {
    fun plan(
        hasRunningStep: Boolean,
        hasUninstallingStep: Boolean,
        hasPending: Boolean,
        hasFailure: Boolean
    ): KiteInstallPlanActionPlan = when {
        hasUninstallingStep -> KiteInstallPlanActionPlan("卸载中", enabled = false)
        hasFailure -> KiteInstallPlanActionPlan("发现异常请手动处理", enabled = false)
        hasRunningStep -> KiteInstallPlanActionPlan("获取中", enabled = false)
        hasPending -> KiteInstallPlanActionPlan(
            "开始获取",
            enabled = true,
            intent = KiteInstallPlanActionIntent.StartNext
        )
        else -> KiteInstallPlanActionPlan(
            "完成",
            enabled = true,
            intent = KiteInstallPlanActionIntent.Finish
        )
    }
}
