package com.kite.app.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteRecipeX11Test {
    @Test
    fun x11StepIsAnUbuntuRecipeStep() {
        val recipe = KiteRecipe(
            id = "kite.x11.test",
            name = "X11 Test",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(
                    KiteRecipeStep(
                        id = "x11",
                        type = KiteRecipe.STEP_X11,
                        cmd = "xterm"
                    )
                )
            )
        )

        assertTrue(recipe.hasUbuntuStep())
        assertEquals(KiteRecipe.STEP_X11, recipe.steps.single().type)
    }

    @Test
    fun shellRunModeIsPartOfRecipeModel() {
        val recipe = KiteRecipe(
            id = "detached.test",
            name = "Detached Test",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(
                    KiteRecipeStep(
                        id = "serve",
                        type = KiteRecipe.STEP_SHELL,
                        cmd = "serve",
                        runMode = KiteRecipe.RUN_MODE_DETACHED
                    )
                )
            )
        )

        assertEquals(KiteRecipe.RUN_MODE_DETACHED, recipe.steps.single().runMode)
    }
}
