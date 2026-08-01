package com.kite.app.agent.auth

import com.kite.app.agent.registration.AgentOfficialAccountCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOfficialAccountResultPolicyTest {
    @Test
    fun `未登录文本优先于其包含的登录文本`() {
        val command = AgentOfficialAccountCommand(
            argv = listOf("agent", "status"),
            loggedInPatterns = listOf("logged in"),
            loggedOutPatterns = listOf("not logged in"),
        )

        assertEquals(
            false,
            AgentOfficialAccountResultPolicy.resolveStatus(
                command,
                AgentOfficialAccountCommandResult(1, "Auth: not logged in"),
            ),
        )
        assertEquals(
            true,
            AgentOfficialAccountResultPolicy.resolveStatus(
                command,
                AgentOfficialAccountCommandResult(0, "Auth: \u001B[32mlogged in\u001B[0m"),
            ),
        )
    }

    @Test
    fun `无成功文本约束时只按进程退出码判断动作结果`() {
        val command = AgentOfficialAccountCommand(argv = listOf("agent", "login"))

        assertTrue(
            AgentOfficialAccountResultPolicy.succeeded(
                command,
                AgentOfficialAccountCommandResult(0, ""),
            ),
        )
        assertFalse(
            AgentOfficialAccountResultPolicy.succeeded(
                command,
                AgentOfficialAccountCommandResult(1, "login failed"),
            ),
        )
    }
}
