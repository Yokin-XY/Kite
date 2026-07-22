package com.kite.app.application.runs

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOrchestratorTest {
    @Test
    fun `步骤执行请求携带实例代次叶子 owner 并写入运行事实`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe("owner-chain", KiteRecipe.STEP_SHELL)

        orchestrator.start(RunStartRequest(recipe, "owner-instance"))

        val request = executor.executeRequests.single()
        val state = gateway.state("owner-instance")!!
        assertEquals(state.createdAt, request.generation)
        assertTrue(request.runtimeRootOwnerId!!.startsWith("card:owner-instance@"))
        assertTrue(request.runtimeOwnerId!!.contains("/step/0-"))
        assertEquals(request.runtimeOwnerId, state.runtimeOwnerId)
        assertEquals(listOf(request.runtimeOwnerId), state.ownedRuntimeOwnerIds)
    }

    @Test
    fun `所有步骤类型按顺序进入同一个执行端口`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor(autoComplete = true)
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe(
            "all-steps",
            KiteRecipe.STEP_SHELL,
            KiteRecipe.STEP_TERMINAL,
            KiteRecipe.STEP_OPEN_WEB,
            KiteRecipe.STEP_X11,
            KiteRecipe.STEP_ANDROID_ACTION
        )

        val result = orchestrator.start(RunStartRequest(recipe, "instance-all"))

        assertEquals(RunCommandResult.Accepted("instance-all"), result)
        assertEquals(recipe.steps.map { it.type }, executor.executeRequests.map { it.step.type })
        assertEquals(CardRunStatus.Completed, gateway.state("instance-all")?.status)
        assertEquals(recipe.steps.size, gateway.state("instance-all")?.currentStepIndex)
    }

    @Test
    fun `同一实例执行未完成时拒绝建立第二条链`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe("single-flight", KiteRecipe.STEP_SHELL)

        val first = orchestrator.start(RunStartRequest(recipe, "same-instance"))
        val second = orchestrator.start(RunStartRequest(recipe, "same-instance"))

        assertEquals(RunCommandResult.Accepted("same-instance"), first)
        assertEquals(RunCommandResult.Ignored("instance_already_active"), second)
        assertEquals(1, executor.executeRequests.size)
    }

    @Test
    fun `有限资源流程完成后清除执行绑定并发布已提交事实`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val lifecycleEvents = mutableListOf<RunLifecycleEvent>()
        val orchestrator = RunOrchestrator(
            stateGateway = gateway,
            executor = executor,
            lifecycleSink = lifecycleEvents::add
        )
        val recipe = recipe("resource-finite", KiteRecipe.STEP_SHELL).copy(
            runtimeSource = KiteResourceInstallRecipes.RUNTIME_SOURCE
        )
        orchestrator.start(RunStartRequest(recipe, "resource-finite-instance"))
        val request = executor.executeRequests.single()

        executor.emit(
            RecipeExecutionEvent.Completed(
                instanceId = request.instanceId,
                generation = request.generation,
                stepIndex = request.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.Running,
                    currentStepIndex = request.stepIndex,
                    runId = "resource-run",
                    pid = "123",
                    rootPid = "123",
                    processGroupId = "123",
                    systemSessionId = "123",
                    lastMeaningfulOutput = "安装完成"
                )
            )
        )

        val state = gateway.state("resource-finite-instance")
        assertEquals(CardRunStatus.Completed, state?.status)
        assertEquals(null, state?.runId)
        assertEquals(null, state?.pid)
        assertEquals(CardRunStatus.Completed, lifecycleEvents.last().state.status)
        assertEquals("resource-finite-instance", lifecycleEvents.last().state.instanceId)
    }

    @Test
    fun `等待步骤只靠运行事实即可由新编排器继续`() {
        val gateway = FakeRunStateGateway()
        val firstExecutor = FakeRecipeExecutor()
        val recipe = recipe("resume", KiteRecipe.STEP_TERMINAL, KiteRecipe.STEP_SHELL)
        val first = RunOrchestrator(gateway, firstExecutor)
        first.start(RunStartRequest(recipe, "resume-instance"))
        val request = firstExecutor.executeRequests.single()
        firstExecutor.emit(
            RecipeExecutionEvent.AwaitingUser(
                instanceId = request.instanceId,
                generation = request.generation,
                stepIndex = request.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.WaitingTerminal,
                    surface = CardRunSurface.Terminal,
                    currentStepIndex = request.stepIndex,
                    terminalSessionId = "terminal-1",
                    lastMeaningfulOutput = "等待终端完成"
                )
            )
        )

        val resumedExecutor = FakeRecipeExecutor(autoComplete = true)
        val resumed = RunOrchestrator(gateway, resumedExecutor)
        val result = resumed.completeStep(
            RunStepActionPolicy.completionCommand(recipe, gateway.state("resume-instance")!!)!!
                .copy(output = "终端已完成")
        )

        assertEquals(RunCommandResult.Accepted("resume-instance"), result)
        assertEquals(listOf(KiteRecipe.STEP_SHELL), resumedExecutor.executeRequests.map { it.step.type })
        assertEquals(CardRunStatus.Completed, gateway.state("resume-instance")?.status)
        assertEquals(null, gateway.state("resume-instance")?.terminalSessionId)

        firstExecutor.emit(
            RecipeExecutionEvent.Completed(
                instanceId = request.instanceId,
                generation = request.generation,
                stepIndex = request.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.Running,
                    currentStepIndex = request.stepIndex,
                    terminalSessionId = "terminal-1",
                    lastMeaningfulOutput = "迟到的终端完成"
                )
            )
        )
        assertEquals(CardRunStatus.Completed, gateway.state("resume-instance")?.status)
        assertEquals(null, gateway.state("resume-instance")?.terminalSessionId)
    }

    @Test
    fun `完成等待步骤时先撤销旧执行回调再分派下一步`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor(emitExecutionWhileCompleting = true)
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe("completion-race", KiteRecipe.STEP_TERMINAL, KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "completion-race-instance"))
        val terminalRequest = executor.executeRequests.single()
        executor.emit(
            RecipeExecutionEvent.AwaitingUser(
                instanceId = terminalRequest.instanceId,
                generation = terminalRequest.generation,
                stepIndex = terminalRequest.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.WaitingTerminal,
                    surface = CardRunSurface.Terminal,
                    currentStepIndex = 0,
                    runId = "terminal-race",
                    terminalSessionId = "terminal-race"
                )
            )
        )

        orchestrator.completeStep(
            RunStepActionPolicy.completionCommand(recipe, gateway.state("completion-race-instance")!!)!!
                .copy(output = "终端完成")
        )

        assertEquals(2, executor.executeRequests.size)
        assertEquals(1, executor.executeRequests.last().stepIndex)
        assertEquals(null, gateway.state("completion-race-instance")?.terminalSessionId)
        assertEquals(null, gateway.state("completion-race-instance")?.runId)
    }

    @Test
    fun `停止确认后迟到的步骤结果不能覆盖停止事实`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe("late-result", KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "late-instance"))
        val request = executor.executeRequests.single()

        orchestrator.stop("late-instance")
        executor.emitStop(StopExecutionResult(StopExecutionOutcome.Confirmed, "已停止"))
        executor.emit(
            RecipeExecutionEvent.Completed(
                instanceId = request.instanceId,
                generation = request.generation,
                stepIndex = request.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.Running,
                    currentStepIndex = request.stepIndex,
                    runId = "late-run",
                    lastMeaningfulOutput = "迟到成功"
                )
            )
        )

        val state = gateway.state("late-instance")
        assertEquals(CardRunStatus.Stopped, state?.status)
        assertEquals("已关闭", state?.lastMeaningfulOutput)
        assertEquals(null, state?.runId)
    }

    @Test
    fun `父实例等待全部子窗口确认后才提交底层停止`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val ownedWindows = FakeRunOwnedWindowGateway()
        val orchestrator = RunOrchestrator(
            stateGateway = gateway,
            executor = executor,
            ownedWindowGateway = ownedWindows
        )
        val recipe = recipe("wait-children", KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "root-instance"))

        orchestrator.stop("root-instance")

        assertEquals(CardRunStatus.Stopping, gateway.state("root-instance")?.status)
        assertTrue(executor.stopRequests.isEmpty())
        assertEquals(listOf("root-instance"), ownedWindows.requests.map { it.first })

        ownedWindows.complete(RunOwnedWindowsCloseResult(confirmed = true))
        assertEquals(1, executor.stopRequests.size)
        executor.emitStop(StopExecutionResult(StopExecutionOutcome.Confirmed, "已停止"))
        assertEquals(CardRunStatus.Stopped, gateway.state("root-instance")?.status)
    }

    @Test
    fun `子窗口清理未确认时父实例保留绑定并进入待确认态`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val ownedWindows = FakeRunOwnedWindowGateway()
        val effects = mutableListOf<RunExecutionEffect>()
        val orchestrator = RunOrchestrator(
            stateGateway = gateway,
            executor = executor,
            effectSink = effects::add,
            ownedWindowGateway = ownedWindows
        )
        val recipe = recipe("child-failure", KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "root-instance"))

        orchestrator.stop("root-instance")
        ownedWindows.complete(
            RunOwnedWindowsCloseResult(
                confirmed = false,
                remainingInstanceIds = listOf("grandchild-instance")
            )
        )

        assertEquals(CardRunStatus.CleanupPending, gateway.state("root-instance")?.status)
        assertTrue(executor.stopRequests.isEmpty())
        assertEquals("仍有子进程未结束，请重试", gateway.state("root-instance")?.lastError)
        assertEquals(false, (effects.single() as RunExecutionEffect.StopResolved).stopped)
    }

    @Test
    fun `旧代次的子窗口关闭回调不能停止新代次实例`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val ownedWindows = FakeRunOwnedWindowGateway()
        val orchestrator = RunOrchestrator(
            stateGateway = gateway,
            executor = executor,
            ownedWindowGateway = ownedWindows
        )
        val recipe = recipe("stale-child-close", KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "same-instance"))
        val oldGeneration = gateway.state("same-instance")!!.createdAt
        orchestrator.stop("same-instance")
        gateway.seed(
            gateway.state("same-instance")!!.copy(
                status = CardRunStatus.Running,
                createdAt = oldGeneration + 100,
                updatedAt = oldGeneration + 100
            )
        )

        ownedWindows.complete(RunOwnedWindowsCloseResult(confirmed = true))

        assertTrue(executor.stopRequests.isEmpty())
        assertEquals(oldGeneration + 100, gateway.state("same-instance")?.createdAt)
        assertEquals(CardRunStatus.Running, gateway.state("same-instance")?.status)
    }

    @Test
    fun `停止仍有残留时不清除运行绑定`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val effects = mutableListOf<RunExecutionEffect>()
        val orchestrator = RunOrchestrator(gateway, executor, effectSink = effects::add)
        val recipe = recipe("residue", KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "residue-instance"))
        val request = executor.executeRequests.single()
        executor.emit(
            RecipeExecutionEvent.Progress(
                instanceId = request.instanceId,
                generation = request.generation,
                stepIndex = request.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.Running,
                    currentStepIndex = request.stepIndex,
                    runId = "run-1",
                    pid = "100"
                )
            )
        )

        orchestrator.stop("residue-instance")
        executor.emitStop(
            StopExecutionResult(
                outcome = StopExecutionOutcome.Failed,
                remainingProcessIds = listOf("100", "100", "bad")
            )
        )

        val state = gateway.state("residue-instance")
        assertEquals(CardRunStatus.CleanupPending, state?.status)
        assertEquals("未能确认所有进程已经结束，请重试", state?.lastError)
        assertEquals("run-1", state?.runId)
        assertEquals("100", state?.pid)
        assertEquals(
            RunExecutionEffect.StopResolved(
                instanceId = "residue-instance",
                recipeId = recipe.id,
                stopped = false,
                message = "未能确认所有进程已经结束，请重试"
            ),
            effects.single()
        )
    }

    @Test
    fun `Bridge 返回失败时即使残留标记为空也不冒充确认`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe("force-kill", KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "force-kill-instance"))
        val request = executor.executeRequests.single()
        executor.emit(
            RecipeExecutionEvent.Progress(
                instanceId = request.instanceId,
                generation = request.generation,
                stepIndex = request.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.Running,
                    currentStepIndex = request.stepIndex,
                    runId = "force-run",
                    pid = "9570"
                )
            )
        )

        orchestrator.stop("force-kill-instance")
        executor.emitStop(
            StopExecutionResult(
                outcome = StopExecutionOutcome.Failed,
                message = "__kite_stop_mode:force-kill\n__kite_stop_remaining:",
                residueMarkerObserved = true
            )
        )

        val state = gateway.state("force-kill-instance")
        assertEquals(CardRunStatus.CleanupPending, state?.status)
        assertEquals("__kite_stop_mode:force-kill\n__kite_stop_remaining:", state?.lastError)
        assertEquals("force-run", state?.runId)
        assertEquals("9570", state?.pid)
    }

    @Test
    fun `重新执行不等待旧 owner 完全退出并使用新叶子 owner`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe("restart-residue", KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "restart-instance"))
        val firstRequest = executor.executeRequests.single()
        executor.emit(
            RecipeExecutionEvent.Progress(
                instanceId = firstRequest.instanceId,
                generation = firstRequest.generation,
                stepIndex = firstRequest.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.Running,
                    currentStepIndex = firstRequest.stepIndex,
                    runId = "old-run",
                    pid = "100"
                )
            )
        )
        val state = gateway.state("restart-instance")!!

        orchestrator.restartStep(
            RunStepRestartCommand(
                instanceId = state.instanceId,
                expectedGeneration = state.createdAt,
                expectedStepIndex = state.currentStepIndex,
                expectedStepId = recipe.steps.single().id
            )
        )
        executor.emitStop(
            StopExecutionResult(
                outcome = StopExecutionOutcome.VerificationUnavailable,
                remainingProcessIds = listOf("100")
            )
        )

        assertEquals(2, executor.executeRequests.size)
        assertTrue(executor.executeRequests.last().runtimeOwnerId != firstRequest.runtimeOwnerId)
        assertEquals(CardRunStatus.Running, gateway.state("restart-instance")?.status)
        assertEquals(null, gateway.state("restart-instance")?.runId)
    }

    @Test
    fun `无运行绑定的实例本地闭合不调用执行核心`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val effects = mutableListOf<RunExecutionEffect>()
        val recipe = recipe("web-local", KiteRecipe.STEP_OPEN_WEB)
        gateway.register(recipe)
        gateway.seed(
            CardRunState(
                instanceId = "web-instance",
                recipeId = recipe.id,
                recipeName = recipe.name,
                status = CardRunStatus.Opened,
                surface = CardRunSurface.Web,
                currentStepIndex = 0,
                nextActionUrl = "http://127.0.0.1:8080",
                createdAt = 10L,
                updatedAt = 10L
            )
        )
        val orchestrator = RunOrchestrator(gateway, executor, effectSink = effects::add)

        val result = orchestrator.stop("web-instance")

        assertEquals(RunCommandResult.Accepted("web-instance"), result)
        assertTrue(executor.stopRequests.isEmpty())
        assertEquals(CardRunStatus.Stopped, gateway.state("web-instance")?.status)
        assertEquals(null, gateway.state("web-instance")?.nextActionUrl)
        assertEquals(
            RunExecutionEffect.StopResolved(
                instanceId = "web-instance",
                recipeId = recipe.id,
                stopped = true,
                message = "已关闭"
            ),
            effects.single()
        )
    }

    @Test
    fun `尚未取得运行绑定的启动实例也能关闭`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val recipe = recipe("starting-local", KiteRecipe.STEP_TERMINAL)
        gateway.register(recipe)
        gateway.seed(
            CardRunState(
                instanceId = "starting-instance",
                recipeId = recipe.id,
                recipeName = recipe.name,
                status = CardRunStatus.Starting,
                surface = CardRunSurface.Terminal,
                currentStepIndex = 0,
                createdAt = 11L,
                updatedAt = 11L
            )
        )
        val orchestrator = RunOrchestrator(gateway, executor)

        val result = orchestrator.stop("starting-instance")

        assertEquals(RunCommandResult.Accepted("starting-instance"), result)
        assertTrue(executor.stopRequests.isEmpty())
        assertEquals(CardRunStatus.Stopped, gateway.state("starting-instance")?.status)
    }

    @Test
    fun `终端自己的会话标识不能被当作 Bridge 运行绑定`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val recipe = recipe("terminal-stop", KiteRecipe.STEP_TERMINAL)
        gateway.register(recipe)
        gateway.seed(
            CardRunState(
                instanceId = "terminal-stop-instance",
                recipeId = recipe.id,
                recipeName = recipe.name,
                status = CardRunStatus.WaitingTerminal,
                surface = CardRunSurface.Terminal,
                currentStepIndex = 0,
                runId = "terminal-session",
                terminalSessionId = "terminal-session",
                createdAt = 20L,
                updatedAt = 20L
            )
        )
        val orchestrator = RunOrchestrator(gateway, executor)

        orchestrator.stop("terminal-stop-instance")

        val request = executor.stopRequests.single()
        assertEquals(null, request.bridgeRunId())
        assertEquals(false, request.hasBridgeProcessBinding())
        assertEquals(true, request.interruptTerminal)
    }

    @Test
    fun `通知动作必须精确匹配实例代次步骤序号和步骤身份`() {
        val gateway = FakeRunStateGateway()
        val executor = FakeRecipeExecutor()
        val orchestrator = RunOrchestrator(gateway, executor)
        val recipe = recipe("exact-step", KiteRecipe.STEP_TERMINAL, KiteRecipe.STEP_SHELL)
        orchestrator.start(RunStartRequest(recipe, "exact-step-instance"))
        val request = executor.executeRequests.single()
        executor.emit(
            RecipeExecutionEvent.AwaitingUser(
                instanceId = request.instanceId,
                generation = request.generation,
                stepIndex = request.stepIndex,
                mutation = RunStateMutation(
                    status = CardRunStatus.WaitingTerminal,
                    surface = CardRunSurface.Terminal,
                    currentStepIndex = 0,
                    terminalSessionId = "terminal-exact"
                )
            )
        )
        val exact = RunStepActionPolicy.completionCommand(recipe, gateway.state(request.instanceId)!!)!!

        assertEquals(
            RunCommandResult.Ignored("generation_mismatch"),
            orchestrator.completeStep(exact.copy(expectedGeneration = exact.expectedGeneration - 1))
        )
        assertEquals(
            RunCommandResult.Ignored("step_index_mismatch"),
            orchestrator.completeStep(exact.copy(expectedStepIndex = exact.expectedStepIndex + 1))
        )
        assertEquals(
            RunCommandResult.Ignored("step_id_mismatch"),
            orchestrator.completeStep(exact.copy(expectedStepId = "other-step"))
        )
        assertEquals(CardRunStatus.WaitingTerminal, gateway.state(request.instanceId)?.status)

        assertEquals(
            RunCommandResult.Accepted(request.instanceId),
            orchestrator.completeStep(exact.copy(output = "当前步骤完成"))
        )
        assertEquals(CardRunStatus.Running, gateway.state(request.instanceId)?.status)
        assertEquals(1, gateway.state(request.instanceId)?.currentStepIndex)
        assertEquals(KiteRecipe.STEP_SHELL, executor.executeRequests.last().step.type)
    }

    private fun recipe(id: String, vararg types: String): KiteRecipe = KiteRecipe(
        id = id,
        name = id,
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(
            types.mapIndexed { index, type ->
                KiteRecipeStep(
                    id = "step-$index",
                    type = type,
                    cmd = "echo $index".takeIf { type != KiteRecipe.STEP_OPEN_WEB },
                    url = "http://127.0.0.1:${8000 + index}".takeIf { type == KiteRecipe.STEP_OPEN_WEB },
                    action = KiteRecipe.ANDROID_ACTION_PREPARE_AI_ENV.takeIf { type == KiteRecipe.STEP_ANDROID_ACTION }
                )
            }
        )
    )
}

