package com.kite.app.agent.pi

import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.process.AgentProcessChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PiRpcAgentProviderTest {
    @Test
    fun `maps native rpc catalog prompt and stream into sdk`() = runBlocking {
        val channel = FakePiChannel()
        val events = mutableListOf<AgentSessionEvent>()
        val provider = PiRpcAgentProvider(
            PiRpcProviderDescriptor("pi", "Pi Coding Agent"),
            PiRpcProcessLauncher { channel },
        )
        val connectedResult = provider.connect(
            AgentConnectionRequest(AgentClientInfo("kite", "test")),
            AgentClientEndpoint(
                eventSink = { _, event -> events += event },
                permissionHandler = { AgentPermissionOutcome.Cancelled },
            ),
        )
        assertTrue(connectedResult.toString(), connectedResult is AgentOperationResult.Success)
        val connected = connectedResult as AgentOperationResult.Success
        val connection = connected.value
        assertTrue(connection.capabilities.prompt.images)
        assertTrue(connection.capabilities.prompt.resourceLinks)

        val created = connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success
        assertEquals("pi-session", created.value.id)
        assertTrue(created.value.configuration.any { it.id == "pi.rpc.model" })
        assertTrue(created.value.configuration.any { it.id == "pi.rpc.thinking" })

        val result = connection.prompt(
            AgentPromptRequest("pi-session", listOf(AgentContent.Text("hello")))
        )
        assertTrue(result is AgentOperationResult.Success)
        assertTrue(events.filterIsInstance<AgentSessionEvent.MessageChunk>()
            .any { (it.content as? AgentContent.Text)?.text == "world" })
        connection.disconnect()
    }

    @Test
    fun `生成期间通过Pi steer立即插话`() = runBlocking {
        val channel = FakePiChannel(settlePromptAutomatically = false)
        val provider = PiRpcAgentProvider(
            PiRpcProviderDescriptor("pi", "Pi Coding Agent"),
            PiRpcProcessLauncher { channel },
        )
        val connected = provider.connect(
            AgentConnectionRequest(AgentClientInfo("kite", "test")),
            AgentClientEndpoint(
                eventSink = { _, _ -> Unit },
                permissionHandler = { AgentPermissionOutcome.Cancelled },
            ),
        ) as AgentOperationResult.Success
        val connection = connected.value
        connection.newSession(AgentNewSessionRequest("/workspace"))
        val first = async {
            connection.prompt(AgentPromptRequest("pi-session", listOf(AgentContent.Text("先检查项目"))))
        }
        withTimeout(2_000L) { channel.promptStarted.await() }

        val steered = connection.steer(
            AgentPromptRequest("pi-session", listOf(AgentContent.Text("改为只检查测试"))),
        )

        assertTrue(steered is AgentOperationResult.Success)
        val steer = channel.requests.single { it.getString("type") == "steer" }
        assertEquals("改为只检查测试", steer.getString("message"))
        channel.settlePrompt()
        assertTrue(first.await() is AgentOperationResult.Success)
        connection.disconnect()
    }

    private class FakePiChannel(
        private val settlePromptAutomatically: Boolean = true,
    ) : AgentProcessChannel {
        private val output = MutableSharedFlow<String>(replay = 32, extraBufferCapacity = 32)
        val requests = mutableListOf<JSONObject>()
        val promptStarted = CompletableDeferred<Unit>()
        override val stdoutLines: Flow<String> = output
        override val stderrLines: Flow<String> = MutableSharedFlow()
        override val pid: Long = 42L
        override var isAlive: Boolean = true

        override suspend fun writeLine(line: String) {
            val request = JSONObject(line)
            requests += JSONObject(request.toString())
            val id = request.optString("id")
            val type = request.getString("type")
            val data = when (type) {
                "get_state" -> JSONObject()
                    .put("sessionId", "pi-session")
                    .put("sessionFile", "/root/.pi/agent/sessions/pi-session.jsonl")
                    .put("thinkingLevel", "medium")
                    .put("model", model())
                "get_available_models" -> JSONObject().put("models", JSONArray().put(model()))
                "get_available_thinking_levels" -> JSONObject().put("levels", JSONArray(listOf("off", "medium", "high")))
                "get_commands" -> JSONObject().put("commands", JSONArray().put(
                    JSONObject().put("name", "skill:test").put("description", "test skill")
                ))
                "new_session" -> JSONObject().put("cancelled", false)
                else -> null
            }
            output.emit(JSONObject().put("id", id).put("type", "response").put("command", type).put("success", true)
                .apply { data?.let { put("data", it) } }.toString())
            if (type == "prompt") {
                promptStarted.complete(Unit)
                output.emit(JSONObject().put("type", "agent_start").toString())
                output.emit(JSONObject().put("type", "message_update").put(
                    "assistantMessageEvent",
                    JSONObject().put("type", "text_delta").put("delta", "world"),
                ).toString())
                if (settlePromptAutomatically) settlePrompt()
            }
        }

        suspend fun settlePrompt() {
            output.emit(JSONObject().put("type", "agent_settled").toString())
        }

        override suspend fun awaitExit(): Int = 0
        override suspend fun stop(gracePeriodMs: Long): Int { isAlive = false; return 0 }
        override fun close() { isAlive = false }

        private fun model() = JSONObject()
            .put("id", "gpt-test")
            .put("name", "GPT Test")
            .put("provider", "openai")
            .put("reasoning", true)
            .put("input", JSONArray(listOf("text", "image")))
    }
}
