package com.kite.app.agent.config.native

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.config.normalizePublishedSessionConfiguration
import com.kite.app.agent.config.SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtocolSessionAgentConfigAdaptersTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Test
    fun `Reasonix 只把官方 ACP 公布的三档审批映射为统一权限`() {
        val adapter = ReasonixAgentConfigAdapter(context)
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
        val adapter = ReasonixAgentConfigAdapter(context)
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

    @Test
    fun `Qwen Code 将原生 mode 映射为五档权限并从工作模式中移除`() {
        val adapter = QwenCodeAgentConfigAdapter(context)
        val native = AgentConfigOption.Select(
            id = "mode",
            name = "Approval Mode",
            category = AgentConfigCategory.Mode,
            currentValue = "default",
            choices = listOf("plan", "default", "auto-edit", "auto", "yolo")
                .map { AgentConfigChoice(it, it) },
        )

        val normalized = adapter.normalizeSessionConfiguration(listOf(native)).single() as AgentConfigOption.Select

        assertEquals(AgentConfigCategory.Permission, normalized.category)
        assertEquals(
            listOf(
                AgentPermissionLevel.ReadOnly,
                AgentPermissionLevel.Approval,
                AgentPermissionLevel.Lenient,
                AgentPermissionLevel.Smart,
                AgentPermissionLevel.Full,
            ),
            normalized.choices.map { it.permission },
        )
        assertEquals(emptyList<AgentMode>(), adapter.normalizeSessionModes(listOf(AgentMode("plan", "Plan"))))
    }

    @Test
    fun `Kimi Code 只映射当前模型公布的推理档并把 mode 作为权限`() {
        val adapter = KimiCodeAgentConfigAdapter(context)
        val mode = AgentConfigOption.Select(
            id = "mode",
            name = "Mode",
            category = AgentConfigCategory.Mode,
            currentValue = "auto",
            choices = listOf("plan", "default", "auto", "yolo").map { AgentConfigChoice(it, it) },
        )
        val thinking = AgentConfigOption.Select(
            id = "thinking",
            name = "Thinking",
            category = AgentConfigCategory.ThoughtLevel,
            currentValue = "high",
            choices = listOf("off", "high", "vendor-special").map { AgentConfigChoice(it, it) },
        )

        val normalized = adapter.normalizePublishedSessionConfiguration(listOf(mode, thinking))
        val permission = normalized.filterIsInstance<AgentConfigOption.Select>()
            .single { it.category == AgentConfigCategory.Permission }
        val reasoning = normalized.filterIsInstance<AgentConfigOption.Select>()
            .single { it.category == AgentConfigCategory.ThoughtLevel }

        assertEquals(AgentPermissionLevel.Smart, permission.choices.single { it.value == "auto" }.permission)
        assertEquals(listOf("off", "high"), reasoning.choices.map { it.value })
        assertEquals(listOf(AgentReasoningLevel.Off, AgentReasoningLevel.High), reasoning.choices.map { it.reasoning })
    }

    @Test
    fun `MiMo Code 缓存稳定内置模式并仅代理真实 ACP 权限请求`() {
        val adapter = MiMoCodeAgentConfigAdapter(context)

        assertEquals(listOf("build", "plan", "compose"), adapter.bundledWorkModeCatalog("mimo")?.modes?.map { it.id })
        assertEquals(
            listOf(AgentPermissionLevel.Restricted, AgentPermissionLevel.Approval, AgentPermissionLevel.Full),
            adapter.sessionPermissionControl()?.option()?.choices?.map { it.permission },
        )
        assertEquals(SESSION_PERMISSION_CONFIG_ID, adapter.sessionPermissionControl()?.option()?.id)
        assertEquals(
            "用户模式",
            adapter.normalizeSessionModes(listOf(AgentMode("custom", "用户模式"))).single().name,
        )
    }
}
