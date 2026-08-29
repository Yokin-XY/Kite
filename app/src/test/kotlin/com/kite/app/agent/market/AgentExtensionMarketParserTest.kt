package com.kite.app.agent.market

import com.kite.app.agent.config.AgentMcpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentExtensionMarketParserTest {
    @Test
    fun parsesClawHubOwnerQualifiedInstallReference() {
        val items = AgentExtensionMarketParser.parseClawHubSearch(
            """
            {"results":[{
              "displayName":"Github",
              "ownerHandle":"steipete",
              "install":{"kind":"clawhub","reference":"steipete/github"},
              "native":{"skill":{"summary":"Use gh safely"}}
            }]}
            """.trimIndent(),
        )

        assertEquals("clawhub:steipete/github", items.single().id)
        assertEquals(
            AgentExtensionInstallSpec.Skill("steipete", "github"),
            items.single().installSpec,
        )
    }

    @Test
    fun keepsOnlyLatestInstallableMcpEntries() {
        val items = AgentExtensionMarketParser.parseMcpRegistrySearch(
            """
            {"servers":[
              {"server":{"name":"com.example/demo","title":"Demo","version":"1.0.0","remotes":[{"type":"streamable-http","url":"https://example.com/mcp"}]},"_meta":{"io.modelcontextprotocol.registry/official":{"status":"active","isLatest":true}}},
              {"server":{"name":"com.example/demo","title":"Demo","version":"0.9.0","remotes":[{"type":"streamable-http","url":"https://example.com/old"}]},"_meta":{"io.modelcontextprotocol.registry/official":{"status":"active","isLatest":false}}}
            ]}
            """.trimIndent(),
        )

        assertEquals(1, items.size)
        val spec = items.single().installSpec as AgentExtensionInstallSpec.Mcp
        assertEquals(AgentMcpTransport.StreamableHttp, spec.server.transport)
        assertTrue(spec.server.url!!.endsWith("/mcp"))
    }
}
