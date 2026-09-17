package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState

internal enum class CardRunTaskCloseReason {
    DismissSurface,
    StopConfirmed,
}

internal enum class CardRunTaskNavigationAction {
    ResolveInstallWizardBack,
    HideTask,
    CloseTask,
}

/** 普通导航只提交意图；是否取消未开始的安装计划仍由进程级状态拥有者决定。 */
internal object CardRunTaskNavigationPolicy {
    fun decide(state: CardRunState?): CardRunTaskNavigationAction =
        when {
            state == null -> CardRunTaskNavigationAction.CloseTask
            state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD ->
                CardRunTaskNavigationAction.ResolveInstallWizardBack
            else -> CardRunTaskNavigationAction.HideTask
        }
}
