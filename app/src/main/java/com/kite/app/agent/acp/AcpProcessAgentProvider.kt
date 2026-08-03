package com.kite.app.agent.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo as AcpClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ClientCapabilities as AcpClientCapabilities
import com.agentclientprotocol.model.AuthCapabilities
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.FileSystemCapability
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionModeId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.contract.AgentSessionSnapshot
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** 一份 ACP stdio provider 的启动描述。产品差异只能停留在这里或更窄的 adapter。 */
data class AcpProcessProviderDescriptor(
    val id: String,
    val name: String,
    val title: String? = null,
    val version: String? = null
)

/**
 * ACP 的 cwd 必须属于实际运行 Agent 进程的文件系统视图。Kite 页面继续使用稳定的
 * `/workspace` 语义；Host runtime 只在协议边界映射到物理目录。
 */
data class AcpSessionPathMapper(
    val toAgent: (String) -> String = { it },
    val fromAgent: (String) -> String = { it },
)

fun interface AcpProcessChannelLauncher {
    suspend fun launch(): AgentProcessChannel
}

/**
 * 使用官方 ACP SDK 驱动任意 stdio Agent。这里不知道 OpenCode、资源卡或 Android 页面。
 */
@OptIn(UnstableApi::class)
class AcpProcessAgentProvider(
    private val descriptor: AcpProcessProviderDescriptor,
    private val launcher: AcpProcessChannelLauncher,
    private val initializeTimeoutMs: Long = DEFAULT_INITIALIZE_TIMEOUT_MS,
    private val diagnosticSink: (String) -> Unit = {},
    private val sessionDelete: (suspend (sessionId: String) -> AgentOperationResult<Unit>)? = null,
    private val sessionRename: (suspend (request: AgentSessionRenameRequest) -> AgentOperationResult<Unit>)? = null,
    private val sessionPathMapper: AcpSessionPathMapper = AcpSessionPathMapper(),
) : KiteAgentProvider {
    override val id: String = descriptor.id

    override suspend fun connect(
        request: AgentConnectionRequest,
        client: AgentClientEndpoint
    ): AgentOperationResult<KiteAgentConnection> {
        val process = try {
            launcher.launch()
        } catch (error: Throwable) {
            return AgentOperationResult.Failure("无法启动 ${descriptor.name}: ${error.message}", error)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("Acp-${descriptor.id}"))
        val inlineSessionUpdates = AcpInlineSessionUpdateBuffer()
        val transport = StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = process.stdoutLines.onEach(inlineSessionUpdates::inspect),
            output = process::writeLine,
            name = "${descriptor.id}-stdio"
        )
        val protocol = Protocol(scope, transport)
        val acpClient = Client(protocol)

        scope.launch(Dispatchers.IO + CoroutineName("${descriptor.id}-stderr")) {
            process.stderrLines.collect(diagnosticSink)
        }

        return try {
            protocol.start()
            val agentInfo = withTimeout(initializeTimeoutMs) {
                acpClient.initialize(
                    AcpClientInfo(
                        protocolVersion = LATEST_PROTOCOL_VERSION,
                        capabilities = request.capabilities.toAcp(),
                        implementation = Implementation(
                            name = request.client.name,
                            version = request.client.version,
                            title = request.client.title
                        )
                    )
                )
            }
            val mappedCapabilities = AcpAgentMapper.capabilities(
                agentInfo.capabilities,
                agentInfo.authMethods
            ).let { capabilities ->
                capabilities.copy(
                    sessions = capabilities.sessions.copy(
                        delete = capabilities.sessions.delete || sessionDelete != null,
                        rename = capabilities.sessions.rename || sessionRename != null,
                    )
                )
            }
            AgentOperationResult.Success(
                AcpProcessAgentConnection(
                    descriptor = descriptor,
                    process = process,
                    scope = scope,
                    protocol = protocol,
                    client = acpClient,
                    endpoint = client,
                    provider = AgentProviderInfo(
                        id = descriptor.id,
                        name = agentInfo.implementation?.name ?: descriptor.name,
                        version = agentInfo.implementation?.version ?: descriptor.version,
                        title = agentInfo.implementation?.title ?: descriptor.title
                    ),
                    capabilities = mappedCapabilities,
                    sessionDelete = sessionDelete,
                    sessionRename = sessionRename,
                    sessionPathMapper = sessionPathMapper,
                    inlineSessionUpdates = inlineSessionUpdates,
                )
            )
        } catch (error: Throwable) {
            protocol.close()
            runCatching { process.stop() }
            scope.cancel("ACP initialize failed", error)
            AgentOperationResult.Failure("${descriptor.name} ACP 初始化失败: ${error.message}", error)
        }
    }

    private fun com.kite.app.agent.contract.AgentClientCapabilities.toAcp(): AcpClientCapabilities =
        AcpClientCapabilities(
            fs = if (readTextFiles || writeTextFiles) {
                FileSystemCapability(readTextFile = readTextFiles, writeTextFile = writeTextFiles)
            } else {
                null
            },
            terminal = terminals,
            auth = if (authentication) AuthCapabilities() else null
        )

    companion object {
        const val DEFAULT_INITIALIZE_TIMEOUT_MS = 20_000L
    }
}

