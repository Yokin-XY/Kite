package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.contract.AgentMode
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Gemini CLI 的 Provider、模型和会话状态由 ACP 管理；Kite 管理已核验的 MCP 与 Skill 原生入口。 */
internal class GeminiCliAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord?,
) : StandardJsonMcpProtocolAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    agentDisplayName = "Gemini CLI",
    configPath = SETTINGS_PATH,
    containerProvider = containerProvider,
    skillRoots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> = modes.map { mode ->
        GEMINI_MODES[mode.id]?.let { presentation ->
            mode.copy(name = presentation.first, description = presentation.second)
        } ?: mode
    }

    companion object {
        const val ADAPTER_ID = "gemini-cli"
        private const val SETTINGS_PATH = "/root/.gemini/settings.json"
        private const val SKILL_ROOT = "/root/.gemini/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private val GEMINI_MODES = mapOf(
            "default" to ("审批" to "工具执行前按 Gemini CLI 原生策略请求确认"),
            "auto_edit" to ("自动编辑" to "自动批准文件编辑，其他工具仍按原生策略确认"),
            "yolo" to ("完全" to "自动批准 Gemini CLI 的全部工具调用"),
            "plan" to ("计划" to "只读分析并制定计划"),
        )
    }
}
