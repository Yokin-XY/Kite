package com.kite.app.agent.sdk.configuration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.config.AgentConfigAdapter
import com.kite.app.agent.config.AgentConfigAdapterRegistry
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigDiscovery
import com.kite.app.agent.config.AgentConfigDiscoveryState
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentFreeProviderCatalog
import com.kite.app.agent.config.AgentFreeProviderCatalogResult
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AgentUserProviderImport
import com.kite.app.agent.config.AgentUserProviderImportResult
import com.kite.app.agent.config.AgentWorkModeCatalog
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.store.AgentCatalogModel
import com.kite.app.agent.store.AgentCatalogProvider
import com.kite.app.agent.store.AgentProviderCatalogPolicy
import com.kite.app.agent.store.AgentProviderCatalogStore
import com.kite.app.agent.store.AgentProviderCredentialVault
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentProviderCatalogApiTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val vault = MemoryCredentialVault()
    private val store by lazy { AgentProviderCatalogStore(context, vault) }
    private lateinit var adapter: FakeAdapter
    private lateinit var api: StoreBackedAgentProviderCatalogApi
    private val target = AgentConfigurationTarget("opencode", "fake")

    @Before
    fun setUp() {
        store.resetForTest()
        adapter = FakeAdapter()
        api = StoreBackedAgentProviderCatalogApi(store, AgentConfigAdapterRegistry(listOf(adapter)))
    }

    @After
    fun tearDown() {
        store.resetForTest()
    }

    @Test
    fun `管理页打开只读缓存且人工刷新才扫描免费目录`() = runTest {
        val first = api.openProviderManager(target)
        val second = api.openProviderManager(target)

        assertEquals(0, adapter.importCalls)
        assertEquals(0, adapter.freeScanCalls)
        assertTrue(first.snapshot.providers.isEmpty())
        assertTrue(second.snapshot.providers.isEmpty())

        api.migrateLegacyUserProviders(target)
        api.migrateLegacyUserProviders(target)

        val migrated = api.snapshot(target)
        assertEquals(1, adapter.importCalls)
        assertEquals(AgentModelSource.UserConfigured, migrated.providers.single { it.id == "zhipu" }.source)
        assertEquals("legacy-key", store.credential("opencode", "zhipu")?.secret)
        assertFalse("zhipu" in migrated.pendingNativeProviderIds)

        api.refreshFreeProviderCatalog(target)
        api.refreshFreeProviderCatalog(target)

        assertEquals(2, adapter.freeScanCalls)
        assertEquals(setOf("zhipu", "opencode"), api.snapshot(target).providers.map { it.id }.toSet())
    }

    @Test
    fun `无缓存时使用Adapter随应用发布的免费目录且不会执行扫描`() {
        adapter.bundled = AgentFreeProviderCatalog(
            sourceId = "bundled-free",
            sourceVersion = "app-v1",
            providers = listOf(
                AgentProviderSummary(
                    id = "opencode",
                    displayName = "OpenCode",
                    models = listOf(AgentProviderModelSummary("big-pickle")),
                    source = AgentModelSource.Free,
                ),
            ),
        )

        val snapshot = api.snapshot(target)

        assertEquals(0, adapter.freeScanCalls)
        assertEquals("big-pickle", snapshot.selectedModelId)
        assertEquals("app-v1", snapshot.providers.single().sourceVersion)
    }

    @Test
    fun `启动兼容迁移不会扫描免费或改写官方目录`() = runTest {
        api.saveOfficialVersion(
            target,
            accountId = "official-account",
            sourceVersion = "login-v1",
            providers = listOf(
                AgentCatalogProvider(
                    id = "official",
                    displayName = "官方登录",
                    models = listOf(AgentCatalogModel("official-model")),
                    source = AgentModelSource.OfficialLogin,
                    policy = AgentProviderCatalogPolicy.OfficialLoginVersion,
                ),
            ),
        )

        val warnings = api.migrateLegacyUserProviders(target)

        assertTrue(warnings.isEmpty())
        assertEquals(1, adapter.importCalls)
        assertEquals(0, adapter.freeScanCalls)
        assertEquals("login-v1", api.snapshot(target).providers.single { it.id == "official" }.sourceVersion)
    }

    @Test
    fun `免费更新不会替换Kite保存的官方登录版本`() = runTest {
        api.saveOfficialVersion(
            target,
            accountId = "official-account",
            sourceVersion = "login-v1",
            providers = listOf(
                AgentCatalogProvider(
                    id = "official",
                    displayName = "官方登录",
                    models = listOf(AgentCatalogModel("official-model")),
                    source = AgentModelSource.OfficialLogin,
                    policy = AgentProviderCatalogPolicy.OfficialLoginVersion,
                ),
            ),
        )

        val opened = api.refreshFreeProviderCatalog(target)

        val official = opened.snapshot.providers.single { it.id == "official" }
        assertEquals("login-v1", official.sourceVersion)
        assertEquals(listOf("official-model"), official.models.map { it.id })
    }

    @Test
    fun `协议官方模型目录按真实分组持久化并保留全部模型`() {
        api.recordProtocolOfficialModels(
            target,
            listOf(
                AgentConfigOption.Select(
                    id = "acp.session.model",
                    name = "模型",
                    category = AgentConfigCategory.Model,
                    currentValue = "gemini-3-flash",
                    choices = listOf(
                        AgentConfigChoice(
                            value = "gemini-3-flash",
                            name = "Gemini 3 Flash",
                            groupId = "gemini",
                            groupName = "Google Gemini",
                            modelSource = AgentModelSource.OfficialLogin,
                        ),
                        AgentConfigChoice(
                            value = "gemini/gemini-3-pro",
                            name = "Gemini 3 Pro",
                            groupId = "gemini",
                            groupName = "Google Gemini",
                            modelSource = AgentModelSource.OfficialLogin,
                        ),
                    ),
                )
            ),
        )

        val provider = api.snapshot(target).providers.single()
        assertEquals("gemini", provider.id)
        assertEquals("Google Gemini", provider.displayName)
        assertEquals(listOf("gemini-3-flash", "gemini-3-pro"), provider.models.map { it.id })
        assertEquals(AgentProviderCatalogPolicy.OfficialLoginVersion, provider.policy)
        assertEquals("protocol", provider.ownerId)
    }

    @Test
    fun `保存和选择只改Kite目录发送准备才写Agent`() = runTest {
        api.saveUserProvider(
            target,
            AgentProviderDraft(
                id = "custom",
                displayName = "用户供应商",
                baseUrl = "https://example.com/v1",
                models = listOf(AgentProviderModelSummary("model-a", "模型 A")),
            ),
            AgentProviderCredentialChange.replace("new-key"),
        )
        assertTrue(api.selectModel(target, "custom", "model-a"))
        assertEquals(0, adapter.applyCalls)

        val prepared = api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("custom", "model-a", usesAgentDefault = false),
        ) as AgentProviderPreparationResult.Ready

        assertEquals(1, adapter.applyCalls)
        assertEquals(AgentSessionConfigurationEffect.Reconnect, prepared.effect)
        assertTrue(prepared.nativeConfigurationChanged)
        assertFalse("custom" in api.snapshot(target).pendingNativeProviderIds)

        api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("custom", "model-a", usesAgentDefault = false),
        )
        assertEquals(1, adapter.applyCalls)
    }

    @Test
    fun `官方登录模型只在发送准备时清除自定义Provider覆盖`() = runTest {
        api.saveOfficialVersion(
            target,
            accountId = "chatgpt",
            sourceVersion = "login-v1",
            providers = listOf(
                AgentCatalogProvider(
                    id = "openai",
                    displayName = "OpenAI",
                    models = listOf(AgentCatalogModel("gpt-5.3-codex", "GPT-5.3 Codex")),
                    source = AgentModelSource.OfficialLogin,
                    policy = AgentProviderCatalogPolicy.OfficialLoginVersion,
                )
            ),
        )
        assertTrue(api.selectModel(target, "openai", "gpt-5.3-codex"))
        assertEquals(0, adapter.applyCalls)

        val prepared = api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("openai", "gpt-5.3-codex", usesAgentDefault = false),
        ) as AgentProviderPreparationResult.Ready

        assertEquals(1, adapter.applyCalls)
        assertEquals(AgentPersistentConfigChange.SetDefaultModel("gpt-5.3-codex", clearProviderOverride = true), adapter.lastChange)
        assertEquals(AgentSessionConfigurationEffect.Reconnect, prepared.effect)
        assertTrue(prepared.nativeConfigurationChanged)
        assertEquals(null, adapter.activeProviderId)
        assertEquals("gpt-5.3-codex", adapter.defaultModel)
    }

    @Test
    fun `同一用户Provider换模型会按所选模型更新原生激活值`() = runTest {
        api.saveUserProvider(
            target,
            AgentProviderDraft(
                id = "custom",
                displayName = "用户供应商",
                baseUrl = "https://example.com/v1",
                models = listOf(
                    AgentProviderModelSummary("model-a", "模型 A"),
                    AgentProviderModelSummary("model-b", "模型 B"),
                ),
            ),
            AgentProviderCredentialChange.replace("new-key"),
        )
        assertTrue(api.selectModel(target, "custom", "model-b"))
        api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("custom", "model-b", usesAgentDefault = false),
        )
        val configured = adapter.lastChange as AgentPersistentConfigChange.ConfigureProvider
        assertEquals("model-b", configured.provider.models.first().id)

        assertTrue(api.selectModel(target, "custom", "model-a"))
        api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("custom", "model-a", usesAgentDefault = false),
        )

        assertEquals(2, adapter.applyCalls)
        assertEquals(AgentPersistentConfigChange.SelectProvider("custom", "model-a"), adapter.lastChange)
        assertEquals("custom", adapter.activeProviderId)
        assertEquals("model-a", adapter.defaultModel)
    }

    @Test
    fun `官方与用户Provider来回切换不会误用旧准备缓存`() = runTest {
        api.saveUserProvider(
            target,
            AgentProviderDraft(
                id = "custom",
                displayName = "用户供应商",
                baseUrl = "https://example.com/v1",
                models = listOf(AgentProviderModelSummary("model-a", "模型 A")),
            ),
            AgentProviderCredentialChange.replace("new-key"),
        )
        api.saveOfficialVersion(
            target,
            accountId = "chatgpt",
            sourceVersion = "login-v1",
            providers = listOf(
                AgentCatalogProvider(
                    id = "openai",
                    displayName = "OpenAI",
                    models = listOf(AgentCatalogModel("official-model")),
                    source = AgentModelSource.OfficialLogin,
                    policy = AgentProviderCatalogPolicy.OfficialLoginVersion,
                )
            ),
        )

        api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("custom", "model-a", usesAgentDefault = false),
        )
        api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("openai", "official-model", usesAgentDefault = false),
        )
        api.prepareSelectedProvider(
            target,
            AgentDraftModelSelection("custom", "model-a", usesAgentDefault = false),
        )

        assertEquals(3, adapter.applyCalls)
        assertEquals(AgentPersistentConfigChange.SelectProvider("custom", "model-a"), adapter.lastChange)
        assertEquals("custom", adapter.activeProviderId)
    }

    @Test
    fun `工作模式预设和选择只进入统一目录不会写Agent`() {
        adapter.bundledModes = AgentWorkModeCatalog(
            modes = listOf(AgentMode("build", "Build"), AgentMode("plan", "Plan")),
            defaultModeId = "build",
        )

        val initial = api.snapshot(target)
        assertEquals(listOf("映射-Build", "映射-Plan"), initial.workModes.map { it.name })
        assertEquals("build", initial.selectedWorkModeId)
        assertTrue(api.selectWorkMode(target, "plan"))

        assertEquals("plan", api.snapshot(target).selectedWorkModeId)
        assertEquals(0, adapter.applyCalls)
    }

    private class FakeAdapter : AgentConfigAdapter {
        override val adapterId: String = "fake"
        var importCalls = 0
        var freeScanCalls = 0
        var applyCalls = 0
        var lastChange: AgentPersistentConfigChange? = null
        var activeProviderId: String? = null
        var defaultModel: String? = null
        var bundled: AgentFreeProviderCatalog? = null
        var bundledModes: AgentWorkModeCatalog? = null
        private var revision = 1
        private val providers = linkedMapOf<String, AgentProviderSummary>()

        override fun capabilities(): AgentConfigCapabilities = AgentConfigCapabilities(supported = emptySet())

        override suspend fun readUserProviderImport(agentId: String): AgentUserProviderImportResult {
            importCalls++
            return AgentUserProviderImportResult.Ready(
                AgentUserProviderImport(
                    providers = listOf(
                        AgentProviderSummary(
                            id = "zhipu",
                            displayName = "智谱",
                            baseUrl = "https://example.com/coding",
                            models = listOf(AgentProviderModelSummary("glm-5.2")),
                            credentialPresence = AgentCredentialPresence.Present,
                        ),
                    ),
                    activeProviderId = "zhipu",
                    defaultModel = "zhipu/glm-5.2",
                    credentials = mapOf("zhipu" to AgentProviderCredentialChange.replace("legacy-key")),
                ),
            )
        }

        override fun bundledFreeProviderCatalog(agentId: String): AgentFreeProviderCatalog? = bundled

        override fun bundledWorkModeCatalog(agentId: String): AgentWorkModeCatalog? = bundledModes

        override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
            modes.map { it.copy(name = "映射-${it.name}") }

        override suspend fun scanFreeProviderCatalog(agentId: String): AgentFreeProviderCatalogResult {
            freeScanCalls++
            return AgentFreeProviderCatalogResult.Ready(
                AgentFreeProviderCatalog(
                    sourceId = "fake-free",
                    sourceVersion = "scan-$freeScanCalls",
                    providers = listOf(
                        AgentProviderSummary(
                            id = "opencode",
                            displayName = "OpenCode",
                            models = listOf(AgentProviderModelSummary("free-$freeScanCalls")),
                            credentialPresence = AgentCredentialPresence.NotApplicable,
                            source = AgentModelSource.Free,
                        ),
                    ),
                ),
            )
        }

        override fun providerConfigurationEffect(): AgentSessionConfigurationEffect =
            AgentSessionConfigurationEffect.Reconnect

        override fun defaultModelChange(option: AgentConfigOption.Select): AgentPersistentConfigChange.SetDefaultModel? {
            if (option.category != AgentConfigCategory.Model) return null
            val choice = option.choices.firstOrNull { it.value == option.currentValue } ?: return null
            if (choice.modelSource != AgentModelSource.OfficialLogin) return null
            return AgentPersistentConfigChange.SetDefaultModel(option.currentValue, clearProviderOverride = true)
        }

        override suspend fun discover(agentId: String): AgentConfigDiscovery = AgentConfigDiscovery(
            agentId,
            adapterId,
            AgentConfigDiscoveryState.Ready,
            displayLocation = "/test",
            writable = true,
        )

        override suspend fun readLive(agentId: String): AgentConfigReadResult = AgentConfigReadResult.Ready(
            AgentLiveConfigSnapshot(
                agentId = agentId,
                adapterId = adapterId,
                revision = "r$revision",
                displayLocation = "/test",
                activeProviderId = activeProviderId,
                defaultModel = defaultModel,
                providers = providers.values.toList(),
            ),
        )

        override fun validate(request: AgentConfigApplyRequest) = emptyList<com.kite.app.agent.config.AgentConfigValidationProblem>()

        override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult {
            applyCalls++
            val change = request.changes.single()
            lastChange = change
            when (change) {
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    providers[change.provider.id] = AgentProviderSummary(
                        id = change.provider.id,
                        displayName = change.provider.displayName ?: change.provider.id,
                        baseUrl = change.provider.baseUrl,
                        models = change.provider.models,
                        credentialPresence = when (change.credential) {
                            AgentProviderCredentialChange.Remove -> AgentCredentialPresence.Missing
                            AgentProviderCredentialChange.Keep -> AgentCredentialPresence.Unknown
                            is AgentProviderCredentialChange.Replace -> AgentCredentialPresence.Present
                        },
                    )
                    activeProviderId = change.provider.id
                    defaultModel = change.provider.models.first().id
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    activeProviderId = change.providerId
                    defaultModel = change.modelId
                }
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    if (change.clearProviderOverride) activeProviderId = null
                    defaultModel = change.modelId
                }
                else -> error("测试 Adapter 不支持 $change")
            }
            revision++
            return (readLive(request.agentId) as AgentConfigReadResult.Ready).snapshot.let {
                AgentConfigApplyResult.Applied(it, backupReference = null)
            }
        }
    }

    private class MemoryCredentialVault : AgentProviderCredentialVault {
        private val values = mutableMapOf<String, String>()
        override fun contains(key: String): Boolean = key in values
        override fun put(key: String, secret: String) { values[key] = secret }
        override fun read(key: String): String? = values[key]
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
