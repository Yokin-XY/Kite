package com.kite.app.feature.home

import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.KiteCardRunUiProjection

internal enum class HomeCatalogPhase {
    Idle,
    Loading,
    Ready,
    Failed
}

internal data class HomeRecipeItemUiState(
    val recipe: KiteRecipe,
    val run: CardRunState,
    val projection: KiteCardRunUiProjection,
    val runtimeBlocked: Boolean
) {
    val recipeId: String get() = recipe.id
}

internal data class HomeFeatureUiState(
    val phase: HomeCatalogPhase = HomeCatalogPhase.Idle,
    val items: List<HomeRecipeItemUiState> = emptyList(),
    val groups: List<KiteCardGroup> = emptyList(),
    val blocksUbuntuActions: Boolean = true,
    val revision: Long = 0L,
    val errorMessage: String? = null
) {
    fun item(recipeId: String): HomeRecipeItemUiState? =
        items.firstOrNull { it.recipeId == recipeId }
}

internal sealed interface HomeFeatureAction {
    data class Refresh(val forceCatalogRefresh: Boolean = false) : HomeFeatureAction
    data object ReconcileRuns : HomeFeatureAction
    data class SetRuntimeBlocked(val blocked: Boolean) : HomeFeatureAction
    data class Primary(val recipeId: String) : HomeFeatureAction
}

internal sealed interface HomeFeatureEffect {
    data class ActionRequested(val request: KiteRecipeActionRequest) : HomeFeatureEffect
    data class ActionUnavailable(val recipeId: String, val reason: String) : HomeFeatureEffect
}
