package com.kite.app.platform.resources

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.resources.KiteResourceManifestLoader
import org.json.JSONObject

/** 从资源清单恢复临时 open 配方，避免 Activity 依赖资源页面模型。 */
internal class AndroidResourceOpenRecipeResolver(
    private val manifestLoader: KiteResourceManifestLoader
) {
    fun resolve(recipeId: String): KiteRecipe? {
        val resourceId = recipeId
            .takeIf { it.startsWith("resource-") && it.endsWith("-open") }
            ?.removePrefix("resource-")
            ?.removeSuffix("-open")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val manifest = manifestLoader.requestManifest(resourceId) ?: return null
        val template = manifestLoader.requestOpenRecipeTemplate(resourceId) ?: return null
        val json = JSONObject(template.toString())
        val base = json.optJSONObject("base") ?: JSONObject().also { json.put("base", it) }
        base.put("id", recipeId)
        if (base.optString("name").isBlank()) base.put("name", manifest.name.ifBlank { resourceId })
        if (base.optString("description").isBlank()) base.put("description", manifest.description)
        if (manifest.iconAsset.isNotBlank()) {
            base.put(
                "icon",
                JSONObject()
                    .put("type", KiteRecipeIcon.TYPE_IMAGE)
                    .put("name", manifest.iconText.ifBlank { "resource" })
                    .put("source", manifest.iconAsset)
            )
        }
        val card = json.optJSONObject("card") ?: JSONObject().also { json.put("card", it) }
        if (card.optString("accent").isBlank()) card.put("accent", "primary")
        return KiteRecipe.fromJson(json, runtimeSource = KiteRecipe.SOURCE_USER)
            .copy(id = recipeId, runtimeSource = RUNTIME_SOURCE)
    }

    companion object {
        const val RUNTIME_SOURCE = "resource_open"
    }
}
