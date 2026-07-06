package com.kite.app.browser

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale

data class BrowserHandoffRequest(
    val url: String,
    val recipeId: String? = null,
    val recipeName: String? = null,
    val instanceId: String? = null,
    val source: String? = null
)

fun interface BrowserHandoffLauncher {
    fun launchBrowserHandoff(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision
    ): Boolean
}

sealed class BrowserHandoffDecision {
    object StayInWebView : BrowserHandoffDecision()
    object OpenExternalBrowser : BrowserHandoffDecision()
    data class StartAuthHandoff(
        val redirectUri: String?,
        val state: String?
    ) : BrowserHandoffDecision()
    data class StartCliCallbackHandoff(
        val redirectUri: String,
        val state: String?
    ) : BrowserHandoffDecision()
    data class ShowUnsupportedFallback(
        val reason: String
    ) : BrowserHandoffDecision()
}

object BrowserHandoffPolicy {
    private val webViewSources = setOf(
        "card_run_surface",
        "browser_proxy",
        "ubuntu_browser",
        "terminal_page",
        "terminal_step",
        "shell_step"
    )
    private val redactedDiagnosticParameterOrder = listOf(
        "response_type",
        "client_id",
        "redirect_uri",
        "scope",
        "state",
        "code_challenge",
        "code_challenge_method",
        "prompt",
        "login_hint",
        "audience",
        "resource"
    )

    fun classify(url: String, source: String? = null): BrowserHandoffDecision {
        val uri = parseUri(url) ?: return BrowserHandoffDecision.ShowUnsupportedFallback("invalid_url")
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return BrowserHandoffDecision.ShowUnsupportedFallback("missing_scheme")
        if (scheme !in setOf("http", "https")) return BrowserHandoffDecision.OpenExternalBrowser
        if (isLocalHttp(uri)) return BrowserHandoffDecision.StayInWebView

        val params = queryParameters(uri)
        val redirectUri = params.firstValue("redirect_uri")
        val state = params.firstValue("state")
        if (isOAuthAuthorizationRequest(uri, params)) {
            return when {
                redirectUri != null && isLoopbackRedirectUri(redirectUri) ->
                    BrowserHandoffDecision.StartCliCallbackHandoff(redirectUri, state)
                redirectUri != null && isKiteAppRedirectUri(redirectUri) ->
                    BrowserHandoffDecision.StartAuthHandoff(redirectUri, state)
                else -> BrowserHandoffDecision.OpenExternalBrowser
            }
        }

        return if (source.orEmpty() in webViewSources) {
            BrowserHandoffDecision.StayInWebView
        } else {
            BrowserHandoffDecision.OpenExternalBrowser
        }
    }

    fun isHandoff(decision: BrowserHandoffDecision): Boolean =
        decision is BrowserHandoffDecision.StartAuthHandoff ||
            decision is BrowserHandoffDecision.StartCliCallbackHandoff

    fun queryParameters(url: String): Map<String, List<String>> =
        parseUri(url)?.let(::queryParameters).orEmpty()

