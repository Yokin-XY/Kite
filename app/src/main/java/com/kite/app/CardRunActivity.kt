package com.kite.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.application.browser.BrowserHandoffCoordinator
import com.kite.app.application.browser.BrowserHandoffLaunchResult
import com.kite.app.application.resources.ResourceRunContinuation
import com.kite.app.application.resources.ResourceRunLaunchRequest
import com.kite.app.application.resources.ResourceRunLaunchResult
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.bridge.KiteBrowserOpenRequest
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.feature.resources.ResourceInstallWizardRunRequest
import com.kite.app.feature.runsurface.CardRunLaunchRequest
import com.kite.app.feature.runsurface.CardRunLaunchResolution
import com.kite.app.feature.runsurface.CardRunLaunchResolver
import com.kite.app.feature.runsurface.CardRunLaunchTarget
import com.kite.app.feature.runsurface.CardRunMissingStatePolicy
import com.kite.app.feature.runsurface.CardRunSpecialRecipes
import com.kite.app.feature.runsurface.RunActivityChrome
import com.kite.app.feature.runsurface.RunSurfaceActionGateway
import com.kite.app.feature.runsurface.RunSurfaceBinding
import com.kite.app.feature.runsurface.RunSurfaceContent
import com.kite.app.feature.runsurface.RunSurfaceController
import com.kite.app.feature.runsurface.RunSurfaceHost
import com.kite.app.feature.runsurface.RunSurfaceUiState
import com.kite.app.feature.runsurface.RunTerminalSurfaceBinding
import com.kite.app.feature.runsurface.RunWebSurfaceBinding
import com.kite.app.feature.runsurface.RunX11SurfaceBinding
import com.kite.app.feature.runsurface.StaticRunSurfaceBinding
import com.kite.app.foundation.bootstrap.StartupTraceStore
import com.kite.app.platform.browser.AndroidBrowserAutomationRunUpdater
import com.kite.app.platform.resources.AndroidResourceOpenRecipeResolver
import com.kite.app.recipe.KiteRecipe
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.run.CardRunBrowserRouter
import com.kite.app.run.CardRunDesktopRouter
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import com.kite.app.shell.KiteAppGraph
import com.kite.app.shell.RunInstallWizardSurfaceBinding
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit
import com.kite.app.ui.terminal.KiteTerminalShellTheme
import com.kite.app.ui.terminal.TerminalChromeHost
import kotlinx.coroutines.launch

/** 只托管一个运行实例及其可见显示面，不拥有首页、资源目录或底层任务生命周期。 */
class CardRunActivity : AppCompatActivity(), TerminalChromeHost {
    private lateinit var graph: KiteAppGraph
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var runOrchestrator: RunOrchestrator
    private lateinit var browserHandoffCoordinator: BrowserHandoffCoordinator
    private lateinit var browserAutomationSessions: BrowserAutomationSessionStore
    private lateinit var browserAutomationUpdater: AndroidBrowserAutomationRunUpdater
    private lateinit var resourceOpenRecipeResolver: AndroidResourceOpenRecipeResolver
    private lateinit var launchResolver: CardRunLaunchResolver
    private lateinit var tokens: ThemeTokens
    private lateinit var root: FrameLayout
    private lateinit var surfaceController: RunSurfaceController
    private var surfaceHost: RunSurfaceHost? = null
    private var chrome: RunActivityChrome? = null
    private var currentTarget: CardRunLaunchTarget? = null
    private var currentState: CardRunState? = null
    private var registeredBrowserInstanceId: String? = null
    private var registeredDesktopInstanceId: String? = null
    private var registeredCloserInstanceId: String? = null
    private var tickScheduled = false

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (surfaceHost?.handleBack() == true) return
            closeTaskWindow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTraceStore.markStage(this, "card_run.super_on_create")
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
        graph = KiteAppGraph.from(applicationContext)
        diagnostics = graph.diagnostics
        runOrchestrator = graph.runOrchestrator
        browserAutomationSessions = graph.browserAutomationSessions
        resourceOpenRecipeResolver = AndroidResourceOpenRecipeResolver(graph.resourceManifestLoader)
        CardRunStore.initialize(applicationContext)
        tokens = loadTokens()
        applyTerminalTheme()
        root = FrameLayout(this).apply { setBackgroundColor(tokens.pageBackground) }
        setContentView(root)
        launchResolver = CardRunLaunchResolver(
            catalogRecipes = { graph.recipeLoader.loadAllRecipes() },
            registeredRecipe = CardRunStore::registeredRecipe,
            specialRecipe = ::resolveSpecialRecipe
        )
        browserHandoffCoordinator = graph.createBrowserHandoffCoordinator(
            recipeResolver = ::resolveRecipeById,
            openExternal = ::openExternalBrowser
        )
        browserAutomationUpdater = AndroidBrowserAutomationRunUpdater(::resolveRecipeById)
        surfaceController = RunSurfaceController(object : RunSurfaceActionGateway {
            override fun completeCurrentStep(instanceId: String, output: String) {
                runOrchestrator.completeCurrentStep(instanceId, output)
            }

            override fun stop(instanceId: String) {
                runOrchestrator.stop(instanceId)
            }
        })
        observeRunFacts()
        handleLaunchIntent(intent)
        root.post { StartupTraceStore.markReady(applicationContext) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        detachVisibleTarget()
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        graph.browserAuthSessions.expirePending().forEach { session ->
            graph.browserLoopbackCallbackBridge.stop(session.sessionId)
        }
        currentTarget?.instanceId?.let(CardRunStore::get)?.let(::renderState)
        StartupTraceStore.markReady(applicationContext)
    }

