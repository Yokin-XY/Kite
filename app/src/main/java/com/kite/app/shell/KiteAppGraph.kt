package com.kite.app.shell

import android.content.Context
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.browser.BrowserAuthSessionStore
import com.kite.app.browser.BrowserLoopbackCallbackBridge
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.dropzone.KiteDropZoneManager
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader

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

    fun createRecipeLoader(): KiteRecipeLoader = KiteRecipeLoader(appContext, diagnostics)

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
