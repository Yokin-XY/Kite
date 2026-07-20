package com.kite.app.feature.settings

import androidx.annotation.StringRes
import com.kite.app.R

/** 设置首页的稳定分组。分组表达用户目标，不表达底层实现模块。 */
internal enum class SettingsSection {
    Personalization,
    Usage,
    System,
    Other,
}

/** 设置入口的交互语义。业务值和副作用仍由对应 Feature/Owner 持有。 */
internal enum class SettingsEntryKind {
    Toggle,
    Choice,
    Navigation,
    SystemSettings,
    Status,
    Action,
    Info,
}

internal enum class SettingsMaturity {
    Stable,
    Experimental,
    DebugOnly,
}

/** Feature 层的类型化目标，由 Shell 映射到具体 AppDestination。 */
internal enum class SettingsCategoryDestination {
    AppearanceAndLanguage,
    AppBehavior,
    TerminalAndWorkbench,
    BrowserAndLogin,
    PermissionsAndFiles,
    RuntimeEnvironment,
    ExperimentalFeatures,
    HelpAndAbout,
}

internal data class SettingsCategorySpec(
    val id: String,
    val section: SettingsSection,
    val kind: SettingsEntryKind,
    val maturity: SettingsMaturity,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val destination: SettingsCategoryDestination,
)

internal object SettingsVisibilityPolicy {
    fun isVisible(maturity: SettingsMaturity, isDebugBuild: Boolean): Boolean = when (maturity) {
        SettingsMaturity.Stable,
        SettingsMaturity.Experimental -> true
        SettingsMaturity.DebugOnly -> isDebugBuild
    }
}

/**
 * 设置首页的编译期目录。这里只登记展示和导航元数据，不读取或保存任何设置事实。
 */
internal object SettingsCatalog {
    val categories: List<SettingsCategorySpec> = listOf(
        SettingsCategorySpec(
            id = "appearance_language",
            section = SettingsSection.Personalization,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Stable,
            titleRes = R.string.settings_category_appearance_language_title,
            summaryRes = R.string.settings_category_appearance_language_summary,
            destination = SettingsCategoryDestination.AppearanceAndLanguage,
        ),
        SettingsCategorySpec(
            id = "app_behavior",
            section = SettingsSection.Usage,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Stable,
            titleRes = R.string.settings_category_app_behavior_title,
            summaryRes = R.string.settings_category_app_behavior_summary,
            destination = SettingsCategoryDestination.AppBehavior,
        ),
        SettingsCategorySpec(
            id = "terminal_workbench",
            section = SettingsSection.Usage,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Stable,
            titleRes = R.string.settings_category_terminal_workbench_title,
            summaryRes = R.string.settings_category_terminal_workbench_summary,
            destination = SettingsCategoryDestination.TerminalAndWorkbench,
        ),
        SettingsCategorySpec(
            id = "browser_login",
            section = SettingsSection.Usage,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Stable,
            titleRes = R.string.settings_category_browser_login_title,
            summaryRes = R.string.settings_category_browser_login_summary,
            destination = SettingsCategoryDestination.BrowserAndLogin,
        ),
        SettingsCategorySpec(
            id = "permissions_files",
            section = SettingsSection.System,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Stable,
            titleRes = R.string.settings_category_permissions_files_title,
            summaryRes = R.string.settings_category_permissions_files_summary,
            destination = SettingsCategoryDestination.PermissionsAndFiles,
        ),
        SettingsCategorySpec(
            id = "runtime_environment",
            section = SettingsSection.System,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Stable,
            titleRes = R.string.settings_category_runtime_environment_title,
            summaryRes = R.string.settings_category_runtime_environment_summary,
            destination = SettingsCategoryDestination.RuntimeEnvironment,
        ),
        SettingsCategorySpec(
            id = "experimental_features",
            section = SettingsSection.Other,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Experimental,
            titleRes = R.string.settings_category_experimental_title,
            summaryRes = R.string.settings_category_experimental_summary,
            destination = SettingsCategoryDestination.ExperimentalFeatures,
        ),
        SettingsCategorySpec(
            id = "help_about",
            section = SettingsSection.Other,
            kind = SettingsEntryKind.Navigation,
            maturity = SettingsMaturity.Stable,
            titleRes = R.string.settings_category_help_about_title,
            summaryRes = R.string.settings_category_help_about_summary,
            destination = SettingsCategoryDestination.HelpAndAbout,
        ),
    )

    fun visibleCategories(isDebugBuild: Boolean): List<SettingsCategorySpec> = categories.filter {
        SettingsVisibilityPolicy.isVisible(it.maturity, isDebugBuild)
    }
}
