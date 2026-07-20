package com.kite.app.feature.settings

import android.content.Context
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.ThemeChoice

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

internal fun Context.themeChoiceLabel(choice: ThemeChoice): String {
    val resourceId = when (choice.key) {
        "cool_cyan" -> R.string.theme_choice_cool_cyan
        "purple" -> R.string.theme_choice_purple
        "green" -> R.string.theme_choice_green
        "blue" -> R.string.theme_choice_blue
        "orange" -> R.string.theme_choice_orange
        "cool_gray" -> R.string.theme_choice_cool_gray
        "white" -> R.string.theme_choice_white
        "ivory" -> R.string.theme_choice_ivory
        "mist_blue" -> R.string.theme_choice_mist_blue
        "light_cyan" -> R.string.theme_choice_light_cyan
        else -> null
    }
    return resourceId?.let(::getString) ?: choice.key
}
