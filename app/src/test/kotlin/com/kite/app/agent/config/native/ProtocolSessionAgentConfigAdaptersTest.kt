package com.kite.app.agent.config.native

import com.kite.app.agent.config.normalizePublishedSessionConfiguration
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolSessionAgentConfigAdaptersTest {
    @Test
    fun `Reasonix 只把官方 ACP 公布的三档审批映射为统一权限`() {
        val adapter = ReasonixAgentConfigAdapter()
        val native = AgentConfigOption.Select(
            id = "tool_approval",
            name = "Tool Approval",
            category = AgentConfigCategory("tool_approval"),
            currentValue = "ask",
            choices = listOf("ask", "auto", "yolo").map { AgentConfigChoice(it, it) },
        )

        val normalized = adapter.normalizeSessionConfiguration(listOf(native)).single() as AgentConfigOption.Select

        assertEquals(AgentConfigCategory.Permission, normalized.category)
        assertEquals(
            listOf(AgentPermissionLevel.Approval, AgentPermissionLevel.Lenient, AgentPermissionLevel.Full),
            normalized.choices.map { it.permission },
        )
    }

    @Test
    fun `Reasonix 推理自动档由原生值映射且未公布值不会被补造`() {
        val adapter = ReasonixAgentConfigAdapter()
        val native = AgentConfigOption.Select(
            id = "effort",
            name = "Effort",
            category = AgentConfigCategory.ThoughtLevel,
            currentValue = "auto",
            choices = listOf("auto", "high", "vendor-special").map { AgentConfigChoice(it, it) },
        )

        val normalized = adapter.normalizePublishedSessionConfiguration(listOf(native)).single() as AgentConfigOption.Select

        assertEquals(listOf("auto", "high"), normalized.choices.map { it.value })
        assertEquals(AgentReasoningMode.Adaptive, normalized.choices.first().reasoning)
    }
}
