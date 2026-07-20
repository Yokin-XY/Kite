package com.kite.app.ui.terminal

import android.content.Context
import android.content.res.Configuration
import com.kite.app.R

enum class TerminalThemeMode(val storageValue: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromStorage(value: String?): TerminalThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}

object TerminalUiPreferences {

    private const val PREFS_NAME = "terminal_ui_prefs"
    private const val KEY_FONT_SIZE_DP = "font_size_dp"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val DEFAULT_FONT_SIZE_DP = 35
    private val FONT_PRESETS = intArrayOf(10, 12, 14, 16, 18, 20, 24, 28, 35, 42, 50, 60, 70, 80)

    fun loadFontSizeDp(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FONT_SIZE_DP, DEFAULT_FONT_SIZE_DP)
            .coerceIn(FONT_PRESETS.first(), FONT_PRESETS.last())
    }

    fun saveFontSizeDp(context: Context, value: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FONT_SIZE_DP, value.coerceIn(FONT_PRESETS.first(), FONT_PRESETS.last()))
            .apply()
    }

    fun stepFontSize(currentValue: Int, direction: Int): Int {
        val currentIndex = FONT_PRESETS.indexOf(closestFontSize(currentValue)).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction).coerceIn(0, FONT_PRESETS.lastIndex)
        return FONT_PRESETS[nextIndex]
    }

    fun scaleFontSize(currentValue: Int, scale: Float): Int {
        val scaled = (currentValue * scale).toInt().coerceIn(FONT_PRESETS.first(), FONT_PRESETS.last())
        return closestFontSize(scaled)
    }

    fun fontPresets(): List<Int> = FONT_PRESETS.toList()

    fun loadThemeMode(context: Context): TerminalThemeMode {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TerminalThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, TerminalThemeMode.SYSTEM.storageValue))
    }

    fun saveThemeMode(context: Context, mode: TerminalThemeMode) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
    }

    fun applySavedTheme(context: Context) {
        loadThemeMode(context.applicationContext)
    }

    fun resolveTerminalDarkMode(context: Context): Boolean {
        return when (loadThemeMode(context.applicationContext)) {
            TerminalThemeMode.DARK -> true
            TerminalThemeMode.LIGHT -> false
            TerminalThemeMode.SYSTEM -> {
                val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun closestFontSize(value: Int): Int {
        return FONT_PRESETS.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT_FONT_SIZE_DP
    }
}

fun Context.terminalThemeLabel(mode: TerminalThemeMode): String = getString(
    when (mode) {
        TerminalThemeMode.SYSTEM -> R.string.terminal_theme_system
        TerminalThemeMode.DARK -> R.string.terminal_theme_dark
        TerminalThemeMode.LIGHT -> R.string.terminal_theme_light
    }
)
