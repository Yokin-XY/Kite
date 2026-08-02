package com.kite.app.agent.sdk.configuration

import com.kite.app.agent.config.AgentConfigAdapterRegistry
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentFreeProviderCatalogResult
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AgentUserProviderImportResult
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.store.AgentCatalogCredentialChange
import com.kite.app.agent.store.AgentCatalogModel
import com.kite.app.agent.store.AgentCatalogProvider
import com.kite.app.agent.store.AgentProviderCatalogPolicy
import com.kite.app.agent.store.AgentProviderCatalogSnapshot
import com.kite.app.agent.store.AgentProviderCatalogStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class AgentProviderCatalogOpenResult(
    val snapshot: AgentProviderCatalogSnapshot,
    val warnings: List<String> = emptyList(),
)

sealed interface AgentProviderPreparationResult {
    data class Ready(
        val effect: AgentSessionConfigurationEffect = AgentSessionConfigurationEffect.Immediate,
        val nativeConfigurationChanged: Boolean = false,
    ) : AgentProviderPreparationResult

    data class Failed(val message: String) : AgentProviderPreparationResult
}

/** UI 与运行时共同使用的固定 Kite Provider 目录端口。 */
interface AgentProviderCatalogApi {
    fun snapshot(target: AgentConfigurationTarget): AgentProviderCatalogSnapshot

    /** 仅 Provider 管理页打开时调用：一次性迁移旧自定义项，并显式更新免费目录。 */
    suspend fun openProviderManager(target: AgentConfigurationTarget): AgentProviderCatalogOpenResult

    /** 升级兼容：只在迁移标记缺失时吸收旧版原生自定义 Provider，不扫描免费或官方目录。 */
    suspend fun migrateLegacyUserProviders(target: AgentConfigurationTarget): List<String>

    fun saveUserProvider(
        target: AgentConfigurationTarget,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
    ): AgentCatalogProvider?

    fun removeUserProvider(target: AgentConfigurationTarget, providerId: String): Boolean

    fun selectModel(target: AgentConfigurationTarget, providerId: String, modelId: String): Boolean

    /** 官方登录成功事件调用；普通刷新路径不得调用。 */
    fun saveOfficialVersion(
        target: AgentConfigurationTarget,
        accountId: String,
        sourceVersion: String,
        providers: List<AgentCatalogProvider>,
    ): AgentProviderCatalogSnapshot

    fun recordMappedControls(target: AgentConfigurationTarget, options: List<AgentConfigOption>)

    /** 发送前读取 Kite 选择并把必要配置翻译到 Agent；页面不调用。 */
    suspend fun prepareSelectedProvider(
        target: AgentConfigurationTarget,
        selection: AgentDraftModelSelection,
    ): AgentProviderPreparationResult
}

/** 兼容既有设置组件的安全投影；事实仍只来自 Kite 目录，不触发 Adapter 读取。 */
fun AgentProviderCatalogSnapshot.toConfigurationProjection(
    target: AgentConfigurationTarget,
): AgentLiveConfigSnapshot = AgentLiveConfigSnapshot(
    agentId = target.agentId,
    adapterId = target.adapterId.orEmpty(),
    revision = "kite:$revision",
    displayLocation = "Kite Provider 目录",
    activeProviderId = selectedProviderId,
    defaultModel = selectedProviderId?.let { providerId ->
        selectedModelId?.let { modelId -> "$providerId/$modelId" }
    },
    providerIds = providers.map(AgentCatalogProvider::id),
    providers = providers.map { provider ->
        com.kite.app.agent.config.AgentProviderSummary(
            id = provider.id,
            displayName = provider.displayName,
            baseUrl = provider.baseUrl,
            models = provider.models.map { model -> AgentProviderModelSummary(model.id, model.displayName) },
            credentialPresence = when {
                provider.policy != AgentProviderCatalogPolicy.UserManaged -> AgentCredentialPresence.NotApplicable
                provider.credentialPresent -> AgentCredentialPresence.Present
                else -> AgentCredentialPresence.Missing
            },
            source = provider.source,
        )
    },
)

