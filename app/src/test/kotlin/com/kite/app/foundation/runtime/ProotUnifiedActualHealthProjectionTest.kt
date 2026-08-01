package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotUnifiedActualHealthProjectionTest {
    @Test
    fun `actual projection separates short and long holders while preserving the unified total`() {
        val projection = ProotUnifiedActualHealthProjection.project(
            snapshot(active = 3, queued = 2, managedActive = 1, managedQueued = 1, max = 4)
        )

        assertEquals(UnifiedProotCapacityState.READY, projection.state)
        assertEquals(2, projection.shortActiveCount)
        assertEquals(1, projection.longActiveCount)
        assertEquals(3, projection.totalActiveCount)
        assertEquals(1, projection.shortQueuedCount)
        assertEquals(1, projection.longQueuedCount)
        assertEquals(2, projection.totalQueuedCount)
        assertEquals(1, projection.remainingCapacity)
        assertEquals(3, projection.longAdmissionMax)
        assertEquals(2, projection.longAdmissionRemaining)
        assertEquals(1, projection.shortHeadroomCapacity)
        assertFalse(projection.shortHeadroomProtected)
    }

    @Test
    fun `overcommit and contract blocks fail closed without evicting existing counts`() {
        val overcommitted = ProotUnifiedActualHealthProjection.project(
            snapshot(active = 2, managedActive = 2, max = 1)
        )
        val blocked = ProotUnifiedActualHealthProjection.project(
            snapshot(active = 1, managedActive = 1, max = 2, contractBlocks = 1)
        )

        assertEquals(UnifiedProotCapacityState.OVERCOMMITTED, overcommitted.state)
        assertEquals(2, overcommitted.totalActiveCount)
        assertEquals(0, overcommitted.remainingCapacity)
        assertEquals(UnifiedProotCapacityState.CONTRACT_MISMATCH, blocked.state)
        assertEquals(1, blocked.totalActiveCount)
        assertEquals(0, blocked.remainingCapacity)
    }

    @Test
    fun `schema is separate from planned fields and never exposes dynamic identity`() {
        val fields = ProotUnifiedActualHealthProjection.project(
            snapshot(active = 1, managedActive = 1, max = 2, restored = 7L)
        ).toRuntimeHealthEnvText()

        assertTrue(fields.contains("proot_long_actual_schema=managed_proot_owner_v2"))
        assertTrue(fields.contains("proot_long_actual_admission_max=1"))
        assertTrue(fields.contains("proot_unified_actual_schema=shared_proot_capacity_v2"))
        assertTrue(fields.contains("proot_unified_actual_short_headroom_capacity=1"))
        assertTrue(fields.contains("proot_unified_actual_short_headroom_protected=true"))
        assertFalse(fields.contains("proot_long_planned_"))
        listOf(
            "private-owner-527",
            "private-lease-913",
            "49157",
            "/workspace/private",
            "openclaw acp",
            "agent-session-secret",
        ).forEach { forbidden ->
            assertFalse("projection leaked $forbidden", fields.contains(forbidden))
        }
    }

    @Test
    fun `invalid category counts report contract mismatch instead of negative short totals`() {
        val projection = ProotUnifiedActualHealthProjection.project(
            snapshot(active = 1, managedActive = 2, max = 2)
        )

        assertEquals(UnifiedProotCapacityState.CONTRACT_MISMATCH, projection.state)
        assertEquals(0, projection.shortActiveCount)
        assertEquals(2, projection.longActiveCount)
        assertEquals(1, projection.totalActiveCount)
        assertEquals(0, projection.remainingCapacity)
    }

    @Test
    fun `low power exposes no fictional short task headroom`() {
        val projection = ProotUnifiedActualHealthProjection.project(
            snapshot(active = 1, managedActive = 1, max = 1)
        )

        assertEquals(1, projection.longAdmissionMax)
        assertEquals(0, projection.longAdmissionRemaining)
        assertEquals(0, projection.shortHeadroomCapacity)
        assertFalse(projection.shortHeadroomProtected)
    }

    private fun snapshot(
        active: Int,
        queued: Int = 0,
        managedActive: Int = 0,
        managedQueued: Int = 0,
        max: Int,
        restored: Long = 0L,
        contractBlocks: Int = 0,
        managedMax: Int = if (max <= 1) 1 else max - 1,
    ) = ProotJobAdmissionSnapshot(
        profileGroup = RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
        pressure = RuntimePressureLevel.NORMAL,
        effectiveGlobalMax = max,
        activeCount = active,
        queuedCount = queued,
        activeSharedWrite = false,
        activeByLane = RuntimeLaneKind.entries.associateWith { lane ->
            if (lane == RuntimeLaneKind.SERVICE) active else 0
        },
        queuedByLane = RuntimeLaneKind.entries.associateWith { lane ->
            if (lane == RuntimeLaneKind.SERVICE) queued else 0
        },
        admittedCount = active.toLong(),
        cancelledCount = 0L,
        timedOutCount = 0L,
        maxObservedActive = active,
        restoredCount = restored,
        contractBlockCount = contractBlocks,
        managedOwnerAdmissionMax = managedMax,
        managedOwnerActiveCount = managedActive,
        managedOwnerQueuedCount = managedQueued,
    )
}
