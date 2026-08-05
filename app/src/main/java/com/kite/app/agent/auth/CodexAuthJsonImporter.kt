package com.kite.app.agent.auth

import android.content.Context
import android.net.Uri
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

internal enum class CodexAuthImportError {
    INVALID_JSON,
    MISSING_ID_TOKEN,
    MISSING_ACCESS_TOKEN,
    MISSING_REFRESH_TOKEN,
    INVALID_ID_TOKEN,
    CONTAINER_NOT_READY,
    WRITE_FAILED,
}

internal data class CodexAuthImportResult(
    val success: Boolean,
    val error: CodexAuthImportError? = null,
    val authBackupCreated: Boolean = false,
    val configBackupCreated: Boolean = false,
    val accountLabel: String? = null,
)

/**
 * 把官方 auth.json、CPA 单账号导出和 Sub2API accounts 导出统一成 Codex 原生凭据。
 *
 * 认证文件只会写入当前容器的可见/可写 PRoot View，避免直接改 Base rootfs 后活跃 View
 * 仍然读取旧内容。导入前会备份现有文件，正文不会写入日志、异常消息或结果对象。
 */
internal object CodexAuthJsonImporter {
    private const val MAX_PAYLOAD_BYTES = 1024 * 1024
    private const val MAX_JSON_NODES = 512
    private const val AUTH_PATH = "/root/.codex/auth.json"
    private const val CONFIG_PATH = "/root/.codex/config.toml"

    fun importFromUri(context: Context, uri: Uri): CodexAuthImportResult {
        val payload = runCatching { readLimited(context, uri) }.getOrNull()
            ?: return CodexAuthImportResult(false, CodexAuthImportError.INVALID_JSON)
        return importIntoDefaultContainer(context, payload)
    }

