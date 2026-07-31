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
    CleanupPending("停止待确认", "cleanup_pending"),
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
            "cleanup_pending" -> CleanupPending
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
    Agent("Agent 会话"),
    InstallWizard("安装向导")
}

enum class CardRunAgentConnectionStatus {
    Preparing,
    Ready,
    WaitingPermission,
    Disconnected,
    Failed,
    Stopped
}

/**
 * CardRunStore 只保存可恢复的轻量 Agent 绑定。消息、工具过程和流式正文不属于这里。
 */
data class CardRunAgentBinding(
    val providerId: String,
    val sessionId: String? = null,
    val status: CardRunAgentConnectionStatus,
    val statusMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun isActive(): Boolean = status == CardRunAgentConnectionStatus.Preparing ||
        status == CardRunAgentConnectionStatus.Ready ||
        status == CardRunAgentConnectionStatus.WaitingPermission
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
    val selectedWindowId: String? = null,
    val currentStepIndex: Int = -1,
    val stepCount: Int = 0,
    val runtimeRootOwnerId: String? = null,
    val runtimeOwnerId: String? = null,
    val runtimeUnitId: String? = null,
    val ownedRuntimeOwnerIds: List<String> = emptyList(),
    val runId: String? = null,
    val terminalSessionId: String? = null,
    val pid: String? = null,
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
    val runtimeLane: String? = null,
    val runtimeFallbackReason: String? = null,
    val lastMeaningfulOutput: String? = null,
    val lastError: String? = null,
    val shellReportText: String? = null,
    val nextActionUrl: String? = null,
    val x11Display: String? = null,
    val x11SocketPath: String? = null,
    /**
     * 运行实例固定绑定的产品身份。它不等于 providerId，也不会随连接停止而清除。
     */
    val agentId: String? = null,
    val agentBinding: CardRunAgentBinding? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val environmentId: String = DEFAULT_ENVIRONMENT_ID
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
            !x11Display.isNullOrBlank() ||
            agentBinding?.isActive() == true

    /**
     * 一次停止事务必须使用同一代实例的完整 owner 集合。
     * 叶子 owner 放在前面，根 owner 只补充生命周期范围，避免调用方各自漏传身份。
     */
    fun runtimeOwnerIdsForStop(): List<String> = (
        ownedRuntimeOwnerIds + listOfNotNull(runtimeOwnerId, runtimeRootOwnerId)
    ).map(String::trim).filter(String::isNotBlank).distinct()

    fun hasRuntimeOwnership(): Boolean = runtimeOwnerIdsForStop().isNotEmpty()

    fun recommendedSurface(): CardRunSurface = when {
        agentBinding != null -> CardRunSurface.Agent
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
        const val DEFAULT_ENVIRONMENT_ID = "default"
        const val OWNER_KIND_CARD = "card"
        const val OWNER_KIND_RESOURCE = "resource"
        const val OWNER_KIND_INSTALL_WIZARD = "install_wizard"
        const val OWNER_KIND_TERMINAL = "terminal"
        const val OWNER_KIND_WEB = "web"
        const val OWNER_KIND_STEP_REPLAY = "step_replay"
        const val OWNER_KIND_X11 = "x11"

        fun instanceIdForEnvironment(baseInstanceId: String, environmentId: String): String {
            val environment = environmentId.trim().ifBlank { DEFAULT_ENVIRONMENT_ID }
            return if (environment == DEFAULT_ENVIRONMENT_ID) {
                baseInstanceId
            } else if (baseInstanceId.endsWith("@environment:$environment")) {
                baseInstanceId
            } else {
                "$baseInstanceId@environment:$environment"
            }
        }

        fun fromRecipeStatus(recipeId: String, status: String): CardRunState =
            CardRunState(
                instanceId = "idle_$recipeId",
                recipeId = recipeId,
                status = CardRunStatus.fromRecipeStatus(status)
            )
    }
}

object CardRunWindowIds {
    private const val WORKFLOW_PREFIX = "workflow:"

    fun workflow(stepIndex: Int, surface: CardRunSurface): String =
        "$WORKFLOW_PREFIX$stepIndex:${surface.name}"

    fun workflowStepIndex(windowId: String): Int? = windowId
        .takeIf { it.startsWith(WORKFLOW_PREFIX) }
        ?.removePrefix(WORKFLOW_PREFIX)
        ?.substringBefore(':')
        ?.toIntOrNull()
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
    val steps: List<CardRunHistoryStep> = emptyList(),
    val environmentId: String = CardRunState.DEFAULT_ENVIRONMENT_ID
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
