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
import androidx.activity.result.contract.ActivityResultContracts
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
import com.kite.app.application.runs.RunInstanceCloseCommand
import com.kite.app.application.runs.RunInstanceCloseSource
import com.kite.app.application.runs.RUN_NOTIFICATIONS_REQUIRED
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.application.runs.RunStepRestartCommand
import com.kite.app.bridge.KiteBrowserOpenRequest
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.feature.resources.ResourceInstallWizardPlanActionResult
import com.kite.app.feature.resources.ResourceInstallWizardRunRequest
import com.kite.app.feature.runsurface.CardRunLaunchRequest
import com.kite.app.feature.runsurface.CardRunLaunchGenerationPolicy
import com.kite.app.feature.runsurface.CardRunLaunchResolution
import com.kite.app.feature.runsurface.CardRunLaunchResolver
import com.kite.app.feature.runsurface.CardRunLaunchTarget
import com.kite.app.feature.runsurface.CardRunMissingStatePolicy
import com.kite.app.feature.runsurface.CardRunTaskCloseReason
import com.kite.app.feature.runsurface.CardRunTaskNavigationAction
import com.kite.app.feature.runsurface.CardRunTaskNavigationPolicy
import com.kite.app.application.runs.CardRunSpecialRecipes
import com.kite.app.feature.runsurface.RunActivityChrome
import com.kite.app.feature.runsurface.RunActivityChromeActions
import com.kite.app.feature.runsurface.RunAgentSurfaceBinding
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
import com.kite.app.platform.runs.AndroidRunWindowSurfaceGateway
import com.kite.app.recipe.KiteRecipe
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.run.CardRunBrowserRouter
import com.kite.app.run.CardRunDesktopRouter
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunWindowIds
import com.kite.app.shell.KiteAppGraph
import com.kite.app.shell.RunInstallWizardSurfaceBinding
import com.kite.app.shell.RunNotificationPermissionFragment
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.theme.kiteThemeEnvironment
import com.kite.app.ui.theme.applyKiteWindowTheme
import com.kite.app.ui.UiKit
import com.kite.app.ui.terminal.KiteTerminalShellTheme
import com.kite.app.shell.TerminalSurfaceShellBinding
import kotlinx.coroutines.launch

