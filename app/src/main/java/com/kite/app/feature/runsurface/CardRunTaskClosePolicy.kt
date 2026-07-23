package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState

/** 明确关闭任务窗口时，决定哪些纯显示面实例应同时退出活动运行集合。 */
internal object CardRunTaskClosePolicy {
    fun shouldRemoveRunState(state: CardRunState): Boolean =
        state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD
}
