package com.kite.app.agent.discovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.registration.AgentConfigurationStatus
import com.kite.app.agent.registration.AgentDefinition
import com.kite.app.agent.registration.AgentInstallationStatus
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentLaunchStatus
import com.kite.app.agent.registration.AgentRegistration
import com.kite.app.agent.registration.AgentRegistrationSource
import com.kite.app.agent.registration.AgentRegistryEntry
import com.kite.app.agent.registration.AgentRegistrySnapshot
import com.kite.app.agent.registration.AgentRuntimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AcpAgentCompatibilityCatalogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `公共 ID 只映射稳定身份而能力继续读取本地注册表`() {
        val catalog = AcpAgentCatalogSnapshot(
            version = "1.0.0",
            entries = listOf(candidate("codex-acp"), candidate("future-agent"), candidate("qwen-code")),
        )
        val registry = AgentRegistrySnapshot(
            entries = listOf(registryEntry("codex")),
            conflicts = emptyList(),
        )

        val resolved = AcpAgentCompatibilityCatalog(context).resolve(catalog, registry)

        assertEquals(AcpAgentIntegrationState.Integrated, resolved.entries[0].state)
        assertEquals("codex", resolved.entries[0].localAgentId)
        assertEquals(AcpAgentIntegrationState.Candidate, resolved.entries[1].state)
        assertEquals(null, resolved.entries[1].localAgentId)
        assertEquals(AcpAgentIntegrationState.Declared, resolved.entries[2].state)
        assertEquals("qwen", resolved.entries[2].localAgentId)
        assertEquals(1, resolved.integratedCount)
    }

    @Test
    fun `重复公共 ID 会拒绝整个兼容声明`() {
        val error = runCatching {
            AcpAgentCompatibilityParser.parse(
                """{"version":1,"aliases":[
                    {"registryId":"same","agentId":"one"},
                    {"registryId":"same","agentId":"two"}
                ]}""",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun candidate(id: String) = AcpAgentCatalogEntry(
        id = id,
        displayName = id,
        version = "1.0.0",
        description = "",
        distributions = listOf(
            AcpAgentDistribution(
                kind = AcpAgentDistributionKind.Npx,
                packageSpec = "$id@1.0.0",
            ),
        ),
    )

    private fun registryEntry(agentId: String) = AgentRegistryEntry(
        registration = AgentRegistration(
            definition = AgentDefinition(agentId, agentId),
            source = AgentRegistrationSource.Resource("kite.$agentId"),
            launch = AgentLaunchSpec.Managed(
                providerId = agentId,
                protocol = "acp",
                transport = "stdio",
                argv = listOf(agentId),
            ),
        ),
        installationStatus = AgentInstallationStatus.Installed,
        configurationStatus = AgentConfigurationStatus.Ready,
        runtimeStatus = AgentRuntimeStatus.Stopped,
        launchStatus = AgentLaunchStatus.Ready,
    )
}
