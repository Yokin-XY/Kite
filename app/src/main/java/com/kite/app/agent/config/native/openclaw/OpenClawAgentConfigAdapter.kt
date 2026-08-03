package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
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
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.agent.config.NativeAgentManagedOutputFormat
import com.kite.app.agent.config.NativeAgentCoreDocumentStore
import com.kite.app.agent.config.native.openclaw.openClawReasoningControl
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File
import java.net.URI

internal class OpenClawAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : JanksonNativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(CONFIG_KEY to CONFIG_PATH),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = listOf(AGENTS_SKILL_ROOT, SKILL_ROOT),
        installRoot = SKILL_ROOT,
        configurationId = ::openClawSkillKey,
    )

    override fun displayName(): String = "OpenClaw"

    override fun reasoningControl(): AgentReasoningControl = openClawReasoningControl

    /** OpenClaw ACP 的 modes 与 thought_level 是同一组思考强度，不是独立工作模式。 */
    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> = emptyList()

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (option !is AgentConfigOption.Select || option.id != ELEVATED_LEVEL_CONFIG_ID) {
                return@mapNotNull option
            }
            // OpenClaw 明确把 ask 定义为 on 的别名；保留当前原生值，但只展示一个审批语义。
            val approvalValue = when {
                option.currentValue in ELEVATED_APPROVAL_VALUES -> option.currentValue
                option.choices.any { it.value == ELEVATED_ON_VALUE } -> ELEVATED_ON_VALUE
                option.choices.any { it.value == ELEVATED_ASK_VALUE } -> ELEVATED_ASK_VALUE
                else -> null
            }
            val levels = buildMap {
                put(ELEVATED_OFF_VALUE, AgentPermissionLevel.Restricted)
                approvalValue?.let { put(it, AgentPermissionLevel.Approval) }
                put(ELEVATED_FULL_VALUE, AgentPermissionLevel.Full)
            }
            val mappedChoices = option.choices.mapNotNull { choice ->
                val level = levels[choice.value] ?: return@mapNotNull null
                choice.copy(
                    name = level.displayName,
                    description = level.description,
                    permission = level,
                )
            }
            if (mappedChoices.size < 2 || mappedChoices.none { it.value == option.currentValue }) {
                null
            } else {
                option.copy(
                    name = "权限",
                    description = "控制 OpenClaw 当前会话的 elevated 执行；仍受工具策略、主机策略和允许名单约束",
                    category = AgentConfigCategory.Permission,
                    choices = mappedChoices,
                )
            }
        }

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> {
        val workspace = openClawWorkspacePath()
        fun document(
            id: String,
            name: String,
            fileName: String,
            semantics: AgentCoreDocumentSemantics,
            priority: String,
            managedOutputFormat: NativeAgentManagedOutputFormat = NativeAgentManagedOutputFormat.Disabled,
        ) = NativeAgentCoreDocumentSpec(
            id = id,
            displayName = name,
            fileName = fileName,
            containerPath = "$workspace/$fileName",
            scope = AgentConfigScope.Workspace,
            semantics = semantics,
            priorityDescription = priority,
            managedOutputFormat = managedOutputFormat,
        )
        return listOf(
            document(
                "openclaw-agents",
                "工作方式",
                "AGENTS.md",
                AgentCoreDocumentSemantics.SupplementalInstructions,
                "每次会话注入的工作区操作说明",
                NativeAgentManagedOutputFormat.CreateOrUpdate,
            ),
            document(
                "openclaw-soul",
                "人格与边界",
                "SOUL.md",
                AgentCoreDocumentSemantics.Persona,
                "定义 Agent 的语气、立场和行为边界",
            ),
            document(
                "openclaw-user",
                "用户资料",
                "USER.md",
                AgentCoreDocumentSemantics.UserProfile,
                "作为工作区用户资料随会话注入",
            ),
            document(
                "openclaw-identity",
                "Agent 身份",
                "IDENTITY.md",
                AgentCoreDocumentSemantics.Identity,
                "定义 Agent 的名称、形象和身份表达",
            ),
        )
    }

    private fun openClawWorkspacePath(): String {
        val configFile = projection.resolve(CONFIG_PATH)
            ?.readFile
            ?.takeIf(File::isFile)
        val configured = configFile
            ?.let { file -> parse(file.readBytes()) }
            ?.getObject("agents")
            ?.getObject("defaults")
            ?.string("workspace")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val expanded = when {
            configured == null -> DEFAULT_WORKSPACE_PATH
            configured == "~" -> "/root"
            configured.startsWith("~/") -> "/root/${configured.removePrefix("~/")}"
            configured.startsWith("\$HOME/") -> "/root/${configured.removePrefix("\$HOME/")}"
            configured.startsWith("\${HOME}/") -> "/root/${configured.removePrefix("\${HOME}/")}"
            else -> configured
        }
        return requireNotNull(NativeAgentCoreDocumentStore.normalizeContainerPath(expanded)) {
            "OpenClaw workspace 不是安全的容器绝对路径"
        }
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
        val root = parse(files.getValue(CONFIG_KEY))
        val providers = root.getObject("models")?.getObject("providers")?.entries.orEmpty().mapNotNull { (id, value) ->
            val provider = value as? JsonObject ?: return@mapNotNull null
            val apiKey = provider["apiKey"]
            AgentProviderSummary(
                id = id,
                displayName = id,
                baseUrl = provider.string("baseUrl"),
                models = (provider["models"] as? JsonArray).orEmptyObjects().mapNotNull { model ->
                    model.string("id")?.takeIf(String::isNotBlank)?.let { modelId ->
                        AgentProviderModelSummary(modelId, model.string("name")?.takeIf(String::isNotBlank) ?: modelId)
                    }
                },
                credentialPresence = if (apiKey != null) AgentCredentialPresence.Present else AgentCredentialPresence.Missing
            )
        }.sortedBy(AgentProviderSummary::id)
        val modelValue = root.getObject("agents")?.getObject("defaults")?.get("model")
        val defaultModel = when (modelValue) {
            is JsonPrimitive -> modelValue.getValue() as? String
            is JsonObject -> modelValue.string("primary")
            else -> null
        }
        return NativeState(
            defaultModel,
            providers,
            overallCredential(providers),
            activeProviderId = defaultModel
                ?.substringBefore('/')
                ?.takeIf { active -> providers.any { it.id == active } },
            mcpServers = openClawMcpServers(root.getObject(MCP_KEY)?.getObject(MCP_SERVERS_KEY)),
            skills = skillDirectory.summaries(
                activation = { entry -> openClawSkillActivation(root, entry.configurationId) },
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
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateOpenClawMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateOpenClawId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateOpenClawId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateOpenClawId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateOpenClawId(index, "skillId", change.skillId, output)
                if (change.activation !in OPENCLAW_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "OpenClaw 只支持启用或停用 Skill")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateOpenClawId(index, "skillId", change.skillId, output)
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
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> setDefault(root, change.modelId)
                is AgentPersistentConfigChange.SelectProvider ->
                    setDefault(root, providerModelRef(change.providerId, change.modelId))
                is AgentPersistentConfigChange.ConfigureProvider -> configure(root, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> removeProvider(root, change.providerId)
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureOpenClawMcp(root, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val mcp = root.objectCopy(MCP_KEY)
                    val servers = mcp.objectCopy(MCP_SERVERS_KEY)
                    val server = servers.objectCopy(change.serverId)
                    putPreserving(server, ENABLED_KEY, JsonPrimitive.of(change.enabled))
                    putPreserving(servers, change.serverId, server)
                    putPreserving(mcp, MCP_SERVERS_KEY, servers)
                    putPreserving(root, MCP_KEY, mcp)
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val mcp = root.objectCopy(MCP_KEY)
                    val servers = mcp.objectCopy(MCP_SERVERS_KEY)
                    servers.remove(change.serverId)
                    putPreserving(mcp, MCP_SERVERS_KEY, servers)
                    putPreserving(root, MCP_KEY, mcp)
                }
                is AgentPersistentConfigChange.SetSkillActivation -> setOpenClawSkillActivation(
                    root,
                    change.skillId,
                    change.activation,
                )
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root))
    }

    private fun openClawMcpServers(section: JsonObject?): List<AgentMcpSummary> = section?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val command = server.string(COMMAND_KEY)
            val url = server.string(URL_KEY)
            val nativeTransport = server.string(TRANSPORT_KEY)
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                url.isNullOrBlank() -> AgentMcpTransport.Unknown
                nativeTransport == SSE_TYPE -> AgentMcpTransport.Sse
                nativeTransport == STREAMABLE_HTTP_TYPE || nativeTransport.isNullOrBlank() ->
                    AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.Unknown
            }
            val enabled = (server[ENABLED_KEY] as? JsonPrimitive)?.getValue() as? Boolean ?: true
            AgentMcpSummary(
                id = id,
                kind = nativeTransport ?: if (command != null) STDIO_TYPE else "remote",
                enabled = enabled,
                transport = transport,
                command = command,
                arguments = (server[ARGS_KEY] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
                    .orEmpty(),
                url = url,
                environmentReferences = openClawReferences(server.getObject(ENV_KEY)),
                headerReferences = openClawReferences(server.getObject(HEADERS_KEY)),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun openClawReferences(values: JsonObject?): List<AgentMcpEnvironmentReference> = values?.entries
        .orEmpty()
        .mapNotNull { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String ?: return@mapNotNull null
            val variable = openClawReferenceVariable(raw) ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }
        .sortedBy(AgentMcpEnvironmentReference::name)

    private fun openClawReferenceVariable(value: String): String? =
        OPENCLAW_ENV_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)
            ?: OPENCLAW_BEARER_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)

    private fun configureOpenClawMcp(root: JsonObject, draft: AgentMcpDraft) {
        val mcp = root.objectCopy(MCP_KEY)
        val servers = mcp.objectCopy(MCP_SERVERS_KEY)
        val server = servers.objectCopy(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, TRANSPORT_KEY, JsonPrimitive.of(STDIO_TYPE))
                putPreserving(server, COMMAND_KEY, JsonPrimitive.of(requireNotNull(draft.command).trim()))
                val arguments = JsonArray()
                draft.arguments.forEach { arguments.add(JsonPrimitive.of(it)) }
                putPreserving(server, ARGS_KEY, arguments)
                server.remove(URL_KEY)
                server.remove(HEADERS_KEY)
                mergeOpenClawReferences(server, ENV_KEY, draft.environmentReferences, authorizationBearer = false)
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                putPreserving(
                    server,
                    TRANSPORT_KEY,
                    JsonPrimitive.of(
                        if (draft.transport == AgentMcpTransport.Sse) SSE_TYPE else STREAMABLE_HTTP_TYPE,
                    ),
                )
                putPreserving(server, URL_KEY, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                server.remove(COMMAND_KEY)
                server.remove(ARGS_KEY)
                server.remove(ENV_KEY)
                server.remove(CWD_KEY)
                mergeOpenClawReferences(server, HEADERS_KEY, draft.headerReferences, authorizationBearer = true)
            }
            else -> error("已由 OpenClaw MCP 校验限制传输类型")
        }
        putPreserving(server, ENABLED_KEY, JsonPrimitive.of(draft.enabled))
        putPreserving(servers, draft.id, server)
        putPreserving(mcp, MCP_SERVERS_KEY, servers)
        putPreserving(root, MCP_KEY, mcp)
    }

    private fun mergeOpenClawReferences(
        server: JsonObject,
        field: String,
        references: List<AgentMcpEnvironmentReference>,
        authorizationBearer: Boolean,
    ) {
        val values = server.objectCopy(field)
        val desired = references.associateBy(AgentMcpEnvironmentReference::name)
        values.entries.toList().forEach { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String
            val currentVariable = raw?.let(::openClawReferenceVariable)
            if (currentVariable != null && name !in desired) values.remove(name)
        }
        references.forEach { reference ->
            val current = (values[reference.name] as? JsonPrimitive)?.getValue() as? String
            if (current?.let(::openClawReferenceVariable) != reference.environmentVariable) {
                val next = if (authorizationBearer && reference.name.equals(AUTHORIZATION_HEADER, ignoreCase = true)) {
                    "Bearer \${${reference.environmentVariable}}"
                } else {
                    "\${${reference.environmentVariable}}"
                }
                putPreserving(values, reference.name, JsonPrimitive.of(next))
            }
        }
        if (values.isEmpty()) server.remove(field) else putPreserving(server, field, values)
    }

    private fun validateOpenClawMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateOpenClawId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "OpenClaw 不支持这个 MCP 传输类型")
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

    private fun validateOpenClawId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value) || value == RESERVED_MCP_ID) {
            output += problem("changes[$index].$field", "ID 格式无效")
        }
    }

    private fun openClawSkillActivation(root: JsonObject, configurationId: String): AgentSkillActivation {
        val entry = root.getObject(SKILLS_KEY)?.getObject(SKILL_ENTRIES_KEY)?.getObject(configurationId)
        val enabled = (entry?.get(ENABLED_KEY) as? JsonPrimitive)?.getValue() as? Boolean ?: true
        return if (enabled) AgentSkillActivation.Enabled else AgentSkillActivation.Disabled
    }

    private fun setOpenClawSkillActivation(
        root: JsonObject,
        skillId: String,
        activation: AgentSkillActivation,
    ) {
        val entry = skillDirectory.discover().firstOrNull { it.id == skillId }
            ?: error("Skill 已不存在，请重新读取")
        val skills = root.objectCopy(SKILLS_KEY)
        val entries = skills.objectCopy(SKILL_ENTRIES_KEY)
        val config = entries.objectCopy(entry.configurationId)
        putPreserving(config, ENABLED_KEY, JsonPrimitive.of(activation == AgentSkillActivation.Enabled))
        putPreserving(entries, entry.configurationId, config)
        putPreserving(skills, SKILL_ENTRIES_KEY, entries)
        putPreserving(root, SKILLS_KEY, skills)
    }

    private fun openClawSkillKey(file: File, fallback: String): String = runCatching {
        val header = file.useLines { lines -> lines.take(MAX_SKILL_HEADER_LINES).joinToString("\n") }
        (OPENCLAW_JSON_SKILL_KEY.find(header) ?: OPENCLAW_YAML_SKILL_KEY.find(header))
            ?.groupValues
            ?.get(1)
            ?.takeIf(::isSafeNativeId)
            ?: fallback
    }.getOrDefault(fallback)

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    private fun configure(root: JsonObject, provider: AgentProviderDraft, credential: AgentProviderCredentialChange) {
        val modelsRoot = root.objectCopy("models")
        if (!modelsRoot.containsKey("mode")) putPreserving(modelsRoot, "mode", JsonPrimitive.of("merge"))
        val providers = modelsRoot.objectCopy("providers")
        val entry = providers.objectCopy(provider.id)
        putPreserving(entry, "baseUrl", JsonPrimitive.of(provider.baseUrl.trim()))
        if (!entry.containsKey("api")) putPreserving(entry, "api", JsonPrimitive.of("openai-completions"))
        when (credential) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> putPreserving(entry, "apiKey", JsonPrimitive.of(credential.secret))
            AgentProviderCredentialChange.Remove -> entry.remove("apiKey")
        }
        val models = JsonArray()
        provider.models.forEach { model ->
            models.add(JsonObject().also { value ->
                value.put("id", JsonPrimitive.of(model.id.trim()))
                if (model.displayName.isNotBlank() && model.displayName != model.id) {
                    value.put("name", JsonPrimitive.of(model.displayName.trim()))
                }
            })
        }
        putPreserving(entry, "models", models)
        putPreserving(providers, provider.id, entry)
        putPreserving(modelsRoot, "providers", providers)
        putPreserving(root, "models", modelsRoot)

        val catalog = defaults(root).objectCopy("models")
        catalog.keys.filter { it.startsWith("${provider.id}/") }.toList().forEach(catalog::remove)
        provider.models.forEach { model ->
            val catalogEntry = JsonObject()
            if (model.displayName.isNotBlank() && model.displayName != model.id) {
                catalogEntry.put("alias", JsonPrimitive.of(model.displayName.trim()))
            }
            catalog.put(providerModelRef(provider.id, model.id.trim()), catalogEntry)
        }
        val defaults = defaults(root)
        putPreserving(defaults, "models", catalog)
        setDefaults(root, defaults)
        if (currentDefault(root).isNullOrBlank()) setDefault(root, providerModelRef(provider.id, provider.models.first().id.trim()))
    }

    private fun removeProvider(root: JsonObject, providerId: String) {
        val modelsRoot = root.objectCopy("models")
        val providers = modelsRoot.objectCopy("providers")
        providers.remove(providerId)
        putPreserving(modelsRoot, "providers", providers)
        putPreserving(root, "models", modelsRoot)
        val defaults = defaults(root)
        val catalog = defaults.objectCopy("models")
        catalog.keys.filter { it.startsWith("$providerId/") }.toList().forEach(catalog::remove)
        putPreserving(defaults, "models", catalog)
        setDefaults(root, defaults)
        if (currentDefault(root)?.startsWith("$providerId/") == true) setDefault(root, null)
    }

    private fun currentDefault(root: JsonObject): String? {
        val value = defaults(root)["model"]
        return when (value) {
            is JsonPrimitive -> value.getValue() as? String
            is JsonObject -> value.string("primary")
            else -> null
        }
    }

    private fun setDefault(root: JsonObject, modelId: String?) {
        val defaults = defaults(root)
        if (modelId == null) {
            defaults.remove("model")
        } else {
            val model = (defaults["model"] as? JsonObject)?.clone() ?: JsonObject()
            putPreserving(model, "primary", JsonPrimitive.of(modelId))
            putPreserving(defaults, "model", model)
        }
        setDefaults(root, defaults)
    }

    private fun defaults(root: JsonObject): JsonObject = root.getObject("agents")?.getObject("defaults")?.clone() ?: JsonObject()

    private fun setDefaults(root: JsonObject, defaults: JsonObject) {
        val agents = root.objectCopy("agents")
        putPreserving(agents, "defaults", defaults)
        putPreserving(root, "agents", agents)
    }

    private fun JsonArray?.orEmptyObjects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject }.orEmpty()

    companion object {
        const val ADAPTER_ID = "openclaw"
        private const val CONFIG_KEY = "config"
        private const val CONFIG_PATH = "/root/.openclaw/openclaw.json"
        private const val DEFAULT_WORKSPACE_PATH = "/root/.openclaw/workspace"
        private const val MCP_KEY = "mcp"
        private const val MCP_SERVERS_KEY = "servers"
        private const val COMMAND_KEY = "command"
        private const val ARGS_KEY = "args"
        private const val ENV_KEY = "env"
        private const val CWD_KEY = "cwd"
        private const val URL_KEY = "url"
        private const val TRANSPORT_KEY = "transport"
        private const val HEADERS_KEY = "headers"
        private const val ENABLED_KEY = "enabled"
        private const val STDIO_TYPE = "stdio"
        private const val STREAMABLE_HTTP_TYPE = "streamable-http"
        private const val SSE_TYPE = "sse"
        private const val SKILLS_KEY = "skills"
        private const val SKILL_ENTRIES_KEY = "entries"
        private const val SKILL_ROOT = "/root/.openclaw/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val RESERVED_MCP_ID = "__proto__"
        private const val MAX_MCP_ITEMS = 64
        private const val MAX_SKILL_HEADER_LINES = 160
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val OPENCLAW_ENV_REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
        private val OPENCLAW_BEARER_REFERENCE = Regex(
            "Bearer\\s+\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}",
            RegexOption.IGNORE_CASE,
        )
        private const val ELEVATED_LEVEL_CONFIG_ID = "elevated_level"
        private const val ELEVATED_OFF_VALUE = "off"
        private const val ELEVATED_ON_VALUE = "on"
        private const val ELEVATED_ASK_VALUE = "ask"
        private const val ELEVATED_FULL_VALUE = "full"
        private val ELEVATED_APPROVAL_VALUES = setOf(ELEVATED_ON_VALUE, ELEVATED_ASK_VALUE)
        private val OPENCLAW_JSON_SKILL_KEY = Regex(
            "[\\\"']skillKey[\\\"']\\s*:\\s*[\\\"']([A-Za-z0-9][A-Za-z0-9._-]{0,127})[\\\"']",
        )
        private val OPENCLAW_YAML_SKILL_KEY = Regex(
            "(?m)^\\s*skillKey\\s*:\\s*[\\\"']?([A-Za-z0-9][A-Za-z0-9._-]{0,127})[\\\"']?\\s*$",
        )
        private val OPENCLAW_SKILL_ACTIVATIONS = setOf(AgentSkillActivation.Enabled, AgentSkillActivation.Disabled)
    }
}
