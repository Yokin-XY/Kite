package com.kite.app.feature.runsurface

import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentSkillUiPolicyTest {
    @Test
    fun summarizesActivationAndScopeWithoutExposingLocation() {
        val skill = AgentSkillSummary(
            id = "review",
            displayName = "代码审查",
            location = "/root/.config/opencode/skills/review/SKILL.md",
            scope = AgentConfigScope.User,
            activation = AgentSkillActivation.ApprovalRequired,
        )

        assertEquals("每次确认 · 用户级", AgentSkillUiPolicy.summary(skill))
    }
}
