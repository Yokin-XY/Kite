package com.kite.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    @Test
    fun `设置目录必须覆盖全部类型化目标且 id 唯一`() {
        val categories = SettingsCatalog.categories

        assertEquals(SettingsCategoryDestination.entries.toSet(), categories.map { it.destination }.toSet())
        assertEquals(categories.size, categories.map { it.id }.toSet().size)
        assertTrue(categories.all { it.id.isNotBlank() })
        assertTrue(categories.all { it.titleRes != 0 && it.summaryRes != 0 })
        assertTrue(categories.all { it.kind == SettingsEntryKind.Navigation })
    }

    @Test
    fun `稳定首页分组顺序保持用户目标层级`() {
        assertEquals(
            listOf(
                SettingsSection.Personalization,
                SettingsSection.Usage,
                SettingsSection.Usage,
                SettingsSection.Usage,
                SettingsSection.System,
                SettingsSection.System,
                SettingsSection.Other,
                SettingsSection.Other,
            ),
            SettingsCatalog.categories.map { it.section },
        )
    }

    @Test
    fun `实验入口在正式包中可见但必须携带实验成熟度`() {
        val releaseCategories = SettingsCatalog.visibleCategories(isDebugBuild = false)
        val experimental = releaseCategories.single {
            it.destination == SettingsCategoryDestination.ExperimentalFeatures
        }

        assertEquals(SettingsMaturity.Experimental, experimental.maturity)
        assertTrue(SettingsVisibilityPolicy.isVisible(SettingsMaturity.Experimental, isDebugBuild = false))
    }

    @Test
    fun `Debug 入口不得进入正式包`() {
        assertFalse(SettingsVisibilityPolicy.isVisible(SettingsMaturity.DebugOnly, isDebugBuild = false))
        assertTrue(SettingsVisibilityPolicy.isVisible(SettingsMaturity.DebugOnly, isDebugBuild = true))
        assertTrue(SettingsVisibilityPolicy.isVisible(SettingsMaturity.Stable, isDebugBuild = false))
    }
}
