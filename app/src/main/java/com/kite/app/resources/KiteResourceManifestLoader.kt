package com.kite.app.resources

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class KiteResourceManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val iconText: String,
    val sections: List<String>,
    val tags: List<String>,
    val provides: List<String>,
    val baseRequirements: List<String>,
    val defaultRequirements: List<String>,
    val extensions: List<String>,
    val sourceType: String,
    val openRecipe: JSONObject?,
    val homeCards: List<KiteResourceHomeCard>
)

data class KiteResourceHomeCard(
    val label: String,
    val policy: String,
    val recipe: JSONObject
)

class KiteResourceManifestLoader(private val context: Context) {
    private var cachedManifests: Map<String, KiteResourceManifest>? = null

    fun manifest(resourceId: String): KiteResourceManifest? =
        manifests()[resourceId]

    fun openRecipeTemplate(resourceId: String): JSONObject? =
        manifest(resourceId)?.openRecipe?.deepCopy()

    fun firstHomeCardRecipeTemplate(resourceId: String): JSONObject? =
        manifest(resourceId)?.homeCards?.firstOrNull()?.recipe?.deepCopy()

    fun hasHomeCardTemplate(resourceId: String): Boolean =
        manifest(resourceId)?.homeCards?.isNotEmpty() == true

    fun manifests(): Map<String, KiteResourceManifest> {
        cachedManifests?.let { return it }
        val loaded = linkedMapOf<String, KiteResourceManifest>()
        context.assets.list(ASSET_ROOT).orEmpty()
            .sorted()
            .forEach { entry ->
                readManifest("$ASSET_ROOT/$entry/manifest.json")?.let { manifest ->
                    if (manifest.id.isNotBlank()) loaded[manifest.id] = manifest
                }
            }
        cachedManifests = loaded
        return loaded
    }

    fun invalidate() {
        cachedManifests = null
    }

    private fun readManifest(path: String): KiteResourceManifest? =
        runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                parseManifest(JSONObject(reader.readText()))
            }
        }.getOrNull()

    private fun parseManifest(json: JSONObject): KiteResourceManifest {
        val base = json.optJSONObject("base") ?: JSONObject()
        val icon = base.optJSONObject("icon")
        val display = json.optJSONObject("display") ?: JSONObject()
        val relations = json.optJSONObject("relations") ?: JSONObject()
        val source = json.optJSONObject("source") ?: JSONObject()
        val actions = json.optJSONObject("actions") ?: JSONObject()
        val open = actions.optJSONObject("open")
        val openRecipe = open
            ?.takeIf { it.optString("runtime") == "kite_recipe" }
            ?.optJSONObject("recipe")
            ?.deepCopy()

        return KiteResourceManifest(
            id = json.optString("id"),
            name = base.optString("name"),
            description = base.optString("description"),
            version = base.optString("version"),
            iconText = when (icon?.optString("type")) {
                "text" -> icon.optString("value")
                else -> ""
            },
            sections = display.optJSONArray("sections").toStringList(),
            tags = display.optJSONArray("tags").toStringList(),
            provides = relations.optJSONArray("provides").toStringList(),
            baseRequirements = relations.optJSONArray("base").toStringList(),
            defaultRequirements = relations.optJSONArray("defaults").toStringList(),
            extensions = relations.optJSONArray("extensions").toStringList(),
            sourceType = source.optString("type"),
            openRecipe = openRecipe,
            homeCards = parseHomeCards(json.optJSONArray("homeCards"))
        )
    }

    private fun parseHomeCards(cardsJson: JSONArray?): List<KiteResourceHomeCard> {
        if (cardsJson == null) return emptyList()
        return buildList {
            for (index in 0 until cardsJson.length()) {
                val card = cardsJson.optJSONObject(index) ?: continue
                val recipe = card.optJSONObject("recipe") ?: continue
                add(
                    KiteResourceHomeCard(
                        label = card.optString("label"),
                        policy = card.optString("policy", "manual"),
                        recipe = recipe.deepCopy()
                    )
                )
            }
        }
    }

    private fun JSONObject.deepCopy(): JSONObject =
        JSONObject(toString())

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    companion object {
        private const val ASSET_ROOT = "resources"
    }
}
