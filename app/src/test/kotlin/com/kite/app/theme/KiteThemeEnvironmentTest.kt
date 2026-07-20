package com.kite.app.theme

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KiteThemeEnvironmentTest {
    private val base = ThemeConfig(
        themeColor = KiteTheme.defaultThemeColor,
        backgroundColor = KiteTheme.defaultBackgroundColor,
    )

    @Test
    fun `跟随系统只在系统暗色时解析为暗色`() {
        assertFalse(KiteTheme.resolveEnvironment(base, systemDark = false).isDark)
        assertTrue(KiteTheme.resolveEnvironment(base, systemDark = true).isDark)
    }

    @Test
    fun `显式明暗模式覆盖系统状态`() {
        assertFalse(
            KiteTheme.resolveEnvironment(
                base.copy(mode = KiteThemeMode.LIGHT),
                systemDark = true,
            ).isDark,
        )
        assertTrue(
            KiteTheme.resolveEnvironment(
                base.copy(mode = KiteThemeMode.DARK),
                systemDark = false,
            ).isDark,
        )
    }

    @Test
    fun `未知样式回退标准样式且作用域继承中央定义`() {
        val environment = KiteTheme.resolveEnvironment(
            base.copy(styleKey = "future_missing_style"),
            systemDark = false,
        )

        assertEquals(KiteTheme.defaultStyleKey, environment.style.key)
        assertEquals(
            environment.style.base,
            environment.forScope(ThemeScope.HOME).components,
        )
        assertEquals(
            environment.style.base,
            environment.forScope(ThemeScope.TERMINAL).components,
        )
    }

    @Test
    fun `明暗环境都提供可读语义前景`() {
        val light = KiteTheme.resolveEnvironment(base, systemDark = false).tokens
        val dark = KiteTheme.resolveEnvironment(base, systemDark = true).tokens

        assertTrue(luminance(light.textPrimary) < luminance(light.pageBackground))
        assertTrue(luminance(dark.textPrimary) > luminance(dark.pageBackground))
        assertTrue(luminance(dark.surfaceElevated) > luminance(dark.pageBackground))
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
