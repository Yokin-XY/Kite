package com.kite.app.agent.runtime

import com.kite.app.agent.contract.AgentClientCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionSnapshot
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import com.kite.app.agent.config.AgentSessionModelSelection
import com.kite.app.agent.store.AgentConversationKey
import com.kite.app.agent.store.AgentConversationStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class AgentRuntimeStartRequest(
    val instanceId: String,
    val generation: Long,
    val providerId: String,
    val cwd: String,
    val additionalDirectories: List<String> = emptyList(),
    val preferredSessionId: String? = null,
    val normalizeConfiguration: (List<AgentConfigOption>) -> List<AgentConfigOption> = { it },
    val resolveDraftModelSelection: (
        target: AgentDraftModelSelection,
        options: List<AgentConfigOption>
    ) -> AgentSessionModelSelection? = { _, _ -> null },
    val initialDraftCatalog: AgentDraftCapabilityCatalog = AgentDraftCapabilityCatalog(),
    val onDraftCatalogChanged: (AgentDraftCapabilityCatalog) -> Unit = {}
)

/** 当前空白页的瞬时模型目标；不写 Agent 原生默认，也不属于任何已创建会话。 */
data class AgentDraftModelSelection(
    val providerId: String,
    val modelId: String,
    val usesAgentDefault: Boolean
)

/**
 * 最近一次真实会话公布的可选能力目录。
 *
 * 它只给当前运行实例的空白草稿提供预选项，不包含消息、会话 ID、密钥或持久默认值。
 */
data class AgentDraftCapabilityCatalog(
    val configuration: List<AgentConfigOption> = emptyList(),
    val modes: List<com.kite.app.agent.contract.AgentMode> = emptyList(),
    val currentModeId: String? = null,
    val commands: List<AgentCommand> = emptyList()
)

data class AgentDraftPreferences(
    val configuration: Map<String, AgentConfigValue> = emptyMap(),
    val modeId: String? = null
)

data class AgentRuntimeSession(
    val instanceId: String,
    val generation: Long,
    val providerId: String,
    val sessionId: String?,
    val cwd: String,
    val snapshot: AgentSessionSnapshot?,
    val capabilities: AgentCapabilities
) {
    val isDraft: Boolean get() = sessionId == null
}

fun interface AgentRuntimeStatusSink {
    fun onStatus(sessionId: String?, phase: AgentSessionPhase, message: String?)
}

/**
 * Agent 长连接与待决权限的进程级拥有者。
 *
 * CardRunStore 仍拥有运行实例事实；这里仅持有不可持久化的 connection 对象，并把协议事件投影到
 * AgentConversationStore。页面只能提交 prompt/cancel/permission 等意图，不能直接持有 SDK 连接。
 */
object AgentRuntimeRegistry {
    private const val MAX_SESSION_LIST_PAGES = 100

    private class ActiveRuntime(
        @Volatile var session: AgentRuntimeSession,
        val defaultCwd: String,
        val additionalDirectories: List<String>,
        val connection: KiteAgentConnection,
        val statusSink: AgentRuntimeStatusSink,
        val normalizeConfiguration: (List<AgentConfigOption>) -> List<AgentConfigOption>,
        val resolveDraftModelSelection: (
            target: AgentDraftModelSelection,
            options: List<AgentConfigOption>
        ) -> AgentSessionModelSelection?,
        val sessionOperationMutex: Mutex = Mutex(),
        @Volatile var draftModelSelection: AgentDraftModelSelection? = null,
        @Volatile var draftCatalog: AgentDraftCapabilityCatalog = AgentDraftCapabilityCatalog(),
        val draftCatalogLock: Any = Any(),
        val draftConfiguration: LinkedHashMap<String, AgentConfigValue> = linkedMapOf(),
        @Volatile var draftModeId: String? = null,
        val onDraftCatalogChanged: (AgentDraftCapabilityCatalog) -> Unit = {}
    )

    private data class PendingPermission(
        val generation: Long,
        val conversationKey: AgentConversationKey,
        val deferred: CompletableDeferred<AgentPermissionOutcome>
    )

