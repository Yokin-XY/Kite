package com.kite.app.application.recipes

import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.run.CardRunState
import kotlinx.coroutines.flow.Flow

data class RecipeFeatureChange(
    val reason: String,
    val affectedRecipeIds: Set<String> = emptySet(),
    val catalogInvalidated: Boolean = false
)

data class RecipeExternalRefreshResult(
    val message: String,
    val imported: Int,
    val skipped: Int,
    val invalid: Int
)

sealed interface RecipeDeleteResult {
    data class RequiresStop(val run: CardRunState) : RecipeDeleteResult
    data class Deleted(val removedCardInstanceIds: Set<String>) : RecipeDeleteResult
    data object Missing : RecipeDeleteResult
}

interface RecipeFeatureGateway {
    val changes: Flow<RecipeFeatureChange>

    suspend fun loadRecipes(forceRefresh: Boolean = false): List<KiteRecipe>

    fun groups(): List<KiteCardGroup>

    fun runSnapshot(recipeId: String): CardRunState?

    suspend fun saveRecipe(input: NewRecipeInput): KiteRecipe

    suspend fun deleteRecipe(recipeId: String): RecipeDeleteResult

    suspend fun createGroup(name: String): KiteCardGroup

    suspend fun refreshExternalRecipes(): RecipeExternalRefreshResult

    fun invalidateCatalog(reason: String, affectedRecipeIds: Set<String> = emptySet())

    fun restoredEditorDraft(maxAgeMs: Long): String?

    fun saveEditorDraft(rawJson: String?)

    fun customEditorIconSources(): List<String>

    fun readEditorIcon(source: String): ByteArray?

    suspend fun saveEditorIcon(pngBytes: ByteArray): String
}

interface RecipeFeatureDependenciesOwner {
    val recipeFeatureGateway: RecipeFeatureGateway
}
