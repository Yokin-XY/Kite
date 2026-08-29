package com.kite.app.agent.zcode

import com.kite.app.agent.contract.AGENT_SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
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
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPermissionRequest
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
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.contract.AgentStopReason
import com.kite.app.agent.contract.AgentToolCall
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentToolKind
import com.kite.app.agent.contract.AgentToolStatus
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.AgentTurnUsage
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class ZCodeAppServerProviderDescriptor(
    val id: String,
    val name: String,
    val title: String? = null,
    val version: String? = null,
)

fun interface ZCodeAppServerProcessLauncher {
    suspend fun launch(): AgentProcessChannel
}

/** ZCode 原生 app-server 到 Kite Agent SDK 的专用适配器。 */
class ZCodeAppServerAgentProvider(
    private val descriptor: ZCodeAppServerProviderDescriptor,
    private val launcher: ZCodeAppServerProcessLauncher,
    private val initializeTimeoutMs: Long = DEFAULT_INITIALIZE_TIMEOUT_MS,
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
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineName("ZCodeAppServer-${descriptor.id}")
        )
        val rpc = ZCodeAppServerRpc(process, scope, diagnosticSink)
        return try {
            rpc.start()
            withTimeout(initializeTimeoutMs) {
                rpc.request(
                    "session/list",
                    JSONObject().put("includeArchived", false).put("limit", 1),
                )
            }
            val connection = ZCodeAppServerConnection(descriptor, process, scope, rpc, client)
            rpc.notificationHandler = connection::onNotification
            rpc.serverRequestHandler = connection::onServerRequest
            AgentOperationResult.Success(connection)
        } catch (error: Throwable) {
            rpc.close(error)
            runCatching { process.stop() }
            scope.cancel("ZCode app-server initialize failed", error)
            AgentFailures.initialize("${descriptor.name} app-server 初始化失败: ${error.message}", error)
        }
    }

    private companion object {
        const val DEFAULT_INITIALIZE_TIMEOUT_MS = 30_000L
    }
}

private data class ZCodeSessionState(
    val id: String,
    var cwd: String,
    var model: ZCodeModel?,
    var models: List<ZCodeModel>,
    var thoughtLevel: String?,
    var thoughtLevels: List<String>,
    var mode: String,
)

