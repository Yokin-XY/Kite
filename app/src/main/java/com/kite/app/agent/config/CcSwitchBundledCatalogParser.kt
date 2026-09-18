package com.kite.app.agent.config

import org.json.JSONObject

/**
 * 解析构建期生成的 CC Switch 供应商目录（[CcSwitchCatalogBundle]）。
 *
 * 数据形状与上游 farion1231/cc-switch 的预设一一对应；Kite 只做投影：
 * - category / accessChannel / market 按 CC Switch 声明映射，不做国别猜测。
 * - capability（推理、上下文窗口、思考档位等）目前原样保留，供模型能力升级时消费。
 * - 官方登录与模板占位符 URL 的条目已在生成期剔除。
 */
internal object CcSwitchBundledCatalogParser {

    data class BundledModel(
        val id: String,
        val displayName: String,
        val capability: JSONObject?,
    )

    data class BundledPreset(
        val id: String,
        val displayName: String,
        val baseUrl: String,
        val models: List<BundledModel>,
        val vendorDisplayName: String,
        val category: AgentProviderCategory,
        val accessChannel: AgentProviderAccessChannel,
        val market: AgentProviderMarket,
        val documentationUrl: String?,
        /** CC Switch 声明的请求协议格式；写入时透传给 Adapter。 */
        val apiFormat: String?,
    )

    private val lock = Any()
    private var parsed: Map<String, List<BundledPreset>>? = null

    fun presetsFor(adapterId: String): List<BundledPreset> = parse()[adapterId].orEmpty()

    private fun parse(): Map<String, List<BundledPreset>> = parsed ?: synchronized(lock) {
        parsed ?: runCatching { parseJson(CcSwitchCatalogBundle.CATALOG_JSON) }
            .getOrElse { error ->
                // 目录损坏或当前运行时不支持 JSON 解析时降级为空目录；
                // 数据质量由 CcSwitchBundledCatalogTest（Robolectric）守护。
                emptyMap()
            }
            .also { parsed = it }
    }

    private fun parseJson(payload: String): Map<String, List<BundledPreset>> {
        val routes = JSONObject(payload).optJSONObject("routes") ?: return emptyMap()
        val result = linkedMapOf<String, MutableList<BundledPreset>>()
        for (adapterId in routes.keys()) {
            val entries = routes.optJSONArray(adapterId) ?: continue
            val list = mutableListOf<BundledPreset>()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val preset = parsePreset(entry) ?: continue
                list += preset
            }
            result[adapterId] = list
        }
        return result
    }

    private fun parsePreset(entry: JSONObject): BundledPreset? {
        val id = entry.optString("id").trim()
        val baseUrl = entry.optString("baseUrl").trim()
        if (id.isEmpty() || baseUrl.isEmpty()) return null
        val modelsArray = entry.optJSONArray("models")
        val models = mutableListOf<BundledModel>()
        if (modelsArray != null) {
            for (index in 0 until modelsArray.length()) {
                val model = modelsArray.optJSONObject(index) ?: continue
                val modelId = model.optString("id").trim()
                if (modelId.isEmpty()) continue
                models += BundledModel(
                    id = modelId,
                    displayName = model.optString("displayName").trim().ifEmpty { modelId },
                    capability = model.optJSONObject("capability"),
                )
            }
        }
        return BundledPreset(
            id = id,
            displayName = entry.optString("displayName").trim().ifEmpty { id },
            baseUrl = baseUrl,
            models = models,
            vendorDisplayName = entry.optString("vendorDisplayName").trim().ifEmpty { id },
            category = runCatching { AgentProviderCategory.valueOf(entry.optString("category")) }
                .getOrDefault(AgentProviderCategory.ThirdParty),
            accessChannel = runCatching { AgentProviderAccessChannel.valueOf(entry.optString("accessChannel")) }
                .getOrDefault(AgentProviderAccessChannel.Api),
            market = runCatching { AgentProviderMarket.valueOf(entry.optString("market")) }
                .getOrDefault(AgentProviderMarket.Unspecified),
            documentationUrl = entry.optString("documentationUrl").trim().takeIf(String::isNotEmpty),
            apiFormat = entry.optString("apiFormat").trim().takeIf(String::isNotEmpty),
        )
    }
}

internal fun CcSwitchBundledCatalogParser.BundledPreset.toAgentProviderPreset(): AgentProviderPreset = AgentProviderPreset(
    id = id,
    providerId = id,
    displayName = displayName,
    baseUrl = baseUrl,
    models = models.map { model -> AgentProviderModelSummary(model.id, model.displayName) },
    vendorId = id,
    vendorDisplayName = vendorDisplayName,
    category = category,
    accessChannel = accessChannel,
    market = market,
    source = AgentProviderPresetSource.Bundled,
    routeSource = AgentProviderPresetRouteSource.AdapterCatalog,
    documentationUrl = documentationUrl,
    catalogModelCount = models.size,
    apiFormat = apiFormat,
)
