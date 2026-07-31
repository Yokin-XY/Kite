package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedProotOwnerAdmissionRegistryTest {
    @Test
    fun `managed owner shares the same actual capacity with short jobs`() {
        val controller = controller(max = 2)
        val owners = ManagedProotOwnerAdmissionRegistry(controller)
        val managed = owners.acquireBlocking(request("service", generation = 1L), generation = 1L)
        assertTrue(managed is ManagedProotOwnerAdmissionResult.Granted)
        val short = controller.acquireBlocking(shortRequest("short")) as ProotJobAdmissionResult.Granted

        val blocked = controller.acquireBlocking(
            shortRequest("blocked").copy(waitTimeoutMs = 5L)
        ) as ProotJobAdmissionResult.Rejected

        assertEquals("admission_global_capacity_timeout", blocked.reason)
        assertEquals(2, controller.snapshot().activeCount)
        assertEquals(1, controller.snapshot().managedOwnerActiveCount)
        assertTrue(owners.release("service", 1L))
        short.lease.close()
        controller.close()
    }

    @Test
    fun `same owner generation is idempotent and a conflicting generation fails closed`() {
        val controller = controller(max = 2)
        val owners = ManagedProotOwnerAdmissionRegistry(controller)

        val first = owners.acquireBlocking(request("service", 7L), 7L)
        val duplicate = owners.acquireBlocking(request("service", 7L), 7L)
        val conflict = owners.acquireBlocking(request("service", 8L), 8L)

        assertTrue(first is ManagedProotOwnerAdmissionResult.Granted)
        assertTrue((duplicate as ManagedProotOwnerAdmissionResult.Granted).existing)
        assertEquals(
            "managed_owner_generation_conflict",
            (conflict as ManagedProotOwnerAdmissionResult.Rejected).reason,
        )
        assertEquals(1, controller.snapshot().activeCount)
        assertFalse(owners.release("service", 8L))
        assertTrue(owners.release("service", 7L))
        assertEquals(0, controller.snapshot().activeCount)
        controller.close()
    }

    @Test
    fun `restored owners retain capacity without an admission wait`() {
        val controller = controller(max = 1)
        val owners = ManagedProotOwnerAdmissionRegistry(controller)

        val restored = owners.restore(request("restored", 3L), 3L)
        val snapshot = owners.snapshot()

        assertTrue((restored as ManagedProotOwnerAdmissionResult.Granted).restored)
        assertEquals(1, snapshot.activeOwnerCount)
        assertEquals(1, snapshot.restoredOwnerCount)
        assertEquals(1L, controller.snapshot().restoredCount)
        assertTrue(owners.release("restored", 3L))
        controller.close()
    }

    private fun controller(max: Int) = ProotJobAdmissionController(
        ProotJobAdmissionPolicy(
            profileGroup = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
            pressure = RuntimePressureLevel.NORMAL,
            globalMaxOverride = max,
            lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
                if (lane.lane == RuntimeLaneKind.SERVICE) {
                    lane.copy(maxConcurrency = 8, backgroundMaxConcurrency = 8)
                } else {
                    lane
                }
            },
        )
    )

    private fun request(ownerId: String, generation: Long) = ProotJobAdmissionRequest(
        jobId = "managed-$ownerId-$generation",
        ownerId = ownerId,
        lane = RuntimeLaneKind.SERVICE,
        cancellationMode = ProotJobCancellationMode.MANAGED_OWNER,
        resultMode = ProotJobResultMode.DETACHED_BINDING,
        waitTimeoutMs = 20L,
    )

    private fun shortRequest(id: String) = ProotJobAdmissionRequest(
        jobId = id,
        ownerId = "short:$id",
        lane = RuntimeLaneKind.INTERACTIVE,
        cancellationMode = ProotJobCancellationMode.TIMEOUT_AND_OWNER,
        resultMode = ProotJobResultMode.CAPTURED_STDIO,
        waitTimeoutMs = 20L,
    )
}
