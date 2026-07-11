package com.kite.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHandoffPolicyTest {
    @Test
    fun localHttpUrlStaysInWebView() {
        val decision = BrowserHandoffPolicy.classify(
            url = "http://127.0.0.1:5173/",
            source = "browser_proxy"
        )

        assertEquals(BrowserHandoffDecision.StayInWebView, decision)
    }

    @Test
    fun localIpv6HttpUrlStaysInWebView() {
        val decision = BrowserHandoffPolicy.classify(
            url = "http://[::1]:5173/",
            source = "browser_proxy"
        )

        assertEquals(BrowserHandoffDecision.StayInWebView, decision)
    }

    @Test
    fun oauthAuthorizationUrlStartsExternalAuthHandoff() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://accounts.example.test/o/oauth2/v2/auth" +
                "?response_type=code" +
                "&client_id=client" +
                "&redirect_uri=kite-auth%3A%2F%2Fcallback" +
                "&scope=openid%20email" +
                "&state=abc",
            source = "browser_proxy"
        )

        assertTrue(decision is BrowserHandoffDecision.StartAuthHandoff)
        val handoff = decision as BrowserHandoffDecision.StartAuthHandoff
        assertEquals("kite-auth://callback", handoff.redirectUri)
        assertEquals("abc", handoff.state)
    }

    @Test
    fun oauthAuthorizationWithExternalHttpsRedirectOpensExternalBrowser() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://accounts.example.test/o/oauth2/v2/auth" +
                "?response_type=code" +
                "&client_id=client" +
                "&redirect_uri=https%3A%2F%2Fdevelopers.example.test%2Foauthplayground" +
                "&scope=openid%20email" +
                "&state=abc",
            source = "browser_proxy"
        )

        assertEquals(BrowserHandoffDecision.OpenExternalBrowser, decision)
    }

    @Test
    fun loopbackRedirectStartsCliCallbackHandoff() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://login.example.test/authorize" +
                "?response_type=code" +
                "&client_id=cli" +
                "&redirect_uri=http%3A%2F%2F127.0.0.1%3A1455%2Fcallback" +
                "&state=cli-state",
            source = "terminal_step"
        )

        assertTrue(decision is BrowserHandoffDecision.StartCliCallbackHandoff)
        val handoff = decision as BrowserHandoffDecision.StartCliCallbackHandoff
        assertEquals("http://127.0.0.1:1455/callback", handoff.redirectUri)
        assertEquals("cli-state", handoff.state)
    }

    @Test
    fun openAiCodexLoopbackRedirectStartsCliCallbackHandoff() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://auth.openai.com/oauth/authorize" +
                "?response_type=code" +
                "&client_id=codex-cli" +
                "&redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback" +
                "&scope=openid%20email" +
                "&state=openai-state" +
                "&originator=codex-tui",
            source = "terminal_step"
        )

        assertTrue(decision is BrowserHandoffDecision.StartCliCallbackHandoff)
        val handoff = decision as BrowserHandoffDecision.StartCliCallbackHandoff
        assertEquals("http://localhost:1455/auth/callback", handoff.redirectUri)
        assertEquals("openai-state", handoff.state)
    }

    @Test
    fun claudeLoopbackRedirectStartsCliCallbackHandoff() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://claude.ai/login" +
                "?response_type=code" +
                "&client_id=claude-code" +
                "&redirect_uri=http%3A%2F%2Flocalhost%3A43299%2Fcallback" +
                "&scope=openid" +
                "&state=claude-state",
            source = "terminal_step"
        )

        assertTrue(decision is BrowserHandoffDecision.StartCliCallbackHandoff)
        val handoff = decision as BrowserHandoffDecision.StartCliCallbackHandoff
        assertEquals("http://localhost:43299/callback", handoff.redirectUri)
        assertEquals("claude-state", handoff.state)
    }

    @Test
    fun openAiExternalHttpsRedirectOpensExternalBrowser() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://auth.openai.com/oauth/authorize" +
                "?response_type=code" +
                "&client_id=codex-cli" +
                "&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback" +
                "&scope=openid%20email" +
                "&state=openai-state",
            source = "browser_proxy"
        )

        assertEquals(BrowserHandoffDecision.OpenExternalBrowser, decision)
    }

    @Test
    fun claudeExternalHttpsRedirectOpensExternalBrowser() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://claude.ai/login" +
                "?response_type=code" +
                "&client_id=claude-code" +
                "&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback" +
                "&scope=openid" +
                "&state=claude-state",
            source = "browser_proxy"
        )

        assertEquals(BrowserHandoffDecision.OpenExternalBrowser, decision)
    }

    @Test
    fun ipv6LoopbackRedirectStartsCliCallbackHandoff() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://login.example.test/authorize" +
                "?response_type=code" +
                "&client_id=cli" +
                "&redirect_uri=http%3A%2F%2F%5B%3A%3A1%5D%3A1455%2Fcallback" +
                "&state=cli-state",
            source = "terminal_step"
        )

        assertTrue(decision is BrowserHandoffDecision.StartCliCallbackHandoff)
        val handoff = decision as BrowserHandoffDecision.StartCliCallbackHandoff
        assertEquals("http://[::1]:1455/callback", handoff.redirectUri)
        assertEquals("cli-state", handoff.state)
    }

    @Test
    fun loopbackEndpointExposesOnlyTransportCoordinates() {
        val endpoint = BrowserHandoffPolicy.loopbackCallbackEndpoint(
            "http://localhost:43299/oauth/callback"
        )

        checkNotNull(endpoint)
        assertEquals("localhost", endpoint.host)
        assertEquals(43299, endpoint.port)
        assertEquals("/oauth/callback", endpoint.path)
        assertEquals(BrowserLoopbackHostKind.Localhost, endpoint.hostKind)
    }

    @Test
    fun ipv4LoopbackRangeIsAcceptedWithoutProviderRules() {
        val endpoint = BrowserHandoffPolicy.loopbackCallbackEndpoint(
            "http://127.23.45.67:9876/callback"
        )

        checkNotNull(endpoint)
        assertEquals(BrowserLoopbackHostKind.Ipv4, endpoint.hostKind)
        assertEquals(9876, endpoint.port)
    }

    @Test
    fun loopbackEndpointRejectsCredentialsAndFragments() {
        assertEquals(
            null,
            BrowserHandoffPolicy.loopbackCallbackEndpoint("http://user@localhost:1455/callback")
        )
        assertEquals(
            null,
            BrowserHandoffPolicy.loopbackCallbackEndpoint("http://localhost:1455/callback#token")
        )
    }

    @Test
    fun oidcHybridResponseTypeStartsExternalAuthHandoff() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://login.example.test/oauth2/authorize" +
                "?response_type=code%20id_token" +
                "&client_id=app" +
                "&redirect_uri=kite-auth%3A%2F%2Fcallback" +
                "&scope=openid" +
                "&state=hybrid",
            source = "browser_proxy"
        )

        assertTrue(decision is BrowserHandoffDecision.StartAuthHandoff)
        val handoff = decision as BrowserHandoffDecision.StartAuthHandoff
        assertEquals("kite-auth://callback", handoff.redirectUri)
        assertEquals("hybrid", handoff.state)
    }

    @Test
    fun ordinaryBrowserProxyUrlStillUsesWebView() {
        val decision = BrowserHandoffPolicy.classify(
            url = "https://example.test/docs",
            source = "browser_proxy"
        )

        assertEquals(BrowserHandoffDecision.StayInWebView, decision)
    }

    @Test
    fun oauthAuthorizationDiagnosticUrlIsRedacted() {
        val redacted = BrowserHandoffPolicy.redactedUrlForDiagnostics(
            "https://login.example.test/oauth2/authorize" +
                "?response_type=code" +
                "&client_id=client-secret" +
                "&redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fcallback" +
                "&scope=openid%20email" +
                "&state=secret-state" +
                "&code_challenge=secret-challenge" +
                "&code_challenge_method=S256"
        )

        assertEquals(
            "https://login.example.test/oauth2/authorize" +
                "?response_type=present" +
                "&client_id=present" +
                "&redirect_uri=loopback" +
                "&scope=present" +
                "&state=present" +
                "&code_challenge=present" +
                "&code_challenge_method=present",
            redacted
        )
    }

    @Test
    fun ordinaryDiagnosticUrlIsKept() {
        val url = "https://example.test/docs?section=install"

        assertEquals(url, BrowserHandoffPolicy.redactedUrlForDiagnostics(url))
    }

    @Test
    fun customSchemeOpensExternalBrowserPath() {
        val decision = BrowserHandoffPolicy.classify(
            url = "mailto:hello@example.test",
            source = "browser_proxy"
        )

        assertEquals(BrowserHandoffDecision.OpenExternalBrowser, decision)
    }

    @Test
    fun parsesKiteAuthRedirect() {
        val redirect = BrowserAuthRedirectParser.parse("kite-auth://callback?code=ok&state=abc")

        checkNotNull(redirect)
        assertEquals("ok", redirect.code)
        assertEquals("abc", redirect.state)
        assertEquals(null, redirect.error)
        assertEquals("kite-auth://callback?code=present&state=present", redirect.redactedUrl)
    }

    @Test
    fun parsesKiteAuthRedirectFragmentWithoutExposingTokens() {
        val redirect = BrowserAuthRedirectParser.parse(
            "kite-auth://callback#access_token=secret-token&id_token=secret-jwt&state=abc"
        )

        checkNotNull(redirect)
        assertEquals(null, redirect.code)
        assertEquals("abc", redirect.state)
        assertEquals(null, redirect.error)
        assertEquals(
            "kite-auth://callback?access_token=present&id_token=present&state=present",
            redirect.redactedUrl
        )
    }
}
