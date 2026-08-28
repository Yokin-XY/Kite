package com.kite.app.action

internal enum class KiteResourceActionIntent {
    Install,
    ReopenInstall,
    Open,
    Stop,
    Uninstall,
    CheckUpdate,
    Update,
    Reinstall,
    Repair,
    CancelInstall,
    CancelFailedInstall,
    BusyStatus,
    Unsupported
}

internal enum class KiteResourceActionSource(val logValue: String) {
    Card("resource_card"),
    Detail("resource_detail"),
    Wizard("install_wizard"),
    Continuation("continuation"),
    Automation("automation")
}

internal data class KiteResourceActionRequest(
    val resourceId: String,
    val intent: KiteResourceActionIntent,
    val source: KiteResourceActionSource
)

/** 过渡期把资源投影标签收口为稳定意图；D3 将直接由 UiState 提供意图。 */
internal object KiteResourceActionCoordinator {
    fun primaryIntent(actionLabel: String, reopenInstall: Boolean): KiteResourceActionIntent {
        if (reopenInstall && actionLabel in installLabels) {
            return KiteResourceActionIntent.ReopenInstall
        }
        return when (actionLabel) {
            in installLabels -> KiteResourceActionIntent.Install
            "处理中", "获取中" -> KiteResourceActionIntent.ReopenInstall
            "打开", "运行中" -> KiteResourceActionIntent.Open
            "修复" -> KiteResourceActionIntent.Repair
            "继续停止" -> KiteResourceActionIntent.Stop
            "卸载", "继续卸载" -> KiteResourceActionIntent.Uninstall
            "卸载中" -> KiteResourceActionIntent.BusyStatus
            else -> KiteResourceActionIntent.Unsupported
        }
    }

    private val installLabels = setOf("获取", "重新获取", "安装", "重新安装")
}
