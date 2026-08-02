package com.kite.app.agent.runtime

import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionCapabilities
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.contract.AgentSessionSnapshot
import com.kite.app.agent.contract.AgentStopReason
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import com.kite.app.agent.store.AgentConversationKey
import com.kite.app.agent.store.AgentConversationHistoryStatus
import com.kite.app.agent.store.AgentConversationStore
import com.kite.app.agent.config.AgentSessionModelSelection
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.sdk.configuration.AgentProviderPreparationResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentRuntimeRegistryTest {
    @Before
    fun setUp() {
        AgentConversationStore.resetForTest()
    }

    @After
    fun tearDown() = runBlocking {
        AgentRuntimeRegistry.resetForTest()
        AgentConversationStore.resetForTest()
    }

    @Test
    fun `启动 权限 消息和停止都保持同一实例代次`() = runTest {
        val provider = FakeProvider()
        val phases = mutableListOf<AgentSessionPhase>()
        val started = AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest("instance-1", 42L, "fake", "/workspace"),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, phase, _ -> phases += phase }
        )
        val session = (started as AgentOperationResult.Success).value

        assertEquals(null, session.sessionId)
        assertTrue(session.isDraft)
        assertEquals(0, provider.connection.newSessionCalls)

        val prompt = async {
            AgentRuntimeRegistry.prompt(
                instanceId = "instance-1",
                generation = 42L,
                content = listOf(AgentContent.Text("你好"))
            )
        }
        val key = AgentConversationKey("fake", "session-1")
        while (AgentConversationStore.snapshot(key)?.pendingPermission == null) {
            testScheduler.runCurrent()
        }
        assertEquals(AgentSessionPhase.WaitingPermission, AgentConversationStore.snapshot(key)?.phase)
        assertTrue(
            AgentRuntimeRegistry.resolvePermission(
                "instance-1",
                42L,
                AgentPermissionOutcome.Selected("allow_once")
            )
        )
        assertTrue(prompt.await() is AgentOperationResult.Success)
        AgentConversationStore.flushForTest()
        val conversation = AgentConversationStore.snapshot(key)
        assertNotNull(conversation)
        assertTrue(conversation!!.timeline.isNotEmpty())
        assertTrue(phases.contains(AgentSessionPhase.WaitingPermission))

        assertFalse(AgentRuntimeRegistry.stop("instance-1", 41L))
        assertTrue(AgentRuntimeRegistry.stop("instance-1", 42L))
        assertTrue(provider.connection.disconnected.get())
        assertEquals(AgentSessionPhase.Closed, AgentConversationStore.snapshot(key)?.phase)
    }

    @Test
    fun `新建草稿 列出和加载会话复用同一 provider connection`() = runTest {
        val provider = FakeProvider()
        val started = AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest("instance-sessions", 88L, "fake", "/workspace"),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        val created = AgentRuntimeRegistry.prepareNewSession("instance-sessions", 88L) as AgentOperationResult.Success
        val listed = AgentRuntimeRegistry.listSessions("instance-sessions", 88L) as AgentOperationResult.Success
        val loaded = AgentRuntimeRegistry.loadSession(
            "instance-sessions",
            88L,
            "historical-1",
            "/workspace/old"
        ) as AgentOperationResult.Success

        assertEquals(null, started.value.sessionId)
        assertEquals(null, created.value.sessionId)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(listOf("historical-1"), listed.value.sessions.map { it.id })
        assertEquals(null, provider.connection.lastListRequest?.cwd)
        assertEquals("historical-1", loaded.value.sessionId)
        assertEquals("/workspace/old", loaded.value.cwd)
        assertEquals("historical-1", AgentRuntimeRegistry.session("instance-sessions")?.sessionId)
        assertEquals("/workspace", AgentRuntimeRegistry.defaultCwd("instance-sessions", 88L))

        val defaultDraft = AgentRuntimeRegistry.prepareNewSession(
            "instance-sessions",
            88L,
            AgentRuntimeRegistry.defaultCwd("instance-sessions", 88L)
        ) as AgentOperationResult.Success
        assertEquals(null, defaultDraft.value.sessionId)
        assertEquals("/workspace", defaultDraft.value.cwd)
        assertEquals(0, provider.connection.newSessionCalls)
    }

    @Test
    fun `列出会话会聚合全部分页后再返回完整目录`() = runTest {
        val provider = FakeProvider()
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest("instance-pages", 1L, "fake", "/workspace"),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> },
        ) as AgentOperationResult.Success
        provider.connection.sessionListHandler = { request ->
            when (request.cursor) {
                null -> AgentOperationResult.Success(AgentSessionPage(
                    sessions = listOf(AgentSessionSummary("session-a", "/workspace/a")),
                    nextCursor = "page-2",
                ))
                "page-2" -> AgentOperationResult.Success(AgentSessionPage(
                    sessions = listOf(AgentSessionSummary("session-b", "/workspace/b")),
                ))
                else -> AgentOperationResult.Failure("未知分页")
            }
        }

        val listed = AgentRuntimeRegistry.listSessions("instance-pages", 1L) as AgentOperationResult.Success

        assertEquals(listOf("session-a", "session-b"), listed.value.sessions.map { it.id })
        assertEquals(listOf(null, "page-2"), provider.connection.listRequests.map { it.cursor })
        assertEquals(null, listed.value.nextCursor)
    }

    @Test
    fun `列出会话遇到重复分页游标时拒绝返回不完整目录`() = runTest {
        val provider = FakeProvider()
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest("instance-loop", 1L, "fake", "/workspace"),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> },
        ) as AgentOperationResult.Success
        provider.connection.sessionListHandler = {
            AgentOperationResult.Success(AgentSessionPage(
                sessions = listOf(AgentSessionSummary("session-a", "/workspace")),
                nextCursor = "same-cursor",
            ))
        }

        val listed = AgentRuntimeRegistry.listSessions("instance-loop", 1L)

        assertTrue(listed is AgentOperationResult.Failure)
        assertEquals(2, provider.connection.listRequests.size)
    }

    @Test
    fun `首发失败后保留已创建会话且重试不会重复创建或留下重复用户消息`() = runTest {
        val provider = FakeProvider(promptFailures = 1, requestPermission = false)
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest("instance-first-send", 1L, "fake", "/workspace"),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        repeat(3) {
            AgentRuntimeRegistry.prepareNewSession("instance-first-send", 1L) as AgentOperationResult.Success
        }
        assertEquals(0, provider.connection.newSessionCalls)

        val first = AgentRuntimeRegistry.prompt(
            "instance-first-send",
            1L,
            listOf(AgentContent.Text("只发送一次"))
        )
        val key = AgentConversationKey("fake", "session-1")
        assertTrue(first is AgentOperationResult.Failure)
        assertEquals("session-1", AgentRuntimeRegistry.session("instance-first-send")?.sessionId)
        assertEquals(1, provider.connection.newSessionCalls)
        assertEquals(1, provider.connection.promptCalls)
        assertTrue(AgentConversationStore.snapshot(key)?.timeline.orEmpty().isEmpty())

        val retried = AgentRuntimeRegistry.prompt(
            "instance-first-send",
            1L,
            listOf(AgentContent.Text("只发送一次"))
        )
        AgentConversationStore.flushForTest()

        assertTrue(retried is AgentOperationResult.Success)
        assertEquals(1, provider.connection.newSessionCalls)
        assertEquals(2, provider.connection.promptCalls)
        val snapshot = AgentConversationStore.snapshot(key)!!
        val userMessages = snapshot.timeline
            .filterIsInstance<com.kite.app.agent.store.AgentConversationItem.Message>()
            .filter { it.role == AgentMessageRole.User }
        assertEquals(snapshot.toString(), 1, userMessages.size)
        assertEquals(null, AgentConversationStore.snapshot(key)?.title)
    }

    @Test
    fun `首发创建失败保持草稿且重试只绑定成功创建的会话`() = runTest {
        val provider = FakeProvider(newSessionFailures = 1, requestPermission = false)
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest("instance-create-failure", 1L, "fake", "/workspace"),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        val failed = AgentRuntimeRegistry.prompt(
            "instance-create-failure",
            1L,
            listOf(AgentContent.Text("重试内容"))
        )
        assertTrue(failed is AgentOperationResult.Failure)
        assertTrue(AgentRuntimeRegistry.session("instance-create-failure")?.isDraft == true)
        assertEquals(1, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.promptCalls)

        val retried = AgentRuntimeRegistry.prompt(
            "instance-create-failure",
            1L,
            listOf(AgentContent.Text("重试内容"))
        )

        assertTrue(retried is AgentOperationResult.Success)
        assertEquals("session-1", AgentRuntimeRegistry.session("instance-create-failure")?.sessionId)
        assertEquals(2, provider.connection.newSessionCalls)
        assertEquals(1, provider.connection.promptCalls)
    }

    @Test
    fun `启动时没有内存投影则用 load 恢复历史且不会静默新建`() = runTest {
        val provider = FakeProvider(resumeSupported = true)

        val started = AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-restore",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        assertEquals("historical-1", started.value.sessionId)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.resumeSessionCalls)
        assertEquals(1, provider.connection.loadSessionCalls)
        assertEquals(2, AgentConversationStore.snapshot(AgentConversationKey("fake", "historical-1"))!!.timeline.size)
    }

    @Test
    fun `已有内存投影时用 resume 避免重复回放`() = runTest {
        val key = AgentConversationKey("fake", "historical-1")
        AgentConversationStore.bind("instance-restore", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(AgentMessageRole.User, AgentContent.Text("已显示"), "existing")
        )
        val provider = FakeProvider(resumeSupported = true)

        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-restore",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        assertEquals(1, provider.connection.resumeSessionCalls)
        assertEquals(0, provider.connection.loadSessionCalls)
        assertEquals(1, AgentConversationStore.snapshot(key)!!.timeline.size)
    }

    @Test
    fun `不支持 resume 时用 load 恢复已有会话`() = runTest {
        val provider = FakeProvider(resumeSupported = false, loadSupported = true)

        val started = AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-load",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        assertEquals("historical-1", started.value.sessionId)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.resumeSessionCalls)
        assertEquals(1, provider.connection.loadSessionCalls)
    }

    @Test
    fun `只有 resume 能力时明确标记无法回放历史`() = runTest {
        val provider = FakeProvider(resumeSupported = true, loadSupported = false)

        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-resume-only",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        assertEquals(1, provider.connection.resumeSessionCalls)
        assertEquals(0, provider.connection.loadSessionCalls)
        assertEquals(
            AgentConversationHistoryStatus.Unavailable,
            AgentConversationStore.snapshot(AgentConversationKey("fake", "historical-1"))!!.history.status
        )
    }

    @Test
    fun `已有会话恢复失败时不创建替代会话`() = runTest {
        val provider = FakeProvider(resumeSupported = true, restoreFails = true)

        val started = AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-failed-restore",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "missing"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        )

        assertTrue(started is AgentOperationResult.Failure)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.resumeSessionCalls)
        assertEquals(1, provider.connection.loadSessionCalls)
        assertTrue(provider.connection.disconnected.get())
    }

    @Test
    fun `恢复已有会话时直接采用 Agent 返回的模型状态`() = runTest {
        val modelOption = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/default",
            choices = listOf(
                AgentConfigChoice("opencode/default", "Default"),
                AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2")
            )
        )
        val provider = FakeProvider(initialConfiguration = listOf(modelOption))

        val started = AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-model-restore",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        assertEquals(
            "opencode/default",
            (started.value.snapshot!!.configuration.single() as AgentConfigOption.Select).currentValue
        )
        assertTrue(provider.connection.selectedModelValues.isEmpty())
    }

    @Test
    fun `草稿选择首次发送前不触碰 Agent 创建后先应用模型再发送`() = runTest {
        val modelOption = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/default",
            choices = listOf(
                AgentConfigChoice("opencode/default", "Default"),
                AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2")
            )
        )
        val provider = FakeProvider(initialConfiguration = listOf(modelOption), requestPermission = false)
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                "instance-draft-model",
                1L,
                "fake",
                "/workspace",
                resolveDraftModelSelection = { target, _ ->
                    AgentSessionModelSelection("model", "${target.providerId}/${target.modelId}")
                }
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        val selected = AgentRuntimeRegistry.selectDraftModel(
            "instance-draft-model",
            1L,
            AgentDraftModelSelection("zhipu", "glm-5.2", usesAgentDefault = false)
        )

        assertTrue(selected is AgentOperationResult.Success)
        assertEquals(0, provider.connection.newSessionCalls)
        assertTrue(provider.connection.selectedModelValues.isEmpty())
        assertEquals(0, provider.connection.promptCalls)

        val prompted = AgentRuntimeRegistry.prompt(
            "instance-draft-model",
            1L,
            listOf(AgentContent.Text("Ping"))
        )

        assertTrue(prompted is AgentOperationResult.Success)
        assertEquals(listOf("new", "model", "prompt"), provider.connection.callOrder)
        assertEquals(listOf("zhipu/glm-5.2"), provider.connection.selectedModelValues)
    }

    @Test
    fun `草稿模型应用失败时保留真实会话但不发送消息`() = runTest {
        val modelOption = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/default",
            choices = listOf(AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2"))
        )
        val provider = FakeProvider(
            initialConfiguration = listOf(modelOption),
            configurationFailures = 1,
            requestPermission = false
        )
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                "instance-draft-model-failure",
                1L,
                "fake",
                "/workspace",
                resolveDraftModelSelection = { _, _ -> AgentSessionModelSelection("model", "zhipu/glm-5.2") }
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success
        AgentRuntimeRegistry.selectDraftModel(
            "instance-draft-model-failure",
            1L,
            AgentDraftModelSelection("zhipu", "glm-5.2", usesAgentDefault = false)
        )

        val prompted = AgentRuntimeRegistry.prompt(
            "instance-draft-model-failure",
            1L,
            listOf(AgentContent.Text("Ping"))
        )

        assertTrue(prompted is AgentOperationResult.Failure)
        assertEquals(1, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.promptCalls)
        assertEquals("session-1", AgentRuntimeRegistry.session("instance-draft-model-failure")?.sessionId)
    }

    @Test
    fun `当前会话选择模型只更新 Agent 返回的即时状态`() = runTest {
        val modelOption = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/default",
            choices = listOf(
                AgentConfigChoice("opencode/default", "Default"),
                AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2")
            )
        )
        val provider = FakeProvider(initialConfiguration = listOf(modelOption))
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-model-record",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        val configured = AgentRuntimeRegistry.setConfiguration(
            "instance-model-record",
            1L,
            "model",
            AgentConfigValue.Select("zhipu/glm-5.2")
        ) as AgentOperationResult.Success

        assertEquals(
            "zhipu/glm-5.2",
            (configured.value.single() as AgentConfigOption.Select).currentValue
        )
        assertEquals(listOf("zhipu/glm-5.2"), provider.connection.selectedModelValues)
    }

    @Test
    fun `真实删除能力只允许删除非当前会话`() = runTest {
        val provider = FakeProvider(deleteSupported = true)
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                "instance-delete",
                1L,
                "fake",
                "/workspace",
                preferredSessionId = "session-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        val current = AgentRuntimeRegistry.deleteSession("instance-delete", 1L, "session-1")
        val historical = AgentRuntimeRegistry.deleteSession("instance-delete", 1L, "historical-1")

        assertTrue(current is AgentOperationResult.Failure)
        assertTrue(historical is AgentOperationResult.Success)
        assertEquals(listOf("historical-1"), provider.connection.deletedSessions)
    }

    @Test
    fun `重命名通过 registry 转发给 Agent 真实能力`() = runTest {
        val provider = FakeProvider(renameSupported = true)
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                "instance-rename",
                1L,
                "fake",
                "/workspace",
                preferredSessionId = "session-1",
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> },
        ) as AgentOperationResult.Success

        val request = AgentSessionRenameRequest("historical-1", "  新标题  ")
        val renamed = AgentRuntimeRegistry.renameSession("instance-rename", 1L, request)

        assertTrue(renamed is AgentOperationResult.Success)
        assertEquals(
            listOf(AgentSessionRenameRequest("historical-1", "新标题")),
            provider.connection.renamedSessions,
        )
    }

    @Test
    fun `配置变更和会话分支经过 registry 更新当前投影`() = runTest {
        val provider = FakeProvider(initialModes = listOf(AgentMode("build", "构建"), AgentMode("plan", "计划")))
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                "instance-config",
                9L,
                "fake",
                "/workspace",
                preferredSessionId = "session-1",
                normalizeConfiguration = { options ->
                    options.map { option ->
                        if (option is AgentConfigOption.Select) {
                            option.copy(choices = option.choices.map {
                                it.copy(groupId = "normalized", groupName = "已归一化")
                            })
                        } else {
                            option
                        }
                    }
                }
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        val configured = AgentRuntimeRegistry.setConfiguration(
            "instance-config",
            9L,
            "model",
            AgentConfigValue.Select("gpt-test")
        ) as AgentOperationResult.Success
        val modeChanged = AgentRuntimeRegistry.setMode(
            "instance-config",
            9L,
            "plan"
        ) as AgentOperationResult.Success
        val forked = AgentRuntimeRegistry.forkSession("instance-config", 9L) as AgentOperationResult.Success

        assertEquals("gpt-test", (configured.value.single() as AgentConfigOption.Select).currentValue)
        assertEquals("normalized", (configured.value.single() as AgentConfigOption.Select).choices.single().groupId)
        assertEquals(
            "normalized",
            (AgentConversationStore.snapshot(AgentConversationKey("fake", "session-1"))
                ?.configuration?.single() as AgentConfigOption.Select).choices.single().groupId
        )
        assertEquals("session-fork", forked.value.sessionId)
        assertEquals("session-fork", AgentRuntimeRegistry.session("instance-config")?.sessionId)
        assertEquals(Unit, modeChanged.value)
        assertEquals("plan", provider.connection.selectedModeId)
    }

    @Test
    fun `草稿复用已公布能力并在首发前应用配置和模式`() = runTest {
        val thoughtOption = AgentConfigOption.Select(
            id = "thought",
            name = "推理强度",
            category = AgentConfigCategory.ThoughtLevel,
            currentValue = "medium",
            choices = listOf(
                AgentConfigChoice("medium", "中"),
                AgentConfigChoice("high", "高")
            )
        )
        val provider = FakeProvider(
            initialModes = listOf(AgentMode("build", "构建"), AgentMode("plan", "计划")),
            initialConfiguration = listOf(thoughtOption),
            initialCommands = listOf(AgentCommand("review", "审查当前改动")),
            requestPermission = false
        )
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-draft-capabilities",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1"
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        AgentRuntimeRegistry.prepareNewSession("instance-draft-capabilities", 1L)
        val catalog = AgentRuntimeRegistry.draftCapabilityCatalog("instance-draft-capabilities", 1L)!!

        assertEquals(listOf("thought"), catalog.configuration.map { it.id })
        assertEquals(listOf("build", "plan"), catalog.modes.map { it.id })
        assertEquals(listOf("review"), catalog.commands.map { it.name })

        val configuration = AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-draft-capabilities",
            1L,
            "thought",
            AgentConfigValue.Select("high")
        )
        val mode = AgentRuntimeRegistry.selectDraftMode(
            "instance-draft-capabilities",
            1L,
            "plan"
        )
        assertTrue(configuration is AgentOperationResult.Success)
        assertTrue(mode is AgentOperationResult.Success)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.promptCalls)
        assertTrue(provider.connection.selectedModelValues.isEmpty())
        assertEquals(null, provider.connection.selectedModeId)

        val prompted = AgentRuntimeRegistry.prompt(
            "instance-draft-capabilities",
            1L,
            listOf(AgentContent.Text("Ping"))
        )

        assertTrue(prompted is AgentOperationResult.Success)
        assertEquals(listOf("new", "model", "mode", "prompt"), provider.connection.callOrder)
        assertEquals(listOf("high"), provider.connection.selectedModelValues)
        assertEquals("plan", provider.connection.selectedModeId)
    }

    @Test
    fun `权限类别沿用通用草稿配置并在首发前应用`() = runTest {
        val permissionOption = AgentConfigOption.Select(
            id = "approval-policy",
            name = "权限",
            category = AgentConfigCategory.Permission,
            currentValue = "ask",
            choices = listOf(
                AgentConfigChoice("ask", "请求批准"),
                AgentConfigChoice("trusted", "已信任")
            )
        )
        val provider = FakeProvider(
            initialConfiguration = listOf(permissionOption),
            requestPermission = false,
        )
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-draft-permission",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1",
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> },
        ) as AgentOperationResult.Success

        AgentRuntimeRegistry.prepareNewSession("instance-draft-permission", 1L)
        val selected = AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-draft-permission",
            1L,
            "approval-policy",
            AgentConfigValue.Select("trusted"),
        )

        assertTrue(selected is AgentOperationResult.Success)
        assertEquals(0, provider.connection.newSessionCalls)
        assertTrue(provider.connection.selectedModelValues.isEmpty())

        val prompted = AgentRuntimeRegistry.prompt(
            "instance-draft-permission",
            1L,
            listOf(AgentContent.Text("Ping")),
        )

        assertTrue(prompted is AgentOperationResult.Success)
        assertEquals(listOf("new", "model", "prompt"), provider.connection.callOrder)
        assertEquals(listOf("trusted"), provider.connection.selectedModelValues)
    }

    @Test
    fun `已有会话的模型推理和权限只在发送时应用并保留到下一轮`() = runTest {
        val modelOption = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/default",
            choices = listOf(
                AgentConfigChoice("opencode/default", "Default"),
                AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2"),
            ),
        )
        val thoughtOption = AgentConfigOption.Select(
            id = "thought",
            name = "推理强度",
            category = AgentConfigCategory.ThoughtLevel,
            currentValue = "medium",
            choices = listOf(
                AgentConfigChoice("medium", "中"),
                AgentConfigChoice("high", "高"),
            ),
        )
        val permissionOption = AgentConfigOption.Select(
            id = "approval-policy",
            name = "权限",
            category = AgentConfigCategory.Permission,
            currentValue = "ask",
            choices = listOf(
                AgentConfigChoice("ask", "请求批准"),
                AgentConfigChoice("trusted", "已信任"),
            ),
        )
        val provider = FakeProvider(
            initialConfiguration = listOf(modelOption, thoughtOption, permissionOption),
            requestPermission = false,
        )
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-active-draft",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                preferredSessionId = "historical-1",
                resolveDraftModelSelection = { target, _ ->
                    AgentSessionModelSelection("model", "${target.providerId}/${target.modelId}")
                },
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> },
        ) as AgentOperationResult.Success

        val model = AgentRuntimeRegistry.selectDraftModel(
            "instance-active-draft",
            1L,
            AgentDraftModelSelection("zhipu", "glm-5.2", usesAgentDefault = false),
        )
        val thought = AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-active-draft",
            1L,
            "thought",
            AgentConfigValue.Select("high"),
        )
        val permission = AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-active-draft",
            1L,
            "approval-policy",
            AgentConfigValue.Select("trusted"),
        )

        assertTrue(model is AgentOperationResult.Success)
        assertTrue(thought is AgentOperationResult.Success)
        assertTrue(permission is AgentOperationResult.Success)
        assertTrue(provider.connection.selectedModelValues.isEmpty())
        assertEquals(0, provider.connection.promptCalls)

        val first = AgentRuntimeRegistry.prompt(
            "instance-active-draft",
            1L,
            listOf(AgentContent.Text("第一轮")),
        )

        assertTrue(first is AgentOperationResult.Success)
        assertEquals(
            listOf("zhipu/glm-5.2", "high", "trusted"),
            provider.connection.selectedModelValues,
        )
        assertEquals(
            listOf("model", "model", "model", "prompt"),
            provider.connection.callOrder,
        )
        assertEquals(
            listOf("model", "thought", "approval-policy"),
            AgentRuntimeRegistry.draftCapabilityCatalog("instance-active-draft", 1L)
                ?.configuration
                ?.map { it.id },
        )
        assertEquals(
            mapOf(
                "thought" to AgentConfigValue.Select("high"),
                "approval-policy" to AgentConfigValue.Select("trusted"),
            ),
            AgentRuntimeRegistry.draftPreferences("instance-active-draft", 1L)?.configuration,
        )
        assertEquals(
            AgentDraftModelSelection("zhipu", "glm-5.2", usesAgentDefault = false),
            AgentRuntimeRegistry.draftModelSelection("instance-active-draft", 1L),
        )

        val second = AgentRuntimeRegistry.prompt(
            "instance-active-draft",
            1L,
            listOf(AgentContent.Text("第二轮")),
        )

        assertTrue(second is AgentOperationResult.Success)
        assertEquals(
            listOf("zhipu/glm-5.2", "high", "trusted", "high", "trusted"),
            provider.connection.selectedModelValues,
        )
        assertEquals(
            listOf("model", "model", "model", "prompt", "model", "model", "prompt"),
            provider.connection.callOrder,
        )
        assertEquals(2, provider.connection.promptCalls)
    }

    @Test
    fun `支持附加目录的 Agent 在首发创建会话时收到安卓真实路径`() = runTest {
        val provider = FakeProvider(
            additionalDirectoriesSupported = true,
            requestPermission = false
        )
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-android-storage",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                additionalDirectories = listOf(
                    "/storage/emulated/0",
                    " /storage/1234-5678 ",
                    "/storage/emulated/0"
                )
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> }
        ) as AgentOperationResult.Success

        AgentRuntimeRegistry.prompt(
            "instance-android-storage",
            1L,
            listOf(AgentContent.Text("整理下载目录"))
        ) as AgentOperationResult.Success

        assertEquals(
            listOf("/storage/emulated/0", "/storage/1234-5678"),
            provider.connection.lastNewSessionRequest?.additionalDirectories
        )
    }

    @Test
    fun `发送时Provider准备要求重连则新连接成功后再创建会话`() = runTest {
        val modelOption = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/default",
            choices = listOf(
                AgentConfigChoice("opencode/default", "Default"),
                AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2"),
            ),
        )
        val provider = FakeProvider(initialConfiguration = listOf(modelOption), requestPermission = false)
        var prepareCalls = 0
        AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = "instance-provider-prepare",
                generation = 1L,
                providerId = "fake",
                cwd = "/workspace",
                resolveDraftModelSelection = { target, _ ->
                    AgentSessionModelSelection("model", "${target.providerId}/${target.modelId}")
                },
                prepareDraftModelSelection = {
                    prepareCalls++
                    AgentProviderPreparationResult.Ready(
                        effect = AgentSessionConfigurationEffect.Reconnect,
                        nativeConfigurationChanged = true,
                    )
                },
            ),
            provider = provider,
            statusSink = AgentRuntimeStatusSink { _, _, _ -> },
        ) as AgentOperationResult.Success
        AgentRuntimeRegistry.selectDraftModel(
            "instance-provider-prepare",
            1L,
            AgentDraftModelSelection("zhipu", "glm-5.2", usesAgentDefault = false),
        )

        val result = AgentRuntimeRegistry.prompt(
            "instance-provider-prepare",
            1L,
            listOf(AgentContent.Text("你好")),
        )

        assertTrue(result is AgentOperationResult.Success)
        assertEquals(1, prepareCalls)
        assertEquals(2, provider.connections.size)
        assertTrue(provider.connections.first().disconnected.get())
        assertEquals(listOf("new", "model", "prompt"), provider.connections.last().callOrder)
        assertEquals(listOf("zhipu/glm-5.2"), provider.connections.last().selectedModelValues)
    }

    private class FakeProvider(
        private val resumeSupported: Boolean = true,
        private val loadSupported: Boolean = true,
        private val restoreFails: Boolean = false,
        private val initialModes: List<AgentMode> = emptyList(),
        private val initialConfiguration: List<AgentConfigOption> = emptyList(),
        private val initialCommands: List<AgentCommand> = emptyList(),
        private val deleteSupported: Boolean = false,
        private val renameSupported: Boolean = false,
        private val newSessionFailures: Int = 0,
        private val promptFailures: Int = 0,
        private val configurationFailures: Int = 0,
        private val additionalDirectoriesSupported: Boolean = false,
        private val requestPermission: Boolean = true
    ) : KiteAgentProvider {
        lateinit var connection: FakeConnection
        val connections = mutableListOf<FakeConnection>()
        override val id: String = "fake"

        override suspend fun connect(
            request: AgentConnectionRequest,
            client: AgentClientEndpoint
        ): AgentOperationResult<KiteAgentConnection> {
            connection = FakeConnection(
                client,
                resumeSupported,
                loadSupported,
                restoreFails,
                initialModes,
                initialConfiguration,
                initialCommands,
                deleteSupported,
                renameSupported,
                newSessionFailures,
                promptFailures,
                configurationFailures,
                additionalDirectoriesSupported,
                requestPermission
            )
            connections += connection
            return AgentOperationResult.Success(connection)
        }
    }

    private class FakeConnection(
        private val endpoint: AgentClientEndpoint,
        resumeSupported: Boolean,
        loadSupported: Boolean,
        private val restoreFails: Boolean,
        private val initialModes: List<AgentMode>,
        private val initialConfiguration: List<AgentConfigOption>,
        private val initialCommands: List<AgentCommand>,
        private val deleteSupported: Boolean,
        private val renameSupported: Boolean,
        private var newSessionFailures: Int,
        private var promptFailures: Int,
        private var configurationFailures: Int,
        additionalDirectoriesSupported: Boolean,
        private val requestPermission: Boolean
    ) : KiteAgentConnection {
        val disconnected = AtomicBoolean(false)
        var newSessionCalls = 0
        var loadSessionCalls = 0
        var resumeSessionCalls = 0
        var promptCalls = 0
        var lastNewSessionRequest: AgentNewSessionRequest? = null
        var lastListRequest: AgentSessionListRequest? = null
        val listRequests = mutableListOf<AgentSessionListRequest>()
        var sessionListHandler: ((AgentSessionListRequest) -> AgentOperationResult<AgentSessionPage>)? = null
        var selectedModeId: String? = null
        val selectedModelValues = mutableListOf<String>()
        val callOrder = mutableListOf<String>()
        val deletedSessions = mutableListOf<String>()
        val renamedSessions = mutableListOf<AgentSessionRenameRequest>()
        override val provider = AgentProviderInfo("fake", "Fake")
        override val capabilities = AgentCapabilities(
            sessions = AgentSessionCapabilities(
                load = loadSupported,
                list = true,
                resume = resumeSupported,
                fork = true,
                delete = deleteSupported,
                rename = renameSupported,
                additionalDirectories = additionalDirectoriesSupported
            )
        )
        private var nextSession = 0

        override suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot> {
            newSessionCalls++
            lastNewSessionRequest = request
            callOrder += "new"
            if (newSessionFailures > 0) {
                newSessionFailures--
                return AgentOperationResult.Failure("创建失败")
            }
            val sessionId = "session-${++nextSession}"
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready, "准备就绪")
            )
            if (initialCommands.isNotEmpty()) {
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.CommandsUpdated(initialCommands))
            }
            return AgentOperationResult.Success(AgentSessionSnapshot(
                sessionId,
                configuration = initialConfiguration,
                modes = initialModes
            ))
        }

        override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> {
            promptCalls++
            callOrder += "prompt"
            if (promptFailures > 0) {
                promptFailures--
                return AgentOperationResult.Failure("发送失败")
            }
            if (requestPermission) {
                val permission = endpoint.permissionHandler.request(
                    AgentPermissionRequest(
                        sessionId = request.sessionId,
                        toolCall = AgentToolCallPatch(id = "tool-1", title = "写文件"),
                        options = listOf(
                            AgentPermissionOption("allow_once", "允许一次", AgentPermissionKind.AllowOnce),
                            AgentPermissionOption("reject_once", "拒绝", AgentPermissionKind.RejectOnce)
                        )
                    )
                )
                check(permission == AgentPermissionOutcome.Selected("allow_once"))
            }
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.MessageChunk(
                    role = AgentMessageRole.Assistant,
                    content = AgentContent.Text("已完成")
                )
            )
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready)
            )
            return AgentOperationResult.Success(AgentTurnResult(AgentStopReason.EndTurn))
        }

        override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> =
            AgentOperationResult.Success(Unit)

        override suspend fun disconnect() {
            disconnected.set(true)
        }

        override suspend fun loadSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> {
            loadSessionCalls++
            if (restoreFails) return AgentOperationResult.Failure("恢复失败")
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.MessageChunk(
                    role = AgentMessageRole.User,
                    content = AgentContent.Text("历史问题"),
                    messageId = "history-user"
                )
            )
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.MessageChunk(
                    role = AgentMessageRole.Assistant,
                    content = AgentContent.Text("历史回答"),
                    messageId = "history-agent"
                )
            )
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready, "准备就绪")
            )
            if (initialCommands.isNotEmpty()) {
                endpoint.eventSink.onEvent(request.sessionId, AgentSessionEvent.CommandsUpdated(initialCommands))
            }
            return AgentOperationResult.Success(AgentSessionSnapshot(
                request.sessionId,
                configuration = initialConfiguration,
                modes = initialModes
            ))
        }

        override suspend fun listSessions(request: AgentSessionListRequest): AgentOperationResult<AgentSessionPage> {
            lastListRequest = request
            listRequests += request
            sessionListHandler?.let { return it(request) }
            return AgentOperationResult.Success(
                AgentSessionPage(
                    sessions = listOf(AgentSessionSummary("historical-1", request.cwd ?: "/workspace"))
                )
            )
        }
        override suspend fun resumeSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> {
            resumeSessionCalls++
            if (restoreFails) return AgentOperationResult.Failure("恢复失败")
            endpoint.eventSink.onEvent(
                request.sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready, "准备就绪")
            )
            return AgentOperationResult.Success(AgentSessionSnapshot(
                request.sessionId,
                configuration = initialConfiguration,
                modes = initialModes
            ))
        }
        override suspend fun forkSession(request: AgentExistingSessionRequest) =
            AgentOperationResult.Success(AgentSessionSnapshot("session-fork"))
        override suspend fun closeSession(sessionId: String) = unsupported<Unit>()
        override suspend fun deleteSession(sessionId: String): AgentOperationResult<Unit> {
            if (!deleteSupported) return unsupported()
            deletedSessions += sessionId
            return AgentOperationResult.Success(Unit)
        }
        override suspend fun renameSession(request: AgentSessionRenameRequest): AgentOperationResult<Unit> {
            if (!renameSupported) return unsupported()
            renamedSessions += request
            return AgentOperationResult.Success(Unit)
        }
        override suspend fun setConfiguration(
            sessionId: String,
            configId: String,
            value: AgentConfigValue
        ): AgentOperationResult<List<AgentConfigOption>> {
            callOrder += "model"
            if (configurationFailures > 0) {
                configurationFailures--
                return AgentOperationResult.Failure("模型切换失败")
            }
            val selected = (value as AgentConfigValue.Select).value
            selectedModelValues += selected
            val declared = initialConfiguration
                .filterIsInstance<AgentConfigOption.Select>()
                .firstOrNull { it.id == configId }
            if (declared != null) {
                return AgentOperationResult.Success(listOf(declared.copy(currentValue = selected)))
            }
            return AgentOperationResult.Success(
                listOf(
                    AgentConfigOption.Select(
                        id = configId,
                        name = "模型",
                        currentValue = selected,
                        choices = listOf(AgentConfigChoice(selected, selected))
                    )
                )
            )
        }

        override suspend fun setMode(sessionId: String, modeId: String): AgentOperationResult<Unit> {
            callOrder += "mode"
            selectedModeId = modeId
            return AgentOperationResult.Success(Unit)
        }

        private fun <T> unsupported(): AgentOperationResult<T> = AgentOperationResult.Unsupported("test")
    }
}
