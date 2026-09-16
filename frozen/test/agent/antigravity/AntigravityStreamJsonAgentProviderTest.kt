package com.kite.app.agent.antigravity

import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.process.AgentProcessChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AntigravityStreamJsonAgentProviderTest {
    @Test
    fun `持久流映射模型推理权限工具和多轮消息`() = runBlocking {
        val launcher = FakeAntigravityLauncher()
        val events = mutableListOf<AgentSessionEvent>()
        val connection = connect(launcher, events)

        val created = connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success
        val snapshot = created.value
        assertEquals("agy-session-1", snapshot.id)
        val model = snapshot.configuration.single { it.category == AgentConfigCategory.Model } as AgentConfigOption.Select
        assertEquals("__agent_default__", model.currentValue)
        assertEquals(listOf("gemini-3.7-flash-high", "claude-sonnet-4-6"), model.choices.drop(1).map { it.value })

        assertTrue(connection.prompt(AgentPromptRequest(snapshot.id, listOf(AgentContent.Text("第一轮")))) is AgentOperationResult.Success)
        assertTrue(connection.prompt(AgentPromptRequest(snapshot.id, listOf(AgentContent.Text("第二轮")))) is AgentOperationResult.Success)
        assertEquals(2, launcher.sessionChannels.first().inputs.size)
        assertTrue(events.filterIsInstance<AgentSessionEvent.MessageChunk>().any {
            it.role == AgentMessageRole.Assistant && (it.content as? AgentContent.Text)?.text == "回答2"
        })
        assertTrue(events.any { it is AgentSessionEvent.ToolCallStarted })
        assertTrue(events.any { it is AgentSessionEvent.ToolCallUpdated })

        assertTrue(
            connection.setConfiguration(
                snapshot.id,
                "antigravity.model",
                AgentConfigValue.Select("claude-sonnet-4-6"),
            ) is AgentOperationResult.Success,
        )
        assertTrue(
            connection.setConfiguration(
                snapshot.id,
                "antigravity.effort",
                AgentConfigValue.Select("high"),
            ) is AgentOperationResult.Success,
        )
        assertTrue(connection.setMode(snapshot.id, "yolo") is AgentOperationResult.Success)
        assertEquals(1, launcher.sessionArguments.size)
        assertTrue(connection.prompt(AgentPromptRequest(snapshot.id, listOf(AgentContent.Text("第三轮")))) is AgentOperationResult.Success)
        val finalArgs = launcher.sessionArguments.last()
        assertTrue(finalArgs.containsAll(listOf("--conversation", snapshot.id, "--model", "claude-sonnet-4-6")))
        assertTrue(finalArgs.containsAll(listOf("--effort", "high", "--dangerously-skip-permissions")))
        connection.disconnect()
    }

    @Test
    fun `从官方JSONL目录列出并原子回放历史后继续同一会话`() = runBlocking {
        val root = Files.createTempDirectory("antigravity-history").toFile()
        val registry = root.resolve("projects.json").apply {
            writeText(JSONObject().put("projects", JSONObject().put("/physical/workspace", "kite-project")).toString())
        }
        val chats = root.resolve("tmp/kite-project/chats").apply { mkdirs() }
        chats.resolve("history.jsonl").writeText(
            listOf(
                "",
                JSONObject()
                    .put("sessionId", "history-session")
                    .put("projectHash", "kite-project")
                    .put("startTime", "2026-08-30T01:00:00Z")
                    .put("lastUpdated", "2026-08-30T01:01:00Z")
                    .put("kind", "main"),
                JSONObject().put("type", "user").put(
                    "content",
                    JSONArray().put(JSONObject().put("text", "旧问题")),
                ),
                JSONObject().put("type", "assistant").put(
                    "content",
                    JSONArray().put(JSONObject().put("text", "旧回答")),
                ),
                JSONObject().put("\$set", JSONObject().put("lastUpdated", Instant.parse("2026-08-30T01:02:00Z"))),
            ).joinToString("\n", transform = Any::toString),
        )
        val resolver = AntigravitySessionFileResolver { path ->
            when (path) {
                "/root/.gemini/projects.json" -> registry
                "/root/.gemini/tmp/kite-project/chats" -> chats
                else -> null
            }
        }
        val launcher = FakeAntigravityLauncher()
        val events = mutableListOf<Pair<String, AgentSessionEvent>>()
        val provider = AntigravityStreamJsonAgentProvider(
            AntigravityProviderDescriptor("antigravity", "Antigravity"),
            launcher,
            sessionFileResolver = resolver,
            sessionPathMapper = AntigravitySessionPathMapper { path ->
                path.replace("/workspace", "/physical/workspace")
            },
        )
        val connected = provider.connect(
            AgentConnectionRequest(AgentClientInfo("kite", "test")),
            AgentClientEndpoint(
                eventSink = { sessionId, event -> events += sessionId to event },
                permissionHandler = { AgentPermissionOutcome.Cancelled },
            ),
        ) as AgentOperationResult.Success
        val connection = connected.value

        val listed = connection.listSessions(AgentSessionListRequest("/workspace")) as AgentOperationResult.Success
        assertEquals(listOf("history-session"), listed.value.sessions.map { it.id })
        val loaded = connection.loadSession(
            AgentExistingSessionRequest("history-session", "/workspace"),
        ) as AgentOperationResult.Success
        assertEquals("history-session", loaded.value.id)
        assertTrue(launcher.sessionArguments.last().containsAll(listOf("--conversation", "history-session")))
        assertEquals(
            listOf("旧问题", "旧回答"),
            events.mapNotNull { (_, event) -> (event as? AgentSessionEvent.MessageChunk)?.content as? AgentContent.Text }
                .map { it.text },
        )
        connection.disconnect()
        root.deleteRecursively()
        Unit
    }

    private suspend fun connect(
        launcher: FakeAntigravityLauncher,
        events: MutableList<AgentSessionEvent>,
    ) = (AntigravityStreamJsonAgentProvider(
        AntigravityProviderDescriptor("antigravity", "Antigravity"),
        launcher,
    ).connect(
        AgentConnectionRequest(AgentClientInfo("kite", "test")),
        AgentClientEndpoint(
            eventSink = { _, event -> events += event },
            permissionHandler = { AgentPermissionOutcome.Cancelled },
        ),
    ) as AgentOperationResult.Success).value

    private class FakeAntigravityLauncher : AntigravityProcessLauncher {
        val sessionArguments = mutableListOf<List<String>>()
        val sessionChannels = mutableListOf<FakeStreamChannel>()
        private var nextSession = 1

        override suspend fun launch(arguments: List<String>): AgentProcessChannel {
            if (arguments == listOf("models")) return CompletedChannel(
                listOf(
                    "\u001B[32m* gemini-3.7-flash-high    Gemini 3.7 Flash (High)\u001B[0m",
                    "claude-sonnet-4-6 Claude Sonnet 4.6 (Thinking)",
                )
            )
            sessionArguments += arguments
            val conversation = arguments.valueAfter("--conversation") ?: "agy-session-${nextSession++}"
            return FakeStreamChannel(conversation).also(sessionChannels::add)
        }
    }

    private class CompletedChannel(lines: List<String>) : AgentProcessChannel {
        override val stdoutLines: Flow<String> = flowOf(*lines.toTypedArray())
        override val stderrLines: Flow<String> = emptyFlow()
        override val pid: Long = 1L
        override val isAlive: Boolean = false
        override suspend fun writeLine(line: String) = error("completed")
        override suspend fun awaitExit(): Int = 0
        override suspend fun stop(gracePeriodMs: Long): Int = 0
        override fun close() = Unit
    }

    private class FakeStreamChannel(private val conversationId: String) : AgentProcessChannel {
        private val output = MutableSharedFlow<String>(replay = 64, extraBufferCapacity = 64)
        private val exited = CompletableDeferred<Int>()
        val inputs = mutableListOf<JSONObject>()
        override val stdoutLines: Flow<String> = output
        override val stderrLines: Flow<String> = MutableSharedFlow()
        override val pid: Long = 2L
        override var isAlive: Boolean = true

        init {
            output.tryEmit(
                JSONObject()
                    .put("event", "init")
                    .put("conversation_id", conversationId)
                    .put("init", JSONObject().put("permission_mode", "request-review"))
                    .toString(),
            )
        }

        override suspend fun writeLine(line: String) {
            val input = JSONObject(line)
            inputs += input
            val turn = inputs.size
            output.emit(step("tool", turn * 10, "ACTIVE").put("tool_name", "view_file").put(
                "tool_info",
                JSONObject().put("name", "view_file").put("parameters", JSONObject().put("path", "README.md")),
            ).wrap())
            output.emit(step("tool", turn * 10, "DONE").put("tool_name", "view_file").put(
                "tool_info",
                JSONObject().put("name", "view_file").put("output", "ok"),
            ).wrap())
            output.emit(step("agent_response", turn * 10 + 1, "DONE").put("text_delta", "回答$turn").put(
                "usage",
                JSONObject().put("input_tokens", 10).put("output_tokens", 2).put("total_tokens", 12),
            ).wrap())
            output.emit(
                JSONObject().put("event", "result").put(
                    "result",
                    JSONObject()
                        .put("conversation_id", conversationId)
                        .put("status", "SUCCESS")
                        .put("response", "回答$turn")
                        .put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 2).put("total_tokens", 12)),
                ).toString(),
            )
        }

        override suspend fun awaitExit(): Int = exited.await()
        override suspend fun stop(gracePeriodMs: Long): Int {
            isAlive = false
            exited.complete(0)
            return 0
        }
        override fun close() {
            isAlive = false
            exited.complete(0)
        }

        private fun step(type: String, index: Int, state: String) = JSONObject()
            .put("conversation_id", conversationId)
            .put("step_index", index)
            .put("state", state)
            .put("step_type", type)

        private fun JSONObject.wrap() = JSONObject().put("event", "step_update").put("step_update", this).toString()
    }
}

private fun List<String>.valueAfter(flag: String): String? =
    indexOf(flag).takeIf { it >= 0 && it < lastIndex }?.let { get(it + 1) }
