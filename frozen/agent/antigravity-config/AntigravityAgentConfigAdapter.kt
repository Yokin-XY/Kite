package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.AgentSessionPermissionProfile
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Antigravity 的模型和会话由 stream-json 公布；这里只映射 Skill 与真实启动权限模式。 */
internal class AntigravityAgentConfigAdapter internal constructor(
    containerProvider: () -> ContainerRecord?,
) : ProtocolOnlyAgentConfigAdapter(
    adapterId = ADAPTER_ID,
    containerProvider = containerProvider,
    skillRoots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
) {
    constructor(context: Context) : this(
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun sessionPermissionControl(): AgentSessionPermissionControl = PERMISSION_CONTROL

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
        modes.filterNot { it.id in PERMISSION_MODES }

    companion object {
        const val ADAPTER_ID = "google-antigravity"
        private const val SKILL_ROOT = "/root/.gemini/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val MODE_DEFAULT = "default"
        private const val MODE_FULL = "yolo"
        private const val MODE_PLAN = "plan"
        private val PERMISSION_MODES = linkedSetOf(MODE_DEFAULT, MODE_FULL, MODE_PLAN)
        private val PERMISSION_CONTROL = AgentSessionPermissionControl(
            profiles = listOf(
                AgentSessionPermissionProfile(
                    id = MODE_DEFAULT,
                    level = AgentPermissionLevel.Lenient,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_FULL,
                    level = AgentPermissionLevel.Full,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_PLAN,
                    level = AgentPermissionLevel.ReadOnly,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
            ),
            initialProfileId = MODE_DEFAULT,
            nativeModeByProfileId = PERMISSION_MODES.associateWith { it },
        )
    }
}
