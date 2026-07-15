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
    data object OpenNotificationSettings : SettingsFeatureAction
    data object OpenDropZone : SettingsFeatureAction
}

internal sealed interface SettingsFeatureEffect {
    data class ThemeChanged(val theme: ThemeConfig) : SettingsFeatureEffect
    data object RecentTaskVisibilityChanged : SettingsFeatureEffect
    data object NotificationSettingsRequested : SettingsFeatureEffect
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
            "已允许通知；点击设置首页卡片进度的横幅、锁屏和提醒方式。"
        } else {
            "未允许通知；点击完成系统授权并设置首页卡片进度。"
        },
        dropZoneAvailable = snapshot.dropZone.available,
        dropZoneMessage = snapshot.dropZone.message,
        revision = snapshot.revision
    )
}
