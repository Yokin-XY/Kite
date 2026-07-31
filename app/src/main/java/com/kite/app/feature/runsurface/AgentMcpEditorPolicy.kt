package com.kite.app.feature.runsurface

import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpConnectionState
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import java.net.URI

internal sealed interface AgentMcpDraftBuildResult {
    data class Ready(val draft: AgentMcpDraft) : AgentMcpDraftBuildResult
    data class Invalid(val message: String) : AgentMcpDraftBuildResult
}

/** MCP 编辑页的纯表单规则；不认识 OpenCode JSON，也不接触 Header 或环境变量真值。 */
internal object AgentMcpEditorPolicy {
    fun buildDraft(
        id: String,
        transport: AgentMcpTransport,
        enabled: Boolean,
        command: String,
        argumentsText: String,
        url: String,
        referencesText: String
    ): AgentMcpDraftBuildResult {
        val normalizedId = id.trim()
        if (normalizedId.isEmpty()) {
            return AgentMcpDraftBuildResult.Invalid("请输入 MCP ID")
        }
        if (!SAFE_ID.matches(normalizedId)) {
            return AgentMcpDraftBuildResult.Invalid("MCP ID 只能包含字母、数字、点、横线和下划线")
        }
        val references = when (val result = parseReferences(referencesText)) {
            is ReferenceResult.Invalid -> return AgentMcpDraftBuildResult.Invalid(result.message)
            is ReferenceResult.Ready -> result.references
        }
        return when (transport) {
            AgentMcpTransport.Stdio -> {
                val executable = command.trim()
                if (executable.isBlank() || executable.any(Char::isISOControl)) {
                    AgentMcpDraftBuildResult.Invalid("请输入本地 MCP 命令")
                } else {
                    AgentMcpDraftBuildResult.Ready(
                        AgentMcpDraft(
                            id = normalizedId,
                            transport = transport,
                            enabled = enabled,
                            command = executable,
                            arguments = argumentsText.lineSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toList(),
                            environmentReferences = references
                        )
                    )
                }
            }
            AgentMcpTransport.RemoteHttpOrSse,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                val normalizedUrl = url.trim()
                val uri = runCatching { URI(normalizedUrl) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    AgentMcpDraftBuildResult.Invalid("请输入有效的 HTTP 或 HTTPS MCP 地址")
                } else {
                    AgentMcpDraftBuildResult.Ready(
                        AgentMcpDraft(
                            id = normalizedId,
                            transport = transport,
                            enabled = enabled,
                            url = normalizedUrl,
                            headerReferences = references
                        )
                    )
                }
            }
            AgentMcpTransport.Unknown -> AgentMcpDraftBuildResult.Invalid("当前 MCP 传输类型无法安全编辑")
        }
    }

    fun referencesText(references: List<AgentMcpEnvironmentReference>): String =
        references.joinToString("\n") { "${it.name}=${it.environmentVariable}" }

    private fun parseReferences(text: String): ReferenceResult {
        val output = mutableListOf<AgentMcpEnvironmentReference>()
        text.lineSequence().map(String::trim).filter(String::isNotEmpty).forEachIndexed { index, line ->
            val name = line.substringBefore('=', missingDelimiterValue = "").trim()
            val variable = line.substringAfter('=', missingDelimiterValue = "").trim()
            if (!SAFE_KEY.matches(name) || !ENVIRONMENT_NAME.matches(variable)) {
                return ReferenceResult.Invalid("第 ${index + 1} 行应使用 名称=环境变量名")
            }
            output += AgentMcpEnvironmentReference(name, variable)
        }
        if (output.map(AgentMcpEnvironmentReference::name).distinct().size != output.size) {
            return ReferenceResult.Invalid("引用名称不能重复")
        }
        return ReferenceResult.Ready(output)
    }

    private sealed interface ReferenceResult {
        data class Ready(val references: List<AgentMcpEnvironmentReference>) : ReferenceResult
        data class Invalid(val message: String) : ReferenceResult
    }

    private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val SAFE_KEY = Regex("[A-Za-z0-9_.$-]{1,128}")
    private val ENVIRONMENT_NAME = Regex("[A-Z_][A-Z0-9_]{0,127}")
}

internal object AgentMcpUiPolicy {
    fun supportsConnectionCheck(server: AgentMcpSummary): Boolean =
        server.enabled && AgentMcpOperation.CheckConnection in server.allowedOperations

    fun transportLabel(server: AgentMcpSummary): String = when (server.transport) {
        AgentMcpTransport.Stdio -> "本地命令"
        AgentMcpTransport.StreamableHttp -> "HTTP"
        AgentMcpTransport.Sse -> "SSE"
        AgentMcpTransport.RemoteHttpOrSse -> "远程地址"
        AgentMcpTransport.Unknown -> server.kind.ifBlank { "未知类型" }
    }

    fun connectionLabel(
        server: AgentMcpSummary,
        state: AgentMcpConnectionState
    ): String = when {
        !server.enabled -> "已停用"
        state == AgentMcpConnectionState.Checking -> "正在检查连接…"
        state == AgentMcpConnectionState.Available -> "可用"
        state == AgentMcpConnectionState.Unavailable -> "不可用"
        !supportsConnectionCheck(server) -> "已保存，连接由 Agent 加载时确认"
        else -> "已保存，尚未检查"
    }
}
