package com.kite.app

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.Dialog
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.kite.app.bridge.BridgeProgress
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.bridge.KiteBrowserOpenRequest
import com.kite.app.bridge.KiteBrowserProxyInstaller
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
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallSpec
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.run.CardRunState as RecipeRuntimeState
import com.kite.app.run.CardRunBrowserRouter
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
import com.kftest.app.foundation.toolchain.ToolchainInstallPhase
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

open class MainActivity : AppCompatActivity(), TerminalChromeHost {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var dropZoneManager: KiteDropZoneManager
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var webShell: KiteWebShell
    private lateinit var localServer: KiteLocalServer
    private lateinit var cardLocalSettings: CardLocalSettingsStore
    private lateinit var resourceInstallStore: KiteResourceInstallStore
    private lateinit var themeStore: SharedPreferences
    private lateinit var appSettings: SharedPreferences
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
    private val cardRunTerminalContainerId = View.generateViewId()
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
    private var registeredBrowserInstanceId: String? = null
    private var currentResourceDetailId: String? = null
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
    private var lastWorkbenchUrl: String? = null
    private var lastShellProgressRenderAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = KiteDiagnostics(this)
        diagnostics.writeCapabilityReport()
        themeStore = getSharedPreferences("kite_theme", MODE_PRIVATE)
        appSettings = getSharedPreferences("kite_app_settings", MODE_PRIVATE)
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
        resourceInstallStore = KiteResourceInstallStore(this)
        localServer = KiteLocalServer(applicationContext, diagnostics) { request ->
            runOnUiThread { handleBrowserOpenRequest(request) }
        }
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
        applyRecentTaskVisibilitySetting()
        val handledLaunchIntent = handleCardRunLaunchIntent(intent)
        if (!handledLaunchIntent && !restoreScreenFromBundle(savedInstanceState) && !restoreRecipeDraftFromSettings()) {
            showConsole()
        }
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

    override fun onResume() {
        super.onResume()
        applyRecentTaskVisibilitySetting()
        refreshResourceScreenIfVisible()
    }

    override fun onPause() {
        persistRecipeDraftIfNeeded()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistRecipeDraftIfNeeded()
        outState.putString(STATE_CURRENT_SCREEN, currentScreen.name)
        lastWorkbenchUrl?.let { outState.putString(STATE_WORKBENCH_URL, it) }
        snapshotRecipeFormDraft()?.let { draft ->
            outState.putString(STATE_RECIPE_DRAFT, draft.toJson().toString())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        if (localServerStarted) {
            localServer.stop()
        }
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX Activity migration.")
    override fun onBackPressed() {
        if (this is CardRunActivity) {
            closeCardRunTask()
            return
        }
        when (currentScreen) {
            Screen.CreateConfig -> discardRecipeDraftAndShowConsole()
            Screen.ThemeSettings -> showSettings()
            Screen.ResourceDetail -> showResources()
            Screen.Resources -> showConsole()
            Screen.Settings -> showConsole()
            Screen.Terminal -> if (isTerminalDetailMode) super.onBackPressed() else showConsole()
            else -> if (currentScreen != Screen.Console) showConsole() else super.onBackPressed()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
    }

    private fun handleCardRunLaunchIntent(sourceIntent: Intent?): Boolean {
        val recipeId = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID).orEmpty()
        if (recipeId.isBlank()) return false

        val instanceId = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: CardRunIntents.newInstanceId(recipeId)
        val autoStart = sourceIntent?.getBooleanExtra(CardRunIntents.EXTRA_AUTO_START, true) ?: true
        val launchSource = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_LAUNCH_SOURCE).orEmpty()
        val launchKey = "$recipeId:$instanceId:$autoStart:$launchSource"
        if (consumedCardRunLaunchKey == launchKey) return true
        consumedCardRunLaunchKey = launchKey

        val recipes = recipeLoader.loadAllRecipes()
        currentRecipes = recipes
        val recipe = recipes.firstOrNull { it.id == recipeId }
            ?: CardRunStore.registeredRecipe(recipeId)
            ?: temporaryRecipeFromIntent(sourceIntent, recipeId)
        if (recipe == null) {
            Toast.makeText(this, "未找到卡片：$recipeId", Toast.LENGTH_SHORT).show()
            diagnostics.logRecipeEvent("card_run_launch_missing_recipe", null, mapOf("recipeId" to recipeId))
            return true
        }

        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        title = recipe.name
        applyCardTaskDescription(recipe)
        val state = CardRunStore.get(instanceId) ?: CardRunStore.start(recipe, instanceId)
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        registerCardRunBrowserHandler(recipe, instanceId)
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
        } else if (this is CardRunActivity) {
            showCardRunSurface(recipe)
        } else {
            showConsole()
        }
        return true
    }

    private fun temporaryRecipeFromIntent(sourceIntent: Intent?, recipeId: String): KiteRecipe? {
        val url = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_TEMP_URL)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val title = sourceIntent.getStringExtra(CardRunIntents.EXTRA_TEMP_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "临时网页"
        return temporaryBrowserRecipe(recipeId, url, title)
    }

