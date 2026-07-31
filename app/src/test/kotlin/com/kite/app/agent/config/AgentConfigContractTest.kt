package com.kite.app.agent.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConfigContractTest {
    @Test
    fun mcpChangeStringDoesNotExposePlainTextValues() {
        val secret = "header-secret-value"
        val request = AgentConfigApplyRequest(
            agentId = "agent",
            expectedRevision = "revision",
            changes = listOf(
                AgentPersistentConfigChange.PutMcpServer(
                    serverId = "demo",
                    configuration = AgentConfigValue.ObjectValue(
                        mapOf(
                            "headers" to AgentConfigValue.ObjectValue(
                                mapOf("Authorization" to AgentConfigValue.Text(secret))
                            ),
                            "token" to AgentConfigValue.EnvironmentReference("MCP_TOKEN")
                        )
                    )
                )
            )
        )

        assertFalse(request.toString().contains(secret))
        assertTrue(request.toString().contains("Text(length=${secret.length})"))
    }

    @Test
    fun itemOperationsDefaultToUnsupportedInsteadOfInventingActions() {
        val capabilities = AgentConfigCapabilities(
            supported = setOf(
                AgentPersistentConfigCapability.Mcp,
                AgentPersistentConfigCapability.Skill
            )
        )

        assertTrue(capabilities.mcpOperations.isEmpty())
        assertTrue(capabilities.skillOperations.isEmpty())
        assertTrue(AgentMcpSummary("mcp", "unknown", enabled = true).allowedOperations.isEmpty())
        assertTrue(AgentSkillSummary("skill").allowedOperations.isEmpty())
    }

    @Test
    fun mcpDraftOnlyCarriesEnvironmentReferences() {
        val draft = AgentMcpDraft(
            id = "github",
            transport = AgentMcpTransport.RemoteHttpOrSse,
            url = "https://example.invalid/mcp",
            headerReferences = listOf(
                AgentMcpEnvironmentReference("Authorization", "GITHUB_MCP_TOKEN")
            )
        )

        assertTrue(draft.toString().contains("GITHUB_MCP_TOKEN"))
        assertFalse(draft.toString().contains("Bearer secret"))
    }
}
