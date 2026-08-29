package com.kite.app.agent.config

/**
 * 用户供应商与远端预置目录之间的同步事实。
 *
 * [presetId] 在单个 Adapter 的预置目录中稳定定位访问渠道；[catalogModelIds] 记录上次同步时
 * 由目录拥有的模型，而 [suppressedModelIds] 只记录用户明确从供应商中删除的目录模型。
 */
data class AgentProviderCatalogSyncMetadata(
    val presetId: String,
    val catalogModelIds: Set<String>,
    val suppressedModelIds: Set<String> = emptySet(),
)

data class AgentProviderCatalogMergeResult(
    val models: List<AgentProviderModelSummary>,
    val metadata: AgentProviderCatalogSyncMetadata,
    val addedCount: Int,
    val removedCount: Int,
    val customCount: Int,
    val suppressedCount: Int,
)

/** 目录刷新只合并编辑草稿；持久化仍由供应商页面的保存动作完成。 */
object AgentProviderCatalogSyncPolicy {
    /** 旧供应商只按真实路由匹配；名称相似不能建立长期目录绑定。 */
    fun matchingPresets(
        providerId: String,
        baseUrl: String,
        presets: List<AgentProviderPreset>,
    ): List<AgentProviderPreset> {
        val normalizedUrl = normalizeUrl(baseUrl)
        if (normalizedUrl.isBlank()) return emptyList()
        val routeMatches = presets.filter { normalizeUrl(it.baseUrl) == normalizedUrl }
        val exact = routeMatches.filter { it.providerId == providerId || it.id == providerId }
        return exact.ifEmpty { routeMatches }
    }

    fun metadataAfterUserEdit(
        metadata: AgentProviderCatalogSyncMetadata?,
        models: List<AgentProviderModelSummary>,
    ): AgentProviderCatalogSyncMetadata? {
        metadata ?: return null
        val currentIds = models.normalizedIds()
        return metadata.copy(
            suppressedModelIds = (metadata.suppressedModelIds + metadata.catalogModelIds) - currentIds,
        ).normalized()
    }

    fun merge(
        currentModels: List<AgentProviderModelSummary>,
        metadata: AgentProviderCatalogSyncMetadata,
        preset: AgentProviderPreset,
    ): AgentProviderCatalogMergeResult {
        val editedMetadata = metadataAfterUserEdit(metadata, currentModels) ?: metadata.normalized()
        val current = currentModels.normalizedModels()
        val currentById = current.associateBy(AgentProviderModelSummary::id)
        val previousCatalogIds = editedMetadata.catalogModelIds
        val nextCatalog = preset.models.normalizedModels()
        val nextCatalogIds = nextCatalog.mapTo(linkedSetOf(), AgentProviderModelSummary::id)

        val catalogModels = nextCatalog.mapNotNull { remote ->
            if (remote.id in editedMetadata.suppressedModelIds) null
            else currentById[remote.id] ?: remote
        }
        val customModels = current.filter { model ->
            model.id !in previousCatalogIds && model.id !in nextCatalogIds
        }
        val merged = (catalogModels + customModels).distinctBy(AgentProviderModelSummary::id)
        val mergedIds = merged.mapTo(linkedSetOf(), AgentProviderModelSummary::id)
        val currentIds = current.mapTo(linkedSetOf(), AgentProviderModelSummary::id)
        val nextMetadata = AgentProviderCatalogSyncMetadata(
            presetId = preset.id,
            catalogModelIds = nextCatalogIds,
            suppressedModelIds = editedMetadata.suppressedModelIds,
        ).normalized()

        return AgentProviderCatalogMergeResult(
            models = merged,
            metadata = nextMetadata,
            addedCount = (mergedIds - currentIds).size,
            removedCount = (currentIds - mergedIds).size,
            customCount = customModels.size,
            suppressedCount = nextCatalogIds.count { it in nextMetadata.suppressedModelIds },
        )
    }

    fun metadataForPreset(
        preset: AgentProviderPreset,
        models: List<AgentProviderModelSummary>,
    ): AgentProviderCatalogSyncMetadata = metadataAfterUserEdit(
        AgentProviderCatalogSyncMetadata(
            presetId = preset.id,
            catalogModelIds = preset.models.normalizedIds(),
        ),
        models,
    )!!

    private fun AgentProviderCatalogSyncMetadata.normalized(): AgentProviderCatalogSyncMetadata = copy(
        presetId = presetId.trim(),
        catalogModelIds = catalogModelIds.map(String::trim).filter(String::isNotBlank).toSet(),
        suppressedModelIds = suppressedModelIds.map(String::trim).filter(String::isNotBlank).toSet(),
    )

    private fun List<AgentProviderModelSummary>.normalizedIds(): Set<String> =
        mapNotNull { it.id.trim().takeIf(String::isNotBlank) }.toSet()

    private fun List<AgentProviderModelSummary>.normalizedModels(): List<AgentProviderModelSummary> =
        mapNotNull { model ->
            val id = model.id.trim()
            id.takeIf(String::isNotBlank)?.let {
                AgentProviderModelSummary(it, model.displayName.trim().ifBlank { it })
            }
        }.distinctBy(AgentProviderModelSummary::id)

    private fun normalizeUrl(value: String): String = value.trim().trimEnd('/').lowercase()
}
