package com.kite.app.platform.settings

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.application.settings.SettingsCommand
import com.kite.app.application.settings.SettingsDropZoneSnapshot
import com.kite.app.application.settings.SettingsGateway
import com.kite.app.application.settings.SettingsSnapshot
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.KiteThemeMode
import com.kite.app.theme.ThemeColorSchemeKey
import com.kite.app.theme.ThemeColorSeed
import com.kite.app.theme.ThemeColorSelection
import com.kite.app.theme.ThemeSelection
import com.kite.app.theme.ThemeStylePackKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** 设置持久化和系统状态读取适配器。文件探测只在显式 refresh 的 IO 段执行。 */
internal class AndroidSettingsGateway(
    context: Context,
    private val readNotificationsEnabled: () -> Boolean = {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager?.areNotificationsEnabled() ?: true
        } else {
            true
        }
    },
    private val readAppLanguage: () -> AppLanguagePreference = { AppLanguagePreference.System },
    private val applyAppLanguage: (AppLanguagePreference) -> Unit = {},
    private val readDropZone: () -> SettingsDropZoneSnapshot
) : SettingsGateway {
    private val appContext = context.applicationContext
    private val themePreferences = appContext.getSharedPreferences(THEME_PREFERENCES, Context.MODE_PRIVATE)
    private val appPreferences = appContext.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
    private var revision = 0L
    private val mutableSnapshots = MutableStateFlow(readSnapshot())

    override val snapshots: StateFlow<SettingsSnapshot> = mutableSnapshots

    override fun currentSnapshot(): SettingsSnapshot = mutableSnapshots.value

    override suspend fun refresh(): SettingsSnapshot = withContext(Dispatchers.IO) {
        publish(readSnapshot(dropZone = readDropZone()))
    }

    override fun update(command: SettingsCommand): SettingsSnapshot {
        when (command) {
            is SettingsCommand.UpdateTheme -> writeThemeSelection(
                KiteTheme.apply(mutableSnapshots.value.themeSelection, command.command)
            )
            is SettingsCommand.SetAppLanguage -> applyAppLanguage(command.language)
            is SettingsCommand.SetBrowserRuntimeMode -> appPreferences.edit()
                .putString(KEY_BROWSER_RUNTIME_MODE, command.mode.storageKey)
                .commit()
            is SettingsCommand.SetRestoreLastScreen -> appPreferences.edit()
                .putBoolean(KEY_RESTORE_LAST_SCREEN, command.enabled)
                .commit()
            is SettingsCommand.SetHideMainTaskFromRecents -> appPreferences.edit()
                .putBoolean(KEY_HIDE_MAIN_TASK_FROM_RECENTS, command.enabled)
                .commit()
        }
        return publish(readSnapshot(dropZone = mutableSnapshots.value.dropZone))
    }

    private fun readSnapshot(
        dropZone: SettingsDropZoneSnapshot = SettingsDropZoneSnapshot()
    ): SettingsSnapshot = SettingsSnapshot(
        appLanguage = readAppLanguage(),
        browserRuntimeMode = BrowserRuntimeMode.fromStorageKey(
            appPreferences.getString(KEY_BROWSER_RUNTIME_MODE, null)
        ),
        restoreLastScreen = appPreferences.getBoolean(KEY_RESTORE_LAST_SCREEN, true),
        hideMainTaskFromRecents = appPreferences.getBoolean(KEY_HIDE_MAIN_TASK_FROM_RECENTS, false),
        notificationsEnabled = readNotificationsEnabled(),
        dropZone = dropZone,
        revision = nextRevision(),
        themeSelection = readThemeSelection(),
    )

    private fun readThemeSelection(): ThemeSelection {
        val colors = when (themePreferences.getString(KEY_COLOR_SELECTION_KIND, null)) {
            COLOR_SELECTION_REGISTERED -> ThemeColorSelection.Registered(
                ThemeColorSchemeKey(
                    themePreferences.getString(KEY_COLOR_SCHEME, null)
                        ?: KiteTheme.defaultColorSchemeKey
                )
            )
            COLOR_SELECTION_CUSTOM -> ThemeColorSelection.Custom(
                ThemeColorSeed(
                    accent = themePreferences.getInt(KEY_CUSTOM_ACCENT, KiteTheme.defaultThemeColor),
                    background = themePreferences.getInt(KEY_CUSTOM_BACKGROUND, KiteTheme.defaultBackgroundColor),
                )
            )
            else -> if (
                themePreferences.contains(KEY_LEGACY_THEME_COLOR) ||
                themePreferences.contains(KEY_LEGACY_BACKGROUND_COLOR)
            ) {
                ThemeColorSelection.Custom(
                    ThemeColorSeed(
                        accent = themePreferences.getInt(KEY_LEGACY_THEME_COLOR, KiteTheme.defaultThemeColor),
                        background = themePreferences.getInt(
                            KEY_LEGACY_BACKGROUND_COLOR,
                            KiteTheme.defaultBackgroundColor,
                        ),
                    )
                )
            } else {
                KiteTheme.defaultSelection.colors
            }
        }
        return KiteTheme.normalize(
            ThemeSelection(
                mode = KiteThemeMode.fromStorageKey(themePreferences.getString(KEY_THEME_MODE, null)),
                colors = colors,
                stylePack = ThemeStylePackKey(
                    themePreferences.getString(KEY_THEME_STYLE_PACK, null)
                        ?: themePreferences.getString(KEY_LEGACY_THEME_STYLE, null)
                        ?: KiteTheme.defaultStyleKey
                ),
            )
        )
    }

    private fun writeThemeSelection(selection: ThemeSelection) {
        val normalized = KiteTheme.normalize(selection)
        val editor = themePreferences.edit()
            .putString(KEY_THEME_MODE, normalized.mode.storageKey)
            .putString(KEY_THEME_STYLE_PACK, normalized.stylePack.value)
        when (val colors = normalized.colors) {
            is ThemeColorSelection.Registered -> editor
                .putString(KEY_COLOR_SELECTION_KIND, COLOR_SELECTION_REGISTERED)
                .putString(KEY_COLOR_SCHEME, colors.key.value)
                .remove(KEY_CUSTOM_ACCENT)
                .remove(KEY_CUSTOM_BACKGROUND)
            is ThemeColorSelection.Custom -> editor
                .putString(KEY_COLOR_SELECTION_KIND, COLOR_SELECTION_CUSTOM)
                .putInt(KEY_CUSTOM_ACCENT, colors.seed.accent)
                .putInt(KEY_CUSTOM_BACKGROUND, colors.seed.background)
                .remove(KEY_COLOR_SCHEME)
        }
        editor.commit()
    }

    @Synchronized
    private fun nextRevision(): Long = ++revision

    private fun publish(snapshot: SettingsSnapshot): SettingsSnapshot = snapshot.also {
        mutableSnapshots.value = it
    }

    private companion object {
        const val THEME_PREFERENCES = "kite_theme"
        const val APP_PREFERENCES = "kite_app_settings"
        const val KEY_COLOR_SELECTION_KIND = "color_selection_kind"
        const val KEY_COLOR_SCHEME = "color_scheme"
        const val KEY_CUSTOM_ACCENT = "custom_accent"
        const val KEY_CUSTOM_BACKGROUND = "custom_background"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_STYLE_PACK = "theme_style_pack"
        const val KEY_LEGACY_THEME_COLOR = "theme_color"
        const val KEY_LEGACY_BACKGROUND_COLOR = "background_color"
        const val KEY_LEGACY_THEME_STYLE = "theme_style"
        const val COLOR_SELECTION_REGISTERED = "registered"
        const val COLOR_SELECTION_CUSTOM = "custom"
        const val KEY_BROWSER_RUNTIME_MODE = "browser_runtime_mode"
        const val KEY_RESTORE_LAST_SCREEN = "restore_last_screen"
        const val KEY_HIDE_MAIN_TASK_FROM_RECENTS = "hide_main_task_from_recents"
    }
}
