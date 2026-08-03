package com.kite.app.feature.settings

import android.content.Context
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.ThemeColorSchemeDefinition
import com.kite.app.theme.KiteThemeMode
import com.kite.app.theme.ThemeStylePackDefinition

internal fun Context.appLanguageLabel(language: AppLanguagePreference): String = getString(
    when (language) {
        AppLanguagePreference.System -> R.string.settings_language_system
        AppLanguagePreference.SimplifiedChinese -> R.string.settings_language_simplified_chinese
        AppLanguagePreference.English -> R.string.settings_language_english
    }
)

internal fun Context.browserModeTitle(mode: BrowserRuntimeMode): String = getString(
    when (mode) {
        BrowserRuntimeMode.WebViewWithSystemAuth -> R.string.browser_mode_webview_system_auth_title
        BrowserRuntimeMode.AutomationBrowser -> R.string.browser_mode_automation_title
    }
)

internal fun Context.themeModeLabel(mode: KiteThemeMode): String = getString(
    when (mode) {
        KiteThemeMode.SYSTEM -> R.string.settings_theme_mode_system
        KiteThemeMode.LIGHT -> R.string.settings_theme_mode_light
        KiteThemeMode.DARK -> R.string.settings_theme_mode_dark
    },
)

internal fun Context.themeColorSchemeLabel(scheme: ThemeColorSchemeDefinition): String =
    when (scheme.key.value) {
        "chatgpt" -> getString(R.string.settings_theme_color_chatgpt)
        "standard" -> getString(R.string.settings_theme_color_standard)
        else -> scheme.key.value
    }

internal fun Context.themeStyleLabel(style: ThemeStylePackDefinition): String = when (style.key.value) {
    "standard" -> getString(R.string.settings_theme_style_standard)
    else -> style.key.value
}
