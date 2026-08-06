package com.kite.app.agent.auth

import android.content.Context
import android.net.Uri
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
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
    val importedAccountCount: Int = 0,
    val activeAccountId: String? = null,
)

internal data class CodexImportedAccount(
    val id: String,
    val label: String,
)

internal data class CodexImportedAccountSnapshot(
    val containerReady: Boolean = false,
    val accounts: List<CodexImportedAccount> = emptyList(),
    val activeAccountId: String? = null,
)

internal data class CodexAccountSwitchResult(
    val success: Boolean,
    val snapshot: CodexImportedAccountSnapshot,
    val error: CodexAuthImportError? = null,
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
    private const val ACCOUNT_STORE_DIRECTORY = "kite-json-accounts"
    private const val ACCOUNT_STORE_VERSION = 1

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

    fun accountSnapshot(context: Context): CodexImportedAccountSnapshot {
        val appContext = context.applicationContext
        val container = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
            ?: return CodexImportedAccountSnapshot()
        val projection = ContainerAgentConfigProjection { container }
        return runCatching {
            val authProjection = projection.resolve(AUTH_PATH)
                ?: return@runCatching CodexImportedAccountSnapshot()
            snapshotFiles(authProjection.writeFile)
        }.getOrDefault(CodexImportedAccountSnapshot())
    }

    fun activateAccount(context: Context, accountId: String): CodexAccountSwitchResult {
        val appContext = context.applicationContext
        val container = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
            ?: return CodexAccountSwitchResult(
                success = false,
                snapshot = CodexImportedAccountSnapshot(),
                error = CodexAuthImportError.CONTAINER_NOT_READY,
            )
        val projection = ContainerAgentConfigProjection { container }
        return runCatching {
            val authProjection = projection.resolve(AUTH_PATH)
                ?: return@runCatching CodexAccountSwitchResult(
                    success = false,
                    snapshot = CodexImportedAccountSnapshot(),
                    error = CodexAuthImportError.CONTAINER_NOT_READY,
                )
            val configProjection = projection.resolve(CONFIG_PATH)
            activateAccountFiles(
                authFile = authProjection.writeFile,
                authReadFile = authProjection.readFile,
                configFile = configProjection?.writeFile,
                configReadFile = configProjection?.readFile,
                accountId = accountId,
            )
        }.getOrElse {
            CodexAccountSwitchResult(
                success = false,
                snapshot = CodexImportedAccountSnapshot(containerReady = true),
                error = CodexAuthImportError.WRITE_FAILED,
            )
        }
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

    internal fun snapshotRootfs(rootfsDir: File): CodexImportedAccountSnapshot =
        snapshotFiles(File(rootfsDir, "root/.codex/auth.json"))

    internal fun activateAccountRootfs(
        rootfsDir: File,
        accountId: String,
    ): CodexAccountSwitchResult {
        val codexDir = File(rootfsDir, "root/.codex")
        return activateAccountFiles(
            authFile = File(codexDir, "auth.json"),
            authReadFile = File(codexDir, "auth.json"),
            configFile = File(codexDir, "config.toml"),
            configReadFile = File(codexDir, "config.toml"),
            accountId = accountId,
        )
    }

    internal fun parseCredentials(payload: String): ImportedCodexCredentials? =
        parseCredentialList(payload).firstOrNull()

    internal fun parseCredentialList(payload: String): List<ImportedCodexCredentials> {
        val roots = parseRoots(payload) ?: return emptyList()
        val objects = candidateObjects(roots)
        val credentials = mutableListOf<ImportedCodexCredentials>()
        val seenTokenSets = mutableSetOf<String>()
        for (candidate in objects) {
            val tokenObject = candidate.optJSONObject("tokens") ?: candidate
            val idToken = tokenObject.firstString("id_token", "idToken") ?: continue
            val accessToken = tokenObject.firstString("access_token", "accessToken") ?: continue
            val refreshToken = tokenObject.firstString("refresh_token", "refreshToken") ?: continue
            val idClaims = decodeJwtClaims(idToken) ?: continue
            if (!seenTokenSets.add("$idToken\u0000$accessToken\u0000$refreshToken")) continue
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
            val lastRefresh = tokenObject.firstString("last_refresh", "lastRefresh")
                ?.takeIf(::isIsoInstant)
                ?: candidate.firstString("last_refresh", "lastRefresh")
                ?.takeIf(::isIsoInstant)
                ?: roots.firstNotNullOfOrNull { it.firstString("last_refresh", "lastRefresh")?.takeIf(::isIsoInstant) }
            val email = tokenObject.firstString("email")
                ?: candidate.firstString("email")
                ?: idClaims.firstString("email")
            credentials += ImportedCodexCredentials(
                idToken = idToken,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accountId = accountId,
                lastRefresh = lastRefresh,
                email = email,
            )
        }
        return credentials
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
        val importedCredentials = parseCredentialList(payload)
        if (importedCredentials.isEmpty()) {
            return CodexAuthImportResult(false, parseError(payload))
        }
        val store = AccountStore(authFile)
        val rollbacks = mutableListOf<Pair<File, RollbackSnapshot>>()
        return try {
            val codexDir = authFile.parentFile ?: error("Codex auth directory is unavailable")
            check(codexDir.exists() || codexDir.mkdirs())
            initializeStore(store)
            saveActiveAccount(store, authBackupSource)

            rollbacks += authFile to createRollbackSnapshot(authBackupSource, codexDir)
            if (configFile != null && configBackupSource != null) {
                rollbacks += configFile to createRollbackSnapshot(configBackupSource, codexDir)
            }
            rollbacks += store.state to createRollbackSnapshot(store.state, store.directory)

            val importedAccounts = importedCredentials.map { credentials ->
                val auth = buildOfficialAuth(credentials, now)
                val id = profileId(credentials, auth)
                val label = accountLabel(credentials, id)
                val profileFile = accountFile(store, id)
                rollbacks += profileFile to createRollbackSnapshot(profileFile, store.directory)
                writeAccount(store, id, label, auth)
                CodexImportedAccount(id, label)
            }
            val active = importedAccounts.first()

            val activeProfile = readAccount(accountFile(store, active.id))
                ?: error("Imported account profile is invalid")
            atomicWrite(authFile, activeProfile.auth.toString(2) + "\n")
            if (configFile?.isFile == true) {
                val currentConfig = configBackupSource?.takeIf(File::isFile)?.readText()
                    ?: configFile.readText()
                val activatedConfig = activateBuiltInOpenAiConfig(currentConfig)
                if (activatedConfig != currentConfig) atomicWrite(configFile, activatedConfig)
            }
            writeActiveAccountId(store, active.id)
            val authBackupCreated = rollbacks
                .firstOrNull { (target, _) -> target == authFile }
                ?.second
                ?.existedBefore == true
            val configBackupCreated = configFile != null && rollbacks
                .firstOrNull { (target, _) -> target == configFile }
                ?.second
                ?.existedBefore == true
            rollbacks.forEach { it.second.delete() }
            CodexAuthImportResult(
                success = true,
                authBackupCreated = authBackupCreated,
                configBackupCreated = configBackupCreated,
                accountLabel = active.label,
                importedAccountCount = importedAccounts.size,
                activeAccountId = active.id,
            )
        } catch (_: Exception) {
            rollbacks.asReversed().forEach { (target, snapshot) ->
                restoreAfterFailedImport(target, snapshot)
                snapshot.delete()
            }
            CodexAuthImportResult(false, CodexAuthImportError.WRITE_FAILED)
        }
    }

    @Synchronized
    private fun activateAccountFiles(
        authFile: File,
        authReadFile: File,
        configFile: File?,
        configReadFile: File?,
        accountId: String,
    ): CodexAccountSwitchResult {
        val store = AccountStore(authFile)
        return runCatching {
            initializeStore(store)
            val account = readAccount(accountFile(store, accountId))
                ?: return CodexAccountSwitchResult(
                    false,
                    snapshotFiles(authFile),
                    CodexAuthImportError.INVALID_JSON,
                )
            saveActiveAccount(store, authReadFile)

            val codexDir = authFile.parentFile ?: error("Codex auth directory is unavailable")
            val authRollback = createRollbackSnapshot(authReadFile, codexDir)
            val configRollback = configReadFile?.let { createRollbackSnapshot(it, codexDir) }
            val stateRollback = createRollbackSnapshot(store.state, store.directory)
            try {
                atomicWrite(authFile, account.auth.toString(2) + "\n")
                if (configFile != null) {
                    val currentConfig = configReadFile?.takeIf(File::isFile)?.readText()
                        ?: configFile.takeIf(File::isFile)?.readText().orEmpty()
                    val activatedConfig = activateBuiltInOpenAiConfig(currentConfig)
                    if (activatedConfig != currentConfig || !configFile.isFile) {
                        atomicWrite(configFile, activatedConfig)
                    }
                }
                writeActiveAccountId(store, account.id)
                authRollback.delete()
                configRollback?.delete()
                stateRollback.delete()
                CodexAccountSwitchResult(
                    success = true,
                    snapshot = snapshotFiles(authFile),
                    accountLabel = account.label,
                )
            } catch (_: Exception) {
                restoreAfterFailedImport(authFile, authRollback)
                if (configFile != null && configRollback != null) {
                    restoreAfterFailedImport(configFile, configRollback)
                }
                restoreAfterFailedImport(store.state, stateRollback)
                authRollback.delete()
                configRollback?.delete()
                stateRollback.delete()
                CodexAccountSwitchResult(
                    false,
                    snapshotFiles(authFile),
                    CodexAuthImportError.WRITE_FAILED,
                )
            }
        }.getOrElse {
            CodexAccountSwitchResult(
                false,
                snapshotFiles(authFile),
                CodexAuthImportError.WRITE_FAILED,
            )
        }
    }

    private fun snapshotFiles(authFile: File): CodexImportedAccountSnapshot {
        val store = AccountStore(authFile)
        val containerReady = authFile.parentFile?.isDirectory == true
        if (!containerReady || !store.accounts.isDirectory) {
            return CodexImportedAccountSnapshot(containerReady = containerReady)
        }
        val accounts = store.accounts.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .mapNotNull(::readAccount)
            .map { CodexImportedAccount(it.id, it.label) }
            .sortedBy { it.label.lowercase() }
        return CodexImportedAccountSnapshot(
            containerReady = true,
            accounts = accounts,
            activeAccountId = readActiveAccountId(store),
        )
    }

    private fun initializeStore(store: AccountStore) {
        check(store.directory.isDirectory || store.directory.mkdirs())
        check(store.accounts.isDirectory || store.accounts.mkdirs())
        secureDirectory(store.directory)
        secureDirectory(store.accounts)
    }

    private fun writeAccount(
        store: AccountStore,
        id: String,
        label: String,
        auth: JSONObject,
    ) {
        val wrapper = JSONObject()
            .put("version", ACCOUNT_STORE_VERSION)
            .put("id", id)
            .put("label", label)
            .put("auth", auth)
        atomicWrite(accountFile(store, id), wrapper.toString(2) + "\n")
    }

    private fun readAccount(file: File): StoredAccount? = runCatching {
        val wrapper = JSONObject(file.readText())
        val id = wrapper.optString("id").trim().takeIf(String::isNotBlank) ?: return null
        val label = wrapper.optString("label").trim().takeIf(String::isNotBlank)
            ?: "JSON account ${id.takeLast(6)}"
        val auth = wrapper.optJSONObject("auth") ?: return null
        if (!isOfficialAuth(auth)) return null
        StoredAccount(id, label, auth)
    }.getOrNull()

    private fun saveActiveAccount(store: AccountStore, visibleAuth: File) {
        val activeId = readActiveAccountId(store) ?: return
        val previous = readAccount(accountFile(store, activeId)) ?: return
        val refreshed = runCatching { JSONObject(visibleAuth.readText()) }.getOrNull() ?: return
        if (!isOfficialAuth(refreshed)) return
        writeAccount(store, activeId, previous.label, refreshed)
    }

    private fun writeActiveAccountId(store: AccountStore, accountId: String) {
        val state = JSONObject()
            .put("version", ACCOUNT_STORE_VERSION)
            .put("active_account_id", accountId)
        atomicWrite(store.state, state.toString(2) + "\n")
    }

    private fun readActiveAccountId(store: AccountStore): String? = runCatching {
        JSONObject(store.state.readText())
            .optString("active_account_id")
            .trim()
            .takeIf(String::isNotBlank)
    }.getOrNull()

    private fun accountFile(store: AccountStore, accountId: String): File {
        require(accountId.matches(Regex("""[a-zA-Z0-9_-]+""")))
        return File(store.accounts, "$accountId.json")
    }

    private fun profileId(credentials: ImportedCodexCredentials, auth: JSONObject): String {
        val stable = credentials.accountId
            ?: credentials.email
            ?: decodeJwtClaims(credentials.idToken)?.firstString("sub")
            ?: auth.optJSONObject("tokens")?.optString("refresh_token").orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
        return "json-${digest.joinToString("") { "%02x".format(it) }.take(16)}"
    }

    private fun accountLabel(credentials: ImportedCodexCredentials, profileId: String): String =
        credentials.email
            ?: credentials.accountId?.let(::compactAccountLabel)
            ?: "JSON account ${profileId.takeLast(6)}"

    private fun isOfficialAuth(auth: JSONObject): Boolean =
        auth.optString("auth_mode") == "chatgpt" &&
            auth.optJSONObject("tokens")?.optString("refresh_token").isNullOrBlank().not()

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

    private fun createRollbackSnapshot(source: File, directory: File): RollbackSnapshot {
        if (!source.isFile) return RollbackSnapshot(null, existedBefore = false)
        val snapshot = File(
            directory,
            ".${source.name}.kite-rollback-${System.nanoTime()}.tmp",
        )
        secureCopy(source, snapshot)
        return RollbackSnapshot(snapshot, existedBefore = true)
    }

    private fun restoreAfterFailedImport(target: File, snapshot: RollbackSnapshot) {
        runCatching {
            if (snapshot.existedBefore && snapshot.file?.isFile == true) {
                secureCopy(snapshot.file, target)
            } else if (!snapshot.existedBefore && target.exists()) {
                target.delete()
            }
        }
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(content.toByteArray(StandardCharsets.UTF_8))
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

    private fun secureDirectory(directory: File) {
        directory.setReadable(false, false)
        directory.setWritable(false, false)
        directory.setExecutable(false, false)
        directory.setReadable(true, true)
        directory.setWritable(true, true)
        directory.setExecutable(true, true)
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

    internal data class ImportedCodexCredentials(
        val idToken: String,
        val accessToken: String,
        val refreshToken: String,
        val accountId: String?,
        val lastRefresh: String?,
        val email: String?,
    )

    private data class StoredAccount(
        val id: String,
        val label: String,
        val auth: JSONObject,
    )

    private data class RollbackSnapshot(
        val file: File?,
        val existedBefore: Boolean,
    ) {
        fun delete() {
            file?.delete()
        }
    }

    private class AccountStore(authFile: File) {
        val directory = File(authFile.parentFile, ACCOUNT_STORE_DIRECTORY)
        val accounts = File(directory, "accounts")
        val state = File(directory, "state.json")
    }

    private val SESSION_KEYS = setOf("session", "session_json", "sessionJson")
}
