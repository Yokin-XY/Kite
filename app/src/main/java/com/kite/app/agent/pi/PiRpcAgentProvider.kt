package com.kite.app.agent.pi

import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentFailures
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPromptCapabilities
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentSessionCapabilities
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.contract.AgentSessionSnapshot
import com.kite.app.agent.contract.AgentStopReason
import com.kite.app.agent.contract.AgentToolCall
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentToolKind
import com.kite.app.agent.contract.AgentToolStatus
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import com.kite.app.agent.process.AgentProcessChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

data class PiRpcProviderDescriptor(
    val id: String,
    val name: String,
    val title: String? = null,
    val version: String? = null,
)

fun interface PiRpcProcessLauncher {
    suspend fun launch(): AgentProcessChannel
}

/** Pi Coding Agent 官方 RPC 到 Kite Agent SDK 的专用适配器。 */
class PiRpcAgentProvider(
    private val descriptor: PiRpcProviderDescriptor,
    private val launcher: PiRpcProcessLauncher,
    private val initializeTimeoutMs: Long = 15_000L,
    private val diagnosticSink: (String) -> Unit = {},
) : KiteAgentProvider {
    override val id: String = descriptor.id

    override suspend fun connect(
        request: AgentConnectionRequest,
        client: AgentClientEndpoint,
    ): AgentOperationResult<KiteAgentConnection> {
        val process = try {
            launcher.launch()
        } catch (error: Throwable) {
            return AgentFailures.launch("无法启动 ${descriptor.name}: ${error.message}", error)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("PiRpc-${descriptor.id}"))
        val rpc = PiRpc(process, scope, diagnosticSink)
        return try {
            rpc.start()
            val state = withTimeout(initializeTimeoutMs) { rpc.request("get_state").toPiState() }
                ?: error("Pi RPC 未返回会话状态")
            val models = rpc.request("get_available_models")
                .optJSONObject("data")?.optJSONArray("models").objects().mapNotNull(JSONObject::toPiModel)
                .distinctBy(PiModel::selectionId)
            val levels = loadThinkingLevels(rpc)
            val commands = loadCommands(rpc)
            val connection = PiRpcConnection(
                descriptor = descriptor,
                process = process,
                scope = scope,
                rpc = rpc,
                endpoint = client,
                initialState = state,
                models = models,
                thinkingLevels = levels,
                commands = commands,
            )
            rpc.eventHandler = connection::onEvent
            AgentOperationResult.Success(connection)
        } catch (error: Throwable) {
            rpc.close(error)
            runCatching { process.stop() }
            scope.cancel("Pi RPC initialize failed", error)
            AgentFailures.initialize("${descriptor.name} RPC 初始化失败: ${error.message}", error)
        }
    }
}

