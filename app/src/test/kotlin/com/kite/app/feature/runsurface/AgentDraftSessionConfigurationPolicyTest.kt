package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentDraftSessionConfigurationPolicyTest {
    @Test
    fun persistentProviderModelCatalogReplacesNarrowRuntimeModelCatalog() {
        val persistent = select(
            id = AgentDraftModelPolicy.CONFIG_ID,
            category = AgentConfigCategory.Model,
            values = listOf("zhipu/glm-5.3-flash", "zhipu/glm-5.3", "zhipu/glm-5.2"),
        )
        val runtime = select(
            id = "agent.runtime.model",
            category = AgentConfigCategory.Model,
            values = listOf("glm-5.3-flash"),
        )

        val merged = AgentDraftSessionConfigurationPolicy.merge(
            storedControls = emptyList(),
            runtimeOptions = listOf(runtime),
            runtimeResolvedCategories = setOf(AgentConfigCategory.Model),
            persistentModel = persistent,
        )

        assertEquals(listOf(persistent), merged)
        assertEquals(3, (merged.single() as AgentConfigOption.Select).choices.size)
    }

    @Test
    fun runtimeKeepsOwnershipOfResolvedNonModelControls() {
        val storedPermission = select(
            id = "kite.permission",
            category = AgentConfigCategory.Permission,
            values = listOf("default", "full"),
        )
        val runtimePermission = select(
            id = "agent.permission",
            category = AgentConfigCategory.Permission,
            values = listOf("ask", "auto"),
        )

        val merged = AgentDraftSessionConfigurationPolicy.merge(
            storedControls = listOf(storedPermission),
            runtimeOptions = listOf(runtimePermission),
            runtimeResolvedCategories = setOf(AgentConfigCategory.Permission),
            persistentModel = null,
        )

        assertEquals(listOf(runtimePermission), merged)
    }

    @Test
    fun storedControlRemainsWhenRuntimeDoesNotOwnItsCategory() {
        val storedMode = select(
            id = "kite.mode",
            category = AgentConfigCategory.Mode,
            values = listOf("default", "plan"),
        )

        val merged = AgentDraftSessionConfigurationPolicy.merge(
            storedControls = listOf(storedMode),
            runtimeOptions = emptyList(),
            runtimeResolvedCategories = emptySet(),
            persistentModel = null,
        )

        assertEquals(listOf(storedMode), merged)
    }

    private fun select(
        id: String,
        category: AgentConfigCategory,
        values: List<String>,
    ) = AgentConfigOption.Select(
        id = id,
        name = id,
        category = category,
        currentValue = values.first(),
        choices = values.map { AgentConfigChoice(it, it) },
    )
}
