package com.kite.app.feature.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.kite.app.theme.ThemeConfig

internal sealed interface SettingsFeatureRequest {
    data object Back : SettingsFeatureRequest
    data object OpenTheme : SettingsFeatureRequest
    data class ApplyTheme(val theme: ThemeConfig) : SettingsFeatureRequest
    data object ApplyRecentTaskVisibility : SettingsFeatureRequest
    data object OpenNotificationSettings : SettingsFeatureRequest
    data class OpenDropZone(val available: Boolean) : SettingsFeatureRequest
}

internal object SettingsFeatureResultContract {
    const val REQUEST_KEY = "kite.settings.feature.request"

    fun send(fragment: Fragment, request: SettingsFeatureRequest) {
        fragment.parentFragmentManager.setFragmentResult(REQUEST_KEY, encode(request))
    }

    fun parse(bundle: Bundle): SettingsFeatureRequest? = when (bundle.getString(KEY_KIND)) {
        KIND_BACK -> SettingsFeatureRequest.Back
        KIND_OPEN_THEME -> SettingsFeatureRequest.OpenTheme
        KIND_APPLY_THEME -> SettingsFeatureRequest.ApplyTheme(
            ThemeConfig(
                themeColor = bundle.getInt(KEY_THEME_COLOR),
                backgroundColor = bundle.getInt(KEY_BACKGROUND_COLOR)
            )
        )
        KIND_APPLY_RECENTS -> SettingsFeatureRequest.ApplyRecentTaskVisibility
        KIND_NOTIFICATION -> SettingsFeatureRequest.OpenNotificationSettings
        KIND_DROP_ZONE -> SettingsFeatureRequest.OpenDropZone(bundle.getBoolean(KEY_AVAILABLE))
        else -> null
    }

    private fun encode(request: SettingsFeatureRequest): Bundle = Bundle().apply {
        when (request) {
            SettingsFeatureRequest.Back -> putString(KEY_KIND, KIND_BACK)
            SettingsFeatureRequest.OpenTheme -> putString(KEY_KIND, KIND_OPEN_THEME)
            is SettingsFeatureRequest.ApplyTheme -> {
                putString(KEY_KIND, KIND_APPLY_THEME)
                putInt(KEY_THEME_COLOR, request.theme.themeColor)
                putInt(KEY_BACKGROUND_COLOR, request.theme.backgroundColor)
            }
            SettingsFeatureRequest.ApplyRecentTaskVisibility -> putString(KEY_KIND, KIND_APPLY_RECENTS)
            SettingsFeatureRequest.OpenNotificationSettings -> putString(KEY_KIND, KIND_NOTIFICATION)
            is SettingsFeatureRequest.OpenDropZone -> {
                putString(KEY_KIND, KIND_DROP_ZONE)
                putBoolean(KEY_AVAILABLE, request.available)
            }
        }
    }

    private const val KEY_KIND = "kind"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_BACKGROUND_COLOR = "background_color"
    private const val KEY_AVAILABLE = "available"
    private const val KIND_BACK = "back"
    private const val KIND_OPEN_THEME = "open_theme"
    private const val KIND_APPLY_THEME = "apply_theme"
    private const val KIND_APPLY_RECENTS = "apply_recents"
    private const val KIND_NOTIFICATION = "notification"
    private const val KIND_DROP_ZONE = "drop_zone"
}
