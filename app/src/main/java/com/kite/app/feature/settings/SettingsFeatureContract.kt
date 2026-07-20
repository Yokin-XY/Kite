package com.kite.app.feature.settings

import com.kite.app.application.settings.SettingsSnapshot
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.ThemeCommand
import com.kite.app.theme.ThemeSelection

internal data class SettingsUiState(
    val theme: ThemeSelection,
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
    data class UpdateTheme(val command: ThemeCommand) : SettingsFeatureAction
    data class SelectAppLanguage(val language: AppLanguagePreference) : SettingsFeatureAction
    data class SelectBrowserMode(val mode: BrowserRuntimeMode) : SettingsFeatureAction
    data class SetRestoreLastScreen(val enabled: Boolean) : SettingsFeatureAction
    data class SetHideMainTaskFromRecents(val enabled: Boolean) : SettingsFeatureAction
    data object OpenNotificationSettings : SettingsFeatureAction
    data object OpenDropZone : SettingsFeatureAction
}

internal sealed interface SettingsFeatureEffect {
    data class ThemeChanged(val theme: ThemeSelection) : SettingsFeatureEffect
    data object RecentTaskVisibilityChanged : SettingsFeatureEffect
    data object NotificationSettingsRequested : SettingsFeatureEffect
    data class DropZoneRequested(val available: Boolean) : SettingsFeatureEffect
    data class AppLanguageChanged(val language: AppLanguagePreference) : SettingsFeatureEffect
    data class BrowserModeChanged(val mode: BrowserRuntimeMode) : SettingsFeatureEffect
}

internal object SettingsProjector {
    fun project(snapshot: SettingsSnapshot): SettingsUiState = SettingsUiState(
        theme = snapshot.themeSelection,
        appLanguage = snapshot.appLanguage,
        browserRuntimeMode = snapshot.browserRuntimeMode,
        restoreLastScreen = snapshot.restoreLastScreen,
        hideMainTaskFromRecents = snapshot.hideMainTaskFromRecents,
        notificationsEnabled = snapshot.notificationsEnabled,
        dropZoneAvailable = snapshot.dropZone.available,
        revision = snapshot.revision
    )
}
