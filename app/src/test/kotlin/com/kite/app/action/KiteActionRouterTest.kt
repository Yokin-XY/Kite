package com.kite.app.action

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteActionRouterTest {
    private val router = KiteActionRouter()

    @Test
    fun `没有显式停止动作时必须走标准停止入口`() {
        val recipe = recipeWithActions()

        val route = router.route(recipe, KiteRecipe.ACTION_STOP)

        assertTrue(route is KiteActionRoute.StopRecipe)
        route as KiteActionRoute.StopRecipe
        assertSame(recipe, route.recipe)
        assertEquals(KiteRecipe.ACTION_STOP, route.actionName)
    }

    @Test
    fun `显式停止动作必须作为工作流执行而不是标准停止兜底`() {
        val stopStep = shellStep("stop-step", "echo stop")
        val recipe = recipeWithActions(
            actions = mapOf(
                KiteRecipe.ACTION_STOP to KiteRecipeAction(
                    id = KiteRecipe.ACTION_STOP,
                    steps = listOf(stopStep)
                )
            )
        )

        val route = router.route(recipe, KiteRecipe.ACTION_STOP)

        assertTrue(route is KiteActionRoute.RunRecipe)
        route as KiteActionRoute.RunRecipe
        assertEquals(KiteRecipe.ACTION_STOP, route.actionName)
        assertEquals(listOf(stopStep), route.recipe.steps)
    }

    @Test
    fun `命名动作必须归一化成只含所选步骤的执行配方`() {
        val startStep = shellStep("start-step", "echo start")
        val restartStep = shellStep("restart-step", "echo restart")
        val restartAction = KiteRecipeAction(
            id = KiteRecipe.ACTION_RESTART,
            label = "重新启动",
            steps = listOf(restartStep)
        )
        val recipe = recipeWithActions(
            actions = linkedMapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = listOf(startStep)
                ),
                KiteRecipe.ACTION_RESTART to restartAction
            )
        )

        val route = router.route(recipe, KiteRecipe.ACTION_RESTART)

        assertTrue(route is KiteActionRoute.RunRecipe)
        route as KiteActionRoute.RunRecipe
        assertEquals(restartAction, route.action)
        assertEquals(listOf(restartStep), route.recipe.execution.steps)
        assertEquals(listOf(restartStep), route.recipe.steps)
        assertEquals(setOf(KiteRecipe.ACTION_START), route.recipe.actions.keys)
        assertEquals(KiteRecipe.ACTION_START, route.recipe.actions.getValue(KiteRecipe.ACTION_START).id)
    }

    @Test
    fun `网页和安卓动作都必须进入统一运行配方`() {
        val steps = listOf(
            KiteRecipeStep(id = "web", type = KiteRecipe.STEP_OPEN_WEB, url = "https://example.com"),
            KiteRecipeStep(
                id = "android",
                type = KiteRecipe.STEP_ANDROID_ACTION,
                action = KiteRecipe.ANDROID_ACTION_TOOLCHAIN_DOCTOR
            )
        )
        val recipe = recipeWithActions(
            actions = mapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = steps
                )
            )
        )

        val route = router.route(recipe, KiteRecipe.ACTION_START)

        assertTrue(route is KiteActionRoute.RunRecipe)
        route as KiteActionRoute.RunRecipe
        assertEquals(steps, route.recipe.steps)
    }

    @Test
    fun `不存在的动作必须返回可诊断的缺失结果`() {
        val recipe = recipeWithActions()

        val route = router.route(recipe, "missing")

        assertTrue(route is KiteActionRoute.Unsupported)
        route as KiteActionRoute.Unsupported
        assertSame(recipe, route.recipe)
        assertEquals("missing", route.actionName)
        assertEquals("missing_action", route.reason)
    }

    @Test
    fun `空动作必须返回可诊断的空动作结果`() {
        val recipe = recipeWithActions(
            actions = mapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = emptyList()
                )
            )
        )

        val route = router.route(recipe, KiteRecipe.ACTION_START)

        assertTrue(route is KiteActionRoute.Unsupported)
        route as KiteActionRoute.Unsupported
        assertEquals("empty_action", route.reason)
        assertEquals(KiteRecipe.ACTION_START, route.actionName)
    }

    private fun recipeWithActions(
        actions: Map<String, KiteRecipeAction> = mapOf(
            KiteRecipe.ACTION_START to KiteRecipeAction(
                id = KiteRecipe.ACTION_START,
                steps = listOf(shellStep("start-step", "echo start"))
            )
        )
    ): KiteRecipe = KiteRecipe(
        id = "test.action.router",
        name = "Action Router",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(emptyList()),
        actions = actions
    )

    private fun shellStep(id: String, command: String): KiteRecipeStep =
        KiteRecipeStep(
            id = id,
            type = KiteRecipe.STEP_SHELL,
            cmd = command
        )
}
