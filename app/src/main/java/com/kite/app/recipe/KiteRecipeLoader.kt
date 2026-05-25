package com.kite.app.recipe

import android.content.Context
import org.json.JSONObject

class KiteRecipeLoader(private val context: Context) {
    fun loadSampleRecipe(): KiteRecipe = loadRecipe("recipes/hermes-webui.json")

    fun loadSampleRecipeJson(): String = readAsset("recipes/hermes-webui.json")

    private fun loadRecipe(assetPath: String): KiteRecipe {
        val jsonText = readAsset(assetPath)
        val json = JSONObject(jsonText)
        val stepsJson = json.getJSONArray("steps")
        val steps = buildList {
            for (index in 0 until stepsJson.length()) {
                val step = stepsJson.getJSONObject(index)
                add(
                    KiteRecipeStep(
                        type = step.getString("type"),
                        cmd = step.optString("cmd").ifBlank { null },
                        wait = if (step.has("wait")) step.getBoolean("wait") else null,
                        url = step.optString("url").ifBlank { null }
                    )
                )
            }
        }

        return KiteRecipe(
            id = json.getString("id"),
            name = json.getString("name"),
            description = json.optString("description"),
            icon = json.optString("icon"),
            taskLabel = json.optString("taskLabel"),
            defaultUrl = json.getString("defaultUrl"),
            shortcut = json.optBoolean("shortcut"),
            taskMode = json.optString("taskMode"),
            steps = steps
        )
    }

    private fun readAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
}
