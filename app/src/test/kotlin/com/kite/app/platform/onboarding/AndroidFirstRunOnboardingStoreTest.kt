package com.kite.app.platform.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.application.onboarding.FirstRunOnboardingPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidFirstRunOnboardingStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences by lazy {
        context.getSharedPreferences("kite_app_settings", Context.MODE_PRIVATE)
    }

    @Before
    @After
    fun clear() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `legacy completion marker migrates to completed phase`() {
        preferences.edit()
            .putBoolean("first_run_permission_onboarding_done", true)
            .commit()

        assertEquals(
            FirstRunOnboardingPhase.Completed,
            AndroidFirstRunOnboardingStore(context).readPhase()
        )
    }

    @Test
    fun `waiting phase is durable but does not claim completion`() {
        val store = AndroidFirstRunOnboardingStore(context)

        store.writePhase(FirstRunOnboardingPhase.AwaitingAllFilesReturn)

        assertEquals(
            FirstRunOnboardingPhase.AwaitingAllFilesReturn,
            AndroidFirstRunOnboardingStore(context).readPhase()
        )
        assertFalse(preferences.getBoolean("first_run_permission_onboarding_done", false))
    }

    @Test
    fun `completion writes phase and legacy compatibility marker together`() {
        val store = AndroidFirstRunOnboardingStore(context)

        store.writePhase(FirstRunOnboardingPhase.Completed)

        assertEquals(
            FirstRunOnboardingPhase.Completed,
            AndroidFirstRunOnboardingStore(context).readPhase()
        )
        assertTrue(preferences.getBoolean("first_run_permission_onboarding_done", false))
    }
}