private class FakeRecipeExecutor(
    private val autoComplete: Boolean = false,
    private val emitExecutionWhileCompleting: Boolean = false
) : RecipeExecutor {
    val executeRequests = mutableListOf<RecipeStepExecutionRequest>()
    val stopRequests = mutableListOf<RecipeStopRequest>()
    private val executionCallbacks = mutableListOf<(RecipeExecutionEvent) -> Unit>()
    private var stopCallback: ((StopExecutionResult) -> Unit)? = null

    override fun execute(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        executeRequests += request
        executionCallbacks += callback
        if (autoComplete) {
            callback(
                RecipeExecutionEvent.Completed(
                    instanceId = request.instanceId,
                    generation = request.generation,
                    stepIndex = request.stepIndex,
                    mutation = RunStateMutation(
                        status = CardRunStatus.Running,
                        surface = RunOrchestrator.surfaceFor(request.step.type),
                        currentStepIndex = request.stepIndex,
                        lastMeaningfulOutput = "完成 ${request.step.type}"
                    )
                )
            )
        }
    }

    override fun completeWaitingStep(
        request: RecipeStepCompletionRequest,
        callback: (RecipeStepCompletionResult) -> Unit
    ) {
        if (emitExecutionWhileCompleting) {
            executionCallbacks.last().invoke(
                RecipeExecutionEvent.Completed(
                    instanceId = request.instanceId,
                    generation = request.generation,
                    stepIndex = request.stepIndex,
                    mutation = RunStateMutation(
                        status = CardRunStatus.Running,
                        surface = CardRunSurface.Terminal,
                        currentStepIndex = request.stepIndex,
                        terminalSessionId = "late-terminal",
                        lastMeaningfulOutput = "迟到的终端结束"
                    )
                )
            )
        }
        callback(RecipeStepCompletionResult.Ready(request.output))
    }

    override fun stop(request: RecipeStopRequest, callback: (StopExecutionResult) -> Unit) {
        stopRequests += request
        stopCallback = callback
    }

    fun emit(event: RecipeExecutionEvent) {
        executionCallbacks.last().invoke(event)
    }

    fun emitStop(result: StopExecutionResult) {
        stopCallback?.invoke(result)
    }
}

