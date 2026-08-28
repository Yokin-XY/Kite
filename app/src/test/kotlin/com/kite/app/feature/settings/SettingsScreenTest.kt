package com.kite.app.feature.settings

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapStage
import com.kite.app.application.runtimebootstrap.RuntimePermissionSnapshot
import com.kite.app.application.runtimemanagement.ProotEnvironmentInspection
import com.kite.app.application.runtimemanagement.ProotEnvironmentOperation
import com.kite.app.application.runtimemanagement.ProotViewAcceptanceCheck
import com.kite.app.application.runtimemanagement.ProotViewAcceptanceResult
import com.kite.app.application.runtimemanagement.ProotViewInspectionSnapshot
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeColorSeed
import com.kite.app.theme.ThemeCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @Test
    fun `设置首页按稳定目录分组且普通状态投影不重建页面`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val opened = mutableListOf<SettingsCategoryDestination>()
        val screen = SettingsScreen(
            context = activity,
            initialState = state(),
            onBack = {},
            onOpenCategory = opened::add,
        )
        activity.setContentView(screen.root)
        val firstChild = (screen.root as ViewGroup).getChildAt(0)

        screen.render(state().copy(revision = 2L))

        assertSame(firstChild, (screen.root as ViewGroup).getChildAt(0))
        val texts = screen.root.allTexts()
        assertTrue(texts.contains(activity.getString(R.string.settings_section_personalization)))
        assertTrue(texts.contains(activity.getString(R.string.settings_section_usage)))
        assertTrue(texts.contains(activity.getString(R.string.settings_section_system)))
        assertTrue(texts.contains(activity.getString(R.string.settings_section_other)))
        val isDebugBuild = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        SettingsCatalog.visibleCategories(isDebugBuild).forEach { category ->
            assertTrue(texts.contains(activity.getString(category.titleRes)))
            assertTrue(texts.contains(activity.getString(category.summaryRes)))
        }
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `设置首页重建时恢复离开前的目录滚动位置`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = SettingsScreen(
            context = activity,
            initialState = state(),
            initialScrollY = 420,
            onBack = {},
            onOpenCategory = {},
        )
        activity.setContentView(screen.root)
        screen.root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
        )
        screen.root.layout(0, 0, 1080, 900)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(420, screen.currentScrollY())
    }

    @Test
    fun `设置二级页使用目录标题且状态变化不重建骨架`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.RuntimeEnvironment,
            initialState = state(),
            onBack = {},
        )
        activity.setContentView(screen.root)
        val firstChild = (screen.root as ViewGroup).getChildAt(0)

        screen.render(state().copy(revision = 2L))

        assertSame(firstChild, (screen.root as ViewGroup).getChildAt(0))
        val texts = screen.root.allTexts()
        assertTrue(texts.contains(activity.getString(R.string.settings_category_runtime_environment_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_runtime_not_ready_summary)))
    }

    @Test
    fun `旧设置迁移后继续从同一状态投影且不触发用户回调`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var callbacks = 0
        val browser = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.BrowserAndLogin,
            initialState = state(),
            onBack = {},
            onSelectBrowserMode = { callbacks += 1 },
        )
        val permissions = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.PermissionsAndFiles,
            initialState = state(),
            onBack = {},
            onOpenNotificationSettings = { callbacks += 1 },
            onOpenDropZone = { callbacks += 1 },
        )
        val appearance = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.AppearanceAndLanguage,
            initialState = state(),
            onBack = {},
            onSelectAppLanguage = { callbacks += 1 },
        )
        val experiments = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.ExperimentalFeatures,
            initialState = state(),
            onBack = {},
            onSelectBrowserMode = { callbacks += 1 },
        )

        val next = state().copy(
            browserRuntimeMode = BrowserRuntimeMode.AutomationBrowser,
            appLanguage = AppLanguagePreference.English,
            notificationsEnabled = true,
            dropZoneAvailable = true,
            revision = 2L,
        )
        browser.render(next)
        permissions.render(next)
        appearance.render(next)
        experiments.render(next)

        assertEquals(0, callbacks)
        assertTrue(browser.root.allTexts().contains(activity.getString(R.string.settings_browser_stable_title)))
        assertTrue(browser.root.allTexts().contains(activity.getString(R.string.settings_network_policy_title)))
        assertTrue(experiments.root.allTexts().contains(activity.getString(R.string.settings_browser_automation_enabled_summary)))
        assertTrue(appearance.root.allTexts().contains(activity.getString(R.string.settings_language_english)))
        assertTrue(permissions.root.allTexts().contains(activity.getString(R.string.settings_notifications_enabled_summary)))
        assertTrue(permissions.root.allTexts().contains(activity.getString(R.string.settings_drop_zone_available_summary)))
    }

    @Test
    fun `终端设置页显示与终端现场共用的默认值`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.TerminalAndWorkbench,
            initialState = state(),
            initialTerminalFontSize = 35,
            initialTerminalTheme = com.kite.app.ui.terminal.TerminalThemeMode.SYSTEM,
            onBack = {},
        )

        screen.renderTerminalPreferences(
            50,
            com.kite.app.ui.terminal.TerminalThemeMode.DARK,
        )

        val texts = screen.root.allTexts()
        assertTrue(texts.contains(activity.getString(R.string.settings_terminal_font_summary, 50)))
        assertTrue(texts.contains(activity.getString(
            R.string.settings_terminal_theme_summary,
            activity.getString(R.string.terminal_theme_dark),
        )))
    }

    @Test
    fun `权限和运行环境只投影 RuntimeBootstrap 事实`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var reportOpenCount = 0
        val missingFiles = RuntimeBootstrapSnapshot(
            permissions = RuntimePermissionSnapshot(needsAllFilesAccess = true),
        )
        val permissions = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.PermissionsAndFiles,
            initialState = state(),
            initialRuntimeSnapshot = missingFiles,
            onBack = {},
        )
        val runtime = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.RuntimeEnvironment,
            initialState = state(),
            initialRuntimeSnapshot = missingFiles,
            onBack = {},
            onOpenStartupReport = { reportOpenCount += 1 },
        )

        val ready = RuntimeBootstrapSnapshot(
            permissions = RuntimePermissionSnapshot(needsAllFilesAccess = false),
            bootstrapStage = RuntimeBootstrapStage.Ready,
            baseImageReady = true,
            defaultContainerReady = true,
        )
        permissions.renderRuntimeSnapshot(ready)
        runtime.renderRuntimeSnapshot(ready)

        assertTrue(permissions.root.allTexts().contains(activity.getString(R.string.settings_all_files_enabled_summary)))
        assertTrue(runtime.root.allTexts().contains(activity.getString(R.string.settings_runtime_ready_summary)))
        assertTrue(runtime.root.allTexts().contains(activity.getString(R.string.settings_processes_title)))
        assertTrue(runtime.root.allTexts().contains(activity.getString(R.string.settings_toolchain_title)))
        assertTrue(runtime.root.allTexts().contains(activity.getString(R.string.settings_startup_report_title)))
        runtime.root.findTextView(activity.getString(R.string.settings_startup_report_title))
            ?.clickableAncestor()
            ?.performClick()
        assertEquals(1, reportOpenCount)
    }

    @Test
    fun `工程页局部投影环境列表并把创建与切换意图交给网关`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var createCount = 0
        val switched = mutableListOf<String>()
        val initial = engineeringSnapshot()
        val screen = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.Engineering,
            initialState = state(),
            initialProotViewSnapshot = initial,
            onBack = {},
            onCreateViewEnvironment = { createCount += 1 },
            onSwitchViewEnvironment = switched::add,
        )
        activity.setContentView(screen.root)
        val firstChild = (screen.root as ViewGroup).getChildAt(0)

        val texts = screen.root.allTexts()
        assertTrue(texts.contains(activity.getString(R.string.settings_engineering_environments_title)))
        assertTrue(texts.any { it.contains("default") && it.contains("view-default") })
        assertTrue(texts.any { it.contains("profile_2") && it.contains("view-profile") })
        assertTrue(texts.any { it.contains("space-main") && it.contains("/workspace/default") })

        screen.root.findTextView(activity.getString(R.string.settings_engineering_environment_create_title))
            ?.clickableAncestor()
            ?.performClick()
        assertEquals(1, createCount)

        screen.root.findTextView(activity.getString(R.string.settings_engineering_environment_switch_title))
            ?.clickableAncestor()
            ?.performClick()
        val dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<ViewGroup>(android.R.id.content)
            .findTextView(activity.getString(R.string.settings_engineering_environment_choice, "profile_2"))
            ?.clickableAncestor()
            ?.performClick()
        assertEquals(listOf("profile_2"), switched)

        screen.renderProotViewSnapshot(initial.copy(
            environmentOperation = ProotEnvironmentOperation.Switching,
            environmentOperationTarget = "profile_2",
        ))
        assertSame(firstChild, (screen.root as ViewGroup).getChildAt(0))
        assertTrue(screen.root.allTexts().any { it.contains("profile_2") && it.contains("…") })
        assertFalse(
            screen.root.findTextView(activity.getString(R.string.settings_engineering_environment_create_title))
                ?.clickableAncestor()
                ?.isEnabled ?: true,
        )
    }

    @Test
    fun `工程验收台一键提交通用验收意图并局部呈现逐项结果`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var acceptanceCount = 0
        val screen = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.Engineering,
            initialState = state(),
            initialProotViewSnapshot = engineeringSnapshot(),
            onBack = {},
            onRunViewAcceptance = { acceptanceCount += 1 },
        )
        activity.setContentView(screen.root)
        val topBar = (screen.root as ViewGroup).getChildAt(0)

        screen.root.findTextView(activity.getString(R.string.settings_engineering_acceptance_action_title))
            ?.clickableAncestor()
            ?.performClick()
        assertEquals(1, acceptanceCount)

        screen.renderProotViewSnapshot(engineeringSnapshot().copy(
            environmentOperation = ProotEnvironmentOperation.VerifyingAcceptance,
        ))
        assertSame(topBar, (screen.root as ViewGroup).getChildAt(0))
        assertTrue(screen.root.allTexts().contains(
            activity.getString(R.string.settings_engineering_acceptance_running),
        ))
        assertFalse(
            screen.root.findTextView(activity.getString(R.string.settings_engineering_acceptance_action_title))
                ?.clickableAncestor()
                ?.isEnabled ?: true,
        )

        screen.renderProotViewSnapshot(engineeringSnapshot().copy(
            lastAcceptance = ProotViewAcceptanceResult(
                checks = listOf(
                    ProotViewAcceptanceCheck("ordinary_view", "普通启动", true, "default"),
                    ProotViewAcceptanceCheck("base_immutable", "Base 未污染", false, "发现变化"),
                ),
                environmentId = "default",
                viewId = "view-default",
                totalMs = 88L,
            ),
        ))
        val texts = screen.root.allTexts()
        assertTrue(texts.any { it.contains("1/2") && it.contains("88ms") })
        assertTrue(texts.any { it.contains("✓ 普通启动") && it.contains("✕ Base 未污染") })
    }

    @Test
    fun `帮助页显示真实版本项目信息和声明入口`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.HelpAndAbout,
            initialState = state(),
            appInfo = SettingsAppInfo("0.0.4", 4L),
            onBack = {},
        )

        val texts = screen.root.allTexts()
        assertTrue(texts.contains(activity.getString(R.string.app_name)))
        assertTrue(texts.contains(activity.getString(R.string.settings_about_version_value, "0.0.4", 4L)))
        assertTrue(texts.contains(activity.getString(R.string.settings_about_repository_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_logs_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_about_open_source_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_about_diagnostics_title)))
    }

    @Test
    fun `开源组件页使用声明排布并可进入完整声明`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = SettingsAboutDetailScreen(
            context = activity,
            initialPage = SettingsAboutPage.OpenSourceComponents,
            initialState = state(),
            onBack = {},
        )

        assertTrue(screen.root.allTexts().contains("Ubuntu"))
        assertTrue(screen.root.allTexts().contains("AndroidX"))
        assertTrue(screen.root.allTexts().contains(activity.getString(R.string.settings_about_full_notices_action)))

        screen.root.findTextView(activity.getString(R.string.settings_about_full_notices_action))
            ?.performClick()

        assertEquals(SettingsAboutPage.FullThirdPartyNotices, screen.currentPage)
        assertTrue(screen.root.allTexts().contains(activity.getString(R.string.settings_about_full_notices_title)))
    }

    @Test
    fun `theme screen only rebuilds when theme identity changes`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = ThemeSettingsScreen(activity, {}, {})
        val initial = state()

        screen.render(initial)
        val firstPage = screen.root.getChildAt(0)
        val texts = screen.root.allTexts()
        screen.render(initial.copy(revision = 2L))
        val samePage = screen.root.getChildAt(0)
        screen.render(initial.copy(
            theme = KiteTheme.apply(
                initial.theme,
                ThemeCommand.SetCustomColors(
                    ThemeColorSeed(0x123456, KiteTheme.defaultBackgroundColor)
                ),
            ),
            revision = 3L
        ))

        assertSame(firstPage, samePage)
        assertNotSame(firstPage, screen.root.getChildAt(0))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_mode_section)))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_mode_title)))
        assertFalse(texts.contains(activity.getString(R.string.settings_theme_style_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_color_section)))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_color_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_color_chatgpt)))
    }

    private fun state() = SettingsUiState(
        theme = KiteTheme.defaultSelection,
        appLanguage = AppLanguagePreference.System,
        browserRuntimeMode = BrowserRuntimeMode.Default,
        restoreLastScreen = true,
        hideMainTaskFromRecents = false,
        notificationsEnabled = false,
        dropZoneAvailable = false,
        revision = 1L
    )

    private fun engineeringSnapshot() = ProotViewInspectionSnapshot(
        available = true,
        enabled = true,
        runtimeSupported = true,
        containerReady = true,
        currentViewId = "view-default",
        environmentId = "default",
        spaceId = "space-main",
        workspacePath = "/workspace/default",
        environments = listOf(
            ProotEnvironmentInspection(
                environmentId = "default",
                viewId = "view-default",
                active = true,
                parentDepth = 0,
                workspacePath = "/workspace/default",
            ),
            ProotEnvironmentInspection(
                environmentId = "profile_2",
                viewId = "view-profile",
                active = false,
                parentDepth = 0,
                workspacePath = "/workspace/profile_2",
            ),
        ),
    )

    private fun View.allTexts(): List<String> = buildList {
        if (this@allTexts is TextView) add(text.toString())
        if (this@allTexts is ViewGroup) {
            repeat(childCount) { index -> addAll(getChildAt(index).allTexts()) }
        }
    }

    private fun View.findTextView(value: String): TextView? {
        if (this is TextView && text.toString() == value) return this
        if (this is ViewGroup) {
            repeat(childCount) { index ->
                getChildAt(index).findTextView(value)?.let { return it }
            }
        }
        return null
    }

    private fun View.clickableAncestor(): View? {
        var current: View? = this
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent as? View
        }
        return null
    }
}
