package com.kite.app.application.settings

import com.kite.app.browser.BrowserRuntimeMode
import kotlinx.coroutines.flow.StateFlow

data class SettingsDropZoneSnapshot(
    val available: Boolean = false,
    val message: String = "投放区尚未检查"
)

data class SettingsSnapshot(
    val themeColor: Int,
    val backgroundColor: Int,
    val browserRuntimeMode: BrowserRuntimeMode,
    val restoreLastScreen: Boolean,
    val hideMainTaskFromRecents: Boolean,
    val notificationsEnabled: Boolean,
    val dropZone: SettingsDropZoneSnapshot,
    val revision: Long = 0L
)

sealed interface SettingsCommand {
    data class SetThemeColor(val color: Int) : SettingsCommand
    data class SetBackgroundColor(val color: Int) : SettingsCommand
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
