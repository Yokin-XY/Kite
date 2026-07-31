package com.kite.app.platform.runs

import android.content.Context
import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeExecutor
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RecipeStopRequest
import com.kite.app.application.runs.RunExecutionEffect
import com.kite.app.application.runs.RunExecutionEffectSink
import com.kite.app.application.runs.RunOwnedWindowGateway
import com.kite.app.application.runs.RunOwnedWindowsCloseResult
import com.kite.app.application.runs.RunStateMutation
import com.kite.app.application.runs.StopCoordinator
import com.kite.app.application.runtimemanagement.InstanceRuntimeTopologyBuilder
import com.kite.app.bridge.KiteBrowserProxyInstaller
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.foundation.runtime.TerminalSessionStore
import com.kite.app.foundation.runtime.ProotOwnerProcessTerminator
import com.kite.app.foundation.runtime.RuntimeOwnerIdentity
import com.kite.app.foundation.runtime.RuntimeOwnerNamespace
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import com.kite.app.resources.KiteResourceInstallRecipes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 实例窗口的 Platform 适配器。手动窗口使用独立子运行事实，流程步骤重放也使用
 * 独立子事实；父实例只保存当前选中的窗口，不复制子窗口运行状态。
 */
internal class AndroidRunWindowSurfaceGateway(
    context: Context,
    private val diagnostics: KiteDiagnostics,
    private val executor: RecipeExecutor,
    private val effectSink: RunExecutionEffectSink = RunExecutionEffectSink { }
) : RunOwnedWindowGateway {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopCoordinator = StopCoordinator()

    fun openBlankWeb(recipe: KiteRecipe, instanceId: String): Boolean {
        val parent = matchingState(recipe, instanceId) ?: return false
        val child = createChild(
            recipe = recipe,
            parent = parent,
            ownerKind = CardRunState.OWNER_KIND_WEB,
            surface = CardRunSurface.Web,
            stepId = null,
            currentStepIndex = -1,
            message = "等待输入网页地址",
            status = CardRunStatus.Opened
        )
        CardRunStore.selectWindow(parent.instanceId, child.instanceId, CardRunSurface.Web)
        diagnostics.logRecipeAction(
            recipe,
            "card_run_blank_web_opened",
            mapOf("instanceId" to instanceId, "windowId" to child.instanceId)
        )
        return true
    }

    fun createBlankTerminal(recipe: KiteRecipe, instanceId: String): Boolean {
        val parent = matchingState(recipe, instanceId) ?: return false
        val child = createChild(
            recipe = recipe,
            parent = parent,
            ownerKind = CardRunState.OWNER_KIND_TERMINAL,
            surface = CardRunSurface.Terminal,
            stepId = null,
            currentStepIndex = -1,
            message = "正在创建全新终端"
        )
        val generation = child.createdAt
        CardRunStore.selectWindow(parent.instanceId, child.instanceId, CardRunSurface.Terminal)
        scope.launch {
            val result = runCatching {
                val space = KFWorkspaceManager.ensureActiveSpace(appContext)
                val record = KFWorkspaceManager.createShellSession(
                    context = appContext,
                    spaceId = space.id,
                    title = "${recipe.name.trim().ifBlank { "Kite" }} · 新终端",
                    sourceLabel = recipe.name
                )
                TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
                val environment = runCatching {
                    KiteBrowserProxyInstaller.environment(
                        context = appContext,
                        recipeId = recipe.id,
                        instanceId = child.instanceId,
                        source = "card_run_blank_terminal"
                    )
                }.getOrDefault(emptyMap())
                TerminalSessionStore.refresh(appContext, force = true)
                record to environment
            }
            val latest = CardRunStore.get(child.instanceId)
            if (latest == null || latest.createdAt != generation) return@launch
            result.fold(
                onSuccess = { (record, environment) ->
                    val runtimeOwner = RuntimeOwnerIdentity.terminal(
                        rootNamespace = runtimeNamespace(recipe, parent),
                        instanceId = child.instanceId,
                        generation = generation,
                        terminalSessionId = record.id
                    )
                    TerminalRuntimeHost.setLaunchEnvironmentOverrides(
                        appContext,
                        record.id,
                        environment + runtimeOwner.environment()
                    )
                    CardRunStore.update(
                        recipe = recipe,
                        status = CardRunStatus.Opened,
                        instanceId = child.instanceId,
                        parentInstanceId = parent.instanceId,
                        ownerKind = CardRunState.OWNER_KIND_TERMINAL,
                        surface = CardRunSurface.Terminal,
                        currentStepIndex = -1,
                        runtimeRootOwnerId = runtimeOwner.rootOwnerId,
                        runtimeOwnerId = runtimeOwner.ownerId,
                        runtimeUnitId = runtimeOwner.unitId,
                        runId = record.id,
                        terminalSessionId = record.id,
                        lastMeaningfulOutput = "已打开全新终端：${record.title}"
                    )
                    diagnostics.logRecipeAction(
                        recipe,
                        "card_run_blank_terminal_opened",
                        mapOf(
                            "instanceId" to instanceId,
                            "windowId" to child.instanceId,
                            "sessionId" to record.id
                        )
                    )
                },
                onFailure = { error ->
                    val message = "创建终端失败：${error.message ?: error.javaClass.simpleName}"
                    CardRunStore.update(
                        recipe = recipe,
                        status = CardRunStatus.Failed,
                        instanceId = child.instanceId,
                        parentInstanceId = parent.instanceId,
                        ownerKind = CardRunState.OWNER_KIND_TERMINAL,
                        surface = CardRunSurface.Terminal,
                        currentStepIndex = -1,
                        lastError = message
                    )
                    diagnostics.logRecipeAction(
                        recipe,
                        "card_run_blank_terminal_failed",
                        mapOf("instanceId" to instanceId, "windowId" to child.instanceId, "error" to message.take(500))
                    )
                }
            )
        }
        return true
    }

    fun openManualWebUrl(recipe: KiteRecipe, parentInstanceId: String, rawUrl: String): Boolean {
        val parent = matchingState(recipe, parentInstanceId) ?: return false
        val child = parent.selectedWindowId
            ?.let(CardRunStore::get)
            ?.takeIf {
                it.parentInstanceId == parent.instanceId &&
                    it.ownerKind == CardRunState.OWNER_KIND_WEB
            }
            ?: return false
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Opened,
            instanceId = child.instanceId,
            parentInstanceId = parent.instanceId,
            ownerKind = CardRunState.OWNER_KIND_WEB,
            surface = CardRunSurface.Web,
            currentStepIndex = -1,
            lastMeaningfulOutput = "手动打开网页",
            nextActionUrl = rawUrl
        )
        return true
    }

    fun closeManualWindow(recipe: KiteRecipe, parentInstanceId: String, windowId: String): Boolean {
        val parent = matchingState(recipe, parentInstanceId) ?: return false
        val child = CardRunStore.get(windowId)?.takeIf {
            it.parentInstanceId == parent.instanceId &&
                it.ownerKind in MANUAL_WINDOW_OWNER_KINDS
        } ?: return false
        child.terminalSessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
            TerminalRuntimeHost.endSession(appContext, sessionId)
        }
        CardRunStore.removeRun(child.instanceId)
        if (parent.selectedWindowId == child.instanceId) {
            CardRunStore.selectSurface(parent.instanceId, parent.recommendedSurface())
        }
        diagnostics.logRecipeAction(
            recipe,
            "card_run_manual_window_closed",
            mapOf("instanceId" to parent.instanceId, "windowId" to child.instanceId)
        )
        return true
    }

    fun replayWorkflowStep(recipe: KiteRecipe, parentInstanceId: String, stepIndex: Int): Boolean {
        val parent = matchingState(recipe, parentInstanceId) ?: return false
        val step = recipe.steps.getOrNull(stepIndex) ?: return false
        val childId = replayChildId(parent.instanceId, stepIndex)
        val previous = CardRunStore.get(childId)
        previous?.instanceId?.let(CardRunStore::removeRun)
        val child = createChild(
            recipe = recipe,
            parent = parent,
            ownerKind = CardRunState.OWNER_KIND_STEP_REPLAY,
            surface = surfaceFor(step.type),
            stepId = step.id,
            currentStepIndex = stepIndex,
            message = replayStartingMessage(step.type),
            instanceId = childId
        )
        val generation = child.createdAt
        val stopRequest = previous?.toStopRequest(recipe)
        if (stopRequest == null) {
            executeReplay(recipe, parent, child, stepIndex, step, generation)
        } else {
            executor.stop(stopRequest) stopCallback@{ _ ->
                if (CardRunStore.get(child.instanceId)?.createdAt != generation) return@stopCallback
                executeReplay(recipe, parent, child, stepIndex, step, generation)
            }
        }
        diagnostics.logRecipeAction(
            recipe,
            "card_run_step_replay_requested",
            mapOf("instanceId" to parent.instanceId, "stepIndex" to stepIndex.toString())
        )
        return true
    }

    override fun closeAll(
        instanceId: String,
        expectedGeneration: Long,
        callback: (RunOwnedWindowsCloseResult) -> Unit
    ) {
        val root = CardRunStore.get(instanceId)
        if (root == null || root.createdAt != expectedGeneration) {
            callback(
                RunOwnedWindowsCloseResult(
                    confirmed = false,
                    message = "实例代次已经变化，未关闭子窗口"
                )
            )
            return
        }
        val topology = InstanceRuntimeTopologyBuilder.build(
            runs = CardRunStore.snapshot(),
            terminals = emptyList(),
            processes = emptyList()
        )
        closeOwnedDescendants(
            rootInstanceId = instanceId,
            rootGeneration = expectedGeneration,
            descendants = topology.descendantsDeepestFirst(instanceId).map { it.run },
            index = 0,
            callback = callback
        )
    }

    private fun closeOwnedDescendants(
        rootInstanceId: String,
        rootGeneration: Long,
        descendants: List<CardRunState>,
        index: Int,
        callback: (RunOwnedWindowsCloseResult) -> Unit
    ) {
        val root = CardRunStore.get(rootInstanceId)
        if (root == null || root.createdAt != rootGeneration) {
            callback(
                RunOwnedWindowsCloseResult(
                    confirmed = false,
                    message = "实例代次已经变化，停止结果已丢弃",
                    remainingInstanceIds = descendants.drop(index).map(CardRunState::instanceId)
                )
            )
            return
        }
        if (index >= descendants.size) {
            callback(RunOwnedWindowsCloseResult(confirmed = true))
            return
        }

        val expected = descendants[index]
        val current = CardRunStore.get(expected.instanceId)
        if (current == null) {
            closeOwnedDescendants(rootInstanceId, rootGeneration, descendants, index + 1, callback)
            return
        }
        if (current.createdAt != expected.createdAt) {
            closeOwnedDescendants(rootInstanceId, rootGeneration, descendants, index + 1, callback)
            return
        }

        val recipe = CardRunStore.registeredRecipe(current.recipeId)
        val request = recipe?.let { current.toStopRequest(it) }
        if (request == null) {
            if (current.hasRunBinding() || current.hasRuntimeOwnership()) {
                ProotOwnerProcessTerminator.scheduleResidualReap(
                    appContext,
                    current.runtimeOwnerIdsForStop()
                )
                callback(
                    RunOwnedWindowsCloseResult(
                        confirmed = false,
                        message = "子窗口仍有运行绑定，已进入后台回收",
                        remainingInstanceIds = descendants.drop(index).map(CardRunState::instanceId),
                    ),
                )
                return
            }
            CardRunStore.removeRun(current.instanceId)
            closeOwnedDescendants(rootInstanceId, rootGeneration, descendants, index + 1, callback)
            return
        }

        executor.stop(request) { result ->
            val latest = CardRunStore.get(current.instanceId)
            if (latest == null) {
                closeOwnedDescendants(rootInstanceId, rootGeneration, descendants, index + 1, callback)
                return@stop
            }
            if (latest.createdAt != current.createdAt) {
                closeOwnedDescendants(rootInstanceId, rootGeneration, descendants, index + 1, callback)
                return@stop
            }
            val resolution = stopCoordinator.resolve(result)
            if (resolution.confirmed) {
                CardRunStore.removeRun(current.instanceId)
                closeOwnedDescendants(rootInstanceId, rootGeneration, descendants, index + 1, callback)
            } else {
                callback(
                    RunOwnedWindowsCloseResult(
                        confirmed = false,
                        message = resolution.summary,
                        remainingInstanceIds = descendants.drop(index).map(CardRunState::instanceId),
                    ),
                )
            }
        }
    }

    private fun executeReplay(
        recipe: KiteRecipe,
        parent: CardRunState,
        child: CardRunState,
        stepIndex: Int,
        step: KiteRecipeStep,
        generation: Long
    ) {
        val request = RecipeStepExecutionRequest(
            recipe = recipe,
            instanceId = child.instanceId,
            generation = generation,
            stepIndex = stepIndex,
            step = step,
            previousState = child,
            attemptId = replayAttemptIds.incrementAndGet(),
            runtimeRootOwnerId = RuntimeOwnerIdentity.root(runtimeNamespace(recipe, parent), child.instanceId, generation)
        ).withStepOwner(runtimeNamespace(recipe, parent))
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Running,
            instanceId = child.instanceId,
            parentInstanceId = parent.instanceId,
            ownerKind = CardRunState.OWNER_KIND_STEP_REPLAY,
            stepId = step.id,
            surface = surfaceFor(step.type),
            currentStepIndex = stepIndex,
            runtimeRootOwnerId = request.runtimeRootOwnerId,
            runtimeOwnerId = request.runtimeOwnerId,
            runtimeUnitId = request.runtimeUnitId
        )
        executor.execute(request) { event ->
            handleReplayEvent(recipe, parent, child, step, generation, event)
        }
    }

    private fun handleReplayEvent(
        recipe: KiteRecipe,
        parent: CardRunState,
        child: CardRunState,
        step: KiteRecipeStep,
        generation: Long,
        event: RecipeExecutionEvent
    ) {
        val current = CardRunStore.get(child.instanceId) ?: return
        if (current.createdAt != generation || event.generation != generation) return
        when (event) {
            is RecipeExecutionEvent.Progress -> applyReplayMutation(recipe, parent, child, step, event.mutation)
            is RecipeExecutionEvent.AwaitingUser -> {
                applyReplayMutation(recipe, parent, child, step, event.mutation)
                event.effect?.let { effect -> effectSink.emit(effect.forParent(parent.instanceId)) }
            }
            is RecipeExecutionEvent.Completed -> applyReplayMutation(recipe, parent, child, step, event.mutation)
            is RecipeExecutionEvent.Failed -> {
                val supplied = event.mutation
                if (supplied != null) {
                    applyReplayMutation(recipe, parent, child, step, supplied)
                } else {
                    CardRunStore.update(
                        recipe = recipe,
                        status = if (event.bridgeUnavailable) CardRunStatus.BridgeUnavailable else CardRunStatus.Failed,
                        instanceId = child.instanceId,
                        parentInstanceId = parent.instanceId,
                        ownerKind = CardRunState.OWNER_KIND_STEP_REPLAY,
                        stepId = step.id,
                        surface = surfaceFor(step.type),
                        currentStepIndex = event.stepIndex,
                        lastError = event.message
                    )
                }
            }
        }
    }

    private fun applyReplayMutation(
        recipe: KiteRecipe,
        parent: CardRunState,
        child: CardRunState,
        step: KiteRecipeStep,
        mutation: RunStateMutation
    ) {
        CardRunStore.update(
            recipe = recipe,
            status = mutation.status,
            instanceId = child.instanceId,
            parentInstanceId = parent.instanceId,
            ownerKind = CardRunState.OWNER_KIND_STEP_REPLAY,
            stepId = step.id,
            surface = mutation.surface ?: surfaceFor(step.type),
            currentStepIndex = mutation.currentStepIndex,
            runtimeRootOwnerId = mutation.runtimeRootOwnerId,
            runtimeOwnerId = mutation.runtimeOwnerId,
            runtimeUnitId = mutation.runtimeUnitId,
            ownedRuntimeOwnerIds = mutation.ownedRuntimeOwnerIds,
            runId = mutation.runId,
            terminalSessionId = mutation.terminalSessionId,
            pid = mutation.pid,
            rootPid = mutation.rootPid,
            processGroupId = mutation.processGroupId,
            systemSessionId = mutation.systemSessionId,
            runtimeLane = mutation.runtimeLane,
            runtimeFallbackReason = mutation.runtimeFallbackReason,
            lastMeaningfulOutput = mutation.lastMeaningfulOutput,
            lastError = mutation.lastError,
            shellReportText = mutation.shellReportText,
            nextActionUrl = mutation.nextActionUrl,
            x11Display = mutation.x11Display,
            x11SocketPath = mutation.x11SocketPath,
            agentId = mutation.agentId,
            agentBinding = mutation.agentBinding,
            clearRunBinding = mutation.clearRunBinding,
            clearTerminalSession = mutation.clearTerminalSession,
            clearNextActionUrl = mutation.clearNextActionUrl,
            clearAgentBinding = mutation.clearAgentBinding
        )
    }

    private fun createChild(
        recipe: KiteRecipe,
        parent: CardRunState,
        ownerKind: String,
        surface: CardRunSurface,
        stepId: String?,
        currentStepIndex: Int,
        message: String,
        instanceId: String = manualChildId(parent.instanceId, ownerKind),
        status: CardRunStatus = CardRunStatus.Starting
    ): CardRunState {
        CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            parentInstanceId = parent.instanceId,
            ownerKind = ownerKind,
            stepId = stepId,
            agentId = parent.agentId,
            environmentId = parent.environmentId
        )
        return CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId,
            parentInstanceId = parent.instanceId,
            ownerKind = ownerKind,
            stepId = stepId,
            surface = surface,
            currentStepIndex = currentStepIndex,
            lastMeaningfulOutput = message,
            clearRunBinding = true,
            clearTerminalSession = true,
            clearNextActionUrl = true,
            environmentId = parent.environmentId
        )
    }

    private fun matchingState(recipe: KiteRecipe, instanceId: String): CardRunState? =
        CardRunStore.get(instanceId)?.takeIf { it.recipeId == recipe.id }

    private fun CardRunState.toStopRequest(recipe: KiteRecipe): RecipeStopRequest? {
        if (!hasRunBinding() && !hasRuntimeOwnership()) return null
        return RecipeStopRequest(
            recipe = recipe,
            instanceId = instanceId,
            generation = createdAt,
            runtimeOwnerIds = runtimeOwnerIdsForStop(),
            runId = runId,
            terminalSessionId = terminalSessionId,
            pid = pid,
            rootPid = rootPid,
            processGroupId = processGroupId,
            systemSessionId = systemSessionId,
            interruptTerminal = !terminalSessionId.isNullOrBlank()
        )
    }

    private fun RunExecutionEffect.forParent(parentInstanceId: String): RunExecutionEffect = when (this) {
        is RunExecutionEffect.OpenWeb -> copy(instanceId = parentInstanceId)
        is RunExecutionEffect.StopResolved -> copy(instanceId = parentInstanceId)
    }

    private fun RecipeStepExecutionRequest.withStepOwner(
        namespace: RuntimeOwnerNamespace
    ): RecipeStepExecutionRequest {
        val owner = RuntimeOwnerIdentity.step(
            namespace = namespace,
            instanceId = instanceId,
            generation = generation,
            stepIndex = stepIndex,
            stepId = step.id,
            attemptId = attemptId
        )
        return copy(
            runtimeRootOwnerId = owner.rootOwnerId,
            runtimeOwnerId = owner.ownerId,
            runtimeUnitId = owner.unitId
        )
    }

    private fun runtimeNamespace(recipe: KiteRecipe, state: CardRunState): RuntimeOwnerNamespace =
        if (
            state.runtimeRootOwnerId?.startsWith("resource:") == true ||
            state.ownerKind == CardRunState.OWNER_KIND_RESOURCE ||
            state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD ||
            recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE
        ) {
            RuntimeOwnerNamespace.Resource
        } else {
            RuntimeOwnerNamespace.Card
        }

    private companion object {
        val replayAttemptIds = AtomicLong(System.currentTimeMillis())
        val MANUAL_WINDOW_OWNER_KINDS = setOf(
            CardRunState.OWNER_KIND_TERMINAL,
            CardRunState.OWNER_KIND_WEB
        )
        val OWNED_WINDOW_KINDS = MANUAL_WINDOW_OWNER_KINDS + CardRunState.OWNER_KIND_STEP_REPLAY

        fun manualChildId(parentInstanceId: String, ownerKind: String): String =
            "$parentInstanceId:window:$ownerKind:${UUID.randomUUID()}"

        fun replayChildId(parentInstanceId: String, stepIndex: Int): String =
            "$parentInstanceId:step-replay:$stepIndex"

        fun surfaceFor(stepType: String): CardRunSurface = when (stepType) {
            KiteRecipe.STEP_SHELL,
            KiteRecipe.STEP_ANDROID_ACTION -> CardRunSurface.Report
            KiteRecipe.STEP_TERMINAL -> CardRunSurface.Terminal
            KiteRecipe.STEP_OPEN_WEB -> CardRunSurface.Web
            KiteRecipe.STEP_X11 -> CardRunSurface.X11
            KiteRecipe.STEP_AGENT -> CardRunSurface.Agent
            else -> CardRunSurface.Summary
        }

        fun replayStartingMessage(stepType: String): String = when (stepType) {
            KiteRecipe.STEP_SHELL -> "正在重新执行 SH"
            KiteRecipe.STEP_TERMINAL -> "正在重新创建终端"
            KiteRecipe.STEP_OPEN_WEB -> "正在重新打开网页"
            KiteRecipe.STEP_X11 -> "正在重新启动 X11"
            else -> "正在重新执行步骤"
        }
    }
}
