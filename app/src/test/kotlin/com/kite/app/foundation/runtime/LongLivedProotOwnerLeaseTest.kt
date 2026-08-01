package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LongLivedProotOwnerLeaseTest {
    @Test
    fun `normal owner lifecycle holds capacity until stop confirmation`() {
        val requested = requested()
        val admitted = accepted(LongLivedProotOwnerLeaseTransitions.admit(requested, 2L))
        val starting = accepted(LongLivedProotOwnerLeaseTransitions.beginStart(admitted, 3L))
        val running = accepted(LongLivedProotOwnerLeaseTransitions.attachProcess(starting, process(), 4L))
        val stopping = accepted(LongLivedProotOwnerLeaseTransitions.beginStop(running, 5L))
        val released = accepted(LongLivedProotOwnerLeaseTransitions.confirmStopped(stopping, 6L))

        assertFalse(requested.holdsCapacity)
        listOf(admitted, starting, running, stopping).forEach { assertTrue(it.holdsCapacity) }
        assertFalse(released.holdsCapacity)
        assertEquals(LongLivedProotLeasePhase.RELEASED, released.phase)
        assertEquals(LongLivedProotReleaseReason.STOP_CONFIRMED, released.releaseReason)
        assertNull(released.processIdentity)
    }

    @Test
    fun `same process attach is idempotent and different phase cannot steal lease`() {
        val running = running()
        val same = LongLivedProotOwnerLeaseTransitions.attachProcess(running, process(), 8L)
        val different = LongLivedProotOwnerLeaseTransitions.attachProcess(
            running,
            LongLivedProotProcessIdentity(43, 101L),
            8L,
        )

        assertTrue(same.accepted)
        assertFalse(same.changed)
        assertSame(running, same.record)
        assertFalse(different.accepted)
        assertEquals("long_lived_process_identity_conflict", different.rejectionReason)
        assertSame(running, different.record)
    }

    @Test
    fun `start failure releases only before a process identity is attached`() {
        val starting = accepted(
            LongLivedProotOwnerLeaseTransitions.beginStart(
                accepted(LongLivedProotOwnerLeaseTransitions.admit(requested(), 2L)),
                3L,
            )
        )
        val failed = LongLivedProotOwnerLeaseTransitions.startFailed(starting, 4L)
        val invalidAfterRunning = LongLivedProotOwnerLeaseTransitions.startFailed(running(), 9L)

        assertTrue(failed.accepted)
        assertEquals(LongLivedProotLeasePhase.RELEASED, failed.record.phase)
        assertEquals(LongLivedProotReleaseReason.START_FAILED, failed.record.releaseReason)
        assertFalse(invalidAfterRunning.accepted)
        assertTrue(invalidAfterRunning.record.holdsCapacity)
    }

    @Test
    fun `lost process keeps capacity through orphan review until confirmed dead`() {
        val running = running()
        val orphan = accepted(LongLivedProotOwnerLeaseTransitions.observeProcessLost(running, 10L))

        assertEquals(LongLivedProotLeasePhase.ORPHAN_REVIEW, orphan.phase)
        assertTrue(orphan.holdsCapacity)
        assertEquals(process(), orphan.processIdentity)

        val mismatch = LongLivedProotOwnerLeaseTransitions.reconcileAlive(
            orphan,
            LongLivedProotProcessIdentity(42, 999L),
            11L,
        )
        assertFalse(mismatch.accepted)
        assertTrue(mismatch.record.holdsCapacity)

        val released = accepted(LongLivedProotOwnerLeaseTransitions.confirmDead(orphan, 12L))
        assertFalse(released.holdsCapacity)
        assertEquals(LongLivedProotReleaseReason.PROCESS_DEAD_CONFIRMED, released.releaseReason)
    }

    @Test
    fun `orphan can reattach only to the exact process generation`() {
        val orphan = accepted(LongLivedProotOwnerLeaseTransitions.observeProcessLost(running(), 10L))
        val reconciled = LongLivedProotOwnerLeaseTransitions.reconcileAlive(orphan, process(), 11L)

        assertTrue(reconciled.accepted)
        assertEquals(LongLivedProotLeasePhase.RUNNING, reconciled.record.phase)
        assertEquals(process(), reconciled.record.processIdentity)
        assertTrue(reconciled.record.holdsCapacity)
    }

    @Test
    fun `reconciliation preserves an existing stop intent`() {
        val stopping = accepted(LongLivedProotOwnerLeaseTransitions.beginStop(running(), 8L))
        val orphan = accepted(LongLivedProotOwnerLeaseTransitions.observeProcessLost(stopping, 9L))
        val reconciled = accepted(LongLivedProotOwnerLeaseTransitions.reconcileAlive(orphan, process(), 10L))

        assertEquals(LongLivedProotLeasePhase.STOPPING, reconciled.phase)
        assertNull(reconciled.phaseBeforeOrphan)
        assertTrue(reconciled.holdsCapacity)
    }

    @Test
    fun `stale transition time cannot move the lease backwards`() {
        val admitted = accepted(LongLivedProotOwnerLeaseTransitions.admit(requested(), 8L))
        val stale = LongLivedProotOwnerLeaseTransitions.beginStart(admitted, 7L)

        assertFalse(stale.accepted)
        assertEquals("long_lived_transition_time_regressed", stale.rejectionReason)
        assertSame(admitted, stale.record)
    }

    @Test
    fun `filesystem posture is separate from short task write access`() {
        val specFields = LongLivedProotLeaseSpec::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("filesystemPosture" in specFields)
        assertFalse("access" in specFields)
        assertFalse("command" in specFields)
        assertFalse("argv" in specFields)
    }

    private fun requested() = LongLivedProotOwnerLeaseTransitions.requested(
        leaseId = "lease-background-1",
        generation = 1L,
        spec = LongLivedProotLeaseSpec(
            owner = LongLivedProotOwnerKey(
                LongLivedProotOwnerKind.BACKGROUND_RUNTIME,
                "background:one",
            ),
            lane = RuntimeLaneKind.SERVICE,
            filesystemPosture = LongLivedProotFilesystemPosture.SHARED_RUNTIME,
        ),
        nowMs = 1L,
    )

    private fun running(): LongLivedProotLeaseRecord {
        val admitted = accepted(LongLivedProotOwnerLeaseTransitions.admit(requested(), 2L))
        val starting = accepted(LongLivedProotOwnerLeaseTransitions.beginStart(admitted, 3L))
        return accepted(LongLivedProotOwnerLeaseTransitions.attachProcess(starting, process(), 4L))
    }

    private fun process() = LongLivedProotProcessIdentity(42, 100L)

    private fun accepted(transition: LongLivedProotLeaseTransition): LongLivedProotLeaseRecord {
        assertTrue(transition.rejectionReason, transition.accepted)
        return transition.record
    }
}
