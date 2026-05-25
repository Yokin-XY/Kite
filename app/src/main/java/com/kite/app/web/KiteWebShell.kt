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
                return if (isLocalUrl(url)) {
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
        if (isLocalUrl(url)) {
            webView.loadUrl(url)
        } else {
            openExternal(url)
        }
    }

    private fun openExternal(url: String) {
        diagnostics.logExternalUrl(url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        activity.startActivity(intent)
        onStatus("Opened in system browser: $url")
    }

    private fun isLocalUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme ?: return false
        val host = uri.host ?: return false
        return scheme == "http" && (host == "127.0.0.1" || host == "localhost")
    }

    fun capabilitySummary(): Map<String, Any> = mapOf(
        "webviewFeatureProxyOverride" to WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    )
}
