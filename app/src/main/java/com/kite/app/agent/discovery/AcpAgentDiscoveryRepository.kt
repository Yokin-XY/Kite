package com.kite.app.agent.discovery

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class AcpAgentCatalogSource {
    Bundled,
    Cache,
    Live,
}

internal enum class AcpAgentDistributionKind {
    Npx,
    Uvx,
    LinuxArm64Binary,
}

/** 外部目录只提供候选元数据；它不能绕过 Kite 资源签名或兼容验证直接执行。 */
internal enum class AcpAgentCandidateTrust {
    MetadataOnly,
}

internal data class AcpAgentDistribution(
    val kind: AcpAgentDistributionKind,
    val packageSpec: String? = null,
    val archiveUrl: String? = null,
    val command: String? = null,
    val arguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val sha256: String? = null,
) {
    val artifactVerified: Boolean
        get() = kind != AcpAgentDistributionKind.LinuxArm64Binary || sha256 != null
}

internal data class AcpAgentCatalogEntry(
    val id: String,
    val displayName: String,
    val version: String,
    val description: String,
    val repositoryUrl: String? = null,
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
    val license: String? = null,
    val distributions: List<AcpAgentDistribution>,
    val trust: AcpAgentCandidateTrust = AcpAgentCandidateTrust.MetadataOnly,
)

internal data class AcpAgentCatalogSnapshot(
    val version: String = "",
    val entries: List<AcpAgentCatalogEntry> = emptyList(),
    val source: AcpAgentCatalogSource = AcpAgentCatalogSource.Bundled,
    val refreshedAtMs: Long = 0L,
    val warning: String? = null,
)

internal sealed interface AcpAgentCatalogFetchResult {
    data class Updated(val payload: String, val etag: String?) : AcpAgentCatalogFetchResult
    data object NotModified : AcpAgentCatalogFetchResult
}

internal fun interface AcpAgentCatalogRemote {
    fun fetch(etag: String?): AcpAgentCatalogFetchResult
}

/**
 * ACP 官方 Registry 的按需目录。
 *
 * 读取顺序固定为“本次在线结果 -> 上次成功缓存 -> 随包兜底”。调用方必须在用户明确刷新
 * Agent 目录时调用 [refresh]；普通页面绑定只读取 [cachedSnapshot]。
 */
