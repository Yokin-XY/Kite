package com.kite.app.agent.config

/**
 * Kite Agent SDK 的供应商预置目录入口。
 *
 * CC Switch 的供应商预置以应用类型为作用域，同一供应商会针对 Claude、Codex、OpenCode 等工具
 * 使用不同的协议端点。Kite 保持同样的事实边界：页面只接收当前 Adapter 能原生写入的预置，
 * 具体端点差异留在兼容目录中；预置仍只是可编辑起点，不会在选择时直接改变 Agent 状态。
 *
 * 数据来源两层：
 * 1. [CcSwitchCatalogBundle] —— 构建期从 farion1231/cc-switch (MIT) 转换的全量目录（主体）。
 * 2. [CcSwitchProviderPresetDefinitions] —— Kite 手维护的少量补充（中国 CLI 生态、更完整的
 *    模型清单）。两层按 baseUrl 对齐合并：手维护条目保留元数据与模型顺序，合并层补齐差异模型；
 *    合并层独有的端点作为新条目追加。
 */
object AgentProviderPresetCatalog {

    fun presetsFor(adapterId: String?): List<AgentProviderPreset> {
        if (adapterId == null) return emptyList()
        val bundled = CcSwitchBundledCatalogParser.presetsFor(adapterId)
            .map { preset -> preset.toAgentProviderPreset() }
        val handCrafted = CcSwitchProviderPresetDefinitions.presetsFor(adapterId)
        if (bundled.isEmpty()) return handCrafted
        return merge(handCrafted, bundled)
    }

    /** 手维护与 CC Switch 全量目录合并；baseUrl 相同视为同一入口，模型取并集（手维护优先序）。 */
    private fun merge(
        handCrafted: List<AgentProviderPreset>,
        bundled: List<AgentProviderPreset>,
    ): List<AgentProviderPreset> {
        val merged = mutableListOf<AgentProviderPreset>()
        val consumed = mutableSetOf<Int>()
        for (preset in handCrafted) {
            val matchIndex = bundled.indexOfFirst { candidate ->
                candidate.id == preset.id || sameBaseUrl(candidate.baseUrl, preset.baseUrl)
            }
            if (matchIndex < 0) {
                merged += preset
                continue
            }
            consumed += matchIndex
            val candidate = bundled[matchIndex]
            merged += mergePreset(preset, candidate)
        }
        bundled.forEachIndexed { index, preset ->
            if (index !in consumed) merged += preset
        }
        return merged
    }

    private fun mergePreset(
        handCrafted: AgentProviderPreset,
        bundled: AgentProviderPreset,
    ): AgentProviderPreset {
        val knownModelIds = handCrafted.models.mapTo(mutableSetOf()) { model -> model.id }
        val extraModels = bundled.models.filterNot { model -> model.id in knownModelIds }
        val models = if (extraModels.isEmpty()) {
            handCrafted.models
        } else {
            handCrafted.models + extraModels
        }
        return handCrafted.copy(
            models = models,
            documentationUrl = handCrafted.documentationUrl ?: bundled.documentationUrl,
            catalogModelCount = maxOf(handCrafted.catalogModelCount, bundled.catalogModelCount),
        )
    }

    private fun sameBaseUrl(first: String, second: String): Boolean =
        normalizeBaseUrl(first) == normalizeBaseUrl(second)

    private fun normalizeBaseUrl(url: String): String = url.trim().trimEnd('/').lowercase()
}
