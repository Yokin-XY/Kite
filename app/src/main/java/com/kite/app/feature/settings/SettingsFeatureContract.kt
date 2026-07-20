package com.kite.app.feature.settings

import com.kite.app.application.settings.SettingsSnapshot
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.KiteThemeMode
import com.kite.app.application.settings.themeConfig

internal data class SettingsUiState(
    val theme: ThemeConfig,
    val appLanguage: AppLanguagePreference,
    val browserRuntimeMode: BrowserRuntimeMode,
    val restoreLastScreen: Boolean,
    val hideMainTaskFromRecents: Boolean,
    val notificationsEnabled: Boolean,
    val dropZoneAvailable: Boolean,
    val revision: Long
)

internal sealed interface SettingsFeatureAction {
    data object Refresh : SettingsFeatureAction
    data class SelectThemeColor(val color: Int) : SettingsFeatureAction
    data class SelectBackgroundColor(val color: Int) : SettingsFeatureAction
    data class SelectThemeMode(val mode: KiteThemeMode) : SettingsFeatureAction
    data class SelectThemeStyle(val styleKey: String) : SettingsFeatureAction
    data class SelectAppLanguage(val language: AppLanguagePreference) : SettingsFeatureAction
    data class SelectBrowserMode(val mode: BrowserRuntimeMode) : SettingsFeatureAction
    data class SetRestoreLastScreen(val enabled: Boolean) : SettingsFeatureAction
    data class SetHideMainTaskFromRecents(val enabled: Boolean) : SettingsFeatureAction
    data object OpenNotificationSettings : SettingsFeatureAction
    data object OpenDropZone : SettingsFeatureAction
}

internal sealed interface SettingsFeatureEffect {
    data class ThemeChanged(val theme: ThemeConfig) : SettingsFeatureEffect
    data object RecentTaskVisibilityChanged : SettingsFeatureEffect
    data object NotificationSettingsRequested : SettingsFeatureEffect
    data class DropZoneRequested(val available: Boolean) : SettingsFeatureEffect
    data class AppLanguageChanged(val language: AppLanguagePreference) : SettingsFeatureEffect
    data class BrowserModeChanged(val mode: BrowserRuntimeMode) : SettingsFeatureEffect
}

internal object SettingsProjector {
    fun project(snapshot: SettingsSnapshot): SettingsUiState = SettingsUiState(
        theme = snapshot.themeConfig(),
        appLanguage = snapshot.appLanguage,
        browserRuntimeMode = snapshot.browserRuntimeMode,
        restoreLastScreen = snapshot.restoreLastScreen,
        hideMainTaskFromRecents = snapshot.hideMainTaskFromRecents,
        notificationsEnabled = snapshot.notificationsEnabled,
        dropZoneAvailable = snapshot.dropZone.available,
        revision = snapshot.revision
    )
}
