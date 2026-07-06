package com.kite.app.browser

enum class BrowserRuntimeMode(
    val storageKey: String,
    val title: String,
    val summary: String
) {
    WebViewWithSystemAuth(
        storageKey = "webview_system_auth",
        title = "WebView + 系统浏览器登录",
        summary = "本地网页和普通页面继续用 Kite WebView，OAuth/SSO 登录交给系统浏览器。"
    ),
    AutomationBrowser(
        storageKey = "automation_browser",
        title = "自动浏览器",
        summary = "实验入口：后续用于元素化和自动控制；账号授权仍保持官方外部浏览器边界。"
    );

    companion object {
        val Default: BrowserRuntimeMode = WebViewWithSystemAuth

        fun fromStorageKey(value: String?): BrowserRuntimeMode =
            values().firstOrNull { it.storageKey == value } ?: Default
    }
}
