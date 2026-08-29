package com.kite.app.agent.sdk.configuration

import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentCoreDocumentListResult
import com.kite.app.agent.config.AgentCoreDocumentReadResult
import com.kite.app.agent.config.AgentCoreDocumentWriteRequest
import com.kite.app.agent.config.AgentCoreDocumentWriteResult
import com.kite.app.agent.config.AgentMcpConnectionCheckResult
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderPreset
import com.kite.app.agent.config.AgentProviderPresetSource
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillDocumentReadResult
import com.kite.app.agent.config.AgentSkillDocumentWriteRequest
import com.kite.app.agent.config.AgentSkillDocumentWriteResult
import com.kite.app.agent.registration.AgentRegistryEntry

/** 只携带稳定 ID；显示名称和产品名称不参与 Adapter 选择。 */
data class AgentConfigurationTarget(
    val agentId: String,
    val adapterId: String?,
)

/** 从登记事实生成 SDK 目标；页面不需要持有或查找 Adapter 实例。 */
fun AgentRegistryEntry.configurationTarget(): AgentConfigurationTarget = AgentConfigurationTarget(
    agentId = registration.definition.agentId,
    adapterId = registration.configAdapterId,
)

/** 页面提交的统一配置意图；原生配置结构不会越过 SDK。 */
sealed interface AgentConfigurationIntent {
    data class SelectModel(val selection: AgentModelSelection) : AgentConfigurationIntent
    data class SetPermission(val profileId: String) : AgentConfigurationIntent
    data class ConfigureProvider(
        val provider: AgentProviderDraft,
        val credential: AgentProviderCredentialChange = AgentProviderCredentialChange.Keep,
    ) : AgentConfigurationIntent
    data class RemoveProvider(val providerId: String, val removeCredential: Boolean = false) : AgentConfigurationIntent
    data class ConfigureMcp(val server: AgentMcpDraft) : AgentConfigurationIntent
    data class SetMcpEnabled(val serverId: String, val enabled: Boolean) : AgentConfigurationIntent
    data class RemoveMcp(val serverId: String) : AgentConfigurationIntent
    data class InstallSkill(val skillId: String, val sourceReference: String) : AgentConfigurationIntent
    data class SetSkillActivation(
        val skillId: String,
        val activation: AgentSkillActivation,
    ) : AgentConfigurationIntent
    data class RemoveSkill(val skillId: String) : AgentConfigurationIntent
}

/** 每次写入后都携带重新读取的当前事实，页面不再自行调用 Adapter.backfill。 */
data class AgentConfigurationMutation(
    val result: AgentConfigApplyResult,
    val current: AgentConfigReadResult,
)

data class AgentProviderPresetRefreshResult(
    val presets: List<AgentProviderPreset>,
    val source: AgentProviderPresetSource,
    val refreshed: Boolean,
    val warning: String? = null,
)

interface AgentConfigurationApi {
    fun capabilities(target: AgentConfigurationTarget): AgentConfigCapabilities?
    fun providerPresets(target: AgentConfigurationTarget): List<AgentProviderPreset> = emptyList()
    /** 只由用户打开供应商配对页时调用；普通页面绘制不得触发网络。 */
    suspend fun refreshProviderPresets(target: AgentConfigurationTarget): AgentProviderPresetRefreshResult =
        AgentProviderPresetRefreshResult(
            presets = providerPresets(target),
            source = AgentProviderPresetSource.Bundled,
            refreshed = false,
        )
    suspend fun read(target: AgentConfigurationTarget): AgentConfigReadResult
    suspend fun apply(
        target: AgentConfigurationTarget,
        expectedRevision: String,
        intents: List<AgentConfigurationIntent>,
    ): AgentConfigurationMutation
    suspend fun listCoreDocuments(
        target: AgentConfigurationTarget,
        workspacePath: String?,
    ): AgentCoreDocumentListResult
    suspend fun readCoreDocument(
        target: AgentConfigurationTarget,
        documentId: String,
        workspacePath: String?,
    ): AgentCoreDocumentReadResult
    suspend fun writeCoreDocument(
        target: AgentConfigurationTarget,
        request: AgentCoreDocumentWriteRequest,
    ): AgentCoreDocumentWriteResult
    suspend fun readSkillDocument(
        target: AgentConfigurationTarget,
        skillId: String,
    ): AgentSkillDocumentReadResult
    suspend fun writeSkillDocument(
        target: AgentConfigurationTarget,
        request: AgentSkillDocumentWriteRequest,
    ): AgentSkillDocumentWriteResult
    suspend fun checkMcp(
        target: AgentConfigurationTarget,
        serverId: String,
    ): AgentMcpConnectionCheckResult
}
