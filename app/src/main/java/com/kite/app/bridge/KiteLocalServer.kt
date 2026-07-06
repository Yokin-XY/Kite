package com.kite.app.bridge

import android.content.Context
import com.kite.app.browser.automation.BrowserAutomationAccessibilityNode
import com.kite.app.browser.automation.BrowserAutomationConsoleEntry
import com.kite.app.browser.automation.BrowserAutomationElementSummary
import com.kite.app.browser.automation.BrowserAutomationAction
import com.kite.app.browser.automation.BrowserAutomationActionResult
import com.kite.app.browser.automation.BrowserAutomationArtifactResolution
import com.kite.app.browser.automation.BrowserAutomationArtifactResolver
import com.kite.app.browser.automation.BrowserAutomationCapabilities
import com.kite.app.browser.automation.BrowserAutomationNetworkEntry
import com.kite.app.browser.automation.BrowserAutomationObservation
import com.kite.app.browser.automation.BrowserAutomationOpenRunRequest
import com.kite.app.browser.automation.BrowserAutomationRedactor
import com.kite.app.browser.automation.BrowserAutomationResultStatus
import com.kite.app.browser.automation.BrowserAutomationRunReconciler
import com.kite.app.browser.automation.BrowserAutomationRunRequest
import com.kite.app.browser.automation.BrowserAutomationRunResult
import com.kite.app.browser.automation.BrowserAutomationSession
import com.kite.app.browser.automation.BrowserAutomationSessionStatus
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.browser.automation.BrowserAutomationSnapshot
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import org.json.JSONArray
import org.json.JSONObject
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import kotlin.concurrent.thread

