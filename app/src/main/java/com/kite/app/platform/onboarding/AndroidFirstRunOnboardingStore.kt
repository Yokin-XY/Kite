package com.kite.app.platform.onboarding

import android.content.Context
import com.kite.app.application.onboarding.FirstRunOnboardingPhase
import com.kite.app.application.onboarding.FirstRunOnboardingStore

/** SharedPreferences 只保存一次性引导阶段，不保存权限或运行时就绪事实。 */
internal class AndroidFirstRunOnboardingStore(context: Context) : FirstRunOnboardingStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun readPhase(): FirstRunOnboardingPhase {
        val stored = preferences.getString(KEY_PHASE, null)
            ?.let { value -> runCatching { FirstRunOnboardingPhase.valueOf(value) }.getOrNull() }
        if (stored != null && stored != FirstRunOnboardingPhase.Completed) return stored
        val wasCompleted = stored == FirstRunOnboardingPhase.Completed ||
            preferences.getBoolean(KEY_LEGACY_DONE, false)
        val completedVersion = preferences.getInt(KEY_COMPLETED_VERSION, 0)
        return if (wasCompleted && completedVersion >= CURRENT_VERSION) {
            FirstRunOnboardingPhase.Completed
        } else FirstRunOnboardingPhase.NotStarted
    }

    override fun writePhase(phase: FirstRunOnboardingPhase) {
        preferences.edit()
            .putString(KEY_PHASE, phase.name)
            .putBoolean(KEY_LEGACY_DONE, phase == FirstRunOnboardingPhase.Completed)
            .apply {
                if (phase == FirstRunOnboardingPhase.Completed) {
                    putInt(KEY_COMPLETED_VERSION, CURRENT_VERSION)
                }
            }
            .commit()
    }

    companion object {
        private const val PREFERENCES_NAME = "kite_app_settings"
        private const val KEY_PHASE = "first_run_permission_onboarding_phase"
        private const val KEY_LEGACY_DONE = "first_run_permission_onboarding_done"
        private const val KEY_COMPLETED_VERSION = "first_run_permission_onboarding_version"
        private const val CURRENT_VERSION = 2
    }
}
