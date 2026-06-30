package com.kite.app

import android.animation.ValueAnimator
import android.animation.LayoutTransition
import android.app.ActivityManager
import android.app.Dialog
import android.app.NotificationManager
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
import android.text.method.ScrollingMovementMethod
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
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
import android.view.WindowManager
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
import com.kite.app.bridge.KiteDesktopOpenRequest
import com.kite.app.bridge.KiteDesktopOpenResponse
import com.kite.app.bridge.KiteInstallApkRequest
import com.kite.app.bridge.KiteInstallApkResponse
import com.kite.app.bridge.KiteLocalServer
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.dropzone.DropZoneStatus
import com.kite.app.dropzone.KiteDropZoneManager
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteCardGroupStore
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
import com.kite.app.resources.KiteResourceDisplayRowSpec
import com.kite.app.resources.KiteResourceHomeSection
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceHomeTab
import com.kite.app.resources.KiteResourcePreviewSpec
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRequestPolicy
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.resources.KiteResourceShellAction
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
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.ThemeTokens
import com.kite.app.web.KiteWebShell
import com.google.android.material.tabs.TabLayout
import com.kite.app.R
import com.kite.app.foundation.bootstrap.BootstrapCoordinator
import com.kite.app.foundation.bootstrap.BootstrapSnapshot
import com.kite.app.foundation.bootstrap.BootstrapStage
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
import com.kite.app.ui.terminal.KiteTerminalShellTheme
import com.kite.app.ui.terminal.TerminalChromeHost
import com.kite.app.ui.terminal.TerminalFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