    override fun onPause() {
        restoreSystemBars()
        super.onPause()
    }

    override fun onDestroy() {
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
        CardRunTaskCloser.unregister(registeredCloserInstanceId)
        surfaceController.detach()
        surfaceHost?.dispose()
        surfaceHost = null
        chrome?.dispose()
        chrome = null
        super.onDestroy()
    }

    override fun setTerminalDetailMode(enabled: Boolean) {
        // 独立运行壳没有主应用底栏；终端详情不能吞掉继续、停止和关闭入口。
        chrome?.setChromeVisible(true)
    }

    override fun openTerminalSession(sessionId: String) {
        val state = CardRunStore.snapshot().firstOrNull { it.terminalSessionId == sessionId } ?: return
        val recipe = resolveRecipeById(state.recipeId) ?: return
        currentTarget = currentTarget?.copy(recipe = recipe, instanceId = state.instanceId)
        surfaceController.attach(recipe, state)
        registerInstanceRoutes(recipe, state.instanceId)
        renderState(state)
    }

    private fun handleLaunchIntent(sourceIntent: Intent?) {
        val request = sourceIntent?.toLaunchRequest()
        if (request == null) {
            showLaunchError("运行窗口缺少启动参数")
            return
        }
        when (val resolution = launchResolver.resolve(request)) {
            is CardRunLaunchResolution.Rejected -> showLaunchError("无法打开运行窗口：${resolution.reason}")
            is CardRunLaunchResolution.Resolved -> bindTarget(resolution.target)
        }
    }

    private fun bindTarget(target: CardRunLaunchTarget) {
        currentTarget = target
        CardRunStore.registerRecipe(target.recipe)
        val existing = CardRunStore.get(target.instanceId)
        if (existing == null && target.missingStatePolicy == CardRunMissingStatePolicy.RequireExisting) {
            showLaunchError("该运行已经结束，请从首页重新启动")
            return
        }
        title = target.recipe.name
        val initial = existing ?: CardRunStore.start(
                recipe = target.recipe,
                instanceId = target.instanceId,
                ownerKind = if (target.installTargetResourceId != null) {
                    CardRunState.OWNER_KIND_INSTALL_WIZARD
                } else {
                    CardRunState.OWNER_KIND_CARD
                },
                stepId = target.installTargetResourceId
            )
        val state = if (target.installTargetResourceId != null && initial.surface != CardRunSurface.InstallWizard) {
            CardRunStore.update(
                recipe = target.recipe,
                status = CardRunStatus.Opened,
                instanceId = target.instanceId,
                ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
                stepId = target.installTargetResourceId,
                surface = CardRunSurface.InstallWizard,
                currentStepIndex = 0,
                lastMeaningfulOutput = "等待获取确认"
            )
        } else {
            initial
        }
        surfaceController.attach(target.recipe, state)
        ensureSurfaceShell()
        registerInstanceRoutes(target.recipe, target.instanceId)
        if (target.autoStart) {
            runOrchestrator.start(
                RunStartRequest(
                    recipe = target.recipe,
                    instanceId = target.instanceId,
                    parentInstanceId = state.parentInstanceId,
                    ownerKind = state.ownerKind,
                    stepId = state.stepId
                )
            )
        }
        renderState(CardRunStore.get(target.instanceId) ?: state)
    }

