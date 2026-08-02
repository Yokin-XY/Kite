package com.kite.app.agent.config

import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentDraftConfigurationPreview
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.contract.AgentSessionSnapshot
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal data class AgentSessionConfigurationOverlayMerge(
    val options: List<AgentConfigOption>,
    val protocolCategoriesPublished: Set<AgentConfigCategory>,
) {
    val protocolPermissionPublished: Boolean
        get() = AgentConfigCategory.Permission in protocolCategoriesPublished
}

internal fun mergeAgentSessionConfigurationOverlay(
    options: List<AgentConfigOption>,
    native: List<AgentConfigOption>,
    protocolCategoriesPublished: Set<AgentConfigCategory> = emptySet(),
    matchingNativeIdsAreProtocol: Boolean = true,
): AgentSessionConfigurationOverlayMerge {
    val nativeIds = native.mapTo(hashSetOf(), AgentConfigOption::id)
    val nativeCategories = native.mapNotNullTo(hashSetOf(), AgentConfigOption::category)
    val protocolPublishedNow = options.asSequence()
        .filter { option ->
            option.category in nativeCategories &&
                (matchingNativeIdsAreProtocol || option.id !in nativeIds)
        }
        .mapNotNull(AgentConfigOption::category)
        .toSet()
    val protocolPublished = protocolCategoriesPublished + protocolPublishedNow
    val withoutStaleNative = if (matchingNativeIdsAreProtocol) {
        options
    } else {
        options.filterNot { it.id in nativeIds }
    }
    return AgentSessionConfigurationOverlayMerge(
        options = withoutStaleNative + native.filterNot { it.category in protocolPublished },
        protocolCategoriesPublished = protocolPublished,
    )
}

/**
 * 在协议连接没有公布权限类别时，投影适配器声明的当前会话权限代理档位。
 *
 * 这层只装饰 SDK 配置合同，不识别 Agent 产品名。自动作答仅发生在真实权限请求到达后，
 * 且只使用一次性选项；Agent 原生默认值仍由设置页和原生配置适配器拥有。
 */