private class ZCodeAppServerConnection(
    descriptor: ZCodeAppServerProviderDescriptor,
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val rpc: ZCodeAppServerRpc,
    private val endpoint: AgentClientEndpoint,
) : KiteAgentConnection {
    override val provider = AgentProviderInfo(descriptor.id, descriptor.name, descriptor.version, descriptor.title)
    override val capabilities = AgentCapabilities(
        prompt = AgentPromptCapabilities(text = true, resourceLinks = true, embeddedResources = true),
        sessions = AgentSessionCapabilities(load = true, list = true, resume = true, close = true),
    )

    private val sessions = ConcurrentHashMap<String, ZCodeSessionState>()
    private val activeTurns = ConcurrentHashMap<String, CompletableDeferred<AgentTurnResult>>()
    private val streamedAssistant = ConcurrentHashMap.newKeySet<String>()
    private val startedTools = ConcurrentHashMap.newKeySet<String>()
    private val subscribed = ConcurrentHashMap.newKeySet<String>()
    private val disconnecting = AtomicBoolean(false)

    init {
        scope.launch(Dispatchers.IO + CoroutineName("${provider.id}-process-exit")) {
            val exitCode = process.awaitExit()
            if (!disconnecting.get()) {
                val error = IllegalStateException("ZCode app-server 进程已退出，exitCode=$exitCode")
                activeTurns.values.forEach { it.completeExceptionally(error) }
                sessions.keys.forEach { sessionId ->
                    endpoint.eventSink.onEvent(
                        sessionId,
                        AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, error.message),
                    )
                }
            }
        }
    }

    override suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        operation("创建 ZCode 会话") {
            val workspace = JSONObject()
                .put("workspaceKey", request.cwd)
                .put("workspacePath", request.cwd)
            val response = rpc.request(METHOD_SESSION_CREATE, JSONObject().put("workspace", workspace))
            val state = response.toSessionState(request.cwd)
                ?: error("ZCode session/create 未返回会话")
            sessions[state.id] = state
            subscribe(state.id)
            snapshot(state).also { announceReady(state) }
        }

    override suspend fun loadSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        restoreSession(request, "加载 ZCode 会话")

    override suspend fun resumeSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        restoreSession(request, "恢复 ZCode 会话")

    private suspend fun restoreSession(
        request: AgentExistingSessionRequest,
        label: String,
    ): AgentOperationResult<AgentSessionSnapshot> = operation(label) {
        val response = rpc.request(
            METHOD_SESSION_RESUME,
            JSONObject().put("sessionId", request.sessionId),
        )
        val state = response.toSessionState(request.cwd)
            ?: error("ZCode session/resume 未返回会话")
        sessions[state.id] = state
        replayHistory(state.id)
        subscribe(state.id)
        snapshot(state).also { announceReady(state) }
    }

    override suspend fun listSessions(request: AgentSessionListRequest): AgentOperationResult<AgentSessionPage> {
        if (request.cursor != null) return AgentOperationResult.Success(AgentSessionPage(emptyList()))
        return operation("列出 ZCode 会话") {
            val response = rpc.request(
                METHOD_SESSION_LIST,
                JSONObject().put("includeArchived", false).put("limit", SESSION_LIST_LIMIT),
            )
            val listed = response.optJSONArray("sessions").zcodeObjects().mapNotNull { item ->
                val sessionId = item.nullableString("sessionId") ?: return@mapNotNull null
                val workspace = item.optJSONObject("workspace") ?: JSONObject()
                val cwd = workspace.nullableString("workspacePath").orEmpty()
                if (request.cwd != null && cwd != request.cwd) return@mapNotNull null
                AgentSessionSummary(
                    id = sessionId,
                    cwd = cwd,
                    title = item.nullableString("title"),
                    updatedAt = item.optLong("updatedAt").takeIf { it > 0L }
                        ?.let { Instant.ofEpochMilli(it).toString() },
                )
            }
            AgentSessionPage(listed)
        }
    }

    override suspend fun forkSession(request: AgentExistingSessionRequest) =
        AgentOperationResult.Unsupported("session/fork")

    override suspend fun closeSession(sessionId: String): AgentOperationResult<Unit> {
        if (sessions.remove(sessionId) == null) return AgentOperationResult.Failure("会话不存在: $sessionId")
        subscribed.remove(sessionId)
        activeTurns.remove(sessionId)?.cancel()
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed))
        return operation("关闭 ZCode 会话") {
            rpc.request(METHOD_SESSION_CLOSE, JSONObject().put("sessionId", sessionId))
            Unit
        }
    }

    override suspend fun deleteSession(sessionId: String) = AgentOperationResult.Unsupported("session/delete")

    override suspend fun renameSession(request: AgentSessionRenameRequest) =
        AgentOperationResult.Unsupported("session/rename")

    override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> {
        val state = sessions[request.sessionId]
            ?: return AgentOperationResult.Failure("会话不存在: ${request.sessionId}")
        val content = request.content.toZCodePrompt()
            ?: return AgentOperationResult.Unsupported("zcode-app-server-unsupported-input")
        if (activeTurns.containsKey(request.sessionId)) {
            return AgentOperationResult.Failure("ZCode 当前已有一轮回复正在生成")
        }
        val completion = CompletableDeferred<AgentTurnResult>()
        activeTurns[request.sessionId] = completion
        streamedAssistant.remove(request.sessionId)
        endpoint.eventSink.onEvent(
            request.sessionId,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting),
        )
        return try {
            refreshSession(state)
            subscribe(request.sessionId)
            rpc.request(
                METHOD_SESSION_SEND,
                JSONObject()
                    .put("sessionId", request.sessionId)
                    .put("content", content)
                    .apply {
                        request.messageId?.takeIf(String::isNotBlank)?.let { id ->
                            put("inputId", id)
                            put("queryId", id)
                        }
                    },
            )
            AgentOperationResult.Success(completion.await())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, error.message),
            )
            AgentFailures.protocol("ZCode 发送消息失败: ${error.message}", error)
        } finally {
            activeTurns.remove(request.sessionId, completion)
        }
    }

    override suspend fun setConfiguration(
        sessionId: String,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<List<AgentConfigOption>> {
        val state = sessions[sessionId] ?: return AgentOperationResult.Failure("会话不存在: $sessionId")
        val selected = (value as? AgentConfigValue.Select)?.value
            ?: return AgentOperationResult.Failure("ZCode 配置必须是单选值")
        return operation("更新 ZCode 会话配置") {
            when (configId) {
                MODEL_CONFIG_ID -> {
                    val model = state.models.firstOrNull { it.selectionId == selected }
                        ?: error("ZCode 未提供该模型")
                    rpc.request(
                        METHOD_SESSION_SET_MODEL,
                        JSONObject().put("sessionId", sessionId).put("model", model.toProtocol()),
                    )
                }
                THOUGHT_CONFIG_ID -> {
                    if (selected !in state.thoughtLevels) error("当前模型不支持该推理强度")
                    rpc.request(
                        METHOD_SESSION_SET_THOUGHT,
                        JSONObject().put("sessionId", sessionId).put("thoughtLevel", selected),
                    )
                }
                AGENT_SESSION_PERMISSION_CONFIG_ID -> setNativeMode(state, selected)
                else -> error("ZCode 不支持该配置项")
            }
            refreshSession(state)
            configuration(state).also { options ->
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ConfigurationUpdated(options))
            }
        }
    }

    override suspend fun setMode(sessionId: String, modeId: String): AgentOperationResult<Unit> {
        val state = sessions[sessionId] ?: return AgentOperationResult.Failure("会话不存在: $sessionId")
        return operation("更新 ZCode 执行模式") {
            setNativeMode(state, modeId)
            refreshSession(state)
            endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ConfigurationUpdated(configuration(state)))
        }
    }

    override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> {
        if (sessions[sessionId] == null) return AgentOperationResult.Failure("会话不存在: $sessionId")
        if (activeTurns[sessionId] == null) return AgentOperationResult.Failure("ZCode 当前没有正在生成的回复")
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelling))
        return operation("取消 ZCode 生成") {
            rpc.request(METHOD_SESSION_STOP, JSONObject().put("sessionId", sessionId))
            Unit
        }
    }

    override suspend fun disconnect() {
        if (!disconnecting.compareAndSet(false, true)) return
        activeTurns.values.forEach { it.cancel() }
        activeTurns.clear()
        sessions.keys.forEach { sessionId ->
            endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed))
        }
        sessions.clear()
        rpc.close()
        process.stop()
        scope.cancel("ZCode app-server disconnected")
    }

    internal suspend fun onNotification(method: String, params: JSONObject) {
        if (method != METHOD_SESSION_EVENT) return
        val sessionId = params.nullableString("sessionId") ?: return
        val payload = params.optJSONObject("payload") ?: JSONObject()
        when (params.optString("type")) {
            "model.streaming" -> {
                val delta = payload.optString("delta")
                if (delta.isBlank()) return
                val role = when (payload.optString("kind")) {
                    "reasoning_delta" -> AgentMessageRole.Thought
                    "text_delta", "" -> AgentMessageRole.Assistant
                    else -> return
                }
                if (role == AgentMessageRole.Assistant) streamedAssistant += sessionId
                endpoint.eventSink.onEvent(
                    sessionId,
                    AgentSessionEvent.MessageChunk(
                        role = role,
                        content = AgentContent.Text(delta),
                        messageId = params.nullableString("turnId"),
                    ),
                )
            }
            // ZCode 同时发 model.streaming 与持久化 part.delta，消费两者会让正文重复。
            "part.delta" -> Unit
            "tool.updated" -> emitToolUpdate(sessionId, payload)
            "turn.completed" -> completeTurn(sessionId, payload)
            "turn.failed" -> failTurn(sessionId, payload)
        }
    }

    internal suspend fun onServerRequest(method: String, params: JSONObject): JSONObject = when (method) {
        METHOD_RUNTIME_PREFERENCES -> JSONObject().put("nativeSearchEnhancementsEnabled", true)
        METHOD_PERMISSION_REQUEST -> handlePermissionRequest(params)
        else -> throw UnsupportedOperationException("ZCode app-server 请求暂不支持: $method")
    }

    private suspend fun handlePermissionRequest(params: JSONObject): JSONObject {
        val sessionId = params.nullableString("sessionId") ?: error("ZCode 权限请求缺少 sessionId")
        endpoint.eventSink.onEvent(
            sessionId,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.WaitingPermission),
        )
        val outcome = endpoint.permissionHandler.request(
            AgentPermissionRequest(
                sessionId = sessionId,
                toolCall = AgentToolCallPatch(
                    id = params.nullableString("toolCallId") ?: params.optString("requestId"),
                    title = params.nullableString("toolName") ?: "ZCode 工具",
                    rawInput = params.opt("input")?.takeUnless { it == JSONObject.NULL }?.toString()
                        ?: params.nullableString("reason"),
                ),
                options = listOf(
                    AgentPermissionOption("allow", "允许一次", AgentPermissionKind.AllowOnce),
                    AgentPermissionOption("deny", "拒绝一次", AgentPermissionKind.RejectOnce),
                ),
            )
        )
        endpoint.eventSink.onEvent(
            sessionId,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting),
        )
        val decision = (outcome as? AgentPermissionOutcome.Selected)?.optionId
            ?.takeIf { it == "allow" } ?: "deny"
        return JSONObject().put("decision", decision)
    }

    private fun emitToolUpdate(sessionId: String, payload: JSONObject) {
        val id = payload.nullableString("toolCallId") ?: return
        val name = payload.nullableString("toolName") ?: "ZCode 工具"
        when (payload.optString("kind")) {
            "scheduled", "started" -> if (startedTools.add(id)) {
                endpoint.eventSink.onEvent(
                    sessionId,
                    AgentSessionEvent.ToolCallStarted(
                        AgentToolCall(
                            id = id,
                            title = name,
                            kind = AgentToolKind(name),
                            status = AgentToolStatus("in_progress"),
                            rawInput = payload.opt("input")?.takeUnless { it == JSONObject.NULL }?.toString()
                                ?: payload.nullableString("description"),
                        ),
                    ),
                )
            }
            "result", "error" -> {
                if (startedTools.add(id)) {
                    endpoint.eventSink.onEvent(
                        sessionId,
                        AgentSessionEvent.ToolCallStarted(
                            AgentToolCall(id, name, AgentToolKind(name), AgentToolStatus("in_progress")),
                        ),
                    )
                }
                val failed = payload.optString("kind") == "error"
                endpoint.eventSink.onEvent(
                    sessionId,
                    AgentSessionEvent.ToolCallUpdated(
                        AgentToolCallPatch(
                            id = id,
                            status = AgentToolStatus(if (failed) "failed" else "completed"),
                            rawOutput = payload.opt(if (failed) "error" else "result")
                                ?.takeUnless { it == JSONObject.NULL }?.toString(),
                        ),
                    ),
                )
                startedTools.remove(id)
            }
        }
    }

    private fun completeTurn(sessionId: String, payload: JSONObject) {
        if (sessionId !in streamedAssistant) {
            payload.nullableString("response")?.let { response ->
                endpoint.eventSink.onEvent(
                    sessionId,
                    AgentSessionEvent.MessageChunk(AgentMessageRole.Assistant, AgentContent.Text(response)),
                )
            }
        }
        val usage = payload.optJSONObject("usage")?.let { raw ->
            val input = raw.optLong("inputTokens")
            val output = raw.optLong("outputTokens")
            AgentTurnUsage(
                inputTokens = input,
                outputTokens = output,
                totalTokens = input + output,
                cachedReadTokens = raw.optLong("cacheReadTokens"),
                cachedWriteTokens = raw.optLong("cacheCreationTokens"),
            )
        }
        activeTurns[sessionId]?.complete(AgentTurnResult(AgentStopReason.EndTurn, usage = usage))
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
        streamedAssistant.remove(sessionId)
    }

    private fun failTurn(sessionId: String, payload: JSONObject) {
        val message = payload.opt("error")?.takeUnless { it == JSONObject.NULL }?.toString()
            ?.takeIf(String::isNotBlank) ?: "ZCode 运行失败"
        activeTurns[sessionId]?.completeExceptionally(IllegalStateException(message))
        endpoint.eventSink.onEvent(
            sessionId,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, message),
        )
        streamedAssistant.remove(sessionId)
    }

    private suspend fun refreshSession(state: ZCodeSessionState) {
        val response = rpc.request(METHOD_SESSION_RESUME, JSONObject().put("sessionId", state.id))
        response.toSessionState(state.cwd)?.let { refreshed ->
            state.cwd = refreshed.cwd
            state.model = refreshed.model
            state.models = refreshed.models
            state.thoughtLevel = refreshed.thoughtLevel
            state.thoughtLevels = refreshed.thoughtLevels
            state.mode = refreshed.mode
        }
    }

    private suspend fun subscribe(sessionId: String) {
        if (!subscribed.add(sessionId)) return
        try {
            rpc.request(
                METHOD_SESSION_SUBSCRIBE,
                JSONObject()
                    .put("sessionId", sessionId)
                    .put("deliveryKind", "desktop-continuous")
                    .put("includeSnapshot", false),
            )
        } catch (error: Throwable) {
            subscribed.remove(sessionId)
            throw error
        }
    }

    private suspend fun replayHistory(sessionId: String) {
        val response = rpc.request(METHOD_SESSION_MESSAGES, JSONObject().put("sessionId", sessionId))
        response.optJSONArray("messages").zcodeObjects().forEachIndexed { index, message ->
            val info = message.optJSONObject("info") ?: JSONObject()
            val visibility = info.optJSONObject("semantics")?.nullableString("uiVisibility")
            if (visibility != null && visibility != "visible") return@forEachIndexed
            val role = when (info.optString("role")) {
                "user" -> AgentMessageRole.User
                "assistant" -> AgentMessageRole.Assistant
                else -> return@forEachIndexed
            }
            val messageId = info.nullableString("id") ?: "zcode-history-$index"
            message.optJSONArray("parts").zcodeObjects().forEach { part ->
                if (part.optString("type") != "text") return@forEach
                part.nullableString("text")?.let { text ->
                    endpoint.eventSink.onEvent(
                        sessionId,
                        AgentSessionEvent.MessageChunk(role, AgentContent.Text(text), messageId),
                    )
                }
            }
        }
    }

    private fun snapshot(state: ZCodeSessionState) = AgentSessionSnapshot(
        id = state.id,
        configuration = configuration(state),
    )

    private fun announceReady(state: ZCodeSessionState) {
        endpoint.eventSink.onEvent(state.id, AgentSessionEvent.ConfigurationUpdated(configuration(state)))
        endpoint.eventSink.onEvent(state.id, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
    }

    private fun configuration(state: ZCodeSessionState): List<AgentConfigOption> = buildList {
        val currentModel = state.model
        if (currentModel != null && state.models.isNotEmpty()) {
            add(
                AgentConfigOption.Select(
                    id = MODEL_CONFIG_ID,
                    name = "模型",
                    description = "来自 ZCode 当前会话的实时模型目录",
                    category = AgentConfigCategory.Model,
                    currentValue = currentModel.selectionId,
                    choices = state.models.distinctBy(ZCodeModel::selectionId).map { model ->
                        AgentConfigChoice(
                            value = model.selectionId,
                            name = model.displayName,
                            groupId = model.providerId,
                            groupName = model.providerId,
                            modelSource = AgentModelSource.UserConfigured,
                        )
                    },
                )
            )
        }
        val reasoningChoices = state.thoughtLevels.distinct().mapNotNull { native ->
            zcodeReasoningSemantics(native)?.let { semantics -> native to semantics }
        }
        if (reasoningChoices.size > 1 && state.thoughtLevel in state.thoughtLevels) {
            add(
                AgentConfigOption.Select(
                    id = THOUGHT_CONFIG_ID,
                    name = "推理强度",
                    description = "只显示当前模型由 ZCode 声明支持的档位",
                    category = AgentConfigCategory.ThoughtLevel,
                    currentValue = state.thoughtLevel.orEmpty(),
                    choices = reasoningChoices.map { (native, semantics) ->
                        AgentConfigChoice(
                            value = native,
                            name = semantics.displayName,
                            description = semantics.description,
                            reasoning = semantics,
                        )
                    },
                )
            )
        }
        if (state.mode in ZCODE_MODES) {
            add(
                AgentConfigOption.Select(
                    id = AGENT_SESSION_PERMISSION_CONFIG_ID,
                    name = "权限",
                    description = "ZCode 官方执行模式",
                    category = AgentConfigCategory.Permission,
                    currentValue = state.mode,
                    choices = ZCODE_MODES.values.map { it.choice },
                )
            )
        }
    }

    private suspend fun setNativeMode(state: ZCodeSessionState, modeId: String) {
        if (modeId !in ZCODE_MODES) error("ZCode 未提供该执行模式")
        rpc.request(
            METHOD_SESSION_SET_MODE,
            JSONObject().put("sessionId", state.id).put("mode", modeId),
        )
    }

    private suspend fun <T> operation(label: String, block: suspend () -> T): AgentOperationResult<T> = try {
        AgentOperationResult.Success(block())
    } catch (error: Throwable) {
        AgentFailures.protocol("$label 失败: ${error.message}", error)
    }

    private companion object {
        const val SESSION_LIST_LIMIT = 500
        const val MODEL_CONFIG_ID = "zcode.app_server.model"
        const val THOUGHT_CONFIG_ID = "zcode.app_server.thought_level"

        const val METHOD_SESSION_CREATE = "session/create"
        const val METHOD_SESSION_RESUME = "session/resume"
        const val METHOD_SESSION_LIST = "session/list"
        const val METHOD_SESSION_MESSAGES = "session/messages"
        const val METHOD_SESSION_SUBSCRIBE = "session/subscribe"
        const val METHOD_SESSION_SEND = "session/send"
        const val METHOD_SESSION_STOP = "session/stop"
        const val METHOD_SESSION_CLOSE = "session/close"
        const val METHOD_SESSION_SET_MODEL = "session/setModel"
        const val METHOD_SESSION_SET_THOUGHT = "session/setThoughtLevel"
        const val METHOD_SESSION_SET_MODE = "session/setMode"
        const val METHOD_SESSION_EVENT = "session/event"
        const val METHOD_RUNTIME_PREFERENCES = "session/requestRuntimePreferences"
        const val METHOD_PERMISSION_REQUEST = "interaction/requestPermission"

        val ZCODE_MODES = linkedMapOf(
            "build" to ZCodeMode(
                AgentConfigChoice(
                    "build",
                    "每次确认",
                    "文件修改和命令执行前请求确认",
                    permission = AgentPermissionLevel.Approval,
                )
            ),
            "edit" to ZCodeMode(
                AgentConfigChoice(
                    "edit",
                    "自动编辑",
                    "自动写入文件，命令执行仍请求确认",
                    permission = AgentPermissionLevel.Lenient,
                )
            ),
            "plan" to ZCodeMode(
                AgentConfigChoice(
                    "plan",
                    "先做计划",
                    "先完成方案确认，再进入实施",
                    permission = AgentPermissionLevel.Approval,
                )
            ),
            "yolo" to ZCodeMode(
                AgentConfigChoice(
                    "yolo",
                    "完全访问",
                    "减少普通确认并连续执行",
                    permission = AgentPermissionLevel.Full,
                )
            ),
        )
    }
}