internal class AcpAgentDiscoveryRepository(
    context: Context,
    private val remote: AcpAgentCatalogRemote = HttpAcpAgentCatalogRemote(),
) {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.filesDir, CACHE_DIRECTORY)
    private val payloadFile = File(cacheDirectory, CACHE_PAYLOAD_FILE)
    private val etagFile = File(cacheDirectory, CACHE_ETAG_FILE)
    private val refreshMutex = Mutex()
    private val memoryLock = Any()

    @Volatile
    private var memory: AcpAgentCatalogSnapshot? = null

    fun cachedSnapshot(): AcpAgentCatalogSnapshot = memory ?: synchronized(memoryLock) {
        memory ?: loadCachedPayload()?.let { payload ->
            runCatching { parseSnapshot(payload, AcpAgentCatalogSource.Cache) }.getOrNull()
        }?.also { memory = it } ?: bundledSnapshot().also { memory = it }
    }

    suspend fun refresh(): AcpAgentCatalogSnapshot = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val bundled = bundledSnapshot()
            val cachedPayload = loadCachedPayload()
            val cached = cachedPayload?.let { payload ->
                runCatching { parseSnapshot(payload, AcpAgentCatalogSource.Cache) }.getOrNull()
            }
            val etag = loadCachedEtag().takeIf { cached != null }
            val fetched = runCatching { remote.fetch(etag) }
            val now = System.currentTimeMillis()
            val next = when (val result = fetched.getOrNull()) {
                is AcpAgentCatalogFetchResult.Updated -> runCatching {
                    val parsed = parseSnapshot(result.payload, AcpAgentCatalogSource.Live)
                        .copy(refreshedAtMs = now)
                    persist(result.payload, result.etag)
                    parsed
                }.getOrElse { error ->
                    fallback(cached, bundled, error.message ?: "目录内容无效")
                }
                AcpAgentCatalogFetchResult.NotModified -> (cached ?: bundled).copy(
                    source = if (cached != null) AcpAgentCatalogSource.Live else AcpAgentCatalogSource.Bundled,
                    refreshedAtMs = now,
                    warning = null,
                )
                null -> fallback(
                    cached,
                    bundled,
                    fetched.exceptionOrNull()?.message ?: "网络不可用",
                )
            }
            synchronized(memoryLock) { memory = next }
            next
        }
    }

    private fun fallback(
        cached: AcpAgentCatalogSnapshot?,
        bundled: AcpAgentCatalogSnapshot,
        reason: String,
    ): AcpAgentCatalogSnapshot = (cached ?: bundled).copy(
        warning = if (cached != null) {
            "ACP Registry 暂时不可用，已使用上次成功目录：$reason"
        } else {
            "ACP Registry 暂时不可用，已使用随包目录：$reason"
        },
    )

    private fun bundledSnapshot(): AcpAgentCatalogSnapshot {
        val payload = appContext.assets.open(BUNDLED_ASSET_PATH).bufferedReader().use { it.readText() }
        return parseSnapshot(payload, AcpAgentCatalogSource.Bundled)
    }

    private fun parseSnapshot(payload: String, source: AcpAgentCatalogSource): AcpAgentCatalogSnapshot {
        val parsed = AcpAgentCatalogParser.parse(payload)
        require(parsed.entries.isNotEmpty()) { "ACP Registry 没有可用的 Android 候选" }
        return parsed.copy(source = source)
    }

    private fun loadCachedPayload(): String? = runCatching {
        payloadFile.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun loadCachedEtag(): String? = runCatching {
        etagFile.takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun persist(payload: String, etag: String?) {
        cacheDirectory.mkdirs()
        writeAtomically(payloadFile, payload)
        if (etag.isNullOrBlank()) {
            etagFile.delete()
        } else {
            writeAtomically(etagFile, etag)
        }
    }

    private fun writeAtomically(target: File, text: String) {
        val atomicFile = AtomicFile(target)
        val output = atomicFile.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw IOException("目录缓存发布失败：${target.name}", error)
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "agent-discovery/acp-registry"
        const val CACHE_PAYLOAD_FILE = "registry.json"
        const val CACHE_ETAG_FILE = "registry.etag"
        const val BUNDLED_ASSET_PATH = "agent-catalog/acp-registry.json"
    }
}

internal object AcpAgentCatalogParser {
    fun parse(payload: String): AcpAgentCatalogSnapshot {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) { "ACP Registry 超出大小限制" }
        val root = JSONObject(payload)
        val version = root.optString("version").trim().takeIf(VERSION::matches)
            ?: throw IllegalArgumentException("ACP Registry 版本无效")
        val agents = root.optJSONArray("agents") ?: JSONArray()
        val entries = buildList {
            for (index in 0 until minOf(agents.length(), MAX_AGENTS)) {
                parseEntry(agents.optJSONObject(index))?.let(::add)
            }
        }.distinctBy(AcpAgentCatalogEntry::id)
            .sortedWith(compareBy<AcpAgentCatalogEntry> { it.displayName.lowercase() }.thenBy { it.id })
        return AcpAgentCatalogSnapshot(version = version, entries = entries)
    }

    private fun parseEntry(json: JSONObject?): AcpAgentCatalogEntry? {
        json ?: return null
        val id = json.optString("id").trim().takeIf(SAFE_ID::matches) ?: return null
        val name = json.optString("name").trim().takeIf { it.isNotBlank() && it.length <= MAX_TEXT } ?: return null
        val version = json.optString("version").trim().takeIf(VERSION::matches) ?: return null
        val distributions = parseDistributions(json.optJSONObject("distribution"))
        if (distributions.isEmpty()) return null
        return AcpAgentCatalogEntry(
            id = id,
            displayName = name,
            version = version,
            description = json.optString("description").trim().take(MAX_DESCRIPTION),
            repositoryUrl = safeHttpsUrl(json.optString("repository")),
            websiteUrl = safeHttpsUrl(json.optString("website")),
            iconUrl = safeHttpsUrl(json.optString("icon")),
            license = json.optString("license").trim().take(MAX_TEXT).ifBlank { null },
            distributions = distributions,
        )
    }

    private fun parseDistributions(json: JSONObject?): List<AcpAgentDistribution> = buildList {
        json ?: return@buildList
        parsePackageDistribution(json.optJSONObject("npx"), AcpAgentDistributionKind.Npx)?.let(::add)
        parsePackageDistribution(json.optJSONObject("uvx"), AcpAgentDistributionKind.Uvx)?.let(::add)
        parseBinaryDistribution(json.optJSONObject("binary")?.optJSONObject("linux-aarch64"))?.let(::add)
    }

    private fun parsePackageDistribution(
        json: JSONObject?,
        kind: AcpAgentDistributionKind,
    ): AcpAgentDistribution? {
        json ?: return null
        val packageSpec = json.optString("package").trim().takeIf(PACKAGE_SPEC::matches) ?: return null
        return AcpAgentDistribution(
            kind = kind,
            packageSpec = packageSpec,
            arguments = parseArguments(json.optJSONArray("args")),
            environment = parseEnvironment(json.optJSONObject("env")),
        )
    }

    private fun parseBinaryDistribution(json: JSONObject?): AcpAgentDistribution? {
        json ?: return null
        val archive = safeHttpsUrl(json.optString("archive")) ?: return null
        val command = json.optString("cmd").trim().takeIf(::safeRelativeCommand) ?: return null
        val sha256 = json.optString("sha256").trim().lowercase().takeIf(SHA256::matches)
        return AcpAgentDistribution(
            kind = AcpAgentDistributionKind.LinuxArm64Binary,
            archiveUrl = archive,
            command = command,
            arguments = parseArguments(json.optJSONArray("args")),
            environment = parseEnvironment(json.optJSONObject("env")),
            sha256 = sha256,
        )
    }

    private fun parseArguments(array: JSONArray?): List<String> = buildList {
        array ?: return@buildList
        for (index in 0 until minOf(array.length(), MAX_ARGUMENTS)) {
            val value = array.optString(index).takeIf { it.isNotBlank() && it.length <= MAX_ARGUMENT_LENGTH }
                ?: continue
            add(value)
        }
    }

    private fun parseEnvironment(json: JSONObject?): Map<String, String> = buildMap {
        json ?: return@buildMap
        json.keys().asSequence().take(MAX_ENVIRONMENT).forEach { key ->
            if (!ENVIRONMENT_KEY.matches(key)) return@forEach
            val value = json.optString(key).takeIf { it.length <= MAX_ENVIRONMENT_VALUE } ?: return@forEach
            put(key, value)
        }
    }

    private fun safeHttpsUrl(raw: String): String? {
        val value = raw.trim().takeIf { it.length <= MAX_URL } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        return value.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
        }
    }

    private fun safeRelativeCommand(command: String): Boolean {
        if (command.isBlank() || command.length > MAX_COMMAND || '\u0000' in command) return false
        val normalized = command.replace('\\', '/')
        if (normalized.startsWith('/') || normalized.split('/').any { it == ".." }) return false
        return COMMAND.matches(normalized)
    }

    private val SAFE_ID = Regex("[a-z][a-z0-9-]{0,63}")
    private val VERSION = Regex("[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}")
    private val PACKAGE_SPEC = Regex("(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*@[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val ENVIRONMENT_KEY = Regex("[A-Z_][A-Z0-9_]{0,63}")
    private val COMMAND = Regex("(?:\\./)?[A-Za-z0-9._/-]+")
    private const val MAX_AGENTS = 512
    private const val MAX_ARGUMENTS = 32
    private const val MAX_ENVIRONMENT = 24
    private const val MAX_ARGUMENT_LENGTH = 256
    private const val MAX_ENVIRONMENT_VALUE = 1024
    private const val MAX_TEXT = 160
    private const val MAX_DESCRIPTION = 1_000
    private const val MAX_URL = 2_048
    private const val MAX_COMMAND = 512
    private const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
}

internal class HttpAcpAgentCatalogRemote : AcpAgentCatalogRemote {
    override fun fetch(etag: String?): AcpAgentCatalogFetchResult {
        val connection = (URL(REGISTRY_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Kite-ACP-Agent-Catalog/1")
            etag?.let { setRequestProperty("If-None-Match", it) }
        }
        return try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> AcpAgentCatalogFetchResult.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val declaredLength = connection.contentLengthLong
                    if (declaredLength > MAX_PAYLOAD_BYTES) {
                        throw IOException("ACP Registry response is too large: $declaredLength")
                    }
                    val bytes = connection.inputStream.use(::readBounded)
                    AcpAgentCatalogFetchResult.Updated(
                        payload = bytes.toString(Charsets.UTF_8),
                        etag = connection.getHeaderField("ETag")?.trim()?.takeIf(String::isNotBlank),
                    )
                }
                else -> throw IOException("ACP Registry HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_PAYLOAD_BYTES) throw IOException("ACP Registry response is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val REGISTRY_URL = "https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
    }
}
