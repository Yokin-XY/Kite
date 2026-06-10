package com.kftest.app.ui.terminal

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.kftest.app.R

object KiteTerminalShellTheme {
    data class Palette(
        @ColorInt val pageBackground: Int,
        @ColorInt val header: Int,
        @ColorInt val surface: Int,
        @ColorInt val textPrimary: Int,
        @ColorInt val textSecondary: Int,
        @ColorInt val border: Int,
        @ColorInt val accent: Int,
        @ColorInt val accentSoft: Int,
        @ColorInt val grayChip: Int,
        @ColorInt val inputBackground: Int,
        @ColorInt val danger: Int
    )

    @Volatile
    private var palette: Palette? = null

    fun apply(next: Palette) {
        palette = next
    }

    fun resolve(context: Context, @ColorRes resId: Int): Int {
        val current = palette ?: return ContextCompat.getColor(context, resId)
        return when (resId) {
            R.color.terminal_page_bg -> current.pageBackground
            R.color.terminal_page_header -> current.header
            R.color.terminal_page_surface -> current.surface
            R.color.terminal_page_text -> current.textPrimary
            R.color.terminal_page_subtext -> current.textSecondary
            R.color.terminal_page_line -> current.border
            R.color.terminal_page_green -> current.accent
            R.color.terminal_page_blue -> current.accent
            R.color.terminal_page_gray_chip -> current.grayChip
            R.color.terminal_page_input_bg -> current.inputBackground
            R.color.error -> current.danger
            else -> ContextCompat.getColor(context, resId)
        }
    }
}
