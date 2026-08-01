package com.kite.app.agent.config

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPermissionRequest

/** Agent 原生持久配置可由适配器安全管理的能力。 */
enum class AgentPersistentConfigCapability {
    DefaultModel,
    Provider,
    ProviderProfiles,
    PermissionProfiles,
    Mcp,
    Skill,
    CoreDocuments,
    CredentialStatus
}

/** 密钥归谁管理；AgentOwned 表示只写 Agent 原生认证位置，Kite 不保留独立副本或回显密钥。 */
enum class AgentCredentialOwnership {
    AgentOwned,
    KiteSecureStore,
    Unsupported
}

data class AgentConfigCapabilities(
    val supported: Set<AgentPersistentConfigCapability>,
    val credentialOwnership: AgentCredentialOwnership = AgentCredentialOwnership.AgentOwned,
    val mcpOperations: Set<AgentMcpOperation> = emptySet(),
    val mcpTransports: Set<AgentMcpTransport> = emptySet(),
    val skillOperations: Set<AgentSkillOperation> = emptySet()
) {
    fun supports(capability: AgentPersistentConfigCapability): Boolean = capability in supported
}

enum class AgentConfigDiscoveryState {
    Ready,
    NoRuntime,
    NotInstalled,
    Unsupported,
    Error
}

/** 只包含可展示的容器内位置；宿主绝对路径由适配器私有持有。 */
data class AgentConfigDiscovery(
    val agentId: String,
    val adapterId: String,
    val state: AgentConfigDiscoveryState,
    val displayLocation: String? = null,
    val writable: Boolean = false,
    val warnings: List<String> = emptyList()
)

enum class AgentCredentialPresence {
    Present,
    Missing,
    Unknown,
    NotApplicable
}

enum class AgentConfigScope {
    User,
    Project,
    Workspace,
    External,
    Unknown
}

/** 核心设定文档被 Agent 解释的真实语义；页面不能把完整覆盖误写成普通追加说明。 */
enum class AgentCoreDocumentSemantics {
    SupplementalInstructions,
    FullSystemPromptReplacement,
    Persona,
    UserProfile,
    Identity
}

/**
 * 可安全进入页面状态的核心设定文档描述符。
 *
 * 正文和 revision 不在这里，只有用户明确进入编辑页后才单独读取。
 */
data class AgentCoreDocumentDescriptor(
    val id: String,
    val displayName: String,
    val fileName: String,
    val displayLocation: String,
    val scope: AgentConfigScope,
    val semantics: AgentCoreDocumentSemantics,
    val exists: Boolean,
    val writable: Boolean,
    val priorityDescription: String,
    val warning: String? = null
)

data class AgentCoreDocumentSnapshot(
    val descriptor: AgentCoreDocumentDescriptor,
    val revision: String,
    val content: String
) {
    override fun toString(): String =
        "AgentCoreDocumentSnapshot(descriptor=$descriptor, revision=$revision, contentLength=${content.length})"
}

data class AgentCoreDocumentWriteRequest(
    val agentId: String,
    val documentId: String,
    val workspacePath: String?,
    val expectedRevision: String,
    val content: String
) {
    override fun toString(): String =
        "AgentCoreDocumentWriteRequest(agentId=$agentId, documentId=$documentId, " +
            "workspacePath=$workspacePath, expectedRevision=$expectedRevision, contentLength=${content.length})"
}

sealed interface AgentCoreDocumentListResult {
    data class Ready(val documents: List<AgentCoreDocumentDescriptor>) : AgentCoreDocumentListResult
    data class Unavailable(val discovery: AgentConfigDiscovery) : AgentCoreDocumentListResult
    data class Failed(val message: String) : AgentCoreDocumentListResult
}

sealed interface AgentCoreDocumentReadResult {
    data class Ready(val snapshot: AgentCoreDocumentSnapshot) : AgentCoreDocumentReadResult
    data class Missing(val message: String = "核心设定文档不存在") : AgentCoreDocumentReadResult
    data class Unavailable(val discovery: AgentConfigDiscovery) : AgentCoreDocumentReadResult
    data class Failed(val message: String) : AgentCoreDocumentReadResult
}

