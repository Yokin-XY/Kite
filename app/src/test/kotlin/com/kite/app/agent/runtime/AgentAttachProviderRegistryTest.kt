package com.kite.app.agent.runtime

import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAttachProviderRegistryTest {
    @After
    fun tearDown() {
        AgentAttachProviderRegistry.resetForTest()
    }

    @Test
    fun `连接引用只绑定一个外部 provider 并可按原对象注销`() {
        val first = provider("first")
        val second = provider("second")

        assertTrue(AgentAttachProviderRegistry.register(" connections/remote ", first))
        assertFalse(AgentAttachProviderRegistry.register("connections/remote", second))
        assertSame(first, AgentAttachProviderRegistry.provider("connections/remote"))
        assertTrue(AgentAttachProviderRegistry.contains("connections/remote"))
        assertFalse(AgentAttachProviderRegistry.unregister("connections/remote", second))
        assertTrue(AgentAttachProviderRegistry.unregister("connections/remote", first))
        assertNull(AgentAttachProviderRegistry.provider("connections/remote"))
    }

    @Test
    fun `空连接引用不会进入目录`() {
        assertFalse(AgentAttachProviderRegistry.register("  ", provider("empty")))
        assertEquals(false, AgentAttachProviderRegistry.contains(""))
    }

    private fun provider(providerId: String): KiteAgentProvider = object : KiteAgentProvider {
        override val id: String = providerId

        override suspend fun connect(
            request: AgentConnectionRequest,
            client: AgentClientEndpoint
        ): AgentOperationResult<KiteAgentConnection> = AgentOperationResult.Unsupported("test")
    }
}
