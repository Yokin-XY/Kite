package com.kite.app.bridge

import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteOutputPolicy
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRunReport
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

class KiteBridgeClient(
    private val diagnostics: KiteDiagnostics,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    fun runRecipe(recipe: KiteRecipe, callback: (BridgeResult) -> Unit) {
        val shellSteps = recipe.steps.filter { it.type == KiteRecipe.STEP_SHELL && !it.cmd.isNullOrBlank() }
        if (shellSteps.isEmpty()) {
            callback(
                BridgeResult(
                    ok = true,
                    accepted = true,
                    status = "no_shell_step",
                    message = "no_shell_step"
                )
            )
            return
        }

        val requestId = newRequestId()
        val payload = JSONObject()
            .put("protocolVersion", KiteRecipe.PROTOCOL_VERSION)
            .put("requestId", requestId)
            .put("recipe", recipe.toJson())

        diagnostics.logBridgeEvent(
            "request_sent",
            recipe,
            mapOf("endpoint" to "$baseUrl/run-recipe", "requestId" to requestId)
        )
        postJsonAsync("/run-recipe", payload, recipe, requestId, callback)
    }

    fun runCommand(recipe: KiteRecipe, command: String, callback: (BridgeResult) -> Unit) {
        val requestId = newRequestId()
        val payload = JSONObject()
            .put("protocolVersion", KiteRecipe.PROTOCOL_VERSION)
            .put("requestId", requestId)
            .put("cmd", command)
            .put("outputPolicy", KiteOutputPolicy().toJson())
        diagnostics.logBridgeEvent(
            "command_request_sent",
            recipe,
            mapOf("endpoint" to "$baseUrl/run-command", "requestId" to requestId)
        )
        postJsonAsync("/run-command", payload, recipe, requestId, callback)
    }

    fun stopRun(recipe: KiteRecipe, runId: String, callback: (BridgeResult) -> Unit) {
        val requestId = newRequestId()
        val payload = JSONObject()
            .put("protocolVersion", KiteRecipe.PROTOCOL_VERSION)
            .put("requestId", requestId)
            .put("recipeId", recipe.id)
            .put("runId", runId)
        diagnostics.logBridgeEvent(
            "stop_run_request_sent",
            recipe,
            mapOf("endpoint" to "$baseUrl/stop-run", "requestId" to requestId, "runId" to runId)
        )
        postJsonAsync("/stop-run", payload, recipe, requestId, callback)
    }

    fun stopRecipe(recipe: KiteRecipe, callback: (BridgeResult) -> Unit) {
        val requestId = newRequestId()
        val payload = JSONObject()
            .put("protocolVersion", KiteRecipe.PROTOCOL_VERSION)
            .put("requestId", requestId)
            .put("recipeId", recipe.id)
        diagnostics.logBridgeEvent(
            "stop_recipe_request_sent",
            recipe,
            mapOf("endpoint" to "$baseUrl/stop-recipe", "requestId" to requestId)
        )
        postJsonAsync("/stop-recipe", payload, recipe, requestId, callback)
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
                callback(
                    BridgeResult(
                        ok = code in 200..299,
                        accepted = code in 200..299,
                        status = if (code in 200..299) "ready" else "failed",
                        message = body,
                    )
                )
            }.onFailure {
                callback(
                    BridgeResult(
                        ok = false,
                        accepted = false,
                        status = KiteRunReport.STATUS_BRIDGE_UNAVAILABLE,
                        message = it.message ?: KiteRunReport.STATUS_BRIDGE_UNAVAILABLE
                    )
                )
            }
        }
    }

    private fun postJsonAsync(
        path: String,
        payload: JSONObject,
        recipe: KiteRecipe,
        requestId: String,
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
                diagnostics.logBridgeRawResponse(recipe, requestId, code, body)
                if (path.startsWith("/stop")) {
                    diagnostics.logBridgeEvent(
                        "stop_response_raw",
                        recipe,
                        mapOf(
                            "requestId" to requestId,
                            "httpCode" to code.toString(),
                            "rawBody" to body.take(2000)
                        )
                    )
                }
                val report = KiteRunReport.fromJsonOrNull(body)
                if (report != null) {
                    diagnostics.logParsedRunReport(recipe, report)
                    BridgeResult(
                        ok = code in 200..299 && report.ok,
                        accepted = report.status in setOf(
                            KiteRunReport.STATUS_ACCEPTED,
                            KiteRunReport.STATUS_RUNNING,
                            KiteRunReport.STATUS_ALREADY_RUNNING,
                            KiteRunReport.STATUS_STOPPED,
                            KiteRunReport.STATUS_FINISHED
                        ),
                        status = report.status,
                        message = body.ifBlank { "http_$code" },
                        requestId = report.requestId.ifBlank { requestId },
                        runReport = report,
                        nextActionUrl = report.openWebUrlIfPresent()
                    )
                } else {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val expectedJson = body.trimStart().startsWith("{") || body.trimStart().startsWith("[")
                    if (expectedJson && json == null) {
                        return@runCatching BridgeResult(
                            ok = false,
                            accepted = false,
                            status = "parse_error",
                            message = body.ifBlank { "parse_error" },
                            requestId = requestId,
                            errorType = BridgeErrorType.ParseError,
                            rawBody = body
                        )
                    }
                    val simpleStatus = json?.optString("status").orEmpty().ifBlank {
                        if (code in 200..299) "accepted" else "failed"
                    }
                    val simpleOk = json?.optBoolean("ok", code in 200..299) ?: (code in 200..299)
                    BridgeResult(
                        ok = simpleOk && code in 200..299,
                        accepted = code in 200..299 && (simpleOk || body.contains("accepted", ignoreCase = true)),
                        status = simpleStatus,
                        message = body.ifBlank { "http_$code" },
                        requestId = requestId,
                        errorType = when (code) {
                            404, 405 -> BridgeErrorType.UnsupportedEndpoint
                            in 200..299 -> BridgeErrorType.None
                            else -> BridgeErrorType.BridgeFailed
                        },
                        rawBody = body
                    )
                }
            }.getOrElse {
                val errorType = when (it) {
                    is SocketTimeoutException -> BridgeErrorType.Timeout
                    is ConnectException -> BridgeErrorType.ConnectionError
                    else -> BridgeErrorType.ConnectionError
                }
                BridgeResult(
                    ok = false,
                    accepted = false,
                    status = KiteRunReport.STATUS_BRIDGE_UNAVAILABLE,
                    message = it.message ?: KiteRunReport.STATUS_BRIDGE_UNAVAILABLE,
                    requestId = requestId,
                    errorType = errorType
                )
            }

            diagnostics.logBridgeEvent(
                event = if (result.ok || result.accepted) "response_ok" else "response_failed",
                recipe = recipe,
                details = mapOf(
                    "requestId" to result.requestId.orEmpty(),
                    "runId" to (result.runReport?.runId ?: ""),
                    "pid" to (result.runReport?.pid ?: ""),
                    "status" to result.status,
                    "errorType" to result.errorType.name,
                    "nextAction" to result.nextActionUrl.orEmpty(),
                    "message" to result.message.take(500)
                )
            )
            callback(result)
        }
    }

    private fun newRequestId(): String = "req_${UUID.randomUUID().toString().replace("-", "")}"

    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8799"
        private const val TIMEOUT_MS = 5000
    }
}

enum class BridgeErrorType {
    None,
    Timeout,
    ConnectionError,
    UnsupportedEndpoint,
    ParseError,
    BridgeFailed
}

data class BridgeResult(
    val ok: Boolean,
    val accepted: Boolean,
    val status: String,
    val message: String,
    val requestId: String? = null,
    val runReport: KiteRunReport? = null,
    val nextActionUrl: String? = null,
    val errorType: BridgeErrorType = BridgeErrorType.None,
    val rawBody: String = ""
)
