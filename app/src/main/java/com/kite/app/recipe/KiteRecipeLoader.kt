package com.kite.app.recipe

import android.content.Context
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.dropzone.KiteDropZoneManager
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

    fun deleteUserRecipe(recipe: KiteRecipe): Boolean {
        if (recipe.runtimeSource != KiteRecipe.SOURCE_USER) return false
        val target = File(userRecipeDir, "${recipe.id}.json")
        val deleted = target.exists() && target.delete()
        diagnostics.logRecipeEvent(
            if (deleted) "recipe_delete_success" else "recipe_delete_failed",
            recipe,
            mapOf("runtimeSource" to recipe.runtimeSource, "file" to target.name)
        )
        return deleted
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
                    val json = JSONObject(file.readText())
                    val source = if (json.has(KiteDropZoneManager.DROPZONE_METADATA)) {
                        KiteRecipe.SOURCE_DROPZONE
                    } else {
                        KiteRecipe.SOURCE_IMPORTED
                    }
                    val recipe = KiteRecipe.fromJson(json, runtimeSource = source)
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
        val id = input.id?.takeIf { it.isNotBlank() } ?: uniqueId(baseId)
        val explicitSteps = input.steps.mapIndexedNotNull { index, step ->
            buildStep(id, index, step)
        }
        val defaultUrl = input.url.trim().ifBlank {
            explicitSteps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url.orEmpty()
        }
        val inferredType = inferType(input.type, explicitSteps, defaultUrl)
        val iconName = input.iconName.ifBlank { KiteRecipeIcon.defaultNameForType(inferredType) }
        val steps = explicitSteps.ifEmpty { legacySteps(id, input, defaultUrl) }
        return KiteRecipe(
            schemaVersion = KiteRecipe.PROTOCOL_VERSION,
            id = id,
            name = input.name.trim(),
            description = input.description.ifBlank { defaultDescription(inferredType) },
            type = inferredType,
            defaultUrl = defaultUrl,
            shortcut = input.shortcut,
            icon = KiteRecipeIcon(type = "builtin", name = KiteRecipeIcon.normalizeName(iconName, inferredType)),
            card = KiteRecipeCard(accent = KiteRecipeCard.defaultAccentForType(inferredType), status = "unknown"),
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

    private fun buildStep(recipeId: String, index: Int, input: NewRecipeStepInput): KiteRecipeStep? {
        return when (input.type) {
            KiteRecipe.STEP_SHELL -> {
                val command = input.command.trim()
                if (command.isBlank()) return null
                val expected = input.expectedText.trim().takeIf { it.isNotBlank() }?.let {
                    KiteExpectedResult(mode = "contains", text = it, source = KiteRecipe.OUTPUT_LAST_MEANINGFUL)
                }
                KiteRecipeStep(
                    id = input.id.ifBlank { "step_cmd_${index + 1}_$recipeId" },
                    type = KiteRecipe.STEP_SHELL,
                    cmd = command,
                    runMode = KiteRecipe.normalizeRunMode(input.runMode) ?: KiteRecipe.RUN_MODE_DETACHED,
                    workdir = input.workdir.trim().ifBlank { null },
                    expected = expected,
                    outputPolicy = KiteOutputPolicy()
                )
            }

            KiteRecipe.STEP_OPEN_WEB -> {
                val url = input.url.trim()
                if (url.isBlank()) return null
                KiteRecipeStep(
                    id = input.id.ifBlank { "step_open_${index + 1}_$recipeId" },
                    type = KiteRecipe.STEP_OPEN_WEB,
                    url = url
                )
            }

            else -> null
        }
    }

    private fun legacySteps(recipeId: String, input: NewRecipeInput, defaultUrl: String): List<KiteRecipeStep> {
        val expected = input.expectedText.trim().takeIf { it.isNotBlank() }?.let {
            KiteExpectedResult(mode = "contains", text = it, source = KiteRecipe.OUTPUT_LAST_MEANINGFUL)
        }
        val shellStep = input.command.trim().takeIf { it.isNotBlank() }?.let {
            KiteRecipeStep(
                id = "step_start_$recipeId",
                type = KiteRecipe.STEP_SHELL,
                cmd = it,
                runMode = KiteRecipe.normalizeRunMode(input.runMode) ?: KiteRecipe.RUN_MODE_DETACHED,
                workdir = input.workdir.trim().ifBlank { null },
                expected = expected,
                outputPolicy = KiteOutputPolicy()
            )
        }
        val openWebStep = defaultUrl.takeIf { it.isNotBlank() }?.let {
            KiteRecipeStep(id = "step_open_$recipeId", type = KiteRecipe.STEP_OPEN_WEB, url = it)
        }
        return when (input.type) {
            KiteRecipe.TYPE_COMMAND_WEB, KiteRecipe.TYPE_SCRIPT_WEB -> listOfNotNull(shellStep, openWebStep)
            KiteRecipe.TYPE_START_SERVICE -> listOfNotNull(shellStep, openWebStep)
            KiteRecipe.TYPE_TEMPLATE -> emptyList()
            else -> listOfNotNull(openWebStep)
        }
    }

    private fun inferType(requestedType: String, steps: List<KiteRecipeStep>, defaultUrl: String): String {
        if (requestedType == KiteRecipe.TYPE_TEMPLATE) return KiteRecipe.TYPE_TEMPLATE
        val hasShell = steps.any { it.type == KiteRecipe.STEP_SHELL }
        val hasOpenWeb = steps.any { it.type == KiteRecipe.STEP_OPEN_WEB } || defaultUrl.isNotBlank()
        return when {
            hasShell && hasOpenWeb -> KiteRecipe.TYPE_COMMAND_WEB
            hasShell -> KiteRecipe.TYPE_START_SERVICE
            hasOpenWeb -> KiteRecipe.TYPE_OPEN_URL
            else -> KiteRecipe.TYPE_TEMPLATE
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
        KiteRecipe.TYPE_START_SERVICE -> "启动本地服务"
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
    val id: String? = null,
    val type: String,
    val name: String,
    val url: String,
    val command: String,
    val shortcut: Boolean,
    val iconName: String = "",
    val description: String = "",
    val workdir: String = "",
    val runMode: String = KiteRecipe.RUN_MODE_DETACHED,
    val expectedText: String = "",
    val steps: List<NewRecipeStepInput> = emptyList()
)

data class NewRecipeStepInput(
    val id: String = "",
    val type: String,
    val command: String = "",
    val url: String = "",
    val workdir: String = "",
    val runMode: String = KiteRecipe.RUN_MODE_DETACHED,
    val expectedText: String = ""
)
