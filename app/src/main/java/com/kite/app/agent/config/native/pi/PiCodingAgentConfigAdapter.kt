package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Pi 的 Provider、模型与推理强度由 RPC 管理；Kite 只统一读取原生 Skill 目录。 */
internal class PiCodingAgentConfigAdapter internal constructor(
    containerProvider: () -> ContainerRecord?,
) : ProtocolOnlyAgentConfigAdapter(
    adapterId = ADAPTER_ID,
    containerProvider = containerProvider,
    skillRoots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
) {
    constructor(context: Context) : this({
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    })

    companion object {
        const val ADAPTER_ID = "pi-coding-agent"
        private const val SKILL_ROOT = "/root/.pi/agent/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
    }
}
