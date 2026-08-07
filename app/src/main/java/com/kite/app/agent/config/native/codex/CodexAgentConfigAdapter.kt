package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigValidationProblem
import com.kite.app.agent.config.AgentOfficialAccountAdapterProvider
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentCoreDocumentSemantics
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentUserProviderImport
import com.kite.app.agent.config.AgentUserProviderImportResult
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.agent.config.NativeAgentManagedOutputFormat
import com.kite.app.agent.config.native.codex.codexReasoningControl
import com.kite.app.agent.codex.CodexPermission
import com.kite.app.agent.codex.codexPermissionOption
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.sdk.account.AgentAccountCapabilities
import com.kite.app.agent.sdk.account.AgentAccountCapability
import com.kite.app.agent.sdk.account.AgentAccountCredentialReadResult
import com.kite.app.agent.sdk.account.AgentAccountCredentialSnapshot
import com.kite.app.agent.sdk.account.AgentAccountCredentialWriteResult
import com.kite.app.agent.sdk.account.AgentAccountIdentity
import com.kite.app.agent.sdk.account.AgentAccountIdentityResult
import com.kite.app.agent.sdk.account.AgentOfficialAccountAdapter
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONObject
import org.tomlj.Toml
import java.net.URI

internal class CodexAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : NativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(
        CONFIG_KEY to CONFIG_PATH,
        RELAY_UPSTREAM_KEY to RELAY_UPSTREAM_PATH,
        RELAY_API_KEY_KEY to RELAY_API_KEY_PATH,
    ),
    CONFIG_KEY,
    containerProvider,
    fileStore
), AgentOfficialAccountAdapterProvider, AgentOfficialAccountAdapter {
    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = listOf(CODEX_SKILL_ROOT, AGENTS_SKILL_ROOT),
        mutableRoots = setOf(CODEX_SKILL_ROOT, AGENTS_SKILL_ROOT),
    )

    override fun displayName(): String = "Codex"

    override fun officialAccountAdapter(): AgentOfficialAccountAdapter = this

    override fun accountCapabilities(): AgentAccountCapabilities = AgentAccountCapabilities(
        supported = setOf(
            AgentAccountCapability.SaveCurrent,
            AgentAccountCapability.Switch,
            AgentAccountCapability.Delete,
            AgentAccountCapability.StableId,
        ),
    )

    override suspend fun currentIdentity(agentId: String): AgentAccountIdentityResult = runCatching {
        val bytes = readAuthBytes()
            ?: return@runCatching AgentAccountIdentityResult.Unavailable("Codex 官方登录尚未生成原生凭据")
        val accountId = codexAccountId(bytes)
            ?: return@runCatching AgentAccountIdentityResult.Unavailable("Codex 没有提供稳定账号 ID")
        AgentAccountIdentity(
            accountId = accountId,
            displayName = "ChatGPT · ${compactAccountId(accountId)}",
        ).let(AgentAccountIdentityResult::Ready)
    }.getOrElse { error ->
        AgentAccountIdentityResult.Failed(error.message ?: "无法读取 Codex 官方账号状态")
    }

    override suspend fun captureCurrent(agentId: String): AgentAccountCredentialReadResult = runCatching {
        val bytes = readAuthBytes()
            ?: return@runCatching AgentAccountCredentialReadResult.Missing()
        if (codexAccountId(bytes) == null) {
            return@runCatching AgentAccountCredentialReadResult.Unavailable("Codex 凭据缺少稳定账号 ID")
        }
        AgentAccountCredentialReadResult.Ready(AgentAccountCredentialSnapshot(bytes.copyOf()))
    }.getOrElse { error ->
        AgentAccountCredentialReadResult.Failed(error.message ?: "无法读取 Codex 官方凭据")
    }

    override suspend fun restoreCurrent(
        agentId: String,
        snapshot: AgentAccountCredentialSnapshot,
    ): AgentAccountCredentialWriteResult {
        val target = projection.resolve(AUTH_PATH)?.writeFile
            ?: return AgentAccountCredentialWriteResult.Unavailable("Kite 运行容器尚未创建")
        val before = runCatching { fileStore.read(target) }.getOrElse { error ->
            return AgentAccountCredentialWriteResult.Failed(
                error.message ?: "无法读取 Codex 原生凭据",
                restored = false,
            )
        }
        return when (
            val result = fileStore.replace(
                target = target,
                expectedRevision = before.revision,
                nextBytes = snapshot.bytes.copyOf(),
                validate = ::validateAuthBytes,
            )
        ) {
            is com.kite.app.agent.config.AtomicConfigFileWriteResult.Applied ->
                AgentAccountCredentialWriteResult.Applied
            is com.kite.app.agent.config.AtomicConfigFileWriteResult.Conflict ->
                AgentAccountCredentialWriteResult.Failed("Codex 原生凭据在切换期间发生变化", restored = false)
            is com.kite.app.agent.config.AtomicConfigFileWriteResult.Rejected ->
                AgentAccountCredentialWriteResult.Failed(result.message, restored = false)
            is com.kite.app.agent.config.AtomicConfigFileWriteResult.Failed ->
                AgentAccountCredentialWriteResult.Failed(result.message, result.restored)
        }
    }

    override fun reasoningControl(): AgentReasoningControl = codexReasoningControl

    override fun providerConfigurationEffect(): AgentSessionConfigurationEffect =
        AgentSessionConfigurationEffect.Reconnect

    override suspend fun readUserProviderImport(agentId: String): AgentUserProviderImportResult = runCatching {
        val config = projection.resolve(CONFIG_PATH)
            ?: return@runCatching AgentUserProviderImportResult.Unsupported
        val parsed = Toml.parse(fileStore.read(config.readFile).bytes.toString(Charsets.UTF_8))
        require(!parsed.hasErrors()) { "Codex TOML 无法解析" }
        val providerId = parsed.getString("model_provider")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != OFFICIAL_PROVIDER_ID }
            ?: return@runCatching AgentUserProviderImportResult.Ready(AgentUserProviderImport(emptyList()))
        val modelId = parsed.getString("model")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return@runCatching AgentUserProviderImportResult.Ready(AgentUserProviderImport(emptyList()))
        val provider = parsed.getTable("model_providers.${tomlPathKey(providerId)}")
            ?: return@runCatching AgentUserProviderImportResult.Ready(AgentUserProviderImport(emptyList()))
        val configuredBaseUrl = provider.getString("base_url")?.trim()?.takeIf(String::isNotBlank)
        val relayUpstream = projectedText(RELAY_UPSTREAM_PATH)
        val usesKiteRelay = configuredBaseUrl?.trimEnd('/') == RELAY_BASE_URL
        val baseUrl = if (usesKiteRelay) {
            relayUpstream.takeIf(String::isNotBlank)
        } else {
            configuredBaseUrl
        }
            ?: return@runCatching AgentUserProviderImportResult.Ready(AgentUserProviderImport(emptyList()))
        val credential = if (usesKiteRelay) {
            projectedText(RELAY_API_KEY_PATH)
        } else {
            provider.getString("experimental_bearer_token").orEmpty().trim()
        }
        AgentUserProviderImportResult.Ready(
            AgentUserProviderImport(
                providers = listOf(
                    AgentProviderSummary(
                        id = providerId,
                        displayName = provider.getString("name")?.trim()?.takeIf(String::isNotBlank) ?: providerId,
                        baseUrl = baseUrl,
                        models = listOf(AgentProviderModelSummary(modelId, modelId)),
                        credentialPresence = if (credential.isBlank()) {
                            AgentCredentialPresence.Missing
                        } else {
                            AgentCredentialPresence.Present
                        },
                        source = AgentModelSource.UserConfigured,
                    )
                ),
                activeProviderId = providerId,
                defaultModel = "$providerId/$modelId",
                credentials = credential.takeIf(String::isNotBlank)?.let { secret ->
                    mapOf(providerId to AgentProviderCredentialChange.replace(secret))
                }.orEmpty(),
            )
        )
    }.getOrElse { AgentUserProviderImportResult.Failed("无法迁移 Codex 原生供应商配置") }

    override suspend fun readSessionConfiguration(agentId: String): List<AgentConfigOption> =
        super.readSessionConfiguration(agentId)
            .filterNot { it.category == AgentConfigCategory.Permission } +
            codexPermissionOption(CodexPermission.Custom)

    override fun defaultModelChange(
        option: AgentConfigOption.Select,
    ): AgentPersistentConfigChange.SetDefaultModel? {
        val change = super.defaultModelChange(option) ?: return null
        val selected = option.choices.firstOrNull { it.value == option.currentValue } ?: return null
        if (selected.modelSource != AgentModelSource.OfficialLogin) return null
        return change.copy(clearProviderOverride = true)
    }

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (
                option is AgentConfigOption.Select &&
                option.category == AgentConfigCategory.Model &&
                option.choices.none { it.value == option.currentValue }
            ) {
                return@mapNotNull option.copy(
                    currentValue = stripUnverifiedCodexEffortSuffix(option.currentValue),
                )
            }
            if (
                option !is AgentConfigOption.Select ||
                option.id != NATIVE_MODE_OPTION_ID ||
                option.category != AgentConfigCategory.Mode
            ) return@mapNotNull option
            val mappedChoices = option.choices.mapNotNull { choice ->
                val level = CODEX_PERMISSION_LEVELS[choice.value] ?: return@mapNotNull null
                choice.copy(
                    name = level.displayName,
                    description = level.description,
                )
            }
            if (mappedChoices.size < 2 || mappedChoices.none { it.value == option.currentValue }) {
                null
            } else {
                option.copy(
                    name = "权限",
                    description = "Codex 当前会话真实提供的沙箱与审批模式",
                    category = AgentConfigCategory.Permission,
                    choices = mappedChoices,
                )
            }
        }

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "codex-global-agents",
            displayName = "Codex 全局说明",
            fileName = "AGENTS.md",
            containerPath = GLOBAL_AGENTS_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "所有 Codex 工作区都会读取的用户级说明",
            managedOutputFormat = NativeAgentManagedOutputFormat.CreateOrUpdate,
        ))
        add(NativeAgentCoreDocumentSpec(
            id = "codex-global-override",
            displayName = "Codex 全局覆盖说明",
            fileName = "AGENTS.override.md",
            containerPath = GLOBAL_OVERRIDE_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "非空时替代同级全局 AGENTS.md",
            managedOutputFormat = NativeAgentManagedOutputFormat.ExistingNonBlankOnly,
        ))
        projectCoreDocument(
            workspacePath,
            id = "codex-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前目录说明；越接近工作目录，实际优先级越高",
        )?.let(::add)
        projectCoreDocument(
            workspacePath,
            id = "codex-project-override",
            displayName = "当前项目覆盖说明",
            fileName = "AGENTS.override.md",
            priorityDescription = "非空时替代当前目录同级 AGENTS.md",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove,
        ),
        mcpTransports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.StreamableHttp),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.Disable,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val text = files.getValue(CONFIG_KEY).toString(Charsets.UTF_8)
        val parsed = Toml.parse(text)
        require(!parsed.hasErrors()) { "Codex TOML 无法解析" }
        val active = parsed.getString("model_provider")
        val model = parsed.getString("model")
        val relayUpstream = files.getValue(RELAY_UPSTREAM_KEY).toString(Charsets.UTF_8).trim()
        val relayApiKey = files.getValue(RELAY_API_KEY_KEY).toString(Charsets.UTF_8).trim()
        val provider = active?.let { id ->
            val table = parsed.getTable("model_providers.${tomlPathKey(id)}")
            val token = table?.getString("experimental_bearer_token")
            val configuredBaseUrl = table?.getString("base_url")
            val usesKiteRelay = configuredBaseUrl?.trimEnd('/') == RELAY_BASE_URL
            AgentProviderSummary(
                id = id,
                displayName = table?.getString("name")?.takeIf(String::isNotBlank) ?: id,
                baseUrl = relayUpstream.takeIf { usesKiteRelay && it.isNotBlank() } ?: configuredBaseUrl,
                models = listOfNotNull(model?.let { AgentProviderModelSummary(it, it) }),
                credentialPresence = if (
                    !token.isNullOrBlank() || usesKiteRelay && relayApiKey.isNotBlank()
                ) AgentCredentialPresence.Present else AgentCredentialPresence.Missing
            )
        }?.let(::listOf).orEmpty()
        return NativeState(
            model,
            provider,
            overallCredential(provider),
            activeProviderId = active,
            mcpServers = codexMcpServers(parsed),
            skills = skillDirectory.summaries(
                activation = { entry -> codexSkillActivation(parsed, "${entry.containerLocation}/SKILL.md") },
                activationOperations = setOf(AgentSkillOperation.Enable, AgentSkillOperation.Disable),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateCodexMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateNativeId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateNativeId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateNativeId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateNativeId(index, "skillId", change.skillId, output)
                if (change.activation !in setOf(AgentSkillActivation.Enabled, AgentSkillActivation.Disabled)) {
                    output += problem("changes[$index].activation", "Codex 只支持启用或停用 Skill")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateNativeId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val fileChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (fileChanges.isEmpty()) return null
        if (request.changes.size != 1 || fileChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 文件变更一次只能执行一项，不能和其他配置混合")),
            )
        }
        skillDirectory.applyFileChange(fileChanges.single())?.let { return it }
        return when (val refreshed = readLive(request.agentId)) {
            is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
            is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
            is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
        }
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override suspend fun readSkillDocument(agentId: String, skillId: String) =
        skillDirectory.readDocument(skillId)

    override suspend fun writeSkillDocument(request: com.kite.app.agent.config.AgentSkillDocumentWriteRequest) =
        skillDirectory.writeDocument(request)

    override fun mutate(files: Map<String, ByteArray>, changes: List<AgentPersistentConfigChange>): Map<String, ByteArray> {
        var editor = TomlTextEditor(files.getValue(CONFIG_KEY).toString(Charsets.UTF_8))
        var relayUpstream = files.getValue(RELAY_UPSTREAM_KEY).toString(Charsets.UTF_8)
        var relayApiKey = files.getValue(RELAY_API_KEY_KEY).toString(Charsets.UTF_8)
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    if (change.clearProviderOverride) {
                        editor = editor.setTopString("model_provider", null)
                    }
                    editor = editor.setTopString("model", change.modelId)
                }
                is AgentPersistentConfigChange.SelectProvider -> editor = editor
                    .setTopString("model_provider", change.providerId)
                    .setTopString("model", change.modelId)
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    val provider = change.provider
                    val before = Toml.parse(editor.text)
                    val matchingProviderIds = buildSet {
                        add(provider.id)
                        before.getTable("model_providers")?.let { providers ->
                            providers.keySet().forEach { providerId ->
                                val existingUrl = providers.getTable(providerId)?.getString("base_url")
                                if (sameProviderEndpoint(existingUrl, provider.baseUrl)) add(providerId)
                            }
                        }
                    }
                    val legacyToken = matchingProviderIds.asSequence()
                        .mapNotNull { providerId ->
                            before.getTable("model_providers.${tomlPathKey(providerId)}")
                                ?.getString("experimental_bearer_token")
                                ?.takeIf(String::isNotBlank)
                        }
                        .firstOrNull()
                    editor = editor
                        .setTopString("model_provider", provider.id)
                        .setTopString("model", provider.models.first().id.trim())
                        .setSectionString(provider.id, "name", provider.displayName?.trim()?.takeIf(String::isNotBlank) ?: provider.id)
                        .setSectionString(provider.id, "base_url", RELAY_BASE_URL)
                        .setSectionString(provider.id, "wire_api", "responses")
                        .setSectionBoolean(provider.id, "requires_openai_auth", false)
                        .setSectionString(provider.id, "experimental_bearer_token", null)
                    matchingProviderIds.forEach { providerId ->
                        editor = editor.setSectionString(providerId, "experimental_bearer_token", null)
                    }
                    val keepsExistingRelayCredential = sameProviderEndpoint(relayUpstream, provider.baseUrl)
                    relayUpstream = provider.baseUrl.trim()
                    relayApiKey = when (val credential = change.credential) {
                        AgentProviderCredentialChange.Keep -> when {
                            keepsExistingRelayCredential && relayApiKey.isNotBlank() -> relayApiKey
                            !legacyToken.isNullOrBlank() -> legacyToken
                            else -> ""
                        }
                        is AgentProviderCredentialChange.Replace -> credential.secret
                        AgentProviderCredentialChange.Remove -> ""
                    }
                }
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val parsed = Toml.parse(editor.text)
                    val wasActive = parsed.getString("model_provider") == change.providerId
                    editor = editor.removeProviderSection(change.providerId)
                    if (wasActive) {
                        editor = editor.setTopString("model_provider", null).setTopString("model", null)
                        relayUpstream = ""
                        relayApiKey = ""
                    }
                }
                is AgentPersistentConfigChange.ConfigureMcpServer -> editor = editor.setMcpServer(change.server)
                is AgentPersistentConfigChange.SetMcpEnabled ->
                    editor = editor.setMcpBoolean(change.serverId, "enabled", change.enabled)
                is AgentPersistentConfigChange.RemoveMcpServer -> editor = editor.removeMcpServer(change.serverId)
                is AgentPersistentConfigChange.SetSkillActivation -> {
                    val skillLocation = skillDirectory.discover()
                        .firstOrNull { it.id == change.skillId }
                        ?.containerLocation
                        ?: "$CODEX_SKILL_ROOT/${change.skillId}"
                    editor = editor.setSkillEnabled(
                        "$skillLocation/SKILL.md",
                        change.activation == AgentSkillActivation.Enabled,
                    )
                }
                else -> Unit
            }
        }
        return mapOf(
            CONFIG_KEY to editor.text.toByteArray(Charsets.UTF_8),
            RELAY_UPSTREAM_KEY to relayUpstream.toByteArray(Charsets.UTF_8),
            RELAY_API_KEY_KEY to relayApiKey.toByteArray(Charsets.UTF_8),
        )
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? {
        val text = bytes.toString(Charsets.UTF_8)
        return when (key) {
            CONFIG_KEY -> if (Toml.parse(text).hasErrors()) "Codex 原生 config.toml 格式无效" else null
            RELAY_UPSTREAM_KEY -> {
                if (text.isBlank()) return null
                val uri = runCatching { URI(text.trim()) }.getOrNull()
                if (
                    bytes.size > MAX_RELAY_UPSTREAM_BYTES ||
                    uri == null ||
                    uri.scheme !in setOf("http", "https") ||
                    uri.host.isNullOrBlank() ||
                    uri.userInfo != null
                ) "Codex 协议桥上游 URL 无效" else null
            }
            RELAY_API_KEY_KEY -> if (bytes.size > MAX_RELAY_API_KEY_BYTES || text.any { it == '\u0000' }) {
                "Codex 协议桥凭据格式无效"
            } else null
            else -> "Codex 配置文件类型无效"
        }
    }

    private fun codexMcpServers(parsed: org.tomlj.TomlParseResult): List<AgentMcpSummary> {
        val servers = parsed.getTable("mcp_servers") ?: return emptyList()
        return servers.keySet().mapNotNull { id ->
            val table = servers.getTable(id) ?: return@mapNotNull null
            val command = table.getString("command")
            val url = table.getString("url")
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                !url.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.Unknown
            }
            val enabled = table.getBoolean("enabled") ?: true
            AgentMcpSummary(
                id = id,
                kind = when (transport) {
                    AgentMcpTransport.Stdio -> "stdio"
                    AgentMcpTransport.StreamableHttp -> "http"
                    else -> "unknown"
                },
                enabled = enabled,
                transport = transport,
                command = command,
                arguments = table.getArray("args")?.toList()?.mapNotNull { it as? String }.orEmpty(),
                workingDirectory = table.getString("cwd"),
                url = url,
                environmentReferences = table.getArray("env_vars")?.toList()?.mapNotNull { value ->
                    (value as? String)?.let { AgentMcpEnvironmentReference(it, it) }
                }.orEmpty(),
                headerReferences = table.getTable("env_http_headers")?.let { headers ->
                    headers.keySet().mapNotNull { name ->
                        headers.getString(name)?.let { variable -> AgentMcpEnvironmentReference(name, variable) }
                    }
                }.orEmpty(),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }.sortedBy(AgentMcpSummary::id)
    }

    private fun projectedText(containerPath: String): String = projection.resolve(containerPath)
        ?.readFile
        ?.let(fileStore::read)
        ?.bytes
        ?.toString(Charsets.UTF_8)
        ?.trim()
        .orEmpty()

    private fun readAuthBytes(): ByteArray? = projection.resolve(AUTH_PATH)
        ?.readFile
        ?.let(fileStore::read)
        ?.bytes
        ?.takeIf { it.isNotEmpty() }

    private fun codexAccountId(bytes: ByteArray): String? = runCatching {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val tokens = root.optJSONObject("tokens")
        listOf(
            root.optString("account_id"),
            root.optString("accountId"),
            tokens?.optString("account_id").orEmpty(),
            tokens?.optString("accountId").orEmpty(),
            tokens?.optString("chatgpt_account_id").orEmpty(),
        ).firstOrNull { value ->
            value.isNotBlank() && value.length <= MAX_ACCOUNT_ID && value.none(Char::isISOControl)
        }
    }.getOrNull()

    private fun validateAuthBytes(bytes: ByteArray): String? = runCatching {
        require(bytes.isNotEmpty()) { "Codex 官方凭据不能为空" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optJSONObject("tokens") != null) { "Codex 官方凭据格式无效" }
        require(codexAccountId(bytes) != null) { "Codex 官方凭据缺少稳定账号 ID" }
        null
    }.getOrElse { error -> error.message ?: "Codex 官方凭据格式无效" }

    private fun compactAccountId(accountId: String): String =
        if (accountId.length <= 12) accountId else "${accountId.take(6)}…${accountId.takeLast(4)}"

    private fun codexSkillActivation(parsed: org.tomlj.TomlParseResult, path: String): AgentSkillActivation {
        val overrides = parsed.getArray("skills.config") ?: return AgentSkillActivation.Enabled
        repeat(overrides.size()) { index ->
            val entry = overrides.getTable(index) ?: return@repeat
            if (entry.getString("path") == path && entry.getBoolean("enabled") == false) {
                return AgentSkillActivation.Disabled
            }
        }
        return AgentSkillActivation.Enabled
    }

    private fun validateCodexMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateNativeId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.arguments.size > MAX_MCP_ITEMS || draft.arguments.any { it.length > MAX_MCP_TEXT || it.any(Char::isISOControl) }) {
                    output += problem("changes[$index].server.arguments", "MCP 参数数量或格式无效")
                }
                if (draft.environmentReferences.any { it.name != it.environmentVariable }) {
                    output += problem("changes[$index].server.environmentReferences", "Codex stdio MCP 只能转发同名环境变量")
                }
            }
            AgentMcpTransport.StreamableHttp -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Codex 不支持这个 MCP 传输类型")
        }
        if (draft.environmentReferences.size > MAX_MCP_ITEMS || draft.headerReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 环境变量或 Header 数量过多")
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (!SAFE_ENV_NAME.matches(reference.environmentVariable) || reference.name.isBlank() || reference.name.any(Char::isISOControl)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
    }

    private fun validateNativeId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    companion object {
        const val ADAPTER_ID = "codex"
        private const val CONFIG_KEY = "config"
        private const val CONFIG_PATH = "/root/.codex/config.toml"
        private const val AUTH_PATH = "/root/.codex/auth.json"
        private const val RELAY_UPSTREAM_KEY = "relay-upstream"
        private const val RELAY_UPSTREAM_PATH = "/workspace/.kf/secrets/kite.codex-relay-upstream"
        private const val RELAY_API_KEY_KEY = "relay-api-key"
        private const val RELAY_API_KEY_PATH = "/workspace/.kf/secrets/kite.codex-relay-api-key"
        private const val RELAY_BASE_URL = "http://127.0.0.1:4453/v1"
        private const val NATIVE_MODE_OPTION_ID = "mode"
        private const val OFFICIAL_PROVIDER_ID = "openai"
        private const val CODEX_SKILL_ROOT = "/root/.codex/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val GLOBAL_AGENTS_PATH = "/root/.codex/AGENTS.md"
        private const val GLOBAL_OVERRIDE_PATH = "/root/.codex/AGENTS.override.md"
        private const val MAX_MCP_ITEMS = 64
        private const val MAX_MCP_TEXT = 2_048
        private const val MAX_RELAY_UPSTREAM_BYTES = 4 * 1024
        private const val MAX_RELAY_API_KEY_BYTES = 64 * 1024
        private const val MAX_ACCOUNT_ID = 256
        private val CODEX_PERMISSION_LEVELS = mapOf(
            "read-only" to AgentPermissionLevel.ReadOnly,
            "agent" to AgentPermissionLevel.Approval,
            "agent-full-access" to AgentPermissionLevel.Full,
        )
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val CODEX_COMPOSITE_MODEL = Regex("^(.+)\\[([^]\\r\\n]+)]$")
        private val CODEX_FALLBACK_EFFORTS = setOf(
            "none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra",
        )
        private fun stripUnverifiedCodexEffortSuffix(value: String): String {
            val match = CODEX_COMPOSITE_MODEL.matchEntire(value) ?: return value
            return match.groupValues[1]
                .takeIf { match.groupValues[2].lowercase() in CODEX_FALLBACK_EFFORTS }
                ?: value
        }
        private fun sameProviderEndpoint(left: String?, right: String?): Boolean =
            !left.isNullOrBlank() && !right.isNullOrBlank() &&
                left.trim().trimEnd('/') == right.trim().trimEnd('/')
        private fun tomlPathKey(value: String): String =
            if (Regex("[A-Za-z0-9_-]+").matches(value)) value
            else "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}

private data class TomlTextEditor(val text: String) {
    fun setTopString(key: String, value: String?): TomlTextEditor =
        copy(text = setField(text, key, value?.let(::tomlQuote), sectionHeader = null))

    fun setSectionString(providerId: String, key: String, value: String?): TomlTextEditor =
        copy(text = setField(text, key, value?.let(::tomlQuote), providerHeader(providerId)))

    fun setSectionBoolean(providerId: String, key: String, value: Boolean?): TomlTextEditor =
        copy(text = setField(text, key, value?.toString(), providerHeader(providerId)))

    fun removeProviderSection(providerId: String): TomlTextEditor {
        val lines = normalizedLines(text)
        val range = sectionRange(lines, providerHeader(providerId)) ?: return this
        return copy(text = normalizeToml((lines.subList(0, range.first) + lines.subList(range.last + 1, lines.size)).joinToString("\n")))
    }

    fun setMcpServer(draft: AgentMcpDraft): TomlTextEditor {
        var editor = this
        MCP_MANAGED_KEYS.forEach { key -> editor = editor.setMcpField(draft.id, key, null) }
        editor = editor.setMcpField(draft.id, "enabled", draft.enabled.toString())
        editor = when (draft.transport) {
            AgentMcpTransport.Stdio -> editor
                .setMcpField(draft.id, "command", tomlQuote(requireNotNull(draft.command).trim()))
                .setMcpField(draft.id, "args", tomlStringArray(draft.arguments))
                .setMcpField(draft.id, "cwd", draft.workingDirectory?.trim()?.takeIf(String::isNotBlank)?.let(::tomlQuote))
                .setMcpField(
                    draft.id,
                    "env_vars",
                    tomlStringArray(draft.environmentReferences.map(AgentMcpEnvironmentReference::environmentVariable)),
                )
            AgentMcpTransport.StreamableHttp -> editor
                .setMcpField(draft.id, "url", tomlQuote(requireNotNull(draft.url).trim()))
                .setMcpField(
                    draft.id,
                    "env_http_headers",
                    tomlStringMap(draft.headerReferences.associate { it.name to it.environmentVariable }),
                )
            else -> error("已由 Codex MCP 校验限制传输类型")
        }
        return editor
    }

    fun setMcpBoolean(serverId: String, key: String, value: Boolean?): TomlTextEditor =
        setMcpField(serverId, key, value?.toString())

    fun removeMcpServer(serverId: String): TomlTextEditor {
        val lines = normalizedLines(text)
        val range = sectionFamilyRange(lines, mcpHeader(serverId)) ?: return this
        return copy(
            text = normalizeToml(
                (lines.subList(0, range.first) + lines.subList(range.last + 1, lines.size)).joinToString("\n"),
            ),
        )
    }

    fun setSkillEnabled(path: String, enabled: Boolean): TomlTextEditor {
        val lines = normalizedLines(text).toMutableList()
        val headers = lines.indices.filter { lines[it].trim() == SKILL_HEADER }
        headers.forEachIndexed { headerIndex, start ->
            val endExclusive = headers.getOrNull(headerIndex + 1)
                ?: (start + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith('[') }
                ?: lines.size
            val pathLine = (start + 1 until endExclusive).firstOrNull { SKILL_PATH_FIELD.containsMatchIn(lines[it]) }
                ?: return@forEachIndexed
            val existingPath = TOML_STRING_VALUE.find(lines[pathLine])?.groupValues?.get(1)?.let(::tomlUnescape)
            if (existingPath != path) return@forEachIndexed
            val enabledLine = (start + 1 until endExclusive).firstOrNull { SKILL_ENABLED_FIELD.containsMatchIn(lines[it]) }
            if (enabledLine == null) lines.add(endExclusive, "enabled = $enabled") else lines[enabledLine] = "enabled = $enabled"
            return copy(text = normalizeToml(lines.joinToString("\n")))
        }
        if (lines.any(String::isNotBlank)) lines += ""
        lines += SKILL_HEADER
        lines += "path = ${tomlQuote(path)}"
        lines += "enabled = $enabled"
        return copy(text = normalizeToml(lines.joinToString("\n")))
    }

    private fun setMcpField(serverId: String, key: String, rendered: String?): TomlTextEditor =
        copy(text = setField(text, key, rendered, mcpHeader(serverId)))

    private fun setField(source: String, key: String, rendered: String?, sectionHeader: String?): String {
        val lines = normalizedLines(source).toMutableList()
        val range = if (sectionHeader == null) topRange(lines) else sectionRange(lines, sectionHeader)
        if (range == null && sectionHeader != null) {
            if (rendered == null) return normalizeToml(lines.joinToString("\n"))
            if (lines.any(String::isNotBlank)) lines += ""
            lines += sectionHeader
            lines += "$key = $rendered"
            return normalizeToml(lines.joinToString("\n"))
        }
        val actual = requireNotNull(range)
        val regex = Regex("^\\s*${Regex.escape(key)}\\s*=")
        val index = (actual.first..actual.last).firstOrNull { regex.containsMatchIn(lines[it]) }
        if (index != null) {
            if (rendered == null) lines.removeAt(index) else lines[index] = "$key = $rendered"
        } else if (rendered != null) {
            val insertAt = actual.last + 1
            lines.add(insertAt.coerceAtMost(lines.size), "$key = $rendered")
        }
        return normalizeToml(lines.joinToString("\n"))
    }

    private fun topRange(lines: List<String>): IntRange {
        val end = lines.indexOfFirst { it.trimStart().startsWith('[') }.let { if (it < 0) lines.lastIndex else it - 1 }
        return 0..end.coerceAtLeast(0)
    }

    private fun sectionRange(lines: List<String>, header: String): IntRange? {
        val start = lines.indexOfFirst { it.trim() == header }
        if (start < 0) return null
        val next = (start + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith('[') } ?: lines.size
        return start until next
    }

    private fun sectionFamilyRange(lines: List<String>, header: String): IntRange? {
        val start = lines.indexOfFirst { it.trim() == header }
        if (start < 0) return null
        val nestedPrefix = header.removeSuffix("]") + "."
        val next = (start + 1 until lines.size).firstOrNull { index ->
            val candidate = lines[index].trim()
            candidate.startsWith('[') && candidate != header && !candidate.startsWith(nestedPrefix)
        } ?: lines.size
        return start until next
    }

    private companion object {
        fun normalizedLines(value: String): List<String> = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        fun normalizeToml(value: String): String = value.trimEnd() + "\n"
        fun tomlQuote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        fun tomlKey(value: String): String = if (Regex("[A-Za-z0-9_-]+").matches(value)) value else tomlQuote(value)
        fun providerHeader(id: String): String = "[model_providers.${tomlKey(id)}]"
        fun mcpHeader(id: String): String = "[mcp_servers.${tomlKey(id)}]"
        fun tomlStringArray(values: List<String>): String? = values.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "[", postfix = "]") { tomlQuote(it) }
        fun tomlStringMap(values: Map<String, String>): String? = values.takeIf { it.isNotEmpty() }
            ?.entries
            ?.sortedBy { it.key }
            ?.joinToString(prefix = "{ ", postfix = " }") { (key, value) -> "${tomlKey(key)} = ${tomlQuote(value)}" }
        fun tomlUnescape(value: String): String = value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

        const val SKILL_HEADER = "[[skills.config]]"
        val SKILL_PATH_FIELD = Regex("^\\s*path\\s*=")
        val SKILL_ENABLED_FIELD = Regex("^\\s*enabled\\s*=")
        val TOML_STRING_VALUE = Regex("=\\s*\"((?:\\\\.|[^\"])*)\"")
        val MCP_MANAGED_KEYS = listOf(
            "command",
            "args",
            "cwd",
            "env_vars",
            "url",
            "bearer_token_env_var",
            "env_http_headers",
            "enabled",
        )
    }
}