@OptIn(UnstableApi::class)
private class AcpProcessAgentConnection(
    private val descriptor: AcpProcessProviderDescriptor,
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val protocol: Protocol,
    private val client: Client,
    private val endpoint: AgentClientEndpoint,
    override val provider: AgentProviderInfo,
    override val capabilities: AgentCapabilities,
    private val sessionDelete: (suspend (sessionId: String) -> AgentOperationResult<Unit>)?,
    private val sessionRename: (suspend (request: AgentSessionRenameRequest) -> AgentOperationResult<Unit>)?,
    private val sessionPathMapper: AcpSessionPathMapper,
    private val inlineSessionUpdates: AcpInlineSessionUpdateBuffer,
) : KiteAgentConnection {
    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private val activePrompts = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val steeringSessions = ConcurrentHashMap.newKeySet<String>()
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
                            "${descriptor.name} ACP 进程已退出，exitCode=$exitCode"
                        )
                    )
                }
            }
        }
    }

    override suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
        createSession(request.cwd, request.additionalDirectories) {
            client.newSession(SessionCreationParameters(
                sessionPathMapper.toAgent(request.cwd),
                emptyList(),
                request.additionalDirectories.map(sessionPathMapper.toAgent),
            )) {
                    sessionId, response ->
                AcpClientOperations(sessionId.value, endpoint)
            }
        }

    override suspend fun loadSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> {
        if (!capabilities.sessions.load) return AgentOperationResult.Unsupported("session/load")
        return restoreSessionWithInlineReplay(request) { relay ->
            client.loadSession(
                SessionId(request.sessionId),
                request.toAcpSessionParameters(),
            ) { sessionId, _ -> AcpClientOperations(sessionId.value, endpoint, relay) }
        }
    }

    override suspend fun listSessions(request: AgentSessionListRequest): AgentOperationResult<AgentSessionPage> {
        if (!capabilities.sessions.list) return AgentOperationResult.Unsupported("session/list")
        return operation("列出会话") {
            val sessions = client.listSessions(request.cwd?.let(sessionPathMapper.toAgent), emptyList()).toList()
            AgentSessionPage(sessions = sessions.map(AcpAgentMapper::sessionSummary).map { summary ->
                summary.copy(
                    cwd = sessionPathMapper.fromAgent(summary.cwd),
                    additionalDirectories = summary.additionalDirectories.map(sessionPathMapper.fromAgent),
                )
            })
        }
    }

    override suspend fun resumeSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> {
        if (!capabilities.sessions.resume) return AgentOperationResult.Unsupported("session/resume")
        return createSession(request.cwd, request.additionalDirectories, request.sessionId) {
            client.resumeSession(
                SessionId(request.sessionId),
                request.toAcpSessionParameters(),
            ) { sessionId, _ -> AcpClientOperations(sessionId.value, endpoint) }
        }
    }

    private suspend fun restoreSessionWithInlineReplay(
        request: AgentExistingSessionRequest,
        restore: suspend (AcpInlineSessionUpdateRelay) -> ClientSession,
    ): AgentOperationResult<AgentSessionSnapshot> {
        val relay = inlineSessionUpdates.begin(request.sessionId) { update ->
            endpoint.eventSink.onEvent(request.sessionId, AcpAgentMapper.sessionEvent(update))
        }
        val result = try {
            createSession(request.cwd, request.additionalDirectories, request.sessionId) {
                restore(relay)
            }
        } catch (cancelled: CancellationException) {
            relay.abort()
            throw cancelled
        } finally {
            inlineSessionUpdates.end(request.sessionId, relay)
        }
        if (result is AgentOperationResult.Success) {
            relay.complete()
            scope.launch {
                delay(INLINE_REPLAY_DEDUPLICATION_MS)
                relay.releaseDeduplication()
            }
        } else {
            relay.abort()
        }
        return result
    }

    private fun AgentExistingSessionRequest.toAcpSessionParameters(): SessionCreationParameters =
        SessionCreationParameters(
            sessionPathMapper.toAgent(cwd),
            emptyList(),
            additionalDirectories.map(sessionPathMapper.toAgent),
        )

    override suspend fun forkSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> {
        if (!capabilities.sessions.fork) return AgentOperationResult.Unsupported("session/fork")
        return createSession(request.cwd, request.additionalDirectories) {
            client.forkSession(
                SessionId(request.sessionId),
                SessionCreationParameters(
                    sessionPathMapper.toAgent(request.cwd),
                    emptyList(),
                    request.additionalDirectories.map(sessionPathMapper.toAgent),
                )
            ) { sessionId, _ -> AcpClientOperations(sessionId.value, endpoint) }
        }
    }

    override suspend fun closeSession(sessionId: String): AgentOperationResult<Unit> {
        if (!capabilities.sessions.close) return AgentOperationResult.Unsupported("session/close")
        val session = sessions[sessionId] ?: return AgentOperationResult.Failure("会话不存在: $sessionId")
        return operation("关闭会话") {
            session.close()
            sessions.remove(sessionId)
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed)
            )
        }
    }

    override suspend fun deleteSession(sessionId: String): AgentOperationResult<Unit> {
        val delete = sessionDelete ?: return AgentOperationResult.Unsupported("session/delete")
        return when (val deleted = delete(sessionId)) {
            is AgentOperationResult.Success -> {
                sessions.remove(sessionId)
                endpoint.eventSink.onEvent(
                    sessionId,
                    AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed, "Agent 会话已删除")
                )
                deleted
            }
            is AgentOperationResult.Failure -> deleted
            is AgentOperationResult.Unsupported -> deleted
        }
    }

    override suspend fun renameSession(request: AgentSessionRenameRequest): AgentOperationResult<Unit> {
        val rename = sessionRename ?: return AgentOperationResult.Unsupported("session/rename")
        return rename(request)
    }

    override suspend fun setMode(sessionId: String, modeId: String): AgentOperationResult<Unit> {
        val session = sessions[sessionId] ?: return AgentOperationResult.Failure("会话不存在: $sessionId")
        val configMode = session.configuration()
            .filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Mode }
        if (configMode != null) {
            if (configMode.choices.none { it.value == modeId }) {
                return AgentOperationResult.Failure("Agent 未提供该工作模式")
            }
            if (configMode.currentValue == modeId) return AgentOperationResult.Success(Unit)
            return operation("切换工作模式") {
                val response = session.setConfigOption(
                    SessionConfigId(configMode.id),
                    SessionConfigOptionValue.StringValue(modeId),
                )
                val configuration = session.configuration(
                    configOptions = AcpAgentMapper.configOptions(response.configOptions),
                )
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.ConfigurationUpdated(configuration))
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.CurrentModeChanged(modeId))
                Unit
            }
        }
        if (!session.modesSupported) return AgentOperationResult.Unsupported("session/set_mode")
        if (session.availableModes.none { it.id.value == modeId }) {
            return AgentOperationResult.Failure("Agent 未提供该工作模式")
        }
        return operation("切换工作模式") {
            session.setMode(SessionModeId(modeId))
            endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.CurrentModeChanged(modeId))
            Unit
        }
    }

    override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> {
        val finished = CompletableDeferred<Unit>()
        if (activePrompts.putIfAbsent(request.sessionId, finished) != null) {
            return AgentOperationResult.Failure("ACP 当前已有一轮回复正在生成")
        }
        return try {
            prompt(request, publishLifecycle = true)
        } finally {
            activePrompts.remove(request.sessionId, finished)
            finished.complete(Unit)
        }
    }

    override suspend fun steer(request: AgentPromptRequest): AgentOperationResult<Unit> {
        val session = sessions[request.sessionId]
            ?: return AgentOperationResult.Failure("会话不存在: ${request.sessionId}")
        val running = activePrompts[request.sessionId]
            ?: return AgentOperationResult.Failure("ACP 当前没有可插话的回复")
        if (!steeringSessions.add(request.sessionId)) {
            return AgentOperationResult.Failure("ACP 正在切换到新的用户输入")
        }
        return try {
            session.cancel()
            val interrupted = withTimeoutOrNull(STEER_INTERRUPT_TIMEOUT_MS) {
                running.await()
                true
            } == true
            if (!interrupted) {
                return AgentOperationResult.Failure("ACP 当前回复未能及时停止，插话未发送")
            }
            val replacement = CompletableDeferred<Unit>()
            if (activePrompts.putIfAbsent(request.sessionId, replacement) != null) {
                return AgentOperationResult.Failure("ACP 当前回复仍在结束中，插话未发送")
            }
            try {
                when (val result = prompt(request, publishLifecycle = false)) {
                    is AgentOperationResult.Success -> {
                        endpoint.eventSink.onEvent(
                            request.sessionId,
                            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready),
                        )
                        AgentOperationResult.Success(Unit)
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
            } finally {
                activePrompts.remove(request.sessionId, replacement)
                replacement.complete(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, error.message),
            )
            AcpAgentMapper.failure("插入当前回复", error)
        } finally {
            steeringSessions.remove(request.sessionId)
        }
    }

    /** ACP SDK 禁止并发 prompt；这里只等待当前 prompt 的中断确认，随后在原会话立即发送。 */
    private suspend fun prompt(
        request: AgentPromptRequest,
        publishLifecycle: Boolean,
    ): AgentOperationResult<AgentTurnResult> {
        val session = sessions[request.sessionId]
            ?: return AgentOperationResult.Failure("会话不存在: ${request.sessionId}")
        if (publishLifecycle) {
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting)
            )
        }
        return try {
            var result: AgentTurnResult? = null
            session.prompt(request.content.map(AgentContent::toAcp)).collect { event ->
                when (event) {
                    is Event.SessionUpdateEvent -> endpoint.eventSink.onEvent(
                        request.sessionId,
                        AcpAgentMapper.sessionEvent(event.update)
                    )
                    is Event.PromptResponseEvent -> result = AgentTurnResult(
                        stopReason = AcpAgentMapper.stopReason(event.response.stopReason),
                        userMessageId = event.response.userMessageId?.value,
                        usage = event.response.usage?.let { usage ->
                            AgentTurnUsage(
                                inputTokens = usage.inputTokens,
                                outputTokens = usage.outputTokens,
                                totalTokens = usage.totalTokens,
                                thoughtTokens = usage.thoughtTokens,
                                cachedReadTokens = usage.cachedReadTokens,
                                cachedWriteTokens = usage.cachedWriteTokens
                            )
                        }
                    )
                }
            }
            val resolved = result ?: error("ACP prompt 没有返回 stop reason")
            if (publishLifecycle && request.sessionId !in steeringSessions) {
                endpoint.eventSink.onEvent(
                    request.sessionId,
                    AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready)
                )
            }
            AgentOperationResult.Success(resolved)
        } catch (cancelled: CancellationException) {
            if (publishLifecycle && request.sessionId !in steeringSessions) {
                endpoint.eventSink.onEvent(
                    request.sessionId,
                    AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelled)
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            if (publishLifecycle && request.sessionId !in steeringSessions) {
                endpoint.eventSink.onEvent(
                    request.sessionId,
                    AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, error.message)
                )
            }
            AcpAgentMapper.failure("发送消息", error)
        }
    }

    override suspend fun setConfiguration(
        sessionId: String,
        configId: String,
        value: AgentConfigValue
    ): AgentOperationResult<List<com.kite.app.agent.contract.AgentConfigOption>> {
        val session = sessions[sessionId] ?: return AgentOperationResult.Failure("会话不存在: $sessionId")
        if (configId == ACP_SESSION_MODEL_CONFIG_ID) {
            if (!session.modelsSupported) return AgentOperationResult.Unsupported("session/set_model")
            val modelId = (value as? AgentConfigValue.Select)?.value
                ?: return AgentOperationResult.Failure("模型配置必须是单选值")
            if (session.availableModels.none { it.modelId.value == modelId }) {
                return AgentOperationResult.Failure("Agent 未提供该模型")
            }
            return operation("切换模型") {
                session.setModel(ModelId(modelId))
                session.configuration(modelId)
            }
        }
        if (!session.configOptionsSupported) return AgentOperationResult.Unsupported("session/set_config_option")
        return operation("更新会话配置") {
            val acpValue = when (value) {
                is AgentConfigValue.Select -> SessionConfigOptionValue.StringValue(value.value)
                is AgentConfigValue.Toggle -> SessionConfigOptionValue.BoolValue(value.value)
            }
            val response = session.setConfigOption(SessionConfigId(configId), acpValue)
            session.configuration(configOptions = AcpAgentMapper.configOptions(response.configOptions))
        }
    }

    override suspend fun authenticate(methodId: String): AgentOperationResult<Unit> {
        if (capabilities.authentication.methods.none { it.id == methodId }) {
            return AgentOperationResult.Unsupported("authenticate:$methodId")
        }
        return operation("Agent 认证") {
            client.authenticate(AuthMethodId(methodId))
            Unit
        }
    }

    override suspend fun logout(): AgentOperationResult<Unit> {
        if (!capabilities.authentication.logout) return AgentOperationResult.Unsupported("logout")
        return operation("Agent 退出登录") {
            client.logout()
            Unit
        }
    }

    override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> {
        val session = sessions[sessionId] ?: return AgentOperationResult.Failure("会话不存在: $sessionId")
        endpoint.eventSink.onEvent(
            sessionId,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelling)
        )
        return operation("取消生成") {
            session.cancel()
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelled)
            )
        }
    }

    override suspend fun disconnect() {
        if (!disconnecting.compareAndSet(false, true)) return
        activePrompts.values.forEach { it.cancel() }
        activePrompts.clear()
        steeringSessions.clear()
        sessions.keys.forEach { sessionId ->
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed)
            )
        }
        sessions.clear()
        protocol.close()
        process.stop()
        scope.cancel("ACP disconnected")
    }

    private suspend fun createSession(
        cwd: String,
        additionalDirectories: List<String>,
        requestedSessionId: String? = null,
        create: suspend () -> ClientSession
    ): AgentOperationResult<AgentSessionSnapshot> {
        requestedSessionId?.let { sessionId ->
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Preparing)
            )
        }
        return operation("创建会话") {
            val session = create()
            val sessionId = session.sessionId.value
            sessions[sessionId] = session
            val snapshot = session.snapshot()
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready, "准备就绪")
            )
            snapshot
        }
    }

    private fun ClientSession.snapshot(): AgentSessionSnapshot = AgentSessionSnapshot(
        id = sessionId.value,
        configuration = configuration(),
        modes = if (modesSupported) {
            availableModes.map { mode -> AgentMode(mode.id.value, mode.name, mode.description) }
        } else {
            emptyList()
        },
        currentModeId = if (modesSupported) currentMode.value.value else null
    )

    private fun ClientSession.configuration(
        modelId: String? = null,
        configOptions: List<com.kite.app.agent.contract.AgentConfigOption> =
            if (configOptionsSupported) AcpAgentMapper.configOptions(this.configOptions.value) else emptyList()
    ): List<com.kite.app.agent.contract.AgentConfigOption> {
        if (!modelsSupported) return configOptions
        val current = modelId ?: currentModel.value.value
        return listOf(AcpAgentMapper.modelOption(current, availableModels)) +
            configOptions.filterNot { it.category == com.kite.app.agent.contract.AgentConfigCategory.Model }
    }

    private suspend fun <T> operation(label: String, block: suspend () -> T): AgentOperationResult<T> = try {
        AgentOperationResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        AcpAgentMapper.failure(label, error)
    }
}

