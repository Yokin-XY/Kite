package com.kite.app.agent.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProviderPresetCatalogContractTest {
    @Test
    fun codingPlanIsSeparateFromGeneralZhipuApiAndUsesOfficialEndpoint() {
        val presets = AgentProviderPresetCatalog.presetsFor("opencode")
        val general = presets.single { it.id == "zhipu" }
        val coding = presets.single { it.id == "zhipu-coding-plan" }

        assertNotEquals(general.providerId, coding.providerId)
        assertNotEquals(general.baseUrl.trimEnd('/'), coding.baseUrl.trimEnd('/'))
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", coding.baseUrl)
        assertEquals("zhipu", general.vendorId)
        assertEquals(general.vendorId, coding.vendorId)
        assertEquals(AgentProviderCategory.ChinaOfficial, coding.category)
        assertEquals(AgentProviderAccessChannel.Api, general.accessChannel)
        assertEquals(AgentProviderAccessChannel.CodingPlan, coding.accessChannel)
        assertEquals(
            listOf("glm-5.2", "glm-5-turbo", "glm-4.7"),
            coding.models.map { it.id },
        )
        assertUniqueAndComplete(presets)
    }

    @Test
    fun presetsAreScopedToTheAdapterNativeProtocol() {
        val openCode = AgentProviderPresetCatalog.presetsFor("opencode")
        val codex = AgentProviderPresetCatalog.presetsFor("codex")
        val claude = AgentProviderPresetCatalog.presetsFor("claude-code")

        assertTrue(openCode.size >= 14)
        assertTrue(codex.size >= 8)
        assertTrue(claude.size >= 9)
        assertFalse(openCode.any { it.id == "kimi-coding" })
        assertFalse(codex.any { it.id == "deepseek" })
        assertEquals(
            "https://open.bigmodel.cn/api/anthropic",
            claude.single { it.id == "zhipu-coding-plan" }.baseUrl,
        )
        assertEquals(
            "https://api.moonshot.cn/anthropic",
            claude.single { it.id == "kimi" }.baseUrl,
        )
        assertUniqueAndComplete(openCode)
        assertUniqueAndComplete(codex)
        assertUniqueAndComplete(claude)
    }

    @Test
    fun unsupportedAdaptersDoNotReceiveAUniversalFallbackCatalog() {
        assertTrue(AgentProviderPresetCatalog.presetsFor("gemini-cli").isEmpty())
        assertTrue(AgentProviderPresetCatalog.presetsFor("unknown").isEmpty())
        assertTrue(AgentProviderPresetCatalog.presetsFor(null).isEmpty())
    }

    private fun assertUniqueAndComplete(presets: List<AgentProviderPreset>) {
        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertEquals(presets.size, presets.map { it.providerId }.distinct().size)
        assertTrue(presets.all { it.baseUrl.isNotBlank() })
        assertTrue(presets.all { it.models.isNotEmpty() })
        assertTrue(presets.flatMap { it.models }.all { it.id.isNotBlank() && it.displayName.isNotBlank() })
    }
}
