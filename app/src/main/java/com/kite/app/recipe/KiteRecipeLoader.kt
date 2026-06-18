package com.kite.app.recipe

import android.content.Context
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.dropzone.KiteDropZoneManager
import com.kftest.app.foundation.runtime.ExternalExchangeManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

class KiteRecipeLoader(
    private val context: Context,
    private val diagnostics: KiteDiagnostics
) {
    private val legacyUserRecipeDir = File(context.filesDir, "recipes").apply { mkdirs() }
    private val legacyImportedRecipeDir = File(legacyUserRecipeDir, "imported").apply { mkdirs() }
    private val runReportsDir = File(context.filesDir, "recipe-runs").apply { mkdirs() }

    fun loadAllRecipes(): List<KiteRecipe> {
        val cardsDir = sharedCardsDir()
        seedAssetRecipesIfNeeded(cardsDir)
        migrateLegacyPrivateRecipes(cardsDir)
        removeDeprecatedAssetRecipesIfNeeded(cardsDir)
        val usedIds = linkedSetOf<String>()
        return sharedRecipeFiles(cardsDir)
            .mapNotNull { file -> loadRecipeFile(file, KiteRecipe.SOURCE_SHARED, canonicalize = true, usedIds = usedIds) }
    }

    fun saveUserRecipe(input: NewRecipeInput): KiteRecipe {
        val recipe = buildRecipe(input)
        deleteRecipeFiles(recipe.id)
        val target = File(sharedCardsDir(), "${safeFileName(recipe.id)}.json")
        runCatching {
            target.writeText(recipe.toJson(includeLocalIdentity = true).toString(2))
        }.onSuccess {
            diagnostics.logRecipeSaved(recipe)
        }.onFailure {
            diagnostics.logRecipeSaveError(recipe, it)
            throw it
        }
        return recipe
    }

    fun addSharedRecipeTemplate(template: JSONObject, fileStem: String): File {
        val json = JSONObject(template.toString())
        val base = json.optJSONObject("base") ?: JSONObject().also { json.put("base", it) }
        if (!base.has("id")) {
            base.put("id", "")
        }
        val target = uniqueTargetFile(sharedCardsDir(), "${safeFileName(fileStem)}.json")
        runCatching {
            target.writeText(json.toString(2))
        }.onSuccess {
            diagnostics.logRecipeEvent(
                "recipe_template_added_to_home",
                null,
                mapOf("file" to target.name, "stem" to fileStem)
            )
        }.onFailure {
            diagnostics.logRecipeEvent(
                "recipe_template_add_failed",
                null,
                mapOf("file" to target.name, "stem" to fileStem, "error" to it.message.orEmpty())
            )
            throw it
        }
        return target
    }

    fun deleteRecipe(recipe: KiteRecipe): Boolean {
        val deleted = deleteRecipeFiles(recipe.id)
        diagnostics.logRecipeEvent(
            if (deleted) "recipe_delete_success" else "recipe_delete_failed",
            recipe,
            mapOf("runtimeSource" to recipe.runtimeSource, "sharedPath" to sharedCardsDir().absolutePath)
        )
        return deleted
    }

    fun deleteUserRecipe(recipe: KiteRecipe): Boolean {
        return deleteRecipe(recipe)
    }

    fun userRecipesPath(): String = sharedCardsDir().absolutePath

    fun importedRecipesPath(): String = ExternalExchangeManager.ensureImportsDir(context).absolutePath

    fun runReportsPath(): String = runReportsDir.absolutePath

    fun sharedCardsPath(): String = sharedCardsDir().absolutePath

    private fun sharedCardsDir(): File =
        ExternalExchangeManager.ensureCardsDir(context)

    private fun seedAssetRecipesIfNeeded(cardsDir: File) {
        val marker = File(cardsDir, ASSET_SEED_MARKER)
        if (marker.exists()) return
        val recipeFiles = context.assets.list("recipes").orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
        recipeFiles.forEach { name ->
            runCatching {
                val json = JSONObject(readAsset("recipes/$name"))
                val id = recipeIdFromJson(json).ifBlank { name.removeSuffix(".json") }
                val target = uniqueTargetFile(cardsDir, "${safeFileName(id)}.json")
                target.writeText(json.toString(2))
                diagnostics.logRecipeEvent(
                    "recipe_asset_seeded",
                    null,
                    mapOf("file" to name, "target" to target.name)
                )
            }.onFailure {
                diagnostics.logRecipeEvent(
                    "recipe_asset_seed_failed",
                    null,
                    mapOf("file" to name, "error" to it.message.orEmpty())
                )
            }
        }
        marker.writeText(Instant.now().toString())
    }

    private fun migrateLegacyPrivateRecipes(cardsDir: File) {
        val marker = File(cardsDir, LEGACY_MIGRATION_MARKER)
        if (marker.exists()) return
        var hasFailure = false
        listOf(legacyImportedRecipeDir, legacyUserRecipeDir).forEach { sourceDir ->
            sourceDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
                .orEmpty()
                .forEach { file ->
                    val target = File(cardsDir, file.name)
                    if (!target.exists()) {
                        runCatching {
                            file.copyTo(target, overwrite = false)
                            diagnostics.logRecipeEvent(
                                "recipe_migrated_to_shared_cards",
                                null,
                                mapOf("from" to file.absolutePath, "target" to target.absolutePath)
                            )
                        }.onFailure {
                            hasFailure = true
                            diagnostics.logRecipeEvent(
                                "recipe_migration_failed",
                                null,
                                mapOf("from" to file.absolutePath, "error" to it.message.orEmpty())
                            )
                        }
                    }
                }
        }
        if (!hasFailure) marker.writeText(Instant.now().toString())
    }

    private fun removeDeprecatedAssetRecipesIfNeeded(cardsDir: File) {
        val marker = File(cardsDir, DEPRECATED_ASSET_CLEANUP_MARKER)
        if (marker.exists()) return
        var hasFailure = false
        sharedRecipeFiles(cardsDir).forEach { file ->
            if (!file.nameWithoutExtension.startsWith(DEPRECATED_HERMES_WEBUI_FILE_STEM)) return@forEach
            val shouldRemove = runCatching {
                isDeprecatedHermesWebUiPreset(JSONObject(file.readText()))
            }.getOrDefault(false)
            if (!shouldRemove) return@forEach
            val deleted = runCatching { file.delete() }
                .onFailure {
                    diagnostics.logRecipeEvent(
                        "recipe_deprecated_asset_cleanup_failed",
                        null,
                        mapOf("file" to file.name, "error" to it.message.orEmpty())
                    )
                }
                .getOrDefault(false)
            if (deleted) {
                diagnostics.logRecipeEvent(
                    "recipe_deprecated_asset_removed",
                    null,
                    mapOf("file" to file.name, "preset" to DEPRECATED_HERMES_WEBUI_FILE_STEM)
                )
            } else {
                hasFailure = true
                diagnostics.logRecipeEvent(
                    "recipe_deprecated_asset_cleanup_failed",
                    null,
                    mapOf("file" to file.name, "error" to "delete returned false")
                )
            }
        }
        if (!hasFailure) marker.writeText(Instant.now().toString())
    }

    private fun isDeprecatedHermesWebUiPreset(json: JSONObject): Boolean {
        val base = json.optJSONObject("base") ?: return false
        if (base.optString("name") != DEPRECATED_HERMES_WEBUI_NAME) return false
        val icon = base.optJSONObject("icon") ?: return false
        if (icon.optString("type") != KiteRecipeIcon.TYPE_BUILTIN || icon.optString("name") != "terminal") {
            return false
        }
        val steps = json.optJSONArray("recipe") ?: return false
        var hasOldStartCommand = false
        var hasOldOpenUrl = false
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            when (step.optString("type")) {
                KiteRecipe.STEP_SHELL -> {
                    hasOldStartCommand = hasOldStartCommand ||
                        step.optString("cmd").trim() == DEPRECATED_HERMES_WEBUI_START_COMMAND
                }

                KiteRecipe.STEP_OPEN_WEB -> {
                    hasOldOpenUrl = hasOldOpenUrl ||
                        step.optString("url").trim() == DEPRECATED_HERMES_WEBUI_OPEN_URL
                }
            }
        }
        return hasOldStartCommand && hasOldOpenUrl
    }

    private fun sharedRecipeFiles(cardsDir: File): List<File> {
        val directJson = cardsDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }.orEmpty()
        val packJson = cardsDir.listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { File(it, "recipe.json") }
            .filter { it.isFile }
        return (directJson.toList() + packJson)
            .distinctBy { it.absolutePath }
            .sortedBy { it.name.lowercase(Locale.US) }
    }

    private fun loadRecipeFile(
        file: File,
        runtimeSource: String,
        canonicalize: Boolean,
        usedIds: MutableSet<String>
    ): KiteRecipe? {
        return runCatching {
            val originalJson = JSONObject(file.readText())
            val json = ensureRecipeIdentity(file, originalJson, usedIds)
            var recipe = KiteRecipe.fromJson(json, runtimeSource = runtimeSource)
            if (canonicalize) {
                recipe = canonicalizeRecipeFile(file, json, recipe)
            }
            usedIds.add(recipe.id)
            logRecipeLoaded(recipe)
            recipe
        }.onFailure {
            diagnostics.logRecipeEvent(
                "recipe_load_error",
                null,
                mapOf("runtimeSource" to runtimeSource, "file" to file.name, "error" to it.message.orEmpty())
            )
        }.getOrNull()
    }

    private fun ensureRecipeIdentity(
        file: File,
        originalJson: JSONObject,
        usedIds: MutableSet<String>
    ): JSONObject {
        val json = JSONObject(originalJson.toString())
        var changed = false
        val base = json.optJSONObject("base") ?: JSONObject().also {
            json.put("base", it)
            changed = true
        }
        val legacyHeader = json.optJSONObject("header")
        val name = base.optString("name").ifBlank {
            legacyHeader?.optString("name").orEmpty().ifBlank { json.optString("name") }
        }
        if (name.isBlank()) {
            base.put("name", file.nameWithoutExtension)
            changed = true
        }
        val currentId = base.optString("id").ifBlank { json.optString("id") }.trim()
        if (currentId.isBlank() || currentId in usedIds) {
            val nextId = uniqueNumericId(usedIds)
            base.put("id", nextId)
            changed = true
        }
        if (changed) {
            file.writeText(json.toString(2))
            diagnostics.logRecipeEvent(
                "recipe_identity_assigned",
                null,
                mapOf("file" to file.name, "id" to base.optString("id"))
            )
        }
        return json
    }

    private fun canonicalizeRecipeFile(file: File, originalJson: JSONObject, recipe: KiteRecipe): KiteRecipe {
        if (!needsCanonicalization(originalJson)) return recipe
        val canonicalRecipe = canonicalRecipeFor(recipe)

        runCatching {
            val canonicalJson = canonicalRecipe.toJson(includeLocalIdentity = true)
            originalJson.optJSONObject(KiteDropZoneManager.DROPZONE_METADATA)?.let {
                canonicalJson.put(KiteDropZoneManager.DROPZONE_METADATA, it)
            }
            file.writeText(canonicalJson.toString(2))
            diagnostics.logRecipeEvent(
                "recipe_canonicalized",
                canonicalRecipe,
                mapOf(
                    "file" to file.name,
                    "runtimeSource" to canonicalRecipe.runtimeSource,
                    "reason" to canonicalizationReasons(originalJson).joinToString(",")
                )
            )
        }.onFailure {
            diagnostics.logRecipeEvent(
                "recipe_canonicalize_failed",
                recipe,
                mapOf("file" to file.name, "error" to it.message.orEmpty())
            )
        }
        return canonicalRecipe
    }

    private fun needsCanonicalization(json: JSONObject): Boolean =
        canonicalizationReasons(json).isNotEmpty()

    private fun canonicalizationReasons(json: JSONObject): List<String> = buildList {
        if (!json.has("base") || json.optJSONObject("base") == null) add("base")
        if (!json.has("recipe") || json.optJSONArray("recipe") == null) add("recipe")
        if (json.has("schemaVersion")) add("legacy_schema_version")
        if (json.has("header") || json.has("name") || json.has("description") || json.has("type") || json.has("defaultUrl") || json.has("icon")) {
            add("legacy_header_fields")
        }
        if (json.has("id")) add("legacy_top_level_id")
        if (json.has("card") || json.has("status") || json.has("accent")) add("legacy_card_state")
        if (json.has("execution")) add("legacy_execution")
        if (json.has("expected") || json.has("taskLabel") || json.has("taskMode")) add("legacy_runtime_fields")
        if (json.has("steps")) add("legacy_steps")
        if (json.has("actions")) add("legacy_actions")
        if (containsLegacyActionObjects(json.optJSONObject("actions"))) add("legacy_action_objects")
        if (containsLegacyStepFields(json)) add("legacy_step_fields")
    }

    private fun containsLegacyActionObjects(actions: JSONObject?): Boolean {
        if (actions == null) return false
        val keys = actions.keys()
        while (keys.hasNext()) {
            if (actions.opt(keys.next()) is JSONObject) return true
        }
        return false
    }

    private fun canonicalRecipeFor(recipe: KiteRecipe): KiteRecipe = recipe

    private fun containsLegacyStepFields(json: JSONObject): Boolean {
        if (containsLegacyStepFields(json.optJSONArray("recipe") ?: JSONArray())) return true
        if (containsLegacyStepFields(json.optJSONArray("steps") ?: JSONArray())) return true
        if (containsLegacyStepFields(json.optJSONObject("execution")?.optJSONArray("steps") ?: JSONArray())) return true
        val actions = json.optJSONObject("actions") ?: return false
        val keys = actions.keys()
        while (keys.hasNext()) {
            when (val action = actions.opt(keys.next())) {
                is JSONObject -> {
                    if (action.has("expected")) return true
                    if (containsLegacyStepFields(action.optJSONArray("steps") ?: JSONArray())) return true
                }
                is JSONArray -> {
                    if (containsLegacyStepFields(action)) return true
                }
            }
        }
        return false
    }

    private fun containsLegacyStepFields(steps: JSONArray): Boolean {
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            if (step.has("id")) return true
            if (step.has("wait")) return true
            if (step.has("expected")) return true
            if (step.has("outputPolicy")) return true
            val runMode = step.optString("runMode")
            if (runMode.isNotBlank()) return true
        }
        return false
    }

    private fun buildRecipe(input: NewRecipeInput): KiteRecipe {
        val now = Instant.now().toString()
        val id = input.id?.takeIf { it.isNotBlank() } ?: uniqueNumericId(existingSharedIds())
        val explicitSteps = input.steps.mapIndexedNotNull { index, step ->
            buildStep(id, index, step)
        }
        val defaultUrl = input.url.trim().ifBlank {
            explicitSteps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url.orEmpty()
        }
        val inferredType = inferType(input.type, explicitSteps, defaultUrl)
        val iconType = input.iconType.trim().lowercase()
        val icon = if (iconType == KiteRecipeIcon.TYPE_IMAGE && input.iconSource.isNotBlank()) {
            KiteRecipeIcon(
                type = KiteRecipeIcon.TYPE_IMAGE,
                name = input.iconName.ifBlank { "custom" },
                source = input.iconSource
            )
        } else {
            val iconName = input.iconName.ifBlank { KiteRecipeIcon.defaultNameForType(inferredType) }
            KiteRecipeIcon(
                type = KiteRecipeIcon.TYPE_BUILTIN,
                name = KiteRecipeIcon.normalizeName(iconName, inferredType)
            )
        }
        return KiteRecipe(
            schemaVersion = KiteRecipe.PROTOCOL_VERSION,
            id = id,
            name = input.name.trim(),
            description = input.description.ifBlank { defaultDescription(inferredType) },
            type = inferredType,
            category = KiteRecipe.normalizeCategory(input.category),
            defaultUrl = defaultUrl,
            shortcut = input.shortcut,
            icon = icon,
            launch = KiteLaunchConfig(openInstance = input.openInstanceOnStart),
            execution = KiteExecution.steps(explicitSteps),
            actions = KiteRecipe.defaultActionsFor(explicitSteps, defaultUrl),
            taskLabel = input.name.trim(),
            taskMode = "separate",
            runtimeSource = KiteRecipe.SOURCE_SHARED
        ).also {
            diagnostics.logRecipeEvent(
                "recipe_built",
                it,
                mapOf(
                    "createdAt" to now,
                    "runtimeSource" to it.runtimeSource,
                    "icon" to it.icon.name,
                    "category" to it.category
                )
            )
        }
    }

    private fun buildStep(recipeId: String, index: Int, input: NewRecipeStepInput): KiteRecipeStep? {
        return when (input.type) {
            KiteRecipe.STEP_TERMINAL -> {
                val text = input.command.trim()
                KiteRecipeStep(
                    id = input.id.ifBlank { "step_terminal_${index + 1}_$recipeId" },
                    type = KiteRecipe.STEP_TERMINAL,
                    text = text.takeIf { it.isNotBlank() }?.let {
                        if (it.endsWith("\n")) it else "$it\n"
                    }
                )
            }

            KiteRecipe.STEP_SHELL -> {
                val command = input.command.trim()
                if (command.isBlank()) return null
                KiteRecipeStep(
                    id = input.id.ifBlank { "step_cmd_${index + 1}_$recipeId" },
                    type = KiteRecipe.STEP_SHELL,
                    cmd = command,
                    workdir = input.workdir.trim().ifBlank { null },
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

    private fun inferType(requestedType: String, steps: List<KiteRecipeStep>, defaultUrl: String): String {
        if (requestedType == KiteRecipe.TYPE_TEMPLATE) return KiteRecipe.TYPE_TEMPLATE
        val hasShell = steps.any { it.type == KiteRecipe.STEP_SHELL || it.type == KiteRecipe.STEP_TERMINAL }
        val hasOpenWeb = steps.any { it.type == KiteRecipe.STEP_OPEN_WEB } || defaultUrl.isNotBlank()
        return when {
            hasShell && hasOpenWeb -> KiteRecipe.TYPE_COMMAND_WEB
            hasShell -> KiteRecipe.TYPE_START_SERVICE
            hasOpenWeb -> KiteRecipe.TYPE_OPEN_URL
            else -> KiteRecipe.TYPE_TEMPLATE
        }
    }

    private fun existingSharedIds(): MutableSet<String> =
        sharedRecipeFiles(sharedCardsDir())
            .mapNotNull { file ->
                runCatching { recipeIdFromJson(JSONObject(file.readText())).takeIf { it.isNotBlank() } }.getOrNull()
            }
            .toMutableSet()

    private fun uniqueId(base: String, existingIds: Set<String>, currentFile: File? = null): String {
        var candidate = base
        var suffix = 2
        while (candidate in existingIds || conflictingCardFileExists(candidate, currentFile)) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun uniqueNumericId(existingIds: Set<String>): String {
        var candidate = System.currentTimeMillis().toString()
        var suffix = 0L
        while (candidate in existingIds || conflictingCardFileExists(candidate, currentFile = null)) {
            suffix += 1
            candidate = (System.currentTimeMillis() + suffix).toString()
        }
        return candidate
    }

    private fun conflictingCardFileExists(candidate: String, currentFile: File?): Boolean {
        val candidateFile = File(sharedCardsDir(), "${safeFileName(candidate)}.json")
        if (!candidateFile.exists()) return false
        if (currentFile == null) return true
        return !sameFile(candidateFile, currentFile)
    }

    private fun sameFile(left: File, right: File): Boolean =
        runCatching { left.canonicalFile == right.canonicalFile }
            .getOrDefault(left.absolutePath == right.absolutePath)

    private fun deleteRecipeFiles(recipeId: String): Boolean {
        val safeId = safeFileName(recipeId)
        val files = sharedRecipeFiles(sharedCardsDir()).filter { file ->
            file.nameWithoutExtension == safeId ||
                runCatching { recipeIdFromJson(JSONObject(file.readText())) == recipeId }.getOrDefault(false)
        }
        return files.fold(false) { deletedAny, file ->
            runCatching { file.delete() }.getOrDefault(false) || deletedAny
        }
    }

    private fun uniqueTargetFile(directory: File, fileName: String): File {
        val safeName = safeFileName(fileName.removeSuffix(".json")).ifBlank { "card" }
        var candidate = File(directory, "$safeName.json")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(directory, "$safeName-$suffix.json")
            suffix += 1
        }
        return candidate
    }

    private fun safeFileName(input: String): String {
        val normalized = Normalizer.normalize(input.lowercase(Locale.US), Normalizer.Form.NFD)
        return normalized
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.')
            .ifBlank { "card" }
    }

    private fun recipeIdFromJson(json: JSONObject): String =
        json.optJSONObject("base")?.optString("id").orEmpty().ifBlank { json.optString("id") }

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
                "category" to recipe.category
            )
        )
    }

    private fun readAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    companion object {
        private const val ASSET_SEED_MARKER = ".asset-presets-seeded-v1"
        private const val LEGACY_MIGRATION_MARKER = ".legacy-private-migrated-v1"
        private const val DEPRECATED_ASSET_CLEANUP_MARKER = ".deprecated-asset-presets-cleaned-v1"
        private const val DEPRECATED_HERMES_WEBUI_FILE_STEM = "hermes-webui"
        private const val DEPRECATED_HERMES_WEBUI_NAME = "Hermes WebUI"
        private const val DEPRECATED_HERMES_WEBUI_START_COMMAND = "hermes-web-ui start --port 8648"
        private const val DEPRECATED_HERMES_WEBUI_OPEN_URL = "http://127.0.0.1:8648"
    }
}

data class NewRecipeInput(
    val id: String? = null,
    val type: String,
    val name: String,
    val category: String = "",
    val url: String,
    val command: String,
    val shortcut: Boolean,
    val openInstanceOnStart: Boolean = true,
    val iconName: String = "",
    val iconType: String = KiteRecipeIcon.TYPE_BUILTIN,
    val iconSource: String = "",
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
    val workdir: String = ""
)
