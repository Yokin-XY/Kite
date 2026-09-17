package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.AgentSessionPermissionProfile
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentPermissionLevel
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

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).map { option ->
            if (option !is AgentConfigOption.Select || option.category != AgentConfigCategory.Model) option
            else option.copy(choices = option.choices.map { choice ->
                choice.copy(
                    groupId = OFFICIAL_MODEL_GROUP_ID,
                    groupName = OFFICIAL_MODEL_GROUP_NAME,
                    modelSource = AgentModelSource.OfficialLogin,
                )
            })
        }

    override fun sessionPermissionControl(): AgentSessionPermissionControl = GEMINI_PERMISSION_CONTROL

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
        modes.filterNot { it.id in GEMINI_PERMISSION_MODES }

    companion object {
        const val ADAPTER_ID = "gemini-cli"
        private const val SETTINGS_PATH = "/root/.gemini/settings.json"
        private const val SKILL_ROOT = "/root/.gemini/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val OFFICIAL_MODEL_GROUP_ID = "gemini"
        private const val OFFICIAL_MODEL_GROUP_NAME = "Google Gemini"
        private const val MODE_DEFAULT = "default"
        private const val MODE_AUTO_EDIT = "auto_edit"
        private const val MODE_YOLO = "yolo"
        private const val MODE_PLAN = "plan"
        private val GEMINI_PERMISSION_MODES = linkedSetOf(
            MODE_DEFAULT,
            MODE_AUTO_EDIT,
            MODE_YOLO,
            MODE_PLAN,
        )
        private val GEMINI_PERMISSION_CONTROL = AgentSessionPermissionControl(
            profiles = listOf(
                AgentSessionPermissionProfile(
                    id = MODE_DEFAULT,
                    level = AgentPermissionLevel.Approval,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_AUTO_EDIT,
                    level = AgentPermissionLevel.Lenient,
                    handling = AgentSessionPermissionHandling.PreserveAgentDecision,
                ),
                AgentSessionPermissionProfile(
                    id = MODE_YOLO,
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
            nativeModeByProfileId = GEMINI_PERMISSION_MODES.associateWith { it },
        )
    }
}
