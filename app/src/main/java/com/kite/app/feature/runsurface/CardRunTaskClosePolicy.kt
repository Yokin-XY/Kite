package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus

internal enum class CardRunTaskCloseReason {
    DismissSurface,
    FinishCompleted,
    StopConfirmed,
}

internal data class CardRunTaskCloseDecision(
    val removeRunState: Boolean,
    val clearInstallPlan: Boolean,
)

/** 明确关闭任务窗口时，决定纯显示面实例是否已经可以退出活动运行集合。 */
internal object CardRunTaskClosePolicy {
    fun decide(
        state: CardRunState,
        reason: CardRunTaskCloseReason,
        hasInstallPlan: Boolean,
        hasRunningInstallPlan: Boolean,
        hasActiveChildRun: Boolean,
    ): CardRunTaskCloseDecision {
        if (state.ownerKind != CardRunState.OWNER_KIND_INSTALL_WIZARD) {
            return CardRunTaskCloseDecision(removeRunState = false, clearInstallPlan = false)
        }
        if (reason == CardRunTaskCloseReason.StopConfirmed) {
            return CardRunTaskCloseDecision(
                removeRunState = true,
                clearInstallPlan = hasInstallPlan,
            )
        }
        val executionActive = hasRunningInstallPlan || hasActiveChildRun
        return CardRunTaskCloseDecision(
            removeRunState = !executionActive,
            clearInstallPlan = hasInstallPlan && !executionActive,
        )
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