sealed interface AgentCoreDocumentWriteResult {
    data class Applied(
        val snapshot: AgentCoreDocumentSnapshot,
        val backupReference: String?
    ) : AgentCoreDocumentWriteResult
    data class Conflict(
        val currentRevision: String,
        val message: String = "核心设定已被其他程序修改，请重新读取"
    ) : AgentCoreDocumentWriteResult
    data class Rejected(val problems: List<AgentConfigValidationProblem>) : AgentCoreDocumentWriteResult
    data class Unavailable(val discovery: AgentConfigDiscovery) : AgentCoreDocumentWriteResult
    data class Failed(val message: String, val restored: Boolean) : AgentCoreDocumentWriteResult
}

enum class AgentMcpConnectionState {
    NotChecked,
    Checking,
    Available,
    Unavailable
}

enum class AgentMcpOperation {
    Create,
    Edit,
    Enable,
    Disable,
    Remove,
    CheckConnection
}

/** MCP 的通用传输形状；RemoteHttpOrSse 表示 Agent 会自行在 HTTP 与 SSE 间协商。 */
enum class AgentMcpTransport {
    Stdio,
    StreamableHttp,
    Sse,
    RemoteHttpOrSse,
    Unknown
}

/** 只暴露环境变量名称，不携带 Header 或环境变量的真实值。 */
data class AgentMcpEnvironmentReference(
    val name: String,
    val environmentVariable: String
)

data class AgentMcpSummary(
    val id: String,
    val kind: String,
    val enabled: Boolean,
    val transport: AgentMcpTransport = AgentMcpTransport.Unknown,
    val command: String? = null,
    val arguments: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val url: String? = null,
    val environmentReferences: List<AgentMcpEnvironmentReference> = emptyList(),
    val headerReferences: List<AgentMcpEnvironmentReference> = emptyList(),
    val scope: AgentConfigScope = AgentConfigScope.User,
    val connectionState: AgentMcpConnectionState = AgentMcpConnectionState.NotChecked,
    val allowedOperations: Set<AgentMcpOperation> = emptySet()
)

/**
 * MCP 编辑页提交给适配器的通用草稿。
 *
 * command/arguments 不经过 shell 拼接；环境和 Header 只能引用变量名。适配器负责把它翻译成
 * 当前 Agent 的原生 schema，并保留未被这个草稿管理的未知字段。
 */
data class AgentMcpDraft(
    val id: String,
    val transport: AgentMcpTransport,
    val enabled: Boolean = true,
    val command: String? = null,
    val arguments: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val url: String? = null,
    val environmentReferences: List<AgentMcpEnvironmentReference> = emptyList(),
    val headerReferences: List<AgentMcpEnvironmentReference> = emptyList()
)

enum class AgentSkillActivation {
    Enabled,
    ApprovalRequired,
    ManualOnly,
    Disabled,
    Unknown
}

enum class AgentSkillOperation {
    Import,
    Enable,
    RequireApproval,
    ManualOnly,
    Disable,
    Remove
}

data class AgentSkillSummary(
    val id: String,
    val displayName: String = id,
    val location: String? = null,
    val scope: AgentConfigScope = AgentConfigScope.User,
    val activation: AgentSkillActivation = AgentSkillActivation.Unknown,
    val allowedOperations: Set<AgentSkillOperation> = emptySet()
)

/** 可安全进入页面状态的供应商模型资料。 */
data class AgentProviderModelSummary(
    val id: String,
    val displayName: String = id
)

/**
 * Agent 原生供应商配置的安全投影。
 *
 * 这里只保留名称、端点、模型和凭据是否存在；原始 Header、API Key 和未知私有字段不会离开适配器。
 */
data class AgentProviderSummary(
    val id: String,
    val displayName: String = id,
    val baseUrl: String? = null,
    val models: List<AgentProviderModelSummary> = emptyList(),
    val credentialPresence: AgentCredentialPresence = AgentCredentialPresence.Unknown
)

/** 配置页面提交给适配器的通用供应商资料，不包含 Agent 原生文件结构。 */
data class AgentProviderDraft(
    val id: String,
    val displayName: String? = null,
    val baseUrl: String,
    val models: List<AgentProviderModelSummary>
)

/** 预置只是可编辑的起点，保存后仍写入对应 Agent 的原生配置。 */
data class AgentProviderPreset(
    val id: String,
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val models: List<AgentProviderModelSummary>
)

