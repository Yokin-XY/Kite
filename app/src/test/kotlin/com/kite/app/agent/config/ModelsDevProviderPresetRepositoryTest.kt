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
        val zcode = ModelsDevProviderPresetParser.presetsFor(MODELS_DEV_PAYLOAD, "zcode")
        val claude = ModelsDevProviderPresetParser.presetsFor(MODELS_DEV_PAYLOAD, "claude-code")

        assertFalse(hermes.any { it.id == "anthropic" })
        assertFalse(hermes.any { it.vendorId == "minimax" })
        assertEquals(hermes.map { it.id }, zcode.map { it.id })
        assertEquals(
            setOf("anthropic", "minimax", "minimax-cn"),
            claude.map { it.id }.toSet(),
        )
    }

    @Test
    fun `cross protocol catalog enriches every model without replacing hermes routes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.filesDir.resolve("agent-provider-catalog").deleteRecursively()
        val repository = ModelsDevProviderPresetRepository(context) {
            ModelsDevFetchResult.Updated(MODELS_DEV_PAYLOAD, "minimax-m3-etag")
        }

        val result = repository.refresh("hermes")
        val china = result.presets.single { it.id == "minimax" }
        val global = result.presets.single { it.id == "minimax-global" }
        val expectedModels = listOf(
            "MiniMax-M3",
            "MiniMax-M2.7",
            "MiniMax-M2.7-highspeed",
            "MiniMax-M2.5-highspeed",
            "MiniMax-M2.5",
            "MiniMax-M2.1",
            "MiniMax-M2",
        )

        assertEquals("https://api.minimaxi.com/v1", china.baseUrl)
        assertEquals("https://api.minimax.io/v1", global.baseUrl)
        assertEquals(expectedModels, china.models.map { it.id })
        assertEquals(expectedModels, global.models.map { it.id })
        assertEquals(7, china.catalogModelCount)
        assertEquals(7, global.catalogModelCount)
        assertEquals(AgentProviderPresetSource.ModelsDev, china.source)
        assertEquals(AgentProviderPresetRouteSource.AdapterCatalog, china.routeSource)
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
        assertTrue(cached.presets.any { it.id == "zhipu-coding-plan" })
        assertEquals(
            AgentProviderPresetSource.ModelsDevCache,
            cached.presets.single { it.id == "minimax" }.source,
        )
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
              "minimax-cn": {
                "id": "minimax-cn",
                "name": "MiniMax (minimaxi.com)",
                "api": "https://api.minimaxi.com/anthropic/v1",
                "npm": "@ai-sdk/anthropic",
                "models": {
                  "MiniMax-M3": {"name": "MiniMax-M3", "release_date": "2026-06-01", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.7": {"name": "MiniMax-M2.7", "release_date": "2026-03-18", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.7-highspeed": {"name": "MiniMax-M2.7-highspeed", "release_date": "2026-03-18", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.5-highspeed": {"name": "MiniMax-M2.5-highspeed", "release_date": "2026-02-13", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.5": {"name": "MiniMax-M2.5", "release_date": "2026-02-12", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.1": {"name": "MiniMax-M2.1", "release_date": "2025-12-23", "modalities": {"output": ["text"]}},
                  "MiniMax-M2": {"name": "MiniMax-M2", "release_date": "2025-10-27", "modalities": {"output": ["text"]}}
                }
              },
              "minimax": {
                "id": "minimax",
                "name": "MiniMax (minimax.io)",
                "api": "https://api.minimax.io/anthropic/v1",
                "npm": "@ai-sdk/anthropic",
                "models": {
                  "MiniMax-M3": {"name": "MiniMax-M3", "release_date": "2026-06-01", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.7": {"name": "MiniMax-M2.7", "release_date": "2026-03-18", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.7-highspeed": {"name": "MiniMax-M2.7-highspeed", "release_date": "2026-03-18", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.5-highspeed": {"name": "MiniMax-M2.5-highspeed", "release_date": "2026-02-13", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.5": {"name": "MiniMax-M2.5", "release_date": "2026-02-12", "modalities": {"output": ["text"]}},
                  "MiniMax-M2.1": {"name": "MiniMax-M2.1", "release_date": "2025-12-23", "modalities": {"output": ["text"]}},
                  "MiniMax-M2": {"name": "MiniMax-M2", "release_date": "2025-10-27", "modalities": {"output": ["text"]}}
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
