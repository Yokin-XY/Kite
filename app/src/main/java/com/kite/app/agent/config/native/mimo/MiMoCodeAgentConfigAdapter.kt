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
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentWorkModeCatalog
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.agent.config.NativeAgentManagedOutputFormat
import com.kite.app.agent.config.mediatedSessionPermissionControl
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.net.URI

internal class MiMoCodeAgentConfigAdapter(
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
        roots = listOf(
            SKILL_ROOT,
            SINGULAR_SKILL_ROOT,
            AGENTS_SKILL_ROOT,
            CLAUDE_SKILL_ROOT,
            CODEX_SKILL_ROOT,
            OPENCODE_SKILL_ROOT,
        ),
        mutableRoots = setOf(SKILL_ROOT, SINGULAR_SKILL_ROOT),
    )

    override fun displayName(): String = "MiMo Code"

    override fun sessionPermissionControl(): AgentSessionPermissionControl =
        mediatedSessionPermissionControl(
            AgentPermissionLevel.Restricted,
            AgentPermissionLevel.Approval,
            AgentPermissionLevel.Full,
        )

    override fun bundledWorkModeCatalog(agentId: String): AgentWorkModeCatalog = AgentWorkModeCatalog(
        modes = MIMO_BUILT_IN_MODES.values.toList(),
        defaultModeId = MODE_BUILD,
    )

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> = modes.map { mode ->
        MIMO_BUILT_IN_MODES[mode.id] ?: mode
    }

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "mimo-global-agents",
            displayName = "MiMo 全局说明",
            fileName = "AGENTS.md",
            containerPath = GLOBAL_AGENTS_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "MiMo Code 跨工作区读取的全局说明",
            managedOutputFormat = NativeAgentManagedOutputFormat.CreateOrUpdate,
        ))
        projectCoreDocument(
            workspacePath,
            id = "mimo-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前工作目录优先匹配的项目说明",
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
        mcpTransports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.RemoteHttpOrSse),
        skillOperations = setOf(AgentSkillOperation.Import, AgentSkillOperation.Remove),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        val providers = root.getObject("provider")?.entries.orEmpty().mapNotNull { (id, value) ->
            val provider = value as? JsonObject ?: return@mapNotNull null
            val options = provider.getObject("options")
            val credential = if (!options?.string("apiKey").isNullOrBlank()) AgentCredentialPresence.Present
            else AgentCredentialPresence.Missing
            AgentProviderSummary(
                id = id,
                displayName = provider.string("name")?.takeIf(String::isNotBlank) ?: id,
                baseUrl = options?.string("baseURL"),
                models = provider.getObject("models")?.entries.orEmpty().map { (modelId, modelValue) ->
                    AgentProviderModelSummary(
                        modelId,
                        (modelValue as? JsonObject)?.string("name")?.takeIf(String::isNotBlank) ?: modelId
                    )
                }.sortedBy(AgentProviderModelSummary::id),
                credentialPresence = credential
            )
        }.sortedBy(AgentProviderSummary::id)
        val defaultModel = root.string("model")
        return NativeState(
            defaultModel,
            providers,
            overallCredential(providers),
            activeProviderId = defaultModel
                ?.substringBefore('/')
                ?.takeIf { active -> providers.any { it.id == active } },
            mcpServers = mimoMcpServers(root.getObject(MCP_KEY)),
            skills = skillDirectory.summaries(
                activation = { AgentSkillActivation.Enabled },
                activationOperations = emptySet(),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateMiMoMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateMiMoId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateMiMoId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateMiMoId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateMiMoId(index, "skillId", change.skillId, output)
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
        if (!root.containsKey("$" + "schema")) putPreserving(root, "$" + "schema", JsonPrimitive.of(SCHEMA_URL))
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> if (change.modelId == null) root.remove("model")
                    else putPreserving(root, "model", JsonPrimitive.of(change.modelId))
                is AgentPersistentConfigChange.SelectProvider ->
                    putPreserving(root, "model", JsonPrimitive.of(providerModelRef(change.providerId, change.modelId)))
                is AgentPersistentConfigChange.ConfigureProvider -> configure(root, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val section = root.objectCopy("provider")
                    section.remove(change.providerId)
                    putPreserving(root, "provider", section)
                    if (root.string("model")?.startsWith("${change.providerId}/") == true) root.remove("model")
                }
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureMiMoMcp(root, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val section = root.objectCopy(MCP_KEY)
                    val server = section.objectCopy(change.serverId)
                    putPreserving(server, ENABLED_KEY, JsonPrimitive.of(change.enabled))
                    putPreserving(section, change.serverId, server)
                    putPreserving(root, MCP_KEY, section)
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val section = root.objectCopy(MCP_KEY)
                    section.remove(change.serverId)
                    putPreserving(root, MCP_KEY, section)
                }
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root))
    }

    private fun mimoMcpServers(section: JsonObject?): List<AgentMcpSummary> = section?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val type = server.string(TYPE_KEY) ?: "unknown"
            val command = (server[COMMAND_KEY] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
                .orEmpty()
            val enabled = (server[ENABLED_KEY] as? JsonPrimitive)?.getValue() as? Boolean ?: true
            val transport = when (type) {
                LOCAL_TYPE -> AgentMcpTransport.Stdio
                REMOTE_TYPE -> AgentMcpTransport.RemoteHttpOrSse
                else -> AgentMcpTransport.Unknown
            }
            AgentMcpSummary(
                id = id,
                kind = type,
                enabled = enabled,
                transport = transport,
                command = command.firstOrNull(),
                arguments = command.drop(1),
                url = server.string(URL_KEY),
                environmentReferences = mimoReferences(server.getObject(ENVIRONMENT_KEY)),
                headerReferences = mimoReferences(server.getObject(HEADERS_KEY)),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun mimoReferences(values: JsonObject?): List<AgentMcpEnvironmentReference> = values?.entries
        .orEmpty()
        .mapNotNull { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String ?: return@mapNotNull null
            val variable = MIMO_ENV_REFERENCE.matchEntire(raw)?.groupValues?.get(1) ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }
        .sortedBy(AgentMcpEnvironmentReference::name)

    private fun configureMiMoMcp(root: JsonObject, draft: AgentMcpDraft) {
        val section = root.objectCopy(MCP_KEY)
        val server = section.objectCopy(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, TYPE_KEY, JsonPrimitive.of(LOCAL_TYPE))
                val command = JsonArray()
                command.add(JsonPrimitive.of(requireNotNull(draft.command).trim()))
                draft.arguments.forEach { command.add(JsonPrimitive.of(it)) }
                putPreserving(server, COMMAND_KEY, command)
                server.remove(URL_KEY)
                server.remove(HEADERS_KEY)
                mergeMiMoReferences(server, ENVIRONMENT_KEY, draft.environmentReferences)
            }
            AgentMcpTransport.RemoteHttpOrSse -> {
                putPreserving(server, TYPE_KEY, JsonPrimitive.of(REMOTE_TYPE))
                putPreserving(server, URL_KEY, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                server.remove(COMMAND_KEY)
                server.remove(ENVIRONMENT_KEY)
                mergeMiMoReferences(server, HEADERS_KEY, draft.headerReferences)
            }
            else -> error("已由 MiMo MCP 校验限制传输类型")
        }
        putPreserving(server, ENABLED_KEY, JsonPrimitive.of(draft.enabled))
        putPreserving(section, draft.id, server)
        putPreserving(root, MCP_KEY, section)
    }

    private fun mergeMiMoReferences(
        server: JsonObject,
        field: String,
        references: List<AgentMcpEnvironmentReference>,
    ) {
        val values = server.objectCopy(field)
        val desired = references.map(AgentMcpEnvironmentReference::name).toSet()
        values.entries.toList().forEach { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String
            if (raw != null && MIMO_ENV_REFERENCE.matches(raw) && name !in desired) values.remove(name)
        }
        references.forEach { reference ->
            putPreserving(values, reference.name, JsonPrimitive.of("{env:${reference.environmentVariable}}"))
        }
        if (values.isEmpty()) server.remove(field) else putPreserving(server, field, values)
    }

    private fun validateMiMoMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateMiMoId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
            }
            AgentMcpTransport.RemoteHttpOrSse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "MiMo Code 不支持这个 MCP 传输类型")
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

    private fun validateMiMoId(
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

    private fun configure(root: JsonObject, provider: AgentProviderDraft, credential: AgentProviderCredentialChange) {
        val providers = root.objectCopy("provider")
        val entry = providers.objectCopy(provider.id)
        provider.displayName?.takeIf(String::isNotBlank)?.let { putPreserving(entry, "name", JsonPrimitive.of(it.trim())) }
        if (!entry.containsKey("npm")) putPreserving(entry, "npm", JsonPrimitive.of("@ai-sdk/openai-compatible"))
        val options = entry.objectCopy("options")
        putPreserving(options, "baseURL", JsonPrimitive.of(provider.baseUrl.trim()))
        when (credential) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> putPreserving(options, "apiKey", JsonPrimitive.of(credential.secret))
            AgentProviderCredentialChange.Remove -> options.remove("apiKey")
        }
        putPreserving(entry, "options", options)
        val models = JsonObject()
        provider.models.forEach { model ->
            val modelEntry = JsonObject()
            if (model.displayName.isNotBlank() && model.displayName != model.id) {
                modelEntry.put("name", JsonPrimitive.of(model.displayName.trim()))
            }
            models.put(model.id.trim(), modelEntry)
        }
        putPreserving(entry, "models", models)
        putPreserving(providers, provider.id, entry)
        putPreserving(root, "provider", providers)
        if (root.string("model").isNullOrBlank()) {
            putPreserving(root, "model", JsonPrimitive.of(providerModelRef(provider.id, provider.models.first().id.trim())))
        }
    }

    companion object {
        const val ADAPTER_ID = "mimo-code"
        private const val MODE_BUILD = "build"
        private val MIMO_BUILT_IN_MODES = linkedMapOf(
            MODE_BUILD to AgentMode(MODE_BUILD, "构建", "按当前权限执行代码修改和工具操作"),
            "plan" to AgentMode("plan", "计划", "分析并制定计划，阻止计划文件之外的编辑"),
            "compose" to AgentMode("compose", "编排", "调用内置编排 Skill 组织复合任务；原生已标记为弃用"),
        )
        private const val CONFIG_KEY = "config"
        private const val CONFIG_PATH = "/root/.config/mimocode/mimocode.jsonc"
        private const val SCHEMA_URL = "https://mimo.xiaomi.com/mimocode/config.json"
        private const val MCP_KEY = "mcp"
        private const val TYPE_KEY = "type"
        private const val COMMAND_KEY = "command"
        private const val ENVIRONMENT_KEY = "environment"
        private const val URL_KEY = "url"
        private const val HEADERS_KEY = "headers"
        private const val ENABLED_KEY = "enabled"
        private const val LOCAL_TYPE = "local"
        private const val REMOTE_TYPE = "remote"
        private const val SKILL_ROOT = "/root/.config/mimocode/skills"
        private const val SINGULAR_SKILL_ROOT = "/root/.config/mimocode/skill"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val GLOBAL_AGENTS_PATH = "/root/.config/mimocode/AGENTS.md"
        private const val CLAUDE_SKILL_ROOT = "/root/.claude/skills"
        private const val CODEX_SKILL_ROOT = "/root/.codex/skills"
        private const val OPENCODE_SKILL_ROOT = "/root/.opencode/skills"
        private const val MAX_MCP_ITEMS = 64
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val MIMO_ENV_REFERENCE = Regex("\\{env:([A-Za-z_][A-Za-z0-9_]*)\\}")
    }
}
