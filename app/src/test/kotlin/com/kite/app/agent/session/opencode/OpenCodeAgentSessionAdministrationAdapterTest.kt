package com.kite.app.agent.session.opencode

import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.session.AgentSessionCommandExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeAgentSessionAdministrationAdapterTest {
    @Test
    fun `删除只通过安全 argv 调用 OpenCode 官方会话命令`() = runTest {
        var capturedArgv: List<String>? = null
        var capturedCwd: String? = null
        val adapter = OpenCodeAgentSessionAdministrationAdapter(
            AgentSessionCommandExecutor { argv, cwd ->
                capturedArgv = argv
                capturedCwd = cwd
                AgentOperationResult.Success(Unit)
            }
        )

        val result = adapter.deleteSession("ses_123", "/workspace/project")

        assertTrue(result is AgentOperationResult.Success)
        assertEquals(listOf("opencode", "session", "delete", "ses_123"), capturedArgv)
        assertEquals("/workspace/project", capturedCwd)
    }

    @Test
    fun `空会话 ID 不启动外部命令`() = runTest {
        var invoked = false
        val adapter = OpenCodeAgentSessionAdministrationAdapter(
            AgentSessionCommandExecutor { _, _ ->
                invoked = true
                AgentOperationResult.Success(Unit)
            }
        )

        val result = adapter.deleteSession("  ", "/workspace")

        assertTrue(result is AgentOperationResult.Failure)
        assertTrue(!invoked)
    }
}
