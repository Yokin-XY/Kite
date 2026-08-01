package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState

internal enum class CardRunTaskCloseReason {
    DismissSurface,
    StopConfirmed,
}

internal enum class CardRunTaskNavigationAction {
    HideTask,
    CloseTask,
}

/** 普通导航只决定显示面去向，不写运行或安装事实。 */
internal object CardRunTaskNavigationPolicy {
    fun decide(state: CardRunState?): CardRunTaskNavigationAction =
        if (state != null) {
            CardRunTaskNavigationAction.HideTask
        } else {
            CardRunTaskNavigationAction.CloseTask
        }
}
