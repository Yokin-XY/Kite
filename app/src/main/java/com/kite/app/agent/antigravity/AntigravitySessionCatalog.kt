package com.kite.app.agent.antigravity

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentSessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant

fun interface AntigravitySessionFileResolver {
    fun resolve(agentPath: String): File?
}

fun interface AntigravitySessionPathMapper {
    fun toAgent(kitePath: String): String
}

internal data class AntigravityPersistedMessage(
    val id: String,
    val role: AgentMessageRole,
    val content: AgentContent.Text,
)

internal data class AntigravitySessionRecord(
    val summary: AgentSessionSummary,
    val messages: List<AntigravityPersistedMessage>,
)

/** 读取 Antigravity 官方 JSONL 会话文件；不把 Kite 内存投影伪装成权威历史。 */
internal class AntigravitySessionCatalog(
    private val fileResolver: AntigravitySessionFileResolver,
    private val pathMapper: AntigravitySessionPathMapper = AntigravitySessionPathMapper { it },
) {
    suspend fun list(cwd: String): List<AntigravitySessionRecord> = withContext(Dispatchers.IO) {
        val slug = projectSlug(cwd)
        val root = fileResolver.resolve("$ANTIGRAVITY_TMP_ROOT/$slug/chats")
            ?.takeIf(File::isDirectory)
            ?: return@withContext emptyList()
        root.listFiles().orEmpty().asSequence()
            .filter { file -> file.isFile && file.extension.equals("jsonl", ignoreCase = true) }
            .sortedByDescending(File::lastModified)
            .take(MAX_SESSION_FILES)
            .mapNotNull { file -> parse(file, cwd) }
            .sortedByDescending { record -> record.summary.updatedAt.orEmpty() }
            .toList()
    }

    suspend fun find(cwd: String, sessionId: String): AntigravitySessionRecord? =
        list(cwd).firstOrNull { record -> record.summary.id == sessionId }

    private fun projectSlug(cwd: String): String {
        val registry = fileResolver.resolve(PROJECT_REGISTRY_PATH)
            ?.takeIf(File::isFile)
            ?.takeIf { it.length() <= MAX_REGISTRY_BYTES }
            ?.let { file -> runCatching { JSONObject(file.readText()) }.getOrNull() }
            ?.optJSONObject("projects")
        val normalized = pathMapper.toAgent(cwd).replace('\\', '/').trimEnd('/')
        registry?.optString(normalized)?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        return normalized.substringAfterLast('/').slugify()
    }

    private fun parse(file: File, cwd: String): AntigravitySessionRecord? {
        if (file.length() > MAX_SESSION_FILE_BYTES) return null
        var sessionId: String? = null
        var kind: String? = null
        var startedAt: String? = null
        var updatedAt: String? = null
        var logicalLine = 0
        val messages = mutableListOf<AntigravityPersistedMessage>()
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.take(MAX_SESSION_LINES).forEachIndexed { physicalLine, line ->
                    if (line.isBlank()) return@forEachIndexed
                    val entry = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachIndexed
                    if (logicalLine++ == 0) {
                        sessionId = entry.optString("sessionId").trim().takeIf(String::isNotBlank)
                        kind = entry.optString("kind").trim().takeIf(String::isNotBlank)
                        startedAt = entry.instantText("startTime")
                        updatedAt = entry.instantText("lastUpdated") ?: startedAt
                        return@forEachIndexed
                    }
                    entry.optJSONObject("\$set")?.instantText("lastUpdated")?.let { updatedAt = it }
                    val role = when (entry.optString("type")) {
                        "user" -> AgentMessageRole.User
                        "assistant" -> AgentMessageRole.Assistant
                        else -> return@forEachIndexed
                    }
                    val content = buildList {
                        val parts = entry.optJSONArray("content") ?: return@buildList
                        for (partIndex in 0 until parts.length()) {
                            parts.optJSONObject(partIndex)
                                ?.optString("text")
                                ?.takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }.joinToString("")
                    if (content.isNotBlank()) {
                        messages += AntigravityPersistedMessage(
                            id = "agy-history-${file.nameWithoutExtension}-$physicalLine",
                            role = role,
                            content = AgentContent.Text(content),
                        )
                    }
                }
            }
        }.getOrElse { return null }
        val id = sessionId ?: return null
        if (kind == "subagent" || messages.none { it.role == AgentMessageRole.User }) return null
        val firstUser = messages.firstOrNull { it.role == AgentMessageRole.User }
            ?.content?.text?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_TITLE_CHARS)
        return AntigravitySessionRecord(
            summary = AgentSessionSummary(
                id = id,
                cwd = cwd,
                title = firstUser ?: id,
                updatedAt = updatedAt ?: startedAt,
            ),
            messages = messages,
        )
    }

    private fun JSONObject.instantText(key: String): String? = optString(key)
        .trim()
        .takeIf(String::isNotBlank)
        ?.let { raw -> runCatching { Instant.parse(raw).toString() }.getOrDefault(raw) }

    private fun String.slugify(): String = lowercase()
        .map { char -> if (char in 'a'..'z' || char in '0'..'9') char else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "project" }

    private companion object {
        const val PROJECT_REGISTRY_PATH = "/root/.gemini/projects.json"
        const val ANTIGRAVITY_TMP_ROOT = "/root/.gemini/tmp"
        const val MAX_SESSION_FILES = 2_000
        const val MAX_SESSION_LINES = 20_000
        const val MAX_SESSION_FILE_BYTES = 32L * 1024L * 1024L
        const val MAX_REGISTRY_BYTES = 2L * 1024L * 1024L
        const val MAX_TITLE_CHARS = 96
    }
}
