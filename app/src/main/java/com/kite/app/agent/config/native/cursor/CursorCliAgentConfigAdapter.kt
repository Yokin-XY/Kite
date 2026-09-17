package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.mediatedSessionPermissionControl
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Cursor CLI 通过 ACP 提供模型、会话与审批；Kite 只管理其公开的 MCP/Skill 文件入口。 */
internal class CursorCliAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord?,
) : StandardJsonMcpProtocolAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    agentDisplayName = "Cursor CLI",
    configPath = MCP_PATH,
    containerProvider = containerProvider,
    skillRoots = listOf(CURSOR_SKILL_ROOT, AGENTS_SKILL_ROOT),
    schema = StandardJsonMcpSchema(
        transports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.RemoteHttpOrSse),
        httpUrlKey = "url",
        sseUrlKey = "url",
        typeKey = "type",
        enablement = StandardJsonMcpEnablement.None,
        referenceStyle = StandardJsonMcpReferenceStyle.CursorEnv,
    ),
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun sessionPermissionControl(): AgentSessionPermissionControl =
        mediatedSessionPermissionControl(
            AgentPermissionLevel.Restricted,
            AgentPermissionLevel.Approval,
            AgentPermissionLevel.Full,
        )

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> = modes.map { mode ->
        CURSOR_MODES[mode.id]?.let { presentation ->
            mode.copy(name = presentation.first, description = presentation.second)
        } ?: mode
    }

    companion object {
        const val ADAPTER_ID = "cursor-cli"
        private const val MCP_PATH = "/root/.cursor/mcp.json"
        private const val CURSOR_SKILL_ROOT = "/root/.cursor/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private val CURSOR_MODES = mapOf(
            "agent" to ("Agent" to "完整工具协作模式"),
            "plan" to ("计划" to "只读分析并制定执行计划"),
            "ask" to ("问答" to "只读问答，不主动修改项目"),
        )
    }
}
