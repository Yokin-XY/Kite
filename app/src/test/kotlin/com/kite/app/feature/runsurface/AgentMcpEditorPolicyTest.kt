package com.kite.app.feature.runsurface

import com.kite.app.agent.config.AgentMcpConnectionState
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMcpEditorPolicyTest {
    @Test
    fun givesAnActionableMessageWhenIdIsEmpty() {
        val result = AgentMcpEditorPolicy.buildDraft(
            id = " ",
            transport = AgentMcpTransport.Stdio,
            enabled = true,
            command = "",
            argumentsText = "",
            url = "",
            referencesText = ""
        ) as AgentMcpDraftBuildResult.Invalid

        assertEquals("请输入 MCP ID", result.message)
    }

    @Test
    fun buildsLocalDraftWithoutShellJoiningOrSecretValues() {
        val result = AgentMcpEditorPolicy.buildDraft(
            id = "github",
            transport = AgentMcpTransport.Stdio,
            enabled = true,
            command = "npx",
            argumentsText = "-y\n@modelcontextprotocol/server-github",
            url = "",
            referencesText = "GITHUB_TOKEN=GITHUB_MCP_TOKEN"
        ) as AgentMcpDraftBuildResult.Ready

        assertEquals("npx", result.draft.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-github"), result.draft.arguments)
        assertEquals(
            listOf(AgentMcpEnvironmentReference("GITHUB_TOKEN", "GITHUB_MCP_TOKEN")),
            result.draft.environmentReferences
        )
    }

    @Test
    fun rejectsMalformedReferenceAndRemoteCredentialsInUrl() {
        assertTrue(AgentMcpEditorPolicy.buildDraft(
            "demo",
            AgentMcpTransport.Stdio,
            true,
            "npx",
            "",
            "",
            "TOKEN=not-lowercase"
        ) is AgentMcpDraftBuildResult.Invalid)
        assertTrue(AgentMcpEditorPolicy.buildDraft(
            "demo",
            AgentMcpTransport.RemoteHttpOrSse,
            true,
            "",
            "",
            "https://user:secret@example.invalid/mcp",
            ""
        ) is AgentMcpDraftBuildResult.Invalid)
    }

    @Test
    fun savedStateAndConnectionStateRemainSeparate() {
        val server = AgentMcpSummary(
            id = "demo",
            kind = "remote",
            enabled = true,
            transport = AgentMcpTransport.RemoteHttpOrSse
        )

        assertEquals(
            "已保存，连接由 Agent 加载时确认",
            AgentMcpUiPolicy.connectionLabel(server, AgentMcpConnectionState.NotChecked),
        )
        assertEquals("正在检查连接…", AgentMcpUiPolicy.connectionLabel(server, AgentMcpConnectionState.Checking))
        assertEquals("可用", AgentMcpUiPolicy.connectionLabel(server, AgentMcpConnectionState.Available))
        assertEquals("不可用", AgentMcpUiPolicy.connectionLabel(server, AgentMcpConnectionState.Unavailable))

        val checkable = server.copy(allowedOperations = setOf(AgentMcpOperation.CheckConnection))
        assertTrue(AgentMcpUiPolicy.supportsConnectionCheck(checkable))
        assertEquals(
            "已保存，尚未检查",
            AgentMcpUiPolicy.connectionLabel(checkable, AgentMcpConnectionState.NotChecked),
        )
    }
}
