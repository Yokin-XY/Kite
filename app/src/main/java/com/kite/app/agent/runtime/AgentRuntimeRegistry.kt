package com.kite.app.agent.runtime

import com.kite.app.agent.contract.AgentClientCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentDraftConfigurationPreview
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.contract.AgentSessionSnapshot
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import com.kite.app.agent.config.AgentSessionModelSelection
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.sdk.configuration.AgentProviderPreparationResult
import com.kite.app.agent.sdk.skill.AgentPromptDraft
import com.kite.app.agent.sdk.skill.AgentSkillPromptComposer
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
    val normalizeModes: (List<AgentMode>) -> List<AgentMode> = { it },
    val resolveDraftModelSelection: (
        target: AgentDraftModelSelection,
        options: List<AgentConfigOption>
    ) -> AgentSessionModelSelection? = { _, _ -> null },
    val prepareDraftModelSelection: suspend (
        target: AgentDraftModelSelection
    ) -> AgentProviderPreparationResult = {
        AgentProviderPreparationResult.Ready()
    },
    val initialDraftCatalog: AgentDraftCapabilityCatalog = AgentDraftCapabilityCatalog(),
    val initialDraftModeId: String? = null,
    val initialDraftPreferences: AgentDraftPersistenceSnapshot = AgentDraftPersistenceSnapshot(),
    val loadSessionDraftPreferences: (String) -> AgentDraftPersistenceSnapshot? = { null },
    val onDraftPreferencesChanged: (
        sessionId: String?,
        preferences: AgentDraftPersistenceSnapshot,
        updateAgentDefault: Boolean,
    ) -> Unit = { _, _, _ -> },
    val composeSkillPrompt: (AgentPromptDraft) -> List<AgentContent> = AgentSkillPromptComposer::compose,
    val onDraftCatalogChanged: (AgentDraftCapabilityCatalog) -> Unit = {},
    val onDraftModeSelected: (String) -> Unit = {},
)

/** 当前输入草稿的瞬时模型目标；点击选择时不写 Agent，发送本轮消息前才应用。 */
data class AgentDraftModelSelection(
    val providerId: String,
    val modelId: String,
    val usesAgentDefault: Boolean
)

/**
 * 当前 Agent 已公布并由 Adapter 映射的输入草稿能力目录。
 *
 * 空白会话和已有会话的下一轮输入共用这一目录；不包含消息、会话 ID、密钥或持久默认值。
 */
data class AgentDraftCapabilityCatalog(
    val configuration: List<AgentConfigOption> = emptyList(),
    /** 已由当前模型明确解析的类别；即使没有选项，也禁止回退到旧缓存。 */
    val resolvedConfigurationCategories: Set<AgentConfigCategory> = emptySet(),
    val modes: List<com.kite.app.agent.contract.AgentMode> = emptyList(),
    val currentModeId: String? = null,
    val commands: List<AgentCommand> = emptyList()
)

data class AgentDraftPreferences(
    val configuration: Map<String, AgentConfigValue> = emptyMap(),
    val modeId: String? = null
)

data class AgentDraftConfigurationSelection(
    val configId: String,
    val value: AgentConfigValue,
)

data class AgentDraftPersistenceSnapshot(
    val modelSelection: AgentDraftModelSelection? = null,
    val permissionSelection: AgentDraftConfigurationSelection? = null,
)

enum class AgentRuntimeSessionState {
    ColdDraft,
    WarmDraft,
    Active,
}

data class AgentRuntimeSession(
    val instanceId: String,
    val generation: Long,
    val providerId: String,
    val sessionId: String?,
    val cwd: String,
    val snapshot: AgentSessionSnapshot?,
    val capabilities: AgentCapabilities,
    val state: AgentRuntimeSessionState = if (sessionId == null) {
        AgentRuntimeSessionState.ColdDraft
    } else {
        AgentRuntimeSessionState.Active
    },
) {
    val isDraft: Boolean get() = state != AgentRuntimeSessionState.Active
    val hasNativeSession: Boolean get() = sessionId != null
}

fun interface AgentRuntimeStatusSink {
    fun onStatus(sessionId: String?, phase: AgentSessionPhase, message: String?)
}

internal data class AgentRuntimeStatusRoute(
    val shouldPublish: Boolean,
    val sessionId: String?,
)

/**
 * 原生连接可以在断开后继续吐出少量缓冲事件。只有当前运行时拥有的会话才能改写 CardRun 的
 * 可见会话身份；准备新会话期间的中间事件只写入会话 Store，最终身份由 activate() 发布。
 */
internal object AgentRuntimeStatusRoutingPolicy {
    fun route(
        eventSessionId: String,
        activeSessionId: String?,
        activeIsDraft: Boolean,
        hasActiveRuntime: Boolean,
        preparingWarmDraft: Boolean,
        preferredSessionId: String?,
    ): AgentRuntimeStatusRoute = when {
        hasActiveRuntime && preparingWarmDraft -> AgentRuntimeStatusRoute(false, null)
        hasActiveRuntime && activeIsDraft -> AgentRuntimeStatusRoute(false, null)
        hasActiveRuntime && activeSessionId != eventSessionId -> AgentRuntimeStatusRoute(false, null)
        hasActiveRuntime -> AgentRuntimeStatusRoute(true, eventSessionId)
        preferredSessionId != null && preferredSessionId != eventSessionId ->
            AgentRuntimeStatusRoute(false, null)
        preferredSessionId != null -> AgentRuntimeStatusRoute(true, eventSessionId)
        else -> AgentRuntimeStatusRoute(true, null)
    }
}

/**
 * Agent 长连接与待决权限的进程级拥有者。
 *
 * CardRunStore 仍拥有运行实例事实；这里仅持有不可持久化的 connection 对象，并把协议事件投影到
 * AgentConversationStore。页面只能提交 prompt/cancel/permission 等意图，不能直接持有 SDK 连接。
 */
object AgentRuntimeRegistry {
    private const val MAX_SESSION_LIST_PAGES = 100
    private const val MAX_COMPOSER_DRAFTS = 64
    private const val DRAFT_CONVERSATION_PREFIX = "kite-draft:"
    private const val COLD_COMPOSER_DRAFT_KEY = "kite-cold-draft"

    private data class FailedLocalMessage(
        val key: AgentConversationKey,
        val messageId: String,
        val draft: AgentPromptDraft,
    )

