package com.kite.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
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
import com.kite.app.dropzone.DropZoneStatus
import com.kite.app.dropzone.KiteDropZoneManager
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeCard
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.KiteRunReport
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.recipe.NewRecipeStepInput
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.ThemeTokens
import com.kite.app.web.KiteWebShell
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var dropZoneManager: KiteDropZoneManager
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var webShell: KiteWebShell
    private lateinit var localServer: KiteLocalServer
    private lateinit var themeStore: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView

    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var workdirInput: EditText
    private lateinit var expectedInput: EditText
    private lateinit var shortcutSwitch: Switch
    private lateinit var commandFieldContainer: View
    private lateinit var urlFieldContainer: View
    private lateinit var workdirFieldContainer: View
    private lateinit var expectedFieldContainer: View
    private lateinit var runModeFieldContainer: View
    private lateinit var typeContainer: LinearLayout
    private lateinit var iconContainer: LinearLayout
    private lateinit var runModeContainer: LinearLayout
    private lateinit var stepsContainer: LinearLayout

    private val runtimeStates = mutableMapOf<String, RecipeRuntimeState>()
    private var currentScreen: Screen = Screen.Console
    private var currentRecipes: List<KiteRecipe> = emptyList()
    private var selectedType = KiteRecipe.TYPE_OPEN_URL
    private var selectedIconName = KiteRecipeIcon.defaultNameForType(KiteRecipe.TYPE_OPEN_URL)
    private var selectedRunMode = KiteRecipe.RUN_MODE_DETACHED
    private var editingRecipe: KiteRecipe? = null
    private var dropZoneStatus: DropZoneStatus = DropZoneStatus(available = false, message = "投放区尚未检查")
    private var isDropZoneRefreshing = false
    private var themeConfig = ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
    private var tokens = KiteTheme.resolve(themeConfig)
    private val formSteps = mutableListOf<RecipeStepDraft>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = KiteDiagnostics(this)
        diagnostics.writeCapabilityReport()
        themeStore = getSharedPreferences("kite_theme", MODE_PRIVATE)
        themeConfig = loadThemeConfig()
        tokens = KiteTheme.resolve(themeConfig)
        recipeLoader = KiteRecipeLoader(this, diagnostics)
        dropZoneManager = KiteDropZoneManager(this, diagnostics)
        dropZoneStatus = dropZoneManager.prepareDropZone()
        bridgeClient = KiteBridgeClient(diagnostics)
        webView = WebView(this)
        webShell = KiteWebShell(this, webView, diagnostics) { }
        localServer = KiteLocalServer(diagnostics) { url -> runOnUiThread { openWeb(url, "endpoint") } }
        localServer.start()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tokens.pageBackground)
        }
        setContentView(root)
        showConsole()
        if (!dropZoneStatus.available) {
            Toast.makeText(this, dropZoneStatus.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        localServer.stop()
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX Activity migration.")
    override fun onBackPressed() {
        when (currentScreen) {
            Screen.ThemeSettings -> showSettings()
            Screen.Settings -> showConsole()
            else -> if (currentScreen != Screen.Console) showConsole() else super.onBackPressed()
        }
    }

    private fun loadThemeConfig(): ThemeConfig =
        ThemeConfig(
            themeColor = themeStore.getInt("theme_color", KiteTheme.defaultThemeColor),
            backgroundColor = themeStore.getInt("background_color", KiteTheme.defaultBackgroundColor)
        )

    private fun saveThemeConfig(config: ThemeConfig) {
        themeConfig = config
        tokens = KiteTheme.resolve(config)
        themeStore.edit()
            .putInt("theme_color", config.themeColor)
            .putInt("background_color", config.backgroundColor)
            .apply()
        if (::root.isInitialized) root.setBackgroundColor(tokens.pageBackground)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_DROPZONE_STORAGE) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            diagnostics.logDropZoneEvent(
                if (granted) "dropzone_permission_granted" else "dropzone_permission_missing",
                path = dropZoneStatus.rootPath,
                reason = if (granted) "" else "storage_permission_denied"
            )
            dropZoneStatus = dropZoneManager.prepareDropZone()
            Toast.makeText(this, dropZoneStatus.message, Toast.LENGTH_SHORT).show()
            if (currentScreen == Screen.Console) showConsole()
        }
    }

    private fun refreshDropZoneRecipes() {
        if (isDropZoneRefreshing) {
            Toast.makeText(this, "正在刷新配置，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        isDropZoneRefreshing = true
        if (currentScreen == Screen.Console) showConsole()
        Toast.makeText(this, "正在刷新 Kite 投放区", Toast.LENGTH_SHORT).show()
        thread {
            val result = dropZoneManager.scanAndImport()
            runOnUiThread {
                isDropZoneRefreshing = false
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                showConsole()
            }
        }
    }

    private fun requestDropZoneAccess() {
        if (Build.VERSION.SDK_INT <= 32 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_DROPZONE_STORAGE)
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun showConsole() {
        currentScreen = Screen.Console
        dropZoneStatus = dropZoneManager.prepareDropZone()
        currentRecipes = recipeLoader.loadAllRecipes()
        currentRecipes.forEach { recipe ->
            runtimeStates.putIfAbsent(recipe.id, RecipeRuntimeState.fromRecipeStatus(recipe.id, recipe.status))
        }
        root.setBackgroundColor(tokens.pageBackground)
        root.removeAllViews()
        root.addView(consoleHeader())
        root.addView(recipeGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
    }

    private fun showSettings() {
        currentScreen = Screen.Settings
        root.setBackgroundColor(tokens.pageBackground)
        root.removeAllViews()
        root.addView(topBar("设置") { showConsole() })
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(18), dp(22), dp(96))
                addView(settingsRow("主题", "主题色、背景色和卡片色彩") { showThemeSettings() })
                addView(settingsRow("投放区", dropZoneStatus.message) {
                    if (dropZoneStatus.available) refreshDropZoneRecipes() else requestDropZoneAccess()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(12), 0, 0) }
                })
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
    }

    private fun showThemeSettings() {
        currentScreen = Screen.ThemeSettings
        root.setBackgroundColor(tokens.pageBackground)
        root.removeAllViews()
        root.addView(topBar("主题") { showSettings() })
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(18), dp(22), dp(96))
                addView(sectionTitle("主题色"))
                addView(colorPresetRow(
                    KiteTheme.themeColorChoices.map { it.label to it.color },
                    themeConfig.themeColor
                ) { color ->
                    saveThemeConfig(themeConfig.copy(themeColor = color))
                    showThemeSettings()
                })
                addView(sectionTitle("背景色").apply { setPadding(0, dp(24), 0, dp(16)) })
                addView(colorPresetRow(
                    KiteTheme.backgroundColorChoices.map { it.label to it.color },
                    themeConfig.backgroundColor
                ) { color ->
                    saveThemeConfig(themeConfig.copy(backgroundColor = color))
                    showThemeSettings()
                })
                addView(themePreviewCard())
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
    }

    private fun consoleHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(24), dp(18), dp(12))
        addView(row {
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = "Kite"
                    textSize = 31f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = "配置表控制台"
                    textSize = 14f
                    setTextColor(tokens.textSecondary)
                })
            })
            addView(iconButton("⌕", dp(62), Color.TRANSPARENT, tokens.textPrimary, dp(18)) {
                Toast.makeText(context, "搜索稍后接入", Toast.LENGTH_SHORT).show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(62), dp(62)).apply {
                    setMargins(0, -dp(3), dp(8), 0)
                }
            })
            addView(iconButton("+", dp(50), tokens.primaryStrong, tokens.buttonText, dp(18)) { showCreateConfig() }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                    setMargins(0, -dp(6), 0, 0)
                }
            })
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
        addView(dropZoneControlRow())
    }

    private fun dropZoneControlRow(): View = row {
        setPadding(0, dp(12), 0, 0)
        addView(TextView(context).apply {
            text = if (dropZoneStatus.available) {
                "投放区：Download/Kite/recipes"
            } else {
                dropZoneStatus.message
            }
            textSize = 12f
            setTextColor(if (dropZoneStatus.available) tokens.textSecondary else tokens.danger)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(dropZoneButton(if (isDropZoneRefreshing) "刷新中..." else "刷新配置") { refreshDropZoneRecipes() }.apply {
            isEnabled = !isDropZoneRefreshing
            alpha = if (isDropZoneRefreshing) 0.62f else 1f
        })
        if (!dropZoneStatus.available) {
            addView(dropZoneButton("授权") { requestDropZoneAccess() }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                    setMargins(dp(8), 0, 0, 0)
                }
            })
        }
    }

    private fun settingsRow(title: String, subtitle: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(16), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            elevation = dp(1).toFloat()
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = title
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = subtitle
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(tokens.textTertiary)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(42))
            })
            setOnClickListener { onClick() }
        }

    private fun colorPresetRow(
        options: List<Pair<String, Int>>,
        selectedColor: Int,
        onSelect: (Int) -> Unit
    ): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row {
            options.forEach { (label, color) ->
                addView(colorPresetChip(label, color, color == selectedColor) { onSelect(color) })
            }
        })
    }

    private fun colorPresetChip(label: String, color: Int, selected: Boolean, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(12), 0)
            background = roundedBox(
                if (selected) tokens.primarySubtle else tokens.surface,
                if (selected) tokens.primaryStrong else tokens.border,
                dp(18).toFloat(),
                dp(if (selected) 2 else 1)
            )
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                setMargins(0, 0, dp(10), 0)
            }
            addView(View(context).apply {
                background = roundedBox(color, color, dp(9).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    setMargins(0, 0, dp(8), 0)
                }
            })
            addView(TextView(context).apply {
                text = label
                textSize = 12.5f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) tokens.primaryText else tokens.textSecondary)
            })
            setOnClickListener { onClick() }
        }

    private fun themePreviewCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBox(tokens.cardBackground, tokens.border, dp(24).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(26), 0, 0)
        }
        addView(row {
            addView(iconTile("terminal", tokens.primaryStrong, tokens.primarySoft))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(context).apply {
                    text = "主题预览"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = "按钮、卡片和辅助信息会跟随这里的颜色。"
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(TextView(context).apply {
            text = "启动 / 打开"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.buttonText)
            background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                setMargins(0, dp(16), 0, 0)
            }
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
                height = dp(170)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(10))
            })
        }
        scroll.addView(grid)
        return scroll
    }

    private fun recipeCard(recipe: KiteRecipe): View = LinearLayout(this).apply {
        val runtimeState = runtimeStateFor(recipe)
        val accentName = displayAccentName(recipe)
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(10))
        background = roundedBox(tokens.cardBackground, tokens.border, dp(24).toFloat())
        elevation = dp(2).toFloat()

        addView(row {
            gravity = Gravity.TOP
            addView(iconTile(recipe.icon.name, accentFor(recipe), tintBackground(accentFor(recipe))))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(stateTag(runtimeState))
        })
        addView(cardTitle(recipe.name))
        addView(cardInfoSlot(recipe, runtimeState, accentName))
        addView(View(context), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(row {
            addView(primaryAction(primaryLabel(recipe, runtimeState), accentName, runtimeState.isBusy()) {
                handleRecipeAction(recipe)
            })
            addView(editAction { showRecipeEditor(recipe) })
        })
    }

    private fun cardInfoSlot(recipe: KiteRecipe, runtimeState: RecipeRuntimeState, accentName: String): View =
        LinearLayout(this).apply {
            val isProblem = runtimeState.status == RecipeRunStatus.Failed || runtimeState.status == RecipeRunStatus.BridgeUnavailable
            val feedback = runtimeState.feedbackSummary()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.START
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32))
                .apply { setMargins(0, dp(6), 0, dp(4)) }

            when {
                isProblem && !feedback.isNullOrBlank() -> addView(runtimeFeedback(feedback, true))
                recipe.defaultUrl.isNotBlank() -> addView(urlPill(recipe.defaultUrl, accentName))
                !feedback.isNullOrBlank() -> addView(runtimeFeedback(feedback, false))
            }
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

    private fun showCreateConfig() = showRecipeForm(null)

    private fun showRecipeEditor(recipe: KiteRecipe) = showRecipeForm(recipe)

    private fun showRecipeForm(recipe: KiteRecipe?) {
        currentScreen = Screen.CreateConfig
        editingRecipe = recipe
        formSteps.clear()
        formSteps.addAll(recipe?.steps?.map { RecipeStepDraft.fromStep(it) } ?: emptyList())
        selectedType = recipe?.let { inferTypeFromDrafts() } ?: KiteRecipe.TYPE_COMMAND_WEB
        selectedIconName = recipe?.icon?.name?.ifBlank { null } ?: KiteRecipeIcon.defaultNameForType(selectedType)
        selectedRunMode = recipe?.firstShellStep()?.runMode?.let { KiteRecipe.normalizeRunMode(it, null) } ?: defaultRunModeForType(selectedType)
        root.removeAllViews()
        root.addView(createTopBar(if (recipe == null) "新建配置" else "编辑配置"))
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(30), dp(24), dp(92))
                addView(formPanel())
                addView(formDivider())
                addView(sectionTitle("动作流程"))
                addView(stepsPanel())
                if (recipe != null) {
                    addView(formDivider())
                    addView(navigationRow("查看原始 JSON") { showRecipeRawJson(recipe) }.apply {
                        setPadding(0, dp(16), 0, dp(8))
                    })
                    if (recipe.runtimeSource == KiteRecipe.SOURCE_USER) {
                        addView(deleteRow(recipe))
                    }
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        prefillRecipeForm(recipe)
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

    private fun formPanel(): View = row {
        setPadding(0, dp(8), 0, dp(30))
        gravity = Gravity.CENTER_VERTICAL
        nameInput = editInput("例如：Hermes WebUI")
        descriptionInput = editInput("例如：启动 Hermes 图形化工作台")
        urlInput = editInput("例如：http://127.0.0.1:8648")
        commandInput = editInput("例如：hermes-web-ui start --port 8648")
        workdirInput = editInput("例如：/workspace/hermes（可选）")
        expectedInput = editInput("例如：ready（可选）")
        shortcutSwitch = Switch(context).apply { isChecked = false }

        addView(largeRecipeIconTile())
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(nameInput.apply {
                hint = "Hermes 工作台"
                textSize = 14.5f
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                setSingleLine(true)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 0)
                background = null
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30))
            })
            addView(TextView(context).apply {
                text = "点击图标可更换 ›"
                textSize = 8.8f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(5), 0, 0)
            })
        })
    }

    private fun appearancePanel(recipe: KiteRecipe?): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(10))
        background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
        elevation = 0f

        addView(toggleRow())
        addView(divider())
        if (recipe != null) {
            addView(navigationRow("查看原始 JSON") { showRecipeRawJson(recipe) })
            if (recipe.runtimeSource == KiteRecipe.SOURCE_USER) {
                addView(deleteRow(recipe))
            }
        } else {
            addView(navigationRow("高级设置（可选）") {
                Toast.makeText(context, "高级设置后续开放", Toast.LENGTH_SHORT).show()
            })
        }
    }

    private fun stepsPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        addView(executionStepsEditor())
    }

    private fun iconChooser(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(9))
        addView(TextView(context).apply {
            text = "图标"
            textSize = 13f
            setTextColor(tokens.textPrimary)
        })
        iconContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, 0)
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
        listOf("web", "terminal", "bot", "file", "more").forEach { iconName ->
            iconContainer.addView(iconChip(iconName, selectedIconName == iconName))
        }
    }

    private fun largeRecipeIconTile(): View = FrameLayout(this).apply {
        background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(18).toFloat(), 0)
        layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))

        val glyph = TextView(context).apply {
            text = displayIconGlyph(selectedIconName)
            textSize = 19.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
        }
        addView(glyph, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        addView(TextView(context).apply {
            text = "✎"
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            background = roundedBox(tokens.surfaceElevated, Color.TRANSPARENT, dp(17).toFloat(), 0)
            elevation = dp(5).toFloat()
        }, FrameLayout.LayoutParams(dp(17), dp(17), Gravity.BOTTOM or Gravity.RIGHT).apply {
            setMargins(0, 0, -dp(8), dp(9))
        })

        setOnClickListener {
            val names = listOf("terminal", "web", "bot", "file", "tools", "server", "code", "default")
            val nextIndex = (names.indexOf(selectedIconName).takeIf { it >= 0 } ?: 0) + 1
            selectedIconName = names[nextIndex % names.size]
            glyph.text = displayIconGlyph(selectedIconName)
        }
    }

    private fun actionIconTile(draft: RecipeStepDraft): View = FrameLayout(this).apply {
        val shell = draft.type == KiteRecipe.STEP_SHELL
        val color = if (shell) tokens.primaryStrong else tokens.success
        background = roundedBox(tintBackground(color), Color.TRANSPARENT, dp(11).toFloat(), 0)
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        addView(TextView(context).apply {
            text = if (shell) ">_" else "◎"
            textSize = if (shell) 14f else 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(color)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun executionStepsEditor(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(9))
        stepsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(stepsContainer)
        addView(addStepButton())
        renderStepOptions()
    }

    private fun stepPresetRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, dp(8))
        addView(stepPresetChip("打开网页") {
            applyStepTemplate(KiteRecipe.TYPE_OPEN_URL)
        })
        addView(stepPresetChip("命令 + 网页") {
            applyStepTemplate(KiteRecipe.TYPE_COMMAND_WEB)
        })
        addView(stepPresetChip("启动服务") {
            applyStepTemplate(KiteRecipe.TYPE_START_SERVICE)
        })
    }

    private fun stepPresetChip(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.primaryStrong)
        setPadding(dp(9), 0, dp(9), 0)
        background = roundedBox(tokens.primarySubtle, tokens.primarySoft, dp(14).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)).apply {
            setMargins(0, 0, dp(7), 0)
        }
        setOnClickListener { onClick() }
    }

    private fun applyStepTemplate(type: String) {
        formSteps.clear()
        when (type) {
            KiteRecipe.TYPE_COMMAND_WEB -> {
                formSteps.add(RecipeStepDraft.shell())
                formSteps.add(RecipeStepDraft.openWeb())
            }
            KiteRecipe.TYPE_START_SERVICE -> formSteps.add(RecipeStepDraft.shell())
            else -> formSteps.add(RecipeStepDraft.openWeb())
        }
        selectedType = type
        selectedRunMode = defaultRunModeForType(type)
        selectedIconName = KiteRecipeIcon.defaultNameForType(type)
        renderIconOptions()
        renderStepOptions()
    }

    private fun renderStepOptions() {
        if (!::stepsContainer.isInitialized) return
        stepsContainer.removeAllViews()
        if (formSteps.isEmpty()) {
            stepsContainer.addView(emptyStepState())
            return
        }
        formSteps.forEachIndexed { index, draft ->
            stepsContainer.addView(stepSummaryCard(index, draft))
        }
    }

    private fun emptyStepState(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14))
    }

    private fun addStepButton(): TextView = TextView(this).apply {
        text = "+  添加动作"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.primaryStrong)
        background = dashedRoundedBox(tokens.surface, tokens.primaryStrong, dp(21).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            setMargins(0, dp(14), 0, dp(4))
        }
        setOnClickListener { showStepDialog() }
    }

    private fun stepSummaryCard(index: Int, draft: RecipeStepDraft): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { showStepDialog(index, draft.copy()) }

        addView(row {
            setPadding(0, dp(10), 0, dp(10))
            addView(TextView(context).apply {
                text = "${index + 1}"
                textSize = 10f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(38)).apply { setMargins(0, 0, dp(11), 0) }
            })
            addView(actionIconTile(draft))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(2), 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = stepTypeLabel(draft)
                    textSize = 11.8f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = stepSummaryText(draft)
                    textSize = 10f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                })
            })
            addView(TextView(context).apply {
                text = "☰"
                textSize = 13.5f
                setTextColor(tokens.textTertiary)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(38))
            })
        })
        addView(divider().apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, 0, 0, 0)
            }
        })
    }

    private fun stepTypeLabel(draft: RecipeStepDraft): String =
        if (draft.type == KiteRecipe.STEP_SHELL) "运行命令" else "打开网页"

    private fun stepSummaryText(draft: RecipeStepDraft): String =
        if (draft.type == KiteRecipe.STEP_SHELL) {
            draft.command.ifBlank { "未填写命令" }
        } else {
            draft.url.ifBlank { "未填写打开地址" }
        }

    private fun showStepDialog(editIndex: Int? = null, initial: RecipeStepDraft? = null) {
        val dialog = Dialog(this)
        val draft = initial ?: RecipeStepDraft.shell()
        var selectedAction = if (draft.type == KiteRecipe.STEP_OPEN_WEB) KiteRecipe.STEP_OPEN_WEB else KiteRecipe.STEP_SHELL
        var commandValue = draft.command
        var urlValue = draft.url
        var workdirValue = draft.workdir
        var expectedValue = draft.expectedText
        var runModeValue = draft.runMode.ifBlank { KiteRecipe.RUN_MODE_DETACHED }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(38))
        }
        val contentScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }

        fun renderContent() {
            renderStepDialogContent(content, selectedAction, commandValue, urlValue, workdirValue, expectedValue, runModeValue,
                onCommand = { commandValue = it },
                onUrl = { urlValue = it },
                onWorkdir = { workdirValue = it },
                onExpected = { expectedValue = it },
                onRunMode = {
                    runModeValue = it
                    renderContent()
                }
            )
        }

        fun dialogTab(label: String, value: String): TextView = TextView(this).apply {
            val selected = selectedAction == value
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
            background = roundedBox(if (selected) tokens.primarySubtle else Color.TRANSPARENT, Color.TRANSPARENT, dp(21).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener {
                selectedAction = value
                renderDialogTabs(tabRow, ::dialogTab)
                renderContent()
            }
        }

        page.addView(row {
            setPadding(dp(8), dp(14), dp(16), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            addView(iconButton("‹", dp(38), Color.TRANSPARENT, tokens.primaryStrong, dp(15)) { dialog.dismiss() })
            addView(TextView(context).apply {
                text = if (editIndex == null) "添加动作" else "编辑动作"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = if (editIndex == null) "添加" else "保存"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(42))
                setOnClickListener {
                    if (selectedAction == "more") {
                        Toast.makeText(context, "更多动作后续开放", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val next = if (selectedAction == KiteRecipe.STEP_SHELL) {
                        if (commandValue.isBlank()) {
                            Toast.makeText(context, "请填写命令", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        RecipeStepDraft.shell().apply {
                            command = commandValue.trim()
                            workdir = workdirValue.trim()
                            expectedText = expectedValue.trim()
                            runMode = runModeValue
                        }
                    } else {
                        if (urlValue.isBlank()) {
                            Toast.makeText(context, "请填写网页地址", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        RecipeStepDraft.openWeb().apply {
                            url = urlValue.trim()
                        }
                    }
                    if (editIndex == null) {
                        formSteps.add(next)
                    } else {
                        formSteps[editIndex] = next
                    }
                    selectedType = inferTypeFromDrafts()
                    renderStepOptions()
                    dialog.dismiss()
                }
            })
        })
        page.addView(divider())
        page.addView(contentScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        content.addView(TextView(this).apply {
            text = "选择动作类型"
            textSize = 12f
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(12))
        })
        content.addView(tabRow.apply {
            background = roundedBox(tokens.surface, tokens.border, dp(23).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                setMargins(0, 0, 0, dp(22))
            }
        })
        content.addView(divider().apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, 0, 0, dp(20))
            }
        })

        renderDialogTabs(tabRow, ::dialogTab)
        renderContent()
        dialog.setContentView(page)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun renderDialogTabs(tabRow: LinearLayout, tabFactory: (String, String) -> TextView) {
        tabRow.removeAllViews()
        tabRow.addView(tabFactory(">_ 命令", KiteRecipe.STEP_SHELL))
        tabRow.addView(tabFactory("◎ 网页", KiteRecipe.STEP_OPEN_WEB))
        tabRow.addView(tabFactory("⋯ 更多", "more"))
    }

    private fun renderStepDialogContent(
        container: LinearLayout,
        selectedAction: String,
        commandValue: String,
        urlValue: String,
        workdirValue: String,
        expectedValue: String,
        runModeValue: String,
        onCommand: (String) -> Unit,
        onUrl: (String) -> Unit,
        onWorkdir: (String) -> Unit,
        onExpected: (String) -> Unit,
        onRunMode: (String) -> Unit
    ) {
        while (container.childCount > 3) {
            container.removeViewAt(3)
        }
        when (selectedAction) {
            KiteRecipe.STEP_SHELL -> {
                container.addView(dialogInput("命令", "hermes-web-ui start --port 8648", commandValue, onCommand))
                container.addView(dialogInput("执行位置（可选）", "/workspace/hermes", workdirValue, onWorkdir))
                container.addView(dialogRunModeChooser(runModeValue, onRunMode))
                container.addView(dialogInput("预期输出（可选）", "命令最后输出包含这段文字时，认为匹配成功", expectedValue, onExpected))
            }
            KiteRecipe.STEP_OPEN_WEB -> {
                container.addView(dialogInput("网页地址", "http://127.0.0.1:8648", urlValue, onUrl))
            }
            else -> {
                container.addView(TextView(this).apply {
                    text = "更多动作会用于打开 App、文件投递、Android 原生动作等。"
                    textSize = 15f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(12), 0, dp(10))
                })
            }
        }
    }

    private fun dialogInput(label: String, hintText: String, value: String, onChanged: (String) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(20))
            addView(TextView(context).apply {
                text = label
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(0, 0, 0, dp(8))
            })
            addView(EditText(context).apply {
                hint = hintText
                setText(value)
                textSize = 12.5f
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                setSingleLine(true)
                setPadding(dp(12), 0, dp(12), 0)
                background = roundedBox(tokens.inputBackground, tokens.border, dp(12).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                addTextChangedListener(simpleTextWatcher { onChanged(it) })
            })
        }

    private fun dialogRunModeChooser(selectedRunMode: String, onRunMode: (String) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(20))
            addView(TextView(context).apply {
                text = "运行方式"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(0, 0, 0, dp(8))
            })
            addView(row {
                background = roundedBox(tokens.surface, tokens.border, dp(22).toFloat())
                setPadding(dp(3), dp(3), dp(3), dp(3))
                addView(dialogRunModeChip("等待结束", KiteRecipe.RUN_MODE_ATTACHED, selectedRunMode, onRunMode))
                addView(dialogRunModeChip("后台运行", KiteRecipe.RUN_MODE_DETACHED, selectedRunMode, onRunMode))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }

    private fun dialogRunModeChip(label: String, value: String, selectedRunMode: String, onRunMode: (String) -> Unit): TextView =
        TextView(this).apply {
            val selected = selectedRunMode == value
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (selected) tokens.primaryStrong else tokens.textPrimary)
            background = roundedBox(if (selected) tokens.primarySubtle else Color.TRANSPARENT, Color.TRANSPARENT, dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener { onRunMode(value) }
        }

    private fun stepEditorCard(index: Int, draft: RecipeStepDraft): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(9), dp(10), dp(7))
        background = roundedBox(tokens.surfaceElevated, tokens.border, dp(13).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, 0, dp(7)) }

        addView(row {
            addView(TextView(context).apply {
                text = if (draft.type == KiteRecipe.STEP_SHELL) "命令步骤 ${index + 1}" else "打开网页 ${index + 1}"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "删除"
                textSize = 11.5f
                setTextColor(tokens.warning)
                setPadding(dp(10), dp(4), 0, dp(4))
                setOnClickListener {
                    formSteps.removeAt(index)
                    renderStepOptions()
                }
            })
        })

        if (draft.type == KiteRecipe.STEP_SHELL) {
            addView(stepInput("命令", "例如：hermes-web-ui start --port 8648", draft.command) { draft.command = it })
            addView(stepInput("执行位置（可选）", "例如：/workspace/hermes", draft.workdir) { draft.workdir = it })
            addView(stepRunModeChooser(draft))
            addView(stepInput("预期输出（可选）", "例如：ready", draft.expectedText) { draft.expectedText = it })
        } else {
            addView(stepInput("打开地址", "例如：http://127.0.0.1:8648", draft.url) { draft.url = it })
        }
    }

    private fun stepInput(label: String, hintText: String, value: String, onChanged: (String) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
            addView(TextView(context).apply {
                text = label
                textSize = 11f
                setTextColor(tokens.textSecondary)
            })
            addView(editInput(hintText).apply {
                setText(value)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply {
                    setMargins(0, dp(4), 0, 0)
                }
                addTextChangedListener(simpleTextWatcher { onChanged(it) })
            })
        }

    private fun stepRunModeChooser(draft: RecipeStepDraft): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, 0)
        addView(TextView(context).apply {
            text = "运行方式"
            textSize = 11f
            setTextColor(tokens.textSecondary)
        })
        addView(row {
            addView(stepRunModeChip("等待结束", KiteRecipe.RUN_MODE_ATTACHED, draft))
            addView(stepRunModeChip("后台服务", KiteRecipe.RUN_MODE_DETACHED, draft))
        })
    }

    private fun stepRunModeChip(label: String, value: String, draft: RecipeStepDraft): TextView =
        TextView(this).apply {
            val selected = draft.runMode == value
            text = label
            textSize = 10.8f
            gravity = Gravity.CENTER
            setTextColor(if (selected) tokens.buttonText else tokens.textPrimary)
            background = roundedBox(if (selected) tokens.primaryStrong else tokens.surface, if (selected) tokens.primaryStrong else tokens.border, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(0, dp(5), dp(7), 0) }
            setOnClickListener {
                draft.runMode = value
                renderStepOptions()
            }
        }

    private fun secondaryStepButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            background = roundedBox(tokens.surface, tokens.primarySoft, dp(13).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { setMargins(0, dp(5), dp(7), 0) }
            setOnClickListener { onClick() }
        }

    private fun runModeChooser(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(18))
        addView(TextView(context).apply {
            text = "运行方式"
            textSize = 15f
            setTextColor(tokens.textPrimary)
        })
        runModeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        addView(runModeContainer)
        renderRunModeOptions()
    }

    private fun renderRunModeOptions() {
        if (!::runModeContainer.isInitialized) return
        runModeContainer.removeAllViews()
        runModeContainer.addView(runModeChip("普通命令：等待结束", KiteRecipe.RUN_MODE_ATTACHED, selectedRunMode == KiteRecipe.RUN_MODE_ATTACHED))
        runModeContainer.addView(runModeChip("后台服务：启动后保持运行", KiteRecipe.RUN_MODE_DETACHED, selectedRunMode == KiteRecipe.RUN_MODE_DETACHED))
    }

    private fun runModeChip(label: String, value: String, selected: Boolean): TextView = TextView(this).apply {
        text = label
        textSize = 12.5f
        gravity = Gravity.CENTER
        setTextColor(if (selected) tokens.buttonText else tokens.textPrimary)
        background = roundedBox(if (selected) tokens.primaryStrong else tokens.surface, if (selected) tokens.primaryStrong else tokens.border, dp(16).toFloat())
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0, 0, dp(8), 0) }
        setOnClickListener {
            selectedRunMode = value
            renderRunModeOptions()
        }
    }

    private fun prefillRecipeForm(recipe: KiteRecipe?) {
        val shellStep = recipe?.firstShellStep()
        val openUrl = recipe?.openWebUrl().orEmpty()
        nameInput.setText(recipe?.name ?: "Hermes 工作台")
        nameInput.setSelection(nameInput.text?.length ?: 0)
        descriptionInput.setText(recipe?.description.orEmpty())
        commandInput.setText(shellStep?.cmd.orEmpty())
        workdirInput.setText(shellStep?.workdir ?: recipe?.execution?.workdir.orEmpty())
        urlInput.setText(openUrl)
        expectedInput.setText(shellStep?.expected?.text ?: recipe?.expected?.text.orEmpty())
        shortcutSwitch.isChecked = recipe?.shortcut ?: false
    }

    private fun updateDynamicFieldVisibility() {
        val hasShell = selectedType.requiresServiceCommand()
        if (::commandFieldContainer.isInitialized) commandFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::workdirFieldContainer.isInitialized) workdirFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::runModeFieldContainer.isInitialized) runModeFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::expectedFieldContainer.isInitialized) expectedFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::urlFieldContainer.isInitialized) urlFieldContainer.visibility = if (selectedType == KiteRecipe.TYPE_TEMPLATE) View.GONE else View.VISIBLE
    }

    private fun defaultRunModeForType(type: String): String =
        if (type.requiresServiceCommand()) KiteRecipe.RUN_MODE_DETACHED else KiteRecipe.RUN_MODE_ATTACHED

    private fun inferTypeFromDrafts(steps: List<RecipeStepDraft> = formSteps): String {
        val hasShell = steps.any { it.type == KiteRecipe.STEP_SHELL && it.command.isNotBlank() }
        val hasOpenWeb = steps.any { it.type == KiteRecipe.STEP_OPEN_WEB && it.url.isNotBlank() }
        return when {
            hasShell && hasOpenWeb -> KiteRecipe.TYPE_COMMAND_WEB
            hasShell -> KiteRecipe.TYPE_START_SERVICE
            hasOpenWeb -> KiteRecipe.TYPE_OPEN_URL
            else -> KiteRecipe.TYPE_TEMPLATE
        }
    }

    private fun saveRecipeForm() {
        val name = nameInput.text?.toString().orEmpty().trim()
        val description = descriptionInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (formSteps.isEmpty()) {
            Toast.makeText(this, "请至少添加一个命令或打开网页步骤", Toast.LENGTH_SHORT).show()
            return
        }
        formSteps.forEachIndexed { index, step ->
            if (step.type == KiteRecipe.STEP_OPEN_WEB && step.url.isBlank()) {
                Toast.makeText(this, "第 ${index + 1} 个打开网页步骤缺少地址", Toast.LENGTH_SHORT).show()
                return
            }
            if (step.type == KiteRecipe.STEP_SHELL && step.command.isBlank()) {
                Toast.makeText(this, "第 ${index + 1} 个命令步骤缺少命令", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val normalizedSteps = formSteps.toList()
        val inferredType = inferTypeFromDrafts(normalizedSteps)
        val defaultUrl = normalizedSteps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB }?.url.orEmpty()

        runCatching {
            recipeLoader.saveUserRecipe(
                NewRecipeInput(
                    id = editingRecipe?.id,
                    type = inferredType,
                    name = name,
                    url = defaultUrl,
                    command = "",
                    shortcut = shortcutSwitch.isChecked,
                    iconName = selectedIconName,
                    description = description,
                    steps = normalizedSteps.map { it.toInput() }
                )
            )
        }.onSuccess {
            Toast.makeText(this, "已保存配置", Toast.LENGTH_SHORT).show()
            editingRecipe = null
            showConsole()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecipeRawJson(recipe: KiteRecipe) {
        currentScreen = Screen.RecipeDetail
        root.removeAllViews()
        root.addView(topBar("原始 JSON") { showRecipeEditor(recipe) })
        root.addView(ScrollView(this).apply {
            addView(TextView(context).apply {
                text = recipe.toJson().toString(2)
                textSize = 14f
                setTextColor(tokens.textPrimary)
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
        root.setBackgroundColor(tokens.pageBackground)
        root.removeAllViews()
        root.addView(topBar("Kite 工作台") { showConsole() })
        val parent = webView.parent
        if (parent is ViewGroup) parent.removeView(webView)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webShell.open(url, recipeId = recipe?.id, recipeName = recipe?.name, openSource = source)
    }

    private fun createTopBar(title: String): View = row {
        setPadding(0, dp(18), dp(22), dp(10))
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton("‹", dp(40), Color.TRANSPARENT, tokens.textPrimary, dp(14)) { showConsole() })
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = "保存"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(42))
            setOnClickListener { saveRecipeForm() }
        })
    }

    private fun topBar(title: String, onBack: () -> Unit): View = row {
        setPadding(dp(18), dp(14), dp(18), dp(10))
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton("‹", dp(44), Color.TRANSPARENT, tokens.textPrimary, dp(16)) { onBack() })
        addView(TextView(context).apply {
            text = title
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(View(context), LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        setPadding(0, 0, 0, dp(12))
    }

    private fun optionCard(option: TypeOption, selected: Boolean, hasRightMargin: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = roundedBox(tokens.surface, if (selected) tokens.primaryStrong else tokens.border, dp(18).toFloat(), dp(if (selected) 2 else 1))
            layoutParams = LinearLayout.LayoutParams(0, dp(98), 1f).apply {
                if (hasRightMargin) setMargins(0, 0, dp(10), 0)
            }
            addView(TextView(context).apply {
                text = iconGlyph(option.icon)
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
            })
            addView(TextView(context).apply {
                text = option.label
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(if (selected) tokens.primaryStrong else tokens.textPrimary)
                setPadding(0, dp(8), 0, 0)
            })
            setOnClickListener {
                selectedType = option.type
                selectedIconName = KiteRecipeIcon.defaultNameForType(selectedType)
                selectedRunMode = defaultRunModeForType(selectedType)
                renderTypeOptions()
                renderIconOptions()
                renderRunModeOptions()
                updateDynamicFieldVisibility()
            }
        }

    private fun iconChip(iconName: String, selected: Boolean): TextView = TextView(this).apply {
        text = iconGlyph(iconName)
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
        background = roundedBox(if (selected) tokens.primarySubtle else tokens.surface, if (selected) tokens.primaryStrong else tokens.border, dp(14).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(38)).apply { setMargins(0, 0, dp(8), 0) }
        setOnClickListener {
            if (iconName == "more") {
                Toast.makeText(context, "自定义图标后续开放", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedIconName = iconName
            renderIconOptions()
        }
    }

    private fun labeledField(label: String, input: EditText): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(8))
        addView(TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(tokens.textPrimary)
        })
        addView(input)
    }

    private fun editInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 13.5f
        setSingleLine(true)
        setTextColor(tokens.textPrimary)
        setHintTextColor(tokens.textTertiary)
        setPadding(dp(11), 0, dp(11), 0)
        background = roundedBox(tokens.inputBackground, tokens.border, dp(12).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
            setMargins(0, dp(5), 0, 0)
        }
    }

    private fun simpleTextWatcher(onChanged: (String) -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }

    private fun toggleRow(): View = row {
        setPadding(0, dp(3), 0, dp(8))
        addView(TextView(context).apply {
            text = "创建快捷方式到桌面"
            textSize = 13f
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        shortcutSwitch = Switch(context).apply { isChecked = false }
        addView(shortcutSwitch)
    }

    private fun navigationRow(label: String, onClick: (() -> Unit)? = null): View = row {
        setPadding(0, dp(10), 0, 0)
        addView(TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = "›"
            textSize = 24f
            setTextColor(tokens.textSecondary)
        })
        if (onClick != null) setOnClickListener { onClick() }
    }

    private fun deleteRow(recipe: KiteRecipe): View = row {
        setPadding(0, dp(10), 0, 0)
        addView(TextView(context).apply {
            text = "删除配置"
            textSize = 13f
            setTextColor(tokens.danger)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        setOnClickListener { confirmDeleteRecipe(recipe) }
    }

    private fun confirmDeleteRecipe(recipe: KiteRecipe) {
        if (recipe.runtimeSource != KiteRecipe.SOURCE_USER) {
            Toast.makeText(this, "内置配置暂不可删除", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("删除配置")
            .setMessage("确定删除 ${recipe.name}？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                if (recipeLoader.deleteUserRecipe(recipe)) {
                    runtimeStates.remove(recipe.id)
                    Toast.makeText(this, "已删除配置", Toast.LENGTH_SHORT).show()
                    showConsole()
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun bottomActions(): View = row {
        setPadding(dp(22), dp(5), dp(22), dp(7))
        setBackgroundColor(Color.WHITE)
        addView(View(context), LinearLayout.LayoutParams(0, dp(1), 1f))
        addView(TextView(context).apply {
            text = "取消"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            background = roundedBox(tokens.surfaceElevated, tokens.surfaceElevated, dp(11).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(86), dp(32)).apply { setMargins(0, 0, dp(10), 0) }
            setOnClickListener { showConsole() }
        })
        addView(TextView(context).apply {
            text = "保存"
            textSize = 12.5f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.buttonText)
            background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(11).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(116), dp(32))
            setOnClickListener { saveRecipeForm() }
        })
    }

    private fun bottomNavigation(): View = row {
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setBackgroundColor(tokens.surfaceElevated)
        addView(navItem("▦", "配置", currentScreen == Screen.Console) { showConsole() })
        addView(navItem("▤", "模板", false) { Toast.makeText(this@MainActivity, "模板后续开放", Toast.LENGTH_SHORT).show() })
        addView(navItem("⌁", "活动", false) { Toast.makeText(this@MainActivity, "活动后续开放", Toast.LENGTH_SHORT).show() })
        addView(navItem("⚙", "设置", currentScreen == Screen.Settings || currentScreen == Screen.ThemeSettings) { showSettings() })
    }

    private fun navItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f)
        addView(TextView(context).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
        })
        addView(TextView(context).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
        })
        setOnClickListener { onClick() }
    }

    private fun row(content: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        content()
    }

    private fun iconButton(text: String, size: Int, fill: Int, textColor: Int, radius: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = when (text) {
                "+" -> 38f
                "⌕" -> 37f
                else -> 24f
            }
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
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
        "more" -> "…"
        else -> "◎"
    }

    private fun displayIconGlyph(iconName: String): String = when (iconName) {
        "terminal", "code", "server" -> "▣"
        else -> iconGlyph(iconName)
    }

    private fun statusColors(status: RecipeRunStatus): SemanticColors = when (status) {
        RecipeRunStatus.Starting,
        RecipeRunStatus.Running,
        RecipeRunStatus.AlreadyRunning,
        RecipeRunStatus.Opened -> SemanticColors(tokens.success, tokens.successSoft, tokens.successBorder)
        RecipeRunStatus.Stopping -> SemanticColors(tokens.warning, tokens.warningSoft, tokens.warningBorder)
        RecipeRunStatus.Failed,
        RecipeRunStatus.BridgeUnavailable -> SemanticColors(tokens.danger, tokens.dangerSoft, tokens.dangerBorder)
        RecipeRunStatus.Unknown,
        RecipeRunStatus.Stopped -> SemanticColors(tokens.textSecondary, tokens.surface, tokens.border)
    }

    private fun stateTag(state: RecipeRuntimeState): TextView = TextView(this).apply {
        val colors = statusColors(state.status)
        text = state.status.label
        textSize = 9.5f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colors.text)
        setPadding(dp(6), dp(3), dp(6), dp(3))
        background = roundedBox(colors.background, colors.border, dp(14).toFloat())
    }

    private fun cardTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(8), 0, 0)
    }

    private fun cardDescription(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        includeFontPadding = false
        setTextColor(tokens.textSecondary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(5), 0, 0)
    }

    private fun failureSummary(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 10.5f
        includeFontPadding = false
        setTextColor(tokens.danger)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, 0, 0, dp(5))
    }

    private fun runtimeFeedback(text: String, isError: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = 10.5f
        includeFontPadding = false
        setTextColor(if (isError) tokens.danger else tokens.textSecondary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, 0, 0, 0)
    }

    private fun urlPill(url: String, accent: String): TextView = TextView(this).apply {
        val tone = KiteTheme.accent(accent, tokens)
        text = url
        textSize = 10f
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(tone.strong)
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background = roundedBox(tone.soft, tone.border, dp(10).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, 0, 0) }
    }

    private fun primaryAction(text: String, accent: String, disabled: Boolean = false, onClick: () -> Unit): View =
        TextView(this).apply {
            this.text = text
            textSize = 11.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.buttonText)
            alpha = if (disabled) 0.62f else 1f
            val fill = KiteTheme.accent(accent, tokens).strong
            background = roundedBox(fill, fill, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(0, 0, dp(6), 0) }
            isEnabled = !disabled
            if (!disabled) setOnClickListener { onClick() }
        }

    private fun editAction(onClick: () -> Unit): View = TextView(this).apply {
        text = "编辑"
        textSize = 11.5f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(tokens.textPrimary)
        background = roundedBox(tokens.surface, tokens.border, dp(12).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(30))
        setOnClickListener { onClick() }
    }

    private fun chip(text: String, selected: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (selected) tokens.buttonText else tokens.textPrimary)
        setPadding(dp(13), dp(8), dp(13), dp(8))
        background = roundedBox(if (selected) tokens.primaryStrong else tokens.surface, if (selected) tokens.primaryStrong else tokens.border, dp(24).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, dp(8), 0) }
    }

    private fun dropZoneButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.primaryStrong)
        setPadding(dp(12), 0, dp(12), 0)
        background = roundedBox(tokens.surface, tokens.border, dp(17).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
            setMargins(dp(8), 0, 0, 0)
        }
        setOnClickListener { onClick() }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(tokens.border)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun formDivider(): View = divider().apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, dp(8), 0, dp(34))
        }
    }

    private fun roundedBox(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = dp(1)): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(strokeWidth, stroke)
        }

    private fun dashedRoundedBox(fill: Int, stroke: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(dp(1), stroke, dp(7).toFloat(), dp(5).toFloat())
        }

    private fun tintBackground(color: Int): Int = KiteTheme.tint(color, 0.88f)

    private fun tintBackgroundBorder(color: Int): Int = KiteTheme.tint(color, 0.72f)

    private fun displayAccentName(recipe: KiteRecipe): String =
        KiteRecipeCard.resolvedAccentFor(recipe.icon.name, recipe.type, recipe.card.accent)

    private fun accentFor(recipe: KiteRecipe): Int = KiteTheme.accent(displayAccentName(recipe), tokens).strong

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

    private data class RecipeStepDraft(
        val type: String,
        var command: String = "",
        var url: String = "",
        var workdir: String = "",
        var runMode: String = KiteRecipe.RUN_MODE_DETACHED,
        var expectedText: String = ""
    ) {
        fun toInput(): NewRecipeStepInput = NewRecipeStepInput(
            type = type,
            command = command,
            url = url,
            workdir = workdir,
            runMode = runMode,
            expectedText = expectedText
        )

        companion object {
            fun shell(): RecipeStepDraft = RecipeStepDraft(type = KiteRecipe.STEP_SHELL)

            fun openWeb(): RecipeStepDraft = RecipeStepDraft(type = KiteRecipe.STEP_OPEN_WEB)

            fun fromStep(step: KiteRecipeStep): RecipeStepDraft =
                RecipeStepDraft(
                    type = step.type,
                    command = step.cmd.orEmpty(),
                    url = step.url.orEmpty(),
                    workdir = step.workdir.orEmpty(),
                    runMode = KiteRecipe.normalizeRunMode(step.runMode) ?: KiteRecipe.RUN_MODE_DETACHED,
                    expectedText = step.expected?.text.orEmpty()
                )
        }
    }

    private data class SemanticColors(
        val text: Int,
        val background: Int,
        val border: Int
    )

    private enum class Screen {
        Console,
        Workbench,
        RecipeDetail,
        CreateConfig,
        Settings,
        ThemeSettings
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

        fun feedbackSummary(): String? = when {
            !lastError.isNullOrBlank() -> lastError
            !lastMeaningfulOutput.isNullOrBlank() -> lastMeaningfulOutput
            status == RecipeRunStatus.BridgeUnavailable -> "桥接不可用"
            else -> null
        }?.take(80)

        companion object {
            fun fromRecipeStatus(recipeId: String, status: String): RecipeRuntimeState =
                RecipeRuntimeState(recipeId = recipeId, status = RecipeRunStatus.fromRecipeStatus(status))
        }
    }

    private enum class RecipeRunStatus(
        val label: String,
        val lifecycleEvent: String
    ) {
        Unknown("未启动", "unknown"),
        Stopped("未启动", "stopped"),
        Starting("启动中", "starting"),
        Running("运行中", "running"),
        AlreadyRunning("已运行", "already_running"),
        Opened("已打开", "opened"),
        Failed("启动失败", "failed"),
        Stopping("停止中", "stopping"),
        BridgeUnavailable("桥接不可用", "bridge_unavailable");

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
        this == KiteRecipe.TYPE_COMMAND_WEB || this == KiteRecipe.TYPE_SCRIPT_WEB || this == KiteRecipe.TYPE_START_SERVICE

    companion object {
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private const val WEB_READY_TIMEOUT_MS = 8000L
        private const val WEB_READY_INTERVAL_MS = 700L
        private const val WEB_READY_CONNECT_TIMEOUT_MS = 700
        private const val WEB_READY_READ_TIMEOUT_MS = 700
        private const val REQUEST_DROPZONE_STORAGE = 801
    }
}
