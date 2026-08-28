package com.kite.app.agent.config.native

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.config.AgentConfigAdapter
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentCoreDocumentListResult
import com.kite.app.agent.config.AgentCoreDocumentReadResult
import com.kite.app.agent.config.AgentCoreDocumentSemantics
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.normalizePublishedSessionConfiguration
import com.kite.app.agent.config.AgentSessionConfigurationApplyResult
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AgentSessionModelSelection
import com.kite.app.agent.config.AgentCoreDocumentWriteRequest
import com.kite.app.agent.config.AgentCoreDocumentWriteResult
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentUserProviderImportResult
import com.kite.app.agent.config.NATIVE_MODEL_CONFIG_ID
import com.kite.app.agent.config.SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
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
class NativeAgentConfigAdaptersTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private lateinit var rootfs: File

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("kite-native-agent-config").toFile()
        File(rootfs, "workspace").mkdirs()
    }

    @After
    fun tearDown() {
        rootfs.deleteRecursively()
    }

    @Test
    fun coreDocumentsKeepEachAgentNativePathsAndSemantics() = runTest {
        nativeFile("root/.openclaw/openclaw.json").writeText(
            """{ agents: { defaults: { workspace: "/workspace/openclaw-home" } } }"""
        )
        val workspace = "/workspace/kite"
        val cases = listOf(
            "kimi" to KimiCodeAgentConfigAdapter(context, ::container),
            "mimo" to MiMoCodeAgentConfigAdapter(context, ::container),
            "openclaw" to OpenClawAgentConfigAdapter(context, ::container),
            "claude" to ClaudeCodeAgentConfigAdapter(context, ::container),
            "codex" to CodexAgentConfigAdapter(context, ::container),
            "hermes" to HermesAgentConfigAdapter(context, ::container),
        )

        val documents = cases.associate { (agentId, adapter) ->
            assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.CoreDocuments))
            val result = adapter.listCoreDocuments(agentId, workspace) as AgentCoreDocumentListResult.Ready
            agentId to result.documents
        }

        assertEquals(
            AgentCoreDocumentSemantics.FullSystemPromptReplacement,
            documents.getValue("kimi").single { it.fileName == "SYSTEM.md" }.semantics,
        )
        assertTrue(documents.getValue("kimi").single { it.fileName == "SYSTEM.md" }.warning?.contains("完整替换") == true)
        assertEquals(
            "/workspace/openclaw-home/SOUL.md",
            documents.getValue("openclaw").single { it.fileName == "SOUL.md" }.displayLocation,
        )
        assertEquals(
            "/root/.claude/CLAUDE.md",
            documents.getValue("claude").single { it.id == "claude-global" }.displayLocation,
        )
        assertEquals(
            "/root/.codex/AGENTS.override.md",
            documents.getValue("codex").single { it.id == "codex-global-override" }.displayLocation,
        )
        assertEquals(
            "/workspace/.kf/software/kite.hermes.core/home/SOUL.md",
            documents.getValue("hermes").single { it.id == "hermes-soul" }.displayLocation,
        )
        assertEquals(
            "/workspace/kite/AGENTS.md",
            documents.getValue("mimo").single { it.id == "mimo-project-agents" }.displayLocation,
        )
    }

    @Test
    fun unboundSettingsDoNotInventProjectDocuments() = runTest {
        nativeFile("root/.openclaw/openclaw.json").writeText(
            """{ agents: { defaults: { workspace: "/workspace/openclaw-home" } } }"""
        )
        val cases = listOf(
            "kimi" to KimiCodeAgentConfigAdapter(context, ::container),
            "mimo" to MiMoCodeAgentConfigAdapter(context, ::container),
            "openclaw" to OpenClawAgentConfigAdapter(context, ::container),
            "claude" to ClaudeCodeAgentConfigAdapter(context, ::container),
            "codex" to CodexAgentConfigAdapter(context, ::container),
            "hermes" to HermesAgentConfigAdapter(context, ::container),
        )

        cases.forEach { (agentId, adapter) ->
            val result = adapter.listCoreDocuments(agentId, null) as AgentCoreDocumentListResult.Ready
            assertTrue("$agentId 不应凭空生成项目文档", result.documents.none { it.scope == AgentConfigScope.Project })
        }
        val openClaw = cases.single { it.first == "openclaw" }.second
            .listCoreDocuments("openclaw", null) as AgentCoreDocumentListResult.Ready
        assertTrue(openClaw.documents.all { it.scope == AgentConfigScope.Workspace })
    }

    @Test
    fun backfillDiscoversSkillsAndMcpAddedAfterInitialReadForEveryNativeAdapter() = runTest {
        val kimiMcp = nativeFile("root/.kimi-code/mcp.json").apply { writeText("{}") }
        val mimoConfig = nativeFile("root/.config/mimocode/mimocode.jsonc").apply { writeText("{}") }
        val openClawConfig = nativeFile("root/.openclaw/openclaw.json").apply { writeText("{}") }
        nativeFile("root/.claude/settings.json").writeText("{}")
        val claudeState = nativeFile("root/.claude.json").apply { writeText("{}") }
        val codexConfig = nativeFile("root/.codex/config.toml").apply { writeText("") }
        val hermesConfig = nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml")
            .apply { writeText("") }
        val cases = listOf(
            Triple("kimi", KimiCodeAgentConfigAdapter(context, ::container), "late-kimi"),
            Triple("mimo", MiMoCodeAgentConfigAdapter(context, ::container), "late-mimo"),
            Triple("openclaw", OpenClawAgentConfigAdapter(context, ::container), "late-openclaw"),
            Triple("claude-code", ClaudeCodeAgentConfigAdapter(context, ::container), "late-claude"),
            Triple("codex", CodexAgentConfigAdapter(context, ::container), "late-codex"),
            Triple("hermes", HermesAgentConfigAdapter(context, ::container), "late-hermes"),
        )

        cases.forEach { (agentId, adapter, marker) ->
            val initial = (adapter.readLive(agentId) as AgentConfigReadResult.Ready).snapshot
            assertTrue("$agentId 初次读取不应预先出现测试 Skill", initial.skills.none { it.id == marker })
            assertTrue("$agentId 初次读取不应预先出现测试 MCP", initial.mcpServers.none { it.id == marker })
        }

        kimiMcp.writeText(
            """{"mcpServers":{"late-kimi":{"command":"demo","args":["serve"]}}}""",
        )
        nativeFile("root/.kimi-code/skills/late-kimi/SKILL.md").writeText(
            "---\nname: late-kimi\ndescription: Late Kimi skill\n---\nLate.",
        )

        mimoConfig.writeText(
            """{"mcp":{"late-mimo":{"type":"local","command":["demo","serve"]}}}""",
        )
        nativeFile("root/.config/mimocode/skills/late-mimo/SKILL.md").writeText(
            "---\nname: late-mimo\ndescription: Late MiMo skill\n---\nLate.",
        )

        openClawConfig.writeText(
            """{mcp:{servers:{"late-openclaw":{command:"demo",args:["serve"]}}}}""",
        )
        nativeFile("root/.openclaw/skills/late-openclaw/SKILL.md").writeText(
            "---\nname: late-openclaw\ndescription: Late OpenClaw skill\n---\nLate.",
        )

        claudeState.writeText(
            """{"mcpServers":{"late-claude":{"type":"stdio","command":"demo","args":["serve"]}}}""",
        )
        nativeFile("root/.claude/skills/late-claude/SKILL.md").writeText(
            "---\nname: late-claude\ndescription: Late Claude skill\n---\nLate.",
        )

        codexConfig.writeText(
            """
                [mcp_servers.late-codex]
                command = "demo"
                args = ["serve"]
            """.trimIndent(),
        )
        nativeFile("root/.codex/skills/late-codex/SKILL.md").writeText(
            "---\nname: late-codex\ndescription: Late Codex skill\n---\nLate.",
        )

        hermesConfig.writeText(
            """
                mcp_servers:
                  late-hermes:
                    command: demo
                    args: [serve]
            """.trimIndent(),
        )
        nativeFile("workspace/.kf/software/kite.hermes.core/home/skills/late-hermes/SKILL.md").writeText(
            "---\nname: late-hermes\ndescription: Late Hermes skill\n---\nLate.",
        )

        cases.forEach { (agentId, adapter, marker) ->
            val refreshed = (adapter.backfill(agentId) as AgentConfigReadResult.Ready).snapshot
            assertTrue("$agentId 应在再次读取后发现新 Skill", refreshed.skills.any { it.id == marker })
            assertTrue("$agentId 应在再次读取后发现新 MCP", refreshed.mcpServers.any { it.id == marker })
        }
    }

    @Test
    fun protocolAdaptersDiscoverTheirNativeAndCompatibleGlobalSkillRoots() = runTest {
        nativeFile("root/.qwen/skills/duplicate/SKILL.md").writeText(
            "---\nname: duplicate\ntitle: Qwen Duplicate\n---\nQwen.",
        )
        nativeFile("root/.reasonix/skills/duplicate/SKILL.md").writeText(
            "---\nname: duplicate\ntitle: Reasonix Duplicate\n---\nReasonix.",
        )
        nativeFile("root/.agents/skills/duplicate/SKILL.md").writeText(
            "---\nname: duplicate\ntitle: Shared Duplicate\n---\nShared.",
        )
        nativeFile("root/.agents/skills/shared/SKILL.md").writeText(
            "---\nname: shared\ntitle: Shared\n---\nShared.",
        )
        nativeFile("root/.agent/skills/agent-compat/SKILL.md").writeText(
            "---\nname: agent-compat\ntitle: Agent Compatibility\n---\nAgent.",
        )
        nativeFile("root/.claude/skills/claude-compat/SKILL.md").writeText(
            "---\nname: claude-compat\ntitle: Claude Compatibility\n---\nClaude.",
        )

        val qwen = (QwenCodeAgentConfigAdapter(::container).readLive("qwen-code") as AgentConfigReadResult.Ready)
            .snapshot
        val reasonix = (ReasonixAgentConfigAdapter(::container).readLive("reasonix") as AgentConfigReadResult.Ready)
            .snapshot

        assertEquals(listOf("duplicate", "shared"), qwen.skills.map { it.id })
        assertEquals("Qwen Duplicate", qwen.skills.single { it.id == "duplicate" }.displayName)
        assertTrue(AgentSkillOperation.Remove in qwen.skills.single { it.id == "duplicate" }.allowedOperations)
        assertFalse(AgentSkillOperation.Remove in qwen.skills.single { it.id == "shared" }.allowedOperations)
        assertEquals(
            listOf("agent-compat", "claude-compat", "duplicate", "shared"),
            reasonix.skills.map { it.id },
        )
        assertEquals("Reasonix Duplicate", reasonix.skills.single { it.id == "duplicate" }.displayName)
        assertTrue(AgentSkillOperation.Remove in reasonix.skills.single { it.id == "duplicate" }.allowedOperations)
    }

    @Test
    fun openClawDoesNotFallBackWhenWorkspaceConfigIsMalformed() = runTest {
        nativeFile("root/.openclaw/openclaw.json").writeText("{ agents:")
        val adapter = OpenClawAgentConfigAdapter(context, ::container)

        val result = adapter.listCoreDocuments("openclaw", "/workspace/kite")

        assertTrue(result is AgentCoreDocumentListResult.Failed)
    }

    @Test
    fun kimiSystemDocumentWritesNativeFileWithoutEnteringConfigSnapshot() = runTest {
        val adapter = KimiCodeAgentConfigAdapter(context, ::container)
        val read = adapter.readCoreDocument("kimi", "kimi-system", "/workspace") as AgentCoreDocumentReadResult.Ready
        val body = "You are a focused coding agent."

        val applied = adapter.writeCoreDocument(
            AgentCoreDocumentWriteRequest(
                agentId = "kimi",
                documentId = "kimi-system",
                workspacePath = "/workspace",
                expectedRevision = read.snapshot.revision,
                content = body,
            )
        ) as AgentCoreDocumentWriteResult.Applied

        assertEquals(body, nativeFile("root/.kimi-code/SYSTEM.md").readText())
        assertEquals(body, applied.snapshot.content)
        assertFalse(applied.snapshot.toString().contains(body))
    }

    @Test
    fun mimoUsesNativeJsoncAndNeverProjectsCredential() = runTest {
        val file = nativeFile("root/.config/mimocode/mimocode.jsonc")
        file.writeText("""{"unknown":{"keep":true}}""")
        val adapter = MiMoCodeAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "mimo", "mimo", "https://api.xiaomimimo.com/v1", "mimo-v2-pro")
        val snapshot = applied.snapshot

        assertEquals("mimo/mimo-v2-pro", snapshot.defaultModel)
        assertEquals("mimo", snapshot.activeProviderId)
        assertEquals("mimo-v2-pro", snapshot.providers.single().models.single().id)
        assertEquals(AgentCredentialPresence.Present, snapshot.credentialPresence)
        assertFalse(snapshot.toString().contains(SECRET))
        assertTrue(file.readText().contains("\"unknown\""))
        assertTrue(file.readText().contains(SECRET))
    }

    @Test
    fun openClawWritesProviderCatalogAndPreservesOtherSections() = runTest {
        val file = nativeFile("root/.openclaw/openclaw.json")
        file.writeText("""{ channels: { telegram: { enabled: true } } }""")
        val adapter = OpenClawAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "openclaw", "local", "http://127.0.0.1:1234/v1", "local-model")

        assertEquals("local/local-model", applied.snapshot.defaultModel)
        assertEquals("local", applied.snapshot.activeProviderId)
        assertEquals("local-model", applied.snapshot.providers.single().models.single().id)
        val text = file.readText()
        assertTrue(text.contains("telegram"))
        assertTrue(text.contains("local/local-model"))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun openClawSeparatesThoughtLevelFromElevatedPermissionWithoutDuplicateModes() {
        val adapter = OpenClawAgentConfigAdapter(context, ::container)
        val thought = AgentConfigOption.Select(
            id = "thought_level",
            name = "Thought level",
            category = AgentConfigCategory.ThoughtLevel,
            currentValue = "high",
            choices = listOf(
                AgentConfigChoice("adaptive", "Adaptive"),
                AgentConfigChoice("low", "Low"),
                AgentConfigChoice("high", "High"),
            ),
        )
        val elevated = AgentConfigOption.Select(
            id = "elevated_level",
            name = "Elevated actions",
            currentValue = "ask",
            choices = listOf(
                AgentConfigChoice("off", "Off"),
                AgentConfigChoice("on", "On"),
                AgentConfigChoice("ask", "Ask"),
                AgentConfigChoice("full", "Full"),
            ),
        )

        val normalized = adapter.normalizePublishedSessionConfiguration(listOf(thought, elevated))
        val reasoning = normalized.single { it.category == AgentConfigCategory.ThoughtLevel } as AgentConfigOption.Select
        val permission = normalized.single { it.category == AgentConfigCategory.Permission } as AgentConfigOption.Select

        assertEquals(listOf("adaptive", "low", "high"), reasoning.choices.map { it.value })
        assertEquals(listOf("off", "ask", "full"), permission.choices.map { it.value })
        assertEquals(
            listOf(AgentPermissionLevel.Restricted, AgentPermissionLevel.Approval, AgentPermissionLevel.Full),
            permission.choices.map { it.permission },
        )
        assertEquals("ask", permission.currentValue)
        assertTrue(adapter.normalizeSessionModes(listOf(AgentMode("high", "High"))).isEmpty())
    }

    @Test
    fun openClawUsesNativeMcpAndGroupedSkillConfigurationKey() = runTest {
        val file = nativeFile("root/.openclaw/openclaw.json")
        file.writeText(
            """
                {
                  mcp: {
                    servers: {
                      docs: {
                        url: "https://docs.example.com/mcp",
                        transport: "streamable-http",
                        headers: {
                          Authorization: "Bearer ${'$'}{DOCS_TOKEN}",
                          "X-Literal": "KEEP_LITERAL"
                        },
                        enabled: true,
                        unknown: 7
                      }
                    }
                  },
                  skills: { entries: { "openclaw-demo": { enabled: false, unknown: true } } },
                  channels: { telegram: { enabled: true } }
                }
            """.trimIndent(),
        )
        nativeFile("root/.openclaw/skills/category/demo/SKILL.md").writeText(
            "---\nname: demo\ndescription: Demo\nmetadata:\n  openclaw:\n    skillKey: openclaw-demo\n---\nDemo.\n",
        )
        nativeFile("root/.agents/skills/shared/SKILL.md").writeText(
            "---\nname: shared\ndescription: Shared\n---\nShared.\n",
        )
        val adapter = OpenClawAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("openclaw") as AgentConfigReadResult.Ready).snapshot

        assertEquals(AgentMcpTransport.StreamableHttp, before.mcpServers.single().transport)
        assertEquals("DOCS_TOKEN", before.mcpServers.single().headerReferences.single().environmentVariable)
        assertEquals(AgentSkillActivation.Disabled, before.skills.single { it.id == "demo" }.activation)
        assertTrue(before.skills.single { it.id == "demo" }.location?.endsWith("category/demo") == true)
        assertTrue(AgentSkillOperation.Remove in before.skills.single { it.id == "demo" }.allowedOperations)
        assertTrue(AgentSkillOperation.Remove !in before.skills.single { it.id == "shared" }.allowedOperations)

        val enabled = adapter.apply(
            AgentConfigApplyRequest(
                "openclaw",
                before.revision,
                listOf(AgentPersistentConfigChange.SetSkillActivation("demo", AgentSkillActivation.Enabled)),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentSkillActivation.Enabled, enabled.snapshot.skills.single { it.id == "demo" }.activation)

        val edited = adapter.apply(
            AgentConfigApplyRequest(
                "openclaw",
                enabled.snapshot.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureMcpServer(
                        AgentMcpDraft(
                            id = "docs",
                            transport = AgentMcpTransport.Sse,
                            url = "https://new.example.com/sse",
                            headerReferences = listOf(AgentMcpEnvironmentReference("Authorization", "DOCS_TOKEN")),
                        ),
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentMcpTransport.Sse, edited.snapshot.mcpServers.single().transport)
        val text = file.readText()
        assertTrue(text.contains("unknown"))
        assertTrue(text.contains("KEEP_LITERAL"))
        assertTrue(text.contains("channels"))
        assertTrue(text.contains("openclaw-demo"))

        val removed = adapter.apply(
            AgentConfigApplyRequest(
                "openclaw",
                edited.snapshot.revision,
                listOf(AgentPersistentConfigChange.RemoveSkill("demo")),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(listOf("shared"), removed.snapshot.skills.map { it.id })
        assertTrue(nativeFile("root/.openclaw/skills/category/.kite-skill-backups").isDirectory)
    }

    @Test
    fun claudeWritesGatewayModelsWithoutReplacingUnrelatedSettings() = runTest {
        val file = nativeFile("root/.claude/settings.json")
        file.writeText("""{"permissions":{"allow":["Read"]},"env":{"KEEP_ME":"yes"}}""")
        val adapter = ClaudeCodeAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "claude-code", "gateway", "https://gateway.example.com", "deployment-sonnet")

        assertEquals("deployment-sonnet", applied.snapshot.defaultModel)
        assertEquals("gateway.example.com", applied.snapshot.activeProviderId)
        assertEquals("deployment-sonnet", applied.snapshot.providers.single().models.single().id)
        val text = file.readText()
        assertTrue(text.contains("KEEP_ME"))
        assertTrue(text.contains("permissions"))
        assertTrue(text.contains("CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY"))
        assertTrue(text.contains("ANTHROPIC_API_KEY"))
        assertFalse(text.contains("ANTHROPIC_AUTH_TOKEN"))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun claudeRoutesZhipuCodingPlanThroughOfficialAnthropicEndpoint() = runTest {
        val settings = nativeFile("root/.claude/settings.json")
        settings.writeText("""{"permissions":{"allow":["Read"]},"env":{"KEEP_ME":"yes"}}""")
        val state = nativeFile("root/.claude.json")
        state.writeText("""{"other":true}""")
        val adapter = ClaudeCodeAgentConfigAdapter(context, ::container)

        val applied = configureModels(
            adapter,
            "claude-code",
            "zhipu-coding-plan",
            "https://open.bigmodel.cn/api/coding/paas/v4",
            "glm-5.2",
            "glm-4.7",
        )

        assertEquals("zhipu-coding-plan", applied.snapshot.activeProviderId)
        assertEquals("glm-5.2", applied.snapshot.defaultModel)
        assertEquals("智谱 GLM Coding Plan", applied.snapshot.providers.single().displayName)
        val text = settings.readText()
        assertTrue(text.contains("https://open.bigmodel.cn/api/anthropic"))
        assertFalse(text.contains("/api/coding/paas/v4"))
        assertTrue(text.contains("ANTHROPIC_AUTH_TOKEN"))
        assertFalse(text.contains("ANTHROPIC_API_KEY"))
        assertTrue(text.contains("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"))
        assertTrue(text.contains("API_TIMEOUT_MS"))
        assertTrue(text.contains("ANTHROPIC_DEFAULT_SONNET_MODEL"))
        assertFalse(text.contains("CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY"))
        assertFalse(text.contains("ANTHROPIC_CUSTOM_MODEL_OPTION"))
        assertTrue(text.contains("KEEP_ME"))
        assertTrue(state.readText().contains("hasCompletedOnboarding"))
        assertTrue(state.readText().contains("other"))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun claudeClearsZhipuOnlyEnvironmentWhenSwitchingBackToGateway() = runTest {
        val settings = nativeFile("root/.claude/settings.json")
        val adapter = ClaudeCodeAgentConfigAdapter(context, ::container)

        configure(
            adapter,
            "claude-code",
            "zhipu-coding-plan",
            "https://open.bigmodel.cn/api/coding/paas/v4",
            "glm-5.2",
        )
        configure(
            adapter,
            "claude-code",
            "gateway",
            "https://gateway.example.com",
            "deployment-sonnet",
        )

        val text = settings.readText()
        assertTrue(text.contains("CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY"))
        assertTrue(text.contains("ANTHROPIC_CUSTOM_MODEL_OPTION"))
        assertFalse(text.contains("ANTHROPIC_DEFAULT_HAIKU_MODEL"))
        assertFalse(text.contains("ANTHROPIC_DEFAULT_SONNET_MODEL"))
        assertFalse(text.contains("ANTHROPIC_DEFAULT_OPUS_MODEL"))
        assertFalse(text.contains("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"))
        assertFalse(text.contains("API_TIMEOUT_MS"))
    }

    @Test
    fun claudeProjectsPublishedModesIntoSixLevelPermissionSemantics() {
        val adapter = ClaudeCodeAgentConfigAdapter(context, ::container)
        val native = AgentConfigOption.Select(
            id = "mode",
            name = "Mode",
            category = AgentConfigCategory.Mode,
            currentValue = "default",
            choices = listOf(
                AgentConfigChoice("plan", "Plan Mode"),
                AgentConfigChoice("dontAsk", "Don't Ask"),
                AgentConfigChoice("default", "Manual"),
                AgentConfigChoice("acceptEdits", "Accept Edits"),
                AgentConfigChoice("auto", "Auto"),
                AgentConfigChoice("bypassPermissions", "Bypass Permissions"),
                AgentConfigChoice("future", "Future"),
            ),
        )

        val permission = adapter.normalizeSessionConfiguration(listOf(native)).single() as AgentConfigOption.Select

        assertEquals(AgentConfigCategory.Permission, permission.category)
        assertEquals("权限", permission.name)
        assertEquals(
            listOf("plan", "dontAsk", "default", "acceptEdits", "auto", "bypassPermissions"),
            permission.choices.map { it.value },
        )
        assertEquals(
            listOf("只读", "受限", "审批", "宽松", "智能", "完全"),
            permission.choices.map { it.name },
        )
        assertEquals("default", permission.currentValue)
    }

    @Test
    fun claudeUsesUserMcpStateAndNativeSkillVisibilityOverrides() = runTest {
        val settings = nativeFile("root/.claude/settings.json")
        settings.writeText("""{"permissions":{"allow":["Read"]},"skillOverrides":{"review":"off"}}""")
        val state = nativeFile("root/.claude.json")
        state.writeText(
            """
                {
                  "mcpServers": {
                    "local": {
                      "type": "stdio",
                      "command": "demo",
                      "args": ["serve"],
                      "env": {"TOKEN": "${'$'}{DEMO_TOKEN}"},
                      "unknown": 7
                    }
                  },
                  "other": true
                }
            """.trimIndent(),
        )
        nativeFile("root/.claude/skills/review/SKILL.md").writeText(
            "---\nname: review\ndescription: Review changes\n---\nReview.",
        )
        val adapter = ClaudeCodeAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("claude-code") as AgentConfigReadResult.Ready).snapshot

        assertEquals(AgentMcpTransport.Stdio, before.mcpServers.single().transport)
        assertEquals("DEMO_TOKEN", before.mcpServers.single().environmentReferences.single().environmentVariable)
        assertEquals(AgentSkillActivation.Disabled, before.skills.single().activation)
        assertTrue(AgentSkillOperation.ManualOnly in before.skills.single().allowedOperations)

        val manual = adapter.apply(
            AgentConfigApplyRequest(
                "claude-code",
                before.revision,
                listOf(AgentPersistentConfigChange.SetSkillActivation("review", AgentSkillActivation.ManualOnly)),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentSkillActivation.ManualOnly, manual.snapshot.skills.single().activation)

        val remote = adapter.apply(
            AgentConfigApplyRequest(
                "claude-code",
                manual.snapshot.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureMcpServer(
                        AgentMcpDraft(
                            id = "remote",
                            transport = AgentMcpTransport.StreamableHttp,
                            url = "https://mcp.example.com/mcp",
                            headerReferences = listOf(
                                com.kite.app.agent.config.AgentMcpEnvironmentReference("Authorization", "REMOTE_TOKEN"),
                            ),
                        ),
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(setOf("local", "remote"), remote.snapshot.mcpServers.map { it.id }.toSet())
        assertTrue(state.readText().contains("unknown"))
        assertTrue(state.readText().contains("\"other\""))
        assertTrue(state.readText().contains("REMOTE_TOKEN"))
        assertTrue(settings.readText().contains("permissions"))
        assertTrue(settings.readText().contains("user-invocable-only"))
    }

    @Test
    fun kimiUsesSeparateMcpFileAndSkillFrontmatterWithoutInventingProviderSchema() = runTest {
        val mcp = nativeFile("root/.kimi-code/mcp.json")
        mcp.writeText(
            """
                {
                  "mcpServers": {
                    "local": {
                      "command": "demo",
                      "args": ["serve"],
                      "enabled": false,
                      "unknown": 9
                    }
                  },
                  "other": true
                }
            """.trimIndent(),
        )
        val skill = nativeFile("root/.kimi-code/skills/review/SKILL.md")
        skill.writeText(
            "---\nname: review\ndescription: Review changes\ndisableModelInvocation: false\n---\nReview.\n",
        )
        nativeFile("root/.agents/skills/shared/SKILL.md").writeText(
            "---\nname: shared\ndescription: Shared skill\n---\nShared.\n",
        )
        val adapter = KimiCodeAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("kimi") as AgentConfigReadResult.Ready).snapshot

        assertTrue(AgentPersistentConfigCapability.Provider !in adapter.capabilities().supported)
        assertEquals(AgentMcpTransport.Stdio, before.mcpServers.single().transport)
        assertFalse(before.mcpServers.single().enabled)
        assertEquals(setOf("review", "shared"), before.skills.map { it.id }.toSet())
        assertTrue(AgentSkillOperation.Remove in before.skills.single { it.id == "review" }.allowedOperations)
        assertTrue(AgentSkillOperation.Remove !in before.skills.single { it.id == "shared" }.allowedOperations)

        val manual = adapter.apply(
            AgentConfigApplyRequest(
                "kimi",
                before.revision,
                listOf(AgentPersistentConfigChange.SetSkillActivation("review", AgentSkillActivation.ManualOnly)),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentSkillActivation.ManualOnly, manual.snapshot.skills.single { it.id == "review" }.activation)
        assertTrue(skill.readText().contains("disableModelInvocation: true"))

        val remote = adapter.apply(
            AgentConfigApplyRequest(
                "kimi",
                manual.snapshot.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureMcpServer(
                        AgentMcpDraft(
                            id = "remote",
                            transport = AgentMcpTransport.StreamableHttp,
                            url = "https://mcp.example.com/mcp",
                            headerReferences = listOf(AgentMcpEnvironmentReference("Authorization", "REMOTE_TOKEN")),
                        ),
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(setOf("local", "remote"), remote.snapshot.mcpServers.map { it.id }.toSet())
        assertTrue(mcp.readText().contains("bearerTokenEnvVar"))
        assertTrue(mcp.readText().contains("REMOTE_TOKEN"))
        assertTrue(mcp.readText().contains("unknown"))
        assertTrue(mcp.readText().contains("other"))
    }

    @Test
    fun mimoProjectsNativeMcpAndOnlyAllowsRealSkillFileOperations() = runTest {
        val config = nativeFile("root/.config/mimocode/mimocode.jsonc")
        config.writeText(
            """
                {
                  "mcp": {
                    "local": {
                      "type": "local",
                      "command": ["demo", "serve"],
                      "environment": {"TOKEN": "{env:DEMO_TOKEN}"},
                      "unknown": 4
                    }
                  },
                  "other": true
                }
            """.trimIndent(),
        )
        nativeFile("root/.config/mimocode/skills/review/SKILL.md").writeText(
            "---\nname: review\ndescription: Review changes\n---\nReview.\n",
        )
        nativeFile("root/.config/mimocode/skill/singular/SKILL.md").writeText(
            "---\nname: singular\ndescription: Singular directory skill\n---\nSingular.\n",
        )
        nativeFile("root/.agents/skills/shared/SKILL.md").writeText(
            "---\nname: shared\ndescription: Shared skill\n---\nShared.\n",
        )
        val adapter = MiMoCodeAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("mimo") as AgentConfigReadResult.Ready).snapshot

        assertEquals(AgentMcpTransport.Stdio, before.mcpServers.single().transport)
        assertEquals("DEMO_TOKEN", before.mcpServers.single().environmentReferences.single().environmentVariable)
        assertEquals(setOf("review", "shared", "singular"), before.skills.map { it.id }.toSet())
        assertTrue(before.skills.all { it.activation == AgentSkillActivation.Enabled })
        assertTrue(before.skills.all { AgentSkillOperation.Disable !in it.allowedOperations })
        assertTrue(AgentSkillOperation.Remove in before.skills.single { it.id == "review" }.allowedOperations)
        assertTrue(AgentSkillOperation.Remove in before.skills.single { it.id == "singular" }.allowedOperations)
        assertTrue(AgentSkillOperation.Remove !in before.skills.single { it.id == "shared" }.allowedOperations)

        val remote = adapter.apply(
            AgentConfigApplyRequest(
                "mimo",
                before.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureMcpServer(
                        AgentMcpDraft(
                            id = "remote",
                            transport = AgentMcpTransport.RemoteHttpOrSse,
                            url = "https://mcp.example.com/mcp",
                            headerReferences = listOf(AgentMcpEnvironmentReference("Authorization", "REMOTE_TOKEN")),
                        ),
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(setOf("local", "remote"), remote.snapshot.mcpServers.map { it.id }.toSet())
        assertTrue(config.readText().contains("{env:REMOTE_TOKEN}"))
        assertTrue(config.readText().contains("unknown"))
        assertTrue(config.readText().contains("other"))
    }

    @Test
    fun codexEditsOnlyActiveProviderAndPreservesTomlComments() = runTest {
        val file = nativeFile("root/.codex/config.toml")
        file.writeText("""
            # 用户注释
            approval_policy = "on-request"

            [sandbox_workspace_write]
            network_access = true
        """.trimIndent())
        val adapter = CodexAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "codex", "mimo", "https://api.xiaomimimo.com/v1", "mimo-v2-pro")

        assertEquals("mimo-v2-pro", applied.snapshot.defaultModel)
        assertEquals("mimo", applied.snapshot.activeProviderId)
        assertEquals("mimo", applied.snapshot.providers.single().id)
        val text = file.readText()
        assertTrue(text.contains("# 用户注释"))
        assertTrue(text.contains("approval_policy"))
        assertTrue(text.contains("[sandbox_workspace_write]"))
        assertTrue(text.contains("[model_providers.mimo]"))
        assertTrue(text.contains("base_url = \"http://127.0.0.1:4453/v1\""))
        assertEquals("https://api.xiaomimimo.com/v1", nativeFile("workspace/.kf/secrets/kite.codex-relay-upstream").readText())
        assertEquals(SECRET, nativeFile("workspace/.kf/secrets/kite.codex-relay-api-key").readText())
        assertFalse(text.contains("experimental_bearer_token"))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun codexSelectingOfficialModelExitsCustomProviderWithoutDeletingIt() = runTest {
        val file = nativeFile("root/.codex/config.toml")
        file.writeText(
            """
                model_provider = "zhipu-coding-plan"
                model = "glm-5.2"

                [model_providers.zhipu-coding-plan]
                name = "智谱 GLM Coding Plan"
                base_url = "http://127.0.0.1:4453/v1"
                wire_api = "responses"
                requires_openai_auth = false
            """.trimIndent(),
        )
        nativeFile("workspace/.kf/secrets/kite.codex-relay-upstream")
            .writeText("https://open.bigmodel.cn/api/coding/paas/v4")
        nativeFile("workspace/.kf/secrets/kite.codex-relay-api-key").writeText(SECRET)
        val adapter = CodexAgentConfigAdapter(context, ::container)
        assertEquals(
            AgentSessionConfigurationEffect.Reconnect,
            adapter.providerConfigurationEffect(),
        )
        val before = (adapter.readLive("codex") as AgentConfigReadResult.Ready).snapshot
        val officialOption = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "gpt-5.6-sol",
            choices = listOf(
                AgentConfigChoice(
                    value = "gpt-5.6-sol",
                    name = "GPT-5.6-Sol",
                    groupId = "openai",
                    groupName = "OpenAI",
                    modelSource = AgentModelSource.OfficialLogin,
                ),
            ),
        )

        val change = requireNotNull(adapter.defaultModelChange(officialOption))
        assertTrue(change.clearProviderOverride)
        val applied = adapter.apply(
            AgentConfigApplyRequest("codex", before.revision, listOf(change)),
        ) as AgentConfigApplyResult.Applied

        assertEquals(null, applied.snapshot.activeProviderId)
        assertEquals("gpt-5.6-sol", applied.snapshot.defaultModel)
        val text = file.readText()
        assertFalse(text.contains("model_provider = \"zhipu-coding-plan\""))
        assertTrue(text.contains("[model_providers.zhipu-coding-plan]"))
        assertEquals(
            "https://open.bigmodel.cn/api/coding/paas/v4",
            nativeFile("workspace/.kf/secrets/kite.codex-relay-upstream").readText(),
        )
        assertEquals(SECRET, nativeFile("workspace/.kf/secrets/kite.codex-relay-api-key").readText())
    }

    @Test
    fun codexImportsTheActiveLegacyCustomProviderIntoTheUnifiedCatalogShape() = runTest {
        nativeFile("root/.codex/config.toml").writeText(
            """
                model_provider = "zhipu-coding-plan"
                model = "glm-5.2"

                [model_providers.zhipu-coding-plan]
                name = "智谱 GLM Coding Plan"
                base_url = "http://127.0.0.1:4453/v1"
                wire_api = "responses"
                requires_openai_auth = false
            """.trimIndent(),
        )
        nativeFile("workspace/.kf/secrets/kite.codex-relay-upstream")
            .writeText("https://open.bigmodel.cn/api/coding/paas/v4")
        nativeFile("workspace/.kf/secrets/kite.codex-relay-api-key").writeText(SECRET)
        val adapter = CodexAgentConfigAdapter(context, ::container)

        val result = adapter.readUserProviderImport("codex") as AgentUserProviderImportResult.Ready
        val imported = result.import

        assertEquals("zhipu-coding-plan", imported.activeProviderId)
        assertEquals("zhipu-coding-plan/glm-5.2", imported.defaultModel)
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", imported.providers.single().baseUrl)
        assertEquals(listOf("glm-5.2"), imported.providers.single().models.map { it.id })
        assertTrue("zhipu-coding-plan" in imported.credentials)
        assertFalse(imported.toString().contains(SECRET))
    }

    @Test
    fun codexMigratesLegacyInlineCredentialIntoPrivateRelayFiles() = runTest {
        val file = nativeFile("root/.codex/config.toml")
        file.writeText(
            """
                model_provider = "zhipu-coding-plan"
                model = "glm-5.2"

                [model_providers.zhipu-coding-plan]
                name = "智谱 GLM Coding Plan"
                base_url = "https://open.bigmodel.cn/api/coding/paas/v4"
                wire_api = "responses"
                requires_openai_auth = false
                experimental_bearer_token = "$SECRET"
            """.trimIndent(),
        )
        val adapter = CodexAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("codex") as AgentConfigReadResult.Ready).snapshot

        val applied = adapter.apply(
            AgentConfigApplyRequest(
                agentId = "codex",
                expectedRevision = before.revision,
                changes = listOf(
                    AgentPersistentConfigChange.ConfigureProvider(
                        AgentProviderDraft(
                            id = "zhipu-coding-plan",
                            displayName = "智谱 GLM Coding Plan",
                            baseUrl = "https://open.bigmodel.cn/api/coding/paas/v4",
                            models = listOf(AgentProviderModelSummary("glm-5.2", "GLM-5.2")),
                        ),
                        AgentProviderCredentialChange.Keep,
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied

        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", applied.snapshot.providers.single().baseUrl)
        assertEquals(AgentCredentialPresence.Present, applied.snapshot.credentialPresence)
        assertEquals(SECRET, nativeFile("workspace/.kf/secrets/kite.codex-relay-api-key").readText())
        assertEquals(
            "https://open.bigmodel.cn/api/coding/paas/v4",
            nativeFile("workspace/.kf/secrets/kite.codex-relay-upstream").readText(),
        )
        assertTrue(file.readText().contains("base_url = \"http://127.0.0.1:4453/v1\""))
        assertFalse(file.readText().contains(SECRET))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun codexMigratesLegacyProviderAliasByMatchingEndpoint() = runTest {
        val file = nativeFile("root/.codex/config.toml")
        file.writeText(
            """
                model_provider = "zhipu"
                model = "glm-5.2"

                [model_providers.zhipu]
                name = "旧智谱配置"
                base_url = "https://open.bigmodel.cn/api/coding/paas/v4/"
                wire_api = "responses"
                requires_openai_auth = false
                experimental_bearer_token = "$SECRET"
            """.trimIndent(),
        )
        val adapter = CodexAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("codex") as AgentConfigReadResult.Ready).snapshot

        val applied = adapter.apply(
            AgentConfigApplyRequest(
                agentId = "codex",
                expectedRevision = before.revision,
                changes = listOf(
                    AgentPersistentConfigChange.ConfigureProvider(
                        AgentProviderDraft(
                            id = "zhipu-coding-plan",
                            displayName = "智谱 GLM Coding Plan",
                            baseUrl = "https://open.bigmodel.cn/api/coding/paas/v4",
                            models = listOf(AgentProviderModelSummary("glm-5.2", "GLM-5.2")),
                        ),
                        AgentProviderCredentialChange.Keep,
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied

        val text = file.readText()
        assertEquals("zhipu-coding-plan", applied.snapshot.activeProviderId)
        assertEquals(SECRET, nativeFile("workspace/.kf/secrets/kite.codex-relay-api-key").readText())
        assertTrue(text.contains("[model_providers.zhipu]"))
        assertTrue(text.contains("[model_providers.zhipu-coding-plan]"))
        assertFalse(text.contains("experimental_bearer_token"))
        assertFalse(text.contains(SECRET))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun codexProjectsNativeModesIntoVerifiedPermissionLevels() {
        val adapter = CodexAgentConfigAdapter(context, ::container)
        val native = AgentConfigOption.Select(
            id = "mode",
            name = "Mode",
            category = AgentConfigCategory.Mode,
            currentValue = "agent",
            choices = listOf(
                AgentConfigChoice("read-only", "Read Only"),
                AgentConfigChoice("agent", "Agent"),
                AgentConfigChoice("agent-full-access", "Agent Full Access"),
                AgentConfigChoice("future-mode", "Future Mode"),
            ),
        )

        val permission = adapter.normalizeSessionConfiguration(listOf(native)).single() as AgentConfigOption.Select

        assertEquals(AgentConfigCategory.Permission, permission.category)
        assertEquals("权限", permission.name)
        assertEquals(listOf("read-only", "agent", "agent-full-access"), permission.choices.map { it.value })
        assertEquals(listOf("只读", "审批", "完全"), permission.choices.map { it.name })
        assertEquals("agent", permission.currentValue)
    }

    @Test
    fun codexDraftUsesTheSameFourPermissionChoicesAsAppServer() = runTest {
        nativeFile("root/.codex/config.toml").writeText(
            """
            model_provider = "zhipu-coding-plan"
            model = "glm-5.2"
            """.trimIndent(),
        )
        val adapter = CodexAgentConfigAdapter(context, ::container)

        val permission = adapter.readSessionConfiguration("codex")
            .filterIsInstance<AgentConfigOption.Select>()
            .single { it.category == AgentConfigCategory.Permission }

        assertEquals(SESSION_PERMISSION_CONFIG_ID, permission.id)
        assertEquals("codex.permission.custom", permission.currentValue)
        assertEquals(
            listOf("请求批准", "替我审批", "完全访问权限", "自定义"),
            permission.choices.map { it.name },
        )
    }

    @Test
    fun codexHidesFallbackEffortSuffixWhenCustomModelHasNoVerifiedChoice() {
        val adapter = CodexAgentConfigAdapter(context, ::container)
        val customModel = AgentConfigOption.Select(
            id = "model",
            name = "Model",
            category = AgentConfigCategory.Model,
            currentValue = "glm-5.2[medium]",
            choices = listOf(
                AgentConfigChoice("gpt-5.6-sol[low]", "GPT-5.6-Sol (low)"),
                AgentConfigChoice("gpt-5.6-sol[high]", "GPT-5.6-Sol (high)"),
            ),
        )

        val normalized = adapter.normalizeSessionConfiguration(listOf(customModel)).single() as AgentConfigOption.Select

        assertEquals("glm-5.2", normalized.currentValue)
        assertEquals(customModel.choices, normalized.choices)

        val verifiedModel = customModel.copy(currentValue = "gpt-5.6-sol[high]")
        val verified = adapter.normalizeSessionConfiguration(listOf(verifiedModel)).single() as AgentConfigOption.Select
        assertEquals("gpt-5.6-sol[high]", verified.currentValue)
    }

    @Test
    fun codexProjectsNativeMcpAndSkillManagementWithoutLosingUnknownToml() = runTest {
        val file = nativeFile("root/.codex/config.toml")
        file.writeText(
            """
                approval_policy = "on-request"

                [mcp_servers.docs]
                url = "https://docs.example.com/mcp"
                enabled = true
                required = true
            """.trimIndent(),
        )
        nativeFile("root/.agents/skills/review/SKILL.md").writeText(
            "---\nname: review\ndescription: Review changes\n---\nDo the review.",
        )
        val adapter = CodexAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("codex") as AgentConfigReadResult.Ready).snapshot

        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.Mcp))
        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.Skill))
        assertEquals(AgentMcpTransport.StreamableHttp, before.mcpServers.single().transport)
        assertTrue(AgentMcpOperation.Disable in before.mcpServers.single().allowedOperations)
        assertEquals(AgentSkillActivation.Enabled, before.skills.single().activation)
        assertTrue(AgentSkillOperation.Disable in before.skills.single().allowedOperations)

        val disabledSkill = adapter.apply(
            AgentConfigApplyRequest(
                "codex",
                before.revision,
                listOf(AgentPersistentConfigChange.SetSkillActivation("review", AgentSkillActivation.Disabled)),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentSkillActivation.Disabled, disabledSkill.snapshot.skills.single().activation)

        val editedMcp = adapter.apply(
            AgentConfigApplyRequest(
                "codex",
                disabledSkill.snapshot.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureMcpServer(
                        AgentMcpDraft(
                            id = "docs",
                            transport = AgentMcpTransport.StreamableHttp,
                            url = "https://new.example.com/mcp",
                            headerReferences = listOf(
                                com.kite.app.agent.config.AgentMcpEnvironmentReference("Authorization", "DOCS_TOKEN"),
                            ),
                        ),
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied

        assertEquals("https://new.example.com/mcp", editedMcp.snapshot.mcpServers.single().url)
        assertTrue(file.readText().contains("required = true"))
        assertTrue(file.readText().contains("approval_policy"))
        assertTrue(file.readText().contains("[[skills.config]]"))
        assertTrue(file.readText().contains("/root/.agents/skills/review/SKILL.md"))
        assertTrue(file.readText().contains("DOCS_TOKEN"))
    }

    @Test
    fun codexImportsAndRecoverablyRemovesPersonalSkill() = runTest {
        nativeFile("root/.codex/config.toml").writeText("")
        val staged = nativeFile("workspace/.kf/imports/skills/import-12345678/SKILL.md")
        staged.writeText("---\nname: demo-skill\ndescription: Demo\n---\nContent")
        val adapter = CodexAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("codex") as AgentConfigReadResult.Ready).snapshot

        val installed = adapter.apply(
            AgentConfigApplyRequest(
                "codex",
                before.revision,
                listOf(AgentPersistentConfigChange.InstallSkill("demo-skill", "kite-import:import-12345678")),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(listOf("demo-skill"), installed.snapshot.skills.map { it.id })
        assertTrue(nativeFile("root/.codex/skills/demo-skill/SKILL.md").isFile)

        val removed = adapter.apply(
            AgentConfigApplyRequest(
                "codex",
                installed.snapshot.revision,
                listOf(AgentPersistentConfigChange.RemoveSkill("demo-skill")),
            ),
        ) as AgentConfigApplyResult.Applied
        assertTrue(removed.snapshot.skills.isEmpty())
        assertTrue(nativeFile("root/.codex/skills/.kite-skill-backups").isDirectory)
    }

    @Test
    fun hermesReplacesOnlyManagedYamlSections() = runTest {
        val file = nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml")
        file.writeText("""
            # 用户说明
            agent:
              max_turns: 77
        """.trimIndent())
        val adapter = HermesAgentConfigAdapter(context, ::container)

        val applied = configure(adapter, "hermes", "work", "https://gpu.example.com/v1", "qwen3-coder")

        assertEquals("qwen3-coder", applied.snapshot.defaultModel)
        assertEquals("work", applied.snapshot.activeProviderId)
        assertEquals("work", applied.snapshot.providers.single().id)
        val text = file.readText()
        assertTrue(text.contains("# 用户说明"))
        assertTrue(text.contains("max_turns: 77"))
        assertTrue(text.contains("providers:"))
        assertTrue(text.contains("api: https://gpu.example.com/v1"))
        assertFalse(applied.snapshot.toString().contains(SECRET))
    }

    @Test
    fun hermesReadsAndUpdatesCurrentProviderSchemaWithoutDroppingNativeFields() = runTest {
        val file = nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml")
        file.writeText(
            """
                model:
                  provider: gateway
                  default: old-model
                providers:
                  gateway:
                    name: 私有网关
                    api: https://old.example.com/v1
                    key_env: GATEWAY_KEY
                    discover_models: false
                    extra_headers:
                      X-Tenant: keep-me
                    models:
                      old-model: {}
                agent:
                  max_turns: 77
            """.trimIndent(),
        )
        val adapter = HermesAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("hermes") as AgentConfigReadResult.Ready).snapshot

        assertEquals("gateway", before.providers.single().id)
        assertEquals("私有网关", before.providers.single().displayName)
        assertEquals("old-model", before.providers.single().models.single().id)

        val applied = adapter.apply(
            AgentConfigApplyRequest(
                agentId = "hermes",
                expectedRevision = before.revision,
                changes = listOf(
                    AgentPersistentConfigChange.ConfigureProvider(
                        AgentProviderDraft(
                            id = "gateway",
                            displayName = "私有网关",
                            baseUrl = "https://new.example.com/v1",
                            models = listOf(AgentProviderModelSummary("new-model", "New Model")),
                        ),
                        AgentProviderCredentialChange.Keep,
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied

        assertEquals("new-model", applied.snapshot.providers.single().models.single().id)
        val text = file.readText()
        assertTrue(text.contains("api: https://new.example.com/v1"))
        assertTrue(text.contains("discover_models: false"))
        assertTrue(text.contains("X-Tenant: keep-me"))
        assertTrue(text.contains("max_turns: 77"))
        assertFalse(text.contains("custom_providers:"))
    }

    @Test
    fun hermesUsesNativeMcpAndGlobalSkillDisableList() = runTest {
        val file = nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml")
        file.writeText(
            """
                # 用户说明
                agent:
                  max_turns: 77
                mcp_servers:
                  docs:
                    url: https://docs.example.com/mcp
                    headers:
                      Authorization: "Bearer ${'$'}{DOCS_TOKEN}"
                      X-Literal: KEEP_LITERAL
                    timeout: 120
                    enabled: true
                skills:
                  disabled: [review]
                  platform_disabled:
                    telegram: [telegram-only]
            """.trimIndent(),
        )
        nativeFile("workspace/.kf/software/kite.hermes.core/home/skills/coding/review/SKILL.md").writeText(
            "---\nname: review\ndescription: Review\n---\nReview.\n",
        )
        val adapter = HermesAgentConfigAdapter(context, ::container)
        val before = (adapter.readLive("hermes") as AgentConfigReadResult.Ready).snapshot

        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.Mcp))
        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.Skill))
        assertEquals(AgentMcpTransport.StreamableHttp, before.mcpServers.single().transport)
        assertEquals("DOCS_TOKEN", before.mcpServers.single().headerReferences.single().environmentVariable)
        assertEquals(AgentSkillActivation.Disabled, before.skills.single().activation)
        assertTrue(before.skills.single().location?.endsWith("coding/review") == true)

        val enabled = adapter.apply(
            AgentConfigApplyRequest(
                "hermes",
                before.revision,
                listOf(AgentPersistentConfigChange.SetSkillActivation("review", AgentSkillActivation.Enabled)),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentSkillActivation.Enabled, enabled.snapshot.skills.single().activation)

        val edited = adapter.apply(
            AgentConfigApplyRequest(
                "hermes",
                enabled.snapshot.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureMcpServer(
                        AgentMcpDraft(
                            id = "docs",
                            transport = AgentMcpTransport.Sse,
                            url = "https://new.example.com/sse",
                            headerReferences = listOf(AgentMcpEnvironmentReference("Authorization", "DOCS_TOKEN")),
                        ),
                    ),
                ),
            ),
        ) as AgentConfigApplyResult.Applied
        assertEquals(AgentMcpTransport.Sse, edited.snapshot.mcpServers.single().transport)
        val text = file.readText()
        assertTrue(text.contains("# 用户说明"))
        assertTrue(text.contains("max_turns: 77"))
        assertTrue(text.contains("timeout: 120"))
        assertTrue(text.contains("KEEP_LITERAL"))
        assertTrue(text.contains("platform_disabled:"))
        assertTrue(text.contains("telegram-only"))

        val removed = adapter.apply(
            AgentConfigApplyRequest(
                "hermes",
                edited.snapshot.revision,
                listOf(AgentPersistentConfigChange.RemoveSkill("review")),
            ),
        ) as AgentConfigApplyResult.Applied
        assertTrue(removed.snapshot.skills.isEmpty())
        assertTrue(
            nativeFile("workspace/.kf/software/kite.hermes.core/home/skills/coding/.kite-skill-backups").isDirectory,
        )
    }

    @Test
    fun hermesProjectsAndAppliesOfficialPermissionProfilesWithoutDroppingOtherYaml() = runTest {
        val file = nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml")
        file.writeText(
            """
                approvals:
                  mode: manual
                  timeout: 300
                agent:
                  max_turns: 77
            """.trimIndent(),
        )
        val adapter = HermesAgentConfigAdapter(context, ::container)

        val before = (adapter.readLive("hermes") as AgentConfigReadResult.Ready).snapshot
        assertTrue(adapter.capabilities().supports(AgentPersistentConfigCapability.PermissionProfiles))
        assertEquals("manual", before.activePermissionProfileId)
        assertEquals(listOf("manual", "smart", "off"), before.permissionProfiles.map { it.id })

        val option = adapter.readSessionConfiguration("hermes").single() as AgentConfigOption.Select
        assertEquals(SESSION_PERMISSION_CONFIG_ID, option.id)
        assertEquals(AgentConfigCategory.Permission, option.category)
        assertEquals("manual", option.currentValue)
        assertEquals(before.permissionProfiles.map { it.id }, option.choices.map { it.value })
        assertEquals(listOf("审批", "智能", "完全"), option.choices.map { it.name })

        val applied = adapter.apply(AgentConfigApplyRequest(
            agentId = "hermes",
            expectedRevision = before.revision,
            changes = listOf(AgentPersistentConfigChange.SetPermissionProfile("off")),
        )) as AgentConfigApplyResult.Applied
        assertEquals("off", applied.snapshot.activePermissionProfileId)

        val text = file.readText()
        assertTrue(Regex("mode:\\s*['\\\"]?off['\\\"]?").containsMatchIn(text))
        assertTrue(text.contains("timeout: 300"))
        assertTrue(text.contains("max_turns: 77"))
    }

    @Test
    fun hermesProjectsConfiguredModelsIntoUnifiedSessionConfiguration() = runTest {
        val file = nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml")
        file.writeText(
            """
                model:
                  provider: zhipu
                  default: glm-5
                custom_providers:
                  - name: zhipu
                    base_url: https://open.bigmodel.cn/api/paas/v4/
                    key_env: ZHIPU_API_KEY
                    models:
                      glm-5: {}
                      glm-5-code: {}
                approvals:
                  mode: smart
                agent:
                  max_turns: 77
            """.trimIndent(),
        )
        val adapter = HermesAgentConfigAdapter(context, ::container)

        val before = adapter.readSessionConfiguration("hermes")
        val model = before.filterIsInstance<AgentConfigOption.Select>()
            .single { it.category == AgentConfigCategory.Model }
        val permission = before.filterIsInstance<AgentConfigOption.Select>()
            .single { it.category == AgentConfigCategory.Permission }
        assertEquals(NATIVE_MODEL_CONFIG_ID, model.id)
        assertEquals("zhipu/glm-5", model.currentValue)
        assertEquals(listOf("zhipu/glm-5", "zhipu/glm-5-code"), model.choices.map { it.value })
        assertEquals(listOf("zhipu", "zhipu"), model.choices.map { it.groupId })
        assertEquals("smart", permission.currentValue)

        val applied = adapter.applySessionConfiguration(
            agentId = "hermes",
            configId = NATIVE_MODEL_CONFIG_ID,
            value = AgentConfigValue.Select("zhipu/glm-5-code"),
        ) as AgentSessionConfigurationApplyResult.Applied

        assertEquals(AgentSessionConfigurationEffect.NewSession, applied.effect)
        val afterModel = applied.options.filterIsInstance<AgentConfigOption.Select>()
            .single { it.category == AgentConfigCategory.Model }
        val afterPermission = applied.options.filterIsInstance<AgentConfigOption.Select>()
            .single { it.category == AgentConfigCategory.Permission }
        assertEquals("zhipu/glm-5-code", afterModel.currentValue)
        assertEquals("smart", afterPermission.currentValue)
        val text = file.readText()
        assertTrue(Regex("provider:\\s*['\"]?zhipu['\"]?").containsMatchIn(text))
        assertTrue(Regex("default:\\s*['\"]?glm-5-code['\"]?").containsMatchIn(text))
        assertTrue(text.contains("max_turns: 77"))
    }

    @Test
    fun hermesMapsConfiguredProviderModelsToItsAcpColonIds() {
        val adapter = HermesAgentConfigAdapter(context, ::container)
        val option = AgentConfigOption.Select(
            id = "acp.session.model",
            name = "Model",
            category = AgentConfigCategory.Model,
            currentValue = "custom:GLM-5.3",
            choices = listOf(
                AgentConfigChoice("custom:other:glm-5.2", "Other · glm-5.2"),
                AgentConfigChoice("zhipu-coding-plan:glm-5.2", "Legacy GLM-5.2"),
                AgentConfigChoice("custom:zhipu-coding-plan:glm-5.2", "GLM-5.2"),
                AgentConfigChoice("openrouter:anthropic/claude-sonnet-4", "Claude Sonnet 4"),
            ),
        )

        assertEquals(
            AgentSessionModelSelection("acp.session.model", "custom:zhipu-coding-plan:glm-5.2"),
            adapter.sessionModelSelection(
                AgentPersistentConfigChange.SelectProvider("zhipu-coding-plan", "glm-5.2"),
                listOf(option),
            ),
        )
        assertEquals(
            AgentSessionModelSelection("acp.session.model", "openrouter:anthropic/claude-sonnet-4"),
            adapter.sessionModelSelection(
                AgentPersistentConfigChange.SelectProvider("openrouter", "anthropic/claude-sonnet-4"),
                listOf(option),
            ),
        )
    }

    @Test
    fun hermesUsesItsNativeDefaultAsCurrentSessionControlDefault() = runTest {
        nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml").writeText(
            "agent:\n  max_turns: 77\n",
        )
        val adapter = HermesAgentConfigAdapter(context, ::container)

        val option = adapter.readSessionConfiguration("hermes").single() as AgentConfigOption.Select

        assertEquals("smart", option.currentValue)
    }

    @Test
    fun selectingProviderAndModelUsesEachAgentsNativeActiveFields() = runTest {
        nativeFile("root/.config/mimocode/mimocode.jsonc").writeText("{}")
        val mimo = MiMoCodeAgentConfigAdapter(context, ::container)
        configureModels(mimo, "mimo", "one", "https://one.example/v1", "a", "b")
        val mimoSelected = select(mimo, "mimo", "one", "b")
        assertEquals("one", mimoSelected.activeProviderId)
        assertEquals("one/b", mimoSelected.defaultModel)

        nativeFile("root/.openclaw/openclaw.json").writeText("{}")
        val openClaw = OpenClawAgentConfigAdapter(context, ::container)
        configureModels(openClaw, "openclaw", "one", "https://one.example/v1", "a", "b")
        val openClawSelected = select(openClaw, "openclaw", "one", "b")
        assertEquals("one", openClawSelected.activeProviderId)
        assertEquals("one/b", openClawSelected.defaultModel)

        nativeFile("root/.claude/settings.json").writeText("{}")
        val claude = ClaudeCodeAgentConfigAdapter(context, ::container)
        configureModels(claude, "claude-code", "gateway", "https://gateway.example.com", "a", "b")
        val claudeSelected = select(claude, "claude-code", "gateway.example.com", "b")
        assertEquals("gateway.example.com", claudeSelected.activeProviderId)
        assertEquals("b", claudeSelected.defaultModel)

        nativeFile("root/.codex/config.toml").writeText("")
        val codex = CodexAgentConfigAdapter(context, ::container)
        configureModels(codex, "codex", "one", "https://one.example/v1", "a", "b")
        val codexSelected = select(codex, "codex", "one", "a")
        assertEquals("one", codexSelected.activeProviderId)
        assertEquals("a", codexSelected.defaultModel)

        nativeFile("workspace/.kf/software/kite.hermes.core/home/config.yaml").writeText("")
        val hermes = HermesAgentConfigAdapter(context, ::container)
        configureModels(hermes, "hermes", "one", "https://one.example/v1", "a", "b")
        val hermesSelected = select(hermes, "hermes", "one", "b")
        assertEquals("one", hermesSelected.activeProviderId)
        assertEquals("b", hermesSelected.defaultModel)
    }

    @Test
    fun staleRevisionIsRejectedInsteadOfOverwritingNativeChange() = runTest {
        val file = nativeFile("root/.config/mimocode/mimocode.jsonc")
        file.writeText("{}")
        val adapter = MiMoCodeAgentConfigAdapter(context, ::container)
        val first = (adapter.readLive("mimo") as AgentConfigReadResult.Ready).snapshot
        file.writeText("""{"native":true}""")

        val result = adapter.apply(
            AgentConfigApplyRequest(
                "mimo",
                first.revision,
                listOf(AgentPersistentConfigChange.SetDefaultModel("mimo/model"))
            )
        )

        assertTrue(result is AgentConfigApplyResult.Conflict)
        assertTrue(file.readText().contains("native"))
    }

    private suspend fun configure(
        adapter: AgentConfigAdapter,
        agentId: String,
        providerId: String,
        baseUrl: String,
        modelId: String
    ): AgentConfigApplyResult.Applied {
        val before = (adapter.readLive(agentId) as AgentConfigReadResult.Ready).snapshot
        return adapter.apply(
            AgentConfigApplyRequest(
                agentId,
                before.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureProvider(
                        AgentProviderDraft(
                            providerId,
                            providerId.replaceFirstChar(Char::uppercase),
                            baseUrl,
                            listOf(AgentProviderModelSummary(modelId, modelId))
                        ),
                        AgentProviderCredentialChange.replace(SECRET)
                    )
                )
            )
        ) as AgentConfigApplyResult.Applied
    }

    private suspend fun configureModels(
        adapter: AgentConfigAdapter,
        agentId: String,
        providerId: String,
        baseUrl: String,
        vararg modelIds: String
    ): AgentConfigApplyResult.Applied {
        val before = (adapter.readLive(agentId) as AgentConfigReadResult.Ready).snapshot
        return adapter.apply(
            AgentConfigApplyRequest(
                agentId,
                before.revision,
                listOf(
                    AgentPersistentConfigChange.ConfigureProvider(
                        AgentProviderDraft(
                            providerId,
                            providerId.replaceFirstChar(Char::uppercase),
                            baseUrl,
                            modelIds.map { AgentProviderModelSummary(it, it) }
                        ),
                        AgentProviderCredentialChange.replace(SECRET)
                    )
                )
            )
        ) as AgentConfigApplyResult.Applied
    }

    private suspend fun select(
        adapter: AgentConfigAdapter,
        agentId: String,
        providerId: String,
        modelId: String
    ) = (adapter.readLive(agentId) as AgentConfigReadResult.Ready).snapshot.let { before ->
        (adapter.apply(
            AgentConfigApplyRequest(
                agentId,
                before.revision,
                listOf(AgentPersistentConfigChange.SelectProvider(providerId, modelId))
            )
        ) as AgentConfigApplyResult.Applied).snapshot
    }

    private fun nativeFile(relative: String): File = File(rootfs, relative).also { it.parentFile?.mkdirs() }

    private fun container(): ContainerRecord = ContainerRecord(
        id = "test",
        displayName = "Test",
        imageName = "ubuntu",
        rootfsPath = rootfs.absolutePath,
        workspacePath = File(rootfs, "workspace").absolutePath,
        createdAt = 1L,
        status = ContainerStatus.RUNNING
    )

    private companion object {
        const val SECRET = "secret-never-project"
    }
}
