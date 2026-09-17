package com.kite.app.agent.pi

import com.kite.app.agent.contract.AgentSessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 把 Pi RPC 暴露的容器路径解析到当前运行 View 中的真实文件。 */
fun interface PiRpcSessionFileResolver {
    fun resolve(agentPath: String): File?
}

/** Pi 可能在 Host Node 下看到物理工作区路径，SDK 始终对外保留 `/workspace`。 */
fun interface PiRpcSessionPathMapper {
    fun fromAgent(agentPath: String): String
}

internal data class PiRpcSessionRecord(
    val summary: AgentSessionSummary,
    val agentPath: String,
)

/**
 * Pi RPC 支持 `switch_session`，但没有列出会话的 RPC。这里读取 Pi 官方 JSONL 会话目录，
 * 只负责会话索引；历史内容仍在切换后通过官方 `get_messages` 获取。
 */
internal class PiRpcSessionCatalog(
    private val fileResolver: PiRpcSessionFileResolver,
    private val pathMapper: PiRpcSessionPathMapper,
) {
    suspend fun list(currentSessionFile: String?, cwd: String?): List<PiRpcSessionRecord> =
        withContext(Dispatchers.IO) {
            val sessionRootPath = currentSessionFile.sessionRootPath() ?: return@withContext emptyList()
            val sessionRoot = fileResolver.resolve(sessionRootPath)
                ?.takeIf(File::isDirectory)
                ?: return@withContext emptyList()
            val expectedCwd = cwd?.trim()?.takeIf(String::isNotBlank)
            sessionRoot.walkTopDown()
                .maxDepth(2)
                .filter { file -> file.isFile && file.extension.equals("jsonl", ignoreCase = true) }
                .take(MAX_SESSION_FILES)
                .mapNotNull { file ->
                    val relative = runCatching {
                        file.relativeTo(sessionRoot).invariantSeparatorsPath
                    }.getOrNull() ?: return@mapNotNull null
                    parseSession(
                        file = file,
                        agentPath = "${sessionRootPath.trimEnd('/')}/$relative",
                    )
                }
                .filter { record ->
                    expectedCwd == null || samePath(record.summary.cwd, expectedCwd)
                }
                .sortedByDescending { record -> record.summary.updatedAt.orEmpty() }
                .toList()
        }

    suspend fun find(currentSessionFile: String?, sessionId: String): PiRpcSessionRecord? =
        list(currentSessionFile, cwd = null).firstOrNull { record -> record.summary.id == sessionId }

    private fun parseSession(file: File, agentPath: String): PiRpcSessionRecord? {
        if (file.length() > MAX_SESSION_FILE_BYTES) return null
        var header: JSONObject? = null
        var title: String? = null
        var firstUserText: String? = null
        var updatedAt: String? = null
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.take(MAX_SESSION_LINES).forEach { line ->
                    val entry = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                    if (header == null && entry.optString("type") == "session") header = entry
                    entry.optString("timestamp").trim().takeIf(String::isNotBlank)?.let { timestamp ->
                        if (updatedAt == null || timestamp > checkNotNull(updatedAt)) updatedAt = timestamp
                    }
                    when (entry.optString("type")) {
                        "session_info" -> title = entry.optString("name").trim().takeIf(String::isNotBlank)
                        "message" -> if (firstUserText == null) {
                            val message = entry.optJSONObject("message")
                            if (message?.optString("role") == "user") {
                                firstUserText = message.opt("content").visibleText()
                            }
                        }
                    }
                }
            }
        }.getOrElse { return null }
        val metadata = header ?: return null
        val id = metadata.optString("id").trim().takeIf(String::isNotBlank) ?: return null
        val nativeCwd = metadata.optString("cwd").trim().takeIf(String::isNotBlank) ?: return null
        val summary = AgentSessionSummary(
            id = id,
            cwd = pathMapper.fromAgent(nativeCwd),
            title = title ?: firstUserText?.singleLine()?.take(MAX_TITLE_CHARS),
            updatedAt = updatedAt ?: metadata.optString("timestamp").trim().takeIf(String::isNotBlank),
        )
        return PiRpcSessionRecord(summary, agentPath)
    }

    private fun Any?.visibleText(): String? = when (this) {
        is String -> trim().takeIf(String::isNotBlank)
        is JSONArray -> buildList {
            for (index in 0 until length()) {
                optJSONObject(index)
                    ?.takeIf { block -> block.optString("type") == "text" }
                    ?.optString("text")
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }.joinToString(" ").takeIf(String::isNotBlank)
        else -> null
    }

    private fun String.singleLine(): String = replace(Regex("\\s+"), " ").trim()

    private fun samePath(left: String, right: String): Boolean =
        left.replace('\\', '/').trimEnd('/') == right.replace('\\', '/').trimEnd('/')

    private fun String?.sessionRootPath(): String? {
        val path = this?.trim()?.takeIf(String::isNotBlank) ?: return null
        val marker = "/sessions/"
        val markerIndex = path.indexOf(marker)
        if (markerIndex >= 0) return path.substring(0, markerIndex) + "/sessions"
        return path.substringBeforeLast('/', missingDelimiterValue = "").takeIf(String::isNotBlank)
    }

    private companion object {
        const val MAX_SESSION_FILES = 2_000
        const val MAX_SESSION_LINES = 20_000
        const val MAX_SESSION_FILE_BYTES = 32L * 1024L * 1024L
        const val MAX_TITLE_CHARS = 96
    }
}
