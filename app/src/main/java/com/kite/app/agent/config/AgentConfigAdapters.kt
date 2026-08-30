package com.kite.app.agent.config

import android.content.Context
import com.kite.app.agent.config.native.AntigravityAgentConfigAdapter
import com.kite.app.agent.config.native.ClaudeCodeAgentConfigAdapter
import com.kite.app.agent.config.native.CodeBuddyCodeAgentConfigAdapter
import com.kite.app.agent.config.native.CopilotAgentConfigAdapter
import com.kite.app.agent.config.native.CodexAgentConfigAdapter
import com.kite.app.agent.config.native.CursorCliAgentConfigAdapter
import com.kite.app.agent.config.native.DeepSeekHarnessAgentConfigAdapter
import com.kite.app.agent.config.native.DevinCliAgentConfigAdapter
import com.kite.app.agent.config.native.HermesAgentConfigAdapter
import com.kite.app.agent.config.native.GeminiCliAgentConfigAdapter
import com.kite.app.agent.config.native.KimiCodeAgentConfigAdapter
import com.kite.app.agent.config.native.MiMoCodeAgentConfigAdapter
import com.kite.app.agent.config.native.OpenClawAgentConfigAdapter
import com.kite.app.agent.config.native.PiCodingAgentConfigAdapter
import com.kite.app.agent.config.native.QwenCodeAgentConfigAdapter
import com.kite.app.agent.config.native.QoderCliAgentConfigAdapter
import com.kite.app.agent.config.native.ReasonixAgentConfigAdapter
import com.kite.app.agent.config.native.TraeCodeAgentConfigAdapter
import com.kite.app.agent.config.native.ZCodeAgentConfigAdapter
import com.kite.app.agent.config.opencode.OpenCodeAgentConfigAdapter

/** 进程内唯一的默认配置适配器集合；页面与 Runtime 共享相同登记，不按 Agent 名称分支。 */
internal fun defaultAgentConfigAdapters(
    context: Context,
    commandExecutor: AgentConfigCommandExecutor? = null
): List<AgentConfigAdapter> = listOf(
    OpenCodeAgentConfigAdapter(
        context.applicationContext,
        commandExecutor = commandExecutor
    ),
    CodexAgentConfigAdapter(context.applicationContext),
    ClaudeCodeAgentConfigAdapter(context.applicationContext),
    CodeBuddyCodeAgentConfigAdapter(context.applicationContext),
    CopilotAgentConfigAdapter(context.applicationContext),
    DeepSeekHarnessAgentConfigAdapter(context.applicationContext),
    KimiCodeAgentConfigAdapter(context.applicationContext),
    HermesAgentConfigAdapter(context.applicationContext),
    GeminiCliAgentConfigAdapter(context.applicationContext),
    AntigravityAgentConfigAdapter(context.applicationContext),
    PiCodingAgentConfigAdapter(context.applicationContext),
    OpenClawAgentConfigAdapter(context.applicationContext),
    MiMoCodeAgentConfigAdapter(context.applicationContext),
    QwenCodeAgentConfigAdapter(context.applicationContext),
    ReasonixAgentConfigAdapter(context.applicationContext),
    TraeCodeAgentConfigAdapter(context.applicationContext),
    ZCodeAgentConfigAdapter(context.applicationContext),
    CursorCliAgentConfigAdapter(context.applicationContext),
    QoderCliAgentConfigAdapter(context.applicationContext),
    DevinCliAgentConfigAdapter(context.applicationContext),
)