    private fun temporaryBrowserRecipe(recipeId: String, url: String, title: String = "临时网页"): KiteRecipe =
        KiteRecipe(
            id = recipeId,
            name = title,
            description = "由 Ubuntu 浏览器请求临时打开",
            type = KiteRecipe.TYPE_OPEN_URL,
            category = "temporary",
            defaultUrl = url,
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep(id = "open_$recipeId", type = KiteRecipe.STEP_OPEN_WEB, url = url))
            ),
            actions = linkedMapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = listOf(KiteRecipeStep(id = "open_$recipeId", type = KiteRecipe.STEP_OPEN_WEB, url = url))
                )
            ),
            runtimeSource = "temporary"
        )

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

    private fun shouldHideMainTaskFromRecents(): Boolean =
        appSettings.getBoolean(KEY_HIDE_MAIN_TASK_FROM_RECENTS, false)

    private fun shouldRestoreLastScreen(): Boolean =
        appSettings.getBoolean(KEY_RESTORE_LAST_SCREEN, true)

    private fun applyRecentTaskVisibilitySetting() {
        if (this is CardRunActivity || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !::appSettings.isInitialized) {
            return
        }
        val hideFromRecents = shouldHideMainTaskFromRecents()
        runCatching {
            val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching
            manager.appTasks.forEach { task ->
                val taskInfo = task.taskInfo
                val isCurrentTask = taskInfo.id == taskId
                val isMainTask = taskInfo.baseIntent?.component?.className == MainActivity::class.java.name
                if (isCurrentTask || isMainTask) {
                    task.setExcludeFromRecents(hideFromRecents)
                }
            }
        }
    }

    private fun restoreScreenFromBundle(savedInstanceState: Bundle?): Boolean {
        if (savedInstanceState == null || !shouldRestoreLastScreen()) return false
        val screen = savedInstanceState.getString(STATE_CURRENT_SCREEN)
            ?.let { value -> runCatching { Screen.valueOf(value) }.getOrNull() }
            ?: return false
        return when (screen) {
            Screen.CreateConfig -> {
                val draft = savedInstanceState.getString(STATE_RECIPE_DRAFT)
                    ?.let { RecipeFormDraft.fromJson(it) }
                if (draft == null) {
                    false
                } else {
                    showRecipeForm(recipeForDraft(draft), draft)
                    true
                }
            }
            Screen.Terminal -> {
                showTerminal()
                true
            }
            Screen.Settings -> {
                showSettings()
                true
            }
            Screen.ThemeSettings -> {
                showThemeSettings()
                true
            }
            Screen.Resources -> {
                showResources()
                true
            }
            Screen.Workbench -> {
                val url = savedInstanceState.getString(STATE_WORKBENCH_URL).orEmpty()
                if (url.isBlank()) {
                    false
                } else {
                    showWorkbench(url, "restore", null)
                    true
                }
            }
            else -> false
        }
    }

    private fun restoreRecipeDraftFromSettings(): Boolean {
        if (!shouldRestoreLastScreen()) return false
        val savedAt = appSettings.getLong(KEY_RECIPE_DRAFT_SAVED_AT, 0L)
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > RECIPE_DRAFT_RESTORE_WINDOW_MS) {
            return false
        }
        val draft = appSettings.getString(KEY_RECIPE_DRAFT, null)
            ?.let { RecipeFormDraft.fromJson(it) }
            ?: return false
        showRecipeForm(recipeForDraft(draft), draft)
        Toast.makeText(this, "已恢复未保存配置", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun recipeForDraft(draft: RecipeFormDraft): KiteRecipe? {
        val recipeId = draft.editingRecipeId.takeIf { it.isNotBlank() } ?: return null
        return recipeLoader.loadAllRecipes().firstOrNull { it.id == recipeId }
    }

    private fun persistRecipeDraftIfNeeded() {
        val draft = snapshotRecipeFormDraft() ?: return
        appSettings.edit()
            .putString(KEY_RECIPE_DRAFT, draft.toJson().toString())
            .putLong(KEY_RECIPE_DRAFT_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun snapshotRecipeFormDraft(): RecipeFormDraft? {
        if (currentScreen != Screen.CreateConfig || !::nameInput.isInitialized) return null
        return RecipeFormDraft(
            editingRecipeId = editingRecipe?.id.orEmpty(),
            selectedType = selectedType,
            selectedIconName = selectedIconName,
            name = nameInput.text?.toString().orEmpty(),
            description = if (::descriptionInput.isInitialized) descriptionInput.text?.toString().orEmpty() else "",
            url = if (::urlInput.isInitialized) urlInput.text?.toString().orEmpty() else "",
            command = if (::commandInput.isInitialized) commandInput.text?.toString().orEmpty() else "",
            workdir = if (::workdirInput.isInitialized) workdirInput.text?.toString().orEmpty() else "",
            shortcutRequested = ::shortcutSwitch.isInitialized && shortcutSwitch.isChecked,
            launchOpenInstance = ::launchInstanceSwitch.isInitialized && launchInstanceSwitch.isChecked,
            steps = formSteps.map { it.copy() }
        )
    }

    private fun clearRecipeDraftState() {
        appSettings.edit()
            .remove(KEY_RECIPE_DRAFT)
            .remove(KEY_RECIPE_DRAFT_SAVED_AT)
            .apply()
    }

    private fun discardRecipeDraftAndShowConsole() {
        clearRecipeDraftState()
        editingRecipe = null
        showConsole()
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
            ?: CardRunStore.registeredRecipe(recipeId)
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
        val transaction = supportFragmentManager.beginTransaction()
        var changed = false

        fun detachFragment(tag: String) {
            (supportFragmentManager.findFragmentByTag(tag) as? TerminalFragment)?.let { fragment ->
                if (fragment.isAdded && !fragment.isDetached) {
                    transaction.detach(fragment)
                    changed = true
                }
            }
        }

        if (detachTerminal) {
            detachFragment(TERMINAL_FRAGMENT_TAG)
        }
        detachFragment(CARD_RUN_TERMINAL_FRAGMENT_TAG)
        if (changed) {
            transaction.commitNowAllowingStateLoss()
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
                setPadding(dp(22), dp(8), dp(22), dp(96))
                addView(settingsRow("主题", "主题色、背景色和卡片色彩") { showThemeSettings() })
                addView(settingsSwitchRow(
                    title = "回前台保持现场",
                    subtitle = "切出去复制内容再回来时，保留正在编辑的配置和当前页面。",
                    checked = shouldRestoreLastScreen()
                ) { checked ->
                    appSettings.edit().putBoolean(KEY_RESTORE_LAST_SCREEN, checked).apply()
                })
                addView(settingsSwitchRow(
                    title = "后台隐藏",
                    subtitle = "开启后主应用从最近任务中隐藏；关闭后可从最近任务回到上一步。",
                    checked = shouldHideMainTaskFromRecents()
                ) { checked ->
                    appSettings.edit().putBoolean(KEY_HIDE_MAIN_TASK_FROM_RECENTS, checked).apply()
                    applyRecentTaskVisibilitySetting()
                })
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
                setPadding(dp(22), dp(8), dp(22), dp(96))
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

    private fun showResources() {
        currentResourceDetailId = null
        currentScreen = Screen.Resources
        ToolchainPackInstaller.refreshState(applicationContext)
        val resources = resourceCatalog()
        lateinit var sectionHost: LinearLayout
        fun renderResourceSections(query: String) {
            val cleanQuery = query.trim()
            val visibleResources = if (cleanQuery.isBlank()) {
                resources
            } else {
                resources.filter { it.matchesResourceQuery(cleanQuery) }
            }
            sectionHost.removeAllViews()
            sectionHost.addView(resourceSection(
                title = "精选推荐",
                items = visibleResources.filter { it.section == "精选推荐" }
            ))
            sectionHost.addView(resourceSection(
                title = "快速开始",
                items = visibleResources.filter { it.section == "快速开始" }
            ))
            sectionHost.addView(resourceSection(
                title = "更多资源",
                items = visibleResources.filter { it.section == "更多资源" }
            ))
            if (visibleResources.isEmpty()) {
                sectionHost.addView(resourceSearchEmptyState(cleanQuery))
            }
        }
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val resourceScroll = ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), 0, dp(22), dp(96))
                addView(resourceHeader())
                addView(resourceHero())
                addView(resourceCategoryChips())
                sectionHost = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                addView(sectionHost)
            })
        }
        renderResourceSections("")
        val searchPill = ResourceSearchBar(this) { query ->
            renderResourceSections(query)
        }
        val nav = bottomNavigation()
        attachResourceScrollChrome(resourceScroll, nav, searchPill)
        root.addView(FrameLayout(this).apply {
            addView(resourceScroll, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(searchPill)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(nav)
    }

    private fun resourceHeader(): View = row {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(16), dp(198), 0)
        addView(TextView(context).apply {
            text = "资源"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun attachResourceScrollChrome(scrollView: ScrollView, bottomNav: View, searchPill: ResourceSearchBar) {
        var navHidden = false
        var searchCollapsed = false
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val delta = scrollY - oldScrollY
            when {
                delta > dp(6) && scrollY > dp(28) -> {
                    if (!navHidden) {
                        navHidden = true
                        setBottomNavigationHidden(bottomNav, true)
                    }
                    if (!searchCollapsed) {
                        searchCollapsed = true
                        searchPill.setCollapsed(true)
                    }
                }
                delta < -dp(6) || scrollY <= dp(8) -> {
                    if (navHidden) {
                        navHidden = false
                        setBottomNavigationHidden(bottomNav, false)
                    }
                    if (searchCollapsed) {
                        searchCollapsed = false
                        searchPill.setCollapsed(false)
                    }
                }
            }
        }
    }

    private fun setBottomNavigationHidden(bottomNav: View, hidden: Boolean) {
        val height = bottomNav.height.takeIf { it > 0 } ?: dp(74)
        bottomNav.animate().cancel()
        if (!hidden) {
            bottomNav.visibility = View.VISIBLE
            bottomNav.translationY = height.toFloat()
            bottomNav.alpha = 0f
        }
        bottomNav.animate()
            .translationY(if (hidden) height.toFloat() else 0f)
            .alpha(if (hidden) 0f else 1f)
            .setDuration(180L)
            .withEndAction {
                if (hidden) bottomNav.visibility = View.GONE
            }
            .start()
    }

    private inner class ResourceSearchBar(
        context: Context,
        private val onQueryChanged: (String) -> Unit
    ) : FrameLayout(context) {
        private val expandedWidth = dp(184)
        private val collapsedWidth = dp(42)
        private val barHeight = dp(38)
        private val searchIconSize = dp(42)
        private val widthInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val iconView = TextView(context).apply {
            text = "⌕"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
        }
        private val inputView = EditText(context).apply {
            hint = "搜索资源"
            textSize = 14f
            includeFontPadding = false
            setSingleLine(true)
            maxLines = 1
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(0, 0, 0, 0)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onQueryChanged(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) setCollapsed(false, focusInput = false)
            }
            setOnEditorActionListener { view, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    view.clearFocus()
                    hideKeyboard()
                    true
                } else {
                    false
                }
            }
        }
        private var widthAnimator: ValueAnimator? = null
        private var collapsed = false

        init {
            background = searchBackground(1f)
            elevation = dp(5).toFloat()
            clipChildren = true
            clipToPadding = true
            setOnClickListener {
                setCollapsed(false, focusInput = true)
            }
            addView(inputView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dp(16), 0, searchIconSize - dp(4), 0)
            })
            addView(iconView, FrameLayout.LayoutParams(
                searchIconSize,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ))
            layoutParams = FrameLayout.LayoutParams(expandedWidth, barHeight, Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(13), dp(20), 0)
            }
        }

        fun setCollapsed(nextCollapsed: Boolean, focusInput: Boolean = false) {
            if (nextCollapsed && (inputView.hasFocus() || inputView.text.isNotBlank())) return
            if (collapsed == nextCollapsed) return
            collapsed = nextCollapsed
            val currentWidth = (layoutParams as? FrameLayout.LayoutParams)?.width ?: expandedWidth
            val targetWidth = if (nextCollapsed) collapsedWidth else expandedWidth
            widthAnimator?.cancel()
            widthAnimator = ValueAnimator.ofInt(currentWidth, targetWidth).apply {
                duration = 260L
                interpolator = widthInterpolator
                addUpdateListener { animator ->
                    val width = animator.animatedValue as Int
                    layoutParams = FrameLayout.LayoutParams(width, barHeight, Gravity.TOP or Gravity.END).apply {
                        setMargins(0, dp(13), dp(20), 0)
                    }
                    val progress = ((width - collapsedWidth).toFloat() / (expandedWidth - collapsedWidth).toFloat())
                        .coerceIn(0f, 1f)
                    inputView.alpha = progress
                    inputView.isEnabled = progress > 0.6f
                    background = searchBackground(progress)
                }
                doOnSearchAnimationEnd(nextCollapsed, focusInput)
                start()
            }
        }

        private fun ValueAnimator.doOnSearchAnimationEnd(nextCollapsed: Boolean, focusInput: Boolean) {
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    inputView.alpha = if (nextCollapsed) 0f else 1f
                    inputView.isEnabled = !nextCollapsed
                    background = searchBackground(if (nextCollapsed) 0f else 1f)
                    if (focusInput && !nextCollapsed) focusSearchInput()
                }
            })
        }

        private fun focusSearchInput() {
            inputView.requestFocus()
            inputView.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        private fun hideKeyboard() {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(inputView.windowToken, 0)
        }

        private fun searchBackground(progress: Float): GradientDrawable =
            roundedBox(
                Color.argb((218 + (18 * progress)).toInt(), 255, 255, 255),
                Color.argb((70 + (22 * progress)).toInt(), 168, 184, 194),
                (barHeight / 2f)
            )
    }

    private fun ResourceItem.matchesResourceQuery(query: String): Boolean {
        val needle = query.lowercase()
        return listOf(
            name,
            description,
            version,
            sizeLabel,
            stateLabel,
            section,
            actionLabel,
            iconText,
            accent
        ).any { it.lowercase().contains(needle) } ||
            steps.any { step ->
                step.type.lowercase().contains(needle) ||
                    step.title.lowercase().contains(needle) ||
                    step.preview.lowercase().contains(needle)
            }
    }

    private fun resourceSearchEmptyState(query: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(34), dp(20), dp(34))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(24), 0, 0) }
            addView(TextView(context).apply {
                text = "没有找到相关资源"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = query
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(8), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

    private fun resourceHero(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(224, 251, 249), Color.rgb(248, 253, 255))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(215, 238, 240))
            }
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(214)
            ).apply { setMargins(0, dp(22), 0, dp(18)) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f)
                addView(TextView(context).apply {
                    text = "精选推荐"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.primaryStrong)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    background = roundedBox(Color.argb(160, 255, 255, 255), Color.rgb(166, 223, 221), dp(14).toFloat())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                addView(TextView(context).apply {
                    text = "本地 AI 工作台"
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    includeFontPadding = false
                    maxLines = 2
                    setLineSpacing(dp(1).toFloat(), 1.0f)
                    setPadding(0, dp(16), 0, 0)
                })
                addView(TextView(context).apply {
                    text = "Hermes、Node 与开发环境"
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setLineSpacing(dp(2).toFloat(), 1.0f)
                    setPadding(0, dp(7), 0, 0)
                })
                addView(row {
                    setPadding(0, dp(15), 0, 0)
                    listOf("H" to "Hermes", "JS" to "Node", ">_" to "Dev").forEach { item ->
                        addView(heroMiniIcon(item.first, item.second))
                    }
                })
            })
            addView(ResourceHeroArtView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.78f).apply {
                    setMargins(dp(4), 0, 0, 0)
                }
            })
        }

    private fun heroMiniIcon(label: String, caption: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(50), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dp(7), 0)
            }
            addView(TextView(context).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                background = roundedBox(Color.argb(180, 255, 255, 255), Color.rgb(220, 235, 238), dp(13).toFloat())
                elevation = dp(1).toFloat()
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            })
            addView(TextView(context).apply {
                text = caption
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(5), 0, 0)
            })
        }

    private fun resourceCategoryChips(): View =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row {
                listOf("全部", "已安装", "本地", "Python", "Node", "AI", "系统工具").forEachIndexed { index, label ->
                    addView(TextView(context).apply {
                        text = label
                        textSize = 12.5f
                        typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        setTextColor(if (index == 0) tokens.buttonText else tokens.textPrimary)
                        setPadding(dp(13), dp(8), dp(13), dp(8))
                        background = roundedBox(
                            if (index == 0) tokens.primaryStrong else tokens.surface,
                            if (index == 0) tokens.primaryStrong else tokens.border,
                            dp(18).toFloat()
                        )
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            .apply { setMargins(0, 0, dp(8), 0) }
                    })
                }
            })
        }

    private fun resourceSection(title: String, items: List<ResourceItem>): View =
        LinearLayout(this).apply {
            if (items.isEmpty()) return@apply
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 19f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(context).apply {
                    text = "查看全部 ›"
                    textSize = 12f
                    setTextColor(tokens.primaryStrong)
                })
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
                elevation = dp(1).toFloat()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(12), 0, 0) }
                items.forEachIndexed { index, item ->
                    addView(resourceListRow(item))
                    if (index != items.lastIndex) addView(divider().apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            setMargins(dp(64), dp(8), dp(12), dp(8))
                        }
                    })
                }
            })
        }

    private fun resourceListRow(item: ResourceItem): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
            setOnClickListener { showResourceDetail(item.id) }
            addView(resourceIcon(item.iconText, item.accent))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(12), 0, dp(10), 0)
                }
                addView(TextView(context).apply {
                    text = item.name
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = item.description
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
                addView(TextView(context).apply {
                    text = "${item.version} · ${item.sizeLabel} · ${item.stateLabel}"
                    textSize = 11f
                    setTextColor(tokens.textTertiary)
                    setPadding(0, dp(3), 0, 0)
                })
            })
            addView(resourceActionButton(item, compact = true))
        }

    private fun resourceIcon(textValue: String, accent: String): View =
        TextView(this).apply {
            text = textValue
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val tone = KiteTheme.accent(accent, tokens)
            setTextColor(tone.strong)
            background = roundedBox(tokens.surface, tone.border, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }

    private fun resourceActionButton(item: ResourceItem, compact: Boolean): TextView =
        TextView(this).apply {
            text = item.actionLabel
            textSize = if (compact) 12f else 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            alpha = if (item.actionEnabled) 1f else 0.58f
            background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(15).toFloat(), 0)
            layoutParams = LinearLayout.LayoutParams(if (compact) dp(76) else ViewGroup.LayoutParams.MATCH_PARENT, dp(36))
            if (item.actionEnabled) setOnClickListener { handleResourceAction(item) }
        }

    private fun showResourceDetail(resourceId: String) {
        val item = resourceCatalog().firstOrNull { it.id == resourceId } ?: return
        currentResourceDetailId = resourceId
        currentScreen = Screen.ResourceDetail
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(34))
                addView(resourceDetailChrome())
                addView(resourceDetailHeader(item))
                addView(resourceActionButton(item, compact = false).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                        setMargins(0, dp(24), 0, 0)
                    }
                })
                addView(resourcePreviewStrip(item))
                addView(resourceInfoBlock("简介", item.longDescription))
                addView(resourceExecutionPreviewBlock(item))
                addView(resourceRequirementsBlock(item))
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun resourceDetailChrome(): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(iconButton("‹", dp(32), Color.TRANSPARENT, tokens.textPrimary, dp(14)) { showResources() })
            addView(View(context), LinearLayout.LayoutParams(0, dp(32), 1f))
            addView(iconButton("•••", dp(32), Color.TRANSPARENT, tokens.textPrimary, dp(14)) {
                Toast.makeText(this@MainActivity, "更多操作稍后接入", Toast.LENGTH_SHORT).show()
            })
        }

    private fun resourceDetailHeader(item: ResourceItem): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
            addView(resourceIcon(item.iconText, item.accent).apply {
                elevation = dp(3).toFloat()
                layoutParams = LinearLayout.LayoutParams(dp(78), dp(78))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(16), 0, 0, 0)
                }
                addView(TextView(context).apply {
                    text = item.name
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(tokens.textPrimary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = item.description
                    textSize = 14f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(6), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(row {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(7), 0, 0)
                    val tone = KiteTheme.accent(item.accent, tokens)
                    addView(TextView(context).apply {
                        text = "✓"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.buttonText)
                        background = roundedBox(tone.strong, tone.strong, dp(8).toFloat())
                        layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                    })
                    addView(TextView(context).apply {
                        text = "Kite 官方资源"
                        textSize = 12.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tone.strong)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            .apply { setMargins(dp(7), 0, 0, 0) }
                    })
                })
                addView(TextView(context).apply {
                    text = "${item.version} · ${item.sizeLabel} · ${item.category}"
                    textSize = 12.5f
                    setTextColor(tokens.textTertiary)
                    setPadding(0, dp(7), 0, 0)
                })
            })
        }

    private fun resourceInfoBlock(title: String, body: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(context).apply {
                text = title
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = body
                textSize = 14.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(13), 0, 0)
                setLineSpacing(dp(5).toFloat(), 1.0f)
            })
        }

    private fun resourceBulletBlock(title: String, items: List<String>): View =
        resourceInfoBlock(title, items.joinToString("\n") { "· $it" })

    private fun resourceExecutionPreviewBlock(item: ResourceItem): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
            addView(TextView(context).apply {
                text = "执行预览"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(tokens.textPrimary)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = roundedBox(tokens.cardBackground, tokens.border, dp(17).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(14), 0, 0) }
                val rows = resourceExecutionRows(item)
                rows.forEachIndexed { index, row ->
                    addView(resourceExecutionRow(row, item.accent))
                    if (index != rows.lastIndex) addView(divider().apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            setMargins(dp(48), dp(2), 0, dp(2))
                        }
                    })
                }
            })
        }

    private fun resourceExecutionRow(row: ResourceExecutionRow, accent: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            val tone = KiteTheme.accent(accent, tokens)
            addView(TextView(context).apply {
                text = row.marker
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(tone.strong)
                background = roundedBox(tone.soft, Color.TRANSPARENT, dp(10).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(12), 0, dp(8), 0)
                }
                addView(row {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = row.label
                        textSize = 13.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    })
                    addView(TextView(context).apply {
                        text = row.value
                        textSize = 12.5f
                        typeface = if (row.monospace) Typeface.MONOSPACE else Typeface.DEFAULT
                        setTextColor(tokens.textSecondary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(dp(14), 0, 0, 0)
                        }
                    })
                })
                if (row.note.isNotBlank()) {
                    addView(TextView(context).apply {
                        text = row.note
                        textSize = 11.5f
                        setTextColor(tokens.textTertiary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, dp(3), 0, 0)
                    })
                }
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 22f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.textTertiary)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(34))
            })
        }

    private fun resourceExecutionRows(item: ResourceItem): List<ResourceExecutionRow> =
        when (item.id) {
            RESOURCE_KF_TOOL_ENV -> listOf(
                ResourceExecutionRow("源", "来源", "本地", "内置资源包，不需要先联网下载", monospace = false),
                ResourceExecutionRow("包", "资源位置", KiteResourceInstallRecipes.localPackPath(RESOURCE_KF_TOOL_ENV), "先作为资源包放入工作区", monospace = true),
                ResourceExecutionRow("命", "执行命令", "install.sh --install", "安装脚本负责展开和链接命令", monospace = true),
                ResourceExecutionRow("位", "安装地点", KiteResourceInstallRecipes.softwarePath(RESOURCE_KF_TOOL_ENV), "按注册名放入软件区", monospace = true),
                ResourceExecutionRow("入", "命令入口", "/workspace/.kf/bin", "node、npm、pnpm、uv、adb 从这里暴露", monospace = true),
                ResourceExecutionRow("环", "包含环境", "Node / Python / adb", "用于后续网络包和卡片脚本", monospace = false)
            )
            RESOURCE_HERMES_WEBUI -> listOf(
                ResourceExecutionRow("源", "来源", "下载", "从 npm 获取 hermes-web-ui", monospace = false),
                ResourceExecutionRow("命", "执行命令", "npm install -g hermes-web-ui", "npm 会处理下载和安装细节", monospace = true),
                ResourceExecutionRow("环", "运行环境", "Node.js / npm", "需要先安装 Node.js 资源", monospace = false),
                ResourceExecutionRow("位", "安装地点", "npm 全局目录", "一般不需要用户手动选择路径", monospace = false),
                ResourceExecutionRow("访", "访问入口", "127.0.0.1:8648", "启动后在 Kite 网页里打开", monospace = true)
            )
            "python-314" -> listOf(
                ResourceExecutionRow("源", "来源", "下载", "下载独立 Python 工具链", monospace = false),
                ResourceExecutionRow("命", "执行命令", "download/extract python toolchain", "先下载，再解包到独立目录", monospace = true),
                ResourceExecutionRow("位", "安装地点", "/workspace/.kf/software/kite.python.314", "按注册名放入软件区", monospace = true),
                ResourceExecutionRow("环", "包含环境", "python / pip / venv", "面向插件和项目运行", monospace = false)
            )
            RESOURCE_NODE_RUNTIME -> listOf(
                ResourceExecutionRow("源", "来源", "本地", "内置 Node.js 压缩包，不需要先联网下载", monospace = false),
                ResourceExecutionRow("包", "资源位置", KiteResourceInstallRecipes.localPackPath(RESOURCE_NODE_RUNTIME), "先作为资源包放入工作区", monospace = true),
                ResourceExecutionRow("命", "执行命令", "install.sh --install-node", "只安装 node、npm、npx 三个入口", monospace = true),
                ResourceExecutionRow("位", "安装地点", "${KiteResourceInstallRecipes.softwarePath(RESOURCE_NODE_RUNTIME)}/node-v24.15.0", "按注册名放入软件区", monospace = true),
                ResourceExecutionRow("入", "命令入口", "/workspace/.kf/bin", "node、npm、npx 从这里暴露", monospace = true),
                ResourceExecutionRow("验", "验证命令", "node --version && npm --version", "用于确认当前环境可用", monospace = true)
            )
            "logs-viewer" -> listOf(
                ResourceExecutionRow("源", "来源", "本地", "Kite 内置功能入口", monospace = false),
                ResourceExecutionRow("命", "打开方式", "open resource logs", "后续接入安装日志和 SH 报告", monospace = true),
                ResourceExecutionRow("位", "显示位置", "Kite 日志页", "不写入 Ubuntu 环境", monospace = false)
            )
            else -> {
                val source = if (item.sourceLabel.contains("网络") || item.sizeLabel.contains("网络")) "下载" else "本地"
                val firstStep = item.steps.firstOrNull()
                listOf(
                    ResourceExecutionRow("源", "来源", source, item.sourceLabel, monospace = false),
                    ResourceExecutionRow("命", "执行命令", firstStep?.preview ?: item.actionLabel, firstStep?.title.orEmpty(), monospace = true),
                    ResourceExecutionRow("环", "运行环境", resourceRuntimeLabel(item), "按资源类型准备环境", monospace = false),
                    ResourceExecutionRow("位", "安装地点", resourceInstallLocation(item), "具体路径由安装脚本决定", monospace = true)
                )
            }
        }

    private fun resourceRuntimeLabel(item: ResourceItem): String =
        when (item.category) {
            "AI" -> "Node.js / npm"
            "Python" -> "Python"
            "Node" -> "Node.js / npm"
            else -> "Kite / Ubuntu"
        }

    private fun resourceInstallLocation(item: ResourceItem): String =
        when {
            item.sourceLabel.contains("内置") || item.sizeLabel.contains("内置") -> "/workspace/.kf/cache/resources"
            item.category == "AI" -> "npm 全局目录"
            item.category == "Python" -> "/workspace/.kf/software/kite.python.314"
            else -> "/workspace"
        }

    private fun resourcePreviewStrip(item: ResourceItem): View =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(20), 0, 0) }
            addView(row {
                listOf(
                    ResourcePreviewCard("工作台", "管理模型、对话和提示词", item.iconText, item.accent),
                    ResourcePreviewCard("资源卡片", "一键部署，快速启动", item.iconText, item.accent),
                    ResourcePreviewCard("启动访问", "配置完成后直接打开", "✓", item.accent)
                ).forEachIndexed { index, preview ->
                    addView(resourcePreviewCard(preview).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(176), dp(136)).apply {
                            setMargins(0, 0, if (index == 2) 0 else dp(12), 0)
                        }
                    })
                }
            })
        }

    private fun resourcePreviewCard(preview: ResourcePreviewCard): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(10))
            background = roundedBox(Color.rgb(244, 253, 252), Color.rgb(214, 237, 240), dp(16).toFloat())
            val tone = KiteTheme.accent(preview.accent, tokens)
            addView(TextView(context).apply {
                text = preview.title
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tone.strong)
            })
            addView(TextView(context).apply {
                text = preview.subtitle
                textSize = 11f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = roundedBox(Color.argb(225, 255, 255, 255), Color.rgb(223, 235, 238), dp(13).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(0, dp(10), 0, 0)
                }
                addView(TextView(context).apply {
                    text = preview.symbol
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(tone.strong)
                    background = roundedBox(tone.soft, tone.border, dp(11).toFloat())
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                })
            })
        }

    private fun resourceRequirementsBlock(item: ResourceItem): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(26), 0, 0)
            addView(TextView(context).apply {
                text = "依赖与要求"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(tokens.textPrimary)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = roundedBox(tokens.cardBackground, tokens.border, dp(17).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(14), 0, 0) }
                val rows = listOf(
                    "运行环境" to when (item.category) {
                        "AI" -> "Node.js / npm"
                        "Python" -> "Python"
                        else -> "Kite / Ubuntu"
                    },
                    "安装来源" to item.sourceLabel,
                    "占用空间" to item.sizeLabel,
                    "状态" to item.stateLabel,
                    "资源类型" to item.category
                )
                rows.forEachIndexed { index, row ->
                    addView(resourceRequirementRow(row.first, row.second))
                    if (index != rows.lastIndex) addView(divider().apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            setMargins(0, dp(1), 0, dp(1))
                        }
                    })
                }
            })
        }

    private fun resourceRequirementRow(label: String, value: String): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
            addView(TextView(context).apply {
                text = label
                textSize = 13.5f
                setTextColor(tokens.textSecondary)
                layoutParams = LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = value
                textSize = 13.5f
                setTextColor(tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

    private fun handleResourceAction(item: ResourceItem) {
        when (item.actionLabel) {
            "安装", "重新安装", "获取" -> {
                val recipe = resourceInstallRecipe(item)
                if (recipe == null) {
                    Toast.makeText(this, "${item.name} 的资源脚本稍后接入", Toast.LENGTH_SHORT).show()
                } else {
                    startResourceInstall(item, recipe)
                }
            }
            "卸载", "继续清理" -> {
                val recipe = resourceUninstallRecipe(item)
                if (recipe == null) {
                    resourceInstallStore.clear(item.id)
                    Toast.makeText(this, "已移除 ${item.name} 的安装记录", Toast.LENGTH_SHORT).show()
                    showResources()
                } else {
                    startResourceUninstall(item, recipe)
                }
            }
            else -> Toast.makeText(this, "正在处理 ${item.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resourceInstallRecipe(item: ResourceItem): KiteRecipe? {
        val step = when (item.id) {
            RESOURCE_NODE_RUNTIME -> KiteRecipeStep(
                id = "install_node",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.localToolchainCommand(item.id, "--install-node"),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 900_000L
            )
            RESOURCE_KF_TOOL_ENV -> KiteRecipeStep(
                id = "install_tool_env",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.localToolchainCommand(item.id, "--install"),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 900_000L
            )
            RESOURCE_HERMES_WEBUI -> KiteRecipeStep(
                id = "install_hermes_webui",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.hermesWebUiInstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 900_000L
            )
            else -> null
        } ?: return null
        return KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = item.id,
                name = "${item.name} 安装",
                description = item.description,
                category = "resource",
                iconName = resourceRecipeIcon(item),
                steps = listOf(step)
            )
        )
    }

    private fun resourceUninstallRecipe(item: ResourceItem): KiteRecipe? {
        val step = when (item.id) {
            RESOURCE_NODE_RUNTIME -> KiteRecipeStep(
                id = "uninstall_node",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.nodeUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            RESOURCE_KF_TOOL_ENV -> KiteRecipeStep(
                id = "uninstall_tool_env",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.toolEnvUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            RESOURCE_HERMES_WEBUI -> KiteRecipeStep(
                id = "uninstall_hermes_webui",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.hermesWebUiUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            else -> null
        } ?: return null
        return KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = item.id,
                name = "${item.name} 卸载",
                description = item.description,
                category = "resource",
                iconName = resourceRecipeIcon(item),
                operation = KiteResourceInstallRecipes.OP_UNINSTALL,
                actionLabel = "卸载",
                steps = listOf(step)
            )
        )
    }

    private fun resourceRecipeIcon(item: ResourceItem): String =
        when (item.category) {
            "AI" -> KiteRecipeIcon.ICON_BOT
            "Node", "Python" -> KiteRecipeIcon.ICON_CODE
            else -> KiteRecipeIcon.ICON_TOOLS
        }

    private fun startResourceInstall(item: ResourceItem, recipe: KiteRecipe) {
        resourceInstallStore.markInstalling(item.id)
        startResourceRun(item, recipe, stageBundledResource = item.isBundledResource())
    }

    private fun startResourceUninstall(item: ResourceItem, recipe: KiteRecipe) {
        resourceInstallStore.markUninstalling(item.id)
        startResourceRun(item, recipe, stageBundledResource = false)
    }

    private fun startResourceRun(item: ResourceItem, recipe: KiteRecipe, stageBundledResource: Boolean) {
        val instanceId = CardRunIntents.newInstanceId(recipe.id)
        CardRunStore.registerRecipe(recipe)
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        activeRunInstanceIds[recipe.id] = instanceId
        runtimeStates[recipe.id] = CardRunStore.start(recipe, instanceId)
        setRuntimeState(
            recipe,
            RecipeRunStatus.Starting,
            surface = CardRunSurface.Report,
            currentStepIndex = 0,
            lastMeaningfulOutput = "正在准备资源：${item.name}",
            shellReportText = "资源：${item.name}\n来源：${item.sourceLabel}\n结果：正在准备资源"
        )
        if (this is CardRunActivity) {
            showCardRunSurface(recipe)
        }

        thread(name = "KiteResourceInstall-${item.id}", isDaemon = true) {
            val staged = runCatching {
                if (stageBundledResource) {
                    ToolchainPackInstaller.stageLocalResourcePack(applicationContext, item.id)
                }
            }
            runOnUiThread {
                staged.onSuccess {
                    if (this is CardRunActivity) {
                        startRecipe(recipe, runtimeStateFor(recipe), instanceId, openConsoleOnStart = false)
                    } else {
                        startActivity(
                            CardRunIntents.launchIntent(
                                context = this,
                                recipeId = recipe.id,
                                instanceId = instanceId,
                                launchSource = CardRunIntents.SOURCE_CARD,
                                autoStart = true
                            )
                        )
                    }
                }.onFailure { error ->
                    val message = "资源准备失败：${error.message ?: error.javaClass.simpleName}"
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.Failed,
                        surface = CardRunSurface.Report,
                        currentStepIndex = 0,
                        lastError = message,
                        shellReportText = "资源：${item.name}\n来源：${item.sourceLabel}\n结果：$message"
                    )
                    markResourceInstallFailed(recipe, null, message)
                    if (this is CardRunActivity) {
                        showCardRunSurface(recipe)
                    } else {
                        Toast.makeText(this, message.take(120), Toast.LENGTH_SHORT).show()
                        refreshResourceScreenIfVisible()
                    }
                }
            }
        }
    }

    private fun refreshResourceScreenIfVisible() {
        if (this is CardRunActivity || !::root.isInitialized || !::resourceInstallStore.isInitialized) return
        when (currentScreen) {
            Screen.Resources -> showResources()
            Screen.ResourceDetail -> currentResourceDetailId?.let { showResourceDetail(it) }
            else -> Unit
        }
    }

    private fun ResourceItem.isBundledResource(): Boolean =
        id == RESOURCE_NODE_RUNTIME || id == RESOURCE_KF_TOOL_ENV

    private fun resourceIdForRecipe(recipe: KiteRecipe): String? {
        if (recipe.runtimeSource != KiteResourceInstallRecipes.RUNTIME_SOURCE) return null
        val raw = recipe.id.removePrefix("resource")
            .trimStart('-')
            .removeSuffix("-${KiteResourceInstallRecipes.OP_INSTALL}")
            .removeSuffix("-${KiteResourceInstallRecipes.OP_UNINSTALL}")
        return raw.takeIf { it.isNotBlank() }
    }

    private fun resourceOperationForRecipe(recipe: KiteRecipe): String? {
        if (recipe.runtimeSource != KiteResourceInstallRecipes.RUNTIME_SOURCE) return null
        return when {
            recipe.id.endsWith("-${KiteResourceInstallRecipes.OP_UNINSTALL}") -> KiteResourceInstallRecipes.OP_UNINSTALL
            recipe.id.endsWith("-${KiteResourceInstallRecipes.OP_INSTALL}") -> KiteResourceInstallRecipes.OP_INSTALL
            else -> null
        }
    }

    private fun markResourceRunSuccess(recipe: KiteRecipe, runId: String?, summary: String?) {
        when (resourceOperationForRecipe(recipe)) {
            KiteResourceInstallRecipes.OP_INSTALL -> markResourceInstallSuccess(recipe, runId, summary)
            KiteResourceInstallRecipes.OP_UNINSTALL -> resourceIdForRecipe(recipe)?.let { resourceInstallStore.clear(it) }
        }
    }

    private fun markResourceInstallSuccess(recipe: KiteRecipe, runId: String?, summary: String?) {
        val resourceId = resourceIdForRecipe(recipe) ?: return
        val version = resourceCatalog().firstOrNull { it.id == resourceId }?.version.orEmpty()
        resourceInstallStore.markInstalled(resourceId, version, runId, summary)
    }

    private fun markResourceInstallFailed(recipe: KiteRecipe, runId: String?, reason: String?) {
        val resourceId = resourceIdForRecipe(recipe) ?: return
        val operation = resourceOperationForRecipe(recipe) ?: KiteResourceInstallStore.OP_INSTALL
        resourceInstallStore.markFailed(resourceId, operation, runId, reason)
    }

    private fun normalizeStaleResourceState(resourceId: String) {
        val operation = when {
            resourceInstallStore.isInstalling(resourceId) -> KiteResourceInstallStore.OP_INSTALL
            resourceInstallStore.isUninstalling(resourceId) -> KiteResourceInstallStore.OP_UNINSTALL
            else -> return
        }
        val recipeId = KiteResourceInstallRecipes.recipeId(resourceId, operation)
        val run = CardRunStore.currentForRecipe(recipeId)
        val stillRunning = run?.isBusy() == true || run?.isActive() == true
        if (!stillRunning) {
            val reason = if (operation == KiteResourceInstallStore.OP_UNINSTALL) {
                "清理流程异常中断"
            } else {
                "安装流程异常中断"
            }
            resourceInstallStore.markFailed(resourceId, operation, run?.runId, reason)
        }
    }

    private fun resourceCatalog(): List<ResourceItem> {
        listOf(RESOURCE_NODE_RUNTIME, RESOURCE_KF_TOOL_ENV, RESOURCE_HERMES_WEBUI)
            .forEach { normalizeStaleResourceState(it) }
        val toolchain = ToolchainPackInstaller.state.value
        val nodeWorkspaceInstalled = ToolchainPackInstaller.isNodeRuntimeInstalled(applicationContext)
        val toolchainWorkspaceInstalled = ToolchainPackInstaller.isToolchainPackInstalled(applicationContext)
        val nodeRecordedInstalled = resourceInstallStore.isInstalled(RESOURCE_NODE_RUNTIME)
        val nodeInstallFailed = resourceInstallStore.isFailed(RESOURCE_NODE_RUNTIME)
        val nodeBusy = resourceInstallStore.isBusy(RESOURCE_NODE_RUNTIME)
        val nodeInstalling = resourceInstallStore.isInstalling(RESOURCE_NODE_RUNTIME)
        val nodeFailedOperation = resourceInstallStore.failedOperation(RESOURCE_NODE_RUNTIME)
        val toolchainRecordedInstalled = resourceInstallStore.isInstalled(RESOURCE_KF_TOOL_ENV)
        val toolchainInstallFailed = resourceInstallStore.isFailed(RESOURCE_KF_TOOL_ENV)
        val toolchainBusy = resourceInstallStore.isBusy(RESOURCE_KF_TOOL_ENV)
        val toolchainInstalling = resourceInstallStore.isInstalling(RESOURCE_KF_TOOL_ENV)
        val toolchainFailedOperation = resourceInstallStore.failedOperation(RESOURCE_KF_TOOL_ENV)
        val hermesRecordedInstalled = resourceInstallStore.isInstalled(RESOURCE_HERMES_WEBUI)
        val hermesInstallFailed = resourceInstallStore.isFailed(RESOURCE_HERMES_WEBUI)
        val hermesBusy = resourceInstallStore.isBusy(RESOURCE_HERMES_WEBUI)
        val hermesInstalling = resourceInstallStore.isInstalling(RESOURCE_HERMES_WEBUI)
        val hermesFailedOperation = resourceInstallStore.failedOperation(RESOURCE_HERMES_WEBUI)
        val nodeInstalled = toolchain.phase == ToolchainInstallPhase.SUCCEEDED &&
            (toolchain.action == "node" || toolchain.action == "prepare") || nodeWorkspaceInstalled || nodeRecordedInstalled
        val toolchainInstalled = toolchain.phase == ToolchainInstallPhase.SUCCEEDED &&
            toolchain.action == "prepare" || toolchainWorkspaceInstalled || toolchainRecordedInstalled
        val toolchainRunning = toolchain.phase == ToolchainInstallPhase.RUNNING
        val nodeAction = when {
            toolchainRunning || nodeBusy -> "处理中"
            nodeInstalled -> "卸载"
            nodeInstallFailed && nodeFailedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "继续清理"
            nodeInstallFailed -> "重新安装"
            else -> "安装"
        }
        val nodeState = when {
            toolchainRunning || nodeInstalling -> "安装中"
            nodeBusy -> "清理中"
            nodeInstalled -> "已安装"
            nodeInstallFailed && nodeFailedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "清理异常"
            nodeInstallFailed -> "安装失败"
            else -> "本地包"
        }
        val toolchainAction = when {
            toolchainRunning || toolchainBusy -> "处理中"
            toolchainInstalled -> "卸载"
            toolchainInstallFailed && toolchainFailedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "继续清理"
            toolchainInstallFailed -> "重新安装"
            else -> "安装"
        }
        val toolchainState = when {
            toolchainRunning || toolchainInstalling -> "安装中"
            toolchainBusy -> "清理中"
            toolchainInstalled -> "已安装"
            toolchainInstallFailed && toolchainFailedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "清理异常"
            toolchainInstallFailed -> "安装失败"
            else -> "本地包"
        }
        val hermesAction = when {
            hermesBusy -> "处理中"
            hermesRecordedInstalled -> "卸载"
            hermesInstallFailed && hermesFailedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "继续清理"
            hermesInstallFailed -> "重新安装"
            else -> "获取"
        }
        val hermesState = when {
            hermesInstalling -> "安装中"
            hermesBusy -> "清理中"
            hermesRecordedInstalled -> "已安装"
            hermesInstallFailed && hermesFailedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "清理异常"
            hermesInstallFailed -> "安装失败"
            else -> "未下载"
        }
        return listOf(
            ResourceItem(
                id = RESOURCE_NODE_RUNTIME,
                name = "Node.js",
                description = "现代 JavaScript 运行环境",
                longDescription = "Node.js 是资源页第一条真实样本。它从 Kite 内置资源缓存解压，按注册名安装到软件区，并把 node、npm、npx 暴露到 /workspace/.kf/bin，供后续 Hermes WebUI、网页工具和卡片脚本使用。",
                section = "精选推荐",
                category = "Node",
                iconText = "JS",
                accent = "green",
                version = "24.15.0",
                sizeLabel = "30.1 MB",
                sourceLabel = "内置",
                stateLabel = nodeState,
                actionLabel = nodeAction,
                actionEnabled = !toolchainRunning && !nodeBusy,
                includes = listOf("node 24.15.0", "npm", "npx", "PATH wrapper"),
                notes = listOf("安装位置是 ${KiteResourceInstallRecipes.softwarePath(RESOURCE_NODE_RUNTIME)}/node-v24.15.0", "命令入口是 /workspace/.kf/bin", "重新安装会先清理自己的注册名目录"),
                steps = listOf(
                    ResourceStep("shell", "解压 Node.js", "extract node-v24.15.0-linux-arm64.tar.xz"),
                    ResourceStep("shell", "暴露命令", "link node/npm/npx -> /workspace/.kf/bin"),
                    ResourceStep("shell", "验证版本", "node --version && npm --version")
                )
            ),
            ResourceItem(
                id = RESOURCE_KF_TOOL_ENV,
                name = "工具环境合集",
                description = "pnpm、uv、adb 与常用命令合集",
                longDescription = "工具环境合集是后续可能保留的一键补全包，用来一次性安装 pnpm、uv、adb、jq、rg 等常用命令。当前第一阶段先把 Node.js 拆成独立样本，这个合集降为备用入口。",
                section = "更多资源",
                category = "系统工具",
                iconText = ">_",
                accent = "teal",
                version = "v14",
                sizeLabel = "内置包",
                sourceLabel = "内置",
                stateLabel = toolchainState,
                actionLabel = toolchainAction,
                actionEnabled = !toolchainRunning && !toolchainBusy,
                includes = listOf("Node 24.15.0 / npm / npx", "pnpm 10.33.2", "uv / uvx", "Python venv/pip 支持", "adb / fastboot", "jq / rg / fd / zip / zstd"),
                notes = listOf("首次安装会写入 ${KiteResourceInstallRecipes.softwarePath(RESOURCE_KF_TOOL_ENV)} 与 /workspace/.kf/bin", "部分 Ubuntu apt 依赖仍可能需要网络", "重新安装会先清理自己的注册名目录"),
                steps = listOf(
                    ResourceStep("shell", "复制内置资源包", "mirror assets/toolchain/ai-dev-pack -> ${KiteResourceInstallRecipes.localPackPath(RESOURCE_KF_TOOL_ENV)}"),
                    ResourceStep("shell", "安装工具链", "bash ${KiteResourceInstallRecipes.localPackPath(RESOURCE_KF_TOOL_ENV)}/install.sh --install"),
                    ResourceStep("shell", "暴露命令", "ln -sf node npm pnpm uv adb -> /workspace/.kf/bin")
                )
            ),
            ResourceItem(
                id = RESOURCE_HERMES_WEBUI,
                name = "Hermes WebUI",
                description = "Hermes 的网页工作台",
                longDescription = "用于在浏览器界面里使用 Hermes 的轻量 Web UI。安装动作后续会复用资源步骤，先安装 npm 包，再生成首页启动卡片。",
                section = "精选推荐",
                category = "AI",
                iconText = "H",
                accent = "mint",
                version = "npm",
                sizeLabel = "网络包",
                sourceLabel = "网络",
                stateLabel = hermesState,
                actionLabel = hermesAction,
                actionEnabled = !hermesBusy,
                includes = listOf("hermes-web-ui npm 包", "启动端口 8648", "首页启动卡片"),
                notes = listOf("需要先安装 Node.js 资源", "首次启动可能需要 Hermes 配置"),
                steps = listOf(
                    ResourceStep("shell", "安装 npm 包", "npm install -g hermes-web-ui"),
                    ResourceStep("shell", "启动服务", "hermes-web-ui start --port 8648"),
                    ResourceStep("open_web", "打开网页", "http://127.0.0.1:8648")
                )
            ),
            ResourceItem(
                id = "python-314",
                name = "Python 3.14",
                description = "独立 Python 工具链",
                longDescription = "计划中的独立 Python 工具链资源，不替换系统 /usr/bin/python3，用于需要新版本 Python 的插件和项目。",
                section = "快速开始",
                category = "Python",
                iconText = "Py",
                accent = "blue",
                version = "规划",
                sizeLabel = "网络包",
                sourceLabel = "网络",
                stateLabel = "未下载",
                actionLabel = "获取",
                includes = listOf("Python 3.14", "pip", "venv", "独立 PATH wrapper"),
                notes = listOf("不会覆盖 Ubuntu 系统 Python", "部分 AI 包可能仍需 Python 3.12/3.13"),
                steps = listOf(ResourceStep("shell", "安装独立 Python", "download/extract python toolchain"))
            ),
            ResourceItem(
                id = "open-webui",
                name = "Open WebUI",
                description = "本地模型网页界面",
                longDescription = "面向本地模型服务的 Web UI 候选资源，第一版只作为人工维护清单占位。",
                section = "更多资源",
                category = "AI",
                iconText = "OW",
                accent = "purple",
                version = "规划",
                sizeLabel = "网络包",
                sourceLabel = "网络",
                stateLabel = "未下载",
                actionLabel = "获取",
                includes = listOf("Web UI 服务", "本地端口入口", "首页启动卡片"),
                notes = listOf("需要确认最终安装方式后接入"),
                steps = listOf(ResourceStep("shell", "预留安装步骤", "install open-webui"))
            ),
            ResourceItem(
                id = "logs-viewer",
                name = "日志查看器",
                description = "查看安装与运行日志",
                longDescription = "用于后续统一打开资源安装日志、SH 报告和运行日志的工具入口。",
                section = "更多资源",
                category = "系统工具",
                iconText = "LOG",
                accent = "slate",
                version = "规划",
                sizeLabel = "本地功能",
                sourceLabel = "内置",
                stateLabel = "规划",
                actionLabel = "打开",
                includes = listOf("安装日志", "运行报告", "错误输出"),
                notes = listOf("第一版先展示入口，后续对接日志页"),
                steps = listOf(ResourceStep("android", "打开日志页", "open resource logs"))
            )
        )
    }

    private fun consoleHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(10))
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
            setPadding(0, dp(12), 0, 0)
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
        val terminalSessionId = state.terminalSessionId?.takeIf { it.isNotBlank() }
        val webUrl = state.nextActionUrl?.takeIf { it.isNotBlank() }
        if (state.surface == CardRunSurface.Terminal && terminalSessionId != null) {
            applyKiteTerminalTheme()
            root.addView(FrameLayout(this).apply {
                id = cardRunTerminalContainerId
                setBackgroundColor(tokens.pageBackground)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            showCardRunTerminalFragment(terminalSessionId)
        } else if (state.surface == CardRunSurface.Web && webUrl != null) {
            showCardRunWebView(recipe, webUrl)
        } else {
            root.addView(ScrollView(this).apply {
                addView(cardRunContent(state))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun showCardRunTerminalFragment(sessionId: String) {
        val existing = supportFragmentManager.findFragmentByTag(CARD_RUN_TERMINAL_FRAGMENT_TAG) as? TerminalFragment
        val fragment = existing ?: TerminalFragment.detailOnly(sessionId)
        supportFragmentManager.beginTransaction().apply {
            when {
                fragment.isDetached -> attach(fragment)
                fragment.isAdded -> show(fragment)
                else -> add(cardRunTerminalContainerId, fragment, CARD_RUN_TERMINAL_FRAGMENT_TAG)
            }
        }.commitNowAllowingStateLoss()
        fragment.openSessionFromExternal(sessionId)
    }

    private fun cardRunTopBar(recipe: KiteRecipe, state: RecipeRuntimeState): View = FrameLayout(this).apply {
        setPadding(dp(18), dp(12), dp(18), dp(5))
        if (state.status == RecipeRunStatus.WaitingTerminal && !state.terminalSessionId.isNullOrBlank()) {
            val authUrl = terminalAuthorizationUrl(state.terminalSessionId)
            addView(row {
                addView(cardRunDoneButton { completeTerminalStepFromCard(recipe, state) })
                if (!authUrl.isNullOrBlank()) {
                    addView(cardRunAuthButton {
                        openExternalAuthorizationUrl(recipe, authUrl)
                    })
                }
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(29), Gravity.LEFT or Gravity.CENTER_VERTICAL))
        }
        val rightChrome = cardRunResultIsland(recipe, state) ?: cardRunControlPill(recipe, state)
        addView(
            rightChrome,
            FrameLayout.LayoutParams(
                if (recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE) ViewGroup.LayoutParams.WRAP_CONTENT else dp(92),
                dp(34),
                Gravity.RIGHT or Gravity.CENTER_VERTICAL
            )
        )
    }

    private fun cardRunResultIsland(recipe: KiteRecipe, state: RecipeRuntimeState): View? {
        if (recipe.runtimeSource != KiteResourceInstallRecipes.RUNTIME_SOURCE) return null
        val operation = resourceOperationForRecipe(recipe) ?: KiteResourceInstallRecipes.OP_INSTALL
        val isUninstall = operation == KiteResourceInstallRecipes.OP_UNINSTALL
        val isSuccess = state.status == RecipeRunStatus.Completed
        val isFailure = state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable
        if (!isSuccess && !isFailure) return null

        val label = when {
            isSuccess && isUninstall -> "清理完成"
            isSuccess -> "安装完成"
            isUninstall -> "清理失败 · 重试"
            else -> "安装失败 · 重试"
        }
        val toneColor = if (isFailure) tokens.danger else tokens.primaryStrong
        return TextView(this).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(16), 0, dp(16), 0)
            minWidth = dp(if (isFailure) 118 else 92)
            setTextColor(toneColor)
            background = roundedBox(
                Color.argb(232, 255, 255, 255),
                if (isFailure) tokens.danger else tokens.primarySoft,
                dp(17).toFloat()
            )
            elevation = dp(4).toFloat()
            setOnClickListener {
                if (isSuccess) {
                    closeCardRunTask()
                } else {
                    startRecipe(recipe, state, focusedRunInstanceId, openConsoleOnStart = false)
                }
            }
        }
    }

    private fun showCardRunWebView(recipe: KiteRecipe, url: String) {
        val target = url.trim().ifBlank { DEFAULT_LOCAL_URL }
        diagnostics.logOpenWebAttempt(recipe, target, "card_run_surface")
        diagnostics.writeWebAppStatus(
            url = target,
            title = recipe.name,
            state = "opening",
            recipeId = recipe.id,
            recipeName = recipe.name,
            openSource = "card_run_surface"
        )
        val parent = webView.parent
        if (parent is ViewGroup) parent.removeView(webView)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webShell.loadInWebView(target, recipeId = recipe.id, recipeName = recipe.name, openSource = "card_run_surface")
    }

    private fun cardRunDoneButton(onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = "完成"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.buttonText)
            background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(15).toFloat())
            elevation = dp(2).toFloat()
            setOnClickListener { onClick() }
        }

    private fun cardRunAuthButton(onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = "授权"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            background = roundedBox(tokens.surfaceElevated, tokens.primarySoft, dp(15).toFloat())
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(8), 0, 0, 0)
            }
            setOnClickListener { onClick() }
        }

    private fun openExternalAuthorizationUrl(recipe: KiteRecipe, url: String) {
        diagnostics.logRecipeAction(recipe, "terminal_authorization_url_open", mapOf("url" to url.take(500)))
        val instanceId = focusedRunInstanceId
            ?: CardRunStore.currentForRecipe(recipe.id)?.instanceId
            ?: ensureRunInstanceId(recipe)
        updateBrowserRequestState(
            recipe = recipe,
            instanceId = instanceId,
            request = KiteBrowserOpenRequest(
                url = url,
                recipeId = recipe.id,
                instanceId = instanceId,
                source = "terminal_step"
            )
        )
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        showCardRunSurface(recipe)
    }

    private fun completeTerminalStepFromCard(recipe: KiteRecipe, state: RecipeRuntimeState) {
        val pending = pendingTerminalFlow?.takeIf {
            it.recipeId == recipe.id &&
                (it.instanceId == state.instanceId || it.sessionId == state.terminalSessionId)
        }
        val nextStepIndex = pending?.nextStepIndex ?: (state.currentStepIndex + 1).coerceAtLeast(0)
        pendingTerminalFlow = null
        diagnostics.logRecipeAction(
            recipe,
            "terminal_step_completed_by_user",
            mapOf(
                "sessionId" to state.terminalSessionId.orEmpty(),
                "stepIndex" to state.currentStepIndex.toString(),
                "nextStepIndex" to nextStepIndex.toString()
            )
        )
        executeRecipeStep(
            recipe = recipe,
            stepIndex = nextStepIndex,
            runId = state.terminalSessionId ?: state.runId,
            pid = state.pid,
            lastOutput = "终端已由用户标记完成"
        )
    }

    private fun cardRunControlPill(recipe: KiteRecipe, state: RecipeRuntimeState): View = row {
        gravity = Gravity.CENTER
        setPadding(dp(3), dp(3), dp(3), dp(3))
        background = roundedBox(Color.argb(232, 255, 255, 255), tokens.border, dp(17).toFloat())
        elevation = dp(4).toFloat()
        if (recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE) {
            addView(
                cardRunPillButton("•••") { showCardRunMenu(recipe, state) },
                LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT)
            )
        } else {
            addView(cardRunPillButton("•••") { showCardRunMenu(recipe, state) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(View(context).apply {
                setBackgroundColor(tokens.border)
                layoutParams = LinearLayout.LayoutParams(dp(1), dp(19))
            })
            addView(cardRunPillButton("◎") { closeCardRunTask() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun cardRunPillButton(textValue: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = textValue
            textSize = if (textValue == "◎") 16f else 14f
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

    private fun String.normalizeShellStreamForDisplay(): String =
        replace('\r', '\n')
            .lineSequence()
            .joinToString("\n") { it.trimEnd() }
            .trimEnd()

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
        if (this !is CardRunActivity) {
            val resourceId = focusedRunRecipe()?.let { resourceIdForRecipe(it) }
            if (!resourceId.isNullOrBlank()) {
                showResourceDetail(resourceId)
            } else {
                showConsole()
            }
            return
        }
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

    private fun settingsSwitchRow(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(16), dp(14))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, 0) }
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
                    setPadding(0, dp(4), dp(8), 0)
                })
            })
            val switch = Switch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onChanged(value) }
            }
            addView(switch)
            setOnClickListener { switch.isChecked = !switch.isChecked }
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
            setPadding(dp(10), dp(8), dp(10), dp(92))
            clipToPadding = false
        }
        val cardWidth = ((resources.displayMetrics.widthPixels - dp(36)) / 2).coerceAtLeast(dp(132))
        currentRecipes.forEach { recipe ->
            grid.addView(recipeCard(recipe), GridLayout.LayoutParams().apply {
                width = cardWidth
                height = dp(130)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dp(4), dp(4), dp(4), dp(8))
            })
        }
        scroll.addView(grid)
        swipeRefresh.addView(scroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return swipeRefresh
    }

    private fun recipeCard(recipe: KiteRecipe): View = FrameLayout(this).apply {
        val runtimeState = runtimeStateFor(recipe)
        val ubuntuBlocked = isUbuntuActionBlocked(recipe)
        val statusText = recipeCardStatusText(recipe, runtimeState, ubuntuBlocked)
        background = roundedBox(tokens.cardBackground, tokens.border, dp(24).toFloat())
        elevation = dp(1).toFloat()
        isClickable = true
        setOnClickListener { showRecipeEditor(recipe) }

        addView(iconTile(recipe.icon.name, accentFor(recipe), tintBackground(accentFor(recipe))).apply {
            textSize = 18f
            layoutParams = FrameLayout.LayoutParams(dp(38), dp(38), Gravity.START or Gravity.TOP).apply {
                setMargins(dp(13), dp(13), 0, 0)
            }
        })
        addView(recipePowerButton(recipe, runtimeState, ubuntuBlocked), FrameLayout.LayoutParams(dp(38), dp(38), Gravity.END or Gravity.TOP).apply {
            setMargins(0, dp(13), dp(13), 0)
        })
        addView(recipeCardName(recipe.name), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
            setMargins(dp(15), 0, dp(15), if (statusText.isBlank()) dp(18) else dp(34))
        })
        if (statusText.isNotBlank()) {
            addView(recipeCardStatus(statusText, runtimeState, ubuntuBlocked), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
                setMargins(dp(15), 0, dp(15), dp(15))
            })
        }
    }

    private fun recipePowerButton(recipe: KiteRecipe, state: RecipeRuntimeState, ubuntuBlocked: Boolean): TextView =
        TextView(this).apply {
            val disabled = state.isBusy() || ubuntuBlocked
            val active = state.isActive()
            text = when {
                ubuntuBlocked -> "…"
                state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable -> "↻"
                active -> "■"
                state.isBusy() -> "…"
                else -> "▶"
            }
            textSize = if (text == "▶") 17f else 18f
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = if (disabled) 0.62f else 1f
            val fill = when {
                state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable -> tokens.danger
                active -> tokens.warning
                else -> tokens.primaryStrong
            }
            background = roundedBox(fill, fill, dp(24).toFloat())
            isEnabled = !disabled
            if (!disabled) setOnClickListener {
                handleRecipeActionWithRouter(recipe)
            }
        }

    private fun recipeCardName(text: String): TextView = TextView(this).apply {
        this.text = text.ifBlank { "未命名卡片" }
        textSize = 14.2f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun recipeCardStatus(textValue: String, state: RecipeRuntimeState, ubuntuBlocked: Boolean): TextView =
        TextView(this).apply {
            text = textValue
            textSize = 10.5f
            includeFontPadding = false
            setTextColor(recipeCardStatusColor(state, ubuntuBlocked))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun recipeCardStatusText(recipe: KiteRecipe, state: RecipeRuntimeState, ubuntuBlocked: Boolean): String = when {
        ubuntuBlocked -> "部署中"
        state.status == RecipeRunStatus.Failed -> "启动失败"
        state.status == RecipeRunStatus.BridgeUnavailable -> "桥接不可用"
        state.status == RecipeRunStatus.WaitingTerminal -> "等待终端"
        state.status == RecipeRunStatus.Starting -> "启动中"
        state.status == RecipeRunStatus.Stopping -> "停止中"
        state.status == RecipeRunStatus.Running || state.status == RecipeRunStatus.AlreadyRunning -> "运行中"
        state.status == RecipeRunStatus.Opened -> "已打开"
        state.status == RecipeRunStatus.Completed -> "上次完成"
        else -> recipePassiveLabel(recipe)
    }

    private fun recipeCardStatusColor(state: RecipeRuntimeState, ubuntuBlocked: Boolean): Int = when {
        ubuntuBlocked -> tokens.warning
        state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable -> tokens.danger
        state.status == RecipeRunStatus.WaitingTerminal ||
            state.status == RecipeRunStatus.Starting ||
            state.status == RecipeRunStatus.Stopping ||
            state.status == RecipeRunStatus.Running ||
            state.status == RecipeRunStatus.AlreadyRunning ||
            state.status == RecipeRunStatus.Opened -> tokens.primaryStrong
        else -> tokens.textSecondary
    }

    private fun recipePassiveLabel(recipe: KiteRecipe): String {
        val steps = recipe.steps
        return when {
            steps.size > 1 -> "组合"
            recipe.category.isNotBlank() && recipe.category != KiteRecipe.CATEGORY_UNCATEGORIZED -> recipe.category.take(8)
            else -> ""
        }
    }

    private fun isUbuntuActionBlocked(recipe: KiteRecipe): Boolean =
        ubuntuRuntimeState.blocksUbuntuActions && recipe.hasUbuntuStep()

    private fun cardInfoSlot(recipe: KiteRecipe, runtimeState: RecipeRuntimeState, accentName: String): View =
        LinearLayout(this).apply {
            val isProblem = runtimeState.status == RecipeRunStatus.Failed || runtimeState.status == RecipeRunStatus.BridgeUnavailable
            val tone = KiteTheme.accent(accentName, tokens)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            background = roundedBox(if (isProblem) tokens.dangerSoft else tone.soft, if (isProblem) tokens.dangerBorder else tone.border, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34))
                .apply { setMargins(0, dp(8), 0, dp(6)) }
            addView(cardSummaryText(recipe, runtimeState, isProblem))
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
        preferredInstanceId: String? = null,
        openConsoleOnStart: Boolean = true
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
            surface = if (openConsoleOnStart) null else CardRunSurface.Report,
            lastMeaningfulOutput = "正在启动流程"
        )
        if (openConsoleOnStart) {
            showConsole()
        } else {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id]
            showCardRunSurface(recipe)
        }
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
                    markResourceInstallFailed(recipe, runId, message)
                    toastIfNotResourceRecipe(recipe, message.take(120))
                    showRunSurfaceOrConsole(recipe)
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
            markResourceRunSuccess(recipe, runId, lastOutput)
            setRuntimeState(
                recipe,
                if (!pid.isNullOrBlank()) RecipeRunStatus.Running else RecipeRunStatus.Completed,
                surface = CardRunSurface.Report,
                currentStepIndex = stepIndex,
                runId = runId,
                pid = pid,
                lastMeaningfulOutput = lastOutput ?: "流程已完成"
            )
            showRunSurfaceOrConsole(recipe)
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
                    markResourceInstallFailed(recipe, runId, "open_web_missing_url")
                    toastIfNotResourceRecipe(recipe, "打开网页步骤缺少地址")
                    showRunSurfaceOrConsole(recipe)
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
                if (shouldOpenStepSurface(recipe, step)) {
                    if (shouldRenderInCardRun(recipe)) {
                        focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
                        showCardRunSurface(recipe)
                    } else {
                        openWeb(url, "recipe_sequence", recipe)
                    }
                } else {
                    diagnostics.logRecipeAction(
                        recipe,
                        "open_web_surface_suppressed",
                        mapOf("stepIndex" to stepIndex.toString(), "url" to url)
                    )
                }
                if (stepIndex < steps.lastIndex) {
                    executeRecipeStep(recipe, stepIndex + 1, runId, pid, lastOutput)
                } else if (!shouldOpenStepSurface(recipe, step)) {
                    showRunSurfaceOrConsole(recipe)
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
                markResourceInstallFailed(recipe, runId, "unsupported_step:${step.type}")
                toastIfNotResourceRecipe(recipe, "暂不支持的步骤：${step.type}")
                showRunSurfaceOrConsole(recipe)
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

    private fun shouldRenderInCardRun(recipe: KiteRecipe): Boolean =
        this is CardRunActivity && focusedRunRecipeId == recipe.id

    private fun shouldStayOnRunSurface(recipe: KiteRecipe): Boolean =
        recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE ||
            currentScreen == Screen.CardRun ||
            this is CardRunActivity

    private fun showRunSurfaceOrConsole(recipe: KiteRecipe) {
        if (shouldStayOnRunSurface(recipe)) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
            showCardRunSurface(recipe)
        } else {
            showConsole()
        }
    }

    private fun toastIfNotResourceRecipe(recipe: KiteRecipe, message: String) {
        if (recipe.runtimeSource != KiteResourceInstallRecipes.RUNTIME_SOURCE) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shouldOpenStepSurface(recipe: KiteRecipe, step: KiteRecipeStep): Boolean =
        when (KiteRecipe.normalizeSurfaceMode(step.surfaceMode)) {
            KiteRecipe.SURFACE_MODE_PANEL -> true
            KiteRecipe.SURFACE_MODE_SILENT -> false
            else -> step.type == KiteRecipe.STEP_OPEN_WEB ||
                step.type == KiteRecipe.STEP_TERMINAL ||
                (step.type == KiteRecipe.STEP_SHELL && recipe.launch.openInstance)
        }

    private fun executeShellRecipeStep(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int,
        previousRunId: String?,
        previousPid: String?
    ) {
        val instanceId = ensureRunInstanceId(recipe)
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
            markResourceInstallFailed(recipe, previousRunId, "shell_missing_command")
            toastIfNotResourceRecipe(recipe, "sh 命令步骤缺少命令")
            showRunSurfaceOrConsole(recipe)
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
        if (shouldOpenStepSurface(recipe, step)) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            showCardRunSurface(recipe)
        } else {
            showConsole()
        }
        bridgeClient.runRecipe(
            stepRecipe,
            extraEnv = KiteBrowserProxyInstaller.environment(
                context = applicationContext,
                recipeId = recipe.id,
                instanceId = instanceId,
                source = "shell_step"
            ),
            onProgress = { progress ->
                runOnUiThread { handleShellProgress(recipe, step, stepIndex, progress) }
            }
        ) { result ->
            runOnUiThread {
                handleSequenceShellResult(recipe, stepIndex, result)
            }
        }
    }

    private fun handleShellProgress(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int,
        progress: BridgeProgress
    ) {
        if (progress.recipeId != recipe.id) return
        val state = runtimeStates[recipe.id] ?: CardRunStore.currentForRecipe(recipe.id) ?: return
        if (state.currentStepIndex != stepIndex || state.status != RecipeRunStatus.Running) return
        val output = progress.outputTail.normalizeShellStreamForDisplay()
        val reportText = buildStreamingShellReport(step, output)
        val updated = CardRunStore.update(
            recipe = recipe,
            status = RecipeRunStatus.Running,
            instanceId = state.instanceId,
            surface = CardRunSurface.Report,
            currentStepIndex = stepIndex,
            lastMeaningfulOutput = progress.lastMeaningfulOutput.ifBlank { "正在执行 sh" },
            shellReportText = reportText
        )
        runtimeStates[recipe.id] = updated
        maybeRenderShellProgress(recipe)
    }

    private fun buildStreamingShellReport(step: KiteRecipeStep, output: String): String =
        buildString {
            append("命令：").append(step.cmd.orEmpty()).append('\n')
            append("结果：执行中")
            if (output.isNotBlank()) {
                append("\n原始输出：\n").append(output)
            }
        }

    private fun maybeRenderShellProgress(recipe: KiteRecipe) {
        val now = System.currentTimeMillis()
        if (now - lastShellProgressRenderAt < SHELL_PROGRESS_RENDER_INTERVAL_MS) return
        lastShellProgressRenderAt = now
        when {
            this is CardRunActivity && focusedRunRecipeId == recipe.id -> showCardRunSurface(recipe)
            currentScreen == Screen.Console -> showConsole()
            currentScreen == Screen.CardRun -> showCardRunSurface(recipe)
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
                markResourceInstallFailed(recipe, runId, "unsupported_android_action")
                toastIfNotResourceRecipe(recipe, "unsupported_android_action")
                showRunSurfaceOrConsole(recipe)
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
            markResourceInstallFailed(recipe, runId, result.message.ifBlank { "Ubuntu 命令通道不可用" })
            toastIfNotResourceRecipe(recipe, "Ubuntu 命令通道不可用")
            showRunSurfaceOrConsole(recipe)
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
            markResourceInstallFailed(recipe, runId, message)
            toastIfNotResourceRecipe(recipe, message.take(120))
            showRunSurfaceOrConsole(recipe)
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
            markResourceInstallFailed(recipe, null, message)
            toastIfNotResourceRecipe(recipe, message.take(120))
            showRunSurfaceOrConsole(recipe)
            return
        }

        val instanceId = ensureRunInstanceId(recipe)
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
        TerminalRuntimeHost.setLaunchEnvironmentOverrides(
            appContext = appContext,
            sessionId = record.id,
            overrides = KiteBrowserProxyInstaller.environment(
                context = appContext,
                recipeId = recipe.id,
                instanceId = instanceId,
                source = "terminal_step"
            )
        )
        val openSurface = shouldOpenStepSurface(recipe, step)
        if (openSurface) {
            focusedRunInstanceId = instanceId
            showCardRunSurface(recipe)
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
        startTerminalAuthorizationLinkWatcher(recipe, record.id, instanceId)
    }

    private fun startTerminalAuthorizationLinkWatcher(recipe: KiteRecipe, sessionId: String, instanceId: String) {
        fun tick(startedAt: Long) {
            val active = pendingTerminalFlow?.let {
                it.recipeId == recipe.id && it.sessionId == sessionId && it.instanceId == instanceId
            } == true
            if (!active || System.currentTimeMillis() - startedAt > TERMINAL_AUTH_LINK_WATCH_MS) return

            val authUrl = terminalAuthorizationUrl(sessionId)
            if (!authUrl.isNullOrBlank()) {
                val state = CardRunStore.get(instanceId)
                if (state != null && state.status == RecipeRunStatus.WaitingTerminal) {
                    val updated = CardRunStore.update(
                        recipe = recipe,
                        status = RecipeRunStatus.WaitingTerminal,
                        instanceId = instanceId,
                        surface = CardRunSurface.Terminal,
                        currentStepIndex = state.currentStepIndex,
                        runId = state.runId,
                        terminalSessionId = state.terminalSessionId,
                        pid = state.pid,
                        lastMeaningfulOutput = "检测到授权链接，点击顶部“授权”打开。"
                    )
                    runtimeStates[recipe.id] = updated
                    if (this is CardRunActivity && focusedRunRecipeId == recipe.id && currentScreen == Screen.CardRun) {
                        showCardRunSurface(recipe)
                    }
                }
                return
            }
            root.postDelayed({ tick(startedAt) }, TERMINAL_AUTH_LINK_POLL_MS)
        }
        root.postDelayed({ tick(System.currentTimeMillis()) }, TERMINAL_AUTH_LINK_POLL_MS)
    }

    private fun terminalAuthorizationUrl(sessionId: String?): String? {
        if (sessionId.isNullOrBlank()) return null
        val entry = TerminalRuntimeRegistry.snapshot().firstOrNull { it.sessionId == sessionId } ?: return null
        val transcript = runCatching { File(entry.transcriptPath).readText() }.getOrNull().orEmpty()
        if (transcript.isBlank()) return null
        return extractTerminalAuthorizationUrl(transcript)
    }

    private fun extractTerminalAuthorizationUrl(text: String): String? {
        val urls = Regex("""https?://[^\s<>"']+""")
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']', '}') }
            .toList()
        return urls.firstOrNull { url ->
            url.contains("user_code=", ignoreCase = true) ||
                url.contains("portal.nousresearch.com", ignoreCase = true) ||
                url.contains("login", ignoreCase = true) ||
                url.contains("oauth", ignoreCase = true) ||
                url.contains("authorize", ignoreCase = true)
        } ?: urls.firstOrNull()
    }

    private fun stopRecipe(recipe: KiteRecipe, previousState: RecipeRuntimeState) {
        val terminalSessionId = previousState.terminalSessionId?.takeIf { it.isNotBlank() }
        if (terminalSessionId != null) {
            pendingTerminalFlow = pendingTerminalFlow?.takeUnless {
                it.recipeId == recipe.id && (it.sessionId == terminalSessionId || it.instanceId == previousState.instanceId)
            }
            diagnostics.logBridgeEvent(
                "stop_terminal_session",
                recipe,
                mapOf("sessionId" to terminalSessionId, "runId" to previousState.runId.orEmpty())
            )
            TerminalRuntimeHost.sendCommand(applicationContext, "\u0003", terminalSessionId)
            root.postDelayed(
                { TerminalRuntimeHost.endSession(applicationContext, terminalSessionId) },
                TERMINAL_STOP_GRACE_MS
            )
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                currentStepIndex = previousState.currentStepIndex,
                runId = previousState.runId,
                terminalSessionId = terminalSessionId,
                pid = previousState.pid,
                lastMeaningfulOutput = "终端已发送中断并关闭",
                nextActionUrl = previousState.nextActionUrl
            )
            showConsole()
            return
        }

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
            markResourceInstallFailed(recipe, runId, result.message.ifBlank { "桥接不可用，未执行命令" })
            toastIfNotResourceRecipe(recipe, "桥接不可用，未执行命令")
            showRunSurfaceOrConsole(recipe)
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
            markResourceInstallFailed(recipe, runId, "result_mismatch")
            toastIfNotResourceRecipe(recipe, "执行结果不匹配，已记录运行报告")
            showRunSurfaceOrConsole(recipe)
            return
        }

        if (report != null && (!report.ok || report.status == KiteRunReport.STATUS_FAILED)) {
            setRuntimeState(recipe, RecipeRunStatus.Failed, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, shellReportText = shellReport, lastError = result.message)
            diagnostics.logRecipeAction(recipe, "bridge_failed", mapOf("requestId" to requestId, "message" to result.message.take(500)))
            markResourceInstallFailed(recipe, runId, result.message.ifBlank { "执行失败" })
            toastIfNotResourceRecipe(recipe, "执行失败，已记录运行报告")
            showRunSurfaceOrConsole(recipe)
            return
        }

        if (result.ok || result.accepted) {
            markResourceRunSuccess(recipe, runId, lastOutput)
            setRuntimeState(recipe, successfulStatus(report, lastOutput), runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, shellReportText = shellReport)
            showRunSurfaceOrConsole(recipe)
            return
        }

        setRuntimeState(recipe, RecipeRunStatus.BridgeUnavailable, runId = runId, pid = pid, lastError = result.message)
        markResourceInstallFailed(recipe, runId, result.message.ifBlank { "桥接不可用，未执行命令" })
        toastIfNotResourceRecipe(recipe, "桥接不可用，未执行命令")
        showRunSurfaceOrConsole(recipe)
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

    private fun ensureRunInstanceId(recipe: KiteRecipe): String {
        val existing = activeRunInstanceIds[recipe.id]
            ?: focusedRunInstanceId?.takeIf { CardRunStore.get(it)?.recipeId == recipe.id }
            ?: CardRunStore.currentForRecipe(recipe.id)?.instanceId
        if (!existing.isNullOrBlank()) {
            activeRunInstanceIds[recipe.id] = existing
            return existing
        }
        val state = CardRunStore.start(recipe)
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        return state.instanceId
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

    private fun showRecipeForm(recipe: KiteRecipe?, draft: RecipeFormDraft? = null) {
        currentScreen = Screen.CreateConfig
        editingRecipe = recipe
        formSteps.clear()
        formSteps.addAll(draft?.steps?.map { it.copy() } ?: recipe?.steps?.map { RecipeStepDraft.fromStep(it) } ?: emptyList())
        selectedType = draft?.selectedType?.takeIf { it.isNotBlank() } ?: recipe?.let { inferTypeFromDrafts() } ?: KiteRecipe.TYPE_COMMAND_WEB
        selectedIconName = draft?.selectedIconName?.takeIf { it.isNotBlank() }
            ?: recipe?.icon?.name?.ifBlank { null }
            ?: KiteRecipeIcon.defaultNameForType(selectedType)
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
        prefillRecipeForm(recipe, draft)
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
            KiteRecipe.STEP_TERMINAL -> draft.command.ifBlank { "打开终端" }
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
                container.addView(dialogInput("终端输入", "可留空，留空只打开终端", commandValue, onCommand))
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

    private fun prefillRecipeForm(recipe: KiteRecipe?, draft: RecipeFormDraft? = null) {
        val shellStep = recipe?.firstShellStep()
        val openUrl = recipe?.openWebUrl().orEmpty()
        nameInput.setText(draft?.name ?: recipe?.name.orEmpty())
        nameInput.setSelection(nameInput.text?.length ?: 0)
        descriptionInput.setText(draft?.description ?: recipe?.description.orEmpty())
        commandInput.setText(draft?.command ?: shellStep?.cmd.orEmpty())
        workdirInput.setText(draft?.workdir ?: shellStep?.workdir ?: recipe?.execution?.workdir.orEmpty())
        urlInput.setText(draft?.url ?: openUrl)
        val recipeId = recipe?.id.orEmpty()
        shortcutSwitch.isChecked = draft?.shortcutRequested ?: (recipeId.isNotBlank() && cardLocalSettings.shortcutRequested(recipeId))
        launchInstanceSwitch.isChecked = draft?.launchOpenInstance ?: (recipe?.launch?.openInstance == true)
    }

    private fun updateDynamicFieldVisibility() {
        val hasShell = selectedType.requiresServiceCommand()
        if (::commandFieldContainer.isInitialized) commandFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::workdirFieldContainer.isInitialized) workdirFieldContainer.visibility = if (hasShell) View.VISIBLE else View.GONE
        if (::urlFieldContainer.isInitialized) urlFieldContainer.visibility = if (selectedType == KiteRecipe.TYPE_TEMPLATE) View.GONE else View.VISIBLE
    }

    private fun inferTypeFromDrafts(steps: List<RecipeStepDraft> = formSteps): String {
        val hasShell = steps.any { it.type == KiteRecipe.STEP_SHELL || it.type == KiteRecipe.STEP_TERMINAL }
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
            clearRecipeDraftState()
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

    private fun registerCardRunBrowserHandler(recipe: KiteRecipe, instanceId: String) {
        if (this !is CardRunActivity || instanceId.isBlank()) return
        if (registeredBrowserInstanceId != instanceId) {
            CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
            registeredBrowserInstanceId = instanceId
        }
        CardRunBrowserRouter.register(instanceId) { request ->
            runOnUiThread { openBrowserRequestInCardRun(recipe, instanceId, request) }
            true
        }
    }

    private fun handleBrowserOpenRequest(request: KiteBrowserOpenRequest) {
        val normalized = request.copy(url = request.url.trim())
        if (normalized.url.isBlank()) return
        if (!normalized.instanceId.isNullOrBlank() && CardRunBrowserRouter.dispatch(normalized)) {
            return
        }

        val recipe = normalized.recipeId?.let { findRecipeById(it) }
        val instanceId = normalized.instanceId?.takeIf { it.isNotBlank() }
        if (recipe != null && instanceId != null) {
            updateBrowserRequestState(recipe, instanceId, normalized)
            diagnostics.logRecipeAction(
                recipe,
                "browser_request_waiting_for_instance",
                mapOf("instanceId" to instanceId, "source" to normalized.source, "url" to normalized.url.take(500))
            )
            if (this is CardRunActivity && focusedRunInstanceId == instanceId) {
                showCardRunSurface(recipe)
            }
            return
        }

        openTemporaryBrowserRequest(normalized)
    }

    private fun openTemporaryBrowserRequest(request: KiteBrowserOpenRequest) {
        val intent = CardRunIntents.temporaryWebIntent(
            context = this,
            url = request.url,
            launchSource = request.source.ifBlank { CardRunIntents.SOURCE_BROWSER_PROXY }
        )
        val recipeId = intent.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID).orEmpty()
        val instanceId = intent.getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID).orEmpty()
        val recipe = temporaryBrowserRecipe(recipeId, request.url)
        CardRunStore.start(recipe, instanceId)
        updateBrowserRequestState(
            recipe = recipe,
            instanceId = instanceId,
            request = request.copy(recipeId = recipeId, instanceId = instanceId)
        )
        diagnostics.logRecipeAction(
            recipe,
            "browser_request_opened_temporary_instance",
            mapOf("instanceId" to instanceId, "source" to request.source, "url" to request.url.take(500))
        )
        runCatching {
            startActivity(intent)
        }.onFailure { error ->
            diagnostics.logOpenWebFailed(recipe, request.url, error.message.orEmpty())
            Toast.makeText(this, "打开临时网页失败：${error.message}", Toast.LENGTH_SHORT).show()
            openWeb(request.url, request.source, recipe)
        }
    }

    private fun openBrowserRequestInCardRun(
        recipe: KiteRecipe,
        instanceId: String,
        request: KiteBrowserOpenRequest
    ) {
        val state = updateBrowserRequestState(recipe, instanceId, request)
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = state.instanceId
        title = recipe.name
        diagnostics.logRecipeAction(
            recipe,
            "browser_request_opened_in_instance",
            mapOf("instanceId" to instanceId, "source" to request.source, "url" to request.url.take(500))
        )
        showCardRunSurface(recipe)
    }

    private fun updateBrowserRequestState(
        recipe: KiteRecipe,
        instanceId: String,
        request: KiteBrowserOpenRequest
    ): RecipeRuntimeState {
        val existing = CardRunStore.get(instanceId)
        val status = when (existing?.status) {
            RecipeRunStatus.Starting,
            RecipeRunStatus.Running,
            RecipeRunStatus.WaitingTerminal -> existing.status
            else -> RecipeRunStatus.Opened
        }
        activeRunInstanceIds[recipe.id] = instanceId
        return CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId,
            surface = CardRunSurface.Web,
            currentStepIndex = existing?.currentStepIndex,
            runId = existing?.runId,
            terminalSessionId = existing?.terminalSessionId,
            pid = existing?.pid,
            lastMeaningfulOutput = "Ubuntu 请求打开网页",
            nextActionUrl = request.url
        ).also { state ->
            runtimeStates[recipe.id] = state
        }
    }

    private fun findRecipeById(recipeId: String): KiteRecipe? {
        if (currentRecipes.isEmpty()) {
            currentRecipes = recipeLoader.loadAllRecipes()
        }
        return currentRecipes.firstOrNull { it.id == recipeId }
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
        lastWorkbenchUrl = url
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
        addView(iconButton("‹", dp(40), Color.TRANSPARENT, tokens.textPrimary, dp(14)) { discardRecipeDraftAndShowConsole() })
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
            setOnClickListener { discardRecipeDraftAndShowConsole() }
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
        setPadding(dp(14), dp(3), dp(14), dp(4))
        setBackgroundColor(Color.argb(
            238,
            Color.red(tokens.surfaceElevated),
            Color.green(tokens.surfaceElevated),
            Color.blue(tokens.surfaceElevated)
        ))
        elevation = dp(6).toFloat()
        addView(navItem("▦", "配置", currentScreen == Screen.Console) { showConsole() })
        addView(navItem(">_", "终端", currentScreen == Screen.Terminal) { showTerminal() })
        addView(navItem("≡", "资源", currentScreen == Screen.Resources || currentScreen == Screen.ResourceDetail) { showResources() })
        addView(navItem("⚙", "设置", currentScreen == Screen.Settings || currentScreen == Screen.ThemeSettings) { showSettings() })
    }

    private fun navItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(47), 1f)
        addView(TextView(context).apply {
            text = icon
            textSize = if (icon == "≡") 17f else 19f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
            )
        })
        addView(TextView(context).apply {
            text = label
            textSize = 10.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(17)
            ).apply {
                setMargins(0, dp(1), 0, 0)
            }
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

    private fun cardTitleCompact(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14.5f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun cardMetaLine(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 10.5f
        includeFontPadding = false
        setTextColor(tokens.textTertiary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, 0)
    }

    private fun cardSummaryText(recipe: KiteRecipe, state: RecipeRuntimeState, isProblem: Boolean): TextView =
        TextView(this).apply {
            text = cardSummary(recipe, state, isProblem)
            textSize = 10.8f
            includeFontPadding = false
            setTextColor(if (isProblem) tokens.danger else tokens.textSecondary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.08f)
        }

    private fun cardKindLine(recipe: KiteRecipe): String {
        val firstStep = recipe.steps.firstOrNull()
        val kind = when (firstStep?.type) {
            KiteRecipe.STEP_SHELL -> "SH"
            KiteRecipe.STEP_TERMINAL -> "终端"
            KiteRecipe.STEP_OPEN_WEB -> "网页"
            KiteRecipe.STEP_ANDROID_ACTION -> "本机"
            else -> if (recipe.defaultUrl.isNotBlank()) "网页" else "卡片"
        }
        val count = recipe.steps.size.takeIf { it > 0 }?.let { "${it} 步" } ?: "单步"
        return "$kind · $count"
    }

    private fun cardSummary(recipe: KiteRecipe, state: RecipeRuntimeState, isProblem: Boolean): String {
        val feedback = state.feedbackSummary()
        if (isProblem && !feedback.isNullOrBlank()) return feedback
        if (state.status == RecipeRunStatus.Starting ||
            state.status == RecipeRunStatus.Running ||
            state.status == RecipeRunStatus.WaitingTerminal ||
            state.status == RecipeRunStatus.Stopping ||
            state.status == RecipeRunStatus.AlreadyRunning
        ) {
            val progress = if (state.stepCount > 0 && state.currentStepIndex >= 0) {
                "步骤 ${state.currentStepIndex + 1}/${state.stepCount}"
            } else {
                state.status.label
            }
            return "$progress · ${state.surface.label}"
        }
        if (!feedback.isNullOrBlank()) return feedback
        return recipeStepSummary(recipe)
    }

    private fun recipeStepSummary(recipe: KiteRecipe): String {
        val firstStep = recipe.steps.firstOrNull()
        return when {
            firstStep?.type == KiteRecipe.STEP_SHELL -> "SH · ${compactCommand(firstStep.cmd ?: firstStep.text.orEmpty())}"
            firstStep?.type == KiteRecipe.STEP_TERMINAL -> "终端 · ${compactCommand(firstStep.cmd ?: firstStep.text.orEmpty()).ifBlank { "打开交互" }}"
            firstStep?.type == KiteRecipe.STEP_OPEN_WEB -> "网页 · ${compactUrlForCard(firstStep.url.orEmpty())}"
            firstStep?.type == KiteRecipe.STEP_ANDROID_ACTION -> "本机 · ${(firstStep.action ?: "系统动作").trim()}"
            recipe.defaultUrl.isNotBlank() -> "网页 · ${compactUrlForCard(recipe.defaultUrl)}"
            recipe.description.isNotBlank() -> recipe.description
            else -> "顺序执行卡片"
        }
    }

    private fun compactCommand(command: String): String =
        command.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.replace(Regex("\\s+"), " ")
            ?.take(90)
            .orEmpty()

    private fun compactUrlForCard(url: String): String {
        if (url.isBlank()) return "未填写网址"
        return runCatching {
            val parsed = Uri.parse(url)
            val host = parsed.host.orEmpty()
            if (host.isBlank()) return@runCatching url.take(90)
            val port = parsed.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
            val path = parsed.path.orEmpty().takeIf { it.isNotBlank() && it != "/" }.orEmpty()
            "$host$port$path".take(90)
        }.getOrDefault(url.take(90))
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
        text = "…"
        textSize = 20f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(tokens.textPrimary)
        typeface = Typeface.DEFAULT_BOLD
        background = roundedBox(tokens.surface, tokens.border, dp(13).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            setMargins(dp(8), 0, 0, 0)
        }
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

    private inner class ResourceHeroArtView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.shader = null
            paint.color = Color.argb(70, 123, 210, 207)
            rect.set(w * 0.06f, h * 0.27f, w * 0.94f, h * 0.80f)
            canvas.drawOval(rect, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(26, 0, 112, 107)
            rect.set(w * 0.16f, h * 0.70f, w * 0.88f, h * 0.93f)
            canvas.drawRoundRect(rect, dp(28).toFloat(), dp(28).toFloat(), paint)

            paint.shader = LinearGradient(
                0f,
                h * 0.56f,
                0f,
                h * 0.92f,
                Color.rgb(250, 255, 255),
                Color.rgb(219, 244, 245),
                Shader.TileMode.CLAMP
            )
            rect.set(w * 0.17f, h * 0.61f, w * 0.87f, h * 0.86f)
            canvas.drawRoundRect(rect, dp(24).toFloat(), dp(24).toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(120, 174, 225, 224)
            canvas.drawRoundRect(rect, dp(24).toFloat(), dp(24).toFloat(), paint)

            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                w * 0.30f,
                h * 0.34f,
                w * 0.78f,
                h * 0.76f,
                Color.rgb(35, 188, 179),
                Color.rgb(6, 148, 112),
                Shader.TileMode.CLAMP
            )
            rect.set(w * 0.32f, h * 0.39f, w * 0.78f, h * 0.72f)
            canvas.drawRoundRect(rect, dp(20).toFloat(), dp(20).toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.argb(120, 205, 255, 252)
            canvas.drawRoundRect(rect, dp(20).toFloat(), dp(20).toFloat(), paint)
            drawCenteredText(canvas, "K", rect.centerX(), rect.centerY(), dp(39).toFloat(), Color.WHITE)

            drawFloatingTile(canvas, w * 0.22f, h * 0.32f, dp(38).toFloat(), -17f, "</>", Color.rgb(60, 190, 185))
            drawFloatingTile(canvas, w * 0.72f, h * 0.24f, dp(34).toFloat(), 15f, "▣", Color.rgb(70, 196, 189))
            drawFloatingTile(canvas, w * 0.83f, h * 0.46f, dp(34).toFloat(), -12f, ">_", Color.rgb(0, 156, 141))

            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Color.argb(130, 118, 211, 208)
            listOf(
                w * 0.15f to h * 0.50f,
                w * 0.56f to h * 0.27f,
                w * 0.91f to h * 0.35f
            ).forEach { point ->
                canvas.drawCircle(point.first, point.second, dp(1).toFloat(), paint)
            }
        }

        private fun drawFloatingTile(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            size: Float,
            rotation: Float,
            label: String,
            accent: Int
        ) {
            canvas.save()
            canvas.rotate(rotation, cx, cy)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                cx - size,
                cy - size,
                cx + size,
                cy + size,
                Color.argb(235, 255, 255, 255),
                Color.argb(215, 230, 252, 252),
                Shader.TileMode.CLAMP
            )
            rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
            canvas.drawRoundRect(rect, dp(10).toFloat(), dp(10).toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.argb(115, 136, 219, 215)
            canvas.drawRoundRect(rect, dp(10).toFloat(), dp(10).toFloat(), paint)
            drawCenteredText(canvas, label, cx, cy, dp(if (label.length > 2) 10 else 15).toFloat(), accent)
            canvas.restore()
        }

        private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, sizePx: Float, color: Int) {
            textPaint.shader = null
            textPaint.textSize = sizePx
            textPaint.color = color
            val metrics = textPaint.fontMetrics
            canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
        }
    }

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

    private data class RecipeFormDraft(
        val editingRecipeId: String,
        val selectedType: String,
        val selectedIconName: String,
        val name: String,
        val description: String,
        val url: String,
        val command: String,
        val workdir: String,
        val shortcutRequested: Boolean,
        val launchOpenInstance: Boolean,
        val steps: List<RecipeStepDraft>
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("editingRecipeId", editingRecipeId)
                .put("selectedType", selectedType)
                .put("selectedIconName", selectedIconName)
                .put("name", name)
                .put("description", description)
                .put("url", url)
                .put("command", command)
                .put("workdir", workdir)
                .put("shortcutRequested", shortcutRequested)
                .put("launchOpenInstance", launchOpenInstance)
                .put("steps", JSONArray().apply {
                    steps.forEach { step ->
                        put(
                            JSONObject()
                                .put("type", step.type)
                                .put("command", step.command)
                                .put("url", step.url)
                                .put("workdir", step.workdir)
                        )
                    }
                })

        companion object {
            fun fromJson(raw: String): RecipeFormDraft? =
                runCatching {
                    val json = JSONObject(raw)
                    val stepsJson = json.optJSONArray("steps") ?: JSONArray()
                    val steps = buildList {
                        for (index in 0 until stepsJson.length()) {
                            val item = stepsJson.optJSONObject(index) ?: continue
                            val type = item.optString("type").ifBlank { KiteRecipe.STEP_SHELL }
                            add(
                                RecipeStepDraft(
                                    type = type,
                                    command = item.optString("command"),
                                    url = item.optString("url"),
                                    workdir = item.optString("workdir")
                                )
                            )
                        }
                    }
                    RecipeFormDraft(
                        editingRecipeId = json.optString("editingRecipeId"),
                        selectedType = json.optString("selectedType").ifBlank { KiteRecipe.TYPE_COMMAND_WEB },
                        selectedIconName = json.optString("selectedIconName").ifBlank { KiteRecipeIcon.defaultNameForType(KiteRecipe.TYPE_COMMAND_WEB) },
                        name = json.optString("name"),
                        description = json.optString("description"),
                        url = json.optString("url"),
                        command = json.optString("command"),
                        workdir = json.optString("workdir"),
                        shortcutRequested = json.optBoolean("shortcutRequested", false),
                        launchOpenInstance = json.optBoolean("launchOpenInstance", false),
                        steps = steps
                    )
                }.getOrNull()
        }
    }

    private data class ResourceItem(
        val id: String,
        val name: String,
        val description: String,
        val longDescription: String,
        val section: String,
        val category: String,
        val iconText: String,
        val accent: String,
        val version: String,
        val sizeLabel: String,
        val sourceLabel: String,
        val stateLabel: String,
        val actionLabel: String,
        val actionEnabled: Boolean = true,
        val includes: List<String>,
        val notes: List<String>,
        val steps: List<ResourceStep>
    )

    private data class ResourceStep(
        val type: String,
        val title: String,
        val preview: String
    )

    private data class ResourcePreviewCard(
        val title: String,
        val subtitle: String,
        val symbol: String,
        val accent: String
    )

    private data class ResourceExecutionRow(
        val marker: String,
        val label: String,
        val value: String,
        val note: String,
        val monospace: Boolean
    )

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
        Resources,
        ResourceDetail,
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
        private const val CARD_RUN_TERMINAL_FRAGMENT_TAG = "kite-card-run-terminal"
        private const val RESOURCE_NODE_RUNTIME = "kite.nodejs"
        private const val RESOURCE_KF_TOOL_ENV = "kite.tool.env"
        private const val RESOURCE_HERMES_WEBUI = "kite.hermes.webui"
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private const val WEB_READY_TIMEOUT_MS = 8000L
        private const val WEB_READY_INTERVAL_MS = 700L
        private const val WEB_READY_CONNECT_TIMEOUT_MS = 700
        private const val WEB_READY_READ_TIMEOUT_MS = 700
        private const val TERMINAL_STEP_COMMAND_DELAY_MS = 650L
        private const val TERMINAL_STOP_GRACE_MS = 350L
        private const val SHELL_PROGRESS_RENDER_INTERVAL_MS = 350L
        private const val TERMINAL_AUTH_LINK_POLL_MS = 1200L
        private const val TERMINAL_AUTH_LINK_WATCH_MS = 10L * 60L * 1000L
        private const val REQUEST_DROPZONE_STORAGE = 801
        private const val KEY_HIDE_MAIN_TASK_FROM_RECENTS = "hide_main_task_from_recents"
        private const val KEY_RESTORE_LAST_SCREEN = "restore_last_screen"
        private const val KEY_RECIPE_DRAFT = "recipe_draft"
        private const val KEY_RECIPE_DRAFT_SAVED_AT = "recipe_draft_saved_at"
        private const val STATE_CURRENT_SCREEN = "kite_current_screen"
        private const val STATE_WORKBENCH_URL = "kite_workbench_url"
        private const val STATE_RECIPE_DRAFT = "kite_recipe_draft"
        private const val RECIPE_DRAFT_RESTORE_WINDOW_MS = 6L * 60L * 60L * 1000L
        private val terminalFlowFinishedStatuses = setOf(
            ManagedTerminalStatus.EXITED,
            ManagedTerminalStatus.FAILED,
            ManagedTerminalStatus.STOPPED
        )
    }
}
