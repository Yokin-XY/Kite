package com.kite.app.platform.recipes

import android.content.Context
import com.kite.app.application.recipes.RecipeFeatureChange
import com.kite.app.application.recipes.RecipeExternalRefreshResult
import com.kite.app.application.recipes.RecipeDeleteResult
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.dropzone.KiteDropZoneManager
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteCardGroupStore
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

internal class AndroidRecipeFeatureGateway(
    context: Context,
    private val recipeLoader: KiteRecipeLoader,
    private val groupStore: KiteCardGroupStore,
    private val dropZoneManager: KiteDropZoneManager,
    private val environmentIdProvider: () -> String = { CardRunState.DEFAULT_ENVIRONMENT_ID }
) : RecipeFeatureGateway {
    private val appContext = context.applicationContext
    private val settings by lazy {
        appContext.getSharedPreferences(APP_SETTINGS, Context.MODE_PRIVATE)
    }
    private val mutationChanges = MutableSharedFlow<RecipeFeatureChange>(
        replay = 1,
        extraBufferCapacity = 8
    )
    @Volatile
    private var cachedRecipes: List<KiteRecipe>? = null

    override val changes: Flow<RecipeFeatureChange> = merge(
        CardRunStore.runs.asRecipeFeatureChanges(),
        mutationChanges.asSharedFlow()
    )

    override suspend fun loadRecipes(forceRefresh: Boolean): List<KiteRecipe> {
        if (!forceRefresh) cachedRecipes?.let { return it }
        return withContext(Dispatchers.IO) { recipeLoader.loadAllRecipes() }
            .also { cachedRecipes = it }
    }

    override fun groups(): List<KiteCardGroup> = groupStore.groups()

    override fun runSnapshot(recipeId: String): CardRunState? =
        CardRunStore.currentForRecipe(recipeId, environmentIdProvider())

    override suspend fun saveRecipe(input: NewRecipeInput): KiteRecipe {
        val (recipe, catalog) = withContext(Dispatchers.IO) {
            val saved = recipeLoader.saveUserRecipe(input)
            saved to recipeLoader.loadAllRecipes()
        }
        cachedRecipes = catalog
        mutationChanges.tryEmit(
            RecipeFeatureChange(
                reason = "recipe_saved",
                affectedRecipeIds = setOf(recipe.id),
                catalogInvalidated = true
            )
        )
        return recipe
    }

    override suspend fun deleteRecipe(recipeId: String): RecipeDeleteResult {
        CardRunStore.currentForRecipe(recipeId, environmentIdProvider())
            ?.takeIf(::mustStopBeforeDelete)
            ?.let { return RecipeDeleteResult.RequiresStop(it) }
        val deleted = withContext(Dispatchers.IO) {
            recipeLoader.loadAllRecipes()
                .firstOrNull { it.id == recipeId }
                ?.let(recipeLoader::deleteRecipe)
                ?: false
        }
        if (!deleted) return RecipeDeleteResult.Missing
        val removedCardInstanceIds = CardRunStore.removeClosedRunStatesForRecipes(listOf(recipeId))
        cachedRecipes = withContext(Dispatchers.IO) { recipeLoader.loadAllRecipes() }
        mutationChanges.tryEmit(
            RecipeFeatureChange(
                reason = "recipe_deleted",
                affectedRecipeIds = setOf(recipeId),
                catalogInvalidated = true
            )
        )
        return RecipeDeleteResult.Deleted(removedCardInstanceIds)
    }

    private fun mustStopBeforeDelete(run: CardRunState): Boolean =
        run.status == CardRunStatus.Opened ||
            run.isBusy() ||
            run.isActive() ||
            (
                run.hasRunBinding() &&
                    run.status != CardRunStatus.Stopping &&
                    run.status != CardRunStatus.Stopped &&
                    run.status != CardRunStatus.Completed &&
                    run.status != CardRunStatus.Failed &&
                    run.status != CardRunStatus.BridgeUnavailable
                )

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
                invalidateCatalog("external_recipes_refreshed")
            }

    override fun invalidateCatalog(reason: String, affectedRecipeIds: Set<String>) {
        cachedRecipes = null
        mutationChanges.tryEmit(
            RecipeFeatureChange(
                reason = reason,
                affectedRecipeIds = affectedRecipeIds,
                catalogInvalidated = true
            )
        )
    }

    override fun restoredEditorDraft(maxAgeMs: Long): String? {
        val raw = settings.getString(KEY_EDITOR_DRAFT, null)?.takeIf { it.isNotBlank() } ?: return null
        val savedAt = settings.getLong(KEY_EDITOR_DRAFT_SAVED_AT, 0L)
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > maxAgeMs.coerceAtLeast(0L)) {
            saveEditorDraft(null)
            return null
        }
        return raw
    }

    override fun saveEditorDraft(rawJson: String?) {
        settings.edit().apply {
            if (rawJson.isNullOrBlank()) {
                remove(KEY_EDITOR_DRAFT)
                remove(KEY_EDITOR_DRAFT_SAVED_AT)
            } else {
                putString(KEY_EDITOR_DRAFT, rawJson)
                putLong(KEY_EDITOR_DRAFT_SAVED_AT, System.currentTimeMillis())
            }
        }.apply()
    }

    override fun customEditorIconSources(): List<String> {
        val raw = settings.getString(KEY_EDITOR_ICON_COLLECTION, "[]").orEmpty()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val source = json.optString(index).takeIf(String::isNotBlank) ?: continue
                    if (editorIconExists(source)) add(source)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    override fun readEditorIcon(source: String): ByteArray? {
        val value = source.trim().takeIf { it.isNotBlank() && !it.contains("..") } ?: return null
        val file = if (value.startsWith("/")) File(value) else File(appContext.filesDir, value)
        if (file.isFile) return runCatching { file.readBytes() }.getOrNull()
        val asset = value.trimStart('/')
        return runCatching { appContext.assets.open(asset).use { it.readBytes() } }.getOrNull()
    }

    private fun editorIconExists(source: String): Boolean {
        val value = source.trim().takeIf { it.isNotBlank() && !it.contains("..") } ?: return false
        val file = if (value.startsWith("/")) File(value) else File(appContext.filesDir, value)
        if (file.isFile) return true
        return runCatching { appContext.assets.open(value.trimStart('/')).use { true } }.getOrDefault(false)
    }

    override suspend fun saveEditorIcon(pngBytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(pngBytes.isNotEmpty()) { "empty_icon" }
        require(pngBytes.size <= MAX_EDITOR_ICON_BYTES) { "icon_too_large" }
        File(appContext.filesDir, "recipe-icons").apply {
            if (!exists() && !mkdirs()) error("icon_directory_failed")
        }
        val relative = "recipe-icons/${UUID.randomUUID()}.png"
        File(appContext.filesDir, relative).writeBytes(pngBytes)
        val merged = (listOf(relative) + customEditorIconSources()).distinct().take(48)
        settings.edit()
            .putString(
                KEY_EDITOR_ICON_COLLECTION,
                JSONArray().apply { merged.forEach(::put) }.toString()
            )
            .apply()
        relative
    }

    companion object {
        fun create(
            context: Context,
            recipeLoader: KiteRecipeLoader,
            groupStore: KiteCardGroupStore,
            dropZoneManager: KiteDropZoneManager,
            environmentIdProvider: () -> String = { CardRunState.DEFAULT_ENVIRONMENT_ID }
        ): AndroidRecipeFeatureGateway = AndroidRecipeFeatureGateway(
            context,
            recipeLoader,
            groupStore,
            dropZoneManager,
            environmentIdProvider
        )

        private const val APP_SETTINGS = "kite_app_settings"
        private const val KEY_EDITOR_DRAFT = "recipe_draft"
        private const val KEY_EDITOR_DRAFT_SAVED_AT = "recipe_draft_saved_at"
        private const val KEY_EDITOR_ICON_COLLECTION = "recipe_icon_collection"
        private const val MAX_EDITOR_ICON_BYTES = 5 * 1024 * 1024
    }
}

/** StateFlow 的首个值就是当前事实；返回首页时必须消费，不能把它当成旧事件丢弃。 */
internal fun Flow<List<CardRunState>>.asRecipeFeatureChanges(): Flow<RecipeFeatureChange> = map { runs ->
    RecipeFeatureChange(
        reason = "card_run_state",
        affectedRecipeIds = runs.map(CardRunState::recipeId).toSet()
    )
}
