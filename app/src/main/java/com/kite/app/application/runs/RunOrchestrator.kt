package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface

/**
 * 进程级运行编排器。它只管理步骤顺序、实例代次和运行事实，不知道页面是否可见。
 */
internal class RunOrchestrator(
    private val stateGateway: RunStateGateway,
    private val executor: RecipeExecutor,
    private val stopCoordinator: StopCoordinator = StopCoordinator(),
    private val effectSink: RunExecutionEffectSink = RunExecutionEffectSink { }
) {
    private data class Flight(val generation: Long, val stepIndex: Int)

    private val lock = Any()
    private val executionFlights = mutableMapOf<String, Flight>()
    private val completionFlights = mutableMapOf<String, Flight>()

    fun start(request: RunStartRequest): RunCommandResult {
        val dispatch = synchronized(lock) {
            stateGateway.register(request.recipe)
            val existing = stateGateway.state(request.instanceId)
            if (existing != null && existing.recipeId != request.recipe.id) {
                return RunCommandResult.Ignored("instance_recipe_conflict")
            }
            if (existing != null && existing.status.endsExecutionGeneration()) {
                clearFlights(existing.instanceId)
            }
            if (existing != null && existing.status.preventsDuplicateStart()) {
                return RunCommandResult.Ignored("instance_already_active")
            }
            val started = stateGateway.start(request)
            val firstStep = request.recipe.steps.firstOrNull()
            if (firstStep == null) {
                stateGateway.update(
                    request.recipe,
                    started.instanceId,
                    RunStateMutation(
                        status = CardRunStatus.Completed,
                        surface = CardRunSurface.Report,
                        currentStepIndex = 0,
                        lastMeaningfulOutput = "流程已完成"
                    )
                )
                return RunCommandResult.Accepted(started.instanceId)
            }
            stateGateway.update(
                request.recipe,
                started.instanceId,
                RunStateMutation(
                    status = CardRunStatus.Starting,
                    surface = surfaceFor(firstStep.type),
                    currentStepIndex = 0,
                    lastMeaningfulOutput = "正在启动流程"
                )
            )
            Dispatch(started.instanceId, started.createdAt, 0)
        }
        dispatch(dispatch)
        return RunCommandResult.Accepted(dispatch.instanceId)
    }

    fun completeCurrentStep(instanceId: String, output: String): RunCommandResult {
        val completion = synchronized(lock) {
            val state = stateGateway.state(instanceId)
                ?: return RunCommandResult.Ignored("missing_instance")
            val recipe = stateGateway.recipe(state.recipeId)
                ?: return RunCommandResult.Ignored("missing_recipe")
            val step = recipe.steps.getOrNull(state.currentStepIndex)
                ?: return RunCommandResult.Ignored("missing_step")
            if (!state.canCompleteCurrentStep()) {
                return RunCommandResult.Ignored("step_not_waiting")
            }
            val flight = Flight(state.createdAt, state.currentStepIndex)
            if (completionFlights.putIfAbsent(instanceId, flight) != null) {
                return RunCommandResult.Ignored("completion_in_flight")
            }
            val request = RecipeStepCompletionRequest(
                recipe = recipe,
                instanceId = instanceId,
                generation = state.createdAt,
                stepIndex = state.currentStepIndex,
                step = step,
                state = state,
                output = output
            )
            executionFlights.remove(instanceId)
            stateGateway.update(
                recipe,
                instanceId,
                RunStateMutation(
                    status = CardRunStatus.Running,
                    surface = if (
                        step.type == KiteRecipe.STEP_TERMINAL ||
                        step.type == KiteRecipe.STEP_OPEN_WEB
                    ) {
                        CardRunSurface.Report
                    } else {
                        state.surface
                    },
                    currentStepIndex = state.currentStepIndex,
                    lastMeaningfulOutput = "正在完成当前步骤",
                    clearRunBinding = step.type == KiteRecipe.STEP_TERMINAL && state.hasOnlyTerminalRunBinding(),
                    clearTerminalSession = step.type == KiteRecipe.STEP_TERMINAL,
                    clearNextActionUrl = step.type == KiteRecipe.STEP_OPEN_WEB
                )
            )
            Completion(request)
        }
        executor.completeWaitingStep(completion.request) { result ->
            handleCompletionResult(completion.request, result)
        }
        return RunCommandResult.Accepted(instanceId)
    }

    fun stop(instanceId: String): RunCommandResult {
        val action = synchronized(lock) {
            val state = stateGateway.state(instanceId)
                ?: return RunCommandResult.Ignored("missing_instance")
            val recipe = stateGateway.recipe(state.recipeId)
                ?: return RunCommandResult.Ignored("missing_recipe")
            when (val plan = stopCoordinator.plan(recipe, state)) {
                is StopPlan.Ignore -> return RunCommandResult.Ignored(plan.reason)
                is StopPlan.CompleteLocally -> {
                    clearFlights(instanceId)
                    stateGateway.update(recipe, instanceId, stoppedMutation(plan.summary))
                    effectSink.emit(
                        RunExecutionEffect.StopResolved(
                            instanceId = instanceId,
                            recipeId = recipe.id,
                            stopped = true,
                            message = plan.summary
                        )
                    )
                    return RunCommandResult.Accepted(instanceId)
                }
                is StopPlan.Execute -> {
                    clearFlights(instanceId)
                    stateGateway.update(
                        recipe,
                        instanceId,
                        RunStateMutation(
                            status = CardRunStatus.Stopping,
                            currentStepIndex = state.currentStepIndex,
                            runId = state.runId,
                            terminalSessionId = state.terminalSessionId,
                            pid = state.pid,
                            rootPid = state.rootPid,
                            processGroupId = state.processGroupId,
                            systemSessionId = state.systemSessionId,
                            lastMeaningfulOutput = state.lastMeaningfulOutput,
                            nextActionUrl = state.nextActionUrl,
                            x11Display = state.x11Display,
                            x11SocketPath = state.x11SocketPath
                        )
                    )
                    StopExecution(state, plan.request)
                }
            }
        }
        executor.stop(action.request) { result -> handleStopResult(action.previousState, result) }
        return RunCommandResult.Accepted(instanceId)
    }

    private fun dispatch(dispatch: Dispatch) {
        val execution = synchronized(lock) {
            val state = stateGateway.state(dispatch.instanceId) ?: return
            if (state.createdAt != dispatch.generation || state.status.stopsExecution()) return
            val recipe = stateGateway.recipe(state.recipeId) ?: return
            val step = recipe.steps.getOrNull(dispatch.stepIndex)
            if (step == null) {
                stateGateway.update(
                    recipe,
                    state.instanceId,
                    RunStateMutation(
                        status = finishedStatus(state),
                        surface = CardRunSurface.Report,
                        currentStepIndex = dispatch.stepIndex,
                        lastMeaningfulOutput = state.lastMeaningfulOutput ?: "流程已完成"
                    )
                )
                return
            }
            val flight = Flight(dispatch.generation, dispatch.stepIndex)
            if (executionFlights.putIfAbsent(state.instanceId, flight) != null) return
            val running = stateGateway.update(
                recipe,
                state.instanceId,
                RunStateMutation(
                    status = CardRunStatus.Running,
                    surface = surfaceFor(step.type),
                    currentStepIndex = dispatch.stepIndex,
                    lastMeaningfulOutput = stepStartingMessage(step.type),
                    clearNextActionUrl = true
                )
            )
            RecipeStepExecutionRequest(
                recipe = recipe,
                instanceId = state.instanceId,
                generation = dispatch.generation,
                stepIndex = dispatch.stepIndex,
                step = step,
                previousState = running
            )
        }
        executor.execute(execution, ::handleExecutionEvent)
    }

    private fun handleExecutionEvent(event: RecipeExecutionEvent) {
        val nextDispatch = synchronized(lock) {
            val state = validStateFor(event) ?: return
            val recipe = stateGateway.recipe(state.recipeId) ?: return
            when (event) {
                is RecipeExecutionEvent.Progress -> {
                    stateGateway.update(recipe, state.instanceId, event.mutation)
                    null
                }
                is RecipeExecutionEvent.AwaitingUser -> {
                    stateGateway.update(recipe, state.instanceId, event.mutation)
                    event.effect?.let(effectSink::emit)
                    null
                }
                is RecipeExecutionEvent.Failed -> {
                    executionFlights.remove(state.instanceId)
                    val supplied = event.mutation
                    stateGateway.update(
                        recipe,
                        state.instanceId,
                        supplied ?: RunStateMutation(
                            status = if (event.bridgeUnavailable) {
                                CardRunStatus.BridgeUnavailable
                            } else {
                                CardRunStatus.Failed
                            },
                            surface = CardRunSurface.Report,
                            currentStepIndex = event.stepIndex,
                            lastError = event.message
                        )
                    )
                    null
                }
                is RecipeExecutionEvent.Completed -> {
                    executionFlights.remove(state.instanceId)
                    val updated = stateGateway.update(recipe, state.instanceId, event.mutation)
                    Dispatch(updated.instanceId, event.generation, event.stepIndex + 1)
                }
            }
        }
        nextDispatch?.let(::dispatch)
    }

    private fun handleCompletionResult(
        request: RecipeStepCompletionRequest,
        result: RecipeStepCompletionResult
    ) {
        val nextDispatch = synchronized(lock) {
            completionFlights.remove(request.instanceId)
            executionFlights.remove(request.instanceId)
            val state = stateGateway.state(request.instanceId) ?: return
            if (state.createdAt != request.generation || state.status.stopsExecution()) return
            val recipe = stateGateway.recipe(state.recipeId) ?: return
            when (result) {
                is RecipeStepCompletionResult.Failed -> {
                    stateGateway.update(
                        recipe,
                        state.instanceId,
                        RunStateMutation(
                            status = CardRunStatus.Failed,
                            surface = request.state.surface,
                            currentStepIndex = request.stepIndex,
                            runId = request.state.runId,
                            terminalSessionId = request.state.terminalSessionId,
                            pid = request.state.pid,
                            rootPid = request.state.rootPid,
                            processGroupId = request.state.processGroupId,
                            systemSessionId = request.state.systemSessionId,
                            nextActionUrl = request.state.nextActionUrl,
                            x11Display = request.state.x11Display,
                            x11SocketPath = request.state.x11SocketPath,
                            lastError = result.message
                        )
                    )
                    null
                }
                is RecipeStepCompletionResult.Ready -> {
                    val cleared = stateGateway.update(
                        recipe,
                        state.instanceId,
                        RunStateMutation(
                            status = CardRunStatus.Running,
                            currentStepIndex = request.stepIndex,
                            lastMeaningfulOutput = result.output,
                            clearTerminalSession = request.step.type == KiteRecipe.STEP_TERMINAL,
                            clearNextActionUrl = request.step.type == KiteRecipe.STEP_OPEN_WEB
                        )
                    )
                    val nextStepIndex = request.stepIndex + 1
                    if (nextStepIndex >= recipe.steps.size) {
                        stateGateway.update(
                            recipe,
                            state.instanceId,
                            RunStateMutation(
                                status = finishedStatus(cleared),
                                surface = CardRunSurface.Report,
                                currentStepIndex = nextStepIndex,
                                lastMeaningfulOutput = result.output
                            )
                        )
                        null
                    } else {
                        Dispatch(state.instanceId, request.generation, nextStepIndex)
                    }
                }
            }
        }
        nextDispatch?.let(::dispatch)
    }

    private fun handleStopResult(previousState: CardRunState, result: StopExecutionResult) {
        synchronized(lock) {
            val current = stateGateway.state(previousState.instanceId) ?: return
            if (current.createdAt != previousState.createdAt || current.status != CardRunStatus.Stopping) return
            val recipe = stateGateway.recipe(previousState.recipeId) ?: return
            when (val resolution = stopCoordinator.resolve(previousState, result)) {
                is StopResolution.Stopped -> {
                    stateGateway.update(recipe, previousState.instanceId, stoppedMutation(resolution.summary))
                    effectSink.emit(
                        RunExecutionEffect.StopResolved(
                            instanceId = previousState.instanceId,
                            recipeId = recipe.id,
                            stopped = true,
                            message = resolution.summary
                        )
                    )
                }
                is StopResolution.Restore -> {
                    stateGateway.update(
                        recipe,
                        previousState.instanceId,
                        RunStateMutation(
                            status = resolution.status,
                            surface = previousState.surface,
                            currentStepIndex = previousState.currentStepIndex,
                            runId = previousState.runId,
                            terminalSessionId = previousState.terminalSessionId,
                            pid = previousState.pid,
                            rootPid = previousState.rootPid,
                            processGroupId = previousState.processGroupId,
                            systemSessionId = previousState.systemSessionId,
                            lastMeaningfulOutput = previousState.lastMeaningfulOutput,
                            lastError = resolution.error,
                            nextActionUrl = previousState.nextActionUrl,
                            x11Display = previousState.x11Display,
                            x11SocketPath = previousState.x11SocketPath
                        )
                    )
                    effectSink.emit(
                        RunExecutionEffect.StopResolved(
                            instanceId = previousState.instanceId,
                            recipeId = recipe.id,
                            stopped = false,
                            message = resolution.error
                        )
                    )
                }
            }
        }
    }

    private fun validStateFor(event: RecipeExecutionEvent): CardRunState? {
        val expected = executionFlights[event.instanceId] ?: return null
        if (expected.generation != event.generation || expected.stepIndex != event.stepIndex) return null
        val state = stateGateway.state(event.instanceId) ?: return null
        if (
            state.createdAt != event.generation ||
            state.currentStepIndex != event.stepIndex ||
            state.status.stopsExecution()
        ) return null
        return state
    }

    private fun clearFlights(instanceId: String) {
        executionFlights.remove(instanceId)
        completionFlights.remove(instanceId)
    }

    private data class Dispatch(val instanceId: String, val generation: Long, val stepIndex: Int)
    private data class Completion(val request: RecipeStepCompletionRequest)
    private data class StopExecution(val previousState: CardRunState, val request: RecipeStopRequest)

    companion object {
        fun surfaceFor(stepType: String): CardRunSurface = when (stepType) {
            KiteRecipe.STEP_SHELL,
            KiteRecipe.STEP_ANDROID_ACTION -> CardRunSurface.Report
            KiteRecipe.STEP_TERMINAL -> CardRunSurface.Terminal
            KiteRecipe.STEP_OPEN_WEB -> CardRunSurface.Web
            KiteRecipe.STEP_X11 -> CardRunSurface.X11
            else -> CardRunSurface.Summary
        }

        private fun stepStartingMessage(stepType: String): String = when (stepType) {
            KiteRecipe.STEP_SHELL -> "正在执行 SH"
            KiteRecipe.STEP_TERMINAL -> "正在创建终端"
            KiteRecipe.STEP_OPEN_WEB -> "正在打开网页"
            KiteRecipe.STEP_X11 -> "正在准备 X11"
            KiteRecipe.STEP_ANDROID_ACTION -> "正在执行安卓动作"
            else -> "正在执行步骤"
        }

        private fun CardRunStatus.preventsDuplicateStart(): Boolean =
            this == CardRunStatus.Running ||
                this == CardRunStatus.WaitingTerminal ||
                this == CardRunStatus.AlreadyRunning ||
                this == CardRunStatus.Opened ||
                this == CardRunStatus.Stopping

        private fun CardRunStatus.endsExecutionGeneration(): Boolean =
            this == CardRunStatus.Stopped ||
                this == CardRunStatus.Completed ||
                this == CardRunStatus.Failed ||
                this == CardRunStatus.BridgeUnavailable ||
                this == CardRunStatus.Unknown

        private fun CardRunStatus.stopsExecution(): Boolean =
            this == CardRunStatus.Stopping || this == CardRunStatus.Stopped

        private fun CardRunState.canCompleteCurrentStep(): Boolean =
            status == CardRunStatus.WaitingTerminal ||
                status == CardRunStatus.Opened ||
                status == CardRunStatus.Running ||
                status == CardRunStatus.AlreadyRunning

        private fun CardRunState.hasOnlyTerminalRunBinding(): Boolean =
            !terminalSessionId.isNullOrBlank() &&
                (runId.isNullOrBlank() || runId == terminalSessionId) &&
                pid.isNullOrBlank() &&
                rootPid.isNullOrBlank() &&
                processGroupId.isNullOrBlank() &&
                systemSessionId.isNullOrBlank()

        private fun finishedStatus(state: CardRunState): CardRunStatus =
            if (listOf(
                    state.pid,
                    state.rootPid,
                    state.processGroupId,
                    state.systemSessionId,
                    state.x11Display
                ).any { !it.isNullOrBlank() }
            ) {
                CardRunStatus.Running
            } else {
                CardRunStatus.Completed
            }

        private fun stoppedMutation(summary: String): RunStateMutation = RunStateMutation(
            status = CardRunStatus.Stopped,
            surface = CardRunSurface.Summary,
            lastMeaningfulOutput = summary,
            clearRunBinding = true,
            clearTerminalSession = true,
            clearNextActionUrl = true
        )
    }
}
