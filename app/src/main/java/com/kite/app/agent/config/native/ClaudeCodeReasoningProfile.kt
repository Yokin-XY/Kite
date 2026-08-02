package com.kite.app.agent.config.native

import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentReasoningNativeMapping
import com.kite.app.agent.config.standardReasoningLevelMappings
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningMode

internal val claudeCodeReasoningControl = AgentReasoningControl(
    standardReasoningLevelMappings(
        levels = setOf(
            AgentReasoningLevel.Off,
            AgentReasoningLevel.Low,
            AgentReasoningLevel.Medium,
            AgentReasoningLevel.High,
            AgentReasoningLevel.ExtraHigh,
            AgentReasoningLevel.Maximum,
        )
    ) + AgentReasoningNativeMapping("auto", AgentReasoningMode.Adaptive)
)