/** Agent 官方权限档位的生效边界；显示层不得把它推断成更强的沙箱承诺。 */
enum class AgentSessionConfigurationEffect {
    Immediate,
    NextTurn,
    NewSession,
    Reconnect
}

/**
 * Kite 对外稳定的权限语义。适配器只声明能够由 Agent 原生能力真实兑现的档位，
 * 原生 ID 仍由 [AgentPermissionProfileSummary.id] 保留。
 */
enum class AgentPermissionLevel(
    val displayName: String,
    val description: String,
    val order: Int,
) {
    ReadOnly("只读", "只允许读取内容，不执行写入或高风险操作", 1),
    Restricted("受限", "只允许已明确放行的操作，其余请求被阻止", 2),
    Approval("审批", "敏感操作会先请求你确认", 3),
    Lenient("宽松", "常规操作直接执行，高风险操作仍受控制", 4),
    Smart("智能", "自动判断风险，不确定时再请求你确认", 5),
    Full("完全", "关闭普通审批，以 Agent 可用的最高权限运行", 6),
}

/**
 * Agent 原生权限档位的安全投影。
 *
 * [id] 必须保留 Agent 官方配置值；[displayName] 只负责中文显示，不能改变原生语义或合并档位。
 */
data class AgentPermissionProfileSummary(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val effect: AgentSessionConfigurationEffect,
    val level: AgentPermissionLevel? = null,
)

/** Kite 只能在真实权限请求到达后代理这些处理方式，不能替 Agent 发明新的沙箱边界。 */
enum class AgentSessionPermissionHandling {
    AskUser,
    AllowRequest,
    RejectRequest,
    /** 保留 Agent 原生风险判断；只有 Agent 仍向客户端提问时才交给用户。 */
    PreserveAgentDecision,
}

/**
 * 某个 Agent 允许 Kite 在当前会话内代理的权限档位。
 *
 * 这里的 ID 与该 Agent 的 [AgentPermissionProfileSummary.id] 共用同一目录；会话选择只保存在
 * 当前连接中，不写回 [AgentPersistentConfigChange.SetPermissionProfile]。
 */
data class AgentSessionPermissionProfile(
    val id: String,
    val level: AgentPermissionLevel,
    val handling: AgentSessionPermissionHandling,
)

data class AgentSessionPermissionControl(
    val profiles: List<AgentSessionPermissionProfile>,
    val initialProfileId: String = profiles.firstOrNull {
        it.handling == AgentSessionPermissionHandling.AskUser
    }?.id.orEmpty(),
) {
    init {
        require(profiles.isNotEmpty()) { "当前会话权限至少需要一个可选档位" }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "当前会话权限 ID 不能重复" }
        require(initialProfileId.isNotBlank() && profiles.any { it.id == initialProfileId }) {
            "当前会话权限初始值必须属于可选档位"
        }
    }

    fun option(currentProfileId: String = initialProfileId): AgentConfigOption.Select = AgentConfigOption.Select(
        id = SESSION_PERMISSION_CONFIG_ID,
        name = "权限",
        description = "只影响当前会话；不会修改 Agent 的默认权限",
        category = AgentConfigCategory.Permission,
        currentValue = currentProfileId,
        choices = profiles.sortedBy { it.level.order }.map { profile ->
            AgentConfigChoice(
                value = profile.id,
                name = profile.level.displayName,
                description = profile.level.description,
            )
        },
    )

    /**
     * 自动代理只使用一次性选项，避免把当前会话选择意外写成 Agent 的跨会话永久规则。
     * 找不到精确的一次性选项时返回 null，由上层继续向用户展示真实请求。
     */
    fun resolve(profileId: String, request: AgentPermissionRequest): AgentPermissionOutcome? {
        val handling = profiles.firstOrNull { it.id == profileId }?.handling ?: return null
        val preferredKind = when (handling) {
            AgentSessionPermissionHandling.AskUser,
            AgentSessionPermissionHandling.PreserveAgentDecision -> return null
            AgentSessionPermissionHandling.AllowRequest -> AgentPermissionKind.AllowOnce
            AgentSessionPermissionHandling.RejectRequest -> AgentPermissionKind.RejectOnce
        }
        return request.options.firstOrNull { it.kind == preferredKind }
            ?.let { AgentPermissionOutcome.Selected(it.id) }
    }
}