internal const val ACP_SESSION_MODEL_CONFIG_ID = "acp.session.model"
private const val STEER_INTERRUPT_TIMEOUT_MS = 5_000L
private const val INLINE_REPLAY_DEDUPLICATION_MS = 1_000L

@OptIn(UnstableApi::class)
private class AcpClientOperations(
    private val sessionId: String,
    private val endpoint: AgentClientEndpoint,
    private val inlineSessionUpdateRelay: AcpInlineSessionUpdateRelay? = null,
) : ClientSessionOperations {
    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<com.agentclientprotocol.model.PermissionOption>,
        _meta: kotlinx.serialization.json.JsonElement?
    ): RequestPermissionResponse {
        val request = com.agentclientprotocol.model.RequestPermissionRequest(
            sessionId = SessionId(sessionId),
            toolCall = toolCall,
            options = permissions,
            _meta = _meta
        )
        val outcome = endpoint.permissionHandler.request(AcpAgentMapper.permissionRequest(request))
        return RequestPermissionResponse(AcpAgentMapper.permissionOutcome(outcome), _meta)
    }

    override suspend fun notify(
        notification: SessionUpdate,
        _meta: kotlinx.serialization.json.JsonElement?
    ) {
        inlineSessionUpdateRelay?.fromSdk(notification)
            ?: endpoint.eventSink.onEvent(sessionId, AcpAgentMapper.sessionEvent(notification))
    }
}

