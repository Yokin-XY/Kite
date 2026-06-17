package com.kite.app

import android.animation.ValueAnimator
import android.animation.LayoutTransition
import android.app.ActivityManager
import android.app.Dialog
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.MotionEvent
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
import android.widget.ImageView
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
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.KiteRunReport
import com.kite.app.recipe.KiteStepReport
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.recipe.NewRecipeStepInput
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallSignal
import com.kite.app.resources.KiteResourceInstallSpec
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRequestPolicy
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.run.CardRunState as RecipeRuntimeState
import com.kite.app.run.CardRunBrowserRouter
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunHistoryStep
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
import com.kftest.app.foundation.runtime.TerminalSessionStore
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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
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
    private lateinit var resourceManifestLoader: KiteResourceManifestLoader
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
    private var selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
    private var selectedIconSource = ""
    private var formShortcutRequested = false
    private var formLaunchOpenInstance = true
    private var recipeMoreDraft: RecipeFormDraft? = null
    private var avatarTileRefresh: (() -> Unit)? = null
    private var recipeIconMenuDialog: Dialog? = null
    private var applyPickedIconAfterCrop = false
    private var reopenIconMenuAfterCrop = false
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
    private var registeredCardRunCloserInstanceId: String? = null
    private var currentResourceDetailId: String? = null
    private var resourceDetailRequestSerial = 0L
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
    private var resourcePageView: View? = null
    private var resourcePageNavView: View? = null
    private var resourceSectionHost: LinearLayout? = null
    private var resourceSearchBar: ResourceSearchBar? = null
    private var resourceSearchQuery: String = ""
    private var resourceSectionsRenderKey: String = ""
    private var resourceSectionsRenderedRequestKey: String = ""
    private var resourceSectionsRequestSerial = 0L
    private var resourceSectionsInFlightKey: String? = null
    private var resourceSectionsDirty = true
    private var resourceDetailInFlightKey: String? = null
    private var resourceManageContentHost: LinearLayout? = null
    private var resourceManageRequestSerial = 0L
    private var currentResourceInstallTargetId: String? = null
    private var resourceInstallWizardPlanIds: List<String> = emptyList()
    private var resourceInstallPlanRequestSerial = 0L
    private val suppressedResourceRunSurfaceRecipeIds = mutableSetOf<String>()
    private val pendingResourceUninstallContinuations = mutableMapOf<String, ResourceUninstallContinuation>()
    private var cachedResourceCatalog: List<ResourceItem>? = null
    private var cachedResourceCatalogUpdatedAt = 0L
    private var resourceCatalogDirty = true
    private var resourceCatalogBackgroundRefreshInFlight = false
    private var cachedToolchainWorkspaceSnapshot = ToolchainWorkspaceSnapshot()
    private var lastConsoleRuntimeRefreshAt = 0L
    private var cardRunReportBinding: CardRunReportBinding? = null
    private var cardRunTopBarBinding: CardRunTopBarBinding? = null
    private var resourceInstallWizardBinding: ResourceInstallWizardBinding? = null
    private var resourceInstallWizardRefreshSerial = 0L
    private var foregroundLiveTickScheduled = false
    private val terminalAuthorizationUrlCache = mutableMapOf<String, String>()
    private val consoleCardBindings = mutableMapOf<String, RecipeCardBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = KiteDiagnostics(this)
        diagnostics.writeCapabilityReport()
        themeStore = getSharedPreferences("kite_theme", MODE_PRIVATE)
        appSettings = getSharedPreferences("kite_app_settings", MODE_PRIVATE)
        CardRunStore.initialize(applicationContext)
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
        resourceManifestLoader = KiteResourceManifestLoader(this)
        prewarmResourceCatalog()
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
        observeCardRunStoreSignals()
        observeResourceInstallSignals()
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
        if (currentScreen == Screen.Console) {
            showConsole()
        }
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
        CardRunTaskCloser.unregister(registeredCardRunCloserInstanceId)
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
            Screen.CreateConfig -> handleRecipeFormBack()
            Screen.RecipeMore -> returnToRecipeFormFromMore()
            Screen.ThemeSettings -> showSettings()
            Screen.ResourceManage -> showResources()
            Screen.ResourceMore -> currentResourceDetailId?.let { showResourceDetail(it) } ?: showResources()
            Screen.ResourceRawJson -> currentResourceDetailId?.let { showResourceDetail(it) } ?: showResources()
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
            ?: recipeId
        val autoStart = sourceIntent?.getBooleanExtra(CardRunIntents.EXTRA_AUTO_START, true) ?: true
        val launchSource = sourceIntent?.getStringExtra(CardRunIntents.EXTRA_LAUNCH_SOURCE).orEmpty()
        val launchKey = "$recipeId:$instanceId:$autoStart:$launchSource"
        if (consumedCardRunLaunchKey == launchKey) return true
        consumedCardRunLaunchKey = launchKey

        val isResourceInstallWizardLaunch = launchSource == CardRunIntents.SOURCE_RESOURCE_INSTALL ||
            recipeId.startsWith("resource-install-wizard-")
        if (isResourceInstallWizardLaunch) {
            if (showResourceInstallWizardFromIntent(sourceIntent, recipeId, instanceId)) {
                return true
            }
            Toast.makeText(this, "获取向导缺少队列信息", Toast.LENGTH_SHORT).show()
            if (this is CardRunActivity) finish()
            return true
        }

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
        registerCardRunTaskCloser(instanceId)
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

    private fun showResourceInstallWizardFromIntent(
        sourceIntent: Intent?,
        recipeId: String,
        instanceId: String
    ): Boolean {
        val targetId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_TARGET_ID)
            ?.takeIf { it.isNotBlank() }
            ?: activeResourceInstallWizard?.targetResourceId
            ?: currentResourceInstallTargetId
            ?: return false
        val planIds = sourceIntent
            ?.getStringArrayListExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_PLAN_IDS)
            ?.filter { it.isNotBlank() }
            .orEmpty()
            .ifEmpty { activeResourceInstallWizard?.planResourceIds.orEmpty() }
            .ifEmpty { resourceInstallWizardPlanIds }
            .ifEmpty { resourceInstallStore.planResourceIds() }
            .ifEmpty { resourceInstallStore.pendingPlanResourceIds() }
        if (planIds.isEmpty()) return false
        showResourceInstallWizardSurface(
            targetResourceId = targetId,
            planResourceIds = planIds,
            recipeId = recipeId,
            instanceId = instanceId
        )
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
                CardShortcutManager.iconBitmap(this, recipe),
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
                    advanceAfterUserCompletedStep(
                        recipe = recipe,
                        state = activeRun,
                        nextStepIndex = pending.nextStepIndex,
                        lastOutput = "终端已结束：${terminal.status.label}"
                    )
                }
            }
        }
    }

    private fun observeCardRunStoreSignals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CardRunStore.runs.collect { runs ->
                    val reportBinding = cardRunReportBinding
                    if (reportBinding != null && currentScreen == Screen.CardRun) {
                        val state = runs.firstOrNull { it.instanceId == reportBinding.instanceId }
                        val recipe = state?.let { recipeForRunState(it) }
                        if (state != null && recipe != null) {
                            runtimeStates[state.recipeId] = state
                            updateVisibleCardRunReport(recipe, state)
                        }
                    }
                    if (currentScreen == Screen.Console && consoleCardBindings.isNotEmpty()) {
                        currentRecipes.forEach { recipe ->
                            val state = CardRunStore.currentForRecipe(recipe.id)
                                ?: runtimeStates[recipe.id]
                                ?: RecipeRuntimeState.fromRecipeStatus(recipe.id, "unknown")
                            runtimeStates[recipe.id] = state
                            updateVisibleConsoleCard(recipe, state)
                        }
                    }
                    updateVisibleResourceInstallWizardElapsed()
                }
            }
        }
    }

    private fun observeResourceInstallSignals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                resourceInstallStore.signals.collect { signal ->
                    if (signal.revision <= 0L) return@collect
                    consumeResourceInstallSignal(signal)
                }
            }
        }
    }

    private fun consumeResourceInstallSignal(signal: KiteResourceInstallSignal) {
        invalidateResourceCatalogCache()
        when (currentScreen) {
            Screen.Resources -> requestResourceSectionsRefresh(forceCatalogRefresh = false)
            Screen.CardRun -> requestVisibleResourceInstallWizardRefresh(signal.reason)
            Screen.ResourceDetail -> currentResourceDetailId?.let { showResourceDetail(it) }
            Screen.ResourceMore -> currentResourceDetailId?.let { resourceId ->
                resourceCatalogForUiRender("resource_more_signal")
                    .firstOrNull { item -> item.id == resourceId }
                    ?.let { item -> showResourceMoreActions(item) }
            }
            Screen.ResourceManage -> requestResourceManageRefresh(forceCatalogRefresh = false, reason = signal.reason)
            else -> Unit
        }
    }

    private fun recipeForRunState(state: RecipeRuntimeState): KiteRecipe? =
        CardRunStore.registeredRecipe(state.recipeId)
            ?: currentRecipes.firstOrNull { it.id == state.recipeId }

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
            BootstrapStage.SPACE_READY -> UbuntuRuntimeUiState(
                title = when (stage) {
                    BootstrapStage.SERVICE_REQUESTED -> "\u6b63\u5728\u5524\u8d77 Ubuntu \u8fd0\u884c\u73af\u5883"
                    BootstrapStage.ROOTFS_EXTRACTING -> "\u6b63\u5728\u89e3\u538b\u7cfb\u7edf\u955c\u50cf"
                    BootstrapStage.BASE_BOOTSTRAP -> "\u6b63\u5728\u521d\u59cb\u5316\u57fa\u7840\u73af\u5883"
                    BootstrapStage.SPACE_READY -> "\u6b63\u5728\u51c6\u5907\u5de5\u4f5c\u533a"
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
        invalidateResourceUiCache()
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
            Screen.ResourceManage -> {
                showResourceManage()
                true
            }
            Screen.ResourceMore -> {
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
        if (currentScreen == Screen.RecipeMore) {
            return recipeMoreDraft?.withLaunchState()
        }
        if (currentScreen != Screen.CreateConfig || !::nameInput.isInitialized) return null
        return RecipeFormDraft(
            editingRecipeId = editingRecipe?.id.orEmpty(),
            selectedType = selectedType,
            selectedIconName = selectedIconName,
            selectedIconType = selectedIconType,
            selectedIconSource = selectedIconSource,
            name = nameInput.text?.toString().orEmpty(),
            description = if (::descriptionInput.isInitialized) descriptionInput.text?.toString().orEmpty() else "",
            url = if (::urlInput.isInitialized) urlInput.text?.toString().orEmpty() else "",
            command = if (::commandInput.isInitialized) commandInput.text?.toString().orEmpty() else "",
            workdir = if (::workdirInput.isInitialized) workdirInput.text?.toString().orEmpty() else "",
            shortcutRequested = formShortcutRequested,
            launchOpenInstance = formLaunchOpenInstance,
            steps = formSteps.map { it.copy() }
        )
    }

    private fun RecipeFormDraft.withLaunchState(): RecipeFormDraft =
        copy(
            shortcutRequested = formShortcutRequested,
            launchOpenInstance = formLaunchOpenInstance
        )

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

    @Deprecated("Use Activity Result APIs after the UI shell is migrated.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_RECIPE_ICON && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            showRecipeIconCropDialog(uri)
        } else if (requestCode == REQUEST_PICK_RECIPE_ICON) {
            applyPickedIconAfterCrop = false
            reopenIconMenuAfterCrop = false
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
                refreshRecipeRuntimeStates(currentRecipes)
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
        refreshRecipeRuntimeStates(currentRecipes)
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

    private fun refreshRecipeRuntimeStates(recipes: List<KiteRecipe> = currentRecipes) {
        recipes.forEach { recipe ->
            runtimeStates[recipe.id] = CardRunStore.currentForRecipe(recipe.id)
                ?: runtimeStates[recipe.id]
                    ?: RecipeRuntimeState.fromRecipeStatus(recipe.id, "unknown")
        }
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
        cardRunReportBinding = null
        cardRunTopBarBinding = null
        resourceInstallWizardBinding = null
        consoleCardBindings.clear()
        val transaction = supportFragmentManager.beginTransaction()
        var changed = false

        fun detachFragment(tag: String) {
            supportFragmentManager.findFragmentByTag(tag)?.let { fragment ->
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
        root.setBackgroundColor(tokens.pageBackground)
        val page = ensureResourcePage()
        val nav = ensureResourcePageNav()
        showBottomNavigationImmediately(nav)
        val alreadyAttached = page.parent === root && nav.parent === root
        if (!alreadyAttached) {
            clearRootForScreen()
            detachFromParent(page)
            detachFromParent(nav)
            root.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(nav)
        }
        requestResourceSectionsRefresh(forceCatalogRefresh = false)
    }

    private fun ensureResourcePage(): View {
        resourcePageView?.let { return it }
        val resourceScroll = ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), 0, dp(22), dp(96))
                addView(resourceHeader())
                addView(resourceHero())
                addView(resourceCategoryChips())
                resourceSectionHost = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                addView(resourceSectionHost)
            })
        }
        val searchPill = ResourceSearchBar(this) { query ->
            resourceSearchQuery = query
            requestResourceSectionsRefresh(query = query, forceCatalogRefresh = false)
        }
        resourceSearchBar = searchPill
        return FrameLayout(this).apply {
            addView(resourceScroll, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(searchPill)
        }.also {
            resourcePageView = it
        }
    }

    private fun ensureResourcePageNav(): View {
        resourcePageNavView?.let { return it }
        return bottomNavigation().also { nav ->
            resourcePageNavView = nav
            val resourceScroll = ((resourcePageView as? FrameLayout)?.getChildAt(0) as? ScrollView)
            if (resourceScroll != null && resourceSearchBar != null) {
                attachResourceScrollChrome(resourceScroll, nav, resourceSearchBar!!)
            }
        }
    }

    private fun refreshResourceSections(
        query: String = resourceSearchQuery,
        forceCatalogRefresh: Boolean = false
    ): Boolean {
        val sectionHost = resourceSectionHost ?: return false
        val payload = buildResourceSectionsPayload(query, forceCatalogRefresh)
        return renderResourceSectionsPayload(sectionHost, payload)
    }

    private fun requestResourceSectionsRefresh(
        query: String = resourceSearchQuery,
        forceCatalogRefresh: Boolean = false
    ) {
        val sectionHost = resourceSectionHost ?: return
        val requestKey = KiteResourceRequestPolicy.storeListKey(query)
        val hasRenderedSections = sectionHost.childCount > 0
        if (
            !forceCatalogRefresh &&
            !resourceSectionsDirty &&
            resourceSectionsRenderedRequestKey == requestKey &&
            resourceSectionsRenderKey.isNotBlank() &&
            hasRenderedSections
        ) {
            return
        }
        if (!forceCatalogRefresh && resourceSectionsInFlightKey == requestKey) return
        val requestId = ++resourceSectionsRequestSerial
        resourceSectionsInFlightKey = requestKey
        resourceInstallStore.clearExpiredPageCache()
        thread(name = "KiteResourceSections-$requestId-${requestKey.take(24)}", isDaemon = true) {
            val result = runCatching { buildResourceSectionsPayload(query, forceCatalogRefresh) }
            runOnUiThread {
                if (requestId != resourceSectionsRequestSerial || currentScreen != Screen.Resources) {
                    if (resourceSectionsInFlightKey == requestKey) resourceSectionsInFlightKey = null
                    return@runOnUiThread
                }
                if (resourceSectionsInFlightKey == requestKey) resourceSectionsInFlightKey = null
                result.onSuccess { payload ->
                    cacheResourceSectionsPayload(requestKey, payload)
                    if (renderResourceSectionsPayload(sectionHost, payload)) {
                        resourceSectionsRenderedRequestKey = requestKey
                    }
                }.onFailure { error ->
                    if (sectionHost.childCount == 0) {
                        renderResourceSectionsError(sectionHost, error)
                    }
                }
            }
        }
    }

    private fun buildResourceSectionsPayload(
        query: String,
        forceCatalogRefresh: Boolean
    ): ResourceSectionsPayload {
        val resources = resourceCatalog(forceRefresh = forceCatalogRefresh)
        val cleanQuery = query.trim()
        val visibleResources = if (cleanQuery.isBlank()) resources else resources.filter { it.matchesResourceQuery(cleanQuery) }
        return ResourceSectionsPayload(
            query = cleanQuery,
            resources = visibleResources,
            renderKey = buildResourceSectionsRenderKey(cleanQuery, visibleResources)
        )
    }

    private fun cacheResourceSectionsPayload(requestKey: String, payload: ResourceSectionsPayload) {
        resourceInstallStore.putPageCache(
            cacheKey = requestKey,
            payloadJson = JSONObject()
                .put("query", payload.query)
                .put("renderKey", payload.renderKey)
                .put("resourceIds", JSONArray().apply { payload.resources.forEach { put(it.id) } })
                .put("updatedAt", System.currentTimeMillis())
                .toString(),
            maxAgeMs = KiteResourceRequestPolicy.STORE_PAGE_CACHE_MS
        )
    }

    private fun renderResourceSectionsPayload(
        sectionHost: LinearLayout,
        payload: ResourceSectionsPayload
    ): Boolean {
        val visibleResources = payload.resources
        val renderKey = payload.renderKey
        if (!resourceSectionsDirty && resourceSectionsRenderKey == renderKey && sectionHost.childCount > 0) {
            return true
        }
        sectionHost.removeAllViews()
        val resourcesBySection = visibleResources.groupBy { it.section }
        sectionHost.addView(resourceSection(
            title = "精选推荐",
            items = resourcesBySection["精选推荐"].orEmpty()
        ))
        sectionHost.addView(resourceSection(
            title = "快速开始",
            items = resourcesBySection["快速开始"].orEmpty()
        ))
        sectionHost.addView(resourceSection(
            title = "更多资源",
            items = resourcesBySection["更多资源"].orEmpty()
        ))
        if (visibleResources.isEmpty()) {
            sectionHost.addView(resourceSearchEmptyState(payload.query))
        }
        resourceSectionsRenderKey = renderKey
        resourceSectionsDirty = false
        return true
    }

    private fun renderResourceSectionsError(sectionHost: LinearLayout, error: Throwable) {
        sectionHost.removeAllViews()
        sectionHost.addView(resourceRequestStateBlock(
            title = "资源请求失败",
            detail = error.message ?: error.javaClass.simpleName,
            loading = false
        ))
    }

    private fun resourceRequestStateBlock(title: String, detail: String, loading: Boolean = false): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(34), dp(18), dp(30))
            if (loading) {
                addView(ProgressBar(context).apply {
                    isIndeterminate = true
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                })
            }
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, dp(14), 0, 0)
            })
            addView(TextView(context).apply {
                text = detail
                textSize = 12f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(7), 0, 0)
            })
        }

    private fun buildResourceSectionsRenderKey(query: String, resources: List<ResourceItem>): String =
        buildString {
            append(query)
            resources.forEach { item ->
                append('|')
                append(item.id)
                append(':')
                append(item.stateLabel)
                append(':')
                append(item.actionLabel)
                append(':')
                append(item.actionEnabled)
            }
        }

    private fun showBottomNavigationImmediately(nav: View) {
        nav.animate().cancel()
        nav.visibility = View.VISIBLE
        nav.translationY = 0f
        nav.alpha = 1f
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun clearResourcePageCache() {
        resourcePageView = null
        resourcePageNavView = null
        resourceSectionHost = null
        resourceSearchBar = null
        resourceSearchQuery = ""
        resourceSectionsRenderKey = ""
        resourceSectionsRenderedRequestKey = ""
        resourceSectionsDirty = true
        resourceManageContentHost = null
    }

    private fun invalidateResourceCatalogCache() {
        resourceCatalogDirty = true
        resourceSectionsDirty = true
        cachedToolchainWorkspaceSnapshot = ToolchainWorkspaceSnapshot()
    }

    private fun invalidateResourceUiCache() {
        clearResourcePageCache()
        invalidateResourceCatalogCache()
    }

    private fun resourceCatalogForUiRender(reason: String): List<ResourceItem> {
        cachedResourceCatalog?.let { return it }
        requestResourceCatalogBackgroundRefresh(reason)
        return emptyList()
    }

    private fun requestResourceCatalogBackgroundRefresh(reason: String) {
        if (resourceCatalogBackgroundRefreshInFlight) return
        resourceCatalogBackgroundRefreshInFlight = true
        thread(name = "KiteResourceCatalogUiRefresh", isDaemon = true) {
            runCatching { resourceCatalog(forceRefresh = false) }
            runOnUiThread {
                resourceCatalogBackgroundRefreshInFlight = false
                when (currentScreen) {
                    Screen.Resources -> requestResourceSectionsRefresh(forceCatalogRefresh = false)
                    Screen.CardRun -> requestVisibleResourceInstallWizardRefresh("catalog:$reason")
                    Screen.ResourceMore -> refreshResourceMoreActionsFromCache()
                    Screen.ResourceManage -> requestResourceManageRefresh(forceCatalogRefresh = false, reason = "catalog:$reason")
                    else -> Unit
                }
            }
        }
    }

    private fun refreshResourceMoreActionsFromCache() {
        val resourceId = currentResourceDetailId ?: return
        cachedResourceCatalog
            ?.firstOrNull { it.id == resourceId }
            ?.let { showResourceMoreActions(it) }
    }

    private fun prewarmResourceCatalog() {
        thread(name = "KiteResourceCatalogPrewarm", isDaemon = true) {
            runCatching { resourceCatalog(forceRefresh = false) }
        }
    }

    private fun resourceHeader(): View = row {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(16), dp(198), 0)
        isClickable = true
        setOnClickListener { showResourceManage() }
        addView(TextView(context).apply {
            text = "资源 ›"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun showResourceManage() {
        currentScreen = Screen.ResourceManage
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(topBar("资源管理") { showResources() })
        root.addView(ScrollView(this).apply {
            val host = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(18), dp(22), dp(34))
                addView(resourceManageEmptyBlock("正在读取资源管理信息", "获取队列和已获取资源会在后台加载，避免阻塞当前页面。"))
            }
            resourceManageContentHost = host
            addView(host)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
        requestResourceManageRefresh(forceCatalogRefresh = true, reason = "open")
    }

    private fun requestResourceManageRefresh(forceCatalogRefresh: Boolean, reason: String) {
        val requestId = ++resourceManageRequestSerial
        thread(name = "KiteResourceManageRefresh", isDaemon = true) {
            val payload = runCatching {
                val catalog = resourceCatalog(forceRefresh = forceCatalogRefresh)
                val planSnapshot = resourceInstallStore.planSnapshot()
                val planIds = planSnapshot.resourceIds
                val registrySnapshot = resourceInstallStore.registrySnapshot(planIds)
                ResourceManagePayload(
                    catalog = catalog,
                    planSnapshot = planSnapshot,
                    planIds = planIds,
                    registrySnapshot = registrySnapshot
                )
            }.getOrNull()
            runOnUiThread {
                if (requestId != resourceManageRequestSerial || currentScreen != Screen.ResourceManage) return@runOnUiThread
                if (payload == null) {
                    resourceManageContentHost?.let { host ->
                        host.removeAllViews()
                        host.addView(resourceManageEmptyBlock("资源管理读取失败", "稍后返回资源页后可重新进入。"))
                    }
                    return@runOnUiThread
                }
                applyResourceManagePayload(payload, reason)
            }
        }
    }

    private fun applyResourceManagePayload(payload: ResourceManagePayload, reason: String) {
        val host = resourceManageContentHost ?: return
        host.removeAllViews()
        val catalogById = payload.catalog.associateBy { it.id }
        host.addView(sectionTitle("获取列表"))
        if (payload.planIds.isEmpty()) {
            host.addView(resourceManageEmptyBlock("暂无获取任务", "从资源商店点击获取后，这里会显示当前队列。"))
        } else {
            host.addView(resourceManageInstallTaskCard(
                payload.planIds,
                catalogById,
                payload.planSnapshot,
                payload.registrySnapshot
            ))
            val activeInstallId = resourceManageActiveInstallId(payload.planIds, payload.planSnapshot, payload.registrySnapshot)
            host.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, 0)
                payload.planIds.forEachIndexed { index, resourceId ->
                    addView(resourceInstallWizardStepRow(
                        index = index,
                        total = payload.planIds.size,
                        item = catalogById[resourceId],
                        resourceId = resourceId,
                        isActive = activeInstallId == resourceId,
                        planSnapshot = payload.planSnapshot,
                        registryEntry = payload.registrySnapshot[resourceId]
                    ))
                }
            })
        }
        host.addView(sectionTitle("已获取资源").apply {
            setPadding(0, dp(24), 0, dp(12))
        })
        val installed = payload.catalog.filter { resourceItemIsInstalled(it) }
        if (installed.isEmpty()) {
            host.addView(resourceManageEmptyBlock("暂无已获取资源", "获取成功并完成注册后，会出现在这里。"))
        } else {
            host.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
                installed.forEachIndexed { index, item ->
                    addView(resourceListRow(item))
                    if (index != installed.lastIndex) addView(divider().apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            setMargins(dp(64), dp(8), dp(12), dp(8))
                        }
                    })
                }
            })
        }
        diagnostics.logRecipeEvent(
            "resource_manage_payload_applied",
            null,
            mapOf(
                "reason" to reason,
                "catalog" to payload.catalog.size.toString(),
                "plan" to payload.planIds.size.toString()
            )
        )
    }

    private fun resourceManageActiveInstallId(
        planIds: List<String>,
        planSnapshot: com.kite.app.resources.KiteResourcePlanSnapshot,
        registrySnapshot: Map<String, KiteResourceRegistryEntry>
    ): String? =
        planIds.firstOrNull { planSnapshot.stepStatus(it) == KiteResourceInstallStore.PLAN_STEP_RUNNING }
            ?: planIds.firstOrNull {
                registrySnapshot[it]?.failed == true ||
                    planSnapshot.stepStatus(it) == KiteResourceInstallStore.PLAN_STEP_FAILED
            }
            ?: planSnapshot.pendingResourceIds.firstOrNull()

    private fun resourceManageInstallTaskCard(
        planIds: List<String>,
        catalogById: Map<String, ResourceItem>,
        planSnapshot: com.kite.app.resources.KiteResourcePlanSnapshot,
        registrySnapshot: Map<String, KiteResourceRegistryEntry>
    ): View {
        val targetId = currentResourceInstallTargetId
            ?: activeResourceInstallWizard?.targetResourceId
            ?: planIds.lastOrNull().orEmpty()
        val targetName = catalogById[targetId]?.name ?: targetId.ifBlank { "获取任务" }
        val completedCount = planIds.count { id ->
            planSnapshot.stepStatus(id) == KiteResourceInstallStore.PLAN_STEP_DONE ||
                (resourceItemIsInstalled(catalogById[id]) && registrySnapshot[id]?.failed != true)
        }
        val hasUninstalling = planIds.any { id -> registrySnapshot[id]?.uninstalling == true }
        val hasUninstallFailure = planIds.any { id ->
            registrySnapshot[id]?.failed == true &&
                registrySnapshot[id]?.operation == KiteResourceInstallStore.OP_UNINSTALL
        }
        val hasFailure = planIds.any { id ->
            registrySnapshot[id]?.uninstalling != true &&
                (registrySnapshot[id]?.failed == true ||
                    planSnapshot.stepStatus(id) == KiteResourceInstallStore.PLAN_STEP_FAILED)
        }
        val hasRunningStep = planIds.any { id ->
            planSnapshot.stepStatus(id) == KiteResourceInstallStore.PLAN_STEP_RUNNING
        }
        val statusText = when {
            hasRunningStep -> "获取中"
            hasUninstalling -> "卸载中"
            hasUninstallFailure -> "卸载失败"
            hasFailure -> "已停止"
            completedCount >= planIds.size -> "已完成"
            else -> "等待中"
        }
        val tone = when {
            hasFailure || hasUninstallFailure -> tokens.danger
            hasRunningStep || hasUninstalling -> tokens.primaryStrong
            completedCount >= planIds.size -> tokens.success
            else -> tokens.textSecondary
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(resourceIcon("↓", "teal").apply {
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                        setMargins(0, 0, dp(12), 0)
                    }
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = targetName
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(TextView(context).apply {
                        text = "$completedCount/${planIds.size} · $statusText"
                        textSize = 12f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = statusText
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(tone)
                    background = roundedBox(tintBackground(tone), Color.TRANSPARENT, dp(11).toFloat())
                    setPadding(dp(9), 0, dp(9), 0)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22))
                })
            })
            addView(row {
                setPadding(0, dp(14), 0, 0)
                addView(resourceManageActionButton("打开向导") {
                    if (targetId.isNotBlank()) showResourceInstallWizard(targetId)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(38), 0.7f)
                })
                addView(resourceManageActionButton("取消", danger = true) {
                    cancelResourceInstallTask(
                        targetResourceId = targetId,
                        planResourceIds = planIds,
                        closeWizard = false
                    )
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(38), 0.3f).apply {
                        setMargins(dp(10), 0, 0, 0)
                    }
                })
            })
        }
    }

    private fun resourceManageActionButton(label: String, danger: Boolean = false, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (danger) tokens.danger else tokens.primaryStrong)
            background = roundedBox(
                if (danger) tintBackground(tokens.danger) else tokens.primarySubtle,
                if (danger) tintBackgroundBorder(tokens.danger) else tokens.primarySoft,
                dp(14).toFloat()
            )
            setOnClickListener { onClick() }
        }

    private fun resourceManageEmptyBlock(title: String, detail: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(17), dp(16), dp(17))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = detail
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(5), 0, 0)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
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
                listOf("全部", "已获取", "本地", "Python", "Node", "AI", "系统工具").forEachIndexed { index, label ->
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
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
            setOnClickListener { showResourceDetail(item.id, item) }
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

    private fun resourceActionEnabled(actionLabel: String, busy: Boolean): Boolean =
        when (actionLabel) {
            "获取中", "卸载中", "处理中" -> false
            else -> !busy
        }

    private fun resourceDetailActionArea(item: ResourceItem): View =
        if (resourceHasSplitActions(item)) {
            row {
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                    setMargins(0, dp(24), 0, 0)
                }
                addView(resourceActionButton(item, compact = false).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(46), 0.7f)
                })
                addView(resourceSecondaryActionButton(item).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(46), 0.3f).apply {
                        setMargins(dp(10), 0, 0, 0)
                    }
                })
            }
        } else {
            resourceActionButton(item, compact = false).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                    setMargins(0, dp(24), 0, 0)
                }
            }
        }

    private fun resourceHasSplitActions(item: ResourceItem): Boolean =
        (resourceIsInstalled(item) && item.actionLabel == "打开") || resourceHasFailedInstallActions(item)

    private fun resourceHasFailedInstallActions(item: ResourceItem): Boolean =
        item.actionLabel == "重新获取" &&
            resourceInstallStore.isFailed(item.id) &&
            resourceInstallStore.failedOperation(item.id) != KiteResourceInstallStore.OP_UNINSTALL

    private fun resourceSecondaryActionButton(item: ResourceItem): TextView =
        TextView(this).apply {
            val isCancel = resourceHasFailedInstallActions(item)
            text = if (isCancel) "取消" else "卸载"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.danger)
            alpha = if (item.actionEnabled) 1f else 0.58f
            background = roundedBox(tintBackground(tokens.danger), tintBackgroundBorder(tokens.danger), dp(15).toFloat(), 0)
            if (item.actionEnabled) {
                setOnClickListener {
                    if (isCancel) {
                        handleResourceFailedInstallCancel(item)
                    } else {
                        handleResourceUninstallAction(item)
                    }
                }
            }
        }

    private fun showResourceDetail(resourceId: String, initialItem: ResourceItem? = null) {
        val requestKey = KiteResourceRequestPolicy.resourceDetailKey(resourceId)
        if (resourceDetailInFlightKey == requestKey && currentScreen == Screen.ResourceDetail) return
        val requestId = ++resourceDetailRequestSerial
        resourceDetailInFlightKey = requestKey
        currentResourceDetailId = resourceId
        currentScreen = Screen.ResourceDetail
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val contentHost = FrameLayout(this)
        root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
        val seedItem = initialItem ?: cachedResourceCatalog?.firstOrNull { it.id == resourceId }
        val seedRenderKey = seedItem?.let { buildResourceDetailRenderKey(it) }
        if (seedItem != null) {
            renderResourceDetailContent(contentHost, seedItem)
            cacheResourceDetailPayload(seedItem)
            requestResourceDetailMedia(seedItem)
        } else {
            renderResourceDetailPending(contentHost, resourceId)
        }
        thread(name = "KiteResourceDetail-$requestId-${requestKey.take(24)}", isDaemon = true) {
            val result = runCatching {
                resourceCatalog(forceRefresh = false).firstOrNull { it.id == resourceId }
            }
            runOnUiThread {
                if (
                    requestId != resourceDetailRequestSerial ||
                    currentScreen != Screen.ResourceDetail ||
                    currentResourceDetailId != resourceId
                ) {
                    if (resourceDetailInFlightKey == requestKey) resourceDetailInFlightKey = null
                    return@runOnUiThread
                }
                if (resourceDetailInFlightKey == requestKey) resourceDetailInFlightKey = null
                val item = result.getOrNull()
                when {
                    item != null -> {
                        if (buildResourceDetailRenderKey(item) != seedRenderKey) {
                            renderResourceDetailContent(contentHost, item)
                        }
                        cacheResourceDetailPayload(item)
                        requestResourceDetailMedia(item)
                    }
                    result.isFailure -> {
                        if (seedItem == null) renderResourceDetailError(contentHost, resourceId, result.exceptionOrNull())
                    }
                    else -> {
                        if (seedItem == null) renderResourceDetailMissing(contentHost, resourceId)
                    }
                }
            }
        }
    }

    private fun cacheResourceDetailPayload(item: ResourceItem) {
        resourceInstallStore.putPageCache(
            cacheKey = KiteResourceRequestPolicy.resourceDetailKey(item.id),
            payloadJson = JSONObject()
                .put("resourceId", item.id)
                .put("name", item.name)
                .put("version", item.version)
                .put("state", item.stateLabel)
                .put("updatedAt", System.currentTimeMillis())
                .toString(),
            maxAgeMs = KiteResourceRequestPolicy.DETAIL_PAGE_CACHE_MS
        )
    }

    private fun buildResourceDetailRenderKey(item: ResourceItem): String =
        buildString {
            append(item.id)
            append(':')
            append(item.stateLabel)
            append(':')
            append(item.actionLabel)
            append(':')
            append(item.actionEnabled)
            append(':')
            append(item.version)
        }

    private fun requestResourceDetailMedia(item: ResourceItem) {
        val requestKey = KiteResourceRequestPolicy.resourceMediaKey(item.id)
        resourceInstallStore.clearExpiredPageCache()
        if (resourceInstallStore.pageCache(requestKey) != null) return
        thread(name = "KiteResourceMedia-${item.id}", isDaemon = true) {
            resourceInstallStore.putPageCache(
                cacheKey = requestKey,
                payloadJson = JSONObject()
                    .put("resourceId", item.id)
                    .put("mediaReady", true)
                    .put("updatedAt", System.currentTimeMillis())
                    .toString(),
                maxAgeMs = KiteResourceRequestPolicy.MEDIA_CACHE_MS
            )
        }
    }

    private fun renderResourceDetailContent(contentHost: FrameLayout, item: ResourceItem) {
        contentHost.removeAllViews()
        contentHost.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(34))
                addView(resourceDetailChrome(item))
                addView(resourceDetailHeader(item))
                addView(resourceDetailActionArea(item))
                addView(resourcePreviewStrip(item))
                addView(resourceInfoBlock("简介", item.longDescription))
                resourceRecommendationBlock(item)?.let { addView(it) }
                addView(resourceExecutionPreviewBlock(item))
                addView(resourceRequirementsBlock(item))
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun renderResourceDetailPending(contentHost: FrameLayout, resourceId: String) {
        contentHost.removeAllViews()
        contentHost.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(34))
                addView(resourceDetailLoadingChrome(null))
                addView(resourceDetailPlaceholderHeader(resourceId))
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun renderResourceDetailMissing(contentHost: FrameLayout, resourceId: String) {
        contentHost.removeAllViews()
        contentHost.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(34))
            addView(resourceDetailLoadingChrome(null))
            addView(resourceRequestStateBlock("资源暂不可用", resourceId, loading = false))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun renderResourceDetailError(contentHost: FrameLayout, resourceId: String, error: Throwable?) {
        contentHost.removeAllViews()
        contentHost.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(34))
            addView(resourceDetailLoadingChrome(null))
            addView(resourceRequestStateBlock(
                "资源请求失败",
                error?.message ?: error?.javaClass?.simpleName ?: resourceId,
                loading = false
            ))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun resourceDetailChrome(item: ResourceItem): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(iconButton("‹", dp(32), Color.TRANSPARENT, tokens.textPrimary, dp(14)) { showResources() })
            addView(View(context), LinearLayout.LayoutParams(0, dp(32), 1f))
            addView(iconButton("•••", dp(32), Color.TRANSPARENT, tokens.textPrimary, dp(14)) {
                showResourceMoreActions(item)
            })
        }

    private fun resourceDetailLoadingChrome(initialItem: ResourceItem?): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(iconButton("‹", dp(32), Color.TRANSPARENT, tokens.textPrimary, dp(14)) { showResources() })
            addView(View(context), LinearLayout.LayoutParams(0, dp(32), 1f))
            if (initialItem != null) {
                addView(iconButton("•••", dp(32), Color.TRANSPARENT, tokens.textPrimary, dp(14)) {
                    showResourceMoreActions(initialItem)
                })
            } else {
                addView(TextView(context).apply {
                    text = ""
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                })
            }
        }

    private fun resourceDetailPlaceholderHeader(resourceId: String): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
            addView(resourceIcon("…", "teal").apply {
                elevation = dp(3).toFloat()
                layoutParams = LinearLayout.LayoutParams(dp(78), dp(78))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(16), 0, 0, 0)
                }
                addView(TextView(context).apply {
                    text = resourceId
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(tokens.textPrimary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = "资源详情"
                    textSize = 13f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(7), 0, 0)
                })
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

    private fun resourceRecommendationBlock(item: ResourceItem): View? {
        val catalogById = cachedResourceCatalog.orEmpty().associateBy { it.id }
        val recommendations = resourceRecommendationsFor(item.id)
            .mapNotNull { recommendation ->
                val target = catalogById[recommendation.resourceId]
                if (target == null || target.id == item.id) null else recommendation to target
            }
        if (recommendations.isEmpty()) return null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, 0)
            addView(TextView(context).apply {
                text = "推荐"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(tokens.textPrimary)
            })
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(10), 0, 0) }
                addView(row {
                    recommendations.forEachIndexed { index, pair ->
                        val (recommendation, target) = pair
                        addView(resourceRecommendationCard(target, recommendation).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(72), dp(82)).apply {
                                setMargins(0, 0, if (index == recommendations.lastIndex) 0 else dp(12), 0)
                            }
                        })
                    }
                })
            })
        }
    }

    private fun resourceRecommendationCard(item: ResourceItem, recommendation: ResourceRecommendation): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            contentDescription = "${item.name}，${recommendation.label}"
            setOnClickListener { showResourceDetail(item.id, item) }
            addView(resourceIcon(item.iconText, item.accent))
            addView(TextView(context).apply {
                text = item.name
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }

    private fun resourceRecommendationsFor(resourceId: String): List<ResourceRecommendation> =
        when (resourceId) {
            RESOURCE_HERMES_WEBUI -> listOf(
                ResourceRecommendation(RESOURCE_HERMES_CORE, "基础依赖"),
                ResourceRecommendation(RESOURCE_NODE_RUNTIME, "运行时"),
                ResourceRecommendation(RESOURCE_REASONIX, "同类 Agent")
            )
            RESOURCE_REASONIX -> listOf(
                ResourceRecommendation(RESOURCE_NODE_RUNTIME, "运行时"),
                ResourceRecommendation(RESOURCE_GIT, "源码工具"),
                ResourceRecommendation(RESOURCE_HERMES_WEBUI, "同类 Agent")
            )
            RESOURCE_HERMES_CORE -> listOf(
                ResourceRecommendation(RESOURCE_PYTHON, "运行时"),
                ResourceRecommendation(RESOURCE_UV, "依赖管理"),
                ResourceRecommendation(RESOURCE_GIT, "源码工具"),
                ResourceRecommendation(RESOURCE_CURL, "下载工具")
            )
            RESOURCE_PYTHON -> listOf(
                ResourceRecommendation(RESOURCE_UV, "包管理"),
                ResourceRecommendation(RESOURCE_HERMES_CORE, "上层应用")
            )
            RESOURCE_NODE_RUNTIME -> listOf(
                ResourceRecommendation(RESOURCE_HERMES_WEBUI, "上层应用"),
                ResourceRecommendation(RESOURCE_REASONIX, "编码 Agent")
            )
            RESOURCE_UV -> listOf(
                ResourceRecommendation(RESOURCE_PYTHON, "运行时"),
                ResourceRecommendation(RESOURCE_HERMES_CORE, "上层应用")
            )
            RESOURCE_GIT -> listOf(
                ResourceRecommendation(RESOURCE_CURL, "同级工具"),
                ResourceRecommendation(RESOURCE_HERMES_CORE, "被依赖")
            )
            RESOURCE_CURL -> listOf(
                ResourceRecommendation(RESOURCE_GIT, "同级工具"),
                ResourceRecommendation(RESOURCE_HERMES_CORE, "被依赖")
            )
            else -> listOf(
                ResourceRecommendation(RESOURCE_HERMES_CORE, "AI 基础"),
                ResourceRecommendation(RESOURCE_NODE_RUNTIME, "Web 运行"),
                ResourceRecommendation(RESOURCE_PYTHON, "运行时")
            )
        }

    private fun resourceExecutionPreviewBlock(item: ResourceItem): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
            addView(TextView(context).apply {
                text = "来源"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(tokens.textPrimary)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(14))
                background = roundedBox(tokens.cardBackground, tokens.border, dp(17).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(14), 0, 0) }
                addView(resourceSourceSummaryRow(item))
                addView(divider().apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        setMargins(0, dp(12), 0, dp(12))
                    }
                })
                addView(navigationRow("查看原始 JSON") { showResourceRawJson(item) }.apply {
                    setPadding(0, 0, 0, 0)
                })
            })
        }

    private fun showResourceRawJson(item: ResourceItem) {
        val latestItem = cachedResourceCatalog?.firstOrNull { it.id == item.id } ?: item
        currentResourceDetailId = latestItem.id
        currentScreen = Screen.ResourceRawJson
        clearRootForScreen()
        root.addView(topBar("原始 JSON") { showResourceDetail(latestItem.id, latestItem) })
        root.addView(ScrollView(this).apply {
            addView(TextView(context).apply {
                text = resourceRawJsonForUi(latestItem)
                textSize = 14f
                setTextColor(tokens.textPrimary)
                setPadding(dp(24), dp(20), dp(24), dp(28))
                typeface = Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun resourceSourceSummaryRow(item: ResourceItem): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val tone = KiteTheme.accent(item.accent, tokens)
            addView(TextView(context).apply {
                text = "源"
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
                    setMargins(dp(12), 0, 0, 0)
                }
                addView(TextView(context).apply {
                    text = resourceSourceTitle(item)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = resourceSourceSubtitle(item)
                    textSize = 11.5f
                    setTextColor(tokens.textTertiary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            })
        }

    private fun resourceSourceTitle(item: ResourceItem): String =
        when {
            item.sourceLabel.contains("内置") || item.sizeLabel.contains("内置") -> "内置资源包"
            item.sourceLabel.equals("apt", ignoreCase = true) -> "Ubuntu apt"
            item.sourceLabel.contains("官方") -> "官方来源"
            item.sourceLabel.contains("网络") || item.sizeLabel.contains("网络") -> "网络下载"
            else -> item.sourceLabel.ifBlank { "本地定义" }
        }

    private fun resourceSourceSubtitle(item: ResourceItem): String =
        listOf(item.sourceLabel, item.version, item.sizeLabel)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")

    private fun resourceRawJsonForUi(item: ResourceItem): String =
        item.rawJson.ifBlank {
            JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("version", item.version)
                .put("source", item.sourceLabel)
                .put("category", item.category)
                .put("steps", JSONArray().apply {
                    item.steps.forEach { step ->
                        put(JSONObject()
                            .put("type", step.type)
                            .put("title", step.title)
                            .put("preview", step.preview)
                        )
                    }
                })
                .toString(2)
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
                    "获取来源" to item.sourceLabel,
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
            "获取", "重新获取", "安装", "重新安装" -> handleResourceInstallAction(item)
            "处理中", "获取中" -> reopenResourceInstallWizard(item)
            "卸载中" -> Toast.makeText(this, "${item.name} 正在卸载", Toast.LENGTH_SHORT).show()
            "打开" -> {
                val recipe = resourceOpenRecipe(item)
                if (recipe == null) {
                    Toast.makeText(this, "${item.name} 的打开动作稍后接入", Toast.LENGTH_SHORT).show()
                } else {
                    startResourceOpen(item, recipe)
                }
            }
            "卸载", "继续卸载" -> {
                handleResourceUninstallAction(item)
            }
            else -> Toast.makeText(this, "正在处理 ${item.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reopenResourceInstallWizard(item: ResourceItem) {
        val planSnapshot = resourceInstallStore.planSnapshot()
        val targetId = when {
            planSnapshot.resourceIds.isNotEmpty() &&
                (item.id in planSnapshot.resourceIds || item.id == planSnapshot.targetResourceId) ->
                planSnapshot.targetResourceId
            activeResourceInstallWizard?.targetResourceId?.isNotBlank() == true ->
                activeResourceInstallWizard!!.targetResourceId
            currentResourceInstallTargetId?.isNotBlank() == true ->
                currentResourceInstallTargetId.orEmpty()
            else -> ""
        }
        val planIds = planSnapshot.resourceIds.ifEmpty { resourceInstallWizardPlanIds }
        if (targetId.isBlank() || planIds.isEmpty()) {
            Toast.makeText(this, "${item.name} 正在处理，获取向导暂不可恢复", Toast.LENGTH_SHORT).show()
            return
        }
        currentResourceInstallTargetId = targetId
        resourceInstallWizardPlanIds = planIds
        showResourceInstallWizard(targetId)
    }

    private fun handleResourceInstallAction(item: ResourceItem) {
        if (
            resourceInstallStore.isFailed(item.id) &&
            resourceInstallStore.failedOperation(item.id) != KiteResourceInstallStore.OP_UNINSTALL
        ) {
            handleResourceReinstallAction(item)
            return
        }
        val requestId = ++resourceInstallPlanRequestSerial
        Toast.makeText(this, "正在准备 ${item.name} 的获取队列", Toast.LENGTH_SHORT).show()
        thread(name = "KiteResourceInstallPlan-$requestId-${item.id}", isDaemon = true) {
            val result = runCatching { buildResourceInstallPlan(item) }
            runOnUiThread {
                if (requestId != resourceInstallPlanRequestSerial) return@runOnUiThread
                result.onSuccess { plan ->
                    handleResourceInstallPlanReady(item, plan)
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        "获取队列准备失败：${error.message ?: error.javaClass.simpleName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun handleResourceReinstallAction(item: ResourceItem) {
        Toast.makeText(this, "正在先卸载 ${item.name} 的残留", Toast.LENGTH_SHORT).show()
        handleResourceUninstallAction(item, ResourceUninstallContinuation.Reinstall)
    }

    private fun handleResourceInstallPlanReady(item: ResourceItem, plan: ResourceInstallPlan) {
        if (plan.missing.isNotEmpty()) {
            val missingNames = plan.missing
                .map { it.resource?.name ?: it.requirement }
                .distinct()
                .joinToString("、")
            Toast.makeText(this, "缺少可获取的基础层：$missingNames", Toast.LENGTH_SHORT).show()
            return
        }
        if (plan.steps.isEmpty()) {
            Toast.makeText(this, "${item.name} 已经就绪", Toast.LENGTH_SHORT).show()
            return
        }
        resetResourceInstallPlanTransientState(plan.steps.map { it.id })
        resourceInstallStore.beginPlan(item.id, plan.steps.map { it.id })
        currentResourceInstallTargetId = item.id
        resourceInstallWizardPlanIds = plan.steps.map { it.id }
        showResourceInstallWizard(item.id)
    }

    private fun resetResourceInstallPlanTransientState(resourceIds: List<String>) {
        val recipeIds = resourceIds
            .filter { it.isNotBlank() }
            .distinct()
            .map { resourceId ->
                if (
                    resourceInstallStore.status(resourceId) != null &&
                    !resourceInstallStore.isInstalled(resourceId)
                ) {
                    resourceInstallStore.clear(resourceId)
                }
                pendingResourceUninstallContinuations.remove(resourceId)
                KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL)
            }
        recipeIds.forEach { recipeId ->
            runtimeStates.remove(recipeId)
            activeRunInstanceIds.remove(recipeId)
            suppressedResourceRunSurfaceRecipeIds.remove(recipeId)
        }
        if (recipeIds.isNotEmpty()) {
            CardRunStore.removeRunStatesForRecipes(recipeIds, removeOpenHistory = true)
        }
    }

    private fun handleResourceUninstallAction(
        item: ResourceItem,
        continuation: ResourceUninstallContinuation = pendingResourceUninstallContinuations[item.id]
            ?: ResourceUninstallContinuation.None
    ) {
        val recipe = resourceUninstallRecipe(item)
        if (recipe == null) {
            pendingResourceUninstallContinuations.remove(item.id)
            resourceInstallStore.clear(item.id)
            invalidateResourceCatalogCache()
            Toast.makeText(this, "已移除 ${item.name} 的获取记录", Toast.LENGTH_SHORT).show()
            if (continuation == ResourceUninstallContinuation.Reinstall) {
                handleResourceInstallAction(item)
            } else if (continuation == ResourceUninstallContinuation.CancelFailedInstall) {
                resourceInstallStore.clearPlan()
                currentResourceInstallTargetId = null
                resourceInstallWizardPlanIds = emptyList()
                activeResourceInstallWizard = null
                if (this is CardRunActivity && currentScreen == Screen.CardRun) {
                    closeCardRunTask()
                } else {
                    showResources()
                }
            } else {
                showResources()
            }
        } else {
            startResourceUninstall(item, recipe, continuation)
        }
    }

    private fun handleResourceFailedInstallCancel(item: ResourceItem) {
        Toast.makeText(this, "正在卸载 ${item.name} 的残留", Toast.LENGTH_SHORT).show()
        handleResourceUninstallAction(item, ResourceUninstallContinuation.CancelFailedInstall)
    }

    private fun handleResourceCancelInstallTask(item: ResourceItem) {
        val planIds = resourceInstallStore.planResourceIds()
            .takeIf { item.id in it }
            .orEmpty()
            .ifEmpty { listOf(item.id) }
        cancelResourceInstallTask(
            targetResourceId = item.id,
            planResourceIds = planIds,
            closeWizard = false
        )
    }

    private fun cancelResourceInstallTask(
        targetResourceId: String?,
        planResourceIds: List<String>,
        closeWizard: Boolean
    ) {
        val targetId = targetResourceId.orEmpty()
        val resourceIds = resolveResourceInstallTaskIds(targetId, planResourceIds)
        val catalogById = resourceCatalog(forceRefresh = true).associateBy { it.id }
        val unfinishedIds = resourceIds
            .filterNot { resourceInstallStore.isInstalled(it) }
            .distinct()
        stopResourceInstallRunsForCancel(unfinishedIds, catalogById)
        Toast.makeText(this, "正在取消获取任务", Toast.LENGTH_SHORT).show()
        if (unfinishedIds.isEmpty()) {
            clearResourceInstallTask(
                targetResourceId = targetId,
                planResourceIds = resourceIds,
                closeWizard = closeWizard
            )
            Toast.makeText(this, "获取任务已取消", Toast.LENGTH_SHORT).show()
            return
        }
        root.postDelayed({
            runResourceCancelCleanup(
                targetResourceId = targetId,
                planResourceIds = resourceIds,
                cleanupResourceIds = unfinishedIds,
                closeWizard = closeWizard
            )
        }, TERMINAL_STOP_GRACE_MS + 450L)
    }

    private fun clearResourceInstallTask(
        targetResourceId: String?,
        planResourceIds: List<String>,
        closeWizard: Boolean
    ) {
        val targetId = targetResourceId.orEmpty()
        val resourceIds = resolveResourceInstallTaskIds(targetId, planResourceIds)
        resourceIds.forEach { resourceId ->
            pendingResourceUninstallContinuations.remove(resourceId)
            if (
                resourceInstallStore.status(resourceId) != null &&
                !resourceInstallStore.isInstalled(resourceId) &&
                resourceInstallStore.failedOperation(resourceId) != KiteResourceInstallStore.OP_UNINSTALL
            ) {
                resourceInstallStore.clear(resourceId)
            }
        }
        resourceInstallStore.clearPlan()
        val recipeIds = resourceIds.flatMap { resourceId ->
            listOf(
                KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL),
                KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_UNINSTALL)
            )
        } + listOfNotNull(
            targetId.takeIf { it.isNotBlank() }?.let { resourceInstallWizardRecipe(it, it).id }
        )
        recipeIds.distinct().forEach { recipeId ->
            runtimeStates.remove(recipeId)
            activeRunInstanceIds.remove(recipeId)
            suppressedResourceRunSurfaceRecipeIds.remove(recipeId)
        }
        CardRunStore.removeRunStatesForRecipes(recipeIds, removeOpenHistory = true)
        invalidateResourceCatalogCache()
        if (closeWizard) {
            currentResourceInstallTargetId = null
            resourceInstallWizardPlanIds = emptyList()
            activeResourceInstallWizard = null
            if (this is CardRunActivity) {
                closeCardRunTask()
            } else {
                showResources()
            }
        } else {
            refreshResourceScreenIfVisible()
        }
    }

    private fun resolveResourceInstallTaskIds(targetId: String, planResourceIds: List<String>): List<String> =
        planResourceIds
            .filter { it.isNotBlank() }
            .ifEmpty { resourceInstallStore.planResourceIds() }
            .ifEmpty { listOfNotNull(targetId.takeIf { it.isNotBlank() }) }
            .distinct()

    private fun stopResourceInstallRunsForCancel(
        resourceIds: List<String>,
        catalogById: Map<String, ResourceItem>
    ) {
        val childRunsByRecipeId = activeResourceInstallWizard
            ?.wizardInstanceId
            ?.let { CardRunStore.childrenOf(it) }
            .orEmpty()
            .associateBy { it.recipeId }
        resourceIds.forEach { resourceId ->
            val item = catalogById[resourceId] ?: return@forEach
            val recipe = resourceInstallRecipe(item) ?: return@forEach
            val state = childRunsByRecipeId[recipe.id] ?: resourceInstallRecipeState(resourceId) ?: return@forEach
            if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened) {
                stopResourceRecipeForCancel(recipe, state)
            }
        }
    }

    private fun stopResourceRecipeForCancel(recipe: KiteRecipe, state: RecipeRuntimeState) {
        runtimeStates[recipe.id] = CardRunStore.update(
            recipe = recipe,
            status = RecipeRunStatus.Stopping,
            instanceId = state.instanceId,
            runId = state.runId,
            terminalSessionId = state.terminalSessionId,
            pid = state.pid,
            lastMeaningfulOutput = "正在取消获取任务"
        )
        state.terminalSessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
            TerminalRuntimeHost.sendCommand(applicationContext, "\u0003", sessionId)
            root.postDelayed(
                { TerminalRuntimeHost.endSession(applicationContext, sessionId) },
                TERMINAL_STOP_GRACE_MS
            )
            return
        }
        val callback: (BridgeResult) -> Unit = { result ->
            diagnostics.logBridgeEvent(
                "resource_cancel_stop_result",
                recipe,
                mapOf(
                    "runId" to state.runId.orEmpty(),
                    "status" to result.status,
                    "ok" to result.ok.toString(),
                    "accepted" to result.accepted.toString(),
                    "message" to result.message.take(300)
                )
            )
        }
        if (!state.runId.isNullOrBlank()) {
            bridgeClient.stopRun(
                recipe = recipe,
                runId = state.runId,
                pid = state.pid,
                rootPid = state.rootPid,
                processGroupId = state.processGroupId,
                systemSessionId = state.systemSessionId,
                callback = callback
            )
        } else {
            bridgeClient.stopRecipe(recipe, callback)
        }
    }

    private fun runResourceCancelCleanup(
        targetResourceId: String,
        planResourceIds: List<String>,
        cleanupResourceIds: List<String>,
        closeWizard: Boolean
    ) {
        val recipe = resourceCancelCleanupRecipe(cleanupResourceIds)
        bridgeClient.runRecipe(recipe) { result ->
            runOnUiThread {
                clearResourceInstallTask(
                    targetResourceId = targetResourceId,
                    planResourceIds = planResourceIds,
                    closeWizard = closeWizard
                )
                val message = if (result.ok || result.accepted) {
                    "获取任务已取消，临时内容已清理"
                } else {
                    "获取任务已取消，部分残留稍后再清理"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resourceCancelCleanupRecipe(resourceIds: List<String>): KiteRecipe {
        val cleanIds = resourceIds.map { KiteResourceInstallRecipes.safeId(it) }.distinct()
        val step = KiteRecipeStep(
            id = "cancel_cleanup",
            type = KiteRecipe.STEP_SHELL,
            cmd = KiteResourceInstallRecipes.cancelCleanupCommand(cleanIds),
            surfaceMode = KiteRecipe.SURFACE_MODE_SILENT,
            workdir = "/workspace",
            timeoutMs = 120_000L
        )
        return KiteRecipe(
            id = "resource-cancel-cleanup-${UUID.randomUUID()}",
            name = "资源取消清理",
            description = cleanIds.joinToString("、"),
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = false),
            execution = KiteExecution.steps(listOf(step)),
            actions = emptyMap(),
            runtimeSource = RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE
        )
    }

    private fun showResourceInstallWizard(targetResourceId: String? = currentResourceInstallTargetId) {
        val targetId = targetResourceId?.takeIf { it.isNotBlank() } ?: return showResources()
        val pendingIds = resourceInstallStore.pendingPlanResourceIds()
        val planIds = resourceInstallWizardPlanIds
            .ifEmpty { resourceInstallStore.planResourceIds() }
            .ifEmpty { pendingIds }
        if (planIds.isEmpty()) return showResources()
        val catalog = resourceCatalog(forceRefresh = false).associateBy { it.id }
        val target = catalog[targetId]
        val wizardRecipe = resourceInstallWizardRecipe(targetId, target?.name ?: targetId)
        val wizardInstanceId = activeResourceInstallWizard
            ?.takeIf { it.targetResourceId == targetId }
            ?.wizardInstanceId
            ?: resourceInstallWizardInstanceId(targetId)
        if (this !is CardRunActivity && currentScreen != Screen.CardRun) {
            CardRunStore.registerRecipe(wizardRecipe)
            startActivity(
                CardRunIntents.resourceInstallWizardIntent(
                    context = this,
                    recipeId = wizardRecipe.id,
                    instanceId = wizardInstanceId,
                    targetResourceId = targetId,
                    planResourceIds = planIds
                )
            )
            return
        }
        showResourceInstallWizardSurface(
            targetResourceId = targetId,
            planResourceIds = planIds,
            recipeId = wizardRecipe.id,
            instanceId = wizardInstanceId
        )
    }

    private fun showResourceInstallWizardSurface(
        targetResourceId: String,
        planResourceIds: List<String>,
        recipeId: String = "resource-install-wizard-${KiteResourceInstallRecipes.safeId(targetResourceId)}",
        instanceId: String = resourceInstallWizardInstanceId(targetResourceId)
    ) {
        val targetId = targetResourceId.takeIf { it.isNotBlank() } ?: return
        val planIds = planResourceIds.filter { it.isNotBlank() }
        if (planIds.isEmpty()) return
        currentResourceInstallTargetId = targetId
        resourceInstallWizardPlanIds = planIds
        val catalog = resourceCatalog(forceRefresh = false).associateBy { it.id }
        val target = catalog[targetId]
        val wizardRecipe = resourceInstallWizardRecipe(targetId, target?.name ?: targetId).copy(id = recipeId)
        val wizardInstanceId = instanceId.takeIf { it.isNotBlank() } ?: resourceInstallWizardInstanceId(targetId)
        activeResourceInstallWizard = ResourceInstallWizardContext(
            targetResourceId = targetId,
            planResourceIds = planIds,
            wizardRecipeId = wizardRecipe.id,
            wizardInstanceId = wizardInstanceId
        )
        CardRunStore.registerRecipe(wizardRecipe)
        activeRunInstanceIds[wizardRecipe.id] = wizardInstanceId
        focusedRunRecipeId = wizardRecipe.id
        focusedRunInstanceId = wizardInstanceId
        if (CardRunStore.get(wizardInstanceId) == null) {
            CardRunStore.start(
                recipe = wizardRecipe,
                instanceId = wizardInstanceId,
                ownerKind = RecipeRuntimeState.OWNER_KIND_INSTALL_WIZARD,
                stepId = targetId
            )
        }
        runtimeStates[wizardRecipe.id] = CardRunStore.update(
            wizardRecipe,
            RecipeRunStatus.Opened,
            instanceId = wizardInstanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_INSTALL_WIZARD,
            stepId = targetId,
            surface = CardRunSurface.InstallWizard,
            currentStepIndex = 0,
            lastMeaningfulOutput = "等待获取确认"
        )
        title = wizardRecipe.name
        if (this is CardRunActivity) {
            applyCardTaskDescription(wizardRecipe)
            registerCardRunTaskCloser(wizardInstanceId)
        }
        showCardRunSurface(wizardRecipe)
    }

    private fun resourceInstallWizardContent(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(34))
            val context = activeResourceInstallWizard
            val targetId = context?.targetResourceId ?: currentResourceInstallTargetId.orEmpty()
            val catalog = resourceCatalogForUiRender("install_wizard_content").associateBy { it.id }
            val planSnapshot = resourceInstallStore.planSnapshot()
            val pendingIds = planSnapshot.pendingResourceIds
            val planIds = context?.planResourceIds.orEmpty()
                .ifEmpty { resourceInstallWizardPlanIds }
                .ifEmpty { planSnapshot.resourceIds }
                .ifEmpty { pendingIds }
            resourceInstallWizardPlanIds = planIds
            val registrySnapshot = resourceInstallStore.registrySnapshot(planIds)
            fun entry(resourceId: String) = registrySnapshot[resourceId]
            fun stepStatus(resourceId: String): String = planSnapshot.stepStatus(resourceId)
            fun isRunningStep(resourceId: String): Boolean =
                stepStatus(resourceId) == KiteResourceInstallStore.PLAN_STEP_RUNNING
            fun isUninstallingStep(resourceId: String): Boolean =
                entry(resourceId)?.uninstalling == true
            fun isFailed(resourceId: String): Boolean =
                !isUninstallingStep(resourceId) &&
                    (entry(resourceId)?.failed == true ||
                        stepStatus(resourceId) == KiteResourceInstallStore.PLAN_STEP_FAILED)
            val target = catalog[targetId]
            val failedIds = planIds.filter { id -> isFailed(id) }
            val uninstallingIds = planIds.filter { id -> isUninstallingStep(id) }
            val hasFailure = failedIds.isNotEmpty()
            val activeId = planIds.firstOrNull { isRunningStep(it) }
                ?: uninstallingIds.firstOrNull()
                ?: failedIds.firstOrNull()
                ?: pendingIds.firstOrNull()
            val completedCount = planIds.count { id ->
                stepStatus(id) == KiteResourceInstallStore.PLAN_STEP_DONE ||
                    (resourceItemIsInstalled(catalog[id]) && entry(id)?.failed != true)
            }
            val hasRunningStep = planIds.any { isRunningStep(it) }
            val hasUninstallingStep = uninstallingIds.isNotEmpty()
            val hasPending = pendingIds.isNotEmpty() && !hasFailure
            val rowHosts = linkedMapOf<String, LinearLayout>()
            val rowBindings = linkedMapOf<String, ResourceInstallWizardRowBinding>()
            var headerDetailTextView: TextView? = null
            var headerProgressTextView: TextView? = null

            addView(resourceInstallWizardHeader(
                title = target?.name ?: targetId,
                detail = when {
                    hasRunningStep -> "正在获取：${catalog[activeId]?.name ?: activeId.orEmpty()}"
                    hasUninstallingStep -> "正在卸载：${catalog[activeId]?.name ?: activeId.orEmpty()}"
                    hasFailure -> "发现异常请手动处理"
                    hasPending -> "将按顺序获取 ${planIds.size} 个资源"
                    else -> "获取队列已完成"
                },
                completedCount = completedCount.coerceIn(0, planIds.size.coerceAtLeast(1)),
                totalCount = planIds.size,
                onBind = { _, detailView, progressView ->
                    headerDetailTextView = detailView
                    headerProgressTextView = progressView
                }
            ))
            val primaryActionHost = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(resourceInstallWizardPrimaryAction(
                    hasRunningStep = hasRunningStep,
                    hasUninstallingStep = hasUninstallingStep,
                    hasPending = hasPending,
                    hasFailure = hasFailure
                ))
            }
            addView(primaryActionHost)
            addView(sectionTitle("获取队列").apply { setPadding(0, dp(24), 0, dp(12)) })
            val rowsHost = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            planIds.forEachIndexed { index, resourceId ->
                val item = catalog[resourceId]
                val rowHost = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(resourceInstallWizardStepRow(
                        index = index,
                        total = planIds.size,
                        item = item,
                        resourceId = resourceId,
                        isActive = resourceId == activeId,
                        planSnapshot = planSnapshot,
                        registryEntry = registrySnapshot[resourceId],
                        onBind = { rowBinding -> rowBindings[resourceId] = rowBinding }
                    ))
                }
                rowHosts[resourceId] = rowHost
                rowsHost.addView(rowHost)
            }
            addView(rowsHost)
            resourceInstallWizardBinding = ResourceInstallWizardBinding(
                targetResourceId = targetId,
                planResourceIds = planIds,
                headerDetailTextView = headerDetailTextView,
                headerProgressTextView = headerProgressTextView,
                primaryActionHost = primaryActionHost,
                rowsHost = rowsHost,
                rowHosts = rowHosts,
                rowBindings = rowBindings
            )
            scheduleForegroundLiveTickIfNeeded()
        }

    private fun resourceInstallWizardRecipe(targetResourceId: String, targetName: String): KiteRecipe =
        KiteRecipe(
            id = "resource-install-wizard-${KiteResourceInstallRecipes.safeId(targetResourceId)}",
            name = "$targetName 获取向导",
            description = "管理资源获取队列",
            type = KiteRecipe.TYPE_TEMPLATE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = true),
            execution = KiteExecution.steps(emptyList()),
            actions = emptyMap(),
            runtimeSource = RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE
        )

    private fun resourceInstallWizardInstanceId(targetResourceId: String): String =
        "resource-install-wizard-${KiteResourceInstallRecipes.safeId(targetResourceId)}-${UUID.randomUUID().toString().replace("-", "")}"

    private fun resourceRunInstanceId(resourceId: String, recipe: KiteRecipe): String =
        "resource-run-${KiteResourceInstallRecipes.safeId(resourceId)}-${KiteResourceInstallRecipes.safeId(recipe.id)}-${UUID.randomUUID().toString().replace("-", "")}"

    private fun resourceInstallWizardHeader(
        title: String,
        detail: String,
        completedCount: Int,
        totalCount: Int,
        onBind: ((TextView, TextView, TextView) -> Unit)? = null
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
        var titleTextView: TextView? = null
        var detailTextView: TextView? = null
        addView(row {
            gravity = Gravity.CENTER_VERTICAL
            addView(resourceIcon("↓", "teal").apply {
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                    setMargins(0, 0, dp(14), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val titleView = TextView(context).apply {
                    text = title
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                titleTextView = titleView
                addView(titleView)
                val detailView = TextView(context).apply {
                    text = detail
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(6), 0, 0)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                detailTextView = detailView
                addView(detailView)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        val progressView = TextView(context).apply {
            text = if (totalCount > 0) "$completedCount/$totalCount" else "--"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.primaryStrong)
            setPadding(0, dp(14), 0, 0)
        }
        addView(progressView)
        val titleView = titleTextView
        val detailView = detailTextView
        if (titleView != null && detailView != null) {
            onBind?.invoke(titleView, detailView, progressView)
        }
    }

    private fun resourceInstallWizardPrimaryAction(
        hasRunningStep: Boolean,
        hasUninstallingStep: Boolean,
        hasPending: Boolean,
        hasFailure: Boolean
    ): View {
        if (hasUninstallingStep) {
            return resourceInstallWizardActionButton(
                label = "卸载中",
                enabled = false
            ) {}.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                    setMargins(0, dp(16), 0, 0)
                }
            }
        }
        if (hasFailure) {
            return resourceInstallWizardActionButton(
                label = "发现异常请手动处理",
                enabled = false
            ) {}.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                    setMargins(0, dp(16), 0, 0)
                }
            }
        }
        return resourceInstallWizardActionButton(
            label = when {
                hasRunningStep -> "获取中"
                hasPending -> "开始获取"
                else -> "完成"
            },
            enabled = !hasRunningStep
        ) {
            if (hasPending) {
                startNextResourceInstallFromPlan()
            } else {
                currentResourceInstallTargetId = null
                resourceInstallWizardPlanIds = emptyList()
                activeResourceInstallWizard = null
                if (this@MainActivity is CardRunActivity) {
                    closeCardRunTask()
                } else {
                    showResources()
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                setMargins(0, dp(16), 0, 0)
            }
        }
    }

    private fun resourceInstallWizardActionButton(
        label: String,
        danger: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        val foreground = when {
            !enabled -> tokens.textSecondary
            danger -> tokens.danger
            else -> tokens.buttonText
        }
        setTextColor(foreground)
        background = roundedBox(
            when {
                !enabled -> tokens.surface
                danger -> tintBackground(tokens.danger)
                else -> tokens.primaryStrong
            },
            if (danger) tintBackgroundBorder(tokens.danger) else Color.TRANSPARENT,
            dp(18).toFloat(),
            0
        )
        alpha = if (enabled) 1f else 0.72f
        if (enabled) setOnClickListener { onClick() }
    }

    private fun resourceInstallWizardStepRow(
        index: Int,
        total: Int,
        item: ResourceItem?,
        resourceId: String,
        isActive: Boolean,
        planSnapshot: com.kite.app.resources.KiteResourcePlanSnapshot? = null,
        registryEntry: KiteResourceRegistryEntry? = null,
        onBind: ((ResourceInstallWizardRowBinding) -> Unit)? = null
    ): View {
        val runOperation = resourceVisibleRunOperation(resourceId, registryEntry)
        val recipeState = resourceRunStateForOperation(resourceId, runOperation)
        val planStepStatus = planSnapshot?.stepStatus(resourceId) ?: resourceInstallStore.planStepStatus(resourceId)
        val uninstalling = registryEntry?.uninstalling == true
        val uninstallFailed = registryEntry?.failed == true &&
            registryEntry.operation == KiteResourceInstallStore.OP_UNINSTALL
        val failed = registryEntry?.failed == true ||
            planStepStatus == KiteResourceInstallStore.PLAN_STEP_FAILED
        val blocked = planStepStatus == KiteResourceInstallStore.PLAN_STEP_BLOCKED
        val done = planStepStatus == KiteResourceInstallStore.PLAN_STEP_DONE
        val running = planStepStatus == KiteResourceInstallStore.PLAN_STEP_RUNNING
        val installed = resourceItemIsInstalled(item)
        val statusLabel = when {
            uninstalling -> "卸载中"
            uninstallFailed -> "卸载失败"
            failed -> "失败"
            running -> "获取中"
            installed || done -> "已完成"
            blocked -> "已暂停"
            isActive -> "等待获取"
            else -> "排队"
        }
        val tone = when {
            uninstallFailed || failed -> tokens.danger
            statusLabel == "获取中" -> tokens.primaryStrong
            statusLabel == "卸载中" -> tokens.primaryStrong
            statusLabel == "已完成" -> tokens.success
            else -> tokens.textSecondary
        }
        val canOpenRunSurface = item != null && recipeState != null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = roundedBox(tokens.cardBackground, if (isActive) tokens.primarySoft else tokens.border, dp(16).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(10))
            }
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = (index + 1).toString()
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(tone)
                    background = roundedBox(tintBackground(tone), Color.TRANSPARENT, dp(12).toFloat(), 0)
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        setMargins(0, 0, dp(12), 0)
                    }
                })
                var subtitleTextView: TextView? = null
                var openButtonTextView: TextView? = null
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = item?.name ?: resourceId
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    val subtitleView = TextView(context).apply {
                        text = "${item?.sourceLabel ?: "资源"} · ${index + 1}/$total"
                        textSize = 11.5f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(4), 0, 0)
                    }
                    subtitleView.text = resourceInstallWizardStepSubtitle(item, index, total, recipeState)
                    subtitleTextView = subtitleView
                    addView(subtitleView)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val statusView = TextView(context).apply {
                    text = statusLabel
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(tone)
                    background = roundedBox(tintBackground(tone), Color.TRANSPARENT, dp(11).toFloat(), 0)
                    setPadding(dp(9), 0, dp(9), 0)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22))
                }
                addView(statusView)
                item?.let { resourceItem ->
                    val openButton = resourceWizardOpenButton(enabled = canOpenRunSurface) {
                        if (resourceVisibleRunState(resourceId, registryEntry) == null) {
                            Toast.makeText(this@MainActivity, "报告正在准备", Toast.LENGTH_SHORT).show()
                        } else {
                            openResourceInstallRunSurface(resourceItem, CardRunSurface.Report, runOperation)
                        }
                    }
                    openButtonTextView = openButton
                    addView(openButton)
                    if (failed && !uninstalling) {
                        addView(resourceWizardInlineButton("卸载", danger = true) {
                            showResourceWizardUninstallConfirm(resourceItem)
                        })
                    }
                }
                subtitleTextView?.let { subtitleView ->
                    onBind?.invoke(
                        ResourceInstallWizardRowBinding(
                            resourceId = resourceId,
                            runOperation = runOperation,
                            subtitleTextView = subtitleView,
                            statusTextView = statusView,
                            openButtonTextView = openButtonTextView
                        )
                    )
                }
            })
            val secondarySurface = recipeState?.surface
            if (
                canOpenRunSurface &&
                (secondarySurface == CardRunSurface.Terminal || secondarySurface == CardRunSurface.Web)
            ) {
                addView(row {
                    setPadding(dp(46), dp(10), 0, 0)
                    item?.let { resourceItem ->
                        if (secondarySurface == CardRunSurface.Terminal) {
                            addView(resourceWizardInlineButton("打开终端") {
                                openResourceInstallRunSurface(resourceItem, CardRunSurface.Terminal, runOperation)
                            })
                        }
                        if (secondarySurface == CardRunSurface.Web) {
                            addView(resourceWizardInlineButton("打开网页") {
                                openResourceInstallRunSurface(resourceItem, CardRunSurface.Web, runOperation)
                            })
                        }
                    }
                })
            }
        }
    }

    private fun showResourceWizardUninstallConfirm(item: ResourceItem) {
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(20).toFloat())
            addView(TextView(context).apply {
                text = "卸载异常资源？"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = "将只执行 ${item.name} 的卸载动作，把它恢复为未获取状态。完成后仍留在当前获取向导里，不会自动重新获取。"
                textSize = 13f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(row {
                setPadding(0, dp(20), 0, 0)
                addView(TextView(context).apply {
                    text = "取消"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(tokens.textPrimary)
                    background = roundedBox(tokens.surface, tokens.border, dp(13).toFloat())
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        setMargins(0, 0, dp(8), 0)
                    }
                    setOnClickListener { dialog.dismiss() }
                })
                addView(TextView(context).apply {
                    text = "卸载"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(tokens.danger)
                    background = roundedBox(tintBackground(tokens.danger), tintBackgroundBorder(tokens.danger), dp(13).toFloat())
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        setMargins(dp(8), 0, 0, 0)
                    }
                    setOnClickListener {
                        dialog.dismiss()
                        runResourceWizardUninstall(item)
                    }
                })
            })
        }
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun runResourceWizardUninstall(item: ResourceItem) {
        val recipe = resourceUninstallRecipe(item)
        if (recipe == null) {
            pendingResourceUninstallContinuations.remove(item.id)
            resourceInstallStore.clear(item.id)
            resourceInstallStore.resumePlanFrom(item.id)
            invalidateResourceCatalogCache()
            Toast.makeText(this, "${item.name} 已恢复为未获取", Toast.LENGTH_SHORT).show()
            requestVisibleResourceInstallWizardRefresh("wizard_uninstall_no_recipe:${item.id}")
            refreshResourceScreenIfVisible()
            return
        }
        Toast.makeText(this, "正在卸载 ${item.name}", Toast.LENGTH_SHORT).show()
        startResourceUninstall(item, recipe, ResourceUninstallContinuation.ResumeInstallWizard)
    }

    private fun resourceWizardOpenButton(enabled: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = "打开"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)).apply {
                setMargins(dp(8), 0, 0, 0)
            }
            setOnClickListener { if (isEnabled) onClick() }
            applyResourceWizardOpenButtonState(this, enabled)
        }

    private fun applyResourceWizardOpenButtonState(button: TextView, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.52f
        button.setTextColor(if (enabled) tokens.buttonText else tokens.textTertiary)
        button.background = roundedBox(
            if (enabled) tokens.primaryStrong else tokens.surface,
            if (enabled) Color.TRANSPARENT else tokens.border,
            dp(12).toFloat(),
            0
        )
    }

    private fun resourceWizardInlineButton(
        label: String,
        danger: Boolean = false,
        onClick: () -> Unit
    ): View =
        TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            val tone = if (danger) tokens.danger else tokens.primaryStrong
            setTextColor(tone)
            background = roundedBox(
                if (danger) tintBackground(tokens.danger) else tokens.primarySubtle,
                if (danger) tintBackgroundBorder(tokens.danger) else tokens.primarySoft,
                dp(13).toFloat()
            )
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)).apply {
                setMargins(0, 0, dp(8), 0)
            }
            setOnClickListener { onClick() }
        }

    private fun resourceInstallWizardStepSubtitle(
        item: ResourceItem?,
        index: Int,
        total: Int,
        state: RecipeRuntimeState?
    ): String {
        val base = "${item?.sourceLabel ?: "资源"} · ${index + 1}/$total"
        val elapsed = state?.let { formatCardRunElapsed(it) } ?: return base
        return when {
            state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened -> "$base · 运行 $elapsed"
            state.status == RecipeRunStatus.Completed -> "$base · 用时 $elapsed"
            state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable -> "$base · 失败 $elapsed"
            state.status == RecipeRunStatus.Stopped -> "$base · 已停止 $elapsed"
            else -> base
        }
    }

    private fun requestVisibleResourceInstallWizardRefresh(reason: String) {
        val binding = resourceInstallWizardBinding ?: return
        if (!resourceInstallWizardSurfaceActive() || currentScreen != Screen.CardRun) return
        val requestId = ++resourceInstallWizardRefreshSerial
        val targetId = binding.targetResourceId
        val planIds = binding.planResourceIds
        thread(name = "KiteInstallWizardRefresh", isDaemon = true) {
            val uiState = runCatching {
                buildResourceInstallWizardUiState(targetId, planIds)
            }.getOrNull() ?: return@thread
            runOnUiThread {
                if (requestId != resourceInstallWizardRefreshSerial) return@runOnUiThread
                if (resourceInstallWizardBinding !== binding || currentScreen != Screen.CardRun) return@runOnUiThread
                applyResourceInstallWizardUiState(binding, uiState)
                diagnostics.logRecipeEvent(
                    "install_wizard_local_refresh",
                    null,
                    mapOf(
                        "reason" to reason,
                        "target" to targetId,
                        "rows" to uiState.planIds.size.toString()
                    )
                )
            }
        }
    }

    private fun buildResourceInstallWizardUiState(
        targetId: String,
        seedPlanIds: List<String>
    ): ResourceInstallWizardUiState {
        val catalog = (cachedResourceCatalog ?: resourceCatalog(forceRefresh = false)).associateBy { it.id }
        val planSnapshot = resourceInstallStore.planSnapshot()
        val pendingIds = planSnapshot.pendingResourceIds
        val planIds = seedPlanIds
            .ifEmpty { resourceInstallWizardPlanIds }
            .ifEmpty { planSnapshot.resourceIds }
            .ifEmpty { pendingIds }
        val registrySnapshot = resourceInstallStore.registrySnapshot(planIds)
        fun entry(resourceId: String) = registrySnapshot[resourceId]
        fun stepStatus(resourceId: String): String = planSnapshot.stepStatus(resourceId)
            fun isRunningStep(resourceId: String): Boolean =
                stepStatus(resourceId) == KiteResourceInstallStore.PLAN_STEP_RUNNING
            fun isUninstallingStep(resourceId: String): Boolean =
                entry(resourceId)?.uninstalling == true
            fun isFailed(resourceId: String): Boolean =
                !isUninstallingStep(resourceId) &&
                    (entry(resourceId)?.failed == true ||
                        stepStatus(resourceId) == KiteResourceInstallStore.PLAN_STEP_FAILED)
            val failedIds = planIds.filter { id -> isFailed(id) }
            val uninstallingIds = planIds.filter { id -> isUninstallingStep(id) }
            val hasFailure = failedIds.isNotEmpty()
            val activeId = planIds.firstOrNull { isRunningStep(it) }
                ?: uninstallingIds.firstOrNull()
                ?: failedIds.firstOrNull()
                ?: pendingIds.firstOrNull()
        val completedCount = planIds.count { id ->
            stepStatus(id) == KiteResourceInstallStore.PLAN_STEP_DONE ||
                (resourceItemIsInstalled(catalog[id]) && entry(id)?.failed != true)
        }
        val hasRunningStep = planIds.any { isRunningStep(it) }
        val hasUninstallingStep = uninstallingIds.isNotEmpty()
        val hasPending = pendingIds.isNotEmpty() && !hasFailure
        val detail = when {
            hasRunningStep -> "正在获取：${catalog[activeId]?.name ?: activeId.orEmpty()}"
            hasUninstallingStep -> "正在卸载：${catalog[activeId]?.name ?: activeId.orEmpty()}"
            hasFailure -> "发现异常请手动处理"
            hasPending -> "将按顺序获取 ${planIds.size} 个资源"
            else -> "获取队列已完成"
        }
        return ResourceInstallWizardUiState(
            targetId = targetId,
            planIds = planIds,
            catalog = catalog,
            planSnapshot = planSnapshot,
            registrySnapshot = registrySnapshot,
            activeId = activeId,
            detail = detail,
            completedCount = completedCount.coerceIn(0, planIds.size.coerceAtLeast(1)),
            hasRunningStep = hasRunningStep,
            hasUninstallingStep = hasUninstallingStep,
            hasPending = hasPending,
            hasFailure = hasFailure
        )
    }

    private fun applyResourceInstallWizardUiState(
        binding: ResourceInstallWizardBinding,
        uiState: ResourceInstallWizardUiState
    ) {
        binding.headerDetailTextView?.text = uiState.detail
        binding.headerProgressTextView?.text = if (uiState.planIds.isNotEmpty()) {
            "${uiState.completedCount}/${uiState.planIds.size}"
        } else {
            "--"
        }
        binding.primaryActionHost.removeAllViews()
        binding.primaryActionHost.addView(resourceInstallWizardPrimaryAction(
            hasRunningStep = uiState.hasRunningStep,
            hasUninstallingStep = uiState.hasUninstallingStep,
            hasPending = uiState.hasPending,
            hasFailure = uiState.hasFailure
        ))
        if (binding.planResourceIds != uiState.planIds) {
            binding.planResourceIds = uiState.planIds
            binding.rowHosts.clear()
            binding.rowBindings.clear()
            binding.rowsHost.removeAllViews()
            uiState.planIds.forEachIndexed { index, resourceId ->
                val rowHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                binding.rowHosts[resourceId] = rowHost
                binding.rowsHost.addView(rowHost)
                renderResourceInstallWizardRowInto(binding, uiState, index, resourceId)
            }
        } else {
            uiState.planIds.forEachIndexed { index, resourceId ->
                renderResourceInstallWizardRowInto(binding, uiState, index, resourceId)
            }
        }
        updateVisibleResourceInstallWizardElapsed()
    }

    private fun renderResourceInstallWizardRowInto(
        binding: ResourceInstallWizardBinding,
        uiState: ResourceInstallWizardUiState,
        index: Int,
        resourceId: String
    ) {
        val host = binding.rowHosts[resourceId] ?: return
        binding.rowBindings.remove(resourceId)
        host.removeAllViews()
        host.addView(resourceInstallWizardStepRow(
            index = index,
            total = uiState.planIds.size,
            item = uiState.catalog[resourceId],
            resourceId = resourceId,
            isActive = resourceId == uiState.activeId,
            planSnapshot = uiState.planSnapshot,
            registryEntry = uiState.registrySnapshot[resourceId],
            onBind = { rowBinding -> binding.rowBindings[resourceId] = rowBinding }
        ))
    }

    private fun updateVisibleResourceInstallWizardElapsed(): Boolean {
        val binding = resourceInstallWizardBinding ?: return false
        if (currentScreen != Screen.CardRun || !resourceInstallWizardSurfaceActive()) return false
        var keepTicking = false
        binding.planResourceIds.forEachIndexed { index, resourceId ->
            val rowBinding = binding.rowBindings[resourceId]
            val state = rowBinding?.let {
                resourceRunStateForOperation(resourceId, it.runOperation)
            } ?: resourceInstallRecipeState(resourceId)
            val item = cachedResourceCatalog?.firstOrNull { it.id == resourceId }
            rowBinding?.openButtonTextView?.let { button ->
                applyResourceWizardOpenButtonState(button, state != null)
            }
            if (state != null && rowBinding != null) {
                rowBinding.subtitleTextView.text = resourceInstallWizardStepSubtitle(
                    item = item,
                    index = index,
                    total = binding.planResourceIds.size,
                    state = state
                )
                if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened) {
                    keepTicking = true
                }
            }
        }
        return keepTicking
    }

    private fun resourceVisibleRunOperation(
        resourceId: String,
        registryEntry: KiteResourceRegistryEntry? = resourceInstallStore.registryEntry(resourceId)
    ): String = when {
        registryEntry?.uninstalling == true -> KiteResourceInstallRecipes.OP_UNINSTALL
        registryEntry?.failed == true && registryEntry.operation == KiteResourceInstallStore.OP_UNINSTALL ->
            KiteResourceInstallRecipes.OP_UNINSTALL
        else -> KiteResourceInstallRecipes.OP_INSTALL
    }

    private fun resourceRunStateForOperation(resourceId: String, operation: String): RecipeRuntimeState? =
        CardRunStore.currentForRecipe(KiteResourceInstallRecipes.recipeId(resourceId, operation))

    private fun resourceRunRecipeForOperation(item: ResourceItem, operation: String): KiteRecipe? =
        if (operation == KiteResourceInstallRecipes.OP_UNINSTALL) {
            resourceUninstallRecipe(item)
        } else {
            resourceInstallRecipe(item)
        }

    private fun resourceVisibleRunState(
        resourceId: String,
        registryEntry: KiteResourceRegistryEntry? = resourceInstallStore.registryEntry(resourceId)
    ): RecipeRuntimeState? =
        resourceRunStateForOperation(resourceId, resourceVisibleRunOperation(resourceId, registryEntry))

    private fun resourceInstallRecipeState(resourceId: String): RecipeRuntimeState? =
        resourceRunStateForOperation(resourceId, KiteResourceInstallRecipes.OP_INSTALL)

    private fun resourceRunIsActive(resourceId: String): Boolean {
        val state = resourceInstallRecipeState(resourceId) ?: return false
        return state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened
    }

    private fun resourceInstallWizardSurfaceActive(): Boolean =
        currentScreen == Screen.CardRun &&
            activeResourceInstallWizard?.let { context ->
                focusedRunRecipeId == context.wizardRecipeId ||
                    focusedRunInstanceId == context.wizardInstanceId
            } == true

    private fun resourceInstallWizardRecipeFor(context: ResourceInstallWizardContext): KiteRecipe {
        CardRunStore.registeredRecipe(context.wizardRecipeId)?.let { return it }
        val targetName = resourceCatalogForUiRender("install_wizard_recipe")
            .firstOrNull { it.id == context.targetResourceId }
            ?.name
            ?: context.targetResourceId
        return resourceInstallWizardRecipe(context.targetResourceId, targetName).also {
            CardRunStore.registerRecipe(it)
        }
    }

    private fun resourceInstallWizardOwnsRecipe(recipe: KiteRecipe): Boolean {
        if (recipe.runtimeSource != KiteResourceInstallRecipes.RUNTIME_SOURCE) return false
        val context = activeResourceInstallWizard ?: return false
        val resourceId = resourceIdForRecipe(recipe) ?: return false
        return resourceId in context.planResourceIds
    }

    private fun resourceInstallWizardShouldHost(recipe: KiteRecipe): Boolean =
        resourceInstallWizardSurfaceActive() && resourceInstallWizardOwnsRecipe(recipe)

    private fun resourceRunSurfaceSuppressed(recipe: KiteRecipe): Boolean =
        resourceInstallWizardShouldHost(recipe) || recipe.id in suppressedResourceRunSurfaceRecipeIds

    private fun renderResourceInstallWizardFor(recipe: KiteRecipe): Boolean {
        if (!resourceInstallWizardShouldHost(recipe)) return false
        val context = activeResourceInstallWizard ?: return false
        if (resourceInstallWizardBinding != null && currentScreen == Screen.CardRun) {
            requestVisibleResourceInstallWizardRefresh("hosted:${recipe.id}")
        } else {
            showCardRunSurface(resourceInstallWizardRecipeFor(context))
        }
        return true
    }

    private fun resourceInstallWizardSelectedRun(
        recipe: KiteRecipe,
        surface: CardRunSurface
    ): Pair<KiteRecipe, RecipeRuntimeState>? {
        if (recipe.runtimeSource != RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE) return null
        if (surface == CardRunSurface.InstallWizard) return null
        val context = activeResourceInstallWizard ?: return null
        val resourceId = context.selectedResourceId ?: return null
        val item = resourceCatalogForUiRender("install_wizard_selected_run").firstOrNull { it.id == resourceId } ?: return null
        val childRecipe = resourceRunRecipeForOperation(item, context.selectedOperation) ?: return null
        CardRunStore.registerRecipe(childRecipe)
        val childState = CardRunStore.currentForRecipe(childRecipe.id)
            ?: runtimeStates[childRecipe.id]
            ?: return null
        return childRecipe to childState
    }

    private fun openResourceInstallRunSurface(
        item: ResourceItem,
        surface: CardRunSurface,
        operation: String = resourceVisibleRunOperation(item.id)
    ) {
        val recipe = resourceRunRecipeForOperation(item, operation) ?: return
        CardRunStore.registerRecipe(recipe)
        val instanceId = CardRunStore.currentForRecipe(recipe.id)?.instanceId ?: recipe.id
        if (CardRunStore.get(instanceId) != null) {
            CardRunStore.selectSurface(instanceId, surface)?.let { runtimeStates[recipe.id] = it }
        }
        activeRunInstanceIds[recipe.id] = instanceId
        val context = activeResourceInstallWizard
        if (context == null || focusedRunRecipe()?.runtimeSource != RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            showCardRunSurface(recipe)
            return
        }
        val wizardRecipe = resourceInstallWizardRecipeFor(context)
        activeResourceInstallWizard = context.copy(
            selectedResourceId = item.id,
            selectedOperation = operation,
            selectedSurface = surface
        )
        focusedRunRecipeId = wizardRecipe.id
        focusedRunInstanceId = context.wizardInstanceId
        activeRunInstanceIds[wizardRecipe.id] = context.wizardInstanceId
        runtimeStates[wizardRecipe.id] = CardRunStore.update(
            recipe = wizardRecipe,
            status = RecipeRunStatus.Opened,
            instanceId = context.wizardInstanceId,
            surface = surface,
            lastMeaningfulOutput = "查看 ${item.name}"
        )
        showCardRunSurface(wizardRecipe)
    }

    private fun showResourceMoreActions(item: ResourceItem) {
        currentResourceDetailId = item.id
        currentScreen = Screen.ResourceMore
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(topBar("资源管理") { showResourceDetail(item.id) })
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(18), dp(22), dp(34))
                addView(resourceMoreHeader(item))
                addView(resourceCreateHomeCardRow(item))
                addView(resourceInstallHistoryPanel(item))
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun resourceMoreHeader(item: ResourceItem): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            addView(resourceIcon(item.iconText, item.accent).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    setMargins(0, 0, dp(12), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = item.name
                    textSize = 18f
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
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun resourceCreateHomeCardRow(item: ResourceItem): View {
        val canCreate = resourceHomeCardTemplate(item) != null
        return row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            alpha = if (canCreate) 1f else 0.56f
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)).apply {
                setMargins(0, dp(22), 0, 0)
            }
            addView(TextView(context).apply {
                text = "+"
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.primaryStrong)
                background = roundedBox(tokens.primarySubtle, tokens.primarySoft, dp(14).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    setMargins(0, 0, dp(14), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "创建首页卡片"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = if (canCreate) "把这个资源的打开卡片固定到首页" else "这个资源还没有可创建的首页模板"
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "›"
                textSize = 24f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.textTertiary)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(42))
            })
            if (canCreate) setOnClickListener { addResourceHomeCard(item) }
        }
    }

    private fun resourceInstallHistoryPanel(item: ResourceItem): View =
        LinearLayout(this).apply {
            val recipe = resourceInstallRecipe(item)
            val history = recipe?.let { CardRunStore.historyForRecipe(it.id) }.orEmpty()
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, dp(4))
            addView(TextView(context).apply {
                text = "最近获取日志"
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(0, 0, 0, dp(10))
            })
            if (recipe == null || history.isEmpty()) {
                addView(resourceRunHistoryEmptyBlock())
            } else {
                history.forEachIndexed { index, entry ->
                    addView(runHistoryPreviewRow(recipe, entry, index + 1) {
                        showResourceRunHistoryDetail(item, recipe, entry)
                    })
                }
            }
        }

    private fun resourceRunHistoryEmptyBlock(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            addView(TextView(context).apply {
                text = "还没有获取日志"
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "资源获取或失败后，这里会保留对应资源自己的步骤和 SH 报告。"
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(8), 0, 0)
            })
        }

    private fun resourceIsInstalled(item: ResourceItem): Boolean =
        item.stateLabel == "已安装" || item.stateLabel == "已获取"

    private fun resourceItemIsInstalled(item: ResourceItem?): Boolean =
        item?.stateLabel == "已安装" || item?.stateLabel == "已获取"

    private fun resourceOpenRecipe(item: ResourceItem): KiteRecipe? =
        resourceOpenRecipeJson(item)?.let { temporaryResourceRecipe(item, "open", it) }

    private fun temporaryResourceRecipe(item: ResourceItem, action: String, template: JSONObject): KiteRecipe {
        val json = JSONObject(template.toString())
        val base = json.optJSONObject("base") ?: JSONObject().also { json.put("base", it) }
        val runtimeId = "tmp-${KiteResourceInstallRecipes.safeId(item.id)}-$action-${UUID.randomUUID()}"
        base.put("id", runtimeId)
        if (base.optString("name").isBlank()) base.put("name", item.name)
        if (base.optString("description").isBlank()) base.put("description", item.description)
        return KiteRecipe.fromJson(json, runtimeSource = KiteRecipe.SOURCE_USER)
            .copy(id = runtimeId, runtimeSource = RESOURCE_OPEN_RUNTIME_SOURCE)
    }

    private fun startResourceOpen(item: ResourceItem, recipe: KiteRecipe) {
        CardRunStore.registerRecipe(recipe)
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = recipe.id
        activeRunInstanceIds[recipe.id] = recipe.id
        if (shouldOpenCardRunTaskFromHome(recipe)) {
            startActivity(
                CardRunIntents.launchIntent(
                    context = this,
                    recipeId = recipe.id,
                    instanceId = recipe.id,
                    launchSource = CardRunIntents.SOURCE_CARD,
                    autoStart = true
                )
            )
        } else {
            startRecipe(
                recipe,
                runtimeStateFor(recipe),
                preferredInstanceId = recipe.id,
                openConsoleOnStart = recipe.launch.openInstance,
                renderOnStart = recipe.launch.openInstance
            )
        }
        Toast.makeText(this, "正在打开 ${item.name}", Toast.LENGTH_SHORT).show()
    }

    private fun addResourceHomeCard(item: ResourceItem) {
        val template = resourceHomeCardTemplate(item)
        if (template == null) {
            Toast.makeText(this, "${item.name} 暂无首页卡片模板", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            recipeLoader.addSharedRecipeTemplate(template, "${KiteResourceInstallRecipes.safeId(item.id)}-home")
            currentRecipes = recipeLoader.loadAllRecipes()
            refreshRecipeRuntimeStates(currentRecipes)
            resourceSectionsDirty = true
        }.onSuccess {
            Toast.makeText(this, "已添加 ${item.name} 到首页", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "添加失败：${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resourceOpenRecipeJson(item: ResourceItem): JSONObject? =
        resourceManifestLoader.requestOpenRecipeTemplate(item.id)

    private fun resourceHomeCardTemplate(item: ResourceItem): JSONObject? =
        resourceManifestLoader.requestFirstHomeCardRecipeTemplate(item.id)
            ?: resourceManifestLoader.requestOpenRecipeTemplate(item.id)

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
            RESOURCE_REASONIX -> KiteRecipeStep(
                id = "install_reasonix",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.reasonixInstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 600_000L
            )
            RESOURCE_GIT -> KiteRecipeStep(
                id = "install_git",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.gitInstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            RESOURCE_CURL -> KiteRecipeStep(
                id = "install_curl",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.curlInstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            RESOURCE_PYTHON -> KiteRecipeStep(
                id = "install_python",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.pythonInstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 600_000L
            )
            RESOURCE_UV -> KiteRecipeStep(
                id = "install_uv",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.localToolchainCommand(item.id, "--install-uv"),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            RESOURCE_HERMES_CORE -> KiteRecipeStep(
                id = "install_hermes_core",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.hermesCoreInstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 1_800_000L
            )
            else -> null
        } ?: return null
        return KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = item.id,
                name = "${item.name} 获取",
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
            RESOURCE_REASONIX -> KiteRecipeStep(
                id = "uninstall_reasonix",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.reasonixUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 180_000L
            )
            RESOURCE_GIT -> KiteRecipeStep(
                id = "uninstall_git",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.gitUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            RESOURCE_CURL -> KiteRecipeStep(
                id = "uninstall_curl",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.curlUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 300_000L
            )
            RESOURCE_PYTHON -> KiteRecipeStep(
                id = "uninstall_python",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.pythonUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 120_000L
            )
            RESOURCE_UV -> KiteRecipeStep(
                id = "uninstall_uv",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.uvUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 120_000L
            )
            RESOURCE_HERMES_CORE -> KiteRecipeStep(
                id = "uninstall_hermes_core",
                type = KiteRecipe.STEP_SHELL,
                cmd = KiteResourceInstallRecipes.hermesCoreUninstallCommand(),
                surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                workdir = "/workspace",
                timeoutMs = 180_000L
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
        when {
            item.id == RESOURCE_GIT || item.id == RESOURCE_CURL || item.id == RESOURCE_UV -> KiteRecipeIcon.ICON_CODE
            item.category == "AI" -> KiteRecipeIcon.ICON_BOT
            item.category == "Node" || item.category == "Python" -> KiteRecipeIcon.ICON_CODE
            else -> KiteRecipeIcon.ICON_TOOLS
        }

    private fun buildResourceInstallPlan(target: ResourceItem): ResourceInstallPlan {
        val catalogList = resourceCatalog(forceRefresh = true)
        val catalog = catalogList.associateBy { it.id }
        val registeredResourceIds = catalogList
            .filter { resourceIsInstalled(it) }
            .mapTo(linkedSetOf()) { it.id }
        val registeredCapabilities = registeredResourceIds
            .flatMap { resourceId -> resourceManifestLoader.requestManifest(resourceId)?.provides.orEmpty() }
            .toSet()
        val serverPlan = resourceManifestLoader.requestInstallPlan(
            resourceId = target.id,
            registeredResourceIds = registeredResourceIds,
            registeredCapabilities = registeredCapabilities
        )
        if (serverPlan != null) {
            resourceInstallStore.putPageCache(
                cacheKey = KiteResourceRequestPolicy.installPlanKey(target.id),
                payloadJson = serverPlan.rawJson.toString(),
                maxAgeMs = KiteResourceRequestPolicy.INSTALL_PLAN_CACHE_MS
            )
            val steps = serverPlan.resourceIds
                .mapNotNull { resourceId -> catalog[resourceId] }
                .filter { !resourceIsInstalled(it) || it.id == target.id }
            val unknownResources = serverPlan.resourceIds
                .filter { resourceId -> resourceId !in catalog }
                .map { resourceId -> ResourceRequirementResolution(resourceId, null) }
            val missing = (serverPlan.missing.map { item ->
                ResourceRequirementResolution(item.requirement, null)
            } + unknownResources).distinctBy { it.resource?.id ?: it.requirement }
            return ResourceInstallPlan(steps = steps, missing = missing)
        }
        return buildResourceInstallPlanFromRelations(target, catalog)
    }

    private fun buildResourceInstallPlanFromRelations(
        target: ResourceItem,
        catalog: Map<String, ResourceItem>
    ): ResourceInstallPlan {
        val ordered = linkedMapOf<String, ResourceItem>()
        val missing = mutableListOf<ResourceRequirementResolution>()
        val visiting = mutableSetOf<String>()

        fun visit(item: ResourceItem) {
            if (!visiting.add(item.id)) return
            val relations = resourceManifestLoader.requestRelationTargets(item.id)
            (relations.base + relations.defaults).forEach { requirement ->
                val providers = requirement.providerIds.mapNotNull { catalog[it] }
                if (providers.any { resourceIsInstalled(it) }) return@forEach
                val provider = providers.firstOrNull()
                if (provider == null) {
                    missing.add(ResourceRequirementResolution(requirement.requirement, null))
                } else {
                    visit(provider)
                }
            }
            visiting.remove(item.id)
            if (!resourceIsInstalled(item) || item.id == target.id) {
                ordered[item.id] = item
            }
        }

        visit(target)
        return ResourceInstallPlan(steps = ordered.values.toList(), missing = missing)
    }

    private fun startNextResourceInstallFromPlan() {
        val planSnapshot = resourceInstallStore.planSnapshot()
        val runningId = planSnapshot.runningResourceIds.firstOrNull()
        if (runningId != null) {
            if (resourceRunIsActive(runningId)) {
                showResourceInstallWizard()
                return
            }
            val recipe = resourceCatalog(forceRefresh = true)
                .firstOrNull { it.id == runningId }
                ?.let { resourceInstallRecipe(it) }
            if (recipe != null) {
                markResourceInstallFailed(recipe, null, "获取流程异常中断")
            } else {
                resourceInstallStore.markFailed(
                    runningId,
                    KiteResourceInstallStore.OP_INSTALL,
                    null,
                    "获取流程异常中断"
                )
                resourceInstallStore.failPlanAt(runningId)
                invalidateResourceCatalogCache()
            }
            showResourceInstallWizard()
            return
        }
        val pendingIds = planSnapshot.pendingResourceIds
        if (pendingIds.isEmpty()) return
        val catalog = resourceCatalog(forceRefresh = true)
        val next = pendingIds
            .mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            .firstOrNull()
        if (next == null) {
            resourceInstallStore.clearPlan()
            Toast.makeText(this, "获取队列缺少资源定义", Toast.LENGTH_SHORT).show()
            showResourceInstallWizard()
            return
        }
        if (resourceIsInstalled(next)) {
            resourceInstallStore.advancePlanAfter(next.id)
            showResourceInstallWizard()
            startNextResourceInstallFromPlan()
            return
        }
        cacheResourceExecutionManifest(next.id)
        val recipe = resourceInstallRecipe(next)
        if (recipe == null) {
            resourceInstallStore.clearPlan()
            Toast.makeText(this, "${next.name} 的获取脚本尚未接入", Toast.LENGTH_SHORT).show()
            showResourceInstallWizard()
            return
        }
        if (!resourceInstallStore.markPlanStepRunning(next.id)) {
            showResourceInstallWizard()
            return
        }
        if (!resourceInstallWizardSurfaceActive()) {
            Toast.makeText(this, "正在获取：${next.name}", Toast.LENGTH_SHORT).show()
        }
        startResourceInstall(next, recipe)
    }

    private fun cacheResourceExecutionManifest(resourceId: String) {
        val manifestJson = resourceManifestLoader.requestExecutionManifestJson(resourceId) ?: return
        resourceInstallStore.putPageCache(
            cacheKey = KiteResourceRequestPolicy.executionManifestKey(resourceId),
            payloadJson = manifestJson.toString(),
            maxAgeMs = KiteResourceRequestPolicy.EXECUTION_MANIFEST_CACHE_MS
        )
    }

    private fun continueResourceInstallPlanAfter(resourceId: String) {
        val remaining = resourceInstallStore.advancePlanAfter(resourceId)
        if (remaining.isEmpty()) {
            Toast.makeText(this, "资源获取队列完成", Toast.LENGTH_SHORT).show()
            if (currentResourceInstallTargetId != null) showResourceInstallWizard()
            return
        }
        if (currentResourceInstallTargetId != null) showResourceInstallWizard()
        startNextResourceInstallFromPlan()
    }

    private fun startResourceInstall(item: ResourceItem, recipe: KiteRecipe) {
        resourceInstallStore.markInstalling(item.id)
        invalidateResourceCatalogCache()
        startResourceRun(
            item = item,
            recipe = recipe,
            stageBundledResource = item.isBundledResource(),
            openRunTask = !resourceInstallWizardSurfaceActive(),
            returnToInstallWizard = resourceInstallWizardSurfaceActive()
        )
        refreshResourceScreenIfVisible()
    }

    private fun startResourceUninstall(
        item: ResourceItem,
        recipe: KiteRecipe,
        continuation: ResourceUninstallContinuation = ResourceUninstallContinuation.None
    ) {
        if (continuation != ResourceUninstallContinuation.None) {
            pendingResourceUninstallContinuations[item.id] = continuation
        } else {
            pendingResourceUninstallContinuations.remove(item.id)
        }
        resourceInstallStore.markUninstalling(item.id)
        invalidateResourceCatalogCache()
        startResourceRun(item, recipe, stageBundledResource = false, openRunTask = false)
        refreshResourceScreenIfVisible()
    }

    private fun startResourceRun(
        item: ResourceItem,
        recipe: KiteRecipe,
        stageBundledResource: Boolean,
        openRunTask: Boolean = true,
        returnToInstallWizard: Boolean = false
    ) {
        val parentInstanceId = activeResourceInstallWizard
            ?.wizardInstanceId
            ?.takeIf { returnToInstallWizard }
        val instanceId = resourceRunInstanceId(item.id, recipe)
        CardRunStore.registerRecipe(recipe)
        activeRunInstanceIds[recipe.id] = instanceId
        if (openRunTask && !returnToInstallWizard) {
            suppressedResourceRunSurfaceRecipeIds.remove(recipe.id)
        } else {
            suppressedResourceRunSurfaceRecipeIds.add(recipe.id)
        }
        runtimeStates[recipe.id] = CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            parentInstanceId = parentInstanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_RESOURCE,
            stepId = item.id
        )
        if (openRunTask && !returnToInstallWizard) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
        }
        setRuntimeState(
            recipe,
            RecipeRunStatus.Starting,
            surface = CardRunSurface.Report,
            currentStepIndex = 0,
            lastMeaningfulOutput = "正在准备资源：${item.name}",
            shellReportText = "资源：${item.name}\n来源：${item.sourceLabel}\n结果：正在准备资源"
        )
        if (this is CardRunActivity && openRunTask && !returnToInstallWizard) {
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
                    if (this is CardRunActivity && (openRunTask || returnToInstallWizard)) {
                        startRecipe(
                            recipe,
                            runtimeStateFor(recipe),
                            instanceId,
                            openConsoleOnStart = false,
                            renderOnStart = openRunTask && !returnToInstallWizard,
                            keepCurrentFocus = returnToInstallWizard
                        )
                        if (returnToInstallWizard) {
                            showResourceInstallWizard()
                        }
                    } else if (!openRunTask) {
                        startRecipe(
                            recipe,
                            runtimeStateFor(recipe),
                            instanceId,
                            openConsoleOnStart = false,
                            renderOnStart = false,
                            keepCurrentFocus = true
                        )
                        if (returnToInstallWizard) {
                            showResourceInstallWizard()
                        } else {
                            refreshResourceScreenIfVisible()
                        }
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
                    if (returnToInstallWizard) {
                        showResourceInstallWizard()
                    } else if (this is CardRunActivity && openRunTask) {
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
        invalidateResourceCatalogCache()
        when (currentScreen) {
            Screen.Resources -> {
                if (resourceSectionHost != null) {
                    requestResourceSectionsRefresh(forceCatalogRefresh = true)
                } else {
                    showResources()
                }
            }
            Screen.ResourceDetail -> currentResourceDetailId?.let { showResourceDetail(it) }
            Screen.ResourceMore -> currentResourceDetailId?.let { resourceId ->
                resourceCatalogForUiRender("resource_more_refresh")
                    .firstOrNull { it.id == resourceId }
                    ?.let { showResourceMoreActions(it) }
            }
            Screen.ResourceManage -> {
                if (resourceManageContentHost != null) {
                    requestResourceManageRefresh(forceCatalogRefresh = true, reason = "visible_refresh")
                } else {
                    showResourceManage()
                }
            }
            else -> Unit
        }
    }

    private fun ResourceItem.isBundledResource(): Boolean =
        id == RESOURCE_NODE_RUNTIME || id == RESOURCE_KF_TOOL_ENV || id == RESOURCE_UV

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
            KiteResourceInstallRecipes.OP_UNINSTALL -> resourceIdForRecipe(recipe)?.let { resourceId ->
                val continuation = pendingResourceUninstallContinuations.remove(resourceId)
                    ?: ResourceUninstallContinuation.None
                resourceInstallStore.clear(resourceId)
                invalidateResourceCatalogCache()
                when (continuation) {
                    ResourceUninstallContinuation.Reinstall -> {
                        val item = resourceCatalog(forceRefresh = true).firstOrNull { it.id == resourceId }
                        if (item == null) {
                            Toast.makeText(this, "卸载完成，但获取目标缺少资源定义", Toast.LENGTH_SHORT).show()
                            refreshResourceScreenIfVisible()
                        } else {
                            Toast.makeText(this, "${item.name} 残留已卸载，继续获取", Toast.LENGTH_SHORT).show()
                            handleResourceInstallAction(item)
                        }
                    }
                    ResourceUninstallContinuation.CancelFailedInstall -> {
                        resourceInstallStore.clearPlan()
                        currentResourceInstallTargetId = null
                        resourceInstallWizardPlanIds = emptyList()
                        activeResourceInstallWizard = null
                        Toast.makeText(this, "残留已卸载，获取任务已取消", Toast.LENGTH_SHORT).show()
                        if (this is CardRunActivity && currentScreen == Screen.CardRun) {
                            closeCardRunTask()
                        } else {
                            refreshResourceScreenIfVisible()
                        }
                    }
                    ResourceUninstallContinuation.ResumeInstallWizard -> {
                        resourceInstallStore.resumePlanFrom(resourceId)
                        Toast.makeText(this, "已卸载异常资源，可继续当前获取队列", Toast.LENGTH_SHORT).show()
                        refreshResourceScreenIfVisible()
                    }
                    ResourceUninstallContinuation.None -> {
                        refreshResourceScreenIfVisible()
                    }
                }
            }
        }
    }

    private fun markResourceInstallSuccess(recipe: KiteRecipe, runId: String?, summary: String?) {
        val resourceId = resourceIdForRecipe(recipe) ?: return
        val item = resourceCatalog(forceRefresh = true).firstOrNull { it.id == resourceId }
        val version = item?.version.orEmpty()
        resourceInstallStore.markInstalled(resourceId, version, runId, summary)
        saveInstalledResourceSnapshot(resourceId, item, version)
        invalidateResourceCatalogCache()
        continueResourceInstallPlanAfter(resourceId)
    }

    private fun saveInstalledResourceSnapshot(resourceId: String, item: ResourceItem?, version: String) {
        val manifest = resourceManifestLoader.requestManifest(resourceId)
        val iconJson = JSONObject().apply {
            put("type", "text")
            put("value", manifest?.iconText?.ifBlank { item?.iconText.orEmpty() } ?: item?.iconText.orEmpty())
        }.toString()
        val manifestJson = resourceManifestLoader.requestExecutionManifestJson(resourceId)?.toString().orEmpty()
        resourceInstallStore.saveInstalledSnapshot(
            resourceId = resourceId,
            name = manifest?.name?.ifBlank { item?.name.orEmpty() } ?: item?.name.orEmpty(),
            iconJson = iconJson,
            version = manifest?.version?.ifBlank { version } ?: version,
            manifestJson = manifestJson
        )
    }

    private fun markResourceInstallFailed(recipe: KiteRecipe, runId: String?, reason: String?) {
        val resourceId = resourceIdForRecipe(recipe) ?: return
        val operation = resourceOperationForRecipe(recipe) ?: KiteResourceInstallStore.OP_INSTALL
        resourceInstallStore.markFailed(resourceId, operation, runId, reason)
        if (operation == KiteResourceInstallStore.OP_INSTALL) {
            resourceInstallStore.failPlanAt(resourceId)
        }
        invalidateResourceCatalogCache()
    }

    private fun normalizeStaleResourceState(
        resourceId: String,
        registryEntry: KiteResourceRegistryEntry? = resourceInstallStore.registryEntry(resourceId)
    ): Boolean {
        val operation = when {
            registryEntry?.installing == true -> KiteResourceInstallStore.OP_INSTALL
            registryEntry?.uninstalling == true -> KiteResourceInstallStore.OP_UNINSTALL
            else -> return false
        }
        val planStepStatus = resourceInstallStore.planStepStatus(resourceId)
        if (
            operation == KiteResourceInstallStore.OP_INSTALL &&
            planStepStatus.isNotBlank() &&
            planStepStatus != KiteResourceInstallStore.PLAN_STEP_RUNNING
        ) {
            resourceInstallStore.clear(resourceId)
            return true
        }
        val recipeId = KiteResourceInstallRecipes.recipeId(resourceId, operation)
        val run = CardRunStore.currentForRecipe(recipeId)
        val stillRunning = run?.isBusy() == true || run?.isActive() == true
        if (!stillRunning) {
            val reason = if (operation == KiteResourceInstallStore.OP_UNINSTALL) {
                "卸载流程异常中断"
            } else {
                "获取流程异常中断"
            }
            resourceInstallStore.markFailed(resourceId, operation, run?.runId, reason)
            if (
                operation == KiteResourceInstallStore.OP_INSTALL &&
                planStepStatus == KiteResourceInstallStore.PLAN_STEP_RUNNING
            ) {
                resourceInstallStore.failPlanAt(resourceId)
            }
            return true
        }
        return false
    }

    private fun resourceCatalog(forceRefresh: Boolean = false): List<ResourceItem> {
        val now = System.currentTimeMillis()
        val onMainThread = Looper.myLooper() == Looper.getMainLooper()
        cachedResourceCatalog?.let { cached ->
            val canReuseCleanCatalog = !resourceCatalogDirty &&
                (!forceRefresh || (onMainThread && now - cachedResourceCatalogUpdatedAt < RESOURCE_CATALOG_FORCE_REFRESH_GRACE_MS))
            if (canReuseCleanCatalog) return cached
        }
        val allowWorkspaceProbe = !onMainThread
        if (allowWorkspaceProbe) {
            ToolchainPackInstaller.refreshState(applicationContext)
        }
        val managedResourceIds = listOf(
            RESOURCE_NODE_RUNTIME,
            RESOURCE_HERMES_CORE,
            RESOURCE_HERMES_WEBUI,
            RESOURCE_REASONIX,
            RESOURCE_GIT,
            RESOURCE_CURL,
            RESOURCE_PYTHON,
            RESOURCE_UV
        )
        var registrySnapshot = resourceInstallStore.registrySnapshot(managedResourceIds)
        val normalizedAny = managedResourceIds.any { normalizeStaleResourceState(it, registrySnapshot[it]) }
        if (normalizedAny) {
            registrySnapshot = resourceInstallStore.registrySnapshot(managedResourceIds)
        }
        fun recordedInstalled(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.installed == true
        fun installFailed(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.failed == true
        fun busy(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.busy == true
        fun installing(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.installing == true
        fun uninstalling(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.uninstalling == true
        fun failedOperation(resourceId: String): String =
            registrySnapshot[resourceId]?.operation.orEmpty()
        fun actionLabelForResource(
            installed: Boolean,
            installing: Boolean,
            uninstalling: Boolean,
            failed: Boolean,
            failedOperation: String
        ): String = when {
            installing -> "获取中"
            uninstalling -> "卸载中"
            installed -> "打开"
            failed && failedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "继续卸载"
            failed -> "重新获取"
            else -> "获取"
        }
        fun stateLabelForResource(
            installed: Boolean,
            installing: Boolean,
            uninstalling: Boolean,
            failed: Boolean,
            failedOperation: String,
            idleLabel: String
        ): String = when {
            installing -> "获取中"
            uninstalling -> "卸载中"
            installed -> "已获取"
            failed && failedOperation == KiteResourceInstallStore.OP_UNINSTALL -> "卸载失败"
            failed -> "获取失败"
            else -> idleLabel
        }
        val toolchain = ToolchainPackInstaller.state.value
        val workspaceSnapshot = toolchainWorkspaceSnapshot(allowProbe = allowWorkspaceProbe)
        val nodeWorkspaceInstalled = workspaceSnapshot.nodeInstalled
        fun clearInstalledIfWorkspaceMissing(resourceId: String, workspaceInstalled: Boolean): Boolean {
            if (!allowWorkspaceProbe || !recordedInstalled(resourceId) || workspaceInstalled) return false
            resourceInstallStore.clear(resourceId)
            return true
        }
        val clearedNodeWorkspaceState =
            clearInstalledIfWorkspaceMissing(RESOURCE_NODE_RUNTIME, nodeWorkspaceInstalled)
        val workspaceStateNormalized = clearedNodeWorkspaceState
        if (workspaceStateNormalized) {
            registrySnapshot = resourceInstallStore.registrySnapshot(managedResourceIds)
        }
        val nodeRecordedInstalled = recordedInstalled(RESOURCE_NODE_RUNTIME)
        val nodeInstallFailed = installFailed(RESOURCE_NODE_RUNTIME)
        val nodeBusy = busy(RESOURCE_NODE_RUNTIME)
        val nodeInstalling = installing(RESOURCE_NODE_RUNTIME)
        val nodeUninstalling = uninstalling(RESOURCE_NODE_RUNTIME)
        val nodeFailedOperation = failedOperation(RESOURCE_NODE_RUNTIME)
        val hermesRecordedInstalled = recordedInstalled(RESOURCE_HERMES_WEBUI)
        val hermesInstallFailed = installFailed(RESOURCE_HERMES_WEBUI)
        val hermesBusy = busy(RESOURCE_HERMES_WEBUI)
        val hermesInstalling = installing(RESOURCE_HERMES_WEBUI)
        val hermesUninstalling = uninstalling(RESOURCE_HERMES_WEBUI)
        val hermesFailedOperation = failedOperation(RESOURCE_HERMES_WEBUI)
        val hermesCoreRecordedInstalled = recordedInstalled(RESOURCE_HERMES_CORE)
        val hermesCoreInstallFailed = installFailed(RESOURCE_HERMES_CORE)
        val hermesCoreBusy = busy(RESOURCE_HERMES_CORE)
        val hermesCoreInstalling = installing(RESOURCE_HERMES_CORE)
        val hermesCoreUninstalling = uninstalling(RESOURCE_HERMES_CORE)
        val hermesCoreFailedOperation = failedOperation(RESOURCE_HERMES_CORE)
        val reasonixRecordedInstalled = recordedInstalled(RESOURCE_REASONIX)
        val reasonixInstallFailed = installFailed(RESOURCE_REASONIX)
        val reasonixBusy = busy(RESOURCE_REASONIX)
        val reasonixInstalling = installing(RESOURCE_REASONIX)
        val reasonixUninstalling = uninstalling(RESOURCE_REASONIX)
        val reasonixFailedOperation = failedOperation(RESOURCE_REASONIX)
        val gitRecordedInstalled = recordedInstalled(RESOURCE_GIT)
        val gitInstallFailed = installFailed(RESOURCE_GIT)
        val gitBusy = busy(RESOURCE_GIT)
        val gitInstalling = installing(RESOURCE_GIT)
        val gitUninstalling = uninstalling(RESOURCE_GIT)
        val gitFailedOperation = failedOperation(RESOURCE_GIT)
        val curlRecordedInstalled = recordedInstalled(RESOURCE_CURL)
        val curlInstallFailed = installFailed(RESOURCE_CURL)
        val curlBusy = busy(RESOURCE_CURL)
        val curlInstalling = installing(RESOURCE_CURL)
        val curlUninstalling = uninstalling(RESOURCE_CURL)
        val curlFailedOperation = failedOperation(RESOURCE_CURL)
        val pythonRecordedInstalled = recordedInstalled(RESOURCE_PYTHON)
        val pythonInstallFailed = installFailed(RESOURCE_PYTHON)
        val pythonBusy = busy(RESOURCE_PYTHON)
        val pythonInstalling = installing(RESOURCE_PYTHON)
        val pythonUninstalling = uninstalling(RESOURCE_PYTHON)
        val pythonFailedOperation = failedOperation(RESOURCE_PYTHON)
        val uvRecordedInstalled = recordedInstalled(RESOURCE_UV)
        val uvInstallFailed = installFailed(RESOURCE_UV)
        val uvBusy = busy(RESOURCE_UV)
        val uvInstalling = installing(RESOURCE_UV)
        val uvUninstalling = uninstalling(RESOURCE_UV)
        val uvFailedOperation = failedOperation(RESOURCE_UV)
        val nodeInstalled = if (allowWorkspaceProbe) {
            nodeWorkspaceInstalled
        } else {
            nodeWorkspaceInstalled || nodeRecordedInstalled
        }
        val toolchainRunning = toolchain.phase == ToolchainInstallPhase.RUNNING
        val nodeAction = actionLabelForResource(
            nodeInstalled,
            toolchainRunning || nodeInstalling,
            nodeUninstalling,
            nodeInstallFailed,
            nodeFailedOperation
        )
        val nodeState = stateLabelForResource(
            nodeInstalled,
            toolchainRunning || nodeInstalling,
            nodeUninstalling,
            nodeInstallFailed,
            nodeFailedOperation,
            "本地包"
        )
        val hermesAction = actionLabelForResource(
            hermesRecordedInstalled,
            hermesInstalling,
            hermesUninstalling,
            hermesInstallFailed,
            hermesFailedOperation
        )
        val hermesState = stateLabelForResource(
            hermesRecordedInstalled,
            hermesInstalling,
            hermesUninstalling,
            hermesInstallFailed,
            hermesFailedOperation,
            "未获取"
        )
        val hermesCoreAction = actionLabelForResource(
            hermesCoreRecordedInstalled,
            hermesCoreInstalling,
            hermesCoreUninstalling,
            hermesCoreInstallFailed,
            hermesCoreFailedOperation
        )
        val hermesCoreState = stateLabelForResource(
            hermesCoreRecordedInstalled,
            hermesCoreInstalling,
            hermesCoreUninstalling,
            hermesCoreInstallFailed,
            hermesCoreFailedOperation,
            "未获取"
        )
        val reasonixAction = actionLabelForResource(
            reasonixRecordedInstalled,
            reasonixInstalling,
            reasonixUninstalling,
            reasonixInstallFailed,
            reasonixFailedOperation
        )
        val reasonixState = stateLabelForResource(
            reasonixRecordedInstalled,
            reasonixInstalling,
            reasonixUninstalling,
            reasonixInstallFailed,
            reasonixFailedOperation,
            "未获取"
        )
        val gitAction = actionLabelForResource(
            gitRecordedInstalled,
            gitInstalling,
            gitUninstalling,
            gitInstallFailed,
            gitFailedOperation
        )
        val gitState = stateLabelForResource(
            gitRecordedInstalled,
            gitInstalling,
            gitUninstalling,
            gitInstallFailed,
            gitFailedOperation,
            "未获取"
        )
        val curlAction = actionLabelForResource(
            curlRecordedInstalled,
            curlInstalling,
            curlUninstalling,
            curlInstallFailed,
            curlFailedOperation
        )
        val curlState = stateLabelForResource(
            curlRecordedInstalled,
            curlInstalling,
            curlUninstalling,
            curlInstallFailed,
            curlFailedOperation,
            "未获取"
        )
        val pythonAction = actionLabelForResource(
            pythonRecordedInstalled,
            pythonInstalling,
            pythonUninstalling,
            pythonInstallFailed,
            pythonFailedOperation
        )
        val pythonState = stateLabelForResource(
            pythonRecordedInstalled,
            pythonInstalling,
            pythonUninstalling,
            pythonInstallFailed,
            pythonFailedOperation,
            "未获取"
        )
        val uvAction = actionLabelForResource(
            uvRecordedInstalled,
            uvInstalling,
            uvUninstalling,
            uvInstallFailed,
            uvFailedOperation
        )
        val uvState = stateLabelForResource(
            uvRecordedInstalled,
            uvInstalling,
            uvUninstalling,
            uvInstallFailed,
            uvFailedOperation,
            "本地包"
        )
        val catalog = listOf(
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
                actionEnabled = resourceActionEnabled(nodeAction, toolchainRunning || nodeBusy),
                includes = listOf("node 24.15.0", "npm", "npx", "PATH wrapper"),
                notes = listOf("安装位置是 ${KiteResourceInstallRecipes.softwarePath(RESOURCE_NODE_RUNTIME)}/node-v24.15.0", "命令入口是 /workspace/.kf/bin", "重新安装会先清理自己的注册名目录"),
                steps = listOf(
                    ResourceStep("shell", "解压 Node.js", "extract node-v24.15.0-linux-arm64.tar.xz"),
                    ResourceStep("shell", "暴露命令", "link node/npm/npx -> /workspace/.kf/bin"),
                    ResourceStep("shell", "验证版本", "node --version && npm --version")
                )
            ),
            ResourceItem(
                id = RESOURCE_GIT,
                name = "Git",
                description = "源码下载与版本管理工具",
                longDescription = "Git 是 Hermes 本体和后续源码型资源的底层工具卡。安装时先检查当前 Ubuntu 环境是否已有 git；已有则只登记为可用，没有则通过 apt 安装 git 与 ca-certificates，并记录这次安装的 ownership。",
                section = "更多资源",
                category = "系统工具",
                iconText = "Git",
                accent = "orange",
                version = "apt",
                sizeLabel = "网络包",
                sourceLabel = "apt",
                stateLabel = gitState,
                actionLabel = gitAction,
                actionEnabled = resourceActionEnabled(gitAction, gitBusy),
                includes = listOf("git CLI", "ca-certificates", "tool.git 能力", "安装 ownership 标记"),
                notes = listOf("基础层能力：tool.git", "如果 Git 原本已存在，卸载只清 Kite 登记", "Hermes Core 后续会引用这张卡"),
                steps = listOf(
                    ResourceStep("shell", "检查 Git", "command -v git && git --version"),
                    ResourceStep("shell", "安装 Git", "apt-get install -y git ca-certificates"),
                    ResourceStep("shell", "登记能力", "provides tool.git")
                )
            ),
            ResourceItem(
                id = RESOURCE_PYTHON,
                name = "Python",
                description = "Hermes 本体需要的 Python 运行时",
                longDescription = "Python 是 Hermes Core 的基础层资源。Hermes 当前声明 Python >=3.11,<3.14；这张卡负责补齐 python3、venv、pip 和证书能力，并把可用状态登记到 Kite。卸载时不移除系统 Python，只清除 Kite 的资源登记。",
                section = "更多资源",
                category = "Python",
                iconText = "Py",
                accent = "blue",
                version = ">=3.11,<3.14",
                sizeLabel = "网络包",
                sourceLabel = "apt",
                stateLabel = pythonState,
                actionLabel = pythonAction,
                actionEnabled = resourceActionEnabled(pythonAction, pythonBusy),
                includes = listOf("python3", "venv", "pip", "runtime.python>=3.11<3.14"),
                notes = listOf("基础层能力：runtime.python>=3.11<3.14", "卸载只清 Kite 登记，不删除系统 Python", "Hermes Core 后续会引用这张卡"),
                steps = listOf(
                    ResourceStep("shell", "校验版本", "python3 >=3.11 and <3.14"),
                    ResourceStep("shell", "补齐 Python", "apt-get install -y python3 python3-venv python3-pip"),
                    ResourceStep("shell", "验证 venv", "python3 -m venv --help")
                )
            ),
            ResourceItem(
                id = RESOURCE_CURL,
                name = "curl",
                description = "网络下载命令",
                longDescription = "curl 是网络型资源下载安装脚本的基础层工具。Hermes Core 需要用它下载官方 install.sh；安装时先检查当前 Ubuntu 环境是否已有 curl，已有则只登记为可用，没有则通过 apt 安装 curl 与 ca-certificates。",
                section = "更多资源",
                category = "系统工具",
                iconText = "curl",
                accent = "orange",
                version = "apt",
                sizeLabel = "网络包",
                sourceLabel = "apt",
                stateLabel = curlState,
                actionLabel = curlAction,
                actionEnabled = resourceActionEnabled(curlAction, curlBusy),
                includes = listOf("curl CLI", "ca-certificates", "tool.curl 能力", "安装 ownership 标记"),
                notes = listOf("基础层能力：tool.curl", "如果 curl 原本已存在，卸载只清 Kite 登记", "Hermes Core 后续会引用这张卡"),
                steps = listOf(
                    ResourceStep("shell", "检查 curl", "command -v curl && curl --version"),
                    ResourceStep("shell", "安装 curl", "apt-get install -y curl ca-certificates"),
                    ResourceStep("shell", "登记能力", "provides tool.curl")
                )
            ),
            ResourceItem(
                id = RESOURCE_UV,
                name = "uv",
                description = "高速 Python 包管理器",
                longDescription = "uv 是 Hermes 安装 Python 依赖和管理虚拟环境的基础工具卡。Kite 使用内置 ai-dev-pack 中的 uv 0.11.1，只安装 uv/uvx 到 /workspace/.kf/bin，不顺带安装完整工具合集。",
                section = "更多资源",
                category = "Python",
                iconText = "uv",
                accent = "green",
                version = "0.11.1",
                sizeLabel = "内置包",
                sourceLabel = "内置",
                stateLabel = uvState,
                actionLabel = uvAction,
                actionEnabled = resourceActionEnabled(uvAction, uvBusy),
                includes = listOf("uv 0.11.1", "uvx", "tool.uv", "tool.uvx"),
                notes = listOf("基础层能力：tool.uv", "从内置包安装，不需要先联网下载 uv", "Hermes Core 后续会引用这张卡"),
                steps = listOf(
                    ResourceStep("shell", "复制内置资源包", "mirror assets/toolchain/ai-dev-pack -> ${KiteResourceInstallRecipes.localPackPath(RESOURCE_UV)}"),
                    ResourceStep("shell", "安装 uv", "bash ${KiteResourceInstallRecipes.localPackPath(RESOURCE_UV)}/install.sh --install-uv"),
                    ResourceStep("shell", "验证版本", "uv --version && uvx --version")
                )
            ),
            ResourceItem(
                id = RESOURCE_HERMES_CORE,
                name = "Hermes Core",
                description = "Hermes Agent 本体与 CLI",
                longDescription = "Hermes Core 是 Hermes Agent 的本体组件。Kite 调用官方 install.sh，但用 --dir 和 --hermes-home 把代码、venv、数据目录固定到这张资源自己的软件区，并暴露 /workspace/.kf/bin/hermes。它只负责本体安装和卸载；面向普通人的配置网页、WebUI 和首页启动卡会作为上层方案或配套卡组织。",
                section = "精选推荐",
                category = "AI",
                iconText = "H",
                accent = "teal",
                version = "main",
                sizeLabel = "网络包",
                sourceLabel = "官方脚本",
                stateLabel = hermesCoreState,
                actionLabel = hermesCoreAction,
                actionEnabled = resourceActionEnabled(hermesCoreAction, hermesCoreBusy),
                includes = listOf("官方 install.sh", "hermes CLI", "独立 venv", "Kite 管理的 HERMES_HOME", "service.hermes 能力"),
                notes = listOf("基础层：Git", "本体卡不直接写首页启动卡片", "配置网页和 WebUI 应由上层方案卡或配套卡关联"),
                steps = listOf(
                    ResourceStep("shell", "下载官方安装器", "curl -fsSL https://hermes-agent.nousresearch.com/install.sh"),
                    ResourceStep("shell", "执行官方安装", "bash install.sh --dir ... --hermes-home ... --skip-setup"),
                    ResourceStep("shell", "暴露命令", "link hermes -> /workspace/.kf/bin")
                )
            ),
            ResourceItem(
                id = RESOURCE_HERMES_WEBUI,
                name = "Hermes WebUI",
                description = "Hermes 的网页工作台",
                longDescription = "用于在浏览器界面里使用 Hermes 的轻量 Web UI。它是 Hermes Core 的上层界面资源，安装时先检查 hermes 与 npm 是否存在；后续配置完成后再写回首页启动卡片。",
                section = "精选推荐",
                category = "AI",
                iconText = "H",
                accent = "mint",
                version = "npm",
                sizeLabel = "网络包",
                sourceLabel = "网络",
                stateLabel = hermesState,
                actionLabel = hermesAction,
                actionEnabled = resourceActionEnabled(hermesAction, hermesBusy),
                includes = listOf("hermes-web-ui npm 包", "启动端口 8648", "首页启动卡片"),
                notes = listOf("基础层：Hermes Core、Node.js", "首次启动需要 Hermes 已完成模型配置"),
                steps = listOf(
                    ResourceStep("shell", "安装 npm 包", "npm install -g hermes-web-ui"),
                    ResourceStep("shell", "启动服务", "hermes-web-ui start --port 8648"),
                    ResourceStep("open_web", "打开网页", "http://127.0.0.1:8648")
                )
            ),
            ResourceItem(
                id = RESOURCE_REASONIX,
                name = "Reasonix",
                description = "DeepSeek 原生终端编码助手",
                longDescription = "Reasonix 是围绕 DeepSeek prefix-cache 稳定性设计的终端编码 Agent。Kite 把它作为 Node.js 上层资源：安装阶段只通过 npm 装 CLI 并暴露 reasonix/dsnix 命令；创建首页卡片后，用户从终端表面启动交互式 Reasonix，不把 API Key 或运行输出写回资源卡。",
                section = "精选推荐",
                category = "AI",
                iconText = "Rx",
                accent = "teal",
                version = "npm",
                sizeLabel = "网络包",
                sourceLabel = "npm",
                stateLabel = reasonixState,
                actionLabel = reasonixAction,
                actionEnabled = resourceActionEnabled(reasonixAction, reasonixBusy),
                includes = listOf("reasonix CLI", "dsnix alias", "DeepSeek API", "终端首页卡片"),
                notes = listOf("基础层：Node.js", "需要用户自行配置 DEEPSEEK_API_KEY", "运行态输出仍归首页卡/终端/SH 报告车道"),
                steps = listOf(
                    ResourceStep("shell", "安装 npm 包", "npm install -g reasonix"),
                    ResourceStep("shell", "暴露命令", "link reasonix/dsnix -> /workspace/.kf/bin"),
                    ResourceStep("terminal", "打开终端", "reasonix")
                )
            )
        ).map { applyResourceManifest(it) }
        cachedResourceCatalog = catalog
        cachedResourceCatalogUpdatedAt = now
        resourceCatalogDirty = false
        return catalog
    }

    private fun toolchainWorkspaceSnapshot(allowProbe: Boolean): ToolchainWorkspaceSnapshot {
        val now = System.currentTimeMillis()
        val cached = cachedToolchainWorkspaceSnapshot
        if (!allowProbe || now - cached.checkedAt < TOOLCHAIN_WORKSPACE_PROBE_TTL_MS) {
            return cached
        }
        return ToolchainWorkspaceSnapshot(
            nodeInstalled = ToolchainPackInstaller.isNodeRuntimeInstalled(applicationContext),
            toolchainInstalled = ToolchainPackInstaller.isToolchainPackInstalled(applicationContext),
            checkedAt = now
        ).also {
            cachedToolchainWorkspaceSnapshot = it
        }
    }

    private fun applyResourceManifest(item: ResourceItem): ResourceItem {
        val manifest = resourceManifestLoader.requestManifest(item.id) ?: return item
        return item.copy(
            name = manifest.name.ifBlank { item.name },
            description = manifest.description.ifBlank { item.description },
            section = resourceSectionLabel(manifest.sections.firstOrNull()).ifBlank { item.section },
            iconText = manifest.iconText.ifBlank { item.iconText },
            version = manifest.version.ifBlank { item.version },
            sourceLabel = resourceSourceLabel(manifest.sourceType).ifBlank { item.sourceLabel },
            includes = mergeResourceStrings(item.includes, manifest.provides),
            notes = mergeResourceStrings(item.notes, resourceRelationNotes(manifest)),
            rawJson = runCatching { manifest.rawJson.toString(2) }.getOrElse { manifest.rawJson.toString() }
        )
    }

    private fun resourceSectionLabel(section: String?): String =
        when (section) {
            "featured" -> "精选推荐"
            "quick" -> "快速开始"
            "more" -> "更多资源"
            else -> ""
        }

    private fun resourceSourceLabel(type: String): String =
        when (type) {
            "bundled" -> "内置"
            "apt" -> "apt"
            "official_script" -> "官方脚本"
            "npm" -> "npm"
            "command" -> "网络"
            else -> ""
        }

    private fun resourceRelationNotes(manifest: KiteResourceManifest): List<String> =
        buildList {
            if (manifest.baseRequirements.isNotEmpty()) add("基础层：${manifest.baseRequirements.joinToString("、")}")
            if (manifest.defaultRequirements.isNotEmpty()) add("默认层：${manifest.defaultRequirements.joinToString("、")}")
            if (manifest.extensions.isNotEmpty()) add("扩展层：${manifest.extensions.joinToString("、")}")
        }

    private fun mergeResourceStrings(primary: List<String>, extra: List<String>): List<String> =
        (primary + extra).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun missingBaseRequirements(item: ResourceItem): List<ResourceRequirementResolution> {
        val baseTargets = resourceManifestLoader.requestRelationTargets(item.id).base
        if (baseTargets.isEmpty()) return emptyList()
        val catalog = resourceCatalog(forceRefresh = true)
        return baseTargets
            .mapNotNull { target -> unresolvedBaseRequirement(target.requirement, target.providerIds, catalog) }
            .distinctBy { it.resource?.id ?: it.requirement }
    }

    private fun unresolvedBaseRequirement(
        requirement: String,
        providerIds: List<String>,
        catalog: List<ResourceItem>
    ): ResourceRequirementResolution? {
        val providers = providerIds.mapNotNull { providerId -> catalog.firstOrNull { it.id == providerId } }
        if (providers.any { resourceIsInstalled(it) }) return null
        return ResourceRequirementResolution(requirement = requirement, resource = providers.firstOrNull())
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
        root.setBackgroundColor(Color.rgb(246, 247, 249))
        clearRootForScreen()
        val state = focusedRunInstanceId
            ?.let { CardRunStore.get(it) }
            ?: runtimeStateFor(recipe)
        val wizardChildRun = resourceInstallWizardSelectedRun(recipe, state.surface)
        val surfaceState = wizardChildRun?.second ?: state
        root.addView(cardRunTopBar(
            recipe = recipe,
            state = state,
            actionRecipe = wizardChildRun?.first ?: recipe,
            actionState = surfaceState
        ))
        val terminalSessionId = surfaceState.terminalSessionId?.takeIf { it.isNotBlank() }
        val webUrl = surfaceState.nextActionUrl?.takeIf { it.isNotBlank() }
        if (state.surface == CardRunSurface.Terminal && terminalSessionId != null) {
            applyKiteTerminalTheme()
            root.addView(FrameLayout(this).apply {
                id = cardRunTerminalContainerId
                setBackgroundColor(tokens.pageBackground)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            showCardRunTerminalFragment(terminalSessionId)
        } else if (state.surface == CardRunSurface.Terminal) {
            root.addView(cardRunLoadingBody("正在准备终端"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else if (state.surface == CardRunSurface.Web && webUrl != null) {
            showCardRunWebView(wizardChildRun?.first ?: recipe, webUrl)
        } else if (state.surface == CardRunSurface.InstallWizard) {
            root.addView(ScrollView(this).apply {
                addView(resourceInstallWizardContent())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else if (wizardChildRun != null) {
            root.addView(ScrollView(this).apply {
                addView(cardRunContent(wizardChildRun.first, wizardChildRun.second))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            root.addView(ScrollView(this).apply {
                addView(cardRunContent(recipe, state))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun showCardRunLoadingSurface(recipe: KiteRecipe, message: String) {
        currentScreen = Screen.CardRun
        root.setBackgroundColor(Color.rgb(246, 247, 249))
        clearRootForScreen()
        root.addView(cardRunTopBar(recipe, runtimeStateFor(recipe)))
        root.addView(cardRunLoadingBody(message), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun cardRunLoadingBody(message: String): View =
        FrameLayout(this).apply {
            setBackgroundColor(tokens.pageBackground)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(28), dp(24), dp(28))
                addView(ProgressBar(context).apply {
                    isIndeterminate = true
                    layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                })
                addView(TextView(context).apply {
                    text = message
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, dp(18), 0, 0)
                })
                addView(TextView(context).apply {
                    text = "正在连接运行环境，请稍候"
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, dp(8), 0, 0)
                })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

    private fun showCardRunTerminalFragment(sessionId: String) {
        val fragment = TerminalFragment.detailOnly(sessionId)
        supportFragmentManager.beginTransaction()
            .replace(cardRunTerminalContainerId, fragment, CARD_RUN_TERMINAL_FRAGMENT_TAG)
            .commitNowAllowingStateLoss()
    }

    private fun cardRunTopBar(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        actionRecipe: KiteRecipe = recipe,
        actionState: RecipeRuntimeState = state
    ): View = FrameLayout(this).apply {
        val waitingForTerminal = actionState.status == RecipeRunStatus.WaitingTerminal && !actionState.terminalSessionId.isNullOrBlank()
        val canCompleteCurrentStep = canCompleteCurrentCardStep(actionRecipe, actionState)
        val sideControlSize = dp(44)
        setPadding(dp(16), dp(12), dp(16), dp(8))
        val leftControl = if (canCompleteCurrentStep) {
            cardRunDoneButton { completeCurrentCardStep(actionRecipe, actionState) }
        } else {
            iconButton("‹", sideControlSize, Color.TRANSPARENT, tokens.textPrimary, dp(18)) { closeCardRunTask() }
        }
        addView(leftControl, FrameLayout.LayoutParams(sideControlSize, sideControlSize, Gravity.LEFT or Gravity.CENTER_VERTICAL))
        addView(TextView(context).apply {
            text = cardRunSurfaceTitle(state)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sideControlSize, Gravity.CENTER))
        if (waitingForTerminal) {
            val authSlot = FrameLayout(context)
            val terminalSessionId = actionState.terminalSessionId.orEmpty()
            cardRunTopBarBinding = CardRunTopBarBinding(
                displayRecipe = recipe,
                actionRecipe = actionRecipe,
                actionInstanceId = actionState.instanceId,
                terminalSessionId = terminalSessionId,
                authSlot = authSlot
            )
            renderTerminalAuthorizationButton(authSlot, actionRecipe, actionState)
            addView(authSlot, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(29), Gravity.LEFT or Gravity.CENTER_VERTICAL).apply {
                setMargins(sideControlSize + dp(4), 0, 0, 0)
            })
        }
        val rightChrome = cardRunControlPill(recipe, state)
        addView(
            rightChrome,
            FrameLayout.LayoutParams(
                sideControlSize,
                sideControlSize,
                Gravity.RIGHT or Gravity.CENTER_VERTICAL
            )
        )
    }

    private fun cardRunSurfaceTitle(state: RecipeRuntimeState): String =
        when (state.surface) {
            CardRunSurface.Report, CardRunSurface.Summary -> "SH 报告"
            CardRunSurface.Terminal -> "终端"
            CardRunSurface.Web -> "网页"
            CardRunSurface.InstallWizard -> "获取向导"
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
            isSuccess -> "获取完成"
            isUninstall -> "清理失败"
            else -> "获取失败"
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
            if (isSuccess) setOnClickListener { closeCardRunTask() }
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
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
            background = roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, dp(12).toFloat(), 0)
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

    private fun renderTerminalAuthorizationButton(
        slot: FrameLayout,
        recipe: KiteRecipe,
        state: RecipeRuntimeState
    ) {
        slot.removeAllViews()
        val authUrl = terminalAuthorizationUrl(state.terminalSessionId)
        if (authUrl.isNullOrBlank()) return
        slot.addView(row {
            addView(cardRunAuthButton {
                openExternalAuthorizationUrl(recipe, authUrl)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun updateVisibleTerminalAuthorizationButton(sessionId: String) {
        val binding = cardRunTopBarBinding ?: return
        if (binding.terminalSessionId != sessionId || currentScreen != Screen.CardRun) return
        val state = CardRunStore.get(binding.actionInstanceId) ?: return
        renderTerminalAuthorizationButton(binding.authSlot, binding.actionRecipe, state)
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

    private fun canCompleteCurrentCardStep(recipe: KiteRecipe, state: RecipeRuntimeState): Boolean {
        val step = recipe.steps.getOrNull(state.currentStepIndex) ?: return false
        return when (step.type) {
            KiteRecipe.STEP_TERMINAL -> state.status == RecipeRunStatus.WaitingTerminal && !state.terminalSessionId.isNullOrBlank()
            KiteRecipe.STEP_OPEN_WEB -> state.surface == CardRunSurface.Web && !state.nextActionUrl.isNullOrBlank()
            else -> false
        }
    }

    private fun completeCurrentCardStep(recipe: KiteRecipe, state: RecipeRuntimeState) {
        val step = recipe.steps.getOrNull(state.currentStepIndex)
        val pending = if (step?.type == KiteRecipe.STEP_TERMINAL) {
            pendingTerminalFlow?.takeIf {
                it.recipeId == recipe.id &&
                    (it.instanceId == state.instanceId || it.sessionId == state.terminalSessionId)
            }
        } else {
            null
        }
        val nextStepIndex = pending?.nextStepIndex ?: (state.currentStepIndex + 1).coerceAtLeast(0)
        if (step?.type == KiteRecipe.STEP_TERMINAL) {
            pendingTerminalFlow = pendingTerminalFlow?.takeUnless {
                it.recipeId == recipe.id &&
                    (it.instanceId == state.instanceId || it.sessionId == state.terminalSessionId)
            }
            state.terminalSessionId?.takeIf { it.isNotBlank() }?.let {
                TerminalRuntimeHost.endSession(applicationContext, it)
            }
        }
        diagnostics.logRecipeAction(
            recipe,
            "card_step_completed_by_user",
            mapOf(
                "type" to step?.type.orEmpty(),
                "sessionId" to state.terminalSessionId.orEmpty(),
                "stepIndex" to state.currentStepIndex.toString(),
                "nextStepIndex" to nextStepIndex.toString()
            )
        )
        advanceAfterUserCompletedStep(
            recipe = recipe,
            state = state,
            nextStepIndex = nextStepIndex,
            lastOutput = when (step?.type) {
                KiteRecipe.STEP_TERMINAL -> "终端已由用户标记完成"
                KiteRecipe.STEP_OPEN_WEB -> "网页已由用户标记完成"
                else -> "步骤已由用户标记完成"
            }
        )
    }

    private fun advanceAfterUserCompletedStep(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        nextStepIndex: Int,
        lastOutput: String
    ) {
        if (nextStepIndex >= recipe.steps.size) {
            val runId = state.runId ?: state.terminalSessionId
            markResourceRunSuccess(recipe, runId, lastOutput)
            setRuntimeState(
                recipe,
                finishedRecipeStatus(recipe, state.pid),
                currentStepIndex = nextStepIndex,
                runId = runId,
                pid = state.pid,
                lastMeaningfulOutput = lastOutput,
                clearTerminalSession = true,
                clearNextActionUrl = true
            )
            closeCardRunTask()
            return
        }
        executeRecipeStep(
            recipe = recipe,
            stepIndex = nextStepIndex,
            runId = state.runId ?: state.terminalSessionId,
            pid = state.pid,
            lastOutput = lastOutput
        )
    }

    private fun cardRunControlPill(recipe: KiteRecipe, state: RecipeRuntimeState): View = row {
        gravity = Gravity.CENTER
        addView(
            cardRunMoreButton { showCardRunMenu(recipe, state) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun cardRunMoreButton(onClick: () -> Unit): View =
        object : View(this) {
            private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tokens.textPrimary
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val density = resources.displayMetrics.density
                val radius = 1.85f * density
                val spacing = 6.2f * density
                val centerX = width / 2f
                val centerY = height / 2f
                canvas.drawCircle(centerX - spacing, centerY, radius, dotPaint)
                canvas.drawCircle(centerX, centerY, radius, dotPaint)
                canvas.drawCircle(centerX + spacing, centerY, radius, dotPaint)
            }
        }.apply {
            background = roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, dp(12).toFloat(), 0)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun cardRunContent(recipe: KiteRecipe, state: RecipeRuntimeState): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
            when (state.surface) {
                CardRunSurface.Terminal -> Unit
                CardRunSurface.Web -> addView(cardRunPlaceholderPanel("网页", state.nextActionUrl ?: "还没有网页地址。"))
                else -> addView(cardRunReportPanel(recipe, state))
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

    private fun cardRunReportPanel(recipe: KiteRecipe, state: RecipeRuntimeState): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 0)
            }
            addView(cardRunReportSummaryCard(recipe, state))
            cardRunFailureInsightCard(recipe, state)?.let { addView(it) }
            addView(cardRunOutputCard(recipe, state))
        }

    private fun cardRunReportSummaryCard(recipe: KiteRecipe, state: RecipeRuntimeState): View =
        LinearLayout(this).apply {
            val reportBorder = Color.rgb(232, 235, 240)
            val reportText = Color.rgb(17, 24, 39)
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(142)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBox(Color.WHITE, reportBorder, dp(20).toFloat())
            elevation = dp(1).toFloat()
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = ">_"
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(Color.rgb(22, 163, 107))
                    background = roundedBox(Color.rgb(234, 248, 240), Color.TRANSPARENT, dp(12).toFloat(), 0)
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                        setMargins(0, 0, dp(14), 0)
                    }
                })
                addView(TextView(context).apply {
                    text = "执行摘要"
                    textSize = 17f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(reportText)
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(cardRunStatusBadge(state), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)).apply {
                    setMargins(dp(12), 0, 0, 0)
                })
            })
            addView(row {
                gravity = Gravity.BOTTOM
                setPadding(0, dp(14), 0, 0)
                val items = listOf(
                    "步骤" to cardRunStepCounter(state),
                    "已运行" to formatRunDuration(state),
                    "当前命令" to currentShellCommand(recipe, state).ifBlank { "--" }
                )
                items.forEachIndexed { index, item ->
                    addView(
                        cardRunSummaryMetric(item.first, item.second) { valueView ->
                            if (item.first == "已运行") {
                                registerCardRunReportBinding(
                                    recipeId = recipe.id,
                                    state = state,
                                    elapsedTextView = valueView
                                )
                            }
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    )
                    if (index != items.lastIndex) {
                        addView(View(context).apply {
                            setBackgroundColor(Color.argb(115, Color.red(reportBorder), Color.green(reportBorder), Color.blue(reportBorder)))
                        }, LinearLayout.LayoutParams(dp(1), dp(32)).apply {
                            setMargins(dp(8), dp(3), dp(8), 0)
                        })
                    }
                }
            })
        }

    private fun cardRunStatusBadge(state: RecipeRuntimeState): TextView {
        val isFailure = state.failureSummary() != null
        val isDone = state.status == RecipeRunStatus.Completed
        val isStopped = state.status == RecipeRunStatus.Stopped
        val color = when {
            isFailure -> tokens.danger
            state.isBusy() || state.isActive() -> tokens.primaryStrong
            isDone -> Color.rgb(22, 163, 107)
            isStopped -> tokens.textSecondary
            else -> Color.rgb(124, 133, 149)
        }
        val backgroundColor = when {
            isDone -> Color.rgb(234, 248, 240)
            isStopped -> tokens.surface
            else -> tintBackground(color)
        }
        val label = when {
            isFailure -> "失败"
            state.isBusy() || state.isActive() -> "运行中"
            isDone -> "已完成"
            isStopped -> "已停止"
            else -> state.status.label
        }
        return TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(color)
            background = roundedBox(backgroundColor, Color.TRANSPARENT, dp(11).toFloat(), 0)
        }
    }

    private fun cardRunFailureInsightCard(recipe: KiteRecipe, state: RecipeRuntimeState): View? {
        val insight = failureInsightFor(recipe, state) ?: return null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = roundedBox(tokens.surfaceElevated, tokens.border, dp(17).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(12), 0, 0)
            }
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = insight.marker
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(insight.color)
                    background = roundedBox(tintBackground(insight.color), Color.TRANSPARENT, dp(10).toFloat(), 0)
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        setMargins(0, 0, dp(11), 0)
                    }
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = insight.title
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                    })
                    addView(TextView(context).apply {
                        text = insight.detail
                        textSize = 12.2f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(4), 0, 0)
                        setLineSpacing(dp(3).toFloat(), 1.0f)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    private fun failureInsightFor(recipe: KiteRecipe, state: RecipeRuntimeState): FailureInsight? {
        if (state.failureSummary() == null) return null
        val text = listOfNotNull(state.lastError, state.lastMeaningfulOutput, state.shellReportText)
            .joinToString("\n")
        if (text.isBlank()) return null
        return when {
            text.contains("terminated with signal 9", ignoreCase = true) ||
                text.contains("signal 9", ignoreCase = true) ||
                Regex("""\bKilled\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                FailureInsight(
                    marker = "杀",
                    title = "不像网络错误，更像进程被系统强制结束",
                    detail = "日志里出现 signal 9。通常是内存/资源压力、PRoot 进程被 Android 杀掉，或安装阶段启动了过重的子进程。可以先关闭其他实例后重试；后续适合把浏览器工具这类重依赖拆成可选步骤。",
                    color = tokens.warning
                )
            isNetworkFailureText(text) ->
                FailureInsight(
                    marker = "网",
                    title = "可能是网络或上游源不可达",
                    detail = networkFailureDetailFor(recipe),
                    color = tokens.warning
                )
            text.contains("No space left on device", ignoreCase = true) ->
                FailureInsight(
                    marker = "存",
                    title = "存储空间不足",
                    detail = "安装目录或缓存目录空间不够。清理资源缓存、旧安装目录或释放手机存储后再重试。",
                    color = tokens.warning
                )
            else -> null
        }
    }

    private fun isNetworkFailureText(text: String): Boolean =
        listOf(
            "ENOTFOUND",
            "ECONNRESET",
            "ECONNREFUSED",
            "ETIMEDOUT",
            "network timeout",
            "Connection timed out",
            "Temporary failure in name resolution",
            "Could not resolve host",
            "Failed to connect",
            "SSL certificate problem",
            "npm ERR!",
            "pip._vendor",
            "ReadTimeout",
            "HTTPError 403",
            "HTTPError 404"
        ).any { text.contains(it, ignoreCase = true) }

    private fun networkFailureDetailFor(recipe: KiteRecipe): String =
        when {
            recipe.id.contains(RESOURCE_HERMES_CORE) ->
                "Hermes Core 需要访问官方安装脚本、GitHub、PyPI 和 files.pythonhosted.org。请确认当前网络或代理能访问这些域名。"
            recipe.id.contains(RESOURCE_HERMES_WEBUI) ->
                "Hermes WebUI 主要需要访问 registry.npmjs.org；如果安装浏览器工具，还可能访问 GitHub 或 CDN。"
            recipe.id.contains(RESOURCE_GIT) || recipe.id.contains(RESOURCE_CURL) || recipe.id.contains(RESOURCE_PYTHON) ->
                "这个资源通过 Ubuntu apt 安装，需要容器能访问当前 apt 软件源。源慢或 DNS 不通时会失败。"
            else ->
                "请检查代理、DNS、证书和上游下载地址；后续资源卡会把具体来源域名展示在详情页。"
        }

    private data class FailureInsight(
        val marker: String,
        val title: String,
        val detail: String,
        val color: Int
    )

    private fun cardRunSummaryMetric(
        label: String,
        value: String,
        onValueView: ((TextView) -> Unit)? = null
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = label
                textSize = 11f
                setTextColor(Color.rgb(152, 162, 179))
                includeFontPadding = false
            })
            val valueText = TextView(context).apply {
                text = value
                textSize = 15f
                setTextColor(Color.rgb(17, 24, 39))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(7), 0, 0)
            }
            addView(valueText)
            onValueView?.invoke(valueText)
        }

    private fun cardRunOutputCard(recipe: KiteRecipe, state: RecipeRuntimeState): View {
        val outputText = cardRunOutputText(recipe, state)
        val isRunning = state.isBusy() || state.isActive()
        val reportBorder = Color.rgb(232, 235, 240)
        val reportText = Color.rgb(17, 24, 39)
        val reportSecondary = Color.rgb(124, 133, 149)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(16))
            background = roundedBox(Color.WHITE, reportBorder, dp(20).toFloat())
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(18), 0, 0)
            }
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34))
                addView(TextView(context).apply {
                    text = "实时输出"
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    includeFontPadding = false
                    setTextColor(reportText)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(reportToolButton("⧉", "复制") {
                    val latest = CardRunStore.get(state.instanceId) ?: state
                    copyTextToClipboard("Kite SH 输出", cardRunOutputText(recipe, latest), "已复制 SH 输出")
                })
            })
            val outputTextView = TextView(context).apply {
                text = lineNumberedOutput(outputText)
                minimumHeight = dp(260)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(if (state.failureSummary() != null) tokens.danger else reportText)
                setLineSpacing(dp(3).toFloat(), 1.0f)
                includeFontPadding = true
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = roundedBox(Color.rgb(248, 250, 252), Color.TRANSPARENT, dp(16).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(12), 0, 0)
                }
            }
            addView(outputTextView)
            if (isRunning) {
                addView(row {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), 0)
                    addView(TextView(context).apply {
                        text = "●"
                        textSize = 10f
                        includeFontPadding = false
                        setTextColor(tokens.success)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, dp(8), 0)
                        }
                    })
                    val footerTextView = TextView(context).apply {
                        text = reportFooterLabel(state)
                        textSize = 13f
                        setTextColor(reportSecondary)
                    }
                    addView(footerTextView)
                    registerCardRunReportBinding(
                        recipeId = recipe.id,
                        state = state,
                        outputTextView = outputTextView,
                        footerTextView = footerTextView
                    )
                })
            } else {
                registerCardRunReportBinding(
                    recipeId = recipe.id,
                    state = state,
                    outputTextView = outputTextView
                )
            }
        }
    }

    private fun reportToolButton(icon: String, label: String, onClick: () -> Unit): View =
        row {
            gravity = Gravity.CENTER
            setPadding(dp(7), 0, dp(7), 0)
            addView(TextView(context).apply {
                text = icon
                textSize = 16f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.rgb(124, 133, 149))
            })
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(124, 133, 149))
                setPadding(dp(5), 0, 0, 0)
            })
            setOnClickListener { onClick() }
        }

    private fun copyTextToClipboard(label: String, text: String, toast: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
    }

    private fun reportFooterLabel(state: RecipeRuntimeState): String =
        when {
            state.failureSummary() != null -> "执行失败 · ${formatRunDuration(state)}"
            state.isBusy() || state.isActive() -> "正在执行 · ${formatRunDuration(state)}"
            state.status == RecipeRunStatus.Completed -> "已完成 · ${formatRunDuration(state)}"
            state.status == RecipeRunStatus.Stopped -> "已停止 · ${formatRunDuration(state)}"
            else -> state.status.label
        }

    private fun registerCardRunReportBinding(
        recipeId: String,
        state: RecipeRuntimeState,
        outputTextView: TextView? = null,
        footerTextView: TextView? = null,
        elapsedTextView: TextView? = null
    ) {
        val current = cardRunReportBinding?.takeIf { it.instanceId == state.instanceId }
        cardRunReportBinding = CardRunReportBinding(
            recipeId = recipeId,
            instanceId = state.instanceId,
            outputTextView = outputTextView ?: current?.outputTextView,
            footerTextView = footerTextView ?: current?.footerTextView,
            elapsedTextView = elapsedTextView ?: current?.elapsedTextView
        )
        scheduleForegroundLiveTickIfNeeded()
    }

    private fun updateVisibleCardRunReport(recipe: KiteRecipe, state: RecipeRuntimeState) {
        val binding = cardRunReportBinding ?: return
        if (binding.instanceId != state.instanceId || currentScreen != Screen.CardRun) return
        binding.outputTextView?.text = lineNumberedOutput(cardRunOutputText(recipe, state))
        binding.footerTextView?.text = reportFooterLabel(state)
        binding.elapsedTextView?.text = formatRunDuration(state)
        if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened) {
            scheduleForegroundLiveTickIfNeeded()
        }
    }

    private fun updateVisibleCardRunReportElapsed(): Boolean {
        val binding = cardRunReportBinding ?: return false
        if (currentScreen != Screen.CardRun) return false
        val state = CardRunStore.get(binding.instanceId) ?: return false
        binding.elapsedTextView?.text = formatRunDuration(state)
        binding.footerTextView?.text = reportFooterLabel(state)
        return state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened
    }

    private fun scheduleForegroundLiveTickIfNeeded() {
        if (!::root.isInitialized || foregroundLiveTickScheduled) return
        foregroundLiveTickScheduled = true
        root.postDelayed({
            foregroundLiveTickScheduled = false
            val keepReportTick = updateVisibleCardRunReportElapsed()
            val keepWizardTick = updateVisibleResourceInstallWizardElapsed()
            if (keepReportTick || keepWizardTick) {
                scheduleForegroundLiveTickIfNeeded()
            }
        }, 1000L)
    }

    private fun cardRunStepCounter(state: RecipeRuntimeState): String =
        if (state.stepCount > 0) "${(state.currentStepIndex + 1).coerceIn(1, state.stepCount)}/${state.stepCount}" else "--"

    private fun formatRunDuration(state: RecipeRuntimeState): String {
        val endAt = if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened) {
            System.currentTimeMillis()
        } else {
            state.updatedAt
        }
        val seconds = ((endAt - state.createdAt).coerceAtLeast(0L) / 1000L).coerceAtMost(99L * 60L + 59L)
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun formatCardRunElapsed(state: RecipeRuntimeState): String {
        val endAt = if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened) {
            System.currentTimeMillis()
        } else {
            state.updatedAt
        }
        val seconds = ((endAt - state.createdAt).coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60L * 60L -> String.format("%02d:%02d", seconds / 60L, seconds % 60L)
            seconds < 24L * 60L * 60L -> "${seconds / (60L * 60L)}小时"
            else -> "${seconds / (24L * 60L * 60L)}天"
        }
    }

    private fun formatLastRunTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val ageMs = (now - timestamp).coerceAtLeast(0L)
        val minuteMs = 60_000L
        val relativeCutoffMs = 30L * minuteMs
        if (ageMs < minuteMs) return "刚刚"
        if (ageMs < relativeCutoffMs) return "${ageMs / minuteMs}分钟前"

        val nowCalendar = Calendar.getInstance()
        val thenCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = nowCalendar.get(Calendar.YEAR) == thenCalendar.get(Calendar.YEAR) &&
            nowCalendar.get(Calendar.DAY_OF_YEAR) == thenCalendar.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return String.format(
                "%02d:%02d",
                thenCalendar.get(Calendar.HOUR_OF_DAY),
                thenCalendar.get(Calendar.MINUTE)
            )
        }

        val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterdayCalendar.get(Calendar.YEAR) == thenCalendar.get(Calendar.YEAR) &&
            yesterdayCalendar.get(Calendar.DAY_OF_YEAR) == thenCalendar.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "昨天"

        return "${thenCalendar.get(Calendar.MONTH) + 1}月${thenCalendar.get(Calendar.DAY_OF_MONTH)}日"
    }

    private fun currentShellCommand(recipe: KiteRecipe, state: RecipeRuntimeState): String {
        val stepCommand = recipe.steps.getOrNull(state.currentStepIndex)?.cmd.orEmpty().trim()
        if (stepCommand.isNotBlank()) return stepCommand.lineSequence().firstOrNull().orEmpty().ifBlank { stepCommand }
        return state.shellReportText.orEmpty()
            .lineSequence()
            .firstOrNull { it.startsWith("命令：") }
            ?.removePrefix("命令：")
            ?.trim()
            .orEmpty()
    }

    private fun fullShellCommand(recipe: KiteRecipe, state: RecipeRuntimeState): String {
        val stepCommand = recipe.steps.getOrNull(state.currentStepIndex)?.cmd.orEmpty().trim()
        if (stepCommand.isNotBlank()) return stepCommand
        return currentShellCommand(recipe, state)
    }

    private fun cardRunOutputText(recipe: KiteRecipe, state: RecipeRuntimeState): String {
        val command = fullShellCommand(recipe, state)
        val report = state.shellReportText.orEmpty().trim()
        val output = extractShellOutput(report)
        val fallback = when {
            output.isNotBlank() -> output
            state.lastError.orEmpty().isNotBlank() -> state.lastError.orEmpty()
            state.lastMeaningfulOutput.orEmpty().isNotBlank() -> state.lastMeaningfulOutput.orEmpty()
            else -> "暂无输出。一次性命令请使用“等待结束”，例如 python3 -V。"
        }.normalizeShellStreamForDisplay()
        return buildString {
            if (command.isNotBlank()) append(command).append("\n\n")
            append(fallback)
            commandHintFor(state)?.let { append("\n\n提示：").append(it) }
        }.trim()
    }

    private fun extractShellOutput(report: String): String {
        val markers = listOf("原始输出：", "有效输出：", "错误输出：", "输出：")
        markers.forEach { marker ->
            val index = report.indexOf(marker)
            if (index >= 0) {
                return report.substring(index + marker.length).trim()
            }
        }
        return report.lineSequence()
            .filterNot { line ->
                line.startsWith("命令：") ||
                    line.startsWith("结果：") ||
                    line.startsWith("退出码：") ||
                    line.startsWith("匹配：")
            }
            .joinToString("\n")
            .trim()
    }

    private fun lineNumberedOutput(text: String): CharSequence {
        val lines = text.ifBlank { "暂无输出。" }.lineSequence().toList().ifEmpty { listOf("暂无输出。") }
        val width = lines.size.toString().length.coerceAtLeast(2)
        return SpannableStringBuilder().apply {
            lines.forEachIndexed { index, line ->
                val numberStart = length
                append((index + 1).toString().padStart(width, ' '))
                val numberEnd = length
                setSpan(ForegroundColorSpan(Color.rgb(152, 162, 179)), numberStart, numberEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(0.92f), numberStart, numberEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("  ")
                append(line)
                if (index != lines.lastIndex) append('\n')
            }
        }
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
        val insightRecipe = CardRunStore.registeredRecipe(state.recipeId)
            ?: currentRecipes.firstOrNull { it.id == state.recipeId }
            ?: focusedRunRecipe()
        if (insightRecipe != null) {
            failureInsightFor(insightRecipe, state)?.let {
                lines += "可能原因：${it.title}"
                lines += "建议：${it.detail}"
            }
        }
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
                buildList {
                    add(CardRunMenuAction("↻", "刷新") {
                        dialog.dismiss()
                        showCardRunSurface(recipe)
                    })
                    if (state.isInterruptible() || state.hasRunBinding()) {
                        add(CardRunMenuAction("■", "终止") {
                            dialog.dismiss()
                            stopRecipe(recipe, state)
                        })
                    } else {
                        add(CardRunMenuAction("↺", "重新执行") {
                            dialog.dismiss()
                            startRecipe(recipe, state, focusedRunInstanceId)
                        })
                    }
                    add(CardRunMenuAction("⧉", "复制报告") {
                        copyCardRunReport(state)
                        dialog.dismiss()
                    })
                    add(CardRunMenuAction("⊙", "关闭实例") {
                        dialog.dismiss()
                        closeCardRunTask()
                    })
                },
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
            if (!currentResourceInstallTargetId.isNullOrBlank()) {
                showResourceInstallWizard()
                return
            }
            val resourceId = focusedRunRecipe()?.let { resourceIdForRecipe(it) }
            if (!resourceId.isNullOrBlank()) {
                showResourceDetail(resourceId)
            } else {
                showConsole()
            }
            return
        }
        val focusedRecipe = focusedRunRecipe()
        if (
            activeResourceInstallWizard != null &&
            focusedRecipe?.runtimeSource == RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE
        ) {
            val focusedState = focusedRunInstanceId?.let { CardRunStore.get(it) }
            if (focusedState?.surface != CardRunSurface.InstallWizard) {
                activeResourceInstallWizard = activeResourceInstallWizard?.copy(
                    selectedResourceId = null,
                    selectedSurface = CardRunSurface.InstallWizard
                )
                runtimeStates[focusedRecipe.id] = CardRunStore.update(
                    recipe = focusedRecipe,
                    status = RecipeRunStatus.Opened,
                    instanceId = focusedState?.instanceId ?: focusedRunInstanceId,
                    surface = CardRunSurface.InstallWizard,
                    lastMeaningfulOutput = "返回获取向导"
                )
                showResourceInstallWizard(activeResourceInstallWizard?.targetResourceId)
                return
            }
        }
        if (
            activeResourceInstallWizard != null &&
            focusedRecipe?.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE
        ) {
            showResourceInstallWizard(activeResourceInstallWizard?.targetResourceId)
            return
        }
        val focusedState = focusedRecipe?.let { recipe ->
            focusedRunInstanceId?.let { CardRunStore.get(it) }
                ?: runtimeStates[recipe.id]
                ?: CardRunStore.currentForRecipe(recipe.id)
        }
        if (focusedRecipe != null && focusedState != null && shouldStopRunWhenClosingInstance(focusedState)) {
            stopRecipe(focusedRecipe, focusedState)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    private fun shouldStopRunWhenClosingInstance(state: RecipeRuntimeState): Boolean =
        state.hasRunBinding() &&
            state.status != RecipeRunStatus.Stopping &&
            state.status != RecipeRunStatus.Stopped &&
            state.status != RecipeRunStatus.Completed &&
            state.status != RecipeRunStatus.Failed &&
            state.status != RecipeRunStatus.BridgeUnavailable

    private fun registerCardRunTaskCloser(instanceId: String) {
        if (this !is CardRunActivity || instanceId.isBlank()) return
        if (registeredCardRunCloserInstanceId == instanceId) return
        CardRunTaskCloser.unregister(registeredCardRunCloserInstanceId)
        registeredCardRunCloserInstanceId = instanceId
        CardRunTaskCloser.register(instanceId) {
            runOnUiThread { closeCardRunTask() }
        }
    }

    private fun closeCardRunInstanceForStop(recipe: KiteRecipe, previousState: RecipeRuntimeState, reason: String) {
        val instanceId = listOf(
            previousState.instanceId,
            activeRunInstanceIds[recipe.id],
            focusedRunInstanceId?.takeIf { CardRunStore.get(it)?.recipeId == recipe.id },
            CardRunStore.currentForRecipe(recipe.id)?.instanceId,
            recipe.id
        ).firstOrNull { !it.isNullOrBlank() } ?: return

        val closedLiveInstance = if (this is CardRunActivity && focusedRunInstanceId == instanceId) {
            closeCardRunTask()
            true
        } else {
            CardRunTaskCloser.close(instanceId)
        }
        val closedTask = finishCardRunTaskByInstanceId(instanceId)
        diagnostics.logRecipeAction(
            recipe,
            "card_run_task_close_requested",
            mapOf(
                "instanceId" to instanceId,
                "reason" to reason,
                "closedLiveInstance" to closedLiveInstance.toString(),
                "closedTask" to closedTask.toString()
            )
        )
    }

    private fun finishCardRunTaskByInstanceId(instanceId: String): Boolean {
        if (instanceId.isBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val targetUri = CardRunIntents.instanceDataUri(instanceId).toString()
        return runCatching {
            val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching false
            var closed = false
            manager.appTasks.forEach { task ->
                val baseIntent = task.taskInfo.baseIntent
                val matchesData = baseIntent?.data?.toString() == targetUri
                val matchesExtra = baseIntent?.getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID) == instanceId
                val isCardRun = baseIntent?.component?.className == CardRunActivity::class.java.name
                if (isCardRun && (matchesData || matchesExtra)) {
                    task.finishAndRemoveTask()
                    closed = true
                }
            }
            closed
        }.getOrDefault(false)
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
            setOnRefreshListener {
                refreshRecipeRuntimeStates(currentRecipes)
                refreshDropZoneRecipes(showToast = false)
            }
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
        background = roundedBox(tokens.cardBackground, tokens.border, dp(24).toFloat())
        elevation = dp(1).toFloat()
        isClickable = true
        setOnClickListener { showRecipeEditor(recipe) }

        addView(recipeIconTile(recipe, dp(38), 18f).apply {
            layoutParams = FrameLayout.LayoutParams(dp(38), dp(38), Gravity.START or Gravity.TOP).apply {
                setMargins(dp(13), dp(13), 0, 0)
            }
        })
        val statusHost = FrameLayout(context)
        applyRecipeStatusBadge(statusHost, runtimeState)
        addView(statusHost, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26), Gravity.END or Gravity.TOP).apply {
            setMargins(0, dp(14), dp(13), 0)
        })
        addView(recipeCardName(recipe.name), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.TOP).apply {
            setMargins(dp(15), dp(58), dp(92), 0)
        })
        addView(recipeCardCategory(recipe), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.TOP).apply {
            setMargins(dp(15), dp(78), dp(15), 0)
        })
        val cueHost = FrameLayout(context)
        cueHost.addView(recipeStepCue(recipe, runtimeState), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(cueHost, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
            setMargins(dp(15), 0, dp(96), dp(20))
        })
        val actionHost = FrameLayout(context)
        actionHost.addView(recipeActionArea(recipe, runtimeState, ubuntuBlocked), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(actionHost, FrameLayout.LayoutParams(dp(82), dp(50), Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(11), dp(8))
        })
        consoleCardBindings[recipe.id] = RecipeCardBinding(
            recipeId = recipe.id,
            statusHost = statusHost,
            cueHost = cueHost,
            actionHost = actionHost
        )
    }

    private fun updateVisibleConsoleCard(recipe: KiteRecipe, state: RecipeRuntimeState) {
        val binding = consoleCardBindings[recipe.id] ?: return
        if (currentScreen != Screen.Console) return
        val ubuntuBlocked = isUbuntuActionBlocked(recipe)
        applyRecipeStatusBadge(binding.statusHost, state)
        binding.cueHost.removeAllViews()
        binding.cueHost.addView(recipeStepCue(recipe, state), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        binding.actionHost.removeAllViews()
        binding.actionHost.addView(recipeActionArea(recipe, state, ubuntuBlocked), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun recipeActionArea(recipe: KiteRecipe, state: RecipeRuntimeState, ubuntuBlocked: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(recipePowerButton(recipe, state, ubuntuBlocked), LinearLayout.LayoutParams(dp(66), dp(31)).apply {
                setMargins(0, 0, 0, dp(4))
            })
            addView(recipeActionSubline(state), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(13)))
        }

    private fun recipePowerButton(recipe: KiteRecipe, state: RecipeRuntimeState, ubuntuBlocked: Boolean): TextView =
        TextView(this).apply {
            val disabled = state.status == RecipeRunStatus.Starting || state.status == RecipeRunStatus.Stopping || ubuntuBlocked
            val interruptible = state.isInterruptible()
            text = when {
                ubuntuBlocked -> "等待"
                state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable -> "重试"
                interruptible -> "停止"
                state.status == RecipeRunStatus.Starting || state.status == RecipeRunStatus.Stopping -> "处理中"
                else -> "启动"
            }
            textSize = 12.5f
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = if (disabled) 0.62f else 1f
            val fill = when {
                state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable -> tokens.danger
                interruptible -> tokens.warning
                else -> tokens.primaryStrong
            }
            background = roundedBox(fill, fill, dp(16).toFloat())
            isEnabled = !disabled
            if (!disabled) setOnClickListener {
                handleRecipeActionWithRouter(recipe)
            }
        }

    private fun recipeActionSubline(state: RecipeRuntimeState): TextView =
        TextView(this).apply {
            textSize = 9.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(recipeActionSublineColor(state))
            fun refresh() {
                text = recipeActionSublineText(state)
                if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened) {
                    postDelayed({
                        if (isAttachedToWindow) refresh()
                    }, 1000L)
                }
            }
            refresh()
        }

    private fun recipeActionSublineColor(state: RecipeRuntimeState): Int =
        when (state.status) {
            RecipeRunStatus.Failed,
            RecipeRunStatus.BridgeUnavailable -> tokens.danger
            else -> tokens.textSecondary
        }

    private fun recipeActionSublineText(state: RecipeRuntimeState): String =
        when {
            state.status == RecipeRunStatus.Unknown -> ""
            state.status == RecipeRunStatus.Failed ||
                state.status == RecipeRunStatus.BridgeUnavailable -> "已停止 · ${formatCardRunElapsed(state)}"
            state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened -> "运行 · ${formatCardRunElapsed(state)}"
            else -> "上次 · ${formatLastRunTime(state.updatedAt)}"
        }

    private fun recipeCardName(text: String): TextView = TextView(this).apply {
        val title = compactRecipeCardTitle(text)
        this.text = title.text
        textSize = title.textSizeSp
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.MIDDLE
    }

    private fun compactRecipeCardTitle(raw: String): RecipeCardTitleText {
        val title = raw.trim().ifBlank { "未命名卡片" }
        val bytes = utf8ByteCount(title)
        val overflow = (bytes - RECIPE_CARD_TITLE_SHRINK_AFTER_BYTES).coerceAtLeast(0)
        val range = (RECIPE_CARD_TITLE_MAX_BYTES - RECIPE_CARD_TITLE_SHRINK_AFTER_BYTES).coerceAtLeast(1)
        val progress = (overflow.toFloat() / range.toFloat()).coerceIn(0f, 1f)
        val textSize = RECIPE_CARD_TITLE_MAX_TEXT_SP -
            ((RECIPE_CARD_TITLE_MAX_TEXT_SP - RECIPE_CARD_TITLE_MIN_TEXT_SP) * progress)
        return RecipeCardTitleText(
            text = middleEllipsizeByUtf8Bytes(title, RECIPE_CARD_TITLE_MAX_BYTES),
            textSizeSp = textSize
        )
    }

    private fun middleEllipsizeByUtf8Bytes(text: String, maxBytes: Int): String {
        if (utf8ByteCount(text) <= maxBytes) return text
        val budget = (maxBytes - utf8ByteCount(RECIPE_CARD_TITLE_ELLIPSIS)).coerceAtLeast(0)
        if (budget <= 0) return RECIPE_CARD_TITLE_ELLIPSIS
        val prefixBudget = (budget * 3) / 5
        val suffixBudget = budget - prefixBudget
        return takeUtf8Prefix(text, prefixBudget) +
            RECIPE_CARD_TITLE_ELLIPSIS +
            takeUtf8Suffix(text, suffixBudget)
    }

    private fun takeUtf8Prefix(text: String, maxBytes: Int): String {
        val builder = StringBuilder()
        var index = 0
        var bytes = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val nextBytes = utf8ByteCount(codePoint)
            if (bytes + nextBytes > maxBytes) break
            builder.appendCodePoint(codePoint)
            bytes += nextBytes
            index += Character.charCount(codePoint)
        }
        return builder.toString()
    }

    private fun takeUtf8Suffix(text: String, maxBytes: Int): String {
        val codePoints = mutableListOf<Int>()
        var index = text.length
        var bytes = 0
        while (index > 0) {
            val codePoint = text.codePointBefore(index)
            val nextBytes = utf8ByteCount(codePoint)
            if (bytes + nextBytes > maxBytes) break
            codePoints.add(codePoint)
            bytes += nextBytes
            index -= Character.charCount(codePoint)
        }
        val builder = StringBuilder()
        for (position in codePoints.indices.reversed()) {
            builder.appendCodePoint(codePoints[position])
        }
        return builder.toString()
    }

    private fun utf8ByteCount(text: String): Int {
        var index = 0
        var bytes = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            bytes += utf8ByteCount(codePoint)
            index += Character.charCount(codePoint)
        }
        return bytes
    }

    private fun utf8ByteCount(codePoint: Int): Int =
        when {
            codePoint <= 0x7F -> 1
            codePoint <= 0x7FF -> 2
            codePoint <= 0xFFFF -> 3
            else -> 4
        }

    private fun recipeCardCategory(recipe: KiteRecipe): TextView = TextView(this).apply {
        text = recipeCategoryLabel(recipe)
        textSize = 9.8f
        includeFontPadding = false
        setTextColor(tokens.textTertiary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun recipeStepCue(recipe: KiteRecipe, state: RecipeRuntimeState): TextView = TextView(this).apply {
        text = recipeStepCueText(recipe, state)
        textSize = 10.4f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(recipeStepCueColor(state))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun applyRecipeStatusBadge(host: FrameLayout, state: RecipeRuntimeState) {
        host.removeAllViews()
        recipeStatusBadge(state)?.let { badge ->
            host.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)))
        }
    }

    private fun recipeStatusBadge(state: RecipeRuntimeState): View? {
        val badge = recipeStatusPresentation(state) ?: return null
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(9), 0)
            background = roundedBox(badge.background, Color.TRANSPARENT, dp(13).toFloat(), 0)
            addView(View(context).apply {
                background = roundedBox(badge.color, Color.TRANSPARENT, dp(4).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                    setMargins(0, 0, dp(5), 0)
                }
            })
            addView(TextView(context).apply {
                text = badge.label
                textSize = 10.5f
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(badge.color)
                maxLines = 1
            })
        }
    }

    private fun recipeStatusPresentation(state: RecipeRuntimeState): RecipeStatusBadge? {
        if (state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable) {
            return RecipeStatusBadge("失败", tokens.danger, tokens.dangerSoft)
        }
        if (state.status == RecipeRunStatus.WaitingTerminal || state.status == RecipeRunStatus.Opened) {
            return RecipeStatusBadge("手动操作", tokens.info, tokens.infoSoft)
        }
        if (state.status == RecipeRunStatus.Starting ||
            state.status == RecipeRunStatus.Stopping ||
            state.status == RecipeRunStatus.Running ||
            state.status == RecipeRunStatus.AlreadyRunning
        ) {
            return RecipeStatusBadge("运行中", tokens.success, tokens.successSoft)
        }
        return null
    }

    private fun recipeCategoryLabel(recipe: KiteRecipe): String =
        KiteRecipe.normalizeCategory(recipe.category)

    private fun recipeStepCueText(recipe: KiteRecipe, state: RecipeRuntimeState): String {
        val steps = recipe.steps
        if (steps.isEmpty()) return "无步骤"
        val isLive = state.status == RecipeRunStatus.Starting ||
            state.status == RecipeRunStatus.Stopping ||
            state.status == RecipeRunStatus.Running ||
            state.status == RecipeRunStatus.WaitingTerminal ||
            state.status == RecipeRunStatus.AlreadyRunning ||
            state.status == RecipeRunStatus.Opened
        val isProblem = state.status == RecipeRunStatus.Failed ||
            state.status == RecipeRunStatus.BridgeUnavailable
        val total = (state.stepCount.takeIf { it > 0 } ?: steps.size).coerceAtLeast(1)
        return if (isLive || isProblem) {
            val index = state.currentStepIndex.coerceIn(0, total - 1)
            "${recipeStepKind(steps.getOrNull(index) ?: steps.firstOrNull())} · ${index + 1}/$total"
        } else {
            val firstKind = recipeStepKind(steps.firstOrNull())
            if (total == 1) firstKind else "$firstKind · ${total}项"
        }
    }

    private fun recipeStepCueColor(state: RecipeRuntimeState): Int =
        when {
            state.status == RecipeRunStatus.Failed ||
                state.status == RecipeRunStatus.BridgeUnavailable -> tokens.danger
            state.status == RecipeRunStatus.Starting ||
                state.status == RecipeRunStatus.Stopping ||
                state.status == RecipeRunStatus.Running ||
                state.status == RecipeRunStatus.WaitingTerminal ||
                state.status == RecipeRunStatus.AlreadyRunning ||
                state.status == RecipeRunStatus.Opened -> tokens.success
            else -> tokens.textSecondary
        }

    private fun recipeStepKind(step: KiteRecipeStep?): String = when (step?.type) {
        KiteRecipe.STEP_SHELL -> "命令"
        KiteRecipe.STEP_TERMINAL -> "终端"
        KiteRecipe.STEP_OPEN_WEB -> "网页"
        KiteRecipe.STEP_ANDROID_ACTION -> "本机"
        else -> "卡片"
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
        if (state.status == RecipeRunStatus.Starting || state.status == RecipeRunStatus.Stopping) return
        if (isUbuntuActionBlocked(recipe)) {
            Toast.makeText(this, ubuntuRuntimeState.title, Toast.LENGTH_SHORT).show()
            return
        }

        if (state.isInterruptible()) {
            stopRecipe(recipe, state)
            return
        }

        val actionName = KiteRecipe.ACTION_START
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
            is KiteActionRoute.RunRecipe -> startRecipe(
                route.recipe,
                state,
                openConsoleOnStart = route.recipe.launch.openInstance,
                renderOnStart = route.recipe.launch.openInstance
            )
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
        openConsoleOnStart: Boolean = true,
        renderOnStart: Boolean = true,
        keepCurrentFocus: Boolean = false
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
            preferredInstanceId ?: activeRunInstanceIds[recipe.id] ?: recipe.id
        ).also {
            activeRunInstanceIds[recipe.id] = it.instanceId
            runtimeStates[recipe.id] = it
        }
        val firstStep = recipe.steps.firstOrNull()
        val initialSurface = firstStep?.let { surfaceForStep(it) } ?: CardRunSurface.Summary
        val deferInitialSurfaceUntilTerminalReady =
            firstStep?.type == KiteRecipe.STEP_TERMINAL && (this is CardRunActivity || !openConsoleOnStart)
        diagnostics.logRecipeAction(
            recipe,
            "recipe_sequence_start",
            mapOf(
                "steps" to recipe.steps.joinToString(" -> ") { it.type },
                "initialSurface" to initialSurface.name,
                "deferInitialSurface" to deferInitialSurfaceUntilTerminalReady.toString()
            )
        )
        setRuntimeState(
            recipe,
            RecipeRunStatus.Starting,
            surface = initialSurface,
            currentStepIndex = 0,
            lastMeaningfulOutput = "正在启动流程"
        )
        if (!renderOnStart) {
            if (!keepCurrentFocus) {
                focusedRunRecipeId = recipe.id
                focusedRunInstanceId = activeRunInstanceIds[recipe.id]
            }
        } else if (openConsoleOnStart && this !is CardRunActivity) {
            showConsole()
        } else {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id]
            if (!deferInitialSurfaceUntilTerminalReady) {
                showCardRunSurface(recipe)
            } else {
                showCardRunLoadingSurface(recipe, "正在准备终端")
            }
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
            lastMeaningfulOutput = "正在准备 Ubuntu",
            clearNextActionUrl = true
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
                finishedRecipeStatus(recipe, pid),
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
                val hostedByInstallWizard = resourceInstallWizardShouldHost(recipe)
                val openSurface = shouldOpenStepSurface(recipe, step)
                val waitForUserSignal = (openSurface && shouldRenderInCardRun(recipe)) ||
                    (hostedByInstallWizard && KiteRecipe.normalizeSurfaceMode(step.surfaceMode) != KiteRecipe.SURFACE_MODE_SILENT)
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
                if (openSurface) {
                    if (shouldRenderInCardRun(recipe)) {
                        focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
                        showCardRunSurface(recipe)
                    } else {
                        openWeb(url, "recipe_sequence", recipe)
                    }
                } else if (hostedByInstallWizard) {
                    renderResourceInstallWizardFor(recipe)
                } else {
                    diagnostics.logRecipeAction(
                        recipe,
                        "open_web_surface_suppressed",
                        mapOf("stepIndex" to stepIndex.toString(), "url" to url)
                    )
                }
                if (stepIndex < steps.lastIndex) {
                    if (waitForUserSignal) {
                        diagnostics.logRecipeAction(
                            recipe,
                            "open_web_waiting_for_user_completion",
                            mapOf("stepIndex" to stepIndex.toString(), "nextStepIndex" to (stepIndex + 1).toString())
                        )
                    } else {
                        executeRecipeStep(recipe, stepIndex + 1, runId, pid, lastOutput)
                    }
                } else if (!openSurface && !waitForUserSignal) {
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

    private fun shouldStayOnRunSurface(): Boolean =
        currentScreen == Screen.CardRun ||
            this is CardRunActivity

    private fun showRunSurfaceOrConsole(recipe: KiteRecipe) {
        if (renderResourceInstallWizardFor(recipe)) {
            Unit
        } else if (resourceRunSurfaceSuppressed(recipe)) {
            refreshResourceScreenIfVisible()
        } else if (shouldStayOnRunSurface()) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
            showCardRunSurface(recipe)
        } else if (currentScreen == Screen.CreateConfig || currentScreen == Screen.RecipeMore) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
        } else {
            showConsole()
        }
    }

    private fun showConsoleUnlessEditingRecipe(recipe: KiteRecipe) {
        if (currentScreen == Screen.CreateConfig || currentScreen == Screen.RecipeMore) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
        } else {
            showConsole()
        }
    }

    private fun toastIfNotResourceRecipe(recipe: KiteRecipe, message: String) {
        if (recipe.runtimeSource != KiteResourceInstallRecipes.RUNTIME_SOURCE) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shouldOpenStepSurface(recipe: KiteRecipe, step: KiteRecipeStep): Boolean {
        if (resourceRunSurfaceSuppressed(recipe)) return false
        return when (KiteRecipe.normalizeSurfaceMode(step.surfaceMode)) {
            KiteRecipe.SURFACE_MODE_PANEL -> true
            KiteRecipe.SURFACE_MODE_SILENT -> false
            else -> {
                val mayAutoOpenSurface = this is CardRunActivity ||
                    currentScreen == Screen.CardRun ||
                    recipe.launch.openInstance
                mayAutoOpenSurface && (
                    step.type == KiteRecipe.STEP_OPEN_WEB ||
                        step.type == KiteRecipe.STEP_TERMINAL ||
                        step.type == KiteRecipe.STEP_SHELL
                    )
            }
        }
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
            shellReportText = "命令：${step.cmd}\n结果：执行中",
            clearNextActionUrl = true
        )
        if (shouldOpenStepSurface(recipe, step)) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            showCardRunSurface(recipe)
        } else if (!renderResourceInstallWizardFor(recipe) && !resourceRunSurfaceSuppressed(recipe)) {
            showConsoleUnlessEditingRecipe(recipe)
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
            runId = progress.runId,
            pid = progress.pid,
            rootPid = progress.rootPid,
            processGroupId = progress.processGroupId,
            systemSessionId = progress.systemSessionId,
            lastMeaningfulOutput = progress.lastMeaningfulOutput.ifBlank { "正在执行 sh" },
            shellReportText = reportText
        )
        runtimeStates[recipe.id] = updated
        updateVisibleCardRunReport(recipe, updated)
        updateVisibleResourceInstallWizardElapsed()
    }

    private fun buildStreamingShellReport(step: KiteRecipeStep, output: String): String =
        buildString {
            append("命令：").append(step.cmd.orEmpty()).append('\n')
            append("结果：执行中")
            if (output.isNotBlank()) {
                append("\n原始输出：\n").append(output)
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
            lastMeaningfulOutput = "正在执行安卓动作：${step.action.orEmpty()}",
            clearNextActionUrl = true
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
        val currentState = runtimeStates[recipe.id] ?: CardRunStore.currentForRecipe(recipe.id)
        if (currentState?.currentStepIndex == stepIndex &&
            (currentState.status == RecipeRunStatus.Stopping || currentState.status == RecipeRunStatus.Stopped)
        ) {
            diagnostics.logRecipeAction(
                recipe,
                "sequence_shell_result_ignored_after_stop",
                mapOf("stepIndex" to stepIndex.toString(), "status" to currentState.status.name)
            )
            return
        }
        val report = result.runReport
        if (report != null) diagnostics.writeRunReport(report)
        val requestId = (report?.requestId ?: result.requestId).orEmpty()
        val runId = report?.runId ?: result.requestId
        val lastOutput = report?.lastMeaningfulOutput()
        val shellReport = shellReportText(report, recipe)
        val pid = report?.pid ?: extractPid(lastOutput) ?: extractPid(result.message)
        val rootPid = report?.rootPid ?: pid
        val processGroupId = report?.processGroupId
        val systemSessionId = report?.systemSessionId

        if (result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.BridgeUnavailable,
                currentStepIndex = stepIndex,
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
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
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
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
            rootPid = rootPid,
            processGroupId = processGroupId,
            systemSessionId = systemSessionId,
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
                title = cardTerminalTitle(recipe, stepIndex),
                sourceLabel = recipe.name
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
        TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
        TerminalSessionStore.refresh(appContext, force = true)

        val instanceId = ensureRunInstanceId(recipe)
        if (!resourceRunSurfaceSuppressed(recipe)) {
            focusedRunInstanceId = instanceId
        }
        pendingTerminalFlow = PendingTerminalFlow(
            recipeId = recipe.id,
            instanceId = instanceId,
            sessionId = record.id,
            nextStepIndex = stepIndex + 1
        )
        setRuntimeState(
            recipe,
            RecipeRunStatus.WaitingTerminal,
            instanceId = instanceId,
            surface = CardRunSurface.Terminal,
            currentStepIndex = stepIndex,
            runId = record.id,
            terminalSessionId = record.id,
            lastMeaningfulOutput = "等待终端完成：${record.title}",
            clearNextActionUrl = true
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
            showCardRunSurface(recipe)
        } else {
            diagnostics.logRecipeAction(
                recipe,
                "terminal_surface_suppressed",
                mapOf("sessionId" to record.id, "stepIndex" to stepIndex.toString())
            )
            if (!renderResourceInstallWizardFor(recipe) && !resourceRunSurfaceSuppressed(recipe)) {
                showConsoleUnlessEditingRecipe(recipe)
            }
            TerminalRuntimeHost.switchToSession(appContext, record.id)
        }
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

    private fun cardTerminalTitle(recipe: KiteRecipe, stepIndex: Int): String {
        val recipeName = recipe.name.trim().ifBlank { "Kite 卡片" }
        val terminalOrder = recipe.steps
            .take(stepIndex + 1)
            .count { it.type == KiteRecipe.STEP_TERMINAL }
            .coerceAtLeast(1)
        val suffix = if (recipe.steps.count { it.type == KiteRecipe.STEP_TERMINAL } > 1) {
            "终端 $terminalOrder"
        } else {
            "终端"
        }
        return "$recipeName · $suffix"
    }

    private fun startTerminalAuthorizationLinkWatcher(recipe: KiteRecipe, sessionId: String, instanceId: String) {
        fun active(): Boolean =
            pendingTerminalFlow?.let {
                it.recipeId == recipe.id && it.sessionId == sessionId && it.instanceId == instanceId
            } == true

        fun tick(startedAt: Long) {
            if (!active() || System.currentTimeMillis() - startedAt > TERMINAL_AUTH_LINK_WATCH_MS) return

            thread(name = "KiteTerminalAuthWatcher", isDaemon = true) {
                val authUrl = readTerminalAuthorizationUrlFromTranscript(sessionId)
                runOnUiThread {
                    if (!active() || System.currentTimeMillis() - startedAt > TERMINAL_AUTH_LINK_WATCH_MS) return@runOnUiThread
                    if (!authUrl.isNullOrBlank()) {
                        terminalAuthorizationUrlCache[sessionId] = authUrl
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
                            updateVisibleTerminalAuthorizationButton(sessionId)
                        }
                        return@runOnUiThread
                    }
                    root.postDelayed({ tick(startedAt) }, TERMINAL_AUTH_LINK_POLL_MS)
                }
            }
        }
        root.postDelayed({ tick(System.currentTimeMillis()) }, TERMINAL_AUTH_LINK_POLL_MS)
    }

    private fun terminalAuthorizationUrl(sessionId: String?): String? {
        if (sessionId.isNullOrBlank()) return null
        return terminalAuthorizationUrlCache[sessionId]
    }

    private fun readTerminalAuthorizationUrlFromTranscript(sessionId: String): String? {
        val entry = TerminalRuntimeRegistry.snapshot().firstOrNull { it.sessionId == sessionId } ?: return null
        val transcript = runCatching {
            val file = File(entry.transcriptPath)
            if (!file.isFile) return@runCatching ""
            val length = file.length()
            file.inputStream().use { input ->
                val skipBytes = length - TERMINAL_AUTH_LINK_TAIL_BYTES
                if (skipBytes > 0L) input.skip(skipBytes)
                input.bufferedReader().readText()
            }
        }.getOrNull().orEmpty()
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
        if (terminalSessionId == null &&
            previousState.runId.isNullOrBlank() &&
            previousState.pid.isNullOrBlank() &&
            previousState.status == RecipeRunStatus.Opened
        ) {
            diagnostics.logBridgeEvent(
                "stop_opened_web_local",
                recipe,
                mapOf("url" to previousState.nextActionUrl.orEmpty())
            )
            webView.stopLoading()
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                surface = CardRunSurface.Summary,
                currentStepIndex = previousState.currentStepIndex,
                lastMeaningfulOutput = "网页实例已关闭",
                clearRunBinding = true,
                clearNextActionUrl = true
            )
            closeCardRunInstanceForStop(recipe, previousState, "stop_opened_web")
            showConsole()
            return
        }
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
                surface = CardRunSurface.Summary,
                currentStepIndex = previousState.currentStepIndex,
                lastMeaningfulOutput = "终端已发送中断并关闭",
                clearRunBinding = true,
                clearTerminalSession = true
            )
            closeCardRunInstanceForStop(recipe, previousState, "stop_terminal_session")
            showConsole()
            return
        }

        setRuntimeState(
            recipe,
            RecipeRunStatus.Stopping,
            runId = previousState.runId,
            pid = previousState.pid,
            rootPid = previousState.rootPid,
            processGroupId = previousState.processGroupId,
            systemSessionId = previousState.systemSessionId,
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
            bridgeClient.stopRun(
                recipe = recipe,
                runId = previousState.runId,
                pid = previousState.pid,
                rootPid = previousState.rootPid,
                processGroupId = previousState.processGroupId,
                systemSessionId = previousState.systemSessionId,
                callback = callback
            )
        } else {
            diagnostics.logBridgeEvent(
                "stop_missing_run_id_fallback",
                recipe,
                mapOf("recipeId" to recipe.id, "message" to "missing runId, fallback to stop-recipe")
            )
            bridgeClient.stopProcessBinding(
                recipe = recipe,
                runId = previousState.instanceId,
                pid = previousState.pid,
                rootPid = previousState.rootPid,
                processGroupId = previousState.processGroupId,
                systemSessionId = previousState.systemSessionId,
                callback = callback
            )
        }
        closeCardRunInstanceForStop(recipe, previousState, "stop_request_sent")
    }

    private fun retryStopRequestAfterStableBridge(recipe: KiteRecipe, previousState: RecipeRuntimeState) {
        diagnostics.logBridgeEvent("stop_timeout_bridge_stable_retry", recipe, mapOf("runId" to previousState.runId.orEmpty()))
        val callback: (BridgeResult) -> Unit = { retryResult ->
            runOnUiThread { handleStopResultV2(recipe, previousState, retryResult, retriedAfterStableBridge = true) }
        }
        if (!previousState.runId.isNullOrBlank()) {
            bridgeClient.stopRun(
                recipe = recipe,
                runId = previousState.runId,
                pid = previousState.pid,
                rootPid = previousState.rootPid,
                processGroupId = previousState.processGroupId,
                systemSessionId = previousState.systemSessionId,
                callback = callback
            )
        } else {
            bridgeClient.stopProcessBinding(
                recipe = recipe,
                runId = previousState.instanceId,
                pid = previousState.pid,
                rootPid = previousState.rootPid,
                processGroupId = previousState.processGroupId,
                systemSessionId = previousState.systemSessionId,
                callback = callback
            )
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
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                surface = CardRunSurface.Summary,
                lastMeaningfulOutput = result.runReport?.lastMeaningfulOutput() ?: "已停止",
                clearRunBinding = true,
                clearTerminalSession = true,
                clearNextActionUrl = true
            )
            diagnostics.logBridgeEvent("stop_success", recipe, mapOf("runId" to previousState.runId.orEmpty()))
            closeCardRunInstanceForStop(recipe, previousState, "stop_success")
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
                        setRuntimeState(
                            recipe,
                            RecipeRunStatus.BridgeUnavailable,
                            runId = previousState.runId,
                            pid = previousState.pid,
                            rootPid = previousState.rootPid,
                            processGroupId = previousState.processGroupId,
                            systemSessionId = previousState.systemSessionId,
                            lastError = "Bridge 连接失败"
                        )
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
            rootPid = previousState.rootPid,
            processGroupId = previousState.processGroupId,
            systemSessionId = previousState.systemSessionId,
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
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                surface = CardRunSurface.Summary,
                lastMeaningfulOutput = result.runReport?.lastMeaningfulOutput() ?: "已停止",
                clearRunBinding = true,
                clearTerminalSession = true,
                clearNextActionUrl = true
            )
            closeCardRunInstanceForStop(recipe, previousState, "stop_success")
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
        val rootPid = report?.rootPid ?: pid
        val processGroupId = report?.processGroupId
        val systemSessionId = report?.systemSessionId

        if (result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.BridgeUnavailable,
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                shellReportText = shellReport,
                lastError = result.message
            )
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
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                lastMeaningfulOutput = lastOutput,
                shellReportText = shellReport,
                nextActionUrl = nextUrl
            )
            waitForWebReady(recipe, nextUrl, successStatus, runId, pid, lastOutput)
            return
        }

        if (report?.hasMismatch() == true) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.Failed,
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                lastMeaningfulOutput = lastOutput,
                shellReportText = shellReport,
                lastError = "result_mismatch"
            )
            diagnostics.logRecipeAction(recipe, "bridge_result_mismatch", mapOf("requestId" to requestId))
            markResourceInstallFailed(recipe, runId, "result_mismatch")
            toastIfNotResourceRecipe(recipe, "执行结果不匹配，已记录运行报告")
            showRunSurfaceOrConsole(recipe)
            return
        }

        if (report != null && (!report.ok || report.status == KiteRunReport.STATUS_FAILED)) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.Failed,
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                lastMeaningfulOutput = lastOutput,
                shellReportText = shellReport,
                lastError = result.message
            )
            diagnostics.logRecipeAction(recipe, "bridge_failed", mapOf("requestId" to requestId, "message" to result.message.take(500)))
            markResourceInstallFailed(recipe, runId, result.message.ifBlank { "执行失败" })
            toastIfNotResourceRecipe(recipe, "执行失败，已记录运行报告")
            showRunSurfaceOrConsole(recipe)
            return
        }

        if (result.ok || result.accepted) {
            markResourceRunSuccess(recipe, runId, lastOutput)
            setRuntimeState(
                recipe,
                successfulBridgeStatus(recipe, report, lastOutput),
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                lastMeaningfulOutput = lastOutput,
                shellReportText = shellReport
            )
            showRunSurfaceOrConsole(recipe)
            return
        }

        setRuntimeState(
            recipe,
            RecipeRunStatus.BridgeUnavailable,
            runId = runId,
            pid = pid,
            rootPid = rootPid,
            processGroupId = processGroupId,
            systemSessionId = systemSessionId,
            lastError = result.message
        )
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

    private fun isFiniteResourceOperation(recipe: KiteRecipe): Boolean =
        recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE &&
            resourceOperationForRecipe(recipe) != null

    private fun finishedRecipeStatus(recipe: KiteRecipe, pid: String?): RecipeRunStatus =
        if (isFiniteResourceOperation(recipe) || pid.isNullOrBlank()) {
            RecipeRunStatus.Completed
        } else {
            RecipeRunStatus.Running
        }

    private fun successfulBridgeStatus(
        recipe: KiteRecipe,
        report: KiteRunReport?,
        output: String?
    ): RecipeRunStatus =
        if (isFiniteResourceOperation(recipe)) {
            RecipeRunStatus.Completed
        } else {
            successfulStatus(report, output)
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
        instanceId: String? = null,
        surface: CardRunSurface? = null,
        currentStepIndex: Int? = null,
        runId: String? = null,
        terminalSessionId: String? = null,
        pid: String? = null,
        rootPid: String? = null,
        processGroupId: String? = null,
        systemSessionId: String? = null,
        lastMeaningfulOutput: String? = null,
        lastError: String? = null,
        shellReportText: String? = null,
        nextActionUrl: String? = null,
        clearRunBinding: Boolean = false,
        clearTerminalSession: Boolean = false,
        clearNextActionUrl: Boolean = false
    ) {
        val state = CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId ?: activeRunInstanceIds[recipe.id],
            surface = surface,
            currentStepIndex = currentStepIndex,
            runId = runId,
            terminalSessionId = terminalSessionId,
            pid = pid,
            rootPid = rootPid,
            processGroupId = processGroupId,
            systemSessionId = systemSessionId,
            lastMeaningfulOutput = lastMeaningfulOutput,
            lastError = lastError,
            shellReportText = shellReportText,
            nextActionUrl = nextActionUrl,
            clearRunBinding = clearRunBinding,
            clearTerminalSession = clearTerminalSession,
            clearNextActionUrl = clearNextActionUrl
        )
        runtimeStates[recipe.id] = state
        updateVisibleCardRunReport(recipe, state)
        updateVisibleResourceInstallWizardElapsed()
        diagnostics.logLifecycleEvent(
            recipe,
            status.lifecycleEvent,
            state.runId,
            state.pid,
            status.name,
            state.lastMeaningfulOutput,
            state.lastError
        )
        maybeRefreshConsoleAfterRuntimeState(recipe, state, status)
    }

    private fun maybeRefreshConsoleAfterRuntimeState(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        status: RecipeRunStatus
    ) {
        if (this is CardRunActivity || currentScreen != Screen.Console) return
        if (consoleCardBindings.containsKey(recipe.id)) {
            updateVisibleConsoleCard(recipe, state)
            return
        }
        val important = status == RecipeRunStatus.Completed ||
            status == RecipeRunStatus.Failed ||
            status == RecipeRunStatus.BridgeUnavailable ||
            status == RecipeRunStatus.Stopped ||
            status == RecipeRunStatus.Opened ||
            status == RecipeRunStatus.WaitingTerminal
        if (!important) return
        val now = System.currentTimeMillis()
        if (now - lastConsoleRuntimeRefreshAt < 600L) return
        lastConsoleRuntimeRefreshAt = now
        root.post {
            if (currentScreen == Screen.Console) showConsole()
        }
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
        selectedIconType = draft?.selectedIconType?.takeIf { it.isNotBlank() }
            ?: recipe?.icon?.type?.ifBlank { null }
            ?: KiteRecipeIcon.TYPE_BUILTIN
        selectedIconSource = draft?.selectedIconSource
            ?: recipe?.icon?.source
            ?: ""
        if (selectedIconType != KiteRecipeIcon.TYPE_IMAGE || selectedIconSource.isBlank()) {
            selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
            selectedIconSource = ""
            selectedIconName = KiteRecipeIcon.normalizeName(selectedIconName, selectedType)
        }
        val recipeId = recipe?.id.orEmpty()
        formShortcutRequested = draft?.shortcutRequested ?: (recipeId.isNotBlank() && cardLocalSettings.shortcutRequested(recipeId))
        formLaunchOpenInstance = draft?.launchOpenInstance ?: (recipe?.launch?.openInstance ?: true)
        clearRootForScreen()
        root.addView(createTopBar(if (recipe == null) "新建配置" else "编辑配置", saveAction = recipe == null))
        val bodyFrame = FrameLayout(this)
        val actionBar = recipe?.let { recipeEditorRunActions(it) }
        val scrollView = ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(30), dp(24), if (actionBar == null) dp(92) else dp(158))
                addView(formPanel())
                addView(formDivider())
                addView(sectionTitle("动作流程"))
                addView(stepsPanel())
                if (recipe != null) {
                    addView(formDivider())
                    addView(navigationRow("查看原始 JSON") { showRecipeRawJson(recipe) }.apply {
                        setPadding(0, dp(16), 0, dp(8))
                    })
                    addView(recentRunHistoryPanel(recipe))
                } else {
                    addView(formDivider())
                    addView(navigationRow("启动配置") { showRecipeFormMoreMenu() }.apply {
                        setPadding(0, dp(16), 0, dp(8))
                    })
                }
            })
        }
        bodyFrame.addView(scrollView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        actionBar?.let { bar ->
            bodyFrame.addView(
                bar,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46), Gravity.BOTTOM).apply {
                    setMargins(dp(24), 0, dp(24), dp(24))
                }
            )
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val threshold = maxOf(dp(180), scrollView.height / 3)
                val hide = scrollY > threshold
                val targetTranslation = if (hide) dp(82).toFloat() else 0f
                val targetAlpha = if (hide) 0f else 1f
                if (bar.translationY != targetTranslation || bar.alpha != targetAlpha) {
                    bar.animate()
                        .translationY(targetTranslation)
                        .alpha(targetAlpha)
                        .setDuration(180L)
                        .start()
                }
            }
        }
        root.addView(bodyFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
                text = "点击头像选择图片 ›"
                textSize = 8.8f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(5), 0, 0)
            })
        })
    }

    private fun recipeEditorRunActions(recipe: KiteRecipe): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
            layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(220L)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                setMargins(0, dp(22), 0, dp(24))
            }
            renderRecipeEditorActionRow(this, recipe)
        }

    private fun renderRecipeEditorActionRow(actionRow: LinearLayout, recipe: KiteRecipe) {
        val state = runtimeStateFor(recipe)
        val live = state.status == RecipeRunStatus.Starting ||
            state.status == RecipeRunStatus.Stopping ||
            state.isInterruptible()
        actionRow.removeAllViews()
        actionRow.gravity = Gravity.CENTER_VERTICAL
        if (live) {
            actionRow.background = roundedBox(tokens.primarySubtle, tokens.border, dp(16).toFloat())
            actionRow.addView(recipeEditorActionSegment(
                label = "打开",
                textColor = tokens.primaryStrong,
                enabled = state.status != RecipeRunStatus.Stopping
            ) {
                openRecipeRunInstance(recipe)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 7f))
            actionRow.addView(View(this).apply {
                setBackgroundColor(tokens.border)
            }, LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, dp(10), 0, dp(10))
            })
            actionRow.addView(recipeEditorActionSegment(
                label = "停止",
                textColor = tokens.danger,
                enabled = state.status != RecipeRunStatus.Stopping
            ) {
                stopRecipe(recipe, state)
                actionRow.postDelayed({ renderRecipeEditorActionRow(actionRow, recipe) }, 180L)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f))
        } else {
            val blocked = isUbuntuActionBlocked(recipe)
            actionRow.background = roundedBox(
                if (blocked) tokens.surface else tokens.primaryStrong,
                if (blocked) tokens.border else tokens.primaryStrong,
                dp(16).toFloat()
            )
            actionRow.addView(recipeEditorActionSegment(
                label = if (state.status == RecipeRunStatus.Failed || state.status == RecipeRunStatus.BridgeUnavailable) "重新启动" else "启动",
                textColor = if (blocked) tokens.textSecondary else Color.WHITE,
                enabled = !blocked
            ) {
                startRecipeFromEditor(recipe, actionRow)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun startRecipeFromEditor(recipe: KiteRecipe, actionRow: LinearLayout) {
        val state = runtimeStateFor(recipe)
        if (state.status == RecipeRunStatus.Starting || state.status == RecipeRunStatus.Stopping) return
        if (isUbuntuActionBlocked(recipe)) {
            Toast.makeText(this, ubuntuRuntimeState.title, Toast.LENGTH_SHORT).show()
            return
        }
        if (state.isInterruptible()) {
            renderRecipeEditorActionRow(actionRow, recipe)
            return
        }

        when (val route = actionRouter.route(recipe, KiteRecipe.ACTION_START)) {
            is KiteActionRoute.RunRecipe -> {
                startRecipe(route.recipe, state, openConsoleOnStart = false, renderOnStart = true)
                renderRecipeEditorActionRow(actionRow, recipe)
            }
            is KiteActionRoute.OpenWeb -> {
                val instanceId = ensureRunInstanceId(recipe)
                setRuntimeState(
                    recipe,
                    RecipeRunStatus.Opened,
                    instanceId = instanceId,
                    surface = CardRunSurface.Web,
                    nextActionUrl = route.url
                )
                renderRecipeEditorActionRow(actionRow, recipe)
            }
            is KiteActionRoute.NativeAction -> {
                runNativeAction(recipe, route)
                renderRecipeEditorActionRow(actionRow, recipe)
            }
            is KiteActionRoute.StopRecipe -> {
                stopRecipe(recipe, state)
                actionRow.postDelayed({ renderRecipeEditorActionRow(actionRow, recipe) }, 180L)
            }
            is KiteActionRoute.Unsupported -> {
                setRuntimeState(recipe, RecipeRunStatus.Failed, lastError = route.reason)
                Toast.makeText(this, route.reason, Toast.LENGTH_SHORT).show()
                renderRecipeEditorActionRow(actionRow, recipe)
            }
        }
    }

    private fun recipeEditorActionButton(
        label: String,
        fill: Int,
        textColor: Int,
        enabled: Boolean = true,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(textColor)
            alpha = if (enabled) 1f else 0.56f
            background = roundedBox(fill, Color.TRANSPARENT, dp(15).toFloat(), 0)
            isEnabled = enabled
            if (enabled) setOnClickListener { onClick() }
        }

    private fun recipeEditorActionSegment(
        label: String,
        textColor: Int,
        enabled: Boolean = true,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(textColor)
            alpha = if (enabled) 1f else 0.52f
            background = roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, dp(16).toFloat(), 0)
            isEnabled = enabled
            if (enabled) setOnClickListener { onClick() }
        }

    private fun openRecipeRunInstance(recipe: KiteRecipe) {
        val instanceId = activeRunInstanceIds[recipe.id]
            ?: focusedRunInstanceId?.takeIf { CardRunStore.get(it)?.recipeId == recipe.id }
            ?: CardRunStore.currentForRecipe(recipe.id)?.instanceId
            ?: recipe.id
        activeRunInstanceIds[recipe.id] = instanceId
        startActivity(
            CardRunIntents.launchIntent(
                context = this,
                recipeId = recipe.id,
                instanceId = instanceId,
                launchSource = CardRunIntents.SOURCE_CARD,
                autoStart = false
            )
        )
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
            addView(recentRunHistoryPanel(recipe))
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
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))

        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        val glyph = TextView(context).apply {
            text = displayIconGlyph(selectedIconName)
            textSize = 19.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
        }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(glyph, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fun renderIcon() {
            val bitmap = selectedIconBitmap()
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                image.visibility = View.VISIBLE
                glyph.visibility = View.GONE
            } else {
                image.setImageDrawable(null)
                image.visibility = View.GONE
                glyph.visibility = View.VISIBLE
                glyph.text = displayIconGlyph(selectedIconName)
            }
        }
        avatarTileRefresh = { renderIcon() }
        renderIcon()

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
            showRecipeIconMenu()
        }
    }

    private fun selectedIconBitmap(): Bitmap? =
        if (selectedIconType == KiteRecipeIcon.TYPE_IMAGE && selectedIconSource.isNotBlank()) {
            decodeRecipeIconSource(selectedIconSource)
        } else {
            null
        }

    private fun showRecipeIconMenu() {
        recipeIconMenuDialog?.dismiss()
        val dialog = Dialog(this)
        recipeIconMenuDialog = dialog
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = roundedBox(tokens.surfaceElevated, Color.TRANSPARENT, dp(24).toFloat(), 0)
            addView(TextView(context).apply {
                text = "选择头像"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "从头像集选择，或添加一张新图片"
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, dp(12))
            })

            addView(recipeIconMenuSectionTitle("头像集"))
            addView(recipeIconGrid().apply {
                customRecipeIconSources().forEach { source ->
                    addView(imageIconChoiceTile(source) {
                        applyImageRecipeIcon(source)
                        dialog.dismiss()
                    })
                }
                addView(addIconChoiceTile {
                    dialog.dismiss()
                    openRecipeIconPicker(applyAfterSave = false, reopenMenuAfterSave = true)
                })
            })

            addView(recipeIconMenuSectionTitle("预置图标").apply {
                setPadding(0, dp(14), 0, dp(8))
            })
            addView(recipeIconGrid().apply {
                presetRecipeIcons().forEach { iconName ->
                    addView(builtinIconChoiceTile(iconName) {
                        applyBuiltinRecipeIcon(iconName)
                        dialog.dismiss()
                    })
                }
            })
        }
        dialog.setContentView(ScrollView(this).apply { addView(content) })
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.setOnDismissListener {
            if (recipeIconMenuDialog == dialog) recipeIconMenuDialog = null
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun recipeIconMenuSectionTitle(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        setPadding(0, 0, 0, dp(8))
    }

    private fun recipeIconGrid(): GridLayout = GridLayout(this).apply {
        columnCount = 4
        setPadding(0, 0, 0, dp(2))
    }

    private fun builtinIconChoiceTile(iconName: String, onClick: () -> Unit): View =
        recipeIconChoiceFrame(
            selected = selectedIconType == KiteRecipeIcon.TYPE_BUILTIN && selectedIconName == iconName,
            onClick = onClick
        ).apply {
            addView(TextView(context).apply {
                text = iconGlyph(iconName)
                textSize = 18f
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(16).toFloat(), 0)
            }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(4)
            })
            addView(TextView(context).apply {
                text = builtinIconLabel(iconName)
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20), Gravity.BOTTOM))
        }

    private fun imageIconChoiceTile(source: String, onClick: () -> Unit): View =
        recipeIconChoiceFrame(
            selected = selectedIconType == KiteRecipeIcon.TYPE_IMAGE && selectedIconSource == source,
            onClick = onClick
        ).apply {
            addView(FrameLayout(context).apply {
                background = roundedBox(tokens.surface, tokens.border, dp(16).toFloat())
                clipToOutline = true
                val bitmap = decodeRecipeIconSource(source)
                if (bitmap != null) {
                    addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(bitmap)
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                } else {
                    addView(TextView(context).apply {
                        text = "?"
                        textSize = 18f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setTextColor(tokens.textTertiary)
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(4)
            })
            addView(TextView(context).apply {
                text = "自定义"
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20), Gravity.BOTTOM))
        }

    private fun addIconChoiceTile(onClick: () -> Unit): View =
        recipeIconChoiceFrame(selected = false, onClick = onClick).apply {
            addView(TextView(context).apply {
                text = "+"
                textSize = 24f
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                background = roundedBox(tokens.surface, tokens.border, dp(16).toFloat())
            }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(4)
            })
            addView(TextView(context).apply {
                text = "添加"
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20), Gravity.BOTTOM))
        }

    private fun recipeIconChoiceFrame(selected: Boolean, onClick: () -> Unit): FrameLayout =
        FrameLayout(this).apply {
            val width = ((resources.displayMetrics.widthPixels * 0.92f).toInt() - dp(36)) / 4
            background = roundedBox(
                if (selected) tokens.primarySubtle else Color.TRANSPARENT,
                if (selected) tokens.primarySoft else Color.TRANSPARENT,
                dp(18).toFloat()
            )
            layoutParams = ViewGroup.MarginLayoutParams(width, dp(76)).apply {
                setMargins(0, 0, 0, dp(8))
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun applyBuiltinRecipeIcon(iconName: String) {
        selectedIconName = iconName
        selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
        selectedIconSource = ""
        renderIconOptions()
        avatarTileRefresh?.invoke()
    }

    private fun applyImageRecipeIcon(source: String) {
        selectedIconType = KiteRecipeIcon.TYPE_IMAGE
        selectedIconName = "custom"
        selectedIconSource = source
        renderIconOptions()
        avatarTileRefresh?.invoke()
    }

    private fun presetRecipeIcons(): List<String> = listOf(
        "terminal",
        "web",
        "bot",
        "file",
        "tools",
        "server",
        "code",
        "logs"
    )

    private fun builtinIconLabel(iconName: String): String = when (iconName) {
        "terminal" -> "终端"
        "web" -> "网页"
        "bot" -> "AI"
        "file" -> "文件"
        "tools" -> "工具"
        "server" -> "服务"
        "code" -> "代码"
        "logs" -> "日志"
        else -> "图标"
    }

    private fun customRecipeIconSources(): List<String> {
        val raw = appSettings.getString(KEY_RECIPE_ICON_COLLECTION, "[]").orEmpty()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val source = json.optString(index).takeIf { it.isNotBlank() } ?: continue
                    if (recipeIconSourceExists(source)) add(source)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun addRecipeIconToCollection(source: String) {
        if (source.isBlank()) return
        val merged = (listOf(source) + customRecipeIconSources()).distinct().take(48)
        appSettings.edit()
            .putString(KEY_RECIPE_ICON_COLLECTION, JSONArray().apply { merged.forEach { put(it) } }.toString())
            .apply()
    }

    private fun openRecipeIconPicker(applyAfterSave: Boolean = false, reopenMenuAfterSave: Boolean = false) {
        applyPickedIconAfterCrop = applyAfterSave
        reopenIconMenuAfterCrop = reopenMenuAfterSave
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        runCatching {
            startActivityForResult(
                Intent.createChooser(intent, "选择卡片头像"),
                REQUEST_PICK_RECIPE_ICON
            )
        }.onFailure { error ->
            applyPickedIconAfterCrop = false
            reopenIconMenuAfterCrop = false
            if (error is ActivityNotFoundException) {
                Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "打开相册失败：${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRecipeIconCropDialog(uri: Uri) {
        val sourceBitmap = decodeBitmapFromUri(uri, 2048) ?: run {
            Toast.makeText(this, "无法读取这张图片", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = Dialog(this)
        val cropView = AvatarCropView(this, sourceBitmap).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)).apply {
                setMargins(0, dp(14), 0, dp(18))
            }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
            background = roundedBox(tokens.surfaceElevated, Color.TRANSPARENT, dp(24).toFloat(), 0)
            addView(TextView(context).apply {
                text = "裁剪头像"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "拖动图片调整位置，双指缩放"
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, 0)
            })
            addView(cropView)
            addView(row {
                gravity = Gravity.RIGHT
                addView(TextView(context).apply {
                    text = "取消"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(tokens.textSecondary)
                    background = roundedBox(tokens.surface, tokens.border, dp(16).toFloat())
                    layoutParams = LinearLayout.LayoutParams(dp(86), dp(38)).apply {
                        setMargins(0, 0, dp(10), 0)
                    }
                    setOnClickListener { dialog.dismiss() }
                })
                addView(TextView(context).apply {
                    text = "保存"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(16).toFloat())
                    layoutParams = LinearLayout.LayoutParams(dp(96), dp(38))
                    setOnClickListener {
                        val cropped = cropView.cropBitmap(512)
                        val source = saveRecipeIconBitmap(cropped)
                        cropped.recycle()
                        if (source.isBlank()) {
                            Toast.makeText(context, "保存头像失败", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        addRecipeIconToCollection(source)
                        if (applyPickedIconAfterCrop) {
                            applyImageRecipeIcon(source)
                        } else {
                            Toast.makeText(context, "已加入头像集", Toast.LENGTH_SHORT).show()
                        }
                        val shouldReopenMenu = reopenIconMenuAfterCrop
                        applyPickedIconAfterCrop = false
                        reopenIconMenuAfterCrop = false
                        dialog.dismiss()
                        if (shouldReopenMenu) {
                            root.post { showRecipeIconMenu() }
                        }
                    }
                })
            })
        }
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.setOnDismissListener {
            if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun decodeBitmapFromUri(uri: Uri, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun saveRecipeIconBitmap(bitmap: Bitmap): String {
        val directory = File(filesDir, "card-icons").apply { mkdirs() }
        val owner = editingRecipe?.id
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            ?: "draft_${UUID.randomUUID().toString().replace("-", "")}"
        val file = File(directory, "${owner}_${System.currentTimeMillis()}.png")
        return runCatching {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            "card-icons/${file.name}"
        }.getOrDefault("")
    }

    private fun decodeRecipeIconSource(source: String): Bitmap? {
        val file = recipeIconFile(source)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    private fun recipeIconSourceExists(source: String): Boolean = recipeIconFile(source).exists()

    private fun recipeIconFile(source: String): File =
        if (source.startsWith("/") || source.contains(":")) {
            File(source)
        } else {
            File(filesDir, source)
        }

    private inner class AvatarCropView(context: Context, private val sourceBitmap: Bitmap) : View(context) {
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77000000 }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val matrix = Matrix()
        private val inverse = Matrix()
        private val cropRect = RectF()
        private var scale = 1f
        private var minScale = 1f
        private var offsetX = 0f
        private var offsetY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var lastDistance = 0f
        private var lastScale = 1f
        private var gestureMode = 0

        init {
            setBackgroundColor(Color.rgb(18, 24, 38))
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val cropSize = min(w, h) * 0.82f
            cropRect.set(
                (w - cropSize) / 2f,
                (h - cropSize) / 2f,
                (w + cropSize) / 2f,
                (h + cropSize) / 2f
            )
            resetToCoverCrop()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            updateMatrix()
            canvas.drawBitmap(sourceBitmap, matrix, imagePaint)
            canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, overlayPaint)
            canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), overlayPaint)
            canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
            canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, overlayPaint)
            canvas.drawRoundRect(cropRect, dp(18).toFloat(), dp(18).toFloat(), borderPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureMode = 1
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        gestureMode = 2
                        lastDistance = pointerDistance(event)
                        lastScale = scale
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (gestureMode == 2 && event.pointerCount >= 2) {
                        val distance = pointerDistance(event)
                        if (lastDistance > 0f) {
                            scale = (lastScale * distance / lastDistance).coerceIn(minScale, minScale * 5f)
                            clampOffset()
                            invalidate()
                        }
                    } else if (gestureMode == 1) {
                        offsetX += event.x - lastX
                        offsetY += event.y - lastY
                        lastX = event.x
                        lastY = event.y
                        clampOffset()
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> gestureMode = 0
                MotionEvent.ACTION_POINTER_UP -> {
                    gestureMode = 1
                    lastX = event.getX(0)
                    lastY = event.getY(0)
                }
            }
            return true
        }

        fun cropBitmap(size: Int): Bitmap {
            updateMatrix()
            matrix.invert(inverse)
            val points = floatArrayOf(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom)
            inverse.mapPoints(points)
            val left = points[0].toInt().coerceIn(0, sourceBitmap.width - 1)
            val top = points[1].toInt().coerceIn(0, sourceBitmap.height - 1)
            val right = points[2].toInt().coerceIn(left + 1, sourceBitmap.width)
            val bottom = points[3].toInt().coerceIn(top + 1, sourceBitmap.height)
            val cropped = Bitmap.createBitmap(sourceBitmap, left, top, right - left, bottom - top)
            return Bitmap.createScaledBitmap(cropped, size, size, true).also {
                if (cropped !== it) cropped.recycle()
            }
        }

        private fun resetToCoverCrop() {
            if (cropRect.isEmpty) return
            minScale = max(cropRect.width() / sourceBitmap.width, cropRect.height() / sourceBitmap.height)
            scale = minScale
            offsetX = cropRect.centerX() - sourceBitmap.width * scale / 2f
            offsetY = cropRect.centerY() - sourceBitmap.height * scale / 2f
            clampOffset()
        }

        private fun updateMatrix() {
            matrix.reset()
            matrix.postScale(scale, scale)
            matrix.postTranslate(offsetX, offsetY)
        }

        private fun clampOffset() {
            val scaledWidth = sourceBitmap.width * scale
            val scaledHeight = sourceBitmap.height * scale
            offsetX = when {
                scaledWidth <= cropRect.width() -> cropRect.centerX() - scaledWidth / 2f
                offsetX > cropRect.left -> cropRect.left
                offsetX + scaledWidth < cropRect.right -> cropRect.right - scaledWidth
                else -> offsetX
            }
            offsetY = when {
                scaledHeight <= cropRect.height() -> cropRect.centerY() - scaledHeight / 2f
                offsetY > cropRect.top -> cropRect.top
                offsetY + scaledHeight < cropRect.bottom -> cropRect.bottom - scaledHeight
                else -> offsetY
            }
        }

        private fun pointerDistance(event: MotionEvent): Float {
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return kotlin.math.sqrt(dx * dx + dy * dy)
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
        selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
        selectedIconSource = ""
        avatarTileRefresh?.invoke()
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
        if (::shortcutSwitch.isInitialized) shortcutSwitch.isChecked = formShortcutRequested
        if (::launchInstanceSwitch.isInitialized) launchInstanceSwitch.isChecked = formLaunchOpenInstance
    }

    private fun handleRecipeFormBack() {
        if (recipeFormHasChanges()) {
            showRecipeUnsavedChangesDialog()
        } else {
            discardRecipeDraftAndShowConsole()
        }
    }

    private fun recipeFormHasChanges(): Boolean =
        currentRecipeFormSnapshot() != baselineRecipeFormSnapshot(editingRecipe)

    private fun recipeFormChangeLabels(): List<String> {
        val current = currentRecipeFormSnapshot()
        val baseline = baselineRecipeFormSnapshot(editingRecipe)
        return buildList {
            if (current.name != baseline.name || current.description != baseline.description) add("基础信息")
            if (
                current.selectedIconName != baseline.selectedIconName ||
                current.selectedIconType != baseline.selectedIconType ||
                current.selectedIconSource != baseline.selectedIconSource
            ) {
                add("头像")
            }
            if (current.steps != baseline.steps) add("动作流程")
            if (
                current.shortcutRequested != baseline.shortcutRequested ||
                current.launchOpenInstance != baseline.launchOpenInstance
            ) {
                add("启动配置")
            }
        }
    }

    private fun showRecipeUnsavedChangesDialog() {
        val dialog = Dialog(this)
        val creatingNewRecipe = editingRecipe == null
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(20).toFloat())
            addView(TextView(context).apply {
                text = if (creatingNewRecipe) "取消新建配置？" else "保存这次修改？"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(row {
                setPadding(0, dp(20), 0, 0)
                if (creatingNewRecipe) {
                    addView(TextView(context).apply {
                        text = "取消"
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(tokens.danger)
                        background = roundedBox(tintBackground(tokens.danger), tintBackgroundBorder(tokens.danger), dp(13).toFloat())
                        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(0, 0, dp(8), 0)
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            discardRecipeDraftAndShowConsole()
                        }
                    })
                    addView(TextView(context).apply {
                        text = "继续编辑"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(tokens.textPrimary)
                        background = roundedBox(tokens.surface, tokens.border, dp(13).toFloat())
                        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(dp(8), 0, 0, 0)
                        }
                        setOnClickListener { dialog.dismiss() }
                    })
                } else {
                    addView(TextView(context).apply {
                        text = "不保存"
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(tokens.danger)
                        background = roundedBox(tintBackground(tokens.danger), tintBackgroundBorder(tokens.danger), dp(13).toFloat())
                        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(0, 0, dp(8), 0)
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            discardRecipeDraftAndShowConsole()
                        }
                    })
                    addView(TextView(context).apply {
                        text = "保存"
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(Color.WHITE)
                        background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(13).toFloat())
                        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(dp(8), 0, 0, 0)
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            saveRecipeForm()
                        }
                    })
                }
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.78f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun currentRecipeFormSnapshot(): RecipeFormSnapshot =
        RecipeFormSnapshot(
            name = if (::nameInput.isInitialized) nameInput.text?.toString().orEmpty().trim() else "",
            description = if (::descriptionInput.isInitialized) descriptionInput.text?.toString().orEmpty().trim() else "",
            selectedIconName = selectedIconName,
            selectedIconType = selectedIconType,
            selectedIconSource = selectedIconSource,
            shortcutRequested = formShortcutRequested,
            launchOpenInstance = formLaunchOpenInstance,
            steps = formSteps.map { it.normalizedCopy() }
        )

    private fun baselineRecipeFormSnapshot(recipe: KiteRecipe?): RecipeFormSnapshot {
        if (recipe == null) {
            return RecipeFormSnapshot(
                name = "",
                description = "",
                selectedIconName = KiteRecipeIcon.defaultNameForType(KiteRecipe.TYPE_COMMAND_WEB),
                selectedIconType = KiteRecipeIcon.TYPE_BUILTIN,
                selectedIconSource = "",
                shortcutRequested = false,
                launchOpenInstance = true,
                steps = emptyList()
            )
        }
        val iconType = recipe.icon.type.ifBlank { KiteRecipeIcon.TYPE_BUILTIN }
        val iconSource = recipe.icon.source.takeIf { iconType == KiteRecipeIcon.TYPE_IMAGE }.orEmpty()
        val iconName = if (iconType == KiteRecipeIcon.TYPE_IMAGE) {
            recipe.icon.name.ifBlank { "custom" }
        } else {
            KiteRecipeIcon.normalizeName(recipe.icon.name, recipe.type)
        }
        return RecipeFormSnapshot(
            name = recipe.name.trim(),
            description = recipe.description.trim(),
            selectedIconName = iconName,
            selectedIconType = if (iconType == KiteRecipeIcon.TYPE_IMAGE && iconSource.isNotBlank()) {
                KiteRecipeIcon.TYPE_IMAGE
            } else {
                KiteRecipeIcon.TYPE_BUILTIN
            },
            selectedIconSource = iconSource,
            shortcutRequested = recipe.id.isNotBlank() && cardLocalSettings.shortcutRequested(recipe.id),
            launchOpenInstance = recipe.launch.openInstance,
            steps = recipe.steps.map { RecipeStepDraft.fromStep(it).normalizedCopy() }
        )
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
        val draft = snapshotRecipeFormDraft()
        val name = draft?.name?.trim()
            ?: nameInput.text?.toString().orEmpty().trim()
        val description = draft?.description?.trim()
            ?: descriptionInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedSteps = draft?.steps?.map { it.normalizedCopy() } ?: formSteps.map { it.normalizedCopy() }
        if (normalizedSteps.isEmpty()) {
            Toast.makeText(this, "请至少添加一个命令或打开网页步骤", Toast.LENGTH_SHORT).show()
            return
        }
        normalizedSteps.forEachIndexed { index, step ->
            if (step.type == KiteRecipe.STEP_OPEN_WEB && step.url.isBlank()) {
                Toast.makeText(this, "第 ${index + 1} 个打开网页步骤缺少地址", Toast.LENGTH_SHORT).show()
                return
            }
            if (step.type == KiteRecipe.STEP_SHELL && step.command.isBlank()) {
                Toast.makeText(this, "第 ${index + 1} 个 sh 命令步骤缺少命令", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val inferredType = inferTypeFromDrafts(normalizedSteps)
        val defaultUrl = normalizedSteps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB }?.url.orEmpty()
        val requestShortcut = draft?.shortcutRequested ?: formShortcutRequested
        val openInstanceOnStart = draft?.launchOpenInstance ?: formLaunchOpenInstance
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
                    category = editingRecipe?.category.orEmpty(),
                    url = defaultUrl,
                    command = "",
                    shortcut = false,
                    openInstanceOnStart = openInstanceOnStart,
                    iconName = draft?.selectedIconName ?: selectedIconName,
                    iconType = draft?.selectedIconType ?: selectedIconType,
                    iconSource = draft?.selectedIconSource ?: selectedIconSource,
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
        val latestRecipe = latestRecipeForRawJson(recipe)
        currentScreen = Screen.RecipeDetail
        clearRootForScreen()
        root.addView(topBar("原始 JSON") { showRecipeEditor(latestRecipe) })
        root.addView(ScrollView(this).apply {
            addView(TextView(context).apply {
                text = latestRecipe.toJson(includeLocalIdentity = true).toString(2)
                textSize = 14f
                setTextColor(tokens.textPrimary)
                setPadding(dp(24), dp(20), dp(24), dp(28))
                typeface = Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun latestRecipeForRawJson(recipe: KiteRecipe): KiteRecipe {
        val recipes = runCatching { recipeLoader.loadAllRecipes() }.getOrNull().orEmpty()
        if (recipes.isNotEmpty()) currentRecipes = recipes
        return recipes.firstOrNull { recipe.id.isNotBlank() && it.id == recipe.id }
            ?: recipes.firstOrNull {
                recipe.id.isBlank() &&
                    it.name == recipe.name &&
                    it.description == recipe.description &&
                    it.steps.map { step -> step.type to (step.cmd ?: step.text ?: step.url).orEmpty() } ==
                    recipe.steps.map { step -> step.type to (step.cmd ?: step.text ?: step.url).orEmpty() }
            }
            ?: recipe
    }

    private fun recentRunHistoryPanel(recipe: KiteRecipe): View =
        LinearLayout(this).apply {
            val history = CardRunStore.historyForRecipe(recipe.id)
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, dp(4))
            addView(TextView(context).apply {
                text = "最近运行"
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(0, 0, 0, dp(10))
            })
            if (history.isEmpty()) {
                addView(runHistoryEmptyBlock(compact = true))
            } else {
                history.forEachIndexed { index, entry ->
                    addView(runHistoryPreviewRow(recipe, entry, index + 1))
                }
            }
        }

    private fun runHistoryEmptyBlock(compact: Boolean = false): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), if (compact) dp(12) else dp(16), dp(16), if (compact) dp(12) else dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            addView(TextView(context).apply {
                text = "还没有运行记录"
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "启动一次卡片后，这里会出现本次流程的时间、步骤和自动执行内容。"
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(8), 0, 0)
            })
        }

    private fun runHistoryPreviewRow(
        recipe: KiteRecipe,
        entry: CardRunHistoryEntry,
        ordinal: Int,
        onClick: () -> Unit = { showRecipeRunHistoryDetail(recipe, entry) }
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(11), dp(12), dp(11))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(16).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(8))
            }
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = ordinal.toString()
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.WHITE)
                background = roundedBox(runHistoryStatusColor(entry), Color.TRANSPARENT, dp(11).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    setMargins(0, 0, dp(11), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = "${entry.status.label} · ${runHistoryDuration(entry)} · ${runHistoryProgress(entry)}"
                    textSize = 12.2f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = runHistoryTimeline(entry)
                    textSize = 10.5f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 24f
                setTextColor(tokens.textTertiary)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }

    private fun showRecipeRunHistoryDetail(recipe: KiteRecipe, entry: CardRunHistoryEntry) {
        val latestRecipe = latestRecipeForRawJson(recipe)
        showRunHistoryDetail(
            screen = Screen.RecipeDetail,
            title = "运行详情",
            backAction = { showRecipeEditor(latestRecipe) },
            recipe = latestRecipe,
            entry = entry,
            onStepReport = { step -> showRecipeRunHistoryStepReport(recipe, entry, step) }
        )
    }

    private fun showResourceRunHistoryDetail(item: ResourceItem, recipe: KiteRecipe, entry: CardRunHistoryEntry) {
        showRunHistoryDetail(
            screen = Screen.ResourceMore,
            title = "获取日志",
            backAction = { showResourceMoreActions(item) },
            recipe = recipe,
            entry = entry,
            onStepReport = { step -> showResourceRunHistoryStepReport(item, recipe, entry, step) }
        )
    }

    private fun showRunHistoryDetail(
        screen: Screen,
        title: String,
        backAction: () -> Unit,
        recipe: KiteRecipe,
        entry: CardRunHistoryEntry,
        onStepReport: (CardRunHistoryStep) -> Unit
    ) {
        currentScreen = screen
        clearRootForScreen()
        root.addView(topBar(title, backAction))
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(18), dp(24), dp(28))
                addView(runHistoryDetailHeader(entry))
                addView(TextView(context).apply {
                    text = "流程快照"
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    setPadding(0, dp(20), 0, dp(10))
                })
                if (entry.steps.isEmpty()) {
                    addView(runHistoryEmptyBlock(compact = true))
                } else {
                    entry.steps.forEach { step ->
                        addView(runHistoryStepRow(recipe, entry, step) { onStepReport(step) })
                    }
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun runHistoryDetailHeader(entry: CardRunHistoryEntry): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "本次运行"
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(context).apply {
                    text = entry.status.label
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    setTextColor(runHistoryStatusColor(entry))
                    background = roundedBox(tintBackground(runHistoryStatusColor(entry)), Color.TRANSPARENT, dp(10).toFloat())
                    setPadding(dp(9), dp(5), dp(9), dp(5))
                })
            })
            addView(TextView(context).apply {
                text = "${runHistoryTimeline(entry)} · ${runHistoryDuration(entry)} · ${runHistoryProgress(entry)}"
                textSize = 11.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(7), 0, 0)
            })
            val message = entry.error.ifBlank { entry.summary }
            if (message.isNotBlank()) {
                addView(TextView(context).apply {
                    text = message.take(160)
                    textSize = 11.2f
                    setTextColor(if (entry.error.isNotBlank()) tokens.danger else tokens.textSecondary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(8), 0, 0)
                })
            }
            if (entry.steps.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = "步骤快照来自本次运行，不会随当前卡片编排修改而改变。"
                    textSize = 10.5f
                    setTextColor(tokens.textTertiary)
                    setPadding(0, dp(9), 0, 0)
                })
            }
        }

    private fun showRecipeRunHistoryStepReport(recipe: KiteRecipe, entry: CardRunHistoryEntry, step: CardRunHistoryStep) {
        showRunHistoryStepReport(
            screen = Screen.RecipeDetail,
            backAction = { showRecipeRunHistoryDetail(recipe, entry) },
            step = step
        )
    }

    private fun showResourceRunHistoryStepReport(
        item: ResourceItem,
        recipe: KiteRecipe,
        entry: CardRunHistoryEntry,
        step: CardRunHistoryStep
    ) {
        showRunHistoryStepReport(
            screen = Screen.ResourceMore,
            backAction = { showResourceRunHistoryDetail(item, recipe, entry) },
            step = step
        )
    }

    private fun showRunHistoryStepReport(
        screen: Screen,
        backAction: () -> Unit,
        step: CardRunHistoryStep
    ) {
        currentScreen = screen
        clearRootForScreen()
        root.addView(topBar("历史 SH 报告", backAction))
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(18), dp(24), dp(28))
                addView(TextView(context).apply {
                    text = "步骤 ${step.index + 1} · ${step.label}"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = runHistoryStepDetail(step)
                    textSize = 11.2f
                    setTextColor(tokens.textSecondary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(5), 0, dp(12))
                })
                addView(readonlyShellReportCard(step))
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun readonlyShellReportCard(step: CardRunHistoryStep): View {
        val outputText = historicalShellOutputText(step)
        val reportBorder = Color.rgb(232, 235, 240)
        val reportText = Color.rgb(17, 24, 39)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(16))
            background = roundedBox(Color.WHITE, reportBorder, dp(20).toFloat())
            elevation = dp(1).toFloat()
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34))
                addView(TextView(context).apply {
                    text = "只读 SH 报告"
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    includeFontPadding = false
                    setTextColor(reportText)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(reportToolButton("⧉", "复制") {
                    copyTextToClipboard("Kite 历史 SH 报告", outputText, "已复制 SH 报告")
                })
            })
            addView(TextView(context).apply {
                text = lineNumberedOutput(outputText)
                minimumHeight = dp(220)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(reportText)
                setLineSpacing(dp(3).toFloat(), 1.0f)
                includeFontPadding = true
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = roundedBox(Color.rgb(248, 250, 252), Color.TRANSPARENT, dp(16).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(12), 0, 0)
                }
            })
        }
    }

    private fun historicalShellOutputText(step: CardRunHistoryStep): String {
        val report = step.reportText.trim()
        val output = extractShellOutput(report).ifBlank { report }
        val command = step.detail.ifBlank {
            report.lineSequence()
                .firstOrNull { it.startsWith("命令：") }
                ?.removePrefix("命令：")
                ?.trim()
                .orEmpty()
        }
        return buildString {
            if (command.isNotBlank()) append(command).append("\n\n")
            append(output.ifBlank { "没有可用的 SH 报告快照。" }.normalizeShellStreamForDisplay())
        }.trim()
    }

    private fun runHistoryStepRow(
        recipe: KiteRecipe,
        entry: CardRunHistoryEntry,
        step: CardRunHistoryStep,
        onReportClick: () -> Unit = { showRecipeRunHistoryStepReport(recipe, entry, step) }
    ): View =
        LinearLayout(this).apply {
            val canOpenReport = step.type == KiteRecipe.STEP_SHELL && step.reportText.isNotBlank()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(16).toFloat())
            isClickable = canOpenReport
            if (canOpenReport) setOnClickListener { onReportClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(8))
            }
            addView(TextView(context).apply {
                text = "${step.index + 1}"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.WHITE)
                background = roundedBox(runHistoryStepColor(entry, step.index), Color.TRANSPARENT, dp(9).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    setMargins(0, dp(1), dp(9), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = "${step.label} · ${runHistoryStepState(entry, step.index)}"
                    textSize = 11.8f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = runHistoryStepDetail(step)
                    textSize = 10.8f
                    setTextColor(tokens.textSecondary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
            })
            if (canOpenReport) {
                addView(TextView(context).apply {
                    text = "报告 ›"
                    textSize = 11.2f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.primaryStrong)
                    includeFontPadding = false
                    setPadding(dp(10), dp(3), 0, 0)
                })
            }
        }

    private fun runHistoryProgress(entry: CardRunHistoryEntry): String {
        val total = entry.stepCount.takeIf { it > 0 } ?: entry.steps.size
        if (total <= 0) return "无步骤"
        val done = when {
            entry.status == RecipeRunStatus.Completed -> total
            entry.currentStepIndex < 0 -> 0
            entry.isClosed() -> (entry.currentStepIndex + 1).coerceIn(0, total)
            else -> entry.currentStepIndex.coerceIn(0, total - 1) + 1
        }
        return "步骤 $done/$total"
    }

    private fun runHistoryDuration(entry: CardRunHistoryEntry): String {
        val endAt = entry.endedAt ?: if (entry.isClosed()) entry.updatedAt else System.currentTimeMillis()
        val seconds = ((endAt - entry.startedAt).coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60L * 60L -> String.format("%02d:%02d", seconds / 60L, seconds % 60L)
            seconds < 24L * 60L * 60L -> "${seconds / (60L * 60L)}小时"
            else -> "${seconds / (24L * 60L * 60L)}天"
        }
    }

    private fun runHistoryTimeline(entry: CardRunHistoryEntry): String {
        val start = formatRunHistoryClock(entry.startedAt)
        val endAt = entry.endedAt ?: entry.updatedAt.takeIf { entry.isClosed() }
        val end = endAt?.let { "结束 ${formatRunHistoryClock(it)}" } ?: "进行中"
        return "开始 $start · $end"
    }

    private fun formatRunHistoryClock(timestamp: Long): String {
        val nowCalendar = Calendar.getInstance()
        val thenCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = nowCalendar.get(Calendar.YEAR) == thenCalendar.get(Calendar.YEAR) &&
            nowCalendar.get(Calendar.DAY_OF_YEAR) == thenCalendar.get(Calendar.DAY_OF_YEAR)
        val time = String.format(
            "%02d:%02d",
            thenCalendar.get(Calendar.HOUR_OF_DAY),
            thenCalendar.get(Calendar.MINUTE)
        )
        return if (sameDay) {
            time
        } else {
            "${thenCalendar.get(Calendar.MONTH) + 1}月${thenCalendar.get(Calendar.DAY_OF_MONTH)}日 $time"
        }
    }

    private fun runHistoryStatusColor(entry: CardRunHistoryEntry): Int =
        when (entry.status) {
            RecipeRunStatus.Failed,
            RecipeRunStatus.BridgeUnavailable -> tokens.danger
            RecipeRunStatus.Completed -> tokens.success
            RecipeRunStatus.Stopped -> tokens.info
            RecipeRunStatus.Starting,
            RecipeRunStatus.Running,
            RecipeRunStatus.WaitingTerminal,
            RecipeRunStatus.AlreadyRunning,
            RecipeRunStatus.Opened -> tokens.primaryStrong
            else -> tokens.textSecondary
        }

    private fun runHistoryStepColor(entry: CardRunHistoryEntry, index: Int): Int =
        when (runHistoryStepState(entry, index)) {
            "失败" -> tokens.danger
            "已完成" -> tokens.success
            "已停止" -> tokens.info
            "未执行" -> tokens.textTertiary
            else -> tokens.primaryStrong
        }

    private fun runHistoryStepState(entry: CardRunHistoryEntry, index: Int): String =
        when {
            entry.status == RecipeRunStatus.Completed && index < entry.stepCount -> "已完成"
            index < entry.currentStepIndex -> "已完成"
            index > entry.currentStepIndex && entry.currentStepIndex >= 0 -> "未执行"
            entry.currentStepIndex < 0 -> if (entry.isClosed()) "未执行" else entry.status.label
            entry.status == RecipeRunStatus.Failed ||
                entry.status == RecipeRunStatus.BridgeUnavailable -> "失败"
            entry.status == RecipeRunStatus.Stopped -> "已停止"
            entry.status == RecipeRunStatus.WaitingTerminal -> "等待终端"
            entry.status == RecipeRunStatus.Opened -> "已打开"
            entry.status == RecipeRunStatus.Running ||
                entry.status == RecipeRunStatus.AlreadyRunning ||
                entry.status == RecipeRunStatus.Starting -> entry.status.label
            else -> entry.status.label
        }

    private fun runHistoryStepDetail(step: CardRunHistoryStep): String =
        step.detail.ifBlank {
            when (step.type) {
                KiteRecipe.STEP_TERMINAL -> "打开交互终端"
                KiteRecipe.STEP_OPEN_WEB -> "未记录网址"
                KiteRecipe.STEP_SHELL -> "未记录命令"
                else -> "无自动内容"
            }
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

    private fun createTopBar(title: String, saveAction: Boolean = false): View = row {
        setPadding(dp(18), dp(14), dp(18), dp(10))
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton("‹", dp(44), Color.TRANSPARENT, tokens.textPrimary, dp(16)) { handleRecipeFormBack() })
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val rightText = if (saveAction) "保存" else "..."
        val rightColor = if (saveAction) tokens.primaryStrong else tokens.textPrimary
        addView(iconButton(rightText, dp(44), Color.TRANSPARENT, rightColor, dp(16)) {
            if (saveAction) saveRecipeForm() else showRecipeFormMoreMenu()
        })
    }

    private fun showRecipeFormMoreMenu() {
        val draft = snapshotRecipeFormDraft() ?: recipeMoreDraft ?: return
        recipeMoreDraft = draft.withLaunchState()
        val activeRecipe = editingRecipe
        currentScreen = Screen.RecipeMore
        clearRootForScreen()
        root.setBackgroundColor(tokens.pageBackground)
        root.addView(topBar("更多配置") { returnToRecipeFormFromMore() })
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(18), dp(24), dp(92))
                addView(TextView(context).apply {
                    text = "启动配置"
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    setPadding(0, 0, 0, dp(12))
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(10))
                    background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
                    addView(launchOptionsPanel())
                })
                if (activeRecipe != null) {
                    addView(formDivider())
                    addView(TextView(context).apply {
                        text = "危险操作"
                        textSize = 13.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        setPadding(0, 0, 0, dp(12))
                    })
                    addView(recipeMoreDeleteRow(activeRecipe))
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun returnToRecipeFormFromMore() {
        val draft = recipeMoreDraft?.withLaunchState()
        recipeMoreDraft = null
        showRecipeForm(editingRecipe, draft)
    }

    private fun recipeMoreDeleteRow(recipe: KiteRecipe): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        background = roundedBox(Color.WHITE, Color.rgb(229, 231, 235), dp(16).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            setMargins(0, dp(8), 0, 0)
        }
        addView(TextView(context).apply {
            text = "删除配置"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.danger)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = "›"
            textSize = 22f
            setTextColor(tokens.danger)
        })
        setOnClickListener {
            showDeleteRecipeConfirmSheet(recipe)
        }
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
                selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
                selectedIconSource = ""
                renderTypeOptions()
                renderIconOptions()
                avatarTileRefresh?.invoke()
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
                showRecipeIconMenu()
                return@setOnClickListener
            }
            selectedIconName = iconName
            selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
            selectedIconSource = ""
            renderIconOptions()
            avatarTileRefresh?.invoke()
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
            detail = "默认从首页启动时进入独立最近任务；关闭后就在首页内静默执行。",
            checked = formLaunchOpenInstance
        ) {
            launchInstanceSwitch = it
            it.setOnCheckedChangeListener { _, isChecked ->
                formLaunchOpenInstance = isChecked
            }
        })
        addView(divider())
        addView(localSwitchRow(
            title = "申请桌面图标",
            detail = "保存后向桌面发起创建申请，删除快捷方式后不做回收。",
            checked = formShortcutRequested
        ) {
            shortcutSwitch = it
            it.setOnCheckedChangeListener { _, isChecked ->
                formShortcutRequested = isChecked
            }
        })
    }

    private fun localSwitchRow(title: String, detail: String, checked: Boolean, bind: (Switch) -> Unit): View = row {
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
        val switch = Switch(context).apply { isChecked = checked }
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
        addView(navItem("≡", "资源", currentScreen == Screen.Resources || currentScreen == Screen.ResourceManage || currentScreen == Screen.ResourceDetail || currentScreen == Screen.ResourceMore || currentScreen == Screen.ResourceRawJson) { showResources() })
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
                "保存" -> 13f
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

    private fun recipeIconTile(recipe: KiteRecipe, size: Int, fallbackTextSize: Float): View {
        val bitmap = if (recipe.icon.type == KiteRecipeIcon.TYPE_IMAGE && recipe.icon.source.isNotBlank()) {
            decodeRecipeIconSource(recipe.icon.source)
        } else {
            null
        }
        if (bitmap == null) {
            return iconTile(recipe.icon.name, accentFor(recipe), tintBackground(accentFor(recipe))).apply {
                textSize = fallbackTextSize
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        }
        return FrameLayout(this).apply {
            background = roundedBox(tokens.surface, tintBackgroundBorder(accentFor(recipe)), dp(14).toFloat())
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(size, size)
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(bitmap)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
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
            state.status == RecipeRunStatus.Stopping ||
            state.isInterruptible()
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
        fun normalizedCopy(): RecipeStepDraft = copy(
            command = command.trim(),
            url = url.trim(),
            workdir = workdir.trim()
        )

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
        val selectedIconType: String,
        val selectedIconSource: String,
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
                .put("selectedIconType", selectedIconType)
                .put("selectedIconSource", selectedIconSource)
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
                        selectedIconType = json.optString("selectedIconType").ifBlank { KiteRecipeIcon.TYPE_BUILTIN },
                        selectedIconSource = json.optString("selectedIconSource"),
                        name = json.optString("name"),
                        description = json.optString("description"),
                        url = json.optString("url"),
                        command = json.optString("command"),
                        workdir = json.optString("workdir"),
                        shortcutRequested = json.optBoolean("shortcutRequested", false),
                        launchOpenInstance = json.optBoolean("launchOpenInstance", true),
                        steps = steps
                    )
                }.getOrNull()
        }
    }

    private data class RecipeFormSnapshot(
        val name: String,
        val description: String,
        val selectedIconName: String,
        val selectedIconType: String,
        val selectedIconSource: String,
        val shortcutRequested: Boolean,
        val launchOpenInstance: Boolean,
        val steps: List<RecipeStepDraft>
    )

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
        val steps: List<ResourceStep>,
        val rawJson: String = ""
    )

    private data class ResourceStep(
        val type: String,
        val title: String,
        val preview: String
    )

    private data class ResourceRecommendation(
        val resourceId: String,
        val label: String
    )

    private data class ResourceRequirementResolution(
        val requirement: String,
        val resource: ResourceItem?
    )

    private data class ResourceInstallPlan(
        val steps: List<ResourceItem>,
        val missing: List<ResourceRequirementResolution>
    )

    private enum class ResourceUninstallContinuation {
        None,
        Reinstall,
        CancelFailedInstall,
        ResumeInstallWizard
    }

    private data class ToolchainWorkspaceSnapshot(
        val nodeInstalled: Boolean = false,
        val toolchainInstalled: Boolean = false,
        val checkedAt: Long = 0L
    )

    private data class ResourceInstallWizardContext(
        val targetResourceId: String,
        val planResourceIds: List<String>,
        val wizardRecipeId: String,
        val wizardInstanceId: String,
        val selectedResourceId: String? = null,
        val selectedOperation: String = KiteResourceInstallRecipes.OP_INSTALL,
        val selectedSurface: CardRunSurface = CardRunSurface.InstallWizard
    )

    private data class CardRunReportBinding(
        val recipeId: String,
        val instanceId: String,
        val outputTextView: TextView?,
        val footerTextView: TextView?,
        val elapsedTextView: TextView?
    )

    private data class CardRunTopBarBinding(
        val displayRecipe: KiteRecipe,
        val actionRecipe: KiteRecipe,
        val actionInstanceId: String,
        val terminalSessionId: String,
        val authSlot: FrameLayout
    )

    private data class ResourceInstallWizardBinding(
        val targetResourceId: String,
        var planResourceIds: List<String>,
        val headerDetailTextView: TextView?,
        val headerProgressTextView: TextView?,
        val primaryActionHost: LinearLayout,
        val rowsHost: LinearLayout,
        val rowHosts: MutableMap<String, LinearLayout>,
        val rowBindings: MutableMap<String, ResourceInstallWizardRowBinding>
    )

    private data class ResourceInstallWizardRowBinding(
        val resourceId: String,
        val runOperation: String,
        val subtitleTextView: TextView,
        val statusTextView: TextView,
        val openButtonTextView: TextView?
    )

    private data class ResourceInstallWizardUiState(
        val targetId: String,
        val planIds: List<String>,
        val catalog: Map<String, ResourceItem>,
        val planSnapshot: KiteResourcePlanSnapshot,
        val registrySnapshot: Map<String, KiteResourceRegistryEntry>,
        val activeId: String?,
        val detail: String,
        val completedCount: Int,
        val hasRunningStep: Boolean,
        val hasUninstallingStep: Boolean,
        val hasPending: Boolean,
        val hasFailure: Boolean
    )

    private data class ResourceManagePayload(
        val catalog: List<ResourceItem>,
        val planSnapshot: KiteResourcePlanSnapshot,
        val planIds: List<String>,
        val registrySnapshot: Map<String, KiteResourceRegistryEntry>
    )

    private data class RecipeCardBinding(
        val recipeId: String,
        val statusHost: FrameLayout,
        val cueHost: FrameLayout,
        val actionHost: FrameLayout
    )

    private data class ResourceSectionsPayload(
        val query: String,
        val resources: List<ResourceItem>,
        val renderKey: String
    )

    private data class ResourcePreviewCard(
        val title: String,
        val subtitle: String,
        val symbol: String,
        val accent: String
    )

    private data class SummaryMetric(
        val label: String,
        val value: String,
        val weight: Float
    )

    private data class SemanticColors(
        val text: Int,
        val background: Int,
        val border: Int
    )

    private data class RecipeStatusBadge(
        val label: String,
        val color: Int,
        val background: Int
    )

    private data class RecipeCardTitleText(
        val text: String,
        val textSizeSp: Float
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
        RecipeMore,
        Resources,
        ResourceManage,
        ResourceDetail,
        ResourceMore,
        ResourceRawJson,
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
        private const val RESOURCE_HERMES_CORE = "kite.hermes.core"
        private const val RESOURCE_HERMES_WEBUI = "kite.hermes.webui"
        private const val RESOURCE_REASONIX = "kite.reasonix"
        private const val RESOURCE_GIT = "kite.git"
        private const val RESOURCE_CURL = "kite.curl"
        private const val RESOURCE_PYTHON = "kite.python"
        private const val RESOURCE_UV = "kite.uv"
        private const val RESOURCE_OPEN_RUNTIME_SOURCE = "resource_open"
        private const val RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE = "resource_install_wizard"
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private const val WEB_READY_TIMEOUT_MS = 8000L
        private const val WEB_READY_INTERVAL_MS = 700L
        private const val WEB_READY_CONNECT_TIMEOUT_MS = 700
        private const val WEB_READY_READ_TIMEOUT_MS = 700
        private const val TERMINAL_STEP_COMMAND_DELAY_MS = 650L
        private const val TERMINAL_STOP_GRACE_MS = 350L
        private const val RESOURCE_CATALOG_FORCE_REFRESH_GRACE_MS = 1_200L
        private const val TOOLCHAIN_WORKSPACE_PROBE_TTL_MS = 5_000L
        private const val TERMINAL_AUTH_LINK_POLL_MS = 1200L
        private const val TERMINAL_AUTH_LINK_WATCH_MS = 10L * 60L * 1000L
        private const val TERMINAL_AUTH_LINK_TAIL_BYTES = 64L * 1024L
        private const val REQUEST_DROPZONE_STORAGE = 801
        private const val REQUEST_PICK_RECIPE_ICON = 802
        private const val KEY_HIDE_MAIN_TASK_FROM_RECENTS = "hide_main_task_from_recents"
        private const val KEY_RESTORE_LAST_SCREEN = "restore_last_screen"
        private const val KEY_RECIPE_DRAFT = "recipe_draft"
        private const val KEY_RECIPE_DRAFT_SAVED_AT = "recipe_draft_saved_at"
        private const val KEY_RECIPE_ICON_COLLECTION = "recipe_icon_collection"
        private const val STATE_CURRENT_SCREEN = "kite_current_screen"
        private const val STATE_WORKBENCH_URL = "kite_workbench_url"
        private const val STATE_RECIPE_DRAFT = "kite_recipe_draft"
        private const val RECIPE_DRAFT_RESTORE_WINDOW_MS = 6L * 60L * 60L * 1000L
        private const val RECIPE_CARD_TITLE_MAX_TEXT_SP = 13.2f
        private const val RECIPE_CARD_TITLE_MIN_TEXT_SP = 10.0f
        private const val RECIPE_CARD_TITLE_SHRINK_AFTER_BYTES = 12
        private const val RECIPE_CARD_TITLE_MAX_BYTES = 20
        private const val RECIPE_CARD_TITLE_ELLIPSIS = "…"
        private val terminalFlowFinishedStatuses = setOf(
            ManagedTerminalStatus.EXITED,
            ManagedTerminalStatus.FAILED,
            ManagedTerminalStatus.STOPPED
        )
        private var activeResourceInstallWizard: ResourceInstallWizardContext? = null
    }
}
