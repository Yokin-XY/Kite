package com.kite.app.feature.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme

internal class SettingsScreen(
    private val context: Context,
    initialState: SettingsUiState,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    private val onSelectAppLanguage: (AppLanguagePreference) -> Unit,
    private val onSelectBrowserMode: (BrowserRuntimeMode) -> Unit,
    onRestoreLastScreen: (Boolean) -> Unit,
    onHideMainTask: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDropZone: () -> Unit
) {
    private val factory = SettingsViewFactory(context, KiteTheme.resolve(initialState.theme))
    private lateinit var languageBinding: SettingsViewFactory.NavigationBinding
    private lateinit var browserBinding: SettingsViewFactory.NavigationBinding
    private lateinit var restoreBinding: SettingsViewFactory.SwitchBinding
    private lateinit var recentsBinding: SettingsViewFactory.SwitchBinding
    private lateinit var notificationBinding: SettingsViewFactory.NavigationBinding
    private lateinit var dropZoneBinding: SettingsViewFactory.NavigationBinding
    private var latestState = initialState

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(factory.tokens.pageBackground)
        addView(factory.topBar(context.getString(R.string.settings_title), onBack))
        addView(ScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(22), factory.dp(8), factory.dp(22), factory.dp(96))
                addView(factory.navigationRow(
                    context.getString(R.string.settings_theme_title),
                    context.getString(R.string.settings_theme_summary),
                    onOpenTheme
                ).root)
                languageBinding = factory.navigationRow(
                    context.getString(R.string.settings_language_title),
                    context.appLanguageLabel(initialState.appLanguage)
                ) { factory.showLanguageDialog(latestState, onSelectAppLanguage) }
                addView(languageBinding.root.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, factory.dp(12), 0, 0) }
                })
                browserBinding = factory.navigationRow(
                    context.getString(R.string.settings_browser_mode_title),
                    context.browserModeTitle(initialState.browserRuntimeMode)
                ) { factory.showBrowserModeDialog(latestState, onSelectBrowserMode) }
                addView(browserBinding.root.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, factory.dp(12), 0, 0) }
                })
                restoreBinding = factory.switchRow(
                    context.getString(R.string.settings_restore_last_screen_title),
                    context.getString(R.string.settings_restore_last_screen_summary),
                    initialState.restoreLastScreen,
                    onRestoreLastScreen
                )
                addView(restoreBinding.root)
                recentsBinding = factory.switchRow(
                    context.getString(R.string.settings_hide_recents_title),
                    context.getString(R.string.settings_hide_recents_summary),
                    initialState.hideMainTaskFromRecents,
                    onHideMainTask
                )
                addView(recentsBinding.root)
                notificationBinding = factory.navigationRow(
                    context.getString(R.string.settings_notifications_title),
                    notificationSummary(initialState.notificationsEnabled),
                    onOpenNotificationSettings
                )
                addView(notificationBinding.root.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, factory.dp(12), 0, 0) }
                })
                dropZoneBinding = factory.navigationRow(
                    context.getString(R.string.settings_drop_zone_title),
                    dropZoneSummary(initialState.dropZoneAvailable),
                    onOpenDropZone
                )
                addView(dropZoneBinding.root.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, factory.dp(12), 0, 0) }
                })
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun render(state: SettingsUiState) {
        latestState = state
        languageBinding.subtitle.text = context.appLanguageLabel(state.appLanguage)
        browserBinding.subtitle.text = context.browserModeTitle(state.browserRuntimeMode)
        restoreBinding.bind(state.restoreLastScreen)
        recentsBinding.bind(state.hideMainTaskFromRecents)
        notificationBinding.subtitle.text = notificationSummary(state.notificationsEnabled)
        dropZoneBinding.subtitle.text = dropZoneSummary(state.dropZoneAvailable)
    }

    private fun notificationSummary(enabled: Boolean): String = context.getString(
        if (enabled) R.string.settings_notifications_enabled_summary
        else R.string.settings_notifications_disabled_summary
    )

    private fun dropZoneSummary(available: Boolean): String = context.getString(
        if (available) R.string.settings_drop_zone_available_summary
        else R.string.settings_drop_zone_unavailable_summary
    )
}
