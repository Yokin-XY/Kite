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

/** CodeBuddy Code 由 ACP 公布模型与会话；Kite 只管理官方 MCP、Skill 目录和原生权限档。 */
internal class CodeBuddyCodeAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord?,
) : StandardJsonMcpProtocolAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    agentDisplayName = "CodeBuddy Code",
    configPath = MCP_PATH,
    containerProvider = containerProvider,
    skillRoots = listOf(SKILL_ROOT),
    schema = StandardJsonMcpSchema(
        transports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
        ),
        httpUrlKey = "url",
        sseUrlKey = "url",
        typeKey = "type",
        referenceStyle = StandardJsonMcpReferenceStyle.Dollar,
        authorizationBearerPrefix = true,
    ),
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun sessionPermissionControl(): AgentSessionPermissionControl = CODEBUDDY_PERMISSION_CONTROL

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
        modes.filterNot { it.id in CODEBUDDY_PERMISSION_MODES }

    companion object {
        const val ADAPTER_ID = "codebuddy-code"
        private const val MCP_PATH = "/root/.codebuddy/.mcp.json"
        private const val SKILL_ROOT = "/root/.codebuddy/skills"
        private const val MODE_DEFAULT = "default"
        private const val MODE_ACCEPT_EDITS = "acceptEdits"
        private const val MODE_AUTO = "auto"
        private const val MODE_DONT_ASK = "dontAsk"
        private const val MODE_PLAN = "plan"
        private const val MODE_BYPASS = "bypassPermissions"
        private val CODEBUDDY_PERMISSION_MODES = linkedSetOf(
            MODE_DEFAULT,
            MODE_ACCEPT_EDITS,
            MODE_AUTO,
            MODE_DONT_ASK,
            MODE_PLAN,
            MODE_BYPASS,
        )
        private val CODEBUDDY_PERMISSION_CONTROL = AgentSessionPermissionControl(
            profiles = listOf(
                AgentSessionPermissionProfile(
                    id = MODE_DEFAULT,
                    level = AgentPermissionLevel.Approval,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_ACCEPT_EDITS,
                    level = AgentPermissionLevel.Lenient,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_AUTO,
                    level = AgentPermissionLevel.Smart,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_DONT_ASK,
                    level = AgentPermissionLevel.Restricted,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_PLAN,
                    level = AgentPermissionLevel.ReadOnly,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_BYPASS,
                    level = AgentPermissionLevel.Full,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
            ),
            initialProfileId = MODE_DEFAULT,
            nativeModeByProfileId = CODEBUDDY_PERMISSION_MODES.associateWith { it },
        )
    }
}
