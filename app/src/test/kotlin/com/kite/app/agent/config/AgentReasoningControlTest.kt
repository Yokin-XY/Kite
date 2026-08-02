package com.kite.app.agent.config

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.agent.config.native.claudecode.claudeCodeReasoningControl
import com.kite.app.agent.config.native.codex.codexReasoningControl
import com.kite.app.agent.config.native.hermes.hermesReasoningControl
import com.kite.app.agent.config.native.openclaw.openClawReasoningControl
import com.kite.app.agent.config.opencode.openCodeReasoningControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentReasoningControlTest {
    @Test
    fun stableReasoningAxisContainsOnlyTheSevenConfirmedLevels() {
        assertEquals(
            listOf("off", "minimal", "low", "medium", "high", "xhigh", "max"),
            AgentReasoningLevel.entries.sortedBy { it.order }.map { it.id },
        )
    }

    @Test
    fun openClawKeepsPublishedSubsetAndProjectsBinaryAndAdaptiveSemantics() {
        val normalized = openClawReasoningControl.normalize(
            select(
                current = "high",
                choices = listOf(
                    AgentConfigChoice("off", "Off"),
                    AgentConfigChoice("low", "on"),
                    AgentConfigChoice("high", "High"),
                    AgentConfigChoice("adaptive", "Adaptive"),
                    AgentConfigChoice("ultra", "Ultra"),
                ),
            )
        ).single() as AgentConfigOption.Select

        assertEquals(listOf("adaptive", "low", "off", "high"), normalized.choices.map { it.value })
        assertEquals(
            listOf(
                AgentReasoningMode.Adaptive,
                AgentReasoningMode.Enabled,
                AgentReasoningLevel.Off,
                AgentReasoningLevel.High,
            ),
            normalized.choices.map { it.reasoning },
        )
        assertEquals("high", normalized.currentValue)
        assertTrue(normalized.choices.none { it.value == "ultra" })
    }

    @Test
    fun claudeDefaultIsNotPublishedAndAutoUsesTheConfirmedAdaptiveMeaning() {
        assertTrue(claudeCodeReasoningControl.normalize(
            select(
                current = "default",
                choices = listOf(
                    AgentConfigChoice("default", "Default"),
                    AgentConfigChoice("high", "High"),
                    AgentConfigChoice("max", "Max"),
                    AgentConfigChoice("ultracode", "Ultracode"),
                ),
            )
        ).isEmpty())

        val normalized = claudeCodeReasoningControl.normalize(
            select(
                current = "auto",
                choices = listOf(
                    AgentConfigChoice("auto", "Auto"),
                    AgentConfigChoice("high", "High"),
                    AgentConfigChoice("ultracode", "Ultracode"),
                ),
            )
        ).single() as AgentConfigOption.Select
        assertEquals(listOf("auto", "high"), normalized.choices.map { it.value })
        assertEquals(AgentReasoningMode.Adaptive, normalized.choices.first().reasoning)
    }

    @Test
    fun unmappedOrNonSelectableReasoningDoesNotPublishASelector() {
        assertTrue(openCodeReasoningControl.normalize(
            select(
                current = "fast",
                choices = listOf(
                    AgentConfigChoice("fast", "Fast"),
                    AgentConfigChoice("deep", "Deep"),
                ),
            )
        ).isEmpty())

        assertTrue(codexReasoningControl.normalize(
            select(
                current = "medium",
                choices = listOf(AgentConfigChoice("medium", "Medium")),
            )
        ).isEmpty())

        val ultra = codexReasoningControl.normalize(
            select(
                current = "ultra",
                choices = listOf(
                    AgentConfigChoice("high", "High"),
                    AgentConfigChoice("ultra", "Ultra"),
                ),
            )
        ).single() as AgentConfigOption.Select
        assertEquals(AgentReasoningLevel.Maximum, ultra.choices.last().reasoning)
    }

    @Test
    fun binaryThoughtToggleRemainsAvailable() {
        val normalized = hermesReasoningControl.normalize(
            listOf(
                AgentConfigOption.Toggle(
                    id = "thinking",
                    name = "Thinking",
                    category = AgentConfigCategory.ThoughtLevel,
                    currentValue = true,
                )
            )
        ).single() as AgentConfigOption.Toggle

        assertEquals("推理强度", normalized.name)
        assertTrue(normalized.currentValue)
    }

    private fun select(
        current: String,
        choices: List<AgentConfigChoice>,
    ) = listOf(
        AgentConfigOption.Select(
            id = "reasoning",
            name = "Reasoning",
            category = AgentConfigCategory.ThoughtLevel,
            currentValue = current,
            choices = choices,
        )
    )
}
