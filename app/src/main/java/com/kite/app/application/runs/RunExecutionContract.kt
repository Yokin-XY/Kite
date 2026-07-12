package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface

internal data class RunStartRequest(
    val recipe: KiteRecipe,
    val instanceId: String,
    val parentInstanceId: String? = null,
    val ownerKind: String = CardRunState.OWNER_KIND_CARD,
    val stepId: String? = null
)

internal data class RunStateMutation(
    val status: CardRunStatus,
    val surface: CardRunSurface? = null,
    val currentStepIndex: Int? = null,
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
    val clearRunBinding: Boolean = false,
    val clearTerminalSession: Boolean = false,
    val clearNextActionUrl: Boolean = false
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
    val previousState: CardRunState
)

internal data class RecipeStepCompletionRequest(
    val recipe: KiteRecipe,
    val instanceId: String,
    val generation: Long,
    val stepIndex: Int,
    val step: KiteRecipeStep,
    val state: CardRunState,
    val output: String
)

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
        val mutation: RunStateMutation
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

internal sealed interface RecipeStepCompletionResult {
    data class Ready(val output: String) : RecipeStepCompletionResult
    data class Failed(val message: String) : RecipeStepCompletionResult
}

internal enum class StopExecutionOutcome {
    Confirmed,
    Failed,
    Timeout,
    ConnectionError,
    Unsupported,
    ParseError
}

internal data class RecipeStopRequest(
    val recipe: KiteRecipe,
    val instanceId: String,
    val runId: String? = null,
    val terminalSessionId: String? = null,
    val pid: String? = null,
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
    val interruptTerminal: Boolean = false
)

internal data class StopExecutionResult(
    val outcome: StopExecutionOutcome,
    val message: String = "",
    val remainingProcessIds: List<String> = emptyList(),
    val manualKillObserved: Boolean = false
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
