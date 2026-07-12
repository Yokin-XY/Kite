package com.kite.app.feature.settings

import android.os.Bundle
import com.kite.app.theme.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsFeatureResultContractTest {
    @Test
    fun `theme request round-trips both colors`() {
        val bundle = Bundle().apply {
            putString("kind", "apply_theme")
            putInt("theme_color", 0x112233)
            putInt("background_color", 0x445566)
        }

        assertEquals(
            SettingsFeatureRequest.ApplyTheme(ThemeConfig(0x112233, 0x445566)),
            SettingsFeatureResultContract.parse(bundle)
        )
    }

    @Test
    fun `unknown request is ignored`() {
        assertNull(SettingsFeatureResultContract.parse(Bundle()))
    }
}
