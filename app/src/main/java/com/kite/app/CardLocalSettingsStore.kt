package com.kite.app

import android.content.Context

class CardLocalSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shortcutRequested(recipeId: String): Boolean =
        prefs.getBoolean(shortcutKey(recipeId), false)

    fun setShortcutRequested(recipeId: String, requested: Boolean) {
        prefs.edit().putBoolean(shortcutKey(recipeId), requested).apply()
    }

    private fun shortcutKey(recipeId: String): String = "shortcut_requested_$recipeId"

    private companion object {
        const val PREFS_NAME = "kite_card_local_settings"
    }
}
