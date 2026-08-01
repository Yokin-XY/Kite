package com.kite.app.application.runs

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.TestRecipes
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
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

    @Test
    fun `无进程绑定的原生能力仍由执行端确认停止`() {
        val recipe = KiteRecipe(
            id = "native-stop",
            name = "native-stop",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(
                    KiteRecipeStep(
                        id = "download",
                        type = KiteRecipe.STEP_NATIVE_CAPABILITY,
                        action = "network.download_sha256"
                    )
                )
            )
        )
        val state = CardRunState(
            instanceId = "native-instance",
            recipeId = recipe.id,
            recipeName = recipe.name,
            status = CardRunStatus.Running,
            currentStepIndex = 0,
            createdAt = 100L,
            updatedAt = 100L
        )

        val plan = StopCoordinator().plan(recipe, state)

        assertTrue(plan is StopPlan.Execute)
        assertEquals(false, (plan as StopPlan.Execute).request.hasBridgeProcessBinding())
        assertEquals(false, plan.request.interruptTerminal)
    }
}