/** Adapter 差异只在这一 SDK 实现内出现，UI 和运行时只看统一结果。 */
class StoreBackedAgentProviderCatalogApi(
    private val store: AgentProviderCatalogStore,
    private val adapters: AgentConfigAdapterRegistry,
) : AgentProviderCatalogApi {
    private val preparedFingerprints = ConcurrentHashMap<String, String>()

    override fun snapshot(target: AgentConfigurationTarget): AgentProviderCatalogSnapshot =
        store.snapshot(target.agentId)

    override suspend fun openProviderManager(
        target: AgentConfigurationTarget,
    ): AgentProviderCatalogOpenResult {
        val adapter = adapters.adapter(target.adapterId)
            ?: return AgentProviderCatalogOpenResult(snapshot(target), listOf("当前 Agent 没有可用的配置 Adapter"))
        adapter.sessionPermissionControl()?.option()?.let { option ->
            store.mergeMappedControls(target.agentId, listOf(option))
        }

        val warnings = migrateLegacyUserProviders(target).toMutableList()

        when (val free = adapter.scanFreeProviderCatalog(target.agentId)) {
            is AgentFreeProviderCatalogResult.Ready -> {
                store.syncFreeProviders(
                    target.agentId,
                    free.catalog.sourceId,
                    free.catalog.sourceVersion,
                    free.catalog.providers.map { provider ->
                        provider.toCatalogProvider(
                            policy = AgentProviderCatalogPolicy.FreeScan,
                            source = AgentModelSource.Free,
                            ownerId = free.catalog.sourceId,
                            sourceVersion = free.catalog.sourceVersion,
                        )
                    },
                )
                free.warning?.let(warnings::add)
            }
            is AgentFreeProviderCatalogResult.Failed -> warnings += free.message
            AgentFreeProviderCatalogResult.Unsupported -> Unit
        }
        return AgentProviderCatalogOpenResult(snapshot(target), warnings.distinct())
    }

    override suspend fun migrateLegacyUserProviders(target: AgentConfigurationTarget): List<String> {
        val adapter = adapters.adapter(target.adapterId) ?: return listOf("当前 Agent 没有可用的配置 Adapter")
        val importId = "${adapter.adapterId}:native-provider-v1"
        if (store.hasCompletedImport(target.agentId, importId)) return emptyList()
        return when (val imported = adapter.readUserProviderImport(target.agentId)) {
            is AgentUserProviderImportResult.Ready -> {
                val before = store.snapshot(target.agentId)
                imported.import.providers.forEach { provider ->
                    if (before.providers.none { it.id == provider.id }) {
                        store.saveUserProvider(
                            target.agentId,
                            provider.toCatalogProvider(
                                policy = AgentProviderCatalogPolicy.UserManaged,
                                source = AgentModelSource.UserConfigured,
                            ),
                            imported.import.credentials[provider.id].toCatalogCredentialChange(),
                        )
                        store.markProviderPrepared(target.agentId, provider.id)
                    }
                }
                val selectedProviderId = imported.import.activeProviderId
                val selectedModelId = selectedProviderId?.let { providerId ->
                    imported.import.defaultModel
                        ?.removePrefix("$providerId/")
                        ?.takeIf(String::isNotBlank)
                }
                if (selectedProviderId != null && selectedModelId != null) {
                    store.select(target.agentId, selectedProviderId, selectedModelId)
                }
                store.markImportCompleted(target.agentId, importId)
                emptyList()
            }
            is AgentUserProviderImportResult.Failed -> listOf(imported.message)
            AgentUserProviderImportResult.Unsupported -> {
                store.markImportCompleted(target.agentId, importId)
                emptyList()
            }
        }
    }

    override fun saveUserProvider(
        target: AgentConfigurationTarget,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
    ): AgentCatalogProvider? {
        val saved = store.saveUserProvider(
            target.agentId,
            AgentCatalogProvider(
                id = provider.id,
                displayName = provider.displayName ?: provider.id,
                baseUrl = provider.baseUrl,
                models = provider.models.map { AgentCatalogModel(it.id, it.displayName) },
                source = AgentModelSource.UserConfigured,
                policy = AgentProviderCatalogPolicy.UserManaged,
            ),
            credential.toCatalogCredentialChange(),
        )
        if (saved != null) preparedFingerprints.remove(preparedKey(target, provider.id))
        return saved
    }

    override fun removeUserProvider(target: AgentConfigurationTarget, providerId: String): Boolean {
        val removed = store.removeUserProvider(target.agentId, providerId)
        if (removed) preparedFingerprints.remove(preparedKey(target, providerId))
        return removed
    }

    override fun selectModel(target: AgentConfigurationTarget, providerId: String, modelId: String): Boolean =
        store.select(target.agentId, providerId, modelId)

    override fun saveOfficialVersion(
        target: AgentConfigurationTarget,
        accountId: String,
        sourceVersion: String,
        providers: List<AgentCatalogProvider>,
    ): AgentProviderCatalogSnapshot =
        store.saveOfficialVersion(target.agentId, accountId, sourceVersion, providers)

    override fun recordMappedControls(target: AgentConfigurationTarget, options: List<AgentConfigOption>) {
        store.mergeMappedControls(target.agentId, options)
    }

    override suspend fun prepareSelectedProvider(
        target: AgentConfigurationTarget,
        selection: AgentDraftModelSelection,
    ): AgentProviderPreparationResult {
        val selected = store.snapshot(target.agentId).providers.firstOrNull { provider ->
            provider.id == selection.providerId && provider.models.any { it.id == selection.modelId }
        } ?: return AgentProviderPreparationResult.Failed("Kite 目录中已没有这个模型，请重新选择")
        if (selected.policy != AgentProviderCatalogPolicy.UserManaged) {
            return AgentProviderPreparationResult.Ready()
        }
        val adapter = adapters.adapter(target.adapterId)
            ?: return AgentProviderPreparationResult.Failed("当前 Agent 没有可用的配置 Adapter")
        val credential = store.credential(target.agentId, selected.id)
        val fingerprint = providerFingerprint(selected, credential != null)
        val preparedKey = preparedKey(target, selected.id)
        if (preparedFingerprints[preparedKey] == fingerprint) {
            return AgentProviderPreparationResult.Ready()
        }
        val before = when (val read = adapter.readLive(target.agentId)) {
            is AgentConfigReadResult.Ready -> read.snapshot
            is AgentConfigReadResult.Failed -> return AgentProviderPreparationResult.Failed(read.message)
            is AgentConfigReadResult.Unavailable -> return AgentProviderPreparationResult.Failed(
                read.discovery.warnings.firstOrNull() ?: "当前无法准备 Agent 配置",
            )
        }
        val existing = before.providers.firstOrNull { it.id == selected.id }
        val samePublicConfiguration = existing?.let { native ->
            native.baseUrl == selected.baseUrl &&
                native.models.map { it.id to it.displayName } == selected.models.map { it.id to it.displayName } &&
                native.credentialPresence == if (credential == null) {
                    AgentCredentialPresence.Missing
                } else {
                    AgentCredentialPresence.Present
                }
        } == true
        val pendingNativeWrite = selected.id in store.snapshot(target.agentId).pendingNativeProviderIds
        if (!pendingNativeWrite && samePublicConfiguration) {
            preparedFingerprints[preparedKey] = fingerprint
            return AgentProviderPreparationResult.Ready()
        }
        val change = AgentPersistentConfigChange.ConfigureProvider(
            provider = AgentProviderDraft(
                id = selected.id,
                displayName = selected.displayName,
                baseUrl = selected.baseUrl.orEmpty(),
                models = selected.models.map { AgentProviderModelSummary(it.id, it.displayName) },
            ),
            credential = credential?.let { AgentProviderCredentialChange.replace(it.secret) }
                ?: AgentProviderCredentialChange.Remove,
        )
        return when (val applied = adapter.apply(AgentConfigApplyRequest(target.agentId, before.revision, listOf(change)))) {
            is AgentConfigApplyResult.Applied -> {
                preparedFingerprints[preparedKey] = fingerprint
                store.markProviderPrepared(target.agentId, selected.id)
                AgentProviderPreparationResult.Ready(
                    effect = adapter.providerConfigurationEffect(),
                    nativeConfigurationChanged = true,
                )
            }
            is AgentConfigApplyResult.Conflict -> AgentProviderPreparationResult.Failed(applied.message)
            is AgentConfigApplyResult.Rejected -> AgentProviderPreparationResult.Failed(
                applied.problems.firstOrNull()?.message ?: "Provider 配置未通过 Agent 校验",
            )
            is AgentConfigApplyResult.Unavailable -> AgentProviderPreparationResult.Failed(
                applied.discovery.warnings.firstOrNull() ?: "当前无法准备 Agent 配置",
            )
            is AgentConfigApplyResult.Failed -> AgentProviderPreparationResult.Failed(applied.message)
        }
    }

    private fun preparedKey(target: AgentConfigurationTarget, providerId: String): String =
        "${target.agentId}\u0000${target.adapterId.orEmpty()}\u0000$providerId"

    private fun providerFingerprint(provider: AgentCatalogProvider, credentialPresent: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            provider.id,
            provider.displayName,
            provider.baseUrl.orEmpty(),
            credentialPresent.toString(),
        ).forEach { value -> digest.update(value.toByteArray()); digest.update(0) }
        provider.models.forEach { model ->
            digest.update(model.id.toByteArray())
            digest.update(0)
            digest.update(model.displayName.toByteArray())
            digest.update(0)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun com.kite.app.agent.config.AgentProviderSummary.toCatalogProvider(
        policy: AgentProviderCatalogPolicy,
        source: AgentModelSource,
        ownerId: String? = null,
        sourceVersion: String? = null,
    ): AgentCatalogProvider = AgentCatalogProvider(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
        models = models.map { AgentCatalogModel(it.id, it.displayName) },
        source = source,
        policy = policy,
        ownerId = ownerId,
        sourceVersion = sourceVersion,
        credentialPresent = credentialPresence == AgentCredentialPresence.Present,
    )

    private fun AgentProviderCredentialChange?.toCatalogCredentialChange(): AgentCatalogCredentialChange =
        when (this) {
            null, AgentProviderCredentialChange.Keep -> AgentCatalogCredentialChange.Keep
            AgentProviderCredentialChange.Remove -> AgentCatalogCredentialChange.Remove
            is AgentProviderCredentialChange.Replace -> AgentCatalogCredentialChange.replace(secret)
        }
}
