package com.kite.app.feature.home

import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.KiteCardRunUiProjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 首页卡片的纯状态控制器，不持有 View、Context、导航或执行器。 */
internal class HomeFeatureController(
    private val gateway: RecipeFeatureGateway,
    initiallyBlocksUbuntuActions: Boolean = true
) {
    private val mutableState = MutableStateFlow(
        HomeFeatureUiState(blocksUbuntuActions = initiallyBlocksUbuntuActions)
    )
    private val dispatchMutex = Mutex()
    val state: StateFlow<HomeFeatureUiState> = mutableState.asStateFlow()

    suspend fun dispatch(action: HomeFeatureAction): HomeFeatureEffect? =
        dispatchMutex.withLock {
            when (action) {
                is HomeFeatureAction.Refresh -> {
                    refresh(action.forceCatalogRefresh)
                    null
                }
                HomeFeatureAction.ReconcileRuns -> {
                    publish(
                        currentRecipes(),
                        mutableState.value.phase,
                        mutableState.value.errorMessage
                    )
                    null
                }
                is HomeFeatureAction.SetRuntimeBlocked -> {
                    if (mutableState.value.blocksUbuntuActions != action.blocked) {
                        mutableState.value = mutableState.value.copy(blocksUbuntuActions = action.blocked)
                        publish(currentRecipes(), mutableState.value.phase, mutableState.value.errorMessage)
                    }
                    null
                }
                is HomeFeatureAction.Primary -> requestPrimary(action.recipeId)
                is HomeFeatureAction.CreateGroup -> createGroup(action.name)
                HomeFeatureAction.RefreshExternalRecipes -> refreshExternalRecipes()
            }
        }

    private suspend fun refresh(forceCatalogRefresh: Boolean) {
        mutableState.value = mutableState.value.copy(
            phase = HomeCatalogPhase.Loading,
            errorMessage = null
        )
        runCatching { gateway.loadRecipes(forceCatalogRefresh) }
            .onSuccess { recipes -> publish(recipes, HomeCatalogPhase.Ready, null) }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    phase = HomeCatalogPhase.Failed,
                    revision = mutableState.value.revision + 1L,
                    errorMessage = error.message ?: error.javaClass.simpleName
                )
            }
    }

    private fun publish(
        recipes: List<KiteRecipe>,
        phase: HomeCatalogPhase,
        errorMessage: String?
    ) {
        val blocked = mutableState.value.blocksUbuntuActions
        val items = recipes
            .filter { it.id.isNotBlank() }
            .distinctBy(KiteRecipe::id)
            .map { recipe ->
                val run = gateway.runSnapshot(recipe.id)
                    ?: CardRunState.fromRecipeStatus(recipe.id, "unknown")
                val runtimeBlocked = blocked && recipe.hasUbuntuStep()
                HomeRecipeItemUiState(
                    recipe = recipe,
                    run = run,
                    projection = KiteCardRunUiProjector.project(run.status, runtimeBlocked),
                    runtimeBlocked = runtimeBlocked
                )
            }
        mutableState.value = HomeFeatureUiState(
            phase = phase,
            items = items,
            groups = gateway.groups(),
            blocksUbuntuActions = blocked,
            revision = mutableState.value.revision + 1L,
            errorMessage = errorMessage
        )
    }

    private fun requestPrimary(recipeId: String): HomeFeatureEffect {
        val item = mutableState.value.item(recipeId)
            ?: return HomeFeatureEffect.ActionUnavailable(recipeId, "recipe_not_in_catalog")
        if (!item.projection.primaryActionEnabled) {
            return HomeFeatureEffect.ActionUnavailable(recipeId, "action_busy")
        }
        return HomeFeatureEffect.ActionRequested(
            KiteRecipeActionRequest(
                recipe = item.recipe,
                intent = KiteRecipeActionIntent.Primary,
                source = KiteRecipeActionSource.ConsoleCard,
                openTaskOnStart = item.recipe.launch.openInstance
            )
        )
    }

    private suspend fun createGroup(rawName: String): HomeFeatureEffect {
        val name = rawName.trim()
        if (name.isBlank()) return HomeFeatureEffect.ActionUnavailable("group", "group_name_blank")
        return runCatching { gateway.createGroup(name) }
            .fold(
                onSuccess = { group ->
                    publish(currentRecipes(), mutableState.value.phase, null)
                    HomeFeatureEffect.GroupCreated(group)
                },
                onFailure = { error ->
                    HomeFeatureEffect.ActionUnavailable(
                        "group",
                        error.message ?: error.javaClass.simpleName
                    )
                }
            )
    }

    private suspend fun refreshExternalRecipes(): HomeFeatureEffect {
        mutableState.value = mutableState.value.copy(phase = HomeCatalogPhase.Loading, errorMessage = null)
        return runCatching {
            val result = gateway.refreshExternalRecipes()
            val recipes = gateway.loadRecipes(forceRefresh = true)
            result to recipes
        }.fold(
            onSuccess = { (result, recipes) ->
                publish(recipes, HomeCatalogPhase.Ready, null)
                HomeFeatureEffect.ExternalRefreshCompleted(result.message)
            },
            onFailure = { error ->
                mutableState.value = mutableState.value.copy(
                    phase = HomeCatalogPhase.Failed,
                    revision = mutableState.value.revision + 1L,
                    errorMessage = error.message ?: error.javaClass.simpleName
                )
                HomeFeatureEffect.ActionUnavailable(
                    "external_recipes",
                    error.message ?: error.javaClass.simpleName
                )
            }
        )
    }

    private fun currentRecipes(): List<KiteRecipe> =
        mutableState.value.items.map(HomeRecipeItemUiState::recipe)
}