    private val activeByInstance = ConcurrentHashMap<String, ActiveRuntime>()
    private val permissionByInstance = ConcurrentHashMap<String, PendingPermission>()

    suspend fun start(
        request: AgentRuntimeStartRequest,
        provider: KiteAgentProvider,
        statusSink: AgentRuntimeStatusSink
    ): AgentOperationResult<AgentRuntimeSession> {
        activeByInstance[request.instanceId]?.let { existing ->
            if (existing.session.generation == request.generation) {
                return AgentOperationResult.Failure("Agent 运行实例已经连接")
            }
            activeByInstance.remove(request.instanceId, existing)
            existing.connection.disconnect()
        }

        var observedCatalog = request.initialDraftCatalog
        val catalogLock = Any()
        fun updateObservedCatalog(
            transform: (AgentDraftCapabilityCatalog) -> AgentDraftCapabilityCatalog
        ) {
            var active: ActiveRuntime? = null
            val observedChange = synchronized(catalogLock) {
                active = activeByInstance[request.instanceId]
                    ?.takeIf { it.session.generation == request.generation }
                if (active == null) {
                    val before = observedCatalog
                    before to transform(before).also { observedCatalog = it }
                } else {
                    null
                }
            }
            active?.let { current ->
                current.updateDraftCatalog(transform)
                return
            }
            val (previous, next) = observedChange ?: return
            if (next != previous) request.onDraftCatalogChanged(next)
        }
        val endpoint = AgentClientEndpoint(
            eventSink = { sessionId, event ->
                val normalizedEvent = when (event) {
                    is AgentSessionEvent.ConfigurationUpdated -> event.copy(
                        options = request.normalizeConfiguration(event.options)
                    )
                    else -> event
                }
                when (normalizedEvent) {
                    is AgentSessionEvent.ConfigurationUpdated -> updateObservedCatalog { current ->
                        current.copy(configuration = normalizedEvent.options)
                    }
                    is AgentSessionEvent.CommandsUpdated -> updateObservedCatalog { current ->
                        current.copy(commands = normalizedEvent.commands)
                    }
                    is AgentSessionEvent.CurrentModeChanged -> updateObservedCatalog { current ->
                        current.copy(currentModeId = normalizedEvent.modeId)
                    }
                    else -> Unit
                }
                val key = AgentConversationKey(request.providerId, sessionId)
                if (AgentConversationStore.snapshot(key) == null) {
                    val initialPhase = (normalizedEvent as? AgentSessionEvent.LifecycleChanged)?.phase
                        ?: AgentSessionPhase.Preparing
                    AgentConversationStore.bind(request.instanceId, key, initialPhase)
                }
                AgentConversationStore.applyEvent(key, normalizedEvent)
                if (normalizedEvent is AgentSessionEvent.LifecycleChanged) {
                    statusSink.onStatus(sessionId, normalizedEvent.phase, normalizedEvent.message)
                }
            },
            permissionHandler = { permission ->
                val key = AgentConversationKey(request.providerId, permission.sessionId)
                if (AgentConversationStore.snapshot(key) == null) {
                    AgentConversationStore.bind(request.instanceId, key, AgentSessionPhase.WaitingPermission)
                }
                AgentConversationStore.requestPermission(key, permission)
                statusSink.onStatus(permission.sessionId, AgentSessionPhase.WaitingPermission, "等待权限选择")
                val pending = PendingPermission(
                    generation = request.generation,
                    conversationKey = key,
                    deferred = CompletableDeferred()
                )
                val previous = permissionByInstance.putIfAbsent(request.instanceId, pending)
                if (previous != null) {
                    AgentPermissionOutcome.Cancelled
                } else {
                    try {
                        pending.deferred.await()
                    } finally {
                        permissionByInstance.remove(request.instanceId, pending)
                        val restored = AgentConversationStore.resolvePermission(key)
                        statusSink.onStatus(
                            permission.sessionId,
                            restored?.phase ?: AgentSessionPhase.Ready,
                            "权限请求已处理"
                        )
                    }
                }
            }
        )
        val connected = provider.connect(
            AgentConnectionRequest(
                client = AgentClientInfo(name = "kite", version = "1", title = "Kite"),
                capabilities = AgentClientCapabilities()
            ),
            endpoint
        )
        val connection = when (connected) {
            is AgentOperationResult.Success -> connected.value
            is AgentOperationResult.Failure -> return connected
            is AgentOperationResult.Unsupported -> return connected
        }
        val preferredSessionId = request.preferredSessionId?.trim()?.takeIf(String::isNotBlank)
        val additionalDirectories = request.additionalDirectories
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .takeIf { connection.capabilities.sessions.additionalDirectories }
            .orEmpty()
        val session = if (preferredSessionId == null) {
            AgentRuntimeSession(
                instanceId = request.instanceId,
                generation = request.generation,
                providerId = request.providerId,
                sessionId = null,
                cwd = request.cwd,
                snapshot = null,
                capabilities = connection.capabilities
            )
        } else {
            val opened = restoreExistingSession(
                connection = connection,
                instanceId = request.instanceId,
                providerId = request.providerId,
                sessionId = preferredSessionId,
                cwd = request.cwd,
                additionalDirectories = additionalDirectories
            )
            val openedSnapshot = when (opened) {
                is AgentOperationResult.Success -> opened.value.copy(
                    configuration = request.normalizeConfiguration(opened.value.configuration)
                )
                is AgentOperationResult.Failure -> {
                    connection.disconnect()
                    return opened
                }
                is AgentOperationResult.Unsupported -> {
                    connection.disconnect()
                    return opened
                }
            }
            AgentRuntimeSession(
                instanceId = request.instanceId,
                generation = request.generation,
                providerId = request.providerId,
                sessionId = openedSnapshot.id,
                cwd = request.cwd,
                snapshot = openedSnapshot,
                capabilities = connection.capabilities
            ).also {
                updateObservedCatalog { current -> current.withSnapshot(openedSnapshot) }
                bindSnapshot(it, openedSnapshot)
            }
        }
        val runtime = ActiveRuntime(
            session = session,
            defaultCwd = request.cwd,
            additionalDirectories = additionalDirectories,
            connection = connection,
            statusSink = statusSink,
            normalizeConfiguration = request.normalizeConfiguration,
            resolveDraftModelSelection = request.resolveDraftModelSelection,
            draftCatalog = observedCatalog,
            onDraftCatalogChanged = request.onDraftCatalogChanged,
        )
        val previous = synchronized(catalogLock) {
            runtime.draftCatalog = observedCatalog
            activeByInstance.putIfAbsent(request.instanceId, runtime)
        }
        if (previous != null) {
            connection.disconnect()
            return AgentOperationResult.Failure("Agent 运行实例连接发生冲突")
        }
        statusSink.onStatus(
            session.sessionId,
            AgentSessionPhase.Ready,
            if (session.isDraft) "可以开始新会话" else "准备就绪"
        )
        return AgentOperationResult.Success(session)
    }