fun mediatedSessionPermissionControl(
    vararg levels: AgentPermissionLevel,
): AgentSessionPermissionControl {
    val profiles = levels.distinct().map { level ->
        val handling = when (level) {
            AgentPermissionLevel.Restricted -> AgentSessionPermissionHandling.RejectRequest
            AgentPermissionLevel.Approval -> AgentSessionPermissionHandling.AskUser
            AgentPermissionLevel.Full -> AgentSessionPermissionHandling.AllowRequest
            else -> error("$level 需要 Agent 原生风险判断，不能由通用审批代理伪造")
        }
        AgentSessionPermissionProfile(
            id = "kite.permission.${level.name.lowercase()}",
            level = level,
            handling = handling,
        )
    }
    return AgentSessionPermissionControl(profiles)
}

/**
 * 从 Agent 的原生权限目录生成当前会话目录。
 *
 * 原生 ID、Kite 六档语义和选项数量都原样保留；适配器只补充每个原生档位到审批中介的
 * 处理方式。这样设置页与会话页可以拥有不同当前值，但不会出现会话页越过原生上限。
 */
fun mediatedSessionPermissionControl(
    profiles: List<AgentPermissionProfileSummary>,
    handlingByProfileId: Map<String, AgentSessionPermissionHandling>,
    initialProfileId: String? = null,
): AgentSessionPermissionControl {
    require(profiles.isNotEmpty()) { "原生权限目录不能为空" }
    val sessionProfiles = profiles.map { profile ->
        val level = requireNotNull(profile.level) { "${profile.id} 缺少 Kite 权限语义" }
        val handling = requireNotNull(handlingByProfileId[profile.id]) {
            "${profile.id} 缺少当前会话审批处理方式"
        }
        AgentSessionPermissionProfile(
            id = profile.id,
            level = level,
            handling = handling,
        )
    }
    val safeInitial = initialProfileId?.takeIf { requested ->
        sessionProfiles.any { it.id == requested }
    } ?: sessionProfiles.firstOrNull {
        it.handling == AgentSessionPermissionHandling.AskUser
    }?.id ?: sessionProfiles.first().id
    return AgentSessionPermissionControl(sessionProfiles, safeInitial)
}

/** API Key 只在本次调用中短暂存在；任何字符串化都必须保持脱敏。 */
sealed interface AgentProviderCredentialChange {
    data object Keep : AgentProviderCredentialChange

    class Replace internal constructor(internal val secret: String) : AgentProviderCredentialChange {
        override fun toString(): String = "Replace([REDACTED])"
    }

    data object Remove : AgentProviderCredentialChange

    companion object {
        fun replace(secret: String): AgentProviderCredentialChange = Replace(secret)
    }
}

/**
 * Agent 原生配置的安全投影。
 *
 * 这里故意不包含原始文档、Provider 选项、环境变量值、Header 或凭据内容，因而可以进入 UI 状态。
 */
data class AgentLiveConfigSnapshot(
    val agentId: String,
    val adapterId: String,
    val revision: String,
    val displayLocation: String,
    val activeProviderId: String? = null,
    val defaultModel: String? = null,
    val providerIds: List<String> = emptyList(),
    val providers: List<AgentProviderSummary> = emptyList(),
    val activePermissionProfileId: String? = null,
    val permissionProfiles: List<AgentPermissionProfileSummary> = emptyList(),
    val mcpServers: List<AgentMcpSummary> = emptyList(),
    val skills: List<AgentSkillSummary> = emptyList(),
    val credentialPresence: AgentCredentialPresence = AgentCredentialPresence.Unknown,
    val warnings: List<String> = emptyList(),
    val runtimeReloadRequired: Boolean = false
)

/** 可写入普通 Agent 配置的非密钥值；密钥必须走独立的安全存储或 Agent 原生认证。 */
sealed interface AgentConfigValue {
    data class Text(val value: String) : AgentConfigValue {
        override fun toString(): String = "Text(length=${value.length})"
    }
    data class Flag(val value: Boolean) : AgentConfigValue
    data class Number(val value: Double) : AgentConfigValue
    data class Values(val values: List<AgentConfigValue>) : AgentConfigValue
    data class ObjectValue(val values: Map<String, AgentConfigValue>) : AgentConfigValue
    data class EnvironmentReference(val variableName: String) : AgentConfigValue
    data object NullValue : AgentConfigValue
}

