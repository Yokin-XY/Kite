package com.kite.app.agent.config.native.codex

import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentReasoningNativeMapping
import com.kite.app.agent.config.standardReasoningLevelMappings
import com.kite.app.agent.contract.AgentReasoningLevel

internal val codexReasoningControl = AgentReasoningControl(
    standardReasoningLevelMappings() +
        AgentReasoningNativeMapping("ultra", AgentReasoningLevel.Maximum)
)
