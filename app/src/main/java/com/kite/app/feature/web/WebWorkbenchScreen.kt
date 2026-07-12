package com.kite.app.feature.web

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import com.kite.app.browser.BrowserHandoffLauncher
import com.kite.app.browser.automation.BrowserAutomationController
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.web.KiteWebShell

/** 普通工作台的唯一可见绑定，拥有 WebView、网页历史和自动化显示会话。 */
internal class WebWorkbenchScreen(
    private val activity: Activity,
    private val pageBackground: Int,
    private val textPrimary: Int,
    diagnostics: KiteDiagnostics,
    automationSessions: BrowserAutomationSessionStore,
    private val onExit: () -> Unit,
    onLaunchHandoff: BrowserHandoffLauncher,
    webViewFactory: (Activity) -> WebView = ::WebView
) {
    private val webView = webViewFactory(activity)
    private val automationController = BrowserAutomationController(
        webView = webView,
        store = automationSessions,
        onEvent = { }
    )
    private val webShell = KiteWebShell(
        activity = activity,
        webView = webView,
        diagnostics = diagnostics,
        onStatus = { },
        browserHandoffLauncher = onLaunchHandoff,
        browserAutomationController = automationController
    )
    private var initialUrl: String = ""
    private var disposed = false

    val root: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pageBackground)
        addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun open(target: WebWorkbenchTarget) {
        check(!disposed) { "WebWorkbenchScreen 已释放" }
        initialUrl = target.url
        webShell.open(
            url = target.url,
            recipeId = target.recipeId,
            recipeName = target.recipeName,
            instanceId = target.instanceId,
            openSource = target.source,
            automationEnabled = target.automationEnabled
        )
    }

    fun handleBack(): Boolean {
        if (disposed || !webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    fun currentUrl(): String = webView.url?.takeIf(String::isNotBlank) ?: initialUrl

    fun onResume() {
        if (!disposed) webView.onResume()
    }

    fun onPause() {
        if (!disposed) webView.onPause()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        automationController.closeActiveSession()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
        root.removeAllViews()
    }

    internal fun isDisposedForTest(): Boolean = disposed

    private fun topBar(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(10))
        addView(TextView(context).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(textPrimary)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                if (!handleBack()) onExit()
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(TextView(context).apply {
            text = "Kite 工作台"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(View(context), LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