/** 只托管一个运行实例及其可见显示面，不拥有首页、资源目录或底层任务生命周期。 */
class CardRunActivity : AppCompatActivity() {
    private lateinit var graph: KiteAppGraph
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var runOrchestrator: RunOrchestrator
    private lateinit var browserHandoffCoordinator: BrowserHandoffCoordinator
    private lateinit var browserAutomationSessions: BrowserAutomationSessionStore
    private lateinit var browserAutomationUpdater: AndroidBrowserAutomationRunUpdater
    private lateinit var resourceOpenRecipeResolver: AndroidResourceOpenRecipeResolver
    private lateinit var runWindowSurfaceGateway: AndroidRunWindowSurfaceGateway
    private lateinit var launchResolver: CardRunLaunchResolver
    private lateinit var tokens: ThemeTokens
    private var themeDark: Boolean = false
    private lateinit var root: FrameLayout
    private lateinit var surfaceController: RunSurfaceController
    private var surfaceHost: RunSurfaceHost? = null
    private var chrome: RunActivityChrome? = null
    private var currentTarget: CardRunLaunchTarget? = null
    private var boundEnvironmentId: String? = null
    private var currentState: CardRunState? = null
    private var currentChildren: List<CardRunState> = emptyList()
    private var registeredBrowserInstanceId: String? = null
    private var registeredDesktopInstanceId: String? = null
    private var registeredCloserInstanceId: String? = null
    private var registeredCloserGeneration: Long? = null
    private var pendingCloseInstanceId: String? = null
    private var pendingCloseGeneration: Long? = null
    private var tickScheduled = false
    private var agentSurfaceBinding: RunAgentSurfaceBinding? = null
    private val agentAttachmentPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        agentSurfaceBinding?.addAttachments(uris)
    }
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (chrome?.handleBack() == true) return
            if (surfaceHost?.handleBack() == true) return
            navigateBackFromTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTraceStore.markStage(this, "card_run.super_on_create")
        super.onCreate(savedInstanceState)
        RunTerminalSurfaceBinding.removeIncompatibleRestoredFragments(supportFragmentManager)
        onBackPressedDispatcher.addCallback(this, backCallback)
        graph = KiteAppGraph.from(applicationContext)
        RunNotificationPermissionFragment.install(supportFragmentManager)
        diagnostics = graph.diagnostics
        runOrchestrator = graph.runOrchestrator
        browserAutomationSessions = graph.browserAutomationSessions
        resourceOpenRecipeResolver = AndroidResourceOpenRecipeResolver(graph.resourceManifestLoader)
        runWindowSurfaceGateway = graph.runWindowSurfaceGateway
        CardRunStore.initialize(applicationContext)
        val theme = kiteThemeEnvironment()
        tokens = theme.tokens
        themeDark = theme.isDark
        applyKiteWindowTheme(tokens, themeDark)
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
        surfaceController = RunSurfaceController()
        registerTerminalSurfaceResults()
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
        graph.browserAuthRedirectCoordinator.reconcile()
        currentTarget?.instanceId
            ?.let(CardRunStore::get)
            ?.takeIf(::matchesCurrentTarget)
            ?.let { state ->
                renderState(state)
                surfaceHost?.reconcile()
            }
        StartupTraceStore.markReady(applicationContext)
    }

    override fun onPause() {
        restoreSystemBars()
        super.onPause()
    }

    override fun onDestroy() {
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
        CardRunTaskCloser.unregister(registeredCloserInstanceId, registeredCloserGeneration)
        surfaceController.detach()
        surfaceHost?.dispose()
        surfaceHost = null
        chrome?.dispose()
        chrome = null
        super.onDestroy()
    }

    private fun registerTerminalSurfaceResults() {
        TerminalSurfaceShellBinding.register(supportFragmentManager, this,
            onChromeMode = { chrome?.setChromeVisible(true) },
            onBack = { onBackPressedDispatcher.onBackPressed() })
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

    private fun bindTarget(requestedTarget: CardRunLaunchTarget) {
        val environmentId = activeEnvironmentId()
        boundEnvironmentId = environmentId
        val target = requestedTarget.copy(
            instanceId = CardRunState.instanceIdForEnvironment(
                requestedTarget.instanceId,
                environmentId
            )
        )
        if (
            currentTarget?.instanceId != target.instanceId ||
            currentTarget?.expectedGeneration != target.expectedGeneration
        ) {
            currentState = null
            currentChildren = emptyList()
        }
        currentTarget = target
        CardRunStore.registerRecipe(target.recipe)
        val existing = CardRunStore.get(target.instanceId, environmentId)
        if (!CardRunLaunchGenerationPolicy.accepts(existing?.createdAt, target.expectedGeneration)) {
            showLaunchError("该运行代次已经结束，请从原入口重新打开")
            return
        }
        if (existing == null && target.missingStatePolicy == CardRunMissingStatePolicy.RequireExisting) {
            showLaunchError("该运行已经结束，请从首页重新启动")
            return
        }
        title = target.recipe.name
        val ownerKind = existing?.ownerKind ?: if (target.installTargetResourceId != null) {
            CardRunState.OWNER_KIND_INSTALL_WIZARD
        } else {
            CardRunState.OWNER_KIND_CARD
        }
        val stepId = existing?.stepId ?: target.installTargetResourceId
        if (target.autoStart && existing?.status.requiresFreshStart()) {
            when (val result = runOrchestrator.start(
                RunStartRequest(
                    recipe = target.recipe,
                    instanceId = target.instanceId,
                    parentInstanceId = existing?.parentInstanceId,
                    ownerKind = ownerKind,
                    stepId = stepId,
                    environmentId = environmentId
                )
            )) {
                is RunCommandResult.Accepted -> Unit
                is RunCommandResult.Ignored -> {
                    if (result.reason == RUN_NOTIFICATIONS_REQUIRED) {
                        showNotificationBlocked(target.recipe.name)
                        RunNotificationPermissionFragment.request(
                            fragmentManager = supportFragmentManager,
                            title = target.recipe.name,
                            key = "start:${target.instanceId}",
                            retry = { bindTarget(target) }
                        )
                    } else {
                        showLaunchError("运行未启动：${result.reason}")
                    }
                    return
                }
            }
        }
        val initial = CardRunStore.get(target.instanceId, environmentId) ?: existing ?: CardRunStore.start(
            recipe = target.recipe,
            instanceId = target.instanceId,
            ownerKind = ownerKind,
            stepId = stepId,
            environmentId = environmentId
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
                lastMeaningfulOutput = "等待获取确认",
                environmentId = environmentId
            )
        } else {
            initial
        }
        surfaceController.attach(
            target.recipe,
            state,
            CardRunStore.childrenOf(state.instanceId, environmentId)
        )
        ensureSurfaceShell()
        registerInstanceRoutes(target.recipe, state)
        renderState(CardRunStore.get(target.instanceId, environmentId) ?: state)
    }

    private fun ensureSurfaceShell() {
        if (surfaceHost != null) return
        val host = RunSurfaceHost(
            context = this,
            tokens = tokens
        )
        val nextChrome = RunActivityChrome(
            context = this,
            tokens = tokens,
            actions = RunActivityChromeActions(
                onCloseInstance = ::closeCurrentInstance,
                onSelectWindow = { windowId, surface ->
                    currentState?.instanceId?.let { instanceId ->
                        CardRunStore.selectWindow(instanceId, windowId, surface)
                    }
                },
                onRestartWindow = { windowId -> restartWorkflowWindow(windowId) },
                onCloseWindow = ::closeManualWindow,
                onOpenWeb = ::openBlankWebSurface,
                onOpenTerminal = ::openBlankTerminalSurface,
                onToggleSurfaceToolbar = { host.toggleSurfaceToolbar() }
            )
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
        if (!matchesCurrentTarget(state)) return
        if (
            pendingCloseInstanceId == state.instanceId &&
            pendingCloseGeneration == state.createdAt &&
            state.status == CardRunStatus.Stopped
        ) {
            pendingCloseInstanceId = null
            pendingCloseGeneration = null
            closeTaskWindow(CardRunTaskCloseReason.StopConfirmed)
            return
        }
        val children = CardRunStore.childrenOf(state.instanceId, state.environmentId)
        if (state == currentState && children == currentChildren) return
        currentState = state
        currentChildren = children
        val uiState = surfaceController.update(target.recipe, state, children)
            ?: surfaceController.attach(target.recipe, state, children)
        applySystemBars(uiState.surface)
        surfaceHost?.render(uiState, ::createSurfaceBinding)
        chrome?.render(uiState)
        scheduleTickIfNeeded()
    }

    private fun matchesCurrentTarget(state: CardRunState): Boolean {
        val target = currentTarget ?: return false
        return state.instanceId == target.instanceId &&
            state.recipeId == target.recipe.id &&
            state.environmentId == targetEnvironmentId() &&
            CardRunLaunchGenerationPolicy.accepts(state.createdAt, target.expectedGeneration)
    }

    private fun createSurfaceBinding(state: RunSurfaceUiState): RunSurfaceBinding = when (state.content) {
        is RunSurfaceContent.Terminal -> RunTerminalSurfaceBinding(
            context = this,
            fragmentManager = supportFragmentManager,
            instanceId = "${state.selectedWindowId}:${state.content.sessionId.orEmpty()}",
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
        is RunSurfaceContent.Agent -> RunAgentSurfaceBinding(
            context = this,
            tokens = tokens,
            onCloseInstance = ::closeCurrentInstance,
            onPickImages = {
                agentAttachmentPicker.launch(arrayOf("image/*"))
            },
            onPickFiles = {
                agentAttachmentPicker.launch(
                    arrayOf("text/*", "application/pdf", "application/octet-stream")
                )
            },
            agentRegistry = graph.agentRegistry,
            officialAccountManager = graph.agentOfficialAccountManager,
            agentConfigurationApi = graph.agentConfigurationApi,
            agentSessionControlApi = graph.agentSessionControlApi,
        ).also { agentSurfaceBinding = it }
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
            onPlanAction = { action, acknowledge ->
                when (action) {
                    KiteInstallPlanActionIntent.StartNext -> startNextPlannedInstall(acknowledge)
                    KiteInstallPlanActionIntent.Finish -> {
                        acknowledge(ResourceInstallWizardPlanActionResult.Accepted)
                        closeCurrentInstance()
                    }
                }
            },
            onUninstallFailedResource = ::confirmUninstallFailedResource,
            onOpenRun = ::openResourceRun,
            onExit = ::navigateBackFromTask,
            onLiveTickRequired = ::scheduleTickIfNeeded
        )
    }

    private fun openResourceRun(request: ResourceInstallWizardRunRequest) {
        val environmentId = targetEnvironmentId()
        val existing = CardRunStore.get(request.instanceId, environmentId)
        if (existing == null) {
            Toast.makeText(this, "运行记录已结束", Toast.LENGTH_SHORT).show()
            return
        }
        val recipe = graph.resourceRunCoordinator.recipe(request.resourceId, request.operation)
            ?: CardRunStore.registeredRecipe(existing.recipeId)
        if (recipe == null || recipe.id != existing.recipeId) {
            Toast.makeText(this, "运行报告暂不可用", Toast.LENGTH_SHORT).show()
            return
        }
        CardRunStore.registerRecipe(recipe)
        CardRunStore.selectSurface(existing.instanceId, request.surface)
        startActivity(
            CardRunIntents.launchIntent(
                context = this,
                recipeId = recipe.id,
                instanceId = existing.instanceId,
                launchSource = CardRunIntents.SOURCE_RESOURCE_INSTALL,
                autoStart = false,
                generation = existing.createdAt,
            ).apply {
                flags = flags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT.inv()
            }
        )
    }

    private fun startNextPlannedInstall(
        acknowledge: (ResourceInstallWizardPlanActionResult) -> Unit,
    ) {
        val target = currentTarget ?: run {
            acknowledge(ResourceInstallWizardPlanActionResult.Rejected)
            return
        }
        if (graph.resourceRunCoordinator.startRejectionReason() == RUN_NOTIFICATIONS_REQUIRED) {
            acknowledge(ResourceInstallWizardPlanActionResult.Deferred)
            RunNotificationPermissionFragment.request(
                fragmentManager = supportFragmentManager,
                title = target.recipe.name,
                key = "resource-next:${target.instanceId}",
                retry = { startNextPlannedInstall(acknowledge) },
                onCancelled = { acknowledge(ResourceInstallWizardPlanActionResult.Rejected) },
            )
            return
        }
        if (!graph.resourceRunCoordinator.startNextPlannedInstall(target.instanceId)) {
            Toast.makeText(this, "当前没有可启动的获取项", Toast.LENGTH_SHORT).show()
            acknowledge(ResourceInstallWizardPlanActionResult.Rejected)
            surfaceHost?.reconcile()
        } else {
            acknowledge(ResourceInstallWizardPlanActionResult.Accepted)
        }
    }

    private fun observeRunFacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CardRunStore.runs.collect { runs ->
                    val target = currentTarget ?: return@collect
                    val environmentId = targetEnvironmentId()
                    val candidate = runs.firstOrNull {
                        it.instanceId == target.instanceId && it.environmentId == environmentId
                    }
                    if (candidate != null && matchesCurrentTarget(candidate)) {
                        renderState(candidate)
                        surfaceHost?.reconcile()
                    } else if (candidate != null && target.expectedGeneration > 0L) {
                        // 同名新代次已经出现；旧文档任务只关闭自己的显示面，绝不换绑。
                        closeTaskWindow(CardRunTaskCloseReason.DismissSurface)
                    }
                }
            }
        }
    }

    private fun closeCurrentInstance() {
        val state = currentState ?: return
        if (state.status == CardRunStatus.Stopped) {
            closeTaskWindow(CardRunTaskCloseReason.StopConfirmed)
            return
        }
        pendingCloseInstanceId = state.instanceId
        pendingCloseGeneration = state.createdAt
        lifecycleScope.launch {
            when (val result = graph.runInstanceCloseCoordinator.close(
                RunInstanceCloseCommand(
                    instanceId = state.instanceId,
                    expectedGeneration = state.createdAt,
                    source = RunInstanceCloseSource.Explicit,
                )
            )) {
                is RunCommandResult.Accepted -> {
                    if (state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD) {
                        closeTaskWindow(CardRunTaskCloseReason.StopConfirmed)
                    }
                }
                is RunCommandResult.Ignored -> {
                    pendingCloseInstanceId = null
                    pendingCloseGeneration = null
                    if (result.reason == "already_stopped" || result.reason == "missing_instance") {
                        closeTaskWindow(CardRunTaskCloseReason.StopConfirmed)
                    } else {
                        Toast.makeText(this@CardRunActivity, "无法关闭：${result.reason}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun restartWorkflowWindow(windowId: String): Boolean {
        val target = currentTarget ?: return false
        val state = currentState ?: return false
        val stepIndex = CardRunWindowIds.workflowStepIndex(windowId) ?: return false
        val step = target.recipe.steps.getOrNull(stepIndex) ?: return false
        val accepted = if (stepIndex == state.currentStepIndex) {
            when (val result = runOrchestrator.restartStep(
                RunStepRestartCommand(
                    instanceId = state.instanceId,
                    expectedGeneration = state.createdAt,
                    expectedStepIndex = stepIndex,
                    expectedStepId = step.id
                )
            )) {
                is RunCommandResult.Accepted -> true
                is RunCommandResult.Ignored -> {
                    Toast.makeText(this, "无法重新执行：${result.reason}", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        } else {
            runWindowSurfaceGateway.replayWorkflowStep(target.recipe, state.instanceId, stepIndex)
        }
        return accepted
    }

    private fun closeManualWindow(windowId: String) {
        val target = currentTarget ?: return
        val instanceId = currentState?.instanceId ?: return
        if (!runWindowSurfaceGateway.closeManualWindow(target.recipe, instanceId, windowId)) {
            Toast.makeText(this, "该窗口不能单独关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBlankWebSurface() {
        val target = currentTarget ?: return
        if (!runWindowSurfaceGateway.openBlankWeb(target.recipe, target.instanceId)) {
            Toast.makeText(this, "当前实例已经结束", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBlankTerminalSurface() {
        val target = currentTarget ?: return
        if (!runWindowSurfaceGateway.createBlankTerminal(target.recipe, target.instanceId)) {
            Toast.makeText(this, "当前实例已经结束", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerInstanceRoutes(recipe: KiteRecipe, state: CardRunState) {
        val instanceId = state.instanceId
        CardRunBrowserRouter.unregister(registeredBrowserInstanceId)
        registeredBrowserInstanceId = instanceId
        CardRunBrowserRouter.register(instanceId) { request ->
            runOnUiThread { handleBrowserRequest(recipe, instanceId, request) }
            true
        }
        CardRunDesktopRouter.unregister(registeredDesktopInstanceId)
        registeredDesktopInstanceId = instanceId
        CardRunDesktopRouter.register(instanceId) {
            runOnUiThread { CardRunStore.get(instanceId, targetEnvironmentId())?.let(::renderState) }
            true
        }
        CardRunTaskCloser.unregister(registeredCloserInstanceId, registeredCloserGeneration)
        registeredCloserInstanceId = instanceId
        registeredCloserGeneration = state.createdAt
        CardRunTaskCloser.register(instanceId, state.createdAt) {
            runOnUiThread { closeTaskWindow(CardRunTaskCloseReason.DismissSurface) }
        }
    }

    private fun handleBrowserRequest(
        recipe: KiteRecipe,
        instanceId: String,
        request: KiteBrowserOpenRequest
    ) {
        val decision = BrowserHandoffPolicy.classify(request.url, request.source)
        when {
            BrowserHandoffPolicy.isHandoff(decision) -> {
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
            decision == BrowserHandoffDecision.OpenExternalBrowser -> {
                if (!openExternalBrowser(request.url)) {
                    Toast.makeText(this, "无法打开系统浏览器", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
        val environmentId = targetEnvironmentId()
        val existing = CardRunStore.get(instanceId, environmentId)
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
            nextActionUrl = request.url,
            environmentId = environmentId
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
        if (runWindowSurfaceGateway.openManualWebUrl(target.recipe, state.instanceId, url)) return
        CardRunStore.update(
            recipe = target.recipe,
            status = state.status.takeIf {
                it == CardRunStatus.Starting || it == CardRunStatus.Running ||
                    it == CardRunStatus.WaitingTerminal || it == CardRunStatus.AlreadyRunning
            } ?: CardRunStatus.Opened,
            instanceId = state.instanceId,
            surface = CardRunSurface.Web,
            lastMeaningfulOutput = "手动打开网页",
            nextActionUrl = url,
            environmentId = state.environmentId
        )
    }

    private fun confirmUninstallFailedResource(resourceId: String) {
        UiKit(this, kiteThemeEnvironment()).showConfirmDialog(
            context = this,
            title = getString(R.string.resource_wizard_cleanup_confirm_title),
            message = getString(R.string.resource_wizard_cleanup_confirm_summary),
            dismissLabel = getString(R.string.common_cancel),
            primaryAction = com.kite.app.ui.UiDialogAction(
                label = getString(R.string.resource_wizard_cleanup_confirm_action),
                role = com.kite.app.ui.UiActionRole.Danger,
                onClick = { uninstallFailedResource(resourceId) },
            ),
        )
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
            is ResourceRunLaunchResult.Rejected -> if (result.reason == RUN_NOTIFICATIONS_REQUIRED) {
                RunNotificationPermissionFragment.request(
                    fragmentManager = supportFragmentManager,
                    title = recipe.name,
                    key = "resource-uninstall:$resourceId",
                    retry = { uninstallFailedResource(resourceId) }
                )
            } else {
                Toast.makeText(this, result.reason, Toast.LENGTH_SHORT).show()
            }
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
            installPlanResourceIds = getStringArrayListExtra(CardRunIntents.EXTRA_RESOURCE_INSTALL_PLAN_IDS).orEmpty(),
            expectedGeneration = getLongExtra(CardRunIntents.EXTRA_GENERATION, 0L),
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

    private fun showNotificationBlocked(title: String) {
        root.removeAllViews()
        root.addView(
            placeholder("等待通知权限", "$title 的进度、结果和下一步会显示在系统通知中。"),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun CardRunStatus?.requiresFreshStart(): Boolean = this == null || this in setOf(
        CardRunStatus.Unknown,
        CardRunStatus.Stopped,
        CardRunStatus.Completed,
        CardRunStatus.Failed,
        CardRunStatus.BridgeUnavailable
    )

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
        CardRunTaskCloser.unregister(registeredCloserInstanceId, registeredCloserGeneration)
        registeredBrowserInstanceId = null
        registeredDesktopInstanceId = null
        registeredCloserInstanceId = null
        registeredCloserGeneration = null
        surfaceController.detach()
        surfaceHost?.dispose()
        surfaceHost = null
        chrome?.dispose()
        chrome = null
        agentSurfaceBinding = null
        currentTarget = null
        boundEnvironmentId = null
        currentState = null
        currentChildren = emptyList()
        pendingCloseInstanceId = null
        pendingCloseGeneration = null
    }

    private fun navigateBackFromTask() {
        when (CardRunTaskNavigationPolicy.decide(currentState)) {
            CardRunTaskNavigationAction.HideTask -> moveTaskToBack(true)
            CardRunTaskNavigationAction.CloseTask -> closeTaskWindow(CardRunTaskCloseReason.DismissSurface)
        }
    }

    private fun closeTaskWindow(@Suppress("UNUSED_PARAMETER") reason: CardRunTaskCloseReason) {
        // 页面只关闭显示任务；运行停止、计划取消和临时事实清理由各自状态拥有者完成。
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
                    applySurfaceWindowTheme(surface)
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
            if (!immersive) applySurfaceWindowTheme(surface)
        }
    }

    private fun applySurfaceWindowTheme(surface: CardRunSurface) {
        applyKiteWindowTheme(tokens, themeDark)
        if (surface != CardRunSurface.Agent) return
        val background = if (themeDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        window.statusBarColor = background
        window.navigationBarColor = background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = background
        }
    }

    private fun activeEnvironmentId(): String = graph.resourceInstallStore.currentEnvironmentId()

    private fun targetEnvironmentId(): String = boundEnvironmentId ?: activeEnvironmentId()

    private fun restoreSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.systemBars())
            applyKiteWindowTheme(tokens, themeDark)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
            applyKiteWindowTheme(tokens, themeDark)
        }
    }
}
