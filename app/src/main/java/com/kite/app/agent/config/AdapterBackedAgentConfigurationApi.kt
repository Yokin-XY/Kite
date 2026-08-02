package com.kite.app.agent.config

import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.sdk.configuration.AgentConfigurationApi
import com.kite.app.agent.sdk.configuration.AgentConfigurationIntent
import com.kite.app.agent.sdk.configuration.AgentConfigurationMutation
import com.kite.app.agent.sdk.configuration.AgentConfigurationTarget

/** Adapter SPI 到固定 Kite Agent SDK 配置端口的唯一桥接实现。 */
class AdapterBackedAgentConfigurationApi(
    private val adapters: AgentConfigAdapterRegistry,
) : AgentConfigurationApi {
    override fun capabilities(target: AgentConfigurationTarget): AgentConfigCapabilities? =
        adapters.adapter(target.adapterId)?.capabilities()

    override fun providerPresets(target: AgentConfigurationTarget): List<AgentProviderPreset> =
        adapters.adapter(target.adapterId)?.let { AgentProviderPresetCatalog.presets }.orEmpty()

    override suspend fun read(target: AgentConfigurationTarget): AgentConfigReadResult =
        adapters.adapter(target.adapterId)?.readLive(target.agentId)
            ?: AgentConfigReadResult.Unavailable(target.unsupportedDiscovery())

    override suspend fun apply(
        target: AgentConfigurationTarget,
        expectedRevision: String,
        intents: List<AgentConfigurationIntent>,
    ): AgentConfigurationMutation {
        val adapter = adapters.adapter(target.adapterId)
            ?: return AgentConfigurationMutation(
                AgentConfigApplyResult.Unavailable(target.unsupportedDiscovery()),
                AgentConfigReadResult.Unavailable(target.unsupportedDiscovery()),
            )
        val changes = intents.mapNotNull { intent -> adapter.toPersistentChange(intent) }
        val result = if (changes.size != intents.size) {
            AgentConfigApplyResult.Rejected(
                listOf(AgentConfigValidationProblem("intents", "当前 Agent 不支持这项统一配置操作"))
            )
        } else {
            adapter.apply(AgentConfigApplyRequest(target.agentId, expectedRevision, changes))
        }
        val current = when (result) {
            is AgentConfigApplyResult.Applied -> AgentConfigReadResult.Ready(result.snapshot)
            else -> adapter.backfill(target.agentId)
        }
        return AgentConfigurationMutation(result, current)
    }

    override suspend fun listCoreDocuments(
        target: AgentConfigurationTarget,
        workspacePath: String?,
    ): AgentCoreDocumentListResult = adapters.adapter(target.adapterId)
        ?.listCoreDocuments(target.agentId, workspacePath)
        ?: AgentCoreDocumentListResult.Unavailable(target.unsupportedDiscovery())

    override suspend fun readCoreDocument(
        target: AgentConfigurationTarget,
        documentId: String,
        workspacePath: String?,
    ): AgentCoreDocumentReadResult = adapters.adapter(target.adapterId)
        ?.readCoreDocument(target.agentId, documentId, workspacePath)
        ?: AgentCoreDocumentReadResult.Unavailable(target.unsupportedDiscovery())

    override suspend fun writeCoreDocument(
        target: AgentConfigurationTarget,
        request: AgentCoreDocumentWriteRequest,
    ): AgentCoreDocumentWriteResult = adapters.adapter(target.adapterId)
        ?.writeCoreDocument(request)
        ?: AgentCoreDocumentWriteResult.Unavailable(target.unsupportedDiscovery())

    override suspend fun readSkillDocument(
        target: AgentConfigurationTarget,
        skillId: String,
    ): AgentSkillDocumentReadResult = adapters.adapter(target.adapterId)
        ?.readSkillDocument(target.agentId, skillId)
        ?: AgentSkillDocumentReadResult.Unavailable(target.unsupportedDiscovery())

    override suspend fun writeSkillDocument(
        target: AgentConfigurationTarget,
        request: AgentSkillDocumentWriteRequest,
    ): AgentSkillDocumentWriteResult = adapters.adapter(target.adapterId)
        ?.writeSkillDocument(request)
        ?: AgentSkillDocumentWriteResult.Unavailable(target.unsupportedDiscovery())

    override suspend fun checkMcp(
        target: AgentConfigurationTarget,
        serverId: String,
    ): AgentMcpConnectionCheckResult = adapters.adapter(target.adapterId)
        ?.checkMcpServer(target.agentId, serverId)
        ?: AgentMcpConnectionCheckResult.Unsupported()

    private fun AgentConfigAdapter.toPersistentChange(
        intent: AgentConfigurationIntent,
    ): AgentPersistentConfigChange? = when (intent) {
        is AgentConfigurationIntent.SelectModel -> persistentModelChange(intent.selection)
        is AgentConfigurationIntent.SetPermission -> AgentPersistentConfigChange.SetPermissionProfile(intent.profileId)
        is AgentConfigurationIntent.ConfigureProvider -> AgentPersistentConfigChange.ConfigureProvider(
            intent.provider,
            intent.credential,
        )
        is AgentConfigurationIntent.RemoveProvider -> AgentPersistentConfigChange.RemoveProvider(
            intent.providerId,
            intent.removeCredential,
        )
        is AgentConfigurationIntent.ConfigureMcp -> AgentPersistentConfigChange.ConfigureMcpServer(intent.server)
        is AgentConfigurationIntent.SetMcpEnabled -> AgentPersistentConfigChange.SetMcpEnabled(
            intent.serverId,
            intent.enabled,
        )
        is AgentConfigurationIntent.RemoveMcp -> AgentPersistentConfigChange.RemoveMcpServer(intent.serverId)
        is AgentConfigurationIntent.InstallSkill -> AgentPersistentConfigChange.InstallSkill(
            intent.skillId,
            intent.sourceReference,
        )
        is AgentConfigurationIntent.SetSkillActivation -> AgentPersistentConfigChange.SetSkillActivation(
            intent.skillId,
            intent.activation,
        )
        is AgentConfigurationIntent.RemoveSkill -> AgentPersistentConfigChange.RemoveSkill(intent.skillId)
    }

    private fun AgentConfigurationTarget.unsupportedDiscovery() = AgentConfigDiscovery(
        agentId = agentId,
        adapterId = adapterId.orEmpty(),
        state = AgentConfigDiscoveryState.Unsupported,
        warnings = listOf("当前 Agent 没有可用的配置 Adapter"),
    )
}

/** Adapter 负责把统一模型选择翻译成自己的原生写入规则。 */
fun AgentConfigAdapter.persistentModelChange(
    selection: com.kite.app.agent.sdk.configuration.AgentModelSelection,
): AgentPersistentConfigChange? {
    if (selection.source == AgentModelSource.UserConfigured) {
        return AgentPersistentConfigChange.SelectProvider(selection.sourceId, selection.modelId)
    }
    return defaultModelChange(
        com.kite.app.agent.contract.AgentConfigOption.Select(
            id = selection.configId,
            name = "模型",
            category = com.kite.app.agent.contract.AgentConfigCategory.Model,
            currentValue = selection.nativeValue,
            choices = listOf(
                com.kite.app.agent.contract.AgentConfigChoice(
                    value = selection.nativeValue,
                    name = selection.nativeValue,
                    groupId = selection.sourceId,
                    modelSource = selection.source,
                )
            ),
        )
    )
}
