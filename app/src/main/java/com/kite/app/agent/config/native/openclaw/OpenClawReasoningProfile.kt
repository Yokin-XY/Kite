package com.kite.app.agent.config.native.openclaw

import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentReasoningNativeMapping
import com.kite.app.agent.config.standardReasoningLevelMappings
import com.kite.app.agent.contract.AgentReasoningMode

internal val openClawReasoningControl = AgentReasoningControl(
    standardReasoningLevelMappings() + listOf(
        AgentReasoningNativeMapping("adaptive", AgentReasoningMode.Adaptive),
        AgentReasoningNativeMapping("on", AgentReasoningMode.Enabled),
        AgentReasoningNativeMapping("enabled", AgentReasoningMode.Enabled),
        // 二值 Provider profile 保留 low 作为原生 ID，并由原生标签 on 明确其开关语义。
        AgentReasoningNativeMapping("low", AgentReasoningMode.Enabled, nativeLabel = "on"),
    )
)
