package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentMcpTransport
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

    companion object {
        const val ADAPTER_ID = "devin-cli"
        private const val MCP_PATH = "/root/.config/devin/mcp_config.json"
        private const val DEVIN_SKILL_ROOT = "/root/.config/devin/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
    }
}
