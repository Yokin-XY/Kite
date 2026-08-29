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
    fun parsesClawHubBrowseWithChineseMetadataAndMetrics() {
        val items = AgentExtensionMarketParser.parseClawHubBrowse(
            """
            {"items":[{
              "slug":"browser-cli-tool-free",
              "displayName":"浏览器CLI工具-免费版",
              "summary":"支持中文交互的浏览器自动化工具",
              "stats":{"downloads":12650,"stars":42},
              "updatedAt":1786929376714,
              "latestVersion":{"version":"1.2.0"}
            }]}
            """.trimIndent(),
        )

        val item = items.single()
        assertEquals("浏览器CLI工具-免费版", item.title)
        assertEquals("支持中文交互的浏览器自动化工具", item.description)
        assertEquals(12_650L, item.downloads)
        assertEquals(42L, item.stars)
        assertEquals("1.2.0", item.versionLabel)
        assertEquals(
            AgentExtensionInstallSpec.Skill(null, "browser-cli-tool-free", "1.2.0"),
            item.installSpec,
        )
    }

    @Test
    fun browseUsesOfficialClawHubSortAndSafetyFilter() {
        var requestedUrl = ""
        val repository = AgentExtensionMarketRepository(AgentExtensionMarketRemote { url, _ ->
            requestedUrl = url
            AgentMarketHttpPayload("{\"items\":[]}".toByteArray(), "application/json")
        })

        repository.browseSkills(AgentExtensionMarketSort.Trending)

        assertTrue(requestedUrl.contains("sort=trending"))
        assertTrue(requestedUrl.contains("nonSuspiciousOnly=true"))
    }

    @Test
    fun sendsChineseSearchDirectlyToClawHubAndKeepsChineseResult() {
        var requestedUrl = ""
        val repository = AgentExtensionMarketRepository(AgentExtensionMarketRemote { url, _ ->
            requestedUrl = url
            AgentMarketHttpPayload(
                """
                {"results":[{
                  "displayName":"浏览器自动化",
                  "ownerHandle":"demo",
                  "downloads":88,
                  "install":{"kind":"clawhub","reference":"demo/browser"},
                  "native":{"skill":{"summary":"支持中文搜索和操作"}}
                }]}
                """.trimIndent().toByteArray(),
                "application/json",
            )
        })

        val snapshot = repository.search(AgentExtensionMarketKind.Skill, "浏览器")

        assertTrue(requestedUrl.contains("q=%E6%B5%8F%E8%A7%88%E5%99%A8"))
        assertEquals("浏览器自动化", snapshot.items.single().title)
        assertEquals("支持中文搜索和操作", snapshot.items.single().description)
    }

    @Test
    fun parsesClawHubOwnerQualifiedInstallReference() {
        val items = AgentExtensionMarketParser.parseClawHubSearch(
            """
            {"results":[{
              "displayName":"Github",
              "ownerHandle":"steipete",
              "downloads":321,
              "install":{"kind":"clawhub","reference":"steipete/github"},
              "native":{"skill":{"summary":"Use gh safely","stats":{"stars":9}}}
            }]}
            """.trimIndent(),
        )

        assertEquals("clawhub:steipete/github", items.single().id)
        assertEquals(
            AgentExtensionInstallSpec.Skill("steipete", "github"),
            items.single().installSpec,
        )
        assertEquals(321L, items.single().downloads)
        assertEquals(9L, items.single().stars)
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
