package com.kite.app.feature.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapStage
import com.kite.app.application.runtimebootstrap.RuntimeRootfsPhase
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.ui.UiTextRole
import com.kite.app.ui.theme.isSystemDarkTheme
import com.kite.app.ui.terminal.TerminalThemeMode
import com.kite.app.ui.terminal.TerminalUiPreferences
import com.kite.app.ui.terminal.terminalThemeLabel

internal data class SettingsAppInfo(
    val versionName: String = "-",
    val versionCode: Long = 0L,
)

/** 设置二级页的轻量骨架；不执行动态探测。 */
internal class SettingsCategoryScreen(
    private val context: Context,
    private val destination: SettingsCategoryDestination,
    initialState: SettingsUiState,
    initialRuntimeSnapshot: RuntimeBootstrapSnapshot = RuntimeBootstrapSnapshot(),
    appInfo: SettingsAppInfo = SettingsAppInfo(),
    initialTerminalFontSize: Int = 35,
    initialTerminalTheme: TerminalThemeMode = TerminalThemeMode.SYSTEM,
    onBack: () -> Unit,
    onOpenCategory: (SettingsCategoryDestination) -> Unit = {},
    onOpenTheme: () -> Unit = {},
    private val onSelectAppLanguage: (AppLanguagePreference) -> Unit = {},
    private val onSelectBrowserMode: (BrowserRuntimeMode) -> Unit = {},
    private val onSelectTerminalFontSize: (Int) -> Unit = {},
    private val onSelectTerminalTheme: (TerminalThemeMode) -> Unit = {},
    onRestoreLastScreen: (Boolean) -> Unit = {},
    onHideMainTask: (Boolean) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenAllFilesSettings: () -> Unit = {},
    onOpenProcesses: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenDropZone: () -> Unit = {},
    onOpenAboutPage: (SettingsAboutPage) -> Unit = {},
    onOpenExternal: (String) -> Unit = {},
) {
    private val spec = SettingsCatalog.categories.single { it.destination == destination }
    private val themeEnvironment = KiteTheme.resolve(
        initialState.theme,
        context.isSystemDarkTheme(),
    )
    private val factory = SettingsViewFactory(
        context,
        themeEnvironment,
    )
    private var latestState = initialState
    private var terminalFontSize = initialTerminalFontSize
    private var terminalTheme = initialTerminalTheme
    private var languageBinding: SettingsViewFactory.NavigationBinding? = null
    private var restoreBinding: SettingsViewFactory.SwitchBinding? = null
    private var recentsBinding: SettingsViewFactory.SwitchBinding? = null
    private var notificationBinding: SettingsViewFactory.NavigationBinding? = null
    private var dropZoneBinding: SettingsViewFactory.NavigationBinding? = null
    private var terminalFontBinding: SettingsViewFactory.NavigationBinding? = null
    private var terminalThemeBinding: SettingsViewFactory.NavigationBinding? = null
    private var automationBinding: SettingsViewFactory.SwitchBinding? = null
    private var allFilesBinding: SettingsViewFactory.NavigationBinding? = null
    private var runtimeStatusBinding: SettingsViewFactory.InformationBinding? = null

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(factory.tokens.pageBackground)
        addView(factory.topBar(context.getString(spec.titleRes), onBack))
        addView(ScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(22), factory.dp(8), factory.dp(22), factory.dp(96))
                when (destination) {
                    SettingsCategoryDestination.AppearanceAndLanguage -> {
                        addRow(factory.navigationRow(
                            context.getString(R.string.settings_theme_title),
                            context.getString(R.string.settings_theme_summary),
                            onOpenTheme,
                        ).root, first = true)
                        languageBinding = factory.navigationRow(
                            context.getString(R.string.settings_language_title),
                            context.appLanguageLabel(initialState.appLanguage),
                        ) { factory.showLanguageDialog(latestState, onSelectAppLanguage) }
                        addRow(languageBinding!!.root)
                    }
                    SettingsCategoryDestination.AppBehavior -> {
                        restoreBinding = factory.switchRow(
                            context.getString(R.string.settings_restore_last_screen_title),
                            context.getString(R.string.settings_restore_last_screen_summary),
                            initialState.restoreLastScreen,
                            onRestoreLastScreen,
                        )
                        addRow(restoreBinding!!.root, first = true)
                        recentsBinding = factory.switchRow(
                            context.getString(R.string.settings_hide_recents_title),
                            context.getString(R.string.settings_hide_recents_summary),
                            initialState.hideMainTaskFromRecents,
                            onHideMainTask,
                        )
                        addRow(recentsBinding!!.root)
                    }
                    SettingsCategoryDestination.TerminalAndWorkbench -> {
                        terminalFontBinding = factory.navigationRow(
                            context.getString(R.string.settings_terminal_font_title),
                            terminalFontSummary(terminalFontSize),
                        ) {
                            val presets = TerminalUiPreferences.fontPresets()
                            factory.showTextChoiceDialog(
                                title = context.getString(R.string.settings_terminal_font_dialog_title),
                                summary = context.getString(R.string.settings_terminal_font_dialog_summary),
                                options = presets.map { "$it sp" },
                                selectedIndex = presets.indexOf(terminalFontSize).coerceAtLeast(0),
                            ) { index -> onSelectTerminalFontSize(presets[index]) }
                        }
                        addRow(terminalFontBinding!!.root, first = true)
                        terminalThemeBinding = factory.navigationRow(
                            context.getString(R.string.settings_terminal_theme_title),
                            terminalThemeSummary(terminalTheme),
                        ) {
                            val modes = TerminalThemeMode.entries
                            factory.showTextChoiceDialog(
                                title = context.getString(R.string.settings_terminal_theme_dialog_title),
                                summary = context.getString(R.string.settings_terminal_theme_dialog_summary),
                                options = modes.map { mode -> context.terminalThemeLabel(mode) },
                                selectedIndex = modes.indexOf(terminalTheme).coerceAtLeast(0),
                            ) { index -> onSelectTerminalTheme(modes[index]) }
                        }
                        addRow(terminalThemeBinding!!.root)
                    }
                    SettingsCategoryDestination.BrowserAndLogin -> {
                        addView(factory.informationCard(
                            context.getString(R.string.settings_browser_stable_title),
                            context.getString(R.string.settings_browser_stable_summary),
                        ))
                        addRow(factory.informationCard(
                            context.getString(R.string.settings_network_policy_title),
                            context.getString(R.string.settings_network_policy_summary),
                        ))
                        addRow(factory.navigationRow(
                            context.getString(R.string.settings_browser_experiment_entry_title),
                            context.getString(R.string.settings_browser_experiment_entry_summary),
                        ) { onOpenCategory(SettingsCategoryDestination.ExperimentalFeatures) }.root)
                    }
                    SettingsCategoryDestination.PermissionsAndFiles -> {
                        allFilesBinding = factory.navigationRow(
                            context.getString(R.string.settings_all_files_title),
                            allFilesSummary(initialRuntimeSnapshot),
                            onOpenAllFilesSettings,
                        )
                        addRow(allFilesBinding!!.root, first = true)
                        notificationBinding = factory.navigationRow(
                            context.getString(R.string.settings_notifications_title),
                            notificationSummary(initialState.notificationsEnabled),
                            onOpenNotificationSettings,
                        )
                        addRow(notificationBinding!!.root)
                        dropZoneBinding = factory.navigationRow(
                            context.getString(R.string.settings_drop_zone_title),
                            dropZoneSummary(initialState.dropZoneAvailable),
                            onOpenDropZone,
                        )
                        addRow(dropZoneBinding!!.root)
                    }
                    SettingsCategoryDestination.RuntimeEnvironment -> {
                        runtimeStatusBinding = factory.informationBinding(
                            context.getString(R.string.settings_runtime_status_title),
                            runtimeSummary(initialRuntimeSnapshot),
                        )
                        addRow(runtimeStatusBinding!!.root, first = true)
                        addRow(factory.navigationRow(
                            context.getString(R.string.settings_processes_title),
                            context.getString(R.string.settings_processes_summary),
                            onOpenProcesses,
                        ).root)
                        addRow(factory.informationCard(
                            context.getString(R.string.settings_toolchain_title),
                            context.getString(R.string.settings_toolchain_summary),
                        ))
                    }
                    SettingsCategoryDestination.ExperimentalFeatures -> {
                        automationBinding = factory.switchRow(
                            context.getString(R.string.browser_mode_automation_title),
                            automationSummary(initialState.browserRuntimeMode),
                            initialState.browserRuntimeMode == BrowserRuntimeMode.AutomationBrowser,
                        ) { enabled ->
                            onSelectBrowserMode(
                                if (enabled) BrowserRuntimeMode.AutomationBrowser
                                else BrowserRuntimeMode.WebViewWithSystemAuth,
                            )
                        }
                        addRow(automationBinding!!.root, first = true)
                        addRow(factory.informationCard(
                            context.getString(R.string.settings_experimental_warning_title),
                            context.getString(R.string.settings_experimental_warning_summary),
                        ))
                    }
                    SettingsCategoryDestination.HelpAndAbout -> {
                        addHelpAboutContent(
                            appInfo = appInfo,
                            onOpenLogs = onOpenLogs,
                            onOpenAboutPage = onOpenAboutPage,
                            onOpenExternal = onOpenExternal,
                        )
                    }
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun render(state: SettingsUiState) {
        latestState = state
        languageBinding?.bind(context.appLanguageLabel(state.appLanguage))
        restoreBinding?.bind(state.restoreLastScreen)
        recentsBinding?.bind(state.hideMainTaskFromRecents)
        notificationBinding?.bind(notificationSummary(state.notificationsEnabled))
        dropZoneBinding?.bind(dropZoneSummary(state.dropZoneAvailable))
        automationBinding?.bind(
            state.browserRuntimeMode == BrowserRuntimeMode.AutomationBrowser,
            automationSummary(state.browserRuntimeMode),
        )
    }

    fun renderRuntimeSnapshot(snapshot: RuntimeBootstrapSnapshot) {
        allFilesBinding?.bind(allFilesSummary(snapshot))
        runtimeStatusBinding?.subtitle?.text = runtimeSummary(snapshot)
    }

    fun renderTerminalPreferences(fontSize: Int, theme: TerminalThemeMode) {
        terminalFontSize = fontSize
        terminalTheme = theme
        terminalFontBinding?.bind(terminalFontSummary(fontSize))
        terminalThemeBinding?.bind(terminalThemeSummary(theme))
    }

    private fun LinearLayout.addRow(view: View, first: Boolean = false) {
        addView(view.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { if (!first) setMargins(0, factory.dp(12), 0, 0) }
        })
    }

    private fun LinearLayout.addHelpAboutContent(
        appInfo: SettingsAppInfo,
        onOpenLogs: () -> Unit,
        onOpenAboutPage: (SettingsAboutPage) -> Unit,
        onOpenExternal: (String) -> Unit,
    ) {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(factory.dp(2), factory.dp(4), factory.dp(2), factory.dp(14))
            addView(ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = context.getString(R.string.app_name)
            }, LinearLayout.LayoutParams(factory.dp(64), factory.dp(64)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(14), 0, 0, 0)
                addView(factory.textView(context.getString(R.string.app_name), UiTextRole.PageTitle))
                addView(factory.textView(
                    context.getString(
                        R.string.settings_about_version_value,
                        appInfo.versionName,
                        appInfo.versionCode,
                    ),
                    UiTextRole.Supporting,
                ).apply { setPadding(0, factory.dp(4), 0, 0) })
                addView(factory.textView(
                    context.getString(R.string.settings_about_description),
                    UiTextRole.Body,
                ).apply { setPadding(0, factory.dp(8), 0, 0) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })

        addAboutSectionTitle(context.getString(R.string.settings_about_project_section), first = true)
        addAboutRow(
            factory.navigationRowWithIcon(
                context.getString(R.string.settings_about_repository_title),
                context.getString(R.string.settings_about_repository_summary),
                R.drawable.ic_open_in_new,
            ) { onOpenExternal(PROJECT_REPOSITORY_URL) }.root,
            first = true,
        )
        addAboutRow(factory.navigationRowWithIcon(
            context.getString(R.string.settings_about_releases_title),
            context.getString(R.string.settings_about_releases_summary),
            R.drawable.ic_open_in_new,
        ) { onOpenExternal(PROJECT_RELEASES_URL) }.root)

        addAboutSectionTitle(context.getString(R.string.settings_about_support_section))
        addAboutRow(
            factory.navigationRowWithIcon(
                context.getString(R.string.settings_about_issues_title),
                context.getString(R.string.settings_about_issues_summary),
                R.drawable.ic_open_in_new,
            ) { onOpenExternal(PROJECT_ISSUES_URL) }.root,
            first = true,
        )
        addAboutRow(factory.navigationRowWithIcon(
            context.getString(R.string.settings_logs_title),
            context.getString(R.string.settings_logs_summary),
            R.drawable.ic_chevron_right_light,
            onOpenLogs,
        ).root)

        addAboutSectionTitle(context.getString(R.string.settings_about_legal_section))
        addAboutRow(
            factory.navigationRowWithIcon(
                context.getString(R.string.settings_about_license_title),
                context.getString(R.string.settings_about_license_summary),
                R.drawable.ic_chevron_right_light,
            ) { onOpenAboutPage(SettingsAboutPage.KiteLicense) }.root,
            first = true,
        )
        addAboutRow(factory.navigationRowWithIcon(
            context.getString(R.string.settings_about_open_source_title),
            context.getString(R.string.settings_about_open_source_summary),
            R.drawable.ic_chevron_right_light,
        ) { onOpenAboutPage(SettingsAboutPage.OpenSourceComponents) }.root)
        addAboutRow(factory.navigationRowWithIcon(
            context.getString(R.string.settings_about_diagnostics_title),
            context.getString(R.string.settings_about_diagnostics_summary),
            R.drawable.ic_chevron_right_light,
        ) { onOpenAboutPage(SettingsAboutPage.Diagnostics) }.root)
    }

    private fun LinearLayout.addAboutSectionTitle(title: String, first: Boolean = false) {
        addView(factory.sectionTitle(title).apply {
            setPadding(0, 0, 0, factory.dp(8))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { if (!first) setMargins(0, factory.dp(16), 0, 0) })
    }

    private fun LinearLayout.addAboutRow(view: View, first: Boolean = false) {
        addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { if (!first) setMargins(0, factory.dp(6), 0, 0) })
    }

    private fun notificationSummary(enabled: Boolean): String = context.getString(
        if (enabled) R.string.settings_notifications_enabled_summary
        else R.string.settings_notifications_disabled_summary,
    )

    private fun dropZoneSummary(available: Boolean): String = context.getString(
        if (available) R.string.settings_drop_zone_available_summary
        else R.string.settings_drop_zone_unavailable_summary,
    )

    private fun terminalFontSummary(fontSize: Int): String =
        context.getString(R.string.settings_terminal_font_summary, fontSize)

    private fun terminalThemeSummary(theme: TerminalThemeMode): String = context.getString(
        R.string.settings_terminal_theme_summary,
        context.terminalThemeLabel(theme),
    )

    private fun automationSummary(mode: BrowserRuntimeMode): String = context.getString(
        if (mode == BrowserRuntimeMode.AutomationBrowser) {
            R.string.settings_browser_automation_enabled_summary
        } else {
            R.string.settings_browser_automation_disabled_summary
        },
    )

    private fun allFilesSummary(snapshot: RuntimeBootstrapSnapshot): String = context.getString(
        if (snapshot.permissions.needsAllFilesAccess) {
            R.string.settings_all_files_disabled_summary
        } else {
            R.string.settings_all_files_enabled_summary
        },
    )

    private fun runtimeSummary(snapshot: RuntimeBootstrapSnapshot): String = context.getString(
        when {
            snapshot.bootstrapStage == RuntimeBootstrapStage.Failed ||
                snapshot.rootfs.phase == RuntimeRootfsPhase.Failed ||
                !snapshot.bootstrapError.isNullOrBlank() -> R.string.settings_runtime_failed_summary
            snapshot.defaultContainerReady && snapshot.baseImageReady ->
                R.string.settings_runtime_ready_summary
            snapshot.deployment.active || snapshot.rootfs.phase in setOf(
                RuntimeRootfsPhase.Preparing,
                RuntimeRootfsPhase.Extracting,
                RuntimeRootfsPhase.Verifying,
            ) || snapshot.bootstrapStage in setOf(
                RuntimeBootstrapStage.ServiceRequested,
                RuntimeBootstrapStage.RootfsExtracting,
                RuntimeBootstrapStage.BaseBootstrap,
                RuntimeBootstrapStage.SpaceReady,
            ) -> R.string.settings_runtime_preparing_summary
            else -> R.string.settings_runtime_not_ready_summary
        },
    )

    private companion object {
        const val PROJECT_REPOSITORY_URL = "https://github.com/Yokin-XY/Kite"
        const val PROJECT_RELEASES_URL = "$PROJECT_REPOSITORY_URL/releases/latest"
        const val PROJECT_ISSUES_URL = "$PROJECT_REPOSITORY_URL/issues"
    }
}
