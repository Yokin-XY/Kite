package com.kite.app.ui.terminal

import android.app.Activity
import com.kite.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalUiPreferencesTest {
    @Test
    fun `设置页与终端现场读写同一份字号和主题偏好`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        TerminalUiPreferences.saveFontSizeDp(activity, 50)
        TerminalUiPreferences.saveThemeMode(activity, TerminalThemeMode.DARK)

        assertEquals(50, TerminalUiPreferences.loadFontSizeDp(activity))
        assertEquals(TerminalThemeMode.DARK, TerminalUiPreferences.loadThemeMode(activity))
        assertTrue(TerminalUiPreferences.fontPresets().contains(50))
        assertEquals(activity.getString(R.string.terminal_theme_dark), activity.terminalThemeLabel(TerminalThemeMode.DARK))
    }

    @Test
    fun `非法字号仍收敛到终端支持范围`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        TerminalUiPreferences.saveFontSizeDp(activity, 999)

        assertEquals(TerminalUiPreferences.fontPresets().last(), TerminalUiPreferences.loadFontSizeDp(activity))
    }

    @Test
    fun `终端跟随模式使用应用有效主题而显式选择仍可覆盖`() {
        assertTrue(TerminalUiPreferences.resolveTerminalDarkMode(TerminalThemeMode.SYSTEM, true))
        assertFalse(TerminalUiPreferences.resolveTerminalDarkMode(TerminalThemeMode.SYSTEM, false))
        assertTrue(TerminalUiPreferences.resolveTerminalDarkMode(TerminalThemeMode.DARK, false))
        assertFalse(TerminalUiPreferences.resolveTerminalDarkMode(TerminalThemeMode.LIGHT, true))
    }
}
