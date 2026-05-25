package com.kite.app.diagnostics

import android.content.Context
import android.webkit.ConsoleMessage
import org.json.JSONObject
import java.io.File
import java.time.Instant

class KiteDiagnostics(context: Context) {
    private val diagnosticsDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private val consoleLog = File(diagnosticsDir, "webview-console.log")
    private val errorsLog = File(diagnosticsDir, "webview-errors.jsonl")
    private val capabilitiesFile = File(diagnosticsDir, "webview-capabilities.json")
    private val statusFile = File(diagnosticsDir, "last-webapp-status.json")

    init {
        consoleLog.createNewFile()
        errorsLog.createNewFile()
        capabilitiesFile.createNewFile()
        statusFile.createNewFile()
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
                .put("url", url)
                .put("title", title ?: JSONObject.NULL)
                .put("state", state)
                .put("recipeId", recipeId ?: JSONObject.NULL)
                .put("recipeName", recipeName ?: JSONObject.NULL)
                .put("openSource", openSource ?: JSONObject.NULL)
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
}
