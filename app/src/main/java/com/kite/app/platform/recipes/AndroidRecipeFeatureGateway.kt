package com.kite.app.platform.recipes

import com.kite.app.application.recipes.RecipeFeatureChange
import com.kite.app.application.recipes.RecipeExternalRefreshResult
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.dropzone.KiteDropZoneManager
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteCardGroupStore
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

internal class AndroidRecipeFeatureGateway(
    private val recipeLoader: KiteRecipeLoader,
    private val groupStore: KiteCardGroupStore,
    private val dropZoneManager: KiteDropZoneManager
) : RecipeFeatureGateway {
    private val mutationChanges = MutableSharedFlow<RecipeFeatureChange>(extraBufferCapacity = 8)

    override val changes: Flow<RecipeFeatureChange> = merge(
        CardRunStore.runs.drop(1).map { runs ->
            RecipeFeatureChange(
                reason = "card_run_state",
                affectedRecipeIds = runs.map(CardRunState::recipeId).toSet()
            )
        },
        mutationChanges.asSharedFlow()
    )

    override suspend fun loadRecipes(forceRefresh: Boolean): List<KiteRecipe> =
        withContext(Dispatchers.IO) { recipeLoader.loadAllRecipes() }

    override fun groups(): List<KiteCardGroup> = groupStore.groups()

    override fun runSnapshot(recipeId: String): CardRunState? =
        CardRunStore.currentForRecipe(recipeId)

    override suspend fun saveRecipe(input: NewRecipeInput): KiteRecipe =
        withContext(Dispatchers.IO) { recipeLoader.saveUserRecipe(input) }.also { recipe ->
            mutationChanges.tryEmit(
                RecipeFeatureChange(
                    reason = "recipe_saved",
                    affectedRecipeIds = setOf(recipe.id),
                    catalogInvalidated = true
                )
            )
        }

    override suspend fun deleteRecipe(recipeId: String): Boolean =
        withContext(Dispatchers.IO) {
            recipeLoader.loadAllRecipes()
                .firstOrNull { it.id == recipeId }
                ?.let(recipeLoader::deleteRecipe)
                ?: false
        }.also { deleted ->
            if (deleted) {
                mutationChanges.tryEmit(
                    RecipeFeatureChange(
                        reason = "recipe_deleted",
                        affectedRecipeIds = setOf(recipeId),
                        catalogInvalidated = true
                    )
                )
            }
        }

    override suspend fun createGroup(name: String): KiteCardGroup =
        withContext(Dispatchers.IO) { groupStore.create(name) }.also {
            mutationChanges.tryEmit(
                RecipeFeatureChange(reason = "group_created", catalogInvalidated = true)
            )
        }

    override suspend fun refreshExternalRecipes(): RecipeExternalRefreshResult =
        withContext(Dispatchers.IO) { dropZoneManager.scanAndImport() }
            .let { result ->
                RecipeExternalRefreshResult(
                    message = result.message,
                    imported = result.imported,
                    skipped = result.skipped,
                    invalid = result.invalid
                )
            }
            .also {
                mutationChanges.tryEmit(
                    RecipeFeatureChange(reason = "external_recipes_refreshed", catalogInvalidated = true)
                )
            }

    companion object {
        fun create(
            recipeLoader: KiteRecipeLoader,
            groupStore: KiteCardGroupStore,
            dropZoneManager: KiteDropZoneManager
        ): AndroidRecipeFeatureGateway = AndroidRecipeFeatureGateway(
            recipeLoader,
            groupStore,
            dropZoneManager
        )
    }
}
