package com.kite.app.recipe

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteRecipeAgentCardTest {
    @Test
    fun newAgentCardRoundTripStoresOnlyStableAgentId() {
        val recipe = recipe(
            KiteRecipeStep(
                id = "agent",
                type = KiteRecipe.STEP_AGENT,
                agentId = "opencode",
                workdir = "/workspace"
            )
        )

        val json = recipe.toJson(includeLocalIdentity = true)
        val stepJson = json.getJSONArray("recipe").getJSONObject(0)
        val restored = KiteRecipe.fromJson(json, KiteRecipe.SOURCE_USER)

        assertEquals("agent", restored.type)
        assertEquals("opencode", restored.steps.single().agentId)
        assertEquals("opencode", stepJson.getString("agentId"))
        assertFalse(stepJson.has("providerId"))
        assertFalse(stepJson.has("argv"))
        assertFalse(stepJson.has("pid"))
    }

    @Test
    fun legacyProviderCardStillLoadsWithoutInventingAgentId() {
        val json = JSONObject(
            """
                {
                  "base": {"id": "legacy", "name": "Legacy", "description": ""},
                  "recipe": [
                    {"type": "agent", "providerId": "opencode", "workdir": "/workspace"}
                  ]
                }
            """.trimIndent()
        )

        val restored = KiteRecipe.fromJson(json, KiteRecipe.SOURCE_USER)
        val serialized = restored.toJson(includeLocalIdentity = true)
            .getJSONArray("recipe")
            .getJSONObject(0)

        assertEquals("", restored.steps.single().agentId.orEmpty())
        assertEquals("opencode", restored.steps.single().providerId)
        assertTrue(serialized.has("providerId"))
        assertFalse(serialized.has("agentId"))
    }

    private fun recipe(step: KiteRecipeStep): KiteRecipe = KiteRecipe(
        id = "agent-card",
        name = "OpenCode",
        description = "Agent card",
        type = KiteRecipe.TYPE_AGENT,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(listOf(step))
    )
}
