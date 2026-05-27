package com.kite.app.dropzone

import android.content.Context
import android.os.Environment
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
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
    private val privateRecipesDir = File(context.filesDir, "recipes").apply { mkdirs() }
    private val privateImportedDir = File(privateRecipesDir, "imported").apply { mkdirs() }

    fun prepareDropZone(): DropZoneStatus {
        diagnostics.logDropZoneEvent("dropzone_create_started", path = rootDir().absolutePath)
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            diagnostics.logDropZoneEvent(
                "dropzone_create_failed",
                path = rootDir().absolutePath,
                reason = "external_storage_not_mounted"
            )
            return unavailable("Kite 投放区不可用，请检查存储权限")
        }

        return runCatching {
            listOf(rootDir(), recipesDir(), importedDir(), logsDir()).forEach { dir ->
                if (!dir.exists() && !dir.mkdirs()) {
                    error("mkdir_failed:${dir.absolutePath}")
                }
                if (!dir.isDirectory) {
                    error("not_directory:${dir.absolutePath}")
                }
            }
            if (!recipesDir().canRead()) error("recipes_not_readable")
            diagnostics.logDropZoneEvent("dropzone_ready", path = recipesDir().absolutePath)
            DropZoneStatus(
                available = true,
                rootPath = rootDir().absolutePath,
                recipesPath = recipesDir().absolutePath,
                message = "投放区已就绪：Download/Kite/recipes"
            )
        }.getOrElse { error ->
            diagnostics.logDropZoneEvent(
                "dropzone_create_failed",
                path = rootDir().absolutePath,
                reason = error.message.orEmpty()
            )
            unavailable("Kite 投放区不可用，请检查存储权限")
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

        diagnostics.logDropZoneEvent("dropzone_scan_started", path = recipesDir().absolutePath)
        var imported = 0
        var skipped = 0
        var invalid = 0

        recipeCandidates().forEach { candidate ->
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
            path = recipesDir().absolutePath,
            details = mapOf(
                "imported" to imported.toString(),
                "skipped" to skipped.toString(),
                "invalid" to invalid.toString()
            )
        )

        val message = when {
            imported > 0 && invalid > 0 -> "导入 $imported 个配置，跳过 $invalid 个无效配置"
            imported > 0 -> "导入 $imported 个配置"
            invalid > 0 -> "没有导入新配置，跳过 $invalid 个无效配置"
            skipped > 0 -> "没有发现新配置"
            else -> "没有发现新配置"
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
                    recipeId = json.optString("id"),
                    reason = validationError
                )
                return ImportResult.Invalid
            }

            val recipeId = json.getString("id")
            if (File(privateRecipesDir, "${safeFileName(recipeId)}.json").exists()) {
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
            File(privateImportedDir, "${safeFileName(recipeId)}.json").writeText(enriched.toString(2))
            diagnostics.logDropZoneEvent(
                "dropzone_recipe_imported",
                path = recipeFile.absolutePath,
                recipeId = recipeId,
                details = mapOf("target" to File(privateImportedDir, "${safeFileName(recipeId)}.json").absolutePath)
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
        if (!json.has("schemaVersion")) return "missing_schemaVersion"
        if (json.optString("id").isBlank()) return "missing_id"
        if (json.optString("name").isBlank()) return "missing_name"
        if (!json.has("execution")) return "missing_execution"
        val execution = json.optJSONObject("execution") ?: return "invalid_execution"
        if (execution.optString("mode").isBlank()) return "missing_execution_mode"
        return null
    }

    private fun recipeCandidates(): List<File> {
        val root = recipesDir()
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
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kite")

    private fun recipesDir(): File = File(rootDir(), "recipes")

    private fun importedDir(): File = File(rootDir(), "imported")

    private fun logsDir(): File = File(rootDir(), "logs")

    private fun unavailable(message: String): DropZoneStatus =
        DropZoneStatus(available = false, rootPath = rootDir().absolutePath, recipesPath = recipesDir().absolutePath, message = message)

    private fun safeFileName(input: String): String {
        val normalized = Normalizer.normalize(input.lowercase(Locale.US), Normalizer.Form.NFD)
        return normalized
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.')
            .ifBlank { "recipe" }
    }

    private enum class ImportResult {
        Imported,
        Skipped,
        Invalid
    }

    companion object {
        const val DROPZONE_METADATA = "_kiteDropZone"
    }
}
