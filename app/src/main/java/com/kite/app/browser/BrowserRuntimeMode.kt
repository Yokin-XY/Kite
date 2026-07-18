package com.kite.app.browser

enum class BrowserRuntimeMode(val storageKey: String) {
    WebViewWithSystemAuth(
        storageKey = "webview_system_auth"
    ),
    AutomationBrowser(
        storageKey = "automation_browser"
    );

    companion object {
        val Default: BrowserRuntimeMode = WebViewWithSystemAuth

        fun fromStorageKey(value: String?): BrowserRuntimeMode =
            values().firstOrNull { it.storageKey == value } ?: Default
    }
}
