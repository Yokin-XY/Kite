package com.kite.app.feature.settings

import com.kite.app.application.settings.SettingsCommand
import com.kite.app.application.settings.SettingsDropZoneSnapshot
import com.kite.app.application.settings.SettingsGateway
import com.kite.app.application.settings.SettingsSnapshot
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.resources.KiteResourceSourceCatalog
import com.kite.app.theme.KiteThemeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeColorSeed
import com.kite.app.theme.ThemeColorSelection
import com.kite.app.theme.ThemeCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFeatureControllerTest {
    @Test
    fun `projector gives system facts user-facing semantics`() {
        val state = SettingsProjector.project(snapshot().copy(
            notificationsEnabled = false,
            dropZone = SettingsDropZoneSnapshot(true)
        ))

        assertFalse(state.notificationsEnabled)
        assertTrue(state.dropZoneAvailable)
        assertEquals(AppLanguagePreference.System, state.appLanguage)
    }

    @Test
    fun `setting commands update single gateway and emit only shell side effects`() = runTest {
        val gateway = FakeGateway()
        val controller = SettingsFeatureController(gateway, backgroundScope)

        assertNull(controller.dispatch(SettingsFeatureAction.SetRestoreLastScreen(false)))
        val recents = controller.dispatch(SettingsFeatureAction.SetHideMainTaskFromRecents(true))
        val theme = controller.dispatch(SettingsFeatureAction.UpdateTheme(
            ThemeCommand.SetCustomColors(ThemeColorSeed(0x123456, 0xF4F6F8))
        ))
        val themeMode = controller.dispatch(SettingsFeatureAction.UpdateTheme(
            ThemeCommand.SetMode(KiteThemeMode.DARK)
        ))
        val language = controller.dispatch(
            SettingsFeatureAction.SelectAppLanguage(AppLanguagePreference.English)
        )
        val browser = controller.dispatch(
            SettingsFeatureAction.SelectBrowserMode(BrowserRuntimeMode.AutomationBrowser)
        )
        assertNull(controller.dispatch(
            SettingsFeatureAction.SetResourceSourceOrder(
                listOf(KiteResourceSourceCatalog.OFFICIAL, KiteResourceSourceCatalog.HUAWEI),
            )
        ))

        assertFalse(gateway.currentSnapshot().restoreLastScreen)
        assertTrue(gateway.currentSnapshot().hideMainTaskFromRecents)
        assertEquals(SettingsFeatureEffect.RecentTaskVisibilityChanged, recents)
        assertEquals(
            ThemeColorSelection.Custom(ThemeColorSeed(0x123456, 0xF4F6F8)),
            (theme as SettingsFeatureEffect.ThemeChanged).theme.colors,
        )
        assertEquals(KiteThemeMode.DARK, (themeMode as SettingsFeatureEffect.ThemeChanged).theme.mode)
        assertEquals(
            AppLanguagePreference.English,
            (language as SettingsFeatureEffect.AppLanguageChanged).language
        )
        assertEquals(AppLanguagePreference.English, gateway.currentSnapshot().appLanguage)
        assertEquals(
            BrowserRuntimeMode.AutomationBrowser,
            (browser as SettingsFeatureEffect.BrowserModeChanged).mode
        )
        assertEquals(
            KiteResourceSourceCatalog.OFFICIAL,
            gateway.currentSnapshot().resourceSourcePreferences.orderedSourceIds.first(),
        )
    }

    @Test
    fun `refresh updates system snapshot without creating navigation effect`() = runTest {
        val gateway = FakeGateway()
        gateway.refreshed = snapshot().copy(
            notificationsEnabled = true,
            dropZone = SettingsDropZoneSnapshot(true)
        )
        val controller = SettingsFeatureController(gateway, backgroundScope)

        assertNull(controller.dispatch(SettingsFeatureAction.Refresh))

        assertTrue(gateway.currentSnapshot().notificationsEnabled)
        assertTrue(gateway.currentSnapshot().dropZone.available)
    }

    private class FakeGateway : SettingsGateway {
        private val mutable = MutableStateFlow(snapshot())
        var refreshed: SettingsSnapshot = mutable.value

        override val snapshots: StateFlow<SettingsSnapshot> = mutable

        override fun currentSnapshot(): SettingsSnapshot = mutable.value

        override suspend fun refresh(): SettingsSnapshot = refreshed.copy(
            revision = mutable.value.revision + 1L
        ).also { mutable.value = it }

        override fun update(command: SettingsCommand): SettingsSnapshot {
            val current = mutable.value
            val next = when (command) {
                is SettingsCommand.UpdateTheme -> current.copy(
                    themeSelection = KiteTheme.apply(current.themeSelection, command.command)
                )
                is SettingsCommand.SetAppLanguage -> current.copy(appLanguage = command.language)
                is SettingsCommand.SetBrowserRuntimeMode -> current.copy(browserRuntimeMode = command.mode)
                is SettingsCommand.SetResourceSourceOrder -> current.copy(
                    resourceSourcePreferences = com.kite.app.resources.KiteResourceSourcePreferences(
                        command.sourceIds,
                    ).normalized(),
                )
                is SettingsCommand.SetRestoreLastScreen -> current.copy(restoreLastScreen = command.enabled)
                is SettingsCommand.SetHideMainTaskFromRecents -> current.copy(
                    hideMainTaskFromRecents = command.enabled
                )
            }.copy(revision = current.revision + 1L)
            mutable.value = next
            return next
        }
    }

    private companion object {
        fun snapshot() = SettingsSnapshot(
            appLanguage = AppLanguagePreference.System,
            browserRuntimeMode = BrowserRuntimeMode.Default,
            restoreLastScreen = true,
            hideMainTaskFromRecents = false,
            notificationsEnabled = false,
            dropZone = SettingsDropZoneSnapshot(),
            revision = 1L,
            themeSelection = KiteTheme.apply(
                KiteTheme.defaultSelection,
                ThemeCommand.SetCustomColors(ThemeColorSeed(0x123456, 0xF4F6F8)),
            ),
        )
    }
}
