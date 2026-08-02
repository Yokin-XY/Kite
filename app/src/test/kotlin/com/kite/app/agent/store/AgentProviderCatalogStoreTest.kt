package com.kite.app.agent.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.contract.AGENT_SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningLevel
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
