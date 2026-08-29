package com.kite.app.agent.market

import com.kite.app.agent.config.AgentMcpImportParser
import com.kite.app.agent.config.AgentMcpSummary
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

internal enum class AgentExtensionMarketKind {
    Skill,
    Mcp,
}

internal sealed interface AgentExtensionInstallSpec {
    data class Skill(
        val ownerHandle: String,
        val slug: String,
        val version: String? = null,
    ) : AgentExtensionInstallSpec

    data class Mcp(val server: AgentMcpSummary) : AgentExtensionInstallSpec
}

internal data class AgentExtensionMarketItem(
    val id: String,
    val title: String,
    val description: String,
    val sourceLabel: String,
    val versionLabel: String?,
    val installSpec: AgentExtensionInstallSpec,
)

internal data class AgentExtensionMarketSnapshot(
    val kind: AgentExtensionMarketKind,
    val sourceLabel: String,
    val query: String,
    val items: List<AgentExtensionMarketItem>,
)

internal data class AgentMarketHttpPayload(
    val bytes: ByteArray,
    val contentType: String?,
)

internal fun interface AgentExtensionMarketRemote {
    fun get(url: String, maxBytes: Int): AgentMarketHttpPayload
}

/** 默认扩展目录只在用户进入市场并主动搜索时联网。 */
internal class AgentExtensionMarketRepository(
    private val remote: AgentExtensionMarketRemote = HttpAgentExtensionMarketRemote(),
) {
    fun search(kind: AgentExtensionMarketKind, rawQuery: String): AgentExtensionMarketSnapshot {
        val query = rawQuery.trim()
        require(query.isNotEmpty()) { "请输入搜索关键词" }
        require(query.length <= MAX_QUERY_LENGTH) { "搜索关键词过长" }
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return when (kind) {
            AgentExtensionMarketKind.Skill -> {
                val payload = remote.get(
                    "$CLAWHUB_BASE/api/v1/search?q=$encoded&nonSuspiciousOnly=true",
                    MAX_CATALOG_BYTES,
                )
                AgentExtensionMarketSnapshot(
                    kind = kind,
                    sourceLabel = "ClawHub",
                    query = query,
                    items = AgentExtensionMarketParser.parseClawHubSearch(payload.bytes.toString(Charsets.UTF_8)),
                )
            }
            AgentExtensionMarketKind.Mcp -> {
                val payload = remote.get(
                    "$MCP_REGISTRY_BASE/v0.1/servers?limit=$MAX_RESULTS&search=$encoded",
                    MAX_CATALOG_BYTES,
                )
                AgentExtensionMarketSnapshot(
                    kind = kind,
                    sourceLabel = "MCP 官方目录 · 预览",
                    query = query,
                    items = AgentExtensionMarketParser.parseMcpRegistrySearch(payload.bytes.toString(Charsets.UTF_8)),
                )
            }
        }
    }

    fun downloadSkill(spec: AgentExtensionInstallSpec.Skill): ByteArray {
        val owner = encodePathSegment(spec.ownerHandle)
        val slug = encodePathSegment(spec.slug)
        val versionQuery = spec.version?.let { "&version=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }
            ?: "&tag=latest"
        val payload = remote.get(
            "$CLAWHUB_BASE/api/v1/download?slug=$slug&ownerHandle=$owner$versionQuery",
            MAX_SKILL_ARCHIVE_BYTES,
        )
        require(payload.contentType?.substringBefore(';')?.trim()?.equals("application/zip", ignoreCase = true) == true) {
            "这个 Skill 当前需要 Git 仓库交接，暂不支持直接安装"
        }
        require(payload.bytes.size >= 4 && payload.bytes[0] == 'P'.code.toByte() && payload.bytes[1] == 'K'.code.toByte()) {
            "ClawHub 没有返回有效的 Skill ZIP"
        }
        return payload.bytes
    }

    private fun encodePathSegment(value: String): String {
        require(SAFE_PATH_SEGMENT.matches(value)) { "市场项目标识无效" }
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private companion object {
        const val CLAWHUB_BASE = "https://clawhub.ai"
        const val MCP_REGISTRY_BASE = "https://registry.modelcontextprotocol.io"
        const val MAX_RESULTS = 40
        const val MAX_QUERY_LENGTH = 120
        const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
        const val MAX_SKILL_ARCHIVE_BYTES = 10 * 1024 * 1024
        val SAFE_PATH_SEGMENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}

internal object AgentExtensionMarketParser {
    fun parseClawHubSearch(payload: String): List<AgentExtensionMarketItem> {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "ClawHub 目录超过大小限制" }
        val results = JSONObject(payload).optJSONArray("results")
        return buildList {
            for (index in 0 until minOf(results?.length() ?: 0, MAX_RESULTS)) {
                val row = results?.optJSONObject(index) ?: continue
                val install = row.optJSONObject("install") ?: continue
                if (install.optString("kind") != "clawhub") continue
                val reference = install.optString("reference").trim()
                val owner = row.optString("ownerHandle").trim().ifBlank { reference.substringBefore('/') }
                val slug = reference.substringAfter('/', "").trim()
                if (!SAFE_PATH_SEGMENT.matches(owner) || !SAFE_PATH_SEGMENT.matches(slug)) continue
                val nativeSkill = row.optJSONObject("native")?.optJSONObject("skill")
                val title = row.optString("displayName").trim().ifBlank { slug }
                val description = nativeSkill?.optString("summary")?.trim()
                    ?.ifBlank { null }
                    ?: row.optString("description").trim()
                add(AgentExtensionMarketItem(
                    id = "clawhub:$owner/$slug",
                    title = title.take(MAX_TITLE),
                    description = description.take(MAX_DESCRIPTION),
                    sourceLabel = "ClawHub · @$owner",
                    versionLabel = null,
                    installSpec = AgentExtensionInstallSpec.Skill(owner, slug),
                ))
            }
        }.distinctBy(AgentExtensionMarketItem::id)
    }

    fun parseMcpRegistrySearch(payload: String): List<AgentExtensionMarketItem> {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "MCP 目录超过大小限制" }
        val servers = JSONObject(payload).optJSONArray("servers")
        return buildList {
            for (index in 0 until minOf(servers?.length() ?: 0, MAX_RESULTS * 4)) {
                val row = servers?.optJSONObject(index) ?: continue
                val officialMeta = row.optJSONObject("_meta")
                    ?.optJSONObject("io.modelcontextprotocol.registry/official")
                if (officialMeta?.has("isLatest") == true && !officialMeta.optBoolean("isLatest")) continue
                if (officialMeta?.optString("status")?.lowercase() in setOf("deleted", "deprecated")) continue
                val source = row.optJSONObject("server") ?: continue
                val candidate = AgentMcpImportParser.parseServerJson(source) ?: continue
                val externalName = source.optString("name").trim()
                val version = candidate.version
                add(AgentExtensionMarketItem(
                    id = "mcp-registry:$externalName:${version.orEmpty()}",
                    title = candidate.title.take(MAX_TITLE),
                    description = candidate.description.take(MAX_DESCRIPTION),
                    sourceLabel = "MCP 官方目录",
                    versionLabel = version,
                    installSpec = AgentExtensionInstallSpec.Mcp(candidate.server),
                ))
            }
        }.distinctBy { item ->
            val server = (item.installSpec as AgentExtensionInstallSpec.Mcp).server
            server.id
        }
    }

    private val SAFE_PATH_SEGMENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private const val MAX_RESULTS = 40
    private const val MAX_TITLE = 160
    private const val MAX_DESCRIPTION = 1_000
    private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
}

internal class HttpAgentExtensionMarketRemote : AgentExtensionMarketRemote {
    override fun get(url: String, maxBytes: Int): AgentMarketHttpPayload = get(URL(url), maxBytes, 0)

    private fun get(url: URL, maxBytes: Int, redirects: Int): AgentMarketHttpPayload {
        require(url.protocol == "https" && !url.host.isNullOrBlank() && url.userInfo == null) { "市场地址无效" }
        require(redirects <= MAX_REDIRECTS) { "市场重定向次数过多" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, application/zip")
            setRequestProperty("User-Agent", "Kite-Agent-Market/1")
        }
        return try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val declared = connection.contentLengthLong
                    if (declared > maxBytes) throw IOException("市场响应超过大小限制")
                    AgentMarketHttpPayload(
                        bytes = connection.inputStream.use { input -> readBounded(input, maxBytes) },
                        contentType = connection.getHeaderField("Content-Type"),
                    )
                }
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308 -> {
                    val location = connection.getHeaderField("Location") ?: throw IOException("市场重定向缺少地址")
                    val target = URI(url.toString()).resolve(location).toURL()
                    get(target, maxBytes, redirects + 1)
                }
                else -> throw IOException("市场请求失败：HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) throw IOException("市场响应超过大小限制")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 4
    }
}
