package com.kite.app.agent.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ModelsDevProviderPresetRepositoryTest {
    @Test
    fun `same vendor keeps api and coding plan as separate routes`() {
        val presets = ModelsDevProviderPresetParser.presetsFor(MODELS_DEV_PAYLOAD, "hermes")
        val api = presets.single { it.id == "zhipuai" }
        val codingPlan = presets.single { it.id == "zhipuai-coding-plan" }
        val globalCodingPlan = presets.single { it.id == "zai-coding-plan" }

        assertEquals("zhipu", api.vendorId)
        assertEquals(api.vendorId, codingPlan.vendorId)
        assertEquals(api.vendorId, globalCodingPlan.vendorId)
        assertEquals("Zhipu AI", codingPlan.vendorDisplayName)
        assertEquals(AgentProviderCategory.ChinaOfficial, api.category)
        assertEquals(AgentProviderAccessChannel.Api, api.accessChannel)
        assertEquals(AgentProviderAccessChannel.CodingPlan, codingPlan.accessChannel)
        assertEquals(AgentProviderMarket.China, codingPlan.market)
        assertEquals(AgentProviderMarket.Global, globalCodingPlan.market)
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", codingPlan.baseUrl)
        assertEquals("https://docs.bigmodel.cn/cn/guide/start/model-overview", codingPlan.documentationUrl)
        assertTrue(codingPlan.models.any { it.id == "glm-5.3-flash" })
        assertFalse(codingPlan.models.any { it.id == "embedding-3" })
    }

    @Test
    fun `adapter protocol filters incompatible provider families`() {
        val hermes = ModelsDevProviderPresetParser.presetsFor(MODELS_DEV_PAYLOAD, "hermes")
        val claude = ModelsDevProviderPresetParser.presetsFor(MODELS_DEV_PAYLOAD, "claude-code")

        assertFalse(hermes.any { it.id == "anthropic" })
        assertEquals(listOf("anthropic"), claude.map { it.id })
    }

    @Test
    fun `network failure reuses last successful models dev snapshot`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.filesDir.resolve("agent-provider-catalog").deleteRecursively()
        var fail = false
        val remote = ModelsDevCatalogRemote {
            if (fail) throw IOException("offline")
            ModelsDevFetchResult.Updated(MODELS_DEV_PAYLOAD, "test-etag")
        }
        val repository = ModelsDevProviderPresetRepository(context, remote)

        val live = repository.refresh("hermes")
        fail = true
        val cached = repository.refresh("hermes")

        assertEquals(AgentProviderPresetSource.ModelsDev, live.source)
        assertEquals(AgentProviderPresetSource.ModelsDevCache, cached.source)
        assertTrue(cached.presets.any { it.id == "zhipuai-coding-plan" })
        assertTrue(cached.warning?.contains("上次成功目录") == true)
    }

    private companion object {
        val MODELS_DEV_PAYLOAD = """
            {
              "zhipuai": {
                "id": "zhipuai",
                "name": "Zhipu AI",
                "api": "https://open.bigmodel.cn/api/paas/v4",
                "npm": "@ai-sdk/openai-compatible",
                "models": {
                  "glm-5.3-flash": {
                    "name": "GLM-5.3 Flash",
                    "release_date": "2026-08-20",
                    "modalities": {"output": ["text"]}
                  }
                }
              },
              "zhipuai-coding-plan": {
                "id": "zhipuai-coding-plan",
                "name": "Zhipu Coding Plan",
                "api": "https://open.bigmodel.cn/api/coding/paas/v4",
                "npm": "@ai-sdk/openai-compatible",
                "doc": "https://docs.bigmodel.cn/cn/guide/start/model-overview",
                "models": {
                  "glm-5.3-flash": {
                    "name": "GLM-5.3 Flash",
                    "release_date": "2026-08-20",
                    "modalities": {"output": ["text"]}
                  },
                  "embedding-3": {
                    "name": "Embedding 3",
                    "release_date": "2026-08-10",
                    "modalities": {"output": ["text"]}
                  }
                }
              },
              "zai-coding-plan": {
                "id": "zai-coding-plan",
                "name": "Z.AI Coding Plan",
                "api": "https://api.z.ai/api/coding/paas/v4",
                "npm": "@ai-sdk/openai-compatible",
                "models": {
                  "glm-5.3-flash": {
                    "name": "GLM-5.3 Flash",
                    "release_date": "2026-08-20",
                    "modalities": {"output": ["text"]}
                  }
                }
              },
              "anthropic": {
                "id": "anthropic",
                "name": "Anthropic",
                "api": "https://api.anthropic.com/v1",
                "npm": "@ai-sdk/anthropic",
                "models": {
                  "claude-sonnet": {
                    "name": "Claude Sonnet",
                    "release_date": "2026-07-01",
                    "modalities": {"output": ["text"]}
                  }
                }
              }
            }
        """.trimIndent()
    }
}
