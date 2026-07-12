package com.kite.app.run

enum class KiteRunUiTone {
    Info,
    Success,
    Warning,
    Danger,
    Neutral
}

enum class KiteRunPrimaryAction {
    Start,
    Stop,
    Retry,
    Busy,
    Blocked
}

data class KiteCardRunUiProjection(
    val badgeLabel: String?,
    val tone: KiteRunUiTone,
    val primaryAction: KiteRunPrimaryAction,
    val primaryActionLabel: String,
    val primaryActionEnabled: Boolean,
    val live: Boolean,
    val problem: Boolean
)

object KiteCardRunUiProjector {
    fun project(status: CardRunStatus, runtimeBlocked: Boolean = false): KiteCardRunUiProjection {
        val problem = status == CardRunStatus.Failed || status == CardRunStatus.BridgeUnavailable
        val live = status == CardRunStatus.Starting ||
            status == CardRunStatus.Stopping ||
            status == CardRunStatus.Running ||
            status == CardRunStatus.WaitingTerminal ||
            status == CardRunStatus.AlreadyRunning ||
            status == CardRunStatus.Opened
        val tone = when (status) {
            CardRunStatus.Failed, CardRunStatus.BridgeUnavailable -> KiteRunUiTone.Danger
            CardRunStatus.Stopping -> KiteRunUiTone.Warning
            CardRunStatus.Starting, CardRunStatus.WaitingTerminal -> KiteRunUiTone.Info
            CardRunStatus.Running, CardRunStatus.AlreadyRunning, CardRunStatus.Opened -> KiteRunUiTone.Success
            else -> KiteRunUiTone.Neutral
        }
        val badge = when {
            problem -> "失败"
            status == CardRunStatus.WaitingTerminal || status == CardRunStatus.Opened -> "手动操作"
            live -> "运行中"
            else -> null
        }
        val action = when {
            runtimeBlocked -> KiteRunPrimaryAction.Blocked
            problem -> KiteRunPrimaryAction.Retry
            status.isInterruptibleStatus() -> KiteRunPrimaryAction.Stop
            status == CardRunStatus.Starting || status == CardRunStatus.Stopping -> KiteRunPrimaryAction.Busy
            else -> KiteRunPrimaryAction.Start
        }
        return KiteCardRunUiProjection(
            badgeLabel = badge,
            tone = tone,
            primaryAction = action,
            primaryActionLabel = when (action) {
                KiteRunPrimaryAction.Start -> "启动"
                KiteRunPrimaryAction.Stop -> "停止"
                KiteRunPrimaryAction.Retry -> "重试"
                KiteRunPrimaryAction.Busy -> "处理中"
                KiteRunPrimaryAction.Blocked -> "等待"
            },
            primaryActionEnabled = action != KiteRunPrimaryAction.Busy && action != KiteRunPrimaryAction.Blocked,
            live = live,
            problem = problem
        )
    }

    private fun CardRunStatus.isInterruptibleStatus(): Boolean = this in CardRunStatus.interruptibleStatuses
}