@OptIn(UnstableApi::class)
internal fun AgentContent.toAcp(): ContentBlock = when (this) {
    is AgentContent.Text -> ContentBlock.Text(text = text)
    is AgentContent.SkillReference -> error("SkillReference 必须在进入 ACP 前转换为文本")
    is AgentContent.Image -> ContentBlock.Image(
        data = data,
        mimeType = mimeType,
        uri = uri.toAgentVisibleMediaUri(),
    )
    is AgentContent.Audio -> ContentBlock.Audio(data = data, mimeType = mimeType)
    is AgentContent.ResourceLink -> ContentBlock.ResourceLink(
        name = name,
        uri = uri,
        description = description,
        mimeType = mimeType,
        size = size,
        title = title
    )
    is AgentContent.EmbeddedText -> ContentBlock.Resource(
        EmbeddedResourceResource.TextResourceContents(text = text, uri = uri, mimeType = mimeType)
    )
    is AgentContent.EmbeddedBlob -> ContentBlock.Resource(
        EmbeddedResourceResource.BlobResourceContents(blob = data, uri = uri, mimeType = mimeType)
    )
}

/** Android 内容提供器地址只对 Kite 进程有效，不能作为 Agent 可访问的媒体地址。 */
private fun String?.toAgentVisibleMediaUri(): String? {
    val normalized = this?.trim()?.takeIf(String::isNotBlank) ?: return null
    val scheme = normalized.substringBefore(':', missingDelimiterValue = "").lowercase()
    return normalized.takeUnless { scheme in ANDROID_PRIVATE_MEDIA_URI_SCHEMES }
}

private val ANDROID_PRIVATE_MEDIA_URI_SCHEMES = setOf("content", "android.resource", "msf")
