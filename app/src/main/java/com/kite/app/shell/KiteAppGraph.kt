package com.kite.app.shell

import android.content.Context
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.browser.BrowserHandoffCoordinator
import com.kite.app.application.browser.BrowserAuthRedirectCoordinator
import com.kite.app.application.resources.ResourceRunCoordinator
import com.kite.app.application.resources.ResourceActionWorkflowCoordinator
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.runs.RunExecutionEffectBus
import com.kite.app.application.runs.RunLifecycleEventHub
import com.kite.app.application.runs.RunHistoryGateway
import com.kite.app.application.runs.RunOrchestrator
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
import com.kite.app.platform.resources.AndroidResourceFeatureGateway
import com.kite.app.platform.browser.AndroidBrowserHandoffGateway
import com.kite.app.platform.browser.AndroidBrowserAuthRedirectGateway
import com.kite.app.platform.browser.AndroidExternalBrowserLauncher
import com.kite.app.recipe.KiteRecipe
import com.kite.app.platform.resources.AndroidResourceRecipeFactory
import com.kite.app.platform.resources.AndroidResourceRunGateway
import com.kite.app.platform.resources.AndroidResourceActionGateway
import com.kite.app.platform.recipes.AndroidRecipeFeatureGateway
import com.kite.app.platform.runs.AndroidRecipeExecutor
import com.kite.app.platform.runs.AndroidRunHistoryGateway
import com.kite.app.platform.runs.AndroidRunStateGateway
import com.kite.app.platform.runtimemanagement.AndroidRuntimeManagementGateway
import com.kite.app.platform.runtimebootstrap.AndroidRuntimeBootstrapGateway
import com.kite.app.platform.onboarding.AndroidFirstRunOnboardingStore
import com.kite.app.platform.settings.AndroidSettingsGateway
import com.kite.app.run.CardRunStore

/**
 * Kite 进程级组合根。这里只装配已有能力，不承载页面状态或业务流程。
 */
internal class KiteAppGraph private constructor(context: Context) {
    private val appContext = context.applicationContext

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
    val resourceInstallStore: KiteResourceInstallStore by lazy { KiteResourceInstallStore(appContext) }
    val resourceManifestLoader: KiteResourceManifestLoader by lazy { KiteResourceManifestLoader(appContext) }
    val recipeLoader: KiteRecipeLoader by lazy { KiteRecipeLoader(appContext, diagnostics) }
    val cardGroupStore: KiteCardGroupStore by lazy { KiteCardGroupStore(appContext) }
    val resourceFeatureGateway: ResourceFeatureGateway by lazy {
        AndroidResourceFeatureGateway.create(
            manifestLoader = resourceManifestLoader,
            installStore = resourceInstallStore,
            nodeRuntimeInstalled = { ToolchainPackInstaller.isNodeRuntimeInstalled(appContext) }
        )
    }
    val recipeFeatureGateway: RecipeFeatureGateway by lazy {
        AndroidRecipeFeatureGateway.create(appContext, recipeLoader, cardGroupStore, createDropZoneManager())
    }
    val runtimeManagementGateway: RuntimeManagementGateway by lazy {
        AndroidRuntimeManagementGateway(appContext)
    }
    val runtimeBootstrapGateway: RuntimeBootstrapGateway by lazy {
        AndroidRuntimeBootstrapGateway(appContext)
    }
    val firstRunOnboardingCoordinator: FirstRunOnboardingCoordinator by lazy {
        FirstRunOnboardingCoordinator(AndroidFirstRunOnboardingStore(appContext))
    }
    val settingsGateway: SettingsGateway by lazy {
        AndroidSettingsGateway(
            context = appContext,
            readDropZone = {
                createDropZoneManager().prepareDropZone().let { status ->
                    SettingsDropZoneSnapshot(status.available, status.message)
                }
            }
        )
    }
    val runExecutionEffectBus: RunExecutionEffectBus by lazy { RunExecutionEffectBus() }
    val runHistoryGateway: RunHistoryGateway by lazy { AndroidRunHistoryGateway() }
    val runLifecycleEventHub: RunLifecycleEventHub by lazy { RunLifecycleEventHub() }
    val runOrchestrator: RunOrchestrator by lazy {
        RunOrchestrator(
            stateGateway = AndroidRunStateGateway(),
            executor = AndroidRecipeExecutor(appContext, bridgeClient, diagnostics),
            effectSink = runExecutionEffectBus,
            lifecycleSink = runLifecycleEventHub
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
                bridgeClient = bridgeClient
            )
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

    companion object {
        @Volatile
        private var instance: KiteAppGraph? = null

        fun from(context: Context): KiteAppGraph =
            instance ?: synchronized(this) {
                instance ?: KiteAppGraph(context).also { instance = it }
            }
    }
}
