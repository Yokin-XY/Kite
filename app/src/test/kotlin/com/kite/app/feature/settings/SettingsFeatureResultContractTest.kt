package com.kite.app.feature.settings

import android.os.Bundle
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.KiteThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsFeatureResultContractTest {
    @Test
    fun `theme request round-trips colors mode and style`() {
        val bundle = Bundle().apply {
            putString("kind", "apply_theme")
            putInt("theme_color", 0x112233)
            putInt("background_color", 0x445566)
            putString("theme_mode", "dark")
            putString("theme_style", "standard")
        }

        assertEquals(
            SettingsFeatureRequest.ApplyTheme(
                ThemeConfig(0x112233, 0x445566, KiteThemeMode.DARK, "standard")
            ),
            SettingsFeatureResultContract.parse(bundle)
        )
    }

    @Test
    fun `unknown request is ignored`() {
        assertNull(SettingsFeatureResultContract.parse(Bundle()))
    }

    @Test
    fun `设置分类目标通过类型化名称解析`() {
        val bundle = Bundle().apply {
            putString("kind", "open_category")
            putString("category", SettingsCategoryDestination.TerminalAndWorkbench.name)
        }

        assertEquals(
            SettingsFeatureRequest.OpenCategory(SettingsCategoryDestination.TerminalAndWorkbench),
            SettingsFeatureResultContract.parse(bundle),
        )
    }

    @Test
    fun `无效设置分类目标被拒绝`() {
        val bundle = Bundle().apply {
            putString("kind", "open_category")
            putString("category", "unknown")
        }

        assertNull(SettingsFeatureResultContract.parse(bundle))
    }

    @Test
    fun `系统与支持入口使用离散类型化请求`() {
        val expected = mapOf(
            "all_files" to SettingsFeatureRequest.OpenAllFilesSettings,
            "processes" to SettingsFeatureRequest.OpenProcesses,
            "logs" to SettingsFeatureRequest.OpenLogs,
        )

        expected.forEach { (kind, request) ->
            assertEquals(
                request,
                SettingsFeatureResultContract.parse(Bundle().apply { putString("kind", kind) }),
            )
        }
    }
}
