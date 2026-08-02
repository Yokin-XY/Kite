package com.kite.app.agent.contract

/**
 * Kite 面向显示层、运行时和第三方 provider 的协议无关 Agent 合同。
 *
 * 此包不得依赖 ACP 或任何具体 Agent SDK。协议适配器只能把外部类型映射进来，
 * 不能要求 UI、CardRunStore 或原生 provider 理解外部协议。
 */
data class AgentProviderInfo(
    val id: String,
    val name: String,
    val version: String? = null,
    val title: String? = null
)

data class AgentProtocolExtension(
    val protocol: String,
    val type: String? = null,
    val payload: String
)

data class AgentCapabilities(
    val prompt: AgentPromptCapabilities = AgentPromptCapabilities(),
    val sessions: AgentSessionCapabilities = AgentSessionCapabilities(),
    val mcp: AgentMcpCapabilities = AgentMcpCapabilities(),
    val authentication: AgentAuthenticationCapabilities = AgentAuthenticationCapabilities(),
    val extension: AgentProtocolExtension? = null
)

data class AgentAuthenticationCapabilities(
    val methods: List<AgentAuthenticationMethod> = emptyList(),
    val logout: Boolean = false,
    val extension: AgentProtocolExtension? = null
)

sealed interface AgentAuthenticationMethod {
    val id: String
    val name: String
    val description: String?
    val extension: AgentProtocolExtension?

    /** 认证流程由 Agent 自己完成，客户端只提交协商得到的 method id。 */
    data class AgentManaged(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentAuthenticationMethod

    /** 需要在受控终端中启动 Agent 声明的登录流程。 */
    data class Terminal(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        val arguments: List<String> = emptyList(),
        val environment: Map<String, String> = emptyMap(),
        override val extension: AgentProtocolExtension? = null
    ) : AgentAuthenticationMethod

    /** 需要由安全配置层收集环境变量；secret 值不得进入普通 JSON、日志或运行快照。 */
    data class EnvironmentVariables(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        val variables: List<AgentAuthenticationVariable>,
        val helpUrl: String? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentAuthenticationMethod

    /** 保留协议新增或 Agent 自定义的认证类型，公共层不猜测执行方式。 */
    data class Extension(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        val type: String,
        val payload: String,
        override val extension: AgentProtocolExtension? = null
    ) : AgentAuthenticationMethod
}

data class AgentAuthenticationVariable(
    val name: String,
    val label: String? = null,
    val secret: Boolean,
    val optional: Boolean,
    val extension: AgentProtocolExtension? = null
)

data class AgentPromptCapabilities(
    val text: Boolean = true,
    val resourceLinks: Boolean = true,
    val images: Boolean = false,
    val audio: Boolean = false,
    val embeddedResources: Boolean = false
)

data class AgentSessionCapabilities(
    val load: Boolean = false,
    val list: Boolean = false,
    val resume: Boolean = false,
    val fork: Boolean = false,
    val close: Boolean = false,
    val delete: Boolean = false,
    val rename: Boolean = false,
    val additionalDirectories: Boolean = false
)

data class AgentMcpCapabilities(
    val stdio: Boolean = true,
    val http: Boolean = false,
    val sse: Boolean = false
)

data class AgentClientCapabilities(
    val readTextFiles: Boolean = false,
    val writeTextFiles: Boolean = false,
    val terminals: Boolean = false,
    val booleanConfiguration: Boolean = false,
    val authentication: Boolean = false
)

data class AgentClientInfo(
    val name: String,
    val version: String,
    val title: String? = null
)

data class AgentConnectionRequest(
    val client: AgentClientInfo,
    val capabilities: AgentClientCapabilities = AgentClientCapabilities()
)

data class AgentNewSessionRequest(
    val cwd: String,
    val additionalDirectories: List<String> = emptyList()
)

data class AgentSessionRenameRequest(
    val sessionId: String,
    val title: String,
)

data class AgentExistingSessionRequest(
    val sessionId: String,
    val cwd: String,
    val additionalDirectories: List<String> = emptyList()
)

data class AgentSessionListRequest(
    val cwd: String? = null,
    val cursor: String? = null
)

data class AgentSessionPage(
    val sessions: List<AgentSessionSummary>,
    val nextCursor: String? = null
)

data class AgentSessionSummary(
    val id: String,
    val cwd: String,
    val title: String? = null,
    val updatedAt: String? = null,
    val additionalDirectories: List<String> = emptyList(),
    val extension: AgentProtocolExtension? = null
)

data class AgentSessionSnapshot(
    val id: String,
    val configuration: List<AgentConfigOption> = emptyList(),
    val modes: List<AgentMode> = emptyList(),
    val currentModeId: String? = null
)

data class AgentMode(
    val id: String,
    val name: String,
    val description: String? = null
)

data class AgentPromptRequest(
    val sessionId: String,
    val content: List<AgentContent>
)

data class AgentTurnResult(
    val stopReason: AgentStopReason,
    val userMessageId: String? = null,
    val usage: AgentTurnUsage? = null
)

data class AgentTurnUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val thoughtTokens: Long? = null,
    val cachedReadTokens: Long? = null,
    val cachedWriteTokens: Long? = null
)

@JvmInline
value class AgentStopReason(val value: String) {
    companion object {
        val EndTurn = AgentStopReason("end_turn")
        val MaxTokens = AgentStopReason("max_tokens")
        val MaxTurnRequests = AgentStopReason("max_turn_requests")
        val Refusal = AgentStopReason("refusal")
        val Cancelled = AgentStopReason("cancelled")
    }
}

sealed interface AgentContent {
    val annotations: AgentContentAnnotations?
    val extension: AgentProtocolExtension?

