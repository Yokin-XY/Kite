package com.kite.app.agent.config.opencode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCommandExecutionResult
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentCoreDocumentListResult
import com.kite.app.agent.config.AgentCoreDocumentReadResult
import com.kite.app.agent.config.AgentCoreDocumentWriteRequest
import com.kite.app.agent.config.AgentCoreDocumentWriteResult
import com.kite.app.agent.config.AgentConfigValue
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentFreeProviderCatalogResult
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpConnectionCheckResult
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.ContainerStatus
import com.kite.app.foundation.runtime.ProotViewLeaseMode
import com.kite.app.foundation.runtime.ProotViewStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
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
class OpenCodeAgentConfigAdapterTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private lateinit var rootfs: File
    private lateinit var configDir: File
    private lateinit var adapter: OpenCodeAgentConfigAdapter

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("kite-opencode-rootfs").toFile()
        configDir = File(rootfs, "root/.config/opencode").also(File::mkdirs)
        adapter = OpenCodeAgentConfigAdapter(
            context = context,
            containerProvider = {
                ContainerRecord(
                    id = "test",
                    displayName = "Test",
                    imageName = "ubuntu",
                    rootfsPath = rootfs.absolutePath,
                    workspacePath = File(rootfs, "workspace").absolutePath,
                    createdAt = 1L,
                    status = ContainerStatus.RUNNING
                )
            }
        )
    }

    @After
    fun tearDown() {
        rootfs.deleteRecursively()
    }

    @Test
    fun exposesAndWritesNativeGlobalAndProjectInstructions() = runTest {
        File(rootfs, "workspace/Kite").mkdirs()
        val documents = adapter.listCoreDocuments("opencode", "/workspace/Kite") as AgentCoreDocumentListResult.Ready

        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.CoreDocuments))
        assertEquals(
            listOf("/root/.config/opencode/AGENTS.md", "/workspace/Kite/AGENTS.md"),
            documents.documents.map { it.displayLocation },
        )
        val read = adapter.readCoreDocument(
            "opencode",
            "opencode-project-agents",
            "/workspace/Kite",
        ) as AgentCoreDocumentReadResult.Ready
        val body = "# Kite project rules"
        val applied = adapter.writeCoreDocument(
            AgentCoreDocumentWriteRequest(
                agentId = "opencode",
                documentId = "opencode-project-agents",
                workspacePath = "/workspace/Kite",
                expectedRevision = read.snapshot.revision,
                content = body,
            )
        ) as AgentCoreDocumentWriteResult.Applied

        assertEquals(body, File(rootfs, "workspace/Kite/AGENTS.md").readText())
        assertFalse(applied.snapshot.toString().contains(body))
    }

    @Test
    fun omitsProjectInstructionsWhenCallerHasNoBoundProject() = runTest {
        val documents = adapter.listCoreDocuments("opencode", null) as AgentCoreDocumentListResult.Ready

        assertEquals(
            listOf("/root/.config/opencode/AGENTS.md"),
            documents.documents.map { it.displayLocation },
        )
        assertTrue(documents.documents.none { it.scope == AgentConfigScope.Project })
    }

    @Test
    fun readsAllGlobalLayersWithoutReadingCredentialContents() = runTest {
        File(configDir, "config.json").writeText(
            """{"model":"base/model","provider":{"base":{"name":"Base"}},"unknown":{"kept":true}}"""
        )
        File(configDir, "opencode.json").writeText(
            """{"model":"middle/model","mcp":{"local":{"type":"local","command":["demo"],"enabled":false}}}"""
        )
        File(configDir, "opencode.jsonc").writeText(
            """
                {
                  // 保留这条注释
                  "model": "last/model",
                  "provider": {"later": {"name": "Later"}},
                  "untouched": {"value": 7},
                }
            """.trimIndent()
        )
        val authFile = File(rootfs, "root/.local/share/opencode/auth.json")
        authFile.parentFile?.mkdirs()
        authFile.writeText("""{"provider":{"type":"api","key":"do-not-read"}}""")
        val skillFile = File(configDir, "skills/demo/SKILL.md")
        skillFile.parentFile?.mkdirs()
        skillFile.writeText("---\nname: demo\ntitle: Demo Skill\n---\n内容")

        val snapshot = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

        assertEquals("/root/.config/opencode/opencode.jsonc", snapshot.displayLocation)
        assertEquals("last/model", snapshot.defaultModel)
        assertEquals(listOf("base", "later"), snapshot.providerIds)
        assertEquals(listOf("base", "later"), snapshot.providers.map { it.id })
        assertTrue(snapshot.providers.all { it.credentialPresence == AgentCredentialPresence.Missing })
        assertEquals("local", snapshot.mcpServers.single().kind)
        assertFalse(snapshot.mcpServers.single().enabled)
        assertEquals(AgentConfigScope.User, snapshot.mcpServers.single().scope)
        assertTrue(AgentMcpOperation.Enable in snapshot.mcpServers.single().allowedOperations)
        assertEquals(listOf("demo"), snapshot.skills.map { it.id })
        assertEquals(AgentSkillActivation.Enabled, snapshot.skills.single().activation)
        assertTrue(AgentSkillOperation.Remove in snapshot.skills.single().allowedOperations)
        assertEquals(AgentCredentialPresence.Present, snapshot.credentialPresence)
        assertFalse(snapshot.toString().contains("do-not-read"))
    }

    @Test
    fun backfillDiscoversSkillAndMcpAddedAfterInitialRead() = runTest {
        val config = File(configDir, "opencode.jsonc").apply { writeText("{}") }
        val initial = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        assertTrue(initial.skills.none { it.id == "late-opencode" })
        assertTrue(initial.mcpServers.none { it.id == "late-opencode" })

        config.writeText(
            """
                {
                  "mcp": {
                    "late-opencode": {
                      "type": "local",
                      "command": ["demo", "serve"],
                      "enabled": true
                    }
                  }
                }
            """.trimIndent(),
        )
        File(configDir, "skills/late-opencode/SKILL.md").apply {
            parentFile?.mkdirs()
            writeText("---\nname: late-opencode\ntitle: Late OpenCode skill\n---\nLate.")
        }

        val refreshed = (adapter.backfill(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

        assertTrue(refreshed.skills.any { it.id == "late-opencode" })
        assertTrue(refreshed.mcpServers.any { it.id == "late-opencode" })
    }

    @Test
    fun usesTheSameOfficialPermissionCatalogForDefaultsAndCurrentSession() = runTest {
        File(configDir, "opencode.jsonc").writeText("{}")

        val snapshot = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        val option = adapter.readSessionConfiguration(AGENT_ID).single() as AgentConfigOption.Select

        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.PermissionProfiles))
        assertEquals(listOf("deny", "ask", "allow"), snapshot.permissionProfiles.map { it.id })
        assertEquals(null, snapshot.activePermissionProfileId)
        assertEquals(SESSION_PERMISSION_CONFIG_ID, option.id)
        assertEquals(AgentConfigCategory.Permission, option.category)
        assertEquals(
            snapshot.permissionProfiles.map { it.id },
            option.choices.map { it.value },
        )
        assertEquals(listOf("受限", "审批", "完全"), option.choices.map { it.name })
        assertEquals("ask", option.currentValue)
        assertTrue(option.description?.contains("只影响当前会话") == true)
    }

    @Test
    fun changesGlobalPermissionActionWhilePreservingSpecificToolRules() = runTest {
        val target = File(configDir, "opencode.jsonc")
        target.writeText(
            """{
                "permission": {
                    "*": "ask",
                    "bash": {
                        "*": "ask",
                        "git status*": "allow",
                        "rm *": "deny"
                    }
                },
                "unknownRoot": {"kept": true}
            }"""
        )
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        assertEquals("ask", before.activePermissionProfileId)

        val applied = adapter.apply(AgentConfigApplyRequest(
            agentId = AGENT_ID,
            expectedRevision = before.revision,
            changes = listOf(AgentPersistentConfigChange.SetPermissionProfile("allow")),
        )) as AgentConfigApplyResult.Applied

        assertEquals("allow", applied.snapshot.activePermissionProfileId)
        val written = target.readText()
        assertTrue(written.contains("\"*\": \"allow\""))
        assertTrue(written.contains("git status*"))
        assertTrue(written.contains("rm *"))
        assertTrue(written.contains("unknownRoot"))
    }

    @Test
    fun appliesMcpAndSkillActivationWithoutReplacingUnknownConfiguration() = runTest {
        val target = File(configDir, "opencode.jsonc")
        target.writeText(
            """
                {
                  "mcp": {
                    "demo-mcp": {
                      "type": "local",
                      "command": ["demo"],
                      "enabled": false,
                      "unknownMcpField": 7
                    }
                  },
                  "permission": {
                    "skill": {
                      "*": "deny",
                      "demo": "ask"
                    }
                  },
                  "unknownRoot": {"kept": true}
                }
            """.trimIndent()
        )
        val skillFile = File(configDir, "skills/demo/SKILL.md")
        skillFile.parentFile?.mkdirs()
        skillFile.writeText("---\nname: demo\ndescription: Demo Skill\n---\n内容")
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

        assertEquals(AgentSkillActivation.ApprovalRequired, before.skills.single().activation)
        assertTrue(AgentMcpOperation.Enable in before.mcpServers.single().allowedOperations)

        val skillChanged = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = before.revision,
                changes = listOf(
                    AgentPersistentConfigChange.SetSkillActivation(
                        "demo",
                        AgentSkillActivation.Disabled
                    )
                )
            )
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentSkillActivation.Disabled, skillChanged.snapshot.skills.single().activation)

        val mcpChanged = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = skillChanged.snapshot.revision,
                changes = listOf(AgentPersistentConfigChange.SetMcpEnabled("demo-mcp", true))
            )
        ) as AgentConfigApplyResult.Applied

        assertTrue(mcpChanged.snapshot.mcpServers.single().enabled)
        assertTrue(AgentMcpOperation.Disable in mcpChanged.snapshot.mcpServers.single().allowedOperations)
        val written = target.readText()
        assertTrue(written.contains("unknownMcpField"))
        assertTrue(written.contains("unknownRoot"))
        assertTrue(written.contains("\"demo\": \"deny\""))
    }

    @Test
    fun configuresMcpFromSafeDraftAndPreservesUnknownPrivateValues() = runTest {
        val target = File(configDir, "opencode.jsonc")
        target.writeText(
            """{
                "mcp": {
                    "demo": {
                        "type": "local",
                        "command": ["old"],
                        "environment": {
                            "PUBLIC_MODE": "literal-kept",
                            "OLD_TOKEN": "{env:OLD_TOKEN}"
                        },
                        "unknownField": 7
                    }
                }
            }"""
        )
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        assertEquals("old", before.mcpServers.single().command)
        assertEquals(listOf("OLD_TOKEN"), before.mcpServers.single().environmentReferences.map { it.name })
        assertFalse(before.toString().contains("literal-kept"))

        val result = adapter.apply(
            AgentConfigApplyRequest(
                AGENT_ID,
                before.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureMcpServer(
                        AgentMcpDraft(
                            id = "demo",
                            transport = AgentMcpTransport.Stdio,
                            command = "npx",
                            arguments = listOf("-y", "demo-mcp"),
                            environmentReferences = listOf(
                                AgentMcpEnvironmentReference("NEW_TOKEN", "DEMO_MCP_TOKEN")
                            )
                        )
                    )
                )
            )
        ) as AgentConfigApplyResult.Applied

        val summary = result.snapshot.mcpServers.single()
        assertEquals(AgentMcpTransport.Stdio, summary.transport)
        assertEquals("npx", summary.command)
        assertEquals(listOf("-y", "demo-mcp"), summary.arguments)
        assertEquals(listOf("NEW_TOKEN"), summary.environmentReferences.map { it.name })
        val written = target.readText()
        assertTrue(written.contains("literal-kept"))
        assertTrue(written.contains("unknownField"))
        assertFalse(written.contains("OLD_TOKEN"))
        assertTrue(written.contains("{env:DEMO_MCP_TOKEN}"))
    }

    @Test
    fun checksOneMcpUsingOpenCodeConnectionStatusWithoutExposingCommandOutput() = runTest {
        File(configDir, "opencode.jsonc").writeText(
            """{"mcp":{"demo":{"type":"remote","url":"https://example.invalid/mcp","enabled":true}}}"""
        )
        val checkingAdapter = OpenCodeAgentConfigAdapter(
            context = context,
            containerProvider = {
                ContainerRecord(
                    id = "test",
                    displayName = "Test",
                    imageName = "ubuntu",
                    rootfsPath = rootfs.absolutePath,
                    workspacePath = File(rootfs, "workspace").absolutePath,
                    createdAt = 1L,
                    status = ContainerStatus.RUNNING
                )
            },
            commandExecutor = { argv, cwd ->
                assertEquals("/workspace", cwd)
                if (argv.contains("models")) {
                    AgentConfigCommandExecutionResult.Completed.of(0, emptyList())
                } else {
                    assertEquals(listOf("opencode", "mcp", "list"), argv)
                    AgentConfigCommandExecutionResult.Completed.of(
                        0,
                        listOf("│  i  ✓ demo connected", "    https://example.invalid/mcp")
                    )
                }
            }
        )

        val snapshot = (checkingAdapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        assertTrue(AgentMcpOperation.CheckConnection in snapshot.mcpServers.single().allowedOperations)
        assertTrue(checkingAdapter.checkMcpServer(AGENT_ID, "demo") is AgentMcpConnectionCheckResult.Available)
    }

    @Test
    fun appliesTargetedJsoncChangesAndPreservesUnknownFieldsAndComments() = runTest {
        val target = File(configDir, "opencode.jsonc")
        target.writeText(
            """
                {
                  // 模型说明必须保留
                  "model": "old/model",
                  "provider": {"custom": {"name": "Original", "unknownOption": 9}},
                  "untouched": {"value": 7},
                }
            """.trimIndent()
        )
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        val request = AgentConfigApplyRequest(
            agentId = AGENT_ID,
            expectedRevision = before.revision,
            changes = listOf(
                AgentPersistentConfigChange.SetDefaultModel("custom/new-model"),
                AgentPersistentConfigChange.PutProvider(
                    providerId = "custom",
                    configuration = AgentConfigValue.ObjectValue(
                        mapOf(
                            "options" to AgentConfigValue.ObjectValue(
                                mapOf(
                                    "apiKey" to AgentConfigValue.EnvironmentReference("CUSTOM_API_KEY"),
                                    "baseURL" to AgentConfigValue.Text("https://example.invalid/v1")
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = adapter.apply(request)
        val text = target.readText()

        assertTrue(result is AgentConfigApplyResult.Applied)
        assertEquals("custom/new-model", (result as AgentConfigApplyResult.Applied).snapshot.defaultModel)
        assertTrue(text.contains("// 模型说明必须保留"))
        assertTrue(text.contains("\"untouched\""))
        assertTrue(text.contains("\"unknownOption\""))
        assertTrue(text.contains("{env:CUSTOM_API_KEY}"))
        assertTrue(text.contains("\"model\": \"custom/new-model\""))
        assertFalse(text.contains("do-not-store"))
        assertTrue(File(configDir, ".kite-backups").listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun externalChangeReturnsConflictAndBackfillReadsNativeValue() = runTest {
        val target = File(configDir, "opencode.jsonc").apply { writeText("""{"model":"before"}""") }
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        target.writeText("""{"model":"external","unknown":true}""")

        val result = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = before.revision,
                changes = listOf(AgentPersistentConfigChange.SetDefaultModel("kite/model"))
            )
        )
        val backfilled = (adapter.backfill(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

        assertTrue(result is AgentConfigApplyResult.Conflict)
        assertEquals("external", backfilled.defaultModel)
        assertTrue(target.readText().contains("unknown"))
    }

    @Test
    fun enabledProotViewDoesNotRedirectAgentConfigurationWithoutExplicitBinding() = runTest {
        val filesRoot = Files.createTempDirectory("kite-opencode-view").toFile()
        try {
            val runtimeRoot = File(filesRoot, "runtime").apply { mkdirs() }
            val viewRootfs = File(runtimeRoot, "containers/ubuntu-main/rootfs").apply { mkdirs() }
            val workspace = File(runtimeRoot, "shared/default").apply { mkdirs() }
            File(runtimeRoot, "proot-runtime.json").writeText(
                """{"capabilities":["${ProotViewStore.RUNTIME_CAPABILITY}"]}"""
            )
            val container = ContainerRecord(
                id = "ubuntu-main",
                displayName = "Ubuntu",
                imageName = "ubuntu",
                rootfsPath = viewRootfs.absolutePath,
                workspacePath = workspace.absolutePath,
                createdAt = 1L,
                status = ContainerStatus.RUNNING
            )
            val store = ProotViewStore.forContainer(container)
            val initial = requireNotNull(store.ensureInitialized().current)
            store.enable()
            val baseTarget = File(viewRootfs, "root/.config/opencode/opencode.jsonc")
            val relative = baseTarget.relativeTo(runtimeRoot).path
            val parentTarget = File(initial.upperRootPath, relative).apply {
                parentFile?.mkdirs()
                writeText(
                    """{"provider":{"custom":{"name":"Parent","options":{"baseURL":"https://old.example/v1"},"models":{"old":{}}}}}"""
                )
            }
            val baseAuth = File(viewRootfs, "root/.local/share/opencode/auth.json")
            val authRelative = baseAuth.relativeTo(runtimeRoot).path
            val parentAuth = File(initial.upperRootPath, authRelative).apply {
                parentFile?.mkdirs()
                writeText("""{"other":{"type":"api","key":"keep-other"}}""")
            }
            val child = store.prepare("agent-configuration")
            store.verify(child.viewId)
            store.acquireLease(child.viewId, "agent-config-test", ProotViewLeaseMode.WRITER)
            store.commit(child.viewId, "agent-config-test")
            store.releaseLease(child.viewId, "agent-config-test")
            val viewAdapter = OpenCodeAgentConfigAdapter(context, containerProvider = { container })
            val before = (viewAdapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

            val result = viewAdapter.apply(
                AgentConfigApplyRequest(
                    agentId = AGENT_ID,
                    expectedRevision = before.revision,
                    changes = listOf(
                        AgentPersistentConfigChange.ConfigureProvider(
                            provider = AgentProviderDraft(
                                id = "custom",
                                displayName = "Current",
                                baseUrl = "https://new.example/v1",
                                models = listOf(AgentProviderModelSummary("new", "New"))
                            ),
                            credential = AgentProviderCredentialChange.replace("new-secret")
                        )
                    )
                )
            )

            assertTrue(result is AgentConfigApplyResult.Applied)
            assertTrue(baseTarget.readText().contains("https://new.example/v1"))
            assertTrue(baseTarget.readText().contains("\"new\""))
            assertTrue(parentTarget.readText().contains("https://old.example/v1"))
            assertFalse(File(child.upperRootPath, relative).exists())
            assertTrue(baseAuth.readText().contains("new-secret"))
            assertFalse(baseAuth.readText().contains("keep-other"))
            assertFalse(parentAuth.readText().contains("new-secret"))
            assertFalse(File(child.upperRootPath, authRelative).exists())
            val after = (viewAdapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
            assertEquals("Current", after.providers.single().displayName)
            assertEquals(listOf("new"), after.providers.single().models.map { it.id })
        } finally {
            filesRoot.deleteRecursively()
        }
    }

    @Test
    fun legacyKiteBaseConfigurationRemainsInBaseWithoutExplicitViewBinding() = runTest {
        val filesRoot = Files.createTempDirectory("kite-opencode-legacy-view").toFile()
        try {
            val runtimeRoot = File(filesRoot, "runtime").apply { mkdirs() }
            val viewRootfs = File(runtimeRoot, "containers/ubuntu-main/rootfs").apply { mkdirs() }
            val workspace = File(runtimeRoot, "shared/default").apply { mkdirs() }
            File(runtimeRoot, "proot-runtime.json").writeText(
                """{"capabilities":["${ProotViewStore.RUNTIME_CAPABILITY}"]}"""
            )
            val container = ContainerRecord(
                id = "ubuntu-main",
                displayName = "Ubuntu",
                imageName = "ubuntu",
                rootfsPath = viewRootfs.absolutePath,
                workspacePath = workspace.absolutePath,
                createdAt = 1L,
                status = ContainerStatus.RUNNING
            )
            val store = ProotViewStore.forContainer(container)
            val initial = requireNotNull(store.ensureInitialized().current)
            store.enable()
            val baseTarget = File(viewRootfs, "root/.config/opencode/opencode.jsonc").apply {
                parentFile?.mkdirs()
                writeText(
                    """{"provider":{"zhipu":{"name":"智谱 GLM","options":{"baseURL":"https://new.example/v1"},"models":{"glm":{"name":"GLM"}}}}}"""
                )
            }
            File(baseTarget.parentFile, ".kite-backups/opencode.jsonc-before.bak").apply {
                parentFile?.mkdirs()
                writeText("{}")
            }
            val baseAuth = File(viewRootfs, "root/.local/share/opencode/auth.json").apply {
                parentFile?.mkdirs()
                writeText("""{"zhipu":{"type":"api","key":"zhipu-secret"}}""")
            }
            val configRelative = baseTarget.relativeTo(runtimeRoot).path
            val authRelative = baseAuth.relativeTo(runtimeRoot).path
            val parentTarget = File(initial.upperRootPath, configRelative).apply {
                parentFile?.mkdirs()
                writeText("""{"provider":{"old":{"models":{"old":{}}}}}""")
            }
            val parentAuth = File(initial.upperRootPath, authRelative).apply {
                parentFile?.mkdirs()
                writeText("""{"other":{"type":"api","key":"keep-other"}}""")
            }
            val child = store.prepare("legacy-agent-configuration")
            store.verify(child.viewId)
            store.acquireLease(child.viewId, "legacy-config-test", ProotViewLeaseMode.WRITER)
            store.commit(child.viewId, "legacy-config-test")
            store.releaseLease(child.viewId, "legacy-config-test")
            val viewAdapter = OpenCodeAgentConfigAdapter(context, containerProvider = { container })

            val first = (viewAdapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

            assertFalse(first.runtimeReloadRequired)
            assertEquals(listOf("zhipu"), first.providerIds)
            assertTrue(first.warnings.none { it.contains("已迁移") })
            assertTrue(baseTarget.readText().contains("智谱 GLM"))
            assertTrue(baseAuth.readText().contains("zhipu-secret"))
            assertFalse(File(child.upperRootPath, configRelative).exists())
            assertFalse(File(child.upperRootPath, authRelative).exists())
            assertTrue(parentTarget.readText().contains("\"old\""))
            assertFalse(parentAuth.readText().contains("zhipu-secret"))

            val second = (viewAdapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
            assertFalse(second.runtimeReloadRequired)
            assertEquals(listOf("zhipu"), second.providerIds)
        } finally {
            filesRoot.deleteRecursively()
        }
    }

    @Test
    fun rejectsPlaintextCredentialButAcceptsEnvironmentReference() = runTest {
        val target = File(configDir, "opencode.jsonc").apply { writeText("{}") }
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        val plaintext = AgentConfigApplyRequest(
            agentId = AGENT_ID,
            expectedRevision = before.revision,
            changes = listOf(
                AgentPersistentConfigChange.PutProvider(
                    "custom",
                    AgentConfigValue.ObjectValue(
                        mapOf("options" to AgentConfigValue.ObjectValue(mapOf("apiKey" to AgentConfigValue.Text("secret"))))
                    )
                )
            )
        )

        val result = adapter.apply(plaintext)

        assertTrue(result is AgentConfigApplyResult.Rejected)
        assertEquals("{}", target.readText())
        assertFalse(result.toString().contains("secret"))
    }

    @Test
    fun configuresProviderAndNativeCredentialInOneSafeTransaction() = runTest {
        val target = File(configDir, "opencode.jsonc").apply {
            writeText(
                """{"provider":{"custom":{"unknown":true,"options":{"timeout":30}}},"kept":7}"""
            )
        }
        val auth = File(rootfs, "root/.local/share/opencode/auth.json").apply {
            parentFile?.mkdirs()
            writeText("""{"other":{"type":"api","key":"keep-other"}}""")
        }
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        val secret = "temporary-test-secret"

        val result = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = before.revision,
                changes = listOf(
                    AgentPersistentConfigChange.ConfigureProvider(
                        provider = AgentProviderDraft(
                            id = "custom",
                            displayName = "Custom Gateway",
                            baseUrl = "https://gateway.example.com/v1",
                            models = listOf(
                                AgentProviderModelSummary("model-a", "Model A"),
                                AgentProviderModelSummary("org/model-b")
                            )
                        ),
                        credential = AgentProviderCredentialChange.replace(secret)
                    )
                )
            )
        )

        assertTrue(result is AgentConfigApplyResult.Applied)
        val applied = result as AgentConfigApplyResult.Applied
        val provider = applied.snapshot.providers.single()
        assertEquals("Custom Gateway", provider.displayName)
        assertEquals("https://gateway.example.com/v1", provider.baseUrl)
        assertEquals(listOf("model-a", "org/model-b"), provider.models.map { it.id })
        assertEquals(AgentCredentialPresence.Present, provider.credentialPresence)
        assertTrue(target.readText().contains("\"unknown\""))
        assertTrue(target.readText().contains("\"timeout\""))
        assertTrue(target.readText().contains("@ai-sdk/openai-compatible"))
        assertTrue(target.readText().contains("\"model-a\""))
        assertTrue(auth.readText().contains(secret))
        assertTrue(auth.readText().contains("keep-other"))
        assertFalse(applied.toString().contains(secret))
        assertTrue(File(configDir, ".kite-backups").isDirectory)
        assertTrue(File(auth.parentFile, ".kite-backups").isDirectory)
    }

    @Test
    fun removesProviderModelsAndCredentialWithoutTouchingOtherAuthEntries() = runTest {
        val target = File(configDir, "opencode.jsonc").apply {
            writeText("""{"provider":{"custom":{"models":{"old":{}}},"kept":{"name":"Kept"}}}""")
        }
        val auth = File(rootfs, "root/.local/share/opencode/auth.json").apply {
            parentFile?.mkdirs()
            writeText("""{"custom":{"type":"api","key":"remove-me"},"kept":{"type":"api","key":"keep-me"}}""")
        }
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

        val result = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = before.revision,
                changes = listOf(AgentPersistentConfigChange.RemoveProvider("custom", removeCredential = true))
            )
        )

        assertTrue(result is AgentConfigApplyResult.Applied)
        assertEquals(listOf("kept"), (result as AgentConfigApplyResult.Applied).snapshot.providerIds)
        assertFalse(target.readText().contains("custom"))
        assertFalse(auth.readText().contains("remove-me"))
        assertTrue(auth.readText().contains("keep-me"))
    }

    @Test
    fun removingMissingCredentialDoesNotCreateEmptyAuthFile() = runTest {
        File(configDir, "opencode.jsonc").writeText("""{"provider":{"custom":{"models":{"old":{}}}}}""")
        val auth = File(rootfs, "root/.local/share/opencode/auth.json")
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

        val result = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = before.revision,
                changes = listOf(AgentPersistentConfigChange.RemoveProvider("custom", removeCredential = true))
            )
        )

        assertTrue(result is AgentConfigApplyResult.Applied)
        assertFalse(auth.exists())
    }

    @Test
    fun rejectsInvalidProviderUrlEmptyModelsAndCredentialWithoutLeakingIt() = runTest {
        File(configDir, "opencode.jsonc").writeText("{}")
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        val secret = "bad\nsecret"
        val request = AgentConfigApplyRequest(
            agentId = AGENT_ID,
            expectedRevision = before.revision,
            changes = listOf(
                AgentPersistentConfigChange.ConfigureProvider(
                    AgentProviderDraft("custom", "Custom", "file:///tmp/socket", emptyList()),
                    AgentProviderCredentialChange.replace(secret)
                )
            )
        )

        val problems = adapter.validate(request)
        val result = adapter.apply(request)

        assertTrue(problems.any { it.field.endsWith("baseUrl") })
        assertTrue(problems.any { it.field.endsWith("models") })
        assertTrue(problems.any { it.field.endsWith("credential") })
        assertTrue(result is AgentConfigApplyResult.Rejected)
        assertFalse(result.toString().contains(secret))
    }

    @Test
    fun installsAndRemovesSkillThroughControlledImportReference() = runTest {
        File(configDir, "opencode.jsonc").writeText("{}")
        val source = File(rootfs, "workspace/.kf/imports/skills/source")
        source.mkdirs()
        File(source, "SKILL.md").writeText("---\nname: imported-skill\ntitle: Imported\n---\n正文")
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot

        val installed = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = before.revision,
                changes = listOf(AgentPersistentConfigChange.InstallSkill("imported-skill", "kite-import:source"))
            )
        )
        assertTrue(installed is AgentConfigApplyResult.Applied)
        val installedSnapshot = (installed as AgentConfigApplyResult.Applied).snapshot
        assertEquals(listOf("imported-skill"), installedSnapshot.skills.map { it.id })

        val removed = adapter.apply(
            AgentConfigApplyRequest(
                agentId = AGENT_ID,
                expectedRevision = installedSnapshot.revision,
                changes = listOf(AgentPersistentConfigChange.RemoveSkill("imported-skill"))
            )
        )
        assertTrue(removed is AgentConfigApplyResult.Applied)
        assertTrue((removed as AgentConfigApplyResult.Applied).snapshot.skills.isEmpty())
        assertTrue(File(configDir, ".kite-skill-backups").listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun noContainerIsReportedWithoutCreatingOne() = runTest {
        val unavailable = OpenCodeAgentConfigAdapter(context, containerProvider = { null })

        val result = unavailable.readLive(AGENT_ID)

        assertTrue(result is AgentConfigReadResult.Unavailable)
    }

    @Test
    fun mapsOnlyVerifiedOpenCodeSessionModelToPersistentDefault() {
        val model = AgentConfigOption.Select(
            id = "model",
            name = "Model",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/big-pickle",
            choices = listOf(AgentConfigChoice("opencode/big-pickle", "Big Pickle"))
        )

        assertEquals(
            AgentPersistentConfigChange.SetDefaultModel("opencode/big-pickle"),
            adapter.defaultModelChange(model)
        )
        assertEquals(
            AgentPersistentConfigChange.SetDefaultModel("opencode/big-pickle"),
            adapter.defaultModelChange(model.copy(id = com.kite.app.agent.config.NATIVE_MODEL_CONFIG_ID))
        )
        assertNull(adapter.defaultModelChange(model.copy(id = "vendor-model")))
        assertNull(adapter.defaultModelChange(model.copy(currentValue = "opencode/not-returned")))
        assertTrue(adapter.validate(AgentConfigApplyRequest(
            agentId = AGENT_ID,
            expectedRevision = "revision",
            changes = listOf(AgentPersistentConfigChange.SetDefaultModel("missing-provider"))
        )).isNotEmpty())
    }

    @Test
    fun selectingProviderWritesNativeDefaultAndMapsTheCurrentSessionChoice() = runTest {
        val target = File(configDir, "opencode.jsonc")
        target.writeText(
            """{
                "model":"opencode/big-pickle",
                "provider":{
                    "zhipu":{"name":"智谱 GLM","models":{"glm-5.2":{"name":"GLM-5.2"}}},
                    "mimo":{"name":"小米 MiMo","models":{"mimo-v2-pro":{}}}
                },
                "unknown":{"kept":true}
            }"""
        )
        val before = (adapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        assertNull(before.activeProviderId)

        val selection = AgentPersistentConfigChange.SelectProvider("zhipu", "glm-5.2")
        val result = adapter.apply(
            AgentConfigApplyRequest(AGENT_ID, before.revision, listOf(selection))
        ) as AgentConfigApplyResult.Applied

        assertEquals("zhipu", result.snapshot.activeProviderId)
        assertEquals("zhipu/glm-5.2", result.snapshot.defaultModel)
        assertTrue(target.readText().contains("\"unknown\""))
        val sessionOption = adapter.normalizeSessionConfiguration(
            listOf(
                AgentConfigOption.Select(
                    id = "model",
                    name = "Model",
                    category = AgentConfigCategory.Model,
                    currentValue = "opencode/big-pickle",
                    choices = listOf(
                        AgentConfigChoice("opencode/big-pickle", "OpenCode Zen/Big Pickle"),
                        AgentConfigChoice("zhipu/glm-5.2", "智谱 GLM/GLM-5.2")
                    )
                )
            )
        )
        assertEquals(
            com.kite.app.agent.config.AgentSessionModelSelection("model", "zhipu/glm-5.2"),
            adapter.sessionModelSelection(selection, sessionOption)
        )
    }

    @Test
    fun normalizesVerifiedOpenCodeProviderModelValuesIntoProviderGroups() {
        val model = AgentConfigOption.Select(
            id = "model",
            name = "Model",
            category = AgentConfigCategory.Model,
            currentValue = "zhipu/glm-5.2",
            choices = listOf(
                AgentConfigChoice("zhipu/glm-5.2", "智谱 GLM/GLM-5.2"),
                AgentConfigChoice("opencode/big-pickle", "OpenCode Zen/Big Pickle")
            )
        )

        val normalized = adapter.normalizeSessionConfiguration(listOf(model))
            .single() as AgentConfigOption.Select

        assertEquals(listOf("zhipu", "opencode"), normalized.choices.map { it.groupId })
        assertEquals(listOf("智谱 GLM", "OpenCode Zen"), normalized.choices.map { it.groupName })
        assertEquals(listOf("GLM-5.2", "Big Pickle"), normalized.choices.map { it.name })
        assertEquals(listOf("zhipu/glm-5.2", "opencode/big-pickle"), normalized.choices.map { it.value })
    }

    @Test
    fun scansPublicModelsOnlyWhenProviderManagerExplicitlyRequestsIt() = runTest {
        val commands = mutableListOf<List<String>>()
        val catalogAdapter = OpenCodeAgentConfigAdapter(
            context = context,
            containerProvider = {
                ContainerRecord(
                    id = "test",
                    displayName = "Test",
                    imageName = "ubuntu",
                    rootfsPath = rootfs.absolutePath,
                    workspacePath = File(rootfs, "workspace").absolutePath,
                    createdAt = 1L,
                    status = ContainerStatus.RUNNING,
                )
            },
            commandExecutor = { argv, cwd ->
                commands += argv
                assertEquals("/workspace", cwd)
                AgentConfigCommandExecutionResult.Completed.of(0, publicCatalogOutput())
            },
        )

        val before = (catalogAdapter.readLive(AGENT_ID) as AgentConfigReadResult.Ready).snapshot
        assertTrue(before.providers.isEmpty())
        assertTrue(commands.isEmpty())

        val scanned = catalogAdapter.scanFreeProviderCatalog(AGENT_ID) as AgentFreeProviderCatalogResult.Ready
        val provider = scanned.catalog.providers.single()
        assertEquals("opencode", provider.id)
        assertEquals(AgentModelSource.Free, provider.source)
        assertEquals(listOf("big-pickle", "mimo-v2.5-free"), provider.models.map { it.id })
        assertTrue(commands.single().containsAll(listOf("--pure", "models", "opencode", "--verbose")))

        catalogAdapter.readSessionConfiguration(AGENT_ID)
        assertEquals(1, commands.size)

        val normalizedAcpOption = catalogAdapter.normalizeSessionConfiguration(
            listOf(
                AgentConfigOption.Select(
                    id = "model",
                    name = "Model",
                    category = AgentConfigCategory.Model,
                    currentValue = "opencode/big-pickle",
                    choices = listOf(
                        AgentConfigChoice("opencode/big-pickle", "OpenCode/Big Pickle"),
                        AgentConfigChoice("zhipu/glm-5.2", "智谱 GLM/GLM-5.2"),
                    ),
                )
            )
        ).single() as AgentConfigOption.Select
        assertEquals(
            listOf(AgentModelSource.Free, null),
            normalizedAcpOption.choices.map { it.modelSource },
        )

        val applied = catalogAdapter.apply(
            AgentConfigApplyRequest(
                AGENT_ID,
                before.revision,
                listOf(AgentPersistentConfigChange.SetDefaultModel("opencode/big-pickle")),
            )
        ) as AgentConfigApplyResult.Applied
        assertNull(applied.snapshot.activeProviderId)
        assertEquals("opencode/big-pickle", applied.snapshot.defaultModel)
        assertTrue(File(configDir, "opencode.jsonc").readText().contains("opencode/big-pickle"))
    }

    @Test
    fun capabilitiesMatchImplementedPersistentOperations() {
        val capabilities = adapter.capabilities()
        val supported = capabilities.supported

        assertTrue(AgentPersistentConfigCapability.DefaultModel in supported)
        assertTrue(AgentPersistentConfigCapability.Provider in supported)
        assertTrue(AgentPersistentConfigCapability.ProviderProfiles in supported)
        assertTrue(AgentPersistentConfigCapability.Mcp in supported)
        assertTrue(AgentPersistentConfigCapability.Skill in supported)
        assertTrue(AgentMcpOperation.Create in capabilities.mcpOperations)
        assertTrue(AgentSkillOperation.Import in capabilities.skillOperations)
    }

    private companion object {
        const val AGENT_ID = "opencode"

        fun publicCatalogOutput(): List<String> = listOf(
            "opencode/big-pickle",
            "{",
            "  \"id\": \"big-pickle\",",
            "  \"providerID\": \"opencode\",",
            "  \"name\": \"Big Pickle\"",
            "}",
            "opencode/mimo-v2.5-free",
            "{",
            "  \"id\": \"mimo-v2.5-free\",",
            "  \"providerID\": \"opencode\",",
            "  \"name\": \"MiMo V2.5 Free\"",
            "}",
        )
    }
}
