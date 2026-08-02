package com.kite.app.agent.codex

import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AGENT_SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentStopReason
import com.kite.app.agent.process.AgentProcessChannel
import com.kite.app.agent.config.native.codex.codexReasoningControl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CodexAppServerAgentProviderTest {
    @Test
    fun `官方模型按模型目录提供推理强度且权限只有四项`() = runBlocking {
        val fixture = CodexAppServerFixture(modelProvider = "openai", selectedModel = "gpt-5.6-sol")
        val events = mutableListOf<Pair<String, AgentSessionEvent>>()
        val connection = connect(fixture, events)

        val created = connection.newSession(AgentNewSessionRequest("/workspace"))
        assertTrue(created is AgentOperationResult.Success)
        val snapshot = (created as AgentOperationResult.Success).value
        val model = snapshot.configuration.select(AgentConfigCategory.Model)
        val effort = snapshot.configuration.select(AgentConfigCategory.ThoughtLevel)
        val permission = snapshot.configuration.select(AgentConfigCategory.Permission)
        assertEquals(AGENT_SESSION_PERMISSION_CONFIG_ID, permission.id)

        assertEquals(listOf("gpt-5.6-sol", "gpt-5.6-terra"), model.choices.map { it.value })
        assertTrue(model.choices.all { it.modelSource == AgentModelSource.OfficialLogin })
        assertEquals(listOf("low", "high", "ultra"), effort.choices.map { it.value })
        assertEquals(
            listOf(AgentReasoningLevel.Low, AgentReasoningLevel.High, AgentReasoningLevel.Maximum),
            effort.choices.map { it.reasoning },
        )
        val normalizedEffort = requireNotNull(codexReasoningControl.normalize(effort)) as AgentConfigOption.Select
        assertEquals(listOf("low", "high", "ultra"), normalizedEffort.choices.map { it.value })
        assertEquals(
            listOf("请求批准", "替我审批", "完全访问权限", "自定义"),
            permission.choices.map { it.name },
        )
        assertEquals("codex.permission.custom", permission.currentValue)

        val terraPreview = connection.previewDraftModelConfiguration("openai", "gpt-5.6-terra")
        assertEquals(setOf(AgentConfigCategory.ThoughtLevel), terraPreview?.replaceCategories)
        val terraEffort = terraPreview?.options.orEmpty().select(AgentConfigCategory.ThoughtLevel)
        assertEquals(listOf("medium", "max"), terraEffort.choices.map { it.value })
        assertEquals("medium", terraEffort.currentValue)

        val autoReview = connection.setConfiguration(
            snapshot.id,
            permission.id,
            AgentConfigValue.Select("codex.permission.auto_review"),
        )
        assertTrue(autoReview is AgentOperationResult.Success)
        val autoParams = fixture.settingsUpdates.last()
        assertEquals("on-request", autoParams.getString("approvalPolicy"))
        assertEquals("auto_review", autoParams.getString("approvalsReviewer"))
        assertEquals("workspaceWrite", autoParams.getJSONObject("sandboxPolicy").getString("type"))

        val custom = connection.setConfiguration(
            snapshot.id,
            permission.id,
            AgentConfigValue.Select("codex.permission.custom"),
        )
        assertTrue(custom is AgentOperationResult.Success)
        val customParams = fixture.settingsUpdates.last()
        assertTrue(customParams.isNull("approvalPolicy"))
        assertTrue(customParams.isNull("approvalsReviewer"))
        assertTrue(customParams.isNull("sandboxPolicy"))

        val switched = connection.setConfiguration(
            snapshot.id,
            model.id,
            AgentConfigValue.Select("gpt-5.6-terra"),
        ) as AgentOperationResult.Success
        val switchedEffort = switched.value.select(AgentConfigCategory.ThoughtLevel)
        assertEquals(listOf("medium", "max"), switchedEffort.choices.map { it.value })
        assertEquals("medium", switchedEffort.currentValue)

        val prompted = withTimeout(5_000L) {
            connection.prompt(AgentPromptRequest(snapshot.id, listOf(AgentContent.Text("你好"))))
        }
        assertTrue(prompted is AgentOperationResult.Success)
        assertEquals(AgentStopReason.EndTurn, (prompted as AgentOperationResult.Success).value.stopReason)
        assertTrue(events.any { (_, event) ->
            event is AgentSessionEvent.MessageChunk &&
                (event.content as? AgentContent.Text)?.text == "收到：你好"
        })
        connection.disconnect()
    }

    @Test
    fun `ChatGPT登录连接会发布官方模型目录而自定义连接不会`() = runBlocking {
        var officialCatalog: CodexOfficialModelCatalog? = null
        val official = CodexAppServerFixture(
            modelProvider = "openai",
            selectedModel = "gpt-5.6-sol",
            accountType = "chatgpt",
        )
        connect(official, mutableListOf(), officialCatalogSink = { officialCatalog = it }).disconnect()

        assertNotNull(officialCatalog)
        assertEquals(
            listOf("gpt-5.6-sol", "gpt-5.6-terra"),
            officialCatalog?.models?.map { it.id },
        )
        assertTrue(officialCatalog?.sourceVersion?.startsWith("sha256:") == true)

        var customCatalog: CodexOfficialModelCatalog? = null
        val custom = CodexAppServerFixture(
            modelProvider = "zhipu-coding-plan",
            selectedModel = "glm-5",
            accountType = null,
        )
        connect(custom, mutableListOf(), officialCatalogSink = { customCatalog = it }).disconnect()
        assertEquals(null, customCatalog)
    }

    @Test
    fun `用户自定义供应商不会混入官方模型目录`() = runBlocking {
        val fixture = CodexAppServerFixture(modelProvider = "zhipu-coding-plan", selectedModel = "glm-5")
        val connection = connect(fixture, mutableListOf())

        val created = connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success
        val model = created.value.configuration.select(AgentConfigCategory.Model)

        assertEquals(listOf("glm-5"), model.choices.map { it.value })
        assertEquals(listOf(AgentModelSource.UserConfigured), model.choices.map { it.modelSource })
        assertEquals(listOf("zhipu-coding-plan"), model.choices.map { it.groupId })
        val preview = connection.previewDraftModelConfiguration("zhipu-coding-plan", "glm-5")
        assertEquals(setOf(AgentConfigCategory.ThoughtLevel), preview?.replaceCategories)
        assertTrue(preview?.options.isNullOrEmpty())
        connection.disconnect()
    }

    @Test
    fun `恢复会话时按真实沙箱和审批参数匹配权限档位`() = runBlocking {
        val fixture = CodexAppServerFixture(
            modelProvider = "openai",
            selectedModel = "gpt-5.6-sol",
            approvalPolicy = "on-request",
            approvalsReviewer = "user",
            sandboxType = "workspaceWrite",
        )
        val connection = connect(fixture, mutableListOf())

        val created = connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success
        val permission = created.value.configuration.select(AgentConfigCategory.Permission)

        assertEquals("codex.permission.ask", permission.currentValue)
        connection.disconnect()
    }

    @Test
    fun `额外权限请求按本轮或会话范围返回真实授权结构`() = runBlocking {
        val fixture = CodexAppServerFixture(modelProvider = "openai", selectedModel = "gpt-5.6-sol")
        val connection = connect(
            fixture = fixture,
            events = mutableListOf(),
            permissionHandler = { AgentPermissionOutcome.Selected("allowSession") },
        )
        connection.newSession(AgentNewSessionRequest("/workspace"))

        val response = fixture.requestAdditionalPermissions(
            JSONObject().put("network", JSONObject().put("enabled", true)),
        )

        assertEquals("session", response.getString("scope"))
        assertTrue(response.getJSONObject("permissions").getJSONObject("network").getBoolean("enabled"))
        connection.disconnect()
    }

    private suspend fun connect(
        fixture: CodexAppServerFixture,
        events: MutableList<Pair<String, AgentSessionEvent>>,
        permissionHandler: suspend () -> AgentPermissionOutcome = { AgentPermissionOutcome.Cancelled },
        officialCatalogSink: (CodexOfficialModelCatalog) -> Unit = {},
    ) = withTimeout(5_000L) {
        val provider = CodexAppServerAgentProvider(
            descriptor = CodexAppServerProviderDescriptor("codex", "Codex"),
            launcher = CodexAppServerProcessLauncher { fixture.start() },
            initializeTimeoutMs = 2_000L,
            officialModelCatalogSink = CodexOfficialModelCatalogSink(officialCatalogSink),
        )
        val result = provider.connect(
            AgentConnectionRequest(AgentClientInfo("kite", "test", "Kite Test")),
            AgentClientEndpoint(
                eventSink = { sessionId, event -> events += sessionId to event },
                permissionHandler = { permissionHandler() },
            ),
        )
        assertTrue(result.toString(), result is AgentOperationResult.Success)
        (result as AgentOperationResult.Success).value
    }

    private fun List<com.kite.app.agent.contract.AgentConfigOption>.select(
        category: AgentConfigCategory,
    ): AgentConfigOption.Select = single { it.category == category } as AgentConfigOption.Select

    private class CodexAppServerFixture(
        private val modelProvider: String,
        private val selectedModel: String,
        private val approvalPolicy: String = "untrusted",
        private val approvalsReviewer: String = "user",
        private val sandboxType: String = "readOnly",
        private val accountType: String? = if (modelProvider == "openai") "chatgpt" else null,
    ) {
        val settingsUpdates = mutableListOf<JSONObject>()
        private val clientToServer = Channel<String>(Channel.UNLIMITED)
        private val serverToClient = Channel<String>(Channel.UNLIMITED)
        private val exit = CompletableDeferred<Int>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val serverResponses = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

        fun start(): AgentProcessChannel {
            scope.launch {
                for (line in clientToServer) handle(JSONObject(line))
            }
            return object : AgentProcessChannel {
                override val stdoutLines: Flow<String> = serverToClient.receiveAsFlow()
                override val stderrLines: Flow<String> = emptyFlow()
                override val pid: Long? = 42L
                override val isAlive: Boolean get() = !exit.isCompleted
                override suspend fun writeLine(line: String) = clientToServer.send(line)
                override suspend fun awaitExit(): Int = exit.await()
                override suspend fun stop(gracePeriodMs: Long): Int {
                    clientToServer.close()
                    serverToClient.close()
                    exit.complete(0)
                    return 0
                }
                override fun close() {
                    clientToServer.close()
                    serverToClient.close()
                    exit.complete(0)
                }
            }
        }

        private suspend fun handle(message: JSONObject) {
            if (!message.has("method")) {
                val responseId = message.opt("id")?.toString() ?: return
                serverResponses.remove(responseId)?.complete(message.optJSONObject("result") ?: JSONObject())
                return
            }
            val id = message.optLong("id", Long.MIN_VALUE)
            if (id == Long.MIN_VALUE) return
            val params = message.optJSONObject("params") ?: JSONObject()
            when (message.getString("method")) {
                "initialize" -> respond(id, JSONObject())
                "account/read" -> respond(
                    id,
                    JSONObject().put(
                        "account",
                        accountType?.let { JSONObject().put("type", it) } ?: JSONObject.NULL,
                    ),
                )
                "model/list" -> respond(id, modelList())
                "thread/start" -> respond(id, threadResponse())
                "thread/settings/update" -> {
                    settingsUpdates += JSONObject(params.toString())
                    respond(id, JSONObject())
                }
                "turn/start" -> {
                    respond(
                        id,
                        JSONObject().put(
                            "turn",
                            JSONObject().put("id", "turn-1").put("status", "inProgress").put("items", JSONArray()),
                        ),
                    )
                    val text = params.getJSONArray("input").getJSONObject(0).getString("text")
                    notify(
                        "item/agentMessage/delta",
                        JSONObject()
                            .put("threadId", "thread-1")
                            .put("turnId", "turn-1")
                            .put("itemId", "message-1")
                            .put("delta", "收到：$text"),
                    )
                    notify(
                        "turn/completed",
                        JSONObject()
                            .put("threadId", "thread-1")
                            .put(
                                "turn",
                                JSONObject().put("id", "turn-1").put("status", "completed").put("items", JSONArray()),
                            ),
                    )
                }
                "thread/unsubscribe" -> respond(id, JSONObject())
                else -> respond(id, JSONObject())
            }
        }

        suspend fun requestAdditionalPermissions(permissions: JSONObject): JSONObject {
            val id = "permission-1"
            val response = CompletableDeferred<JSONObject>()
            serverResponses[id] = response
            serverToClient.send(
                JSONObject()
                    .put("id", id)
                    .put("method", "item/permissions/requestApproval")
                    .put(
                        "params",
                        JSONObject()
                            .put("threadId", "thread-1")
                            .put("turnId", "turn-1")
                            .put("itemId", "permission-item-1")
                            .put("cwd", "/workspace")
                            .put("startedAtMs", 1L)
                            .put("permissions", permissions),
                    )
                    .toString(),
            )
            return withTimeout(2_000L) { response.await() }
        }

        private fun modelList(): JSONObject = JSONObject()
            .put(
                "data",
                JSONArray()
                    .put(model("gpt-5.6-sol", "GPT-5.6-Sol", "low", listOf("low", "high", "ultra"), true))
                    .put(model("gpt-5.6-terra", "GPT-5.6-Terra", "medium", listOf("medium", "max"), false)),
            )
            .put("nextCursor", JSONObject.NULL)

        private fun model(
            id: String,
            displayName: String,
            defaultEffort: String,
            efforts: List<String>,
            isDefault: Boolean,
        ): JSONObject = JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("description", "$displayName description")
            .put("defaultReasoningEffort", defaultEffort)
            .put("hidden", false)
            .put("isDefault", isDefault)
            .put(
                "supportedReasoningEfforts",
                JSONArray(efforts.map { effort ->
                    JSONObject().put("reasoningEffort", effort).put("description", "$effort reasoning")
                }),
            )

        private fun threadResponse(): JSONObject = JSONObject()
            .put("thread", JSONObject().put("id", "thread-1"))
            .put("cwd", "/workspace")
            .put("modelProvider", modelProvider)
            .put("model", selectedModel)
            .put("approvalPolicy", approvalPolicy)
            .put("approvalsReviewer", approvalsReviewer)
            .put("sandbox", JSONObject().put("type", sandboxType))
            .put("reasoningEffort", if (selectedModel == "gpt-5.6-sol") "low" else JSONObject.NULL)

        private suspend fun respond(id: Long, result: JSONObject) {
            serverToClient.send(JSONObject().put("id", id).put("result", result).toString())
        }

        private suspend fun notify(method: String, params: JSONObject) {
            serverToClient.send(JSONObject().put("method", method).put("params", params).toString())
        }
    }
}
