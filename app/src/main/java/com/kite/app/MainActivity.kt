package com.kite.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.kite.app.bridge.BridgeErrorType
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.bridge.KiteLocalServer
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRunReport
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.web.KiteWebShell
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var webShell: KiteWebShell
    private lateinit var localServer: KiteLocalServer
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView

    private lateinit var nameInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var shortcutSwitch: Switch
    private lateinit var commandFieldContainer: View
    private lateinit var typeContainer: LinearLayout
    private lateinit var iconContainer: LinearLayout

    private val runtimeStates = mutableMapOf<String, RecipeRuntimeState>()
    private var currentScreen: Screen = Screen.Console
    private var currentRecipes: List<KiteRecipe> = emptyList()
    private var selectedType = KiteRecipe.TYPE_OPEN_URL
    private var selectedIconName = KiteRecipeIcon.defaultNameForType(KiteRecipe.TYPE_OPEN_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = KiteDiagnostics(this)
        diagnostics.writeCapabilityReport()
        recipeLoader = KiteRecipeLoader(this, diagnostics)
        bridgeClient = KiteBridgeClient(diagnostics)
        webView = WebView(this)
        webShell = KiteWebShell(this, webView, diagnostics) { }
        localServer = KiteLocalServer(diagnostics) { url -> runOnUiThread { openWeb(url, "endpoint") } }
        localServer.start()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        setContentView(root)
        showConsole()
    }

    override fun onDestroy() {
        localServer.stop()
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX Activity migration.")
    override fun onBackPressed() {
        if (currentScreen != Screen.Console) showConsole() else super.onBackPressed()
    }

    private fun showConsole() {
        currentScreen = Screen.Console
        currentRecipes = recipeLoader.loadAllRecipes()
        currentRecipes.forEach { recipe ->
            runtimeStates.putIfAbsent(recipe.id, RecipeRuntimeState.fromRecipeStatus(recipe.id, recipe.status))
        }
        root.removeAllViews()
        root.addView(consoleHeader())
        root.addView(recipeGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
    }

    private fun consoleHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(26), dp(24), dp(26), dp(12))
        addView(row {
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = "Kite"
                    textSize = 31f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(TEXT_DARK)
                })
                addView(TextView(context).apply {
                    text = "配置表控制台"
                    textSize = 14f
                    setTextColor(TEXT_MUTED)
                })
            })
            addView(iconButton("⌕", dp(48), Color.TRANSPARENT, TEXT_DARK, dp(18)) {
                Toast.makeText(context, "搜索稍后接入", Toast.LENGTH_SHORT).show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    setMargins(0, 0, dp(12), 0)
                }
            })
            addView(iconButton("+", dp(58), PURPLE, Color.WHITE, dp(20)) { showCreateConfig() })
        })

        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(20), 0, 0)
            addView(row {
                val opened = runtimeStates.values.count { it.status in RecipeRunStatus.activeStatuses }
                val stopped = runtimeStates.values.count {
                    it.status == RecipeRunStatus.Stopped || it.status == RecipeRunStatus.BridgeUnavailable
                }
                addView(chip("▦  全部 ${currentRecipes.size}", true))
                addView(chip("▶  已打开 $opened", false))
                addView(chip("■  已停止 $stopped", false))
            })
        })
    }

    private fun recipeGrid(): View {
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(18), dp(8), dp(18), dp(92))
            clipToPadding = false
        }
        currentRecipes.forEach { recipe ->
            grid.addView(recipeCard(recipe), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(190)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(10))
            })
        }
        scroll.addView(grid)
        return scroll
    }

    private fun recipeCard(recipe: KiteRecipe): View = LinearLayout(this).apply {
        val runtimeState = runtimeStateFor(recipe)
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(10))
        background = roundedBox(Color.WHITE, BORDER, dp(24).toFloat())
        elevation = dp(2).toFloat()

        addView(row {
            gravity = Gravity.TOP
            addView(iconTile(recipe.icon.name, accentFor(recipe), tintBackground(accentFor(recipe))))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(stateTag(runtimeState))
        })
        addView(cardTitle(recipe.name))
        addView(cardDescription(recipe.description.ifBlank { "打开本地工作台" }))
        addView(urlPill(recipe.defaultUrl, recipe.card.accent == "green"))
        runtimeState.failureSummary()?.let { addView(failureSummary(it)) }
        addView(row {
            addView(primaryAction(primaryLabel(recipe, runtimeState), recipe.card.accent == "green", runtimeState.isBusy()) {
                handleRecipeAction(recipe)
            })
            addView(editAction { showRecipeDetail(recipe) })
        })
    }

    private fun handleRecipeAction(recipe: KiteRecipe) {
        val state = runtimeStateFor(recipe)
        diagnostics.logRecipeAction(recipe, "card_click", mapOf("type" to recipe.type, "status" to state.status.name))
        if (state.isBusy()) return
        if (state.isActive()) {
            stopRecipe(recipe, state)
            return
        }

        when (recipe.type) {
            KiteRecipe.TYPE_OPEN_URL, KiteRecipe.TYPE_TEMPLATE -> {
                setRuntimeState(recipe, RecipeRunStatus.Opened, nextActionUrl = recipe.openWebUrl())
                openWeb(recipe.openWebUrl(), "recipe_card", recipe)
            }

            KiteRecipe.TYPE_COMMAND_WEB, KiteRecipe.TYPE_START_SERVICE, KiteRecipe.TYPE_SCRIPT_WEB -> {
                if (recipe.hasShellStep()) {
                    startRecipe(recipe, state)
                } else {
                    setRuntimeState(recipe, RecipeRunStatus.Opened, nextActionUrl = recipe.openWebUrl())
                    openWeb(recipe.openWebUrl(), "recipe_card", recipe)
                }
            }

            else -> {
                setRuntimeState(recipe, RecipeRunStatus.Failed, lastError = "unsupported_recipe_type")
                Toast.makeText(this, "暂不支持的配置类型", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecipe(recipe: KiteRecipe, previousState: RecipeRuntimeState) {
        if (previousState.status == RecipeRunStatus.Failed || previousState.status == RecipeRunStatus.BridgeUnavailable) {
            diagnostics.logLifecycleEvent(recipe, "retry", previousState.runId, previousState.pid, previousState.status.name)
        }
        if (previousState.hasRunBinding()) {
            diagnostics.logLifecycleEvent(
                recipe,
                "cleanup_skipped",
                previousState.runId,
                previousState.pid,
                previousState.status.name,
                previousState.lastMeaningfulOutput,
                previousState.lastError
            )
        }
        setRuntimeState(recipe, RecipeRunStatus.Starting)
        showConsole()
        bridgeClient.runRecipe(recipe) { result ->
            runOnUiThread { handleBridgeResult(recipe, result) }
        }
    }

    private fun stopRecipe(recipe: KiteRecipe, previousState: RecipeRuntimeState) {
        setRuntimeState(
            recipe,
            RecipeRunStatus.Stopping,
            runId = previousState.runId,
            pid = previousState.pid,
            lastMeaningfulOutput = previousState.lastMeaningfulOutput,
            nextActionUrl = previousState.nextActionUrl
        )
        showConsole()
        val callback: (BridgeResult) -> Unit = { result -> runOnUiThread { handleStopResultV2(recipe, previousState, result) } }
        diagnostics.logBridgeEvent(
            "stop_request_sent",
            recipe,
            mapOf(
                "runId" to previousState.runId.orEmpty(),
                "pid" to previousState.pid.orEmpty(),
                "strategy" to if (!previousState.runId.isNullOrBlank()) "stop-run" else "stop-recipe"
            )
        )
        if (!previousState.runId.isNullOrBlank()) {
            bridgeClient.stopRun(recipe, previousState.runId, callback)
        } else {
            diagnostics.logBridgeEvent(
                "stop_missing_run_id_fallback",
                recipe,
                mapOf("recipeId" to recipe.id, "message" to "missing runId, fallback to stop-recipe")
            )
            bridgeClient.stopRecipe(recipe, callback)
        }
    }

    private fun retryStopRequestAfterStableBridge(recipe: KiteRecipe, previousState: RecipeRuntimeState) {
        diagnostics.logBridgeEvent("stop_timeout_bridge_stable_retry", recipe, mapOf("runId" to previousState.runId.orEmpty()))
        val callback: (BridgeResult) -> Unit = { retryResult ->
            runOnUiThread { handleStopResultV2(recipe, previousState, retryResult, retriedAfterStableBridge = true) }
        }
        if (!previousState.runId.isNullOrBlank()) {
            bridgeClient.stopRun(recipe, previousState.runId, callback)
        } else {
            bridgeClient.stopRecipe(recipe, callback)
        }
    }

    private fun handleStopResultV2(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        result: BridgeResult,
        retriedAfterStableBridge: Boolean = false
    ) {
        val report = result.runReport
        if (report != null) diagnostics.writeRunReport(report)
        diagnostics.logBridgeEvent(
            "stop_response_parsed",
            recipe,
            mapOf(
                "requestId" to result.requestId.orEmpty(),
                "runId" to (report?.runId ?: previousState.runId).orEmpty(),
                "status" to result.status,
                "ok" to result.ok.toString(),
                "errorType" to result.errorType.name,
                "message" to result.message.take(500)
            )
        )
        if ((result.ok || result.accepted) && result.status == KiteRunReport.STATUS_STOPPED) {
            setRuntimeState(recipe, RecipeRunStatus.Stopped)
            diagnostics.logBridgeEvent("stop_success", recipe, mapOf("runId" to previousState.runId.orEmpty()))
            showConsole()
            return
        }

        if (result.errorType == BridgeErrorType.Timeout && !retriedAfterStableBridge) {
            diagnostics.logBridgeEvent("stop_timeout", recipe, mapOf("runId" to previousState.runId.orEmpty()))
            bridgeClient.checkStatus { status ->
                runOnUiThread {
                    if (status.ok || status.accepted) {
                        retryStopRequestAfterStableBridge(recipe, previousState)
                    } else {
                        diagnostics.logBridgeEvent("stop_connection_error", recipe, mapOf("message" to status.message.take(500)))
                        setRuntimeState(recipe, RecipeRunStatus.BridgeUnavailable, runId = previousState.runId, pid = previousState.pid, lastError = "Bridge 连接失败")
                        showConsole()
                    }
                }
            }
            return
        }

        val errorMessage = when (result.errorType) {
            BridgeErrorType.Timeout -> {
                diagnostics.logBridgeEvent("stop_timeout", recipe, mapOf("runId" to previousState.runId.orEmpty()))
                "停止超时，Bridge 未及时响应"
            }
            BridgeErrorType.ConnectionError -> {
                diagnostics.logBridgeEvent("stop_connection_error", recipe, mapOf("message" to result.message.take(500)))
                "Bridge 连接失败"
            }
            BridgeErrorType.UnsupportedEndpoint -> {
                diagnostics.logBridgeEvent("stop_unsupported_endpoint", recipe, mapOf("message" to result.message.take(500)))
                "停止接口暂不可用"
            }
            BridgeErrorType.ParseError -> {
                diagnostics.logBridgeEvent("stop_parse_error", recipe, mapOf("raw" to result.rawBody.take(1000)))
                "停止响应解析失败"
            }
            BridgeErrorType.BridgeFailed, BridgeErrorType.None -> {
                diagnostics.logBridgeEvent("stop_bridge_failed", recipe, mapOf("message" to result.message.take(500)))
                result.runReport?.lastMeaningfulOutput() ?: result.message.ifBlank { "Bridge 返回停止失败" }
            }
        }
        diagnostics.logLifecycleEvent(
            recipe,
            "stop_failed",
            previousState.runId,
            previousState.pid,
            previousState.status.name,
            previousState.lastMeaningfulOutput,
            errorMessage
        )
        runtimeStates[recipe.id] = previousState.copy(updatedAt = System.currentTimeMillis(), lastError = errorMessage)
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        showConsole()
    }

    private fun handleStopResult(recipe: KiteRecipe, previousState: RecipeRuntimeState, result: BridgeResult) {
        val report = result.runReport
        if (report != null) diagnostics.writeRunReport(report)
        if ((result.ok || result.accepted) && result.status == KiteRunReport.STATUS_STOPPED) {
            setRuntimeState(recipe, RecipeRunStatus.Stopped)
            showConsole()
            return
        }

        diagnostics.logLifecycleEvent(
            recipe,
            "stop_unavailable",
            previousState.runId,
            previousState.pid,
            previousState.status.name,
            previousState.lastMeaningfulOutput,
            result.message
        )
        runtimeStates[recipe.id] = previousState.copy(updatedAt = System.currentTimeMillis(), lastError = "停止接口暂不可用")
        Toast.makeText(this, "停止接口暂不可用", Toast.LENGTH_SHORT).show()
        showConsole()
    }

    private fun handleBridgeResult(recipe: KiteRecipe, result: BridgeResult) {
        val report = result.runReport
        if (report != null) diagnostics.writeRunReport(report)
        val requestId = (report?.requestId ?: result.requestId).orEmpty()
        val runId = report?.runId ?: result.requestId
        val lastOutput = report?.lastMeaningfulOutput()
        val pid = report?.pid ?: extractPid(lastOutput) ?: extractPid(result.message)

        if (result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE) {
            setRuntimeState(recipe, RecipeRunStatus.BridgeUnavailable, runId = runId, pid = pid, lastError = result.message)
            diagnostics.logRecipeAction(
                recipe,
                "bridge_unavailable",
                mapOf("requestId" to requestId, "message" to result.message.take(500))
            )
            Toast.makeText(this, "桥接不可用，未执行命令", Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        val nextUrl = report?.openWebUrlIfPresent() ?: result.nextActionUrl
        if (!nextUrl.isNullOrBlank()) {
            diagnostics.logRecipeAction(
                recipe,
                "next_action_detected",
                mapOf(
                    "requestId" to requestId,
                    "runId" to runId.orEmpty(),
                    "pid" to pid.orEmpty(),
                    "status" to (report?.status ?: result.status),
                    "ok" to (report?.ok ?: result.ok).toString(),
                    "url" to nextUrl,
                    "hasMismatch" to (report?.hasMismatch() == true).toString()
                )
            )
            if (report?.hasMismatch() == true) {
                diagnostics.logRecipeAction(recipe, "bridge_result_mismatch_warning", mapOf("requestId" to requestId, "url" to nextUrl))
            }
            val successStatus = successfulStatus(report, lastOutput)
            setRuntimeState(
                recipe,
                if (successStatus == RecipeRunStatus.AlreadyRunning) RecipeRunStatus.AlreadyRunning else RecipeRunStatus.Running,
                runId = runId,
                pid = pid,
                lastMeaningfulOutput = lastOutput,
                nextActionUrl = nextUrl
            )
            waitForWebReady(recipe, nextUrl, successStatus, runId, pid, lastOutput)
            return
        }

        if (report?.hasMismatch() == true) {
            setRuntimeState(recipe, RecipeRunStatus.Failed, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, lastError = "result_mismatch")
            diagnostics.logRecipeAction(recipe, "bridge_result_mismatch", mapOf("requestId" to requestId))
            Toast.makeText(this, "执行结果不匹配，已记录运行报告", Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        if (report != null && (!report.ok || report.status == KiteRunReport.STATUS_FAILED)) {
            setRuntimeState(recipe, RecipeRunStatus.Failed, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, lastError = result.message)
            diagnostics.logRecipeAction(recipe, "bridge_failed", mapOf("requestId" to requestId, "message" to result.message.take(500)))
            Toast.makeText(this, "执行失败，已记录运行报告", Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        if (result.ok || result.accepted) {
            setRuntimeState(recipe, successfulStatus(report, lastOutput), runId = runId, pid = pid, lastMeaningfulOutput = lastOutput)
            showConsole()
            return
        }

        setRuntimeState(recipe, RecipeRunStatus.BridgeUnavailable, runId = runId, pid = pid, lastError = result.message)
        Toast.makeText(this, "桥接不可用，未执行命令", Toast.LENGTH_SHORT).show()
        showConsole()
    }

    private fun waitForWebReady(
        recipe: KiteRecipe,
        url: String,
        finalStatus: RecipeRunStatus,
        runId: String?,
        pid: String?,
        lastOutput: String?
    ) {
        if (!shouldProbeWebReady(url)) {
            diagnostics.logBridgeEvent("open_web_after_ready", recipe, mapOf("url" to url, "mode" to "probe_skipped"))
            setRuntimeState(recipe, finalStatus, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, nextActionUrl = url)
            openWeb(url, "bridge_next_action", recipe)
            return
        }

        diagnostics.logBridgeEvent("web_ready_probe_start", recipe, mapOf("url" to url, "runId" to runId.orEmpty()))
        thread(name = "KiteWebReadyProbe", isDaemon = true) {
            val deadline = System.currentTimeMillis() + WEB_READY_TIMEOUT_MS
            var attempt = 0
            var ready = false
            var lastError = ""
            while (System.currentTimeMillis() < deadline && !ready) {
                attempt += 1
                val result = runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = WEB_READY_CONNECT_TIMEOUT_MS
                    connection.readTimeout = WEB_READY_READ_TIMEOUT_MS
                    connection.requestMethod = "GET"
                    connection.instanceFollowRedirects = false
                    val code = connection.responseCode
                    connection.disconnect()
                    code
                }
                ready = result.isSuccess
                lastError = result.exceptionOrNull()?.message.orEmpty()
                if (!ready) Thread.sleep(WEB_READY_INTERVAL_MS)
            }
            runOnUiThread {
                if (ready) {
                    diagnostics.logBridgeEvent("web_ready_probe_success", recipe, mapOf("url" to url, "attempts" to attempt.toString()))
                    diagnostics.logBridgeEvent("open_web_after_ready", recipe, mapOf("url" to url, "runId" to runId.orEmpty()))
                    setRuntimeState(recipe, finalStatus, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, nextActionUrl = url)
                    openWeb(url, "bridge_next_action_ready", recipe)
                } else {
                    diagnostics.logBridgeEvent(
                        "web_ready_probe_timeout",
                        recipe,
                        mapOf("url" to url, "attempts" to attempt.toString(), "lastError" to lastError.take(500))
                    )
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.Running,
                        runId = runId,
                        pid = pid,
                        lastMeaningfulOutput = lastOutput,
                        lastError = "服务启动中，网页暂不可用",
                        nextActionUrl = url
                    )
                    Toast.makeText(this, "服务启动中，网页暂不可用", Toast.LENGTH_SHORT).show()
                    showConsole()
                }
            }
        }
    }

    private fun shouldProbeWebReady(url: String): Boolean =
        runCatching {
            val parsed = URL(url)
            parsed.protocol.equals("http", ignoreCase = true) &&
                (parsed.host == "127.0.0.1" || parsed.host.equals("localhost", ignoreCase = true))
        }.getOrDefault(false)

    private fun successfulStatus(report: KiteRunReport?, output: String?): RecipeRunStatus =
        if (report?.status == KiteRunReport.STATUS_ALREADY_RUNNING || output.orEmpty().contains("already_running", true)) {
            RecipeRunStatus.AlreadyRunning
        } else if (report?.status == KiteRunReport.STATUS_RUNNING || report?.status == KiteRunReport.STATUS_ACCEPTED) {
            RecipeRunStatus.Running
        } else {
            RecipeRunStatus.Opened
        }

    private fun extractPid(text: String?): String? {
        val value = text ?: return null
        val match = Regex("""pid\s*[:=]\s*(\d+)|pid\s+(\d+)""", RegexOption.IGNORE_CASE).find(value) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
    }

    private fun runtimeStateFor(recipe: KiteRecipe): RecipeRuntimeState =
        runtimeStates[recipe.id] ?: RecipeRuntimeState.fromRecipeStatus(recipe.id, recipe.status).also {
            runtimeStates[recipe.id] = it
        }

    private fun setRuntimeState(
        recipe: KiteRecipe,
        status: RecipeRunStatus,
        runId: String? = null,
        pid: String? = null,
        lastMeaningfulOutput: String? = null,
        lastError: String? = null,
        nextActionUrl: String? = null
    ) {
        runtimeStates[recipe.id] = RecipeRuntimeState(
            recipeId = recipe.id,
            status = status,
            runId = runId,
            pid = pid,
            lastMeaningfulOutput = lastMeaningfulOutput,
            lastError = lastError,
            nextActionUrl = nextActionUrl
        )
        diagnostics.logLifecycleEvent(recipe, status.lifecycleEvent, runId, pid, status.name, lastMeaningfulOutput, lastError)
    }

    private fun showCreateConfig() {
        currentScreen = Screen.CreateConfig
        selectedType = KiteRecipe.TYPE_OPEN_URL
        selectedIconName = KiteRecipeIcon.defaultNameForType(selectedType)
        root.removeAllViews()
        root.addView(createTopBar())
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(132))
                addView(sectionTitle("1. 类型"))
                typeContainer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                addView(typeContainer)
                renderTypeOptions()
                addView(sectionTitle("2. 基础信息"))
                addView(formPanel())
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomActions())
    }

    private fun renderTypeOptions() {
        typeContainer.removeAllViews()
        val options = listOf(
            TypeOption(KiteRecipe.TYPE_OPEN_URL, "web", "打开网页"),
            TypeOption(KiteRecipe.TYPE_START_SERVICE, "server", "启动服务"),
            TypeOption(KiteRecipe.TYPE_COMMAND_WEB, "terminal", "命令+网页"),
            TypeOption(KiteRecipe.TYPE_TEMPLATE, "tools", "模板")
        )
        options.forEachIndexed { index, option ->
            typeContainer.addView(optionCard(option, selectedType == option.type, index != options.lastIndex))
        }
    }

    private fun formPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(20))
        background = roundedBox(Color.WHITE, BORDER, dp(28).toFloat())
        elevation = dp(1).toFloat()

        nameInput = editInput("例如：Hermes WebUI")
        urlInput = editInput("例如：http://127.0.0.1:8648")
        commandInput = editInput("例如：hermes-web-ui start --port 8648")

        addView(labeledField("名称", nameInput))
        commandFieldContainer = labeledField("命令", commandInput).apply {
            visibility = if (selectedType.requiresServiceCommand()) View.VISIBLE else View.GONE
        }
        addView(commandFieldContainer)
        addView(labeledField("地址", urlInput))
        addView(iconChooser())
        addView(toggleRow())
        addView(divider())
        addView(navigationRow("高级设置（可选）"))
    }

    private fun iconChooser(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(18))
        addView(TextView(context).apply {
            text = "图标"
            textSize = 15f
            setTextColor(TEXT_DARK)
        })
        iconContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(iconContainer)
        })
        renderIconOptions()
    }

    private fun renderIconOptions() {
        if (!::iconContainer.isInitialized) return
        iconContainer.removeAllViews()
        listOf("web", "terminal", "bot", "file", "tools").forEach { iconName ->
            iconContainer.addView(iconChip(iconName, selectedIconName == iconName))
        }
    }

    private fun saveNewRecipe() {
        val name = nameInput.text?.toString().orEmpty().trim()
        val url = urlInput.text?.toString().orEmpty().trim()
        val command = commandInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (url.isBlank()) {
            Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedType.requiresServiceCommand() && command.isBlank()) {
            Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            recipeLoader.saveUserRecipe(
                NewRecipeInput(
                    type = selectedType,
                    name = name,
                    url = url,
                    command = command,
                    shortcut = shortcutSwitch.isChecked,
                    iconName = selectedIconName
                )
            )
        }.onSuccess {
            Toast.makeText(this, "已保存配置", Toast.LENGTH_SHORT).show()
            showConsole()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecipeDetail(recipe: KiteRecipe) {
        currentScreen = Screen.RecipeDetail
        root.removeAllViews()
        root.addView(topBar("配置详情") { showConsole() })
        root.addView(ScrollView(this).apply {
            addView(TextView(context).apply {
                text = recipe.toJson().toString(2)
                textSize = 14f
                setTextColor(TEXT_DARK)
                setPadding(dp(24), dp(20), dp(24), dp(28))
                typeface = Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun openWeb(url: String, source: String, recipe: KiteRecipe? = null) {
        val target = url.trim().ifBlank { DEFAULT_LOCAL_URL }
        diagnostics.logOpenWebAttempt(recipe, target, source)
        diagnostics.writeWebAppStatus(
            url = target,
            title = recipe?.name,
            state = "opening",
            recipeId = recipe?.id,
            recipeName = recipe?.name,
            openSource = source
        )
        runCatching {
            showWorkbench(target, source, recipe)
        }.onFailure {
            diagnostics.logOpenWebFailed(recipe, target, it.message.orEmpty())
            Toast.makeText(this, "打开工作台失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWorkbench(url: String, source: String, recipe: KiteRecipe?) {
        currentScreen = Screen.Workbench
        root.removeAllViews()
        root.addView(topBar("Kite 工作台") { showConsole() })
        val parent = webView.parent
        if (parent is ViewGroup) parent.removeView(webView)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webShell.open(url, recipeId = recipe?.id, recipeName = recipe?.name, openSource = source)
    }

    private fun createTopBar(): View = row {
        setPadding(dp(24), dp(22), dp(24), dp(14))
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton("‹", dp(44), Color.TRANSPARENT, TEXT_DARK, dp(16)) { showConsole() })
        addView(TextView(context).apply {
            text = "新建配置"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(TEXT_DARK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(iconButton("✓", dp(44), Color.TRANSPARENT, PURPLE, dp(16)) { saveNewRecipe() })
    }

    private fun topBar(title: String, onBack: () -> Unit): View = row {
        setPadding(dp(18), dp(14), dp(18), dp(10))
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton("‹", dp(44), Color.TRANSPARENT, TEXT_DARK, dp(16)) { onBack() })
        addView(TextView(context).apply {
            text = title
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_DARK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(View(context), LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(TEXT_DARK)
        setPadding(0, dp(30), 0, dp(14))
    }

    private fun optionCard(option: TypeOption, selected: Boolean, hasRightMargin: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = roundedBox(Color.WHITE, if (selected) PURPLE else BORDER, dp(18).toFloat(), dp(if (selected) 2 else 1))
            layoutParams = LinearLayout.LayoutParams(0, dp(98), 1f).apply {
                if (hasRightMargin) setMargins(0, 0, dp(10), 0)
            }
            addView(TextView(context).apply {
                text = iconGlyph(option.icon)
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (selected) PURPLE else TEXT_MUTED)
            })
            addView(TextView(context).apply {
                text = option.label
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(if (selected) PURPLE else TEXT_DARK)
                setPadding(0, dp(8), 0, 0)
            })
            setOnClickListener {
                selectedType = option.type
                selectedIconName = KiteRecipeIcon.defaultNameForType(selectedType)
                renderTypeOptions()
                renderIconOptions()
                if (::commandFieldContainer.isInitialized) {
                    commandFieldContainer.visibility = if (selectedType.requiresServiceCommand()) View.VISIBLE else View.GONE
                }
            }
        }

    private fun iconChip(iconName: String, selected: Boolean): TextView = TextView(this).apply {
        text = iconGlyph(iconName)
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(if (selected) PURPLE else TEXT_MUTED)
        background = roundedBox(if (selected) Color.rgb(244, 240, 255) else Color.WHITE, if (selected) PURPLE else BORDER, dp(14).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(38)).apply { setMargins(0, 0, dp(8), 0) }
        setOnClickListener {
            selectedIconName = iconName
            renderIconOptions()
        }
    }

    private fun labeledField(label: String, input: EditText): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(18))
        addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(TEXT_DARK)
        })
        addView(input)
    }

    private fun editInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setSingleLine(true)
        setTextColor(TEXT_DARK)
        setHintTextColor(Color.rgb(148, 163, 184))
        setPadding(dp(14), 0, dp(14), 0)
        background = roundedBox(Color.WHITE, BORDER, dp(16).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            setMargins(0, dp(8), 0, 0)
        }
    }

    private fun toggleRow(): View = row {
        setPadding(0, dp(10), 0, dp(16))
        addView(TextView(context).apply {
            text = "创建快捷方式到桌面"
            textSize = 16f
            setTextColor(TEXT_DARK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        shortcutSwitch = Switch(context).apply { isChecked = false }
        addView(shortcutSwitch)
    }

    private fun navigationRow(label: String): View = row {
        setPadding(0, dp(20), 0, 0)
        addView(TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(TEXT_DARK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = "›"
            textSize = 28f
            setTextColor(TEXT_MUTED)
        })
    }

    private fun bottomActions(): View = row {
        setPadding(dp(24), dp(16), dp(24), dp(24))
        setBackgroundColor(Color.WHITE)
        addView(TextView(context).apply {
            text = "取消"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(TEXT_DARK)
            background = roundedBox(Color.rgb(241, 245, 249), Color.rgb(241, 245, 249), dp(19).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(62), 0.9f).apply { setMargins(0, 0, dp(18), 0) }
            setOnClickListener { showConsole() }
        })
        addView(TextView(context).apply {
            text = "保存"
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBox(PURPLE, PURPLE, dp(19).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(62), 1.1f)
            setOnClickListener { saveNewRecipe() }
        })
    }

    private fun bottomNavigation(): View = row {
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setBackgroundColor(Color.WHITE)
        addView(navItem("▦", "配置", true))
        addView(navItem("▤", "模板", false))
        addView(navItem("⌁", "活动", false))
        addView(navItem("⚙", "设置", false))
    }

    private fun navItem(icon: String, label: String, selected: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f)
        addView(TextView(context).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (selected) PURPLE else TEXT_MUTED)
        })
        addView(TextView(context).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) PURPLE else TEXT_MUTED)
        })
    }

    private fun row(content: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        content()
    }

    private fun iconButton(text: String, size: Int, fill: Int, textColor: Int, radius: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = if (text == "+") 30f else 28f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = roundedBox(fill, fill, radius.toFloat())
            if (fill != Color.TRANSPARENT) elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }

    private fun iconTile(iconName: String, tint: Int, fill: Int): TextView = TextView(this).apply {
        text = iconGlyph(iconName)
        textSize = 20f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tint)
        background = roundedBox(fill, tintBackgroundBorder(tint), dp(14).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }

    private fun iconGlyph(iconName: String): String = when (iconName) {
        "terminal" -> ">_"
        "web" -> "◎"
        "bot" -> "AI"
        "file" -> "文"
        "music" -> "♪"
        "shopping" -> "购"
        "logs" -> "日"
        "tools" -> "⚙"
        "code" -> "{ }"
        "server" -> "▷"
        else -> "◎"
    }

    private fun stateTag(state: RecipeRuntimeState): TextView = TextView(this).apply {
        text = state.status.label
        textSize = 10.5f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(state.status.textColor)
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background = roundedBox(state.status.bgColor, state.status.borderColor, dp(14).toFloat())
    }

    private fun cardTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(TEXT_DARK)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(8), 0, 0)
    }

    private fun cardDescription(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        includeFontPadding = false
        setTextColor(TEXT_MUTED)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(5), 0, 0)
    }

    private fun failureSummary(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 10.5f
        includeFontPadding = false
        setTextColor(Color.rgb(185, 28, 28))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, 0, 0, dp(5))
    }

    private fun urlPill(url: String, active: Boolean): TextView = TextView(this).apply {
        text = url
        textSize = 10.5f
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(if (active) STATUS_GREEN else BLUE)
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background = roundedBox(
            if (active) Color.rgb(232, 248, 238) else Color.rgb(239, 246, 255),
            if (active) Color.rgb(190, 234, 205) else Color.rgb(191, 219, 254),
            dp(10).toFloat()
        )
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(6), 0, dp(7)) }
    }

    private fun primaryAction(text: String, green: Boolean, disabled: Boolean = false, onClick: () -> Unit): View =
        TextView(this).apply {
            this.text = text
            textSize = 11.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            alpha = if (disabled) 0.62f else 1f
            background = roundedBox(if (green) STATUS_GREEN else BLUE, if (green) STATUS_GREEN else BLUE, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(0, 0, dp(6), 0) }
            isEnabled = !disabled
            if (!disabled) setOnClickListener { onClick() }
        }

    private fun editAction(onClick: () -> Unit): View = TextView(this).apply {
        text = "编辑"
        textSize = 11.5f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(TEXT_DARK)
        background = roundedBox(Color.WHITE, BORDER, dp(12).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(30))
        setOnClickListener { onClick() }
    }

    private fun chip(text: String, selected: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (selected) Color.WHITE else TEXT_DARK)
        setPadding(dp(18), dp(9), dp(18), dp(9))
        background = roundedBox(if (selected) PURPLE else Color.WHITE, if (selected) PURPLE else BORDER, dp(24).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, dp(10), 0) }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(BORDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun roundedBox(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = dp(1)): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(strokeWidth, stroke)
        }

    private fun tintBackground(color: Int): Int = Color.rgb(
        Color.red(color) + ((255 - Color.red(color)) * 0.88).toInt(),
        Color.green(color) + ((255 - Color.green(color)) * 0.88).toInt(),
        Color.blue(color) + ((255 - Color.blue(color)) * 0.88).toInt()
    )

    private fun tintBackgroundBorder(color: Int): Int = Color.rgb(
        Color.red(color) + ((255 - Color.red(color)) * 0.72).toInt(),
        Color.green(color) + ((255 - Color.green(color)) * 0.72).toInt(),
        Color.blue(color) + ((255 - Color.blue(color)) * 0.72).toInt()
    )

    private fun accentFor(recipe: KiteRecipe): Int = when (recipe.card.accent) {
        "green" -> STATUS_GREEN
        "purple" -> PURPLE
        "orange" -> ORANGE
        else -> BLUE
    }

    private fun primaryLabel(recipe: KiteRecipe, state: RecipeRuntimeState): String = when (state.status) {
        RecipeRunStatus.Starting -> "启动中"
        RecipeRunStatus.Stopping -> "停止中"
        RecipeRunStatus.Running, RecipeRunStatus.AlreadyRunning, RecipeRunStatus.Opened -> "停止"
        RecipeRunStatus.Failed, RecipeRunStatus.BridgeUnavailable -> "重试"
        RecipeRunStatus.Unknown, RecipeRunStatus.Stopped -> {
            if (recipe.type == KiteRecipe.TYPE_OPEN_URL || recipe.type == KiteRecipe.TYPE_TEMPLATE) "打开" else "启动"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class TypeOption(val type: String, val icon: String, val label: String)

    private enum class Screen {
        Console,
        Workbench,
        RecipeDetail,
        CreateConfig
    }

    private data class RecipeRuntimeState(
        val recipeId: String,
        val status: RecipeRunStatus,
        val runId: String? = null,
        val pid: String? = null,
        val lastMeaningfulOutput: String? = null,
        val lastError: String? = null,
        val nextActionUrl: String? = null,
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        fun isBusy(): Boolean = status == RecipeRunStatus.Starting || status == RecipeRunStatus.Stopping
        fun isActive(): Boolean = status in RecipeRunStatus.activeStatuses
        fun hasRunBinding(): Boolean = !runId.isNullOrBlank() || !pid.isNullOrBlank()
        fun failureSummary(): String? = when (status) {
            RecipeRunStatus.Failed -> lastError ?: lastMeaningfulOutput
            RecipeRunStatus.BridgeUnavailable -> lastError ?: "桥接不可用"
            else -> null
        }?.take(80)

        companion object {
            fun fromRecipeStatus(recipeId: String, status: String): RecipeRuntimeState =
                RecipeRuntimeState(recipeId = recipeId, status = RecipeRunStatus.fromRecipeStatus(status))
        }
    }

    private enum class RecipeRunStatus(
        val label: String,
        val textColor: Int,
        val bgColor: Int,
        val borderColor: Int,
        val lifecycleEvent: String
    ) {
        Unknown("未启动", Color.rgb(71, 85, 105), Color.rgb(248, 250, 252), BORDER, "unknown"),
        Stopped("未启动", Color.rgb(71, 85, 105), Color.rgb(248, 250, 252), BORDER, "stopped"),
        Starting("启动中", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205), "starting"),
        Running("运行中", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205), "running"),
        AlreadyRunning("已运行", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205), "already_running"),
        Opened("已打开", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205), "opened"),
        Failed("启动失败", Color.rgb(185, 28, 28), Color.rgb(254, 242, 242), Color.rgb(254, 202, 202), "failed"),
        Stopping("停止中", ORANGE, Color.rgb(255, 247, 237), Color.rgb(254, 215, 170), "stopping"),
        BridgeUnavailable("桥接不可用", Color.rgb(185, 28, 28), Color.rgb(254, 242, 242), Color.rgb(254, 202, 202), "bridge_unavailable");

        companion object {
            val activeStatuses = setOf(Running, AlreadyRunning, Opened)

            fun fromRecipeStatus(status: String): RecipeRunStatus = when (status) {
                "opened" -> Opened
                "running" -> Running
                "already_running" -> AlreadyRunning
                "failed" -> Failed
                "stopped" -> Stopped
                else -> Unknown
            }
        }
    }

    private fun String.requiresServiceCommand(): Boolean =
        this == KiteRecipe.TYPE_COMMAND_WEB || this == KiteRecipe.TYPE_START_SERVICE

    companion object {
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private const val WEB_READY_TIMEOUT_MS = 8000L
        private const val WEB_READY_INTERVAL_MS = 700L
        private const val WEB_READY_CONNECT_TIMEOUT_MS = 700
        private const val WEB_READY_READ_TIMEOUT_MS = 700
        private val BG = Color.rgb(248, 250, 252)
        private val TEXT_DARK = Color.rgb(15, 23, 42)
        private val TEXT_MUTED = Color.rgb(100, 116, 139)
        private val BORDER = Color.rgb(226, 232, 240)
        private val PURPLE = Color.rgb(109, 67, 230)
        private val STATUS_GREEN = Color.rgb(5, 150, 105)
        private val BLUE = Color.rgb(37, 99, 235)
        private val ORANGE = Color.rgb(234, 88, 12)
    }
}
