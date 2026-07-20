package com.kite.app.feature.settings

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapStage
import com.kite.app.application.runtimebootstrap.RuntimePermissionSnapshot
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

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
        SettingsCatalog.categories.forEach { category ->
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
    }

    @Test
    fun `帮助页显示真实传入版本和日志入口`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = SettingsCategoryScreen(
            context = activity,
            destination = SettingsCategoryDestination.HelpAndAbout,
            initialState = state(),
            appInfo = SettingsAppInfo("0.0.4", 4L),
            onBack = {},
        )

        val texts = screen.root.allTexts()
        assertTrue(texts.contains(activity.getString(R.string.settings_version_summary, "0.0.4", 4L)))
        assertTrue(texts.contains(activity.getString(R.string.settings_logs_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_diagnostics_scope_title)))
    }

    @Test
    fun `theme screen only rebuilds when theme identity changes`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = ThemeSettingsScreen(activity, {}, {}, {})
        val initial = state()

        screen.render(initial)
        val firstPage = screen.root.getChildAt(0)
        val texts = screen.root.allTexts()
        screen.render(initial.copy(revision = 2L))
        val samePage = screen.root.getChildAt(0)
        screen.render(initial.copy(
            theme = ThemeConfig(0x123456, initial.theme.backgroundColor),
            revision = 3L
        ))

        assertSame(firstPage, samePage)
        assertNotSame(firstPage, screen.root.getChildAt(0))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_mode_section)))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_mode_title)))
        assertTrue(texts.contains(activity.getString(R.string.settings_theme_style_standard)))
    }

    private fun state() = SettingsUiState(
        theme = ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor),
        appLanguage = AppLanguagePreference.System,
        browserRuntimeMode = BrowserRuntimeMode.Default,
        restoreLastScreen = true,
        hideMainTaskFromRecents = false,
        notificationsEnabled = false,
        dropZoneAvailable = false,
        revision = 1L
    )

    private fun View.allTexts(): List<String> = buildList {
        if (this@allTexts is TextView) add(text.toString())
        if (this@allTexts is ViewGroup) {
            repeat(childCount) { index -> addAll(getChildAt(index).allTexts()) }
        }
    }
}
