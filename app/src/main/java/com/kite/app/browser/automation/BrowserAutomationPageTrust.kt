package com.kite.app.browser.automation

import java.net.URI

object BrowserAutomationPageTrust {
    fun evaluateAllowed(url: String?): Boolean =
        scope(url) == SCOPE_LOCAL

    fun scope(url: String?): String {
        val uri = runCatching { URI(url.orEmpty().trim()) }.getOrNull() ?: return SCOPE_UNKNOWN
        val scheme = uri.scheme.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        return when (scheme) {
            "file" -> SCOPE_LOCAL
            "http", "https" -> if (isLocalHost(host)) SCOPE_LOCAL else SCOPE_REMOTE
            else -> SCOPE_UNKNOWN
        }
    }

    private fun isLocalHost(host: String): Boolean =
        host == "localhost" ||
            host == "::1" ||
            host == "0:0:0:0:0:0:0:1" ||
            host == "appassets.androidplatform.net" ||
            host.startsWith("127.")

    const val SCOPE_LOCAL = "local"
    const val SCOPE_REMOTE = "remote"
    const val SCOPE_UNKNOWN = "unknown"
}
