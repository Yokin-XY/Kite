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
    val backgroundColor: Int,
    val mode: KiteThemeMode = KiteThemeMode.SYSTEM,
    val styleKey: String = "standard",
)

enum class KiteThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageKey(value: String?): KiteThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}

enum class KiteEffectiveThemeMode {
    LIGHT,
    DARK,
}

/** 页面只声明自己属于哪个显示作用域，不按样式名写条件分支。 */
enum class ThemeScope {
    APP,
    HOME,
    RESOURCE,
    SETTINGS,
    EDITOR,
    RUN,
    TERMINAL,
}

data class ThemeChoice(
    val key: String,
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

/**
 * 与颜色无关的形状 token。页面只能消费这些语义半径，避免各自写一套圆角。
 * 数值单位为 dp，由视图层在使用时换算为像素。
 */
data class ThemeShapes(
    val cardRadius: Int,
    val controlRadius: Int,
    val chipRadius: Int,
    val iconTileRadius: Int
)

/**
 * 页面级间距 token。这里只收口跨页面会反复出现的骨架尺寸。
 * 数值单位为 dp。
 */
data class ThemeSpacing(
    val pageHorizontal: Int,
    val sectionGap: Int,
    val itemGap: Int
)

/**
 * 与颜色分离的组件样式合同。以后改变圆角、描边或层级，只扩展样式定义；
 * 页面仍消费相同的语义字段。
 */
data class ThemeComponentStyle(
    val shapes: ThemeShapes,
    val spacing: ThemeSpacing,
    val cardElevation: Int,
    val controlElevation: Int,
    val strokeWidth: Int,
)

data class ThemeStyleDefinition(
    val key: String,
    val base: ThemeComponentStyle,
    val scopeOverrides: Map<ThemeScope, ThemeComponentStyle> = emptyMap(),
) {
    fun forScope(scope: ThemeScope): ThemeComponentStyle = scopeOverrides[scope] ?: base
}

data class ScopedThemeEnvironment(
    val scope: ThemeScope,
    val mode: KiteEffectiveThemeMode,
    val tokens: ThemeTokens,
    val components: ThemeComponentStyle,
) {
    val isDark: Boolean get() = mode == KiteEffectiveThemeMode.DARK
}

data class ThemeEnvironment(
    val config: ThemeConfig,
    val mode: KiteEffectiveThemeMode,
    val tokens: ThemeTokens,
    val style: ThemeStyleDefinition,
) {
    val isDark: Boolean get() = mode == KiteEffectiveThemeMode.DARK

    fun forScope(scope: ThemeScope): ScopedThemeEnvironment = ScopedThemeEnvironment(
        scope = scope,
        mode = mode,
        tokens = tokens,
        components = style.forScope(scope),
    )
}

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
    const val defaultStyleKey: String = "standard"

    private val standardStyle = ThemeStyleDefinition(
        key = defaultStyleKey,
        base = ThemeComponentStyle(
            shapes = ThemeShapes(
                cardRadius = 24,
                controlRadius = 18,
                chipRadius = 20,
                iconTileRadius = 14,
            ),
            spacing = ThemeSpacing(
                pageHorizontal = 18,
                sectionGap = 12,
                itemGap = 8,
            ),
            cardElevation = 1,
            controlElevation = 0,
            strokeWidth = 1,
        ),
    )

    /** 新样式只需登记到这里；设置页和作用域解析不需要新增页面分支。 */
    val styleDefinitions: List<ThemeStyleDefinition> = listOf(standardStyle)

    /** 兼容现有组件；新代码优先从 ThemeEnvironment.components 消费。 */
    val shapes: ThemeShapes get() = standardStyle.base.shapes
    val spacing: ThemeSpacing get() = standardStyle.base.spacing

    val defaultThemeColor: Int = Color.rgb(14, 116, 144)
    val defaultBackgroundColor: Int = Color.rgb(246, 248, 250)

    val themeColorChoices: List<ThemeChoice> = listOf(
        ThemeChoice("cool_cyan", Color.rgb(14, 116, 144)),
        ThemeChoice("purple", Color.rgb(109, 67, 230)),
        ThemeChoice("green", Color.rgb(5, 150, 105)),
        ThemeChoice("blue", Color.rgb(37, 99, 235)),
        ThemeChoice("orange", Color.rgb(234, 88, 12))
    )

    val backgroundColorChoices: List<ThemeChoice> = listOf(
        ThemeChoice("cool_gray", Color.rgb(246, 248, 250)),
        ThemeChoice("white", Color.WHITE),
        ThemeChoice("ivory", Color.rgb(251, 247, 239)),
        ThemeChoice("mist_blue", Color.rgb(243, 247, 251)),
        ThemeChoice("light_cyan", Color.rgb(241, 248, 247))
    )

    val defaultTextPrimary: Int = Color.rgb(15, 23, 42)
    val defaultTextSecondary: Int = Color.rgb(100, 116, 139)
    val defaultBorder: Int = Color.rgb(226, 232, 240)

    /** 旧调用点的过渡入口，等同于在非暗色系统中解析。 */
    fun resolve(config: ThemeConfig): ThemeTokens = resolveEnvironment(config, systemDark = false).tokens

    fun resolveEnvironment(config: ThemeConfig, systemDark: Boolean): ThemeEnvironment {
        val effectiveMode = when (config.mode) {
            KiteThemeMode.SYSTEM -> if (systemDark) KiteEffectiveThemeMode.DARK else KiteEffectiveThemeMode.LIGHT
            KiteThemeMode.LIGHT -> KiteEffectiveThemeMode.LIGHT
            KiteThemeMode.DARK -> KiteEffectiveThemeMode.DARK
        }
        val style = styleDefinitions.firstOrNull { it.key == config.styleKey } ?: standardStyle
        val normalizedConfig = if (style.key == config.styleKey) config else config.copy(styleKey = style.key)
        val tokens = when (effectiveMode) {
            KiteEffectiveThemeMode.LIGHT -> resolveLightTokens(normalizedConfig)
            KiteEffectiveThemeMode.DARK -> resolveDarkTokens(normalizedConfig)
        }
        return ThemeEnvironment(
            config = normalizedConfig,
            mode = effectiveMode,
            tokens = tokens,
            style = style,
        )
    }

    private fun resolveLightTokens(config: ThemeConfig): ThemeTokens {
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

    private fun resolveDarkTokens(config: ThemeConfig): ThemeTokens {
        val bg = blend(config.backgroundColor, Color.rgb(9, 14, 24), 0.94f)
        val primary = blend(config.themeColor, Color.WHITE, 0.18f)
        val danger = Color.rgb(248, 113, 113)
        val warning = Color.rgb(251, 146, 60)
        val success = Color.rgb(52, 211, 153)
        val info = Color.rgb(96, 165, 250)
        return ThemeTokens(
            pageBackground = bg,
            surface = blend(Color.rgb(30, 41, 59), bg, 0.34f),
            surfaceElevated = blend(Color.rgb(51, 65, 85), bg, 0.38f),
            cardBackground = blend(Color.rgb(30, 41, 59), bg, 0.28f),
            inputBackground = blend(Color.rgb(15, 23, 42), bg, 0.22f),
            overlay = Color.argb(184, 0, 0, 0),
            border = Color.rgb(51, 65, 85),
            borderStrong = Color.rgb(71, 85, 105),
            shadow = Color.argb(110, 0, 0, 0),
            textPrimary = Color.rgb(241, 245, 249),
            textSecondary = Color.rgb(148, 163, 184),
            textTertiary = Color.rgb(100, 116, 139),
            primaryStrong = primary,
            primarySoft = blend(primary, bg, 0.78f),
            primarySubtle = blend(primary, bg, 0.88f),
            primaryText = blend(primary, Color.WHITE, 0.2f),
            buttonText = Color.WHITE,
            danger = danger,
            dangerSoft = blend(danger, bg, 0.86f),
            dangerBorder = blend(danger, bg, 0.68f),
            warning = warning,
            warningSoft = blend(warning, bg, 0.86f),
            warningBorder = blend(warning, bg, 0.68f),
            success = success,
            successSoft = blend(success, bg, 0.84f),
            successBorder = blend(success, bg, 0.66f),
            info = info,
            infoSoft = blend(info, bg, 0.86f),
            infoBorder = blend(info, bg, 0.68f),
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
            "teal", "cyan" -> KiteTone(tokens.primaryStrong, tokens.primarySoft, tokens.primarySubtle, tokens.primarySoft)
            "mint" -> KiteTone(tokens.success, tokens.successSoft, tokens.successSoft, tokens.successBorder)
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
