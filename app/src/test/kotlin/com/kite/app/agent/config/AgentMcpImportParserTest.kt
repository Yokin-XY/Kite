package com.kite.app.agent.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentMcpImportParserTest {
    @Test
    fun parsesOfficialRemoteServerWithoutImportingSecretValues() {
        val candidate = AgentMcpImportParser.parse(
            """
            {
              "name": "ai.example/github-mcp",
              "title": "GitHub MCP",
              "version": "1.2.0",
              "remotes": [{
                "type": "streamable-http",
                "url": "https://example.com/mcp",
                "headers": [{"name":"Authorization","value":"Bearer {github_token}","isSecret":true}]
              }]
            }
            """.trimIndent(),
        ).single()

        assertEquals(AgentMcpTransport.StreamableHttp, candidate.server.transport)
        assertEquals("https://example.com/mcp", candidate.server.url)
        assertEquals(
            listOf(AgentMcpEnvironmentReference("Authorization", "GITHUB_TOKEN")),
            candidate.server.headerReferences,
        )
        assertFalse(candidate.server.headerReferences.any { it.environmentVariable.contains("Bearer") })
    }

    @Test
    fun parsesCommonClientConfigAsIndependentCandidates() {
        val candidates = AgentMcpImportParser.parse(
            """
            {"mcpServers": {
              "local": {"command":"npx","args":["-y","demo"],"env":{"TOKEN":"actual-secret"}},
              "remote": {"type":"sse","url":"https://example.com/sse"}
            }}
            """.trimIndent(),
        )

        assertEquals(listOf("local", "remote"), candidates.map { it.server.id }.sorted())
        val local = candidates.first { it.server.id == "local" }.server
        assertEquals(listOf("-y", "demo"), local.arguments)
        assertEquals(listOf(AgentMcpEnvironmentReference("TOKEN", "TOKEN")), local.environmentReferences)
    }
}
