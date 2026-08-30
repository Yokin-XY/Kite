package com.kite.app.agent.config

import android.content.Context
import com.kite.app.agent.config.native.ClaudeCodeAgentConfigAdapter
import com.kite.app.agent.config.native.CopilotAgentConfigAdapter
import com.kite.app.agent.config.native.CodexAgentConfigAdapter
import com.kite.app.agent.config.native.DeepSeekHarnessAgentConfigAdapter
import com.kite.app.agent.config.native.HermesAgentConfigAdapter
import com.kite.app.agent.config.native.KimiCodeAgentConfigAdapter
import com.kite.app.agent.config.native.MiMoCodeAgentConfigAdapter
import com.kite.app.agent.config.native.OpenClawAgentConfigAdapter
import com.kite.app.agent.config.native.PiCodingAgentConfigAdapter
import com.kite.app.agent.config.native.QwenCodeAgentConfigAdapter
import com.kite.app.agent.config.native.ReasonixAgentConfigAdapter
import com.kite.app.agent.config.native.ZCodeAgentConfigAdapter
import com.kite.app.agent.config.opencode.OpenCodeAgentConfigAdapter
import com.kite.app.agent.sdk.configuration.AgentProviderPresetRefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/** 供应商配对目录只在用户主动打开时刷新，普通页面始终读取同步缓存。 */
interface AgentProviderPresetRepository {
    fun cachedPresets(adapterId: String): List<AgentProviderPreset>
    suspend fun refresh(adapterId: String): AgentProviderPresetRefreshResult
}

internal object BundledAgentProviderPresetRepository : AgentProviderPresetRepository {
    override fun cachedPresets(adapterId: String): List<AgentProviderPreset> =
        AgentProviderPresetCatalog.presetsFor(adapterId)

    override suspend fun refresh(adapterId: String): AgentProviderPresetRefreshResult =
        AgentProviderPresetRefreshResult(
            presets = cachedPresets(adapterId),
            source = AgentProviderPresetSource.Bundled,
            refreshed = false,
        )
}

/**
 * models.dev 提供供应商、端点和模型事实；Kite 只补充稳定分类、厂商归一和 Adapter 协议过滤。
 * 网络失败时按“上次成功缓存 -> 随应用目录”降级，不清空当前可选项。
 */