private class PiRpcConnection(
    descriptor: PiRpcProviderDescriptor,
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val rpc: PiRpc,
    private val endpoint: AgentClientEndpoint,
    initialState: PiState,
    models: List<PiModel>,
    thinkingLevels: List<String>,
    private val commands: List<AgentCommand>,
) : KiteAgentConnection {
    override val provider = AgentProviderInfo(descriptor.id, descriptor.name, descriptor.version, descriptor.title)
    override val capabilities = AgentCapabilities(
        prompt = AgentPromptCapabilities(text = true, resourceLinks = true, images = true),
        sessions = AgentSessionCapabilities(close = true, rename = true),
    )
    private val modelsById = models.associateBy(PiModel::selectionId)
    private var state = initialState
    private var availableThinkingLevels = thinkingLevels
    private var activeSessionId: String? = null
    private var activeTurn: CompletableDeferred<AgentTurnResult>? = null
    private val disconnecting = AtomicBoolean(false)

    init {
        scope.launch(Dispatchers.IO + CoroutineName("${provider.id}-process-exit")) {
            val exitCode = process.awaitExit()
            if (!disconnecting.get()) activeSessionId?.let { sessionId ->
                endpoint.eventSink.onEvent(
                    sessionId,
                    AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, "${provider.name} RPC 进程已退出，exitCode=$exitCode"),
                )
                activeTurn?.completeExceptionally(IllegalStateException("Pi RPC 进程已退出"))
            }
        }
    }

    override suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        operation("创建会话") {
            rpc.request("new_session")
            state = rpc.request("get_state").toPiState() ?: error("Pi RPC 未返回新会话状态")
            availableThinkingLevels = loadThinkingLevels(rpc)
            activeSessionId = state.sessionId
            snapshot().also { announceReady(it.id) }
        }

    override suspend fun loadSession(request: AgentExistingSessionRequest) = AgentOperationResult.Unsupported("session/load")
    override suspend fun listSessions(request: AgentSessionListRequest) = AgentOperationResult.Unsupported("session/list")
    override suspend fun resumeSession(request: AgentExistingSessionRequest) = AgentOperationResult.Unsupported("session/resume")
    override suspend fun forkSession(request: AgentExistingSessionRequest) = AgentOperationResult.Unsupported("session/fork")

    override suspend fun closeSession(sessionId: String): AgentOperationResult<Unit> {
        if (activeSessionId != sessionId) return AgentOperationResult.Failure("会话不存在: $sessionId")
        activeSessionId = null
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed))
        return AgentOperationResult.Success(Unit)
    }

    override suspend fun deleteSession(sessionId: String) = AgentOperationResult.Unsupported("session/delete")

    override suspend fun renameSession(request: AgentSessionRenameRequest): AgentOperationResult<Unit> {
        if (activeSessionId != request.sessionId) return AgentOperationResult.Failure("会话不存在: ${request.sessionId}")
        return operation("重命名会话") {
            rpc.request("set_session_name", JSONObject().put("name", request.title))
            state = state.copy(sessionName = request.title)
            endpoint.eventSink.onEvent(request.sessionId, AgentSessionEvent.SessionInfoChanged(title = request.title))
        }
    }

    override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> {
        if (activeSessionId != request.sessionId) return AgentOperationResult.Failure("会话不存在: ${request.sessionId}")
        val payload = request.content.toPiPrompt()
            ?: return AgentOperationResult.Unsupported("pi-rpc-unsupported-input")
        if (activeTurn != null) return AgentOperationResult.Failure("Pi 当前已有一轮回复正在生成")
        val completion = CompletableDeferred<AgentTurnResult>()
        activeTurn = completion
        endpoint.eventSink.onEvent(request.sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting))
        return try {
            rpc.request(
                "prompt",
                JSONObject().put("message", payload.message).apply {
                    if (payload.images.length() > 0) put("images", payload.images)
                },
            )
            AgentOperationResult.Success(completion.await())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            endpoint.eventSink.onEvent(request.sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, error.message))
            AgentOperationResult.Failure("Pi 发送消息失败: ${error.message}", error)
        } finally {
            if (activeTurn === completion) activeTurn = null
        }
    }

    override suspend fun steer(request: AgentPromptRequest): AgentOperationResult<Unit> {
        if (activeSessionId != request.sessionId) {
            return AgentOperationResult.Failure("会话不存在: ${request.sessionId}")
        }
        if (activeTurn == null) return AgentOperationResult.Failure("Pi 当前没有可插话的回复")
        val payload = request.content.toPiPrompt()
            ?: return AgentOperationResult.Unsupported("pi-rpc-unsupported-input")
        return operation("插入 Pi 当前回复") {
            rpc.request(
                "steer",
                JSONObject().put("message", payload.message).apply {
                    if (payload.images.length() > 0) put("images", payload.images)
                },
            )
            Unit
        }
    }

    override suspend fun setConfiguration(
        sessionId: String,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<List<AgentConfigOption>> {
        if (activeSessionId != sessionId) return AgentOperationResult.Failure("会话不存在: $sessionId")
        val selected = (value as? AgentConfigValue.Select)?.value
            ?: return AgentOperationResult.Failure("Pi 配置必须是单选值")
        return operation("更新 Pi 会话配置") {
            when (configId) {
                MODEL_CONFIG_ID -> {
                    val model = modelsById[selected] ?: error("Pi 未提供该模型")
                    rpc.request("set_model", JSONObject().put("provider", model.provider).put("modelId", model.id))
                    state = state.copy(model = model)
                    availableThinkingLevels = loadThinkingLevels(rpc)
                    state = rpc.request("get_state").toPiState() ?: state
                }
                THINKING_CONFIG_ID -> {
                    if (selected !in availableThinkingLevels) error("当前模型不支持该推理强度")
                    rpc.request("set_thinking_level", JSONObject().put("level", selected))
                    state = state.copy(thinkingLevel = selected)
                }
                else -> error("Pi 不支持该配置项")
            }
            configuration().also { endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ConfigurationUpdated(it)) }
        }
    }

    override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> {
        if (activeSessionId != sessionId) return AgentOperationResult.Failure("会话不存在: $sessionId")
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelling))
        return operation("取消生成") { rpc.request("abort"); Unit }
    }

    override suspend fun disconnect() {
        if (!disconnecting.compareAndSet(false, true)) return
        activeTurn?.cancel()
        activeSessionId?.let { endpoint.eventSink.onEvent(it, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed)) }
        activeSessionId = null
        rpc.close()
        process.stop()
        scope.cancel("Pi RPC disconnected")
    }

    internal suspend fun onEvent(event: JSONObject) {
        val sessionId = activeSessionId ?: return
        when (event.optString("type")) {
            "agent_start" -> endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting))
            "agent_settled" -> {
                activeTurn?.complete(AgentTurnResult(AgentStopReason.EndTurn))
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
            }
            "message_update" -> event.optJSONObject("assistantMessageEvent")?.let { update ->
                when (update.optString("type")) {
                    "text_delta" -> emitText(sessionId, AgentMessageRole.Assistant, update.optString("delta"))
                    "thinking_delta" -> emitText(sessionId, AgentMessageRole.Thought, update.optString("delta"))
                }
            }
            "tool_execution_start" -> {
                val id = event.optString("toolCallId")
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ToolCallStarted(
                    AgentToolCall(
                        id = id,
                        title = event.optString("toolName", "工具"),
                        kind = AgentToolKind(event.optString("toolName", "tool")),
                        status = AgentToolStatus("in_progress"),
                        rawInput = event.optJSONObject("args")?.toString(),
                    )
                ))
            }
            "tool_execution_update", "tool_execution_end" -> {
                val finished = event.optString("type") == "tool_execution_end"
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ToolCallUpdated(
                    AgentToolCallPatch(
                        id = event.optString("toolCallId"),
                        status = AgentToolStatus(if (!finished) "in_progress" else if (event.optBoolean("isError")) "failed" else "completed"),
                        rawOutput = (event.optJSONObject(if (finished) "result" else "partialResult"))?.toString(),
                    )
                ))
            }
            "extension_error" -> {
                val message = event.optString("error").ifBlank { "Pi 扩展执行失败" }
                activeTurn?.completeExceptionally(IllegalStateException(message))
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, message))
            }
        }
    }

    private fun announceReady(sessionId: String) {
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.CommandsUpdated(commands))
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ConfigurationUpdated(configuration()))
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
    }

    private fun snapshot() = AgentSessionSnapshot(state.sessionId, configuration())

    private fun configuration(): List<AgentConfigOption> = buildList {
        val currentModel = state.model
        if (modelsById.isNotEmpty() && currentModel != null) add(
            AgentConfigOption.Select(
                id = MODEL_CONFIG_ID,
                name = "模型",
                description = "来自 Pi 当前配置的真实模型目录",
                category = AgentConfigCategory.Model,
                currentValue = currentModel.selectionId,
                choices = modelsById.values.map { model ->
                    AgentConfigChoice(
                        value = model.selectionId,
                        name = model.name,
                        groupId = model.provider,
                        groupName = model.provider,
                        modelSource = AgentModelSource.UserConfigured,
                    )
                },
            )
        )
        val levels = availableThinkingLevels.mapNotNull { level -> piReasoningSemantics(level)?.let { level to it } }
        if (levels.size > 1) add(
            AgentConfigOption.Select(
                id = THINKING_CONFIG_ID,
                name = "推理强度",
                description = "只显示当前模型由 Pi 声明支持的档位",
                category = AgentConfigCategory.ThoughtLevel,
                currentValue = state.thinkingLevel?.takeIf { it in availableThinkingLevels } ?: levels.first().first,
                choices = levels.map { (native, semantics) ->
                    AgentConfigChoice(native, semantics.displayName, semantics.description, reasoning = semantics)
                },
            )
        )
    }

    private fun emitText(sessionId: String, role: AgentMessageRole, text: String) {
        if (text.isNotEmpty()) endpoint.eventSink.onEvent(
            sessionId,
            AgentSessionEvent.MessageChunk(role, AgentContent.Text(text)),
        )
    }

    private suspend fun <T> operation(label: String, block: suspend () -> T): AgentOperationResult<T> = try {
        AgentOperationResult.Success(block())
    } catch (error: Throwable) {
        AgentOperationResult.Failure("$label 失败: ${error.message}", error)
    }

    private companion object {
        const val MODEL_CONFIG_ID = "pi.rpc.model"
        const val THINKING_CONFIG_ID = "pi.rpc.thinking"
    }
}

private suspend fun loadThinkingLevels(rpc: PiRpc): List<String> =
    rpc.request("get_available_thinking_levels")
        .optJSONObject("data")?.optJSONArray("levels").strings().distinct()

private suspend fun loadCommands(rpc: PiRpc): List<AgentCommand> =
    rpc.request("get_commands")
        .optJSONObject("data")?.optJSONArray("commands").objects().mapNotNull { command ->
            command.optString("name").takeIf(String::isNotBlank)?.let { name ->
                AgentCommand(name, command.optString("description").ifBlank { name })
            }
        }
