package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotActualTuningSnapshotTest {
    @Test
    fun `actual scheduler projection is explicit and contains no task payload`() {
        val text = WarmProotExecutionCoordinator.TuningSnapshot(
            profileGroup = RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
            pressure = RuntimePressureLevel.NORMAL,
            foreground = true,
            configuredGlobalMax = 2,
            effectiveGlobalMax = 2,
            maxWarmRunners = 2,
            idleTimeoutMs = 30_000L,
            activeJobs = 1,
            queuedJobs = 3,
            totalWarmSessions = 2,
            activeWarmSessions = 1,
            idleWarmSessions = 1,
            staleWarmSessions = 0,
            oldestIdleAgeMs = 125L,
        ).toRuntimeHealthEnvText()

        assertTrue(text.contains("proot_actual_scheduler_scope=actual_not_planned"))
        assertTrue(text.contains("proot_actual_effective_global_max=2"))
        assertTrue(text.contains("proot_actual_queued_jobs=3"))
        assertTrue(text.contains("proot_actual_oldest_idle_age_ms=125"))
        listOf("argv", "cwd", "owner", "command", "environment", "output").forEach { forbidden ->
            assertFalse("projection must not contain $forbidden", text.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun `reading tuning before pool creation reports zero sessions`() {
        val snapshot = WarmProotExecutionCoordinator.tuningSnapshot()

        assertEquals(0, snapshot.totalWarmSessions)
        assertEquals(0, snapshot.activeWarmSessions)
        assertEquals(0, snapshot.idleWarmSessions)
        assertEquals(0, snapshot.staleWarmSessions)
    }
}