internal class ModelsDevProviderPresetRepository(
    context: Context,
    private val remote: ModelsDevCatalogRemote = HttpModelsDevCatalogRemote(),
) : AgentProviderPresetRepository {
    private val cacheDirectory = File(context.applicationContext.filesDir, CACHE_DIRECTORY)
    private val payloadFile = File(cacheDirectory, CACHE_PAYLOAD_FILE)
    private val etagFile = File(cacheDirectory, CACHE_ETAG_FILE)
    private val memory = ConcurrentHashMap<String, List<AgentProviderPreset>>()
    private val refreshMutex = Mutex()

    override fun cachedPresets(adapterId: String): List<AgentProviderPreset> =
        memory[adapterId] ?: AgentProviderPresetCatalog.presetsFor(adapterId)

    override suspend fun refresh(adapterId: String): AgentProviderPresetRefreshResult =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val bundled = AgentProviderPresetCatalog.presetsFor(adapterId)
                val cachedPayload = readCachedPayload()
                val cachedEtag = readCachedEtag().takeIf { cachedPayload != null }
                val fetched = runCatching { remote.fetch(cachedEtag) }
                val livePayload = fetched.getOrNull().let { result ->
                    when (result) {
                        is ModelsDevFetchResult.Updated -> result.payload
                        ModelsDevFetchResult.NotModified -> cachedPayload
                        null -> null
                    }
                }
                if (livePayload != null) {
                    val resolution = runCatching {
                        ModelsDevProviderPresetParser.resolve(livePayload, adapterId)
                    }.getOrNull()
                    if (resolution != null && resolution.modelCatalogs.isNotEmpty()) {
                        val merged = mergePresets(
                            dynamic = resolution.compatiblePresets,
                            bundled = bundled,
                            modelCatalogs = resolution.modelCatalogs,
                        )
                        if (merged.isEmpty()) return@withLock AgentProviderPresetRefreshResult(
                            presets = bundled,
                            source = AgentProviderPresetSource.Bundled,
                            refreshed = false,
                            warning = "在线目录与当前 Agent 不兼容，已使用随应用目录",
                        )
                        val result = fetched.getOrNull()
                        if (result is ModelsDevFetchResult.Updated) {
                            persist(result.payload, result.etag)
                        }
                        memory[adapterId] = merged
                        return@withLock AgentProviderPresetRefreshResult(
                            presets = merged,
                            source = AgentProviderPresetSource.ModelsDev,
                            refreshed = true,
                        )
                    }
                }

                if (cachedPayload != null) {
                    val cached = runCatching {
                        ModelsDevProviderPresetParser.resolve(cachedPayload, adapterId)
                    }.getOrNull()
                    if (cached != null && cached.modelCatalogs.isNotEmpty()) {
                        val merged = mergePresets(
                            dynamic = cached.compatiblePresets.map {
                                it.copy(source = AgentProviderPresetSource.ModelsDevCache)
                            },
                            bundled = bundled,
                            modelCatalogs = cached.modelCatalogs.map {
                                it.copy(source = AgentProviderPresetSource.ModelsDevCache)
                            },
                        )
                        if (merged.isEmpty()) return@withLock AgentProviderPresetRefreshResult(
                            presets = bundled,
                            source = AgentProviderPresetSource.Bundled,
                            refreshed = false,
                            warning = "缓存目录与当前 Agent 不兼容，已使用随应用目录",
                        )
                        memory[adapterId] = merged
                        return@withLock AgentProviderPresetRefreshResult(
                            presets = merged,
                            source = AgentProviderPresetSource.ModelsDevCache,
                            refreshed = false,
                            warning = "models.dev 暂时不可用，已使用上次成功目录",
                        )
                    }
                }

                memory[adapterId] = bundled
                AgentProviderPresetRefreshResult(
                    presets = bundled,
                    source = AgentProviderPresetSource.Bundled,
                    refreshed = false,
                    warning = "在线供应商目录暂时不可用，已使用随应用目录",
                )
            }
        }

    private fun readCachedPayload(): String? = runCatching {
        if (!payloadFile.isFile || payloadFile.length() !in 1L..MAX_PAYLOAD_BYTES.toLong()) return@runCatching null
        payloadFile.readText(Charsets.UTF_8)
    }.getOrNull()

    private fun readCachedEtag(): String? = runCatching {
        etagFile.takeIf(File::isFile)?.readText(Charsets.UTF_8)?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun persist(payload: String, etag: String?) {
        runCatching {
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES)
            cacheDirectory.mkdirs()
            atomicWrite(payloadFile, payload)
            if (etag.isNullOrBlank()) {
                Files.deleteIfExists(etagFile.toPath())
            } else {
                atomicWrite(etagFile, etag.trim())
            }
        }
    }

    private fun atomicWrite(target: File, value: String) {
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        temporary.writeText(value, Charsets.UTF_8)
        runCatching {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun mergePresets(
        dynamic: List<AgentProviderPreset>,
        bundled: List<AgentProviderPreset>,
        modelCatalogs: List<AgentProviderPreset>,
    ): List<AgentProviderPreset> {
        val enrichedBundled = bundled.map { preset ->
            val catalog = modelCatalogs.firstOrNull { candidate ->
                candidate.catalogIdentity() == preset.catalogIdentity()
            } ?: return@map preset
            preset.copy(
                models = catalog.models,
                source = catalog.source,
                documentationUrl = catalog.documentationUrl ?: preset.documentationUrl,
                catalogModelCount = catalog.catalogModelCount,
            )
        }
        val adapterIdentities = bundled.mapTo(hashSetOf()) { it.catalogIdentity() }
        val dynamicWithoutAdapterRoute = dynamic.filterNot { it.catalogIdentity() in adapterIdentities }
        return (dynamicWithoutAdapterRoute + enrichedBundled).distinctBy(AgentProviderPreset::id).sortedWith(
            compareBy<AgentProviderPreset>(
                { it.category.ordinal },
                { it.vendorId.lowercase() },
                { it.accessChannel.ordinal },
                { it.displayName.lowercase() },
            )
        )
    }

    private fun AgentProviderPreset.catalogIdentity() = ProviderCatalogIdentity(
        vendorId = vendorId.lowercase(),
        market = market,
        accessChannel = accessChannel,
    )

    private data class ProviderCatalogIdentity(
        val vendorId: String,
        val market: AgentProviderMarket,
        val accessChannel: AgentProviderAccessChannel,
    )

    private companion object {
        const val CACHE_DIRECTORY = "agent-provider-catalog"
        const val CACHE_PAYLOAD_FILE = "models-dev.json"
        const val CACHE_ETAG_FILE = "models-dev.etag"
        const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    }
}

internal sealed interface ModelsDevFetchResult {
    data class Updated(val payload: String, val etag: String?) : ModelsDevFetchResult
    data object NotModified : ModelsDevFetchResult
}

internal fun interface ModelsDevCatalogRemote {
    fun fetch(etag: String?): ModelsDevFetchResult
}

internal class HttpModelsDevCatalogRemote : ModelsDevCatalogRemote {
    override fun fetch(etag: String?): ModelsDevFetchResult {
        val connection = (URL(MODELS_DEV_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Kite-Agent-Provider-Catalog/1")
            etag?.takeIf(String::isNotBlank)?.let { setRequestProperty("If-None-Match", it) }
        }
        return try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> ModelsDevFetchResult.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val declaredLength = connection.contentLengthLong
                    if (declaredLength > MAX_PAYLOAD_BYTES) {
                        throw IOException("models.dev response is too large: $declaredLength")
                    }
                    val bytes = connection.inputStream.use(::readLimited)
                    ModelsDevFetchResult.Updated(
                        payload = bytes.toString(Charsets.UTF_8),
                        etag = connection.getHeaderField("ETag"),
                    )
                }
                else -> throw IOException("models.dev HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_PAYLOAD_BYTES) throw IOException("models.dev response is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MODELS_DEV_URL = "https://models.dev/api.json"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    }
}

internal data class ModelsDevProviderPresetResolution(
    val compatiblePresets: List<AgentProviderPreset>,
    val modelCatalogs: List<AgentProviderPreset>,
)

internal object ModelsDevProviderPresetParser {
    private enum class ProtocolFamily { OpenAiCompatible, Anthropic }

    private data class ParsedModel(
        val summary: AgentProviderModelSummary,
        val releaseDate: String,
    )

    private data class ParsedProvider(
        val preset: AgentProviderPreset,
        val protocol: ProtocolFamily,
    )

    fun presetsFor(payload: String, adapterId: String): List<AgentProviderPreset> =
        resolve(payload, adapterId).compatiblePresets

    fun resolve(payload: String, adapterId: String): ModelsDevProviderPresetResolution {
        val root = JSONObject(payload)
        val providers = root.keys().asSequence().mapNotNull { key ->
            val provider = root.optJSONObject(key) ?: return@mapNotNull null
            val registryId = provider.optString("id", key).trim().ifBlank { key }
            if (!SAFE_ID.matches(registryId)) return@mapNotNull null
            val baseUrl = provider.optString("api").trim().trimEnd('/')
            if (!isSafeEndpoint(baseUrl)) return@mapNotNull null
            val protocol = protocolFamily(registryId, provider.optString("npm")) ?: return@mapNotNull null
            val modelsObject = provider.optJSONObject("models") ?: return@mapNotNull null
            val allModels = modelsObject.keys().asSequence().mapNotNull { modelId ->
                parseModel(modelId, modelsObject.optJSONObject(modelId))
            }.sortedWith(
                compareByDescending<ParsedModel> { it.releaseDate }
                    .thenBy { it.summary.displayName.lowercase() }
                    .thenBy { it.summary.id.lowercase() }
            ).toList()
            if (allModels.isEmpty()) return@mapNotNull null
            val displayName = provider.optString("name", registryId).trim().ifBlank { registryId }
            val canonicalVendorId = vendorId(registryId)
            ParsedProvider(
                preset = AgentProviderPreset(
                    id = registryId,
                    providerId = registryId,
                    displayName = displayName,
                    baseUrl = baseUrl,
                    models = allModels.take(MAX_PREFILLED_MODELS).map(ParsedModel::summary),
                    vendorId = canonicalVendorId,
                    vendorDisplayName = vendorDisplayName(canonicalVendorId, displayName),
                    category = category(registryId),
                    accessChannel = accessChannel(registryId, displayName),
                    market = market(registryId, displayName, baseUrl),
                    source = AgentProviderPresetSource.ModelsDev,
                    routeSource = AgentProviderPresetRouteSource.ModelsDev,
                    documentationUrl = provider.optString("doc").trim().takeIf(::isSafeEndpoint),
                    catalogModelCount = allModels.size,
                ),
                protocol = protocol,
            )
        }.toList()
        val ordering = compareBy<AgentProviderPreset>(
            { it.category.ordinal },
            { it.vendorId.lowercase() },
            { it.accessChannel.ordinal },
            { it.displayName.lowercase() },
        )
        return ModelsDevProviderPresetResolution(
            compatiblePresets = providers.asSequence()
                .filter { supports(adapterId, it.protocol) }
                .map(ParsedProvider::preset)
                .sortedWith(ordering)
                .toList(),
            modelCatalogs = providers.map(ParsedProvider::preset).sortedWith(ordering),
        )
    }

    private fun parseModel(modelId: String, value: JSONObject?): ParsedModel? {
        val id = modelId.trim()
        if (id.isBlank() || id.any { Character.isISOControl(it.code) }) return null
        val status = value?.optString("status")?.lowercase().orEmpty()
        if (status == "deprecated") return null
        val outputModalities = value?.optJSONObject("modalities")?.optJSONArray("output")
        if (outputModalities != null && outputModalities.length() > 0) {
            val modalities = (0 until outputModalities.length())
                .map { outputModalities.optString(it).lowercase() }
                .filter(String::isNotBlank)
            if ("text" !in modalities) return null
        }
        val displayName = value?.optString("name", id)?.trim().orEmpty().ifBlank { id }
        val searchable = "$id $displayName".lowercase()
        if (NON_CHAT_MODEL_MARKERS.any(searchable::contains)) return null
        return ParsedModel(
            summary = AgentProviderModelSummary(id, displayName),
            releaseDate = value?.optString("release_date")?.trim().orEmpty(),
        )
    }

    private fun protocolFamily(providerId: String, npm: String): ProtocolFamily? = when {
        npm == "@ai-sdk/openai-compatible" -> ProtocolFamily.OpenAiCompatible
        providerId == "openrouter" && npm == "@openrouter/ai-sdk-provider" -> ProtocolFamily.OpenAiCompatible
        npm == "@ai-sdk/anthropic" -> ProtocolFamily.Anthropic
        else -> null
    }

    private fun supports(adapterId: String, protocol: ProtocolFamily): Boolean = when (protocol) {
        ProtocolFamily.OpenAiCompatible -> adapterId in OPEN_AI_COMPATIBLE_ADAPTERS
        ProtocolFamily.Anthropic -> adapterId == ClaudeCodeAgentConfigAdapter.ADAPTER_ID
    }

    private fun isSafeEndpoint(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    private fun vendorId(providerId: String): String {
        VENDOR_ALIASES[providerId]?.let { return it }
        return providerId
            .removeSuffix("-coding-plan")
            .removeSuffix("-token-plan")
            .removeSuffix("-global")
            .removeSuffix("-cn")
    }

    private fun category(providerId: String): AgentProviderCategory {
        val vendor = vendorId(providerId)
        return when {
            vendor in CHINA_OFFICIAL_VENDORS -> AgentProviderCategory.ChinaOfficial
            providerId in CLOUD_PROVIDERS || CLOUD_MARKERS.any(providerId::contains) ->
                AgentProviderCategory.CloudProvider
            vendor in AGGREGATORS -> AgentProviderCategory.Aggregator
            vendor in OFFICIAL_VENDORS -> AgentProviderCategory.Official
            else -> AgentProviderCategory.ThirdParty
        }
    }

    private fun accessChannel(providerId: String, displayName: String): AgentProviderAccessChannel {
        val value = "$providerId $displayName".lowercase()
        return when {
            "token-plan" in value || "token plan" in value -> AgentProviderAccessChannel.TokenPlan
            "coding-plan" in value || "coding plan" in value -> AgentProviderAccessChannel.CodingPlan
            "oauth" in value -> AgentProviderAccessChannel.OfficialLogin
            else -> AgentProviderAccessChannel.Api
        }
    }

    private fun market(
        providerId: String,
        displayName: String,
        baseUrl: String,
    ): AgentProviderMarket {
        EXPLICIT_MARKETS[providerId]?.let { return it }
        val value = "$providerId $displayName $baseUrl".lowercase()
        return when {
            providerId.endsWith("-cn") || "(china)" in value || "（中国）" in value ||
                ".cn/" in value || "-cn." in value -> AgentProviderMarket.China
            else -> AgentProviderMarket.Unspecified
        }
    }

    private fun vendorDisplayName(vendorId: String, providerDisplayName: String): String =
        VENDOR_DISPLAY_NAMES[vendorId] ?: providerDisplayName
            .replace(PARENTHETICAL_MARKET, "")
            .replace(CHANNEL_MARKERS, "")
            .trim()
            .ifBlank { providerDisplayName }

    private val OPEN_AI_COMPATIBLE_ADAPTERS = setOf(
        CodexAgentConfigAdapter.ADAPTER_ID,
        OpenCodeAgentConfigAdapter.ADAPTER_ID,
        OpenClawAgentConfigAdapter.ADAPTER_ID,
        HermesAgentConfigAdapter.ADAPTER_ID,
        MiMoCodeAgentConfigAdapter.ADAPTER_ID,
        KimiCodeAgentConfigAdapter.ADAPTER_ID,
        PiCodingAgentConfigAdapter.ADAPTER_ID,
        QwenCodeAgentConfigAdapter.ADAPTER_ID,
        ReasonixAgentConfigAdapter.ADAPTER_ID,
        CopilotAgentConfigAdapter.ADAPTER_ID,
        DeepSeekHarnessAgentConfigAdapter.ADAPTER_ID,
        ZCodeAgentConfigAdapter.ADAPTER_ID,
    )
    private val VENDOR_ALIASES = mapOf(
        "zhipuai" to "zhipu",
        "zhipuai-coding-plan" to "zhipu",
        "zai" to "zhipu",
        "zai-coding-plan" to "zhipu",
        "moonshotai" to "moonshotai",
        "moonshotai-cn" to "moonshotai",
        "minimax-cn-coding-plan" to "minimax",
        "minimax-coding-plan" to "minimax",
        "siliconflow-cn" to "siliconflow",
    )
    private val VENDOR_DISPLAY_NAMES = mapOf(
        "alibaba" to "Alibaba",
        "baidu" to "Baidu",
        "deepseek" to "DeepSeek",
        "longcat" to "LongCat",
        "minimax" to "MiniMax",
        "moonshotai" to "Moonshot AI",
        "siliconflow" to "SiliconFlow",
        "stepfun" to "StepFun",
        "tencent" to "Tencent",
        "volcengine" to "Volcengine",
        "xiaomi" to "Xiaomi",
        "zhipu" to "Zhipu AI",
    )
    private val EXPLICIT_MARKETS = mapOf(
        "minimax" to AgentProviderMarket.Global,
        "minimax-coding-plan" to AgentProviderMarket.Global,
        "minimax-cn" to AgentProviderMarket.China,
        "minimax-cn-coding-plan" to AgentProviderMarket.China,
        "moonshotai" to AgentProviderMarket.Global,
        "moonshotai-cn" to AgentProviderMarket.China,
        "siliconflow" to AgentProviderMarket.Global,
        "siliconflow-cn" to AgentProviderMarket.China,
        "zai" to AgentProviderMarket.Global,
        "zai-coding-plan" to AgentProviderMarket.Global,
        "zhipuai" to AgentProviderMarket.China,
        "zhipuai-coding-plan" to AgentProviderMarket.China,
    )
    private val CHINA_OFFICIAL_VENDORS = setOf(
        "alibaba",
        "baidu",
        "deepseek",
        "longcat",
        "minimax",
        "moonshotai",
        "stepfun",
        "tencent",
        "volcengine",
        "xiaomi",
        "zhipu",
    )
    private val CLOUD_PROVIDERS = setOf("amazon-bedrock", "google-vertex", "google-vertex-anthropic")
    private val CLOUD_MARKERS = listOf("azure", "bedrock", "cloudflare", "vertex")
    private val AGGREGATORS = setOf(
        "cerebras",
        "fireworks-ai",
        "groq",
        "huggingface",
        "modelscope",
        "nvidia",
        "novita-ai",
        "opencode",
        "openrouter",
        "replicate",
        "siliconflow",
        "togetherai",
    )
    private val OFFICIAL_VENDORS = setOf("anthropic", "cohere", "google", "mistral", "openai", "xai")
    private val NON_CHAT_MODEL_MARKERS = listOf(
        "embedding",
        "moderation",
        "realtime",
        "transcribe",
        "speech-to-text",
        "text-to-speech",
        " tts",
        "image-generation",
        "video-generation",
    )
    private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val PARENTHETICAL_MARKET = Regex("\\s*[（(](?:China|中国|[^)）]*\\.(?:com|io|cn))[）)]\\s*", RegexOption.IGNORE_CASE)
    private val CHANNEL_MARKERS = Regex("\\s+(?:Coding Plan|Token Plan|OAuth)\\s*", RegexOption.IGNORE_CASE)
    private const val MAX_PREFILLED_MODELS = 60
}
