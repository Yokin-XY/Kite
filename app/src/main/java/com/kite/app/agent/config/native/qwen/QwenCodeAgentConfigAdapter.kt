package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Qwen Code 消费原生 ACP 会话目录；认证模型由 CLI 管理，Skill 直接读取原生用户目录。 */
internal class QwenCodeAgentConfigAdapter internal constructor(
    containerProvider: () -> ContainerRecord?,
) : ProtocolOnlyAgentConfigAdapter(
    adapterId = ADAPTER_ID,
    containerProvider = containerProvider,
    skillRoots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
) {
    constructor(context: Context) : this({
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    })

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
        private const val SKILL_ROOT = "/root/.qwen/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
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
