package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigValidationProblem
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
import com.kite.app.agent.config.AgentPermissionProfileSummary
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AgentSessionModelSelection
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.agent.config.NativeAgentManagedOutputFormat
import com.kite.app.agent.config.mediatedSessionPermissionControl
import com.kite.app.agent.config.native.hermes.hermesReasoningControl
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URI
import java.util.LinkedHashMap

internal class HermesAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : NativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(CONFIG_KEY to CONFIG_PATH),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val yaml = Yaml(
        SafeConstructor(LoaderOptions().apply {
            maxAliasesForCollections = 50
            nestingDepthLimit = 32
            codePointLimit = 8 * 1024 * 1024
        }),
        org.yaml.snakeyaml.representer.Representer(DumperOptions()),
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            width = 120
        }
    )
    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = listOf(SKILL_ROOT),
    )

    override fun displayName(): String = "Hermes"

    override fun reasoningControl(): AgentReasoningControl = hermesReasoningControl

    override fun sessionPermissionControl(): AgentSessionPermissionControl =
        mediatedSessionPermissionControl(
            profiles = HERMES_PERMISSION_PROFILES,
            handlingByProfileId = mapOf(
                "manual" to AgentSessionPermissionHandling.AskUser,
                "smart" to AgentSessionPermissionHandling.PreserveAgentDecision,
                "off" to AgentSessionPermissionHandling.AllowRequest,
            ),
            initialProfileId = DEFAULT_APPROVAL_MODE,
            nativeModeByProfileId = mapOf(
                "manual" to HERMES_MODE_DEFAULT,
                "smart" to HERMES_MODE_ACCEPT_EDITS,
                "off" to HERMES_MODE_DONT_ASK,
            ),
        )

    override fun normalizeSessionModes(modes: List<com.kite.app.agent.contract.AgentMode>) =
        modes.filterNot { it.id in HERMES_PERMISSION_MODE_IDS }

    override fun sessionModelSelection(
        selection: AgentPersistentConfigChange.SelectProvider,
        options: List<AgentConfigOption>,
    ): AgentSessionModelSelection? {
        super.sessionModelSelection(selection, options)?.let { return it }
        val modelOption = options
            .filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
            ?: return null
        val providerId = selection.providerId.removePrefix(HERMES_CUSTOM_PROVIDER_PREFIX)
        val nativeValues = listOf(
            "$HERMES_CUSTOM_PROVIDER_PREFIX$providerId:${selection.modelId}",
            "${selection.providerId}:${selection.modelId}",
        )
        val choice = nativeValues.firstNotNullOfOrNull { nativeValue ->
            modelOption.choices.firstOrNull { it.value == nativeValue }
        } ?: return null
        return AgentSessionModelSelection(modelOption.id, choice.value)
    }

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "hermes-soul",
            displayName = "人格与身份",
            fileName = "SOUL.md",
            containerPath = "$HERMES_HOME_PATH/SOUL.md",
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.Persona,
            priorityDescription = "作为 Hermes 系统提示的第一层身份；非空时替代内置身份",
            managedOutputFormat = NativeAgentManagedOutputFormat.ExistingNonBlankOnly,
        ))
        add(NativeAgentCoreDocumentSpec(
            id = "hermes-user",
            displayName = "用户资料",
            fileName = "USER.md",
            containerPath = "$HERMES_HOME_PATH/memories/USER.md",
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.UserProfile,
            priorityDescription = "作为跨会话用户资料进入 Hermes 的可变上下文",
        ))
        projectCoreDocument(
            workspacePath,
            id = "hermes-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前工作目录的项目规则；与人格文件分开加载",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.PermissionProfiles,
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
        mcpTransports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
        ),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.Disable,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = yamlMap(files.getValue(CONFIG_KEY))
        val model = root.map("model")
        val providerId = model.string("provider")
        val defaultModel = model.string("default") ?: model.string("model")
        val legacyProviders = root.list(LEGACY_PROVIDERS_KEY).mapNotNull(::legacyProviderSummary)
        val modernProviders = root.map(PROVIDERS_KEY).mapNotNull { (id, value) ->
            modernProviderSummary(id, value)
        }
        val providers = (legacyProviders.filter { legacy -> modernProviders.none { it.id == legacy.id } } + modernProviders)
            .sortedBy(AgentProviderSummary::id)
            .ifEmpty { providerId?.let { active ->
            listOf(
                AgentProviderSummary(
                    id = active,
                    displayName = active,
                    baseUrl = model.string("base_url"),
                    models = listOfNotNull(defaultModel?.let { AgentProviderModelSummary(it, it) }),
                    credentialPresence = if (!model.string("api_key").isNullOrBlank()) AgentCredentialPresence.Present
                    else AgentCredentialPresence.Missing
                )
            )
        }.orEmpty() }
        val approvalMode = root.map(APPROVALS_KEY).string(MODE_KEY)
        val activePermissionProfileId = when {
            approvalMode == null -> DEFAULT_APPROVAL_MODE
            approvalMode in HERMES_PERMISSION_PROFILE_IDS -> approvalMode
            else -> null
        }
        val warnings = buildList {
            if (approvalMode != null && activePermissionProfileId == null) {
                add("Hermes approvals.mode 不是官方支持值，Kite 不会替换或猜测")
            }
        }
        val disabledSkills = hermesDisabledSkills(root)
        return NativeState(
            defaultModel,
            providers,
            overallCredential(providers),
            warnings,
            activeProviderId = providerId?.takeIf { active -> providers.any { it.id == active } },
            activePermissionProfileId = activePermissionProfileId,
            permissionProfiles = HERMES_PERMISSION_PROFILES,
            mcpServers = hermesMcpServers(root.map(MCP_SERVERS_KEY)),
            skills = skillDirectory.summaries(
                activation = { entry ->
                    if (entry.id in disabledSkills) AgentSkillActivation.Disabled else AgentSkillActivation.Enabled
                },
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
            is AgentPersistentConfigChange.SetPermissionProfile -> {
                if (change.profileId !in HERMES_PERMISSION_PROFILE_IDS) {
                    output += problem("changes[$index].profileId", "Hermes 不支持这个官方权限档位")
                }
            }
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateHermesMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateHermesId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateHermesId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateHermesId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateHermesId(index, "skillId", change.skillId, output)
                if (change.activation !in HERMES_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "Hermes 只支持启用或停用 Skill")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateHermesId(index, "skillId", change.skillId, output)
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
        return refreshedApplyResult(request.agentId)
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override suspend fun readSkillDocument(agentId: String, skillId: String) =
        skillDirectory.readDocument(skillId)

    override suspend fun writeSkillDocument(request: com.kite.app.agent.config.AgentSkillDocumentWriteRequest) =
        skillDirectory.writeDocument(request)

    override fun mutate(files: Map<String, ByteArray>, changes: List<AgentPersistentConfigChange>): Map<String, ByteArray> {
        val original = files.getValue(CONFIG_KEY).toString(Charsets.UTF_8)
        val root = yamlMap(files.getValue(CONFIG_KEY)).toMutableMap()
        val model = root.map("model").toMutableMap()
        val legacyProviders = root.list(LEGACY_PROVIDERS_KEY).mapNotNull { value ->
            (value as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { it.key.toString() to it.value }
        }.toMutableList()
        val providers = root.map(PROVIDERS_KEY).entries.associateTo(linkedMapOf()) { (id, value) ->
            id to ((value as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { it.key.toString() to it.value }
                ?: linkedMapOf())
        }
        var mcpChanged = false
        var skillsChanged = false
        var approvalsChanged = false
        var modelChanged = false
        var providersChanged = false
        var legacyProvidersChanged = false
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    if (change.modelId == null) model.remove("default") else model["default"] = change.modelId
                    modelChanged = true
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    model["provider"] = change.providerId
                    model["default"] = change.modelId
                    modelChanged = true
                }
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    val draft = change.provider
                    val entry = LinkedHashMap<String, Any?>(providers[draft.id].orEmpty()).apply {
                        this["api"] = draft.baseUrl.trim()
                        remove("base_url")
                        remove("url")
                        this["models"] = linkedMapOf<String, Any?>().also { models ->
                            draft.models.forEach { models[it.id.trim()] = linkedMapOf<String, Any?>() }
                        }
                        when (val credential = change.credential) {
                            AgentProviderCredentialChange.Keep -> Unit
                            is AgentProviderCredentialChange.Replace -> {
                                this["api_key"] = credential.secret
                                remove("key_env")
                            }
                            AgentProviderCredentialChange.Remove -> {
                                remove("api_key")
                                remove("key_env")
                            }
                        }
                    }
                    providers[draft.id] = entry
                    providersChanged = true
                    val removedLegacy = legacyProviders.removeAll { it["name"] == draft.id }
                    legacyProvidersChanged = legacyProvidersChanged || removedLegacy
                    if (model["default"] == null) {
                        model["provider"] = draft.id
                        model["default"] = draft.models.first().id.trim()
                        modelChanged = true
                    }
                }
                is AgentPersistentConfigChange.RemoveProvider -> {
                    providersChanged = providers.remove(change.providerId) != null || providersChanged
                    legacyProvidersChanged = legacyProviders.removeAll { it["name"] == change.providerId } || legacyProvidersChanged
                    if (model["provider"] == change.providerId) {
                        model.remove("provider")
                        model.remove("default")
                        modelChanged = true
                    }
                }
                is AgentPersistentConfigChange.SetPermissionProfile -> {
                    val approvals = root.mutableMap(APPROVALS_KEY)
                    approvals[MODE_KEY] = change.profileId
                    root[APPROVALS_KEY] = approvals
                    approvalsChanged = true
                }
                is AgentPersistentConfigChange.ConfigureMcpServer -> {
                    configureHermesMcp(root, change.server)
                    mcpChanged = true
                }
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val servers = root.mutableMap(MCP_SERVERS_KEY)
                    val server = servers.mutableMap(change.serverId)
                    server[ENABLED_KEY] = change.enabled
                    servers[change.serverId] = server
                    root[MCP_SERVERS_KEY] = servers
                    mcpChanged = true
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val servers = root.mutableMap(MCP_SERVERS_KEY)
                    servers.remove(change.serverId)
                    root[MCP_SERVERS_KEY] = servers
                    mcpChanged = true
                }
                is AgentPersistentConfigChange.SetSkillActivation -> {
                    setHermesSkillActivation(root, change.skillId, change.activation)
                    skillsChanged = true
                }
                else -> Unit
            }
        }
        val sections = linkedMapOf<String, Any?>()
        if (modelChanged) sections["model"] = model
        if (providersChanged) sections[PROVIDERS_KEY] = providers
        if (legacyProvidersChanged) sections[LEGACY_PROVIDERS_KEY] = legacyProviders
        if (mcpChanged) sections[MCP_SERVERS_KEY] = root[MCP_SERVERS_KEY]
        if (skillsChanged) sections[SKILLS_KEY] = root[SKILLS_KEY]
        if (approvalsChanged) sections[APPROVALS_KEY] = root[APPROVALS_KEY]
        val next = replaceYamlSections(
            original,
            sections,
        )
        return mapOf(CONFIG_KEY to next.toByteArray(Charsets.UTF_8))
    }

    private fun legacyProviderSummary(value: Any?): AgentProviderSummary? {
        val provider = value as? Map<*, *> ?: return null
        val id = provider.string("name")?.takeIf(String::isNotBlank) ?: return null
        return providerSummary(
            id = id,
            displayName = id,
            provider = provider,
            baseUrl = provider.string("base_url") ?: provider.string("api") ?: provider.string("url"),
        )
    }

    private fun modernProviderSummary(id: String, value: Any?): AgentProviderSummary? {
        val provider = value as? Map<*, *> ?: return null
        if (provider[ENABLED_KEY] == false) return null
        return providerSummary(
            id = id,
            displayName = provider.string("name")?.takeIf(String::isNotBlank) ?: id,
            provider = provider,
            baseUrl = provider.string("api") ?: provider.string("base_url") ?: provider.string("url"),
        )
    }

    private fun providerSummary(
        id: String,
        displayName: String,
        provider: Map<*, *>,
        baseUrl: String?,
    ): AgentProviderSummary {
        val models = when (val source = provider["models"]) {
            is Map<*, *> -> source.keys.mapNotNull { it as? String }
            is List<*> -> source.mapNotNull { item ->
                when (item) {
                    is String -> item
                    is Map<*, *> -> item.string("id") ?: item.string("name")
                    else -> null
                }
            }
            else -> listOfNotNull(provider.string("model") ?: provider.string("default_model"))
        }.filter(String::isNotBlank).distinct().map { AgentProviderModelSummary(it, it) }
        return AgentProviderSummary(
            id = id,
            displayName = displayName,
            baseUrl = baseUrl,
            models = models,
            credentialPresence = if (
                !provider.string("api_key").isNullOrBlank() ||
                !provider.string("key_env").isNullOrBlank() ||
                !provider.string("api_key_env").isNullOrBlank()
            ) AgentCredentialPresence.Present else AgentCredentialPresence.Missing,
        )
    }

    private fun hermesMcpServers(section: Map<String, Any?>): List<AgentMcpSummary> = section.mapNotNull { (id, value) ->
        val server = value.asStringMap() ?: return@mapNotNull null
        val command = server.string(COMMAND_KEY)
        val url = server.string(URL_KEY)
        val nativeTransport = server.string(TRANSPORT_KEY)
        val transport = when {
            !command.isNullOrBlank() -> AgentMcpTransport.Stdio
            url.isNullOrBlank() -> AgentMcpTransport.Unknown
            nativeTransport == SSE_TYPE -> AgentMcpTransport.Sse
            nativeTransport.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
            else -> AgentMcpTransport.Unknown
        }
        val enabled = server.boolean(ENABLED_KEY) ?: true
        AgentMcpSummary(
            id = id,
            kind = nativeTransport ?: if (command != null) STDIO_TYPE else "remote",
            enabled = enabled,
            transport = transport,
            command = command,
            arguments = server.stringList(ARGS_KEY),
            url = url,
            environmentReferences = hermesReferences(server.map(ENV_KEY)),
            headerReferences = hermesReferences(server.map(HEADERS_KEY)),
            scope = AgentConfigScope.User,
            allowedOperations = buildSet {
                if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                add(AgentMcpOperation.Remove)
            },
        )
    }.sortedBy(AgentMcpSummary::id)

    private fun hermesReferences(values: Map<String, Any?>): List<AgentMcpEnvironmentReference> = values.mapNotNull { (name, value) ->
        val variable = (value as? String)?.let(::hermesReferenceVariable) ?: return@mapNotNull null
        AgentMcpEnvironmentReference(name, variable)
    }.sortedBy(AgentMcpEnvironmentReference::name)

    private fun hermesReferenceVariable(value: String): String? =
        HERMES_ENV_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)
            ?: HERMES_BEARER_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)

    private fun configureHermesMcp(root: MutableMap<String, Any?>, draft: AgentMcpDraft) {
        val servers = root.mutableMap(MCP_SERVERS_KEY)
        val server = servers.mutableMap(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                server[COMMAND_KEY] = requireNotNull(draft.command).trim()
                server[ARGS_KEY] = draft.arguments.toList()
                server.remove(URL_KEY)
                server.remove(TRANSPORT_KEY)
                server.remove(HEADERS_KEY)
                mergeHermesReferences(server, ENV_KEY, draft.environmentReferences, authorizationBearer = false)
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                server[URL_KEY] = requireNotNull(draft.url).trim()
                if (draft.transport == AgentMcpTransport.Sse) server[TRANSPORT_KEY] = SSE_TYPE
                else server.remove(TRANSPORT_KEY)
                server.remove(COMMAND_KEY)
                server.remove(ARGS_KEY)
                server.remove(ENV_KEY)
                mergeHermesReferences(server, HEADERS_KEY, draft.headerReferences, authorizationBearer = true)
            }
            else -> error("已由 Hermes MCP 校验限制传输类型")
        }
        server[ENABLED_KEY] = draft.enabled
        servers[draft.id] = server
        root[MCP_SERVERS_KEY] = servers
    }

    private fun mergeHermesReferences(
        server: MutableMap<String, Any?>,
        field: String,
        references: List<AgentMcpEnvironmentReference>,
        authorizationBearer: Boolean,
    ) {
        val values = server.mutableMap(field)
        val desired = references.associateBy(AgentMcpEnvironmentReference::name)
        values.entries.toList().forEach { (name, value) ->
            if ((value as? String)?.let(::hermesReferenceVariable) != null && name !in desired) values.remove(name)
        }
        references.forEach { reference ->
            val current = (values[reference.name] as? String)?.let(::hermesReferenceVariable)
            if (current != reference.environmentVariable) {
                values[reference.name] = if (
                    authorizationBearer && reference.name.equals(AUTHORIZATION_HEADER, ignoreCase = true)
                ) {
                    "Bearer \${${reference.environmentVariable}}"
                } else {
                    "\${${reference.environmentVariable}}"
                }
            }
        }
        if (values.isEmpty()) server.remove(field) else server[field] = values
    }

    private fun hermesDisabledSkills(root: Map<String, Any?>): Set<String> = when (val disabled = root.map(SKILLS_KEY)[DISABLED_KEY]) {
        is String -> setOf(disabled.trim()).filterTo(linkedSetOf(), String::isNotBlank)
        is Iterable<*> -> disabled.mapNotNullTo(linkedSetOf()) { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
        else -> emptySet()
    }

    private fun setHermesSkillActivation(
        root: MutableMap<String, Any?>,
        skillId: String,
        activation: AgentSkillActivation,
    ) {
        if (skillDirectory.discover().none { it.id == skillId }) error("Skill 已不存在，请重新读取")
        val skills = root.mutableMap(SKILLS_KEY)
        val disabled = hermesDisabledSkills(root).toMutableSet()
        if (activation == AgentSkillActivation.Disabled) disabled += skillId else disabled -= skillId
        skills[DISABLED_KEY] = disabled.sorted()
        root[SKILLS_KEY] = skills
    }

    private fun validateHermesMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateHermesId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.arguments.any { value -> value.any(Char::isISOControl) }) {
                    output += problem("changes[$index].server.arguments", "MCP 参数不能包含控制字符")
                }
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Hermes 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS || draft.environmentReferences.size > MAX_MCP_ITEMS || draft.headerReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 参数、环境变量或 Header 数量过多")
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (reference.name.isBlank() || reference.name.any(Char::isISOControl) || !SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
    }

    private fun validateHermesId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? = runCatching { yamlMap(bytes); null }
        .getOrElse { "Hermes 原生 config.yaml 格式无效" }

    private fun yamlMap(bytes: ByteArray): Map<String, Any?> {
        val text = bytes.toString(Charsets.UTF_8)
        if (text.isBlank()) return emptyMap()
        val loaded = yaml.load<Any?>(text) ?: return emptyMap()
        require(loaded is Map<*, *>) { "Hermes config.yaml 顶层必须是对象" }
        return loaded.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value }
    }

    private fun replaceYamlSections(source: String, sections: LinkedHashMap<String, Any?>): String {
        var result = source.replace("\r\n", "\n").replace('\r', '\n')
        sections.forEach { (key, value) ->
            val dumped = yaml.dump(linkedMapOf(key to value)).trimEnd() + "\n"
            val range = yamlSectionRange(result, key)
            result = if (range == null) {
                result.trimEnd().let { existing -> if (existing.isBlank()) dumped else "$existing\n\n$dumped" }
            } else {
                result.substring(0, range.first) + dumped + result.substring(range.last + 1)
            }
        }
        return result.trimEnd() + "\n"
    }

    private fun yamlSectionRange(source: String, key: String): IntRange? {
        val linePattern = Regex("(?m)^${Regex.escape(key)}:(?:[ \\t].*)?(?:\\n|$)")
        val startMatch = linePattern.find(source) ?: return null
        val next = Regex("(?m)^[A-Za-z0-9_.-]+:(?:[ \\t].*)?(?:\\n|$)")
            .find(source, startMatch.range.last + 1)
        val endExclusive = next?.range?.first ?: source.length
        return startMatch.range.first until endExclusive
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String
    private fun Map<*, *>.boolean(key: String): Boolean? = this[key] as? Boolean
    private fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? Iterable<*>)
        ?.mapNotNull { it as? String }
        .orEmpty()
    private fun Any?.asStringMap(): Map<String, Any?>? = (this as? Map<*, *>)
        ?.entries
        ?.associateTo(linkedMapOf()) { it.key.toString() to it.value }
    private fun Map<String, Any?>.map(key: String): Map<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { it.key.toString() to it.value }.orEmpty()
    private fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()
    private fun MutableMap<String, Any?>.mutableMap(key: String): LinkedHashMap<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { it.key.toString() to it.value }
            ?: linkedMapOf()

    companion object {
        const val ADAPTER_ID = "hermes"
        private const val HERMES_CUSTOM_PROVIDER_PREFIX = "custom:"
        private const val CONFIG_KEY = "config"
        private const val HERMES_HOME_PATH = "/workspace/.kf/software/kite.hermes.core/home"
        private const val CONFIG_PATH = "$HERMES_HOME_PATH/config.yaml"
        private const val PROVIDERS_KEY = "providers"
        private const val LEGACY_PROVIDERS_KEY = "custom_providers"
        private const val MCP_SERVERS_KEY = "mcp_servers"
        private const val COMMAND_KEY = "command"
        private const val ARGS_KEY = "args"
        private const val ENV_KEY = "env"
        private const val URL_KEY = "url"
        private const val TRANSPORT_KEY = "transport"
        private const val HEADERS_KEY = "headers"
        private const val ENABLED_KEY = "enabled"
        private const val STDIO_TYPE = "stdio"
        private const val SSE_TYPE = "sse"
        private const val SKILLS_KEY = "skills"
        private const val DISABLED_KEY = "disabled"
        private const val APPROVALS_KEY = "approvals"
        private const val MODE_KEY = "mode"
        private const val DEFAULT_APPROVAL_MODE = "smart"
        private const val HERMES_MODE_DEFAULT = "default"
        private const val HERMES_MODE_ACCEPT_EDITS = "accept_edits"
        private const val HERMES_MODE_DONT_ASK = "dont_ask"
        private val HERMES_PERMISSION_MODE_IDS = setOf(
            HERMES_MODE_DEFAULT,
            HERMES_MODE_ACCEPT_EDITS,
            HERMES_MODE_DONT_ASK,
        )
        private const val SKILL_ROOT = "$HERMES_HOME_PATH/skills"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private val HERMES_PERMISSION_PROFILES = listOf(
            AgentPermissionProfileSummary(
                id = "manual",
                displayName = "手动（manual）",
                description = "危险命令始终请求用户确认",
                effect = AgentSessionConfigurationEffect.Immediate,
                level = AgentPermissionLevel.Approval,
            ),
            AgentPermissionProfileSummary(
                id = "smart",
                displayName = "智能（smart）",
                description = "低风险命令自动通过，高风险命令自动拒绝，不确定时请求确认",
                effect = AgentSessionConfigurationEffect.Immediate,
                level = AgentPermissionLevel.Smart,
            ),
            AgentPermissionProfileSummary(
                id = "off",
                displayName = "关闭审批（off）",
                description = "关闭危险命令审批；Hermes 的不可恢复操作硬阻止仍然生效",
                effect = AgentSessionConfigurationEffect.Immediate,
                level = AgentPermissionLevel.Full,
            ),
        )
        private val HERMES_PERMISSION_PROFILE_IDS = HERMES_PERMISSION_PROFILES.mapTo(linkedSetOf()) { it.id }
        private const val MAX_MCP_ITEMS = 64
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val HERMES_ENV_REFERENCE = Regex("\\$\\{(?:env:)?([A-Za-z_][A-Za-z0-9_]*)\\}")
        private val HERMES_BEARER_REFERENCE = Regex(
            "Bearer\\s+\\$\\{(?:env:)?([A-Za-z_][A-Za-z0-9_]*)\\}",
            RegexOption.IGNORE_CASE,
        )
        private val HERMES_SKILL_ACTIVATIONS = setOf(AgentSkillActivation.Enabled, AgentSkillActivation.Disabled)
    }
}
