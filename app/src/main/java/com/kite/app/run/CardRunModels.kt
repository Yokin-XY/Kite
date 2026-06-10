package com.kite.app.run

enum class CardRunStatus(
    val label: String,
    val lifecycleEvent: String
) {
    Unknown("未启动", "unknown"),
    Stopped("未启动", "stopped"),
    Starting("启动中", "starting"),
    Running("运行中", "running"),
    WaitingTerminal("等待终端", "waiting_terminal"),
    AlreadyRunning("已运行", "already_running"),
    Opened("已打开", "opened"),
    Completed("已完成", "finished"),
    Failed("启动失败", "failed"),
    Stopping("停止中", "stopping"),
    BridgeUnavailable("桥接不可用", "bridge_unavailable");

    companion object {
        val activeStatuses = setOf(Running, AlreadyRunning)

        fun fromRecipeStatus(status: String): CardRunStatus = when (status) {
            "opened" -> Opened
            "finished" -> Completed
            "waiting_terminal" -> WaitingTerminal
            "running" -> Running
            "already_running" -> AlreadyRunning
            "failed" -> Failed
            "stopped" -> Stopped
            else -> Unknown
        }
    }
}

enum class CardRunSurface(val label: String) {
    Summary("概览"),
    Report("报告"),
    Terminal("终端"),
    Web("网页")
}

data class CardRunState(
    val instanceId: String,
    val recipeId: String,
    val recipeName: String = "",
    val status: CardRunStatus,
    val surface: CardRunSurface = CardRunSurface.Summary,
    val currentStepIndex: Int = -1,
    val stepCount: Int = 0,
    val runId: String? = null,
    val terminalSessionId: String? = null,
    val pid: String? = null,
    val lastMeaningfulOutput: String? = null,
    val lastError: String? = null,
    val shellReportText: String? = null,
    val nextActionUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun isBusy(): Boolean = status == CardRunStatus.Starting ||
        status == CardRunStatus.Stopping ||
        status == CardRunStatus.WaitingTerminal

    fun isActive(): Boolean = status in CardRunStatus.activeStatuses

    fun hasRunBinding(): Boolean =
        !runId.isNullOrBlank() || !pid.isNullOrBlank() || !terminalSessionId.isNullOrBlank()

    fun recommendedSurface(): CardRunSurface = when {
        !nextActionUrl.isNullOrBlank() -> CardRunSurface.Web
        !terminalSessionId.isNullOrBlank() -> CardRunSurface.Terminal
        failureSummary() != null || !lastMeaningfulOutput.isNullOrBlank() -> CardRunSurface.Report
        else -> CardRunSurface.Summary
    }

    fun failureSummary(): String? = when (status) {
        CardRunStatus.Failed -> lastError ?: lastMeaningfulOutput
        CardRunStatus.BridgeUnavailable -> lastError ?: "桥接不可用"
        else -> null
    }?.take(80)

    fun feedbackSummary(): String? = when {
        !lastError.isNullOrBlank() -> lastError
        !lastMeaningfulOutput.isNullOrBlank() -> lastMeaningfulOutput
        status == CardRunStatus.BridgeUnavailable -> "桥接不可用"
        else -> null
    }?.take(80)

    companion object {
        fun fromRecipeStatus(recipeId: String, status: String): CardRunState =
            CardRunState(
                instanceId = "idle_$recipeId",
                recipeId = recipeId,
                status = CardRunStatus.fromRecipeStatus(status)
            )
    }
}

data class PendingTerminalFlow(
    val recipeId: String,
    val instanceId: String,
    val sessionId: String,
    val nextStepIndex: Int
)