open class MainActivity : AppCompatActivity(), TerminalChromeHost,
    RecipeRawJsonFragment.RecipeProvider,
    RecipeRawJsonFragment.RecipeRawJsonHost,
    RecipeRawJsonFragment.UiKitProvider,
    ResourceManageFragment.ResourceManageHost,
    ResourceSearchFragment.ResourceSearchHost,
    ResourcesFragment.ResourcesHost,
    ResourceDetailFragment.ResourceDetailHost {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var dropZoneManager: KiteDropZoneManager
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var webShell: KiteWebShell
    private lateinit var localServer: KiteLocalServer
    private lateinit var resourceInstallStore: KiteResourceInstallStore
    private lateinit var resourceManifestLoader: KiteResourceManifestLoader
    private lateinit var themeStore: SharedPreferences
    private lateinit var appSettings: SharedPreferences
    private lateinit var rootHost: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView

    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var workdirInput: EditText
    private lateinit var launchInstanceSwitch: Switch
    private lateinit var commandFieldContainer: View
    private lateinit var urlFieldContainer: View
    private lateinit var workdirFieldContainer: View
    private lateinit var typeContainer: LinearLayout
    private lateinit var iconContainer: LinearLayout
    private lateinit var stepsContainer: LinearLayout

    private val runtimeStates = mutableMapOf<String, RecipeRuntimeState>()
    private val activeRunInstanceIds = mutableMapOf<String, String>()
    private val cardRunWindowHiddenSurfaces = mutableMapOf<String, MutableSet<String>>()
    private val actionRouter = KiteActionRouter()
    /**
     * Screen 路由收口(T6)。过渡期把 navigate 委托回老的 show* 方法;
     * 后续各 Screen 逐个 Fragment 化时,在此替换为 routeToFragment。
     */
    private val screenRouter: ScreenRouter by lazy {
        ScreenRouter(this) { screen -> dispatchLegacyScreen(screen) }
    }
    private val cardGroupStore by lazy { KiteCardGroupStore(applicationContext) }
    private var currentScreen: Screen = Screen.Console
    private var pendingRawJsonRecipeId: String? = null
    private var pendingResourceDetailInitialItem: ResourceItem? = null
    private var pendingResourceDetailRequestId: Long = 0L
    private var pendingResourceDetailRequestKey: String? = null

    /**
     * 仅用于单元测试:暴露当前 Screen 的枚举名(字符串),供 Robolectric 路由测试断言。
     * 用字符串而非 Screen 类型,避免把 private nested enum 改成 internal。
     * 生产代码不应调用。
     */
    @androidx.annotation.VisibleForTesting
    internal fun currentScreenNameForTest(): String = currentScreen.name

    private var currentRecipes: List<KiteRecipe> = emptyList()
    private var consolePageId: String = CONSOLE_PAGE_ALL
    private var consolePageTabsView: TabLayout? = null
    private var consolePageBodyHost: FrameLayout? = null
    private var selectedType = KiteRecipe.TYPE_OPEN_URL
    private var selectedIconName = KiteRecipeIcon.defaultNameForType(KiteRecipe.TYPE_OPEN_URL)
    private var selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
    private var selectedIconSource = ""
    private var selectedGroupId = ""
    private var groupSelectionDetailView: TextView? = null
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
    private var pendingRuntimePermissionBootstrap = false
    private var runtimePermissionRequestInFlight = false
    private var firstRunRuntimeGateShown = false
    private var bootstrapResourceGateInFlight = false
    private var firstRunPermissionOnboardingInFlight = false
    private var firstRunPermissionRequestInFlight = false
    private var firstRunRuntimePermissionsRequested = false
    private var firstRunAllFilesSettingsOpened = false
    private var ubuntuRuntimeState = UbuntuRuntimeUiState.checking()
    private val formSteps = mutableListOf<RecipeStepDraft>()
    private var pendingTerminalFlow: PendingTerminalFlow? = null
    private var localServerStarted = false
    private var consumedCardRunLaunchKey: String? = null
    private var focusedRunRecipeId: String? = null
    private var focusedRunInstanceId: String? = null
    private var registeredBrowserInstanceId: String? = null
    private var registeredDesktopInstanceId: String? = null
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
    private var resourcePageView: View? = null
    private var resourcePageNavView: View? = null
    private var resourceSectionHost: LinearLayout? = null
    private var resourceSearchBar: ResourceSearchBar? = null
    private var resourceSearchQuery: String = ""
    private var resourceSearchContentHost: LinearLayout? = null
    private var currentResourceSearchQuery: String = ""
    private var resourceSearchRequestSerial = 0L
    private var resourceSearchRenderKey = ""
    private var resourceHomeTab: String = RESOURCE_HOME_TAB_ALL
    private var resourceSectionsRenderKey: String = ""
    private var resourceSectionsRenderedRequestKey: String = ""
    private var resourceSectionsRequestSerial = 0L
    private var resourceSectionsRenderSerial = 0L
    private var resourceSectionsInFlightKey: String? = null
    private var resourceSectionsDirty = true
    private val resourceItemBindings = mutableMapOf<String, MutableList<ResourceItemBinding>>()
    private var resourceItemPatchRequestSerial = 0L
    private var resourceDetailInFlightKey: String? = null
    private var resourceDetailContentHost: FrameLayout? = null
    private var resourceDetailBinding: ResourceDetailBinding? = null
    private var resourceManageContentHost: LinearLayout? = null
    private var resourceManageBinding: ResourceManageBinding? = null
    private var resourceManageRequestSerial = 0L
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
    private var resourceRunUiRefreshPosted = false
    private var lastConsoleRuntimeRefreshAt = 0L
    private var cardRunSurfaceSignature = ""
    private var cardRunReportBinding: CardRunReportBinding? = null
    private var cardRunReportRefreshScheduled = false
    private var cardRunReportLastRefreshAt = 0L
    private var pendingCardRunReportState: RecipeRuntimeState? = null
    private var resourceInstallWizardBinding: ResourceInstallWizardBinding? = null
    private var resourceInstallWizardRefreshSerial = 0L
    private var resourceInstallWizardRefreshPosted = false
    private var foregroundLiveTickScheduled = false
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
        resourceInstallStore = KiteResourceInstallStore(this)
        resourceManifestLoader = KiteResourceManifestLoader(this)
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
            }
        )
        if (shouldStartLocalServer()) {
            localServer.start()
            localServerStarted = true
        }

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
        updateRuntimeGateOverlay()
        observeUbuntuBootstrapState()
        observeRootfsExtractionProgress()
        observeRuntimeBootstrapProgress()
        observeTerminalFlowSignals()
        observeCardRunStoreSignals()
        observeRuntimePanelSummarySignals()
        observeResourceInstallSignals()
        applyRecentTaskVisibilitySetting()
        val handledAutomationIntent = handleRuntimeAutomationIntent(intent)
        val handledLaunchIntent = !handledAutomationIntent && handleCardRunLaunchIntent(intent)
        if (!handledLaunchIntent && !restoreScreenFromBundle(savedInstanceState) && !restoreRecipeDraftFromSettings()) {
            showConsole()
        }
        refreshUbuntuRuntimeState()
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
        if (handleRuntimeAutomationIntent(intent)) return
        handleCardRunLaunchIntent(intent)
    }

    private fun handleRuntimeAutomationIntent(sourceIntent: Intent?): Boolean {
        val intent = sourceIntent ?: return false
        val runtimeAction = intent
            .getStringExtra(EXTRA_AUTOMATION_RUNTIME_ACTION)
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
        intent.removeExtra(EXTRA_AUTOMATION_RUNTIME_ACTION)
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
        applyRecentTaskVisibilitySetting()
        resumePendingFirstRunPermissionOnboarding()
        resumePendingRuntimePermissionBootstrap()
        rebindVisibleResourceStateOnResume()
        ensureBundledToolBootstrapIfNeeded("resume")
        if (this is CardRunActivity && rebindFocusedCardRunSurface("resume")) return
        when (currentScreen) {
            Screen.Console -> showConsole()
            Screen.Settings -> showSettings()
            else -> Unit
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
        closeResourceInstallTaskIfActivityDestroyed()
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
        CardRunTaskCloser.unregister(registeredCardRunCloserInstanceId)
        if (localServerStarted) {
            localServer.stop()
        }
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX Activity migration.")
    override fun onBackPressed() {
        if (handleWebViewBackSignal()) return
        if (this is CardRunActivity) {
            handleCardRunBackSignal()
            return
        }
        when (currentScreen) {
            Screen.CreateConfig -> handleRecipeFormBack()
            Screen.RecipeDetail -> {
                // T6b:RecipeDetail 走 Fragment,系统 back 时移除 Fragment 回编辑器
                if (supportFragmentManager.findFragmentByTag(TAG_RECIPE_RAW_JSON_FRAGMENT) != null) {
                    onExitRecipeRawJson()
                } else {
                    showConsole()
                }
            }
            Screen.RecipeMore -> returnToRecipeFormFromMore()
            Screen.ThemeSettings -> showSettings()
            Screen.ResourceManage -> showResources()
            Screen.ResourceSearch -> showResources()
            Screen.ResourceMore -> currentResourceDetailId?.let { showResourceDetail(it) } ?: showResources()
            Screen.ResourceRawJson -> currentResourceDetailId?.let { showResourceDetail(it) } ?: showResources()
            Screen.ResourceDetail -> showResources()
            Screen.Resources -> showConsole()
            Screen.Settings -> showConsole()
            Screen.Processes -> showConsole()
            Screen.Terminal -> if (isTerminalDetailMode) super.onBackPressed() else showConsole()
            else -> if (currentScreen != Screen.Console) showConsole() else super.onBackPressed()
        }
    }

    private fun handleWebViewBackSignal(): Boolean {
        if (!::webView.isInitialized) return false
        if (currentScreen != Screen.Workbench && currentScreen != Screen.CardRun) return false
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
                            updateVisibleCardRunReport(state)
                        }
                    }
                    rebindVisibleCardRunSurfaceFromStore(runs)
                    if (currentScreen == Screen.Console && consoleCardBindings.isNotEmpty()) {
                        currentRecipes.forEach { recipe ->
                            val state = CardRunStore.currentForRecipe(recipe.id)
                                ?: runtimeStates[recipe.id]
                                ?: RecipeRuntimeState.fromRecipeStatus(recipe.id, "unknown")
                            runtimeStates[recipe.id] = state
                            updateVisibleConsoleCard(recipe, state)
                        }
                    }
                    consumeResourceOpenRunSignals(runs)
                    renderRuntimePanelCounts()
                    updateVisibleResourceInstallWizardElapsed()
                }
            }
        }
    }

    private fun rebindVisibleCardRunSurfaceFromStore(runs: List<RecipeRuntimeState>) {
        if (currentScreen != Screen.CardRun) return
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
        when (currentScreen) {
            Screen.Resources,
            Screen.ResourceSearch -> {
                invalidateResourceRuntimeStateCache()
                requestVisibleResourceItemStatePatch(
                    reason = signal.reason,
                    preferredResourceIds = listOfNotNull(signal.resourceId, signal.targetResourceId)
                )
            }
            Screen.CardRun -> requestVisibleResourceInstallWizardRefresh(signal.reason)
            Screen.ResourceDetail -> requestVisibleResourceDetailStatePatch(signal.reason)
            Screen.ResourceMore -> invalidateResourceRuntimeStateCache()
            Screen.ResourceManage -> {
                invalidateResourceRuntimeStateCache()
                requestVisibleResourceItemStatePatch(
                    reason = signal.reason,
                    preferredResourceIds = listOfNotNull(signal.resourceId, signal.targetResourceId)
                )
                requestResourceManageRefresh(forceCatalogRefresh = false, reason = signal.reason)
            }
            else -> Unit
        }
    }

    private fun consumeResourceOpenRunSignals(runs: List<RecipeRuntimeState>) {
        val signature = buildResourceOpenRunSignature(runs)
        if (signature == resourceOpenRunSignature) return
        resourceOpenRunSignature = signature
        invalidateResourceRuntimeStateCache()
        requestResourceRunStateUiRefresh()
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
            Screen.Resources,
            Screen.ResourceSearch,
            Screen.ResourceDetail,
            Screen.ResourceMore,
            Screen.ResourceManage -> Unit
            else -> return
        }
        if (resourceRunUiRefreshPosted) return
        resourceRunUiRefreshPosted = true
        root.post {
            resourceRunUiRefreshPosted = false
            when (currentScreen) {
                Screen.Resources,
                Screen.ResourceSearch -> requestVisibleResourceItemStatePatch("resource_open_run_state")
                Screen.ResourceDetail -> requestVisibleResourceDetailStatePatch("resource_open_run_state")
                Screen.ResourceMore -> invalidateResourceRuntimeStateCache()
                Screen.ResourceManage -> requestResourceManageRefresh(forceCatalogRefresh = false, reason = "resource_open_run_state")
                else -> Unit
            }
        }
    }

    private fun rebindVisibleResourceStateOnResume() {
        if (this is CardRunActivity || !::root.isInitialized || !::resourceInstallStore.isInitialized) return
        when (currentScreen) {
            Screen.Resources,
            Screen.ResourceSearch -> {
                invalidateResourceRuntimeStateCache()
                requestVisibleResourceItemStatePatch("resume")
            }
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
        if (currentScreen == Screen.Console) showConsole()
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
                if (currentScreen == Screen.Resources) {
                    requestResourceSectionsRefresh(forceCatalogRefresh = true)
                }
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
        if (::root.isInitialized && currentScreen == Screen.Console && shouldRefreshConsoleForRuntimeState(previous, state)) {
            showConsole()
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
            Screen.ResourceSearch -> {
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
            groupId = selectedGroupId,
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
            if (currentScreen == Screen.Console) showConsole()
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
            if (currentScreen == Screen.Settings) showSettings()
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
        currentScreen = Screen.Console
        dropZoneStatus = dropZoneManager.prepareDropZone()
        currentRecipes = recipeLoader.loadAllRecipes()
        refreshRecipeRuntimeStates(currentRecipes)
        val focusedRecipe = focusedRunRecipe()
        if (this is CardRunActivity && focusedRecipe != null) {
            showCardRunSurface(focusedRecipe)
            return
        }
        if (consolePages().none { it.id == consolePageId }) consolePageId = CONSOLE_PAGE_ALL
        root.setBackgroundColor(tokens.pageBackground)
        clearRootForScreen()
        root.addView(consoleHeader())
        ubuntuRuntimeBanner()?.let { root.addView(it) }
        val pageHost = FrameLayout(this).also { consolePageBodyHost = it }
        renderConsolePageBody(pageHost)
        root.addView(pageHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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

    private fun showKiteProcessOverview(forceRefresh: Boolean = true) {
        if (forceRefresh) {
            requestRuntimePanelSummaryRefresh(force = true)
        }
        pruneRunManagementPendingProcessStops()
        currentScreen = Screen.Processes
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
        resourceInstallWizardBinding = null
        resourceDetailContentHost = null
        consoleCardBindings.clear()
        consolePageTabsView = null
        consolePageBodyHost = null
        groupSelectionDetailView = null
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
     * 过渡期:ScreenRouter 的老路径分发。把 Screen 枚举映射到老的 show* 方法。
     * T6b 起逐个 Screen 改走 Fragment 时,这些分支会被 routeToFragment 取代。
     * 仅无参、可在路由层触发的 Screen 在此分发;带参 Screen(如 ResourceDetail 需 resourceId)
     * 仍由各自的 show*(args) 直接调用,不经过此无参入口。
     */
    private fun dispatchLegacyScreen(screen: Screen) {
        when (screen) {
            Screen.Console -> showConsole()
            Screen.Settings -> showSettings()
            Screen.ThemeSettings -> showThemeSettings()
            Screen.Resources -> showResources()
            // 带 arg 或条件复杂的 Screen 暂不在无参路由分发,保留各自入口
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
        // T7:走 Fragment 路径。
        currentResourceDetailId = null
        currentScreen = Screen.Resources
        ensureBundledToolBootstrapIfNeeded("show_resources")
        clearRootForScreen()
        root.visibility = View.GONE
        val fragment = ResourcesFragment()
        supportFragmentManager.beginTransaction()
            .replace(rootHost.id, fragment, TAG_RESOURCES_FRAGMENT)
            .commitAllowingStateLoss()
    }

    /** ResourcesFragment.ResourcesHost 实现。 */
    override fun renderResourcesInto(container: ViewGroup) {
        rootHost.setBackgroundColor(tokens.pageBackground)
        container.setBackgroundColor(tokens.pageBackground)
        val page = ensureResourcePage()
        val nav = ensureResourcePageNav()
        showBottomNavigationImmediately(nav)
        detachFromParent(page)
        detachFromParent(nav)
        container.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(nav)
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
                addView(resourceCategoryTabs())
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
            showResourceSearch(query)
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

    private fun showResourceSearch(initialQuery: String = currentResourceSearchQuery) {
        // T7:走 Fragment 路径。
        currentScreen = Screen.ResourceSearch
        currentResourceDetailId = null
        currentResourceSearchQuery = initialQuery.trim()
        resourceSearchRenderKey = ""
        clearRootForScreen()
        root.visibility = View.GONE
        val fragment = ResourceSearchFragment.newInstance(currentResourceSearchQuery)
        supportFragmentManager.beginTransaction()
            .replace(rootHost.id, fragment, TAG_RESOURCE_SEARCH_FRAGMENT)
            .commitAllowingStateLoss()
    }

    /** ResourceSearchFragment.ResourceSearchHost 实现。 */
    override fun renderResourceSearchInto(container: ViewGroup, initialQuery: String) {
        rootHost.setBackgroundColor(tokens.pageBackground)
        container.setBackgroundColor(tokens.pageBackground)
        resourceSearchContentHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val searchInput = resourceSearchInput(initialQuery)
        container.addView(resourceSearchTopBar(searchInput))
        container.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(12), dp(22), dp(88))
                addView(resourceSearchContentHost)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        requestResourceSearchRefresh()
        searchInput.requestFocus()
        searchInput.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun resourceSearchTopBar(searchInput: EditText): View = row {
        setPadding(dp(16), dp(12), dp(16), dp(8))
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton("‹", dp(44), Color.TRANSPARENT, tokens.textPrimary, dp(16)) { showResources() })
        addView(FrameLayout(context).apply {
            background = roundedBox(tokens.surfaceElevated, tokens.border, dp(22).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(6), 0, dp(10), 0)
            }
            addView(TextView(context).apply {
                text = "⌕"
                textSize = 28f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.textPrimary)
            }, FrameLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL))
            addView(searchInput, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dp(44), 0, dp(42), 0)
            })
            addView(TextView(context).apply {
                text = "×"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.textTertiary)
                setOnClickListener {
                    searchInput.setText("")
                    searchInput.requestFocus()
                }
            }, FrameLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL))
        })
    }

    private fun resourceSearchInput(initialQuery: String): EditText =
        EditText(this).apply {
            setText(initialQuery)
            setSelection(text?.length ?: 0)
            hint = "搜索资源"
            textSize = 17f
            includeFontPadding = false
            setSingleLine(true)
            maxLines = 1
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(0, 0, 0, 0)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            addTextChangedListener(simpleTextWatcher { query ->
                currentResourceSearchQuery = query.trim()
                requestResourceSearchRefresh()
            })
            setOnEditorActionListener { view, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    currentResourceSearchQuery = view.text?.toString().orEmpty().trim()
                    requestResourceSearchRefresh()
                    hideKeyboard(view)
                    true
                } else {
                    false
                }
            }
        }

    private fun hideKeyboard(anchor: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(anchor.windowToken, 0)
    }

    private fun requestResourceSearchRefresh(
        query: String = currentResourceSearchQuery,
        forceCatalogRefresh: Boolean = false
    ) {
        val host = resourceSearchContentHost ?: return
        val requestId = ++resourceSearchRequestSerial
        if (host.childCount == 0) {
            host.addView(resourceRequestStateBlock("正在搜索资源", "资源列表会在后台加载。", loading = true))
        }
        thread(name = "KiteResourceSearch-$requestId", isDaemon = true) {
            val result = runCatching {
                val cleanQuery = query.trim()
                val resources = resourceCatalog(forceRefresh = forceCatalogRefresh)
                resources.searchResourceItems(cleanQuery)
            }
            runOnUiThread {
                if (requestId != resourceSearchRequestSerial || currentScreen != Screen.ResourceSearch) return@runOnUiThread
                result.onSuccess { items ->
                    renderResourceSearchResults(host, query.trim(), items)
                }.onFailure { error ->
                    host.removeAllViews()
                    host.addView(resourceRequestStateBlock("资源搜索失败", error.message ?: error.javaClass.simpleName))
                }
            }
        }
    }

    private fun renderResourceSearchResults(host: LinearLayout, query: String, items: List<ResourceItem>) {
        val renderKey = buildString {
            append(query)
            items.forEach { item ->
                append('|').append(item.id).append(':').append(item.stateLabel).append(':').append(item.actionLabel)
            }
        }
        if (resourceSearchRenderKey == renderKey && host.childCount > 0) return
        resourceSearchRenderKey = renderKey
        host.removeAllViews()
        if (items.isEmpty()) {
            host.addView(resourceSearchEmptyState(query))
        } else {
            host.addView(resourceListSection("搜索结果", items, showTitle = false))
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
        val payload = buildResourceSectionsPayload(query, forceCatalogRefresh, resourceHomeTab)
        return renderResourceSectionsPayload(sectionHost, payload)
    }

    private fun requestResourceSectionsRefresh(
        query: String = resourceSearchQuery,
        forceCatalogRefresh: Boolean = false
    ) {
        val sectionHost = resourceSectionHost ?: return
        val tabId = resourceHomeTab
        val requestKey = resourceSectionsRequestKey(query, tabId)
        val hasRenderedSections = sectionHost.childCount > 0
        if (
            !forceCatalogRefresh &&
            !resourceSectionsDirty &&
            resourceSectionsRenderedRequestKey == requestKey &&
            resourceSectionsRenderKey.isNotBlank() &&
            hasRenderedSections
        ) {
            requestVisibleResourceItemStatePatch("resource_sections_unchanged")
            return
        }
        if (!forceCatalogRefresh && resourceSectionsInFlightKey == requestKey) return
        val requestId = ++resourceSectionsRequestSerial
        resourceSectionsInFlightKey = requestKey
        resourceInstallStore.clearExpiredPageCache()
        thread(name = "KiteResourceSections-$requestId-${requestKey.take(24)}", isDaemon = true) {
            val result = runCatching { buildResourceSectionsPayload(query, forceCatalogRefresh, tabId) }
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

    private fun resourceSectionsRequestKey(query: String, tabId: String): String =
        "${KiteResourceRequestPolicy.storeListKey(query)}:tab:${KiteResourceInstallRecipes.safeId(tabId)}"

    private fun buildResourceSectionsPayload(
        query: String,
        forceCatalogRefresh: Boolean,
        tabId: String
    ): ResourceSectionsPayload {
        val resources = resourceCatalog(forceRefresh = forceCatalogRefresh)
        val cleanQuery = query.trim()
        val visibleResources = if (cleanQuery.isBlank()) resources else resources.filter { it.matchesResourceQuery(cleanQuery) }
        val sections = buildResourceHomeSections(visibleResources, resourceManifestLoader.requestHomeLayout(), tabId)
        return ResourceSectionsPayload(
            query = cleanQuery,
            resources = visibleResources,
            sections = sections,
            renderKey = buildResourceSectionsRenderKey(tabId, cleanQuery, sections)
        )
    }

    private fun buildResourceHomeSections(
        resources: List<ResourceItem>,
        layout: KiteResourceHomeLayout?,
        tabId: String
    ): List<ResourceHomeSectionUi> {
        val tab = layout?.tabs.orEmpty().firstOrNull { it.id == tabId }
        val sectionSpecs = tab?.sections?.takeIf { it.isNotEmpty() } ?: layout?.sections.orEmpty()
        val includeFallback = tab == null || tab.id == RESOURCE_HOME_TAB_ALL || tab.sections.isEmpty()
        return buildResourceHomeSections(resources, sectionSpecs, includeFallback)
    }

    private fun buildResourceHomeSections(
        resources: List<ResourceItem>,
        sectionSpecs: List<KiteResourceHomeSection>,
        includeFallback: Boolean
    ): List<ResourceHomeSectionUi> {
        val resourcesById = resources.associateBy { it.id }
        val usedIds = linkedSetOf<String>()
        val layoutSections = sectionSpecs.mapNotNull { section ->
            val sectionItems = section.items.mapNotNull { resourceId ->
                resourcesById[resourceId]?.also { usedIds.add(it.id) }
            }
            if (sectionItems.isEmpty()) {
                null
            } else {
                ResourceHomeSectionUi(id = section.id, title = section.title, style = section.style, items = sectionItems)
            }
        }
        val fallbackSections = if (!includeFallback) {
            emptyList()
        } else {
            resources
            .filterNot { it.id in usedIds }
            .filterNot { it.section == "仅搜索" }
            .groupBy { it.section }
            .mapNotNull { (title, items) ->
                val cleanTitle = title.ifBlank { "更多资源" }
                if (items.isEmpty()) null else ResourceHomeSectionUi(
                    id = KiteResourceInstallRecipes.safeId(cleanTitle),
                    title = cleanTitle,
                    style = "list",
                    items = items
                )
            }
        }
        return layoutSections + fallbackSections
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
        val renderSerial = ++resourceSectionsRenderSerial
        resourceItemBindings.clear()
        sectionHost.removeAllViews()
        if (payload.sections.isEmpty() && visibleResources.isEmpty()) {
            sectionHost.addView(resourceSearchEmptyState(payload.query))
        } else {
            renderResourceSectionsBatch(sectionHost, payload.sections, renderSerial)
        }
        resourceSectionsRenderKey = renderKey
        resourceSectionsDirty = false
        return true
    }

    private fun renderResourceSectionsBatch(
        sectionHost: LinearLayout,
        sections: List<ResourceHomeSectionUi>,
        renderSerial: Long,
        startIndex: Int = 0
    ) {
        if (renderSerial != resourceSectionsRenderSerial) return
        if (currentScreen != Screen.Resources) {
            resourceSectionsDirty = true
            return
        }
        val endIndex = (startIndex + RESOURCE_SECTION_RENDER_BATCH_SIZE).coerceAtMost(sections.size)
        for (index in startIndex until endIndex) {
            sectionHost.addView(resourceSection(sections[index]))
        }
        if (endIndex < sections.size) {
            sectionHost.postDelayed(
                { renderResourceSectionsBatch(sectionHost, sections, renderSerial, endIndex) },
                RESOURCE_SECTION_RENDER_BATCH_DELAY_MS
            )
        }
    }

    private fun renderResourceSectionsError(sectionHost: LinearLayout, error: Throwable) {
        resourceSectionsRenderSerial++
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

    private fun buildResourceSectionsRenderKey(
        tabId: String,
        query: String,
        sections: List<ResourceHomeSectionUi>
    ): String =
        buildString {
            append(tabId)
            append(':')
            append(query)
            sections.forEach { section ->
                append("|section:")
                append(section.id)
                append(':')
                append(section.title)
                append(':')
                append(section.style)
                append(':')
                append(section.items.joinToString(",") { it.id })
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
        resourceSearchContentHost = null
        currentResourceSearchQuery = ""
        resourceSearchRenderKey = ""
        resourceHomeTab = RESOURCE_HOME_TAB_ALL
        resourceSectionsRenderKey = ""
        resourceSectionsRenderedRequestKey = ""
        resourceSectionsRenderSerial++
        resourceSectionsDirty = true
        resourceItemBindings.clear()
        resourceDetailContentHost = null
        resourceDetailBinding = null
        resourceManageContentHost = null
        resourceManageBinding = null
    }

    private fun invalidateResourceCatalogCache() {
        resourceCatalogDirty = true
        resourceSectionsDirty = true
        cachedResourceCatalog = null
        cachedResourceCatalogUpdatedAt = 0L
        cachedToolchainWorkspaceSnapshot = ToolchainWorkspaceSnapshot()
        resourceManifestLoader.invalidate()
    }

    private fun invalidateResourceRuntimeStateCache() {
        resourceCatalogDirty = true
        cachedResourceCatalog = null
        cachedResourceCatalogUpdatedAt = 0L
        cachedToolchainWorkspaceSnapshot = ToolchainWorkspaceSnapshot()
    }

    private fun invalidateResourceUiCache() {
        clearResourcePageCache()
        invalidateResourceRuntimeStateCache()
    }

    private fun registerResourceItemBinding(binding: ResourceItemBinding) {
        purgeResourceItemBindings()
        resourceItemBindings
            .getOrPut(binding.resourceId) { mutableListOf() }
            .add(binding)
    }

    private fun purgeResourceItemBindings() {
        val iterator = resourceItemBindings.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.removeAll { it.root.parent == null }
            if (entry.value.isEmpty()) iterator.remove()
        }
    }

    private fun visibleResourceItemBindingIds(): Set<String> {
        purgeResourceItemBindings()
        return resourceItemBindings
            .filterValues { bindings -> bindings.any { it.root.isAttachedToWindow } }
            .keys
            .toSet()
    }

    private fun requestVisibleResourceItemStatePatch(
        reason: String,
        preferredResourceIds: Collection<String> = emptyList()
    ) {
        if (this is CardRunActivity || !::root.isInitialized || !::resourceInstallStore.isInitialized) return
        val screen = currentScreen
        if (screen != Screen.Resources && screen != Screen.ResourceSearch && screen != Screen.ResourceManage) return
        val visibleIds = visibleResourceItemBindingIds()
        val resourceIds = (visibleIds + preferredResourceIds)
            .map { KiteResourceInstallRecipes.safeId(it) }
            .filter { it.isNotBlank() }
            .toSet()
        if (resourceIds.isEmpty()) {
            if (screen == Screen.Resources && resourceSectionHost?.childCount == 0) {
                requestResourceSectionsRefresh(forceCatalogRefresh = false)
            } else if (screen == Screen.ResourceSearch && resourceSearchContentHost?.childCount == 0) {
                requestResourceSearchRefresh()
            } else if (screen == Screen.ResourceManage && resourceManageContentHost?.childCount == 0) {
                requestResourceManageRefresh(forceCatalogRefresh = false, reason = reason)
            }
            return
        }
        val requestId = ++resourceItemPatchRequestSerial
        thread(name = "KiteResourceItemPatch-$requestId-${reason.take(24)}", isDaemon = true) {
            val itemsById = runCatching {
                resourceCatalog(forceRefresh = false)
                    .filter { it.id in resourceIds }
                    .associateBy { it.id }
            }.getOrDefault(emptyMap())
            runOnUiThread {
                if (requestId != resourceItemPatchRequestSerial) return@runOnUiThread
                if (
                    currentScreen != Screen.Resources &&
                    currentScreen != Screen.ResourceSearch &&
                    currentScreen != Screen.ResourceManage
                ) return@runOnUiThread
                applyVisibleResourceItemStatePatch(itemsById)
            }
        }
    }

    private fun applyVisibleResourceItemStatePatch(itemsById: Map<String, ResourceItem>) {
        purgeResourceItemBindings()
        itemsById.forEach { (resourceId, item) ->
            resourceItemBindings[resourceId]
                .orEmpty()
                .filter { it.root.isAttachedToWindow }
                .forEach { binding -> bindResourceItemState(binding, item) }
        }
    }

    private fun bindResourceItemState(binding: ResourceItemBinding, item: ResourceItem) {
        binding.root.setOnClickListener { showResourceDetail(item.id, item) }
        binding.stateTextView?.text = "${item.version} · ${item.sizeLabel} · ${item.stateLabel}"
        binding.actionButton?.let { bindResourceActionButton(it, item, binding.compactAction) }
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
                    Screen.ResourceSearch -> requestResourceSearchRefresh()
                    Screen.CardRun -> requestVisibleResourceInstallWizardRefresh("catalog:$reason")
                    Screen.ResourceMore -> Unit
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
        // T7:走 Fragment 路径(ResourceManageFragment 壳 + 复用 Activity 渲染)。
        currentScreen = Screen.ResourceManage
        resourceManageBinding = null
        clearRootForScreen()
        root.visibility = View.GONE
        val fragment = ResourceManageFragment()
        supportFragmentManager.beginTransaction()
            .replace(rootHost.id, fragment, TAG_RESOURCE_MANAGE_FRAGMENT)
            .commitAllowingStateLoss()
    }

    /** ResourceManageFragment.ResourceManageHost 实现:把已验证的资源管理渲染挂进容器。 */
    override fun renderResourceManageInto(container: ViewGroup) {
        rootHost.setBackgroundColor(tokens.pageBackground)
        container.setBackgroundColor(tokens.pageBackground)
        container.addView(topBar("资源管理") {
            // 退出 Fragment 回资源首页
            supportFragmentManager.findFragmentByTag(TAG_RESOURCE_MANAGE_FRAGMENT)?.let { f ->
                supportFragmentManager.beginTransaction().remove(f).commitAllowingStateLoss()
            }
            root.visibility = View.VISIBLE
            showResources()
        })
        container.addView(ScrollView(this).apply {
            val host = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(18), dp(22), dp(34))
                addView(resourceManageEmptyBlock("正在读取资源管理信息", "执行队列和已获取资源会在后台加载，避免阻塞当前页面。"))
            }
            resourceManageContentHost = host
            addView(host)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(bottomNavigation())
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
                        resourceManageBinding = null
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
        val binding = ensureResourceManageBinding(host)
        val catalogById = payload.catalog.associateBy { it.id }
        val queueRenderKey = resourceManageQueueRenderKey(payload)
        if (binding.queueRenderKey != queueRenderKey) {
            binding.queueHost.removeAllViews()
            if (payload.planIds.isEmpty()) {
                binding.queueHost.addView(resourceManageEmptyBlock("暂无执行任务", "从资源商店点击获取或卸载后，这里会显示当前队列。"))
            } else {
                binding.queueHost.addView(resourceManageInstallTaskCard(
                    payload.planIds,
                    catalogById,
                    payload.planSnapshot,
                    payload.registrySnapshot
                ))
            }
            binding.queueRenderKey = queueRenderKey
        }
        val installed = payload.catalog.filter { resourceItemIsInstalled(it) }
        val installedRenderKey = resourceManageInstalledRenderKey(installed)
        if (binding.installedRenderKey != installedRenderKey) {
            binding.installedHost.removeAllViews()
            if (installed.isEmpty()) {
                binding.installedHost.addView(resourceManageEmptyBlock("暂无已获取资源", "获取成功并完成注册后，会出现在这里。"))
            } else {
                binding.installedHost.addView(LinearLayout(this).apply {
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
            binding.installedRenderKey = installedRenderKey
        } else {
            applyVisibleResourceItemStatePatch(installed.associateBy { it.id })
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

    private fun ensureResourceManageBinding(host: LinearLayout): ResourceManageBinding {
        resourceManageBinding?.takeIf { it.host === host }?.let { return it }
        host.removeAllViews()
        val queueHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val installedHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        host.addView(sectionTitle("执行队列"))
        host.addView(queueHost)
        host.addView(sectionTitle("已获取资源").apply {
            setPadding(0, dp(24), 0, dp(12))
        })
        host.addView(installedHost)
        return ResourceManageBinding(
            host = host,
            queueHost = queueHost,
            installedHost = installedHost
        ).also { resourceManageBinding = it }
    }

    private fun resourceManageQueueRenderKey(payload: ResourceManagePayload): String =
        buildString {
            append(payload.planSnapshot.targetResourceId)
            append(':').append(payload.planSnapshot.status)
            payload.planIds.forEach { resourceId ->
                val entry = payload.registrySnapshot[resourceId]
                append('|').append(resourceId)
                append(':').append(payload.planSnapshot.stepStatus(resourceId))
                append(':').append(entry?.status.orEmpty())
                append(':').append(entry?.operation.orEmpty())
                append(':').append(entry?.updatedAt ?: 0L)
            }
        }

    private fun resourceManageInstalledRenderKey(installed: List<ResourceItem>): String =
        installed.joinToString("|") { item ->
            listOf(item.id, item.name, item.version, item.sizeLabel, item.sourceLabel).joinToString(":")
        }

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
            resourcePlanStepIsInstalled(id, catalogById, registrySnapshot[id])
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
            if (targetId.isNotBlank()) {
                bindResourceManageInstallTaskCardGesture(
                    view = this,
                    targetResourceId = targetId,
                    planResourceIds = planIds
                )
            }
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
                        text = "$completedCount/${planIds.size} · $statusText · 点击打开，上滑关闭"
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
        }
    }

    private fun bindResourceManageInstallTaskCardGesture(
        view: View,
        targetResourceId: String,
        planResourceIds: List<String>
    ) {
        var downX = 0f
        var downY = 0f
        view.isClickable = true
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dy < -dp(16) && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                        view.translationY = dy.coerceAtLeast(-dp(56).toFloat())
                        view.alpha = (1f + dy / dp(160).toFloat()).coerceIn(0.62f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    view.translationY = 0f
                    view.alpha = 1f
                    if (dy < -dp(48) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) {
                        cancelResourceInstallTask(
                            targetResourceId = targetResourceId,
                            planResourceIds = planResourceIds,
                            closeWizard = false
                        )
                    } else {
                        view.performClick()
                        showResourceInstallWizard(targetResourceId)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    view.translationY = 0f
                    view.alpha = 1f
                    true
                }
                else -> true
            }
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
        private val onSearchRequested: (String) -> Unit
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
            setOnClickListener { onSearchRequested(text?.toString().orEmpty()) }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onSearchRequested(text?.toString().orEmpty())
            }
            setOnEditorActionListener { view, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onSearchRequested(view.text?.toString().orEmpty())
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
            setOnClickListener { onSearchRequested(inputView.text?.toString().orEmpty()) }
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
                    if (focusInput && !nextCollapsed) onSearchRequested(inputView.text?.toString().orEmpty())
                }
            })
        }

        private fun searchBackground(progress: Float): GradientDrawable =
            roundedBox(
                Color.argb((218 + (18 * progress)).toInt(), 255, 255, 255),
                Color.argb((70 + (22 * progress)).toInt(), 168, 184, 194),
                (barHeight / 2f)
            )
    }

    private fun ResourceItem.matchesResourceQuery(query: String): Boolean {
        return resourceSearchScore(query) > 0
    }

    private fun List<ResourceItem>.searchResourceItems(query: String): List<ResourceItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return this
        return mapIndexedNotNull { index, item ->
            val score = item.resourceSearchScore(cleanQuery)
            if (score <= 0) null else Triple(score, index, item)
        }
            .sortedWith(compareByDescending<Triple<Int, Int, ResourceItem>> { it.first }.thenBy { it.second })
            .map { it.third }
    }

    private fun ResourceItem.resourceSearchScore(query: String): Int {
        val needle = normalizeResourceSearchText(query)
        if (needle.isBlank()) return 0
        val compactName = normalizeResourceSearchText(name)
        val acronym = resourceSearchAcronym(name)
        val nameIndex = compactName.indexOf(needle)
        return when {
            compactName == needle -> 10_000
            compactName.startsWith(needle) -> 9_000 - (compactName.length - needle.length).coerceAtLeast(0)
            nameIndex >= 0 -> 8_000 - (nameIndex * 80) - (compactName.length - needle.length).coerceAtMost(80)
            acronym.startsWith(needle) -> 7_000
            needle.length == 1 -> 0
            normalizeResourceSearchText(description).contains(needle) -> 3_000
            needle.length < 3 -> 0
            else -> resourceFuzzyNameScore(needle, compactName)
        }
    }

    private fun normalizeResourceSearchText(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun resourceSearchAcronym(value: String): String =
        value.split(Regex("""[^A-Za-z0-9\u4e00-\u9fff]+"""))
            .mapNotNull { token -> token.firstOrNull()?.lowercaseChar() }
            .joinToString("")

    private fun resourceFuzzyNameScore(needle: String, candidate: String): Int {
        if (needle.length < 3 || candidate.isBlank()) return 0
        val samePosition = needle.indices.count { index ->
            index < candidate.length && needle[index] == candidate[index]
        }
        var ordered = 0
        var cursor = 0
        needle.forEach { char ->
            val foundAt = candidate.indexOf(char, cursor)
            if (foundAt >= 0) {
                ordered++
                cursor = foundAt + 1
            }
        }
        val samePercent = samePosition * 100 / needle.length
        val orderedPercent = ordered * 100 / needle.length
        if (samePercent < 60 && orderedPercent < 75) return 0
        return 4_000 + samePercent * 8 + orderedPercent * 4 - kotlin.math.abs(candidate.length - needle.length).coerceAtMost(80)
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

    private fun resourceHero(): View {
        val hero = resourceManifestLoader.requestHomeLayout()?.hero
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(22), 0, dp(18)) }
            addView(row {
                hero?.let { addView(resourceHeroPoster(it.resourceId, it.imageAsset, it.contentDescription)) }
            })
        }
    }

    private fun resourceHeroPoster(resourceId: String, imageAsset: String, contentDescriptionText: String): View {
        val posterWidth = (resources.displayMetrics.widthPixels - dp(44)).coerceAtLeast(dp(280))
        val posterHeight = (posterWidth * 780f / 1200f).toInt().coerceIn(dp(180), dp(240))
        return FrameLayout(this).apply {
            contentDescription = contentDescriptionText.ifBlank { "资源海报，点击查看资源详情" }
            isClickable = true
            isFocusable = true
            background = roundedBox(tokens.surface, Color.rgb(225, 226, 229), dp(24).toFloat())
            clipToOutline = true
            elevation = dp(1).toFloat()
            setOnClickListener { showResourceDetail(resourceId) }
            layoutParams = LinearLayout.LayoutParams(posterWidth, posterHeight)
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                requestResourceIconBitmap(imageAsset, maxOf(posterWidth, posterHeight)) { bitmap ->
                    if (parent != null) setImageBitmap(bitmap)
                }?.let { setImageBitmap(it) }
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun resourceCategoryTabs(): View =
        TabLayout(this).apply {
            contentDescription = "资源分类"
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setBackgroundColor(Color.TRANSPARENT)
            setSelectedTabIndicatorColor(tokens.primaryStrong)
            setTabTextColors(tokens.textSecondary, tokens.primaryStrong)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }

            val tabs = resourceHomeTabs(resourceManifestLoader.requestHomeLayout())
            val selectedId = resourceHomeTab.takeIf { selected -> tabs.any { it.id == selected } }
                ?: RESOURCE_HOME_TAB_ALL
            resourceHomeTab = selectedId
            tabs.forEach { homeTab ->
                addTab(newTab().setText(homeTab.label).setTag(homeTab.id), homeTab.id == selectedId)
            }
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val nextTab = tab.tag as? String ?: RESOURCE_HOME_TAB_ALL
                    if (resourceHomeTab == nextTab) return
                    resourceHomeTab = nextTab
                    resourceSectionsDirty = true
                    requestResourceSectionsRefresh(forceCatalogRefresh = false)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }

    private fun resourceHomeTabs(layout: KiteResourceHomeLayout?): List<KiteResourceHomeTab> {
        val tabs = layout?.tabs.orEmpty()
        if (tabs.isNotEmpty()) return tabs
        return listOf(KiteResourceHomeTab(RESOURCE_HOME_TAB_ALL, "全部", emptyList()))
    }

    private fun resourceSection(section: ResourceHomeSectionUi): View =
        if (section.style.equals("shelf", ignoreCase = true)) {
            resourceToolShelfSection(section)
        } else {
            resourceListSection(
                title = section.title,
                items = section.items,
                showTitle = resourceHomeTab == RESOURCE_HOME_TAB_ALL || section.id != resourceHomeTab
            )
        }

    private fun resourceListSection(title: String, items: List<ResourceItem>, showTitle: Boolean = true): View {
        if (items.isEmpty()) return LinearLayout(this)
        val sectionView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, if (showTitle) dp(24) else 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (showTitle) {
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
            }
        }
        val rowsHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, if (showTitle) dp(12) else 0, 0, 0) }
        }
        sectionView.addView(rowsHost)
        rowsHost.post { renderResourceListRowsBatch(sectionView, rowsHost, items) }
        return sectionView
    }

    private fun renderResourceListRowsBatch(
        sectionView: View,
        rowsHost: LinearLayout,
        items: List<ResourceItem>,
        startIndex: Int = 0
    ) {
        if (currentScreen != Screen.Resources && currentScreen != Screen.ResourceSearch) {
            resourceSectionsDirty = true
            return
        }
        if (sectionView.parent == null) return
        val endIndex = (startIndex + RESOURCE_LIST_ROW_RENDER_BATCH_SIZE).coerceAtMost(items.size)
        for (index in startIndex until endIndex) {
            rowsHost.addView(resourceListRow(items[index]))
            if (index != items.lastIndex) {
                rowsHost.addView(divider().apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        setMargins(dp(64), dp(8), dp(12), dp(8))
                    }
                })
            }
        }
        if (endIndex < items.size) {
            rowsHost.postDelayed(
                { renderResourceListRowsBatch(sectionView, rowsHost, items, endIndex) },
                RESOURCE_LIST_ROW_RENDER_BATCH_DELAY_MS
            )
        }
    }

    private fun resourceToolShelfSection(section: ResourceHomeSectionUi): View {
        val items = section.items
        if (items.isEmpty()) return LinearLayout(this)
        val sectionView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val itemsHost = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        sectionView.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(itemsHost)
        })
        itemsHost.post { renderResourceShelfItemsBatch(sectionView, itemsHost, items) }
        return sectionView
    }

    private fun renderResourceShelfItemsBatch(
        sectionView: View,
        itemsHost: LinearLayout,
        items: List<ResourceItem>,
        startIndex: Int = 0
    ) {
        if (currentScreen != Screen.Resources) {
            resourceSectionsDirty = true
            return
        }
        if (sectionView.parent == null) return
        val endIndex = (startIndex + RESOURCE_TOOL_SHELF_RENDER_BATCH_SIZE).coerceAtMost(items.size)
        for (index in startIndex until endIndex) {
            itemsHost.addView(resourceToolShelfItem(items[index]).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(RESOURCE_TOOL_SHELF_ITEM_WIDTH_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index != items.lastIndex) setMargins(0, 0, dp(RESOURCE_TOOL_SHELF_ITEM_GAP_DP), 0)
                }
            })
        }
        if (endIndex < items.size) {
            itemsHost.postDelayed(
                { renderResourceShelfItemsBatch(sectionView, itemsHost, items, endIndex) },
                RESOURCE_TOOL_SHELF_RENDER_BATCH_DELAY_MS
            )
        }
    }

    private fun resourceToolShelfItem(item: ResourceItem): View {
        var actionButton: TextView? = null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, dp(1))
            setOnClickListener { showResourceDetail(item.id, item) }
            addView(resourceShelfIcon(item))
            addView(TextView(context).apply {
                text = item.name
                textSize = RESOURCE_TOOL_SHELF_TITLE_TEXT_SP
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                setPadding(0, dp(7), 0, 0)
                layoutParams = LinearLayout.LayoutParams(dp(RESOURCE_TOOL_SHELF_TEXT_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            val button = resourceActionButton(item, compact = true).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(RESOURCE_ACTION_BUTTON_COMPACT_WIDTH_DP),
                    dp(RESOURCE_ACTION_BUTTON_COMPACT_HEIGHT_DP)
                ).apply {
                    setMargins(0, dp(7), 0, 0)
                }
            }
            actionButton = button
            addView(button)
        }.also { root ->
            registerResourceItemBinding(
                ResourceItemBinding(
                    resourceId = item.id,
                    root = root,
                    stateTextView = null,
                    actionButton = actionButton,
                    compactAction = true
                )
            )
        }
    }

    private fun resourceListRow(item: ResourceItem): View {
        var stateTextView: TextView? = null
        var actionButton: TextView? = null
        return row {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
            setOnClickListener { showResourceDetail(item.id, item) }
            addView(resourceIcon(item))
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
                val stateView = TextView(context).apply {
                    text = "${item.version} · ${item.sizeLabel} · ${item.stateLabel}"
                    textSize = 11f
                    setTextColor(tokens.textTertiary)
                    setPadding(0, dp(3), 0, 0)
                }
                stateTextView = stateView
                addView(stateView)
            })
            val button = resourceActionButton(item, compact = true)
            actionButton = button
            addView(button)
        }.also { root ->
            registerResourceItemBinding(
                ResourceItemBinding(
                    resourceId = item.id,
                    root = root,
                    stateTextView = stateTextView,
                    actionButton = actionButton,
                    compactAction = true
                )
            )
        }
    }

    private fun resourceIcon(item: ResourceItem): View =
        resourceIcon(item.iconText, item.accent, item.iconAsset, item.iconFit)

    private fun resourceIcon(textValue: String, accent: String, assetPath: String = "", iconFit: String = ""): View {
        return resourceIcon(textValue, accent, assetPath, iconFit, size = dp(56), padding = dp(7), radius = dp(14).toFloat(), textSize = 15f)
    }

    private fun resourceShelfIcon(item: ResourceItem): View =
        resourceIcon(
            item.iconText,
            item.accent,
            item.iconAsset,
            item.iconFit,
            size = dp(RESOURCE_TOOL_SHELF_ICON_SIZE_DP),
            padding = dp(RESOURCE_TOOL_SHELF_ICON_PADDING_DP),
            radius = dp(RESOURCE_TOOL_SHELF_ICON_RADIUS_DP).toFloat(),
            textSize = RESOURCE_TOOL_SHELF_ICON_TEXT_SP
        )

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

    private fun resourceActionButton(item: ResourceItem, compact: Boolean): TextView =
        TextView(this).apply {
            bindResourceActionButton(this, item, compact)
            layoutParams = LinearLayout.LayoutParams(
                if (compact) dp(RESOURCE_ACTION_BUTTON_COMPACT_WIDTH_DP) else ViewGroup.LayoutParams.MATCH_PARENT,
                if (compact) dp(RESOURCE_ACTION_BUTTON_COMPACT_HEIGHT_DP) else dp(36)
            )
        }

    private fun bindResourceActionButton(button: TextView, item: ResourceItem, compact: Boolean) {
        button.apply {
            text = item.actionLabel
            textSize = if (compact) RESOURCE_ACTION_BUTTON_COMPACT_TEXT_SP else 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            alpha = if (item.actionEnabled) 1f else 0.58f
            isEnabled = item.actionEnabled
            background = roundedBox(
                tokens.primarySubtle,
                Color.TRANSPARENT,
                dp(if (compact) RESOURCE_ACTION_BUTTON_COMPACT_RADIUS_DP else 15).toFloat(),
                0
            )
            setOnClickListener(null)
            if (item.actionEnabled) setOnClickListener { handleResourceAction(item) }
        }
    }

    private fun resourceActionEnabled(actionLabel: String, busy: Boolean): Boolean =
        when (actionLabel) {
            "启动中", "停止中" -> false
            "获取中" -> true
            "卸载中", "处理中" -> false
            else -> !busy
        }

    private fun resourceDetailActionArea(item: ResourceItem): ResourceDetailActionBinding {
        val primaryButton = TextView(this)
        val secondaryButton = TextView(this)
        val root = row {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                setMargins(0, dp(24), 0, 0)
            }
            addView(primaryButton)
            addView(secondaryButton)
        }
        return ResourceDetailActionBinding(
            root = root,
            primaryButton = primaryButton,
            secondaryButton = secondaryButton
        ).also { bindResourceDetailActionArea(it, item) }
    }

    private fun bindResourceDetailActionArea(binding: ResourceDetailActionBinding, item: ResourceItem) {
        val splitActions = resourceHasSplitActions(item)
        binding.primaryButton.layoutParams = LinearLayout.LayoutParams(
            0,
            dp(46),
            if (splitActions) 0.7f else 1f
        )
        bindResourceActionButton(binding.primaryButton, item, compact = false)
        binding.secondaryButton.layoutParams = LinearLayout.LayoutParams(0, dp(46), 0.3f).apply {
            setMargins(dp(10), 0, 0, 0)
        }
        if (splitActions) {
            binding.secondaryButton.visibility = View.VISIBLE
            bindResourceSecondaryActionButton(binding.secondaryButton, item)
        } else {
            binding.secondaryButton.visibility = View.GONE
            binding.secondaryButton.setOnClickListener(null)
        }
    }

    private fun resourceHasSplitActions(item: ResourceItem): Boolean =
        item.actionLabel == "获取中" ||
            (resourceIsInstalled(item) && (item.actionLabel == "打开" || item.actionLabel == "运行中")) ||
            resourceHasFailedInstallActions(item)

    private fun resourceHasFailedInstallActions(item: ResourceItem): Boolean =
        item.actionLabel == "重新获取" &&
            resourceInstallStore.isFailed(item.id) &&
            resourceInstallStore.failedOperation(item.id) != KiteResourceInstallStore.OP_UNINSTALL

    private fun resourceSecondaryActionButton(item: ResourceItem): TextView =
        TextView(this).apply { bindResourceSecondaryActionButton(this, item) }

    private fun bindResourceSecondaryActionButton(button: TextView, item: ResourceItem) {
        val isCancel = item.actionLabel == "获取中" || resourceHasFailedInstallActions(item)
        val isRunningOpen = item.actionLabel == "运行中"
        button.apply {
            text = when {
                isCancel -> "取消"
                isRunningOpen -> "中止"
                else -> "卸载"
            }
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.danger)
            alpha = if (item.actionEnabled) 1f else 0.58f
            isEnabled = item.actionEnabled
            background = roundedBox(tintBackground(tokens.danger), tintBackgroundBorder(tokens.danger), dp(15).toFloat(), 0)
            setOnClickListener(null)
            if (item.actionEnabled) {
                setOnClickListener {
                    if (item.actionLabel == "获取中") {
                        handleResourceCancelInstallTask(item)
                    } else if (isRunningOpen) {
                        handleResourceOpenStopAction(item)
                    } else if (isCancel) {
                        handleResourceFailedInstallCancel(item)
                    } else {
                        handleResourceUninstallAction(item)
                    }
                }
            }
        }
    }

    private fun showResourceDetail(resourceId: String, initialItem: ResourceItem? = null) {
        val requestKey = KiteResourceRequestPolicy.resourceDetailKey(resourceId)
        if (currentScreen == Screen.ResourceDetail && currentResourceDetailId == resourceId) {
            val binding = resourceDetailBinding
            val contentHost = resourceDetailContentHost
            if (binding != null && contentHost != null && binding.contentHost === contentHost) {
                val latestItem = initialItem ?: cachedResourceCatalog?.firstOrNull { it.id == resourceId }
                if (latestItem != null && binding.renderKey == buildResourceDetailRenderKey(latestItem)) {
                    patchResourceDetailState(binding, latestItem)
                } else {
                    requestVisibleResourceDetailStatePatch("same_detail")
                }
                return
            }
        }
        if (resourceDetailInFlightKey == requestKey && currentScreen == Screen.ResourceDetail) return
        val requestId = ++resourceDetailRequestSerial
        resourceDetailInFlightKey = requestKey
        currentResourceDetailId = resourceId
        currentScreen = Screen.ResourceDetail
        // T7:走 Fragment 路径。把渲染(含异步线程)交给 renderResourceDetailInto。
        clearRootForScreen()
        root.visibility = View.GONE
        val fragment = ResourceDetailFragment.newInstance(resourceId)
        supportFragmentManager.beginTransaction()
            .replace(rootHost.id, fragment, TAG_RESOURCE_DETAIL_FRAGMENT)
            .commitAllowingStateLoss()
        // initialItem 通过成员传递给 renderResourceDetailInto
        pendingResourceDetailInitialItem = initialItem
        pendingResourceDetailRequestId = requestId
        pendingResourceDetailRequestKey = requestKey
    }

    /** ResourceDetailFragment.ResourceDetailHost 实现:渲染资源详情(含异步加载线程)。 */
    override fun renderResourceDetailInto(container: ViewGroup, resourceId: String) {
        rootHost.setBackgroundColor(tokens.pageBackground)
        container.setBackgroundColor(tokens.pageBackground)
        val requestId = pendingResourceDetailRequestId
        val requestKey = pendingResourceDetailRequestKey ?: KiteResourceRequestPolicy.resourceDetailKey(resourceId)
        val initialItem = pendingResourceDetailInitialItem
        pendingResourceDetailInitialItem = null
        val contentHost = FrameLayout(this)
        resourceDetailContentHost = contentHost
        container.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(bottomNavigation())
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
                        val renderKey = buildResourceDetailRenderKey(item)
                        if (seedItem == null && renderKey != seedRenderKey) {
                            renderResourceDetailContent(contentHost, item)
                        } else {
                            patchVisibleResourceDetailState(item)
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

    private fun refreshVisibleResourceDetail(resourceId: String) {
        val contentHost = resourceDetailContentHost
        if (contentHost == null || currentScreen != Screen.ResourceDetail || currentResourceDetailId != resourceId) {
            showResourceDetail(resourceId)
            return
        }
        val requestKey = KiteResourceRequestPolicy.resourceDetailKey(resourceId)
        val requestId = ++resourceDetailRequestSerial
        resourceDetailInFlightKey = requestKey
        thread(name = "KiteResourceDetailRefresh-$requestId-${requestKey.take(24)}", isDaemon = true) {
            val result = runCatching {
                resourceCatalog(forceRefresh = false).firstOrNull { it.id == resourceId }
            }
            runOnUiThread {
                if (
                    requestId != resourceDetailRequestSerial ||
                    currentScreen != Screen.ResourceDetail ||
                    currentResourceDetailId != resourceId ||
                    resourceDetailContentHost !== contentHost
                ) {
                    if (resourceDetailInFlightKey == requestKey) resourceDetailInFlightKey = null
                    return@runOnUiThread
                }
                if (resourceDetailInFlightKey == requestKey) resourceDetailInFlightKey = null
                val item = result.getOrNull()
                when {
                    item != null -> {
                        val binding = resourceDetailBinding
                        if (
                            binding != null &&
                            binding.contentHost === contentHost &&
                            binding.resourceId == item.id &&
                            binding.renderKey == buildResourceDetailRenderKey(item)
                        ) {
                            patchResourceDetailState(binding, item)
                        } else {
                            renderResourceDetailContent(contentHost, item)
                        }
                        cacheResourceDetailPayload(item)
                        requestResourceDetailMedia(item)
                    }
                    result.isFailure -> renderResourceDetailError(contentHost, resourceId, result.exceptionOrNull())
                    else -> renderResourceDetailMissing(contentHost, resourceId)
                }
            }
        }
    }

    private fun requestVisibleResourceDetailStatePatch(reason: String) {
        val binding = resourceDetailBinding ?: return
        val resourceId = currentResourceDetailId ?: return
        if (
            currentScreen != Screen.ResourceDetail ||
            binding.resourceId != resourceId ||
            binding.contentHost !== resourceDetailContentHost
        ) return
        val requestId = ++resourceDetailRequestSerial
        thread(name = "KiteResourceDetailStatePatch-$requestId-${reason.take(24)}", isDaemon = true) {
            val item = runCatching {
                resourceCatalog(forceRefresh = false).firstOrNull { it.id == resourceId }
            }.getOrNull()
            runOnUiThread {
                if (
                    requestId != resourceDetailRequestSerial ||
                    currentScreen != Screen.ResourceDetail ||
                    currentResourceDetailId != resourceId ||
                    resourceDetailBinding !== binding ||
                    binding.contentHost !== resourceDetailContentHost
                ) return@runOnUiThread
                item?.let { latest ->
                    if (binding.renderKey == buildResourceDetailRenderKey(latest)) {
                        patchResourceDetailState(binding, latest)
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
            append(item.version)
            append(':')
            append(item.badge)
            append(':')
            append(item.media)
            append(':')
            append(item.previewCards)
            append(':')
            append(item.requirementRows)
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
        val actionBinding = resourceDetailActionArea(item)
        val actionHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(actionBinding.root)
        }
        resourceDetailBinding = ResourceDetailBinding(
            resourceId = item.id,
            contentHost = contentHost,
            actionHost = actionHost,
            actionBinding = actionBinding,
            renderKey = buildResourceDetailRenderKey(item)
        )
        contentHost.removeAllViews()
        contentHost.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(34))
                addView(resourceDetailChrome(item))
                addView(resourceDetailHeader(item))
                addView(actionHost)
                addView(resourceDetailVisualBlock(item))
                addView(resourceInfoBlock("简介", item.longDescription))
                resourceRecommendationBlock(item)?.let { addView(it) }
                addView(resourceExecutionPreviewBlock(item))
                addView(resourceRequirementsBlock(item))
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun renderResourceDetailPending(contentHost: FrameLayout, resourceId: String) {
        resourceDetailBinding = null
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
        resourceDetailBinding = null
        contentHost.removeAllViews()
        contentHost.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(34))
            addView(resourceDetailLoadingChrome(null))
            addView(resourceRequestStateBlock("资源暂不可用", resourceId, loading = false))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun renderResourceDetailError(contentHost: FrameLayout, resourceId: String, error: Throwable?) {
        resourceDetailBinding = null
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

    private fun patchVisibleResourceDetailState(item: ResourceItem): Boolean {
        val binding = resourceDetailBinding ?: return false
        if (
            currentScreen != Screen.ResourceDetail ||
            currentResourceDetailId != item.id ||
            binding.resourceId != item.id ||
            binding.contentHost !== resourceDetailContentHost
        ) return false
        patchResourceDetailState(binding, item)
        return true
    }

    private fun patchResourceDetailState(binding: ResourceDetailBinding, item: ResourceItem) {
        bindResourceDetailActionArea(binding.actionBinding, item)
        binding.renderKey = buildResourceDetailRenderKey(item)
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
            addView(resourceIcon(item).apply {
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
                    val tone = KiteTheme.accent(item.badge.accent, tokens)
                    addView(TextView(context).apply {
                        text = item.badge.iconText
                        textSize = 11f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.buttonText)
                        background = roundedBox(tone.strong, tone.strong, dp(8).toFloat())
                        layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                    })
                    addView(TextView(context).apply {
                        text = item.badge.label
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
        val recommendations = resourceRecommendationsFor(item)
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
            addView(resourceIcon(item))
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

    private fun resourceRecommendationsFor(item: ResourceItem): List<ResourceRecommendation> {
        return item.recommendations
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

    private fun resourceDetailVisualBlock(item: ResourceItem): View =
        if (item.media != null) {
            resourceImageBanner(item)
        } else {
            resourcePreviewStrip(item)
        }

    private fun resourceImageBanner(item: ResourceItem): View =
        FrameLayout(this).apply {
            val tone = KiteTheme.accent(item.accent, tokens)
            background = roundedBox(tokens.surface, tone.border, dp(22).toFloat())
            clipToOutline = true
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(228)).apply {
                setMargins(0, dp(20), 0, 0)
            }
            addView(ImageView(context).apply {
                contentDescription = item.media?.contentDescription ?: "${item.name} 视觉预览"
                scaleType = ImageView.ScaleType.CENTER_CROP
                item.media?.asset?.let { asset ->
                    requestResourceIconBitmap(asset, maxOf(resources.displayMetrics.widthPixels, dp(228))) { bitmap ->
                        if (parent != null) setImageBitmap(bitmap)
                    }?.let { setImageBitmap(it) }
                }
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

    private fun resourcePreviewStrip(item: ResourceItem): View =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(20), 0, 0) }
            addView(row {
                item.previewCards.forEachIndexed { index, preview ->
                    addView(resourcePreviewCard(preview).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(176), dp(136)).apply {
                            setMargins(0, 0, if (index == item.previewCards.lastIndex) 0 else dp(12), 0)
                        }
                    })
                }
            })
        }

    private fun resourcePreviewCard(preview: ResourcePreviewCard): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(10))
            val tone = KiteTheme.accent(preview.accent, tokens)
            background = roundedBox(tokens.cardBackground, tone.border, dp(16).toFloat())
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
                background = roundedBox(tokens.surface, tokens.border, dp(13).toFloat())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(0, dp(10), 0, 0)
                }
                addView(resourceIcon(
                    preview.symbol,
                    preview.accent,
                    preview.iconAsset,
                    preview.iconFit,
                    size = dp(42),
                    padding = dp(6),
                    radius = dp(12).toFloat(),
                    textSize = 15f
                ))
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
                val rows = (item.requirementRows.ifEmpty { resourceDefaultRequirementRows(item) } +
                    ResourceRequirementRow("状态", item.stateLabel))
                rows.forEachIndexed { index, row ->
                    addView(resourceRequirementRow(row.label, row.value))
                    if (index != rows.lastIndex) addView(divider().apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            setMargins(0, dp(1), 0, dp(1))
                        }
                    })
                }
            })
        }

    private fun resourceDefaultRequirementRows(item: ResourceItem): List<ResourceRequirementRow> =
        listOf(
            ResourceRequirementRow("获取来源", item.sourceLabel),
            ResourceRequirementRow("占用空间", item.sizeLabel),
            ResourceRequirementRow("资源类型", item.category)
        ).filter { it.value.isNotBlank() }

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
        if (
            item.actionLabel in listOf("获取", "重新获取", "安装", "重新安装") &&
            resourceActionShouldReopenInstallWizard(item)
        ) {
            reopenResourceInstallWizard(item)
            return
        }
        when (item.actionLabel) {
            "获取", "重新获取", "安装", "重新安装" -> handleResourceInstallAction(item)
            "处理中", "获取中" -> reopenResourceInstallWizard(item)
            "卸载中" -> showResourceDiscreteToast("${item.name} 正在卸载")
            "打开", "运行中" -> {
                val recipe = resourceOpenRecipe(item)
                if (recipe == null) {
                    showResourceDiscreteToast("${item.name} 的打开动作稍后接入")
                } else {
                    startResourceOpen(item, recipe)
                }
            }
            "卸载", "继续卸载" -> {
                handleResourceUninstallAction(item)
            }
            else -> showResourceDiscreteToast("正在处理 ${item.name}")
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
        val requestId = ++resourceInstallPlanRequestSerial
        thread(name = "KiteResourceInstallPlan-$requestId-${item.id}", isDaemon = true) {
            val result = runCatching { buildResourceInstallPlan(item) }
            runOnUiThread {
                if (requestId != resourceInstallPlanRequestSerial) return@runOnUiThread
                result.onSuccess { plan ->
                    handleResourceInstallPlanReady(item, plan)
                }.onFailure { error ->
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
            val missingNames = plan.missing
                .map { it.resource?.name ?: it.requirement }
                .distinct()
                .joinToString("、")
            showResourceDiscreteToast("缺少可获取的基础层：$missingNames")
            return
        }
        if (plan.steps.isEmpty()) {
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
                handleResourceInstallAction(item)
            } else if (continuation == ResourceUninstallContinuation.CancelFailedInstall) {
                resourceInstallStore.clearPlan()
                currentResourceInstallTargetId = null
                resourceInstallWizardPlanIds = emptyList()
                activeResourceInstallWizard = null
                if (this is CardRunActivity && currentScreen == Screen.CardRun) {
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
            currentScreen == Screen.Resources ||
            currentScreen == Screen.ResourceSearch ||
            currentScreen == Screen.ResourceDetail ||
            currentScreen == Screen.ResourceMore ||
            currentScreen == Screen.ResourceManage

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
        resourceInstallWizardBinding = null
        if (removeRunState && wizardRecipeIds.isNotEmpty()) {
            wizardRecipeIds.forEach { recipeId ->
                runtimeStates.remove(recipeId)
                activeRunInstanceIds.remove(recipeId)
                suppressedResourceRunSurfaceRecipeIds.remove(recipeId)
            }
            CardRunStore.removeRunStatesForRecipes(wizardRecipeIds, removeOpenHistory = true)
        }
    }

    private fun closeResourceInstallTaskIfActivityDestroyed() {
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
        if (this !is CardRunActivity && currentScreen != Screen.CardRun) {
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
        val binding = resourceInstallWizardBinding ?: return false
        if (currentScreen != Screen.CardRun) return false
        if (binding.targetResourceId != targetId || binding.planResourceIds != planIds) return false
        if (focusedRunRecipeId != recipeId || focusedRunInstanceId != instanceId) return false
        if (activeResourceInstallWizard?.selectedSurface != CardRunSurface.InstallWizard) return false
        requestVisibleResourceInstallWizardRefresh(reason)
        return true
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
                resourcePlanStepIsInstalled(id, catalog, entry(id))
            }
            val hasRunningStep = planIds.any { isRunningStep(it) }
            val hasUninstallingStep = uninstallingIds.isNotEmpty()
            val hasPending = pendingIds.isNotEmpty() && !hasFailure
            val primaryActionRenderKey = resourceInstallWizardPrimaryActionRenderKey(
                hasRunningStep = hasRunningStep,
                hasUninstallingStep = hasUninstallingStep,
                hasPending = hasPending,
                hasFailure = hasFailure
            )
            val rowHosts = linkedMapOf<String, LinearLayout>()
            val rowBindings = linkedMapOf<String, ResourceInstallWizardRowBinding>()
            val rowRenderKeys = linkedMapOf<String, String>()
            var headerDetailTextView: TextView? = null
            var headerProgressTextView: TextView? = null

            addView(resourceInstallWizardHeader(
                title = target?.name ?: targetId,
                detail = when {
                    hasRunningStep -> "正在获取：${catalog[activeId]?.name ?: activeId.orEmpty()}"
                    hasUninstallingStep -> "正在卸载：${catalog[activeId]?.name ?: activeId.orEmpty()}"
                    hasFailure -> "发现异常请手动处理"
                    hasPending -> "将按顺序获取 ${planIds.size} 个资源"
                    else -> "执行队列已完成"
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
            }
            val primaryActionButton = resourceInstallWizardPrimaryAction(
                hasRunningStep = hasRunningStep,
                hasUninstallingStep = hasUninstallingStep,
                hasPending = hasPending,
                hasFailure = hasFailure
            )
            primaryActionHost.addView(primaryActionButton)
            addView(primaryActionHost)
            addView(sectionTitle("执行队列").apply { setPadding(0, dp(24), 0, dp(12)) })
            val rowsHost = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            planIds.forEachIndexed { index, resourceId ->
                val item = catalog[resourceId]
                val rowRenderKey = resourceInstallWizardRowRenderKey(
                    index = index,
                    total = planIds.size,
                    item = item,
                    resourceId = resourceId
                )
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
                rowRenderKeys[resourceId] = rowRenderKey
                rowsHost.addView(rowHost)
            }
            addView(rowsHost)
            resourceInstallWizardBinding = ResourceInstallWizardBinding(
                targetResourceId = targetId,
                planResourceIds = planIds,
                headerDetailTextView = headerDetailTextView,
                headerProgressTextView = headerProgressTextView,
                primaryActionRenderKey = primaryActionRenderKey,
                primaryActionButton = primaryActionButton,
                primaryActionHost = primaryActionHost,
                rowsHost = rowsHost,
                rowHosts = rowHosts,
                rowRenderKeys = rowRenderKeys,
                rowBindings = rowBindings
            )
            scheduleForegroundLiveTickIfNeeded()
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

    private fun resourceInstallWizardHeader(
        title: String,
        detail: String,
        completedCount: Int,
        totalCount: Int,
        onBind: ((TextView, TextView, TextView) -> Unit)? = null
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(136))
        setPadding(dp(18), dp(18), dp(18), dp(18))
        minimumHeight = dp(136)
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
    ): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                setMargins(0, dp(16), 0, 0)
            }
            configureResourceInstallWizardPrimaryAction(
                button = this,
                hasRunningStep = hasRunningStep,
                hasUninstallingStep = hasUninstallingStep,
                hasPending = hasPending,
                hasFailure = hasFailure
            )
        }

    private fun resourceInstallWizardPrimaryActionRenderKey(
        hasRunningStep: Boolean,
        hasUninstallingStep: Boolean,
        hasPending: Boolean,
        hasFailure: Boolean
    ): String =
        listOf(hasRunningStep, hasUninstallingStep, hasPending, hasFailure).joinToString("|")

    private fun configureResourceInstallWizardPrimaryAction(
        button: TextView,
        hasRunningStep: Boolean,
        hasUninstallingStep: Boolean,
        hasPending: Boolean,
        hasFailure: Boolean
    ) {
        when {
            hasUninstallingStep -> configureResourceInstallWizardActionButton(
                button = button,
                label = "卸载中",
                enabled = false
            ) {}
            hasFailure -> configureResourceInstallWizardActionButton(
                button = button,
                label = "发现异常请手动处理",
                enabled = false
            ) {}
            else -> configureResourceInstallWizardActionButton(
                button = button,
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
            }
        }
    }

    private fun resourceInstallWizardActionButton(
        label: String,
        danger: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        configureResourceInstallWizardActionButton(
            button = this,
            label = label,
            danger = danger,
            enabled = enabled,
            onClick = onClick
        )
    }

    private fun configureResourceInstallWizardActionButton(
        button: TextView,
        label: String,
        danger: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        button.apply {
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
            isEnabled = enabled
            isClickable = enabled
            setOnClickListener(if (enabled) View.OnClickListener { onClick() } else null)
        }
    }

    private fun resourceInstallWizardRowRenderKey(
        index: Int,
        total: Int,
        item: ResourceItem?,
        resourceId: String
    ): String =
        listOf(
            index.toString(),
            total.toString(),
            resourceId,
            item?.id.orEmpty(),
            item?.name.orEmpty(),
            item?.version.orEmpty(),
            item?.sourceLabel.orEmpty()
        ).joinToString("|")

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
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(10))
            }
            lateinit var numberView: TextView
            lateinit var subtitleTextView: TextView
            lateinit var statusView: TextView
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                numberView = TextView(context).apply {
                    text = (index + 1).toString()
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        setMargins(0, 0, dp(12), 0)
                    }
                }
                addView(numberView)
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
                    subtitleTextView = TextView(context).apply {
                        text = "${item?.sourceLabel ?: "资源"} · ${index + 1}/$total"
                        textSize = 11.5f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(4), 0, 0)
                    }
                    addView(subtitleTextView)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                statusView = TextView(context).apply {
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(dp(12), 0, dp(12), 0)
                    minWidth = dp(58)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply {
                        setMargins(dp(12), 0, 0, 0)
                    }
                }
                addView(statusView)
            })
            val secondaryActionsHost = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(46), dp(10), 0, 0)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            addView(secondaryActionsHost)
            val binding = ResourceInstallWizardRowBinding(
                resourceId = resourceId,
                runOperation = resourceVisibleRunOperation(resourceId, registryEntry),
                rootView = this,
                numberTextView = numberView,
                subtitleTextView = subtitleTextView,
                statusTextView = statusView,
                secondaryActionsHost = secondaryActionsHost
            )
            applyResourceInstallWizardRowState(
                binding = binding,
                index = index,
                total = total,
                item = item,
                resourceId = resourceId,
                isActive = isActive,
                planSnapshot = planSnapshot,
                registryEntry = registryEntry
            )
            onBind?.invoke(binding)
        }
    }

    private fun resourceInstallWizardRowState(
        index: Int,
        total: Int,
        item: ResourceItem?,
        resourceId: String,
        isActive: Boolean,
        planSnapshot: com.kite.app.resources.KiteResourcePlanSnapshot? = null,
        registryEntry: KiteResourceRegistryEntry? = null
    ): ResourceInstallWizardRowState {
        val runOperation = resourceVisibleRunOperation(resourceId, registryEntry)
        val recipeState = resourceRunStateForOperation(resourceId, runOperation)
        val planStepStatus = planSnapshot?.stepStatus(resourceId) ?: resourceInstallStore.planStepStatus(resourceId)
        val uninstalling = registryEntry?.uninstalling == true
        val uninstallFailed = registryEntry?.failed == true &&
            registryEntry.operation == KiteResourceInstallStore.OP_UNINSTALL
        val failed = registryEntry?.failed == true ||
            planStepStatus == KiteResourceInstallStore.PLAN_STEP_FAILED
        val blocked = planStepStatus == KiteResourceInstallStore.PLAN_STEP_BLOCKED
        val running = planStepStatus == KiteResourceInstallStore.PLAN_STEP_RUNNING
        val installed = resourceItemIsInstalled(item) || registryEntry?.installed == true
        val statusLabel = when {
            uninstalling -> "卸载中"
            uninstallFailed || failed -> "需卸载"
            running -> "获取中"
            installed -> "已完成"
            blocked -> "已暂停"
            isActive -> "待获取"
            else -> "待获取"
        }
        val tone = when {
            uninstallFailed || failed -> tokens.danger
            statusLabel == "获取中" -> tokens.primaryStrong
            statusLabel == "卸载中" -> tokens.primaryStrong
            statusLabel == "已完成" -> tokens.success
            else -> tokens.textSecondary
        }
        return ResourceInstallWizardRowState(
            runOperation = runOperation,
            recipeState = recipeState,
            statusLabel = statusLabel,
            tone = tone,
            subtitle = resourceInstallWizardStepSubtitle(item, index, total, recipeState),
            failed = failed || uninstallFailed,
            uninstalling = uninstalling,
            canOpenRunSurface = item != null && recipeState != null,
            secondarySurface = recipeState?.surface
        )
    }

    private fun applyResourceInstallWizardRowState(
        binding: ResourceInstallWizardRowBinding,
        index: Int,
        total: Int,
        item: ResourceItem?,
        resourceId: String,
        isActive: Boolean,
        planSnapshot: com.kite.app.resources.KiteResourcePlanSnapshot? = null,
        registryEntry: KiteResourceRegistryEntry? = null
    ) {
        val state = resourceInstallWizardRowState(
            index = index,
            total = total,
            item = item,
            resourceId = resourceId,
            isActive = isActive,
            planSnapshot = planSnapshot,
            registryEntry = registryEntry
        )
        binding.runOperation = state.runOperation
        binding.rootView.background = roundedBox(
            tokens.cardBackground,
            if (isActive) tokens.primarySoft else tokens.border,
            dp(16).toFloat()
        )
        binding.numberTextView.text = (index + 1).toString()
        binding.numberTextView.setTextColor(state.tone)
        binding.numberTextView.background = roundedBox(tintBackground(state.tone), Color.TRANSPARENT, dp(12).toFloat(), 0)
        binding.subtitleTextView.text = state.subtitle
        binding.statusTextView.text = state.statusLabel
        binding.statusTextView.setTextColor(state.tone)
        binding.statusTextView.background = roundedBox(tintBackground(state.tone), Color.TRANSPARENT, dp(11).toFloat(), 0)
        binding.statusTextView.setOnClickListener(null)
        binding.statusTextView.isClickable = false
        item?.let { resourceItem ->
            binding.rootView.isClickable = true
            binding.rootView.setOnClickListener {
                if (resourceVisibleRunState(resourceId, registryEntry) == null) {
                    showResourceDiscreteToast("报告正在准备")
                } else {
                    openResourceInstallRunSurface(resourceItem, CardRunSurface.Report, state.runOperation)
                }
            }
            if (state.failed && !state.uninstalling) {
                binding.statusTextView.isClickable = true
                binding.statusTextView.setOnClickListener {
                    showResourceWizardUninstallConfirm(resourceItem)
                }
            }
        } ?: run {
            binding.rootView.setOnClickListener(null)
            binding.rootView.isClickable = false
        }
        val secondaryActionsRenderKey = listOf(
            item?.id.orEmpty(),
            state.runOperation,
            state.secondarySurface?.name.orEmpty(),
            state.canOpenRunSurface.toString()
        ).joinToString("|")
        if (binding.secondaryActionsRenderKey != secondaryActionsRenderKey) {
            binding.secondaryActionsHost.removeAllViews()
            if (
                item != null &&
                state.canOpenRunSurface &&
                (state.secondarySurface == CardRunSurface.Terminal || state.secondarySurface == CardRunSurface.Web)
            ) {
                if (state.secondarySurface == CardRunSurface.Terminal) {
                    binding.secondaryActionsHost.addView(resourceWizardInlineButton("打开终端") {
                        openResourceInstallRunSurface(item, CardRunSurface.Terminal, state.runOperation)
                    })
                }
                if (state.secondarySurface == CardRunSurface.Web) {
                    binding.secondaryActionsHost.addView(resourceWizardInlineButton("打开网页") {
                        openResourceInstallRunSurface(item, CardRunSurface.Web, state.runOperation)
                    })
                }
                binding.secondaryActionsHost.visibility = View.VISIBLE
            } else {
                binding.secondaryActionsHost.visibility = View.GONE
            }
            binding.secondaryActionsRenderKey = secondaryActionsRenderKey
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
            requestVisibleResourceInstallWizardRefresh("wizard_uninstall_no_recipe:${item.id}")
            refreshResourceScreenIfVisible()
            return
        }
        startResourceUninstall(item, recipe, ResourceUninstallContinuation.ResumeInstallWizard)
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
        if (!resourceInstallWizardSurfaceActive() || currentScreen != Screen.CardRun) return
        if (resourceInstallWizardRefreshPosted) return
        resourceInstallWizardRefreshPosted = true
        root.post {
            resourceInstallWizardRefreshPosted = false
            val binding = resourceInstallWizardBinding ?: return@post
            if (!resourceInstallWizardSurfaceActive() || currentScreen != Screen.CardRun) return@post
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
            resourcePlanStepIsInstalled(id, catalog, entry(id))
        }
        val hasRunningStep = planIds.any { isRunningStep(it) }
        val hasUninstallingStep = uninstallingIds.isNotEmpty()
        val hasPending = pendingIds.isNotEmpty() && !hasFailure
        val detail = when {
            hasRunningStep -> "正在获取：${catalog[activeId]?.name ?: activeId.orEmpty()}"
            hasUninstallingStep -> "正在卸载：${catalog[activeId]?.name ?: activeId.orEmpty()}"
            hasFailure -> "发现异常请手动处理"
            hasPending -> "将按顺序获取 ${planIds.size} 个资源"
            else -> "执行队列已完成"
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
        val primaryActionRenderKey = resourceInstallWizardPrimaryActionRenderKey(
            hasRunningStep = uiState.hasRunningStep,
            hasUninstallingStep = uiState.hasUninstallingStep,
            hasPending = uiState.hasPending,
            hasFailure = uiState.hasFailure
        )
        if (binding.primaryActionRenderKey != primaryActionRenderKey) {
            configureResourceInstallWizardPrimaryAction(
                button = binding.primaryActionButton,
                hasRunningStep = uiState.hasRunningStep,
                hasUninstallingStep = uiState.hasUninstallingStep,
                hasPending = uiState.hasPending,
                hasFailure = uiState.hasFailure
            )
            binding.primaryActionRenderKey = primaryActionRenderKey
        }
        if (binding.planResourceIds != uiState.planIds) {
            binding.planResourceIds = uiState.planIds
            binding.rowHosts.clear()
            binding.rowBindings.clear()
            binding.rowRenderKeys.clear()
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
        val item = uiState.catalog[resourceId]
        val registryEntry = uiState.registrySnapshot[resourceId]
        val renderKey = resourceInstallWizardRowRenderKey(
            index = index,
            total = uiState.planIds.size,
            item = item,
            resourceId = resourceId
        )
        binding.rowBindings[resourceId]?.let { rowBinding ->
            if (binding.rowRenderKeys[resourceId] == renderKey) {
                applyResourceInstallWizardRowState(
                    binding = rowBinding,
                    index = index,
                    total = uiState.planIds.size,
                    item = item,
                    resourceId = resourceId,
                    isActive = resourceId == uiState.activeId,
                    planSnapshot = uiState.planSnapshot,
                    registryEntry = registryEntry
                )
                return
            }
        }
        binding.rowRenderKeys[resourceId] = renderKey
        binding.rowBindings.remove(resourceId)
        host.removeAllViews()
        host.addView(resourceInstallWizardStepRow(
            index = index,
            total = uiState.planIds.size,
            item = item,
            resourceId = resourceId,
            isActive = resourceId == uiState.activeId,
            planSnapshot = uiState.planSnapshot,
            registryEntry = registryEntry,
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
        item.stateLabel == "已安装" || item.stateLabel == "已获取" || item.stateLabel == "运行中"

    private fun resourceItemIsInstalled(item: ResourceItem?): Boolean =
        item?.stateLabel == "已安装" || item?.stateLabel == "已获取" || item?.stateLabel == "运行中"

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
            requestVisibleResourceItemStatePatch("resource_open_stop_missing", listOf(item.id))
            showResourceDiscreteToast("${item.name} 没有运行中的实例")
            return
        }
        focusedRunRecipeId = recipe.id
        focusedRunInstanceId = state.instanceId
        activeRunInstanceIds[recipe.id] = state.instanceId
        runtimeStates[recipe.id] = state
        stopRecipe(recipe, state)
        invalidateResourceRuntimeStateCache()
        requestVisibleResourceItemStatePatch("resource_open_stop", listOf(item.id))
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

    private fun resourceOpenRunLabels(resourceId: String): ResourceRuntimeLabels? =
        CardRunStore.currentForRecipe(KiteResourceInstallRecipes.recipeId(resourceId, "open"))
            ?.let { state ->
                when (state.status) {
                    RecipeRunStatus.Starting -> ResourceRuntimeLabels("启动中", "启动中", busy = true)
                    RecipeRunStatus.Stopping -> ResourceRuntimeLabels("停止中", "停止中", busy = true)
                    RecipeRunStatus.WaitingTerminal -> ResourceRuntimeLabels("等待终端", "运行中", busy = false)
                    RecipeRunStatus.Running,
                    RecipeRunStatus.AlreadyRunning,
                    RecipeRunStatus.Opened -> ResourceRuntimeLabels("运行中", "运行中", busy = false)
                    else -> null
                }
            }

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
            resourceSectionsDirty = true
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
            KiteResourceInstallRecipes.OP_INSTALL -> bundledToolchainManifestInstallCommand(item, action.cmd)
                ?: KiteResourceInstallRecipes.manifestInstallCommand(
                    resourceId = item.id,
                    displayName = item.name,
                    rawCommand = action.cmd,
                    managedCommands = action.managedCommands,
                    cleanInstallRoot = action.cleanInstallRoot
                )
            KiteResourceInstallRecipes.OP_UNINSTALL -> KiteResourceInstallRecipes.manifestUninstallCommand(
                resourceId = item.id,
                rawCommand = action.cmd,
                managedCommands = action.managedCommands,
                npmUninstallPackages = action.npmUninstallPackages
            )
            else -> action.cmd
        }

    private fun bundledToolchainManifestInstallCommand(item: ResourceItem, command: String): String? {
        if (!item.isBundledResource()) return null
        val trimmed = command.trim()
        if (trimmed != "install.sh" && !trimmed.startsWith("install.sh ")) return null
        val mode = trimmed.removePrefix("install.sh").trim().ifBlank { "--install" }
        if (!Regex("""--?[A-Za-z0-9][A-Za-z0-9_-]*""").matches(mode)) return null
        return KiteResourceInstallRecipes.localToolchainCommand(item.id, mode)
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
        requestVisibleResourceItemStatePatch("resource_install_start", listOf(item.id))
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
        requestVisibleResourceItemStatePatch("resource_uninstall_start", listOf(item.id))
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
            Screen.Resources -> {
                if (resourceSectionHost != null) {
                    requestVisibleResourceItemStatePatch("visible_refresh")
                } else {
                    showResources()
                }
            }
            Screen.ResourceSearch -> {
                if (resourceSearchContentHost != null) {
                    requestVisibleResourceItemStatePatch("visible_refresh")
                } else {
                    showResourceSearch()
                }
            }
            Screen.ResourceDetail -> requestVisibleResourceDetailStatePatch("visible_refresh")
            Screen.ResourceMore -> invalidateResourceRuntimeStateCache()
            Screen.ResourceManage -> {
                if (resourceManageContentHost != null) {
                    requestResourceManageRefresh(forceCatalogRefresh = false, reason = "visible_refresh")
                } else {
                    showResourceManage()
                }
            }
            else -> Unit
        }
    }

    private fun settleVisibleResourceMutation(reason: String) {
        if (this is CardRunActivity || !::root.isInitialized) return
        when (currentScreen) {
            Screen.Resources,
            Screen.ResourceSearch,
            Screen.ResourceDetail,
            Screen.ResourceMore,
            Screen.ResourceManage -> refreshResourceScreenIfVisible()
            else -> showResources()
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
                            handleResourceInstallAction(item)
                        }
                    }
                    ResourceUninstallContinuation.CancelFailedInstall -> {
                        resourceInstallStore.clearPlan()
                        closeResourceInstallWizardInstance(resourceId, removeRunState = true)
                        showResourceDiscreteToast("残留已卸载，获取任务已取消")
                        if (this is CardRunActivity && currentScreen == Screen.CardRun) {
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
            registryEntry?.installing == true -> KiteResourceInstallStore.OP_INSTALL
            registryEntry?.uninstalling == true -> KiteResourceInstallStore.OP_UNINSTALL
            else -> return false
        }
        if (operation == KiteResourceInstallStore.OP_INSTALL && registryEntry.bootstrapInstallStillRunning()) {
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
        fun installing(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.installing == true
        fun uninstalling(resourceId: String): Boolean =
            registrySnapshot[resourceId]?.uninstalling == true
        fun failedOperation(resourceId: String): String =
            registrySnapshot[resourceId]?.operation.orEmpty()
        fun planStepStatus(resourceId: String): String =
            planSnapshot.stepStatus(KiteResourceInstallRecipes.safeId(resourceId))
        fun planContainsResource(resourceId: String): Boolean {
            val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
            return cleanId.isNotBlank() &&
                (cleanId == planSnapshot.targetResourceId || cleanId in planSnapshot.resourceIds)
        }
        fun installPlanFailed(resourceId: String): Boolean {
            if (!planContainsResource(resourceId)) return false
            val status = planStepStatus(resourceId)
            if (
                status == KiteResourceInstallStore.PLAN_STEP_FAILED ||
                status == KiteResourceInstallStore.PLAN_STEP_BLOCKED
            ) return true
            val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
            return cleanId == planSnapshot.targetResourceId &&
                planSnapshot.resourceIds.any { id ->
                    val stepStatus = planStepStatus(id)
                    stepStatus == KiteResourceInstallStore.PLAN_STEP_FAILED ||
                        stepStatus == KiteResourceInstallStore.PLAN_STEP_BLOCKED
                }
        }
        fun installPlanBusy(resourceId: String): Boolean {
            if (!planContainsResource(resourceId) || installPlanFailed(resourceId)) return false
            val status = planStepStatus(resourceId)
            if (status == KiteResourceInstallStore.PLAN_STEP_RUNNING) return true
            if (status == KiteResourceInstallStore.PLAN_STEP_DONE) return false
            if (status.isNotBlank()) return true
            val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
            return cleanId == planSnapshot.targetResourceId &&
                (planSnapshot.pendingResourceIds.isNotEmpty() || planSnapshot.runningResourceIds.isNotEmpty())
        }
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
        fun labelsForResource(resourceId: String, idleLabel: String = "未获取"): ResourceRuntimeLabels {
            val recordedInstalled = recordedInstalled(resourceId)
            val planBusy = installPlanBusy(resourceId)
            val installing = installing(resourceId) || planBusy
            val uninstalling = uninstalling(resourceId)
            val failed = installFailed(resourceId) || installPlanFailed(resourceId)
            val failedOperation = failedOperation(resourceId)
                .ifBlank { if (failed) KiteResourceInstallStore.OP_INSTALL else "" }
            val openRunLabels = if (recordedInstalled && !installing && !uninstalling && !failed) {
                resourceOpenRunLabels(resourceId)
            } else {
                null
            }
            return openRunLabels ?: ResourceRuntimeLabels(
                state = stateLabelForResource(recordedInstalled, installing, uninstalling, failed, failedOperation, idleLabel),
                action = actionLabelForResource(recordedInstalled, installing, uninstalling, failed, failedOperation),
                busy = busy(resourceId) || planBusy
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
        fun labelsForManifest(manifest: KiteResourceManifest): ResourceRuntimeLabels {
            if (manifest.id != RESOURCE_NODE_RUNTIME) {
                return labelsForResource(manifest.id, resourceIdleLabelForManifest(manifest))
            }
            val recordedInstalled = recordedInstalled(RESOURCE_NODE_RUNTIME)
            val nodeInstalled = recordedInstalled || nodeWorkspaceInstalled
            val planBusy = installPlanBusy(RESOURCE_NODE_RUNTIME)
            val installing = toolchainRunning || installing(RESOURCE_NODE_RUNTIME) || planBusy
            val uninstalling = uninstalling(RESOURCE_NODE_RUNTIME)
            val failed = installFailed(RESOURCE_NODE_RUNTIME) || installPlanFailed(RESOURCE_NODE_RUNTIME)
            val failedOperation = failedOperation(RESOURCE_NODE_RUNTIME)
                .ifBlank { if (failed) KiteResourceInstallStore.OP_INSTALL else "" }
            val openRunLabels = if (nodeInstalled && !installing && !uninstalling && !failed) {
                resourceOpenRunLabels(RESOURCE_NODE_RUNTIME)
            } else {
                null
            }
            if (openRunLabels != null) {
                return openRunLabels.copy(busy = openRunLabels.busy || toolchainRunning)
            }
            return ResourceRuntimeLabels(
                state = stateLabelForResource(nodeInstalled, installing, uninstalling, failed, failedOperation, "本地包"),
                action = actionLabelForResource(nodeInstalled, installing, uninstalling, failed, failedOperation),
                busy = busy(RESOURCE_NODE_RUNTIME) || toolchainRunning || planBusy
            )
        }
        val catalog = visibleManifests.map { manifest ->
            val labels = labelsForManifest(manifest)
            resourceItemFromManifest(
                manifest = manifest,
                labels = labels,
                actionEnabled = resourceActionEnabled(labels.action, labels.busy)
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
        actionEnabled: Boolean
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
            actionEnabled = actionEnabled,
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
        action.cmd.lineSequence()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .take(160)

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

        addView(consolePageTabs())
    }

    private fun consolePageTabs(): View = row {
        setPadding(0, dp(12), 0, 0)
        val pages = consolePages()
        val selectedId = consolePageId.takeIf { selected -> pages.any { it.id == selected } } ?: CONSOLE_PAGE_ALL
        consolePageId = selectedId
        addView(TabLayout(context).apply {
            consolePageTabsView = this
            contentDescription = "配置分页"
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setBackgroundColor(Color.TRANSPARENT)
            setSelectedTabIndicatorColor(tokens.primaryStrong)
            setTabTextColors(tokens.textSecondary, tokens.primaryStrong)
            pages.forEach { page ->
                addTab(newTab().setText(page.label).setTag(page.id), page.id == selectedId)
            }
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    selectConsolePage(tab.tag as? String ?: CONSOLE_PAGE_ALL)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(TextView(context).apply {
            text = "+"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            background = roundedBox(tokens.surface, tokens.border, dp(18).toFloat())
            setOnClickListener { showAddConsolePageDialog() }
        }, LinearLayout.LayoutParams(dp(42), dp(38)).apply {
            setMargins(dp(8), dp(5), 0, 0)
        })
    }

    private fun consolePages(): List<ConsolePage> {
        val opened = currentRecipes.count { isConsoleOpenedRecipe(it) }
        val stopped = currentRecipes.count { isConsoleStoppedRecipe(it) }
        val base = listOf(
            ConsolePage(CONSOLE_PAGE_ALL, "▦  全部 ${currentRecipes.size}"),
            ConsolePage(CONSOLE_PAGE_OPENED, "▶  已打开 $opened"),
            ConsolePage(CONSOLE_PAGE_STOPPED, "■  已停止 $stopped")
        )
        return base + cardGroupStore.groups().map { group ->
            ConsolePage(consoleGroupPageId(group.id), group.name)
        }
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
        val statusPill = systemStatusPill()
        addView(statusPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply {
            setMargins(dp(10), dp(2), 0, 0)
        })
        val openPanel = { showUbuntuRuntimePanel(auto = false, anchor = statusPill) }
        setOnClickListener { openPanel() }
        statusPill.setOnClickListener { openPanel() }
    }

    private fun consolePageBody(): View {
        val recipes = recipesForConsolePage()
        return if (recipes.isEmpty() && consolePageId != CONSOLE_PAGE_ALL) {
            emptyConsolePage(consolePages().firstOrNull { it.id == consolePageId }?.label.orEmpty())
        } else {
            recipeGrid(recipes)
        }.withConsolePageSwipe()
    }

    private fun recipesForConsolePage(): List<KiteRecipe> =
        when (consolePageId) {
            CONSOLE_PAGE_ALL -> currentRecipes
            CONSOLE_PAGE_OPENED -> currentRecipes.filter { isConsoleOpenedRecipe(it) }
            CONSOLE_PAGE_STOPPED -> currentRecipes.filter { isConsoleStoppedRecipe(it) }
            else -> consoleGroupId(consolePageId)
                ?.let { groupId -> currentRecipes.filter { recipe -> recipeInGroup(recipe, groupId) } }
                ?: currentRecipes
        }

    private fun isConsoleOpenedRecipe(recipe: KiteRecipe): Boolean =
        when (runtimeStateFor(recipe).status) {
            RecipeRunStatus.Starting,
            RecipeRunStatus.Running,
            RecipeRunStatus.WaitingTerminal,
            RecipeRunStatus.AlreadyRunning,
            RecipeRunStatus.Opened,
            RecipeRunStatus.Stopping -> true
            else -> false
        }

    private fun isConsoleStoppedRecipe(recipe: KiteRecipe): Boolean =
        when (runtimeStateFor(recipe).status) {
            RecipeRunStatus.Stopped,
            RecipeRunStatus.Completed,
            RecipeRunStatus.Failed,
            RecipeRunStatus.BridgeUnavailable -> true
            else -> false
        }

    private fun renderConsolePageBody() {
        renderConsolePageBody(consolePageBodyHost ?: return)
    }

    private fun renderConsolePageBody(host: FrameLayout) {
        consoleCardBindings.clear()
        host.removeAllViews()
        host.addView(consolePageBody(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun View.withConsolePageSwipe(): View {
        val page = this
        return object : FrameLayout(this@MainActivity) {
            private var downX = 0f
            private var downY = 0f

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (kotlin.math.abs(dx) > dp(54) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                            selectConsolePageOffset(if (dx < 0) 1 else -1)
                            return true
                        }
                    }
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            addView(page, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun selectConsolePageOffset(offset: Int) {
        val pages = consolePages()
        val index = pages.indexOfFirst { it.id == consolePageId }
        val next = (index.takeIf { it >= 0 } ?: 0) + offset
        pages.getOrNull(next)?.let { selectConsolePage(it.id) }
    }

    private fun selectConsolePage(pageId: String) {
        if (consolePageId == pageId) return
        consolePageId = pageId
        consolePageTabsView?.let { tabs ->
            for (index in 0 until tabs.tabCount) {
                val tab = tabs.getTabAt(index) ?: continue
                if (tab.tag == pageId && tabs.selectedTabPosition != index) {
                    tabs.selectTab(tab)
                    break
                }
            }
        }
        renderConsolePageBody()
    }

    private fun emptyConsolePage(label: String): View =
        FrameLayout(this).apply {
            addView(TextView(context).apply {
                text = label.removePrefix("▶  ").removePrefix("■  ").ifBlank { "分页" }
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(tokens.textTertiary)
                background = roundedBox(tokens.surface, tokens.border, dp(22).toFloat())
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88), Gravity.TOP).apply {
                setMargins(dp(18), dp(16), dp(18), 0)
            })
        }

    private fun showAddConsolePageDialog() {
        showCardGroupDialog(selectedGroupId = consoleGroupId(consolePageId).orEmpty()) { group ->
            consolePageId = consoleGroupPageId(group.id)
            showConsole()
        }
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
            state.requiresPermission -> tokens.primaryStrong
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
        if (refreshVisibleCardRunSurfaceInsteadOfRebuild(surfaceSignature, state, surfaceState)) return
        currentScreen = Screen.CardRun
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
            showCardRunWebView(surfaceHost, actionRecipe, webUrl)
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
            surfaceHost.addView(ScrollView(this).apply {
                addView(resourceInstallWizardContent())
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
            activeResourceInstallWizard?.selectedSurface?.name.orEmpty()
        ).joinToString("|")

    private fun refreshVisibleCardRunSurfaceInsteadOfRebuild(
        surfaceSignature: String,
        state: RecipeRuntimeState,
        surfaceState: RecipeRuntimeState
    ): Boolean {
        if (currentScreen != Screen.CardRun || cardRunSurfaceSignature != surfaceSignature) return false
        when (state.surface) {
            CardRunSurface.InstallWizard -> requestVisibleResourceInstallWizardRefresh("same_card_surface")
            CardRunSurface.Report -> updateVisibleCardRunReport(surfaceState)
            else -> Unit
        }
        return true
    }

    private fun showCardRunLoadingSurface(recipe: KiteRecipe, message: String) {
        currentScreen = Screen.CardRun
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

    private fun showCardRunWebView(parentHost: FrameLayout, recipe: KiteRecipe, url: String) {
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
        val parent = webView.parent
        if (parent is ViewGroup) parent.removeView(webView)
        parentHost.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webShell.loadInWebView(target, recipeId = recipe.id, recipeName = recipe.name, openSource = "card_run_surface")
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
            mapOf("instanceId" to instanceId, "url" to url.take(500))
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
                addView(cardRunStatusBadge(state), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)).apply {
                    setMargins(dp(12), 0, 0, 0)
                })
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
        elapsedTextView: TextView? = null
    ) {
        val current = cardRunReportBinding?.takeIf { it.instanceId == state.instanceId }
        cardRunReportBinding = CardRunReportBinding(
            recipeId = recipeId,
            instanceId = state.instanceId,
            outputTextView = outputTextView ?: current?.outputTextView,
            outputScrollView = outputScrollView ?: current?.outputScrollView,
            footerTextView = footerTextView ?: current?.footerTextView,
            elapsedTextView = elapsedTextView ?: current?.elapsedTextView
        )
        scheduleForegroundLiveTickIfNeeded()
    }

    private fun updateVisibleCardRunReport(state: RecipeRuntimeState) {
        val binding = cardRunReportBinding ?: return
        if (binding.instanceId != state.instanceId || currentScreen != Screen.CardRun) return
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
        if (binding.instanceId != state.instanceId || currentScreen != Screen.CardRun) return
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
        stopRecipe(recipe, latestRootState)
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
                if (currentScreen == Screen.CardRun && focusedRunInstanceId == instanceId) {
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

    private fun shouldStopRunBeforeDelete(state: RecipeRuntimeState): Boolean =
        state.status == RecipeRunStatus.Opened ||
            state.isBusy() ||
            state.isActive() ||
            shouldStopRunWhenClosingInstance(state)

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
            addView(iconButton("‹", dp(42), Color.TRANSPARENT, tokens.textPrimary, dp(16)) { showConsole() })
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

    private fun runManagementStatusColors(status: RecipeRunStatus): SemanticColors = when (status) {
        RecipeRunStatus.Starting,
        RecipeRunStatus.WaitingTerminal -> SemanticColors(tokens.info, tokens.infoSoft, tokens.infoBorder)
        RecipeRunStatus.Running,
        RecipeRunStatus.AlreadyRunning,
        RecipeRunStatus.Opened -> SemanticColors(tokens.success, tokens.successSoft, tokens.successBorder)
        RecipeRunStatus.Stopping,
        RecipeRunStatus.Failed,
        RecipeRunStatus.BridgeUnavailable -> SemanticColors(tokens.warning, tokens.warningSoft, tokens.warningBorder)
        RecipeRunStatus.Completed,
        RecipeRunStatus.Stopped,
        RecipeRunStatus.Unknown -> SemanticColors(tokens.textSecondary, tokens.surface, tokens.border)
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
        stopRecipe(recipe, group.run)
        root.postDelayed({ if (!isFinishing && !isDestroyed) showKiteProcessOverview(forceRefresh = true) }, 260L)
    }

    private fun endRunManagementTerminal(terminal: TerminalSessionItem) {
        TerminalSessionStore.end(applicationContext, terminal.id)
        Toast.makeText(this, "正在结束终端", Toast.LENGTH_SHORT).show()
        requestRuntimePanelSummaryRefresh(force = true)
        root.postDelayed({ if (currentScreen == Screen.Processes) showKiteProcessOverview(forceRefresh = false) }, 260L)
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
        if (currentScreen == Screen.Processes) showKiteProcessOverview(forceRefresh = false)
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
                if (currentScreen != Screen.Processes || isFinishing || isDestroyed) return@postDelayed
                requestRuntimePanelSummaryRefresh(force = false)
                root.postDelayed({
                    if (currentScreen == Screen.Processes && !isFinishing && !isDestroyed) {
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

    private fun recipeGrid(recipes: List<KiteRecipe> = currentRecipes): View {
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
        recipes.forEach { recipe ->
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
        recipeGroupLabel(recipe).ifBlank { "未分组" }

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

    private fun recipeUsesResourceInlineStatus(recipe: KiteRecipe): Boolean =
        recipe.runtimeSource == KiteResourceInstallRecipes.RUNTIME_SOURCE ||
            recipe.runtimeSource == RESOURCE_OPEN_RUNTIME_SOURCE ||
            recipe.runtimeSource == RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE ||
            resourceIdForRecipe(recipe) != null

    private fun resourceSurfaceHasInlineRuntimeStatus(): Boolean {
        if (resourceInstallWizardSurfaceActive()) return true
        return when (currentScreen) {
            Screen.Resources,
            Screen.ResourceSearch,
            Screen.ResourceDetail,
            Screen.ResourceMore,
            Screen.ResourceManage -> true
            Screen.CardRun -> focusedRunRecipe()?.let { recipe ->
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

    private fun requestResourceRuntimeInlineRefresh(recipe: KiteRecipe, reason: String) {
        invalidateResourceRuntimeStateCache()
        val resourceId = resourceIdForRecipe(recipe)
        if (resourceInstallWizardShouldHost(recipe)) {
            requestVisibleResourceInstallWizardRefresh(reason)
        }
        requestVisibleResourceItemStatePatch(reason, listOfNotNull(resourceId))
        if (currentScreen == Screen.ResourceDetail && resourceId == currentResourceDetailId) {
            requestVisibleResourceDetailStatePatch(reason)
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
        val state = runtimeStateFor(recipe)
        diagnostics.logRecipeAction(recipe, "card_click", mapOf("type" to recipe.type, "status" to state.status.name))
        if (state.status == RecipeRunStatus.Starting || state.status == RecipeRunStatus.Stopping) return
        if (isUbuntuActionBlocked(recipe)) {
            ensureKfRuntimeBootstrap()
            if (shouldSuppressTransientRuntimeChrome()) {
                requestResourceRuntimeInlineRefresh(recipe, "ubuntu_runtime_blocked")
            } else {
                showUbuntuRuntimePanel(auto = true)
                Toast.makeText(this, ubuntuRuntimeState.title, Toast.LENGTH_SHORT).show()
            }
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
        KiteRecipe.STEP_X11 -> CardRunSurface.X11
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
            invalidateResourceRuntimeStateCache()
            requestVisibleResourceItemStatePatch("resource_run_suppressed", listOfNotNull(resourceIdForRecipe(recipe)))
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
                    currentScreen == Screen.CardRun ||
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
                    if (!openSurface || (currentScreen == Screen.CardRun && focusedRunInstanceId == instanceId)) {
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
                    if (currentScreen == Screen.CardRun && focusedRunInstanceId == instanceId) {
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

    private fun stopRecipe(recipe: KiteRecipe, previousState: RecipeRuntimeState) {
        stopRecipeByCardInstanceId(recipe, previousState.cardInstanceId, previousState)
    }

    private fun stopRecipeByCardInstanceId(
        recipe: KiteRecipe,
        cardInstanceId: String,
        fallbackState: RecipeRuntimeState? = null
    ) {
        val previousState = CardRunStore.get(cardInstanceId)
            ?: fallbackState?.takeIf { it.recipeId == recipe.id }
            ?: CardRunStore.currentForRecipe(recipe.id)
            ?: return
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
                showConsole()
                val callback: (BridgeResult) -> Unit = { result -> runOnUiThread { handleStopResultV2(recipe, previousState, result) } }
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
            showConsole()
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
        retriedAfterStableBridge: Boolean = false
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
            showConsole()
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
                            instanceId = previousState.instanceId,
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
        showConsole()
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
        selectedGroupId = draft?.groupId ?: recipe?.groupId.orEmpty()
        if (selectedIconType != KiteRecipeIcon.TYPE_IMAGE || selectedIconSource.isBlank()) {
            selectedIconType = KiteRecipeIcon.TYPE_BUILTIN
            selectedIconSource = ""
            selectedIconName = KiteRecipeIcon.normalizeName(selectedIconName, selectedType)
        }
        formShortcutRequested = draft?.shortcutRequested ?: false
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
                    addView(cardGroupConfigRow())
                } else {
                    addView(formDivider())
                    addView(navigationRow("启动配置") { showRecipeFormMoreMenu() }.apply {
                        setPadding(0, dp(16), 0, dp(8))
                    })
                    addView(cardGroupConfigRow())
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

    private fun cardGroupConfigRow(): View = row {
        setPadding(0, dp(16), 0, dp(4))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            showCardGroupDialog(selectedGroupId) { group ->
                selectedGroupId = group.id
                groupSelectionDetailView?.text = formGroupLabel()
            }
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "所属分组"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            groupSelectionDetailView = TextView(context).apply {
                text = formGroupLabel()
                textSize = 11f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(3), dp(8), 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(groupSelectionDetailView)
        })
        addView(TextView(context).apply {
            text = "选择"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            background = roundedBox(tokens.primarySubtle, tokens.primarySoft, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(66), dp(34))
        })
    }

    private fun showCardGroupDialog(selectedGroupId: String, onSelected: (KiteCardGroup) -> Unit) {
        val dialog = Dialog(this)
        val input = EditText(this).apply {
            hint = "新建分组"
            textSize = 13f
            setSingleLine(true)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBox(tokens.inputBackground, tokens.border, dp(12).toFloat())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            addView(TextView(context).apply {
                text = "选择分组"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ScrollView(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val groups = cardGroupStore.groups()
                    if (groups.isEmpty()) {
                        addView(TextView(context).apply {
                            text = "还没有分组"
                            textSize = 13f
                            gravity = Gravity.CENTER
                            setTextColor(tokens.textTertiary)
                            setPadding(0, dp(24), 0, dp(24))
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    } else {
                        groups.forEach { group ->
                            addView(cardGroupDialogRow(group, group.id == selectedGroupId) {
                                onSelected(group)
                                dialog.dismiss()
                            })
                        }
                    }
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)).apply {
                setMargins(0, dp(14), 0, dp(14))
            })
            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(input, LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(TextView(context).apply {
                    text = "新建"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(Color.WHITE)
                    background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(14).toFloat())
                    setOnClickListener {
                        val name = input.text?.toString()?.trim().orEmpty()
                        if (name.isBlank()) {
                            Toast.makeText(context, "请输入分组名", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val group = cardGroupStore.create(name)
                        onSelected(group)
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(dp(72), dp(44)).apply {
                    setMargins(dp(10), 0, 0, 0)
                })
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun cardGroupDialogRow(group: KiteCardGroup, selected: Boolean, onClick: () -> Unit): View =
        TextView(this).apply {
            text = group.name
            textSize = 14f
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (selected) tokens.primaryStrong else tokens.textPrimary)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBox(
                if (selected) tokens.primarySubtle else tokens.surface,
                if (selected) tokens.primarySoft else tokens.border,
                dp(14).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                setMargins(0, 0, 0, dp(8))
            }
            setOnClickListener { onClick() }
        }

    private fun formGroupLabel(): String =
        cardGroupName(selectedGroupId)
            ?: KiteRecipe.normalizeCategory(editingRecipe?.category).ifBlank { "未分组" }

    private fun recipeGroupLabel(recipe: KiteRecipe): String =
        cardGroupName(recipe.groupId)
            ?: KiteRecipe.normalizeCategory(recipe.category)

    private fun cardGroupName(groupId: String): String? =
        groupId.takeIf { it.isNotBlank() }
            ?.let { id -> cardGroupStore.groups().firstOrNull { it.id == id }?.name }

    private fun recipeInGroup(recipe: KiteRecipe, groupId: String): Boolean {
        if (recipe.groupId == groupId) return true
        val groupName = cardGroupName(groupId) ?: return false
        return recipe.groupId.isBlank() && KiteRecipe.normalizeCategory(recipe.category) == groupName
    }

    private fun consoleGroupPageId(groupId: String): String =
        "$CONSOLE_PAGE_GROUP_PREFIX$groupId"

    private fun consoleGroupId(pageId: String): String? =
        pageId.takeIf { it.startsWith(CONSOLE_PAGE_GROUP_PREFIX) }
            ?.removePrefix(CONSOLE_PAGE_GROUP_PREFIX)
            ?.takeIf { it.isNotBlank() }

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

    private fun recipeIconSourceExists(source: String): Boolean {
        val normalized = source.trim().trimStart('/')
        if (normalized.isBlank() || normalized.contains("..")) return false
        return recipeIconFile(source).exists() ||
            runCatching {
                assets.open(normalized).use { true }
            }.getOrDefault(false)
    }

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
            if (current.groupId != baseline.groupId) add("所属分组")
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
            groupId = selectedGroupId,
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
                groupId = "",
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
            groupId = recipe.groupId,
            shortcutRequested = false,
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

    private fun handleShortcutRequestAction() {
        val existingRecipe = editingRecipe
        if (existingRecipe == null || existingRecipe.id.isBlank()) {
            formShortcutRequested = true
            Toast.makeText(this, "保存后会申请桌面图标", Toast.LENGTH_SHORT).show()
            return
        }

        requestShortcutForRecipe(shortcutRecipeForCurrentForm(existingRecipe))
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

    private fun shortcutRecipeForCurrentForm(existingRecipe: KiteRecipe): KiteRecipe {
        val draft = snapshotRecipeFormDraft()
        val normalizedSteps = draft?.steps?.map { it.normalizedCopy() }.orEmpty()
        val inferredType = normalizedSteps
            .takeIf { it.isNotEmpty() }
            ?.let { inferTypeFromDrafts(it) }
            ?: existingRecipe.type
        val iconType = draft?.selectedIconType?.takeIf { it.isNotBlank() }
            ?: selectedIconType.takeIf { it.isNotBlank() }
            ?: existingRecipe.icon.type
        val iconSource = draft?.selectedIconSource?.takeIf { it.isNotBlank() }
            ?: selectedIconSource.takeIf { it.isNotBlank() }
            ?: existingRecipe.icon.source
        val iconName = draft?.selectedIconName?.takeIf { it.isNotBlank() }
            ?: selectedIconName.takeIf { it.isNotBlank() }
            ?: existingRecipe.icon.name
        val icon = if (iconType == KiteRecipeIcon.TYPE_IMAGE && iconSource.isNotBlank()) {
            KiteRecipeIcon(
                type = KiteRecipeIcon.TYPE_IMAGE,
                name = iconName.ifBlank { "custom" },
                source = iconSource
            )
        } else {
            KiteRecipeIcon(
                type = KiteRecipeIcon.TYPE_BUILTIN,
                name = KiteRecipeIcon.normalizeName(iconName, inferredType)
            )
        }
        val group = cardGroupStore.groups().firstOrNull { it.id == (draft?.groupId ?: selectedGroupId) }

        return existingRecipe.copy(
            name = draft?.name?.trim()?.ifBlank { existingRecipe.name } ?: existingRecipe.name,
            description = draft?.description?.trim()?.ifBlank { existingRecipe.description }
                ?: existingRecipe.description,
            type = inferredType,
            category = group?.name ?: existingRecipe.category,
            groupId = group?.id ?: existingRecipe.groupId,
            defaultUrl = normalizedSteps
                .firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB && it.url.isNotBlank() }
                ?.url
                ?: existingRecipe.defaultUrl,
            icon = icon,
            launch = KiteLaunchConfig(openInstance = draft?.launchOpenInstance ?: formLaunchOpenInstance)
        )
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
        val group = cardGroupStore.groups().firstOrNull { it.id == (draft?.groupId ?: selectedGroupId) }

        runCatching {
            recipeLoader.saveUserRecipe(
                NewRecipeInput(
                    id = editingRecipe?.id,
                    type = inferredType,
                    name = name,
                    category = group?.name ?: editingRecipe?.category.orEmpty(),
                    groupId = group?.id ?: editingRecipe?.groupId.orEmpty(),
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
            if (requestShortcut) requestShortcutForRecipe(savedRecipe)
            Toast.makeText(this, "已保存配置", Toast.LENGTH_SHORT).show()
            editingRecipe = null
            clearRecipeDraftState()
            showConsole()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecipeRawJson(recipe: KiteRecipe) {
        // T6b:走 Fragment 路径(RecipeRawJsonFragment)。先记录目标 recipe 供 Fragment 按 id 加载,
        // 再通过 routeToRecipeRawJsonFragment 切换到 Fragment。
        pendingRawJsonRecipeId = recipe.id.ifBlank { recipe.name }
        routeToRecipeRawJsonFragment()
    }

    /**
     * T6b:把 root 隐藏,把 RecipeRawJsonFragment 加到 rootHost 容器。
     * 这是 P2 第一个走 Fragment 的 Screen,验证整套机制。
     */
    private fun routeToRecipeRawJsonFragment() {
        currentScreen = Screen.RecipeDetail
        clearRootForScreen()
        root.visibility = View.GONE
        val recipeId = pendingRawJsonRecipeId ?: return
        val fragment = RecipeRawJsonFragment.newInstance(recipeId)
        supportFragmentManager.beginTransaction()
            .replace(rootHost.id, fragment, TAG_RECIPE_RAW_JSON_FRAGMENT)
            .commitAllowingStateLoss()
    }

    /** 退出 RecipeRawJson Fragment:恢复 root,回到编辑器。 */
    override fun onExitRecipeRawJson() {
        supportFragmentManager.findFragmentByTag(TAG_RECIPE_RAW_JSON_FRAGMENT)?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
        }
        root.visibility = View.VISIBLE
        // 回到编辑器:用最近一次记录的 recipe
        val recipe = pendingRawJsonRecipeId?.let { id -> latestRecipeById(id) }
        if (recipe != null) showRecipeEditor(recipe) else showConsole()
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
                mapOf("instanceId" to instanceId, "source" to normalized.source, "url" to normalized.url.take(500))
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
            return KiteDesktopOpenResponse(false, normalized.recipeId, normalized.instanceId, "", "")
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
            return KiteDesktopOpenResponse(false, recipe.id, instanceId, "", "")
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
        currentScreen = Screen.Workbench
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
            root.addView(topBar("Kite 工作台") { showConsole() })
            root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
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

    private fun toggleRow(): View = shortcutActionRow("创建快捷方式到桌面")

    private fun shortcutActionRow(title: String): View =
        localActionRow(
            title = title,
            detail = when {
                formShortcutRequested && editingRecipe?.id.isNullOrBlank() -> "保存时会弹出桌面确认。"
                editingRecipe?.id.isNullOrBlank() -> "点击后保存卡片时弹出桌面确认。"
                else -> "点击后立即弹出桌面确认。"
            },
            actionLabel = "申请"
        ) { handleShortcutRequestAction() }

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
        addView(shortcutActionRow("申请桌面图标"))
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

    private fun localActionRow(title: String, detail: String, actionLabel: String, onClick: () -> Unit): View = row {
        setPadding(0, dp(4), 0, dp(4))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
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
        addView(TextView(context).apply {
            text = actionLabel
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            background = roundedBox(tokens.primarySubtle, tokens.primarySoft, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(66), dp(34))
            setOnClickListener { onClick() }
        })
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
                    val activeDeleteState = (CardRunStore.currentForRecipe(recipe.id) ?: runtimeStates[recipe.id])
                        ?.takeIf { shouldStopRunBeforeDelete(it) }
                    if (activeDeleteState != null) {
                        stopRecipeByCardInstanceId(recipe, activeDeleteState.cardInstanceId, activeDeleteState)
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, "已先停止运行，请停止后再删除配置", Toast.LENGTH_SHORT).show()
                        showConsole()
                        return@setOnClickListener
                    }
                    val deleted = recipeLoader.deleteRecipe(recipe)
                    dialog.dismiss()
                    if (deleted) {
                        val removedCardInstanceIds = CardRunStore.removeClosedRunStatesForRecipes(listOf(recipe.id))
                        if (removedCardInstanceIds.isNotEmpty()) {
                            bridgeClient.cleanCardRunPidDirs(removedCardInstanceIds)
                        }
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
        val groupId: String,
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
                .put("groupId", groupId)
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
                        groupId = json.optString("groupId"),
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
        val groupId: String,
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
        val iconAsset: String = "",
        val iconFit: String = "",
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
        val busy: Boolean
    )

    private data class ResourceItemBinding(
        val resourceId: String,
        val root: View,
        val stateTextView: TextView?,
        val actionButton: TextView?,
        val compactAction: Boolean
    )

    private data class ResourceDetailBinding(
        val resourceId: String,
        val contentHost: FrameLayout,
        val actionHost: LinearLayout,
        val actionBinding: ResourceDetailActionBinding,
        var renderKey: String
    )

    private data class ResourceDetailActionBinding(
        val root: LinearLayout,
        val primaryButton: TextView,
        val secondaryButton: TextView
    )

    private data class ResourceManageBinding(
        val host: LinearLayout,
        val queueHost: LinearLayout,
        val installedHost: LinearLayout,
        var queueRenderKey: String = "",
        var installedRenderKey: String = ""
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
        val outputScrollView: ScrollView?,
        val footerTextView: TextView?,
        val elapsedTextView: TextView?
    )

    private data class ResourceInstallWizardBinding(
        val targetResourceId: String,
        var planResourceIds: List<String>,
        val headerDetailTextView: TextView?,
        val headerProgressTextView: TextView?,
        var primaryActionRenderKey: String,
        val primaryActionButton: TextView,
        val primaryActionHost: LinearLayout,
        val rowsHost: LinearLayout,
        val rowHosts: MutableMap<String, LinearLayout>,
        val rowRenderKeys: MutableMap<String, String>,
        val rowBindings: MutableMap<String, ResourceInstallWizardRowBinding>
    )

    private data class ResourceInstallWizardRowBinding(
        val resourceId: String,
        var runOperation: String,
        val rootView: LinearLayout,
        val numberTextView: TextView,
        val subtitleTextView: TextView,
        val statusTextView: TextView,
        val secondaryActionsHost: LinearLayout,
        var secondaryActionsRenderKey: String = ""
    )

    private data class ResourceInstallWizardRowState(
        val runOperation: String,
        val recipeState: RecipeRuntimeState?,
        val statusLabel: String,
        val tone: Int,
        val subtitle: String,
        val failed: Boolean,
        val uninstalling: Boolean,
        val canOpenRunSurface: Boolean,
        val secondarySurface: CardRunSurface?
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

    private data class ConsolePage(
        val id: String,
        val label: String
    )

    private data class ResourceSectionsPayload(
        val query: String,
        val resources: List<ResourceItem>,
        val sections: List<ResourceHomeSectionUi>,
        val renderKey: String
    )

    private data class ResourceHomeSectionUi(
        val id: String,
        val title: String,
        val style: String,
        val items: List<ResourceItem>
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

    private data class RecipeStatusBadge(
        val label: String,
        val color: Int,
        val background: Int
    )

    private data class RecipeCardTitleText(
        val text: String,
        val textSizeSp: Float
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

    internal enum class Screen {
        Console,
        Terminal,
        Workbench,
        CardRun,
        RecipeDetail,
        CreateConfig,
        RecipeMore,
        Resources,
        ResourceSearch,
        ResourceManage,
        ResourceDetail,
        ResourceMore,
        ResourceRawJson,
        Processes,
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
        private const val TAG_RECIPE_RAW_JSON_FRAGMENT = "kite-recipe-raw-json"
        private const val TAG_RESOURCE_MANAGE_FRAGMENT = "kite-resource-manage"
        private const val TAG_RESOURCE_SEARCH_FRAGMENT = "kite-resource-search"
        private const val TAG_RESOURCES_FRAGMENT = "kite-resources"
        private const val TAG_RESOURCE_DETAIL_FRAGMENT = "kite-resource-detail"
        private const val TERMINAL_FRAGMENT_INITIAL_SESSION_ARG = "initial_session_id"
        private const val CONSOLE_PAGE_ALL = "all"
        private const val CONSOLE_PAGE_OPENED = "opened"
        private const val CONSOLE_PAGE_STOPPED = "stopped"
        private const val CONSOLE_PAGE_GROUP_PREFIX = "group:"
        private const val RESOURCE_NODE_RUNTIME = "kite.nodejs"
        private const val RESOURCE_KF_TOOL_ENV = "kite.tool.env"
        private const val RESOURCE_HERMES_CORE = "kite.hermes.core"
        private const val RESOURCE_HERMES_WEBUI = "kite.hermes.webui"
        private const val RESOURCE_GIT = "kite.git"
        private const val RESOURCE_CURL = "kite.curl"
        private const val RESOURCE_PYTHON = "kite.python"
        private const val RESOURCE_UV = "kite.uv"
        private const val RESOURCE_HOME_TAB_ALL = "all"
        private const val RESOURCE_TOOL_SHELF_ITEM_WIDTH_DP = 68
        private const val RESOURCE_TOOL_SHELF_ITEM_GAP_DP = 7
        private const val RESOURCE_TOOL_SHELF_ICON_SIZE_DP = 58
        private const val RESOURCE_TOOL_SHELF_TEXT_WIDTH_DP = 52
        private const val RESOURCE_TOOL_SHELF_ICON_PADDING_DP = 7
        private const val RESOURCE_TOOL_SHELF_ICON_RADIUS_DP = 16
        private const val RESOURCE_TOOL_SHELF_ICON_TEXT_SP = 14f
        private const val RESOURCE_TOOL_SHELF_TITLE_TEXT_SP = 11.5f
        private const val RESOURCE_ICON_FIT_FULL_BLEED = "fullBleed"
        private const val RESOURCE_ACTION_BUTTON_COMPACT_WIDTH_DP = 60
        private const val RESOURCE_ACTION_BUTTON_COMPACT_HEIGHT_DP = 32
        private const val RESOURCE_ACTION_BUTTON_COMPACT_RADIUS_DP = 16
        private const val RESOURCE_ACTION_BUTTON_COMPACT_TEXT_SP = 12.2f
        private const val RESOURCE_SECTION_RENDER_BATCH_SIZE = 1
        private const val RESOURCE_SECTION_RENDER_BATCH_DELAY_MS = 16L
        private const val RESOURCE_TOOL_SHELF_RENDER_BATCH_SIZE = 5
        private const val RESOURCE_TOOL_SHELF_RENDER_BATCH_DELAY_MS = 16L
        private const val RESOURCE_LIST_ROW_RENDER_BATCH_SIZE = 4
        private const val RESOURCE_LIST_ROW_RENDER_BATCH_DELAY_MS = 16L
        private const val RESOURCE_OPEN_RUNTIME_SOURCE = "resource_open"
        private const val RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE = "resource_install_wizard"
        private const val EXTRA_AUTOMATION_RUNTIME_ACTION = "runtime_action"
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
        private const val TOOLCHAIN_WORKSPACE_PROBE_TTL_MS = 5_000L
        private const val REQUEST_DROPZONE_STORAGE = 801
        private const val REQUEST_PICK_RECIPE_ICON = 802
        private const val REQUEST_FIRST_RUN_RUNTIME_PERMISSIONS = 803
        private const val REQUEST_NOTIFICATION_PERMISSION = 804
        private const val REQUEST_FIRST_RUN_PERMISSION_ONBOARDING = 805
        private const val KEY_HIDE_MAIN_TASK_FROM_RECENTS = "hide_main_task_from_recents"
        private const val KEY_RESTORE_LAST_SCREEN = "restore_last_screen"
        private const val KEY_FIRST_RUN_PERMISSION_ONBOARDING_DONE = "first_run_permission_onboarding_done"
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
