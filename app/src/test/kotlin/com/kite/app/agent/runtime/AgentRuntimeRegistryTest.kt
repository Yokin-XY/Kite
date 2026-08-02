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
import com.kite.app.agent.contract.AgentDraftConfigurationPreview
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
import com.kite.app.agent.sdk.skill.AgentPromptDraft
import com.kite.app.agent.sdk.skill.AgentSelectedSkill
import com.kite.app.agent.sdk.skill.AgentSkillPromptComposer
import com.kite.app.agent.config.AgentSessionModelSelection
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.sdk.configuration.AgentProviderPreparationResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
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
    private val emptyStatusSink = AgentRuntimeStatusSink { _, _, _ -> }

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
    fun `Skill胶囊留在会话显示而中性提示只进入Agent请求`() = runTest {
        val provider = FakeProvider(requestPermission = false)
        start(provider, request("instance-skill-draft"))

        val result = AgentRuntimeRegistry.prompt(
            "instance-skill-draft",
            1L,
            AgentPromptDraft(
                content = listOf(AgentContent.Text("帮我看看这个 Skill 是干什么的")),
                skills = listOf(AgentSelectedSkill("document-helper", "文档助手")),
            ),
        )

        assertTrue(result is AgentOperationResult.Success)
        val sent = provider.connection.promptRequests.single().content.filterIsInstance<AgentContent.Text>()
        assertTrue(sent.first().text.contains("不要仅因关联而强制执行"))
        assertEquals(
            listOf(AgentContent.SkillReference("document-helper", "文档助手")),
            AgentSkillPromptComposer.restoreVisibleText(sent.first().text),
        )
        assertEquals("帮我看看这个 Skill 是干什么的", sent.last().text)
        val local = AgentConversationStore.snapshot(AgentConversationKey("fake", "session-1"))!!
            .timeline.filterIsInstance<com.kite.app.agent.store.AgentConversationItem.Message>()
            .single { it.role == AgentMessageRole.User }
        assertEquals(
            listOf(AgentContent.SkillReference("document-helper", "文档助手"), AgentContent.Text("帮我看看这个 Skill 是干什么的")),
            local.content,
        )
    }

    @Test
    fun `启动 权限 消息和停止都保持同一实例代次`() = runTest {
        val provider = FakeProvider()
        val phases = mutableListOf<AgentSessionPhase>()
        val visibleSessionIds = mutableListOf<String?>()
        val session = start(
            provider,
            request("instance-1", generation = 42L),
            AgentRuntimeStatusSink { sessionId, phase, _ ->
                visibleSessionIds += sessionId
                phases += phase
            },
        )

        assertEquals(null, session.sessionId)
        assertTrue(session.isDraft)
        assertEquals(AgentRuntimeSessionState.ColdDraft, session.state)
        assertEquals(0, provider.connection.newSessionCalls)
        assertTrue(visibleSessionIds.all { it == null })

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
        assertTrue(visibleSessionIds.contains("session-1"))

        assertFalse(AgentRuntimeRegistry.stop("instance-1", 41L))
        assertTrue(AgentRuntimeRegistry.stop("instance-1", 42L))
        assertTrue(provider.connection.disconnected.get())
        assertEquals(AgentSessionPhase.Closed, AgentConversationStore.snapshot(key)?.phase)
    }

    @Test
    fun `冷草稿 列出和加载会话复用同一 provider connection`() = runTest {
        val provider = FakeProvider()
        val started = start(provider, request("instance-sessions", generation = 88L))

        val created = AgentRuntimeRegistry.prepareNewSession("instance-sessions", 88L) as AgentOperationResult.Success
        val listed = AgentRuntimeRegistry.listSessions("instance-sessions", 88L) as AgentOperationResult.Success
        val loaded = AgentRuntimeRegistry.loadSession(
            "instance-sessions",
            88L,
            "historical-1",
            "/workspace/old"
        ) as AgentOperationResult.Success

        assertEquals(null, started.sessionId)
        assertTrue(started.isDraft)
        assertEquals(AgentRuntimeSessionState.ColdDraft, started.state)
        assertEquals(null, created.value.sessionId)
        assertTrue(created.value.isDraft)
        assertEquals(AgentRuntimeSessionState.ColdDraft, created.value.state)
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
        assertTrue(defaultDraft.value.isDraft)
        assertEquals("/workspace", defaultDraft.value.cwd)
        assertEquals(0, provider.connection.newSessionCalls)
    }

    @Test
    fun `冷草稿创建原生会话前立即回显用户消息并在成功后迁移`() = runTest {
        val newSessionGate = CompletableDeferred<Unit>()
        val provider = FakeProvider(requestPermission = false, newSessionGate = newSessionGate)
        start(provider, request("instance-optimistic", generation = 9L))
        val draftSessionId = requireNotNull(
            AgentRuntimeRegistry.conversationProjectionSessionId("instance-optimistic", 9L),
        )
        val draftKey = AgentConversationKey("fake", draftSessionId)

        val prompt = async {
            AgentRuntimeRegistry.prompt(
                "instance-optimistic",
                9L,
                listOf(AgentContent.Text("立即显示")),
            )
        }
        testScheduler.runCurrent()

        val optimistic = requireNotNull(AgentConversationStore.snapshot(draftKey))
        val optimisticMessage = optimistic.timeline
            .filterIsInstance<com.kite.app.agent.store.AgentConversationItem.Message>()
            .single()
        assertEquals(listOf(AgentContent.Text("立即显示")), optimisticMessage.content)
        assertEquals(AgentSessionPhase.Preparing, optimistic.phase)
        assertEquals(1, provider.connection.newSessionCalls)

        newSessionGate.complete(Unit)
        assertTrue(prompt.await() is AgentOperationResult.Success)

        assertEquals(null, AgentConversationStore.snapshot(draftKey))
        val native = requireNotNull(
            AgentConversationStore.snapshot(AgentConversationKey("fake", "session-1")),
        )
        val nativeMessage = native.timeline
            .filterIsInstance<com.kite.app.agent.store.AgentConversationItem.Message>()
            .first { it.role == AgentMessageRole.User }
        assertEquals(listOf(AgentContent.Text("立即显示")), nativeMessage.content)
    }

    @Test
    fun `列出会话会聚合全部分页后再返回完整目录`() = runTest {
        val provider = FakeProvider()
        start(provider, "instance-pages")
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
        start(provider, "instance-loop")
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
        start(provider, "instance-first-send")

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
        start(provider, "instance-create-failure")

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

        val started = start(provider, "instance-restore", preferredSessionId = "historical-1")

        assertEquals("historical-1", started.sessionId)
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

        start(provider, "instance-restore", preferredSessionId = "historical-1")

        assertEquals(1, provider.connection.resumeSessionCalls)
        assertEquals(0, provider.connection.loadSessionCalls)
        assertEquals(1, AgentConversationStore.snapshot(key)!!.timeline.size)
    }

    @Test
    fun `只有 resume 能力时明确标记无法回放历史`() = runTest {
        val provider = FakeProvider(resumeSupported = true, loadSupported = false)

        start(provider, "instance-resume-only", preferredSessionId = "historical-1")

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
            request = request("instance-failed-restore", preferredSessionId = "missing"),
            provider = provider,
            statusSink = emptyStatusSink,
        )

        assertTrue(started is AgentOperationResult.Failure)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.resumeSessionCalls)
        assertEquals(1, provider.connection.loadSessionCalls)
        assertTrue(provider.connection.disconnected.get())
    }

    @Test
    fun `草稿模型应用失败时保留真实会话但不发送消息`() = runTest {
        val modelOption = selectOption(
            "model", "模型", AgentConfigCategory.Model, "opencode/default",
            "zhipu/glm-5.2" to "GLM-5.2",
        )
        val provider = FakeProvider(
            initialConfiguration = listOf(modelOption),
            configurationFailures = 1,
            requestPermission = false
        )
        start(
            provider,
            request("instance-draft-model-failure").copy(
                resolveDraftModelSelection = { _, _ -> AgentSessionModelSelection("model", "zhipu/glm-5.2") }
            ),
        )
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
    fun `会话管理只删除非当前会话并转发重命名`() = runTest {
        val provider = FakeProvider(deleteSupported = true, renameSupported = true)
        start(provider, "instance-admin", preferredSessionId = "session-1")

        val current = AgentRuntimeRegistry.deleteSession("instance-admin", 1L, "session-1")
        val historical = AgentRuntimeRegistry.deleteSession("instance-admin", 1L, "historical-1")
        val renamed = AgentRuntimeRegistry.renameSession(
            "instance-admin",
            1L,
            AgentSessionRenameRequest("historical-1", "  新标题  "),
        )

        assertTrue(current is AgentOperationResult.Failure)
        assertTrue(historical is AgentOperationResult.Success)
        assertEquals(listOf("historical-1"), provider.connection.deletedSessions)
        assertTrue(renamed is AgentOperationResult.Success)
        assertEquals(
            listOf(AgentSessionRenameRequest("historical-1", "新标题")),
            provider.connection.renamedSessions,
        )
    }

    @Test
    fun `配置变更和会话分支经过 registry 更新当前投影`() = runTest {
        val provider = FakeProvider(initialModes = listOf(AgentMode("build", "构建"), AgentMode("plan", "计划")))
        start(
            provider,
            request("instance-config", generation = 9L, preferredSessionId = "session-1").copy(
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
        )

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
    fun `草稿复用已公布能力并在首个请求前应用配置和模式`() = runTest {
        val thoughtOption = selectOption(
            "thought", "推理强度", AgentConfigCategory.ThoughtLevel, "medium",
            "medium" to "中", "high" to "高",
        )
        val permissionOption = selectOption(
            "approval-policy", "权限", AgentConfigCategory.Permission, "ask",
            "ask" to "请求批准", "trusted" to "已信任",
        )
        val provider = FakeProvider(
            initialModes = listOf(AgentMode("build", "构建"), AgentMode("plan", "计划")),
            initialConfiguration = listOf(thoughtOption, permissionOption),
            initialCommands = listOf(AgentCommand("review", "审查当前改动")),
            requestPermission = false
        )
        start(provider, "instance-draft-capabilities", preferredSessionId = "historical-1")

        AgentRuntimeRegistry.prepareNewSession("instance-draft-capabilities", 1L)
        val catalog = AgentRuntimeRegistry.draftCapabilityCatalog("instance-draft-capabilities", 1L)!!

        assertEquals(listOf("thought", "approval-policy"), catalog.configuration.map { it.id })
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
        val permission = AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-draft-capabilities",
            1L,
            "approval-policy",
            AgentConfigValue.Select("trusted"),
        )
        assertTrue(configuration is AgentOperationResult.Success)
        assertTrue(mode is AgentOperationResult.Success)
        assertTrue(permission is AgentOperationResult.Success)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.promptCalls)
        assertTrue(provider.connection.selectedModelValues.isEmpty())
        assertEquals(null, provider.connection.selectedModeId)

        val prompted = AgentRuntimeRegistry.prompt(
            "instance-draft-capabilities",
            1L,
            listOf(AgentContent.Text("Ping")),
        )

        assertTrue(prompted is AgentOperationResult.Success)
        assertEquals(listOf("new", "model", "model", "mode", "prompt"), provider.connection.callOrder)
        assertEquals(listOf("high", "trusted"), provider.connection.selectedModelValues)
        assertEquals("plan", provider.connection.selectedModeId)
    }

    @Test
    fun `数据库预选工作模式在发送前不生效且新草稿继续使用`() = runTest {
        val provider = FakeProvider(requestPermission = false)
        start(
            provider,
            request("instance-cached-mode").copy(
                initialDraftCatalog = AgentDraftCapabilityCatalog(
                    modes = listOf(AgentMode("build", "执行"), AgentMode("plan", "规划")),
                    currentModeId = "plan",
                ),
                initialDraftModeId = "plan",
            ),
        )

        assertEquals("plan", AgentRuntimeRegistry.draftPreferences("instance-cached-mode", 1L)?.modeId)
        assertEquals(null, provider.connection.selectedModeId)
        assertEquals(0, provider.connection.newSessionCalls)

        val prompted = AgentRuntimeRegistry.prompt(
            "instance-cached-mode",
            1L,
            listOf(AgentContent.Text("Ping")),
        )

        assertTrue(prompted is AgentOperationResult.Success)
        assertEquals(listOf("new", "mode", "prompt"), provider.connection.callOrder)
        assertEquals("plan", provider.connection.selectedModeId)

        AgentRuntimeRegistry.prepareNewSession("instance-cached-mode", 1L)
        assertEquals("plan", AgentRuntimeRegistry.draftPreferences("instance-cached-mode", 1L)?.modeId)
    }

    @Test
    fun `新草稿继承Agent最近选择而已有会话恢复自己的模型和权限`() = runTest {
        val permissionOption = selectOption(
            "approval-policy", "权限", AgentConfigCategory.Permission, "ask",
            "ask" to "请求批准", "full" to "完全访问",
        )
        val global = AgentDraftPersistenceSnapshot(
            modelSelection = AgentDraftModelSelection("opencode", "mimo", false),
            permissionSelection = AgentDraftConfigurationSelection(
                "approval-policy",
                AgentConfigValue.Select("ask"),
            ),
        )
        val perSession = AgentDraftPersistenceSnapshot(
            modelSelection = AgentDraftModelSelection("zhipu", "glm-5.2", false),
            permissionSelection = AgentDraftConfigurationSelection(
                "approval-policy",
                AgentConfigValue.Select("full"),
            ),
        )
        val publications = mutableListOf<Triple<String?, AgentDraftPersistenceSnapshot, Boolean>>()
        val provider = FakeProvider(
            initialConfiguration = listOf(permissionOption),
            requestPermission = false,
        )
        start(
            provider,
            request("instance-inherited-draft").copy(
                initialDraftCatalog = AgentDraftCapabilityCatalog(
                    configuration = listOf(permissionOption),
                ),
                initialDraftPreferences = global,
                loadSessionDraftPreferences = { sessionId ->
                    perSession.takeIf { sessionId == "historical-1" }
                },
                onDraftPreferencesChanged = { sessionId, preferences, updateDefault ->
                    publications += Triple(sessionId, preferences, updateDefault)
                },
            ),
        )

        assertEquals(global.modelSelection, AgentRuntimeRegistry.draftModelSelection("instance-inherited-draft", 1L))
        assertEquals(
            mapOf("approval-policy" to AgentConfigValue.Select("ask")),
            AgentRuntimeRegistry.draftPreferences("instance-inherited-draft", 1L)?.configuration,
        )

        AgentRuntimeRegistry.loadSession("instance-inherited-draft", 1L, "historical-1")

        assertEquals(perSession.modelSelection, AgentRuntimeRegistry.draftModelSelection("instance-inherited-draft", 1L))
        assertEquals(
            mapOf("approval-policy" to AgentConfigValue.Select("full")),
            AgentRuntimeRegistry.draftPreferences("instance-inherited-draft", 1L)?.configuration,
        )
        assertTrue(publications.any { (sessionId, preferences, updateDefault) ->
            sessionId == "historical-1" && preferences == perSession && !updateDefault
        })

        AgentRuntimeRegistry.prepareNewSession("instance-inherited-draft", 1L)
        assertEquals(global.modelSelection, AgentRuntimeRegistry.draftModelSelection("instance-inherited-draft", 1L))
        assertEquals(
            mapOf("approval-policy" to AgentConfigValue.Select("ask")),
            AgentRuntimeRegistry.draftPreferences("instance-inherited-draft", 1L)?.configuration,
        )
        AgentRuntimeRegistry.loadSession("instance-inherited-draft", 1L, "historical-1")

        val latestModel = AgentDraftModelSelection("opencode", "big-pickle", false)
        AgentRuntimeRegistry.selectDraftModel("instance-inherited-draft", 1L, latestModel)
        AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-inherited-draft",
            1L,
            "approval-policy",
            AgentConfigValue.Select("ask"),
        )
        AgentRuntimeRegistry.prepareNewSession("instance-inherited-draft", 1L)

        assertEquals(latestModel, AgentRuntimeRegistry.draftModelSelection("instance-inherited-draft", 1L))
        assertEquals(
            mapOf("approval-policy" to AgentConfigValue.Select("ask")),
            AgentRuntimeRegistry.draftPreferences("instance-inherited-draft", 1L)?.configuration,
        )
        assertTrue(publications.count { it.third } >= 2)
    }

    @Test
    fun `已有会话的模型推理和权限只在发送时应用并保留到下一轮`() = runTest {
        val modelOption = selectOption(
            "model", "模型", AgentConfigCategory.Model, "opencode/default",
            "opencode/default" to "Default", "zhipu/glm-5.2" to "GLM-5.2",
        )
        val thoughtOption = selectOption(
            "thought", "推理强度", AgentConfigCategory.ThoughtLevel, "medium",
            "medium" to "中", "high" to "高",
        )
        val permissionOption = selectOption(
            "approval-policy", "权限", AgentConfigCategory.Permission, "ask",
            "ask" to "请求批准", "trusted" to "已信任",
        )
        val provider = FakeProvider(
            initialConfiguration = listOf(modelOption, thoughtOption, permissionOption),
            requestPermission = false,
        )
        start(
            provider,
            request("instance-active-draft", preferredSessionId = "historical-1").copy(
                resolveDraftModelSelection = { target, _ ->
                    AgentSessionModelSelection("model", "${target.providerId}/${target.modelId}")
                },
            ),
        )

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
        start(
            provider,
            request("instance-android-storage").copy(
                additionalDirectories = listOf(
                    "/storage/emulated/0",
                    " /storage/1234-5678 ",
                    "/storage/emulated/0"
                )
            ),
        )

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
        val modelOption = selectOption(
            "model", "模型", AgentConfigCategory.Model, "opencode/default",
            "opencode/default" to "Default", "zhipu/glm-5.2" to "GLM-5.2",
        )
        val provider = FakeProvider(initialConfiguration = listOf(modelOption), requestPermission = false)
        var prepareCalls = 0
        start(
            provider,
            request("instance-provider-prepare").copy(
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
        )
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

    @Test
    fun `活动会话跨Provider重连时创建新原生会话而不恢复旧线程`() = runTest {
        val modelOption = selectOption(
            "model", "模型", AgentConfigCategory.Model, "openai/default",
            "openai/default" to "Default", "zhipu/glm-5.2" to "GLM-5.2",
        )
        val provider = FakeProvider(initialConfiguration = listOf(modelOption), requestPermission = false)
        start(
            provider,
            request("instance-provider-new-session").copy(
                resolveDraftModelSelection = { target, _ ->
                    AgentSessionModelSelection("model", "${target.providerId}/${target.modelId}")
                },
                prepareDraftModelSelection = {
                    AgentProviderPreparationResult.Ready(
                        effect = AgentSessionConfigurationEffect.ReconnectNewSession,
                        nativeConfigurationChanged = true,
                    )
                },
            ),
        )
        AgentRuntimeRegistry.prompt(
            "instance-provider-new-session",
            1L,
            listOf(AgentContent.Text("第一轮")),
        )
        AgentRuntimeRegistry.selectDraftModel(
            "instance-provider-new-session",
            1L,
            AgentDraftModelSelection("zhipu", "glm-5.2", usesAgentDefault = false),
        )

        val result = AgentRuntimeRegistry.prompt(
            "instance-provider-new-session",
            1L,
            listOf(AgentContent.Text("第二轮")),
        )

        assertTrue(result is AgentOperationResult.Success)
        assertEquals(2, provider.connections.size)
        assertEquals(0, provider.connections.last().loadSessionCalls)
        assertEquals(0, provider.connections.last().resumeSessionCalls)
        assertEquals(listOf("new", "model", "prompt"), provider.connections.last().callOrder)
        assertEquals(listOf("zhipu/glm-5.2"), provider.connections.last().selectedModelValues)
    }

    @Test
    fun `草稿模型变化会替换关联能力并清除不再支持的推理选择`() = runTest {
        val modelOption = selectOption(
            "model", "模型", AgentConfigCategory.Model, "gpt-5.6-sol",
            "gpt-5.6-sol" to "Sol", "gpt-5.6-terra" to "Terra",
        ).copy(choices = listOf(
            AgentConfigChoice("gpt-5.6-sol", "Sol", groupId = "openai"),
            AgentConfigChoice("gpt-5.6-terra", "Terra", groupId = "openai"),
        ))
        val solEffort = selectOption(
            "effort", "推理强度", AgentConfigCategory.ThoughtLevel, "low",
            "low" to "低", "high" to "高",
        )
        val terraEffort = selectOption(
            "effort", "推理强度", AgentConfigCategory.ThoughtLevel, "medium",
            "medium" to "中", "max" to "最高",
        )
        val provider = FakeProvider(
            initialConfiguration = listOf(modelOption, solEffort),
            requestPermission = false,
            modelPreviews = mapOf(
                ("openai" to "gpt-5.6-terra") to AgentDraftConfigurationPreview(
                    replaceCategories = setOf(AgentConfigCategory.ThoughtLevel),
                    options = listOf(terraEffort),
                ),
                ("custom" to "glm-5") to AgentDraftConfigurationPreview(
                    replaceCategories = setOf(AgentConfigCategory.ThoughtLevel),
                    options = emptyList(),
                ),
            ),
        )
        start(provider, request("instance-model-preview"))
        AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-model-preview",
            1L,
            "effort",
            AgentConfigValue.Select("high"),
        )

        AgentRuntimeRegistry.selectDraftModel(
            "instance-model-preview",
            1L,
            AgentDraftModelSelection("openai", "gpt-5.6-terra", usesAgentDefault = false),
        )

        val terraCatalog = requireNotNull(
            AgentRuntimeRegistry.draftCapabilityCatalog("instance-model-preview", 1L)
        )
        assertEquals(
            listOf("medium", "max"),
            terraCatalog.configuration.filterIsInstance<AgentConfigOption.Select>()
                .single { it.category == AgentConfigCategory.ThoughtLevel }
                .choices
                .map { it.value },
        )
        assertFalse(
            "effort" in requireNotNull(
                AgentRuntimeRegistry.draftPreferences("instance-model-preview", 1L)
            ).configuration
        )

        AgentRuntimeRegistry.selectDraftConfiguration(
            "instance-model-preview",
            1L,
            "effort",
            AgentConfigValue.Select("max"),
        )
        AgentRuntimeRegistry.selectDraftModel(
            "instance-model-preview",
            1L,
            AgentDraftModelSelection("custom", "glm-5", usesAgentDefault = false),
        )

        val customCatalog = requireNotNull(
            AgentRuntimeRegistry.draftCapabilityCatalog("instance-model-preview", 1L)
        )
        assertTrue(AgentConfigCategory.ThoughtLevel in customCatalog.resolvedConfigurationCategories)
        assertTrue(customCatalog.configuration.none { it.category == AgentConfigCategory.ThoughtLevel })
        assertFalse(
            "effort" in requireNotNull(
                AgentRuntimeRegistry.draftPreferences("instance-model-preview", 1L)
            ).configuration
        )
    }

    @Test
    fun `Provider配置已写入但首次重连失败时下次发送仍会继续重连`() = runTest {
        val modelOption = selectOption(
            "model", "模型", AgentConfigCategory.Model, "openai/default",
            "openai/default" to "Default", "zhipu/glm-5.2" to "GLM-5.2",
        )
        val provider = FakeProvider(
            initialConfiguration = listOf(modelOption),
            requestPermission = false,
            newSessionFailuresByConnection = listOf(0, 1, 0),
        )
        var prepareCalls = 0
        start(
            provider,
            request("instance-provider-retry").copy(
                resolveDraftModelSelection = { target, _ ->
                    AgentSessionModelSelection("model", "${target.providerId}/${target.modelId}")
                },
                prepareDraftModelSelection = {
                    prepareCalls++
                    AgentProviderPreparationResult.Ready(
                        effect = AgentSessionConfigurationEffect.ReconnectNewSession,
                        nativeConfigurationChanged = prepareCalls == 1,
                    )
                },
            ),
        )
        AgentRuntimeRegistry.selectDraftModel(
            "instance-provider-retry",
            1L,
            AgentDraftModelSelection("zhipu", "glm-5.2", usesAgentDefault = false),
        )

        val failed = AgentRuntimeRegistry.prompt(
            "instance-provider-retry",
            1L,
            listOf(AgentContent.Text("第一次")),
        )
        assertTrue(failed is AgentOperationResult.Failure)
        assertFalse(provider.connections.first().disconnected.get())
        assertTrue(provider.connections[1].disconnected.get())

        val retried = AgentRuntimeRegistry.prompt(
            "instance-provider-retry",
            1L,
            listOf(AgentContent.Text("第二次")),
        )

        assertTrue(retried is AgentOperationResult.Success)
        assertEquals(2, prepareCalls)
        assertEquals(3, provider.connections.size)
        assertTrue(provider.connections.first().disconnected.get())
        assertEquals(listOf("new", "model", "prompt"), provider.connections.last().callOrder)
    }

    private fun selectOption(
        id: String,
        name: String,
        category: AgentConfigCategory,
        currentValue: String,
        vararg choices: Pair<String, String>,
    ) = AgentConfigOption.Select(
        id = id,
        name = name,
        category = category,
        currentValue = currentValue,
        choices = choices.map { (value, label) -> AgentConfigChoice(value, label) },
    )

    private fun request(
        instanceId: String,
        generation: Long = 1L,
        preferredSessionId: String? = null,
    ) = AgentRuntimeStartRequest(
        instanceId = instanceId,
        generation = generation,
        providerId = "fake",
        cwd = "/workspace",
        preferredSessionId = preferredSessionId,
    )

    private suspend fun start(
        provider: FakeProvider,
        instanceId: String,
        generation: Long = 1L,
        preferredSessionId: String? = null,
    ): AgentRuntimeSession = start(
        provider,
        request(instanceId, generation, preferredSessionId),
    )

    private suspend fun start(
        provider: FakeProvider,
        request: AgentRuntimeStartRequest,
        statusSink: AgentRuntimeStatusSink = emptyStatusSink,
    ): AgentRuntimeSession = (
        AgentRuntimeRegistry.start(request, provider, statusSink) as AgentOperationResult.Success
    ).value

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
        private val requestPermission: Boolean = true,
        private val modelPreviews: Map<Pair<String, String>, AgentDraftConfigurationPreview> = emptyMap(),
        private val newSessionFailuresByConnection: List<Int>? = null,
        private val newSessionGate: CompletableDeferred<Unit>? = null,
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
                newSessionFailuresByConnection?.getOrNull(connections.size) ?: newSessionFailures,
                promptFailures,
                configurationFailures,
                additionalDirectoriesSupported,
                requestPermission,
                modelPreviews,
                newSessionGate,
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
        private val requestPermission: Boolean,
        private val modelPreviews: Map<Pair<String, String>, AgentDraftConfigurationPreview>,
        private val newSessionGate: CompletableDeferred<Unit>?,
    ) : KiteAgentConnection {
        val disconnected = AtomicBoolean(false)
        var newSessionCalls = 0
        var loadSessionCalls = 0
        var resumeSessionCalls = 0
        var promptCalls = 0
        val promptRequests = mutableListOf<AgentPromptRequest>()
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
            newSessionGate?.await()
            if (newSessionFailures > 0) {
                newSessionFailures--
                return AgentOperationResult.Failure("创建失败")
            }
            return AgentOperationResult.Success(readySnapshot("session-${++nextSession}"))
        }

        override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> {
            promptCalls++
            promptRequests += request
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
            return AgentOperationResult.Success(readySnapshot(request.sessionId, includeHistory = true))
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
            return AgentOperationResult.Success(readySnapshot(request.sessionId))
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

        override fun previewDraftModelConfiguration(
            providerId: String,
            modelId: String,
        ): AgentDraftConfigurationPreview? = modelPreviews[providerId to modelId]

        override suspend fun setMode(sessionId: String, modeId: String): AgentOperationResult<Unit> {
            callOrder += "mode"
            selectedModeId = modeId
            return AgentOperationResult.Success(Unit)
        }

        private suspend fun readySnapshot(sessionId: String, includeHistory: Boolean = false): AgentSessionSnapshot {
            if (includeHistory) {
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.MessageChunk(
                    AgentMessageRole.User, AgentContent.Text("历史问题"), "history-user"
                ))
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.MessageChunk(
                    AgentMessageRole.Assistant, AgentContent.Text("历史回答"), "history-agent"
                ))
            }
            endpoint.eventSink.onEvent(
                sessionId,
                AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready, "准备就绪"),
            )
            if (initialCommands.isNotEmpty()) {
                endpoint.eventSink.onEvent(sessionId, AgentSessionEvent.CommandsUpdated(initialCommands))
            }
            return AgentSessionSnapshot(sessionId, configuration = initialConfiguration, modes = initialModes)
        }

        private fun <T> unsupported(): AgentOperationResult<T> = AgentOperationResult.Unsupported("test")
    }
}
