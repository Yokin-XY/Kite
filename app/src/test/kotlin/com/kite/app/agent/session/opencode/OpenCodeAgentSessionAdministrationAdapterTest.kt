package com.kite.app.agent.session.opencode

import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.session.AgentSessionCommand
import com.kite.app.agent.session.AgentSessionCommandExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeAgentSessionAdministrationAdapterTest {
    @Test
    fun `删除只通过安全 argv 调用 OpenCode 官方会话命令`() = runTest {
        var captured: AgentSessionCommand? = null
        val adapter = OpenCodeAgentSessionAdministrationAdapter(
            AgentSessionCommandExecutor { command ->
                captured = command
                AgentOperationResult.Success(Unit)
            }
        )

        val result = adapter.deleteSession("ses_123", "/workspace/project")

        assertTrue(result is AgentOperationResult.Success)
        assertEquals(listOf("opencode", "session", "delete", "ses_123"), captured?.argv)
        assertEquals("/workspace/project", captured?.cwd)
        assertEquals(null, captured?.stdinLine)
    }

    @Test
    fun `重命名通过 OpenCode 官方会话 API 而非 Kite 别名`() = runTest {
        var captured: AgentSessionCommand? = null
        val adapter = OpenCodeAgentSessionAdministrationAdapter(
            AgentSessionCommandExecutor { command ->
                captured = command
                AgentOperationResult.Success(Unit)
            }
        )

        val result = adapter.renameSession(
            AgentSessionRenameRequest("ses_123", "  新名称 \"A\" \\ B  "),
            "/workspace/project",
        )

        assertTrue(result is AgentOperationResult.Success)
        assertEquals(listOf("sh", "-c"), captured?.argv?.take(2))
        assertTrue(captured?.argv?.get(2)?.contains("PATCH") == true)
        assertTrue(captured?.argv?.get(2)?.contains("/session/${'$'}session_id") == true)
        assertEquals("ses_123", captured?.argv?.last())
        assertEquals("{\"title\":\"新名称 \\\"A\\\" \\\\ B\"}", captured?.stdinLine)
        assertEquals("/workspace/project", captured?.cwd)
        assertTrue(adapter.supportsRename)
    }

    @Test
    fun `空会话 ID 不启动外部命令`() = runTest {
        var invoked = false
        val adapter = OpenCodeAgentSessionAdministrationAdapter(
            AgentSessionCommandExecutor {
                invoked = true
                AgentOperationResult.Success(Unit)
            }
        )

        val result = adapter.deleteSession("  ", "/workspace")

        assertTrue(result is AgentOperationResult.Failure)
        assertTrue(!invoked)
    }
}
