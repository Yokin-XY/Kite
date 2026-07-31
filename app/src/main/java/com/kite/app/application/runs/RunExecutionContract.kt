package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunAgentBinding

internal data class RunStartRequest(
    val recipe: KiteRecipe,
    val instanceId: String,
    val parentInstanceId: String? = null,
    val ownerKind: String = CardRunState.OWNER_KIND_CARD,
    val stepId: String? = null,
    val agentId: String? = null,
    val environmentId: String = ""
)

internal data class RunStateMutation(
    val status: CardRunStatus,
    val surface: CardRunSurface? = null,
    val currentStepIndex: Int? = null,
    val runtimeRootOwnerId: String? = null,
    val runtimeOwnerId: String? = null,
    val runtimeUnitId: String? = null,
    val ownedRuntimeOwnerIds: List<String>? = null,
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
    val agentId: String? = null,
    val agentBinding: CardRunAgentBinding? = null,
    val clearRunBinding: Boolean = false,
    val clearTerminalSession: Boolean = false,
    val clearNextActionUrl: Boolean = false,
    val clearAgentBinding: Boolean = false
)

/**
 * 运行事实端口。实现层可以继续使用 CardRunStore，但编排层不依赖它的 Android 持久化细节。
 */
internal interface RunStateGateway {
    fun register(recipe: KiteRecipe)
    fun recipe(recipeId: String): KiteRecipe?
    fun state(instanceId: String): CardRunState?
    fun current(recipeId: String): CardRunState?
    fun start(request: RunStartRequest): CardRunState
    fun update(recipe: KiteRecipe, instanceId: String, mutation: RunStateMutation): CardRunState
}

internal data class RecipeStepExecutionRequest(
    val recipe: KiteRecipe,
    val instanceId: String,
    val generation: Long,
    val stepIndex: Int,
    val step: KiteRecipeStep,
    val previousState: CardRunState,
    val attemptId: Long = 0L,
    val runtimeRootOwnerId: String? = null,
    val runtimeOwnerId: String? = null,
    val runtimeUnitId: String? = null
)

/**
 * 为一次运行补充底层执行环境。提供者只按运行实例返回通用环境变量，
 * 不感知资源类型、步骤类型或具体命令。
 */
internal fun interface RunExecutionEnvironmentProvider {
    fun environment(request: RecipeStepExecutionRequest): Map<String, String>

    companion object {
        val None = RunExecutionEnvironmentProvider { emptyMap() }
    }
}

internal data class RecipeStepCompletionRequest(
    val recipe: KiteRecipe,
    val instanceId: String,
    val generation: Long,
    val stepIndex: Int,
    val step: KiteRecipeStep,
    val state: CardRunState,
    val output: String
)

/**
 * 一次人工步骤确认的完整身份。调用方必须提交它看到的运行代次和步骤，
 * 编排器不会把迟到动作重新解释成“完成当前步骤”。
 */
internal data class RunStepCompletionCommand(
    val instanceId: String,
    val expectedGeneration: Long,
    val expectedStepIndex: Int,
    val expectedStepId: String,
    val output: String
)

internal data class RunStepRestartCommand(
    val instanceId: String,
    val expectedGeneration: Long,
    val expectedStepIndex: Int,
    val expectedStepId: String
)

internal data class RunOwnedWindowsCloseResult(
    val confirmed: Boolean,
    val message: String = "",
    val remainingInstanceIds: List<String> = emptyList()
)

internal fun interface RunOwnedWindowGateway {
    fun closeAll(
        instanceId: String,
        expectedGeneration: Long,
        callback: (RunOwnedWindowsCloseResult) -> Unit
    )

    companion object {
        val None = RunOwnedWindowGateway { _, _, callback ->
            callback(RunOwnedWindowsCloseResult(confirmed = true))
        }
    }
}

internal fun interface RunStartGate {
    fun rejectionReason(): String?

    companion object {
        val Allow = RunStartGate { null }
    }
}

internal sealed interface RecipeExecutionEvent {
    val instanceId: String
    val generation: Long
    val stepIndex: Int

    data class Progress(
        override val instanceId: String,
        override val generation: Long,
        override val stepIndex: Int,
        val mutation: RunStateMutation
    ) : RecipeExecutionEvent

    data class Completed(
        override val instanceId: String,
        override val generation: Long,
        override val stepIndex: Int,
        val mutation: RunStateMutation
    ) : RecipeExecutionEvent

    data class AwaitingUser(
        override val instanceId: String,
        override val generation: Long,
        override val stepIndex: Int,
        val mutation: RunStateMutation,
        val effect: RunExecutionEffect? = null
    ) : RecipeExecutionEvent

    data class Failed(
        override val instanceId: String,
        override val generation: Long,
        override val stepIndex: Int,
        val message: String,
        val bridgeUnavailable: Boolean = false,
        val mutation: RunStateMutation? = null
    ) : RecipeExecutionEvent
}

internal sealed interface RunExecutionEffect {
    val instanceId: String
    val recipeId: String

    data class OpenWeb(
        override val instanceId: String,
        override val recipeId: String,
        val url: String,
        val surfaceMode: String
    ) : RunExecutionEffect

    data class StopResolved(
        override val instanceId: String,
        override val recipeId: String,
        val stopped: Boolean,
        val message: String
    ) : RunExecutionEffect
}

internal fun interface RunExecutionEffectSink {
    fun emit(effect: RunExecutionEffect)
}

internal sealed interface RecipeStepCompletionResult {
    data class Ready(val output: String) : RecipeStepCompletionResult
    data class Failed(val message: String) : RecipeStepCompletionResult
}

internal enum class StopExecutionOutcome {
    Confirmed,
    StillRunning,
    VerificationUnavailable,
    Failed,
    ConnectionError,
    Unsupported,
    ParseError
}

internal data class RecipeStopRequest(
    val recipe: KiteRecipe,
    val instanceId: String,
    val generation: Long = 0L,
    val runtimeOwnerIds: List<String> = emptyList(),
    val runId: String? = null,
    val terminalSessionId: String? = null,
    val pid: String? = null,
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
    val interruptTerminal: Boolean = false
) {
    fun bridgeRunId(): String? = runId
        ?.takeIf { it.isNotBlank() && (terminalSessionId.isNullOrBlank() || it != terminalSessionId) }

    fun hasBridgeProcessBinding(): Boolean =
        runtimeOwnerIds.any { it.isNotBlank() } ||
            bridgeRunId() != null ||
            !pid.isNullOrBlank() ||
            !rootPid.isNullOrBlank() ||
            !processGroupId.isNullOrBlank() ||
            !systemSessionId.isNullOrBlank()
}

internal data class StopExecutionResult(
    val outcome: StopExecutionOutcome,
    val message: String = "",
    val remainingProcessIds: List<String> = emptyList(),
    val manualKillObserved: Boolean = false,
    val residueMarkerObserved: Boolean = false
)

/**
 * 所有步骤类型只通过这一端口进入执行层。实现层负责 PRoot、终端、Web、X11 和 Android 能力。
 */
internal interface RecipeExecutor {
    fun execute(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    )

    fun completeWaitingStep(
        request: RecipeStepCompletionRequest,
        callback: (RecipeStepCompletionResult) -> Unit
    ) {
        callback(RecipeStepCompletionResult.Ready(request.output))
    }

    fun stop(
        request: RecipeStopRequest,
        callback: (StopExecutionResult) -> Unit
    )
}

internal sealed interface RunCommandResult {
    data class Accepted(val instanceId: String) : RunCommandResult
    data class Ignored(val reason: String) : RunCommandResult
}
