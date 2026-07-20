package com.kite.app.ui.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class TerminalColorPaletteTest {

    @Test
    fun `明暗色板重复生成稳定且互不污染`() {
        val firstDark = TerminalColorPalette.create(isDark = true)
        val firstLight = TerminalColorPalette.create(isDark = false)
        firstDark[1] = 0
        firstLight[2] = 0

        val secondDark = TerminalColorPalette.create(isDark = true)
        val secondLight = TerminalColorPalette.create(isDark = false)

        assertNotEquals(0, secondDark[1])
        assertNotEquals(0, secondLight[2])
        assertArrayEquals(secondDark, TerminalColorPalette.create(isDark = true))
        assertArrayEquals(secondLight, TerminalColorPalette.create(isDark = false))
    }

    @Test
    fun `色板覆盖完整索引并保持标准扩展色`() {
        val dark = TerminalColorPalette.create(isDark = true)
        val light = TerminalColorPalette.create(isDark = false)

        assertEquals(TerminalColorPalette.COLOR_COUNT, dark.size)
        assertEquals(TerminalColorPalette.COLOR_COUNT, light.size)
        assertEquals(0xFF000000.toInt(), dark[16])
        assertEquals(0xFF5F0000.toInt(), dark[52])
        assertEquals(0xFFEEEEEE.toInt(), dark[255])
        assertEquals(dark.sliceArray(16..255).toList(), light.sliceArray(16..255).toList())
    }

    @Test
    fun `亮色基础色达到正文对比度`() {
        val colors = TerminalColorPalette.create(isDark = false)
        val background = colors[TerminalColorPalette.BACKGROUND_INDEX]

        (0..15).forEach { index ->
            assertTrue(
                "light ANSI $index contrast=${contrast(colors[index], background)}",
                contrast(colors[index], background) >= 4.5,
            )
        }
        assertTrue(contrast(colors[TerminalColorPalette.FOREGROUND_INDEX], background) >= 7.0)
    }

    @Test
    fun `暗色彩色文字清晰且黑灰层级保留`() {
        val colors = TerminalColorPalette.create(isDark = true)
        val background = colors[TerminalColorPalette.BACKGROUND_INDEX]

        (1..15).forEach { index ->
            assertTrue(
                "dark ANSI $index contrast=${contrast(colors[index], background)}",
                contrast(colors[index], background) >= 3.5,
            )
        }
        assertTrue(contrast(colors[TerminalColorPalette.FOREGROUND_INDEX], background) >= 7.0)
        assertTrue(contrast(colors[0], background) < contrast(colors[8], background))
    }

    private fun contrast(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun component(shift: Int): Double {
            val channel = ((color shr shift) and 0xFF) / 255.0
            return if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * component(16) + 0.7152 * component(8) + 0.0722 * component(0)
    }
}
