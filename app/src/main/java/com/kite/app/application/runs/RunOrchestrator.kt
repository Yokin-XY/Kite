package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.foundation.runtime.RuntimeOwnerIdentity
import com.kite.app.foundation.runtime.RuntimeOwnerNamespace
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import java.util.concurrent.atomic.AtomicLong

/**
 * 进程级运行编排器。它只管理步骤顺序、实例代次和运行事实，不知道页面是否可见。
 */
internal class RunOrchestrator(
    private val stateGateway: RunStateGateway,
    private val executor: RecipeExecutor,
    private val stopCoordinator: StopCoordinator = StopCoordinator(),
    private val effectSink: RunExecutionEffectSink = RunExecutionEffectSink { },
    private val lifecycleSink: RunLifecycleSink = RunLifecycleSink { },
    private val startGate: RunStartGate = RunStartGate.Allow,
    private val ownedWindowGateway: RunOwnedWindowGateway = RunOwnedWindowGateway.None
) {
    private data class Flight(val generation: Long, val stepIndex: Int, val attemptId: Long)

    private val lock = Any()
    private val executionFlights = mutableMapOf<String, Flight>()
    private val completionFlights = mutableMapOf<String, Flight>()
    private val attemptIds = AtomicLong()

    fun start(request: RunStartRequest): RunCommandResult {
        startRejection()?.let { return it }
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
                commit(
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
            commit(
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

    fun startRejection(): RunCommandResult.Ignored? =
        startGate.rejectionReason()?.let(RunCommandResult::Ignored)

    fun completeStep(command: RunStepCompletionCommand): RunCommandResult {
        val completion = synchronized(lock) {
            val state = stateGateway.state(command.instanceId)
                ?: return RunCommandResult.Ignored("missing_instance")
            val recipe = stateGateway.recipe(state.recipeId)
                ?: return RunCommandResult.Ignored("missing_recipe")
            val step = recipe.steps.getOrNull(state.currentStepIndex)
                ?: return RunCommandResult.Ignored("missing_step")
            if (state.createdAt != command.expectedGeneration) {
                return RunCommandResult.Ignored("generation_mismatch")
            }
            if (state.currentStepIndex != command.expectedStepIndex) {
                return RunCommandResult.Ignored("step_index_mismatch")
            }
            if (step.id != command.expectedStepId) {
                return RunCommandResult.Ignored("step_id_mismatch")
            }
            if (!RunStepActionPolicy.canComplete(recipe, state)) {
                return RunCommandResult.Ignored("step_not_waiting")
            }
            val flight = Flight(state.createdAt, state.currentStepIndex, attemptIds.incrementAndGet())
            if (completionFlights.putIfAbsent(command.instanceId, flight) != null) {
                return RunCommandResult.Ignored("completion_in_flight")
            }
            val request = RecipeStepCompletionRequest(
                recipe = recipe,
                instanceId = command.instanceId,
                generation = state.createdAt,
                stepIndex = state.currentStepIndex,
                step = step,
                state = state,
                output = command.output
            )
            executionFlights.remove(command.instanceId)
            commit(
                recipe,
                command.instanceId,
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
        return RunCommandResult.Accepted(command.instanceId)
    }

    fun restartStep(command: RunStepRestartCommand): RunCommandResult {
        val restart = synchronized(lock) {
            val state = stateGateway.state(command.instanceId)
                ?: return RunCommandResult.Ignored("missing_instance")
            val recipe = stateGateway.recipe(state.recipeId)
                ?: return RunCommandResult.Ignored("missing_recipe")
            val step = recipe.steps.getOrNull(command.expectedStepIndex)
                ?: return RunCommandResult.Ignored("missing_step")
            if (state.createdAt != command.expectedGeneration) {
                return RunCommandResult.Ignored("generation_mismatch")
            }
            if (state.currentStepIndex != command.expectedStepIndex) {
                return RunCommandResult.Ignored("step_not_current")
            }
            if (step.id != command.expectedStepId) {
                return RunCommandResult.Ignored("step_id_mismatch")
            }
            if (state.status == CardRunStatus.Stopping || state.status == CardRunStatus.Stopped) {
                return RunCommandResult.Ignored("instance_stopping")
            }
            clearFlights(state.instanceId)
            commit(
                recipe,
                state.instanceId,
                RunStateMutation(
                    status = CardRunStatus.Starting,
                    surface = surfaceFor(step.type),
                    currentStepIndex = state.currentStepIndex,
                    lastMeaningfulOutput = restartMessage(step.type),
                    clearRunBinding = step.type == KiteRecipe.STEP_SHELL || step.type == KiteRecipe.STEP_X11,
                    clearTerminalSession = step.type == KiteRecipe.STEP_TERMINAL,
                    clearNextActionUrl = step.type == KiteRecipe.STEP_OPEN_WEB
                )
            )
            Restart(
                dispatch = Dispatch(state.instanceId, state.createdAt, state.currentStepIndex),
                previousState = state,
                stopRequest = restartStopRequest(recipe, state, step.type)
            )
        }
        val stopRequest = restart.stopRequest
        if (stopRequest == null) {
            dispatch(restart.dispatch)
        } else {
            executor.stop(stopRequest) { result -> handleRestartStopResult(restart, result) }
        }
        return RunCommandResult.Accepted(command.instanceId)
    }

    fun stop(instanceId: String): RunCommandResult {
        val preparation = synchronized(lock) {
            val state = stateGateway.state(instanceId)
                ?: return RunCommandResult.Ignored("missing_instance")
            val recipe = stateGateway.recipe(state.recipeId)
                ?: return RunCommandResult.Ignored("missing_recipe")
            when (val plan = stopCoordinator.plan(recipe, state)) {
                is StopPlan.Ignore -> return RunCommandResult.Ignored(plan.reason)
                is StopPlan.CompleteLocally -> {
                    clearFlights(instanceId)
                    commit(recipe, instanceId, stoppingMutation(state))
                    StopPreparation(
                        previousState = state,
                        recipe = recipe,
                        localSummary = plan.summary
                    )
                }
                is StopPlan.Execute -> {
                    clearFlights(instanceId)
                    commit(recipe, instanceId, stoppingMutation(state))
                    StopPreparation(
                        previousState = state,
                        recipe = recipe,
                        request = plan.request
                    )
                }
            }
        }
        ownedWindowGateway.closeAll(
            instanceId = instanceId,
            expectedGeneration = preparation.previousState.createdAt
        ) { result ->
            handleOwnedWindowsCloseResult(preparation, result)
        }
        return RunCommandResult.Accepted(instanceId)
    }

    private fun handleOwnedWindowsCloseResult(
        preparation: StopPreparation,
        result: RunOwnedWindowsCloseResult
    ) {
        val execution = synchronized(lock) {
            val previous = preparation.previousState
            val current = stateGateway.state(previous.instanceId) ?: return
            if (current.createdAt != previous.createdAt || current.status != CardRunStatus.Stopping) return
            if (!result.confirmed) {
                val remaining = result.remainingInstanceIds
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                val error = result.message.ifBlank {
                    if (remaining.isEmpty()) {
                        "子窗口未确认关闭，父实例未停止"
                    } else {
                        "子窗口未确认关闭：${remaining.joinToString(",")}"
                    }
                }
                commit(
                    preparation.recipe,
                    previous.instanceId,
                    restoreAfterStopFailure(previous, error)
                )
                effectSink.emit(
                    RunExecutionEffect.StopResolved(
                        instanceId = previous.instanceId,
                        recipeId = preparation.recipe.id,
                        stopped = false,
                        message = error
                    )
                )
                return
            }
            val localSummary = preparation.localSummary
            if (localSummary != null) {
                commit(preparation.recipe, previous.instanceId, stoppedMutation(localSummary))
                effectSink.emit(
                    RunExecutionEffect.StopResolved(
                        instanceId = previous.instanceId,
                        recipeId = preparation.recipe.id,
                        stopped = true,
                        message = localSummary
                    )
                )
                null
            } else {
                preparation.request?.let { StopExecution(previous, it) }
            }
        }
        execution?.let { action ->
            executor.stop(action.request) { stopResult ->
                handleStopResult(action.previousState, stopResult)
            }
        }
    }

    private fun dispatch(dispatch: Dispatch) {
        val execution = synchronized(lock) {
            val state = stateGateway.state(dispatch.instanceId) ?: return
            if (state.createdAt != dispatch.generation || state.status.stopsExecution()) return
            val recipe = stateGateway.recipe(state.recipeId) ?: return
            val step = recipe.steps.getOrNull(dispatch.stepIndex)
            if (step == null) {
                val finalStatus = finishedStatus(recipe, state)
                commit(
                    recipe,
                    state.instanceId,
                    RunStateMutation(
                        status = finalStatus,
                        surface = CardRunSurface.Report,
                        currentStepIndex = dispatch.stepIndex,
                        lastMeaningfulOutput = state.lastMeaningfulOutput ?: "流程已完成",
                        clearRunBinding = finalStatus == CardRunStatus.Completed
                    )
                )
                return
            }
            val flight = Flight(dispatch.generation, dispatch.stepIndex, attemptIds.incrementAndGet())
            if (executionFlights.putIfAbsent(state.instanceId, flight) != null) return
            val runtimeOwner = RuntimeOwnerIdentity.step(
                namespace = runtimeNamespace(recipe, state),
                instanceId = state.instanceId,
                generation = dispatch.generation,
                stepIndex = dispatch.stepIndex,
                stepId = step.id,
                attemptId = flight.attemptId
            )
            val running = commit(
                recipe,
                state.instanceId,
                RunStateMutation(
                    status = CardRunStatus.Running,
                    surface = surfaceFor(step.type),
                    currentStepIndex = dispatch.stepIndex,
                    runtimeRootOwnerId = runtimeOwner.rootOwnerId,
                    runtimeOwnerId = runtimeOwner.ownerId,
                    runtimeUnitId = runtimeOwner.unitId,
                    lastMeaningfulOutput = stepStartingMessage(step.type),
                    clearNextActionUrl = true
                )
            )
            Execution(
                request = RecipeStepExecutionRequest(
                    recipe = recipe,
                    instanceId = state.instanceId,
                    generation = dispatch.generation,
                    stepIndex = dispatch.stepIndex,
                    step = step,
                    previousState = running,
                    attemptId = flight.attemptId,
                    runtimeRootOwnerId = runtimeOwner.rootOwnerId,
                    runtimeOwnerId = runtimeOwner.ownerId,
                    runtimeUnitId = runtimeOwner.unitId
                ),
                attemptId = flight.attemptId
            )
        }
        executor.execute(execution.request) { event ->
            handleExecutionEvent(event, execution.attemptId)
        }
    }

    private fun handleExecutionEvent(event: RecipeExecutionEvent, attemptId: Long) {
        val nextDispatch = synchronized(lock) {
            val state = validStateFor(event, attemptId) ?: return
            val recipe = stateGateway.recipe(state.recipeId) ?: return
            when (event) {
                is RecipeExecutionEvent.Progress -> {
                    commit(recipe, state.instanceId, event.mutation)
                    null
                }
                is RecipeExecutionEvent.AwaitingUser -> {
                    commit(recipe, state.instanceId, event.mutation)
                    event.effect?.let(effectSink::emit)
                    null
                }
                is RecipeExecutionEvent.Failed -> {
                    executionFlights.remove(state.instanceId)
                    val supplied = event.mutation
                    commit(
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
                    val updated = commit(recipe, state.instanceId, event.mutation)
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
                    commit(
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
                    val cleared = commit(
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
                        val finalStatus = finishedStatus(recipe, cleared)
                        commit(
                            recipe,
                            state.instanceId,
                            RunStateMutation(
                                status = finalStatus,
                                surface = CardRunSurface.Report,
                                currentStepIndex = nextStepIndex,
                                lastMeaningfulOutput = result.output,
                                clearRunBinding = finalStatus == CardRunStatus.Completed
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

    private fun handleRestartStopResult(restart: Restart, result: StopExecutionResult) {
        val shouldDispatch = synchronized(lock) {
            val state = stateGateway.state(restart.dispatch.instanceId) ?: return
            if (
                state.createdAt != restart.dispatch.generation ||
                state.currentStepIndex != restart.dispatch.stepIndex ||
                state.status == CardRunStatus.Stopping ||
                state.status == CardRunStatus.Stopped
            ) return
            val recipe = stateGateway.recipe(state.recipeId) ?: return
            if (result.outcome == StopExecutionOutcome.Confirmed) {
                true
            } else {
                val previous = restart.previousState
                commit(
                    recipe,
                    state.instanceId,
                    RunStateMutation(
                        status = CardRunStatus.Failed,
                        surface = previous.surface,
                        currentStepIndex = previous.currentStepIndex,
                        runtimeRootOwnerId = previous.runtimeRootOwnerId,
                        runtimeOwnerId = previous.runtimeOwnerId,
                        runtimeUnitId = previous.runtimeUnitId,
                        ownedRuntimeOwnerIds = previous.ownedRuntimeOwnerIds,
                        runId = previous.runId,
                        terminalSessionId = previous.terminalSessionId,
                        pid = previous.pid,
                        rootPid = previous.rootPid,
                        processGroupId = previous.processGroupId,
                        systemSessionId = previous.systemSessionId,
                        nextActionUrl = previous.nextActionUrl,
                        x11Display = previous.x11Display,
                        x11SocketPath = previous.x11SocketPath,
                        lastError = result.message.ifBlank { "旧步骤未确认停止，未开始重新执行" }
                    )
                )
                false
            }
        }
        if (shouldDispatch) dispatch(restart.dispatch)
    }

    private fun handleStopResult(previousState: CardRunState, result: StopExecutionResult) {
        synchronized(lock) {
            val current = stateGateway.state(previousState.instanceId) ?: return
            if (current.createdAt != previousState.createdAt || current.status != CardRunStatus.Stopping) return
            val recipe = stateGateway.recipe(previousState.recipeId) ?: return
            when (val resolution = stopCoordinator.resolve(previousState, result)) {
                is StopResolution.Stopped -> {
                    commit(recipe, previousState.instanceId, stoppedMutation(resolution.summary))
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
                    commit(
                        recipe,
                        previousState.instanceId,
                        RunStateMutation(
                            status = resolution.status,
                            surface = previousState.surface,
                            currentStepIndex = previousState.currentStepIndex,
                            runtimeRootOwnerId = previousState.runtimeRootOwnerId,
                            runtimeOwnerId = previousState.runtimeOwnerId,
                            runtimeUnitId = previousState.runtimeUnitId,
                            ownedRuntimeOwnerIds = previousState.ownedRuntimeOwnerIds,
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

    private fun validStateFor(event: RecipeExecutionEvent, attemptId: Long): CardRunState? {
        val expected = executionFlights[event.instanceId] ?: return null
        if (
            expected.generation != event.generation ||
            expected.stepIndex != event.stepIndex ||
            expected.attemptId != attemptId
        ) return null
        val state = stateGateway.state(event.instanceId) ?: return null
        if (
            state.createdAt != event.generation ||
            state.currentStepIndex != event.stepIndex ||
            state.status.stopsExecution()
        ) return null
        return state
    }

    private fun commit(
        recipe: KiteRecipe,
        instanceId: String,
        mutation: RunStateMutation
    ): CardRunState = stateGateway.update(recipe, instanceId, mutation).also { state ->
        lifecycleSink.onStateCommitted(RunLifecycleEvent(recipe, state))
    }

    private fun clearFlights(instanceId: String) {
        executionFlights.remove(instanceId)
        completionFlights.remove(instanceId)
    }

    private data class Dispatch(val instanceId: String, val generation: Long, val stepIndex: Int)
    private data class Execution(val request: RecipeStepExecutionRequest, val attemptId: Long)
    private data class Completion(val request: RecipeStepCompletionRequest)
    private data class StopExecution(val previousState: CardRunState, val request: RecipeStopRequest)
    private data class StopPreparation(
        val previousState: CardRunState,
        val recipe: KiteRecipe,
        val request: RecipeStopRequest? = null,
        val localSummary: String? = null
    )
    private data class Restart(
        val dispatch: Dispatch,
        val previousState: CardRunState,
        val stopRequest: RecipeStopRequest?
    )

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

        private fun restartMessage(stepType: String): String = when (stepType) {
            KiteRecipe.STEP_SHELL -> "正在重置并重新执行 SH"
            KiteRecipe.STEP_TERMINAL -> "正在关闭旧终端并创建新终端"
            KiteRecipe.STEP_OPEN_WEB -> "正在重新打开流程网页"
            KiteRecipe.STEP_X11 -> "正在重置 X11 窗口"
            KiteRecipe.STEP_ANDROID_ACTION -> "正在重新执行安卓动作"
            else -> "正在重新执行步骤"
        }

        private fun restartStopRequest(
            recipe: KiteRecipe,
            state: CardRunState,
            stepType: String
        ): RecipeStopRequest? = when (stepType) {
            KiteRecipe.STEP_TERMINAL -> state.terminalSessionId
                ?.takeIf { it.isNotBlank() }
                ?.let { sessionId ->
                    RecipeStopRequest(
                        recipe = recipe,
                        instanceId = state.instanceId,
                        generation = state.createdAt,
                        runtimeOwnerIds = state.ownedRuntimeOwnerIds,
                        runId = sessionId,
                        terminalSessionId = sessionId,
                        interruptTerminal = true
                    )
                }
            KiteRecipe.STEP_SHELL,
            KiteRecipe.STEP_X11 -> if (state.hasRunBinding()) {
                RecipeStopRequest(
                    recipe = recipe,
                    instanceId = state.instanceId,
                    generation = state.createdAt,
                    runtimeOwnerIds = state.ownedRuntimeOwnerIds,
                    runId = state.runId,
                    terminalSessionId = state.terminalSessionId,
                    pid = state.pid,
                    rootPid = state.rootPid,
                    processGroupId = state.processGroupId,
                    systemSessionId = state.systemSessionId
                )
            } else {
                null
            }
            else -> null
        }

        private fun runtimeNamespace(recipe: KiteRecipe, state: CardRunState): RuntimeOwnerNamespace =
            if (
                state.ownerKind == CardRunState.OWNER_KIND_RESOURCE ||
                state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD ||
                recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE
            ) {
                RuntimeOwnerNamespace.Resource
            } else {
                RuntimeOwnerNamespace.Card
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

        private fun CardRunState.hasOnlyTerminalRunBinding(): Boolean =
            !terminalSessionId.isNullOrBlank() &&
                (runId.isNullOrBlank() || runId == terminalSessionId) &&
                pid.isNullOrBlank() &&
                rootPid.isNullOrBlank() &&
                processGroupId.isNullOrBlank() &&
                systemSessionId.isNullOrBlank()

        private fun finishedStatus(recipe: KiteRecipe, state: CardRunState): CardRunStatus =
            if (recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE) {
                CardRunStatus.Completed
            } else if (listOf(
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

        private fun stoppingMutation(state: CardRunState): RunStateMutation = RunStateMutation(
            status = CardRunStatus.Stopping,
            currentStepIndex = state.currentStepIndex,
            runtimeRootOwnerId = state.runtimeRootOwnerId,
            runtimeOwnerId = state.runtimeOwnerId,
            runtimeUnitId = state.runtimeUnitId,
            ownedRuntimeOwnerIds = state.ownedRuntimeOwnerIds,
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

        private fun restoreAfterStopFailure(state: CardRunState, error: String): RunStateMutation = RunStateMutation(
            status = state.status,
            surface = state.surface,
            currentStepIndex = state.currentStepIndex,
            runtimeRootOwnerId = state.runtimeRootOwnerId,
            runtimeOwnerId = state.runtimeOwnerId,
            runtimeUnitId = state.runtimeUnitId,
            ownedRuntimeOwnerIds = state.ownedRuntimeOwnerIds,
            runId = state.runId,
            terminalSessionId = state.terminalSessionId,
            pid = state.pid,
            rootPid = state.rootPid,
            processGroupId = state.processGroupId,
            systemSessionId = state.systemSessionId,
            lastMeaningfulOutput = state.lastMeaningfulOutput,
            lastError = error,
            nextActionUrl = state.nextActionUrl,
            x11Display = state.x11Display,
            x11SocketPath = state.x11SocketPath
        )
    }
}
