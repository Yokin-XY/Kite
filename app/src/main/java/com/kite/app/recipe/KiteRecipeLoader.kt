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
                val recipe = KiteRecipe.fromJson(
                    JSONObject(readAsset("recipes/$name")),
                    runtimeSource = KiteRecipe.SOURCE_ASSETS
                )
                logRecipeLoaded(recipe)
                recipe
            }.onFailure {
                diagnostics.logRecipeEvent(
                    "recipe_load_error",
                    null,
                    mapOf("runtimeSource" to KiteRecipe.SOURCE_ASSETS, "file" to name, "error" to it.message.orEmpty())
                )
            }.getOrNull()
        }
    }

    private fun loadUserRecipes(): List<KiteRecipe> =
        userRecipeDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val recipe = KiteRecipe.fromJson(JSONObject(file.readText()), runtimeSource = KiteRecipe.SOURCE_USER)
                    logRecipeLoaded(recipe)
                    recipe
                }.onFailure {
                    diagnostics.logRecipeEvent(
                        "recipe_load_error",
                        null,
                        mapOf("runtimeSource" to KiteRecipe.SOURCE_USER, "file" to file.name, "error" to it.message.orEmpty())
                    )
                }.getOrNull()
            }

    private fun loadImportedRecipes(): List<KiteRecipe> =
        importedRecipeDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val recipe = KiteRecipe.fromJson(JSONObject(file.readText()), runtimeSource = KiteRecipe.SOURCE_IMPORTED)
                    logRecipeLoaded(recipe)
                    recipe
                }.onFailure {
                    diagnostics.logRecipeEvent(
                        "recipe_load_error",
                        null,
                        mapOf("runtimeSource" to KiteRecipe.SOURCE_IMPORTED, "file" to file.name, "error" to it.message.orEmpty())
                    )
                }.getOrNull()
            }

    private fun buildRecipe(input: NewRecipeInput): KiteRecipe {
        val now = Instant.now().toString()
        val baseId = slug(input.name).ifBlank { "recipe" }
        val id = uniqueId(baseId)
        val defaultUrl = input.url.trim()
        val iconName = input.iconName.ifBlank { KiteRecipeIcon.defaultNameForType(input.type) }
        val steps = when (input.type) {
            KiteRecipe.TYPE_COMMAND_WEB, KiteRecipe.TYPE_START_SERVICE -> listOf(
                KiteRecipeStep(
                    id = "step_start_$id",
                    type = KiteRecipe.STEP_SHELL,
                    cmd = input.command.trim(),
                    runMode = KiteRecipe.RUN_MODE_DETACHED,
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
            icon = KiteRecipeIcon(type = "builtin", name = KiteRecipeIcon.normalizeName(iconName, input.type)),
            card = KiteRecipeCard(accent = KiteRecipeCard.defaultAccentForType(input.type), status = "unknown"),
            execution = KiteExecution.steps(steps),
            taskLabel = input.name.trim(),
            taskMode = "separate",
            runtimeSource = KiteRecipe.SOURCE_USER
        ).also {
            diagnostics.logRecipeEvent(
                "recipe_built",
                it,
                mapOf(
                    "createdAt" to now,
                    "runtimeSource" to it.runtimeSource,
                    "icon" to it.icon.name,
                    "accent" to it.card.accent
                )
            )
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
        KiteRecipe.TYPE_SCRIPT_WEB -> "运行脚本并打开网页工作台"
        KiteRecipe.TYPE_START_SERVICE -> "启动本地服务工作台"
        KiteRecipe.TYPE_TEMPLATE -> "配置模板"
        else -> "打开网页工作台"
    }

    private fun logRecipeLoaded(recipe: KiteRecipe) {
        diagnostics.logRecipeEvent(
            "recipe_loaded",
            recipe,
            mapOf(
                "runtimeSource" to recipe.runtimeSource,
                "icon" to recipe.icon.name,
                "accent" to recipe.card.accent
            )
        )
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
    val iconName: String = "",
    val description: String = ""
)
