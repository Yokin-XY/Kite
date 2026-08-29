package com.kite.app.agent.store

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentPlanEntry
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentToolCall
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentToolStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentConversationStoreTest {
    private val key = AgentConversationKey("opencode", "session-1")

    @Before
    fun setUp() {
        AgentConversationStore.resetForTest()
    }

    @After
    fun tearDown() {
        AgentConversationStore.resetForTest()
    }

    @Test
    fun `流式消息合并且工具更新覆盖同一工具事实`() {
        AgentConversationStore.bind("run-1", key)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting)
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                role = AgentMessageRole.Assistant,
                content = AgentContent.Text("你"),
                messageId = "message-1"
            )
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                role = AgentMessageRole.Assistant,
                content = AgentContent.Text("好"),
                messageId = "message-1"
            )
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.ToolCallStarted(
                AgentToolCall(
                    id = "tool-1",
                    title = "运行测试",
                    status = AgentToolStatus("pending")
                )
            )
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.ToolCallUpdated(
                AgentToolCallPatch(
                    id = "tool-1",
                    status = AgentToolStatus("completed"),
                    rawOutput = "通过"
                )
            )
        )
        AgentConversationStore.flushForTest()

        val state = AgentConversationStore.snapshot(key)!!
        val message = state.timeline[0] as AgentConversationItem.Message
        val tool = state.timeline[1] as AgentConversationItem.Tool
        assertEquals("你好", (message.content.single() as AgentContent.Text).text)
        assertEquals("completed", tool.call.status?.value)
        assertEquals("通过", tool.call.rawOutput)
        assertEquals(2, state.timeline.size)
    }

    @Test
    fun `权限等待和恢复只修改会话 Store`() {
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        val request = AgentPermissionRequest(
            sessionId = key.sessionId,
            toolCall = AgentToolCallPatch("tool-1", title = "删除文件"),
            options = listOf(
                AgentPermissionOption("allow", "允许一次", AgentPermissionKind.AllowOnce)
            )
        )

        val waiting = AgentConversationStore.requestPermission(key, request)
        val restored = AgentConversationStore.resolvePermission(key)

        assertEquals(AgentSessionPhase.WaitingPermission, waiting?.phase)
        assertEquals(request, waiting?.pendingPermission)
        assertEquals(AgentSessionPhase.Ready, restored?.phase)
        assertNull(restored?.pendingPermission)
    }

    @Test
    fun `高频 chunk 在一帧内合并发布且正文不丢失`() {
        AgentConversationStore.bind("run-1", key)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting)
        )
        val before = AgentConversationStore.publicationCountForTest()

        repeat(2_000) {
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    role = AgentMessageRole.Assistant,
                    content = AgentContent.Text("字"),
                    messageId = "stream"
                )
            )
        }
        AgentConversationStore.flushForTest()

        val publications = AgentConversationStore.publicationCountForTest() - before
        val message = AgentConversationStore.snapshot(key)!!.timeline.single() as AgentConversationItem.Message
        assertTrue("2000 个 chunk 不应触发同数量的可见发布", publications < 100)
        assertEquals(2_000, (message.content.single() as AgentContent.Text).text.length)
    }

    @Test
    fun `页面重建可按稳定 key 重取同一会话且移除实例会清理投影`() {
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(AgentMessageRole.Assistant, AgentContent.Text("已恢复"))
        )

        assertEquals("run-1", AgentConversationStore.snapshot(key)?.instanceId)
        assertEquals(key, AgentConversationStore.conversations.value.keys.single())

        val removed = AgentConversationStore.removeInstance("run-1")
        assertEquals(1, removed.size)
        assertNull(AgentConversationStore.snapshot(key))
        assertTrue(AgentConversationStore.conversations.value.isEmpty())
    }

    @Test
    fun `底层更换原生会话标识时完整迁移既有时间线`() {
        val nextKey = AgentConversationKey("opencode", "session-2")
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(AgentMessageRole.User, AgentContent.Text("第一问"), "user-1")
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(AgentMessageRole.Assistant, AgentContent.Text("第一答"), "answer-1")
        )
        AgentConversationStore.bind("run-1", nextKey, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(
            nextKey,
            AgentSessionEvent.CurrentModeChanged("plan")
        )

        val moved = AgentConversationStore.rekey("run-1", key, nextKey)

        assertNull(AgentConversationStore.snapshot(key))
        assertEquals(nextKey, moved.key)
        assertEquals("plan", moved.currentModeId)
        assertEquals(
            listOf("第一问", "第一答"),
            moved.timeline.filterIsInstance<AgentConversationItem.Message>().map { message ->
                (message.content.single() as AgentContent.Text).text
            }
        )

        AgentConversationStore.applyEvent(
            nextKey,
            AgentSessionEvent.MessageChunk(AgentMessageRole.User, AgentContent.Text("第二问"), "user-2")
        )
        assertEquals(3, AgentConversationStore.snapshot(nextKey)?.timeline?.size)
    }

    @Test
    fun `Agent 主动更新会话配置时完整保留返回顺序和未知分类`() {
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        val options = listOf(
            AgentConfigOption.Select(
                id = "vendor-speed",
                name = "Vendor Speed",
                category = AgentConfigCategory("vendor_speed"),
                currentValue = "fast",
                choices = listOf(AgentConfigChoice("fast", "Fast"))
            ),
            AgentConfigOption.Select(
                id = "model",
                name = "Model",
                category = AgentConfigCategory.Model,
                currentValue = "provider/model",
                choices = listOf(AgentConfigChoice("provider/model", "Model"))
            )
        )

        AgentConversationStore.applyEvent(key, AgentSessionEvent.ConfigurationUpdated(options))
        AgentConversationStore.flushForTest()

        assertEquals(listOf("vendor-speed", "model"), AgentConversationStore.snapshot(key)!!.configuration.map { it.id })
    }

    @Test
    fun `计划按当前轮次进入时间线且更新同一计划项`() {
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting))
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.PlanUpdated(
                listOf(AgentPlanEntry("检查项目", "high", "in_progress"))
            )
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.PlanUpdated(
                listOf(
                    AgentPlanEntry("检查项目", "high", "completed"),
                    AgentPlanEntry("运行测试", "medium", "in_progress")
                )
            )
        )

        val snapshot = AgentConversationStore.snapshot(key)!!
        val timelinePlan = snapshot.timeline.single() as AgentConversationItem.Plan

        assertEquals(snapshot.plan, timelinePlan.entries)
        assertEquals(listOf("completed", "in_progress"), timelinePlan.entries.map { it.status })
    }

    @Test
    fun `历史回放在完成前不发布半份结果且首屏只暴露最近窗口`() {
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(AgentMessageRole.Assistant, AgentContent.Text("原内容"), "old")
        )
        AgentConversationStore.flushForTest()
        AgentConversationStore.beginHistoryReplay("run-1", key)
        val beforeReplayEvents = AgentConversationStore.publicationCountForTest()

        repeat(120) { index ->
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.Assistant,
                    AgentContent.Text("历史 $index"),
                    "history-$index"
                )
            )
        }

        assertEquals(beforeReplayEvents, AgentConversationStore.publicationCountForTest())
        assertEquals("原内容", ((AgentConversationStore.snapshot(key)!!.timeline.single() as AgentConversationItem.Message)
            .content.single() as AgentContent.Text).text)

        val restored = AgentConversationStore.completeHistoryReplay(key)!!
        assertEquals(120, restored.history.totalItems)
        assertEquals(80, restored.history.visibleItems)
        assertTrue(restored.history.hasEarlierItems)
        assertEquals("历史 40", ((restored.timeline.first() as AgentConversationItem.Message)
            .content.single() as AgentContent.Text).text)

        val expanded = AgentConversationStore.revealEarlier(key)!!
        assertEquals(120, expanded.timeline.size)
        assertFalse(expanded.history.hasEarlierItems)
    }

    @Test
    fun `重复历史回放替换投影而不追加重复消息`() {
        repeat(2) {
            AgentConversationStore.beginHistoryReplay("run-1", key)
            repeat(3) { index ->
                AgentConversationStore.applyEvent(
                    key,
                    AgentSessionEvent.MessageChunk(
                        AgentMessageRole.User,
                        AgentContent.Text("消息 $index"),
                        "message-$index"
                    )
                )
            }
            AgentConversationStore.completeHistoryReplay(key)
        }

        assertEquals(3, AgentConversationStore.snapshot(key)!!.history.totalItems)
    }

    @Test
    fun `原生历史确认本地回合后重复加载不会追加副本`() {
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.User,
                AgentContent.Text("你好你好"),
                "local-user",
            ),
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.Assistant,
                AgentContent.Text("OK"),
                "live-answer",
            ),
        )
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))

        repeat(2) {
            AgentConversationStore.beginHistoryReplay("run-1", key)
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.User,
                    AgentContent.Text("你好你好"),
                    "native-user",
                ),
            )
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.Assistant,
                    AgentContent.Text("OK"),
                    "native-answer",
                ),
            )
            AgentConversationStore.completeHistoryReplay(key)
        }

        assertEquals(
            listOf("你好你好", "OK"),
            AgentConversationStore.snapshot(key)!!.timeline
                .filterIsInstance<AgentConversationItem.Message>()
                .map { message -> (message.content.single() as AgentContent.Text).text },
        )
    }

    @Test
    fun `历史投影按内联媒体预算淘汰最早项目`() {
        val sharedPayload = "A".repeat(2 * 1024 * 1024)
        AgentConversationStore.beginHistoryReplay("run-1", key)

        repeat(20) { index ->
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.Assistant,
                    AgentContent.Image(sharedPayload, "image/png"),
                    "image-$index"
                )
            )
        }

        val restored = AgentConversationStore.completeHistoryReplay(key)!!
        assertTrue(restored.history.totalItems < 20)
        assertTrue(restored.history.truncatedItems > 0)
        assertTrue(restored.history.totalItems > 0)
    }

    @Test
    fun `本地用户消息先于 Prompting 时仍与过程和回答归入同一轮`() {
        var now = 1_000L
        AgentConversationStore.setNowMillisForTest { now }
        AgentConversationStore.bind("run-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.User,
                AgentContent.Text("开始"),
                "user-1",
            )
        )
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting))
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.Thought,
                AgentContent.Text("检查项目"),
                "thought-1",
            )
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.ToolCallStarted(
                AgentToolCall(id = "tool-1", title = "读取文件")
            )
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.Assistant,
                AgentContent.Text("完成"),
                "answer-1",
            )
        )
        now = 4_500L
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))

        val snapshot = AgentConversationStore.snapshot(key)!!
        assertEquals(listOf(1L), snapshot.timeline.map { it.turnOrdinal }.distinct())
        assertEquals(AgentConversationTurnState.Completed, snapshot.turns.single().state)
        assertEquals(3_500L, snapshot.turns.single().durationMillis)
    }

    @Test
    fun `历史回放按用户消息分轮且不虚构计时`() {
        AgentConversationStore.beginHistoryReplay("run-1", key)
        repeat(2) { index ->
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.User,
                    AgentContent.Text("问题 $index"),
                    "user-$index",
                )
            )
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.Assistant,
                    AgentContent.Text("回答 $index"),
                    "answer-$index",
                )
            )
        }

        val restored = AgentConversationStore.completeHistoryReplay(key)!!
        assertEquals(listOf(1L, 2L), restored.turns.map { it.ordinal })
        assertTrue(restored.turns.all { it.state == AgentConversationTurnState.Historical })
        assertTrue(restored.turns.all { it.durationMillis == null })
        assertEquals(listOf(1L, 1L, 2L, 2L), restored.timeline.map { it.turnOrdinal })
    }

    @Test
    fun `历史回放完成后的新回合恢复实时状态`() {
        AgentConversationStore.beginHistoryReplay("run-1", key)
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.User,
                AgentContent.Text("历史问题"),
                "history-user",
            )
        )
        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.Assistant,
                AgentContent.Text("历史回答"),
                "history-answer",
            )
        )
        AgentConversationStore.completeHistoryReplay(key)

        AgentConversationStore.applyEvent(
            key,
            AgentSessionEvent.MessageChunk(
                AgentMessageRole.User,
                AgentContent.Text("实时问题"),
                "live-user",
            )
        )

        val snapshot = AgentConversationStore.snapshot(key)!!
        assertEquals(AgentConversationTurnState.Historical, snapshot.turns.first().state)
        assertEquals(AgentConversationTurnState.Running, snapshot.turns.last().state)
        assertTrue(snapshot.turns.last().startedAtMillis != null)
    }
}
