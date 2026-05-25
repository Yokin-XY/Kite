package com.kite.app.recipe

import org.json.JSONArray
import org.json.JSONObject

data class KiteRecipe(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val defaultUrl: String,
    val shortcut: Boolean,
    val status: String,
    val icon: String = "",
    val taskLabel: String = "",
    val taskMode: String = "",
    val source: String = "asset",
    val steps: List<KiteRecipeStep> = emptyList()
) {
    fun hasShellStep(): Boolean = steps.any { it.type == STEP_SHELL && !it.cmd.isNullOrBlank() }

    fun openWebUrl(): String = steps.firstOrNull { it.type == STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url
        ?: defaultUrl

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("description", description)
        .put("type", type)
        .put("defaultUrl", defaultUrl)
        .put("shortcut", shortcut)
        .put("status", status)
        .put("icon", icon)
        .put("taskLabel", taskLabel)
        .put("taskMode", taskMode)
        .put("steps", JSONArray().apply {
            steps.forEach { put(it.toJson()) }
        })

    companion object {
        const val TYPE_OPEN_URL = "open_url"
        const val TYPE_START_SERVICE = "start_service"
        const val TYPE_COMMAND_WEB = "command_web"
        const val TYPE_TEMPLATE = "template"

        const val STEP_OPEN_WEB = "open_web"
        const val STEP_SHELL = "shell"

        fun fromJson(json: JSONObject, source: String): KiteRecipe {
            val stepsJson = json.optJSONArray("steps") ?: JSONArray()
            val steps = buildList {
                for (index in 0 until stepsJson.length()) {
                    add(KiteRecipeStep.fromJson(stepsJson.getJSONObject(index)))
                }
            }
            return KiteRecipe(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description"),
                type = json.optString("type", TYPE_OPEN_URL),
                defaultUrl = json.getString("defaultUrl"),
                shortcut = json.optBoolean("shortcut", false),
                status = json.optString("status", "unknown"),
                icon = json.optString("icon"),
                taskLabel = json.optString("taskLabel"),
                taskMode = json.optString("taskMode"),
                source = source,
                steps = steps
            )
        }
    }
}

data class KiteRecipeStep(
    val type: String,
    val cmd: String? = null,
    val wait: Boolean? = null,
    val url: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .apply {
            if (!cmd.isNullOrBlank()) put("cmd", cmd)
            if (wait != null) put("wait", wait)
            if (!url.isNullOrBlank()) put("url", url)
        }

    companion object {
        fun fromJson(json: JSONObject): KiteRecipeStep = KiteRecipeStep(
            type = json.getString("type"),
            cmd = json.optString("cmd").ifBlank { null },
            wait = if (json.has("wait")) json.optBoolean("wait") else null,
            url = json.optString("url").ifBlank { null }
        )
    }
}
