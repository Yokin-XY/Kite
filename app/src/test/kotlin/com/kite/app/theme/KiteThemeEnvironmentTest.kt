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
    private val base = KiteTheme.defaultSelection

    @Test
    fun `跟随系统只在系统暗色时解析为暗色`() {
        assertFalse(KiteTheme.resolve(base, systemDark = false).isDark)
        assertTrue(KiteTheme.resolve(base, systemDark = true).isDark)
    }

    @Test
    fun `显式明暗模式覆盖系统状态`() {
        assertFalse(
            KiteTheme.resolve(
                base.copy(mode = KiteThemeMode.LIGHT),
                systemDark = true,
            ).isDark,
        )
        assertTrue(
            KiteTheme.resolve(
                base.copy(mode = KiteThemeMode.DARK),
                systemDark = false,
            ).isDark,
        )
    }

    @Test
    fun `未知色彩和样式在中间协议中央回退`() {
        val environment = KiteTheme.resolve(
            base.copy(
                colors = ThemeColorSelection.Registered(ThemeColorSchemeKey("missing")),
                stylePack = ThemeStylePackKey("missing"),
            ),
            systemDark = false,
        )

        assertEquals(KiteTheme.defaultSelection, environment.selection)
        assertEquals(KiteTheme.defaultStyleKey, environment.selection.stylePack.value)
        assertEquals(KiteTheme.defaultColorSchemeKey,
            (environment.selection.colors as ThemeColorSelection.Registered).key.value)
    }

    @Test
    fun `命令端和接收端共用同一主题协议`() {
        val selected = KiteTheme.apply(base, ThemeCommand.SetMode(KiteThemeMode.DARK))
        val environment = KiteTheme.resolve(selected, systemDark = false)

        assertEquals(KiteThemeMode.DARK, selected.mode)
        assertTrue(environment.isDark)
        assertEquals(selected, environment.selection)
    }

    @Test
    fun `历史自定义颜色通过受控选择保留而不进入公开目录`() {
        val custom = ThemeColorSeed(0xFF123456.toInt(), 0xFFF4F6F8.toInt())
        val selected = KiteTheme.apply(base, ThemeCommand.SetCustomColors(custom))
        val environment = KiteTheme.resolve(selected, systemDark = false)

        assertEquals(ThemeColorSelection.Custom(custom), environment.selection.colors)
        assertEquals(2, KiteTheme.catalog.selectableColorSchemes.size)
        assertEquals(1, KiteTheme.catalog.selectableStylePacks.size)
    }

    @Test
    fun `ChatGPT中性色是默认方案且经典青色仍可显式选择`() {
        val light = KiteTheme.resolve(base, systemDark = false).tokens
        val dark = KiteTheme.resolve(base, systemDark = true).tokens

        assertEquals("chatgpt", KiteTheme.defaultColorSchemeKey)
        assertEquals(Color.WHITE, light.pageBackground)
        assertEquals(Color.rgb(247, 247, 247), light.cardBackground)
        assertEquals(Color.rgb(33, 33, 33), light.primaryStrong)
        assertEquals(Color.BLACK, dark.pageBackground)
        assertEquals(Color.rgb(236, 236, 236), dark.primaryStrong)
        assertEquals(Color.rgb(20, 20, 20), dark.buttonText)

        val classic = KiteTheme.apply(
            base,
            ThemeCommand.SetColorScheme(ThemeColorSchemeKey("standard")),
        )
        assertEquals(
            Color.rgb(14, 116, 144),
            KiteTheme.resolve(classic, systemDark = false).tokens.primaryStrong,
        )
    }

    @Test
    fun `固定基础与组件配方分离且特殊内容策略明确`() {
        val environment = KiteTheme.resolve(base, systemDark = false)

        assertEquals(48, environment.foundations.minimumTouchTarget)
        assertEquals(22f, environment.foundations.typography.pageTitle)
        assertEquals(16f, environment.foundations.typography.cardTitle)
        assertEquals(12.5f, environment.foundations.typography.supporting)
        assertTrue(
            environment.foundations.typography.pageTitle >
                environment.foundations.typography.cardTitle,
        )
        assertTrue(
            environment.foundations.typography.cardTitle >
                environment.foundations.typography.supporting,
        )
        assertEquals(24, environment.components.card.radius)
        assertEquals(18, environment.components.control.radius)
        assertEquals(ThemeContentModePolicy.FOLLOW_EFFECTIVE_MODE, environment.contentPolicies.terminal)
        assertEquals(ThemeContentModePolicy.PREFER_EFFECTIVE_MODE, environment.contentPolicies.web)
        assertEquals(ThemeContentModePolicy.PRESERVE_CONTENT, environment.contentPolicies.x11)
    }

    @Test
    fun `明暗环境都提供可读语义前景`() {
        val light = KiteTheme.resolve(base, systemDark = false).tokens
        val dark = KiteTheme.resolve(base, systemDark = true).tokens

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
