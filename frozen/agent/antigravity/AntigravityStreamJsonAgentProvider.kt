package com.kite.app.agent.antigravity

import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentDraftConfigurationPreview
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentFailureCode
import com.kite.app.agent.contract.AgentFailures
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPromptCapabilities
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentSessionCapabilities
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionSnapshot
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class AntigravityProviderDescriptor(
    val id: String,
    val name: String,
    val title: String? = null,
    val version: String? = null,
)

fun interface AntigravityProcessLauncher {
    suspend fun launch(arguments: List<String>): AgentProcessChannel
}

/** Google Antigravity 官方 stream-json 到 Kite Agent SDK 的专用适配器。 */
class AntigravityStreamJsonAgentProvider(
    private val descriptor: AntigravityProviderDescriptor,
    private val launcher: AntigravityProcessLauncher,
    private val initializeTimeoutMs: Long = 45_000L,
    private val promptTimeoutMs: Long = 31L * 60L * 1_000L,
    private val diagnosticSink: (String) -> Unit = {},
    sessionFileResolver: AntigravitySessionFileResolver? = null,
    sessionPathMapper: AntigravitySessionPathMapper = AntigravitySessionPathMapper { it },
) : KiteAgentProvider {
    override val id: String = descriptor.id
    private val sessionCatalog = sessionFileResolver?.let { resolver ->
        AntigravitySessionCatalog(resolver, sessionPathMapper)
    }

    override suspend fun connect(
        request: AgentConnectionRequest,
        client: AgentClientEndpoint,
    ): AgentOperationResult<KiteAgentConnection> {
        val models = runCatching { probeModels() }
            .onFailure { error -> diagnosticSink("Antigravity 模型目录读取失败: ${error.message}") }
            .getOrDefault(emptyList())
        return AgentOperationResult.Success(
            AntigravityConnection(
                descriptor = descriptor,
                launcher = launcher,
                endpoint = client,
                models = models,
                sessionCatalog = sessionCatalog,
                initializeTimeoutMs = initializeTimeoutMs,
                promptTimeoutMs = promptTimeoutMs,
                diagnosticSink = diagnosticSink,
            )
        )
    }

    private suspend fun probeModels(): List<AntigravityModel> = coroutineScope {
        val process = launcher.launch(listOf("models"))
        try {
            val stdout = async(Dispatchers.IO) { process.stdoutLines.toList() }
            val stderr = async(Dispatchers.IO) { process.stderrLines.toList() }
            val exit = withTimeout(initializeTimeoutMs) { process.awaitExit() }
            val diagnostics = stderr.await().joinToString(" ").trim()
            check(exit == 0) { diagnostics.ifBlank { "agy models exitCode=$exit" } }
            stdout.await().mapNotNull(::parseModelLine).distinctBy(AntigravityModel::id)
        } finally {
            process.close()
        }
    }
}

private data class AntigravityModel(val id: String, val name: String)

private data class ActiveAntigravityStream(
    val sessionId: String,
    val stream: AntigravityStream,
)

