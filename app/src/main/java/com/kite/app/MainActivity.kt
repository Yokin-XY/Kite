package com.kite.app

import android.app.ActivityManager
import android.app.Dialog
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.kite.app.action.KiteActionRoute
import com.kite.app.action.KiteActionRouter
import com.kite.app.bridge.BridgeErrorType
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.bridge.KiteLocalServer
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.dropzone.DropZoneStatus
import com.kite.app.dropzone.KiteDropZoneManager
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.KiteRunReport
import com.kite.app.recipe.KiteStepReport
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.recipe.NewRecipeStepInput
import com.kite.app.run.CardRunState as RecipeRuntimeState
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunStatus as RecipeRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.PendingTerminalFlow
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.ThemeTokens
import com.kite.app.web.KiteWebShell
import com.kftest.app.foundation.bootstrap.BootstrapCoordinator
import com.kftest.app.foundation.bootstrap.BootstrapSnapshot
import com.kftest.app.foundation.bootstrap.BootstrapStage
import com.kftest.app.foundation.runtime.AssetExtractor
import com.kftest.app.foundation.runtime.RuntimeBootstrapProgress
import com.kftest.app.foundation.runtime.RuntimeBootstrapProgressSnapshot
import com.kftest.app.foundation.terminal.TerminalRuntimeHost
import com.kftest.app.foundation.terminal.TerminalRuntimeRegistry
import com.kftest.app.foundation.workspace.ManagedTerminalStatus
import com.kftest.app.foundation.toolchain.ToolchainPackInstaller
import com.kftest.app.foundation.workspace.KFWorkspaceManager
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kftest.app.ui.terminal.KiteTerminalShellTheme
import com.kftest.app.ui.terminal.TerminalChromeHost
import com.kftest.app.ui.terminal.TerminalFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlinx.coroutines.launch

open class MainActivity : AppCompatActivity(), TerminalChromeHost {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var dropZoneManager: KiteDropZoneManager
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var webShell: KiteWebShell
    private lateinit var localServer: KiteLocalServer
    private lateinit var cardLocalSettings: CardLocalSettingsStore
    private lateinit var themeStore: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView

    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var workdirInput: EditText
    private lateinit var shortcutSwitch: Switch
    private lateinit var launchInstanceSwitch: Switch
    private lateinit var commandFieldContainer: View
    private lateinit var urlFieldContainer: View
    private lateinit var workdirFieldContainer: View
    private lateinit var typeContainer: LinearLayout
    private lateinit var iconContainer: LinearLayout
    private lateinit var stepsContainer: LinearLayout

