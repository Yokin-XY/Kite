package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.store.AgentConversationItem
import com.kite.app.agent.store.AgentConversationTurn
import com.kite.app.agent.store.AgentConversationTurnState
import org.junit.Assert.assertTrue
import org.junit.Test

/** 复现真机：User + Thought + Assistant 的 Completed turn，正文是否被吞。 */
class AgentConversationComposeTurnsReproTest {

    @Test
    fun assistantTextSurvivesWhenThoughtPrecedesIt() {
        val items = listOf(
            AgentConversationItem.Message(
                id = "s:m:1", role = AgentMessageRole.User,
                messageId = "u1", content = listOf(AgentContent.Text("what was my first word")), turnOrdinal = 10L,
            ),
            AgentConversationItem.Message(
                id = "s:m:2", role = AgentMessageRole.Thought,
                messageId = "msg_x", content = listOf(AgentContent.Text("thinking...")), turnOrdinal = 10L,
            ),
            AgentConversationItem.Message(
                id = "s:m:3", role = AgentMessageRole.Assistant,
                messageId = "msg_x", content = listOf(AgentContent.Text("Your first word was \"hi\".")), turnOrdinal = 10L,
            ),
        )
        val turns = listOf(
            AgentConversationTurn(
                ordinal = 10L,
                state = AgentConversationTurnState.Completed,
                startedAtMillis = 1000L,
                endedAtMillis = 25000L,
            ),
        )

        val blocks = AgentConversationPresentation.composeTurns(
            items = items,
            turns = turns,
            phase = AgentSessionPhase.Ready,
        )

        val texts = blocks.joinToString("\n") { it::class.simpleName + " " + it.id }
        println("BLOCKS:\n$texts")
        val assistantVisible = blocks.any { it is AgentConversationDisplayItem.AssistantText && it.id.startsWith("s:m:3") }
        assertTrue("AssistantText 被吞！blocks=$texts", assistantVisible)
    }
}
