package com.kite.app

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabsIntent
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.action.KiteResourceActionCoordinator
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.application.runs.RunExecutionEffect
import com.kite.app.application.runs.RunExecutionEffectBus
import com.kite.app.application.runs.RecipeActionEffect
import com.kite.app.application.runs.RecipeActionWorkflowCoordinator
import com.kite.app.application.runs.DesktopOpenCoordinator
import com.kite.app.application.runs.DesktopOpenRequest
import com.kite.app.application.runs.RuntimeOwnerProbeCoordinator
import com.kite.app.application.runs.RuntimeOwnerProbeRequest
import com.kite.app.application.browser.BrowserOpenCoordinator
import com.kite.app.application.browser.BrowserOpenRequest as BrowserOpenWorkflowRequest
import com.kite.app.application.browser.BrowserOpenResult
import com.kite.app.application.packages.InstallApkCoordinator
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.browser.automation.BrowserAutomationAction
import com.kite.app.browser.automation.BrowserAutomationActionResult
import com.kite.app.browser.automation.BrowserAutomationActionScript
import com.kite.app.browser.automation.BrowserAutomationController
import com.kite.app.browser.automation.BrowserAutomationControllerRegistry
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
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.run.CardRunState as RecipeRuntimeState
import com.kite.app.run.CardRunDesktopRouter
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunStatus as RecipeRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.shell.AppDestination
import com.kite.app.shell.AppIntentRouter
import com.kite.app.shell.AppNavigator
import com.kite.app.shell.KiteAppGraph
import com.kite.app.shell.NavigationBackAction
import com.kite.app.shell.RestorePolicy
import com.kite.app.shell.RunNotificationPermissionFragment
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.R
import com.kite.app.foundation.bootstrap.StartupTraceStore
import com.kite.app.foundation.runtime.RuntimeAutomationActions
import com.kite.app.foundation.runtime.RuntimeHealthStore
import com.kite.app.foundation.runtime.RuntimeReclaimer
import com.kite.app.application.runs.CardRunSpecialRecipes
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.foundation.runtime.TerminalSessionStore
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.feature.resources.ResourceFeatureRequest
import com.kite.app.feature.resources.ResourceFeatureResultContract
import com.kite.app.feature.resources.ResourceDetailFragment
import com.kite.app.feature.resources.ResourceManageFragment
import com.kite.app.feature.resources.ResourceMoreFragment
import com.kite.app.feature.resources.ResourceRawJsonFragment
import com.kite.app.feature.resources.ResourceSearchFragment
import com.kite.app.feature.resources.ResourcesFragment
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.browser.BrowserHandoffCoordinator
import com.kite.app.application.browser.BrowserHandoffLaunchResult
import com.kite.app.application.browser.BrowserAuthRedirectCoordinator
import com.kite.app.application.browser.BrowserAuthRedirectResult
import com.kite.app.application.resources.ResourceActionEffect
import com.kite.app.application.resources.ResourceActionWorkflowCoordinator
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapGateway
import com.kite.app.application.runtimebootstrap.RuntimePermissionKind
import com.kite.app.application.onboarding.FirstRunOnboardingCoordinator
import com.kite.app.application.onboarding.FirstRunOnboardingEffect
import com.kite.app.application.onboarding.FirstRunOnboardingFacts
import com.kite.app.application.onboarding.FirstRunOnboardingTransition
import com.kite.app.application.settings.SettingsGateway
import com.kite.app.application.surface.SurfaceChromeMode
import com.kite.app.feature.home.HomeFeatureRequest
import com.kite.app.feature.home.HomeFeatureResultContract
import com.kite.app.feature.home.HomeFragment
import com.kite.app.feature.runtimemanagement.RuntimeManagementFragment
import com.kite.app.feature.runtimemanagement.RuntimeManagementRequest
import com.kite.app.feature.runtimemanagement.RuntimeManagementResultContract
import com.kite.app.feature.runtimebootstrap.RuntimePermissionOnboardingUiInput
import com.kite.app.feature.runtimebootstrap.RuntimeStatusChrome
import com.kite.app.feature.runtimebootstrap.RuntimeStatusAction
import com.kite.app.feature.runtimebootstrap.RuntimeStatusFeatureController
import com.kite.app.feature.runtimebootstrap.RuntimeStatusFeatureEffect
import com.kite.app.feature.runtimebootstrap.RuntimeStatusUiState
import com.kite.app.feature.web.WebWorkbenchFragment
import com.kite.app.feature.web.WebWorkbenchTarget
import com.kite.app.feature.settings.SettingsFeatureRequest
import com.kite.app.feature.settings.SettingsFeatureResultContract
import com.kite.app.feature.settings.SettingsFragment
import com.kite.app.feature.settings.ThemeSettingsFragment
import com.kite.app.feature.recipeeditor.RecipeEditorDraft
import com.kite.app.feature.recipeeditor.RecipeEditorFragment
import com.kite.app.feature.recipeeditor.RecipeEditorRequest
import com.kite.app.feature.recipeeditor.RecipeEditorResultContract
import com.kite.app.feature.recipeeditor.RecipeRawJsonFragment
import com.kite.app.feature.runhistory.RunHistoryFragment
import com.kite.app.feature.runhistory.RunHistoryResultContract
import com.kite.app.ui.terminal.KiteTerminalShellTheme
import com.kite.app.ui.terminal.TerminalFragment
import com.kite.app.shell.TerminalSurfaceShellBinding
import com.kite.app.platform.runs.AndroidRunNotificationAccess
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

open class MainActivity : AppCompatActivity() {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var dropZoneManager: KiteDropZoneManager
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var browserAuthRedirectCoordinator: BrowserAuthRedirectCoordinator
    private lateinit var browserHandoffCoordinator: BrowserHandoffCoordinator
    private lateinit var browserAutomationSessions: BrowserAutomationSessionStore
    private lateinit var localServer: KiteLocalServer
    private lateinit var resourceFeatureGateway: ResourceFeatureGateway
    private lateinit var recipeFeatureGateway: RecipeFeatureGateway
    private lateinit var runExecutionEffectBus: RunExecutionEffectBus
    private lateinit var resourceActionWorkflowCoordinator: ResourceActionWorkflowCoordinator
    private lateinit var recipeActionWorkflowCoordinator: RecipeActionWorkflowCoordinator
    private lateinit var desktopOpenCoordinator: DesktopOpenCoordinator
    private lateinit var runtimeOwnerProbeCoordinator: RuntimeOwnerProbeCoordinator
    private lateinit var browserOpenCoordinator: BrowserOpenCoordinator
    private lateinit var installApkCoordinator: InstallApkCoordinator
    private lateinit var settingsGateway: SettingsGateway
    private lateinit var rootHost: FrameLayout
    private lateinit var root: LinearLayout

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

