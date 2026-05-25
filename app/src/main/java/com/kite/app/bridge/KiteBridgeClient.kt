package com.kite.app.bridge

import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class KiteBridgeClient(
    private val diagnostics: KiteDiagnostics,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    fun runRecipe(recipe: KiteRecipe, callback: (BridgeResult) -> Unit) {
        val shellSteps = recipe.steps.filter { it.type == KiteRecipe.STEP_SHELL && !it.cmd.isNullOrBlank() }
        if (shellSteps.isEmpty()) {
            callback(BridgeResult(ok = true, accepted = true, message = "no_shell_step"))
            return
        }

        val payload = JSONObject()
            .put("recipeId", recipe.id)
            .put("recipeName", recipe.name)
            .put("steps", JSONArray().apply {
                shellSteps.forEach { put(it.toJson()) }
            })

        diagnostics.logBridgeEvent("request_sent", recipe, mapOf("endpoint" to "$baseUrl/run-recipe"))
        postJsonAsync("/run-recipe", payload, recipe, callback)
    }

    fun runCommand(recipe: KiteRecipe, command: String, callback: (BridgeResult) -> Unit) {
        val payload = JSONObject()
            .put("recipeId", recipe.id)
            .put("recipeName", recipe.name)
            .put("cmd", command)
        diagnostics.logBridgeEvent("command_request_sent", recipe, mapOf("endpoint" to "$baseUrl/run-command"))
        postJsonAsync("/run-command", payload, recipe, callback)
    }

    fun checkStatus(callback: (BridgeResult) -> Unit) {
        thread(name = "KiteBridgeStatus", isDaemon = true) {
            runCatching {
                val connection = URL("$baseUrl/status").openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.requestMethod = "GET"
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                callback(BridgeResult(ok = code in 200..299, accepted = code in 200..299, message = body))
            }.onFailure {
                callback(BridgeResult(ok = false, accepted = false, message = it.message ?: "bridge_unavailable"))
            }
        }
    }

    private fun postJsonAsync(
        path: String,
        payload: JSONObject,
        recipe: KiteRecipe,
        callback: (BridgeResult) -> Unit
    ) {
        thread(name = "KiteBridgeClient", isDaemon = true) {
            val result = runCatching {
                val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                BridgeResult(
                    ok = code in 200..299,
                    accepted = code in 200..299 && body.contains("accepted", ignoreCase = true),
                    message = body.ifBlank { "http_$code" }
                )
            }.getOrElse {
                BridgeResult(ok = false, accepted = false, message = it.message ?: "bridge_unavailable")
            }

            diagnostics.logBridgeEvent(
                event = if (result.ok) "response_ok" else "response_failed",
                recipe = recipe,
                details = mapOf("message" to result.message.take(500))
            )
            callback(result)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8799"
        private const val TIMEOUT_MS = 1200
    }
}

data class BridgeResult(
    val ok: Boolean,
    val accepted: Boolean,
    val message: String
)
