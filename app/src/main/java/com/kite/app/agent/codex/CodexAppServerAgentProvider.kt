package com.kite.app.agent.codex

import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AGENT_SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentPlanEntry
import com.kite.app.agent.contract.AgentPromptCapabilities
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningSemantics
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class CodexAppServerProviderDescriptor(
    val id: String,
    val name: String,
    val title: String? = null,
    val version: String? = null,
)

fun interface CodexAppServerProcessLauncher {
    suspend fun launch(): AgentProcessChannel
}

/**
 * Codex 官方 App Server 到 Kite Agent SDK 的专用适配器。
 *
 * 外部 JSON-RPC、模型目录和权限参数只存在于本文件；显示层只消费 Kite 的统一合同。
 */
class CodexAppServerAgentProvider(
    private val descriptor: CodexAppServerProviderDescriptor,
    private val launcher: CodexAppServerProcessLauncher,
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
            return AgentOperationResult.Failure("无法启动 ${descriptor.name}: ${error.message}", error)
        }
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineName("CodexAppServer-${descriptor.id}")
        )
        val rpc = CodexAppServerRpc(process, scope, diagnosticSink)
        return try {
            rpc.start()
            withTimeout(initializeTimeoutMs) {
                rpc.request(
                    "initialize",
                    JSONObject().put(
                        "clientInfo",
                        JSONObject()
                            .put("name", request.client.name)
                            .put("title", request.client.title ?: "Kite")
                            .put("version", request.client.version),
                    ).put(
                        "capabilities",
                        JSONObject().put("experimentalApi", true),
                    ),
                )
            }
            rpc.notify("initialized", JSONObject())
            val models = loadModels(rpc)
            val connection = CodexAppServerConnection(
                descriptor = descriptor,
                process = process,
                scope = scope,
                rpc = rpc,
                endpoint = client,
                models = models,
            )
            rpc.notificationHandler = connection::onNotification
            rpc.serverRequestHandler = connection::onServerRequest
            AgentOperationResult.Success(connection)
        } catch (error: Throwable) {
            rpc.close(error)
            runCatching { process.stop() }
            scope.cancel("Codex App Server initialize failed", error)
            AgentOperationResult.Failure("${descriptor.name} App Server 初始化失败: ${error.message}", error)
        }
    }

    private suspend fun loadModels(rpc: CodexAppServerRpc): List<CodexModel> {
        val output = mutableListOf<CodexModel>()
        var cursor: String? = null
        do {
            val params = JSONObject().put("limit", MODEL_PAGE_SIZE).put("includeHidden", false)
            cursor?.let { params.put("cursor", it) }
            val result = rpc.request("model/list", params)
            result.optJSONArray("data").objects().forEach { model ->
                val id = model.optString("id").trim()
                if (id.isBlank() || model.optBoolean("hidden")) return@forEach
                val efforts = model.optJSONArray("supportedReasoningEfforts").objects().mapNotNull { effort ->
                    val value = effort.optString("reasoningEffort").trim()
                    val semantics = codexReasoningSemantics(value) ?: return@mapNotNull null
                    CodexEffort(value, effort.optString("description").trim().takeIf(String::isNotBlank), semantics)
                }.distinctBy(CodexEffort::value)
                output += CodexModel(
                    id = id,
                    displayName = model.optString("displayName", id).trim().ifBlank { id },
                    description = model.optString("description").trim().takeIf(String::isNotBlank),
                    defaultEffort = model.optString("defaultReasoningEffort").trim().takeIf(String::isNotBlank),
                    efforts = efforts,
                    isDefault = model.optBoolean("isDefault"),
                )
            }
            cursor = result.nullableString("nextCursor")
        } while (cursor != null)
        return output.distinctBy(CodexModel::id)
    }

    companion object {
        const val DEFAULT_INITIALIZE_TIMEOUT_MS = 20_000L
        private const val MODEL_PAGE_SIZE = 100
    }
}

private data class CodexEffort(
    val value: String,
    val description: String?,
    val semantics: AgentReasoningSemantics,
)

private data class CodexModel(
    val id: String,
    val displayName: String,
    val description: String?,
    val defaultEffort: String?,
    val efforts: List<CodexEffort>,
    val isDefault: Boolean,
)

