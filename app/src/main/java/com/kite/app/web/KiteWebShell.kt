package com.kite.app.web

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.HttpAuthHandler
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewFeature
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffLauncher
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.automation.BrowserAutomationController
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.foundation.runtime.ExternalExchangeManager
import java.io.File
import java.net.URI

data class KiteWebNavigationState(
    val url: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val loading: Boolean,
    val progress: Int
)

class KiteWebShell(
    private val activity: Activity,
    private val webView: WebView,
    private val diagnostics: KiteDiagnostics,
    private val onStatus: (String) -> Unit,
    private val browserHandoffLauncher: BrowserHandoffLauncher? = null,
    private val browserAutomationController: BrowserAutomationController? = null,
    private val onNavigationState: (KiteWebNavigationState) -> Unit = {}
) {
    private var currentRecipeId: String? = null
    private var currentRecipeName: String? = null
    private var currentInstanceId: String? = null
    private var currentOpenSource: String? = null
    private var pendingHttpAuth: BasicHttpAuth? = null
    private var lastNavigationState: KiteWebNavigationState? = null

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
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                publishNavigationState(
                    url = view.url,
                    loading = newProgress < 100,
                    progress = newProgress
                )
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                diagnostics.logConsole(consoleMessage)
                browserAutomationController?.recordConsoleMessage(
                    level = consoleMessage.messageLevel().name,
                    message = consoleMessage.message().orEmpty(),
                    sourceId = consoleMessage.sourceId(),
                    lineNumber = consoleMessage.lineNumber()
                )
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return handleNavigation(url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                publishNavigationState(url = url, loading = true, progress = 0)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                publishNavigationState(url = url)
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
                publishNavigationState(url = url, loading = false, progress = 100)
                browserAutomationController?.onPageFinished(url, view.title)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                browserAutomationController?.recordNetworkRequest(
                    method = request.method.orEmpty(),
                    url = request.url.toString(),
                    isForMainFrame = request.isForMainFrame
                )
                return super.shouldInterceptRequest(view, request)
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
                    publishNavigationState(
                        url = request.url.toString(),
                        loading = false,
                        progress = 100
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                browserAutomationController?.recordNetworkHttpError(
                    method = request.method.orEmpty(),
                    url = request.url.toString(),
                    isForMainFrame = request.isForMainFrame,
                    statusCode = errorResponse.statusCode,
                    reasonPhrase = errorResponse.reasonPhrase
                )
                super.onReceivedHttpError(view, request, errorResponse)
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

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    fun open(
        url: String,
        recipeId: String? = null,
        recipeName: String? = null,
        instanceId: String? = null,
        openSource: String? = null,
        automationEnabled: Boolean = false
    ) {
        currentRecipeId = recipeId
        currentRecipeName = recipeName
        currentInstanceId = instanceId
        currentOpenSource = openSource
        val preparedUrl = prepareBasicAuthUrl(url)
        if (handleInitialUrl(preparedUrl)) {
            browserAutomationController?.closeActiveSession()
            return
        }
        if (shouldStayInWebView(preparedUrl)) {
            browserAutomationController?.prepareLoad(
                enabled = automationEnabled,
                recipeId = recipeId,
                recipeName = recipeName,
                instanceId = instanceId,
                source = openSource,
                url = preparedUrl
            )
            webView.loadUrl(preparedUrl)
            publishNavigationState(preparedUrl, loading = true, progress = 0)
        } else {
            browserAutomationController?.closeActiveSession()
            openExternal(preparedUrl)
        }
    }

    fun loadInWebView(
        url: String,
        recipeId: String? = null,
        recipeName: String? = null,
        instanceId: String? = null,
        openSource: String? = null,
        automationEnabled: Boolean = false
    ) {
        currentRecipeId = recipeId
        currentRecipeName = recipeName
        currentInstanceId = instanceId
        currentOpenSource = openSource
        val preparedUrl = prepareBasicAuthUrl(url)
        if (handleInitialUrl(preparedUrl)) {
            browserAutomationController?.closeActiveSession()
            return
        }
        browserAutomationController?.prepareLoad(
            enabled = automationEnabled,
            recipeId = recipeId,
            recipeName = recipeName,
            instanceId = instanceId,
            source = openSource,
            url = preparedUrl
        )
        webView.loadUrl(preparedUrl)
        publishNavigationState(preparedUrl, loading = true, progress = 0)
    }

    private fun publishNavigationState(
        url: String? = null,
        loading: Boolean? = null,
        progress: Int? = null
    ) {
        val previous = lastNavigationState
        val nextProgress = (progress ?: previous?.progress ?: 0).coerceIn(0, 100)
        val next = KiteWebNavigationState(
            url = url ?: webView.url.orEmpty().ifBlank { previous?.url.orEmpty() },
            canGoBack = webView.canGoBack(),
            canGoForward = webView.canGoForward(),
            loading = loading ?: (previous?.loading == true && nextProgress < 100),
            progress = nextProgress
        )
        if (next == previous) return
        lastNavigationState = next
        onNavigationState(next)
    }

    private fun handleNavigation(url: String): Boolean {
        val decision = BrowserHandoffPolicy.classify(url, currentOpenSource)
        return when (decision) {
            BrowserHandoffDecision.StayInWebView -> false
            BrowserHandoffDecision.OpenExternalBrowser -> {
                browserAutomationController?.markNavigationBlocked(url, "external_browser")
                openExternal(url)
                true
            }
            is BrowserHandoffDecision.StartAuthHandoff,
            is BrowserHandoffDecision.StartCliCallbackHandoff -> {
                browserAutomationController?.markNavigationBlocked(url, "auth_handoff")
                launchBrowserHandoff(url, decision) || run {
                    openExternal(url)
                    true
                }
            }
            is BrowserHandoffDecision.ShowUnsupportedFallback -> {
                browserAutomationController?.markNavigationBlocked(url, decision.reason)
                openExternal(url)
                true
            }
        }
    }

    private fun handleInitialUrl(url: String): Boolean {
        val decision = BrowserHandoffPolicy.classify(url, currentOpenSource)
        if (!BrowserHandoffPolicy.isHandoff(decision)) return false
        return launchBrowserHandoff(url, decision)
    }

    private fun launchBrowserHandoff(url: String, decision: BrowserHandoffDecision): Boolean {
        val launcher = browserHandoffLauncher ?: return false
        val request = BrowserHandoffRequest(
            url = url,
            recipeId = currentRecipeId,
            recipeName = currentRecipeName,
            instanceId = currentInstanceId,
            source = currentOpenSource
        )
        return launcher.launchBrowserHandoff(request, decision).also { accepted ->
            if (accepted) {
                onStatus("Waiting for browser login: $url")
            }
        }
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

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https")) {
            openExternal(url)
            return
        }
        val downloadUri = uri ?: return
        val downloadsDir = File(ExternalExchangeManager.ensureExchangeDir(activity), DOWNLOADS_DIR_NAME).apply {
            mkdirs()
        }
        val target = uniqueDownloadFile(
            downloadsDir,
            sanitizeDownloadName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        )
        val request = DownloadManager.Request(downloadUri)
            .setTitle(target.name)
            .setDescription("${ExternalExchangeManager.CONTAINER_MOUNT_PATH}/$DOWNLOADS_DIR_NAME/${target.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(target))
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            openExternal(url)
            return
        }
        runCatching { manager.enqueue(request) }
            .onSuccess {
                onStatus("Downloading: ${ExternalExchangeManager.CONTAINER_MOUNT_PATH}/$DOWNLOADS_DIR_NAME/${target.name}")
            }
            .onFailure {
                openExternal(url)
            }
    }

    private fun sanitizeDownloadName(name: String): String {
        val cleaned = name
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]+"""), "_")
            .trim()
            .take(120)
        return cleaned.ifBlank { "download.bin" }
    }

    private fun uniqueDownloadFile(directory: File, fileName: String): File {
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        var candidate = File(directory, fileName)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(directory, "$stem-$suffix$extension")
            suffix += 1
        }
        return candidate
    }

    private fun isLocalUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme ?: return false
        val host = uri.host ?: return false
        return scheme == "http" && (host == "127.0.0.1" || host == "localhost")
    }

    private fun shouldStayInWebView(url: String): Boolean {
        return BrowserHandoffPolicy.classify(url, currentOpenSource) == BrowserHandoffDecision.StayInWebView
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

    companion object {
        private const val DOWNLOADS_DIR_NAME = "downloads"
    }
}