private class AntigravityConnection(
    descriptor: AntigravityProviderDescriptor,
    private val launcher: AntigravityProcessLauncher,
    private val endpoint: AgentClientEndpoint,
    models: List<AntigravityModel>,
    private val sessionCatalog: AntigravitySessionCatalog?,
    private val initializeTimeoutMs: Long,
    private val promptTimeoutMs: Long,
    private val diagnosticSink: (String) -> Unit,
) : KiteAgentConnection {
    override val provider = AgentProviderInfo(descriptor.id, descriptor.name, descriptor.version, descriptor.title)
    override val capabilities = AgentCapabilities(
        prompt = AgentPromptCapabilities(text = true, resourceLinks = true, embeddedResources = true),
        sessions = AgentSessionCapabilities(
            load = sessionCatalog != null,
            list = sessionCatalog != null,
            resume = true,
            close = true,
        ),
    )
    private val modelsById = models.associateBy(AntigravityModel::id)
    private val operationMutex = Mutex()
    @Volatile private var active: ActiveAntigravityStream? = null
    @Volatile private var cwd: String = "/workspace"
    @Volatile private var selectedModel: String? = null
    @Volatile private var selectedEffort: String? = null
    @Volatile private var selectedMode: String = MODE_DEFAULT
    @Volatile private var configurationDirty: Boolean = false

    override suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        operationMutex.withLock {
            cwd = request.cwd
            replaceStream(conversationId = null).map { current -> snapshot(current.sessionId) }
        }

    override suspend fun loadSession(
        request: AgentExistingSessionRequest,
    ): AgentOperationResult<AgentSessionSnapshot> = operationMutex.withLock {
        val catalog = sessionCatalog ?: return@withLock AgentOperationResult.Unsupported("session/load")
        val record = catalog.find(request.cwd, request.sessionId)
            ?: return@withLock AgentOperationResult.Failure("Antigravity 会话不存在: ${request.sessionId}")
        cwd = request.cwd
        when (val opened = replaceStream(request.sessionId)) {
            is AgentOperationResult.Success -> {
                record.messages.forEach { message ->
                    endpoint.eventSink.onEvent(
                        request.sessionId,
                        AgentSessionEvent.MessageChunk(message.role, message.content, message.id),
                    )
                }
                endpoint.eventSink.onEvent(
                    request.sessionId,
                    AgentSessionEvent.SessionInfoChanged(
                        title = record.summary.title,
                        updatedAt = record.summary.updatedAt,
                    ),
                )
                AgentOperationResult.Success(snapshot(opened.value.sessionId))
            }
            is AgentOperationResult.Failure -> opened
            is AgentOperationResult.Unsupported -> opened
        }
    }

    override suspend fun listSessions(request: AgentSessionListRequest): AgentOperationResult<AgentSessionPage> {
        val catalog = sessionCatalog ?: return AgentOperationResult.Unsupported("session/list")
        val targetCwd = request.cwd?.trim()?.takeIf(String::isNotBlank) ?: cwd
        return runCatching { AgentSessionPage(catalog.list(targetCwd).map(AntigravitySessionRecord::summary)) }
            .fold(
                onSuccess = { AgentOperationResult.Success(it) },
                onFailure = { AgentOperationResult.Failure("读取 Antigravity 会话失败: ${it.message}", it) },
            )
    }

    override suspend fun resumeSession(
        request: AgentExistingSessionRequest,
    ): AgentOperationResult<AgentSessionSnapshot> = operationMutex.withLock {
        cwd = request.cwd
        replaceStream(request.sessionId).map { snapshot(it.sessionId) }
    }

    override suspend fun forkSession(request: AgentExistingSessionRequest) =
        AgentOperationResult.Unsupported("session/fork")

    override suspend fun closeSession(sessionId: String): AgentOperationResult<Unit> = operationMutex.withLock {
        val current = active ?: return@withLock AgentOperationResult.Success(Unit)
        if (current.sessionId != sessionId) return@withLock AgentOperationResult.Failure("Antigravity 会话不匹配")
        active = null
        current.stream.close()
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed))
        AgentOperationResult.Success(Unit)
    }

    override suspend fun deleteSession(sessionId: String) = AgentOperationResult.Unsupported("session/delete")

    override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> =
        operationMutex.withLock {
            val current = active
                ?.takeIf { it.sessionId == request.sessionId }
                ?: return@withLock AgentOperationResult.Failure("Antigravity 会话尚未准备好")
            val prepared = if (configurationDirty) replaceStream(request.sessionId) else {
                AgentOperationResult.Success(current)
            }
            val ready = when (prepared) {
                is AgentOperationResult.Success -> prepared.value
                is AgentOperationResult.Failure -> return@withLock prepared
                is AgentOperationResult.Unsupported -> return@withLock prepared
            }
            val prompt = request.content.toAntigravityPrompt()
                ?: return@withLock AgentOperationResult.Unsupported("prompt/content")
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting, "Antigravity 正在处理"),
            )
            when (val result = ready.stream.prompt(prompt, promptTimeoutMs)) {
                is AgentOperationResult.Success -> {
                    endpoint.eventSink.onEvent(
                        request.sessionId,
                        AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready),
                    )
                    result
                }
                is AgentOperationResult.Failure -> {
                    endpoint.eventSink.onEvent(
                        request.sessionId,
                        AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, result.message),
                    )
                    result
                }
                is AgentOperationResult.Unsupported -> result
            }
        }

    override suspend fun setConfiguration(
        sessionId: String,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<List<AgentConfigOption>> = operationMutex.withLock {
        if (active?.sessionId != sessionId) return@withLock AgentOperationResult.Failure("Antigravity 会话不匹配")
        val selected = (value as? AgentConfigValue.Select)?.value
            ?: return@withLock AgentOperationResult.Failure("Antigravity 配置需要选择一个值")
        val previousModel = selectedModel
        val previousEffort = selectedEffort
        when (configId) {
            MODEL_CONFIG_ID -> {
                if (selected != AGENT_DEFAULT_VALUE && selected !in modelsById) {
                    return@withLock AgentOperationResult.Failure("Antigravity 不提供这个模型")
                }
                selectedModel = selected.takeUnless { it == AGENT_DEFAULT_VALUE }
            }
            EFFORT_CONFIG_ID -> {
                if (selected != AGENT_DEFAULT_VALUE && selected !in EFFORTS) {
                    return@withLock AgentOperationResult.Failure("Antigravity 不提供这个推理强度")
                }
                selectedEffort = selected.takeUnless { it == AGENT_DEFAULT_VALUE }
            }
            else -> return@withLock AgentOperationResult.Unsupported("session/set_config:$configId")
        }
        configurationDirty = selectedModel != previousModel || selectedEffort != previousEffort || configurationDirty
        AgentOperationResult.Success(configuration())
    }

    override fun previewDraftModelConfiguration(
        providerId: String,
        modelId: String,
    ): AgentDraftConfigurationPreview? {
        if (providerId != MODEL_GROUP_ID || modelId !in modelsById) return null
        return AgentDraftConfigurationPreview(
            replaceCategories = setOf(AgentConfigCategory.ThoughtLevel),
            options = listOf(effortOption()),
        )
    }

    override suspend fun setMode(sessionId: String, modeId: String): AgentOperationResult<Unit> =
        operationMutex.withLock {
            if (active?.sessionId != sessionId) return@withLock AgentOperationResult.Failure("Antigravity 会话不匹配")
            if (modeId !in MODES) return@withLock AgentOperationResult.Failure("Antigravity 不提供这个执行模式")
            val previous = selectedMode
            selectedMode = modeId
            configurationDirty = selectedMode != previous || configurationDirty
            endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.CurrentModeChanged(modeId))
            AgentOperationResult.Success(Unit)
        }

    override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> {
        val current = active?.takeIf { it.sessionId == sessionId }
            ?: return AgentOperationResult.Failure("Antigravity 会话不匹配")
        current.stream.cancel()
        endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelled))
        return operationMutex.withLock {
            if (active !== current) return@withLock AgentOperationResult.Success(Unit)
            replaceStream(sessionId).map { Unit }
        }
    }

    override suspend fun disconnect() {
        val current = active
        active = null
        current?.stream?.close()
    }

    private suspend fun replaceStream(
        conversationId: String?,
    ): AgentOperationResult<ActiveAntigravityStream> {
        val arguments = buildList {
            add("--input-format")
            add("stream-json")
            add("--output-format")
            add("stream-json")
            add("--print-timeout")
            add("30m")
            conversationId?.let {
                add("--conversation")
                add(it)
            }
            selectedModel?.let {
                add("--model")
                add(it)
            }
            selectedEffort?.let {
                add("--effort")
                add(it)
            }
            when (selectedMode) {
                MODE_FULL -> add("--dangerously-skip-permissions")
                MODE_PLAN -> {
                    add("--mode=plan")
                    add("--sandbox")
                }
                else -> add("--sandbox")
            }
        }
        val process = try {
            launcher.launch(arguments)
        } catch (error: Throwable) {
            return AgentFailures.launch("无法启动 ${provider.name}: ${error.message}", error)
        }
        val candidate = AntigravityStream(process, endpoint, diagnosticSink)
        return try {
            candidate.start()
            val initialized = withTimeout(initializeTimeoutMs) { candidate.initialized.await() }
            if (conversationId != null && initialized != conversationId) {
                error("Antigravity 返回了不匹配的会话: $initialized")
            }
            val next = ActiveAntigravityStream(initialized, candidate)
            val previous = active
            active = next
            configurationDirty = false
            previous?.stream?.close()
            endpoint.eventSink.onEvent(initialized, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
            AgentOperationResult.Success(next)
        } catch (error: Throwable) {
            candidate.close()
            AgentFailures.initialize("${provider.name} stream-json 初始化失败: ${error.message}", error)
        }
    }

    private fun snapshot(sessionId: String): AgentSessionSnapshot = AgentSessionSnapshot(
        id = sessionId,
        configuration = configuration(),
        modes = listOf(
            AgentMode(MODE_DEFAULT, "受限执行", "文件操作按 Antigravity 默认策略，终端命令在沙箱中运行"),
            AgentMode(MODE_FULL, "完全执行", "自动批准全部工具调用"),
            AgentMode(MODE_PLAN, "只读计划", "只使用只读工具分析并输出计划"),
        ),
        currentModeId = selectedMode,
    )

    private fun configuration(): List<AgentConfigOption> = buildList {
        if (modelsById.isNotEmpty()) add(
            AgentConfigOption.Select(
                id = MODEL_CONFIG_ID,
                name = "模型",
                description = "来自 agy models 的当前官方目录",
                category = AgentConfigCategory.Model,
                currentValue = selectedModel ?: AGENT_DEFAULT_VALUE,
                choices = listOf(AgentConfigChoice(AGENT_DEFAULT_VALUE, "Agent 默认模型")) +
                    modelsById.values.map { model ->
                    AgentConfigChoice(
                        value = model.id,
                        name = model.name,
                        groupId = MODEL_GROUP_ID,
                        groupName = MODEL_GROUP_NAME,
                        modelSource = AgentModelSource.OfficialLogin,
                    )
                    },
            )
        )
        add(effortOption())
    }

    private fun effortOption() = AgentConfigOption.Select(
        id = EFFORT_CONFIG_ID,
        name = "推理强度",
        category = AgentConfigCategory.ThoughtLevel,
        currentValue = selectedEffort ?: AGENT_DEFAULT_VALUE,
        choices = listOf(
            AgentConfigChoice(AGENT_DEFAULT_VALUE, "Agent 默认强度"),
            AgentConfigChoice("low", "低", reasoning = AgentReasoningLevel.Low),
            AgentConfigChoice("medium", "中", reasoning = AgentReasoningLevel.Medium),
            AgentConfigChoice("high", "高", reasoning = AgentReasoningLevel.High),
        ),
    )

    private companion object {
        const val MODEL_CONFIG_ID = "antigravity.model"
        const val EFFORT_CONFIG_ID = "antigravity.effort"
        const val MODEL_GROUP_ID = "antigravity"
        const val MODEL_GROUP_NAME = "Antigravity 官方模型"
        const val MODE_DEFAULT = "default"
        const val MODE_FULL = "yolo"
        const val MODE_PLAN = "plan"
        const val AGENT_DEFAULT_VALUE = "__agent_default__"
        val EFFORTS = setOf("low", "medium", "high")
        val MODES = setOf(MODE_DEFAULT, MODE_FULL, MODE_PLAN)
    }
}

