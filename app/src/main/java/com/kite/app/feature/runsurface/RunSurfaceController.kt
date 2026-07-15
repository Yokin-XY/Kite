package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState

/** 只拥有当前显示绑定；attach/detach 不拥有底层运行生命周期。 */
internal class RunSurfaceController(
    private val actions: RunSurfaceActionGateway
) {
    private var target: RunSurfaceTarget? = null

    fun attach(
        recipe: KiteRecipe,
        state: CardRunState,
        children: List<CardRunState> = emptyList()
    ): RunSurfaceUiState {
        val next = RunSurfaceProjector.project(recipe, state, children)
        target = next.target
        return next
    }

    fun update(
        recipe: KiteRecipe,
        state: CardRunState,
        children: List<CardRunState> = emptyList()
    ): RunSurfaceUiState? {
        val current = target ?: return null
        if (recipe.id != current.recipeId || state.instanceId != current.instanceId) return null
        return RunSurfaceProjector.project(recipe, state, children)
    }

    fun detach() {
        target = null
    }

    fun stop(): Boolean {
        val current = target ?: return false
        actions.stop(current.instanceId)
        return true
    }
}
