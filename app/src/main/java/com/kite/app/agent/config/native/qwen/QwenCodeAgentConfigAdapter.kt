package com.kite.app.agent.config.native

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel

/** Qwen Code 只消费原生 ACP 会话目录；认证和持久模型配置继续由 qwen CLI 管理。 */
internal class QwenCodeAgentConfigAdapter : ProtocolOnlyAgentConfigAdapter(ADAPTER_ID) {
    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
        modes.filterNot { it.id in QWEN_PERMISSION_LEVELS }

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (
                option !is AgentConfigOption.Select ||
                option.id != MODE_CONFIG_ID ||
                option.category != AgentConfigCategory.Mode
            ) return@mapNotNull option
            val choices = option.choices.mapNotNull { choice ->
                val level = QWEN_PERMISSION_LEVELS[choice.value] ?: return@mapNotNull null
                choice.copy(
                    name = level.displayName,
                    description = level.description,
                    permission = level,
                )
            }
            if (choices.size < 2 || choices.none { it.value == option.currentValue }) {
                null
            } else {
                option.copy(
                    name = "权限",
                    description = "Qwen Code 当前会话真实提供的工具审批模式",
                    category = AgentConfigCategory.Permission,
                    choices = choices,
                )
            }
        }

    companion object {
        const val ADAPTER_ID = "qwen-code"
        private const val MODE_CONFIG_ID = "mode"
        private val QWEN_PERMISSION_LEVELS = mapOf(
            "plan" to AgentPermissionLevel.ReadOnly,
            "default" to AgentPermissionLevel.Approval,
            "auto-edit" to AgentPermissionLevel.Lenient,
            "auto" to AgentPermissionLevel.Smart,
            "yolo" to AgentPermissionLevel.Full,
        )
    }
}
