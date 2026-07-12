package com.kite.app.shell

import android.content.Context
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.recipes.RecipeFeatureGateway
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
import com.kite.app.platform.recipes.AndroidRecipeFeatureGateway

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

    fun createRecipeLoader(): KiteRecipeLoader = recipeLoader

    fun createDropZoneManager(): KiteDropZoneManager = KiteDropZoneManager(appContext, diagnostics)

    companion object {
        @Volatile
        private var instance: KiteAppGraph? = null

        fun from(context: Context): KiteAppGraph =
            instance ?: synchronized(this) {
                instance ?: KiteAppGraph(context).also { instance = it }
            }
    }
}
