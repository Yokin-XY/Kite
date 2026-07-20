package com.kite.app.ui.terminal

/**
 * Kite 拥有的确定性终端色板。
 *
 * Termux 的默认色表是进程级可变对象，因此这里始终从不可变规则重新生成，
 * 不能把当前全局色表再当作下一次主题切换的输入。
 */
internal object TerminalColorPalette {
    const val ANSI_COLOR_COUNT = 16
    const val EXTENDED_COLOR_END = 255
    const val FOREGROUND_INDEX = 256
    const val BACKGROUND_INDEX = 257
    const val CURSOR_INDEX = 258
    const val COLOR_COUNT = 259

    private val darkAnsi = intArrayOf(
        0xFF202A35.toInt(),
        0xFFF47067.toInt(),
        0xFF57D18B.toInt(),
        0xFFE5C07B.toInt(),
        0xFF61AFEF.toInt(),
        0xFFC678DD.toInt(),
        0xFF56B6C2.toInt(),
        0xFFC8D0DA.toInt(),
        0xFF667381.toInt(),
        0xFFFF7B72.toInt(),
        0xFF6DDB9B.toInt(),
        0xFFF0CF86.toInt(),
        0xFF79B8FF.toInt(),
        0xFFD99AF0.toInt(),
        0xFF68D4DF.toInt(),
        0xFFF7FBFF.toInt(),
    )

    private val lightAnsi = intArrayOf(
        0xFF252B33.toInt(),
        0xFFB42318.toInt(),
        0xFF18794E.toInt(),
        0xFF8A5A00.toInt(),
        0xFF155EEF.toInt(),
        0xFF7A3E9D.toInt(),
        0xFF0E7490.toInt(),
        0xFF5F6873.toInt(),
        0xFF697381.toInt(),
        0xFFC4320A.toInt(),
        0xFF147D55.toInt(),
        0xFF986801.toInt(),
        0xFF175CD3.toInt(),
        0xFF8B45A6.toInt(),
        0xFF087E8B.toInt(),
        0xFF4D5661.toInt(),
    )

    fun create(isDark: Boolean): IntArray {
        val colors = IntArray(COLOR_COUNT)
        val ansi = if (isDark) darkAnsi else lightAnsi
        System.arraycopy(ansi, 0, colors, 0, ANSI_COLOR_COUNT)
        fillStandardExtendedColors(colors)
        if (isDark) {
            colors[FOREGROUND_INDEX] = 0xFFF2F6FB.toInt()
            colors[BACKGROUND_INDEX] = 0xFF0B1118.toInt()
            colors[CURSOR_INDEX] = 0xFFA8C7FA.toInt()
        } else {
            colors[FOREGROUND_INDEX] = 0xFF1F2329.toInt()
            colors[BACKGROUND_INDEX] = 0xFFFDFDFD.toInt()
            colors[CURSOR_INDEX] = 0xFF155EEF.toInt()
        }
        return colors
    }

    fun applyTo(target: IntArray, isDark: Boolean) {
        val calibrated = create(isDark)
        System.arraycopy(calibrated, 0, target, 0, minOf(calibrated.size, target.size))
    }

    private fun fillStandardExtendedColors(colors: IntArray) {
        var index = ANSI_COLOR_COUNT
        for (red in 0..5) {
            for (green in 0..5) {
                for (blue in 0..5) {
                    colors[index++] = argb(
                        component(red),
                        component(green),
                        component(blue),
                    )
                }
            }
        }
        for (step in 0 until 24) {
            val channel = 8 + step * 10
            colors[index++] = argb(channel, channel, channel)
        }
        check(index == EXTENDED_COLOR_END + 1)
    }

    private fun component(value: Int): Int = if (value == 0) 0 else 55 + value * 40

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
