package com.kite.app.feature.home

import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.KiteCardRunUiProjection
import com.kite.app.run.KiteRunPrimaryAction

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

    fun primaryTarget(): HomePrimaryActionTarget = HomePrimaryActionTarget(
        recipeId = recipeId,
        action = projection.primaryAction,
        instanceId = run.instanceId,
        generation = run.createdAt
    )
}

/** 固定用户实际看到并点击的主动作，避免下游按更晚状态把“启动”重新解释成“停止”。 */
internal data class HomePrimaryActionTarget(
    val recipeId: String,
    val action: KiteRunPrimaryAction,
    val instanceId: String,
    val generation: Long
)

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
    data class Primary(val target: HomePrimaryActionTarget) : HomeFeatureAction
    data class CreateGroup(val name: String) : HomeFeatureAction
    data object RefreshExternalRecipes : HomeFeatureAction
}

internal sealed interface HomeFeatureEffect {
    data class ActionRequested(val request: KiteRecipeActionRequest) : HomeFeatureEffect
    data class ActionUnavailable(val targetId: String, val reason: String) : HomeFeatureEffect
    data class GroupCreated(val group: KiteCardGroup) : HomeFeatureEffect
    data class ExternalRefreshCompleted(val message: String) : HomeFeatureEffect
}