private data class CodexSession(
    val id: String,
    val cwd: String,
    var modelProvider: String,
    var modelId: String,
    var effort: String?,
    var permission: CodexPermission,
)

private class CodexAppServerConnection(
    private val descriptor: CodexAppServerProviderDescriptor,
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val rpc: CodexAppServerRpc,
    private val endpoint: AgentClientEndpoint,
    models: List<CodexModel>,
) : KiteAgentConnection {
    override val provider: AgentProviderInfo = AgentProviderInfo(
        id = descriptor.id,
        name = descriptor.name,
        version = descriptor.version,
        title = descriptor.title,
    )
    override val capabilities: AgentCapabilities = AgentCapabilities(
        prompt = AgentPromptCapabilities(text = true, resourceLinks = false, images = false),
        sessions = AgentSessionCapabilities(
            load = true,
            list = true,
            resume = true,
            fork = true,
            close = true,
            delete = true,
            rename = true,
        ),
    )
    private val models = models.associateBy(CodexModel::id)
    private val sessions = ConcurrentHashMap<String, CodexSession>()
    private val activeTurns = ConcurrentHashMap<String, CompletableDeferred<AgentTurnResult>>()
    private val activeTurnIds = ConcurrentHashMap<String, String>()
    private val lastUsage = ConcurrentHashMap<String, AgentTurnUsage>()
    private val streamedMessages = ConcurrentHashMap.newKeySet<String>()
    private val disconnecting = AtomicBoolean(false)

    init {
        scope.launch(Dispatchers.IO + CoroutineName("${descriptor.id}-process-exit")) {
            val exitCode = process.awaitExit()
            if (!disconnecting.get()) {
                sessions.keys.forEach { sessionId ->
                    endpoint.eventSink.onEvent(
                        sessionId,
                        AgentSessionEvent.LifecycleChanged(
                            AgentSessionPhase.Failed,
                            "${descriptor.name} App Server 已退出，exitCode=$exitCode",
                        ),
                    )
                }
                activeTurns.values.forEach { pending ->
                    pending.completeExceptionally(IllegalStateException("Codex App Server 已退出"))
                }
            }
        }
    }

    override suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        operation("创建会话") {
            val response = rpc.request(
                "thread/start",
                JSONObject()
                    .put("cwd", request.cwd)
                    .put("serviceName", "kite"),
            )
            registerSession(response, request.cwd)
        }

    override suspend fun loadSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        resume(request)

    override suspend fun resumeSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        resume(request)

    private suspend fun resume(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        operation("恢复会话") {
            val response = rpc.request(
                "thread/resume",
                JSONObject()
                    .put("threadId", request.sessionId)
                    .put("cwd", request.cwd),
            )
            registerSession(response, request.cwd)
        }

    override suspend fun forkSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        operation("分叉会话") {
            val response = rpc.request(
                "thread/fork",
                JSONObject()
                    .put("threadId", request.sessionId)
                    .put("cwd", request.cwd),
            )
            registerSession(response, request.cwd)
        }

    override suspend fun listSessions(request: AgentSessionListRequest): AgentOperationResult<AgentSessionPage> =
        operation("列出会话") {
            val params = JSONObject().put("limit", THREAD_PAGE_SIZE)
            request.cursor?.let { params.put("cursor", it) }
            request.cwd?.let { params.put("cwd", it) }
            val response = rpc.request("thread/list", params)
            AgentSessionPage(
                sessions = response.optJSONArray("data").objects().mapNotNull { thread ->
                    val id = thread.optString("id").trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                    AgentSessionSummary(
                        id = id,
                        cwd = thread.optString("cwd"),
                        title = thread.nullableString("name") ?: thread.nullableString("preview"),
                        updatedAt = thread.opt("updatedAt")?.takeUnless { it == JSONObject.NULL }?.toString(),
                    )
                },
                nextCursor = response.nullableString("nextCursor"),
            )
        }

    override suspend fun closeSession(sessionId: String): AgentOperationResult<Unit> = operation("关闭会话") {
        rpc.request("thread/unsubscribe", JSONObject().put("threadId", sessionId))
        sessions.remove(sessionId)
        activeTurns.remove(sessionId)?.cancel()
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed))
    }

    override suspend fun deleteSession(sessionId: String): AgentOperationResult<Unit> = operation("删除会话") {
        rpc.request("thread/delete", JSONObject().put("threadId", sessionId))
        sessions.remove(sessionId)
        activeTurns.remove(sessionId)?.cancel()
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed))
    }

    override suspend fun renameSession(request: AgentSessionRenameRequest): AgentOperationResult<Unit> =
        operation("重命名会话") {
            rpc.request(
                "thread/name/set",
                JSONObject().put("threadId", request.sessionId).put("name", request.title),
            )
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.SessionInfoChanged(title = request.title),
            )
        }

    override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> {
        val session = sessions[request.sessionId]
            ?: return AgentOperationResult.Failure("会话不存在: ${request.sessionId}")
        val input = request.content.toCodexInput()
            ?: return AgentOperationResult.Unsupported("codex-app-server-non-text-input")
        endpoint.eventSink.onEvent(
            request.sessionId,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting),
        )
        val completion = CompletableDeferred<AgentTurnResult>()
        if (activeTurns.putIfAbsent(request.sessionId, completion) != null) {
            return AgentOperationResult.Failure("Codex 当前已有一轮回复正在生成")
        }
        return try {
            val started = rpc.request(
                "turn/start",
                JSONObject()
                    .put("threadId", session.id)
                    .put("input", input),
            )
            started.optJSONObject("turn")?.optString("id")
                ?.takeIf(String::isNotBlank)
                ?.takeIf { !completion.isCompleted }
                ?.let { activeTurnIds[request.sessionId] = it }
            AgentOperationResult.Success(completion.await())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, error.message),
            )
            AgentOperationResult.Failure("Codex 发送消息失败: ${error.message}", error)
        } finally {
            activeTurns.remove(request.sessionId, completion)
        }
    }

    override suspend fun setConfiguration(
        sessionId: String,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<List<AgentConfigOption>> {
        val session = sessions[sessionId] ?: return AgentOperationResult.Failure("会话不存在: $sessionId")
        val selected = (value as? AgentConfigValue.Select)?.value
            ?: return AgentOperationResult.Failure("Codex 配置必须是单选值")
        if (configId !in setOf(MODEL_CONFIG_ID, EFFORT_CONFIG_ID, PERMISSION_CONFIG_ID)) {
            return AgentOperationResult.Unsupported("codex-app-server-config:$configId")
        }
        return operation("更新 Codex 会话配置") {
            val params = JSONObject().put("threadId", sessionId)
            when (configId) {
                MODEL_CONFIG_ID -> {
                    val model = availableModels(session).firstOrNull { it.id == selected }
                        ?: error("Codex 未提供该模型")
                    val nextEffort = session.effort?.takeIf { effort -> model.efforts.any { it.value == effort } }
                        ?: model.defaultEffort?.takeIf { effort -> model.efforts.any { it.value == effort } }
                        ?: model.efforts.firstOrNull()?.value
                    params.put("model", model.id)
                    nextEffort?.let { params.put("effort", it) }
                    rpc.request("thread/settings/update", params)
                    session.modelId = model.id
                    session.effort = nextEffort
                }
                EFFORT_CONFIG_ID -> {
                    val model = availableModels(session).firstOrNull { it.id == session.modelId }
                        ?: error("当前模型不在 Codex 模型目录中")
                    if (model.efforts.none { it.value == selected }) error("当前模型不支持该推理强度")
                    params.put("effort", selected)
                    rpc.request("thread/settings/update", params)
                    session.effort = selected
                }
                PERMISSION_CONFIG_ID -> {
                    val permission = CodexPermission.entries.firstOrNull { it.id == selected }
                        ?: error("Codex 未提供该权限选项")
                    permission.applyTo(params)
                    rpc.request("thread/settings/update", params)
                    session.permission = permission
                }
                else -> error("已由 Codex 配置 ID 校验限制分支")
            }
            configuration(session).also { options ->
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ConfigurationUpdated(options))
            }
        }
    }

    override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> {
        if (sessions[sessionId] == null) return AgentOperationResult.Failure("会话不存在: $sessionId")
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelling))
        val turnId = activeTurnIds[sessionId]
            ?: return AgentOperationResult.Failure("Codex 当前没有正在生成的回复")
        return operation("取消生成") {
            rpc.request(
                "turn/interrupt",
                JSONObject().put("threadId", sessionId).put("turnId", turnId),
            )
        }
    }

    override suspend fun disconnect() {
        if (!disconnecting.compareAndSet(false, true)) return
        activeTurns.values.forEach { it.cancel() }
        activeTurns.clear()
        activeTurnIds.clear()
        sessions.keys.forEach { sessionId ->
            endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed))
        }
        sessions.clear()
        rpc.close()
        process.stop()
        scope.cancel("Codex App Server disconnected")
    }

    internal suspend fun onNotification(method: String, params: JSONObject) {
        val sessionId = params.optString("threadId").takeIf(String::isNotBlank)
        when (method) {
            "turn/started" -> sessionId?.let { id ->
                params.optJSONObject("turn")?.optString("id")
                    ?.takeIf(String::isNotBlank)
                    ?.let { activeTurnIds[id] = it }
            }
            "item/agentMessage/delta" -> sessionId?.let { id ->
                val itemId = params.optString("itemId").takeIf(String::isNotBlank)
                itemId?.let(streamedMessages::add)
                endpoint.eventSink.onEvent(
                    id,
                    AgentSessionEvent.MessageChunk(
                        role = AgentMessageRole.Assistant,
                        content = AgentContent.Text(params.optString("delta")),
                        messageId = itemId,
                    ),
                )
            }
            "item/reasoning/summaryTextDelta", "item/reasoning/textDelta" -> sessionId?.let { id ->
                endpoint.eventSink.onEvent(
                    id,
                    AgentSessionEvent.MessageChunk(
                        role = AgentMessageRole.Thought,
                        content = AgentContent.Text(params.optString("delta")),
                        messageId = params.optString("itemId").takeIf(String::isNotBlank),
                    ),
                )
            }
            "turn/plan/updated" -> sessionId?.let { id ->
                endpoint.eventSink.onEvent(
                    id,
                    AgentSessionEvent.PlanUpdated(
                        params.optJSONArray("plan").objects().map { entry ->
                            AgentPlanEntry(
                                content = entry.optString("step"),
                                priority = "normal",
                                status = entry.optString("status"),
                            )
                        }
                    ),
                )
            }
            "item/started" -> sessionId?.let { id ->
                params.optJSONObject("item")?.toToolCall()?.let { call ->
                    endpoint.eventSink.onEvent(id, AgentSessionEvent.ToolCallStarted(call))
                }
            }
            "item/completed" -> sessionId?.let { id ->
                val item = params.optJSONObject("item") ?: return@let
                if (item.optString("type") == "agentMessage") {
                    val itemId = item.optString("id")
                    if (itemId !in streamedMessages) {
                        endpoint.eventSink.onEvent(
                            id,
                            AgentSessionEvent.MessageChunk(
                                role = AgentMessageRole.Assistant,
                                content = AgentContent.Text(item.optString("text")),
                                messageId = itemId.takeIf(String::isNotBlank),
                            ),
                        )
                    }
                    streamedMessages.remove(itemId)
                } else {
                    item.toToolPatch()?.let { patch ->
                        endpoint.eventSink.onEvent(id, AgentSessionEvent.ToolCallUpdated(patch))
                    }
                }
            }
            "thread/tokenUsage/updated" -> sessionId?.let { id ->
                val usage = params.optJSONObject("tokenUsage")?.optJSONObject("last")?.toTurnUsage()
                    ?: return@let
                lastUsage[id] = usage
                val contextSize = params.optJSONObject("tokenUsage")
                    ?.optLong("modelContextWindow", 0L)
                    ?.takeIf { it > 0L }
                contextSize?.let { size ->
                    endpoint.eventSink.onEvent(
                        id,
                        AgentSessionEvent.UsageChanged(usage.totalTokens, size),
                    )
                }
            }
            "turn/completed" -> sessionId?.let { id -> completeTurn(id, params.optJSONObject("turn")) }
            "error" -> sessionId?.let { id ->
                val message = params.optJSONObject("error")?.optString("message")
                    ?.takeIf(String::isNotBlank) ?: "Codex 运行失败"
                endpoint.eventSink.onEvent(id, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, message))
            }
            "thread/name/updated" -> sessionId?.let { id ->
                endpoint.eventSink.onEvent(
                    id,
                    AgentSessionEvent.SessionInfoChanged(title = params.nullableString("name")),
                )
            }
        }
    }

    internal suspend fun onServerRequest(method: String, params: JSONObject): JSONObject = when (method) {
        "item/commandExecution/requestApproval", "item/fileChange/requestApproval" -> {
            val sessionId = params.optString("threadId")
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.WaitingPermission),
            )
            val decisions = params.optJSONArray("availableDecisions")
                ?.strings()
                ?.filter { it in APPROVAL_DECISIONS }
                .orEmpty()
                .ifEmpty { APPROVAL_DECISIONS }
            val outcome = endpoint.permissionHandler.request(
                AgentPermissionRequest(
                    sessionId = sessionId,
                    toolCall = AgentToolCallPatch(
                        id = params.optString("itemId"),
                        title = params.nullableString("reason") ?: params.nullableString("command")
                            ?: if (method.contains("fileChange")) "修改文件" else "执行命令",
                        rawInput = params.nullableString("command"),
                    ),
                    options = decisions.map(::approvalOption),
                )
            )
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting),
            )
            val decision = (outcome as? AgentPermissionOutcome.Selected)?.optionId
                ?.takeIf { it in decisions } ?: "cancel"
            JSONObject().put("decision", decision)
        }
        "item/permissions/requestApproval" -> {
            val sessionId = params.optString("threadId")
            val requested = params.optJSONObject("permissions") ?: JSONObject()
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.WaitingPermission),
            )
            val outcome = endpoint.permissionHandler.request(
                AgentPermissionRequest(
                    sessionId = sessionId,
                    toolCall = AgentToolCallPatch(
                        id = params.optString("itemId"),
                        title = params.nullableString("reason") ?: "请求额外文件或网络权限",
                        rawInput = requested.toString(),
                    ),
                    options = listOf(
                        AgentPermissionOption("allowOnce", "允许本轮", AgentPermissionKind.AllowOnce),
                        AgentPermissionOption("allowSession", "本次会话允许", AgentPermissionKind.AllowAlways),
                        AgentPermissionOption("deny", "拒绝", AgentPermissionKind.RejectOnce),
                    ),
                ),
            )
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting),
            )
            val selected = (outcome as? AgentPermissionOutcome.Selected)?.optionId
            val approved = selected == "allowOnce" || selected == "allowSession"
            JSONObject()
                .put("permissions", if (approved) JSONObject(requested.toString()) else JSONObject())
                .put("scope", if (selected == "allowSession") "session" else "turn")
        }
        else -> throw UnsupportedOperationException("Codex App Server 请求暂不支持: $method")
    }

    private fun registerSession(response: JSONObject, requestedCwd: String): AgentSessionSnapshot {
        val thread = response.getJSONObject("thread")
        val session = CodexSession(
            id = thread.getString("id"),
            cwd = response.optString("cwd", requestedCwd).ifBlank { requestedCwd },
            modelProvider = response.optString("modelProvider", OFFICIAL_PROVIDER_ID),
            modelId = response.optString("model").ifBlank {
                models.values.firstOrNull(CodexModel::isDefault)?.id
                    ?: models.values.firstOrNull()?.id.orEmpty()
            },
            effort = response.nullableString("reasoningEffort"),
            permission = permissionFromResponse(response),
        )
        sessions[session.id] = session
        endpoint.eventSink.onEvent(
            session.id,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready, "准备就绪"),
        )
        return snapshot(session)
    }

    private fun snapshot(session: CodexSession): AgentSessionSnapshot = AgentSessionSnapshot(
        id = session.id,
        configuration = configuration(session),
    )

    private fun configuration(session: CodexSession): List<AgentConfigOption> = buildList {
        val available = availableModels(session)
        if (available.isNotEmpty()) {
            add(AgentConfigOption.Select(
                id = MODEL_CONFIG_ID,
                name = "模型",
                description = "来自 Codex App Server 的真实模型目录",
                category = AgentConfigCategory.Model,
                currentValue = session.modelId,
                choices = available.map { model ->
                    AgentConfigChoice(
                        value = model.id,
                        name = model.displayName,
                        description = model.description,
                        groupId = session.modelGroupId(),
                        groupName = session.modelGroupName(),
                        modelSource = session.modelSource(),
                    )
                },
            ))
        }
        available.firstOrNull { it.id == session.modelId }
            ?.efforts
            ?.takeIf { it.size > 1 }
            ?.let { efforts ->
                val current = session.effort?.takeIf { selected -> efforts.any { it.value == selected } }
                    ?: efforts.first().value
                add(AgentConfigOption.Select(
                    id = EFFORT_CONFIG_ID,
                    name = "推理强度",
                    description = "只显示当前模型由 Codex 声明支持的强度",
                    category = AgentConfigCategory.ThoughtLevel,
                    currentValue = current,
                    choices = efforts.map { effort ->
                        AgentConfigChoice(
                            value = effort.value,
                            name = effort.semantics.displayName,
                            description = effort.description ?: effort.semantics.description,
                            reasoning = effort.semantics,
                        )
                    },
                ))
            }
        add(codexPermissionOption(session.permission))
    }

    private fun availableModels(session: CodexSession): List<CodexModel> {
        if (session.modelSource() == AgentModelSource.Official) return models.values.toList()
        return listOf(models[session.modelId] ?: CodexModel(
            id = session.modelId,
            displayName = session.modelId,
            description = null,
            defaultEffort = session.effort,
            efforts = session.effort?.let { value ->
                codexReasoningSemantics(value)?.let { listOf(CodexEffort(value, null, it)) }
            }.orEmpty(),
            isDefault = true,
        ))
    }

    private fun completeTurn(sessionId: String, turn: JSONObject?) {
        val status = turn?.optString("status").orEmpty()
        val result = when (status) {
            "completed" -> AgentTurnResult(AgentStopReason.EndTurn, usage = lastUsage.remove(sessionId))
            "interrupted" -> AgentTurnResult(AgentStopReason.Cancelled, usage = lastUsage.remove(sessionId))
            "failed" -> {
                val message = turn?.optJSONObject("error")?.optString("message")
                    ?.takeIf(String::isNotBlank) ?: "Codex 本轮执行失败"
                activeTurns.remove(sessionId)?.completeExceptionally(IllegalStateException(message))
                endpoint.eventSink.onEvent(
                    sessionId,
                    AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, message),
                )
                return
            }
            else -> AgentTurnResult(AgentStopReason(status.ifBlank { "unknown" }), usage = lastUsage.remove(sessionId))
        }
        activeTurns.remove(sessionId)?.complete(result)
        activeTurnIds.remove(sessionId)
        endpoint.eventSink.onEvent(
            sessionId,
            AgentSessionEvent.LifecycleChanged(
                if (status == "interrupted") AgentSessionPhase.Cancelled else AgentSessionPhase.Ready,
            ),
        )
    }

    private suspend fun <T> operation(label: String, block: suspend () -> T): AgentOperationResult<T> = try {
        AgentOperationResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        AgentOperationResult.Failure("Codex $label 失败: ${error.message}", error)
    }

    private fun CodexSession.modelSource(): AgentModelSource =
        if (modelProvider.isBlank() || modelProvider == OFFICIAL_PROVIDER_ID) {
            AgentModelSource.Official
        } else {
            AgentModelSource.UserConfigured
        }

    private fun CodexSession.modelGroupId(): String =
        if (modelSource() == AgentModelSource.Official) OFFICIAL_PROVIDER_ID else modelProvider

    private fun CodexSession.modelGroupName(): String =
        if (modelSource() == AgentModelSource.Official) "OpenAI" else modelProvider

    private fun CodexPermission.applyTo(params: JSONObject) {
        params.put("permissions", JSONObject.NULL)
        when (this) {
            CodexPermission.Ask -> params
                .put("approvalPolicy", "on-request")
                .put("approvalsReviewer", "user")
                .put("sandboxPolicy", JSONObject().put("type", "workspaceWrite"))
            CodexPermission.AutoReview -> params
                .put("approvalPolicy", "on-request")
                .put("approvalsReviewer", "auto_review")
                .put("sandboxPolicy", JSONObject().put("type", "workspaceWrite"))
            CodexPermission.FullAccess -> params
                .put("approvalPolicy", "never")
                .put("approvalsReviewer", "user")
                .put("sandboxPolicy", JSONObject().put("type", "dangerFullAccess"))
            CodexPermission.Custom -> params
                .put("approvalPolicy", JSONObject.NULL)
                .put("approvalsReviewer", JSONObject.NULL)
                .put("sandboxPolicy", JSONObject.NULL)
        }
    }

    private fun permissionFromResponse(response: JSONObject): CodexPermission {
        val approval = response.opt("approvalPolicy")?.takeUnless { it == JSONObject.NULL }?.toString()
        val reviewer = response.optString("approvalsReviewer")
        val sandboxType = response.optJSONObject("sandbox")?.optString("type")
            ?.ifBlank { null }
            ?: response.optJSONObject("sandboxPolicy")?.optString("type")?.ifBlank { null }
        return when {
            approval == "on-request" && reviewer in setOf("auto_review", "guardian_subagent") &&
                sandboxType == "workspaceWrite" -> CodexPermission.AutoReview
            approval == "never" && sandboxType == "dangerFullAccess" -> CodexPermission.FullAccess
            approval == "on-request" && reviewer == "user" && sandboxType == "workspaceWrite" ->
                CodexPermission.Ask
            else -> CodexPermission.Custom
        }
    }

    companion object {
        private const val MODEL_CONFIG_ID = "codex.app_server.model"
        private const val EFFORT_CONFIG_ID = "codex.app_server.effort"
        private const val PERMISSION_CONFIG_ID = AGENT_SESSION_PERMISSION_CONFIG_ID
        private const val OFFICIAL_PROVIDER_ID = "openai"
        private const val THREAD_PAGE_SIZE = 50
        private val APPROVAL_DECISIONS = listOf("accept", "acceptForSession", "decline", "cancel")
    }
}

