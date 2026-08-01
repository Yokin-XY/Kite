package com.kite.app.shell

import android.content.Context
import android.util.Log
import com.kite.app.CardRunTaskCloser
import com.kite.app.action.KiteActionRouter
import com.kite.app.action.KiteRecipeActionCoordinator
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.agent.registration.KiteCustomAgentRegistrationStore
import com.kite.app.agent.auth.AgentOfficialAccountManager
import com.kite.app.agent.config.AgentConfigAdapterRegistry
import com.kite.app.agent.config.defaultAgentConfigAdapters
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.browser.BrowserHandoffCoordinator
import com.kite.app.application.browser.BrowserAuthRedirectCoordinator
import com.kite.app.application.browser.BrowserOpenCoordinator
import com.kite.app.application.packages.InstallApkCoordinator
import com.kite.app.application.resources.ResourceRunCoordinator
import com.kite.app.application.resources.ResourceActionWorkflowCoordinator
import com.kite.app.application.resources.ResourceVersionCoordinator
import com.kite.app.application.resources.ResourceVersionBatchSummary
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.runs.RunExecutionEffectBus
import com.kite.app.application.runs.RunExecutionEnvironmentProvider
import com.kite.app.application.runs.RunLifecycleEventHub
import com.kite.app.application.runs.RunHistoryGateway
import com.kite.app.application.runs.RunInstanceCloseCoordinator
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartGate
import com.kite.app.application.runs.RecipeActionWorkflowCoordinator
import com.kite.app.application.runs.DesktopOpenCoordinator
import com.kite.app.application.runs.RuntimeOwnerProbeCoordinator
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementCoordinator
import com.kite.app.application.runtimemanagement.RuntimeManagementDispatchResult
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapGateway
import com.kite.app.application.onboarding.FirstRunOnboardingCoordinator
import com.kite.app.application.settings.SettingsDropZoneSnapshot
import com.kite.app.application.settings.SettingsGateway
import com.kite.app.browser.BrowserAuthSessionStore
import com.kite.app.browser.BrowserLoopbackCallbackBridge
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.dropzone.KiteDropZoneManager
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteCardGroupStore
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.foundation.runtime.StructuredJsonStringContext
import com.kite.app.foundation.runtime.StructuredJsonStringRoot
import com.kite.app.platform.resources.AndroidResourceFeatureGateway
import com.kite.app.platform.browser.AndroidBrowserHandoffGateway
import com.kite.app.platform.browser.AndroidBrowserAuthRedirectGateway
import com.kite.app.platform.browser.AndroidExternalBrowserLauncher
import com.kite.app.platform.browser.AndroidBrowserOpenGateway
import com.kite.app.platform.packages.AndroidInstallApkGateway
import com.kite.app.recipe.KiteRecipe
import com.kite.app.platform.resources.AndroidResourceRecipeFactory
import com.kite.app.platform.resources.AndroidResourceRunGateway
import com.kite.app.platform.resources.AndroidResourceActionGateway
import com.kite.app.platform.resources.AndroidResourceVersionGateway
import com.kite.app.platform.recipes.AndroidRecipeFeatureGateway
import com.kite.app.platform.runs.AndroidAgentConfigCommandExecutor
import com.kite.app.platform.runs.AndroidAgentOfficialAccountCommandRunner
import com.kite.app.platform.runs.AndroidRecipeExecutor
import com.kite.app.platform.runs.AndroidRecipeActionGateway
import com.kite.app.platform.runs.AndroidDesktopOpenGateway
import com.kite.app.platform.runs.AndroidRuntimeOwnerProbeGateway
import com.kite.app.platform.runs.AndroidRunHistoryGateway
import com.kite.app.platform.runs.AndroidRunStateGateway
import com.kite.app.platform.runs.AndroidRunWindowSurfaceGateway
import com.kite.app.platform.runs.AndroidRunNotificationCoordinator
import com.kite.app.platform.runtimemanagement.AndroidRuntimeManagementGateway
import com.kite.app.platform.runtimebootstrap.AndroidRuntimeBootstrapGateway
import com.kite.app.platform.onboarding.AndroidFirstRunOnboardingStore
import com.kite.app.platform.settings.AndroidSettingsGateway
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Kite 进程级组合根。这里只装配已有能力，不承载页面状态或业务流程。
 */
