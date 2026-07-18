package com.kite.app.application.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguagePreferenceTest {
    @Test
    fun `empty locale list follows system`() {
        assertEquals(AppLanguagePreference.System, AppLanguagePreference.fromLanguageTags(""))
    }

    @Test
    fun `supported locale variants map to stable app choices`() {
        assertEquals(
            AppLanguagePreference.SimplifiedChinese,
            AppLanguagePreference.fromLanguageTags("zh-Hans-CN")
        )
        assertEquals(
            AppLanguagePreference.English,
            AppLanguagePreference.fromLanguageTags("en-US")
        )
    }

    @Test
    fun `unknown stored locale safely falls back to system`() {
        assertEquals(AppLanguagePreference.System, AppLanguagePreference.fromLanguageTags("fr-FR"))
    }
}