private class AntigravityStream(
    private val process: AgentProcessChannel,
    private val endpoint: AgentClientEndpoint,
    private val diagnosticSink: (String) -> Unit,
) {
    val initialized = CompletableDeferred<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("Antigravity-stream"))
    private val closed = AtomicBoolean(false)
    private val toolIds = ConcurrentHashMap.newKeySet<String>()
    private val streamId = UUID.randomUUID().toString().take(8)
    @Volatile private var sessionId: String? = null
    @Volatile private var pendingTurn: PendingAntigravityTurn? = null

    fun start() {
        scope.launch(Dispatchers.IO + CoroutineName("antigravity-stdout")) {
            process.stdoutLines.collect(::handleLine)
        }
        scope.launch(Dispatchers.IO + CoroutineName("antigravity-stderr")) {
            process.stderrLines.collect(diagnosticSink)
        }
        scope.launch(Dispatchers.IO + CoroutineName("antigravity-exit")) {
            val exit = process.awaitExit()
            if (!closed.get()) {
                val error = IllegalStateException("Antigravity stream-json 进程已退出，exitCode=$exit")
                initialized.completeExceptionally(error)
                pendingTurn?.result?.completeExceptionally(error)
            }
        }
    }

    suspend fun prompt(text: String, timeoutMs: Long): AgentOperationResult<AgentTurnResult> {
        check(pendingTurn == null) { "Antigravity 上一轮尚未结束" }
        val turn = PendingAntigravityTurn()
        pendingTurn = turn
        return try {
            process.writeLine(
                JSONObject()
                    .put("event", "user")
                    .put("message", JSONObject().put("content", text))
                    .toString(),
            )
            val result = withTimeout(timeoutMs) { turn.result.await() }
            if (result.status == "SUCCESS") {
                if (turn.text.isEmpty() && result.response.isNotEmpty()) {
                    emitAssistant(result.response)
                }
                AgentOperationResult.Success(
                    AgentTurnResult(
                        stopReason = AgentStopReason.EndTurn,
                        usage = turn.usage ?: result.usage,
                    )
                )
            } else {
                val message = result.error.ifBlank { "Antigravity 返回状态 ${result.status}" }
                AgentFailures.protocol(
                    message,
                    IllegalStateException(message),
                    code = if (message.contains("authentication required", ignoreCase = true)) {
                        AgentFailureCode.AuthenticationRequired
                    } else {
                        AgentFailureCode.ProtocolFailure
                    },
                )
            }
        } catch (error: Throwable) {
            AgentFailures.protocol("Antigravity 发送消息失败: ${error.message}", error)
        } finally {
            pendingTurn = null
        }
    }

    suspend fun cancel() {
        closed.set(true)
        process.stop()
        pendingTurn?.result?.completeExceptionally(IllegalStateException("Antigravity 会话已取消"))
        scope.cancel("Antigravity cancelled")
    }

    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { process.stop() }
        process.close()
        pendingTurn?.result?.completeExceptionally(IllegalStateException("Antigravity 会话已关闭"))
        scope.cancel("Antigravity closed")
    }

    private fun handleLine(line: String) {
        val event = runCatching { JSONObject(line) }.getOrElse {
            diagnosticSink("Antigravity 输出了无效 JSON")
            return
        }
        when (event.optString("event")) {
            "init" -> {
                val id = event.optString("conversation_id").trim().takeIf(String::isNotBlank)
                    ?: event.optJSONObject("init")?.optString("conversation_id")?.trim()?.takeIf(String::isNotBlank)
                    ?: return
                sessionId = id
                initialized.complete(id)
            }
            "step_update" -> handleStep(event.optJSONObject("step_update") ?: return)
            "result" -> handleResult(event.optJSONObject("result") ?: return)
            else -> diagnosticSink("Antigravity 忽略未知事件: ${event.optString("event")}")
        }
    }

    private fun handleStep(step: JSONObject) {
        val id = sessionId ?: return
        when (step.optString("step_type")) {
            "agent_response" -> {
                step.optString("text_delta").takeIf(String::isNotEmpty)?.let { delta ->
                    pendingTurn?.text?.append(delta)
                    emitAssistant(delta)
                }
                step.optJSONObject("usage")?.toTurnUsage()?.let { pendingTurn?.usage = it }
            }
            "tool" -> {
                val index = step.optLong("step_index", -1L)
                val toolId = "$id:agy-tool:$streamId:$index"
                val info = step.optJSONObject("tool_info")
                val name = step.optString("tool_name").ifBlank {
                    info?.optString("name").orEmpty().ifBlank { "工具" }
                }
                if (toolIds.add(toolId)) {
                    endpoint.eventSink.onEvent(
                        id,
                        AgentSessionEvent.ToolCallStarted(
                            AgentToolCall(
                                id = toolId,
                                title = name,
                                kind = AgentToolKind(name),
                                status = AgentToolStatus("in_progress"),
                                rawInput = info?.optJSONObject("parameters")?.toString(),
                            )
                        ),
                    )
                }
                if (step.optString("state") == "DONE") {
                    val error = info?.optJSONObject("error")
                    endpoint.eventSink.onEvent(
                        id,
                        AgentSessionEvent.ToolCallUpdated(
                            AgentToolCallPatch(
                                id = toolId,
                                status = AgentToolStatus(if (error == null) "completed" else "failed"),
                                rawOutput = error?.optString("message")?.takeIf(String::isNotBlank)
                                    ?: info?.opt("output")?.toString(),
                            )
                        ),
                    )
                }
            }
        }
    }

    private fun handleResult(result: JSONObject) {
        val envelope = AntigravityResult(
            status = result.optString("status").ifBlank { "ERROR" },
            response = result.optString("response"),
            error = result.optString("error"),
            usage = result.optJSONObject("usage")?.toTurnUsage(),
        )
        pendingTurn?.result?.complete(envelope)
    }

    private fun emitAssistant(text: String) {
        val id = sessionId ?: return
        endpoint.eventSink.onEvent(
            id,
            AgentSessionEvent.MessageChunk(AgentMessageRole.Assistant, AgentContent.Text(text)),
        )
    }
}

