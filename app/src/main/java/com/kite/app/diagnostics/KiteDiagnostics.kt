package com.kite.app.diagnostics

import android.content.Context
import android.webkit.ConsoleMessage
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRunReport
import org.json.JSONObject
import java.io.File
import java.time.Instant

class KiteDiagnostics(context: Context) {
    private val diagnosticsDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private val recipeRunsDir = File(context.filesDir, "recipe-runs").apply { mkdirs() }
    private val consoleLog = File(diagnosticsDir, "webview-console.log")
    private val errorsLog = File(diagnosticsDir, "webview-errors.jsonl")
    private val capabilitiesFile = File(diagnosticsDir, "webview-capabilities.json")
    private val statusFile = File(diagnosticsDir, "last-webapp-status.json")
    private val recipeSaveLog = File(diagnosticsDir, "recipe-save.log")
    private val recipeEventsLog = File(diagnosticsDir, "recipe-events.jsonl")
    private val bridgeEventsLog = File(diagnosticsDir, "bridge-events.jsonl")

    init {
        consoleLog.createNewFile()
        errorsLog.createNewFile()
        capabilitiesFile.createNewFile()
        statusFile.createNewFile()
        recipeSaveLog.createNewFile()
        recipeEventsLog.createNewFile()
        bridgeEventsLog.createNewFile()
    }

    fun logConsole(message: ConsoleMessage) {
        consoleLog.appendText(
            "${Instant.now()} ${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}\n"
        )
    }

    fun logWebError(url: String, code: Int, description: String) {
        errorsLog.appendText(
            JSONObject()
                .put("at", Instant.now().toString())
                .put("url", url)
                .put("code", code)
                .put("description", description)
                .toString() + "\n"
        )
    }

    fun logExternalUrl(url: String) {
        consoleLog.appendText("${Instant.now()} EXTERNAL open_in_system_browser $url\n")
    }

    fun logLocalServer(message: String) {
        consoleLog.appendText("${Instant.now()} LOCAL_SERVER $message\n")
    }

    fun logRecipeSaved(recipe: KiteRecipe) {
        recipeSaveLog.appendText(
            "${Instant.now()} id=${recipe.id} name=${recipe.name} type=${recipe.type} defaultUrl=${recipe.defaultUrl} runtimeSource=${recipe.runtimeSource} icon=${recipe.icon.name} accent=${recipe.card.accent}\n"
        )
        logRecipeEvent("saved", recipe)
    }

    fun logRecipeSaveError(recipe: KiteRecipe, error: Throwable) {
        logRecipeEvent("save_failed", recipe, mapOf("error" to error.message.orEmpty()))
    }

    fun logRecipeAction(recipe: KiteRecipe, action: String, details: Map<String, String> = emptyMap()) {
        logRecipeEvent(action, recipe, details)
    }

    fun logRecipeEvent(event: String, recipe: KiteRecipe?, details: Map<String, String> = emptyMap()) {
        recipeEventsLog.appendText(
            JSONObject()
                .put("at", Instant.now().toString())
                .put("event", event)
                .put("recipeId", recipe?.id ?: JSONObject.NULL)
                .put("recipeName", recipe?.name ?: JSONObject.NULL)
                .put("recipeType", recipe?.type ?: JSONObject.NULL)
                .put("runtimeSource", recipe?.runtimeSource ?: JSONObject.NULL)
                .put("icon", recipe?.icon?.name ?: JSONObject.NULL)
                .put("cardAccent", recipe?.card?.accent ?: JSONObject.NULL)
                .put("cardStatus", recipe?.card?.status ?: JSONObject.NULL)
                .apply { details.forEach { (key, value) -> put(key, value) } }
                .toString() + "\n"
        )
    }

    fun logBridgeEvent(event: String, recipe: KiteRecipe?, details: Map<String, String> = emptyMap()) {
        bridgeEventsLog.appendText(
            JSONObject()
                .put("at", Instant.now().toString())
                .put("event", event)
                .put("recipeId", recipe?.id ?: JSONObject.NULL)
                .put("recipeName", recipe?.name ?: JSONObject.NULL)
                .apply { details.forEach { (key, value) -> put(key, value) } }
                .toString() + "\n"
        )
    }

    fun writeRunReport(report: KiteRunReport): File {
        val fileName = "${report.runId.ifBlank { report.requestId.ifBlank { "run_${Instant.now().toEpochMilli()}" } }}.json"
            .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val target = File(recipeRunsDir, fileName)
        target.writeText(report.toJson().toString(2))
        return target
    }

    fun writeWebAppStatus(
        url: String,
        title: String?,
        state: String,
        recipeId: String? = null,
        recipeName: String? = null,
        openSource: String? = null
    ) {
        statusFile.writeText(
            JSONObject()
                .put("at", Instant.now().toString())
                .put("openedAt", Instant.now().toString())
                .put("url", url)
                .put("title", title ?: JSONObject.NULL)
                .put("state", state)
                .put("recipeId", recipeId ?: JSONObject.NULL)
                .put("recipeName", recipeName ?: JSONObject.NULL)
                .put("openSource", openSource ?: JSONObject.NULL)
                .put("source", openSource ?: JSONObject.NULL)
                .put("openTarget", if (url.contains("127.0.0.1") || url.contains("localhost")) "kite_web_shell" else "system_browser")
                .toString(2)
        )
    }

    fun writeCapabilityReport() {
        capabilitiesFile.writeText(capabilitiesJson())
    }

    fun capabilitiesJson(): String = capabilityReport().toString(2)

    private fun capabilityReport(): JSONObject = JSONObject()
        .put("ok", true)
        .put("webview", true)
        .put("openWeb", true)
        .put("diagnostics", true)
        .put("recipeRead", true)
        .put("shellExecution", false)
        .put("shizuku", false)
        .put("accessibility", false)
        .put("screenshot", false)
        .put("externalUrlPolicy", "open_in_system_browser")
        .put("javascript", true)
        .put("localUrlAllowed", true)
        .put("speechSynthesis", "planned_noop_shim")
        .put("clipboard", "planned_android_bridge")
        .put("filePicker", "planned_android_bridge")
        .put("diagnosticsPath", "files/diagnostics/")
        .put("recipeRunsPath", "files/recipe-runs/")
        .put("recipeProtocolVersion", KiteRecipe.PROTOCOL_VERSION)
}