private class FakeRunStateGateway : RunStateGateway {
    private val recipes = linkedMapOf<String, KiteRecipe>()
    private val states = linkedMapOf<String, CardRunState>()
    private var generation = 100L

    override fun register(recipe: KiteRecipe) {
        recipes[recipe.id] = recipe
    }

    override fun recipe(recipeId: String): KiteRecipe? = recipes[recipeId]

    override fun state(instanceId: String): CardRunState? = states[instanceId]

    override fun current(recipeId: String): CardRunState? =
        states.values.filter { it.recipeId == recipeId }.maxByOrNull { it.updatedAt }

    override fun start(request: RunStartRequest): CardRunState {
        register(request.recipe)
        val now = ++generation
        return CardRunState(
            instanceId = request.instanceId,
            recipeId = request.recipe.id,
            recipeName = request.recipe.name,
            parentInstanceId = request.parentInstanceId,
            ownerKind = request.ownerKind,
            stepId = request.stepId,
            status = CardRunStatus.Starting,
            stepCount = request.recipe.steps.size,
            createdAt = now,
            updatedAt = now
        ).also { states[it.instanceId] = it }
    }

    override fun update(
        recipe: KiteRecipe,
        instanceId: String,
        mutation: RunStateMutation
    ): CardRunState {
        val existing = states[instanceId] ?: start(RunStartRequest(recipe, instanceId))
        val now = ++generation
        return existing.copy(
            recipeName = recipe.name,
            status = mutation.status,
            surface = mutation.surface ?: existing.surface,
            currentStepIndex = mutation.currentStepIndex ?: existing.currentStepIndex,
            stepCount = recipe.steps.size,
            runtimeRootOwnerId = if (mutation.clearRunBinding) {
                null
            } else {
                mutation.runtimeRootOwnerId ?: existing.runtimeRootOwnerId
            },
            runtimeOwnerId = if (mutation.clearRunBinding) null else mutation.runtimeOwnerId ?: existing.runtimeOwnerId,
            runtimeUnitId = if (mutation.clearRunBinding) null else mutation.runtimeUnitId ?: existing.runtimeUnitId,
            ownedRuntimeOwnerIds = if (mutation.clearRunBinding) {
                emptyList()
            } else {
                (mutation.ownedRuntimeOwnerIds.orEmpty().ifEmpty { existing.ownedRuntimeOwnerIds } +
                    listOfNotNull(mutation.runtimeOwnerId))
                    .distinct()
            },
            runId = if (mutation.clearRunBinding) null else mutation.runId ?: existing.runId,
            terminalSessionId = if (mutation.clearRunBinding || mutation.clearTerminalSession) {
                null
            } else {
                mutation.terminalSessionId ?: existing.terminalSessionId
            },
            pid = if (mutation.clearRunBinding) null else mutation.pid ?: existing.pid,
            rootPid = if (mutation.clearRunBinding) null else mutation.rootPid ?: existing.rootPid,
            processGroupId = if (mutation.clearRunBinding) null else mutation.processGroupId ?: existing.processGroupId,
            systemSessionId = if (mutation.clearRunBinding) null else mutation.systemSessionId ?: existing.systemSessionId,
            lastMeaningfulOutput = mutation.lastMeaningfulOutput ?: existing.lastMeaningfulOutput,
            lastError = mutation.lastError,
            shellReportText = mutation.shellReportText ?: existing.shellReportText,
            nextActionUrl = if (mutation.clearRunBinding || mutation.clearNextActionUrl) {
                null
            } else {
                mutation.nextActionUrl ?: existing.nextActionUrl
            },
            x11Display = if (mutation.clearRunBinding) null else mutation.x11Display ?: existing.x11Display,
            x11SocketPath = if (mutation.clearRunBinding) null else mutation.x11SocketPath ?: existing.x11SocketPath,
            updatedAt = now
        ).also { states[instanceId] = it }
    }

    fun seed(state: CardRunState) {
        states[state.instanceId] = state
    }
}

private class FakeRunOwnedWindowGateway : RunOwnedWindowGateway {
    val requests = mutableListOf<Pair<String, Long>>()
    private var callback: ((RunOwnedWindowsCloseResult) -> Unit)? = null

    override fun closeAll(
        instanceId: String,
        expectedGeneration: Long,
        callback: (RunOwnedWindowsCloseResult) -> Unit
    ) {
        requests += instanceId to expectedGeneration
        this.callback = callback
    }

    fun complete(result: RunOwnedWindowsCloseResult) {
        callback?.invoke(result)
    }
}
