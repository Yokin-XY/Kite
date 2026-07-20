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
            is SettingsCommand.SetThemeColor -> themePreferences.edit()
                .putInt(KEY_THEME_COLOR, command.color)
                .commit()
            is SettingsCommand.SetBackgroundColor -> themePreferences.edit()
                .putInt(KEY_BACKGROUND_COLOR, command.color)
                .commit()
            is SettingsCommand.SetThemeMode -> themePreferences.edit()
                .putString(KEY_THEME_MODE, command.mode.storageKey)
                .commit()
            is SettingsCommand.SetThemeStyle -> themePreferences.edit()
                .putString(KEY_THEME_STYLE, command.styleKey)
                .commit()
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
        themeColor = themePreferences.getInt(KEY_THEME_COLOR, KiteTheme.defaultThemeColor),
        backgroundColor = themePreferences.getInt(KEY_BACKGROUND_COLOR, KiteTheme.defaultBackgroundColor),
        appLanguage = readAppLanguage(),
        browserRuntimeMode = BrowserRuntimeMode.fromStorageKey(
            appPreferences.getString(KEY_BROWSER_RUNTIME_MODE, null)
        ),
        restoreLastScreen = appPreferences.getBoolean(KEY_RESTORE_LAST_SCREEN, true),
        hideMainTaskFromRecents = appPreferences.getBoolean(KEY_HIDE_MAIN_TASK_FROM_RECENTS, false),
        notificationsEnabled = readNotificationsEnabled(),
        dropZone = dropZone,
        revision = nextRevision(),
        themeMode = KiteThemeMode.fromStorageKey(themePreferences.getString(KEY_THEME_MODE, null)),
        themeStyleKey = themePreferences.getString(KEY_THEME_STYLE, null)
            ?.takeIf { key -> KiteTheme.styleDefinitions.any { it.key == key } }
            ?: KiteTheme.defaultStyleKey,
    )

    @Synchronized
    private fun nextRevision(): Long = ++revision

    private fun publish(snapshot: SettingsSnapshot): SettingsSnapshot = snapshot.also {
        mutableSnapshots.value = it
    }

    private companion object {
        const val THEME_PREFERENCES = "kite_theme"
        const val APP_PREFERENCES = "kite_app_settings"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_BACKGROUND_COLOR = "background_color"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_STYLE = "theme_style"
        const val KEY_BROWSER_RUNTIME_MODE = "browser_runtime_mode"
        const val KEY_RESTORE_LAST_SCREEN = "restore_last_screen"
        const val KEY_HIDE_MAIN_TASK_FROM_RECENTS = "hide_main_task_from_recents"
    }
}
