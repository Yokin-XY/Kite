package com.kite.app.agent.config

import com.kite.app.agent.registration.AgentDefinition
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentRegistration
import com.kite.app.agent.registration.AgentRegistrationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentConfigAdapterRegistryTest {
    @Test
    fun registrationSelectsAdapterByStableIdRatherThanAgentName() {
        val adapter = FakeAdapter("shared-config")
        val registry = AgentConfigAdapterRegistry(listOf(adapter))
        val registration = AgentRegistration(
            definition = AgentDefinition("renamed-agent", "可以改名"),
            source = AgentRegistrationSource.Custom,
            launch = AgentLaunchSpec.Managed("provider", "acp", "stdio", listOf("agent", "acp")),
            configAdapterId = "shared-config"
        )

        assertEquals(adapter, registry.adapterFor(registration))
        assertNull(registry.adapterFor(registration.copy(configAdapterId = null)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateAdapterIdIsRejected() {
        AgentConfigAdapterRegistry(listOf(FakeAdapter("same"), FakeAdapter("same")))
    }

    private class FakeAdapter(override val adapterId: String) : AgentConfigAdapter {
        override fun capabilities() = AgentConfigCapabilities(emptySet())
        override suspend fun discover(agentId: String) = AgentConfigDiscovery(
            agentId,
            adapterId,
            AgentConfigDiscoveryState.Unsupported
        )
        override suspend fun readLive(agentId: String): AgentConfigReadResult =
            AgentConfigReadResult.Unavailable(discover(agentId))
        override fun validate(request: AgentConfigApplyRequest) = emptyList<AgentConfigValidationProblem>()
        override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult =
            AgentConfigApplyResult.Unavailable(discover(request.agentId))
    }
}
