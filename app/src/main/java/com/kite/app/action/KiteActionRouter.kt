package com.kite.app.action

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction

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

        return KiteActionRoute.Unsupported(actionRecipe, actionName, "empty_action")
    }
}

sealed class KiteActionRoute {
    abstract val recipe: KiteRecipe
    abstract val actionName: String

    data class RunRecipe(
        override val recipe: KiteRecipe,
        override val actionName: String,
        val action: KiteRecipeAction
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