    /** 仅用于 Robolectric 路由合同断言。 */
    @androidx.annotation.VisibleForTesting
    internal fun currentScreenNameForTest(): String = currentScreen.name

    @androidx.annotation.VisibleForTesting
    internal fun enterScreenForTest(screenName: String) {
        val screen = AppDestination.valueOf(screenName)
        enterScreen(screen)
    }

    private fun enterScreen(screen: AppDestination, onBack: (() -> Unit)? = null) {
        currentScreen = screen
        appNavigator.enter(screen, onBack)
        if (::runtimeStatusChrome.isInitialized) {
            runtimeStatusChrome.render(runtimeStatusState, shouldSuppressTransientRuntimeChrome(runtimeStatusState))
        }
    }

    private var currentRecipes: List<KiteRecipe> = emptyList()
    private var dropZoneStatus: DropZoneStatus = DropZoneStatus(available = false, message = "投放区尚未检查")
    private var isDropZoneRefreshing = false
    private var themeConfig = ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
    private var tokens = KiteTheme.resolve(themeConfig)
    private var pendingRuntimePermissionBootstrap = false
    private var runtimePermissionRequestInFlight = false
    private lateinit var firstRunOnboardingCoordinator: FirstRunOnboardingCoordinator
    private lateinit var runtimeBootstrapGateway: RuntimeBootstrapGateway
    private lateinit var runtimeStatusController: RuntimeStatusFeatureController
    private lateinit var runtimeStatusChrome: RuntimeStatusChrome
    private var runtimeStatusState = RuntimeStatusUiState.checking()
    private var localServerStarted = false
    private var focusedRunInstanceId: String? = null
    private var currentResourceDetailId: String? = null
    private var lastWorkbenchUrl: String? = null
    private var consoleSystemStatusPillView: TextView? = null
    private var consoleRuntimeBannerHost: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTraceStore.markStage(this, "main.super_on_create")
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, navigationBackCallback)
        StartupTraceStore.markStage(this, "main.diagnostics_and_settings")
        val appGraph = KiteAppGraph.from(applicationContext)
        RunNotificationPermissionFragment.install(supportFragmentManager)
        resourceFeatureGateway = appGraph.resourceFeatureGateway
        recipeFeatureGateway = appGraph.recipeFeatureGateway
        diagnostics = appGraph.diagnostics
        diagnostics.writeCapabilityReport()
        settingsGateway = appGraph.settingsGateway
        CardRunStore.initialize(applicationContext)
        runExecutionEffectBus = appGraph.runExecutionEffectBus
        resourceActionWorkflowCoordinator = appGraph.resourceActionWorkflowCoordinator
        recipeActionWorkflowCoordinator = appGraph.recipeActionWorkflowCoordinator
        desktopOpenCoordinator = appGraph.desktopOpenCoordinator
        runtimeOwnerProbeCoordinator = appGraph.runtimeOwnerProbeCoordinator
        browserOpenCoordinator = appGraph.browserOpenCoordinator
        installApkCoordinator = appGraph.installApkCoordinator
        runtimeBootstrapGateway = appGraph.runtimeBootstrapGateway
        firstRunOnboardingCoordinator = appGraph.firstRunOnboardingCoordinator
        runtimeStatusController = RuntimeStatusFeatureController(
            bootstrapGateway = runtimeBootstrapGateway,
            managementGateway = appGraph.runtimeManagementGateway,
            scope = lifecycleScope
        )
        themeConfig = settingsGateway.currentSnapshot().let { snapshot ->
            ThemeConfig(snapshot.themeColor, snapshot.backgroundColor)
        }
        tokens = KiteTheme.resolve(themeConfig)
        applyKiteTerminalTheme()
        recipeLoader = appGraph.createRecipeLoader()
        dropZoneManager = appGraph.createDropZoneManager()
        dropZoneStatus = dropZoneManager.prepareDropZone()
        bridgeClient = appGraph.bridgeClient
        browserAuthRedirectCoordinator = appGraph.browserAuthRedirectCoordinator
        browserHandoffCoordinator = appGraph.createBrowserHandoffCoordinator(
            recipeResolver = { recipeId -> findRecipeById(recipeId) ?: CardRunStore.registeredRecipe(recipeId) },
            openExternal = { url -> openCustomTabOrSystemBrowser(Uri.parse(url)) }
        )
        StartupTraceStore.markStage(this, "main.webview_create")
        browserAutomationSessions = appGraph.browserAutomationSessions
        StartupTraceStore.markStage(this, "main.resources_and_server")
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
        runtimeStatusChrome = createRuntimeStatusChrome()
        registerResourceFeatureResults()
        registerHomeFeatureResults()
        registerRecipeEditorResults()
        registerRuntimeManagementResults()
        registerSettingsFeatureResults()
        registerTerminalSurfaceResults()
        registerRunHistoryResults()
        StartupTraceStore.markStage(this, "main.observers_and_intent")
        observeRuntimeStatus()
        observeRunExecutionEffects()
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
        runtimeStatusController.refresh()
        StartupTraceStore.markStage(this, "main.permissions_and_runtime_gate")
        if (!maybeStartFirstRunPermissionOnboarding()) {
            runtimeStatusController.ensureReady()
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
        val rawUrl = sourceIntent?.dataString?.takeIf(String::isNotBlank) ?: return false
        return when (val result = browserAuthRedirectCoordinator.handle(rawUrl)) {
            BrowserAuthRedirectResult.NotRedirect -> false
            BrowserAuthRedirectResult.Unmatched -> {
                Toast.makeText(this, "浏览器已返回，但没有匹配的登录会话", Toast.LENGTH_LONG).show()
                true
            }
            is BrowserAuthRedirectResult.MissingTarget -> {
                Toast.makeText(this, "浏览器已返回，但找不到发起登录的运行实例", Toast.LENGTH_LONG).show()
                true
            }
            is BrowserAuthRedirectResult.DeliveryFailed -> {
                Toast.makeText(this, "浏览器登录结果暂时无法交给运行实例", Toast.LENGTH_LONG).show()
                true
            }
            is BrowserAuthRedirectResult.Delivered -> {
                startActivity(
                    CardRunIntents.launchIntent(
                        context = this,
                        recipeId = result.recipeId,
                        instanceId = result.instanceId,
                        launchSource = CardRunIntents.SOURCE_BROWSER_PROXY,
                        autoStart = false
                    )
                )
                true
            }
        }
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
        intent.removeExtra(EXTRA_AUTOMATION_RUNTIME_ID)
        intent.removeExtra(CardRunIntents.EXTRA_RECIPE_ID)
        intent.removeExtra(CardRunIntents.EXTRA_INSTANCE_ID)
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
        submitRecipeAction(
            KiteRecipeActionRequest(
                recipe = recipe,
                intent = KiteRecipeActionIntent.Stop,
                source = KiteRecipeActionSource.Automation,
                instanceId = state.instanceId
            )
        )
    }

    private fun startResourceOwnerProbeFromAutomation(sourceIntent: Intent?) {
        val resourceId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_TARGET_ID)
            .orEmpty()
        val instanceId = sourceIntent
            ?.getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID)
        runtimeOwnerProbeCoordinator.start(
            RuntimeOwnerProbeRequest(
                resourceId = resourceId,
                instanceId = instanceId
            )
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
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_resource_open_start",
            null,
            mapOf("resourceId" to resourceId)
        )
        lifecycleScope.launch {
            applyResourceActionEffects(
                resourceActionWorkflowCoordinator.dispatch(
                    KiteResourceActionRequest(
                        resourceId,
                        KiteResourceActionIntent.Open,
                        KiteResourceActionSource.Automation
                    )
                )
            )
        }
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
        lifecycleScope.launch {
            applyResourceActionEffects(
                resourceActionWorkflowCoordinator.installDirect(resourceId)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        StartupTraceStore.markStage(this, "main.resume_permissions")
        applyRecentTaskVisibilitySetting()
        resumePendingFirstRunPermissionOnboarding()
        resumePendingRuntimePermissionBootstrap()
        StartupTraceStore.markStage(this, "main.resume_runtime_and_visible_state")
        runtimeStatusController.ensureReady()
        browserAuthRedirectCoordinator.reconcile()
        when (currentScreen) {
            AppDestination.Console -> resumeConsoleSurface()
            else -> Unit
        }
        rootHost.post { StartupTraceStore.markReady(applicationContext) }
    }

    override fun onPause() {
        firstRunOnboardingCoordinator.onHostPaused()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_SCREEN, currentScreen.name)
        val workbenchUrl = (supportFragmentManager.findFragmentByTag(TAG_WEB_WORKBENCH_FRAGMENT) as? WebWorkbenchFragment)
            ?.currentUrlForState()
            ?: lastWorkbenchUrl
        workbenchUrl?.let { outState.putString(STATE_WORKBENCH_URL, it) }
        (supportFragmentManager.findFragmentByTag(TAG_RECIPE_EDITOR_FRAGMENT) as? RecipeEditorFragment)
            ?.currentDraftRaw()
            ?.let { outState.putString(STATE_RECIPE_DRAFT, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::runtimeStatusChrome.isInitialized) runtimeStatusChrome.dispose()
        if (localServerStarted) {
            localServer.stop()
        }
        super.onDestroy()
    }

    private fun requestNavigationBack() {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun handleAppNavigationBack() {
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

    private fun observeRuntimeStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                runtimeStatusController.state.collect(::setRuntimeStatusState)
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
        if (focusedRunInstanceId == state.instanceId) {
            focusedRunInstanceId = null
        }
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

    private fun recipeForRunState(state: RecipeRuntimeState): KiteRecipe? =
        CardRunStore.registeredRecipe(state.recipeId)
            ?: currentRecipes.firstOrNull { it.id == state.recipeId }

    private fun maybeStartFirstRunPermissionOnboarding(): Boolean {
        val transition = firstRunOnboardingCoordinator.startOrRecover(currentFirstRunOnboardingFacts())
        applyFirstRunOnboardingTransition(transition)
        if (transition.state.active) runtimeStatusChrome.showPanel(auto = true)
        return transition.state.active
    }

    private fun continueFirstRunPermissionOnboarding() {
        applyFirstRunOnboardingTransition(
            firstRunOnboardingCoordinator.continueNow(currentFirstRunOnboardingFacts())
        )
    }

    private fun resumePendingFirstRunPermissionOnboarding() {
        applyFirstRunOnboardingTransition(
            firstRunOnboardingCoordinator.onHostResumed(currentFirstRunOnboardingFacts())
        )
    }

    private fun currentFirstRunOnboardingFacts(): FirstRunOnboardingFacts {
        val permissionSnapshot = runtimeBootstrapGateway.currentSnapshot().permissions
        val missing = permissionSnapshot.missing.toMutableSet()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += RuntimePermissionKind.Notifications
        }
        return FirstRunOnboardingFacts(
            missingRuntimePermissions = missing,
            needsAllFilesAccess = permissionSnapshot.needsAllFilesAccess,
            needsNotificationChannelSetup = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        )
    }

    private fun applyFirstRunOnboardingTransition(transition: FirstRunOnboardingTransition) {
        runtimeStatusController.updateOnboarding(
            RuntimePermissionOnboardingUiInput(
                active = transition.state.active,
                missingPermissions = transition.state.missingRuntimePermissions,
                needsAllFilesAccess = transition.state.needsAllFilesAccess,
                needsNotificationChannelSetup = transition.state.needsNotificationChannelSetup
            )
        )
        when (val effect = transition.effect) {
            is FirstRunOnboardingEffect.RequestRuntimePermissions -> {
                val permissions = effect.permissions.mapNotNull(::runtimePermissionString)
                if (permissions.isEmpty()) {
                    applyFirstRunOnboardingTransition(
                        firstRunOnboardingCoordinator.onRuntimePermissionResult(
                            currentFirstRunOnboardingFacts()
                        )
                    )
                } else {
                    requestPermissions(
                        permissions.toTypedArray(),
                        REQUEST_FIRST_RUN_PERMISSION_ONBOARDING
                    )
                }
            }
            FirstRunOnboardingEffect.OpenAllFilesSettings -> openAllFilesAccessSettings()
            FirstRunOnboardingEffect.OpenRunNotificationSettings -> openRunNotificationSettings()
            null -> if (!transition.state.active) runtimeStatusController.ensureReady()
        }
    }


    private fun requestFirstRunRuntimePermissions(startBootstrapAfterGrant: Boolean) {
        if (startBootstrapAfterGrant) {
            pendingRuntimePermissionBootstrap = true
        }
        val permissionState = runtimeBootstrapGateway.currentSnapshot().permissions
        runtimeStatusController.refresh()
        val runtimePermissions = permissionState.missing.mapNotNull(::runtimePermissionString)
        when {
            runtimePermissions.isNotEmpty() -> {
                if (!runtimePermissionRequestInFlight) {
                    runtimePermissionRequestInFlight = true
                    requestPermissions(
                        runtimePermissions.toTypedArray(),
                        REQUEST_FIRST_RUN_RUNTIME_PERMISSIONS
                    )
                }
            }
            permissionState.needsAllFilesAccess -> openAllFilesAccessSettings()
            startBootstrapAfterGrant -> resumePendingRuntimePermissionBootstrap()
        }
    }

    private fun requestRunNotificationSettings() {
        if (AndroidRunNotificationAccess.needsRuntimePermission(this)) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
            return
        }
        openRunNotificationSettings()
    }

    private fun openAppNotificationSettings() {
        runCatching { startActivity(AndroidRunNotificationAccess.appSettingsIntent(this)) }
            .onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
    }

    private fun openRunNotificationSettings() {
        runCatching { startActivity(AndroidRunNotificationAccess.runChannelSettingsIntent(this)) }
            .onFailure { openAppNotificationSettings() }
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
        val permissionState = runtimeBootstrapGateway.currentSnapshot().permissions
        runtimeStatusController.refresh()
        if (!permissionState.ready) {
            return
        }
        pendingRuntimePermissionBootstrap = false
        runtimeStatusController.ensureReady()
    }

    private fun runtimePermissionString(kind: RuntimePermissionKind): String? = when (kind) {
        RuntimePermissionKind.FileRead -> Manifest.permission.READ_EXTERNAL_STORAGE
        RuntimePermissionKind.FileWrite -> Manifest.permission.WRITE_EXTERNAL_STORAGE
        RuntimePermissionKind.Notifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null
    }

    private fun runtimePermissionLabels(
        missing: Set<RuntimePermissionKind>,
        needsAllFilesAccess: Boolean
    ): List<String> = buildList {
        if (needsAllFilesAccess) add("全部文件访问")
        missing.forEach { kind ->
            add(
                when (kind) {
                    RuntimePermissionKind.FileRead -> "文件读取"
                    RuntimePermissionKind.FileWrite -> "文件写入"
                    RuntimePermissionKind.Notifications -> "系统通知"
                }
            )
        }
    }.ifEmpty { listOf("文件访问") }


    private fun setRuntimeStatusState(state: RuntimeStatusUiState) {
        val previous = runtimeStatusState
        if (runtimeStatusState == state) return
        runtimeStatusState = state
        runtimeStatusChrome.render(state, shouldSuppressTransientRuntimeChrome(state))
        (supportFragmentManager.findFragmentByTag(TAG_RECIPE_EDITOR_FRAGMENT) as? RecipeEditorFragment)
            ?.updateRuntimeBlocked(state.blocksUbuntuActions)
        if (::root.isInitialized && currentScreen == AppDestination.Console && shouldRefreshConsoleForRuntimeState(previous, state)) {
            refreshConsoleRuntimeChrome()
        }
    }

    private fun createRuntimeStatusChrome(): RuntimeStatusChrome = RuntimeStatusChrome(
        activity = this,
        rootHost = rootHost,
        tokens = tokens,
        onRefresh = runtimeStatusController::refresh,
        onPrimaryAction = ::handleRuntimeStatusPrimaryAction
    )

    private fun handleRuntimeStatusPrimaryAction() {
        when (runtimeStatusController.submitPrimaryAction()) {
            RuntimeStatusFeatureEffect.ContinueFirstRunPermissionOnboarding ->
                continueFirstRunPermissionOnboarding()
            RuntimeStatusFeatureEffect.RequestRuntimePermissions ->
                requestFirstRunRuntimePermissions(startBootstrapAfterGrant = true)
            RuntimeStatusFeatureEffect.OpenAllFilesSettings -> {
                pendingRuntimePermissionBootstrap = true
                openAllFilesAccessSettings()
            }
            RuntimeStatusFeatureEffect.OpenProcessManagement -> {
                runtimeStatusChrome.dismissPanel()
                showKiteProcessOverview()
            }
            null -> runtimeStatusChrome.dismissPanel()
        }
    }

    private fun shouldRefreshConsoleForRuntimeState(previous: RuntimeStatusUiState, next: RuntimeStatusUiState): Boolean {
        if (previous.visible != next.visible) return true
        if (previous.title != next.title) return true
        if (previous.isProblem != next.isProblem) return true
        if (previous.blocksUbuntuActions != next.blocksUbuntuActions) return true
        if (previous.requiresPermission != next.requiresPermission) return true
        if (previous.primaryAction != next.primaryAction) return true
        if (previous.showProgress != next.showProgress) return true
        val previousPercent = previous.progressPercent
        val nextPercent = next.progressPercent
        if (previousPercent != null && nextPercent != null) {
            return kotlin.math.abs(nextPercent - previousPercent) >= 5
        }
        return previous.progressText.isBlank() != next.progressText.isBlank()
    }


    private fun applyThemeConfig(config: ThemeConfig) {
        if (themeConfig == config) return
        themeConfig = config
        tokens = KiteTheme.resolve(config)
        applyKiteTerminalTheme()
        if (::root.isInitialized) {
            root.setBackgroundColor(tokens.pageBackground)
            rootHost.setBackgroundColor(tokens.pageBackground)
            rebindBottomNavigationTheme()
        }
        if (::runtimeStatusChrome.isInitialized) {
            runtimeStatusChrome.dispose()
            runtimeStatusChrome = createRuntimeStatusChrome()
            runtimeStatusChrome.render(runtimeStatusState, shouldSuppressTransientRuntimeChrome(runtimeStatusState))
        }
    }

    private fun shouldHideMainTaskFromRecents(): Boolean =
        settingsGateway.currentSnapshot().hideMainTaskFromRecents

    private fun shouldRestoreLastScreen(): Boolean =
        settingsGateway.currentSnapshot().restoreLastScreen

    private fun applyRecentTaskVisibilitySetting() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !::settingsGateway.isInitialized) {
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
            (supportFragmentManager.findFragmentByTag(TAG_SETTINGS_FRAGMENT) as? SettingsFragment)
                ?.refresh()
        } else if (requestCode == REQUEST_FIRST_RUN_RUNTIME_PERMISSIONS) {
            runtimePermissionRequestInFlight = false
            dropZoneStatus = dropZoneManager.prepareDropZone()
            val permissionState = runtimeBootstrapGateway.currentSnapshot().permissions
            runtimeStatusController.refresh()
            if (!permissionState.ready) {
                if (permissionState.missing.isEmpty() && permissionState.needsAllFilesAccess) {
                    openAllFilesAccessSettings()
                } else {
                    Toast.makeText(
                        this,
                        "仍缺少：${runtimePermissionLabels(permissionState.missing, permissionState.needsAllFilesAccess).joinToString("、")}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                resumePendingRuntimePermissionBootstrap()
            }
            if (currentScreen == AppDestination.Console) showConsole()
        } else if (requestCode == REQUEST_FIRST_RUN_PERMISSION_ONBOARDING) {
            dropZoneStatus = dropZoneManager.prepareDropZone()
            applyFirstRunOnboardingTransition(
                firstRunOnboardingCoordinator.onRuntimePermissionResult(currentFirstRunOnboardingFacts())
            )
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (AndroidRunNotificationAccess.isAvailable(this)) {
                Toast.makeText(this, "通知已开启", Toast.LENGTH_SHORT).show()
                openRunNotificationSettings()
            } else {
                Toast.makeText(this, "通知未开启，可在设置中再次授权", Toast.LENGTH_SHORT).show()
            }
            (supportFragmentManager.findFragmentByTag(TAG_SETTINGS_FRAGMENT) as? SettingsFragment)
                ?.refresh()
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
                    isDropZoneRefreshing = false
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                    when (currentScreen) {
                        AppDestination.Console -> showConsole()
                        AppDestination.Settings ->
                            (supportFragmentManager.findFragmentByTag(TAG_SETTINGS_FRAGMENT) as? SettingsFragment)
                                ?.refresh()
                        else -> Unit
                    }
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
            runtimeStatusChrome.createInlineBanner(runtimeStatusState)?.let(::addView)
        })
        val content = FrameLayout(this).apply {
            id = R.id.kite_feature_content
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
        val existing = supportFragmentManager.findFragmentByTag(TAG_HOME_FRAGMENT) as? HomeFragment
        val fragment = existing ?: HomeFragment.newInstance(runtimeStatusState.blocksUbuntuActions)
        fragment.updateRuntimeBlocked(runtimeStatusState.blocksUbuntuActions)
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
        fragment.updateRuntimeBlocked(runtimeStatusState.blocksUbuntuActions)
        refreshConsoleRuntimeChrome()
    }

    private fun refreshConsoleRuntimeChrome() {
        consoleSystemStatusPillView?.let { runtimeStatusChrome.bindStatusPill(it, runtimeStatusState) }
        consoleRuntimeBannerHost?.apply {
            removeAllViews()
            runtimeStatusChrome.createInlineBanner(runtimeStatusState)?.let(::addView)
        }
        (supportFragmentManager.findFragmentByTag(TAG_HOME_FRAGMENT) as? HomeFragment)
            ?.updateRuntimeBlocked(runtimeStatusState.blocksUbuntuActions)
    }

    private fun showKiteProcessOverview(forceRefresh: Boolean = true) {
        enterScreen(AppDestination.Processes)
        showFeatureFragment(
            fragment = RuntimeManagementFragment.newInstance(forceRefresh),
            tag = TAG_RUNTIME_MANAGEMENT_FRAGMENT
        )
    }

    private fun showTerminal() {
        val currentTerminalFragment = supportFragmentManager.findFragmentByTag(TERMINAL_FRAGMENT_TAG) as? TerminalFragment
        if (currentScreen == AppDestination.Terminal && currentTerminalFragment?.isAdded == true) {
            applyKiteTerminalTheme()
            return
        }
        enterScreen(AppDestination.Terminal)
        applyKiteTerminalTheme()
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen(detachTerminal = false)
        val container = FrameLayout(this).apply {
            id = R.id.kite_feature_content
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())

        val fragment = currentTerminalFragment ?: TerminalFragment()
        supportFragmentManager.beginTransaction().apply {
            when {
                fragment.isDetached -> attach(fragment)
                fragment.isAdded -> show(fragment)
                else -> add(R.id.kite_feature_content, fragment, TERMINAL_FRAGMENT_TAG)
            }
        }.commitNowAllowingStateLoss()
    }

    private fun ensureKfRuntimeBootstrap() {
        val permissionState = runtimeBootstrapGateway.currentSnapshot().permissions
        if (!permissionState.ready) {
            runtimeStatusChrome.showPanel(auto = true)
            requestFirstRunRuntimePermissions(startBootstrapAfterGrant = true)
            return
        }
        runtimeStatusController.ensureReady()
    }

    private fun clearRootForScreen(detachTerminal: Boolean = true) {
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
        supportFragmentManager.findFragmentByTag(TAG_RESOURCE_MORE_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_RESOURCE_RAW_JSON_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_RUN_HISTORY_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_RUNTIME_MANAGEMENT_FRAGMENT)?.let { fragment ->
            transaction.remove(fragment)
            changed = true
        }
        supportFragmentManager.findFragmentByTag(TAG_WEB_WORKBENCH_FRAGMENT)?.let { fragment ->
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
        showFeatureFragment(SettingsFragment(), TAG_SETTINGS_FRAGMENT)
    }

    private fun browserRuntimeMode(): BrowserRuntimeMode =
        settingsGateway.currentSnapshot().browserRuntimeMode

    private fun showThemeSettings() {
        enterScreen(AppDestination.ThemeSettings)
        showFeatureFragment(ThemeSettingsFragment(), TAG_THEME_SETTINGS_FRAGMENT)
    }

    private fun showResources() {
        currentResourceDetailId = null
        enterScreen(AppDestination.Resources)
        runtimeStatusController.ensureReady()
        showFeatureFragment(ResourcesFragment(), TAG_RESOURCES_FRAGMENT)
    }

    private fun showFeatureFragment(
        fragment: Fragment,
        tag: String
    ) {
        rootHost.setBackgroundColor(tokens.pageBackground)
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        val content = FrameLayout(this).apply {
            id = R.id.kite_feature_content
            setBackgroundColor(tokens.pageBackground)
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (appNavigator.contract().showsPrimaryNavigation) {
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

    private fun registerRuntimeManagementResults() {
        supportFragmentManager.setFragmentResultListener(
            RuntimeManagementResultContract.REQUEST_KEY,
            this
        ) { _, bundle ->
            when (val request = RuntimeManagementResultContract.parse(bundle)) {
                RuntimeManagementRequest.Back -> requestNavigationBack()
                is RuntimeManagementRequest.OpenSurface -> {
                    val state = CardRunStore.get(request.instanceId)
                    if (state == null || state.recipeId != request.recipeId) {
                        Toast.makeText(this, "运行窗口不可用", Toast.LENGTH_SHORT).show()
                    } else {
                        CardRunStore.selectSurface(request.instanceId, request.surface)
                        startActivity(
                            CardRunIntents.launchIntent(
                                context = this,
                                recipeId = request.recipeId,
                                instanceId = request.instanceId,
                                launchSource = CardRunIntents.SOURCE_CARD,
                                autoStart = false
                            )
                        )
                    }
                }
                null -> Unit
            }
        }
    }

    private fun registerSettingsFeatureResults() {
        supportFragmentManager.setFragmentResultListener(
            SettingsFeatureResultContract.REQUEST_KEY,
            this
        ) { _, bundle ->
            when (val request = SettingsFeatureResultContract.parse(bundle)) {
                SettingsFeatureRequest.Back -> requestNavigationBack()
                SettingsFeatureRequest.OpenTheme -> showThemeSettings()
                is SettingsFeatureRequest.ApplyTheme -> applyThemeConfig(request.theme)
                SettingsFeatureRequest.ApplyRecentTaskVisibility -> applyRecentTaskVisibilitySetting()
                SettingsFeatureRequest.OpenNotificationSettings -> requestRunNotificationSettings()
                is SettingsFeatureRequest.OpenDropZone -> {
                    if (request.available) refreshDropZoneRecipes() else requestDropZoneAccess()
                }
                null -> Unit
            }
        }
    }

    private fun registerTerminalSurfaceResults() {
        TerminalSurfaceShellBinding.register(supportFragmentManager, this,
            onChromeMode = { mode ->
                if (currentScreen == AppDestination.Terminal) setMainSurfaceChromeMode(mode)
            },
            onBack = {
                if (currentScreen == AppDestination.Terminal) onBackPressedDispatcher.onBackPressed()
            })
    }

    private fun registerRunHistoryResults() {
        supportFragmentManager.setFragmentResultListener(
            RunHistoryResultContract.REQUEST_KEY,
            this
        ) { _, bundle ->
            if (RunHistoryResultContract.isBack(bundle)) requestNavigationBack()
        }
    }

    private fun setMainSurfaceChromeMode(mode: SurfaceChromeMode) {
        val navigation = (0 until root.childCount).map(root::getChildAt)
            .firstOrNull { it.tag == TAG_BOTTOM_NAVIGATION_VIEW } ?: return
        navigation.visibility = if (mode == SurfaceChromeMode.Immersive) View.GONE else View.VISIBLE
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
                RecipeEditorRequest.CloseRawJson -> requestNavigationBack()
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
                is ResourceFeatureRequest.OpenMore -> showResourceMore(request.resourceId)
                is ResourceFeatureRequest.OpenRawJson -> showResourceRawJson(request.resourceId)
                is ResourceFeatureRequest.CreateHomeCard -> lifecycleScope.launch {
                    applyResourceActionEffects(
                        resourceActionWorkflowCoordinator.createHomeCard(request.resourceId)
                    )
                }
                is ResourceFeatureRequest.OpenRunHistory -> showRunHistory(
                    recipeId = request.recipeId,
                    screen = AppDestination.ResourceMore,
                    backAction = { showResourceMore(request.resourceId) },
                    listTitle = "最近获取日志",
                    emptyTitle = "还没有获取日志",
                    emptyDetail = "资源获取或失败后，这里会保留对应资源自己的步骤和 SH 报告。",
                    initialHistoryId = request.historyId
                )
                is ResourceFeatureRequest.OpenInstallPlan -> lifecycleScope.launch {
                    applyResourceActionEffects(
                        resourceActionWorkflowCoordinator.dispatch(
                            KiteResourceActionRequest(
                                resourceId = request.targetResourceId,
                                intent = KiteResourceActionIntent.ReopenInstall,
                                source = KiteResourceActionSource.Wizard
                            )
                        )
                    )
                }
                is ResourceFeatureRequest.CancelInstallPlan -> lifecycleScope.launch {
                    applyResourceActionEffects(
                        resourceActionWorkflowCoordinator.cancelPlan(
                            request.targetResourceId,
                            request.resourceIds
                        )
                    )
                }
                is ResourceFeatureRequest.SubmitAction -> lifecycleScope.launch {
                    applyResourceActionEffects(
                        resourceActionWorkflowCoordinator.dispatch(request.request)
                    )
                }
                null -> Unit
            }
        }
    }

    private fun applyResourceActionEffects(effects: List<ResourceActionEffect>) {
        effects.forEach { effect ->
            when (effect) {
                is ResourceActionEffect.OpenRun -> startActivity(
                    CardRunIntents.launchIntent(
                        context = this,
                        recipeId = effect.recipeId,
                        instanceId = effect.instanceId,
                        launchSource = CardRunIntents.SOURCE_CARD,
                        autoStart = effect.autoStart
                    )
                )
                is ResourceActionEffect.OpenInstallWizard -> startActivity(
                    CardRunIntents.resourceInstallWizardIntent(
                        context = this,
                        recipeId = effect.recipeId,
                        instanceId = effect.instanceId,
                        targetResourceId = effect.targetResourceId,
                        planResourceIds = effect.planResourceIds
                    )
                )
                is ResourceActionEffect.Message -> showResourceDiscreteToast(effect.text)
                ResourceActionEffect.RequireNotifications ->
                    RunNotificationPermissionFragment.request(supportFragmentManager, "Kite 资源任务", "resource-start")
            }
        }
    }

    private fun showResourceDiscreteToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        if (currentScreen !in RESOURCE_STATUS_SCREENS) {
            Toast.makeText(this, message, length).show()
        }
    }

    private fun showResourceSearch(initialQuery: String = "") {
        enterScreen(AppDestination.ResourceSearch)
        currentResourceDetailId = null
        showFeatureFragment(
            ResourceSearchFragment.newInstance(initialQuery.trim()),
            TAG_RESOURCE_SEARCH_FRAGMENT
        )
    }

    private fun showResourceMore(resourceId: String) {
        enterScreen(AppDestination.ResourceMore) { showResourceDetail(resourceId) }
        showFeatureFragment(ResourceMoreFragment.newInstance(resourceId), TAG_RESOURCE_MORE_FRAGMENT)
    }

    private fun showResourceRawJson(resourceId: String) {
        enterScreen(AppDestination.ResourceRawJson) { showResourceDetail(resourceId) }
        showFeatureFragment(
            ResourceRawJsonFragment.newInstance(resourceId),
            TAG_RESOURCE_RAW_JSON_FRAGMENT
        )
    }

    private fun showBottomNavigationImmediately(nav: View) {
        nav.animate().cancel()
        nav.visibility = View.VISIBLE
        nav.translationY = 0f
        nav.alpha = 1f
    }

    private fun showResourceManage() {
        enterScreen(AppDestination.ResourceManage)
        showFeatureFragment(ResourceManageFragment(), TAG_RESOURCE_MANAGE_FRAGMENT)
    }

    private fun showResourceDetail(resourceId: String, onBack: (() -> Unit)? = null) {
        currentResourceDetailId = resourceId
        enterScreen(AppDestination.ResourceDetail, onBack)
        showFeatureFragment(
            ResourceDetailFragment.newInstance(resourceId),
            TAG_RESOURCE_DETAIL_FRAGMENT
        )
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
                    text = getString(R.string.console_subtitle)
                    textSize = 14f
                    setTextColor(tokens.textSecondary)
                })
            })
            addView(iconButton("⌕", dp(62), Color.TRANSPARENT, tokens.textPrimary, dp(18)) {
                Toast.makeText(context, getString(R.string.console_search_pending), Toast.LENGTH_SHORT).show()
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
        val openPanel = { runtimeStatusChrome.showPanel(auto = false, anchor = statusPill) }
        setOnClickListener { openPanel() }
        statusPill.setOnClickListener { openPanel() }
    }

    private fun systemStatusPill(): TextView = TextView(this).apply {
        runtimeStatusChrome.bindStatusPill(this, runtimeStatusState)
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
            ?: return BrowserAutomationActionScript.rejectedResult(
                action = action,
                sessionId = action.sessionId ?: action.instanceId ?: "display_not_available",
                errorCode = "display_not_available",
                detail = "自动浏览器显示面当前不可用"
            )
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

    private fun browserAutomationControllerFor(action: BrowserAutomationAction): BrowserAutomationController? {
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

    private fun closeCardRunInstanceForStop(recipe: KiteRecipe, previousState: RecipeRuntimeState, reason: String) {
        val instanceId = listOf(
            previousState.instanceId,
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


    private fun isUbuntuActionBlocked(recipe: KiteRecipe): Boolean =
        runtimeStatusState.blocksUbuntuActions && recipe.hasUbuntuStep()

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

    private fun shouldSuppressTransientRuntimeChrome(state: RuntimeStatusUiState = runtimeStatusState): Boolean =
        resourceSurfaceHasInlineRuntimeStatus() &&
            state.blocksUbuntuActions &&
            !state.requiresPermission &&
            !state.firstRunPermissionOnboarding &&
            !state.isProblem &&
            state.primaryAction != RuntimeStatusAction.RetryDeployment

    private fun submitRecipeAction(request: KiteRecipeActionRequest) {
        val effects = recipeActionWorkflowCoordinator.dispatch(
            request = request,
            runtimeBlocked = isUbuntuActionBlocked(request.recipe),
            focusedInstanceId = focusedRunInstanceId
        )
        if (RecipeActionEffect.RequireNotifications in effects) {
            RunNotificationPermissionFragment.request(
                fragmentManager = supportFragmentManager,
                title = request.recipe.name.ifBlank { "Kite 运行实例" },
                key = "start:${request.recipe.id}",
                retry = { submitRecipeAction(request) }
            )
        }
        applyRecipeActionEffects(effects)
    }

    private fun applyRecipeActionEffects(effects: List<RecipeActionEffect>) {
        effects.forEach { effect ->
            when (effect) {
                RecipeActionEffect.EnsureRuntime -> {
                    ensureKfRuntimeBootstrap()
                    if (!shouldSuppressTransientRuntimeChrome()) {
                        runtimeStatusChrome.showPanel(auto = true)
                        Toast.makeText(this, runtimeStatusState.title, Toast.LENGTH_SHORT).show()
                    }
                }
                is RecipeActionEffect.FocusRun -> focusedRunInstanceId = effect.instanceId
                is RecipeActionEffect.OpenRun -> startActivity(
                    CardRunIntents.launchIntent(
                        context = this,
                        recipeId = effect.recipeId,
                        instanceId = effect.instanceId,
                        launchSource = CardRunIntents.SOURCE_CARD,
                        autoStart = effect.autoStart
                    )
                )
                is RecipeActionEffect.CloseRunTask -> {
                    val state = CardRunStore.get(effect.instanceId)
                    val recipe = state
                        ?.takeIf { it.recipeId == effect.recipeId }
                        ?.let(::recipeForRunState)
                    if (state != null && recipe != null) {
                        closeCardRunInstanceForStop(recipe, state, "recipe_action_stop_accepted")
                    }
                }
                RecipeActionEffect.ShowConsole -> showConsole()
                is RecipeActionEffect.Message -> Toast.makeText(this, effect.text, Toast.LENGTH_SHORT).show()
                RecipeActionEffect.RequireNotifications -> Unit
            }
        }
    }





    private fun shouldOpenStepSurface(recipe: KiteRecipe, step: KiteRecipeStep): Boolean {
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
                runtimeBlocked = runtimeStatusState.blocksUbuntuActions
            )
        }
        fragment.updateRuntimeBlocked(runtimeStatusState.blocksUbuntuActions)
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
        enterScreen(AppDestination.RecipeDetail) { showRecipeEditor(recipe) }
        showFeatureFragment(
            RecipeRawJsonFragment.newInstance(recipe.id.ifBlank { recipe.name }, themeConfig),
            TAG_RECIPE_RAW_JSON_FRAGMENT
        )
    }

    private fun showRecipeRunHistory(recipe: KiteRecipe) {
        showRunHistory(
            recipeId = recipe.id,
            screen = AppDestination.RecipeMore,
            backAction = { showRecipeEditor(recipe) },
            listTitle = "运行历史",
            emptyTitle = "还没有运行记录",
            emptyDetail = "启动一次卡片后，这里会出现本次流程的时间、步骤和自动执行内容。"
        )
    }

    private fun showRunHistory(
        recipeId: String,
        screen: AppDestination,
        backAction: () -> Unit,
        listTitle: String,
        emptyTitle: String,
        emptyDetail: String,
        initialHistoryId: String? = null
    ) {
        enterScreen(screen, backAction)
        showFeatureFragment(
            RunHistoryFragment.newInstance(
                recipeId = recipeId,
                theme = themeConfig,
                listTitle = listTitle,
                emptyTitle = emptyTitle,
                emptyDetail = emptyDetail,
                initialHistoryId = initialHistoryId
            ),
            TAG_RUN_HISTORY_FRAGMENT
        )
    }

    private fun handleBrowserOpenRequest(request: KiteBrowserOpenRequest) {
        when (val result = browserOpenCoordinator.open(
            BrowserOpenWorkflowRequest(
                url = request.url,
                recipeId = request.recipeId,
                instanceId = request.instanceId,
                source = request.source
            )
        )) {
            BrowserOpenResult.Ignored,
            BrowserOpenResult.RoutedToExistingSurface,
            is BrowserOpenResult.RecordedForInstance -> Unit
            is BrowserOpenResult.OpenTemporaryRun -> openTemporaryBrowserRun(result)
            is BrowserOpenResult.OpenExternalBrowser -> {
                if (!openCustomTabOrSystemBrowser(Uri.parse(result.url))) {
                    Toast.makeText(this, "无法打开系统浏览器", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleDesktopOpenRequest(request: KiteDesktopOpenRequest): KiteDesktopOpenResponse {
        val result = desktopOpenCoordinator.open(
            DesktopOpenRequest(
                command = request.command,
                title = request.title,
                recipeId = request.recipeId,
                instanceId = request.instanceId,
                source = request.source
            )
        )
        if (result.openRunTask && result.recipeId != null && result.instanceId != null) {
            runOnUiThread {
                startActivity(
                    CardRunIntents.launchIntent(
                        context = this,
                        recipeId = result.recipeId,
                        instanceId = result.instanceId,
                        launchSource = CardRunIntents.SOURCE_CARD,
                        autoStart = false
                    )
                )
            }
        }
        if (result.accepted && result.instanceId != null) {
            CardRunDesktopRouter.dispatch(
                request.copy(
                    command = request.command.trim(),
                    recipeId = result.recipeId,
                    instanceId = result.instanceId
                )
            )
        }
        return KiteDesktopOpenResponse(
            accepted = result.accepted,
            recipeId = result.recipeId,
            instanceId = result.instanceId,
            display = result.display,
            socketPath = result.socketPath,
            error = result.error
        )
    }

    private fun handleInstallApkRequest(request: KiteInstallApkRequest): KiteInstallApkResponse {
        val result = installApkCoordinator.resolve(request.path)
        if (result.accepted) {
            runOnUiThread { openApkInstaller(File(result.resolvedPath)) }
        }
        return KiteInstallApkResponse(
            accepted = result.accepted,
            path = result.path,
            resolvedPath = result.resolvedPath,
            error = result.error
        )
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

    private fun openTemporaryBrowserRun(result: BrowserOpenResult.OpenTemporaryRun) {
        val intent = CardRunIntents.launchIntent(
            context = this,
            recipeId = result.recipeId,
            instanceId = result.instanceId,
            launchSource = result.source.ifBlank { CardRunIntents.SOURCE_BROWSER_PROXY },
            autoStart = false
        ).putExtra(CardRunIntents.EXTRA_TEMP_URL, result.url)
            .putExtra(CardRunIntents.EXTRA_TEMP_TITLE, result.title)
        val recipe = CardRunStore.registeredRecipe(result.recipeId)
        runCatching { startActivity(intent) }
            .onFailure { error ->
                diagnostics.logOpenWebFailed(recipe, result.url, error.message.orEmpty())
                Toast.makeText(this, "打开临时网页失败：${error.message}", Toast.LENGTH_SHORT).show()
                openWeb(result.url, result.source, recipe)
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
        showFeatureFragment(
            fragment = WebWorkbenchFragment.newInstance(
                target = WebWorkbenchTarget(
                    url = url,
                    source = source,
                    recipeId = recipe?.id,
                    recipeName = recipe?.name,
                    automationEnabled = browserRuntimeMode() == BrowserRuntimeMode.AutomationBrowser
                ),
                pageBackground = tokens.pageBackground,
                textPrimary = tokens.textPrimary
            ),
            tag = TAG_WEB_WORKBENCH_FRAGMENT
        )
    }

    private fun bottomNavigation(): View = row {
        tag = TAG_BOTTOM_NAVIGATION_VIEW
        setPadding(dp(14), dp(3), dp(14), dp(4))
        setBackgroundColor(Color.argb(
            238,
            Color.red(tokens.surfaceElevated),
            Color.green(tokens.surfaceElevated),
            Color.blue(tokens.surfaceElevated)
        ))
        elevation = dp(6).toFloat()
        addView(navItem("▦", getString(R.string.nav_cards), currentScreen == AppDestination.Console) { appNavigator.navigate(AppDestination.Console) })
        addView(navItem(">_", getString(R.string.nav_terminal), currentScreen == AppDestination.Terminal) { appNavigator.navigate(AppDestination.Terminal) })
        addView(navItem("≡", getString(R.string.nav_resources), currentScreen == AppDestination.Resources) { appNavigator.navigate(AppDestination.Resources) })
        addView(navItem("⚙", getString(R.string.nav_settings), currentScreen == AppDestination.Settings) { appNavigator.navigate(AppDestination.Settings) })
    }

    private fun rebindBottomNavigationTheme() {
        val index = (0 until root.childCount).firstOrNull { childIndex ->
            root.getChildAt(childIndex).tag == TAG_BOTTOM_NAVIGATION_VIEW
        } ?: return
        val replacement = bottomNavigation()
        root.removeViewAt(index)
        root.addView(replacement, index)
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

    private fun roundedBox(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = dp(1)): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TERMINAL_FRAGMENT_TAG = "kite-terminal"
        private const val TAG_RECIPE_RAW_JSON_FRAGMENT = "kite-recipe-raw-json"
        private const val TAG_RECIPE_EDITOR_FRAGMENT = "kite-recipe-editor"
        private const val TAG_RESOURCE_MANAGE_FRAGMENT = "kite-resource-manage"
        private const val TAG_RESOURCE_SEARCH_FRAGMENT = "kite-resource-search"
        private const val TAG_RESOURCES_FRAGMENT = "kite-resources"
        private const val TAG_HOME_FRAGMENT = "kite-home"
        private const val TAG_RESOURCE_DETAIL_FRAGMENT = "kite-resource-detail"
        private const val TAG_RESOURCE_MORE_FRAGMENT = "kite-resource-more"
        private const val TAG_RESOURCE_RAW_JSON_FRAGMENT = "kite-resource-raw-json"
        private const val TAG_RUN_HISTORY_FRAGMENT = "kite-run-history"
        private const val TAG_RUNTIME_MANAGEMENT_FRAGMENT = "kite-runtime-management"
        private const val TAG_WEB_WORKBENCH_FRAGMENT = "kite-web-workbench"
        private const val TAG_SETTINGS_FRAGMENT = "kite-settings"
        private const val TAG_THEME_SETTINGS_FRAGMENT = "kite-theme-settings"
        private const val TAG_BOTTOM_NAVIGATION_VIEW = "kite-bottom-navigation"
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
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private const val TERMINAL_STOP_GRACE_MS = 350L
        private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
        private const val REQUEST_DROPZONE_STORAGE = 801
        private const val REQUEST_FIRST_RUN_RUNTIME_PERMISSIONS = 803
        private const val REQUEST_NOTIFICATION_PERMISSION = 804
        private const val REQUEST_FIRST_RUN_PERMISSION_ONBOARDING = 805
        private const val STATE_CURRENT_SCREEN = "kite_current_screen"
        private const val STATE_WORKBENCH_URL = "kite_workbench_url"
        private const val STATE_RECIPE_DRAFT = "kite_recipe_draft"
        private const val RECIPE_DRAFT_RESTORE_WINDOW_MS = 6L * 60L * 60L * 1000L
        private val RESOURCE_STATUS_SCREENS = setOf(
            AppDestination.Resources,
            AppDestination.ResourceSearch,
            AppDestination.ResourceDetail,
            AppDestination.ResourceMore,
            AppDestination.ResourceManage
        )
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
