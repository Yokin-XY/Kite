package com.kite.app.agent.config.opencode

import com.kite.app.agent.contract.AgentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCodeWorkModeProfileTest {
    @Test
    fun `内置工作模式保留原生ID并提供中文显示`() {
        val catalog = openCodeWorkModeCatalog()

        assertEquals(listOf("build", "plan"), catalog.modes.map { it.id })
        assertEquals(listOf("执行", "规划"), catalog.modes.map { it.name })
        assertEquals("build", catalog.defaultModeId)
    }

    @Test
    fun `协议模式只翻译已知项并保留自定义模式`() {
        val normalized = normalizeOpenCodeWorkModes(
            listOf(
                AgentMode("plan", "Plan", "native"),
                AgentMode("review", "Review", "custom"),
            ),
        )

        assertEquals("规划", normalized.first().name)
        assertEquals(AgentMode("review", "Review", "custom"), normalized.last())
    }
}
