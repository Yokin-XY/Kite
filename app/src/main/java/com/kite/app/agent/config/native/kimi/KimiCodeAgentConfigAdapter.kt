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
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File
import java.net.URI

internal class KimiCodeAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
) : JanksonNativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(MCP_KEY to MCP_PATH),
    MCP_KEY,
    containerProvider,
    fileStore,
) {
    private val skillDirectory = NativeAgentSkillDirectory(
        projection::resolve,
        listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
    )

    override fun displayName(): String = "Kimi Code"

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "kimi-global-agents",
            displayName = "Kimi 全局说明",
            fileName = "AGENTS.md",
            containerPath = GLOBAL_AGENTS_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "Kimi Code 跨工作区加载的专属说明",
        ))
        add(NativeAgentCoreDocumentSpec(
            id = "kimi-system",
            displayName = "主 Agent 系统提示",
            fileName = "SYSTEM.md",
            containerPath = SYSTEM_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.FullSystemPromptReplacement,
            priorityDescription = "非空时替换内置主 Agent 系统提示；显式 Agent 覆盖仍优先",
            warning = "这不是附加说明。非空内容会完整替换 Kimi Code 内置主 Agent 的系统提示；" +
                "如仍需默认能力或 Plugin 指令，请按 Kimi 模板规则显式保留。",
        ))
        projectCoreDocument(
            workspacePath,
            id = "kimi-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前工作目录的项目说明，比全局说明更贴近本项目",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
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
            AgentSkillOperation.ManualOnly,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(MCP_KEY))
        return NativeState(
            defaultModel = null,
            providers = emptyList(),
            credentialPresence = AgentCredentialPresence.NotApplicable,
            mcpServers = kimiMcpServers(root.getObject(MCP_SERVERS_KEY)),
            skills = skillDirectory.summaries(
                activation = ::kimiSkillActivation,
                activationOperations = setOf(AgentSkillOperation.Enable, AgentSkillOperation.ManualOnly),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateKimiMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateKimiId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateKimiId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateKimiId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateKimiId(index, "skillId", change.skillId, output)
                if (change.activation !in KIMI_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "Kimi Code 只支持启用或仅手动调用")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateKimiId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val skillChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill ||
                it is AgentPersistentConfigChange.RemoveSkill ||
                it is AgentPersistentConfigChange.SetSkillActivation
        }
        if (skillChanges.isEmpty()) return null
        if (request.changes.size != 1 || skillChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 变更一次只能执行一项，不能和 MCP 配置混合")),
            )
        }
        when (val change = skillChanges.single()) {
            is AgentPersistentConfigChange.SetSkillActivation -> skillDirectory.applyTextChange(
                skillId = change.skillId,
                transform = { setKimiSkillManualOnly(it, change.activation == AgentSkillActivation.ManualOnly) },
                validate = { text -> if (frontmatterRange(text) == null) "SKILL.md 缺少有效 frontmatter" else null },
            )?.let { return it }
            else -> skillDirectory.applyFileChange(change)?.let { return it }
        }
        return refreshedApplyResult(request.agentId)
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val root = parse(files.getValue(MCP_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureKimiMcp(root, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val servers = root.objectCopy(MCP_SERVERS_KEY)
                    val server = servers.objectCopy(change.serverId)
                    putPreserving(server, ENABLED_KEY, JsonPrimitive.of(change.enabled))
                    putPreserving(servers, change.serverId, server)
                    putPreserving(root, MCP_SERVERS_KEY, servers)
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val servers = root.objectCopy(MCP_SERVERS_KEY)
                    servers.remove(change.serverId)
                    putPreserving(root, MCP_SERVERS_KEY, servers)
                }
                else -> Unit
            }
        }
        return mapOf(MCP_KEY to serialize(root))
    }

    private fun kimiMcpServers(section: JsonObject?): List<AgentMcpSummary> = section?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val command = server.string(COMMAND_KEY)
            val url = server.string(URL_KEY)
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                !url.isNullOrBlank() && server.string(TRANSPORT_KEY) == SSE_TYPE -> AgentMcpTransport.Sse
                !url.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.Unknown
            }
            val enabled = (server[ENABLED_KEY] as? JsonPrimitive)?.getValue() as? Boolean ?: true
            AgentMcpSummary(
                id = id,
                kind = when (transport) {
                    AgentMcpTransport.Stdio -> STDIO_TYPE
                    AgentMcpTransport.Sse -> SSE_TYPE
                    AgentMcpTransport.StreamableHttp -> HTTP_TYPE
                    else -> "unknown"
                },
                enabled = enabled,
                transport = transport,
                command = command,
                arguments = (server[ARGS_KEY] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
                    .orEmpty(),
                workingDirectory = server.string(CWD_KEY),
                url = url,
                headerReferences = listOfNotNull(
                    server.string(BEARER_ENV_KEY)?.takeIf(SAFE_ENV_NAME::matches)?.let {
                        AgentMcpEnvironmentReference(AUTHORIZATION_HEADER, it)
                    },
                ),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun configureKimiMcp(root: JsonObject, draft: AgentMcpDraft) {
        val servers = root.objectCopy(MCP_SERVERS_KEY)
        val server = servers.objectCopy(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, COMMAND_KEY, JsonPrimitive.of(requireNotNull(draft.command).trim()))
                val args = JsonArray()
                draft.arguments.forEach { args.add(JsonPrimitive.of(it)) }
                putPreserving(server, ARGS_KEY, args)
                draft.workingDirectory?.trim()?.takeIf(String::isNotBlank)?.let {
                    putPreserving(server, CWD_KEY, JsonPrimitive.of(it))
                } ?: server.remove(CWD_KEY)
                listOf(URL_KEY, TRANSPORT_KEY, HEADERS_KEY, BEARER_ENV_KEY).forEach(server::remove)
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                putPreserving(server, URL_KEY, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                if (draft.transport == AgentMcpTransport.Sse) {
                    putPreserving(server, TRANSPORT_KEY, JsonPrimitive.of(SSE_TYPE))
                } else {
                    server.remove(TRANSPORT_KEY)
                }
                listOf(COMMAND_KEY, ARGS_KEY, CWD_KEY, ENV_KEY).forEach(server::remove)
                val bearer = draft.headerReferences.singleOrNull()?.environmentVariable
                if (bearer == null) server.remove(BEARER_ENV_KEY)
                else putPreserving(server, BEARER_ENV_KEY, JsonPrimitive.of(bearer))
            }
            else -> error("已由 Kimi MCP 校验限制传输类型")
        }
        putPreserving(server, ENABLED_KEY, JsonPrimitive.of(draft.enabled))
        putPreserving(servers, draft.id, server)
        putPreserving(root, MCP_SERVERS_KEY, servers)
    }

    private fun validateKimiMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateKimiId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.environmentReferences.isNotEmpty() || draft.headerReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.references", "Kimi Code 的 stdio MCP 不支持安全变量引用编辑")
                }
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
                if (draft.environmentReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.environmentReferences", "远程 MCP 不使用进程环境变量映射")
                }
                if (draft.headerReferences.size > 1 || draft.headerReferences.any { it.name != AUTHORIZATION_HEADER }) {
                    output += problem("changes[$index].server.headerReferences", "Kimi Code 只支持 Authorization bearer 环境变量引用")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Kimi Code 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server.arguments", "MCP 参数数量过多")
        }
        draft.headerReferences.forEach { reference ->
            if (!SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.headerReferences", "MCP 环境变量名称无效")
            }
        }
    }

    private fun validateKimiId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private fun kimiSkillActivation(entry: NativeAgentSkillDirectory.Entry): AgentSkillActivation {
        val text = runCatching { File(entry.directory, SKILL_FILE).readText() }.getOrNull()
            ?: return AgentSkillActivation.Unknown
        val range = frontmatterRange(text) ?: return AgentSkillActivation.Unknown
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        return if ((range.first + 1 until range.last).any { index ->
                val line = lines[index]
                SKILL_MANUAL_FIELD.matchEntire(line.trim())?.groupValues?.get(2)?.equals("true", ignoreCase = true) == true
            }
        ) AgentSkillActivation.ManualOnly else AgentSkillActivation.Enabled
    }

    private fun setKimiSkillManualOnly(text: String, manualOnly: Boolean): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val range = frontmatterRange(normalized) ?: error("SKILL.md 缺少有效 frontmatter")
        val lines = normalized.split('\n').toMutableList()
        val existing = range.first + 1 until range.last
        val index = existing.firstOrNull { SKILL_MANUAL_FIELD.matches(lines[it].trim()) }
        val rendered = "disableModelInvocation: $manualOnly"
        if (index == null) lines.add(range.last, rendered) else lines[index] = rendered
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun frontmatterRange(text: String): IntRange? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.firstOrNull()?.trim() != "---") return null
        val end = (1 until minOf(lines.size, MAX_FRONTMATTER_LINES)).firstOrNull { lines[it].trim() == "---" }
            ?: return null
        return 0..end
    }

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    companion object {
        const val ADAPTER_ID = "kimi-code"
        private const val MCP_KEY = "mcp"
        private const val MCP_PATH = "/root/.kimi-code/mcp.json"
        private const val MCP_SERVERS_KEY = "mcpServers"
        private const val SKILL_ROOT = "/root/.kimi-code/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val GLOBAL_AGENTS_PATH = "/root/.kimi-code/AGENTS.md"
        private const val SYSTEM_PATH = "/root/.kimi-code/SYSTEM.md"
        private const val SKILL_FILE = "SKILL.md"
        private const val COMMAND_KEY = "command"
        private const val ARGS_KEY = "args"
        private const val CWD_KEY = "cwd"
        private const val ENV_KEY = "env"
        private const val URL_KEY = "url"
        private const val TRANSPORT_KEY = "transport"
        private const val HEADERS_KEY = "headers"
        private const val BEARER_ENV_KEY = "bearerTokenEnvVar"
        private const val ENABLED_KEY = "enabled"
        private const val STDIO_TYPE = "stdio"
        private const val HTTP_TYPE = "http"
        private const val SSE_TYPE = "sse"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val MAX_MCP_ITEMS = 64
        private const val MAX_FRONTMATTER_LINES = 160
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val SKILL_MANUAL_FIELD = Regex(
            "(disableModelInvocation|disable-model-invocation|disable_model_invocation)\\s*:\\s*(true|false)",
            RegexOption.IGNORE_CASE,
        )
        private val KIMI_SKILL_ACTIVATIONS = setOf(AgentSkillActivation.Enabled, AgentSkillActivation.ManualOnly)
    }
}
