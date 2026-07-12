package com.kite.app.feature.runtimemanagement

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.kite.app.run.CardRunSurface

internal sealed interface RuntimeManagementRequest {
    data object Back : RuntimeManagementRequest
    data class OpenSurface(
        val recipeId: String,
        val instanceId: String,
        val surface: CardRunSurface
    ) : RuntimeManagementRequest
}

internal object RuntimeManagementResultContract {
    const val REQUEST_KEY = "kite.runtime.management.request"

    private const val KEY_KIND = "kind"
    private const val KEY_RECIPE_ID = "recipe_id"
    private const val KEY_INSTANCE_ID = "instance_id"
    private const val KEY_SURFACE = "surface"
    private const val KIND_BACK = "back"
    private const val KIND_OPEN_SURFACE = "open_surface"

    fun send(fragment: Fragment, request: RuntimeManagementRequest) {
        fragment.parentFragmentManager.setFragmentResult(REQUEST_KEY, encode(request))
    }

    fun parse(bundle: Bundle): RuntimeManagementRequest? {
        return when (bundle.getString(KEY_KIND)) {
            KIND_BACK -> RuntimeManagementRequest.Back
            KIND_OPEN_SURFACE -> {
                val recipeId = bundle.getString(KEY_RECIPE_ID)?.takeIf(String::isNotBlank) ?: return null
                val instanceId = bundle.getString(KEY_INSTANCE_ID)?.takeIf(String::isNotBlank) ?: return null
                val surface = bundle.getString(KEY_SURFACE)
                    ?.let { runCatching { CardRunSurface.valueOf(it) }.getOrNull() }
                    ?: return null
                RuntimeManagementRequest.OpenSurface(recipeId, instanceId, surface)
            }
            else -> null
        }
    }

    private fun encode(request: RuntimeManagementRequest): Bundle = Bundle().apply {
        when (request) {
            RuntimeManagementRequest.Back -> putString(KEY_KIND, KIND_BACK)
            is RuntimeManagementRequest.OpenSurface -> {
                putString(KEY_KIND, KIND_OPEN_SURFACE)
                putString(KEY_RECIPE_ID, request.recipeId)
                putString(KEY_INSTANCE_ID, request.instanceId)
                putString(KEY_SURFACE, request.surface.name)
            }
        }
    }
}
