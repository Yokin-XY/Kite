package com.kite.app.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewFeature
import com.kite.app.diagnostics.KiteDiagnostics

class KiteWebShell(
    private val activity: Activity,
    private val webView: WebView,
    private val diagnostics: KiteDiagnostics,
    private val onStatus: (String) -> Unit
) {
    private var currentRecipeId: String? = null
    private var currentRecipeName: String? = null
    private var currentOpenSource: String? = null

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                diagnostics.logConsole(consoleMessage)
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (shouldStayInWebView(url)) {
                    false
                } else {
                    openExternal(url)
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                diagnostics.writeWebAppStatus(
                    url = url,
                    title = view.title ?: currentRecipeName,
                    state = "loaded",
                    recipeId = currentRecipeId,
                    recipeName = currentRecipeName,
                    openSource = currentOpenSource
                )
                onStatus("Loaded: $url")
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    diagnostics.logWebError(
                        url = request.url.toString(),
                        code = error.errorCode,
                        description = error.description?.toString().orEmpty()
                    )
                    onStatus("Load failed: ${request.url}")
                }
            }
        }
    }

    fun open(
        url: String,
        recipeId: String? = null,
        recipeName: String? = null,
        openSource: String? = null
    ) {
        currentRecipeId = recipeId
        currentRecipeName = recipeName
        currentOpenSource = openSource
        if (shouldStayInWebView(url)) {
            webView.loadUrl(url)
        } else {
            openExternal(url)
        }
    }

    fun loadInWebView(
        url: String,
        recipeId: String? = null,
        recipeName: String? = null,
        openSource: String? = null
    ) {
        currentRecipeId = recipeId
        currentRecipeName = recipeName
        currentOpenSource = openSource
        webView.loadUrl(url)
    }

    private fun openExternal(url: String) {
        diagnostics.logExternalUrl(url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching {
            activity.startActivity(intent)
        }.onSuccess {
            onStatus("Opened in system browser: $url")
        }.onFailure { error ->
            diagnostics.logWebError(
                url = url,
                code = -1,
                description = "external_open_failed:${error.javaClass.simpleName}:${error.message.orEmpty()}"
            )
            onStatus("External URL ignored: $url")
        }
    }

    private fun isLocalUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme ?: return false
        val host = uri.host ?: return false
        return scheme == "http" && (host == "127.0.0.1" || host == "localhost")
    }

    private fun shouldStayInWebView(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme ?: return false
        if (scheme !in setOf("http", "https")) return false
        if (isLocalUrl(url)) return true
        val source = currentOpenSource.orEmpty()
        return source in setOf(
            "card_run_surface",
            "browser_proxy",
            "ubuntu_browser",
            "terminal_page",
            "terminal_step",
            "shell_step"
        )
    }

    fun capabilitySummary(): Map<String, Any> = mapOf(
        "webviewFeatureProxyOverride" to WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    )
}
