package com.kite.app.agent.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProviderPresetCatalogContractTest {
    @Test
    fun codingPlanIsSeparateFromGeneralZhipuApiAndUsesOfficialEndpoint() {
        val general = AgentProviderPresetCatalog.presets.single { it.id == "zhipu" }
        val coding = AgentProviderPresetCatalog.presets.single { it.id == "zhipu-coding-plan" }

        assertNotEquals(general.providerId, coding.providerId)
        assertNotEquals(general.baseUrl.trimEnd('/'), coding.baseUrl.trimEnd('/'))
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", coding.baseUrl)
        assertEquals(
            listOf("glm-5.2", "glm-5-turbo", "glm-4.7"),
            coding.models.map { it.id },
        )
        assertTrue(AgentProviderPresetCatalog.presets.map { it.id }.distinct().size ==
            AgentProviderPresetCatalog.presets.size)
        assertTrue(AgentProviderPresetCatalog.presets.map { it.providerId }.distinct().size ==
            AgentProviderPresetCatalog.presets.size)
    }
}

