package com.kite.app.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewFeature
import com.kite.app.diagnostics.KiteDiagnostics
import java.net.URI

class KiteWebShell(
    private val activity: Activity,
    private val webView: WebView,
    private val diagnostics: KiteDiagnostics,
    private val onStatus: (String) -> Unit
) {
    private var currentRecipeId: String? = null
    private var currentRecipeName: String? = null
    private var currentOpenSource: String? = null
    private var pendingHttpAuth: BasicHttpAuth? = null

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

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

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String
            ) {
                val auth = pendingHttpAuth
                if (auth != null && auth.matches(host, view.url)) {
                    handler.proceed(auth.username, auth.password)
                } else {
                    super.onReceivedHttpAuthRequest(view, handler, host, realm)
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
        val preparedUrl = prepareBasicAuthUrl(url)
        if (shouldStayInWebView(preparedUrl)) {
            webView.loadUrl(preparedUrl)
        } else {
            openExternal(preparedUrl)
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
        webView.loadUrl(prepareBasicAuthUrl(url))
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

    private fun prepareBasicAuthUrl(url: String): String {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return url
        val userInfo = parsed.userInfo?.takeIf { it.isNotBlank() } ?: return url
        val host = parsed.host?.takeIf { it.isNotBlank() } ?: return url
        val parts = userInfo.split(":", limit = 2)
        val username = parts.getOrNull(0).orEmpty()
        val password = parts.getOrNull(1).orEmpty()
        if (username.isBlank()) return url
        pendingHttpAuth = BasicHttpAuth(host = host, port = parsed.port, username = username, password = password)
        return runCatching {
            URI(parsed.scheme, null, host, parsed.port, parsed.path, parsed.query, parsed.fragment).toString()
        }.getOrDefault(url)
    }

    private data class BasicHttpAuth(
        val host: String,
        val port: Int,
        val username: String,
        val password: String
    ) {
        fun matches(requestHost: String, currentUrl: String?): Boolean {
            if (!requestHost.equals(host, ignoreCase = true)) return false
            val currentPort = runCatching { Uri.parse(currentUrl).port }.getOrDefault(-1)
            return port <= 0 || currentPort <= 0 || currentPort == port
        }
    }
}
