package com.kite.app.theme

import android.graphics.Color

/**
 * Central design-token entry for Kite.
 *
 * Treat these values like UI environment variables: screens and components
 * should request semantic tokens instead of hard-coding colors locally.
 */
data class ThemeConfig(
    val themeColor: Int,
    val backgroundColor: Int
)

data class ThemeChoice(
    val label: String,
    val color: Int
)

data class ThemeTokens(
    val pageBackground: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val cardBackground: Int,
    val inputBackground: Int,
    val overlay: Int,
    val border: Int,
    val borderStrong: Int,
    val shadow: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val primaryStrong: Int,
    val primarySoft: Int,
    val primarySubtle: Int,
    val primaryText: Int,
    val buttonText: Int,
    val danger: Int,
    val dangerSoft: Int,
    val dangerBorder: Int,
    val warning: Int,
    val warningSoft: Int,
    val warningBorder: Int,
    val success: Int,
    val successSoft: Int,
    val successBorder: Int,
    val info: Int,
    val infoSoft: Int,
    val infoBorder: Int
)

data class KiteTone(
    val strong: Int,
    val soft: Int,
    val subtle: Int,
    val border: Int
)

enum class KiteAccent(val key: String) {
    Terminal("terminal"),
    Web("web"),
    Bot("bot"),
    File("file"),
    Music("music"),
    Shopping("shopping"),
    Logs("logs"),
    Tools("tools"),
    Code("code"),
    Server("server"),
    Default("default")
}

object KiteTheme {
    val defaultThemeColor: Int = Color.rgb(14, 116, 144)
    val defaultBackgroundColor: Int = Color.rgb(246, 248, 250)

    val themeColorChoices: List<ThemeChoice> = listOf(
        ThemeChoice("冷青", Color.rgb(14, 116, 144)),
        ThemeChoice("紫", Color.rgb(109, 67, 230)),
        ThemeChoice("绿", Color.rgb(5, 150, 105)),
        ThemeChoice("蓝", Color.rgb(37, 99, 235)),
        ThemeChoice("橙", Color.rgb(234, 88, 12))
    )

    val backgroundColorChoices: List<ThemeChoice> = listOf(
        ThemeChoice("冷灰", Color.rgb(246, 248, 250)),
        ThemeChoice("白", Color.WHITE),
        ThemeChoice("米白", Color.rgb(251, 247, 239)),
        ThemeChoice("雾蓝", Color.rgb(243, 247, 251)),
        ThemeChoice("浅青", Color.rgb(241, 248, 247))
    )

    val defaultTextPrimary: Int = Color.rgb(15, 23, 42)
    val defaultTextSecondary: Int = Color.rgb(100, 116, 139)
    val defaultBorder: Int = Color.rgb(226, 232, 240)

    fun resolve(config: ThemeConfig): ThemeTokens {
        val bg = config.backgroundColor
        val primary = config.themeColor
        val danger = Color.rgb(185, 28, 28)
        val warning = Color.rgb(234, 88, 12)
        val success = Color.rgb(5, 150, 105)
        val info = Color.rgb(37, 99, 235)
        return ThemeTokens(
            pageBackground = bg,
            surface = blend(Color.WHITE, bg, 0.42f),
            surfaceElevated = blend(Color.WHITE, bg, 0.25f),
            cardBackground = blend(Color.WHITE, bg, 0.34f),
            inputBackground = blend(Color.WHITE, bg, 0.18f),
            overlay = Color.argb(150, 15, 23, 42),
            border = blend(Color.rgb(203, 213, 225), bg, 0.42f),
            borderStrong = blend(Color.rgb(148, 163, 184), bg, 0.28f),
            shadow = Color.argb(38, 15, 23, 42),
            textPrimary = defaultTextPrimary,
            textSecondary = defaultTextSecondary,
            textTertiary = Color.rgb(148, 163, 184),
            primaryStrong = primary,
            primarySoft = blend(primary, bg, 0.84f),
            primarySubtle = blend(primary, bg, 0.91f),
            primaryText = darken(primary, 0.12f),
            buttonText = Color.WHITE,
            danger = danger,
            dangerSoft = blend(danger, bg, 0.9f),
            dangerBorder = blend(danger, bg, 0.76f),
            warning = warning,
            warningSoft = blend(warning, bg, 0.9f),
            warningBorder = blend(warning, bg, 0.76f),
            success = success,
            successSoft = blend(success, bg, 0.88f),
            successBorder = blend(success, bg, 0.72f),
            info = info,
            infoSoft = blend(info, bg, 0.9f),
            infoBorder = blend(info, bg, 0.76f)
        )
    }

    fun accent(name: String?, tokens: ThemeTokens): KiteTone =
        when (name?.lowercase()) {
            "primary", "theme", "workflow" -> KiteTone(
                tokens.primaryStrong,
                tokens.primarySoft,
                tokens.primarySubtle,
                tokens.primarySoft
            )
            "green" -> KiteTone(tokens.success, tokens.successSoft, tokens.primarySubtle, tokens.successBorder)
            "purple" -> KiteTone(tokens.primaryStrong, tokens.primarySoft, tokens.primarySubtle, tokens.primarySoft)
            "orange" -> KiteTone(tokens.warning, tokens.warningSoft, tokens.warningSoft, tokens.warningBorder)
            "blue" -> KiteTone(tokens.info, tokens.infoSoft, tokens.infoSoft, tokens.infoBorder)
            else -> KiteTone(tokens.primaryStrong, tokens.primarySoft, tokens.primarySubtle, tokens.primarySoft)
        }

    fun blend(foreground: Int, background: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(foreground) + ((Color.red(background) - Color.red(foreground)) * clamped)).toInt(),
            (Color.green(foreground) + ((Color.green(background) - Color.green(foreground)) * clamped)).toInt(),
            (Color.blue(foreground) + ((Color.blue(background) - Color.blue(foreground)) * clamped)).toInt()
        )
    }

    fun tint(color: Int, amount: Float = 0.88f): Int = Color.rgb(
        Color.red(color) + ((255 - Color.red(color)) * amount).toInt(),
        Color.green(color) + ((255 - Color.green(color)) * amount).toInt(),
        Color.blue(color) + ((255 - Color.blue(color)) * amount).toInt()
    )

    fun darken(color: Int, amount: Float): Int = blend(color, Color.BLACK, amount)
}
