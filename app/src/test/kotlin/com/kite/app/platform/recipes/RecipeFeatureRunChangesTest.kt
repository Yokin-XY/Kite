package com.kite.app.platform.recipes

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeFeatureRunChangesTest {
    @Test
    fun `returning home receives the current run snapshot as the first change`() = runTest {
        val runs = MutableStateFlow(
            listOf(
                CardRunState(
                    instanceId = "agent-run",
                    recipeId = "agent-recipe",
                    status = CardRunStatus.Running
                )
            )
        )

        val change = runs.asRecipeFeatureChanges().first()

        assertEquals("card_run_state", change.reason)
        assertEquals(setOf("agent-recipe"), change.affectedRecipeIds)
    }
}
