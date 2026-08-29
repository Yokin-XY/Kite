package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentConfigValidationProblem
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillDocumentWriteRequest
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.foundation.contracts.ContainerRecord
import java.net.URI

/**
 * 共享“标准 JSON mcpServers + 原生 Skill 目录”的协议适配骨架。
 *
 * 这层只复用已经形成事实标准的文件形状；模型、Provider、推理、权限和会话仍由 ACP
 * 或具体 Agent Adapter 公布，不能因为配置文件相似就在这里猜测能力。
 */
internal abstract class StandardJsonMcpProtocolAgentConfigAdapter protected constructor(
    context: Context,
    adapterId: String,
    private val agentDisplayName: String,
    configPath: String,
    containerProvider: () -> ContainerRecord?,
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
    skillRoots: List<String>,
    private val schema: StandardJsonMcpSchema = StandardJsonMcpSchema(),
) : JanksonNativeAgentConfigAdapter(
    context = context,
    adapterId = adapterId,
    paths = linkedMapOf(CONFIG_KEY to configPath),
    primaryKey = CONFIG_KEY,
    containerProvider = containerProvider,
    fileStore = fileStore,
) {
    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = skillRoots,
    )

    override fun displayName(): String = agentDisplayName

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = buildSet {
            add(AgentMcpOperation.Create)
            add(AgentMcpOperation.Edit)
            add(AgentMcpOperation.Remove)
            if (schema.enablement != StandardJsonMcpEnablement.None) {
                add(AgentMcpOperation.Enable)
                add(AgentMcpOperation.Disable)
            }
        },
        mcpTransports = schema.transports,
        skillOperations = setOf(AgentSkillOperation.Import, AgentSkillOperation.Remove),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        return NativeState(
            defaultModel = null,
            providers = emptyList(),
            credentialPresence = AgentCredentialPresence.NotApplicable,
            mcpServers = summaries(root),
            skills = skillDirectory.summaries(
                activation = { AgentSkillActivation.Enabled },
                activationOperations = emptySet(),
            ),
            warnings = listOf("模型、Provider、推理和权限以 $agentDisplayName 当前会话公布的原生能力为准"),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> {
                validateId(index, "serverId", change.serverId, output)
                if (schema.enablement == StandardJsonMcpEnablement.None) {
                    output += problem("changes[$index].enabled", "$agentDisplayName 的持久启停格式尚未核验")
                }
            }
            is AgentPersistentConfigChange.RemoveMcpServer -> validateId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override fun validateNativeRequest(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> =
        request.changes.mapIndexedNotNull { index, change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel,
                is AgentPersistentConfigChange.SelectProvider,
                is AgentPersistentConfigChange.ConfigureProvider,
                is AgentPersistentConfigChange.RemoveProvider ->
                    problem(
                        "changes[$index]",
                        "$agentDisplayName 的模型和 Provider 必须由当前会话能力或专用 Adapter 管理",
                    )
                else -> null
            }
        }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val skillChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (skillChanges.isEmpty()) return null
        if (request.changes.size != 1 || skillChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 变更一次只能执行一项，不能和 MCP 配置混合")),
            )
        }
        skillDirectory.applyFileChange(skillChanges.single())?.let { return it }
        return refreshedApplyResult(request.agentId)
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override suspend fun readSkillDocument(agentId: String, skillId: String) =
        skillDirectory.readDocument(skillId)

    override suspend fun writeSkillDocument(request: AgentSkillDocumentWriteRequest) =
        skillDirectory.writeDocument(request)

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.ConfigureMcpServer -> configure(root, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> setEnabled(root, change.serverId, change.enabled)
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val servers = root.objectCopy(MCP_SERVERS_KEY)
                    servers.remove(change.serverId)
                    putPreserving(root, MCP_SERVERS_KEY, servers)
                    removeExcluded(root, change.serverId)
                }
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root))
    }

    private fun summaries(root: JsonObject): List<AgentMcpSummary> {
        val excluded = excludedIds(root)
        return root.getObject(MCP_SERVERS_KEY)?.entries.orEmpty().mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val command = server.string(COMMAND_KEY)
            val httpUrl = server.string(schema.httpUrlKey)
            val sseUrl = server.string(schema.sseUrlKey)
            val type = schema.typeKey?.let { key -> server.string(key) }
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                type in schema.sseTypeValues && !(sseUrl ?: httpUrl).isNullOrBlank() -> AgentMcpTransport.Sse
                type in schema.httpTypeValues && !(httpUrl ?: sseUrl).isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                schema.httpUrlKey == schema.sseUrlKey && !(httpUrl ?: sseUrl).isNullOrBlank() ->
                    AgentMcpTransport.RemoteHttpOrSse
                !httpUrl.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                !sseUrl.isNullOrBlank() -> AgentMcpTransport.Sse
                else -> AgentMcpTransport.Unknown
            }
            val enabled = when (schema.enablement) {
                StandardJsonMcpEnablement.DisabledBoolean ->
                    !((server[DISABLED_KEY] as? JsonPrimitive)?.getValue() as? Boolean ?: false)
                StandardJsonMcpEnablement.ExcludedList -> id !in excluded
                StandardJsonMcpEnablement.None -> true
            }
            AgentMcpSummary(
                id = id,
                kind = when (transport) {
                    AgentMcpTransport.Stdio -> STDIO_TYPE
                    AgentMcpTransport.Sse -> SSE_TYPE
                    AgentMcpTransport.StreamableHttp -> HTTP_TYPE
                    AgentMcpTransport.RemoteHttpOrSse -> REMOTE_TYPE
                    else -> "unknown"
                },
                enabled = enabled,
                transport = transport,
                command = command,
                arguments = server.stringArray(ARGS_KEY),
                workingDirectory = server.string(CWD_KEY),
                url = when (transport) {
                    AgentMcpTransport.Sse -> sseUrl ?: httpUrl
                    AgentMcpTransport.StreamableHttp -> httpUrl ?: sseUrl
                    AgentMcpTransport.RemoteHttpOrSse -> httpUrl ?: sseUrl
                    else -> null
                },
                environmentReferences = environmentReferences(server.getObject(ENV_KEY)),
                headerReferences = environmentReferences(server.getObject(HEADERS_KEY)),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport in schema.transports) add(AgentMcpOperation.Edit)
                    if (schema.enablement != StandardJsonMcpEnablement.None) {
                        add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    }
                    add(AgentMcpOperation.Remove)
                },
            )
        }.sortedBy(AgentMcpSummary::id)
    }

    private fun configure(root: JsonObject, draft: AgentMcpDraft) {
        val servers = root.objectCopy(MCP_SERVERS_KEY)
        val server = servers.objectCopy(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, COMMAND_KEY, JsonPrimitive.of(requireNotNull(draft.command).trim()))
                putPreserving(server, ARGS_KEY, draft.arguments.toJsonArray())
                draft.workingDirectory?.trim()?.takeIf(String::isNotBlank)?.let {
                    putPreserving(server, CWD_KEY, JsonPrimitive.of(it))
                } ?: server.remove(CWD_KEY)
                putReferences(server, ENV_KEY, draft.environmentReferences)
                listOf(schema.httpUrlKey, schema.sseUrlKey, HEADERS_KEY).distinct().forEach(server::remove)
                setTransportType(server, AgentMcpTransport.Stdio)
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
            AgentMcpTransport.RemoteHttpOrSse -> {
                val urlKey = if (draft.transport == AgentMcpTransport.Sse) schema.sseUrlKey else schema.httpUrlKey
                putPreserving(server, urlKey, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                listOf(schema.httpUrlKey, schema.sseUrlKey).distinct().filterNot { it == urlKey }.forEach(server::remove)
                listOf(COMMAND_KEY, ARGS_KEY, CWD_KEY, ENV_KEY).forEach(server::remove)
                putReferences(server, HEADERS_KEY, draft.headerReferences)
                setTransportType(server, draft.transport)
            }
            else -> error("已由标准 MCP 校验限制传输类型")
        }
        putPreserving(servers, draft.id, server)
        putPreserving(root, MCP_SERVERS_KEY, servers)
        setEnabled(root, draft.id, draft.enabled)
    }

    private fun setTransportType(server: JsonObject, transport: AgentMcpTransport) {
        val key = schema.typeKey ?: return
        val value = when (transport) {
            AgentMcpTransport.Stdio -> schema.stdioTypeValue
            AgentMcpTransport.Sse -> schema.sseTypeValues.first()
            AgentMcpTransport.StreamableHttp -> schema.httpTypeValues.first()
            AgentMcpTransport.RemoteHttpOrSse -> null
            else -> null
        }
        if (value == null) server.remove(key) else putPreserving(server, key, JsonPrimitive.of(value))
    }

    private fun setEnabled(root: JsonObject, serverId: String, enabled: Boolean) {
        when (schema.enablement) {
            StandardJsonMcpEnablement.DisabledBoolean -> {
                val servers = root.objectCopy(MCP_SERVERS_KEY)
                val server = servers.objectCopy(serverId)
                putPreserving(server, DISABLED_KEY, JsonPrimitive.of(!enabled))
                putPreserving(servers, serverId, server)
                putPreserving(root, MCP_SERVERS_KEY, servers)
            }
            StandardJsonMcpEnablement.ExcludedList -> {
                val mcp = root.objectCopy(MCP_POLICY_KEY)
                val ids = mcp.stringArray(EXCLUDED_KEY).toMutableSet()
                if (enabled) ids.remove(serverId) else ids.add(serverId)
                putPreserving(mcp, EXCLUDED_KEY, ids.sorted().toJsonArray())
                putPreserving(root, MCP_POLICY_KEY, mcp)
            }
            StandardJsonMcpEnablement.None -> Unit
        }
    }

    private fun excludedIds(root: JsonObject): Set<String> = if (
        schema.enablement == StandardJsonMcpEnablement.ExcludedList
    ) root.getObject(MCP_POLICY_KEY)?.stringArray(EXCLUDED_KEY).orEmpty().toSet() else emptySet()

    private fun removeExcluded(root: JsonObject, serverId: String) {
        if (schema.enablement != StandardJsonMcpEnablement.ExcludedList) return
        setEnabled(root, serverId, enabled = true)
    }

    private fun validateMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateId(index, "server.id", draft.id, output)
        if (draft.transport !in schema.transports) {
            output += problem("changes[$index].server.transport", "$agentDisplayName 不支持这个 MCP 传输类型")
            return
        }
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.headerReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.headerReferences", "本地 MCP 不使用 HTTP Header")
                }
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
            AgentMcpTransport.RemoteHttpOrSse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
                if (draft.environmentReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.environmentReferences", "远程 MCP 不使用进程环境变量映射")
                }
            }
            else -> Unit
        }
        if (draft.arguments.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server.arguments", "MCP 参数数量过多")
        }
        draft.environmentReferences.forEach { reference ->
            if (!SAFE_ENV_NAME.matches(reference.name) || !SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
        draft.headerReferences.forEach { reference ->
            if (!SAFE_HEADER_NAME.matches(reference.name) || !SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.references", "MCP Header 环境变量引用格式无效")
            }
        }
    }

    private fun validateId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private fun putReferences(
        server: JsonObject,
        key: String,
        references: List<AgentMcpEnvironmentReference>,
    ) {
        if (references.isEmpty()) {
            server.remove(key)
            return
        }
        val objectValue = JsonObject()
        references.forEach { reference ->
            putPreserving(
                objectValue,
                reference.name,
                JsonPrimitive.of(renderReference(reference.name, reference.environmentVariable, key == HEADERS_KEY)),
            )
        }
        putPreserving(server, key, objectValue)
    }

    private fun environmentReferences(section: JsonObject?): List<AgentMcpEnvironmentReference> =
        section?.entries.orEmpty().mapNotNull { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String ?: return@mapNotNull null
            val variable = parseReference(raw) ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }.sortedBy(AgentMcpEnvironmentReference::name)

    private fun renderReference(name: String, variable: String, header: Boolean): String = when (schema.referenceStyle) {
        StandardJsonMcpReferenceStyle.Dollar -> "\${$variable}"
        StandardJsonMcpReferenceStyle.CursorEnv -> {
            val rendered = "\${env:$variable}"
            if (header && name.equals(AUTHORIZATION_HEADER, ignoreCase = true)) "Bearer $rendered" else rendered
        }
    }

    private fun parseReference(value: String): String? = when (schema.referenceStyle) {
        StandardJsonMcpReferenceStyle.Dollar -> DOLLAR_ENV_REFERENCE.matchEntire(value)
            ?.groupValues?.drop(1)?.firstOrNull(String::isNotBlank)
        StandardJsonMcpReferenceStyle.CursorEnv -> CURSOR_ENV_REFERENCE.matchEntire(value)
            ?.groupValues?.get(1)
    }

    private fun JsonObject.stringArray(key: String): List<String> = (get(key) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
        .orEmpty()

    private fun Iterable<String>.toJsonArray(): JsonArray = JsonArray().also { array ->
        forEach { array.add(JsonPrimitive.of(it)) }
    }

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    protected data class StandardJsonMcpSchema(
        val transports: Set<AgentMcpTransport> = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
        ),
        val httpUrlKey: String = HTTP_URL_KEY,
        val sseUrlKey: String = URL_KEY,
        val typeKey: String? = null,
        val stdioTypeValue: String = STDIO_TYPE,
        val httpTypeValues: List<String> = listOf(HTTP_TYPE, STREAMABLE_HTTP_TYPE),
        val sseTypeValues: List<String> = listOf(SSE_TYPE),
        val enablement: StandardJsonMcpEnablement = StandardJsonMcpEnablement.None,
        val referenceStyle: StandardJsonMcpReferenceStyle = StandardJsonMcpReferenceStyle.Dollar,
    )

    protected enum class StandardJsonMcpEnablement {
        None,
        DisabledBoolean,
        ExcludedList,
    }

    protected enum class StandardJsonMcpReferenceStyle {
        Dollar,
        CursorEnv,
    }

    private companion object {
        const val CONFIG_KEY = "settings"
        const val MCP_SERVERS_KEY = "mcpServers"
        const val MCP_POLICY_KEY = "mcp"
        const val EXCLUDED_KEY = "excluded"
        const val COMMAND_KEY = "command"
        const val ARGS_KEY = "args"
        const val CWD_KEY = "cwd"
        const val ENV_KEY = "env"
        const val HEADERS_KEY = "headers"
        const val URL_KEY = "url"
        const val HTTP_URL_KEY = "httpUrl"
        const val DISABLED_KEY = "disabled"
        const val STDIO_TYPE = "stdio"
        const val HTTP_TYPE = "http"
        const val STREAMABLE_HTTP_TYPE = "streamable-http"
        const val SSE_TYPE = "sse"
        const val REMOTE_TYPE = "remote"
        const val AUTHORIZATION_HEADER = "Authorization"
        const val MAX_MCP_ITEMS = 64
        val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        val SAFE_HEADER_NAME = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
        val DOLLAR_ENV_REFERENCE = Regex(
            "(?:\\$([A-Za-z_][A-Za-z0-9_]{0,127})|\\$\\{([A-Za-z_][A-Za-z0-9_]{0,127})\\})",
        )
        val CURSOR_ENV_REFERENCE = Regex(
            "(?:Bearer\\s+)?\\$\\{env:([A-Za-z_][A-Za-z0-9_-]{0,127})\\}",
            RegexOption.IGNORE_CASE,
        )
    }
}
