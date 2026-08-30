package com.kite.app.agent.config.native

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.codex.CodexPermission
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.normalizePublishedSessionConfiguration
import com.kite.app.agent.config.SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.sdk.configuration.AgentControlCatalogProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtocolSessionAgentConfigAdaptersTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Test
    fun `Codex 冷草稿持续公布原生会话权限目录`() {
        val control = requireNotNull(CodexAgentConfigAdapter(context).sessionPermissionControl())
        val option = control.option()
        val projected = AgentControlCatalogProjector.project(listOf(option)).permission

        assertEquals(SESSION_PERMISSION_CONFIG_ID, option.id)
        assertEquals(CodexPermission.entries.map { it.id }, control.profiles.map { it.id })
        assertEquals(CodexPermission.Custom.id, option.currentValue)
        assertEquals(
            listOf(
                AgentSessionPermissionHandling.AskUser,
                AgentSessionPermissionHandling.PreserveAgentDecision,
                AgentSessionPermissionHandling.AllowRequest,
                AgentSessionPermissionHandling.PreserveAgentDecision,
            ),
            control.profiles.map { it.handling },
        )
        assertNotNull(projected)
        assertEquals(CodexPermission.Custom.id, projected?.currentProfileId)
    }

    @Test
    fun `Gemini CLI 把 ACP 原生审批模式收进统一权限入口`() {
        val adapter = GeminiCliAgentConfigAdapter(context)
        val modes = adapter.normalizeSessionModes(
            listOf(
                AgentMode("default", "Default"),
                AgentMode("auto_edit", "Auto Edit"),
                AgentMode("yolo", "YOLO"),
                AgentMode("plan", "Plan"),
            )
        )

        val control = requireNotNull(adapter.sessionPermissionControl())
        assertTrue(modes.isEmpty())
        assertEquals(listOf("default", "auto_edit", "yolo", "plan"), control.profiles.map { it.id })
        assertEquals(
            listOf(
                AgentPermissionLevel.Approval,
                AgentPermissionLevel.Lenient,
                AgentPermissionLevel.Full,
                AgentPermissionLevel.ReadOnly,
            ),
            control.profiles.map { it.level },
        )
        assertEquals(control.profiles.map { it.id }, control.profiles.mapNotNull { control.nativeModeId(it.id) })
    }

    @Test
    fun `Gemini CLI 把 ACP 模型声明为官方目录并保留真实模型 ID`() {
        val adapter = GeminiCliAgentConfigAdapter(context)
        val native = AgentConfigOption.Select(
            id = "acp.session.model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "gemini-3-flash",
            choices = listOf(
                AgentConfigChoice("gemini-3-flash", "Gemini 3 Flash"),
                AgentConfigChoice("gemini-3-pro", "Gemini 3 Pro"),
            ),
        )

        val normalized = adapter.normalizeSessionConfiguration(listOf(native)).single() as AgentConfigOption.Select

        assertEquals(native.currentValue, normalized.currentValue)
        assertEquals(native.choices.map { it.value }, normalized.choices.map { it.value })
        assertEquals(listOf("gemini", "gemini"), normalized.choices.map { it.groupId })
        assertEquals(listOf(AgentModelSource.OfficialLogin, AgentModelSource.OfficialLogin), normalized.choices.map { it.modelSource })
    }

    @Test
    fun `Antigravity 只把官方启动参数可兑现的三档模式映射为统一权限`() {
        val adapter = AntigravityAgentConfigAdapter(context)
        val modes = adapter.normalizeSessionModes(
            listOf(
                AgentMode("default", "Default"),
                AgentMode("yolo", "YOLO"),
                AgentMode("plan", "Plan"),
            ),
        )
        val control = requireNotNull(adapter.sessionPermissionControl())

        assertTrue(modes.isEmpty())
        assertEquals(listOf("default", "yolo", "plan"), control.profiles.map { it.id })
        assertEquals(
            listOf(
                AgentPermissionLevel.Lenient,
                AgentPermissionLevel.Full,
                AgentPermissionLevel.ReadOnly,
            ),
            control.profiles.map { it.level },
        )
        assertEquals(control.profiles.map { it.id }, control.profiles.mapNotNull { control.nativeModeId(it.id) })
    }

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

    @Test
    fun `Devin 将原生权限档位与计划模式分开投影`() {
        val adapter = DevinCliAgentConfigAdapter(context)
        val control = requireNotNull(adapter.sessionPermissionControl())

        assertEquals(
            listOf(
                AgentPermissionLevel.Approval,
                AgentPermissionLevel.Lenient,
                AgentPermissionLevel.Smart,
                AgentPermissionLevel.Full,
            ),
            control.option().choices.map { it.permission },
        )
        assertEquals("normal", control.initialProfileId)
        assertEquals("bypass", control.nativeModeId("bypass"))
        assertEquals(
            listOf("plan", "custom"),
            adapter.normalizeSessionModes(
                listOf(
                    AgentMode("normal", "Normal"),
                    AgentMode("accept-edits", "Accept Edits"),
                    AgentMode("smart", "Smart"),
                    AgentMode("bypass", "Bypass"),
                    AgentMode("plan", "Plan"),
                    AgentMode("autonomous", "Autonomous"),
                    AgentMode("custom", "用户模式"),
                ),
            ).map { it.id },
        )
    }

    @Test
    fun `CodeBuddy 使用官方六档权限并从工作模式中移除`() {
        val adapter = CodeBuddyCodeAgentConfigAdapter(context)
        val control = requireNotNull(adapter.sessionPermissionControl())

        assertEquals(
            listOf(
                AgentPermissionLevel.ReadOnly,
                AgentPermissionLevel.Restricted,
                AgentPermissionLevel.Approval,
                AgentPermissionLevel.Lenient,
                AgentPermissionLevel.Smart,
                AgentPermissionLevel.Full,
            ),
            control.option().choices.map { it.permission },
        )
        assertEquals("default", control.initialProfileId)
        assertEquals("bypassPermissions", control.nativeModeId("bypassPermissions"))
        assertEquals(
            listOf("custom"),
            adapter.normalizeSessionModes(
                listOf(
                    AgentMode("default", "Default"),
                    AgentMode("acceptEdits", "Accept Edits"),
                    AgentMode("auto", "Auto"),
                    AgentMode("dontAsk", "Don't Ask"),
                    AgentMode("plan", "Plan"),
                    AgentMode("bypassPermissions", "Bypass"),
                    AgentMode("custom", "用户模式"),
                ),
            ).map { it.id },
        )
    }

    @Test
    fun `TraeCode 使用官方三档权限并从工作模式中移除`() {
        val adapter = TraeCodeAgentConfigAdapter(context)
        val control = requireNotNull(adapter.sessionPermissionControl())

        assertEquals(
            listOf(
                AgentPermissionLevel.Approval,
                AgentPermissionLevel.Smart,
                AgentPermissionLevel.Full,
            ),
            control.option().choices.map { it.permission },
        )
        assertEquals("default", control.initialProfileId)
        assertEquals("bypass_permissions", control.nativeModeId("bypass_permissions"))
        assertEquals(
            listOf("custom"),
            adapter.normalizeSessionModes(
                listOf(
                    AgentMode("default", "Default"),
                    AgentMode("auto", "Auto"),
                    AgentMode("bypass_permissions", "Bypass"),
                    AgentMode("custom", "用户模式"),
                ),
            ).map { it.id },
        )
    }
}
