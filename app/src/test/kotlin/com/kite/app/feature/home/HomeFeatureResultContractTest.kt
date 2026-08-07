package com.kite.app.feature.home

import android.os.Bundle
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeFeatureResultContractTest {
    @Test
    fun `home stop target keeps instance and generation across feature request`() {
        val request = KiteRecipeActionRequest(
            recipe = recipe(),
            intent = KiteRecipeActionIntent.Stop,
            source = KiteRecipeActionSource.ConsoleCard,
            instanceId = "agent-run",
            expectedGeneration = 4321L
        )

        val action = HomeFeatureResultContract.actionRequest(request)

        assertEquals("agent-run", action.instanceId)
        assertEquals(4321L, action.expectedGeneration)
        val parsed = HomeFeatureResultContract.parse(
            Bundle().apply {
                putString("kind", "action")
                putString("recipe_id", "agent-recipe")
                putString("intent", KiteRecipeActionIntent.Stop.name)
                putString("source", KiteRecipeActionSource.ConsoleCard.name)
                putString("instance_id", "agent-run")
                putLong("expected_generation", 4321L)
            }
        ) as HomeFeatureRequest.SubmitAction
        assertEquals("agent-run", parsed.instanceId)
        assertEquals(4321L, parsed.expectedGeneration)
    }

    private fun recipe(): KiteRecipe = KiteRecipe(
        id = "agent-recipe",
        name = "Agent",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(emptyList())
    )
}
