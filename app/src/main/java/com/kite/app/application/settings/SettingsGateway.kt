package com.kite.app.application.settings

import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.resources.KiteResourceSourcePreferences
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeCommand
import com.kite.app.theme.ThemeSelection
import kotlinx.coroutines.flow.StateFlow

data class SettingsDropZoneSnapshot(
    val available: Boolean = false
)

data class SettingsSnapshot(
    val appLanguage: AppLanguagePreference,
    val browserRuntimeMode: BrowserRuntimeMode,
    val resourceSourcePreferences: KiteResourceSourcePreferences = KiteResourceSourcePreferences(),
    val restoreLastScreen: Boolean,
    val hideMainTaskFromRecents: Boolean,
    val notificationsEnabled: Boolean,
    val dropZone: SettingsDropZoneSnapshot,
    val revision: Long = 0L,
    val themeSelection: ThemeSelection = KiteTheme.defaultSelection,
)

sealed interface SettingsCommand {
    data class UpdateTheme(val command: ThemeCommand) : SettingsCommand
    data class SetAppLanguage(val language: AppLanguagePreference) : SettingsCommand
    data class SetBrowserRuntimeMode(val mode: BrowserRuntimeMode) : SettingsCommand
    data class SetResourceSourceOrder(val sourceIds: List<String>) : SettingsCommand
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
