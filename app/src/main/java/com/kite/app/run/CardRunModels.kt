package com.kite.app.run

enum class CardRunStatus(
    val label: String,
    val lifecycleEvent: String
) {
    Unknown("未启动", "unknown"),
    Stopped("已停止", "stopped"),
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
        val interruptibleStatuses = setOf(Running, WaitingTerminal, AlreadyRunning, Opened)

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
    Web("网页"),
    X11("X11"),
    InstallWizard("安装向导")
}

data class CardRunState(
    val instanceId: String,
    val recipeId: String,
    val recipeName: String = "",
    val parentInstanceId: String? = null,
    val ownerKind: String = OWNER_KIND_CARD,
    val stepId: String? = null,
    val status: CardRunStatus,
    val surface: CardRunSurface = CardRunSurface.Summary,
    val currentStepIndex: Int = -1,
    val stepCount: Int = 0,
    val runId: String? = null,
    val terminalSessionId: String? = null,
    val pid: String? = null,
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
    val lastMeaningfulOutput: String? = null,
    val lastError: String? = null,
    val shellReportText: String? = null,
    val nextActionUrl: String? = null,
    val x11Display: String? = null,
    val x11SocketPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val cardInstanceId: String get() = instanceId

    fun isBusy(): Boolean = status == CardRunStatus.Starting ||
        status == CardRunStatus.Stopping ||
        status == CardRunStatus.WaitingTerminal

    fun isActive(): Boolean = status in CardRunStatus.activeStatuses

    fun isInterruptible(): Boolean = status in CardRunStatus.interruptibleStatuses

    fun hasRunBinding(): Boolean =
        !runId.isNullOrBlank() ||
            !pid.isNullOrBlank() ||
            !rootPid.isNullOrBlank() ||
            !processGroupId.isNullOrBlank() ||
            !terminalSessionId.isNullOrBlank() ||
            !x11Display.isNullOrBlank()

    fun recommendedSurface(): CardRunSurface = when {
        !nextActionUrl.isNullOrBlank() -> CardRunSurface.Web
        !terminalSessionId.isNullOrBlank() -> CardRunSurface.Terminal
        !x11Display.isNullOrBlank() -> CardRunSurface.X11
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
        const val OWNER_KIND_CARD = "card"
        const val OWNER_KIND_RESOURCE = "resource"
        const val OWNER_KIND_INSTALL_WIZARD = "install_wizard"
        const val OWNER_KIND_TERMINAL = "terminal"
        const val OWNER_KIND_WEB = "web"
        const val OWNER_KIND_X11 = "x11"

        fun fromRecipeStatus(recipeId: String, status: String): CardRunState =
            CardRunState(
                instanceId = "idle_$recipeId",
                recipeId = recipeId,
                status = CardRunStatus.fromRecipeStatus(status)
            )
    }
}

data class CardRunHistoryEntry(
    val historyId: String,
    val recipeId: String,
    val recipeName: String,
    val instanceId: String,
    val ownerKind: String = CardRunState.OWNER_KIND_CARD,
    val status: CardRunStatus,
    val currentStepIndex: Int = -1,
    val stepCount: Int = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val updatedAt: Long = startedAt,
    val summary: String = "",
    val error: String = "",
    val shellReportText: String = "",
    val steps: List<CardRunHistoryStep> = emptyList()
) {
    fun isClosed(): Boolean =
        endedAt != null ||
            status == CardRunStatus.Completed ||
            status == CardRunStatus.Failed ||
            status == CardRunStatus.Stopped ||
            status == CardRunStatus.BridgeUnavailable
}

data class CardRunHistoryStep(
    val index: Int,
    val type: String,
    val label: String,
    val detail: String,
    val reportText: String = ""
)
