package com.kite.app.agent.store

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 回归：恢复会话时 mergeLocalTurnsFrom 不得把本地 turn 复制成两份。
 *
 * 现场：live 会话中用户消息存两条（本地 echo local-xxx + 协议回显 uuid），
 * 回放历史里只有协议那条。merge 指纹消费错位会把整个 turn 判为"非持久"而重放复制，
 * 表现为 UI 顶部出现一份残缺 turn（User 行 4px 无文本），正文看似"消失"。
 */
@RunWith(RobolectricTestRunner::class)
class AgentConversationStoreReplayMergeTest {

    private val key = AgentConversationKey("provider.test", "session-1")

    @org.junit.Before
    fun setUp() {
        AgentConversationStore.resetForTest()
    }

    private fun user(text: String, messageId: String?) =
        AgentSessionEvent.MessageChunk(AgentMessageRole.User, AgentContent.Text(text), messageId)

    private fun assistant(text: String, messageId: String?) =
        AgentSessionEvent.MessageChunk(AgentMessageRole.Assistant, AgentContent.Text(text), messageId)

    @Test
    fun `bind 不同运行实例接管同一会话时继承既有时间线`() {
        AgentConversationStore.bind("instance-A", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(key, user("hi", "local-1"))
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting))
        AgentConversationStore.applyEvent(key, assistant("Hello!", "msg_1"))
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
        val before = AgentConversationStore.snapshot(key)!!
        assertEquals(2, before.timeline.size)

        // 新实例接管（进程重启/重连后的典型场景）：历史不得丢失
        AgentConversationStore.bind("instance-B", key, AgentSessionPhase.Preparing)
        val after = AgentConversationStore.snapshot(key)!!
        assertEquals(
            "换实例不得清空时间线",
            before.timeline.map { it.id },
            after.timeline.map { it.id },
        )
        assertEquals("instance-B", after.instanceId)

        // 接管后新回合继续追加在同一会话（开新轮）
        AgentConversationStore.applyEvent(key, user("again", "local-2"))
        val continued = AgentConversationStore.snapshot(key)!!
        assertEquals(3, continued.timeline.size)
        assertEquals(2, continued.turns.size)
    }

    @Test
    fun `local turn with protocol echo is not duplicated by replay merge`() {
        // live 会话：本地 echo（local-）+ 协议回显（uuid）+ Agent 回复
        AgentConversationStore.bind("instance-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(key, user("what was my first word", "local-100"))
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting))
        AgentConversationStore.applyEvent(key, assistant("Your first word was \"hi\".", "msg_a"))
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
        AgentConversationStore.applyEvent(key, user("what was my first word", "echo-uuid-1"))
        println("== BEFORE REPLAY ==")
        AgentConversationStore.snapshot(key)!!.timeline.forEach { item ->
            val m = item as? AgentConversationItem.Message
            println("ITEM turn=${item.turnOrdinal} role=${m?.role} mid=${m?.messageId?.take(14)}")
        }

        // 回放：协议视角的完整历史（同一 turn 只有 uuid 用户消息）
        AgentConversationStore.beginHistoryReplay("instance-2", key)
        AgentConversationStore.applyEvent(key, user("what was my first word", "echo-uuid-1"))
        AgentConversationStore.applyEvent(key, assistant("Your first word was \"hi\".", "msg_a"))
        AgentConversationStore.completeHistoryReplay(key)

        val snapshot = AgentConversationStore.snapshot(key)!!
        snapshot.timeline.forEach { item ->
            val m = item as? AgentConversationItem.Message
            println("ITEM turn=${item.turnOrdinal} role=${m?.role} mid=${m?.messageId?.take(14)} text=${m?.content?.firstOrNull()?.let { (it as? AgentContent.Text)?.text?.take(20) }}")
        }
        val userMessages = snapshot.timeline.filter {
            it is AgentConversationItem.Message && it.role == AgentMessageRole.User
        }
        val assistantMessages = snapshot.timeline.filter {
            it is AgentConversationItem.Message && it.role == AgentMessageRole.Assistant
        }
        assertEquals("用户消息不得重复", 1, userMessages.size)
        assertEquals("回复不得重复", 1, assistantMessages.size)
    }

    @Test
    fun `历史回放的交替 user 与 agent 流每条 user 开新轮`() {
        // 现场：Hermes 的历史回放不带 stopReason、无 phase 事件，全部消息曾挤进同一轮，
        // 表现为"用户连发 1、2、3、4、5 + 最后一条回复"。
        AgentConversationStore.bind("instance-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.beginHistoryReplay("instance-2", key)
        repeat(4) { index ->
            AgentConversationStore.applyEvent(key, user("你好", null))
            AgentConversationStore.applyEvent(key, assistant("回复$index", null))
        }
        AgentConversationStore.completeHistoryReplay(key)

        val snapshot = AgentConversationStore.snapshot(key)!!
        assertEquals("四轮交替应产生四个轮次", 4, snapshot.turns.size)
        val turnCounts = snapshot.timeline.groupBy { it.turnOrdinal }
        assertEquals("每轮各含 user+agent 两条", setOf(2), turnCounts.values.map { it.size }.toSet())
    }

    @Test
    fun `回放缺失既有历史消息时拒绝替换并保留原投影`() {
        // live 已有 2 条历史消息；重放只回了最后 1 条（Agent 回放不完整）。
        AgentConversationStore.bind("instance-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(key, user("first", null))
        AgentConversationStore.applyEvent(key, assistant("first-answer", null))
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
        val before = AgentConversationStore.snapshot(key)!!

        AgentConversationStore.beginHistoryReplay("instance-2", key)
        AgentConversationStore.applyEvent(key, user("first", null))
        val completed = AgentConversationStore.completeHistoryReplay(key)

        assertNull("覆盖性对账不通过时返回 null 供调用方重试", completed)
        val after = AgentConversationStore.snapshot(key)!!
        assertEquals(
            "原投影时间线必须原样保留",
            before.timeline.map { it.id },
            after.timeline.map { it.id },
        )
    }

    @Test
    fun `回放比当前投影更完整时正常替换`() {
        AgentConversationStore.bind("instance-1", key, AgentSessionPhase.Ready)
        AgentConversationStore.applyEvent(key, user("hi", null))
        AgentConversationStore.applyEvent(key, assistant("hello", null))
        AgentConversationStore.applyEvent(key, AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))

        AgentConversationStore.beginHistoryReplay("instance-2", key)
        AgentConversationStore.applyEvent(key, user("earlier", null))
        AgentConversationStore.applyEvent(key, assistant("earlier-answer", null))
        AgentConversationStore.applyEvent(key, user("hi", null))
        AgentConversationStore.applyEvent(key, assistant("hello", null))
        val completed = AgentConversationStore.completeHistoryReplay(key)

        assertNotNull("更完整的回放应正常替换", completed)
        assertEquals(4, AgentConversationStore.snapshot(key)!!.timeline.size)
    }
}