    fun importIntoDefaultContainer(
        context: Context,
        payload: ByteArray,
        now: Instant = Instant.now(),
    ): CodexAuthImportResult {
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD_BYTES) {
            return CodexAuthImportResult(false, CodexAuthImportError.INVALID_JSON)
        }
        val appContext = context.applicationContext
        val container = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
            ?: return CodexAuthImportResult(false, CodexAuthImportError.CONTAINER_NOT_READY)
        val projection = ContainerAgentConfigProjection { container }
        return runCatching {
            val authProjection = projection.resolve(AUTH_PATH)
                ?: return@runCatching CodexAuthImportResult(false, CodexAuthImportError.CONTAINER_NOT_READY)
            val configProjection = projection.resolve(CONFIG_PATH)
            importIntoFiles(
                authFile = authProjection.writeFile,
                authBackupSource = authProjection.readFile,
                configFile = configProjection?.writeFile,
                configBackupSource = configProjection?.readFile,
                payload = payload.toString(StandardCharsets.UTF_8),
                now = now,
            )
        }.getOrElse { CodexAuthImportResult(false, CodexAuthImportError.WRITE_FAILED) }
    }

    internal fun importIntoRootfs(
        rootfsDir: File,
        payload: String,
        now: Instant = Instant.now(),
    ): CodexAuthImportResult {
        val codexDir = File(rootfsDir, "root/.codex")
        return importIntoFiles(
            authFile = File(codexDir, "auth.json"),
            authBackupSource = File(codexDir, "auth.json"),
            configFile = File(codexDir, "config.toml"),
            configBackupSource = File(codexDir, "config.toml"),
            payload = payload,
            now = now,
        )
    }

    internal fun parseCredentials(payload: String): ImportedCodexCredentials? {
        val roots = parseRoots(payload) ?: return null
        val objects = candidateObjects(roots)
        for (candidate in objects) {
            val tokenObject = candidate.optJSONObject("tokens") ?: candidate
            val idToken = tokenObject.firstString("id_token", "idToken") ?: continue
            val accessToken = tokenObject.firstString("access_token", "accessToken") ?: continue
            val refreshToken = tokenObject.firstString("refresh_token", "refreshToken") ?: continue
            val idClaims = decodeJwtClaims(idToken) ?: return null
            val accountId = tokenObject.firstString(
                "account_id",
                "accountId",
                "chatgpt_account_id",
                "chatgptAccountId",
            ) ?: candidate.firstString(
                "account_id",
                "accountId",
                "chatgpt_account_id",
                "chatgptAccountId",
            ) ?: accountIdFromClaims(idClaims)
            val lastRefresh = candidate.firstString("last_refresh", "lastRefresh")
                ?.takeIf(::isIsoInstant)
                ?: roots.firstNotNullOfOrNull { it.firstString("last_refresh", "lastRefresh")?.takeIf(::isIsoInstant) }
            val email = candidate.firstString("email")
                ?: roots.firstNotNullOfOrNull { it.firstString("email") }
                ?: idClaims.firstString("email")
            return ImportedCodexCredentials(
                idToken = idToken,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accountId = accountId,
                lastRefresh = lastRefresh,
                email = email,
            )
        }
        return null
    }

    internal fun parseError(payload: String): CodexAuthImportError {
        val roots = parseRoots(payload) ?: return CodexAuthImportError.INVALID_JSON
        val objects = candidateObjects(roots)
        val tokenObject = objects.firstOrNull { candidate ->
            val tokens = candidate.optJSONObject("tokens") ?: candidate
            tokens.firstString("id_token", "idToken") != null
        }?.let { it.optJSONObject("tokens") ?: it }
            ?: return CodexAuthImportError.MISSING_ID_TOKEN
        if (tokenObject.firstString("access_token", "accessToken") == null) {
            return CodexAuthImportError.MISSING_ACCESS_TOKEN
        }
        if (tokenObject.firstString("refresh_token", "refreshToken") == null) {
            return CodexAuthImportError.MISSING_REFRESH_TOKEN
        }
        return if (decodeJwtClaims(tokenObject.firstString("id_token", "idToken").orEmpty()) == null) {
            CodexAuthImportError.INVALID_ID_TOKEN
        } else {
            CodexAuthImportError.INVALID_JSON
        }
    }

    internal fun buildOfficialAuth(
        credentials: ImportedCodexCredentials,
        now: Instant,
    ): JSONObject = JSONObject()
        .put("auth_mode", "chatgpt")
        .put(
            "tokens",
            JSONObject()
                .put("id_token", credentials.idToken)
                .put("access_token", credentials.accessToken)
                .put("refresh_token", credentials.refreshToken)
                .apply { credentials.accountId?.let { put("account_id", it) } },
        )
        .put("last_refresh", credentials.lastRefresh ?: now.toString())

    internal fun activateBuiltInOpenAiConfig(config: String): String {
        if (config.isBlank()) return ""
        val lines = config.lines()
        var inTopLevel = true
        var activeProvider: String? = null
        val providerPattern = Regex("""^\s*model_provider\s*=\s*[\"']([^\"']+)[\"']\s*(?:#.*)?$""")
        lines.forEach { line ->
            if (line.trimStart().startsWith("[")) inTopLevel = false
            if (inTopLevel && activeProvider == null) {
                activeProvider = providerPattern.matchEntire(line)?.groupValues?.getOrNull(1)
            }
        }
        val customProvider = activeProvider?.takeUnless { it.equals("openai", ignoreCase = true) }
        var skippingProviderSection = false
        var beforeFirstSection = true
        val output = mutableListOf<String>()
        lines.forEach { line ->
            val trimmed = line.trim()
            val isSection = trimmed.startsWith("[") && trimmed.endsWith("]")
            if (isSection) {
                beforeFirstSection = false
                skippingProviderSection = customProvider != null && isProviderSection(trimmed, customProvider)
                if (!skippingProviderSection) output += line
                return@forEach
            }
            if (skippingProviderSection) return@forEach
            if (beforeFirstSection && customProvider != null) {
                if (trimmed.matches(Regex("""model_provider\s*=.*"""))) return@forEach
                if (trimmed.matches(Regex("""model\s*=.*"""))) return@forEach
            }
            output += line
        }
        return output.joinToString("\n").trimEnd() + "\n"
    }

    private fun importIntoFiles(
        authFile: File,
        authBackupSource: File,
        configFile: File?,
        configBackupSource: File?,
        payload: String,
        now: Instant,
    ): CodexAuthImportResult {
        val credentials = parseCredentials(payload)
            ?: return CodexAuthImportResult(false, parseError(payload))
        val authExisted = authBackupSource.isFile
        val configExisted = configBackupSource?.isFile == true
        var authBackup: File? = null
        var configBackup: File? = null
        return try {
            val codexDir = authFile.parentFile ?: error("Codex auth directory is unavailable")
            check(codexDir.exists() || codexDir.mkdirs())
            val backupDir = File(codexDir, "import-backups").apply {
                check(isDirectory || mkdirs())
            }
            val suffix = timestampSuffix(now)
            authBackup = File(backupDir, "auth.$suffix.json.bak")
                .takeIf { backupIfPresent(authBackupSource, it) }
            configBackup = configBackupSource?.let { source ->
                File(backupDir, "config.$suffix.toml.bak").takeIf { backupIfPresent(source, it) }
            }

            atomicWrite(authFile, buildOfficialAuth(credentials, now).toString(2) + "\n")
            if (configFile?.isFile == true) {
                val currentConfig = configFile.readText()
                val activatedConfig = activateBuiltInOpenAiConfig(currentConfig)
                if (activatedConfig != currentConfig) atomicWrite(configFile, activatedConfig)
            }
            CodexAuthImportResult(
                success = true,
                authBackupCreated = authBackup != null,
                configBackupCreated = configBackup != null,
                accountLabel = credentials.email
                    ?: credentials.accountId?.let(::compactAccountLabel),
            )
        } catch (_: Exception) {
            restoreAfterFailedImport(authFile, authBackup, authExisted)
            if (configFile != null) restoreAfterFailedImport(configFile, configBackup, configExisted)
            CodexAuthImportResult(false, CodexAuthImportError.WRITE_FAILED)
        }
    }

    private fun parseRoots(payload: String): List<JSONObject>? {
        val value = runCatching { JSONObject(payload) }.getOrNull()
        if (value != null) return listOf(value)
        val array = runCatching { JSONArray(payload) }.getOrNull() ?: return null
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }.takeIf { it.isNotEmpty() }
    }

    private fun candidateObjects(roots: List<JSONObject>): List<JSONObject> {
        val output = ArrayList<JSONObject>()
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<JSONObject, Boolean>())
        fun visit(value: Any?, depth: Int) {
            if (value == null || depth > 8 || output.size >= MAX_JSON_NODES) return
            when (value) {
                is JSONObject -> {
                    if (!seen.add(value)) return
                    output += value
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        when (child) {
                            is JSONObject, is JSONArray -> visit(child, depth + 1)
                            is String -> if (key in SESSION_KEYS) {
                                runCatching { JSONObject(child) }.getOrNull()?.let { visit(it, depth + 1) }
                            }
                        }
                    }
                }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index), depth + 1)
            }
        }
        roots.forEach { visit(it, 0) }
        return output
    }

    private fun decodeJwtClaims(token: String): JSONObject? {
        val parts = token.split(".")
        if (parts.size != 3 || parts.any(String::isBlank)) return null
        return runCatching {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            JSONObject(String(decoded, StandardCharsets.UTF_8))
        }.getOrNull()
    }

    private fun accountIdFromClaims(claims: JSONObject): String? =
        claims.firstString("https://api.openai.com/auth.chatgpt_account_id", "chatgpt_account_id")
            ?: claims.optJSONObject("https://api.openai.com/auth")
                ?.firstString("chatgpt_account_id", "account_id")

    private fun JSONObject.firstString(vararg keys: String): String? {
        keys.forEach { key ->
            optString(key).trim().takeIf(String::isNotBlank)?.let { return it }
        }
        return null
    }

    private fun readLimited(context: Context, uri: Uri): ByteArray {
        val output = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_PAYLOAD_BYTES) return ByteArray(MAX_PAYLOAD_BYTES + 1)
                output.write(buffer, 0, read)
            }
        } ?: error("Unable to read selected file")
        return output.toByteArray()
    }

    private fun backupIfPresent(source: File, destination: File): Boolean {
        if (!source.isFile) return false
        source.copyTo(destination, overwrite = false)
        securePermissions(destination)
        return true
    }

    private fun restoreAfterFailedImport(target: File, backup: File?, existedBefore: Boolean) {
        runCatching {
            if (existedBefore && backup?.isFile == true) secureCopy(backup, target)
            else if (!existedBefore && target.exists()) target.delete()
        }
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        temporary.writeText(content)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        securePermissions(target)
    }

    private fun secureCopy(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        securePermissions(destination)
    }

    private fun securePermissions(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    private fun isProviderSection(section: String, provider: String): Boolean {
        val normalized = section.removePrefix("[").removeSuffix("]").trim()
        val quoted = "model_providers.\"$provider\""
        val plain = "model_providers.$provider"
        return normalized == quoted || normalized.startsWith("$quoted.") ||
            normalized == plain || normalized.startsWith("$plain.")
    }

    private fun isIsoInstant(value: String): Boolean = try {
        Instant.parse(value)
        true
    } catch (_: DateTimeParseException) {
        false
    }

    private fun compactAccountLabel(accountId: String): String =
        if (accountId.length <= 12) accountId else "${accountId.take(6)}…${accountId.takeLast(4)}"

    private fun timestampSuffix(now: Instant): String = now.toString()
        .replace(":", "")
        .replace("-", "")
        .replace(".", "")

    internal data class ImportedCodexCredentials(
        val idToken: String,
        val accessToken: String,
        val refreshToken: String,
        val accountId: String?,
        val lastRefresh: String?,
        val email: String?,
    )

    private val SESSION_KEYS = setOf("session", "session_json", "sessionJson")
}