sealed interface AgentPersistentConfigChange {
    data class SetDefaultModel(val modelId: String?) : AgentPersistentConfigChange
    data class SelectProvider(
        val providerId: String,
        val modelId: String
    ) : AgentPersistentConfigChange
    data class PutProvider(
        val providerId: String,
        val configuration: AgentConfigValue.ObjectValue
    ) : AgentPersistentConfigChange
    data class ConfigureProvider(
        val provider: AgentProviderDraft,
        val credential: AgentProviderCredentialChange = AgentProviderCredentialChange.Keep
    ) : AgentPersistentConfigChange {
        override fun toString(): String = "ConfigureProvider(provider=$provider, credential=$credential)"
    }
    data class RemoveProvider(
        val providerId: String,
        val removeCredential: Boolean = false
    ) : AgentPersistentConfigChange
    data class SetPermissionProfile(val profileId: String) : AgentPersistentConfigChange
    data class PutMcpServer(
        val serverId: String,
        val configuration: AgentConfigValue.ObjectValue
    ) : AgentPersistentConfigChange
    data class ConfigureMcpServer(val server: AgentMcpDraft) : AgentPersistentConfigChange
    data class SetMcpEnabled(val serverId: String, val enabled: Boolean) : AgentPersistentConfigChange
    data class RemoveMcpServer(val serverId: String) : AgentPersistentConfigChange
    data class InstallSkill(val skillId: String, val sourceReference: String) : AgentPersistentConfigChange
    data class SetSkillActivation(
        val skillId: String,
        val activation: AgentSkillActivation
    ) : AgentPersistentConfigChange
    data class RemoveSkill(val skillId: String) : AgentPersistentConfigChange
}

/** 供应商默认值在当前 Agent 会话中有真实对应项时，可同时切换当前会话。 */
data class AgentSessionModelSelection(
    val configId: String,
    val value: String
)

sealed interface AgentSessionConfigurationApplyResult {
    data class Applied(
        val options: List<AgentConfigOption>,
        val effect: AgentSessionConfigurationEffect
    ) : AgentSessionConfigurationApplyResult

    data class Unsupported(val operation: String) : AgentSessionConfigurationApplyResult
    data class Failed(val message: String) : AgentSessionConfigurationApplyResult
}

data class AgentConfigApplyRequest(
    val agentId: String,
    val expectedRevision: String,
    val changes: List<AgentPersistentConfigChange>
)

data class AgentConfigValidationProblem(
    val field: String,
    val message: String
)

sealed interface AgentConfigReadResult {
    data class Ready(val snapshot: AgentLiveConfigSnapshot) : AgentConfigReadResult
    data class Unavailable(val discovery: AgentConfigDiscovery) : AgentConfigReadResult
    data class Failed(val message: String) : AgentConfigReadResult
}

sealed interface AgentConfigApplyResult {
    data class Applied(val snapshot: AgentLiveConfigSnapshot, val backupReference: String?) : AgentConfigApplyResult
    data class Conflict(val currentRevision: String, val message: String = "原生配置已发生变化，请重新读取") : AgentConfigApplyResult
    data class Rejected(val problems: List<AgentConfigValidationProblem>) : AgentConfigApplyResult
    data class Unavailable(val discovery: AgentConfigDiscovery) : AgentConfigApplyResult
    data class Failed(val message: String, val restored: Boolean) : AgentConfigApplyResult
}

sealed interface AgentMcpConnectionCheckResult {
    data class Available(val message: String? = null) : AgentMcpConnectionCheckResult
    data class Unavailable(val message: String) : AgentMcpConnectionCheckResult
    data class Unsupported(val message: String = "当前 Agent 不支持从 Kite 检查 MCP 连接") :
        AgentMcpConnectionCheckResult
}

/**
 * 每种 Agent 原生配置的最小适配合同。
 *
 * readLive/backfill 返回同一种安全投影；backfill 表示重新吸收原生修改，而不是写入第二份配置事实。
 */
interface AgentConfigAdapter {
    val adapterId: String

    fun capabilities(): AgentConfigCapabilities

    /**
     * 把 Agent 当前会话的模型值转换成该 Agent 原生配置可接受的默认模型变更。
     *
     * 默认不支持；页面不能仅凭 category 或显示名称猜测两种 ID 属于同一命名空间。
     */
    fun defaultModelChange(
        option: AgentConfigOption.Select
    ): AgentPersistentConfigChange.SetDefaultModel? = null

