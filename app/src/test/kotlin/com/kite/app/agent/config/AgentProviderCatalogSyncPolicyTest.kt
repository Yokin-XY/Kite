package com.kite.app.agent.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentProviderCatalogSyncPolicyTest {
    @Test
    fun `目录刷新保留自定义模型且不会恢复用户删除的模型`() {
        val metadata = AgentProviderCatalogSyncMetadata(
            presetId = "zhipu-coding-plan",
            catalogModelIds = setOf("old", "keep", "hidden"),
        )
        val current = models("keep", "custom")
        val preset = preset("zhipu-coding-plan", "https://example.com/v1", "keep", "new", "hidden")

        val merged = AgentProviderCatalogSyncPolicy.merge(current, metadata, preset)

        assertEquals(listOf("keep", "new", "custom"), merged.models.map { it.id })
        assertEquals(setOf("old", "hidden"), merged.metadata.suppressedModelIds)
        assertEquals(1, merged.addedCount)
        assertEquals(0, merged.removedCount)
        assertEquals(1, merged.customCount)
        assertEquals(1, merged.suppressedCount)
    }

    @Test
    fun `手动重新添加目录模型会清除排除记录`() {
        val metadata = AgentProviderCatalogSyncMetadata(
            presetId = "zhipu-coding-plan",
            catalogModelIds = setOf("hidden"),
            suppressedModelIds = setOf("hidden"),
        )

        val edited = AgentProviderCatalogSyncPolicy.metadataAfterUserEdit(metadata, models("hidden"))!!
        val merged = AgentProviderCatalogSyncPolicy.merge(
            models("hidden"),
            edited,
            preset("zhipu-coding-plan", "https://example.com/v1", "hidden", "new"),
        )

        assertEquals(emptySet<String>(), merged.metadata.suppressedModelIds)
        assertEquals(listOf("hidden", "new"), merged.models.map { it.id })
    }

    @Test
    fun `旧供应商首次绑定会加入远端新模型并保留无法判定来源的旧模型`() {
        val current = models("old", "custom")
        val merged = AgentProviderCatalogSyncPolicy.merge(
            current,
            AgentProviderCatalogSyncMetadata("zhipu-coding-plan", emptySet()),
            preset("zhipu-coding-plan", "https://example.com/v1", "new", "current"),
        )

        assertEquals(listOf("new", "current", "old", "custom"), merged.models.map { it.id })
        assertEquals(setOf("new", "current"), merged.metadata.catalogModelIds)
        assertEquals(2, merged.addedCount)
        assertEquals(2, merged.customCount)
    }

    @Test
    fun `旧供应商只按真实地址匹配且优先稳定ID`() {
        val exact = preset("zhipu", "https://example.com/v1/", "a")
        val sameRoute = preset("zhipu-plan", "https://example.com/v1", "b")
        val other = preset("zhipu-global", "https://global.example.com/v1", "c")

        assertEquals(
            listOf("zhipu"),
            AgentProviderCatalogSyncPolicy.matchingPresets(
                "zhipu",
                "HTTPS://EXAMPLE.COM/v1",
                listOf(exact, sameRoute, other),
            ).map { it.id },
        )
        assertEquals(
            listOf("zhipu", "zhipu-plan"),
            AgentProviderCatalogSyncPolicy.matchingPresets(
                "legacy",
                "https://example.com/v1",
                listOf(exact, sameRoute, other),
            ).map { it.id },
        )
    }

    private fun models(vararg ids: String) = ids.map { AgentProviderModelSummary(it, it) }

    private fun preset(id: String, url: String, vararg modelIds: String) = AgentProviderPreset(
        id = id,
        providerId = id,
        displayName = id,
        baseUrl = url,
        models = models(*modelIds),
    )
}
