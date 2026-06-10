package com.kite.app.action

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeStep

class KiteActionRouter {
    fun route(recipe: KiteRecipe, actionName: String): KiteActionRoute {
        if (actionName == KiteRecipe.ACTION_STOP && recipe.action(actionName) == null) {
            return KiteActionRoute.StopRecipe(recipe)
        }

        val action = recipe.action(actionName)
            ?: return KiteActionRoute.Unsupported(recipe, actionName, "missing_action")
        val actionRecipe = recipe.asExecutionRecipe(actionName)

        if (action.steps.isNotEmpty()) {
            return KiteActionRoute.RunRecipe(actionRecipe, actionName, action)
        }

        val openUrl = firstOpenWebUrl(action.steps)
        if (!openUrl.isNullOrBlank()) {
            return KiteActionRoute.OpenWeb(actionRecipe, actionName, openUrl)
        }

        return KiteActionRoute.Unsupported(actionRecipe, actionName, "empty_action")
    }

    private fun firstOpenWebUrl(steps: List<KiteRecipeStep>): String? =
        steps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url
}

sealed class KiteActionRoute {
    abstract val recipe: KiteRecipe
    abstract val actionName: String

    data class RunRecipe(
        override val recipe: KiteRecipe,
        override val actionName: String,
        val action: KiteRecipeAction
    ) : KiteActionRoute()

    data class OpenWeb(
        override val recipe: KiteRecipe,
        override val actionName: String,
        val url: String
    ) : KiteActionRoute()

    data class NativeAction(
        override val recipe: KiteRecipe,
        override val actionName: String,
        val step: KiteRecipeStep,
        val nextUrl: String?
    ) : KiteActionRoute()

    data class StopRecipe(
        override val recipe: KiteRecipe,
        override val actionName: String = KiteRecipe.ACTION_STOP
    ) : KiteActionRoute()

    data class Unsupported(
        override val recipe: KiteRecipe,
        override val actionName: String,
        val reason: String
    ) : KiteActionRoute()
}