internal class KiteAppGraph private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val diagnostics: KiteDiagnostics by lazy { KiteDiagnostics(appContext) }
    val bridgeClient: KiteBridgeClient by lazy { KiteBridgeClient(diagnostics, appContext) }
    val browserAuthSessions: BrowserAuthSessionStore by lazy { BrowserAuthSessionStore(appContext) }
    val browserLoopbackCallbackBridge: BrowserLoopbackCallbackBridge by lazy {
        BrowserLoopbackCallbackBridge.get(appContext)
    }
    val browserAutomationSessions: BrowserAutomationSessionStore by lazy {
        BrowserAutomationSessionStore(appContext)
    }
    val webWorkbenchHandoffCoordinator: BrowserHandoffCoordinator by lazy {
        createBrowserHandoffCoordinator(
            recipeResolver = ::resolveRecipe,
            openExternal = { url -> AndroidExternalBrowserLauncher.open(appContext, url) }
        )
    }
    val browserAuthRedirectCoordinator: BrowserAuthRedirectCoordinator by lazy {
        BrowserAuthRedirectCoordinator(
            AndroidBrowserAuthRedirectGateway(
                sessions = browserAuthSessions,
                loopbackBridge = browserLoopbackCallbackBridge,
                diagnostics = diagnostics,
                recipeResolver = ::resolveRecipe
            )
        )
    }
    val browserOpenCoordinator: BrowserOpenCoordinator by lazy {
        BrowserOpenCoordinator(
            AndroidBrowserOpenGateway(
                diagnostics,
                ::resolveRecipe,
                resourceInstallStore::currentEnvironmentId
            )
        )
    }
    val installApkCoordinator: InstallApkCoordinator by lazy {
        InstallApkCoordinator(AndroidInstallApkGateway(appContext))
    }
    val resourceInstallStore: KiteResourceInstallStore by lazy {
        // 正式资源事实固定归属原生 PRoot。View 只服务显式事务，不能再切换资源状态域。
        KiteResourceInstallStore(appContext)
    }
    val resourceManifestLoader: KiteResourceManifestLoader by lazy { KiteResourceManifestLoader(appContext) }
    val customAgentRegistrationStore: KiteCustomAgentRegistrationStore by lazy {
        KiteCustomAgentRegistrationStore(appContext)
    }
    val agentConfigAdapterRegistry: AgentConfigAdapterRegistry by lazy {
        AgentConfigAdapterRegistry(
            defaultAgentConfigAdapters(
                appContext,
                commandExecutor = AndroidAgentConfigCommandExecutor(appContext)
            )
        )
    }
    val agentRegistry: KiteAgentRegistry by lazy {
        KiteAgentRegistry(
            context = appContext,
            manifestLoader = resourceManifestLoader,
            installStore = resourceInstallStore,
            customStore = customAgentRegistrationStore
        )
    }
    val agentOfficialAccountManager: AgentOfficialAccountManager by lazy {
        AgentOfficialAccountManager(
            scope = processScope,
            registry = agentRegistry,
            commandRunner = AndroidAgentOfficialAccountCommandRunner(
                context = appContext,
                openExternal = { url -> AndroidExternalBrowserLauncher.open(appContext, url) }
            )
        )
    }
    val recipeLoader: KiteRecipeLoader by lazy { KiteRecipeLoader(appContext, diagnostics) }
    val cardGroupStore: KiteCardGroupStore by lazy { KiteCardGroupStore(appContext) }
    val resourceFeatureGateway: ResourceFeatureGateway by lazy {
        AndroidResourceFeatureGateway.create(
            manifestLoader = resourceManifestLoader,
            installStore = resourceInstallStore,
            nodeRuntimeInstalled = {
                resourceInstallStore.isInstalled(ToolchainPackInstaller.RESOURCE_NODEJS)
            }
        )
    }
    val recipeFeatureGateway: RecipeFeatureGateway by lazy {
        AndroidRecipeFeatureGateway.create(
            appContext,
            recipeLoader,
            cardGroupStore,
            createDropZoneManager(),
            resourceInstallStore::currentEnvironmentId
        )
    }
    val runtimeManagementGateway: RuntimeManagementGateway by lazy {
        AndroidRuntimeManagementGateway(appContext, resourceInstallStore::currentEnvironmentId)
    }
    val runtimeBootstrapGateway: RuntimeBootstrapGateway by lazy {
        AndroidRuntimeBootstrapGateway(appContext)
    }
    val prootViewInspectionGateway: com.kite.app.application.runtimemanagement.ProotViewInspectionGateway by lazy {
        com.kite.app.platform.runtimemanagement.AndroidProotViewInspectionGateway(appContext)
    }
    val firstRunOnboardingCoordinator: FirstRunOnboardingCoordinator by lazy {
        FirstRunOnboardingCoordinator(AndroidFirstRunOnboardingStore(appContext))
    }
    val settingsGateway: SettingsGateway by lazy {
        AndroidSettingsGateway(
            context = appContext,
            readAppLanguage = AndroidAppLocaleOwner::current,
            applyAppLanguage = AndroidAppLocaleOwner::apply,
            readDropZone = {
                createDropZoneManager().prepareDropZone().let { status ->
                    SettingsDropZoneSnapshot(status.available)
                }
            }
        )
    }
    val runExecutionEffectBus: RunExecutionEffectBus by lazy { RunExecutionEffectBus() }
    val runHistoryGateway: RunHistoryGateway by lazy {
        AndroidRunHistoryGateway(resourceInstallStore::currentEnvironmentId)
    }
    private val recipeExecutor: AndroidRecipeExecutor by lazy {
        AndroidRecipeExecutor(
            appContext,
            bridgeClient,
            diagnostics,
            // 普通卡片、终端与资源运行固定走原生 PRoot。View 环境只能由更新事务等
            // 显式调用方逐次注入，不能因运行实例带有 environmentId 就全局回到 View。
            RunExecutionEnvironmentProvider.None
        )
    }
    val runWindowSurfaceGateway: AndroidRunWindowSurfaceGateway by lazy {
        AndroidRunWindowSurfaceGateway(
            context = appContext,
            diagnostics = diagnostics,
            executor = recipeExecutor,
            effectSink = runExecutionEffectBus
        )
    }
    val runLifecycleEventHub: RunLifecycleEventHub by lazy { RunLifecycleEventHub() }
    val recipeActionGateway: AndroidRecipeActionGateway by lazy {
        AndroidRecipeActionGateway(
            runOrchestrator,
            diagnostics,
            resourceInstallStore::currentEnvironmentId
        )
    }
    val runNotificationCoordinator: AndroidRunNotificationCoordinator by lazy {
        AndroidRunNotificationCoordinator(
            context = appContext,
            recipeResolver = ::resolveRecipe,
            restartRecipeResolver = ::resolveLatestRecipe,
            completeStep = { command -> runOrchestrator.completeStep(command) },
            closeRun = { recipe, state -> recipeActionGateway.stop(recipe, state) },
            restartRun = { recipe, state ->
                recipeActionGateway.start(recipe, state, state.instanceId).command
            },
            closeRunTask = { instanceId, generation -> CardRunTaskCloser.close(instanceId, generation) },
            viewBinder = AndroidRunNotificationViewBinder(appContext),
            environmentIdProvider = resourceInstallStore::currentEnvironmentId
        )
    }
    val runOrchestrator: RunOrchestrator by lazy {
        RunOrchestrator(
            stateGateway = AndroidRunStateGateway(resourceInstallStore::currentEnvironmentId),
            executor = recipeExecutor,
            effectSink = runExecutionEffectBus,
            lifecycleSink = runLifecycleEventHub,
            startGate = RunStartGate(runNotificationCoordinator::startRejectionReason),
            ownedWindowGateway = runWindowSurfaceGateway
        )
    }
    val recipeActionWorkflowCoordinator: RecipeActionWorkflowCoordinator by lazy {
        RecipeActionWorkflowCoordinator(
            planner = KiteRecipeActionCoordinator(KiteActionRouter()),
            gateway = recipeActionGateway
        )
    }
    val desktopOpenCoordinator: DesktopOpenCoordinator by lazy {
        DesktopOpenCoordinator(
            AndroidDesktopOpenGateway(
                appContext,
                diagnostics,
                ::resolveRecipe,
                resourceInstallStore::currentEnvironmentId
            )
        )
    }
    val runtimeOwnerProbeCoordinator: RuntimeOwnerProbeCoordinator by lazy {
        RuntimeOwnerProbeCoordinator(
            AndroidRuntimeOwnerProbeGateway(
                runOrchestrator,
                diagnostics,
                resourceInstallStore::currentEnvironmentId
            )
        )
    }
    val runtimeManagementCoordinator: RuntimeManagementCoordinator by lazy {
        RuntimeManagementCoordinator(
            gateway = runtimeManagementGateway,
            stopRun = { instanceId ->
                when (val result = runOrchestrator.stop(instanceId)) {
                    is com.kite.app.application.runs.RunCommandResult.Accepted ->
                        RuntimeManagementDispatchResult.accepted("run_stop_requested")
                    is com.kite.app.application.runs.RunCommandResult.Ignored ->
                        RuntimeManagementDispatchResult.rejected(result.reason)
                }
            }
        )
    }
    private val resourceRecipeFactory: AndroidResourceRecipeFactory by lazy {
        AndroidResourceRecipeFactory(resourceManifestLoader)
    }
    val resourceRunCoordinator: ResourceRunCoordinator by lazy {
        ResourceRunCoordinator(
            gateway = AndroidResourceRunGateway(
                context = appContext,
                installStore = resourceInstallStore,
                manifestLoader = resourceManifestLoader,
                recipeFactory = resourceRecipeFactory,
                diagnostics = diagnostics
            ),
            runOrchestrator = runOrchestrator,
            lifecycleHub = runLifecycleEventHub
        )
    }
    val resourceActionWorkflowCoordinator: ResourceActionWorkflowCoordinator by lazy {
        ResourceActionWorkflowCoordinator(
            AndroidResourceActionGateway(
                context = appContext,
                installStore = resourceInstallStore,
                manifestLoader = resourceManifestLoader,
                runCoordinator = resourceRunCoordinator,
                runOrchestrator = runOrchestrator,
                recipeLoader = recipeLoader,
                recipeFeatureGateway = recipeFeatureGateway,
                bridgeClient = bridgeClient,
                diagnostics = diagnostics,
                backgroundScope = processScope,
                versionBatchObserver = { summary: ResourceVersionBatchSummary ->
                    Log.i(
                        "KiteVersionBatchRoute",
                        "total=${summary.total} structuredNativeRemote=${summary.structuredNativeRemote} " +
                            "prootCompatibility=${summary.prootCompatibility} " +
                            "maxStructuredNativeRemote=${summary.maxStructuredNativeRemote} " +
                            "maxProotCompatibility=${summary.maxProotCompatibility}",
                    )
                },
                versionCoordinator = ResourceVersionCoordinator(
                    AndroidResourceVersionGateway(
                        bridgeClient = bridgeClient,
                        metadataContextProvider = {
                            StructuredJsonStringContext(
                                listOf(
                                    StructuredJsonStringRoot(
                                        containerPath = "/workspace",
                                        directory = KFContainerManager.resolveWorkspaceDirectory(appContext),
                                    )
                                )
                            )
                        },
                        routeObserver = { event ->
                            Log.i(
                                "KiteVersionRoute",
                                "route=${event.route.name.lowercase()} reason=${event.reason}",
                            )
                        },
                    )
                )
            )
        )
    }
    val runInstanceCloseCoordinator: RunInstanceCloseCoordinator by lazy {
        RunInstanceCloseCoordinator(
            scope = processScope,
            state = CardRunStore::get,
            stopRun = { command -> runOrchestrator.stop(command) },
            cancelInstallWizard = { state ->
                val targetResourceId = state.stepId.orEmpty()
                val plan = resourceInstallStore.planSnapshot(state.environmentId)
                if (state.status in INSTALL_WIZARD_ENDED_STATUSES) {
                    CardRunStore.removeRun(state.instanceId, state.createdAt) != null
                } else if (
                    targetResourceId.isNotBlank() &&
                    plan.targetResourceId == targetResourceId
                ) {
                    resourceActionWorkflowCoordinator.cancelInstallWizard(
                        targetResourceId = targetResourceId,
                        planResourceIds = plan.resourceIds,
                        environmentId = state.environmentId,
                        instanceId = state.instanceId,
                        expectedGeneration = state.createdAt,
                    )
                } else {
                    CardRunStore.removeRun(state.instanceId, state.createdAt) != null
                }
            },
        )
    }

    fun createRecipeLoader(): KiteRecipeLoader = recipeLoader

    fun createDropZoneManager(): KiteDropZoneManager = KiteDropZoneManager(appContext, diagnostics)

    fun createBrowserHandoffCoordinator(
        recipeResolver: (String) -> KiteRecipe?,
        openExternal: (String) -> Boolean
    ): BrowserHandoffCoordinator = BrowserHandoffCoordinator(
        AndroidBrowserHandoffGateway(
            sessions = browserAuthSessions,
            loopbackBridge = browserLoopbackCallbackBridge,
            diagnostics = diagnostics,
            recipeResolver = recipeResolver,
            openExternal = openExternal
        )
    )

    private fun resolveRecipe(recipeId: String): KiteRecipe? =
        CardRunStore.registeredRecipe(recipeId)
            ?: recipeLoader.loadAllRecipes().firstOrNull { it.id == recipeId }

    private fun resolveLatestRecipe(recipeId: String): KiteRecipe? =
        recipeLoader.loadAllRecipes().firstOrNull { it.id == recipeId }

    companion object {
        private val INSTALL_WIZARD_ENDED_STATUSES = setOf(
            CardRunStatus.Unknown,
            CardRunStatus.Stopped,
            CardRunStatus.Completed,
            CardRunStatus.Failed,
            CardRunStatus.BridgeUnavailable,
        )

        @Volatile
        private var instance: KiteAppGraph? = null

        fun from(context: Context): KiteAppGraph =
            instance ?: synchronized(this) {
                instance ?: KiteAppGraph(context).also { instance = it }
            }
    }
}
