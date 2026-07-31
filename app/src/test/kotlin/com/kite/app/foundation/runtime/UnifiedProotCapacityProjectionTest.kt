package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedProotCapacityProjectionTest {
    @Test
    fun `projection is a side effect free contract and not a production controller`() {
        val source = File(
            "src/main/kotlin/com/kite/app/foundation/runtime/UnifiedProotCapacity.kt"
        ).readText()

        assertTrue(source.contains("unified_contract_not_production"))
        assertFalse(source.contains("ProcessBuilder"))
        assertFalse(source.contains("android.content.Context"))
        assertFalse(source.contains("BackgroundRuntimeRegistry"))
        assertFalse(source.contains("WarmProotExecutionCoordinator"))
    }

    @Test
    fun `short tasks and long holders share one effective maximum`() {
        val short = snapshot(
            effectiveMax = 4,
            activeByLane = mapOf(RuntimeLaneKind.INTERACTIVE to 2),
            queuedByLane = mapOf(RuntimeLaneKind.BUILD to 1),
        )
        val long = running("service-one", RuntimeLaneKind.SERVICE, generation = 1L)

        val result = UnifiedProotCapacityProjection.project(short, listOf(long))

        assertEquals(UnifiedProotCapacityState.READY, result.state)
        assertEquals(3, result.totalActiveCount)
        assertEquals(1, result.totalQueuedCount)
        assertEquals(1, result.remainingCapacity)
        assertEquals(2, result.activeByLane[RuntimeLaneKind.INTERACTIVE])
        assertEquals(1, result.activeByLane[RuntimeLaneKind.SERVICE])
        assertTrue(result.allowsNewAdmission)
    }

    @Test
    fun `requested owners queue without consuming capacity and released generations disappear`() {
        val requested = LongLivedProotOwnerLeaseTransitions.requested(
            leaseId = "lease-requested",
            generation = 2L,
            spec = spec("queued"),
            nowMs = 1L,
        )
        val released = accepted(
            LongLivedProotOwnerLeaseTransitions.cancelBeforeStart(
                LongLivedProotOwnerLeaseTransitions.requested(
                    leaseId = "lease-released",
                    generation = 1L,
                    spec = spec("queued"),
                    nowMs = 1L,
                ),
                nowMs = 2L,
            )
        )

        val result = UnifiedProotCapacityProjection.project(
            snapshot(effectiveMax = 2),
            listOf(requested, released),
        )

        assertEquals(0, result.longActiveCount)
        assertEquals(1, result.longQueuedCount)
        assertEquals(2, result.remainingCapacity)
        assertEquals(0, result.duplicateOwnerCount)
    }

    @Test
    fun `pressure shrink never evicts holders and closes new admission while overcommitted`() {
        val result = UnifiedProotCapacityProjection.project(
            snapshot(
                effectiveMax = 1,
                activeByLane = mapOf(RuntimeLaneKind.INTERACTIVE to 1),
            ),
            listOf(running("service", RuntimeLaneKind.SERVICE, generation = 1L)),
        )

        assertEquals(UnifiedProotCapacityState.OVERCOMMITTED, result.state)
        assertEquals(2, result.totalActiveCount)
        assertEquals(0, result.remainingCapacity)
        assertFalse(result.allowsNewAdmission)
    }

    @Test
    fun `duplicate owners and process identities fail closed without exposing identity`() {
        val first = running("same-owner", RuntimeLaneKind.SERVICE, generation = 1L)
        val duplicateOwner = running("same-owner", RuntimeLaneKind.BUILD, generation = 2L)
        val duplicateProcess = running(
            ownerId = "other-owner",
            lane = RuntimeLaneKind.SERVICE,
            generation = 1L,
            process = requireNotNull(first.processIdentity),
        )

        val result = UnifiedProotCapacityProjection.project(
            snapshot(effectiveMax = 4),
            listOf(first, duplicateOwner, duplicateProcess),
        )

        assertEquals(UnifiedProotCapacityState.CONTRACT_MISMATCH, result.state)
        assertEquals(1, result.duplicateOwnerCount)
        assertEquals(1, result.conflictingProcessIdentityCount)
        assertFalse(result.allowsNewAdmission)
        val fields = UnifiedProotCapacitySnapshot::class.java.declaredFields.map { it.name }.toSet()
        assertFalse(fields.any { it.contains("ownerId", ignoreCase = true) })
        assertFalse(fields.contains("processIdentity"))
        assertFalse(fields.any { it.contains("command", ignoreCase = true) })
    }

    @Test
    fun `exclusive maintenance is only valid as the sole active holder`() {
        val exclusive = running(
            ownerId = "maintenance",
            lane = RuntimeLaneKind.BUILD,
            generation = 1L,
            posture = LongLivedProotFilesystemPosture.EXCLUSIVE_MAINTENANCE,
        )
        val alone = UnifiedProotCapacityProjection.project(snapshot(effectiveMax = 4), listOf(exclusive))
        val conflicting = UnifiedProotCapacityProjection.project(
            snapshot(
                effectiveMax = 4,
                activeByLane = mapOf(RuntimeLaneKind.INTERACTIVE to 1),
            ),
            listOf(exclusive),
        )

        assertEquals(UnifiedProotCapacityState.EXCLUSIVE_MAINTENANCE_ACTIVE, alone.state)
        assertEquals(UnifiedProotCapacityState.CONTRACT_MISMATCH, conflicting.state)
        assertFalse(alone.allowsNewAdmission)
        assertFalse(conflicting.allowsNewAdmission)
    }

    private fun snapshot(
        effectiveMax: Int,
        activeByLane: Map<RuntimeLaneKind, Int> = emptyMap(),
        queuedByLane: Map<RuntimeLaneKind, Int> = emptyMap(),
    ): ProotJobAdmissionSnapshot {
        val active = RuntimeLaneKind.entries.associateWith { activeByLane[it] ?: 0 }
        val queued = RuntimeLaneKind.entries.associateWith { queuedByLane[it] ?: 0 }
        return ProotJobAdmissionSnapshot(
            profileGroup = RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
            pressure = RuntimePressureLevel.NORMAL,
            effectiveGlobalMax = effectiveMax,
            activeCount = active.values.sum(),
            queuedCount = queued.values.sum(),
            activeSharedWrite = false,
            activeByLane = active,
            queuedByLane = queued,
            admittedCount = 0L,
            cancelledCount = 0L,
            timedOutCount = 0L,
            maxObservedActive = active.values.sum(),
        )
    }

    private fun running(
        ownerId: String,
        lane: RuntimeLaneKind,
        generation: Long,
        posture: LongLivedProotFilesystemPosture = LongLivedProotFilesystemPosture.SHARED_RUNTIME,
        process: LongLivedProotProcessIdentity = LongLivedProotProcessIdentity(
            hostPid = (10_000L + generation + ownerId.length).toInt(),
            processStartTicks = 20_000L + generation + ownerId.length,
        ),
    ): LongLivedProotLeaseRecord {
        val requested = LongLivedProotOwnerLeaseTransitions.requested(
            leaseId = "lease-$ownerId-$generation",
            generation = generation,
            spec = spec(ownerId, lane, posture),
            nowMs = 1L,
        )
        val admitted = accepted(LongLivedProotOwnerLeaseTransitions.admit(requested, 2L))
        val starting = accepted(LongLivedProotOwnerLeaseTransitions.beginStart(admitted, 3L))
        return accepted(LongLivedProotOwnerLeaseTransitions.attachProcess(starting, process, 4L))
    }

    private fun spec(
        ownerId: String,
        lane: RuntimeLaneKind = RuntimeLaneKind.SERVICE,
        posture: LongLivedProotFilesystemPosture = LongLivedProotFilesystemPosture.SHARED_RUNTIME,
    ) = LongLivedProotLeaseSpec(
        owner = LongLivedProotOwnerKey(LongLivedProotOwnerKind.BACKGROUND_RUNTIME, ownerId),
        lane = lane,
        filesystemPosture = posture,
    )

    private fun accepted(transition: LongLivedProotLeaseTransition): LongLivedProotLeaseRecord {
        check(transition.accepted) {
            transition.rejectionReason ?: "unified_proot_capacity_test_transition_rejected"
        }
        return transition.record
    }
}
