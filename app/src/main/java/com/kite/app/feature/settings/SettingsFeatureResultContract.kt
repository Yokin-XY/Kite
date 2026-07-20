package com.kite.app.feature.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.kite.app.theme.ThemeSelection
import com.kite.app.ui.theme.getThemeSelection
import com.kite.app.ui.theme.putThemeSelection

internal sealed interface SettingsFeatureRequest {
    data object Back : SettingsFeatureRequest
    data class OpenCategory(val destination: SettingsCategoryDestination) : SettingsFeatureRequest
    data object OpenTheme : SettingsFeatureRequest
    data class ApplyTheme(val theme: ThemeSelection) : SettingsFeatureRequest
    data object ApplyRecentTaskVisibility : SettingsFeatureRequest
    data object OpenNotificationSettings : SettingsFeatureRequest
    data object OpenAllFilesSettings : SettingsFeatureRequest
    data object OpenProcesses : SettingsFeatureRequest
    data object OpenLogs : SettingsFeatureRequest
    data class OpenDropZone(val available: Boolean) : SettingsFeatureRequest
}

internal object SettingsFeatureResultContract {
    const val REQUEST_KEY = "kite.settings.feature.request"

    fun send(fragment: Fragment, request: SettingsFeatureRequest) {
        fragment.parentFragmentManager.setFragmentResult(REQUEST_KEY, encode(request))
    }

    fun parse(bundle: Bundle): SettingsFeatureRequest? = when (bundle.getString(KEY_KIND)) {
        KIND_BACK -> SettingsFeatureRequest.Back
        KIND_OPEN_CATEGORY -> bundle.getString(KEY_CATEGORY)
            ?.let { value -> runCatching { SettingsCategoryDestination.valueOf(value) }.getOrNull() }
            ?.let(SettingsFeatureRequest::OpenCategory)
        KIND_OPEN_THEME -> SettingsFeatureRequest.OpenTheme
        KIND_APPLY_THEME -> SettingsFeatureRequest.ApplyTheme(
            bundle.getThemeSelection(THEME_PREFIX)
        )
        KIND_APPLY_RECENTS -> SettingsFeatureRequest.ApplyRecentTaskVisibility
        KIND_NOTIFICATION -> SettingsFeatureRequest.OpenNotificationSettings
        KIND_ALL_FILES -> SettingsFeatureRequest.OpenAllFilesSettings
        KIND_PROCESSES -> SettingsFeatureRequest.OpenProcesses
        KIND_LOGS -> SettingsFeatureRequest.OpenLogs
        KIND_DROP_ZONE -> SettingsFeatureRequest.OpenDropZone(bundle.getBoolean(KEY_AVAILABLE))
        else -> null
    }

    private fun encode(request: SettingsFeatureRequest): Bundle = Bundle().apply {
        when (request) {
            SettingsFeatureRequest.Back -> putString(KEY_KIND, KIND_BACK)
            is SettingsFeatureRequest.OpenCategory -> {
                putString(KEY_KIND, KIND_OPEN_CATEGORY)
                putString(KEY_CATEGORY, request.destination.name)
            }
            SettingsFeatureRequest.OpenTheme -> putString(KEY_KIND, KIND_OPEN_THEME)
            is SettingsFeatureRequest.ApplyTheme -> {
                putString(KEY_KIND, KIND_APPLY_THEME)
                putThemeSelection(THEME_PREFIX, request.theme)
            }
            SettingsFeatureRequest.ApplyRecentTaskVisibility -> putString(KEY_KIND, KIND_APPLY_RECENTS)
            SettingsFeatureRequest.OpenNotificationSettings -> putString(KEY_KIND, KIND_NOTIFICATION)
            SettingsFeatureRequest.OpenAllFilesSettings -> putString(KEY_KIND, KIND_ALL_FILES)
            SettingsFeatureRequest.OpenProcesses -> putString(KEY_KIND, KIND_PROCESSES)
            SettingsFeatureRequest.OpenLogs -> putString(KEY_KIND, KIND_LOGS)
            is SettingsFeatureRequest.OpenDropZone -> {
                putString(KEY_KIND, KIND_DROP_ZONE)
                putBoolean(KEY_AVAILABLE, request.available)
            }
        }
    }

    private const val KEY_KIND = "kind"
    private const val THEME_PREFIX = "theme"
    private const val KEY_AVAILABLE = "available"
    private const val KEY_CATEGORY = "category"
    private const val KIND_BACK = "back"
    private const val KIND_OPEN_CATEGORY = "open_category"
    private const val KIND_OPEN_THEME = "open_theme"
    private const val KIND_APPLY_THEME = "apply_theme"
    private const val KIND_APPLY_RECENTS = "apply_recents"
    private const val KIND_NOTIFICATION = "notification"
    private const val KIND_ALL_FILES = "all_files"
    private const val KIND_PROCESSES = "processes"
    private const val KIND_LOGS = "logs"
    private const val KIND_DROP_ZONE = "drop_zone"
}
