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
import com.kite.app.application.runtimemanagement.ProotEnvironmentOperation
import com.kite.app.application.runtimemanagement.ProotViewInspectionSnapshot
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
    initialProotViewSnapshot: ProotViewInspectionSnapshot = ProotViewInspectionSnapshot(),
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
    onOpenStartupReport: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenDropZone: () -> Unit = {},
    onOpenAboutPage: (SettingsAboutPage) -> Unit = {},
    onOpenExternal: (String) -> Unit = {},
    private val onRunViewAcceptance: () -> Unit = {},
    private val onRunViewVerification: () -> Unit = {},
    private val onCreateViewEnvironment: () -> Unit = {},
    private val onSwitchViewEnvironment: (String) -> Unit = {},
    private val onRunEnvironmentIsolationVerification: () -> Unit = {},
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
    private var prootViewSnapshot: ProotViewInspectionSnapshot = initialProotViewSnapshot
    private var prootViewAcceptanceBinding: SettingsViewFactory.InformationBinding? = null
    private var prootViewAcceptanceActionBinding: SettingsViewFactory.NavigationBinding? = null
    private var prootViewEnabledBinding: SettingsViewFactory.InformationBinding? = null
    private var prootViewCurrentBinding: SettingsViewFactory.InformationBinding? = null
    private var prootEnvironmentBinding: SettingsViewFactory.InformationBinding? = null
    private var prootEnvironmentCreateBinding: SettingsViewFactory.NavigationBinding? = null
    private var prootEnvironmentSwitchBinding: SettingsViewFactory.NavigationBinding? = null
    private var prootEnvironmentIsolationBinding: SettingsViewFactory.InformationBinding? = null
    private var prootEnvironmentIsolationActionBinding: SettingsViewFactory.NavigationBinding? = null
    private var prootViewStorageBinding: SettingsViewFactory.InformationBinding? = null
    private var prootViewScopeBinding: SettingsViewFactory.InformationBinding? = null
    private var prootViewVerificationBinding: SettingsViewFactory.InformationBinding? = null
    private var prootViewVerificationActionBinding: SettingsViewFactory.NavigationBinding? = null

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
                            context.getString(R.string.settings_startup_report_title),
                            context.getString(R.string.settings_startup_report_summary),
                            onOpenStartupReport,
                        ).root)
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
                    SettingsCategoryDestination.Engineering -> {
                        prootViewAcceptanceBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_acceptance_title),
                            prootViewAcceptanceSummary(),
                        ).also { addView(it.root) }
                        prootViewAcceptanceActionBinding = factory.navigationRow(
                            context.getString(R.string.settings_engineering_acceptance_action_title),
                            context.getString(R.string.settings_engineering_acceptance_action_summary),
                            onRunViewAcceptance,
                        ).also { addView(it.root) }
                        prootViewEnabledBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_view_enabled_title),
                            prootViewEnabledSummary(),
                        ).also { addView(it.root) }
                        prootViewCurrentBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_view_current_title),
                            prootViewCurrentSummary(),
                        ).also { addView(it.root) }
                        prootEnvironmentBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_environments_title),
                            prootEnvironmentSummary(),
                        ).also { addView(it.root) }
                        prootEnvironmentCreateBinding = factory.navigationRow(
                            context.getString(R.string.settings_engineering_environment_create_title),
                            context.getString(R.string.settings_engineering_environment_create_summary),
                            onCreateViewEnvironment,
                        ).also { addView(it.root) }
                        prootEnvironmentSwitchBinding = factory.navigationRow(
                            context.getString(R.string.settings_engineering_environment_switch_title),
                            prootEnvironmentSwitchSummary(),
                        ) {
                            showEnvironmentChoice()
                        }.also { addView(it.root) }
                        prootEnvironmentIsolationBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_environment_isolation_title),
                            prootEnvironmentIsolationSummary(),
                        ).also { addView(it.root) }
                        prootEnvironmentIsolationActionBinding = factory.navigationRow(
                            context.getString(R.string.settings_engineering_environment_isolation_action_title),
                            context.getString(R.string.settings_engineering_environment_isolation_action_summary),
                            onRunEnvironmentIsolationVerification,
                        ).also { addView(it.root) }
                        prootViewStorageBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_view_storage_title),
                            prootViewStorageSummary(),
                        ).also { addView(it.root) }
                        prootViewScopeBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_view_scope_title),
                            prootViewScopeSummary(),
                        ).also { addView(it.root) }
                        prootViewVerificationBinding = factory.informationBinding(
                            context.getString(R.string.settings_engineering_view_verification_title),
                            prootViewVerificationSummary(),
                        ).also { addView(it.root) }
                        prootViewVerificationActionBinding = factory.navigationRow(
                            context.getString(R.string.settings_engineering_run_verification_title),
                            context.getString(R.string.settings_engineering_run_verification_summary),
                            onRunViewVerification,
                        ).also { addView(it.root) }
                        updateEngineeringActionState()
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

    fun renderProotViewSnapshot(snapshot: ProotViewInspectionSnapshot) {
        prootViewSnapshot = snapshot
        prootViewAcceptanceBinding?.subtitle?.text = prootViewAcceptanceSummary()
        prootViewEnabledBinding?.subtitle?.text = prootViewEnabledSummary()
        prootViewCurrentBinding?.subtitle?.text = prootViewCurrentSummary()
        prootEnvironmentBinding?.subtitle?.text = prootEnvironmentSummary()
        prootEnvironmentSwitchBinding?.bind(prootEnvironmentSwitchSummary())
        prootEnvironmentIsolationBinding?.subtitle?.text = prootEnvironmentIsolationSummary()
        prootViewStorageBinding?.subtitle?.text = prootViewStorageSummary()
        prootViewScopeBinding?.subtitle?.text = prootViewScopeSummary()
        prootViewVerificationBinding?.subtitle?.text = prootViewVerificationSummary()
        updateEngineeringActionState()
    }

    private fun prootViewEnabledSummary(): String {
        val s = prootViewSnapshot
        return buildString {
            append("View 启用：").append(if (s.enabled) "是" else "否")
            append('\n').append("Base 封存：").append(if (s.baseSealed) "是" else "否")
            append('\n').append("运行时能力：").append(if (s.runtimeSupported) "完整" else "缺失")
            if (!s.available) append('\n').append("（容器未就绪或 View 不可用）")
        }
    }

    private fun prootViewCurrentSummary(): String {
        val s = prootViewSnapshot
        return buildString {
            append("环境：").append(s.environmentId.ifBlank { "-" })
            append('\n').append("Space：").append(s.spaceId.ifBlank { "-" })
            append('\n').append("工作区：").append(s.workspacePath.ifBlank { "-" })
            append('\n').append("current viewId：").append(s.currentViewId.ifBlank { "-" })
            append('\n').append("父层深度：").append(s.parentDepth)
        }
    }

    private fun prootEnvironmentSummary(): String {
        val snapshot = prootViewSnapshot
        if (snapshot.environments.isEmpty()) {
            return context.getString(R.string.settings_engineering_environments_empty)
        }
        return buildString {
            when (snapshot.environmentOperation) {
                ProotEnvironmentOperation.Creating -> append(
                    context.getString(R.string.settings_engineering_environment_creating),
                )
                ProotEnvironmentOperation.Switching -> append(
                    context.getString(
                        R.string.settings_engineering_environment_switching,
                        snapshot.environmentOperationTarget.ifBlank { "-" },
                    ),
                )
                ProotEnvironmentOperation.VerifyingAcceptance -> append(
                    context.getString(R.string.settings_engineering_acceptance_running),
                )
                ProotEnvironmentOperation.VerifyingIsolation -> append(
                    context.getString(R.string.settings_engineering_environment_isolation_running),
                )
                ProotEnvironmentOperation.Idle -> when {
                    snapshot.environmentOperationError.isNotBlank() -> append(
                        context.getString(
                            R.string.settings_engineering_environment_operation_failed,
                            snapshot.environmentOperationError,
                        ),
                    )
                    snapshot.environmentOperationTarget.isNotBlank() -> append(
                        context.getString(
                            R.string.settings_engineering_environment_operation_completed,
                            snapshot.environmentOperationTarget,
                        ),
                    )
                    else -> append(
                        context.getString(
                            R.string.settings_engineering_environment_count,
                            snapshot.environments.size,
                        ),
                    )
                }
            }
            snapshot.environments.forEach { environment ->
                append('\n').append(
                    context.getString(
                        if (environment.active) R.string.settings_engineering_environment_active_row
                        else R.string.settings_engineering_environment_inactive_row,
                        environment.environmentId,
                        environment.viewId,
                    ),
                )
                append('\n').append(environment.workspacePath)
            }
        }
    }

    private fun prootEnvironmentSwitchSummary(): String = context.getString(
        R.string.settings_engineering_environment_switch_summary,
        prootViewSnapshot.environmentId.ifBlank { "-" },
        prootViewSnapshot.environments.size,
    )

    private fun prootEnvironmentIsolationSummary(): String {
        val result = prootViewSnapshot.lastIsolationVerification
        return if (result == null) {
            context.getString(R.string.settings_engineering_environment_isolation_not_run)
        } else buildString {
            append(context.getString(
                if (result.success) R.string.settings_engineering_environment_isolation_success
                else R.string.settings_engineering_environment_isolation_failed,
            ))
            append('\n').append(context.getString(
                R.string.settings_engineering_environment_isolation_pair,
                result.firstEnvironmentId.ifBlank { "-" },
                result.secondEnvironmentId.ifBlank { "-" },
            ))
            append('\n').append(context.getString(
                R.string.settings_engineering_environment_isolation_evidence,
                passLabel(result.rootIsolated),
                passLabel(result.workspaceIsolated),
                passLabel(result.exchangeShared),
                passLabel(result.baseUntouched),
                passLabel(result.originalEnvironmentRestored),
            ))
            if (result.message.isNotBlank()) append('\n').append(result.message)
        }
    }

    private fun passLabel(success: Boolean): String = context.getString(
        if (success) R.string.settings_engineering_check_pass else R.string.settings_engineering_check_fail,
    )

    private fun prootViewAcceptanceSummary(): String {
        if (prootViewSnapshot.environmentOperation == ProotEnvironmentOperation.VerifyingAcceptance) {
            return context.getString(R.string.settings_engineering_acceptance_running)
        }
        val result = prootViewSnapshot.lastAcceptance
            ?: return context.getString(R.string.settings_engineering_acceptance_not_run)
        val passed = result.checks.count { it.passed }
        return buildString {
            append(context.getString(
                if (result.success) R.string.settings_engineering_acceptance_success
                else R.string.settings_engineering_acceptance_failed,
                passed,
                result.checks.size,
                result.totalMs,
            ))
            if (result.environmentId.isNotBlank()) {
                append('\n').append("环境：").append(result.environmentId)
                append(" · View：").append(result.viewId.ifBlank { "-" })
            }
            result.checks.forEach { check ->
                append('\n')
                append(if (check.passed) "✓ " else "✕ ")
                append(check.title)
                if (check.detail.isNotBlank()) append("：").append(check.detail)
            }
        }
    }

    private fun showEnvironmentChoice() {
        val environments = prootViewSnapshot.environments
        if (environments.size < 2 || prootViewSnapshot.environmentOperation != ProotEnvironmentOperation.Idle) {
            return
        }
        factory.showTextChoiceDialog(
            title = context.getString(R.string.settings_engineering_environment_switch_dialog_title),
            summary = context.getString(R.string.settings_engineering_environment_switch_dialog_summary),
            options = environments.map { environment ->
                context.getString(
                    if (environment.active) R.string.settings_engineering_environment_choice_active
                    else R.string.settings_engineering_environment_choice,
                    environment.environmentId,
                )
            },
            selectedIndex = environments.indexOfFirst { it.active }.coerceAtLeast(0),
        ) { index -> onSwitchViewEnvironment(environments[index].environmentId) }
    }

    private fun updateEngineeringActionState() {
        val busy = prootViewSnapshot.environmentOperation != ProotEnvironmentOperation.Idle
        setActionEnabled(prootViewAcceptanceActionBinding?.root, !busy && prootViewSnapshot.available)
        setActionEnabled(prootEnvironmentCreateBinding?.root, !busy && prootViewSnapshot.available)
        setActionEnabled(
            prootEnvironmentSwitchBinding?.root,
            !busy && prootViewSnapshot.available && prootViewSnapshot.environments.size > 1,
        )
        setActionEnabled(prootViewVerificationActionBinding?.root, !busy && prootViewSnapshot.available)
        setActionEnabled(
            prootEnvironmentIsolationActionBinding?.root,
            !busy && prootViewSnapshot.available,
        )
    }

    private fun setActionEnabled(view: View?, enabled: Boolean) {
        view ?: return
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.55f
    }

    private fun prootViewStorageSummary(): String {
        val s = prootViewSnapshot
        val allocated = s.upperAllocatedBytes
        return buildString {
            append("Upper 逻辑字节：").append(s.upperLogicalBytes)
            append('\n').append("Upper 分配字节：").append(allocated ?: "未知")
        }
    }

    private fun prootViewScopeSummary(): String {
        val scopes = prootViewSnapshot.scopeRootPaths
        return if (scopes.isEmpty()) "受管范围：-" else buildString {
            append("受管范围：")
            scopes.forEachIndexed { index, scope ->
                if (index > 0) append('\n')
                append("• ").append(scope)
            }
        }
    }

    private fun prootViewVerificationSummary(): String {
        val result = prootViewSnapshot.lastVerification
        return if (result == null) {
            "最近验证：尚未运行"
        } else {
            buildString {
                append("最近验证：").append(if (result.success) "成功" else "失败")
                append('\n').append("runCount：").append(result.runCount)
                append('\n').append("viewId：").append(result.viewId.ifBlank { "-" })
                if (result.fileSha256.isNotBlank()) {
                    append('\n').append("SHA-256：").append(result.fileSha256)
                }
                if (result.message.isNotBlank()) append('\n').append(result.message)
            }
        }
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
