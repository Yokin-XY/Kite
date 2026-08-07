package com.kite.app.feature.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource

internal sealed interface HomeFeatureRequest {
    data class OpenEditor(val recipeId: String) : HomeFeatureRequest
    data class SubmitAction(
        val recipeId: String,
        val intent: KiteRecipeActionIntent,
        val source: KiteRecipeActionSource,
        val openTaskOnStart: Boolean,
        val instanceId: String?,
        val expectedGeneration: Long?
    ) : HomeFeatureRequest
}

internal object HomeFeatureResultContract {
    const val REQUEST_KEY = "kite.home.feature.request"

    private const val KEY_KIND = "kind"
    private const val KEY_RECIPE_ID = "recipe_id"
    private const val KEY_INTENT = "intent"
    private const val KEY_SOURCE = "source"
    private const val KEY_OPEN_TASK = "open_task"
    private const val KEY_INSTANCE_ID = "instance_id"
    private const val KEY_EXPECTED_GENERATION = "expected_generation"
    private const val KIND_EDITOR = "editor"
    private const val KIND_ACTION = "action"

    fun send(fragment: Fragment, request: HomeFeatureRequest) {
        fragment.parentFragmentManager.setFragmentResult(REQUEST_KEY, encode(request))
    }

    fun actionRequest(request: KiteRecipeActionRequest): HomeFeatureRequest.SubmitAction =
        HomeFeatureRequest.SubmitAction(
            recipeId = request.recipe.id,
            intent = request.intent,
            source = request.source,
            openTaskOnStart = request.openTaskOnStart,
            instanceId = request.instanceId,
            expectedGeneration = request.expectedGeneration
        )

    fun parse(bundle: Bundle): HomeFeatureRequest? {
        val recipeId = bundle.getString(KEY_RECIPE_ID)?.trim()?.takeIf(String::isNotBlank) ?: return null
        return when (bundle.getString(KEY_KIND)) {
            KIND_EDITOR -> HomeFeatureRequest.OpenEditor(recipeId)
            KIND_ACTION -> {
                val intent = bundle.getString(KEY_INTENT)
                    ?.let { runCatching { KiteRecipeActionIntent.valueOf(it) }.getOrNull() }
                    ?: return null
                val source = bundle.getString(KEY_SOURCE)
                    ?.let { runCatching { KiteRecipeActionSource.valueOf(it) }.getOrNull() }
                    ?: return null
                HomeFeatureRequest.SubmitAction(
                    recipeId = recipeId,
                    intent = intent,
                    source = source,
                    openTaskOnStart = bundle.getBoolean(KEY_OPEN_TASK),
                    instanceId = bundle.getString(KEY_INSTANCE_ID)?.takeIf(String::isNotBlank),
                    expectedGeneration = bundle.getLong(KEY_EXPECTED_GENERATION)
                        .takeIf { bundle.containsKey(KEY_EXPECTED_GENERATION) && it > 0L }
                )
            }
            else -> null
        }
    }

    private fun encode(request: HomeFeatureRequest): Bundle = Bundle().apply {
        when (request) {
            is HomeFeatureRequest.OpenEditor -> {
                putString(KEY_KIND, KIND_EDITOR)
                putString(KEY_RECIPE_ID, request.recipeId)
            }
            is HomeFeatureRequest.SubmitAction -> {
                putString(KEY_KIND, KIND_ACTION)
                putString(KEY_RECIPE_ID, request.recipeId)
                putString(KEY_INTENT, request.intent.name)
                putString(KEY_SOURCE, request.source.name)
                putBoolean(KEY_OPEN_TASK, request.openTaskOnStart)
                request.instanceId?.let { putString(KEY_INSTANCE_ID, it) }
                request.expectedGeneration?.takeIf { it > 0L }?.let {
                    putLong(KEY_EXPECTED_GENERATION, it)
                }
            }
        }
    }
}
