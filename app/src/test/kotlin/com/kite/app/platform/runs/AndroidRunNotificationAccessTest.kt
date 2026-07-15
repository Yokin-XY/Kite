package com.kite.app.platform.runs

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.kite.app.foundation.bootstrap.KFApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidRunNotificationAccessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `run settings intent targets the home card channel`() {
        val intent = AndroidRunNotificationAccess.runChannelSettingsIntent(context)

        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(
            KFApplication.CHANNEL_RUNS,
            intent.getStringExtra(Settings.EXTRA_CHANNEL_ID)
        )
    }

    @Test
    fun `app settings intent keeps the package scope`() {
        val intent = AndroidRunNotificationAccess.appSettingsIntent(context)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
