package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentReasoningNativeMapping
import com.kite.app.agent.config.AgentWorkModeCatalog
import com.kite.app.agent.config.standardReasoningLevelMappings
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Reasonix 消费 ACP 会话能力；不复制 config.toml，只读取它真实支持的用户级 Skill 目录。 */
internal class ReasonixAgentConfigAdapter internal constructor(
    containerProvider: () -> ContainerRecord?,
) : ProtocolOnlyAgentConfigAdapter(
    adapterId = ADAPTER_ID,
    containerProvider = containerProvider,
    skillRoots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT, AGENT_SKILL_ROOT, CLAUDE_SKILL_ROOT),
) {
    constructor(context: Context) : this({
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    })

    override fun bundledWorkModeCatalog(agentId: String): AgentWorkModeCatalog = AgentWorkModeCatalog(
        modes = REASONIX_COLLABORATION_MODES.values.toList(),
        defaultModeId = MODE_NORMAL,
    )

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> = modes.map { mode ->
        REASONIX_COLLABORATION_MODES[mode.id] ?: mode
    }

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (option !is AgentConfigOption.Select || option.id != TOOL_APPROVAL_CONFIG_ID) {
                return@mapNotNull option
            }
            val choices = option.choices.mapNotNull { choice ->
                val level = REASONIX_PERMISSION_LEVELS[choice.value] ?: return@mapNotNull null
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
                    description = "Reasonix 当前会话真实提供的工具审批策略",
                    category = AgentConfigCategory.Permission,
                    choices = choices,
                )
            }
        }

    override fun reasoningControl(): AgentReasoningControl = REASONIX_REASONING_CONTROL

    companion object {
        const val ADAPTER_ID = "reasonix"
        private const val SKILL_ROOT = "/root/.reasonix/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val AGENT_SKILL_ROOT = "/root/.agent/skills"
        private const val CLAUDE_SKILL_ROOT = "/root/.claude/skills"
        private const val TOOL_APPROVAL_CONFIG_ID = "tool_approval"
        private const val MODE_NORMAL = "normal"
        private val REASONIX_PERMISSION_LEVELS = mapOf(
            "ask" to AgentPermissionLevel.Approval,
            "auto" to AgentPermissionLevel.Lenient,
            "yolo" to AgentPermissionLevel.Full,
        )
        private val REASONIX_REASONING_CONTROL = AgentReasoningControl(
            standardReasoningLevelMappings() +
                AgentReasoningNativeMapping("auto", AgentReasoningMode.Adaptive),
        )
        private val REASONIX_COLLABORATION_MODES = linkedMapOf(
            MODE_NORMAL to AgentMode(MODE_NORMAL, "常规", "正常协作并按当前权限执行任务"),
            "plan" to AgentMode("plan", "计划", "先分析和规划，再由用户确认后继续"),
            "goal" to AgentMode("goal", "目标", "围绕明确目标持续推进并保持任务状态"),
        )
    }
}