    private val runtimeStates = mutableMapOf<String, RecipeRuntimeState>()
    private val activeRunInstanceIds = mutableMapOf<String, String>()
    private val actionRouter = KiteActionRouter()
    private var currentScreen: Screen = Screen.Console
    private var currentRecipes: List<KiteRecipe> = emptyList()
    private var selectedType = KiteRecipe.TYPE_OPEN_URL
    private var selectedIconName = KiteRecipeIcon.defaultNameForType(KiteRecipe.TYPE_OPEN_URL)
    private var editingRecipe: KiteRecipe? = null
    private var dropZoneStatus: DropZoneStatus = DropZoneStatus(available = false, message = "投放区尚未检查")
    private var isDropZoneRefreshing = false
    private var themeConfig = ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
    private var tokens = KiteTheme.resolve(themeConfig)
    private val terminalContainerId = View.generateViewId()
    private var terminalBottomNavigation: View? = null
    private var isTerminalDetailMode = false
    private var kfRuntimeBootstrapRequested = false
    private var ubuntuRuntimeState = UbuntuRuntimeUiState.hidden()
    private val formSteps = mutableListOf<RecipeStepDraft>()
    private var pendingTerminalFlow: PendingTerminalFlow? = null
    private var localServerStarted = false
    private var consumedCardRunLaunchKey: String? = null
    private var focusedRunRecipeId: String? = null
    private var focusedRunInstanceId: String? = null
    private var latestBootstrapSnapshot = BootstrapCoordinator.snapshot.value
    private var latestRootfsProgress = AssetExtractor.rootfsProgress.value
    private var latestRuntimeBootstrapProgress = RuntimeBootstrapProgress.snapshot.value
    private var ubuntuRuntimeDialog: Dialog? = null
    private var runtimePanelTitleView: TextView? = null
    private var runtimePanelDetailView: TextView? = null
    private var runtimePanelProgressBar: ProgressBar? = null
    private var runtimePanelProgressTextView: TextView? = null
    private var runtimePanelActionButton: TextView? = null
    private var autoOpenedRootfsRunAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = KiteDiagnostics(this)
        diagnostics.writeCapabilityReport()
        themeStore = getSharedPreferences("kite_theme", MODE_PRIVATE)
        themeConfig = loadThemeConfig()
        tokens = KiteTheme.resolve(themeConfig)
        applyKiteTerminalTheme()
        recipeLoader = KiteRecipeLoader(this, diagnostics)
        dropZoneManager = KiteDropZoneManager(this, diagnostics)
        dropZoneStatus = dropZoneManager.prepareDropZone()
        bridgeClient = KiteBridgeClient(diagnostics, applicationContext)
        webView = WebView(this)
        webShell = KiteWebShell(this, webView, diagnostics) { }
        cardLocalSettings = CardLocalSettingsStore(this)
        localServer = KiteLocalServer(applicationContext, diagnostics) { url -> runOnUiThread { openWeb(url, "endpoint") } }
        if (shouldStartLocalServer()) {
            localServer.start()
            localServerStarted = true
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tokens.pageBackground)
        }
        setContentView(root)
        observeUbuntuBootstrapState()
        observeRootfsExtractionProgress()
        observeRuntimeBootstrapProgress()
        observeTerminalFlowSignals()
        showConsole()
        handleCardRunLaunchIntent(intent)
        refreshUbuntuRuntimeState()
        if (!dropZoneStatus.available) {
            Toast.makeText(this, dropZoneStatus.message, Toast.LENGTH_LONG).show()
        }
    }

    protected open fun shouldStartLocalServer(): Boolean = true

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCardRunLaunchIntent(intent)
    }

    override fun onDestroy() {
        if (localServerStarted) {
            localServer.stop()
        }
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX Activity migration.")
    override fun onBackPressed() {
        when (currentScreen) {
            Screen.ThemeSettings -> showSettings()
            Screen.Settings -> showConsole()
            Screen.Terminal -> if (isTerminalDetailMode) super.onBackPressed() else showConsole()
            else -> if (currentScreen != Screen.Console) showConsole() else super.onBackPressed()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
    }

    private fun handleCardRunLaunchIntent(sourceIntent: Intent?) {
        val recipeId = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID).orEmpty()
        if (recipeId.isBlank()) return

        val instanceId = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: CardRunIntents.newInstanceId(recipeId)
        val autoStart = sourceIntent?.getBooleanExtra(CardRunIntents.EXTRA_AUTO_START, true) ?: true
        val launchSource = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_LAUNCH_SOURCE).orEmpty()
        val launchKey = "$recipeId:$instanceId:$autoStart:$launchSource"
        if (consumedCardRunLaunchKey == launchKey) return
        consumedCardRunLaunchKey = launchKey

        val recipes = recipeLoader.loadAllRecipes()
        currentRecipes = recipes
        val recipe = recipes.firstOrNull { it.id == recipeId }
        if (recipe == null) {
            Toast.makeText(this, "未找到卡片：$recipeId", Toast.LENGTH_SHORT).show()
            diagnostics.logRecipeEvent("card_run_launch_missing_recipe", null, mapOf("recipeId" to recipeId))
            return
        }

        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        title = recipe.name
        applyCardTaskDescription(recipe)
        val state = CardRunStore.get(instanceId) ?: CardRunStore.start(recipe, instanceId)
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        diagnostics.logRecipeAction(
            recipe,
            "card_run_task_launch",
            mapOf(
                "instanceId" to instanceId,
                "source" to launchSource,
                "autoStart" to autoStart.toString()
            )
        )

        if (autoStart) {
            startRecipe(recipe, state, instanceId)
        } else {
            showConsole()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyCardTaskDescription(recipe: KiteRecipe) {
        setTaskDescription(
            ActivityManager.TaskDescription(
                recipe.name.ifBlank { "Kite 卡片" },
                CardShortcutManager.iconBitmap(recipe),
                opaqueColor(tokens.primaryStrong)
            )
        )
    }

    private fun opaqueColor(color: Int): Int =
        Color.rgb(Color.red(color), Color.green(color), Color.blue(color))

    private fun observeUbuntuBootstrapState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BootstrapCoordinator.snapshot.collect { snapshot ->
                    latestBootstrapSnapshot = snapshot
                    setUbuntuRuntimeState(buildUbuntuRuntimeUiState() ?: return@collect)
                }
            }
        }
    }

    private fun observeRootfsExtractionProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AssetExtractor.rootfsProgress.collect { progress ->
                    latestRootfsProgress = progress
                    setUbuntuRuntimeState(buildUbuntuRuntimeUiState() ?: return@collect)
                }
            }
        }
    }

    private fun observeRuntimeBootstrapProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RuntimeBootstrapProgress.snapshot.collect { progress ->
                    latestRuntimeBootstrapProgress = progress
                    setUbuntuRuntimeState(buildUbuntuRuntimeUiState() ?: return@collect)
                }
            }
        }
    }

    private fun observeTerminalFlowSignals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TerminalRuntimeRegistry.entries.collect { entries ->
                    val pending = pendingTerminalFlow ?: return@collect
                    val terminal = entries.firstOrNull { it.sessionId == pending.sessionId } ?: return@collect
                    if (terminal.status !in terminalFlowFinishedStatuses) return@collect
                    val activeRun = CardRunStore.get(pending.instanceId)
                    if (activeRun == null || activeRun.recipeId != pending.recipeId) {
                        pendingTerminalFlow = null
                        return@collect
                    }

                    pendingTerminalFlow = null
                    val recipe = currentRecipes.firstOrNull { it.id == pending.recipeId }
                        ?: recipeLoader.loadAllRecipes().firstOrNull { it.id == pending.recipeId }
                        ?: return@collect
                    diagnostics.logRecipeAction(
                        recipe,
                        "terminal_step_finished",
                        mapOf(
                            "sessionId" to pending.sessionId,
                            "status" to terminal.status.name,
                            "exitCode" to (terminal.lastExitCode?.toString() ?: "")
                        )
                    )
                    executeRecipeStep(
                        recipe = recipe,
                        stepIndex = pending.nextStepIndex,
                        runId = pending.sessionId,
                        pid = terminal.lastPid?.toString(),
                        lastOutput = "终端已结束：${terminal.status.label}"
                    )
                }
            }
        }
    }

    private fun refreshUbuntuRuntimeState() {
        thread(name = "KiteUbuntuRuntimeCheck", isDaemon = true) {
            val state = runCatching {
                if (WorkSurfaceRuntimeBridge.isBaseImageReady(applicationContext)) {
                    UbuntuRuntimeUiState.hidden()
                } else {
                    UbuntuRuntimeUiState(
                        title = "\u0055\u0062\u0075\u006e\u0074\u0075 \u672a\u90e8\u7f72",
                        detail = "\u9996\u6b21\u542f\u52a8 Ubuntu \u5361\u7247\u6216\u7ec8\u7aef\u65f6\u4f1a\u5148\u89e3\u538b\u7cfb\u7edf\u955c\u50cf\u3002",
                        blocksUbuntuActions = false,
                        isProblem = false
                    )
                }
            }.getOrElse { error ->
                UbuntuRuntimeUiState(
                    title = "\u0055\u0062\u0075\u006e\u0074\u0075 \u72b6\u6001\u672a\u77e5",
                    detail = error.message ?: error.javaClass.simpleName,
                    blocksUbuntuActions = false,
                    isProblem = true
                )
            }
            runOnUiThread { setUbuntuRuntimeState(state) }
        }
    }

    private fun setUbuntuRuntimeState(state: UbuntuRuntimeUiState) {
        val previous = ubuntuRuntimeState
        if (ubuntuRuntimeState == state) return
        ubuntuRuntimeState = state
        renderUbuntuRuntimePanelState()
        maybeAutoShowUbuntuRuntimePanel(state)
        if (::root.isInitialized && currentScreen == Screen.Console && shouldRefreshConsoleForRuntimeState(previous, state)) {
            showConsole()
        }
    }

    private fun shouldRefreshConsoleForRuntimeState(previous: UbuntuRuntimeUiState, next: UbuntuRuntimeUiState): Boolean {
        if (previous.visible != next.visible) return true
        if (previous.title != next.title) return true
        if (previous.isProblem != next.isProblem) return true
        if (previous.blocksUbuntuActions != next.blocksUbuntuActions) return true
        if (previous.showProgress != next.showProgress) return true
        val previousPercent = previous.progressPercent
        val nextPercent = next.progressPercent
        if (previousPercent != null && nextPercent != null) {
            return kotlin.math.abs(nextPercent - previousPercent) >= 5
        }
        return previous.progressText.isBlank() != next.progressText.isBlank()
    }

    private fun buildUbuntuRuntimeUiState(): UbuntuRuntimeUiState? {
        latestRootfsProgress.toUbuntuRuntimeUiState()?.let { return it }
        latestRuntimeBootstrapProgress.toUbuntuRuntimeUiState()?.let { return it }
        return latestBootstrapSnapshot.toUbuntuRuntimeUiState()
    }

    private fun RuntimeBootstrapProgressSnapshot.toUbuntuRuntimeUiState(): UbuntuRuntimeUiState? {
        if (!active) {
            if (percent == 100 && latestBootstrapSnapshot.stage == BootstrapStage.READY) {
                return UbuntuRuntimeUiState.hidden()
            }
            return null
        }
        return UbuntuRuntimeUiState(
            title = title.ifBlank { "正在部署 Ubuntu" },
            detail = detail.ifBlank { "正在执行当前初始化步骤。" },
            blocksUbuntuActions = true,
            isProblem = false,
            progressPercent = percent,
            progressText = percent?.let { "总进度 $it%" }.orEmpty(),
            showProgress = percent != null
        )
    }

    private fun AssetExtractor.RootfsExtractionProgress.toUbuntuRuntimeUiState(): UbuntuRuntimeUiState? {
        val progressLabel = progressLabel()
        return when (phase) {
            AssetExtractor.RootfsExtractionPhase.PREPARING,
            AssetExtractor.RootfsExtractionPhase.EXTRACTING,
            AssetExtractor.RootfsExtractionPhase.VERIFYING -> UbuntuRuntimeUiState(
                title = when (phase) {
                    AssetExtractor.RootfsExtractionPhase.VERIFYING -> "正在校验 Ubuntu 系统镜像"
                    else -> "正在解压 Ubuntu 系统镜像"
                },
                detail = message.ifBlank {
                    if (entriesExtracted > 0) "已处理 $entriesExtracted 个文件，完成后会自动继续启动。" else "正在准备系统镜像，完成后会自动继续启动。"
                },
                blocksUbuntuActions = true,
                isProblem = false,
                progressPercent = percent?.let { 5 + (it * 45 / 100) },
                progressText = progressLabel,
                showProgress = true,
                autoOpenPanel = phase != AssetExtractor.RootfsExtractionPhase.VERIFYING
            )

            AssetExtractor.RootfsExtractionPhase.FAILED -> UbuntuRuntimeUiState(
                title = "Ubuntu 部署失败",
                detail = listOfNotNull(
                    errorMessage?.takeIf { it.isNotBlank() },
                    "未完成的 rootfs 不会被当作成功使用，下次启动会清理后重新解压。"
                ).joinToString("\n"),
                blocksUbuntuActions = false,
                isProblem = true,
                progressPercent = percent,
                progressText = progressLabel,
                showProgress = progressLabel.isNotBlank(),
                canRetry = true
            )

            AssetExtractor.RootfsExtractionPhase.READY -> {
                when (latestBootstrapSnapshot.stage) {
                    BootstrapStage.ROOTFS_EXTRACTING,
                    BootstrapStage.BASE_BOOTSTRAP -> {
                        if (latestRuntimeBootstrapProgress.active) {
                            null
                        } else {
                            UbuntuRuntimeUiState(
                                title = "正在初始化基础环境",
                                detail = "系统镜像已经解压完成，正在补齐 apt、dpkg 和常用基础工具。这个阶段可能比解压更久，完成后会继续准备工作区。",
                                blocksUbuntuActions = true,
                                isProblem = false,
                                progressPercent = 55,
                                progressText = "总进度 55%",
                                showProgress = true
                            )
                        }
                    }
                    BootstrapStage.IDLE,
                    BootstrapStage.READY -> UbuntuRuntimeUiState.hidden()
                    else -> null
                }
            }

            AssetExtractor.RootfsExtractionPhase.IDLE -> null
        }
    }

    private fun AssetExtractor.RootfsExtractionProgress.progressLabel(): String =
        when {
            percent != null -> "rootfs 解压 $percent% · 已处理 $entriesExtracted 个文件"
            entriesExtracted > 0 -> "已处理 $entriesExtracted 个文件"
            bytesRead > 0L -> "已读取 ${formatBytes(bytesRead)}"
            else -> ""
        }

    private fun BootstrapSnapshot.toUbuntuRuntimeUiState(): UbuntuRuntimeUiState? =
        when (stage) {
            BootstrapStage.IDLE -> null
            BootstrapStage.READY -> UbuntuRuntimeUiState.hidden()
            BootstrapStage.FAILED -> UbuntuRuntimeUiState(
                title = "\u0055\u0062\u0075\u006e\u0074\u0075 \u90e8\u7f72\u5931\u8d25",
                detail = lastError ?: "\u521d\u59cb\u5316\u8fc7\u7a0b\u4e2d\u51fa\u73b0\u672a\u77e5\u9519\u8bef\u3002",
                blocksUbuntuActions = false,
                isProblem = true
            )
            BootstrapStage.SERVICE_REQUESTED,
            BootstrapStage.ROOTFS_EXTRACTING,
            BootstrapStage.BASE_BOOTSTRAP,
            BootstrapStage.SPACE_READY,
            BootstrapStage.TERMINAL_WARMING -> UbuntuRuntimeUiState(
                title = when (stage) {
                    BootstrapStage.SERVICE_REQUESTED -> "\u6b63\u5728\u5524\u8d77 Ubuntu \u8fd0\u884c\u73af\u5883"
                    BootstrapStage.ROOTFS_EXTRACTING -> "\u6b63\u5728\u89e3\u538b\u7cfb\u7edf\u955c\u50cf"
                    BootstrapStage.BASE_BOOTSTRAP -> "\u6b63\u5728\u521d\u59cb\u5316\u57fa\u7840\u73af\u5883"
                    BootstrapStage.SPACE_READY -> "\u6b63\u5728\u51c6\u5907\u5de5\u4f5c\u533a"
                    BootstrapStage.TERMINAL_WARMING -> "\u6b63\u5728\u9884\u70ed\u7ec8\u7aef"
                    else -> "\u6b63\u5728\u90e8\u7f72 Ubuntu"
                },
                detail = "\u90e8\u7f72\u671f\u95f4 Ubuntu \u5361\u7247\u6682\u65f6\u9501\u5b9a\uff0c\u5b8c\u6210\u540e\u4f1a\u81ea\u52a8\u6062\u590d\u3002",
                blocksUbuntuActions = true,
                isProblem = false,
                showProgress = stage == BootstrapStage.ROOTFS_EXTRACTING,
                progressText = if (stage == BootstrapStage.ROOTFS_EXTRACTING) "正在等待解压进度" else ""
            )
        }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1fKB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format("%.1fMB", mb)
        return String.format("%.1fGB", mb / 1024.0)
    }

    private fun loadThemeConfig(): ThemeConfig =
        ThemeConfig(
            themeColor = themeStore.getInt("theme_color", KiteTheme.defaultThemeColor),
            backgroundColor = themeStore.getInt("background_color", KiteTheme.defaultBackgroundColor)
        )

    private fun saveThemeConfig(config: ThemeConfig) {
        themeConfig = config
        tokens = KiteTheme.resolve(config)
        applyKiteTerminalTheme()
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

    private fun refreshDropZoneRecipes(showToast: Boolean = true) {
        if (isDropZoneRefreshing) {
            if (showToast) {
                Toast.makeText(this, "正在刷新配置，请稍候", Toast.LENGTH_SHORT).show()
            }
            return
        }
        isDropZoneRefreshing = true
        if (currentScreen == Screen.Console && showToast) showConsole()
        if (showToast) {
            Toast.makeText(this, "正在刷新 Kite 投放区", Toast.LENGTH_SHORT).show()
        }
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
            runtimeStates[recipe.id] = CardRunStore.currentForRecipe(recipe.id)
                ?: runtimeStates[recipe.id]
                    ?: RecipeRuntimeState.fromRecipeStatus(recipe.id, "unknown")
        }
        val focusedRecipe = focusedRunRecipe()
        if (this is CardRunActivity && focusedRecipe != null) {
            showCardRunSurface(focusedRecipe)
            return
        }
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(consoleHeader())
        ubuntuRuntimeBanner()?.let { root.addView(it) }
        root.addView(recipeGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
    }

    private fun focusedRunRecipe(): KiteRecipe? {
        val recipeId = focusedRunRecipeId?.takeIf { it.isNotBlank() } ?: return null
        return currentRecipes.firstOrNull { it.id == recipeId }
            ?: recipeLoader.loadAllRecipes().firstOrNull { it.id == recipeId }
    }

    private fun showTerminal() {
        val currentTerminalFragment = supportFragmentManager.findFragmentByTag(TERMINAL_FRAGMENT_TAG) as? TerminalFragment
        if (currentScreen == Screen.Terminal && currentTerminalFragment?.isAdded == true) {
            applyKiteTerminalTheme()
            terminalBottomNavigation?.visibility = if (isTerminalDetailMode) View.GONE else View.VISIBLE
            return
        }
        currentScreen = Screen.Terminal
        isTerminalDetailMode = false
        applyKiteTerminalTheme()
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen(detachTerminal = false)
        val container = FrameLayout(this).apply {
            id = terminalContainerId
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        terminalBottomNavigation = bottomNavigation().also { nav ->
            root.addView(nav)
        }

        val fragment = currentTerminalFragment ?: TerminalFragment()
        supportFragmentManager.beginTransaction().apply {
            when {
                fragment.isDetached -> attach(fragment)
                fragment.isAdded -> show(fragment)
                else -> add(terminalContainerId, fragment, TERMINAL_FRAGMENT_TAG)
            }
        }.commitNowAllowingStateLoss()
    }

    private fun ensureKfRuntimeBootstrap() {
        if (kfRuntimeBootstrapRequested) {
            return
        }
        kfRuntimeBootstrapRequested = true
        BootstrapCoordinator.ensureStarted(applicationContext)
    }

    private fun clearRootForScreen(detachTerminal: Boolean = true) {
        terminalBottomNavigation = null
        if (detachTerminal) {
            (supportFragmentManager.findFragmentByTag(TERMINAL_FRAGMENT_TAG) as? TerminalFragment)?.let { fragment ->
                if (fragment.isAdded && !fragment.isDetached) {
                    supportFragmentManager.beginTransaction()
                        .detach(fragment)
                        .commitNowAllowingStateLoss()
                }
            }
        }
        root.removeAllViews()
    }

    private fun applyKiteTerminalTheme() {
        KiteTerminalShellTheme.apply(
            KiteTerminalShellTheme.Palette(
                pageBackground = tokens.pageBackground,
                header = tokens.surfaceElevated,
                surface = tokens.surface,
                textPrimary = tokens.textPrimary,
                textSecondary = tokens.textSecondary,
                border = tokens.border,
                accent = tokens.primaryStrong,
                accentSoft = tokens.primarySoft,
                grayChip = tokens.textTertiary,
                inputBackground = tokens.inputBackground,
                danger = tokens.danger
            )
        )
    }

    private fun showSettings() {
        currentScreen = Screen.Settings
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
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
        clearRootForScreen()
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
                addView(systemTitleButton())
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
    }

    private fun systemTitleButton(): View = row {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, dp(8), 0)
        addView(TextView(context).apply {
            text = "Kite"
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
        })
        addView(systemStatusPill(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply {
            setMargins(dp(10), dp(2), 0, 0)
        })
        setOnClickListener { showUbuntuRuntimePanel(auto = false) }
    }

    private fun systemStatusPill(): TextView = TextView(this).apply {
        val state = ubuntuRuntimeState
        text = systemStatusLabel(state)
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        val color = when {
            state.isProblem -> tokens.danger
            state.blocksUbuntuActions -> tokens.primaryStrong
            state.visible -> tokens.textSecondary
            else -> tokens.success
        }
        setTextColor(color)
        setPadding(dp(8), 0, dp(8), 0)
        background = roundedBox(tintBackground(color), tintBackgroundBorder(color), dp(13).toFloat())
    }

    private fun systemStatusLabel(state: UbuntuRuntimeUiState): String = when {
        state.isProblem -> "异常"
        state.showProgress && state.progressPercent != null -> "解压 ${state.progressPercent}%"
        state.blocksUbuntuActions -> "部署中"
        state.visible -> "未部署"
        else -> "就绪"
    }

    private fun dropZoneControlRow(): View = row {
        setPadding(0, dp(12), 0, 0)
        addView(TextView(context).apply {
            text = if (dropZoneStatus.available) {
                "卡片目录：${dropZoneStatus.recipesPath}"
            } else {
                dropZoneStatus.message
            }
            textSize = 12f
            setTextColor(if (dropZoneStatus.available) tokens.textSecondary else tokens.danger)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(dropZoneButton(if (isDropZoneRefreshing) "刷新中..." else "刷新卡片") { refreshDropZoneRecipes() }.apply {
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

    private fun ubuntuRuntimeBanner(): View? {
        val state = ubuntuRuntimeState
        if (!state.visible) return null
        val border = if (state.isProblem) tokens.danger else tokens.border
        val titleColor = if (state.isProblem) tokens.danger else tokens.textPrimary
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBox(tokens.surfaceElevated, border, dp(16).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(18), 0, dp(18), dp(10)) }
            addView(TextView(context).apply {
                text = state.title
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(titleColor)
            })
            addView(TextView(context).apply {
                text = state.detail
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, 0)
            })
            addView(runtimeProgressView(state, compact = true))
            setOnClickListener { showUbuntuRuntimePanel(auto = false) }
        }
    }

    private fun showCardRunSurface(recipe: KiteRecipe) {
        currentScreen = Screen.CardRun
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val state = focusedRunInstanceId
            ?.let { CardRunStore.get(it) }
            ?: runtimeStateFor(recipe)
        root.addView(cardRunTopBar(recipe, state))
        root.addView(ScrollView(this).apply {
            addView(cardRunContent(state))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun cardRunTopBar(recipe: KiteRecipe, state: RecipeRuntimeState): View = FrameLayout(this).apply {
        setPadding(dp(18), dp(10), dp(18), dp(4))
        addView(cardRunControlPill(recipe, state), FrameLayout.LayoutParams(dp(83), dp(29), Gravity.RIGHT or Gravity.CENTER_VERTICAL))
    }

    private fun cardRunControlPill(recipe: KiteRecipe, state: RecipeRuntimeState): View = row {
        gravity = Gravity.CENTER
        setPadding(dp(2), dp(2), dp(2), dp(2))
        background = roundedBox(tokens.surfaceElevated, tokens.border, dp(15).toFloat())
        elevation = dp(2).toFloat()
        addView(cardRunPillButton("•••") { showCardRunMenu(recipe, state) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(View(context).apply {
            setBackgroundColor(tokens.border)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(17))
        })
        addView(cardRunPillButton("◎") { closeCardRunTask() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun cardRunPillButton(textValue: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = textValue
            textSize = if (textValue == "◎") 15f else 13f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            background = roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, dp(12).toFloat(), 0)
            setOnClickListener { onClick() }
        }

    private fun cardRunContent(state: RecipeRuntimeState): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(28))
            when (state.surface) {
                CardRunSurface.Terminal -> addView(cardRunPlaceholderPanel("终端", state.terminalSessionId ?: "还没有终端会话。"))
                CardRunSurface.Web -> addView(cardRunPlaceholderPanel("网页", state.nextActionUrl ?: "还没有网页地址。"))
                else -> addView(cardRunReportPanel(state))
            }
        }

    private fun cardRunStatusPanel(recipe: KiteRecipe, state: RecipeRuntimeState): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            addView(row {
                addView(TextView(context).apply {
                    text = state.status.label
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (state.failureSummary() != null) tokens.danger else tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(context).apply {
                    text = if (state.stepCount > 0) "${(state.currentStepIndex + 1).coerceAtLeast(0)}/${state.stepCount}" else "--"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(tokens.textSecondary)
                    background = roundedBox(tokens.surface, tokens.border, dp(13).toFloat())
                    layoutParams = LinearLayout.LayoutParams(dp(52), dp(26))
                })
            })
            addView(TextView(context).apply {
                text = cardRunStatusDetail(state)
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(8), 0, 0)
            })
            addView(row {
                setPadding(0, dp(14), 0, 0)
                addView(primaryAction(if (state.isBusy()) "运行中" else "重新执行", displayAccentName(recipe), state.isBusy()) {
                    startRecipe(recipe, state, focusedRunInstanceId)
                })
            })
        }

    private fun cardRunStatusDetail(state: RecipeRuntimeState): String {
        val stepText = if (state.stepCount > 0 && state.currentStepIndex >= 0) {
            "步骤 ${state.currentStepIndex + 1}/${state.stepCount}"
        } else {
            "等待执行"
        }
        val binding = listOfNotNull(
            state.runId?.takeIf { it.isNotBlank() }?.let { "run=$it" },
            state.pid?.takeIf { it.isNotBlank() }?.let { "pid=$it" },
            state.terminalSessionId?.takeIf { it.isNotBlank() }?.let { "terminal=$it" }
        ).joinToString(" · ")
        return listOf(stepText, state.surface.label, binding.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
    }

    private fun cardRunReportPanel(state: RecipeRuntimeState): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 0)
            }
            addView(TextView(context).apply {
                text = "SH 报告"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = cardRunReportText(state)
                textSize = 12.5f
                setTextColor(if (state.failureSummary() != null) tokens.danger else tokens.textSecondary)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(10), 0, 0)
            })
        }

    private fun cardRunReportText(state: RecipeRuntimeState): String {
        val lines = mutableListOf<String>()
        val error = state.lastError.orEmpty().trim()
        val output = state.lastMeaningfulOutput.orEmpty().trim()
        val shellReport = state.shellReportText.orEmpty().trim()
        val hasReport = shellReport.isNotBlank()
        lines += "状态：${state.status.label}"
        lines += "位置：${state.surface.label}"
        lines += cardRunStatusDetail(state)
        commandHintFor(state)?.let { lines += "解释：$it" }
        if (hasReport) {
            lines += shellReport
        } else {
            if (error.isNotBlank()) lines += "错误：$error"
            if (output.isNotBlank() && output != error) lines += "输出：$output"
        }
        if (!state.nextActionUrl.isNullOrBlank()) lines += "网页：${state.nextActionUrl}"
        if (!hasReport && error.isBlank() && output.isBlank() && state.nextActionUrl.isNullOrBlank()) {
            lines += "暂无输出。一次性命令请使用“等待结束”，例如 python3 -V。"
        }
        return lines.joinToString("\n")
    }

    private fun commandHintFor(state: RecipeRuntimeState): String? {
        val text = listOfNotNull(state.lastError, state.lastMeaningfulOutput, state.shellReportText).joinToString("\n")
        val missingCommand = Regex("""(?:^|\n).*?:\s*([A-Za-z0-9_.+-]+): command not found""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        return when {
            text.contains("python: command not found", ignoreCase = true) ->
                "当前环境没有 python 这个别名。先试 python3 -V；如果以后想直接用 python，可以再补一个别名。"
            text.contains("python3: command not found", ignoreCase = true) ->
                "当前环境没有 Python 3，需要先安装 Python。"
            missingCommand != null ->
                "没有找到命令：$missingCommand。一般是还没安装、命令名写错，或者当前环境的 PATH 没包含它。"
            text.contains("Permission denied", ignoreCase = true) ->
                "权限不足，或者这个文件还没有执行权限。"
            text.contains("No such file or directory", ignoreCase = true) ->
                "路径或文件不存在，先检查命令里的目录和文件名。"
            text.contains("timed out", ignoreCase = true) || text.contains("timeout", ignoreCase = true) ->
                "命令超时，可能还在等待输入、网络、服务启动，或者命令本身卡住了。"
            else -> null
        }
    }

    private fun shellReportText(report: KiteRunReport?, recipe: KiteRecipe): String? {
        val shellSteps = report?.steps?.filter { it.type == KiteRecipe.STEP_SHELL }.orEmpty()
        if (shellSteps.isEmpty()) return null
        val recipeStepsById = recipe.steps.associateBy { it.id }
        return shellSteps.joinToString("\n\n") { stepReport ->
            val recipeStep = recipeStepsById[stepReport.stepId]
            shellStepReportText(stepReport, recipeStep)
        }
    }

    private fun shellStepReportText(report: KiteStepReport, step: KiteRecipeStep?): String {
        val lines = mutableListOf<String>()
        step?.cmd?.takeIf { it.isNotBlank() }?.let { lines += "命令：$it" }
        lines += "结果：${shellReportStatusLabel(report)}"
        report.exitCode?.let { lines += "退出码：$it（0 表示命令成功结束）" }
        report.lastMeaningfulOutput.trim().takeIf { it.isNotBlank() }?.let { lines += "有效输出：$it" }
        val rawOutput = report.stdoutTail.trim()
        if (rawOutput.isNotBlank() && rawOutput != report.lastMeaningfulOutput.trim()) {
            lines += "原始输出：\n$rawOutput"
        }
        report.stderrTail.trim().takeIf { it.isNotBlank() && it != rawOutput }?.let { lines += "错误输出：$it" }
        report.matchResult?.takeIf { it.enabled }?.let { match ->
            lines += "匹配：${if (match.matched) "通过" else "未通过"}（${match.text}）"
        }
        if (rawOutput.isBlank() && report.lastMeaningfulOutput.isBlank()) {
            lines += "输出：命令没有打印内容。"
        }
        return lines.joinToString("\n")
    }

    private fun shellReportStatusLabel(report: KiteStepReport): String =
        when {
            report.status == KiteRunReport.STATUS_FINISHED && report.exitCode == 0 -> "成功"
            report.status == KiteRunReport.STATUS_RUNNING -> "已启动"
            report.status == KiteRunReport.STATUS_STOPPED -> "已停止"
            report.status == KiteRunReport.STATUS_FAILED -> "失败"
            else -> report.status
        }

    private fun showCardRunMenu(recipe: KiteRecipe, state: RecipeRuntimeState) {
        val dialog = Dialog(this)
        val sheetFill = Color.WHITE
        val tileFill = Color.rgb(244, 244, 246)
        val primaryText = Color.rgb(20, 20, 24)
        val secondaryText = Color.rgb(105, 105, 112)
        val dividerColor = Color.rgb(232, 232, 236)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            background = roundedTopBox(sheetFill, sheetFill, dp(18).toFloat())
            addView(cardRunMenuHeader(recipe, primaryText, secondaryText))
            addView(cardRunMenuDivider(dividerColor))
            addView(cardRunMenuActionRow(
                listOf(
                    CardRunMenuAction("↻", "刷新") {
                        dialog.dismiss()
                        showCardRunSurface(recipe)
                    },
                    CardRunMenuAction("↺", "重新执行") {
                        dialog.dismiss()
                        startRecipe(recipe, state, focusedRunInstanceId)
                    },
                    CardRunMenuAction("⧉", "复制报告") {
                        copyCardRunReport(state)
                        dialog.dismiss()
                    },
                    CardRunMenuAction("⊙", "关闭实例") {
                        dialog.dismiss()
                        closeCardRunTask()
                    }
                ),
                tileFill,
                primaryText,
                secondaryText
            ))
            addView(cardRunMenuDivider(dividerColor))
            addView(cardRunMenuActionRow(
                listOf(
                    CardRunMenuAction("SH", "SH 报告") {
                        dialog.dismiss()
                        selectCardRunSurface(recipe, CardRunSurface.Report)
                    },
                    CardRunMenuAction(">_", "终端") {
                        dialog.dismiss()
                        selectCardRunSurface(recipe, CardRunSurface.Terminal)
                    },
                    CardRunMenuAction("◎", "网页") {
                        dialog.dismiss()
                        selectCardRunSurface(recipe, CardRunSurface.Web)
                    }
                ),
                tileFill,
                primaryText,
                secondaryText
            ))
            addView(cardRunMenuDivider(dividerColor))
            addView(cardRunMenuCancel(dialog, secondaryText))
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private data class CardRunMenuAction(
        val icon: String,
        val label: String,
        val onClick: () -> Unit
    )

    private fun cardRunMenuHeader(recipe: KiteRecipe, primaryText: Int, secondaryText: Int): View =
        row {
            setPadding(dp(18), dp(14), dp(18), dp(12))
            addView(TextView(context).apply {
                text = iconGlyph(recipe.icon.name)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBox(accentFor(recipe), accentFor(recipe), dp(18).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    setMargins(0, 0, dp(12), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = recipe.name.ifBlank { "Kite 卡片" }
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(primaryText)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = "Kite 卡片实例"
                    textSize = 11.5f
                    setTextColor(secondaryText)
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "›"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(secondaryText)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(36))
            })
        }

    private fun cardRunMenuSectionTitle(title: String, textColor: Int): TextView =
        TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setPadding(dp(22), dp(18), dp(22), dp(8))
        }

    private fun cardRunMenuActionRow(
        actions: List<CardRunMenuAction>,
        tileFill: Int,
        primaryText: Int,
        secondaryText: Int
    ): View = row {
        gravity = Gravity.TOP
        setPadding(dp(16), dp(10), dp(16), dp(10))
        actions.forEach { action ->
            addView(cardRunMenuActionButton(action, tileFill, primaryText, secondaryText), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        repeat((4 - actions.size).coerceAtLeast(0)) {
            addView(View(context), LinearLayout.LayoutParams(0, dp(1), 1f))
        }
    }

    private fun cardRunMenuActionButton(
        action: CardRunMenuAction,
        tileFill: Int,
        primaryText: Int,
        secondaryText: Int
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), 0, dp(4), 0)
            addView(TextView(context).apply {
                text = action.icon
                textSize = if (action.icon.length <= 2) 18f else 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(primaryText)
                background = roundedBox(tileFill, tileFill, dp(11).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            })
            addView(TextView(context).apply {
                text = action.label
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(secondaryText)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { action.onClick() }
        }

    private fun cardRunMenuDivider(color: Int): View =
        View(this).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }

    private fun cardRunMenuCancel(dialog: Dialog, textColor: Int): View =
        TextView(this).apply {
            text = "取消"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(0, dp(13), 0, dp(13))
            setOnClickListener { dialog.dismiss() }
        }

    private fun selectCardRunSurface(recipe: KiteRecipe, surface: CardRunSurface) {
        val instanceId = focusedRunInstanceId
            ?: CardRunStore.currentForRecipe(recipe.id)?.instanceId
            ?: CardRunStore.start(recipe).also { focusedRunInstanceId = it.instanceId }.instanceId
        CardRunStore.selectSurface(instanceId, surface)?.let { state ->
            runtimeStates[recipe.id] = state
        }
        showCardRunSurface(recipe)
    }

    private fun cardRunPlaceholderPanel(title: String, detail: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedBox(tokens.surfaceElevated, tokens.border, dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(12), 0, 0)
            }
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = detail
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(10), 0, 0)
            })
        }

    private fun menuRow(label: String, onClick: () -> Unit): View =
        TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(tokens.textPrimary)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
            setOnClickListener { onClick() }
        }

    private fun copyCardRunReport(state: RecipeRuntimeState) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Kite 执行报告", cardRunReportText(state)))
        Toast.makeText(this, "已复制执行报告", Toast.LENGTH_SHORT).show()
    }

    private fun closeCardRunTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    private fun runtimeProgressView(state: UbuntuRuntimeUiState, compact: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (state.showProgress) View.VISIBLE else View.GONE
            setPadding(0, dp(if (compact) 8 else 14), 0, 0)
            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = state.progressPercent ?: 0
                isIndeterminate = state.progressPercent == null
                progressDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
                indeterminateDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (compact) 5 else 7))
            })
            if (state.progressText.isNotBlank()) {
                addView(TextView(context).apply {
                    text = state.progressText
                    textSize = if (compact) 10.5f else 12f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(5), 0, 0)
                })
            }
        }

    private fun maybeAutoShowUbuntuRuntimePanel(state: UbuntuRuntimeUiState) {
        val startedAt = latestRootfsProgress.startedAt
        if (!state.autoOpenPanel || startedAt <= 0L || autoOpenedRootfsRunAt == startedAt) return
        autoOpenedRootfsRunAt = startedAt
        showUbuntuRuntimePanel(auto = true)
    }

    private fun showUbuntuRuntimePanel(auto: Boolean) {
        val existing = ubuntuRuntimeDialog
        if (existing?.isShowing == true) {
            renderUbuntuRuntimePanelState()
            return
        }

        val dialog = Dialog(this)
        ubuntuRuntimeDialog = dialog

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBox(tokens.pageBackground, tokens.border, dp(24).toFloat())
        }

        content.addView(row {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "系统状态"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "×"
                textSize = 24f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.textSecondary)
                background = roundedBox(tokens.surface, tokens.border, dp(18).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { dialog.dismiss() }
            })
        })

        content.addView(TextView(this).apply {
            runtimePanelTitleView = this
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            setPadding(0, dp(22), 0, 0)
        })
        content.addView(TextView(this).apply {
            runtimePanelDetailView = this
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            runtimePanelProgressBar = this
            max = 100
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                setMargins(0, dp(18), 0, 0)
            }
        })
        content.addView(TextView(this).apply {
            runtimePanelProgressTextView = this
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(7), 0, 0)
        })
        content.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(TextView(this).apply {
            runtimePanelActionButton = this
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.buttonText)
            background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            setOnClickListener { handleRuntimePanelAction() }
        })

        dialog.setContentView(content)
        dialog.setOnDismissListener {
            if (ubuntuRuntimeDialog == dialog) {
                ubuntuRuntimeDialog = null
                runtimePanelTitleView = null
                runtimePanelDetailView = null
                runtimePanelProgressBar = null
                runtimePanelProgressTextView = null
                runtimePanelActionButton = null
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.TOP)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.61f).toInt()
        )
        renderUbuntuRuntimePanelState()
    }

    private fun renderUbuntuRuntimePanelState() {
        val dialog = ubuntuRuntimeDialog
        if (dialog?.isShowing != true) return
        val state = ubuntuRuntimeState
        runtimePanelTitleView?.apply {
            text = if (state.visible) state.title else "Kite 系统就绪"
            setTextColor(if (state.isProblem) tokens.danger else tokens.textPrimary)
        }
        runtimePanelDetailView?.text = if (state.visible) {
            state.detail
        } else {
            "Ubuntu 系统镜像已经准备好，可以启动卡片、终端或网页流程。"
        }
        runtimePanelProgressBar?.apply {
            visibility = if (state.showProgress) View.VISIBLE else View.GONE
            isIndeterminate = state.progressPercent == null
            progress = state.progressPercent ?: 0
            progressDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
            indeterminateDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
        }
        runtimePanelProgressTextView?.apply {
            text = state.progressText
            visibility = if (state.showProgress && state.progressText.isNotBlank()) View.VISIBLE else View.GONE
        }
        runtimePanelActionButton?.apply {
            val shouldRetry = state.canRetry || (state.visible && !state.blocksUbuntuActions)
            text = if (shouldRetry) "重新检查 / 继续部署" else "关闭"
            background = roundedBox(
                if (shouldRetry) tokens.primaryStrong else tokens.surface,
                if (shouldRetry) tokens.primaryStrong else tokens.border,
                dp(14).toFloat()
            )
            setTextColor(if (shouldRetry) tokens.buttonText else tokens.textPrimary)
        }
    }

    private fun handleRuntimePanelAction() {
        val state = ubuntuRuntimeState
        if (state.canRetry || (state.visible && !state.blocksUbuntuActions)) {
            ubuntuRuntimeDialog?.dismiss()
            kfRuntimeBootstrapRequested = false
            ensureKfRuntimeBootstrap()
        } else {
            ubuntuRuntimeDialog?.dismiss()
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
        val swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(tokens.primaryStrong)
            setProgressBackgroundColorSchemeColor(tokens.surfaceElevated)
            isRefreshing = isDropZoneRefreshing
            setOnRefreshListener { refreshDropZoneRecipes(showToast = false) }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
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
        swipeRefresh.addView(scroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return swipeRefresh
    }

    private fun recipeCard(recipe: KiteRecipe): View = LinearLayout(this).apply {
        val runtimeState = runtimeStateFor(recipe)
        val accentName = displayAccentName(recipe)
        val ubuntuBlocked = isUbuntuActionBlocked(recipe)
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
            val label = if (ubuntuBlocked) "\u90e8\u7f72\u4e2d" else primaryLabelForAction(runtimeState)
            addView(primaryAction(label, accentName, runtimeState.isBusy() || ubuntuBlocked) {
                handleRecipeActionWithRouter(recipe)
            })
            addView(editAction { showRecipeEditor(recipe) })
        })
    }

    private fun isUbuntuActionBlocked(recipe: KiteRecipe): Boolean =
        ubuntuRuntimeState.blocksUbuntuActions && recipe.hasUbuntuStep()

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

    private fun handleRecipeActionWithRouter(recipe: KiteRecipe) {
        val state = runtimeStateFor(recipe)
        diagnostics.logRecipeAction(recipe, "card_click", mapOf("type" to recipe.type, "status" to state.status.name))
        if (state.isBusy()) return
        if (isUbuntuActionBlocked(recipe)) {
            Toast.makeText(this, ubuntuRuntimeState.title, Toast.LENGTH_SHORT).show()
            return
        }

        val actionName = if (state.isActive()) KiteRecipe.ACTION_STOP else KiteRecipe.ACTION_START
        if (actionName == KiteRecipe.ACTION_START && shouldOpenCardRunTaskFromHome(recipe)) {
            diagnostics.logRecipeAction(recipe, "card_run_task_requested", mapOf("source" to CardRunIntents.SOURCE_CARD))
            startActivity(
                CardRunIntents.launchIntent(
                    context = this,
                    recipeId = recipe.id,
                    launchSource = CardRunIntents.SOURCE_CARD,
                    autoStart = true
                )
            )
            return
        }
        when (val route = actionRouter.route(recipe, actionName)) {
            is KiteActionRoute.StopRecipe -> stopRecipe(recipe, state)
            is KiteActionRoute.RunRecipe -> startRecipe(route.recipe, state)
            is KiteActionRoute.OpenWeb -> {
                setRuntimeState(recipe, RecipeRunStatus.Opened, nextActionUrl = route.url)
                openWeb(route.url, "recipe_card", recipe)
            }
            is KiteActionRoute.NativeAction -> runNativeAction(recipe, route)
            is KiteActionRoute.Unsupported -> {
                setRuntimeState(recipe, RecipeRunStatus.Failed, lastError = route.reason)
                Toast.makeText(this, route.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shouldOpenCardRunTaskFromHome(recipe: KiteRecipe): Boolean =
        this !is CardRunActivity && recipe.launch.openInstance

    private fun runNativeAction(recipe: KiteRecipe, route: KiteActionRoute.NativeAction) {
        when (route.step.action) {
            KiteRecipe.ANDROID_ACTION_PREPARE_AI_ENV -> {
                ToolchainPackInstaller.prepareAiEnv(applicationContext)
                val url = route.nextUrl ?: recipe.openWebUrl(route.actionName)
                setRuntimeState(recipe, RecipeRunStatus.Opened, nextActionUrl = url)
                if (url.isNotBlank()) openWeb(url, "recipe_card", recipe)
            }
            KiteRecipe.ANDROID_ACTION_TOOLCHAIN_DOCTOR -> {
                ToolchainPackInstaller.doctor(applicationContext)
                val url = route.nextUrl ?: recipe.openWebUrl(route.actionName)
                setRuntimeState(recipe, RecipeRunStatus.Opened, nextActionUrl = url)
                if (url.isNotBlank()) openWeb(url, "recipe_card", recipe)
            }
            else -> {
                setRuntimeState(recipe, RecipeRunStatus.Failed, lastError = "unsupported_android_action")
                Toast.makeText(this, "unsupported_android_action", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleRecipeAction(recipe: KiteRecipe) = handleRecipeActionWithRouter(recipe)

    private fun startRecipe(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        preferredInstanceId: String? = null
    ) {
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
        CardRunStore.start(
            recipe,
            preferredInstanceId ?: activeRunInstanceIds[recipe.id] ?: CardRunIntents.newInstanceId(recipe.id)
        ).also {
            activeRunInstanceIds[recipe.id] = it.instanceId
            runtimeStates[recipe.id] = it
        }
        setRuntimeState(
            recipe,
            RecipeRunStatus.Starting,
            lastMeaningfulOutput = "正在启动流程"
        )
        showConsole()
        executeRecipeStep(recipe, stepIndex = 0)
    }

    private fun runUbuntuStepWhenReady(
        recipe: KiteRecipe,
        stepIndex: Int,
        runId: String?,
        pid: String?,
        surface: CardRunSurface,
        onReady: () -> Unit
    ) {
        setRuntimeState(
            recipe,
            RecipeRunStatus.Running,
            surface = surface,
            currentStepIndex = stepIndex,
            runId = runId,
            pid = pid,
            lastMeaningfulOutput = "正在准备 Ubuntu"
        )
        setUbuntuRuntimeState(
            UbuntuRuntimeUiState(
                title = "\u6b63\u5728\u90e8\u7f72 Ubuntu",
                detail = "当前步骤需要 Ubuntu，正在检查系统镜像、工作区和容器。",
                blocksUbuntuActions = true,
                isProblem = false
            )
        )
        thread(name = "KiteUbuntuPreflight", isDaemon = true) {
            val context = applicationContext
            val ready = runCatching {
                val baseReady = WorkSurfaceRuntimeBridge.isBaseImageReady(context)
                diagnostics.logBridgeEvent(
                    "ubuntu_preflight_start",
                    recipe,
                    mapOf("baseImageReady" to baseReady.toString())
                )
                WorkSurfaceRuntimeBridge.ensureBaseImageReady(context)
                runOnUiThread {
                    setUbuntuRuntimeState(
                        UbuntuRuntimeUiState(
                            title = "\u6b63\u5728\u51c6\u5907 Ubuntu \u5de5\u4f5c\u533a",
                            detail = "\u7cfb\u7edf\u955c\u50cf\u5df2\u5c31\u7eea\uff0c\u6b63\u5728\u521d\u59cb\u5316\u9ed8\u8ba4\u7a7a\u95f4\u548c\u5bb9\u5668\u3002",
                            blocksUbuntuActions = true,
                            isProblem = false
                        )
                    )
                }
                KFWorkspaceManager.ensureDefaultSpace(context)
                WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
                TerminalRuntimeHost.refreshRuntimeSnapshot(context)
                diagnostics.logBridgeEvent("ubuntu_preflight_ok", recipe)
            }
            ready.onSuccess {
                runOnUiThread { setUbuntuRuntimeState(UbuntuRuntimeUiState.hidden()) }
                runOnUiThread { onReady() }
            }.onFailure { error ->
                val message = "Ubuntu \u73af\u5883\u672a\u5c31\u7eea: ${error.message ?: error.javaClass.simpleName}"
                runOnUiThread {
                    setUbuntuRuntimeState(
                        UbuntuRuntimeUiState(
                            title = "\u0055\u0062\u0075\u006e\u0074\u0075 \u90e8\u7f72\u5931\u8d25",
                            detail = message,
                            blocksUbuntuActions = false,
                            isProblem = true
                        )
                    )
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.BridgeUnavailable,
                        currentStepIndex = stepIndex,
                        runId = runId,
                        pid = pid,
                        lastError = message
                    )
                    diagnostics.logBridgeEvent(
                        "ubuntu_preflight_failed",
                        recipe,
                        mapOf("message" to message.take(500))
                    )
                    Toast.makeText(this, message.take(120), Toast.LENGTH_SHORT).show()
                    showConsole()
                }
            }
        }
    }

    private fun executeRecipeStep(
        recipe: KiteRecipe,
        stepIndex: Int,
        runId: String? = null,
        pid: String? = null,
        lastOutput: String? = null
    ) {
        val steps = recipe.steps
        if (stepIndex >= steps.size) {
            setRuntimeState(
                recipe,
                if (!pid.isNullOrBlank()) RecipeRunStatus.Running else RecipeRunStatus.Completed,
                currentStepIndex = stepIndex,
                runId = runId,
                pid = pid,
                lastMeaningfulOutput = lastOutput ?: "流程已完成"
            )
            showConsole()
            return
        }

        val step = steps[stepIndex]
        when (step.type) {
            KiteRecipe.STEP_SHELL -> runUbuntuStepWhenReady(recipe, stepIndex, runId, pid, CardRunSurface.Report) {
                executeShellRecipeStep(recipe, step, stepIndex, runId, pid)
            }
            KiteRecipe.STEP_TERMINAL -> runUbuntuStepWhenReady(recipe, stepIndex, runId, pid, CardRunSurface.Terminal) {
                executeTerminalRecipeStep(recipe, step, stepIndex)
            }
            KiteRecipe.STEP_OPEN_WEB -> {
                val url = step.url.orEmpty().ifBlank { recipe.defaultUrl }
                if (url.isBlank()) {
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.Failed,
                        currentStepIndex = stepIndex,
                        runId = runId,
                        pid = pid,
                        lastError = "open_web_missing_url"
                    )
                    Toast.makeText(this, "打开网页步骤缺少地址", Toast.LENGTH_SHORT).show()
                    showConsole()
                    return
                }
                setRuntimeState(
                    recipe,
                    if (!pid.isNullOrBlank()) RecipeRunStatus.Running else RecipeRunStatus.Opened,
                    surface = CardRunSurface.Web,
                    currentStepIndex = stepIndex,
                    runId = runId,
                    pid = pid,
                    lastMeaningfulOutput = lastOutput,
                    nextActionUrl = url
                )
                if (shouldOpenStepSurface(step)) {
                    openWeb(url, "recipe_sequence", recipe)
                } else {
                    diagnostics.logRecipeAction(
                        recipe,
                        "open_web_surface_suppressed",
                        mapOf("stepIndex" to stepIndex.toString(), "url" to url)
                    )
                }
                if (stepIndex < steps.lastIndex) {
                    executeRecipeStep(recipe, stepIndex + 1, runId, pid, lastOutput)
                } else if (!shouldOpenStepSurface(step)) {
                    showConsole()
                }
            }
            KiteRecipe.STEP_ANDROID_ACTION -> executeAndroidRecipeStep(recipe, step, stepIndex, runId, pid, lastOutput)
            else -> {
                setRuntimeState(
                    recipe,
                    RecipeRunStatus.Failed,
                    surface = surfaceForStep(step),
                    currentStepIndex = stepIndex,
                    runId = runId,
                    pid = pid,
                    lastError = "unsupported_step:${step.type}"
                )
                Toast.makeText(this, "暂不支持的步骤：${step.type}", Toast.LENGTH_SHORT).show()
                showConsole()
            }
        }
    }

    private fun surfaceForStep(step: KiteRecipeStep): CardRunSurface = when (step.type) {
        KiteRecipe.STEP_OPEN_WEB -> CardRunSurface.Web
        KiteRecipe.STEP_TERMINAL -> CardRunSurface.Terminal
        KiteRecipe.STEP_SHELL,
        KiteRecipe.STEP_ANDROID_ACTION -> CardRunSurface.Report
        else -> CardRunSurface.Summary
    }

    private fun shouldOpenStepSurface(step: KiteRecipeStep): Boolean =
        when (KiteRecipe.normalizeSurfaceMode(step.surfaceMode)) {
            KiteRecipe.SURFACE_MODE_PANEL -> true
            KiteRecipe.SURFACE_MODE_SILENT -> false
            else -> step.type == KiteRecipe.STEP_OPEN_WEB || step.type == KiteRecipe.STEP_TERMINAL
        }

    private fun executeShellRecipeStep(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int,
        previousRunId: String?,
        previousPid: String?
    ) {
        if (step.cmd.isNullOrBlank()) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.Failed,
                surface = CardRunSurface.Report,
                currentStepIndex = stepIndex,
                runId = previousRunId,
                pid = previousPid,
                lastError = "shell_missing_command"
            )
            Toast.makeText(this, "sh 命令步骤缺少命令", Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }
        val stepRecipe = recipe.copy(
            execution = KiteExecution.steps(listOf(step)),
            actions = linkedMapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = listOf(step),
                    expected = step.expected ?: recipe.expected
                )
            ),
            expected = step.expected ?: recipe.expected
        )
        setRuntimeState(
            recipe,
            RecipeRunStatus.Running,
            surface = CardRunSurface.Report,
            currentStepIndex = stepIndex,
            runId = previousRunId,
            pid = previousPid,
            lastMeaningfulOutput = "正在执行 sh：${step.cmd.take(80)}",
            shellReportText = "命令：${step.cmd}\n结果：执行中"
        )
        showConsole()
        bridgeClient.runRecipe(stepRecipe) { result ->
            runOnUiThread {
                handleSequenceShellResult(recipe, stepIndex, result)
            }
        }
    }

    private fun executeAndroidRecipeStep(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int,
        runId: String?,
        pid: String?,
        lastOutput: String?
    ) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.Running,
                surface = CardRunSurface.Report,
                currentStepIndex = stepIndex,
                runId = runId,
                pid = pid,
            lastMeaningfulOutput = "正在执行安卓动作：${step.action.orEmpty()}"
        )
        when (step.action) {
            KiteRecipe.ANDROID_ACTION_PREPARE_AI_ENV -> {
                ToolchainPackInstaller.prepareAiEnv(applicationContext)
                executeRecipeStep(recipe, stepIndex + 1, runId, pid, lastOutput ?: "安卓动作完成：prepare_ai_env")
            }
            KiteRecipe.ANDROID_ACTION_TOOLCHAIN_DOCTOR -> {
                ToolchainPackInstaller.doctor(applicationContext)
                executeRecipeStep(recipe, stepIndex + 1, runId, pid, lastOutput ?: "安卓动作完成：toolchain_doctor")
            }
            else -> {
                setRuntimeState(
                    recipe,
                    RecipeRunStatus.Failed,
                    surface = CardRunSurface.Report,
                    currentStepIndex = stepIndex,
                    runId = runId,
                    pid = pid,
                    lastError = "unsupported_android_action"
                )
                Toast.makeText(this, "unsupported_android_action", Toast.LENGTH_SHORT).show()
                showConsole()
            }
        }
    }

    private fun handleSequenceShellResult(
        recipe: KiteRecipe,
        stepIndex: Int,
        result: BridgeResult
    ) {
        val report = result.runReport
        if (report != null) diagnostics.writeRunReport(report)
        val requestId = (report?.requestId ?: result.requestId).orEmpty()
        val runId = report?.runId ?: result.requestId
        val lastOutput = report?.lastMeaningfulOutput()
        val shellReport = shellReportText(report, recipe)
        val pid = report?.pid ?: extractPid(lastOutput) ?: extractPid(result.message)

        if (result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.BridgeUnavailable,
                currentStepIndex = stepIndex,
                runId = runId,
                pid = pid,
                shellReportText = shellReport,
                lastError = result.message
            )
            diagnostics.logRecipeAction(recipe, "sequence_shell_bridge_unavailable", mapOf("requestId" to requestId))
            Toast.makeText(this, "Ubuntu 命令通道不可用", Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        if (report?.hasMismatch() == true || report?.ok == false || report?.status == KiteRunReport.STATUS_FAILED || (!result.ok && !result.accepted)) {
            val message = report?.lastMeaningfulOutput() ?: result.message.ifBlank { "sh 命令执行失败" }
            setRuntimeState(
                recipe,
                RecipeRunStatus.Failed,
                currentStepIndex = stepIndex,
                runId = runId,
                pid = pid,
                lastMeaningfulOutput = lastOutput,
                shellReportText = shellReport,
                lastError = message
            )
            diagnostics.logRecipeAction(
                recipe,
                "sequence_shell_failed",
                mapOf("requestId" to requestId, "status" to result.status, "message" to message.take(500))
            )
            Toast.makeText(this, message.take(120), Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        diagnostics.logRecipeAction(
            recipe,
            "sequence_shell_ok",
            mapOf("requestId" to requestId, "status" to result.status, "stepIndex" to stepIndex.toString())
        )
        setRuntimeState(
            recipe,
            RecipeRunStatus.Running,
            surface = CardRunSurface.Report,
            currentStepIndex = stepIndex,
            runId = runId,
            pid = pid,
            lastMeaningfulOutput = lastOutput,
            shellReportText = shellReport
        )
        executeRecipeStep(recipe, stepIndex + 1, runId, pid, lastOutput)
    }

    private fun executeTerminalRecipeStep(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int
    ) {
        val text = step.text.orEmpty().ifBlank { step.cmd.orEmpty() }
        val appContext = applicationContext
        val record = runCatching {
            val space = KFWorkspaceManager.ensureDefaultSpace(appContext)
            KFWorkspaceManager.createShellSession(
                context = appContext,
                spaceId = space.id,
                title = "${recipe.name} 配置"
            )
        }.getOrElse { error ->
            val message = "创建终端失败：${error.message ?: error.javaClass.simpleName}"
            setRuntimeState(
                recipe,
                RecipeRunStatus.Failed,
                surface = CardRunSurface.Report,
                currentStepIndex = stepIndex,
                lastError = message
            )
            Toast.makeText(this, message.take(120), Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        val instanceId = CardRunStore.currentForRecipe(recipe.id)?.instanceId
            ?: CardRunStore.start(recipe).also { runtimeStates[recipe.id] = it }.instanceId
        pendingTerminalFlow = PendingTerminalFlow(
            recipeId = recipe.id,
            instanceId = instanceId,
            sessionId = record.id,
            nextStepIndex = stepIndex + 1
        )
        setRuntimeState(
            recipe,
            RecipeRunStatus.WaitingTerminal,
            surface = CardRunSurface.Terminal,
            currentStepIndex = stepIndex,
            runId = record.id,
            terminalSessionId = record.id,
            lastMeaningfulOutput = "等待终端完成：${record.title}"
        )
        diagnostics.logRecipeAction(
            recipe,
            "terminal_step_started",
            mapOf("sessionId" to record.id, "stepIndex" to stepIndex.toString())
        )
        val openSurface = shouldOpenStepSurface(step)
        if (openSurface) {
            showTerminal()
        } else {
            diagnostics.logRecipeAction(
                recipe,
                "terminal_surface_suppressed",
                mapOf("sessionId" to record.id, "stepIndex" to stepIndex.toString())
            )
            showConsole()
        }
        TerminalRuntimeHost.switchToSession(appContext, record.id)
        if (text.isNotBlank()) {
            root.postDelayed(
                {
                    TerminalRuntimeHost.sendCommand(
                        appContext = appContext,
                        command = if (text.endsWith("\n")) text else "$text\n",
                        sessionId = record.id
                    )
                },
                TERMINAL_STEP_COMMAND_DELAY_MS
            )
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
        runtimeStates[recipe.id] = CardRunStore.update(
            recipe = recipe,
            status = previousState.status,
            currentStepIndex = previousState.currentStepIndex,
            runId = previousState.runId,
            terminalSessionId = previousState.terminalSessionId,
            pid = previousState.pid,
            lastMeaningfulOutput = previousState.lastMeaningfulOutput,
            lastError = errorMessage,
            nextActionUrl = previousState.nextActionUrl
        )
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
        runtimeStates[recipe.id] = CardRunStore.update(
            recipe = recipe,
            status = previousState.status,
            currentStepIndex = previousState.currentStepIndex,
            runId = previousState.runId,
            terminalSessionId = previousState.terminalSessionId,
            pid = previousState.pid,
            lastMeaningfulOutput = previousState.lastMeaningfulOutput,
            lastError = "停止接口暂不可用",
            nextActionUrl = previousState.nextActionUrl
        )
        Toast.makeText(this, "停止接口暂不可用", Toast.LENGTH_SHORT).show()
        showConsole()
    }

    private fun handleBridgeResult(recipe: KiteRecipe, result: BridgeResult) {
        val report = result.runReport
        if (report != null) diagnostics.writeRunReport(report)
        val requestId = (report?.requestId ?: result.requestId).orEmpty()
        val runId = report?.runId ?: result.requestId
        val lastOutput = report?.lastMeaningfulOutput()
        val shellReport = shellReportText(report, recipe)
        val pid = report?.pid ?: extractPid(lastOutput) ?: extractPid(result.message)

        if (result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE) {
            setRuntimeState(recipe, RecipeRunStatus.BridgeUnavailable, runId = runId, pid = pid, shellReportText = shellReport, lastError = result.message)
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
                shellReportText = shellReport,
                nextActionUrl = nextUrl
            )
            waitForWebReady(recipe, nextUrl, successStatus, runId, pid, lastOutput)
            return
        }

        if (report?.hasMismatch() == true) {
            setRuntimeState(recipe, RecipeRunStatus.Failed, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, shellReportText = shellReport, lastError = "result_mismatch")
            diagnostics.logRecipeAction(recipe, "bridge_result_mismatch", mapOf("requestId" to requestId))
            Toast.makeText(this, "执行结果不匹配，已记录运行报告", Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        if (report != null && (!report.ok || report.status == KiteRunReport.STATUS_FAILED)) {
            setRuntimeState(recipe, RecipeRunStatus.Failed, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, shellReportText = shellReport, lastError = result.message)
            diagnostics.logRecipeAction(recipe, "bridge_failed", mapOf("requestId" to requestId, "message" to result.message.take(500)))
            Toast.makeText(this, "执行失败，已记录运行报告", Toast.LENGTH_SHORT).show()
            showConsole()
            return
        }

        if (result.ok || result.accepted) {
            setRuntimeState(recipe, successfulStatus(report, lastOutput), runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, shellReportText = shellReport)
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
                    val message = "\u670d\u52a1\u672a\u54cd\u5e94\uff0c\u8bf7\u786e\u8ba4\u542f\u52a8\u65e5\u5fd7"
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.Failed,
                        runId = runId,
                        pid = pid,
                        lastMeaningfulOutput = lastOutput,
                        lastError = message,
                        nextActionUrl = url
                    )
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        activeRunInstanceIds[recipe.id]
            ?.let { CardRunStore.get(it) }
            ?.also {
                runtimeStates[recipe.id] = it
            }
            ?: CardRunStore.currentForRecipe(recipe.id)?.also {
            runtimeStates[recipe.id] = it
        } ?: runtimeStates[recipe.id] ?: RecipeRuntimeState.fromRecipeStatus(recipe.id, "unknown").also {
            runtimeStates[recipe.id] = it
        }

    private fun setRuntimeState(
        recipe: KiteRecipe,
        status: RecipeRunStatus,
        surface: CardRunSurface? = null,
        currentStepIndex: Int? = null,
        runId: String? = null,
        terminalSessionId: String? = null,
        pid: String? = null,
        lastMeaningfulOutput: String? = null,
        lastError: String? = null,
        shellReportText: String? = null,
        nextActionUrl: String? = null
    ) {
        val state = CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = activeRunInstanceIds[recipe.id],
            surface = surface,
            currentStepIndex = currentStepIndex,
            runId = runId,
            terminalSessionId = terminalSessionId,
            pid = pid,
            lastMeaningfulOutput = lastMeaningfulOutput,
            lastError = lastError,
            shellReportText = shellReportText,
            nextActionUrl = nextActionUrl
        )
        runtimeStates[recipe.id] = state
        diagnostics.logLifecycleEvent(
            recipe,
            status.lifecycleEvent,
            state.runId,
            state.pid,
            status.name,
            state.lastMeaningfulOutput,
            state.lastError
        )
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
        clearRootForScreen()
        root.addView(createTopBar(if (recipe == null) "新建配置" else "编辑配置"))
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(30), dp(24), dp(92))
                addView(formPanel())
                addView(formDivider())
                addView(sectionTitle("动作流程"))
                addView(stepsPanel())
                addView(formDivider())
                addView(sectionTitle("启动配置"))
                addView(launchOptionsPanel())
                if (recipe != null) {
                    addView(formDivider())
                    addView(navigationRow("查看原始 JSON") { showRecipeRawJson(recipe) }.apply {
                        setPadding(0, dp(16), 0, dp(8))
                    })
                    addView(deleteRow(recipe))
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

        addView(largeRecipeIconTile())
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(nameInput.apply {
                hint = "输入卡片名称"
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
            addView(View(context).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
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
            addView(deleteRow(recipe))
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
        when (draft.type) {
            KiteRecipe.STEP_TERMINAL -> "终端"
            KiteRecipe.STEP_SHELL -> "sh 命令"
            else -> "打开网页"
        }

    private fun stepSummaryText(draft: RecipeStepDraft): String =
        when (draft.type) {
            KiteRecipe.STEP_TERMINAL -> draft.command.ifBlank { "未填写终端输入" }
            KiteRecipe.STEP_SHELL -> draft.command.ifBlank { "未填写 sh 命令" }
            else -> draft.url.ifBlank { "未填写打开地址" }
        }

    private fun showStepDialog(editIndex: Int? = null, initial: RecipeStepDraft? = null) {
        val dialog = Dialog(this)
        val draft = initial ?: RecipeStepDraft.shell()
        var selectedAction = when (draft.type) {
            KiteRecipe.STEP_TERMINAL -> KiteRecipe.STEP_TERMINAL
            KiteRecipe.STEP_OPEN_WEB -> KiteRecipe.STEP_OPEN_WEB
            else -> KiteRecipe.STEP_SHELL
        }
        var commandValue = draft.command
        var urlValue = draft.url
        var workdirValue = draft.workdir

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
            renderStepDialogContent(
                content,
                selectedAction,
                commandValue,
                urlValue,
                workdirValue,
                onCommand = { commandValue = it },
                onUrl = { urlValue = it },
                onWorkdir = { workdirValue = it }
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
                    val next = when (selectedAction) {
                        KiteRecipe.STEP_TERMINAL -> {
                            if (commandValue.isBlank()) {
                                Toast.makeText(context, "请填写终端输入", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            RecipeStepDraft.terminal().apply {
                                command = commandValue.trim()
                            }
                        }
                        KiteRecipe.STEP_SHELL -> {
                            if (commandValue.isBlank()) {
                                Toast.makeText(context, "请填写 sh 命令", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            RecipeStepDraft.shell().apply {
                                command = commandValue.trim()
                                workdir = workdirValue.trim()
                            }
                        }
                        else -> {
                            if (urlValue.isBlank()) {
                                Toast.makeText(context, "请填写网页地址", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            RecipeStepDraft.openWeb().apply {
                                url = urlValue.trim()
                            }
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
        tabRow.addView(tabFactory(">_ 终端", KiteRecipe.STEP_TERMINAL))
        tabRow.addView(tabFactory("sh 命令", KiteRecipe.STEP_SHELL))
        tabRow.addView(tabFactory("◎ 网页", KiteRecipe.STEP_OPEN_WEB))
        tabRow.addView(tabFactory("⋯ 更多", "more"))
    }

    private fun renderStepDialogContent(
        container: LinearLayout,
        selectedAction: String,
        commandValue: String,
        urlValue: String,
        workdirValue: String,
        onCommand: (String) -> Unit,
        onUrl: (String) -> Unit,
        onWorkdir: (String) -> Unit
    ) {
        while (container.childCount > 3) {
            container.removeViewAt(3)
        }
        when (selectedAction) {
            KiteRecipe.STEP_TERMINAL -> {
                container.addView(dialogInput("终端输入", "claude", commandValue, onCommand))
            }
            KiteRecipe.STEP_SHELL -> {
                container.addView(dialogInput("sh 命令", "hermes-web-ui start --port 8648", commandValue, onCommand))
                container.addView(dialogInput("执行位置（可选）", "/workspace/hermes", workdirValue, onWorkdir))
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

    private fun prefillRecipeForm(recipe: KiteRecipe?) {
        val shellStep = recipe?.firstShellStep()
        val openUrl = recipe?.openWebUrl().orEmpty()
        nameInput.setText(recipe?.name.orEmpty())
        nameInput.setSelection(nameInput.text?.length ?: 0)
        descriptionInput.setText(recipe?.description.orEmpty())
        commandInput.setText(shellStep?.cmd.orEmpty())
        workdirInput.setText(shellStep?.workdir ?: recipe?.execution?.workdir.orEmpty())
        urlInput.setText(openUrl)
        val recipeId = recipe?.id.orEmpty()
        shortcutSwitch.isChecked = recipeId.isNotBlank() && cardLocalSettings.shortcutRequested(recipeId)
        launchInstanceSwitch.isChecked = recipe?.launch?.openInstance == true
    }

    private fun updateDynamicFieldVisibility() {
        val hasShell = selectedType.requiresServiceCommand()
        if (::commandFieldContainer.isInitialized) commandFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::workdirFieldContainer.isInitialized) workdirFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::urlFieldContainer.isInitialized) urlFieldContainer.visibility = if (selectedType == KiteRecipe.TYPE_TEMPLATE) View.GONE else View.VISIBLE
    }

    private fun inferTypeFromDrafts(steps: List<RecipeStepDraft> = formSteps): String {
        val hasShell = steps.any {
            (it.type == KiteRecipe.STEP_SHELL || it.type == KiteRecipe.STEP_TERMINAL) && it.command.isNotBlank()
        }
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
                Toast.makeText(this, "第 ${index + 1} 个 sh 命令步骤缺少命令", Toast.LENGTH_SHORT).show()
                return
            }
            if (step.type == KiteRecipe.STEP_TERMINAL && step.command.isBlank()) {
                Toast.makeText(this, "第 ${index + 1} 个终端步骤缺少输入", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val normalizedSteps = formSteps.toList()
        val inferredType = inferTypeFromDrafts(normalizedSteps)
        val defaultUrl = normalizedSteps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB }?.url.orEmpty()
        val requestShortcut = shortcutSwitch.isChecked
        val openInstanceOnStart = launchInstanceSwitch.isChecked
        val previousShortcutRequested = editingRecipe?.id
            ?.takeIf { it.isNotBlank() }
            ?.let { recipeId ->
                cardLocalSettings.shortcutRequested(recipeId) ||
                    CardShortcutManager.hasPinnedShortcut(this, recipeId)
            } == true

        runCatching {
            recipeLoader.saveUserRecipe(
                NewRecipeInput(
                    id = editingRecipe?.id,
                    type = inferredType,
                    name = name,
                    url = defaultUrl,
                    command = "",
                    shortcut = false,
                    openInstanceOnStart = openInstanceOnStart,
                    iconName = selectedIconName,
                    description = description,
                    steps = normalizedSteps.map { it.toInput() }
                )
            )
        }.onSuccess { savedRecipe ->
            val shortcutAlreadyKnown = previousShortcutRequested ||
                cardLocalSettings.shortcutRequested(savedRecipe.id) ||
                CardShortcutManager.hasPinnedShortcut(this, savedRecipe.id)
            if (requestShortcut && !shortcutAlreadyKnown) {
                val requested = CardShortcutManager.requestPinnedShortcut(this, savedRecipe)
                if (requested) {
                    cardLocalSettings.setShortcutRequested(savedRecipe.id, true)
                    Toast.makeText(this, "已提交桌面快捷方式申请", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "当前桌面不支持自动创建快捷方式", Toast.LENGTH_SHORT).show()
                }
            } else {
                cardLocalSettings.setShortcutRequested(savedRecipe.id, requestShortcut || shortcutAlreadyKnown)
            }
            Toast.makeText(this, "已保存配置", Toast.LENGTH_SHORT).show()
            editingRecipe = null
            showConsole()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecipeRawJson(recipe: KiteRecipe) {
        currentScreen = Screen.RecipeDetail
        clearRootForScreen()
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
        clearRootForScreen()
        if (this is CardRunActivity && recipe != null) {
            root.addView(cardRunTopBar(recipe, runtimeStateFor(recipe)))
        } else {
            root.addView(topBar("Kite 工作台") { showConsole() })
        }
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
                renderTypeOptions()
                renderIconOptions()
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

    private fun launchOptionsPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(8))
        addView(localSwitchRow(
            title = "启动时打开独立实例页",
            detail = "从首页启动这张卡片时进入独立最近任务；关闭后就在首页内执行。"
        ) {
            launchInstanceSwitch = it
        })
        addView(divider())
        addView(localSwitchRow(
            title = "申请桌面图标",
            detail = "保存后向桌面发起创建申请，删除快捷方式后不做回收。"
        ) {
            shortcutSwitch = it
        })
    }

    private fun localSwitchRow(title: String, detail: String, bind: (Switch) -> Unit): View = row {
        setPadding(0, dp(4), 0, dp(4))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = detail
                textSize = 10.8f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(2), dp(8), 0)
            })
        })
        val switch = Switch(context).apply { isChecked = false }
        bind(switch)
        addView(switch)
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

    private fun deleteRow(recipe: KiteRecipe): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
        background = roundedBox(Color.WHITE, Color.rgb(229, 231, 235), dp(18).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
            setMargins(0, dp(28), 0, 0)
        }
        addView(TextView(context).apply {
            text = "删除配置"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(242, 85, 74))
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        setOnClickListener { showDeleteRecipeConfirmSheet(recipe) }
    }

    private fun showDeleteRecipeConfirmSheet(recipe: KiteRecipe) {
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(24))
            background = roundedBox(tokens.pageBackground, tokens.pageBackground, dp(24).toFloat())
            addView(TextView(context).apply {
                text = "⌫"
                textSize = 27f
                gravity = Gravity.CENTER
                setTextColor(tokens.danger)
                background = roundedBox(KiteTheme.tint(tokens.danger, 0.88f), KiteTheme.tint(tokens.danger, 0.88f), dp(24).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, dp(12))
                }
            })
            addView(TextView(context).apply {
                text = "删除配置？"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = "这将删除该配置文件及所有动作，且无法撤销。"
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(8), 0, dp(18))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = "取消"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                background = roundedBox(tokens.surface, tokens.border, dp(12).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                    setMargins(0, 0, 0, dp(10))
                }
                setOnClickListener { dialog.dismiss() }
            })
            addView(TextView(context).apply {
                text = "删除配置"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBox(tokens.danger, tokens.danger, dp(12).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
                setOnClickListener {
                    val deleted = recipeLoader.deleteRecipe(recipe)
                    dialog.dismiss()
                    if (deleted) {
                        runtimeStates.remove(recipe.id)
                        Toast.makeText(this@MainActivity, "已删除配置", Toast.LENGTH_SHORT).show()
                        showConsole()
                    } else {
                        Toast.makeText(this@MainActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        addView(navItem(">_", "终端", currentScreen == Screen.Terminal) { showTerminal() })
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
        RecipeRunStatus.WaitingTerminal,
        RecipeRunStatus.AlreadyRunning,
        RecipeRunStatus.Opened,
        RecipeRunStatus.Completed -> SemanticColors(tokens.success, tokens.successSoft, tokens.successBorder)
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

    private fun roundedTopBox(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = dp(1)): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
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
        "primary"

    private fun accentFor(recipe: KiteRecipe): Int = KiteTheme.accent(displayAccentName(recipe), tokens).strong

    private fun primaryLabelForAction(state: RecipeRuntimeState): String = when (state.status) {
        RecipeRunStatus.Starting -> "\u542f\u52a8\u4e2d"
        RecipeRunStatus.WaitingTerminal -> "等待终端"
        RecipeRunStatus.Stopping -> "\u505c\u6b62\u4e2d"
        RecipeRunStatus.Running, RecipeRunStatus.AlreadyRunning -> "\u505c\u6b62"
        RecipeRunStatus.Opened, RecipeRunStatus.Completed, RecipeRunStatus.Failed, RecipeRunStatus.BridgeUnavailable,
        RecipeRunStatus.Unknown, RecipeRunStatus.Stopped -> "\u542f\u52a8"
    }

    private fun primaryLabel(recipe: KiteRecipe, state: RecipeRuntimeState): String = when (state.status) {
        RecipeRunStatus.Starting -> "启动中"
        RecipeRunStatus.WaitingTerminal -> "等待终端"
        RecipeRunStatus.Stopping -> "停止中"
        RecipeRunStatus.Running, RecipeRunStatus.AlreadyRunning, RecipeRunStatus.Opened -> "停止"
        RecipeRunStatus.Completed -> "重跑"
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
        var workdir: String = ""
    ) {
        fun toInput(): NewRecipeStepInput = NewRecipeStepInput(
            type = type,
            command = command,
            url = url,
            workdir = workdir
        )

        companion object {
            fun terminal(): RecipeStepDraft = RecipeStepDraft(type = KiteRecipe.STEP_TERMINAL)

            fun shell(): RecipeStepDraft = RecipeStepDraft(type = KiteRecipe.STEP_SHELL)

            fun openWeb(): RecipeStepDraft = RecipeStepDraft(type = KiteRecipe.STEP_OPEN_WEB)

            fun fromStep(step: KiteRecipeStep): RecipeStepDraft =
                RecipeStepDraft(
                    type = step.type,
                    command = (step.cmd ?: step.text).orEmpty().trimEnd('\n'),
                    url = step.url.orEmpty(),
                    workdir = step.workdir.orEmpty()
                )
        }
    }

    private data class SemanticColors(
        val text: Int,
        val background: Int,
        val border: Int
    )

    private data class UbuntuRuntimeUiState(
        val title: String,
        val detail: String,
        val blocksUbuntuActions: Boolean,
        val isProblem: Boolean,
        val visible: Boolean = true,
        val progressPercent: Int? = null,
        val progressText: String = "",
        val showProgress: Boolean = false,
        val canRetry: Boolean = false,
        val autoOpenPanel: Boolean = false
    ) {
        companion object {
            fun hidden(): UbuntuRuntimeUiState =
                UbuntuRuntimeUiState(
                    title = "",
                    detail = "",
                    blocksUbuntuActions = false,
                    isProblem = false,
                    visible = false
                )
        }
    }

    private enum class Screen {
        Console,
        Terminal,
        Workbench,
        CardRun,
        RecipeDetail,
        CreateConfig,
        Settings,
        ThemeSettings
    }

    override fun setTerminalDetailMode(enabled: Boolean) {
        isTerminalDetailMode = enabled
        terminalBottomNavigation?.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    override fun openTerminalSession(sessionId: String) {
        showTerminal()
        (supportFragmentManager.findFragmentByTag(TERMINAL_FRAGMENT_TAG) as? TerminalFragment)
            ?.openSessionFromExternal(sessionId)
    }

    private fun String.requiresServiceCommand(): Boolean =
        this == KiteRecipe.TYPE_COMMAND_WEB || this == KiteRecipe.TYPE_SCRIPT_WEB || this == KiteRecipe.TYPE_START_SERVICE

    companion object {
        private const val TERMINAL_FRAGMENT_TAG = "kite-terminal"
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private const val WEB_READY_TIMEOUT_MS = 8000L
        private const val WEB_READY_INTERVAL_MS = 700L
        private const val WEB_READY_CONNECT_TIMEOUT_MS = 700
        private const val WEB_READY_READ_TIMEOUT_MS = 700
        private const val TERMINAL_STEP_COMMAND_DELAY_MS = 650L
        private const val REQUEST_DROPZONE_STORAGE = 801
        private val terminalFlowFinishedStatuses = setOf(
            ManagedTerminalStatus.EXITED,
            ManagedTerminalStatus.FAILED,
            ManagedTerminalStatus.STOPPED
        )
    }
}
