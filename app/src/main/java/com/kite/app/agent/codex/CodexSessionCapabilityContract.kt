package com.kite.app.agent.codex

import com.kite.app.agent.contract.AGENT_SESSION_PERMISSION_CONFIG_ID
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentPermissionLevel

internal enum class CodexPermission(
    val id: String,
    val displayName: String,
    val description: String,
    val level: AgentPermissionLevel,
) {
    Ask(
        "codex.permission.ask",
        "请求批准",
        "在工作区内运行；需要越过边界时由你确认",
        AgentPermissionLevel.Approval,
    ),
    AutoReview(
        "codex.permission.auto_review",
        "替我审批",
        "保持工作区边界，由 Codex 自动审查需要批准的请求",
        AgentPermissionLevel.Smart,
    ),
    FullAccess(
        "codex.permission.full_access",
        "完全访问权限",
        "不使用普通沙箱和审批限制",
        AgentPermissionLevel.Full,
    ),
    Custom(
        "codex.permission.custom",
        "自定义",
        "不添加 Kite 覆盖，使用 config.toml 中的原生权限规则",
        AgentPermissionLevel.Custom,
    ),
}

internal fun codexPermissionOption(
    current: CodexPermission = CodexPermission.Custom,
): AgentConfigOption.Select = AgentConfigOption.Select(
    id = AGENT_SESSION_PERMISSION_CONFIG_ID,
    name = "权限",
    description = "Codex 当前会话的审批与沙箱方式",
    category = AgentConfigCategory.Permission,
    currentValue = current.id,
    choices = CodexPermission.entries.map { permission ->
        AgentConfigChoice(
            value = permission.id,
            name = permission.displayName,
            description = permission.description,
            permission = permission.level,
        )
    },
)