    private class ActiveRuntime(
        @Volatile var session: AgentRuntimeSession,
        val defaultCwd: String,
        val additionalDirectories: List<String>,
        @Volatile var connection: KiteAgentConnection,
        val provider: KiteAgentProvider,
        val connectionRequest: AgentConnectionRequest,
        val endpoint: AgentClientEndpoint,
        val statusSink: AgentRuntimeStatusSink,
        val normalizeConfiguration: (List<AgentConfigOption>) -> List<AgentConfigOption>,
        val normalizeModes: (List<AgentMode>) -> List<AgentMode>,
        val resolveDraftModelSelection: (
            target: AgentDraftModelSelection,
            options: List<AgentConfigOption>
        ) -> AgentSessionModelSelection?,
        val prepareDraftModelSelection: suspend (
            target: AgentDraftModelSelection
        ) -> AgentProviderPreparationResult,
        val composeSkillPrompt: (AgentPromptDraft) -> List<AgentContent>,
        val sessionOperationMutex: Mutex = Mutex(),
        @Volatile var draftModelSelection: AgentDraftModelSelection? = null,
        @Volatile var defaultDraftModelSelection: AgentDraftModelSelection? = null,
        @Volatile var draftCatalog: AgentDraftCapabilityCatalog = AgentDraftCapabilityCatalog(),
        val draftCatalogLock: Any = Any(),
        val draftConfiguration: LinkedHashMap<String, AgentConfigValue> = linkedMapOf(),
        val composerDrafts: LinkedHashMap<String, AgentPromptDraft> = linkedMapOf(),
        val composerDraftLock: Any = Any(),
        val failedLocalMessages: LinkedHashMap<String, FailedLocalMessage> = linkedMapOf(),
        @Volatile var defaultDraftPermissionSelection: AgentDraftConfigurationSelection? = null,
        @Volatile var draftModeId: String? = null,
        @Volatile var defaultDraftModeId: String? = null,
        @Volatile var preparingWarmDraft: Boolean = false,
        @Volatile var pendingProviderConfigurationEffect: AgentSessionConfigurationEffect? = null,
        val onDraftCatalogChanged: (AgentDraftCapabilityCatalog) -> Unit = {},
        val onDraftModeSelected: (String) -> Unit = {},
        val loadSessionDraftPreferences: (String) -> AgentDraftPersistenceSnapshot? = { null },
        val onDraftPreferencesChanged: (String?, AgentDraftPersistenceSnapshot, Boolean) -> Unit = { _, _, _ -> },
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

        val preferredSessionId = request.preferredSessionId?.trim()?.takeIf(String::isNotBlank)
        val restoredPreferences = preferredSessionId
            ?.let { sessionId -> runCatching { request.loadSessionDraftPreferences(sessionId) }.getOrNull() }
        val startupPreferences = restoredPreferences ?: request.initialDraftPreferences
        startupPreferences.modelSelection?.let { selection ->
            when (val prepared = request.prepareDraftModelSelection(selection)) {
                is AgentProviderPreparationResult.Failed ->
                    return AgentOperationResult.Failure(prepared.message)
                is AgentProviderPreparationResult.Ready -> Unit
            }
        }
        var observedCatalog = request.initialDraftCatalog.copy(
            modes = request.normalizeModes(request.initialDraftCatalog.modes).distinctBy(AgentMode::id),
        )
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
                        current.copy(configuration = mergeDraftConfigurationCatalog(
                            current.configuration,
                            normalizedEvent.options,
                        ))
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
                    val active = activeByInstance[request.instanceId]
                        ?.takeIf { it.session.generation == request.generation }
                    val route = AgentRuntimeStatusRoutingPolicy.route(
                        eventSessionId = sessionId,
                        activeSessionId = active?.session?.sessionId,
                        activeIsDraft = active?.session?.isDraft == true,
                        hasActiveRuntime = active != null,
                        preparingWarmDraft = active?.preparingWarmDraft == true,
                        preferredSessionId = preferredSessionId,
                    )
                    if (route.shouldPublish) {
                        statusSink.onStatus(route.sessionId, normalizedEvent.phase, normalizedEvent.message)
                    }
                }
            },
            permissionHandler = permissionHandler@ { permission ->
                val permissionRoute = activeByInstance[request.instanceId]
                    ?.takeIf { it.session.generation == request.generation }
                    .let { active ->
                        AgentRuntimeStatusRoutingPolicy.route(
                            eventSessionId = permission.sessionId,
                            activeSessionId = active?.session?.sessionId,
                            activeIsDraft = active?.session?.isDraft == true,
                            hasActiveRuntime = active != null,
                            preparingWarmDraft = active?.preparingWarmDraft == true,
                            preferredSessionId = preferredSessionId,
                        )
                }
                if (!permissionRoute.shouldPublish) {
                    return@permissionHandler AgentPermissionOutcome.Cancelled
                }
                val key = AgentConversationKey(request.providerId, permission.sessionId)
                if (AgentConversationStore.snapshot(key) == null) {
                    AgentConversationStore.bind(request.instanceId, key, AgentSessionPhase.WaitingPermission)
                }
                AgentConversationStore.requestPermission(key, permission)
                statusSink.onStatus(
                    permissionRoute.sessionId,
                    AgentSessionPhase.WaitingPermission,
                    "等待权限选择",
                )
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
                        val active = activeByInstance[request.instanceId]
                            ?.takeIf { it.session.generation == request.generation }
                        val restoredRoute = AgentRuntimeStatusRoutingPolicy.route(
                            eventSessionId = permission.sessionId,
                            activeSessionId = active?.session?.sessionId,
                            activeIsDraft = active?.session?.isDraft == true,
                            hasActiveRuntime = active != null,
                            preparingWarmDraft = active?.preparingWarmDraft == true,
                            preferredSessionId = preferredSessionId,
                        )
                        if (restoredRoute.shouldPublish) {
                            statusSink.onStatus(
                                restoredRoute.sessionId,
                                restored?.phase ?: AgentSessionPhase.Ready,
                                "权限请求已处理",
                            )
                        }
                    }
                }
            }
        )
        val connectionRequest = AgentConnectionRequest(
            client = AgentClientInfo(name = "kite", version = "1", title = "Kite"),
            capabilities = AgentClientCapabilities(),
        )
        val connected = provider.connect(connectionRequest, endpoint)
        val connection = when (connected) {
            is AgentOperationResult.Success -> connected.value
            is AgentOperationResult.Failure -> return connected
            is AgentOperationResult.Unsupported -> return connected
        }
        val additionalDirectories = request.additionalDirectories
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .takeIf { connection.capabilities.sessions.additionalDirectories }
            .orEmpty()
        val session = if (preferredSessionId == null) {
            // 打开 Agent 只建立连接并消费 Kite 缓存；用户首次发送前不得创建原生会话。
            AgentRuntimeSession(
                instanceId = request.instanceId,
                generation = request.generation,
                providerId = request.providerId,
                sessionId = null,
                cwd = request.cwd,
                snapshot = null,
                capabilities = connection.capabilities,
                state = AgentRuntimeSessionState.ColdDraft,
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
                    configuration = mergeDraftConfigurationCatalog(
                        request.initialDraftCatalog.configuration,
                        request.normalizeConfiguration(opened.value.configuration),
                    ),
                    modes = request.normalizeModes(opened.value.modes)
                        .ifEmpty { observedCatalog.modes }
                        .distinctBy(AgentMode::id),
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
                capabilities = connection.capabilities,
                state = AgentRuntimeSessionState.Active,
            ).also {
                updateObservedCatalog { current ->
                    current.withSnapshot(openedSnapshot, request.normalizeModes)
                }
                bindSnapshot(it, openedSnapshot)
            }
        }
        val defaultPermission = observedCatalog.acceptedPermissionSelection(
            request.initialDraftPreferences.permissionSelection,
        )
        val restoredPermission = observedCatalog.acceptedPermissionSelection(
            restoredPreferences?.permissionSelection,
        ) ?: defaultPermission
        val runtime = ActiveRuntime(
            session = session,
            defaultCwd = request.cwd,
            additionalDirectories = additionalDirectories,
            connection = connection,
            provider = provider,
            connectionRequest = connectionRequest,
            endpoint = endpoint,
            statusSink = statusSink,
            normalizeConfiguration = request.normalizeConfiguration,
            normalizeModes = request.normalizeModes,
            resolveDraftModelSelection = request.resolveDraftModelSelection,
            prepareDraftModelSelection = request.prepareDraftModelSelection,
            composeSkillPrompt = request.composeSkillPrompt,
            draftModelSelection = startupPreferences.modelSelection,
            defaultDraftModelSelection = request.initialDraftPreferences.modelSelection,
            draftCatalog = observedCatalog,
            draftConfiguration = linkedMapOf<String, AgentConfigValue>().apply {
                restoredPermission?.let { put(it.configId, it.value) }
            },
            defaultDraftPermissionSelection = defaultPermission,
            draftModeId = request.initialDraftModeId
                ?.takeIf { id -> session.isDraft && observedCatalog.modes.any { it.id == id } },
            defaultDraftModeId = request.initialDraftModeId
                ?.takeIf { id -> observedCatalog.modes.any { it.id == id } },
            onDraftCatalogChanged = request.onDraftCatalogChanged,
            onDraftModeSelected = request.onDraftModeSelected,
            loadSessionDraftPreferences = request.loadSessionDraftPreferences,
            onDraftPreferencesChanged = request.onDraftPreferencesChanged,
        )
        val previous = synchronized(catalogLock) {
            runtime.draftCatalog = observedCatalog
            activeByInstance.putIfAbsent(request.instanceId, runtime)
        }
        if (previous != null) {
            connection.disconnect()
            return AgentOperationResult.Failure("Agent 运行实例连接发生冲突")
        }
        if (session.isDraft) {
            AgentConversationStore.bind(
                request.instanceId,
                AgentConversationKey(request.providerId, draftConversationId(request.instanceId, request.generation)),
                AgentSessionPhase.Ready,
            )
        }
        if (session.sessionId != null) runtime.publishDraftPreferences(updateAgentDefault = false)
        statusSink.onStatus(
            session.sessionId.takeUnless { session.isDraft },
            AgentSessionPhase.Ready,
            if (session.isDraft) "可以开始新会话" else "准备就绪"
        )
        return AgentOperationResult.Success(session)
    }

    fun session(instanceId: String): AgentRuntimeSession? = activeByInstance[instanceId]?.session

    fun conversationProjectionSessionId(instanceId: String, generation: Long): String? =
        activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?.session
            ?.let { session ->
                session.sessionId ?: draftConversationId(instanceId, generation)
            }

    fun defaultCwd(instanceId: String, generation: Long): String? = activeByInstance[instanceId]
        ?.takeIf { it.session.generation == generation }
        ?.defaultCwd

    fun draftModelSelection(instanceId: String, generation: Long): AgentDraftModelSelection? =
        activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?.draftModelSelection

    fun draftCapabilityCatalog(instanceId: String, generation: Long): AgentDraftCapabilityCatalog? =
        activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?.draftCatalog

    fun draftPreferences(instanceId: String, generation: Long): AgentDraftPreferences? =
        activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
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
        active.draftModelSelection = selection
        active.defaultDraftModelSelection = selection
        active.draftCatalog.configuration
            .filter { it.category == AgentConfigCategory.Model }
            .forEach { option -> synchronized(active.draftConfiguration) { active.draftConfiguration.remove(option.id) } }
        active.connection.previewDraftModelConfiguration(selection.providerId, selection.modelId)
            ?.let { preview -> active.applyDraftConfigurationPreview(preview) }
        active.publishDraftPreferences(updateAgentDefault = true)
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
        val option = active.draftCatalog.configuration.firstOrNull { it.id == configId }
            ?: return AgentOperationResult.Failure("当前 Agent 未提供该输入配置")
        if (!option.accepts(value)) {
            return AgentOperationResult.Failure("当前 Agent 不接受该输入配置值")
        }
        synchronized(active.draftConfiguration) {
            active.draftConfiguration[configId] = value
        }
        if (option.category == AgentConfigCategory.Permission) {
            active.defaultDraftPermissionSelection = AgentDraftConfigurationSelection(configId, value)
        }
        if (option.category == AgentConfigCategory.Model) {
            active.draftModelSelection = null
            val choice = (option as? AgentConfigOption.Select)
                ?.choices
                ?.firstOrNull { candidate -> candidate.value == (value as? AgentConfigValue.Select)?.value }
            val providerId = choice?.groupId
            val modelId = providerId?.let { groupId ->
                choice.value.removePrefix("$groupId/")
            }

            if (providerId != null && modelId != null) {
                active.connection.previewDraftModelConfiguration(providerId, modelId)
                    ?.let { preview -> active.applyDraftConfigurationPreview(preview) }
            }
        }
        if (option.category == AgentConfigCategory.Permission) {
            active.publishDraftPreferences(updateAgentDefault = true)
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

    /** 页面重建或切换会话时使用的统一输入草稿；只保存在当前 Runtime 生命周期内。 */
    fun composerDraft(
        instanceId: String,
        generation: Long,
        sessionId: String?,
    ): AgentPromptDraft? = activeByInstance[instanceId]
        ?.takeIf { it.session.generation == generation }
        ?.let { active ->
            synchronized(active.composerDraftLock) {
                active.composerDrafts[composerDraftKey(sessionId)]?.immutableCopy()
            }
        }

    fun updateComposerDraft(
        instanceId: String,
        generation: Long,
        sessionId: String?,
        draft: AgentPromptDraft,
    ): AgentOperationResult<Unit> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        active.storeComposerDraft(sessionId, draft)
        return AgentOperationResult.Success(Unit)
    }

    fun clearComposerDraft(
        instanceId: String,
        generation: Long,
        sessionId: String?,
    ): AgentOperationResult<Unit> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        synchronized(active.composerDraftLock) {
            active.composerDrafts.remove(composerDraftKey(sessionId))
        }
        return AgentOperationResult.Success(Unit)
    }

    fun selectDraftMode(
        instanceId: String,
        generation: Long,
        modeId: String
    ): AgentOperationResult<AgentDraftPreferences> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (active.draftCatalog.modes.none { it.id == modeId }) {
            return AgentOperationResult.Failure("当前 Agent 未提供该工作模式")
        }
        active.draftModeId = modeId
        active.defaultDraftModeId = modeId
        active.onDraftModeSelected(modeId)
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

    suspend fun renameSession(
        instanceId: String,
        generation: Long,
        request: AgentSessionRenameRequest,
    ): AgentOperationResult<Unit> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (!active.session.capabilities.sessions.rename) {
            return AgentOperationResult.Unsupported("session/rename")
        }
        val title = request.title.trim()
        if (request.sessionId.isBlank() || title.isBlank()) {
            return AgentOperationResult.Failure("会话 ID 和名称不能为空")
        }
        return active.connection.renameSession(request.copy(title = title))
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
            active.loadPreparedSession(sessionId, nextCwd)
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
                    active.activate(forked.value, active.session.cwd, preserveDraftPreferences = false)
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
    ): AgentOperationResult<AgentTurnResult> = prompt(
        instanceId,
        generation,
        AgentPromptDraft(content),
    )

    suspend fun prompt(
        instanceId: String,
        generation: Long,
        draft: AgentPromptDraft,
    ): AgentOperationResult<AgentTurnResult> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        if (draft.content.isEmpty() && draft.skills.isEmpty()) {
            return AgentOperationResult.Failure("消息内容为空")
        }
        val visibleContent = draft.visibleContent
        val transportContent = active.composeSkillPrompt(draft)
        if (transportContent.isEmpty() || transportContent.any { it is AgentContent.SkillReference }) {
            return AgentOperationResult.Failure("Skill 草稿未能转换为 Agent 输入")
        }
        val sourceComposerSessionId = active.session.sessionId
        active.storeComposerDraft(sourceComposerSessionId, draft)
        return active.sessionOperationMutex.withLock {
            val retry = active.takeFailedLocalMessage(sourceComposerSessionId, draft)
            val localMessageId = retry?.messageId ?: "local-${System.currentTimeMillis()}"
            val optimisticKey = retry?.key ?: AgentConversationKey(
                active.session.providerId,
                active.session.sessionId ?: draftConversationId(instanceId, generation),
            )
            AgentConversationStore.bind(active.session.instanceId, optimisticKey, AgentSessionPhase.Ready)
            val reusedMessage = retry != null && AgentConversationStore.retryLocalTurn(
                optimisticKey,
                localMessageId,
            )
            if (!reusedMessage) {
                visibleContent.forEach { block ->
                    AgentConversationStore.applyEvent(
                        optimisticKey,
                        AgentSessionEvent.MessageChunk(
                            role = com.kite.app.agent.contract.AgentMessageRole.User,
                            content = block,
                            messageId = localMessageId
                        )
                    )
                }
            }
            AgentConversationStore.applyEvent(
                optimisticKey,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Preparing),
            )
            val sessionId = when (val prepared = active.prepareDraftRequest()) {
                is AgentOperationResult.Success -> prepared.value
                is AgentOperationResult.Failure -> {
                    val failureKey = active.session.sessionId?.let { nativeSessionId ->
                        AgentConversationKey(active.session.providerId, nativeSessionId)
                    } ?: optimisticKey
                    if (failureKey != optimisticKey) {
                        AgentConversationStore.rekey(
                            active.session.instanceId,
                            optimisticKey,
                            failureKey,
                            AgentSessionPhase.Ready,
                        )
                    }
                    AgentConversationStore.failLocalTurn(failureKey, localMessageId, prepared.message)
                    active.rememberFailedLocalMessage(
                        active.session.sessionId,
                        FailedLocalMessage(failureKey, localMessageId, draft.immutableCopy()),
                    )
                    return@withLock prepared
                }
                is AgentOperationResult.Unsupported -> {
                    val failureKey = active.session.sessionId?.let { nativeSessionId ->
                        AgentConversationKey(active.session.providerId, nativeSessionId)
                    } ?: optimisticKey
                    if (failureKey != optimisticKey) {
                        AgentConversationStore.rekey(
                            active.session.instanceId,
                            optimisticKey,
                            failureKey,
                            AgentSessionPhase.Ready,
                        )
                    }
                    AgentConversationStore.failLocalTurn(
                        failureKey,
                        localMessageId,
                        "Agent 不支持：${prepared.operation}",
                    )
                    active.rememberFailedLocalMessage(
                        active.session.sessionId,
                        FailedLocalMessage(failureKey, localMessageId, draft.immutableCopy()),
                    )
                    return@withLock prepared
                }
            }
            val key = AgentConversationKey(active.session.providerId, sessionId)
            if (key != optimisticKey) {
                AgentConversationStore.rekey(
                    instanceId = active.session.instanceId,
                    fromKey = optimisticKey,
                    toKey = key,
                    phase = AgentSessionPhase.Ready,
                )
            }
            active.promoteWarmDraft()
            val result = active.connection.prompt(
                AgentPromptRequest(sessionId, transportContent, messageId = localMessageId),
            )
            if (result !is AgentOperationResult.Success) {
                val message = when (result) {
                    is AgentOperationResult.Failure -> result.message
                    is AgentOperationResult.Unsupported -> "Agent 不支持：${result.operation}"
                    is AgentOperationResult.Success -> error("unreachable")
                }
                AgentConversationStore.failLocalTurn(key, localMessageId, message)
                active.rememberFailedLocalMessage(
                    active.session.sessionId,
                    FailedLocalMessage(key, localMessageId, draft.immutableCopy()),
                )
            } else {
                synchronized(active.composerDraftLock) {
                    active.composerDrafts.remove(composerDraftKey(sourceComposerSessionId))
                }
            }
            result
        }
    }

    /**
     * 当前原生回合生成期间立即补充输入。这里刻意不获取 [ActiveRuntime.sessionOperationMutex]：
     * 插话必须在首轮 prompt 仍挂起时抵达 Agent，不能退化为 Kite 本地排队。
     */
    suspend fun steer(
        instanceId: String,
        generation: Long,
        draft: AgentPromptDraft,
    ): AgentOperationResult<Unit> {
        val active = activeByInstance[instanceId]
            ?.takeIf { it.session.generation == generation }
            ?: return AgentOperationResult.Failure("Agent 会话尚未连接")
        val sessionId = active.session.sessionId
            ?.takeIf { !active.session.isDraft }
            ?: return AgentOperationResult.Failure("Agent 原生会话尚未准备好插话")
        if (draft.content.isEmpty() && draft.skills.isEmpty()) {
            return AgentOperationResult.Failure("消息内容为空")
        }
        val visibleContent = draft.visibleContent
        val transportContent = active.composeSkillPrompt(draft)
        if (transportContent.isEmpty() || transportContent.any { it is AgentContent.SkillReference }) {
            return AgentOperationResult.Failure("Skill 草稿未能转换为 Agent 输入")
        }
        val key = AgentConversationKey(active.session.providerId, sessionId)
        val phase = AgentConversationStore.snapshot(key)?.phase
        if (phase != AgentSessionPhase.Prompting) {
            return AgentOperationResult.Failure("Agent 当前没有正在生成的回复")
        }
        val localMessageId = "local-${System.currentTimeMillis()}"
        visibleContent.forEach { block ->
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    role = com.kite.app.agent.contract.AgentMessageRole.User,
                    content = block,
                    messageId = localMessageId,
                ),
            )
        }
        val result = active.connection.steer(
            AgentPromptRequest(sessionId, transportContent, messageId = localMessageId),
        )
        if (result !is AgentOperationResult.Success) {
            AgentConversationStore.discardLocalMessage(key, localMessageId)
        } else {
            synchronized(active.composerDraftLock) {
                active.composerDrafts.remove(composerDraftKey(sessionId))
            }
        }
        return result
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
        active.statusSink.onStatus(
            active.session.sessionId.takeUnless { active.session.isDraft },
            AgentSessionPhase.Closed,
            "Agent 会话已关闭",
        )
        return true
    }

    internal suspend fun resetForTest() {
        permissionByInstance.values.forEach { it.deferred.complete(AgentPermissionOutcome.Cancelled) }
        permissionByInstance.clear()
        activeByInstance.values.forEach { it.connection.disconnect() }
        activeByInstance.clear()
    }

    private suspend fun ActiveRuntime.activate(
        snapshot: AgentSessionSnapshot,
        cwd: String,
        preserveDraftPreferences: Boolean,
        state: AgentRuntimeSessionState = AgentRuntimeSessionState.Active,
    ): AgentRuntimeSession {
        val normalizedSnapshot = snapshot.copy(
            configuration = mergeDraftConfigurationCatalog(
                draftCatalog.configuration,
                normalizeConfiguration(snapshot.configuration),
            ),
            modes = normalizeModes(snapshot.modes).ifEmpty { draftCatalog.modes }.distinctBy(AgentMode::id),
        )
        val next = AgentRuntimeSession(
            instanceId = session.instanceId,
            generation = session.generation,
            providerId = session.providerId,
            sessionId = normalizedSnapshot.id,
            cwd = cwd,
            snapshot = normalizedSnapshot,
            capabilities = connection.capabilities,
            state = state,
        )
        session = next
        publishDraftCatalog(draftCatalog.withSnapshot(normalizedSnapshot, normalizeModes))
        if (!preserveDraftPreferences) {
            restoreDraftPreferences(normalizedSnapshot.id)
            draftModeId = defaultDraftModeId?.takeIf { id -> draftCatalog.modes.any { it.id == id } }
        }
        sanitizeDraftPermissionSelection()
        bindSnapshot(next, normalizedSnapshot)
        publishDraftPreferences(updateAgentDefault = false)
        statusSink.onStatus(
            next.sessionId.takeUnless { next.isDraft },
            AgentSessionPhase.Ready,
            if (next.isDraft) "可以开始新会话" else "准备就绪",
        )
        return next
    }

    private fun ActiveRuntime.enterDraft(cwd: String): AgentRuntimeSession {
        session.snapshot?.let { snapshot ->
            publishDraftCatalog(draftCatalog.withSnapshot(snapshot, normalizeModes))
        }
        val next = session.copy(
            sessionId = null,
            cwd = cwd,
            snapshot = null,
            state = AgentRuntimeSessionState.ColdDraft,
        )
        session = next
        val draftKey = AgentConversationKey(
            next.providerId,
            draftConversationId(next.instanceId, next.generation),
        )
        AgentConversationStore.remove(draftKey)
        AgentConversationStore.bind(next.instanceId, draftKey, AgentSessionPhase.Ready)
        restoreDraftPreferences(sessionId = null)
        draftModeId = defaultDraftModeId?.takeIf { id -> draftCatalog.modes.any { it.id == id } }
        statusSink.onStatus(null, AgentSessionPhase.Ready, "可以开始新会话")
        return next
    }

    private suspend fun ActiveRuntime.createWarmDraft(
        cwd: String,
        preserveDraftPreferences: Boolean,
    ): AgentOperationResult<AgentRuntimeSession> {
        preparingWarmDraft = true
        val created = try {
            connection.newSession(AgentNewSessionRequest(cwd, additionalDirectories))
        } finally {
            preparingWarmDraft = false
        }
        return when (created) {
            is AgentOperationResult.Success -> AgentOperationResult.Success(
                activate(
                    created.value,
                    cwd,
                    preserveDraftPreferences = preserveDraftPreferences,
                    state = AgentRuntimeSessionState.WarmDraft,
                )
            )
            is AgentOperationResult.Failure -> created
            is AgentOperationResult.Unsupported -> created
        }
    }

    private fun ActiveRuntime.promoteWarmDraft() {
        if (session.state != AgentRuntimeSessionState.WarmDraft) return
        session = session.copy(state = AgentRuntimeSessionState.Active)
        statusSink.onStatus(session.sessionId, AgentSessionPhase.Ready, "准备就绪")
    }

    /** 模型、权限、推理强度、工作模式和 Skill 草稿统一在消息发送前应用。 */
    private suspend fun ActiveRuntime.prepareDraftRequest(): AgentOperationResult<String> {
        when (val prepared = prepareDraftProvider()) {
            is AgentOperationResult.Success -> Unit
            is AgentOperationResult.Failure -> return prepared
            is AgentOperationResult.Unsupported -> return prepared
        }
        if (!session.hasNativeSession) {
            when (val created = connection.newSession(AgentNewSessionRequest(session.cwd, additionalDirectories))) {
                is AgentOperationResult.Success -> activate(
                    created.value,
                    session.cwd,
                    preserveDraftPreferences = true,
                    state = AgentRuntimeSessionState.WarmDraft,
                )
                is AgentOperationResult.Failure -> return created
                is AgentOperationResult.Unsupported -> return created
            }
        }
        when (val configured = applyDraftModelSelection()) {
            is AgentOperationResult.Success -> Unit
            is AgentOperationResult.Failure -> return configured
            is AgentOperationResult.Unsupported -> return configured
        }
        when (val configured = applyDraftConfiguration()) {
            is AgentOperationResult.Success -> Unit
            is AgentOperationResult.Failure -> return configured
            is AgentOperationResult.Unsupported -> return configured
        }
        when (val configured = applyDraftMode()) {
            is AgentOperationResult.Success -> Unit
            is AgentOperationResult.Failure -> return configured
            is AgentOperationResult.Unsupported -> return configured
        }
        val sessionId = session.sessionId
            ?: return AgentOperationResult.Failure("Agent 会话创建后未返回会话 ID")
        return AgentOperationResult.Success(sessionId)
    }

    private suspend fun ActiveRuntime.applyDraftModelSelection(): AgentOperationResult<Unit> {
        val target = draftModelSelection ?: return AgentOperationResult.Success(Unit)
        val snapshot = session.snapshot
            ?: return AgentOperationResult.Failure("Agent 会话状态不可用")
        val mapped = resolveDraftModelSelection(target, snapshot.configuration)
        if (mapped == null) {
            if (target.usesAgentDefault) {
                return AgentOperationResult.Success(Unit)
            }
            return AgentOperationResult.Failure("当前 Agent 未提供该本轮模型，请重新选择后发送")
        }
        val current = snapshot.configuration
            .filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.id == mapped.configId }
        if (current?.currentValue == mapped.value) {
            return AgentOperationResult.Success(Unit)
        }
        return when (val result = connection.setConfiguration(
            snapshot.id,
            mapped.configId,
            AgentConfigValue.Select(mapped.value)
        )) {
            is AgentOperationResult.Success -> {
                val normalized = normalizeConfiguration(result.value)
                val complete = mergeDraftConfigurationCatalog(snapshot.configuration, normalized)
                val key = AgentConversationKey(session.providerId, snapshot.id)
                AgentConversationStore.applyEvent(key, AgentSessionEvent.ConfigurationUpdated(complete))
                session = session.copy(snapshot = snapshot.copy(configuration = complete))
                publishDraftCatalog(draftCatalog.copy(
                    configuration = mergeDraftConfigurationCatalog(
                        draftCatalog.configuration,
                        complete,
                    )
                ))
                AgentOperationResult.Success(Unit)
            }
            is AgentOperationResult.Failure -> result
            is AgentOperationResult.Unsupported -> result
        }
    }

    private suspend fun ActiveRuntime.applyDraftConfiguration(): AgentOperationResult<Unit> {
        val pending = synchronized(draftConfiguration) { draftConfiguration.toList() }
            .sortedBy { (configId, _) ->
                when (session.snapshot?.configuration?.firstOrNull { it.id == configId }?.category) {
                    AgentConfigCategory.Model -> 0
                    AgentConfigCategory.ThoughtLevel -> 1
                    AgentConfigCategory.Permission -> 2
                    else -> 3
                }
            }
        pending.forEach { (configId, value) ->
            if (session.snapshot?.configuration?.none { it.id == configId && it.accepts(value) } != false) {
                return AgentOperationResult.Failure("当前 Agent 未提供本轮预选配置：$configId")
            }
            when (val result = applySessionConfiguration(configId, value)) {
                is AgentOperationResult.Success -> Unit
                is AgentOperationResult.Failure -> return result
                is AgentOperationResult.Unsupported -> return result
            }
        }
        return AgentOperationResult.Success(Unit)
    }

    private suspend fun ActiveRuntime.applyDraftMode(): AgentOperationResult<Unit> {
        val target = draftModeId ?: return AgentOperationResult.Success(Unit)
        return when (val result = applySessionMode(target)) {
            is AgentOperationResult.Success -> result
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
                val complete = mergeDraftConfigurationCatalog(snapshot.configuration, normalized)
                val key = AgentConversationKey(session.providerId, sessionId)
                AgentConversationStore.applyEvent(key, AgentSessionEvent.ConfigurationUpdated(complete))
                session = session.copy(snapshot = snapshot.copy(configuration = complete))
                publishDraftCatalog(draftCatalog.copy(
                    configuration = mergeDraftConfigurationCatalog(
                        draftCatalog.configuration,
                        complete,
                    )
                ))
                AgentOperationResult.Success(complete)
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
        val mode = snapshot.modes.ifEmpty { draftCatalog.modes }.firstOrNull { it.id == modeId }
            ?: return AgentOperationResult.Failure("当前 Agent 未提供本轮预选工作模式")
        return when (val result = connection.setMode(sessionId, mode.id)) {
            is AgentOperationResult.Success -> {
                val key = AgentConversationKey(session.providerId, sessionId)
                AgentConversationStore.applyEvent(key, AgentSessionEvent.CurrentModeChanged(mode.id))
                session = session.copy(snapshot = snapshot.copy(
                    modes = snapshot.modes.ifEmpty { draftCatalog.modes },
                    currentModeId = mode.id,
                ))
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

    private fun AgentDraftCapabilityCatalog.acceptedPermissionSelection(
        selection: AgentDraftConfigurationSelection?,
    ): AgentDraftConfigurationSelection? {
        selection ?: return null
        val option = configuration.firstOrNull {
            it.id == selection.configId && it.category == AgentConfigCategory.Permission
        }
        return selection.takeIf { option?.accepts(selection.value) == true }
    }

    private fun ActiveRuntime.restoreDraftPreferences(sessionId: String?) {
        val restored = sessionId
            ?.let { runCatching { loadSessionDraftPreferences(it) }.getOrNull() }
        draftModelSelection = restored?.modelSelection ?: defaultDraftModelSelection
        val permission = draftCatalog.acceptedPermissionSelection(restored?.permissionSelection)
            ?: draftCatalog.acceptedPermissionSelection(defaultDraftPermissionSelection)
        synchronized(draftConfiguration) {
            draftConfiguration.clear()
            permission?.let { draftConfiguration[it.configId] = it.value }
        }
    }

    private fun ActiveRuntime.sanitizeDraftPermissionSelection() {
        synchronized(draftConfiguration) {
            val permissionIds = draftCatalog.configuration
                .filter { it.category == AgentConfigCategory.Permission }
                .mapTo(hashSetOf(), AgentConfigOption::id)
            draftConfiguration.keys
                .filter { it in permissionIds }
                .filter { configId ->
                    val value = draftConfiguration[configId] ?: return@filter false
                    draftCatalog.configuration.none { it.id == configId && it.accepts(value) }
                }
                .forEach(draftConfiguration::remove)
        }
    }

    private fun ActiveRuntime.publishDraftPreferences(updateAgentDefault: Boolean) {
        val permission = synchronized(draftConfiguration) {
            draftCatalog.configuration
                .firstOrNull { option ->
                    option.category == AgentConfigCategory.Permission &&
                        draftConfiguration[option.id]?.let { value -> option.accepts(value) } == true
                }
                ?.let { option ->
                    AgentDraftConfigurationSelection(option.id, checkNotNull(draftConfiguration[option.id]))
                }
        }
        runCatching {
            onDraftPreferencesChanged(
                session.sessionId,
                AgentDraftPersistenceSnapshot(draftModelSelection, permission),
                updateAgentDefault,
            )
        }
    }

    private fun AgentDraftCapabilityCatalog.withSnapshot(
        snapshot: AgentSessionSnapshot,
        normalizeModes: (List<AgentMode>) -> List<AgentMode>,
    ): AgentDraftCapabilityCatalog {
        val publishedModes = normalizeModes(snapshot.modes).distinctBy(AgentMode::id)
        val nextModes = publishedModes.ifEmpty { modes }
        return copy(
            configuration = mergeDraftConfigurationCatalog(configuration, snapshot.configuration),
            modes = nextModes,
            currentModeId = snapshot.currentModeId?.takeIf { id -> nextModes.any { it.id == id } }
                ?: currentModeId?.takeIf { id -> nextModes.any { it.id == id } },
        )
    }

    private fun mergeDraftConfigurationCatalog(
        current: List<AgentConfigOption>,
        published: List<AgentConfigOption>,
    ): List<AgentConfigOption> {
        if (current.isEmpty()) return published
        if (published.isEmpty()) return current
        val publishedIds = published.mapTo(hashSetOf(), AgentConfigOption::id)
        val publishedCategories = published.mapNotNullTo(hashSetOf(), AgentConfigOption::category)
        return current.filterNot { option ->
            option.id in publishedIds ||
                (option.category != null && option.category in publishedCategories)
        } + published
    }

    private fun ActiveRuntime.applyDraftConfigurationPreview(
        preview: AgentDraftConfigurationPreview,
    ) {
        if (preview.replaceCategories.isEmpty()) return
        val normalized = normalizeConfiguration(preview.options)
            .filter { it.category in preview.replaceCategories }
        synchronized(draftConfiguration) {
            val replacements = normalized.associateBy(AgentConfigOption::id)
            draftCatalog.configuration
                .filter { it.category in preview.replaceCategories }
                .forEach { previous ->
                    val selected = draftConfiguration[previous.id] ?: return@forEach
                    if (replacements[previous.id]?.accepts(selected) != true) {
                        draftConfiguration.remove(previous.id)
                    }
                }
        }
        publishDraftCatalog(draftCatalog.copy(
            configuration = draftCatalog.configuration.filterNot { option ->
                option.category in preview.replaceCategories
            } + normalized,
            resolvedConfigurationCategories =
                draftCatalog.resolvedConfigurationCategories + preview.replaceCategories,
        ))
    }

    private suspend fun ActiveRuntime.prepareDraftProvider(): AgentOperationResult<Unit> {
        val target = draftModelSelection ?: return AgentOperationResult.Success(Unit)
        return when (val prepared = prepareDraftModelSelection(target)) {
            is AgentProviderPreparationResult.Failed -> AgentOperationResult.Failure(prepared.message)
            is AgentProviderPreparationResult.Ready -> {
                if (prepared.nativeConfigurationChanged) {
                    pendingProviderConfigurationEffect = prepared.effect
                }
                val effect = pendingProviderConfigurationEffect
                    ?: return AgentOperationResult.Success(Unit)
                val applied = when (effect) {
                    AgentSessionConfigurationEffect.Reconnect -> reconnectForProviderSelection()
                    AgentSessionConfigurationEffect.ReconnectNewSession ->
                        reconnectForProviderSelection(forceNewSession = true)
                    AgentSessionConfigurationEffect.NewSession ->
                    when (val warmed = createWarmDraft(session.cwd, preserveDraftPreferences = true)) {
                        is AgentOperationResult.Success -> AgentOperationResult.Success(Unit)
                        is AgentOperationResult.Failure -> warmed
                        is AgentOperationResult.Unsupported -> warmed
                    }
                    else -> AgentOperationResult.Success(Unit)
                }
                if (applied is AgentOperationResult.Success) {
                    pendingProviderConfigurationEffect = null
                }
                applied
            }
        }
    }

    /** 新连接完全可用后才释放旧连接，失败时仍可保留用户当前会话。 */
    private suspend fun ActiveRuntime.reconnectForProviderSelection(
        forceNewSession: Boolean = false,
    ): AgentOperationResult<Unit> {
        val connected = provider.connect(connectionRequest, endpoint)
        val nextConnection = when (connected) {
            is AgentOperationResult.Success -> connected.value
            is AgentOperationResult.Failure -> return connected
            is AgentOperationResult.Unsupported -> return connected
        }
        val previousConnection = connection
        val previousSessionId = session.sessionId
        val wasDraft = session.isDraft
        val restored = if (wasDraft || forceNewSession) {
            preparingWarmDraft = true
            try {
                nextConnection.newSession(AgentNewSessionRequest(session.cwd, additionalDirectories))
            } finally {
                preparingWarmDraft = false
            }
        } else {
            previousSessionId?.let { sessionId ->
                restoreExistingSession(
                    connection = nextConnection,
                    instanceId = session.instanceId,
                    providerId = session.providerId,
                    sessionId = sessionId,
                    cwd = session.cwd,
                    additionalDirectories = additionalDirectories,
                )
            }
        }
        val restoredSnapshot = when (restored) {
            null -> null
            is AgentOperationResult.Success -> restored.value
            is AgentOperationResult.Failure -> {
                nextConnection.disconnect()
                return restored
            }
            is AgentOperationResult.Unsupported -> {
                nextConnection.disconnect()
                return restored
            }
        }
        connection = nextConnection
        previousConnection.disconnect()
        if (restoredSnapshot == null) {
            session = session.copy(
                sessionId = null,
                snapshot = null,
                capabilities = nextConnection.capabilities,
                state = AgentRuntimeSessionState.ColdDraft,
            )
        } else {
            activate(
                restoredSnapshot,
                session.cwd,
                preserveDraftPreferences = true,
                state = if (wasDraft) AgentRuntimeSessionState.WarmDraft else AgentRuntimeSessionState.Active,
            )
        }
        return AgentOperationResult.Success(Unit)
    }

    private fun ActiveRuntime.publishDraftCatalog(next: AgentDraftCapabilityCatalog) {
        val modes = normalizeModes(next.modes).distinctBy(AgentMode::id)
        val normalized = next.copy(
            modes = modes,
            currentModeId = next.currentModeId?.takeIf { id -> modes.any { it.id == id } },
        )
        if (defaultDraftModeId?.let { id -> modes.none { it.id == id } } == true) {
            defaultDraftModeId = normalized.currentModeId
        }
        if (draftModeId?.let { id -> modes.none { it.id == id } } == true) {
            draftModeId = if (session.isDraft) defaultDraftModeId else null
        }
        updateDraftCatalog { normalized }
    }

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

    private fun draftConversationId(instanceId: String, generation: Long): String =
        "$DRAFT_CONVERSATION_PREFIX$instanceId:$generation"

    private fun composerDraftKey(sessionId: String?): String =
        sessionId?.trim()?.takeIf(String::isNotBlank) ?: COLD_COMPOSER_DRAFT_KEY

    private fun ActiveRuntime.storeComposerDraft(sessionId: String?, draft: AgentPromptDraft) {
        val snapshot = draft.immutableCopy()
        synchronized(composerDraftLock) {
            val key = composerDraftKey(sessionId)
            if (snapshot.content.isEmpty() && snapshot.skills.isEmpty()) {
                composerDrafts.remove(key)
            } else {
                composerDrafts.remove(key)
                composerDrafts[key] = snapshot
                while (composerDrafts.size > MAX_COMPOSER_DRAFTS) {
                    composerDrafts.remove(composerDrafts.keys.first())
                }
            }
        }
    }

    private fun ActiveRuntime.takeFailedLocalMessage(
        sessionId: String?,
        draft: AgentPromptDraft,
    ): FailedLocalMessage? = synchronized(composerDraftLock) {
        val key = composerDraftKey(sessionId)
        val failed = failedLocalMessages[key] ?: return@synchronized null
        if (failed.draft != draft) {
            failedLocalMessages.remove(key)
            null
        } else {
            failedLocalMessages.remove(key)
        }
    }

    private fun ActiveRuntime.rememberFailedLocalMessage(
        sessionId: String?,
        failed: FailedLocalMessage,
    ) {
        synchronized(composerDraftLock) {
            failedLocalMessages[composerDraftKey(sessionId)] = failed
            while (failedLocalMessages.size > MAX_COMPOSER_DRAFTS) {
                failedLocalMessages.remove(failedLocalMessages.keys.first())
            }
        }
    }

    private fun AgentPromptDraft.immutableCopy(): AgentPromptDraft = AgentPromptDraft(
        content = content.toList(),
        skills = skills.toList(),
    )

    /**
     * 加载历史会话前先恢复该会话保存的 Provider 选择。若 Agent 进程在配置准备前已经启动，
     * 第一次加载失败后只重建一次连接并复用同一 session/load，不创建替代会话。
     */
    private suspend fun ActiveRuntime.loadPreparedSession(
        sessionId: String,
        cwd: String,
    ): AgentOperationResult<AgentRuntimeSession> {
        val preferences = runCatching { loadSessionDraftPreferences(sessionId) }.getOrNull()
        val targetModel = preferences?.modelSelection ?: defaultDraftModelSelection
        val preparation = targetModel?.let { prepareDraftModelSelection(it) }
        if (preparation is AgentProviderPreparationResult.Failed) {
            return AgentOperationResult.Failure(preparation.message)
        }

        val needsFreshConnection = (preparation as? AgentProviderPreparationResult.Ready)
            ?.takeIf { it.nativeConfigurationChanged }
            ?.effect in setOf(
                AgentSessionConfigurationEffect.Reconnect,
                AgentSessionConfigurationEffect.ReconnectNewSession,
            )
        var candidate = connection
        var candidateOwned = false

        suspend fun connectCandidate(): AgentOperationResult<Unit> = when (
            val connected = provider.connect(connectionRequest, endpoint)
        ) {
            is AgentOperationResult.Success -> {
                candidate = connected.value
                candidateOwned = candidate !== connection
                AgentOperationResult.Success(Unit)
            }
            is AgentOperationResult.Failure -> connected
            is AgentOperationResult.Unsupported -> connected
        }

        if (needsFreshConnection) {
            when (val connected = connectCandidate()) {
                is AgentOperationResult.Success -> Unit
                is AgentOperationResult.Failure -> return connected
                is AgentOperationResult.Unsupported -> return connected
            }
        }

        suspend fun restore(target: KiteAgentConnection) = restoreExistingSession(
            connection = target,
            instanceId = session.instanceId,
            providerId = session.providerId,
            sessionId = sessionId,
            cwd = cwd,
            additionalDirectories = additionalDirectories,
        )

        var loaded = restore(candidate)
        if (loaded is AgentOperationResult.Failure && !candidateOwned && targetModel != null) {
            when (val connected = connectCandidate()) {
                is AgentOperationResult.Success -> loaded = restore(candidate)
                is AgentOperationResult.Failure -> return connected
                is AgentOperationResult.Unsupported -> return connected
            }
        }

        return when (loaded) {
            is AgentOperationResult.Success -> {
                if (candidateOwned) {
                    val previous = connection
                    connection = candidate
                    previous.disconnect()
                }
                draftModelSelection = targetModel
                pendingProviderConfigurationEffect = null
                AgentOperationResult.Success(
                    activate(loaded.value, cwd, preserveDraftPreferences = false),
                )
            }
            is AgentOperationResult.Failure -> {
                if (candidateOwned) candidate.disconnect()
                loaded
            }
            is AgentOperationResult.Unsupported -> {
                if (candidateOwned) candidate.disconnect()
                loaded
            }
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
        val request = AgentExistingSessionRequest(sessionId, cwd, additionalDirectories)
        // `resume` 只恢复连接，不回放历史。只要 Agent 支持 `load`，切换会话就重新读取权威历史，
        // 再由 ConversationStore 与仍未进入原生历史的本地回合对账，不能把一份内存投影视为完整历史。
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
