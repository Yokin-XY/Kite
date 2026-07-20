package com.kite.app.shell

import com.kite.app.feature.settings.SettingsCategoryDestination

internal fun SettingsCategoryDestination.toAppDestination(): AppDestination =
    categoryToDestination.getValue(this)

internal fun AppDestination.toSettingsCategoryOrNull(): SettingsCategoryDestination? =
    destinationToCategory[this]

private val categoryToDestination = mapOf(
    SettingsCategoryDestination.AppearanceAndLanguage to AppDestination.SettingsAppearanceLanguage,
    SettingsCategoryDestination.AppBehavior to AppDestination.SettingsAppBehavior,
    SettingsCategoryDestination.TerminalAndWorkbench to AppDestination.SettingsTerminalWorkbench,
    SettingsCategoryDestination.BrowserAndLogin to AppDestination.SettingsBrowserLogin,
    SettingsCategoryDestination.PermissionsAndFiles to AppDestination.SettingsPermissionsFiles,
    SettingsCategoryDestination.RuntimeEnvironment to AppDestination.SettingsRuntimeEnvironment,
    SettingsCategoryDestination.ExperimentalFeatures to AppDestination.SettingsExperimentalFeatures,
    SettingsCategoryDestination.HelpAndAbout to AppDestination.SettingsHelpAbout,
)

private val destinationToCategory = categoryToDestination.entries.associate { (category, destination) ->
    destination to category
}