    /**
     * 将持久供应商选择映射到 Agent 已公布的真实会话模型选项。
     *
     * 只使用协议分组和真实 value；无法证明对应关系时返回 null，由新连接读取原生默认值。
     */
    fun sessionModelSelection(
        selection: AgentPersistentConfigChange.SelectProvider,
        options: List<AgentConfigOption>
    ): AgentSessionModelSelection? {
        val modelOption = options.filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
            ?: return null
        val providerModelValue = "${selection.providerId}/${selection.modelId}"
        val choice = modelOption.choices.firstOrNull { candidate ->
            when {
                candidate.groupId == selection.providerId ->
                    candidate.value == selection.modelId || candidate.value == providerModelValue
                candidate.groupId.isNullOrBlank() -> candidate.value == selection.modelId
                else -> false
            }
        } ?: return null
        return AgentSessionModelSelection(modelOption.id, choice.value)
    }

    /**
     * 把协议返回的会话配置补充为该 Agent 已验证过的展示结构。
     *
     * 默认完全透传；特殊 Agent 只能在自己的适配器中补充分组等元数据，公共 ACP 映射和
     * 显示层不能按产品名称猜测协议值。
     */
    fun normalizeSessionConfiguration(
        options: List<AgentConfigOption>
    ): List<AgentConfigOption> = options

    /**
     * 声明当前 Agent 能够把哪些原生推理值安全映射到 Kite 统一语义。
     *
     * 这里声明的是映射词表，不是可见选项全集；实际选项仍必须由当前协议、Provider 和 Model
     * 公布。未声明时统一推理入口隐藏，显示层不得按产品名称或文案猜测。
     */
    fun reasoningControl(): AgentReasoningControl? = null

    /**
     * 声明 Kite 可以为当前会话代理的权限请求处理档位。
     *
     * 默认关闭。适配器必须确认该 Agent 的连接会把审批请求交给 [AgentPermissionRequest]，才能公布；
     * 协议若直接公布 permission 配置，则由连接装饰器按类别优先使用协议能力。
     */
    fun sessionPermissionControl(): AgentSessionPermissionControl? = null

    /**
     * 在协议没有公布对应类别时，把已核验的 Agent 原生模型与会话审批代理投影到会话配置。
     *
     * 原生权限默认值只属于设置页，不能在这里冒充当前会话状态。
     */
    suspend fun readSessionConfiguration(agentId: String): List<AgentConfigOption> {
        val snapshot = (readLive(agentId) as? AgentConfigReadResult.Ready)?.snapshot ?: return emptyList()
        return buildList {
            if (capabilities().supports(AgentPersistentConfigCapability.DefaultModel)) {
                snapshot.modelOption()?.let(::add)
            }
            sessionPermissionControl()?.let { control ->
                val current = snapshot.activePermissionProfileId?.takeIf { profileId ->
                    control.profiles.any { it.id == profileId }
                } ?: control.initialProfileId
                add(control.option(current))
            }
        }
    }

