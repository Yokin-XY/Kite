package com.kite.app.platform.runs

import android.content.Context

/** 只保存用户清除过的结果通知代次，不保存或推断任何运行事实。 */
internal class RunNotificationDismissalStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun isDismissed(instanceId: String, generation: Long): Boolean {
        val key = key(instanceId)
        val dismissedGeneration = preferences.getLong(key, NO_GENERATION)
        if (dismissedGeneration == generation) return true
        if (dismissedGeneration != NO_GENERATION) preferences.edit().remove(key).apply()
        return false
    }

    @Synchronized
    fun dismiss(instanceId: String, generation: Long) {
        if (instanceId.isBlank() || generation <= 0L) return
        preferences.edit().putLong(key(instanceId), generation).apply()
    }

    @Synchronized
    fun clear(instanceId: String) {
        preferences.edit().remove(key(instanceId)).apply()
    }

    @Synchronized
    fun prune(knownInstanceIds: Set<String>) {
        val obsolete = preferences.all.keys.filter { storedKey ->
            storedKey.startsWith(KEY_PREFIX) && storedKey.removePrefix(KEY_PREFIX) !in knownInstanceIds
        }
        if (obsolete.isEmpty()) return
        preferences.edit().apply {
            obsolete.forEach(::remove)
        }.apply()
    }

    private fun key(instanceId: String): String = "$KEY_PREFIX$instanceId"

    private companion object {
        const val PREFERENCES_NAME = "kite_run_notification_display"
        const val KEY_PREFIX = "dismissed_generation:"
        const val NO_GENERATION = -1L
    }
}
