package com.kite.app.foundation.runtime

import android.content.ComponentCallbacks2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RuntimeAppVisibilityState {
    FOREGROUND,
    UI_HIDDEN,
    BACKGROUND_PRESSURE,
    LOW_MEMORY
}

data class RuntimeLifecycleSignalSnapshot(
    val mode: String = "android_lifecycle_signal_v0",
    val generatedAtMs: Long = System.currentTimeMillis(),
    val visibilityState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val lastEvent: String = "initial_foreground_assumed",
    val foregroundActivityCount: Int = 0,
    val lastForegroundAtMs: Long = generatedAtMs,
    val lastResumedAtMs: Long? = null,
    val lastResumedActivity: String = "none",
    val backgroundSinceMs: Long? = null,
    val lastTrimLevel: Int? = null,
    val lastTrimLabel: String = "none",
    val lastTrimAtMs: Long? = null,
    val lastLowMemoryAtMs: Long? = null
) {
    fun backgroundAgeMs(now: Long = System.currentTimeMillis()): Long {
        return backgroundSinceMs?.let { (now - it).coerceAtLeast(0L) } ?: 0L
    }

    fun summary(now: Long = System.currentTimeMillis()): String {
        return "mode=$mode state=$visibilityState event=$lastEvent " +
            "activities=$foregroundActivityCount backgroundAgeMs=${backgroundAgeMs(now)} " +
            "trim=$lastTrimLabel"
    }
}

object RuntimeLifecycleSignalStore {

    private val _snapshot = MutableStateFlow(RuntimeLifecycleSignalSnapshot())
    val snapshot: StateFlow<RuntimeLifecycleSignalSnapshot> = _snapshot
    private val startedActivityIds = mutableSetOf<String>()
    private val resumedActivityIds = mutableSetOf<String>()

    @Synchronized
    fun onActivityStarted(activityName: String, activityId: String = activityName) {
        val now = System.currentTimeMillis()
        val previous = _snapshot.value
        val key = activityId.toLifecycleEnvValue()
        startedActivityIds += key
        val count = startedActivityIds.size.coerceAtLeast(1)
        _snapshot.value = previous.copy(
            generatedAtMs = now,
            visibilityState = RuntimeAppVisibilityState.FOREGROUND,
            lastEvent = "activity_started:${activityName.toLifecycleEnvValue()}",
            foregroundActivityCount = count,
            lastForegroundAtMs = now,
            backgroundSinceMs = null
        )
    }

    @Synchronized
    fun onActivityResumed(activityName: String, activityId: String = activityName) {
        val now = System.currentTimeMillis()
        val previous = _snapshot.value
        val key = activityId.toLifecycleEnvValue()
        startedActivityIds += key
        resumedActivityIds += key
        _snapshot.value = previous.copy(
            generatedAtMs = now,
            visibilityState = RuntimeAppVisibilityState.FOREGROUND,
            lastEvent = "activity_resumed:${activityName.toLifecycleEnvValue()}",
            foregroundActivityCount = startedActivityIds.size.coerceAtLeast(1),
            lastForegroundAtMs = now,
            lastResumedAtMs = now,
            lastResumedActivity = activityName.toLifecycleEnvValue(),
            backgroundSinceMs = null
        )
    }

    @Synchronized
    fun onActivityPaused(activityName: String, activityId: String = activityName) {
        resumedActivityIds -= activityId.toLifecycleEnvValue()
    }

    @Synchronized
    fun onActivityStopped(activityName: String, activityId: String = activityName) {
        val now = System.currentTimeMillis()
        val previous = _snapshot.value
        val key = activityId.toLifecycleEnvValue()
        startedActivityIds -= key
        resumedActivityIds -= key
        val count = startedActivityIds.size.coerceAtLeast(0)
        _snapshot.value = previous.copy(
            generatedAtMs = now,
            lastEvent = "activity_stopped:${activityName.toLifecycleEnvValue()}",
            foregroundActivityCount = count,
            backgroundSinceMs = if (count == 0) previous.backgroundSinceMs ?: now else null,
            visibilityState = if (count == 0) {
                RuntimeAppVisibilityState.UI_HIDDEN
            } else {
                RuntimeAppVisibilityState.FOREGROUND
            }
        )
    }

    fun onTrimMemory(level: Int) {
        val now = System.currentTimeMillis()
        val previous = _snapshot.value
        val label = labelForLevel(level)
        val nextState = when {
            previous.foregroundActivityCount > 0 -> RuntimeAppVisibilityState.FOREGROUND
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> RuntimeAppVisibilityState.UI_HIDDEN
            previous.visibilityState == RuntimeAppVisibilityState.LOW_MEMORY -> RuntimeAppVisibilityState.LOW_MEMORY
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> RuntimeAppVisibilityState.BACKGROUND_PRESSURE
            else -> previous.visibilityState
        }
        _snapshot.value = previous.copy(
            generatedAtMs = now,
            visibilityState = nextState,
            lastEvent = "trim_memory:$label",
            backgroundSinceMs = if (nextState == RuntimeAppVisibilityState.FOREGROUND) {
                previous.backgroundSinceMs
            } else {
                previous.backgroundSinceMs ?: now
            },
            lastTrimLevel = level,
            lastTrimLabel = label,
            lastTrimAtMs = now
        )
    }

    fun onLowMemory() {
        val now = System.currentTimeMillis()
        val previous = _snapshot.value
        _snapshot.value = previous.copy(
            generatedAtMs = now,
            visibilityState = RuntimeAppVisibilityState.LOW_MEMORY,
            lastEvent = "low_memory",
            backgroundSinceMs = previous.backgroundSinceMs ?: now,
            lastLowMemoryAtMs = now,
            lastTrimLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            lastTrimLabel = labelForLevel(ComponentCallbacks2.TRIM_MEMORY_COMPLETE),
            lastTrimAtMs = now
        )
    }

    fun labelForLevel(level: Int): String {
        return when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> level.toString()
        }
    }
}

private fun String.toLifecycleEnvValue(): String {
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/-]"), "_")
        .take(80)
}