private data class ZCodeMode(val choice: AgentConfigChoice)

private fun JSONObject.toSessionState(fallbackCwd: String): ZCodeSessionState? {
    val session = optJSONObject("session") ?: this
    val sessionId = session.nullableString("sessionId") ?: nullableString("sessionId") ?: return null
    val cwd = session.optJSONObject("workspace")?.nullableString("workspacePath") ?: fallbackCwd
    val settings = optJSONObject("settings") ?: JSONObject()
    val modelSettings = settings.optJSONObject("model") ?: JSONObject()
    val currentModel = modelSettings.optJSONObject("current")?.toZCodeModel()
    val availableModels = modelSettings.optJSONArray("available").zcodeObjects()
        .mapNotNull(JSONObject::toZCodeModel)
        .toMutableList()
        .apply { currentModel?.takeIf { current -> none { it.selectionId == current.selectionId } }?.let(::add) }
    val thoughtSettings = settings.optJSONObject("thoughtLevel") ?: JSONObject()
    val thoughtLevel = thoughtSettings.nullableString("current")
        ?: thoughtSettings.optJSONObject("current")?.nullableString("value")
    val modeSettings = settings.optJSONObject("mode") ?: JSONObject()
    val mode = modeSettings.nullableString("current")
        ?: settings.optJSONObject("permission")?.nullableString("mode")
        ?: "build"
    return ZCodeSessionState(
        id = sessionId,
        cwd = cwd,
        model = currentModel,
        models = availableModels,
        thoughtLevel = thoughtLevel,
        thoughtLevels = thoughtSettings.optJSONArray("available").zcodeStrings(),
        mode = mode,
    )
}
