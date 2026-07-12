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
import android.view.View
import android.view.ViewGroup
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
import com.kite.app.feature.runsurface.CardRunSpecialRecipes
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.foundation.runtime.TerminalSessionItem
import com.kite.app.foundation.runtime.TerminalSessionStore
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.toolchain.ToolchainInstallPhase
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.feature.resources.ResourceFeatureRequest
import com.kite.app.feature.resources.ResourceFeatureResultContract
import com.kite.app.feature.resources.ResourceDetailFragment
import com.kite.app.feature.resources.ResourceManageFragment
import com.kite.app.feature.resources.ResourceSearchFragment
import com.kite.app.feature.resources.ResourcesFragment
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.browser.BrowserHandoffCoordinator
import com.kite.app.application.browser.BrowserHandoffLaunchResult
import com.kite.app.application.resources.ResourceRunContinuation
import com.kite.app.application.resources.ResourceRunCoordinator
import com.kite.app.application.resources.ResourceRunLaunchRequest
import com.kite.app.application.resources.ResourceRunLaunchResult
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
    private lateinit var browserHandoffCoordinator: BrowserHandoffCoordinator
    private lateinit var browserAutomationSessions: BrowserAutomationSessionStore
    private lateinit var browserAutomationController: BrowserAutomationController
    private lateinit var localServer: KiteLocalServer
    private lateinit var resourceInstallStore: KiteResourceInstallStore
    private lateinit var resourceManifestLoader: KiteResourceManifestLoader
    private lateinit var resourceFeatureGateway: ResourceFeatureGateway
    private lateinit var recipeFeatureGateway: RecipeFeatureGateway
    private lateinit var runOrchestrator: RunOrchestrator
    private lateinit var runExecutionEffectBus: RunExecutionEffectBus
    private lateinit var resourceRunCoordinator: ResourceRunCoordinator
    private lateinit var themeStore: SharedPreferences
    private lateinit var appSettings: SharedPreferences
    private lateinit var rootHost: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView
    private var activityDisplaySurfacesReleased = false

    private val runtimeStates = mutableMapOf<String, RecipeRuntimeState>()
    private val activeRunInstanceIds = mutableMapOf<String, String>()
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
    private var localServerStarted = false
    private var focusedRunRecipeId: String? = null
    private var focusedRunInstanceId: String? = null
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
        resourceRunCoordinator = appGraph.resourceRunCoordinator
        themeConfig = loadThemeConfig()
        tokens = KiteTheme.resolve(themeConfig)
        applyKiteTerminalTheme()
        recipeLoader = appGraph.createRecipeLoader()
        dropZoneManager = appGraph.createDropZoneManager()
        dropZoneStatus = dropZoneManager.prepareDropZone()
        bridgeClient = appGraph.bridgeClient
        browserAuthSessions = appGraph.browserAuthSessions
        browserLoopbackCallbackBridge = appGraph.browserLoopbackCallbackBridge
        browserHandoffCoordinator = appGraph.createBrowserHandoffCoordinator(
            recipeResolver = { recipeId -> findRecipeById(recipeId) ?: CardRunStore.registeredRecipe(recipeId) },
            openExternal = { url -> openCustomTabOrSystemBrowser(Uri.parse(url)) }
        )
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
        observeRunExecutionEffects()
        observeCardRunStoreSignals()
        observeRuntimePanelSummarySignals()
        observeResourceInstallSignals()
        applyRecentTaskVisibilitySetting()
        val handledLaunchIntent = AppIntentRouter.dispatch(
            intent,
            ::handleBrowserAuthRedirect,
            ::handleRuntimeAutomationIntent,
            ::forwardCardRunIntent
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
            ::forwardCardRunIntent
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
        when (currentScreen) {
            AppDestination.Console -> resumeConsoleSurface()
            AppDestination.Settings -> showSettings()
            else -> Unit
        }
        rootHost.post { StartupTraceStore.markReady(applicationContext) }
    }

    override fun onPause() {
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
        releaseActivityDisplaySurfaces()
        if (localServerStarted) {
            localServer.stop()
        }
        super.onDestroy()
    }

    private fun releaseActivityDisplaySurfaces() {
        if (activityDisplaySurfacesReleased) return
        activityDisplaySurfacesReleased = true
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
        when (val action = appNavigator.resolveBack()) {
            NavigationBackAction.System -> dispatchSystemBack()
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
        if (currentScreen != AppDestination.Workbench) return false
        if (webView.parent == null || !webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    override fun onPostResume() {
        super.onPostResume()
    }

    private fun forwardCardRunIntent(sourceIntent: Intent?): Boolean {
        val source = sourceIntent ?: return false
        val recipeId = source.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID)?.takeIf { it.isNotBlank() }
            ?: return false
        val forwarded = Intent(source)
            .setClass(this, CardRunActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        return runCatching {
            startActivity(forwarded)
            true
        }.getOrElse { error ->
            diagnostics.logRecipeEvent(
                "card_run_forward_failed",
                null,
                mapOf("recipeId" to recipeId, "error" to error.message.orEmpty().take(500))
            )
            Toast.makeText(this, "运行窗口打开失败：${error.message}", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun temporaryBrowserRecipe(recipeId: String, url: String, title: String = "临时网页"): KiteRecipe =
        CardRunSpecialRecipes.temporaryBrowser(recipeId, url, title)

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
        openWeb(effect.url, "recipe_orchestrator", recipe)
    }

    private fun observeCardRunStoreSignals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CardRunStore.runs.collect { runs ->
                    consumeResourceOpenRunSignals(runs)
                    renderRuntimePanelCounts()
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
        syncVisibleResourceState(signal.reason)
    }

    private fun syncVisibleResourceState(@Suppress("UNUSED_PARAMETER") reason: String) {
        // 每个 Activity 都有自己的资源缓存。即使当前不在资源页，也要先标脏，
        // 避免稍后进入资源页时继续显示状态变化前的按钮文字。
        invalidateResourceRuntimeStateCache()
        when (currentScreen) {
            AppDestination.Resources,
            AppDestination.ResourceSearch -> Unit
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
        if (!::root.isInitialized) return
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
        if (!::root.isInitialized || !::resourceInstallStore.isInitialized) return
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
        if (firstRunRuntimeGateShown) return
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
        if (bootstrapResourceGateInFlight) return
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !::appSettings.isInitialized) {
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
        continuation: ResourceUninstallContinuation = ResourceUninstallContinuation.None
    ) {
        val recipe = resourceUninstallRecipe(item)
        if (recipe == null) {
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
                settleVisibleResourceMutation("cancel_failed_install")
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
            showResources()
        } else {
            refreshResourceScreenIfVisible()
        }
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
        if (removeRunState && wizardRecipeIds.isNotEmpty()) {
            wizardRecipeIds.forEach { recipeId ->
                runtimeStates.remove(recipeId)
                activeRunInstanceIds.remove(recipeId)
                suppressedResourceRunSurfaceRecipeIds.remove(recipeId)
            }
            CardRunStore.removeRunStatesForRecipes(wizardRecipeIds, removeOpenHistory = true)
        }
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
        CardRunSpecialRecipes.installWizard(targetResourceId, targetName)

    private fun resourceInstallWizardInstanceId(targetResourceId: String): String =
        "resource-install-wizard-${KiteResourceInstallRecipes.safeId(targetResourceId)}-${UUID.randomUUID().toString().replace("-", "")}"

    private fun resourceRunInstanceId(resourceId: String, recipe: KiteRecipe): String =
        "resource-run-${KiteResourceInstallRecipes.safeId(resourceId)}-${KiteResourceInstallRecipes.safeId(recipe.id)}-${UUID.randomUUID().toString().replace("-", "")}"
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
                    openCardRunTask(recipe)
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




    private fun resourceInstallRecipe(item: ResourceItem): KiteRecipe? =
        resourceRunCoordinator.recipe(item.id, KiteResourceInstallRecipes.OP_INSTALL)


    private fun resourceUninstallRecipe(item: ResourceItem): KiteRecipe? =
        resourceRunCoordinator.recipe(item.id, KiteResourceInstallRecipes.OP_UNINSTALL)



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
            resourceInstallStore.markFailed(
                runningId,
                KiteResourceInstallStore.OP_INSTALL,
                null,
                "获取流程异常中断"
            )
            resourceInstallStore.failPlanAt(runningId)
            invalidateResourceRuntimeStateCache()
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


    private fun startResourceInstall(item: ResourceItem, recipe: KiteRecipe) {
        invalidateResourceRuntimeStateCache()
        startResourceRun(
            item = item,
            recipe = recipe,
            stageBundledResource = resourceRunCoordinator.isBundled(item.id),
            openRunTask = true,
            returnToInstallWizard = false
        )
    }

    private fun startResourceUninstall(
        item: ResourceItem,
        recipe: KiteRecipe,
        continuation: ResourceUninstallContinuation = ResourceUninstallContinuation.None
    ) {
        invalidateResourceRuntimeStateCache()
        startResourceRun(
            item = item,
            recipe = recipe,
            stageBundledResource = false,
            openRunTask = false,
            continuation = continuation
        )
    }

    private fun startResourceRun(
        item: ResourceItem,
        recipe: KiteRecipe,
        stageBundledResource: Boolean,
        openRunTask: Boolean = true,
        returnToInstallWizard: Boolean = false,
        continuation: ResourceUninstallContinuation = ResourceUninstallContinuation.None
    ) {
        val parentInstanceId = activeResourceInstallWizard
            ?.wizardInstanceId
            ?.takeIf { returnToInstallWizard }
        val instanceId = resourceRunInstanceId(item.id, recipe)
        if (openRunTask && !returnToInstallWizard) {
            suppressedResourceRunSurfaceRecipeIds.remove(recipe.id)
        } else {
            suppressedResourceRunSurfaceRecipeIds.add(recipe.id)
        }
        val result = resourceRunCoordinator.start(
            ResourceRunLaunchRequest(
                resourceId = item.id,
                recipe = recipe,
                operation = resourceOperationForRecipe(recipe) ?: KiteResourceInstallRecipes.OP_INSTALL,
                stageBundledResource = stageBundledResource,
                parentInstanceId = parentInstanceId,
                preferredInstanceId = instanceId,
                continuation = continuation.toResourceRunContinuation()
            )
        )
        val state = when (result) {
            is ResourceRunLaunchResult.Accepted -> result.state
            is ResourceRunLaunchResult.Rejected -> {
                showResourceDiscreteToast("资源运行未启动：${result.reason}")
                return
            }
        }
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        if (openRunTask && !returnToInstallWizard) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = state.instanceId
        }
        when {
            returnToInstallWizard -> showResourceInstallWizard()
            !openRunTask -> refreshResourceScreenIfVisible()
            else -> startActivity(
                CardRunIntents.launchIntent(
                    context = this,
                    recipeId = recipe.id,
                    instanceId = state.instanceId,
                    launchSource = CardRunIntents.SOURCE_CARD,
                    autoStart = false
                )
            )
        }
    }

    private fun ResourceUninstallContinuation.toResourceRunContinuation(): ResourceRunContinuation = when (this) {
        ResourceUninstallContinuation.None -> ResourceRunContinuation.None
        ResourceUninstallContinuation.Reinstall -> ResourceRunContinuation.Reinstall
        ResourceUninstallContinuation.CancelFailedInstall -> ResourceRunContinuation.CancelFailedInstall
        ResourceUninstallContinuation.ResumeInstallWizard -> ResourceRunContinuation.ResumeInstallWizard
    }

    private fun refreshResourceScreenIfVisible() {
        if (!::root.isInitialized || !::resourceInstallStore.isInitialized) return
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
        if (!::root.isInitialized) return
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

    /** 主壳只打开既有运行实例；显示面装配全部属于 CardRunActivity。 */
    private fun openCardRunTask(recipe: KiteRecipe) {
        val state = focusedRunInstanceId
            ?.let { CardRunStore.get(it) }
            ?: CardRunStore.currentForRecipe(recipe.id)
            ?: return
        val launchIntent = if (state.surface == CardRunSurface.InstallWizard) {
            val targetId = state.stepId
                ?.takeIf { it.isNotBlank() }
                ?: activeResourceInstallWizard?.targetResourceId
                ?: currentResourceInstallTargetId
                ?: return
            val planIds = activeResourceInstallWizard?.planResourceIds.orEmpty()
                .ifEmpty { resourceInstallWizardPlanIds }
                .ifEmpty { resourceInstallStore.planResourceIds() }
                .ifEmpty { resourceInstallStore.pendingPlanResourceIds() }
            if (planIds.isEmpty()) return
            CardRunIntents.resourceInstallWizardIntent(
                context = this,
                recipeId = recipe.id,
                instanceId = state.instanceId,
                targetResourceId = targetId,
                planResourceIds = planIds
            )
        } else {
            CardRunIntents.launchIntent(
                context = this,
                recipeId = recipe.id,
                instanceId = state.instanceId,
                launchSource = CardRunIntents.SOURCE_CARD,
                autoStart = false
            )
        }
        startActivity(launchIntent)
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

    private fun launchBrowserHandoff(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision
    ): Boolean =
        launchBrowserHandoff(request, decision, force = false, rerenderFocusedSurface = true)

    private fun launchBrowserHandoff(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision,
        force: Boolean = false,
        @Suppress("UNUSED_PARAMETER") rerenderFocusedSurface: Boolean = true
    ): Boolean {
        val result = browserHandoffCoordinator.launch(request, decision, force)
        result.targetUpdate?.let { update ->
            activeRunInstanceIds[update.recipe.id] = update.state.instanceId
            runtimeStates[update.recipe.id] = update.state
        }
        if (result is BrowserHandoffLaunchResult.Failed) {
            Toast.makeText(this, "无法打开安全浏览器", Toast.LENGTH_LONG).show()
        }
        return result.accepted
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

    private data class CardRunWindowItem(
        val key: String,
        val instanceId: String,
        val surface: CardRunSurface,
        val title: String,
        val caption: String
    )

    private fun cardRunWindowAllItems(recipe: KiteRecipe, state: RecipeRuntimeState): List<CardRunWindowItem> {
        val rootState = cardRunWindowRootState(state)
        val items = mutableListOf<CardRunWindowItem>()
        fun addItem(
            owner: RecipeRuntimeState,
            surface: CardRunSurface,
            title: String,
            caption: String
        ) {
            items += CardRunWindowItem(
                key = cardRunWindowItemKey(owner.instanceId, surface),
                instanceId = owner.instanceId,
                surface = surface,
                title = title,
                caption = caption
            )
        }
        if (cardRunReportSurfaceAvailable(recipe, rootState)) {
            addItem(rootState, CardRunSurface.Report, "SH 报告", "执行输出")
        }
        if (!rootState.terminalSessionId.isNullOrBlank()) {
            addItem(rootState, CardRunSurface.Terminal, "终端", "终端窗口")
        }
        if (!rootState.nextActionUrl.isNullOrBlank() || rootState.surface == CardRunSurface.Web) {
            addItem(
                rootState,
                CardRunSurface.Web,
                cardRunWindowWebTitle(rootState.nextActionUrl),
                cardRunWindowWebCaption(rootState.nextActionUrl)
            )
        }
        rootState.x11Display?.takeIf { it.isNotBlank() }?.let { display ->
            addItem(rootState, CardRunSurface.X11, "X11", "DISPLAY=$display")
        }
        CardRunStore.childrenOf(rootState.instanceId)
            .sortedBy { it.createdAt }
            .forEach { child ->
                if (!child.terminalSessionId.isNullOrBlank()) {
                    addItem(child, CardRunSurface.Terminal, "终端", "终端窗口")
                } else if (!child.nextActionUrl.isNullOrBlank() || child.surface == CardRunSurface.Web) {
                    addItem(
                        child,
                        CardRunSurface.Web,
                        cardRunWindowWebTitle(child.nextActionUrl),
                        cardRunWindowWebCaption(child.nextActionUrl)
                    )
                } else if (!child.x11Display.isNullOrBlank()) {
                    addItem(child, CardRunSurface.X11, "X11", "DISPLAY=${child.x11Display}")
                }
            }
        if (items.isEmpty() && rootState.instanceId.isNotBlank()) {
            addItem(
                rootState,
                rootState.surface,
                runManagementSurfaceTitle(rootState.surface),
                rootState.status.label
            )
        }
        return items
    }

    private fun cardRunWindowRootState(state: RecipeRuntimeState): RecipeRuntimeState =
        state.parentInstanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { CardRunStore.get(it) }
            ?: state

    private fun cardRunWindowItemKey(instanceId: String, surface: CardRunSurface): String =
        "$instanceId:${surface.name}"

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

    private fun runManagementSurfaceTitle(surface: CardRunSurface): String =
        when (surface) {
            CardRunSurface.Web -> "网页"
            CardRunSurface.Terminal -> "终端"
            CardRunSurface.X11 -> "X11"
            CardRunSurface.InstallWizard -> "获取向导"
            CardRunSurface.Report,
            CardRunSurface.Summary -> "运行报告"
        }

    private fun cardRunReportSurfaceAvailable(recipe: KiteRecipe, state: RecipeRuntimeState): Boolean {
        val hasShellStep = recipe.steps.any { step ->
            step.type == KiteRecipe.STEP_SHELL || step.type == KiteRecipe.STEP_ANDROID_ACTION
        }
        return !state.shellReportText.isNullOrBlank() ||
            (hasShellStep && (state.hasRunBinding() || state.isBusy() || !state.lastMeaningfulOutput.isNullOrBlank() || !state.lastError.isNullOrBlank())) ||
            (state.surface == CardRunSurface.Report && (!state.lastMeaningfulOutput.isNullOrBlank() || !state.lastError.isNullOrBlank()))
    }

    private fun selectCardRunWindowItem(recipe: KiteRecipe, item: CardRunWindowItem) {
        val target = CardRunStore.get(item.instanceId) ?: return
        val root = cardRunWindowRootState(target)
        activeRunInstanceIds[recipe.id] = root.instanceId
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = item.instanceId
        CardRunStore.selectSurface(item.instanceId, item.surface)?.let { state ->
            runtimeStates[recipe.id] = state
        }
        val targetRecipe = recipeForRunState(target) ?: recipe
        startActivity(
            CardRunIntents.launchIntent(
                context = this,
                recipeId = targetRecipe.id,
                instanceId = target.instanceId,
                launchSource = CardRunIntents.SOURCE_CARD,
                autoStart = false
            )
        )
    }

    private fun closeCardRunInstanceForStop(recipe: KiteRecipe, previousState: RecipeRuntimeState, reason: String) {
        val instanceId = listOf(
            previousState.instanceId,
            activeRunInstanceIds[recipe.id],
            focusedRunInstanceId?.takeIf { CardRunStore.get(it)?.recipeId == recipe.id },
            CardRunStore.currentForRecipe(recipe.id)?.instanceId,
            recipe.id
        ).firstOrNull { !it.isNullOrBlank() } ?: return

        val closedLiveInstance = CardRunTaskCloser.close(instanceId)
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
        return when (currentScreen) {
            AppDestination.Resources,
            AppDestination.ResourceSearch,
            AppDestination.ResourceDetail,
            AppDestination.ResourceMore,
            AppDestination.ResourceManage -> true
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
        @Suppress("UNUSED_PARAMETER") recipe: KiteRecipe,
        @Suppress("UNUSED_PARAMETER") reason: String
    ) {
        invalidateResourceRuntimeStateCache()
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
            is KiteActionRoute.Unsupported -> {
                setRuntimeState(recipe, RecipeRunStatus.Failed, lastError = route.reason)
                Toast.makeText(this, route.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shouldOpenCardRunTaskFromHome(recipe: KiteRecipe): Boolean =
        recipe.launch.openInstance

    private fun handleRecipeAction(recipe: KiteRecipe) = handleRecipeActionWithRouter(recipe)

    private fun startRecipe(
        recipe: KiteRecipe,
        previousState: RecipeRuntimeState,
        preferredInstanceId: String? = null,
        openConsoleOnStart: Boolean = true,
        renderOnStart: Boolean = true,
        keepCurrentFocus: Boolean = false
    ) {
        startRecipeWithOrchestrator(
            recipe = recipe,
            previousState = previousState,
            preferredInstanceId = preferredInstanceId,
            openConsoleOnStart = openConsoleOnStart,
            renderOnStart = renderOnStart,
            keepCurrentFocus = keepCurrentFocus
        )
    }

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
        if (!renderOnStart) {
            if (!keepCurrentFocus) {
                focusedRunRecipeId = recipe.id
                focusedRunInstanceId = instanceId
            }
        } else if (openConsoleOnStart) {
            showConsole()
        } else {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = instanceId
            openCardRunTask(recipe)
        }
    }





    private fun showRunSurfaceOrConsole(recipe: KiteRecipe) {
        if (resourceRunSurfaceSuppressed(recipe)) {
            invalidateResourceRuntimeStateCache()
        } else if (currentScreen == AppDestination.CreateConfig || currentScreen == AppDestination.RecipeMore) {
            focusedRunRecipeId = recipe.id
            focusedRunInstanceId = activeRunInstanceIds[recipe.id] ?: focusedRunInstanceId
        } else {
            showConsole()
        }
    }

    private fun resourceRunSurfaceSuppressed(recipe: KiteRecipe): Boolean =
        recipe.id in suppressedResourceRunSurfaceRecipeIds



    private fun shouldOpenStepSurface(recipe: KiteRecipe, step: KiteRecipeStep): Boolean {
        if (resourceRunSurfaceSuppressed(recipe)) return false
        return when (KiteRecipe.normalizeSurfaceMode(step.surfaceMode)) {
            KiteRecipe.SURFACE_MODE_PANEL -> true
            KiteRecipe.SURFACE_MODE_SILENT -> false
            else -> {
                val mayAutoOpenSurface = recipe.launch.openInstance
                mayAutoOpenSurface && (
                    step.type == KiteRecipe.STEP_OPEN_WEB ||
                        step.type == KiteRecipe.STEP_TERMINAL ||
                        step.type == KiteRecipe.STEP_X11 ||
                        step.type == KiteRecipe.STEP_SHELL
                    )
            }
        }
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
        stopRecipeWithOrchestrator(recipe, previousState, navigateToConsole)
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
        if (focusedRunInstanceId == instanceId) {
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

    private fun String.normalizeShellStreamForDisplay(): String =
        replace(ANSI_ESCAPE_REGEX, "")
            .replace('\r', '\n')
            .lineSequence()
            .joinToString("\n") { it.trimEnd() }
            .trimEnd()

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
            runOnUiThread { runtimeStates[recipe.id] = failedState }
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
        openCardRunTask(recipe)
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
        openCardRunTask(recipe)
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
        root.addView(topBar("Kite 工作台", ::requestNavigationBack))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
        private const val TAG_RECIPE_RAW_JSON_FRAGMENT = "kite-recipe-raw-json"
        private const val TAG_RECIPE_EDITOR_FRAGMENT = "kite-recipe-editor"
        private const val TAG_RESOURCE_MANAGE_FRAGMENT = "kite-resource-manage"
        private const val TAG_RESOURCE_SEARCH_FRAGMENT = "kite-resource-search"
        private const val TAG_RESOURCES_FRAGMENT = "kite-resources"
        private const val TAG_HOME_FRAGMENT = "kite-home"
        private const val TAG_RESOURCE_DETAIL_FRAGMENT = "kite-resource-detail"
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
        private const val RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE =
            CardRunSpecialRecipes.RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE
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
        private const val TERMINAL_STOP_GRACE_MS = 350L
        private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
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
