package com.kite.app.application.settings

import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.KiteThemeMode
import com.kite.app.theme.ThemeConfig
import kotlinx.coroutines.flow.StateFlow

data class SettingsDropZoneSnapshot(
    val available: Boolean = false
)

data class SettingsSnapshot(
    val themeColor: Int,
    val backgroundColor: Int,
    val appLanguage: AppLanguagePreference,
    val browserRuntimeMode: BrowserRuntimeMode,
    val restoreLastScreen: Boolean,
    val hideMainTaskFromRecents: Boolean,
    val notificationsEnabled: Boolean,
    val dropZone: SettingsDropZoneSnapshot,
    val revision: Long = 0L,
    val themeMode: KiteThemeMode = KiteThemeMode.SYSTEM,
    val themeStyleKey: String = KiteTheme.defaultStyleKey,
)

fun SettingsSnapshot.themeConfig(): ThemeConfig = ThemeConfig(
    themeColor = themeColor,
    backgroundColor = backgroundColor,
    mode = themeMode,
    styleKey = themeStyleKey,
)

sealed interface SettingsCommand {
    data class SetThemeColor(val color: Int) : SettingsCommand
    data class SetBackgroundColor(val color: Int) : SettingsCommand
    data class SetThemeMode(val mode: KiteThemeMode) : SettingsCommand
    data class SetThemeStyle(val styleKey: String) : SettingsCommand
    data class SetAppLanguage(val language: AppLanguagePreference) : SettingsCommand
    data class SetBrowserRuntimeMode(val mode: BrowserRuntimeMode) : SettingsCommand
    data class SetRestoreLastScreen(val enabled: Boolean) : SettingsCommand
    data class SetHideMainTaskFromRecents(val enabled: Boolean) : SettingsCommand
}

interface SettingsGateway {
    val snapshots: StateFlow<SettingsSnapshot>
    fun currentSnapshot(): SettingsSnapshot
    suspend fun refresh(): SettingsSnapshot
    fun update(command: SettingsCommand): SettingsSnapshot
}

interface SettingsFeatureDependenciesOwner {
    val settingsFeatureGateway: SettingsGateway
}
