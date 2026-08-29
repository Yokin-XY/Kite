package com.kite.app.agent.config.native

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.config.AgentConfigAdapter
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.ContainerStatus
import java.io.File
import java.nio.file.Files
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
class ZhipuCompatibleAgentConfigAdaptersTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private lateinit var rootfs: File

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("kite-zhipu-adapters").toFile()
        File(rootfs, "workspace").mkdirs()
    }

    @After
    fun tearDown() {
        rootfs.deleteRecursively()
    }

    @Test
    fun piWritesNativeModelsJsonAndKeepsSelectionAtSessionScope() = runTest {
        val file = nativeFile("root/.pi/agent/models.json")
        file.writeText("""{"telemetry":{"enabled":false}}""")
        val adapter = PiCodingAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "pi", "zhipu", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5")

        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.Provider))
        assertFalse(adapter.capabilities().supports(AgentPersistentConfigCapability.DefaultModel))
        assertEquals("zhipu", applied.snapshot.providers.single().id)
        assertEquals(null, applied.snapshot.defaultModel)
        assertEquals(AgentCredentialPresence.Present, applied.snapshot.credentialPresence)
        assertTrue(file.readText().contains("openai-completions"))
        assertTrue(file.readText().contains("telemetry"))
        assertFalse(applied.snapshot.toString().contains(SECRET))

        val selected = adapter.apply(
            AgentConfigApplyRequest(
                "pi",
                applied.snapshot.revision,
                listOf(AgentPersistentConfigChange.SelectProvider("zhipu", "glm-5")),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(applied.snapshot.revision, selected.snapshot.revision)
    }

    @Test
    fun qwenWritesDocumentedModelProvidersWithoutLosingMcp() = runTest {
        val file = nativeFile("root/.qwen/settings.json")
        file.writeText("""{"ui":{"theme":"dark"},"mcpServers":{"docs":{"command":"demo"}}}""")
        val adapter = QwenCodeAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "qwen", "zhipu", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5")

        assertEquals("zhipu", applied.snapshot.activeProviderId)
        assertEquals("glm-5", applied.snapshot.defaultModel)
        assertEquals("docs", applied.snapshot.mcpServers.single().id)
        assertEquals(AgentCredentialPresence.Present, applied.snapshot.credentialPresence)
        val text = file.readText()
        assertTrue(text.contains("modelProviders"))
        assertTrue(text.contains("providerProtocol"))
        assertTrue(text.contains("selectedType"))
        assertTrue(text.contains("theme"))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun reasonixWritesProviderTomlAndCredentialToNativeDotenv() = runTest {
        val config = nativeFile("root/.reasonix/config.toml")
        config.writeText("log_level = \"debug\"\n")
        val environment = nativeFile("root/.reasonix/.env")
        environment.writeText("KEEP_ME=yes\n")
        val adapter = ReasonixAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "reasonix", "zhipu", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5")

        assertEquals("zhipu", applied.snapshot.activeProviderId)
        assertEquals("zhipu/glm-5", applied.snapshot.defaultModel)
        assertEquals(AgentCredentialPresence.Present, applied.snapshot.credentialPresence)
        assertTrue(config.readText().contains("[[providers]]"))
        assertTrue(config.readText().contains("log_level"))
        assertTrue(environment.readText().contains("KEEP_ME=yes"))
        assertTrue(environment.readText().contains(SECRET))
        assertFalse(config.readText().contains(SECRET))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun kimiWritesProviderAndModelTomlWithoutLosingMcp() = runTest {
        val config = nativeFile("root/.kimi-code/config.toml")
        config.writeText("work_dir = \"/workspace\"\n")
        nativeFile("root/.kimi-code/mcp.json").writeText(
            """{"mcpServers":{"docs":{"command":"demo","args":[]}}}""",
        )
        val adapter = KimiCodeAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "kimi", "zhipu", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5")

        assertEquals("zhipu", applied.snapshot.activeProviderId)
        assertEquals("zhipu/glm-5", applied.snapshot.defaultModel)
        assertEquals("docs", applied.snapshot.mcpServers.single().id)
        assertEquals(AgentCredentialPresence.Present, applied.snapshot.credentialPresence)
        val text = config.readText()
        assertTrue(text.contains("[providers.zhipu]"))
        assertTrue(text.contains("[models.\"zhipu/glm-5\"]"))
        assertTrue(text.contains("max_context_size = 128000"))
        assertTrue(text.contains("work_dir"))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    private suspend fun configure(
        adapter: AgentConfigAdapter,
        agentId: String,
        providerId: String,
        baseUrl: String,
        modelId: String,
    ): AgentConfigApplyResult.Applied {
        val before = (adapter.readLive(agentId) as AgentConfigReadResult.Ready).snapshot
        val result = adapter.apply(
            AgentConfigApplyRequest(
                agentId,
                before.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureProvider(
                        AgentProviderDraft(
                            providerId,
                            "智谱 Coding Plan",
                            baseUrl,
                            listOf(AgentProviderModelSummary(modelId, "GLM-5")),
                        ),
                        AgentProviderCredentialChange.replace(SECRET),
                    ),
                ),
            ),
        )
        assertTrue(result.toString(), result is AgentConfigApplyResult.Applied)
        return result as AgentConfigApplyResult.Applied
    }

    private fun nativeFile(relative: String): File = File(rootfs, relative).also { it.parentFile?.mkdirs() }

    private fun container(): ContainerRecord = ContainerRecord(
        id = "test",
        displayName = "Test",
        imageName = "ubuntu",
        rootfsPath = rootfs.absolutePath,
        workspacePath = File(rootfs, "workspace").absolutePath,
        createdAt = 1L,
        status = ContainerStatus.RUNNING,
    )

    companion object {
        private const val SECRET = "zhipu-test-secret"
    }
}
