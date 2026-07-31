package com.kite.app.platform.runs

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeExecutor
import com.kite.app.application.runs.RecipeStepCompletionRequest
import com.kite.app.application.runs.RecipeStepCompletionResult
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RecipeStopRequest
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunExecutionEffect
import com.kite.app.application.runs.RunExecutionEnvironmentProvider
import com.kite.app.application.runs.RunStateMutation
import com.kite.app.application.runs.StopExecutionOutcome
import com.kite.app.application.runs.StopExecutionResult
import com.kite.app.bridge.BridgeErrorType
import com.kite.app.bridge.BridgeProgress
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.bridge.OwnerStopOutputEvidence
import com.kite.app.bridge.KiteBrowserProxyInstaller
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.foundation.contracts.ManagedTerminalStatus
import com.kite.app.foundation.runtime.RuntimeExecutionPayload
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import com.kite.app.foundation.workspace.ContainerVisibleFileResolver
import com.kite.app.foundation.runtime.ProotOwnerProcessTerminator
import com.kite.app.foundation.runtime.ProotOwnerTerminationOutcome
import com.kite.app.foundation.runtime.RuntimeFrameCoordinator
import com.kite.app.foundation.runtime.RuntimeLaunchTrace
import com.kite.app.foundation.runtime.RuntimeOwnerIdentity
import com.kite.app.foundation.runtime.RuntimeOwnerNamespace
import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.terminal.TerminalRuntimeRegistry
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.ManagedRuntimeLaunchPlan
import com.kite.app.foundation.workspace.ManagedRuntimeLaunchPlanner
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.KiteRunReport
import com.kite.app.recipe.KiteStepReport
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import com.kite.app.run.KiteX11SurfaceBinding
import com.kite.app.run.KiteX11SurfacePlan
import com.kite.app.run.KiteX11SurfaceServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Android/PRoot 执行适配器。它不持有页面，也不决定导航。 */
internal class AndroidRecipeExecutor(
    context: Context,
    private val bridgeClient: KiteBridgeClient,
    private val diagnostics: KiteDiagnostics,
    private val executionEnvironmentProvider: RunExecutionEnvironmentProvider =
        RunExecutionEnvironmentProvider.None,
    private val agentRuntime: AgentRecipeRuntime = AndroidAgentRecipeRuntime(context),
    private val nativeCapabilityRuntime: NativeCapabilityRecipeRuntime =
        AndroidNativeCapabilityRecipeRuntime(context),
) : RecipeExecutor {
    private data class PendingTerminal(
        val request: RecipeStepExecutionRequest,
        val callback: (RecipeExecutionEvent) -> Unit
    )

    private data class PreparedTerminalLaunch(
        val record: com.kite.app.foundation.contracts.ManagedTerminalRecord,
        val runtimeConfig: com.kite.app.foundation.contracts.ContainerLaunchConfig?,
        val runtimeLane: String,
        val fallbackReason: String,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "KiteRecipeExecutorSchedule").apply { isDaemon = true }
    }
    private val pendingTerminals = ConcurrentHashMap<String, PendingTerminal>()

    init {
        scope.launch {
            TerminalRuntimeRegistry.entries.collectLatest { entries ->
                entries.forEach { terminal ->
                    if (terminal.status !in TERMINAL_FINISHED_STATUSES) return@forEach
                    val pending = pendingTerminals.remove(terminal.sessionId) ?: return@forEach
                    pending.callback(
                        RecipeExecutionEvent.Completed(
                            instanceId = pending.request.instanceId,
                            generation = pending.request.generation,
                            stepIndex = pending.request.stepIndex,
                            mutation = RunStateMutation(
                                status = CardRunStatus.Running,
                                surface = CardRunSurface.Terminal,
                                currentStepIndex = pending.request.stepIndex,
                                runId = terminal.sessionId,
                                lastMeaningfulOutput = "终端已结束：${terminal.status.label}",
                                clearTerminalSession = true
                            )
                        )
                    )
                }
            }
        }
    }

    override fun execute(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.EXECUTOR_RECEIVED)
        when (request.step.type) {
            KiteRecipe.STEP_SHELL -> withUbuntuRuntime(request, callback) {
                executeShell(request, callback)
            }
            KiteRecipe.STEP_TERMINAL -> withUbuntuRuntime(request, callback) { readyLease ->
                executeTerminal(request, callback, readyLease)
            }
            KiteRecipe.STEP_X11 -> withUbuntuRuntime(request, callback) {
                executeX11(request, callback)
            }
            KiteRecipe.STEP_AGENT -> executeAgent(request, callback)
            KiteRecipe.STEP_OPEN_WEB -> executeWeb(request, callback)
            KiteRecipe.STEP_ANDROID_ACTION -> executeAndroidAction(request, callback)
            KiteRecipe.STEP_NATIVE_CAPABILITY -> nativeCapabilityRuntime.execute(request, callback)
            else -> callback(request.failed("unsupported_step:${request.step.type}"))
        }
    }

    override fun completeWaitingStep(
        request: RecipeStepCompletionRequest,
        callback: (RecipeStepCompletionResult) -> Unit
    ) {
        if (request.step.type == KiteRecipe.STEP_TERMINAL) {
            request.state.terminalSessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
                pendingTerminals.remove(sessionId)
                TerminalRuntimeHost.endSession(appContext, sessionId)
            }
        }
        callback(RecipeStepCompletionResult.Ready(request.output))
    }

    override fun stop(request: RecipeStopRequest, callback: (StopExecutionResult) -> Unit) {
        request.terminalSessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
            pendingTerminals.remove(sessionId)
            TerminalRuntimeHost.sendCommand(appContext, "\u0003", sessionId)
            TerminalRuntimeHost.endSession(appContext, sessionId)
        }
        if (agentRuntime.owns(request.instanceId, request.generation)) {
            agentRuntime.stop(request.instanceId, request.generation) { stopped ->
                if (!stopped) {
                    stopRuntime(request, callback)
                    return@stop
                }
                val result = if (request.runtimeOwnerIds.any(String::isNotBlank)) {
                    verifyStopAfterTransportDeadline(request)
                } else {
                    StopExecutionResult(StopExecutionOutcome.Confirmed, "Agent 会话已关闭")
                }
                deliverStopResult(request, result, callback)
            }
            return
        }
        if (nativeCapabilityRuntime.owns(request.instanceId, request.generation)) {
            nativeCapabilityRuntime.stop(request.instanceId, request.generation) { stopped ->
                if (!stopped) {
                    callback(
                        StopExecutionResult(
                            StopExecutionOutcome.StillRunning,
                            "原生能力仍在停止中",
                        )
                    )
                } else if (request.hasBridgeProcessBinding()) {
                    stopRuntime(request, callback)
                } else {
                    callback(StopExecutionResult(StopExecutionOutcome.Confirmed, "原生能力已停止"))
                }
            }
            return
        }
        if (!request.hasBridgeProcessBinding()) {
            callback(StopExecutionResult(StopExecutionOutcome.Confirmed, "终端已发送中断并关闭"))
            return
        }
        stopRuntime(request, callback)
    }

    private fun executeAgent(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        agentRuntime.start(
            request = request,
            environment = browserEnvironment(request, "agent_step"),
            callback = callback
        )
    }

    private fun withUbuntuRuntime(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit,
        onReady: (RuntimeReadyLease) -> Unit
    ) {
        RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.RUNTIME_PREP_STARTED)
        callback(
            RecipeExecutionEvent.Progress(
                request.instanceId,
                request.generation,
                request.stepIndex,
                runtimePreparationMutation(request.step.type, request.stepIndex)
            )
        )
        thread(name = "KiteRunPrep-${request.instanceId.take(24)}", isDaemon = true) {
            runCatching {
                val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(appContext)
                val executionEnvironment = executionEnvironmentProvider.environment(request)
                val preparedSpace = if (requiresActiveWorkspacePreparation(executionEnvironment)) {
                    val activeEnvironment = WorkSurfaceRuntimeBridge.resolveActiveWorkspaceEnvironment(container)
                    KFWorkspaceManager.ensureActiveSpace(appContext, activeEnvironment).also { space ->
                        TerminalRuntimeHost.refreshRuntimeSnapshot(appContext, preparedSpace = space)
                    }
                } else {
                    null
                }
                RuntimeReadyLease.create(
                    request = request,
                    container = container,
                    preparedSpace = preparedSpace,
                    runtimeSnapshotRefreshed = preparedSpace != null,
                )
            }.onSuccess { readyLease ->
                RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.RUNTIME_PREP_READY)
                RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.RUNTIME_LEASE_ISSUED)
                onReady(readyLease)
            }.onFailure { error ->
                RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.RUNTIME_PREP_FAILED)
                callback(
                    request.failed(
                        message = "Ubuntu 环境未就绪: ${error.message ?: error.javaClass.simpleName}",
                        bridgeUnavailable = true
                    )
                )
            }
        }
    }

    private fun executeShell(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        val step = request.step
        if (step.cmd.isNullOrBlank()) {
            callback(request.failed("shell_missing_command"))
            return
        }
        val stepRecipe = request.recipe.singleStepRecipe(step)
        callback(
            RecipeExecutionEvent.Progress(
                request.instanceId,
                request.generation,
                request.stepIndex,
                RunStateMutation(
                    status = CardRunStatus.Running,
                    surface = CardRunSurface.Report,
                    currentStepIndex = request.stepIndex,
                    lastMeaningfulOutput = "正在执行 sh：${step.cmd.take(80)}",
                    shellReportText = "命令：${step.cmd}\n结果：执行中",
                    clearNextActionUrl = true
                )
            )
        )
        bridgeClient.runRecipe(
            recipe = stepRecipe,
            extraEnv = browserEnvironment(request, "shell_step"),
            onProgress = { progress -> handleShellProgress(request, progress, callback) }
        ) { result ->
            handleShellResult(request, result, callback)
        }
    }

    private fun handleShellProgress(
        request: RecipeStepExecutionRequest,
        progress: BridgeProgress,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        if (progress.recipeId != request.recipe.id) return
        val output = progress.outputTail.normalizedShellOutput()
        callback(
            RecipeExecutionEvent.Progress(
                request.instanceId,
                request.generation,
                request.stepIndex,
                RunStateMutation(
                    status = CardRunStatus.Running,
                    surface = CardRunSurface.Report,
                    currentStepIndex = request.stepIndex,
                    runId = progress.runId,
                    pid = progress.pid,
                    rootPid = progress.rootPid,
                    processGroupId = progress.processGroupId,
                    systemSessionId = progress.systemSessionId,
                    lastMeaningfulOutput = progress.lastMeaningfulOutput.ifBlank { "正在执行 sh" },
                    shellReportText = buildString {
                        append("命令：").append(request.step.cmd.orEmpty()).append('\n')
                        append("结果：执行中")
                        if (output.isNotBlank()) append("\n原始输出：\n").append(output)
                    }
                )
            )
        )
    }

    private fun handleShellResult(
        request: RecipeStepExecutionRequest,
        result: BridgeResult,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        result.runReport?.let(diagnostics::writeRunReport)
        val report = result.runReport
        val output = report?.lastMeaningfulOutput()
        val runId = report?.runId ?: result.requestId
        val pid = report?.pid ?: extractPid(output) ?: extractPid(result.message)
        val shellReport = shellReportText(report, request.recipe)
        val processExited = shellProcessExited(report?.status, report?.ok)
        if (result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE) {
            callback(
                request.failed(
                    message = result.message.ifBlank { "Ubuntu 命令通道不可用" },
                    bridgeUnavailable = true,
                    mutation = RunStateMutation(
                        status = CardRunStatus.BridgeUnavailable,
                        surface = CardRunSurface.Report,
                        currentStepIndex = request.stepIndex,
                        runId = runId,
                        pid = pid,
                        rootPid = report?.rootPid ?: pid,
                        processGroupId = report?.processGroupId,
                        systemSessionId = report?.systemSessionId,
                        shellReportText = shellReport,
                        lastError = result.message
                    )
                )
            )
            return
        }
        val failed = report?.hasMismatch() == true ||
            report?.ok == false ||
            report?.status == KiteRunReport.STATUS_FAILED ||
            (!result.ok && !result.accepted)
        if (failed) {
            val message = output ?: result.message.ifBlank { "sh 命令执行失败" }
            callback(
                request.failed(
                    message,
                    mutation = RunStateMutation(
                        status = CardRunStatus.Failed,
                        surface = CardRunSurface.Report,
                        currentStepIndex = request.stepIndex,
                        runId = runId,
                        pid = pid,
                        rootPid = report?.rootPid ?: pid,
                        processGroupId = report?.processGroupId,
                        systemSessionId = report?.systemSessionId,
                        lastMeaningfulOutput = output,
                        shellReportText = shellReport,
                        lastError = message
                    )
                )
            )
            return
        }
        callback(
            RecipeExecutionEvent.Completed(
                request.instanceId,
                request.generation,
                request.stepIndex,
                RunStateMutation(
                    status = CardRunStatus.Running,
                    surface = CardRunSurface.Report,
                    currentStepIndex = request.stepIndex,
                    runId = runId,
                    pid = pid,
                    rootPid = report?.rootPid ?: pid,
                    processGroupId = report?.processGroupId,
                    systemSessionId = report?.systemSessionId,
                    lastMeaningfulOutput = output,
                    shellReportText = shellReport,
                    clearRunBinding = processExited
                )
            )
        )
    }

    private fun executeTerminal(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit,
        readyLease: RuntimeReadyLease,
    ) {
        thread(name = "KiteTerminalStep-${request.instanceId.take(24)}", isDaemon = true) {
            val command = request.step.text.orEmpty().ifBlank { request.step.cmd.orEmpty() }
            runCatching {
                val space = readyLease.spaceFor(request) ?: KFWorkspaceManager.ensureActiveSpace(appContext)
                val container = readyLease.containerFor(request)
                RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.RUNTIME_LEASE_CONSUMED)
                val runtimeLaunch = if (readyLease.preparedSpace != null && command.isNotBlank()) {
                    ManagedRuntimeLaunchPlanner.plan(
                        context = appContext,
                        container = container,
                        workspaceDirectory = File(space.workspacePath),
                        request = RuntimeExecutionRequest(
                            payload = RuntimeExecutionPayload.CommandLine(command),
                            workingDirectory = request.step.workdir,
                        ),
                    )
                } else {
                    ManagedRuntimeLaunchPlan.Fallback("explicit_runtime_or_command_missing")
                }
                if (runtimeLaunch is ManagedRuntimeLaunchPlan.Blocked) {
                    error("runtime_provider_blocked:${runtimeLaunch.reason}")
                }
                val fallbackReason = (runtimeLaunch as? ManagedRuntimeLaunchPlan.Fallback)?.reason ?: "none"
                val readyRuntime = runtimeLaunch as? ManagedRuntimeLaunchPlan.Ready
                PreparedTerminalLaunch(
                    record = KFWorkspaceManager.createEmbeddedShellSession(
                        spaceId = space.id,
                        title = terminalTitle(request.recipe, request.stepIndex),
                        sourceLabel = request.recipe.name,
                    ),
                    runtimeConfig = readyRuntime?.config,
                    runtimeLane = readyRuntime?.lane?.value ?: "proot_shell",
                    fallbackReason = fallbackReason,
                )
            }.onSuccess { prepared ->
                val record = prepared.record
                RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.TERMINAL_RECORD_CREATED)
                RuntimeLaunchTrace.bindTerminal(request.instanceId, record.id)
                val terminalOwner = RuntimeOwnerIdentity.terminal(
                    rootNamespace = request.runtimeNamespace(),
                    instanceId = request.instanceId,
                    generation = request.generation,
                    terminalSessionId = record.id,
                    stepIndex = request.stepIndex,
                    stepId = request.step.id,
                    attemptId = request.attemptId
                )
                TerminalRuntimeHost.setLaunchEnvironmentOverrides(
                    appContext = appContext,
                    sessionId = record.id,
                    overrides = browserEnvironment(request, "terminal_step") + terminalOwner.environment()
                )
                prepared.runtimeConfig?.let { config ->
                    TerminalRuntimeHost.setLaunchConfigOverride(appContext, record.id, config)
                }
                TerminalRuntimeHost.stageEmbeddedSession(appContext, record)
                RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.TERMINAL_STAGED)
                pendingTerminals[record.id] = PendingTerminal(request, callback)
                callback(
                    RecipeExecutionEvent.AwaitingUser(
                        request.instanceId,
                        request.generation,
                        request.stepIndex,
                        RunStateMutation(
                            status = CardRunStatus.WaitingTerminal,
                            surface = CardRunSurface.Terminal,
                            currentStepIndex = request.stepIndex,
                            runtimeRootOwnerId = terminalOwner.rootOwnerId,
                            runtimeOwnerId = terminalOwner.ownerId,
                            runtimeUnitId = terminalOwner.unitId,
                            ownedRuntimeOwnerIds = (
                                request.previousState.ownedRuntimeOwnerIds
                                    .filterNot { it == request.runtimeOwnerId } + terminalOwner.ownerId
                                ).distinct(),
                            runId = record.id,
                            terminalSessionId = record.id,
                            runtimeLane = prepared.runtimeLane,
                            runtimeFallbackReason = prepared.fallbackReason,
                            lastMeaningfulOutput = "等待终端完成：${record.title}",
                            clearNextActionUrl = true
                        )
                    )
                )
                RuntimeLaunchTrace.mark(request.instanceId, RuntimeLaunchTrace.TERMINAL_OPEN_REQUESTED)
                TerminalRuntimeHost.openEmbeddedSession(appContext, record)
                if (prepared.runtimeConfig == null && command.isNotBlank()) {
                    diagnostics.logRecipeAction(
                        request.recipe,
                        "terminal_step_command_scheduled",
                        mapOf(
                            "instanceId" to request.instanceId,
                            "sessionId" to record.id,
                            "stepIndex" to request.stepIndex.toString(),
                            "chars" to command.length.toString()
                        )
                    )
                    scheduler.schedule(
                        {
                            TerminalRuntimeHost.sendCommand(
                                appContext,
                                if (command.endsWith("\n")) command else "$command\n",
                                record.id
                            )
                            RuntimeLaunchTrace.mark(
                                request.instanceId,
                                RuntimeLaunchTrace.TERMINAL_COMMAND_DISPATCHED,
                            )
                            diagnostics.logRecipeAction(
                                request.recipe,
                                "terminal_step_command_dispatched",
                                mapOf("instanceId" to request.instanceId, "sessionId" to record.id)
                            )
                        },
                        TERMINAL_COMMAND_DELAY_MS,
                        TimeUnit.MILLISECONDS
                    )
                }
                diagnostics.logRecipeAction(
                    request.recipe,
                    "terminal_runtime_lane_selected",
                    mapOf(
                        "instanceId" to request.instanceId,
                        "sessionId" to record.id,
                        "lane" to prepared.runtimeLane,
                        "fallbackReason" to prepared.fallbackReason,
                    )
                )
            }.onFailure { error ->
                callback(request.failed("创建终端失败：${error.message ?: error.javaClass.simpleName}"))
            }
        }
    }

    private fun executeWeb(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        val url = request.step.url.orEmpty().ifBlank { request.recipe.defaultUrl }
        if (url.isBlank()) {
            callback(request.failed("open_web_missing_url"))
            return
        }
        if (KiteRecipe.normalizeSurfaceMode(request.step.surfaceMode) == KiteRecipe.SURFACE_MODE_SILENT) {
            callback(
                RecipeExecutionEvent.Completed(
                    request.instanceId,
                    request.generation,
                    request.stepIndex,
                    RunStateMutation(
                        status = CardRunStatus.Running,
                        surface = CardRunSurface.Web,
                        currentStepIndex = request.stepIndex,
                        lastMeaningfulOutput = "网页步骤已静默跳过",
                        clearNextActionUrl = true
                    )
                )
            )
            return
        }
        callback(
            RecipeExecutionEvent.AwaitingUser(
                request.instanceId,
                request.generation,
                request.stepIndex,
                RunStateMutation(
                    status = if (request.previousState.hasRunBinding()) CardRunStatus.Running else CardRunStatus.Opened,
                    surface = CardRunSurface.Web,
                    currentStepIndex = request.stepIndex,
                    runId = request.previousState.runId,
                    pid = request.previousState.pid,
                    rootPid = request.previousState.rootPid,
                    processGroupId = request.previousState.processGroupId,
                    systemSessionId = request.previousState.systemSessionId,
                    lastMeaningfulOutput = request.previousState.lastMeaningfulOutput,
                    nextActionUrl = url
                ),
                effect = RunExecutionEffect.OpenWeb(
                    instanceId = request.instanceId,
                    recipeId = request.recipe.id,
                    url = url,
                    surfaceMode = KiteRecipe.normalizeSurfaceMode(request.step.surfaceMode)
                )
            )
        )
    }

    private fun executeX11(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        val command = request.step.cmd.orEmpty().ifBlank { request.step.text.orEmpty() }
        if (command.isBlank()) {
            callback(request.failed("x11_missing_command"))
            return
        }
        val binding = request.previousState.x11Display?.let(KiteX11SurfacePlan::binding)
            ?: KiteX11SurfacePlan.allocate(
                instanceId = request.instanceId,
                occupiedDisplays = CardRunStore.snapshot()
                    .filterNot { it.instanceId == request.instanceId }
                    .mapNotNull { it.x11Display }
                    .toSet()
            )
        callback(
            RecipeExecutionEvent.Progress(
                request.instanceId,
                request.generation,
                request.stepIndex,
                RunStateMutation(
                    status = CardRunStatus.Running,
                    surface = CardRunSurface.Report,
                    currentStepIndex = request.stepIndex,
                    lastMeaningfulOutput = "${request.recipe.name} native X11 准备中",
                    x11Display = binding.display,
                    x11SocketPath = binding.socketPath,
                    clearNextActionUrl = true
                )
            )
        )
        thread(name = "KiteX11Start-${request.instanceId.take(24)}", isDaemon = true) {
            KiteX11SurfaceServer.ensureStarted(appContext, binding)
                .onSuccess { launchX11(request, command, binding, callback) }
                .onFailure { error -> callback(request.failed(error.message ?: "native X11 启动失败")) }
        }
    }

    private fun launchX11(
        request: RecipeStepExecutionRequest,
        command: String,
        binding: KiteX11SurfaceBinding,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        val shellStep = request.step.copy(
            type = KiteRecipe.STEP_SHELL,
            cmd = command,
            runMode = request.step.runMode ?: KiteRecipe.RUN_MODE_DETACHED
        )
        val stepRecipe = request.recipe.singleStepRecipe(shellStep)
        bridgeClient.runRecipe(
            recipe = stepRecipe,
            extraEnv = browserEnvironment(request, "x11_step") + binding.environment(),
            onProgress = { progress ->
                if (progress.recipeId == request.recipe.id) {
                    callback(
                        RecipeExecutionEvent.Progress(
                            request.instanceId,
                            request.generation,
                            request.stepIndex,
                            RunStateMutation(
                                status = CardRunStatus.Running,
                                surface = CardRunSurface.X11,
                                currentStepIndex = request.stepIndex,
                                runId = progress.runId,
                                pid = progress.pid,
                                rootPid = progress.rootPid,
                                processGroupId = progress.processGroupId,
                                systemSessionId = progress.systemSessionId,
                                lastMeaningfulOutput = progress.lastMeaningfulOutput.ifBlank { "${request.recipe.name} native X11 运行中" },
                                x11Display = binding.display,
                                x11SocketPath = binding.socketPath
                            )
                        )
                    )
                }
            }
        ) { result ->
            result.runReport?.let(diagnostics::writeRunReport)
            val report = result.runReport
            if (!result.accepted && !result.ok) {
                callback(
                    request.failed(
                        report?.lastMeaningfulOutput() ?: result.message.ifBlank { "X11 启动失败" },
                        bridgeUnavailable = result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE
                    )
                )
                return@runRecipe
            }
            val mutation = RunStateMutation(
                status = CardRunStatus.Running,
                surface = CardRunSurface.X11,
                currentStepIndex = request.stepIndex,
                runId = report?.runId ?: result.requestId,
                pid = report?.pid,
                rootPid = report?.rootPid,
                processGroupId = report?.processGroupId,
                systemSessionId = report?.systemSessionId,
                lastMeaningfulOutput = "${request.recipe.name} native X11 运行中",
                x11Display = binding.display,
                x11SocketPath = binding.socketPath
            )
            if (KiteRecipe.normalizeSurfaceMode(request.step.surfaceMode) == KiteRecipe.SURFACE_MODE_SILENT) {
                callback(RecipeExecutionEvent.Completed(request.instanceId, request.generation, request.stepIndex, mutation))
            } else {
                callback(RecipeExecutionEvent.AwaitingUser(request.instanceId, request.generation, request.stepIndex, mutation))
            }
        }
    }

    private fun executeAndroidAction(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        runCatching {
            when (request.step.action) {
                KiteRecipe.ANDROID_ACTION_PREPARE_AI_ENV -> {
                    ToolchainPackInstaller.prepareAiEnv(appContext)
                    "安卓动作完成：prepare_ai_env"
                }
                KiteRecipe.ANDROID_ACTION_TOOLCHAIN_DOCTOR -> {
                    ToolchainPackInstaller.doctor(appContext)
                    "安卓动作完成：toolchain_doctor"
                }
                KiteRecipe.ANDROID_ACTION_INSTALL_APK -> installApk(request.step)
                else -> error("unsupported_android_action")
            }
        }.onSuccess { output ->
            callback(
                RecipeExecutionEvent.Completed(
                    request.instanceId,
                    request.generation,
                    request.stepIndex,
                    RunStateMutation(
                        status = CardRunStatus.Running,
                        surface = CardRunSurface.Report,
                        currentStepIndex = request.stepIndex,
                        lastMeaningfulOutput = output,
                        clearNextActionUrl = true
                    )
                )
            )
        }.onFailure { error ->
            callback(request.failed(error.message ?: "unsupported_android_action"))
        }
    }

    private fun installApk(step: KiteRecipeStep): String {
        val path = step.params?.optString("path")?.takeIf { it.isNotBlank() }
            ?: step.params?.optString("apk")?.takeIf { it.isNotBlank() }
            ?: step.cmd?.takeIf { it.isNotBlank() }
            ?: step.text?.takeIf { it.isNotBlank() }
            ?: error("install_apk_missing_path")
        val file = ContainerVisibleFileResolver.resolve(appContext, path)
            ?: error("install_apk_path_not_allowed")
        if (!file.isFile) error("install_apk_missing_file")
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "已打开安装器：${file.absolutePath}"
    }

    private fun stopRuntime(
        request: RecipeStopRequest,
        callback: (StopExecutionResult) -> Unit
    ) {
        val bridgeCallback: (BridgeResult) -> Unit = { result ->
            if (result.errorType == BridgeErrorType.Timeout) {
                deliverStopResult(request, verifyStopAfterTransportDeadline(request), callback)
            } else {
                deliverStopResult(request, result.toStopExecutionResult(), callback)
            }
        }
        val bridgeRunId = request.bridgeRunId()
        if (bridgeRunId != null) {
            bridgeClient.stopRun(
                recipe = request.recipe,
                runId = bridgeRunId,
                pid = request.pid,
                rootPid = request.rootPid,
                processGroupId = request.processGroupId,
                systemSessionId = request.systemSessionId,
                cardInstanceId = request.instanceId,
                runtimeOwnerIds = request.runtimeOwnerIds,
                callback = bridgeCallback
            )
        } else {
            bridgeClient.stopProcessBinding(
                recipe = request.recipe,
                runId = request.instanceId,
                pid = request.pid,
                rootPid = request.rootPid,
                processGroupId = request.processGroupId,
                systemSessionId = request.systemSessionId,
                cardInstanceId = request.instanceId,
                runtimeOwnerIds = request.runtimeOwnerIds,
                callback = bridgeCallback
            )
        }
    }

    /**
     * 传输等待窗口结束只触发一次 owner 定向核验，不产生“超时”终态，也不重复提交停止命令。
     * 最终结果只来自强身份进程事实；缺少 owner 时明确报告无法核验。
     */
    private fun verifyStopAfterTransportDeadline(request: RecipeStopRequest): StopExecutionResult {
        val ownerIds = request.runtimeOwnerIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (ownerIds.isEmpty()) {
            return StopExecutionResult(
                outcome = StopExecutionOutcome.VerificationUnavailable,
                message = "缺少运行 owner 身份，无法核验实例是否已经停止",
            )
        }

        val results = ProotOwnerProcessTerminator.terminateAll(appContext, ownerIds)
        ProotOwnerProcessTerminator.scheduleResidualReap(
            context = appContext,
            ownerIds = results.filterNot { it.settled }.map { it.ownerId },
        )
        val output = results.joinToString(separator = "") { it.toStopOutput() }
        val remaining = OwnerStopOutputEvidence.remainingProcessIds(output)
        val confirmed = results.all { it.settled } && remaining.isEmpty()
        return StopExecutionResult(
            outcome = when {
                confirmed -> StopExecutionOutcome.Confirmed
                remaining.isNotEmpty() || results.any {
                    it.outcome == ProotOwnerTerminationOutcome.STILL_RUNNING
                } -> StopExecutionOutcome.StillRunning
                else -> StopExecutionOutcome.VerificationUnavailable
            },
            message = OwnerStopOutputEvidence.userMessage(output)
                ?: if (confirmed) "已关闭" else "无法确认实例进程状态",
            remainingProcessIds = remaining,
        )
    }

    private fun deliverStopResult(
        request: RecipeStopRequest,
        result: StopExecutionResult,
        callback: (StopExecutionResult) -> Unit
    ) {
        if (result.outcome == StopExecutionOutcome.Confirmed && result.remainingProcessIds.isEmpty()) {
            TaskManagerStore.confirmOwnersStopped(request.runtimeOwnerIds)
        }
        scope.launch(Dispatchers.IO) {
            RuntimeFrameCoordinator.refreshProcessSnapshot(
                context = appContext,
                reason = "run-stop:${request.instanceId}"
            )
            RuntimeFrameCoordinator.scheduleProcessRefreshBurst(
                context = appContext,
                reason = "run-stop:${request.instanceId}"
            )
        }
        callback(result)
    }

    private fun BridgeResult.toStopExecutionResult(): StopExecutionResult {
        val observation = stopObservationText()
        val observationLines = observation.lineSequence().map(String::trim).toList()
        val ownerOutcomeUnconfirmed = OwnerStopOutputEvidence.hasUnconfirmedOwnerOutcome(observation)
        val bridgeConfirmedStop = status == KiteRunReport.STATUS_STOPPED && (ok || accepted)
        val residueMarkerObserved = bridgeConfirmedStop &&
            !ownerOutcomeUnconfirmed &&
            observationLines.any {
                it.startsWith("__kite_stop_remaining:") ||
                    it.startsWith("__kite_stop_remaining_pgid:")
            }
        val remaining = OwnerStopOutputEvidence.remainingProcessIds(observation)
        val confirmed = !ownerOutcomeUnconfirmed &&
            remaining.isEmpty() &&
            (ok || accepted) &&
            status == KiteRunReport.STATUS_STOPPED
        val ownerMessage = OwnerStopOutputEvidence.userMessage(observation)
        return StopExecutionResult(
            outcome = when {
                confirmed -> StopExecutionOutcome.Confirmed
                remaining.isNotEmpty() || observation.contains("__kite_owner_stop_outcome:STILL_RUNNING") ->
                    StopExecutionOutcome.StillRunning
                ownerOutcomeUnconfirmed || errorType == BridgeErrorType.Timeout ->
                    StopExecutionOutcome.VerificationUnavailable
                errorType == BridgeErrorType.ConnectionError -> StopExecutionOutcome.ConnectionError
                errorType == BridgeErrorType.UnsupportedEndpoint -> StopExecutionOutcome.Unsupported
                errorType == BridgeErrorType.ParseError -> StopExecutionOutcome.ParseError
                else -> StopExecutionOutcome.Failed
            },
            message = ownerMessage ?: runReport?.lastMeaningfulOutput() ?: message,
            remainingProcessIds = remaining,
            manualKillObserved = bridgeConfirmedStop &&
                !ownerOutcomeUnconfirmed &&
                MANUAL_STOP_KILLED_REGEX.containsMatchIn(observation),
            residueMarkerObserved = residueMarkerObserved
        )
    }

    private fun BridgeResult.stopObservationText(): String = buildString {
        runReport?.steps.orEmpty().forEach { step ->
            appendLine(step.lastMeaningfulOutput)
            appendLine(step.stdoutTail)
            appendLine(step.stderrTail)
        }
        appendLine(message)
        appendLine(rawBody)
    }

    internal fun browserEnvironment(request: RecipeStepExecutionRequest, source: String): Map<String, String> =
        KiteBrowserProxyInstaller.environment(
            context = appContext,
            recipeId = request.recipe.id,
            instanceId = request.instanceId,
            source = source
        ) + listOfNotNull(
            request.runtimeOwnerId?.takeIf { it.isNotBlank() }
                ?.let { RuntimeOwnerIdentity.RUNTIME_ID_ENV to it },
            request.runtimeUnitId?.takeIf { it.isNotBlank() }
                ?.let { RuntimeOwnerIdentity.UNIT_ID_ENV to it }
        ).toMap() + executionEnvironmentProvider.environment(request)

    private fun RecipeStepExecutionRequest.runtimeNamespace(): RuntimeOwnerNamespace =
        if (runtimeRootOwnerId?.startsWith("resource:") == true) {
            RuntimeOwnerNamespace.Resource
        } else {
            RuntimeOwnerNamespace.Card
        }

    private fun KiteRecipe.singleStepRecipe(step: KiteRecipeStep): KiteRecipe = copy(
        execution = KiteExecution.steps(listOf(step)),
        actions = linkedMapOf(
            KiteRecipe.ACTION_START to KiteRecipeAction(
                id = KiteRecipe.ACTION_START,
                steps = listOf(step),
                expected = step.expected ?: expected
            )
        ),
        expected = step.expected ?: expected
    )

    private fun RecipeStepExecutionRequest.failed(
        message: String,
        bridgeUnavailable: Boolean = false,
        mutation: RunStateMutation? = null
    ): RecipeExecutionEvent.Failed = RecipeExecutionEvent.Failed(
        instanceId = instanceId,
        generation = generation,
        stepIndex = stepIndex,
        message = message,
        bridgeUnavailable = bridgeUnavailable,
        mutation = mutation
    )

    private fun terminalTitle(recipe: KiteRecipe, stepIndex: Int): String {
        val order = recipe.steps.take(stepIndex + 1).count { it.type == KiteRecipe.STEP_TERMINAL }.coerceAtLeast(1)
        val suffix = if (recipe.steps.count { it.type == KiteRecipe.STEP_TERMINAL } > 1) "终端 $order" else "终端"
        return "${recipe.name.trim().ifBlank { "Kite 卡片" }} · $suffix"
    }

    private fun shellReportText(report: KiteRunReport?, recipe: KiteRecipe): String? {
        val shellSteps = report?.steps?.filter { it.type == KiteRecipe.STEP_SHELL }.orEmpty()
        if (shellSteps.isEmpty()) return null
        val recipeStepsById = recipe.steps.associateBy { it.id }
        return shellSteps.joinToString("\n\n") { stepReport ->
            shellStepReportText(stepReport, recipeStepsById[stepReport.stepId])
        }
    }

    private fun shellStepReportText(report: KiteStepReport, step: KiteRecipeStep?): String = buildList {
        step?.cmd?.takeIf { it.isNotBlank() }?.let { add("命令：$it") }
        add("结果：${shellReportStatusLabel(report)}")
        report.exitCode?.let { add("退出码：$it（0 表示命令成功结束）") }
        report.lastMeaningfulOutput.trim().takeIf { it.isNotBlank() }?.let { add("有效输出：$it") }
        val rawOutput = report.stdoutTail.trim()
        if (rawOutput.isNotBlank() && rawOutput != report.lastMeaningfulOutput.trim()) add("原始输出：\n$rawOutput")
        report.stderrTail.trim().takeIf { it.isNotBlank() && it != rawOutput }?.let { add("错误输出：$it") }
        report.matchResult?.takeIf { it.enabled }?.let { add("匹配：${if (it.matched) "通过" else "未通过"}（${it.text}）") }
        if (rawOutput.isBlank() && report.lastMeaningfulOutput.isBlank()) add("输出：命令没有打印内容。")
    }.joinToString("\n")

    private fun shellReportStatusLabel(report: KiteStepReport): String = when {
        report.status == KiteRunReport.STATUS_FINISHED && report.exitCode == 0 -> "成功"
        report.status == KiteRunReport.STATUS_RUNNING -> "已启动"
        report.status == KiteRunReport.STATUS_STOPPED -> "已停止"
        report.status == KiteRunReport.STATUS_FAILED -> "失败"
        else -> report.status
    }

    private fun String.normalizedShellOutput(): String =
        replace(ANSI_ESCAPE_REGEX, "")
            .replace('\r', '\n')
            .lineSequence()
            .joinToString("\n") { it.trimEnd() }
            .trimEnd()

    private fun extractPid(text: String?): String? = text
        ?.let { Regex("""pid\s*[:=]\s*(\d+)|pid\s+(\d+)""", RegexOption.IGNORE_CASE).find(it) }
        ?.groupValues
        ?.drop(1)
        ?.firstOrNull { it.isNotBlank() }

    companion object {
        internal fun requiresActiveWorkspacePreparation(environment: Map<String, String>): Boolean =
            environment[ProotViewBinding.ENV_VIEW_ID].isNullOrBlank()

        internal fun shellProcessExited(status: String?, ok: Boolean?): Boolean =
            status == KiteRunReport.STATUS_FINISHED && ok == true

        internal fun runtimePreparationMutation(stepType: String, stepIndex: Int): RunStateMutation =
            RunStateMutation(
                status = CardRunStatus.Running,
                surface = RunOrchestrator.surfaceFor(stepType),
                currentStepIndex = stepIndex,
                runtimeLane = SHELL_RUNTIME_LANE.takeIf { stepType == KiteRecipe.STEP_SHELL },
                runtimeFallbackReason = SHELL_RUNTIME_REASON.takeIf { stepType == KiteRecipe.STEP_SHELL },
                lastMeaningfulOutput = when (stepType) {
                    KiteRecipe.STEP_TERMINAL -> "正在准备终端环境"
                    KiteRecipe.STEP_X11 -> "正在准备 X11 环境"
                    KiteRecipe.STEP_SHELL -> "正在准备 SH 环境"
                    else -> "正在准备运行环境"
                }
            )

        internal const val SHELL_RUNTIME_LANE = "proot_shell"
        internal const val SHELL_RUNTIME_REASON = "shell_command_requires_proot"
        private const val TERMINAL_COMMAND_DELAY_MS = 650L
        private val TERMINAL_FINISHED_STATUSES = setOf(
            ManagedTerminalStatus.EXITED,
            ManagedTerminalStatus.FAILED,
            ManagedTerminalStatus.STOPPED
        )
        private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
        private val MANUAL_STOP_KILLED_REGEX = Regex("""\bKilled\b""", RegexOption.IGNORE_CASE)
    }
}
