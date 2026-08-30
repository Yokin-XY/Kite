package com.kite.app.agent.zcode

import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.process.AgentProcessChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZCodeAppServerAgentProviderTest {
    @Test
    fun `原生快照投影模型推理权限并完成消息流`() = runBlocking {
        val channel = FakeZCodeChannel()
        val events = mutableListOf<AgentSessionEvent>()
        val connection = connect(channel) { event -> events += event }

        val created = connection.newSession(AgentNewSessionRequest("/workspace"))
            as AgentOperationResult.Success
        assertEquals("zcode-session", created.value.id)
        val model = created.value.configuration.select(AgentConfigCategory.Model)
        val reasoning = created.value.configuration.select(AgentConfigCategory.ThoughtLevel)
        val permission = created.value.configuration.select(AgentConfigCategory.Permission)
        assertEquals("zai/glm-5.3", model.currentValue)
        assertEquals(listOf("zai/glm-5.3", "zai/glm-5.3-flash"), model.choices.map { it.value })
        assertEquals(listOf("Z.AI", "Z.AI"), model.choices.map { it.groupName })
        assertEquals(listOf(AgentModelSource.OfficialLogin, AgentModelSource.OfficialLogin), model.choices.map { it.modelSource })
        assertEquals("high", reasoning.currentValue)
        assertEquals("build", permission.currentValue)

        val result = connection.prompt(
            AgentPromptRequest("zcode-session", listOf(AgentContent.Text("你好")), messageId = "input-1"),
        )
        assertTrue(result is AgentOperationResult.Success)
        assertTrue(events.filterIsInstance<AgentSessionEvent.MessageChunk>().any { event ->
            event.role == AgentMessageRole.Thought && (event.content as? AgentContent.Text)?.text == "想一想"
        })
        assertTrue(events.filterIsInstance<AgentSessionEvent.MessageChunk>().any { event ->
            event.role == AgentMessageRole.Assistant && (event.content as? AgentContent.Text)?.text == "你好"
        })
        connection.disconnect()
    }

    @Test
    fun `创建会话前通过官方workspace协议注入Kite供应商和runtimeModel`() = runBlocking {
        val channel = FakeZCodeChannel()
        val catalog = ZCodeRuntimeModelCatalog(
            revision = "sha256:test",
            generatedAt = 123L,
            providers = listOf(
                ZCodeRuntimeModelProvider(
                    providerId = "zhipu-coding-plan",
                    label = "智谱 Coding Plan",
                    kind = "openai-compatible",
                    apiFormat = "openai-chat-completions",
                    baseUrl = "https://open.bigmodel.cn/api/coding/paas/v4",
                    apiKey = "test-secret",
                    models = listOf(
                        com.kite.app.agent.config.AgentProviderModelSummary("glm-5.3", "GLM-5.3"),
                        com.kite.app.agent.config.AgentProviderModelSummary("glm-5.3-flash", "GLM-5.3 Flash"),
                    ),
                )
            ),
            selectedProviderId = "zhipu-coding-plan",
            selectedModelId = "glm-5.3-flash",
        )
        val connection = connect(channel, runtimeModelCatalogSource = { catalog }) { }

        val created = connection.newSession(AgentNewSessionRequest("/workspace"))
        assertTrue(created is AgentOperationResult.Success)

        val requests = channel.requests()
        val upsert = requests.single { it.optString("method") == "workspace/upsertModelProvider" }
        val provider = upsert.getJSONObject("params").getJSONObject("provider")
        assertEquals("zhipu-coding-plan", provider.getString("providerId"))
        assertEquals(2, provider.getJSONArray("models").length())
        assertEquals("inline", provider.getJSONObject("apiKey").getString("source"))
        val create = requests.single { it.optString("method") == "session/create" }
        val runtimeModel = create.getJSONObject("params").getJSONObject("runtimeModel")
        assertEquals("zhipu-coding-plan", runtimeModel.getJSONObject("model").getString("providerId"))
        assertEquals("glm-5.3-flash", runtimeModel.getJSONObject("model").getString("modelId"))
        connection.disconnect()
    }

    @Test
    fun `加载会话读取ZCode权威历史且设置使用原生方法`() = runBlocking {
        val channel = FakeZCodeChannel()
        val events = mutableListOf<AgentSessionEvent>()
        val connection = connect(channel) { event -> events += event }

        val loaded = connection.loadSession(AgentExistingSessionRequest("zcode-session", "/workspace"))
            as AgentOperationResult.Success
        assertEquals("zcode-session", loaded.value.id)
        assertTrue(events.filterIsInstance<AgentSessionEvent.MessageChunk>().any { event ->
            event.role == AgentMessageRole.User && (event.content as? AgentContent.Text)?.text == "旧问题"
        })
        assertTrue(events.filterIsInstance<AgentSessionEvent.MessageChunk>().any { event ->
            event.role == AgentMessageRole.Assistant && (event.content as? AgentContent.Text)?.text == "旧回答"
        })

        val changed = connection.setConfiguration(
            "zcode-session",
            "zcode.app_server.model",
            AgentConfigValue.Select("zai/glm-5.3-flash"),
        )
        assertTrue(changed is AgentOperationResult.Success)
        assertTrue(channel.requests().any { request -> request.optString("method") == "session/setModel" })
        connection.disconnect()
    }

    private suspend fun connect(
        channel: FakeZCodeChannel,
        runtimeModelCatalogSource: suspend () -> ZCodeRuntimeModelCatalog? = { null },
        onEvent: (AgentSessionEvent) -> Unit,
    ) = (ZCodeAppServerAgentProvider(
        ZCodeAppServerProviderDescriptor("zcode", "ZCode"),
        ZCodeAppServerProcessLauncher { channel },
        runtimeModelCatalogSource = runtimeModelCatalogSource,
    ).connect(
        AgentConnectionRequest(AgentClientInfo("kite", "test")),
        AgentClientEndpoint(
            eventSink = { _, event -> onEvent(event) },
            permissionHandler = { AgentPermissionOutcome.Selected("allow") },
        ),
    ) as AgentOperationResult.Success).value

    private class FakeZCodeChannel : AgentProcessChannel {
        private val output = MutableSharedFlow<String>(replay = 64, extraBufferCapacity = 64)
        private val recorded = mutableListOf<JSONObject>()
        override val stdoutLines: Flow<String> = output
        override val stderrLines: Flow<String> = MutableSharedFlow()
        override val pid: Long = 43L
        override var isAlive: Boolean = true

        fun requests(): List<JSONObject> = synchronized(recorded) { recorded.map { JSONObject(it.toString()) } }

        override suspend fun writeLine(line: String) {
            val request = JSONObject(line)
            synchronized(recorded) { recorded += JSONObject(request.toString()) }
            if (!request.has("method")) return
            val id = request.getLong("id")
            val method = request.getString("method")
            val result = when (method) {
                "session/list" -> JSONObject().put("sessions", JSONArray())
                "workspace/upsertModelProvider" -> JSONObject().put("status", "applied")
                "session/create", "session/resume" -> snapshot()
                "session/messages" -> history()
                "session/subscribe", "session/send", "session/stop", "session/close",
                "session/setModel", "session/setThoughtLevel", "session/setMode" -> JSONObject()
                else -> JSONObject()
            }
            output.emit(JSONObject().put("id", id).put("result", result).toString())
            if (method == "session/send") emitTurn()
        }

        private suspend fun emitTurn() {
            output.emit(event("model.streaming", JSONObject().put("kind", "reasoning_delta").put("delta", "想一想")))
            output.emit(event("model.streaming", JSONObject().put("kind", "text_delta").put("delta", "你好")))
            output.emit(event("part.delta", JSONObject().put("field", "text").put("delta", "你好")))
            output.emit(
                event(
                    "turn.completed",
                    JSONObject().put("response", "你好").put(
                        "usage",
                        JSONObject().put("inputTokens", 2).put("outputTokens", 3),
                    ),
                )
            )
        }

        private fun event(type: String, payload: JSONObject) = JSONObject()
            .put("method", "session/event")
            .put(
                "params",
                JSONObject()
                    .put("sessionId", "zcode-session")
                    .put("turnId", "turn-1")
                    .put("type", type)
                    .put("payload", payload),
            ).toString()

        private fun snapshot() = JSONObject()
            .put(
                "session",
                JSONObject()
                    .put("sessionId", "zcode-session")
                    .put(
                        "workspace",
                        JSONObject().put("workspaceKey", "/workspace").put("workspacePath", "/workspace"),
                    ),
            )
            .put(
                "settings",
                JSONObject()
                    .put(
                        "model",
                        JSONObject()
                            .put("current", model("glm-5.3", "GLM-5.3"))
                            .put(
                                "available",
                                JSONArray()
                                    .put(model("glm-5.3", "GLM-5.3"))
                                    .put(model("glm-5.3-flash", "GLM-5.3 Flash")),
                            ),
                    )
                    .put(
                        "thoughtLevel",
                        JSONObject()
                            .put("current", "high")
                            .put("available", JSONArray(listOf("low", "high"))),
                    )
                    .put("mode", JSONObject().put("current", "build")),
            )

        private fun model(id: String, name: String) = JSONObject()
            .put("ref", JSONObject().put("providerId", "zai").put("modelId", id))
            .put("label", name)
            .put("providerLabel", "Z.AI")
            .put("providerSource", "builtin")

        private fun history() = JSONObject().put(
            "messages",
            JSONArray()
                .put(message("user", "旧问题"))
                .put(message("assistant", "旧回答")),
        )

        private fun message(role: String, text: String) = JSONObject()
            .put(
                "info",
                JSONObject()
                    .put("id", "$role-message")
                    .put("role", role)
                    .put("semantics", JSONObject().put("uiVisibility", "visible")),
            )
            .put("parts", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

        override suspend fun awaitExit(): Int = 0
        override suspend fun stop(gracePeriodMs: Long): Int { isAlive = false; return 0 }
        override fun close() { isAlive = false }
    }
}

private fun List<AgentConfigOption>.select(category: AgentConfigCategory): AgentConfigOption.Select =
    filterIsInstance<AgentConfigOption.Select>().single { it.category == category }
