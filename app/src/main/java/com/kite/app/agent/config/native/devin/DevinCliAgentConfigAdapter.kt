package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.AgentSessionPermissionProfile
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Devin CLI 的模型、推理、权限和会话由 ACP 公布；这里管理已公开的全局 MCP 与 Skill 文件。 */
internal class DevinCliAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord?,
) : StandardJsonMcpProtocolAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    agentDisplayName = "Devin CLI",
    configPath = MCP_PATH,
    containerProvider = containerProvider,
    skillRoots = listOf(DEVIN_SKILL_ROOT, AGENTS_SKILL_ROOT),
    schema = StandardJsonMcpSchema(
        transports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
            AgentMcpTransport.RemoteHttpOrSse,
        ),
        httpUrlKey = "url",
        sseUrlKey = "url",
        typeKey = "transport",
        enablement = StandardJsonMcpEnablement.DisabledBoolean,
        referenceStyle = StandardJsonMcpReferenceStyle.CursorEnv,
    ),
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun sessionPermissionControl(): AgentSessionPermissionControl = DEVIN_PERMISSION_CONTROL

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> = modes
        .filterNot { it.id == MODE_AUTONOMOUS }
        .filterNot { it.id in DEVIN_PERMISSION_MODES }
        .map { mode ->
            if (mode.id == MODE_PLAN) {
                mode.copy(name = "计划", description = "只读分析并制定执行计划")
            } else {
                mode
            }
        }

    companion object {
        const val ADAPTER_ID = "devin-cli"
        private const val MCP_PATH = "/root/.config/devin/mcp_config.json"
        private const val DEVIN_SKILL_ROOT = "/root/.config/devin/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val MODE_NORMAL = "normal"
        private const val MODE_ACCEPT_EDITS = "accept-edits"
        private const val MODE_SMART = "smart"
        private const val MODE_BYPASS = "bypass"
        private const val MODE_PLAN = "plan"
        private const val MODE_AUTONOMOUS = "autonomous"
        private val DEVIN_PERMISSION_MODES = setOf(
            MODE_NORMAL,
            MODE_ACCEPT_EDITS,
            MODE_SMART,
            MODE_BYPASS,
        )
        private val DEVIN_PERMISSION_CONTROL = AgentSessionPermissionControl(
            profiles = listOf(
                AgentSessionPermissionProfile(
                    id = MODE_NORMAL,
                    level = AgentPermissionLevel.Approval,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_ACCEPT_EDITS,
                    level = AgentPermissionLevel.Lenient,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_SMART,
                    level = AgentPermissionLevel.Smart,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_BYPASS,
                    level = AgentPermissionLevel.Full,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
            ),
            initialProfileId = MODE_NORMAL,
            nativeModeByProfileId = DEVIN_PERMISSION_MODES.associateWith { it },
        )
    }
}
