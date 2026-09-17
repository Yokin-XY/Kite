package com.kite.app.agent.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.contract.AGENT_SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.config.AgentProviderCatalogSyncMetadata
import com.kite.app.agent.sdk.configuration.AgentControlCatalogProjector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentProviderCatalogStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val vault = MemoryCredentialVault()
    private val store by lazy { AgentProviderCatalogStore(context, vault) }

    @Before
    fun setUp() {
        store.resetForTest()
    }

    @After
    fun tearDown() {
        store.resetForTest()
    }

    @Test
    fun `自定义Provider和模型由Kite保存且公开目录不包含API Key`() {
        val secret = "secret-do-not-print"

        val saved = store.saveUserProvider(
            "opencode",
            provider("zhipu", "智谱", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5.2"),
            AgentCatalogCredentialChange.replace(secret),
        )

        assertEquals("zhipu", saved?.id)
        val snapshot = store.snapshot("opencode")
        val provider = snapshot.providers.single()
        assertEquals(AgentModelSource.UserConfigured, provider.source)
        assertEquals(AgentProviderCatalogPolicy.UserManaged, provider.policy)
        assertEquals("glm-5.2", provider.models.single().id)
        assertTrue(provider.credentialPresent)
        assertEquals(secret, store.credential("opencode", "zhipu")?.secret)
        assertFalse(snapshot.toString().contains(secret))
        assertFalse(
            context.getSharedPreferences("kite_agent_provider_catalog", Context.MODE_PRIVATE)
                .all
                .values
                .any { it.toString().contains(secret) },
        )
    }

    @Test
    fun `供应商目录绑定和用户排除记录会随Provider持久化`() {
        val sync = AgentProviderCatalogSyncMetadata(
            presetId = "zhipu-coding-plan",
            catalogModelIds = setOf("glm-5.3-flash", "glm-5.2"),
            suppressedModelIds = setOf("glm-5.2"),
        )

        store.saveUserProvider(
            "hermes",
            provider("zhipu", "智谱", "https://example.com/v1", "glm-5.3-flash")
                .copy(catalogSync = sync),
            AgentCatalogCredentialChange.Keep,
        )

        val restored = AgentProviderCatalogStore(context, vault).snapshot("hermes").providers.single()
        assertEquals(sync, restored.catalogSync)
    }

    @Test
    fun `免费扫描只增量替换自己的目录并删除已下线模型`() {
        store.saveUserProvider(
            "opencode",
            provider("zhipu", "智谱", "https://example.com/v1", "glm-5.2"),
            AgentCatalogCredentialChange.Keep,
        )
        store.syncFreeProviders(
            "opencode",
            scanSourceId = "opencode-cli",
            sourceVersion = "scan-1",
            providers = listOf(provider("opencode", "OpenCode", null, "old-free")),
        )
        assertTrue(store.select("opencode", "opencode", "old-free"))

        store.syncFreeProviders(
            "opencode",
            scanSourceId = "opencode-cli",
            sourceVersion = "scan-2",
            providers = listOf(provider("opencode", "OpenCode", null, "new-free")),
        )

        val snapshot = store.snapshot("opencode")
        assertEquals(setOf("zhipu", "opencode"), snapshot.providers.map { it.id }.toSet())
        assertEquals(listOf("new-free"), snapshot.providers.single { it.id == "opencode" }.models.map { it.id })
        assertEquals("scan-2", snapshot.providers.single { it.id == "opencode" }.sourceVersion)
        assertNull(snapshot.selectedProviderId)
        assertNull(snapshot.selectedModelId)
    }

    @Test
    fun `首次免费预设立即建立默认选择且不能覆盖已有扫描版本`() {
        val first = store.seedFreeProvidersIfAbsent(
            "opencode",
            sourceId = "opencode-public",
            sourceVersion = "bundled-v1",
            providers = listOf(provider("opencode", "OpenCode", null, "big-pickle")),
        )

        assertEquals("opencode", first.selectedProviderId)
        assertEquals("big-pickle", first.selectedModelId)
        assertEquals("bundled-v1", first.providers.single().sourceVersion)

        val unchanged = store.seedFreeProvidersIfAbsent(
            "opencode",
            sourceId = "opencode-public",
            sourceVersion = "bundled-v2",
            providers = listOf(provider("opencode", "OpenCode", null, "different")),
        )

        assertEquals("bundled-v1", unchanged.providers.single().sourceVersion)
        assertEquals(listOf("big-pickle"), unchanged.providers.single().models.map { it.id })
    }

    @Test
    fun `官方目录只在保存登录版本时替换且不受免费扫描影响`() {
        val v1 = store.saveOfficialVersion(
            "codex",
            accountId = "chatgpt",
            sourceVersion = "login-v1",
            providers = listOf(provider("openai", "OpenAI", null, "gpt-5.6")),
        )
        assertEquals(1L, v1.revision)

        store.syncFreeProviders(
            "codex",
            scanSourceId = "free-probe",
            sourceVersion = "scan-v2",
            providers = listOf(provider("free", "免费", null, "small")),
        )

        val unchangedOfficial = store.snapshot("codex").providers.single { it.id == "openai" }
        assertEquals("login-v1", unchangedOfficial.sourceVersion)
        assertEquals(listOf("gpt-5.6"), unchangedOfficial.models.map { it.id })

        store.saveOfficialVersion(
            "codex",
            accountId = "chatgpt",
            sourceVersion = "login-v2",
            providers = listOf(provider("openai", "OpenAI", null, "gpt-6")),
        )

        val replaced = store.snapshot("codex").providers.single { it.id == "openai" }
        assertEquals("login-v2", replaced.sourceVersion)
        assertEquals(listOf("gpt-6"), replaced.models.map { it.id })
    }

    @Test
    fun `权限和推理映射持久化后仍保留统一语义`() {
        store.replaceMappedControls(
            "opencode",
            listOf(
                AgentConfigOption.Select(
                    id = AGENT_SESSION_PERMISSION_CONFIG_ID,
                    name = "权限",
                    category = AgentConfigCategory.Permission,
                    currentValue = "ask",
                    choices = listOf(
                        AgentConfigChoice("deny", "受限", permission = AgentPermissionLevel.Restricted),
                        AgentConfigChoice("ask", "审批", permission = AgentPermissionLevel.Approval),
                        AgentConfigChoice("allow", "完全", permission = AgentPermissionLevel.Full),
                    ),
                ),
                AgentConfigOption.Select(
                    id = "effort",
                    name = "推理强度",
                    category = AgentConfigCategory.ThoughtLevel,
                    currentValue = "medium",
                    choices = listOf(
                        AgentConfigChoice("low", "低", reasoning = AgentReasoningLevel.Low),
                        AgentConfigChoice("medium", "中", reasoning = AgentReasoningLevel.Medium),
                        AgentConfigChoice("high", "高", reasoning = AgentReasoningLevel.High),
                    ),
                ),
            ),
        )

        val restored = AgentProviderCatalogStore(context, vault).snapshot("opencode")
        val projected = AgentControlCatalogProjector.project(restored.controls)

        assertEquals(
            listOf(AgentPermissionLevel.Restricted, AgentPermissionLevel.Approval, AgentPermissionLevel.Full),
            projected.permission?.choices?.map { it.level },
        )
        assertEquals(
            listOf(AgentReasoningLevel.Low, AgentReasoningLevel.Medium, AgentReasoningLevel.High),
            (projected.reasoning as? com.kite.app.agent.sdk.configuration.AgentReasoningControlCatalog.Select)
                ?.choices
                ?.map { it.semantics },
        )
    }

    @Test
    fun `权限最近选择持久化且能力目录更新不会把它改回Agent默认值`() {
        fun permission(current: String) = AgentConfigOption.Select(
            id = AGENT_SESSION_PERMISSION_CONFIG_ID,
            name = "权限",
            category = AgentConfigCategory.Permission,
            currentValue = current,
            choices = listOf(
                AgentConfigChoice("deny", "受限", permission = AgentPermissionLevel.Restricted),
                AgentConfigChoice("ask", "审批", permission = AgentPermissionLevel.Approval),
                AgentConfigChoice("allow", "完全", permission = AgentPermissionLevel.Full),
            ),
        )
        store.replaceMappedControls("opencode", listOf(permission("ask")))

        assertTrue(store.selectControl(
            "opencode",
            AGENT_SESSION_PERMISSION_CONFIG_ID,
            AgentConfigValue.Select("allow"),
        ))
        store.mergeMappedControls("opencode", listOf(permission("ask")))

        val restored = AgentProviderCatalogStore(context, vault).snapshot("opencode")
        assertEquals(
            "allow",
            (restored.controls.single() as AgentConfigOption.Select).currentValue,
        )
        assertFalse(store.selectControl(
            "opencode",
            AGENT_SESSION_PERMISSION_CONFIG_ID,
            AgentConfigValue.Select("missing"),
        ))
    }

    @Test
    fun `多Store更新不同字段时基于最新持久化快照合并`() {
        val otherStore = AgentProviderCatalogStore(context, vault)
        store.replaceMappedControls("opencode", listOf(permissionControl("ask")))
        assertEquals("ask", otherStore.snapshot("opencode").permissionValue())

        assertTrue(store.selectControl(
            "opencode",
            AGENT_SESSION_PERMISSION_CONFIG_ID,
            AgentConfigValue.Select("allow"),
        ))
        otherStore.replaceWorkModes(
            "opencode",
            modes = listOf(AgentMode("build", "执行")),
            currentModeId = "build",
        )

        val restored = AgentProviderCatalogStore(context, vault).snapshot("opencode")
        assertEquals("allow", restored.permissionValue())
        assertEquals("build", restored.selectedWorkModeId)
    }

    @Test
    fun `多Store缓存会在其他实例写入后刷新`() {
        val otherStore = AgentProviderCatalogStore(context, vault)
        store.replaceMappedControls("opencode", listOf(permissionControl("ask")))
        assertEquals("ask", otherStore.snapshot("opencode").permissionValue())

        assertTrue(store.selectControl(
            "opencode",
            AGENT_SESSION_PERMISSION_CONFIG_ID,
            AgentConfigValue.Select("allow"),
        ))

        assertEquals("allow", otherStore.snapshot("opencode").permissionValue())
    }

    @Test
    fun `局部能力更新不会删除另一类固定控件且原生导入只需完成一次`() {
        store.replaceMappedControls(
            "opencode",
            listOf(
                AgentConfigOption.Select(
                    id = AGENT_SESSION_PERMISSION_CONFIG_ID,
                    name = "权限",
                    category = AgentConfigCategory.Permission,
                    currentValue = "ask",
                    choices = listOf(AgentConfigChoice("ask", "审批", permission = AgentPermissionLevel.Approval)),
                ),
            ),
        )
        store.mergeMappedControls(
            "opencode",
            listOf(
                AgentConfigOption.Select(
                    id = "effort",
                    name = "推理强度",
                    category = AgentConfigCategory.ThoughtLevel,
                    currentValue = "high",
                    choices = listOf(AgentConfigChoice("high", "高", reasoning = AgentReasoningLevel.High)),
                ),
            ),
        )
        store.markImportCompleted("opencode", "opencode-config-v1")

        val restored = AgentProviderCatalogStore(context, vault).snapshot("opencode")
        assertEquals(
            setOf(AgentConfigCategory.Permission, AgentConfigCategory.ThoughtLevel),
            restored.controls.mapNotNull(AgentConfigOption::category).toSet(),
        )
        assertTrue(store.hasCompletedImport("opencode", "opencode-config-v1"))
    }

    @Test
    fun `工作模式目录和草稿选择持久化且目录更新只淘汰失效选择`() {
        val seeded = store.seedWorkModesIfAbsent(
            "opencode",
            modes = listOf(AgentMode("build", "执行"), AgentMode("plan", "规划")),
            defaultModeId = "build",
        )
        assertEquals("build", seeded.selectedWorkModeId)
        assertTrue(store.selectWorkMode("opencode", "plan"))

        val restored = AgentProviderCatalogStore(context, vault).snapshot("opencode")
        assertEquals(listOf("build", "plan"), restored.workModes.map { it.id })
        assertEquals("plan", restored.selectedWorkModeId)

        val updated = store.replaceWorkModes(
            "opencode",
            modes = listOf(AgentMode("build", "执行新版"), AgentMode("plan", "规划新版")),
            currentModeId = "build",
        )
        assertEquals("plan", updated.selectedWorkModeId)
        assertEquals("执行新版", updated.workModes.first().name)

        val removed = store.replaceWorkModes(
            "opencode",
            modes = listOf(AgentMode("build", "执行")),
            currentModeId = "build",
        )
        assertEquals("build", removed.selectedWorkModeId)
        assertFalse(store.selectWorkMode("opencode", "missing"))
    }

    private fun provider(
        id: String,
        name: String,
        url: String?,
        vararg models: String,
    ) = AgentCatalogProvider(
        id = id,
        displayName = name,
        baseUrl = url,
        models = models.map { AgentCatalogModel(it, it) },
        source = AgentModelSource.UserConfigured,
        policy = AgentProviderCatalogPolicy.UserManaged,
    )

    private fun permissionControl(current: String) = AgentConfigOption.Select(
        id = AGENT_SESSION_PERMISSION_CONFIG_ID,
        name = "权限",
        category = AgentConfigCategory.Permission,
        currentValue = current,
        choices = listOf(
            AgentConfigChoice("deny", "受限", permission = AgentPermissionLevel.Restricted),
            AgentConfigChoice("ask", "审批", permission = AgentPermissionLevel.Approval),
            AgentConfigChoice("allow", "完全", permission = AgentPermissionLevel.Full),
        ),
    )

    private fun AgentProviderCatalogSnapshot.permissionValue(): String? =
        (controls.single { it.id == AGENT_SESSION_PERMISSION_CONFIG_ID } as AgentConfigOption.Select).currentValue

    private class MemoryCredentialVault : AgentProviderCredentialVault {
        private val values = mutableMapOf<String, String>()

        override fun contains(key: String): Boolean = key in values
        override fun put(key: String, secret: String) {
            values[key] = secret
        }
        override fun read(key: String): String? = values[key]
        override fun remove(key: String) {
            values.remove(key)
        }
        override fun clear() {
            values.clear()
        }
    }
}
