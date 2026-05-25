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
    private val importedRecipeDir = File(userRecipeDir, "imported").apply { mkdirs() }
    private val runReportsDir = File(context.filesDir, "recipe-runs").apply { mkdirs() }

    fun loadAllRecipes(): List<KiteRecipe> {
        val merged = linkedMapOf<String, KiteRecipe>()
        loadAssetRecipes().forEach { merged[it.id] = it }
        loadImportedRecipes().forEach { merged[it.id] = it }
        loadUserRecipes().forEach { merged[it.id] = it }
        return merged.values.toList()
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

    fun importedRecipesPath(): String = importedRecipeDir.absolutePath

    fun runReportsPath(): String = runReportsDir.absolutePath

    private fun loadAssetRecipes(): List<KiteRecipe> {
        val recipeFiles = context.assets.list("recipes").orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
        return recipeFiles.mapNotNull { name ->
            runCatching {
                KiteRecipe.fromJson(JSONObject(readAsset("recipes/$name")), source = KiteRecipe.SOURCE_ASSETS)
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
                    KiteRecipe.fromJson(JSONObject(file.readText()), source = KiteRecipe.SOURCE_USER)
                }.onFailure {
                    diagnostics.logRecipeEvent("user_load_failed", null, mapOf("file" to file.name, "error" to it.message.orEmpty()))
                }.getOrNull()
            }

    private fun loadImportedRecipes(): List<KiteRecipe> =
        importedRecipeDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    KiteRecipe.fromJson(JSONObject(file.readText()), source = KiteRecipe.SOURCE_IMPORTED)
                }.onFailure {
                    diagnostics.logRecipeEvent("imported_load_failed", null, mapOf("file" to file.name, "error" to it.message.orEmpty()))
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
                    id = "step_start_$id",
                    type = KiteRecipe.STEP_SHELL,
                    cmd = input.command.trim(),
                    runMode = KiteRecipe.RUN_MODE_WAIT,
                    outputPolicy = KiteOutputPolicy()
                ),
                KiteRecipeStep(id = "step_open_$id", type = KiteRecipe.STEP_OPEN_WEB, url = defaultUrl)
            )

            else -> listOf(KiteRecipeStep(id = "step_open_$id", type = KiteRecipe.STEP_OPEN_WEB, url = defaultUrl))
        }
        return KiteRecipe(
            schemaVersion = KiteRecipe.PROTOCOL_VERSION,
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
            source = KiteRecipe.SOURCE_USER,
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
        KiteRecipe.TYPE_START_SERVICE -> "▷"
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
