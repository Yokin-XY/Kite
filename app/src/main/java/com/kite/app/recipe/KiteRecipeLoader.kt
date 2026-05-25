package com.kite.app.recipe

import android.content.Context
import com.kite.app.diagnostics.KiteDiagnostics
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

class KiteRecipeLoader(
    private val context: Context,
    private val diagnostics: KiteDiagnostics
) {
    private val userRecipeDir = File(context.filesDir, "recipes").apply { mkdirs() }

    fun loadAllRecipes(): List<KiteRecipe> {
        val assets = loadAssetRecipes()
        val users = loadUserRecipes()
        return (assets + users).distinctBy { it.id }
    }

    fun loadSampleRecipeJson(): String = readAsset("recipes/hermes-webui.json")

    fun saveUserRecipe(input: NewRecipeInput): KiteRecipe {
        val recipe = buildRecipe(input)
        val target = File(userRecipeDir, "${recipe.id}.json")
        runCatching {
            target.writeText(recipe.toJson().toString(2))
        }.onSuccess {
            diagnostics.logRecipeSaved(recipe)
        }.onFailure {
            diagnostics.logRecipeSaveError(recipe, it)
            throw it
        }
        return recipe
    }

    fun userRecipesPath(): String = userRecipeDir.absolutePath

    private fun loadAssetRecipes(): List<KiteRecipe> {
        val recipeFiles = context.assets.list("recipes").orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
        return recipeFiles.mapNotNull { name ->
            runCatching {
                KiteRecipe.fromJson(JSONObject(readAsset("recipes/$name")), source = "asset")
            }.onFailure {
                diagnostics.logRecipeEvent("asset_load_failed", null, mapOf("file" to name, "error" to it.message.orEmpty()))
            }.getOrNull()
        }
    }

    private fun loadUserRecipes(): List<KiteRecipe> =
        userRecipeDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    KiteRecipe.fromJson(JSONObject(file.readText()), source = "user")
                }.onFailure {
                    diagnostics.logRecipeEvent("user_load_failed", null, mapOf("file" to file.name, "error" to it.message.orEmpty()))
                }.getOrNull()
            }

    private fun buildRecipe(input: NewRecipeInput): KiteRecipe {
        val now = Instant.now().toString()
        val baseId = slug(input.name).ifBlank { "recipe" }
        val id = uniqueId(baseId)
        val defaultUrl = input.url.trim()
        val steps = when (input.type) {
            KiteRecipe.TYPE_COMMAND_WEB -> listOf(
                KiteRecipeStep(
                    type = KiteRecipe.STEP_SHELL,
                    cmd = input.command.trim(),
                    wait = true
                ),
                KiteRecipeStep(type = KiteRecipe.STEP_OPEN_WEB, url = defaultUrl)
            )

            else -> listOf(KiteRecipeStep(type = KiteRecipe.STEP_OPEN_WEB, url = defaultUrl))
        }
        return KiteRecipe(
            id = id,
            name = input.name.trim(),
            description = input.description.ifBlank { defaultDescription(input.type) },
            type = input.type,
            defaultUrl = defaultUrl,
            shortcut = input.shortcut,
            status = "unknown",
            icon = iconFor(input.type),
            taskLabel = input.name.trim(),
            taskMode = "separate",
            source = "user",
            steps = steps
        ).also {
            diagnostics.logRecipeEvent("recipe_built", it, mapOf("createdAt" to now))
        }
    }

    private fun uniqueId(base: String): String {
        var candidate = base
        var suffix = 2
        while (File(userRecipeDir, "$candidate.json").exists()) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun slug(text: String): String {
        val normalized = Normalizer.normalize(text.lowercase(Locale.US), Normalizer.Form.NFD)
        return normalized
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
            .trim('-')
            .take(48)
    }

    private fun defaultDescription(type: String): String = when (type) {
        KiteRecipe.TYPE_COMMAND_WEB -> "执行命令后打开网页工作台"
        KiteRecipe.TYPE_START_SERVICE -> "启动本地服务工作台"
        KiteRecipe.TYPE_TEMPLATE -> "配置模板"
        else -> "打开网页工作台"
    }

    private fun iconFor(type: String): String = when (type) {
        KiteRecipe.TYPE_COMMAND_WEB -> ">_"
        KiteRecipe.TYPE_START_SERVICE -> "▶"
        KiteRecipe.TYPE_TEMPLATE -> "▦"
        else -> "◎"
    }

    private fun readAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
}

data class NewRecipeInput(
    val type: String,
    val name: String,
    val url: String,
    val command: String,
    val shortcut: Boolean,
    val description: String = ""
)
