package com.kite.app.foundation.runtime

import android.content.ComponentCallbacks2
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimePressureResponderTest {
    @Before
    fun setUp() {
        RuntimePressureResponder.resetForTests()
        RuntimeLifecycleSignalStore.resetForTests()
    }

    @After
    fun tearDown() {
        RuntimePressureResponder.resetForTests()
        RuntimeLifecycleSignalStore.resetForTests()
    }

    @Test
    fun planResponse_suppressesSamePressureInsideCooldown() {
        val plan = RuntimePressureResponder.planResponse(
            profile = RuntimeResidentProfile.BALANCED,
            level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            now = 2_000L,
            lastHandleAt = 1_000L,
            lastHandledLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        )

        assertFalse(plan.execute)
        assertEquals(RuntimePressureRefreshAction.PROCESS_SNAPSHOT, plan.action)
        assertEquals("cooldown_same_or_lower_pressure", plan.reason)
    }

    @Test
    fun planResponse_allowsEscalationInsideCooldown() {
        val plan = RuntimePressureResponder.planResponse(
            profile = RuntimeResidentProfile.BALANCED,
            level = ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            now = 2_000L,
            lastHandleAt = 1_000L,
            lastHandledLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        )

        assertTrue(plan.execute)
        assertEquals(RuntimePressureRefreshAction.TASK_MANAGER, plan.action)
        assertEquals("pressure_escalated", plan.reason)
    }

    @Test
    fun planResponse_doesNotTreatUiHiddenAsMemoryPressure() {
        val plan = RuntimePressureResponder.planResponse(
            profile = RuntimeResidentProfile.BALANCED,
            level = ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            now = 2_000L,
            lastHandleAt = 0L,
            lastHandledLevel = Int.MIN_VALUE
        )

        assertFalse(plan.execute)
        assertEquals(RuntimePressureRefreshAction.NONE, plan.action)
        assertEquals("visibility_only", plan.reason)
    }

    @Test
    fun lowMemorySignal_preservesLowMemoryStateWithForegroundActivity() {
        RuntimeLifecycleSignalStore.onActivityStarted("MainActivity", "main")

        RuntimeLifecycleSignalStore.onLowMemory()

        val signal = RuntimeLifecycleSignalStore.snapshot.value
        assertEquals(RuntimeAppVisibilityState.LOW_MEMORY, signal.visibilityState)
        assertEquals("low_memory", signal.lastEvent)
        assertEquals(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, signal.lastTrimLevel)
    }
}
