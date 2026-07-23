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
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallOutput
import com.kite.app.foundation.runtime.ProotOwnerProcessTerminator
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class KiteBridgeClient(
    private val diagnostics: KiteDiagnostics,
    private val appContext: Context? = null,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val stopExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KiteBridgeStop").apply { isDaemon = true }
    }
) {
    private val directRuns = ConcurrentHashMap<String, DirectRunBinding>()
    private val directProcesses = ConcurrentHashMap<String, DirectProcessBinding>()

    fun cleanCardRunPidDirs(cardInstanceIds: Collection<String>) {
        val context = appContext ?: return
        val ids = cardInstanceIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (ids.isEmpty()) return
        thread(name = "KiteCardRunPidDirCleanup", isDaemon = true) {
            ids.forEach { cardInstanceId ->
                runCatching {
                    val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
                        context = context,
                        workingDirectory = DEFAULT_WORKDIR,
                        payload = cleanCardRunPidDirPayload(cardInstanceId),
                        loginShell = true
                    )
                    executeProcess(config.command, config.env, DIRECT_STOP_TIMEOUT_MS)
                }
            }
        }
    }

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
        stopRun(
            recipe = recipe,
            runId = runId,
            pid = null,
            rootPid = null,
            processGroupId = null,
            systemSessionId = null,
            cardInstanceId = null,
            runtimeOwnerIds = emptyList(),
            callback = callback
        )
    }

    fun stopRun(
        recipe: KiteRecipe,
        runId: String,
        pid: String? = null,
        rootPid: String? = null,
        processGroupId: String? = null,
        systemSessionId: String? = null,
        cardInstanceId: String? = null,
        runtimeOwnerIds: Collection<String> = emptyList(),
        callback: (BridgeResult) -> Unit
    ) {
        val context = appContext
        val direct = directRuns[runId]
        if (context != null && direct != null) {
            stopDirectRuns(context, recipe, listOf(direct), runtimeOwnerIds, callback)
            return
        }
        val directProcess = directProcesses[runId]
        if (context != null && directProcess != null) {
            stopDirectProcesses(context, recipe, listOf(directProcess), cardInstanceId, runtimeOwnerIds, callback)
            return
        }
        if (context != null) {
            val persisted = DirectRunBinding(
                recipeId = recipe.id,
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                cardInstanceId = cardInstanceId,
                runtimeOwnerId = runtimeOwnerIds.firstOrNull { it.isNotBlank() },
                pidFilePath = cardInstanceId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { cardRunPidFilePath(it, runId) }
            ).takeIf { it.hasProcessBinding() }
            if (persisted != null) {
                stopDirectRuns(context, recipe, listOf(persisted), runtimeOwnerIds, callback)
                return
            }
            stopExecutor.execute {
                callback(stoppedWithoutActiveDirectBinding(context, recipe, runId, cardInstanceId, runtimeOwnerIds))
            }
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
            stopDirectRuns(context, recipe, direct, emptyList(), callback)
            return
        }
        val directProcess = directProcesses.values.filter { it.recipeId == recipe.id }
        if (context != null && directProcess.isNotEmpty()) {
            stopDirectProcesses(context, recipe, directProcess, null, emptyList(), callback)
            return
        }
        if (context != null) {
            stopExecutor.execute {
                callback(stoppedWithoutActiveDirectBinding(context, recipe, "", null, emptyList()))
            }
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
                var rootPid: String? = null
                var processGroupId: String? = null
                var systemSessionId: String? = null
                val runEnv = directRuntimeEnv(recipe, runId, extraEnv)

                for (step in shellSteps) {
                    val execution = executeDirectShellStep(context, recipe, runId, requestId, step, runEnv, onProgress)
                    stepReports.add(execution.report)
                    if (!execution.pid.isNullOrBlank()) pid = execution.pid
                    if (!execution.rootPid.isNullOrBlank()) rootPid = execution.rootPid
                    if (!execution.processGroupId.isNullOrBlank()) processGroupId = execution.processGroupId
                    if (!execution.systemSessionId.isNullOrBlank()) systemSessionId = execution.systemSessionId
                    if (execution.detached) {
                        detached = true
                        directRuns[runId] = DirectRunBinding(
                            recipeId = recipe.id,
                            runId = runId,
                            pid = execution.pid,
                            rootPid = execution.rootPid,
                            processGroupId = execution.processGroupId,
                            systemSessionId = execution.systemSessionId,
                            cardInstanceId = execution.cardInstanceId,
                            runtimeOwnerId = execution.runtimeOwnerId,
                            pidFilePath = execution.pidFilePath
                        )
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
                    rootPid = rootPid,
                    processGroupId = processGroupId,
                    systemSessionId = systemSessionId,
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
        runId: String,
        requestId: String,
        step: KiteRecipeStep,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null
    ): DirectStepExecution {
        val runMode = KiteRecipe.normalizeRunMode(step.runMode) ?: KiteRecipe.RUN_MODE_ATTACHED
        return if (runMode == KiteRecipe.RUN_MODE_DETACHED) {
            executeDetachedShellStep(context, recipe, runId, requestId, step, extraEnv, onProgress)
        } else {
            executeAttachedShellStep(context, recipe, runId, requestId, step, extraEnv, onProgress)
        }
    }

    fun stopProcessBinding(
        recipe: KiteRecipe,
        runId: String,
        pid: String?,
        rootPid: String?,
        processGroupId: String?,
        systemSessionId: String?,
        cardInstanceId: String? = null,
        runtimeOwnerIds: Collection<String> = emptyList(),
        callback: (BridgeResult) -> Unit
    ) {
        stopRun(
            recipe = recipe,
            runId = runId.ifBlank { newRequestId() },
            pid = pid,
            rootPid = rootPid,
            processGroupId = processGroupId,
            systemSessionId = systemSessionId,
            cardInstanceId = cardInstanceId,
            runtimeOwnerIds = runtimeOwnerIds,
            callback = callback
        )
    }

    private fun executeAttachedShellStep(
        context: Context,
        recipe: KiteRecipe,
        runId: String,
        requestId: String,
        step: KiteRecipeStep,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null
    ): DirectStepExecution {
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = step.workdir?.trim().orEmpty().ifBlank { DEFAULT_WORKDIR },
            payload = groupedAttachedPayload(step.cmd.orEmpty()),
            loginShell = true
        )
        val timeoutMs = step.timeoutMs?.takeIf { it > 0L } ?: DEFAULT_ATTACHED_TIMEOUT_MS
        val process = executeProcess(
            command = config.command,
            env = config.env + extraEnv,
            timeoutMs = timeoutMs,
            activeRecipeId = recipe.id,
            activeRunId = runId,
            runtimeOwnerId = extraEnv["KF_RUNTIME_ID"]
        ) { output, chunk ->
            updateDirectProcessBinding(runId, output)
            val meta = extractRunBindingMeta(output)
            onProgress?.invoke(
                BridgeProgress(
                    requestId = requestId,
                    runId = runId,
                    recipeId = recipe.id,
                    stepId = step.id,
                    command = step.cmd.orEmpty(),
                    outputTail = output.takeLast(OUTPUT_TAIL_CHARS),
                    lastChunk = chunk,
                    lastMeaningfulOutput = lastMeaningfulLine(output),
                    pid = meta.rootPid,
                    rootPid = meta.rootPid,
                    processGroupId = meta.processGroupId,
                    systemSessionId = meta.systemSessionId
                )
            )
        }
        val output = process.output
        updateDirectProcessBinding(runId, output)
        val meaningful = lastMeaningfulLine(output)
        val expected = step.expected ?: recipe.expected
        val match = matchExpected(expected, output, meaningful)
        val success = !process.timedOut && process.exitCode == 0 && (match?.matched != false)
        val status = if (success) KiteRunReport.STATUS_FINISHED else KiteRunReport.STATUS_FAILED
        val runMeta = extractRunBindingMeta(output)
        return DirectStepExecution(
            ok = success,
            detached = false,
            pid = null,
            rootPid = runMeta.rootPid,
            processGroupId = runMeta.processGroupId,
            systemSessionId = runMeta.systemSessionId,
            runtimeOwnerId = extraEnv["KF_RUNTIME_ID"],
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
        runId: String,
        requestId: String,
        step: KiteRecipeStep,
        extraEnv: Map<String, String> = emptyMap(),
        onProgress: ((BridgeProgress) -> Unit)? = null
    ): DirectStepExecution {
        val logPath = "/tmp/kite-${safeId(recipe.id)}-${safeId(step.id)}.log"
        val cardInstanceId = cardInstanceIdFrom(extraEnv, recipe)
        val pidFilePath = cardRunPidFilePath(cardInstanceId, runId)
        val launchPayload = runnerAwareLaunchPayload(step.cmd.orEmpty())
        val payload = detachedLaunchPayload(
            launchPayload = launchPayload,
            logPath = logPath,
            pidFilePath = pidFilePath,
            cardInstanceId = cardInstanceId,
            runId = runId
        )
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = step.workdir?.trim().orEmpty().ifBlank { DEFAULT_WORKDIR },
            payload = payload,
            loginShell = true
        )
        val process = executeProcess(config.command, config.env + extraEnv, DETACHED_START_TIMEOUT_MS) { output, chunk ->
            val meta = extractRunBindingMeta(output)
            onProgress?.invoke(
                BridgeProgress(
                    requestId = requestId,
                    runId = runId,
                    recipeId = recipe.id,
                    stepId = step.id,
                    command = step.cmd.orEmpty(),
                    outputTail = output.takeLast(OUTPUT_TAIL_CHARS),
                    lastChunk = chunk,
                    lastMeaningfulOutput = lastMeaningfulLine(output),
                    pid = meta.rootPid ?: extractPid(output),
                    rootPid = meta.rootPid ?: extractPid(output),
                    processGroupId = meta.processGroupId ?: extractPid(output),
                    systemSessionId = meta.systemSessionId ?: extractPid(output)
                )
            )
        }
        val output = process.output
        val runMeta = extractRunBindingMeta(output)
        val pid = runMeta.rootPid ?: extractPid(output)
        val success = detachedStartAccepted(process.timedOut, process.exitCode, pid)
        return DirectStepExecution(
            ok = success,
            detached = success,
            pid = pid,
            rootPid = runMeta.rootPid ?: pid,
            processGroupId = runMeta.processGroupId ?: pid,
            systemSessionId = runMeta.systemSessionId ?: pid,
            cardInstanceId = cardInstanceId,
            runtimeOwnerId = extraEnv["KF_RUNTIME_ID"],
            pidFilePath = pidFilePath,
            report = KiteStepReport(
                stepId = step.id,
                type = step.type,
                status = if (success) KiteRunReport.STATUS_RUNNING else KiteRunReport.STATUS_FAILED,
                exitCode = process.exitCode,
                lastMeaningfulOutput = lastMeaningfulLine(output).ifBlank { logPath },
                stdoutTail = output.takeLast(OUTPUT_TAIL_CHARS),
                stderrTail = if (process.timedOut && !success) "timeout" else "",
                matchResult = null
            )
        )
    }

    private fun stopDirectRuns(
        context: Context,
        recipe: KiteRecipe,
        bindings: List<DirectRunBinding>,
        ownerIdHints: Collection<String>,
        callback: (BridgeResult) -> Unit
    ) {
        val requestId = newRequestId()
        stopExecutor.execute {
            val runId = bindings.firstOrNull()?.runId ?: requestId
            val output = StringBuilder()
            val ownerIds = ownerIdHints + bindings.mapNotNull { it.runtimeOwnerId }
            val ownerOutput = stopOwnerProcesses(
                context = context,
                recipe = recipe,
                cardInstanceIds = bindings.map { it.cardInstanceId },
                runtimeOwnerIds = ownerIds,
            )
            val stoppedOk = OwnerStopOutputEvidence.isConfirmed(ownerOutput)
            output.append(ownerOutput)
            if (stoppedOk) {
                bindings.forEach { binding ->
                    directRuns.remove(binding.runId)
                    output.append(cleanCardRunPidFile(context, binding.pidFilePath))
                }
            }
            val status = if (stoppedOk) KiteRunReport.STATUS_STOPPED else KiteRunReport.STATUS_FAILED
            val report = KiteRunReport(
                protocolVersion = KiteRecipe.PROTOCOL_VERSION,
                requestId = requestId,
                runId = runId,
                recipeId = recipe.id,
                status = status,
                ok = stoppedOk,
                steps = listOf(
                    KiteStepReport(
                        stepId = "direct_stop",
                        type = KiteRecipe.STEP_SHELL,
                        status = status,
                        exitCode = if (stoppedOk) 0 else 1,
                        lastMeaningfulOutput = output.toString().trim().takeLast(OUTPUT_TAIL_CHARS)
                    )
                )
            )
            callback(
                BridgeResult(
                    ok = stoppedOk,
                    accepted = stoppedOk,
                    status = status,
                    message = report.toJson().toString(),
                    requestId = requestId,
                    runReport = report,
                    errorType = if (stoppedOk) BridgeErrorType.None else BridgeErrorType.BridgeFailed
                )
            )
        }
    }

    private fun stopDirectProcesses(
        context: Context,
        recipe: KiteRecipe,
        bindings: List<DirectProcessBinding>,
        cardInstanceIdHint: String?,
        ownerIdHints: Collection<String>,
        callback: (BridgeResult) -> Unit
    ) {
        val requestId = newRequestId()
        stopExecutor.execute {
            val runId = bindings.firstOrNull()?.runId ?: requestId
            val output = StringBuilder()
            val ownerIds = ownerIdHints + bindings.mapNotNull { it.runtimeOwnerId }
            val ownerOutput = stopOwnerProcesses(
                context = context,
                recipe = recipe,
                cardInstanceIds = listOf(cardInstanceIdHint),
                runtimeOwnerIds = ownerIds,
            )
            val stoppedOk = OwnerStopOutputEvidence.isConfirmed(ownerOutput)
            if (stoppedOk) {
                bindings.forEach { binding ->
                    runCatching {
                        if (binding.process.isAlive) {
                            binding.process.destroy()
                            if (!binding.process.waitFor(300L, TimeUnit.MILLISECONDS)) {
                                binding.process.destroyForcibly()
                            }
                        }
                    }
                    directProcesses.remove(binding.runId)
                }
            }
            output.append(ownerOutput)
            val status = if (stoppedOk) KiteRunReport.STATUS_STOPPED else KiteRunReport.STATUS_FAILED
            val report = KiteRunReport(
                protocolVersion = KiteRecipe.PROTOCOL_VERSION,
                requestId = requestId,
                runId = runId,
                recipeId = recipe.id,
                status = status,
                ok = stoppedOk,
                steps = listOf(
                    KiteStepReport(
                        stepId = "direct_process_stop",
                        type = KiteRecipe.STEP_SHELL,
                        status = status,
                        exitCode = if (stoppedOk) 0 else 1,
                        lastMeaningfulOutput = output.toString().trim().ifBlank { "已中断正在执行的 SH 命令。" }.takeLast(OUTPUT_TAIL_CHARS)
                    )
                )
            )
            callback(
                BridgeResult(
                    ok = stoppedOk,
                    accepted = stoppedOk,
                    status = status,
                    message = report.toJson().toString(),
                    requestId = requestId,
                    runReport = report,
                    errorType = if (stoppedOk) BridgeErrorType.None else BridgeErrorType.BridgeFailed
                )
            )
        }
    }

    private fun stoppedWithoutActiveDirectBinding(
        context: Context,
        recipe: KiteRecipe,
        runId: String,
        cardInstanceId: String?,
        runtimeOwnerIds: Collection<String>
    ): BridgeResult {
        val requestId = newRequestId()
        val resolvedRunId = runId.ifBlank { requestId }
        val ownerStopOutput = stopOwnerProcesses(context, recipe, listOf(cardInstanceId), runtimeOwnerIds)
        val stoppedOk = OwnerStopOutputEvidence.isConfirmed(ownerStopOutput)
        val status = if (stoppedOk) KiteRunReport.STATUS_STOPPED else KiteRunReport.STATUS_FAILED
        val report = KiteRunReport(
            protocolVersion = KiteRecipe.PROTOCOL_VERSION,
            requestId = requestId,
            runId = resolvedRunId,
            recipeId = recipe.id,
            status = status,
            ok = stoppedOk,
            steps = listOf(
                KiteStepReport(
                    stepId = "local_stop",
                    type = KiteRecipe.STEP_SHELL,
                    status = status,
                    exitCode = if (stoppedOk) 0 else 1,
                    lastMeaningfulOutput = ownerStopOutput.trim().ifBlank { "没有发现仍在运行的后台进程，已关闭卡片实例。" }.takeLast(OUTPUT_TAIL_CHARS)
                )
            )
        )
        diagnostics.logParsedRunReport(recipe, report)
        return BridgeResult(
            ok = stoppedOk,
            accepted = stoppedOk,
            status = status,
            message = report.toJson().toString(),
            requestId = requestId,
            runReport = report,
            errorType = if (stoppedOk) BridgeErrorType.None else BridgeErrorType.BridgeFailed
        )
    }

    private fun executeProcess(
        command: List<String>,
        env: Map<String, String>,
        timeoutMs: Long,
        activeRecipeId: String? = null,
        activeRunId: String? = null,
        runtimeOwnerId: String? = null,
        onOutput: ((output: String, chunk: String) -> Unit)? = null
    ): DirectProcessResult {
        val output = StringBuilder()
        val outputLock = Any()
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()
        if (!activeRecipeId.isNullOrBlank() && !activeRunId.isNullOrBlank()) {
            directProcesses[activeRunId] = DirectProcessBinding(
                recipeId = activeRecipeId,
                runId = activeRunId,
                process = process,
                runtimeOwnerId = runtimeOwnerId
            )
        }
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
        if (!activeRunId.isNullOrBlank()) {
            directProcesses.remove(activeRunId)
        }
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

    private fun lastMeaningfulLine(output: String): String {
        var last = ""
        var lastNonEndMarker = ""
        var summary = ""
        var fail = ""
        var resourceFailure = ""
        var resourceProgress = ""
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            last = line
            if (!line.isEndMarkerLine()) lastNonEndMarker = line
            if (line.startsWith("SUMMARY ")) summary = line
            if (line.startsWith("FAIL\t") || line.startsWith("FAIL ")) fail = line
            KiteResourceInstallOutput.summary(line)?.let { structuredSummary ->
                if (KiteResourceInstallOutput.isFailure(line)) {
                    resourceFailure = structuredSummary
                } else {
                    resourceProgress = structuredSummary
                }
            }
        }
        return fail.ifBlank {
            resourceFailure.ifBlank {
                summary.ifBlank {
                    resourceProgress.ifBlank { lastNonEndMarker.ifBlank { last } }
                }
            }
        }
    }

    private fun String.isEndMarkerLine(): Boolean =
        endsWith("_END") && all { it == '_' || it.isUpperCase() || it.isDigit() }

    private fun updateDirectProcessBinding(runId: String, output: String) {
        val meta = extractRunBindingMeta(output)
        if (meta.isEmpty()) return
        directProcesses.computeIfPresent(runId) { _, existing ->
            existing.copy(
                pid = meta.rootPid ?: existing.pid,
                processGroupId = meta.processGroupId ?: existing.processGroupId,
                systemSessionId = meta.systemSessionId ?: existing.systemSessionId
            )
        }
    }

    private fun groupedAttachedPayload(command: String): String =
        runnerAwareLaunchPayload(command)

    private fun cardInstanceIdFrom(extraEnv: Map<String, String>, recipe: KiteRecipe): String =
        extraEnv["KITE_CARD_INSTANCE_ID"]
            ?: extraEnv["KITE_INSTANCE_ID"]
            ?: recipe.id

    private fun directRuntimeEnv(
        recipe: KiteRecipe,
        runId: String,
        extraEnv: Map<String, String>
    ): Map<String, String> {
        val cardInstanceId = cardInstanceIdFrom(extraEnv, recipe)
        val ownerId = extraEnv["KF_RUNTIME_ID"]?.takeIf { it.isNotBlank() }
            ?: runtimeOwnerId(recipe, cardInstanceId)
        val unitId = extraEnv["KF_UNIT_ID"]?.takeIf { it.isNotBlank() }
            ?: "run:${safeId(runId)}"
        return extraEnv + mapOf(
            "KF_RUNTIME_ID" to ownerId,
            "KF_UNIT_ID" to unitId
        )
    }

    private fun runtimeOwnerId(recipe: KiteRecipe, cardInstanceId: String): String {
        // ponytail: bridge only knows recipe/runtime ids; pass CardRun ownerKind here if owner rules grow.
        return if (recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE) {
            "resource:${resourceIdFromRecipe(recipe) ?: safeId(cardInstanceId)}"
        } else {
            "card:${safeId(cardInstanceId)}"
        }
    }

    private fun resourceIdFromRecipe(recipe: KiteRecipe): String? {
        val clean = recipe.id.removePrefix("resource-")
        if (clean == recipe.id) return null
        return when {
            clean.endsWith("-${KiteResourceInstallRecipes.OP_INSTALL}") ->
                clean.removeSuffix("-${KiteResourceInstallRecipes.OP_INSTALL}")
            clean.endsWith("-${KiteResourceInstallRecipes.OP_UNINSTALL}") ->
                clean.removeSuffix("-${KiteResourceInstallRecipes.OP_UNINSTALL}")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun stopOwnerProcesses(
        context: Context,
        recipe: KiteRecipe,
        cardInstanceIds: Collection<String?>,
        runtimeOwnerIds: Collection<String> = emptyList()
    ): String {
        val explicitOwnerIds = runtimeOwnerIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val ownerIds = explicitOwnerIds.ifEmpty {
            cardInstanceIds
                .ifEmpty { listOf(null) }
                .map { cardInstanceId -> runtimeOwnerId(recipe, cardInstanceId?.takeIf { it.isNotBlank() } ?: recipe.id) }
                .distinct()
        }
        val results = ProotOwnerProcessTerminator.terminateAll(context, ownerIds)
        ProotOwnerProcessTerminator.scheduleResidualReap(
            context = context,
            ownerIds = results.filterNot { it.settled }.map { it.ownerId }
        )
        return results.joinToString(separator = "") { result -> result.toStopOutput() }
    }

    private fun cardRunPidDir(cardInstanceId: String): String =
        "${WorkspaceBuildSupport.CONTAINER_HELPER_SYSTEM_STATE_PATH}/card-runs/${safeId(cardInstanceId)}"

    private fun cardRunPidFilePath(cardInstanceId: String, runId: String): String =
        "${cardRunPidDir(cardInstanceId)}/${safeId(runId)}.pid"

    private fun cleanCardRunPidFile(context: Context, pidFilePath: String?): String {
        val path = pidFilePath?.takeIf { it.isNotBlank() } ?: return ""
        val payload = "rm -f -- ${shellQuote(path)} && printf '__kite_pid_file_cleaned:%s\\n' ${shellQuote(path)}"
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = DEFAULT_WORKDIR,
            payload = payload,
            loginShell = true
        )
        return executeProcess(config.command, config.env, DIRECT_STOP_TIMEOUT_MS).output
    }

    private fun cleanCardRunPidDirPayload(cardInstanceId: String): String {
        val dir = cardRunPidDir(cardInstanceId)
        return "rm -rf -- ${shellQuote(dir)} && printf '__kite_pid_dir_cleaned:%s\\n' ${shellQuote(dir)}"
    }

    private fun detachedLaunchPayload(
        launchPayload: String,
        logPath: String,
        pidFilePath: String,
        cardInstanceId: String,
        runId: String
    ): String = """
        mkdir -p /tmp ${shellQuote(cardRunPidDir(cardInstanceId))}
        { $launchPayload; } > ${shellQuote(logPath)} 2>&1 < /dev/null &
        kite_root_pid=${'$'}!
        kite_pgid="${'$'}kite_root_pid"
        kite_sid="${'$'}kite_root_pid"
        kite_pid_file=${shellQuote(pidFilePath)}
        {
          printf 'cardInstanceId=%s\n' ${shellQuote(cardInstanceId)}
          printf 'runId=%s\n' ${shellQuote(runId)}
          printf 'rootPid=%s\n' "${'$'}kite_root_pid"
          printf 'processGroupId=%s\n' "${'$'}kite_pgid"
          printf 'systemSessionId=%s\n' "${'$'}kite_sid"
          printf 'logPath=%s\n' ${shellQuote(logPath)}
        } > "${'$'}kite_pid_file"
        printf '__kite_pid_file:%s\n' "${'$'}kite_pid_file"
        printf 'pid:%s\n' "${'$'}kite_root_pid"
        printf 'rootPid:%s\n' "${'$'}kite_root_pid"
        printf 'processGroupId:%s\n' "${'$'}kite_pgid"
        printf 'systemSessionId:%s\n' "${'$'}kite_sid"
    """.trimIndent()

    private fun runnerAwareLaunchPayload(command: String): String {
        val safeCommand = command.ifBlank { ":" }
        val runnerPath = WorkspaceBuildSupport.CONTAINER_KITE_RUNNER_PATH
        val runnerCommand = "${shellQuote(runnerPath)} --shell ${shellQuote(safeCommand)}"
        val fallbackCommand = "setsid bash -lc ${shellQuote(groupedShellBody(safeCommand))}"
        return "if [ -x ${shellQuote(runnerPath)} ]; then $runnerCommand; else $fallbackCommand; fi"
    }

    private fun groupedShellBody(command: String): String {
        val safeCommand = command.ifBlank { ":" }
        return listOf(
            "kite_root_pid=\"\$\$\"",
            "kite_pgid=\"\$kite_root_pid\"",
            "kite_sid=\"\$kite_root_pid\"",
            "printf '__kite_root_pid:%s\\n' \"\$kite_root_pid\"",
            "printf '__kite_process_group_id:%s\\n' \"\$kite_pgid\"",
            "printf '__kite_system_session_id:%s\\n' \"\$kite_sid\"",
            "exec bash -lc ${shellQuote(safeCommand)}"
        ).joinToString("; ")
    }

    private fun extractRunBindingMeta(text: String): RunBindingMeta {
        val rootPid = extractTaggedNumber(text, "__kite_root_pid")
            ?: extractTaggedNumber(text, "rootPid")
            ?: extractTaggedNumber(text, "pid")
        return RunBindingMeta(
            rootPid = rootPid,
            processGroupId = extractTaggedNumber(text, "__kite_process_group_id")
                ?: extractTaggedNumber(text, "processGroupId")
                ?: extractTaggedNumber(text, "pgid")
                ?: rootPid,
            systemSessionId = extractTaggedNumber(text, "__kite_system_session_id")
                ?: extractTaggedNumber(text, "systemSessionId")
                ?: extractTaggedNumber(text, "sid")
                ?: rootPid
        )
    }

    private fun extractTaggedNumber(text: String, label: String): String? {
        val pattern = Regex("""(?im)^\s*${Regex.escape(label)}\s*[:=]\s*(\d+)\s*$""")
        return pattern.find(text)?.groupValues?.getOrNull(1)
    }

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
    val pid: String?,
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
    val cardInstanceId: String? = null,
    val runtimeOwnerId: String? = null,
    val pidFilePath: String? = null
) {
    fun hasProcessBinding(): Boolean =
        !pid.isNullOrBlank() ||
            !rootPid.isNullOrBlank() ||
            !processGroupId.isNullOrBlank() ||
            !systemSessionId.isNullOrBlank()
}

private data class DirectProcessBinding(
    val recipeId: String,
    val runId: String,
    val process: Process,
    val pid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
    val runtimeOwnerId: String? = null
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
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
    val cardInstanceId: String? = null,
    val runtimeOwnerId: String? = null,
    val pidFilePath: String? = null,
    val report: KiteStepReport
)

private data class RunBindingMeta(
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null
) {
    fun isEmpty(): Boolean =
        rootPid.isNullOrBlank() && processGroupId.isNullOrBlank() && systemSessionId.isNullOrBlank()
}

internal fun detachedStartAccepted(timedOut: Boolean, exitCode: Int, pid: String?): Boolean =
    !pid.isNullOrBlank() && (exitCode == 0 || timedOut)

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
    val runId: String? = null,
    val recipeId: String,
    val stepId: String,
    val command: String,
    val outputTail: String,
    val lastChunk: String,
    val lastMeaningfulOutput: String,
    val pid: String? = null,
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null
)