private class CodexAppServerRpc(
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val diagnosticSink: (String) -> Unit,
) {
    private val sequence = AtomicLong(0L)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val closed = AtomicBoolean(false)
    var notificationHandler: suspend (String, JSONObject) -> Unit = { _, _ -> }
    var serverRequestHandler: suspend (String, JSONObject) -> JSONObject = { method, _ ->
        throw UnsupportedOperationException("Codex App Server 请求暂不支持: $method")
    }

    fun start() {
        scope.launch(Dispatchers.IO + CoroutineName("codex-app-server-stdout")) {
            process.stdoutLines.collect { line -> handleLine(line) }
        }
        scope.launch(Dispatchers.IO + CoroutineName("codex-app-server-stderr")) {
            process.stderrLines.collect(diagnosticSink)
        }
    }

    suspend fun request(method: String, params: JSONObject): JSONObject {
        check(!closed.get()) { "Codex App Server 连接已关闭" }
        val id = sequence.incrementAndGet()
        val response = CompletableDeferred<JSONObject>()
        pending[id] = response
        try {
            process.writeLine(
                JSONObject().put("method", method).put("id", id).put("params", params).toString()
            )
            return response.await()
        } finally {
            pending.remove(id, response)
        }
    }

    suspend fun notify(method: String, params: JSONObject) {
        check(!closed.get()) { "Codex App Server 连接已关闭" }
        process.writeLine(JSONObject().put("method", method).put("params", params).toString())
    }

    fun close(cause: Throwable? = null) {
        if (!closed.compareAndSet(false, true)) return
        val error = cause ?: CancellationException("Codex App Server 连接已关闭")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private fun handleLine(line: String) {
        val message = runCatching { JSONObject(line) }.getOrElse {
            diagnosticSink("Codex App Server 输出了无效 JSON")
            return
        }
        val rawId = message.opt("id")?.takeUnless { it == JSONObject.NULL }
        val responseId = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }
        if (responseId != null && (message.has("result") || message.has("error"))) {
            val deferred = pending.remove(responseId) ?: return
            val error = message.optJSONObject("error")
            if (error != null) {
                deferred.completeExceptionally(
                    IllegalStateException(error.optString("message").ifBlank { "Codex App Server 请求失败" })
                )
            } else {
                deferred.complete(message.optJSONObject("result") ?: JSONObject())
            }
            return
        }
        val method = message.optString("method").takeIf(String::isNotBlank) ?: return
        val params = message.optJSONObject("params") ?: JSONObject()
        if (rawId != null) {
            scope.launch {
                runCatching { serverRequestHandler(method, params) }
                    .onSuccess { result ->
                        process.writeLine(JSONObject().put("id", rawId).put("result", result).toString())
                    }
                    .onFailure { error ->
                        process.writeLine(
                            JSONObject().put("id", rawId).put(
                                "error",
                                JSONObject().put("code", -32601).put("message", error.message),
                            ).toString()
                        )
                    }
            }
        } else {
            scope.launch { notificationHandler(method, params) }
        }
    }
}

