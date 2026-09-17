package com.kite.app.feature.recipeeditor

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource

internal sealed interface RecipeEditorRequest {
    data object Close : RecipeEditorRequest
    data object CloseRawJson : RecipeEditorRequest
    data class OpenRawJson(val recipeId: String) : RecipeEditorRequest
    data class OpenRunHistory(val recipeId: String) : RecipeEditorRequest
    data class RequestShortcut(val recipeId: String) : RecipeEditorRequest
    data class Deleted(
        val recipeId: String,
        val removedCardInstanceIds: Set<String>
    ) : RecipeEditorRequest
    data class SubmitAction(
        val recipeId: String,
        val intent: KiteRecipeActionIntent,
        val source: KiteRecipeActionSource,
        val openTaskOnStart: Boolean,
        val instanceId: String?
    ) : RecipeEditorRequest
}

internal object RecipeEditorResultContract {
    const val REQUEST_KEY = "kite.recipe.editor.request"

    private const val KEY_KIND = "kind"
    private const val KEY_RECIPE_ID = "recipe_id"
    private const val KEY_INTENT = "intent"
    private const val KEY_SOURCE = "source"
    private const val KEY_OPEN_TASK = "open_task"
    private const val KEY_INSTANCE_ID = "instance_id"
    private const val KEY_REMOVED_INSTANCE_IDS = "removed_instance_ids"
    private const val KIND_CLOSE = "close"
    private const val KIND_CLOSE_RAW_JSON = "close_raw_json"
    private const val KIND_RAW_JSON = "raw_json"
    private const val KIND_HISTORY = "history"
    private const val KIND_SHORTCUT = "shortcut"
    private const val KIND_DELETED = "deleted"
    private const val KIND_ACTION = "action"

    fun send(fragment: Fragment, request: RecipeEditorRequest) {
        fragment.parentFragmentManager.setFragmentResult(REQUEST_KEY, encode(request))
    }

    fun actionRequest(request: KiteRecipeActionRequest): RecipeEditorRequest.SubmitAction =
        RecipeEditorRequest.SubmitAction(
            recipeId = request.recipe.id,
            intent = request.intent,
            source = request.source,
            openTaskOnStart = request.openTaskOnStart,
            instanceId = request.instanceId
        )

    fun parse(bundle: Bundle): RecipeEditorRequest? = when (bundle.getString(KEY_KIND)) {
        KIND_CLOSE -> RecipeEditorRequest.Close
        KIND_CLOSE_RAW_JSON -> RecipeEditorRequest.CloseRawJson
        KIND_RAW_JSON -> bundle.recipeId()?.let(RecipeEditorRequest::OpenRawJson)
        KIND_HISTORY -> bundle.recipeId()?.let(RecipeEditorRequest::OpenRunHistory)
        KIND_SHORTCUT -> bundle.recipeId()?.let(RecipeEditorRequest::RequestShortcut)
        KIND_DELETED -> bundle.recipeId()?.let { recipeId ->
            RecipeEditorRequest.Deleted(
                recipeId = recipeId,
                removedCardInstanceIds = bundle.getStringArrayList(KEY_REMOVED_INSTANCE_IDS)
                    .orEmpty()
                    .filter(String::isNotBlank)
                    .toSet()
            )
        }
        KIND_ACTION -> parseAction(bundle)
        else -> null
    }

    private fun encode(request: RecipeEditorRequest): Bundle = Bundle().apply {
        when (request) {
            RecipeEditorRequest.Close -> putString(KEY_KIND, KIND_CLOSE)
            RecipeEditorRequest.CloseRawJson -> putString(KEY_KIND, KIND_CLOSE_RAW_JSON)
            is RecipeEditorRequest.OpenRawJson -> putRecipe(KIND_RAW_JSON, request.recipeId)
            is RecipeEditorRequest.OpenRunHistory -> putRecipe(KIND_HISTORY, request.recipeId)
            is RecipeEditorRequest.RequestShortcut -> putRecipe(KIND_SHORTCUT, request.recipeId)
            is RecipeEditorRequest.Deleted -> {
                putRecipe(KIND_DELETED, request.recipeId)
                putStringArrayList(
                    KEY_REMOVED_INSTANCE_IDS,
                    ArrayList(request.removedCardInstanceIds)
                )
            }
            is RecipeEditorRequest.SubmitAction -> {
                putRecipe(KIND_ACTION, request.recipeId)
                putString(KEY_INTENT, request.intent.name)
                putString(KEY_SOURCE, request.source.name)
                putBoolean(KEY_OPEN_TASK, request.openTaskOnStart)
                request.instanceId?.let { putString(KEY_INSTANCE_ID, it) }
            }
        }
    }

    private fun Bundle.putRecipe(kind: String, recipeId: String) {
        putString(KEY_KIND, kind)
        putString(KEY_RECIPE_ID, recipeId)
    }

    private fun Bundle.recipeId(): String? =
        getString(KEY_RECIPE_ID)?.trim()?.takeIf(String::isNotBlank)

    private fun parseAction(bundle: Bundle): RecipeEditorRequest? {
        val recipeId = bundle.recipeId() ?: return null
        val intent = bundle.getString(KEY_INTENT)
            ?.let { runCatching { KiteRecipeActionIntent.valueOf(it) }.getOrNull() }
            ?: return null
        val source = bundle.getString(KEY_SOURCE)
            ?.let { runCatching { KiteRecipeActionSource.valueOf(it) }.getOrNull() }
            ?: return null
        return RecipeEditorRequest.SubmitAction(
            recipeId = recipeId,
            intent = intent,
            source = source,
            openTaskOnStart = bundle.getBoolean(KEY_OPEN_TASK),
            instanceId = bundle.getString(KEY_INSTANCE_ID)?.takeIf(String::isNotBlank)
        )
    }
}
