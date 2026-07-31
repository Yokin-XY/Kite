package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentToolKind
import com.kite.app.agent.contract.AgentToolLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPermissionPresentationPolicyTest {
    @Test
    fun `保留 Agent 原生选项并解释一次和长期作用域`() {
        val presentation = AgentPermissionPresentationPolicy.present(
            AgentPermissionRequest(
                sessionId = "session",
                toolCall = AgentToolCallPatch(
                    id = "tool",
                    title = "修改项目文件",
                    kind = AgentToolKind("edit"),
                    locations = listOf(AgentToolLocation("/workspace/app.kt", 12)),
                ),
                options = listOf(
                    AgentPermissionOption("once", "仅允许本次修改", AgentPermissionKind.AllowOnce),
                    AgentPermissionOption("always", "本会话继续允许", AgentPermissionKind.AllowAlways),
                    AgentPermissionOption("deny", "拒绝", AgentPermissionKind.RejectOnce),
                ),
            )
        )

        assertEquals("修改项目文件", presentation.title)
        assertTrue(presentation.details.contains("工具类型 · edit"))
        assertTrue(presentation.details.contains("/workspace/app.kt:12"))
        assertEquals("仅允许本次修改", presentation.options[0].name)
        assertEquals("仅这一次", presentation.options[0].scopeHint)
        assertEquals("后续同类请求", presentation.options[1].scopeHint)
        assertTrue(presentation.options[1].allow)
        assertFalse(presentation.options[2].allow)
    }

    @Test
    fun `参数摘要会统一遮住常见密钥`() {
        val presentation = AgentPermissionPresentationPolicy.present(
            AgentPermissionRequest(
                sessionId = "session",
                toolCall = AgentToolCallPatch(
                    id = "tool",
                    rawInput = "API_KEY=secret-value curl -H 'Authorization: Bearer abc123'",
                ),
                options = listOf(
                    AgentPermissionOption("deny", "拒绝", AgentPermissionKind.RejectOnce)
                ),
            )
        )

        val detail = presentation.details.joinToString(" ")
        assertFalse(detail.contains("secret-value"))
        assertFalse(detail.contains("abc123"))
        assertTrue(detail.contains("••••"))
    }
}
