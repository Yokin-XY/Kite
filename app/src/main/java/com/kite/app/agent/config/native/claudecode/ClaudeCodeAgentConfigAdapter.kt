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
import com.kite.app.agent.contract.AgentPermissionLevel
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
import com.kite.app.agent.config.native.claudecode.claudeCodeReasoningControl
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.net.URI

internal class ClaudeCodeAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : JanksonNativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(CONFIG_KEY to CONFIG_PATH, STATE_KEY to STATE_PATH),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val skillDirectory = NativeAgentSkillDirectory(projection::resolve, listOf(SKILL_ROOT))

    override fun displayName(): String = "Claude Code"

    override fun reasoningControl(): AgentReasoningControl = claudeCodeReasoningControl

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (
                option !is AgentConfigOption.Select ||
                option.id != NATIVE_MODE_OPTION_ID ||
                option.category != AgentConfigCategory.Mode
            ) return@mapNotNull option
            val mappedChoices = option.choices.mapNotNull { choice ->
                val level = CLAUDE_PERMISSION_LEVELS[choice.value] ?: return@mapNotNull null
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
                    description = "Claude Code 当前会话真实提供的权限模式",
                    category = AgentConfigCategory.Permission,
                    choices = mappedChoices,
                )
            }
        }

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "claude-global",
            displayName = "Claude 全局说明",
            fileName = "CLAUDE.md",
            containerPath = GLOBAL_CLAUDE_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "所有 Claude Code 项目都会加载的用户级说明",
            managedOutputFormat = NativeAgentManagedOutputFormat.CreateOrUpdate,
        ))
        projectCoreDocument(
            workspacePath,
            id = "claude-project",
            displayName = "当前项目说明",
            fileName = "CLAUDE.md",
            priorityDescription = "项目共享说明，在用户级说明之后加入上下文",
        )?.let(::add)
        projectCoreDocument(
            workspacePath,
            id = "claude-local",
            displayName = "当前项目个人说明",
            fileName = "CLAUDE.local.md",
            priorityDescription = "当前项目的个人补充，在项目共享说明之后加入上下文",
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
        mcpOperations = setOf(AgentMcpOperation.Create, AgentMcpOperation.Edit, AgentMcpOperation.Remove),
        mcpTransports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.ManualOnly,
            AgentSkillOperation.Disable,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        val env = root.getObject("env")
        val baseUrl = env?.string("ANTHROPIC_BASE_URL")
        val model = root.string("model")
        val modelIds = (root["availableModels"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOfNotNull(model) }
        val hasCredential = !env?.string("ANTHROPIC_API_KEY").isNullOrBlank() ||
            !env?.string("ANTHROPIC_AUTH_TOKEN").isNullOrBlank()
        val provider = if (baseUrl.isNullOrBlank() && modelIds.isEmpty() && !hasCredential) emptyList() else listOf(
            AgentProviderSummary(
                id = providerId(baseUrl),
                displayName = providerName(baseUrl),
                baseUrl = baseUrl ?: "https://api.anthropic.com",
                models = modelIds.map { AgentProviderModelSummary(it, it) },
                credentialPresence = if (hasCredential) AgentCredentialPresence.Present else AgentCredentialPresence.Missing
            )
        )
        val state = parse(files.getValue(STATE_KEY))
        return NativeState(
            model,
            provider,
            overallCredential(provider),
            activeProviderId = provider.firstOrNull()?.id?.takeIf { !model.isNullOrBlank() },
            mcpServers = claudeMcpServers(state),
            skills = skillDirectory.summaries(
                activation = { entry -> claudeSkillActivation(root, entry.id) },
                activationOperations = setOf(
                    AgentSkillOperation.Enable,
                    AgentSkillOperation.ManualOnly,
                    AgentSkillOperation.Disable,
                ),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateClaudeMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateClaudeId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateClaudeId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateClaudeId(index, "skillId", change.skillId, output)
                if (change.activation !in CLAUDE_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "Claude Code 不支持这个 Skill 状态")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateClaudeId(index, "skillId", change.skillId, output)
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
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        val state = parse(files.getValue(STATE_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> if (change.modelId == null) root.remove("model")
                    else putPreserving(root, "model", JsonPrimitive.of(change.modelId))
                is AgentPersistentConfigChange.SelectProvider ->
                    putPreserving(root, "model", JsonPrimitive.of(change.modelId))
                is AgentPersistentConfigChange.ConfigureProvider -> configure(root, state, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> removeProvider(root)
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureClaudeMcp(state, change.server)
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val servers = state.objectCopy(MCP_KEY)
                    servers.remove(change.serverId)
                    putPreserving(state, MCP_KEY, servers)
                }
                is AgentPersistentConfigChange.SetSkillActivation -> {
                    val overrides = root.objectCopy(SKILL_OVERRIDES_KEY)
                    putPreserving(
                        overrides,
                        change.skillId,
                        JsonPrimitive.of(
                            when (change.activation) {
                                AgentSkillActivation.Enabled -> "on"
                                AgentSkillActivation.ManualOnly -> "user-invocable-only"
                                AgentSkillActivation.Disabled -> "off"
                                else -> error("已由 Claude Code Skill 校验限制状态")
                            },
                        ),
                    )
                    putPreserving(root, SKILL_OVERRIDES_KEY, overrides)
                }
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root), STATE_KEY to serialize(state))
    }

    private fun claudeMcpServers(state: JsonObject): List<AgentMcpSummary> = state.getObject(MCP_KEY)?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val type = server.string("type")
            val command = server.string("command")
            val url = server.string("url")
            val transport = when (type) {
                "stdio" -> AgentMcpTransport.Stdio
                "http" -> AgentMcpTransport.StreamableHttp
                "sse" -> AgentMcpTransport.Sse
                else -> when {
                    !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                    !url.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                    else -> AgentMcpTransport.Unknown
                }
            }
            AgentMcpSummary(
                id = id,
                kind = type ?: "unknown",
                enabled = true,
                transport = transport,
                command = command,
                arguments = (server["args"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }.orEmpty(),
                url = url,
                environmentReferences = claudeReferences(server.getObject("env")),
                headerReferences = claudeReferences(server.getObject("headers")),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun claudeReferences(values: JsonObject?): List<AgentMcpEnvironmentReference> = values?.entries
        .orEmpty()
        .mapNotNull { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String ?: return@mapNotNull null
            val variable = CLAUDE_ENV_REFERENCE.matchEntire(raw)?.groupValues?.get(1) ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }
        .sortedBy(AgentMcpEnvironmentReference::name)

    private fun claudeSkillActivation(settings: JsonObject, skillId: String): AgentSkillActivation =
        when (settings.getObject(SKILL_OVERRIDES_KEY)?.string(skillId)) {
            "off" -> AgentSkillActivation.Disabled
            "user-invocable-only" -> AgentSkillActivation.ManualOnly
            "on", "name-only", null -> AgentSkillActivation.Enabled
            else -> AgentSkillActivation.Unknown
        }

    private fun configureClaudeMcp(state: JsonObject, draft: AgentMcpDraft) {
        val servers = state.objectCopy(MCP_KEY)
        val server = servers.objectCopy(draft.id)
        listOf("type", "command", "args", "env", "url", "headers").forEach(server::remove)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, "type", JsonPrimitive.of("stdio"))
                putPreserving(server, "command", JsonPrimitive.of(requireNotNull(draft.command).trim()))
                val arguments = JsonArray()
                draft.arguments.forEach { arguments.add(JsonPrimitive.of(it)) }
                putPreserving(server, "args", arguments)
                if (draft.environmentReferences.isNotEmpty()) {
                    val env = JsonObject()
                    draft.environmentReferences.forEach { reference ->
                        env.put(reference.name, JsonPrimitive.of("${'$'}{${reference.environmentVariable}}"))
                    }
                    putPreserving(server, "env", env)
                }
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                putPreserving(
                    server,
                    "type",
                    JsonPrimitive.of(if (draft.transport == AgentMcpTransport.Sse) "sse" else "http"),
                )
                putPreserving(server, "url", JsonPrimitive.of(requireNotNull(draft.url).trim()))
                if (draft.headerReferences.isNotEmpty()) {
                    val headers = JsonObject()
                    draft.headerReferences.forEach { reference ->
                        headers.put(reference.name, JsonPrimitive.of("${'$'}{${reference.environmentVariable}}"))
                    }
                    putPreserving(server, "headers", headers)
                }
            }
            else -> error("已由 Claude Code MCP 校验限制传输类型")
        }
        putPreserving(servers, draft.id, server)
        putPreserving(state, MCP_KEY, servers)
    }

    private fun validateClaudeMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateClaudeId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Claude Code 不支持这个 MCP 传输类型")
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

    private fun validateClaudeId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private fun configure(
        root: JsonObject,
        state: JsonObject,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
    ) {
        val env = root.objectCopy("env")
        val previousBaseUrl = env.string("ANTHROPIC_BASE_URL")
        val baseUrl = claudeProviderBaseUrl(provider)
        val sameEndpoint = sameClaudeEndpoint(previousBaseUrl, baseUrl)
        val isZhipuCodingPlan = provider.id == ZHIPU_CODING_PLAN_PROVIDER_ID
        putPreserving(env, "ANTHROPIC_BASE_URL", JsonPrimitive.of(baseUrl))
        val first = provider.models.first()
        if (isZhipuCodingPlan) {
            listOf(
                "CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY",
                "ANTHROPIC_CUSTOM_MODEL_OPTION",
                "ANTHROPIC_CUSTOM_MODEL_OPTION_NAME",
                "ANTHROPIC_CUSTOM_MODEL_OPTION_DESCRIPTION",
            ).forEach(env::remove)
            when (credential) {
                AgentProviderCredentialChange.Keep -> {
                    val existing = if (sameEndpoint) {
                        env.string("ANTHROPIC_AUTH_TOKEN") ?: env.string("ANTHROPIC_API_KEY")
                    } else {
                        null
                    }
                    if (!existing.isNullOrBlank()) {
                        putPreserving(env, "ANTHROPIC_AUTH_TOKEN", JsonPrimitive.of(existing))
                    } else {
                        env.remove("ANTHROPIC_AUTH_TOKEN")
                    }
                    env.remove("ANTHROPIC_API_KEY")
                }
                is AgentProviderCredentialChange.Replace -> {
                    putPreserving(env, "ANTHROPIC_AUTH_TOKEN", JsonPrimitive.of(credential.secret))
                    env.remove("ANTHROPIC_API_KEY")
                }
                AgentProviderCredentialChange.Remove -> {
                    env.remove("ANTHROPIC_API_KEY")
                    env.remove("ANTHROPIC_AUTH_TOKEN")
                }
            }
            listOf(
                "ANTHROPIC_DEFAULT_HAIKU_MODEL",
                "ANTHROPIC_DEFAULT_SONNET_MODEL",
                "ANTHROPIC_DEFAULT_OPUS_MODEL",
            ).forEach { key -> putPreserving(env, key, JsonPrimitive.of(first.id.trim())) }
            putPreserving(env, "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", JsonPrimitive.of("1"))
            putPreserving(env, "API_TIMEOUT_MS", JsonPrimitive.of("3000000"))
            putPreserving(state, "hasCompletedOnboarding", JsonPrimitive.of(true))
        } else {
            listOf(
                "ANTHROPIC_DEFAULT_HAIKU_MODEL",
                "ANTHROPIC_DEFAULT_SONNET_MODEL",
                "ANTHROPIC_DEFAULT_OPUS_MODEL",
                "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC",
                "API_TIMEOUT_MS",
            ).forEach(env::remove)
            putPreserving(env, "CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY", JsonPrimitive.of("1"))
            putPreserving(env, "ANTHROPIC_CUSTOM_MODEL_OPTION", JsonPrimitive.of(first.id.trim()))
            if (first.displayName.isNotBlank()) {
                putPreserving(env, "ANTHROPIC_CUSTOM_MODEL_OPTION_NAME", JsonPrimitive.of(first.displayName.trim()))
            }
            when (credential) {
                AgentProviderCredentialChange.Keep -> if (!sameEndpoint) {
                    env.remove("ANTHROPIC_API_KEY")
                    env.remove("ANTHROPIC_AUTH_TOKEN")
                }
                is AgentProviderCredentialChange.Replace -> {
                    putPreserving(env, "ANTHROPIC_API_KEY", JsonPrimitive.of(credential.secret))
                    env.remove("ANTHROPIC_AUTH_TOKEN")
                }
                AgentProviderCredentialChange.Remove -> {
                    env.remove("ANTHROPIC_API_KEY")
                    env.remove("ANTHROPIC_AUTH_TOKEN")
                }
            }
        }
        putPreserving(root, "env", env)
        val models = JsonArray()
        provider.models.forEach { models.add(JsonPrimitive.of(it.id.trim())) }
        putPreserving(root, "availableModels", models)
        if (provider.models.none { it.id.trim() == root.string("model") }) {
            putPreserving(root, "model", JsonPrimitive.of(first.id.trim()))
        }
    }

    private fun removeProvider(root: JsonObject) {
        val env = root.objectCopy("env")
        listOf(
            "ANTHROPIC_BASE_URL",
            "ANTHROPIC_API_KEY",
            "ANTHROPIC_AUTH_TOKEN",
            "CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY",
            "ANTHROPIC_CUSTOM_MODEL_OPTION",
            "ANTHROPIC_CUSTOM_MODEL_OPTION_NAME",
            "ANTHROPIC_CUSTOM_MODEL_OPTION_DESCRIPTION",
            "ANTHROPIC_DEFAULT_HAIKU_MODEL",
            "ANTHROPIC_DEFAULT_SONNET_MODEL",
            "ANTHROPIC_DEFAULT_OPUS_MODEL",
            "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC",
            "API_TIMEOUT_MS",
        ).forEach(env::remove)
        putPreserving(root, "env", env)
        root.remove("availableModels")
        root.remove("model")
    }

    private fun providerId(baseUrl: String?): String = if (isZhipuCodingPlanEndpoint(baseUrl)) {
        ZHIPU_CODING_PLAN_PROVIDER_ID
    } else runCatching {
        URI(baseUrl ?: "https://api.anthropic.com").host
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9.-]"), "-")
            ?.take(64)
    }.getOrNull().orEmpty().ifBlank { "active" }

    private fun providerName(baseUrl: String?): String = if (isZhipuCodingPlanEndpoint(baseUrl)) {
        "智谱 GLM Coding Plan"
    } else runCatching {
        URI(baseUrl ?: "https://api.anthropic.com").host
    }.getOrNull().orEmpty().ifBlank { "Claude 当前供应商" }

    private fun claudeProviderBaseUrl(provider: AgentProviderDraft): String =
        if (provider.id == ZHIPU_CODING_PLAN_PROVIDER_ID) ZHIPU_CODING_PLAN_CLAUDE_BASE_URL
        else provider.baseUrl.trim()

    private fun isZhipuCodingPlanEndpoint(baseUrl: String?): Boolean =
        sameClaudeEndpoint(baseUrl, ZHIPU_CODING_PLAN_CLAUDE_BASE_URL)

    private fun sameClaudeEndpoint(left: String?, right: String?): Boolean =
        !left.isNullOrBlank() && !right.isNullOrBlank() &&
            left.trim().trimEnd('/') == right.trim().trimEnd('/')

    companion object {
        const val ADAPTER_ID = "claude-code"
        private const val CONFIG_KEY = "settings"
        private const val CONFIG_PATH = "/root/.claude/settings.json"
        private const val STATE_KEY = "state"
        private const val STATE_PATH = "/root/.claude.json"
        private const val SKILL_ROOT = "/root/.claude/skills"
        private const val GLOBAL_CLAUDE_PATH = "/root/.claude/CLAUDE.md"
        private const val MCP_KEY = "mcpServers"
        private const val NATIVE_MODE_OPTION_ID = "mode"
        private const val ZHIPU_CODING_PLAN_PROVIDER_ID = "zhipu-coding-plan"
        private const val ZHIPU_CODING_PLAN_CLAUDE_BASE_URL = "https://open.bigmodel.cn/api/anthropic"
        private val CLAUDE_PERMISSION_LEVELS = mapOf(
            "plan" to AgentPermissionLevel.ReadOnly,
            "dontAsk" to AgentPermissionLevel.Restricted,
            "default" to AgentPermissionLevel.Approval,
            "acceptEdits" to AgentPermissionLevel.Lenient,
            "auto" to AgentPermissionLevel.Smart,
            "bypassPermissions" to AgentPermissionLevel.Full,
        )
        private const val SKILL_OVERRIDES_KEY = "skillOverrides"
        private const val MAX_MCP_ITEMS = 64
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val CLAUDE_ENV_REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
        private val CLAUDE_SKILL_ACTIVATIONS = setOf(
            AgentSkillActivation.Enabled,
            AgentSkillActivation.ManualOnly,
            AgentSkillActivation.Disabled,
        )
    }
}
