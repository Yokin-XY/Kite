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

    private class FakeAdapter : AgentConfigAdapter {
        override val adapterId: String = "fake"
        var importCalls = 0
        var freeScanCalls = 0
        var applyCalls = 0
        var bundled: AgentFreeProviderCatalog? = null
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
                providers = providers.values.toList(),
            ),
        )

        override fun validate(request: AgentConfigApplyRequest) = emptyList<com.kite.app.agent.config.AgentConfigValidationProblem>()

        override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult {
            applyCalls++
            val change = request.changes.single() as AgentPersistentConfigChange.ConfigureProvider
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
