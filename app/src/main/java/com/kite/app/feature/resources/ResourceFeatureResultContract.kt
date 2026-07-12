package com.kite.app.feature.resources

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.action.KiteResourceActionSource

internal sealed interface ResourceFeatureRequest {
    data object Back : ResourceFeatureRequest
    data object OpenManage : ResourceFeatureRequest
    data class OpenSearch(val query: String) : ResourceFeatureRequest
    data class OpenDetail(val resourceId: String) : ResourceFeatureRequest
    data class OpenMore(val resourceId: String) : ResourceFeatureRequest
    data class OpenRawJson(val resourceId: String) : ResourceFeatureRequest
    data class OpenInstallPlan(val targetResourceId: String) : ResourceFeatureRequest
    data class CancelInstallPlan(
        val targetResourceId: String,
        val resourceIds: List<String>
    ) : ResourceFeatureRequest
    data class SubmitAction(val request: KiteResourceActionRequest) : ResourceFeatureRequest
}

internal object ResourceFeatureResultContract {
    const val REQUEST_KEY = "kite.resource.feature.request"

    private const val KEY_KIND = "kind"
    private const val KEY_QUERY = "query"
    private const val KEY_RESOURCE_ID = "resource_id"
    private const val KEY_INTENT = "intent"
    private const val KEY_SOURCE = "source"
    private const val KEY_RESOURCE_IDS = "resource_ids"

    fun send(fragment: Fragment, request: ResourceFeatureRequest) {
        fragment.parentFragmentManager.setFragmentResult(REQUEST_KEY, encode(request))
    }

    fun parse(bundle: Bundle): ResourceFeatureRequest? = when (bundle.getString(KEY_KIND)) {
        KIND_BACK -> ResourceFeatureRequest.Back
        KIND_MANAGE -> ResourceFeatureRequest.OpenManage
        KIND_SEARCH -> ResourceFeatureRequest.OpenSearch(bundle.getString(KEY_QUERY).orEmpty())
        KIND_DETAIL -> bundle.resourceId()?.let(ResourceFeatureRequest::OpenDetail)
        KIND_MORE -> bundle.resourceId()?.let(ResourceFeatureRequest::OpenMore)
        KIND_RAW_JSON -> bundle.resourceId()?.let(ResourceFeatureRequest::OpenRawJson)
        KIND_OPEN_PLAN -> bundle.resourceId()?.let(ResourceFeatureRequest::OpenInstallPlan)
        KIND_CANCEL_PLAN -> bundle.resourceId()?.let { targetResourceId ->
            ResourceFeatureRequest.CancelInstallPlan(
                targetResourceId = targetResourceId,
                resourceIds = bundle.getStringArrayList(KEY_RESOURCE_IDS).orEmpty()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
            )
        }
        KIND_ACTION -> parseAction(bundle)
        else -> null
    }

    private fun encode(request: ResourceFeatureRequest): Bundle = Bundle().apply {
        when (request) {
            ResourceFeatureRequest.Back -> putString(KEY_KIND, KIND_BACK)
            ResourceFeatureRequest.OpenManage -> putString(KEY_KIND, KIND_MANAGE)
            is ResourceFeatureRequest.OpenSearch -> {
                putString(KEY_KIND, KIND_SEARCH)
                putString(KEY_QUERY, request.query)
            }
            is ResourceFeatureRequest.OpenDetail -> putResource(KIND_DETAIL, request.resourceId)
            is ResourceFeatureRequest.OpenMore -> putResource(KIND_MORE, request.resourceId)
            is ResourceFeatureRequest.OpenRawJson -> putResource(KIND_RAW_JSON, request.resourceId)
            is ResourceFeatureRequest.OpenInstallPlan -> putResource(KIND_OPEN_PLAN, request.targetResourceId)
            is ResourceFeatureRequest.CancelInstallPlan -> {
                putResource(KIND_CANCEL_PLAN, request.targetResourceId)
                putStringArrayList(KEY_RESOURCE_IDS, ArrayList(request.resourceIds))
            }
            is ResourceFeatureRequest.SubmitAction -> {
                putString(KEY_KIND, KIND_ACTION)
                putString(KEY_RESOURCE_ID, request.request.resourceId)
                putString(KEY_INTENT, request.request.intent.name)
                putString(KEY_SOURCE, request.request.source.name)
            }
        }
    }

    private fun Bundle.putResource(kind: String, resourceId: String) {
        putString(KEY_KIND, kind)
        putString(KEY_RESOURCE_ID, resourceId)
    }

    private fun Bundle.resourceId(): String? =
        getString(KEY_RESOURCE_ID)?.trim()?.takeIf(String::isNotBlank)

    private fun parseAction(bundle: Bundle): ResourceFeatureRequest? {
        val resourceId = bundle.resourceId() ?: return null
        val intent = bundle.getString(KEY_INTENT)
            ?.let { runCatching { KiteResourceActionIntent.valueOf(it) }.getOrNull() }
            ?: return null
        val source = bundle.getString(KEY_SOURCE)
            ?.let { runCatching { KiteResourceActionSource.valueOf(it) }.getOrNull() }
            ?: return null
        return ResourceFeatureRequest.SubmitAction(
            KiteResourceActionRequest(resourceId, intent, source)
        )
    }

    private const val KIND_BACK = "back"
    private const val KIND_MANAGE = "manage"
    private const val KIND_SEARCH = "search"
    private const val KIND_DETAIL = "detail"
    private const val KIND_MORE = "more"
    private const val KIND_RAW_JSON = "raw_json"
    private const val KIND_OPEN_PLAN = "open_plan"
    private const val KIND_CANCEL_PLAN = "cancel_plan"
    private const val KIND_ACTION = "action"
}
