package com.kite.app.agent.sdk.configuration

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgentControlCatalogTest {
    @Test
    fun `fixed catalog only includes explicitly mapped model permission and reasoning options`() {
        val catalog = AgentControlCatalogProjector.project(
            listOf(
                AgentConfigOption.Select(
                    id = "model",
                    name = "模型",
                    category = AgentConfigCategory.Model,
                    currentValue = "opencode/free-small",
                    choices = listOf(
                        AgentConfigChoice(
                            value = "opencode/free-small",
                            name = "Free Small",
                            groupId = "opencode",
                            modelSource = AgentModelSource.Free,
                        ),
                        AgentConfigChoice("unknown/model", "Unknown", groupId = "unknown"),
                    ),
                ),
                AgentConfigOption.Select(
                    id = "permission",
                    name = "权限",
                    category = AgentConfigCategory.Permission,
                    currentValue = "ask",
                    choices = listOf(
                        AgentConfigChoice("ask", "Ask", permission = AgentPermissionLevel.Approval),
                        AgentConfigChoice("full", "Full", permission = AgentPermissionLevel.Full),
                        AgentConfigChoice("native", "Native"),
                    ),
                ),
                AgentConfigOption.Select(
                    id = "reasoning",
                    name = "推理强度",
                    category = AgentConfigCategory.ThoughtLevel,
                    currentValue = "auto",
                    choices = listOf(
                        AgentConfigChoice("default", "Default"),
                        AgentConfigChoice("auto", "Auto", reasoning = AgentReasoningMode.Adaptive),
                        AgentConfigChoice("high", "High", reasoning = AgentReasoningLevel.High),
                    ),
                ),
            )
        )

        assertEquals(listOf("opencode/free-small"), catalog.model?.choices?.map { it.selection.nativeValue })
        assertEquals(AgentModelSource.Free, catalog.model?.current?.source)
        assertEquals(
            listOf(AgentPermissionLevel.Approval, AgentPermissionLevel.Full),
            catalog.permission?.choices?.map { it.level },
        )
        val reasoning = catalog.reasoning as AgentReasoningControlCatalog.Select
        assertEquals(listOf("auto", "high"), reasoning.choices.map { it.nativeValue })
    }

    @Test
    fun `unmapped current values do not create guessed components`() {
        val catalog = AgentControlCatalogProjector.project(
            listOf(
                AgentConfigOption.Select(
                    id = "model",
                    name = "模型",
                    category = AgentConfigCategory.Model,
                    currentValue = "unknown/model",
                    choices = listOf(AgentConfigChoice("unknown/model", "Unknown")),
                ),
                AgentConfigOption.Select(
                    id = "reasoning",
                    name = "推理强度",
                    category = AgentConfigCategory.ThoughtLevel,
                    currentValue = "default",
                    choices = listOf(
                        AgentConfigChoice("default", "Default"),
                        AgentConfigChoice("high", "High", reasoning = AgentReasoningLevel.High),
                    ),
                ),
            )
        )

        assertNull(catalog.model)
        assertNull(catalog.reasoning)
        assertNotNull(AgentControlCatalogProjector.project(listOf(
            AgentConfigOption.Toggle(
                id = "thinking",
                name = "Thinking",
                category = AgentConfigCategory.ThoughtLevel,
                currentValue = true,
            )
        )).reasoning)
    }
}