class AgentSessionConfigurationOverlayProvider(
    private val delegate: KiteAgentProvider,
    private val agentId: String,
    private val adapter: AgentConfigAdapter,
    private val initialConfiguration: List<AgentConfigOption>? = null,
) : KiteAgentProvider {
    override val id: String = delegate.id

    override suspend fun connect(
        request: AgentConnectionRequest,
        client: AgentClientEndpoint
    ): AgentOperationResult<KiteAgentConnection> {
        val overlay = AtomicReference(initialConfiguration ?: adapter.readSessionConfiguration(agentId))
        val permissionControl = adapter.sessionPermissionControl()
        val actualCategoriesPublished = AtomicReference<Set<AgentConfigCategory>>(emptySet())
        val configurationBySession = ConcurrentHashMap<String, List<AgentConfigOption>>()
        val permissionProfileBySession = ConcurrentHashMap<String, String>()

        fun initialPermissionProfileId(option: AgentConfigOption.Select): String =
            option.currentValue.takeIf { current ->
                permissionControl?.profiles?.any { it.id == current } == true
            } ?: permissionControl?.initialProfileId ?: option.currentValue

        fun merge(
            options: List<AgentConfigOption>,
            sessionId: String? = null,
            protocolResponse: Boolean = true,
        ): List<AgentConfigOption> {
            val sessionOverlay = overlay.get().map { option ->
                if (
                    option.id == SESSION_PERMISSION_CONFIG_ID &&
                    option is AgentConfigOption.Select &&
                    sessionId != null
                ) {
                    option.copy(
                        currentValue = permissionProfileBySession.getOrPut(sessionId) {
                            initialPermissionProfileId(option)
                        }
                    )
                } else {
                    option
                }
            }
            val merged = mergeAgentSessionConfigurationOverlay(
                options = options,
                native = sessionOverlay,
                protocolCategoriesPublished = actualCategoriesPublished.get(),
                matchingNativeIdsAreProtocol = protocolResponse,
            )
            actualCategoriesPublished.set(merged.protocolCategoriesPublished)
            return merged.options
        }

        val endpoint = AgentClientEndpoint(
            eventSink = { sessionId, event ->
                val projected = when (event) {
                    is AgentSessionEvent.ConfigurationUpdated -> event.copy(
                        options = merge(event.options, sessionId).also { configurationBySession[sessionId] = it },
                    )
                    is AgentSessionEvent.CurrentModeChanged -> {
                        val permissionProfileId = permissionControl?.profileIdForNativeMode(event.modeId)
                        if (permissionProfileId == null) {
                            event
                        } else {
                            permissionProfileBySession[sessionId] = permissionProfileId
                            AgentSessionEvent.ConfigurationUpdated(
                                merge(configurationBySession[sessionId].orEmpty(), sessionId, false).also {
                                    configurationBySession[sessionId] = it
                                },
                            )
                        }
                    }
                    else -> event
                }
                client.eventSink.onEvent(sessionId, projected)
            },
            permissionHandler = { permissionRequest ->
                val mediated = permissionControl
                    ?.takeIf { AgentConfigCategory.Permission !in actualCategoriesPublished.get() }
                    ?.resolve(
                        permissionProfileBySession[permissionRequest.sessionId]
                            ?: permissionControl.initialProfileId,
                        permissionRequest,
                    )
                mediated ?: client.permissionHandler.request(permissionRequest)
            }
        )
        return when (val connected = delegate.connect(request, endpoint)) {
            is AgentOperationResult.Success -> AgentOperationResult.Success(
                OverlayConnection(
                    delegate = connected.value,
                    agentId = agentId,
                    adapter = adapter,
                    overlay = overlay,
                    actualCategoriesPublished = actualCategoriesPublished,
                    configurationBySession = configurationBySession,
                    permissionProfileBySession = permissionProfileBySession,
                    permissionControl = permissionControl,
                    merge = ::merge,
                )
            )
            is AgentOperationResult.Failure -> connected
            is AgentOperationResult.Unsupported -> connected
        }
    }

    private class OverlayConnection(
        private val delegate: KiteAgentConnection,
        private val agentId: String,
        private val adapter: AgentConfigAdapter,
        private val overlay: AtomicReference<List<AgentConfigOption>>,
        private val actualCategoriesPublished: AtomicReference<Set<AgentConfigCategory>>,
        private val configurationBySession: ConcurrentHashMap<String, List<AgentConfigOption>>,
        private val permissionProfileBySession: ConcurrentHashMap<String, String>,
        private val permissionControl: AgentSessionPermissionControl?,
        private val merge: (List<AgentConfigOption>, String?, Boolean) -> List<AgentConfigOption>,
    ) : KiteAgentConnection {
        override val provider: AgentProviderInfo get() = delegate.provider
        override val capabilities get() = delegate.capabilities

        override suspend fun newSession(request: AgentNewSessionRequest) = delegate.newSession(request).project()

        override suspend fun loadSession(request: AgentExistingSessionRequest) = delegate.loadSession(request).project()

        override suspend fun listSessions(request: AgentSessionListRequest): AgentOperationResult<AgentSessionPage> =
            delegate.listSessions(request)

        override suspend fun resumeSession(request: AgentExistingSessionRequest) = delegate.resumeSession(request).project()

        override suspend fun forkSession(request: AgentExistingSessionRequest) = delegate.forkSession(request).project()

        override suspend fun closeSession(sessionId: String): AgentOperationResult<Unit> {
            configurationBySession.remove(sessionId)
            permissionProfileBySession.remove(sessionId)
            return delegate.closeSession(sessionId)
        }

        override suspend fun deleteSession(sessionId: String): AgentOperationResult<Unit> {
            configurationBySession.remove(sessionId)
            permissionProfileBySession.remove(sessionId)
            return delegate.deleteSession(sessionId)
        }

        override suspend fun renameSession(request: AgentSessionRenameRequest): AgentOperationResult<Unit> =
            delegate.renameSession(request)

        override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> =
            delegate.prompt(request)

        override suspend fun setConfiguration(
            sessionId: String,
            configId: String,
            value: AgentConfigValue
        ): AgentOperationResult<List<AgentConfigOption>> {
            val nativeOption = overlay.get().firstOrNull { it.id == configId }
            if (nativeOption == null || nativeOption.category in actualCategoriesPublished.get()) {
                return delegate.setConfiguration(sessionId, configId, value).projectConfiguration(sessionId)
            }
            if (configId == SESSION_PERMISSION_CONFIG_ID) {
                val selected = (value as? AgentConfigValue.Select)?.value
                    ?: return AgentOperationResult.Failure("当前会话权限需要选择一个档位")
                val control = permissionControl
                    ?: return AgentOperationResult.Unsupported("session-permission-control")
                if (control.profiles.none { it.id == selected }) {
                    return AgentOperationResult.Failure("当前 Agent 未提供该会话权限档位")
                }
                control.nativeModeId(selected)?.let { nativeModeId ->
                    when (val changed = delegate.setMode(sessionId, nativeModeId)) {
                        is AgentOperationResult.Success -> Unit
                        is AgentOperationResult.Failure -> return changed
                        is AgentOperationResult.Unsupported -> return changed
                    }
                }
                permissionProfileBySession[sessionId] = selected
                val current = configurationBySession[sessionId].orEmpty()
                return AgentOperationResult.Success(
                    merge(current, sessionId, false).also { configurationBySession[sessionId] = it }
                )
            }
            return when (val result = adapter.applySessionConfiguration(agentId, configId, value)) {
                is AgentSessionConfigurationApplyResult.Applied -> {
                    overlay.set(result.options)
                    val current = configurationBySession[sessionId].orEmpty()
                    AgentOperationResult.Success(
                        merge(current, sessionId, false).also { configurationBySession[sessionId] = it }
                    )
                }
                is AgentSessionConfigurationApplyResult.Failed -> AgentOperationResult.Failure(result.message)
                is AgentSessionConfigurationApplyResult.Unsupported -> AgentOperationResult.Unsupported(result.operation)
            }
        }

        override suspend fun setMode(sessionId: String, modeId: String): AgentOperationResult<Unit> =
            delegate.setMode(sessionId, modeId)

        override fun previewDraftModelConfiguration(
            providerId: String,
            modelId: String,
        ): AgentDraftConfigurationPreview? = delegate.previewDraftModelConfiguration(providerId, modelId)

        override suspend fun authenticate(methodId: String): AgentOperationResult<Unit> = delegate.authenticate(methodId)

        override suspend fun logout(): AgentOperationResult<Unit> = delegate.logout()

        override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> = delegate.cancel(sessionId)

        override suspend fun disconnect() {
            configurationBySession.clear()
            permissionProfileBySession.clear()
            delegate.disconnect()
        }

        private fun AgentOperationResult<AgentSessionSnapshot>.project(): AgentOperationResult<AgentSessionSnapshot> =
            when (this) {
                is AgentOperationResult.Success -> {
                    permissionControl?.profileIdForNativeMode(value.currentModeId)?.let { profileId ->
                        permissionProfileBySession[value.id] = profileId
                    }
                    val publishedModes = value.modes.filterNot { mode ->
                        permissionControl?.profileIdForNativeMode(mode.id) != null
                    }
                    val publishedCurrentModeId = value.currentModeId?.takeIf { modeId ->
                        permissionControl?.profileIdForNativeMode(modeId) == null
                    }
                    AgentOperationResult.Success(
                        value.copy(
                            configuration = merge(value.configuration, value.id, true).also {
                                configurationBySession[value.id] = it
                            },
                            modes = publishedModes,
                            currentModeId = publishedCurrentModeId,
                        )
                    )
                }
                is AgentOperationResult.Failure -> this
                is AgentOperationResult.Unsupported -> this
            }

        private fun AgentOperationResult<List<AgentConfigOption>>.projectConfiguration(
            sessionId: String
        ): AgentOperationResult<List<AgentConfigOption>> = when (this) {
            is AgentOperationResult.Success -> AgentOperationResult.Success(
                merge(value, sessionId, true).also { configurationBySession[sessionId] = it }
            )
            is AgentOperationResult.Failure -> this
            is AgentOperationResult.Unsupported -> this
        }
    }
}
