package com.kite.app.agent.registration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRegistryAssemblerTest {
    @Test
    fun displayNamesMayRepeatButStableAgentIdsMayNot() {
        val sameName = listOf(
            registration("agent-a", "同名 Agent", "provider-a"),
            registration("agent-b", "同名 Agent", "provider-b")
        )
        val valid = AgentRegistryAssembler.assemble(sameName, emptySet())
        val duplicate = AgentRegistryAssembler.assemble(
            sameName + registration("agent-a", "改名后的 Agent", "provider-c"),
            emptySet()
        )

        assertEquals(2, valid.entries.size)
        assertTrue(valid.conflicts.isEmpty())
        assertNull(duplicate.entry("agent-a"))
        assertEquals(listOf("custom"), duplicate.conflicts.single().sources)
    }

    @Test
    fun installationConfigurationAndRuntimeRemainSeparateFacts() {
        val resource = registration(
            agentId = "resource-agent",
            displayName = "Resource Agent",
            providerId = "resource-provider",
            source = AgentRegistrationSource.Resource("kite.agent.resource")
        )
        val custom = registration(
            agentId = "custom-agent",
            displayName = "Custom Agent",
            providerId = "custom-provider",
            configurationRequired = true
        )
        val snapshot = AgentRegistryAssembler.assemble(
            registrations = listOf(resource, custom),
            installedResourceIds = setOf("kite.agent.resource"),
            runningProviderIds = setOf("resource-provider")
        )

        val resourceEntry = requireNotNull(snapshot.entry("resource-agent"))
        assertTrue(resourceEntry.registered)
        assertEquals(AgentInstallationStatus.Installed, resourceEntry.installationStatus)
        assertEquals(AgentConfigurationStatus.NotRequired, resourceEntry.configurationStatus)
        assertEquals(AgentRuntimeStatus.Running, resourceEntry.runtimeStatus)
        assertEquals(AgentLaunchStatus.Ready, resourceEntry.launchStatus)
        assertTrue(resourceEntry.canOpen)

        val customEntry = requireNotNull(snapshot.entry("custom-agent"))
        assertEquals(AgentInstallationStatus.NotApplicable, customEntry.installationStatus)
        assertEquals(AgentConfigurationStatus.Required, customEntry.configurationStatus)
        assertEquals(AgentRuntimeStatus.Stopped, customEntry.runtimeStatus)
        assertFalse(customEntry.canOpen)
    }

    private fun registration(
        agentId: String,
        displayName: String,
        providerId: String,
        source: AgentRegistrationSource = AgentRegistrationSource.Custom,
        configurationRequired: Boolean = false
    ) = AgentRegistration(
        definition = AgentDefinition(agentId, displayName),
        source = source,
        launch = AgentLaunchSpec.Managed(providerId, "acp", "stdio", listOf(providerId, "acp")),
        configurationRequired = configurationRequired
    )
}