    /**
     * 应用适配器投影的原生会话配置。配置事实仍写回 Agent 原生位置，不在 Kite 另存副本。
     */
    suspend fun applySessionConfiguration(
        agentId: String,
        configId: String,
        value: com.kite.app.agent.contract.AgentConfigValue
    ): AgentSessionConfigurationApplyResult {
        if (value !is com.kite.app.agent.contract.AgentConfigValue.Select) {
            return AgentSessionConfigurationApplyResult.Unsupported("native-session-config:$configId")
        }
        val before = (readLive(agentId) as? AgentConfigReadResult.Ready)?.snapshot
            ?: return AgentSessionConfigurationApplyResult.Failed("无法读取当前 Agent 配置")
        val (change, effect) = when (configId) {
            NATIVE_MODEL_CONFIG_ID -> {
                val option = before.modelOption()
                    ?: return AgentSessionConfigurationApplyResult.Failed("当前 Agent 未提供可选模型")
                val choice = option.choices.firstOrNull { it.value == value.value }
                    ?: return AgentSessionConfigurationApplyResult.Failed("当前 Agent 未提供该模型")
                val providerId = choice.groupId
                    ?: choice.value.substringBefore('/').takeIf { it != choice.value && it.isNotBlank() }
                    ?: return AgentSessionConfigurationApplyResult.Failed("模型缺少供应商信息")
                val prefix = "$providerId/"
                val modelId = choice.value.removePrefix(prefix).takeIf(String::isNotBlank)
                    ?: return AgentSessionConfigurationApplyResult.Failed("模型 ID 无效")
                AgentPersistentConfigChange.SelectProvider(providerId, modelId) to
                    AgentSessionConfigurationEffect.NewSession
            }
            else -> return AgentSessionConfigurationApplyResult.Unsupported("native-session-config:$configId")
        }
        return when (val result = apply(
            AgentConfigApplyRequest(
                agentId = agentId,
                expectedRevision = before.revision,
                changes = listOf(change)
            )
        )) {
            is AgentConfigApplyResult.Applied -> AgentSessionConfigurationApplyResult.Applied(
                options = readSessionConfiguration(agentId),
                effect = effect
            )
            is AgentConfigApplyResult.Conflict -> AgentSessionConfigurationApplyResult.Failed(result.message)
            is AgentConfigApplyResult.Rejected -> AgentSessionConfigurationApplyResult.Failed(
                result.problems.joinToString("；") { it.message }
            )
            is AgentConfigApplyResult.Unavailable -> AgentSessionConfigurationApplyResult.Failed(
                result.discovery.warnings.firstOrNull() ?: "Agent 权限配置当前不可用"
            )
            is AgentConfigApplyResult.Failed -> AgentSessionConfigurationApplyResult.Failed(result.message)
        }
    }

    suspend fun discover(agentId: String): AgentConfigDiscovery

    suspend fun readLive(agentId: String): AgentConfigReadResult

    fun validate(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem>

    suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult

    suspend fun backfill(agentId: String): AgentConfigReadResult = readLive(agentId)

    /** 只返回文档元数据；正文不会随普通设置页读取。 */
    suspend fun listCoreDocuments(
        agentId: String,
        workspacePath: String?
    ): AgentCoreDocumentListResult = AgentCoreDocumentListResult.Ready(emptyList())

    /** 只有用户明确打开某一文档时才读取正文。 */
    suspend fun readCoreDocument(
        agentId: String,
        documentId: String,
        workspacePath: String?
    ): AgentCoreDocumentReadResult = AgentCoreDocumentReadResult.Missing()

    /** 使用独立 revision 写回原生文件，不改变普通配置快照。 */
    suspend fun writeCoreDocument(
        request: AgentCoreDocumentWriteRequest
    ): AgentCoreDocumentWriteResult = AgentCoreDocumentWriteResult.Rejected(
        listOf(AgentConfigValidationProblem("documentId", "当前 Agent 不支持管理核心设定"))
    )

    suspend fun checkMcpServer(agentId: String, serverId: String): AgentMcpConnectionCheckResult =
        AgentMcpConnectionCheckResult.Unsupported()
}

const val NATIVE_MODEL_CONFIG_ID = "kite.default_model"
const val SESSION_PERMISSION_CONFIG_ID = "kite.session_permission"

private const val NATIVE_UNSELECTED_MODEL_VALUE = "kite.model.unselected"

private fun AgentLiveConfigSnapshot.modelOption(): AgentConfigOption.Select? {
    val choices = providers.flatMap { provider ->
        provider.models.map { model ->
            val value = if (model.id.startsWith("${provider.id}/")) model.id else "${provider.id}/${model.id}"
            com.kite.app.agent.contract.AgentConfigChoice(
                value = value,
                name = model.displayName,
                description = null,
                groupId = provider.id,
                groupName = provider.displayName
            )
        }
    }
    if (choices.isEmpty()) return null
    val providerId = activeProviderId ?: providers.singleOrNull()?.id
    val selected = if (providerId == null || defaultModel.isNullOrBlank()) {
        null
    } else if (defaultModel.startsWith("$providerId/")) {
        defaultModel
    } else {
        "$providerId/$defaultModel"
    }
    return AgentConfigOption.Select(
        id = NATIVE_MODEL_CONFIG_ID,
        name = "模型",
        description = "由当前 Agent 原生供应商配置提供；切换后用于新会话",
        category = AgentConfigCategory.Model,
        currentValue = selected?.takeIf { current -> choices.any { it.value == current } }
            ?: NATIVE_UNSELECTED_MODEL_VALUE,
        choices = choices
    )
}
