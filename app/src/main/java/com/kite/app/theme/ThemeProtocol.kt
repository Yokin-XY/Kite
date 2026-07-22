package com.kite.app.theme

/**
 * 主题系统的中间规则协议。
 *
 * 设置页、快捷命令和未来的外部配置入口只提交 [ThemeCommand]；界面只消费
 * [ThemeEnvironment]。两侧都不需要知道对方的存储方式或页面结构。
 */
data class ThemeColorSchemeKey(val value: String)

data class ThemeStylePackKey(val value: String)

data class ThemeColorSeed(
    val accent: Int,
    val background: Int,
)

sealed interface ThemeColorSelection {
    data class Registered(val key: ThemeColorSchemeKey) : ThemeColorSelection

    /** 只用于兼容历史调色板或受控导入，不作为默认设置入口。 */
    data class Custom(val seed: ThemeColorSeed) : ThemeColorSelection
}

data class ThemeSelection(
    val mode: KiteThemeMode,
    val colors: ThemeColorSelection,
    val stylePack: ThemeStylePackKey,
)

sealed interface ThemeCommand {
    data class SetMode(val mode: KiteThemeMode) : ThemeCommand
    data class SetColorScheme(val key: ThemeColorSchemeKey) : ThemeCommand
    data class SetCustomColors(val seed: ThemeColorSeed) : ThemeCommand
    data class SetStylePack(val key: ThemeStylePackKey) : ThemeCommand
    data object RestoreDefaults : ThemeCommand
}

data class ThemeColorSchemeDefinition(
    val key: ThemeColorSchemeKey,
    val light: ThemeTokens,
    val dark: ThemeTokens,
    val userSelectable: Boolean = true,
)

/** 固定设计基础不会跟随样式包切换。 */
data class ThemeFoundations(
    val spacing: ThemeSpacing,
    val typography: ThemeTypography,
    val minimumTouchTarget: Int,
)

/**
 * 固定排版层级，单位为 sp。
 *
 * 排版表达的是信息层级，不属于可切换样式包，避免换主题时页面结构和可读性一起漂移。
 */
data class ThemeTypography(
    val pageTitle: Float,
    val sectionTitle: Float,
    val cardTitle: Float,
    val body: Float,
    val supporting: Float,
    val action: Float,
    val badge: Float,
)

/** 一个语义容器的形状、层级和边界配方，单位均为 dp。 */
data class ThemeContainerRecipe(
    val radius: Int,
    val elevation: Int,
    val strokeWidth: Int,
)

/** 页面按语义组件取配方，不再按页面名取一套主题。 */
data class ThemeComponentRecipes(
    val card: ThemeContainerRecipe,
    val interactiveCard: ThemeContainerRecipe,
    val control: ThemeContainerRecipe,
    val chip: ThemeContainerRecipe,
    val iconTile: ThemeContainerRecipe,
    val dialog: ThemeContainerRecipe,
)

data class ThemeStylePackDefinition(
    val key: ThemeStylePackKey,
    val components: ThemeComponentRecipes,
    val userSelectable: Boolean = true,
)

enum class ThemeContentModePolicy {
    FOLLOW_EFFECTIVE_MODE,
    PREFER_EFFECTIVE_MODE,
    PRESERVE_CONTENT,
}

/** 终端、网页、X11 等特殊内容面只接收明确的适配策略。 */
data class ThemeContentPolicies(
    val terminal: ThemeContentModePolicy,
    val web: ThemeContentModePolicy,
    val x11: ThemeContentModePolicy,
)

data class ThemeCatalog(
    val colorSchemes: List<ThemeColorSchemeDefinition>,
    val stylePacks: List<ThemeStylePackDefinition>,
) {
    val selectableColorSchemes: List<ThemeColorSchemeDefinition>
        get() = colorSchemes.filter(ThemeColorSchemeDefinition::userSelectable)

    val selectableStylePacks: List<ThemeStylePackDefinition>
        get() = stylePacks.filter(ThemeStylePackDefinition::userSelectable)
}

data class ThemeEnvironment(
    val selection: ThemeSelection,
    val mode: KiteEffectiveThemeMode,
    val tokens: ThemeTokens,
    val foundations: ThemeFoundations,
    val components: ThemeComponentRecipes,
    val contentPolicies: ThemeContentPolicies,
) {
    val isDark: Boolean get() = mode == KiteEffectiveThemeMode.DARK
}

interface ThemeRuleProtocol {
    val catalog: ThemeCatalog
    val defaultSelection: ThemeSelection

    fun normalize(selection: ThemeSelection): ThemeSelection
    fun apply(selection: ThemeSelection, command: ThemeCommand): ThemeSelection
    fun resolve(selection: ThemeSelection, systemDark: Boolean): ThemeEnvironment
}
