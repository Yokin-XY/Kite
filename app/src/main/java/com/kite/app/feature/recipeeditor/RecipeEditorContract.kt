package com.kite.app.feature.recipeeditor

/**
 * 配方编辑器——已冻结。
 * 保留 MainActivity 引用的接口形状；Fragment 不可交互。
 */

internal sealed class RecipeEditorRequest {
    data object Close : RecipeEditorRequest()
    data object CloseRawJson : RecipeEditorRequest()
    data class OpenRawJson(val recipeId: String) : RecipeEditorRequest()
    data class OpenRunHistory(val recipeId: String) : RecipeEditorRequest()
    data class RequestShortcut(val recipeId: String) : RecipeEditorRequest()
    data class Deleted(val removedCardInstanceIds: List<String> = emptyList()) : RecipeEditorRequest()
    data class SubmitAction(
        val recipeId: String,
        val intent: com.kite.app.action.KiteRecipeActionIntent,
        val source: com.kite.app.action.KiteRecipeActionSource,
        val openTaskOnStart: Boolean = false,
        val instanceId: String = "",
    ) : RecipeEditorRequest()

    companion object {
        fun fromJson(@Suppress("UNUSED_PARAMETER") raw: String?): RecipeEditorRequest? = null
    }
}

internal data class RecipeEditorDraft(
    val rawJson: String = "",
    val editingRecipeId: String = "",
) {
    companion object {
        fun fromJson(@Suppress("UNUSED_PARAMETER") raw: String?): RecipeEditorDraft? = null
    }
}

internal object RecipeEditorResultContract {
    const val REQUEST_KEY = "recipe_editor_result"
    fun parse(@Suppress("UNUSED_PARAMETER") bundle: android.os.Bundle): RecipeEditorRequest =
        RecipeEditorRequest.Close
}

internal class RecipeEditorFragment : androidx.fragment.app.Fragment() {
    companion object {
        fun newInstance(
            recipeId: String? = null,
            restoredDraftRaw: String? = null,
            runtimeBlocked: Boolean = false,
        ): RecipeEditorFragment = RecipeEditorFragment()
    }
    fun currentDraftRaw(): String? = null
    fun updateRuntimeBlocked(@Suppress("UNUSED_PARAMETER") blocked: Boolean) { /* frozen */ }
    fun handleBackRequest() { /* frozen */ }
    fun recipeIdHint(): String = ""
}

internal class RecipeRawJsonFragment : androidx.fragment.app.Fragment() {
    companion object {
        fun newInstance(
            recipeId: String,
            @Suppress("UNUSED_PARAMETER") themeSelection: Any? = null,
        ): RecipeRawJsonFragment = RecipeRawJsonFragment()
    }
}
