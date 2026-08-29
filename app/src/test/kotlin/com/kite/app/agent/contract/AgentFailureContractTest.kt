package com.kite.app.agent.contract

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFailureContractTest {
    @Test
    fun `启动失败要求修复运行环境而不是盲目重试`() {
        val failure = AgentFailures.launch("启动失败", IOException("missing executable"))

        assertEquals(AgentFailureCode.LaunchFailed, failure.code)
        assertEquals(AgentFailurePhase.Launch, failure.details?.phase)
        assertEquals(AgentFailureRecovery.RepairRuntime, failure.details?.recovery)
        assertFalse(failure.details?.retryable ?: true)
    }

    @Test
    fun `初始化错误沿异常链识别网络阶段`() {
        val failure = AgentFailures.initialize(
            "初始化失败",
            IOException("wrapped", UnknownHostException("registry.example")),
        )

        assertEquals(AgentFailurePhase.Network, failure.details?.phase)
        assertEquals(AgentFailureRecovery.Retry, failure.details?.recovery)
        assertTrue(failure.details?.retryable == true)
    }

    @Test
    fun `协议认证错误保留统一登录恢复动作`() {
        val failure = AgentFailures.protocol(
            message = "需要登录",
            cause = IOException("unauthorized"),
            code = AgentFailureCode.AuthenticationRequired,
            extension = AgentProtocolExtension("acp", "json_rpc_error", "{}"),
        )

        assertEquals(AgentFailurePhase.Authentication, failure.details?.phase)
        assertEquals(AgentFailureRecovery.Authenticate, failure.details?.recovery)
        assertFalse(failure.details?.retryable ?: true)
        assertEquals("acp", failure.extension?.protocol)
    }
}
