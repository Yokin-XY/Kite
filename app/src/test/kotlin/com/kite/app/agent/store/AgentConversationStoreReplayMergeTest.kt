package com.kite.app.agent.store

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionPhase
import org.junit.Assert.assertEquals
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
}
