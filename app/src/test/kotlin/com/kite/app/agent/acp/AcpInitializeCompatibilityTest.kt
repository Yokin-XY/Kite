@file:OptIn(com.agentclientprotocol.annotations.UnstableApi::class)

package com.kite.app.agent.acp

import com.agentclientprotocol.model.InitializeResponse
import com.kite.app.agent.contract.AgentAuthenticationMethod
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-07-29 在同一台魅族 18、同一条 Kite CardRun/KFShell/PRoot 运行链采集的 initialize 夹具。
 * 两个样本始终经过同一个解析和映射函数，不允许根据产品名称分支。
 */
class AcpInitializeCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun realInitializeFixturesUseOneCapabilityAndAuthenticationMapper() {
        fixtures.forEach { fixture ->
            val response = json.decodeFromString(InitializeResponse.serializer(), fixture.initializeResult)
            val mapped = AcpAgentMapper.capabilities(response.agentCapabilities, response.authMethods)

            assertEquals(fixture.label, 1, response.protocolVersion)
            assertTrue(fixture.label, mapped.prompt.text)
            assertTrue(fixture.label, mapped.prompt.resourceLinks)
            assertTrue(fixture.label, mapped.prompt.images)
            assertFalse(fixture.label, mapped.prompt.audio)
            assertTrue(fixture.label, mapped.prompt.embeddedResources)
            assertTrue(fixture.label, mapped.sessions.load)
            assertTrue(fixture.label, mapped.sessions.list)
            assertTrue(fixture.label, mapped.sessions.resume)
            assertEquals(fixture.label, fixture.fork, mapped.sessions.fork)
            assertEquals(fixture.label, fixture.close, mapped.sessions.close)
            assertFalse(fixture.label, mapped.sessions.delete)
            assertFalse(fixture.label, mapped.sessions.additionalDirectories)
            assertTrue(fixture.label, mapped.mcp.stdio)
            assertTrue(fixture.label, mapped.mcp.http)
            assertTrue(fixture.label, mapped.mcp.sse)
            assertFalse(fixture.label, mapped.authentication.logout)

            val authentication = mapped.authentication.methods.single()
            assertEquals(fixture.label, fixture.authMethodId, authentication.id)
            assertEquals(fixture.label, fixture.authenticationType, authentication::class)
        }
    }

    @Test
    fun terminalAuthenticationPreservesArgumentsAndExtensionMetadata() {
        val fixture = fixtures.single { it.label == "Kimi Code CLI 0.27.0" }
        val response = json.decodeFromString(InitializeResponse.serializer(), fixture.initializeResult)
        val method = AcpAgentMapper.capabilities(response.agentCapabilities, response.authMethods)
            .authentication.methods.single() as AgentAuthenticationMethod.Terminal

        assertEquals(listOf("--login"), method.arguments)
        assertTrue(method.environment.isEmpty())
        assertTrue(method.extension?.payload?.contains("terminal-auth") == true)
        assertTrue(method.extension?.payload?.contains("/workspace/.kf/bin/kimi") == true)
    }

    private data class Fixture(
        val label: String,
        val initializeResult: String,
        val fork: Boolean,
        val close: Boolean,
        val authMethodId: String,
        val authenticationType: kotlin.reflect.KClass<out AgentAuthenticationMethod>
    )

    private companion object {
        val fixtures = listOf(
            Fixture(
                label = "OpenCode 1.18.5",
                initializeResult = """
                    {
                      "protocolVersion": 1,
                      "agentCapabilities": {
                        "loadSession": true,
                        "mcpCapabilities": {"http": true, "sse": true},
                        "promptCapabilities": {"embeddedContext": true, "image": true},
                        "sessionCapabilities": {"close": {}, "fork": {}, "list": {}, "resume": {}}
                      },
                      "authMethods": [{
                        "description": "Run `opencode auth login` in the terminal",
                        "name": "Login with opencode",
                        "id": "opencode-login"
                      }],
                      "agentInfo": {"name": "OpenCode", "version": "1.18.5"}
                    }
                """.trimIndent(),
                fork = true,
                close = true,
                authMethodId = "opencode-login",
                authenticationType = AgentAuthenticationMethod.AgentManaged::class
            ),
            Fixture(
                label = "Kimi Code CLI 0.27.0",
                initializeResult = """
                    {
                      "protocolVersion": 1,
                      "agentCapabilities": {
                        "loadSession": true,
                        "promptCapabilities": {"image": true, "audio": false, "embeddedContext": true},
                        "mcpCapabilities": {"http": true, "sse": true},
                        "sessionCapabilities": {"list": {}, "resume": {}}
                      },
                      "authMethods": [{
                        "id": "login",
                        "type": "terminal",
                        "name": "Login with Kimi account",
                        "description": "Open the device-code login flow in a terminal.",
                        "args": ["--login"],
                        "env": {},
                        "_meta": {
                          "terminal-auth": {
                            "type": "terminal",
                            "label": "Login with Kimi account",
                            "command": "/workspace/.kf/bin/kimi",
                            "args": ["login"],
                            "env": {}
                          }
                        }
                      }],
                      "agentInfo": {"name": "Kimi Code CLI", "version": "0.27.0"}
                    }
                """.trimIndent(),
                fork = false,
                close = false,
                authMethodId = "login",
                authenticationType = AgentAuthenticationMethod.Terminal::class
            )
        )
    }
}
