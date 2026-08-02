package com.kite.app.agent.config.opencode

import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.standardReasoningLevelMappings

/** OpenCode 原生推理值映射；只过滤它真实公布的选项，不补造档位。 */
internal val openCodeReasoningControl = AgentReasoningControl(standardReasoningLevelMappings())