    fun redactedUrlForDiagnostics(url: String): String {
        val uri = parseUri(url) ?: return url.take(500)
        val params = queryParameters(uri)
        if (!isOAuthAuthorizationRequest(uri, params)) return url.take(500)
        val scheme = uri.scheme?.lowercase(Locale.US) ?: "unknown"
        val host = uri.host?.lowercase(Locale.US).orEmpty().ifBlank { "unknown-host" }
        val path = uri.rawPath.orEmpty()
        val markers = redactedDiagnosticParameterOrder.mapNotNull { key ->
            val value = params.firstValue(key) ?: return@mapNotNull null
            val marker = if (key == "redirect_uri") {
                redirectUriDiagnosticMarker(value)
            } else {
                "present"
            }
            "$key=$marker"
        }
        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(path)
            if (markers.isNotEmpty()) {
                append("?")
                append(markers.joinToString("&"))
            }
        }.take(500)
    }

    fun requestKey(url: String): String =
        fingerprint("request:${url.trim()}")

    fun stateKey(state: String): String =
        fingerprint("state:$state")

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun isLoopbackRedirectUri(value: String): Boolean {
        val uri = parseUri(value) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        return scheme == "http" && isLoopbackHost(uri.host)
    }

    fun isKiteAppRedirectUri(value: String): Boolean {
        val uri = parseUri(value) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        return scheme == BrowserAuthRedirectParser.CALLBACK_SCHEME &&
            host == BrowserAuthRedirectParser.CALLBACK_HOST
    }

    private fun isOAuthAuthorizationRequest(
        uri: URI,
        params: Map<String, List<String>>
    ): Boolean {
        val hasCoreParams = params.containsKey("client_id") &&
            params.containsKey("redirect_uri") &&
            params.containsKey("response_type")
        if (!hasCoreParams) return false
        val responseType = params.firstValue("response_type").orEmpty().lowercase(Locale.US)
        val responseTypes = responseType.split(" ", "+")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (responseTypes.none { it in setOf("code", "token", "id_token") }) return false
        val path = uri.rawPath.orEmpty().lowercase(Locale.US)
        return path.contains("oauth") ||
            path.contains("authorize") ||
            path.contains("/auth") ||
            params.containsKey("scope") ||
            params.containsKey("state")
    }

    private fun isLocalHttp(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        return scheme == "http" && isLoopbackHost(uri.host)
    }

    private fun redirectUriDiagnosticMarker(value: String): String {
        val uri = parseUri(value) ?: return "present"
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        return when {
            isLoopbackRedirectUri(value) -> "loopback"
            isKiteAppRedirectUri(value) -> "kite_app"
            scheme == "https" -> "https"
            scheme.isNotBlank() -> scheme
            else -> "present"
        }
    }

    private fun isLoopbackHost(host: String?): Boolean {
        val normalized = host
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.lowercase(Locale.US)
            ?: return false
        return normalized == "127.0.0.1" ||
            normalized == "localhost" ||
            normalized == "::1" ||
            normalized == "0:0:0:0:0:0:0:1"
    }

    private fun queryParameters(uri: URI): Map<String, List<String>> {
        val rawQuery = uri.rawQuery ?: return emptyMap()
        if (rawQuery.isBlank()) return emptyMap()
        val values = linkedMapOf<String, MutableList<String>>()
        rawQuery.split("&")
            .filter { it.isNotBlank() }
            .forEach { pair ->
                val rawKey = pair.substringBefore("=")
                val rawValue = pair.substringAfter("=", "")
                val key = decode(rawKey).takeIf { it.isNotBlank() } ?: return@forEach
                values.getOrPut(key) { mutableListOf() }.add(decode(rawValue))
            }
        return values
    }

    private fun parseUri(url: String): URI? =
        runCatching { URI(url.trim()) }.getOrNull()

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private fun Map<String, List<String>>.firstValue(key: String): String? =
        this[key]?.firstOrNull()?.takeIf { it.isNotBlank() }
}

data class BrowserAuthRedirect(
    val url: String,
    val state: String?,
    val code: String?,
    val error: String?,
    val redactedUrl: String
)

object BrowserAuthRedirectParser {
    const val CALLBACK_SCHEME = "kite-auth"
    const val CALLBACK_HOST = "callback"
    private val redactedParameterOrder = listOf(
        "error",
        "code",
        "access_token",
        "id_token",
        "refresh_token",
        "token",
        "state"
    )

    fun parse(url: String): BrowserAuthRedirect? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (scheme != CALLBACK_SCHEME || host != CALLBACK_HOST) return null
        val params = callbackParameters(uri)
        return BrowserAuthRedirect(
            url = url,
            state = params["state"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            code = params["code"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            error = params["error"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            redactedUrl = redactedCallbackUrl(params)
        )
    }

    private fun callbackParameters(uri: URI): Map<String, List<String>> {
        val values = linkedMapOf<String, MutableList<String>>()
        appendParameters(uri.rawQuery, values)
        appendParameters(uri.rawFragment, values)
        return values
    }

    private fun appendParameters(
        rawParameters: String?,
        values: MutableMap<String, MutableList<String>>
    ) {
        if (rawParameters.isNullOrBlank()) return
        rawParameters.split("&")
            .filter { it.isNotBlank() }
            .forEach { pair ->
                val rawKey = pair.substringBefore("=")
                val rawValue = pair.substringAfter("=", "")
                val key = decode(rawKey).takeIf { it.isNotBlank() } ?: return@forEach
                values.getOrPut(key) { mutableListOf() }.add(decode(rawValue))
            }
    }

    private fun redactedCallbackUrl(params: Map<String, List<String>>): String {
        val markers = redactedParameterOrder.mapNotNull { key ->
            val present = params[key]?.any { it.isNotBlank() } == true
            if (present) "$key=present" else null
        }
        return buildString {
            append(CALLBACK_SCHEME)
            append("://")
            append(CALLBACK_HOST)
            if (markers.isNotEmpty()) {
                append("?")
                append(markers.joinToString("&"))
            }
        }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
}
