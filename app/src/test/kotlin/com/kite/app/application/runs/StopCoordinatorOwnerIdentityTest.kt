package com.kite.app.application.runs

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.TestRecipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopCoordinatorOwnerIdentityTest {
    @Test
    fun `真机同形态的根 owner 不会从停止请求中丢失`() {
        val recipe = TestRecipes.serviceRecipe("root-owner-stop")
        val state = CardRunState(
            instanceId = "card-instance",
            recipeId = recipe.id,
            recipeName = recipe.name,
            status = CardRunStatus.Running,
            runtimeRootOwnerId = "card:card-instance@100",
            ownedRuntimeOwnerIds = emptyList(),
            runId = "terminal-session",
            terminalSessionId = "terminal-session",
            createdAt = 100L,
            updatedAt = 100L
        )

        val plan = StopCoordinator().plan(recipe, state)

        assertTrue(plan is StopPlan.Execute)
        assertEquals(
            listOf("card:card-instance@100"),
            (plan as StopPlan.Execute).request.runtimeOwnerIds
        )
    }
}
