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

/** Qoder CLI 由 ACP 提供模型与会话；Kite 投影其两档原生权限并管理 MCP/Skill 原生目录。 */
internal class QoderCliAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord?,
) : StandardJsonMcpProtocolAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    agentDisplayName = "Qoder CLI",
    configPath = SETTINGS_PATH,
    containerProvider = containerProvider,
    skillRoots = listOf(QODER_SKILL_ROOT, AGENTS_SKILL_ROOT),
    schema = StandardJsonMcpSchema(
        transports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
        ),
        httpUrlKey = "url",
        sseUrlKey = "url",
        typeKey = "type",
        enablement = StandardJsonMcpEnablement.DisabledBoolean,
    ),
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun sessionPermissionControl(): AgentSessionPermissionControl = QODER_PERMISSION_CONTROL

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
        modes.filterNot { it.id in QODER_PERMISSION_MODES }

    companion object {
        const val ADAPTER_ID = "qoder-cli"
        private const val SETTINGS_PATH = "/root/.qoder/settings.json"
        private const val QODER_SKILL_ROOT = "/root/.qoder/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val MODE_DEFAULT = "default"
        private const val MODE_BYPASS = "bypass_permissions"
        private val QODER_PERMISSION_MODES = setOf(MODE_DEFAULT, MODE_BYPASS)
        private val QODER_PERMISSION_CONTROL = AgentSessionPermissionControl(
            profiles = listOf(
                AgentSessionPermissionProfile(
                    id = MODE_DEFAULT,
                    level = AgentPermissionLevel.Approval,
                    handling = AgentSessionPermissionHandling.AskUser,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_BYPASS,
                    level = AgentPermissionLevel.Full,
                    handling = AgentSessionPermissionHandling.AllowRequest,
                ),
            ),
            initialProfileId = MODE_DEFAULT,
            nativeModeByProfileId = mapOf(
                MODE_DEFAULT to MODE_DEFAULT,
                MODE_BYPASS to MODE_BYPASS,
            ),
        )
    }
}