private fun codexReasoningSemantics(value: String): AgentReasoningSemantics? = when (value.lowercase()) {
    "none", "off" -> AgentReasoningLevel.Off
    "minimal" -> AgentReasoningLevel.Minimal
    "low" -> AgentReasoningLevel.Low
    "medium" -> AgentReasoningLevel.Medium
    "high" -> AgentReasoningLevel.High
    "xhigh", "x-high", "x_high", "extra-high" -> AgentReasoningLevel.ExtraHigh
    "max" -> AgentReasoningLevel.Maximum
    "ultra" -> AgentReasoningLevel.Ultra
    else -> null
}

private fun List<AgentContent>.toCodexInput(): JSONArray? {
    if (any { it !is AgentContent.Text }) return null
    return JSONArray().apply {
        this@toCodexInput.filterIsInstance<AgentContent.Text>().forEach { content ->
            put(JSONObject().put("type", "text").put("text", content.text))
        }
    }
}

private fun JSONObject.toToolCall(): AgentToolCall? {
    val type = optString("type")
    val id = optString("id").takeIf(String::isNotBlank) ?: return null
    val title = when (type) {
        "commandExecution" -> optString("command").ifBlank { "执行命令" }
        "fileChange" -> "修改文件"
        "mcpToolCall" -> listOf(optString("server"), optString("tool")).filter(String::isNotBlank).joinToString("/")
        "webSearch" -> optString("query").ifBlank { "搜索网页" }
        else -> return null
    }
    return AgentToolCall(
        id = id,
        title = title,
        kind = AgentToolKind(type),
        status = AgentToolStatus(optString("status", "inProgress")),
        rawInput = opt("arguments")?.takeUnless { it == JSONObject.NULL }?.toString(),
        rawOutput = nullableString("aggregatedOutput"),
    )
}

