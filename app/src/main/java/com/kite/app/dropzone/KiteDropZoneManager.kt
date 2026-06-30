package com.kite.app.dropzone

import android.content.Context
import android.os.Environment
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.foundation.runtime.ExternalExchangeManager
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

data class DropZoneStatus(
    val available: Boolean,
    val rootPath: String = "",
    val recipesPath: String = "",
    val message: String,
    val canRequestAccess: Boolean = true
)

data class DropZoneScanResult(
    val imported: Int,
    val skipped: Int,
    val invalid: Int,
    val message: String
)

class KiteDropZoneManager(
    private val context: Context,
    private val diagnostics: KiteDiagnostics
) {
    fun prepareDropZone(): DropZoneStatus {
        return runCatching {
            val root = rootDir()
            val cards = cardsDir()
            listOf(root, cards, importedDir(), logsDir(), legacyRecipesDir()).forEach { dir ->
                if (!dir.exists() && !dir.mkdirs()) {
                    error("mkdir_failed:${dir.absolutePath}")
                }
                if (!dir.isDirectory) {
                    error("not_directory:${dir.absolutePath}")
                }
            }
            if (!cards.canRead()) error("cards_not_readable")
            diagnostics.logDropZoneEvent("dropzone_ready", path = cards.absolutePath)
            DropZoneStatus(
                available = true,
                rootPath = root.absolutePath,
                recipesPath = cards.absolutePath,
                message = "共享卡片目录：${cards.absolutePath}"
            )
        }.getOrElse { error ->
            val rootPath = runCatching { rootDir().absolutePath }.getOrDefault("")
            diagnostics.logDropZoneEvent(
                "dropzone_create_failed",
                path = rootPath,
                reason = error.message.orEmpty()
            )
            unavailable("Kite 共享区不可用，请检查存储权限", rootPath = rootPath)
        }
    }

    fun scanAndImport(): DropZoneScanResult {
        val status = prepareDropZone()
        if (!status.available) {
            diagnostics.logDropZoneEvent(
                "dropzone_permission_missing",
                path = rootDir().absolutePath,
                reason = status.message
            )
            return DropZoneScanResult(0, 0, 0, status.message)
        }

        val cards = cardsDir()
        diagnostics.logDropZoneEvent("dropzone_scan_started", path = cards.absolutePath)
        var imported = 0
        var skipped = 0
        var invalid = 0

        legacyRecipeCandidates().forEach { candidate ->
            diagnostics.logDropZoneEvent("dropzone_recipe_found", path = candidate.absolutePath)
            val result = importCandidate(candidate)
            when (result) {
                ImportResult.Imported -> imported += 1
                ImportResult.Skipped -> skipped += 1
                ImportResult.Invalid -> invalid += 1
            }
        }

        diagnostics.logDropZoneEvent(
            "dropzone_scan_finished",
            path = cards.absolutePath,
            details = mapOf(
                "imported" to imported.toString(),
                "skipped" to skipped.toString(),
                "invalid" to invalid.toString()
            )
        )

        val sharedCount = sharedRecipeCandidates().size
        val message = when {
            imported > 0 && invalid > 0 -> "已加入 $imported 个卡片，跳过 $invalid 个无效文件；当前 $sharedCount 个"
            imported > 0 -> "已加入 $imported 个卡片；当前 $sharedCount 个"
            invalid > 0 -> "已刷新共享卡片目录，跳过 $invalid 个无效文件；当前 $sharedCount 个"
            skipped > 0 -> "已刷新共享卡片目录；当前 $sharedCount 个"
            else -> "已刷新共享卡片目录；当前 $sharedCount 个"
        }
        return DropZoneScanResult(imported, skipped, invalid, message)
    }

    private fun importCandidate(recipeFile: File): ImportResult {
        return runCatching {
            val json = JSONObject(recipeFile.readText())
            val validationError = validateRecipe(json)
            if (validationError != null) {
                diagnostics.logDropZoneEvent(
                    "dropzone_recipe_invalid",
                    path = recipeFile.absolutePath,
                    recipeId = recipeIdFromJson(json),
                    reason = validationError
                )
                return ImportResult.Invalid
            }

            val recipeId = recipeIdFromJson(json).ifBlank { recipeFile.nameWithoutExtension }
            if (sharedRecipeAlreadyAvailable(recipeId, recipeFile.absolutePath)) {
                diagnostics.logDropZoneEvent(
                    "dropzone_recipe_skipped",
                    path = recipeFile.absolutePath,
                    recipeId = recipeId,
                    reason = "recipe_already_in_shared_cards"
                )
                return ImportResult.Skipped
            }
            val target = uniqueTargetFile(cardsDir(), "${safeFileName(recipeId)}.json")
            if (target.exists()) {
                diagnostics.logDropZoneEvent(
                    "dropzone_recipe_skipped",
                    path = recipeFile.absolutePath,
                    recipeId = recipeId,
                    reason = "user_recipe_takes_precedence"
                )
                return ImportResult.Skipped
            }

            val enriched = JSONObject(json.toString()).put(
                DROPZONE_METADATA,
                JSONObject()
                    .put("runtimeSource", KiteRecipe.SOURCE_DROPZONE)
                    .put("sourcePath", recipeFile.absolutePath)
                    .put("sourceDir", recipeFile.parentFile?.absolutePath.orEmpty())
                    .put("importedAt", Instant.now().toString())
            )
            target.writeText(enriched.toString(2))
            diagnostics.logDropZoneEvent(
                "dropzone_recipe_imported",
                path = recipeFile.absolutePath,
                recipeId = recipeId,
                details = mapOf("target" to target.absolutePath)
            )
            ImportResult.Imported
        }.getOrElse { error ->
            diagnostics.logDropZoneEvent(
                "dropzone_recipe_invalid",
                path = recipeFile.absolutePath,
                reason = error.message.orEmpty()
            )
            ImportResult.Invalid
        }
    }

    private fun validateRecipe(json: JSONObject): String? {
        val recipe = json.optJSONArray("recipe")
        val hasRecipe = recipe != null && recipe.length() > 0
        val actions = json.optJSONObject("actions")
        val hasActions = actions != null && actions.length() > 0
        if (!json.has("execution") && !hasActions && !hasRecipe) return "missing_recipe"
        val execution = json.optJSONObject("execution")
        if (execution != null && execution.optString("mode").isBlank()) return "missing_execution_mode"
        if (json.has("execution") && execution == null) return "invalid_execution"
        return null
    }

    private fun legacyRecipeCandidates(): List<File> {
        val root = legacyRecipesDir()
        if (!root.exists()) return emptyList()
        val directJson = root.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }.orEmpty()
        val packJson = root.listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { File(it, "recipe.json") }
            .filter { it.isFile }
        return (directJson.toList() + packJson).distinctBy { it.absolutePath }
    }

    private fun sharedRecipeAlreadyAvailable(recipeId: String, sourcePath: String): Boolean =
        sharedRecipeCandidates().any { file ->
            runCatching {
                val json = JSONObject(file.readText())
                recipeIdFromJson(json) == recipeId ||
                    json.optJSONObject(DROPZONE_METADATA)?.optString("sourcePath") == sourcePath
            }.getOrDefault(false)
        }

    private fun sharedRecipeCandidates(): List<File> {
        val root = cardsDir()
        val directJson = root.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }.orEmpty()
        val packJson = root.listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { File(it, "recipe.json") }
            .filter { it.isFile }
        return (directJson.toList() + packJson).distinctBy { it.absolutePath }
    }

    private fun rootDir(): File =
        ExternalExchangeManager.ensureExchangeDir(context)

    private fun cardsDir(): File =
        ExternalExchangeManager.ensureCardsDir(context)

    private fun legacyRecipesDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kite/recipes")

    private fun importedDir(): File =
        ExternalExchangeManager.ensureImportsDir(context)

    private fun logsDir(): File = File(rootDir(), "logs")

    private fun unavailable(
        message: String,
        rootPath: String = runCatching { rootDir().absolutePath }.getOrDefault(""),
        recipesPath: String = runCatching { cardsDir().absolutePath }.getOrDefault("")
    ): DropZoneStatus =
        DropZoneStatus(available = false, rootPath = rootPath, recipesPath = recipesPath, message = message)

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
            .ifBlank { "recipe" }
    }

    private fun recipeIdFromJson(json: JSONObject): String =
        json.optJSONObject("base")?.optString("id").orEmpty().ifBlank { json.optString("id") }

    private enum class ImportResult {
        Imported,
        Skipped,
        Invalid
    }

    companion object {
        const val DROPZONE_METADATA = "_kiteDropZone"
    }
}
