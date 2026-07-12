package com.kite.app.feature.settings

import com.kite.app.application.settings.SettingsSnapshot
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.ThemeConfig

internal data class SettingsUiState(
    val theme: ThemeConfig,
    val browserRuntimeMode: BrowserRuntimeMode,
    val restoreLastScreen: Boolean,
    val hideMainTaskFromRecents: Boolean,
    val notificationsEnabled: Boolean,
    val notificationSubtitle: String,
    val dropZoneAvailable: Boolean,
    val dropZoneMessage: String,
    val revision: Long
)

internal sealed interface SettingsFeatureAction {
    data object Refresh : SettingsFeatureAction
    data class SelectThemeColor(val color: Int) : SettingsFeatureAction
    data class SelectBackgroundColor(val color: Int) : SettingsFeatureAction
    data class SelectBrowserMode(val mode: BrowserRuntimeMode) : SettingsFeatureAction
    data class SetRestoreLastScreen(val enabled: Boolean) : SettingsFeatureAction
    data class SetHideMainTaskFromRecents(val enabled: Boolean) : SettingsFeatureAction
    data class RequestNotificationState(val enabled: Boolean) : SettingsFeatureAction
    data object OpenDropZone : SettingsFeatureAction
}

internal sealed interface SettingsFeatureEffect {
    data class ThemeChanged(val theme: ThemeConfig) : SettingsFeatureEffect
    data object RecentTaskVisibilityChanged : SettingsFeatureEffect
    data class NotificationStateRequested(val enabled: Boolean) : SettingsFeatureEffect
    data class DropZoneRequested(val available: Boolean) : SettingsFeatureEffect
    data class BrowserModeChanged(val mode: BrowserRuntimeMode) : SettingsFeatureEffect
}

internal object SettingsProjector {
    fun project(snapshot: SettingsSnapshot): SettingsUiState = SettingsUiState(
        theme = ThemeConfig(snapshot.themeColor, snapshot.backgroundColor),
        browserRuntimeMode = snapshot.browserRuntimeMode,
        restoreLastScreen = snapshot.restoreLastScreen,
        hideMainTaskFromRecents = snapshot.hideMainTaskFromRecents,
        notificationsEnabled = snapshot.notificationsEnabled,
        notificationSubtitle = if (snapshot.notificationsEnabled) {
            "已开启，后台运行和容器服务会显示系统通知。"
        } else {
            "未开启，点击后进入系统通知授权。"
        },
        dropZoneAvailable = snapshot.dropZone.available,
        dropZoneMessage = snapshot.dropZone.message,
        revision = snapshot.revision
    )
}
