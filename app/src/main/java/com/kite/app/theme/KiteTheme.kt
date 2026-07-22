package com.kite.app.theme

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
/**
 * 页面级间距 token。这里只收口跨页面会反复出现的骨架尺寸。
 * 数值单位为 dp。
 */
data class ThemeSpacing(
    val pageHorizontal: Int,
    val sectionGap: Int,
    val itemGap: Int
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

object KiteTheme : ThemeRuleProtocol {
    const val defaultColorSchemeKey: String = "standard"
    const val defaultStyleKey: String = "standard"

    val defaultThemeColor: Int = rgb(14, 116, 144)
    val defaultBackgroundColor: Int = rgb(246, 248, 250)
    val defaultTextPrimary: Int = rgb(15, 23, 42)
    val defaultTextSecondary: Int = rgb(100, 116, 139)
    val defaultBorder: Int = rgb(226, 232, 240)

    private val defaultSeed = ThemeColorSeed(defaultThemeColor, defaultBackgroundColor)

    override val defaultSelection = ThemeSelection(
        mode = KiteThemeMode.SYSTEM,
        colors = ThemeColorSelection.Registered(ThemeColorSchemeKey(defaultColorSchemeKey)),
        stylePack = ThemeStylePackKey(defaultStyleKey),
    )

    val foundations = ThemeFoundations(
        spacing = ThemeSpacing(pageHorizontal = 18, sectionGap = 12, itemGap = 8),
        typography = ThemeTypography(
            pageTitle = 22f,
            sectionTitle = 13.5f,
            cardTitle = 16f,
            body = 14f,
            supporting = 12.5f,
            action = 13f,
            badge = 10.5f,
        ),
        minimumTouchTarget = 48,
    )

    private val standardComponents = ThemeComponentRecipes(
        card = ThemeContainerRecipe(radius = 24, elevation = 1, strokeWidth = 1),
        interactiveCard = ThemeContainerRecipe(radius = 24, elevation = 1, strokeWidth = 1),
        control = ThemeContainerRecipe(radius = 18, elevation = 0, strokeWidth = 1),
        chip = ThemeContainerRecipe(radius = 20, elevation = 0, strokeWidth = 1),
        iconTile = ThemeContainerRecipe(radius = 14, elevation = 0, strokeWidth = 0),
        dialog = ThemeContainerRecipe(radius = 24, elevation = 8, strokeWidth = 1),
    )

    private val contentPolicies = ThemeContentPolicies(
        terminal = ThemeContentModePolicy.FOLLOW_EFFECTIVE_MODE,
        web = ThemeContentModePolicy.PREFER_EFFECTIVE_MODE,
        x11 = ThemeContentModePolicy.PRESERVE_CONTENT,
    )

    private val standardScheme = ThemeColorSchemeDefinition(
        key = ThemeColorSchemeKey(defaultColorSchemeKey),
        light = resolveLightTokens(defaultSeed),
        dark = resolveDarkTokens(defaultSeed),
    )

    private val standardStylePack = ThemeStylePackDefinition(
        key = ThemeStylePackKey(defaultStyleKey),
        components = standardComponents,
    )

    override val catalog = ThemeCatalog(
        colorSchemes = listOf(standardScheme),
        stylePacks = listOf(standardStylePack),
    )

    /** 兼容尚未迁移为标准组件的旧视图；新代码应从 ThemeEnvironment 获取。 */
    val spacing: ThemeSpacing get() = foundations.spacing

    override fun normalize(selection: ThemeSelection): ThemeSelection {
        val colors = when (val selected = selection.colors) {
            is ThemeColorSelection.Custom -> selected
            is ThemeColorSelection.Registered -> {
                if (catalog.colorSchemes.any { it.key == selected.key }) selected else defaultSelection.colors
            }
        }
        val stylePack = selection.stylePack.takeIf { selected ->
            catalog.stylePacks.any { it.key == selected }
        } ?: defaultSelection.stylePack
        return selection.copy(colors = colors, stylePack = stylePack)
    }

    override fun apply(selection: ThemeSelection, command: ThemeCommand): ThemeSelection {
        val current = normalize(selection)
        val changed = when (command) {
            is ThemeCommand.SetMode -> current.copy(mode = command.mode)
            is ThemeCommand.SetColorScheme -> current.copy(colors = ThemeColorSelection.Registered(command.key))
            is ThemeCommand.SetCustomColors -> current.copy(colors = ThemeColorSelection.Custom(command.seed))
            is ThemeCommand.SetStylePack -> current.copy(stylePack = command.key)
            ThemeCommand.RestoreDefaults -> defaultSelection
        }
        return normalize(changed)
    }

    override fun resolve(selection: ThemeSelection, systemDark: Boolean): ThemeEnvironment {
        val normalized = normalize(selection)
        val effectiveMode = when (normalized.mode) {
            KiteThemeMode.SYSTEM -> if (systemDark) KiteEffectiveThemeMode.DARK else KiteEffectiveThemeMode.LIGHT
            KiteThemeMode.LIGHT -> KiteEffectiveThemeMode.LIGHT
            KiteThemeMode.DARK -> KiteEffectiveThemeMode.DARK
        }
        val stylePack = catalog.stylePacks.first { it.key == normalized.stylePack }
        val tokens = when (val colors = normalized.colors) {
            is ThemeColorSelection.Custom -> when (effectiveMode) {
                KiteEffectiveThemeMode.LIGHT -> resolveLightTokens(colors.seed)
                KiteEffectiveThemeMode.DARK -> resolveDarkTokens(colors.seed)
            }
            is ThemeColorSelection.Registered -> {
                val scheme = catalog.colorSchemes.first { it.key == colors.key }
                when (effectiveMode) {
                    KiteEffectiveThemeMode.LIGHT -> scheme.light
                    KiteEffectiveThemeMode.DARK -> scheme.dark
                }
            }
        }
        return ThemeEnvironment(
            selection = normalized,
            mode = effectiveMode,
            tokens = tokens,
            foundations = foundations,
            components = stylePack.components,
            contentPolicies = contentPolicies,
        )
    }

    private fun resolveLightTokens(seed: ThemeColorSeed): ThemeTokens {
        val bg = seed.background
        val primary = seed.accent
        val danger = rgb(185, 28, 28)
        val warning = rgb(234, 88, 12)
        val success = rgb(5, 150, 105)
        val info = rgb(37, 99, 235)
        return ThemeTokens(
            pageBackground = bg,
            surface = blend(COLOR_WHITE, bg, 0.42f),
            surfaceElevated = blend(COLOR_WHITE, bg, 0.25f),
            cardBackground = blend(COLOR_WHITE, bg, 0.34f),
            inputBackground = blend(COLOR_WHITE, bg, 0.18f),
            overlay = argb(150, 15, 23, 42),
            border = blend(rgb(203, 213, 225), bg, 0.42f),
            borderStrong = blend(rgb(148, 163, 184), bg, 0.28f),
            shadow = argb(38, 15, 23, 42),
            textPrimary = defaultTextPrimary,
            textSecondary = defaultTextSecondary,
            textTertiary = rgb(148, 163, 184),
            primaryStrong = primary,
            primarySoft = blend(primary, bg, 0.84f),
            primarySubtle = blend(primary, bg, 0.91f),
            primaryText = darken(primary, 0.12f),
            buttonText = COLOR_WHITE,
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

    private fun resolveDarkTokens(seed: ThemeColorSeed): ThemeTokens {
        val bg = blend(seed.background, rgb(9, 14, 24), 0.94f)
        val primary = blend(seed.accent, COLOR_WHITE, 0.18f)
        val danger = rgb(248, 113, 113)
        val warning = rgb(251, 146, 60)
        val success = rgb(52, 211, 153)
        val info = rgb(96, 165, 250)
        return ThemeTokens(
            pageBackground = bg,
            surface = blend(rgb(30, 41, 59), bg, 0.34f),
            surfaceElevated = blend(rgb(51, 65, 85), bg, 0.38f),
            cardBackground = blend(rgb(30, 41, 59), bg, 0.28f),
            inputBackground = blend(rgb(15, 23, 42), bg, 0.22f),
            overlay = argb(184, 0, 0, 0),
            border = rgb(51, 65, 85),
            borderStrong = rgb(71, 85, 105),
            shadow = argb(110, 0, 0, 0),
            textPrimary = rgb(241, 245, 249),
            textSecondary = rgb(148, 163, 184),
            textTertiary = rgb(100, 116, 139),
            primaryStrong = primary,
            primarySoft = blend(primary, bg, 0.78f),
            primarySubtle = blend(primary, bg, 0.88f),
            primaryText = blend(primary, COLOR_WHITE, 0.2f),
            buttonText = COLOR_WHITE,
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
        return rgb(
            (red(foreground) + ((red(background) - red(foreground)) * clamped)).toInt(),
            (green(foreground) + ((green(background) - green(foreground)) * clamped)).toInt(),
            (blue(foreground) + ((blue(background) - blue(foreground)) * clamped)).toInt()
        )
    }

    fun tint(color: Int, amount: Float = 0.88f): Int = rgb(
        red(color) + ((255 - red(color)) * amount).toInt(),
        green(color) + ((255 - green(color)) * amount).toInt(),
        blue(color) + ((255 - blue(color)) * amount).toInt()
    )

    fun darken(color: Int, amount: Float): Int = blend(color, COLOR_BLACK, amount)

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        argb(255, red, green, blue)

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xFF) shl 24) or ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or (blue and 0xFF)

    private fun red(color: Int): Int = color ushr 16 and 0xFF
    private fun green(color: Int): Int = color ushr 8 and 0xFF
    private fun blue(color: Int): Int = color and 0xFF

    private const val COLOR_WHITE = -0x1
    private const val COLOR_BLACK = -0x1000000
}
