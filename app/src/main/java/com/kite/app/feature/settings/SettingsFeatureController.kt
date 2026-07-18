package com.kite.app.feature.settings

import com.kite.app.application.settings.SettingsCommand
import com.kite.app.application.settings.SettingsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class SettingsFeatureController(
    private val gateway: SettingsGateway,
    scope: CoroutineScope
) {
    val state: StateFlow<SettingsUiState> = gateway.snapshots
        .map(SettingsProjector::project)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsProjector.project(gateway.currentSnapshot())
        )

    suspend fun dispatch(action: SettingsFeatureAction): SettingsFeatureEffect? = when (action) {
        SettingsFeatureAction.Refresh -> {
            gateway.refresh()
            null
        }
        is SettingsFeatureAction.SelectThemeColor -> {
            val snapshot = gateway.update(SettingsCommand.SetThemeColor(action.color))
            SettingsFeatureEffect.ThemeChanged(
                com.kite.app.theme.ThemeConfig(snapshot.themeColor, snapshot.backgroundColor)
            )
        }
        is SettingsFeatureAction.SelectBackgroundColor -> {
            val snapshot = gateway.update(SettingsCommand.SetBackgroundColor(action.color))
            SettingsFeatureEffect.ThemeChanged(
                com.kite.app.theme.ThemeConfig(snapshot.themeColor, snapshot.backgroundColor)
            )
        }
        is SettingsFeatureAction.SelectAppLanguage -> {
            gateway.update(SettingsCommand.SetAppLanguage(action.language))
            SettingsFeatureEffect.AppLanguageChanged(action.language)
        }
        is SettingsFeatureAction.SelectBrowserMode -> {
            gateway.update(SettingsCommand.SetBrowserRuntimeMode(action.mode))
            SettingsFeatureEffect.BrowserModeChanged(action.mode)
        }
        is SettingsFeatureAction.SetRestoreLastScreen -> {
            gateway.update(SettingsCommand.SetRestoreLastScreen(action.enabled))
            null
        }
        is SettingsFeatureAction.SetHideMainTaskFromRecents -> {
            gateway.update(SettingsCommand.SetHideMainTaskFromRecents(action.enabled))
            SettingsFeatureEffect.RecentTaskVisibilityChanged
        }
        SettingsFeatureAction.OpenNotificationSettings ->
            SettingsFeatureEffect.NotificationSettingsRequested
        SettingsFeatureAction.OpenDropZone -> SettingsFeatureEffect.DropZoneRequested(
            state.value.dropZoneAvailable
        )
    }
}
