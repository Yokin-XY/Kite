package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentToolContent

internal data class AgentPermissionPresentation(
    val title: String,
    val details: List<String>,
    val options: List<Option>,
) {
    data class Option(
        val id: String,
        val name: String,
        val scopeHint: String,
        val allow: Boolean,
    )
}

/**
 * 权限卡片只投影 Agent 已经给出的请求，不推断允许结果，也不保存自动审批策略。
 */
internal object AgentPermissionPresentationPolicy {
    fun present(request: AgentPermissionRequest): AgentPermissionPresentation {
        val toolCall = request.toolCall
        val details = buildList {
            toolCall.kind?.value
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add("工具类型 · $it") }
            toolCall.locations.orEmpty().take(MAX_LOCATIONS).forEach { location ->
                add(location.path + (location.line?.let { ":$it" } ?: ""))
            }
            toolCall.content.orEmpty()
                .mapNotNull(::contentSummary)
                .take(MAX_CONTENT_LINES)
                .forEach(::add)
            if (size <= 1) {
                toolCall.rawInput
                    ?.let(::redactAndBound)
                    ?.takeIf(String::isNotBlank)
                    ?.let { add("参数 · $it") }
            }
        }.distinct().take(MAX_DETAIL_LINES)
        return AgentPermissionPresentation(
            title = toolCall.title.orEmpty().ifBlank { "Agent 请求执行工具" },
            details = details,
            options = request.options.map { option ->
                AgentPermissionPresentation.Option(
                    id = option.id,
                    name = option.name,
                    scopeHint = scopeHint(option.kind),
                    allow = option.kind == AgentPermissionKind.AllowOnce ||
                        option.kind == AgentPermissionKind.AllowAlways,
                )
            },
        )
    }

    private fun contentSummary(content: AgentToolContent): String? = when (content) {
        is AgentToolContent.Content -> when (val value = content.content) {
            is AgentContent.Text -> value.text
            is AgentContent.Image -> "图片 · ${value.mimeType}"
            is AgentContent.Audio -> "音频 · ${value.mimeType}"
            is AgentContent.ResourceLink -> value.title ?: value.name
            is AgentContent.EmbeddedText -> value.text
            is AgentContent.EmbeddedBlob -> "文件 · ${value.mimeType ?: value.uri}"
        }.let(::redactAndBound).takeIf(String::isNotBlank)
        is AgentToolContent.Diff -> "将修改 ${content.path}"
        is AgentToolContent.Terminal -> "终端 · ${content.terminalId}"
    }

    private fun scopeHint(kind: AgentPermissionKind): String = when (kind) {
        AgentPermissionKind.AllowOnce -> "仅这一次"
        AgentPermissionKind.AllowAlways -> "后续同类请求"
        AgentPermissionKind.RejectOnce -> "拒绝这一次"
        AgentPermissionKind.RejectAlways -> "持续拒绝"
    }

    private fun redactAndBound(value: String): String = value
        .replace(JSON_SECRET) { match -> "${match.groupValues[1]}••••${match.groupValues[3]}" }
        .replace(ASSIGNMENT_SECRET) { match -> "${match.groupValues[1]}=••••" }
        .replace(BEARER_SECRET, "Bearer ••••")
        .replace(WHITESPACE, " ")
        .trim()
        .take(MAX_DETAIL_LENGTH)

    private val JSON_SECRET = Regex(
        "(?i)([\\\"'](?:api[_-]?key|token|secret|password|authorization)[\\\"']\\s*:\\s*[\\\"'])([^\\\"']*)([\\\"'])"
    )
    private val ASSIGNMENT_SECRET = Regex(
        "(?i)\\b([A-Z0-9_]*(?:API_KEY|TOKEN|SECRET|PASSWORD|AUTHORIZATION))\\s*=\\s*([^\\s]+)"
    )
    private val BEARER_SECRET = Regex("(?i)Bearer\\s+[^\\s,}\\]]+")
    private val WHITESPACE = Regex("\\s+")
    private const val MAX_LOCATIONS = 3
    private const val MAX_CONTENT_LINES = 2
    private const val MAX_DETAIL_LINES = 4
    private const val MAX_DETAIL_LENGTH = 360
}