    fun session(instanceId: String): AgentRuntimeSession? = activeByInstance[instanceId]?.session

    fun defaultCwd(instanceId: String, generation: Long): String? = activeByInstance[instanceId]
        ?.takeIf { it.session.generation == generation }
        ?.defaultCwd

    fun draftModelSelection(instanceId: String, generation: Long): AgentDraftModelSelection? =
        activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation && it.session.isDraft }
            ?.draftModelSelection

    fun draftCapabilityCatalog(instanceId: String, generation: Long): AgentDraftCapabilityCatalog? =
        activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation && it.session.isDraft }
            ?.draftCatalog

    fun draftPreferences(instanceId: String, generation: Long): AgentDraftPreferences? =
        activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation && it.session.isDraft }
            ?.let { active ->
                AgentDraftPreferences(
                    configuration = synchronized(active.draftConfiguration) {
                        active.draftConfiguration.toMap()
                    },
                    modeId = active.draftModeId
                )
            }

    fun selectDraftModel(
        instanceId: String,
        generation: Long,
        selection: AgentDraftModelSelection
    ): AgentOperationResult<AgentDraftModelSelection> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (!active.session.isDraft) {
            return AgentOperationResult.Unsupported("session/draft-model-active-session")
        }
        active.draftModelSelection = selection
        active.draftCatalog.configuration
            .filter { it.category == com.kite.app.agent.contract.AgentConfigCategory.Model }
            .forEach { option -> synchronized(active.draftConfiguration) { active.draftConfiguration.remove(option.id) } }
        return AgentOperationResult.Success(selection)
    }

    fun selectDraftConfiguration(
        instanceId: String,
        generation: Long,
        configId: String,
        value: AgentConfigValue
    ): AgentOperationResult<AgentDraftPreferences> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (!active.session.isDraft) {
            return AgentOperationResult.Unsupported("session/draft-config-active-session")
        }
        val option = active.draftCatalog.configuration.firstOrNull { it.id == configId }
            ?: return AgentOperationResult.Failure("当前 Agent 未提供该草稿配置")
        if (!option.accepts(value)) {
            return AgentOperationResult.Failure("当前 Agent 不接受该草稿配置值")
        }
        synchronized(active.draftConfiguration) {
            active.draftConfiguration[configId] = value
        }
        if (option.category == com.kite.app.agent.contract.AgentConfigCategory.Model) {
            active.draftModelSelection = null
        }
        return AgentOperationResult.Success(
            AgentDraftPreferences(
                configuration = synchronized(active.draftConfiguration) {
                    active.draftConfiguration.toMap()
                },
                modeId = active.draftModeId
            )
        )
    }

    fun selectDraftMode(
        instanceId: String,
        generation: Long,
        modeId: String
    ): AgentOperationResult<AgentDraftPreferences> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (!active.session.isDraft) {
            return AgentOperationResult.Unsupported("session/draft-mode-active-session")
        }
        if (active.draftCatalog.modes.none { it.id == modeId }) {
            return AgentOperationResult.Failure("当前 Agent 未提供该工作模式")
        }
        active.draftModeId = modeId
        return AgentOperationResult.Success(
            AgentDraftPreferences(
                configuration = synchronized(active.draftConfiguration) {
                    active.draftConfiguration.toMap()
                },
                modeId = modeId
            )
        )
    }

    suspend fun prepareNewSession(
        instanceId: String,
        generation: Long,
        cwd: String? = null
    ): AgentOperationResult<AgentRuntimeSession> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        val nextCwd = cwd?.trim()?.takeIf(String::isNotBlank) ?: active.session.cwd
        return active.sessionOperationMutex.withLock {
            AgentOperationResult.Success(active.enterDraft(nextCwd))
        }
    }

    suspend fun listSessions(
        instanceId: String,
        generation: Long,
        cwd: String? = null
    ): AgentOperationResult<AgentSessionPage> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        val sessions = linkedMapOf<String, AgentSessionSummary>()
        val seenCursors = linkedSetOf<String>()
        var cursor: String? = null
        repeat(MAX_SESSION_LIST_PAGES) {
            when (val result = active.connection.listSessions(AgentSessionListRequest(cwd = cwd, cursor = cursor))) {
                is AgentOperationResult.Success -> {
                    result.value.sessions.forEach { session -> sessions[session.id] = session }
                    val nextCursor = result.value.nextCursor?.takeIf(String::isNotBlank)
                        ?: return AgentOperationResult.Success(AgentSessionPage(sessions.values.toList()))
                    if (!seenCursors.add(nextCursor)) {
                        return AgentOperationResult.Failure("Agent 会话列表分页游标重复")
                    }
                    cursor = nextCursor
                }
                is AgentOperationResult.Failure -> return result
                is AgentOperationResult.Unsupported -> return result
            }
        }
        return AgentOperationResult.Failure("Agent 会话列表分页过多")
    }

    suspend fun deleteSession(
        instanceId: String,
        generation: Long,
        sessionId: String
    ): AgentOperationResult<Unit> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (!active.session.capabilities.sessions.delete) {
            return AgentOperationResult.Unsupported("session/delete")
        }
        if (active.session.sessionId == sessionId) {
            return AgentOperationResult.Failure("当前会话正在使用，请先切换到其他会话")
        }
        return when (val deleted = active.connection.deleteSession(sessionId)) {
            is AgentOperationResult.Success -> {
                AgentConversationStore.remove(AgentConversationKey(active.session.providerId, sessionId))
                deleted
            }
            is AgentOperationResult.Failure -> deleted
            is AgentOperationResult.Unsupported -> deleted
        }
    }

    suspend fun loadSession(
        instanceId: String,
        generation: Long,
        sessionId: String,
        cwd: String? = null
    ): AgentOperationResult<AgentRuntimeSession> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        val nextCwd = cwd?.trim()?.takeIf(String::isNotBlank) ?: active.session.cwd
        return active.sessionOperationMutex.withLock {
            when (val loaded = restoreExistingSession(
                connection = active.connection,
                instanceId = active.session.instanceId,
                providerId = active.session.providerId,
                sessionId = sessionId,
                cwd = nextCwd,
                additionalDirectories = active.additionalDirectories
            )) {
                is AgentOperationResult.Success -> AgentOperationResult.Success(
                    active.activate(loaded.value, nextCwd)
                )
                is AgentOperationResult.Failure -> loaded
                is AgentOperationResult.Unsupported -> loaded
            }
        }
    }

    suspend fun forkSession(
        instanceId: String,
        generation: Long
    ): AgentOperationResult<AgentRuntimeSession> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        return active.sessionOperationMutex.withLock {
            val currentSessionId = active.session.sessionId
                ?: return@withLock AgentOperationResult.Unsupported("session/fork-draft")
            when (val forked = active.connection.forkSession(
                AgentExistingSessionRequest(
                    currentSessionId,
                    active.session.cwd,
                    active.additionalDirectories
                )
            )) {
                is AgentOperationResult.Success -> AgentOperationResult.Success(
                    active.activate(forked.value, active.session.cwd)
                )
                is AgentOperationResult.Failure -> forked
                is AgentOperationResult.Unsupported -> forked
            }
        }
    }

    suspend fun setConfiguration(
        instanceId: String,
        generation: Long,
        configId: String,
        value: AgentConfigValue
    ): AgentOperationResult<List<AgentConfigOption>> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (active.session.isDraft) {
            return AgentOperationResult.Unsupported("session/set_config_option-draft")
        }
        return active.applySessionConfiguration(configId, value)
    }

    suspend fun setMode(
        instanceId: String,
        generation: Long,
        modeId: String
    ): AgentOperationResult<Unit> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (active.session.isDraft) {
            return AgentOperationResult.Unsupported("session/set_mode-draft")
        }
        return active.applySessionMode(modeId)
    }

    suspend fun prompt(
        instanceId: String,
        generation: Long,
        content: List<AgentContent>
    ): AgentOperationResult<AgentTurnResult> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (content.isEmpty()) return AgentOperationResult.Failure("消息内容为空")
        return active.sessionOperationMutex.withLock {
            if (active.session.isDraft) {
                when (val created = active.connection.newSession(
                    AgentNewSessionRequest(active.session.cwd, active.additionalDirectories)
                )) {
                    is AgentOperationResult.Success -> active.activate(created.value, active.session.cwd)
                    is AgentOperationResult.Failure -> return@withLock created
                    is AgentOperationResult.Unsupported -> return@withLock created
                }
            }
            when (val configured = active.applyDraftModelSelection()) {
                is AgentOperationResult.Success -> Unit
                is AgentOperationResult.Failure -> return@withLock configured
                is AgentOperationResult.Unsupported -> return@withLock configured
            }
            when (val configured = active.applyDraftConfiguration()) {
                is AgentOperationResult.Success -> Unit
                is AgentOperationResult.Failure -> return@withLock configured
                is AgentOperationResult.Unsupported -> return@withLock configured
            }
            when (val configured = active.applyDraftMode()) {
                is AgentOperationResult.Success -> Unit
                is AgentOperationResult.Failure -> return@withLock configured
                is AgentOperationResult.Unsupported -> return@withLock configured
            }
            val sessionId = active.session.sessionId
                ?: return@withLock AgentOperationResult.Failure("Agent 会话创建后未返回会话 ID")
            val key = AgentConversationKey(active.session.providerId, sessionId)
            val localMessageId = "local-${System.currentTimeMillis()}"
            content.forEach { block ->
                AgentConversationStore.applyEvent(
                    key,
                    AgentSessionEvent.MessageChunk(
                        role = com.kite.app.agent.contract.AgentMessageRole.User,
                        content = block,
                        messageId = localMessageId
                    )
                )
            }
            val result = active.connection.prompt(AgentPromptRequest(sessionId, content))
            if (result !is AgentOperationResult.Success) {
                AgentConversationStore.discardLocalMessage(key, localMessageId)
            }
            result
        }
    }

    suspend fun cancel(instanceId: String, generation: Long): AgentOperationResult<Unit> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        val sessionId = active.session.sessionId
            ?: return AgentOperationResult.Failure("空白草稿没有正在生成的会话")
        return active.connection.cancel(sessionId)
    }

    fun resolvePermission(
        instanceId: String,
        generation: Long,
        outcome: AgentPermissionOutcome
    ): Boolean {
        val pending = permissionByInstance[instanceId]
            ?.takeIf { it.generation == generation }
            ?: return false
        return pending.deferred.complete(outcome)
    }

    suspend fun stop(instanceId: String, generation: Long): Boolean {
        permissionByInstance.remove(instanceId)
            ?.takeIf { it.generation == generation }
            ?.deferred
            ?.complete(AgentPermissionOutcome.Cancelled)
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return false
        if (!activeByInstance.remove(instanceId, active)) return false
        active.connection.disconnect()
        active.session.sessionId?.let { sessionId ->
            val key = AgentConversationKey(active.session.providerId, sessionId)
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Closed, "Agent 会话已关闭")
            )
        }
        active.statusSink.onStatus(active.session.sessionId, AgentSessionPhase.Closed, "Agent 会话已关闭")
        return true
    }

    internal suspend fun resetForTest() {
        permissionByInstance.values.forEach { it.deferred.complete(AgentPermissionOutcome.Cancelled) }
        permissionByInstance.clear()
        activeByInstance.values.forEach { it.connection.disconnect() }
        activeByInstance.clear()
    }

    private suspend fun ActiveRuntime.activate(snapshot: AgentSessionSnapshot, cwd: String): AgentRuntimeSession {
        val normalizedSnapshot = snapshot.copy(
            configuration = normalizeConfiguration(snapshot.configuration)
        )
        val next = AgentRuntimeSession(
            instanceId = session.instanceId,
            generation = session.generation,
            providerId = session.providerId,
            sessionId = normalizedSnapshot.id,
            cwd = cwd,
            snapshot = normalizedSnapshot,
            capabilities = connection.capabilities
        )
        session = next
        publishDraftCatalog(draftCatalog.withSnapshot(normalizedSnapshot))
        bindSnapshot(next, normalizedSnapshot)
        statusSink.onStatus(next.sessionId, AgentSessionPhase.Ready, "准备就绪")
        return next
    }

    private fun ActiveRuntime.enterDraft(cwd: String): AgentRuntimeSession {
        session.snapshot?.let { snapshot -> publishDraftCatalog(draftCatalog.withSnapshot(snapshot)) }
        val next = session.copy(sessionId = null, cwd = cwd, snapshot = null)
        session = next
        draftModelSelection = null
        synchronized(draftConfiguration) { draftConfiguration.clear() }
        draftModeId = null
        statusSink.onStatus(null, AgentSessionPhase.Ready, "可以开始新会话")
        return next
    }

    private suspend fun ActiveRuntime.applyDraftModelSelection(): AgentOperationResult<Unit> {
        val target = draftModelSelection ?: return AgentOperationResult.Success(Unit)
        val snapshot = session.snapshot
            ?: return AgentOperationResult.Failure("Agent 会话状态不可用")
        val mapped = resolveDraftModelSelection(target, snapshot.configuration)
        if (mapped == null) {
            if (target.usesAgentDefault) {
                draftModelSelection = null
                return AgentOperationResult.Success(Unit)
            }
            return AgentOperationResult.Failure("当前 Agent 未提供该新会话模型，请选择默认模型后重试")
        }
        val current = snapshot.configuration
            .filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.id == mapped.configId }
        if (current?.currentValue == mapped.value) {
            draftModelSelection = null
            return AgentOperationResult.Success(Unit)
        }
        return when (val result = connection.setConfiguration(
            snapshot.id,
            mapped.configId,
            AgentConfigValue.Select(mapped.value)
        )) {
            is AgentOperationResult.Success -> {
                val normalized = normalizeConfiguration(result.value)
                val key = AgentConversationKey(session.providerId, snapshot.id)
                AgentConversationStore.applyEvent(key, AgentSessionEvent.ConfigurationUpdated(normalized))
                session = session.copy(snapshot = snapshot.copy(configuration = normalized))
                publishDraftCatalog(draftCatalog.copy(configuration = normalized))
                draftModelSelection = null
                AgentOperationResult.Success(Unit)
            }
            is AgentOperationResult.Failure -> result
            is AgentOperationResult.Unsupported -> result
        }
    }

    private suspend fun ActiveRuntime.applyDraftConfiguration(): AgentOperationResult<Unit> {
        val pending = synchronized(draftConfiguration) { draftConfiguration.toList() }
        pending.forEach { (configId, value) ->
            if (session.snapshot?.configuration?.none { it.id == configId && it.accepts(value) } != false) {
                return AgentOperationResult.Failure("新会话未提供预选配置：$configId")
            }
            when (val result = applySessionConfiguration(configId, value)) {
                is AgentOperationResult.Success -> synchronized(draftConfiguration) {
                    draftConfiguration.remove(configId)
                }
                is AgentOperationResult.Failure -> return result
                is AgentOperationResult.Unsupported -> return result
            }
        }
        return AgentOperationResult.Success(Unit)
    }

    private suspend fun ActiveRuntime.applyDraftMode(): AgentOperationResult<Unit> {
        val target = draftModeId ?: return AgentOperationResult.Success(Unit)
        return when (val result = applySessionMode(target)) {
            is AgentOperationResult.Success -> {
                draftModeId = null
                result
            }
            is AgentOperationResult.Failure -> result
            is AgentOperationResult.Unsupported -> result
        }
    }

    private suspend fun ActiveRuntime.applySessionConfiguration(
        configId: String,
        value: AgentConfigValue
    ): AgentOperationResult<List<AgentConfigOption>> {
        val sessionId = session.sessionId
            ?: return AgentOperationResult.Unsupported("session/set_config_option-draft")
        val snapshot = session.snapshot
            ?: return AgentOperationResult.Failure("Agent 会话状态不可用")
        return when (val result = connection.setConfiguration(sessionId, configId, value)) {
            is AgentOperationResult.Success -> {
                val normalized = normalizeConfiguration(result.value)
                val key = AgentConversationKey(session.providerId, sessionId)
                AgentConversationStore.applyEvent(key, AgentSessionEvent.ConfigurationUpdated(normalized))
                session = session.copy(snapshot = snapshot.copy(configuration = normalized))
                publishDraftCatalog(draftCatalog.copy(configuration = normalized))
                AgentOperationResult.Success(normalized)
            }
            is AgentOperationResult.Failure -> result
            is AgentOperationResult.Unsupported -> result
        }
    }

    private suspend fun ActiveRuntime.applySessionMode(modeId: String): AgentOperationResult<Unit> {
        val sessionId = session.sessionId
            ?: return AgentOperationResult.Unsupported("session/set_mode-draft")
        val snapshot = session.snapshot
            ?: return AgentOperationResult.Failure("Agent 会话状态不可用")
        val mode = snapshot.modes.firstOrNull { it.id == modeId }
            ?: return AgentOperationResult.Failure("新会话未提供预选工作模式")
        return when (val result = connection.setMode(sessionId, mode.id)) {
            is AgentOperationResult.Success -> {
                val key = AgentConversationKey(session.providerId, sessionId)
                AgentConversationStore.applyEvent(key, AgentSessionEvent.CurrentModeChanged(mode.id))
                session = session.copy(snapshot = snapshot.copy(currentModeId = mode.id))
                publishDraftCatalog(draftCatalog.copy(currentModeId = mode.id))
                result
            }
            is AgentOperationResult.Failure -> result
            is AgentOperationResult.Unsupported -> result
        }
    }

    private fun AgentConfigOption.accepts(value: AgentConfigValue): Boolean = when {
        this is AgentConfigOption.Select && value is AgentConfigValue.Select ->
            choices.any { it.value == value.value }
        this is AgentConfigOption.Toggle && value is AgentConfigValue.Toggle -> true
        else -> false
    }

    private fun AgentDraftCapabilityCatalog.withSnapshot(snapshot: AgentSessionSnapshot): AgentDraftCapabilityCatalog =
        copy(
            configuration = snapshot.configuration,
            modes = snapshot.modes,
            currentModeId = snapshot.currentModeId
        )

    private fun ActiveRuntime.publishDraftCatalog(next: AgentDraftCapabilityCatalog) =
        updateDraftCatalog { next }

    private fun ActiveRuntime.updateDraftCatalog(
        transform: (AgentDraftCapabilityCatalog) -> AgentDraftCapabilityCatalog
    ) {
        val (previous, next) = synchronized(draftCatalogLock) {
            val before = draftCatalog
            before to transform(before).also { draftCatalog = it }
        }
        if (next != previous) onDraftCatalogChanged(next)
    }

    private fun bindSnapshot(session: AgentRuntimeSession, snapshot: AgentSessionSnapshot) {
        val key = AgentConversationKey(session.providerId, snapshot.id)
        AgentConversationStore.bind(session.instanceId, key, AgentSessionPhase.Ready)
        if (snapshot.configuration.isNotEmpty()) {
            AgentConversationStore.applyEvent(key, AgentSessionEvent.ConfigurationUpdated(snapshot.configuration))
        }
        snapshot.currentModeId?.let { modeId ->
            AgentConversationStore.applyEvent(key, AgentSessionEvent.CurrentModeChanged(modeId))
        }
    }

    private suspend fun restoreExistingSession(
        connection: KiteAgentConnection,
        instanceId: String,
        providerId: String,
        sessionId: String,
        cwd: String,
        additionalDirectories: List<String>
    ): AgentOperationResult<AgentSessionSnapshot> {
        val key = AgentConversationKey(providerId, sessionId)
        val hasProjection = AgentConversationStore.snapshot(key)?.history?.totalItems?.let { it > 0 } == true
        val request = AgentExistingSessionRequest(sessionId, cwd, additionalDirectories)
        if (hasProjection && connection.capabilities.sessions.resume) {
            return connection.resumeSession(request)
        }
        if (connection.capabilities.sessions.load) {
            AgentConversationStore.beginHistoryReplay(instanceId, key)
            return when (val loaded = connection.loadSession(request)) {
                is AgentOperationResult.Success -> {
                    AgentConversationStore.completeHistoryReplay(key)
                    loaded
                }
                is AgentOperationResult.Failure -> {
                    AgentConversationStore.abortHistoryReplay(key)
                    loaded
                }
                is AgentOperationResult.Unsupported -> {
                    AgentConversationStore.abortHistoryReplay(key)
                    loaded
                }
            }
        }
        if (connection.capabilities.sessions.resume) {
            AgentConversationStore.bind(instanceId, key, AgentSessionPhase.Preparing)
            AgentConversationStore.markHistoryUnavailable(key)
            return connection.resumeSession(request)
        }
        return AgentOperationResult.Unsupported("session/resume-or-load")
    }

}
