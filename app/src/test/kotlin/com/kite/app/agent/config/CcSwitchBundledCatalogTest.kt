package com.kite.app.agent.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CC Switch 全量目录（[CcSwitchCatalogBundle]）与 Kite 手维护预置的合并行为。
 */
@RunWith(RobolectricTestRunner::class)
class CcSwitchBundledCatalogTest {

    @Test
    fun `bundled catalog parses for all six ACP agents`() {
        for (adapterId in listOf("claude-code", "codex", "opencode", "openclaw", "hermes", "pi-coding-agent")) {
            val presets = AgentProviderPresetCatalog.presetsFor(adapterId)
            assertTrue("$adapterId 应有预置", presets.isNotEmpty())
            presets.forEach { preset ->
                assertTrue("$adapterId/${preset.id} baseUrl 应为 URL: ${preset.baseUrl}", preset.baseUrl.startsWith("http"))
                assertTrue("$adapterId/${preset.id} 应有模型", preset.models.isNotEmpty())
            }
        }
    }

    @Test
    fun `pi catalog carries apiFormat from cc switch presets`() {
        val presets = CcSwitchBundledCatalogParser.presetsFor("pi-coding-agent")
        val formats = presets.mapNotNull { it.apiFormat }.toSet()
        assertTrue("应包含 openai-completions: $formats", "openai-completions" in formats)
        assertTrue("应包含 anthropic-messages: $formats", "anthropic-messages" in formats)
        val zhipu = presets.first { it.id == "zhipu-glm" }
        assertEquals("openai-completions", zhipu.apiFormat)
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", zhipu.baseUrl)
    }

    @Test
    fun `hermes catalog carries api_mode format`() {
        val formats = CcSwitchBundledCatalogParser.presetsFor("hermes")
            .mapNotNull { it.apiFormat }.toSet()
        assertTrue("chat_completions" in formats)
        assertTrue("anthropic_messages" in formats)
    }

    @Test
    fun `hand crafted zhipu coding plan keeps richer model list when merged`() {
        // Kite 手维护的智谱 Coding Plan pi 路由与 CC Switch zhipu-glm 同 baseUrl，
        // 合并后应保留 Kite 的完整模型清单（glm-5.3 系列）且 id 稳定。
        val presets = AgentProviderPresetCatalog.presetsFor("pi-coding-agent")
        val merged = presets.firstOrNull { it.id == "zhipu-coding-plan" }
        assertNotNull("手维护 zhipu-coding-plan 应保留", merged)
        assertTrue(
            "合并不应丢失手维护的 GLM-5.3 模型",
            merged!!.models.any { it.id == "glm-5.3" },
        )
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", merged!!.baseUrl)
    }

    @Test
    fun `bundled only presets appear as additional entries`() {
        val presets = AgentProviderPresetCatalog.presetsFor("pi-coding-agent")
        // CC Switch 独有条目（手维护没有的）应出现在目录里
        assertTrue(presets.size > CcSwitchProviderPresetDefinitions.presetsFor("pi-coding-agent").size)
        assertTrue("CC Switch 独有条目应出现", presets.any { it.id == "zhipu-glm-en" })
    }

    @Test
    fun `preset ids are unique per adapter`() {
        for (adapterId in listOf("claude-code", "codex", "opencode", "openclaw", "hermes", "pi-coding-agent")) {
            val ids = AgentProviderPresetCatalog.presetsFor(adapterId).map { it.id }
            assertEquals("$adapterId 预置 id 不应重复", ids.size, ids.distinct().size)
        }
    }
}