class KiteLocalServer(
    context: Context,
    private val diagnostics: KiteDiagnostics,
    private val openWeb: (KiteBrowserOpenRequest) -> Unit,
    private val openDesktop: (KiteDesktopOpenRequest) -> KiteDesktopOpenResponse,
    private val installApk: (KiteInstallApkRequest) -> KiteInstallApkResponse,
    private val browserAutomationAction: ((BrowserAutomationAction) -> BrowserAutomationActionResult)? = null,
    private val browserAutomationEnabled: (() -> Boolean)? = null
) {
    private val appContext = context.applicationContext
    private val browserAutomationStore = BrowserAutomationSessionStore(appContext)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    fun start(port: Int = DEFAULT_PORT) {
        if (running) return
        running = true
        thread(name = "KiteLocalServer", isDaemon = true) {
            runCatching {
                ServerSocket(port, 16, InetAddress.getByName("127.0.0.1")).use { socket ->
                    serverSocket = socket
                    while (running) {
                        val client = runCatching { socket.accept() }.getOrNull() ?: continue
                        thread(name = "KiteLocalServerClient", isDaemon = true) {
                            handleClient(client)
                        }
                    }
                }
            }.onFailure {
                running = false
                val message = if (it is BindException) {
                    "local_server_port_in_use:$port"
                } else {
                    "local_server_error:${it.message}"
                }
                diagnostics.logExternalUrl(message)
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 3000
            val request = readHttpRequest(client) ?: run {
                writeJson(client, 400, JSONObject().put("ok", false).put("error", "bad_request"))
                return
            }

            val parts = request.requestLine.split(" ")
            if (parts.size < 2) {
                writeJson(client, 400, JSONObject().put("ok", false).put("error", "bad_request"))
                return
            }

            val method = parts[0].uppercase(Locale.US)
            val target = parts[1]
            val path = target.substringBefore("?")
            val query = parseQuery(target.substringAfter("?", ""))
            diagnostics.logLocalServer("$method $path")

            when {
                method == "GET" && path == "/status" -> writeJson(
                    client,
                    200,
                    JSONObject()
                        .put("ok", true)
                        .put("app", "Kite")
                        .put("version", "0.3")
                        .put("server", "running")
                )

                method == "GET" && path == "/capabilities" -> writeRawJson(
                    client,
                    200,
                    diagnostics.capabilitiesJson()
                )

                method == "GET" && path == "/browser-automation/capabilities" -> writeJson(
                    client,
                    200,
                    BrowserAutomationCapabilities.toEndpointJson()
                )

                method == "GET" && path == "/browser-automation/sessions" -> writeJson(
                    client,
                    200,
                    browserAutomationSessionsJson(query)
                )

                method == "GET" && path == "/browser-automation/session" -> {
                    val session = query["sessionId"]
                        ?.takeIf { it.isNotBlank() }
                        ?.let(browserAutomationStore::get)
                        ?: query["instanceId"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let(browserAutomationStore::latestForInstance)
                        ?: browserAutomationStore.latestOpenSession()
                    if (session == null) {
                        writeJson(client, 404, JSONObject().put("ok", false).put("error", "session_not_found"))
                    } else {
                        val snapshot = browserAutomationStore.latestSnapshot(session.sessionId)
                        val result = browserAutomationStore.latestResult(session.sessionId)
                        val consoleEntries = browserAutomationStore.recentConsoleEntries(
                            session.sessionId,
                            query["limit"]?.toIntOrNull() ?: 20
                        )
                        val actionResults = browserAutomationStore.recentResults(
                            session.sessionId,
                            query["actionLimit"]?.toIntOrNull() ?: 20
                        )
                        val networkEntries = browserAutomationStore.recentNetworkEntries(
                            session.sessionId,
                            query["networkLimit"]?.toIntOrNull() ?: 40
                        )
                        val runResults = browserAutomationStore.recentRuns(
                            session.sessionId,
                            query["runLimit"]?.toIntOrNull() ?: 10
                        )
                        writeJson(
                            client,
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("session", session.toPublicJson())
                                .put("snapshot", snapshot?.toPublicJson() ?: JSONObject.NULL)
                                .put("result", result?.toJson() ?: JSONObject.NULL)
                                .put("actions", JSONArray().apply {
                                    actionResults.forEach { put(it.toJson()) }
                                })
                                .put("runs", JSONArray().apply {
                                    runResults.forEach { put(it.toJson()) }
                                })
                                .put("console", JSONArray().apply {
                                    consoleEntries.forEach { put(it.toPublicJson()) }
                                })
                                .put("network", JSONArray().apply {
                                    networkEntries.forEach { put(it.toPublicJson()) }
                                })
                        )
                    }
                }

                method == "GET" && path == "/browser-automation/observe" -> {
                    val session = resolveBrowserAutomationSession(query)
                    if (session == null) {
                        writeJson(client, 404, JSONObject().put("ok", false).put("error", "session_not_found"))
                    } else {
                        val snapshot = browserAutomationStore.latestSnapshot(session.sessionId)
                        val recentAction = browserAutomationStore.latestResult(session.sessionId)
                        val recentRun = browserAutomationStore.recentRuns(session.sessionId, 1).firstOrNull()
                        writeJson(
                            client,
                            200,
                            BrowserAutomationObservation.toJson(
                                session = session,
                                snapshot = snapshot,
                                recentAction = recentAction,
                                recentRun = recentRun,
                                interactiveLimit = query["interactiveLimit"]?.toIntOrNull() ?: 30,
                                textLimit = query["textLimit"]?.toIntOrNull() ?: 1200
                            )
                        )
                    }
                }

                method == "GET" && path == "/browser-automation/artifact" -> {
                    when (val artifact = BrowserAutomationArtifactResolver.resolve(appContext.filesDir, query["path"])) {
                        is BrowserAutomationArtifactResolution.Found -> {
                            val bytes = runCatching { artifact.file.readBytes() }.getOrNull()
                            if (bytes == null) {
                                writeJson(client, 404, JSONObject().put("ok", false).put("error", "artifact_not_found"))
                            } else {
                                writeBytes(client, 200, "image/png", bytes)
                            }
                        }
                        BrowserAutomationArtifactResolution.Missing -> writeJson(
                            client,
                            404,
                            JSONObject().put("ok", false).put("error", "artifact_not_found")
                        )
                        is BrowserAutomationArtifactResolution.Rejected -> writeJson(
                            client,
                            400,
                            JSONObject().put("ok", false).put("error", artifact.error)
                        )
                    }
                }

                method == "GET" && path == "/browser-automation/runs" -> {
                    val runId = query["runId"]?.takeIf { it.isNotBlank() }
                    if (runId != null) {
                        val runResult = browserAutomationStore.getRun(runId)
                        if (runResult == null) {
                            writeJson(client, 404, JSONObject().put("ok", false).put("error", "run_not_found"))
                        } else {
                            writeJson(client, 200, runResult.toJson().put("ok", true))
                        }
                    } else {
                        val session = resolveBrowserAutomationSession(query)
                        if (session == null) {
                            writeJson(client, 404, JSONObject().put("ok", false).put("error", "session_not_found"))
                        } else {
                            val limit = query["limit"]?.toIntOrNull() ?: 50
                            writeJson(
                                client,
                                200,
                                JSONObject()
                                    .put("ok", true)
                                    .put("sessionId", session.sessionId)
                                    .put("runs", JSONArray().apply {
                                        browserAutomationStore
                                            .recentRuns(session.sessionId, limit)
                                            .forEach { put(it.toJson()) }
                                    })
                            )
                        }
                    }
                }

                method == "GET" && path == "/browser-automation/actions" -> {
                    val session = resolveBrowserAutomationSession(query)
                    if (session == null) {
                        writeJson(client, 404, JSONObject().put("ok", false).put("error", "session_not_found"))
                    } else {
                        val limit = query["limit"]?.toIntOrNull() ?: 50
                        writeJson(
                            client,
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("sessionId", session.sessionId)
                                .put("results", JSONArray().apply {
                                    browserAutomationStore
                                        .recentResults(session.sessionId, limit)
                                        .forEach { put(it.toJson()) }
                                })
                        )
                    }
                }

                method == "GET" && path == "/browser-automation/console" -> {
                    val session = resolveBrowserAutomationSession(query)
                    if (session == null) {
                        writeJson(client, 404, JSONObject().put("ok", false).put("error", "session_not_found"))
                    } else {
                        val limit = query["limit"]?.toIntOrNull() ?: 50
                        writeJson(
                            client,
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("sessionId", session.sessionId)
                                .put("entries", JSONArray().apply {
                                    browserAutomationStore
                                        .recentConsoleEntries(session.sessionId, limit)
                                        .forEach { put(it.toPublicJson()) }
                                })
                        )
                    }
                }

                method == "GET" && path == "/browser-automation/network" -> {
                    val session = resolveBrowserAutomationSession(query)
                    if (session == null) {
                        writeJson(client, 404, JSONObject().put("ok", false).put("error", "session_not_found"))
                    } else {
                        val limit = query["limit"]?.toIntOrNull() ?: 80
                        writeJson(
                            client,
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("sessionId", session.sessionId)
                                .put("entries", JSONArray().apply {
                                    browserAutomationStore
                                        .recentNetworkEntries(session.sessionId, limit)
                                        .forEach { put(it.toPublicJson()) }
                                })
                        )
                    }
                }

                method == "GET" && path == "/browser-automation/test-page" -> writeHtml(
                    client,
                    200,
                    browserAutomationTestPageHtml()
                )

                method == "GET" && path == "/browser-automation/test-frame" -> writeHtml(
                    client,
                    200,
                    browserAutomationTestFrameHtml()
                )

                method == "GET" && path == "/toolchain" -> writeHtml(
                    client,
                    200,
                    toolchainPageHtml()
                )

                method == "GET" && path == "/toolchain/status" -> {
                    ToolchainPackInstaller.refreshState(appContext)
                    writeJson(client, 200, ToolchainPackInstaller.state.value.toJson())
                }

                method == "POST" && path == "/toolchain/prepare" -> {
                    ToolchainPackInstaller.prepareAiEnv(appContext)
                    writeJson(client, 202, JSONObject().put("ok", true).put("accepted", true).put("action", "prepare_ai_env"))
                }

                method == "POST" && path == "/toolchain/doctor" -> {
                    ToolchainPackInstaller.doctor(appContext)
                    writeJson(client, 202, JSONObject().put("ok", true).put("accepted", true).put("action", "toolchain_doctor"))
                }

                method in setOf("GET", "POST") && path == "/open-web" -> {
                    val openRequest = parseOpenWebRequest(query, request.body)
                    if (openRequest == null) {
                        writeJson(client, 400, JSONObject().put("ok", false).put("error", "missing_url"))
                    } else {
                        openWeb(openRequest)
                        writeJson(
                            client,
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("accepted", true)
                                .put("url", openRequest.url)
                                .put("recipeId", openRequest.recipeId ?: "")
                                .put("instanceId", openRequest.instanceId ?: "")
                                .put("cardInstanceId", openRequest.instanceId ?: "")
                        )
                    }
                }

                method in setOf("GET", "POST") && path == "/browser-automation/action" -> {
                    val action = parseBrowserAutomationAction(query, request.body)
                    val handler = browserAutomationAction
                    when {
                        action == null -> writeJson(
                            client,
                            400,
                            JSONObject().put("ok", false).put("error", "bad_action")
                        )
                        handler == null -> writeJson(
                            client,
                            503,
                            JSONObject().put("ok", false).put("error", "browser_automation_unavailable")
                        )
                        else -> {
                            val result = awaitSettledBrowserAutomationResult(handler(action))
                            writeJson(
                                client,
                                if (result.status == BrowserAutomationResultStatus.Rejected) 409 else 200,
                                result.toJson().put("ok", result.succeeded)
                            )
                        }
                    }
                }

                method == "POST" && path == "/browser-automation/run" -> {
                    val runRequest = parseBrowserAutomationRun(query, request.body)
                    val handler = browserAutomationAction
                    when {
                        runRequest == null -> writeJson(
                            client,
                            400,
                            JSONObject().put("ok", false).put("error", "bad_run")
                        )
                        handler == null -> writeJson(
                            client,
                            503,
                            JSONObject().put("ok", false).put("error", "browser_automation_unavailable")
                        )
                        else -> {
                            val runResult = executeBrowserAutomationRun(runRequest, handler)
                            browserAutomationStore.saveRunResult(runResult)
                            writeJson(
                                client,
                                statusCodeForRunResult(runResult),
                                runResult.toJson().put("ok", runResult.succeeded)
                            )
                        }
                    }
                }

                method == "POST" && path == "/browser-automation/open-run" -> {
                    val openRunRequest = parseBrowserAutomationOpenRun(query, request.body)
                    val handler = browserAutomationAction
                    val enabled = browserAutomationEnabled?.invoke() ?: true
                    when {
                        openRunRequest == null -> writeJson(
                            client,
                            400,
                            JSONObject().put("ok", false).put("error", "bad_open_run")
                        )
                        handler == null -> writeJson(
                            client,
                            503,
                            JSONObject().put("ok", false).put("error", "browser_automation_unavailable")
                        )
                        !enabled -> {
                            val startedAt = System.currentTimeMillis()
                            val runResult = openRunFailureResult(
                                request = openRunRequest,
                                status = BrowserAutomationResultStatus.Rejected,
                                errorCode = "mode_not_enabled",
                                detail = "当前浏览器模式不是自动浏览器",
                                startedAt = startedAt
                            )
                            browserAutomationStore.saveRunResult(runResult)
                            writeJson(
                                client,
                                409,
                                openRunResponseJson(openRunRequest, null, runResult, opened = false)
                            )
                        }
                        else -> {
                            val startedAt = System.currentTimeMillis()
                            val openStartedAt = System.currentTimeMillis()
                            openWeb(
                                KiteBrowserOpenRequest(
                                    url = openRunRequest.url,
                                    recipeId = openRunRequest.recipeId,
                                    instanceId = openRunRequest.instanceId,
                                    source = openRunRequest.source
                                )
                            )
                            val session = waitForOpenedAutomationSession(openRunRequest, openStartedAt)
                            if (session == null) {
                                val runResult = openRunFailureResult(
                                    request = openRunRequest,
                                    status = BrowserAutomationResultStatus.TimedOut,
                                    errorCode = "session_open_timeout",
                                    detail = "自动浏览器 session 未在超时内 ready",
                                    startedAt = startedAt
                                )
                                browserAutomationStore.saveRunResult(runResult)
                                writeJson(
                                    client,
                                    504,
                                    openRunResponseJson(openRunRequest, null, runResult, opened = true)
                                )
                            } else {
                                val runResult = executeBrowserAutomationRun(
                                    runRequest = openRunRequest.runRequest.withSession(session),
                                    handler = handler,
                                    startedAt = startedAt
                                )
                                browserAutomationStore.saveRunResult(runResult)
                                writeJson(
                                    client,
                                    statusCodeForRunResult(runResult),
                                    openRunResponseJson(openRunRequest, session, runResult, opened = true)
                                )
                            }
                        }
                    }
                }

                method in setOf("GET", "POST") && path == "/open-desktop" -> {
                    val openRequest = parseOpenDesktopRequest(query, request.body)
                    if (openRequest == null) {
                        writeJson(client, 400, JSONObject().put("ok", false).put("error", "missing_command"))
                    } else {
                        val response = openDesktop(openRequest)
                        writeJson(
                            client,
                            if (response.accepted) 200 else 500,
                            JSONObject()
                                .put("ok", response.accepted)
                                .put("accepted", response.accepted)
                                .put("recipeId", response.recipeId ?: "")
                                .put("instanceId", response.instanceId ?: "")
                                .put("cardInstanceId", response.instanceId ?: "")
                                .put("display", response.display)
                                .put("socketPath", response.socketPath)
                                .put("error", response.error)
                        )
                    }
                }

                method in setOf("GET", "POST") && path == "/install-apk" -> {
                    val installRequest = parseInstallApkRequest(query, request.body)
                    if (installRequest == null) {
                        writeJson(client, 400, JSONObject().put("ok", false).put("error", "missing_path"))
                    } else {
                        val response = installApk(installRequest)
                        writeJson(
                            client,
                            if (response.accepted) 200 else 400,
                            JSONObject()
                                .put("ok", response.accepted)
                                .put("accepted", response.accepted)
                                .put("path", response.path)
                                .put("resolvedPath", response.resolvedPath)
                                .put("error", response.error)
                        )
                    }
                }

                else -> writeJson(client, 404, JSONObject().put("ok", false).put("error", "not_found"))
            }
        }
    }

    private fun parseOpenWebRequest(query: Map<String, String>, body: String): KiteBrowserOpenRequest? {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val rawBody = body.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val url = json?.optString("url")?.takeIf { it.isNotBlank() }
            ?: query["url"]?.takeIf { it.isNotBlank() }
            ?: rawBody
            ?: return null
        return KiteBrowserOpenRequest(
            url = url,
            recipeId = json?.optString("recipeId")?.takeIf { it.isNotBlank() }
                ?: query["recipeId"]?.takeIf { it.isNotBlank() },
            instanceId = json?.optString("cardInstanceId")?.takeIf { it.isNotBlank() }
                ?: json?.optString("instanceId")?.takeIf { it.isNotBlank() }
                ?: query["cardInstanceId"]?.takeIf { it.isNotBlank() }
                ?: query["instanceId"]?.takeIf { it.isNotBlank() },
            source = json?.optString("source")?.takeIf { it.isNotBlank() }
                ?: query["source"]?.takeIf { it.isNotBlank() }
                ?: KiteBrowserOpenRequest.SOURCE_UBUNTU_BROWSER
        )
    }

    private fun parseOpenDesktopRequest(query: Map<String, String>, body: String): KiteDesktopOpenRequest? {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val rawBody = body.trim().takeIf { it.isNotBlank() && !it.startsWith("{") }
        val command = json?.optString("command")?.takeIf { it.isNotBlank() }
            ?: json?.optString("cmd")?.takeIf { it.isNotBlank() }
            ?: query["command"]?.takeIf { it.isNotBlank() }
            ?: query["cmd"]?.takeIf { it.isNotBlank() }
            ?: rawBody
            ?: return null
        return KiteDesktopOpenRequest(
            command = command,
            title = json?.optString("title")?.takeIf { it.isNotBlank() }
                ?: query["title"]?.takeIf { it.isNotBlank() }
                ?: command.take(80),
            recipeId = json?.optString("recipeId")?.takeIf { it.isNotBlank() }
                ?: query["recipeId"]?.takeIf { it.isNotBlank() },
            instanceId = json?.optString("cardInstanceId")?.takeIf { it.isNotBlank() }
                ?: json?.optString("instanceId")?.takeIf { it.isNotBlank() }
                ?: query["cardInstanceId"]?.takeIf { it.isNotBlank() }
                ?: query["instanceId"]?.takeIf { it.isNotBlank() },
            source = json?.optString("source")?.takeIf { it.isNotBlank() }
                ?: query["source"]?.takeIf { it.isNotBlank() }
                ?: KiteDesktopOpenRequest.SOURCE_UBUNTU_DESKTOP
        )
    }

    private fun parseInstallApkRequest(query: Map<String, String>, body: String): KiteInstallApkRequest? {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val rawBody = body.trim().takeIf { it.isNotBlank() && !it.startsWith("{") }
        val path = json?.optString("path")?.takeIf { it.isNotBlank() }
            ?: json?.optString("apk")?.takeIf { it.isNotBlank() }
            ?: query["path"]?.takeIf { it.isNotBlank() }
            ?: query["apk"]?.takeIf { it.isNotBlank() }
            ?: rawBody
            ?: return null
        return KiteInstallApkRequest(
            path = path,
            source = json?.optString("source")?.takeIf { it.isNotBlank() }
                ?: query["source"]?.takeIf { it.isNotBlank() }
                ?: KiteInstallApkRequest.SOURCE_UBUNTU_SHELL
        )
    }

    private fun parseBrowserAutomationAction(query: Map<String, String>, body: String): BrowserAutomationAction? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: JSONObject()
        query.forEach { (key, value) ->
            if (!json.has(key)) json.put(key, value)
        }
        if (!json.has("target")) {
            json.put(
                "target",
                JSONObject()
                    .put("kind", json.optString("targetKind"))
                    .put("value", json.optString("target"))
                    .put("name", json.optString("name").ifBlank { json.optString("targetName") })
                    .put("match", json.optString("match"))
                    .put("index", json.optInt("index", 0))
            )
        }
        return BrowserAutomationAction.fromJson(json)
    }

    private fun parseBrowserAutomationRun(query: Map<String, String>, body: String): BrowserAutomationRunRequest? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        query.forEach { (key, value) ->
            if (!json.has(key)) json.put(key, value)
        }
        return BrowserAutomationRunRequest.fromJson(json)
    }

    private fun parseBrowserAutomationOpenRun(query: Map<String, String>, body: String): BrowserAutomationOpenRunRequest? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        query.forEach { (key, value) ->
            if (!json.has(key)) json.put(key, value)
        }
        return BrowserAutomationOpenRunRequest.fromJson(json)
    }

    private fun executeBrowserAutomationRun(
        runRequest: BrowserAutomationRunRequest,
        handler: (BrowserAutomationAction) -> BrowserAutomationActionResult,
        startedAt: Long = System.currentTimeMillis()
    ): BrowserAutomationRunResult {
        val results = mutableListOf<BrowserAutomationActionResult>()
        for (action in runRequest.actions) {
            val result = awaitSettledBrowserAutomationResult(handler(action))
            results.add(result)
            if (runRequest.stopOnFailure && !result.succeeded) break
        }
        return BrowserAutomationRunResult.fromResults(
            request = runRequest,
            startedAt = startedAt,
            results = results
        )
    }

    private fun awaitSettledBrowserAutomationResult(
        result: BrowserAutomationActionResult
    ): BrowserAutomationActionResult {
        if (!BrowserAutomationRunReconciler.isRequestTimeout(result)) return result
        val deadline = System.currentTimeMillis() + LATE_ACTION_RECONCILE_MS
        while (System.currentTimeMillis() <= deadline) {
            val storedResult = browserAutomationStore.latestResultForAction(
                actionId = result.actionId,
                sessionId = result.sessionId
            )
            val reconciled = BrowserAutomationRunReconciler.reconcileActionResult(result, storedResult)
            if (reconciled != result) return reconciled
            Thread.sleep(LATE_ACTION_RECONCILE_POLL_MS)
        }
        return result
    }

    private fun waitForOpenedAutomationSession(
        request: BrowserAutomationOpenRunRequest,
        openedAt: Long
    ): BrowserAutomationSession? {
        val deadline = System.currentTimeMillis() + request.openTimeoutMs
        val targetUrl = BrowserAutomationRedactor.redactUrl(request.url)
        var latestCandidate: BrowserAutomationSession? = null
        while (System.currentTimeMillis() <= deadline) {
            latestCandidate = browserAutomationStore
                .allSessions()
                .filter { session ->
                    val instanceMatches = request.instanceId.isNullOrBlank() || request.instanceId == session.instanceId
                    val urlMatches = !request.instanceId.isNullOrBlank() || sessionUrlMatches(session.url, targetUrl)
                    session.status != BrowserAutomationSessionStatus.Closed &&
                        session.updatedAt >= openedAt - 500L &&
                        instanceMatches &&
                        urlMatches
                }
                .maxByOrNull { it.updatedAt }
            if (latestCandidate?.status == BrowserAutomationSessionStatus.Ready) {
                return latestCandidate
            }
            Thread.sleep(150L)
        }
        return latestCandidate?.takeIf { it.status == BrowserAutomationSessionStatus.Ready }
    }

    private fun sessionUrlMatches(sessionUrl: String, targetUrl: String): Boolean {
        val left = BrowserAutomationRedactor.redactUrl(sessionUrl).trimEnd('/')
        val right = targetUrl.trimEnd('/')
        return left == right ||
            left.substringBefore("#") == right.substringBefore("#")
    }

    private fun openRunFailureResult(
        request: BrowserAutomationOpenRunRequest,
        status: BrowserAutomationResultStatus,
        errorCode: String,
        detail: String,
        startedAt: Long
    ): BrowserAutomationRunResult {
        val action = request.runRequest.actions.first()
        val result = BrowserAutomationActionResult(
            actionId = action.actionId,
            sessionId = action.sessionId
                ?: request.instanceId
                ?: request.runRequest.sessionId
                ?: errorCode,
            type = action.type,
            status = status,
            durationMs = System.currentTimeMillis() - startedAt,
            url = "",
            title = null,
            message = detail,
            errorCode = errorCode,
            errorDetail = detail
        )
        return BrowserAutomationRunResult.fromResults(request.runRequest, startedAt, listOf(result))
    }

    private fun openRunResponseJson(
        request: BrowserAutomationOpenRunRequest,
        session: BrowserAutomationSession?,
        runResult: BrowserAutomationRunResult,
        opened: Boolean
    ): JSONObject =
        runResult.toJson()
            .put("ok", runResult.succeeded)
            .put(
                "open",
                JSONObject()
                    .put("requested", opened)
                    .put("url", BrowserAutomationRedactor.redactUrl(request.url))
                    .put("source", request.source)
                    .put("sessionId", session?.sessionId.orEmpty())
                    .put("instanceId", session?.instanceId ?: request.instanceId.orEmpty())
                    .put("status", session?.status?.name.orEmpty())
            )

    private fun statusCodeForRunResult(runResult: BrowserAutomationRunResult): Int =
        when {
            runResult.succeeded -> 200
            runResult.status == BrowserAutomationResultStatus.Rejected && runResult.results.none { it.succeeded } -> 409
            runResult.status == BrowserAutomationResultStatus.TimedOut -> 504
            else -> 200
        }

    private fun resolveBrowserAutomationSession(query: Map<String, String>): BrowserAutomationSession? =
        query["sessionId"]
            ?.takeIf { it.isNotBlank() }
            ?.let(browserAutomationStore::get)
            ?: query["instanceId"]
                ?.takeIf { it.isNotBlank() }
                ?.let(browserAutomationStore::latestForInstance)
            ?: browserAutomationStore.latestOpenSession()

    private fun browserAutomationSessionsJson(query: Map<String, String>): JSONObject {
        val includeClosed = query["includeClosed"].equals("true", ignoreCase = true)
        val sessions = browserAutomationStore.recentSessions(
            limit = query["limit"]?.toIntOrNull() ?: 20,
            includeClosed = includeClosed,
            instanceId = query["instanceId"]?.takeIf { it.isNotBlank() }
        )
        return JSONObject()
            .put("ok", true)
            .put("count", sessions.size)
            .put("latestSessionId", sessions.firstOrNull()?.sessionId.orEmpty())
            .put("source", "BrowserAutomationSessionStore")
            .put("includeClosed", includeClosed)
            .put("sessions", JSONArray().apply {
                sessions.forEach { put(it.toPublicJson()) }
            })
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&")
            .mapNotNull { part ->
                val key = part.substringBefore("=").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = part.substringAfter("=", "")
                decodeQueryPart(key) to decodeQueryPart(value)
            }
            .toMap()
    }

    private fun decodeQueryPart(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private fun readHttpRequest(socket: Socket): HttpRequest? {
        val input = socket.getInputStream()
        val headerBytes = ArrayList<Byte>()
        while (true) {
            val next = input.read()
            if (next < 0) break
            headerBytes.add(next.toByte())
            val size = headerBytes.size
            if (size >= 4 &&
                headerBytes[size - 4] == '\r'.code.toByte() &&
                headerBytes[size - 3] == '\n'.code.toByte() &&
                headerBytes[size - 2] == '\r'.code.toByte() &&
                headerBytes[size - 1] == '\n'.code.toByte()
            ) {
                break
            }
            if (size > MAX_HEADER_BYTES) return null
        }
        if (headerBytes.isEmpty()) return null

        val headerText = headerBytes.toByteArray().toString(Charsets.UTF_8)
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val contentLength = lines
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        val bodyBytes = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(bodyBytes, offset, contentLength - offset)
            if (read < 0) break
            offset += read
        }
        return HttpRequest(requestLine, bodyBytes.copyOf(offset).toString(Charsets.UTF_8))
    }

    private fun writeJson(socket: Socket, status: Int, json: JSONObject) {
        writeRawJson(socket, status, json.toString())
    }

    private fun writeHtml(socket: Socket, status: Int, body: String) {
        writeRaw(socket, status, "text/html; charset=utf-8", body)
    }

    private fun writeRawJson(socket: Socket, status: Int, body: String) {
        writeRaw(socket, status, "application/json; charset=utf-8", body)
    }

    private fun writeRaw(socket: Socket, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeBytes(socket, status, contentType, bytes)
    }

    private fun writeBytes(socket: Socket, status: Int, contentType: String, bytes: ByteArray) {
        socket.getOutputStream().write(
            "HTTP/1.1 $status ${statusText(status)}\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.UTF_8)
        )
        socket.getOutputStream().write(bytes)
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        409 -> "Conflict"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> "OK"
    }

    private fun toolchainPageHtml(): String = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>KF Tool Environment</title>
          <style>
            :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
            body { margin: 0; padding: 18px; background: #f6f7f9; color: #172033; }
            main { max-width: 760px; margin: 0 auto; }
            h1 { font-size: 22px; margin: 0 0 8px; }
            p { color: #5d667a; line-height: 1.45; }
            .panel { background: #fff; border: 1px solid #dfe3ea; border-radius: 8px; padding: 14px; margin-top: 12px; }
            .row { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
            button { border: 0; border-radius: 8px; padding: 11px 14px; background: #16856a; color: white; font-weight: 700; }
            button.secondary { background: #354052; }
            pre { white-space: pre-wrap; word-break: break-word; max-height: 46vh; overflow: auto; background: #111827; color: #e5e7eb; padding: 12px; border-radius: 8px; }
            .status { font-weight: 800; }
            @media (prefers-color-scheme: dark) {
              body { background: #111318; color: #eef2f7; }
              .panel { background: #1b2029; border-color: #303846; }
              p { color: #aeb7c7; }
            }
          </style>
        </head>
        <body>
          <main>
            <h1>KF tool environment</h1>
            <p>Checks and repairs Node 24 LTS, uv, pnpm, Python venv/pip support, adb/fastboot, and common CLI tools inside the KF Ubuntu workspace.</p>
            <div class="panel">
              <div>Phase: <span id="phase" class="status">loading</span></div>
              <div>Action: <span id="action">--</span></div>
              <div>Summary: <span id="summary">--</span></div>
              <div>Exit: <span id="exit">--</span></div>
              <div>Log: <span id="log">--</span></div>
              <div class="row">
                <button onclick="postAction('/toolchain/prepare')">Check and repair</button>
                <button class="secondary" onclick="postAction('/toolchain/doctor')">Run diagnostics</button>
              </div>
            </div>
            <div class="panel">
              <pre id="preview">Waiting for status...</pre>
            </div>
          </main>
          <script>
            async function postAction(path) {
              await fetch(path, { method: 'POST' });
              await refresh();
            }
            async function refresh() {
              try {
                const res = await fetch('/toolchain/status?ts=' + Date.now());
                const s = await res.json();
                document.getElementById('phase').textContent = s.phase || '--';
                document.getElementById('action').textContent = s.action || '--';
                document.getElementById('summary').textContent = s.summary || '--';
                document.getElementById('exit').textContent = (s.exitCode === null || s.exitCode === undefined) ? '--' : s.exitCode;
                document.getElementById('log').textContent = s.logPath || '--';
                document.getElementById('preview').textContent = s.outputPreview || 'No output yet.';
              } catch (e) {
                document.getElementById('phase').textContent = 'error';
                document.getElementById('preview').textContent = String(e);
              }
            }
            refresh();
            setInterval(refresh, 1500);
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun browserAutomationTestPageHtml(): String = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Kite Automation Test</title>
          <style>
            :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
            body { margin: 0; padding: 20px; background: #f7f8fa; color: #172033; }
            main { max-width: 680px; margin: 0 auto; }
            label { display: block; font-weight: 700; margin: 18px 0 8px; }
            input, select, textarea, [contenteditable="true"] { width: 100%; box-sizing: border-box; border: 1px solid #bac3d1; border-radius: 8px; padding: 12px; font-size: 16px; background: white; }
            textarea { min-height: 92px; resize: vertical; font-family: inherit; }
            [contenteditable="true"] { min-height: 42px; outline: none; }
            label.inline-control { display: flex; align-items: center; gap: 10px; margin: 14px 0 8px; }
            label.inline-control input { width: auto; min-width: 18px; height: 18px; margin: 0; padding: 0; }
            fieldset { margin: 14px 0 8px; padding: 10px 12px; border: 1px solid #d5dae3; border-radius: 8px; }
            legend { font-weight: 800; padding: 0 4px; }
            button { margin-top: 14px; border: 0; border-radius: 8px; padding: 12px 16px; background: #166d5a; color: white; font-weight: 800; font-size: 15px; }
            .automation-nav { margin-top: 14px; display: flex; flex-wrap: wrap; gap: 10px; }
            .automation-nav a { color: #166d5a; font-weight: 800; }
            #result, #pointer-result, #hover-result, #double-result, #duplicate-result, #disabled-result, #nav-result { margin-top: 18px; padding: 14px; border: 1px solid #d5dae3; border-radius: 8px; background: white; min-height: 24px; }
            iframe { display: block; width: 100%; min-height: 230px; margin-top: 18px; border: 1px solid #b7c1d4; border-radius: 8px; background: white; }
            open-shadow-widget, closed-shadow-widget { display: block; margin-top: 18px; padding: 14px; border: 1px dashed #9aa7bd; border-radius: 8px; background: rgba(22, 109, 90, 0.04); }
            .spacer { height: 1200px; display: flex; align-items: center; justify-content: center; color: #667085; }
            #deep-target { margin-top: 18px; padding: 16px; border: 1px solid #b7c1d4; border-radius: 8px; background: #fff; }
            @media (prefers-color-scheme: dark) {
              body { background: #111318; color: #eef2f7; }
              input, select, textarea, [contenteditable="true"] { background: #151922; color: #eef2f7; border-color: #3b4555; }
              fieldset { border-color: #303846; }
              .automation-nav a { color: #59d2b4; }
              #result, #pointer-result, #hover-result, #double-result, #duplicate-result, #disabled-result, #nav-result { background: #1b2029; border-color: #303846; }
              iframe { background: #1b2029; border-color: #303846; }
              open-shadow-widget, closed-shadow-widget { border-color: #3b4555; background: rgba(89, 210, 180, 0.08); }
              #deep-target { background: #1b2029; border-color: #303846; }
            }
          </style>
        </head>
        <body>
          <main>
            <h1>Kite Automation Test</h1>
            <label for="name">Name</label>
            <input id="name" name="name" placeholder="输入名字" autocomplete="off">
            <label for="notes">Notes</label>
            <textarea id="notes" name="notes" aria-label="Notes">Prefilled notes</textarea>
            <label for="editable-note">Editable note</label>
            <div id="editable-note" role="textbox" aria-label="Editable note" contenteditable="true">Prefilled editable</div>
            <label for="disabled-name">Disabled Name</label>
            <input id="disabled-name" name="disabled-name" placeholder="Disabled input" value="Locked disabled value" autocomplete="off" disabled>
            <label for="readonly-name">Readonly Name</label>
            <input id="readonly-name" name="readonly-name" placeholder="Readonly input" value="Readonly locked value" autocomplete="off" readonly>
            <label for="tone">Tone</label>
            <select id="tone" name="tone">
              <option value="">Plain</option>
              <option value="friendly">Friendly</option>
              <option value="focused">Focused</option>
              <option value="formal">Formal</option>
            </select>
            <label for="subscribe" class="inline-control">
              <input id="subscribe" name="subscribe" type="checkbox">
              <span>Subscribe updates</span>
            </label>
            <fieldset aria-label="Plan">
              <legend>Plan</legend>
              <label for="plan-basic" class="inline-control">
                <input id="plan-basic" name="plan" type="radio" value="basic">
                <span>Basic plan</span>
              </label>
              <label for="plan-pro" class="inline-control">
                <input id="plan-pro" name="plan" type="radio" value="pro">
                <span>Pro plan</span>
              </label>
            </fieldset>
            <label for="secret">Secret</label>
            <input id="secret" name="secret" type="password" placeholder="Password" value="a7-password-should-not-leak" autocomplete="off">
            <div id="hidden-secret" style="display:none">a7-hidden-text-should-not-leak</div>
            <button id="apply" type="button">Apply greeting</button>
            <div id="result" role="status" aria-label="automation result">Waiting</div>
            <button id="pointer-apply" type="button">Pointer gated click</button>
            <div id="pointer-result" role="status" aria-label="pointer automation result">Pointer waiting</div>
            <button id="hover-apply" type="button">Hover reveal menu</button>
            <div id="hover-result" role="status" aria-label="hover automation result">Hover waiting</div>
            <button id="double-apply" type="button">Double click open</button>
            <div id="double-result" role="status" aria-label="double click automation result">Double click waiting</div>
            <button id="duplicate-first" type="button">Duplicate action</button>
            <button id="duplicate-second" type="button">Duplicate action</button>
            <div id="duplicate-result" role="status" aria-label="duplicate automation result">Duplicate waiting</div>
            <button id="disabled-apply" type="button" disabled>Disabled action</button>
            <div id="disabled-result" role="status" aria-label="disabled automation result">Disabled waiting</div>
            <nav class="automation-nav" aria-label="navigation automation">
              <a id="nav-first" href="#a23-first">Go first hash</a>
              <a id="nav-second" href="#a23-second">Go second hash</a>
            </nav>
            <div id="nav-result" role="status" aria-label="navigation automation result">Hash waiting</div>
            <iframe id="same-origin-frame" title="Same origin automation frame" src="/browser-automation/test-frame"></iframe>
            <iframe id="sandboxed-frame" title="Sandboxed blocked frame" sandbox srcdoc='<main><p>sandbox-secret-should-not-leak</p><button>Sandbox hidden action</button></main>'></iframe>
            <open-shadow-widget id="open-shadow-widget" aria-label="Open shadow automation host"></open-shadow-widget>
            <closed-shadow-widget id="closed-shadow-widget" role="region" aria-label="Closed shadow automation host"></closed-shadow-widget>
            <div class="spacer">Scroll area</div>
            <section id="deep-target" aria-label="deep automation target">
              <h2>Deep target ready</h2>
              <button id="deep-apply" type="button">Mark deep target</button>
              <div id="deep-result" role="status">Deep waiting</div>
            </section>
          </main>
          <script>
            function applyGreeting() {
              var value = document.getElementById('name').value || 'Kite';
              var tone = document.getElementById('tone');
              var toneText = tone && tone.value ? ' (' + tone.options[tone.selectedIndex].text + ')' : '';
              var subscribe = document.getElementById('subscribe');
              var subscribeText = subscribe && subscribe.checked ? ' +Subscribed' : '';
              var plan = document.querySelector('input[name="plan"]:checked');
              var planText = plan ? ' [' + plan.value + ']' : '';
              document.getElementById('result').textContent = 'Hello ' + value + toneText + subscribeText + planText;
              document.body.setAttribute('data-automation-result', value);
              console.log('automation:greeting:' + value);
              fetch('/status?automationNetwork=' + encodeURIComponent(value)).catch(function () {});
            }
            document.getElementById('apply').addEventListener('click', function () {
              applyGreeting();
            });
            var pointerReady = false;
            document.getElementById('pointer-apply').addEventListener('pointerdown', function () {
              pointerReady = true;
              document.body.setAttribute('data-pointer-down', 'pointer');
            });
            document.getElementById('pointer-apply').addEventListener('mousedown', function () {
              pointerReady = true;
              document.body.setAttribute('data-mouse-down', 'mouse');
            });
            document.getElementById('pointer-apply').addEventListener('click', function () {
              var result = document.getElementById('pointer-result');
              if (pointerReady) {
                result.textContent = 'Pointer sequence clicked';
                document.body.setAttribute('data-pointer-result', 'clicked');
                console.log('automation:pointer-clicked');
              } else {
                result.textContent = 'Pointer sequence missing';
                document.body.setAttribute('data-pointer-result', 'missing');
              }
              pointerReady = false;
            });
            var hoverSource = { pointer: false, mouse: false };
            function revealHoverMenu(source) {
              hoverSource[source] = true;
              var result = document.getElementById('hover-result');
              result.textContent = 'Hover menu revealed';
              document.body.setAttribute('data-hover-result', 'revealed');
              document.body.setAttribute('data-hover-' + source, source);
              console.log('automation:hover-revealed:' + source);
            }
            ['pointerover', 'pointerenter', 'pointermove'].forEach(function (name) {
              document.getElementById('hover-apply').addEventListener(name, function () {
                revealHoverMenu('pointer');
              });
            });
            ['mouseover', 'mouseenter', 'mousemove'].forEach(function (name) {
              document.getElementById('hover-apply').addEventListener(name, function () {
                revealHoverMenu('mouse');
              });
            });
            var doubleClickCount = 0;
            document.getElementById('double-apply').addEventListener('click', function () {
              doubleClickCount += 1;
              document.body.setAttribute('data-double-clicks', String(doubleClickCount));
            });
            document.getElementById('double-apply').addEventListener('dblclick', function () {
              document.getElementById('double-result').textContent = 'Double click opened';
              document.body.setAttribute('data-double-result', 'opened');
              console.log('automation:double-click-opened');
            });
            document.getElementById('duplicate-first').addEventListener('click', function () {
              document.getElementById('duplicate-result').textContent = 'Duplicate first clicked';
              document.body.setAttribute('data-duplicate-result', 'first');
              console.log('automation:duplicate:first');
            });
            document.getElementById('duplicate-second').addEventListener('click', function () {
              document.getElementById('duplicate-result').textContent = 'Duplicate second clicked';
              document.body.setAttribute('data-duplicate-result', 'second');
              console.log('automation:duplicate:second');
            });
            document.getElementById('disabled-apply').addEventListener('click', function () {
              document.getElementById('disabled-result').textContent = 'Disabled action clicked';
              document.body.setAttribute('data-disabled-result', 'clicked');
              console.log('automation:disabled-clicked');
            });
            function updateNavigationStatus() {
              var hash = location.hash || '#none';
              var label = hash.replace(/^#/, '') || 'none';
              document.getElementById('nav-result').textContent = 'Hash ' + label;
              document.body.setAttribute('data-nav-hash', hash);
              console.log('automation:navigation:' + hash);
            }
            window.addEventListener('hashchange', updateNavigationStatus);
            updateNavigationStatus();
            document.getElementById('name').addEventListener('keydown', function (event) {
              if (event.key === 'Enter') {
                event.preventDefault();
                applyGreeting();
              }
            });
            document.getElementById('deep-apply').addEventListener('click', function () {
              document.getElementById('deep-result').textContent = 'Deep target clicked';
              document.body.setAttribute('data-deep-result', 'clicked');
              console.log('automation:deep-clicked');
              fetch('/browser-automation/capabilities?networkProbe=deep').catch(function () {});
            });
            customElements.define('open-shadow-widget', class extends HTMLElement {
              connectedCallback() {
                if (this.shadowRoot) return;
                var root = this.attachShadow({ mode: 'open' });
                root.innerHTML = [
                  '<style>',
                  ':host { font-family: system-ui, sans-serif; }',
                  'label { display: block; font-weight: 800; margin-bottom: 6px; }',
                  'input { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #b7c1d4; border-radius: 6px; font-size: 16px; }',
                  'button { margin-top: 12px; padding: 10px 14px; border: 0; border-radius: 6px; background: #166d5a; color: white; font-weight: 800; }',
                  '#shadow-result { margin-top: 14px; padding: 12px; border: 1px solid #d5dae3; border-radius: 6px; background: white; min-height: 22px; }',
                  '</style>',
                  '<label for="shadow-name">Shadow Name</label>',
                  '<input id="shadow-name" name="shadow-name" placeholder="shadow input" autocomplete="off">',
                  '<button id="shadow-apply" type="button">Apply shadow greeting</button>',
                  '<div id="shadow-result" role="status" aria-label="shadow automation result">Shadow waiting</div>'
                ].join('');
                root.getElementById('shadow-apply').addEventListener('click', function () {
                  var value = root.getElementById('shadow-name').value || 'Shadow';
                  root.getElementById('shadow-result').textContent = 'Shadow hello ' + value;
                  document.body.setAttribute('data-shadow-result', value);
                  console.log('automation:shadow-greeting:' + value);
                });
              }
            });
            customElements.define('closed-shadow-widget', class extends HTMLElement {
              connectedCallback() {
                if (this.__closedReady) return;
                this.__closedReady = true;
                var root = this.attachShadow({ mode: 'closed' });
                var marker = document.createElement('div');
                marker.textContent = 'closed-shadow-secret-should-not-leak';
                var button = document.createElement('button');
                button.textContent = 'Closed shadow hidden action';
                root.appendChild(marker);
                root.appendChild(button);
              }
            });
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun browserAutomationTestFrameHtml(): String = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>Kite Automation Frame</title>
          <style>
            body { margin: 0; padding: 16px; font-family: system-ui, sans-serif; background: #f8fafc; color: #172033; }
            label { display: block; margin: 0 0 6px; font-weight: 700; }
            input { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #b7c1d4; border-radius: 6px; font-size: 16px; }
            button { margin-top: 12px; padding: 10px 14px; border: 0; border-radius: 6px; background: #166d5a; color: white; font-weight: 800; }
            #frame-result { margin-top: 14px; padding: 12px; border: 1px solid #d5dae3; border-radius: 6px; background: white; min-height: 22px; }
            @media (prefers-color-scheme: dark) {
              body { background: #1b2029; color: #eef2f7; }
              input { background: #151922; color: #eef2f7; border-color: #3b4555; }
              #frame-result { background: #151922; border-color: #303846; }
            }
          </style>
        </head>
        <body>
          <main aria-label="same origin iframe automation">
            <label for="frame-name">Frame Name</label>
            <input id="frame-name" name="frame-name" placeholder="iframe input" autocomplete="off">
            <button id="frame-apply" type="button">Apply frame greeting</button>
            <div id="frame-result" role="status" aria-label="frame automation result">Frame waiting</div>
          </main>
          <script>
            document.getElementById('frame-apply').addEventListener('click', function () {
              var value = document.getElementById('frame-name').value || 'Frame';
              document.getElementById('frame-result').textContent = 'Frame hello ' + value;
              document.body.setAttribute('data-frame-result', value);
              console.log('automation:frame-greeting:' + value);
            });
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun BrowserAutomationSession.toPublicJson(): JSONObject =
        JSONObject()
            .put("sessionId", sessionId)
            .put("recipeId", recipeId.orEmpty())
            .put("recipeName", recipeName.orEmpty())
            .put("instanceId", instanceId.orEmpty())
            .put("source", source.orEmpty())
            .put("url", url)
            .put("mode", mode)
            .put("status", status.name)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("lastActionId", lastActionId.orEmpty())
            .put("lastSnapshotId", lastSnapshotId.orEmpty())
            .put("lastError", lastError.orEmpty())

    private fun BrowserAutomationSnapshot.toPublicJson(): JSONObject =
        JSONObject()
            .put("snapshotId", snapshotId)
            .put("sessionId", sessionId)
            .put("url", url)
            .put("title", title.orEmpty())
            .put("readyState", readyState.orEmpty())
            .put("text", text.take(1200))
            .put("elementCount", elementCount)
            .put("elements", JSONArray().apply {
                elements.take(20).forEach { put(it.toPublicJson()) }
            })
            .put("accessibility", JSONArray().apply {
                accessibility.take(40).forEach { put(it.toPublicJson()) }
            })
            .put("capturedAt", capturedAt)

    private fun BrowserAutomationAccessibilityNode.toPublicJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("role", role)
            .put("name", name)
            .put("tag", tag)
            .put("type", type.orEmpty())
            .put("level", level ?: JSONObject.NULL)
            .put("visible", visible)
            .put("enabled", enabled)
            .put("checked", checked.orEmpty())
            .put("selected", selected ?: JSONObject.NULL)
            .put("expanded", expanded ?: JSONObject.NULL)
            .put("x", x)
            .put("y", y)
            .put("width", width)
            .put("height", height)
            .put("framePath", framePath.orEmpty())
            .put("frameUrl", frameUrl.orEmpty())
            .put("frameName", frameName.orEmpty())
            .put("frameAccessible", frameAccessible ?: JSONObject.NULL)
            .put("shadowPath", shadowPath.orEmpty())
            .put("shadowHost", shadowHost.orEmpty())

    private fun BrowserAutomationElementSummary.toPublicJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("tag", tag)
            .put("type", type.orEmpty())
            .put("role", role.orEmpty())
            .put("text", text.orEmpty())
            .put("placeholder", placeholder.orEmpty())
            .put("ariaLabel", ariaLabel.orEmpty())
            .put("visible", visible)
            .put("enabled", enabled)
            .put("x", x)
            .put("y", y)
            .put("width", width)
            .put("height", height)
            .put("framePath", framePath.orEmpty())
            .put("frameUrl", frameUrl.orEmpty())
            .put("frameName", frameName.orEmpty())
            .put("shadowPath", shadowPath.orEmpty())
            .put("shadowHost", shadowHost.orEmpty())

    private fun BrowserAutomationConsoleEntry.toPublicJson(): JSONObject =
        JSONObject()
            .put("entryId", entryId)
            .put("sessionId", sessionId)
            .put("level", level)
            .put("message", message)
            .put("sourceId", sourceId.orEmpty())
            .put("lineNumber", lineNumber)
            .put("capturedAt", capturedAt)

    private fun BrowserAutomationNetworkEntry.toPublicJson(): JSONObject =
        JSONObject()
            .put("entryId", entryId)
            .put("sessionId", sessionId)
            .put("kind", kind)
            .put("method", method)
            .put("url", url)
            .put("isForMainFrame", isForMainFrame)
            .put("statusCode", statusCode ?: JSONObject.NULL)
            .put("reasonPhrase", reasonPhrase.orEmpty())
            .put("capturedAt", capturedAt)

    companion object {
        private const val DEFAULT_PORT = 8791
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val LATE_ACTION_RECONCILE_MS = 4_000L
        private const val LATE_ACTION_RECONCILE_POLL_MS = 100L
    }

    private data class HttpRequest(
        val requestLine: String,
        val body: String
    )
}
