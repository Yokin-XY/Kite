package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentConfigValidationProblem
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.AgentSessionPermissionProfile
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.net.URI
import java.util.LinkedHashMap

/** TraeCode CLI 由 ACP 公布模型与会话；Kite 管理其真实 TOML MCP、Skill 目录和权限档。 */
internal class TraeCodeAgentConfigAdapter internal constructor(
    context: Context,
    containerProvider: () -> ContainerRecord?,
) : NativeAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    paths = linkedMapOf(CONFIG_KEY to CONFIG_PATH),
    primaryKey = CONFIG_KEY,
    containerProvider = containerProvider,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = listOf(CLI_SKILL_ROOT, DESKTOP_SKILL_ROOT),
        mutableRoots = setOf(CLI_SKILL_ROOT),
    )

    override fun displayName(): String = "TraeCode CLI"

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(AgentPersistentConfigCapability.Mcp, AgentPersistentConfigCapability.Skill),
        credentialOwnership = AgentCredentialOwnership.Unsupported,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove,
        ),
        mcpTransports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.StreamableHttp),
        skillOperations = setOf(AgentSkillOperation.Import, AgentSkillOperation.Remove),
    )

    override fun sessionPermissionControl(): AgentSessionPermissionControl = TRAE_PERMISSION_CONTROL

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
        modes.filterNot { it.id in TRAE_PERMISSION_MODES }

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val parsed = Toml.parse(files.getValue(CONFIG_KEY).toString(Charsets.UTF_8))
        check(!parsed.hasErrors()) { "TraeCode traecli.toml 格式无效" }
        return NativeState(
            defaultModel = null,
            providers = emptyList(),
            credentialPresence = AgentCredentialPresence.NotApplicable,
            warnings = listOf("模型、推理强度和认证由 TraeCode ACP 与官方登录管理"),
            mcpServers = parseMcpServers(parsed.getTable(MCP_SERVERS_KEY)),
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
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateId(index, "serverId", change.serverId, output)
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

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: com.kite.app.agent.config.AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val skillChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (skillChanges.isEmpty()) return null
        if (request.changes.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 变更一次只能执行一项，不能和 MCP 配置混合")),
            )
        }
        skillDirectory.applyFileChange(skillChanges.single())?.let { return it }
        return when (val refreshed = readLive(request.agentId)) {
            is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, null)
            is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, false)
            is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
        }
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override suspend fun readSkillDocument(agentId: String, skillId: String) = skillDirectory.readDocument(skillId)

    override suspend fun writeSkillDocument(request: com.kite.app.agent.config.AgentSkillDocumentWriteRequest) =
        skillDirectory.writeDocument(request)

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val editor = NativeTomlTextEditor(files.getValue(CONFIG_KEY).toString(Charsets.UTF_8))
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.ConfigureMcpServer -> setMcpServer(editor, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> editor.setTableFields(
                    mcpPath(change.serverId),
                    mapOf(ENABLED_KEY to change.enabled.toString()),
                )
                is AgentPersistentConfigChange.RemoveMcpServer -> editor.removeTableTree(mcpPath(change.serverId))
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to editor.text.toByteArray(Charsets.UTF_8))
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? = when (key) {
        CONFIG_KEY -> if (Toml.parse(bytes.toString(Charsets.UTF_8)).hasErrors()) {
            "TraeCode 原生 traecli.toml 格式无效"
        } else null
        else -> "未知的 TraeCode 配置文件"
    }

    private fun parseMcpServers(servers: TomlTable?): List<AgentMcpSummary> = servers?.keySet()
        ?.mapNotNull { id ->
            val table = servers.getTable(id) ?: return@mapNotNull null
            val command = table.getString(COMMAND_KEY)
            val url = table.getString(URL_KEY)
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                !url.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.Unknown
            }
            val enabled = table.getBoolean(ENABLED_KEY) ?: true
            val bearerTokenVariable = table.getString(BEARER_TOKEN_ENV_KEY)
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
                arguments = table.getArray(ARGS_KEY)?.toList()?.mapNotNull { it as? String }.orEmpty(),
                workingDirectory = table.getString(CWD_KEY),
                url = url,
                environmentReferences = table.getTable(ENV_KEY)?.let(::parseEnvironmentReferences).orEmpty(),
                headerReferences = bearerTokenVariable?.let {
                    listOf(AgentMcpEnvironmentReference(AUTHORIZATION_HEADER, it))
                }.orEmpty(),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        ?.sortedBy(AgentMcpSummary::id)
        .orEmpty()

    private fun parseEnvironmentReferences(table: TomlTable): List<AgentMcpEnvironmentReference> =
        table.keySet().mapNotNull { name ->
            val variable = table.getString(name)?.let(ENV_REFERENCE::matchEntire)?.groupValues?.get(1)
                ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }.sortedBy(AgentMcpEnvironmentReference::name)

    private fun setMcpServer(editor: NativeTomlTextEditor, draft: AgentMcpDraft) {
        editor.removeTableTree(mcpPath(draft.id) + ENV_KEY)
        val fields = LinkedHashMap<String, String?>().apply {
            put(ENABLED_KEY, null)
            put(COMMAND_KEY, null)
            put(ARGS_KEY, null)
            put(CWD_KEY, null)
            put(URL_KEY, null)
            put(BEARER_TOKEN_ENV_KEY, null)
        }
        fields[ENABLED_KEY] = draft.enabled.toString()
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                fields[COMMAND_KEY] = NativeTomlTextEditor.tomlString(requireNotNull(draft.command).trim())
                fields[ARGS_KEY] = NativeTomlTextEditor.tomlStringArray(draft.arguments)
                fields[CWD_KEY] = draft.workingDirectory?.trim()?.takeIf(String::isNotBlank)
                    ?.let(NativeTomlTextEditor::tomlString)
            }
            AgentMcpTransport.StreamableHttp -> {
                fields[URL_KEY] = NativeTomlTextEditor.tomlString(requireNotNull(draft.url).trim())
                fields[BEARER_TOKEN_ENV_KEY] = draft.headerReferences.singleOrNull()
                    ?.environmentVariable
                    ?.let(NativeTomlTextEditor::tomlString)
            }
            else -> error("已由 TraeCode MCP 校验限制传输类型")
        }
        editor.setTableFields(mcpPath(draft.id), fields)
        if (draft.transport == AgentMcpTransport.Stdio && draft.environmentReferences.isNotEmpty()) {
            editor.setTableFields(
                mcpPath(draft.id) + ENV_KEY,
                draft.environmentReferences.associate { reference ->
                    reference.name to NativeTomlTextEditor.tomlString("\${${reference.environmentVariable}}")
                },
            )
        }
    }

    private fun validateMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.headerReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.headerReferences", "TraeCode stdio MCP 不支持 Header")
                }
            }
            AgentMcpTransport.StreamableHttp -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
                if (draft.environmentReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.environmentReferences", "TraeCode HTTP MCP 不支持进程环境变量")
                }
                if (draft.headerReferences.size > 1 || draft.headerReferences.any { it.name != AUTHORIZATION_HEADER }) {
                    output += problem("changes[$index].server.headerReferences", "TraeCode HTTP MCP 只支持 Authorization Bearer 环境变量")
                }
            }
            else -> output += problem("changes[$index].server.transport", "TraeCode 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS || draft.environmentReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 参数或环境变量数量过多")
        }
        draft.arguments.forEach { value ->
            if (value.length > MAX_MCP_TEXT || value.any(Char::isISOControl)) {
                output += problem("changes[$index].server.arguments", "MCP 参数格式无效")
            }
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (!SAFE_ENV_NAME.matches(reference.environmentVariable) || !SAFE_ENV_NAME.matches(reference.name)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
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

    private fun mcpPath(id: String): List<String> = listOf(MCP_SERVERS_KEY, id)

    companion object {
        const val ADAPTER_ID = "trae-code"
        private const val CONFIG_KEY = "config"
        private const val INSTALL_ROOT = "/workspace/.kf/software/kite.trae.code"
        private const val CONFIG_PATH = "$INSTALL_ROOT/user-home/.trae/traecli.toml"
        private const val CLI_SKILL_ROOT = "$INSTALL_ROOT/user-home/.traecli/skills"
        private const val DESKTOP_SKILL_ROOT = "$INSTALL_ROOT/user-home/.trae-cn/skills"
        private const val MCP_SERVERS_KEY = "mcp_servers"
        private const val COMMAND_KEY = "command"
        private const val ARGS_KEY = "args"
        private const val CWD_KEY = "cwd"
        private const val ENV_KEY = "env"
        private const val URL_KEY = "url"
        private const val ENABLED_KEY = "enabled"
        private const val BEARER_TOKEN_ENV_KEY = "bearer_token_env_var"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val MODE_DEFAULT = "default"
        private const val MODE_AUTO = "auto"
        private const val MODE_BYPASS = "bypass_permissions"
        private const val MAX_MCP_ITEMS = 64
        private const val MAX_MCP_TEXT = 2_048
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val ENV_REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]{0,127})\\}")
        private val TRAE_PERMISSION_MODES = setOf(MODE_DEFAULT, MODE_AUTO, MODE_BYPASS)
        private val TRAE_PERMISSION_CONTROL = AgentSessionPermissionControl(
            profiles = listOf(
                AgentSessionPermissionProfile(
                    id = MODE_DEFAULT,
                    level = AgentPermissionLevel.Approval,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_AUTO,
                    level = AgentPermissionLevel.Smart,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_BYPASS,
                    level = AgentPermissionLevel.Full,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
            ),
            initialProfileId = MODE_DEFAULT,
            nativeModeByProfileId = TRAE_PERMISSION_MODES.associateWith { it },
        )
    }
}
