package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus

internal enum class CardRunTaskCloseReason {
    DismissSurface,
    FinishCompleted,
    StopConfirmed,
}

/** 明确关闭任务窗口时，决定纯显示面实例是否已经可以退出活动运行集合。 */
internal object CardRunTaskClosePolicy {
    fun shouldRemoveRunState(
        state: CardRunState,
        reason: CardRunTaskCloseReason,
        hasActiveInstallPlan: Boolean,
        hasActiveChildRun: Boolean,
    ): Boolean {
        if (state.ownerKind != CardRunState.OWNER_KIND_INSTALL_WIZARD) return false
        if (reason == CardRunTaskCloseReason.StopConfirmed) return true
        return !hasActiveInstallPlan && !hasActiveChildRun
    }

    fun isActiveChild(state: CardRunState): Boolean = state.status !in endedChildStatuses

    private val endedChildStatuses = setOf(
        CardRunStatus.Unknown,
        CardRunStatus.Stopped,
        CardRunStatus.Completed,
        CardRunStatus.Failed,
        CardRunStatus.BridgeUnavailable,
    )
}