private fun JSONObject.toToolPatch(): AgentToolCallPatch? {
    val call = toToolCall() ?: return null
    return AgentToolCallPatch(
        id = call.id,
        title = call.title,
        kind = call.kind,
        status = call.status,
        rawInput = call.rawInput,
        rawOutput = call.rawOutput,
    )
}

private fun JSONObject.toTurnUsage(): AgentTurnUsage = AgentTurnUsage(
    inputTokens = optLong("inputTokens"),
    outputTokens = optLong("outputTokens"),
    totalTokens = optLong("totalTokens"),
    thoughtTokens = optLong("reasoningOutputTokens"),
    cachedReadTokens = optLong("cachedInputTokens"),
    cachedWriteTokens = optLong("cacheWriteInputTokens"),
)

private fun approvalOption(decision: String): AgentPermissionOption = when (decision) {
    "accept" -> AgentPermissionOption(decision, "允许一次", AgentPermissionKind.AllowOnce)
    "acceptForSession" -> AgentPermissionOption(decision, "本次会话允许", AgentPermissionKind.AllowAlways)
    "decline" -> AgentPermissionOption(decision, "拒绝一次", AgentPermissionKind.RejectOnce)
    else -> AgentPermissionOption(decision, "拒绝并停止", AgentPermissionKind.RejectAlways)
}

private fun JSONArray?.objects(): List<JSONObject> = buildList {
    val source = this@objects ?: return@buildList
    repeat(source.length()) { index -> source.optJSONObject(index)?.let(::add) }
}

private fun JSONArray.strings(): List<String> = buildList {
    repeat(length()) { index -> optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

private fun JSONObject.nullableString(key: String): String? =
    opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()?.takeIf(String::isNotBlank)