private class PendingAntigravityTurn {
    val result = CompletableDeferred<AntigravityResult>()
    val text = StringBuilder()
    @Volatile var usage: AgentTurnUsage? = null
}

private data class AntigravityResult(
    val status: String,
    val response: String,
    val error: String,
    val usage: AgentTurnUsage?,
)

private fun JSONObject.toTurnUsage(): AgentTurnUsage = AgentTurnUsage(
    inputTokens = optLong("input_tokens"),
    outputTokens = optLong("output_tokens"),
    totalTokens = optLong("total_tokens"),
    thoughtTokens = optLong("thinking_tokens").takeIf { has("thinking_tokens") },
    cachedReadTokens = optLong("cache_read_tokens").takeIf { has("cache_read_tokens") },
)

private fun parseModelLine(raw: String): AntigravityModel? {
    val line = raw.replace(ANSI_ESCAPE, "").trim().trimStart('*', '>').trim()
    if (line.isBlank() || line.startsWith("Available ", ignoreCase = true)) return null
    val separator = line.indexOfFirst(Char::isWhitespace)
    if (separator < 0) return null
    val id = line.substring(0, separator)
    if (!MODEL_ID.matches(id)) return null
    val name = line.removePrefix(id).trim().ifBlank { id }
    return AntigravityModel(id, name)
}

private fun List<AgentContent>.toAntigravityPrompt(): String? {
    val blocks = mapNotNull { content ->
        when (content) {
            is AgentContent.Text -> content.text
            is AgentContent.ResourceLink -> content.localFilePath()?.let { "[用户附加文件，可按需读取：$it]" }
            is AgentContent.EmbeddedText -> "[用户附加文本：${content.uri}]\n${content.text}"
            else -> return null
        }
    }
    return blocks.filter(String::isNotBlank).joinToString("\n\n").takeIf(String::isNotBlank)
}

private fun AgentContent.ResourceLink.localFilePath(): String? = runCatching {
    val parsed = URI(uri)
    if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching null
    parsed.path?.takeIf { it.startsWith('/') }
}.getOrNull()

private inline fun <T, R> AgentOperationResult<T>.map(transform: (T) -> R): AgentOperationResult<R> = when (this) {
    is AgentOperationResult.Success -> AgentOperationResult.Success(transform(value))
    is AgentOperationResult.Failure -> this
    is AgentOperationResult.Unsupported -> this
}

private val MODEL_ID = Regex("[a-z0-9][A-Za-z0-9._/-]*")
private val ANSI_ESCAPE = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