    private fun ensureSurfaceShell() {
        if (surfaceHost != null) return
        val host = RunSurfaceHost(
            context = this,
            tokens = tokens,
            onCompleteCurrentStep = ::completeCurrentStep
        )
        val nextChrome = RunActivityChrome(
            context = this,
            tokens = tokens,
            onComplete = ::completeCurrentStep,
            onStop = ::stopCurrentRun,
            onReload = { host.reload() },
            onClose = ::closeTaskWindow
        )
        host.setOverlay(nextChrome.root)
        surfaceHost = host
        chrome = nextChrome
        root.removeAllViews()
        root.addView(
            host.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun renderState(state: CardRunState) {
        val target = currentTarget ?: return
        if (state.instanceId != target.instanceId || state.recipeId != target.recipe.id) return
        currentState = state
        val uiState = surfaceController.update(target.recipe, state)
            ?: surfaceController.attach(target.recipe, state)
        applySystemBars(uiState.surface)
        surfaceHost?.render(uiState, ::createSurfaceBinding)
        chrome?.render(uiState)
        scheduleTickIfNeeded()
    }

    private fun createSurfaceBinding(state: RunSurfaceUiState): RunSurfaceBinding = when (state.content) {
        is RunSurfaceContent.Terminal -> RunTerminalSurfaceBinding(
            context = this,
            fragmentManager = supportFragmentManager,
            instanceId = state.target.instanceId,
            tokens = tokens
        )
        is RunSurfaceContent.Web -> RunWebSurfaceBinding(
            activity = this,
            tokens = tokens,
            diagnostics = diagnostics,
            automationSessions = browserAutomationSessions,
            automationEnabled = { browserRuntimeMode() == BrowserRuntimeMode.AutomationBrowser },
            onAutomationEvent = { event -> browserAutomationUpdater.update(event) },
            onLaunchHandoff = { request, decision, force -> launchBrowserHandoff(request, decision, force) },
            onOpenExternal = ::openExternalBrowser,
            onManualUrl = ::openManualWebUrl
        )
        is RunSurfaceContent.X11 -> RunX11SurfaceBinding(this, tokens)
        RunSurfaceContent.InstallWizard -> createInstallWizardBinding()
        else -> StaticRunSurfaceBinding(placeholder(state.title, state.statusLabel))
    }

    private fun createInstallWizardBinding(): RunSurfaceBinding {
        val target = currentTarget
        val targetId = target?.installTargetResourceId.orEmpty()
        val planIds = target?.installPlanResourceIds.orEmpty()
            .ifEmpty { graph.resourceInstallStore.planResourceIds() }
            .ifEmpty { graph.resourceInstallStore.pendingPlanResourceIds() }
        return RunInstallWizardSurfaceBinding(
            context = this,
            gateway = graph.resourceFeatureGateway,
            targetResourceId = targetId,
            planResourceIds = planIds,
            onPlanAction = { action ->
                when (action) {
                    KiteInstallPlanActionIntent.StartNext -> {
                        if (!graph.resourceRunCoordinator.startNextPlannedInstall(target?.instanceId)) {
                            Toast.makeText(this, "当前没有可启动的获取项", Toast.LENGTH_SHORT).show()
                            surfaceHost?.reconcile()
                        }
                    }
                    KiteInstallPlanActionIntent.Finish -> closeTaskWindow()
                }
            },
            onOpenRun = ::openResourceRun,
            onUninstallFailedResource = ::uninstallFailedResource,
            onReportUnavailable = { Toast.makeText(this, "报告正在准备", Toast.LENGTH_SHORT).show() },
            onLiveTickRequired = ::scheduleTickIfNeeded
        )
    }

    private fun observeRunFacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CardRunStore.runs.collect { runs ->
                    val instanceId = currentTarget?.instanceId ?: return@collect
                    runs.firstOrNull { it.instanceId == instanceId }?.let(::renderState)
                    surfaceHost?.reconcile()
                }
            }
        }
    }

    private fun completeCurrentStep() {
        val state = currentState ?: return
        val target = currentTarget ?: return
        if (!RunSurfaceProjectorBridge.canComplete(target.recipe, state)) return
        runOrchestrator.completeCurrentStep(state.instanceId, completionMessage(target.recipe, state))
    }

    private fun stopCurrentRun() {
        val instanceId = currentState?.instanceId ?: return
        when (val result = runOrchestrator.stop(instanceId)) {
            is RunCommandResult.Accepted -> closeTaskWindow()
            is RunCommandResult.Ignored -> Toast.makeText(this, "无法停止：${result.reason}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun completionMessage(recipe: KiteRecipe, state: CardRunState): String =
        when (recipe.steps.getOrNull(state.currentStepIndex)?.type) {
            KiteRecipe.STEP_TERMINAL -> "终端已由用户标记完成"
            KiteRecipe.STEP_OPEN_WEB -> "网页已由用户标记完成"
            KiteRecipe.STEP_X11 -> "X11 GUI 已由用户标记完成"
            KiteRecipe.STEP_SHELL -> "SH 报告已由用户确认继续"
            else -> "步骤已由用户标记完成"
        }

    private fun registerInstanceRoutes(recipe: KiteRecipe, instanceId: String) {
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        registeredBrowserInstanceId = instanceId
        CardRunBrowserRouter.register(instanceId) { request ->
            runOnUiThread { handleBrowserRequest(recipe, instanceId, request) }
            true
        }
        CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
        registeredDesktopInstanceId = instanceId
        CardRunDesktopRouter.register(instanceId) {
            runOnUiThread { CardRunStore.get(instanceId)?.let(::renderState) }
            true
        }
        CardRunTaskCloser.unregister(registeredCloserInstanceId)
        registeredCloserInstanceId = instanceId
        CardRunTaskCloser.register(instanceId) { runOnUiThread(::closeTaskWindow) }
    }

    private fun handleBrowserRequest(
        recipe: KiteRecipe,
        instanceId: String,
        request: KiteBrowserOpenRequest
    ) {
        val decision = BrowserHandoffPolicy.classify(request.url, request.source)
        if (BrowserHandoffPolicy.isHandoff(decision)) {
            launchBrowserHandoff(
                BrowserHandoffRequest(
                    url = request.url,
                    recipeId = recipe.id,
                    recipeName = recipe.name,
                    instanceId = instanceId,
                    source = request.source
                ),
                decision,
                false
            )
            return
        }
        val existing = CardRunStore.get(instanceId)
        CardRunStore.update(
            recipe = recipe,
            status = existing?.status?.takeIf {
                it == CardRunStatus.Starting || it == CardRunStatus.Running || it == CardRunStatus.WaitingTerminal
            } ?: CardRunStatus.Opened,
            instanceId = instanceId,
            surface = CardRunSurface.Web,
            currentStepIndex = existing?.currentStepIndex,
            runId = existing?.runId,
            terminalSessionId = existing?.terminalSessionId,
            pid = existing?.pid,
            lastMeaningfulOutput = "Ubuntu 请求打开网页",
            nextActionUrl = request.url
        )
    }

    private fun launchBrowserHandoff(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision,
        force: Boolean
    ): Boolean {
        val result = browserHandoffCoordinator.launch(request, decision, force)
        if (result is BrowserHandoffLaunchResult.Failed) {
            Toast.makeText(this, "无法打开安全浏览器", Toast.LENGTH_LONG).show()
        }
        return result.accepted
    }

    private fun openManualWebUrl(rawUrl: String) {
        val state = currentState ?: return
        val target = currentTarget ?: return
        val url = normalizeWebUrl(rawUrl)
        if (url.isBlank()) {
            Toast.makeText(this, "请输入网页地址", Toast.LENGTH_SHORT).show()
            return
        }
        CardRunStore.update(
            recipe = target.recipe,
            status = state.status.takeIf {
                it == CardRunStatus.Starting || it == CardRunStatus.Running ||
                    it == CardRunStatus.WaitingTerminal || it == CardRunStatus.AlreadyRunning
            } ?: CardRunStatus.Opened,
            instanceId = state.instanceId,
            surface = CardRunSurface.Web,
            lastMeaningfulOutput = "手动打开网页",
            nextActionUrl = url
        )
    }

    private fun openResourceRun(request: ResourceInstallWizardRunRequest) {
        val recipe = graph.resourceRunCoordinator.recipe(request.resourceId, request.operation)
            ?: CardRunStore.get(request.instanceId)?.recipeId?.let(::resolveRecipeById)
        if (recipe == null) {
            Toast.makeText(this, "运行报告正在准备", Toast.LENGTH_SHORT).show()
            return
        }
        CardRunStore.registerRecipe(recipe)
        val childIntent = CardRunIntents.launchIntent(
            context = this,
            recipeId = recipe.id,
            instanceId = request.instanceId,
            launchSource = CardRunIntents.SOURCE_RESOURCE_INSTALL,
            autoStart = false
        ).apply { flags = flags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT.inv() }
        startActivity(childIntent)
    }

    private fun uninstallFailedResource(resourceId: String) {
        val recipe = graph.resourceRunCoordinator.recipe(resourceId, KiteResourceInstallRecipes.OP_UNINSTALL)
        if (recipe == null) {
            Toast.makeText(this, "缺少卸载动作", Toast.LENGTH_SHORT).show()
            return
        }
        when (val result = graph.resourceRunCoordinator.start(
            ResourceRunLaunchRequest(
                resourceId = resourceId,
                recipe = recipe,
                operation = KiteResourceInstallRecipes.OP_UNINSTALL,
                parentInstanceId = currentTarget?.instanceId,
                continuation = ResourceRunContinuation.ResumeInstallWizard
            )
        )) {
            is ResourceRunLaunchResult.Accepted -> Unit
            is ResourceRunLaunchResult.Rejected -> Toast.makeText(this, result.reason, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveSpecialRecipe(request: CardRunLaunchRequest): KiteRecipe? {
        request.temporaryUrl?.takeIf { it.isNotBlank() }?.let { url ->
            return CardRunSpecialRecipes.temporaryBrowser(
                recipeId = request.recipeId,
                url = url,
                title = request.temporaryTitle?.takeIf { it.isNotBlank() } ?: "临时网页"
            )
        }
        request.installTargetResourceId?.takeIf { it.isNotBlank() }?.let { resourceId ->
            val name = graph.resourceManifestLoader.requestManifest(resourceId)?.name ?: resourceId
            return CardRunSpecialRecipes.installWizard(resourceId, name, request.recipeId)
        }
        resourceOpenRecipeResolver.resolve(request.recipeId)?.let { return it }
        return finiteResourceRecipe(request.recipeId)
    }

    private fun finiteResourceRecipe(recipeId: String): KiteRecipe? {
        val operation = when {
            recipeId.endsWith("-${KiteResourceInstallRecipes.OP_INSTALL}") -> KiteResourceInstallRecipes.OP_INSTALL
            recipeId.endsWith("-${KiteResourceInstallRecipes.OP_UNINSTALL}") -> KiteResourceInstallRecipes.OP_UNINSTALL
            else -> return null
        }
        val resourceId = recipeId
            .removePrefix("resource-")
            .removeSuffix("-$operation")
            .takeIf { it.isNotBlank() }
            ?: return null
        return graph.resourceRunCoordinator.recipe(resourceId, operation)
    }

    private fun resolveRecipeById(recipeId: String): KiteRecipe? =
        currentTarget?.recipe?.takeIf { it.id == recipeId }
            ?: CardRunStore.registeredRecipe(recipeId)
            ?: graph.recipeLoader.loadAllRecipes().firstOrNull { it.id == recipeId }
            ?: resourceOpenRecipeResolver.resolve(recipeId)
            ?: finiteResourceRecipe(recipeId)

    private fun Intent.toLaunchRequest(): CardRunLaunchRequest? {
        val recipeId = getStringExtra(CardRunIntents.EXTRA_RECIPE_ID)?.takeIf { it.isNotBlank() } ?: return null
        return CardRunLaunchRequest(
            recipeId = recipeId,
            instanceId = getStringExtra(CardRunIntents.EXTRA_INSTANCE_ID),
            autoStart = getBooleanExtra(CardRunIntents.EXTRA_AUTO_START, true),
            launchSource = getStringExtra(CardRunIntents.EXTRA_LAUNCH_SOURCE).orEmpty(),
            temporaryUrl = getStringExtra(CardRunIntents.EXTRA_TEMP_URL),
            temporaryTitle = getStringExtra(CardRunIntents.EXTRA_TEMP_TITLE),
            installTargetResourceId = getStringExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_TARGET_ID),
            installPlanResourceIds = getStringArrayListExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_PLAN_IDS).orEmpty()
        )
    }

    private fun loadTokens(): ThemeTokens {
        val prefs = getSharedPreferences("kite_theme", MODE_PRIVATE)
        return KiteTheme.resolve(
            ThemeConfig(
                themeColor = prefs.getInt("theme_color", KiteTheme.defaultThemeColor),
                backgroundColor = prefs.getInt("background_color", KiteTheme.defaultBackgroundColor)
            )
        )
    }

    private fun applyTerminalTheme() {
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

    private fun browserRuntimeMode(): BrowserRuntimeMode = BrowserRuntimeMode.fromStorageKey(
        getSharedPreferences("kite_app_settings", MODE_PRIVATE).getString("browser_runtime_mode", null)
    )

    private fun openExternalBrowser(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        }.isSuccess || runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
    }

    private fun normalizeWebUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        val lower = value.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return value
        if (value.all(Char::isDigit)) return "http://127.0.0.1:$value"
        if (lower.startsWith("localhost") || lower.startsWith("127.0.0.1")) return "http://$value"
        if ("://" in value) return value
        return "https://$value"
    }

    private fun placeholder(title: String, detail: String): View {
        val ui = UiKit(this, tokens)
        return TextView(this).apply {
            text = "$title\n$detail"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            setBackgroundColor(tokens.pageBackground)
            setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24))
        }
    }

    private fun showLaunchError(message: String) {
        root.removeAllViews()
        root.addView(
            placeholder("运行窗口无法打开", message),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun scheduleTickIfNeeded() {
        if (tickScheduled) return
        tickScheduled = true
        root.postDelayed({
            tickScheduled = false
            if (surfaceHost?.tick() == true) scheduleTickIfNeeded()
        }, 1_000L)
    }

    private fun detachVisibleTarget() {
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
        CardRunTaskCloser.unregister(registeredCloserInstanceId)
        registeredBrowserInstanceId = null
        registeredDesktopInstanceId = null
        registeredCloserInstanceId = null
        surfaceController.detach()
        surfaceHost?.dispose()
        surfaceHost = null
        chrome?.dispose()
        chrome = null
        currentTarget = null
        currentState = null
    }

    private fun closeTaskWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask() else finish()
    }

    private fun applySystemBars(surface: CardRunSurface) {
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
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (immersive) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                0
            }
        }
    }

    private fun restoreSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    }
}

/** 复用既有纯投影的完成门禁，避免 Activity 自己重新定义步骤规则。 */
private object RunSurfaceProjectorBridge {
    fun canComplete(recipe: KiteRecipe, state: CardRunState): Boolean =
        com.kite.app.feature.runsurface.RunSurfaceProjector.project(recipe, state).canCompleteCurrentStep
}
