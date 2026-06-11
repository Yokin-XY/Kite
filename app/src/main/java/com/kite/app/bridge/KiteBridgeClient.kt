package com.kite.app.bridge

import android.content.Context
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteExpectedResult
import com.kite.app.recipe.KiteMatchResult
import com.kite.app.recipe.KiteNextAction
import com.kite.app.recipe.KiteOutputPolicy
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.KiteRunReport
import com.kite.app.recipe.KiteStepReport
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class KiteBridgeClient(
    private val diagnostics: KiteDiagnostics,
    private val appContext: Context? = null,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val directRuns = ConcurrentHashMap<String, DirectRunBinding>()

    fun runRecipe(
        recipe: KiteRecipe,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null,
        callback: (BridgeResult) -> Unit
    ) {
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
        val context = appContext
        if (context != null) {
            diagnostics.logBridgeEvent(
                "direct_request_sent",
                recipe,
                mapOf("requestId" to requestId, "shellSteps" to shellSteps.size.toString())
            )
            runDirectRecipe(context, recipe, requestId, shellSteps, extraEnv, onProgress, callback)
            return
        }

        val payload = JSONObject()
            .put("protocolVersion", KiteRecipe.PROTOCOL_VERSION)
            .put("requestId", requestId)
            .put("recipe", recipe.toJson(includeLocalIdentity = true))

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
        val context = appContext
        val direct = directRuns[runId]
        if (context != null && direct != null) {
            stopDirectRuns(context, recipe, listOf(direct), callback)
            return
        }
        if (context != null) {
            callback(stoppedWithoutActiveDirectBinding(recipe, runId))
            return
        }

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
        val context = appContext
        val direct = directRuns.values.filter { it.recipeId == recipe.id }
        if (context != null && direct.isNotEmpty()) {
            stopDirectRuns(context, recipe, direct, callback)
            return
        }
        if (context != null) {
            callback(stoppedWithoutActiveDirectBinding(recipe, ""))
            return
        }

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

    private fun runDirectRecipe(
        context: Context,
        recipe: KiteRecipe,
        requestId: String,
        shellSteps: List<KiteRecipeStep>,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null,
        callback: (BridgeResult) -> Unit
    ) {
        thread(name = "KiteDirectBridge", isDaemon = true) {
            val result = runCatching {
                WorkSurfaceRuntimeBridge.ensureBaseImageReady(context)
                WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)

                val runId = "run_${UUID.randomUUID().toString().replace("-", "")}"
                val stepReports = mutableListOf<KiteStepReport>()
                var ok = true
                var detached = false
                var pid: String? = null

                for (step in shellSteps) {
                    val execution = executeDirectShellStep(context, recipe, requestId, step, extraEnv, onProgress)
                    stepReports.add(execution.report)
                    if (!execution.pid.isNullOrBlank()) pid = execution.pid
                    if (execution.detached) {
                        detached = true
                        directRuns[runId] = DirectRunBinding(recipe.id, runId, execution.pid)
                        break
                    }
                    if (!execution.ok) {
                        ok = false
                        break
                    }
                    step.delayAfterMs?.takeIf { it > 0L }?.let { Thread.sleep(it) }
                }

                val nextUrl = if (ok) {
                    recipe.steps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url
                } else {
                    null
                }
                val status = when {
                    !ok -> KiteRunReport.STATUS_FAILED
                    detached -> KiteRunReport.STATUS_RUNNING
                    else -> KiteRunReport.STATUS_FINISHED
                }
                val report = KiteRunReport(
                    protocolVersion = KiteRecipe.PROTOCOL_VERSION,
                    requestId = requestId,
                    runId = runId,
                    recipeId = recipe.id,
                    status = status,
                    ok = ok,
                    pid = pid,
                    steps = stepReports,
                    nextAction = nextUrl?.let { KiteNextAction(KiteRecipe.STEP_OPEN_WEB, it) }
                )
                diagnostics.logParsedRunReport(recipe, report)
                BridgeResult(
                    ok = report.ok,
                    accepted = report.ok || report.status == KiteRunReport.STATUS_RUNNING,
                    status = report.status,
                    message = report.toJson().toString(),
                    requestId = requestId,
                    runReport = report,
                    nextActionUrl = nextUrl
                )
            }.getOrElse { error ->
                BridgeResult(
                    ok = false,
                    accepted = false,
                    status = KiteRunReport.STATUS_BRIDGE_UNAVAILABLE,
                    message = error.message ?: error.javaClass.simpleName,
                    requestId = requestId,
                    errorType = BridgeErrorType.ConnectionError
                )
            }
            diagnostics.logBridgeEvent(
                event = if (result.ok || result.accepted) "direct_response_ok" else "direct_response_failed",
                recipe = recipe,
                details = mapOf(
                    "requestId" to result.requestId.orEmpty(),
                    "status" to result.status,
                    "errorType" to result.errorType.name,
                    "message" to result.message.take(500)
                )
            )
            callback(result)
        }
    }

    private fun executeDirectShellStep(
        context: Context,
        recipe: KiteRecipe,
        requestId: String,
        step: KiteRecipeStep,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null
    ): DirectStepExecution {
        val runMode = KiteRecipe.normalizeRunMode(step.runMode) ?: KiteRecipe.RUN_MODE_ATTACHED
        return if (runMode == KiteRecipe.RUN_MODE_DETACHED) {
            executeDetachedShellStep(context, recipe, requestId, step, extraEnv, onProgress)
        } else {
            executeAttachedShellStep(context, recipe, requestId, step, extraEnv, onProgress)
        }
    }

    private fun executeAttachedShellStep(
        context: Context,
        recipe: KiteRecipe,
        requestId: String,
        step: KiteRecipeStep,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null
    ): DirectStepExecution {
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = step.workdir?.trim().orEmpty().ifBlank { DEFAULT_WORKDIR },
            payload = step.cmd.orEmpty(),
            loginShell = true
        )
        val timeoutMs = step.timeoutMs?.takeIf { it > 0L } ?: DEFAULT_ATTACHED_TIMEOUT_MS
        val process = executeProcess(config.command, config.env + extraEnv, timeoutMs) { output, chunk ->
            onProgress?.invoke(
                BridgeProgress(
                    requestId = requestId,
                    recipeId = recipe.id,
                    stepId = step.id,
                    command = step.cmd.orEmpty(),
                    outputTail = output.takeLast(OUTPUT_TAIL_CHARS),
                    lastChunk = chunk,
                    lastMeaningfulOutput = lastMeaningfulLine(output)
                )
            )
        }
        val output = process.output
        val meaningful = lastMeaningfulLine(output)
        val expected = step.expected ?: recipe.expected
        val match = matchExpected(expected, output, meaningful)
        val success = !process.timedOut && process.exitCode == 0 && (match?.matched != false)
        val status = if (success) KiteRunReport.STATUS_FINISHED else KiteRunReport.STATUS_FAILED
        return DirectStepExecution(
            ok = success,
            detached = false,
            pid = null,
            report = KiteStepReport(
                stepId = step.id,
                type = step.type,
                status = status,
                exitCode = process.exitCode,
                lastMeaningfulOutput = meaningful,
                stdoutTail = output.takeLast(OUTPUT_TAIL_CHARS),
                stderrTail = if (process.timedOut) "timeout" else "",
                matchResult = match
            )
        )
    }

    private fun executeDetachedShellStep(
        context: Context,
        recipe: KiteRecipe,
        requestId: String,
        step: KiteRecipeStep,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null
    ): DirectStepExecution {
        val logPath = "/tmp/kite-${safeId(recipe.id)}-${safeId(step.id)}.log"
        val payload = "mkdir -p /tmp && nohup bash -lc ${shellQuote(step.cmd.orEmpty())} > ${shellQuote(logPath)} 2>&1 < /dev/null & echo pid:$!"
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = step.workdir?.trim().orEmpty().ifBlank { DEFAULT_WORKDIR },
            payload = payload,
            loginShell = true
        )
        val process = executeProcess(config.command, config.env + extraEnv, DETACHED_START_TIMEOUT_MS) { output, chunk ->
            onProgress?.invoke(
                BridgeProgress(
                    requestId = requestId,
                    recipeId = recipe.id,
                    stepId = step.id,
                    command = step.cmd.orEmpty(),
                    outputTail = output.takeLast(OUTPUT_TAIL_CHARS),
                    lastChunk = chunk,
                    lastMeaningfulOutput = lastMeaningfulLine(output)
                )
            )
        }
        val output = process.output
        val pid = extractPid(output)
        val success = !process.timedOut && process.exitCode == 0 && !pid.isNullOrBlank()
        return DirectStepExecution(
            ok = success,
            detached = success,
            pid = pid,
            report = KiteStepReport(
                stepId = step.id,
                type = step.type,
                status = if (success) KiteRunReport.STATUS_RUNNING else KiteRunReport.STATUS_FAILED,
                exitCode = process.exitCode,
                lastMeaningfulOutput = lastMeaningfulLine(output).ifBlank { logPath },
                stdoutTail = output.takeLast(OUTPUT_TAIL_CHARS),
                stderrTail = if (process.timedOut) "timeout" else "",
                matchResult = null
            )
        )
    }

    private fun stopDirectRuns(
        context: Context,
        recipe: KiteRecipe,
        bindings: List<DirectRunBinding>,
        callback: (BridgeResult) -> Unit
    ) {
        val requestId = newRequestId()
        thread(name = "KiteDirectBridgeStop", isDaemon = true) {
            val runId = bindings.firstOrNull()?.runId ?: requestId
            val output = StringBuilder()
            bindings.forEach { binding ->
                val pid = binding.pid
                if (!pid.isNullOrBlank()) {
                    val payload = "kill -TERM $pid >/dev/null 2>&1 || true; sleep 1; kill -0 $pid >/dev/null 2>&1 && kill -KILL $pid >/dev/null 2>&1 || true"
                    val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
                        context = context,
                        workingDirectory = DEFAULT_WORKDIR,
                        payload = payload,
                        loginShell = true
                    )
                    output.append(executeProcess(config.command, config.env, DIRECT_STOP_TIMEOUT_MS).output)
                }
                directRuns.remove(binding.runId)
            }
            val report = KiteRunReport(
                protocolVersion = KiteRecipe.PROTOCOL_VERSION,
                requestId = requestId,
                runId = runId,
                recipeId = recipe.id,
                status = KiteRunReport.STATUS_STOPPED,
                ok = true,
                steps = listOf(
                    KiteStepReport(
                        stepId = "direct_stop",
                        type = KiteRecipe.STEP_SHELL,
                        status = KiteRunReport.STATUS_STOPPED,
                        exitCode = 0,
                        lastMeaningfulOutput = output.toString().trim().takeLast(OUTPUT_TAIL_CHARS)
                    )
                )
            )
            callback(
                BridgeResult(
                    ok = true,
                    accepted = true,
                    status = KiteRunReport.STATUS_STOPPED,
                    message = report.toJson().toString(),
                    requestId = requestId,
                    runReport = report
                )
            )
        }
    }

    private fun stoppedWithoutActiveDirectBinding(recipe: KiteRecipe, runId: String): BridgeResult {
        val requestId = newRequestId()
        val resolvedRunId = runId.ifBlank { requestId }
        val report = KiteRunReport(
            protocolVersion = KiteRecipe.PROTOCOL_VERSION,
            requestId = requestId,
            runId = resolvedRunId,
            recipeId = recipe.id,
            status = KiteRunReport.STATUS_STOPPED,
            ok = true,
            steps = listOf(
                KiteStepReport(
                    stepId = "local_stop",
                    type = KiteRecipe.STEP_SHELL,
                    status = KiteRunReport.STATUS_STOPPED,
                    exitCode = 0,
                    lastMeaningfulOutput = "没有发现仍在运行的后台进程，已关闭卡片实例。"
                )
            )
        )
        diagnostics.logParsedRunReport(recipe, report)
        return BridgeResult(
            ok = true,
            accepted = true,
            status = KiteRunReport.STATUS_STOPPED,
            message = report.toJson().toString(),
            requestId = requestId,
            runReport = report
        )
    }

    private fun executeProcess(
        command: List<String>,
        env: Map<String, String>,
        timeoutMs: Long,
        onOutput: ((output: String, chunk: String) -> Unit)? = null
    ): DirectProcessResult {
        val output = StringBuilder()
        val outputLock = Any()
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()
        val reader = thread(start = true, isDaemon = true, name = "KiteDirectReader") {
            runCatching {
                val buffer = ByteArray(1024)
                while (true) {
                    val read = process.inputStream.read(buffer)
                    if (read <= 0) break
                    val chunk = String(buffer, 0, read)
                    val snapshot = synchronized(outputLock) {
                        output.append(chunk)
                        if (output.length > OUTPUT_CAPTURE_CHARS) {
                            output.delete(0, output.length - OUTPUT_CAPTURE_CHARS)
                        }
                        output.toString()
                    }
                    onOutput?.invoke(snapshot, chunk)
                }
            }
        }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        reader.join(1500L)
        val finalOutput = synchronized(outputLock) { output.toString() }
        return DirectProcessResult(
            exitCode = if (finished) process.exitValue() else -1,
            timedOut = !finished,
            output = finalOutput
        )
    }

    private fun matchExpected(
        expected: KiteExpectedResult?,
        output: String,
        meaningful: String
    ): KiteMatchResult? {
        val text = expected?.text?.takeIf { it.isNotBlank() } ?: return null
        val source = if (expected.source == KiteRecipe.OUTPUT_LAST_MEANINGFUL) meaningful else output
        val matched = when (expected.mode.trim().lowercase()) {
            "equals", "exact" -> source.trim() == text.trim()
            "regex" -> runCatching { Regex(text).containsMatchIn(source) }.getOrDefault(false)
            else -> source.contains(text)
        }
        return KiteMatchResult(
            enabled = true,
            matched = matched,
            mode = expected.mode,
            text = text,
            source = expected.source
        )
    }

    private fun lastMeaningfulLine(output: String): String =
        output.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.lastOrNull().orEmpty()

    private fun extractPid(text: String): String? {
        val match = Regex("""pid\s*[:=]\s*(\d+)|pid\s+(\d+)""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
    }

    private fun safeId(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9_.-]"), "_").ifBlank { "recipe" }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun newRequestId(): String = "req_${UUID.randomUUID().toString().replace("-", "")}"

    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8799"
        private const val TIMEOUT_MS = 5000
        private const val DEFAULT_WORKDIR = "/workspace"
        private const val DEFAULT_ATTACHED_TIMEOUT_MS = 600_000L
        private const val DETACHED_START_TIMEOUT_MS = 15_000L
        private const val DIRECT_STOP_TIMEOUT_MS = 8_000L
        private const val OUTPUT_CAPTURE_CHARS = 16_000
        private const val OUTPUT_TAIL_CHARS = 4_000
    }
}

private data class DirectRunBinding(
    val recipeId: String,
    val runId: String,
    val pid: String?
)

private data class DirectProcessResult(
    val exitCode: Int,
    val timedOut: Boolean,
    val output: String
)

private data class DirectStepExecution(
    val ok: Boolean,
    val detached: Boolean,
    val pid: String?,
    val report: KiteStepReport
)

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

data class BridgeProgress(
    val requestId: String,
    val recipeId: String,
    val stepId: String,
    val command: String,
    val outputTail: String,
    val lastChunk: String,
    val lastMeaningfulOutput: String
)
