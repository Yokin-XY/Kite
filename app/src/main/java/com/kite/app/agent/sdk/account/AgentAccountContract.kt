package com.kite.app.agent.sdk.account

/**
 * Agent 官方账号能力的统一边界。
 *
 * UI 和 SDK 只处理账号意图、稳定 ID 以及非敏感元数据；原生凭据快照只在账号协调器和
 * Agent Adapter 之间短暂流转，不提供给页面状态、日志或 Provider 目录。
 */
internal enum class AgentAccountCapability {
    SaveCurrent,
    Switch,
    Delete,
    StableId,
}

internal data class AgentAccountCapabilities(
    val supported: Set<AgentAccountCapability>,
) {
    fun supports(capability: AgentAccountCapability): Boolean = capability in supported
}

internal data class AgentAccountIdentity(
    val accountId: String,
    val displayName: String,
)

/** 不透明的 Agent 原生凭据快照；toString 永远不包含凭据内容。 */
internal class AgentAccountCredentialSnapshot internal constructor(
    val bytes: ByteArray,
) {
    override fun toString(): String =
        "AgentAccountCredentialSnapshot(size=${bytes.size})"
}

internal sealed interface AgentAccountIdentityResult {
    data class Ready(val identity: AgentAccountIdentity) : AgentAccountIdentityResult
    data class Unavailable(val message: String) : AgentAccountIdentityResult
    data class Failed(val message: String) : AgentAccountIdentityResult
}

internal sealed interface AgentAccountCredentialReadResult {
    /** 身份与凭据必须来自同一次原生快照，避免账号在两次读取之间变化。 */
    data class Ready(
        val snapshot: AgentAccountCredentialSnapshot,
        val identity: AgentAccountIdentity,
    ) : AgentAccountCredentialReadResult
    data class Missing(val message: String = "当前 Agent 没有可保存的官方凭据") : AgentAccountCredentialReadResult
    data class Unavailable(val message: String) : AgentAccountCredentialReadResult
    data class Failed(val message: String) : AgentAccountCredentialReadResult
}

internal sealed interface AgentAccountCredentialWriteResult {
    data object Applied : AgentAccountCredentialWriteResult
    data class Unavailable(val message: String) : AgentAccountCredentialWriteResult
    data class Failed(val message: String, val restored: Boolean) : AgentAccountCredentialWriteResult
}

/**
 * 具体 Agent 拥有真实凭据位置和文件格式；Kite 不在 UI 或公共 Provider API 中处理这些内容。
 */
internal interface AgentOfficialAccountAdapter {
    val adapterId: String

    fun accountCapabilities(): AgentAccountCapabilities

    suspend fun currentIdentity(agentId: String): AgentAccountIdentityResult

    suspend fun captureCurrent(agentId: String): AgentAccountCredentialReadResult

    suspend fun restoreCurrent(
        agentId: String,
        snapshot: AgentAccountCredentialSnapshot,
    ): AgentAccountCredentialWriteResult
}
