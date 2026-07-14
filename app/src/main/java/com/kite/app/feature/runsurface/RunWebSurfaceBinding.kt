package com.kite.app.feature.runsurface

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffLauncher
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.automation.BrowserAutomationController
import com.kite.app.browser.automation.BrowserAutomationEvent
import com.kite.app.browser.automation.BrowserAutomationSessionStore
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit
import com.kite.app.web.KiteWebNavigationState
import com.kite.app.web.KiteWebShell

/** Web 显示绑定拥有自己的 WebView；认证会话与运行事实仍由外部 Gateway 拥有。 */
internal class RunWebSurfaceBinding(
    private val activity: Activity,
    private val tokens: ThemeTokens,
    private val diagnostics: KiteDiagnostics,
    private val automationSessions: BrowserAutomationSessionStore,
    private val automationEnabled: () -> Boolean,
    private val onAutomationEvent: (BrowserAutomationEvent) -> Unit,
    private val onLaunchHandoff: (BrowserHandoffRequest, BrowserHandoffDecision, Boolean) -> Boolean,
    private val onOpenExternal: (String) -> Boolean,
    private val onManualUrl: (String) -> Unit,
    private val onNavigationState: (RunWebNavigationUiState) -> Unit
) : RunSurfaceBinding {
    private val ui = UiKit(activity, tokens)
    private var webView: WebView? = null
    private var webShell: KiteWebShell? = null
    private var automationController: BrowserAutomationController? = null
    private var renderedKey = ""
    private var disposed = false
    private var navigationState = RunWebNavigationUiState()

    override val root: FrameLayout = FrameLayout(activity).apply {
        setBackgroundColor(tokens.pageBackground)
    }

    override fun render(state: RunSurfaceUiState) {
        val content = state.content as? RunSurfaceContent.Web ?: return
        val url = content.url?.trim().orEmpty()
        val nextKey = "${state.target.instanceId}|$url"
        if (nextKey == renderedKey) return
        renderedKey = nextKey
        if (url.isBlank()) {
            releaseWebRuntime()
            root.removeAllViews()
            publishNavigation(RunWebNavigationUiState())
            root.addView(addressInputBody(), matchParent())
            return
        }
        val request = BrowserHandoffRequest(
            url = url,
            recipeId = state.target.recipeId,
            recipeName = state.title,
            instanceId = state.target.instanceId,
            source = OPEN_SOURCE
        )
        when (val decision = BrowserHandoffPolicy.classify(url, OPEN_SOURCE)) {
            is BrowserHandoffDecision.StartAuthHandoff,
            is BrowserHandoffDecision.StartCliCallbackHandoff -> {
                releaseWebRuntime()
                root.removeAllViews()
                publishNavigation(RunWebNavigationUiState(url = url))
                root.addView(authWaitingBody(request, decision), matchParent())
                onLaunchHandoff(request, decision, false)
            }
            BrowserHandoffDecision.OpenExternalBrowser -> {
                releaseWebRuntime()
                root.removeAllViews()
                publishNavigation(RunWebNavigationUiState(url = url))
                val opened = onOpenExternal(url)
                root.addView(externalBrowserBody(url, opened), matchParent())
            }
            is BrowserHandoffDecision.ShowUnsupportedFallback,
            BrowserHandoffDecision.StayInWebView -> loadInWebView(state, url)
        }
    }

    override fun handleBack(): Boolean {
        val current = webView ?: return false
        if (!current.canGoBack()) return false
        current.goBack()
        return true
    }

    override fun reload(): Boolean {
        val current = webView ?: return false
        current.reload()
        return true
    }

    override fun goForward(): Boolean {
        val current = webView ?: return false
        if (!current.canGoForward()) return false
        current.goForward()
        return true
    }

    override fun stopLoading(): Boolean {
        val current = webView ?: return false
        current.stopLoading()
        publishNavigation(navigationState.copy(loading = false))
        return true
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        releaseWebRuntime()
        root.removeAllViews()
    }

    private fun loadInWebView(state: RunSurfaceUiState, url: String) {
        root.removeAllViews()
        val existingView = webView
        val existingShell = webShell
        if (existingView != null && existingShell != null) {
            root.addView(existingView, matchParent())
            existingShell.loadInWebView(
                url = url,
                recipeId = state.target.recipeId,
                recipeName = state.title,
                instanceId = state.target.instanceId,
                openSource = OPEN_SOURCE,
                automationEnabled = automationEnabled()
            )
            return
        }
        val currentWebView = WebView(activity)
        val controller = BrowserAutomationController(
            webView = currentWebView,
            store = automationSessions,
            onEvent = { event -> activity.runOnUiThread { onAutomationEvent(event) } }
        )
        val shell = KiteWebShell(
            activity = activity,
            webView = currentWebView,
            diagnostics = diagnostics,
            onStatus = {},
            browserHandoffLauncher = BrowserHandoffLauncher { request, decision ->
                onLaunchHandoff(request, decision, false)
            },
            browserAutomationController = controller,
            onNavigationState = ::handleNavigationState
        )
        webView = currentWebView
        automationController = controller
        webShell = shell
        root.addView(currentWebView, matchParent())
        shell.loadInWebView(
            url = url,
            recipeId = state.target.recipeId,
            recipeName = state.title,
            instanceId = state.target.instanceId,
            openSource = OPEN_SOURCE,
            automationEnabled = automationEnabled()
        )
    }

    private fun handleNavigationState(state: KiteWebNavigationState) {
        publishNavigation(
            RunWebNavigationUiState(
                url = state.url,
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                loading = state.loading,
                progress = state.progress
            )
        )
    }

    private fun publishNavigation(state: RunWebNavigationUiState) {
        if (navigationState == state) return
        navigationState = state
        onNavigationState(state)
    }

    private fun releaseWebRuntime() {
        automationController?.closeActiveSession()
        automationController = null
        webShell = null
        webView?.let { current ->
            (current.parent as? ViewGroup)?.removeView(current)
            current.stopLoading()
            current.onPause()
            current.removeAllViews()
            current.destroy()
        }
        webView = null
    }

    private fun authWaitingBody(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision
    ): View = centeredBody {
        val isLoopback = decision is BrowserHandoffDecision.StartCliCallbackHandoff
        addView(ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(ui.dp(40), ui.dp(40))
        })
        addView(title(if (isLoopback) "正在等待浏览器回调" else "正在等待浏览器登录返回").apply {
            setPadding(0, ui.dp(18), 0, 0)
        })
        addView(detail(if (isLoopback) {
            "登录页已用安全浏览器打开。回调会通过 Android 本机 loopback 原样交给登录发起方，由发起方校验并保存登录状态。"
        } else {
            "登录页已用安全浏览器打开。返回后 Kite 会校验 state，并把结果交回当前运行实例。"
        }).apply { setPadding(0, ui.dp(10), 0, 0) })
        addView(actionRow(
            firstLabel = "重新打开",
            firstAction = { onLaunchHandoff(request, decision, true) },
            secondLabel = "复制地址",
            secondAction = { copyUrl("Kite login URL", request.url, "已复制登录地址") }
        ))
    }

    private fun externalBrowserBody(url: String, opened: Boolean): View = centeredBody {
        addView(title(if (opened) "已在系统浏览器打开" else "无法打开系统浏览器").apply {
            setTextColor(if (opened) tokens.textPrimary else tokens.danger)
        })
        addView(detail(if (opened) {
            "登录页使用系统浏览器承载。完成后请按网页或工具提示继续。"
        } else {
            "Kite 没能启动系统浏览器。可以复制地址后手动打开。"
        }).apply { setPadding(0, ui.dp(10), 0, 0) })
        addView(actionRow(
            firstLabel = "重新打开",
            firstAction = { onOpenExternal(url) },
            secondLabel = "复制地址",
            secondAction = { copyUrl("Kite external URL", url, "已复制地址") }
        ))
    }

    private fun addressInputBody(): View = FrameLayout(activity).apply {
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
            setPadding(ui.dp(18), 0, ui.dp(8), 0)
        }
        val submit = { onManualUrl(input.text?.toString().orEmpty()) }
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
            setMargins(ui.dp(24), 0, ui.dp(24), ui.dp(108))
        })
        addView(FrameLayout(context).apply {
            background = ui.roundedBox(Color.WHITE, Color.rgb(198, 205, 216), ui.dp(26).toFloat(), ui.dp(1))
            elevation = ui.dp(1).toFloat()
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 0, ui.dp(54), 0)
            })
            addView(TextView(context).apply {
                text = "⌕"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(20, 24, 33))
                setOnClickListener { submit() }
            }, FrameLayout.LayoutParams(ui.dp(52), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54), Gravity.CENTER).apply {
            setMargins(ui.dp(24), 0, ui.dp(24), 0)
        })
        input.post {
            if (disposed) return@post
            input.requestFocus()
            (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun centeredBody(content: LinearLayout.() -> Unit): View = FrameLayout(activity).apply {
        setBackgroundColor(tokens.pageBackground)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(28))
            content()
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    private fun title(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun detail(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 13f
        setTextColor(tokens.textSecondary)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun actionRow(
        firstLabel: String,
        firstAction: () -> Unit,
        secondLabel: String,
        secondAction: () -> Unit
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, ui.dp(20), 0, 0)
        addView(actionButton(firstLabel, firstAction), LinearLayout.LayoutParams(ui.dp(112), ui.dp(36)))
        addView(actionButton(secondLabel, secondAction), LinearLayout.LayoutParams(ui.dp(112), ui.dp(36)).apply {
            setMargins(ui.dp(10), 0, 0, 0)
        })
    }

    private fun actionButton(label: String, action: () -> Unit): TextView = TextView(activity).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(tokens.primaryStrong)
        background = ui.roundedBox(tokens.primarySubtle, tokens.primarySoft, ui.dp(14).toFloat())
        setOnClickListener { action() }
    }

    private fun copyUrl(label: String, url: String, toast: String) {
        (activity.getSystemService(Activity.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText(label, url))
        Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
    }

    private fun matchParent(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    private companion object {
        const val OPEN_SOURCE = "card_run_surface"
    }
}