    data class Text(
        val text: String,
        override val annotations: AgentContentAnnotations? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentContent

    data class Image(
        val data: String,
        val mimeType: String,
        val uri: String? = null,
        override val annotations: AgentContentAnnotations? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentContent

    data class Audio(
        val data: String,
        val mimeType: String,
        override val annotations: AgentContentAnnotations? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentContent

    data class ResourceLink(
        val name: String,
        val uri: String,
        val description: String? = null,
        val mimeType: String? = null,
        val size: Long? = null,
        val title: String? = null,
        override val annotations: AgentContentAnnotations? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentContent

    data class EmbeddedText(
        val text: String,
        val uri: String,
        val mimeType: String? = null,
        override val annotations: AgentContentAnnotations? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentContent

    data class EmbeddedBlob(
        val data: String,
        val uri: String,
        val mimeType: String? = null,
        override val annotations: AgentContentAnnotations? = null,
        override val extension: AgentProtocolExtension? = null
    ) : AgentContent
}

data class AgentContentAnnotations(
    val audience: List<String> = emptyList(),
    val priority: Double? = null,
    val lastModified: String? = null,
    val extension: AgentProtocolExtension? = null
)

@JvmInline
value class AgentConfigCategory(val value: String) {
    companion object {
        val Mode = AgentConfigCategory("mode")
        val Permission = AgentConfigCategory("permission")
        val Model = AgentConfigCategory("model")
        val ModelConfiguration = AgentConfigCategory("model_config")
        val ThoughtLevel = AgentConfigCategory("thought_level")
    }
}

/**
 * Kite 对外稳定的推理强度语义。
 *
 * 这些值只描述一条有序的纯推理强度轴；适配器仍须保留 Agent 的原生配置值，
 * 并且只能公布当前 Provider/Model 真正支持的子集。
 */
enum class AgentReasoningLevel(
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val order: Int,
) : AgentReasoningSemantics {
    Off("off", "关闭", "关闭当前工具可控制的扩展推理；不代表模型完全没有内部推理", 10),
    Minimal("minimal", "最低", "使用最少的可控推理开销", 20),
    Low("low", "低", "优先响应速度与资源开销", 30),
    Medium("medium", "中", "在速度、开销与推理深度之间保持平衡", 40),
    High("high", "高", "为复杂任务使用更深的推理", 50),
    ExtraHigh("xhigh", "极高", "使用模型提供的额外高强度推理", 60),
    Maximum("max", "最高", "使用模型提供的最高纯推理强度", 70),
    Ultra("ultra", "超强", "使用模型提供的 Ultra 推理与主动编排能力", 80),
}

/** 不属于有序强度轴、但可以由 Agent 原生能力明确提供的控制语义。 */
enum class AgentReasoningMode(
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val order: Int,
) : AgentReasoningSemantics {
    Inherit("inherit", "跟随默认", "清除当前会话覆盖，使用工具或模型的默认值", 1),
    Adaptive("adaptive", "自动", "由工具或模型根据任务动态决定推理强度", 2),
    Enabled("enabled", "开启", "当前模型只提供推理开关，具体强度由模型决定", 3),
}

sealed interface AgentReasoningSemantics {
    val id: String
    val displayName: String
    val description: String
    val order: Int
}

sealed interface AgentConfigOption {
    val id: String
    val name: String
    val description: String?
    val category: AgentConfigCategory?
    val extension: AgentProtocolExtension?

    data class Select(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        override val category: AgentConfigCategory? = null,
        val currentValue: String,
        val choices: List<AgentConfigChoice>,
        override val extension: AgentProtocolExtension? = null
    ) : AgentConfigOption

    data class Toggle(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        override val category: AgentConfigCategory? = null,
        val currentValue: Boolean,
        override val extension: AgentProtocolExtension? = null
    ) : AgentConfigOption
}

/**
 * 模型的真实来源。来源由 Agent 适配器显式声明，显示层不得根据模型名或分组名猜测。
 */
enum class AgentModelSource(
    val displayName: String,
) {
    Free("免费"),
    Official("官方"),
    AgentBuiltIn("Agent 内置"),
    UserConfigured("用户自定义"),
}

data class AgentConfigChoice(
    val value: String,
    val name: String,
    val description: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val extension: AgentProtocolExtension? = null,
    /** 原生 value 保持不变；这里仅携带 Kite 已验证的统一推理语义。 */
    val reasoning: AgentReasoningSemantics? = null,
    /** 模型选项才使用；为 null 表示旧适配器尚未声明来源。 */
    val modelSource: AgentModelSource? = null,
)

sealed interface AgentConfigValue {
    data class Select(val value: String) : AgentConfigValue
    data class Toggle(val value: Boolean) : AgentConfigValue
}

sealed interface AgentSessionEvent {
    data class LifecycleChanged(
        val phase: AgentSessionPhase,
        val message: String? = null
    ) : AgentSessionEvent

    data class MessageChunk(
        val role: AgentMessageRole,
        val content: AgentContent,
        val messageId: String? = null,
        val extension: AgentProtocolExtension? = null
    ) : AgentSessionEvent

    data class ToolCallStarted(
        val call: AgentToolCall,
        val extension: AgentProtocolExtension? = null
    ) : AgentSessionEvent

    data class ToolCallUpdated(
        val update: AgentToolCallPatch,
        val extension: AgentProtocolExtension? = null
    ) : AgentSessionEvent

    data class PlanUpdated(
        val entries: List<AgentPlanEntry>,
        val extension: AgentProtocolExtension? = null
    ) : AgentSessionEvent

    data class CommandsUpdated(val commands: List<AgentCommand>) : AgentSessionEvent
    data class CurrentModeChanged(val modeId: String) : AgentSessionEvent
    data class ConfigurationUpdated(
        val options: List<AgentConfigOption>,
        val extension: AgentProtocolExtension? = null
    ) : AgentSessionEvent

    data class SessionInfoChanged(
        val title: String? = null,
        val updatedAt: String? = null,
        val extension: AgentProtocolExtension? = null
    ) : AgentSessionEvent

    data class UsageChanged(
        val used: Long,
        val size: Long,
        val cost: AgentCost? = null,
        val extension: AgentProtocolExtension? = null
    ) : AgentSessionEvent

    data class Extension(
        val type: String,
        val payload: String,
        val metadata: AgentProtocolExtension? = null
    ) : AgentSessionEvent
}

enum class AgentSessionPhase {
    Preparing,
    Ready,
    Prompting,
    WaitingPermission,
    Cancelling,
    Cancelled,
    Failed,
    Closed
}

enum class AgentMessageRole {
    User,
    Assistant,
    Thought
}

data class AgentToolCall(
    val id: String,
    val title: String,
    val kind: AgentToolKind? = null,
    val status: AgentToolStatus? = null,
    val content: List<AgentToolContent> = emptyList(),
    val locations: List<AgentToolLocation> = emptyList(),
    val rawInput: String? = null,
    val rawOutput: String? = null
)

data class AgentToolCallPatch(
    val id: String,
    val title: String? = null,
    val kind: AgentToolKind? = null,
    val status: AgentToolStatus? = null,
    val content: List<AgentToolContent>? = null,
    val locations: List<AgentToolLocation>? = null,
    val rawInput: String? = null,
    val rawOutput: String? = null
)

@JvmInline
value class AgentToolKind(val value: String)

@JvmInline
value class AgentToolStatus(val value: String)

data class AgentToolLocation(
    val path: String,
    val line: Long? = null,
    val extension: AgentProtocolExtension? = null
)

sealed interface AgentToolContent {
    data class Content(val content: AgentContent) : AgentToolContent
    data class Diff(
        val path: String,
        val newText: String,
        val oldText: String? = null,
        val extension: AgentProtocolExtension? = null
    ) : AgentToolContent
    data class Terminal(
        val terminalId: String,
        val extension: AgentProtocolExtension? = null
    ) : AgentToolContent
}

data class AgentPlanEntry(
    val content: String,
    val priority: String,
    val status: String,
    val extension: AgentProtocolExtension? = null
)

data class AgentCommand(
    val name: String,
    val description: String,
    val inputHint: String? = null,
    val extension: AgentProtocolExtension? = null
)

data class AgentCost(
    val amount: Double,
    val currency: String,
    val extension: AgentProtocolExtension? = null
)

data class AgentPermissionRequest(
    val sessionId: String,
    val toolCall: AgentToolCallPatch,
    val options: List<AgentPermissionOption>,
    val extension: AgentProtocolExtension? = null
)

data class AgentPermissionOption(
    val id: String,
    val name: String,
    val kind: AgentPermissionKind,
    val extension: AgentProtocolExtension? = null
)

enum class AgentPermissionKind {
    AllowOnce,
    AllowAlways,
    RejectOnce,
    RejectAlways
}

sealed interface AgentPermissionOutcome {
    data class Selected(val optionId: String) : AgentPermissionOutcome
    data object Cancelled : AgentPermissionOutcome
}

sealed interface AgentOperationResult<out T> {
    data class Success<T>(val value: T) : AgentOperationResult<T>
    data class Unsupported(val operation: String) : AgentOperationResult<Nothing>
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val code: AgentFailureCode? = null,
        val extension: AgentProtocolExtension? = null
    ) : AgentOperationResult<Nothing>
}

@JvmInline
value class AgentFailureCode(val value: String) {
    companion object {
        val AuthenticationRequired = AgentFailureCode("authentication_required")
    }
}

fun interface AgentSessionEventSink {
    fun onEvent(sessionId: String, event: AgentSessionEvent)
}

fun interface AgentPermissionHandler {
    suspend fun request(request: AgentPermissionRequest): AgentPermissionOutcome
}

data class AgentClientEndpoint(
    val eventSink: AgentSessionEventSink,
    val permissionHandler: AgentPermissionHandler
)

/**
 * 一个已经建立连接的 Agent。可选操作必须先由 [capabilities] 声明支持。
 */
interface KiteAgentConnection {
    val provider: AgentProviderInfo
    val capabilities: AgentCapabilities

    suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot>
    suspend fun loadSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot>
    suspend fun listSessions(request: AgentSessionListRequest): AgentOperationResult<AgentSessionPage>
    suspend fun resumeSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot>
    suspend fun forkSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot>
    suspend fun closeSession(sessionId: String): AgentOperationResult<Unit>
    suspend fun deleteSession(sessionId: String): AgentOperationResult<Unit>
    suspend fun renameSession(request: AgentSessionRenameRequest): AgentOperationResult<Unit> =
        AgentOperationResult.Unsupported("session/rename")
    suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult>
    suspend fun setConfiguration(
        sessionId: String,
        configId: String,
        value: AgentConfigValue
    ): AgentOperationResult<List<AgentConfigOption>>
    suspend fun setMode(sessionId: String, modeId: String): AgentOperationResult<Unit> =
        AgentOperationResult.Unsupported("session/set_mode")
    suspend fun authenticate(methodId: String): AgentOperationResult<Unit> =
        AgentOperationResult.Unsupported("authenticate")
    suspend fun logout(): AgentOperationResult<Unit> =
        AgentOperationResult.Unsupported("logout")
    suspend fun cancel(sessionId: String): AgentOperationResult<Unit>
    suspend fun disconnect()
}

/**
 * Provider 封装一种具体连接方式；ACP、原生 Agent 或其他协议均可实现。
 */
interface KiteAgentProvider {
    val id: String

    suspend fun connect(
        request: AgentConnectionRequest,
        client: AgentClientEndpoint
    ): AgentOperationResult<KiteAgentConnection>
}
