package com.kite.app

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.Dialog
import android.app.NotificationManager
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabsIntent
import com.kite.app.action.KiteActionRoute
import com.kite.app.action.KiteActionRouter
import com.kite.app.action.KiteRecipeActionCoordinator
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionPlan
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.action.KiteResourceActionCoordinator
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunExecutionEffect
import com.kite.app.application.runs.RunExecutionEffectBus
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.browser.BrowserAuthRedirect
import com.kite.app.browser.BrowserAuthRedirectParser
import com.kite.app.browser.BrowserAuthSession
import com.kite.app.browser.BrowserAuthSessionKind
import com.kite.app.browser.BrowserAuthSessionStatus
import com.kite.app.browser.BrowserAuthSessionStore
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffLauncher
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.BrowserLoopbackCallbackBridge
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.browser.automation.BrowserAutomationAction
import com.kite.app.browser.automation.BrowserAutomationActionResult
import com.kite.app.browser.automation.BrowserAutomationActionScript
import com.kite.app.browser.automation.BrowserAutomationController
import com.kite.app.browser.automation.BrowserAutomationControllerRegistry
import com.kite.app.browser.automation.BrowserAutomationEvent
import com.kite.app.browser.automation.BrowserAutomationEventKind
import com.kite.app.browser.automation.BrowserAutomationResultStatus
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.bridge.BridgeErrorType
import com.kite.app.bridge.BridgeProgress
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.bridge.KiteBrowserOpenRequest
import com.kite.app.bridge.KiteBrowserProxyInstaller
import com.kite.app.bridge.KiteDesktopOpenRequest
import com.kite.app.bridge.KiteDesktopOpenResponse
import com.kite.app.bridge.KiteInstallApkRequest
import com.kite.app.bridge.KiteInstallApkResponse
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
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallPlanCompiler
import com.kite.app.resources.KiteResourceInstallSignal
import com.kite.app.resources.KiteResourceInstallSpec
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceDisplayRowSpec
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourcePreviewSpec
import com.kite.app.resources.KiteResourceRequestPolicy
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.resources.KiteResourceShellAction
import com.kite.app.resources.KiteResourceUiProjector
import com.kite.app.resources.KiteResourceRuntimeFacts
import com.kite.app.resources.KiteResourceRuntimeFactsProjector
import com.kite.app.run.CardRunState as RecipeRuntimeState
import com.kite.app.run.CardRunBrowserRouter
import com.kite.app.run.CardRunDesktopRouter
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunHistoryStep
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunStatus as RecipeRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.KiteX11SurfacePlan
import com.kite.app.run.KiteX11SurfaceServer
import com.kite.app.run.PendingTerminalFlow
import com.kite.app.run.KiteCardRunUiProjector
import com.kite.app.run.KiteRunUiTone
import com.kite.app.shell.AppDestination
import com.kite.app.shell.AppIntentRouter
import com.kite.app.shell.AppNavigator
import com.kite.app.shell.KiteAppGraph
import com.kite.app.shell.NavigationBackAction
import com.kite.app.shell.RestorePolicy
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.web.KiteWebShell
import com.kite.app.R
import com.kite.app.foundation.bootstrap.BootstrapCoordinator
import com.kite.app.foundation.bootstrap.BootstrapSnapshot
import com.kite.app.foundation.bootstrap.BootstrapStage
import com.kite.app.foundation.bootstrap.StartupTraceStore
import com.kite.app.foundation.runtime.AssetExtractor
import com.kite.app.foundation.runtime.ExternalExchangeManager
import com.kite.app.foundation.runtime.RuntimeAutomationActions
import com.kite.app.foundation.runtime.RuntimeBootstrapProgress
import com.kite.app.foundation.runtime.RuntimeBootstrapProgressSnapshot
import com.kite.app.foundation.runtime.RuntimeHealthStore
import com.kite.app.foundation.runtime.RuntimeReclaimer
import com.kite.app.foundation.runtime.TaskManagerProcessItem
import com.kite.app.foundation.runtime.TaskManagerSnapshot
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.foundation.runtime.TerminalSessionItem
import com.kite.app.foundation.runtime.TerminalSessionStore
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.terminal.TerminalRuntimeRegistry
import com.kite.app.foundation.contracts.ManagedTerminalStatus
import com.kite.app.foundation.toolchain.ToolchainInstallPhase
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.feature.resources.ResourceFeatureRequest
import com.kite.app.feature.resources.ResourceFeatureResultContract
import com.kite.app.feature.resources.ResourceDetailFragment
import com.kite.app.feature.resources.ResourceManageFragment
import com.kite.app.feature.resources.ResourceInstallWizardRunRequest
import com.kite.app.feature.resources.ResourceInstallWizardSurface
import com.kite.app.feature.resources.ResourceSearchFragment
import com.kite.app.feature.resources.ResourcesFragment
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.feature.home.HomeFeatureRequest
import com.kite.app.feature.home.HomeFeatureResultContract
import com.kite.app.feature.home.HomeFragment
import com.kite.app.feature.recipeeditor.RecipeEditorDraft
import com.kite.app.feature.recipeeditor.RecipeEditorFragment
import com.kite.app.feature.recipeeditor.RecipeEditorRequest
import com.kite.app.feature.recipeeditor.RecipeEditorResultContract
import com.kite.app.ui.terminal.KiteTerminalShellTheme
import com.kite.app.ui.terminal.TerminalChromeHost
import com.kite.app.ui.terminal.TerminalFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

open class MainActivity : AppCompatActivity(), TerminalChromeHost,
    RecipeRawJsonFragment.RecipeProvider,
    RecipeRawJsonFragment.RecipeRawJsonHost,
    RecipeRawJsonFragment.UiKitProvider {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var dropZoneManager: KiteDropZoneManager
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var webShell: KiteWebShell
    private lateinit var browserAuthSessions: BrowserAuthSessionStore
    private lateinit var browserLoopbackCallbackBridge: BrowserLoopbackCallbackBridge
    private lateinit var browserAutomationSessions: BrowserAutomationSessionStore
    private lateinit var browserAutomationController: BrowserAutomationController
    private lateinit var localServer: KiteLocalServer
    private lateinit var resourceInstallStore: KiteResourceInstallStore
    private lateinit var resourceManifestLoader: KiteResourceManifestLoader
    private lateinit var resourceFeatureGateway: ResourceFeatureGateway
    private lateinit var recipeFeatureGateway: RecipeFeatureGateway
    private lateinit var runOrchestrator: RunOrchestrator
    private lateinit var runExecutionEffectBus: RunExecutionEffectBus
    private lateinit var themeStore: SharedPreferences
    private lateinit var appSettings: SharedPreferences
    private lateinit var rootHost: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView
    private var activityDisplaySurfacesReleased = false

    private val runtimeStates = mutableMapOf<String, RecipeRuntimeState>()
    private val activeRunInstanceIds = mutableMapOf<String, String>()
    private val cardRunWindowHiddenSurfaces = mutableMapOf<String, MutableSet<String>>()
    private val actionRouter = KiteActionRouter()
    private val recipeActionCoordinator = KiteRecipeActionCoordinator(actionRouter)
    /**
     * AppDestination 路由收口(T6)。过渡期把 navigate 委托回老的 show* 方法;
     * 后续各 AppDestination 逐个 Fragment 化时,在此替换为 routeToFragment。
     */
    private val appNavigator: AppNavigator by lazy {
        AppNavigator(
            destinationSink = AppNavigator.DestinationSink { screen -> dispatchLegacyDestination(screen) }
        )
    }
    private val navigationBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleAppNavigationBack()
        }
    }
    private var currentScreen: AppDestination = AppDestination.Console
    private var pendingRawJsonRecipeId: String? = null

    /** 仅用于 Robolectric 路由合同断言。 */
    @androidx.annotation.VisibleForTesting
    internal fun currentScreenNameForTest(): String = currentScreen.name

    @androidx.annotation.VisibleForTesting
    internal fun enterScreenForTest(screenName: String) {
        val screen = AppDestination.valueOf(screenName)
        enterScreen(screen)
    }

    @androidx.annotation.VisibleForTesting
    internal fun activityDisplaySurfacesReleasedForTest(): Boolean = activityDisplaySurfacesReleased

    private fun enterScreen(screen: AppDestination, onBack: (() -> Unit)? = null) {
        currentScreen = screen
        appNavigator.enter(screen, onBack)
    }

    private var currentRecipes: List<KiteRecipe> = emptyList()
    private var dropZoneStatus: DropZoneStatus = DropZoneStatus(available = false, message = "投放区尚未检查")
    private var isDropZoneRefreshing = false
    private var themeConfig = ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
    private var tokens = KiteTheme.resolve(themeConfig)
    private val terminalContainerId = View.generateViewId()
    private val cardRunTerminalContainerId = View.generateViewId()
    private var terminalBottomNavigation: View? = null
    private var isTerminalDetailMode = false
    private var kfRuntimeBootstrapRequested = false
    private var pendingRuntimePermissionBootstrap = false
    private var runtimePermissionRequestInFlight = false
    private var firstRunRuntimeGateShown = false
    private var bootstrapResourceGateInFlight = false
    private var firstRunPermissionOnboardingInFlight = false
    private var firstRunPermissionRequestInFlight = false
    private var firstRunRuntimePermissionsRequested = false
    private var firstRunAllFilesSettingsOpened = false
    private var ubuntuRuntimeState = UbuntuRuntimeUiState.checking()
    private var pendingTerminalFlow: PendingTerminalFlow? = null
    private var localServerStarted = false
    private var consumedCardRunLaunchKey: String? = null
    private var focusedRunRecipeId: String? = null
    private var focusedRunInstanceId: String? = null
    private var registeredBrowserInstanceId: String? = null
    private var registeredDesktopInstanceId: String? = null
    private var registeredCardRunCloserInstanceId: String? = null
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
    private var runtimePanelActionRow: LinearLayout? = null
    private var runtimePanelActionChevronView: TextView? = null
    private var runtimePanelCardCountView: TextView? = null
    private var runtimePanelTerminalCountView: TextView? = null
    private var runtimePanelProcessCountView: TextView? = null
    private var runtimeGateOverlay: FrameLayout? = null
    private var runtimeGateTitleView: TextView? = null
    private var runtimeGateDetailView: TextView? = null
    private var runtimeGateProgressBar: ProgressBar? = null
    private var runtimeGateProgressTextView: TextView? = null
    private var runtimeGateActionButton: TextView? = null
    private val runManagementExpandedIds = mutableSetOf<String>()
    private val runManagementExpandedTerminalIds = mutableSetOf<String>()
    private val runManagementExpandedProcessIds = mutableSetOf<String>()
    private val runManagementExpandedChildProcessIds = mutableSetOf<String>()
    private val runManagementPendingProcessStopIds = mutableSetOf<Int>()
    private var autoOpenedRootfsRunAt = 0L
    private var lastWorkbenchUrl: String? = null
    private var currentResourceInstallTargetId: String? = null
    private var resourceInstallWizardPlanIds: List<String> = emptyList()
    private var resourceInstallPlanRequestSerial = 0L
    private val suppressedResourceRunSurfaceRecipeIds = mutableSetOf<String>()
    private val pendingResourceUninstallContinuations = mutableMapOf<String, ResourceUninstallContinuation>()
    private var cachedResourceCatalog: List<ResourceItem>? = null
    private var cachedResourceCatalogUpdatedAt = 0L
    private var resourceCatalogDirty = true
    private val resourceIconBitmapLock = Any()
    private val resourceIconBitmapCache = mutableMapOf<String, Bitmap>()
    private val resourceIconBitmapInFlight = mutableSetOf<String>()
    private val resourceIconBitmapWaiters = mutableMapOf<String, MutableList<(Bitmap) -> Unit>>()
    private val recipeIconBitmapCache = mutableMapOf<String, Bitmap>()
    private var resourceCatalogBackgroundRefreshInFlight = false
    private var cachedToolchainWorkspaceSnapshot = ToolchainWorkspaceSnapshot()
    private var resourceOpenRunSignature = ""
    private var resourceOpenRunStatusByResourceId: Map<String, RecipeRunStatus> = emptyMap()
    private var resourceRunUiRefreshPosted = false
    private var cardRunSurfaceSignature = ""
    private var cardRunReportBinding: CardRunReportBinding? = null
    private var cardRunReportRefreshScheduled = false
    private var cardRunReportLastRefreshAt = 0L
    private var pendingCardRunReportState: RecipeRuntimeState? = null
    private var resourceInstallWizardSurface: ResourceInstallWizardSurface? = null
    private var foregroundLiveTickScheduled = false
    private var consoleSystemStatusPillView: TextView? = null
    private var consoleRuntimeBannerHost: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTraceStore.markStage(this, "main.super_on_create")
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, navigationBackCallback)
        StartupTraceStore.markStage(this, "main.diagnostics_and_settings")
        val appGraph = KiteAppGraph.from(applicationContext)
        resourceFeatureGateway = appGraph.resourceFeatureGateway
        recipeFeatureGateway = appGraph.recipeFeatureGateway
        diagnostics = appGraph.diagnostics
        diagnostics.writeCapabilityReport()
        themeStore = getSharedPreferences("kite_theme", MODE_PRIVATE)
        appSettings = getSharedPreferences("kite_app_settings", MODE_PRIVATE)
        CardRunStore.initialize(applicationContext)
        runOrchestrator = appGraph.runOrchestrator
        runExecutionEffectBus = appGraph.runExecutionEffectBus
        themeConfig = loadThemeConfig()
        tokens = KiteTheme.resolve(themeConfig)
        applyKiteTerminalTheme()
        recipeLoader = appGraph.createRecipeLoader()
        dropZoneManager = appGraph.createDropZoneManager()
        dropZoneStatus = dropZoneManager.prepareDropZone()
        bridgeClient = appGraph.bridgeClient
        browserAuthSessions = appGraph.browserAuthSessions
        browserLoopbackCallbackBridge = appGraph.browserLoopbackCallbackBridge
        StartupTraceStore.markStage(this, "main.webview_create")
        webView = WebView(this)
        browserAutomationSessions = appGraph.browserAutomationSessions
        browserAutomationController = BrowserAutomationController(
            webView = webView,
            store = browserAutomationSessions,
            onEvent = { event ->
                runOnUiThread { handleBrowserAutomationEvent(event) }
            }
        )
        webShell = KiteWebShell(
            activity = this,
            webView = webView,
            diagnostics = diagnostics,
            onStatus = { },
            browserHandoffLauncher = BrowserHandoffLauncher { request, decision ->
                launchBrowserHandoff(request, decision)
            },
            browserAutomationController = browserAutomationController
        )
        StartupTraceStore.markStage(this, "main.resources_and_server")
        resourceInstallStore = appGraph.resourceInstallStore
        resourceManifestLoader = appGraph.resourceManifestLoader
        prewarmResourceCatalog()
        localServer = KiteLocalServer(
            context = applicationContext,
            diagnostics = diagnostics,
            openWeb = { request ->
                runOnUiThread { handleBrowserOpenRequest(request) }
            },
            openDesktop = { request ->
                handleDesktopOpenRequest(request)
            },
            installApk = { request ->
                handleInstallApkRequest(request)
            },
            browserAutomationAction = { action ->
                handleBrowserAutomationActionRequest(action)
            },
            browserAutomationEnabled = {
                browserRuntimeMode() == BrowserRuntimeMode.AutomationBrowser
            }
        )
        if (shouldStartLocalServer()) {
            localServer.start()
            localServerStarted = true
        }

        StartupTraceStore.markStage(this, "main.content_view")
        rootHost = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(tokens.pageBackground)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tokens.pageBackground)
        }
        rootHost.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(rootHost)
        registerResourceFeatureResults()
        registerHomeFeatureResults()
        registerRecipeEditorResults()
        StartupTraceStore.markStage(this, "main.observers_and_intent")
        updateRuntimeGateOverlay()
        observeUbuntuBootstrapState()
        observeRootfsExtractionProgress()
        observeRuntimeBootstrapProgress()
        observeTerminalFlowSignals()
        observeRunExecutionEffects()
        observeCardRunStoreSignals()
        observeRuntimePanelSummarySignals()
        observeResourceInstallSignals()
        applyRecentTaskVisibilitySetting()
        val handledLaunchIntent = AppIntentRouter.dispatch(
            intent,
            ::handleBrowserAuthRedirect,
            ::handleRuntimeAutomationIntent,
            ::handleCardRunLaunchIntent
        )
        if (!handledLaunchIntent && !restoreScreenFromBundle(savedInstanceState) && !restoreRecipeDraftFromSettings()) {
            showConsole()
        }
        refreshUbuntuRuntimeState()
        StartupTraceStore.markStage(this, "main.permissions_and_runtime_gate")
        if (!maybeStartFirstRunPermissionOnboarding()) {
            maybeStartFirstRunRuntimeGate()
        }
        if (!dropZoneStatus.available) {
            Toast.makeText(this, dropZoneStatus.message, Toast.LENGTH_LONG).show()
        }
    }

    protected open fun shouldStartLocalServer(): Boolean = true

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppIntentRouter.dispatch(
            intent,
            ::handleBrowserAuthRedirect,
            ::handleRuntimeAutomationIntent,
            ::handleCardRunLaunchIntent
        )
    }

    private fun handleBrowserAuthRedirect(sourceIntent: Intent?): Boolean {
        val data = sourceIntent?.data?.toString()?.takeIf { it.isNotBlank() } ?: return false
        val redirect = BrowserAuthRedirectParser.parse(data) ?: return false
        val session = browserAuthSessions.markReturned(redirect)
        if (session == null) {
            diagnostics.logRecipeEvent(
                "browser_auth_redirect_unmatched",
                null,
                mapOf("hasState" to (!redirect.state.isNullOrBlank()).toString())
            )
            Toast.makeText(this, "浏览器已返回，但没有匹配的登录会话", Toast.LENGTH_LONG).show()
            return true
        }
        deliverBrowserAuthRedirect(session, redirect)
        return true
    }

    private fun deliverBrowserAuthRedirect(session: BrowserAuthSession, redirect: BrowserAuthRedirect) {
        val recipe = session.recipeId
            ?.takeIf { it.isNotBlank() }
            ?.let { findRecipeById(it) ?: CardRunStore.registeredRecipe(it) }
        val instanceId = session.instanceId?.takeIf { it.isNotBlank() }
        if (recipe == null || instanceId == null) {
            browserAuthSessions.markFailed(session.sessionId, "missing_target")
            Toast.makeText(this, "浏览器已返回，但找不到发起登录的运行实例", Toast.LENGTH_LONG).show()
            diagnostics.logRecipeEvent(
                "browser_auth_redirect_missing_target",
                recipe,
                mapOf(
                    "sessionId" to session.sessionId,
                    "recipeId" to session.recipeId.orEmpty(),
                    "instanceId" to session.instanceId.orEmpty()
                )
            )
            return
        }

        val failed = session.status == BrowserAuthSessionStatus.Failed || !redirect.error.isNullOrBlank()
        val summary = if (failed) {
            "浏览器登录返回失败：${redirect.error ?: session.failureReason ?: "unknown"}"
        } else {
            "浏览器登录已返回，等待发起方确认登录状态"
        }
        val report = buildString {
            appendLine(summary)
            appendLine("sessionId=${session.sessionId}")
            appendLine("kind=${session.kind.name}")
            appendLine("state=${if (redirect.state.isNullOrBlank()) "missing" else "matched"}")
            appendLine("code=${if (redirect.code.isNullOrBlank()) "missing" else "present"}")
            if (!redirect.error.isNullOrBlank()) appendLine("error=${redirect.error}")
        }.trim()
        val updated = CardRunStore.update(
            recipe = recipe,
            status = if (failed) RecipeRunStatus.Failed else RecipeRunStatus.Opened,
            instanceId = instanceId,
            surface = CardRunSurface.Report,
            lastMeaningfulOutput = summary,
            lastError = if (failed) summary else null,
            shellReportText = report,
            clearNextActionUrl = true
        )
        if (failed) {
            browserAuthSessions.markFailed(session.sessionId, redirect.error ?: session.failureReason ?: "redirect_failed")
        } else {
            browserAuthSessions.markDelivered(session.sessionId)
        }
        activeRunInstanceIds[recipe.id] = instanceId
        runtimeStates[recipe.id] = updated
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        diagnostics.logRecipeAction(
            recipe,
            "browser_auth_redirect_delivered",
            mapOf(
                "instanceId" to instanceId,
                "sessionId" to session.sessionId,
                "kind" to session.kind.name,
                "hasCode" to (!redirect.code.isNullOrBlank()).toString(),
                "hasError" to (!redirect.error.isNullOrBlank()).toString()
            )
        )
        if (this is CardRunActivity) {
            title = recipe.name
            showCardRunSurface(recipe)
        } else {
            startActivity(
                CardRunIntents.launchIntent(
                    context = this,
                    recipeId = recipe.id,
                    instanceId = instanceId,
                    launchSource = CardRunIntents.SOURCE_BROWSER_PROXY,
                    autoStart = false
                )
            )
        }
    }

    private fun expireBrowserAuthSessionsOnResume() {
        browserAuthSessions.expirePending().forEach { session ->
            browserLoopbackCallbackBridge.stop(session.sessionId)
        }
        browserAuthSessions.forwardedLoopbackNeedingRuntimeSync().forEach { session ->
            if (updateForwardedLoopbackBrowserAuthSession(session)) {
                browserAuthSessions.markRuntimeNotified(session.sessionId)
            }
        }
        browserAuthSessions.expiredNeedingRuntimeSync().forEach { session ->
            if (updateExpiredBrowserAuthSession(session)) {
                browserAuthSessions.markRuntimeNotified(session.sessionId)
            }
        }
    }

    private fun updateForwardedLoopbackBrowserAuthSession(session: BrowserAuthSession): Boolean {
        val instanceId = session.instanceId?.takeIf { it.isNotBlank() } ?: return true
        val existing = CardRunStore.get(instanceId) ?: return true
        val recipe = session.recipeId
            ?.takeIf { it.isNotBlank() }
            ?.let { findRecipeById(it) ?: CardRunStore.registeredRecipe(it) }
            ?: findRecipeById(existing.recipeId)
            ?: CardRunStore.registeredRecipe(existing.recipeId)
            ?: return false
        val summary = "浏览器回调已交给登录发起方，正在由发起方确认登录结果"
        val updated = CardRunStore.update(
            recipe = recipe,
            status = existing.status,
            instanceId = instanceId,
            surface = existing.surface,
            currentStepIndex = existing.currentStepIndex,
            runId = existing.runId,
            terminalSessionId = existing.terminalSessionId,
            pid = existing.pid,
            rootPid = existing.rootPid,
            processGroupId = existing.processGroupId,
            systemSessionId = existing.systemSessionId,
            lastMeaningfulOutput = summary,
            lastError = existing.lastError,
            shellReportText = existing.shellReportText,
            nextActionUrl = existing.nextActionUrl
        )
        activeRunInstanceIds[recipe.id] = instanceId
        runtimeStates[recipe.id] = updated
        diagnostics.logRecipeEvent(
            "browser_loopback_callback_forwarded",
            recipe,
            mapOf(
                "instanceId" to instanceId,
                "sessionId" to session.sessionId,
                "channel" to session.callbackChannelStatus.name
            )
        )
        return true
    }

    private fun updateExpiredBrowserAuthSession(session: BrowserAuthSession): Boolean {
        val instanceId = session.instanceId?.takeIf { it.isNotBlank() }
        if (instanceId == null) {
            val recipe = session.recipeId
                ?.takeIf { it.isNotBlank() }
                ?.let { findRecipeById(it) ?: CardRunStore.registeredRecipe(it) }
            diagnostics.logRecipeEvent(
                "browser_auth_session_expired_missing_instance",
                recipe,
                mapOf(
                    "sessionId" to session.sessionId,
                    "kind" to session.kind.name,
                    "recipeId" to session.recipeId.orEmpty()
                )
            )
            return true
        }

        val existing = CardRunStore.get(instanceId)
        if (existing == null) {
            val recipe = session.recipeId
                ?.takeIf { it.isNotBlank() }
                ?.let { findRecipeById(it) ?: CardRunStore.registeredRecipe(it) }
            diagnostics.logRecipeEvent(
                "browser_auth_session_expired_no_active_run",
                recipe,
                mapOf(
                    "instanceId" to instanceId,
                    "sessionId" to session.sessionId,
                    "kind" to session.kind.name,
                    "recipeId" to session.recipeId.orEmpty()
                )
            )
            return true
        }
        val recipe = session.recipeId
            ?.takeIf { it.isNotBlank() }
            ?.let { findRecipeById(it) ?: CardRunStore.registeredRecipe(it) }
            ?: findRecipeById(existing.recipeId)
            ?: CardRunStore.registeredRecipe(existing.recipeId)
        if (recipe == null) {
            diagnostics.logRecipeEvent(
                "browser_auth_session_expired_missing_target",
                null,
                mapOf(
                    "sessionId" to session.sessionId,
                    "kind" to session.kind.name,
                    "recipeId" to session.recipeId.orEmpty(),
                    "instanceId" to instanceId
                )
            )
            return false
        }
        val preserveTerminalSurface = session.kind == BrowserAuthSessionKind.CliLoopback &&
            existing.surface == CardRunSurface.Terminal &&
            !existing.terminalSessionId.isNullOrBlank()
        val summary = if (session.kind == BrowserAuthSessionKind.CliLoopback) {
            "未在等待时间内确认浏览器回调，登录结果请以发起方终端为准"
        } else {
            "浏览器登录等待超时，请重新打开登录页"
        }
        val report = buildString {
            appendLine(summary)
            appendLine("sessionId=${session.sessionId}")
            appendLine("kind=${session.kind.name}")
            appendLine("reason=${session.failureReason ?: "expired"}")
            appendLine("callbackChannel=${session.callbackChannelStatus.name}")
            session.redirectUri?.takeIf { it.isNotBlank() }?.let { appendLine("redirectUri=$it") }
        }.trim()
        val updated = CardRunStore.update(
            recipe = recipe,
            status = if (preserveTerminalSurface) {
                existing.status
            } else {
                RecipeRunStatus.Failed
            },
            instanceId = instanceId,
            surface = if (preserveTerminalSurface) CardRunSurface.Terminal else CardRunSurface.Report,
            currentStepIndex = existing.currentStepIndex,
            runId = existing.runId,
            terminalSessionId = existing.terminalSessionId,
            pid = existing.pid,
            rootPid = existing.rootPid,
            processGroupId = existing.processGroupId,
            systemSessionId = existing.systemSessionId,
            lastMeaningfulOutput = summary,
            lastError = if (preserveTerminalSurface) null else summary,
            shellReportText = if (preserveTerminalSurface) existing.shellReportText else report,
            clearNextActionUrl = true
        )
        activeRunInstanceIds[recipe.id] = instanceId
        runtimeStates[recipe.id] = updated
        diagnostics.logRecipeEvent(
            "browser_auth_session_expired",
            recipe,
            mapOf(
                "instanceId" to instanceId,
                "sessionId" to session.sessionId,
                "kind" to session.kind.name,
                "preserveTerminalSurface" to preserveTerminalSurface.toString()
            )
        )
        return true
    }

    private fun handleRuntimeAutomationIntent(sourceIntent: Intent?): Boolean {
        val intent = sourceIntent ?: return false
        val runtimeAction = intent
            .getStringExtra(AppIntentRouter.EXTRA_RUNTIME_ACTION)
            ?.trim()
            .orEmpty()
        if (runtimeAction.isBlank()) return false

        if (!RuntimeAutomationActions.isEnabled(applicationContext, runtimeAction)) {
            clearRuntimeAutomationExtras(intent)
            return true
        }
        when (runtimeAction.lowercase()) {
            ACTION_DUMP_DIAGNOSTICS -> RuntimeAutomationActions.dumpDiagnostics(applicationContext)
            ACTION_ROTATE_PROOT_TELEMETRY -> RuntimeAutomationActions.rotateProotTelemetry(applicationContext)
            ACTION_REFRESH_PROOT_TELEMETRY_HEARTBEAT -> {
                RuntimeAutomationActions.refreshProotTelemetryHeartbeat(applicationContext)
            }
            ACTION_PREPARE_PROOT_LIVE_TRACEE_PROBE -> RuntimeAutomationActions.prepareProotLiveTraceeProbe(
                context = applicationContext,
                targetLiveTracees = readProbeTargetLiveTracees(intent)
            )
            ACTION_INJECT_PROOT_LIVE_TRACEE_PROBE -> RuntimeAutomationActions.injectProotLiveTraceeProbe(
                context = applicationContext,
                targetLiveTracees = readProbeTargetLiveTracees(intent)
            )
            ACTION_STOP_BACKGROUND_RUNTIME -> stopBackgroundRuntimeFromAutomation(intent)
            ACTION_RECLAIM_OWNER_RUNTIME -> reclaimOwnerRuntimeFromAutomation(intent)
            ACTION_START_RESOURCE_OWNER_PROBE -> startResourceOwnerProbeFromAutomation(intent)
            ACTION_START_RESOURCE_INSTALL -> startResourceInstallFromAutomation(intent)
            ACTION_START_RESOURCE_OPEN -> startResourceOpenFromAutomation(intent)
            ACTION_STOP_CARD_RUN -> stopCardRunFromAutomation(intent)
            else -> diagnostics.logRecipeEvent(
                "kite_runtime_automation_ignored",
                null,
                mapOf("runtimeAction" to runtimeAction)
            )
        }
        clearRuntimeAutomationExtras(intent)
        return true
    }

    private fun clearRuntimeAutomationExtras(intent: Intent) {
        intent.removeExtra(AppIntentRouter.EXTRA_RUNTIME_ACTION)
        intent.removeExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES)
        intent.removeExtra(EXTRA_AUTOMATION_OWNER_ID)
        intent.removeExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_TARGET_ID)
        setIntent(intent)
    }

    private fun stopBackgroundRuntimeFromAutomation(sourceIntent: Intent?) {
        val runtimeId = sourceIntent
            ?.getStringExtra(EXTRA_AUTOMATION_RUNTIME_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_stop_missing_runtime",
                null,
                emptyMap()
            )
        TaskManagerStore.stopRuntime(applicationContext, runtimeId)
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_stop_background_runtime",
            null,
            mapOf("runtimeId" to runtimeId)
        )
    }

    private fun reclaimOwnerRuntimeFromAutomation(sourceIntent: Intent?) {
        val ownerId = sourceIntent
            ?.getStringExtra(EXTRA_AUTOMATION_OWNER_ID)
            ?.trim()
            .orEmpty()
        if (ownerId.isBlank()) {
            return diagnostics.logRecipeEvent(
                "kite_runtime_automation_reclaim_missing_owner",
                null,
                emptyMap()
            )
        }
        if (!ownerId.isAutomationRuntimeOwnerId()) {
            return diagnostics.logRecipeEvent(
                "kite_runtime_automation_reclaim_invalid_owner",
                null,
                mapOf("ownerId" to ownerId)
            )
        }
        val result = RuntimeReclaimer.reclaimOwnerRuntime(
            context = applicationContext,
            ownerId = ownerId,
            title = ownerId,
            reason = "adb-owner-reclaim"
        )
        RuntimeHealthStore.refresh(applicationContext, reason = "adb-owner-reclaim")
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_reclaim_owner_runtime",
            null,
            mapOf(
                "ownerId" to ownerId,
                "executed" to result.executed.toString(),
                "reason" to result.reason,
                "signal" to result.signal,
                "targetMode" to result.targetMode
            )
        )
    }

    private fun readProbeTargetLiveTracees(intent: Intent?): Int {
        if (intent == null || !intent.hasExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES)) return 4
        val rawInt = intent.getIntExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES, Int.MIN_VALUE)
        if (rawInt != Int.MIN_VALUE) return rawInt.coerceAtLeast(0)
        return intent.getStringExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES)
            ?.trim()
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 4
    }

    private fun String.isAutomationRuntimeOwnerId(): Boolean {
        return startsWith("card:") ||
            startsWith("resource:") ||
            startsWith("terminal:")
    }

    private fun stopCardRunFromAutomation(sourceIntent: Intent?) {
        val recipeId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_stop_missing_recipe",
                null,
                emptyMap()
            )
        val instanceId = sourceIntent
            .getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: recipeId
        val recipes = recipeLoader.loadAllRecipes()
        currentRecipes = recipes
        val recipe = recipes.firstOrNull { it.id == recipeId }
            ?: CardRunStore.registeredRecipe(recipeId)
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_stop_missing_recipe",
                null,
                mapOf("recipeId" to recipeId, "instanceId" to instanceId)
            )
        val state = CardRunStore.get(instanceId)
            ?: runtimeStates[recipe.id]?.takeIf { it.instanceId == instanceId || it.cardInstanceId == instanceId }
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_stop_missing_state",
                recipe,
                mapOf("recipeId" to recipeId, "instanceId" to instanceId)
            )
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_stop_card_run",
            recipe,
            mapOf("recipeId" to recipeId, "instanceId" to instanceId, "status" to state.status.name)
        )
        stopRecipeByCardInstanceId(recipe, state.cardInstanceId, state)
    }

    private fun startResourceOwnerProbeFromAutomation(sourceIntent: Intent?) {
        val resourceId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_TARGET_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { KiteResourceInstallRecipes.safeId(it) }
            ?: RESOURCE_OWNER_PROBE_ID
        val recipe = resourceOwnerProbeRecipe(resourceId)
        val instanceId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "resource-owner-probe-$resourceId"
        CardRunStore.registerRecipe(recipe)
        activeRunInstanceIds[recipe.id] = instanceId
        val state = CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_RESOURCE,
            stepId = resourceId
        )
        runtimeStates[recipe.id] = state
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_start_resource_owner_probe",
            recipe,
            mapOf("resourceId" to resourceId, "instanceId" to instanceId)
        )
        startRecipe(
            recipe = recipe,
            previousState = state,
            preferredInstanceId = instanceId,
            openConsoleOnStart = false,
            renderOnStart = false,
            keepCurrentFocus = true
        )
    }

    private fun startResourceOpenFromAutomation(sourceIntent: Intent?) {
        val resourceId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_TARGET_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { KiteResourceInstallRecipes.safeId(it) }
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_resource_open_missing_id",
                null,
                emptyMap()
            )
        invalidateResourceCatalogCache()
        val item = resourceCatalog(forceRefresh = true).firstOrNull { it.id == resourceId }
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_resource_open_missing_resource",
                null,
                mapOf("resourceId" to resourceId)
            )
        val recipe = resourceOpenRecipe(item)
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_resource_open_missing_recipe",
                null,
                mapOf("resourceId" to resourceId)
            )
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_resource_open_start",
            recipe,
            mapOf("resourceId" to resourceId)
        )
        startResourceOpen(item, recipe)
    }

    private fun startResourceInstallFromAutomation(sourceIntent: Intent?) {
        val resourceId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_TARGET_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { KiteResourceInstallRecipes.safeId(it) }
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_resource_install_missing_id",
                null,
                emptyMap()
            )
        invalidateResourceCatalogCache()
        val item = resourceCatalog(forceRefresh = true).firstOrNull { it.id == resourceId }
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_resource_install_missing_resource",
                null,
                mapOf("resourceId" to resourceId)
            )
        val recipe = resourceInstallRecipe(item)
            ?: return diagnostics.logRecipeEvent(
                "kite_runtime_automation_resource_install_missing_recipe",
                null,
                mapOf("resourceId" to resourceId)
            )
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_resource_install_start",
            recipe,
            mapOf("resourceId" to resourceId)
        )
        cacheResourceExecutionManifest(item.id)
        startResourceInstall(item, recipe)
    }

    private fun resourceOwnerProbeRecipe(resourceId: String): KiteRecipe {
        val step = KiteRecipeStep(
            id = "resource_owner_probe_$resourceId",
            type = KiteRecipe.STEP_SHELL,
            cmd = "bash -lc 'echo KITE_RESOURCE_OWNER_PROBE_START; sleep 600'",
            surfaceMode = KiteRecipe.SURFACE_MODE_SILENT,
            workdir = "/workspace",
            timeoutMs = 900_000L
        )
        return KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = resourceId,
                name = "Resource owner probe",
                description = "Debug resource owner telemetry probe",
                category = "resource",
                operation = KiteResourceInstallRecipes.OP_INSTALL,
                actionLabel = "Probe",
                steps = listOf(step)
            )
        )
    }

    override fun onResume() {
        super.onResume()
        StartupTraceStore.markStage(this, "main.resume_permissions")
        applyRecentTaskVisibilitySetting()
        resumePendingFirstRunPermissionOnboarding()
        resumePendingRuntimePermissionBootstrap()
        StartupTraceStore.markStage(this, "main.resume_runtime_and_visible_state")
        rebindVisibleResourceStateOnResume()
        ensureBundledToolBootstrapIfNeeded("resume")
        expireBrowserAuthSessionsOnResume()
        if (this is CardRunActivity && rebindFocusedCardRunSurface("resume")) {
            rootHost.post { StartupTraceStore.markReady(applicationContext) }
            return
        }
        when (currentScreen) {
            AppDestination.Console -> resumeConsoleSurface()
            AppDestination.Settings -> showSettings()
            else -> Unit
        }
        rootHost.post { StartupTraceStore.markReady(applicationContext) }
    }

    override fun onPause() {
        restoreCardRunSystemBarsIfNeeded()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_SCREEN, currentScreen.name)
        lastWorkbenchUrl?.let { outState.putString(STATE_WORKBENCH_URL, it) }
        (supportFragmentManager.findFragmentByTag(TAG_RECIPE_EDITOR_FRAGMENT) as? RecipeEditorFragment)
            ?.currentDraftRaw()
            ?.let { outState.putString(STATE_RECIPE_DRAFT, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        restoreCardRunSystemBarsIfNeeded()
        releaseResourceInstallWizardSurfaceIfActivityDestroyed()
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
        CardRunTaskCloser.unregister(registeredCardRunCloserInstanceId)
        releaseActivityDisplaySurfaces()
        if (localServerStarted) {
            localServer.stop()
        }
        super.onDestroy()
    }

    private fun releaseActivityDisplaySurfaces() {
        if (activityDisplaySurfacesReleased) return
        activityDisplaySurfacesReleased = true
        resourceInstallWizardSurface?.dispose()
        resourceInstallWizardSurface = null
        if (::browserAutomationController.isInitialized) {
            browserAutomationController.closeActiveSession()
        }
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    private fun requestNavigationBack() {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun handleAppNavigationBack() {
        if (handleWebViewBackSignal()) return
        when (val action = appNavigator.resolveBack(this is CardRunActivity)) {
            NavigationBackAction.System -> dispatchSystemBack()
            NavigationBackAction.CardRunTask -> handleCardRunBackSignal()
            NavigationBackAction.Contextual -> appNavigator.invokeContextualBack()
            is NavigationBackAction.Navigate -> appNavigator.navigate(action.destination)
        }
    }

    private fun dispatchSystemBack() {
        navigationBackCallback.isEnabled = false
        try {
            onBackPressedDispatcher.onBackPressed()
        } finally {
            navigationBackCallback.isEnabled = true
        }
    }

    private fun handleWebViewBackSignal(): Boolean {
        if (!::webView.isInitialized) return false
        if (currentScreen != AppDestination.Workbench && currentScreen != AppDestination.CardRun) return false
        if (webView.parent == null || !webView.canGoBack()) return false
        webView.goBack()
        return true
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
        val existingLaunchState = CardRunStore.get(instanceId)
        if (
            consumedCardRunLaunchKey == launchKey &&
            !shouldRestartConsumedCardRunLaunch(autoStart, existingLaunchState)
        ) {
            if (this is CardRunActivity) {
                rebindCardRunLaunchSurface(sourceIntent, recipeId, instanceId, "duplicate_launch")
            }
            return true
        }
        consumedCardRunLaunchKey = launchKey

        val isResourceInstallWizardLaunch = launchSource == CardRunIntents.SOURCE_RESOURCE_INSTALL ||
            recipeId.startsWith("resource-install-wizard-")
        if (isResourceInstallWizardLaunch) {
            if (showResourceInstallWizardFromIntent(sourceIntent, recipeId, instanceId)) {
                return true
            }
            showResourceDiscreteToast("获取向导缺少队列信息")
            if (this is CardRunActivity) finish()
            return true
        }

        val recipes = recipeLoader.loadAllRecipes()
        currentRecipes = recipes
        val recipe = resolveCardRunLaunchRecipe(sourceIntent, recipeId)
        if (recipe == null) {
            Toast.makeText(this, "未找到卡片：$recipeId", Toast.LENGTH_SHORT).show()
            diagnostics.logRecipeEvent("card_run_launch_missing_recipe", null, mapOf("recipeId" to recipeId))
            return true
        }
        CardRunStore.registerRecipe(recipe)

        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        title = recipe.name
        applyCardTaskDescription(recipe)
        val state = CardRunStore.get(instanceId) ?: CardRunStore.start(recipe, instanceId)
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        registerCardRunBrowserHandler(recipe, instanceId)
        registerCardRunDesktopHandler(recipe, instanceId)
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

    private fun shouldRestartConsumedCardRunLaunch(autoStart: Boolean, state: RecipeRuntimeState?): Boolean {
        if (!autoStart) return false
        return state == null ||
            state.status == RecipeRunStatus.Stopped ||
            state.status == RecipeRunStatus.Completed ||
            state.status == RecipeRunStatus.Failed ||
            state.status == RecipeRunStatus.BridgeUnavailable ||
            state.status == RecipeRunStatus.Unknown
    }

    private fun resolveCardRunLaunchRecipe(sourceIntent: Intent?, recipeId: String): KiteRecipe? {
        val recipes = currentRecipes.takeIf { it.isNotEmpty() } ?: recipeLoader.loadAllRecipes().also {
            currentRecipes = it
        }
        return recipes.firstOrNull { it.id == recipeId }
            ?: CardRunStore.registeredRecipe(recipeId)
            ?: temporaryRecipeFromIntent(sourceIntent, recipeId)
            ?: resourceOpenRecipeFromLaunchRecipeId(recipeId)
    }

    private fun resourceOpenRecipeFromLaunchRecipeId(recipeId: String): KiteRecipe? {
        val resourceId = resourceIdForOpenRunRecipeId(recipeId) ?: return null
        return runCatching {
            resourceCatalog(forceRefresh = false)
                .firstOrNull { it.id == resourceId }
                ?.let { resourceOpenRecipe(it) }
        }.getOrNull()
    }

    private fun rebindFocusedCardRunSurface(reason: String): Boolean {
        val recipeId = focusedRunRecipeId
            ?.takeIf { it.isNotBlank() }
            ?: intent?.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID)?.takeIf { it.isNotBlank() }
            ?: return false
        val instanceId = focusedRunInstanceId
            ?.takeIf { it.isNotBlank() }
            ?: intent?.getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID)?.takeIf { it.isNotBlank() }
            ?: recipeId
        return rebindCardRunLaunchSurface(intent, recipeId, instanceId, reason)
    }

    private fun rebindCardRunLaunchSurface(
        sourceIntent: Intent?,
        recipeId: String,
        instanceId: String,
        reason: String
    ): Boolean {
        val recipe = resolveCardRunLaunchRecipe(sourceIntent, recipeId) ?: return false
        CardRunStore.registerRecipe(recipe)
        val state = CardRunStore.get(instanceId) ?: CardRunStore.currentForRecipe(recipe.id)
        val resolvedInstanceId = state?.instanceId ?: instanceId
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = resolvedInstanceId
        activeRunInstanceIds[recipe.id] = resolvedInstanceId
        state?.let { runtimeStates[recipe.id] = it }
        title = recipe.name
        applyCardTaskDescription(recipe)
        registerCardRunBrowserHandler(recipe, resolvedInstanceId)
        registerCardRunDesktopHandler(recipe, resolvedInstanceId)
        registerCardRunTaskCloser(resolvedInstanceId)
        diagnostics.logRecipeAction(
            recipe,
            "card_run_task_rebound",
            mapOf("instanceId" to resolvedInstanceId, "reason" to reason)
        )
        showCardRunSurface(recipe)
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

    private fun temporaryDesktopRecipe(recipeId: String, command: String, title: String = "临时桌面"): KiteRecipe =
        KiteRecipe(
            id = recipeId,
            name = title,
            description = "由 Ubuntu 桌面请求临时打开",
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "temporary",
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep(id = "desktop_$recipeId", type = KiteRecipe.STEP_X11, cmd = command))
            ),
            actions = linkedMapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = listOf(KiteRecipeStep(id = "desktop_$recipeId", type = KiteRecipe.STEP_X11, cmd = command))
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

    private fun observeRuntimePanelSummarySignals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TerminalSessionStore.snapshot.collect {
                    renderRuntimePanelCounts()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TaskManagerStore.snapshot.collect {
                    renderRuntimePanelCounts()
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

    private fun observeRunExecutionEffects() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                runExecutionEffectBus.effects.collect { effect ->
                    when (effect) {
                        is RunExecutionEffect.OpenWeb -> handleRunOpenWebEffect(effect)
                        is RunExecutionEffect.StopResolved -> handleRunStopResolvedEffect(effect)
                    }
                }
            }
        }
    }

    private fun handleRunStopResolvedEffect(effect: RunExecutionEffect.StopResolved) {
        val state = CardRunStore.get(effect.instanceId) ?: return
        if (state.recipeId != effect.recipeId) return
        val recipe = recipeForRunState(state) ?: return
        activeRunInstanceIds[recipe.id] = state.instanceId
        diagnostics.logRecipeAction(
            recipe,
            "run_stop_resolved",
            mapOf(
                "instanceId" to state.instanceId,
                "stopped" to effect.stopped.toString(),
                "status" to state.status.name,
                "message" to effect.message.take(500)
            )
        )
        if (!effect.stopped && effect.message.isNotBlank()) {
            Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleRunOpenWebEffect(effect: RunExecutionEffect.OpenWeb) {
        val state = CardRunStore.get(effect.instanceId) ?: return
        if (state.recipeId != effect.recipeId || state.nextActionUrl != effect.url) return
        val recipe = findRecipeById(effect.recipeId)
            ?: CardRunStore.registeredRecipe(effect.recipeId)
            ?: return
        val step = recipe.steps.getOrNull(state.currentStepIndex) ?: return
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        diagnostics.logRecipeAction(
            recipe,
            "run_open_web_effect",
            mapOf(
                "instanceId" to state.instanceId,
                "stepIndex" to state.currentStepIndex.toString(),
                "surfaceMode" to effect.surfaceMode,
                "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(effect.url)
            )
        )
        if (!shouldOpenStepSurface(recipe, step)) return
        if (shouldRenderInCardRun(recipe)) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = state.instanceId
            showCardRunSurface(recipe)
        } else {
            openWeb(effect.url, "recipe_orchestrator", recipe)
        }
    }

    private fun observeCardRunStoreSignals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CardRunStore.runs.collect { runs ->
                    val reportBinding = cardRunReportBinding
                    if (reportBinding != null && currentScreen == AppDestination.CardRun) {
                        val state = runs.firstOrNull { it.instanceId == reportBinding.instanceId }
                        val recipe = state?.let { recipeForRunState(it) }
                        if (state != null && recipe != null) {
                            runtimeStates[state.recipeId] = state
                            updateVisibleCardRunReport(state)
                        }
                    }
                    rebindVisibleCardRunSurfaceFromStore(runs)
                    consumeResourceOpenRunSignals(runs)
                    renderRuntimePanelCounts()
                    resourceInstallWizardSurface?.tick()
                }
            }
        }
    }

    private fun rebindVisibleCardRunSurfaceFromStore(runs: List<RecipeRuntimeState>) {
        if (currentScreen != AppDestination.CardRun) return
        val instanceId = focusedRunInstanceId?.takeIf { it.isNotBlank() } ?: return
        val state = runs.firstOrNull { it.instanceId == instanceId } ?: return
        val recipe = recipeForRunState(state) ?: return
        val wizardChildRun = resourceInstallWizardSelectedRun(recipe, state.surface)
        val actionRecipe = wizardChildRun?.first ?: recipe
        val surfaceState = wizardChildRun?.second ?: state
        val surfaceSignature = cardRunSurfaceSignature(recipe, state, actionRecipe, surfaceState)
        if (surfaceSignature == cardRunSurfaceSignature) return
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = state.instanceId
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        showCardRunSurface(recipe)
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
        syncVisibleResourceState(signal.reason)
    }

    private fun syncVisibleResourceState(@Suppress("UNUSED_PARAMETER") reason: String) {
        // 每个 Activity 都有自己的资源缓存。即使当前不在资源页，也要先标脏，
        // 避免稍后进入资源页时继续显示状态变化前的按钮文字。
        invalidateResourceRuntimeStateCache()
        when (currentScreen) {
            AppDestination.Resources,
            AppDestination.ResourceSearch -> Unit
            AppDestination.CardRun -> Unit
            AppDestination.ResourceDetail -> Unit
            AppDestination.ResourceMore -> Unit
            AppDestination.ResourceManage -> Unit
            else -> Unit
        }
    }

    private fun consumeResourceOpenRunSignals(runs: List<RecipeRuntimeState>) {
        val signature = buildResourceOpenRunSignature(runs)
        if (signature == resourceOpenRunSignature) return
        val nextStatuses = runs
            .asSequence()
            .mapNotNull { state ->
                resourceIdForOpenRunRecipeId(state.recipeId)?.let { resourceId -> resourceId to state }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, states) -> states.maxByOrNull { it.updatedAt }?.status }
            .mapNotNull { (resourceId, status) -> status?.let { resourceId to it } }
            .toMap()
        resourceOpenRunStatusByResourceId = nextStatuses
        resourceOpenRunSignature = signature
        invalidateResourceRuntimeStateCache()
        syncVisibleResourceState("resource_open_run_state")
    }

    private fun buildResourceOpenRunSignature(runs: List<RecipeRuntimeState>): String =
        runs
            .asSequence()
            .mapNotNull { state ->
                val resourceId = resourceIdForOpenRunRecipeId(state.recipeId) ?: return@mapNotNull null
                resourceId to state
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, states) -> states.maxByOrNull { it.updatedAt } }
            .toSortedMap()
            .mapNotNull { (resourceId, state) ->
                state?.let { "$resourceId:${it.instanceId}:${it.status.name}" }
            }
            .joinToString("|")

    private fun requestResourceRunStateUiRefresh() {
        if (this is CardRunActivity || !::root.isInitialized) return
        when (currentScreen) {
            AppDestination.Resources,
            AppDestination.ResourceSearch,
            AppDestination.ResourceDetail,
            AppDestination.ResourceMore,
            AppDestination.ResourceManage -> Unit
            else -> return
        }
        if (resourceRunUiRefreshPosted) return
        resourceRunUiRefreshPosted = true
        root.post {
            resourceRunUiRefreshPosted = false
            when (currentScreen) {
                AppDestination.Resources,
                AppDestination.ResourceSearch,
                AppDestination.ResourceDetail -> Unit
                AppDestination.ResourceMore -> invalidateResourceRuntimeStateCache()
                AppDestination.ResourceManage -> Unit
                else -> Unit
            }
        }
    }

    private fun rebindVisibleResourceStateOnResume() {
        if (this is CardRunActivity || !::root.isInitialized || !::resourceInstallStore.isInitialized) return
        when (currentScreen) {
            AppDestination.Resources,
            AppDestination.ResourceSearch,
            AppDestination.ResourceDetail,
            AppDestination.ResourceMore,
            AppDestination.ResourceManage -> syncVisibleResourceState("resume")
            else -> Unit
        }
    }

    private fun recipeForRunState(state: RecipeRuntimeState): KiteRecipe? =
        CardRunStore.registeredRecipe(state.recipeId)
            ?: currentRecipes.firstOrNull { it.id == state.recipeId }

    private fun maybeStartFirstRunPermissionOnboarding(): Boolean {
        if (this is CardRunActivity) return false
        if (appSettings.getBoolean(KEY_FIRST_RUN_PERMISSION_ONBOARDING_DONE, false)) return false
        appSettings.edit().putBoolean(KEY_FIRST_RUN_PERMISSION_ONBOARDING_DONE, true).apply()
        firstRunPermissionOnboardingInFlight = true
        firstRunRuntimePermissionsRequested = false
        firstRunAllFilesSettingsOpened = false
        setUbuntuRuntimeState(buildFirstRunPermissionOnboardingUiState())
        showUbuntuRuntimePanel(auto = true)
        continueFirstRunPermissionOnboarding()
        return true
    }

    private fun continueFirstRunPermissionOnboarding() {
        if (!firstRunPermissionOnboardingInFlight || firstRunPermissionRequestInFlight) return
        val state = currentFirstRunPermissionState()
        setUbuntuRuntimeState(buildFirstRunPermissionOnboardingUiState(state))
        when {
            state.runtimePermissionsToRequest.isNotEmpty() && !firstRunRuntimePermissionsRequested -> {
                firstRunRuntimePermissionsRequested = true
                firstRunPermissionRequestInFlight = true
                requestPermissions(
                    state.runtimePermissionsToRequest.toTypedArray(),
                    REQUEST_FIRST_RUN_PERMISSION_ONBOARDING
                )
            }
            state.needsAllFilesAccess && !firstRunAllFilesSettingsOpened -> {
                firstRunAllFilesSettingsOpened = true
                openAllFilesAccessSettings()
            }
            else -> completeFirstRunPermissionOnboarding()
        }
    }

    private fun resumePendingFirstRunPermissionOnboarding() {
        if (!firstRunPermissionOnboardingInFlight || firstRunPermissionRequestInFlight) return
        continueFirstRunPermissionOnboarding()
    }

    private fun completeFirstRunPermissionOnboarding() {
        if (!firstRunPermissionOnboardingInFlight) return
        firstRunPermissionOnboardingInFlight = false
        firstRunPermissionRequestInFlight = false
        firstRunRuntimePermissionsRequested = false
        firstRunAllFilesSettingsOpened = false
        dropZoneStatus = dropZoneManager.prepareDropZone()
        maybeStartFirstRunRuntimeGate()
        if (currentScreen == AppDestination.Console) showConsole()
    }

    private fun currentFirstRunPermissionState(): FirstRunPermissionState {
        val runtimePermissions = mutableListOf<String>()
        runtimePermissions += currentRuntimePermissionState().missingPermissions
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            runtimePermissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return FirstRunPermissionState(
            runtimePermissionsToRequest = runtimePermissions.distinct(),
            needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
        )
    }

    private fun buildFirstRunPermissionOnboardingUiState(
        state: FirstRunPermissionState = currentFirstRunPermissionState()
    ): UbuntuRuntimeUiState {
        val labels = state.labels()
        return UbuntuRuntimeUiState(
            title = "首次授权",
            detail = listOf(
                "Kite 会先集中申请一次系统能力：${labels.joinToString("、")}。",
                "如果你暂时拒绝，Kite 不会反复打扰；之后 Ubuntu 解压、通知、桌面图标会在各自入口继续提示。",
                "完成授权后会继续检查 Ubuntu 基础环境。"
            ).joinToString("\n"),
            blocksUbuntuActions = true,
            isProblem = false,
            canRetry = true,
            firstRunPermissionOnboarding = true,
            permissionActionLabel = "开始授权"
        )
    }

    private fun FirstRunPermissionState.labels(): List<String> =
        buildList {
            if (needsAllFilesAccess) add("全部文件访问")
            runtimePermissionsToRequest
                .map(::runtimePermissionLabel)
                .filter { it.isNotBlank() }
                .forEach { add(it) }
        }.distinct().ifEmpty { listOf("当前所需权限") }

    private fun maybeStartFirstRunRuntimeGate() {
        if (this is CardRunActivity || firstRunRuntimeGateShown) return
        firstRunRuntimeGateShown = true
        thread(name = "KiteFirstRunRuntimeGate", isDaemon = true) {
            val permissionState = currentRuntimePermissionState()
            if (!permissionState.ready) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setUbuntuRuntimeState(buildRuntimePermissionUiState(baseImageReady = false) ?: return@runOnUiThread)
                }
                return@thread
            }
            val runtimeReady = runCatching {
                WorkSurfaceRuntimeBridge.isDefaultContainerReady(applicationContext)
            }.getOrDefault(false)
            val bootstrapResourcesSettled = runtimeReady &&
                ToolchainPackInstaller.bootstrapResourcesSettled(applicationContext)
            if (runtimeReady && bootstrapResourcesSettled) {
                runOnUiThread { setUbuntuRuntimeState(UbuntuRuntimeUiState.hidden()) }
                return@thread
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setUbuntuRuntimeState(runtimeDeployPendingState())
                ensureKfRuntimeBootstrap()
            }
        }
    }

    private fun ensureBundledToolBootstrapIfNeeded(reason: String) {
        if (this is CardRunActivity || bootstrapResourceGateInFlight) return
        bootstrapResourceGateInFlight = true
        thread(name = "KiteBundledToolBootstrapGate-${reason.take(24)}", isDaemon = true) {
            val permissionReady = currentRuntimePermissionState().ready
            val resourcesSettled = permissionReady &&
                ToolchainPackInstaller.bootstrapResourcesSettled(applicationContext)
            runOnUiThread {
                bootstrapResourceGateInFlight = false
                if (isFinishing || isDestroyed || !permissionReady || resourcesSettled) return@runOnUiThread
                setUbuntuRuntimeState(runtimeDeployPendingState())
                ensureKfRuntimeBootstrap()
            }
        }
    }

    private fun currentRuntimePermissionState(): RuntimePermissionState {
        val missing = mutableListOf<String>()
        fun addIfMissing(permission: String) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing += permission
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            addIfMissing(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                addIfMissing(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        return RuntimePermissionState(
            missingPermissions = missing.distinct(),
            needsAllFilesAccess = needsAllFilesAccess
        )
    }

    private fun buildRuntimePermissionUiState(baseImageReady: Boolean): UbuntuRuntimeUiState? {
        if (baseImageReady) return null
        val permissionState = currentRuntimePermissionState()
        if (permissionState.ready) return null
        val labels = permissionState.missingLabels()
        val actionLabel = when {
            permissionState.missingPermissions.isNotEmpty() -> "弹出权限请求"
            permissionState.needsAllFilesAccess -> "打开文件访问设置"
            else -> "继续部署"
        }
        return UbuntuRuntimeUiState(
            title = "需要完成首次授权",
            detail = listOf(
                "首次部署 Ubuntu 前需要先完成文件访问授权，否则共享投放区、导入目录和部分系统准备步骤可能被系统拦截。",
                "未完成：${labels.joinToString("、")}",
                "点击下方按钮后，请按系统提示完成授权；返回 Kite 后会自动继续解压 Ubuntu。"
            ).joinToString("\n"),
            blocksUbuntuActions = true,
            isProblem = false,
            canRetry = true,
            requiresPermission = true,
            permissionActionLabel = actionLabel
        )
    }

    private fun RuntimePermissionState.missingLabels(): List<String> =
        buildList {
            if (needsAllFilesAccess) add("全部文件访问")
            missingPermissions
                .map(::runtimePermissionLabel)
                .filter { it.isNotBlank() }
                .forEach { add(it) }
        }.distinct().ifEmpty { listOf("文件访问") }

    private fun runtimePermissionLabel(permission: String): String =
        when (permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> "文件读取"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "文件写入"
            Manifest.permission.POST_NOTIFICATIONS -> "系统通知"
            else -> permission.substringAfterLast('.')
        }

    private fun requestFirstRunRuntimePermissions(startBootstrapAfterGrant: Boolean) {
        if (startBootstrapAfterGrant) {
            pendingRuntimePermissionBootstrap = true
        }
        val uiState = buildRuntimePermissionUiState(baseImageReady = false)
        if (uiState != null) {
            setUbuntuRuntimeState(uiState)
        }
        val permissionState = currentRuntimePermissionState()
        when {
            permissionState.missingPermissions.isNotEmpty() -> {
                if (!runtimePermissionRequestInFlight) {
                    runtimePermissionRequestInFlight = true
                    requestPermissions(
                        permissionState.missingPermissions.toTypedArray(),
                        REQUEST_FIRST_RUN_RUNTIME_PERMISSIONS
                    )
                }
            }
            permissionState.needsAllFilesAccess -> openAllFilesAccessSettings()
            startBootstrapAfterGrant -> resumePendingRuntimePermissionBootstrap()
        }
    }

    private fun notificationsEnabled(): Boolean {
        val manager = getSystemService(NotificationManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.areNotificationsEnabled()
        } else {
            true
        }
    }

    private fun notificationSettingsSubtitle(): String =
        if (notificationsEnabled()) {
            "已开启，后台运行和容器服务会显示系统通知。"
        } else {
            "未开启，点击后进入系统通知授权。"
        }

    private fun requestNotificationAccess() {
        if (notificationsEnabled()) {
            openAppNotificationSettings()
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
            return
        }
        openAppNotificationSettings()
    }

    private fun handleNotificationSettingToggle(wantsEnabled: Boolean) {
        if (wantsEnabled) {
            requestNotificationAccess()
        } else {
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
    }

    private fun openAllFilesAccessSettings() {
        val appSettingsIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(appSettingsIntent) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    .onFailure { error ->
                        Toast.makeText(
                            this,
                            "无法打开文件访问授权：${error.message ?: error.javaClass.simpleName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
    }

    private fun resumePendingRuntimePermissionBootstrap() {
        if (!pendingRuntimePermissionBootstrap || runtimePermissionRequestInFlight) return
        val uiState = buildRuntimePermissionUiState(baseImageReady = false)
        if (uiState != null) {
            setUbuntuRuntimeState(uiState)
            return
        }
        pendingRuntimePermissionBootstrap = false
        kfRuntimeBootstrapRequested = false
        setUbuntuRuntimeState(runtimeDeployPendingState())
        ensureKfRuntimeBootstrap()
    }

    private fun runtimeDeployPendingState(): UbuntuRuntimeUiState =
        UbuntuRuntimeUiState(
            title = "正在准备 Ubuntu 部署",
            detail = "权限已经就绪，正在启动系统镜像解压和基础环境初始化。",
            blocksUbuntuActions = true,
            isProblem = false,
            showProgress = true,
            progressText = "等待解压进度"
        )

    private fun refreshUbuntuRuntimeState() {
        thread(name = "KiteUbuntuRuntimeCheck", isDaemon = true) {
            val state = runCatching {
                val baseReady = WorkSurfaceRuntimeBridge.isBaseImageReady(applicationContext)
                buildRuntimePermissionUiState(baseImageReady = baseReady) ?: if (baseReady) {
                    if (WorkSurfaceRuntimeBridge.isDefaultContainerReady(applicationContext)) {
                        if (ToolchainPackInstaller.bootstrapResourcesSettled(applicationContext)) {
                            UbuntuRuntimeUiState.hidden()
                        } else {
                            runtimeDeployPendingState()
                        }
                    } else {
                        UbuntuRuntimeUiState(
                            title = "\u0055\u0062\u0075\u006e\u0074\u0075 \u672a\u90e8\u7f72",
                            detail = "系统镜像已经解压完成，正在准备 PRoot、工作区和内置工具安装路径。",
                            blocksUbuntuActions = true,
                            isProblem = false,
                            showProgress = true,
                            progressText = "等待首次部署"
                        )
                    }
                } else {
                    UbuntuRuntimeUiState(
                        title = "\u0055\u0062\u0075\u006e\u0074\u0075 \u672a\u90e8\u7f72",
                        detail = "\u9996\u6b21\u542f\u52a8 Ubuntu \u5361\u7247\u6216\u7ec8\u7aef\u65f6\u4f1a\u5148\u89e3\u538b\u7cfb\u7edf\u955c\u50cf\u3002",
                        blocksUbuntuActions = true,
                        isProblem = false,
                        showProgress = true,
                        progressText = "等待首次部署"
                    )
                }
            }.getOrElse { error ->
                UbuntuRuntimeUiState(
                    title = "\u0055\u0062\u0075\u006e\u0074\u0075 \u542f\u52a8\u6821\u9a8c\u672a\u901a\u8fc7",
                    detail = "系统镜像可能已经存在，但最小 shell 没有成功启动。\n${error.message ?: error.javaClass.simpleName}",
                    blocksUbuntuActions = true,
                    isProblem = true,
                    canRetry = true
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
        updateRuntimeGateOverlay()
        maybeAutoShowUbuntuRuntimePanel(state)
        (supportFragmentManager.findFragmentByTag(TAG_RECIPE_EDITOR_FRAGMENT) as? RecipeEditorFragment)
            ?.updateRuntimeBlocked(state.blocksUbuntuActions)
        if (::root.isInitialized && currentScreen == AppDestination.Console && shouldRefreshConsoleForRuntimeState(previous, state)) {
            refreshConsoleRuntimeChrome()
        }
    }

    private fun shouldRefreshConsoleForRuntimeState(previous: UbuntuRuntimeUiState, next: UbuntuRuntimeUiState): Boolean {
        if (previous.visible != next.visible) return true
        if (previous.title != next.title) return true
        if (previous.isProblem != next.isProblem) return true
        if (previous.blocksUbuntuActions != next.blocksUbuntuActions) return true
        if (previous.requiresPermission != next.requiresPermission) return true
        if (previous.canRetry != next.canRetry) return true
        if (previous.showProgress != next.showProgress) return true
        val previousPercent = previous.progressPercent
        val nextPercent = next.progressPercent
        if (previousPercent != null && nextPercent != null) {
            return kotlin.math.abs(nextPercent - previousPercent) >= 5
        }
        return previous.progressText.isBlank() != next.progressText.isBlank()
    }

    private fun buildUbuntuRuntimeUiState(): UbuntuRuntimeUiState? {
        if (ubuntuRuntimeState.requiresPermission && !currentRuntimePermissionState().ready) {
            buildRuntimePermissionUiState(baseImageReady = false)?.let { return it }
        }
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
                if (latestRuntimeBootstrapProgress.active) return null
                when (latestBootstrapSnapshot.stage) {
                    BootstrapStage.ROOTFS_EXTRACTING,
                    BootstrapStage.BASE_BOOTSTRAP -> {
                        UbuntuRuntimeUiState(
                            title = "正在初始化基础环境",
                            detail = "系统镜像已经解压完成，正在准备 PRoot、工作区和内置工具安装路径。",
                            blocksUbuntuActions = true,
                            isProblem = false,
                            progressPercent = 55,
                            progressText = "总进度 55%",
                            showProgress = true
                        )
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
            ?.let { value -> runCatching { AppDestination.valueOf(value) }.getOrNull() }
            ?: return false
        return when (val policy = appNavigator.contract(screen).restorePolicy) {
            RestorePolicy.None -> false
            RestorePolicy.Direct -> {
                appNavigator.navigate(screen)
                true
            }
            is RestorePolicy.AsParent -> {
                appNavigator.navigate(policy.destination)
                true
            }
            RestorePolicy.RecipeDraft -> {
                val rawDraft = savedInstanceState.getString(STATE_RECIPE_DRAFT)
                val draft = rawDraft?.let(RecipeEditorDraft::fromJson)
                if (draft == null) {
                    false
                } else {
                    showRecipeEditorFeature(
                        recipeId = draft.editingRecipeId.takeIf(String::isNotBlank),
                        restoredDraftRaw = rawDraft
                    )
                    true
                }
            }
            RestorePolicy.WorkbenchUrl -> {
                val url = savedInstanceState.getString(STATE_WORKBENCH_URL).orEmpty()
                if (url.isBlank()) {
                    false
                } else {
                    showWorkbench(url, "restore", null)
                    true
                }
            }
        }
    }

    private fun restoreRecipeDraftFromSettings(): Boolean {
        if (!shouldRestoreLastScreen()) return false
        val rawDraft = recipeFeatureGateway.restoredEditorDraft(RECIPE_DRAFT_RESTORE_WINDOW_MS)
            ?: return false
        val draft = RecipeEditorDraft.fromJson(rawDraft) ?: run {
            recipeFeatureGateway.saveEditorDraft(null)
            return false
        }
        showRecipeEditorFeature(
            recipeId = draft.editingRecipeId.takeIf(String::isNotBlank),
            restoredDraftRaw = rawDraft
        )
        Toast.makeText(this, "已恢复未保存配置", Toast.LENGTH_SHORT).show()
        return true
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
            if (currentScreen == AppDestination.Console) showConsole()
        } else if (requestCode == REQUEST_FIRST_RUN_RUNTIME_PERMISSIONS) {
            runtimePermissionRequestInFlight = false
            dropZoneStatus = dropZoneManager.prepareDropZone()
            val permissionState = currentRuntimePermissionState()
            val permissionUiState = buildRuntimePermissionUiState(baseImageReady = false)
            if (permissionUiState != null) {
                setUbuntuRuntimeState(permissionUiState)
                if (permissionState.missingPermissions.isEmpty() && permissionState.needsAllFilesAccess) {
                    openAllFilesAccessSettings()
                } else {
                    Toast.makeText(
                        this,
                        "仍缺少：${permissionState.missingLabels().joinToString("、")}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                resumePendingRuntimePermissionBootstrap()
            }
            if (currentScreen == AppDestination.Console) showConsole()
        } else if (requestCode == REQUEST_FIRST_RUN_PERMISSION_ONBOARDING) {
            firstRunPermissionRequestInFlight = false
            dropZoneStatus = dropZoneManager.prepareDropZone()
            continueFirstRunPermissionOnboarding()
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (notificationsEnabled()) {
                Toast.makeText(this, "通知已开启", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "通知未开启，可在设置中再次授权", Toast.LENGTH_SHORT).show()
            }
            if (currentScreen == AppDestination.Settings) showSettings()
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
        if (currentScreen == AppDestination.Console && showToast) showConsole()
        if (showToast) {
            Toast.makeText(this, "正在刷新 Kite 投放区", Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            runCatching { recipeFeatureGateway.refreshExternalRecipes() }
                .onSuccess { result ->
                    currentRecipes = recipeFeatureGateway.loadRecipes(forceRefresh = false)
                    refreshRecipeRuntimeStates(currentRecipes)
                    isDropZoneRefreshing = false
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                    showConsole()
                }
                .onFailure { error ->
                    isDropZoneRefreshing = false
                    Toast.makeText(
                        this@MainActivity,
                        "刷新失败：${error.message ?: error.javaClass.simpleName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (currentScreen == AppDestination.Console) showConsole()
                }
        }
    }

    private fun requestDropZoneAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            openAllFilesAccessSettings()
            return
        }
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
        enterScreen(AppDestination.Console)
        val focusedRecipe = focusedRunRecipe()
        if (this is CardRunActivity && focusedRecipe != null) {
            showCardRunSurface(focusedRecipe)
            return
        }
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(consoleShellHeader())
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            consoleRuntimeBannerHost = this
            ubuntuRuntimeBanner()?.let(::addView)
        })
        val content = FrameLayout(this).apply {
            id = R.id.kite_feature_content
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
        val existing = supportFragmentManager.findFragmentByTag(TAG_HOME_FRAGMENT) as? HomeFragment
        val fragment = existing ?: HomeFragment.newInstance(ubuntuRuntimeState.blocksUbuntuActions)
        fragment.updateRuntimeBlocked(ubuntuRuntimeState.blocksUbuntuActions)
        supportFragmentManager.beginTransaction().apply {
            if (fragment.isDetached) {
                attach(fragment)
            } else if (!fragment.isAdded) {
                add(R.id.kite_feature_content, fragment, TAG_HOME_FRAGMENT)
            } else {
                replace(R.id.kite_feature_content, fragment, TAG_HOME_FRAGMENT)
            }
        }.commitAllowingStateLoss()
    }

    private fun resumeConsoleSurface() {
        val fragment = supportFragmentManager.findFragmentByTag(TAG_HOME_FRAGMENT) as? HomeFragment
        if (fragment == null || fragment.isDetached || fragment.view?.parent == null || root.visibility != View.VISIBLE) {
            showConsole()
            return
        }
        fragment.updateRuntimeBlocked(ubuntuRuntimeState.blocksUbuntuActions)
        refreshConsoleRuntimeChrome()
    }

    private fun refreshConsoleRuntimeChrome() {
        consoleSystemStatusPillView?.let { bindSystemStatusPill(it, ubuntuRuntimeState) }
        consoleRuntimeBannerHost?.apply {
            removeAllViews()
            ubuntuRuntimeBanner()?.let(::addView)
        }
        (supportFragmentManager.findFragmentByTag(TAG_HOME_FRAGMENT) as? HomeFragment)
            ?.updateRuntimeBlocked(ubuntuRuntimeState.blocksUbuntuActions)
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

    private fun showKiteProcessOverview(forceRefresh: Boolean = true) {
        if (forceRefresh) {
            requestRuntimePanelSummaryRefresh(force = true)
        }
        pruneRunManagementPendingProcessStops()
        enterScreen(AppDestination.Processes)
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val taskSnapshot = TaskManagerStore.snapshot.value
        val terminalItems = TerminalSessionStore.snapshot.value.sessions
        val runs = CardRunStore.runs.value
        val summary = runtimePanelSummary(taskSnapshot = taskSnapshot)
        root.addView(runManagementHeader(summary))
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(8), dp(18), dp(28))
                val groups = buildRunManagementGroups(
                    runs = runs,
                    terminalItems = terminalItems,
                    processItems = taskSnapshot.processes
                )
                val otherProcessSections = runManagementOtherProcessSections(groups, taskSnapshot.processes)
                if (groups.isEmpty() && otherProcessSections.isEmpty()) {
                    addView(TextView(context).apply {
                        text = "当前没有运行中的卡片或进程"
                        textSize = 13.5f
                        gravity = Gravity.CENTER
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(46), 0, dp(20))
                    })
                } else {
                    groups.forEach { group ->
                        addView(runManagementCard(group))
                    }
                    otherProcessSections.forEach { (title, processes) ->
                        addView(runManagementProcessSection(title, processes))
                    }
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
    }

    private fun showTerminal() {
        val currentTerminalFragment = supportFragmentManager.findFragmentByTag(TERMINAL_FRAGMENT_TAG) as? TerminalFragment
        if (currentScreen == AppDestination.Terminal && currentTerminalFragment?.isAdded == true) {
            applyKiteTerminalTheme()
            terminalBottomNavigation?.visibility = if (isTerminalDetailMode) View.GONE else View.VISIBLE
            return
        }
        enterScreen(AppDestination.Terminal)
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
        val baseReady = runCatching { WorkSurfaceRuntimeBridge.isBaseImageReady(applicationContext) }
            .getOrDefault(false)
        if (!baseReady) {
            val permissionUiState = buildRuntimePermissionUiState(baseImageReady = false)
            if (permissionUiState != null) {
                setUbuntuRuntimeState(permissionUiState)
                showUbuntuRuntimePanel(auto = true)
                requestFirstRunRuntimePermissions(startBootstrapAfterGrant = true)
                return
            }
        }
        kfRuntimeBootstrapRequested = true
        BootstrapCoordinator.ensureStarted(applicationContext)
    }

    private fun clearRootForScreen(detachTerminal: Boolean = true) {
        terminalBottomNavigation = null
        cardRunSurfaceSignature = ""
        cardRunReportBinding = null
        resourceInstallWizardSurface?.dispose()
        resourceInstallWizardSurface = null
        consoleSystemStatusPillView = null
        consoleRuntimeBannerHost = null
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
        detachFragment(TAG_HOME_FRAGMENT)
        detachFragment(TAG_RECIPE_EDITOR_FRAGMENT)
        detachFragment(CARD_RUN_TERMINAL_FRAGMENT_TAG)
        // T6b/T7:切走时移除 Fragment(RecipeRawJson、ResourceManage)并恢复 root 可见性
        supportFragmentManager.findFragmentByTag(TAG_RECIPE_RAW_JSON_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_RESOURCE_MANAGE_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_RESOURCE_SEARCH_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_RESOURCES_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_RESOURCE_DETAIL_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        root.visibility = View.VISIBLE
        if (changed) {
            transaction.commitNowAllowingStateLoss()
        }
        root.removeAllViews()
    }

    /**
     * 过渡期:AppNavigator 的老路径分发。把 AppDestination 枚举映射到老的 show* 方法。
     * T6b 起逐个 AppDestination 改走 Fragment 时,这些分支会被 routeToFragment 取代。
     * 仅无参、可在路由层触发的 AppDestination 在此分发;带参 AppDestination(如 ResourceDetail 需 resourceId)
     * 仍由各自的 show*(args) 直接调用,不经过此无参入口。
     */
    private fun dispatchLegacyDestination(screen: AppDestination) {
        when (screen) {
            AppDestination.Console -> showConsole()
            AppDestination.Terminal -> showTerminal()
            AppDestination.Settings -> showSettings()
            AppDestination.ThemeSettings -> showThemeSettings()
            AppDestination.Resources -> showResources()
            AppDestination.ResourceManage -> showResourceManage()
            AppDestination.Processes -> showKiteProcessOverview()
            // 带参数或条件复杂的 AppDestination 保留各自入口。
            else -> Unit
        }
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
        enterScreen(AppDestination.Settings)
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(topBar("设置", ::requestNavigationBack))
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(96))
                addView(settingsRow("主题", "主题色、背景色和卡片色彩") { showThemeSettings() })
                addView(settingsRow("浏览器模式", browserRuntimeMode().title) { showBrowserRuntimeModeDialog() }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(12), 0, 0) }
                })
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
                addView(settingsSwitchRow(
                    title = "系统通知",
                    subtitle = notificationSettingsSubtitle(),
                    checked = notificationsEnabled()
                ) { checked ->
                    handleNotificationSettingToggle(checked)
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

    private fun browserRuntimeMode(): BrowserRuntimeMode =
        BrowserRuntimeMode.fromStorageKey(appSettings.getString(KEY_BROWSER_RUNTIME_MODE, null))

    private fun saveBrowserRuntimeMode(mode: BrowserRuntimeMode) {
        appSettings.edit().putString(KEY_BROWSER_RUNTIME_MODE, mode.storageKey).apply()
    }

    private fun showBrowserRuntimeModeDialog() {
        val dialog = Dialog(this)
        val currentMode = browserRuntimeMode()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            addView(TextView(context).apply {
                text = "浏览器模式"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "选择网页运行面；账号授权仍遵守系统浏览器回跳边界。"
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(6), 0, dp(12))
            })
            BrowserRuntimeMode.values().forEach { mode ->
                addView(browserRuntimeModeChoiceRow(mode, mode == currentMode) {
                    saveBrowserRuntimeMode(mode)
                    dialog.dismiss()
                    Toast.makeText(context, "已切换为：${mode.title}", Toast.LENGTH_SHORT).show()
                    showSettings()
                })
            }
            addView(TextView(context).apply {
                text = "关闭"
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textSecondary)
                background = roundedBox(tokens.surface, tokens.border, dp(14).toFloat())
                setPadding(0, dp(11), 0, dp(11))
                setOnClickListener { dialog.dismiss() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(12), 0, 0) }
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun browserRuntimeModeChoiceRow(
        mode: BrowserRuntimeMode,
        selected: Boolean,
        onClick: () -> Unit
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = roundedBox(
                if (selected) tokens.primarySubtle else tokens.surface,
                if (selected) tokens.primaryStrong else tokens.border,
                dp(16).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, 0) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = mode.title
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = mode.summary
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(4), dp(10), 0)
                })
            })
            addView(TextView(context).apply {
                text = if (selected) "✓" else ""
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(40))
            })
            setOnClickListener { onClick() }
        }

    private fun showThemeSettings() {
        enterScreen(AppDestination.ThemeSettings)
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(topBar("主题", ::requestNavigationBack))
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
        enterScreen(AppDestination.Resources)
        ensureBundledToolBootstrapIfNeeded("show_resources")
        showResourceFeatureFragment(ResourcesFragment(), TAG_RESOURCES_FRAGMENT)
    }

    private fun showResourceFeatureFragment(
        fragment: Fragment,
        tag: String,
        showBottomNavigation: Boolean = true
    ) {
        rootHost.setBackgroundColor(tokens.pageBackground)
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val content = FrameLayout(this).apply {
            id = R.id.kite_feature_content
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (showBottomNavigation) {
            val nav = bottomNavigation()
            showBottomNavigationImmediately(nav)
            root.addView(nav)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.kite_feature_content, fragment, tag)
            .commitAllowingStateLoss()
    }

    private fun registerHomeFeatureResults() {
        supportFragmentManager.setFragmentResultListener(
            HomeFeatureResultContract.REQUEST_KEY,
            this
        ) { _, bundle ->
            when (val request = HomeFeatureResultContract.parse(bundle)) {
                is HomeFeatureRequest.OpenEditor -> lifecycleScope.launch {
                    val recipes = recipeFeatureGateway.loadRecipes(forceRefresh = false)
                    currentRecipes = recipes
                    val recipe = recipes.firstOrNull { it.id == request.recipeId }
                    if (recipe == null) {
                        Toast.makeText(this@MainActivity, "卡片目录正在更新，请稍后重试", Toast.LENGTH_SHORT).show()
                    } else {
                        showRecipeEditor(recipe)
                    }
                }
                is HomeFeatureRequest.SubmitAction -> lifecycleScope.launch {
                    val recipes = recipeFeatureGateway.loadRecipes(forceRefresh = false)
                    currentRecipes = recipes
                    val recipe = recipes.firstOrNull { it.id == request.recipeId }
                        ?: CardRunStore.registeredRecipe(request.recipeId)
                    if (recipe == null) {
                        Toast.makeText(this@MainActivity, "卡片目录正在更新，请稍后重试", Toast.LENGTH_SHORT).show()
                    } else {
                        submitRecipeAction(
                            KiteRecipeActionRequest(
                                recipe = recipe,
                                intent = request.intent,
                                source = request.source,
                                openTaskOnStart = request.openTaskOnStart,
                                instanceId = request.instanceId
                            )
                        )
                    }
                }
                null -> Unit
            }
        }
    }

    private fun registerRecipeEditorResults() {
        supportFragmentManager.setFragmentResultListener(
            RecipeEditorResultContract.REQUEST_KEY,
            this
        ) { _, bundle ->
            when (val request = RecipeEditorResultContract.parse(bundle)) {
                RecipeEditorRequest.Close -> {
                    removeRecipeEditorFeature()
                    showConsole()
                }
                is RecipeEditorRequest.OpenRawJson -> lifecycleScope.launch {
                    resolveEditorRecipe(request.recipeId)?.let(::showRecipeRawJson)
                        ?: Toast.makeText(
                            this@MainActivity,
                            "卡片目录正在更新，请稍后重试",
                            Toast.LENGTH_SHORT
                        ).show()
                }
                is RecipeEditorRequest.OpenRunHistory -> lifecycleScope.launch {
                    resolveEditorRecipe(request.recipeId)?.let(::showRecipeRunHistory)
                        ?: Toast.makeText(
                            this@MainActivity,
                            "卡片目录正在更新，请稍后重试",
                            Toast.LENGTH_SHORT
                        ).show()
                }
                is RecipeEditorRequest.RequestShortcut -> lifecycleScope.launch {
                    resolveEditorRecipe(request.recipeId)?.let(::requestShortcutForRecipe)
                }
                is RecipeEditorRequest.Deleted -> {
                    if (request.removedCardInstanceIds.isNotEmpty()) {
                        bridgeClient.cleanCardRunPidDirs(request.removedCardInstanceIds)
                    }
                    runtimeStates.remove(request.recipeId)
                    removeRecipeEditorFeature()
                    showConsole()
                }
                is RecipeEditorRequest.SubmitAction -> lifecycleScope.launch {
                    val recipe = resolveEditorRecipe(request.recipeId)
                    if (recipe == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "卡片目录正在更新，请稍后重试",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        submitRecipeAction(
                            KiteRecipeActionRequest(
                                recipe = recipe,
                                intent = request.intent,
                                source = request.source,
                                openTaskOnStart = request.openTaskOnStart,
                                instanceId = request.instanceId
                            )
                        )
                    }
                }
                null -> Unit
            }
        }
    }

    private suspend fun resolveEditorRecipe(recipeId: String): KiteRecipe? {
        val recipes = recipeFeatureGateway.loadRecipes(forceRefresh = false)
        currentRecipes = recipes
        return recipes.firstOrNull { it.id == recipeId }
            ?: CardRunStore.registeredRecipe(recipeId)
    }

    private fun removeRecipeEditorFeature() {
        supportFragmentManager.findFragmentByTag(TAG_RECIPE_EDITOR_FRAGMENT)?.let { fragment ->
            supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commitNowAllowingStateLoss()
        }
    }

    private fun registerResourceFeatureResults() {
        supportFragmentManager.setFragmentResultListener(
            ResourceFeatureResultContract.REQUEST_KEY,
            this
        ) { _, bundle ->
            when (val request = ResourceFeatureResultContract.parse(bundle)) {
                ResourceFeatureRequest.Back -> requestNavigationBack()
                ResourceFeatureRequest.OpenManage -> showResourceManage()
                is ResourceFeatureRequest.OpenSearch -> showResourceSearch(request.query)
                is ResourceFeatureRequest.OpenDetail -> {
                    val returnToManage = currentScreen == AppDestination.ResourceManage
                    showResourceDetail(
                        resourceId = request.resourceId,
                        onBack = if (returnToManage) ::showResourceManage else null
                    )
                }
                is ResourceFeatureRequest.OpenMore -> resourceCatalog(forceRefresh = false)
                    .firstOrNull { it.id == request.resourceId }
                    ?.let(::showResourceMoreActions)
                is ResourceFeatureRequest.OpenRawJson -> resourceCatalog(forceRefresh = false)
                    .firstOrNull { it.id == request.resourceId }
                    ?.let(::showResourceRawJson)
                is ResourceFeatureRequest.OpenInstallPlan ->
                    showResourceInstallWizard(request.targetResourceId)
                is ResourceFeatureRequest.CancelInstallPlan ->
                    cancelResourceInstallTask(
                        targetResourceId = request.targetResourceId,
                        planResourceIds = request.resourceIds,
                        closeWizard = false
                    )
                is ResourceFeatureRequest.SubmitAction -> {
                    val item = resourceCatalog(forceRefresh = false)
                        .firstOrNull { it.id == request.request.resourceId }
                    if (item == null) {
                        showResourceDiscreteToast("资源目录正在更新，请稍后重试")
                    } else {
                        submitResourceAction(item, request.request.intent, request.request.source)
                    }
                }
                null -> Unit
            }
        }
    }

    private fun showResourceSearch(initialQuery: String = "") {
        enterScreen(AppDestination.ResourceSearch)
        currentResourceDetailId = null
        showResourceFeatureFragment(
            ResourceSearchFragment.newInstance(initialQuery.trim()),
            TAG_RESOURCE_SEARCH_FRAGMENT,
            showBottomNavigation = false
        )
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

    private fun showBottomNavigationImmediately(nav: View) {
        nav.animate().cancel()
        nav.visibility = View.VISIBLE
        nav.translationY = 0f
        nav.alpha = 1f
    }

    private fun invalidateResourceCatalogCache() {
        resourceCatalogDirty = true
        cachedResourceCatalog = null
        cachedResourceCatalogUpdatedAt = 0L
        cachedToolchainWorkspaceSnapshot = ToolchainWorkspaceSnapshot()
        resourceManifestLoader.invalidate()
    }

    private fun invalidateResourceRuntimeStateCache() {
        resourceCatalogDirty = true
        cachedResourceCatalogUpdatedAt = 0L
    }

    private fun invalidateResourceUiCache() {
        invalidateResourceRuntimeStateCache()
    }

   private fun resourceRuntimeFactsFromStore(item: ResourceItem): KiteResourceRuntimeFacts {
        val resourceId = KiteResourceInstallRecipes.safeId(item.id)
        val entry = resourceInstallStore.registryEntry(resourceId)
        val plan = resourceInstallStore.planSnapshot()
        return KiteResourceRuntimeFactsProjector.project(
            resourceId = resourceId,
            registryEntry = entry,
            plan = plan,
            baselineInstalled = item.id == RESOURCE_NODE_RUNTIME && item.runtimeFacts.installed && entry == null,
            idleStateLabel = item.runtimeFacts.idleStateLabel,
            extraBusy = item.runtimeFacts.extraBusy
        )
    }

    private fun projectResourceItemRuntime(item: ResourceItem): ResourceItem {
        val facts = resourceRuntimeFactsFromStore(item)
        val openRunStatus = CardRunStore
            .currentForRecipe(KiteResourceInstallRecipes.recipeId(item.id, "open"))
            ?.status
        val projection = KiteResourceUiProjector.project(
            installed = facts.installed,
            preparing = facts.preparing,
            installing = facts.installing,
            uninstalling = facts.uninstalling,
            failed = facts.failed,
            failedOperation = facts.failedOperation,
            idleStateLabel = facts.idleStateLabel,
            openRunStatus = openRunStatus,
            extraBusy = facts.extraBusy
        )
        return item.copy(
            stateLabel = projection.stateLabel,
            actionLabel = projection.actionLabel,
            actionEnabled = projection.actionEnabled,
            secondaryActionLabel = projection.secondaryActionLabel,
            runtimeFacts = facts
        )
    }

   private fun resourceCatalogForUiRender(reason: String): List<ResourceItem> {
        cachedResourceCatalog?.let { cached ->
            if (resourceCatalogDirty) requestResourceCatalogBackgroundRefresh(reason)
            return cached.map { item -> projectResourceItemRuntime(item) }
        }
        requestResourceCatalogBackgroundRefresh(reason)
        return emptyList()
    }

    private fun requestResourceCatalogBackgroundRefresh(@Suppress("UNUSED_PARAMETER") reason: String) {
        if (resourceCatalogBackgroundRefreshInFlight) return
        resourceCatalogBackgroundRefreshInFlight = true
        thread(name = "KiteResourceCatalogUiRefresh", isDaemon = true) {
            runCatching { resourceCatalog(forceRefresh = false) }
            runOnUiThread {
                resourceCatalogBackgroundRefreshInFlight = false
                when (currentScreen) {
                    AppDestination.Resources -> Unit
                    AppDestination.ResourceSearch -> Unit
                    AppDestination.CardRun -> Unit
                    AppDestination.ResourceMore -> Unit
                    AppDestination.ResourceManage -> Unit
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

    private fun showResourceManage() {
        enterScreen(AppDestination.ResourceManage)
        showResourceFeatureFragment(ResourceManageFragment(), TAG_RESOURCE_MANAGE_FRAGMENT)
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

    private fun resourceIcon(item: ResourceItem): View =
        resourceIcon(item.iconText, item.accent, item.iconAsset, item.iconFit)

    private fun resourceIcon(textValue: String, accent: String, assetPath: String = "", iconFit: String = ""): View {
        return resourceIcon(textValue, accent, assetPath, iconFit, size = dp(56), padding = dp(7), radius = dp(14).toFloat(), textSize = 15f)
    }

    private fun resourceIcon(
        textValue: String,
        accent: String,
        assetPath: String,
        iconFit: String,
        size: Int,
        padding: Int,
        radius: Float,
        textSize: Float
    ): View {
        if (assetPath.isBlank()) return resourceTextIcon(textValue, accent, size, radius, textSize)
        val isFullBleed = iconFit.equals(RESOURCE_ICON_FIT_FULL_BLEED, ignoreCase = true)
        val tone = KiteTheme.accent(accent, tokens)
        return FrameLayout(this).apply {
            fun applyIconBackground(hasBitmap: Boolean) {
                background = if (isFullBleed && hasBitmap) {
                    roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, radius)
                } else {
                    roundedBox(tokens.surface, tone.border, radius)
                }
            }
            fun showBitmap(bitmap: Bitmap) {
                applyIconBackground(hasBitmap = true)
                removeAllViews()
                addView(ImageView(context).apply {
                    scaleType = if (isFullBleed) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_INSIDE
                    if (!isFullBleed) setPadding(padding, padding, padding, padding)
                    setImageBitmap(bitmap)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            applyIconBackground(hasBitmap = false)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(size, size)
            val cachedBitmap = requestResourceIconBitmap(assetPath, size) { bitmap ->
                if (parent != null) showBitmap(bitmap)
            }
            if (cachedBitmap != null) {
                showBitmap(cachedBitmap)
            } else {
                addView(TextView(context).apply {
                    text = textValue
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(tone.strong)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }

    private fun resourceTextIcon(textValue: String, accent: String, size: Int, radius: Float, textSizeValue: Float): View =
        TextView(this).apply {
            text = textValue
            textSize = textSizeValue
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val tone = KiteTheme.accent(accent, tokens)
            setTextColor(tone.strong)
            background = roundedBox(tokens.surface, tone.border, radius)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

    private fun requestResourceIconBitmap(
        assetPath: String,
        maxDimensionPx: Int = 0,
        onLoaded: (Bitmap) -> Unit
    ): Bitmap? {
        val normalized = normalizedResourceAssetPath(assetPath) ?: return null
        val boundedMaxDimension = maxDimensionPx.coerceAtLeast(0)
        val cacheKey = resourceIconBitmapCacheKey(normalized, boundedMaxDimension)
        synchronized(resourceIconBitmapLock) {
            resourceIconBitmapCache[cacheKey]?.let { return it }
            resourceIconBitmapWaiters.getOrPut(cacheKey) { mutableListOf() }.add(onLoaded)
            if (!resourceIconBitmapInFlight.add(cacheKey)) return null
        }
        thread(name = "KiteResourceImage-${cacheKey.hashCode()}", isDaemon = true) {
            val bitmap = decodeResourceIconBitmap(normalized, boundedMaxDimension)
            val waiters = synchronized(resourceIconBitmapLock) {
                if (bitmap != null) resourceIconBitmapCache[cacheKey] = bitmap
                resourceIconBitmapInFlight.remove(cacheKey)
                resourceIconBitmapWaiters.remove(cacheKey).orEmpty()
            }
            if (bitmap != null && waiters.isNotEmpty()) {
                root.post { waiters.forEach { waiter -> waiter(bitmap) } }
            }
        }
        return null
    }

    private fun normalizedResourceAssetPath(assetPath: String): String? {
        val normalized = assetPath.trim().trimStart('/')
        if (normalized.isBlank() || normalized.contains("..")) return null
        return normalized
    }

    private fun resourceIconBitmapCacheKey(normalizedAssetPath: String, maxDimensionPx: Int): String =
        if (maxDimensionPx > 0) "$normalizedAssetPath@$maxDimensionPx" else normalizedAssetPath

    private fun decodeResourceIconBitmap(normalizedAssetPath: String, maxDimensionPx: Int): Bitmap? =
        runCatching {
            if (maxDimensionPx <= 0) {
                assets.open(normalizedAssetPath).use { stream -> BitmapFactory.decodeStream(stream) }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assets.open(normalizedAssetPath).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
                }
                assets.open(normalizedAssetPath).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
            }
        }.getOrNull()

    private fun bitmapSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
        if (width <= 0 || height <= 0 || maxDimensionPx <= 0) return 1
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= maxDimensionPx && sampledHeight / 2 >= maxDimensionPx) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun showResourceDetail(resourceId: String, onBack: (() -> Unit)? = null) {
        currentResourceDetailId = resourceId
        enterScreen(AppDestination.ResourceDetail, onBack)
        showResourceFeatureFragment(
            ResourceDetailFragment.newInstance(resourceId),
            TAG_RESOURCE_DETAIL_FRAGMENT
        )
    }

   private fun showResourceRawJson(item: ResourceItem) {
        val latestItem = cachedResourceCatalog?.firstOrNull { it.id == item.id } ?: item
        currentResourceDetailId = latestItem.id
        enterScreen(AppDestination.ResourceRawJson) { showResourceDetail(latestItem.id) }
        clearRootForScreen()
        root.addView(topBar("原始 JSON", ::requestNavigationBack))
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

   private fun handleResourceAction(item: ResourceItem) {
        val intent = KiteResourceActionCoordinator.primaryIntent(
            actionLabel = item.actionLabel,
            reopenInstall = resourceActionShouldReopenInstallWizard(item)
        )
        submitResourceAction(item, intent, KiteResourceActionSource.Card)
    }

    private fun submitResourceAction(
        item: ResourceItem,
        intent: KiteResourceActionIntent,
        source: KiteResourceActionSource
    ) {
        val request = KiteResourceActionRequest(item.id, intent, source)
        diagnostics.logBridgeEvent(
            "resource_action_submit",
            null,
            mapOf("resourceId" to request.resourceId, "intent" to intent.name, "source" to source.logValue)
        )
        when (request.intent) {
            KiteResourceActionIntent.Install -> handleResourceInstallAction(item)
            KiteResourceActionIntent.ReopenInstall -> reopenResourceInstallWizard(item)
            KiteResourceActionIntent.Open -> {
                val recipe = resourceOpenRecipe(item)
                if (recipe == null) {
                    showResourceDiscreteToast("${item.name} 的打开动作稍后接入")
                } else {
                    startResourceOpen(item, recipe)
                }
            }
            KiteResourceActionIntent.Stop -> handleResourceOpenStopAction(item)
            KiteResourceActionIntent.Uninstall -> handleResourceUninstallAction(item)
            KiteResourceActionIntent.CancelInstall -> handleResourceCancelInstallTask(item)
            KiteResourceActionIntent.CancelFailedInstall -> handleResourceFailedInstallCancel(item)
            KiteResourceActionIntent.BusyStatus -> showResourceDiscreteToast("${item.name} 正在卸载")
            KiteResourceActionIntent.Unsupported -> showResourceDiscreteToast("正在处理 ${item.name}")
        }
    }

    private fun resourceActionShouldReopenInstallWizard(item: ResourceItem): Boolean {
        val resourceId = KiteResourceInstallRecipes.safeId(item.id)
        val planSnapshot = resourceInstallStore.planSnapshot()
        if (
            planSnapshot.resourceIds.isNotEmpty() &&
            (resourceId in planSnapshot.resourceIds || resourceId == planSnapshot.targetResourceId)
        ) return true
        return activeResourceInstallWizard?.let { context ->
            resourceId == KiteResourceInstallRecipes.safeId(context.targetResourceId) ||
                resourceId in context.planResourceIds.map { KiteResourceInstallRecipes.safeId(it) }
        } == true
    }

    private fun reopenResourceInstallWizard(item: ResourceItem) {
        val planSnapshot = resourceInstallStore.planSnapshot()
        val resourceId = KiteResourceInstallRecipes.safeId(item.id)
        val targetId = when {
            planSnapshot.resourceIds.isNotEmpty() &&
                (resourceId in planSnapshot.resourceIds || resourceId == planSnapshot.targetResourceId) ->
                planSnapshot.targetResourceId
            activeResourceInstallWizard?.targetResourceId?.isNotBlank() == true ->
                activeResourceInstallWizard!!.targetResourceId
            currentResourceInstallTargetId?.isNotBlank() == true ->
                currentResourceInstallTargetId.orEmpty()
            item.actionLabel == "获取中" -> item.id
            else -> ""
        }
        val planIds = planSnapshot.resourceIds
            .ifEmpty { resourceInstallWizardPlanIds }
            .ifEmpty { resourceInstallStore.planResourceIds() }
            .ifEmpty { if (item.actionLabel == "获取中") listOf(item.id) else emptyList() }
        if (targetId.isBlank() || planIds.isEmpty()) {
            showResourceDiscreteToast("${item.name} 正在处理，获取向导暂不可恢复")
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
        resourceInstallStore.markPreparing(item.id)
        invalidateResourceRuntimeStateCache()
        val requestId = ++resourceInstallPlanRequestSerial
        thread(name = "KiteResourceInstallPlan-$requestId-${item.id}", isDaemon = true) {
            val result = runCatching { buildResourceInstallPlan(item) }
            runOnUiThread {
                if (requestId != resourceInstallPlanRequestSerial) {
                    if (resourceInstallStore.isPreparing(item.id)) resourceInstallStore.clear(item.id)
                    return@runOnUiThread
                }
                result.onSuccess { plan ->
                    handleResourceInstallPlanReady(item, plan)
                }.onFailure { error ->
                    resourceInstallStore.markFailed(
                        item.id,
                        KiteResourceInstallStore.OP_INSTALL,
                        null,
                        error.message ?: error.javaClass.simpleName
                    )
                    invalidateResourceRuntimeStateCache()
                    showResourceDiscreteToast("执行队列准备失败：${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    private fun handleResourceReinstallAction(item: ResourceItem) {
        clearResourceInstallRunState(item.id)
        handleResourceUninstallAction(item, ResourceUninstallContinuation.Reinstall)
    }

    private fun handleResourceInstallPlanReady(item: ResourceItem, plan: ResourceInstallPlan) {
        if (plan.missing.isNotEmpty()) {
            resourceInstallStore.clear(item.id)
            invalidateResourceRuntimeStateCache()
            val missingNames = plan.missing
                .map { it.resource?.name ?: it.requirement }
                .distinct()
                .joinToString("、")
            showResourceDiscreteToast("缺少可获取的基础层：$missingNames")
            return
        }
        if (plan.steps.isEmpty()) {
            resourceInstallStore.clear(item.id)
            invalidateResourceRuntimeStateCache()
            showResourceDiscreteToast("${item.name} 已经就绪")
            return
        }
        val planIds = plan.steps
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
        resetResourceInstallPlanTransientState(planIds)
        resourceInstallStore.beginPlan(item.id, planIds)
        currentResourceInstallTargetId = item.id
        resourceInstallWizardPlanIds = planIds
        showResourceInstallWizard(item.id)
    }

    private fun clearResourceInstallRunState(resourceId: String) {
        val recipeId = KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL)
        runtimeStates.remove(recipeId)
        activeRunInstanceIds.remove(recipeId)
        suppressedResourceRunSurfaceRecipeIds.remove(recipeId)
        CardRunStore.removeRunStatesForRecipes(listOf(recipeId), removeOpenHistory = true)
    }

    private fun resetResourceInstallPlanTransientState(resourceIds: List<String>) {
        val recipeIds = resourceIds
            .filter { it.isNotBlank() }
            .distinct()
            .map { resourceId ->
                if (
                    resourceInstallStore.status(resourceId) != null &&
                    resourceInstallStore.status(resourceId) != KiteResourceInstallStore.STATUS_PREPARING &&
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
                invalidateResourceRuntimeStateCache()
            showResourceDiscreteToast("已移除 ${item.name} 的获取记录")
            if (continuation == ResourceUninstallContinuation.Reinstall) {
                submitResourceAction(item, KiteResourceActionIntent.Install, KiteResourceActionSource.Continuation)
            } else if (continuation == ResourceUninstallContinuation.CancelFailedInstall) {
                resourceInstallStore.clearPlan()
                currentResourceInstallTargetId = null
                resourceInstallWizardPlanIds = emptyList()
                activeResourceInstallWizard = null
                if (this is CardRunActivity && currentScreen == AppDestination.CardRun) {
                    closeCardRunTask()
                } else {
                    settleVisibleResourceMutation("cancel_failed_install")
                }
            } else {
                settleVisibleResourceMutation("uninstall_without_recipe")
            }
        } else {
            startResourceUninstall(item, recipe, continuation)
        }
    }

    private fun handleResourceFailedInstallCancel(item: ResourceItem) {
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
        val unfinishedIds = resourceIds
            .filterNot { resourceInstallStore.isInstalled(it) }
            .distinct()
        stopResourceInstallRunsForCancel(unfinishedIds)
        if (unfinishedIds.isEmpty()) {
            clearResourceInstallTask(
                targetResourceId = targetId,
                planResourceIds = resourceIds,
                closeWizard = closeWizard
            )
            showResourceDiscreteToast("获取任务已取消")
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

    private fun showResourceDiscreteToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        if (resourceStatusToastSuppressed()) return
        Toast.makeText(this, message, length).show()
    }

    private fun resourceStatusToastSuppressed(): Boolean =
        resourceInstallWizardSurfaceActive() ||
            currentScreen == AppDestination.Resources ||
            currentScreen == AppDestination.ResourceSearch ||
            currentScreen == AppDestination.ResourceDetail ||
            currentScreen == AppDestination.ResourceMore ||
            currentScreen == AppDestination.ResourceManage

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
                !resourceInstallStore.isFailed(resourceId)
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
        invalidateResourceRuntimeStateCache()
        closeResourceInstallWizardInstance(targetId, removeRunState = true)
        if (closeWizard) {
            if (this is CardRunActivity) {
                closeCardRunTask()
            } else {
                showResources()
            }
        } else {
            refreshResourceScreenIfVisible()
        }
    }

    private fun closeResourceInstallWizardAndCancelRuns(context: ResourceInstallWizardContext?) {
        val targetId = context?.targetResourceId ?: currentResourceInstallTargetId.orEmpty()
        val planIds = context?.planResourceIds.orEmpty()
            .ifEmpty { resourceInstallWizardPlanIds }
            .ifEmpty { resourceInstallStore.planResourceIds() }
        val resourceIds = resolveResourceInstallTaskIds(targetId, planIds)
        stopResourceInstallRunsForCancel(resourceIds)
        clearResourceInstallTask(
            targetResourceId = targetId,
            planResourceIds = resourceIds,
            closeWizard = false
        )
    }

    private fun closeResourceInstallWizardInstance(
        targetResourceId: String? = null,
        removeRunState: Boolean = false
    ) {
        val targetId = targetResourceId.orEmpty()
        val context = activeResourceInstallWizard
        val matchesCurrent = targetId.isBlank() ||
            currentResourceInstallTargetId == targetId ||
            context?.targetResourceId == targetId
        if (!matchesCurrent) return
        val wizardRecipeIds = listOfNotNull(
            context?.wizardRecipeId,
            targetId.takeIf { it.isNotBlank() }?.let { resourceInstallWizardRecipe(it, it).id }
        ).distinct()
        currentResourceInstallTargetId = null
        resourceInstallWizardPlanIds = emptyList()
        activeResourceInstallWizard = null
        resourceInstallWizardSurface?.dispose()
        resourceInstallWizardSurface = null
        if (removeRunState && wizardRecipeIds.isNotEmpty()) {
            wizardRecipeIds.forEach { recipeId ->
                runtimeStates.remove(recipeId)
                activeRunInstanceIds.remove(recipeId)
                suppressedResourceRunSurfaceRecipeIds.remove(recipeId)
            }
            CardRunStore.removeRunStatesForRecipes(wizardRecipeIds, removeOpenHistory = true)
        }
    }

    private fun releaseResourceInstallWizardSurfaceIfActivityDestroyed() {
        if (this !is CardRunActivity || isChangingConfigurations) return
        if (!::resourceInstallStore.isInitialized) return
        val context = activeResourceInstallWizard ?: return
        if (focusedRunInstanceId != context.wizardInstanceId && focusedRunRecipeId != context.wizardRecipeId) return
        val targetId = context.targetResourceId
        val planIds = context.planResourceIds.ifEmpty { resourceInstallStore.planResourceIds() }
        val resourceIds = resolveResourceInstallTaskIds(targetId, planIds)
        if (resourceIds.isEmpty()) {
            closeResourceInstallWizardInstance(targetId, removeRunState = true)
            return
        }
        closeResourceInstallWizardInstance(targetId, removeRunState = true)
    }

    private fun resolveResourceInstallTaskIds(targetId: String, planResourceIds: List<String>): List<String> =
        planResourceIds
            .filter { it.isNotBlank() }
            .ifEmpty { resourceInstallStore.planResourceIds() }
            .ifEmpty { listOfNotNull(targetId.takeIf { it.isNotBlank() }) }
            .distinct()

    private fun stopResourceInstallRunsForCancel(
        resourceIds: List<String>
    ) {
        val childRunsByRecipeId = activeResourceInstallWizard
            ?.wizardInstanceId
            ?.let { CardRunStore.childrenOf(it) }
            .orEmpty()
            .associateBy { it.recipeId }
        resourceIds.forEach { resourceId ->
            listOf(KiteResourceInstallRecipes.OP_INSTALL, KiteResourceInstallRecipes.OP_UNINSTALL).forEach operationLoop@ { operation ->
                val recipeId = KiteResourceInstallRecipes.recipeId(resourceId, operation)
                val recipe = CardRunStore.registeredRecipe(recipeId) ?: return@operationLoop
                val state = childRunsByRecipeId[recipe.id]
                    ?: resourceRunStateForOperation(resourceId, operation)
                    ?: return@operationLoop
                if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened || state.hasRunBinding()) {
                    stopResourceRecipeForCancel(recipe, state)
                }
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
                cardInstanceId = state.cardInstanceId,
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
                showResourceDiscreteToast(message)
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
        val wizardInstanceId = existingResourceInstallWizardInstanceId(targetId, wizardRecipe.id)
            ?: resourceInstallWizardInstanceId(targetId)
        currentResourceInstallTargetId = targetId
        resourceInstallWizardPlanIds = planIds
        activeResourceInstallWizard = ResourceInstallWizardContext(
            targetResourceId = targetId,
            planResourceIds = planIds,
            wizardRecipeId = wizardRecipe.id,
            wizardInstanceId = wizardInstanceId
        )
        CardRunStore.registerRecipe(wizardRecipe)
        activeRunInstanceIds[wizardRecipe.id] = wizardInstanceId
        if (
            refreshVisibleResourceInstallWizardInsteadOfRebuild(
                targetId = targetId,
                planIds = planIds,
                recipeId = wizardRecipe.id,
                instanceId = wizardInstanceId,
                reason = "show_wizard"
            )
        ) return
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
        if (this !is CardRunActivity && currentScreen != AppDestination.CardRun) {
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
        if (
            refreshVisibleResourceInstallWizardInsteadOfRebuild(
                targetId = targetId,
                planIds = planIds,
                recipeId = wizardRecipe.id,
                instanceId = wizardInstanceId,
                reason = "show_wizard_surface"
            )
        ) return
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

    private fun refreshVisibleResourceInstallWizardInsteadOfRebuild(
        targetId: String,
        planIds: List<String>,
        recipeId: String,
        instanceId: String,
        reason: String
    ): Boolean {
        val surface = resourceInstallWizardSurface ?: return false
        if (currentScreen != AppDestination.CardRun) return false
        if (!surface.matches(targetId, planIds)) return false
        if (focusedRunRecipeId != recipeId || focusedRunInstanceId != instanceId) return false
        if (activeResourceInstallWizard?.selectedSurface != CardRunSurface.InstallWizard) return false
        surface.reconcile()
        diagnostics.logRecipeEvent(
            "install_wizard_feature_reconcile",
            null,
            mapOf("reason" to reason, "target" to targetId)
        )
        return true
    }

    private fun existingResourceInstallWizardInstanceId(targetResourceId: String, recipeId: String): String? {
        val targetId = KiteResourceInstallRecipes.safeId(targetResourceId)
        activeResourceInstallWizard
            ?.takeIf { context ->
                KiteResourceInstallRecipes.safeId(context.targetResourceId) == targetId &&
                    context.wizardRecipeId == recipeId &&
                    context.wizardInstanceId.isNotBlank()
            }
            ?.wizardInstanceId
            ?.let { return it }
        return CardRunStore.currentForRecipe(recipeId)
            ?.takeIf { resourceInstallWizardRunIsReusable(it, targetId, recipeId) }
            ?.instanceId
    }

    private fun resourceInstallWizardRunIsReusable(
        state: RecipeRuntimeState,
        targetResourceId: String,
        recipeId: String
    ): Boolean =
        state.recipeId == recipeId &&
            state.ownerKind == RecipeRuntimeState.OWNER_KIND_INSTALL_WIZARD &&
            (state.stepId.isNullOrBlank() || KiteResourceInstallRecipes.safeId(state.stepId.orEmpty()) == targetResourceId) &&
            state.status != RecipeRunStatus.Unknown &&
            state.status != RecipeRunStatus.Stopped &&
            state.status != RecipeRunStatus.Completed &&
            state.status != RecipeRunStatus.Failed &&
            state.status != RecipeRunStatus.Stopping &&
            state.status != RecipeRunStatus.BridgeUnavailable

    private fun resourceInstallWizardRecipe(targetResourceId: String, targetName: String): KiteRecipe =
        KiteRecipe(
            id = "resource-install-wizard-${KiteResourceInstallRecipes.safeId(targetResourceId)}",
            name = "$targetName 获取向导",
            description = "管理资源执行队列",
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
    private fun submitInstallPlanAction(intent: KiteInstallPlanActionIntent) {
        when (intent) {
            KiteInstallPlanActionIntent.StartNext -> startNextResourceInstallFromPlan()
            KiteInstallPlanActionIntent.Finish -> {
                currentResourceInstallTargetId = null
                resourceInstallWizardPlanIds = emptyList()
                activeResourceInstallWizard = null
                if (this is CardRunActivity) {
                    closeCardRunTask()
                } else {
                    appNavigator.navigate(AppDestination.Resources)
                }
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
            invalidateResourceRuntimeStateCache()
            showResourceDiscreteToast("${item.name} 已恢复为未获取")
            refreshResourceScreenIfVisible()
            return
        }
        startResourceUninstall(item, recipe, ResourceUninstallContinuation.ResumeInstallWizard)
    }
    private fun resourceRunStateForOperation(resourceId: String, operation: String): RecipeRuntimeState? =
        CardRunStore.currentForRecipe(KiteResourceInstallRecipes.recipeId(resourceId, operation))

    private fun resourceRunRecipeForOperation(item: ResourceItem, operation: String): KiteRecipe? =
        if (operation == KiteResourceInstallRecipes.OP_UNINSTALL) {
            resourceUninstallRecipe(item)
        } else {
            resourceInstallRecipe(item)
        }

    private fun resourceInstallRecipeState(resourceId: String): RecipeRuntimeState? =
        resourceRunStateForOperation(resourceId, KiteResourceInstallRecipes.OP_INSTALL)

    private fun resourceRunIsActive(resourceId: String): Boolean {
        val state = resourceInstallRecipeState(resourceId) ?: return false
        return state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened
    }

    private fun resourceInstallWizardSurfaceActive(): Boolean =
        currentScreen == AppDestination.CardRun &&
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
        if (resourceInstallWizardSurface != null && currentScreen == AppDestination.CardRun) {
            resourceInstallWizardSurface?.reconcile()
        } else {
            showCardRunSurface(resourceInstallWizardRecipeFor(context))
        }
        return true
    }

    private fun createResourceInstallWizardFeatureSurface(): View {
        val context = activeResourceInstallWizard
        val targetId = context?.targetResourceId ?: currentResourceInstallTargetId.orEmpty()
        val planIds = context?.planResourceIds.orEmpty()
            .ifEmpty { resourceInstallWizardPlanIds }
            .ifEmpty { resourceInstallStore.planResourceIds() }
            .ifEmpty { resourceInstallStore.pendingPlanResourceIds() }
        resourceInstallWizardPlanIds = planIds
        resourceInstallWizardSurface?.dispose()
        return ResourceInstallWizardSurface(
            context = this,
            gateway = resourceFeatureGateway,
            targetResourceId = targetId,
            planResourceIds = planIds,
            onPlanAction = ::submitInstallPlanAction,
            onOpenRun = ::openResourceInstallRunSurface,
            onUninstallFailedResource = { resourceId ->
                resourceInstallWizardItem(resourceId)?.let(::showResourceWizardUninstallConfirm)
                    ?: showResourceDiscreteToast("资源信息正在准备")
            },
            onReportUnavailable = { showResourceDiscreteToast("报告正在准备") },
            onLiveTickRequired = ::scheduleForegroundLiveTickIfNeeded
        ).also { resourceInstallWizardSurface = it }.root
    }

    private fun resourceInstallWizardItem(resourceId: String): ResourceItem? =
        cachedResourceCatalog
            ?.firstOrNull { it.id == resourceId }
            ?.let(::projectResourceItemRuntime)
            ?: resourceCatalog(forceRefresh = false).firstOrNull { it.id == resourceId }

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
        val childState = context.selectedInstanceId
            ?.let(CardRunStore::get)
            ?.takeIf { it.recipeId == childRecipe.id }
            ?: CardRunStore.currentForRecipe(childRecipe.id)
            ?: runtimeStates[childRecipe.id]
            ?: return null
        return childRecipe to childState
    }

    private fun openResourceInstallRunSurface(request: ResourceInstallWizardRunRequest) {
        val item = resourceInstallWizardItem(request.resourceId)
            ?: return showResourceDiscreteToast("资源信息正在准备")
        val recipe = resourceRunRecipeForOperation(item, request.operation)
            ?: return showResourceDiscreteToast("这个资源没有可打开的运行记录")
        CardRunStore.registerRecipe(recipe)
        val selectedRun = CardRunStore.get(request.instanceId)
            ?.takeIf { it.recipeId == recipe.id }
            ?: return showResourceDiscreteToast("运行记录已经变化，请重试")
        val selectedState = CardRunStore.selectSurface(selectedRun.instanceId, request.surface)
            ?: return showResourceDiscreteToast("运行记录暂时不可打开")
        runtimeStates[recipe.id] = selectedState
        activeRunInstanceIds[recipe.id] = selectedRun.instanceId
        val context = activeResourceInstallWizard
        if (context == null || focusedRunRecipe()?.runtimeSource != RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = selectedRun.instanceId
            showCardRunSurface(recipe)
            return
        }
        val wizardRecipe = resourceInstallWizardRecipeFor(context)
        activeResourceInstallWizard = context.copy(
            selectedResourceId = item.id,
            selectedOperation = request.operation,
            selectedInstanceId = selectedRun.instanceId,
            selectedSurface = request.surface
        )
        focusedRunRecipeId = wizardRecipe.id
        focusedRunInstanceId = context.wizardInstanceId
        activeRunInstanceIds[wizardRecipe.id] = context.wizardInstanceId
        runtimeStates[wizardRecipe.id] = CardRunStore.update(
            recipe = wizardRecipe,
            status = RecipeRunStatus.Opened,
            instanceId = context.wizardInstanceId,
            surface = request.surface,
            lastMeaningfulOutput = "查看 ${item.name}"
        )
        showCardRunSurface(wizardRecipe)
    }

    private fun showResourceMoreActions(item: ResourceItem) {
        currentResourceDetailId = item.id
        enterScreen(AppDestination.ResourceMore) { showResourceDetail(item.id) }
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(topBar("资源管理", ::requestNavigationBack))
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
            addView(resourceIcon(item).apply {
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
        item.runtimeFacts.installed && !item.runtimeFacts.uninstalling

    private fun resourceItemIsInstalled(item: ResourceItem?): Boolean =
        item?.runtimeFacts?.let { facts -> facts.installed && !facts.uninstalling } == true

    private fun resourcePlanStepIsInstalled(
        resourceId: String,
        catalogById: Map<String, ResourceItem>,
        registryEntry: KiteResourceRegistryEntry?
    ): Boolean =
        registryEntry?.failed != true &&
            (registryEntry?.installed == true || resourceItemIsInstalled(catalogById[resourceId]))

    private fun resourceOpenRecipe(item: ResourceItem): KiteRecipe? =
        resourceOpenRecipeJson(item)?.let { temporaryResourceRecipe(item, "open", it) }

    private fun temporaryResourceRecipe(item: ResourceItem, action: String, template: JSONObject): KiteRecipe {
        val json = JSONObject(template.toString())
        val base = json.optJSONObject("base") ?: JSONObject().also { json.put("base", it) }
        val runtimeId = KiteResourceInstallRecipes.recipeId(item.id, action)
        base.put("id", runtimeId)
        if (base.optString("name").isBlank()) base.put("name", item.name)
        if (base.optString("description").isBlank()) base.put("description", item.description)
        return KiteRecipe.fromJson(json, runtimeSource = KiteRecipe.SOURCE_USER)
            .copy(id = runtimeId, runtimeSource = RESOURCE_OPEN_RUNTIME_SOURCE)
    }

    private fun startResourceOpen(item: ResourceItem, recipe: KiteRecipe) {
        CardRunStore.registerRecipe(recipe)
        CardRunStore.currentForRecipe(recipe.id)
            ?.let { existing ->
                if (existing.status == RecipeRunStatus.Stopping) {
                    invalidateResourceRuntimeStateCache()
                    requestResourceRunStateUiRefresh()
                    showResourceDiscreteToast("${item.name} 正在停止，请稍后再启动")
                    return
                }
                if (!resourceOpenRunIsReusable(existing)) return@let
                focusedRunRecipeId = recipe.id
                focusedRunInstanceId = existing.instanceId
                activeRunInstanceIds[recipe.id] = existing.instanceId
                runtimeStates[recipe.id] = existing
                invalidateResourceRuntimeStateCache()
                requestResourceRunStateUiRefresh()
                if (shouldOpenCardRunTaskFromHome(recipe)) {
                    startActivity(
                        CardRunIntents.launchIntent(
                            context = this,
                            recipeId = recipe.id,
                            instanceId = existing.instanceId,
                            launchSource = CardRunIntents.SOURCE_CARD,
                            autoStart = false
                        )
                    )
                } else {
                    showCardRunSurface(recipe)
                }
                showResourceDiscreteToast("${item.name} 已在运行，正在打开原实例")
                return
            }
        val instanceId = activeRunInstanceIds[recipe.id]
            ?: focusedRunInstanceId?.takeIf { CardRunStore.get(it)?.recipeId == recipe.id }
            ?: recipe.id
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        activeRunInstanceIds[recipe.id] = instanceId
        val state = CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_RESOURCE,
            stepId = item.id
        )
        runtimeStates[recipe.id] = state
        invalidateResourceRuntimeStateCache()
        requestResourceRunStateUiRefresh()
        if (shouldOpenCardRunTaskFromHome(recipe)) {
            startActivity(
                CardRunIntents.launchIntent(
                    context = this,
                    recipeId = recipe.id,
                    instanceId = instanceId,
                    launchSource = CardRunIntents.SOURCE_CARD,
                    autoStart = true
                )
            )
        } else {
            startRecipe(
                recipe,
                state,
                preferredInstanceId = instanceId,
                openConsoleOnStart = recipe.launch.openInstance,
                renderOnStart = recipe.launch.openInstance
            )
        }
        showResourceDiscreteToast("正在打开 ${item.name}")
    }

    private fun handleResourceOpenStopAction(item: ResourceItem) {
        val recipe = resourceOpenRecipe(item)
        if (recipe == null) {
            showResourceDiscreteToast("${item.name} 的运行入口不存在")
            return
        }
        CardRunStore.registerRecipe(recipe)
        val state = CardRunStore.currentForRecipe(recipe.id)?.takeIf { resourceOpenRunIsReusable(it) }
        if (state == null) {
            invalidateResourceRuntimeStateCache()
            showResourceDiscreteToast("${item.name} 没有运行中的实例")
            return
        }
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = state.instanceId
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        stopRecipe(recipe, state, navigateToConsole = false)
        invalidateResourceRuntimeStateCache()
        showResourceDiscreteToast("正在中止 ${item.name}")
    }

    private fun resourceOpenRunIsReusable(state: RecipeRuntimeState): Boolean =
        state.status == RecipeRunStatus.Starting ||
            state.status == RecipeRunStatus.WaitingTerminal ||
            state.status == RecipeRunStatus.Running ||
            state.status == RecipeRunStatus.AlreadyRunning ||
            state.status == RecipeRunStatus.Opened

    private fun resourceOpenRunIsReusable(resourceId: String): Boolean =
        CardRunStore.currentForRecipe(KiteResourceInstallRecipes.recipeId(resourceId, "open"))
            ?.let { resourceOpenRunIsReusable(it) } == true

    private fun addResourceHomeCard(item: ResourceItem) {
        val template = resourceHomeCardTemplate(item)
        if (template == null) {
            showResourceDiscreteToast("${item.name} 暂无首页卡片模板")
            return
        }
        runCatching {
            recipeLoader.addSharedRecipeTemplate(template, "${KiteResourceInstallRecipes.safeId(item.id)}-home")
            currentRecipes = recipeLoader.loadAllRecipes()
            refreshRecipeRuntimeStates(currentRecipes)
            recipeFeatureGateway.invalidateCatalog("resource_home_card_added")
        }.onSuccess {
            showResourceDiscreteToast("已添加 ${item.name} 到首页")
        }.onFailure {
            showResourceDiscreteToast("添加失败：${it.message ?: it.javaClass.simpleName}")
        }
    }

    private fun resourceOpenRecipeJson(item: ResourceItem): JSONObject? =
        resourceManifestLoader.requestOpenRecipeTemplate(item.id)
            ?.let { hydrateResourceRecipeTemplate(item, it) }

    private fun resourceHomeCardTemplate(item: ResourceItem): JSONObject? =
        (
            resourceManifestLoader.requestFirstHomeCardRecipeTemplate(item.id)
                ?: resourceManifestLoader.requestOpenRecipeTemplate(item.id)
            )?.let { hydrateResourceRecipeTemplate(item, it) }

    private fun hydrateResourceRecipeTemplate(item: ResourceItem, template: JSONObject): JSONObject {
        val json = JSONObject(template.toString())
        val base = json.optJSONObject("base") ?: JSONObject().also { json.put("base", it) }
        if (base.optString("name").isBlank()) base.put("name", item.name)
        if (base.optString("description").isBlank()) base.put("description", item.description)
        if (item.iconAsset.isNotBlank()) {
            base.put(
                "icon",
                JSONObject()
                    .put("type", KiteRecipeIcon.TYPE_IMAGE)
                    .put("name", item.iconText.ifBlank { "resource" })
                    .put("source", item.iconAsset)
            )
        }
        val card = json.optJSONObject("card") ?: JSONObject().also { json.put("card", it) }
        if (card.optString("accent").isBlank() || card.optString("accent").equals("primary", ignoreCase = true)) {
            card.put("accent", item.accent.ifBlank { "primary" })
        }
        return json
    }

    private fun resourceManifestRecipeSteps(item: ResourceItem, operation: String): List<KiteRecipeStep> {
        val actions = when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL -> resourceManifestLoader.requestInstallActions(item.id)
            KiteResourceInstallRecipes.OP_UNINSTALL -> resourceManifestLoader.requestUninstallActions(item.id)
            else -> emptyList()
        }
        return actions.mapIndexed { index, action ->
            KiteRecipeStep(
                id = "${operation}_${KiteResourceInstallRecipes.safeId(item.id)}_${index + 1}",
                type = KiteRecipe.STEP_SHELL,
                cmd = resourceManifestActionCommand(item, operation, action),
                surfaceMode = action.surfaceMode.ifBlank { KiteRecipe.SURFACE_MODE_PANEL },
                workdir = action.workdir.ifBlank { "/workspace" },
                timeoutMs = action.timeoutMs.takeIf { it > 0L } ?: 1_800_000L
            )
        }
    }

    private fun resourceManifestActionCommand(
        item: ResourceItem,
        operation: String,
        action: KiteResourceShellAction
    ): String =
        when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL -> {
                val bundledCommand = KiteResourceInstallPlanCompiler.bundledCommand(action)
                    ?.let { bundledToolchainManifestInstallCommand(item, it, cleanInstallRoot = false) }
                    ?: bundledToolchainManifestInstallCommand(item, action.cmd, cleanInstallRoot = true)
                val installCommand = bundledCommand ?: KiteResourceInstallPlanCompiler.compile(action)
                KiteResourceInstallRecipes.manifestInstallCommand(
                    resourceId = item.id,
                    displayName = item.name,
                    rawCommand = installCommand,
                    managedCommands = action.managedCommands,
                    cleanInstallRoot = action.cleanInstallRoot,
                    verificationCommand = KiteResourceInstallPlanCompiler.compileVerification(action)
                )
            }
            KiteResourceInstallRecipes.OP_UNINSTALL -> KiteResourceInstallRecipes.manifestUninstallCommand(
                resourceId = item.id,
                rawCommand = action.cmd,
                managedCommands = action.managedCommands,
                npmUninstallPackages = action.npmUninstallPackages
            )
            else -> action.cmd
        }

    private fun bundledToolchainManifestInstallCommand(
        item: ResourceItem,
        command: String,
        cleanInstallRoot: Boolean
    ): String? {
        if (!item.isBundledResource()) return null
        val trimmed = command.trim()
        if (trimmed != "install.sh" && !trimmed.startsWith("install.sh ")) return null
        val mode = trimmed.removePrefix("install.sh").trim().ifBlank { "--install" }
        if (!Regex("""--?[A-Za-z0-9][A-Za-z0-9_-]*""").matches(mode)) return null
        return KiteResourceInstallRecipes.localToolchainCommand(item.id, mode, cleanInstallRoot)
    }

    private fun resourceInstallRecipe(item: ResourceItem): KiteRecipe? {
        val steps = resourceManifestRecipeSteps(item, KiteResourceInstallRecipes.OP_INSTALL)
            .ifEmpty { legacyResourceInstallStep(item)?.let { listOf(it) }.orEmpty() }
        if (steps.isEmpty()) return null
        return KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = item.id,
                name = "${item.name} 获取",
                description = item.description,
                category = "resource",
                iconName = resourceRecipeIcon(item),
                steps = steps
            )
        )
    }

    private fun legacyResourceInstallStep(item: ResourceItem): KiteRecipeStep? =
        when (item.id) {
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
        }

    private fun resourceUninstallRecipe(item: ResourceItem): KiteRecipe? {
        val steps = resourceManifestRecipeSteps(item, KiteResourceInstallRecipes.OP_UNINSTALL)
            .ifEmpty { legacyResourceUninstallStep(item)?.let { listOf(it) }.orEmpty() }
        if (steps.isEmpty()) return null
        return KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = item.id,
                name = "${item.name} 卸载",
                description = item.description,
                category = "resource",
                iconName = resourceRecipeIcon(item),
                operation = KiteResourceInstallRecipes.OP_UNINSTALL,
                actionLabel = "卸载",
                steps = steps
            )
        )
    }

    private fun legacyResourceUninstallStep(item: ResourceItem): KiteRecipeStep? =
        when (item.id) {
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
        }

    private fun resourceRecipeIcon(item: ResourceItem): String =
        when {
            item.id == RESOURCE_GIT || item.id == RESOURCE_CURL || item.id == RESOURCE_UV -> KiteRecipeIcon.ICON_CODE
            item.category == "AI" -> KiteRecipeIcon.ICON_BOT
            item.category == "Node" || item.category == "JavaScript" || item.category == "Python" -> KiteRecipeIcon.ICON_CODE
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
            val registryEntry = resourceInstallStore.registryEntry(runningId)
            if (registryEntry.bootstrapInstallStillRunning() || registryEntry.installMutationIsFresh()) {
                showResourceInstallWizard()
                return
            }
            val recipe = resourceCatalog(forceRefresh = false)
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
                invalidateResourceRuntimeStateCache()
            }
            showResourceInstallWizard()
            return
        }
        val pendingIds = planSnapshot.pendingResourceIds
        if (pendingIds.isEmpty()) return
        val catalog = resourceCatalog(forceRefresh = false)
        val next = pendingIds
            .mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            .firstOrNull()
        if (next == null) {
            resourceInstallStore.clearPlan()
            showResourceDiscreteToast("执行队列缺少资源定义")
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
            showResourceDiscreteToast("${next.name} 的获取脚本尚未接入")
            showResourceInstallWizard()
            return
        }
        if (!resourceInstallStore.markPlanStepRunning(next.id)) {
            showResourceInstallWizard()
            return
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
            showResourceDiscreteToast("资源执行队列完成")
            if (currentResourceInstallTargetId != null) showResourceInstallWizard()
            return
        }
        if (currentResourceInstallTargetId != null) showResourceInstallWizard()
        startNextResourceInstallFromPlan()
    }

    private fun startResourceInstall(item: ResourceItem, recipe: KiteRecipe) {
        resourceInstallStore.markInstalling(item.id)
        invalidateResourceRuntimeStateCache()
        startResourceRun(
            item = item,
            recipe = recipe,
            stageBundledResource = item.isBundledResource(),
            openRunTask = !resourceInstallWizardSurfaceActive(),
            returnToInstallWizard = resourceInstallWizardSurfaceActive()
        )
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
        invalidateResourceRuntimeStateCache()
        startResourceRun(item, recipe, stageBundledResource = false, openRunTask = false)
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
                        showResourceDiscreteToast(message.take(120))
                        refreshResourceScreenIfVisible()
                    }
                }
            }
        }
    }

    private fun refreshResourceScreenIfVisible() {
        if (this is CardRunActivity || !::root.isInitialized || !::resourceInstallStore.isInitialized) return
        invalidateResourceRuntimeStateCache()
        when (currentScreen) {
            AppDestination.Resources -> Unit
            AppDestination.ResourceSearch -> Unit
            AppDestination.ResourceDetail -> Unit
            AppDestination.ResourceMore -> invalidateResourceRuntimeStateCache()
            AppDestination.ResourceManage -> Unit
            else -> Unit
        }
    }

    private fun settleVisibleResourceMutation(reason: String) {
        if (this is CardRunActivity || !::root.isInitialized) return
        when (currentScreen) {
            AppDestination.Resources,
            AppDestination.ResourceSearch,
            AppDestination.ResourceDetail,
            AppDestination.ResourceMore,
            AppDestination.ResourceManage -> refreshResourceScreenIfVisible()
            else -> {
                invalidateResourceRuntimeStateCache()
                resourceCatalogDirty = true
            }
        }
        diagnostics.logRecipeEvent(
            "resource_visible_mutation_settled",
            null,
            mapOf("reason" to reason, "screen" to currentScreen.name)
        )
    }

    private fun ResourceItem.isBundledResource(): Boolean =
        resourceManifestLoader.requestManifest(id)?.sourceType == "bundled"

    private fun resourceIdForRecipe(recipe: KiteRecipe): String? {
        val supportedSource = recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE ||
            recipe.runtimeSource == RESOURCE_OPEN_RUNTIME_SOURCE
        if (!supportedSource) return null
        val raw = recipe.id.removePrefix("resource")
            .trimStart('-')
            .removeSuffix("-${KiteResourceInstallRecipes.OP_INSTALL}")
            .removeSuffix("-${KiteResourceInstallRecipes.OP_UNINSTALL}")
            .removeSuffix("-open")
        return raw.takeIf { it.isNotBlank() }
    }

    private fun resourceIdForOpenRunRecipeId(recipeId: String): String? =
        recipeId
            .takeIf { it.startsWith("resource-") && it.endsWith("-open") }
            ?.removePrefix("resource-")
            ?.removeSuffix("-open")
            ?.takeIf { it.isNotBlank() }

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
                invalidateResourceRuntimeStateCache()
                when (continuation) {
                    ResourceUninstallContinuation.Reinstall -> {
                        val item = resourceCatalog(forceRefresh = false).firstOrNull { it.id == resourceId }
                        if (item == null) {
                            showResourceDiscreteToast("卸载完成，但获取目标缺少资源定义")
                            refreshResourceScreenIfVisible()
                        } else {
                            showResourceDiscreteToast("${item.name} 残留已卸载，继续获取")
                            submitResourceAction(item, KiteResourceActionIntent.Install, KiteResourceActionSource.Wizard)
                        }
                    }
                    ResourceUninstallContinuation.CancelFailedInstall -> {
                        resourceInstallStore.clearPlan()
                        closeResourceInstallWizardInstance(resourceId, removeRunState = true)
                        showResourceDiscreteToast("残留已卸载，获取任务已取消")
                        if (this is CardRunActivity && currentScreen == AppDestination.CardRun) {
                            closeCardRunTask()
                        } else {
                            refreshResourceScreenIfVisible()
                        }
                    }
                    ResourceUninstallContinuation.ResumeInstallWizard -> {
                        resourceInstallStore.resumePlanFrom(resourceId)
                        showResourceDiscreteToast("已卸载异常资源，可继续当前执行队列")
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
        val item = resourceCatalog(forceRefresh = false).firstOrNull { it.id == resourceId }
        val version = item?.version.orEmpty()
        resourceInstallStore.markInstalled(resourceId, version, runId, summary)
        saveInstalledResourceSnapshot(resourceId, item, version)
        invalidateResourceRuntimeStateCache()
        continueResourceInstallPlanAfter(resourceId)
    }

    private fun saveInstalledResourceSnapshot(resourceId: String, item: ResourceItem?, version: String) {
        val manifest = resourceManifestLoader.requestManifest(resourceId)
        val snapshotIconAsset = manifest?.iconAsset?.ifBlank { item?.iconAsset.orEmpty() } ?: item?.iconAsset.orEmpty()
        val snapshotIconText = manifest?.iconText?.ifBlank { item?.iconText.orEmpty() } ?: item?.iconText.orEmpty()
        val snapshotIconFit = manifest?.iconFit?.ifBlank { item?.iconFit.orEmpty() } ?: item?.iconFit.orEmpty()
        val iconJson = JSONObject().apply {
            if (snapshotIconAsset.isNotBlank()) {
                put("type", "asset")
                put("value", snapshotIconAsset)
                put("fallbackText", snapshotIconText)
                if (snapshotIconFit.isNotBlank()) put("fit", snapshotIconFit)
            } else {
                put("type", "text")
                put("value", snapshotIconText)
            }
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
        invalidateResourceRuntimeStateCache()
    }

    private fun normalizeStaleResourceState(
        resourceId: String,
        registryEntry: KiteResourceRegistryEntry? = resourceInstallStore.registryEntry(resourceId)
    ): Boolean {
        val operation = when {
            registryEntry?.preparing == true -> KiteResourceInstallStore.OP_INSTALL
            registryEntry?.installing == true -> KiteResourceInstallStore.OP_INSTALL
            registryEntry?.uninstalling == true -> KiteResourceInstallStore.OP_UNINSTALL
            else -> return false
        }
        if (operation == KiteResourceInstallStore.OP_INSTALL && registryEntry.bootstrapInstallStillRunning()) {
            return false
        }
        if (registryEntry.preparing && registryEntry.preparingMutationIsFresh()) {
            return false
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
            if (registryEntry.installMutationIsFresh()) {
                return false
            }
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

    private fun KiteResourceRegistryEntry?.bootstrapInstallStillRunning(): Boolean =
        this?.runId?.startsWith(ToolchainPackInstaller.BOOTSTRAP_RESOURCE_RUN_PREFIX) == true &&
            ToolchainPackInstaller.state.value.phase == ToolchainInstallPhase.RUNNING

    private fun KiteResourceRegistryEntry?.installMutationIsFresh(): Boolean {
        val updatedAt = this?.updatedAt ?: return false
        return updatedAt > 0L && System.currentTimeMillis() - updatedAt < RESOURCE_INSTALL_STALE_GRACE_MS
    }

    private fun KiteResourceRegistryEntry?.preparingMutationIsFresh(): Boolean {
        val updatedAt = this?.updatedAt ?: return false
        return updatedAt > 0L && System.currentTimeMillis() - updatedAt < RESOURCE_PREPARING_STALE_GRACE_MS
    }

    private fun resourceCatalog(forceRefresh: Boolean = false): List<ResourceItem> {
        val now = System.currentTimeMillis()
        val onMainThread = Looper.myLooper() == Looper.getMainLooper()
        cachedResourceCatalog?.let { cached ->
            val canReuseCleanCatalog = !resourceCatalogDirty &&
                (!forceRefresh || (onMainThread && now - cachedResourceCatalogUpdatedAt < RESOURCE_CATALOG_FORCE_REFRESH_GRACE_MS))
            if (canReuseCleanCatalog) return cached
        }
        val visibleManifests = resourceCatalogManifestsForUi()
        val managedResourceIds = visibleManifests.map { it.id }
        if (managedResourceIds.isEmpty()) {
            cachedResourceCatalog = emptyList()
            cachedResourceCatalogUpdatedAt = now
            resourceCatalogDirty = false
            return emptyList()
        }
        val allowWorkspaceProbe = !onMainThread
        if (allowWorkspaceProbe) {
            ToolchainPackInstaller.refreshState(applicationContext)
        }
        var registrySnapshot = resourceInstallStore.registrySnapshot(managedResourceIds)
        val normalizedAny = managedResourceIds.any { normalizeStaleResourceState(it, registrySnapshot[it]) }
        if (normalizedAny) {
            registrySnapshot = resourceInstallStore.registrySnapshot(managedResourceIds)
        }
        val planSnapshot = resourceInstallStore.planSnapshot()
        fun recordedInstalled(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.installed == true
        fun installFailed(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.failed == true
        fun busy(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.busy == true
        fun runtimeFactsForResource(resourceId: String, idleLabel: String = "未获取"): KiteResourceRuntimeFacts =
            KiteResourceRuntimeFactsProjector.project(
                resourceId = resourceId,
                registryEntry = registrySnapshot[resourceId],
                plan = planSnapshot,
                idleStateLabel = idleLabel
            )
        fun labelsForFacts(resourceId: String, facts: KiteResourceRuntimeFacts): ResourceRuntimeLabels {
            val openRunStatus = if (facts.installed && !facts.installing && !facts.uninstalling && !facts.failed) {
                CardRunStore.currentForRecipe(KiteResourceInstallRecipes.recipeId(resourceId, "open"))?.status
            } else {
                null
            }
            val projection = KiteResourceUiProjector.project(
                installed = facts.installed,
                preparing = facts.preparing,
                installing = facts.installing,
                uninstalling = facts.uninstalling,
                failed = facts.failed,
                failedOperation = facts.failedOperation,
                idleStateLabel = facts.idleStateLabel,
                openRunStatus = openRunStatus,
                extraBusy = facts.extraBusy
            )
            return ResourceRuntimeLabels(
                state = projection.stateLabel,
                action = projection.actionLabel,
                actionEnabled = projection.actionEnabled,
                secondaryAction = projection.secondaryActionLabel
            )
        }
        val toolchain = ToolchainPackInstaller.state.value
        val workspaceSnapshot = toolchainWorkspaceSnapshot(allowProbe = allowWorkspaceProbe)
        val nodeWorkspaceInstalled = workspaceSnapshot.nodeInstalled
        fun restoreInstalledIfWorkspacePresent(
            resourceId: String,
            workspaceInstalled: Boolean,
            version: String,
            summary: String
        ): Boolean {
            if (
                !allowWorkspaceProbe ||
                resourceId !in managedResourceIds ||
                !workspaceInstalled ||
                recordedInstalled(resourceId) ||
                busy(resourceId) ||
                installFailed(resourceId)
            ) {
                return false
            }
            resourceInstallStore.markInstalled(resourceId, version, null, summary)
            return true
        }
        val nodeManifest = visibleManifests.firstOrNull { it.id == RESOURCE_NODE_RUNTIME }
        val workspaceStateNormalized = restoreInstalledIfWorkspacePresent(
            resourceId = RESOURCE_NODE_RUNTIME,
            workspaceInstalled = nodeWorkspaceInstalled,
            version = nodeManifest?.version?.ifBlank { "26.4.0" } ?: "26.4.0",
            summary = "Node.js workspace files verified"
        )
        if (workspaceStateNormalized) {
            registrySnapshot = resourceInstallStore.registrySnapshot(managedResourceIds)
        }
        val toolchainRunning = toolchain.phase == ToolchainInstallPhase.RUNNING
        fun runtimeFactsForManifest(manifest: KiteResourceManifest): KiteResourceRuntimeFacts {
            if (manifest.id != RESOURCE_NODE_RUNTIME) {
                return runtimeFactsForResource(manifest.id, resourceIdleLabelForManifest(manifest))
            }
            val recordedInstalled = recordedInstalled(RESOURCE_NODE_RUNTIME)
            val nodeInstalled = recordedInstalled || nodeWorkspaceInstalled
            return KiteResourceRuntimeFactsProjector.project(
                resourceId = RESOURCE_NODE_RUNTIME,
                registryEntry = registrySnapshot[RESOURCE_NODE_RUNTIME],
                plan = planSnapshot,
                baselineInstalled = nodeInstalled,
                idleStateLabel = "本地包",
                extraBusy = toolchainRunning
            )
        }
        val catalog = visibleManifests.map { manifest ->
            val facts = runtimeFactsForManifest(manifest)
            val labels = labelsForFacts(manifest.id, facts)
            resourceItemFromManifest(
                manifest = manifest,
                labels = labels,
                runtimeFacts = facts
            )
        }
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

    private fun resourceCatalogManifestsForUi(): List<KiteResourceManifest> {
        val manifests = resourceManifestLoader.manifests().values
            .filter { it.sections.isNotEmpty() }
        if (manifests.isEmpty()) return emptyList()
        val byId = manifests.associateBy { it.id }
        val homeOrder = resourceManifestLoader.requestHomeLayout()
            ?.sections
            .orEmpty()
            .flatMap { it.items }
        return (homeOrder + manifests.map { it.id })
            .distinct()
            .mapNotNull { byId[it] }
    }

    // One manifest record plus live registry labels becomes the shared UI projection for every resource template.
    private fun resourceItemFromManifest(
        manifest: KiteResourceManifest,
        labels: ResourceRuntimeLabels,
        runtimeFacts: KiteResourceRuntimeFacts
    ): ResourceItem {
        val sourceLabel = resourceSourceLabel(manifest.sourceType)
            .ifBlank { manifest.sourceType.ifBlank { "本地定义" } }
        return ResourceItem(
            id = manifest.id,
            name = manifest.name.ifBlank { manifest.id },
            description = manifest.description.ifBlank { sourceLabel },
            longDescription = manifest.displayLongDescription.ifBlank {
                manifest.description.ifBlank { manifest.id }
            },
            section = resourceSectionLabel(manifest.sections.firstOrNull()).ifBlank { "更多资源" },
            category = manifest.displayCategory.ifBlank { resourceCategoryForManifest(manifest) },
            iconText = manifest.iconText.ifBlank { resourceFallbackIconText(manifest) },
            iconAsset = manifest.iconAsset,
            iconFit = manifest.iconFit,
            accent = manifest.displayAccent.ifBlank { resourceAccentForManifest(manifest) },
            version = manifest.version.ifBlank { "latest" },
            sizeLabel = manifest.displaySizeLabel.ifBlank { resourceSizeLabelForManifest(manifest) },
            sourceLabel = sourceLabel,
            stateLabel = labels.state,
            actionLabel = labels.action,
            actionEnabled = labels.actionEnabled,
            secondaryActionLabel = labels.secondaryAction,
            runtimeFacts = runtimeFacts,
            includes = resourceIncludesForManifest(manifest),
            notes = resourceNotesForManifest(manifest),
            steps = resourceStepsForManifest(manifest),
            badge = resourceBadgeForManifest(manifest),
            media = resourceMediaForManifest(manifest),
            previewCards = resourcePreviewCardsForManifest(manifest),
            requirementRows = resourceRequirementRowsForManifest(manifest),
            recommendations = manifest.displayRecommendations.map { ResourceRecommendation(it.resourceId, it.label) },
            rawJson = runCatching { manifest.rawJson.toString(2) }.getOrElse { manifest.rawJson.toString() }
        )
    }

    private fun resourceBadgeForManifest(manifest: KiteResourceManifest): ResourceBadge {
        val badge = manifest.displayBadge
        val accent = badge?.accent?.ifBlank { manifest.displayAccent } ?: manifest.displayAccent
        return ResourceBadge(
            label = badge?.label?.ifBlank { "Kite 官方资源" } ?: "Kite 官方资源",
            iconText = badge?.iconText?.ifBlank { "✓" } ?: "✓",
            accent = accent.ifBlank { resourceAccentForManifest(manifest) }
        )
    }

    private fun resourceMediaForManifest(manifest: KiteResourceManifest): ResourceMedia? =
        manifest.displayMedia?.let { media ->
            ResourceMedia(
                type = media.type,
                asset = media.asset,
                contentDescription = media.contentDescription.ifBlank { "${manifest.name} 视觉预览" }
            )
        }

    private fun resourcePreviewCardsForManifest(manifest: KiteResourceManifest): List<ResourcePreviewCard> {
        val previews = manifest.displayPreviewCards.map { it.toResourcePreviewCard(manifest) }
        if (previews.isNotEmpty()) return previews
        val accent = manifest.displayAccent.ifBlank { resourceAccentForManifest(manifest) }
        val iconText = manifest.iconText.ifBlank { resourceFallbackIconText(manifest) }
        return listOf(
            ResourcePreviewCard("工作台", "管理模型、对话和提示词", iconText, accent, manifest.iconAsset, manifest.iconFit),
            ResourcePreviewCard("资源卡片", "一键部署，快速启动", iconText, accent, manifest.iconAsset, manifest.iconFit),
            ResourcePreviewCard("启动访问", "配置完成后直接打开", "✓", accent)
        )
    }

    private fun KiteResourcePreviewSpec.toResourcePreviewCard(manifest: KiteResourceManifest): ResourcePreviewCard {
        val accent = this.accent.ifBlank { manifest.displayAccent.ifBlank { resourceAccentForManifest(manifest) } }
        return ResourcePreviewCard(
            title = title,
            subtitle = subtitle,
            symbol = symbol.ifBlank { manifest.iconText.ifBlank { resourceFallbackIconText(manifest) } },
            accent = accent,
            iconAsset = iconAsset.ifBlank { manifest.iconAsset },
            iconFit = iconFit.ifBlank { manifest.iconFit }
        )
    }

    private fun resourceRequirementRowsForManifest(manifest: KiteResourceManifest): List<ResourceRequirementRow> =
        manifest.displayRequirementRows.map { it.toResourceRequirementRow() }

    private fun KiteResourceDisplayRowSpec.toResourceRequirementRow(): ResourceRequirementRow =
        ResourceRequirementRow(label = label, value = value)

    private fun resourceIdleLabelForManifest(manifest: KiteResourceManifest): String =
        if (manifest.sourceType == "bundled") "本地包" else "未获取"

    private fun resourceCategoryForManifest(manifest: KiteResourceManifest): String {
        val tags = manifest.tags.map { it.lowercase() }.toSet()
        val provides = manifest.provides.map { it.lowercase() }
        return when {
            "ai" in tags || "agent" in tags || "coding-agent" in tags ||
                provides.any { it.startsWith("agent.") } -> "AI"
            provides.any { it.startsWith("runtime.node") || it == "tool.npm" || it == "tool.npx" } -> "JavaScript"
            provides.any {
                it.startsWith("runtime.python") ||
                    it == "tool.pip" ||
                    it == "tool.venv" ||
                    it == "tool.uv" ||
                    it == "tool.uvx"
            } -> "Python"
            else -> "系统工具"
        }
    }

    private fun resourceAccentForManifest(manifest: KiteResourceManifest): String =
        when (resourceCategoryForManifest(manifest)) {
            "AI" -> "teal"
            "JavaScript" -> "green"
            "Python" -> "blue"
            else -> "orange"
        }

    private fun resourceSizeLabelForManifest(manifest: KiteResourceManifest): String =
        when (manifest.sourceType) {
            "bundled" -> "内置包"
            "apt", "npm", "official_script", "git", "command" -> "网络包"
            else -> ""
        }

    private fun resourceFallbackIconText(manifest: KiteResourceManifest): String {
        val name = manifest.name.ifBlank { manifest.id.substringAfterLast('.') }.trim()
        return name.take(2).ifBlank { "R" }
    }

    private fun resourceIncludesForManifest(manifest: KiteResourceManifest): List<String> =
        mergeResourceStrings(
            manifest.provides,
            manifest.installActions.flatMap { it.managedCommands } + manifest.homeCards.map { it.label }
        )

    private fun resourceNotesForManifest(manifest: KiteResourceManifest): List<String> =
        mergeResourceStrings(
            resourceRelationNotes(manifest),
            when (manifest.sourceType) {
                "bundled" -> listOf("来源：内置资源包")
                "apt" -> listOf("来源：Ubuntu apt")
                "npm" -> listOf("来源：npm")
                "official_script" -> listOf("来源：官方安装脚本")
                "git" -> listOf("来源：Git 仓库")
                else -> emptyList()
            }
        )

    private fun resourceStepsForManifest(manifest: KiteResourceManifest): List<ResourceStep> {
        val installSteps = manifest.installActions.mapIndexed { index, action ->
            ResourceStep(
                type = action.type,
                title = resourceInstallActionTitle(manifest, index),
                preview = resourceActionPreview(action)
            )
        }
        if (installSteps.isNotEmpty()) return installSteps
        return listOf(
            ResourceStep(
                type = "open",
                title = "打开资源",
                preview = manifest.openRecipe?.optJSONObject("base")?.optString("name").orEmpty()
                    .ifBlank { manifest.name }
            )
        )
    }

    private fun resourceInstallActionTitle(manifest: KiteResourceManifest, index: Int): String {
        if (index > 0) return "执行获取步骤 ${index + 1}"
        return when (manifest.sourceType) {
            "bundled" -> "安装内置资源"
            "apt" -> "安装 apt 包"
            "npm" -> "安装 npm 包"
            "official_script" -> "执行官方安装器"
            "git" -> "获取源码"
            else -> "执行获取步骤"
        }
    }

    private fun resourceActionPreview(action: KiteResourceShellAction): String =
        KiteResourceInstallPlanCompiler.preview(action)

    private fun resourceSectionLabel(section: String?): String =
        when (section) {
            "foundation" -> "基础环境"
            "ai-vendor" -> "厂商工具"
            "ai-community" -> "独立工具"
            "featured" -> "精选推荐"
            "quick" -> "快速开始"
            "more" -> "更多资源"
            "search-only" -> "仅搜索"
            else -> ""
        }

    private fun resourceSourceLabel(type: String): String =
        when (type) {
            "bundled" -> "内置"
            "apt" -> "apt"
            "official_script" -> "官方脚本"
            "npm" -> "npm"
            "git" -> "GitHub"
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

    private fun consoleShellHeader(): View = LinearLayout(this).apply {
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
        val statusPill = systemStatusPill().also { consoleSystemStatusPillView = it }
        addView(statusPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply {
            setMargins(dp(10), dp(2), 0, 0)
        })
        val openPanel = { showUbuntuRuntimePanel(auto = false, anchor = statusPill) }
        setOnClickListener { openPanel() }
        statusPill.setOnClickListener { openPanel() }
    }

    private fun systemStatusPill(): TextView = TextView(this).apply {
        bindSystemStatusPill(this, ubuntuRuntimeState)
    }

    private fun bindSystemStatusPill(view: TextView, state: UbuntuRuntimeUiState) {
        view.apply {
        text = systemStatusLabel(state)
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        val color = when {
            state.isProblem -> tokens.danger
            state.requiresPermission -> tokens.primaryStrong
            state.blocksUbuntuActions -> tokens.primaryStrong
            state.visible -> tokens.textSecondary
            else -> tokens.success
        }
        setTextColor(color)
        setPadding(dp(8), 0, dp(8), 0)
        background = roundedBox(tintBackground(color), tintBackgroundBorder(color), dp(13).toFloat())
        }
    }

    private fun systemStatusLabel(state: UbuntuRuntimeUiState): String = when {
        state.isProblem -> "异常"
        state.requiresPermission -> "待授权"
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

    /**
     * CardRun surface 的唯一渲染入口(T9 收口证明)。
     *
     * 全部 30 个 CardRun surface 调用点都指向本方法(已收口到单方法,无外部调用),
     * 任何 CardRun 渲染变更只需改这里。内部按 state.surface 分发到
     * Terminal/Web/X11/InstallWizard/default 五种面,并与 TerminalFragment 共生
     * (showCardRunTerminalFragment 复用 CARD_RUN_TERMINAL_FRAGMENT_TAG)。
     *
     * 渲染本身已是容器模式(surfaceHost FrameLayout),与 TerminalFragment 深度共生,
     * 强行再包一层 CardRun Fragment 收益低风险高 —— 故 T8 的 CardRun 以"收口到本方法"
     * 为交付,不强行 Fragment 化。surfaceSignature 去重由 refreshVisibleCardRunSurfaceInsteadOfRebuild 处理。
     */
    private fun showCardRunSurface(recipe: KiteRecipe) {
        val state = focusedRunInstanceId
            ?.let { CardRunStore.get(it) }
            ?: runtimeStateFor(recipe)
        val wizardChildRun = resourceInstallWizardSelectedRun(recipe, state.surface)
        val actionRecipe = wizardChildRun?.first ?: recipe
        val surfaceState = wizardChildRun?.second ?: state
        val surfaceSignature = cardRunSurfaceSignature(recipe, state, actionRecipe, surfaceState)
        applyCardRunSystemBarsForSurface(state.surface)
        if (refreshVisibleCardRunSurfaceInsteadOfRebuild(surfaceSignature, state, surfaceState)) return
        enterScreen(AppDestination.CardRun)
        root.setBackgroundColor(Color.rgb(246, 247, 249))
        clearRootForScreen()
        cardRunSurfaceSignature = surfaceSignature
        val surfaceHost = FrameLayout(this).apply {
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(surfaceHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val terminalSessionId = surfaceState.terminalSessionId?.takeIf { it.isNotBlank() }
        val webUrl = surfaceState.nextActionUrl?.takeIf { it.isNotBlank() }
        if (state.surface == CardRunSurface.Terminal && terminalSessionId != null) {
            applyKiteTerminalTheme()
            surfaceHost.addView(FrameLayout(this).apply {
                id = cardRunTerminalContainerId
                setBackgroundColor(tokens.pageBackground)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            showCardRunTerminalFragment(terminalSessionId)
        } else if (state.surface == CardRunSurface.Terminal) {
            surfaceHost.addView(cardRunLoadingBody("正在准备终端"), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else if (state.surface == CardRunSurface.Web && webUrl != null) {
            showCardRunWebView(surfaceHost, actionRecipe, surfaceState, webUrl)
        } else if (state.surface == CardRunSurface.Web) {
            surfaceHost.addView(
                cardRunWebAddressInputBody(actionRecipe, surfaceState),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        } else if (state.surface == CardRunSurface.X11) {
            surfaceHost.addView(
                cardRunX11SurfaceBody(recipe, surfaceState),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        } else if (state.surface == CardRunSurface.InstallWizard) {
            surfaceHost.addView(
                createResourceInstallWizardFeatureSurface(),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        } else if (wizardChildRun != null) {
            surfaceHost.addView(ScrollView(this).apply {
                addView(cardRunContent(wizardChildRun.first, wizardChildRun.second))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            surfaceHost.addView(ScrollView(this).apply {
                addView(cardRunContent(recipe, state))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        addCardRunFloatingCapsule(surfaceHost, recipe, state, actionRecipe, surfaceState)
    }

    private fun applyCardRunSystemBarsForSurface(surface: CardRunSurface) {
        if (this !is CardRunActivity) return
        val immersive = surface == CardRunSurface.X11
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!immersive)
            window.insetsController?.let { controller ->
                if (immersive) {
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsets.Type.systemBars())
                }
            }
        } else {
            window.decorView.systemUiVisibility = if (immersive) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                0
            }
        }
    }

    private fun restoreCardRunSystemBarsIfNeeded() {
        if (this !is CardRunActivity) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            window.decorView.systemUiVisibility = 0
        }
    }

    private fun cardRunSurfaceSignature(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        actionRecipe: KiteRecipe,
        surfaceState: RecipeRuntimeState
    ): String =
        listOf(
            recipe.id,
            state.instanceId,
            state.surface.name,
            actionRecipe.id,
            surfaceState.instanceId,
            surfaceState.surface.name,
            surfaceState.terminalSessionId.orEmpty(),
            surfaceState.nextActionUrl.orEmpty(),
            surfaceState.x11Display.orEmpty(),
            surfaceState.x11SocketPath.orEmpty(),
            activeResourceInstallWizard?.selectedResourceId.orEmpty(),
            activeResourceInstallWizard?.selectedOperation.orEmpty(),
            activeResourceInstallWizard?.selectedInstanceId.orEmpty(),
            activeResourceInstallWizard?.selectedSurface?.name.orEmpty()
        ).joinToString("|")

    private fun refreshVisibleCardRunSurfaceInsteadOfRebuild(
        surfaceSignature: String,
        state: RecipeRuntimeState,
        surfaceState: RecipeRuntimeState
    ): Boolean {
        if (currentScreen != AppDestination.CardRun || cardRunSurfaceSignature != surfaceSignature) return false
        when (state.surface) {
            CardRunSurface.InstallWizard -> resourceInstallWizardSurface?.reconcile()
            CardRunSurface.Report -> updateVisibleCardRunReport(surfaceState)
            else -> Unit
        }
        return true
    }

    private fun showCardRunLoadingSurface(recipe: KiteRecipe, message: String) {
        enterScreen(AppDestination.CardRun)
        root.setBackgroundColor(Color.rgb(246, 247, 249))
        clearRootForScreen()
        val state = runtimeStateFor(recipe)
        val surfaceHost = FrameLayout(this).apply {
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(surfaceHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        surfaceHost.addView(cardRunLoadingBody(message), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addCardRunFloatingCapsule(surfaceHost, recipe, state, recipe, state)
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
        val currentFragment = supportFragmentManager
            .findFragmentByTag(CARD_RUN_TERMINAL_FRAGMENT_TAG) as? TerminalFragment
        val currentSessionId = currentFragment
            ?.arguments
            ?.getString(TERMINAL_FRAGMENT_INITIAL_SESSION_ARG)
        if (currentFragment?.isAdded == true && !currentFragment.isDetached && currentSessionId == sessionId) {
            return
        }
        val fragment = TerminalFragment.detailOnly(sessionId)
        supportFragmentManager.beginTransaction()
            .replace(cardRunTerminalContainerId, fragment, CARD_RUN_TERMINAL_FRAGMENT_TAG)
            .commitAllowingStateLoss()
    }

    private fun cardRunTopBar(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        actionRecipe: KiteRecipe = recipe,
        actionState: RecipeRuntimeState = state
    ): View = FrameLayout(this).apply {
        val canCompleteCurrentStep = canCompleteCurrentCardStep(actionRecipe, actionState)
        val sideControlSize = dp(44)
        setPadding(dp(16), dp(12), dp(16), dp(8))
        val leftControl = if (canCompleteCurrentStep) {
            cardRunDoneButton { completeCurrentCardStep(actionRecipe, actionState) }
        } else {
            View(context)
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

    private fun addCardRunFloatingCapsule(
        host: FrameLayout,
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        actionRecipe: KiteRecipe,
        actionState: RecipeRuntimeState
    ) {
        var toggleFloatingCapsule: (() -> Unit)? = null
        val capsule = cardRunFloatingCapsule(recipe, state, actionRecipe, actionState) { toggle ->
            toggleFloatingCapsule = toggle
        }
        host.addView(
            capsule,
            FrameLayout.LayoutParams(0, dp(40), Gravity.TOP or Gravity.RIGHT).apply {
                setMargins(0, dp(12), dp(12), 0)
            }
        )
        capsule.bringToFront()
        val handle = cardRunSideFloatingHandle {
            toggleFloatingCapsule?.invoke()
        }
        host.addView(
            handle,
            FrameLayout.LayoutParams(dp(20), dp(64), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
                setMargins(0, 0, dp(4), 0)
            }
        )
        handle.bringToFront()
    }

    private fun cardRunFloatingCapsule(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        actionRecipe: KiteRecipe,
        actionState: RecipeRuntimeState,
        onToggleReady: ((() -> Unit) -> Unit)? = null
    ): View {
        val isWebChrome = actionState.surface == CardRunSurface.Web
        val collapsedWidth = 0
        val collapsedHeight = dp(36)
        val actionCapsuleLength = dp(60)
        val windowCapsuleLength = dp(40)
        val capsuleControlHeight = dp(36)
        val webSearchCapsuleHeight = dp(42)
        val expandedHeight = if (isWebChrome) webSearchCapsuleHeight else dp(40)
        val collapsedTopMargin = dp(12)
        val canComplete = canCompleteCurrentCardStep(actionRecipe, actionState)
        val currentWebUrl = (actionState.nextActionUrl
            ?.takeIf { it.isNotBlank() }
            ?: lastWorkbenchUrl?.takeIf { it.isNotBlank() })
            ?.let { redactUrlCredentials(it) }
            .orEmpty()
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (!isWebChrome && canComplete) {
            actions += "继续" to { completeCurrentCardStep(actionRecipe, actionState) }
        }

        val maxAvailableWidth = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(collapsedWidth)
        val webExpandedWidth = maxAvailableWidth.coerceAtLeast(minOf(dp(260), maxAvailableWidth))
        val capsuleGapWidth = dp(12)
        val expandedWidth = if (isWebChrome) {
            webExpandedWidth
        } else if (actions.isNotEmpty()) {
            (actionCapsuleLength * actions.size) + windowCapsuleLength + (capsuleGapWidth * actions.size)
        } else {
            windowCapsuleLength
        }
        val shell = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f
            alpha = 0f
            clipChildren = false
            clipToPadding = false
        }

        val actionsRow = row {
            gravity = Gravity.CENTER_VERTICAL
        }.apply {
            alpha = 0f
            translationX = dp(14).toFloat()
        }

        var expanded = false
        var touchedByUser = false
        fun setExpanded(open: Boolean) {
            if (expanded == open) return
            expanded = open
            val startWidth = shell.layoutParams?.width?.takeIf { it > 0 } ?: collapsedWidth
            val startHeight = shell.layoutParams?.height?.takeIf { it > 0 } ?: collapsedHeight
            val targetWidth = if (open) expandedWidth else collapsedWidth
            val targetHeight = if (open) expandedHeight else collapsedHeight
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220L
                interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    val easedAlpha = if (open) progress else 1f - progress
                    val currentWidth = (startWidth + (targetWidth - startWidth) * progress).roundToInt()
                    val currentHeight = (startHeight + (targetHeight - startHeight) * progress).roundToInt()
                    shell.layoutParams = shell.layoutParams.apply {
                        width = currentWidth
                        height = currentHeight
                        if (this is FrameLayout.LayoutParams && isWebChrome) {
                            topMargin = collapsedTopMargin - ((currentHeight - collapsedHeight) / 2)
                        }
                    }
                    actionsRow.alpha = easedAlpha
                    actionsRow.translationX = (if (open) 1f - progress else progress) * dp(14)
                    shell.alpha = easedAlpha
                }
            }.start()
        }
        onToggleReady?.invoke {
            touchedByUser = true
            setExpanded(!expanded)
        }

        val rightControls = row {
            gravity = Gravity.CENTER
            background = roundedBox(Color.argb(142, 255, 255, 255), Color.argb(44, 123, 137, 156), dp(18).toFloat(), dp(1))
            elevation = dp(5).toFloat()
            setPadding(dp(3), 0, dp(3), 0)
            addView(
                cardRunCapsuleMark { showCardRunMenu(recipe, state) },
                LinearLayout.LayoutParams(dp(29), ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        val content = row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            if (isWebChrome || actions.isNotEmpty()) {
                addView(actionsRow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(0, 0, capsuleGapWidth, 0)
                })
            }
            addView(
                rightControls,
                LinearLayout.LayoutParams(windowCapsuleLength, capsuleControlHeight)
            )
        }
        if (isWebChrome) {
            if (canComplete) {
                actionsRow.addView(cardRunCapsuleAction("继续") {
                    completeCurrentCardStep(actionRecipe, actionState)
                }, LinearLayout.LayoutParams(actionCapsuleLength, capsuleControlHeight).apply {
                    setMargins(0, 0, capsuleGapWidth, 0)
                })
            }
            val addressChrome = cardRunWebAddressCapsule(currentWebUrl) { input ->
                openCardRunManualWebUrl(actionRecipe, actionState, input.text?.toString().orEmpty())
            }
            val addressLayoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                setMargins(0, 0, dp(2), 0)
            }
            val searchGroup = row {
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBox(Color.argb(142, 255, 255, 255), Color.argb(44, 123, 137, 156), dp(18).toFloat(), dp(1))
                elevation = dp(5).toFloat()
                setPadding(dp(2), 0, dp(2), 0)
                addView(cardRunCapsuleIconAction("↻") {
                    webView.reload()
                }, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                    setMargins(0, 0, dp(5), 0)
                })
                addView(addressChrome.first, addressLayoutParams)
            }
            actionsRow.addView(searchGroup, LinearLayout.LayoutParams(0, webSearchCapsuleHeight, 1f))
        } else {
            actions.forEach { (label, handler) ->
                actionsRow.addView(cardRunCapsuleAction(label) {
                    handler()
                    setExpanded(false)
                }, LinearLayout.LayoutParams(actionCapsuleLength, capsuleControlHeight))
            }
        }
        shell.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (canComplete) {
            shell.postDelayed({
                if (shell.isAttachedToWindow && !touchedByUser) setExpanded(true)
            }, 500L)
        }
        return shell
    }

    private fun cardRunSideFloatingHandle(onClick: () -> Unit): View =
        object : View(this) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(164, 255, 255, 255)
                style = Paint.Style.FILL
            }
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(56, 123, 137, 156)
                style = Paint.Style.STROKE
                strokeWidth = 1f * resources.displayMetrics.density
            }
            private val rect = RectF()
            private var dragVisualProgress = 0f
            private var dragVisualAnimator: ValueAnimator? = null

            fun animateDragVisual(active: Boolean) {
                val start = dragVisualProgress
                val end = if (active) 1f else 0f
                animate()
                    .scaleX(if (active) 1.22f else 1f)
                    .scaleY(if (active) 1.08f else 1f)
                    .setDuration(130L)
                    .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
                dragVisualAnimator?.cancel()
                if (start == end) {
                    postInvalidateOnAnimation()
                    return
                }
                dragVisualAnimator = ValueAnimator.ofFloat(start, end).apply {
                    duration = 130L
                    interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
                    addUpdateListener { animator ->
                        dragVisualProgress = animator.animatedValue as Float
                        postInvalidateOnAnimation()
                    }
                    start()
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val visualWidth = dp(4) + ((dp(14) - dp(4)) * dragVisualProgress)
                val visualHeight = dp(54) + ((dp(4)) * dragVisualProgress)
                val radius = visualWidth / 2f
                rect.set(
                    cx - visualWidth / 2f,
                    cy - visualHeight / 2f,
                    cx + visualWidth / 2f,
                    cy + visualHeight / 2f
                )
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
                canvas.drawRoundRect(rect, radius, radius, strokePaint)
            }
        }.apply handle@{
            setBackgroundColor(Color.TRANSPARENT)
            elevation = dp(7).toFloat()
            isClickable = true
            var pressed = false
            var dragging = false
            var moved = false
            var downRawY = 0f
            var downTop = 0
            var dragStarter: Runnable? = null
            val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
            fun enterDragMode(view: View) {
                if (dragging || !pressed || !view.isAttachedToWindow) return
                dragging = true
                (view.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                this@handle.animateDragVisual(true)
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pressed = true
                        dragging = false
                        moved = false
                        downRawY = event.rawY
                        downTop = view.top
                        val starter = Runnable { enterDragMode(view) }
                        dragStarter = starter
                        view.postDelayed(starter, longPressTimeout)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - downRawY
                        if (kotlin.math.abs(dy) > dp(4)) moved = true
                        if (!dragging && event.eventTime - event.downTime >= longPressTimeout) {
                            enterDragMode(view)
                        }
                        if (dragging) {
                            val parent = view.parent as? ViewGroup
                            val params = view.layoutParams as? FrameLayout.LayoutParams
                            if (parent != null && params != null && parent.height > view.height) {
                                val minTop = dp(12)
                                val maxTop = parent.height - view.height - dp(12)
                                params.gravity = Gravity.RIGHT or Gravity.TOP
                                params.topMargin = (downTop + dy.roundToInt()).coerceIn(minTop, maxTop)
                                params.rightMargin = dp(4)
                                view.layoutParams = params
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        pressed = false
                        dragStarter?.let { view.removeCallbacks(it) }
                        val wasDragging = dragging
                        dragging = false
                        this@handle.animateDragVisual(false)
                        if (!wasDragging && !moved) {
                            view.performClick()
                            onClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        dragging = false
                        dragStarter?.let { view.removeCallbacks(it) }
                        this@handle.animateDragVisual(false)
                        true
                    }
                    else -> false
                }
            }
        }

    private fun cardRunCapsuleAction(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
            background = roundedBox(
                Color.argb(132, 255, 255, 255),
                Color.argb(36, 123, 137, 156),
                dp(16).toFloat(),
                dp(1)
            )
            elevation = dp(5).toFloat()
            setOnClickListener { onClick() }
        }

    private fun cardRunCapsuleIconAction(label: String, onClick: () -> Unit): View =
        TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
            background = roundedBox(Color.argb(132, 255, 255, 255), Color.argb(44, 123, 137, 156), dp(18).toFloat())
            setOnClickListener { onClick() }
        }

    private fun cardRunWebAddressCapsule(currentUrl: String, onSubmit: (EditText) -> Unit): Pair<FrameLayout, EditText> {
        lateinit var input: EditText
        val root = FrameLayout(this).apply {
            background = roundedBox(Color.argb(178, 255, 255, 255), Color.argb(44, 123, 137, 156), dp(18).toFloat())
            input = EditText(context).apply {
                hint = "输入网址"
                textSize = 13f
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                imeOptions = EditorInfo.IME_ACTION_GO
                setTextColor(tokens.textPrimary)
                setHintTextColor(Color.rgb(132, 143, 160))
                background = ColorDrawable(Color.TRANSPARENT)
                includeFontPadding = false
                setPadding(dp(11), 0, dp(6), 0)
                if (currentUrl.isNotBlank()) setText(currentUrl)
                setSelectAllOnFocus(true)
            }
            val submit = {
                onSubmit(input)
            }
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                    submit()
                    true
                } else {
                    false
                }
            }
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 0, dp(34), 0)
            })
            addView(cardRunWebSearchButton { submit() }, FrameLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT))
        }
        return root to input
    }

    private fun cardRunCapsuleMark(onClick: () -> Unit): View =
        object : View(this) {
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 29, 38)
                style = Paint.Style.STROKE
                strokeWidth = 1.9f * resources.displayMetrics.density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val rect = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val size = width.coerceAtMost(height).toFloat()
                val cx = width / 2f
                val cy = height / 2f
                val r = size * 0.22f
                rect.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawRoundRect(rect, r * 0.55f, r * 0.55f, strokePaint)
                canvas.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, strokePaint)
            }
        }.apply {
            alpha = 0.86f
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun cardRunSurfaceTitle(state: RecipeRuntimeState): String =
        when (state.surface) {
            CardRunSurface.Report, CardRunSurface.Summary -> "SH 报告"
            CardRunSurface.Terminal -> "终端"
            CardRunSurface.Web -> "网页"
            CardRunSurface.X11 -> "X11"
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

    private fun showCardRunWebView(
        parentHost: FrameLayout,
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        url: String
    ) {
        val target = url.trim().ifBlank { DEFAULT_LOCAL_URL }
        val displayTarget = redactUrlCredentials(target)
        diagnostics.logOpenWebAttempt(recipe, displayTarget, "card_run_surface")
        diagnostics.writeWebAppStatus(
            url = displayTarget,
            title = recipe.name,
            state = "opening",
            recipeId = recipe.id,
            recipeName = recipe.name,
            openSource = "card_run_surface"
        )
        val decision = BrowserHandoffPolicy.classify(target, "card_run_surface")
        when (decision) {
            is BrowserHandoffDecision.StartAuthHandoff,
            is BrowserHandoffDecision.StartCliCallbackHandoff -> {
                parentHost.addView(
                    cardRunBrowserAuthWaitingBody(recipe, state, target, decision),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                launchBrowserHandoff(
                    request = BrowserHandoffRequest(
                        url = target,
                        recipeId = recipe.id,
                        recipeName = recipe.name,
                        instanceId = state.instanceId,
                        source = "card_run_surface"
                    ),
                    decision = decision,
                    rerenderFocusedSurface = false
                )
                return
            }
            BrowserHandoffDecision.OpenExternalBrowser -> {
                val opened = openCustomTabOrSystemBrowser(Uri.parse(target))
                parentHost.addView(
                    cardRunExternalBrowserBody(recipe, target, opened),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                diagnostics.logRecipeEvent(
                    if (opened) "browser_external_opened" else "browser_external_open_failed",
                    recipe,
                    mapOf(
                        "instanceId" to state.instanceId,
                        "source" to "card_run_surface",
                        "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(target)
                    )
                )
                return
            }
            is BrowserHandoffDecision.ShowUnsupportedFallback,
            BrowserHandoffDecision.StayInWebView -> Unit
        }
        val parent = webView.parent
        if (parent is ViewGroup) parent.removeView(webView)
        parentHost.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val automationEnabled = browserRuntimeMode() == BrowserRuntimeMode.AutomationBrowser
        webShell.loadInWebView(
            target,
            recipeId = recipe.id,
            recipeName = recipe.name,
            instanceId = state.instanceId,
            openSource = "card_run_surface",
            automationEnabled = automationEnabled
        )
    }

    private fun handleBrowserAutomationEvent(event: BrowserAutomationEvent) {
        val session = event.session
        val recipeId = session.recipeId?.takeIf { it.isNotBlank() } ?: return
        val instanceId = session.instanceId?.takeIf { it.isNotBlank() } ?: return
        val recipe = findRecipeById(recipeId) ?: CardRunStore.registeredRecipe(recipeId) ?: return
        val existing = CardRunStore.get(instanceId)
        val status = browserAutomationRunStatus(event, existing?.status)
        val isFatalFailure = event.kind == BrowserAutomationEventKind.Failed
        val actionFailed = event.actionResult?.succeeded == false
        val updated = CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId,
            parentInstanceId = existing?.parentInstanceId,
            ownerKind = existing?.ownerKind,
            stepId = existing?.stepId,
            surface = if (isFatalFailure) CardRunSurface.Report else existing?.surface ?: CardRunSurface.Web,
            currentStepIndex = existing?.currentStepIndex,
            runId = existing?.runId,
            terminalSessionId = existing?.terminalSessionId,
            pid = existing?.pid,
            rootPid = existing?.rootPid,
            processGroupId = existing?.processGroupId,
            systemSessionId = existing?.systemSessionId,
            lastMeaningfulOutput = browserAutomationSummary(event),
            lastError = if (isFatalFailure || actionFailed) event.message.take(500) else null,
            shellReportText = browserAutomationReport(event)
        )
        runtimeStates[recipe.id] = updated
        updateVisibleCardRunReport(updated)
    }

    private fun handleBrowserAutomationActionRequest(action: BrowserAutomationAction): BrowserAutomationActionResult {
        if (browserRuntimeMode() != BrowserRuntimeMode.AutomationBrowser) {
            return BrowserAutomationActionScript.rejectedResult(
                action = action,
                sessionId = action.sessionId ?: action.instanceId ?: "mode_not_enabled",
                errorCode = "mode_not_enabled",
                detail = "当前浏览器模式不是自动浏览器"
            )
        }
        val targetController = browserAutomationControllerFor(action)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val reference = AtomicReference<BrowserAutomationActionResult>()
            targetController.performAction(action) { result ->
                reference.set(result)
            }
            return reference.get() ?: BrowserAutomationActionScript.rejectedResult(
                action = action,
                sessionId = action.sessionId ?: action.instanceId ?: "no_result",
                errorCode = "no_result",
                detail = "自动浏览器没有返回动作结果"
            )
        }
        val latch = CountDownLatch(1)
        val reference = AtomicReference<BrowserAutomationActionResult>()
        runOnUiThread {
            targetController.performAction(action) { result ->
                reference.set(result)
                latch.countDown()
            }
        }
        val timeoutMs = (action.timeoutMs + 1_000L).coerceAtMost(16_000L)
        val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return if (completed) {
            reference.get() ?: BrowserAutomationActionScript.rejectedResult(
                action = action,
                sessionId = action.sessionId ?: action.instanceId ?: "no_result",
                errorCode = "no_result",
                detail = "自动浏览器没有返回动作结果"
            )
        } else {
            BrowserAutomationActionResult(
                actionId = action.actionId,
                sessionId = action.sessionId ?: action.instanceId ?: "timeout",
                type = action.type,
                status = BrowserAutomationResultStatus.TimedOut,
                durationMs = timeoutMs,
                url = "",
                title = null,
                message = "action request timed out",
                errorCode = "request_timeout",
                errorDetail = action.displaySummary()
            )
        }
    }

    private fun browserAutomationControllerFor(action: BrowserAutomationAction): BrowserAutomationController {
        val session = action.sessionId
            ?.takeIf { it.isNotBlank() }
            ?.let(browserAutomationSessions::get)
            ?: action.instanceId
                ?.takeIf { it.isNotBlank() }
                ?.let(browserAutomationSessions::latestForInstance)
            ?: browserAutomationSessions.latestOpenSession()
        return session
            ?.sessionId
            ?.let(BrowserAutomationControllerRegistry::controllerFor)
            ?: BrowserAutomationControllerRegistry.latestController()
            ?: browserAutomationController
    }

    private fun browserAutomationRunStatus(
        event: BrowserAutomationEvent,
        currentStatus: RecipeRunStatus?
    ): RecipeRunStatus =
        when (event.kind) {
            BrowserAutomationEventKind.SessionOpening -> when (currentStatus) {
                RecipeRunStatus.Starting,
                RecipeRunStatus.Running,
                RecipeRunStatus.WaitingTerminal,
                RecipeRunStatus.AlreadyRunning,
                RecipeRunStatus.Opened -> currentStatus
                else -> RecipeRunStatus.Starting
            }
            BrowserAutomationEventKind.SnapshotReady -> when (currentStatus) {
                RecipeRunStatus.Running,
                RecipeRunStatus.WaitingTerminal,
                RecipeRunStatus.AlreadyRunning -> currentStatus
                else -> RecipeRunStatus.Opened
            }
            BrowserAutomationEventKind.ActionFinished -> when (currentStatus) {
                RecipeRunStatus.Running,
                RecipeRunStatus.WaitingTerminal,
                RecipeRunStatus.AlreadyRunning -> currentStatus
                else -> RecipeRunStatus.Opened
            }
            BrowserAutomationEventKind.Failed -> when (currentStatus) {
                RecipeRunStatus.Running,
                RecipeRunStatus.WaitingTerminal,
                RecipeRunStatus.AlreadyRunning -> currentStatus
                else -> RecipeRunStatus.Failed
            }
        }

    private fun browserAutomationSummary(event: BrowserAutomationEvent): String =
        when (event.kind) {
            BrowserAutomationEventKind.SessionOpening -> "自动浏览器正在打开页面"
            BrowserAutomationEventKind.SnapshotReady -> {
                val snapshot = event.snapshot
                val title = snapshot?.title?.takeIf { it.isNotBlank() } ?: snapshot?.url ?: event.session.url
                "自动浏览器已采集页面快照：$title"
            }
            BrowserAutomationEventKind.ActionFinished -> {
                val result = event.actionResult
                if (result?.succeeded == true) {
                    "自动浏览器动作完成：${result.type.wireName}"
                } else {
                    "自动浏览器动作失败：${result?.errorCode ?: "unknown"}"
                }
            }
            BrowserAutomationEventKind.Failed -> event.message.take(500)
        }

    private fun browserAutomationReport(event: BrowserAutomationEvent): String {
        val session = event.session
        val snapshot = event.snapshot
        return buildString {
            appendLine("自动浏览器")
            appendLine("Session: ${session.sessionId}")
            appendLine("状态: ${session.status}")
            appendLine("URL: ${snapshot?.url ?: session.url}")
            if (!snapshot?.title.isNullOrBlank()) appendLine("标题: ${snapshot?.title}")
            if (!snapshot?.readyState.isNullOrBlank()) appendLine("DOM: ${snapshot?.readyState}")
            appendLine("消息: ${event.message}")
            if (!event.errorCode.isNullOrBlank()) appendLine("错误码: ${event.errorCode}")
            event.actionResult?.let { result ->
                appendLine()
                appendLine("动作结果")
                appendLine("Action: ${result.actionId}")
                appendLine("类型: ${result.type.wireName}")
                appendLine("结果: ${result.status}")
                appendLine("耗时: ${result.durationMs}ms")
                appendLine("匹配数量: ${result.matchedCount}")
                appendLine("消息: ${result.message}")
                if (!result.snapshotId.isNullOrBlank()) appendLine("快照: ${result.snapshotId}")
                if (!result.artifactPath.isNullOrBlank()) appendLine("证据文件: ${result.artifactPath}")
                if (!result.errorCode.isNullOrBlank()) appendLine("错误码: ${result.errorCode}")
                if (!result.errorDetail.isNullOrBlank()) appendLine("错误详情: ${result.errorDetail}")
            }
            if (snapshot != null) {
                appendLine()
                appendLine("页面摘要")
                appendLine(snapshot.text.ifBlank { "(页面没有可见文本)" }.take(1200))
                appendLine()
                appendLine("元素摘要: ${snapshot.elementCount} 个 DOM 节点，采样 ${snapshot.elements.size} 个可交互元素")
                snapshot.elements.take(20).forEach { element ->
                    val label = listOfNotNull(
                        element.tag,
                        element.role?.let { "role=$it" },
                        element.type?.let { "type=$it" },
                        element.text?.let { "text=$it" },
                        element.placeholder?.let { "placeholder=$it" },
                        element.ariaLabel?.let { "aria=$it" }
                    ).joinToString(" ")
                    appendLine("- #${element.index} $label visible=${element.visible} enabled=${element.enabled}")
                }
                if (snapshot.accessibility.isNotEmpty()) {
                    appendLine()
                    appendLine("语义摘要: ${snapshot.accessibility.size} 个节点")
                    snapshot.accessibility.take(20).forEach { node ->
                        val state = listOfNotNull(
                            node.level?.let { "level=$it" },
                            node.checked?.takeIf { it.isNotBlank() }?.let { "checked=$it" },
                            node.selected?.let { "selected=$it" },
                            node.expanded?.let { "expanded=$it" }
                        ).joinToString(" ")
                        appendLine("- #${node.index} ${node.role} name=${node.name} tag=${node.tag} enabled=${node.enabled} $state".trim())
                    }
                }
            }
        }.take(4000)
    }

    private fun cardRunBrowserAuthWaitingBody(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        url: String,
        decision: BrowserHandoffDecision
    ): View =
        FrameLayout(this).apply {
            setBackgroundColor(tokens.pageBackground)
            val isLoopback = decision is BrowserHandoffDecision.StartCliCallbackHandoff
            val titleText = if (isLoopback) "正在等待浏览器回调" else "正在等待浏览器登录返回"
            val bodyText = if (isLoopback) {
                "登录页已用安全浏览器打开。回调会通过 Android 本机 loopback 原样交给登录发起方，由发起方校验并保存登录状态。"
            } else {
                "登录页已用安全浏览器打开。返回后 Kite 会校验 state，并把结果交回当前运行实例。"
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(28), dp(24), dp(28))
                addView(ProgressBar(context).apply {
                    isIndeterminate = true
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                })
                addView(TextView(context).apply {
                    text = titleText
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, dp(18), 0, 0)
                })
                addView(TextView(context).apply {
                    text = bodyText
                    textSize = 13f
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, dp(10), 0, 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(row {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, 0)
                    addView(cardRunCapsuleAction("重新打开") {
                        launchBrowserHandoff(
                            request = BrowserHandoffRequest(
                                url = url,
                                recipeId = recipe.id,
                                recipeName = recipe.name,
                                instanceId = state.instanceId,
                                source = "card_run_surface"
                            ),
                            decision = decision,
                            force = true,
                            rerenderFocusedSurface = false
                        )
                    }, LinearLayout.LayoutParams(dp(112), dp(36)))
                    addView(cardRunCapsuleAction("复制地址") {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Kite login URL", url))
                        Toast.makeText(this@MainActivity, "已复制登录地址", Toast.LENGTH_SHORT).show()
                    }, LinearLayout.LayoutParams(dp(112), dp(36)).apply {
                        setMargins(dp(10), 0, 0, 0)
                    })
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

    private fun cardRunExternalBrowserBody(
        recipe: KiteRecipe,
        url: String,
        opened: Boolean
    ): View =
        FrameLayout(this).apply {
            setBackgroundColor(tokens.pageBackground)
            val titleText = if (opened) "已在系统浏览器打开" else "无法打开系统浏览器"
            val bodyText = if (opened) {
                "登录页使用系统浏览器承载。这个授权地址没有配置回到 Kite 的 redirect，完成后请按网页或工具提示继续。"
            } else {
                "Kite 没能启动系统浏览器。可以复制地址后手动打开。"
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(28), dp(24), dp(28))
                addView(TextView(context).apply {
                    text = titleText
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (opened) tokens.textPrimary else tokens.danger)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(context).apply {
                    text = bodyText
                    textSize = 13f
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, dp(10), 0, 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(row {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, 0)
                    addView(cardRunCapsuleAction("重新打开") {
                        val reopened = openCustomTabOrSystemBrowser(Uri.parse(url))
                        diagnostics.logRecipeEvent(
                            if (reopened) "browser_external_reopened" else "browser_external_reopen_failed",
                            recipe,
                            mapOf(
                                "source" to "card_run_surface",
                                "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(url)
                            )
                        )
                    }, LinearLayout.LayoutParams(dp(112), dp(36)))
                    addView(cardRunCapsuleAction("复制地址") {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Kite external URL", url))
                        Toast.makeText(this@MainActivity, "已复制地址", Toast.LENGTH_SHORT).show()
                    }, LinearLayout.LayoutParams(dp(112), dp(36)).apply {
                        setMargins(dp(10), 0, 0, 0)
                    })
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

    private fun launchBrowserHandoff(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision
    ): Boolean =
        launchBrowserHandoff(request, decision, force = false, rerenderFocusedSurface = true)

    private fun launchBrowserHandoff(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision,
        force: Boolean = false,
        rerenderFocusedSurface: Boolean = true
    ): Boolean {
        if (!BrowserHandoffPolicy.isHandoff(decision)) return false
        val existing = if (!force) {
            browserAuthSessions.findPending(request.instanceId, request.url)
        } else {
            null
        }
        if (existing != null) return true

        val session = browserAuthSessions.createPending(request, decision)
        updateBrowserHandoffWaitingState(session, request, rerenderFocusedSurface)
        val loopbackPreparation = if (decision is BrowserHandoffDecision.StartCliCallbackHandoff) {
            browserLoopbackCallbackBridge.prepare(session)
        } else {
            null
        }
        val uri = Uri.parse(request.url)
        val opened = openCustomTabOrSystemBrowser(uri)

        val recipe = request.recipeId
            ?.takeIf { it.isNotBlank() }
            ?.let { findRecipeById(it) ?: CardRunStore.registeredRecipe(it) }
        diagnostics.logRecipeEvent(
            if (opened) "browser_auth_handoff_opened" else "browser_auth_handoff_open_failed",
            recipe,
            mapOf(
                "instanceId" to request.instanceId.orEmpty(),
                "sessionId" to session.sessionId,
                "kind" to session.kind.name,
                "source" to request.source.orEmpty(),
                "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url),
                "callbackChannel" to (loopbackPreparation?.mode?.name ?: "app_redirect"),
                "callbackPort" to loopbackPreparation?.port?.toString().orEmpty()
            )
        )
        if (!opened) {
            browserLoopbackCallbackBridge.stop(session.sessionId)
            browserAuthSessions.markFailed(session.sessionId, "external_browser_open_failed")
            Toast.makeText(this, "无法打开安全浏览器", Toast.LENGTH_LONG).show()
        }
        return opened
    }

    private fun openSystemBrowserForHandoff(uri: Uri): Boolean =
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.isSuccess

    private fun openCustomTabOrSystemBrowser(uri: Uri): Boolean =
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, uri)
        }.isSuccess || openSystemBrowserForHandoff(uri)

    private fun updateBrowserHandoffWaitingState(
        session: BrowserAuthSession,
        request: BrowserHandoffRequest,
        rerenderFocusedSurface: Boolean
    ) {
        val recipe = request.recipeId
            ?.takeIf { it.isNotBlank() }
            ?.let { findRecipeById(it) ?: CardRunStore.registeredRecipe(it) }
            ?: return
        val instanceId = request.instanceId?.takeIf { it.isNotBlank() } ?: return
        val existing = CardRunStore.get(instanceId)
        val status = when (existing?.status) {
            RecipeRunStatus.Starting,
            RecipeRunStatus.Running,
            RecipeRunStatus.WaitingTerminal -> existing.status
            else -> RecipeRunStatus.Opened
        }
        val message = when (session.kind) {
            BrowserAuthSessionKind.CliLoopback -> "已打开安全浏览器，等待登录发起方接收回调"
            BrowserAuthSessionKind.AppRedirect -> "已打开安全浏览器，等待登录返回 Kite"
            BrowserAuthSessionKind.ExternalOnly -> "已打开系统浏览器"
        }
        val preserveTerminalSurface = session.kind == BrowserAuthSessionKind.CliLoopback &&
            existing?.surface == CardRunSurface.Terminal &&
            !existing.terminalSessionId.isNullOrBlank()
        val targetSurface = if (preserveTerminalSurface) CardRunSurface.Terminal else CardRunSurface.Web
        val updated = CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId,
            surface = targetSurface,
            currentStepIndex = existing?.currentStepIndex,
            runId = existing?.runId,
            terminalSessionId = existing?.terminalSessionId,
            pid = existing?.pid,
            lastMeaningfulOutput = message,
            nextActionUrl = request.url.takeUnless { preserveTerminalSurface }
        )
        activeRunInstanceIds[recipe.id] = instanceId
        runtimeStates[recipe.id] = updated
        if (rerenderFocusedSurface && this is CardRunActivity && focusedRunInstanceId == instanceId) {
            showCardRunSurface(recipe)
        }
    }

    private fun cardRunWebAddressInputBody(recipe: KiteRecipe, state: RecipeRuntimeState): View =
        FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            val input = EditText(context).apply {
                hint = "输入网址或本地端口"
                textSize = 15f
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                imeOptions = EditorInfo.IME_ACTION_GO
                setTextColor(tokens.textPrimary)
                setHintTextColor(Color.rgb(150, 160, 176))
                background = ColorDrawable(Color.TRANSPARENT)
                setPadding(dp(18), 0, dp(8), 0)
            }
            val submit = {
                openCardRunManualWebUrl(recipe, state, input.text?.toString().orEmpty())
            }
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                    submit()
                    true
                } else {
                    false
                }
            }
            addView(TextView(context).apply {
                text = "Kite"
                textSize = 33f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(Color.rgb(20, 28, 42))
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(24), 0, dp(24), dp(108))
            })
            addView(FrameLayout(context).apply {
                background = roundedBox(Color.WHITE, Color.rgb(198, 205, 216), dp(26).toFloat(), dp(1))
                elevation = dp(1).toFloat()
                addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    setMargins(0, 0, dp(54), 0)
                })
                addView(cardRunWebSearchButton { submit() }, FrameLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54), Gravity.CENTER).apply {
                setMargins(dp(24), 0, dp(24), 0)
            })
            input.post {
                input.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }

    private fun cardRunWebSearchButton(onClick: () -> Unit): View =
        object : View(this) {
            private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 24, 33)
                style = Paint.Style.STROKE
                strokeWidth = 2.7f * resources.displayMetrics.density
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val size = width.coerceAtMost(height).toFloat()
                val radius = size * 0.16f
                val cx = width * 0.45f
                val cy = height * 0.45f
                canvas.drawCircle(cx, cy, radius, searchPaint)
                canvas.drawLine(
                    cx + radius * 0.72f,
                    cy + radius * 0.72f,
                    cx + radius * 1.65f,
                    cy + radius * 1.65f,
                    searchPaint
                )
            }
        }.apply {
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun openCardRunManualWebUrl(recipe: KiteRecipe, state: RecipeRuntimeState, rawUrl: String) {
        val url = normalizeManualCardRunUrl(rawUrl)
        if (url.isBlank()) {
            Toast.makeText(this, "请输入网页地址", Toast.LENGTH_SHORT).show()
            return
        }
        val rootState = cardRunWindowRootState(state)
        val instanceId = state.instanceId.takeIf { it.isNotBlank() && !it.startsWith("idle_") }
            ?: ensureCardRunWindowInstance(recipe, rootState)
        val status = cardRunManualSurfaceStatus(CardRunStore.get(instanceId) ?: state)
        activeRunInstanceIds[recipe.id] = rootState.instanceId
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        cardRunWindowHiddenSurfaces[rootState.instanceId]?.remove(cardRunWindowItemKey(instanceId, CardRunSurface.Web))
        val updated = CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId,
            surface = CardRunSurface.Web,
            lastMeaningfulOutput = "手动打开网页",
            nextActionUrl = url
        )
        runtimeStates[recipe.id] = updated
        diagnostics.logRecipeAction(
            recipe,
            "card_run_manual_web_open",
            mapOf("instanceId" to instanceId, "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(url))
        )
        showCardRunSurface(recipe)
    }

    private fun normalizeManualCardRunUrl(rawUrl: String): String {
        val value = rawUrl.trim()
        if (value.isBlank()) return ""
        val lower = value.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return value
        if (value.all { it.isDigit() }) return "http://127.0.0.1:$value"
        if (lower.startsWith("localhost") || lower.startsWith("127.0.0.1")) return "http://$value"
        if ("://" in value) return value
        return "https://$value"
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

    private fun handleCardRunBackSignal() {
        val recipe = focusedRunRecipe() ?: return
        val state = focusedRunInstanceId
            ?.let { CardRunStore.get(it) }
            ?: runtimeStateFor(recipe)
        val wizardChildRun = resourceInstallWizardSelectedRun(recipe, state.surface)
        val actionRecipe = wizardChildRun?.first ?: recipe
        val actionState = wizardChildRun?.second ?: state
        if (canCompleteCurrentCardStep(actionRecipe, actionState)) {
            completeCurrentCardStep(actionRecipe, actionState)
            return
        }
        closeCardRunTask()
    }

    private fun canCompleteCurrentCardStep(recipe: KiteRecipe, state: RecipeRuntimeState): Boolean {
        val step = recipe.steps.getOrNull(state.currentStepIndex) ?: return false
        return when (step.type) {
            KiteRecipe.STEP_SHELL -> state.surface == CardRunSurface.Report &&
                state.currentStepIndex < recipe.steps.lastIndex &&
                !state.shellReportText.isNullOrBlank() &&
                (state.status == RecipeRunStatus.Running || state.status == RecipeRunStatus.AlreadyRunning)
            KiteRecipe.STEP_TERMINAL -> state.status == RecipeRunStatus.WaitingTerminal && !state.terminalSessionId.isNullOrBlank()
            KiteRecipe.STEP_OPEN_WEB -> state.surface == CardRunSurface.Web && !state.nextActionUrl.isNullOrBlank()
            KiteRecipe.STEP_X11 -> state.surface == CardRunSurface.X11 && !state.x11Display.isNullOrBlank()
            else -> false
        }
    }

    private fun completeCurrentCardStep(recipe: KiteRecipe, state: RecipeRuntimeState) {
        val step = recipe.steps.getOrNull(state.currentStepIndex)
        if (step != null && recipeUsesProcessRunOrchestrator(recipe)) {
            val output = when (step.type) {
                KiteRecipe.STEP_TERMINAL -> "终端已由用户标记完成"
                KiteRecipe.STEP_OPEN_WEB -> "网页已由用户标记完成"
                KiteRecipe.STEP_X11 -> "X11 GUI 已由用户标记完成"
                KiteRecipe.STEP_SHELL -> "SH 报告已由用户确认继续"
                else -> "步骤已由用户标记完成"
            }
            diagnostics.logRecipeAction(
                recipe,
                "orchestrated_step_completed_by_user",
                mapOf(
                    "type" to step.type,
                    "instanceId" to state.instanceId,
                    "stepIndex" to state.currentStepIndex.toString()
                )
            )
            runOrchestrator.completeCurrentStep(state.instanceId, output)
            return
        }
        val pending = if (step?.type == KiteRecipe.STEP_TERMINAL) {
            pendingTerminalFlow?.takeIf {
                it.recipeId == recipe.id &&
                    (it.instanceId == state.instanceId || it.sessionId == state.terminalSessionId)
            }
        } else {
            null
        }
        val nextStepIndex = pending?.nextStepIndex ?: (state.currentStepIndex + 1).coerceAtLeast(0)
        val hasNextStep = nextStepIndex < recipe.steps.size
        if (step?.type == KiteRecipe.STEP_TERMINAL) {
            pendingTerminalFlow = pendingTerminalFlow?.takeUnless {
                it.recipeId == recipe.id &&
                    (it.instanceId == state.instanceId || it.sessionId == state.terminalSessionId)
            }
            if (hasNextStep) state.terminalSessionId?.takeIf { it.isNotBlank() }?.let {
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
                KiteRecipe.STEP_X11 -> "X11 GUI 已由用户标记完成"
                KiteRecipe.STEP_SHELL -> "SH 报告已由用户确认继续"
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
                CardRunSurface.X11 -> addView(cardRunX11SurfaceBody(recipe, state))
                else -> addView(cardRunReportPanel(recipe, state))
            }
        }

    private fun x11TaskTitle(recipe: KiteRecipe): String = recipe.name.ifBlank { "X11" }

    private fun cardRunX11SurfaceBody(recipe: KiteRecipe, state: RecipeRuntimeState): View =
        FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            val display = state.x11Display?.takeIf { it.isNotBlank() }
            if (display == null) {
                addView(
                    cardRunPlaceholderPanel(x11TaskTitle(recipe), "DISPLAY=待分配"),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(dp(16), dp(16), dp(16), 0)
                    }
                )
                return@apply
            }
            val binding = KiteX11SurfacePlan.binding(display)
            runCatching {
                KiteX11SurfaceServer.surfaceView(this@MainActivity, binding)
            }.onSuccess { surface ->
                addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }.onFailure { error ->
                addView(
                    cardRunPlaceholderPanel(
                        x11TaskTitle(recipe),
                        listOf(
                            "DISPLAY=${binding.display}",
                            "socket=${binding.socketPath}",
                            "native X11 启动失败：${error.message.orEmpty()}"
                        ).joinToString("\n")
                    ),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(dp(16), dp(16), dp(16), 0)
                    }
                )
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
                val statusBadgeTextView = cardRunStatusBadge(state)
                addView(statusBadgeTextView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)).apply {
                    setMargins(dp(12), 0, 0, 0)
                })
                registerCardRunReportBinding(
                    recipeId = recipe.id,
                    state = state,
                    statusBadgeTextView = statusBadgeTextView
                )
            })
            addView(row {
                gravity = Gravity.BOTTOM
                setPadding(0, dp(14), 0, 0)
                val rawCommand = fullShellCommand(recipe, state)
                val items = listOf(
                    "步骤" to cardRunStepCounter(state),
                    "已运行" to formatRunDuration(state),
                    "当前命令" to currentShellCommand(recipe, state).ifBlank { "--" }
                )
                items.forEachIndexed { index, item ->
                    addView(
                        cardRunSummaryMetric(
                            item.first,
                            item.second,
                            onClick = if (item.first == "当前命令" && rawCommand.isNotBlank()) {
                                { showShellCommandDialog(rawCommand) }
                            } else {
                                null
                            }
                        ) { valueView ->
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
        return TextView(this).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(8), 0, dp(8), 0)
            applyCardRunStatusBadge(this, state)
        }
    }

    private fun applyCardRunStatusBadge(view: TextView, state: RecipeRuntimeState) {
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
        view.text = label
        view.setTextColor(color)
        view.background = roundedBox(backgroundColor, Color.TRANSPARENT, dp(11).toFloat(), 0)
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
                "Hermes 需要访问官方安装脚本、GitHub、PyPI 和 files.pythonhosted.org。请确认当前网络或代理能访问这些域名。"
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
        onClick: (() -> Unit)? = null,
        onValueView: ((TextView) -> Unit)? = null
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
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
        val outputText = cardRunOutputText(state)
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
                    copyTextToClipboard("Kite SH 输出", cardRunOutputText(latest), "已复制 SH 输出")
                })
                if (canCompleteCurrentCardStep(recipe, state)) {
                    addView(reportToolButton("›", "继续") {
                        val latest = CardRunStore.get(state.instanceId) ?: state
                        if (canCompleteCurrentCardStep(recipe, latest)) {
                            completeCurrentCardStep(recipe, latest)
                        }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                        setMargins(dp(12), 0, 0, 0)
                    })
                }
            })
            val outputTextView = TextView(context).apply {
                text = liveCardRunOutputText(outputText)
                minimumHeight = dp(260)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(if (state.failureSummary() != null) tokens.danger else reportText)
                setLineSpacing(dp(3).toFloat(), 1.0f)
                includeFontPadding = true
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val outputScrollView = ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                background = roundedBox(Color.rgb(248, 250, 252), Color.TRANSPARENT, dp(16).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    reportOutputViewportHeight()
                ).apply {
                    setMargins(0, dp(12), 0, 0)
                }
                addView(outputTextView)
            }
            addView(outputScrollView)
            if (isRunning) outputScrollView.post { outputScrollView.fullScroll(View.FOCUS_DOWN) }
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
                        outputScrollView = outputScrollView,
                        footerTextView = footerTextView
                    )
                })
            } else {
                registerCardRunReportBinding(
                    recipeId = recipe.id,
                    state = state,
                    outputTextView = outputTextView,
                    outputScrollView = outputScrollView
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

    private fun showShellCommandDialog(command: String) {
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            addView(TextView(context).apply {
                text = "原始命令"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(ScrollView(context).apply {
                background = roundedBox(Color.rgb(248, 250, 252), Color.TRANSPARENT, dp(14).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)).apply {
                    setMargins(0, dp(12), 0, 0)
                }
                addView(TextView(context).apply {
                    text = command
                    textSize = 12.5f
                    typeface = Typeface.MONOSPACE
                    setTextColor(tokens.textPrimary)
                    setLineSpacing(dp(3).toFloat(), 1.0f)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                })
            })
            addView(row {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setPadding(0, dp(14), 0, 0)
                addView(resourceManageActionButton("复制命令") {
                    copyTextToClipboard("Kite 原始命令", command, "已复制原始命令")
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 0.62f)
                })
                addView(resourceManageActionButton("关闭") { dialog.dismiss() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 0.38f).apply {
                        setMargins(dp(10), 0, 0, 0)
                    }
                })
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        outputScrollView: ScrollView? = null,
        footerTextView: TextView? = null,
        elapsedTextView: TextView? = null,
        statusBadgeTextView: TextView? = null
    ) {
        val current = cardRunReportBinding?.takeIf { it.instanceId == state.instanceId }
        cardRunReportBinding = CardRunReportBinding(
            recipeId = recipeId,
            instanceId = state.instanceId,
            outputTextView = outputTextView ?: current?.outputTextView,
            outputScrollView = outputScrollView ?: current?.outputScrollView,
            footerTextView = footerTextView ?: current?.footerTextView,
            elapsedTextView = elapsedTextView ?: current?.elapsedTextView,
            statusBadgeTextView = statusBadgeTextView ?: current?.statusBadgeTextView
        )
        scheduleForegroundLiveTickIfNeeded()
    }

    private fun updateVisibleCardRunReport(state: RecipeRuntimeState) {
        val binding = cardRunReportBinding ?: return
        if (binding.instanceId != state.instanceId || currentScreen != AppDestination.CardRun) return
        pendingCardRunReportState = state
        scheduleVisibleCardRunReportRefresh()
    }

    private fun scheduleVisibleCardRunReportRefresh() {
        if (!::root.isInitialized || cardRunReportRefreshScheduled) return
        val now = System.currentTimeMillis()
        val elapsed = now - cardRunReportLastRefreshAt
        val delayMs = if (cardRunReportLastRefreshAt == 0L || elapsed >= CARD_RUN_REPORT_REFRESH_INTERVAL_MS) {
            0L
        } else {
            CARD_RUN_REPORT_REFRESH_INTERVAL_MS - elapsed
        }
        val render = Runnable {
            cardRunReportRefreshScheduled = false
            val state = pendingCardRunReportState ?: return@Runnable
            pendingCardRunReportState = null
            renderVisibleCardRunReport(state)
        }
        cardRunReportRefreshScheduled = true
        if (delayMs > 0L) {
            root.postDelayed(render, delayMs)
        } else if (Looper.myLooper() == Looper.getMainLooper()) {
            render.run()
        } else {
            root.post(render)
        }
    }

    private fun renderVisibleCardRunReport(state: RecipeRuntimeState) {
        val binding = cardRunReportBinding ?: return
        if (binding.instanceId != state.instanceId || currentScreen != AppDestination.CardRun) return
        cardRunReportLastRefreshAt = System.currentTimeMillis()
        val outputText = liveCardRunOutputText(cardRunOutputText(state))
        binding.outputTextView?.let { textView ->
            updateCardRunReportOutputText(textView, outputText)
        }
        if (state.isBusy() || state.isActive()) {
            binding.outputScrollView?.let { scrollView ->
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }
        binding.footerTextView?.text = reportFooterLabel(state)
        binding.elapsedTextView?.text = formatRunDuration(state)
        binding.statusBadgeTextView?.let { applyCardRunStatusBadge(it, state) }
        if (state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened) {
            scheduleForegroundLiveTickIfNeeded()
        }
    }

    private fun updateVisibleCardRunReportElapsed(): Boolean {
        val binding = cardRunReportBinding ?: return false
        if (currentScreen != AppDestination.CardRun) return false
        val state = CardRunStore.get(binding.instanceId) ?: return false
        binding.elapsedTextView?.text = formatRunDuration(state)
        binding.footerTextView?.text = reportFooterLabel(state)
        binding.statusBadgeTextView?.let { applyCardRunStatusBadge(it, state) }
        return state.isBusy() || state.isActive() || state.status == RecipeRunStatus.Opened
    }

    private fun scheduleForegroundLiveTickIfNeeded() {
        if (!::root.isInitialized || foregroundLiveTickScheduled) return
        foregroundLiveTickScheduled = true
        root.postDelayed({
            foregroundLiveTickScheduled = false
            val keepReportTick = updateVisibleCardRunReportElapsed()
            val keepWizardTick = resourceInstallWizardSurface?.tick() == true
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

    private fun cardRunOutputText(state: RecipeRuntimeState): String {
        val report = state.shellReportText.orEmpty().trim()
        val output = extractShellOutput(report)
        val fallback = when {
            output.isNotBlank() -> output
            state.lastError.orEmpty().isNotBlank() -> state.lastError.orEmpty()
            state.lastMeaningfulOutput.orEmpty().isNotBlank() -> state.lastMeaningfulOutput.orEmpty()
            else -> "暂无输出。一次性命令请使用“等待结束”，例如 python3 -V。"
        }.normalizeShellStreamForDisplay()
        return buildString {
            append(fallback.withoutKiteProcessMarkers())
            commandHintFor(state)?.let { append("\n\n提示：").append(it) }
        }.trim()
    }

    private fun reportOutputViewportHeight(): Int {
        val screenBoundedHeight = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        return screenBoundedHeight.coerceIn(dp(260), dp(420))
    }

    private fun liveCardRunOutputText(text: String): String =
        text.ifBlank { "暂无输出。" }

    private fun updateCardRunReportOutputText(textView: TextView, outputText: String) {
        val current = textView.text?.toString().orEmpty()
        when {
            current == outputText -> Unit
            current == "暂无输出。" -> textView.text = outputText
            outputText.startsWith(current) -> textView.append(outputText.substring(current.length))
            else -> textView.text = outputText
        }
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
        if (!state.nextActionUrl.isNullOrBlank()) lines += "网页：${redactUrlCredentials(state.nextActionUrl)}"
        if (!hasReport && error.isBlank() && output.isBlank() && state.nextActionUrl.isNullOrBlank()) {
            lines += "暂无输出。一次性命令请使用“等待结束”，例如 python3 -V。"
        }
        return lines.joinToString("\n")
    }

    private fun commandHintFor(state: RecipeRuntimeState): String? {
        val text = listOfNotNull(state.lastError, state.lastMeaningfulOutput, state.shellReportText).joinToString("\n")
        return cardRunCommandHint(state.status, text)
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
        replace(ANSI_ESCAPE_REGEX, "")
            .replace('\r', '\n')
            .lineSequence()
            .joinToString("\n") { it.trimEnd() }
            .trimEnd()

    private fun String.withoutKiteProcessMarkers(): String =
        lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("__kite_root_pid:") ||
                    trimmed.startsWith("__kite_process_group_id:") ||
                    trimmed.startsWith("__kite_system_session_id:")
            }
            .joinToString("\n")
            .trim()

    private fun showCardRunMenu(recipe: KiteRecipe, state: RecipeRuntimeState) {
        showCardRunWindowOverview(recipe, state)
    }

    private fun showCardRunWindowOverview(recipe: KiteRecipe, state: RecipeRuntimeState) {
        val dialog = Dialog(this)
        val rootState = cardRunWindowRootState(state)
        val windowItems = cardRunWindowOverviewItems(recipe, state)
        val pageFill = Color.rgb(246, 248, 252)
        val content = FrameLayout(this).apply {
            setBackgroundColor(pageFill)
            alpha = 0f
            scaleX = 0.96f
            scaleY = 0.96f
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(28), dp(44), dp(28), dp(112))
                addView(row {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = "实例窗口"
                        textSize = 21f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.rgb(20, 24, 33))
                        includeFontPadding = false
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
                addView(TextView(context).apply {
                    text = "管理当前实例中的前端窗口"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(Color.rgb(128, 139, 157))
                    setPadding(0, dp(8), 0, 0)
                })
                addView(cardRunWindowGrid(recipe, state, windowItems, dialog), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(24), 0, 0)
                })
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(
            cardRunWindowOverviewDock(recipe, rootState, dialog),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92), Gravity.BOTTOM)
        )
        dialog.setContentView(content)
        dialog.setOnShowListener {
            content.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(190L)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .start()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun cardRunWindowGrid(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        items: List<CardRunWindowItem>,
        dialog: Dialog
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cardHeight = cardRunWindowCardHeightPx()
            if (items.isEmpty()) {
                addView(cardRunWindowEmptyState(), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(180)
                ))
                return@apply
            }
            items.chunked(2).forEachIndexed { rowIndex, rowItems ->
                addView(row {
                    gravity = Gravity.TOP
                    rowItems.forEachIndexed { index, item ->
                        val card = cardRunWindowCard(recipe, state, item, dialog)
                        addView(card, LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                            setMargins(
                                if (index == 0) 0 else dp(7),
                                0,
                                if (index == 0) dp(7) else 0,
                                0
                            )
                        })
                    }
                    if (rowItems.size == 1) {
                        addView(View(context), LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                            setMargins(dp(7), 0, 0, 0)
                        })
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, if (rowIndex == 0) 0 else dp(16), 0, 0)
                })
            }
        }

    private fun cardRunWindowCardHeightPx(): Int {
        val availableWidth = resources.displayMetrics.widthPixels - dp(56) - dp(14)
        val cardWidth = (availableWidth / 2f).coerceAtLeast(dp(132).toFloat())
        return (cardWidth * 1.34f).roundToInt().coerceIn(dp(210), dp(242))
    }

    private fun cardRunWindowCard(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        item: CardRunWindowItem,
        dialog: Dialog
    ): View =
        LinearLayout(this).apply {
            val selected = cardRunWindowItemSelected(state, item)
            orientation = LinearLayout.VERTICAL
            background = roundedBox(
                Color.WHITE,
                if (selected) Color.rgb(51, 127, 245) else Color.rgb(224, 229, 237),
                dp(22).toFloat(),
                if (selected) dp(3) else dp(1)
            )
            elevation = dp(if (selected) 7 else 3).toFloat()
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(7))
                addView(ImageView(context).apply {
                    setImageResource(cardRunWindowTypeIconRes(item.kind))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = false
                }, LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    setMargins(0, 0, dp(7), 0)
                })
                addView(TextView(context).apply {
                    text = item.title
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(Color.rgb(27, 31, 42))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = "×"
                    textSize = 21f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(Color.rgb(108, 118, 134))
                    setOnClickListener { clicked ->
                        cardRunWindowPress(clicked) {
                            hideCardRunWindowItem(recipe, state, item, dialog)
                        }
                    }
                }, LinearLayout.LayoutParams(dp(28), dp(28)))
            })
            addView(FrameLayout(context).apply {
                addView(ImageView(context).apply {
                    setImageResource(cardRunWindowPreviewRes(item.kind))
                    scaleType = ImageView.ScaleType.FIT_XY
                    adjustViewBounds = false
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(dp(10), 0, dp(10), dp(10))
            })
            addView(TextView(context).apply {
                text = item.caption
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.rgb(132, 142, 158))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(10), 0, dp(10), dp(9))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { clicked ->
                cardRunWindowPress(clicked) {
                    dialog.dismiss()
                    selectCardRunWindowItem(recipe, item)
                }
            }
            translationY = dp(12).toFloat()
            alpha = 0f
            postDelayed({
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180L)
                    .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start()
            }, item.staggerMs)
        }

    private fun cardRunWindowEmptyState(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBox(Color.WHITE, Color.rgb(224, 229, 237), dp(22).toFloat(), dp(1))
            addView(TextView(context).apply {
                text = "暂无打开窗口"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(Color.rgb(42, 49, 64))
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "点底部 + 新建或恢复窗口"
                textSize = 12f
                includeFontPadding = false
                setTextColor(Color.rgb(128, 139, 157))
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, 0)
            })
        }

    private fun cardRunWindowOverviewDock(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        dialog: Dialog
    ): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(22), dp(10), dp(22), dp(12))
            background = roundedTopBox(Color.argb(238, 255, 255, 255), Color.rgb(229, 234, 242), dp(1).toFloat())
            addView(cardRunWindowDockButton("trash", "关闭") {
                closeCardRunWindowInstance(recipe, state, dialog)
            }, LinearLayout.LayoutParams(0, dp(76), 1f))
            addView(cardRunWindowDockButton("plus", "新建") {
                showCardRunWindowCreateBubble(recipe, state, dialog)
            }, LinearLayout.LayoutParams(0, dp(76), 1f))
            addView(cardRunWindowDockButton("back", "返回") {
                dialog.dismiss()
            }, LinearLayout.LayoutParams(0, dp(76), 1f))
        }

    private fun cardRunWindowDockButton(kind: String, label: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            contentDescription = label
            addView(ImageView(context).apply {
                setImageResource(cardRunWindowDockIconRes(kind))
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
            }, LinearLayout.LayoutParams(if (kind == "plus") dp(54) else dp(50), if (kind == "plus") dp(54) else dp(50)))
            setOnClickListener { clicked ->
                cardRunWindowPress(clicked, action)
            }
        }

    private fun closeCardRunWindowInstance(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        dialog: Dialog
    ) {
        val rootState = cardRunWindowRootState(CardRunStore.get(state.instanceId) ?: state)
        val latestRootState = CardRunStore.get(rootState.instanceId) ?: rootState
        dialog.dismiss()
        activeRunInstanceIds[recipe.id] = latestRootState.instanceId
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = latestRootState.instanceId
        if (
            latestRootState.status == RecipeRunStatus.Stopped ||
            latestRootState.status == RecipeRunStatus.Completed ||
            latestRootState.status == RecipeRunStatus.Failed ||
            latestRootState.status == RecipeRunStatus.BridgeUnavailable
        ) {
            closeCardRunTask()
            return
        }
        submitRecipeAction(
            KiteRecipeActionRequest(
                recipe = recipe,
                intent = KiteRecipeActionIntent.Stop,
                source = KiteRecipeActionSource.RunSurface,
                instanceId = latestRootState.instanceId
            )
        )
    }

    private fun showCardRunWindowCreateBubble(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        overviewDialog: Dialog
    ) {
        val bubble = Dialog(this)
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { bubble.dismiss() }
            addView(row {
                gravity = Gravity.CENTER
                addView(cardRunWindowCreateBubbleButton("terminal") {
                    handleCardRunWindowCreateChoice(recipe, state, CardRunSurface.Terminal, overviewDialog, bubble)
                }, LinearLayout.LayoutParams(dp(62), dp(62)).apply {
                    setMargins(0, 0, dp(16), 0)
                })
                addView(cardRunWindowCreateBubbleButton("web") {
                    handleCardRunWindowCreateChoice(recipe, state, CardRunSurface.Web, overviewDialog, bubble)
                }, LinearLayout.LayoutParams(dp(62), dp(62)))
            }.apply {
                isClickable = true
                setOnClickListener { }
                alpha = 0f
                scaleX = 0.72f
                scaleY = 0.72f
                translationY = dp(22).toFloat()
                post {
                    animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(180L)
                        .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                        .start()
                }
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(0, 0, 0, dp(112))
            })
        }
        bubble.setContentView(overlay)
        bubble.show()
        bubble.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        bubble.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        bubble.window?.setDimAmount(0f)
        bubble.window?.decorView?.setPadding(0, 0, 0, 0)
        bubble.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun cardRunWindowCreateBubbleButton(kind: String, action: () -> Unit): View =
        FrameLayout(this).apply {
            val isTerminal = kind == "terminal"
            contentDescription = if (isTerminal) "新建终端" else "新建网页"
            background = roundedBox(
                if (isTerminal) Color.rgb(34, 184, 98) else Color.rgb(59, 130, 246),
                Color.TRANSPARENT,
                dp(31).toFloat(),
                0
            )
            elevation = dp(6).toFloat()
            addView(cardRunWindowCreateBubbleGlyph(kind), FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
            setOnClickListener { clicked ->
                cardRunWindowPress(clicked, action)
            }
        }

    private fun cardRunWindowCreateBubbleGlyph(kind: String): View =
        object : View(this) {
            private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                glyphPaint.style = Paint.Style.STROKE
                glyphPaint.strokeWidth = 2.5f * resources.displayMetrics.density
                if (kind == "terminal") {
                    canvas.drawLine(w * 0.24f, h * 0.34f, w * 0.43f, h * 0.50f, glyphPaint)
                    canvas.drawLine(w * 0.24f, h * 0.66f, w * 0.43f, h * 0.50f, glyphPaint)
                    canvas.drawLine(w * 0.56f, h * 0.68f, w * 0.78f, h * 0.68f, glyphPaint)
                } else {
                    canvas.drawRoundRect(
                        RectF(w * 0.18f, h * 0.24f, w * 0.82f, h * 0.64f),
                        dp(3).toFloat(),
                        dp(3).toFloat(),
                        glyphPaint
                    )
                    canvas.drawLine(w * 0.50f, h * 0.64f, w * 0.50f, h * 0.78f, glyphPaint)
                    canvas.drawLine(w * 0.34f, h * 0.80f, w * 0.66f, h * 0.80f, glyphPaint)
                }
            }
        }

    private fun handleCardRunWindowCreateChoice(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        surface: CardRunSurface,
        overviewDialog: Dialog,
        bubble: Dialog
    ) {
        val latest = CardRunStore.get(state.instanceId) ?: state
        bubble.dismiss()
        when (surface) {
            CardRunSurface.Terminal -> openCardRunBlankTerminal(recipe, latest, overviewDialog)
            CardRunSurface.Web -> openCardRunBlankWebSurface(recipe, latest, overviewDialog)
            else -> Toast.makeText(this, "暂不支持新建这个窗口", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cardRunWindowOverviewItems(recipe: KiteRecipe, state: RecipeRuntimeState): List<CardRunWindowItem> {
        val rootState = cardRunWindowRootState(state)
        val allItems = cardRunWindowAllItems(recipe, state)
        val hidden = cardRunWindowHiddenSurfaces[rootState.instanceId] ?: return allItems
        val availableKeys = allItems.map { it.key }.toSet()
        hidden.retainAll(availableKeys)
        if (hidden.isEmpty()) {
            cardRunWindowHiddenSurfaces.remove(rootState.instanceId)
            return allItems
        }
        return allItems.filterNot { it.key in hidden }
    }

    private fun cardRunWindowAllItems(recipe: KiteRecipe, state: RecipeRuntimeState): List<CardRunWindowItem> {
        val rootState = cardRunWindowRootState(state)
        val items = mutableListOf<CardRunWindowItem>()
        fun addItem(
            owner: RecipeRuntimeState,
            surface: CardRunSurface,
            kind: String,
            title: String,
            caption: String
        ) {
            items += CardRunWindowItem(
                key = cardRunWindowItemKey(owner.instanceId, surface),
                instanceId = owner.instanceId,
                surface = surface,
                kind = kind,
                title = title,
                caption = caption,
                staggerMs = 40L + (items.size * 40L)
            )
        }
        if (cardRunReportSurfaceAvailable(recipe, rootState)) {
            addItem(rootState, CardRunSurface.Report, "report", "SH 报告", "执行输出")
        }
        if (!rootState.terminalSessionId.isNullOrBlank()) {
            addItem(rootState, CardRunSurface.Terminal, "terminal", "终端", "终端窗口")
        }
        if (!rootState.nextActionUrl.isNullOrBlank() || rootState.surface == CardRunSurface.Web) {
            addItem(
                rootState,
                CardRunSurface.Web,
                "web",
                cardRunWindowWebTitle(rootState.nextActionUrl),
                cardRunWindowWebCaption(rootState.nextActionUrl)
            )
        }
        rootState.x11Display?.takeIf { it.isNotBlank() }?.let { display ->
            addItem(rootState, CardRunSurface.X11, "x11", "X11", "DISPLAY=$display")
        }
        CardRunStore.childrenOf(rootState.instanceId)
            .sortedBy { it.createdAt }
            .forEach { child ->
                if (!child.terminalSessionId.isNullOrBlank()) {
                    addItem(child, CardRunSurface.Terminal, "terminal", "终端", "终端窗口")
                } else if (!child.nextActionUrl.isNullOrBlank() || child.surface == CardRunSurface.Web) {
                    addItem(
                        child,
                        CardRunSurface.Web,
                        "web",
                        cardRunWindowWebTitle(child.nextActionUrl),
                        cardRunWindowWebCaption(child.nextActionUrl)
                    )
                } else if (!child.x11Display.isNullOrBlank()) {
                    addItem(child, CardRunSurface.X11, "x11", "X11", "DISPLAY=${child.x11Display}")
                }
            }
        if (items.isEmpty() && rootState.instanceId.isNotBlank()) {
            addItem(
                rootState,
                rootState.surface,
                cardRunWindowKindForSurface(rootState.surface),
                cardRunSurfaceTitle(rootState),
                rootState.status.label
            )
        }
        return items
    }

    private fun cardRunWindowSelectedItem(
        state: RecipeRuntimeState,
        items: List<CardRunWindowItem>
    ): CardRunWindowItem? =
        items.firstOrNull { cardRunWindowItemSelected(state, it) }
            ?: items.firstOrNull()

    private fun hideCardRunWindowItem(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        item: CardRunWindowItem,
        dialog: Dialog
    ) {
        val latest = CardRunStore.get(state.instanceId) ?: state
        val rootState = cardRunWindowRootState(latest)
        val allItems = cardRunWindowAllItems(recipe, latest)
        if (allItems.none { it.key == item.key }) {
            Toast.makeText(this, "窗口已不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val hidden = cardRunWindowHiddenSurfaces.getOrPut(rootState.instanceId) { mutableSetOf() }
        hidden += item.key
        val nextItem = allItems.firstOrNull { it.key != item.key && it.key !in hidden }
        val shouldSwitchSurface = cardRunWindowItemSelected(latest, item)
        if (shouldSwitchSurface) {
            nextItem?.let { selectCardRunWindowItem(recipe, it, render = false) }
        }
        dialog.dismiss()
        showCardRunSurface(recipe)
        showCardRunWindowOverview(recipe, CardRunStore.get(focusedRunInstanceId.orEmpty()) ?: rootState)
    }

    private fun openCardRunBlankWebSurface(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        overviewDialog: Dialog
    ) {
        val rootInstanceId = ensureCardRunWindowInstance(recipe, cardRunWindowRootState(state))
        val instanceId = newCardRunChildInstanceId(rootInstanceId, CardRunSurface.Web)
        CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            parentInstanceId = rootInstanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_WEB,
            stepId = "manual_web"
        )
        val updated = CardRunStore.update(
            recipe = recipe,
            status = RecipeRunStatus.Opened,
            instanceId = instanceId,
            surface = CardRunSurface.Web,
            lastMeaningfulOutput = "等待输入网页地址",
            clearNextActionUrl = true
        )
        activeRunInstanceIds[recipe.id] = rootInstanceId
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        runtimeStates[recipe.id] = updated
        cardRunWindowHiddenSurfaces[rootInstanceId]?.remove(cardRunWindowItemKey(instanceId, CardRunSurface.Web))
        overviewDialog.dismiss()
        showCardRunSurface(recipe)
    }

    private fun openCardRunBlankTerminal(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        overviewDialog: Dialog
    ) {
        val rootState = cardRunWindowRootState(state)
        val rootInstanceId = ensureCardRunWindowInstance(recipe, rootState)
        val instanceId = newCardRunChildInstanceId(rootInstanceId, CardRunSurface.Terminal)
        CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            parentInstanceId = rootInstanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_TERMINAL,
            stepId = "manual_terminal"
        )
        val preparing = CardRunStore.update(
            recipe = recipe,
            status = RecipeRunStatus.Starting,
            instanceId = instanceId,
            surface = CardRunSurface.Terminal,
            lastMeaningfulOutput = "正在创建空白终端"
        )
        activeRunInstanceIds[recipe.id] = rootInstanceId
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        runtimeStates[recipe.id] = preparing
        cardRunWindowHiddenSurfaces[rootInstanceId]?.remove(cardRunWindowItemKey(instanceId, CardRunSurface.Terminal))
        overviewDialog.dismiss()
        showCardRunSurface(recipe)

        val appContext = applicationContext
        thread(name = "KiteCardRunBlankTerminal-${recipe.id.take(28)}", isDaemon = true) {
            val recordResult = runCatching {
                val space = KFWorkspaceManager.ensureDefaultSpace(appContext)
                val record = KFWorkspaceManager.createShellSession(
                    context = appContext,
                    spaceId = space.id,
                    title = "${recipe.name.trim().ifBlank { "Kite" }} · 终端",
                    sourceLabel = recipe.name
                )
                TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
                val environment = runCatching {
                    KiteBrowserProxyInstaller.environment(
                        context = appContext,
                        recipeId = recipe.id,
                        instanceId = rootInstanceId,
                        source = "card_run_blank_terminal"
                    )
                }.getOrDefault(emptyMap())
                record to environment.withTerminalOwner(record.id, instanceId)
            }
            runOnUiThread {
                val current = CardRunStore.get(instanceId)
                if (current == null || current.terminalSessionId?.isNotBlank() == true) return@runOnUiThread
                val (record, environment) = recordResult.getOrElse { error ->
                    val message = "创建终端失败：${error.message ?: error.javaClass.simpleName}"
                    val failed = CardRunStore.update(
                        recipe = recipe,
                        status = RecipeRunStatus.Failed,
                        instanceId = instanceId,
                        surface = CardRunSurface.Report,
                        lastError = message
                    )
                    runtimeStates[recipe.id] = failed
                    Toast.makeText(this, message.take(120), Toast.LENGTH_SHORT).show()
                    showCardRunSurface(recipe)
                    return@runOnUiThread
                }
                TerminalSessionStore.refresh(appContext, force = true)
                if (environment.isNotEmpty()) {
                    TerminalRuntimeHost.setLaunchEnvironmentOverrides(appContext, record.id, environment)
                }
                val updated = CardRunStore.update(
                    recipe = recipe,
                    status = cardRunManualSurfaceStatus(current),
                    instanceId = instanceId,
                    surface = CardRunSurface.Terminal,
                    runId = record.id,
                    terminalSessionId = record.id,
                    lastMeaningfulOutput = "已打开空白终端：${record.title}"
                )
                runtimeStates[recipe.id] = updated
                diagnostics.logRecipeAction(
                    recipe,
                    "card_run_blank_terminal_opened",
                    mapOf("rootInstanceId" to rootInstanceId, "instanceId" to instanceId, "sessionId" to record.id)
                )
                if (currentScreen == AppDestination.CardRun && focusedRunInstanceId == instanceId) {
                    showCardRunSurface(recipe)
                }
            }
        }
    }

    private fun ensureCardRunWindowInstance(recipe: KiteRecipe, state: RecipeRuntimeState): String {
        val stateId = state.instanceId.takeIf { it.isNotBlank() && !it.startsWith("idle_") }
        val instanceId = stateId
            ?: focusedRunInstanceId?.takeIf { CardRunStore.get(it)?.recipeId == recipe.id }
            ?: ensureRunInstanceId(recipe)
        activeRunInstanceIds[recipe.id] = instanceId
        return instanceId
    }

    private fun cardRunWindowRootState(state: RecipeRuntimeState): RecipeRuntimeState =
        state.parentInstanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { CardRunStore.get(it) }
            ?: state

    private fun newCardRunChildInstanceId(parentInstanceId: String, surface: CardRunSurface): String =
        "${parentInstanceId}_${surface.name.lowercase()}_${System.currentTimeMillis()}"

    private fun cardRunWindowItemKey(instanceId: String, surface: CardRunSurface): String =
        "$instanceId:${surface.name}"

    private fun cardRunWindowItemSelected(state: RecipeRuntimeState, item: CardRunWindowItem): Boolean =
        state.instanceId == item.instanceId &&
            cardRunWindowSurfaceSelected(state.surface, item.surface)

    private fun cardRunWindowWebTitle(url: String?): String {
        val host = normalizedWebHost(url).orEmpty()
        return when {
            host.endsWith("baidu.com") -> "百度"
            host.isNotBlank() -> host.substringBeforeLast('.').replaceFirstChar { it.uppercase() }
            else -> "Web"
        }
    }

    private fun cardRunWindowWebCaption(url: String?): String =
        normalizedWebHost(url) ?: "等待输入网址"

    private fun normalizedWebHost(url: String?): String? =
        url
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }

    private fun cardRunManualSurfaceStatus(state: RecipeRuntimeState): RecipeRunStatus =
        when (state.status) {
            RecipeRunStatus.Starting,
            RecipeRunStatus.Running,
            RecipeRunStatus.WaitingTerminal,
            RecipeRunStatus.AlreadyRunning -> state.status
            else -> RecipeRunStatus.Opened
        }

    private fun cardRunWindowSurfaceSelected(current: CardRunSurface, candidate: CardRunSurface): Boolean =
        current == candidate || (current == CardRunSurface.Summary && candidate == CardRunSurface.Report)

    private fun cardRunWindowKindForSurface(surface: CardRunSurface): String =
        when (surface) {
            CardRunSurface.Web -> "web"
            CardRunSurface.Terminal -> "terminal"
            CardRunSurface.X11 -> "x11"
            else -> "report"
        }

    private fun cardRunWindowTypeIconRes(kind: String): Int =
        when (kind) {
            "web" -> R.drawable.card_run_window_icon_web
            "terminal" -> R.drawable.card_run_window_icon_terminal
            "x11" -> R.drawable.card_run_window_icon_terminal
            "report" -> R.drawable.card_run_window_icon_shell
            else -> R.drawable.card_run_window_icon_shell
        }

    private fun cardRunWindowPreviewRes(kind: String): Int =
        when (kind) {
            "web" -> R.drawable.card_run_window_preview_web
            "terminal" -> R.drawable.card_run_window_preview_terminal
            "report" -> R.drawable.card_run_window_preview_shell
            else -> R.drawable.card_run_window_preview_shell
        }

    private fun cardRunWindowDockIconRes(kind: String): Int =
        when (kind) {
            "trash" -> R.drawable.card_run_window_dock_trash
            "plus" -> R.drawable.card_run_window_dock_add
            else -> R.drawable.card_run_window_dock_back
        }

    private fun cardRunWindowPress(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(70L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(110L)
                    .withEndAction(action)
                    .start()
            }
            .start()
    }

    private data class CardRunWindowItem(
        val key: String,
        val instanceId: String,
        val surface: CardRunSurface,
        val kind: String,
        val title: String,
        val caption: String,
        val staggerMs: Long
    )

    private fun cardRunSurfaceMenuActions(
        recipe: KiteRecipe,
        state: RecipeRuntimeState,
        dialog: Dialog
    ): List<CardRunMenuAction> = buildList {
        if (cardRunReportSurfaceAvailable(recipe, state)) {
            add(CardRunMenuAction("SH", "SH 报告") {
                dialog.dismiss()
                selectCardRunSurface(recipe, CardRunSurface.Report)
            })
        }
        if (!state.terminalSessionId.isNullOrBlank()) {
            add(CardRunMenuAction(">_", "终端") {
                dialog.dismiss()
                selectCardRunSurface(recipe, CardRunSurface.Terminal)
            })
        }
        state.nextActionUrl?.takeIf { it.isNotBlank() }?.let { url ->
            add(CardRunMenuAction("◎", cardRunWebSurfaceLabel(url)) {
                dialog.dismiss()
                selectCardRunSurface(recipe, CardRunSurface.Web)
            })
        }
        state.x11Display?.takeIf { it.isNotBlank() }?.let { display ->
            add(CardRunMenuAction("X11", "X11 $display") {
                dialog.dismiss()
                selectCardRunSurface(recipe, CardRunSurface.X11)
            })
        }
    }

    private fun cardRunReportSurfaceAvailable(recipe: KiteRecipe, state: RecipeRuntimeState): Boolean {
        val hasShellStep = recipe.steps.any { step ->
            step.type == KiteRecipe.STEP_SHELL || step.type == KiteRecipe.STEP_ANDROID_ACTION
        }
        return !state.shellReportText.isNullOrBlank() ||
            (hasShellStep && (state.hasRunBinding() || state.isBusy() || !state.lastMeaningfulOutput.isNullOrBlank() || !state.lastError.isNullOrBlank())) ||
            (state.surface == CardRunSurface.Report && (!state.lastMeaningfulOutput.isNullOrBlank() || !state.lastError.isNullOrBlank()))
    }

    private fun cardRunWebSurfaceLabel(url: String): String =
        runCatching {
            val parsed = Uri.parse(url)
            val host = parsed.host.orEmpty()
            val port = parsed.port.takeIf { it > 0 }?.let { " $it" }.orEmpty()
            when {
                host.equals("127.0.0.1", ignoreCase = true) || host.equals("localhost", ignoreCase = true) -> "本地$port"
                host.isNotBlank() -> host.removePrefix("www.").take(14)
                else -> "网页"
            }
        }.getOrDefault("网页")

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

    private fun selectCardRunWindowItem(recipe: KiteRecipe, item: CardRunWindowItem, render: Boolean = true) {
        val target = CardRunStore.get(item.instanceId) ?: return
        val root = cardRunWindowRootState(target)
        cardRunWindowHiddenSurfaces[root.instanceId]?.remove(item.key)
        activeRunInstanceIds[recipe.id] = root.instanceId
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = item.instanceId
        CardRunStore.selectSurface(item.instanceId, item.surface)?.let { state ->
            runtimeStates[recipe.id] = state
        }
        if (render) showCardRunSurface(recipe)
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
            closeResourceInstallWizardAndCancelRuns(activeResourceInstallWizard)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
            return
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
            submitRecipeAction(
                KiteRecipeActionRequest(
                    recipe = focusedRecipe,
                    intent = KiteRecipeActionIntent.Stop,
                    source = KiteRecipeActionSource.RunSurface,
                    instanceId = focusedState.instanceId
                )
            )
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

    private fun updateRuntimeGateOverlay() {
        if (!::rootHost.isInitialized) return
        val state = ubuntuRuntimeState
        if (!shouldShowRuntimeGate(state) || shouldSuppressTransientRuntimeChrome(state)) {
            runtimeGateOverlay?.visibility = View.GONE
            return
        }
        val overlay = runtimeGateOverlay ?: createRuntimeGateOverlay().also { view ->
            runtimeGateOverlay = view
            rootHost.addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        overlay.setBackgroundColor(colorWithAlpha(tokens.pageBackground, 238))
        runtimeGateTitleView?.apply {
            text = state.title.ifBlank { "正在准备 Ubuntu" }
            setTextColor(if (state.isProblem) tokens.danger else tokens.textPrimary)
        }
        runtimeGateDetailView?.apply {
            text = state.detail
            visibility = if (state.detail.isBlank()) View.GONE else View.VISIBLE
        }
        runtimeGateProgressBar?.apply {
            visibility = if (state.showProgress && !state.isProblem) View.VISIBLE else View.GONE
            isIndeterminate = state.progressPercent == null
            progress = state.progressPercent ?: 0
            progressDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
            indeterminateDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
        }
        runtimeGateProgressTextView?.apply {
            text = state.progressText.ifBlank {
                if (state.showProgress && !state.isProblem) "正在执行首次准备" else ""
            }
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        runtimeGateActionButton?.apply {
            val actionVisible = runtimePanelUsesPrimaryAction(state)
            visibility = if (actionVisible) View.VISIBLE else View.GONE
            text = when {
                state.firstRunPermissionOnboarding -> state.permissionActionLabel.ifBlank { "开始授权" }
                state.requiresPermission -> state.permissionActionLabel.ifBlank { "打开授权 / 继续" }
                state.canRetry -> "重新检查 / 继续部署"
                else -> ""
            }
            setOnClickListener { handleRuntimePanelAction() }
        }
    }

    private fun shouldShowRuntimeGate(state: UbuntuRuntimeUiState): Boolean =
        state.visible && (
            state.blocksUbuntuActions ||
                state.requiresPermission ||
                state.firstRunPermissionOnboarding ||
                state.isProblem
            )

    private fun createRuntimeGateOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(20))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            elevation = dp(10).toFloat()
            addView(TextView(context).apply {
                runtimeGateTitleView = this
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                runtimeGateDetailView = this
                textSize = 13f
                setTextColor(tokens.textSecondary)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(12), 0, 0)
            })
            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                runtimeGateProgressBar = this
                max = 100
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                    setMargins(0, dp(18), 0, 0)
                }
            })
            addView(TextView(context).apply {
                runtimeGateProgressTextView = this
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(8), 0, 0)
            })
            addView(TextView(context).apply {
                runtimeGateActionButton = this
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.buttonText)
                background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(16).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                    setMargins(0, dp(18), 0, 0)
                }
            })
        }
        overlay.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            setMargins(dp(24), 0, dp(24), 0)
        })
        return overlay
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

    private fun runtimePanelMetric(
        iconText: String,
        label: String,
        accent: Int,
        bindValue: (TextView) -> Unit
    ): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = iconText
                textSize = 18f
                gravity = Gravity.CENTER
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(accent)
                background = roundedBox(tintBackground(accent), Color.TRANSPARENT, dp(13).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    setMargins(0, 0, dp(12), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = label
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                })
                addView(TextView(context).apply {
                    bindValue(this)
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(tokens.textPrimary)
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun runtimePanelSummary(
        taskSnapshot: TaskManagerSnapshot = TaskManagerStore.snapshot.value
    ): RuntimePanelSummary {
        val health = RuntimeHealthStore.snapshot.value
        val processCount = listOf(
            taskSnapshot.processes.size,
            health.prootTelemetry.processLiveTable.liveTraceeCount,
            health.processResourceSnapshot.processCount,
            health.processSnapshotMergedProcessCount,
            health.roots.sumOf { it.processCount }
        ).maxOrNull() ?: 0
        return RuntimePanelSummary(
            runningCards = CardRunStore.runs.value.count { it.countsAsRuntimePanelCard() },
            runningTerminals = TerminalSessionStore.snapshot.value.liveSessions.size,
            runningProcesses = processCount.coerceAtLeast(0)
        )
    }

    private fun requestRuntimePanelSummaryRefresh(force: Boolean = false) {
        val appContext = applicationContext
        TerminalSessionStore.refresh(appContext, force = force)
        TaskManagerStore.refresh(appContext, force = force)
    }

    private fun renderRuntimePanelCounts(summary: RuntimePanelSummary = runtimePanelSummary()) {
        runtimePanelCardCountView?.text = summary.runningCards.toString()
        runtimePanelTerminalCountView?.text = summary.runningTerminals.toString()
        runtimePanelProcessCountView?.text = summary.runningProcesses.toString()
    }

    private fun runtimePanelUsesPrimaryAction(state: UbuntuRuntimeUiState): Boolean =
        state.firstRunPermissionOnboarding ||
            state.requiresPermission ||
            state.canRetry ||
            (state.visible && !state.blocksUbuntuActions)

    private fun RecipeRuntimeState.countsAsRuntimePanelCard(): Boolean =
        parentInstanceId.isNullOrBlank() && when (status) {
            RecipeRunStatus.Starting,
            RecipeRunStatus.Running,
            RecipeRunStatus.WaitingTerminal,
            RecipeRunStatus.AlreadyRunning,
            RecipeRunStatus.Opened,
            RecipeRunStatus.Stopping -> true
            else -> false
        }

    private fun runManagementHeader(summary: RuntimePanelSummary): View =
        row {
            setPadding(dp(16), dp(12), dp(16), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            addView(iconButton("‹", dp(42), Color.TRANSPARENT, tokens.textPrimary, dp(16)) { requestNavigationBack() })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = "运行管理"
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    includeFontPadding = false
                })
                addView(TextView(context).apply {
                    text = "按卡片整理系统与其他进程"
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(5), 0, 0)
                })
                addView(TextView(context).apply {
                    text = "运行卡片 ${summary.runningCards} · 进程 ${summary.runningProcesses}"
                    textSize = 11.5f
                    setTextColor(tokens.textTertiary)
                    setPadding(0, dp(4), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            })
            addView(iconButton("↻", dp(42), Color.TRANSPARENT, tokens.textPrimary, dp(16)) {
                Toast.makeText(this@MainActivity, "正在刷新运行管理", Toast.LENGTH_SHORT).show()
                scheduleRunManagementLazyRefresh(force = true)
            })
        }

    private fun buildRunManagementGroups(
        runs: List<RecipeRuntimeState> = CardRunStore.runs.value,
        terminalItems: List<TerminalSessionItem> = TerminalSessionStore.snapshot.value.sessions,
        processItems: List<TaskManagerProcessItem> = TaskManagerStore.snapshot.value.processes
    ): List<RunManagementGroup> {
        return runs
            .filter { it.countsAsRunManagementCard() }
            .sortedByDescending { it.updatedAt }
            .map { run ->
                val terminal = run.terminalSessionId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sessionId -> terminalItems.firstOrNull { it.id == sessionId } }
                val boundPids = run.boundProcessIds()
                val ownerId = run.runtimeOwnerIdForRunManagement()
                val unitId = run.runtimeUnitIdForRunManagement()
                val runProcesses = processItems
                    .filter { item -> item.belongsToRun(run, boundPids, ownerId, unitId) }
                    .sortedWith(compareBy<TaskManagerProcessItem> { it.parentPid }.thenBy { it.pid })
                val mainProcess = runProcesses.firstOrNull { it.isMainProcessForRun(boundPids) }
                    ?: runProcesses.firstOrNull()
                val childProcesses = runProcesses.filterNot { it.id == mainProcess?.id }
                RunManagementGroup(
                    run = run,
                    recipe = recipeForRunState(run),
                    terminal = terminal,
                    mainProcess = mainProcess,
                    childProcesses = childProcesses,
                    processCount = listOf(
                        terminal?.processCount ?: 0,
                        runProcesses.size,
                        if (boundPids.isEmpty()) 0 else 1
                    ).maxOrNull()?.coerceAtLeast(0) ?: 0
                )
            }
    }

    private fun runManagementOtherProcessSections(
        groups: List<RunManagementGroup>,
        processItems: List<TaskManagerProcessItem> = TaskManagerStore.snapshot.value.processes
    ): List<Pair<String, List<TaskManagerProcessItem>>> {
        val cardProcessIds = groups
            .flatMap { group -> listOfNotNull(group.mainProcess) + group.childProcesses }
            .mapTo(mutableSetOf()) { it.id }
        val processes = processItems
            .filterNot { it.id in cardProcessIds }
            .sortedWith(compareBy<TaskManagerProcessItem> { it.isRunManagementSystemProcess() }.thenBy { it.pid })
        val systemProcesses = processes.filter { it.isRunManagementSystemProcess() }
        val otherProcesses = processes.filterNot { it.isRunManagementSystemProcess() }
        return listOf(
            "系统" to systemProcesses,
            "其他" to otherProcesses
        ).filter { it.second.isNotEmpty() }
    }

    private fun TaskManagerProcessItem.isRunManagementSystemProcess(): Boolean {
        val text = listOf(
            title,
            sourceLabel,
            runtimeOwnerKindLabel.orEmpty(),
            runtimeUnitId.orEmpty(),
            command,
            commandLine
        ).joinToString(" ").lowercase()
        return runtimeOwnerKindLabel == "后台运行项" ||
            sourceLabel.startsWith("后台") ||
            "容器骨架" in text ||
            "容量工作器" in text ||
            "supervisord" in text ||
            "/runtime/bin/proot" in text ||
            "link2symlink" in text ||
            "/workspace/.kf/system/" in text ||
            "locale-check" in text ||
            "mkdir -p /run/" in text
    }

    private fun RecipeRuntimeState.countsAsRunManagementCard(): Boolean =
        countsAsRuntimePanelCard() ||
            (hasRunBinding() && (status == RecipeRunStatus.Failed || status == RecipeRunStatus.BridgeUnavailable))

    private fun RecipeRuntimeState.boundProcessIds(): Set<Int> =
        listOf(rootPid, pid)
            .mapNotNull { it?.trim()?.toIntOrNull()?.takeIf { value -> value > 0 } }
            .toSet()

    private fun RecipeRuntimeState.runtimeOwnerIdForRunManagement(): String =
        when (ownerKind) {
            RecipeRuntimeState.OWNER_KIND_RESOURCE -> "resource:${runManagementSafeOwnerId(resourceIdForRunManagement() ?: cardInstanceId)}"
            RecipeRuntimeState.OWNER_KIND_TERMINAL -> "terminal:${terminalSessionId ?: runId ?: cardInstanceId}"
            else -> "card:${runManagementSafeOwnerId(cardInstanceId)}"
        }

    private fun RecipeRuntimeState.runtimeUnitIdForRunManagement(): String =
        "card:${cardInstanceId.trim().ifBlank { instanceId }}"

    private fun RecipeRuntimeState.resourceIdForRunManagement(): String? {
        if (recipeId.startsWith("resource-")) {
            return recipeId
                .removePrefix("resource-")
                .removeSuffix("-${KiteResourceInstallRecipes.OP_INSTALL}")
                .removeSuffix("-${KiteResourceInstallRecipes.OP_UNINSTALL}")
                .takeIf { it.isNotBlank() }
        }
        return stepId?.takeIf { it.isNotBlank() }
    }

    private fun runManagementSafeOwnerId(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9_.-]"), "_").ifBlank { "recipe" }

    private fun TaskManagerProcessItem.belongsToRun(
        run: RecipeRuntimeState,
        boundPids: Set<Int>,
        ownerId: String,
        unitId: String
    ): Boolean {
        if (runtimeOwnerId == ownerId) return true
        if (runtimeUnitId == unitId) return true
        val terminalId = run.terminalSessionId?.takeIf { it.isNotBlank() }
        if (terminalId != null && linkedTerminalSessionId == terminalId) return true
        if (boundPids.isEmpty()) return false
        return pid in boundPids ||
            parentPid in boundPids ||
            runtimeRootPid in boundPids
    }

    private fun TaskManagerProcessItem.isMainProcessForRun(boundPids: Set<Int>): Boolean =
        boundPids.isNotEmpty() && (pid in boundPids || runtimeRootPid in boundPids)

    private fun runManagementCard(group: RunManagementGroup): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(16).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(10))
            }
            val run = group.run
            val expanded = runManagementExpandedIds.contains(run.instanceId)
            isClickable = true
            setOnClickListener { toggleRunManagementCard(run.instanceId) }
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(runManagementIcon(run), LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    setMargins(0, 0, dp(12), 0)
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = run.recipeName.ifBlank { run.recipeId.ifBlank { "Kite 卡片" } }
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(TextView(context).apply {
                        text = group.runManagementCardSubtitle()
                        textSize = 11.5f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(4), 0, 0)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                })
                addView(runManagementStatusPill(run.status))
                addView(runManagementChevron(expanded))
            })
            if (expanded) {
                addView(runManagementDetails(group))
            }
        }

    private fun toggleRunManagementCard(instanceId: String) {
        if (runManagementExpandedIds.contains(instanceId)) {
            runManagementExpandedIds.remove(instanceId)
        } else {
            runManagementExpandedIds.add(instanceId)
        }
        showKiteProcessOverview(forceRefresh = false)
    }

    private fun RunManagementGroup.runManagementCardSubtitle(): String =
        "${run.ownerKind.runManagementSourceLabel()} · ${formatLastRunTime(run.createdAt)} 启动"

    private fun String.runManagementSourceLabel(): String = when (this) {
        RecipeRuntimeState.OWNER_KIND_RESOURCE -> "资源"
        RecipeRuntimeState.OWNER_KIND_INSTALL_WIZARD -> "安装"
        RecipeRuntimeState.OWNER_KIND_TERMINAL -> "终端"
        RecipeRuntimeState.OWNER_KIND_WEB -> "网页"
        else -> "首页"
    }

    private fun runManagementIcon(run: RecipeRuntimeState): TextView =
        TextView(this).apply {
            text = when (run.ownerKind) {
                RecipeRuntimeState.OWNER_KIND_RESOURCE -> "≡"
                RecipeRuntimeState.OWNER_KIND_TERMINAL -> ">_"
                RecipeRuntimeState.OWNER_KIND_WEB -> "↗"
                else -> "▦"
            }
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(12).toFloat(), 0)
        }

    private fun runManagementStatusPill(status: RecipeRunStatus): TextView {
        val colors = runManagementStatusColors(status)
        return TextView(this).apply {
            text = status.label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(colors.text)
            background = roundedBox(colors.background, colors.border, dp(11).toFloat(), 0)
            minWidth = dp(58)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply {
                setMargins(dp(10), 0, 0, 0)
            }
            setPadding(dp(12), 0, dp(12), 0)
        }
    }

    private fun runManagementChevron(expanded: Boolean): TextView =
        TextView(this).apply {
            text = if (expanded) "⌃" else "›"
            textSize = 18f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.textTertiary)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(26)).apply {
                setMargins(dp(6), 0, 0, 0)
            }
        }

    private fun runManagementStatusColors(status: RecipeRunStatus): SemanticColors =
        cardRunUiToneColors(KiteCardRunUiProjector.project(status).tone)

    private fun cardRunUiToneColors(tone: KiteRunUiTone): SemanticColors = when (tone) {
        KiteRunUiTone.Info -> SemanticColors(tokens.info, tokens.infoSoft, tokens.infoBorder)
        KiteRunUiTone.Success -> SemanticColors(tokens.success, tokens.successSoft, tokens.successBorder)
        KiteRunUiTone.Warning -> SemanticColors(tokens.warning, tokens.warningSoft, tokens.warningBorder)
        KiteRunUiTone.Danger -> SemanticColors(tokens.danger, tokens.dangerSoft, tokens.dangerBorder)
        KiteRunUiTone.Neutral -> SemanticColors(tokens.textSecondary, tokens.surface, tokens.border)
    }

    private fun runManagementDetails(group: RunManagementGroup): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(46), dp(10), 0, 0)
            val surfaceItems = runManagementSurfaceItems(group)
            surfaceItems
                .filter { it.surface == CardRunSurface.Report }
                .forEach { item ->
                    addView(runManagementSurfaceRow(
                        icon = "SH",
                        title = item.title,
                        subtitle = item.caption,
                        onClick = { openRunManagementSurface(group, item) }
                    ))
                }
            surfaceItems
                .filter { it.surface == CardRunSurface.Terminal }
                .forEach { item ->
                    val expanded = runManagementExpandedTerminalIds.contains(item.key)
                    val terminal = runManagementTerminalForItem(item)
                    addView(runManagementSurfaceRow(
                        icon = ">_",
                        title = item.title,
                        subtitle = runManagementTerminalSubtitle(item, terminal),
                        expanded = expanded,
                        onClick = { toggleRunManagementTerminal(item.key) }
                    ))
                    if (expanded) {
                        addView(runManagementDetailRow(
                            title = terminal?.title ?: item.title,
                            subtitle = terminal?.runManagementSubtitle() ?: item.caption,
                            actions = listOfNotNull(
                                RunManagementAction("打开") { openRunManagementSurface(group, item) },
                                terminal?.let { RunManagementAction("结束", danger = true) { endRunManagementTerminal(it) } }
                            )
                        ))
                    }
                }
            surfaceItems
                .filter { it.surface == CardRunSurface.Web }
                .forEach { item ->
                    addView(runManagementSurfaceRow(
                        icon = "↗",
                        title = item.title,
                        subtitle = item.caption,
                        onClick = { openRunManagementSurface(group, item) }
                    ))
                }
            surfaceItems
                .filter { it.surface == CardRunSurface.X11 }
                .forEach { item ->
                    addView(runManagementSurfaceRow(
                        icon = "X11",
                        title = item.title,
                        subtitle = item.caption,
                        onClick = { openRunManagementSurface(group, item) }
                    ))
                }
            if (group.hasProcessBinding()) {
                val processExpanded = runManagementExpandedProcessIds.contains(group.run.instanceId)
                addView(runManagementSurfaceRow(
                    icon = "PID",
                    title = "进程",
                    subtitle = group.runManagementProcessPreview(),
                    expanded = processExpanded,
                    onClick = { toggleRunManagementProcess(group.run.instanceId) }
                ))
                if (processExpanded) {
                    addView(runManagementProcessDetails(group))
                }
            }
        }

    private fun runManagementSurfaceItems(group: RunManagementGroup): List<CardRunWindowItem> =
        group.recipe
            ?.let { recipe -> cardRunWindowAllItems(recipe, group.run) }
            .orEmpty()
            .filter { item ->
                item.surface == CardRunSurface.Report ||
                    item.surface == CardRunSurface.Terminal ||
                    item.surface == CardRunSurface.Web ||
                    item.surface == CardRunSurface.X11
            }

    private fun runManagementSurfaceRow(
        icon: String,
        title: String,
        subtitle: String,
        expanded: Boolean? = null,
        onClick: () -> Unit
    ): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
            isClickable = true
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = icon
                textSize = if (icon.length <= 2) 11.5f else 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.primaryStrong)
                background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(9).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    setMargins(0, 0, dp(10), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(3), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = when (expanded) {
                    true -> "⌃"
                    false -> "›"
                    null -> "›"
                }
                textSize = 16f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.textTertiary)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(28)).apply {
                    setMargins(dp(6), 0, 0, 0)
                }
            })
        }

    private fun runManagementTerminalForItem(item: CardRunWindowItem): TerminalSessionItem? {
        val sessionId = CardRunStore.get(item.instanceId)
            ?.terminalSessionId
            ?.takeIf { it.isNotBlank() }
            ?: groupTerminalFallbackSessionId(item)
        return sessionId?.let { id ->
            TerminalSessionStore.snapshot.value.sessions.firstOrNull { it.id == id }
        }
    }

    private fun groupTerminalFallbackSessionId(item: CardRunWindowItem): String? =
        if (item.surface == CardRunSurface.Terminal) item.instanceId.takeIf { it.isNotBlank() } else null

    private fun runManagementTerminalSubtitle(item: CardRunWindowItem, terminal: TerminalSessionItem?): String =
        terminal?.let { "${it.statusLabel} · ${it.title}".trimForRunManagement(48) } ?: item.caption

    private fun toggleRunManagementTerminal(key: String) {
        if (runManagementExpandedTerminalIds.contains(key)) {
            runManagementExpandedTerminalIds.remove(key)
        } else {
            runManagementExpandedTerminalIds.add(key)
        }
        showKiteProcessOverview(forceRefresh = false)
    }

    private fun toggleRunManagementProcess(instanceId: String) {
        if (runManagementExpandedProcessIds.contains(instanceId)) {
            runManagementExpandedProcessIds.remove(instanceId)
        } else {
            runManagementExpandedProcessIds.add(instanceId)
        }
        showKiteProcessOverview(forceRefresh = false)
    }

    private fun RunManagementGroup.hasProcessBinding(): Boolean =
        mainProcess != null || childProcesses.isNotEmpty() || run.boundProcessIds().isNotEmpty()

    private fun RunManagementGroup.runManagementProcessPreview(): String =
        buildList {
            val mainPid = mainProcess?.pid?.takeIf { it > 0 } ?: run.boundProcessIds().firstOrNull()
            mainPid?.let { add("主进程 PID $it") }
            if (childProcesses.isNotEmpty()) add("子进程 ${childProcesses.size}")
        }.joinToString(" · ").ifBlank { "已绑定" }

    private fun runManagementProcessDetails(group: RunManagementGroup): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(38), dp(3), 0, dp(2))
            val mainProcess = group.mainProcess
            val fallbackPid = group.run.boundProcessIds().firstOrNull()
            if (mainProcess != null || fallbackPid != null) {
                addView(runManagementDetailRow(
                    title = runManagementProcessTitle(mainProcess, fallbackPid),
                    subtitle = mainProcess?.runManagementProcessSubtitle() ?: "卡片运行链路绑定",
                    actions = listOfNotNull(
                        (mainProcess?.pid ?: fallbackPid)?.takeIf { it > 0 }?.let { pid ->
                            RunManagementAction("结束进程", danger = true) {
                                showRunManagementProcessDialog(mainProcess, pid)
                            }
                        }
                    ),
                    onClick = { showRunManagementProcessDialog(mainProcess, fallbackPid) }
                ))
            }
            if (group.childProcesses.isNotEmpty()) {
                val childExpanded = runManagementExpandedChildProcessIds.contains(group.run.instanceId)
                addView(runManagementChildToggle(group.childProcesses.size, childExpanded) {
                    if (childExpanded) {
                        runManagementExpandedChildProcessIds.remove(group.run.instanceId)
                    } else {
                        runManagementExpandedChildProcessIds.add(group.run.instanceId)
                    }
                    showKiteProcessOverview(forceRefresh = false)
                })
                if (childExpanded) {
                    group.childProcesses.forEach { process ->
                        addView(runManagementDetailRow(
                            title = runManagementProcessTitle(process, process.pid),
                            subtitle = process.runManagementProcessSubtitle(),
                            actions = listOf(
                                RunManagementAction("结束进程", danger = true) {
                                    showRunManagementProcessDialog(process, process.pid)
                                }
                            ),
                            onClick = { showRunManagementProcessDialog(process, process.pid) }
                        ))
                    }
                }
            }
        }

    private fun runManagementProcessSection(title: String, processes: List<TaskManagerProcessItem>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(2))
            addView(runManagementSectionTitle("$title · ${processes.size}"))
            processes.forEach { process ->
                addView(runManagementSurfaceRow(
                    icon = "PID",
                    title = runManagementProcessTitle(process, process.pid),
                    subtitle = process.runManagementProcessSubtitle(),
                    onClick = { showRunManagementProcessDialog(process, process.pid) }
                ))
            }
        }

    private fun runManagementSectionTitle(title: String): TextView =
        TextView(this).apply {
            text = title
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            setPadding(0, dp(13), 0, dp(7))
        }

    private fun runManagementDetailRow(
        title: String,
        subtitle: String,
        actions: List<RunManagementAction>,
        onClick: (() -> Unit)? = null
    ): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
            onClick?.let { click ->
                isClickable = true
                setOnClickListener { click() }
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 13f
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(4), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            actions.forEach { action ->
                addView(runManagementActionButton(action).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                        setMargins(dp(8), 0, 0, 0)
                    }
                })
            }
        }

    private fun runManagementActionButton(action: RunManagementAction): TextView =
        TextView(this).apply {
            text = action.label
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            val textColor = if (action.danger) tokens.warning else tokens.textPrimary
            setTextColor(textColor)
            background = roundedBox(
                if (action.danger) tokens.warningSoft else tokens.surface,
                if (action.danger) tokens.warningBorder else tokens.border,
                dp(12).toFloat(),
                0
            )
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30))
            setOnClickListener { action.onClick() }
        }

    private fun runManagementChildToggle(count: Int, expanded: Boolean, onClick: () -> Unit): View =
        row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(5))
            addView(TextView(context).apply {
                text = "子进程 $count"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = if (expanded) "收起" else "展开 >"
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textSecondary)
            })
            setOnClickListener { onClick() }
        }

    private fun TerminalSessionItem.runManagementSubtitle(): String =
        buildList {
            add(statusLabel)
            (observedPid ?: rootPid)?.takeIf { it > 0 }?.let { add("PID $it") }
            if (processCount > 0) add("进程 $processCount")
        }.joinToString(" · ")

    private fun runManagementProcessTitle(process: TaskManagerProcessItem?, fallbackPid: Int?): String {
        val pid = process?.pid?.takeIf { it > 0 } ?: fallbackPid
        return listOfNotNull(
            pid?.let { "PID $it" },
            process?.runManagementProcessName()?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { "主进程" }
    }

    private fun TaskManagerProcessItem.runManagementProcessSubtitle(): String =
        listOf(
            runManagementProcessOwnerLabel(),
            if (pid in runManagementPendingProcessStopIds) "结束中" else stateLabel
        )
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    private fun TaskManagerProcessItem.runManagementProcessName(): String {
        val text = runManagementIdentityText()
        return when {
            "supervisord" in text -> "容器守护进程"
            "/runtime/bin/proot" in text || "link2symlink" in text -> "PRoot 容器入口"
            "/workspace/.kf/system/bin/kf-runner" in text -> "Kite 命令启动器"
            "locale-check" in text -> "语言环境检查"
            "mkdir -p /run/" in text -> "运行目录准备"
            else -> title.ifBlank { command.ifBlank { commandLine.substringBefore(' ') } }
                .trimForRunManagement(36)
                .ifBlank { "进程" }
        }
    }

    private fun TaskManagerProcessItem.runManagementProcessPurpose(): String {
        val text = runManagementIdentityText()
        return when {
            "supervisord" in text -> "维护 Ubuntu 容器里的后台服务"
            "/runtime/bin/proot" in text || "link2symlink" in text -> "启动并隔离 Ubuntu 文件系统"
            "/workspace/.kf/system/bin/kf-runner" in text -> "执行卡片命令前的统一入口"
            "locale-check" in text -> "检查 Ubuntu 语言环境"
            "mkdir -p /run/" in text -> "准备 Ubuntu 运行目录"
            else -> "卡片或用户启动的普通进程"
        }
    }

    private fun TaskManagerProcessItem.runManagementProcessOwnerLabel(): String =
        when {
            runtimeUnitId?.startsWith("card:") == true || runtimeOwnerId?.startsWith("card:") == true -> "卡片"
            runtimeOwnerId?.startsWith("resource:") == true -> "资源"
            runtimeOwnerId?.startsWith("terminal:") == true || runtimeOwnerKindLabel == "终端" -> "卡片终端"
            isRunManagementSystemProcess() -> "系统"
            runtimeOwnerKindLabel.isNullOrBlank() ||
                runtimeOwnerKindLabel == "未归属运行根" ||
                sourceLabel == "未归属运行根" -> "未关联卡片"
            else -> runtimeOwnerKindLabel
        }

    private fun TaskManagerProcessItem.runManagementIdentityText(): String =
        listOf(title, sourceLabel, runtimeOwnerKindLabel.orEmpty(), runtimeUnitId.orEmpty(), command, commandLine)
            .joinToString(" ")
            .lowercase()

    private fun String.trimForRunManagement(maxLength: Int = 72): String {
        val compact = trim().replace(Regex("\\s+"), " ")
        return if (compact.length <= maxLength) compact else compact.take(maxLength - 1) + "…"
    }

    private fun showRunManagementProcessDialog(process: TaskManagerProcessItem?, fallbackPid: Int? = null) {
        val pid = process?.pid?.takeIf { it > 0 } ?: fallbackPid?.takeIf { it > 0 }
        if (pid == null) {
            Toast.makeText(this, "进程 PID 不可用", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(20).toFloat())
            addView(TextView(context).apply {
                text = "进程详情"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = runManagementProcessDialogText(process, pid)
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(12), 0, dp(4))
                setLineSpacing(dp(2).toFloat(), 1f)
                maxHeight = dp(280)
                movementMethod = ScrollingMovementMethod.getInstance()
                isVerticalScrollBarEnabled = true
            })
            addView(row {
                setPadding(0, dp(16), 0, 0)
                addView(runManagementDialogButton("取消") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(0, 0, dp(8), 0)
                })
                addView(runManagementDialogButton("结束进程", danger = true) {
                    showRunManagementProcessConfirmDialog(process, pid, dialog)
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(8), 0, 0, 0)
                })
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showRunManagementProcessConfirmDialog(
        process: TaskManagerProcessItem?,
        pid: Int,
        detailDialog: Dialog
    ) {
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(20).toFloat())
            addView(TextView(context).apply {
                text = "确认结束进程？"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "将直接强制结束 PID $pid。进程退出前不会再等待温和停止。"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(12), 0, dp(4))
            })
            addView(row {
                setPadding(0, dp(16), 0, 0)
                addView(runManagementDialogButton("取消") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(0, 0, dp(8), 0)
                })
                addView(runManagementDialogButton("结束进程", danger = true) {
                    dialog.dismiss()
                    detailDialog.dismiss()
                    stopRunManagementProcess(process, pid)
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(8), 0, 0, 0)
                })
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun runManagementProcessDialogText(process: TaskManagerProcessItem?, pid: Int): String =
        buildList {
            add("PID: $pid")
            process?.let { item ->
                add("名称: ${item.runManagementProcessName()}")
                item.parentPid.takeIf { it > 0 }?.let { add("PPID: $it") }
                add("状态: ${if (item.pid in runManagementPendingProcessStopIds) "结束中" else item.stateLabel}")
                add("归属: ${item.runManagementProcessOwnerLabel()}")
                add("用途: ${item.runManagementProcessPurpose()}")
                item.commandLine.takeIf { it.isNotBlank() }?.let { add("完整命令:\n${it.trim()}") }
            }
        }.joinToString("\n")

    private fun runManagementDialogButton(
        label: String,
        danger: Boolean = false,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            typeface = if (danger) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (danger) tokens.danger else tokens.textPrimary)
            background = roundedBox(
                if (danger) tokens.warningSoft else tokens.surface,
                if (danger) tokens.warningBorder else tokens.border,
                dp(13).toFloat()
            )
            setOnClickListener { onClick() }
        }

    private fun stopRunManagementCard(group: RunManagementGroup) {
        val recipe = group.recipe
        if (recipe == null) {
            Toast.makeText(this, "卡片信息不可用，无法停止", Toast.LENGTH_SHORT).show()
            return
        }
        submitRecipeAction(
            KiteRecipeActionRequest(
                recipe = recipe,
                intent = KiteRecipeActionIntent.Stop,
                source = KiteRecipeActionSource.RunManagement,
                instanceId = group.run.instanceId
            )
        )
        root.postDelayed({ if (!isFinishing && !isDestroyed) showKiteProcessOverview(forceRefresh = true) }, 260L)
    }

    private fun endRunManagementTerminal(terminal: TerminalSessionItem) {
        TerminalSessionStore.end(applicationContext, terminal.id)
        Toast.makeText(this, "正在结束终端", Toast.LENGTH_SHORT).show()
        requestRuntimePanelSummaryRefresh(force = true)
        root.postDelayed({ if (currentScreen == AppDestination.Processes) showKiteProcessOverview(forceRefresh = false) }, 260L)
    }

    private fun openRunManagementSurface(group: RunManagementGroup, item: CardRunWindowItem) {
        val recipe = group.recipe
        if (recipe == null || CardRunStore.get(item.instanceId) == null) {
            Toast.makeText(this, "运行窗口不可用", Toast.LENGTH_SHORT).show()
            return
        }
        selectCardRunWindowItem(recipe, item)
    }

    private fun openRunManagementWeb(group: RunManagementGroup, url: String) {
        openWeb(url, "run_management", group.recipe)
    }

    private fun closeRunManagementWebSurface(group: RunManagementGroup) {
        val recipe = group.recipe
        if (recipe == null) {
            Toast.makeText(this, "卡片信息不可用，无法关闭显示", Toast.LENGTH_SHORT).show()
            return
        }
        setRuntimeState(
            recipe,
            group.run.status,
            instanceId = group.run.instanceId,
            surface = CardRunSurface.Summary,
            clearNextActionUrl = true
        )
        Toast.makeText(this, "已关闭网页显示", Toast.LENGTH_SHORT).show()
        showKiteProcessOverview(forceRefresh = false)
    }

    private fun stopRunManagementProcess(process: TaskManagerProcessItem?, pid: Int) {
        runManagementPendingProcessStopIds.add(pid)
        markRunManagementProcessStopAsUserStop(process, pid)
        TaskManagerStore.endProcess(applicationContext, process, pid)
        Toast.makeText(this, "正在结束进程 PID $pid，稍后刷新", Toast.LENGTH_SHORT).show()
        if (currentScreen == AppDestination.Processes) showKiteProcessOverview(forceRefresh = false)
        scheduleRunManagementLazyRefresh(force = true)
    }

    private fun markRunManagementProcessStopAsUserStop(process: TaskManagerProcessItem?, pid: Int) {
        val run = runManagementRunForProcess(process, pid) ?: return
        val recipe = recipeForRunState(run) ?: return
        setRuntimeState(
            recipe,
            RecipeRunStatus.Stopped,
            instanceId = run.instanceId,
            surface = CardRunSurface.Summary,
            currentStepIndex = run.currentStepIndex,
            lastMeaningfulOutput = "已手动结束进程 PID $pid",
            clearRunBinding = true,
            clearTerminalSession = true,
            clearNextActionUrl = true
        )
    }

    private fun runManagementRunForProcess(process: TaskManagerProcessItem?, pid: Int): RecipeRuntimeState? {
        val unitCardId = process?.runtimeUnitId
            ?.takeIf { it.startsWith("card:") }
            ?.substringAfter(':')
            ?.takeIf { it.isNotBlank() }
        val ownerCardId = process?.runtimeOwnerId
            ?.takeIf { it.startsWith("card:") }
            ?.substringAfter(':')
            ?.takeIf { it.isNotBlank() }
        val ownerTerminalId = process?.runtimeOwnerId
            ?.takeIf { it.startsWith("terminal:") }
            ?.substringAfter(':')
            ?.takeIf { it.isNotBlank() }
        return listOfNotNull(unitCardId, ownerCardId)
            .firstNotNullOfOrNull(CardRunStore::get)
            ?: ownerTerminalId?.let { terminalId ->
                CardRunStore.runs.value.firstOrNull { it.terminalSessionId == terminalId }
            }
            ?: CardRunStore.runs.value.firstOrNull { run ->
                pid in run.boundProcessIds()
            }
    }

    private fun scheduleRunManagementLazyRefresh(force: Boolean = false) {
        requestRuntimePanelSummaryRefresh(force = force)
        listOf(260L, 900L, 1800L).forEach { delayMs ->
            root.postDelayed({
                if (currentScreen != AppDestination.Processes || isFinishing || isDestroyed) return@postDelayed
                requestRuntimePanelSummaryRefresh(force = false)
                root.postDelayed({
                    if (currentScreen == AppDestination.Processes && !isFinishing && !isDestroyed) {
                        showKiteProcessOverview(forceRefresh = false)
                    }
                }, 180L)
            }, delayMs)
        }
    }

    private fun pruneRunManagementPendingProcessStops() {
        if (runManagementPendingProcessStopIds.isEmpty()) return
        val livePids = TaskManagerStore.snapshot.value.processes.mapTo(mutableSetOf()) { it.pid }
        runManagementPendingProcessStopIds.removeAll(runManagementPendingProcessStopIds.filter { it !in livePids }.toSet())
    }

    private fun maybeAutoShowUbuntuRuntimePanel(state: UbuntuRuntimeUiState) {
        if (shouldShowRuntimeGate(state)) return
        if (shouldSuppressTransientRuntimeChrome(state)) return
        val startedAt = latestRootfsProgress.startedAt
        if (!state.autoOpenPanel || startedAt <= 0L || autoOpenedRootfsRunAt == startedAt) return
        autoOpenedRootfsRunAt = startedAt
        showUbuntuRuntimePanel(auto = true)
    }

    private fun runtimePanelAnchorPoint(
        anchor: View?,
        panelLeft: Int,
        panelWidth: Int,
        fallbackBottomY: Int
    ): Pair<Int, Int> {
        val fallback = (panelLeft + panelWidth / 2) to fallbackBottomY
        if (anchor == null || anchor.width <= 0 || !anchor.isAttachedToWindow) {
            return fallback
        }
        val location = IntArray(2)
        return runCatching {
            anchor.getLocationOnScreen(location)
            val visibleFrame = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
            (location[0] + anchor.width / 2) to (location[1] + anchor.height - visibleFrame.top)
        }.getOrDefault(fallback)
    }

    private fun showUbuntuRuntimePanel(auto: Boolean, anchor: View? = null) {
        if (auto && shouldShowRuntimeGate(ubuntuRuntimeState)) return
        if (auto && shouldSuppressTransientRuntimeChrome(ubuntuRuntimeState)) return
        val existing = ubuntuRuntimeDialog
        if (existing?.isShowing == true) {
            requestRuntimePanelSummaryRefresh(force = !auto)
            renderUbuntuRuntimePanelState()
            return
        }
        requestRuntimePanelSummaryRefresh(force = !auto)

        val screenWidth = resources.displayMetrics.widthPixels
        val panelWidth = (screenWidth - dp(36)).coerceAtMost(dp(560))
        val panelLeft = ((screenWidth - panelWidth) / 2).coerceAtLeast(dp(8))
        val pointerSize = dp(22)
        val pointerMinLeft = dp(24)
        val pointerMaxLeft = (panelWidth - dp(24) - pointerSize).coerceAtLeast(pointerMinLeft)
        val (anchorCenterX, anchorBottomY) = runtimePanelAnchorPoint(
            anchor,
            panelLeft,
            panelWidth,
            fallbackBottomY = if (auto) dp(56) else dp(58)
        )
        val pointerLeft = (anchorCenterX - panelLeft - pointerSize / 2).coerceIn(pointerMinLeft, pointerMaxLeft)
        val panelTop = (anchorBottomY + dp(3)).coerceAtLeast(dp(6))

        val dialog = Dialog(this)
        ubuntuRuntimeDialog = dialog

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), 0)
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            elevation = dp(8).toFloat()
        }

        content.addView(row {
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context).apply {
                background = roundedBox(tokens.success, Color.TRANSPARENT, dp(5).toFloat(), 0)
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    setMargins(0, 0, dp(10), 0)
                }
            })
            addView(TextView(context).apply {
                text = "运行状态"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        content.addView(TextView(this).apply {
            runtimePanelTitleView = this
            textSize = 13.5f
            typeface = Typeface.DEFAULT
            setTextColor(tokens.textPrimary)
            setPadding(0, dp(14), 0, 0)
        })
        content.addView(TextView(this).apply {
            runtimePanelDetailView = this
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(5), 0, 0)
        })
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(16))
            addView(runtimePanelMetric("▣", "卡片", tokens.primaryStrong) { runtimePanelCardCountView = it })
            addView(runtimePanelMetric(">_", "终端", Color.rgb(0, 150, 136)) { runtimePanelTerminalCountView = it })
            addView(runtimePanelMetric("⌁", "进程", tokens.warning) { runtimePanelProcessCountView = it })
        })
        content.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            runtimePanelProgressBar = this
            max = 100
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                setMargins(0, 0, 0, 0)
            }
        })
        content.addView(TextView(this).apply {
            runtimePanelProgressTextView = this
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(7), 0, 0)
        })

        content.addView(divider().apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(-dp(18), dp(18), -dp(18), 0)
            }
        })
        val actionRow = row {
            runtimePanelActionRow = this
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { handleRuntimePanelAction() }
            addView(TextView(context).apply {
                runtimePanelActionButton = this
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            })
            addView(TextView(context).apply {
                runtimePanelActionChevronView = this
                text = "›"
                textSize = 30f
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                layoutParams = LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT)
            })
        }
        content.addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        val popover = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
            pivotX = pointerLeft + pointerSize / 2f
            pivotY = dp(13).toFloat()
            val pointer = View(context).apply {
                rotation = 45f
                background = roundedBox(tokens.cardBackground, Color.TRANSPARENT, dp(2).toFloat(), 0)
            }
            val pointerParams = FrameLayout.LayoutParams(pointerSize, pointerSize, Gravity.TOP or Gravity.START).apply {
                leftMargin = pointerLeft
                topMargin = dp(2)
            }
            addView(pointer, pointerParams)
            val contentParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(11)
            }
            addView(content, contentParams)
        }

        dialog.setContentView(popover)
        dialog.setOnDismissListener {
            if (ubuntuRuntimeDialog == dialog) {
                ubuntuRuntimeDialog = null
                runtimePanelTitleView = null
                runtimePanelDetailView = null
                runtimePanelProgressBar = null
                runtimePanelProgressTextView = null
                runtimePanelActionButton = null
                runtimePanelActionRow = null
                runtimePanelActionChevronView = null
                runtimePanelCardCountView = null
                runtimePanelTerminalCountView = null
                runtimePanelProcessCountView = null
            }
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            setGravity(Gravity.TOP or Gravity.START)
            attributes = attributes.apply {
                x = panelLeft
                y = panelTop
                dimAmount = 0f
            }
            setLayout(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        popover.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
            .start()
        renderUbuntuRuntimePanelState()
    }

    private fun renderUbuntuRuntimePanelState() {
        val dialog = ubuntuRuntimeDialog
        if (dialog?.isShowing != true) return
        val state = ubuntuRuntimeState
        val summary = runtimePanelSummary()
        runtimePanelTitleView?.apply {
            text = if (state.visible) state.title else "Ubuntu 环境可用"
            setTextColor(if (state.isProblem) tokens.danger else tokens.textPrimary)
        }
        runtimePanelDetailView?.apply {
            val detailText = if (state.visible) state.detail else ""
            text = detailText
            visibility = if (detailText.isBlank()) View.GONE else View.VISIBLE
        }
        renderRuntimePanelCounts(summary)
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
        val primaryAction = runtimePanelUsesPrimaryAction(state)
        runtimePanelActionRow?.apply {
            background = if (primaryAction) {
                roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(14).toFloat())
            } else {
                null
            }
        }
        runtimePanelActionButton?.apply {
            text = when {
                state.firstRunPermissionOnboarding -> state.permissionActionLabel.ifBlank { "开始授权" }
                state.requiresPermission -> state.permissionActionLabel.ifBlank { "打开授权 / 继续" }
                state.canRetry || (state.visible && !state.blocksUbuntuActions) -> "重新检查 / 继续部署"
                else -> "查看进程"
            }
            setTextColor(if (primaryAction) tokens.buttonText else tokens.textPrimary)
        }
        runtimePanelActionChevronView?.apply {
            visibility = if (primaryAction) View.GONE else View.VISIBLE
            setTextColor(tokens.textSecondary)
        }
    }

    private fun handleRuntimePanelAction() {
        val state = ubuntuRuntimeState
        if (state.firstRunPermissionOnboarding) {
            continueFirstRunPermissionOnboarding()
        } else if (state.requiresPermission) {
            requestFirstRunRuntimePermissions(startBootstrapAfterGrant = true)
        } else if (state.canRetry || (state.visible && !state.blocksUbuntuActions)) {
            ubuntuRuntimeDialog?.dismiss()
            kfRuntimeBootstrapRequested = false
            ensureKfRuntimeBootstrap()
        } else {
            ubuntuRuntimeDialog?.dismiss()
            showKiteProcessOverview()
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

    private fun isUbuntuActionBlocked(recipe: KiteRecipe): Boolean =
        ubuntuRuntimeState.blocksUbuntuActions && recipe.hasUbuntuStep()

    private fun recipeUsesResourceInlineStatus(recipe: KiteRecipe): Boolean =
        recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE ||
            recipe.runtimeSource == RESOURCE_OPEN_RUNTIME_SOURCE ||
            recipe.runtimeSource == RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE ||
            resourceIdForRecipe(recipe) != null

    private fun resourceSurfaceHasInlineRuntimeStatus(): Boolean {
        if (resourceInstallWizardSurfaceActive()) return true
        return when (currentScreen) {
            AppDestination.Resources,
            AppDestination.ResourceSearch,
            AppDestination.ResourceDetail,
            AppDestination.ResourceMore,
            AppDestination.ResourceManage -> true
            AppDestination.CardRun -> focusedRunRecipe()?.let { recipe ->
                recipeUsesResourceInlineStatus(recipe)
            } == true
            else -> false
        }
    }

    private fun shouldSuppressTransientRuntimeChrome(state: UbuntuRuntimeUiState = ubuntuRuntimeState): Boolean =
        resourceSurfaceHasInlineRuntimeStatus() &&
            state.blocksUbuntuActions &&
            !state.requiresPermission &&
            !state.firstRunPermissionOnboarding &&
            !state.isProblem &&
            !state.canRetry

    private fun requestResourceRuntimeInlineRefresh(
        recipe: KiteRecipe,
        @Suppress("UNUSED_PARAMETER") reason: String
    ) {
        invalidateResourceRuntimeStateCache()
        if (resourceInstallWizardShouldHost(recipe)) {
            resourceInstallWizardSurface?.reconcile()
        }
    }

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
        submitRecipeAction(
            KiteRecipeActionRequest(
                recipe = recipe,
                intent = KiteRecipeActionIntent.Primary,
                source = KiteRecipeActionSource.ConsoleCard,
                openTaskOnStart = shouldOpenCardRunTaskFromHome(recipe)
            )
        )
    }

    private fun submitRecipeAction(
        request: KiteRecipeActionRequest,
        afterDispatch: () -> Unit = {}
    ) {
        val recipe = request.recipe
        val state = request.instanceId
            ?.let { instanceId -> CardRunStore.get(instanceId) }
            ?: runtimeStateFor(recipe)
        request.instanceId?.takeIf { it.isNotBlank() }?.let { instanceId ->
            activeRunInstanceIds[recipe.id] = instanceId
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            runtimeStates[recipe.id] = state
        }
        diagnostics.logRecipeAction(
            recipe,
            "action_submit",
            mapOf(
                "intent" to request.intent.name,
                "source" to request.source.logValue,
                "status" to state.status.name
            )
        )
        when (val plan = recipeActionCoordinator.plan(request, state, isUbuntuActionBlocked(recipe))) {
            is KiteRecipeActionPlan.Ignored -> Unit
            KiteRecipeActionPlan.RuntimeRequired -> {
                ensureKfRuntimeBootstrap()
                if (shouldSuppressTransientRuntimeChrome()) {
                    requestResourceRuntimeInlineRefresh(recipe, "ubuntu_runtime_blocked")
                } else {
                    showUbuntuRuntimePanel(auto = true)
                    Toast.makeText(this, ubuntuRuntimeState.title, Toast.LENGTH_SHORT).show()
                }
            }
            KiteRecipeActionPlan.OpenRun -> openRecipeRunInstance(recipe)
            KiteRecipeActionPlan.LaunchTask -> {
                diagnostics.logRecipeAction(recipe, "card_run_task_requested", mapOf("source" to CardRunIntents.SOURCE_CARD))
                startActivity(
                    CardRunIntents.launchIntent(
                        context = this,
                        recipeId = recipe.id,
                        launchSource = CardRunIntents.SOURCE_CARD,
                        autoStart = true
                    )
                )
            }
            KiteRecipeActionPlan.Stop -> stopRecipe(recipe, state)
            is KiteRecipeActionPlan.Execute -> executeRecipeActionRoute(request, state, plan.route)
        }
        afterDispatch()
    }

    private fun executeRecipeActionRoute(
        request: KiteRecipeActionRequest,
        state: RecipeRuntimeState,
        route: KiteActionRoute
    ) {
        val recipe = request.recipe
        when (route) {
            is KiteActionRoute.StopRecipe -> stopRecipe(recipe, state)
            is KiteActionRoute.RunRecipe -> startRecipe(
                route.recipe,
                state,
                openConsoleOnStart = request.source != KiteRecipeActionSource.Editor && route.recipe.launch.openInstance,
                renderOnStart = true
            )
            is KiteActionRoute.OpenWeb -> {
                setRuntimeState(recipe, RecipeRunStatus.Opened, nextActionUrl = route.url)
                openWeb(route.url, request.source.logValue, recipe)
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
            KiteRecipe.ANDROID_ACTION_INSTALL_APK -> {
                val response = handleInstallApkRequest(KiteInstallApkRequest(installApkPathFromStep(route.step), "recipe_card"))
                if (response.accepted) {
                    setRuntimeState(recipe, RecipeRunStatus.Opened, lastMeaningfulOutput = "已打开安装器：${response.resolvedPath}")
                } else {
                    setRuntimeState(recipe, RecipeRunStatus.Failed, lastError = response.error.ifBlank { "install_apk_failed" })
                    Toast.makeText(this, response.error.ifBlank { "install_apk_failed" }, Toast.LENGTH_SHORT).show()
                }
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
        if (recipeUsesProcessRunOrchestrator(recipe)) {
            startRecipeWithOrchestrator(
                recipe = recipe,
                previousState = previousState,
                preferredInstanceId = preferredInstanceId,
                openConsoleOnStart = openConsoleOnStart,
                renderOnStart = renderOnStart,
                keepCurrentFocus = keepCurrentFocus
            )
            return
        }
        legacyStartRecipe(
            recipe = recipe,
            previousState = previousState,
            preferredInstanceId = preferredInstanceId,
            openConsoleOnStart = openConsoleOnStart,
            renderOnStart = renderOnStart,
            keepCurrentFocus = keepCurrentFocus
        )
    }

    private fun recipeUsesProcessRunOrchestrator(recipe: KiteRecipe): Boolean =
        recipe.runtimeSource != KiteResourceInstallRecipes.RUNTIME_SOURCE

    private fun startRecipeWithOrchestrator(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        preferredInstanceId: String?,
        openConsoleOnStart: Boolean,
        renderOnStart: Boolean,
        keepCurrentFocus: Boolean
    ) {
        val instanceId = preferredInstanceId ?: activeRunInstanceIds[recipe.id] ?: recipe.id
        activeRunInstanceIds[recipe.id] = instanceId
        if (!keepCurrentFocus) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
        }
        val result = runOrchestrator.start(
            RunStartRequest(
                recipe = recipe,
                instanceId = instanceId,
                parentInstanceId = previousState.parentInstanceId,
                ownerKind = previousState.ownerKind,
                stepId = previousState.stepId
            )
        )
        val state = CardRunStore.get(instanceId) ?: previousState
        runtimeStates[recipe.id] = state
        diagnostics.logRecipeAction(
            recipe,
            "run_orchestrator_start",
            mapOf(
                "instanceId" to instanceId,
                "result" to when (result) {
                    is RunCommandResult.Accepted -> "accepted"
                    is RunCommandResult.Ignored -> "ignored:${result.reason}"
                },
                "steps" to recipe.steps.joinToString(" -> ") { it.type }
            )
        )
        val firstStep = recipe.steps.firstOrNull()
        val deferInitialSurfaceUntilTerminalReady =
            firstStep?.type == KiteRecipe.STEP_TERMINAL && (this is CardRunActivity || !openConsoleOnStart)
        if (!renderOnStart) {
            if (!keepCurrentFocus) {
                focusedRunRecipeId = recipe.id
                focusedRunInstanceId = instanceId
            }
        } else if (openConsoleOnStart && this !is CardRunActivity) {
            showConsole()
        } else {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            if (!deferInitialSurfaceUntilTerminalReady) {
                showCardRunSurface(recipe)
            } else {
                showCardRunLoadingSurface(recipe, "正在准备终端")
            }
        }
    }

    private fun legacyStartRecipe(
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
            recipe = recipe,
            instanceId = preferredInstanceId ?: activeRunInstanceIds[recipe.id] ?: recipe.id,
            parentInstanceId = previousState.parentInstanceId,
            ownerKind = previousState.ownerKind,
            stepId = previousState.stepId
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
                isProblem = false,
                showProgress = true,
                progressText = "正在检查系统镜像"
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
                            isProblem = false,
                            showProgress = true,
                            progressText = "正在准备工作区"
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
            KiteRecipe.STEP_X11 -> runUbuntuStepWhenReady(recipe, stepIndex, runId, pid, CardRunSurface.X11) {
                executeX11RecipeStep(recipe, step, stepIndex, runId, pid)
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
                        mapOf(
                            "stepIndex" to stepIndex.toString(),
                            "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(url)
                        )
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
        KiteRecipe.STEP_X11 -> CardRunSurface.X11
        KiteRecipe.STEP_SHELL,
        KiteRecipe.STEP_ANDROID_ACTION -> CardRunSurface.Report
        else -> CardRunSurface.Summary
    }

    private fun shouldRenderInCardRun(recipe: KiteRecipe): Boolean =
        this is CardRunActivity && focusedRunRecipeId == recipe.id

    private fun shouldStayOnRunSurface(): Boolean =
        currentScreen == AppDestination.CardRun ||
            this is CardRunActivity

    private fun showRunSurfaceOrConsole(recipe: KiteRecipe) {
        if (renderResourceInstallWizardFor(recipe)) {
            Unit
        } else if (resourceRunSurfaceSuppressed(recipe)) {
            invalidateResourceRuntimeStateCache()
        } else if (shouldStayOnRunSurface()) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
            showCardRunSurface(recipe)
        } else if (currentScreen == AppDestination.CreateConfig || currentScreen == AppDestination.RecipeMore) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
        } else {
            showConsole()
        }
    }

    private fun showConsoleUnlessEditingRecipe(recipe: KiteRecipe) {
        if (currentScreen == AppDestination.CreateConfig || currentScreen == AppDestination.RecipeMore) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
        } else {
            showConsole()
        }
    }

    private fun toastIfNotResourceRecipe(recipe: KiteRecipe, message: String) {
        if (!recipeUsesResourceInlineStatus(recipe)) {
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
                    currentScreen == AppDestination.CardRun ||
                    recipe.launch.openInstance
                mayAutoOpenSurface && (
                    step.type == KiteRecipe.STEP_OPEN_WEB ||
                        step.type == KiteRecipe.STEP_TERMINAL ||
                        step.type == KiteRecipe.STEP_X11 ||
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
        if (state.hasActiveX11Handoff(stepIndex)) {
            val updated = CardRunStore.update(
                recipe = recipe,
                status = RecipeRunStatus.Running,
                instanceId = state.instanceId,
                surface = CardRunSurface.X11,
                currentStepIndex = stepIndex,
                runId = progress.runId,
                pid = progress.pid,
                rootPid = progress.rootPid,
                processGroupId = progress.processGroupId,
                systemSessionId = progress.systemSessionId,
                lastMeaningfulOutput = progress.lastMeaningfulOutput.ifBlank { "X11 桌面运行中" },
                shellReportText = reportText,
                x11Display = state.x11Display,
                x11SocketPath = state.x11SocketPath
            )
            runtimeStates[recipe.id] = updated
            resourceInstallWizardSurface?.tick()
            return
        }
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
        updateVisibleCardRunReport(updated)
        resourceInstallWizardSurface?.tick()
    }

    private fun RecipeRuntimeState.hasActiveX11Handoff(stepIndex: Int): Boolean =
        status == RecipeRunStatus.Running &&
            currentStepIndex == stepIndex &&
            surface == CardRunSurface.X11 &&
            !x11Display.isNullOrBlank()

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
            KiteRecipe.ANDROID_ACTION_INSTALL_APK -> {
                val response = handleInstallApkRequest(KiteInstallApkRequest(installApkPathFromStep(step), "recipe_sequence"))
                if (response.accepted) {
                    executeRecipeStep(recipe, stepIndex + 1, runId, pid, "已打开安装器：${response.resolvedPath}")
                } else {
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.Failed,
                        surface = CardRunSurface.Report,
                        currentStepIndex = stepIndex,
                        runId = runId,
                        pid = pid,
                        lastError = response.error.ifBlank { "install_apk_failed" }
                    )
                    markResourceInstallFailed(recipe, runId, response.error.ifBlank { "install_apk_failed" })
                    toastIfNotResourceRecipe(recipe, response.error.ifBlank { "install_apk_failed" })
                    showRunSurfaceOrConsole(recipe)
                }
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

        if (currentState != null && currentState.currentStepIndex > stepIndex) {
            diagnostics.logRecipeAction(
                recipe,
                "sequence_shell_result_ignored_after_manual_continue",
                mapOf(
                    "stepIndex" to stepIndex.toString(),
                    "currentStepIndex" to currentState.currentStepIndex.toString(),
                    "requestId" to requestId,
                    "runId" to runId.orEmpty()
                )
            )
            return
        }
        val currentRunId = currentState?.runId
        if (!currentRunId.isNullOrBlank() && !runId.isNullOrBlank() && currentRunId != runId) {
            diagnostics.logRecipeAction(
                recipe,
                "sequence_shell_result_ignored_for_stale_run",
                mapOf(
                    "stepIndex" to stepIndex.toString(),
                    "currentRunId" to currentRunId,
                    "incomingRunId" to runId,
                    "requestId" to requestId
                )
            )
            return
        }

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
        if (currentState?.hasActiveX11Handoff(stepIndex) == true &&
            (result.accepted || result.status == KiteRunReport.STATUS_RUNNING || report?.status == KiteRunReport.STATUS_RUNNING)
        ) {
            val updated = CardRunStore.update(
                recipe = recipe,
                status = RecipeRunStatus.Running,
                instanceId = currentState.instanceId,
                surface = CardRunSurface.X11,
                currentStepIndex = stepIndex,
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                lastMeaningfulOutput = lastOutput ?: "X11 桌面运行中",
                shellReportText = shellReport,
                x11Display = currentState.x11Display,
                x11SocketPath = currentState.x11SocketPath
            )
            runtimeStates[recipe.id] = updated
            diagnostics.logRecipeAction(
                recipe,
                "sequence_shell_x11_handoff_preserved",
                mapOf(
                    "requestId" to requestId,
                    "stepIndex" to stepIndex.toString(),
                    "instanceId" to currentState.instanceId,
                    "display" to currentState.x11Display.orEmpty()
                )
            )
            resourceInstallWizardSurface?.tick()
            return
        }
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

    private fun executeX11RecipeStep(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int,
        previousRunId: String?,
        previousPid: String?
    ) {
        val instanceId = ensureRunInstanceId(recipe)
        val command = step.cmd.orEmpty().ifBlank { step.text.orEmpty() }
        if (command.isBlank()) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.Failed,
                instanceId = instanceId,
                surface = CardRunSurface.Report,
                currentStepIndex = stepIndex,
                runId = previousRunId,
                pid = previousPid,
                lastError = "x11_missing_command"
            )
            markResourceInstallFailed(recipe, previousRunId, "x11_missing_command")
            toastIfNotResourceRecipe(recipe, "X11 步骤缺少命令")
            showRunSurfaceOrConsole(recipe)
            return
        }
        val binding = CardRunStore.get(instanceId)?.x11Display?.let { KiteX11SurfacePlan.binding(it) }
            ?: KiteX11SurfacePlan.allocate(
                instanceId = instanceId,
                occupiedDisplays = CardRunStore.snapshot()
                    .filterNot { it.instanceId == instanceId }
                    .mapNotNull { it.x11Display }
                    .toSet()
            )
        setRuntimeState(
            recipe,
            RecipeRunStatus.Running,
            instanceId = instanceId,
            surface = CardRunSurface.Report,
            currentStepIndex = stepIndex,
            runId = previousRunId,
            pid = previousPid,
            lastMeaningfulOutput = "${x11TaskTitle(recipe)} native X11 准备中",
            x11Display = binding.display,
            x11SocketPath = binding.socketPath,
            clearNextActionUrl = true
        )
        if (shouldOpenStepSurface(recipe, step)) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            showCardRunLoadingSurface(recipe, "正在准备 X11")
        }
        thread(name = "KiteX11Start-${recipe.id.take(32)}", isDaemon = true) {
            val x11Start = KiteX11SurfaceServer.ensureStarted(applicationContext, binding)
            runOnUiThread {
                if (x11Start.isFailure) {
                    val message = x11Start.exceptionOrNull()?.message ?: "native X11 启动失败"
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.Failed,
                        instanceId = instanceId,
                        surface = CardRunSurface.Report,
                        currentStepIndex = stepIndex,
                        runId = previousRunId,
                        pid = previousPid,
                        lastError = message,
                        x11Display = binding.display,
                        x11SocketPath = binding.socketPath
                    )
                    markResourceInstallFailed(recipe, previousRunId, message)
                    toastIfNotResourceRecipe(recipe, message.take(120))
                    showRunSurfaceOrConsole(recipe)
                    return@runOnUiThread
                }
                launchX11RecipeStep(
                    recipe = recipe,
                    step = step,
                    stepIndex = stepIndex,
                    instanceId = instanceId,
                    command = command,
                    binding = binding,
                    previousRunId = previousRunId,
                    previousPid = previousPid
                )
            }
        }
    }

    private fun launchX11RecipeStep(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int,
        instanceId: String,
        command: String,
        binding: com.kite.app.run.KiteX11SurfaceBinding,
        previousRunId: String?,
        previousPid: String?
    ) {
        val openSurface = shouldOpenStepSurface(recipe, step)
        val waitForUserSignal = openSurface && shouldRenderInCardRun(recipe)
        val title = x11TaskTitle(recipe)
        setRuntimeState(
            recipe,
            RecipeRunStatus.Running,
            instanceId = instanceId,
            surface = CardRunSurface.X11,
            currentStepIndex = stepIndex,
            runId = previousRunId,
            pid = previousPid,
            lastMeaningfulOutput = "$title native X11 启动中",
            x11Display = binding.display,
            x11SocketPath = binding.socketPath,
            clearNextActionUrl = true
        )
        if (openSurface) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            showCardRunSurface(recipe)
        }
        val x11Step = step.copy(
            type = KiteRecipe.STEP_SHELL,
            cmd = command,
            runMode = step.runMode ?: KiteRecipe.RUN_MODE_DETACHED
        )
        val stepRecipe = recipe.copy(
            execution = KiteExecution.steps(listOf(x11Step)),
            actions = linkedMapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = listOf(x11Step),
                    expected = step.expected ?: recipe.expected
                )
            ),
            expected = step.expected ?: recipe.expected
        )
        bridgeClient.runRecipe(
            stepRecipe,
            extraEnv = KiteBrowserProxyInstaller.environment(
                context = applicationContext,
                recipeId = recipe.id,
                instanceId = instanceId,
                source = "x11_step"
            ) + binding.environment(),
            onProgress = { progress ->
                runOnUiThread {
                    handleX11Progress(recipe, stepIndex, binding, progress)
                }
            }
        ) { result ->
            runOnUiThread {
                handleX11Result(recipe, stepIndex, instanceId, binding, result, waitForUserSignal)
            }
        }
    }

    private fun handleX11Progress(
        recipe: KiteRecipe,
        stepIndex: Int,
        binding: com.kite.app.run.KiteX11SurfaceBinding,
        progress: BridgeProgress
    ) {
        if (progress.recipeId != recipe.id) return
        val state = runtimeStates[recipe.id] ?: CardRunStore.currentForRecipe(recipe.id) ?: return
        if (state.currentStepIndex != stepIndex || state.surface != CardRunSurface.X11) return
        val updated = CardRunStore.update(
            recipe = recipe,
            status = RecipeRunStatus.Running,
            instanceId = state.instanceId,
            surface = CardRunSurface.X11,
            currentStepIndex = stepIndex,
            runId = progress.runId,
            pid = progress.pid,
            rootPid = progress.rootPid,
            processGroupId = progress.processGroupId,
            systemSessionId = progress.systemSessionId,
            lastMeaningfulOutput = progress.lastMeaningfulOutput.ifBlank { "${x11TaskTitle(recipe)} native X11 运行中" },
            x11Display = binding.display,
            x11SocketPath = binding.socketPath
        )
        runtimeStates[recipe.id] = updated
    }

    private fun handleX11Result(
        recipe: KiteRecipe,
        stepIndex: Int,
        instanceId: String,
        binding: com.kite.app.run.KiteX11SurfaceBinding,
        result: BridgeResult,
        waitForUserSignal: Boolean
    ) {
        val report = result.runReport
        val runId = report?.runId ?: result.requestId
        val lastOutput = report?.lastMeaningfulOutput() ?: result.message.take(500)
        val status = when {
            result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE -> RecipeRunStatus.BridgeUnavailable
            result.accepted -> RecipeRunStatus.Running
            else -> RecipeRunStatus.Failed
        }
        setRuntimeState(
            recipe,
            status,
            instanceId = instanceId,
            surface = if (status == RecipeRunStatus.Failed || status == RecipeRunStatus.BridgeUnavailable) CardRunSurface.Report else CardRunSurface.X11,
            currentStepIndex = stepIndex,
            runId = runId,
            pid = report?.pid,
            rootPid = report?.rootPid,
            processGroupId = report?.processGroupId,
            systemSessionId = report?.systemSessionId,
            lastMeaningfulOutput = if (status == RecipeRunStatus.Running) {
                val pid = report?.pid ?: report?.rootPid ?: report?.processGroupId ?: report?.systemSessionId
                "${x11TaskTitle(recipe)} native X11 运行中${pid?.let { " pid=$it" }.orEmpty()}"
            } else null,
            lastError = if (status == RecipeRunStatus.Running) null else lastOutput.ifBlank { result.status },
            x11Display = binding.display.takeIf { status == RecipeRunStatus.Running },
            x11SocketPath = binding.socketPath.takeIf { status == RecipeRunStatus.Running }
        )
        if (status != RecipeRunStatus.Running) {
            markResourceInstallFailed(recipe, runId, lastOutput.ifBlank { result.status })
            toastIfNotResourceRecipe(recipe, lastOutput.take(120).ifBlank { "X11 启动失败" })
            showRunSurfaceOrConsole(recipe)
            return
        }
        if (stepIndex < recipe.steps.lastIndex && !waitForUserSignal) {
            executeRecipeStep(recipe, stepIndex + 1, runId, report?.pid, lastOutput)
        } else {
            showRunSurfaceOrConsole(recipe)
        }
    }

    private fun executeTerminalRecipeStep(
        recipe: KiteRecipe,
        step: KiteRecipeStep,
        stepIndex: Int
    ) {
        val text = step.text.orEmpty().ifBlank { step.cmd.orEmpty() }
        val appContext = applicationContext
        val instanceId = ensureRunInstanceId(recipe)
        if (!resourceRunSurfaceSuppressed(recipe)) {
            focusedRunInstanceId = instanceId
        }
        setRuntimeState(
            recipe,
            RecipeRunStatus.Running,
            instanceId = instanceId,
            surface = CardRunSurface.Terminal,
            currentStepIndex = stepIndex,
            lastMeaningfulOutput = "正在创建终端",
            clearNextActionUrl = true
        )
        val openSurface = shouldOpenStepSurface(recipe, step)
        if (openSurface) {
            showCardRunSurface(recipe)
        }
        diagnostics.logRecipeAction(
            recipe,
            "terminal_step_session_prepare",
            mapOf("instanceId" to instanceId, "stepIndex" to stepIndex.toString())
        )

        thread(name = "KiteTerminalStep-${recipe.id.take(32)}", isDaemon = true) {
            val recordResult = runCatching {
                val space = KFWorkspaceManager.ensureDefaultSpace(appContext)
                val record = KFWorkspaceManager.createShellSession(
                    context = appContext,
                    spaceId = space.id,
                    title = cardTerminalTitle(recipe, stepIndex),
                    sourceLabel = recipe.name
                )
                TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
                record
            }
            runOnUiThread {
                val stillPreparing = CardRunStore.get(instanceId)?.let { current ->
                    current.status == RecipeRunStatus.Running &&
                        current.surface == CardRunSurface.Terminal &&
                        current.currentStepIndex == stepIndex &&
                        current.terminalSessionId.isNullOrBlank()
                } == true
                if (!stillPreparing) {
                    diagnostics.logRecipeAction(
                        recipe,
                        "terminal_step_session_discarded",
                        mapOf("instanceId" to instanceId, "stepIndex" to stepIndex.toString())
                    )
                    return@runOnUiThread
                }

                val record = recordResult.getOrElse { error ->
                    val message = "创建终端失败：${error.message ?: error.javaClass.simpleName}"
                    setRuntimeState(
                        recipe,
                        RecipeRunStatus.Failed,
                        instanceId = instanceId,
                        surface = CardRunSurface.Report,
                        currentStepIndex = stepIndex,
                        lastError = message
                    )
                    markResourceInstallFailed(recipe, null, message)
                    toastIfNotResourceRecipe(recipe, message.take(120))
                    if (!openSurface || (currentScreen == AppDestination.CardRun && focusedRunInstanceId == instanceId)) {
                        showRunSurfaceOrConsole(recipe)
                    }
                    return@runOnUiThread
                }

                TerminalSessionStore.refresh(appContext, force = true)
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
                    ).withTerminalOwner(record.id, instanceId)
                )
                if (openSurface) {
                    if (currentScreen == AppDestination.CardRun && focusedRunInstanceId == instanceId) {
                        showCardRunSurface(recipe)
                    }
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
            }
        }
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

    private fun Map<String, String>.withTerminalOwner(
        sessionId: String,
        cardInstanceId: String
    ): Map<String, String> {
        val terminalId = sessionId.trim().takeIf { it.isNotBlank() } ?: return this
        val unitId = cardInstanceId.trim().takeIf { it.isNotBlank() } ?: terminalId
        return this + mapOf(
            "KF_RUNTIME_ID" to "terminal:$terminalId",
            "KF_UNIT_ID" to "card:$unitId"
        )
    }

    private fun stopRecipe(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        navigateToConsole: Boolean = true
    ) {
        stopRecipeByCardInstanceId(recipe, previousState.cardInstanceId, previousState, navigateToConsole)
    }

    private fun stopRecipeByCardInstanceId(
        recipe: KiteRecipe,
        cardInstanceId: String,
        fallbackState: RecipeRuntimeState? = null,
        navigateToConsole: Boolean = true
    ) {
        val previousState = CardRunStore.get(cardInstanceId)
            ?: fallbackState?.takeIf { it.recipeId == recipe.id }
            ?: CardRunStore.currentForRecipe(recipe.id)
            ?: return
        if (recipeUsesProcessRunOrchestrator(recipe)) {
            stopRecipeWithOrchestrator(recipe, previousState, navigateToConsole)
            return
        }
        legacyStopRecipeByCardInstanceId(recipe, previousState, navigateToConsole)
    }

    private fun stopRecipeWithOrchestrator(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        navigateToConsole: Boolean
    ) {
        activeRunInstanceIds[recipe.id] = previousState.instanceId
        diagnostics.logBridgeEvent(
            "stop_orchestrator_request",
            recipe,
            mapOf(
                "cardInstanceId" to previousState.cardInstanceId,
                "runId" to previousState.runId.orEmpty(),
                "terminalSessionId" to previousState.terminalSessionId.orEmpty()
            )
        )
        when (val result = runOrchestrator.stop(previousState.instanceId)) {
            is RunCommandResult.Accepted -> {
                if (
                    previousState.status == RecipeRunStatus.Opened &&
                    !previousState.hasRunBinding()
                ) {
                    webView.stopLoading()
                }
                closeCardRunInstanceForStop(recipe, previousState, "stop_orchestrator_accepted")
                if (navigateToConsole) showConsole()
            }
            is RunCommandResult.Ignored -> diagnostics.logBridgeEvent(
                "stop_orchestrator_ignored",
                recipe,
                mapOf(
                    "cardInstanceId" to previousState.cardInstanceId,
                    "reason" to result.reason
                )
            )
        }
    }

    private fun legacyStopRecipeByCardInstanceId(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        navigateToConsole: Boolean
    ) {
        activeRunInstanceIds[recipe.id] = previousState.instanceId
        runtimeStates[recipe.id] = previousState
        diagnostics.logBridgeEvent(
            "stop_card_instance_resolved",
            recipe,
            mapOf(
                "cardInstanceId" to previousState.cardInstanceId,
                "runId" to previousState.runId.orEmpty(),
                "terminalSessionId" to previousState.terminalSessionId.orEmpty()
            )
        )
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
                instanceId = previousState.instanceId,
                surface = CardRunSurface.Summary,
                currentStepIndex = previousState.currentStepIndex,
                lastMeaningfulOutput = "网页实例已关闭",
                clearRunBinding = true,
                clearNextActionUrl = true
            )
            closeCardRunInstanceForStop(recipe, previousState, "stop_opened_web")
            if (navigateToConsole) showConsole()
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
            if (previousState.hasProcessBindingForStop()) {
                setRuntimeState(
                    recipe,
                    RecipeRunStatus.Stopping,
                    instanceId = previousState.instanceId,
                    runId = previousState.runId,
                    terminalSessionId = terminalSessionId,
                    pid = previousState.pid,
                    rootPid = previousState.rootPid,
                    processGroupId = previousState.processGroupId,
                    systemSessionId = previousState.systemSessionId,
                    lastMeaningfulOutput = previousState.lastMeaningfulOutput,
                    nextActionUrl = previousState.nextActionUrl
                )
                if (navigateToConsole) showConsole()
                val callback: (BridgeResult) -> Unit = { result ->
                    runOnUiThread { handleStopResultV2(recipe, previousState, result, navigateToConsole = navigateToConsole) }
                }
                diagnostics.logBridgeEvent(
                    "stop_terminal_process_request_sent",
                    recipe,
                    mapOf(
                        "sessionId" to terminalSessionId,
                        "runId" to previousState.runId.orEmpty(),
                        "pid" to previousState.pid.orEmpty(),
                        "rootPid" to previousState.rootPid.orEmpty(),
                        "processGroupId" to previousState.processGroupId.orEmpty()
                    )
                )
                bridgeClient.stopProcessBinding(
                    recipe = recipe,
                    runId = previousState.runId.orEmpty().ifBlank { previousState.instanceId },
                    pid = previousState.pid,
                    rootPid = previousState.rootPid,
                    processGroupId = previousState.processGroupId,
                    systemSessionId = previousState.systemSessionId,
                    cardInstanceId = previousState.cardInstanceId,
                    callback = callback
                )
                closeCardRunInstanceForStop(recipe, previousState, "stop_terminal_and_process_request_sent")
                return
            }
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                instanceId = previousState.instanceId,
                surface = CardRunSurface.Summary,
                currentStepIndex = previousState.currentStepIndex,
                lastMeaningfulOutput = "终端已发送中断并关闭",
                clearRunBinding = true,
                clearTerminalSession = true
            )
            closeCardRunInstanceForStop(recipe, previousState, "stop_terminal_session")
            if (navigateToConsole) showConsole()
            return
        }

        setRuntimeState(
            recipe,
            RecipeRunStatus.Stopping,
            instanceId = previousState.instanceId,
            runId = previousState.runId,
            pid = previousState.pid,
            rootPid = previousState.rootPid,
            processGroupId = previousState.processGroupId,
            systemSessionId = previousState.systemSessionId,
            lastMeaningfulOutput = previousState.lastMeaningfulOutput,
            nextActionUrl = previousState.nextActionUrl
        )
        if (navigateToConsole) showConsole()
        val callback: (BridgeResult) -> Unit = { result ->
            runOnUiThread { handleStopResultV2(recipe, previousState, result, navigateToConsole = navigateToConsole) }
        }
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
                cardInstanceId = previousState.cardInstanceId,
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
                cardInstanceId = previousState.cardInstanceId,
                callback = callback
            )
        }
        closeCardRunInstanceForStop(recipe, previousState, "stop_request_sent")
    }

    private fun RecipeRuntimeState.hasProcessBindingForStop(): Boolean =
        !pid.isNullOrBlank() ||
            !rootPid.isNullOrBlank() ||
            !processGroupId.isNullOrBlank() ||
            !systemSessionId.isNullOrBlank()

    private fun retryStopRequestAfterStableBridge(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        navigateToConsole: Boolean
    ) {
        diagnostics.logBridgeEvent("stop_timeout_bridge_stable_retry", recipe, mapOf("runId" to previousState.runId.orEmpty()))
        val callback: (BridgeResult) -> Unit = { retryResult ->
            runOnUiThread {
                handleStopResultV2(
                    recipe,
                    previousState,
                    retryResult,
                    retriedAfterStableBridge = true,
                    navigateToConsole = navigateToConsole
                )
            }
        }
        if (!previousState.runId.isNullOrBlank()) {
            bridgeClient.stopRun(
                recipe = recipe,
                runId = previousState.runId,
                pid = previousState.pid,
                rootPid = previousState.rootPid,
                processGroupId = previousState.processGroupId,
                systemSessionId = previousState.systemSessionId,
                cardInstanceId = previousState.cardInstanceId,
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
                cardInstanceId = previousState.cardInstanceId,
                callback = callback
            )
        }
    }

    private fun handleStopResultV2(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        result: BridgeResult,
        retriedAfterStableBridge: Boolean = false,
        navigateToConsole: Boolean = true
    ) {
        activeRunInstanceIds[recipe.id] = previousState.instanceId
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
        val stopRemaining = stopRemainingProcesses(result)
        if (stopRemaining.isNotEmpty()) {
            diagnostics.logBridgeEvent(
                "stop_residue_detected",
                recipe,
                mapOf("remaining" to stopRemaining.joinToString(","))
            )
        }
        if (stopRemaining.isEmpty() && stopLooksLikeManualKill(result)) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                instanceId = previousState.instanceId,
                surface = CardRunSurface.Summary,
                lastMeaningfulOutput = "已停止",
                clearRunBinding = true,
                clearTerminalSession = true,
                clearNextActionUrl = true
            )
            diagnostics.logBridgeEvent("stop_killed_output_suppressed", recipe, mapOf("runId" to previousState.runId.orEmpty()))
            closeCardRunInstanceForStop(recipe, previousState, "stop_killed_output_suppressed")
            if (navigateToConsole) showConsole()
            return
        }
        if (stopRemaining.isEmpty() && (result.ok || result.accepted) && result.status == KiteRunReport.STATUS_STOPPED) {
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                instanceId = previousState.instanceId,
                surface = CardRunSurface.Summary,
                lastMeaningfulOutput = if (stopResidueMarkerSeen(result)) {
                    "已停止，未发现进程残留"
                } else {
                    result.runReport?.lastMeaningfulOutput() ?: "已停止"
                },
                clearRunBinding = true,
                clearTerminalSession = true,
                clearNextActionUrl = true
            )
            diagnostics.logBridgeEvent("stop_success", recipe, mapOf("runId" to previousState.runId.orEmpty()))
            closeCardRunInstanceForStop(recipe, previousState, "stop_success")
            if (navigateToConsole) showConsole()
            return
        }

        if (result.errorType == BridgeErrorType.Timeout && !retriedAfterStableBridge) {
            diagnostics.logBridgeEvent("stop_timeout", recipe, mapOf("runId" to previousState.runId.orEmpty()))
            bridgeClient.checkStatus { status ->
                runOnUiThread {
                    if (status.ok || status.accepted) {
                        retryStopRequestAfterStableBridge(recipe, previousState, navigateToConsole)
                    } else {
                        diagnostics.logBridgeEvent("stop_connection_error", recipe, mapOf("message" to status.message.take(500)))
                        setRuntimeState(
                            recipe,
                            RecipeRunStatus.BridgeUnavailable,
                            instanceId = previousState.instanceId,
                            runId = previousState.runId,
                            pid = previousState.pid,
                            rootPid = previousState.rootPid,
                            processGroupId = previousState.processGroupId,
                            systemSessionId = previousState.systemSessionId,
                            lastError = "Bridge 连接失败"
                        )
                        if (navigateToConsole) showConsole()
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
                if (stopRemaining.isNotEmpty()) {
                    "停止后仍有进程残留：${stopRemaining.joinToString(",")}"
                } else {
                    result.runReport?.lastMeaningfulOutput() ?: result.message.ifBlank { "Bridge 返回停止失败" }
                }
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
            instanceId = previousState.instanceId,
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
        if (navigateToConsole) showConsole()
    }

    private fun stopRemainingProcesses(result: BridgeResult): List<String> =
        stopObservationText(result)
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("__kite_stop_remaining:") }
            .lastOrNull()
            ?.substringAfter(':')
            ?.split(',')
            .orEmpty()
            .map { it.trim() }
            .filter { it.matches(Regex("\\d+")) }
            .distinct()

    private fun stopResidueMarkerSeen(result: BridgeResult): Boolean =
        stopObservationText(result)
            .lineSequence()
            .map { it.trim() }
            .any { it.startsWith("__kite_stop_remaining:") }

    private fun stopLooksLikeManualKill(result: BridgeResult): Boolean =
        MANUAL_STOP_KILLED_REGEX.containsMatchIn(stopObservationText(result))

    private fun stopObservationText(result: BridgeResult): String = buildString {
        result.runReport?.steps.orEmpty().forEach { step ->
            appendLine(step.lastMeaningfulOutput)
            appendLine(step.stdoutTail)
            appendLine(step.stderrTail)
        }
        appendLine(result.message)
        appendLine(result.rawBody)
    }

    private fun bridgeFailureArrivedAfterManualStop(recipe: KiteRecipe, result: BridgeResult): RecipeRuntimeState? {
        val currentState = runtimeStateFor(recipe)
        if (currentState.status != RecipeRunStatus.Stopping && currentState.status != RecipeRunStatus.Stopped) return null
        val report = result.runReport
        val failed = result.status == KiteRunReport.STATUS_BRIDGE_UNAVAILABLE ||
            report?.hasMismatch() == true ||
            report?.ok == false ||
            report?.status == KiteRunReport.STATUS_FAILED ||
            (!result.ok && !result.accepted)
        if (!failed || !stopLooksLikeManualKill(result)) return null
        return currentState
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

        bridgeFailureArrivedAfterManualStop(recipe, result)?.let { stoppedState ->
            setRuntimeState(
                recipe,
                RecipeRunStatus.Stopped,
                instanceId = stoppedState.instanceId,
                surface = CardRunSurface.Summary,
                lastMeaningfulOutput = "已停止",
                clearRunBinding = true,
                clearTerminalSession = true,
                clearNextActionUrl = true
            )
            diagnostics.logRecipeAction(
                recipe,
                "bridge_killed_output_suppressed_after_stop",
                mapOf("requestId" to requestId, "runId" to runId.orEmpty())
            )
            closeCardRunInstanceForStop(recipe, stoppedState, "bridge_killed_output_suppressed_after_stop")
            showConsole()
            return
        }

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
        val diagnosticUrl = BrowserHandoffPolicy.redactedUrlForDiagnostics(url)
        if (!shouldProbeWebReady(url)) {
            diagnostics.logBridgeEvent("open_web_after_ready", recipe, mapOf("url" to diagnosticUrl, "mode" to "probe_skipped"))
            setRuntimeState(recipe, finalStatus, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, nextActionUrl = url)
            openWeb(url, "bridge_next_action", recipe)
            return
        }

        diagnostics.logBridgeEvent("web_ready_probe_start", recipe, mapOf("url" to diagnosticUrl, "runId" to runId.orEmpty()))
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
                    diagnostics.logBridgeEvent("web_ready_probe_success", recipe, mapOf("url" to diagnosticUrl, "attempts" to attempt.toString()))
                    diagnostics.logBridgeEvent("open_web_after_ready", recipe, mapOf("url" to diagnosticUrl, "runId" to runId.orEmpty()))
                    setRuntimeState(recipe, finalStatus, runId = runId, pid = pid, lastMeaningfulOutput = lastOutput, nextActionUrl = url)
                    openWeb(url, "bridge_next_action_ready", recipe)
                } else {
                    diagnostics.logBridgeEvent(
                        "web_ready_probe_timeout",
                        recipe,
                        mapOf("url" to diagnosticUrl, "attempts" to attempt.toString(), "lastError" to lastError.take(500))
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
        x11Display: String? = null,
        x11SocketPath: String? = null,
        clearRunBinding: Boolean = false,
        clearTerminalSession: Boolean = false,
        clearNextActionUrl: Boolean = false
    ) {
        if (shouldIgnoreRuntimeStateAfterUserStop(
                recipe = recipe,
                status = status,
                instanceId = instanceId,
                runId = runId,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                lastMeaningfulOutput = lastMeaningfulOutput,
                lastError = lastError,
                shellReportText = shellReportText,
                x11Display = x11Display,
                x11SocketPath = x11SocketPath
            )
        ) {
            return
        }
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
            x11Display = x11Display,
            x11SocketPath = x11SocketPath,
            clearRunBinding = clearRunBinding,
            clearTerminalSession = clearTerminalSession,
            clearNextActionUrl = clearNextActionUrl
        )
        runtimeStates[recipe.id] = state
        if (recipe.runtimeSource == RESOURCE_OPEN_RUNTIME_SOURCE) {
            invalidateResourceCatalogCache()
        }
        if (status == RecipeRunStatus.Stopped) {
            clearActiveRunInstance(recipe, state.instanceId)
        }
        updateVisibleCardRunReport(state)
        resourceInstallWizardSurface?.tick()
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

    private fun shouldIgnoreRuntimeStateAfterUserStop(
        recipe: KiteRecipe,
        status: RecipeRunStatus,
        instanceId: String?,
        runId: String?,
        pid: String?,
        rootPid: String?,
        processGroupId: String?,
        systemSessionId: String?,
        lastMeaningfulOutput: String?,
        lastError: String?,
        shellReportText: String?,
        x11Display: String?,
        x11SocketPath: String?
    ): Boolean {
        if (status == RecipeRunStatus.Stopping || status == RecipeRunStatus.Stopped) return false
        val targetInstanceId = instanceId
            ?: activeRunInstanceIds[recipe.id]
            ?: CardRunStore.currentForRecipe(recipe.id)?.instanceId
            ?: return false
        val current = CardRunStore.get(targetInstanceId) ?: return false
        if (current.status != RecipeRunStatus.Stopping && current.status != RecipeRunStatus.Stopped) return false
        val carriesOldRuntimeResult = listOf(
            runId,
            pid,
            rootPid,
            processGroupId,
            systemSessionId,
            lastMeaningfulOutput,
            lastError,
            shellReportText,
            x11Display,
            x11SocketPath
        ).any { !it.isNullOrBlank() } || status == RecipeRunStatus.BridgeUnavailable
        if (!carriesOldRuntimeResult) return false
        // ponytail: instance-level stale callback gate; replace with request tokens if launches become multi-flight.
        diagnostics.logRecipeAction(
            recipe,
            "runtime_state_ignored_after_user_stop",
            mapOf(
                "instanceId" to targetInstanceId,
                "currentStatus" to current.status.name,
                "incomingStatus" to status.name,
                "runId" to runId.orEmpty(),
                "pid" to pid.orEmpty()
            )
        )
        return true
    }

    private fun clearActiveRunInstance(recipe: KiteRecipe, instanceId: String) {
        if (activeRunInstanceIds[recipe.id] == instanceId) {
            activeRunInstanceIds.remove(recipe.id)
        }
        if (focusedRunInstanceId == instanceId && this !is CardRunActivity) {
            focusedRunInstanceId = null
        }
    }

    private fun showCreateConfig() = showRecipeEditorFeature(recipeId = null)

    private fun showRecipeEditor(recipe: KiteRecipe) = showRecipeEditorFeature(recipe.id)

    private fun showRecipeEditorFeature(
        recipeId: String?,
        restoredDraftRaw: String? = null
    ) {
        val normalizedRecipeId = recipeId?.trim().orEmpty()
        enterScreen(AppDestination.CreateConfig) {
            (supportFragmentManager.findFragmentByTag(TAG_RECIPE_EDITOR_FRAGMENT) as? RecipeEditorFragment)
                ?.handleBackRequest()
                ?: showConsole()
        }
        rootHost.setBackgroundColor(tokens.pageBackground)
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val content = FrameLayout(this).apply {
            id = R.id.kite_feature_content
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val existing = supportFragmentManager
            .findFragmentByTag(TAG_RECIPE_EDITOR_FRAGMENT) as? RecipeEditorFragment
        val reuseExisting = existing != null &&
            restoredDraftRaw == null &&
            existing.recipeIdHint() == normalizedRecipeId
        if (existing != null && !reuseExisting) {
            supportFragmentManager.beginTransaction()
                .remove(existing)
                .commitNowAllowingStateLoss()
        }
        val fragment = if (reuseExisting) {
            existing!!
        } else {
            RecipeEditorFragment.newInstance(
                recipeId = normalizedRecipeId.takeIf(String::isNotBlank),
                restoredDraftRaw = restoredDraftRaw,
                runtimeBlocked = ubuntuRuntimeState.blocksUbuntuActions
            )
        }
        fragment.updateRuntimeBlocked(ubuntuRuntimeState.blocksUbuntuActions)
        supportFragmentManager.beginTransaction().apply {
            if (fragment.isDetached) {
                attach(fragment)
            } else if (!fragment.isAdded) {
                add(R.id.kite_feature_content, fragment, TAG_RECIPE_EDITOR_FRAGMENT)
            } else {
                replace(R.id.kite_feature_content, fragment, TAG_RECIPE_EDITOR_FRAGMENT)
            }
        }.commitNowAllowingStateLoss()
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

    private fun decodeRecipeIconSource(source: String): Bitmap? {
        val normalized = source.trim().trimStart('/')
        if (normalized.isBlank() || normalized.contains("..")) return null
        recipeIconBitmapCache[normalized]?.let { return it }
        val file = recipeIconFile(source)
        val bitmap = if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            runCatching {
                assets.open(normalized).use { stream -> BitmapFactory.decodeStream(stream) }
            }.getOrNull()
        }
        if (bitmap != null) recipeIconBitmapCache[normalized] = bitmap
        return bitmap
    }

    private fun recipeIconFile(source: String): File =
        if (source.startsWith("/") || source.contains(":")) {
            File(source)
        } else {
            File(filesDir, source)
        }

    private fun requestShortcutForRecipe(recipe: KiteRecipe): Boolean {
        if (recipe.id.isBlank()) return false
        if (CardShortcutManager.hasPinnedShortcut(this, recipe.id)) {
            Toast.makeText(this, "桌面图标已存在", Toast.LENGTH_SHORT).show()
            return true
        }

        val requested = CardShortcutManager.requestPinnedShortcut(this, recipe)
        if (requested) {
            Toast.makeText(this, "已提交桌面快捷方式申请", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "当前桌面不支持自动创建快捷方式", Toast.LENGTH_SHORT).show()
        }
        return requested
    }

    private fun showRecipeRawJson(recipe: KiteRecipe) {
        // T6b:走 Fragment 路径(RecipeRawJsonFragment)。先记录目标 recipe 供 Fragment 按 id 加载,
        // 再通过 routeToRecipeRawJsonFragment 切换到 Fragment。
        pendingRawJsonRecipeId = recipe.id.ifBlank { recipe.name }
        routeToRecipeRawJsonFragment()
    }

    /**
     * T6b:把 root 隐藏,把 RecipeRawJsonFragment 加到 rootHost 容器。
     * 这是 P2 第一个走 Fragment 的 AppDestination,验证整套机制。
     */
    private fun routeToRecipeRawJsonFragment() {
        enterScreen(AppDestination.RecipeDetail, ::exitRecipeRawJson)
        clearRootForScreen()
        root.visibility = View.GONE
        val recipeId = pendingRawJsonRecipeId ?: return
        val fragment = RecipeRawJsonFragment.newInstance(recipeId)
        supportFragmentManager.beginTransaction()
            .replace(rootHost.id, fragment, TAG_RECIPE_RAW_JSON_FRAGMENT)
            .commitAllowingStateLoss()
    }

    /** 退出 RecipeRawJson Fragment:恢复 root,回到编辑器。 */
    private fun exitRecipeRawJson() {
        supportFragmentManager.findFragmentByTag(TAG_RECIPE_RAW_JSON_FRAGMENT)?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
        }
        root.visibility = View.VISIBLE
        // 回到编辑器:用最近一次记录的 recipe
        val recipe = pendingRawJsonRecipeId?.let { id -> latestRecipeById(id) }
        if (recipe != null) showRecipeEditor(recipe) else showConsole()
    }

    override fun onRecipeRawJsonBackRequested() {
        requestNavigationBack()
    }

    /** RecipeRawJsonFragment.RecipeProvider 实现:按 id 加载最新 recipe。 */
    override fun latestRecipeFor(recipeId: String): KiteRecipe? = latestRecipeById(recipeId)

    /** RecipeRawJsonFragment.UiKitProvider 实现:共享 Activity 的主题 tokens 给 Fragment。 */
    override fun provideUiKit(): com.kite.app.ui.UiKit =
        com.kite.app.ui.UiKit(this, tokens)

    private fun latestRecipeById(recipeId: String): KiteRecipe {
        val seed = currentRecipes.firstOrNull { it.id == recipeId || it.name == recipeId }
        return latestRecipeForRawJson(seed ?: KiteRecipe(id = recipeId, name = recipeId, description = "", type = KiteRecipe.TYPE_OPEN_URL, defaultUrl = "", shortcut = false))
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

    private fun showRecipeRunHistory(recipe: KiteRecipe) {
        enterScreen(AppDestination.RecipeMore) { showRecipeEditor(recipe) }
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(topBar("运行历史", ::requestNavigationBack))
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(40))
                addView(recentRunHistoryPanel(recipe))
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
            screen = AppDestination.RecipeDetail,
            title = "运行详情",
            backAction = { showRecipeEditor(latestRecipe) },
            recipe = latestRecipe,
            entry = entry,
            onStepReport = { step -> showRecipeRunHistoryStepReport(recipe, entry, step) }
        )
    }

    private fun showResourceRunHistoryDetail(item: ResourceItem, recipe: KiteRecipe, entry: CardRunHistoryEntry) {
        showRunHistoryDetail(
            screen = AppDestination.ResourceMore,
            title = "获取日志",
            backAction = { showResourceMoreActions(item) },
            recipe = recipe,
            entry = entry,
            onStepReport = { step -> showResourceRunHistoryStepReport(item, recipe, entry, step) }
        )
    }

    private fun showRunHistoryDetail(
        screen: AppDestination,
        title: String,
        backAction: () -> Unit,
        recipe: KiteRecipe,
        entry: CardRunHistoryEntry,
        onStepReport: (CardRunHistoryStep) -> Unit
    ) {
        enterScreen(screen, backAction)
        clearRootForScreen()
        root.addView(topBar(title, ::requestNavigationBack))
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
            screen = AppDestination.RecipeDetail,
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
            screen = AppDestination.ResourceMore,
            backAction = { showResourceRunHistoryDetail(item, recipe, entry) },
            step = step
        )
    }

    private fun showRunHistoryStepReport(
        screen: AppDestination,
        backAction: () -> Unit,
        step: CardRunHistoryStep
    ) {
        enterScreen(screen, backAction)
        clearRootForScreen()
        root.addView(topBar("历史 SH 报告", ::requestNavigationBack))
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

    private fun registerCardRunDesktopHandler(recipe: KiteRecipe, instanceId: String) {
        if (this !is CardRunActivity || instanceId.isBlank()) return
        if (registeredDesktopInstanceId != instanceId) {
            CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
            registeredDesktopInstanceId = instanceId
        }
        CardRunDesktopRouter.register(instanceId) { request ->
            runOnUiThread { focusDesktopRequestInCardRun(recipe, instanceId, request) }
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
                mapOf(
                    "instanceId" to instanceId,
                    "source" to normalized.source,
                    "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(normalized.url)
                )
            )
            if (this is CardRunActivity && focusedRunInstanceId == instanceId) {
                showCardRunSurface(recipe)
            }
            return
        }

        openTemporaryBrowserRequest(normalized)
    }

    private fun handleDesktopOpenRequest(request: KiteDesktopOpenRequest): KiteDesktopOpenResponse {
        val normalized = request.copy(command = request.command.trim())
        if (normalized.command.isBlank()) {
            return KiteDesktopOpenResponse(false, normalized.recipeId, normalized.instanceId, "", "", "missing_command")
        }
        val accepted = acceptDesktopOpenRequest(normalized)
        if (accepted.accepted) {
            accepted.instanceId?.let { CardRunDesktopRouter.dispatch(normalized.copy(recipeId = accepted.recipeId, instanceId = it)) }
        }
        return accepted
    }

    private fun handleInstallApkRequest(request: KiteInstallApkRequest): KiteInstallApkResponse {
        val normalizedPath = request.path.trim()
        if (normalizedPath.isBlank()) {
            return KiteInstallApkResponse(false, request.path, error = "missing_path")
        }
        val apkFile = resolveInstallApkFile(normalizedPath)
            ?: return KiteInstallApkResponse(false, normalizedPath, error = "unsupported_path")
        if (!apkFile.name.endsWith(".apk", ignoreCase = true)) {
            return KiteInstallApkResponse(false, normalizedPath, apkFile.absolutePath, "not_apk")
        }
        if (!apkFile.isFile) {
            return KiteInstallApkResponse(false, normalizedPath, apkFile.absolutePath, "apk_not_found")
        }
        runOnUiThread { openApkInstaller(apkFile) }
        return KiteInstallApkResponse(true, normalizedPath, apkFile.absolutePath)
    }

    private fun installApkPathFromStep(step: KiteRecipeStep): String =
        step.params?.optString("path")?.takeIf { it.isNotBlank() }
            ?: step.params?.optString("apk")?.takeIf { it.isNotBlank() }
            ?: step.cmd?.takeIf { it.isNotBlank() }
            ?: step.text?.takeIf { it.isNotBlank() }
            ?: ""

    private fun resolveInstallApkFile(path: String): File? {
        val rawPath = if (path.startsWith("file://", ignoreCase = true)) {
            Uri.parse(path).path.orEmpty()
        } else {
            path
        }.trim()
        if (rawPath.isBlank()) return null
        return when {
            rawPath == ExternalExchangeManager.CONTAINER_MOUNT_PATH -> null
            rawPath.startsWith("${ExternalExchangeManager.CONTAINER_MOUNT_PATH}/") -> {
                val relative = rawPath.removePrefix("${ExternalExchangeManager.CONTAINER_MOUNT_PATH}/")
                File(ExternalExchangeManager.ensureExchangeDir(this), relative)
            }
            rawPath.startsWith("/sdcard/") || rawPath.startsWith("/storage/") -> File(rawPath)
            else -> null
        }?.absoluteFile
    }

    private fun openApkInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }
            .onSuccess { Toast.makeText(this, "已打开安装器：${apkFile.name}", Toast.LENGTH_SHORT).show() }
            .onFailure { error ->
                Toast.makeText(this, "无法打开安装器：${error.message.orEmpty()}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun acceptDesktopOpenRequest(request: KiteDesktopOpenRequest): KiteDesktopOpenResponse {
        val existingState = request.instanceId?.takeIf { it.isNotBlank() }?.let { CardRunStore.get(it) }
        val recipeId = request.recipeId?.takeIf { it.isNotBlank() }
            ?: existingState?.recipeId
            ?: "temp_desktop_${UUID.randomUUID().toString().replace("-", "")}"
        val recipe = CardRunStore.registeredRecipe(recipeId)
            ?: currentRecipes.firstOrNull { it.id == recipeId }
            ?: runCatching { recipeLoader.loadAllRecipes().firstOrNull { it.id == recipeId } }.getOrNull()
            ?: temporaryDesktopRecipe(recipeId, request.command, request.title?.takeIf { it.isNotBlank() } ?: "临时桌面")
        val instanceId = request.instanceId?.takeIf { it.isNotBlank() } ?: CardRunIntents.newInstanceId(recipe.id)
        val binding = existingState?.x11Display?.let { KiteX11SurfacePlan.binding(it) }
            ?: KiteX11SurfacePlan.allocate(
                instanceId = instanceId,
                occupiedDisplays = CardRunStore.snapshot()
                    .filterNot { it.instanceId == instanceId }
                    .mapNotNull { it.x11Display }
                    .toSet()
            )
        CardRunStore.registerRecipe(recipe)
        CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_X11,
            stepId = "desktop_request"
        )
        val preparingState = CardRunStore.update(
            recipe = recipe,
            status = RecipeRunStatus.Running,
            instanceId = instanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_X11,
            stepId = "desktop_request",
            surface = CardRunSurface.Report,
            currentStepIndex = 0,
            lastMeaningfulOutput = "正在准备 X11 桌面：${request.command.take(120)}",
            x11Display = binding.display,
            x11SocketPath = binding.socketPath,
            clearNextActionUrl = true
        )
        runOnUiThread {
            activeRunInstanceIds[recipe.id] = instanceId
            runtimeStates[recipe.id] = preparingState
            if (request.instanceId.isNullOrBlank()) {
                startActivity(
                    CardRunIntents.launchIntent(
                        context = this,
                        recipeId = recipe.id,
                        instanceId = instanceId,
                        launchSource = CardRunIntents.SOURCE_CARD,
                        autoStart = false
                    )
                )
            } else if (this is CardRunActivity && focusedRunInstanceId == instanceId) {
                showCardRunLoadingSurface(recipe, "正在准备 X11 桌面")
            }
        }
        val x11Start = KiteX11SurfaceServer.ensureStarted(applicationContext, binding)
        if (x11Start.isFailure) {
            val message = x11Start.exceptionOrNull()?.message ?: "native X11 启动失败"
            val failedState = CardRunStore.update(
                recipe = recipe,
                status = RecipeRunStatus.Failed,
                instanceId = instanceId,
                ownerKind = RecipeRuntimeState.OWNER_KIND_X11,
                stepId = "desktop_request",
                surface = CardRunSurface.Report,
                currentStepIndex = 0,
                lastError = message,
                x11Display = binding.display,
                x11SocketPath = binding.socketPath
            )
            runOnUiThread {
                runtimeStates[recipe.id] = failedState
                if (this is CardRunActivity && focusedRunInstanceId == instanceId) showRunSurfaceOrConsole(recipe)
            }
            diagnostics.logRecipeAction(
                recipe,
                "desktop_request_x11_failed",
                mapOf(
                    "instanceId" to instanceId,
                    "source" to request.source,
                    "display" to binding.display,
                    "error" to message
                )
            )
            return KiteDesktopOpenResponse(false, recipe.id, instanceId, "", "", message)
        }

        val state = CardRunStore.update(
            recipe = recipe,
            status = RecipeRunStatus.Running,
            instanceId = instanceId,
            ownerKind = RecipeRuntimeState.OWNER_KIND_X11,
            stepId = "desktop_request",
            surface = CardRunSurface.X11,
            currentStepIndex = 0,
            lastMeaningfulOutput = "Ubuntu 请求桌面：${request.command.take(120)}",
            x11Display = binding.display,
            x11SocketPath = binding.socketPath,
            clearNextActionUrl = true
        )
        runOnUiThread {
            activeRunInstanceIds[recipe.id] = instanceId
            runtimeStates[recipe.id] = state
            if (this is CardRunActivity && focusedRunInstanceId == instanceId) {
                showCardRunSurface(recipe)
            }
        }
        diagnostics.logRecipeAction(
            recipe,
            "desktop_request_accepted",
            mapOf(
                "instanceId" to instanceId,
                "source" to request.source,
                "display" to binding.display,
                "command" to request.command.take(500)
            )
        )
        return KiteDesktopOpenResponse(true, recipe.id, instanceId, binding.display, binding.socketPath)
    }

    private fun focusDesktopRequestInCardRun(
        recipe: KiteRecipe,
        instanceId: String,
        request: KiteDesktopOpenRequest
    ) {
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = instanceId
        title = recipe.name
        diagnostics.logRecipeAction(
            recipe,
            "desktop_request_opened_in_instance",
            mapOf("instanceId" to instanceId, "source" to request.source, "command" to request.command.take(500))
        )
        showCardRunSurface(recipe)
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
            mapOf(
                "instanceId" to instanceId,
                "source" to request.source,
                "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url)
            )
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
        val decision = BrowserHandoffPolicy.classify(request.url, request.source)
        if (decision is BrowserHandoffDecision.StartCliCallbackHandoff) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            title = recipe.name
            launchBrowserHandoff(
                request = BrowserHandoffRequest(
                    url = request.url,
                    recipeId = recipe.id,
                    recipeName = recipe.name,
                    instanceId = instanceId,
                    source = request.source
                ),
                decision = decision,
                rerenderFocusedSurface = false
            )
            diagnostics.logRecipeAction(
                recipe,
                "browser_cli_loopback_handoff_opened_in_instance",
                mapOf(
                    "instanceId" to instanceId,
                    "source" to request.source,
                    "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url)
                )
            )
            return
        }
        val state = updateBrowserRequestState(recipe, instanceId, request)
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = state.instanceId
        title = recipe.name
        diagnostics.logRecipeAction(
            recipe,
            "browser_request_opened_in_instance",
            mapOf(
                "instanceId" to instanceId,
                "source" to request.source,
                "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url)
            )
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
        val displayTarget = redactUrlCredentials(target)
        diagnostics.logOpenWebAttempt(recipe, displayTarget, source)
        diagnostics.writeWebAppStatus(
            url = displayTarget,
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
        enterScreen(AppDestination.Workbench)
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val parent = webView.parent
        if (parent is ViewGroup) parent.removeView(webView)
        if (this is CardRunActivity && recipe != null) {
            val state = focusedRunInstanceId
                ?.let { CardRunStore.get(it) }
                ?: runtimeStateFor(recipe)
            val surfaceHost = FrameLayout(this).apply {
                setBackgroundColor(tokens.pageBackground)
            }
            root.addView(surfaceHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            surfaceHost.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addCardRunFloatingCapsule(surfaceHost, recipe, state, recipe, state)
        } else {
            root.addView(topBar("Kite 工作台", ::requestNavigationBack))
            root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        webShell.open(url, recipeId = recipe?.id, recipeName = recipe?.name, openSource = source)
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

    private fun bottomNavigation(): View = row {
        setPadding(dp(14), dp(3), dp(14), dp(4))
        setBackgroundColor(Color.argb(
            238,
            Color.red(tokens.surfaceElevated),
            Color.green(tokens.surfaceElevated),
            Color.blue(tokens.surfaceElevated)
        ))
        elevation = dp(6).toFloat()
        addView(navItem("▦", "配置", currentScreen == AppDestination.Console) { appNavigator.navigate(AppDestination.Console) })
        addView(navItem(">_", "终端", currentScreen == AppDestination.Terminal) { appNavigator.navigate(AppDestination.Terminal) })
        addView(navItem("≡", "资源", currentScreen == AppDestination.Resources || currentScreen == AppDestination.ResourceManage || currentScreen == AppDestination.ResourceDetail || currentScreen == AppDestination.ResourceMore || currentScreen == AppDestination.ResourceRawJson) { appNavigator.navigate(AppDestination.Resources) })
        addView(navItem("⚙", "设置", currentScreen == AppDestination.Settings || currentScreen == AppDestination.ThemeSettings) { appNavigator.navigate(AppDestination.Settings) })
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
            KiteRecipe.STEP_X11 -> "X11"
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
            firstStep?.type == KiteRecipe.STEP_X11 -> "X11 · ${compactCommand(firstStep.cmd ?: firstStep.text.orEmpty())}"
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
        val displayUrl = redactUrlCredentials(url)
        return runCatching {
            val parsed = Uri.parse(displayUrl)
            val host = parsed.host.orEmpty()
            if (host.isBlank()) return@runCatching displayUrl.take(90)
            val port = parsed.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
            val path = parsed.path.orEmpty().takeIf { it.isNotBlank() && it != "/" }.orEmpty()
            "$host$port$path".take(90)
        }.getOrDefault(displayUrl.take(90))
    }

    private fun redactUrlCredentials(url: String?): String {
        val value = url.orEmpty()
        if (value.isBlank()) return value
        return runCatching {
            val parsed = java.net.URI(value)
            if (parsed.userInfo.isNullOrBlank() || parsed.host.isNullOrBlank()) {
                value
            } else {
                java.net.URI(parsed.scheme, null, parsed.host, parsed.port, parsed.path, parsed.query, parsed.fragment).toString()
            }
        }.getOrDefault(value)
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

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun displayAccentName(recipe: KiteRecipe): String =
        recipe.card.accent.ifBlank { "primary" }

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

    private data class ResourceItem(
        val id: String,
        val name: String,
        val description: String,
        val longDescription: String,
        val section: String,
        val category: String,
        val iconText: String,
        val iconAsset: String = "",
        val iconFit: String = "",
        val accent: String,
        val version: String,
        val sizeLabel: String,
        val sourceLabel: String,
        val stateLabel: String,
        val actionLabel: String,
        val actionEnabled: Boolean = true,
        val secondaryActionLabel: String? = null,
        val runtimeFacts: KiteResourceRuntimeFacts,
        val includes: List<String>,
        val notes: List<String>,
        val steps: List<ResourceStep>,
        val badge: ResourceBadge,
        val media: ResourceMedia?,
        val previewCards: List<ResourcePreviewCard>,
        val requirementRows: List<ResourceRequirementRow>,
        val recommendations: List<ResourceRecommendation> = emptyList(),
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

    private data class ResourceBadge(
        val label: String,
        val iconText: String,
        val accent: String
    )

    private data class ResourceMedia(
        val type: String,
        val asset: String,
        val contentDescription: String
    )

    private data class ResourceRequirementRow(
        val label: String,
        val value: String
    )

    private data class ResourceRuntimeLabels(
        val state: String,
        val action: String,
        val actionEnabled: Boolean,
        val secondaryAction: String? = null
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
        val selectedInstanceId: String? = null,
        val selectedSurface: CardRunSurface = CardRunSurface.InstallWizard
    )

    private data class CardRunReportBinding(
        val recipeId: String,
        val instanceId: String,
        val outputTextView: TextView?,
        val outputScrollView: ScrollView?,
        val footerTextView: TextView?,
        val elapsedTextView: TextView?,
        val statusBadgeTextView: TextView?
    )
    private data class ResourcePreviewCard(
        val title: String,
        val subtitle: String,
        val symbol: String,
        val accent: String,
        val iconAsset: String = "",
        val iconFit: String = ""
    )

    private data class SummaryMetric(
        val label: String,
        val value: String,
        val weight: Float
    )

    private data class RuntimePanelSummary(
        val runningCards: Int,
        val runningTerminals: Int,
        val runningProcesses: Int
    )

    private data class RunManagementGroup(
        val run: RecipeRuntimeState,
        val recipe: KiteRecipe?,
        val terminal: TerminalSessionItem?,
        val mainProcess: TaskManagerProcessItem?,
        val childProcesses: List<TaskManagerProcessItem>,
        val processCount: Int
    )

    private data class RunManagementAction(
        val label: String,
        val danger: Boolean = false,
        val onClick: () -> Unit
    )

    private data class SemanticColors(
        val text: Int,
        val background: Int,
        val border: Int
    )

    private data class RuntimePermissionState(
        val missingPermissions: List<String>,
        val needsAllFilesAccess: Boolean
    ) {
        val ready: Boolean = missingPermissions.isEmpty() && !needsAllFilesAccess
    }

    private data class FirstRunPermissionState(
        val runtimePermissionsToRequest: List<String>,
        val needsAllFilesAccess: Boolean
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
        val autoOpenPanel: Boolean = false,
        val requiresPermission: Boolean = false,
        val firstRunPermissionOnboarding: Boolean = false,
        val permissionActionLabel: String = ""
    ) {
        companion object {
            fun checking(): UbuntuRuntimeUiState =
                UbuntuRuntimeUiState(
                    title = "正在检查 Ubuntu",
                    detail = "正在确认系统镜像、权限和基础运行环境。",
                    blocksUbuntuActions = true,
                    isProblem = false
                )

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
        private const val TAG_RECIPE_RAW_JSON_FRAGMENT = "kite-recipe-raw-json"
        private const val TAG_RECIPE_EDITOR_FRAGMENT = "kite-recipe-editor"
        private const val TAG_RESOURCE_MANAGE_FRAGMENT = "kite-resource-manage"
        private const val TAG_RESOURCE_SEARCH_FRAGMENT = "kite-resource-search"
        private const val TAG_RESOURCES_FRAGMENT = "kite-resources"
        private const val TAG_HOME_FRAGMENT = "kite-home"
        private const val TAG_RESOURCE_DETAIL_FRAGMENT = "kite-resource-detail"
        private const val TERMINAL_FRAGMENT_INITIAL_SESSION_ARG = "initial_session_id"
        private const val RESOURCE_NODE_RUNTIME = "kite.nodejs"
        private const val RESOURCE_KF_TOOL_ENV = "kite.tool.env"
        private const val RESOURCE_HERMES_CORE = "kite.hermes.core"
        private const val RESOURCE_HERMES_WEBUI = "kite.hermes.webui"
        private const val RESOURCE_GIT = "kite.git"
        private const val RESOURCE_CURL = "kite.curl"
        private const val RESOURCE_PYTHON = "kite.python"
        private const val RESOURCE_UV = "kite.uv"
        private const val RESOURCE_ICON_FIT_FULL_BLEED = "fullBleed"
        private const val RESOURCE_OPEN_RUNTIME_SOURCE = "resource_open"
        private const val RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE = "resource_install_wizard"
        private const val EXTRA_AUTOMATION_RUNTIME_ID = "runtime_id"
        private const val EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES = "probe_target_live_tracees"
        private const val EXTRA_AUTOMATION_OWNER_ID = "owner_id"
        private const val ACTION_DUMP_DIAGNOSTICS = "dump_diagnostics"
        private const val ACTION_ROTATE_PROOT_TELEMETRY = "rotate_proot_telemetry"
        private const val ACTION_REFRESH_PROOT_TELEMETRY_HEARTBEAT = "refresh_proot_telemetry_heartbeat"
        private const val ACTION_PREPARE_PROOT_LIVE_TRACEE_PROBE = "prepare_proot_live_tracee_probe"
        private const val ACTION_INJECT_PROOT_LIVE_TRACEE_PROBE = "inject_proot_live_tracee_probe"
        private const val ACTION_STOP_BACKGROUND_RUNTIME = "stop_background_runtime"
        private const val ACTION_RECLAIM_OWNER_RUNTIME = "reclaim_owner_runtime"
        private const val ACTION_START_RESOURCE_OWNER_PROBE = "start_resource_owner_probe"
        private const val ACTION_START_RESOURCE_INSTALL = "start_resource_install"
        private const val ACTION_START_RESOURCE_OPEN = "start_resource_open"
        private const val ACTION_STOP_CARD_RUN = "stop_card_run"
        private const val RESOURCE_OWNER_PROBE_ID = "kite.owner.telemetry.probe"
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private const val WEB_READY_TIMEOUT_MS = 8000L
        private const val WEB_READY_INTERVAL_MS = 700L
        private const val WEB_READY_CONNECT_TIMEOUT_MS = 700
        private const val WEB_READY_READ_TIMEOUT_MS = 700
        private const val TERMINAL_STEP_COMMAND_DELAY_MS = 650L
        private const val TERMINAL_STOP_GRACE_MS = 350L
        private const val CARD_RUN_REPORT_REFRESH_INTERVAL_MS = 33L
        private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
        private val MANUAL_STOP_KILLED_REGEX = Regex("""\bKilled\b""", RegexOption.IGNORE_CASE)
        private const val RESOURCE_CATALOG_FORCE_REFRESH_GRACE_MS = 1_200L
        private const val RESOURCE_INSTALL_STALE_GRACE_MS = 5_000L
        private const val RESOURCE_PREPARING_STALE_GRACE_MS = 30_000L
        private const val TOOLCHAIN_WORKSPACE_PROBE_TTL_MS = 5_000L
        private const val REQUEST_DROPZONE_STORAGE = 801
        private const val REQUEST_FIRST_RUN_RUNTIME_PERMISSIONS = 803
        private const val REQUEST_NOTIFICATION_PERMISSION = 804
        private const val REQUEST_FIRST_RUN_PERMISSION_ONBOARDING = 805
        private const val KEY_HIDE_MAIN_TASK_FROM_RECENTS = "hide_main_task_from_recents"
        private const val KEY_RESTORE_LAST_SCREEN = "restore_last_screen"
        private const val KEY_FIRST_RUN_PERMISSION_ONBOARDING_DONE = "first_run_permission_onboarding_done"
        private const val KEY_BROWSER_RUNTIME_MODE = "browser_runtime_mode"
        private const val STATE_CURRENT_SCREEN = "kite_current_screen"
        private const val STATE_WORKBENCH_URL = "kite_workbench_url"
        private const val STATE_RECIPE_DRAFT = "kite_recipe_draft"
        private const val RECIPE_DRAFT_RESTORE_WINDOW_MS = 6L * 60L * 60L * 1000L
        private val terminalFlowFinishedStatuses = setOf(
            ManagedTerminalStatus.EXITED,
            ManagedTerminalStatus.FAILED,
            ManagedTerminalStatus.STOPPED
        )
        private var activeResourceInstallWizard: ResourceInstallWizardContext? = null
    }
}

internal fun cardRunCommandHint(status: RecipeRunStatus, text: String): String? {
    if (status != RecipeRunStatus.Failed && status != RecipeRunStatus.BridgeUnavailable) return null
    val missingCommand = Regex("""(?:^|\n).*?:\s*([A-Za-z0-9_.+-]+): command not found""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
    val timeoutSignal = Regex(
        """(?i)(?:\btimed\s+out\b|\btimedOut\s*=\s*true\b|命令超时|(?:^|[\s:=])timeout(?:$|[\s.,;]))"""
    ).containsMatchIn(text)
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
        timeoutSignal ->
            "命令超时，可能还在等待输入、网络、服务启动，或者命令本身卡住了。"
        else -> null
    }
}
