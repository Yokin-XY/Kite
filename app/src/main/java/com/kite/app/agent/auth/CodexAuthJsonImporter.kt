package com.kite.app.agent.auth

import android.content.Context
import android.net.Uri
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
)

/**
 * Imports the native Codex `auth.json` format into the active PRoot View.
 *
 * This first, deliberately narrow PR treats the selected file as opaque
 * credential data: it only validates the outer native shape and writes the
 * original bytes unchanged. Rollback snapshots are same-directory temporary
 * files and are deleted after a successful import or a failed rollback.
 */
internal object CodexAuthJsonImporter {
    private const val MAX_PAYLOAD_BYTES = 1024 * 1024
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
                payload = payload,
            )
        }.getOrElse { CodexAuthImportResult(false, CodexAuthImportError.WRITE_FAILED) }
    }

    internal fun importIntoRootfs(
        rootfsDir: File,
        payload: String,
    ): CodexAuthImportResult {
        val codexDir = File(rootfsDir, "root/.codex")
        return importIntoFiles(
            authFile = File(codexDir, "auth.json"),
            authBackupSource = File(codexDir, "auth.json"),
            configFile = File(codexDir, "config.toml"),
            configBackupSource = File(codexDir, "config.toml"),
            payload = payload.toByteArray(StandardCharsets.UTF_8),
        )
    }

    internal fun validateOfficialAuthJson(payload: String): CodexAuthImportError? {
        val root = runCatching { JSONObject(payload) }.getOrNull()
            ?: return CodexAuthImportError.INVALID_JSON
        if (!root.optString("auth_mode").equals("chatgpt", ignoreCase = true)) {
            return CodexAuthImportError.INVALID_JSON
        }
        val tokens = root.optJSONObject("tokens")
            ?: return CodexAuthImportError.MISSING_ID_TOKEN
        if (tokens.optString("id_token").isBlank()) {
            return CodexAuthImportError.MISSING_ID_TOKEN
        }
        if (tokens.optString("access_token").isBlank()) {
            return CodexAuthImportError.MISSING_ACCESS_TOKEN
        }
        if (tokens.optString("refresh_token").isBlank()) {
            return CodexAuthImportError.MISSING_REFRESH_TOKEN
        }
        return null
    }

    private fun importIntoFiles(
        authFile: File,
        authBackupSource: File,
        configFile: File?,
        configBackupSource: File?,
        payload: ByteArray,
    ): CodexAuthImportResult {
        val validationError = validateOfficialAuthJson(payload.toString(StandardCharsets.UTF_8))
        if (validationError != null) return CodexAuthImportResult(false, validationError)

        var authRollback: RollbackSnapshot? = null
        var configRollback: RollbackSnapshot? = null
        return try {
            val codexDir = authFile.parentFile ?: error("Codex auth directory is unavailable")
            check(codexDir.exists() || codexDir.mkdirs())
            authRollback = createRollbackSnapshot(authBackupSource, codexDir)
            configRollback = configBackupSource?.let { createRollbackSnapshot(it, codexDir) }

            writeAtomic(authFile, payload)
            if (configFile?.isFile == true) {
                val currentConfig = configFile.readText()
                val activatedConfig = activateBuiltInOpenAiConfig(currentConfig)
                if (activatedConfig != currentConfig) {
                    writeAtomic(configFile, activatedConfig.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val result = CodexAuthImportResult(
                success = true,
                authBackupCreated = authRollback?.file != null,
                configBackupCreated = configRollback?.file != null,
            )
            authRollback?.delete()
            configRollback?.delete()
            result
        } catch (_: Exception) {
            restoreAfterFailedImport(authFile, authRollback)
            if (configFile != null) restoreAfterFailedImport(configFile, configRollback)
            authRollback?.delete()
            configRollback?.delete()
            CodexAuthImportResult(false, CodexAuthImportError.WRITE_FAILED)
        }
    }

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

    private fun parseRollbackPath(directory: File): File =
        File(directory, ".kite-auth-rollback-${System.nanoTime()}.tmp")

    private fun createRollbackSnapshot(source: File, directory: File): RollbackSnapshot {
        if (!source.isFile) return RollbackSnapshot(null, existedBefore = false)
        val snapshot = parseRollbackPath(directory)
        secureCopy(source, snapshot)
        return RollbackSnapshot(snapshot, existedBefore = true)
    }

    private fun restoreAfterFailedImport(target: File, snapshot: RollbackSnapshot?) {
        runCatching {
            if (snapshot?.existedBefore == true && snapshot.file?.isFile == true) {
                secureCopy(snapshot.file, target)
            } else if (snapshot != null && target.exists()) {
                target.delete()
            }
        }
    }

    private fun writeAtomic(target: File, content: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(content)
                stream.flush()
                stream.fd.sync()
            }
            securePermissions(temporary)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            securePermissions(target)
        } finally {
            temporary.delete()
        }
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

    private data class RollbackSnapshot(
        val file: File?,
        val existedBefore: Boolean,
    ) {
        fun delete() {
            file?.delete()
        }
    }
}
