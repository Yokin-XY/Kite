package com.kite.app.platform.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.application.settings.SettingsCommand
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.application.settings.SettingsDropZoneSnapshot
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.KiteThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidSettingsGatewayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("kite_theme", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("kite_app_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `defaults form one coherent snapshot`() {
        val gateway = gateway()

        val snapshot = gateway.currentSnapshot()

        assertEquals(KiteTheme.defaultThemeColor, snapshot.themeColor)
        assertEquals(KiteTheme.defaultBackgroundColor, snapshot.backgroundColor)
        assertEquals(KiteThemeMode.SYSTEM, snapshot.themeMode)
        assertEquals(KiteTheme.defaultStyleKey, snapshot.themeStyleKey)
        assertEquals(AppLanguagePreference.System, snapshot.appLanguage)
        assertEquals(BrowserRuntimeMode.Default, snapshot.browserRuntimeMode)
        assertTrue(snapshot.restoreLastScreen)
        assertFalse(snapshot.hideMainTaskFromRecents)
        assertFalse(snapshot.notificationsEnabled)
    }

    @Test
    fun `commands persist and publish app settings without losing other fields`() {
        val gateway = gateway()

        gateway.update(SettingsCommand.SetThemeColor(0x112233))
        gateway.update(SettingsCommand.SetBackgroundColor(0x445566))
        gateway.update(SettingsCommand.SetThemeMode(KiteThemeMode.DARK))
        gateway.update(SettingsCommand.SetThemeStyle(KiteTheme.defaultStyleKey))
        gateway.update(SettingsCommand.SetBrowserRuntimeMode(BrowserRuntimeMode.AutomationBrowser))
        gateway.update(SettingsCommand.SetRestoreLastScreen(false))
        val latest = gateway.update(SettingsCommand.SetHideMainTaskFromRecents(true))
        val restored = gateway()

        assertEquals(0x112233, latest.themeColor)
        assertEquals(0x445566, latest.backgroundColor)
        assertEquals(KiteThemeMode.DARK, latest.themeMode)
        assertEquals(KiteTheme.defaultStyleKey, latest.themeStyleKey)
        assertEquals(BrowserRuntimeMode.AutomationBrowser, latest.browserRuntimeMode)
        assertFalse(latest.restoreLastScreen)
        assertTrue(latest.hideMainTaskFromRecents)
        assertEquals(latest.copy(revision = restored.currentSnapshot().revision), restored.currentSnapshot())
    }

    @Test
    fun `refresh performs system probes and publishes their result`() = runBlocking {
        val gateway = AndroidSettingsGateway(
            context = context,
            readNotificationsEnabled = { true },
            readDropZone = { SettingsDropZoneSnapshot(true) }
        )

        val refreshed = gateway.refresh()

        assertTrue(refreshed.notificationsEnabled)
        assertTrue(refreshed.dropZone.available)
        assertEquals(refreshed, gateway.snapshots.value)
    }

    @Test
    fun `language command uses platform locale owner and publishes the selected language`() {
        var platformLanguage = AppLanguagePreference.System
        val gateway = AndroidSettingsGateway(
            context = context,
            readNotificationsEnabled = { false },
            readAppLanguage = { platformLanguage },
            applyAppLanguage = { platformLanguage = it },
            readDropZone = { SettingsDropZoneSnapshot() }
        )

        val snapshot = gateway.update(
            SettingsCommand.SetAppLanguage(AppLanguagePreference.English)
        )

        assertEquals(AppLanguagePreference.English, platformLanguage)
        assertEquals(AppLanguagePreference.English, snapshot.appLanguage)
        assertEquals(snapshot, gateway.snapshots.value)
    }

    private fun gateway(): AndroidSettingsGateway = AndroidSettingsGateway(
        context = context,
        readNotificationsEnabled = { false },
        readAppLanguage = { AppLanguagePreference.System },
        readDropZone = { SettingsDropZoneSnapshot(false) }
    )
}
