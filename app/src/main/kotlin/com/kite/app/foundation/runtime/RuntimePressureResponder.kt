package com.kite.app.foundation.runtime

import android.content.ComponentCallbacks2
import android.content.Context
import com.kite.app.foundation.logging.Logger
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
    @Volatile
    private var lastHandledLevel = Int.MIN_VALUE

    fun onTrimMemory(context: Context, level: Int) {
        RuntimeLifecycleSignalStore.onTrimMemory(level)
        handlePressure(
            context = context,
            level = level,
            eventLabel = RuntimeLifecycleSignalStore.labelForLevel(level)
        )
    }

    fun onLowMemory(context: Context) {
        RuntimeLifecycleSignalStore.onLowMemory()
        handlePressure(
            context = context,
            level = ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            eventLabel = "LOW_MEMORY"
        )
    }

    private fun handlePressure(
        context: Context,
        level: Int,
        eventLabel: String
    ) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        scope.launch {
            val policy = RuntimeResidentPolicyStore.load(appContext)
            val response = reserveResponse(
                profile = policy.activeProfile,
                level = level,
                now = now
            )
            if (!response.execute) {
                Logger.i(LOG_TAG, "pressure refresh skipped: event=$eventLabel reason=${response.reason}")
                return@launch
            }
            RuntimeHealthStore.attachContext(appContext)
            Logger.i(
                LOG_TAG,
                "pressure received: event=$eventLabel profile=${policy.activeProfile.name} action=${response.action.name}"
            )
            when (response.action) {
                RuntimePressureRefreshAction.TASK_MANAGER -> {
                    RuntimeFrameCoordinator.refreshTaskManager(appContext)
                }
                RuntimePressureRefreshAction.PROCESS_SNAPSHOT -> {
                    RuntimeFrameCoordinator.refreshProcessSnapshot(
                        context = appContext,
                        reason = "memory-pressure:$eventLabel"
                    )
                }
                RuntimePressureRefreshAction.NONE -> Unit
            }
        }
    }

    @Synchronized
    private fun reserveResponse(
        profile: RuntimeResidentProfile,
        level: Int,
        now: Long
    ): RuntimePressureResponsePlan {
        val response = planResponse(
            profile = profile,
            level = level,
            now = now,
            lastHandleAt = lastHandleAt,
            lastHandledLevel = lastHandledLevel
        )
        if (response.execute) {
            lastHandleAt = now
            lastHandledLevel = level
        }
        return response
    }

    internal fun planResponse(
        profile: RuntimeResidentProfile,
        level: Int,
        now: Long,
        lastHandleAt: Long,
        lastHandledLevel: Int
    ): RuntimePressureResponsePlan {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            return RuntimePressureResponsePlan(
                RuntimePressureRefreshAction.NONE,
                execute = false,
                reason = "visibility_only"
            )
        }
        val action = when {
            level >= profile.trimTaskRefreshMinLevel -> RuntimePressureRefreshAction.TASK_MANAGER
            level >= profile.trimProcessRefreshMinLevel -> RuntimePressureRefreshAction.PROCESS_SNAPSHOT
            else -> RuntimePressureRefreshAction.NONE
        }
        if (action == RuntimePressureRefreshAction.NONE) {
            return RuntimePressureResponsePlan(action, execute = false, reason = "below_profile_threshold")
        }
        val insideCooldown = now - lastHandleAt < MIN_HANDLE_INTERVAL_MS
        if (insideCooldown && level <= lastHandledLevel) {
            return RuntimePressureResponsePlan(action, execute = false, reason = "cooldown_same_or_lower_pressure")
        }
        return RuntimePressureResponsePlan(
            action = action,
            execute = true,
            reason = if (insideCooldown) "pressure_escalated" else "refresh_required"
        )
    }

    internal fun resetForTests() {
        lastHandleAt = 0L
        lastHandledLevel = Int.MIN_VALUE
    }
}

internal enum class RuntimePressureRefreshAction {
    NONE,
    PROCESS_SNAPSHOT,
    TASK_MANAGER
}

internal data class RuntimePressureResponsePlan(
    val action: RuntimePressureRefreshAction,
    val execute: Boolean,
    val reason: String
)
