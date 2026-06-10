package com.kftest.app.foundation.runtime

import android.content.ComponentCallbacks2
import android.content.Context
import com.kftest.app.foundation.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RuntimePressureResponder {

    private const val LOG_TAG = "RuntimePressureResponder"
    private const val MIN_HANDLE_INTERVAL_MS = 2_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastHandleAt = 0L

    fun onTrimMemory(context: Context, level: Int) {
        RuntimeLifecycleSignalStore.onTrimMemory(level)
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        if (now - lastHandleAt < MIN_HANDLE_INTERVAL_MS) {
            Logger.i(LOG_TAG, "trim ignored by cooldown: level=$level")
            return
        }
        lastHandleAt = now
        scope.launch {
            val policy = RuntimeResidentPolicyStore.load(appContext)
            RuntimeHealthStore.attachContext(appContext)
            val profile = policy.activeProfile
            Logger.i(
                LOG_TAG,
                "trim received: level=${RuntimeLifecycleSignalStore.labelForLevel(level)} profile=${profile.name}"
            )
            when {
                level >= profile.trimTaskRefreshMinLevel -> {
                    RuntimeFrameCoordinator.refreshTaskManager(appContext)
                }

                level >= profile.trimProcessRefreshMinLevel -> {
                    RuntimeFrameCoordinator.refreshProcessSnapshot(
                        context = appContext,
                        reason = "trim-memory:${RuntimeLifecycleSignalStore.labelForLevel(level)}"
                    )
                }
            }
        }
    }

    fun onLowMemory(context: Context) {
        RuntimeLifecycleSignalStore.onLowMemory()
        onTrimMemory(context, ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
