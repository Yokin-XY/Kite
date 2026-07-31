package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongLivedProotRecoveryPlannerTest {
    @Test
    fun `exact process generation restores running without requesting a process start`() {
        val running = running(owner("running"), identity())
        val plan = plan(listOf(running), observed = setOf(identity()))
        val decision = plan.decisions.single()

        assertEquals(LongLivedProotRecoveryAction.RESTORED_RUNNING, decision.action)
        assertEquals(LongLivedProotLeasePhase.RUNNING, decision.record.phase)
        assertEquals(LongLivedProotProcessMatch.EXACT_GENERATION, decision.processMatch)
        assertEquals(0, plan.processStartsRequested)
    }

    @Test
    fun `same pid with a different start generation cannot reattach`() {
        val running = running(owner("reused"), identity())
        val reused = LongLivedProotProcessIdentity(identity().hostPid, identity().processStartTicks + 1L)
        val decision = plan(listOf(running), observed = setOf(reused)).decisions.single()

        assertEquals(LongLivedProotRecoveryAction.MOVED_TO_ORPHAN_REVIEW, decision.action)
        assertEquals(LongLivedProotProcessMatch.PID_REUSED, decision.processMatch)
        assertEquals(LongLivedProotLeasePhase.ORPHAN_REVIEW, decision.record.phase)
        assertTrue(decision.record.holdsCapacity)
        assertEquals(identity(), decision.record.processIdentity)
    }

    @Test
    fun `missing attached process remains capacity holder until death confirmation`() {
        val decision = plan(listOf(running(owner("missing"), identity()))).decisions.single()

        assertEquals(LongLivedProotRecoveryAction.MOVED_TO_ORPHAN_REVIEW, decision.action)
        assertTrue(decision.record.holdsCapacity)
        val released = LongLivedProotOwnerLeaseTransitions.confirmDead(decision.record, 101L)
        assertTrue(released.accepted)
        assertFalse(released.record.holdsCapacity)
    }

    @Test
    fun `stop request wins over an exact live running process`() {
        val owner = owner("stop-running")
        val decision = plan(
            persisted = listOf(running(owner, identity())),
            observed = setOf(identity()),
            stopRequested = setOf(owner),
        ).decisions.single()

        assertEquals(LongLivedProotRecoveryAction.RESTORED_STOPPING, decision.action)
        assertEquals(LongLivedProotLeasePhase.STOPPING, decision.record.phase)
        assertTrue(decision.record.holdsCapacity)
    }

    @Test
    fun `stop request during orphan review survives exact reattachment`() {
        val owner = owner("stop-orphan")
        val orphan = accepted(
            LongLivedProotOwnerLeaseTransitions.observeProcessLost(running(owner, identity()), 10L)
        )
        val decision = plan(
            persisted = listOf(orphan),
            observed = setOf(identity()),
            stopRequested = setOf(owner),
        ).decisions.single()

        assertEquals(LongLivedProotRecoveryAction.RESTORED_STOPPING, decision.action)
        assertEquals(LongLivedProotLeasePhase.STOPPING, decision.record.phase)
    }

    @Test
    fun `recovery keeps one newest generation per owner`() {
        val owner = owner("generations")
        val old = requested(owner, leaseId = "old", generation = 1L)
        val newest = requested(owner, leaseId = "new", generation = 2L)
        val plan = plan(listOf(old, newest, newest))
        val decision = plan.decisions.single()

        assertEquals("new", decision.record.leaseId)
        assertEquals(1, decision.discardedOlderGenerations)
        assertEquals(1, decision.collapsedExactDuplicates)
        assertEquals(LongLivedProotRecoveryAction.RESTORED_QUEUED, decision.action)
    }

    @Test
    fun `conflicting records in the same generation require review`() {
        val owner = owner("conflict")
        val first = requested(owner, leaseId = "a", generation = 2L)
        val second = requested(owner, leaseId = "b", generation = 2L)
        val decision = plan(listOf(second, first)).decisions.single()

        assertEquals(LongLivedProotRecoveryAction.DUPLICATE_CONFLICT_REVIEW, decision.action)
        assertEquals("a", decision.record.leaseId)
        assertEquals(1, plan(listOf(second, first)).decisions.size)
    }

    @Test
    fun `one observed process identity cannot be restored to two owners`() {
        val first = running(owner("first-owner"), identity())
        val second = running(owner("second-owner"), identity())
        val plan = plan(listOf(first, second), observed = setOf(identity()))

        assertEquals(2, plan.decisions.size)
        assertTrue(plan.decisions.all {
            it.action == LongLivedProotRecoveryAction.PROCESS_IDENTITY_CONFLICT_REVIEW
        })
        assertEquals(0, plan.processStartsRequested)
    }

    @Test
    fun `unstarted admitted lease is released but unattached start is held for review`() {
        val admittedOwner = owner("admitted")
        val startingOwner = owner("starting")
        val admitted = accepted(LongLivedProotOwnerLeaseTransitions.admit(requested(admittedOwner), 2L))
        val starting = accepted(
            LongLivedProotOwnerLeaseTransitions.beginStart(
                accepted(LongLivedProotOwnerLeaseTransitions.admit(requested(startingOwner), 2L)),
                3L,
            )
        )
        val decisions = plan(listOf(admitted, starting)).decisions.associateBy { it.record.spec.owner }

        assertEquals(
            LongLivedProotRecoveryAction.RELEASED_UNSTARTED_ADMISSION,
            decisions[admittedOwner]?.action,
        )
        assertFalse(requireNotNull(decisions[admittedOwner]).record.holdsCapacity)
        assertEquals(LongLivedProotRecoveryAction.HELD_START_REVIEW, decisions[startingOwner]?.action)
        assertTrue(requireNotNull(decisions[startingOwner]).record.holdsCapacity)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recovery rejects a timestamp older than persisted state`() {
        LongLivedProotRecoveryPlanner.plan(
            persisted = listOf(running(owner("late"), identity())),
            observedAlive = setOf(identity()),
            nowMs = 3L,
        )
    }

    private fun plan(
        persisted: List<LongLivedProotLeaseRecord>,
        observed: Set<LongLivedProotProcessIdentity> = emptySet(),
        stopRequested: Set<LongLivedProotOwnerKey> = emptySet(),
    ) = LongLivedProotRecoveryPlanner.plan(
        persisted = persisted,
        observedAlive = observed,
        stopRequestedOwners = stopRequested,
        nowMs = 100L,
    )

    private fun requested(
        owner: LongLivedProotOwnerKey,
        leaseId: String = "lease-${owner.ownerId}",
        generation: Long = 1L,
    ) = LongLivedProotOwnerLeaseTransitions.requested(
        leaseId = leaseId,
        generation = generation,
        spec = LongLivedProotLeaseSpec(
            owner = owner,
            lane = RuntimeLaneKind.SERVICE,
            filesystemPosture = LongLivedProotFilesystemPosture.SHARED_RUNTIME,
        ),
        nowMs = 1L,
    )

    private fun running(
        owner: LongLivedProotOwnerKey,
        identity: LongLivedProotProcessIdentity,
    ): LongLivedProotLeaseRecord {
        val admitted = accepted(LongLivedProotOwnerLeaseTransitions.admit(requested(owner), 2L))
        val starting = accepted(LongLivedProotOwnerLeaseTransitions.beginStart(admitted, 3L))
        return accepted(LongLivedProotOwnerLeaseTransitions.attachProcess(starting, identity, 4L))
    }

    private fun owner(id: String) =
        LongLivedProotOwnerKey(LongLivedProotOwnerKind.BACKGROUND_RUNTIME, id)

    private fun identity() = LongLivedProotProcessIdentity(hostPid = 42, processStartTicks = 100L)

    private fun accepted(transition: LongLivedProotLeaseTransition): LongLivedProotLeaseRecord {
        check(transition.accepted) { transition.rejectionReason ?: "unexpected_recovery_test_rejection" }
        return transition.record
    }
}
